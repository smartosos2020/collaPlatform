---
title: S04 通用实时网关与知识协同运行手册
status: active
program: PLATFORM-SCALE
stage: PLATFORM-SCALE-S04
updated_at: 2026-07-24
---

# S04 通用实时网关与知识协同运行手册

## 1. 适用范围与边界

本文覆盖生产基线中的双 Event Gateway 与双 Hocuspocus collaboration 节点，包括部署、滚动、扩缩、观测、REST 校准、故障恢复和单节点回退。

当前交付是“应用层节点可替换、可水平扩展”的基线，不是基础设施高可用承诺：

- PostgreSQL、Redis 和 MinIO 仍各为单实例，属于共享单点故障域。
- Redis 只承载瞬时 realtime fanout、Yjs 多节点广播和 awareness，不是业务事实源。
- PostgreSQL 保存业务事实、durable realtime signal、Yjs update 和 snapshot。
- Event Gateway 不处理知识编辑；Hocuspocus/Yjs 是唯一知识实时协议。
- Spring 不得恢复旧 room、presence、autosave、cleanup 或知识编辑 WebSocket。
- S04 证据只证明功能、故障隔离和恢复，不构成生产容量、长稳或 SLA 承诺；这些属于 S05。

## 2. 冻结拓扑

| 组件 | 实例 | 运行职责 | 公开入口 |
| --- | --- | --- | --- |
| HTTP API | `api-a`、`api-b` | 业务 HTTP、事务和查询 | Nginx `/api` |
| Event Worker | `worker-a`、`worker-b` | durable event handler 与 realtime signal 生产 | 不公开 |
| Event Gateway | `event-gateway-a`、`event-gateway-b` | Redis signal 订阅、本地 WebSocket session 和 fanout | Nginx `/ws/events` |
| Collaboration | `collaboration-a`、`collaboration-b` | Hocuspocus/Yjs update、awareness、durable store/recovery | Nginx `/collaboration` |
| Maintenance | `maintenance` | Flyway 和一次性初始化 | 不加入 upstream |

所有同类节点必须使用同一不可变镜像和同一 `SOURCE_COMMIT`，但使用唯一 instance/node id。Nginx 通过 Docker DNS 解析当前容器地址，不要求粘性会话。

## 3. 资源与保护预算

以下是 S04 功能验收使用的保护上限，不是经负载验证的生产容量：

| 预算 | 默认值 |
| --- | --- |
| 每个 Gateway 最大连接 | 5000 |
| Gateway CPU / 内存 | 1 CPU / 768 MiB |
| Gateway 发送线程 / 执行器队列 | 4 / 512 |
| 每 session 发送队列 | 64 |
| Gateway 最近 signal 去重缓存 | 10000 |
| realtime payload 上限 | 16 KiB |
| 每个 collaboration 最大连接 / room | 5000 / 1000 |
| collaboration 最大单 update | 1 MiB |
| PostgreSQL 故障期间每节点 durable queue | 1024 updates / 32 MiB |
| collaboration 授权暂态宽限 / 重试间隔 | 120 秒 / 5 秒 |
| collaboration CPU / 内存 | 1 CPU / 768 MiB |

达到连接、room、发送队列或 durable queue 上限时必须拒绝或隔离新压力，不能无界增长。不要在没有 S05 负载证据和 PostgreSQL/Redis 预算复核的情况下直接提高这些值。

## 4. 健康与就绪

### Event Gateway

- liveness：`/actuator/health/liveness`，只反映进程生命状态。
- readiness：`/actuator/health/readiness`，要求运行角色和 PostgreSQL 可用。
- Prometheus：`/actuator/prometheus`，只允许内部监控访问。

### Collaboration

- `/health`：进程存活。
- `/ready`：Redis 两个客户端就绪且后端依赖可用；依赖降级时应退出 upstream，而不是重启风暴。
- `/metrics`：受 `COLLA_COLLABORATION_INTERNAL_SECRET` 保护的 JSON 指标；不得经公网暴露。

