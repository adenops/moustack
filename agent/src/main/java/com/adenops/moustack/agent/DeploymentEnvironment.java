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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.client.AbstractPackagingClient;
import com.adenops.moustack.agent.client.DesignateClient;
import com.adenops.moustack.agent.client.DockerLocalClient;
import com.adenops.moustack.agent.client.DpkgClient;
import com.adenops.moustack.agent.client.KeystoneClient;
import com.adenops.moustack.agent.client.MongoClient;
import com.adenops.moustack.agent.client.MySQLClient;
import com.adenops.moustack.agent.client.ValidationClient;
import com.adenops.moustack.agent.client.YumClient;
import com.adenops.moustack.agent.config.StackConfig;

/**
 * Everything the modules may need during the deployment.
 *
 * We use lazy loading because most of the services do not exist yet when the environment is instanciated.
 * All instantiations are thread safe, even if it shouldn't be necessary with the current implementation.
 *
 */
public class DeploymentEnvironment {
	public final Logger log = LoggerFactory.getLogger(DeploymentEnvironment.class);

	public enum OSFamily {
		DEBIAN, REDHAT;
	}

	// OS information
	private final OSFamily osFamily;
	private final String osId;
	private final String osVersion;

	// stack configuration
	private final StackConfig stack;

	// clients
	private final AbstractPackagingClient packagingClient;
	private DockerLocalClient dockerClient;
	private KeystoneClient keystoneClient;
	private DesignateClient designateClient;
	private MongoClient mongoClient;
	private MySQLClient mySQLClient;
	private ValidationClient validationClient;

	public DeploymentEnvironment(StackConfig stack, OSFamily osFamily, String osId, String osVersion)
			throws DeploymentException {
		this.stack = stack;
		this.osFamily = osFamily;
		this.osId = osId;
		this.osVersion = osVersion;
		switch (osFamily) {
		case DEBIAN:
			packagingClient = new DpkgClient();
			break;
		case REDHAT:
			packagingClient = new YumClient();
			break;
		default:
			throw new DeploymentException("unexpected OS family " + osFamily);
		}
	}

	public StackConfig getStack() {
		return stack;
	}

	public DockerLocalClient getDockerClient() throws DeploymentException {
		if (dockerClient == null) {
			synchronized (DockerLocalClient.class) {
				if (dockerClient != null)
					return dockerClient;
				dockerClient = new DockerLocalClient(stack);
			}
		}
		return dockerClient;
	}

	public boolean isDockerClientInitialized() {
		return dockerClient == null;
	}

	public KeystoneClient getKeystoneClient() throws DeploymentException {
		if (keystoneClient == null) {
			synchronized (KeystoneClient.class) {
				if (keystoneClient != null)
					return keystoneClient;
				keystoneClient = new KeystoneClient(stack);
			}
		}
		return keystoneClient;
	}

	public DesignateClient getDesignateClient() throws DeploymentException {
		if (designateClient == null) {
			synchronized (DesignateClient.class) {
				if (designateClient != null)
					return designateClient;
				designateClient = new DesignateClient(stack, getKeystoneClient());
			}
		}
		return designateClient;
	}

	public MongoClient getMongoClient() throws DeploymentException {
		if (mongoClient == null) {
			synchronized (MongoClient.class) {
				if (mongoClient != null)
					return mongoClient;
				mongoClient = new MongoClient(stack);
			}
		}
		return mongoClient;
	}

	public MySQLClient getMySQLClient() throws DeploymentException {
		if (mySQLClient == null) {
			synchronized (MySQLClient.class) {
				if (mySQLClient != null)
					return mySQLClient;
				mySQLClient = new MySQLClient(stack);
			}
		}
		return mySQLClient;
	}

	public ValidationClient getValidationClient() throws DeploymentException {
		if (validationClient == null) {
			synchronized (ValidationClient.class) {
				if (validationClient != null)
					return validationClient;
				validationClient = new ValidationClient(stack);
			}
		}
		return validationClient;
	}

	public AbstractPackagingClient getPackagingClient() {
		return packagingClient;
	}

	public OSFamily getOsFamily() {
		return osFamily;
	}

	public String getOsId() {
		return osId;
	}

	public String getOsVersion() {
		return osVersion;
	}
}
