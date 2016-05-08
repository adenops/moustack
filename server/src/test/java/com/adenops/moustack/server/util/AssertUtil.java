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

package com.adenops.moustack.server.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

public class AssertUtil {
	public static void assertJsonEquals(String data, String path, String expected) {
		assertThat(data).isNotEmpty();
		String status = null;
		try {
			status = JsonPath.read(data, path);
		} catch (InvalidPathException e) {
			fail("could not apply path " + path + " to json " + data);
		}
		assertThat(status).isEqualTo(expected);
	}

	public static void assertJsonContains(String data, String path, String expected) {
		assertThat(data).isNotEmpty();
		String status = null;
		try {
			status = JsonPath.read(data, path);
		} catch (InvalidPathException e) {
			fail("could not apply path " + path + " to json " + data);
		}
		assertThat(status).contains(expected);
	}
}
