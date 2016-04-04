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

package com.adenops.moustack.agent.util;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.adenops.moustack.agent.DeploymentException;

public class HttpUtil {

	public static SSLContext disableSSLVerification() {

		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}

		} };

		SSLContext context = null;
		try {
			context = SSLContext.getInstance("SSL");
			context.init(null, trustAllCerts, new java.security.SecureRandom());
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

		return context;
	}

	public static TrustManager[] certs = new TrustManager[] { new X509TrustManager() {
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		}
	} };

	public static class TrustAllHostNameVerifier implements HostnameVerifier {

		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}

	}

	public static boolean isSuccess(Response response) {
		return Status.Family.familyOf(response.getStatus()).equals(Status.Family.SUCCESSFUL);
	}

	public static boolean isNotFound(Response response) {
		return Status.Family.familyOf(response.getStatus()).equals(Status.Family.CLIENT_ERROR);
	}

	public static Response get(WebTarget target) throws DeploymentException {
		Response response;
		try {
			response = target.request(MediaType.APPLICATION_JSON_TYPE).get();
		} catch (ProcessingException e) {
			throw new DeploymentException("communication error with the server", e);
		}
		if (!HttpUtil.isSuccess(response)) {
			throw new DeploymentException("GET request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + ")");
		}
		return response;
	}

	public static Response post(WebTarget target, Object object) throws DeploymentException {
		Response response;
		try {
			response = target.request(MediaType.APPLICATION_JSON_TYPE).post(
					Entity.entity(object, MediaType.APPLICATION_JSON_TYPE));
		} catch (ProcessingException e) {
			throw new DeploymentException("communication error with the server", e);
		}
		if (!HttpUtil.isSuccess(response)) {
			throw new DeploymentException("POST request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + ")");
		}
		return response;
	}

	public static Response longPoll(WebTarget target) throws DeploymentException {
		Response response;
		try {
			response = target.request(MediaType.APPLICATION_JSON_TYPE).async().get().get();
		} catch (ProcessingException | ExecutionException e) {
			throw new DeploymentException("communication error with the server", e);
		} catch (InterruptedException e) {
			throw new DeploymentException("received interruption during long polling", e);
		}

		// for long polling, we consider timeout not an error
		if (!HttpUtil.isSuccess(response) && !response.getStatusInfo().equals(Response.Status.REQUEST_TIMEOUT)) {
			throw new DeploymentException("GET request " + target.getUri() + " returned HTTP code "
					+ response.getStatus() + " (" + response.getStatusInfo() + ")");
		}

		return response;
	}
}
