#!/bin/bash
## @brief  Simple perf + FlameGraph wrapper
## @author zhoufei
## @email  gavineaglechou@gmail.com
## @date   2018-04-01-Fri

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

usage() {
    echo "Usage: sudo $0 [pid] [java]"
    echo ""
    echo "  pid   : Target process PID; if not specified, sample entire system (-a)"
    echo "  java  : Optional, pass \"java\" to generate Java symbol maps (using FlameGraph/jmaps)"
    echo ""
    echo "Example:"
    echo "  sudo $0                # Sample entire system"
    echo "  sudo $0 12345          # Sample process with pid=12345"
    echo "  sudo $0 12345 java     # Sample Java process and generate Java flame graph"
}

# ---------------------------------------------------------------
# Permission & environment checks
# ---------------------------------------------------------------
if [ "$(whoami)" != "root" ]; then
    error "This script must be run as root (sudo)."
    echo ""
    usage
    exit 1
fi

if ! command -v perf >/dev/null 2>&1; then
    error "perf command not found. Please install perf first."
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

if [ ! -d "${SCRIPT_DIR}/FlameGraph" ]; then
    warn "FlameGraph directory not found at ${SCRIPT_DIR}/FlameGraph"
    warn "Please ensure FlameGraph is cloned under this directory."
fi

# ATTN: Uncomment to choose the one that suits you
# s3=https://justtmp-bj-xxxx.cos.ap-beijing.myqcloud.com

# for java flame, change in need
export JAVA_HOME=/opt/java/jdk1.8.0_131
export AGENT_HOME="${SCRIPT_DIR}/perf-map-agent"

info "Working directory: ${SCRIPT_DIR}"
info "Start time: $(date '+%Y-%m-%d %H:%M:%S')"

# ---------------------------------------------------------------
# Parameter parsing
# ---------------------------------------------------------------
perf_time=10
pid=$1
java_flag=$2

info "Sampling duration: ${perf_time}s"

# ---------------------------------------------------------------
# Sampling phase
# ---------------------------------------------------------------
rm -f perf.data

if [ -z "${pid}" ]; then
    info "No PID specified, sampling entire system (-a)."
    info "Running: perf record -F 99 -a -g -o perf.data -- sleep ${perf_time}"
    perf record -F 99 -a -g -o perf.data -- sleep "${perf_time}"
else
    info "Sampling process with PID=${pid}."
    info "Running: perf record -F 99 -a -g -p ${pid} -o perf.data -- sleep ${perf_time}"
    perf record -F 99 -a -g -p "${pid}" -o perf.data -- sleep "${perf_time}"
fi

if [ "x${java_flag}" = "xjava" ]; then
    info "Java mode enabled, generating Java symbol maps with FlameGraph/jmaps."
    if [ -x "./FlameGraph/jmaps" ]; then
        ./FlameGraph/jmaps
    else
        warn "FlameGraph/jmaps not found or not executable, skip Java maps generation."
    fi
fi

# ---------------------------------------------------------------
# Generate Flame Graph
# ---------------------------------------------------------------
svg_name="$(hostname)-$(date +'%Y%m%d%H%M%S').svg"

info "Generating flame graph SVG: ${svg_name}"

# perf script | ./FlameGraph/stackcollapse-perf.pl > out.perf-folded
# ./FlameGraph/flamegraph.pl out.perf-folded > ${svg_name}
perf script | ./FlameGraph/stackcollapse-perf.pl | ./FlameGraph/flamegraph.pl > "${svg_name}"

if [ -f "${svg_name}" ]; then
    info "✓ Flame graph generated: ${svg_name}"
else
    error "Failed to generate flame graph SVG."
    exit 1
fi

# ---------------------------------------------------------------
# Optional: Upload to object storage
# ---------------------------------------------------------------
if [[ -z "${s3}" ]]; then
    warn "s3 endpoint not configured, skip upload.(try setting 's3' endpoint in perf.sh)"
else
    upload_url="${s3}/flame-graph/${svg_name}"
    info "Uploading SVG to: ${upload_url}"
    cmd="curl -H 'content-type:application/xml' -T \"${svg_name}\" \"${upload_url}\""
    echo "${cmd}"
    if eval "${cmd}"; then
        info "✓ Upload success."
        info "You can download it via:"
        echo "  wget ${upload_url}"
    else
        warn "Upload failed. Please check network or s3 configuration."
    fi
fi

# ---------------------------------------------------------------
# Final result summary
# ---------------------------------------------------------------
echo ""
info "=========================================="
info "Flame graph generation complete!"
info "=========================================="
info "Local SVG file : ${svg_name}"
if [[ -n "${s3}" ]]; then
    info "Remote URL     : ${s3}/flame-graph/${svg_name}"
fi
echo ""
info "How to view:"
info "  1. On macOS: open ${svg_name}"
info "  2. On Linux: xdg-open ${svg_name}  (or open directly in browser)"
echo ""
