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

package com.adenops.moustack.agent.module.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentEnvironment;
import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.module.ContainerModule;
import com.github.dockerjava.api.model.Capability;

public class Ceilometer extends ContainerModule {
	private static final Logger log = LoggerFactory.getLogger(Ceilometer.class);

	public Ceilometer(String name, String image, List<String> files, List<String> environments, List<Volume> volumes,
			List<Capability> capabilities, boolean privileged, List<String> devices, boolean syslog) {
		super(name, image, files, environments, volumes, capabilities, privileged, devices, syslog);
	}

	@Override
	public boolean deploy(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = false;
		changed |= env.getKeystoneClient().createService(env.getStack(), "ceilometer", "OpenStack Telemetry service",
				"metering", "http://%s:8777", "http://%s:8777", "http://%s:8777");
		changed |= env.getKeystoneClient().createProjectUser(env.getStack(), StackProperty.KS_CEILOMETER_USER,
				"Ceilometer user", "ceilometer@localhost", StackProperty.KS_CEILOMETER_PASSWORD,
				StackProperty.KEYSTONE_SERVICES_PROJECT);
		changed |= env.getKeystoneClient().grantProjectRole(env.getStack(), StackProperty.KS_CEILOMETER_USER,
				StackProperty.KEYSTONE_SERVICES_PROJECT, StackProperty.KEYSTONE_ADMIN_ROLE);

		env.getMongoClient().createDatabaseUser(env.getStack().get(StackProperty.DB_CEILOMETER_DATABASE),
				env.getStack().get(StackProperty.DB_CEILOMETER_USER),
				env.getStack().get(StackProperty.DB_CEILOMETER_PASSWORD));

		changed |= deployConfig(env);

		if (changed)
			env.getDockerClient().startOrRestartContainer(this);

		return changed;
	}

	@Override
	public void validate(DeploymentEnvironment env) throws DeploymentException {
		super.validate(env);
		env.getValidationClient().validateEndpoint(env.getStack(), "ceilometer", "http://%s:8777", 401);
	}
}
