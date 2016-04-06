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

package com.adenops.moustack.agent;

import java.util.HashMap;
import java.util.Map;

import com.adenops.moustack.agent.module.BaseModule;

public class ModuleRegistry {
	private final static Map<String, Class<? extends BaseModule>> registry = new HashMap<>();

	static {
		registry.put("network", com.adenops.moustack.agent.module.misc.Network.class);
		registry.put("sysctl", com.adenops.moustack.agent.module.misc.Sysctl.class);
		registry.put("selinux", com.adenops.moustack.agent.module.misc.SELinux.class);
		registry.put("modprobe", com.adenops.moustack.agent.module.misc.Modprobe.class);

		registry.put("keystone", com.adenops.moustack.agent.module.controller.Keystone.class);
		registry.put("neutron-controller", com.adenops.moustack.agent.module.controller.Neutron.class);
		registry.put("glance", com.adenops.moustack.agent.module.controller.Glance.class);
		registry.put("cinder", com.adenops.moustack.agent.module.controller.Cinder.class);
		registry.put("nova-controller", com.adenops.moustack.agent.module.controller.Nova.class);
		registry.put("ceilometer", com.adenops.moustack.agent.module.controller.Ceilometer.class);
		registry.put("heat", com.adenops.moustack.agent.module.controller.Heat.class);
		registry.put("yumrepos", com.adenops.moustack.agent.module.misc.YumRepos.class);
	}

	public static Class<? extends BaseModule> getRegistered(String name) {
		return registry.get(name);
	}
}
