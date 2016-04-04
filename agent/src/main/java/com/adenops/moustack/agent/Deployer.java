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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.client.Clients;
import com.adenops.moustack.agent.client.MoustackClient;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.log4j2.MemoryAppender;
import com.adenops.moustack.agent.model.docker.Container;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.model.exec.ExecResult;
import com.adenops.moustack.agent.module.BaseModule;
import com.adenops.moustack.agent.util.GitUtil;
import com.adenops.moustack.agent.util.PathUtil;
import com.adenops.moustack.agent.util.ProcessUtil;
import com.adenops.moustack.agent.util.PropertiesUtil;
import com.adenops.moustack.agent.util.YamlUtil;
import com.adenops.moustack.agent.util.YumUtil;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Capability;

public class Deployer {
	public static final Logger log = LoggerFactory.getLogger(Deployer.class);

	// XXX: debian!!!!
	private static final boolean REPORT_YUM_PACKAGES = false;

	// this is a global list of files to ensure there are no overrides
	private final List<String> declaredFiles = new ArrayList<>();
	// similar list, to ensure same container name is not used two times
	private final List<String> declaredContainers = new ArrayList<>();

	private final StackConfig stack;
	private final String[] roles;
	private final ArrayList<BaseModule> modules;
	private final Map<String, Container> containers;

	public Deployer() throws DeploymentException {
		// prepare the stack config for this run
		stack = new StackConfig();

		// retrieve repository information from the server
		Map<String, String> json = MoustackClient.getInstance().getRepositoryInfo();
		stack.setGitRepo(json.get("config_git_url"));
		stack.setGitBranch(json.get("config_git_branch"));
		log.info("config git repo: " + stack.getGitRepo());
		log.info("config git branch: " + stack.getGitBranch());

		// synchronize git repo locally
		GitUtil.synchronizeConfiguration(stack);

		// load host properties
		stack.setProperties(PropertiesUtil.loadHostProperties(AgentConfig.getInstance()));

		// list of roles, we always prepend common
		roles = new String[] { "common", stack.get(StackProperty.ROLE) };

		// load containers definitions
		containers = new HashMap<>();
		containers.putAll(loadContainers());

		// load modules definitions
		modules = new ArrayList<>();
		for (String role : roles)
			modules.addAll(loadModules(role));

		// initialize clients
		Clients.init(stack);
	}

	private void validateFile(String fileFrom, String fileTo) throws DeploymentException {
		// ensure the source file exists
		if (!new File(fileFrom).exists())
			throw new DeploymentException("source file " + fileFrom + " is declared but cannot be found");

		// if no target (container)
		if (fileTo == null)
			return;

		// ensure the destination file has not already been declared
		if (declaredFiles.contains(fileTo))
			throw new DeploymentException("target file " + fileTo + " (from " + fileFrom + ") is already declared");

		// record the destination file
		declaredFiles.add(fileTo);
	}

	private void validateContainer(Container container) throws DeploymentException {
		if (declaredContainers.contains(container.getName()))
			throw new DeploymentException("container with name " + container.getName() + " is already declared");

		declaredContainers.add(container.getName());
	}

