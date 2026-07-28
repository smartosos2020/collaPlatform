---
title: 平台模块边界与公共合同 ADR
status: active
decision: accepted
revision: 1
updated_at: 2026-07-26
---

# 平台模块边界与公共合同 ADR

## 1. 决策范围

本文冻结模块化单体的术语、依赖方向、table owner、公开合同、组合查询、流程协调器、同步/异步选择、事务和例外审批规则。机器事实来源是：

- `tools/workbench/config/platform-modules.json`
- `tools/workbench/config/platform-table-owners.json`
- `tools/workbench/config/platform-boundary-exceptions.json`
- `pnpm architecture:contracts`

本文不声明现有依赖已经合格。M2 冻结合同，M3 建立失败门禁，M4 才迁移 project/shared 的 P0 私有依赖。

## 2. 规范术语

| 术语 | 可判定定义 |
| --- | --- |
| 模块 | `com.colla.platform.modules.<module>` 下拥有业务能力、代码 owner 和状态的部署内边界 |
| owner | 对模块行为、表事实、合同兼容和例外退出负责的团队或能力 owner |
| 公开合同 | `modules.<module>.contract` 下的 facade、SPI、record、value 或 event；这是唯一允许被其他业务模块依赖的 provider 包 |
| 私有包 | provider 的 `api`、`application`、`domain`、`infrastructure`；其他模块不得直接 import |
| 组合模块 | `search`、`admin`、`workspace` 等只编排多个公开 query facade 或维护自身投影、不拥有来源事实的模块 |
| 流程协调器 | 对跨 owner 长流程保存状态、重试和补偿的明确 application 能力；它不取得参与模块表的 owner 身份 |
| 技术 shared | 不包含业务规则、不 import 任一业务模块，只承载稳定技术机制的 `com.colla.platform.shared` |
| table owner | 对一张当前有效表的 schema、写入语义、生命周期和事实一致性唯一负责的模块 |

“同一 Spring 进程”不构成直接依赖私有包或私表的理由。

## 3. 依赖规则

### 3.1 允许

1. 模块内部可以按 `api -> application -> domain` 和 infrastructure adapter 方向协作。
2. consumer 可以依赖 foreign `contract` 中的稳定 facade/SPI/record/value/event。
3. provider application/infrastructure 可以实现本模块 contract。
4. provider SPI 的接口和值类型归属平台 contract；业务模块实现 SPI 时只依赖该 contract，平台模块不得反向 import provider 私有包。
5. shared 可以被模块依赖，但 shared 不得依赖业务模块。
6. 同一 PostgreSQL schema 可以保留跨 owner 外键；外键不授予 consumer 直接业务读写权限。

### 3.2 禁止

1. foreign `api`、`application`、`domain`、`infrastructure` import。
2. shared -> modules 反向依赖。
3. consumer 直接写 foreign owner 表。
4. 用通配路径、目录、模块或 read/write 混合模式放行历史例外。
5. 通过全局 helper、共享 Repository 或“公共 JdbcTemplate”隐藏跨 owner SQL。
6. contract import provider 私有包、Spring MVC DTO、Repository、存储密钥或认证凭据。

## 4. 事务与同步/异步决策

### 4.1 同事务

- 模块自身聚合和自身 owner 表写入。
- 业务命令与 transactional outbox append。
- 业务命令与必须原子记录的 audit append。

outbox/audit 的公开 port 必须加入调用方现有数据库事务。不得为了消除 import 改成事务提交后的易丢失调用。

### 4.2 同步公开合同

同步调用只用于立即完成当前命令所必需、延迟低、失败可直接返回且不会形成跨 owner 写事务的操作，例如：

- identity 主体状态和认证成员查询。
- file 元数据与访问判定。
- platform object summary/access state。
- IM 可见消息读取。

同步 query 必须批量、workspace scoped、最小披露，并明确 unavailable/hidden。

### 4.3 异步事件

满足任一条件时使用 outbox 事件：

- 调用不是当前命令提交的必要条件。
- consumer 可以稍后收敛。
- 存在一对多消费者、重试、积压或独立扩容需求。
- 搜索、通知、派生投影等可重建结果。

event envelope 固定 event type/version、workspace、aggregate、幂等、correlation/causation 和发生时间。payload 不得包含密码、token、MinIO key、私密正文或无界对象快照。

### 4.4 可恢复流程

跨 owner 写入、人员交接、跨模块删除或需要补偿的流程必须由显式流程协调器执行：

1. 每个参与模块只通过自己的 command contract 修改自己的表。
2. 协调器记录步骤、幂等键、结果和补偿状态。
3. 不扩大为一个跨多个私有 Repository 的同步数据库事务。
4. 失败必须可重试、可观察，且不会泄露被隐藏对象。

## 5. 公开合同规则与兼容

contract 当前版本为 1。允许在同版本中做向后兼容的新增；删除、重命名、收窄枚举、改变 null/hidden/错误语义或事务保证，必须：

1. 新增并行版本化合同。
2. 给出 consumer 清单和迁移 Stage。
3. 保持旧版本到退出 Stage。
4. 通过正反 fixture 和 provider/consumer 集成测试后删除。

