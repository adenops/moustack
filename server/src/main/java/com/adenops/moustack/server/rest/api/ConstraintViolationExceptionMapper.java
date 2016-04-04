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

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.adenops.moustack.lib.model.rest.ApiResponse;

/*
 * Wrapper to return our own error format on bean validation errors
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
	@Context
	private HttpHeaders headers;

	@Override
	public Response toResponse(final ConstraintViolationException exception) {
		final MediaType type = headers.getMediaType();
		final Locale locale = headers.getLanguage();

		String[] messages = new String[exception.getConstraintViolations().size()];

		int i = 0;
		for (@SuppressWarnings("rawtypes")
		ConstraintViolation cv : exception.getConstraintViolations()) {
			messages[i] = cv.getMessage();
		}

		return Response.status(Response.Status.NOT_ACCEPTABLE).type(type).language(locale)
				.entity(new ApiResponse(String.join(", ", messages))).build();
	}
}
