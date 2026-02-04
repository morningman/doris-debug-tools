# Doris Ranger Test Program

A standalone test program for quickly verifying Doris Ranger service connectivity and policy retrieval functionality.

## Features

- **Connectivity Testing**: Verify if Ranger Admin service is reachable
- **Plugin Initialization Test**: Check if RangerDorisPlugin is properly initialized
- **Policy Retrieval Test**: Test permission checks across different resource levels
- **Detailed Output**: Display policy ID, version, priority, and other detailed information

## Project Structure

```
doris-ranger-test/
├── pom.xml                         # Maven configuration file
├── src/
│   └── main/
│       ├── java/
│       │   └── org/apache/doris/ranger/test/
│       │       ├── DorisRangerTester.java      # Main test class
│       │       ├── RangerConnectionTest.java   # Connectivity test
│       │       ├── RangerPolicyTest.java       # Policy test
│       │       ├── DorisObjectType.java        # Object type enum
│       │       ├── DorisAccessType.java        # Access type enum
│       │       ├── RangerDorisResource.java    # Resource definition class
│       │       └── RangerDorisPlugin.java      # Ranger plugin class
│       └── resources/
│           ├── ranger-doris-security.xml.template  # Ranger config template
│           └── log4j2.xml                          # Logging configuration
├── config/
│   └── ranger-doris-security.xml.example       # Configuration example
├── build.sh                        # Build script
├── run.sh                          # Run script
└── README.md                       # Usage documentation
```

## Quick Start

### 1. Build the Project

```bash
cd doris-ranger-test
./build.sh
```

### 2. Configure Ranger Connection

Copy the configuration template and edit it:

```bash
cp config/ranger-doris-security.xml.example config/ranger-doris-security.xml
vim config/ranger-doris-security.xml
```

Modify the following key configuration items:

```xml
<!-- Ranger Admin service address -->
<property>
    <name>ranger.plugin.doris.policy.rest.url</name>
    <value>http://your-ranger-admin-host:6080</value>
</property>

<!-- Ranger service name -->
<property>
    <name>ranger.plugin.doris.service.name</name>
    <value>your-doris-service-name</value>
</property>
```

### 3. Run the Test

```bash
./run.sh --config config/ranger-doris-security.xml
```

#### Run with Parameters

```bash
# Specify service name and test user
./run.sh --config config/ranger-doris-security.xml \
         --service-name doris \
         --test-user admin
```

## Command Line Parameters

### run.sh Parameters

| Parameter | Description | Required | Default |
|------|------|------|--------|
| `--config <file>` | Ranger configuration file path | Yes | - |
| `--service-name <name>` | Ranger service name | No | doris |
| `--test-user <user>` | Test username | No | test_user |
| `--help, -h` | Display help information | No | - |

## Configuration File Description

### ranger-doris-security.xml Key Configuration Items

```xml
<!-- Ranger Admin REST API address -->
<property>
    <name>ranger.plugin.doris.policy.rest.url</name>
    <value>http://localhost:6080</value>
</property>

<!-- Doris service name (must match the service name configured in Ranger Admin) -->
<property>
    <name>ranger.plugin.doris.service.name</name>
    <value>doris</value>
</property>

<!-- Policy refresh interval (milliseconds) -->
<property>
    <name>ranger.plugin.doris.policy.pollIntervalMs</name>
    <value>30000</value>
</property>

<!-- Policy cache directory -->
<property>
    <name>ranger.plugin.doris.policy.cache.dir</name>
    <value>/tmp/ranger-doris-cache</value>
</property>

<!-- Connection timeout (milliseconds) -->
<property>
    <name>ranger.plugin.doris.policy.rest.client.connection.timeoutMs</name>
    <value>120000</value>
</property>

<!-- Read timeout (milliseconds) -->
<property>
    <name>ranger.plugin.doris.policy.rest.client.read.timeoutMs</name>
    <value>30000</value>
</property>
```

## Test Contents

### 1. Connectivity Test

- Plugin initialization check
- Ranger Admin connection check
- Policy Engine status check
- Policy version information

### 2. Policy Test

Test permission checks across the following resource levels:

