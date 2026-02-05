# Doris JDBC Connection Project

A Maven-based Java application for connecting to Apache Doris database with support for multiple authentication modes.

## Features

- **Basic Authentication**: Standard username/password connection
- **SSL Connection**: Secure connection with SSL/TLS encryption
- **LDAP Authentication**: Custom authentication plugin for LDAP integration
- **Automated Testing**: Executes test SQL commands to verify connectivity

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Access to a Doris database instance

## Project Structure

```
ldap/
├── pom.xml                          # Maven configuration
├── build.sh                         # Build script
├── run.sh                           # Run script
├── src/
│   └── main/
│       ├── java/
│       │   └── com/doris/jdbc/
│       │       ├── DorisConnection.java          # Main application
│       │       ├── ConnectionFactory.java        # Connection builder
│       │       └── auth/
│       │           └── ClearPasswordPlugin.java  # Custom auth plugin
│       └── resources/
│           └── connection.properties             # Connection settings
└── target/                          # Build output (generated)
```

## Quick Start

### 1. Configure Connection Settings

Edit `src/main/resources/connection.properties` with your Doris connection details:

```properties
# Basic Authentication
doris.host=localhost
doris.port=9030
doris.database=test_db
doris.user=root
doris.password=

# SSL Connection
doris.ssl.host=localhost
doris.ssl.port=9030
doris.ssl.database=test_db
doris.ssl.user=root
doris.ssl.password=

# LDAP Authentication
doris.ldap.host=localhost
doris.ldap.port=9030
doris.ldap.database=test_db
doris.ldap.user=ldap_user
doris.ldap.password=ldap_password
```

### 2. Build the Project

```bash
./build.sh
```

This will:
- Clean previous builds
- Compile the Java source code
- Package into an executable JAR with dependencies
- Make the run script executable

### 3. Run the Application

Choose one of the three connection modes:

```bash
# Basic authentication
./run.sh basic

# SSL-enabled connection
./run.sh ssl

# LDAP authentication
./run.sh ldap
```

## Connection Modes

### Basic Mode

Uses standard JDBC authentication with username and password.

**JDBC URL format:**
```
jdbc:mysql://host:port/database
```

### SSL Mode

Enables SSL/TLS encryption for secure connections.

**JDBC URL parameters:**
- `useSSL=true` - Enable SSL
- `requireSSL=true` - Require SSL connection
- `verifyServerCertificate=false` - Skip certificate verification (for testing)
- `allowPublicKeyRetrieval=true` - Allow public key retrieval

### LDAP Mode

Uses a custom authentication plugin (`ClearPasswordPlugin`) that extends `MysqlClearPasswordPlugin` and overrides `requiresConfidentiality()` to enable clear-text password authentication for LDAP.

**JDBC URL parameters:**
- `authenticationPlugins=com.doris.jdbc.auth.ClearPasswordPlugin` - Specifies the custom plugin
- `defaultAuthenticationPlugin=com.doris.jdbc.auth.ClearPasswordPlugin` - Sets it as the default
- `disabledAuthenticationPlugins=com.mysql.jdbc.authentication.MysqlClearPasswordPlugin` - Disables the default MySQL plugin

This approach follows the official Apache Doris documentation for LDAP authentication.

## Test Operations

The application automatically executes the following SQL commands to verify connectivity:

1. `CREATE DATABASE IF NOT EXISTS test_db`
2. `USE test_db`
3. `SHOW DATABASES`
4. `CREATE TABLE IF NOT EXISTS test_table (id INT, name VARCHAR(100))`
5. `INSERT INTO test_table VALUES (1, 'test_data')`
6. `SELECT * FROM test_table`

Results are formatted and displayed in the console.

## Custom Authentication Plugin

The `ClearPasswordPlugin` class is designed for Doris LDAP authentication following the official Doris documentation approach:

**Implementation:**
- Extends `MysqlClearPasswordPlugin` from MySQL Connector/J
- Overrides `requiresConfidentiality()` to return `false`
- Allows clear-text password authentication without requiring SSL

**JDBC URL Configuration:**
```
jdbc:mysql://host:port/db?
  authenticationPlugins=com.doris.jdbc.auth.ClearPasswordPlugin
  &defaultAuthenticationPlugin=com.doris.jdbc.auth.ClearPasswordPlugin
  &disabledAuthenticationPlugins=com.mysql.jdbc.authentication.MysqlClearPasswordPlugin
```

**Reference:**
- [Apache Doris Federation Authentication Documentation](https://doris.apache.org/docs/4.x/admin-manual/auth/authentication/federation)

**Security Note:** In production environments, consider using SSL for enhanced security when transmitting passwords.

## Troubleshooting

### Connection Failures

1. Verify Doris is running and accessible
2. Check host, port, and credentials in `connection.properties`
3. Ensure firewall allows connections to Doris port (default 9030)
4. For SSL mode, verify SSL is enabled on Doris server

### Build Failures

1. Verify Java 17 or higher is installed: `java -version`
2. Verify Maven is installed: `mvn -version`
3. Check internet connectivity for Maven dependency downloads

### LDAP Authentication Issues

1. Ensure LDAP authentication is configured on Doris server
2. Verify LDAP user credentials
3. Check that the custom plugin is properly packaged in the JAR

## Build Output

After building, the following files are generated:

- `target/doris-jdbc-1.0-SNAPSHOT.jar` - Regular JAR
- `target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar` - Executable fat JAR with all dependencies

The run script uses the fat JAR for convenience.

## Dependencies

- **MySQL Connector/J 8.0.33**: JDBC driver for MySQL protocol
- **SLF4J 2.0.9**: Logging framework

## License

This project is provided as-is for educational and testing purposes.
