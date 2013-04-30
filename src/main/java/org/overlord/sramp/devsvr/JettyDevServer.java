/*
 * Copyright 2013 JBoss Inc
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
package org.overlord.sramp.devsvr;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.errai.bus.server.servlet.DefaultBlockingServlet;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.sramp.repository.jcr.JCRRepository;
import org.overlord.sramp.server.atom.services.SRAMPApplication;
import org.overlord.sramp.ui.server.filters.GWTCacheControlFilter;
import org.overlord.sramp.ui.server.filters.ResourceCacheControlFilter;
import org.overlord.sramp.ui.server.servlets.ArtifactDownloadServlet;
import org.overlord.sramp.ui.server.servlets.ArtifactUploadServlet;

/**
 *
 * @author eric.wittmann@redhat.com
 */
public class JettyDevServer {

    /**
     * Main entry point.
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        // Use an in-memory config for s-ramp
        System.setProperty("sramp.modeshape.config.url", "classpath://" + JCRRepository.class.getName()
                + "/META-INF/modeshape-configs/inmemory-sramp-config.json");
        // No authentication provider - the s-ramp server is not protected
        System.setProperty("s-ramp-ui.atom-api.authentication.provider", "org.overlord.sramp.ui.server.api.NoAuthenticationProvider");
        // Don't do any resource caching!
        System.setProperty("s-ramp-ui.resource-caching.disabled", "true");
        System.out.println("**** Starting up the S-RAMP Repository in jetty 9...");

        DevServerEnvironment environment = DevServerEnvironment.discover(args);
        if (environment.isIde_srampUI() && !environment.isUsingClassHiderAgent()) {
            System.out.println("******************************************************************");
            System.out.println("WARNING: we detected that you are running from within an IDE");
            System.out.println("         but are not using the Errai class hiding agent.  As");
            System.out.println("         a result, you may see a number of Weld related errors ");
            System.out.println("         during startup.  This is due to client-only classes");
            System.out.println("         being included on the server classpath.  To address");
            System.out.println("         this issue, please see:");
            System.out.println("         ");
            System.out.println("         https://github.com/jfuerth/client-local-class-hider");
            System.out.println("         ");
            System.out.println("         The above is a Java Agent that will hide the client-");
            System.out.println("         only classes from Weld, thereby suppressing the errors.");
            System.out.println("******************************************************************");
            Thread.sleep(5000);
        }
        environment.createAppConfigs();

        /* *********
         * S-RAMP UI
         * ********* */
        ServletContextHandler srampUI = new ServletContextHandler(ServletContextHandler.SESSIONS);
        srampUI.setContextPath("/s-ramp-ui");
        srampUI.setResourceBase(environment.getSrampUIWebAppDir().getCanonicalPath());
        srampUI.setInitParameter("errai.properties", "/WEB-INF/errai.properties");
        srampUI.setInitParameter("login.config", "/WEB-INF/login.config");
        srampUI.setInitParameter("users.properties", "/WEB-INF/users.properties");
        srampUI.addEventListener(new Listener());
        srampUI.addEventListener(new BeanManagerResourceBindingListener());
        srampUI.addFilter(GWTCacheControlFilter.class, "/app/*", EnumSet.of(DispatcherType.REQUEST));
        srampUI.addFilter(ResourceCacheControlFilter.class, "/css/*", EnumSet.of(DispatcherType.REQUEST));
        srampUI.addFilter(ResourceCacheControlFilter.class, "/images/*", EnumSet.of(DispatcherType.REQUEST));
        srampUI.addFilter(ResourceCacheControlFilter.class, "/js/*", EnumSet.of(DispatcherType.REQUEST));

        // Servlets
        ServletHolder erraiServlet = new ServletHolder(DefaultBlockingServlet.class);
        erraiServlet.setInitOrder(1);
        srampUI.addServlet(erraiServlet, "*.erraiBus");
        srampUI.addServlet(new ServletHolder(ArtifactDownloadServlet.class), "/app/services/artifactDownload");
        srampUI.addServlet(new ServletHolder(ArtifactUploadServlet.class), "/app/services/artifactUpload");
        ServletHolder headerDataServlet = new ServletHolder(OverlordHeaderDataJS.class);
        headerDataServlet.setInitParameter("app-id", "s-ramp-ui");
        srampUI.addServlet(headerDataServlet, "/js/overlord-header-data.js");
        // File resources
        ServletHolder resources = new ServletHolder(new MultiDefaultServlet());
        resources.setInitParameter("resourceBase", "/");
        resources.setInitParameter("resourceBases", environment.getSrampUIWebAppDir().getCanonicalPath()
                + "|" + environment.getOverlordHeaderDir().getCanonicalPath());
        resources.setInitParameter("dirAllowed", "true");
        resources.setInitParameter("pathInfoOnly", "false");
        String[] fileTypes = new String[] { "html", "js", "css", "png", "gif" };
        for (String fileType : fileTypes) {
            srampUI.addServlet(resources, "*." + fileType);
        }

        /* *************
         * S-RAMP server
         * ************* */
        ServletContextHandler srampServer = new ServletContextHandler(ServletContextHandler.SESSIONS);
        srampServer.setContextPath("/s-ramp-server");
        ServletHolder resteasyServlet = new ServletHolder(new HttpServletDispatcher());
        resteasyServlet.setInitParameter("javax.ws.rs.Application", SRAMPApplication.class.getName());
        srampServer.addServlet(resteasyServlet, "/*");

        ContextHandlerCollection handlers = new ContextHandlerCollection();
        handlers.addHandler(srampUI);
        handlers.addHandler(srampServer);


        // Create the server.
        Server server = new Server(8080);
        server.setHandler(handlers);
        server.start();
        long endTime = System.currentTimeMillis();
        System.out.println("******* Started up in " + (endTime - startTime) + "ms");
        server.join();
    }

}
