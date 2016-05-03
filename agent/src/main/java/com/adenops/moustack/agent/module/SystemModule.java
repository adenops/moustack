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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentEnvironment;
import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.util.DeploymentUtil;
import com.adenops.moustack.agent.util.SystemCtlUtil;

/**
 *
 * @author jb
 *
 */
public class SystemModule extends BaseModule {
	private static final Logger log = LoggerFactory.getLogger(SystemModule.class);

	protected final List<String> packages;
	protected final List<String> files;
	protected final List<String> services;

	public SystemModule(String name, List<String> files, List<String> packages, List<String> services) {
		super(name);
		this.files = files;
		this.packages = packages;
		this.services = services;
	}

	@Override
	public boolean deploy(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = deployConfig(env);
		changed |= SystemCtlUtil.startServices(changed, services);
		return changed;
	}

	@Override
	protected boolean deployConfig(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = env.getPackagingClient().install(packages.toArray(new String[packages.size()]));
		changed |= DeploymentUtil.deploySystemFiles(env.getStack(), name, files);
		return changed;
	}

	@Override
	public void validate(DeploymentEnvironment env) throws DeploymentException {
		for (String service : services) {
			log.debug("validating service " + service);
			if (!SystemCtlUtil.unitIsActive(service))
				throw new DeploymentException("service " + service + " is not running");
		}
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

	@Override
	public String getType() {
		return "system";
	}
}
