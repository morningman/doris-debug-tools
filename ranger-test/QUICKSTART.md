# Doris Ranger Test - Quick Start Guide

This document provides a quick start guide to help you compile, configure, and run the Doris Ranger test program.

## Prerequisites

- Java 1.8 or higher
- Maven 3.6 or higher
- Accessible Ranger Admin service (or prepare to test connection failure scenarios)

## Quick Start (3 Steps)

### Step 1: Build the Project

```bash
cd /mnt/disk1/yy/git/doris-ranger-test
./build.sh
```

Expected output:
```
==========================================
Building Doris Ranger Test
==========================================
[INFO] Scanning for projects...
[INFO] Building Doris Ranger Test 1.0-SNAPSHOT
...
[INFO] BUILD SUCCESS
==========================================
Build completed successfully!
==========================================
JAR location: target/doris-ranger-test-1.0-SNAPSHOT-jar-with-dependencies.jar

To run the test program:
  ./run.sh --config config/ranger-doris-security.xml
```

### Step 2: Configure Ranger Connection

```bash
# Copy the configuration template
cp config/ranger-doris-security.xml.example config/ranger-doris-security.xml

# Edit the configuration file (modify according to actual situation)
vim config/ranger-doris-security.xml
```

**Required configuration items**:

```xml
<!-- Modify to your Ranger Admin address -->
<property>
    <name>ranger.plugin.doris.policy.rest.url</name>
    <value>http://YOUR-RANGER-HOST:6080</value>
</property>

<!-- Modify to your Doris service name created in Ranger -->
<property>
    <name>ranger.plugin.doris.service.name</name>
    <value>YOUR-SERVICE-NAME</value>
</property>
```

### Step 3: Run the Test

```bash
./run.sh --config config/ranger-doris-security.xml
```

**Run with parameters**:

```bash
./run.sh --config config/ranger-doris-security.xml \
         --service-name your-service \
         --test-user admin
```

## Expected Output

### Successful Connection to Ranger

```
========================================
Doris Ranger Test Program
========================================
Configuration File: config/ranger-doris-security.xml
Service Name: doris
Test User: test_user

Ranger Admin URL: http://your-ranger-host:6080

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
  ! Note: Policy count and cache details require Ranger Admin API access

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
  Policy Priority: 0

...

========================================
Summary
========================================
Total Tests: 5
Passed: 5
Failed: 0

Note: To verify complete functionality, you may need to
create corresponding policies in Ranger first.

========================================
Test Completed
========================================
```

### Unable to Connect to Ranger

```
Initializing Ranger Plugin...
✗ Failed to initialize Ranger Plugin
Error: Connection refused

Connectivity test failed. Please check:
  1. Ranger Admin service is running and accessible
  2. Configuration file has correct settings
  3. Network connectivity to Ranger Admin
```

## Common Troubleshooting

### 1. Build Failure

**Problem**: Maven cannot find dependencies

**Solution**:
```bash
# Clean and re-download dependencies
mvn clean
rm -rf ~/.m2/repository/org/apache/ranger
mvn package -DskipTests
```

### 2. Configuration File Not Found

**Problem**: `Error: Configuration file not found`

**Solution**:
```bash
# Ensure configuration file exists
ls -l config/ranger-doris-security.xml

# Use absolute path
./run.sh --config /full/path/to/ranger-doris-security.xml
```

### 3. Connection Timeout

**Problem**: Connection to Ranger Admin times out

**Solution**:
1. Check Ranger Admin service status
2. Check network connection: `curl http://ranger-host:6080`
3. Increase timeout (in configuration file):
```xml
<property>
    <name>ranger.plugin.doris.policy.rest.client.connection.timeoutMs</name>
    <value>300000</value>  <!-- Increase to 5 minutes -->
</property>
```

### 4. All Policy Tests Return DENIED

This is normal! You need to create corresponding policies in Ranger Admin.

**Solution Steps**:
1. Log in to Ranger Admin Web UI
2. Create Doris service (if not already created)
3. Add policies for the test user
4. Wait 30 seconds (policy refresh interval)
5. Re-run the test

## Test Scenario Description

The program will test permissions for the following 5 resource types:

1. **GLOBAL**: `*` - Global admin permissions
2. **CATALOG**: `internal` - Catalog access permissions
3. **DATABASE**: `internal.test_db` - Database access permissions
4. **TABLE**: `internal.test_db.test_table` - Table access permissions
5. **COLUMN**: `internal.test_db.test_table.col1` - Column access permissions

## Next Steps

1. Create test policies in Ranger Admin
2. Test with different users and resources
3. Verify policy priority and inheritance
4. Test row-level filtering and column masking

## Getting Help

```bash
# View command line help
./run.sh --help

# View complete documentation
cat README.md
```

## Project Directory Structure

```
doris-ranger-test/
├── build.sh                    # Build script
├── run.sh                      # Run script
├── pom.xml                     # Maven configuration
├── config/
│   └── ranger-doris-security.xml.example  # Configuration template
├── src/
│   └── main/
│       ├── java/              # Java source code
│       └── resources/         # Resource files
└── target/                    # Build output (generated after compilation)
    └── doris-ranger-test-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## License

Apache License 2.0
