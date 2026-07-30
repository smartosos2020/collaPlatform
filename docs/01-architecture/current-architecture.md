---
title: 当前技术架构
status: active
last_code_check: 2026-07-30
---

# 当前技术架构

本文只描述当前代码和数据库事实。历史路线、迁移过程和阶段验证结果保存在 `docs/90-reports/` 与 `docs/99-archive/`，不在本文重复展开。

## 总体形态

Colla Platform 当前是模块化单体：

- 后端：单个 Spring Boot 应用，按业务模块分包。
- 前端：单个 React SPA，用户工作台和管理后台使用独立 Shell、导航和路由边界。
- 数据库：单个 PostgreSQL schema，通过 Flyway V001-V141 演进。
- 基础设施：Redis、MinIO、WebSocket、平台对象、权限、事件、审计和搜索由模块共享。
- 交付：本地 Docker 依赖；生产基线是 maintenance、双 API、Worker、Event Gateway、双协作节点的 Docker Compose + Nginx。

当前不拆微服务、不拆前后端仓库，也不把管理后台复制成第二套后端服务。

## 后端模块

后端代码位于 `server/src/main/java/com/colla/platform/modules`：

| 模块 | 当前职责 |
| --- | --- |
| `identity` | 登录、成员、部门、用户组、角色、权限码和设备 |
| `workspace` | 当前用户工作台聚合 |
| `im` | 会话、消息、已读、反应、链接和消息转事项/知识内容 |
| `project` | 项目空间、成员、空间级工作项类型定义与版本、研发预置补齐，以及 legacy 项目与事项、工作流、BUG 验证、评论和关系 |
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

### 可重复架构清单基线

`pnpm architecture:inventory -- --compare-ref ee8fb6883ac5868976cb261a25ab6d4972c33981 --expectation-path tools/workbench/config/platform-scale-s01-m1-baseline.json --label platform-scale-s01-m1-architecture-inventory` 是当前架构清单的跨平台入口。命令以 schemaVersion 1 输出稳定 JSON/Markdown 到 `.local-reports/`，并在冻结计数漂移时失败。

提交 `134c3706db280f364eeffd7ae44526b6b1d6180d` 的 S01-M1 历史冻结事实为：15 个后端模块、233 个 Java 文件、204 条跨模块 import、47 条 foreign infrastructure import、58 个模块方向、16 个跨模块事务文件和 3 条 shared 反向依赖；前端为 16 个 feature、64 条跨 feature import、23 个涉及文件、40 个方向，现有 SCC 为 `knowledgeBases <-> search`；当时 V001-V065 计算得到 85 张有效表、96 个跨 owner 文件-表候选和 41 个动态 SQL 人工复核候选。该段只用于解释 S01 基线，不是当前计数。

该历史快照只描述当时的依赖和访问事实，不代表当前计数、边界合格或容量承诺。table owner、允许例外和公共 contract 在 S01-M2 冻结，自动失败门禁在 S01-M3 交付。

S01-M2 已接受 `platform-module-contracts.md`，并以 `platform-modules.json`、`platform-table-owners.json`、`platform-boundary-exceptions.json` 和 `pnpm architecture:contracts` 建立模块、table owner、精确只读例外及 identity/file/platform/event/audit/IM 公共合同。2026-07-27 在 V001-V101 基线执行当前工具确认 15 个后端模块、150 张当前有效表、93 条精确例外和 29 个公共合同文件。150 张表在 owner manifest 中全部唯一归属，重复与 ownerless 均为 0；fresh inventory 以本地工具结果为准。

## 前端模块与路由

前端代码位于 `web/src`。`web/src/app/router.tsx` 是路由事实来源。

用户工作台路由：

- `/`：工作台。
- `/im`：消息。
- `/project-spaces`、`/project-spaces/:spaceId`、`/project-spaces/:spaceId/members|settings|types/:typeId?`：项目空间协作、成员治理、空间设置和 owner/admin 类型配置。
- `/projects`、`/projects/:projectId`、`/issues/:issueId`：legacy 项目与事项。
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
- `/admin/project-spaces`、`/admin/project-spaces/:spaceId`
- `/admin/audit-logs`

边界规则：

- 用户工作台使用 `UserWorkspaceShell`，管理后台使用 `AdminConsoleShell`。
- `/admin/*` 必须经过 `RequireAdmin`，且后端管理 API 仍需独立鉴权。
- 用户工作台不展示组织、权限和审计治理页面；后台不复用 IM、项目、知识内容或 Base 的用户协作页面作为主体。
- 共享组件只承载低业务语义 UI；用户页面和后台页面不得相互引用页面级样式。
- `projectSpaces` 用户模块只调用 `/api/project-spaces` 协作 API；`AdminProjectSpacesPage` 只调用 `/api/admin/project-spaces` 治理 API，后台页面不复用用户空间页面。
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

当前 Flyway 版本为 V101。历史迁移文件不可修改。

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
| V054 | 增加知识内容评论锚点生命周期 |
| V055 | 增加知识内容模板版本合同 |
| V056 | 建立项目空间、成员、角色分配、邀请、legacy 映射和迁移批次底座 |
| V057 | 强化空间成员/角色同空间外键、邀请目标约束、版本和请求幂等索引 |
| V058 | 为 legacy 映射补充批次归属列，支持按批次校验与回退 |
| V059 | 允许已回退映射的空间引用置空，解除回退删除空间的外键阻塞 |
| V060 | 把 legacy 映射到迁移批次的引用改为 workspace 复合外键，阻止跨 workspace 的 map-batch 归属 |
| V061 | 建立 workspace/space 隔离的工作项类型定义与不可变版本底座、生命周期约束和系统标记 |
| V062 | 建立工作项类型命令回执，承载 request id 幂等与响应重放 |
| V063 | 强化系统预置不可覆盖/retire/删除，并为 legacy 整空间迁移回滚提供 transaction-local 清理通道 |
| V064 | 建立 workspace/space/type 隔离的工作项字段定义与命令回执，包含永久 key、生命周期、配置 hash、排序、乐观版本、复合外键和身份保护触发器 |
| V065 | 建立字段选项子聚合，包含 workspace/space/type/field 复合归属、永久 option key、显示属性、启停状态、排序索引和不可删除/不可改身份触发器 |
| V066 | 对有效直接用户角色分配增加唯一约束，保证多实例管理员初始化幂等 |
| V067 | 建立版本化 domain event envelope、逐 Handler delivery/receipt 和 aggregate sequence |
| V068 | 增加 delivery lease、fencing、失败分类、dead letter、replay 和不可变操作历史 |
| V069 | 建立对象级搜索投影版本和 durable realtime signal |
| V070 | 增加 signal transport delivery、稳定 realtime envelope、发布状态和索引 |
| V071 | 删除活动旧 Spring 协同 room/presence/update/snapshot 状态，保留 Hocuspocus durable 模型 |
| V072 | 使 collaboration ticket 单次原子消费，阻止重连复用已使用 ticket |
| V073 | 为容量验证事件探针运行记录增加查询索引 |
| V074 | 为知识内容块父节点关系增加索引 |
| V075 | 为知识库目录父节点外键增加索引 |
| V076 | 为大规模知识库外键关系补齐索引 |
| V077 | 建立 workspace/space/type 隔离的 create/detail 布局、节点、字段访问策略和命令回执，包含永久标识、同域字段引用、规范 hash、乐观版本、索引和身份保护触发器 |
| V078 | 为布局命令回执增加 schema 版本、聚合版本、配置 hash 和不可变响应 JSON，完成命令后冻结原始响应并保护已完成回执不可更新或删除 |
| V079 | 为 BUG 验证日志增加数据库单调序号，消除墙钟回拨或同精度时间戳导致的历史顺序不确定性 |
| V080 | 建立完整配置草稿、草稿命令回执与 legacy 草稿诊断底座，并以不可变触发器保护终态草稿和回执 |
| V081 | 退役 `project_work_item_type_versions` 的 legacy `draft` 状态，只保留不可变 published/superseded 历史版本 |
| V082 | 扩展完整配置版本 lineage，新增原子发布命令回执并强化 published version、draft identity 和已完成回执不可变保护 |
| V083-V085 | 建立配置模板版本、安装/升级历史并完成空间配置清理闭包 |
| V086 | 建立规范工作项、类型计数器和持久命令回执 |
| V087 | 建立类型化字段投影、参与者与不可变活动账本 |
| V088 | 建立事项迁移 batch/unit/manifest/failure、显式 legacy map、cutover 与 shadow compare；S21-M2 后仅保留为历史/恢复兼容证据 |
| V089 | 完成迁移 plan/lease/fencing、规范评论/附件、来源 provenance、独立 verification 与不可变迁移历史 |
| V090 | 允许系统预置类型通过既有完整配置发布事务推进 current version，同时保持系统身份不可改 |
| V091 | 建立状态流 current state、持久命令回执与不可变 workflow history 的 workspace/space/WorkItem 复合边界；不创建节点实例或 token 表 |
| V092 | 建立显式状态 backfill batch/unit、不可变 manifest/source binding、失败续跑与 verify 事实，并以受控会话触发器保护 current-state binding upgrade；不创建节点实例或 token 表 |
| V093 | 建立节点流独立 instance、token、task、vote、join、command receipt 和不可变 history 基础表；只提供 schema 权威与保护，不激活节点运行命令、Repository、API 或 UI |
| V094 | 激活节点运行时的不可变 vote supersession/withdrawal 链、join arrival 事实、任务候选与开放任务查询索引，并扩展命令操作约束；不复用 S08 状态流私表 |
| V095 | 冻结节点 task 的候选用户、表单、交付物与 UTC elapsed 时间合同，建立不可变 task artifact、收件箱/到期索引及 transfer/submit/timeout 历史和命令约束 |
| V096 | 建立节点恢复补偿 run/step、显式 backfill batch/unit、instance recovery 水位与受控 binding upgrade；冻结 manifest/source binding，保护实例身份并禁止绕过升级命令直接改绑定 |
| V138 | 建立 append-only legacy 退出审计快照、finding 与 removal decision |
| V139 | 撤销旧 Project/Issue 活跃产品注册和派生索引，同时保留迁移、审计与恢复证据 |
| V140 | 建立 workspace/space/user 隔离的项目空间体验模式偏好 |
| V141 | 建立 workspace/space/user 隔离的项目空间 onboarding 状态与低敏引导事件 |

数据库规则：

- 新表、列、约束或索引只能通过新的 Flyway 文件增加。
- 不建立旧表兼容视图，不恢复旧产品 objectType。
- 迁移必须同时支持空库安装和存量升级；破坏性迁移必须有备份、恢复和数据一致性证据。
- 集成测试使用 Testcontainers PostgreSQL，不向共享本地开发库写测试夹具。

## API 边界

| 边界 | 主要前缀 | 规则 |
| --- | --- | --- |
| 用户协作 | `/api/workspace`、`/api/conversations`、`/api/project-spaces`、`/api/knowledge-bases`、`/api/bases`、`/api/approvals`、`/api/notifications`、`/api/search` | 返回当前用户可见和可操作内容，不夹带后台治理字段；项目产品只使用 canonical `project_space`/`work_item` 合同，空间类型配置只对空间 owner/admin 开放，成员摘要只返回 active 展示语义 |
| Legacy 定位兼容 | `/api/compat/work-items/legacy/issues/{issueId}/location`、`/api/project-spaces/legacy-resolve/{legacyProjectId}` | 只在重新鉴权后返回站内 canonical 相对位置或统一不可用状态，不返回旧 Project/Issue DTO、列表、搜索、写入口或产品事实 |
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

当前项目对象类型只包括 `project_space` 与 `work_item`；其他活动类型包括 knowledge_content、base、base_table、base_record、message、approval 和 file。内部链接、IM 卡片、通知、最近、收藏、关系和搜索结果复用同一对象摘要。旧 `project`/`issue` 产品注册已由 V139 退出，不能作为选择、搜索、写入或业务读取对象恢复；历史标识只允许经受权 location compatibility 解析到 canonical 相对路径。

用户搜索：

- `GET /api/search` 固定返回 `searchScope=user_content`。
- 覆盖事项、知识内容、Base、数据表、记录、消息和审批。
- SQL 召回阶段和 resolver 返回阶段都执行可见性检查。
- 知识内容命中可携带 block/comment 定位信息。

后台治理搜索走 `/api/admin/search-governance`，索引重建只允许通过 `POST /api/admin/search-governance/reindex` 的管理 facade 执行；用户搜索 API 不暴露治理写动作。

个人通知只消费用户协作通知；后台治理通知不得进入普通用户列表、未读数或用户侧已读写操作。通知偏好按来源持久化，资源授权与安全类必要通知不可关闭。

## 实时事件与协同

- WebSocket 统一入口为 `/ws/events`。
- 事件由独立 `worker` 角色中的 `ReliableDomainEventWorker` 从持久化事件与逐 Handler delivery 表消费；通知、搜索和 realtime signal 经版本化 registry 分派。
- S04-M1 已建立版本化通用 realtime envelope、PostgreSQL signal/transport 两段 delivery、Redis pub/sub 和双 Event Gateway 本地 fanout。
- S04-M2 已将通知、IM、项目、权限、角色和身份变化迁移到 durable signal pipeline；业务模块不再直接依赖本地 sender/session。Gateway 只发送最小失效提示，PostgreSQL 与 REST 校准仍是事实来源。
- S04-M3 已建立单一前端 realtime provider、严格 v1 parser、连接状态机、指数退避、按 sequence scope/key 的 watermark/去重/gap 检测，以及通知、IM、项目、权限和身份的 REST 校准。账号、workspace、设备或标签页上下文变化会重建连接并清理旧缓存水位。
- 知识内容使用 Hocuspocus/Yjs 协议统一标题、正文块、presence 和远端选择区。
- 两个协作节点通过 Redis 广播房间更新；每个节点使用唯一 node ID，Nginx 以 least-connections 分配连接。
- PostgreSQL 的 Yjs snapshot 和去重 update log 是恢复事实来源；Redis 只负责瞬时广播、awareness 和协调。
- Spring 只在 API/combined 角色提供短期权限 ticket 与 secret 保护的 load/store/invalidate/health gateway；`/ws/events` 不承载知识编辑，收到旧知识命令时只返回 `protocol.upgrade_required` 或按显式 observe 策略记指标。
- 每个知识条目拥有持久化 `collaboration_generation`。invalidate 会原子递增 generation 并清除旧状态；update、snapshot 与 update id 唯一键均绑定 generation，旧节点或在途请求不能覆盖新代状态。
- snapshot 必须携带它实际包含的 `snapshotSequence`，数据库拒绝错误 generation、落后于当前 durable update 水位或倒退的 snapshot。两个 collaboration 节点可按顺序尝试多个 API 地址，readiness 同时验证内部 gateway 协议与 schema。
- 浏览器短暂离线可在有限更新数和字节预算内继续编辑，恢复时交换缺失状态；超限前可导出 `.yjs` 恢复副本。

