#!/bin/bash

# Doris BE Memory Analyzer
# Usage: sh mem-analyzer.sh <be_pid>

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

function info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

function warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

function error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Parse arguments
BE_PID=$1

if [ -z "$BE_PID" ]; then
    error "Missing BE PID"
    echo "Usage: $0 <be_pid>"
    echo ""
    echo "Example:"
    echo "  $0 12345"
    exit 1
fi

# Check if process exists
if ! ps -p $BE_PID > /dev/null 2>&1; then
    error "Process $BE_PID does not exist"
    exit 1
fi

info "Analyzing Doris BE with PID: $BE_PID"

# Get process information
PROC_CWD=$(readlink -f /proc/$BE_PID/cwd 2>/dev/null || lsof -p $BE_PID 2>/dev/null | grep cwd | awk '{print $NF}')
PROC_EXE=$(readlink -f /proc/$BE_PID/exe 2>/dev/null || ps -p $BE_PID -o command= | awk '{print $1}')

if [ -z "$PROC_EXE" ]; then
    error "Cannot get process information for PID $BE_PID"
    exit 1
fi

# Infer DORIS_HOME
# BE binary is usually at ${DORIS_HOME}/lib/doris_be
DORIS_BE_BIN=$(readlink -f $PROC_EXE)
DORIS_HOME=$(dirname $(dirname $DORIS_BE_BIN))

info "Detected DORIS_HOME: $DORIS_HOME"
info "BE Binary: $DORIS_BE_BIN"

# Check if jeprof exists
JEPROF="$DORIS_HOME/bin/jeprof"
if [ ! -f "$JEPROF" ]; then
    warn "jeprof not found at $JEPROF, trying system jeprof"
    JEPROF=$(which jeprof 2>/dev/null || echo "")
    if [ -z "$JEPROF" ]; then
        error "jeprof not found. Please install it or ensure it's in the BE bin directory"
        exit 1
    fi
fi

info "Using jeprof: $JEPROF"

# Get BE webserver_port from config
BE_CONF="$DORIS_HOME/conf/be.conf"
if [ -f "$BE_CONF" ]; then
    WEB_PORT=$(grep "^webserver_port" $BE_CONF | awk -F'=' '{print $2}' | tr -d ' ')
fi

# Use default port if not found
if [ -z "$WEB_PORT" ]; then
    WEB_PORT=8040
    warn "Could not find webserver_port in config, using default: $WEB_PORT"
else
    info "Detected webserver_port: $WEB_PORT"
fi

# Trigger Heap Dump via HTTP interface
DUMP_URL="http://localhost:$WEB_PORT/jeheap/dump"
info "Triggering heap dump via HTTP... $DUMP_URL"
DUMP_RESPONSE=$(curl -s "$DUMP_URL" 2>&1)

if [ $? -ne 0 ]; then
    error "Failed to trigger heap dump. Please check if BE is running and port $WEB_PORT is accessible"
    error "Response: $DUMP_RESPONSE"
    exit 1
fi

info "Heap dump triggered successfully"
info "Response: $DUMP_RESPONSE"

# Wait for heap profile file to be generated
info "Waiting for heap profile file to be generated..."
sleep 3

# Find the most recent heap profile file
HEAP_DIR="$DORIS_HOME/log"
if [ ! -d "$HEAP_DIR" ]; then
    HEAP_DIR="$DORIS_HOME/log"
fi

if [ ! -d "$HEAP_DIR" ]; then
    # Try to find log directory from be.conf
    if [ -f "$BE_CONF" ]; then
        LOG_DIR=$(grep "^sys_log_dir" $BE_CONF | awk -F'=' '{print $2}' | tr -d ' ')
        if [ -n "$LOG_DIR" ]; then
            HEAP_DIR="$LOG_DIR"
        fi
    fi
fi

info "Looking for heap profile files in: $HEAP_DIR"

# Find the most recent jemalloc heap profile file
LATEST_HEAP=$(ls -t $HEAP_DIR/jeheap_dump.* 2>/dev/null | head -1)

if [ -z "$LATEST_HEAP" ]; then
    # Try jemalloc_heap_profile_ prefix
    LATEST_HEAP=$(ls -t $HEAP_DIR/jemalloc_heap_profile_* 2>/dev/null | head -1)
fi

