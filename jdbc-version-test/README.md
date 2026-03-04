# JDBC Version Compatibility Test

针对 [Issue #60634](https://github.com/apache/doris/issues/60634) 的测试工具：`mysql-connector-j` 9.5.0+ 连接 Doris 时查询返回空数据。

## 问题背景

| 关键信息 | 说明 |
|---|---|
| **触发条件** | `useServerPrepStmts=true` + mysql-connector-j ≥ 9.5.0 |
| **现象** | 查询返回空结果集，但表中有数据 |
| **Workaround** | `useServerPrepStmts=false&cacheResultSetMetadata=true` |
| **影响版本** | Doris 3.1.4、4.0.2 均受影响 |

## 快速开始

### 前置条件

- Java 17+
- Maven 3.x
- 可访问的 Doris 集群

### 1. 修改配置

编辑 `src/main/resources/connection.properties`：

```properties
doris.host=127.0.0.1
doris.port=9030
doris.database=jdbc_version_test
doris.user=root
doris.password=
```

### 2. 测试单个版本

```bash
# 构建（指定驱动版本 profile）
./build.sh v9.5.0

# 运行
./run.sh
```

### 3. 批量测试所有版本

```bash
./run-all.sh
```

一次性构建并测试 6 个驱动版本（8.0.33 ~ 9.6.0），最终输出对比表格。

## 测试内容

### 驱动版本 (Maven Profile)

| Profile | 版本 |
|---|---|
| `v8.0.33` | 8.0.33 |
| `v9.1.0` | 9.1.0 |
| `v9.2.0` | 9.2.0 |
| `v9.4.0` | 9.4.0 |
| `v9.5.0` | 9.5.0 |
| `v9.6.0` | 9.6.0 |

### JDBC 参数组合

每个版本会测试 4 种参数组合：
1. **默认参数** — 无额外 URL 参数
2. **`useServerPrepStmts=true`** — 显式启用服务端预编译（触发 Bug 的场景）
3. **`useServerPrepStmts=false`** — 禁用服务端预编译
4. **`useServerPrepStmts=false&cacheResultSetMetadata=true`** — 评论区推荐的 Workaround

### 查询场景 (每种参数组合 9 个测试)

1. `Statement` — `SELECT *`
2. `Statement` — `SELECT WHERE`
3. `PreparedStatement` — `SELECT *`（核心场景）
4. `PreparedStatement` — `SELECT WHERE id = ?`（核心场景）
5. `PreparedStatement` — `SELECT WHERE username = ?`
6. 聚合查询 — `COUNT(*)`
7. `SHOW DATABASES`
8. `SHOW TABLES`
9. `ResultSet` 元数据检查

## 输出示例

```
╔══════════════════════════════════════════════════════════════════════╗
║                     COMPARISON SUMMARY                             ║
╠══════════════╦════════════════╦══════════╦══════════════════════════╣
║ Version      ║ Status         ║ Passed   ║ Failed                   ║
╠══════════════╬════════════════╬══════════╬══════════════════════════╣
║ v8.0.33      ║ ✅ ALL PASS    ║ 36       ║ 0                        ║
║ v9.1.0       ║ ✅ ALL PASS    ║ 36       ║ 0                        ║
║ v9.4.0       ║ ✅ ALL PASS    ║ 36       ║ 0                        ║
║ v9.5.0       ║ ❌ FAILURES    ║ 27       ║ 9                        ║
║ v9.6.0       ║ ❌ FAILURES    ║ 27       ║ 9                        ║
╚══════════════╩════════════════╩══════════╩══════════════════════════╝
```

## 目录结构

```
jdbc-version-test/
├── pom.xml                   # Maven profiles 切换驱动版本
├── build.sh                  # 构建脚本
├── run.sh                    # 运行脚本
├── run-all.sh                # 批量测试所有版本
├── README.md
├── .gitignore
└── src/main/
    ├── java/com/doris/versiontest/
    │   └── JdbcVersionTest.java
    └── resources/
        └── connection.properties
```
