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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

public class YamlUtil {
	@SuppressWarnings("unchecked")
	public static List<String> getList(Object obj) {
		if (obj == null || !(obj instanceof List)) {
			return new ArrayList<>();
		}
		return (List<String>) obj;
	}

	@SuppressWarnings("unchecked")
	public static Map<Object, Object> loadYaml(String path) throws FileNotFoundException, YamlException {
		FileReader fileReader = null;
		YamlReader yamlReader = null;
		try {
			fileReader = new FileReader(path);
			yamlReader = new YamlReader(fileReader);
			return (Map<Object, Object>) yamlReader.read();
		} finally {
			IOUtils.closeQuietly(fileReader);
			if (yamlReader != null) {
				try {
					yamlReader.close();
				} catch (IOException e) {
					// ignored
				}
			}
		}
	}
}
