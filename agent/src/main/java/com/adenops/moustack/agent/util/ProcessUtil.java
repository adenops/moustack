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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.model.exec.ExecResult;

public class ProcessUtil {
	private static final Logger log = LoggerFactory.getLogger(ProcessUtil.class);
	private static final File WORKING_DIR = new File("/tmp");
	private static final long EXEC_TIMEOUT = 600;
	private static final long MAX_CAPTURE_LENGTH = 1024 * 1024;

	public static ExecResult execute(String... command) throws DeploymentException {
		return execute(null, null, null, false, command);
	}

	public static ExecResult execute(String user, File cwd, Map<String, String> env, boolean allowFailure,
			String... command) throws DeploymentException {
		log.debug("executing [" + String.join(" ", command) + "]");

		ExecResult result = new ExecResult();
		ProcessBuilder processBuilder = new ProcessBuilder(command);

		// is the command has to be executed with a specific user, wrap it with su
		// if not, we assume we are running as root
		if (user != null && !user.isEmpty())
			command = new String[] { "su", "-s", "/bin/sh", "-c", String.join(" ", command), user };

		// set working directory
		processBuilder.directory(cwd == null ? WORKING_DIR : cwd);

		// the the environment if provided
		if (env != null)
			processBuilder.environment().putAll(env);

		// start the process
		Process process = null;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			throw new DeploymentException("error while starting process builder", e);
		}

		// wait for the process to finish
		try {
			process.waitFor(EXEC_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			process.destroy();
			throw new DeploymentException(String.format("command [%s] timed out or was interrupted",
					String.join(" ", command)), e);
		}
		result.setExitCode(process.exitValue());

		// read stdout
		try {
			IOUtils.copyLarge(process.getInputStream(), result.getStdout(), 0, MAX_CAPTURE_LENGTH);
		} catch (IOException e) {
			log.error("error while reading command stdout: " + e.getMessage());
		}

		// display stderr if relevant
		// if we allow failure, we can assume we don't care about stderr
		if (!allowFailure) {
			BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			String line = null;
			try {
				while ((line = stderr.readLine()) != null) {
					log.error(line);
				}
			} catch (IOException e) {
				log.error("error while reading command stderr: " + e.getMessage());
			}
		}

		IOUtils.closeQuietly(process.getInputStream());
		IOUtils.closeQuietly(process.getErrorStream());

		// throw an exception if process failed
		if (!allowFailure && process.exitValue() != 0)
			throw new DeploymentException("process exited with value " + process.exitValue());

		return result;
	}
}
