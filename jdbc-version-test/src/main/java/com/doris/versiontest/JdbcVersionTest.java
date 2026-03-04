package com.doris.versiontest;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * JDBC Version Compatibility Test for Apache Doris.
 *
 * Reproduces: https://github.com/apache/doris/issues/60634
 *
 * Tests whether mysql-connector-j 9.5.0+ returns empty results
 * when useServerPrepStmts=true (server-side prepared statements).
 *
 * Test dimensions:
 *   1. Driver version (controlled via Maven profile)
 *   2. JDBC URL parameters (useServerPrepStmts on/off)
 *   3. Query method (Statement vs PreparedStatement)
 */
public class JdbcVersionTest {

    private static final String PROPERTIES_FILE = "connection.properties";
    private static final String TEST_DB = "jdbc_version_test";
    private static final String TEST_TABLE = "t_user";

    // JDBC parameter combinations to test
    private static final Map<String, String> PARAM_COMBOS = new LinkedHashMap<>();
    static {
        PARAM_COMBOS.put("default",                "");
        PARAM_COMBOS.put("serverPrep=true",         "useServerPrepStmts=true");
        PARAM_COMBOS.put("serverPrep=false",        "useServerPrepStmts=false");
        PARAM_COMBOS.put("serverPrep=false+cache",  "useServerPrepStmts=false&cacheResultSetMetadata=true");
    }

    // Track test results
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static final List<FailedTestRecord> failedRecords = new ArrayList<>();

    /** Structured record for each failed test, enabling detailed reproduction output. */
    private static class FailedTestRecord {
        final String testName;
        final String jdbcUrl;
        final String jdbcParams;
        final String queryMethod;   // "Statement" or "PreparedStatement"
        final String sql;
        final String bindParams;    // e.g. "setLong(1, 1)" or "(none)"
        final String expected;
        final String actual;

        FailedTestRecord(String testName, String jdbcUrl, String jdbcParams,
                         String queryMethod, String sql, String bindParams,
                         String expected, String actual) {
            this.testName = testName;
            this.jdbcUrl = jdbcUrl;
            this.jdbcParams = jdbcParams;
            this.queryMethod = queryMethod;
            this.sql = sql;
            this.bindParams = bindParams;
            this.expected = expected;
            this.actual = actual;
        }
    }

    // Current test context (set before each test group)
    private static String currentJdbcUrl = "";
    private static String currentJdbcParams = "";