## 文件与对象存储

文件模块使用 MinIO，负责上传会话、完成确认、下载 URL 和文件使用关系。业务模块只能通过 file service 使用文件能力，不自行拼接 MinIO URL 或直接操作 bucket。

## 交付与运行

本地依赖由根 `docker-compose.yml` 提供 PostgreSQL、Redis 和 MinIO。开发启动见 `docs/05-runbooks/local-dev.md`。

生产基线位于 `deploy/`：

- `docker-compose.prod.yml`：PostgreSQL、Redis、MinIO、maintenance、双 API、双 Worker、双 Event Gateway、两个 collaboration sidecar、Web 和 Nginx。
- `nginx/colla.conf`：Web、API、事件 WebSocket、双节点知识协同和健康检查代理。
- `pnpm ops:backup`、`ops:restore`、`ops:restore-drill`：备份、恢复和恢复演练。
- `pnpm ops:health`：健康与可选 Prometheus 检查。
- `pnpm ops:release-check`、`ops:rollback`：发布检查和显式回滚。

当前 API、Worker、Event Gateway 与知识协同进程均有双节点生产模板。S04-M1 已验证两个 Gateway 无粘性分流、优雅/强制退出、恢复和单节点回退；PostgreSQL、Redis 和 MinIO 仍为单实例，因此不承诺整个平台高可用、自动扩缩容或 Kubernetes。

## 测试与质量门禁

后端使用 JUnit 5、Spring Boot Test 和 Testcontainers。Surefire 注入 `spring.profiles.active=test`。前端使用 TypeScript build、ESLint 和 Vite build。

质量门禁由 `tools/workbench` 提供并通过 `pnpm verify` 执行，包含：

- 后端编译、目标测试或完整测试。
- 后端 package。
- 前端 lint/build、chunk budget 和路由懒加载检查。
- 安全扫描、Flyway 顺序、生成物、文档结构和工作循环契约。
- 知识命名守卫。
- `architecture:inventory` 可重复架构清单及冻结基线漂移检查。

PLATFORM-SCALE-S01-S04 已完成并归档。S03 交付版本化 envelope、聚合 sequence、Handler registry、逐 Handler delivery/receipt、payload 防线、lease/fencing、dead letter/replay、有界背压、双 Worker 部署与故障接管；S04 交付双 Event Gateway Redis fanout、统一客户端 sequence/重连/REST 校准、唯一 Hocuspocus/Yjs 知识协议及双 collaboration durable recovery。PLATFORM-SCALE-S05 仅完成 M1 容量环境、确定性种子、HTTP/WS/协作/Worker 加载器和证据合同底座；M2-M5 的真实目标容量、长稳、故障恢复、发布扩缩容和最终 Go/No-Go 压测已明确 Deferred，恢复前置是核心功能、接口、数据模型和负载模型足够完整稳定。

## 平台模块边界当前事实

PLATFORM-SCALE-S01 把模块边界从文档约定升级为机器门禁。2026-07-26 的当前可重复清单为：

- 15 个后端模块、418 个纳管 Java 文件、266 条后端跨模块 import。
- 前端有 64 条跨 feature import。
- V001-V101 形成 150 张当前有效表，每张表有唯一 owner；跨 owner SQL 候选由精确例外治理，foreign write 仍为禁止项。
- 27 个公开 contract 文件受合同门禁约束；`pnpm architecture:contracts` 同时检查模块、table owner、例外和公共合同来源。
- S02 收口复核确认 93 条只读例外不属于运行隔离交付范围，退出 Stage 已重新批准为 PLATFORM-SCALE-S05，其中 project 9 条；因 S05-M2-M5 Deferred，例外清理尚未完成，现有精确条目仍不能扩张，修改相关文件时只能保持或减少。

以上事实来自当前 inventory/config，而不是沿用 S03 历史快照。S02 已实现双 API 和独立运行角色，S03 已交付 Worker 多实例 lease、逐 Handler 可靠消费和恢复门槛，S04 已交付双 Event Gateway fanout、业务信号迁移、前端重连校准、旧 Spring 协同退出和双 Hocuspocus 节点恢复。基础设施集群高可用与正式容量承诺仍未交付；PLATFORM-SCALE-S05 的 M2-M5 已 Deferred。PROJECT-PLATFORM-S07 至 S10 均已完成并归档。复杂节点流当前包括 snapshot v3 阶段/节点/边/分支/汇聚/恢复/补偿定义，V093-V096 独立 instance/token/task/artifact/vote/join/receipt/history/backfill 权威，且不读写 S08 私表。S10 通过 snapshot v4 与 V097-V100 交付关系、层级、影响和显式迁移闭环。S11-M1 已把 configuration snapshot 升至 v5，并由 V101 建立分层 role binding、WorkItem role assignment、permission receipt 与 immutable decision evidence 定义底座；M2 已激活只解释绑定 snapshot 的统一单项/批量 decision，并接管 WorkItem、子资源、状态流、节点流和关系的主要运行入口。字段、节点、关系 key 与 data scope 的组合裁剪、治理 API 和 UI 尚由 M3-M5 承接。

## 项目模块当前事实

PROJECT-PLATFORM-S01 于 2026-07-18 完成项目模块当前事实审计、目标领域合同、迁移技术 spike 和 S02 准入评审。S02-M1 随后通过 V056 和 `ProjectSpace` 后端模块交付空间生命周期与可见性底座；详细证据见 `docs/90-reports/project-platform-s02-m1-execution-report.md`。

