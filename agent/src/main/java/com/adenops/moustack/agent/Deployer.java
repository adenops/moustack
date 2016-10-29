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
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentEnvironment.OSFamily;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.log4j2.MemoryAppender;
import com.adenops.moustack.agent.model.deployment.DeploymentFile;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// TODO: review files/volumes declarations & deployment logic
// TODO: factorise "files: section from container/system modules if possible
public class Deployer {
	public static final Logger log = LoggerFactory.getLogger(Deployer.class);
	private static final Pattern DOCKER_IMAGE_REGEX = Pattern.compile("^(.+):([^:]+)$");

	// this is a global list of files to ensure there are no overrides
	private final List<String> systemFiles = new ArrayList<>();

	// deployment plan (ordered modules list to deploy)
	private final List<BaseModule> deploymentPlan;

	// deployment environment
	private final DeploymentEnvironment env;

	public Deployer(StackConfig stack) throws DeploymentException {
		// detect OS information
		DeploymentEnvironment.OSFamily osFamily = null;
		if (new File("/etc/debian_version").exists())
			osFamily = OSFamily.DEBIAN;
		else if (new File("/etc/redhat-release").exists())
			osFamily = OSFamily.REDHAT;
		else
			throw new DeploymentException("could no detect OS family");

		String osId = null;
		String osVersion = null;

		try (InputStream is = new FileInputStream(new File("/etc/os-release"))) {
			Properties osRelease = new Properties();
			osRelease.load(is);

			// retrieve OS information we may need
			osId = osRelease.getProperty("ID");
			osVersion = osRelease.getProperty("VERSION_ID");

			// sanitize
			if (osId != null)
				osId = osId.replaceAll("\"", "");
			if (osVersion != null)
				osVersion = osVersion.replaceAll("\"", "");

		} catch (IOException e) {
			log.warn("unknown OS, could not parse /etc/os-release found");
		}

		log.info("OS family: {}", osFamily.name().toLowerCase());
		log.info("OS id: {}", osId != null ? osId : "unknown");
		log.info("OS version: {}", osVersion != null ? osVersion : "unknown");

		// synchronize git repo locally
		GitUtil.synchronizeConfiguration(stack);

		// load node properties hierarchy
		stack.setProperties(PropertiesUtil.loadHostProperties(AgentConfig.getInstance()));

		// validate mandatory properties are set
		for (StackProperty property : StackProperty.values())
			if (stack.get(property) == null)
				throw new DeploymentException("mandatory property " + property.getName() + " is not set");

		// load deployment environment
		env = new DeploymentEnvironment(stack, osFamily, osId, osVersion);

		// load the global modules list
		Map<String, BaseModule> modules = loadModules();
		log.info("loaded {} modules definitions", modules.size());

		// load deployment plan
		deploymentPlan = loadDeploymentPlan(modules);
	}

