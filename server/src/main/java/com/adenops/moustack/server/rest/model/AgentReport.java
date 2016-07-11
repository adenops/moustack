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

package com.adenops.moustack.server.rest.model;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "report")
public class AgentReport implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Reason {
		UPDATE_SUCCESS, UPDATE_NOCHANGE, UPDATE_FAILURE, SYSTEM_STATUS;
	}

	@Id
	@GeneratedValue(generator = "increment")
	@GenericGenerator(name = "increment", strategy = "increment")
	private long id;

	@NotNull(message = "hostname must be set")
	private String hostname;

	@Temporal(TemporalType.TIMESTAMP)
	@Type(type = "timestamp")
	@Column(name = "date")
	@NotNull(message = "date must be set")
	private Date date;

	@NotNull(message = "reason must be set")
	private Reason reason;

	@Column(columnDefinition = "LONGTEXT")
	private String content;

	public AgentReport() {
	}

	public AgentReport(long id, String hostname, Date date, Reason reason) {
		this.id = id;
		this.hostname = hostname;
		this.date = date;
		this.reason = reason;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public Reason getReason() {
		return reason;
	}

	public void setReason(Reason reason) {
		this.reason = reason;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