contract 只允许 JDK 类型、同 contract 包类型或另一个经过批准的公共 contract。禁止把 application service、domain aggregate、Repository、Controller DTO、JDBC row 或存储实现作为合同值。

## 6. Table Owner

V001-V100 当前 146 张有效表在 `platform-table-owners.json` 中恰好归属一个 owner。规则如下：

- `foreignWrite = forbidden`：foreign write 没有例外通道。
- foreign read 必须匹配精确文件、精确表、read 模式和有效退出 Stage。
- 新建、rename、drop 表必须在同一变更同步 owner manifest。
- `domain_events`、`audit_logs`、`search_index_entries` 是有明确 owner 的技术表，不是无 owner shared 表。
- `realtime_signals` 由 event owner 管理，`search_projection_versions` 由 search owner 管理；二者都是技术事实表，不是跨模块共享写入点。
- migration/map 表仍归属对应业务模块；“临时迁移”不等于无人负责。
- history/log 表仍由产生该事实的模块拥有。
- 当前 `shared`、`ownerless`、`retired` 列表均为空；出现新条目必须有明确 ADR。

共享数据库和跨模块外键继续保留。本规则治理业务读写入口，不要求每个模块独立 schema。

## 7. 边界例外审批

每项例外必须具有唯一 ID、kind、source/target module、精确 source file、精确 target、模式、原因、owner、引入 Stage、退出 Stage、到期决定和批准状态。

- 审批人：source owner、target owner、platform architecture。
- 通配符和目录级放行无效。
- foreign write 不能审批。
- 修改旧违规文件时只能保持或减少既有范围；新增目标、表、模式、次数或方向均失败。
- 退出 Stage 到达时必须选择 remove、replace-with-contract 或 replace-with-projection，不能自动续期。

M2 只批准两个代表性只读入口用于锁定 schema；M3 会把完整历史 import/SQL 基线转成精确、可收敛的机器条目。

## 8. 组合查询与投影政策

### 8.1 Search

- 目标是消费事件维护自身 `search_index_entries` 投影。
- 现有在线读取业务表只允许精确 read 例外。
- 搜索不得写任何来源模块表，也不得成为业务事实 owner。
- 退出条件：对应对象类型具有增量事件、重建命令、延迟/积压指标和权限删除收敛。

### 8.2 Admin

- 企业治理页面只使用治理 query facade 或专用只读投影。
- 现有聚合 SQL 只允许精确 read 例外。
- 写操作必须调用目标模块 command contract；admin 不直接写目标私表。
- 治理 DTO 必须执行最小披露，不能返回凭据、存储 key 或用户不可见业务正文。

### 8.3 Workspace

- workspace 是用户体验组合层，不拥有 project、IM、knowledge、notification 等业务事实。
- 只允许批量 query facade 或自身投影，不允许直接 Repository/私表访问。
- 一个来源失败时返回明确的局部降级，不通过跨 owner 事务制造全有或全无。

## 9. Identity 公共合同

`SubjectDirectory` 批量解析 member、department、user-group，输入必须包含 workspace 和 actor。输出状态仅为 active/disabled/hidden；hidden 不返回 display name。

`AuthenticationQuery` 只返回 active member 的 workspace/user/username/displayName。它不公开密码、session、device token、角色私表或 Repository。

语义：

- 跨 workspace 一律 hidden。
- disabled 可在调用方确需区分“存在但不可用”时返回 disabled。
- actor 无查看权限、对象不存在或已删除统一 hidden，防止枚举。
- consumer 不得缓存身份权限事实作为长期授权依据。

## 10. File 公共合同

`FileAccess` 批量返回 available/unavailable/hidden。只有 available 才携带 file id、workspace、状态、size 和 MIME。

- 不公开 object key、bucket、签名 URL、上传 token 或后端实现。
- workspace 不匹配、无权和不存在统一 hidden。
- pending/deleted 可按调用场景收敛为 unavailable。
- 下载 URL 仍由 file 模块的用户 API 在权限判定后短期签发。

## 11. Platform Object 公共合同

- `PlatformObjectResolver` 是业务 provider 实现的 SPI。
- `PlatformObjectRegistry` 负责注册、summary 和 access state 查询。
- `PlatformObjectCommands` 负责 link/favorite 命令。
- `PlatformObjectSummary` 只包含 object type/id、title、route、access state 和有界 attributes。

依赖方向固定为 provider implementation -> platform contract；platform registry 只发现 SPI Bean，不 import provider application/domain/infrastructure。

## 12. Transactional Outbox

`TransactionalOutbox.append(EventEnvelope)` 是同事务 append port。event id 和 idempotency key 由调用方提供并稳定重试；event type/version 不从 Java 类名推断。

M2 不实现 Worker lease、dead-letter、replay、handler registry 或独立进程，这些属于后续 Stage。

## 13. Audit

`AuditAppender.append(AuditEntry)` 接收 actor/action/object/request boundary、correlation、时间、已脱敏上下文、before/after hash 和有界 diff。

