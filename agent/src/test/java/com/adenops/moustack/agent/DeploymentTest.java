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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.adenops.moustack.agent.client.DockerLocalClient;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.LogLevel;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.module.ContainerModule;

public class DeploymentTest {
	private static final File TMP_DIR = new File("/tmp/moustack-test");
	private static StackConfig stack;
	private static DockerLocalClient docker;
	private static ContainerModule container;

	@BeforeClass
	public static void setup() throws Exception {
		AgentConfig agentConfig = AgentConfig.getInstance();
		agentConfig.setUser("junit");
		agentConfig.setPassword("junit");
		agentConfig.setServer("http://127.0.0.1:8989");
		agentConfig.setProfile("junit");
		agentConfig.setLogLevel(LogLevel.DEBUG);
		agentConfig.setConfigDir(TMP_DIR.toPath().resolve("config").toFile().toURI().toString());

		File source = new File(DeploymentTest.class.getResource("/profiles").toURI());

		if (TMP_DIR.exists())
			FileUtils.deleteDirectory(TMP_DIR);
		TMP_DIR.mkdirs();

		File profilesDir = TMP_DIR.toPath().resolve("profiles").toFile();
		FileUtils.copyDirectory(source, profilesDir);

		Git git = Git.init().setDirectory(profilesDir).call();
		git.add().addFilepattern(".").call();
		git.commit().setMessage("test").call();

		stack = new StackConfig();
		stack.setGitRepo(profilesDir.toURI().toString());
		stack.setGitBranch("master");

		docker = new DockerLocalClient(stack);
		container = new ContainerModule("container", "test", "latest", null, null, null, null, false, null, false);
	}

	@AfterClass
	public static void tearDown() throws Exception {
		docker.discardContainer(container);
		FileUtils.forceDelete(TMP_DIR);
	}

	@Test
	public void deployContainer() throws Exception {
		AgentConfig.getInstance().setId("junit-1");
		Deployer deployer = new Deployer(stack);
		deployer.start();
		assertThat(docker.containerIsRunning(container)).isTrue();
	}

	@Test
	public void deploySystem() throws Exception {
		AgentConfig.getInstance().setId("junit-2");
		Deployer deployer = new Deployer(stack);
		deployer.start();
	}

	@Test
	public void deployAll() throws Exception {
		AgentConfig.getInstance().setId("junit-3");
		Deployer deployer = new Deployer(stack);
		deployer.start();
		assertThat(docker.containerIsRunning(container)).isTrue();
	}
}
