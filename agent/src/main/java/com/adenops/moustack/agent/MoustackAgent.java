/**
 * Copyright (C) 2016 Adenops Consultants Informatique Inc.
 *
 * This file is part of the Moustack project, see http://www.moustack.org for
 * more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adenops.moustack.agent;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.client.MoustackClient;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.moustack.AgentReport;
import com.adenops.moustack.agent.model.moustack.AgentStatus;
import com.adenops.moustack.agent.model.moustack.ServerCommand;
import com.adenops.moustack.lib.argsparser.ArgumentsParser;
import com.adenops.moustack.lib.argsparser.exception.ParserInternalException;
import com.adenops.moustack.lib.model.ApplicationInfo;
import com.adenops.moustack.lib.util.LockUtil;
import com.adenops.moustack.lib.util.MiscUtil;

public class MoustackAgent {
	public static final Logger log = LoggerFactory.getLogger(MoustackAgent.class);
	public static ApplicationInfo applicationInfo;

	// for locking (prevent multiple parallel runs)
	private final File lockFile = new File("/var/run/moustack-agent.lock");
	private FileLock lock;

	// keep track of the last deployer instance, so we can reapply config even
	// if the connection with the server is broken
	private Deployer deployer;

	private static void error(String message) {
		System.err.println(applicationInfo.getApplicationName() + ": error: " + message);
		System.exit(1);
	}

	// XXX: we need to be able to post status and/or report updates here
	// and in the shutdown hook
	public static void main(String[] args) throws ParserInternalException {
		applicationInfo = MiscUtil.loadApplicationInfo("moustack-agent");
		System.out.println(String.format("%s %s (build %s)", applicationInfo.getDisplayName(),
				applicationInfo.getVersion(), applicationInfo.getBuild()));

		AgentConfig agentConfig = (AgentConfig) new ArgumentsParser(applicationInfo.getDisplayName(),
				applicationInfo.getApplicationName(), applicationInfo.getVersion(), applicationInfo.getDescription(),
				applicationInfo.getUrl(), AgentConfig.class).parse(args);

		// if config is null but no exception, help has been triggered
		if (agentConfig == null)
			return;

		// setup logging
		MiscUtil.configureLogging(agentConfig.getLogLevel());

		int exitCode = 5;
		try {
			exitCode = new MoustackAgent().start(agentConfig);
		} catch (DeploymentException e) {
			log.error("the following exception occurred:", e);
		}

		// we need a system exit even with no error because of jersey thread timeout
		System.exit(exitCode);
	}

	private boolean validString(String string) {
		return string != null && !string.isEmpty();
	}

	public int start(AgentConfig agentConfig) throws DeploymentException {

		// try to acquire lock
		// TODO: try to lock only the configuration update part
		try {
			lock = LockUtil.acquireLock(lockFile);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
			return 2;
		}
		if (lock == null) {
			log.error("another instance is already running");
			return 2;
		}

		// add a shutdown hook to ensure the lock is released
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				LockUtil.releaseLock(lock, lockFile);
			}
		});

		// TODO: convert to regex in argparser
		if (!validString(AgentConfig.getInstance().getServer())
				|| !(AgentConfig.getInstance().getServer().startsWith("http://")
						|| AgentConfig.getInstance().getServer().startsWith("https://")))
			error("need a valid server url");

		MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.STANDBY);

		if (AgentConfig.getInstance().isRunOnce()) {
			int code = deploy();
			MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.SHUTDOWN);
			return code;
		}

		log.info("connecting to the server and waiting for command");
		Response response = null;
		while (true) {
			try {
				response = MoustackClient.getInstance().longPoll();

				// timeout is expected with long-polling, skip to next iteration
				if (response.getStatusInfo().equals(Response.Status.REQUEST_TIMEOUT))
					continue;

			} catch (DeploymentException e) {

				log.error("server error: " + e.getMessage());
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
				}
				continue;
			}

			ServerCommand command = MoustackClient.getInstance().readCommand(response);
			log.debug("server sent command: " + command.getCommand());

			switch (command.getCommand()) {
			case RUN:
				log.info("server triggered a run");
				deploy();
				break;
			case REPORT:
				log.info("server requests a report");

				if (deployer == null) {
					log.warn("report requested but deployer not yet initialized");
					break;
				}

				MoustackClient.getInstance().postReport(AgentReport.ReasonEnum.SYSTEM_STATUS,
						deployer.getSystemReport(false));
				break;
			case SHUTDOWN:
				log.info("server requests agent shutdown");
				// XXX: we only catch requested shutdown
				// we need to find a way to avoid passing agentConfig to the getInstance
				MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.SHUTDOWN);
				return 0;
			}
		}
	}

	private int deploy() throws DeploymentException {
		MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.UPDATING);

		try {
			// prepare the stack config for this run
			StackConfig stack = new StackConfig();

			// retrieve repository information from the server
			Map<String, String> json = MoustackClient.getInstance().getRepositoryInfo();

			stack.setGitRepo(json.get("gitUrl"));
			stack.setGitBranch(json.get("gitBranch"));
			if (StringUtils.isBlank(stack.getGitRepo()))
				throw new DeploymentException("invalid git repo url");
			if (StringUtils.isBlank(stack.getGitBranch()))
				throw new DeploymentException("invalid git branch");
			stack.setDockerRegistry(json.get("dockerRegistry"));
			stack.setDockerMoustackTag(json.get("dockerMoustackTag"));

			log.info("git repo: " + stack.getGitRepo());
			log.info("git branch: " + stack.getGitBranch());
			if (!StringUtils.isBlank(stack.getDockerRegistry()))
				log.info("docker registry: " + stack.getDockerRegistry());
			if (!StringUtils.isBlank(stack.getDockerMoustackTag()))
				log.info("docker moustack tag: " + stack.getDockerMoustackTag());

			deployer = new Deployer(stack);

			boolean changed = deployer.start();
			MoustackClient.getInstance().postReport(
					changed ? AgentReport.ReasonEnum.UPDATE_SUCCESS : AgentReport.ReasonEnum.UPDATE_NOCHANGE,
					deployer.getSystemReport(true));

			return 0;
		} catch (DeploymentException e) {
			log.error("deployment failed, the following exception occurred:", e);
			String content = deployer == null ? null : deployer.getSystemReport(true);
			MoustackClient.getInstance().postReport(AgentReport.ReasonEnum.UPDATE_FAILURE, content);
			return 1;
		} finally {
			MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.STANDBY);
			displayStats();
		}
	}

	private void displayStats() {
		Runtime instance = Runtime.getRuntime();
		log.info("JVM Total Memory: " + instance.totalMemory() / 1048576);
		log.info("JVM Free Memory: " + instance.freeMemory() / 1048576);
		log.info("JVM Used Memory: " + (instance.totalMemory() - instance.freeMemory()) / 1048576);
		log.info("JVM Max Memory: " + instance.maxMemory() / 1048576);
	}
}