- `project_spaces` 是新空间事实表；`project_space_members` 与 `project_space_role_assignments` 分离成员身份和当前内置角色。成员、角色和邀请业务已由 `ProjectSpaceMembershipService` 实现；legacy 映射与迁移批次的执行能力已由 S02-M4 交付（见下）。
- S02-M4 已交付 legacy 数据画像/预检：`GET /api/admin/project-migrations/profile` 由 `ProjectLegacyProfileService` 按调用方 workspace 汇总活跃/归档项目与成员数量、角色分布、孤立成员（用户缺失/已删除/已停用/跨 workspace）、非法角色、重复 owner、共享 IM 会话、无 owner 项目、双向 IM 成员漂移和缺失/归档/类型不符会话，每类报告总数并对明细截断；入口要求 `project.manage`，画像动作写审计 `project_migration.profiled`。该能力只读 legacy 数据，不写 `project_legacy_space_maps` 或 `project_space_migration_batches`。
- S02-M4 已交付 legacy project 到 space/member 的迁移执行能力：`ProjectLegacyMappingService` 计算确定性映射（space id 为 `UUID.nameUUIDFromBytes("colla:project-legacy-space:" + projectId)`，key 由 project_key 规范化，冲突时追加确定性 hex 后缀；owner/member/viewer 映射为 owner/member/guest，未知角色与孤立成员进入失败清单且绝不写入空间）；`ProjectSpaceMigrationService` 提供 dry-run、execute、resume、verify、rollback 状态机，批次记录水位、source/result SHA-256 校验和、summary 与 failures，每个 project 一个独立单元事务，同 workspace 批次由行锁串行化；rollback 只删除本批次新模型产物（角色分配、成员、空间、对象链接）并将映射置 rolled_back，legacy 表永不修改。
- 2026-07-22 第三轮复审补验明确批次生命周期归属：`summary.projects` 只表示最近一次尝试，`summary.manifestProjects` 跨 resume 合并并永久保留本批次创建产物的 `CREATED`、`spaceId` 与 `ownedByBatch=true`；verify/rollback 依据生命周期归属判断，因此 A 批次先部分失败、resume 后回退、B 批次重迁时，verify(A) 会为 A 的每个历史产物报告 `MAP_SUPERSEDED`，不会因 resume 把旧产物标成 `REUSED` 而漏报。真实服务集成测试通过 `REQUIRES_NEW` 并发写直接验证生产 `REPEATABLE_READ` 事务边界；无污染 rehearsal v4 对 legacy 四表执行前后 SHA-256 快照并证明完全一致。2026-07-19 第二轮复审补验已将映射计划与输入指纹放入同一个 REPEATABLE_READ 事务（持有 workspace 迁移锁）内读取，dry-run 使用 REPEATABLE_READ 只读事务；workspace 收敛验证独立为 `POST /api/admin/project-migrations/workspaces:verify-convergence`（`MAP_MISSING`/`MAP_UNEXPECTED`，不写任何批次 summary，独立审计 `project_migration.convergence_verified`）；指纹摘要覆盖成员用户状态且每个迁移单元写入前拒绝过期快照，rollback 追加保留失败清单，V060 使用 workspace 复合外键约束 map->batch。
- 迁移治理由 `/api/admin/project-migrations` 承载：画像、批次列表/详情、`spaces:dry-run`、`spaces:execute`（确认串 `EXECUTE`）、`batches/{id}:resume|:verify|:rollback`（确认串 `ROLLBACK`）；全部端点复用 `project.manage` 最小权限并写批次级审计。用户侧 `GET /api/project-spaces/legacy-resolve/{legacyProjectId}` 提供旧深链兼容解析：`mapped` 只返回 spaceId（不返回名称），private 非成员返回 `unavailable`，未迁移返回 `unmigrated`，映射失效返回 `failed`；legacy 项目页在 `mapped` 时展示迁移提示条并可跳转新空间。
- S02 兼容边界已冻结：legacy project/issue 业务写仍走 legacy 表，迁移不产生双写也不切换写路径；`ProjectLegacyWriteBoundaryIntegrationTests` 以行数与内容哈希断言 legacy 写不触碰新模型五表。完整 WorkItem 迁移责任属于 S07。
- 用户协作 API 位于 `/api/project-spaces`，空间设置 API 位于 `/api/project-spaces/{spaceId}/settings`，企业治理 API 位于 `/api/admin/project-spaces`。三者使用独立 DTO/权限语义。
- 用户 Web 主入口位于 `/project-spaces`，以本地最近访问顺序和服务端可见空间列表组成目录；空间详情、成员和设置使用可刷新深链。旧 `/projects`、`/issues` 路由继续服务 legacy 业务，不与新空间 API 混写。
- `ProjectSpacesPage` 承载用户空间 Shell；owner/admin 才渲染成员与设置入口。`AdminProjectSpacesPage` 只承载企业状态治理、权限解释和审计深链，不提供空间协作正文或成员操作。
- 空间设置由有效 `owner/admin` 空间角色控制；企业治理由 `project.manage` 控制，但企业管理员不是空间成员时不能通过用户 API 读取私有空间，也不会取得内容访问权。
- 空间 Repository 所有查询均带 workspace 边界；V056 使用复合外键阻止跨 workspace 的空间成员、角色、邀请和 legacy 映射关系。
- `project_space` 已接入平台对象摘要和审计。创建、修改、停用、恢复、归档记录 actor、对象、状态前后值、来源、请求路径和 request ID。
- S03 已建立 `project_work_item_types` 与 `project_work_item_type_versions`：类型按 workspace/space 隔离，`type_key` 在空间内永久唯一；创建类型时原子生成首个 published 骨架版本，published/superseded 行受数据库触发器保护且不可改写。配置 API 只允许空间 owner/admin 写，member/guest 只读取 active 摘要，普通非成员和仅有企业治理权限的管理员均不能读取私有空间配置。
- `development-v1` 六类系统预置 `project/requirement/task/bug/iteration/release` 随新空间事务安装，并在启动时逐空间补齐既有 active 空间。自定义同 key 冲突保留人工治理，不覆盖用户定义；冲突空间不会阻断其他空间补齐。系统类型允许停用、恢复和排序，拒绝改键、覆盖、retire 和删除。
- S04-M1 已建立 `project_work_item_field_definitions` 与 `project_work_item_field_commands`：字段归属 workspace/space/type definition 复合边界，`field_key`、字段类型和系统标记创建后不可变；active/disabled/retired、排序与 aggregate version 由领域服务和数据库约束共同保护。字段写命令使用 request id 幂等回执，审计和 domain event 与业务事务同成同败。
- S04-M2 已建立 `project_work_item_field_options`：option key 在字段内永久唯一，数据库禁止删除或改写归属/key；名称、颜色、顺序和 active/disabled 可在字段聚合命令内变更。现存 key 不能从提交中省略，调用方必须显式停用。
- `WorkItemFieldTypeRegistry` 是当前 11 类字段的唯一能力目录，返回 storage kind、config schema、value/type-config schema、operators、filter/sort/index、supportsOptions、validationRuleKinds、referencePolicy 和 invalidReferencePolicy。字段配置 schema version 1 统一包含 required、defaultValue、validationRules 和 typeConfig；规则也携带 schemaVersion，规范序列化后生成稳定 JSONB/SHA-256 hash。
- S04-M3 的复杂配置仍存放在字段定义规范 JSONB 中，不增加按字段动态列或引用快照表。`WorkItemFieldConfigCanonicalizer` 负责 user/date/datetime/url/attachment/work_item_reference 的闭合属性、数量、时区、精度、范围、协议、MIME 和方向规范化；`WorkItemFieldComplexReferenceValidator` 再按当前 workspace/space/actor 调用 identity、organization、user-group、file、platform-object 和 work-item-type 事实进行写入校验。API 只返回规范 ID/标量，不返回身份、文件或目标标题快照。
- S04-M4 前端通过 `workItemFieldKeys(spaceId,typeId,fieldId)` 隔离 catalog、列表和详情缓存；`ProjectWorkItemFieldsPanel` 只消费字段配置 DTO、类型目录 capability 与服务端 `availableActions`。路由使用 `/project-spaces/{spaceId}/types/{typeId}/fields/{fieldId?}` 保留空间/type 上下文，创建、编辑、配置、排序和生命周期命令继续落到同一字段聚合 API。筛选和排序是当前已加载字段定义的确定性目录投影，不查询或伪造工作项字段值。
- 字段配置性能基线使用真实 PostgreSQL/Flyway 合成 120 个字段和 2400 个选项，配置目录 API 预算为 3 秒；查询计划必须保留 workspace/space/type/status 范围并走索引，复合字段索引结构由 schema 集成测试锁定。该基线明确禁止按字段动态 DDL 或动态值表。
- 复杂引用采用 `unavailable_without_snapshot`：缺失、停用、跨 workspace、跨空间、已删除或无权对象均不披露具体目标。附件继续由 file resolver 判定可用性；工作项引用当前只允许配置同空间目标类型和 outbound/deferred 能力，实例默认值必须为空，S07 前不创建关系、反向引用或 `work_item` resolver。
- S05-M1 已建立 `project_work_item_layouts`、`project_work_item_layout_nodes`、`project_work_item_field_access_policies` 与 `project_work_item_layout_commands`。create/detail 各自是独立图；节点永久 ID/key、父子关系、顺序、条件 schema、字段 `fieldId + fieldKey` 引用和策略 schema 均由规范化器及数据库约束共同保护。
- 布局保存使用完整图替换、aggregate version 和 request ID 命令回执；V078 后已完成命令冻结 schema 版本、聚合版本、配置 hash 和响应 JSON，相同请求精确重放原始响应且不重复产生审计/事件，异载荷复用或旧版本写入稳定冲突。V078 前没有响应快照的历史回执安全拒绝重放。审计与 outbox 只包含布局 kind、规范 hash、节点/策略数量和版本，不包含策略原文。
- 布局配置 API 为 `/api/project-spaces/{spaceId}/configuration/types/{typeId}/layouts/{layoutKind}`，只允许空间 owner/admin 读取和保存。member/guest 返回 forbidden，非成员和仅企业管理员按 not-found 最小披露。读取遇到缺失、key 不一致、disabled/retired 字段时保留原图并返回诊断，绝不静默重绑。
- S05-M2 在同一配置聚合上增加原子节点命令 API。section/tab/column/field/summary 的新增、复制、移动、重排、更新和确认删除均先构造完整候选图，再统一规范化、引用校验和事务保存；旧 aggregate version 返回冲突，相同 request ID 重放不重复执行。
- 条件显示 DSL 固定为 schema v1，支持字段或受限上下文 predicate、`all/any/not`、类型化操作符、8 层/64 表达式预算和无副作用内存求值。保存会拒绝非法字段、操作符和值、布局外隐藏依赖与条件循环；条件只影响显示，不授予字段访问。
- 前端 `/project-spaces/{spaceId}/types/{typeId}/layouts` 只对空间 owner/admin 展示，提供独立 create/detail、控件面板、紧凑布局树、属性/组合条件编辑、拖放/键盘操作、冲突保留重试和共享渲染预览。缓存键包含 space/type/layoutKind；成员身份没有配置入口或伪可用按钮。
- S05-M3 已实现 schema v1 字段访问策略、确定性求值器和脱敏布局投影。owner/admin/member、guest、non-member、enterprise admin 的服务端上限分别为 write、read、hidden 三层；disabled/archived/retired 资源继续收窄，企业管理员不自动获得空间内容访问。
- 正式读取 API `/api/project-spaces/{spaceId}/types/{typeId}/layouts/{layoutKind}/projection` 只返回可见节点、安全字段 DTO、逐字段决策与脱敏诊断；hidden 字段身份和原策略不出服务边界。策略专用写和 synthetic preview 继续位于 configuration API，复用版本、幂等和审计，预览严格无持久化副作用。
- `WorkItemLayoutRenderer` 现在只消费服务端访问投影；hidden 不渲染、read 禁用控件、required 显示必填，缺失投影按 hidden 失败关闭。管理 UI 支持六身份规则、危险收窄确认、角色/资源状态/字段样本预览，保留已有条件规则。
- S05-M4 增加 `GET /api/project-spaces/{spaceId}/configuration/types/{typeId}/layout-workbench` 配置集合读模型，在同一 repeatable-read 快照中组合类型、字段目录、create/detail 布局与服务端访问投影；字段选项按类型批量读取，避免按字段 N+1。配置与投影版本/hash 不一致时返回可重试冲突，不拼接混合快照。
- 用户侧 `POST /api/project-spaces/{spaceId}/types/{typeId}/layouts/{layoutKind}/sample` 只向有效空间成员返回当前身份的 synthetic 只读样本；member/guest 不获得配置读权限，non-member 与仅企业管理员仍按 not-found 最小披露。用户投影的选项 DTO 只包含 key、名称、颜色、排序和状态，诊断不返回管理细节。
- 管理预览和用户样本复用 `WorkItemLayoutRenderer`。当前 S04 注册的 11 类字段均有显式编辑/只读映射；rich-text presentation 复用 text 的多行模式，附件和工作项引用只呈现规范占位而不创建上传或实例事实。interval/computed 尚未进入 S04 注册表，遇到未知类型时失败关闭并显示安全的不支持状态。
- 布局配置基线在真实 PostgreSQL 中覆盖 120 字段与 2400 选项，集合读模型预算为 3 秒；冻结布局图最多 120 节点，因此合法最大渲染图为 1 个根 section 加 119 个字段节点。该结论只覆盖配置读取与合成渲染，不代表真实 WorkItem、动态值查询或平台容量。
- S05-M5 已完成 V065 存量升级到 V079、空库迁移、幂等回执、权限最小披露、复杂布局、浏览器身份矩阵和文档准入的最终收口；V079 同时稳定 BUG 验证历史的最新优先顺序。M1-M4 的布局与字段访问能力已经完整复验，Stage 可归档并进入 S06。
- S06 已交付唯一 active 配置草稿、完整规范快照、校验/hash、原子发布、不可变版本、版本历史、语义 diff、rollback-as-new-version、模板 lineage/三方合并和兼容分析。S04 字段与 S05 布局/策略 live 表只作为配置编排输入；发布物是自包含 snapshot，历史版本不回查 live 表。
- `PublishedSnapshotReader` 与 `PublishedSnapshotAdapter` 已冻结：只读取精确 schema v1、完整且 hash 一致的 published/superseded snapshot；legacy partial、未来 schema 和完整性失败均拒绝。runtime 对 active draft、live 字段/选项/布局/策略及模板服务的依赖由架构测试禁止。
- S07-M1 已由 V086 建立 `project_work_items`、按空间/类型锁定的展示编号计数器和持久命令回执。每个实例显式保存 workspace、space、type definition、不可变 type version 与 config hash；复合外键、唯一编号/展示 key、乐观版本和清理闭包由数据库约束保护。
- 用户协作实例 API 位于 `/api/project-spaces/{spaceId}/work-items`，支持创建、列表、详情、基础字段更新、归档和恢复。创建事务锁定类型当前版本，只经 `PublishedSnapshotAdapter` 解释完整 schema v1 快照；默认值、required/write/hidden 和详情最小披露均由绑定版本计算，ArchUnit 阻止实例命令或投影读取 active draft/live 配置。
- 实例命令将规范实例、精确响应回执、平台对象链接、审计和 `work_item.changed` outbox 放在同一事务。相同 request id/载荷精确重放，异载荷复用、陈旧 expected version 和跨空间组合 ID 稳定拒绝；enterprise admin 不因治理角色自动获得空间内容访问。
- S07-M2 已由 V087 建立 `project_work_item_field_projections`、`project_work_item_participants` 和不可变 `project_work_item_activities`。`project_work_items.field_values` 仍是动态值唯一权威；创建、更新时 snapshot codec 先规范 11 类注册值，再在同一事务替换可重建投影。unset 与显式 null 均表示删除，数组按集合去重排序，number/date/datetime/URL 采用稳定编码；未注册的 interval/computed 显式拒绝。
- 动态查询只接受绑定 snapshot 发布的字段能力和受控 `eq` SQL 模板，按 config hash 隔离不同版本语义。类型化列承担 text/number/boolean/date/timestamp，集合/引用使用 canonical hash；不允许调用方提供列名、SQL 或未发布操作符。投影漂移可从权威 JSONB 重建，重建不会修改实例版本或活动历史。
- 工作项创建者自动成为 owner。参与者角色限定 owner/assignee/collaborator/watcher；变更复用实例乐观版本、持久回执和用户 active 校验，最后一个 owner/assignee 不可移除。活动按实例单调序号追加且数据库禁止更新/删除；公共 payload 只含版本、状态或参与者定位，不保存动态字段前后值，因此 hidden 值不会进入活动、审计或事件。
- S07-M2 的用户 API 增加参与者、活动和受控字段查询；`work_item.changed` 已提升为 `project.contract.WorkItemChangedEvent` 公共合同，搜索/通知/协作只能消费最小定位事件并经 resolver/API 校准，不能读取 project 私表。本里程碑不提前实现这些模块的产品投影或 realtime handler。
- S07-M3 曾由 V088 建立事项级 migration batch/unit、append-only manifest/failure、显式 `project_legacy_work_item_maps`、workspace/space cutover 和 shadow sample。S21-M2 退出旧产品后，这些结构只供登记的 migration、audit、location compatibility 与 recovery owner 使用，不再是活动产品读写控制面。
- S07 阶段的 legacy/shadow/canonical-read 路由和旧读 kill switch 只属于迁移历史。V139 之后产品请求不能切回 legacy Project/Issue 列表、详情、搜索或写入，也不能借 V088 map/cutover 恢复双读双写；canonical 缺失或无权时必须失败关闭。
- 旧标识 resolver 目前只返回重新鉴权后的 canonical `project_space`/`work_item` 站内相对位置或统一不可用状态。跨模块消费者不得读取 V088/V089 私表，shadow hash、failure、verification 和 provenance 仍只是恢复审计证据。
- legacy 项目/事项写入口由同一兼容服务检查 cutover。旧写关闭后返回 `410 legacy_write_closed` 和 `Location`；不双写，回退也不允许 legacy 覆盖 canonical 新事实。M3 只冻结和实施读取/切流控制，真实分批数据搬运、verify/resume/rollback 属于 M4，用户完整竖切属于 M5。
- S07-M4 已由 V089 与 `WorkItemMigrationService` 落地真实分批搬运。plan 在单一 `REPEATABLE_READ` 快照中冻结 project 单元 manifest、目标 published snapshot binding、source/plan fingerprint 与预检清单；dry-run 只写迁移控制事实，不写 WorkItem、map 或平台对象。
- S08-M1 把轻量状态流作为完整 configuration snapshot schema v2 的可选部分。状态、动作、转换和 guard 使用永久 semantic key；canonicalizer 按 key 稳定排序，validator 对唯一 initial、可达性、终态/取消态、死路、悬空引用、声明式 guard、授权角色、必填字段、字段 patch 和受控副作用失败关闭。schema v1 继续表示“可解释但无状态流能力”，未知未来 schema 拒绝。
- 状态流定义只存在于 S06 `ConfigurationDraft.snapshot` 与不可变 published snapshot，不新增 live/published definition 表。live 字段、类型或布局变更刷新草稿时保留已有 `stateFlow`；diff、兼容分析、发布、rollback 和模板沿用同一快照/hash/乐观版本/幂等合同。六类研发预置使用显式确定性输入，自定义类型不猜默认流程。
- V091 的 `project_work_item_current_states`、`project_work_item_workflow_commands` 和 `project_work_item_workflow_history` 已由 S08-M2 Repository/命令服务激活。新建且绑定 schema v2 stateFlow 的 WorkItem 原子初始化唯一 initial state；旧无流实例显式返回 `not_configured/uninitialized`，不静默回填。current state 的 WorkItem version 会随规范实例的字段、参与者、评论、附件和生命周期命令同步，forward 动作再以 WorkItem 行锁、current-state 行锁和双版本 CAS 原子推进。
- `WorkItemStateRuntimeAdapter` 只接收 `PublishedSnapshotAdapter.requireComplete` 返回的绑定版本/hash；`WorkItemStateFlowDecisionService` 同时服务 `availableActions` 与 execute，覆盖字段、当前调用者参与者角色、空间角色、组合 guard 和 required fields。用户 runtime 已移除原始 `stateFlow` 策略正文，只投影当前 state、policy version 和已允许动作，不披露 guard 输入、未授权目标或 hidden 字段。
- 用户状态入口固定为 `/api/project-spaces/{spaceId}/work-items/{workItemId}/workflow`、`/history`、`/actions/{actionKey}`、`/corrections` 和 `/binding-upgrades`；空间级显式回填入口为 `/api/project-spaces/{spaceId}/workflow-backfills`。forward/return/reopen/terminate/restore 复用同一决策与事务，correction/binding upgrade/backfill 另要求 owner/admin、原因和危险确认。404/403/409/422 保持用户边界语义，企业管理员不会绕过空间成员关系。
- S08-M3 的 return 只执行 snapshot 声明的目标转换；reopen 只能从 terminal 到明示非终态，terminate 只能到 canceled，restore 只能从 canceled 到明示非终态。重复 request 精确重放，archive 时保留 current state 但隐藏 workflow actions，对象 restore 不猜测或改写业务状态。
- V092 的 `project_work_item_state_backfill_batches/units` 冻结 manifest、target binding、source binding/version、reason hash、attempt 和稳定 failure。每个单元以 `REQUIRES_NEW` 提交 WorkItem binding/字段投影/current state/system history/activity/audit/outbox；失败完整回滚后单独记 failure，可显式 resume 并通过 verify 检测漂移。
- `WorkItemWorkflowConsumerContractHandler` 订阅 action/state/initialized/binding-changed v1，只校验 `eventSchemaVersion=1` 的公共最小 payload，不读取状态私表、不生成通知或搜索正文。既有 delivery receipt/replay 去重；未知 payload schema 分类为 permanent，由通用 Worker 进入 dead letter。
- S08-M4 已在空间配置页交付状态/动作/转换/guard 编辑、diagnostics、preview/diff/compatibility、发布确认、rollback 与显式 backfill；WorkItem 详情只消费服务端 `availableActions`，并支持原子 required-field patch、历史和受控 correction。服务端发布拒绝 `blocked`，`migration_required` 要求显式确认，发布后的 active draft 从完整 current published snapshot 保留 `stateFlow`。
- S09-M1 把复杂节点流作为完整 configuration snapshot schema v3 的可选部分。stage/node/edge/branch/join、节点类型与 single/any/all/quorum/automatic 处理策略使用永久 semantic key；validator 对单一入口、终点、可达性、终点路径、非法环、悬空边、分支/汇聚闭包、声明式条件、候选角色和 quorum 失败关闭。schema v1/v2 继续可解释，未知未来 schema 拒绝。
- `nodeFlow` 复用 S06 唯一 draft、canonical hash、diff、compatibility、publish、rollback 和模板链路；同一 snapshot 不允许同时激活 `stateFlow` 与 `nodeFlow`。已发布或 active draft 的流程定义在 live 配置刷新时被完整保留，project/release 的确定性平台模板使用显式节点图，未知类型不猜默认流程。
- V093 的 `project_node_workflow_instances/tokens/tasks/votes/joins/commands/history` 与 S08 表族完全分离，V094 增加不可变 join-arrival 与 vote supersession/withdrawal 链及有界查询索引。M2 的 `WorkItemNodeRuntimeAdapter` 只解释 WorkItem 绑定的不可变 snapshot/version/hash；独立 Repository 以 WorkItem 行锁、instance/token/task/join 行锁、双版本 CAS 和 request ID/hash 事务运行，不读取或写入 S08 current-state/history/backfill 私表。
- M2 新建绑定 schema v3 nodeFlow 的 WorkItem 时原子初始化 instance 与 start token；自动节点以非递归 128 步上限推进，manual 节点支持 single/any/all/quorum 的 claim/delegate/complete/vote/withdraw。exclusive/parallel 分支使用内部 correlation，all/any/quorum join 使用不可变 arrival；提前汇聚会原子取消未完成分支任务/token，避免 stranded branch。
- 用户节点入口固定为 `/api/project-spaces/{spaceId}/work-items/{workItemId}/node-workflow`、`/history`、`:start` 和 `/tasks/{taskId}/actions/{operation}`。presentation 与命令共享同一授权/候选决策，只公开可操作 task/token/action、版本和最小 reason；候选角色、条件正文、quorum 配置、父 token、split/join/correlation 不出用户 DTO。
- 成功节点命令在同一事务推进 WorkItem/instance version，完成 receipt，追加不可变 history、WorkItem activity、audit 和 `node_workflow.changed` v1 outbox；相同 request ID/hash 精确重放，异载荷、stale 或并发败者不追加完成事实。开放任务按 `(node_key,id)` 稳定排序并硬限 200，active token 硬限 256，代表性 PostgreSQL 查询命中开放任务索引。
- S09-M3 在 task 创建时冻结 explicit user、space role、participant role 与 field participant 解析后的 active member 集合；后续入组/离场不改变 all/quorum 阈值。空候选保留 `assignment_empty` 恢复事实，空间 owner/admin 可通过 transfer 恢复，不以动态私表读取补算候选。
- 节点 form 的 hidden/read/write/required、artifact kind/count/required/object type 与 planned/due instant 均进入 task 冻结事实。`submit` 在单一事务执行字段 patch/canonical projection、FileAccess/PlatformObjectRegistry 公共鉴权、不可变 artifact、task/token/instance/WorkItem、history/activity/audit/outbox/receipt；任何一步失败整体回滚。
- 成员后端入口新增空间级 `/node-tasks` 收件箱与 `:process-due`，以及 WorkItem node-workflow task context。列表按 `(created_at,id)` 游标和 200 硬上限查询，详情只返回服务端允许的 form/value/artifact/action；原始 `nodeFlow`、hidden 字段、候选身份和文件私有元数据不进入 runtime。
- `NodeTaskLifecycleEvent` 发布 `node_task.lifecycle` v1 最小到期事实；`NodeTaskConsumerContractHandler` 与既有节点事件共用 durable delivery/replay/dead-letter 机制，只校验公共载荷，不读取节点私表。
- S09-M4 把 return、jump、terminate、correct、compensation 声明冻结进绑定 snapshot；恢复命令只接受 snapshot 明示来源/目标、owner/admin、双 expected version、10-500 字原因 hash、精确危险确认和 caller-stable request ID。旧 task/token/join 原子关闭，旧 history 永不删除，archive/restore 仍只改变 WorkItem 对象生命周期。
- 节点 binding upgrade 只接受 published/superseded 目标 snapshot 和覆盖全部 active source node 的显式 one-to-one/split/merge map；`blocked` 兼容变化、缺失/多余映射、非可执行目标或版本漂移均失败关闭。成功时 WorkItem binding/field projection、instance binding、mapped token、history/activity/audit/outbox/receipt 同事务提交。
- V096 的 compensation run/step 只执行已注册的 `record_audit_marker`、`close_open_work`，按顺序和 step 状态幂等续跑；空间 owner/admin 使用精确确认恢复 pending/failed ledger，不允许任意代码或直接改表纠错。
- pre-S09 实例只能通过 1-500 项显式 manifest backfill。batch 冻结目标 snapshot/hash/entry、source binding/version、reason/request hash；每个 unit 使用独立事务，失败完整回滚并记录稳定 failure，排除根因后显式 resume，verify 校验 WorkItem/instance binding、hash 和版本水位。
- 用户恢复入口固定在既有 WorkItem/空间边界的 `/node-workflow/recoveries/{commandKey}`、`/node-workflow:upgrade`、`/node-workflow/compensations/{runId}:resume` 与空间级 `/node-workflow-backfills`；企业管理员不会因后台角色自动获得空间内容可见性。M5 视觉闭环仍未实现。
- M4 真实隔离浏览器覆盖六身份、并发单赢家、离线保留、终态重开、业务恢复、backfill/verify、键盘、长名称与 1366/820；PostgreSQL 16 上 V001-V092 和非空实例恢复复验通过。这是功能与恢复证据，不是生产 cutover、容量或 HA 承诺。
- 每个 legacy project 是一个独立 `REQUIRES_NEW` 单元，覆盖 project/issue、成员/参与者、评论、附件、活动与 provenance。执行使用批次 lease、token、递增 fencing、heartbeat、限速、暂停和同批次 resume；失败单元完整回滚，兄弟单元继续，failure/provenance/verification 只追加。
- batch verification 固定针对原 manifest；workspace convergence verification 独立追加，不改写历史结论。pre-cutover rollback 删除本批次规范目标、链接和有效 map，但保留 rolled-back map、provenance、failure、manifest、verification 与审计；canonical-write 后拒绝删除并启用 kill switch，要求显式补偿。
- 企业治理 API 与跨平台 Node CLI 提供 plan/execute/pause/status/failure download/verify/convergence/rollback。入口只接受 `project.manage` 或 enterprise admin，workspace 复合边界隐藏外部批次；生产 cutover 仍需要目标环境备份恢复、观测、操作批准和独立变更窗口。
- 当前运行时查询预算覆盖真实 PostgreSQL 中 251 个实例、单个 text `eq` 条件和状态 `open` 投影、limit 50；分别证明命中 `idx_project_work_item_field_projection_text` 与 `idx_project_work_item_current_states_projection`。状态列表批量读取 current-state/actor-participant roles，并按绑定版本/hash 缓存 snapshot 解释；隔离测试预算小于 5 秒。该证据不代表 10 万实例、组合 filter/group、复杂节点流或生产容量；这些高级能力仍归 S09/S13 和后续真实容量验证。
- 成员治理以 `project_spaces` 行级悲观锁串行化同空间变更；成员唯一约束、活动角色唯一索引和邀请 pending 唯一索引承担最终数据库防线。直接加入、角色变化、移除、owner 转移和邀请状态变化均支持重复请求收敛。
- 邀请 token 使用 32 字节安全随机输入并只持久化 SHA-256 哈希；API/通知/审计只传 invitation ID。邀请过期使用独立事务持久化，避免业务 409 回滚过期状态。
- 身份模块停用用户前检查唯一 owner；离职流程通过显式 handover 在空间锁内转移唯一 owner，再停用原成员。企业操作人不被自动加入目标空间。

