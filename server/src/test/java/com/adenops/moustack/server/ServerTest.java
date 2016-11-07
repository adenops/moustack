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

package com.adenops.moustack.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Date;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.adenops.moustack.server.rest.model.AgentStatus;
import com.adenops.moustack.server.rest.model.AgentStatus.Status;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class ServerTest {
	private static final Logger log = LoggerFactory.getLogger(ServerTest.class);
	private static final int PORT = 8989;
	private static final String USER = "moustack";
	private static final String PASSWORD = "password";
	private static ObjectMapper objectMapper;
	private static Server jetty;

	@BeforeClass
	public static void setup() throws Exception {
		ServerConfig serverConfig = ServerConfig.getInstance();

		serverConfig.setUser(USER);
		serverConfig.setPassword(PASSWORD);
		serverConfig.setGitRepoUri("file:///home/jb/dev/moustack/profiles");
		serverConfig.setPort(PORT);
		serverConfig.setDevMode(true);
		serverConfig.setDbUser("sa");
		serverConfig.setDbPassword("");

		objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		MoustackServer server = new MoustackServer();
		jetty = server.start(serverConfig);

		waitForServer();
	}

	@AfterClass
	public static void teardown() throws Exception {
		jetty.stop();
		jetty.destroy();
	}

	@Test
	public void getStatuses() throws Exception {
		WebResponse response = get("/status");
		assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_OK);
	}

	@Test
	public void postStatus() throws Exception {
		AgentStatus status = new AgentStatus();
		status.setHostname("junit");
		status.setDate(new Date());
		status.setStatus(Status.SHUTDOWN);
		WebResponse response = post("/status", status);
		assertThat(response.getResponseCode()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
	}

	protected static void waitForServer() throws Exception {
		int toWait = 10000;

		while (toWait > 0) {
			// use health check endpoint to ensure server is ready
			WebResponse response = get("/");

			if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
				return;

			log.info("waiting for the server");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				Thread.currentThread().interrupt();
			}
			toWait -= 1000;
		}
		throw new RuntimeException("server took too long to start");
	}

	protected static WebResponse post(String path, Object data) throws IOException, SAXException {
		WebConversation conversation = new WebConversation();
		conversation.setExceptionsThrownOnErrorStatus(false);
		ByteArrayInputStream inputStream = new ByteArrayInputStream(objectMapper.writeValueAsBytes(data));
		WebRequest request = new PostMethodWebRequest("http://localhost:" + PORT + "/rest" + path, inputStream,
				"application/json");
		request.setHeaderField("Authorization",
				"Basic " + Base64.getEncoder().encodeToString(new String(USER + ":" + PASSWORD).getBytes()));
		// request.setHeaderField("Content-Type", "application/json");
		return conversation.getResponse(request);
	}

	protected static WebResponse get(String path) throws IOException, SAXException {
		WebConversation conversation = new WebConversation();
		conversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest request = new GetMethodWebRequest(new URL("http://localhost:" + PORT), "/rest" + path);
		request.setHeaderField("Authorization",
				"Basic " + Base64.getEncoder().encodeToString(new String(USER + ":" + PASSWORD).getBytes()));
		return conversation.getResponse(request);
	}
}
