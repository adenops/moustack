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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.model.keystone.Domain;
import com.adenops.moustack.agent.model.keystone.Domains;
import com.adenops.moustack.agent.model.keystone.Endpoint;
import com.adenops.moustack.agent.model.keystone.Endpoints;
import com.adenops.moustack.agent.model.keystone.Error;
import com.adenops.moustack.agent.model.keystone.IgnoredResponse;
import com.adenops.moustack.agent.model.keystone.OSAuth;
import com.adenops.moustack.agent.model.keystone.OSEntity;
import com.adenops.moustack.agent.model.keystone.Project;
import com.adenops.moustack.agent.model.keystone.Projects;
import com.adenops.moustack.agent.model.keystone.Role;
import com.adenops.moustack.agent.model.keystone.Roles;
import com.adenops.moustack.agent.model.keystone.Service;
import com.adenops.moustack.agent.model.keystone.ServiceResponse;
import com.adenops.moustack.agent.model.keystone.Services;
import com.adenops.moustack.agent.model.keystone.User;
import com.adenops.moustack.agent.model.keystone.UserResponse;
import com.adenops.moustack.agent.model.keystone.Users;
import com.adenops.moustack.agent.util.HttpUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/*
 *  TODO: use a proxy or something to avoid duplication of the retry logic
 */
public class KeystoneClient extends ManagedClient {
	private static final Logger log = LoggerFactory.getLogger(KeystoneClient.class);
	private static final int CONNECTION_MAX_RETRY = 10;
	private static final int CONNECTION_RETRY_SLEEP = 4;

	private final WebTarget webTarget;
	private final Client client;
	private final ObjectMapper mapper;

	protected KeystoneClient(StackConfig stack) throws DeploymentException {
		log.debug("initializing Keystone client");

		JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider().configure(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		client = ClientBuilder.newClient();
		client.register(jacksonJsonProvider);

		// allow patch method
		client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);

		webTarget = client.target(String.format("http://%s:35357/v3", stack.get(StackProperty.SERVICES_PUBLIC_IP)));

		// instanciate a single object mapper to reuse it
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	protected void release() {
		if (client == null)
			return;

		log.debug("closing Keystone client");
		client.close();
	}

	private static boolean stringsEqual(String str1, String str2) {
		if (str1 == null && str2 != null)
			return false;
		if (str1 != null && str2 == null)
			return false;

		if (str1 == null && str2 == null)
			return true;

		return str1.equals(str2);
	}

	public <E> E get(StackConfig stack, Class<E> clazz, String path, Map<String, String> parameters)
			throws DeploymentException {

		WebTarget target = webTarget.path(path);
		if (parameters != null)
			for (Entry<String, String> parameter : parameters.entrySet())
				target = target.queryParam(parameter.getKey(), parameter.getValue());

		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("X-Auth-Token", stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN));

		if (log.isTraceEnabled()) {
			log.trace("GET request: " + target.getUri());
		}
		Response response = null;
		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				response = invocationBuilder.get();
				break;
			} catch (ProcessingException e) {
				log.debug("failed to connect to keystone, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		if (response == null)
			throw new DeploymentException("failed to connect to keystone");

		if (!HttpUtil.isSuccess(response)) {
			Error error = null;
			try {
				error = response.readEntity(Error.class);
			} catch (Exception e) {
			}

			throw new DeploymentException("GET request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + ")"
					+ (error == null ? "" : ": " + error.getError().getMessage()));
		}

		if (log.isTraceEnabled()) {
			String json = response.readEntity(String.class);
			log.trace("GET response: " + json);
			try {
				return mapper.reader(clazz).readValue(json);
			} catch (IOException e) {
				throw new DeploymentException("error while parsing json response", e);
			}
		}

		return response.readEntity(clazz);
	}

