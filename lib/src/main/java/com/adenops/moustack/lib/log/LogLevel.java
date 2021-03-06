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

package com.adenops.moustack.lib.log;

import org.apache.logging.log4j.Level;

public enum LogLevel {
	ERROR(Level.ERROR), WARN(Level.WARN), INFO(Level.INFO), DEBUG(Level.DEBUG), TRACE(Level.TRACE);
	private final Level log4jLevel;

	private LogLevel(Level log4jLevel) {
		this.log4jLevel = log4jLevel;
	}

	public Level getLog4jLevel() {
		return log4jLevel;
	}
}
