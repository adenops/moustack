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

package com.adenops.moustack.server.rest.api;

import java.util.Locale;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.lib.model.rest.ApiResponse;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class GeneralExceptionMapper implements ExceptionMapper<Throwable> {
	private static final Logger log = LoggerFactory.getLogger(GeneralExceptionMapper.class);

	@Context
	private HttpHeaders headers;

	@Override
	public Response toResponse(Throwable exception) {
		final MediaType type = headers.getMediaType();
		final Locale locale = headers.getLanguage();
		Status status = Response.Status.INTERNAL_SERVER_ERROR;

		if (exception.getMessage() != null)
			log.error(exception.getMessage());

		// extract status code for WebApplicationException to respect
		// Jersey built-in behaviour
		if (exception instanceof WebApplicationException) {
			Response jerseyResponse = ((WebApplicationException) exception).getResponse();
			status = Status.fromStatusCode(jerseyResponse.getStatus());
		}

		return Response.status(status).type(type).language(locale).entity(new ApiResponse(exception.getMessage()))
				.build();
	}
}
