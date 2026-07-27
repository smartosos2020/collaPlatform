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