以下 legacy 运行事实仍保持不变：

- 当前模型是固定 project + 固定 issue type + Java 内置 workflow，不是可发布配置版本驱动的通用工作项平台。
- 项目运行时以 `project_members` 决定 view/edit；企业 `project.*` / `issue.*` 权限码没有被运行时消费，通用 project ACL 也不改变 `ProjectService` 的访问结果。
- `ProjectService` 已接入 IM、平台对象、事件、文件和审计，但 Workspace、Knowledge、Search 与 Admin 仍有直接依赖项目 Repository 或私有表的路径。
- 前端提供项目列表、事项看板/表格/抽屉和固定流程动作；项目成员、iteration、项目生命周期、附件上传及保存视图没有完整用户路径。
- 本地数据有 33 个项目、34 条项目成员关系、2 个事项和 0 个 iteration；未发现孤儿关系，但有 31 条项目成员未出现在对应项目群中的同步漂移。

S07-M5 已完成用户侧规范 WorkItem 竖切：类型入口、列表、创建、详情、绑定 snapshot 动态值、参与者、活动、评论、真实附件上传/下载以及归档/恢复均走 `/api/project-spaces/{spaceId}/work-items`；旧 issue 路由只通过显式 map 解析或重定向，不恢复 legacy 写。隔离 route-final 已覆盖六身份、PostgreSQL/Redis/MinIO、离线、409 冲突、1366/820 视口、迁移 verify 和 pre-cutover rollback。流程版本和高级可配置视图仍是后续目标架构能力；隔离环境迁移演练不等于生产切流批准。

S11-M5 已完成规范 WorkItem 分层数据权限闭环：configuration snapshot v5 是空间/事项角色、subject selector、allow/deny、字段/节点/关系限定和 data scope 的唯一配置权威；`WorkItemPermissionDecisionService` 统一服务 projection/query/execute。关系读模型携带每个端点自身的 config hash、字段值和创建者决策绑定，并以 relation key 校准 data scope；关系命令先锁规范 relation 再创建 FK 回执，避免并发 reparent 锁升级死锁。用户详情只返回 capability、安全 explanation 和字段访问投影，运行 snapshot 明确移除 permission model、状态/节点策略正文。owner/admin 治理入口仍要求空间成员内容边界，仅 enterprise-admin 身份返回最小披露。V001-V101、532 项后端测试、真实 PostgreSQL、六身份和 1440/1366/820 隔离 route-final 已通过。

S12-M1 已交付规范 WorkItem 个人聚合：`project.contract.PersonalWorkQuery` 是 workspace dashboard 的公共查询端口，project 内部按 participant 与 node task 生成 todo/responsible/participating/watching 多原因候选，再按绑定 snapshot、真实 participant context 和 S11 contextual decision 过滤后返回最小摘要、capability、签名 cursor 与受权 deep link。V102 的个人投影、失效水位和命令回执属于 project owner，均不保存标题、字段、权限策略或 subject 私有信息；`work_item.changed`/`node_task.lifecycle` 只触发已知投影失效，读取时重查权威事实。当前工作台已显示四类入口；recent/favorite、草稿/卡片、全局搜索和动态/提醒/催办/通知仍未由 S12 M2-M4 收口。

S12-M2 已收敛个人对象与卡片：platform 的 recent/favorite 仍只保存规范引用，REST 返回前逐项经 resolver 重校准并清理失效引用；收藏与卡片命令使用 caller-stable request ID、持久回执和布局 expected version。V103 的布局/命令表由 platform 唯一拥有，不保存对象正文或权限快照。project 通过 `DraftSummaryQuery` 仅公开当前用户在活动成员空间内更新的活动草稿摘要，workspace 不读 project 草稿私表。当前 Dashboard 目录包含 12 张可显隐、可排序卡片：原有四类个人工作、最近/收藏对象和本人草稿之外，最近事项、未读会话、审批待办、最新通知、最近知识内容和表格也使用同一全目录布局合同。读取历史 7-card 布局时保留原顺序与 hidden、追加 5 张默认可见卡并返回稠密位置，不在 GET 时写库；历史回执仍按旧 payload hash 优先重放，尚未持久化新目录的首次旧客户端更新可扩成 12 张，已持久化 12 张后再提交新的 7-card 命令必须 409 刷新。full replace 在同一 workspace/user 事务 advisory lock 内重读 expected version，确保并发保存 one-winner。Dashboard/Web 已支持本人草稿恢复、卡片显隐/排序、重复重放与多标签冲突刷新；全局搜索和动态/提醒/催办/通知仍由 M3-M4 交付。

S12-M3 已把规范 WorkItem 接入现有全局搜索：V104 只扩展可重建 `search_index_entries` 的空间、类型、状态和来源版本元数据，正文仅含 display key、标题与类型名，不复制 JSONB 字段、策略或 subject。project owner 实现 `platform.contract.PlatformSearchProjectionProvider`，search 不读取 project 私表，并以最多 200 项一批调用绑定 snapshot 的 S11 contextual decision，随后才生成可见页、facet、签名 cursor 与高亮；无权、已归档或 resolver 失效对象不进入响应。查询使用白名单筛选、500 候选硬限和稳定次序，管理员重建只操作投影，enterprise admin 不因治理身份读取私有命中。Web 已支持项目空间/对象类型/状态/参与角色筛选、分页和规范 WorkItem 深链；动态、提醒、催办与个人通知仍由 M4 交付。

