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

package com.adenops.moustack.server;

import com.adenops.moustack.lib.argsparser.annotation.Argument;
import com.adenops.moustack.lib.argsparser.annotation.Argument.Type;
import com.adenops.moustack.lib.log.LogLevel;

public class ServerConfig {
	private static final ServerConfig instance = new ServerConfig();

	private int port;
	private String user;
	private String password;
	private String gitRepoUri;
	private String dbType;
	private String dbHost;
	private String dbName;
	private String dbUser;
	private String dbPassword;
	private boolean devMode;
	private String dockerRegistry;
	private String dockerMoustackTag;
	private LogLevel logLevel;

	private ServerConfig() {
	}

	public static ServerConfig getInstance() {
		return instance;
	}

	@Argument(clazz = Integer.class, property = "server.port", placeholder = "PORT", shortarg = "-P", longarg = "--port", defaultvalue = "8080", description = "Server port")
	public void setPort(int port) {
		this.port = port;
	}

	@Argument(property = "server.user", placeholder = "USER", shortarg = "-u", longarg = "--user", description = "Server user")
	public void setUser(String user) {
		this.user = user;
	}

	@Argument(property = "server.password", placeholder = "PASSWORD", shortarg = "-p", longarg = "--password", description = "Server password")
	public void setPassword(String password) {
		this.password = password;
	}

	@Argument(property = "git.repo.uri", placeholder = "GIT_REPO_URI", shortarg = "-r", longarg = "--repo", mandatory = true, description = "Repo URI. Can be local (file:///) and will be served or remote (http://)")
	public void setGitRepoUri(String gitRepoUri) {
		this.gitRepoUri = gitRepoUri;
	}

	@Argument(property = "database.type", defaultvalue = "hsql", description = "Database type, mysql or hsql")
	public void setDbType(String dbType) {
		this.dbType = dbType;
	}

	@Argument(property = "database.host", defaultvalue = "127.0.0.1", description = "Database port")
	public void setDbHost(String dbHost) {
		this.dbHost = dbHost;
	}

	@Argument(property = "database.name", defaultvalue = "moustack", description = "Database name")
	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	@Argument(property = "database.user", defaultvalue = "root", description = "Database user")
	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}

	@Argument(property = "database.password", defaultvalue = "", description = "Database password")
	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	@Argument(property = "docker.registry.url", longarg = "--docker-registry", description = "Override Docker registry url, example: myregistry.local:5000 (for development only)")
	public void setDockerRegistryURL(String dockerRegistry) {
		this.dockerRegistry = dockerRegistry;
	}

	@Argument(property = "docker.moustack.tag", longarg = "--moustack-tag", description = "Override Docker tag for Moustack images (for development only)")
	public void setDockerMoustackTag(String dockerMoustackTag) {
		this.dockerMoustackTag = dockerMoustackTag;
	}

	@Argument(type = Type.FLAG, shortarg = "-D", longarg = "--dev", description = "Start in dev mode")
	public void setDevMode(boolean devMode) {
		this.devMode = devMode;
	}

	@Argument(clazz = LogLevel.class, property = "log.level", placeholder = "LEVEL", shortarg = "-L", longarg = "--log-level", defaultvalue = "INFO", description = "Log level")
	public void setLogLevel(LogLevel logLevel) {
		this.logLevel = logLevel;
	}

	@Argument(type = Type.CONFIGURATION, shortarg = "-c", longarg = "--config", defaultvalue = "/etc/moustack-server", description = "Configuration file")
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

	public int getPort() {
		return port;
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public String getGitRepoUri() {
		return gitRepoUri;
	}

	public String getDbType() {
		return dbType;
	}

	public String getDbHost() {
		return dbHost;
	}

	public String getDbName() {
		return dbName;
	}

	public String getDbUser() {
		return dbUser;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public boolean getDevMode() {
		return devMode;
	}

	public String getDockerRegistry() {
		return dockerRegistry;
	}

	public String getDockerMoustackTag() {
		return dockerMoustackTag;
	}

	public LogLevel getLogLevel() {
		return logLevel;
	}

}
