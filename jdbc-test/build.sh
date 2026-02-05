#!/bin/bash

# Check Java version
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Error: Java 17 or higher is required"
    echo "Current version: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

echo "========================================="
echo "Building Doris JDBC Connection Project"
echo "========================================="
echo "Using Java: $(java -version 2>&1 | head -n 1)"
echo "========================================="

# Clean previous builds
echo ""
echo "[1/3] Cleaning previous builds..."
mvn clean

if [ $? -ne 0 ]; then
    echo "Error: Maven clean failed"
    exit 1
fi

# Compile and package
echo ""
echo "[2/3] Compiling and packaging..."
mvn package

if [ $? -ne 0 ]; then
    echo "Error: Maven package failed"
    exit 1
fi

# Make run.sh executable
echo ""
echo "[3/4] Making run.sh executable..."
chmod +x run.sh

# Create output directory with complete runnable environment
echo ""
echo "[4/4] Creating output directory..."
rm -rf output
mkdir -p output

# Copy JAR file
echo "  - Copying JAR file..."
cp target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar output/doris-jdbc.jar

# Copy and create run script for output directory
echo "  - Copying and adapting run script..."
cp run.sh output/run.sh

# Adjust paths for the output directory
sed -i 's|target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar|doris-jdbc.jar|g' output/run.sh
sed -i 's|src/main/resources/connection.properties|connection.properties|g' output/run.sh

chmod +x output/run.sh

# Copy configuration file
echo "  - Copying configuration file..."
cp src/main/resources/connection.properties output/connection.properties

# Copy README
echo "  - Copying README..."
cp README.md output/README.md

echo ""
echo "========================================="
echo "Build completed successfully!"
echo "========================================="
echo ""
echo "Source files:"
echo "  - target/doris-jdbc-1.0-SNAPSHOT.jar"
echo "  - target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar"
echo ""
echo "Portable package created in: output/"
echo "  - output/doris-jdbc.jar"
echo "  - output/connection.properties"
echo "  - output/run.sh"
echo "  - output/README.md"
echo ""
echo "You can now:"
echo "  1. Edit output/connection.properties with your settings"
echo "  2. Run: cd output && ./run.sh basic"
echo "  3. Or copy the entire output/ directory to another machine"
echo ""