S12-M4 已完成个人协作闭环：`PersonalCollaborationQuery` 提供仅针对当前可见 WorkItem 的动态分页/已读水位、node task 临期/到期/超期提醒、时区与提醒偏好、受权催办和可丢弃投影一致性扫描。V105 新增 project owner 的个人动态已读、提醒偏好和不可变催办回执，并为 notification owner 增加可撤销可见性标记；催办使用精确 request ID/hash、30 分钟冷却、接收者白名单、audit 与标准 `notification.created` outbox。通知列表和未读数在返回前通过公共 `PlatformSearchProjectionProvider` 批量重校准 WorkItem 可见性，失效通知不回显标题、数量或路径；活动已读和催办不伪造 notification realtime changeType，通知创建仍沿用既有最小 signal 与 REST 校准。Web 已交付动态、提醒偏好/派发、催办反馈、长名称及 1440/1366/820 响应式闭环。

S13-M1 已建立统一 WorkItem 查询 DSL：`QueryDefinition`/`FilterNode`/`SortSpec`/`GroupSpec` 固定 schema v1、注册 field/operator、5 层/24 节点/4 sort/200 candidate 硬限和稳定 SHA-256 语义 hash；动态字段进入查询前必须通过绑定 published snapshot 的 capability 校验。系统字段、participant、状态流、节点流、关系与 S10 hierarchy 由 project 公共只读投影边界组合，候选先经 S11 contextual decision，再形成筛选、排序、分组、聚合、计数与签名 identity-scope cursor。V106 建立 project owner 的查询定义、不可变回执和可重建投影统计；`/api/project-spaces/{spaceId}/work-item-queries:execute|:explain|:dry-run` 只返回受权字段和低基数计划信息。当前执行预算固定为 200 个候选，不声明生产 SLO；表格、紧凑列表、树和保存/共享视图仍由 S13-M2 至 M4 交付。

S13-M2 已在同一查询权威上交付表格与紧凑列表：schema v1 `ViewRequest`、注册 `ColumnSpec`、最小 `CellProjection` 和服务端 `availableActions` 不把 read-denied cell、隐藏行或策略正文带到客户端。V107 的用户偏好按 workspace/space/user/view key 乐观更新并精确重放；受控导出只保存不可变输入与 1 天 TTL，下载时再次执行当前成员、S11 decision 和列投影，并对 CSV 公式注入失败关闭。批量 archive/restore 最多 100 个选择，每个对象经规范 WorkItem 命令、独立 expected version 和稳定短幂等键执行，部分失败只返回安全 reason code。Web 已提供 table/list、密度、列配置、偏好、批量反馈和 CSV 下载；应用级 realtime/online/focus 只失效缓存并 REST 校准。当前查询有效页上限 100，真实隔离六身份 E2E 已覆盖越权隐藏、偏好重放、导出收权、批量恢复和窄屏；树与个人/共享保存视图仍未实现。

S13-M3 已交付 permission-scoped canonical hierarchy tree。`WorkItemHierarchyProjectionProvider` 是 S10 identity/depth 的最小公共投影；树服务只把它与 M1 query/decision 允许集合相交，不读取 hierarchy/relation 私表。隐藏父节点被无标记折叠到最近可见祖先，root/child/path/count/aggregate 只含可见节点；循环、自引用与 cursor 漂移失败关闭。预算为 200 个受权候选、32 层、50 项展开页和 64 个展开 identity。V108 只保存用户 relation key/展开 identity/版本与可重建统计，不保存 edge、parent、标题、结果或授权。Web 已提供懒展开、勾选、键盘/深链、table/list/tree 上下文保持和响应式长名称；批量与导出继续复用 M2。真实隔离 E2E 覆盖三层树、六身份、循环拒绝、离线与三个视口；个人/共享保存视图仍未实现。

S13-M4 已交付版本化个人/共享保存视图。V109 的 saved view、不可变 version、share 与 command receipt 四表均由 project owner 持有；视图只保存 schema v1 查询和 table/list/tree 展示配置，不保存结果、WorkItem 标题/字段、角色或授权快照。创建、更新、复制、分享、撤销、移交和删除使用 caller-stable request ID、request hash、expected aggregate version、稳定 identity、audit 与最小 `saved_view.changed` outbox；执行和 resolver 每次重新检查空间成员、当前分享以及 M1/S11 query/column/tree 权限。platform favorite/recent 只保留 `saved_view` 引用，不可见或删除后由 resolver 清理且不回显旧名称。Web 已提供目录、版本编辑、复制、收藏、分享、撤销、移交和删除；真实隔离 route-final 覆盖六身份、跨空间、并发 one-winner、收权、幽灵收藏清理、离线、长名称及 1440/1366/820。S13 四个 Milestone、48 个 Task 已完成并归档；S14 已激活但尚无实现事实，当前只允许从 S14-M1-T01 开始。

S14-M1 已交付 schema v1 WorkItem 看板。看板以 S13 受权 `QueryDefinition` 为唯一卡片集合，服务端生成稳定列、空列、泳道、可见计数、签名分页和最小卡片投影；动态分组字段必须通过 published snapshot capability，hidden/read-denied 对象或值不进入列、泳道、计数和错误外形。V110 的个人偏好、可重建排序、不可变移动回执和低基数统计由 project owner 持有；排序仅在来源 WorkItem version 与权威分组一致时恢复。拖拽只调用 S08 状态或 S09 节点公共命令，并以 request hash、expected WorkItem/order/instance version 提供精确重放、并发 one-winner 和收权失败关闭。Web 已提供 board、泳道、拖拽、键盘移动、WIP 展示/编辑与 1440/1366/820 响应式体验；真实隔离 E2E 覆盖六身份、跨空间、并发、撤权、离线和恢复。当前确定性上界为 12 列、24 泳道、100 卡、200 次 presentation 端口调用和 388 个投影容器，不声明生产容量。日历、甘特、基线与时间线仍未实现，下一入口为 S14-M2-T01。

S14-M2 已交付 schema v1 WorkItem 日历。CalendarView 只通过 S13 受权查询和 published snapshot 的 date/datetime capability 读取规范字段，按 IANA 时区派生月/周/日窗口、单日/区间/跨日/全天/无日期事件与最多 8 层重叠布局；DST 和 locale 不改写存储值，hidden/read-denied 对象不进入日期、数量、空隙、游标或错误外形。V111 的个人偏好、可重建窗口索引、不可变日期命令回执和低基数统计由 project owner 持有。移动/拉伸以 caller-stable request ID、request hash 和 expected WorkItem version 调用规范 WorkItem 更新，复用既有 validation、audit 与 outbox，并提供精确重放和并发 one-winner。Web 已交付月/周/日、字段与时区选择、拖放/键盘移动、区间调整、无日期区、离线输入恢复与 1440/1366/820 响应式布局。确定性上界为 62 天、100 个可见事件、8 个重叠层和 163 个窗口投影容器；不声明生产容量。甘特、关键路径、基线和时间线仍未实现，下一入口为 S14-M3-T01。

S14-M3 已交付 schema v1 WorkItem 甘特。甘特每次组合 S13 受权查询、M2 日期派生、S10 canonical hierarchy 公共投影和 identity-only dependency 公共投影；依赖双端必须同在可见集合，隐藏祖先只折叠到最近可见父级，不能通过行、线、空隙、数量或降级原因泄漏。V112 的个人偏好、展开状态、可重建排期索引和低基数统计由 project owner 持有，不复制标题、字段、关系、层级或授权。关键路径和浮动量是服务端有界派生，循环、缺日期和截断显式降级；日期移动/拉伸继续调用 M2 canonical mutation。Web 已交付层级展开、日/周/月缩放、依赖/关键路径、键盘排期、离线输入恢复与 1440/1366/820 响应式布局。确定性上界为 100 行、200 依赖线、32 层深度和 64 个展开节点；不声明生产容量。基线和时间线仍未实现，下一入口为 S14-M4-T01。

S14-M4 已交付 schema v1 排期基线、差异和最小时间线。V113 只冻结受权 WorkItem identity/version、日期、可见父级和依赖 identity/version；不冻结标题、字段值、角色、权限或隐藏对象，条目/依赖不可变，个人基线上限 20、单基线 100 条目/200 依赖、90 天保留。读取和 diff 先重跑当前 Gantt 受权集合，收权后的旧 identity、数量和标题不会回显。时间线通过 project owner 的 activity/workflow/relation 投影和 audit 公共合同组合，最多 200 项，可重建索引不成为新 history。Web 已交付基线创建/比较/删除、差异摘要、时间线和离线输入恢复。并行 Gantt/Timeline 暴露的 calendar/gantt/timeline 可重建索引竞态已改为复合键幂等 upsert。S14 四个 Milestone、48 个 Task 已完成；S15 尚未激活。

S01 已确定 ProjectSpace + versioned WorkItemType + WorkItem、规范 JSONB + capability typed projection、显式 legacy ID map、canonical-only write cutover 和状态/节点双运行时。S02-S06 已落地空间、类型、字段、布局/字段权限和不可变发布配置；S07 已落地规范 WorkItem、兼容 resolver、受控切流边界和可恢复第一阶段迁移。生产 canonical write cutover 仍需目标环境画像、备份恢复、观测窗口、容量证据和批准，不能由本地 rehearsal 自动授权。

## 当前架构 Gap

## S02 运行角色基线

- 同一 Server 构建产物通过 `colla.runtime.role` 选择 `api`、`worker`、`event-gateway` 或 `maintenance`；`combined` 只允许 local/test，生产缺失、未知、组合值或 combined 均启动失败。
- API 承载 HTTP 业务、事务命令、查询和 outbox append，不创建 `DomainEventWorker`、通用 WebSocket、旧知识协同 scheduler、建桶或管理员初始化 runner。
- Worker 只承载可靠 domain event polling、Handler 执行和 transport-neutral realtime signal 生产；不创建知识协同 room、presence、autosave、cleanup 或内部协作 gateway。
- Event Gateway 承载 `/ws/events`、JWT 认证解析、Redis subscriber 和本地 session registry，不创建可靠事件 Worker、业务 Controller 或知识协同协议。S04-M1 已通过共享 Redis 完成跨实例 fanout。
- Maintenance 执行 Flyway、MinIO bucket 和初始管理员等一次性初始化，全部 runner 完成后关闭应用上下文，不加入业务 upstream。
- scheduling 不再由应用根类全局开启，只在 worker/combined 的角色配置中启用。Actuator info、日志上下文和 Micrometer common tags 使用 role、instance id、version、commit 区分实例。
- `server.shutdown=graceful` 且 shutdown phase 最长 30 秒。M3 已验证 `api-a`/`api-b` 双 upstream、优雅/强制退出、恢复接流和单 API 回退；M4 又验证跨节点撤销、幂等、上传、初始化与 PostgreSQL/Redis/MinIO 故障恢复。
- API/Worker/Gateway 的数据库连接获取和校验超时分别显式收敛，PostgreSQL 故障时 readiness 在入口预算内返回不可用。Nginx 使用 Docker DNS 动态刷新 API、协作和 Event Gateway 地址，容器滚动重建后不会继续访问旧 IP。
- 上传完成以 PostgreSQL pending 记录和 MinIO object stat 为共同前置条件；对象不存在、大小不符、越权和重复完成有稳定语义。管理员初始化由 PostgreSQL advisory lock 串行化，直接管理员授权写入 `role_assignments` 主模型并由 V066 有效直接用户角色唯一索引兜底。
- API 的知识协同健康查询只读取 PostgreSQL collaboration state，不创建 room/presence/dirty snapshot 内存结构；旧 Spring `CollaborationMessageHandler`、维护 Worker 和内存协作服务已退出活动代码。
- `WebSocketSessionRegistry` 只在 event-gateway/combined 创建，并按 connection/user/workspace/device 建立本地索引。API 不持有本地连接；Worker 通过 transport-neutral publisher 发布，Gateway subscriber 只向本节点匹配连接 fanout。
- readiness 组由 `readinessState` 和 `runtimeRoleReadiness` 组成：流量角色要求 PostgreSQL 可查询，maintenance 固定 `OUT_OF_SERVICE`；liveness 只反映进程状态，避免依赖抖动触发重启风暴。
- 上下文关闭先发布 `REFUSING_TRAFFIC`；Worker 停止新 claim 后完成当前处理，Gateway 关闭现有 session 并由客户端重连校准。请求日志、Actuator info 和指标都携带 role/instance。

## S03 可靠事件基线

