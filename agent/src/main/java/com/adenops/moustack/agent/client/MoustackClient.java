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

import java.util.Date;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.model.moustack.AgentReport;
import com.adenops.moustack.agent.model.moustack.AgentStatus;
import com.adenops.moustack.agent.model.moustack.ServerCommand;
import com.adenops.moustack.agent.model.openstack.BaseResponse;

public class MoustackClient extends AbstractRestClient {
	private static final Logger log = LoggerFactory.getLogger(MoustackClient.class);

	private static final String API_AGENT = "rest/agent";
	private static final String API_REPORT = "rest/report";
	private static final String API_STATUS = "rest/status";

	private static final MoustackClient instance = new MoustackClient();

	public static MoustackClient getInstance() {
		return instance;
	}

	private MoustackClient() {
		super("moustack", AgentConfig.getInstance().getServer(), 3, 1);

		// setup basic authentication
		if (!StringUtils.isBlank(AgentConfig.getInstance().getUser())
				&& !StringUtils.isBlank(AgentConfig.getInstance().getPassword())) {
			HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(AgentConfig.getInstance().getUser(),
					AgentConfig.getInstance().getPassword());
			webTarget.register(feature);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> getRepositoryInfo() throws DeploymentException {
		return get(Map.class, String.join("/", API_AGENT, AgentConfig.getInstance().getId(), "config"), null);
	}

	public void postReport(AgentReport.ReasonEnum reason, String content) {
		AgentReport report = new AgentReport();
		report.setDate(new Date());
		report.setHostname(AgentConfig.getInstance().getId());
		report.setReason(reason);
		report.setContent(content);

		try {
			post(BaseResponse.class, API_REPORT, report);
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
			post(BaseResponse.class, API_STATUS, agentStatus);
		} catch (DeploymentException e) {
			// we don't want to handle this error later
			log.error("could not post report: " + e.getMessage());
		}
	}

	public Response longPoll() throws DeploymentException {
		return longPoll(String.join("/", API_AGENT, AgentConfig.getInstance().getId(), "poll"));
	}

	// TODO: let's see later if we can improve that
	public ServerCommand readCommand(Response response) {
		if (response == null)
			return null;
		return response.readEntity(ServerCommand.class);
	}
}
