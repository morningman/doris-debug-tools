#!/usr/bin/env bash
#
# Build script for MaxCompute Large String Write Test
#
# Usage:
#   ./build.sh          # normal build
#   ./build.sh clean     # clean and rebuild
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ">>> Building mc-string-write-test ..."

if [ "${1:-}" = "clean" ]; then
    echo ">>> Cleaning previous build ..."
    mvn -f "${SCRIPT_DIR}/pom.xml" clean
fi

mvn -f "${SCRIPT_DIR}/pom.xml" package -DskipTests

echo ""
echo ">>> Build successful!"
echo ">>> JAR: ${SCRIPT_DIR}/target/mc-string-write-test-1.0-SNAPSHOT.jar"
