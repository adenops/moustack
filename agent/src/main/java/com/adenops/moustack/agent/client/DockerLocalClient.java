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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.module.ContainerModule;
import com.adenops.moustack.agent.util.PathUtil;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.ContainerNotFoundException;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerExit;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.Device;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.RestartPolicy;
import com.spotify.docker.client.messages.ImageInfo;
import com.spotify.docker.client.messages.Info;
import com.spotify.docker.client.messages.LogConfig;

public class DockerLocalClient {
	private static final Logger log = LoggerFactory.getLogger(DockerLocalClient.class);
	private static final int STOP_TIMEOUT_SECONDS = 10;
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private final DockerClient client;
	private final StackConfig stack;

	public DockerLocalClient(StackConfig stack) throws DeploymentException {
		log.debug("initializing Docker client");

		this.stack = stack;

		try {
			client = DefaultDockerClient.fromEnv().build();
		} catch (DockerCertificateException e) {
			throw new DeploymentException("could not initialize Docker client", e);
		}

		try {
			Info info = client.info();
			log.debug("images: " + info.images());
			log.debug("containers: " + info.containersRunning());
		} catch (DockerException e) {
			throw new DeploymentException("could not get Docker client info", e);
		} catch (InterruptedException e) {
			interrupt(e);
		}
	}

	private void interrupt(InterruptedException e) throws DeploymentException {
		throw new DeploymentException("interrupted", e);
	}

	private void appendLine(StringBuffer sb, String... strings) {
		for (String string : strings)
			sb.append(string);
		sb.append("\n");
	}

	private boolean listsEquals(List<?> list1, List<?> list2) {
		if ((list1 == null || list1.isEmpty()) && (list2 == null || list2.isEmpty()))
			return true;
		return list1.equals(list2);
	}

	private boolean listContains(List<?> listMain, List<?> listContained) {
		if ((listMain == null || listMain.isEmpty()) && (listContained == null || listContained.isEmpty()))
			return true;
		return listMain.containsAll(listContained);
	}

	private List<String> environmentAsList(ContainerModule container) throws DeploymentException {
		List<String> environment = new ArrayList<>();
		for (String envFile : container.getEnvironments()) {
			File file = new File(PathUtil.getContainerTargetFilePath(stack, container.getName(), envFile));
			try {
				environment.addAll(FileUtils.readLines(file));
			} catch (IOException e) {
				throw new DeploymentException("error while reading envfile " + file, e);
			}
		}
		return environment;
	}

	public boolean containerIsRunning(ContainerModule container) throws DeploymentException {
		try {
			ContainerInfo info = client.inspectContainer(container.getName());
			return info.state().running();
		} catch (ContainerNotFoundException e) {
			return false;
		} catch (DockerException e) {
			throw new DeploymentException("error while checking if container " + container.getName() + " is running", e);
		} catch (InterruptedException e) {
			interrupt(e);
		}
		return false;
	}

	private boolean containerConfigChanged(ContainerModule container) throws DeploymentException {
		ContainerInfo info = null;
		try {
			info = client.inspectContainer(container.getName());
		} catch (ContainerNotFoundException e) {
			return true;
		} catch (DockerException e) {
			throw new DeploymentException("error while checking if container " + container.getName() + " is running", e);
		} catch (InterruptedException e) {
			interrupt(e);
		}

		HostConfig hostConfig = info.hostConfig();
		ContainerConfig containerConfig = info.config();

		// compare privileged
		if (hostConfig.privileged() != container.isPrivileged()) {
			log.info("container {} privilege changed", container.getName());
			return true;
		}

		// compare log driver
		LogConfig logConfig = hostConfig.logConfig();
		// right now we just check the log driver as we only support syslog or default
		if (logConfig.logType().equals("syslog") != container.isSyslog()) {
			log.info("container {} log driver changed", container.getName());
			return true;
		}

		// compare volumes
		if (!listsEquals(hostConfig.binds(), Volume.asStringList(container.getVolumes()))) {
			log.info("container {} volumes changed", container.getName());
			return true;
		}

		// compare devices
		List<Device> devices = new ArrayList<>();
		for (String device : container.getDevices())
			devices.add(new Device("rwm", device, device));
		if (!listsEquals(hostConfig.devices(), devices)) {
			log.info("container {} devices changed", container.getName());
			return true;
		}

		// compare capabilities
		if (!listsEquals(hostConfig.capAdd(), container.getCapabilities())) {
			log.info("container {} capabilities changed", container.getName());
			return true;
		}

		// compare environment
		// Note, we cannot compare exactly the environments (the container can export some), we can only ensure our
		// keys are present.
		if (!listContains(containerConfig.env(), environmentAsList(container))) {
			log.info("container {} environment changed", container.getName());
			return true;
		}

		return false;
	}

