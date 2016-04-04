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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.model.moustack.AgentReport;
import com.adenops.moustack.agent.model.moustack.AgentStatus;
import com.adenops.moustack.agent.model.moustack.ServerCommand;
import com.adenops.moustack.agent.util.HttpUtil;
import com.adenops.moustack.agent.util.HttpUtil.TrustAllHostNameVerifier;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/*
 * TODO: implement retry logic
 */
public class MoustackClient {
	private static final Logger log = LoggerFactory.getLogger(MoustackClient.class);

	private static final int CONNECTION_MAX_RETRY = 5;
	private static final int CONNECTION_RETRY_SLEEP = 2;

	private static final String API_AGENT = "rest/agent";
	private static final String API_REPORT = "rest/report";
	private static final String API_STATUS = "rest/status";

	private final Client client;
	private final WebTarget webTarget;
	private final ObjectMapper mapper;

	private static final MoustackClient instance = new MoustackClient();

	public static MoustackClient getInstance() {
		return instance;
	}

	private MoustackClient() {

		log.debug("initializing Moustack client");

		JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider().configure(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		ClientBuilder clientBuilder = ClientBuilder.newBuilder();

		// disable SSL verification if requested
		if (!AgentConfig.getInstance().isSslVerify()) {
			clientBuilder.hostnameVerifier(new TrustAllHostNameVerifier());
			try {
				SSLContext ctx = SSLContext.getInstance("SSL");
				ctx.init(null, HttpUtil.certs, new SecureRandom());
				clientBuilder.sslContext(ctx);
			} catch (KeyManagementException | NoSuchAlgorithmException e) {
				// not clean, but easier to throw a runtime exception here
				throw new RuntimeException("cannot initialize SSl context", e);
			}
		}

		// setup basic authentication
		HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(AgentConfig.getInstance().getUser(),
				AgentConfig.getInstance().getPassword());
		clientBuilder.register(feature);

		// build client and register Jackson serializer
		client = clientBuilder.build();
		client.register(jacksonJsonProvider);

		// allow patch method
		client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);

		// prepare target
		webTarget = client.target(AgentConfig.getInstance().getServer());

		// instanciate a single object mapper to reuse it
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public Map<String, String> getRepositoryInfo() throws DeploymentException {
		Response response = HttpUtil.get(getTarget(API_AGENT, AgentConfig.getInstance().getId(), "config"));
		return response.readEntity(Map.class);
	}

	public void postReport(AgentReport.ReasonEnum reason, String content) {
		AgentReport report = new AgentReport();
		report.setDate(new Date());
		report.setHostname(AgentConfig.getInstance().getId());
		report.setReason(reason);
		report.setContent(content);

		try {
			HttpUtil.post(getTarget(API_REPORT), report);
		} catch (DeploymentException e) {
			// we don't want to handle this error later, this is not critical
			log.error("could not post report: " + e.getMessage());
		}
	}

	public void postStatus(AgentStatus.StatusEnum status) {
		AgentStatus agentStatus = new AgentStatus();
		agentStatus.setDate(new Date());
		agentStatus.setHostname(AgentConfig.getInstance().getId());
		agentStatus.setStatus(status);

		try {
			HttpUtil.post(getTarget(API_STATUS), agentStatus);
		} catch (DeploymentException e) {
			// we don't want to handle this error later
			log.error("could not post report: " + e.getMessage());
		}
	}

	public Response longPoll() throws DeploymentException {
		return HttpUtil.longPoll(getTarget(API_AGENT, AgentConfig.getInstance().getId(), "poll"));
	}

	private WebTarget getTarget(String... path) {
		return webTarget.path(String.join("/", path));
	}

	public ServerCommand readCommand(Response response) {
		if (response == null)
			return null;
		return response.readEntity(ServerCommand.class);
	}
}
