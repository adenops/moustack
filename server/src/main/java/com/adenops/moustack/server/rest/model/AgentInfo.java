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

/*
 * Composite object to get all the information in one request
 */
public class AgentInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	private String hostname;
	private boolean isConnected;
	private AgentStatus.Status lastStatus;
	private Date lastStatusDate;
	private AgentReport.Reason lastReportResult;
	private Date lastReportDate;
	private long lastReportId;

	public AgentInfo() {
	}

	public AgentInfo(String hostname) {
		this.hostname = hostname;
	}

	public String getHostname() {
		return hostname;
	}

	public boolean isConnected() {
		return isConnected;
	}

	public void setConnected(boolean isConnected) {
		this.isConnected = isConnected;
	}

	public long getLastReportId() {
		return lastReportId;
	}

	public void setLastReportId(long lastReportId) {
		this.lastReportId = lastReportId;
	}

	public AgentReport.Reason getLastReportResult() {
		return lastReportResult;
	}

	public void setLastReportResult(AgentReport.Reason lastReportResult) {
		this.lastReportResult = lastReportResult;
	}

	public Date getLastReportDate() {
		return lastReportDate;
	}

	public void setLastReportDate(Date lastReportDate) {
		this.lastReportDate = lastReportDate;
	}

	public AgentStatus.Status getLastStatus() {
		return lastStatus;
	}

	public void setLastStatus(AgentStatus.Status lastStatus) {
		this.lastStatus = lastStatus;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public Date getLastStatusDate() {
		return lastStatusDate;
	}

	public void setLastStatusDate(Date lastStatusDate) {
		this.lastStatusDate = lastStatusDate;
	}
}
