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

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentEnvironment;
import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.deployment.DeploymentFile;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.util.DeploymentUtil;
import com.github.dockerjava.api.model.Capability;

public class ContainerModule extends BaseModule {
	private static final Logger log = LoggerFactory.getLogger(ContainerModule.class);

	private final String image;
	private final List<DeploymentFile> files;
	private final List<String> environments;
	private final List<Volume> volumes;
	private final List<Capability> capabilities;
	private final boolean privileged;
	private final List<String> devices;
	private final boolean syslog;

	public ContainerModule(String name, String image, List<DeploymentFile> files, List<String> environments,
			List<Volume> volumes, List<Capability> capabilities, boolean privileged, List<String> devices,
			boolean syslog) {
		super(name);
		this.image = image;
		this.files = files;
		this.environments = environments;
		this.volumes = volumes;
		this.capabilities = capabilities;
		this.privileged = privileged;
		this.devices = devices;
		this.syslog = syslog;
	}

	/*
	 * we use unmodifiable lists because we only use this constructor for
	 * temporary containers
	 *
	 * TODO: this has been migrated from the old container logic, we need to re-evaluate if the module muste be used in this case.
	 *
	 */
	public ContainerModule(String name, ContainerModule container) {
		super(name);
		this.image = container.image;
		this.environments = Collections.unmodifiableList(container.environments);
		this.files = Collections.unmodifiableList(container.files);
		this.volumes = Collections.unmodifiableList(container.volumes);
		this.capabilities = Collections.unmodifiableList(container.capabilities);
		this.privileged = container.privileged;
		this.devices = container.devices;
		this.syslog = container.isSyslog();
	}

	@Override
	public boolean deploy(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = deployConfig(env);
		if (changed)
			env.getDockerClient().startOrRestartContainer(this);
		return changed;
	}

	@Override
	protected boolean deployConfig(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = false;
		changed |= DeploymentUtil.deployFiles(env.getStack(), name, files);
		changed |= env.getDockerClient().containerCheckUpdate(this);
		return changed;
	}

	@Override
	public void validate(DeploymentEnvironment env) throws DeploymentException {
		log.debug("validating container " + name);
		if (!env.getDockerClient().containerIsRunning(this))
			throw new DeploymentException("container " + name + " is not running");
	}

	public String getImage() {
		return image;
	}

	public List<Volume> getVolumes() {
		return volumes;
	}

	public List<Capability> getCapabilities() {
		return capabilities;
	}

	public boolean isPrivileged() {
		return privileged;
	}

	public List<String> getDevices() {
		return devices;
	}

	public boolean isSyslog() {
		return syslog;
	}

	public List<String> getEnvironments() {
		return environments;
	}

	public List<DeploymentFile> getFiles() {
		return files;
	}

	@Override
	public String getType() {
		return "container";
	}
}
