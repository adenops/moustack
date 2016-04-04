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

package com.adenops.moustack.server.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.server.ServerConfig;
import com.adenops.moustack.server.rest.model.AgentInfo;
import com.adenops.moustack.server.rest.model.AgentReport;
import com.adenops.moustack.server.rest.model.AgentStatus;

public class PersistenceClient {
	private static final Logger log = LoggerFactory.getLogger(PersistenceClient.class);
	private final EntityManagerFactory entityManagerFactory;

	// TODO: this is an arbitrary value to prevent too much records to be fetched
	private final int MAX_RESULTS = 1000;

	private final int MAX_AGENT_REPORTS = 10;

	private static final PersistenceClient instance = new PersistenceClient();

	private PersistenceClient() {
		try {
			Properties persistenceProperties = new Properties();
			StringBuilder jdbUrl = new StringBuilder("jdbc:mysql://");
			jdbUrl.append(ServerConfig.getInstance().getDbHost());
			jdbUrl.append("/");
			jdbUrl.append(ServerConfig.getInstance().getDbName());
			jdbUrl.append("?createDatabaseIfNotExist=true&amp;useTimezone=true&amp;serverTimezone=UTC&amp;autoReconnect=true");
			persistenceProperties.put("javax.persistence.jdbc.url", jdbUrl.toString());
			persistenceProperties.put("javax.persistence.jdbc.user", ServerConfig.getInstance().getDbUser());
			persistenceProperties.put("javax.persistence.jdbc.password", ServerConfig.getInstance().getDbPassword());
			entityManagerFactory = Persistence.createEntityManagerFactory("com.adenops.moustack",
					persistenceProperties);
		} catch (Throwable e) {
			throw new RuntimeException("error while connection to the database", e);
		}
	}

	public static PersistenceClient getInstance() {
		return instance;
	}

	public void check() {
		// TODO: do a select 1
	}

	public void update(Object object) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();
		entityManager.merge(object);
		entityManager.getTransaction().commit();
		entityManager.close();
	}

	public void create(Object object) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();
		entityManager.persist(object);
		entityManager.getTransaction().commit();
		entityManager.close();
	}

	// TODO: this is not efficient, but should be enough for some time
	// XXX 1: native query because of the limit in the subquery
	// XXX 2: sub-sub query because of MySQL limitations
	// (http://dev.mysql.com/doc/refman/5.0/en/subquery-restrictions.html)
	public void purgeReports(String hostname) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();
		Query query = entityManager
				.createNativeQuery("delete from report where id not in (select * from (select id from report where hostname = :hostname order by date desc limit :max) as t)");
		int deletedCount = query.setParameter("hostname", hostname).setParameter("max", MAX_AGENT_REPORTS)
				.executeUpdate();
		if (deletedCount > 0)
			log.debug("deleted " + deletedCount + " old reports");
		entityManager.getTransaction().commit();
		entityManager.close();
	}

	public AgentReport getLastReport(String hostname) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		List<AgentReport> reports = entityManager
				.createQuery("from AgentReport as r where r.hostname = :hostname order by r.date desc",
						AgentReport.class).setParameter("hostname", hostname).setMaxResults(1).getResultList();

		entityManager.close();

		if (reports.isEmpty())
			return null;

		return reports.get(0);
	}

	public AgentReport getAgentReport(long id) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		AgentReport result = entityManager.find(AgentReport.class, id);
		entityManager.close();
		return result;
	}

	public List<AgentReport> getAgentsReports() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		// custom request to prevent content from being included
		// TODO: content should be on a different table
		List<AgentReport> reports = entityManager
				.createQuery("select new AgentReport(r.id,r.hostname,r.date,r.reason) from AgentReport r",
						AgentReport.class).setMaxResults(MAX_RESULTS).getResultList();

		entityManager.close();

		return reports;
	}

	public AgentStatus getAgentStatus(String hostname) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		AgentStatus result = entityManager.find(AgentStatus.class, hostname);
		entityManager.close();
		return result;
	}

	public List<AgentStatus> getAgentsStatuses() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		List<AgentStatus> results = entityManager.createQuery("from AgentStatus").setMaxResults(MAX_RESULTS)
				.getResultList();
		entityManager.close();
		return results;
	}

	// assemble information from different objects
	// current method is inefficient, but will stay that way until the API are more defined
	public List<AgentInfo> getAgentsInfo() {
		List<AgentInfo> infos = new ArrayList<AgentInfo>();

		// use status to retrieve the list of agents
		List<AgentStatus> statuses = getAgentsStatuses();

		// now we get the last report for each agent
		for (AgentStatus status : statuses) {
			AgentReport report = getLastReport(status.getHostname());

			AgentInfo info = new AgentInfo();
			info.setHostname(status.getHostname());
			info.setLastStatus(status.getStatus());
			info.setLastStatusDate(status.getDate());

			if (report != null) {
				info.setLastReportId(report.getId());
				info.setLastReportDate(report.getDate());
				info.setLastReportResult(report.getReason());
			}

			infos.add(info);
		}

		return infos;
	}
}