发布、扩容或恢复时必须先等新节点 readiness 成功，再加入流量。节点 readiness 失败但 liveness 正常时，应先检查依赖，不要反复重启进程掩盖 PostgreSQL 或 Redis 故障。

## 5. 关键指标与初始告警

### Event Gateway

- `colla.realtime.websocket.connections`
- `colla.realtime.websocket.capacity.rejections`
- `colla.realtime.websocket.send{outcome=enqueued|sent|failed|dropped|slow_closed}`
- `colla.realtime.websocket.session.queues`
- `colla.realtime.websocket.legacy.frame{outcome=emitted|blocked}`
- `colla.realtime.redis.publish{outcome=published|failed}`
- `colla.realtime.redis.subscribers`
- `colla.realtime.redis.consume{outcome=...}`

### Collaboration

受保护的 JSON 指标至少检查：

- `nodeId`、`redisStatus`、`connections`、`acceptedConnections`、`documents`
- `capacityRejections.connections|rooms`
- `staleWrites.snapshot|generation`
- `authorizationGraceUses`
- `durableQueue.updates|bytes|retryAttempts|recoveredUpdates|backpressureRejections`
- `failures.backend|redis|recovery|store`
- `lastFailure`

在 S05 发布容量基线前使用以下保守告警：

| 信号 | 告警条件 | 操作 |
| --- | --- | --- |
| readiness | 任一节点连续 3 次失败 | 停止滚动，检查 PostgreSQL、Redis、内部 API 和镜像版本 |
| capacity rejection | 任一非零增量 | 冻结扩流，确认连接/room/队列预算和异常客户端 |
| Gateway `failed`/`dropped`/`slow_closed` | 5 分钟持续增长 | 按 instance 定位，检查慢客户端、发送队列和 Redis |
| Redis consume/publish failure | 任一持续增长 | 业务事实仍以 REST/PostgreSQL 为准；恢复 Redis 后执行校准 |
| collaboration durable queue | `updates` 或 `bytes` 持续增长 | 检查 PostgreSQL；接近上限时停止新增编辑流量 |
| authorization grace | 有增量 | 确认 PostgreSQL/内部授权 API；120 秒内恢复，否则会话应拒绝继续写 |
| stale write / recovery failure | 任一非零增量 | 停止滚动，保留 correlation 和节点指标，核对 durable sequence |

指标标签和日志不得包含 token、ticket、正文、消息内容、ACL 清单或 workspace 高基数值。

## 6. 发布、扩容与降容

1. 通过 release gate 验证同一 `SOURCE_COMMIT` 的不可变 Server、Web 和 Collaboration 镜像。
2. Maintenance 先完成 Flyway；API、Worker、Gateway、Collaboration 均不得自行执行迁移。
3. Event Gateway 和 Collaboration 每次只替换一个节点。
4. 等待新节点 readiness，确认 instance/node id、镜像 revision 和关键指标正常。
5. 用真实 WebSocket/Yjs 连接确认新节点接受连接，再处理另一节点。
6. 降容前先从 upstream 移除目标节点，等待连接重连或正常关闭，再停止进程。
7. 每次扩缩、滚动和回退必须记录操作者、目标环境、目标节点、变更编号、镜像 revision 和校准结果。

单节点回退允许临时只保留一个 Event Gateway 和一个 Collaboration 节点。回退不得反向 Flyway、恢复旧 Spring 知识协议、启用 API 本地通用 WebSocket，或把 Redis signal 当作业务事实。

## 7. 客户端事实校准

WebSocket signal 只用于低延迟提示。重复、乱序、gap、重新连接、窗口重新获得焦点或 Redis 故障后，客户端必须通过 REST 恢复事实：

| 域 | 权威校准入口 |
| --- | --- |
| 通知 | `GET /api/notifications`、`GET /api/notifications/unread-count` |
| IM 会话 | `GET /api/conversations` |
| IM 消息 | `GET /api/conversations/{id}/messages?afterSeq={n}` |
| 项目 | `GET /api/projects/{id}` |
| 事项 | `GET /api/issues/{id}` |
| 项目空间 | `GET /api/project-spaces/{id}` |
| 身份/权限 | signal payload 中经过校验的本地 `/api/admin/...` 或 `/api/resource-permissions/...` 路径 |

