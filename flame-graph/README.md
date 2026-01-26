# Flame Graph Generator

A simple script to generate CPU flame graphs using Linux `perf` and FlameGraph tools.

## Usage

```bash
# Profile all processes
sudo sh perf.sh

# Profile a specific process
sudo sh perf.sh <pid>

# Profile a Java process with symbol resolution
sudo sh perf.sh <pid> java
```

## Output

Generates an SVG flame graph named `<hostname>-<timestamp>.svg` in the current directory.

## S3 Upload (Optional)

To enable automatic upload to S3-compatible storage, configure the `s3` variable in the script:

```bash
s3=https://your-bucket.cos.region.myqcloud.com
```

Set `s3=""` to disable upload feature.
