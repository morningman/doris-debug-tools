package com.doris.jdbc;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

/**
 * Main application for testing Doris JDBC connections with different authentication modes.
 */
public class DorisConnection {

    private static final String PROPERTIES_FILE = "connection.properties";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -jar doris-jdbc.jar <mode>");
            System.out.println("Modes: basic, ssl, ldap, ldapssl");
            System.out.println("\nExample:");
            System.out.println("  java -jar doris-jdbc.jar basic");
            System.out.println("  java -jar doris-jdbc.jar ssl");
            System.out.println("  java -jar doris-jdbc.jar ldap");
            System.out.println("  java -jar doris-jdbc.jar ldapssl");
            System.exit(1);
        }

        String mode = args[0].toLowerCase();

        try {
            // Load properties
            Properties props = loadProperties();

            // Get connection parameters based on mode
            String host, user, password;
            int port;
            String database;

            switch (mode) {
                case "basic":
                    host = props.getProperty("doris.host");
                    port = Integer.parseInt(props.getProperty("doris.port"));
                    database = props.getProperty("doris.database");
                    user = props.getProperty("doris.user");
                    password = props.getProperty("doris.password", "");
                    break;

                case "ssl":
                    host = props.getProperty("doris.ssl.host");
                    port = Integer.parseInt(props.getProperty("doris.ssl.port"));
                    database = props.getProperty("doris.ssl.database");
                    user = props.getProperty("doris.ssl.user");
                    password = props.getProperty("doris.ssl.password", "");
                    break;

                case "ldap":
                    host = props.getProperty("doris.ldap.host");
                    port = Integer.parseInt(props.getProperty("doris.ldap.port"));
                    database = props.getProperty("doris.ldap.database");
                    user = props.getProperty("doris.ldap.user");
                    password = props.getProperty("doris.ldap.password", "");
                    break;

                case "ldapssl":
                    host = props.getProperty("doris.ldapssl.host");
                    port = Integer.parseInt(props.getProperty("doris.ldapssl.port"));
                    database = props.getProperty("doris.ldapssl.database");
                    user = props.getProperty("doris.ldapssl.user");
                    password = props.getProperty("doris.ldapssl.password", "");
                    break;

                default:
                    System.err.println("Invalid mode: " + mode);
                    System.err.println("Valid modes are: basic, ssl, ldap, ldapssl");
                    System.exit(1);
                    return;
            }

            System.out.println("========================================");
            System.out.println("Doris JDBC Connection Test");
            System.out.println("========================================");
            System.out.println("Mode: " + mode.toUpperCase());
            System.out.println("Host: " + host + ":" + port);
            System.out.println("User: " + user);
            System.out.println("Database: " + database);
            System.out.println("========================================\n");

            // Establish connection
            Connection conn = null;
            try {
                conn = createConnection(mode, host, port, database, user, password);
                System.out.println("[SUCCESS] Connected to Doris database\n");

                // Execute test SQL commands
                executeTestCommands(conn, database);

            } finally {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                    System.out.println("\n[INFO] Connection closed");
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Connection failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Properties loadProperties() throws Exception {
        Properties props = new Properties();

        // Try to load from current directory first (for portable deployment)
        java.io.File externalConfig = new java.io.File("connection.properties");
        if (externalConfig.exists()) {
            try (InputStream input = new java.io.FileInputStream(externalConfig)) {
                props.load(input);
                System.out.println("[CONFIG] Loaded from: " + externalConfig.getAbsolutePath());
                return props;
            }
        }

        // Fall back to classpath resource (for development)
        try (InputStream input = DorisConnection.class.getClassLoader()
                .getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new Exception("Unable to find " + PROPERTIES_FILE + " in classpath or current directory");
            }
            props.load(input);
            System.out.println("[CONFIG] Loaded from classpath");
            return props;
        }
    }

    private static Connection createConnection(String mode, String host, int port,
                                              String database, String user, String password)
            throws SQLException {
        switch (mode) {
            case "basic":
                return ConnectionFactory.createBasicConnection(host, port, database, user, password);
            case "ssl":
                return ConnectionFactory.createSSLConnection(host, port, database, user, password);
            case "ldap":
                return ConnectionFactory.createLDAPConnection(host, port, database, user, password);
            case "ldapssl":
                return ConnectionFactory.createLDAPSSLConnection(host, port, database, user, password);
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void executeTestCommands(Connection conn, String database) throws SQLException {
        Statement stmt = conn.createStatement();

        try {
            // 1. Create database
            System.out.println("[1/6] Creating database if not exists...");
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + database);
            System.out.println("      Database '" + database + "' is ready\n");

            // 2. Use database
            System.out.println("[2/6] Switching to database...");
            stmt.execute("USE " + database);
            System.out.println("      Now using database '" + database + "'\n");

            // 3. Show databases
            System.out.println("[3/6] Listing databases...");
            ResultSet rs = stmt.executeQuery("SHOW DATABASES");
            System.out.println("      Available databases:");
            while (rs.next()) {
                System.out.println("      - " + rs.getString(1));
            }
            rs.close();
            System.out.println();

            // 4. Create table
            System.out.println("[4/6] Creating test table...");
            stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT, name VARCHAR(100)) PROPERTIES('replication_num' = '1')");
            System.out.println("      Table 'test_table' created\n");

            // 5. Insert data
            System.out.println("[5/6] Inserting test data...");
            int rows = stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'test_data')");
            System.out.println("      Inserted " + rows + " row(s)\n");

            // 6. Select data
            System.out.println("[6/6] Querying test table...");
            rs = stmt.executeQuery("SELECT * FROM test_table");
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Print header
            System.out.println("      Results:");
            System.out.print("      ");
            for (int i = 1; i <= columnCount; i++) {
                System.out.print(String.format("%-20s", metaData.getColumnName(i)));
            }
            System.out.println();
            System.out.print("      ");
            for (int i = 1; i <= columnCount; i++) {
                System.out.print(String.format("%-20s", "--------------------"));
            }
            System.out.println();

            // Print rows
            while (rs.next()) {
                System.out.print("      ");
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(String.format("%-20s", rs.getString(i)));
                }
                System.out.println();
            }
            rs.close();

            System.out.println("\n========================================");
            System.out.println("All tests completed successfully!");
            System.out.println("========================================");

        } finally {
            stmt.close();
        }
    }
}
