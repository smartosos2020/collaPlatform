---
title: 模块化单体与独立运行组件准入基线
status: reference
snapshot_at: 2026-07-24T00:10:35+08:00
snapshot_head: ee8fb6883ac5868976cb261a25ab6d4972c33981
snapshot_scope: working-tree
---

# 模块化单体与独立运行组件准入基线

## 1. 文档定位

本文记录 `PROJECT-PLATFORM-S04` 执行期间的一次只读架构准备审计，为后续 `PLATFORM-SCALE` 专项提案提供当前证据。本文不是当前架构事实文档、不是第二份执行路线，也不改变 S04 的范围、状态或完成结论。

审计时工作区基于提交 `ee8fb6883ac5868976cb261a25ab6d4972c33981`，另有 50 个 S04 暂态变更路径，其中 `docs` 9 个、`server` 30 个、`web` 11 个。另一会话仍在推进 S04，因此本文中的计数是时间点基线；S04-M5 合入主干后必须重新扫描并记录差异，不能直接把本报告当作激活时事实。

关联提案：

- `docs/01-architecture/platform-scale-target-architecture-proposal.md`
- `docs/00-product/initiatives/platform-scale-program-proposal.md`

## 2. 审计范围和方法

审计读取：

- `server/pom.xml`
- `server/src/main/java`
- `server/src/main/resources/db/migration`
- `server/src/test/java`
- `web/src/modules`
- `collaboration`
- `deploy/docker-compose.prod.yml`
- `deploy/nginx/colla.conf`
- 当前 active 产品、架构、技术选型、专项和路线文档

静态统计方法：

1. 后端按 `com.colla.platform.modules.<module>` 解析 Java import。
2. 跨模块 `infrastructure` import 单独计数。
3. 根据 import 有向图计算互相可达的强连通分量。
4. 以当前有效表名和暂定 owner 映射扫描 Java SQL 中的 `from/join/update/into/delete from`。
5. 前端按相对路径和 `@/` 路径解析 feature 之间的静态及动态 import。
6. 读取运行时内存状态、定时任务、事件 claim、Redis、协同 sidecar 和生产 Compose。

边界：

- SQL 扫描只识别已登记表名和常见 SQL 关键字，动态 SQL、函数封装和间接访问仍需人工复核。
- 前端计数是准备期扫描；正式门禁必须改用 TypeScript AST 和真实路径别名解析。
- 本轮未执行压力、长稳和故障注入，不形成容量承诺。

## 3. 总体判断

目标是保留同一代码库和共享 PostgreSQL，把系统演进为：

- 业务后端是具有自动边界门禁的模块化单体。
- HTTP API 可以增加同构无状态实例。
- 异步消费可以独立于 API 增减实例。
- 通用实时连接可以独立于 API 增减实例。
- 现有 Hocuspocus/Yjs 知识协同继续作为独立实时组件。

当前不是从零开始，但还不能声明达到目标。

| 维度 | 暂态成熟度 | 主要依据 |
| --- | ---: | --- |
| 领域和包结构 | 6/10 | 15 个业务模块普遍具有 api/application/domain/infrastructure 分层 |
| 可执行模块边界 | 3/10 | 204 条跨模块 import，47 条直接跨模块 infrastructure import，缺少架构测试 |
| HTTP API 横向准备 | 6/10 | 数据和文件状态已外置，但单 Server 同时承载 API、定时任务和通用 WebSocket |
| 知识实时协同 | 7/10 | 两个 Hocuspocus 节点、Redis 广播、PostgreSQL 恢复和多节点集成测试已经存在 |
| 通用实时事件 | 2/10 | `/ws/events` session 只保存在单个 Spring 进程内 |
| 异步独立扩容 | 4/10 | 有持久化事件、幂等键、重试和 `SKIP LOCKED`，但 Worker 与 API 同进程且消费者硬编码 |
| 容量与故障验收 | 3/10 | 有健康检查和部分指标，没有双 API、独立 Worker、通用实时网关的容量基线 |

