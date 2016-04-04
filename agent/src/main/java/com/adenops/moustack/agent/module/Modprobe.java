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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.Stage;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.docker.Container;
import com.adenops.moustack.agent.util.PathUtil;
import com.adenops.moustack.agent.util.ProcessUtil;

public class Modprobe extends BaseModule {
	private static final Logger log = LoggerFactory.getLogger(Modprobe.class);

	public Modprobe(String name, Stage stage, String role, List<String> files, List<String> packages,
			List<String> services, List<Container> containers) {
		super(name, stage, role, files, packages, services, containers);
	}

	@Override
	public boolean deployHost(StackConfig stack) throws DeploymentException {
		boolean changed = super.deployHost(stack);

		if (!changed)
			return false;

		for (String file : files) {
			String path = PathUtil.getRoleSourceFilePath(AgentConfig.getInstance(), role, file);
			try {
				for (String entry : FileUtils.readLines(new File(path)))
					ProcessUtil.execute("modprobe", entry);
			} catch (IOException e) {
				throw new DeploymentException("error while reading modprobe file " + path, e);
			}
		}

		return true;
	}
}
