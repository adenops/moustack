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

package com.adenops.moustack.agent.module;

import java.util.List;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.Stage;
import com.adenops.moustack.agent.client.Clients;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.docker.Container;
import com.adenops.moustack.agent.util.DeploymentUtil;
import com.adenops.moustack.agent.util.SystemCtlUtil;
import com.adenops.moustack.agent.util.YumUtil;

/**
 *
 * @author jb
 *
 *         role: role from which the module instance is declared. The same module could be used from multiple roles at
 *         the same time...
 *
 */
public class BaseModule {
	protected final Stage stage;
	protected final String name;
	protected final String role;
	protected final List<String> packages;
	protected final List<String> files;
	protected final List<String> services;
	protected final List<Container> containers;

	public BaseModule(String name, Stage stage, String role, List<String> files, List<String> packages,
			List<String> services, List<Container> containers) {
		this.name = name;
		this.stage = stage;
		this.role = role;
		this.files = files;
		this.packages = packages;
		this.services = services;
		this.containers = containers;
	}

	public boolean deploy(StackConfig stack) throws DeploymentException {
		boolean changed = deployHost(stack);
		changed |= deployContainers(stack);
		return changed;
	}

	protected boolean deployHost(StackConfig stack) throws DeploymentException {
		boolean changed = deployHostConfig(stack);
		changed |= SystemCtlUtil.startServices(changed, services);
		return changed;
	}

	protected boolean deployHostConfig(StackConfig stack) throws DeploymentException {
		boolean changed = YumUtil.install(packages.toArray(new String[packages.size()]));
		changed |= DeploymentUtil.deployRoleFiles(stack, name, role, files);
		return changed;
	}

	protected boolean deployContainers(StackConfig stack) throws DeploymentException {
		boolean changed = deployContainersConfig(stack);
		if (changed)
			Clients.getDockerClient().startOrRestartContainers(containers);
		return changed;
	}

	protected boolean deployContainersConfig(StackConfig stack) throws DeploymentException {
		boolean changed = false;
		for (Container container : containers) {

			changed |= DeploymentUtil.deployContainerFiles(stack, name, role, container.getFiles());
			changed |= Clients.getDockerClient().containerCheckUpdate(container);
		}
		return changed;
	}

	protected Container getContainer(String name) throws DeploymentException {
		for (Container container : containers) {
			if (container.getName().equalsIgnoreCase(name))
				return container;
		}
		throw new DeploymentException("container " + name + " not found");
	}

	public List<String> getPackages() {
		return packages;
	}

	public List<String> getFiles() {
		return files;
	}

	public List<String> getServices() {
		return services;
	}

	public Stage getStage() {
		return stage;
	}

	public String getName() {
		return name;
	}

	public String getRole() {
		return role;
	}

	public List<Container> getContainers() {
		return containers;
	}
}
