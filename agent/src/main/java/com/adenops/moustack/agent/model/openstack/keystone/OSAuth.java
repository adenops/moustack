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

package com.adenops.moustack.agent.model.openstack.keystone;

public class OSAuth {
	private Identity identity;

	public OSAuth() {
	}

	public OSAuth(String name, String password, String domainId) {
		this.identity = new Identity(name, password, domainId);
	}

	public class Identity {
		private String[] methods = new String[] { "password" };
		private Password password;

		public Identity(String id, String password, String domainId) {
			this.password = new Password(id, password, domainId);
		}

		public class Password {
			private User user;

			public Password() {
			}

			public Password(String name, String password, String domainId) {
				this.user = new User(name, password, domainId);
			}

			public class User {
				private String name;
				private String password;
				private Domain domain;

				public User() {
				}

				public User(String name, String password, String domainId) {
					this.name = name;
					this.password = password;
					this.domain = new Domain(domainId, null);
				}

				public String getName() {
					return name;
				}

				public void setName(String name) {
					this.name = name;
				}

				public String getPassword() {
					return password;
				}

				public void setPassword(String password) {
					this.password = password;
				}

				public Domain getDomain() {
					return domain;
				}

				public void setDomain(Domain domain) {
					this.domain = domain;
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