	private boolean containerImageChanged(ContainerModule container) throws DeploymentException {
		String imageFullName = String.format("%s:%s", container.getImage(), container.getImageTag());

		boolean needPull = false;
		if (container.getImageTag().equals("latest")) {
			log.info("image {} tag is latest, pulling...", container.getImage());
			needPull = true;
		} else {
			try {
				client.inspectImage(imageFullName);
				log.debug("image {} with tag {} found, no need for pulling", container.getImage(),
						container.getImageTag());
				return false;
			} catch (ImageNotFoundException e) {
				log.info("image {} is not present, pulling...", container.getImage());
				needPull = true;
			} catch (DockerException e) {
				throw new DeploymentException("error while inspecting image " + container.getImage(), e);
			} catch (InterruptedException e) {
				interrupt(e);
			}
		}

		if (needPull) {
			try {
				client.pull(imageFullName);
			} catch (DockerException e) {
				throw new DeploymentException("error while pulling image " + imageFullName, e);
			} catch (InterruptedException e) {
				interrupt(e);
			}
		}

		if (container.getImageTag().equals("latest")) {
			try {
				ContainerInfo containerInfo = client.inspectContainer(container.getName());
				ImageInfo imageInfo = client.inspectImage(imageFullName);

				if (containerInfo.image().equals(imageInfo.id())) {
					log.debug("image {} did not change", imageFullName);
					return false;
				}
			} catch (ContainerNotFoundException e) {
				// ignore
			} catch (DockerException e) {
				throw new DeploymentException("error while checking pulled image " + imageFullName, e);
			} catch (InterruptedException e) {
				interrupt(e);
			}
		}

		return true;
	}

	public void discardContainer(ContainerModule container) throws DeploymentException {
		try {
			client.stopContainer(container.getName(), STOP_TIMEOUT_SECONDS);
		} catch (ContainerNotFoundException e) {
			// ignore
		} catch (DockerException e) {
			throw new DeploymentException("error while stopping container " + container.getName(), e);
		} catch (InterruptedException e) {
			interrupt(e);
		}

		try {
			client.removeContainer(container.getName());
		} catch (ContainerNotFoundException e) {
			// ignore
		} catch (DockerException e) {
			throw new DeploymentException("error while removing container " + container.getName(), e);
		} catch (InterruptedException e) {
			interrupt(e);
		}
	}

	public boolean startContainer(boolean changed, ContainerModule container) throws DeploymentException {
		// always check for image
		changed |= containerImageChanged(container);

		// only check for configuration changes if no change until now
		if (!changed)
			changed |= containerConfigChanged(container);

		// no changes and container already running, we don't need to start/restart the container
		if (!changed && containerIsRunning(container))
			return false;

		log.info("updating container " + container.getName());
		startOrRestartContainer(container, false, new String[] {});
		return true;
	}

	public void startEphemeralContainer(ContainerModule container, String user, String... command)
			throws DeploymentException {
		// always check for image
		containerImageChanged(container);

		String name = container.getName() + "-" + RandomStringUtils.random(10, true, true);
		String[] suCommand = new String[] { "su", "-s", "/bin/sh", "-c", String.join(" ", command), user };

		ContainerModule temporaryContainer = new ContainerModule(name, container);

		log.debug("executing command " + String.join(" ", command));
		startOrRestartContainer(temporaryContainer, true, suCommand);

		try {
			ContainerExit code = client.waitContainer(name);
			if (code.statusCode() == 0)
				return;
			throw new DeploymentException("ephemeral container " + name + " execution returned " + code
					+ " for command " + String.join(" ", suCommand));
		} catch (DockerException e) {
			throw new DeploymentException("error while waiting for container " + name, e);
		} catch (InterruptedException e) {
			interrupt(e);
		}

		discardContainer(temporaryContainer);
	}