不要根据 WebSocket payload 重建完整业务对象，也不要绕过权限直接查询私表。完整事件与披露边界见 `docs/01-architecture/event-side-effect-matrix.md`。

## 8. 故障处置矩阵

### 单个 Event Gateway 退出

1. 确认存活节点 readiness 与 Redis 订阅正常。
2. 客户端应自动重连到存活节点并收到 `connection.ready`。
3. 通过通知、IM、项目和权限 REST 入口完成四域校准。
4. 恢复故障节点，确认两个 instance 都接受新连接且无重复 fanout。

### Redis 中断

1. 确认 HTTP 业务写和 Worker durable event 继续，禁止将 Redis 失败解释为业务回滚。
2. Gateway 与 Collaboration 应显式降级；客户端进入重连/校准状态。
3. 恢复 Redis 后确认所有节点重新订阅、readiness 恢复。
4. 通过 REST 校准四域事实；协同会话确认 peer 收敛。
5. 检查订阅数、duplicate、failed、durable queue 和进程 restart count，确认无泄漏或重复。

### 单个 Collaboration 节点退出

1. 保留另一节点，确认 `/ready` 成功。
2. 客户端必须申请新的单次 ticket 并连接存活节点，不能复用已消费 ticket。
3. 在存活节点编辑并确认多个浏览器最终一致。
4. 恢复故障节点后确认它从 PostgreSQL durable update/snapshot 收敛。

### PostgreSQL 中断

1. Gateway/Collaboration readiness 应在健康检查预算内失败，liveness 可保持正常。
2. 已验证的协同会话仅可在最多 120 秒暂态授权宽限内继续；明确 401/403 或非暂态错误不得使用宽限。
3. 协同 update 进入有界内存 durable queue；达到 1024 updates 或 32 MiB 时必须背压，不能丢弃后伪报成功。
4. 恢复 PostgreSQL 后确认 `recoveredUpdates` 增长、queue 回到 0、内容持久化。
5. 关闭会话并依次重启两个 Collaboration 节点，再加载文档验证 durable 恢复。

该流程不是 PostgreSQL HA。若中断超过宽限或队列预算，应停止新增编辑并进入基础设施恢复流程。

### 慢客户端或发送异常

1. 按 Gateway instance 检查 session queue、`slow_closed`、`failed` 和 `dropped`。
2. 单个慢连接应被关闭，健康连接不得被其阻塞。
3. 不要先扩大队列；先排除异常客户端、网络和 payload 超限。
4. 客户端重连后以 REST 校准，不能要求服务端无限保留瞬时 signal。

## 9. 验证入口

以下入口会停止或重启隔离环境中的节点，只能用于明确命名的测试 Compose project，不能指向共享或生产环境：

```shell
pnpm smoke:dual-gateway
pnpm --dir web exec playwright test e2e/platform-scale-s04-m5-redis-recovery.spec.ts
pnpm --dir web exec playwright test e2e/platform-scale-s04-m5-collaboration-faults.spec.ts
```

执行故障注入前记录 Compose project、端口、目标服务、事件/变更编号和恢复负责人。执行后必须在 `finally` 或操作记录中恢复全部节点，并确认：

- 两个 Gateway 和两个 Collaboration 节点 healthy/ready。
- PostgreSQL、Redis、MinIO 已恢复。
- 容器 restart count 无意外增长。
- 四域 REST 校准和 Yjs durable reload 通过。

## 10. 禁止事项

- 禁止直接更新 realtime signal、Yjs update/snapshot 或 event delivery 私表来“修复”状态。
- 禁止在日志、截图或报告中保存 access token、refresh token、collaboration ticket 或内部 secret。
- 禁止同时滚动两个 Gateway 或两个 Collaboration 节点。
- 禁止在 S04 证据上宣称生产连接数、吞吐、P95、8 小时长稳或基础设施 HA。
- 禁止通过恢复旧 Spring 知识协议完成回退。
