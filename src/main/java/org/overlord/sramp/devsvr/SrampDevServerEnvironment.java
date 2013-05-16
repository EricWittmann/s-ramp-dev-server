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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.dtgov.ui.server.DtgovUI;
import org.overlord.sramp.atom.archive.ArchiveUtils;
import org.overlord.sramp.ui.client.shared.beans.ArtifactSummaryBean;

/**
 * Holds information about the S-RAMP development runtime environment.
 * @author eric.wittmann@redhat.com
 */
public class SrampDevServerEnvironment {

    /**
     * Determine the current runtime environment.
     * @param args
     */
    public static SrampDevServerEnvironment discover(String[] args) {
        final SrampDevServerEnvironment environment = new SrampDevServerEnvironment(args);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                environment.onVmExit();
            }
        }));
        return environment;
    }

    private boolean ide_srampUI = false;
    private boolean ide_dtgovUI = false;
    private boolean ide_overlordHeader = false;
    private boolean usingClassHiderAgent = false;

    private File targetDir;
    private File srampUIWebAppDir;
    private File dtgovUIWebAppDir;
    private File overlordHeaderDir;

    private File srampUIWorkDir = null;
    private File dtgovUIWorkDir = null;
    private File overlordCommonsWorkDir = null;

    /**
     * Constructor.
     * @param args
     */
    private SrampDevServerEnvironment(String[] args) {
        findTargetDir();
        System.out.println("----------------------------------");
        findSrampUIWebAppDir();
        System.out.println("----------------------------------\n");
        findDtgovUIWebAppDir();
        System.out.println("----------------------------------\n");
        findOverlordCommonsDir();
        System.out.println("----------------------------------\n");
        inspectArgs(args);
        detectAgent();
    }


    /**
     * Do any cleanup on exit.
     */
    protected void onVmExit() {
        cleanSrampUIWorkDir();
        cleanDtgovUIWorkDir();
        cleanOverlordCommonsWorkDir();
    }

    /**
     * @return the ide_srampUI
     */
    public boolean isIde_srampUI() {
        return ide_srampUI;
    }

    /**
     * @return the ide_dtgovUI
     */
    public boolean isIde_dtgovUI() {
        return ide_dtgovUI;
    }

    /**
     * @return the ide_overlordHeader
     */
    public boolean isIde_overlordHeader() {
        return ide_overlordHeader;
    }

    /**
     * @return the targetDir
     */
    public File getTargetDir() {
        return targetDir;
    }

    /**
     * @param targetDir the targetDir to set
     */
    public void setTargetDir(File targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * @return the srampUIWebAppDir
     */
    public File getSrampUIWebAppDir() {
        return srampUIWebAppDir;
    }

    /**
     * @return the dtgovUIWebAppDir
     */
    public File getDtgovUIWebAppDir() {
        return dtgovUIWebAppDir;
    }

    /**
     * @param srampUIWebAppDir the srampUIWebAppDir to set
     */
    public void setSrampUIWebAppDir(File srampUIWebAppDir) {
        this.srampUIWebAppDir = srampUIWebAppDir;
    }

    /**
     * @return the overlordHeaderDir
     */
    public File getOverlordHeaderDir() {
        return overlordHeaderDir;
    }

    /**
     * @param overlordHeaderDir the overlordHeaderDir to set
     */
    public void setOverlordHeaderDir(File overlordHeaderDir) {
        this.overlordHeaderDir = overlordHeaderDir;
    }

    /**
     * @return the maven target dir
     */
    private void findTargetDir() {
        String path = SrampJettyDevServer.class.getClassLoader()
                .getResource(SrampJettyDevServer.class.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find target directory.");
        }
        if (path.contains("/target/")) {
            path = path.substring(0, path.indexOf("/target/")) + "/target";
            targetDir = new File(path);
            System.out.println("Detected runtime 'target' directory: " + targetDir);
        } else {
            throw new RuntimeException("Failed to find target directory.");
        }
    }

    /**
     * Attempts to find the webapp directory for S-RAMP UI.  When running in Eclipse
     * this should return a file path to the src/main/webapp folder of s-ramp-ui-war.
     * When not running in Eclipse, this should unpack the WAR to a temporary location
     * and return a path to it.
     */
    private void findSrampUIWebAppDir() {
        String path = ArtifactSummaryBean.class.getClassLoader()
                .getResource(ArtifactSummaryBean.class.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find S-RAMP UI war.");
        }
        File file = new File(path);
        // The class file is available on the file system.
        if (file.exists()) {
            System.out.println("Detected S-RAMP UI classes on the filesystem.");
            System.out.println("\tAssumption: s-ramp-ui-war is imported into your IDE");
            this.ide_srampUI = true;
            if (path.contains("/WEB-INF/classes/")) {
                String pathToWebApp = path.substring(0, path.indexOf("/WEB-INF/classes/"));
                this.srampUIWebAppDir = new File(pathToWebApp);
                System.out.println("Detected S-RAMP UI web app: " + srampUIWebAppDir);
                return;
            } else {
                throw new RuntimeException("Failed to find s-ramp-ui-war/src/main/webapp.");
            }
        } else {
            System.out.println("Detected S-RAMP UI classes in JAR.");
            System.out.println("\tAssumption: not running from IDE or s-ramp-ui-war not imported");
            this.ide_srampUI = false;
            if (path.contains("-classes.jar") && path.startsWith("file:")) {
                String pathToWar = path.substring(5, path.indexOf("-classes.jar")) + ".war";
                File war = new File(pathToWar);
                if (war.isFile()) {
                    System.out.println("Discovered S-RAMP UI War: " + war);
                    this.srampUIWorkDir = new File(this.targetDir, "s-ramp-ui-war");
                    cleanSrampUIWorkDir();
                    this.srampUIWorkDir.mkdirs();
                    try {
                        System.out.println("Unpacking S-RAMP UI war to: " + srampUIWorkDir);
                        ArchiveUtils.unpackToWorkDir(war, srampUIWorkDir);
                        FileUtils.deleteDirectory(new File(srampUIWorkDir, "WEB-INF/lib"));
                        FileUtils.deleteDirectory(new File(srampUIWorkDir, "WEB-INF/classes/org/overlord/sramp/ui/client/local"));
                        this.srampUIWebAppDir = srampUIWorkDir;
                        System.out.println("Detected S-RAMP UI web app: " + srampUIWebAppDir);
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to find S-RAMP UI webapp directory.");
    }

    /**
     * Attempts to find the webapp directory for the DTGov UI.  When running in Eclipse
     * this should return a file path to the src/main/webapp folder of dtgov-ui-war.
     * When not running in Eclipse, this should unpack the WAR to a temporary location
     * and return a path to it.
     */
    private void findDtgovUIWebAppDir() {
        String path = DtgovUI.class.getClassLoader()
                .getResource(DtgovUI.class.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find DTGov UI war.");
        }
        File file = new File(path);
        // The class file is available on the file system.
        if (file.exists()) {
            System.out.println("Detected DTGov UI classes on the filesystem.");
            System.out.println("\tAssumption: dtgov-ui-war is imported into your IDE");
            this.ide_dtgovUI = true;
            if (path.contains("/WEB-INF/classes/")) {
                String pathToWebApp = path.substring(0, path.indexOf("/WEB-INF/classes/"));
                this.dtgovUIWebAppDir = new File(pathToWebApp);
                System.out.println("Detected DTGov UI web app: " + getDtgovUIWebAppDir());
                return;
            } else {
                throw new RuntimeException("Failed to find dtgov-ui-war/src/main/webapp.");
            }
        } else {
            System.out.println("Detected DTGov UI classes in JAR.");
            System.out.println("\tAssumption: not running from IDE or dtgov-ui-war not imported");
            this.ide_dtgovUI = false;
            if (path.contains("-classes.jar") && path.startsWith("file:")) {
                String pathToWar = path.substring(5, path.indexOf("-classes.jar")) + ".war";
                File war = new File(pathToWar);
                if (war.isFile()) {
                    System.out.println("Discovered DTGov UI War: " + war);
                    this.dtgovUIWorkDir = new File(this.targetDir, "dtgov-ui-war");
                    cleanDtgovUIWorkDir();
                    this.dtgovUIWorkDir.mkdirs();
                    try {
                        System.out.println("Unpacking DTGov UI war to: " + dtgovUIWorkDir);
                        ArchiveUtils.unpackToWorkDir(war, dtgovUIWorkDir);
                        FileUtils.deleteDirectory(new File(dtgovUIWorkDir, "WEB-INF/lib"));
                        FileUtils.deleteDirectory(new File(dtgovUIWorkDir, "WEB-INF/classes/org/overlord/dtgov/ui/client/local"));
                        this.dtgovUIWebAppDir = dtgovUIWorkDir;
                        System.out.println("Detected S-RAMP UI web app: " + getDtgovUIWebAppDir());
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to find DTGov UI webapp directory.");
    }

    /**
     * Attempts to find the directory containing the Overlord Commons shared
     * header javascript and CSS files.  If running in an IDE, and if the
     * overlord-commons project is imported, then a file path directly to those
     * files should get returned.  If running from maven or if the overlord-commons
     * project is not imported into the IDE, then the overlord-commons JAR will
     * be unpacked to a temporary directory.  This temp directory will get used
     * instead.
     */
    private void findOverlordCommonsDir() {
        String path = OverlordHeaderDataJS.class.getClassLoader()
                .getResource(OverlordHeaderDataJS.class.getName().replace('.', '/') + ".class").getPath();
        if (path == null) {
            throw new RuntimeException("Failed to find Overlord Commons classes.");
        }
        File file = new File(path);
        // The class file is available on the file system.
        if (file.exists()) {
            System.out.println("Detected Overlord Commons Header UI classes on the filesystem.");
            System.out.println("\tAssumption: overlord-commons is imported into your IDE.");
            this.ide_overlordHeader = true;
            if (path.contains("/target/classes/")) {
                String pathToProj = path.substring(0, path.indexOf("/target/classes/"));
                this.overlordHeaderDir = new File(pathToProj, "src/main/resources/META-INF/resources");
                if (!this.overlordHeaderDir.isDirectory()) {
                    throw new RuntimeException("Missing directory: " + this.overlordHeaderDir);
                }
                System.out.println("Detected Overlord Header UI path: " + overlordHeaderDir);
                return;
            } else {
                throw new RuntimeException("Failed to find Overlord Header UI files.");
            }
        } else {
            System.out.println("Detected Overlord Commons Header UI classes in JAR.");
            System.out.println("\tAssumption: running from Maven or overlord-commons not imported in IDE.");
            this.ide_overlordHeader = false;
            if (path.contains(".jar") && path.startsWith("file:")) {
                String pathToJar = path.substring(5, path.indexOf(".jar")) + ".jar";
                File jar = new File(pathToJar);
                if (jar.isFile()) {
                    System.out.println("Discovered Overlord Commons UI jar: " + jar);
                    this.overlordCommonsWorkDir = new File(this.targetDir, "overlord-commons-uiheader");
                    cleanOverlordCommonsWorkDir();
                    this.overlordCommonsWorkDir.mkdirs();
                    try {
                        System.out.println("Unpacking Overlord Commons UI jar to: " + overlordCommonsWorkDir);
                        ArchiveUtils.unpackToWorkDir(jar, overlordCommonsWorkDir);
                        this.overlordHeaderDir = new File(overlordCommonsWorkDir, "META-INF/resources");
                        System.out.println("Detected Overlord Commons Header UI path: " + overlordHeaderDir);
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to find S-RAMP UI webapp directory.");
    }

    /**
     * Clean the overlord commons work dir.
     */
    private void cleanOverlordCommonsWorkDir() {
        if (overlordCommonsWorkDir != null) {
            try { FileUtils.deleteDirectory(overlordCommonsWorkDir); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * Clean the sramp ui work dir.
     */
    private void cleanSrampUIWorkDir() {
        if (srampUIWorkDir != null) {
            try { FileUtils.deleteDirectory(srampUIWorkDir); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * Clean the dtgov ui work dir.
     */
    private void cleanDtgovUIWorkDir() {
        if (dtgovUIWorkDir != null) {
            try { FileUtils.deleteDirectory(dtgovUIWorkDir); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    /**
     * @return the usingClassHiderAgent
     */
    public boolean isUsingClassHiderAgent() {
        return usingClassHiderAgent;
    }

    /**
     * @param usingClassHiderAgent the usingClassHiderAgent to set
     */
    public void setUsingClassHiderAgent(boolean usingClassHiderAgent) {
        this.usingClassHiderAgent = usingClassHiderAgent;
    }

    /**
     * Checks for interesting command line args.
     * @param args
     */
    private void inspectArgs(String[] args) {
    }

    /**
     * Checks for the existence of the java agent.
     */
    private void detectAgent() {
        try {
            Class.forName("org.jboss.errai.ClientLocalClassHidingAgent");
            this.usingClassHiderAgent = true;
        } catch (ClassNotFoundException e) {
            this.usingClassHiderAgent = false;
        }
    }

    /**
     * Creates the UI application configs and sets the system property telling the Overlord
     * Header servlet where to find them.
     * @throws Exception
     */
    public void createAppConfigs() throws Exception {
        File dir = new File(this.targetDir, "overlord-apps");
        if (dir.isDirectory()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();

        File configFile1 = new File(dir, "srampui-overlordapp.properties");
        Properties props = new Properties();
        props.setProperty("overlordapp.app-id", "s-ramp-ui");
        props.setProperty("overlordapp.href", "/s-ramp-ui/index.html" + (ide_srampUI ? "?gwt.codesvr=127.0.0.1:9997" : ""));
        props.setProperty("overlordapp.label", "S-RAMP");
        props.setProperty("overlordapp.primary-brand", "JBoss Overlord");
        props.setProperty("overlordapp.secondary-brand", "S-RAMP Repository");
        props.store(new FileWriter(configFile1), "S-RAMP UI application");

        File configFile2 = new File(dir, "dtgov-overlordapp.properties");
        props = new Properties();
        props.setProperty("overlordapp.app-id", "dtgov");
        props.setProperty("overlordapp.href", "/dtgov/index.html" + (ide_dtgovUI ? "?gwt.codesvr=127.0.0.1:9997" : ""));
        props.setProperty("overlordapp.label", "DTGov");
        props.setProperty("overlordapp.primary-brand", "JBoss Overlord");
        props.setProperty("overlordapp.secondary-brand", "Design Time Governance");
        props.store(new FileWriter(configFile2), "DTGov UI application");

        File configFile3 = new File(dir, "gadgets-overlordapp.properties");
        props = new Properties();
        props.setProperty("overlordapp.app-id", "gadgets");
        props.setProperty("overlordapp.href", "/gadgets/");
        props.setProperty("overlordapp.label", "Gadget Server");
        props.setProperty("overlordapp.primary-brand", "JBoss Overlord");
        props.setProperty("overlordapp.secondary-brand", "Gadget Server");
        props.store(new FileWriter(configFile3), "Gadget Server UI application");

        System.setProperty("org.overlord.apps.config-dir", dir.getCanonicalPath());
        System.out.println("Generated app configs in: " + dir.getCanonicalPath());
    }

}
