// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.ranger.test;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class DorisRangerTester {
    private static final Logger LOG = LoggerFactory.getLogger(DorisRangerTester.class);

    private String serviceName = "doris";
    private String testUser = "test_user";
    private String configFile = null;

    public static void main(String[] args) {
        DorisRangerTester tester = new DorisRangerTester();
        if (!tester.parseArgs(args)) {
            System.exit(1);
        }

        try {
            tester.run();
        } catch (Exception e) {
            LOG.error("Test execution failed", e);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private boolean parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--service-name":
                    if (i + 1 < args.length) {
                        serviceName = args[++i];
                    } else {
                        System.err.println("Error: --service-name requires a value");
                        printUsage();
                        return false;
                    }
                    break;
                case "--test-user":
                    if (i + 1 < args.length) {
                        testUser = args[++i];
                    } else {
                        System.err.println("Error: --test-user requires a value");
                        printUsage();
                        return false;
                    }
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return false;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    return false;
            }
        }

        // Check if config file is provided via system property
        configFile = System.getProperty("ranger.config.file");
        if (configFile == null || configFile.isEmpty()) {
            System.err.println("Error: Configuration file not specified");
            System.err.println("Please use: -Dranger.config.file=<path-to-config>");
            System.err.println();
            printUsage();
            return false;
        }

        // Verify config file exists
        File cfgFile = new File(configFile);
        if (!cfgFile.exists()) {
            System.err.println("Error: Configuration file not found: " + configFile);
            return false;
        }

        return true;
    }

    private void printUsage() {
        System.out.println("Usage: java -Dranger.config.file=<config-file> -jar doris-ranger-test.jar [OPTIONS]");
        System.out.println();
        System.out.println("Required System Properties:");
        System.out.println("  -Dranger.config.file=<path>   Path to ranger-doris-security.xml");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --service-name <name>         Ranger service name (default: doris)");
        System.out.println("  --test-user <user>            Test user name (default: test_user)");
        System.out.println("  --help, -h                    Show this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -Dranger.config.file=config/ranger-doris-security.xml \\");
        System.out.println("       -jar doris-ranger-test.jar --service-name doris --test-user admin");
    }

    private void run() throws Exception {
        System.out.println("========================================");
        System.out.println("Doris Ranger Test Program");
        System.out.println("========================================");
        System.out.println("Configuration File: " + configFile);
        System.out.println("Service Name: " + serviceName);
        System.out.println("Test User: " + testUser);
        System.out.println();

        // Initialize Hadoop Configuration with the config file
        Configuration conf = new Configuration();

        // Load core-site.xml if exists (for Kerberos configuration)
        File coreSiteFile = new File("config/core-site.xml");
        if (coreSiteFile.exists()) {
            System.out.println("Loading core-site.xml for Hadoop/Kerberos configuration...");
            conf.addResource(coreSiteFile.toURI().toURL());
        }

        // Load ranger configuration
        conf.addResource(new File(configFile).toURI().toURL());

        // Configure Kerberos if enabled
        String authType = conf.get("hadoop.security.authentication");
        // if ("kerberos".equalsIgnoreCase(authType)) {
        //     System.out.println("Kerberos authentication is enabled");

        //     // Set system properties for JAAS and Kerberos
        //     System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        //     System.setProperty("java.security.krb5.conf", "/etc/krb5.conf");

        //     // Enable Kerberos debugging (optional, can be commented out for production)
        //     // System.setProperty("sun.security.krb5.debug", "true");
        //     // System.setProperty("sun.security.spnego.debug", "true");

        //     // Check if keytab file exists
        //     File keytabFile = new File("rangeradmin.keytab");
        //     if (keytabFile.exists()) {
        //         String principal = "rangeradmin/master-1-1.c-a212282673679a24.cn-beijing.emr.aliyuncs.com@EMR.C-A212282673679A24.COM";
        //         System.out.println("Using Kerberos principal: " + principal);
        //         System.out.println("Using keytab: " + keytabFile.getAbsolutePath());

        //         // Configure UserGroupInformation for Kerberos
        //         UserGroupInformation.setConfiguration(conf);
        //         UserGroupInformation.loginUserFromKeytab(principal, keytabFile.getAbsolutePath());

        //         System.out.println("✓ Kerberos login successful");
        //         System.out.println("Current user: " + UserGroupInformation.getCurrentUser());
        //     } else {
        //         System.out.println("Warning: Keytab file not found at: " + keytabFile.getAbsolutePath());
        //     }
        //     System.out.println();
        // }

        // Log configuration details
        String rangerUrl = conf.get("ranger.plugin.doris.policy.rest.url", "not configured");
        System.out.println("Ranger Admin URL: " + rangerUrl);
        System.out.println();

        // Set Ranger properties as system properties so RangerBasePlugin can find them
        for (java.util.Map.Entry<String, String> entry : conf) {
            String key = entry.getKey();
            if (key.startsWith("ranger.") || key.startsWith("xasecure.")) {
                System.setProperty(key, entry.getValue());
            }
        }

        // Initialize Ranger Plugin
        System.out.println("Initializing Ranger Plugin...");
        RangerDorisPlugin plugin = null;
        try {
            plugin = new RangerDorisPlugin(serviceName);
            plugin.initialize();
            System.out.println("✓ Ranger Plugin initialized");
            System.out.println();
        } catch (Exception e) {
            System.err.println("✗ Failed to initialize Ranger Plugin");
            System.err.println("Error: " + e.getMessage());
            LOG.error("Failed to initialize Ranger Plugin", e);
            throw e;
        }

        // Run Connectivity Test
        RangerConnectionTest connectionTest = new RangerConnectionTest(plugin, serviceName);
        boolean connectionSuccess = connectionTest.runTest();

        if (!connectionSuccess) {
            System.err.println("Connectivity test failed. Please check:");
            System.err.println("  1. Ranger Admin service is running and accessible");
            System.err.println("  2. Configuration file has correct settings");
            System.err.println("  3. Network connectivity to Ranger Admin");
            System.err.println();
        }

        // Run Policy Test
        RangerPolicyTest policyTest = new RangerPolicyTest(plugin, testUser);
        policyTest.runTest();

        System.out.println("========================================");
        System.out.println("Test Completed");
        System.out.println("========================================");
    }
}
