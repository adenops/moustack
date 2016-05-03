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

import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.agent.DeploymentException;
import com.adenops.moustack.agent.config.StackConfig;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoClient {
	private static final Logger log = LoggerFactory.getLogger(MongoClient.class);
	private com.mongodb.MongoClient client;

	public MongoClient(StackConfig stack) throws DeploymentException {
		log.debug("initializing MongoDB client");
		client = new com.mongodb.MongoClient("127.0.0.1", 27017);
	}

	public boolean createDatabaseUser(String database, String user, String password) {
		boolean changed = false;
		MongoDatabase db = client.getDatabase("admin");
		BasicDBObject query = new BasicDBObject();
		query.put("user", user);
		// query.put("password", stack.get(
		// Variable.DB_CEILOMETER_PASSWORD));
		// XXX: properly check is user exists
		MongoCollection<Document> collection = db.getCollection("system.users");
		if (!collection.find(query).iterator().hasNext()) {
			log.info("creating user " + user);
			db = client.getDatabase(database);
			Map<String, Object> commandArguments = new BasicDBObject();
			commandArguments.put("createUser", user);
			commandArguments.put("pwd", password);
			String[] roles = { "readWrite", "dbAdmin" };
			commandArguments.put("roles", roles);
			BasicDBObject command = new BasicDBObject(commandArguments);
			db.runCommand(command);
			changed = true;
		}
		return changed;
	}
}
