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
import com.adenops.moustack.agent.model.deployment.DeploymentFile;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.module.ContainerModule;

public class Cinder extends ContainerModule {
	private static final Logger log = LoggerFactory.getLogger(Cinder.class);

	public Cinder(String name, String image, String imageTag, String imageRegistry, List<DeploymentFile> files,
			List<String> environments, List<Volume> volumes, List<String> capabilities, boolean privileged,
			List<String> devices, boolean syslog) {
		super(name, image, imageTag, imageRegistry, files, environments, volumes, capabilities, privileged, devices,
				syslog);
	}

	@Override
	public boolean deploy(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = false;
		changed |= env.getKeystoneClient().createService(env.getStack(), "cinder",
				"OpenStack OpenStack Block Storage service", "volume", "http://%s:8776/v1/%%(tenant_id)s",
				"http://%s:8776/v1/%%(tenant_id)s", "http://%s:8776/v1/%%(tenant_id)s");
		changed |= env.getKeystoneClient().createService(env.getStack(), "cinderv2",
				"OpenStack OpenStack Block Storage service", "volumev2", "http://%s:8776/v2/%%(tenant_id)s",
				"http://%s:8776/v2/%%(tenant_id)s", "http://%s:8776/v2/%%(tenant_id)s");
		changed |= env.getKeystoneClient().createProjectUser(env.getStack(), StackProperty.KS_CINDER_USER,
				"Cinder user", "cinder@localhost", StackProperty.KS_CINDER_PASSWORD,
				StackProperty.KEYSTONE_SERVICES_PROJECT);
		changed |= env.getKeystoneClient().grantProjectRole(env.getStack(), StackProperty.KS_CINDER_USER,
				StackProperty.KEYSTONE_SERVICES_PROJECT, StackProperty.KEYSTONE_ADMIN_ROLE);

		changed |= env.getMySQLClient().createDatabaseUser("cinder", "cinder",
				env.getStack().get(StackProperty.DB_CINDER_PASSWORD));

		changed |= deployConfig(env);

		if (changed) {
			env.getDockerClient().discardContainer(this);
			log.info("running cinder DB migration");
			env.getDockerClient().startEphemeralContainer(this, "cinder", "cinder-manage", "db sync");
		}

		env.getDockerClient().startContainer(changed, this);

		return changed;
	}

	@Override
	public void validate(DeploymentEnvironment env) throws DeploymentException {
		super.validate(env);
		env.getValidationClient().validateEndpoint(env.getStack(), "cinder", "http://%s:8776/", 300);
	}
}