- 原始密码、token、文件 key、富文本正文和完整配置快照禁止进入审计。
- 大对象只记录 hash、数量和允许字段的差异摘要。
- 调用方负责在 contract 边界前脱敏；audit provider 再执行防御性过滤。
- 必须审计的业务命令与 audit append 保持同事务。

## 14. Project 与 IM

`ProjectMessaging` 只暴露：

- 建立项目会话。
- 增加会话成员。
- 按 actor 读取可见且未越权的消息摘要。
- 发送具有幂等键的系统消息。

撤回时间通过最小 `MessageSnapshot` 返回；project 不读取 `messages`、`conversation_members` 或 `ImRepository`。项目创建时 IM 会话是同步必要步骤；活动广播是 best-effort 或异步，不得回滚已提交项目事实。消息转事项先通过可见消息 query 验证，再只在 project owner 表记录来源引用。

### 14.1 Project WorkItem 运行时公共合同

- `project.contract.WorkItemChangedEvent` 是规范工作项对搜索、通知和协作消费者开放的唯一增量事件载荷，固定 `eventType=work_item.changed`、`eventVersion=1` 和 `aggregateType=work_item`。
- 载荷只含 space/type definition/type version/config hash、work item version、status 和 mutation；不得增加标题、字段值、参与者、访问策略或命令回执。
- `project_work_item_field_projections`、`project_work_item_participants`、`project_work_item_activities` 均是 project 私表。其他模块不得直接读取；消费者需要详情时必须经平台对象 resolver 或用户 API 重新鉴权。
- 字段投影是 `project_work_items.field_values` 的可重建派生数据，不是共享查询库。跨模块不得把投影延迟或缺失解释成工作项不存在。
- 规范对象类型始终是 `work_item`；`issue`/`project` 仅是有限 legacy alias。跨模块消费者只能调用 `PlatformObjectRegistry`、resolver 或规范用户 API，不得读取 legacy map、cutover、manifest、failure 或 shadow 私表。
- 旧链接先授权再查显式 map；map、cutover stage、冲突原因和 shadow 差异均不是可枚举业务信息。旧写关闭返回稳定 `legacy_write_closed` 与 canonical `Location`，不建立双写，也不允许 kill switch 恢复 legacy 写。
- 用户协作命令固定从 `/api/project-spaces/{spaceId}/work-items` 进入；管理端 `/api/admin/project-migrations` 只拥有迁移 plan/execute/verify/rollback 与审计，不因企业管理员身份自动获得空间内容读取。
- 管理迁移 plan 可选显式 `projectIds` 作为批次范围；未提供时为 workspace 全量。范围过滤只影响冻结 manifest，不改变 workspace 授权、published snapshot 前置条件或失败关闭语义。
- S08 的 State/Action/Transition/Guard Definition 是 WorkItemType configuration snapshot 内部配置，不是公共合同或共享表。`project_work_item_current_states`、`project_work_item_workflow_commands`、`project_work_item_workflow_history`、`project_work_item_state_backfill_batches/units` 均由 project owner 持有；用户 API 只暴露安全 current/action/history/recovery DTO，其他模块没有私表读写入口。
- S09 的 Stage/Node/Edge/Branch/Join/Recovery/Compensation Definition 同样只存在于 WorkItemType configuration snapshot。`project_node_workflow_instances/tokens/tasks/task_artifacts/votes/joins/join_arrivals/commands/history/compensation_runs/compensation_steps/backfill_batches/backfill_units` 均由 project owner 独占；用户入口只返回授权后的 node workflow presentation/history/inbox/context/action/recovery/backfill DTO，其他模块没有私表读写入口，也不得借用 S08 current-state/history/backfill 私表。
- `project.contract.WorkItemNodeWorkflowEvent` 是节点运行时唯一公共增量合同，固定 `eventType=node_workflow.changed`、`eventVersion=1`、`aggregateType=work_item`。payload 只含 space/type binding、operation/node、WorkItem/instance version、instance status 与 decision reference；不得增加候选角色、条件、quorum、token lineage、split/join correlation、字段值或原因正文。消费者必须经 `work_item` resolver/用户 API 校准。
- `project.contract.NodeTaskLifecycleEvent` 是任务到期的最小公共合同，固定 `eventType=node_task.lifecycle`、`eventVersion=1`、`aggregateType=work_item`，只携带 space/task/work-item identity、event kind、node key 与 due instant。通知/搜索消费者通过 `project.node-task.consumer-contract` 的 delivery receipt 去重，不得读取 task/artifact 私表补全正文。
- `project.contract.WorkItemWorkflowEvent` 发布最小的 `workflow.action_executed/state_changed/initialized/binding_changed` v1 envelope。`project.workflow.consumer-contract` 只校验公共 payload schema 并依赖 delivery receipt 去重；未知 payload schema 永久失败。通知、搜索或协作若选择订阅，只能使用该 payload 再经 resolver/用户 API 校准，不得读取状态、回填、命令、history 私表或 active draft 补算事实。
- S08-M4 的状态配置器、成员执行 UI 和 backfill 管理只调用上述 project 用户/空间配置 API，没有新增跨模块端口、共享表或企业后台内容入口。
- S09 只能共享版本化 command/event、authorization/guard、aggregate-version、receipt/outbox SPI，不能复用 current-state、workflow history 或 backfill 私表作为 node-instance/token/join/vote 权威。节点恢复、补偿、binding upgrade 和 backfill 仍以 `work_item` 为 aggregate identity；compensation/backfill ledger 不升级为公共对象或跨模块查询表。
- S10 的 RelationDefinition 只存在于 WorkItemType configuration snapshot v4；`project_work_item_relations/relation_commands/relation_history/hierarchy_paths/hierarchy_rebuild_batches` 均由 project owner 独占。M2-M3 只通过 project 用户 API 激活关系实例、局部层级命令/查询，并通过 owner/admin 治理 API 重建派生 closure；其他模块不得读取这些表、legacy `issue_relations` 或 S08/S09 私表推导关系。
- `project.contract.WorkItemRelationChangedEvent` 是关系运行时唯一公共增量合同，固定 `eventType=work_item_relation.changed`、`eventVersion=1`、`aggregateType=work_item_relation`。payload 只含 space/relation identity、relation key、双端 WorkItem identity、relation version 和 mutation；消费者必须经 `work_item` resolver/用户 API 校准，未知版本失败关闭。M2 未注册 notification/search/realtime consumer。
- hierarchy path 与 rebuild batch 是 project 私有派生/恢复事实，不是公共合同或第二套边权威。attach/detach/reparent/split-child 仍通过规范 relation command 产生同一最小公共事件；scan/dry-run/rebuild/resume 不伪造业务关系事件。
- S11 的 SpaceRole、WorkItemRole、PermissionPolicy、SubjectSelector 与 DataScope 只存在于 WorkItemType configuration snapshot v5；角色绑定、事项角色分配、命令回执和 decision evidence 四表均由 project owner 独占。其他模块不得读取 project/permission/identity 私表拼接授权。
- `project.contract.WorkItemPermissionContracts` 冻结最小 request/decision/explanation DTO；`project.contract.WorkItemPermissionChangedEvent` v1 只携带 workspace/space/WorkItem、policy version/hash、change kind 与 aggregate version，用于失效和重读，不携带策略正文、角色显示名、字段值或 subject 隐私。
- M1 公共合同只是定义与边界，不代表运行授权已接管。M2 起 projection/query/execute/resolver/consumer 必须消费同一 decision 或批量等价结果；enterprise governance 不能映射为私有内容 owner。
- S11-M2 已激活统一 `WorkItemPermissionDecisionService`：单项和批量只解释绑定 snapshot，并以 config hash、policy version 与 subject version 校准。WorkItem、子资源、S08/S09 和 S10 入口只能调用该服务或等价批量结果，不得各自读取私表重建角色。
- S11-M4 的用户 explanation、治理 trace、request adapter、角色 mutation、策略 preview、consistency scan 和 legacy disposition 均属于 project 公共 API/应用边界；治理 trace 需要治理权限与内容访问交集，enterprise governance 不能反向取得 WorkItem 内容。
- 通用 permission 模块继续拥有公共申请/审批/授予/拒绝/撤回事实；project 只提交规范 WorkItem identity 与有界申请合同，不允许 permission 模块读取 project 私表或把通用 ACL 当作 snapshot policy 的第二权威。
- S11-M5 的 Web 配置器只写 S06 草稿 API；成员详情只读服务端 capability/explanation/access projection。运行 DTO 必须移除 `permissionModel`，禁止前端接收后按角色、selector 或 deny 正文补算授权。
- S12-M1 的 `project.contract.PersonalWorkQuery` 是 workspace dashboard 可依赖的唯一个人 WorkItem 聚合端口。返回 `PersonalWorkItem`、四类 bucket、多值 reason、capability、签名 cursor 与规范 deep link；workspace 不得引用 project 私有 application/domain/infrastructure，也不得读 project 表拼列表。
- `project_personal_work_projections` 与失效水位是 project 私有可重建数据，只保存 workspace/user/object/bucket/source/version/time。`work_item.changed` 和 `node_task.lifecycle` 只使投影失效；下一次读经 participant/node-task owner 事实、绑定 snapshot 与 S11 decision 重校准，事件和投影都不携带标题、字段值、策略或 subject 私有信息。
- S12-M2 的 `project.contract.DraftSummaryQuery` 是 workspace 读取 project 配置草稿的唯一公共端口；返回本人可见的最小摘要与 owner 解释的恢复路径，禁止 workspace/platform 读取配置草稿表或复制 snapshot。
- `platform.contract.DashboardPersonalization` 冻结稳定 card key、position/hidden、layout version 与全目录替换合同。platform 拥有 recent/favorite、card layout 与命令回执；所有对象标题和路径只由实时 resolver 返回，非 available 引用清理后不回显旧快照。
- S12-M3 的 `platform.contract.PlatformSearchProjectionProvider` 是 owner 向 search 提供最小可重建文档和 `view` decision 的横向公共端口。project 实现只输出 display key、标题、类型、空间、状态、来源版本与规范链接；decision 每批不超过 200 个 identity，只返回允许 identity。search 不得引用 project application/domain/infrastructure、读取 project 私表或自行解释空间、participant、字段和策略。
- S12-M4 的 `project.contract.PersonalCollaborationQuery` 是动态、提醒、催办与一致性恢复的公共入口。动态和提醒先通过 `PersonalWorkQuery` 取得当前可见 WorkItem；催办再校准空间、对象、接收者与冷却，并以不可变 receipt、audit 和 `notification.created` 交付。notification 仅通过 `PlatformSearchProjectionProvider.allowed` 重校准 WorkItem target，不读取 project 私表；权限收紧后以 `invalidated_at` 排除旧通知，未读数与列表使用同一可见集合。
- S13-M1 的 `project.contract.WorkItemQueryContextProvider` 是统一查询 DSL 读取 participant、状态流、节点流、关系和规范 hierarchy 投影的 project 内公共边界；调用方只能提交注册 AST 和规范 identity，不得提交 SQL、脚本、表名或任意 JSON path。动态字段必须经绑定 snapshot capability；所有可观察结果先经 S11 decision/data scope。V106 三表归 project 唯一 owner，其他模块不得读取查询定义、回执或统计私表拼装第二查询权威。
- S13-M2 的 table/list、偏好、批量与导出仍是 project 用户 API 和私有实现，不新增跨模块内容端口。V107 偏好、命令回执和导出任务表归 project 唯一 owner；其他模块不得读取保存的 query/column 输入、下载文件或偏好拼装第二视图。每次 render/export/download/bulk 都重新执行空间成员与 S11 decision/capability，realtime 只能使查询失效并要求 REST 校准。
- S13-M3 的 `project.contract.WorkItemHierarchyProjectionProvider` 是 S10 canonical hierarchy 向树视图暴露的最小 identity/depth 公共投影；它不返回标题、字段、授权或 edge 正文。树消费者必须与当前 M1/S11 allowed identity 相交后才能生成 root/child/path/count。V108 偏好与统计表归 project 唯一 owner，不能被 workspace/search/platform 读取为层级、内容或授权权威。
- S13-M4 的保存视图仍是 project 私有事实，不新增跨模块内容查询端口。platform 只通过 `saved_view` 的 `PlatformObjectResolver` 取得实时最小摘要并保存 favorite/recent 引用；不可见、撤销、移交或删除后 resolver 失败关闭并触发引用清理。V109 saved view/version/share/command 四表归 project 唯一 owner；其他模块不得读取 query/presentation/share 私表或把保存视图当结果、权限、层级或 S14 时间排期权威。
- `project_personal_activity_read_states`、`project_reminder_preferences`、`project_nudge_receipts` 由 project owner 独占；`notifications.invalidated_at` 由 notification owner 独占。动态、提醒和一致性 rebuild 不修改 WorkItem、node task、收藏或通知规范事实，也不把提醒/催办扩展为 S17 任意规则。
- `SearchFilters` 只接受空间、对象类型、状态、参与角色、知识元数据和时间的固定白名单；`SearchFacet` 与签名 `SearchCursor` 只从 decision/resolver 后的 available 集合生成。索引与管理员 rebuild 都不是授权、计数或对象内容权威。

