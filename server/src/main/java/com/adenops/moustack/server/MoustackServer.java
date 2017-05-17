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

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Collections;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adenops.moustack.lib.argsparser.ArgumentsParser;
import com.adenops.moustack.lib.model.ApplicationInfo;
import com.adenops.moustack.lib.util.LockUtil;
import com.adenops.moustack.lib.util.MiscUtil;
import com.adenops.moustack.server.client.PersistenceClient;

import io.swagger.jaxrs.config.BeanConfig;

public class MoustackServer {
	public static final Logger log = LoggerFactory.getLogger(MoustackServer.class);
	public static ApplicationInfo applicationInfo;

	public static final String GIT_CONTEXT = "/git";
	private static final String REST_CONTEXT = "/rest";
	private static final String SWAGGER_CONTEXT = "/swagger";
	public static final String REPOSITORY_NAME = "configuration";
	public static final String REPOSITORY_BRANCH = "master";

	// for locking (prevent multiple parallel runs)
	private static final File lockFile = new File("/var/tmp/moustack-server.lock");
	private static FileLock lock;

	private static File createTempFolder() throws IOException {
		// this sucks, but it seem there are no better ways in java to create a temporary folder...
		File tempDir = File.createTempFile("moustack-profiles", ".tmp");
		tempDir.delete();
		tempDir.mkdir();

		// register a shutdown hook to cleanup the temporary folder on exit
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					FileUtils.deleteDirectory(tempDir);
				} catch (IOException e) {
					log.error("error", e);
					log.error("could not delete {}", tempDir);
				}
			}
		});

		return tempDir;
	}

	private static ServletHolder setupGitServlet(ServerConfig config) throws Exception {
		// remove leading file://
		String path = config.getGitRepoUri().substring(7);

		// prepare init command
		InitCommand init = Git.init().setDirectory(new File(path)).setBare(false);

		// if dev mode, store git metadata in a temporary folder
		if (config.getDevMode()) {
			log.debug("initializing git environment in dev mode");
			init.setGitDir(createTempFolder());
		}

		// initialize repo
		Git git = init.call();
		Repository repository = git.getRepository();

		// serve git repository
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver(new RepositoryResolver<HttpServletRequest>() {
			@Override
			public Repository open(HttpServletRequest req, String name)
					throws RepositoryNotFoundException, ServiceNotEnabledException {

				if (!name.equals(REPOSITORY_NAME))
					throw new RepositoryNotFoundException(name);

				if (config.getDevMode()) {
					// if we are in dev mode, add all files and commit
					try {
						git.add().addFilepattern(".").call();

						// check if we actually need to commit
						if (!git.status().call().isClean()) {
							RevCommit commit = git.commit().setMessage("moustack dev mode").call();
							log.debug("dev mode: created new commit {}", commit.name());
						}
					} catch (Exception e) {
						log.debug("dev mode: error while adding/committing changes", e);
					}
				}

				return repository;
			}
		});

		// prevent push on the repository
		gs.addReceivePackFilter(new Filter() {
			@Override
			public void init(FilterConfig filterConfig) throws ServletException {
			}

			@Override
			public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				GitSmartHttpTools.sendError((HttpServletRequest) request, (HttpServletResponse) response,
						HttpServletResponse.SC_FORBIDDEN, "upload-pack not permitted on this server");
			}

			@Override
			public void destroy() {
			}
		});
		return new ServletHolder(gs);

	}

	private static ServletHolder setupRESTServlet() {
		ServletHolder jerseyServlet = new ServletHolder(new ServletContainer());

		jerseyServlet.setInitOrder(0);
		jerseyServlet.setAsyncSupported(true);
		jerseyServlet.setInitParameter(ServerProperties.PROVIDER_PACKAGES,
				"com.adenops.moustack.server;com.api.resources;io.swagger.jaxrs.json;io.swagger.jaxrs.listing");
		jerseyServlet.setInitParameter(ServerProperties.PROVIDER_SCANNING_RECURSIVE, "true");
		jerseyServlet.setInitParameter(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, "true");

		return jerseyServlet;
	}

	private static ServletContextHandler setupSwaggerContextHandler() {
		// prepare context for swagger ui (static files)
		String swaggerUIResourceBasePath = MoustackServer.class.getResource("/webapp/swagger-ui").toExternalForm();
		ServletContextHandler swaggerContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
		swaggerContext.setContextPath(SWAGGER_CONTEXT);
		swaggerContext.setWelcomeFiles(new String[] { "index.html" });
		swaggerContext.setResourceBase(swaggerUIResourceBasePath);

		// add default servlet for swagger ui context
		ServletHolder holderHome = new ServletHolder(new DefaultServlet());
		holderHome.setInitParameter("resourceBase", swaggerUIResourceBasePath);
		swaggerContext.addServlet(holderHome, "/");

		// setup swagger configuration
		final BeanConfig swaggerConfig = new BeanConfig();
		swaggerConfig.setResourcePackage("com.adenops.moustack.server.rest");
		swaggerConfig.setVersion(applicationInfo.getVersion());
		swaggerConfig.setBasePath(REST_CONTEXT);
		swaggerConfig.setTitle(applicationInfo.getDisplayName());
		swaggerConfig.setDescription(applicationInfo.getDisplayName());
		swaggerConfig.setContact("contact@adenops.com");
		swaggerConfig.setScan(true);

		return swaggerContext;
	}

	private static ConstraintSecurityHandler setupSecurityHandler(String username, String password) {
		MappedLoginService users = new MappedLoginService() {
			@Override
			protected UserIdentity loadUser(String who) {
				return null;
			}

			@Override
			protected void loadUsers() throws IOException {
				putUser(username, new Password(password), new String[] { "user" });
			}

			@Override
			public String getName() {
				return "Authentication needed";
			}

			@Override
			protected String[] loadRoleInfo(KnownUser user) {
				return null;
			}

			@Override
			protected KnownUser loadUserInfo(String username) {
				return null;
			}
		};

		ConstraintSecurityHandler security = new ConstraintSecurityHandler();

		Constraint constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[] { "user" });

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setPathSpec("/*");
		mapping.setConstraint(constraint);

		security.setConstraintMappings(Collections.singletonList(mapping));
		security.setAuthenticator(new BasicAuthenticator());
		security.setLoginService(users);

		return security;
	}

	public Server start(ServerConfig config) throws Exception {
		long startTime = System.currentTimeMillis();
		Server server = new Server(config.getPort());

		// prepare the context handler for the servlets
		ServletContextHandler servletsContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
		servletsContext.setContextPath("/");

		// add static resources for the UI in the main context
		String staticresourceBasePath = MoustackServer.class.getResource("/webapp/moustack").toExternalForm();
		servletsContext.setWelcomeFiles(new String[] { "index.html" });
		servletsContext.setResourceBase(staticresourceBasePath);

		// Jersey REST API
		servletsContext.addServlet(setupRESTServlet(), REST_CONTEXT + "/*");

		// Git Servlet
		if (config.getGitRepoUri().startsWith("file:///"))
			servletsContext.addServlet(setupGitServlet(config), GIT_CONTEXT + "/*");

		// add default servlet to be spec compliant
		servletsContext.addServlet(new ServletHolder(new DefaultServlet()), "/");

		// Swagger context
		ServletContextHandler swaggerContext = setupSwaggerContextHandler();

		// prepare the contexts collection
		ContextHandlerCollection contexts = new ContextHandlerCollection();
		contexts.setHandlers(new Handler[] { servletsContext, swaggerContext });

		// finally, wrap into a security context if necessary
		if (!StringUtils.isBlank(config.getUser()) && !StringUtils.isBlank(config.getPassword())) {
			ConstraintSecurityHandler security = setupSecurityHandler(config.getUser(), config.getPassword());
			security.setHandler(contexts);
			server.setHandler(security);
		} else
			server.setHandler(contexts);

		server.start();
		log.info("server listening on port {}", config.getPort());
		log.info("server started in {} ms", System.currentTimeMillis() - startTime);

		return server;
	}

	// TODO: handle exceptions
	public static void main(String[] args) throws Exception {
		applicationInfo = MiscUtil.loadApplicationInfo("moustack-server");
		System.out.println(String.format("%s %s (build %s)", applicationInfo.getDisplayName(),
				applicationInfo.getVersion(), applicationInfo.getBuild()));

		ServerConfig config = (ServerConfig) new ArgumentsParser(applicationInfo.getDisplayName(),
				applicationInfo.getApplicationName(), applicationInfo.getVersion(), applicationInfo.getDescription(),
				applicationInfo.getUrl(), ServerConfig.class).parse(args);

		if (config == null)
			return;

		// setup logging
		MiscUtil.configureLogging(config.getLogLevel());

		try {
			lock = LockUtil.acquireLock(lockFile);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		if (lock == null) {
			log.error("another instance is already running");
			return;
		}

		log.info("git repo: {}", config.getGitRepoUri());
		if (!StringUtils.isBlank(config.getDockerRegistry()))
			log.info("docker registry override: {}", config.getDockerRegistry());
		if (!StringUtils.isBlank(config.getDockerMoustackTag()))
			log.info("docker moustack tag override: {}", config.getDockerMoustackTag());

		// add a shutdown hook to ensure the lock is released
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				LockUtil.releaseLock(lock, lockFile);
			}
		});

		// check if database connection is OK
		PersistenceClient.getInstance().check();

		Server server = null;
		try {
			server = new MoustackServer().start(config);
			server.join();
		} finally {
			if (server != null)
				server.destroy();
		}
	}
}
