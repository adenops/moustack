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

package com.adenops.moustack.agent.model.keystone;

public class OSAuth {
	private Identity identity;

	public OSAuth() {
	}

	public OSAuth(String id, String password) {
		this.identity = new Identity(id, password);
	}

	public class Identity {
		private String[] methods = new String[] { "password" };
		private Password password;

		public Identity(String id, String password) {
			this.password = new Password(id, password);
		}

		public class Password {
			private User user;

			public Password() {
			}

			public Password(String id, String password) {
				this.user = new User(id, password);
			}

			public class User {
				private String id;
				private String password;

				public User() {
				}

				public User(String id, String password) {
					this.id = id;
					this.password = password;
				}

				public String getId() {
					return id;
				}

				public void setId(String id) {
					this.id = id;
				}

				public String getPassword() {
					return password;
				}

				public void setPassword(String password) {
					this.password = password;
				}
			}

			public User getUser() {
				return user;
			}

			public void setUser(User user) {
				this.user = user;
			}
		}

		public Password getPassword() {
			return password;
		}

		public void setPassword(Password password) {
			this.password = password;
		}

		public String[] getMethods() {
			return methods;
		}

		public void setMethods(String[] methods) {
			this.methods = methods;
		}
	}

	public Identity getIdentity() {
		return identity;
	}

	public void setIdentity(Identity identity) {
		this.identity = identity;
	}
}
