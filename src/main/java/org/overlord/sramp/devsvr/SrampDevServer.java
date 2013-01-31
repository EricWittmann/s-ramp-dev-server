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

import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.test.EmbeddedContainer;
import org.overlord.sramp.atom.providers.HttpResponseProvider;
import org.overlord.sramp.atom.providers.SrampAtomExceptionProvider;
import org.overlord.sramp.repository.jcr.JCRRepository;
import org.overlord.sramp.server.atom.services.ArtifactResource;
import org.overlord.sramp.server.atom.services.BatchResource;
import org.overlord.sramp.server.atom.services.FeedResource;
import org.overlord.sramp.server.atom.services.QueryResource;
import org.overlord.sramp.server.atom.services.ServiceDocumentResource;

/**
 *
 * @author eric.wittmann@redhat.com
 */
public class SrampDevServer {

    protected static ResteasyDeployment deployment;
    protected static Dispatcher dispatcher;

    /**
     * Main entry point.
     * @param args
     */
    public static void main(String [] args) throws Exception {
        System.setProperty("sramp.modeshape.config.url", "classpath://" + JCRRepository.class.getName()
                + "/META-INF/modeshape-configs/inmemory-sramp-config.json");
        System.out.println("**** Starting up the S-RAMP Repository...");
        String portVar = System.getProperty("org.jboss.resteasy.port");
        if (portVar == null) {
            System.setProperty("org.jboss.resteasy.port", "8080");
        }
        deployment = EmbeddedContainer.start("/s-ramp-server/");
        dispatcher = deployment.getDispatcher();
        deployment.getProviderFactory().registerProvider(SrampAtomExceptionProvider.class);
        deployment.getProviderFactory().registerProvider(HttpResponseProvider.class);
        dispatcher.getRegistry().addPerRequestResource(ServiceDocumentResource.class);
        dispatcher.getRegistry().addPerRequestResource(ArtifactResource.class);
        dispatcher.getRegistry().addPerRequestResource(FeedResource.class);
        dispatcher.getRegistry().addPerRequestResource(BatchResource.class);
        dispatcher.getRegistry().addPerRequestResource(QueryResource.class);
        System.out.println("*** S-RAMP repository started");
    }

}
