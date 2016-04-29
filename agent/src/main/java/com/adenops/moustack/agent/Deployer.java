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
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
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
import com.adenops.moustack.agent.log4j2.MemoryAppender;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.model.exec.ExecResult;
import com.adenops.moustack.agent.module.BaseModule;
import com.adenops.moustack.agent.module.ContainerModule;
import com.adenops.moustack.agent.module.SystemModule;
import com.adenops.moustack.agent.util.GitUtil;
import com.adenops.moustack.agent.util.PathUtil;
import com.adenops.moustack.agent.util.ProcessUtil;
import com.adenops.moustack.agent.util.PropertiesUtil;
import com.adenops.moustack.agent.util.YamlUtil;
import com.adenops.moustack.agent.util.YumUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Capability;

// TODO: review files/volumes declarations & deployment logic
// TODO: factorise "files: section from container/system modules if possible
// TODO: detect OS: debian/redhat, abstract packaging
public class Deployer {
	public static final Logger log = LoggerFactory.getLogger(Deployer.class);

	// XXX: debian!!!!
	private static final boolean REPORT_YUM_PACKAGES = false;

	// this is a global list of files to ensure there are no overrides
	private final List<String> systemFiles = new ArrayList<>();

	private final StackConfig stack;
	private final Map<String, BaseModule> modules;
	private final List<BaseModule> deploymentPlan;

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

		// load node properties hierarchy
		stack.setProperties(PropertiesUtil.loadHostProperties(AgentConfig.getInstance()));

		// load the global modules list
		modules = loadModules();
		log.info("loaded {} modules definitions", modules.size());

		// load deployment plan
		deploymentPlan = loadDeploymentPlan();

