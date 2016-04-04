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
public class AgentReport   {

  private Long id = null;
  private String hostname = null;
  private Date date = null;


  public enum ReasonEnum {
    UPDATE_SUCCESS("UPDATE_SUCCESS"),
    UPDATE_NOCHANGE("UPDATE_NOCHANGE"),
    UPDATE_FAILURE("UPDATE_FAILURE"),
    SYSTEM_STATUS("SYSTEM_STATUS");

    private String value;

    ReasonEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return value;
    }
  }

  private ReasonEnum reason = null;
  private String content = null;


  /**
   **/
  public AgentReport id(Long id) {
    this.id = id;
    return this;
  }


  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }


  /**
   **/
  public AgentReport hostname(String hostname) {
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
  public AgentReport date(Date date) {
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
  public AgentReport reason(ReasonEnum reason) {
    this.reason = reason;
    return this;
  }


  @JsonProperty("reason")
  public ReasonEnum getReason() {
    return reason;
  }
  public void setReason(ReasonEnum reason) {
    this.reason = reason;
  }


  /**
   **/
  public AgentReport content(String content) {
    this.content = content;
    return this;
  }


  @JsonProperty("content")
  public String getContent() {
    return content;
  }
  public void setContent(String content) {
    this.content = content;
  }



  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AgentReport agentReport = (AgentReport) o;

    return true && Objects.equals(id, agentReport.id) &&
        Objects.equals(hostname, agentReport.hostname) &&
        Objects.equals(date, agentReport.date) &&
        Objects.equals(reason, agentReport.reason) &&
        Objects.equals(content, agentReport.content)
    ;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, hostname, date, reason, content);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AgentReport {\n");

    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    hostname: ").append(toIndentedString(hostname)).append("\n");
    sb.append("    date: ").append(toIndentedString(date)).append("\n");
    sb.append("    reason: ").append(toIndentedString(reason)).append("\n");
    sb.append("    content: ").append(toIndentedString(content)).append("\n");
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
