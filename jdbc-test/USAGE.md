# Doris JDBC Connection - Quick Start Guide

## Prerequisites

- Java 17 (located at: /mnt/disk1/yy/tools/jdk-17.0.8)
- Maven
- Apache Doris database instance

## Installation

The project has already been built successfully!

## Configuration

Edit the connection settings in `src/main/resources/connection.properties`:

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

## Usage

### Run with Basic Authentication
```bash
./run.sh basic
```

### Run with SSL Connection
```bash
./run.sh ssl
```

### Run with LDAP Authentication
```bash
./run.sh ldap
```

## What the Application Does

When you run the application, it will:

1. Connect to the Doris database using the specified authentication mode
2. Create a test database (`test_db`) if it doesn't exist
3. Switch to the test database
4. List all available databases
5. Create a test table (`test_table`) with columns: id (INT), name (VARCHAR)
6. Insert a test record: (1, 'test_data')
7. Query and display all records from the test table

## LDAP Authentication Details

The LDAP mode uses a custom authentication plugin based on Apache Doris documentation:

**Plugin Class:** `com.doris.jdbc.auth.ClearPasswordPlugin`

**How it works:**
- Extends `MysqlClearPasswordPlugin` from MySQL Connector/J
- Overrides `requiresConfidentiality()` to return `false`
- Enables clear-text password transmission for LDAP authentication

**JDBC URL Configuration:**
```
jdbc:mysql://host:port/database?
  authenticationPlugins=com.doris.jdbc.auth.ClearPasswordPlugin
  &defaultAuthenticationPlugin=com.doris.jdbc.auth.ClearPasswordPlugin
  &disabledAuthenticationPlugins=com.mysql.jdbc.authentication.MysqlClearPasswordPlugin
```

## Rebuilding the Project

If you make any code changes:

```bash
./build.sh
```

This will:
- Clean previous builds
- Compile the Java source code
- Package into an executable JAR with all dependencies
- Output to: `target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Troubleshooting

### Connection Refused
- Verify Doris is running
- Check host and port in connection.properties
- Ensure firewall allows connections to port 9030

### Authentication Failed
- Verify username and password in connection.properties
- For LDAP mode: ensure LDAP authentication is enabled on Doris server
- For LDAP mode: verify the LDAP user exists in the LDAP directory

### Build Errors
- Ensure Java 17 is available: `/mnt/disk1/yy/tools/jdk-17.0.8`
- Check Maven is installed: `mvn --version`
- Verify internet connection for dependency downloads

## Project Structure

```
ldap/
├── pom.xml                                      # Maven configuration
├── build.sh                                     # Build script
├── run.sh                                       # Run script
├── README.md                                    # Full documentation
├── USAGE.md                                     # This quick start guide
├── src/
│   └── main/
│       ├── java/com/doris/jdbc/
│       │   ├── DorisConnection.java            # Main application
│       │   ├── ConnectionFactory.java          # Connection builder
│       │   └── auth/
│       │       └── ClearPasswordPlugin.java    # Custom LDAP auth plugin
│       └── resources/
│           └── connection.properties           # Database configuration
└── target/
    └── doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Reference

- Apache Doris Documentation: https://doris.apache.org/docs/4.x/admin-manual/auth/authentication/federation
- MySQL Clear-Text Authentication: https://dev.mysql.com/doc/refman/8.0/en/cleartext-pluggable-authentication.html