	private List<BaseModule> loadModules(String role) throws DeploymentException {
		List<BaseModule> sequence = new ArrayList<>();

		YamlReader reader = null;
		String path = PathUtil.getRoleModulesConfigPath(AgentConfig.getInstance(), role);
		try {
			reader = new YamlReader(new FileReader(path));
		} catch (FileNotFoundException e) {
			throw new DeploymentException("cannot load yaml file " + path, e);
		}

		while (true) {
			@SuppressWarnings("rawtypes")
			Map moduleConfig = null;
			try {
				moduleConfig = (Map) reader.read();
			} catch (YamlException e) {
				throw new DeploymentException("error while parsing module definitiion from " + path);
			}
			if (moduleConfig == null)
				break;

			String name = (String) moduleConfig.get("module");
			boolean registered = Boolean.valueOf((String) moduleConfig.get("registered"));

			Stage stage;
			if (moduleConfig.get("stage") == null)
				stage = Stage.MAIN;
			else {
				try {
					stage = Stage.valueOf(moduleConfig.get("stage").toString().toUpperCase());
				} catch (IllegalArgumentException e) {
					throw new DeploymentException("module " + name + " declares an invalid stage: "
							+ moduleConfig.get("stage"), e);
				}
			}

			List<String> moduleFiles = YamlUtil.getList(moduleConfig.get("files"));
			List<String> modulePackages = YamlUtil.getList(moduleConfig.get("packages"));
			List<String> moduleServices = YamlUtil.getList(moduleConfig.get("services"));
			List<String> moduleContainersNames = YamlUtil.getList(moduleConfig.get("containers"));

			List<Container> moduleContainers = new ArrayList<>();
			for (String containerName : moduleContainersNames) {
				Container container = containers.get(containerName);
				if (container == null)
					throw new DeploymentException("module " + name + " declares an unknown container: " + containerName);
				moduleContainers.add(container);
			}

			// files validation
			for (String file : moduleFiles) {
				String from = PathUtil.getRoleSourceFilePath(AgentConfig.getInstance(), role, file);
				String to = PathUtil.getRoleTargetFilePath(AgentConfig.getInstance(), role, file);
				validateFile(from, to);
			}

			// containers validation
			for (Container container : moduleContainers)
				validateContainer(container);

			BaseModule module = null;
			if (registered) {
				Class<? extends BaseModule> registeredClass = ModuleRegistry.getRegistered(name);
				try {
					module = registeredClass.getConstructor(String.class, Stage.class, String.class, List.class,
							List.class, List.class, List.class).newInstance(name, stage, role, moduleFiles,
							modulePackages, moduleServices, moduleContainers);
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException | SecurityException e) {
					throw new DeploymentException("cannot register module " + name, e);
				}
			} else {
				module = new BaseModule(name, stage, role, moduleFiles, modulePackages, moduleServices,
						moduleContainers);
			}
			sequence.add(module);

			StringBuffer logSB = new StringBuffer("added ");
			logSB.append(registered ? "registered" : "anonymous");
			logSB.append(" module ");
			logSB.append(name);
			if (!stage.equals(Stage.MAIN)) {
				logSB.append(" [stage: ");
				logSB.append(stage.getKey());
				logSB.append("]");
			}
			log.debug(logSB.toString());
		}

		try {
			reader.close();
		} catch (IOException e) {
		}
		return sequence;
	}

	private Map<String, Container> loadContainers() throws DeploymentException {
		Map<String, Container> containers = new HashMap<>();

		YamlReader reader = null;
		String path = PathUtil.getContainersConfigPath(AgentConfig.getInstance());
		try {
			reader = new YamlReader(new FileReader(path));
		} catch (FileNotFoundException e) {
			throw new DeploymentException("cannot load yaml file " + path, e);
		}

		while (true) {
			@SuppressWarnings("rawtypes")
			Map moduleConfig = null;
			try {
				moduleConfig = (Map) reader.read();
			} catch (YamlException e) {
				throw new DeploymentException("error while parsing container definition from " + path);
			}
			if (moduleConfig == null)
				break;

			// TODO: better handling of default values
			String name = (String) moduleConfig.get("container");
			String image = (String) moduleConfig.get("image");
			boolean privileged = Boolean.valueOf((String) moduleConfig.get("privileged"));
			boolean syslog = Boolean.valueOf((String) moduleConfig.getOrDefault("syslog", "true"));
			List<String> environments = YamlUtil.getList(moduleConfig.get("environments"));
			List<String> devices = YamlUtil.getList(moduleConfig.get("devices"));

			List<Volume> volumes = new ArrayList<Volume>();
			List<String> files = new ArrayList<String>();
			List<Capability> capabilities = new ArrayList<Capability>();

			// we iterate through files entries and add each entry as:
			// * file entry for the deployment
			// * volume entry for availability in the containers
			for (String entry : YamlUtil.getList(moduleConfig.get("files"))) {
				String[] split = entry.split(":", 2);
				if (split.length != 2)
					throw new DeploymentException("invalid file definition: " + entry);

				files.add(split[0]);
				volumes.add(new Volume(PathUtil.getContainerTargetFilePath(stack, split[0]), split[1], true));
			}

			// add environments to files for deployment
			files.addAll(environments);

			// TODO: use enum for ro/rw?
			for (String entry : YamlUtil.getList(moduleConfig.get("volumes"))) {
				String[] split = entry.split(":");
				if (split.length < 2 || split.length > 3)
					throw new DeploymentException("invalid volume definition: " + entry);
				String permission = split.length == 3 ? split[2] : "rw";
				if (!permission.equals("rw") && !permission.equals("ro"))
					throw new DeploymentException("invalid volume permission: " + entry);
				volumes.add(new Volume(split[0], split[1], permission.equals("ro")));
			}

			for (String entry : YamlUtil.getList(moduleConfig.get("capabilities"))) {
				try {
					capabilities.add(Capability.valueOf(entry));
				} catch (IllegalArgumentException e) {
					throw new DeploymentException("invalid capability: " + entry);
				}
			}

			for (String file : files) {
				String from = PathUtil.getContainerSourceFilePath(AgentConfig.getInstance(), file);
				validateFile(from, null);
			}

			containers.put(name, new Container(name, image, files, environments, volumes, capabilities, privileged,
					devices, syslog));
			log.debug("added container " + name);
		}

		try {
			reader.close();
		} catch (IOException e) {
			// not important
		}
		return containers;
	}

