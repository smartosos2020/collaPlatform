---
title: 当前技术架构
status: active
last_code_check: 2026-07-16
---

# 当前技术架构

本文只描述当前代码和数据库事实。历史路线、迁移过程和阶段验证结果保存在 `docs/90-reports/` 与 `docs/99-archive/`，不在本文重复展开。

## 总体形态

Colla Platform 当前是模块化单体：

- 后端：单个 Spring Boot 应用，按业务模块分包。
- 前端：单个 React SPA，用户工作台和管理后台使用独立 Shell、导航和路由边界。
- 数据库：单个 PostgreSQL schema，通过 Flyway V001-V052 演进。
- 基础设施：Redis、MinIO、WebSocket、平台对象、权限、事件、审计和搜索由模块共享。
- 交付：本地 Docker 依赖；生产基线是单后端、双协作节点的 Docker Compose + Nginx。

当前不拆微服务、不拆前后端仓库，也不把管理后台复制成第二套后端服务。

## 后端模块

后端代码位于 `server/src/main/java/com/colla/platform/modules`：

| 模块 | 当前职责 |
| --- | --- |
| `identity` | 登录、成员、部门、用户组、角色、权限码和设备 |
| `workspace` | 当前用户工作台聚合 |
| `im` | 会话、消息、已读、反应、链接和消息转事项/知识内容 |
| `project` | 项目、事项、工作流、BUG 验证、评论和关系 |
| `knowledge` | 知识库空间、目录项、知识内容、块、评论、版本、分享、导入导出和协同 |
| `base` | Base、数据表、字段、记录、视图、评论、关系和导入导出 |
| `approval` | 审批定义、实例、处理和统计 |
| `notification` | 用户通知和未读状态 |
| `search` | 用户内容搜索、索引刷新和知识库检索上下文 |
| `permission` | 通用资源 ACL、权限决策、申请、继承和治理 |
| `platform` | 平台对象摘要、卡片、内部链接、最近和收藏 |
| `event` | 领域事件持久化与异步消费 |
| `audit` | 审计日志写入和后台查询 |
| `file` | MinIO 文件上传、完成、下载和使用关系 |
| `admin` | 企业概览、应用治理和后台治理 facade |

分层约束：Controller 只处理 HTTP；application service 编排事务、权限和用例；domain 保存业务模型；infrastructure 处理数据库和外部系统。跨模块能力不得通过读取对方私有表绕过应用边界。

## 前端模块与路由

前端代码位于 `web/src`。`web/src/app/router.tsx` 是路由事实来源。

用户工作台路由：

- `/`：工作台。
- `/im`：消息。
- `/projects`、`/projects/:projectId`、`/issues/:issueId`：项目与事项。
- `/knowledge-bases`、`/knowledge-bases/:spaceId`、`/knowledge-bases/:spaceId/items/:itemId`：知识库与知识内容。
- `/bases/...`：多维表格。
- `/approvals/...`：审批。
- `/notifications`、`/devices`、`/search`：通知、设备和搜索。

管理后台路由：

- `/admin/overview`
- `/admin/users`
- `/admin/departments`
- `/admin/user-groups`
- `/admin/roles`
- `/admin/permission-governance`
- `/admin/knowledge-bases`
- `/admin/app-governance`
- `/admin/audit-logs`

边界规则：

- 用户工作台使用 `UserWorkspaceShell`，管理后台使用 `AdminConsoleShell`。
- `/admin/*` 必须经过 `RequireAdmin`，且后端管理 API 仍需独立鉴权。
- 用户工作台不展示组织、权限和审计治理页面；后台不复用 IM、项目、知识内容或 Base 的用户协作页面作为主体。
- 共享组件只承载低业务语义 UI；用户页面和后台页面不得相互引用页面级样式。
- 未匹配路由统一进入 404；旧 `/docs` 路由已删除。

## 知识库模型

知识库当前采用唯一模型：

| 概念 | 当前模型 | 物理表 |
| --- | --- | --- |
| 空间 | `KnowledgeBaseSpace` | `knowledge_base_spaces` |
| 目录项 | `KnowledgeBaseItem` | `knowledge_base_items` |
| 正文块 | `KnowledgeContentBlock` | `knowledge_content_blocks` |
| 版本 | `KnowledgeContentVersion` | `knowledge_content_versions` |
| 评论 | `KnowledgeContentComment` | `knowledge_content_comments` |
| 协同状态 | Knowledge content collaboration state | `knowledge_content_collaboration_states` |
| 分享、关系、模板 | Knowledge content subordinate models | `knowledge_content_*` / `knowledge_item_*` |

模型约束：

