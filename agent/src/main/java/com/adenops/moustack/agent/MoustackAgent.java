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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
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
import com.adenops.moustack.lib.util.LockUtil;

public class MoustackAgent {
	public static final Logger log = LoggerFactory.getLogger(MoustackAgent.class);

	private static final String PROG_NAME = "Moustack Agent";
	private static final String PROG_CMD = "moustack-agent";
	private static final String PROG_VERSION = "0.1";
	private static final String HELP_HEADER = "OpenStack configuration management system";
	private static final String HELP_FOOTER = "https://github.com/adenops/moustack";

	// for locking (prevent multiple parallel runs)
	private final File lockFile = new File("/var/run/moustack-agent.lock");
	private FileLock lock;

	// keep track of the last deployer instance, so we can reapply config even
	// if the connection with the server is broken
	private Deployer deployer;

	private static void error(String message) {
		System.err.println(PROG_NAME + ": error: " + message);
		System.exit(1);
	}

	// XXX: we need to be able to post status and/or report updates here
	// and in the shutdown hook
	public static void main(String[] args) throws ParserInternalException {
		AgentConfig agentConfig = (AgentConfig) new ArgumentsParser(PROG_NAME, PROG_CMD, PROG_VERSION, HELP_HEADER,
				HELP_FOOTER, AgentConfig.class).parse(args);

		// if config is null but no exception, help has been triggered
		if (agentConfig == null)
			return;

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

		// setup logging
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();
		LoggerConfig loggerConfig = config.getLoggerConfig("com.adenops.moustack");
		loggerConfig.setLevel(AgentConfig.getInstance().getLevel().getLog4jLevel());
		ctx.updateLoggers();

		// TODO: convert to regex in argparser
		if (!validString(AgentConfig.getInstance().getServer())
				|| !(AgentConfig.getInstance().getServer().startsWith("http://") || AgentConfig.getInstance()
						.getServer().startsWith("https://")))
			error("need a valid server url");

		MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.STANDBY);

		if (!AgentConfig.getInstance().isLongPolling()) {
			int code = deploy();
			MoustackClient.getInstance().postStatus(AgentStatus.StatusEnum.SHUTDOWN);
			return code;
		}

		log.info("starting long polling");
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
			stack.setGitRepo(json.get("config_git_url"));
			stack.setGitBranch(json.get("config_git_branch"));
			log.info("config git repo: " + stack.getGitRepo());
			log.info("config git branch: " + stack.getGitBranch());

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
