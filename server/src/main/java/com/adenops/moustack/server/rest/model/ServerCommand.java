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

package com.adenops.moustack.server.rest.model;

import io.swagger.annotations.ApiModel;

@ApiModel
public class ServerCommand {
	public enum Cmd {
		RUN, REPORT, SHUTDOWN;
	}

	private Cmd command;

	public ServerCommand() {
	}

	public ServerCommand(Cmd cmd) {
		this.command = cmd;
	}

	public Cmd getCommand() {
		return command;
	}

	public void setCommand(Cmd command) {
		this.command = command;
	}
}
