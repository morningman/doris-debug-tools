#!/bin/bash
#
# Build the JDBC Version Test project with a specific mysql-connector-j version.
#
# Usage:
#   ./build.sh              # Build with default version (8.0.33)
#   ./build.sh v9.5.0       # Build with mysql-connector-j 9.5.0
#
# Available profiles: v8.0.33, v9.1.0, v9.2.0, v9.4.0, v9.5.0, v9.6.0
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check Java
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    echo "Error: Java 17 or higher is required"
    echo "Current version: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

PROFILE="$1"

echo "========================================="
echo "Building JDBC Version Test"
echo "========================================="
echo "Java: $(java -version 2>&1 | head -n 1)"

if [ -n "$PROFILE" ]; then
    echo "Profile: $PROFILE"
    echo "========================================="
    mvn clean package -P "$PROFILE" -q -DskipTests
else
    echo "Profile: default (8.0.33)"
    echo "========================================="
    mvn clean package -q -DskipTests
fi

echo ""
echo "Build successful!"
echo "JAR: target/jdbc-version-test-1.0-SNAPSHOT-jar-with-dependencies.jar"
echo ""
echo "Run with: ./run.sh"
