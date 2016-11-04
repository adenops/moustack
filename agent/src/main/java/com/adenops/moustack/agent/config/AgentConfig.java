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

package com.adenops.moustack.agent.config;

import com.adenops.moustack.lib.argsparser.annotation.Argument;
import com.adenops.moustack.lib.argsparser.annotation.Argument.Type;

public class AgentConfig {
	private static final AgentConfig instance = new AgentConfig();

	private String id;
	private String profile;
	private String server;
	private String user;
	private String password;
	private boolean sslVerify;
	private boolean runOnce;
	private String configDir;
	private LogLevel level;
	private boolean dryRun;

	private AgentConfig() {
	}

	public static AgentConfig getInstance() {
		return instance;
	}

	@Argument(property = "hostname", placeholder = "HOSTNAME", shortarg = "-I", longarg = "--id", mandatory = true, description = "Node hostname")
	public void setId(String id) {
		this.id = id;
	}

	@Argument(property = "server.user", placeholder = "USER", shortarg = "-u", longarg = "--user", description = "Server user")
	public void setUser(String user) {
		this.user = user;
	}

	@Argument(property = "server.password", placeholder = "PASSWORD", shortarg = "-p", longarg = "--password", description = "Server password")
	public void setPassword(String password) {
		this.password = password;
	}

	@Argument(type = Type.FLAG, property = "server.ssl.verify", shortarg = "-r", longarg = "--ssl-verify", description = "Verify SSL certificate")
	public void setSslVerify(boolean sslVerify) {
		this.sslVerify = sslVerify;
	}

	@Argument(property = "stack.dir", placeholder = "STACK_DIR", shortarg = "-c", longarg = "--stack-dir", defaultvalue = "/var/lib/moustack/stack", description = "Stack directory (where GIT will maintain stack configuration)")
	public void setConfigDir(String configDir) {
		this.configDir = configDir;
	}

	@Argument(property = "server.url", placeholder = "SERVER", shortarg = "-s", longarg = "--server", mandatory = true, description = "Server URL (example: http://moustackmaster:8080)")
	public void setServer(String server) {
		this.server = server;
	}

	@Argument(property = "stack.profile", placeholder = "STACK_PROFILE", shortarg = "-P", longarg = "--stack-profile", mandatory = true, description = "Stack profile")
	public void setProfile(String profile) {
		this.profile = profile;
	}

	@Argument(type = Type.FLAG, longarg = "--run-once", description = "Run once and exit")
	public void setRunOnce(boolean runOnce) {
		this.runOnce = runOnce;
	}

	@Argument(clazz = LogLevel.class, property = "log.level", placeholder = "LEVEL", shortarg = "-L", longarg = "--log-level", defaultvalue = "INFO", description = "Log level")
	public void setLevel(LogLevel level) {
		this.level = level;
	}

	@Argument(type = Type.FLAG, shortarg = "-d", longarg = "--dryrun", description = "Dry run")
	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
	}

	@Argument(type = Type.CONFIGURATION, shortarg = "-c", longarg = "--config", defaultvalue = "/etc/moustack-agent", description = "Configuration file")
	public void _config() {
	}

	@Argument(type = Type.HELP, shortarg = "-h", longarg = "--help", description = "Help")
	public void _help() {
	}

	@Argument(type = Type.HELP_CONFIGURATION, longarg = "--help-config", description = "Show configuration file sample")
	public void _helpConfig() {
	}

	@Argument(type = Type.VERSION, shortarg = "-v", longarg = "--version", description = "Version")
	public void _version() {
	}

	public String getId() {
		return id;
	}

	public String getConfigDir() {
		return configDir;
	}

	public String getServer() {
		return server;
	}

	public boolean isRunOnce() {
		return runOnce;
	}

	public String getProfile() {
		return profile;
	}

	public LogLevel getLevel() {
		return level;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public boolean isSslVerify() {
		return sslVerify;
	}
}
