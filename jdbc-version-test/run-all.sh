#!/bin/bash
#
# Run JDBC Version Test across ALL driver versions and produce a comparison summary.
#
# Usage:
#   ./run-all.sh
#
# This script will:
#   1. Build the project for each mysql-connector-j version
#   2. Run the test suite
#   3. Collect PASS/FAIL results
#   4. Print a comparison summary table
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VERSIONS=("v8.0.33" "v9.1.0" "v9.2.0" "v9.4.0" "v9.5.0" "v9.6.0")

LOG_DIR="$SCRIPT_DIR/test-logs"
rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"

SUMMARY_FILE="$LOG_DIR/summary.txt"

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          JDBC Version Compatibility - Batch Test Runner            ║"
echo "║          Issue: https://github.com/apache/doris/issues/60634       ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
echo "Will test versions: ${VERSIONS[*]}"
echo "Logs directory: $LOG_DIR"
echo ""

# Results array
declare -a RESULTS

for VERSION in "${VERSIONS[@]}"; do
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  Building and testing: $VERSION"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    LOG_FILE="$LOG_DIR/${VERSION}.log"

    # Build
    echo "  [1/2] Building..."
    if ! ./build.sh "$VERSION" > "$LOG_DIR/${VERSION}-build.log" 2>&1; then
        echo "  [ERROR] Build failed for $VERSION. See $LOG_DIR/${VERSION}-build.log"
        RESULTS+=("$VERSION|BUILD_FAILED|0|0")
        continue
    fi

    # Run
    echo "  [2/2] Running tests..."
    EXIT_CODE=0
    java -jar target/jdbc-version-test-1.0-SNAPSHOT-jar-with-dependencies.jar > "$LOG_FILE" 2>&1 || EXIT_CODE=$?

    # Parse results from log
    TOTAL=$(grep -o "Total Tests    : [0-9]*" "$LOG_FILE" | grep -o "[0-9]*" || echo "0")
    PASSED=$(grep -o "Passed         : [0-9]*" "$LOG_FILE" | grep -o "[0-9]*" || echo "0")
    FAILED=$(grep -o "Failed         : [0-9]*" "$LOG_FILE" | grep -o "[0-9]*" || echo "0")

    if [ "$EXIT_CODE" -eq 0 ]; then
        STATUS="✅ ALL PASS"
    else
        STATUS="❌ FAILURES"
    fi

    RESULTS+=("$VERSION|$STATUS|$PASSED|$FAILED")
    echo "  Result: $STATUS (passed=$PASSED, failed=$FAILED)"
    echo ""
done

# Print summary table
echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║                     COMPARISON SUMMARY                             ║"
echo "╠══════════════╦════════════════╦══════════╦══════════════════════════╣"
echo "║ Version      ║ Status         ║ Passed   ║ Failed                   ║"
echo "╠══════════════╬════════════════╬══════════╬══════════════════════════╣"

for RESULT in "${RESULTS[@]}"; do
    IFS='|' read -r VER STATUS PASS FAIL <<< "$RESULT"
    printf "║ %-12s ║ %-14s ║ %-8s ║ %-24s ║\n" "$VER" "$STATUS" "$PASS" "$FAIL"
done

echo "╚══════════════╩════════════════╩══════════╩══════════════════════════╝"
echo ""
echo "Detailed logs saved in: $LOG_DIR/"
echo ""

# Also save summary to file
{
    echo "JDBC Version Compatibility Test Summary"
    echo "======================================="
    echo "Date: $(date)"
    echo ""
    printf "%-12s %-16s %-10s %-10s\n" "Version" "Status" "Passed" "Failed"
    printf "%-12s %-16s %-10s %-10s\n" "--------" "--------" "------" "------"
    for RESULT in "${RESULTS[@]}"; do
        IFS='|' read -r VER STATUS PASS FAIL <<< "$RESULT"
        printf "%-12s %-16s %-10s %-10s\n" "$VER" "$STATUS" "$PASS" "$FAIL"
    done
} > "$SUMMARY_FILE"

echo "Summary also saved to: $SUMMARY_FILE"
