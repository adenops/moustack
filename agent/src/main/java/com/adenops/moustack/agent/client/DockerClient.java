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
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.docker.Volume;
import com.adenops.moustack.agent.module.ContainerModule;
import com.adenops.moustack.agent.util.PathUtil;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.LogConfig.LoggingType;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;

/*
 * Note: starting with Docker 1.10 there will be a --tmpfs option that we should
 * use combined with --read-only to have more "production" containers, for example:
 * docker run -d --read-only --tmpfs /run --tmpfs /tmp IMAGE
 * Currently we manually scrap /tmp and /run during the container start to ensure
 * a clean state which is important for some applications like networking that
 * maintain its state in /run/network/ifstate.
 *
 */
public class DockerClient {
	private static final Logger log = LoggerFactory.getLogger(DockerClient.class);
	private static final String DOCKER_URI = "unix:///var/run/docker.sock";
	private final int WAIT_EPHEMERAL_TIMEOUT = 60 * 1000;
	private final com.github.dockerjava.api.DockerClient client;
	private final StackConfig stack;

	public DockerClient(StackConfig stack) throws DeploymentException {
		log.debug("initializing Docker client");

		this.stack = stack;

		// TODO: this should be configurable
		DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder().withDockerHost(DOCKER_URI)
				.withDockerTlsVerify(false).withApiVersion("1.22").build();

		client = DockerClientBuilder.getInstance(config).build();

		Info info = client.infoCmd().exec();
		log.debug("images: " + info.getImages());
		log.debug("containers: " + info.getContainers());
	}

	public void stopContainers(List<ContainerModule> containers) {
		for (ContainerModule container : containers)
			stopContainer(container);
	}

	public void stopContainer(ContainerModule container) {
		try {
			client.stopContainerCmd(container.getName()).exec();
			log.debug("stopped container " + container.getName());
		} catch (NotFoundException | NotModifiedException e) {
		}
		removeContainer(container);
	}

	private void removeContainerRetry(ContainerModule container) {
		try {
			// retry to remove container because of docker bug https://github.com/docker/docker/issues/14474
			// this may also be related to cadvisor bug https://github.com/google/cadvisor/issues/771
			for (int i = 0; i < 3; i++) {
				try {
					client.removeContainerCmd(container.getName()).withForce(true).exec();
					return;
				} catch (InternalServerErrorException e) {
					log.error("failed to remove container " + container.getImage());
				}
			}
			// try one last time to trigger the exception if we fail
			client.removeContainerCmd(container.getName()).withForce(true).exec();
		} catch (NotFoundException | NotModifiedException e) {
		}
	}

	private void removeContainer(ContainerModule container) {
		try {
			removeContainerRetry(container);
			log.debug("removed container " + container.getName());
		} catch (NotFoundException | NotModifiedException e) {
		}
	}

	@SuppressWarnings("unused")
	private String asString(InputStream response) {
		return consumeAsString(response);
	}

	private String consumeAsString(InputStream response) {

		StringWriter logwriter = new StringWriter();

		try {
			LineIterator itr = IOUtils.lineIterator(response, "UTF-8");

			while (itr.hasNext()) {
				String line = itr.next();
				logwriter.write(line + (itr.hasNext() ? "\n" : ""));
				log.trace("line: " + line);
			}
			response.close();

			return logwriter.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(response);
		}
	}

	private void appendLine(StringBuffer sb, String... strings) {
		for (String string : strings)
			sb.append(string);
		sb.append("\n");
	}

	public boolean containerIsRunning(ContainerModule container) {
		InspectContainerResponse containerInspect = null;
		try {
			// inspect container and retrieve corresponding image id
			containerInspect = client.inspectContainerCmd(container.getName()).exec();
		} catch (NotFoundException e) {
			return false;
		}
		return containerInspect.getState().getRunning();
	}

	public String getContainerInfo(ContainerModule container) {
		InspectContainerResponse containerInspect = null;
		try {
			// inspect container and retrieve corresponding image id
			containerInspect = client.inspectContainerCmd(container.getName()).exec();
		} catch (NotFoundException e) {
			return "container " + container.getName() + " not found\n";
		}
		StringBuffer sb = new StringBuffer();
		appendLine(sb, "container ", container.getName(), ":");
		appendLine(sb, "  id: ", containerInspect.getId());
		appendLine(sb, "  image id: ", containerInspect.getImageId());
		try {
			InspectImageResponse imageInspect = client.inspectImageCmd(containerInspect.getImageId()).exec();
			appendLine(sb, "  image size: ", String.valueOf(imageInspect.getSize()));
		} catch (NotFoundException e) {
			appendLine(sb, "  !! image not found");
		}

		appendLine(sb, "  created: ", containerInspect.getCreated());
		appendLine(sb, "  running: ", String.valueOf(containerInspect.getState().getRunning()));

		return sb.toString();
	}

