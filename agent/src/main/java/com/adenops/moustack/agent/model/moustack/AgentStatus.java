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

package com.adenops.moustack.agent.model.moustack;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Date;

// Generated class, DO NOT MODIFY
public class AgentStatus {

	private String hostname = null;
	private Date date = null;

	public enum StatusEnum {
		STANDBY("STANDBY"), UPDATING("UPDATING"), SHUTDOWN("SHUTDOWN");

		private String value;

		StatusEnum(String value) {
			this.value = value;
		}

		@Override
		@JsonValue
		public String toString() {
			return value;
		}
	}

	private StatusEnum status = null;

	/**
	 **/
	public AgentStatus hostname(String hostname) {
		this.hostname = hostname;
		return this;
	}

	@JsonProperty("hostname")
	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	/**
	 **/
	public AgentStatus date(Date date) {
		this.date = date;
		return this;
	}

	@JsonProperty("date")
	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	/**
	 **/
	public AgentStatus status(StatusEnum status) {
		this.status = status;
		return this;
	}

	@JsonProperty("status")
	public StatusEnum getStatus() {
		return status;
	}

	public void setStatus(StatusEnum status) {
		this.status = status;
	}

	@Override
	public boolean equals(java.lang.Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AgentStatus agentStatus = (AgentStatus) o;

		return true && Objects.equals(hostname, agentStatus.hostname) && Objects.equals(date, agentStatus.date)
				&& Objects.equals(status, agentStatus.status);
	}

	@Override
	public int hashCode() {
		return Objects.hash(hostname, date, status);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class AgentStatus {\n");

		sb.append("    hostname: ").append(toIndentedString(hostname)).append("\n");
		sb.append("    date: ").append(toIndentedString(date)).append("\n");
		sb.append("    status: ").append(toIndentedString(status)).append("\n");
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Convert the given object to string with each line indented by 4 spaces
	 * (except the first line).
	 */
	private String toIndentedString(java.lang.Object o) {
		if (o == null) {
			return "null";
		}
		return o.toString().replace("\n", "\n    ");
	}
}
