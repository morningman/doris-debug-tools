# JDBC Version Compatibility Test

A testing tool for [Issue #60634](https://github.com/apache/doris/issues/60634): `mysql-connector-j` 9.5.0+ returns empty query results when connecting to Apache Doris.

## Background

| Key Info | Details |
|---|---|
| **Trigger** | `useServerPrepStmts=true` + mysql-connector-j ≥ 9.5.0 |
| **Symptom** | Queries return empty result sets, but the table actually contains data |
| **Workaround** | `useServerPrepStmts=false&cacheResultSetMetadata=true` |
| **Affected Versions** | Doris 3.1.4 and 4.0.2 are both affected |

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.x
- An accessible Apache Doris cluster

### 1. Configure Connection

Edit `src/main/resources/connection.properties`:

```properties
doris.host=127.0.0.1
doris.port=9030
doris.database=jdbc_version_test
doris.user=root
doris.password=
```

### 2. Test a Single Driver Version

```bash
# Build with a specific driver version profile
./build.sh v9.5.0

# Run tests
./run.sh
```

### 3. Batch Test All Versions

```bash
./run-all.sh
```

This builds and tests all 6 driver versions (8.0.33 ~ 9.6.0) in sequence and outputs a comparison table.

## What's Being Tested

### Driver Versions (Maven Profiles)

| Profile | Version |
|---|---|
| `v8.0.33` | 8.0.33 |
| `v9.1.0` | 9.1.0 |
| `v9.2.0` | 9.2.0 |
| `v9.4.0` | 9.4.0 |
| `v9.5.0` | 9.5.0 |
| `v9.6.0` | 9.6.0 |

### JDBC Parameter Combinations

Each version is tested with 4 parameter combinations:

1. **Default** — No extra URL parameters
2. **`useServerPrepStmts=true`** — Explicitly enable server-side prepared statements (triggers the bug)
3. **`useServerPrepStmts=false`** — Disable server-side prepared statements
4. **`useServerPrepStmts=false&cacheResultSetMetadata=true`** — Recommended workaround from issue discussion

### Query Scenarios (9 tests per parameter combination)

1. `Statement` — `SELECT *`
2. `Statement` — `SELECT ... WHERE`
3. `PreparedStatement` — `SELECT *` (core scenario)
4. `PreparedStatement` — `SELECT ... WHERE id = ?` (core scenario)
5. `PreparedStatement` — `SELECT ... WHERE username = ?`
6. Aggregate query — `COUNT(*)`
7. `SHOW DATABASES`
8. `SHOW TABLES`
9. `ResultSet` metadata inspection

## Sample Output

```
╔══════════════════════════════════════════════════════════════════════╗
║                     COMPARISON SUMMARY                             ║
╠══════════════╦════════════════╦══════════╦══════════════════════════╣
║ Version      ║ Status         ║ Passed   ║ Failed                   ║
╠══════════════╬════════════════╬══════════╬══════════════════════════╣
║ v8.0.33      ║ ✅ ALL PASS    ║ 36       ║ 0                        ║
║ v9.1.0       ║ ✅ ALL PASS    ║ 36       ║ 0                        ║
║ v9.4.0       ║ ✅ ALL PASS    ║ 36       ║ 0                        ║
║ v9.5.0       ║ ❌ FAILURES    ║ 27       ║ 9                        ║
║ v9.6.0       ║ ❌ FAILURES    ║ 27       ║ 9                        ║
╚══════════════╩════════════════╩══════════╩══════════════════════════╝
```

For each failed test case, detailed reproduction steps are printed, including:
- JDBC URL, parameters, SQL statement, and bind parameters
- Copy-pasteable Java code to reproduce the issue

## Project Structure

```
jdbc-version-test/
├── pom.xml                   # Maven profiles to switch driver versions
├── build.sh                  # Build script
├── run.sh                    # Run script
├── run-all.sh                # Batch test all versions
├── README.md                 # Documentation (Chinese)
├── README_EN.md              # Documentation (English)
├── .gitignore
└── src/main/
    ├── java/com/doris/versiontest/
    │   └── JdbcVersionTest.java
    └── resources/
        └── connection.properties
```
