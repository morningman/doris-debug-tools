package com.doris.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Factory class for creating different types of JDBC connections to Doris database.
 */
public class ConnectionFactory {

    /**
     * Creates a basic JDBC connection with standard authentication.
     */
    public static Connection createBasicConnection(String host, int port, String database,
                                                   String user, String password) throws SQLException {
        String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Creates an SSL-enabled JDBC connection.
     */
    public static Connection createSSLConnection(String host, int port, String database,
                                                String user, String password) throws SQLException {
        // Check if certificate verification should be skipped
        boolean verifyServerCert = !Boolean.getBoolean("doris.ssl.skipVerify");

        String url = String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=true&requireSSL=true&verifyServerCertificate=%s&allowPublicKeyRetrieval=true",
            host, port, database, verifyServerCert
        );
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Creates an LDAP-authenticated connection using custom ClearPasswordPlugin.
     *
     * Uses the Doris-recommended approach for LDAP authentication:
     * - authenticationPlugins: specifies the custom plugin
     * - defaultAuthenticationPlugin: sets it as default
     * - disabledAuthenticationPlugins: disables the default MySQL clear password plugin
     */
    public static Connection createLDAPConnection(String host, int port, String database,
                                                  String user, String password) throws SQLException {
        String url = String.format(
            "jdbc:mysql://%s:%d/%s?authenticationPlugins=com.doris.jdbc.auth.ClearPasswordPlugin&defaultAuthenticationPlugin=com.doris.jdbc.auth.ClearPasswordPlugin&disabledAuthenticationPlugins=com.mysql.jdbc.authentication.MysqlClearPasswordPlugin",
            host, port, database
        );
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Creates an LDAP-authenticated connection with SSL enabled.
     *
     * When SSL is enabled, the standard MySQL authentication plugin can be used
     * because SSL provides the necessary encryption for password transmission.
     * No need for custom ClearPasswordPlugin in this case.
     */
    public static Connection createLDAPSSLConnection(String host, int port, String database,
                                                     String user, String password) throws SQLException {
        // Check if certificate verification should be skipped
        boolean verifyServerCert = !Boolean.getBoolean("doris.ssl.skipVerify");

        String url = String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=true&sslMode=REQUIRED",
            host, port, database, verifyServerCert
        );
        return DriverManager.getConnection(url, user, password);
    }
}
