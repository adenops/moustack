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

package com.adenops.moustack.agent;

import org.junit.BeforeClass;
import org.junit.Test;

import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.LogLevel;
import com.adenops.moustack.server.MoustackServer;
import com.adenops.moustack.server.ServerConfig;

// XXX: not ready
public class DeploymentIT {
	private static AgentConfig agentConfig;

	@BeforeClass
	public static void setup() throws Exception {
		if (!System.getProperty("user.name").equals("root"))
			throw new RuntimeException("this test must be run as root (and not on you host!)");

		ServerConfig serverConfig = ServerConfig.getInstance();

		serverConfig.setUser("moustack");
		serverConfig.setPassword("password");
		serverConfig.setRepoUri("file:///home/jb/dev/moustack/profiles");
		serverConfig.setPort(8989);
		serverConfig.setDevMode(true);
		serverConfig.setDbHost("172.17.0.1");
		serverConfig.setDbName("moustack");
		serverConfig.setDbUser("root");
		serverConfig.setDbPassword("");

		MoustackServer server = new MoustackServer();
		server.start(serverConfig);

		AgentConfig agentConfig = AgentConfig.getInstance();
		agentConfig.setUser("moustack");
		agentConfig.setPassword("password");
		agentConfig.setServer("http://127.0.0.1:8989");
		agentConfig.setProfile("docker-goldorak");
		agentConfig.setId("dockerstack");
		agentConfig.setLevel(LogLevel.DEBUG);
		agentConfig.setConfigDir("/tmp/integration-tests");
	}

	@Test
	public void baseDeployment() throws DeploymentException, InterruptedException {
		MoustackAgent agent = new MoustackAgent();
		agent.start(agentConfig);
	}
}