    public static void main(String[] args) {
        try {
            Properties props = loadProperties();
            String host = props.getProperty("doris.host", "127.0.0.1");
            int port = Integer.parseInt(props.getProperty("doris.port", "9030"));
            String database = props.getProperty("doris.database", TEST_DB);
            String user = props.getProperty("doris.user", "root");
            String password = props.getProperty("doris.password", "");

            // Detect driver version
            String driverVersion = detectDriverVersion();

            printBanner(driverVersion, host, port, database, user);

            // Setup: create database and table, insert test data
            String baseUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
            setupTestData(baseUrl, user, password);

            // Run tests for each JDBC parameter combination
            for (Map.Entry<String, String> entry : PARAM_COMBOS.entrySet()) {
                String comboName = entry.getKey();
                String params = entry.getValue();
                String url = params.isEmpty() ? baseUrl : baseUrl + "?" + params;

                // Set context for recordResult
                currentJdbcUrl = url;
                currentJdbcParams = params.isEmpty() ? "(none)" : params;

                System.out.println("\n" + "=".repeat(70));
                System.out.printf("  JDBC Params: [%s]%n", comboName);
                System.out.printf("  URL: %s%n", url);
                System.out.println("=".repeat(70));

                try (Connection conn = DriverManager.getConnection(url, user, password)) {
                    runTestSuite(conn, comboName);
                } catch (Exception e) {
                    System.err.printf("  [ERROR] Connection failed for [%s]: %s%n", comboName, e.getMessage());
                    recordFail(comboName + " / Connection", "DriverManager.getConnection()",
                        url, "(none)", "Successful connection", "Exception: " + e.getMessage());
                }
            }

            // Print summary + detailed reproduction steps
            printSummary(driverVersion);
            if (!failedRecords.isEmpty()) {
                printReproductionSteps(driverVersion, host, port, database, user);
            }

        } catch (Exception e) {
            System.err.println("[FATAL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(failedTests > 0 ? 1 : 0);
    }

    // ================================================================
    // Setup
    // ================================================================

    private static void setupTestData(String baseUrl, String user, String password) throws SQLException {
        System.out.println("\n>>> Setting up test data...");

        try (Connection conn = DriverManager.getConnection(baseUrl, user, password);
             Statement stmt = conn.createStatement()) {

            // Create test table (matches Ruffianjiang's DDL from issue comments)
            stmt.execute("CREATE TABLE IF NOT EXISTS `" + TEST_TABLE + "` (\n" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',\n" +
                "  `username` VARCHAR(64) NOT NULL COMMENT 'Username',\n" +
                "  `password` VARCHAR(128) NOT NULL COMMENT 'Password',\n" +
                "  `email` VARCHAR(128) COMMENT 'Email',\n" +
                "  `phone` VARCHAR(20) COMMENT 'Phone',\n" +
                "  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create Time',\n" +
                "  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Update Time'\n" +
                ") ENGINE=OLAP\n" +
                "UNIQUE KEY(`id`)\n" +
                "DISTRIBUTED BY HASH(`id`) BUCKETS 3\n" +
                "PROPERTIES (\"replication_num\" = \"1\");"
            );
            System.out.println("    Table '" + TEST_TABLE + "' created/verified.");

            // Check if data already exists
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE);
            rs.next();
            int existingCount = rs.getInt(1);
            rs.close();

            if (existingCount == 0) {
                // Insert test data (10 rows)
                stmt.execute("INSERT INTO `" + TEST_TABLE + "` " +
                    "(`id`, `username`, `password`, `email`, `phone`, `create_time`, `update_time`) VALUES\n" +
                    "(1, 'alice',   'pass_alice',   'alice@example.com',   '13800000001', '2026-01-01 10:00:00', '2026-01-01 10:00:00'),\n" +
                    "(2, 'bob',     'pass_bob',     'bob@example.com',     '13800000002', '2026-01-02 10:00:00', '2026-01-02 10:00:00'),\n" +
                    "(3, 'charlie', 'pass_charlie', 'charlie@example.com', '13800000003', '2026-01-03 10:00:00', '2026-01-03 10:00:00'),\n" +
                    "(4, 'david',   'pass_david',   'david@example.com',   '13800000004', '2026-01-04 10:00:00', '2026-01-04 10:00:00'),\n" +
                    "(5, 'eve',     'pass_eve',     'eve@example.com',     '13800000005', '2026-01-05 10:00:00', '2026-01-05 10:00:00'),\n" +
                    "(6, 'frank',   'pass_frank',   'frank@example.com',   '13800000006', '2026-01-06 10:00:00', '2026-01-06 10:00:00'),\n" +
                    "(7, 'grace',   'pass_grace',   'grace@example.com',   '13800000007', '2026-01-07 10:00:00', '2026-01-07 10:00:00'),\n" +
                    "(8, 'henry',   'pass_henry',   'henry@example.com',   '13800000008', '2026-01-08 10:00:00', '2026-01-08 10:00:00'),\n" +
                    "(9, 'ivy',     'pass_ivy',     'ivy@example.com',     '13800000009', '2026-01-09 10:00:00', '2026-01-09 10:00:00'),\n" +
                    "(10,'jack',    'pass_jack',    'jack@example.com',    '13800000010', '2026-01-10 10:00:00', '2026-01-10 10:00:00');"
                );
                System.out.println("    Inserted 10 test rows.");
            } else {
                System.out.println("    Test data already exists (" + existingCount + " rows). Skipping insert.");
            }
        }

        System.out.println(">>> Setup complete.\n");
    }

    // ================================================================
    // Test Suite
    // ================================================================

    private static void runTestSuite(Connection conn, String comboName) throws SQLException {
        testStatementSelectAll(conn, comboName);
        testStatementSelectWhere(conn, comboName);
        testPreparedStatementSelectAll(conn, comboName);
        testPreparedStatementSelectById(conn, comboName);
        testPreparedStatementSelectByName(conn, comboName);
        testAggregateQuery(conn, comboName);
        testShowDatabases(conn, comboName);
        testShowTables(conn, comboName);
        testResultSetMetadata(conn, comboName);
    }

    // ---- Test 1: Statement.executeQuery("SELECT * FROM t_user") ----
    private static void testStatementSelectAll(Connection conn, String combo) throws SQLException {
        String testName = combo + " / Statement SELECT *";
        String sql = "SELECT * FROM " + TEST_TABLE;
        System.out.printf("%n  [Test] %s%n", testName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = printAndCountResults(rs);
            boolean pass = count >= 10;
            if (pass) {
                recordPass(testName, "Got " + count + " rows");
            } else {
                recordFail(testName, "Statement", sql, "(none)",
                    ">=10 rows", count + " rows");
            }
        }
    }

    // ---- Test 2: Statement.executeQuery("SELECT * FROM t_user WHERE id = 1") ----
    private static void testStatementSelectWhere(Connection conn, String combo) throws SQLException {
        String testName = combo + " / Statement SELECT WHERE";
        String sql = "SELECT * FROM " + TEST_TABLE + " WHERE id = 1";
        System.out.printf("%n  [Test] %s%n", testName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = printAndCountResults(rs);
            boolean pass = count == 1;
            if (pass) {
                recordPass(testName, "Got 1 row");
            } else {
                recordFail(testName, "Statement", sql, "(none)",
                    "1 row", count + " rows");
            }
        }
    }

    // ---- Test 3: PreparedStatement SELECT * (core scenario) ----
    private static void testPreparedStatementSelectAll(Connection conn, String combo) throws SQLException {
        String testName = combo + " / PreparedStatement SELECT *";
        String sql = "SELECT * FROM " + TEST_TABLE;
        System.out.printf("%n  [Test] %s%n", testName);
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            int count = printAndCountResults(rs);
            boolean pass = count >= 10;
            if (pass) {
                recordPass(testName, "Got " + count + " rows");
            } else {
                recordFail(testName, "PreparedStatement", sql, "(none)",
                    ">=10 rows", count + " rows");
            }
        }
    }

