#!/usr/bin/env bash
#
# Run script for MaxCompute Large String Write Test
#
# Usage:
#   1. First, fill in the required config values below.
#   2. Run: ./run.sh
#
# Or pass config via command line:
#   ./run.sh --endpoint <endpoint> --project <project> --accessKey <ak> --secretKey <sk>
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ============================================================
# Configuration — fill in your MaxCompute credentials here
# ============================================================
MC_ENDPOINT="${MC_ENDPOINT:-}"       # e.g., http://service.cn-hangzhou.maxcompute.aliyun.com/api
MC_PROJECT="${MC_PROJECT:-}"         # e.g., my_project
MC_ACCESS_KEY="${MC_ACCESS_KEY:-}"   # Aliyun AccessKey ID
MC_SECRET_KEY="${MC_SECRET_KEY:-}"   # Aliyun AccessKey Secret

# Optional settings
MC_TABLE="${MC_TABLE:-mc_large_string_test}"
MC_ROWS="${MC_ROWS:-10}"
MC_STRING_SIZE="${MC_STRING_SIZE:-1048576}"   # 1MB per row
MC_BATCH_ROWS="${MC_BATCH_ROWS:-1}"
MC_QUOTA="${MC_QUOTA:-pay-as-you-go}"
SKIP_CREATE="${SKIP_CREATE:-false}"

# ============================================================
# Build (if needed)
# ============================================================
JAR_FILE="${SCRIPT_DIR}/target/mc-string-write-test-1.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo ">>> Building project ..."
    mvn -f "${SCRIPT_DIR}/pom.xml" package -DskipTests -q
fi

# ============================================================
# Run
# ============================================================
ARGS=()

# Use env vars if command-line args not provided
if [ $# -eq 0 ]; then
    if [ -z "$MC_ENDPOINT" ] || [ -z "$MC_PROJECT" ] || [ -z "$MC_ACCESS_KEY" ] || [ -z "$MC_SECRET_KEY" ]; then
        echo "ERROR: Please set MC_ENDPOINT, MC_PROJECT, MC_ACCESS_KEY, MC_SECRET_KEY environment variables"
        echo "       or pass them as command-line arguments."
        echo ""
        echo "Example:"
        echo "  export MC_ENDPOINT=http://service.cn-hangzhou.maxcompute.aliyun.com/api"
        echo "  export MC_PROJECT=my_project"
        echo "  export MC_ACCESS_KEY=your_access_key"
        echo "  export MC_SECRET_KEY=your_secret_key"
        echo "  ./run.sh"
        exit 1
    fi
    ARGS+=(--endpoint "$MC_ENDPOINT")
    ARGS+=(--project "$MC_PROJECT")
    ARGS+=(--accessKey "$MC_ACCESS_KEY")
    ARGS+=(--secretKey "$MC_SECRET_KEY")
    ARGS+=(--table "$MC_TABLE")
    ARGS+=(--rows "$MC_ROWS")
    ARGS+=(--stringSize "$MC_STRING_SIZE")
    ARGS+=(--batchRows "$MC_BATCH_ROWS")
    ARGS+=(--quota "$MC_QUOTA")
    if [ "$SKIP_CREATE" = "true" ]; then
        ARGS+=(--skipCreate)
    fi
else
    ARGS=("$@")
fi

echo ">>> Running MaxCompute Large String Write Test ..."
echo ">>> JAR: $JAR_FILE"
echo ">>> Args: ${ARGS[*]}"
echo ""

JAVA_OPTS="--add-opens=java.base/java.nio=ALL-UNNAMED"

java ${JAVA_OPTS} -jar "$JAR_FILE" "${ARGS[@]}"
