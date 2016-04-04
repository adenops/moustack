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

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.server.client.PersistenceClient;
import com.adenops.moustack.server.rest.model.AgentStatus;

@Api(value = "/status")
@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class APIStatus extends APIBase {
	private static final Logger log = LoggerFactory.getLogger(APIStatus.class);

	@POST
	public void postStatus(@Valid AgentStatus status) {
		log.info("received status " + status.getStatus() + " from " + status.getHostname());
		PersistenceClient.getInstance().update(status);
	}

	@GET
	@Path("/{hostname}")
	public AgentStatus getStatus(
			@PathParam("hostname") @NotNull(message = "you must specify a hostname") String hostname) {
		AgentStatus status = PersistenceClient.getInstance().getAgentStatus(hostname);
		validateNotNull(status);
		return status;
	}

	@GET
	public List<AgentStatus> getStatuses() {
		List<AgentStatus> statuses = PersistenceClient.getInstance().getAgentsStatuses();
		return statuses;
	}
}
