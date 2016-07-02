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

package com.adenops.moustack.agent.module.misc;

import java.io.File;
import java.util.List;

import com.adenops.moustack.agent.DeploymentEnvironment;
import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.deployment.DeploymentFile;
import com.adenops.moustack.agent.module.SystemModule;
import com.adenops.moustack.agent.util.ProcessUtil;

/**
 * Notes: prepare the keys:
 *
 * apt-key list
 * apt-key export 2C52609D > etc/pki/apt-gpg/docker.gpg
 */
public class AptRepos extends SystemModule {
	public AptRepos(String name, List<DeploymentFile> files, List<String> packages, List<String> services) {
		super(name, files, packages, services);
	}

	@Override
	public boolean deploy(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = super.deploy(env);
		if (changed) {
			// import gpg keys
			for (File file : new File("/etc/pki/apt-gpg").listFiles())
				ProcessUtil.execute("apt-key", "add", file.getAbsolutePath());

			// clean cache
			ProcessUtil.execute("apt-get", "clean");

			// update cache
			ProcessUtil.execute("apt-get", "update");
		}
		return changed;
	}
}