- `knowledge_base_spaces` 是知识库空间唯一主模型。
- `KnowledgeBaseItem` 可以是内容、目录、对象引用或外部链接；只有可编辑正文 item 才是 `KnowledgeContent`。
- `root_item_id` 和 `home_item_id` 表达空间根和首页。
- active blocks、版本 `block_snapshot`、模板 blocks 和协同 payload blocks 是持久化正文来源。
- 平台对象类型只使用 `knowledge_content`。
- 用户 API 和路由必须同时携带 `spaceId + itemId` 并校验归属。
- `/knowledge-bases/{spaceId}` 是入口解析路由，服务端返回的有效 `homeItemId` 决定规范 `/knowledge-bases/{spaceId}/items/{itemId}`；首页缺失时回退到根 item，正文、目录、对象入口和外链共用该 item 上下文。
- 用户默认内容路径不得渲染治理仪表盘；空间管理保留为显式 `view=management` 辅助路径，权限和元数据面板在正文中默认折叠且按权限出现。
- `/api/docs`、`Document*` 产品类型、`documents/document_*` 活动表和旧兼容 resolver 已删除。

## 数据库迁移

当前 Flyway 版本为 V053。历史迁移文件不可修改。

知识库最后四个迁移：

| 迁移 | 结果 |
| --- | --- |
| V044 | 把旧知识内容物理模型迁为 `knowledge_base_items` 和 `knowledge_content_*` |
| V045 | 删除旧正文快照和双写字段，确立 blocks/块快照事实来源 |
| V046 | 把对象链接、ACL、通知、搜索和关系迁为规范知识语义 |
| V047 | 删除旧权限和兼容结构，并阻止活动表重新写入旧产品类型 |
| V048 | 注册项目平台对象类型及规范路由元数据 |
| V049 | 增加用户通知偏好及用户维度索引 |
| V050 | 建立知识内容 canonical 文档和迁移批次合同 |
| V051 | 为版本、模板和协同状态补齐 canonical snapshot/schema 字段 |
| V052 | 建立 Yjs snapshot、update log 和短期协同 ticket 合同 |
| V053 | 回填可选择的平台对象并建立对象引用查询索引 |

数据库规则：

- 新表、列、约束或索引只能通过新的 Flyway 文件增加。
- 不建立旧表兼容视图，不恢复旧产品 objectType。
- 迁移必须同时支持空库安装和存量升级；破坏性迁移必须有备份、恢复和数据一致性证据。
- 集成测试使用 Testcontainers PostgreSQL，不向共享本地开发库写测试夹具。

## API 边界

| 边界 | 主要前缀 | 规则 |
| --- | --- | --- |
| 用户协作 | `/api/workspace`、`/api/conversations`、`/api/projects`、`/api/issues`、`/api/knowledge-bases`、`/api/bases`、`/api/approvals`、`/api/notifications`、`/api/search` | 返回当前用户可见和可操作内容，不夹带后台治理字段 |
| 管理治理 | `/api/admin/*` | 返回组织、权限、风险、审计、统计和治理动作，必须显式校验后台权限 |
| 共享平台 | `/api/platform`、`/api/resource-permissions`、`/api/files` | 只提供对象摘要、权限原语和文件能力，不决定页面信息架构 |
| 身份 | `/api/auth`、`/api/devices` | 当前用户身份、会话和设备所有权 |
| 健康 | `/api/health`、`/actuator/health` | 服务与运维健康检查 |

知识内容规范 API 位于 `/api/knowledge-bases/{spaceId}/items/{itemId}/*`，覆盖正文、blocks、评论、版本、分享、权限、关系、导入导出、性能和协同。空间生命周期和目录树仍位于 `/api/knowledge-bases/{spaceId}` 及其 `/items` 子资源。

## 权限与可见性

系统区分三类权限：

1. 用户动作权限：项目成员、审批参与人、会话成员、知识内容和 Base 协作动作。
2. 资源管理权限：owner/manage 对分享、授权、空间设置、移动和归档等动作。
3. 后台管理权限：组织、成员、角色、权限治理、知识库治理和审计查询。

`PermissionDecisionService` 对 `resource_permissions` 计算最高权限，支持 user、department、user_group 和 role 主体。权限解释必须返回允许状态、当前等级、要求等级和来源；无权限对象不得泄露标题、路径、正文或维护信息。

`ResourcePermissionManagementService` 负责授权、撤销、申请和继承。`PermissionGovernanceService` 负责权限排查、风险巡检和导出。高风险角色或 manage/owner 授权必须二次确认并写审计。

## 平台对象、搜索和通知