## 4. 后端模块边界基线

### 4.1 规模

- 模块：15
- Java 文件：233
- 跨模块 import：204
- 涉及跨模块 import 的文件：59
- 跨模块 `infrastructure` import：47
- 涉及跨模块 `infrastructure` import 的文件：22
- 跨模块有向边：58

模块文件规模：

| 模块 | Java 文件 |
| --- | ---: |
| project | 76 |
| knowledge | 38 |
| identity | 24 |
| permission | 16 |
| platform | 12 |
| base | 10 |
| im | 8 |
| approval | 7 |
| audit | 7 |
| file | 7 |
| notification | 7 |
| search | 7 |
| admin | 6 |
| event | 4 |
| workspace | 4 |

### 4.2 循环依赖

以下 11 个模块处于同一个强连通分量：

`audit, base, event, file, identity, im, knowledge, permission, platform, project, search`

其余 `admin`、`approval`、`notification`、`workspace` 各自不在该强连通分量中，但仍可能通过基础设施 import 或私有表访问形成单向耦合。

这意味着当前包名表达了模块意图，但编译依赖尚不满足可独立理解、可替换和可渐进提取的模块化单体要求。

### 4.3 主要 infrastructure import 边

| 来源 | 目标 | infrastructure import |
| --- | --- | ---: |
| project | event | 5 |
| project | platform | 4 |
| project | identity | 4 |
| knowledge | platform | 2 |
| project | audit | 2 |
| permission | knowledge | 2 |
| project | file | 2 |
| knowledge | identity | 2 |
| knowledge | event | 2 |

典型路径：

- `project/application/WorkItemFieldComplexReferenceValidator.java` 直接依赖 identity、organization、user-group 和 file Repository。
- `workspace/application/WorkspaceDashboardService.java` 直接依赖 project、IM、notification、knowledge 和 Base Repository。
- 多个模块直接依赖 `event.infrastructure.DomainEventRepository` 和 `audit.infrastructure.AuditRepository`。
- `shared/websocket/WebSocketAuthInterceptor.java` 直接依赖 identity infrastructure，shared 层并非真正向下稳定。

### 4.4 跨模块事务

16 个带 `@Transactional` 的 application 文件同时 import 其他模块 infrastructure。高优先级示例：

- `identity/application/MemberService.java` 在离职事务内直接更新 `knowledge_base_spaces` 和 `conversations`，并调用项目空间交接。
- `knowledge/application/KnowledgeContentService.java` 同时依赖 event、file、identity、platform 和 project infrastructure。
- `project/application/ProjectService.java` 同时依赖 event、file、IM 和 platform infrastructure。
- `permission/application/ResourcePermissionManagementService.java` 直接依赖 knowledge Repository。

事件和审计写入需要与业务命令同事务，但应通过稳定的 outbox/audit port 暴露，不能要求所有业务模块 import 对方 infrastructure。

## 5. 数据归属基线

### 5.1 暂定 owner

| Owner | 核心表族 |
| --- | --- |
| identity | workspaces、users、sessions、devices、departments、user_groups 及成员关系 |
| permission | roles、permissions、role assignments、resource permissions 和 permission requests |
| platform | object type rules、object links、recent accesses、favorites |
| project | legacy project/issue、project spaces、type/version、field/option/command |
| knowledge | knowledge base、item、block、comment、version、template、relation、collaboration persistence |
| base | bases、tables、fields、records、views、record collaboration |
| im | conversations、members、messages、mentions、links、reactions |
| approval | forms、flow nodes、instances、tasks、action logs |
| file | files、file usages |
| notification | notifications、notification preferences |
| event | domain_events |
| audit | audit_logs |
| search | search_index_entries |
| admin/workspace | 不拥有业务事实表，只做治理或用户工作台组合 |

