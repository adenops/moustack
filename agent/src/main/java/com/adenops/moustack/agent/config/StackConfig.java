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

package com.adenops.moustack.agent.config;

import java.util.Properties;

import com.adenops.moustack.agent.DeploymentException;

public class StackConfig {
	private Properties properties;
	private String gitRepo;
	private String gitBranch = "master";
	private String gitHead;

	public StackConfig() {
	}

	public String[] getRoles() throws DeploymentException {
		// XXX: temporary, until we support multi-roles
		return new String[] { get(StackProperty.ROLE) };
	}

	public String get(StackProperty variable) throws DeploymentException {
		if (variable == null)
			return null;
		return properties.getProperty(variable.getName());
	}

	/**
	 * This is a dirty and modify properties should be avoid as much as possible. This has been introduced for designate.
	 */
	public void set(StackProperty variable, String value) {
		properties.setProperty(variable.getName(), value);
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public String getGitRepo() {
		return gitRepo;
	}

	public void setGitRepo(String gitRepo) {
		this.gitRepo = gitRepo;
	}

	public String getGitBranch() {
		return gitBranch;
	}

	public void setGitBranch(String gitBranch) {
		this.gitBranch = gitBranch;
	}

	public String getGitHead() {
		return gitHead;
	}

	public void setGitHead(String gitHead) {
		this.gitHead = gitHead;
	}
}
