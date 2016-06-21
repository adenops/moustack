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

package com.adenops.moustack.agent.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.exec.ExecResult;

public class SystemCtlUtil {
	private static final Logger log = LoggerFactory.getLogger(SystemCtlUtil.class);
	private static final Pattern FRAGMENTPATH_REGEX = Pattern.compile("^FragmentPath=/lib/systemd/system/(.*)\n");

	/*
	 * Global wrapper that assembles and executes a systemctl command.
	 * This should only be called by others systemctl wrapper.
	 */
	private static ExecResult systemctlExec(String action, String... arguments) throws DeploymentException {
		String[] command = new String[] { "systemctl", action };
		if (arguments.length > 0)
			command = (String[]) ArrayUtils.addAll(command, arguments);

		return ProcessUtil.execute(null, null, null, true, command);
	}

	/*
	 * Executes a systemctl command that modify the system, and throw an exception on error.
	 */
	private static void systemctlCmd(String command, String services) throws DeploymentException {
		ExecResult result = systemctlExec(command, services);
		if (result.getExitCode() != 0)
			throw new DeploymentException("systemctl command failed with exit code " + result.getExitCode());
	}

	/*
	 * Executes a systemctl check that does not modify the system, and throw an exception only on unexpected errors.
	 */
	private static boolean systemctlCheck(String command, String services) throws DeploymentException {
		ExecResult result = systemctlExec(command, services);
		return result.getExitCode() == 0;
	}

	/*
	 * We use FragmentPath to help detect if there has been a unit override in /etc, it seems systemd does
	 * not provide a proper mechanism for that.
	 */
	public static boolean unitIsUpdated(String unit) throws DeploymentException {
		ExecResult result = systemctlExec("show", unit, "--property=NeedDaemonReload", "--property=FragmentPath");

		if (result.getExitCode() != 0)
			throw new DeploymentException("systemctl command failed with exit code " + result.getExitCode());

		String stdout = new String(result.getStdout().toByteArray(), StandardCharsets.UTF_8);

		// If systemd has detected it needs a reload.
		if (stdout.contains("NeedDaemonReload=yes"))
			return true;

		Matcher matcher = FRAGMENTPATH_REGEX.matcher(stdout);
		if (!matcher.find())
			return false;

		// Check if the same service file exists in /etc
		return new File(String.format("/etc/systemd/system/%s", matcher.group(1))).exists();
	}

	public static void daemonReload() throws DeploymentException {
		ExecResult result = systemctlExec("daemon-reload");
		if (result.getExitCode() != 0)
			throw new DeploymentException("systemctl command failed with exit code " + result.getExitCode());
	}

	public static boolean unitIsActive(String unit) throws DeploymentException {
		return systemctlCheck("is-active", unit);
	}

	public static boolean unitIsEnabled(String unit) throws DeploymentException {
		return systemctlCheck("is-enabled", unit);
	}

	public static void unitStop(String unit) throws DeploymentException {
		systemctlCmd("stop", unit);
	}

	public static void unitRestart(String unit) throws DeploymentException {
		systemctlCmd("restart", unit);
	}

	public static void unitEnable(String unit) throws DeploymentException {
		systemctlCmd("enable", unit);
	}

	public static void unitDisable(String unit) throws DeploymentException {
		systemctlCmd("disable", unit);
	}

	public static boolean startService(boolean forceRestart, String service) throws DeploymentException {
		if (unitIsUpdated(service))
			daemonReload();

		if (!unitIsEnabled(service))
			unitEnable(service);

		if (!forceRestart && unitIsActive(service))
			return false;

		unitRestart(service);
		return true;
	}

	public static boolean startServices(boolean forceRestart, List<String> services) throws DeploymentException {
		boolean changed = false;
		for (String service : services)
			changed |= startService(forceRestart, service);
		return changed;
	}

	public static boolean stopService(String service) throws DeploymentException {
		if (unitIsEnabled(service))
			unitDisable(service);

		if (!unitIsActive(service))
			return false;

		unitStop(service);
		return true;
	}

	public static boolean stopServices(List<String> services) throws DeploymentException {
		boolean changed = false;
		for (String service : services)
			changed |= stopService(service);
		return changed;
	}
}
