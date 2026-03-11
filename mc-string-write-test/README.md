# MaxCompute Large String Write Test

A standalone Java tool for testing the write behavior of large `STRING` columns in [Alibaba Cloud MaxCompute](https://www.alibabacloud.com/product/maxcompute). It creates a MaxCompute table, generates rows with large random string values, and writes them via the **MaxCompute Storage API** (Arrow-based batch write).

This is useful for:

- Benchmarking MaxCompute write throughput with large string payloads.
- Reproducing and diagnosing issues related to oversized `STRING` columns.
- Validating MaxCompute SDK & Arrow integration in custom data pipelines.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Build](#build)
- [Usage](#usage)
  - [Option 1 — Environment Variables](#option-1--environment-variables)
  - [Option 2 — Command-Line Arguments](#option-2--command-line-arguments)
  - [Option 3 — Quick Run with Script](#option-3--quick-run-with-script)
- [Parameters](#parameters)
- [Examples](#examples)
- [How It Works](#how-it-works)

## Prerequisites

| Requirement | Version |
|---|---|
| **JDK** | 8 or later |
| **Maven** | 3.x |
| **Alibaba Cloud Account** | AccessKey ID & Secret with MaxCompute permissions |

## Build

Use the provided build script:

```bash
# Normal build
./build.sh

# Clean rebuild
./build.sh clean
```

Or build with Maven directly:

```bash
mvn package -DskipTests
```

The shaded (fat) JAR is produced at:

```
target/mc-string-write-test-1.0-SNAPSHOT.jar
```

## Usage

### Option 1 — Environment Variables

Set your MaxCompute credentials as environment variables, then run `./run.sh`.
The script will automatically build the project if the JAR does not exist.

```bash
export MC_ENDPOINT="http://service.cn-hangzhou.maxcompute.aliyun.com/api"
export MC_PROJECT="my_project"
export MC_ACCESS_KEY="your_access_key_id"
export MC_SECRET_KEY="your_access_key_secret"

# Optional overrides (shown with defaults)
export MC_TABLE="mc_large_string_test"
export MC_ROWS=10
export MC_STRING_SIZE=1048576      # 1 MB per row
export MC_BATCH_ROWS=1
export MC_QUOTA="pay-as-you-go"
export SKIP_CREATE=false            # set to "true" to skip table creation

./run.sh
```

### Option 2 — Command-Line Arguments

Pass all parameters directly:

```bash
./run.sh \
  --endpoint  http://service.cn-hangzhou.maxcompute.aliyun.com/api \
  --project   my_project \
  --accessKey your_access_key_id \
  --secretKey your_access_key_secret \
  --table     mc_large_string_test \
  --rows      10 \
  --stringSize 1048576 \
  --batchRows 1 \
  --quota     pay-as-you-go
```

### Option 3 — Quick Run with Script

Edit `run-with-env.sh` to fill in your credentials and desired settings, then execute it:

```bash
# 1. Edit run-with-env.sh and set MC_ACCESS_KEY / MC_SECRET_KEY
# 2. Run
./run-with-env.sh
```

### Running the JAR Directly

You can also invoke the JAR manually. Note the `--add-opens` JVM flag is required for Arrow memory access on JDK 9+:

```bash
java --add-opens=java.base/java.nio=ALL-UNNAMED \
  -jar target/mc-string-write-test-1.0-SNAPSHOT.jar \
  --endpoint  <endpoint> \
  --project   <project> \
  --accessKey <ak> \
  --secretKey <sk>
```

## Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `--endpoint` | **Yes** | — | MaxCompute service endpoint URL |
| `--project` | **Yes** | — | MaxCompute project name |
| `--accessKey` | **Yes** | — | Alibaba Cloud AccessKey ID |
| `--secretKey` | **Yes** | — | Alibaba Cloud AccessKey Secret |
| `--table` | No | `mc_large_string_test` | Target table name |
| `--rows` | No | `10` | Total number of rows to write |
| `--stringSize` | No | `1048576` (1 MB) | Size in bytes of each generated string value |
| `--batchRows` | No | `1` | Number of rows per Arrow write batch |
| `--quota` | No | `pay-as-you-go` | MaxCompute quota name |
| `--skipCreate` | No | `false` | If set, skip table creation (table must already exist) |

## Examples

**Write 5 rows, each with a 2 MB string:**

```bash
export MC_ENDPOINT="http://service.cn-hangzhou.maxcompute.aliyun.com/api"
export MC_PROJECT="my_project"
export MC_ACCESS_KEY="LTAI..."
export MC_SECRET_KEY="xyz..."

export MC_ROWS=5
export MC_STRING_SIZE=2097152   # 2 MB

./run.sh
```

**Write to an existing table (skip create), batch 5 rows per write:**

```bash
./run.sh \
  --endpoint  http://service.cn-hangzhou.maxcompute.aliyun.com/api \
  --project   my_project \
  --accessKey LTAI... \
  --secretKey xyz... \
  --table     my_existing_table \
  --rows      100 \
  --stringSize 524288 \
  --batchRows 5 \
  --skipCreate
```

## How It Works

1. **Create Table** — Unless `--skipCreate` is set, the program drops the table if it already exists and recreates it with the schema:

   | Column | Type | Description |
   |---|---|---|
   | `id` | `BIGINT` | Row ID (0-indexed) |
   | `content` | `STRING` | Large generated string |

2. **Open Write Session** — A `TableBatchWriteSession` is created using the MaxCompute Storage API with Arrow serialization.

3. **Generate & Write Data** — For each batch:
   - An Arrow `VectorSchemaRoot` is allocated.
   - The `id` column is populated with sequential integers.
   - The `content` column is filled with randomly generated printable ASCII text of the configured byte size, prefixed with a row identifier (`[ROW-000042]`).
   - The batch is written via `BatchWriter.write()`.

4. **Commit** — The writer and session are committed to finalize the data.