    // ---- Test 4: PreparedStatement SELECT WHERE id = ? (core scenario) ----
    private static void testPreparedStatementSelectById(Connection conn, String combo) throws SQLException {
        String testName = combo + " / PreparedStatement SELECT by ID";
        String sql = "SELECT * FROM " + TEST_TABLE + " WHERE id = ?";
        System.out.printf("%n  [Test] %s%n", testName);
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, 1L);
            try (ResultSet rs = pstmt.executeQuery()) {
                int count = printAndCountResults(rs);
                boolean pass = count == 1;
                if (pass) {
                    recordPass(testName, "Got 1 row");
                } else {
                    recordFail(testName, "PreparedStatement", sql, "pstmt.setLong(1, 1L)",
                        "1 row", count + " rows");
                }
            }
        }
    }

    // ---- Test 5: PreparedStatement SELECT WHERE username = ? ----
    private static void testPreparedStatementSelectByName(Connection conn, String combo) throws SQLException {
        String testName = combo + " / PreparedStatement SELECT by name";
        String sql = "SELECT * FROM " + TEST_TABLE + " WHERE username = ?";
        System.out.printf("%n  [Test] %s%n", testName);
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "alice");
            try (ResultSet rs = pstmt.executeQuery()) {
                int count = printAndCountResults(rs);
                boolean pass = count == 1;
                if (pass) {
                    recordPass(testName, "Got 1 row");
                } else {
                    recordFail(testName, "PreparedStatement", sql, "pstmt.setString(1, \"alice\")",
                        "1 row", count + " rows");
                }
            }
        }
    }

    // ---- Test 6: Aggregate query ----
    private static void testAggregateQuery(Connection conn, String combo) throws SQLException {
        String testName = combo + " / Aggregate COUNT(*)";
        String sql = "SELECT COUNT(*) AS cnt FROM " + TEST_TABLE;
        System.out.printf("%n  [Test] %s%n", testName);
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            int count = 0;
            long total = 0;
            while (rs.next()) {
                total = rs.getLong("cnt");
                count++;
            }
            boolean pass = count == 1 && total >= 10;
            if (pass) {
                recordPass(testName, "COUNT(*)=" + total);
            } else {
                recordFail(testName, "PreparedStatement", sql, "(none)",
                    "1 row with cnt>=10", "rows=" + count + ", cnt=" + total);
            }
            System.out.printf("    COUNT(*) = %d%n", total);
        }
    }

    // ---- Test 7: SHOW DATABASES ----
    private static void testShowDatabases(Connection conn, String combo) throws SQLException {
        String testName = combo + " / SHOW DATABASES";
        String sql = "SHOW DATABASES";
        System.out.printf("%n  [Test] %s%n", testName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                count++;
            }
            boolean pass = count > 0;
            if (pass) {
                recordPass(testName, "Got " + count + " databases");
            } else {
                recordFail(testName, "Statement", sql, "(none)",
                    ">0 databases", count + " databases");
            }
            System.out.printf("    Found %d databases%n", count);
        }
    }

    // ---- Test 8: SHOW TABLES ----
    private static void testShowTables(Connection conn, String combo) throws SQLException {
        String testName = combo + " / SHOW TABLES";
        String sql = "SHOW TABLES";
        System.out.printf("%n  [Test] %s%n", testName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                count++;
            }
            boolean pass = count > 0;
            if (pass) {
                recordPass(testName, "Got " + count + " tables");
            } else {
                recordFail(testName, "Statement", sql, "(none)",
                    ">0 tables", count + " tables");
            }
            System.out.printf("    Found %d tables%n", count);
        }
    }

    // ---- Test 9: ResultSet Metadata ----
    private static void testResultSetMetadata(Connection conn, String combo) throws SQLException {
        String testName = combo + " / ResultSet Metadata";
        String sql = "SELECT * FROM " + TEST_TABLE + " LIMIT 1";
        System.out.printf("%n  [Test] %s%n", testName);
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            System.out.printf("    Column count: %d%n", colCount);
            for (int i = 1; i <= colCount; i++) {
                System.out.printf("    [%d] %s (%s)%n", i, meta.getColumnName(i), meta.getColumnTypeName(i));
            }

            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
            }

            boolean pass = colCount >= 7 && rowCount == 1;
            if (pass) {
                recordPass(testName, colCount + " columns, " + rowCount + " row");
            } else {
                recordFail(testName, "PreparedStatement", sql, "(none)",
                    ">=7 cols and 1 row", colCount + " cols and " + rowCount + " rows");
            }
        }
    }

    // ================================================================
    // Result Recording
    // ================================================================

    private static void recordPass(String testName, String detail) {
        totalTests++;
        passedTests++;
        System.out.printf("    >> PASS: %s%n", detail);
    }

    private static void recordFail(String testName, String queryMethod, String sql,
                                   String bindParams, String expected, String actual) {
        totalTests++;
        failedTests++;
        failedRecords.add(new FailedTestRecord(
            testName, currentJdbcUrl, currentJdbcParams,
            queryMethod, sql, bindParams, expected, actual
        ));
        System.out.printf("    >> FAIL: expected %s, got %s%n", expected, actual);
    }

    // ================================================================
    // Utilities
    // ================================================================

    private static int printAndCountResults(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        int rowCount = 0;

        // Print up to 5 rows for display
        while (rs.next()) {
            rowCount++;
            if (rowCount <= 5) {
                StringBuilder sb = new StringBuilder("    ");
                for (int i = 1; i <= Math.min(colCount, 4); i++) {
                    sb.append(String.format("%-20s", rs.getString(i)));
                }
                if (colCount > 4) sb.append("...");
                System.out.println(sb);
            }
        }
        if (rowCount > 5) {
            System.out.printf("    ... (%d more rows)%n", rowCount - 5);
        }
        System.out.printf("    Total rows: %d%n", rowCount);
        return rowCount;
    }

    private static String detectDriverVersion() {
        try {
            Driver driver = DriverManager.getDriver("jdbc:mysql://localhost:3306/");
            return driver.getMajorVersion() + "." + driver.getMinorVersion();
        } catch (Exception e) {
            // Fallback: read from package metadata
            try {
                Package pkg = Class.forName("com.mysql.cj.jdbc.Driver").getPackage();
                if (pkg != null && pkg.getImplementationVersion() != null) {
                    return pkg.getImplementationVersion();
                }
            } catch (Exception ignored) {}
        }
        return "unknown";
    }

    private static Properties loadProperties() throws Exception {
        Properties props = new Properties();

        // Try external file first
        java.io.File externalConfig = new java.io.File("connection.properties");
        if (externalConfig.exists()) {
            try (InputStream input = new java.io.FileInputStream(externalConfig)) {
                props.load(input);
                System.out.println("[CONFIG] Loaded from: " + externalConfig.getAbsolutePath());
                return props;
            }
        }

        // Fallback to classpath
        try (InputStream input = JdbcVersionTest.class.getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new Exception("Unable to find " + PROPERTIES_FILE);
            }
            props.load(input);
            System.out.println("[CONFIG] Loaded from classpath");
            return props;
        }
    }

    // ================================================================
    // Output Formatting
    // ================================================================

    private static void printBanner(String driverVersion, String host, int port, String database, String user) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║       JDBC Version Compatibility Test for Apache Doris              ║");
        System.out.println("║       Issue: https://github.com/apache/doris/issues/60634           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Driver Version : %-50s║%n", driverVersion);
        System.out.printf( "║  Doris Endpoint : %-50s║%n", host + ":" + port);
        System.out.printf( "║  Database       : %-50s║%n", database);
        System.out.printf( "║  User           : %-50s║%n", user);
        System.out.printf( "║  Java Version   : %-50s║%n", System.getProperty("java.version"));
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Test Params:                                                       ║");
        for (Map.Entry<String, String> e : PARAM_COMBOS.entrySet()) {
            String display = e.getValue().isEmpty() ? "(none)" : e.getValue();
            System.out.printf("║    %-16s : %-46s║%n", e.getKey(), display);
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    private static void printSummary(String driverVersion) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         TEST SUMMARY                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Driver Version : %-50s║%n", driverVersion);
        System.out.printf( "║  Total Tests    : %-50d║%n", totalTests);
        System.out.printf( "║  Passed         : %-50d║%n", passedTests);
        System.out.printf( "║  Failed         : %-50d║%n", failedTests);
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        if (failedTests == 0) {
            System.out.println("║  ✅ ALL TESTS PASSED                                                ║");
        } else {
            System.out.println("║  ❌ SOME TESTS FAILED:                                              ║");
            for (FailedTestRecord rec : failedRecords) {
                System.out.printf("║  - %-66s║%n", rec.testName);
                System.out.printf("║      Expected: %-53s║%n", rec.expected);
                System.out.printf("║      Actual:   %-53s║%n", rec.actual);
            }
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Print detailed reproduction steps for each failed test case.
     * This gives the user exact JDBC URL, SQL, and Java code to reproduce.
     */
    private static void printReproductionSteps(String driverVersion,
                                               String host, int port, String database, String user) {
        System.out.println();
        System.out.println("################################################################################");
        System.out.println("#                     FAILED TEST REPRODUCTION STEPS                            #");
        System.out.println("################################################################################");
        System.out.println();

        int idx = 0;
        for (FailedTestRecord rec : failedRecords) {
            idx++;
            System.out.println("────────────────────────────────────────────────────────────────────────────────");
            System.out.printf("  Failed Case #%d: %s%n", idx, rec.testName);
            System.out.println("────────────────────────────────────────────────────────────────────────────────");
            System.out.println();
            System.out.println("  Driver Version : " + driverVersion);
            System.out.println("  JDBC URL       : " + rec.jdbcUrl);
            System.out.println("  JDBC Params    : " + rec.jdbcParams);
            System.out.println("  Query Method   : " + rec.queryMethod);
            System.out.println("  SQL            : " + rec.sql);
            System.out.println("  Bind Params    : " + rec.bindParams);
            System.out.println("  Expected       : " + rec.expected);
            System.out.println("  Actual         : " + rec.actual);
            System.out.println();
            System.out.println("  Reproduction Java Code:");
            System.out.println("  ┌─────────────────────────────────────────────────────────────────────────");

            if (rec.queryMethod.equals("PreparedStatement")) {
                System.out.println("  │ String url = \"" + rec.jdbcUrl + "\";");
                System.out.println("  │ Connection conn = DriverManager.getConnection(url, \"" + user + "\", \"<password>\");");
                System.out.println("  │ PreparedStatement pstmt = conn.prepareStatement(\"" + rec.sql + "\");");
                if (!rec.bindParams.equals("(none)")) {
                    System.out.println("  │ " + rec.bindParams + ";");
                }
                System.out.println("  │ ResultSet rs = pstmt.executeQuery();");
                System.out.println("  │ int count = 0;");
                System.out.println("  │ while (rs.next()) { count++; }");
                System.out.println("  │ System.out.println(\"Row count: \" + count);  // Expected: " + rec.expected);
            } else {
                System.out.println("  │ String url = \"" + rec.jdbcUrl + "\";");
                System.out.println("  │ Connection conn = DriverManager.getConnection(url, \"" + user + "\", \"<password>\");");
                System.out.println("  │ Statement stmt = conn.createStatement();");
                System.out.println("  │ ResultSet rs = stmt.executeQuery(\"" + rec.sql + "\");");
                System.out.println("  │ int count = 0;");
                System.out.println("  │ while (rs.next()) { count++; }");
                System.out.println("  │ System.out.println(\"Row count: \" + count);  // Expected: " + rec.expected);
            }

            System.out.println("  └─────────────────────────────────────────────────────────────────────────");
            System.out.println();
        }

        System.out.println("################################################################################");
        System.out.println("#  Key Finding: All failures occur with useServerPrepStmts=true                 #");
        System.out.println("#  Workaround:  Set useServerPrepStmts=false&cacheResultSetMetadata=true        #");
        System.out.println("#  Issue:       https://github.com/apache/doris/issues/60634                    #");
        System.out.println("################################################################################");
    }
}