	public boolean head(StackConfig stack, String path, Map<String, String> parameters) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		if (parameters != null)
			for (Entry<String, String> parameter : parameters.entrySet())
				target = target.queryParam(parameter.getKey(), parameter.getValue());
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("X-Auth-Token", stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN));

		if (log.isTraceEnabled())
			log.trace("HEAD request: " + target.getUri());

		Response response = null;
		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				response = invocationBuilder.head();
				break;
			} catch (ProcessingException e) {
				log.debug("failed to connect to keystone, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		if (response == null)
			throw new DeploymentException("failed to connect to keystone");

		if (log.isTraceEnabled())
			log.trace("HEAD response: " + response.getStatus());

		if (HttpUtil.isSuccess(response))
			return true;

		if (HttpUtil.isNotFound(response))
			return false;

		Error error = response.readEntity(Error.class);
		throw new DeploymentException("HEAD request " + target.getUri() + " returned HTTP code " + response.getStatus()
				+ " (" + response.getStatusInfo() + "): " + error.getError().getMessage());
	}

	public void put(StackConfig stack, String path) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("X-Auth-Token", stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN));

		if (log.isTraceEnabled())
			log.trace("PUT request: " + target.getUri());

		Response response = null;
		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				response = invocationBuilder.put(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
				break;
			} catch (ProcessingException e) {
				log.debug("failed to connect to keystone, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		if (response == null)
			throw new DeploymentException("failed to connect to keystone");

		if (log.isTraceEnabled())
			log.trace("PUT response: " + response.getStatus());

		if (HttpUtil.isSuccess(response))
			return;

		Error error = response.readEntity(Error.class);
		throw new DeploymentException("HEAD request " + target.getUri() + " returned HTTP code " + response.getStatus()
				+ " (" + response.getStatusInfo() + "): " + error.getError().getMessage());
	}

	public void delete(StackConfig stack, String path) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("X-Auth-Token", stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN));

		if (log.isTraceEnabled())
			log.trace("DELETE request: " + target.getUri());

		Response response = null;
		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				response = invocationBuilder.delete();
				break;
			} catch (ProcessingException e) {
				log.debug("failed to connect to keystone, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		if (response == null)
			throw new DeploymentException("failed to connect to keystone");

		if (log.isTraceEnabled())
			log.trace("DELETE response: " + response.getStatus());

		if (HttpUtil.isSuccess(response))
			return;

		Error error = response.readEntity(Error.class);
		throw new DeploymentException("DELETE request " + target.getUri() + " returned HTTP code "
				+ response.getStatus() + " (" + response.getStatusInfo() + "): " + error.getError().getMessage());
	}

	public <E> E post(StackConfig stack, Class<E> clazz, String path, String key, Object entity)
			throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("X-Auth-Token", stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN));

		if (log.isTraceEnabled()) {
			log.trace("POST request: " + target.getUri());
		}

		// XXX: workaround to ensure we are not posting en entry with an id
		// jackson 2.6 support allowGetters and allowSetters properties for
		// the annotation JsonIgnoreProperties unfortunately jersey-media-json-jackson
		// depends on jackson 2.5.
		if (entity instanceof OSEntity)
			((OSEntity) entity).setId(null);

		Response response = null;
		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				response = invocationBuilder.post(Entity.entity(Collections.singletonMap(key, entity),
						MediaType.APPLICATION_JSON_TYPE));
				break;
			} catch (ProcessingException e) {
				log.debug("failed to connect to keystone, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		if (response == null)
			throw new DeploymentException("failed to connect to keystone");

		if (!HttpUtil.isSuccess(response)) {
			Error error = response.readEntity(Error.class);
			throw new DeploymentException("POST request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + "): " + error.getError().getMessage());
		}

		if (log.isTraceEnabled()) {
			String json = response.readEntity(String.class);
			log.trace("POST response: " + json);
			try {
				return mapper.reader(clazz).readValue(json);
			} catch (IOException e) {
				throw new DeploymentException("error while parsing json response", e);
			}
		}

		return response.readEntity(clazz);
	}

	public void patch(StackConfig stack, String path, String key, Object entity) throws DeploymentException {

		WebTarget target = webTarget.path(path);
		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("X-Auth-Token", stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN));

		if (log.isTraceEnabled()) {
			log.trace("PATCH request: " + target.getUri());
		}

		Response response = null;
		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				response = invocationBuilder.method("PATCH",
						Entity.entity(Collections.singletonMap(key, entity), MediaType.APPLICATION_JSON_TYPE));
				break;
			} catch (ProcessingException e) {
				log.debug("failed to connect to keystone, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		if (response == null)
			throw new DeploymentException("failed to connect to keystone");

		if (!HttpUtil.isSuccess(response)) {
			Error error = response.readEntity(Error.class);
			throw new DeploymentException("PATCH request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + "): " + error.getError().getMessage());
		}
	}

	public User getUser(StackConfig stack, String name) throws DeploymentException {
		Users users = get(stack, Users.class, "users", Collections.singletonMap("name", name));
		if (users.getUsers().isEmpty())
			return null;
		return users.getUsers().get(0);
	}

	public Role getRole(StackConfig stack, String name) throws DeploymentException {
		Roles roles = get(stack, Roles.class, "roles", Collections.singletonMap("name", name));
		if (roles.getRoles().isEmpty())
			return null;
		return roles.getRoles().get(0);
	}

	public Project getProject(StackConfig stack, String name) throws DeploymentException {
		Projects projects = get(stack, Projects.class, "projects", Collections.singletonMap("name", name));
		if (projects.getProjects().isEmpty())
			return null;
		return projects.getProjects().get(0);
	}

	public Domain getDomain(StackConfig stack, String name) throws DeploymentException {
		Domains domains = get(stack, Domains.class, "domains", Collections.singletonMap("name", name));
		if (domains.getDomains().isEmpty())
			return null;
		return domains.getDomains().get(0);
	}

	public Service getService(StackConfig stack, String name) throws DeploymentException {
		Services services = get(stack, Services.class, "services", Collections.singletonMap("name", name));
		if (services.getServices().isEmpty())
			return null;
		return services.getServices().get(0);
	}

	public Endpoint getEndpoint(StackConfig stack, String serviceId, String _interface) throws DeploymentException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put("service_id", serviceId);
		parameters.put("interface", _interface);
		Endpoints endpoints = get(stack, Endpoints.class, "endpoints", parameters);
		if (endpoints.getEndpoints().isEmpty())
			return null;
		return endpoints.getEndpoints().get(0);
	}

	public boolean createUser(StackConfig stack, String name, String description, String email, String password,
			String projectName, String domainName) throws DeploymentException {

		User user = getUser(stack, name);

		Project project = projectName != null ? getProject(stack, projectName) : null;
		if (projectName != null && project == null)
			throw new DeploymentException("project " + projectName + " not found");
		String projectId = project != null ? project.getId() : null;

		Domain domain = domainName != null ? getDomain(stack, domainName) : null;
		if (domainName != null && domain == null)
			throw new DeploymentException("domain " + domainName + " not found");
		String domainId = domain != null ? domain.getId() : User.DEFAULT_DOMAIN_ID;

		if (user != null) {
			log.debug("user " + name + " already exists");

			// checking attributes
			if (stringsEqual(user.getDescription(), description) && stringsEqual(user.getEmail(), email)
					&& stringsEqual(user.getDefault_project_id(), project != null ? project.getId() : null)) {

				try {
					// checking authentication
					log.debug("checking authentication for user " + name);
					post(stack, UserResponse.class, "auth/tokens", "auth", new OSAuth(user.getId(), password));

					// no exception, authentication success
					return false;
				} catch (Exception e) {
					log.info("password  changed for user " + name);
				}
			} else {
				// if attributes changed but not password, we can just update
				// them
				log.info("updating user " + name);
				patch(stack, String.format("users/%s", user.getId()), "user", new User(name, description, email,
						password, projectId, domainId));
				return true;
			}

			// delete the user because we cannot update the password
			log.info("deleting user " + name + " (for password update)");
			delete(stack, String.format("users/%s", user.getId()));
		}

		log.info("creating user " + name);
		post(stack, UserResponse.class, "users", "user", new User(name, description, email, password, projectId,
				domainId));

		return true;
	}

	public boolean createRole(StackConfig stack, String name) throws DeploymentException {
		if (getRole(stack, name) != null) {
			log.debug("role " + name + " already exists");
			return false;
		}
		log.info("creating role " + name);
		post(stack, IgnoredResponse.class, "roles", "role", new Role(name));
		return true;
	}

	public boolean createDomain(StackConfig stack, String name, String description) throws DeploymentException {
		Domain domain = getDomain(stack, name);
		if (domain != null) {
			log.debug("domain " + name + " already exists");

			if (stringsEqual(description, domain.getDescription()))
				return false;

			log.info("updating domain " + name);
			patch(stack, String.format("domains/%s", domain.getId()), "domain", new Domain(name, description));
			return true;
		}

		log.info("creating domain " + name);
		post(stack, IgnoredResponse.class, "domains", "domain", new Domain(name, description));
		return true;
	}

	public boolean createProject(StackConfig stack, String name, String description) throws DeploymentException {
		Project project = getProject(stack, name);
		if (project != null) {
			log.debug("project " + name + " already exists");

			if (stringsEqual(description, project.getDescription()))
				return false;

			log.info("updating project " + name);
			patch(stack, String.format("projects/%s", project.getId()), "project", new Project(name, description));
			return true;
		}

		log.info("creating project " + name);
		post(stack, IgnoredResponse.class, "projects", "project", new Project(name, description));
		return true;
	}

	public boolean createService(StackConfig stack, String name, String description, String type, String publicURL,
			String internalURL, String adminURL) throws DeploymentException {
		Service service = getService(stack, name);
		boolean changed = false;
		if (service != null) {
			log.debug("service " + name + " already exists");

			if (!stringsEqual(description, service.getDescription()) || !stringsEqual(type, service.getType())) {
				log.info("updating service " + name);
				patch(stack, String.format("services/%s", service.getId()), "service", new Service(name, description,
						type));
				changed = true;
			}
		} else {
			log.info("creating service " + name + " of type " + type);
			ServiceResponse response = post(stack, ServiceResponse.class, "services", "service", new Service(name,
					description, type));
			changed = true;
			service = response.getService();
		}
		changed |= createEndpoint(stack, service.getId(), "public",
				String.format(publicURL, stack.get(StackProperty.SERVICES_PUBLIC_IP)));
		changed |= createEndpoint(stack, service.getId(), "internal",
				String.format(internalURL, stack.get(StackProperty.SERVICES_INTERNAL_IP)));
		changed |= createEndpoint(stack, service.getId(), "admin",
				String.format(adminURL, stack.get(StackProperty.SERVICES_ADMIN_IP)));

		return changed;
	}

	// TODO: add update (based on object comparison)
	public boolean createEndpoint(StackConfig stack, String serviceId, String _interface, String url)
			throws DeploymentException {
		Endpoint endpoint = getEndpoint(stack, serviceId, _interface);
		if (endpoint != null) {
			log.debug("endpoint " + url + " already exists");

			if (stringsEqual(url, endpoint.getUrl()))
				return false;

			log.info("updating endpoint " + url);
			patch(stack, String.format("endpoints/%s", endpoint.getId()), "endpoint", new Endpoint(serviceId,
					_interface, stack.get(StackProperty.REGION), url));

			return true;
		}
		log.info("creating endpoint " + url + " of type " + _interface);
		post(stack, IgnoredResponse.class, "endpoints", "endpoint",
				new Endpoint(serviceId, _interface, stack.get(StackProperty.REGION), url));
		return true;
	}

	public boolean grantProjectRole(StackConfig stack, String userName, String projectName, String roleName)
			throws DeploymentException {
		User user = getUser(stack, userName);

		if (user == null)
			throw new DeploymentException("user " + userName + " not found");

		Project project = getProject(stack, projectName);
		if (project == null)
			throw new DeploymentException("project " + projectName + " not found");

		Role role = getRole(stack, roleName);
		if (role == null)
			throw new DeploymentException("role " + roleName + " not found");

		boolean hasRole = head(stack,
				String.format("projects/%s/users/%s/roles/%s", project.getId(), user.getId(), role.getId()), null);

		if (hasRole) {
			if (log.isTraceEnabled())
				log.trace("user " + userName + " already has " + roleName + " role in project " + projectName);
			return false;
		}

		log.info("granting " + roleName + " role to " + userName + " in project " + projectName);
		put(stack, String.format("projects/%s/users/%s/roles/%s", project.getId(), user.getId(), role.getId()));

		return true;
	}

	public boolean grantDomainRole(StackConfig stack, String userName, String domainName, String roleName)
			throws DeploymentException {
		User user = getUser(stack, userName);

		if (user == null)
			throw new DeploymentException("user " + userName + " not found");

		Domain domain = getDomain(stack, domainName);
		if (domain == null)
			throw new DeploymentException("domain " + domainName + " not found");

		Role role = getRole(stack, roleName);
		if (role == null)
			throw new DeploymentException("role " + roleName + " not found");

		boolean hasRole = head(stack,
				String.format("domains/%s/users/%s/roles/%s", domain.getId(), user.getId(), role.getId()), null);

		if (hasRole) {
			if (log.isTraceEnabled())
				log.trace("user " + userName + " already has " + roleName + " role in domain " + domainName);
			return false;
		}

		log.info("granting " + roleName + " role to " + userName + " in domain " + domainName);
		put(stack, String.format("domains/%s/users/%s/roles/%s", domain.getId(), user.getId(), role.getId()));

		return true;
	}

	public boolean grantProjectRole(StackConfig stack, StackProperty userKey, StackProperty projectKey,
			StackProperty roleKey) throws DeploymentException {

		String user = stack.get(userKey);
		String project = stack.get(projectKey);
		String role = stack.get(roleKey);

		return grantProjectRole(stack, user, project, role);
	}

	public boolean grantDomainRole(StackConfig stack, StackProperty userKey, StackProperty domainKey,
			StackProperty roleKey) throws DeploymentException {

		String user = stack.get(userKey);
		String domain = stack.get(domainKey);
		String role = stack.get(roleKey);

		return grantDomainRole(stack, user, domain, role);
	}

	public boolean createProjectUser(StackConfig stack, StackProperty userKey, String description, String email,
			StackProperty passwordKey, StackProperty projectKey) throws DeploymentException {

		String user = stack.get(userKey);
		String password = stack.get(passwordKey);
		String project = stack.get(projectKey);

		return createUser(stack, user, description, email, password, project, null);
	}

	public boolean createDomainUser(StackConfig stack, StackProperty userKey, String description, String email,
			StackProperty passwordKey, StackProperty domainKey) throws DeploymentException {

		String user = stack.get(userKey);
		String password = stack.get(passwordKey);
		String domain = stack.get(domainKey);

		return createUser(stack, user, description, email, password, null, domain);
	}
}
