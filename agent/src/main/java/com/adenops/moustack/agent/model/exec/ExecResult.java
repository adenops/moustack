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

package com.adenops.moustack.agent.model.exec;

import org.apache.commons.io.output.ByteArrayOutputStream;

public class ExecResult {
	private int exitCode = -1;
	private String command;
	private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

	public int getExitCode() {
		return exitCode;
	}

	public void setExitCode(int statusCode) {
		this.exitCode = statusCode;
	}

	public ByteArrayOutputStream getStdout() {
		return stdout;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}
}
