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

package com.adenops.moustack.agent.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;

public class GitUtil {
	private static final Logger log = LoggerFactory.getLogger(GitUtil.class);

	private static boolean RESET = false;

	private static OutputStream outputWrapper() {
		return new OutputStream() {
			private final transient ByteArrayOutputStream buffer = new ByteArrayOutputStream();

			@Override
			public void write(final int data) throws IOException {
				if (data == '\n') {
					log.info(this.buffer.toString("UTF-8"));
					this.buffer.reset();
				} else {
					this.buffer.write(data);
				}
			}
		};
	}

	public static void synchronizeConfiguration(StackConfig stack) throws DeploymentException {
		File repoDir = new File(AgentConfig.getInstance().getConfigDir());

		CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(AgentConfig.getInstance()
				.getUser(), AgentConfig.getInstance().getPassword());

		if (!AgentConfig.getInstance().isSslVerify())
			HttpUtil.disableSSLVerification();

		if (RESET) {
			log.info("removing directory " + repoDir);
			try {
				FileUtils.deleteDirectory(repoDir);
			} catch (IOException e) {
				throw new DeploymentException("cannot delete directory " + repoDir, e);
			}
		}

		Git git;
		try {
			git = Git.init().setDirectory(repoDir).call();
		} catch (IllegalStateException | GitAPIException e) {
			throw new DeploymentException("cannot initialize git repository " + repoDir, e);
		}

		StoredConfig config = git.getRepository().getConfig();
		String remote = config.getString("remote", "origin", "url");
		if (remote == null || !remote.equals(stack.getGitRepo())) {
			if (remote == null)
				log.debug("setting remote to " + stack.getGitRepo());
			else
				log.debug("updating remote to " + stack.getGitRepo());

			if (!AgentConfig.getInstance().isSslVerify())
				config.setString("http", null, "sslVerify", "false");

			config.setString("remote", "origin", "url", stack.getGitRepo());
			try {
				config.save();
			} catch (IOException e) {
				throw new DeploymentException("cannot save git configuration for " + repoDir);
			}
		}

		ObjectId oldHead;
		try {
			oldHead = git.getRepository().resolve(Constants.HEAD);
		} catch (RevisionSyntaxException | IOException e) {
			throw new DeploymentException("error while trying to resolve git HEAD", e);
		}

		log.debug("fetching configuration");
		try {
			git.fetch()
					.setCredentialsProvider(credentialsProvider)
					.setRefSpecs(
							new RefSpec("+refs/heads/" + stack.getGitBranch() + ":refs/remotes/origin/"
									+ stack.getGitBranch())).call();
		} catch (GitAPIException e) {
			throw new DeploymentException("error while fetching from remote git repository", e);
		}

		try {
			git.checkout().setName("origin/" + stack.getGitBranch()).call();
		} catch (GitAPIException e) {
			throw new DeploymentException("error while checkout of the branch " + stack.getGitBranch(), e);
		}

		ObjectId newHead;
		try {
			newHead = git.getRepository().resolve(Constants.HEAD);
		} catch (RevisionSyntaxException | IOException e) {
			throw new DeploymentException("error while resolving git HEAD", e);
		}

		if (oldHead != null) {
			DiffFormatter formatter = new DiffFormatter(outputWrapper());
			formatter.setRepository(git.getRepository());

			List<DiffEntry> diff;
			try {
				diff = formatter.scan(oldHead, newHead);
			} catch (IOException e) {
				formatter.close();
				throw new DeploymentException("cannot diff commits " + oldHead.getName() + " and " + newHead.getName(),
						e);
			}
			if (!diff.isEmpty()) {
				log.info("configuration changes:");
				for (DiffEntry entry : diff) {
					try {
						formatter.format(entry);
					} catch (IOException e) {
						formatter.close();
						throw new DeploymentException("cannot format git diff", e);
					}
				}
			}

			formatter.close();
		} else
			log.info("new configuration created");

		stack.setGitHead(newHead.name());
		log.info("configuration version: " + stack.getGitHead());

		git.close();
	}
}