共享数据库中的 workspace/user 外键可以保留；表归属规则约束的是业务读写入口，不要求为了模块化而删除 referential integrity。

### 5.2 已确认的跨 owner SQL

准备期扫描确认 22 个文件包含 96 个跨 owner SQL 表引用。主要关系：

| 来源 | Owner | 引用数 | 说明 |
| --- | --- | ---: | --- |
| permission | identity | 14 | 主体、部门和用户组解析 |
| knowledge | identity | 9 | 维护人、成员和主体解析 |
| search | base | 6 | 全局搜索源表读取 |
| project | identity | 6 | 用户和 workspace 校验/展示 |
| identity | permission | 5 | 登录身份与角色权限读取 |
| search | identity | 5 | 人员、部门和用户组搜索 |
| knowledge | permission | 5 | 直接 ACL 读写 |
| search | knowledge | 4 | 知识索引构建 |
| permission | knowledge | 4 | 资源存在性和范围解析 |

需要分类处理：

- `search`：允许作为暂时只读例外，但目标是由事件维护自身投影，不再在线联查所有业务私表。
- `admin`：允许作为暂时治理读模型例外，但目标是调用 query facade 或专用治理投影，禁止写入来源模块表。
- `MemberService` 等跨模块写事务：阻断级问题，应转为显式交接流程或流程协调器。
- 权限主体和资源解析：应转为 `SubjectDirectory`、`ResourceDescriptor` 等公共合同。
- 展示用户名称的只读 join：可以保留数据库外键，但应用代码应逐步使用批量 directory/query port，避免每个 Repository 自行理解 users 表。

## 6. 异步基线

已经具备：

- `domain_events` 持久化事实。
- workspace 范围幂等键。
- retry count、next attempt 和 last error。
- claim 使用单条 `update ... where id in (select ... for update skip locked) returning`，支持多个消费者竞争 pending 事件。
- 通知具有 dedupe key。

主要缺口：

1. `DomainEventWorker` 是无条件 `@Scheduled` Bean，和 API 一起扩缩容。
2. Worker 直接依赖 Notification Repository、Notification Service 和 SearchIndexService，没有 handler registry。
3. 事件进入 `processing` 后没有 `claimed_at`、lease owner 或 visibility timeout；进程在 mark 前退出会留下无法自动回收的事件。
4. 没有 dead-letter 状态、受控 replay、积压年龄、处理延迟和失败率指标。
5. Search 对任意受支持聚合事件执行 workspace 全量 refresh，不是增量读模型。
6. 生产 Compose 只有一个 Spring Server，没有独立 worker service。

## 7. 实时基线

### 7.1 已有独立知识协同

生产部署已经包含：

- `collaboration-a`
- `collaboration-b`
- Hocuspocus/Yjs
- Redis extension
- Nginx least-connections
- PostgreSQL snapshot 和去重 update log
- 节点重启、Redis 降级和数据库恢复集成测试

因此知识协同不需要重新设计协议或回迁 Spring。

### 7.2 待收口的通用实时链路

`/ws/events` 仍由 Spring Server 承载：

- `WebSocketSessionRegistry` 使用本地 `ConcurrentHashMap`。
- IM 和 Notification 直接调用 `WebSocketMessageSender`，只能命中当前进程的 session。
- API 扩成两个实例后，命令执行节点与用户连接节点可能不同。
- 当前 Nginx 只为 collaboration 定义多节点 upstream，Server 仍是单节点。

### 7.3 Spring 旧知识协同残留

Spring 内仍存在：

- 本地 room、dirty snapshot、presence 和 session map。
- autosave 和 presence cleanup 定时任务。
- `PlatformWebSocketHandler` 对旧知识协同命令的分派。
- `/collaboration/health` 仍读取旧 service，而 ticket 已走 Hocuspocus gateway。

S04 后应先确认真实客户端和兼容范围，再按观测、关闭入口、删除定时任务、删除旧状态的顺序退出，不能直接删除。

