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

package com.adenops.moustack.server.rest.api;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.server.MoustackServer;
import com.adenops.moustack.server.ServerConfig;
import com.adenops.moustack.server.client.PersistenceClient;
import com.adenops.moustack.server.rest.model.AgentInfo;
import com.adenops.moustack.server.rest.model.AgentReport;
import com.adenops.moustack.server.rest.model.ServerCommand;

/*
 * This API endpoint is a bit special, and does not respect REST standards
 * TODO: check if we can do better
 */
@Api(value = "/agent")
@Path("/agent")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class APIAgent extends APIBase {
	private static final Logger log = LoggerFactory.getLogger(APIAgent.class);
	// use synchronized Map for safety, performance is not a concern
	private static final Map<String, AsyncResponse> pollingClients = Collections.synchronizedMap(new HashMap<>());

	public String getRepositoryURL() {
		if (!ServerConfig.getInstance().getRepoUri().startsWith("file:///"))
			return ServerConfig.getInstance().getRepoUri();

		URI baseUri = uriInfo.getBaseUri();

		// dynamically generate the url, this way we should support
		// being behind a proxy
		StringBuffer sb = new StringBuffer(baseUri.getScheme());
		sb.append("://");
		sb.append(baseUri.getHost());
		sb.append(":");
		sb.append(baseUri.getPort());
		sb.append(MoustackServer.GIT_CONTEXT);
		sb.append("/");
		sb.append(MoustackServer.REPOSITORY_NAME);
		return sb.toString();
	}

	public static String getRepositoryBranch() {
		return MoustackServer.REPOSITORY_BRANCH;
	}

	@GET
	@Path("/{hostname}/poll")
	@ApiOperation(value = "", response = ServerCommand.class)
	public void poll(@Suspended final AsyncResponse asyncResponse,
			@PathParam("hostname") @NotNull(message = "you must specify a hostname") String hostname)
			throws InterruptedException {

		if (pollingClients.containsKey(hostname)) {
			log.error("rejecting long polling for hostname " + hostname);
			asyncResponse.resume(error(hostname + " already connected for long polling"));
			return;
		}

		log.info("client " + hostname + " connected for long polling");

		asyncResponse.setTimeoutHandler(new TimeoutHandler() {
			@Override
			public void handleTimeout(AsyncResponse asyncResponse) {
				pollingClients.remove(hostname);
				asyncResponse.resume(error(Response.Status.REQUEST_TIMEOUT, "timeout"));
			}
		});

		asyncResponse.setTimeout(60, TimeUnit.SECONDS);
		pollingClients.put(hostname, asyncResponse);
	}

	@GET
	@Path("/{hostname}/command/{command}")
	public Response command(@PathParam("hostname") @NotNull(message = "you must specify a hostname") String hostname,
			@PathParam("command") @NotNull(message = "you must specify a commnand") ServerCommand.Cmd command) {
		AsyncResponse response = pollingClients.get(hostname);
		if (response == null) {
			log.error("client " + hostname + " not connected");
			return error("client not connected");
		}

		pollingClients.remove(hostname);
		response.resume(new ServerCommand(command));

		return success();
	}

	@GET
	@Path("/{hostname}")
	public AgentInfo getAgent(
			@PathParam("hostname") @NotNull(message = "you must specify a hostname") String hostname) {
		AgentInfo info = null;

		AgentReport lastReport = PersistenceClient.getInstance().getLastReport(hostname);
		if (lastReport != null) {
			info = new AgentInfo(hostname);
			info.setLastReportId(lastReport.getId());
		}
		validateNotNull(info);

		info.setConnected(pollingClients.containsKey(hostname));

		return info;
	}

	@GET
	public List<AgentInfo> getAgents() {
		List<AgentInfo> agentsInfo = PersistenceClient.getInstance().getAgentsInfo();

		// a bit of post-processing to add the connected flag
		for (AgentInfo agentInfo : agentsInfo)
			agentInfo.setConnected(pollingClients.containsKey(agentInfo.getHostname()));

		return agentsInfo;
	}

	@GET
	@Path("/{hostname}/config")
	public Map<String, String> info(
			@PathParam("hostname") @NotNull(message = "you must specify a hostname") String hostname) {
		Map<String, String> map = new HashMap<>();
		map.put("config_git_url", getRepositoryURL());
		map.put("config_git_branch", getRepositoryBranch());
		return map;
	}
}
