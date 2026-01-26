# Doris BE Memory Analyzer

A simple and easy-to-use shell script for analyzing Apache Doris BE memory usage using jemalloc heap profiling.

## Prerequisites

1. **Jemalloc profiling must be enabled** in `be.conf`:
   ```
   JEMALLOC_CONF=prof:true,prof_active:true,...
   ```
   For Doris 2.1.8 and 3.0.4+, `prof` is already `true` by default.

2. **jeprof tool** should be available at `${DORIS_HOME}/be/bin/jeprof` or in system PATH

3. **addr2line version 2.35.2+** is required (for proper symbol resolution)

4. **Optional: graphviz** for generating PDF/SVG visualizations:
   ```bash
   # CentOS/RedHat
   yum install ghostscript graphviz

   # Ubuntu/Debian
   apt install ghostscript graphviz
   ```

## Usage

```bash
sh mem-analyzer.sh <be_pid>
```

### Example

```bash
# Find BE process ID
ps aux | grep doris_be

# Run analyzer
sh mem-analyzer.sh 12345
```

## What it does

The script will automatically:

1. Detect Doris BE installation directory from the process
2. Read BE configuration to get webserver port
3. Trigger a heap dump via HTTP API (`/jeheap/dump`)
4. Find the latest heap profile file
5. Generate analysis results in multiple formats:
   - **heap.txt** - Text report showing memory allocations
   - **heap.dot** - DOT format call graph
   - **heap.pdf** - PDF visualization (if graphviz is installed)
   - **heap.svg** - SVG visualization (if graphviz is installed)

## Output

Results are saved in a timestamped directory:
```
heap_analysis_YYYYMMDD_HHMMSS_pid<PID>/
├── heap.txt          # Text report
├── heap.dot          # DOT graph
├── heap.pdf          # PDF visualization
├── heap.svg          # SVG visualization
├── jeprof.log        # jeprof execution log
└── jeheap_dump.*     # Original heap profile
```

## Viewing Results

### 1. Text Report
```bash
cat heap_analysis_*/heap.txt
```

The text report shows memory allocations sorted by size:
```
Total: 668.6 MB
   610.6  91.3%  91.3%    610.6  91.3% doris::SystemAllocator::allocate_via_malloc
    18.1   2.7%  94.0%     18.1   2.7% _objalloc_alloc
     5.6   0.8%  94.9%     63.4   9.5% doris::RowBatch::RowBatch
```

### 2. DOT Graph (Online Viewer)
1. Open http://www.webgraphviz.com/
2. Copy and paste content of `heap.dot`
3. View the interactive call graph

### 3. PDF/SVG (Local Viewer)
```bash
# macOS
open heap_analysis_*/heap.pdf

# Linux
xdg-open heap_analysis_*/heap.pdf
```

## Troubleshooting

### Error: "jeprof not found"
- Make sure jeprof is available in `${DORIS_HOME}/be/bin/jeprof`
- Or install jeprof system-wide

### Error: "No heap profile file found"
- Check if jemalloc profiling is enabled in `be.conf`
- Set `prof:true` and `prof_active:true` in `JEMALLOC_CONF`
- Restart BE after changing configuration

### Error: "addr2line: Dwarf Error"
- Update addr2line to version 2.35.2 or higher
- See [debug-tool.md](../debug-tool.md) for installation instructions

### Heap stack shows memory addresses instead of function names
- Run the script on the same machine where BE is running
- Make sure the BE binary has debug symbols
- See the manual parsing script in [debug-tool.md](../debug-tool.md) section 3

## Advanced Usage

### Compare Two Heap Profiles

To analyze memory growth between two time points:

```bash
# Take first snapshot
sh mem-analyzer.sh <be_pid>

# Wait some time...
sleep 600

# Take second snapshot
sh mem-analyzer.sh <be_pid>

# Compare the two heap files manually
jeprof --dot ${DORIS_HOME}/lib/doris_be \
  --base=heap_analysis_1/jeheap_dump.* \
  heap_analysis_2/jeheap_dump.* > diff.dot
```

### Regular Heap Profiling

For long-term memory observation, you can enable automatic heap dumps in `be.conf`:

```bash
# Dump when cumulative memory reaches 16GB intervals
JEMALLOC_CONF=prof:true,lg_prof_interval:34,...

# Dump when memory reaches new high
JEMALLOC_CONF=prof:true,prof_gdump:true,...
```

## Reference

Based on Apache Doris documentation:
- [Debug Tool - Jemalloc HEAP PROFILE](../debug-tool.md#jemalloc-heap-profile)
- [Debug Tool - jeprof 解析 Heap Profile](../debug-tool.md#jeprof-解析-heap-profile)

## License

Apache License 2.0