## 15. 非目标

本 ADR 的非目标：

- 不拆微服务、数据库 schema 或前后端仓库。
- 不在 M2 修改现有 API、错误码、权限或用户界面。
- 不在 M2 实现 provider adapter 或迁移 consumer。
- 不引入分布式事务。
- 不交付 API/Worker/event-gateway 运行角色拆分。
- 不形成吞吐量、并发数、高可用或容量承诺。

## 16. 验证

`pnpm architecture:contracts` 必须验证：

1. 代码中的 15 个模块与 manifest 完全一致，未知模块失败。
2. 当前有效表与 owner manifest 完全一致，重复、缺失、未知 owner 或 ownerless 失败。
3. 例外没有通配符、foreign write、缺失 owner/Stage/决定或未知模块。
4. contract Java 源文件不 import provider 私有包。
5. 本文保留全部必要决策词和非目标。

## 17. S14-M1 WorkItem 看板模块边界

- 看板 API、偏好、排序、回执与统计均由 project owner 实现和持表；workspace、platform、search、notification 与其他模块不得读取 V110 私表拼装卡片、动作、计数或权限。
- 看板只组合 project 内已有的 S13 query、S11 permission decision、S08 state presentation/command 与 S09 node presentation/command 公共边界，不创建新的跨模块内容端口，也不允许浏览器解释 guard 或直接写流程表。
- V110 owner manifest、复合 FK、唯一约束、清理顺序和不可变 completed receipt 是架构门禁的一部分。偏好和排序是用户展示事实或可重建投影，不是 WorkItem、流程、层级、日期或授权权威。
- M1 不改变 S10 relation/hierarchy、platform object resolver、事件 worker 或 realtime transport 的 owner；M2-M4 的日历、甘特、基线和时间线仍无已实现模块合同。