## 8. 前端模块基线

- feature 模块：16
- 跨 feature import：64
- 涉及文件：23
- 跨 feature 有向边：40
- 强连通分量：`knowledgeBases <-> search`

主要耦合：

- knowledgeBases 直接依赖 platform、permissions、files、auth、projects、search、bases 和 admin。
- search 页面直接依赖 knowledgeBases API。
- admin 直接依赖 projectSpaces。
- messenger 直接依赖 projects。

目标不是拆成多个 SPA，而是保留单个 Vite 应用，要求每个 feature 通过显式 public entry 或 shared client contract 暴露能力，禁止跨 feature 深路径 import。正式门禁应使用 TypeScript AST，不使用简单文本正则作为唯一证据。

## 9. 部署和运维基线

当前生产 Compose：

- 一个 Spring Server。
- 两个 collaboration sidecar。
- 一个 PostgreSQL。
- 一个 Redis。
- 一个 MinIO。
- 一个 Web 和一个 Nginx。

已有 Actuator、Prometheus registry、健康检查、备份恢复和 collaboration 指标。尚未交付：

- API upstream 和两个 API 实例。
- 独立 worker service。
- 独立通用 event gateway。
- Worker backlog/lease/replay 指标。
- API、Worker、event gateway 的故障注入和容量基线。
- PostgreSQL、Redis、MinIO 高可用。

面向单企业部署，第一阶段只要求应用层横向扩展和基础设施可恢复；不把数据库集群、自动扩缩容或 Kubernetes 设为阻断目标。

## 10. 优先级结论

### P0：S05 前阻止继续扩散

1. 建立后端 import 边界和前端 feature 边界门禁。
2. 冻结表 owner 和允许例外。
3. 为 project 新增复杂字段依赖建立 identity/file/platform 公共合同。
4. 把 outbox、audit 从 infrastructure import 提升为稳定公共 port。
5. 明确 API、worker、event-gateway 运行角色开关。

### P1：独立运行闭环

1. Worker lease、回收、handler、dead-letter、replay 和指标。
2. API 双实例，定时任务和 WebSocket 从 API 角色移除。
3. 通用 event gateway 使用 Redis pub/sub 做跨节点瞬时信号，客户端通过 REST 事实源重连校准。
4. 退出 Spring 旧知识协同链路。

### P2：容量和故障验收

1. HTTP/通用 WebSocket 使用容器化 k6 或等价工具。
2. Hocuspocus/Yjs 使用协议级 Node 客户端负载工具。
3. 执行节点退出、Redis 短时中断、Worker 崩溃恢复和 backlog 消化。
4. 固化容量环境、数据种子、指标和结果，不以本地单次数字作承诺。

## 11. S04 合入后的强制重扫

激活任何 `PLATFORM-SCALE` Stage 前必须：

1. 确认 S04-M5 完成、S04 路线归档且变更已合入主干。
2. 在干净主干重新统计模块文件、跨模块 import、infrastructure import、强连通分量和跨 owner SQL。
3. 对比本报告，单独列出 S04 新增、删除和改变的依赖。
4. 运行 S04 route-final 证据复核，不把架构准备报告替代 S04 验收。
5. 更新目标架构提案中的当前缺口和容量假设。
6. 再决定将 `PLATFORM-SCALE` 设为 Active，或继续保持 Proposed。

## 12. 准备期结论

当前最合理的演进方式不是拆业务微服务，而是：

1. 先把模块边界变成可执行门禁。
2. 使用同一 Server 代码库产生不同运行角色。
3. 保留共享 PostgreSQL 和跨模块外键，但禁止无归属的私表业务读写。
4. 保留已经独立部署的知识协同。
5. 将通用实时与异步处理从 API 进程中拆出并分别扩容。

本报告完成了 S04 后架构专项所需的准备基线，但所有计数和热点都必须在 S04 合入后复验。
