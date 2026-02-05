# Doris JDBC 部署指南

## 构建后的 output 目录

执行 `./build.sh` 后，会在项目根目录生成一个 `output/` 目录，包含完整的可运行环境：

```
output/
├── doris-jdbc.jar           # 可执行 JAR（包含所有依赖）
├── connection.properties    # 数据库连接配置
├── run.sh                   # 运行脚本
└── README.txt              # 使用说明
```

## 快速开始

### 1. 配置连接参数

编辑 `output/connection.properties`：

```bash
cd output
vim connection.properties
```

根据您的环境修改相应的连接参数（basic/ssl/ldap）。

### 2. 本地运行

```bash
cd output
./run.sh basic    # 基本认证
./run.sh ssl      # SSL 连接
./run.sh ldap     # LDAP 认证
```

### 3. 部署到远程服务器

#### 方式 1: 使用 scp

```bash
# 从项目目录
scp -r output/ user@remote-host:/path/to/destination/

# 登录远程服务器
ssh user@remote-host
cd /path/to/destination/output
vim connection.properties    # 修改配置
./run.sh basic               # 运行
```

#### 方式 2: 打包后传输

```bash
# 打包
cd /mnt/disk1/yy/tools/ldap
tar czf doris-jdbc-portable.tar.gz output/

# 传输
scp doris-jdbc-portable.tar.gz user@remote-host:/tmp/

# 在远程服务器解压
ssh user@remote-host
cd /opt/applications
tar xzf /tmp/doris-jdbc-portable.tar.gz
cd output
vim connection.properties
./run.sh basic
```

#### 方式 3: 直接拷贝目录

```bash
# 使用 U 盘或共享存储
cp -r output/ /mnt/usb/doris-jdbc/

# 在目标机器上
cp -r /mnt/usb/doris-jdbc /opt/applications/
cd /opt/applications/doris-jdbc
vim connection.properties
./run.sh basic
```

## 环境要求

目标机器只需要：
- **Java 17 或更高版本**
- 能够访问 Doris 数据库的网络连接

检查 Java 版本：
```bash
java -version
```

如果没有 Java 17，可以：
```bash
# CentOS/RHEL
sudo yum install java-17-openjdk

# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# 或下载 Oracle JDK 17
```

## 配置文件说明

`connection.properties` 包含三种连接模式的配置：

### 基本认证模式（Basic）
```properties
doris.host=172.20.32.136
doris.port=9033
doris.database=db1
doris.user=root
doris.password=
```

### SSL 连接模式（SSL）
```properties
doris.ssl.host=localhost
doris.ssl.port=9030
doris.ssl.database=test_db
doris.ssl.user=root
doris.ssl.password=
```

### LDAP 认证模式（LDAP）
```properties
doris.ldap.host=localhost
doris.ldap.port=9030
doris.ldap.database=test_db
doris.ldap.user=ldap_user
doris.ldap.password=ldap_password
```

## 测试运行

应用程序会自动执行以下操作：

1. 连接到 Doris 数据库
2. 创建测试数据库（如果不存在）
3. 切换到测试数据库
4. 列出所有数据库
5. 创建测试表
6. 插入测试数据
7. 查询并显示结果

成功运行的输出示例：

```
========================================
Doris JDBC Connection Test
========================================
Mode: BASIC
Host: 172.20.32.136:9033
User: root
Database: db1
========================================

[CONFIG] Loaded from: /path/to/output/connection.properties
[SUCCESS] Connected to Doris database

[1/6] Creating database if not exists...
      Database 'db1' is ready

[2/6] Switching to database...
      Now using database 'db1'

[3/6] Listing databases...
      Available databases:
      - information_schema
      - db1
      - mysql

[4/6] Creating test table...
      Table 'test_table' created

[5/6] Inserting test data...
      Inserted 1 row(s)

[6/6] Querying test table...
      Results:
      id                  name
      -------------------- --------------------
      1                   test_data

========================================
All tests completed successfully!
========================================

[INFO] Connection closed
```

## 故障排除

### 连接失败
```
Error: Connection refused
```
**解决方案：**
- 检查 Doris 服务是否运行
- 验证 host 和 port 配置是否正确
- 确认防火墙允许访问端口

### 认证失败
```
Error: Access denied for user
```
**解决方案：**
- 检查用户名和密码是否正确
- 对于 LDAP 模式，确认 Doris 已启用 LDAP 认证
- 验证用户在 Doris 中有相应权限

### Java 版本问题
```
Error: UnsupportedClassVersionError
```
**解决方案：**
- 确保安装 Java 17 或更高版本
- 设置正确的 JAVA_HOME 环境变量

## 生产环境部署建议

1. **配置文件权限**
   ```bash
   chmod 600 connection.properties  # 保护密码
   ```

2. **日志记录**
   ```bash
   ./run.sh basic 2>&1 | tee -a logs/doris-jdbc.log
   ```

3. **定时任务**
   ```bash
   # 添加到 crontab
   0 2 * * * cd /opt/doris-jdbc/output && ./run.sh basic
   ```

4. **使用 SSL**
   - 生产环境建议使用 SSL 模式
   - 配置 Doris 服务器端 SSL 证书

5. **LDAP 认证**
   - 确保 Doris FE 配置了 LDAP
   - 建议结合 SSL 使用（LDAP + SSL）

## 重新构建

如果需要修改代码后重新构建：

```bash
cd /mnt/disk1/yy/tools/ldap
./build.sh
```

新的 `output/` 目录会自动创建，包含更新后的 JAR 文件。

## 项目结构

源代码位置：
```
/mnt/disk1/yy/tools/ldap/
├── src/main/java/com/doris/jdbc/
│   ├── DorisConnection.java          # 主程序
│   ├── ConnectionFactory.java        # 连接工厂
│   └── auth/ClearPasswordPlugin.java # LDAP 认证插件
└── src/main/resources/
    └── connection.properties          # 配置模板
```

构建产物：
```
output/                                # 可移植的完整包
target/                                # Maven 构建输出
```

## 参考资料

- Apache Doris 官方文档: https://doris.apache.org/docs/4.x/admin-manual/auth/authentication/federation
- MySQL Connector/J 文档: https://dev.mysql.com/doc/connector-j/8.0/en/
- 项目 GitHub（如有）

---

**注意**: output 目录是完全独立的，可以复制到任何有 Java 17+ 的机器上运行，无需额外依赖。