## 18. S14-M2 WorkItem 日历模块边界

- 日历 API、个人偏好、可重建窗口索引、日期命令回执和统计均由 project owner 实现和持表；其他 owner 不得读取 V111 私表拼装事件、日期、计数或权限。
- 日历只组合 S13 query、S11 permission/data scope、published snapshot field capability 与 WorkItem 公共更新命令；浏览器不得自行解释字段权限、直接写工作项或把显示时区值当成存储事实。
- 日期更新仍由 WorkItem transaction 负责 validation、activity、audit 与 outbox。V111 receipt 和 index 不能成为第二套日期、事件、关系或 history 权威。
- M2 不改变 S10 relation/hierarchy、platform resolver 或 realtime transport owner；甘特、关键路径、基线和时间线仍由 M3-M4 交付。

## 19. S14-M3 WorkItem 甘特模块边界

- 甘特 API、个人偏好、可重建排期索引和统计均由 project owner 实现和持表；其他 owner 不得读取 V112 私表拼装行、日期、层级、依赖、关键路径或权限。
- 甘特只组合 S13 query、M2 calendar/date mutation、S10 hierarchy 公共投影和 identity-only dependency 公共投影。dependency provider 的调用方先给出当前受权 identity 集合，provider 只返回双端均在集合内的稳定 edge。
- 关键路径、浮动量、最近可见祖先和 schedule index 均为可重建派生，不成为第二套 relation、hierarchy、date 或 WorkItem 权威；浏览器不得自行补隐藏边或解释权限。
- M3 不改变 relation/hierarchy/calendar/platform resolver/realtime owner；基线和时间线仍由 M4 交付。

