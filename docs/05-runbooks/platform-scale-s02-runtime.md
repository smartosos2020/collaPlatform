---
title: PLATFORM-SCALE-S02 运行隔离部署与故障处置手册
status: active
program: PLATFORM-SCALE
program_revision: 4
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S02 运行隔离部署与故障处置手册

## 1. 适用范围

本手册适用于 S02 交付的模块化单体运行形态：同一 Server 镜像分别以 `api`、`worker`、`event-gateway`、`maintenance` 角色运行，两个 API 实例共享 PostgreSQL、Redis、MinIO。知识协同继续由独立 Hocuspocus/Yjs 服务承载。

本手册不承诺 Worker 多实例接管、Event Gateway 多节点 fanout、基础设施集群高可用或固定容量；这些分别属于 PLATFORM-SCALE-S03、S04、S05。

## 2. 冻结拓扑

| 组件 | 数量 | 角色与职责 | 可进入业务流量 |
| --- | ---: | --- | --- |
| `maintenance` | 按发布执行 | Flyway、初始管理员、MinIO bucket 等一次性初始化 | 否 |
| `api-a` / `api-b` | 2 | HTTP 查询、命令、事务和 outbox append | 是 |
| `worker` | 1 | domain event 消费和临时旧知识协同维护任务 | 否 |
| `event-gateway` | 1 | `/ws/events`、认证和本地 session | WebSocket |
| `collaboration-a` / `collaboration-b` | 2 | Hocuspocus/Yjs 知识实时协同 | WebSocket |
| PostgreSQL / Redis / MinIO | 各 1 | 共享事实、瞬时信号/缓存、对象存储 | 内部 |

生产环境不得使用 `combined`，也不得把 Worker、通用 WebSocket 或维护初始化重新放回 API。

## 3. 发布顺序

1. 准备生产环境变量并固定 `SERVER_IMAGE`、`WEB_IMAGE`、`COLLABORATION_IMAGE` 到不可变版本。
2. 启动 PostgreSQL、Redis、MinIO，等待各自 healthcheck 通过。
3. 单独执行 `maintenance`，要求退出码为 0；失败时停止发布，不启动新 API。
4. 启动 `api-a`、`api-b`、`worker`、`event-gateway`、两个 collaboration 节点和 Web。
5. API 与 Gateway readiness 通过后再启动或刷新 Nginx。
6. 通过入口执行登录、权限读取、文件读写和知识协同 smoke。

```bash
docker compose -p <project> -f deploy/docker-compose.prod.yml up -d postgres redis minio
docker compose -p <project> -f deploy/docker-compose.prod.yml run --rm maintenance
docker compose -p <project> -f deploy/docker-compose.prod.yml up -d api-a api-b worker event-gateway collaboration-a collaboration-b web nginx
```

不得让两个 API 隐式执行 Flyway。schema 回退不是单 API 回退步骤的一部分。

## 4. 健康与观测

| 检查 | 用途 | 预期 |
| --- | --- | --- |
| `/actuator/health/liveness` | 判断进程是否应被重启 | 短暂依赖故障不应使 liveness 失败 |
| `/actuator/health/readiness` | 判断实例能否接收新流量 | API 依赖 PostgreSQL；maintenance 固定不接流量 |
| `/api/health` | 入口业务健康 | 至少一个 API 可服务时成功 |
| Actuator info、日志、指标 | 定位运行实例 | 必须包含 role、instance id、version、commit |

排障先按 request/correlation id 串联 Nginx、API、Worker/Gateway 日志，再按 role 与 instance id 缩小范围。日志不得记录 access token、refresh token、密码或生产密钥。

## 5. API 扩缩与摘除

API 扩容必须使用同一镜像、同一 schema 和不同 `COLLA_INSTANCE_ID`。新增实例先通过 readiness，再加入 upstream；同时核算 PostgreSQL 连接池总预算。

摘除节点按以下顺序：

1. 从 upstream 停止向目标节点发送新请求，或先使 readiness 进入 refusing traffic。
2. 最多等待 30 秒完成在途请求。
3. 停止实例并确认另一 API 持续通过入口健康检查。
4. 检查失败请求、重复业务结果、outbox 与 audit 是否异常。

强制终止仅用于故障演练或进程失控。恢复节点必须重新通过 readiness 后才能接流量。

## 6. 单 API 回退

回退只摘除一个 API upstream，保留另一实例：

1. 确认保留节点 readiness 和入口 `/api/health` 正常。
2. 摘除故障节点并停止容器。
3. 执行登录续用、权限读取、幂等命令和文件查询 smoke。
4. 观察 request/correlation、outbox、audit 和数据库连接。

禁止回滚 schema、启用 `combined`、恢复 API 内 Worker/WS，或切换到单节点专用代码。恢复双节点时使用相同镜像启动目标节点，readiness 通过后再加回 upstream。

## 7. 依赖故障矩阵

### PostgreSQL

- 预期：API readiness 失败，写请求不得产生部分业务、outbox 或 audit 副作用。
- 诊断：连接池超时、数据库连接数、迁移版本和事务错误。
- 恢复：先恢复数据库，再等待两个 API readiness；复核孤立 outbox/audit/文件记录。

### Redis

- 预期：持久 HTTP 事实仍以 PostgreSQL 为准；缓存和瞬时广播显式降级。
- 诊断：Redis 连接与超时、Gateway/协同降级日志。
- 恢复：恢复 Redis 后由 REST/数据库事实校准，不把丢失广播当成永久事实。

### MinIO

- 预期：文件上传完成/下载明确失败，非文件 API 继续服务。
- 诊断：bucket、object stat、对象大小和文件 pending 记录。
- 恢复：恢复 MinIO 后重试完成确认；禁止把无对象记录标为 completed。

## 8. 自动复验

```bash
COLLA_COMPOSE_PROJECT=<project> COLLA_BASE_URL=<entry-url> pnpm smoke:dual-api
COLLA_COMPOSE_PROJECT=<project> pnpm smoke:multi-instance-state
COLLA_COMPOSE_PROJECT=<project> pnpm smoke:maintenance-rehearsal
```

三项分别覆盖双 API 分发/退出/恢复、跨节点认证/撤销/幂等/上传/依赖故障、全新库并发迁移与初始化重放。任何失败都必须形成阻断或明确修复，不能通过延长无限等待或跳过断言关闭。

## 9. 已知边界与升级入口

- Worker 仍为单实例；lease、dead letter、replay 和多 Worker 接管归 PLATFORM-SCALE-S03。
- Event Gateway 仍为单实例；Redis fanout、多 Gateway 和重连校准归 PLATFORM-SCALE-S04。
- PostgreSQL、Redis、MinIO 仍为单点故障域。
- 当前验证是功能与恢复基线，不是容量承诺；固定负载、长稳和故障预算归 PLATFORM-SCALE-S05。
- S02 收口后建议恢复 PROJECT-PLATFORM-S05。若后续真实使用证明 Worker/Gateway 已成为阻断，再依据专项变更记录恢复 PLATFORM-SCALE-S03/S04。
