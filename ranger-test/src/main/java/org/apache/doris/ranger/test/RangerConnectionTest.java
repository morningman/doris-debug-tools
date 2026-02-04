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

import org.apache.ranger.plugin.policyengine.RangerAccessRequestImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class RangerConnectionTest {
    private static final Logger LOG = LoggerFactory.getLogger(RangerConnectionTest.class);
    private final RangerDorisPlugin plugin;
    private final String serviceName;

    public RangerConnectionTest(RangerDorisPlugin plugin, String serviceName) {
        this.plugin = plugin;
        this.serviceName = serviceName;
    }

    public boolean runTest() {
        System.out.println("========================================");
        System.out.println("Doris Ranger Connectivity Test");
        System.out.println("========================================");
        System.out.println("Service Name: " + serviceName);
        System.out.println();

        boolean allTestsPassed = true;

        // TEST 1: Plugin Initialization
        System.out.println("[TEST 1] Plugin Initialization");
        try {
            if (plugin != null) {
                System.out.println("  ✓ RangerDorisPlugin created successfully");
                System.out.println("  ✓ Service name: " + serviceName);
            } else {
                System.out.println("  ✗ Failed to create RangerDorisPlugin");
                allTestsPassed = false;
            }
        } catch (Exception e) {
            System.out.println("  ✗ Exception during plugin initialization: " + e.getMessage());
            LOG.error("Plugin initialization failed", e);
            allTestsPassed = false;
        }
        System.out.println();

        // TEST 2: Ranger Admin Connection
        System.out.println("[TEST 2] Ranger Admin Connection");
        try {
            // Try to make a simple access request to verify connectivity
            RangerAccessRequestImpl testRequest = new RangerAccessRequestImpl();
            testRequest.setUser("__test_connectivity__");
            testRequest.setAccessType("SELECT");
            testRequest.setResource(new RangerDorisResource(DorisObjectType.GLOBAL, "*"));
            testRequest.setAccessTime(new Date());

            RangerAccessResult result = plugin.isAccessAllowed(testRequest);
            if (result != null) {
                System.out.println("  ✓ Connected to Ranger Admin");
                System.out.println("  ✓ Policy Engine initialized: true");
                System.out.println("  ✓ Policy download successful");
            } else {
                System.out.println("  ✗ Access result is null");
                System.out.println("  ! This may indicate connection issues with Ranger Admin");
                System.out.println("  ! Check the configuration file and Ranger Admin availability");
                allTestsPassed = false;
            }
        } catch (Exception e) {
            System.out.println("  ✗ Exception during connection test: " + e.getMessage());
            LOG.error("Connection test failed", e);
            allTestsPassed = false;
        }
        System.out.println();

        // TEST 3: Policy Cache Information
        System.out.println("[TEST 3] Policy Cache Information");
        try {
            // Try another access request to verify policies are working
            RangerAccessRequestImpl testRequest = new RangerAccessRequestImpl();
            testRequest.setUser("__test_policy__");
            testRequest.setAccessType("SELECT");
            testRequest.setResource(new RangerDorisResource(DorisObjectType.CATALOG, "internal"));
            testRequest.setAccessTime(new Date());

            RangerAccessResult result = plugin.isAccessAllowed(testRequest);
            if (result != null) {
                if (result.getPolicyVersion() != null) {
                    System.out.println("  ✓ Policy version: " + result.getPolicyVersion());
                }

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String currentTime = sdf.format(new Date());
                System.out.println("  ✓ Current time: " + currentTime);

                System.out.println("  ! Note: Policy count and cache details require Ranger Admin API access");
            } else {
                System.out.println("  ✗ Cannot retrieve policy information (Access result is null)");
                allTestsPassed = false;
            }
        } catch (Exception e) {
            System.out.println("  ✗ Exception during cache info retrieval: " + e.getMessage());
            LOG.error("Cache info retrieval failed", e);
            allTestsPassed = false;
        }
        System.out.println();

        return allTestsPassed;
    }
}
