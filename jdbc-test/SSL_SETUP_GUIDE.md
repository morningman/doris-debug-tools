# SSL 连接设置指南

本指南说明如何使用 SSL 模式连接 Doris 数据库。

## 快速开始

### 1. 使用默认证书（Doris 开发环境）

如果您的 Doris 服务器使用默认的自签名证书，只需：

```bash
./run.sh ssl
```

脚本会自动检测默认证书位置并提示：

```
==========================================
SSL Connection Setup
==========================================

Please specify the path to the CA root certificate (ca.pem):

Default: /mnt/disk1/yy/git/selectdb-core/output/remote-debug/mysql_ssl_default_certificate/client_certificate/ca.pem

Press Enter to use default, or enter custom path:
```

**直接按 Enter 键** 即可使用默认证书。

### 2. 使用自定义证书

如果您有自己的 CA 证书，在提示时输入证书路径：

```bash
./run.sh ssl
# 在提示时输入: /path/to/your/ca.pem
```

## 工作流程

脚本会自动执行以下步骤：

### 步骤 1：验证证书

```
[1/3] Verifying certificate...

Certificate Information:
----------------------------------------
subject=C = CN, ST = Beijing, L = Beijing, O = Doris, OU = Doris, CN = Doris, emailAddress = dev@doris.apache.org
issuer=C = CN, ST = Beijing, L = Beijing, O = Doris, OU = Doris, CN = Doris, emailAddress = dev@doris.apache.org
notBefore=Apr 10 10:42:24 2023 GMT
notAfter=Feb 16 10:42:24 2033 GMT
----------------------------------------
```

### 步骤 2：创建 Java 信任库

```
[2/3] Creating Java truststore...
   Truststore created: ./ssl-truststore.jks
```

脚本会自动将 CA 证书导入到 Java truststore，并设置随机密码。

### 步骤 3：执行连接测试

```
[3/3] Starting Doris JDBC Connection in SSL mode...
```

使用配置好的 truststore 连接 Doris，并执行以下测试：
1. 创建数据库
2. 切换到数据库
3. 列出所有数据库
4. 创建测试表
5. 插入测试数据
6. 查询测试数据

## 配置文件

在 `src/main/resources/connection.properties` 中配置 SSL 连接参数：

```properties
# ========================================
# SSL Connection Settings
# ========================================
doris.ssl.host=localhost        # Doris 服务器地址
doris.ssl.port=9030            # Doris MySQL 协议端口
doris.ssl.database=test_db     # 测试数据库名
doris.ssl.user=root            # 用户名
doris.ssl.password=            # 密码（可选）
```

## 保存 Truststore 以便重用

测试完成后，脚本会询问是否保留 truststore：

```
Keep truststore file for reuse? [y/N]:
```

### 选择 Yes（y）

Truststore 文件将被保存，您会看到：

```
Truststore saved: /mnt/disk1/yy/tools/ldap/ssl-truststore.jks
Password: doris-ssl-1234567890

To reuse this truststore, run:
java -Djavax.net.ssl.trustStore=/mnt/disk1/yy/tools/ldap/ssl-truststore.jks \
     -Djavax.net.ssl.trustStorePassword=doris-ssl-1234567890 \
     -jar target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar ssl
```

您可以复制此命令，在不重新导入证书的情况下直接运行测试。

### 选择 No（n）或直接回车

Truststore 文件会被自动清理，下次运行时重新创建。

## 故障排除

### 问题 1：证书验证失败

**错误信息：**
```
Caused by: java.security.cert.CertPathValidatorException: Path does not chain with any of the trust anchors
```

**原因：**
- 指定的 CA 证书不正确
- 服务器使用的证书与提供的 CA 不匹配

**解决方案：**
1. 确认服务器使用的证书
2. 获取正确的 CA 根证书
3. 重新运行 `./run.sh ssl` 并指定正确的证书路径

### 问题 2：证书文件未找到

**错误信息：**
```
Error: Certificate file not found: /path/to/ca.pem
```

**解决方案：**
检查证书路径是否正确，使用绝对路径或相对于当前目录的路径。

### 问题 3：证书已过期

**错误信息：**
```
notAfter=Apr 10 10:42:24 2023 GMT  (已过期)
```

**解决方案：**
联系管理员重新生成证书，或在测试环境中使用 `verifyServerCertificate=false`（不推荐）。

### 问题 4：无法连接到服务器

**可能原因：**
1. 服务器地址或端口配置错误
2. 服务器未启用 SSL
3. 防火墙阻止连接

**解决方案：**
1. 检查 `connection.properties` 中的配置
2. 验证服务器 SSL 配置：
   ```bash
   openssl s_client -connect <host>:<port> -showcerts
   ```

## 获取服务器证书

如果您需要从远程 Doris 服务器获取 CA 证书：

```bash
# 连接到服务器并获取证书链
openssl s_client -connect <doris-host>:<doris-port> -showcerts < /dev/null 2>/dev/null | \
  openssl x509 -outform PEM > doris-ca.pem

# 然后使用此证书
./run.sh ssl
# 输入路径: ./doris-ca.pem
```

## 生产环境注意事项

⚠️ **重要提示**

Doris 默认证书（位于 `mysql_ssl_default_certificate` 目录）**仅用于开发和测试**。

在生产环境中，您应该：

1. **使用正规 CA 签发的证书**（如 Let's Encrypt、DigiCert）
2. **启用双向认证**（客户端证书验证）
3. **定期更新证书**
4. **使用强密码保护 truststore**
5. **将 truststore 安全存储**，不要提交到版本控制系统

## 高级用法

### 手动创建 Truststore

如果您想手动管理 truststore：

```bash
# 创建 truststore
keytool -importcert \
  -alias doris-ca \
  -file /path/to/ca.pem \
  -keystore my-truststore.jks \
  -storepass mypassword \
  -noprompt

# 使用 truststore 运行
java -Djavax.net.ssl.trustStore=./my-truststore.jks \
     -Djavax.net.ssl.trustStorePassword=mypassword \
     -jar target/doris-jdbc-1.0-SNAPSHOT-jar-with-dependencies.jar ssl
```

### 在 JDBC URL 中指定 Truststore

修改 `ConnectionFactory.java` 中的 `createSSLConnection` 方法：

```java
public static Connection createSSLConnection(String host, int port, String database,
                                            String user, String password) throws SQLException {
    String url = String.format(
        "jdbc:mysql://%s:%d/%s?useSSL=true&requireSSL=true&verifyServerCertificate=true&" +
        "trustCertificateKeyStoreUrl=file:/path/to/truststore.jks&" +
        "trustCertificateKeyStorePassword=yourpassword",
        host, port, database
    );
    return DriverManager.getConnection(url, user, password);
}
```

## 相关文档

- [Doris SSL 配置文档](https://doris.apache.org/docs/admin-manual/certificate)
- [MySQL Connector/J SSL 配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)
- [Java Keytool 文档](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
