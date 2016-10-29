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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.exec.ExecResult;
import com.adenops.moustack.agent.util.ProcessUtil;

public class DpkgClient extends AbstractPackagingClient {
	private static final Logger log = LoggerFactory.getLogger(DpkgClient.class);

	private static final Map<String, String> APT_ENV = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;
		{
			// prevent services from being automatically started.
			put("RUNLEVEL", "1");
			// prevent interactive package configuration DEBIAN_FRONTEND=noninteractive
			put("DEBIAN_FRONTEND", "noninteractive");
		}
	};

	private void apt(String action, String... packages) throws DeploymentException {
		String[] command = ArrayUtils.addAll(new String[] { "apt-get", action, "--assume-yes", "--quiet" }, packages);
		ProcessUtil.execute(null, null, APT_ENV, false, command);
	}

	// TODO: I couldn't find a clean way (without pipe and with a proper exit code) to check that a package is not
	// installed.
	// With current implementation, if a package has been removed but not purged, it will be considered installed.
	private String[] filterPackages(boolean filterInstalled, String... pkgs) throws DeploymentException {
		if (pkgs.length == 0)
			return pkgs;

		List<String> result = new ArrayList<>();

		for (String pkg : pkgs) {
			// split before = to exclude possible version
			pkg = pkg.split("=")[0];

			ExecResult execResult = ProcessUtil.execute(null, null, null, true, "dpkg", "-s", pkg);

			// if the package is installed and we filter packages installed, add it to the results
			if (execResult.getExitCode() == 0 && filterInstalled)
				result.add(pkg);

			// if the package is not installed and we filter package not installed, add it to the results
			else if (execResult.getExitCode() != 0 && !filterInstalled)
				result.add(pkg);
		}

		return result.toArray(new String[result.size()]);
	}

	@Override
	public boolean install(String... packages) throws DeploymentException {
		packages = filterPackages(false, packages);
		if (packages.length == 0)
			return false;
		log.info("installing " + String.join(" ", packages));
		apt("install", packages);
		return true;
	}

	@Override
	public boolean remove(String... packages) throws DeploymentException {
		packages = filterPackages(true, packages);
		if (packages.length == 0)
			return false;
		log.info("removing " + String.join(" ", packages));
		apt("purge", packages);
		return true;
	}
}