if [ -z "$LATEST_HEAP" ]; then
    error "No heap profile file found in $HEAP_DIR"
    error "Please check if jemalloc profiling is enabled in be.conf"
    error "Set prof:true in JEMALLOC_CONF"
    exit 1
fi

info "Found heap profile: $LATEST_HEAP"

# Create output directory
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_DIR="./heap_analysis_${TIMESTAMP}_pid${BE_PID}"
mkdir -p $OUTPUT_DIR

info "Output directory: $OUTPUT_DIR"
info ""
info "=========================================="
info "Analyzing heap profile..."
info "=========================================="

# Generate DOT file
info "Generating DOT file..."
$JEPROF --dot $DORIS_BE_BIN $LATEST_HEAP > $OUTPUT_DIR/heap.dot 2>$OUTPUT_DIR/jeprof.log

if [ $? -eq 0 ]; then
    info "✓ DOT file generated: $OUTPUT_DIR/heap.dot"
else
    warn "Failed to generate DOT file. Check $OUTPUT_DIR/jeprof.log for details"
fi

# Generate text report
info "Generating text report..."
$JEPROF --text $DORIS_BE_BIN $LATEST_HEAP > $OUTPUT_DIR/heap.txt 2>>$OUTPUT_DIR/jeprof.log

if [ $? -eq 0 ]; then
    info "✓ Text report generated: $OUTPUT_DIR/heap.txt"
    echo ""
    info "Top 20 memory allocations:"
    echo "----------------------------------------"
    head -25 $OUTPUT_DIR/heap.txt
    echo "----------------------------------------"
else
    warn "Failed to generate text report"
fi

# Generate PDF (requires graphviz)
if command -v dot > /dev/null 2>&1; then
    info "Generating PDF..."
    $JEPROF --pdf $DORIS_BE_BIN $LATEST_HEAP > $OUTPUT_DIR/heap.pdf 2>>$OUTPUT_DIR/jeprof.log
    if [ $? -eq 0 ]; then
        info "✓ PDF generated: $OUTPUT_DIR/heap.pdf"
    else
        warn "Failed to generate PDF"
    fi
else
    warn "graphviz not installed, skipping PDF generation"
    info "Install with: yum install ghostscript graphviz (CentOS/RedHat)"
    info "           or: apt install ghostscript graphviz (Ubuntu/Debian)"
fi

# Generate SVG
if command -v dot > /dev/null 2>&1; then
    info "Generating SVG..."
    $JEPROF --svg $DORIS_BE_BIN $LATEST_HEAP > $OUTPUT_DIR/heap.svg 2>>$OUTPUT_DIR/jeprof.log
    if [ $? -eq 0 ]; then
        info "✓ SVG generated: $OUTPUT_DIR/heap.svg"
    fi
fi

# Copy heap profile to output directory
cp $LATEST_HEAP $OUTPUT_DIR/

info ""
info "=========================================="
info "Analysis complete!"
info "=========================================="
info "Results saved to: $OUTPUT_DIR"
info ""
info "Files generated:"
info "  - heap.txt  : Text report of memory allocations"
info "  - heap.dot  : DOT format call graph"
if [ -f "$OUTPUT_DIR/heap.pdf" ]; then
    info "  - heap.pdf  : PDF call graph"
fi
if [ -f "$OUTPUT_DIR/heap.svg" ]; then
    info "  - heap.svg  : SVG call graph"
fi
info ""
info "How to view results:"
info "  1. Text report:"
info "     cat $OUTPUT_DIR/heap.txt"
info ""
info "  2. Online DOT viewer:"
info "     Open http://www.webgraphviz.com/"
info "     Paste content of $OUTPUT_DIR/heap.dot"
info ""
if [ -f "$OUTPUT_DIR/heap.pdf" ]; then
    info "  3. PDF viewer:"
    info "     open $OUTPUT_DIR/heap.pdf  (macOS)"
    info "     xdg-open $OUTPUT_DIR/heap.pdf  (Linux)"
    info ""
fi
if [ -f "$OUTPUT_DIR/heap.svg" ]; then
    info "  4. SVG viewer:"
    info "     open $OUTPUT_DIR/heap.svg  (macOS)"
    info "     xdg-open $OUTPUT_DIR/heap.svg  (Linux)"
    info ""
fi

info "For more details, check the debug tool documentation"