	private List<BaseModule> loadDeploymentPlan(Map<String, BaseModule> modules) throws DeploymentException {
		List<BaseModule> plan = new ArrayList<BaseModule>();

		for (String role : env.getStack().getRoles()) {
			log.debug("loading deployment plan for role [{}]", role);

			Map<Object, Object> planConfig = YamlUtil
					.loadYaml(PathUtil.getRoleModulesConfigPath(AgentConfig.getInstance(), role));

			List<String> planModules = YamlUtil.getList(planConfig.get("modules"));

			for (String moduleName : planModules) {
				log.debug("adding module [{}] to the role [{}] deployment plan", moduleName, role);
				BaseModule module = modules.get(moduleName);
				if (module == null)
					throw new DeploymentException(
							"module " + moduleName + " not defined but declared by the role " + role);
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

	private DeploymentFile toSystemDeploymentFile(String moduleName, String fileDefinition, boolean parse)
			throws DeploymentException {
		String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), moduleName, fileDefinition);
		String to = PathUtil.getSystemTargetFilePath(AgentConfig.getInstance(), fileDefinition);
		validateFile(from, to);

		return new DeploymentFile(from, to, parse);
	}

	private BaseModule loadSystemModule(String name, Map<Object, Object> moduleConfig) throws DeploymentException {
		List<DeploymentFile> files = new ArrayList<>();

		String register = (String) moduleConfig.get("register");
		List<String> moduleFiles = YamlUtil.getList(moduleConfig.get("files"));
		List<String> moduleRawFiles = YamlUtil.getList(moduleConfig.get("rawfiles"));
		List<String> modulePackages = YamlUtil.getList(moduleConfig.get("packages"));
		List<String> moduleServices = YamlUtil.getList(moduleConfig.get("services"));

		// TODO: validation (mandatory fields)

		for (String file : moduleFiles)
			files.add(toSystemDeploymentFile(name, file, true));

		for (String file : moduleRawFiles)
			files.add(toSystemDeploymentFile(name, file, false));

		SystemModule module = null;
		if (StringUtils.isEmpty(register)) {
			module = new SystemModule(name, files, modulePackages, moduleServices);
		} else {
			if (!(SystemModule.class.isAssignableFrom(ModuleRegistry.getRegistered(register))))
				throw new DeploymentException("module " + name + " registration error");
			@SuppressWarnings("unchecked")
			Class<SystemModule> registeredClass = (Class<SystemModule>) ModuleRegistry.getRegistered(register);

			try {
				module = registeredClass.getConstructor(String.class, List.class, List.class, List.class)
						.newInstance(name, files, modulePackages, moduleServices);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new DeploymentException("cannot register module " + name, e);
			}
		}

		logModule(name, "system", register);
		return module;
	}

	private DeploymentFile toContainerDeploymentFile(String moduleName, String fileDefinition, boolean parse)
			throws DeploymentException {
		String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), moduleName, fileDefinition);
		String to = PathUtil.getContainerTargetFilePath(env.getStack(), moduleName, fileDefinition);
		validateFile(from, null);