- `TransactionalOutbox.EventEnvelope` 是生产者唯一公共合同，包含 type/version、workspace、aggregate、actor、occurred/correlation/causation、idempotency 和规范化 payload。
- `domain_events` 以 PostgreSQL advisory transaction lock 分配同 workspace/aggregate 的单调 `aggregate_sequence`；相同 workspace/idempotency key 返回原事件，不创建重复事实。
- `DomainEventHandler` 与启动期 registry 以 handler key/version 和 event type/version 精确绑定；重复、空订阅和非法版本在启动时失败。
- `domain_event_handler_deliveries` 和 `domain_event_handler_receipts` 分别保存逐 Handler 投递与幂等结果；event/handler/version 由唯一约束保护。
- payload 递归拒绝密码、token、secret、access/private key 等敏感键，按 key 稳定排序并限制为 256 KiB；日志只输出标识、版本、workspace、aggregate 和 correlation，不输出 payload。
- 无匹配 Handler 的事件保留为可观察 pending 事实并记录 `domain_event_without_handler`；它既不会被静默标记成功，也不会进入无界重试。Handler 上线和历史回放必须走显式维护流程。
- M2 在 delivery 上增加 worker owner、claimed/heartbeat/lease、递增 fencing、attempt、失败分类、错误指纹、dead-letter、replay 和 abandon 事实；所有 owner 写入都同时校验 worker、fencing 和未过期 lease。
- claim 使用 `FOR UPDATE SKIP LOCKED`；有序 Handler 会等待同 workspace/aggregate/handler 的较小 sequence 终结，无序 Handler 可独立并行。
- transient/permanent/unknown 失败由唯一策略分类；退避带确定性抖动并受最大间隔/次数约束，错误摘要最长 2048 字符且赋值型敏感内容被遮蔽。
- dead-letter 只能通过受管理员访问约束的 inspect/replay/abandon 命令处理。每次操作要求 10-512 字符理由，同时写 `domain_event_delivery_replays` 历史和 `audit_logs`；历史失败不删除。
- `event` 的死信维护只依赖 `audit.contract.AuditLog` 追加审计；该 `event -> audit` 公共合同边在 S03-M2 经架构门禁增量接受，未开放对 audit 私有包或表的访问。
- S03-M3 新增显式开关控制的 `ReliableDomainEventWorker`：poll 只按执行器剩余容量 claim，Handler 经 registry 分发，任务拥有独立 heartbeat，异常只影响对应 delivery。
- 可靠 Worker 使用固定并发与有界队列；拒绝、draining 和超时停止均通过 owner/fencing 条件释放 delivery。readiness 同时反映 PostgreSQL、调度失败和 draining，liveness 保持进程级。
- 生产 Compose 使用同一不可变 Server image 的 `worker-a`/`worker-b`，不发布主机端口且不运行 Flyway。默认每实例并发 4、连接 6、CPU 1、内存 768 MiB；总连接守卫为 `expectedInstances * connectionBudget <= postgresqlConnectionBudget`。
- backlog、oldest age、processing、expired lease、retry、dead-letter、吞吐与处理时延进入 Micrometer；标签仅含 runtime role、instance、version、Handler 和 outcome，不含 workspace 或 payload。
- M4 已把 Notification、Search 与 realtime signal 迁移为 `notification.projection`、`search.projection`、`realtime.signal` 三个版本化 Handler。`DomainEventWorker` 只保留基于 registry 的 combined/test 兼容调度，不再包含业务 event type 分支，也不 import notification/search 私有 Repository 或 WebSocket session。
- 通知 Handler 尊重通知偏好，以业务 dedupe key 保证重复投递不重复落库；只有通知事实首次创建时才追加最小化 `realtime.signal.requested` 事件，因此 Search 失败不会阻塞通知，实时信号失败也不会回滚通知事实。
- Search Handler 只按 workspace、对象类型、对象 id、aggregate sequence 和 upsert/delete 操作更新单对象投影；`search_projection_versions` 拒绝旧版本覆盖新版本。普通查询和 Worker 不再触发全 workspace refresh。
- 搜索全量重建只允许管理员通过显式 Maintenance API 发起；批处理按 workspace、对象类型、cursor 和最大 250 条执行，使用事务 advisory lock、理由、限流和审计。旧 `/reindex` 仅保留为受保护的显式兼容维护入口。
- realtime signal 以 `realtime_signals` 保存 source event 唯一、版本化 envelope、audience、对象 sequence、correlation、最小 payload 和 `/api/...` 校准路径。V070 兼容升级旧行；S04-M1 增加独立 transport delivery，Redis 失败可重试且不回滚业务事实。
- 生产 realtime channel 默认 `colla:prod:realtime:v1`；Gateway 对未知版本、非法或超限 envelope 安全丢弃，并以有界近期缓存去重。每连接串行有界发送，慢连接或发送异常只关闭该连接。
- M1 的真实隔离验收在双 API、双 Worker、双 Gateway 环境中完成：同一 durable notification signal 经 `event-gateway-a` 与 `event-gateway-b` 各到达目标连接一次；两个 Gateway 分别经优雅停止和强制停止后，存活节点继续接入并在恢复后重新分流。
- M2 的真实隔离验收让同一成员分别连接两个 Gateway，并连续验证 `identity.invalidated`、`message.created`、`project.changed`、`permission.invalidated` 与 `notification.created`；两个连接得到相同 eventId/sequence，payload 不含标题、正文、ACL 或成员清单。
- M3 的真实隔离验收在生产构建中验证两个浏览器标签页断网、WebSocket 关闭、重新收到 `connection.ready`、通知和 IM 事实恢复；离线期间创建的项目工作项与资源授权也先经 REST 证明 durable。双 Gateway smoke 同时验证节点分布、优雅/强制退出、存活节点回退和恢复。
- API 角色不会创建可靠消费 Worker；生产消费要求 `worker` 角色且显式 `COLLA_EVENT_WORKER_ENABLED=true`。`combined` 仅用于 local/test 兼容，真实验收使用独立 API、Worker 和浏览器前端。

- 用户工作台和管理后台仍共享一个 SPA 与后端进程，边界依赖路由、facade、权限和 DTO 纪律维持。
- 双 API、双 Worker、双 Event Gateway 和双协作节点已消除单个 HTTP、异步消费、通用 WebSocket 或 Hocuspocus 进程故障；共享 Redis、PostgreSQL 和 MinIO 仍是单点故障域。
- S03 收口用 24 项、40 ms 固定 Handler 夹具验证横向恢复：单 Worker 1541 ms、双 Worker 755 ms，提升 52%；该结果只冻结当前部署形态的运行门槛，不是生产容量承诺。
- Base 尚缺公式、自动化、字段级和记录级权限策略。
- 项目模块存在成员权限、企业权限码、资源 ACL 与平台权限解释分裂；固定流程、并发事项编号、成员/项目群同步和跨模块私有表读取需要按 PROJECT-PLATFORM Program 收敛。
- 通知矩阵已覆盖 Base 授权、审批细分动作和直接资源授权变化；跨模块通知投影仍需随 Worker 独立化继续收敛。
- 平台对象 file resolver、对象关系全局图谱仍不完整。
- 正式桌面端、原生移动端、高可用部署和自动发布体系未交付。

## S15-M1 项目计划当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/project-plans` 提供计划列表、详情、创建与版本化 mutate；V114 保存计划、阶段、里程碑、WorkItem 链接、不可变变更和命令回执。
- 当前成员权限在每次读写时重新判定；guest 只读，空间外和 enterprise governance 均不可见。隐藏链接与失效责任人会在服务端投影时移除，不进入计数、进度或错误。
- 计划 mutation 使用 expected version 与精确 request hash replay，并在同事务写 audit 和 `project.plan.changed` outbox；Web 不把本地状态、realtime signal 或离线草稿当成提交成功。
- 当前只完成计划、阶段和里程碑。风险/问题/决策/变更台账、交付物/评审/验收和项目健康尚未实现，入口为 `PROJECT-PLATFORM-S15-M2-T01`。

## S15-M2 项目治理台账当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/project-register` 提供统一风险、问题、决策和变更列表/详情/命令；V115 保存类型明细、响应计划、受权引用、不可变历史和精确回执。
- guest 只读，空间外与 enterprise governance 隐藏；每次读取重新校准责任人及 WorkItem/Plan 引用，收权来源不进入数量或错误。
- 所有 mutation 使用 expected version 和 exact request hash，成功后写 `project_register.*` audit 与 `project.register.changed` outbox；批准变更通过 M1 服务执行 canonical plan command。
- 当前入口为 `PROJECT-PLATFORM-S15-M3-T01`；交付物、评审、会签、验收和最终健康聚合尚未实现。

## S15-M3 交付评审当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/deliverables` 提供交付物目录/详情、不可变版本、评审轮次、并发会签和验收命令；V116 保存七张 project-owned 表。
- platform resolver 与 M1/M2 公共服务负责物料及追踪授权；数据库只保存稳定 type/identity/version/URI。当前读取会移除收权物料、失效参与者和不可见台账追踪。
- 会签 required signer/quorum、撤销、关闭结论和验收全部由服务端判定；exact replay 不重复版本、签署、验收、audit 或 outbox。
- M4 将只聚合 M1-M3 当前可见事实生成项目详情与健康解释；S16 估分、工时和人员产能尚未实现。

## S15-M4 项目详情与健康聚合当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/project-detail` 聚合 M1 计划/偏差、M2 风险/问题/变更和 M3 交付/验收的当前可见最小事实；它只调用这些 owner public service，不读取其他 aggregate 私表或建立第二份计划/台账/交付权威。
- `ProjectDetail`、`HealthSignal`、`HealthStatus`、`Deviation` 和 `BlockingSummary` 固定 schema v1。每个健康信号携带稳定来源 identity/version、观测时间、规则和解释；计划/台账/交付达到 20/200/100 上界或信号超过 50 时显式返回 `unknown`，不伪装为完整结论。
- V117 保存用户/空间级详情区块与紧凑偏好、精确命令回执，以及五分钟可重建健康投影。投影只含低基数 status/count/policy/fingerprint，写失败不阻断 canonical 响应，且从不用于授权。
- 所有读写先验证当前活动空间成员，随后由 M1-M3 服务各自重校准来源权限；enterprise admin 和 non-member 无内容旁路，隐藏来源不进入标题、数量、信号、原因或错误外形。
- Web 已交付健康状态、阻塞计数、可解释信号、计划偏差、个人偏好、REST 校准和离线本地输入，并通过 1440/1366/820、六身份、跨空间、幂等及并发冲突真实隔离验收。S15 四个 Milestone、48 个 Task 已完成，S16 尚未激活。

## S16 归档与 S17 激活状态

S16-M1-M4 已交付 V118-V121 日历/例外/显式估分、实际工时/不可变修订、人员分配/产能规则、资源排期与 canonical 调整；Web 支持 1440/1366/820、离线输入、重叠条交互及六身份当前权限校准。S16 完成路线已归档，S17 在 Program revision 41 激活；S17-M1 已交付 V122 规则模型，当前入口为 `PROJECT-PLATFORM-S17-M2-T01`。

## S17-M1 自动化规则模型当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/automation` 提供 schema v1 规则目录、事件目录、操作目录、草稿保存、不可变发布和启停/归档命令。
- V122 保存 AutomationRule、不可变 RuleVersion、公共事件目录镜像、精确命令回执和低基数统计；所有表属于 project owner，规则不注册为新的 platform object。
- 条件仅接受最多 64 节点、8 层的声明式 all/any/not/compare，引用和操作符有固定 allowlist；script、SQL、code 和 template 形状失败关闭。
- 规则配置只允许当前 active 空间 owner/admin；member/guest 只读，空间外和 enterprise governance 不可见。exact replay、expected version、audit 与 `project.automation.rule.changed` 同事务。
- Web 已交付规则目录和 Trigger → Condition → Action 编辑器、REST 校准、离线本地输入及 1440/1366/820 响应式体验。M2 的 run/step、业务操作执行和 worker 尚未实现。

## S17-M2 自动化执行当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/automation/runs` 与规则 execute 命令提供 schema v1 的 AutomationRun、RunStep 和精确 ActionReceipt。
- V123 保存运行、步骤、操作回执和可重建统计；source、run 与 step 分层去重，运行绑定不可变 RuleVersion，并携带 attempt、lease 与 fencing 元数据。
- 字段更新、状态/节点流转和创建关联项分别调用 canonical WorkItem/Workflow/Relation 公共命令；通知只追加 `notification.created` 公共事件，不写 owner 私表。
- 手工执行限 owner/admin；当前成员只读运行历史，空间外与 enterprise governance 隐藏。dry-run 明确跳过全部业务副作用，错误只返回稳定代码。
- Web 已交付规则预览、真实执行、步骤结果与 REST 校准。M3 时间调度、M4 外部连接器仍未实现。

## S17-M3 时间触发当前实现

- V124 保存 schedule、cursor/lease/fencing 与唯一 fire receipt；触发只接受 cron、固定时间、临期、超期和停留时间声明。
- IANA timezone、DST、missed policy、cooldown、最多 20 次追赶和 200 个候选均为显式有界合同。M4 外部连接器仍未实现。

## S17-M4 连接器当前实现

- V125 保存连接器元数据/凭据引用、投递、不可变尝试、死信和精确回执；真实 secret 不入库。
- HTTPS 目标逐次执行 DNS/IP allow policy，禁止重定向、内网/元数据和 userinfo；payload 64 KiB 上限，使用 timestamp、nonce 与 HMAC v1。
- owner/admin 配置、测试与治理，当前成员只读；空间外及 enterprise governance 隐藏。Web 显式区分 dry-run 与真实投递。

## S17-M5 自动化管理与 Stage 收口当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/automation/management` 聚合规则、运行、步骤、连接器、投递、四维限额、个人偏好和低基数诊断；聚合只调用 M1/M2/M4 公共服务，不跨 owner 私表 join。
- V126 保存个人管理偏好、空间/规则/actor/action 日窗口限额、执行 claim receipt 和治理 receipt。稳定执行来源只消费一次配额；暂停/恢复要求 owner/admin、理由、expected version 与 exact request hash。
- 管理响应最多返回 100 条各类事实，diagnostic/health 仅为当前受权可解释派生，不授权、不复制业务 payload、响应正文、secret 或隐藏对象。
- Web 已交付规则/运行/连接器摘要、过滤偏好、配额进度和暂停/恢复，真实隔离验证覆盖 1440/1366/820、当前成员、空间外/enterprise 隐藏、跨空间空集与精确回放。
- S17 五个 Milestone、60 个 Task、V122-V126 与 route-final 已完成，Program revision 42 的 `current_stage` 为 `none`。S18 只可在独立归档/激活后建立跨空间授权、关系和同步。

## S16-M3 人员负荷与产能当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/resource-planning/capacity` 聚合当前日历、分配和已提交工时；V120 保存分配、规则、可重建负荷索引和精确命令回执。
- 分配创建、调整、结束与归档使用 expected version 和 caller-stable request hash；成功后追加 audit 与 `project.resource.capacity.changed`，精确回放不重复副作用。
- 每个最多 366 天的负荷桶携带可用分钟、分配分钟、实际分钟和 underloaded/full/overloaded 解释。缓存/索引不授权，也不形成组织利用率事实。
- 当前成员可读，owner/admin 可写；guest 只读，空间外和 enterprise governance 不可见。M4 将在这些公共合同上构建资源排期与调整。