平台对象通过 resolver 统一返回 `available`、`forbidden`、`deleted`、`not_found` 或 `invalid`。用户卡片和后台卡片使用不同展示上下文；无后台权限时不得返回治理动作。

当前对象类型包括 issue、knowledge_content、base、base_table、base_record、message、approval 和 file。内部链接、IM 卡片、通知、最近、收藏、关系和搜索结果复用同一对象摘要。

用户搜索：

- `GET /api/search` 固定返回 `searchScope=user_content`。
- 覆盖事项、知识内容、Base、数据表、记录、消息和审批。
- SQL 召回阶段和 resolver 返回阶段都执行可见性检查。
- 知识内容命中可携带 block/comment 定位信息。

后台治理搜索走 `/api/admin/search-governance`，索引重建只允许通过 `POST /api/admin/search-governance/reindex` 的管理 facade 执行；用户搜索 API 不暴露治理写动作。

个人通知只消费用户协作通知；后台治理通知不得进入普通用户列表、未读数或用户侧已读写操作。通知偏好按来源持久化，资源授权与安全类必要通知不可关闭。

## 实时事件与协同

- WebSocket 统一入口为 `/ws/events`。
- 事件由 `DomainEventWorker` 从持久化事件表消费并推送。
- IM、通知、项目和知识内容使用该通道更新客户端状态。
- 知识内容使用 Hocuspocus/Yjs 协议统一标题、正文块、presence 和远端选择区。
- 两个协作节点通过 Redis 广播房间更新；每个节点使用唯一 node ID，Nginx 以 least-connections 分配连接。
- PostgreSQL 的 Yjs snapshot 和去重 update log 是恢复事实来源；Redis 只负责瞬时广播、awareness 和协调。
- 浏览器短暂离线可在有限更新数和字节预算内继续编辑，恢复时交换缺失状态；超限前可导出 `.yjs` 恢复副本。

## 文件与对象存储

文件模块使用 MinIO，负责上传会话、完成确认、下载 URL 和文件使用关系。业务模块只能通过 file service 使用文件能力，不自行拼接 MinIO URL 或直接操作 bucket。

## 交付与运行

本地依赖由根 `docker-compose.yml` 提供 PostgreSQL、Redis 和 MinIO。开发启动见 `docs/05-runbooks/local-dev.md`。

生产基线位于 `deploy/`：

- `docker-compose.prod.yml`：PostgreSQL、Redis、MinIO、后端、两个 collaboration sidecar、Web 和 Nginx。
- `nginx/colla.conf`：Web、API、事件 WebSocket、双节点知识协同和健康检查代理。
- `deploy/scripts/backup.ps1`、`restore.ps1`、`restore-drill.ps1`：备份、恢复和恢复演练。
- `deploy/scripts/health-check.ps1`：健康与可选 Prometheus 检查。
- `deploy/scripts/release-check.ps1`、`rollback.ps1`：发布检查和显式回滚。

当前只在知识协同进程层提供双节点重连恢复。Spring 后端、PostgreSQL、Redis 和 MinIO 仍是单实例，因此不承诺整个平台高可用、自动扩缩容或 Kubernetes。

## 测试与质量门禁

后端使用 JUnit 5、Spring Boot Test 和 Testcontainers。Surefire 注入 `spring.profiles.active=test`。前端使用 TypeScript build、ESLint 和 Vite build。

质量门禁由本地 `scripts/ai-quality-gate.ps1` 执行，包含：

- 后端编译、目标测试或完整测试。
- 后端 package。
- 前端 lint/build、chunk budget 和路由懒加载检查。
- 安全扫描、Flyway 顺序、生成物、文档结构和工作循环契约。
- 知识命名守卫。

最近一次路线级全量验证为 KB-NAME-M11：后端 60/60，通过当时的空库迁移、后端 package、前端 build、安全审计、命名和文档门禁；后续已应用 V048-V052，新的 V001-V052 路线级全量验证按 KB-PRODUCT-M12 计划执行。

## 当前架构 Gap

- 用户工作台和管理后台仍共享一个 SPA 与后端进程，边界依赖路由、facade、权限和 DTO 纪律维持。
- 双协作节点已经消除单个 Hocuspocus 进程的房间内存依赖，但共享 Redis、Spring 后端和 PostgreSQL 仍是单点故障域。
- Base 尚缺公式、自动化、字段级和记录级权限策略。
- 通知矩阵已覆盖 Base 授权、审批细分动作和直接资源授权变化；M5-T09/T10 仍需完成集成与端到端验收。
- 平台对象 file resolver、对象关系全局图谱仍不完整。
- 正式桌面端、原生移动端、高可用部署和自动发布体系未交付。
