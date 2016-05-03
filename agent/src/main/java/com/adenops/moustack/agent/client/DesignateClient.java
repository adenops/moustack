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

package com.adenops.moustack.agent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.model.openstack.BaseResponse;
import com.adenops.moustack.agent.model.openstack.designate.Domain;
import com.adenops.moustack.agent.model.openstack.designate.DomainsResponse;
import com.adenops.moustack.agent.model.openstack.designate.Server;
import com.adenops.moustack.agent.model.openstack.designate.ServersResponse;
import com.adenops.moustack.agent.model.openstack.keystone.TokenResponse;

public class DesignateClient extends AbstractOpenStackClient {
	private static final Logger log = LoggerFactory.getLogger(DesignateClient.class);

	public DesignateClient(StackConfig stack, KeystoneClient keystoneClient) throws DeploymentException {
		super("designate", String.format("http://%s:9001/v1", stack.get(StackProperty.SERVICES_INTERNAL_IP)));
		TokenResponse response = keystoneClient.getAdminToken(stack);
		token = (String) response.getHeaders().getFirst("X-Subject-Token");
		log.debug("got token {}", token);
	}

	private Server getServer(StackConfig stack, String name) throws DeploymentException {
		// the API does not support filtering, wee need to retrieve all entries then manually filter
		// WARNING: this will return the first match
		ServersResponse serversResponse = get(ServersResponse.class, "servers", null);
		for (Server server : serversResponse.getServers()) {
			if (server.getName().equals(name))
				return server;
		}
		return null;
	}

	public boolean createServer(StackConfig stack, String name) throws DeploymentException {
		Server existingServer = getServer(stack, name);
		if (existingServer != null) {
			log.debug("server " + name + " already exists");
			return false;
		}

		log.info("creating server " + name);
		post(BaseResponse.class, "servers", null, new Server(name));
		return true;
	}

	public Domain getDomain(StackConfig stack, String name) throws DeploymentException {
		// same as getServer
		DomainsResponse domainsResponse = get(DomainsResponse.class, "domains", null);
		for (Domain domain : domainsResponse.getDomains()) {
			if (domain.getName().equals(name))
				return domain;
		}
		return null;
	}

	public boolean createDomain(StackConfig stack, String name, String email, int ttl, String description)
			throws DeploymentException {
		Domain existingDomain = getDomain(stack, name);
		if (existingDomain != null) {
			// TODO: add attributes check and update if necessary
			log.debug("domain " + name + " already exists");
			return false;
		}

		log.info("creating domain " + name);
		post(BaseResponse.class, "domains", new Domain(name, email, ttl, description));
		return true;
	}
}