## S16-M4 人员排期与资源调整当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/resource-planning/schedule` 组合 M3 当前 `CapacityFoundation`，派生最多 200 行、500 条分配条和 366 个冲突标记；不读取其他 owner 私表。
- V121 保存个人排期窗口偏好、可重建 index、低基数 stats 与精确调整回执。index/stats 可删除且不授权，偏好窗口最大 366 天。
- 调整 preview 无副作用；commit 只调用 canonical allocation update，先执行 exact receipt replay、再执行 expected version 校验，新请求冲突失败关闭。
- Web 对重叠分配条分层并限制轨道高度，支持键盘、REST 校准、离线输入保留与 1440/1366/820。六身份、跨空间、回放与 route-final 已通过。

## S18 完成与 S19 当前执行边界

- S18 完成路线已归档；当前已实现事实止于 V130 的跨空间授权、关系命令调用、同步/冲突/补偿、全景与协作审计，相关事实继续由既有 owner 和 project S18 owner 持有。
- Program revision 45 已激活 S19 为 4 个 Milestone、48 个 Task；S19-M1/M2/M3 已交付 V131-V133 指标语义、数据源、图表/看板、风险策略/信号 API 与 Web，下一合法入口为 `PROJECT-PLATFORM-S19-M4-T01`。
- S18-M1 已新增 V127 `project_cross_space_grants`、不可变 grant version 与精确 command receipt。`CrossSpaceGrantService` 持有草稿、修订、请求、双方确认、暂停、恢复、撤销和归档编排；scope 只保存方向、最小操作及双方已发布类型/版本 identity，不复制类型标题、字段、实例、成员或 ACL。
- 每个 grant 必须由 source/target 空间当前 owner/admin 分别确认；空间停用/归档、确认者收权/停用或 grant 暂停/撤销后，受保护入口立即失败关闭。enterprise-admin、缓存、realtime 和 S17 自动化/连接器均不授权。
- Web 已提供授权草稿、范围预览、双方确认、暂停/恢复、撤销/归档和离线/online/focus REST 校准；请求 ID/hash、expected version、receipt、audit 与最小 `project.cross-space.grant.changed` outbox 构成同一事务闭包。
- S18-M2 已新增 V128 versioned relation policy、双端 link intent、exact receipt，以及由 S10 公共 `CrossSpaceRelationCommand` 唯一持有的 canonical cross-space edge/history 扩展。策略与 intent 每步重新校准 active grant、已发布定义、source `relate`、target `accept_link`、endpoint version、方向、基数和环；并发由事务锁与唯一 active edge 保证单赢家。
- 端点引用与反向关系只暴露 opaque identity、type key、active/version 和 policy provenance，不返回标题、字段、状态路径或关系数。grant 暂停/撤销或任一方收权会停止新建链，既有 edge/history 仍由 S10 按 withdrawn/retained projection 解释。
- Web 已交付策略请求/双方确认、受权 ID 选择、目标接受、反向引用、暂停/恢复/撤销治理，以及 offline/online/focus REST 校准；1440/1366/820、六身份、并发重复、环和最小披露通过真实隔离验收。
- S18-M3 已新增 V129 同步规则、不可变规则版本、运行、步骤、冲突和精确回执。每个运行绑定 active grant/policy/canonical relation 与双方当前确认者；字段和状态变更只经 `CrossSpaceWorkItemCommand` 的 canonical update/transition 执行。
- 同步规则只允许声明式 `copy` 字段映射和显式状态 action；origin/input fingerprint 去重、8 层链深、50 步、expected endpoint version、冲突/补偿/死信均有界，run/step 不保存字段值。Web 已交付规则双确认、执行历史、冲突治理、响应式与离线 REST 校准。
- S18-M4 已新增 V130 个人全景偏好、低基数可重建 slice stats 和治理回执。`CrossTeamPanoramaService` 只组合 M1 grant、M2 relation 与 M3 sync 公共响应，生成最多 200 条当前受权 slice/audit lineage 和 healthy/attention/unknown 解释，不读取它们的私表。
- Web 已交付授权/关系/同步/冲突全景、来源版本、健康解释、紧凑偏好、1440/1366/820 与 offline/online/focus 校准。S18 四个 Milestone、48 个 Task 与 V127-V130 已完成并归档。
- S19 必须逐来源复用 S11 当前 decision/data scope 与 S13-S18 owner 公共合同；未知、缺失、过期、截断和无样本不能归零，指标/图表/风险/报表不授权，enterprise-admin 无内容旁路，个人排名和绩效评分禁止。

## S19-M1 指标语义、维度与时间窗口当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/metrics` 提供 schema v1 的 MetricDefinition 目录、草稿保存、不可变发布、修订/停用/归档和有界窗口预览；当前成员可读，owner/admin 可写，空间外与 enterprise governance 统一隐藏。
- V131 保存定义、不可变版本、版本化维度目录、caller-stable 命令回执和可重建结果索引。定义 identity/key、复合 workspace/space 边界、current version FK、唯一版本号、过期索引、不可变 trigger 和空间清理顺序已进入 schema。
- 表达式只接受注册 measure、dimension 与 count/sum/average/ratio；最多 4 个维度、500 个预览样本。SQL、脚本、模板、反射、私表名称、未知字段/未来 schema 和单位不匹配均在持久化前失败关闭。
- Window 固定 IANA 时区、ISO-8601 日历、rolling/fixed、day/week/month 以及 previous-period 比较；边界使用 calendar arithmetic 处理 DST，不假设自然日恒为 24 小时。窗口外迟到样本不进入当前结果。
- 预览只接收调用方显式标记为当前受权的有界样本；无权样本在聚合前移除，少于 3 个有效样本抑制。`unknown/no_sample/suppressed/stale/truncated` 的 value 均为空，不折算为 0。
- Web 已交付指标目录、语义编辑、窗口配置、不可变发布、版本 diff、来源说明、离线本地草稿与 online/focus/storage REST 校准；支持 1440/1366/820。M2 在该已发布语义之上实现图表、看板和跨空间数据源，不改写 M1 权威。

## S19-M2 图表、看板与跨空间数据源当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/metric-dashboards` 提供 DataSourceBinding、ChartDefinition、Dashboard、Layout、Filter、QueryResult、偏好和安全钻取 schema v1。当前成员可读，owner/admin 可保存、发布、分享和治理，空间外及 enterprise governance 不可见。
- V132 保存当前数据源绑定、图表/看板草稿、不可变图表/看板版本、个人偏好、exact command receipt 与带 permission/query fingerprint 和 expiry 的低基数可重建 cache；cache、图表结果和分享配置均不授权。
- `MetricDataSourceResolver` 只调用 S13 `WorkItemQueryService.execute`、`WorkItemSavedViewService.get` 和 S18 `CrossTeamPanoramaService.get` 公共合同。WorkItem 初始聚合版本 0 被保留为合法来源版本；查询按 owner 服务先授权后聚合，不读取 S11/S13/S14/S18 私表。
- 每个 Dashboard 最多 12 个 binding、24 个 chart；每图最多 50 series、500 point，drilldown 每页最多 100 个当前可见 opaque identity。表格、指标卡、折线、柱状、堆叠和分布共用同一服务端结果合同。
- chart 固定不可变 metric identity/version、维度、window 与 source version。active grant 被撤销后跨空间样本立即排除并返回 `no_sample`；suppressed/stale/truncated/unknown 时 series、facet、count 与 source version 全部隐藏。
- 发布版本不可变，保存/发布/分享/撤销分享/停用/修订/归档使用 expected version、request hash、exact response receipt、audit 和 `project.dashboard.changed`/`project_space.changed`。分享只把配置 scope 设为 space，不复制结果或扩大成员权限。
- Web 已交付目录、设计器、跨空间 binding、响应式结果、来源/freshness/truncation 解释、localStorage 离线布局、多标签同步及 realtime/online/focus REST 校准；支持 1440/1366/820。M3 风险策略/信号尚未实现，图表结果不得作为风险或权限权威。

## S19-M3 延期、阻塞、质量与资源风险当前实现

- project 模块通过 `/api/project-spaces/{spaceId}/metric-risks` 提供 RiskPolicy 草稿/不可变发布、当前 RiskSignal、评估和 acknowledge/close/suppress/reopen/invalidate schema v1；owner/admin 治理，member/guest 只读，空间外与 enterprise governance 隐藏。
- V133 保存 policy/version/signal/evidence/action/command/stats 六类表；policy version 与 action/command receipt 不可变，signal 以 policy/version/type/fingerprint 去重，cooldown、复合边界、过期索引和空间清理已落地。
- `MetricRiskEvidenceResolver` 只调用 S15 ProjectDetail 与 S16 ResourceCapacity 公共服务。延期、阻塞、停滞和质量保留当前可见 source identity/version/explanation；资源只保存空间级冲突数量，不保存人员、工时、负荷排行或隐式利用率。
- 关闭/抑制不改写来源；证据 fingerprint 变化会重算并可重新打开信号。目录最多 50 policy/200 signal，每个信号 20 evidence，链深 8、扇出 50；截断显式说明数量不完整。
- Web 已交付策略、版本、信号、证据解释与治理动作，支持 REST/realtime/online/focus/storage 校准、离线失败关闭和 1440/1366/820；六身份、跨空间隐藏、精确重放及并发版本冲突已通过真实隔离验收。M4 才实现 GovernanceOverview、ConfigHealth、AuditReport、Export 与 Stage 收口。

## S19-M4 治理驾驶舱、审计报表与完成状态

- project 模块通过 `/api/project-spaces/{spaceId}/metric-governance` 提供当前 GovernanceOverview、ConfigHealth、AuditReport、ReportRun 和 ExportReceipt schema v1。
- V134 保存报表定义、不可变运行、不可变导出回执和 exact command receipt；最多 50 份报表、100 次运行和 500 行导出，空间清理与 owner manifest 已覆盖。
- 聚合只调用 M1-M3 公共 foundation；ConfigHealth 绑定 source version，来源截断时显式 `unknown` 且不返回误导数量。导出逐次重算当前受权治理元数据，不保存成员、内容、标题、字段或权限快照。
- Web 已交付完整驾驶舱、健康解释、报表目录/运行/导出、离线失败关闭和 1440/1366/820；owner/admin 可写，member/guest 只读，空间外与 enterprise governance 隐藏。
- S19 四个 Milestone、48 个 Task 与 V131-V134 已完成；Program revision 46 的 `current_stage` 为 `none`。S20 仍为 Planned，须独立归档 S19 后激活。

## S19 归档与 S20 当前执行边界

- S19 完成路线已归档至 `docs/99-archive/superseded-roadmaps/project-platform-s19-roadmap-completed-2026-07-29.md`；Program revision 47 已激活 S20 的 5 个 Milestone、60 个 Task，当前唯一入口为 `PROJECT-PLATFORM-S20-M1-T01`。
- S20 复用现有单类型配置模板、草稿/发布、三方合并和 S03-S19 公共合同，新增权威仅限场景目录/版本/组件清单、安装运行/步骤、差异/升级决策和精确回执。
- 本次激活只冻结规划与验收边界；尚未实现任何 S20 schema、API、安装编排或 UI。S21 的旧模型删除、全量迁移、全仓最终验证和真人团队试用不得提前执行。

## S20-M1 研发场景模板当前实现

- V135 已建立 project-owned 场景目录、不可变版本、组件清单、安装记录/运行/步骤和精确命令回执基础表；复合 workspace/space 边界、稳定 key、版本唯一性、不可变历史、索引及空间清理均已进入 schema。
- `ScenarioTemplateCatalog` 当前只发布 `development` 清单：项目、需求、任务、缺陷、版本、迭代六个类型组件，以及研发层级/缺陷追溯、待办视图、交付看板、路线计划、缺陷分诊自动化和交付健康指标，共 13 个稳定组件。
- `ScenarioTemplateService` 对 schema、组件种类、owner contract、依赖扇出、重复 key、缺失依赖和环失败关闭，生成确定性清单哈希与拓扑安装顺序；目录导入幂等，版本内容不可更新。
- `/api/project-spaces/{spaceId}/scenario-templates` 只向当前可见空间成员返回目录和预览，owner/admin/member/guest 同一最小披露，non-member 与 enterprise governance 不获得内容旁路。目录、缓存、预览、校验和拓扑顺序均不授权。
- Web 已交付研发目录、来源版本、组件/依赖解释、校验结果、1440/1366/820 响应式与 offline/online/focus REST 校准；M1 不提供安装按钮，市场、HR、交付清单和统一安装/升级继续由 M2-M5 交付。

## S20-M2 市场活动场景模板当前实现

- V136 在不改写 V135 的前提下扩展注册组件 kind 约束；built-in catalog 新增 `marketing` 不可变清单，包含活动、内容、素材、渠道、投放、复盘六类配置，以及评审/发布流程、两组关系、市场日历、看板、受控通知、复盘指标和面板共 15 个组件。
- 素材只声明文件公共引用，通知只声明注册 action；文件内容、外部渠道凭据、任意 webhook/脚本、私表和权限快照均为显式禁止能力。
- 市场清单复用 M1 的 schema/hash/topology/visibility 合同，目录重放只新增稳定 marketing version，不改写 development version。Web 可切换模板并解释流程、关系和来源，仍无安装副作用。

## S20-M3 HR 招聘场景模板当前实现

- built-in catalog 新增 `human-resources` 不可变清单：招聘计划、职位、候选人、面试、Offer、入职六类配置，以及审批/阶段/会签流程、最小关系、招聘看板、面试日历、待办、受控提醒和受限指标共 16 个组件。
- 候选人敏感字段默认受限，流程节点不扩大字段可见范围；目录/诊断显式禁止候选人 PII、评价、隐藏数量、个人排名和面试官绩效。
- HR 清单复用当前 visibility/hash/topology 合同；owner/admin/member/guest 仅看到相同配置清单，non-member/enterprise 无内容旁路。离线仍只读且没有安装副作用。

