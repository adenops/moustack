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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.exec.ExecResult;
import com.adenops.moustack.agent.model.pkg.RPMPackageInfo;
import com.adenops.moustack.agent.util.ProcessUtil;

public class YumClient extends AbstractPackagingClient {
	private static final Logger log = LoggerFactory.getLogger(YumClient.class);
	private static final Pattern INFO_KEY_PATTERN = Pattern.compile("^([^ :]+) *: (.*)$");

	private void yum(String action, String... packages) throws DeploymentException {
		String[] command = ArrayUtils
				.addAll(new String[] { "yum", action, "--assumeyes", "--debuglevel=0", "--errorlevel=0" }, packages);
		ProcessUtil.execute(command);
	}

	private List<RPMPackageInfo> toPackageInfo(String... pkgs) throws DeploymentException {
		if (pkgs.length == 0)
			return Collections.emptyList();

		List<RPMPackageInfo> result = new ArrayList<>();

		for (String pkg : pkgs) {
			String[] parts = pkg.split("=", 2);
			String name = parts[0];
			String version = parts.length > 1 ? parts[1] : null;

			RPMPackageInfo packageInfo = new RPMPackageInfo(name, version);
			ExecResult execResult = null;

			// first, handle versionlock information
			if (version != null) {
				execResult = ProcessUtil.execute(null, null, null, true, "yum", "versionlock", "list");
				try (BufferedReader br = new BufferedReader(
						new InputStreamReader(execResult.getStdout().toInputStream()))) {
					Pattern versionLockPattern = Pattern
							.compile(String.format("^\\d:%s\\.\\*$", packageInfo.getFullName()));
					String line = null;
					while ((line = br.readLine()) != null) {
						if (line.isEmpty())
							continue;

						if (versionLockPattern.matcher(line).matches()) {
							packageInfo.setLocked(true);
							break;
						}
					}
				} catch (IOException e) {
					log.error("error while parsing version lock", e);
				}
			}

			// now we extract and parse package information
			execResult = ProcessUtil.execute(null, null, null, true, "rpm", "-qi", packageInfo.getName());
			packageInfo.setInstalled(execResult.getExitCode() == 0);

			result.add(packageInfo);

			if (!packageInfo.isInstalled())
				continue;

			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(execResult.getStdout().toInputStream()))) {
				String installedVersion = null;
				String installedRelease = null;
				String line = null;
				while ((line = br.readLine()) != null) {
					if (line.isEmpty())
						continue;

					Matcher matcher = INFO_KEY_PATTERN.matcher(line);

					// if not matching or invalid, we can skip
					if (!matcher.matches() || StringUtils.isBlank(matcher.group(1))
							|| StringUtils.isBlank(matcher.group(2)))
						continue;

					switch (matcher.group(1)) {
					case "Version":
						installedVersion = matcher.group(2);
						break;

					case "Release":
						installedRelease = matcher.group(2);
						break;
					}
					packageInfo.setInstalledVersion(String.format("%s-%s", installedVersion, installedRelease));
				}
			} catch (IOException e) {
				throw new DeploymentException("error while parsing package information", e);
			}
		}

		return result;
	}

	@Override
	public boolean install(String... packages) throws DeploymentException {
		if (packages.length == 0)
			return false;

		boolean changed = false;

		List<RPMPackageInfo> packagesInfo = toPackageInfo(packages);

		List<String> packagesToUnlock = new ArrayList<>();
		List<String> packagesToInstall = new ArrayList<>();
		List<String> packagesToDowngrade = new ArrayList<>();
		List<String> packagesToLock = new ArrayList<>();

		for (RPMPackageInfo packageInfo : packagesInfo) {
			// if the package is not installed or at the wrong version
			if (!packageInfo.isInstalled() || (packageInfo.isInstalled()
					&& !StringUtils.isBlank(packageInfo.getRequiredVersion())
					&& !StringUtils.equals(packageInfo.getRequiredVersion(), packageInfo.getInstalledVersion()))) {

				// if not locked, we need to ensure we unlock before installing the package because it could
				// be locked to another version.
				if (!packageInfo.isLocked())
					packagesToUnlock.add(packageInfo.getName());

				// find out if we need to install or downgrade
				if (!StringUtils.isBlank(packageInfo.getRequiredVersion())
						&& !StringUtils.isBlank(packageInfo.getInstalledVersion())
						&& StringUtils.compareIgnoreCase(packageInfo.getInstalledVersion(),
								packageInfo.getRequiredVersion()) > 0)
					packagesToDowngrade.add(packageInfo.getFullName());
				else
					packagesToInstall.add(packageInfo.getFullName());
			}

			// if we require a specific version, we may have to (re-)lock
			if (!StringUtils.isBlank(packageInfo.getRequiredVersion()) && !packageInfo.isLocked())
				packagesToLock.add(packageInfo.getFullName());
		}

		if (!packagesToUnlock.isEmpty()) {
			changed = true;

			log.debug("unlocking " + String.join(" ", packagesToUnlock));
			String[] command = ArrayUtils.addAll(new String[] { "yum", "versionlock", "delete" },
					packagesToUnlock.toArray(new String[packagesToUnlock.size()]));
			ProcessUtil.execute(null, null, null, true, command);
		}

		if (!packagesToInstall.isEmpty()) {
			changed = true;

			log.info("installing " + String.join(" ", packagesToInstall));
			yum("install", packagesToInstall.toArray(new String[packagesToInstall.size()]));
		}

		if (!packagesToDowngrade.isEmpty()) {
			changed = true;

			log.info("downgrading " + String.join(" ", packagesToDowngrade));
			yum("downgrade", packagesToDowngrade.toArray(new String[packagesToDowngrade.size()]));
		}

		if (!packagesToLock.isEmpty()) {
			changed = true;

			log.info("locking " + String.join(" ", packagesToLock));
			String[] command = ArrayUtils.addAll(new String[] { "yum", "versionlock", "add" },
					packagesToLock.toArray(new String[packagesToLock.size()]));
			ProcessUtil.execute(null, null, null, false, command);
		}

		return changed;
	}

	@Override
	public boolean remove(String... packages) throws DeploymentException {
		if (packages.length == 0)
			return false;

		List<RPMPackageInfo> packagesInfo = toPackageInfo(packages);

		String[] packagesToRemove = packagesInfo.stream().filter(p -> p.isInstalled()).map(p -> p.getName())
				.toArray(size -> new String[size]);

		if (packagesToRemove.length == 0)
			return false;

		log.info("removing " + String.join(" ", packages));
		yum("erase", packagesToRemove);

		return true;
	}

	@Override
	public void init() throws DeploymentException {
		ExecResult execResult = ProcessUtil.execute(null, null, null, true, "rpm", "-q", "yum-plugin-versionlock");
		if (execResult.getExitCode() != 0) {
			log.info("installing required package yum-plugin-versionlock");
			yum("install", "yum-plugin-versionlock");
		}
	}
}
