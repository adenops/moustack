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

package com.adenops.moustack.agent.model.openstack;

import java.util.List;

public class Error {
	private List<ErrorEmbedded> errors;
	private String message;
	private int code;
	private String type;

	public static class ErrorEmbedded {
		private String path;
		private String message;
		private String validator;

		public ErrorEmbedded() {
		}

		public ErrorEmbedded(String path, String message, String validator) {
			this.path = path;
			this.message = message;
			this.validator = validator;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getValidator() {
			return validator;
		}

		public void setValidator(String validator) {
			this.validator = validator;
		}
	}

	public List<ErrorEmbedded> getErrors() {
		return errors;
	}

	public void setErrors(List<ErrorEmbedded> errors) {
		this.errors = errors;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