	private void startOrRestartContainer(ContainerModule container, boolean ephemeral, String... command)
			throws DeploymentException {

		com.spotify.docker.client.messages.HostConfig.Builder hostConfigBuilder = HostConfig.builder();
		com.spotify.docker.client.messages.ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();

		hostConfigBuilder.capAdd(container.getCapabilities());
		hostConfigBuilder.networkMode("host");
		hostConfigBuilder.privileged(container.isPrivileged());

		containerConfigBuilder.image(container.getImage());

		// auto restart container if it is not ephemeral
		if (!ephemeral)
			hostConfigBuilder.restartPolicy(RestartPolicy.always());

		// enable syslog logging driver
		if (!ephemeral && container.isSyslog()) {
			Map<String, String> logOptions = new HashMap<>();
			logOptions.put("syslog-address", "udp://127.0.0.1:50014");
			logOptions.put("syslog-tag", container.getName());
			logOptions.put("syslog-format", "rfc3164");
			hostConfigBuilder.logConfig(LogConfig.create("syslog", logOptions));
		}

		// add the command line (for ephemerals)
		if (command.length > 0)
			containerConfigBuilder.cmd(command);

		// prepare devices list
		List<Device> devices = new ArrayList<>();
		for (String device : container.getDevices())
			devices.add(new Device("rwm", device, device));
		hostConfigBuilder.devices(devices);

		// prepare volumes binds
		hostConfigBuilder.appendBinds(Volume.asStringList(container.getVolumes()));

		// prepare environment
		containerConfigBuilder.env(environmentAsList(container));

		// We are finally done with the configuration, we can now start the container

		discardContainer(container);

		String containerId = null;
		try {
			containerConfigBuilder.hostConfig(hostConfigBuilder.build());
			ContainerCreation creation = client.createContainer(containerConfigBuilder.build(), container.getName());
			containerId = creation.id();
		} catch (DockerException e) {
			throw new DeploymentException("could not create container " + container.getName(), e);
		} catch (InterruptedException e) {
			interrupt(e);
		}

		try {
			client.startContainer(containerId);
		} catch (DockerException e) {
			throw new DeploymentException("could not start container " + container.getName(), e);
		} catch (InterruptedException e) {
			interrupt(e);
		}

		log.debug("started container " + container.getName());
	}

	public String getContainerInfo(ContainerModule container) throws DeploymentException {
		ContainerInfo containerInfo = null;
		ImageInfo imageInfo = null;

		try {
			containerInfo = client.inspectContainer(container.getName());
		} catch (ContainerNotFoundException e) {
			return "container " + container.getName() + " not found\n";
		} catch (DockerException e) {
			throw new DeploymentException("error while inspecting container " + container.getName(), e);
		} catch (InterruptedException e) {
			interrupt(e);
		}

		try {
			imageInfo = client.inspectImage(container.getImage());
		} catch (DockerException e) {
			// ignore
		} catch (InterruptedException e) {
			interrupt(e);
		}

		StringBuffer sb = new StringBuffer();
		appendLine(sb, "container ", container.getName(), ":");
		appendLine(sb, "  id: ", containerInfo.id());
		appendLine(sb, "  image: ", String.format("%s:%s", container.getImage(), container.getImageTag()));
		appendLine(sb, "  image id: ", containerInfo.image());
		if (imageInfo != null)
			appendLine(sb, "  image size: ", imageInfo.size().toString());
		else
			appendLine(sb, "  !! image not found");
		appendLine(sb, "  created: ", DATE_FORMAT.format(containerInfo.created()));
		appendLine(sb, "  restarts: ", containerInfo.restartCount().toString());
		appendLine(sb, "  running: ", containerInfo.state().running().toString());

		return sb.toString();
	}
}
