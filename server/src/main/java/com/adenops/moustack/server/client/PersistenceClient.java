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
		log.info("using database backend: {}", ServerConfig.getInstance().getDbType());
		try {
			// load the default properties
			Properties persistenceProperties = new Properties();
			persistenceProperties.load(PersistenceClient.class.getResourceAsStream(
					String.format("/database-%s.properties", ServerConfig.getInstance().getDbType())));
			String jdbUrl = String.format(persistenceProperties.getProperty("hibernate.connection.url"),
					ServerConfig.getInstance().getDbHost(), ServerConfig.getInstance().getDbName());
			persistenceProperties.put("hibernate.connection.url", jdbUrl);
			persistenceProperties.put("hibernate.connection.username", ServerConfig.getInstance().getDbUser());
			persistenceProperties.put("hibernate.connection.password", ServerConfig.getInstance().getDbPassword());
			entityManagerFactory = Persistence.createEntityManagerFactory("com.adenops.moustack",
					persistenceProperties);
		} catch (Throwable e) {
			// TODO: exceptions seem to be hidden here (because of the static instantiation?
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

	// TODO: this should be done in a separate scheduled thread.
	public synchronized void purgeReports(String hostname) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		// check if we need to cleanup.
		long count = entityManager
				.createQuery("SELECT count(*) FROM AgentReport AS r WHERE r.hostname = :hostname", Long.class)
				.setParameter("hostname", hostname).getSingleResult();
		// if we don't have to delete old reports, stop here.
		// an offset is used to avoid cleaning reports every times.
		if (count <= MAX_AGENT_REPORTS + 5)
			return;

		// remove old reports.
		entityManager.getTransaction().begin();
		// XXX 1: native query because of the limit in the subquery
		// XXX 2: sub-sub query because of MySQL limitations
		// (http://dev.mysql.com/doc/refman/5.0/en/subquery-restrictions.html)
		int deletedCount = entityManager
				.createNativeQuery(
						"DELETE FROM report WHERE id NOT IN (SELECT * FROM (SELECT id FROM report WHERE hostname = :hostname ORDER BY date DESC LIMIT :max) AS t)")
				.setParameter("hostname", hostname).setParameter("max", MAX_AGENT_REPORTS).executeUpdate();
		entityManager.getTransaction().commit();
		entityManager.close();
		log.debug("deleted " + deletedCount + " old reports");
	}

	public AgentReport getLastReport(String hostname) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();

		List<AgentReport> reports = entityManager
				.createQuery("FROM AgentReport AS r WHERE r.hostname = :hostname ORDER BY r.date DESC",
						AgentReport.class)
				.setParameter("hostname", hostname).setMaxResults(1).getResultList();

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
				.createQuery("SELECT new AgentReport(r.id,r.hostname,r.date,r.reason) FROM AgentReport r",
						AgentReport.class)
				.setMaxResults(MAX_RESULTS).getResultList();

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
		List<AgentStatus> results = entityManager.createQuery("from AgentStatus", AgentStatus.class)
				.setMaxResults(MAX_RESULTS).getResultList();
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