		return new DeploymentFile(from, to, parse);
	}

	private BaseModule loadContainerModule(String name, Map<Object, Object> moduleConfig) throws DeploymentException {
		List<DeploymentFile> files = new ArrayList<>();

		// TODO: better handling of default values
		String register = (String) moduleConfig.get("register");
		String image = (String) moduleConfig.get("image");
		boolean privileged = Boolean.valueOf((String) moduleConfig.get("privileged"));
		boolean syslog = Boolean.valueOf((String) moduleConfig.getOrDefault("syslog", "true"));
		List<String> moduleFiles = YamlUtil.getList(moduleConfig.get("files"));
		List<String> moduleRawFiles = YamlUtil.getList(moduleConfig.get("rawfiles"));
		List<String> environments = YamlUtil.getList(moduleConfig.get("environments"));
		List<String> devices = YamlUtil.getList(moduleConfig.get("devices"));
		List<String> capabilities = YamlUtil.getList(moduleConfig.get("capabilities"));

		List<Volume> volumes = new ArrayList<>();

		// TODO: validation (mandatory fields)

		Matcher matcher = DOCKER_IMAGE_REGEX.matcher(image);
		if (!matcher.find())
			throw new DeploymentException("could not extract tag for image " + image);
		image = matcher.group(1);
		String imageTag = matcher.group(2);

		for (String file : moduleFiles) {
			files.add(toContainerDeploymentFile(name, file, true));
			String toContainer = Paths.get("/", file).toString();
			volumes.add(new Volume(PathUtil.getContainerTargetFilePath(env.getStack(), name, file), toContainer, "ro"));
		}

		for (String file : moduleRawFiles) {
			files.add(toContainerDeploymentFile(name, file, false));
			String toContainer = Paths.get("/", file).toString();
			volumes.add(new Volume(PathUtil.getContainerTargetFilePath(env.getStack(), name, file), toContainer, "ro"));
		}

		for (String file : environments)
			files.add(toContainerDeploymentFile(name, file, true));

		for (String entry : YamlUtil.getList(moduleConfig.get("volumes"))) {
			String[] split = entry.split(":");
			if (split.length < 2 || split.length > 3)
				throw new DeploymentException("invalid volume definition: " + entry);
			String mode = split.length == 3 ? split[2] : "rw";
			// TODO: add mode validation
			volumes.add(new Volume(split[0], split[1], mode));
		}

		ContainerModule module = null;
		if (StringUtils.isEmpty(register)) {
			module = new ContainerModule(name, image, imageTag, files, environments, volumes, capabilities, privileged,
					devices, syslog);
		} else {
			if (!(ContainerModule.class.isAssignableFrom(ModuleRegistry.getRegistered(register))))
				throw new DeploymentException("module " + name + " registration error");
			@SuppressWarnings("unchecked")
			Class<ContainerModule> registeredClass = (Class<ContainerModule>) ModuleRegistry.getRegistered(register);

			try {
				module = registeredClass.getConstructor(String.class, String.class, String.class, List.class,
						List.class, List.class, List.class, boolean.class, List.class, boolean.class).newInstance(name,
								image, imageTag, files, environments, volumes, capabilities, privileged, devices,
								syslog);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new DeploymentException("cannot register module " + name, e);
			}
		}

		logModule(name, "container", register);
		return module;
	}

	private BaseModule loadModule(String module) throws DeploymentException {
		Map<Object, Object> moduleConfig = null;

		String fileName = null;

		// first try MODULE-OS_ID-OS_VERSION
		fileName = String.format("module-%s-%s", env.getOsId(), env.getOsVersion());
		try {
			moduleConfig = YamlUtil.loadYaml(PathUtil.getModuleConfigPath(AgentConfig.getInstance(), module, fileName));
		} catch (Exception e) {
			log.trace("module file {} not found", fileName);
		}
		// second try MODULE-OS_ID
		fileName = String.format("module-%s", module, env.getOsId());
		try {
			moduleConfig = YamlUtil.loadYaml(PathUtil.getModuleConfigPath(AgentConfig.getInstance(), module, fileName));
		} catch (Exception e) {
			log.trace("module file {} not found", fileName);
		}
		// last try MODULE only
		moduleConfig = YamlUtil.loadYaml(PathUtil.getModuleConfigPath(AgentConfig.getInstance(), module, "module"));

		String type = (String) moduleConfig.get("type");
		String name = (String) moduleConfig.get("name");

		log.trace("module {} type is {}", name, type);

		switch (type) {
		case "system":
			return loadSystemModule(name, moduleConfig);
		case "container":
			return loadContainerModule(name, moduleConfig);
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

	private void logModule(String name, String type, String register) {
		if (!log.isDebugEnabled())
			return;
		StringBuffer sb = new StringBuffer("loaded ");
		if (!StringUtils.isEmpty(register)) {
			sb.append("registered (");
			sb.append(register);
			sb.append(") ");
		} else
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
				+ String.join(",", env.getStack().getRoles()) + ")");

		for (BaseModule module : deploymentPlan) {
			log.info("deploying {} module [{}]", module.getType(), module.getName());
			changed |= module.deploy(env);
			module.validate(env);
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
		appendLine(sb, "roles: ", String.join(",", env.getStack().getRoles()));
		appendLine(sb, "git repo: ", env.getStack().getGitRepo());
		appendLine(sb, "git branch: ", env.getStack().getGitBranch());
		appendLine(sb, "git head: ", env.getStack().getGitHead());
		appendLine(sb);

		report.put("general", toBase64(sb.toString()));

		// logs (if required)
		if (includeLogs) {
			report.put("logs", toBase64(MemoryAppender.getBuffer().toString()));
			MemoryAppender.clearBuffer();

		}

		// containers
		try {
			sb = new StringBuffer();
			for (BaseModule module : deploymentPlan) {
				if (module instanceof ContainerModule) {
					sb.append(env.getDockerClient().getContainerInfo((ContainerModule) module));
					appendLine(sb);
				}
			}
			report.put("containers", toBase64(sb.toString()));
		} catch (Throwable e) {
			// docker may not yet be present/ready
			log.warn("could not retrieve containers information");
		}

		// packages installed
		if (env.getOsFamily().equals(DeploymentEnvironment.OSFamily.REDHAT)) {
			ExecResult result = ProcessUtil.execute("yum", "list", "installed", "--debuglevel=0");
			report.put("packages", toBase64(new String(result.getStdout().toByteArray(), StandardCharsets.UTF_8)));
		} else {
			ExecResult result = ProcessUtil.execute("dpkg-query", "-f", "${binary:Package}-${Version}\n", "-W");
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