## 20. S14-M4 排期基线与时间线模块边界

- baseline API、不可变条目/依赖、命令回执和可重建 timeline index 由 project owner 持有；V113 不保存标题、字段值、角色、权限或 history 正文。
- project service 只通过 `AuditTimelineQuery` 公共合同读取 audit owner 的最小 work_item 事件；不得直接读取 `audit_logs`。activity、workflow 和 relation 由 project owner 内部投影为稳定来源 identity。
- baseline compare 与 timeline render 必须先取得当前 S13/S11/S10/M2/M3 受权 identity 集合；隐藏 baseline entry 或 relation endpoint 不进入 diff、数量、事件或错误外形。
- entry/dependency 是 90 天生命周期内的不可变视图快照，timeline index 可删除重建；两者都不是 WorkItem、日期、关系、层级、授权、audit 或 workflow history 权威。

## 21. S15-M1 项目计划模块边界

- project owner 持有 V114 的 plan、phase、milestone、link、change 和 command receipt；其他模块不得读取这些私表拼装计划、进度或权限。
- PlanLink v1 只保存当前受权 canonical `work_item` identity/version。节点、依赖、日期和 S14 baseline 继续通过既有 project 公共投影或命令消费；计划服务不得复制这些来源的标题、ACL、流程、关系或 baseline 私有内容。
- 计划阶段与里程碑是独立治理事实，不是流程节点或 WorkItem；计划日期和状态 mutation 不直接写 WorkItem、relation、hierarchy 或 calendar 私表。
- project 仅通过 `AuditLog` 与 `TransactionalOutbox` 公共合同记录副作用；V114 复合 FK、owner manifest、清理顺序和不可变 change trigger 是架构门禁的一部分。

## 22. S15-M2 项目治理台账模块边界

- project owner 持有 V115 register entry/reference/response/history/command 表；其他 owner 不得读取私表拼装风险分、问题阻断、决策、变更、响应或权限。
- register reference 只通过 WorkItemService/ProjectPlanService 公共合同校验当前 identity/version；不复制 WorkItem、流程、计划图或权限内容，收权后引用完整省略。
- 变更批准只调用 ProjectPlanService canonical mutation，不直接写 V114；计划命令与台账命令加入同一事务，精确重放不重复计划、history、audit 或 outbox。
- `AuditLog`、`TransactionalOutbox` 是唯一跨 owner 写边；V115 复合边界、owner manifest、清理顺序和 history immutable trigger 由架构门禁保护。

## 23. S15-M3 交付评审模块边界

- project owner 持有 V116 deliverable/version/material/review/signoff/acceptance/command 表；file、knowledge、platform、audit、event 或其他 owner 不得读取这些私表拼装结论。
- 文件/知识/WorkItem 物料只经 `PlatformObjectRegistry` 公共合同校验，Plan/Milestone/Register 只经 M1/M2 public service；V116 不复制 provider 标题、正文、路径、ACL 或成员事实。
- submitted version/material、signoff 与 acceptance 是不可变治理事实；current pointer、review close 和 deliverable status 在 project 事务内更新，浏览器不得自行计算 quorum 或验收成功。
- audit/outbox 仍是唯一跨 owner 写边；V116 owner manifest、清理顺序、复合 FK、序列锁和 immutable triggers 属于架构门禁。

## 24. S15-M4 项目详情与健康模块边界

