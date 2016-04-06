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

package com.adenops.moustack.agent.util;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;

public class PathUtil {
	private static final Logger log = LoggerFactory.getLogger(PathUtil.class);

	private static StringBuffer getProfilePath(AgentConfig agentConfig) {
		StringBuffer sb = new StringBuffer(agentConfig.getConfigDir());
		sb.append(File.separator);
		sb.append(agentConfig.getProfile());
		return sb;
	}

	private static StringBuffer getRolePath(AgentConfig agentConfig, String role) {
		StringBuffer sb = new StringBuffer(getProfilePath(agentConfig));
		sb.append(File.separator);
		sb.append("roles");
		sb.append(File.separator);
		sb.append(role);
		return sb;
	}

	public static String getModulesPath(AgentConfig agentConfig) {
		StringBuffer sb = new StringBuffer(getProfilePath(agentConfig));
		sb.append(File.separator);
		sb.append("modules");
		return sb.toString();
	}

	public static String getModulePath(AgentConfig agentConfig, String module) {
		StringBuffer sb = new StringBuffer(getModulesPath(agentConfig));
		sb.append(File.separator);
		sb.append(module);
		return sb.toString();
	}

	public static String getModuleConfigPath(AgentConfig agentConfig, String module) {
		StringBuffer sb = new StringBuffer(getModulePath(agentConfig, module));
		sb.append(File.separator);
		sb.append("module.yaml");
		return sb.toString();
	}

	public static String getProfilePropertiesPath(AgentConfig agentConfig) {
		StringBuffer sb = new StringBuffer(getProfilePath(agentConfig));
		sb.append(File.separator);
		sb.append("profile.properties");
		return sb.toString();
	}

	public static String getRolePropertiesPath(AgentConfig agentConfig, String role) {
		StringBuffer sb = new StringBuffer(getRolePath(agentConfig, role));
		sb.append(File.separator);
		sb.append("role.properties");
		return sb.toString();
	}

	public static String getNodePropertiesPath(AgentConfig agentConfig) {
		StringBuffer sb = new StringBuffer(getProfilePath(agentConfig));
		sb.append(File.separator);
		sb.append("nodes");
		sb.append(File.separator);
		sb.append(agentConfig.getId());
		sb.append(".properties");
		return sb.toString();
	}

	public static String getRoleModulesConfigPath(AgentConfig agentConfig, String role) {
		StringBuffer sb = new StringBuffer(getRolePath(agentConfig, role));
		sb.append(File.separator);
		sb.append("modules.yaml");
		return sb.toString();
	}

	public static String getModuleSourceFilePath(AgentConfig agentConfig, String module, String file) {
		StringBuffer sb = new StringBuffer(getModulePath(agentConfig, module));
		sb.append(File.separator);
		sb.append(file);
		return sb.toString();
	}

	public static String getContainerTargetFilePath(StackConfig stack, String module, String file)
			throws DeploymentException {
		StringBuffer sb = new StringBuffer(stack.get(StackProperty.CONTAINERS_ROOT));
		sb.append(File.separator);
		sb.append(module);
		sb.append(File.separator);
		sb.append(file);
		return sb.toString();
	}

	public static String getSystemTargetFilePath(AgentConfig agentConfig, String file) {
		StringBuffer sb = new StringBuffer(File.separator);
		sb.append(file);
		return sb.toString();
	}
}
