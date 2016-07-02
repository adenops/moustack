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
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.model.deployment.DeploymentFile;

public class DeploymentUtil {
	private static final Logger log = LoggerFactory.getLogger(DeploymentUtil.class);
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\@\\{([^}]+)\\}");
	private static final boolean ERROR_ON_MISSING_VAR = true;

	private static String replaceTokens(String value, Properties tokens) throws DeploymentException {
		Matcher matcher = TOKEN_PATTERN.matcher(value);

		StringBuilder builder = new StringBuilder();
		int i = 0;
		while (matcher.find()) {
			String replacement = (String) tokens.get(matcher.group(1));
			if (replacement == null) {
				if (ERROR_ON_MISSING_VAR)
					throw new DeploymentException("unknown variable " + matcher.group(1));
				log.error("unknown variable " + matcher.group(1));
				replacement = "";
			}

			builder.append(value.substring(i, matcher.start()));
			builder.append(replacement);

			i = matcher.end();
		}
		builder.append(value.substring(i, value.length()));
		return builder.toString();

	}

	private static boolean deployFile(StackConfig stack, DeploymentFile file) throws DeploymentException {
		boolean changed = false;
		File fileFrom = new File(file.getSource());
		File fileTo = new File(file.getTarget());

		// ensure the source file exists
		if (!fileFrom.exists())
			throw new DeploymentException("file " + file.getSource() + " not found");

		// load the new file and replace tokens
		String fileFromContent = FilesUtils.fileToString(file.getSource(), false);
		fileFromContent = replaceTokens(fileFromContent, stack.getProperties());

		boolean alreadyExists = fileTo.exists();
		if (!alreadyExists) {
			// if the file did not exist, ensure parent directory exists
			File parentFile = fileTo.getParentFile();
			if (parentFile.mkdirs())
				log.debug("created directory {}", parentFile);
		}

		String fileToContent = null;
		if (alreadyExists)
			// if the file already exists, load the content
			fileToContent = FilesUtils.fileToString(file.getTarget(), false);

		if (!fileFromContent.equals(fileToContent)) {
			// if content is different, update the file
			log.info("{} file {}", alreadyExists ? "creating" : "updating", file.getTarget());
			try {
				Files.write(fileTo.toPath(), fileFromContent.getBytes());
			} catch (IOException e) {
				throw new DeploymentException("cannot write file " + file.getTarget(), e);
			}
			changed = true;
		}

		changed |= updateFilePermissions(fileFrom, fileTo, alreadyExists);

		return changed;
	}

	private static String md5(String file) throws DeploymentException {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			return DigestUtils.md5Hex(fis);
		} catch (Exception e) {
			throw new DeploymentException("cannot compute MD5 sum for file " + file);
		} finally {
			IOUtils.closeQuietly(fis);
		}
	}

	private static boolean deployRawFile(StackConfig stack, DeploymentFile file) throws DeploymentException {
		boolean changed = false;
		File fileFrom = new File(file.getSource());
		File fileTo = new File(file.getTarget());

		// ensure the source file exists
		if (!fileFrom.exists())
			throw new DeploymentException("file " + file.getSource() + " not found");

		String md5Source = md5(file.getSource());

		String md5Target = null;
		boolean alreadyExists = fileTo.exists();
		if (!alreadyExists) {
			// if the file did not exist, ensure parent directory exists
			File parentFile = fileTo.getParentFile();
			if (parentFile.mkdirs())
				log.debug("created directory {}", parentFile);
		} else
			md5Target = md5(file.getTarget());

		if (!md5Source.equals(md5Target)) {
			log.info("{} file {}", alreadyExists ? "creating" : "updating", file.getTarget());
			try {
				FileUtils.copyFile(fileFrom, fileTo);
			} catch (IOException e) {
				throw new DeploymentException("cannot write file " + file.getTarget(), e);
			}
			changed = true;
		}

		changed |= updateFilePermissions(fileFrom, fileTo, alreadyExists);

		return changed;
	}

	private static boolean updateFilePermissions(File source, File target, boolean targetAlreadyExisted)
			throws DeploymentException {
		// now we check the permissions
		Set<PosixFilePermission> permissionsFrom = FilesUtils.getPermissions(source);
		Set<PosixFilePermission> permissionsTo = FilesUtils.getPermissions(target);

		// update permission if necessary
		if (!CollectionUtils.isEqualCollection(permissionsFrom, permissionsTo)) {
			if (targetAlreadyExisted)
				log.info("updating file permissions {}", target);
			FilesUtils.updatePermissions(target, permissionsFrom);
			return true;
		}

		return false;
	}

	public static boolean deployFiles(StackConfig stack, String module, List<DeploymentFile> files)
			throws DeploymentException {
		boolean changed = false;
		log.debug("deploying module " + module + " files");

		for (DeploymentFile file : files) {
			if (file.isParse())
				changed |= deployFile(stack, file);
			else
				changed |= deployRawFile(stack, file);
		}

		return changed;
	}
}
