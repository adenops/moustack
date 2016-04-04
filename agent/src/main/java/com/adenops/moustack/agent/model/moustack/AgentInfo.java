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
public class AgentInfo   {

  private String hostname = null;


  public enum LastStatusEnum {
    STANDBY("STANDBY"),
    UPDATING("UPDATING"),
    SHUTDOWN("SHUTDOWN");

    private String value;

    LastStatusEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return value;
    }
  }

  private LastStatusEnum lastStatus = null;
  private Date lastStatusDate = null;


  public enum LastReportResultEnum {
    UPDATE_SUCCESS("UPDATE_SUCCESS"),
    UPDATE_NOCHANGE("UPDATE_NOCHANGE"),
    UPDATE_FAILURE("UPDATE_FAILURE"),
    SYSTEM_STATUS("SYSTEM_STATUS");

    private String value;

    LastReportResultEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return value;
    }
  }

  private LastReportResultEnum lastReportResult = null;
  private Date lastReportDate = null;
  private Long lastReportId = null;
  private Boolean connected = false;


  /**
   **/
  public AgentInfo hostname(String hostname) {
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
  public AgentInfo lastStatus(LastStatusEnum lastStatus) {
    this.lastStatus = lastStatus;
    return this;
  }


  @JsonProperty("lastStatus")
  public LastStatusEnum getLastStatus() {
    return lastStatus;
  }
  public void setLastStatus(LastStatusEnum lastStatus) {
    this.lastStatus = lastStatus;
  }


  /**
   **/
  public AgentInfo lastStatusDate(Date lastStatusDate) {
    this.lastStatusDate = lastStatusDate;
    return this;
  }


  @JsonProperty("lastStatusDate")
  public Date getLastStatusDate() {
    return lastStatusDate;
  }
  public void setLastStatusDate(Date lastStatusDate) {
    this.lastStatusDate = lastStatusDate;
  }


  /**
   **/
  public AgentInfo lastReportResult(LastReportResultEnum lastReportResult) {
    this.lastReportResult = lastReportResult;
    return this;
  }


  @JsonProperty("lastReportResult")
  public LastReportResultEnum getLastReportResult() {
    return lastReportResult;
  }
  public void setLastReportResult(LastReportResultEnum lastReportResult) {
    this.lastReportResult = lastReportResult;
  }


  /**
   **/
  public AgentInfo lastReportDate(Date lastReportDate) {
    this.lastReportDate = lastReportDate;
    return this;
  }


  @JsonProperty("lastReportDate")
  public Date getLastReportDate() {
    return lastReportDate;
  }
  public void setLastReportDate(Date lastReportDate) {
    this.lastReportDate = lastReportDate;
  }


  /**
   **/
  public AgentInfo lastReportId(Long lastReportId) {
    this.lastReportId = lastReportId;
    return this;
  }


  @JsonProperty("lastReportId")
  public Long getLastReportId() {
    return lastReportId;
  }
  public void setLastReportId(Long lastReportId) {
    this.lastReportId = lastReportId;
  }


  /**
   **/
  public AgentInfo connected(Boolean connected) {
    this.connected = connected;
    return this;
  }


  @JsonProperty("connected")
  public Boolean getConnected() {
    return connected;
  }
  public void setConnected(Boolean connected) {
    this.connected = connected;
  }



  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AgentInfo agentInfo = (AgentInfo) o;

    return true && Objects.equals(hostname, agentInfo.hostname) &&
        Objects.equals(lastStatus, agentInfo.lastStatus) &&
        Objects.equals(lastStatusDate, agentInfo.lastStatusDate) &&
        Objects.equals(lastReportResult, agentInfo.lastReportResult) &&
        Objects.equals(lastReportDate, agentInfo.lastReportDate) &&
        Objects.equals(lastReportId, agentInfo.lastReportId) &&
        Objects.equals(connected, agentInfo.connected)
    ;
  }

  @Override
  public int hashCode() {
    return Objects.hash(hostname, lastStatus, lastStatusDate, lastReportResult, lastReportDate, lastReportId, connected);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AgentInfo {\n");

    sb.append("    hostname: ").append(toIndentedString(hostname)).append("\n");
    sb.append("    lastStatus: ").append(toIndentedString(lastStatus)).append("\n");
    sb.append("    lastStatusDate: ").append(toIndentedString(lastStatusDate)).append("\n");
    sb.append("    lastReportResult: ").append(toIndentedString(lastReportResult)).append("\n");
    sb.append("    lastReportDate: ").append(toIndentedString(lastReportDate)).append("\n");
    sb.append("    lastReportId: ").append(toIndentedString(lastReportId)).append("\n");
    sb.append("    connected: ").append(toIndentedString(connected)).append("\n");
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
