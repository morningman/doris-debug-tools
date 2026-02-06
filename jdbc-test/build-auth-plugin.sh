#!/bin/bash

# Script to build a standalone JAR containing only the ClearPasswordPlugin class
# This JAR can be placed in the classpath of any JDBC client

set -e

echo "Building ClearPasswordPlugin standalone JAR..."

# Get the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# Create output directory
OUTPUT_DIR="$PROJECT_DIR/output/auth-plugin"
mkdir -p "$OUTPUT_DIR"

# Clean previous build
rm -rf "$OUTPUT_DIR/classes"
mkdir -p "$OUTPUT_DIR/classes"

# Get MySQL Connector dependency from Maven local repository
# First ensure dependencies are downloaded
echo "Ensuring Maven dependencies are available..."
mvn dependency:resolve -q

# Get the MySQL Connector JAR path
MYSQL_CONNECTOR_JAR=$(mvn dependency:build-classpath | grep -v "^\[" | tr ':' '\n' | grep mysql-connector-j)

if [ -z "$MYSQL_CONNECTOR_JAR" ]; then
    echo "Error: Could not find mysql-connector-j in Maven dependencies"
    exit 1
fi

echo "Using MySQL Connector: $MYSQL_CONNECTOR_JAR"

# Compile the ClearPasswordPlugin class
echo "Compiling ClearPasswordPlugin..."
javac -cp "$MYSQL_CONNECTOR_JAR" \
    -d "$OUTPUT_DIR/classes" \
    -encoding UTF-8 \
    src/main/java/com/doris/jdbc/auth/ClearPasswordPlugin.java

# Create META-INF/services directory for SPI
mkdir -p "$OUTPUT_DIR/classes/META-INF/services"

# Create the SPI service file
cat > "$OUTPUT_DIR/classes/META-INF/services/com.mysql.cj.protocol.AuthenticationPlugin" <<EOF
com.doris.jdbc.auth.ClearPasswordPlugin
EOF

# Create the JAR file
JAR_NAME="doris-clear-password-plugin.jar"
echo "Creating JAR: $JAR_NAME"
cd "$OUTPUT_DIR/classes"
jar cf "../$JAR_NAME" .
cd "$PROJECT_DIR"

echo ""
echo "============================================"
echo "Build successful!"
echo "============================================"
echo "Output: $OUTPUT_DIR/$JAR_NAME"
echo ""
echo "To use this plugin, add the JAR to your classpath and use:"
echo "  jdbc:mysql://host:port/db?authenticationPlugins=com.doris.jdbc.auth.ClearPasswordPlugin"
echo "    &defaultAuthenticationPlugin=com.doris.jdbc.auth.ClearPasswordPlugin"
echo "    &disabledAuthenticationPlugins=com.mysql.cj.protocol.a.authentication.MysqlClearPasswordPlugin"
echo ""
echo "File size: $(du -h "$OUTPUT_DIR/$JAR_NAME" | cut -f1)"
echo "============================================"
