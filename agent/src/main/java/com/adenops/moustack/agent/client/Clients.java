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

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;

/*
 * This class manages clients that depend on the stack configuration, ie that
 * can change between two runs.
 *
 * This is a weird pattern, but I couldn't find one that fit better with the
 * current code design.
 *
 */
public class Clients {
	private static DockerClient dockerClient;
	private static KeystoneClient keystoneClient;
	private static DesignateClient designateClient;
	private static MongoClient mongoClient;
	private static MySQLClient mySQLClient;
	private static ValidationClient validationClient;
	private static StackConfig stack;

	public static synchronized void init(StackConfig stack) throws DeploymentException {
		release();
		Clients.stack = stack;
	}

	private static void release(ManagedClient managedClient) {
		if (managedClient == null)
			return;
		managedClient.release();
		Clients.stack = null;
	}

	public static void release() {
		release(dockerClient);
		dockerClient = null;
		release(keystoneClient);
		keystoneClient = null;
		release(designateClient);
		designateClient = null;
		release(mongoClient);
		mongoClient = null;
		release(mySQLClient);
		mySQLClient = null;
		release(validationClient);
		validationClient = null;
	}

	public static DockerClient getDockerClient() throws DeploymentException {
		if (dockerClient == null) {
			synchronized (DockerClient.class) {
				if (dockerClient != null)
					return dockerClient;
				dockerClient = new DockerClient(stack);
			}
		}
		return dockerClient;
	}

	public static KeystoneClient getKeystoneClient() throws DeploymentException {
		if (keystoneClient == null) {
			synchronized (KeystoneClient.class) {
				if (keystoneClient != null)
					return keystoneClient;
				keystoneClient = new KeystoneClient(stack);
			}
		}
		return keystoneClient;
	}

	public static DesignateClient getDesignateClient() throws DeploymentException {
		if (designateClient == null) {
			synchronized (DesignateClient.class) {
				if (designateClient != null)
					return designateClient;
				designateClient = new DesignateClient(stack);
			}
		}
		return designateClient;
	}

	public static MongoClient getMongoClient() throws DeploymentException {
		if (mongoClient == null) {
			synchronized (MongoClient.class) {
				if (mongoClient != null)
					return mongoClient;
				mongoClient = new MongoClient(stack);
			}
		}
		return mongoClient;
	}

	public static MySQLClient getMySQLClient() throws DeploymentException {
		if (mySQLClient == null) {
			synchronized (MySQLClient.class) {
				if (mySQLClient != null)
					return mySQLClient;
				mySQLClient = new MySQLClient(stack);
			}
		}
		return mySQLClient;
	}

	public static ValidationClient getValidationClient() throws DeploymentException {
		if (validationClient == null) {
			synchronized (ValidationClient.class) {
				if (validationClient != null)
					return validationClient;
				validationClient = new ValidationClient(stack);
			}
		}
		return validationClient;
	}
}
