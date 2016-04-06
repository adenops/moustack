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
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.util.DeploymentUtil;
import com.adenops.moustack.agent.util.SystemCtlUtil;
import com.adenops.moustack.agent.util.YumUtil;

/**
 *
 * @author jb
 *
 */
public class SystemModule extends BaseModule {
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
	public boolean deploy(StackConfig stack) throws DeploymentException {
		boolean changed = deployConfig(stack);
		changed |= SystemCtlUtil.startServices(changed, services);
		return changed;
	}

	@Override
	protected boolean deployConfig(StackConfig stack) throws DeploymentException {
		boolean changed = YumUtil.install(packages.toArray(new String[packages.size()]));
		changed |= DeploymentUtil.deploySystemFiles(stack, name, files);
		return changed;
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