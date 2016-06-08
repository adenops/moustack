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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.AgentConfig;
import com.adenops.moustack.agent.config.StackConfig;

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

	// TODO: use something more efficient like MD5 comparison
	private static boolean deployFile(StackConfig stack, String fileFrom, String fileTo) throws DeploymentException {
		// ensure the source file exists
		if (!new File(fileFrom).exists())
			throw new DeploymentException("file " + fileFrom + " not found");

		// load the new file and replace tokens
		String fileFromContent = FilesUtils.fileToString(fileFrom, false);
		fileFromContent = replaceTokens(fileFromContent, stack.getProperties());

		if (new File(fileTo).exists()) {
			// if the target file already exists, compare the content

			// load old file
			String fileToContent = FilesUtils.fileToString(fileTo, false);

			// if content is the same, we are done
			if (fileFromContent.equals(fileToContent)) {
				log.trace("file " + fileToContent + " did not change");
				return false;
			}
		} else {
			// if the file did not exist, ensure parent directory exists
			File parentFile = new File(fileTo).getParentFile();
			if (parentFile.mkdirs())
				log.debug("created directory " + parentFile);
		}

		log.info("updating " + fileTo);
		try {
			Files.write(Paths.get(fileTo), fileFromContent.getBytes());
		} catch (IOException e) {
			throw new DeploymentException("cannot write file " + fileTo, e);
		}

		return true;
	}

	public static boolean deploySystemFiles(StackConfig stack, String module, List<String> files)
			throws DeploymentException {
		boolean changed = false;
		log.debug("deploying module " + module + " host files");

		for (String file : files) {
			String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), module, file);
			String to = PathUtil.getSystemTargetFilePath(AgentConfig.getInstance(), file);

			changed |= deployFile(stack, from, to);
		}

		return changed;
	}

	public static boolean deployContainerFiles(StackConfig stack, String module, List<String> files)
			throws DeploymentException {
		boolean changed = false;
		log.debug("deploying module " + module + " containers files");

		for (String file : files) {
			String from = PathUtil.getModuleSourceFilePath(AgentConfig.getInstance(), module, file);
			String to = PathUtil.getContainerTargetFilePath(stack, module, file);

			changed |= deployFile(stack, from, to);
		}

		return changed;
	}
}
