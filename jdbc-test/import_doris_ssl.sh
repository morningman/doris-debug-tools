#!/bin/bash

# Doris SSL 证书导入脚本

CA_CERT="/mnt/disk1/yy/git/selectdb-core/output/remote-debug/mysql_ssl_default_certificate/client_certificate/ca.pem"
TRUSTSTORE_PATH="./doris-truststore.jks"
TRUSTSTORE_PASSWORD="doris123"

echo "正在创建 Java 信任库..."

# 删除旧的信任库（如果存在）
rm -f "$TRUSTSTORE_PATH"

# 导入 CA 证书
keytool -importcert \
  -alias doris-ca \
  -file "$CA_CERT" \
  -keystore "$TRUSTSTORE_PATH" \
  -storepass "$TRUSTSTORE_PASSWORD" \
  -noprompt

if [ $? -eq 0 ]; then
    echo "✓ 证书导入成功！"
    echo ""
    echo "使用方法："
    echo "----------------------------------------"
    echo "方法1 - JDBC URL 参数："
    echo "jdbc:mysql://host:port/db?useSSL=true&verifyServerCertificate=true&trustCertificateKeyStoreUrl=file:$(pwd)/${TRUSTSTORE_PATH}&trustCertificateKeyStorePassword=${TRUSTSTORE_PASSWORD}"
    echo ""
    echo "方法2 - JVM 启动参数："
    echo "java -Djavax.net.ssl.trustStore=$(pwd)/${TRUSTSTORE_PATH} -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD} YourApp"
    echo "----------------------------------------"

    # 验证导入
    echo ""
    echo "信任库内容："
    keytool -list -keystore "$TRUSTSTORE_PATH" -storepass "$TRUSTSTORE_PASSWORD"
else
    echo "✗ 证书导入失败"
    exit 1
fi
