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

package com.adenops.moustack.agent.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.adenops.moustack.agent.config.StackProperty;

public class MySQLClient extends ManagedClient {
	private static final Logger log = LoggerFactory.getLogger(MySQLClient.class);
	private static final int CONNECTION_MAX_RETRY = 10;
	private static final int CONNECTION_RETRY_SLEEP = 4;
	private Connection connection;

	protected MySQLClient(StackConfig stack) throws DeploymentException {
		log.debug("initializing MySQL client");
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			log.error("MySQL driver cannot be loaded");
			throw new DeploymentException("cannot load MySQL driver");
		}

		for (int i = 0; i < CONNECTION_MAX_RETRY; i++) {
			try {
				Connection connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1/mysql?"
						+ "user=root&password=" + stack.get(StackProperty.MYSQL_ROOT_PASSWORD));
				connection.setAutoCommit(false);
				this.connection = connection;
				return;
			} catch (SQLException e) {
				log.warn("MySQL connection failed, waiting " + CONNECTION_RETRY_SLEEP + "s");
				try {
					Thread.sleep(CONNECTION_RETRY_SLEEP * 1000);
				} catch (InterruptedException e1) {
				}
			}
		}
		log.error("could not connection to MySQL database");
		throw new DeploymentException("db connection error");
	}

	@Override
	protected void release() {
		if (connection == null)
			return;

		log.debug("closing MySQL client");

		try {
			connection.close();
		} catch (SQLException e) {
			log.error("error when closing MySQL connection");
		}
	}

	private void close(ResultSet resultSet) {
		if (resultSet == null)
			return;
		try {
			resultSet.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void close(Statement statement) {
		if (statement == null)
			return;
		try {
			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void close(PreparedStatement statement) {
		if (statement == null)
			return;
		try {
			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("resource")
	private boolean checkDBExist(Connection connection, String name) throws DeploymentException {
		ResultSet resultSet = null;
		try {
			resultSet = connection.getMetaData().getCatalogs();
			while (resultSet.next()) {
				if (name.equals(resultSet.getString(1)))
					return true;
			}
		} catch (SQLException e) {
			throw new DeploymentException("cannot check db existence: " + name, e);
		} finally {
			close(resultSet);
		}
		return false;
	}

	private void createDB(Connection connection, String name) throws DeploymentException {
		log.info("creating database " + name);
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + name
					+ " DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci");
		} catch (SQLException e) {
			throw new DeploymentException("cannot create db: " + name);
		} finally {
			close(stmt);
		}
	}

	private boolean checkUserExist(Connection connection, String host, String name, String password)
			throws DeploymentException {
		PreparedStatement stmt = null;
		ResultSet resultSet = null;
		try {
			stmt = connection.prepareStatement("SELECT 1 FROM user where Host=? and User=? and password=PASSWORD(?)");
			stmt.setString(1, host);
			stmt.setString(2, name);
			stmt.setString(3, password);
			if (log.isTraceEnabled())
				log.trace(stmt.toString());
			resultSet = stmt.executeQuery();
			return resultSet.next();
		} catch (SQLException e) {
			throw new DeploymentException("cannot check user existence: " + name, e);
		} finally {
			close(resultSet);
			close(stmt);
		}
	}

	private void createUser(Connection connection, String host, String database, String name, String password)
			throws DeploymentException {
		log.info("creating user " + name + " for database " + database + " (" + host + ")");
		PreparedStatement stmt = null;
		try {
			stmt = connection.prepareStatement("GRANT ALL PRIVILEGES ON " + database + ".* TO ?@? IDENTIFIED BY ?");
			stmt.setString(1, name);
			stmt.setString(2, host);
			stmt.setString(3, password);
			if (log.isTraceEnabled())
				log.trace(stmt.toString());
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw new DeploymentException("cannot create user: " + name, e);
		} finally {
			close(stmt);
		}
	}

	public boolean createDatabaseUser(String database, String user, String password) throws DeploymentException {
		try {
			boolean changed = false;

			if (!checkDBExist(connection, database)) {
				createDB(connection, database);
				changed = true;
			}

			if (!checkUserExist(connection, "localhost", user, password)) {
				createUser(connection, "localhost", database, user, password);
				createUser(connection, "127.0.0.1", database, user, password);
				changed = true;
			}

			connection.commit();

			return changed;
		} catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			}
			throw new DeploymentException("cannot configure database: " + database, e);
		}
	}
}
