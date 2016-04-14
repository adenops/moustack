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
import com.adenops.moustack.agent.client.Clients;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.model.openstack.designate.Domain;
import com.adenops.moustack.agent.module.ContainerModule;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.model.Capability;

public class Designate extends ContainerModule {
	private static final Logger log = LoggerFactory.getLogger(Designate.class);

	public Designate(String name, String image, List<String> files, List<String> environments, List<Volume> volumes,
			List<Capability> capabilities, boolean privileged, List<String> devices, boolean syslog) {
		super(name, image, files, environments, volumes, capabilities, privileged, devices, syslog);
	}

	@Override
	public boolean deploy(StackConfig stack) throws DeploymentException {
		boolean changed = false;
		changed |= Clients.getKeystoneClient().createService(stack, "designate",
				"OpenStack DNS service", "dns", "http://%s:9001",
				"http://%s:9001", "http://%s:9001");
		changed |= Clients.getKeystoneClient().createProjectUser(stack, StackProperty.KS_DESIGNATE_USER, "Designate user",
				"designate@localhost", StackProperty.KS_DESIGNATE_PASSWORD, StackProperty.KEYSTONE_SERVICES_PROJECT);
		changed |= Clients.getKeystoneClient().grantProjectRole(stack, StackProperty.KS_DESIGNATE_USER,
				StackProperty.KEYSTONE_SERVICES_PROJECT, StackProperty.KEYSTONE_ADMIN_ROLE);

		changed |= Clients.getMySQLClient().createDatabaseUser("designate", "designate",
				stack.get(StackProperty.DB_DESIGNATE_PASSWORD));

		changed |= Clients.getMySQLClient().createDatabaseUser("designate_pool_manager", "designate",
				stack.get(StackProperty.DB_DESIGNATE_PASSWORD));

		changed |= deployConfig(stack);

		if (changed) {
			Clients.getDockerClient().stopContainer(this);
			log.info("running designate DB migration");
			Clients.getDockerClient().startEphemeralContainer(this, "designate", "designate-manage", "database", "sync");
			log.info("running designate pool manager DB migration");
			Clients.getDockerClient().startEphemeralContainer(this, "designate", "designate-manage", "pool-manager-cache", "sync");
			Clients.getDockerClient().startOrRestartContainer(this);
		}

		changed = false;
		changed |= Clients.getDesignateClient().createServer(stack, stack.get(StackProperty.DESIGNATE_SERVER_NAME) + ".");
		changed |= Clients.getDesignateClient().createDomain(stack, stack.get(StackProperty.DHCP_DOMAIN) + ".", "root@" + stack.get(StackProperty.DHCP_DOMAIN), 60, "Default domain");

		// if a new domain have been created, wait for it to be available using DNS query
		if (changed) {
			int dns_ready_count = 10;

			while (dns_ready_count != 0) {
				try {
					Clients.getDockerClient().startEphemeralContainer(this, "designate", "host", "-W1", "-t SOA", stack.get(StackProperty.DHCP_DOMAIN), stack.get(StackProperty.SERVICES_PUBLIC_IP));
					dns_ready_count = 0;

				} catch (DockerClientException e) {
					log.info("Waiting for SOA (" + dns_ready_count + " attempts left)");

					try {
						Thread.sleep(1000);
					} catch (InterruptedException thread_interrupted) {
						Thread.currentThread().interrupt();
					}
				}
			}

			if (dns_ready_count != 0) {
				throw new DeploymentException("Timeout while waiting for DNS server to be ready");
			}
		}

		String dhcp_domain_id = Clients.getDesignateClient().getDomain(stack, stack.get(StackProperty.DHCP_DOMAIN) + ".").getId();
		log.debug("DHCP domain " + stack.get(StackProperty.DHCP_DOMAIN) + " has id " + dhcp_domain_id);

		// Inject DHCP domain ID into stack properties
		stack.set(StackProperty.DESIGNATE_DEFAULT_DOMAIN_ID, dhcp_domain_id);
		changed |= deployConfig(stack);

		if (changed) {
			Clients.getDockerClient().stopContainer(this);
			log.info("Restart designate container to use newly created domain");
			Clients.getDockerClient().startOrRestartContainer(this);
		}

		return changed;
	}
}
