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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;

public class FilesUtils {
	private static final Logger log = LoggerFactory.getLogger(FilesUtils.class);

	public static String fileToString(String path, boolean isResource) throws DeploymentException {
		try {
			return IOUtils.toString(isResource ? DeploymentUtil.class.getResourceAsStream(path) : new FileInputStream(
					path), "UTF-8");
		} catch (IOException e) {
			throw new DeploymentException("error while reading file " + path, e);
		}
	}

	public static Set<PosixFilePermission> getPermissions(File file) throws DeploymentException {
		try {
			return Files.getPosixFilePermissions(file.toPath());
		} catch (IOException e) {
			throw new DeploymentException("error while getting file permissions " + file, e);
		}
	}

	public static void updatePermissions(File file, Set<PosixFilePermission> permissions) throws DeploymentException {
		try {
			Files.setPosixFilePermissions(file.toPath(), permissions);
		} catch (IOException e) {
			throw new DeploymentException("error while setting file permissions " + file, e);
		}
	}

	public static boolean chown(File file, String owner, String group) throws DeploymentException {
		boolean changed = false;

		if (!file.exists())
			throw new DeploymentException("cannot chown path " + file + " (not found)");

		PosixFileAttributeView view = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class);
		PosixFileAttributes attributes;
		try {
			attributes = view.readAttributes();
		} catch (IOException e) {
			throw new DeploymentException("error while reading attributes from file " + file, e);
		}
		UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
		if (!attributes.owner().getName().equals(owner)) {
			log.info("set owner " + owner + " on " + file);
			UserPrincipal userPrincipal;
			try {
				userPrincipal = lookupService.lookupPrincipalByName(owner);
			} catch (IOException e) {
				throw new DeploymentException("error while getting owner for file " + file, e);
			}
			try {
				view.setOwner(userPrincipal);
			} catch (IOException e) {
				throw new DeploymentException("error while setting owner " + owner + " for file " + file, e);
			}
			changed = true;
		}

		if (!attributes.group().getName().equals(owner)) {
			log.info("set group {} on {}", group, file);
			GroupPrincipal groupPrincipal;
			try {
				groupPrincipal = lookupService.lookupPrincipalByGroupName(group);
			} catch (IOException e) {
				throw new DeploymentException("error while getting group for file " + file, e);
			}
			try {
				view.setGroup(groupPrincipal);
			} catch (IOException e) {
				throw new DeploymentException("error while setting group " + group + " for file " + file, e);
			}
			changed = true;
		}

		return changed;
	}

	public static boolean mkdir(String pathStr) {
		boolean changed = false;

		File path = new File(pathStr);
		if (!path.exists()) {
			log.info("create directory " + pathStr);
			path.mkdirs();
			changed = true;
		}

		return changed;
	}
}
