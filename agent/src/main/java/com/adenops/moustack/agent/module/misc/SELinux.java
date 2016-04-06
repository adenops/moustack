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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;
import com.adenops.moustack.agent.module.SystemModule;
import com.adenops.moustack.agent.util.ProcessUtil;

public class SELinux extends SystemModule {
	private static final Logger log = LoggerFactory.getLogger(SELinux.class);

	public SELinux(String name, List<String> files, List<String> packages, List<String> services) {
		super(name, files, packages, services);
	}

	@Override
	public boolean deploy(StackConfig stack) throws DeploymentException {
		boolean changed = super.deploy(stack);
		if (changed)
			ProcessUtil.execute("setenforce", stack.get(StackProperty.SELINUX_POLICY));
		return changed;
	}
}
