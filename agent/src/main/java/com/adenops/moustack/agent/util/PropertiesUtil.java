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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackProperty;

public class PropertiesUtil {
	private static final Logger log = LoggerFactory.getLogger(PropertiesUtil.class);

	private static void loadPropertiesFile(Properties properties, String path) throws DeploymentException {
		InputStream in = null;
		try {
			in = new FileInputStream(path);
			properties.load(in);
		} catch (IOException e) {
			throw new DeploymentException("cannot load properties from " + path, e);
		}
		try {
			in.close();
		} catch (IOException e) {
		}
	}

	public static Properties loadHostProperties(AgentConfig agentConfig) throws DeploymentException {
		log.debug("loading variables");
		Properties variables = new Properties();

		// even if it's the most specific config we need to load it first to get the role so we store it in a different
		// map, that we will merge later
		Properties hostConfig = new Properties();
		try {
			loadPropertiesFile(hostConfig, PathUtil.getNodePropertiesPath(agentConfig));
		} catch (DeploymentException e) {
			throw new DeploymentException("unknown host: " + agentConfig.getId(), e);
		}
		// allow system properties to override configuration
		hostConfig.putAll(System.getProperties());

		// load profile properties
		loadPropertiesFile(variables, PathUtil.getProfilePropertiesPath(agentConfig));

		// load role specific properties
		String role = (String) hostConfig.get(StackProperty.ROLE.getName());
		if (role != null && !role.isEmpty())
			loadPropertiesFile(variables, PathUtil.getRolePropertiesPath(agentConfig, role));

		// finally we can merge host properties
		variables.putAll(hostConfig);

		return variables;
	}
}
