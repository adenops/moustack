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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.openstack.BaseResponse;
import com.adenops.moustack.agent.model.openstack.Error;
import com.adenops.moustack.agent.model.openstack.OSEntity;
import com.adenops.moustack.agent.util.HttpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;

public abstract class AbstractOpenStackClient extends AbstractRestClient {
	private static final Logger log = LoggerFactory.getLogger(AbstractOpenStackClient.class);
	protected String token;

	protected AbstractOpenStackClient(String name, String url) {
		super(name, url, 10, 4);
	}

	@Override
	protected void setHeaders(Builder invocationBuilder) {
		super.setHeaders(invocationBuilder);
		invocationBuilder.header("X-Auth-Token", token);
	}

	@Override
	protected void preprocessResponse(Response response, Status... extraAllowedStatuses) throws DeploymentException {
		log.trace("received response: {}", response);

		if (HttpUtil.isSuccess(response))
			return;

		// we allow extra statuses because sometimes an HTTP error is expected (for example 404)
		for (Status status : extraAllowedStatuses) {
			if (status.getStatusCode() == response.getStatus()) {
				log.trace("returned HTTP status {} but this is expected", status);
				return;
			}
		}

		// try to parse the error returned by the API
		Error error = readEntity(response, Error.class);
		if (error == null)
			throw new DeploymentException("API returned error: HTTP status " + response.getStatus());

		String message = error.getMessage();
		String type = error.getType();

		if (log.isDebugEnabled()) {
			try {
				log.debug("received error from the API: \n{}",
						mapper.writerWithDefaultPrettyPrinter().writeValueAsString(error));
			} catch (JsonProcessingException e) {
			}
		}

		throw new DeploymentException("API returned error: {type: " + type + ", message: " + message + "}");
	}

	/**
	 * Workaround to extract a single result from the OpenStack API responses
	 */
	@SuppressWarnings("rawtypes")
	public <E> E getSingle(StackConfig stack, String key, Class<E> clazz, String path, Map<String, String> parameters)
			throws DeploymentException {
		Map map = get(Map.class, path, parameters);
		if (map == null)
			throw new DeploymentException(path + ": could not extract map from response");

		Object results = map.get(key);
		if (results == null || !(results instanceof List))
			throw new DeploymentException(path + ": could not extract list from response");

		List resultsList = (List) results;
		if (resultsList.size() > 1)
			throw new DeploymentException(path + ": received more than one result");
		if (resultsList.isEmpty())
			return null;

		Object result = resultsList.get(0);
		try {
			return mapper.convertValue(result, clazz);
		} catch (IllegalArgumentException e) {
			throw new DeploymentException(path + ": could not deserialize to class " + clazz);
		}
	}

	public <E extends BaseResponse> E post(Class<E> clazz, String path, String key, Object object)
			throws DeploymentException {
		// XXX: workaround to ensure we are not posting en entry with an id
		// jackson 2.6 support allowGetters and allowSetters properties for
		// the annotation JsonIgnoreProperties unfortunately jersey-media-json-jackson
		// depends on jackson 2.5.
		if (object instanceof OSEntity)
			((OSEntity) object).setId(null);

		Object data = key == null ? object : Collections.singletonMap(key, object);
		return super.post(clazz, path, data);
	}

	public void patch(StackConfig stack, String path, String key, Object object) throws DeploymentException {
		Object data = key == null ? object : Collections.singletonMap(key, object);
		super.patch(path, data);
	}
}
