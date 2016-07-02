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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentEnvironment;
import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.deployment.DeploymentFile;
import com.adenops.moustack.agent.model.exec.ExecResult;
import com.adenops.moustack.agent.module.SystemModule;
import com.adenops.moustack.agent.util.DeploymentUtil;
import com.adenops.moustack.agent.util.ProcessUtil;
import com.adenops.moustack.agent.util.SystemCtlUtil;

public class Shell extends SystemModule {
	private static final Logger log = LoggerFactory.getLogger(Shell.class);

	public Shell(String name, List<DeploymentFile> files, List<String> packages, List<String> services) {
		super(name, files, packages, services);
	}

	@Override
	public boolean deploy(DeploymentEnvironment env) throws DeploymentException {
		boolean changed = env.getPackagingClient().install(packages.toArray(new String[packages.size()]));

		for (DeploymentFile file : files) {
			boolean fileChanged = DeploymentUtil.deployFiles(env.getStack(), name, files);
			if (fileChanged) {
				String scriptPath = file.getTarget();
				log.info("executing {}", scriptPath);

				ExecResult result = ProcessUtil.execute("/bin/sh", scriptPath);

				// TODO: this is a temporary solution to display stdout, we should be able to find a better solution and
				// try to log live
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
						result.getStdout().toByteArray())));
				String line;
				try {
					while ((line = bufferedReader.readLine()) != null) {
						log.info(line);
					}
				} catch (IOException e) {
					log.error("error while reading command stdout: " + e.getMessage());
				}
			}
			changed |= fileChanged;
		}

		changed |= SystemCtlUtil.startServices(changed, services);
		return changed;
	}
}
