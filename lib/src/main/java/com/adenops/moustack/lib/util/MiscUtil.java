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

package com.adenops.moustack.lib.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

import com.adenops.moustack.lib.model.ApplicationInfo;

public class MiscUtil {
	public static ApplicationInfo loadApplicationInfo(String defaultName) {
		ApplicationInfo info = new ApplicationInfo();

		Properties buildProperties = new Properties();
		InputStream is = null;
		try {
			is = MiscUtil.class.getClassLoader().getResourceAsStream("build.properties");
			if (is != null)
				buildProperties.load(is);
		} catch (IOException e) {
			IOUtils.closeQuietly(is);
		}

		info.setApplicationName(buildProperties.getProperty("application", defaultName));
		info.setDisplayName(buildProperties.getProperty("name", defaultName));
		info.setDescription(buildProperties.getProperty("description", "No description"));
		info.setVersion(buildProperties.getProperty("version", "0.1-SNAPSHOT"));
		info.setUrl(buildProperties.getProperty("url", "http://adenops.com/"));
		info.setBuild(buildProperties.getProperty("build", String.valueOf(new Date().getTime())));

		return info;
	}
}
