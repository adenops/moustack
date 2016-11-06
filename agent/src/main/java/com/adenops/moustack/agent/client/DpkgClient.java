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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.pkg.DebPackageInfo;
import com.adenops.moustack.agent.util.ProcessUtil;

public class DpkgClient extends AbstractPackagingClient {
	private static final Logger log = LoggerFactory.getLogger(DpkgClient.class);
	private static final Pattern STATUS_KEY_PATTERN = Pattern.compile("^([^:]+): (.*)$");

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
		String[] command = ArrayUtils
				.addAll(new String[] { "apt-get", action, "--assume-yes", "--quiet", "--allow-downgrades" }, packages);
		ProcessUtil.execute(null, null, APT_ENV, false, command);
	}

	private List<DebPackageInfo> toPackageInfo(String... pkgs) throws DeploymentException {
		if (pkgs.length == 0)
			return Collections.emptyList();

		List<DebPackageInfo> result = new ArrayList<>();
		Map<String, String> packages = new HashMap<>();

		for (String pkg : pkgs) {
			String[] parts = pkg.split("=", 2);
			String name = parts[0];
			String version = parts.length > 1 ? parts[1] : null;
			packages.put(name, version);
		}

		try (BufferedReader br = new BufferedReader(new FileReader("/var/lib/dpkg/status"))) {
			String line = null;
			boolean skip = false;

			DebPackageInfo currentPkg = null;

			while ((line = br.readLine()) != null && !packages.isEmpty()) {
				// if line is empty, this is the section separator
				if (line.isEmpty()) {
					skip = false;
					continue;
				}
				// when skip is enabled, don't need to go further
				if (skip)
					continue;

				Matcher matcher = STATUS_KEY_PATTERN.matcher(line);

				// if not matching or invalid, we can skip
				if (!matcher.matches() || StringUtils.isBlank(matcher.group(1))
						|| StringUtils.isBlank(matcher.group(2)))
					continue;

				switch (matcher.group(1)) {
				case "Package":
					String name = matcher.group(2);

					if (!packages.containsKey(name)) {
						skip = true;
						continue;
					}

					currentPkg = new DebPackageInfo(name, packages.get(name));
					break;

				case "Status":
					currentPkg.setInstalled(matcher.group(2).endsWith(" ok installed"));
					currentPkg.setLocked(matcher.group(2).startsWith("hold "));
					break;

				case "Version":
					currentPkg.setInstalledVersion(matcher.group(2));
					result.add(currentPkg);

					// skip to next package
					packages.remove(currentPkg.getName());
					skip = true;
					break;
				}
			}
		} catch (FileNotFoundException e) {
			throw new DeploymentException("could not open file /var/lib/dpkg/status");
		} catch (IOException e) {
			throw new DeploymentException("error while reading file /var/lib/dpkg/status", e);
		}

		// finally we add the packages we didn't find in the status
		for (Entry<String, String> entry : packages.entrySet())
			result.add(new DebPackageInfo(entry.getKey(), entry.getValue()));

		return result;
	}

	@Override
	public boolean install(String... packages) throws DeploymentException {
		if (packages.length == 0)
			return false;

		boolean changed = false;

		List<DebPackageInfo> packagesInfo = toPackageInfo(packages);

		String[] packagesToInstall = packagesInfo.stream()
				.filter(p -> !p.isInstalled() || (!StringUtils.isBlank(p.getRequiredVersion()) && p.isInstalled()
						&& !StringUtils.equals(p.getRequiredVersion(), p.getInstalledVersion())))
				.map(p -> p.getFullName()).toArray(size -> new String[size]);

		if (packagesToInstall.length > 0) {
			changed = true;
			log.info("installing " + String.join(" ", packagesToInstall));
			apt("install", packagesToInstall);
		}

		String[] packagesToLock = packagesInfo.stream()
				.filter(p -> !StringUtils.isBlank(p.getRequiredVersion()) && !p.isLocked()).map(p -> p.getName())
				.toArray(size -> new String[size]);

		if (packagesToLock.length > 0) {
			changed = true;
			log.info("locking " + String.join(" ", packagesToLock));
			String[] command = ArrayUtils.addAll(new String[] { "apt-mark", "hold" }, packagesToLock);
			ProcessUtil.execute(null, null, APT_ENV, false, command);
		}

		return changed;
	}

	@Override
	public boolean remove(String... packages) throws DeploymentException {
		if (packages.length == 0)
			return false;

		List<DebPackageInfo> packagesInfo = toPackageInfo(packages);

		String[] packagesToRemove = packagesInfo.stream().filter(p -> p.isInstalled()).map(p -> p.getName())
				.toArray(size -> new String[size]);

		if (packagesToRemove.length == 0)
			return false;

		log.info("removing " + String.join(" ", packages));
		apt("purge", packagesToRemove);

		return true;
	}
}
