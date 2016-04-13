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
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.model.openstack.BaseResponse;
import com.adenops.moustack.agent.util.HttpUtil;
import com.adenops.moustack.agent.util.HttpUtil.TrustAllHostNameVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public abstract class AbstractRestClient extends ManagedClient {
	private static final Logger log = LoggerFactory.getLogger(AbstractRestClient.class);

	protected final String name;
	protected WebTarget webTarget;
	protected final Client client;
	protected final ObjectMapper mapper;
	private final int maxRetries;
	private final int retryWait;

	protected AbstractRestClient(String name, String url, int maxRetries, int retryWait) {
		log.debug("initializing REST client [{}]", name);

		this.name = name;
		this.maxRetries = maxRetries;
		this.retryWait = retryWait;

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

		// add our Jackson customizations
		JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider().configure(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		// build the client and register Jackson
		client = clientBuilder.build();
		client.register(jacksonJsonProvider);

		// allow patch method
		client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);

		// create the base target
		webTarget = client.target(url);

		// instanciate a single object mapper to reuse it
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	protected void release() {
		if (client == null)
			return;

		log.debug("closing REST client [{}]", name);
		client.close();
	}

	protected void setHeaders(Builder invocationBuilder) {
		// no default headers here, this is a placeholder for the children
	}

	/**
	 * Simple wrapper to catch parsing exception and display meaningful error message
	 */
	protected <T> T readEntity(Response response, Class<T> entityType) throws DeploymentException {
		// if we are in debug, use entity buffering to be able to parse the response a second time to display the
		// original JSON
		if (log.isDebugEnabled())
			response.bufferEntity();

		if (log.isTraceEnabled()) {
			try {
				Object jsonEntity = response.readEntity(Object.class);
				String json;
				if (jsonEntity == null)
					json = "[NO CONTENT]";
				else
					json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonEntity);
				log.trace("received response:\n{}", json);
			} catch (JsonProcessingException e) {
			}
		}

		try {
			return response.readEntity(entityType);
		} catch (ProcessingException e) {
			if (log.isDebugEnabled()) {
				// a bit messy but it's nice to have pretty print
				try {
					log.debug("cannot map json to class {}:\n{}", entityType, mapper.writerWithDefaultPrettyPrinter()
							.writeValueAsString(response.readEntity(Object.class)));
				} catch (JsonProcessingException e1) {
				}
			}
			throw new DeploymentException("error during deserialization to class " + entityType, e);
		}
	}

	/**
	 * A few operation we always want to do with every request (logging, ...)
	 */
	protected void preprocessRequest(String method, WebTarget target, Object data) {
		if (!log.isTraceEnabled())
			return;

		log.trace("{} request: {}", method, target.getUri());

		if (data == null)
			return;

		try {
			log.trace("payload:\n{}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
		} catch (JsonProcessingException e) {
		}
	}

	protected void preprocessRequest(String method, WebTarget target) {
		preprocessRequest(method, target, null);
	}

	/**
	 * A few operation we always want to do with every response (check HTTP status, logging, ...)
	 */
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

		throw new DeploymentException("API returned error: HTTP status " + response.getStatus());
	}

	public <E> E get(Class<E> clazz, String path, Map<String, String> parameters) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		if (parameters != null)
			for (Entry<String, String> parameter : parameters.entrySet())
				target = target.queryParam(parameter.getKey(), parameter.getValue());

		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		setHeaders(invocationBuilder);

		preprocessRequest("GET", target);

		Response response = null;
		for (int i = 0; i <= maxRetries; i++) {
			try {
				response = invocationBuilder.get();
				break;
			} catch (ProcessingException e) {
				if (i == maxRetries)
					throw new DeploymentException("connection error with [" + name + "] API");

				log.debug("failed to connect to [{}], waiting {}s", name, retryWait);
				try {
					Thread.sleep(retryWait * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}

		preprocessResponse(response);

		return readEntity(response, clazz);
	}

	public boolean head(String path, Map<String, String> parameters) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		if (parameters != null)
			for (Entry<String, String> parameter : parameters.entrySet())
				target = target.queryParam(parameter.getKey(), parameter.getValue());
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		setHeaders(invocationBuilder);

		preprocessRequest("HEAD", target);

		Response response = null;
		for (int i = 0; i <= maxRetries; i++) {
			try {
				response = invocationBuilder.head();
				break;
			} catch (ProcessingException e) {
				if (i == maxRetries)
					throw new DeploymentException("connection error with [" + name + "] API");

				log.debug("failed to connect to [{}], waiting {}s", name, retryWait);
				try {
					Thread.sleep(retryWait * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}

		preprocessResponse(response, Status.NOT_FOUND);

		if (HttpUtil.isSuccess(response))
			return true;

		return false;
	}

	public void put(String path) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		setHeaders(invocationBuilder);

		preprocessRequest("PUT", target);

		Response response = null;
		for (int i = 0; i <= maxRetries; i++) {
			try {
				response = invocationBuilder.put(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
				break;
			} catch (ProcessingException e) {
				if (i == maxRetries)
					throw new DeploymentException("connection error with [" + name + "] API");

				log.debug("failed to connect to [{}], waiting {}s", name, retryWait);
				try {
					Thread.sleep(retryWait * 1000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}

		preprocessResponse(response);
	}

	public void delete(String path) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		setHeaders(invocationBuilder);

		preprocessRequest("DELETE", target);

		Response response = null;
		for (int i = 0; i <= maxRetries; i++) {
			try {
				response = invocationBuilder.delete();
				break;
			} catch (ProcessingException e) {
				if (i == maxRetries)
					throw new DeploymentException("connection error with [" + name + "] API");

				log.debug("failed to connect to [{}], waiting {}s", name, retryWait);
				try {
					Thread.sleep(retryWait * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}

		preprocessResponse(response);
	}

	public <E extends BaseResponse> E post(Class<E> clazz, String path, Object data) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		setHeaders(invocationBuilder);

		preprocessRequest("POST", target, data);

		Response response = null;
		for (int i = 0; i <= maxRetries; i++) {
			try {
				response = invocationBuilder.post(Entity.entity(data, MediaType.APPLICATION_JSON_TYPE));
				break;
			} catch (ProcessingException e) {
				if (i == maxRetries)
					throw new DeploymentException("connection error with [" + name + "] API");

				log.debug("failed to connect to [{}], waiting {}s", name, retryWait);
				try {
					Thread.sleep(retryWait * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}

		preprocessResponse(response);

		E result = readEntity(response, clazz);

		if (result == null) {
			// TODO: should it be a warn? and error?
			// XXX: only for moustack server, should be fixed
			log.trace("the server returned no response body");
			return null;
		}

		result.setHeaders(response.getHeaders());
		return result;
	}

	public void patch(String path, Object data) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		setHeaders(invocationBuilder);

		preprocessRequest("PATCH", target, data);

		Response response = null;
		for (int i = 0; i <= maxRetries; i++) {
			try {
				response = invocationBuilder.method("PATCH", Entity.entity(data, MediaType.APPLICATION_JSON_TYPE));
				break;
			} catch (ProcessingException e) {
				if (i == maxRetries)
					throw new DeploymentException("connection error with [" + name + "] API");

				log.debug("failed to connect to [{}], waiting {}s", name, retryWait);
				try {
					Thread.sleep(retryWait * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}

		preprocessResponse(response);
	}

	public Response longPoll(String path) throws DeploymentException {
		WebTarget target = webTarget.path(path);

		Response response;
		try {
			response = target.request(MediaType.APPLICATION_JSON_TYPE).async().get().get();
		} catch (ProcessingException | ExecutionException e) {
			throw new DeploymentException("communication error with the server", e);
		} catch (InterruptedException e) {
			throw new DeploymentException("received interruption during long polling", e);
		}

		// for long polling, we consider timeout not an error
		if (!HttpUtil.isSuccess(response) && !response.getStatusInfo().equals(Response.Status.REQUEST_TIMEOUT)) {
			throw new DeploymentException("GET request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + ")");
		}

		return response;
	}

	// should probably be moved in a more generic library
	protected static boolean stringsEqual(String str1, String str2) {
		if (str1 == null && str2 != null)
			return false;
		if (str1 != null && str2 == null)
			return false;

		if (str1 == null && str2 == null)
			return true;

		return str1.equals(str2);
	}
}
