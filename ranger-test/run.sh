#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Default values
SERVICE_NAME="doris"
TEST_USER="admin"
CONFIG_FILE=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --config)
      CONFIG_FILE="$2"
      shift 2
      ;;
    --service-name)
      SERVICE_NAME="$2"
      shift 2
      ;;
    --test-user)
      TEST_USER="$2"
      shift 2
      ;;
    --help|-h)
      echo "Usage: $0 --config <config-file> [OPTIONS]"
      echo ""
      echo "Required:"
      echo "  --config <file>           Path to ranger-doris-security.xml"
      echo ""
      echo "Options:"
      echo "  --service-name <name>     Ranger service name (default: doris)"
      echo "  --test-user <user>        Test user name (default: test_user)"
      echo "  --help, -h                Show this help message"
      echo ""
      echo "Example:"
      echo "  $0 --config config/ranger-doris-security.xml --service-name doris --test-user admin"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Check if config file is specified
if [ -z "$CONFIG_FILE" ]; then
  echo "Error: --config parameter is required"
  echo ""
  echo "Usage: $0 --config <config-file> [OPTIONS]"
  echo ""
  echo "Example:"
  echo "  $0 --config config/ranger-doris-security.xml --service-name doris --test-user admin"
  exit 1
fi

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: Config file not found: $CONFIG_FILE"
  exit 1
fi

# Check if JAR exists
JAR_FILE="target/doris-ranger-test-1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found: $JAR_FILE"
  echo "Please run ./build.sh first"
  exit 1
fi

# Convert relative path to absolute path
CONFIG_FILE="$(cd "$(dirname "$CONFIG_FILE")" && pwd)/$(basename "$CONFIG_FILE")"

# Run the test program
echo "Running Doris Ranger Test..."
echo "Config File: $CONFIG_FILE"
echo ""

java -Dranger.config.file="$CONFIG_FILE" \
     -cp "config/:$JAR_FILE" \
     org.apache.doris.ranger.test.DorisRangerTester \
     --service-name "$SERVICE_NAME" \
     --test-user "$TEST_USER"
