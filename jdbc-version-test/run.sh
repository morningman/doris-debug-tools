#!/bin/bash
#
# Run the JDBC Version Test.
#
# Usage:
#   ./run.sh             # Run (must ./build.sh first)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

JAR_FILE="target/jdbc-version-test-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please run ./build.sh first"
    exit 1
fi

java -jar "$JAR_FILE"
