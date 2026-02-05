package com.doris.jdbc.auth;

import com.mysql.cj.protocol.a.authentication.MysqlClearPasswordPlugin;

/**
 * Custom authentication plugin for Doris LDAP authentication.
 *
 * This plugin extends MysqlClearPasswordPlugin and overrides requiresConfidentiality()
 * to return false, allowing clear-text password authentication without SSL.
 *
 * Based on Apache Doris documentation:
 * https://doris.apache.org/docs/4.x/admin-manual/auth/authentication/federation
 *
 * Usage in JDBC URL:
 * jdbc:mysql://host:port/db?authenticationPlugins=com.doris.jdbc.auth.ClearPasswordPlugin
 *   &defaultAuthenticationPlugin=com.doris.jdbc.auth.ClearPasswordPlugin
 *   &disabledAuthenticationPlugins=com.mysql.jdbc.authentication.MysqlClearPasswordPlugin
 */
public class ClearPasswordPlugin extends MysqlClearPasswordPlugin {

    /**
     * Override to return false, allowing clear-text password transmission without SSL.
     *
     * Note: In production environments, it's recommended to use SSL for security.
     * This is primarily for LDAP authentication scenarios where SSL is not available.
     */
    @Override
    public boolean requiresConfidentiality() {
        return false;
    }
}