- **GLOBAL**: Global permissions (e.g., ADMIN)
- **CATALOG**: Catalog-level permissions (e.g., internal)
- **DATABASE**: Database-level permissions (e.g., internal.test_db)
- **TABLE**: Table-level permissions (e.g., internal.test_db.test_table)
- **COLUMN**: Column-level permissions (e.g., internal.test_db.test_table.col1)

## Sample Output

### Successful Connection Example

```
========================================
Doris Ranger Test Program
========================================
Configuration File: /path/to/ranger-doris-security.xml
Service Name: doris
Test User: test_user

Ranger Admin URL: http://localhost:6080

Initializing Ranger Plugin...
✓ Ranger Plugin initialized

========================================
Doris Ranger Connectivity Test
========================================
Service Name: doris

[TEST 1] Plugin Initialization
  ✓ RangerDorisPlugin created successfully
  ✓ Service name: doris

[TEST 2] Ranger Admin Connection
  ✓ Connected to Ranger Admin
  ✓ Policy Engine initialized: true
  ✓ Policy download successful

[TEST 3] Policy Cache Information
  ✓ Policy version: 5
  ✓ Current time: 2026-02-03 10:30:15

========================================
Doris Ranger Policy Test
========================================
Test User: test_user
Test Resources: various (GLOBAL, CATALOG, DATABASE, TABLE, COLUMN)

[TEST 1] Global Privilege Check
  Resource: GLOBAL (*)
  Access Type: ADMIN
  Result: DENIED
  Policy ID: -1

[TEST 2] Catalog Privilege Check
  Resource: CATALOG (internal)
  Access Type: SELECT
  Result: ALLOWED
  Policy ID: 101
  Policy Version: 2
  Policy Priority: NORMAL

...

========================================
Summary
========================================
Total Tests: 5
Passed: 5
Failed: 0

Note: To verify complete functionality, you may need to
create corresponding policies in Ranger first.
```

## Troubleshooting

### Issue 1: Unable to Connect to Ranger Admin

**Symptoms**:
```
✗ Policy Engine is null
! This may indicate connection issues with Ranger Admin
```

**Solutions**:
1. Check if Ranger Admin service is running: `curl http://ranger-host:6080`
2. Verify the URL in the configuration file is correct
3. Check network connectivity and firewall settings
4. Check Ranger Admin logs

### Issue 2: Configuration File Not Found

**Symptoms**:
```
Error: Configuration file not found: xxx
```

**Solutions**:
1. Confirm the configuration file path is correct
2. Use absolute path or path relative to the run directory
3. Ensure the configuration file exists and is readable

### Issue 3: All Policy Tests Return DENIED

**Symptoms**:
```
Result: DENIED
Policy ID: -1
```

**Solutions**:
1. Create corresponding policies in Ranger Admin
2. Ensure service name matches the service name in Ranger
3. Ensure the test user has corresponding permissions in Ranger policies
4. Wait for policy refresh (default 30 seconds)

## Dependency Versions

- Java: 1.8+
- Maven: 3.6+
- Ranger: 2.4.0
- Hadoop: 3.3.4
- Log4j: 2.17.1

## Maven Dependencies

Main dependencies:
- `org.apache.ranger:ranger-plugins-common:2.4.0`
- `org.apache.hadoop:hadoop-common:3.3.4`
- `org.apache.logging.log4j:log4j-core:2.17.1`

## Development Notes

### Adding New Test Scenarios

1. Add new test methods in `RangerPolicyTest.java`
2. Use `testAccessRequest()` method to construct test requests
3. Rebuild and run tests

### Modifying Log Level

Edit `src/main/resources/log4j2.xml`:

```xml
<!-- Adjust Ranger log level to debug -->
<Logger name="org.apache.ranger" level="debug" additivity="false">
    <AppenderRef ref="Console"/>
</Logger>
```

## Limitations and Notes

1. This program is for testing purposes only, not suitable for production environments
2. SSL/TLS connections are not supported (can be added via configuration file)
3. Kerberos authentication is not supported (requires additional configuration)
4. Test results depend on policy configuration in Ranger Admin

## License

Licensed under the Apache License, Version 2.0

## Related Resources

- [Apache Ranger Official Documentation](https://ranger.apache.org/)
- [Apache Doris Official Documentation](https://doris.apache.org/)
- [Ranger Plugin Development Guide](https://ranger.apache.org/developer-guide.html)

## Contact

If you have any questions or suggestions, please submit an issue.
