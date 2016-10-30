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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.model.openstack.BaseResponse;
import com.adenops.moustack.agent.model.openstack.keystone.Domain;
import com.adenops.moustack.agent.model.openstack.keystone.Endpoint;
import com.adenops.moustack.agent.model.openstack.keystone.OSAuth;
import com.adenops.moustack.agent.model.openstack.keystone.Project;
import com.adenops.moustack.agent.model.openstack.keystone.Role;
import com.adenops.moustack.agent.model.openstack.keystone.Service;
import com.adenops.moustack.agent.model.openstack.keystone.ServiceResponse;
import com.adenops.moustack.agent.model.openstack.keystone.TokenResponse;
import com.adenops.moustack.agent.model.openstack.keystone.User;
import com.adenops.moustack.agent.model.openstack.keystone.UserResponse;

/*
 *  TODO: use a proxy or something to avoid duplication of the retry logic
 */
public class KeystoneClient extends AbstractOpenStackClient {
	private static final Logger log = LoggerFactory.getLogger(KeystoneClient.class);

	public KeystoneClient(StackConfig stack) throws DeploymentException {
		super("keystone", String.format("http://%s:35357/v3", stack.get(StackProperty.SERVICES_ADMIN_IP)));
		token = stack.get(StackProperty.KEYSTONE_ADMIN_TOKEN);
	}

	public User getUser(StackConfig stack, String name) throws DeploymentException {
		return getSingle(stack, "users", User.class, "users", Collections.singletonMap("name", name));
	}

	public Role getRole(StackConfig stack, String name) throws DeploymentException {
		return getSingle(stack, "roles", Role.class, "roles", Collections.singletonMap("name", name));
	}

	public Project getProject(StackConfig stack, String name) throws DeploymentException {
		return getSingle(stack, "projects", Project.class, "projects", Collections.singletonMap("name", name));
	}

	public Domain getDomain(StackConfig stack, String name) throws DeploymentException {
		return getSingle(stack, "domains", Domain.class, "domains", Collections.singletonMap("name", name));
	}

	public Service getService(StackConfig stack, String name) throws DeploymentException {
		return getSingle(stack, "services", Service.class, "services", Collections.singletonMap("name", name));
	}

	public Endpoint getEndpoint(StackConfig stack, String serviceId, String _interface) throws DeploymentException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put("service_id", serviceId);
		parameters.put("interface", _interface);
		return getSingle(stack, "endpoints", Endpoint.class, "endpoints", parameters);
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
					// lollol
					post(UserResponse.class, "auth/tokens", "auth", new OSAuth(name, password, domainId));

					// no exception, authentication success
					return false;
				} catch (Exception e) {
					log.info("password  changed for user " + name);
				}
			} else {
				// if attributes changed but not password, we can just update
				// them
				log.info("updating user " + name);
				patch(stack, String.format("users/%s", user.getId()), "user",
						new User(name, description, email, password, projectId, domainId));
				return true;
			}

			// delete the user because we cannot update the password
			log.info("deleting user " + name + " (for password update)");
			delete(String.format("users/%s", user.getId()));
		}

		log.info("creating user " + name);
		post(UserResponse.class, "users", "user", new User(name, description, email, password, projectId, domainId));

		return true;
	}

	public boolean createRole(StackConfig stack, String name) throws DeploymentException {
		if (getRole(stack, name) != null) {
			log.debug("role " + name + " already exists");
			return false;
		}
		log.info("creating role " + name);
		post(BaseResponse.class, "roles", "role", new Role(name));
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
		post(BaseResponse.class, "domains", "domain", new Domain(name, description));
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
		post(BaseResponse.class, "projects", "project", new Project(name, description));
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
				patch(stack, String.format("services/%s", service.getId()), "service",
						new Service(name, description, type));
				changed = true;
			}
		} else {
			log.info("creating service " + name + " of type " + type);
			ServiceResponse response = post(ServiceResponse.class, "services", "service",
					new Service(name, description, type));
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
			patch(stack, String.format("endpoints/%s", endpoint.getId()), "endpoint",
					new Endpoint(serviceId, _interface, stack.get(StackProperty.REGION), url));

			return true;
		}
		log.info("creating endpoint " + url + " of type " + _interface);
		post(BaseResponse.class, "endpoints", "endpoint",
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

		boolean hasRole = head(
				String.format("projects/%s/users/%s/roles/%s", project.getId(), user.getId(), role.getId()), null);

		if (hasRole) {
			if (log.isTraceEnabled())
				log.trace("user " + userName + " already has " + roleName + " role in project " + projectName);
			return false;
		}

		log.info("granting " + roleName + " role to " + userName + " in project " + projectName);
		put(String.format("projects/%s/users/%s/roles/%s", project.getId(), user.getId(), role.getId()));

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

		boolean hasRole = head(
				String.format("domains/%s/users/%s/roles/%s", domain.getId(), user.getId(), role.getId()), null);

		if (hasRole) {
			if (log.isTraceEnabled())
				log.trace("user " + userName + " already has " + roleName + " role in domain " + domainName);
			return false;
		}

		log.info("granting " + roleName + " role to " + userName + " in domain " + domainName);
		put(String.format("domains/%s/users/%s/roles/%s", domain.getId(), user.getId(), role.getId()));

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

	public TokenResponse getAdminToken(StackConfig stack) throws DeploymentException {
		return post(TokenResponse.class, "auth/tokens", "auth", new OSAuth(stack.get(StackProperty.KEYSTONE_ADMIN_USER),
				stack.get(StackProperty.KEYSTONE_ADMIN_PASSWORD), User.DEFAULT_DOMAIN_ID));
	}
}