		// initialize clients
		Clients.init(stack);
	}

	private List<BaseModule> loadDeploymentPlan() throws DeploymentException {
		List<BaseModule> plan = new ArrayList<BaseModule>();

		for (String role : stack.getRoles()) {
			log.debug("loading deployment plan for role [{}]", role);

			Map<Object, Object> planConfig = YamlUtil.loadYaml(PathUtil.getRoleModulesConfigPath(
					AgentConfig.getInstance(), role));

			List<String> planModules = YamlUtil.getList(planConfig.get("modules"));

			for (String moduleName : planModules) {
				log.debug("adding module [{}] to the role [{}] deployment plan", moduleName, role);
				BaseModule module = modules.get(moduleName);
				if (module == null)
					throw new DeploymentException("module " + moduleName + " not defined but declared by the role "
							+ role);
				plan.add(module);
			}

			log.info("deployment plan for role [{}] declares {} modules", role, modules.size());
		}
		return plan;
	}

	private void validateFile(String fileFrom, String fileTo) throws DeploymentException {
		// ensure the source file exists
		if (!new File(fileFrom).exists())
			throw new DeploymentException("source file " + fileFrom + " is declared but cannot be found");

		// if no target (container)
		if (fileTo == null)
			return;

		// ensure the destination file has not already been declared
		if (systemFiles.contains(fileTo))
			throw new DeploymentException("target file " + fileTo + " (from " + fileFrom + ") is already declared");

		// record the destination file
		systemFiles.add(fileTo);
	}

	private BaseModule loadSystemModule(String name, boolean registered, Map<Object, Object> moduleConfig)
			throws DeploymentException {
		List<String> moduleFiles = YamlUtil.getList(moduleConfig.get("files"));
		List<String> modulePackages = YamlUtil.getList(moduleConfig.get("packages"));
		List<String> moduleServices = YamlUtil.getList(moduleConfig.get("services"));

		// files validation
		for (String file : moduleFiles) {
			String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), name, file);
			String to = PathUtil.getSystemTargetFilePath(AgentConfig.getInstance(), file);
			validateFile(from, to);
		}

		SystemModule module = null;
		if (registered) {

			if (!(SystemModule.class.isAssignableFrom(ModuleRegistry.getRegistered(name))))
				throw new DeploymentException("module " + name + " registration error");
			@SuppressWarnings("unchecked")
			Class<SystemModule> registeredClass = (Class<SystemModule>) ModuleRegistry.getRegistered(name);

			try {
				module = registeredClass.getConstructor(String.class, List.class, List.class, List.class).newInstance(
						name, moduleFiles, modulePackages, moduleServices);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new DeploymentException("cannot register module " + name, e);
			}
		} else {
			module = new SystemModule(name, moduleFiles, modulePackages, moduleServices);
		}

		logModule(name, "system", registered);
		return module;
	}

	private BaseModule loadContainerModule(String name, boolean registered, Map<Object, Object> moduleConfig)
			throws DeploymentException {
		// TODO: better handling of default values
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
		for (String file : YamlUtil.getList(moduleConfig.get("files"))) {
			String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), name, file);
			String to = Paths.get("/", file).toString();
			validateFile(from, null);
			files.add(file);
			// TODO: "ro" is ugly
			volumes.add(new Volume(PathUtil.getContainerTargetFilePath(stack, name, file), to, "ro"));
		}

		// add environments to files for deployment
		files.addAll(environments);

		for (String entry : YamlUtil.getList(moduleConfig.get("volumes"))) {
			String[] split = entry.split(":");
			if (split.length < 2 || split.length > 3)
				throw new DeploymentException("invalid volume definition: " + entry);
			String mode = split.length == 3 ? split[2] : "rw";
			// TODO: add mode validation
			volumes.add(new Volume(split[0], split[1], mode));
		}

		for (String entry : YamlUtil.getList(moduleConfig.get("capabilities"))) {
			try {
				capabilities.add(Capability.valueOf(entry));
			} catch (IllegalArgumentException e) {
				throw new DeploymentException("invalid capability: " + entry);
			}
		}

		for (String file : files) {
			String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), name, file);
			validateFile(from, null);
		}

		ContainerModule module = null;
		if (registered) {

			if (!(ContainerModule.class.isAssignableFrom(ModuleRegistry.getRegistered(name))))
				throw new DeploymentException("module " + name + " registration error");
			@SuppressWarnings("unchecked")
			Class<ContainerModule> registeredClass = (Class<ContainerModule>) ModuleRegistry.getRegistered(name);

			try {
				module = registeredClass.getConstructor(String.class, String.class, List.class, List.class, List.class,
						List.class, boolean.class, List.class, boolean.class).newInstance(name, image, files,
						environments, volumes, capabilities, privileged, devices, syslog);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new DeploymentException("cannot register module " + name, e);
			}
		} else {
			module = new ContainerModule(name, image, files, environments, volumes, capabilities, privileged, devices,
					syslog);
		}

		logModule(name, "container", registered);
		return module;
	}

	private BaseModule loadModule(String module) throws DeploymentException {
		Map<Object, Object> moduleConfig = YamlUtil.loadYaml(PathUtil.getModuleConfigPath(AgentConfig.getInstance(),
				module));

		String type = (String) moduleConfig.get("type");
		String name = (String) moduleConfig.get("name");
		boolean registered = Boolean.valueOf((String) moduleConfig.get("registered"));

		log.trace("module {} type is {}", name, type);

		switch (type) {
		case "system":
			return loadSystemModule(name, registered, moduleConfig);
		case "container":
			return loadContainerModule(name, registered, moduleConfig);
		default:
			throw new DeploymentException("invalid module type: " + type);
		}
	}

	private Map<String, BaseModule> loadModules() throws DeploymentException {
		Map<String, BaseModule> modules = new HashMap<>();

		// locate the modules
		File file = new File(PathUtil.getModulesPath(AgentConfig.getInstance()));
		String[] directories = file.list(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				return new File(current, name).isDirectory();
			}
		});

		// iterate on directories and load modules definitions
		for (String modulePath : directories) {
			log.trace("found module in {}", modulePath);
			BaseModule module = loadModule(modulePath);
			modules.put(module.getName(), module);
		}

		return modules;
	}

	private void logModule(String name, String type, boolean registered) {
		if (!log.isDebugEnabled())
			return;
		StringBuffer sb = new StringBuffer("loaded ");
		if (registered)
			sb.append("registered ");
		else
			sb.append("anonymous ");
		sb.append(type);
		sb.append(" module [");
		sb.append(name);
		sb.append("]");
		log.debug(sb.toString());
	}

	public boolean start() throws DeploymentException {
		long start = System.currentTimeMillis();
		boolean changed = false;

		log.info("starting deployment of " + AgentConfig.getInstance().getId() + " (roles: "
				+ String.join(",", stack.getRoles()) + ")");

		// install eatmydata if we are going to use it
		if (YumUtil.EATMYDATA)
			YumUtil.install("eatmydata");

		for (BaseModule module : deploymentPlan) {
			log.info("deploying {} module [{}]", module.getType(), module.getName());
			changed |= module.deploy(stack);
			module.validate(stack);
		}

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

		Map<String, String> report = new HashMap<>();

		// general information
		appendLine(sb, "date: ", new Date().toString());
		appendLine(sb, "host: ", AgentConfig.getInstance().getId());
		appendLine(sb, "roles: ", String.join(",", stack.getRoles()));
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
		for (BaseModule module : deploymentPlan) {
			if (module instanceof ContainerModule) {
				sb.append(Clients.getDockerClient().getContainerInfo((ContainerModule) module));
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