	private boolean runStage(Stage stage) throws DeploymentException {
		boolean changed = false;
		for (BaseModule module : modules) {
			if (module.getStage().equals(stage)) {
				log.info("deploying module " + module.getName());
				changed |= module.deploy(stack);
			}
		}
		return changed;
	}

	public boolean start() throws DeploymentException {
		long start = System.currentTimeMillis();
		boolean changed = false;

		log.info("starting deployment of " + AgentConfig.getInstance().getId() + " (roles: " + String.join(",", roles)
				+ ")");

		log.info("entering stage: [pre]");
		// install eatmydata if we are going to use it
		if (YumUtil.EATMYDATA)
			YumUtil.install("eatmydata");
		changed |= runStage(Stage.PRE);

		log.info("entering stage: [main]");
		changed |= runStage(Stage.MAIN);

		log.info("entering stage: [post]");
		changed |= runStage(Stage.POST);

		long duration = System.currentTimeMillis() - start;
		log.info("deployment finished (" + duration / 1000 + "s)");

		if (changed)
			log.info("system has been updated");
		else
			log.info("no change on the system");

		return changed;
	}

	private void appendLine(StringBuffer sb, String... strings) {
		for (String string : strings)
			sb.append(string);
		sb.append("\n");
	}

	private String toBase64(String content) {
		if (content == null || content.equals(""))
			content = "No content";
		try {
			return Base64.getEncoder().encodeToString(content.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			log.error("cannot base64 encode report section: " + e.getMessage());
			return null;
		}
	}

	public String getSystemReport(boolean includeLogs) throws DeploymentException {
		StringBuffer sb = new StringBuffer();

		Map<String, String> report = new HashMap();

		// general information
		appendLine(sb, "date: ", new Date().toString());
		appendLine(sb, "host: ", AgentConfig.getInstance().getId());
		appendLine(sb, "roles: ", String.join(",", roles));
		appendLine(sb, "git repo: ", stack.getGitRepo());
		appendLine(sb, "git branch: ", stack.getGitBranch());
		appendLine(sb, "git head: ", stack.getGitHead());
		appendLine(sb);

		report.put("general", toBase64(sb.toString()));

		// logs (if required)
		if (includeLogs) {
			report.put("logs", toBase64(MemoryAppender.getBuffer().toString()));
			MemoryAppender.clearBuffer();

		}

		// containers
		sb = new StringBuffer();
		for (BaseModule module : modules) {
			for (Container container : module.getContainers()) {
				sb.append(Clients.getDockerClient().getContainerInfo(container));
				appendLine(sb);
			}
		}

		report.put("containers", toBase64(sb.toString()));

		// packages installed
		if (REPORT_YUM_PACKAGES) {
			ExecResult result = ProcessUtil.execute("yum", "list", "installed", "--debuglevel=0");
			report.put("packages", toBase64(new String(result.getStdout().toByteArray(), StandardCharsets.UTF_8)));
		}

		ObjectMapper objectMapper = new ObjectMapper();

		try {
			return objectMapper.writeValueAsString(report);
		} catch (JsonProcessingException e) {
			log.error("error while serializing report", e);
		}

		return null;
	}
}
