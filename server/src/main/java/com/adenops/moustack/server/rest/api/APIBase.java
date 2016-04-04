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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.lib.model.rest.ApiResponse;

public abstract class APIBase {
	private static final Logger log = LoggerFactory.getLogger(APIBase.class);

	@Context
	protected UriInfo uriInfo;

	protected Response error(Response.Status status, String message) {
		return Response.status(status).entity(new ApiResponse(message)).build();
	}

	protected Response error(String message) {
		return error(Response.Status.SERVICE_UNAVAILABLE, message);
	}

	protected Response success() {
		return success(new ApiResponse("success"));
	}

	protected Response success(Object object) {
		return Response.status(Response.Status.OK).entity(object).build();
	}

	protected void validateNotNull(Object object) {
		if (object == null)
			throw new WebApplicationException(Status.NOT_FOUND);
	}
}
