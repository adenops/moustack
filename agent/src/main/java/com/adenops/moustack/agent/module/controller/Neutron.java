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

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.Stage;
import com.adenops.moustack.agent.client.Clients;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.model.docker.Container;
import com.adenops.moustack.agent.module.BaseModule;

public class Neutron extends BaseModule {
	private static final Logger log = LoggerFactory.getLogger(Neutron.class);

	public Neutron(String name, Stage stage, String role, List<String> files, List<String> packages,
			List<String> services, List<Container> containers) {
		super(name, stage, role, files, packages, services, containers);
	}

	@Override
	public boolean deployContainers(StackConfig stack) throws DeploymentException {
		boolean changed = false;
		changed |= Clients.getKeystoneClient().createService(stack, "neutron", "OpenStack Networking service",
				"network", "http://%s:9696", "http://%s:9696", "http://%s:9696");
		changed |= Clients.getKeystoneClient().createProjectUser(stack, StackProperty.KS_NEUTRON_USER, "Neutron user",
				"neutron@localhost", StackProperty.KS_NEUTRON_PASSWORD, StackProperty.KEYSTONE_SERVICES_PROJECT);
		changed |= Clients.getKeystoneClient().grantProjectRole(stack, StackProperty.KS_NEUTRON_USER,
				StackProperty.KEYSTONE_SERVICES_PROJECT, StackProperty.KEYSTONE_ADMIN_ROLE);

		changed |= Clients.getMySQLClient().createDatabaseUser("neutron", "neutron",
				stack.get(StackProperty.DB_NEUTRON_PASSWORD));

		changed |= deployContainersConfig(stack);

		if (changed) {
			Clients.getDockerClient().stopContainers(containers);
			log.info("running neutron DB migration");
			Clients.getDockerClient().startEphemeralContainer(getContainer("neutron-controller"), "neutron",
					"neutron-db-manage", "--config-file", "/etc/neutron/neutron.conf", "--config-file",
					"/etc/neutron/plugins/ml2/ml2_conf.ini", "upgrade", "head");
			Clients.getDockerClient().startOrRestartContainers(containers);
		}

		return changed;
	}
}