## S20-M4 客户交付场景模板当前实现

- built-in catalog 新增 `delivery` 不可变清单：交付项目、任务、风险、交付物、评审、验收六类配置，以及阶段/整改/验收流程、追溯/风险关系、计划时间线、交付台账、受控通知、风险策略和治理面板共 16 个组件。
- 文件与验收仅保存公共引用；目录显式禁止文件/证据复制、签署模拟、客户内容诊断和隐式交付成功。计划、风险、文件、评审/验收与治理事实继续由 S15/S19 等 owner 持有。
- 四类场景目录已齐备并通过独立真实隔离验收；安装、diff、升级、重试、解绑和精确回执仍未执行，只能由 M5 收口。

## S20-M5 场景安装、差异化与升级当前实现

- V137 新增不可变 upgrade diff/conflict 历史，并把安装运行、步骤、精确命令回执和当前 installation 串成完整编排事实；同一 actor/operation/request ID 以 request hash 精确重放，hash 不同则失败关闭。
- owner/admin 可对四类场景执行 dry-run、install、retry、upgrade 和 detach。类型组件通过 `WorkItemTypeConfigurationService` 公共写合同创建稳定、合法且空间内唯一的类型 key；其他组件只验证并记录公开 owner reference，不读取或写入 owner 私表。
- dry-run 不写 owner 事实；安装逐步固定 source template version、target identity/version 与状态。未决本地清单冲突使全部步骤保持 skipped/attention；选择 local 或 upstream 后才可完成。detach 只解除场景引用，不删除已经由 owner 持有的配置事实。
- Web 已交付当前安装、预检、步骤、冲突、本地保留升级、重试与解绑工作台；只有 owner/admin 可见写入口，离线禁用命令。四场景、六身份、精确重放、21+ 类型增长、冲突、解绑和 1440/1366/820 已通过真实隔离 route-final。
- S20 五个 Milestone、60 个 Task、V135-V137 与完整真实隔离 route-final 已完成；完成路线已归档至 `docs/99-archive/superseded-roadmaps/project-platform-s20-roadmap-completed-2026-07-29.md`。

## S20 归档与 S21 当前执行边界

- S21-M1 至 M7 已完成。Program revision 50 保留 9 个 Milestone、108 个 Task；当前停在 `PROJECT-PLATFORM-S21-M8` 真人试用准备点，尚未执行任何 M8 真人任务。
- M4-M7 已交付信息架构合同、角色化简洁模式、渐进引导以及兼容/安全/可观测复验。当前 Flyway 最新 schema 为 V141；M8/M9 仍无真人结论、产品 Go、生产切流批准或归档结论。
- V088/V089 migration map、batch、unit、cutover、shadow、provenance 与 verification 仅保留为历史/恢复兼容证据。S21-M2 已退出 `/projects`、旧 Issue 产品 DTO/API/页面/搜索/写路径和活动 `project`/`issue` 注册，后续 rollout、kill switch 或恢复动作均不得把它们重新变成活跃产品。

## S21-M1 旧模型退出审计当前实现

- V138 新增不可变 LegacyExitAuditSnapshot、Finding 与 RemovalDecision 三类 project-owned 治理事实；历史快照、finding 和决定由数据库 trigger 阻止更新/删除。
- `LegacyExitAuditService` 注册 API、Web、工作台、搜索、realtime、平台对象、消息转换、legacy 表、迁移历史和兼容 resolver 十类退出 surface，并对 project/issue map、迁移失败、verification、shadow 与 cutover 做确定性聚合和指纹。
- `/api/admin/legacy-exit-audit` 每次重新校准 `project.manage`，支持快照、读取、导出和带 caller-stable request ID/hash 的 removal decision；enterprise 身份不因此取得对象内容。
- Web 管理台提供完成度、finding、surface 决定和导出，离线禁止提交决定；真实隔离 PostgreSQL/服务/浏览器已覆盖 1440/1366/820。该证据只授权进入 M2 清理评审，不代表生产迁移完成或允许物理删表。

## S21-M2 旧产品合同退出当前实现

- V139 撤销 `issue` 的平台对象规则、搜索投影/索引、对象链接和 `issue.*` 权限注册；旧 `projects`、`issues` 及 migration batch/unit/map/provenance/verification/audit 事实仅作为恢复与审计证据保留。
- 活跃产品面只暴露 `project_space` 与 `work_item`：旧 ProjectController、固定 DTO、service/repository 写路径、ProjectsPage/API client、旧 realtime/search/workspace 消费者均已退出。
- 旧 `/issues/{id}` Web 深链只执行受权且不可枚举的 location resolve：存在活动映射时跳转规范 WorkItem，否则显示统一不可用/已停止语义，不返回旧 Issue DTO。
- IM 与知识内容的转换统一依赖 `WorkItemCreationCommand`；工作台、搜索、通知筛选、对象选择和知识嵌入均使用 canonical `work_item`/`project_space`。
- V088/V089 与旧源表只允许登记的 migration、audit、location compatibility 和 recovery owner 读取；任何 feature flag、kill switch、recovery 或新功能都不得恢复旧 Project/Issue 产品读写、注册、搜索或永久双读双写。

## S21-M3 工程就绪当前实现

- `s21-engineering-readiness.v1.json` 冻结本地隔离证据合同：只使用确定性合成的 `project_space`/`work_item` 数据，研发、市场、HR、交付各 250 个 WorkItem；明确禁止把旧 capacity-v1 的 project/issue fixture 或本地预算表述为生产 SLO。
- `S21EngineeringReadinessIntegrationTests` 在 M3 checkpoint 使用 PostgreSQL 16 从 V001 迁移至当时最新 V139，执行索引读、聚合读、写入和数据库占用预算，验证 legacy 权限/对象注册为零及跨空间外键失败关闭；M5/M6 追加 V140/V141 后，当前全量迁移基线已推进到 V141。
- 同一测试执行 quiesced `pg_dump -Fc`、SHA-256 校验、隔离数据库 `pg_restore --single-transaction`、Flyway 版本/规范事实 digest 对账，并演练中断事务回滚。Redis 被定义为可重建状态；对象存储恢复要求版本化归档、对象数量和 SHA-256，当前本地证据不声明外部生产环境已恢复。
- route-final 使用完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全门禁和真实隔离浏览器；受控运维 UI 只呈现低基数退出证据，浏览器不持有数据库或备份凭据。
- revision 49 的 M3 工程 Go 只曾允许准备真人试用；revision 50 要求先完成 M4-M7 易用性收敛并取得新的 Engineering Go，才可进入 M8。研发、市场、HR、交付原始试用证据仍必须由真人在 consent 后产生，AI、维护者测试和 Playwright 均不能代签。

## S21-M4 信息架构合同当前实现

- `projectSpaceInformationArchitecture.ts` 冻结“概览、工作项、项目管理、成员、设置”五入口，以及工作模型、流程与权限、自动化与协同、度量治理、场景模板五类高级配置；M4 仍是静态合同，不把 M5 运行 Shell 提前写成已交付。
- 五个入口分别要求 `view_overview`、`view_work_items`、`view_project_management`、`view_members`、`view_settings` 服务端 capability。角色只决定默认落点和解释文案；未来自定义角色不受角色名 allowlist 限制，enterprise-admin 非空间成员显式留在治理 Shell。
- 现有二级 Tab、owner 公共组件和 `/types/**` 等配置深链继续保留；route-context 合同把旧配置路径解释为“设置/高级配置/工作模型”，并要求保留 `typeId`、`create`、`savedViewId`、`panel` 和来源 query。
- 信息架构审计已登记所有当前 surface、内容层、术语、模式回退、上下文返回、1440/1366/820、键盘和四场景威胁模型；真实隔离浏览器仅验证当前兼容基线与最小披露，不宣称五入口运行实现完成。

## S21-M5 简洁模式与角色化项目空间当前实现

- V140 新增 `project_space_experience_preferences`，以 workspace/space/user 唯一隔离 `simple|advanced`、schema version、CAS version 与更新时间。GET/PUT/DELETE 每次先用 `ProjectSpaceService.getVisible` 重新校准；默认和读取失败均为 simple，reset 只删除体验偏好，不触碰业务事实。
- `UserProjectSpaceView.availableActions` 新增五个逐入口 capability。non-member 可发现空间仅有 `view_overview`，guest 增加 `view_work_items`，member 增加受权项目管理，当前 manager 增加成员与设置；旧 `open/settings` 暂留作兼容动作，但 Web 五入口不消费角色名或旧动作授权。
- Web 只消费集中导航注册表和唯一 route-context resolver，运行时提供“概览、工作项、项目管理、成员、设置”五入口；项目管理组合既有计划、资源、风险、交付和指标公共面板，设置在 advanced 模式按五类高级配置渐进展开。
- `/management` 成为规范聚合路由；`/types/**` 旧配置路由继续直达原 owner 组件，并把一级上下文标记为设置。模式切换保留当前 query，服务端 CAS 冲突会重新读取；non-member 概览只渲染空间元数据，不挂载成员内容查询。

## S21-M6 渐进式配置与首次使用引导当前实现

- V141 新增 `project_space_onboarding_states` 与 `project_space_onboarding_telemetry_events`。前者以 workspace/space/user 唯一隔离 schema version、flow version、起步方式、提示确认、dismissal、opt-out、caller request ID 和 CAS version；后者仅保存 allowlist step/result/duration bucket/error code，不保存 user、标题、正文、字段值、文件名或个人绩效。
- `GET /api/project-spaces/{spaceId}/onboarding` 每次先校准当前成员身份、空间状态和管理 capability；`POST .../commands` 使用 caller-stable UUID、expected version 和 action allowlist，重复 request 返回既有结果；non-member 与 enterprise-admin 非成员得到相同的最小披露 404。
- 四类场景与“基础空间”选择的固定响应为 `selectionEffect=experience_only`、`installationRequested=false`、`publicationRequested=false`。选择只改变当前用户的引导状态；场景 validation/dry-run/install、配置草稿/校验/发布、成员、工作项与通知继续由 S03-S20 公共 owner API 持有。
- Web 在现有项目空间 Shell 上挂载可关闭和可恢复的引导 Drawer。管理者依次进入起步选择、模板影响、工作模型、流程权限、发布、成员、首项与交接；成员依次进入找工作、创建或更新、评论附件、流转和通知；访客仅获得当前受权只读说明。
- “基础空间”明确表示不额外安装场景模板，并保留空间创建时已有的六个基础类型。S20 install 当前真正创建或复用的只有 `work_item_type`，其他步骤只核验公共 owner 引用；界面不把选择、安装、草稿或 owner reference verification 描述为已发布配置。
- 离线、停用和归档状态禁用引导写入口并保留安全解释；dismiss/resume/reset 只影响体验状态。上下文帮助复用集中术语和 route-context，遥测 opt-out 与低敏事件失败均不改变业务命令结果。

## S21-M7 兼容、安全、可观测与工程准入当前实现

- Legacy Web 适配器只把 `/projects`、`/projects/{legacyId}`、`/issues/{issueId}` 和既有 `/types/**` 深链解析到重新鉴权后的 canonical `project_space`/`work_item` 站内相对路由。query 仅按目标路由 allowlist 保留 `typeId`、`create`、`savedViewId`、`panel`、`source`、`metricPanel`、`metricConfig`，hash 只保留页面上下文；协议/host、`//`、路径穿越、`redirect`、`returnUrl`、`next` 和重复控制参数失败关闭。
- 客户端体验缓存采用 `colla.project-space-ui.v2` 键族：最近空间按 workspace/user 隔离并只迁移当前可访问 space，草稿恢复 envelope 进一步按 workspace/user/space/kind 隔离、版本化和限时。坏值、未知版本、过期、quota 或身份切换只产生 cache miss/简洁回退，不删除服务端偏好、onboarding、收藏、业务草稿、已发布配置、审计或不可变回执。
- `GET /api/project-spaces/{spaceId}/experience-rollout` 由服务端合并 workspace/space/user include/exclude 与稳定分桶；只有 fresh 且显式 `enabled` 才启用增强呈现。baseline、kill、unknown、TTL 过期和读取失败全部回到 canonical project-space baseline。生产安全默认配置为 rollout 关闭、kill switch 开启、0 basis points、telemetry 关闭和 0 sampling，开关从不缓存或扩大 capability，也绝不恢复已退出的 Project/Issue 产品。
- `POST /api/project-space-experience/telemetry` 只接受 schema v1 的严格枚举 allowlist 和最多 20 条批量，事件不携带 user/workspace/space/object ID、URL query/hash、标题、正文、字段值、文件名、成员、角色或个人评分。浏览器只执行 opt-out/offline/allowlist/batch gate，服务端按 event ID、policy version 与 salt 做一次确定性采样；观测失败不阻塞产品。
- members、onboarding、WorkItem 配置、CrossSpace、Scenario 和 Metric 等可选 surface 已改为 `React.lazy`/`Suspense` 首次挂载加载；隐藏面板不预取，rollout cache 只在 fresh TTL 内复用，身份边界变化立即失效，route chunk 继续受 500 KiB 预算约束。
- M7 使用真实 API、随机身份和私有空间的隔离浏览器证据，不用网络 mock，覆盖六身份、canonical/legacy 路由、缓存迁移、rollout/kill、低敏遥测、离线、1440/1366/820、S20 四场景业务事实不丢及 finally 清理。P0/P1 清零和可恢复环境只形成进入 M8 准备的 Engineering Go；该结论不是生产批准、产品 Go 或真人试用证据。

## S21-M8 真人试用准备边界

- 当前只允许重新确认 trialRun、participant、consent、任务、观察、原始反馈、finding、清理与恢复合同并准备隔离环境。M8 尚未执行，AI、维护者、模拟账号和自动化浏览器都不能代签研发、市场、HR、交付参与者或真实管理者的操作与结论。