- project owner 持有 V117 detail preference、command receipt 和 disposable health projection 表；其他 owner 不得读取这些私表拼装权限、健康或计数。
- `ProjectDetailService` 只组合 `ProjectPlanService`、`ProjectRegisterService` 和 `ProjectDeliveryService` 公共合同。来源服务先执行当前成员/权限校准，详情层不得全量读取后在浏览器过滤。
- projection index 写入失败不阻断 canonical 详情；任何缓存、realtime signal、fingerprint 或旧健康状态都不能授权。online/focus/reconnect 只触发 REST 重读。
- 个人偏好使用 caller-stable request ID/hash、expected version、精确回执、audit/outbox；V117 owner manifest、复合 FK、过期索引和空间清理顺序由架构门禁保护。

## 25. S16-M1 工作日历与估分模块边界

- project owner 独占 V118 calendar、exception、estimate 和 command receipt；其他 owner 不得读取这些私表拼装日期、估分、可用性、人员负荷或权限。
- Estimate 只保存 canonical WorkItem identity/source version、显式 unit/amount 和 aggregate version；每次读取通过 `WorkItemService` 公共应用边界重校准，不复制标题、字段、计划、里程碑、成员或策略。
- WorkCalendar 使用 IANA timezone、ISO 工作周和日期例外。ScheduleProjection 是最多 730 天的即时派生，不持表、不反写 S14 日期/S15 计划，也不把 point 换算成时间。
- `AuditLog` 与 `TransactionalOutbox` 仍是唯一跨 owner 写边；V118 复合 FK、owner manifest、空间清理顺序和 caller-stable exact receipt 由架构门禁保护。

## 26. S16-M2 实际工时模块边界

- project owner 独占 V119 worklog/current revision/receipt 表；其他 owner 不得读取私表拼装人员、工时、偏差、审批状态或权限。
- Worklog 只引用 canonical WorkItem 与 workspace user；WorkItem 当前可见性通过 owner 服务校准，代理边界由空间成员角色和 immutable reason 决定。
- revision 触发器禁止正常 update/delete；空间清理由受控 session flag 删除。submitted 偏差是有界派生，不创建 estimate、plan、capacity 或 utilization 权威。

## 27. S16-M3 人员负荷与产能模块边界

- project owner 独占 V120 allocation、capacity rule、load index 和 command receipt；其他 owner 不得读取私表拼装人员、负荷、空隙、冲突或权限。
- Allocation 只引用 canonical WorkItem 与 workspace user；成员及事项当前可见性通过公共服务校准，不改写 WorkItem assignee、日历、估分、工时或计划。
- CapacityFoundation 最多返回 200 个分配与 366 个桶，结合当前日历和 submitted worklog 即时派生。load index 可删除重建，不能授权或形成组织利用率事实。
- audit/outbox 是唯一跨 owner 写边；V120 复合边界、owner manifest、空间清理顺序和 caller-stable exact receipt 由架构门禁保护。

## 28. S16-M4 人员排期与调整模块边界

- project owner 独占 V121 preference、disposable schedule index/stats 与 adjustment receipt；其他 owner 不得读取私表拼装排期、冲突、权限或利用率。
- `ResourceScheduleService` 只组合 `ResourceCapacityService` 当前公开响应。行、条和标记携带来源 identity/version/window，不复制 WorkItem 标题、成员资料、计划或里程碑内容。
- adjustment preview 无写入，commit 只调用 `ResourceCapacityService.mutate` canonical command；M4 不直接更新 V120 allocation，也不改写 WorkItem assignee/日期。
- current member gate、exact replay、owner manifest、复合 FK 和空间清理顺序由架构门禁保护；enterprise governance 无内容旁路。

## 29. S17-M1 自动化规则模型模块边界

- project owner 独占 V122 rule、immutable version、event catalog mirror、command receipt 和低基数 stats；其他 owner 不得读取这些私表拼装规则、权限或业务副作用。
- EventCatalog 只声明 S03 公共 envelope 的稳定 event type/version/allowed fields，不复制 `domain_events`、delivery、receipt 或 producer 私表；未知事件和版本失败关闭。
- ActionCatalog 只声明 canonical owner、版本与副作用类别。M1 不执行字段、流程、关系、通知或 Webhook，也不建立第二个可靠 worker。
- 条件 DSL 只接受有界声明式节点和安全引用，不提供脚本、SQL、模板或任意代码入口。当前空间 owner/admin 配置，所有成员读取仍重新校准空间 membership。
- `AuditLog` 和 `TransactionalOutbox` 是唯一跨 owner 写边；exact receipt、复合 FK、不可变 RuleVersion 和 owner manifest 由架构门禁保护。

## 30. S17-M2 自动化执行模块边界

