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

import java.util.Date;

public class RangerPolicyTest {
    private static final Logger LOG = LoggerFactory.getLogger(RangerPolicyTest.class);
    private final RangerDorisPlugin plugin;
    private final String testUser;

    public RangerPolicyTest(RangerDorisPlugin plugin, String testUser) {
        this.plugin = plugin;
        this.testUser = testUser;
    }

    public boolean runTest() {
        System.out.println("========================================");
        System.out.println("Doris Ranger Policy Test");
        System.out.println("========================================");
        System.out.println("Test User: " + testUser);
        System.out.println("Test Resources: various (GLOBAL, CATALOG, DATABASE, TABLE, COLUMN)");
        System.out.println();

        int totalTests = 0;
        int passedTests = 0;

        // TEST 1: Global Privilege Check
        System.out.println("[TEST 1] Global Privilege Check");
        totalTests++;
        if (testAccessRequest(
                new RangerDorisResource(DorisObjectType.GLOBAL, "*"),
                DorisAccessType.ADMIN.name(),
                "GLOBAL (*)"
        )) {
            passedTests++;
        }
        System.out.println();

        // TEST 2: Catalog Privilege Check
        System.out.println("[TEST 2] Catalog Privilege Check");
        totalTests++;
        if (testAccessRequest(
                new RangerDorisResource(DorisObjectType.CATALOG, "internal"),
                DorisAccessType.SELECT.name(),
                "CATALOG (internal)"
        )) {
            passedTests++;
        }
        System.out.println();

        // TEST 3: Database Privilege Check
        System.out.println("[TEST 3] Database Privilege Check");
        totalTests++;
        if (testAccessRequest(
                new RangerDorisResource(DorisObjectType.DATABASE, "internal", "db1"),
                DorisAccessType.SELECT.name(),
                "DATABASE (internal.db1)"
        )) {
            passedTests++;
        }
        System.out.println();

        // TEST 4: Table Privilege Check
        System.out.println("[TEST 4] Table Privilege Check");
        totalTests++;
        if (testAccessRequest(
                new RangerDorisResource(DorisObjectType.TABLE, "internal", "db1", "tbl2"),
                DorisAccessType.SELECT.name(),
                "TABLE (internal.db1.tbl2)"
        )) {
            passedTests++;
        }
        System.out.println();

        // TEST 5: Column Privilege Check
        System.out.println("[TEST 5] Column Privilege Check");
        totalTests++;
        if (testAccessRequest(
                new RangerDorisResource(DorisObjectType.COLUMN, "internal", "db1", "tbl2", "col1"),
                DorisAccessType.SELECT.name(),
                "COLUMN (internal.db1.tbl2.col1)"
        )) {
            passedTests++;
        }
        System.out.println();

        // Summary
        System.out.println("========================================");
        System.out.println("Summary");
        System.out.println("========================================");
        System.out.println("Total Tests: " + totalTests);
        System.out.println("Passed: " + passedTests);
        System.out.println("Failed: " + (totalTests - passedTests));
        System.out.println();
        System.out.println("Note: To verify complete functionality, you may need to");
        System.out.println("create corresponding policies in Ranger first.");
        System.out.println();

        return passedTests > 0; // At least one test should execute successfully
    }

    private boolean testAccessRequest(RangerDorisResource resource, String accessType, String resourceDesc) {
        try {
            RangerAccessRequestImpl request = new RangerAccessRequestImpl();
            request.setUser(testUser);
            request.setClusterType("doris");
            request.setClientType("doris");
            request.setAccessTime(new Date());

            request.setAction(accessType);
            request.setAccessType(accessType);
            request.setResource(resource);

            RangerAccessResult result = plugin.isAccessAllowed(request);

            System.out.println("  Resource: " + resourceDesc);
            System.out.println("  Access Type: " + accessType);
            System.out.println("  Result: " + (result.getIsAllowed() ? "ALLOWED" : "DENIED"));
            System.out.println("  Policy ID: " + result.getPolicyId());

            if (result.getPolicyVersion() != null && result.getPolicyVersion() > 0) {
                System.out.println("  Policy Version: " + result.getPolicyVersion());
            }

            if (result.getPolicyPriority() != 0) {
                System.out.println("  Policy Priority: " + result.getPolicyPriority());
            }

            if (result.getReason() != null && !result.getReason().isEmpty()) {
                System.out.println("  Reason: " + result.getReason());
            }

            return true; // Test executed successfully
        } catch (Exception e) {
            System.out.println("  ✗ Exception during access request: " + e.getMessage());
            LOG.error("Access request failed for resource: " + resourceDesc, e);
            return false;
        }
    }
}
