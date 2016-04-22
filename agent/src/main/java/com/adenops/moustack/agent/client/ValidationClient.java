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

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;

public class ValidationClient extends ManagedClient {
	private static final Logger log = LoggerFactory.getLogger(ValidationClient.class);
	private static final int MAX_RETRIES = 20;
	private static final int RETRY_WAIT = 1;
	protected final Client client;

	protected ValidationClient(StackConfig stack) throws DeploymentException {
		client = ClientBuilder.newBuilder().build();
	}

	public void validateEndpoint(StackConfig stack, String name, String url, int expectedStatus)
			throws DeploymentException {
		WebTarget webTarget = client.target(String.format(url, stack.get(StackProperty.SERVICES_INTERNAL_IP)));
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON_TYPE);

		for (int i = 0; i <= MAX_RETRIES; i++) {
			try {
				Response response = invocationBuilder.get();
				if (response.getStatus() == expectedStatus) {
					log.trace("validation succeed for endpoint {}", webTarget.getUri());
					return;
				}
			} catch (ProcessingException e) {
				// ignore
			}

			if (i == MAX_RETRIES)
				throw new DeploymentException("connection error with [" + name + "] API");

			log.debug("failed to connect to [{}], waiting {}s", name, RETRY_WAIT);
			try {
				Thread.sleep(RETRY_WAIT * 1000);
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	protected void release() {
		if (client == null)
			return;

		log.debug("closing validation client");
		client.close();
	}
}