- project owner 独占 V123 run、step、action receipt 和 disposable stats；其他 owner 不得读取这些私表拼装执行历史、权限或副作用。
- `AutomationExecutionService` 只调用 `WorkItemService`、`WorkItemRelationService`、`SubjectDirectory`、`AuditLog` 与 `TransactionalOutbox` 公共合同，不直接写 WorkItem、流程、关系、成员或通知私表。
- 每个 run 绑定确切 RuleVersion，最多 8 步；source/run/step/receipt 各自稳定去重。dry-run 只生成 skipped step，不能升级为真实成功。
- S03 handler 仅匹配最多 20 条 enabled 规则；执行前重新校准当前空间可见性，运行历史缓存和统计均不授权。

## 31. S17-M4 连接器模块边界

- project owner 独占 V125 connector/delivery/attempt/dead-letter/receipt；credential owner 只通过 `AutomationCredentialResolver` 返回短生命周期字符数组。
- 网络策略禁止非 HTTPS、重定向、私网、loopback、link-local、multicast 和元数据地址；每次尝试重新解析。
- 外部响应只保存状态码、稳定错误和时长，不保存 secret、签名或响应正文。重放/放弃要求当前权限与 10-512 字符理由。

## 32. S17-M5 自动化管理与限额模块边界

- project owner 独占 V126 management preference、quota state、quota claim receipt 和 governance receipt；其他 owner 不得读取这些私表拼装管理视图、执行权限或组织指标。
- `AutomationManagementService` 只组合 `AutomationRuleService`、`AutomationExecutionService`、`AutomationConnectorService` 与 `AutomationQuotaService` 的当前公开响应；统计、健康和诊断均为有界低基数派生。
- `AutomationQuotaService` 在真实执行事务内按 space/rule/actor/action 消费稳定 claim；caller-stable source receipt 防止重放重复计数。暂停/恢复使用 current owner/admin gate、expected version、reason 和 exact governance receipt。
- preference、quota、diagnostic、Web cache 与 realtime signal 均不授权。S17 不定义跨空间成员、关系或字段同步；S18 必须建立独立 versioned authority。

## 33. S18-M1 跨空间授权模块边界

- project 模块的 `CrossSpaceGrantService` 是 grant 生命周期与 scope version 的唯一 application owner；Controller 只映射协议，`JdbcCrossSpaceGrantRepository` 只持有 V127 grant/version/receipt 私表。
- 空间身份和状态通过 `ProjectSpaceRepository` 公共合同读取，双方类型版本通过 `PublishedSnapshotAdapter` 校验；grant 不写成员、ACL、类型、实例、关系、流程、自动化或 credential 私表。
- 受保护授权入口同时检查 active grant、scope operation、source/target 当前空间状态和双方确认者当前 owner/admin 身份。调用方不得缓存成功 decision 或从 Web/realtime 推导授权。
- 成功命令通过稳定 AuditLog/TransactionalOutbox 公共端口写治理证据；payload 只含 grant/space identity、状态、版本和 change，不含类型/字段/成员正文。

## 34. S18-M2 跨空间关系模块边界

- `CrossSpaceRelationService` 只拥有 V128 policy、intent 和 exact receipt；它通过 `CrossSpaceGrantService.requireActiveGrant`、`PublishedSnapshotAdapter`、`WorkItemRelationAccessDecisionService` 与 `WorkItemRepository` 公共合同重校准双方当前能力和 endpoint binding。
- `CrossSpaceRelationCommand` 是 S10 的唯一跨空间 canonical edge 公共命令。其 JDBC adapter 独占 cross-space edge/history 私表、事务图锁、唯一 active edge、基数、环、withdraw history 和 endpoint version 判定；S18 repository 不引用这些表。
- Controller 只公开 foundation、policy lifecycle、intent lifecycle、minimal endpoint reference、canonical relation reference 与 reasoned withdrawal；404/403 外形不泄露对端标题、字段、状态、路径或数量。
- 客户端 cache、realtime、offline draft 和 intent 均不授权。每次 accept/withdraw 以当前 grant/policy/space/member/definition/endpoint decision 为准，成功后追加 exact receipt、audit 与最小 outbox。

## 35. S18-M3 跨空间同步模块边界

- `CrossSpaceSyncService` 独占 V129 rule/version/run/step/conflict/receipt；`CrossSpaceWorkItemCommand` 是同步调用规范 WorkItem 字段/状态命令的最小公共端口，adapter 才可调用 `WorkItemService`。
- 同步服务只通过 `CrossSpaceGrantService`、`CrossSpaceRelationRepository` 的 policy 合同、S10 `CrossSpaceRelationCommand` 和当前 access decision 校准。它不得读取关系、事项、字段、流程、成员或权限私表。
- run/step/conflict 只保存 identity、版本、方向、指纹、稳定错误和治理终态。AuditLog/TransactionalOutbox 仍是唯一横向写端口；缓存、统计、Web 与 realtime 均不授权。

## 36. S18-M4 跨团队全景模块边界

- `CrossTeamPanoramaService` 只调用 `CrossSpaceGrantService`、`CrossSpaceRelationService` 和 `CrossSpaceSyncService` 公共 foundation，不读取 V127-V129 私表。
- V130 preference/stats/receipt 由 project owner 独占；stats 可删除重建且不授权。全景、health、diagnostic 与 Web cache 都不是 S19 指标权威。
