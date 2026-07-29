# 起点银行（qdbank）1.0.3

适用于 Paper 1.21.1+ / Java 21 的跨服银行系统，由独立 Netty/SQLite 服务端与 Paper 客户端插件组成。

## 构建

```bash
mvn clean package
```

构建产物：

- `qdbank-server/target/qdbank-server.jar`
- `qdbank-client/target/qdbank-client.jar`

也可以执行 `./build.sh`。脚本会进行完整构建，并将两个 JAR、配置示例和部署说明集中复制到 `dist/`。

## 部署

1. 将服务端 JAR 放入独立目录，首次执行 `java -jar qdbank-server.jar` 会生成 `config.yml`。
2. 修改服务端 `secret_key` 和允许连接的 `clients[].id`，然后重新启动。
3. 将客户端 JAR 放入每个 Paper 服务端的 `plugins` 目录。
4. 首次启动 Paper 后修改 `plugins/qdbank/config.yml`，确保客户端 ID、密钥、地址和端口与独立服务端一致。
5. 重启 Paper，使用 `/qdbank status` 检查连接。

生产环境应使用防火墙限制 TCP 端口只允许受信 Minecraft 主机访问，并使用长度足够的随机密钥。SQLite 数据库默认启用 WAL；备份时请使用 SQLite 在线备份工具或在停止服务后复制数据库文件。

## 一致性策略

- 玩家首次访问银行时自动创建 `checking` 与 `saving` 账户。
- 同一账户允许多人同时打开和操作。
- 每次内容提交都携带 revision，服务端按到达顺序只接受第一个有效版本，后到的冲突操作会被拒绝并回退到权威内容。
- 审计记录包含物品增减数量、完整 Paper 物品二进制数据、世界、坐标和客户端服务器 ID。
- `/qdbank admin lookup <player> <range>` 查询审计，物品名称支持悬停查看完整提示信息。
- `/qdbank see` 的账户名补全直接读取银行服务端；`/qdbank admin see` 可补全银行内全部正版 UUID 玩家及其账户。
- 玩家登录以及客户端重新连接银行服务端时，会检查并补齐默认账户。
- 审计查询使用 `/qdbank admin lookup user:<玩家> action:<inventory|+inventory|-inventory> time:<时间> include:<物品> exclude:<物品>`；默认 `action:inventory time:30m`。
- 客户端服务器 ID 支持短字符串或中文，例如 `ghzs`、`公会生存`。
- 使用 `/qdbank admin page <页码>` 或查询结果中的可点击按钮翻页。
- 回滚使用与查询相同的筛选参数，并按审计中的每个槽位物品变更逐项逆序恢复。
- GUI 在一次保存确认前暂时阻止下一次变更。
- 网络断开或版本冲突会立即关闭相关 GUI。
- 所有服务端账户操作进入同一权威队列，避免更新与关闭乱序。
