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

import java.io.InputStream;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.errai.bus.server.servlet.DefaultBlockingServlet;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;
import org.oasis_open.docs.s_ramp.ns.s_ramp_v1.BaseArtifactType;
import org.overlord.commons.dev.server.DevServerEnvironment;
import org.overlord.commons.dev.server.ErraiDevServer;
import org.overlord.commons.dev.server.MultiDefaultServlet;
import org.overlord.commons.dev.server.discovery.ErraiWebAppModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.JarModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.JarModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.gwt.server.filters.GWTCacheControlFilter;
import org.overlord.commons.gwt.server.filters.ResourceCacheControlFilter;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.sramp.client.SrampAtomApiClient;
import org.overlord.sramp.common.ArtifactType;
import org.overlord.sramp.common.SrampModelUtils;
import org.overlord.sramp.repository.jcr.JCRRepository;
import org.overlord.sramp.server.atom.services.SRAMPApplication;
import org.overlord.sramp.ui.client.shared.beans.ArtifactSummaryBean;
import org.overlord.sramp.ui.server.servlets.ArtifactDownloadServlet;
import org.overlord.sramp.ui.server.servlets.ArtifactUploadServlet;

/**
 * A dev server for s-ramp.
 * @author eric.wittmann@redhat.com
 */
public class SrampDevServer extends ErraiDevServer {

    /**
     * Main entry point.
     * @param args
     */
    public static void main(String [] args) throws Exception {
        System.setProperty("discovery-strategy.debug", "true");
        SrampDevServer devServer = new SrampDevServer(args);
        devServer.go();
    }

    /**
     * Constructor.
     * @param args
     */
    public SrampDevServer(String [] args) {
        super(args);
    }

    /**
     * @see org.overlord.commons.dev.server.ErraiDevServer#getErraiModuleId()
     */
    @Override
    protected String getErraiModuleId() {
        return "s-ramp-ui";
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#preConfig()
     */
    @Override
    protected void preConfig() {
        // Use an in-memory config for s-ramp
        System.setProperty("sramp.modeshape.config.url", "classpath://" + JCRRepository.class.getName()
                + "/META-INF/modeshape-configs/inmemory-sramp-config.json");
        // No authentication provider - the s-ramp server is not protected
        System.setProperty("s-ramp-ui.atom-api.authentication.provider", "org.overlord.sramp.ui.server.api.NoAuthenticationProvider");
        // Don't do any resource caching!
        System.setProperty("overlord.resource-caching.disabled", "true");
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#createDevEnvironment()
     */
    @Override
    protected DevServerEnvironment createDevEnvironment() {
        return new SrampDevServerEnvironment(args);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#addModules(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void addModules(DevServerEnvironment environment) {
        environment.addModule("s-ramp-ui",
                new WebAppModuleFromIDEDiscoveryStrategy(ArtifactSummaryBean.class),
                new ErraiWebAppModuleFromMavenDiscoveryStrategy(ArtifactSummaryBean.class));
        environment.addModule("overlord-commons-uiheader",
                new JarModuleFromIDEDiscoveryStrategy(OverlordHeaderDataJS.class, "src/main/resources/META-INF/resources"),
                new JarModuleFromMavenDiscoveryStrategy(OverlordHeaderDataJS.class, "/META-INF/resources"));
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#addModulesToJetty(org.overlord.commons.dev.server.DevServerEnvironment, org.eclipse.jetty.server.handler.ContextHandlerCollection)
     */
    @Override
    protected void addModulesToJetty(DevServerEnvironment environment, ContextHandlerCollection handlers) throws Exception {
        super.addModulesToJetty(environment, handlers);

        /* *********
         * S-RAMP UI
         * ********* */
        ServletContextHandler srampUI = new ServletContextHandler(ServletContextHandler.SESSIONS);
        srampUI.setContextPath("/s-ramp-ui");
        srampUI.setWelcomeFiles(new String[] { "index.html" });
        srampUI.setResourceBase(environment.getModuleDir("s-ramp-ui").getCanonicalPath());
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
        resources.setInitParameter("resourceBases", environment.getModuleDir("s-ramp-ui").getCanonicalPath()
                + "|" + environment.getModuleDir("overlord-commons-uiheader").getCanonicalPath());
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


        // Add to handlers
        handlers.addHandler(srampUI);
        handlers.addHandler(srampServer);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#postStart(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void postStart(DevServerEnvironment environment) throws Exception {
        System.out.println("----------  Seeding the Repository  ---------------");

        SrampAtomApiClient client = new SrampAtomApiClient("http://localhost:8080/s-ramp-server");
        InputStream is = null;

        // Ontology #1
        try {
            is = SrampDevServer.class.getResourceAsStream("colors.owl.xml");
            client.uploadOntology(is);
            System.out.println("Ontology 1 added");
        } finally {
            IOUtils.closeQuietly(is);
        }

        // Ontology #2
        try {
            is = SrampDevServer.class.getResourceAsStream("regional.owl.xml");
            client.uploadOntology(is);
            System.out.println("Ontology 2 added");
        } finally {
            IOUtils.closeQuietly(is);
        }

        // PDF Document
        try {
            is = SrampDevServer.class.getResourceAsStream("sample.pdf");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.Document(), is, "sample.pdf");
            artifact.setDescription("This is just a sample PDF file that is included in the dev server so that we have some content when we start up.");
            artifact.setVersion("1.0");
            client.updateArtifactMetaData(artifact);
            System.out.println("PDF added");
        } finally {
            IOUtils.closeQuietly(is);
        }

        // XML Document
        try {
            is = SrampDevServer.class.getResourceAsStream("order.xml");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.XmlDocument(), is, "order.xml");
            artifact.getClassifiedBy().add("http://www.example.org/colors.owl#Blue");
            SrampModelUtils.setCustomProperty(artifact, "foo", "bar");
            SrampModelUtils.setCustomProperty(artifact, "angle", "obtuse");
            client.updateArtifactMetaData(artifact);
            System.out.println("XML file added");
        } finally {
            IOUtils.closeQuietly(is);
        }

        // WSDL Document
        try {
            is = SrampDevServer.class.getResourceAsStream("deriver.wsdl");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.WsdlDocument(), is, "deriver.wsdl");
            artifact.getClassifiedBy().add("http://www.example.org/colors.owl#Red");
            artifact.getClassifiedBy().add("http://www.example.org/regional.owl#Asia");
            client.updateArtifactMetaData(artifact);
            System.out.println("WSDL added");
        } finally {
            IOUtils.closeQuietly(is);
        }

        System.out.println("----------  DONE  ---------------");
        System.out.println("Now try:  \n  http://localhost:8080/s-ramp-ui/index.html");
        System.out.println("---------------------------------");
    }

}