	/*
	 * TODO: handle change in container name (by checking image names).
	 */
	public boolean containerCheckUpdate(ContainerModule container) throws DeploymentException {
		String containerImageId = null;
		InspectContainerResponse containerInspect = null;
		boolean needRestart = false;

		try {
			// inspect container and retrieve corresponding image id
			containerInspect = client.inspectContainerCmd(container.getName()).exec();
			containerImageId = containerInspect.getImageId();
		} catch (NotFoundException e) {
			needRestart = true;
			log.debug("container " + container.getName() + " does not exist");
		}

		// check if the container is running
		if (containerInspect != null && !containerInspect.getState().getRunning()) {
			log.warn("container " + container.getName() + " exists but is not running");
			needRestart = true;
		}

		// compare privileged value
		if (containerInspect != null && containerInspect.getHostConfig().getPrivileged() != container.isPrivileged()) {
			log.info("container " + container.getName() + " privilege changed");
			needRestart = true;
		}

		// compare log driver value
		if (containerInspect != null) {
			boolean currentSyslog = containerInspect.getHostConfig().getLogConfig().getType()
					.equals(LoggingType.SYSLOG);
			if (currentSyslog != container.isSyslog()) {
				log.info("container " + container.getName() + " syslog changed");
				needRestart = true;
			}
		}

		// compare volume entries
		if (containerInspect != null && !needRestart) {
			Set<String> currentVolumes = new HashSet<>();
			for (Bind bind : containerInspect.getHostConfig().getBinds())
				currentVolumes.add(bind.getPath() + ":" + bind.getVolume().getPath() + ":" + bind.getAccessMode());

			Set<String> newVolumes = new HashSet<>();
			for (Volume volume : container.getVolumes())
				newVolumes.add(volume.getHostPath() + ":" + volume.getGuestPath() + ":" + volume.getMode());

			if (!currentVolumes.equals(newVolumes)) {
				log.info("container " + container.getName() + " volumes changed");
				needRestart = true;
			}
		}

		// compare devices
		if (containerInspect != null && !needRestart) {
			Set<String> currentDevices = new HashSet<>();
			if (containerInspect.getHostConfig().getDevices() != null) {
				for (Device device : containerInspect.getHostConfig().getDevices())
					currentDevices.add(device.getPathOnHost());
			}

			Set<String> newDevices = new HashSet<>(container.getDevices());

			if (!currentDevices.equals(newDevices)) {
				log.info("container " + container.getName() + " devices changed");
				needRestart = true;
			}
		}

		// compare capabilities
		if (containerInspect != null && !needRestart) {
			Set<Capability> currentCapabilities = new HashSet<>();
			for (Capability capability : containerInspect.getHostConfig().getCapAdd())
				currentCapabilities.add(capability);

			Set<Capability> newCapabilities = new HashSet<>(container.getCapabilities());

			if (!currentCapabilities.equals(newCapabilities)) {
				log.info("container " + container.getName() + " capabilities changed");
				needRestart = true;
			}
		}

		// if it is a tagged image, we check if we need to pull
		boolean needPull = false;
		if (container.getImage().endsWith(":latest")) {
			// always pull if latest (dev)
			needPull = true;
		} else {
			try {
				InspectImageResponse imageInspect = client.inspectImageCmd(container.getImage()).exec();
				String imageId = imageInspect.getId();

				if (!imageId.equals(containerImageId)) {
					log.info("tagged image " + container.getImage() + " has changed");
					needPull = true;
				}

			} catch (NotFoundException e) {
				needPull = true;
			}
		}

		// if no change and no new image, the container is considered up-to-date
		if (!needRestart && !needPull) {
			log.debug("container " + container.getName() + " is up-to-date");
			return false;
		}

		// pull image from docker registry
		if (needPull) {
			log.debug("pulling image " + container.getImage());
			try {
				client.pullImageCmd(container.getImage()).exec(new PullImageResultCallback() {
					// override awaitSuccess because the upstream one is not working
					// XXX: this is not good enough, it won't detect if it failed with a 'latest' image
					@Override
					public void awaitSuccess() {
						try {
							awaitCompletion();
						} catch (InterruptedException e) {
							throw new DockerClientException("image pulling interrupted for " + container.getImage(), e);
						}

						try {
							// use inspect to check if the image is present
							client.inspectImageCmd(container.getImage()).exec();
						} catch (NotFoundException e) {
							throw new DockerClientException("Could not pull docker image " + container.getImage(), e);
						}
					}
				}).awaitSuccess();
			} catch (DockerClientException e) {
				throw new DeploymentException(e.getMessage(), e);
			}
		}

		// if no change, check if the image has been updated
		if (!needRestart) {
			InspectImageResponse imageInspect = client.inspectImageCmd(container.getImage()).exec();
			String imageId = imageInspect.getId();

			// if the image is the same, we can stop here
			if (imageId.equals(containerImageId)) {
				log.debug("image " + container.getImage() + " did not change");
				return false;
			}
		}

		// if we got here, need a restart
		return true;
	}

