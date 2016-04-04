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

package com.adenops.moustack.agent.model.keystone;

public class User extends OSEntity {
	// TODO: should be configurable
	public static final String DEFAULT_DOMAIN_ID = "default";
	private String name;
	private String description;
	private String email;
	private String password;
	private String default_project_id;
	private String domain_id = DEFAULT_DOMAIN_ID;
	private boolean enabled = true;

	public User() {
	}

	public User(String name, String description, String email, String password, String default_project_id, String domain_id) {
		this.name = name;
		this.description = description;
		this.email = email;
		this.password = password;
		this.default_project_id = default_project_id;
		this.domain_id = domain_id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDomain_id() {
		return domain_id;
	}

	public void setDomain_id(String domain_id) {
		this.domain_id = domain_id;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDefault_project_id() {
		return default_project_id;
	}

	public void setDefault_project_id(String default_project_id) {
		this.default_project_id = default_project_id;
	}
}
