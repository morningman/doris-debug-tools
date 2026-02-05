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

# Check if mode argument is provided
if [ $# -eq 0 ]; then
    echo "Usage: ./run.sh <mode>"
    echo ""
    echo "Available modes:"
    echo "  basic    - Basic authentication (username/password)"
    echo "  ssl      - SSL-enabled connection"
    echo "  ldap     - LDAP authentication with custom plugin"
    echo "  ldapssl  - LDAP authentication with SSL encryption"
    echo ""
    echo "Examples:"
    echo "  ./run.sh basic"
    echo "  ./run.sh ssl"
    echo "  ./run.sh ldap"
    echo "  ./run.sh ldapssl"
    echo ""
    echo "Configuration:"
    echo "  Edit connection parameters in src/main/resources/connection.properties"
    exit 1
fi

MODE=$1

# Validate mode
if [ "$MODE" != "basic" ] && [ "$MODE" != "ssl" ] && [ "$MODE" != "ldap" ] && [ "$MODE" != "ldapssl" ]; then
    echo "Error: Invalid mode '$MODE'"
    echo "Valid modes are: basic, ssl, ldap, ldapssl"
    exit 1
fi

# Check if JAR file exists
JAR_FILE="target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please run ./build.sh first"
    exit 1
fi

# SSL mode - interactive certificate setup
if [ "$MODE" == "ssl" ] || [ "$MODE" == "ldapssl" ]; then
    echo "=========================================="
    echo "SSL Connection Setup"
    echo "=========================================="
    echo ""

    # Ask if user wants to verify server certificate
    echo "Do you want to verify the server certificate?"
    echo ""
    echo "  Yes - Requires CA root certificate (more secure)"
    echo "  No  - Skip certificate verification (less secure, for testing)"
    echo ""
    read -p "Verify server certificate? [Y/n]: " VERIFY_CERT
    echo ""

    # Default to yes if empty
    if [ -z "$VERIFY_CERT" ]; then
        VERIFY_CERT="y"
    fi

    # If user chooses not to verify, skip certificate setup
    if [[ ! "$VERIFY_CERT" =~ ^[Yy]$ ]]; then
        echo "Skipping certificate verification (verifyServerCertificate=false)"
        echo ""
        echo "Starting Doris JDBC Connection in $MODE mode..."
        echo ""
        java -Ddoris.ssl.skipVerify=true -jar "$JAR_FILE" "$MODE"
        exit $?
    fi

    # Continue with certificate verification setup
    echo "Certificate verification enabled (verifyServerCertificate=true)"
    echo ""

    # Default certificate path (Doris default location)
    DEFAULT_CA_CERT="./client_certificate/ca.pem"

    # Ask for CA certificate path
    echo "Please specify the path to the CA root certificate (ca.pem):"
    echo ""
    if [ -f "$DEFAULT_CA_CERT" ]; then
        echo "Default: $DEFAULT_CA_CERT"
        echo ""
        read -p "Press Enter to use default, or enter custom path: " CA_CERT_INPUT

        if [ -z "$CA_CERT_INPUT" ]; then
            CA_CERT="$DEFAULT_CA_CERT"
        else
            CA_CERT="$CA_CERT_INPUT"
        fi
    else
        read -p "CA certificate path: " CA_CERT
    fi

    # Validate certificate file exists
    if [ ! -f "$CA_CERT" ]; then
        echo ""
        echo "Error: Certificate file not found: $CA_CERT"
        exit 1
    fi

    echo ""
    echo "Using CA certificate: $CA_CERT"
    echo ""

    # Verify certificate is valid
    echo "[1/3] Verifying certificate..."
    if ! openssl x509 -in "$CA_CERT" -text -noout > /dev/null 2>&1; then
        echo "Error: Invalid certificate file"
        exit 1
    fi

    # Display certificate info
    echo ""
    echo "Certificate Information:"
    echo "----------------------------------------"
    openssl x509 -in "$CA_CERT" -subject -issuer -dates -noout
    echo "----------------------------------------"
    echo ""

    # Setup truststore
    TRUSTSTORE_PATH="./ssl-truststore.jks"
    TRUSTSTORE_PASSWORD="doris-ssl-$(date +%s)"

    echo "[2/3] Creating Java truststore..."

    # Remove old truststore if exists
    rm -f "$TRUSTSTORE_PATH"

    # Import CA certificate
    keytool -importcert \
        -alias doris-ca \
        -file "$CA_CERT" \
        -keystore "$TRUSTSTORE_PATH" \
        -storepass "$TRUSTSTORE_PASSWORD" \
        -noprompt > /dev/null 2>&1

    if [ $? -ne 0 ]; then
        echo "Error: Failed to import certificate to truststore"
        exit 1
    fi

    echo "   Truststore created: $TRUSTSTORE_PATH"
    echo ""

    # Run with SSL configuration
    echo "[3/3] Starting Doris JDBC Connection in $MODE mode..."
    echo ""

    java -Djavax.net.ssl.trustStore="$(pwd)/$TRUSTSTORE_PATH" \
         -Djavax.net.ssl.trustStorePassword="$TRUSTSTORE_PASSWORD" \
         -jar "$JAR_FILE" "$MODE"

    # Cleanup
    SSL_EXIT_CODE=$?
    echo ""
    read -p "Keep truststore file for reuse? [y/N]: " KEEP_TRUSTSTORE

    if [[ ! "$KEEP_TRUSTSTORE" =~ ^[Yy]$ ]]; then
        rm -f "$TRUSTSTORE_PATH"
        echo "Truststore cleaned up"
    else
        echo "Truststore saved: $(pwd)/$TRUSTSTORE_PATH"
        echo "Password: $TRUSTSTORE_PASSWORD"
        echo ""
        echo "To reuse this truststore, run:"
        echo "java -Djavax.net.ssl.trustStore=$(pwd)/$TRUSTSTORE_PATH \\"
        echo "     -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD \\"
        echo "     -jar $JAR_FILE $MODE"
    fi

    exit $SSL_EXIT_CODE
else
    # Run the application in basic or ldap mode
    echo "Starting Doris JDBC Connection in $MODE mode..."
    echo ""
    java -jar "$JAR_FILE" "$MODE"
fi