	public void startOrRestartContainers(List<ContainerModule> containers) throws DeploymentException {
		for (ContainerModule container : containers)
			startOrRestartContainer(container);
	}

	public void startOrRestartContainer(ContainerModule container) throws DeploymentException {
		log.info("updating container " + container.getName());
		startOrRestartContainer(container, false, new String[] {});
	}

	public void startEphemeralContainer(ContainerModule container, String user, String... command)
			throws DeploymentException {
		String name = container.getName() + "-" + RandomStringUtils.random(10, true, true);
		String[] suCommand = new String[] { "su", "-s", "/bin/sh", "-c", String.join(" ", command), user };

		ContainerModule temporaryContainer = new ContainerModule(name, container);

		log.debug("executing command " + String.join(" ", command));
		startOrRestartContainer(temporaryContainer, true, suCommand);
		try {
			Integer code = client.waitContainerCmd(name).exec(new WaitContainerResultCallback())
					.awaitStatusCode(WAIT_EPHEMERAL_TIMEOUT, TimeUnit.MILLISECONDS);
			if (code != 0)
				throw new DockerClientException("ephemeral container " + container.getImage() + " execution returned "
						+ code + " for command " + String.join(" ", suCommand));
		} catch (DockerClientException e) {
			throw new DockerClientException("error while waiting for container " + container.getImage(), e);
		}
		removeContainer(temporaryContainer);
	}

	/*
	 * TODO: handle change in container name (by checking image names).
	 */
	private void startOrRestartContainer(ContainerModule container, boolean ephemeral, String... command)
			throws DeploymentException {
		// prepare container creation command
		CreateContainerCmd createContainerCmd = client.createContainerCmd(container.getImage())
				.withCapAdd(container.getCapabilities().toArray(new Capability[container.getCapabilities().size()]))
				.withNetworkMode("host").withPrivileged(container.isPrivileged()).withName(container.getName());

		if (!ephemeral) {
			// auto restart container
			createContainerCmd.withRestartPolicy(RestartPolicy.alwaysRestart());
		}

		if (!ephemeral && container.isSyslog()) {
			// enable syslog logging driver
			Map<String, String> logOptions = new HashMap<>();
			logOptions.put("syslog-address", "udp://127.0.0.1:50014");
			logOptions.put("syslog-tag", container.getName());
			logOptions.put("syslog-format", "rfc3164");
			createContainerCmd.withLogConfig(new LogConfig(LoggingType.SYSLOG, logOptions));
		}

		if (command.length > 0)
			createContainerCmd.withCmd(command);

		// prepare devices list
		List<Device> devices = new ArrayList<>();
		for (String device : container.getDevices())
			devices.add(new Device("rwm", device, device));
		createContainerCmd.withDevices(devices.toArray(new Device[devices.size()]));

		// prepare volumes binds
		// XXX: pretty sure we can do that a lot cleaner
		List<Volume> volumes = container.getVolumes();
		List<Bind> binds = new ArrayList<>();
		for (Volume volume : volumes)
			binds.add(new Bind(volume.getHostPath(), new com.github.dockerjava.api.model.Volume(volume.getGuestPath()),
					volume.getMode()));
		createContainerCmd.withBinds(binds.toArray(new Bind[binds.size()]));

		// prepare environment
		// no env-file, so we need to read generated env files on the host filesystem and generate the environment...
		List<String> environment = new ArrayList<>();
		for (String envFile : container.getEnvironments()) {
			File file = new File(PathUtil.getContainerTargetFilePath(stack, container.getName(), envFile));
			try {
				environment.addAll(FileUtils.readLines(file));
			} catch (IOException e) {
				throw new DeploymentException("error while reading envfile " + file);
			}
		}
		createContainerCmd.withEnv(environment.toArray(new String[environment.size()]));

		// first we stop and delete existing container
		stopContainer(container);

		CreateContainerResponse dockerContainer = createContainerCmd.exec();
		client.startContainerCmd(dockerContainer.getId()).exec();

		log.debug("started container " + container.getName());
	}
}
