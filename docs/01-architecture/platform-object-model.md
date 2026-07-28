---
title: 平台对象模型
status: active
last_code_check: 2026-07-26
---

# 平台对象模型

平台对象模型用于让 IM、搜索、通知、最近访问、收藏、知识内容嵌入块和跨模块链接共享同一套对象摘要和权限态。

## 核心数据结构

当前后端统一摘要为 `PlatformObjectSummary`：

| 字段 | 含义 |
| --- | --- |
| `objectType` | 对象类型，例如 `issue`、`knowledge_content` |
| `objectId` | 对象 ID |
| `accessState` | `available`、`forbidden`、`deleted`、`not_found`、`invalid` |
| `title` | 可展示标题 |
| `subtitle` | 可展示副标题 |
| `status` | 业务状态 |
| `webPath` | Web 跳转路径 |
| `deepLink` | 多端 deep link |
| `metadata` | 模块扩展字段 |

无权限、已删除或不存在对象必须返回不可用摘要，不应返回敏感标题或正文。

## 当前对象类型

当前 resolver 和前端 label 覆盖：

| objectType | 来源模块 | Web path | Deep link | 当前用途 |
| --- | --- | --- | --- | --- |
| `project` | project | `/projects/{id}` | `colla://project/{id}` | 项目对象选择、关系、最近和规范跳转；当前不进入用户全文搜索召回 |
| `issue` | project | `/issues/{id}` | `colla://issue/{id}` | 事项卡片、搜索、通知、IM 链接 |
| `work_item` | project | `/project-spaces/{spaceId}/work-items/{id}` | `colla://work-item/{id}` | S07 规范实例摘要、链接和统一对象 identity；搜索/通知消费尚未切流 |
| `knowledge_content` | knowledge | `/knowledge-bases/{spaceId}/items/{id}` | `colla://knowledge-content/{id}?spaceId={spaceId}` | 知识内容卡片、搜索、通知、IM 链接和对象嵌入 |
| `base` | base | `/bases/{id}` | `colla://base/{id}` | 表格空间卡片、最近访问、收藏 |
| `base_table` | base | `/bases/{baseId}/tables/{id}` | `colla://base_table/{id}` | 数据表卡片 |
| `base_record` | base | `/bases/{baseId}/tables/{tableId}/records/{id}` | `colla://base_record/{id}` | 表格记录卡片、搜索 |
| `message` | im | `/im?conversationId={conversationId}&messageId={id}` | `colla://message/{id}` | 消息搜索、消息对象摘要、事项反链 |
| `approval` | approval | `/approvals/{id}` | `colla://approval/{id}` | 审批通知、审批卡片 |
| `file` | file | 当前没有专用 resolver | 当前没有稳定 deep link | 文件元数据和附件引用 |

注意：`PlatformObjectType` 枚举中仍有历史大写类型，但当前 resolver 和前端使用小写 `objectType` 字符串。

### KB-NAME-M1 目标对象类型契约

平台对象类型已冻结为 `KNOWLEDGE_CONTENT` / `knowledge_content`，只表示可编辑知识内容；目录、对象引用和外部链接属于 `KnowledgeBaseItem`，不得自动注册为知识内容对象：

- 新写入和读取只接受 `knowledge_content`；`document` 产品别名已删除。
- 规范 Web 路径必须由 `itemId -> spaceId -> canonicalPath` 定位服务生成 `/knowledge-bases/{spaceId}/items/{itemId}`。
- `colla://document/{id}`、`/docs/{id}` 和 persisted `document` 不再由活动 resolver 或路由解析；旧链接按不可用链接处理。
- `resource_permissions`、搜索索引、通知、对象链接、收藏/最近、订阅和关系 payload 已由 V046/V047 回填并增加禁止旧类型约束。
- 历史无权限、已归档、不存在和失效对象继续返回安全摘要，不得因别名解析泄露标题、空间或路径。
- M1 的旧类型数据仅作为历史基线；M11 存量升级演练确认活动旧表、旧列和旧类型引用均为 0。

## 主要 API

| API | 用途 |
| --- | --- |
| `GET /api/platform/objects/{type}/{id}/summary` | 获取对象摘要 |
| `GET /api/platform/objects/{type}/{id}/card?context=user/admin` | 获取 user/admin 展示上下文下的对象卡片 |
| `GET /api/platform/object-types` | 获取对象类型规则 |
| `GET /api/platform/objects/{type}/{id}/navigation` | 获取导航信息并记录最近访问 |
| `GET /api/platform/objects/{type}/{id}/permission-explanation` | 获取当前用户对对象的操作权限解释 |
| `POST /api/platform/objects/{type}/{id}/access` | 记录访问 |
| `GET /api/platform/recent` | 最近访问对象 |
| `GET /api/platform/favorites` | 收藏对象 |
| `POST /api/platform/objects/{type}/{id}/favorite` | 收藏对象 |
| `POST /api/platform/objects/{type}/{id}/favorite/remove` | 取消收藏 |
| `POST /api/platform/links/resolve` | 解析内部链接 |

## 内部链接解析

当前内部链接解析支持：

- `colla://{objectType}/{id}`
- `/issues/{id}`
- `/knowledge-bases/{spaceId}/items/{id}`，知识库上下文内的用户主内容页入口
- `/bases/{id}`
- `/approvals/{id}`
- `/im?conversationId={conversationId}&messageId={id}`
- 包含上述路径的完整 URL

IM 消息发送时会扫描文本中的内部链接，解析后写入 `message_links`，前端展示 `InternalLinkCard`。消息右键复制出的 `/im?conversationId=...&messageId=...` 链接再次粘贴到 IM 后会解析为 `message` 对象卡片。

## 搜索关系

搜索索引当前覆盖：

- `issue`
- `knowledge_content`，用户侧展示为“知识内容”，默认排除已归档内容节点
- `base`
- `base_table`
- `base_record`
- `message`

搜索结果经过 `PlatformObjectResolverRegistry` 解析，返回当前用户视角下的 `accessState`、`title`、`webPath`、`deepLink` 和 `permissionExplanation`。查询使用 PostgreSQL `simple` 全文检索并叠加小写 `ILIKE` 后备匹配，用于召回中文短语、对象编号、标题和正文/记录值。不可访问对象不返回原始标题和正文。

用户侧 `/api/search` 固定为 `searchScope=user_content`，只返回协作内容对象。后台治理搜索固定走 `/api/admin/search-governance`，返回 `searchScope=admin_governance`，不得把权限、审计、组织治理等结果混入用户搜索页。

## 对象卡片展示上下文

`PlatformObjectCard` 在 `summary` 基础上补充 `presentationContext`、`actions` 和 `permissionHint`：

| 字段 | 含义 |
| --- | --- |
| `summary` | 平台对象摘要 |
| `presentationContext` | `user` 或 `admin`；无后台权限用户请求 admin 会降级为 user |
| `actions` | 当前上下文允许展示的操作入口，例如打开对象、权限排查、审计日志 |
| `permissionHint` | 当前对象权限解释摘要 |

用户上下文只能展示协作入口；后台上下文只有在当前用户具备后台权限时才可展示权限排查、审计日志等治理入口。对象卡片不得因为当前用户是管理员就在用户工作台默认展示后台操作。

## 权限解释

`PlatformObjectService.explainPermission` 统一返回 `PermissionExplanation`：

| 字段 | 含义 |
| --- | --- |
| `allowed` | 当前用户是否满足指定 action |
| `accessState` | resolver 视角下的对象访问态 |
| `currentLevel` | 当前权限级别，常见为 `view`、`edit`、`manage` 或 `none` |
| `requiredLevel` | 指定 action 需要的最低权限级别 |
| `source` | 权限来源，例如 `project_members`、`resource_permissions(knowledge_content)`、`base_members`、`approval_participants` |
| `reason` | 可展示给用户的解释文案 |
| `actionCategory` | `user_action`、`object_management`、`space_management`、`admin_management` 或 `super_admin` |
| `presentationContext` | `user` 或 `admin` |
| `actionAdvice` | 用户侧行动建议 |
| `policySourceDetail` | 后台侧策略来源细节，用户侧不默认展示 |

内部链接卡片和搜索页使用该解释模型展示“为什么不能看/不能操作”。项目、知识内容、Base、审批和消息先通过各自 resolver 归一为平台对象访问态，前端不直接读取业务私有权限表。

## 最近访问和收藏

平台对象服务会在对象导航或显式 access 时记录最近访问；用户可对平台对象执行收藏/取消收藏。工作台会展示最近知识内容、表格、最近访问和收藏对象。

## 知识内容对象摘要

`KnowledgeContentPlatformObjectResolver` 只以 `knowledge_content` 作为 objectType，并按知识内容权限返回摘要：

- 可访问内容返回标题、知识库主 Web path、`colla://knowledge-content/{id}?spaceId={spaceId}` 和知识库路径 metadata；主跳转是 `/knowledge-bases/{spaceId}/items/{id}`。
- 已归档内容节点的 `status` 返回 `archived`，普通内容节点返回 `active`。
- `metadata` 当前包含 `contentType`、`archived`、知识库 ID、知识库名称和知识库路径，供 IM 卡片、搜索结果和知识内容嵌入块复用。
- 无法确定 `spaceId` 时安全回退到 `/knowledge-bases`，不得构造无空间上下文的伪内容路由。

## 项目对象摘要

项目 resolver 读取当前用户的项目成员关系后返回摘要：

- 可访问项目返回名称、项目编码、状态、`/projects/{id}` 和 `colla://project/{id}`。
- V048 注册 `project` 对象类型，V053 回填可选择对象链接并补充查询索引。
- project 当前可用于对象选择、关系和规范导航，但搜索索引只覆盖 issue，尚未召回 project。
- 项目没有归档/删除 API，因此对象链接的完整生命周期同步尚未形成。

## 项目空间对象摘要

`ProjectSpacePlatformObjectResolver` 以 `project_space` 作为独立 objectType，并严格区分空间治理权和空间内容访问权：

- 有效空间成员可解析私有空间；非成员只能解析 `active` 的 `discoverable/workspace` 空间。
- 企业 `project.manage` 不会绕过 resolver 的成员与可见性判断，私有空间对非成员返回 `forbidden` 且不泄露名称。
- 可访问空间返回名称、空间编号副标题、生命周期状态、`/project-spaces/{id}` 和 `colla://project-space/{id}`。
- 已归档空间返回 `deleted`；停用空间保留可解释状态，由业务入口决定只读/治理动作。
- V056 注册 `project_space` 并建立对象链接；S02-M3 已交付对应用户页面 `/project-spaces/{id}` 和管理后台页面 `/admin/project-spaces/{id}`，深链已有生产 UI 落点。

### 工作项类型的当前对象边界

S03 的 `WorkItemTypeDefinition` 是 `ProjectSpace` 内的配置定义，不是可独立收藏、搜索、关系化或跨模块嵌入的平台对象，因此当前不注册 `work_item_type` resolver，也不生成独立 deep link。类型配置页始终由 `spaceId + typeId` 定位，并先执行空间成员与角色授权。

S04-M1 的 `FieldDefinition` 同样是类型内部的配置定义，通过 `spaceId + typeId + fieldId` 定位，不注册独立 `work_item_field` resolver。字段定义只能由空间 owner/admin 读取和编排；企业治理权限不等于空间配置访问权。字段类型目录是能力元数据，不是平台对象目录。

S04-M2 的 `FieldOption` 是 `FieldDefinition` 聚合内的稳定值域成员，不是独立平台对象。option key 创建后不可改、不可删除或复用，只能修改显示属性、排序和 active/disabled；默认值与结构化规则随字段配置 hash 和 aggregate version 原子变更。审计仅记录配置 hash 和选项数量差异，不复制默认值或规则载荷。

S04-M3 的 user、attachment 和 work_item_reference 配置保存的 UUID 只是对身份、文件和同空间工作项类型事实的引用，不是新的平台对象，也不生成对象关系。写入时必须重新解析当前对象状态和 actor 访问权；读取配置只返回 UUID 与能力合同，不复制名称、路径、文件 key、标题或其他快照。解析失败统一降级为 `unavailable_without_snapshot`，避免通过错误差异枚举隐藏对象。work_item_reference 的实例和值继续由 S07 承接。

S04-M4 为字段定义增加生产配置入口，但没有改变对象边界。字段深链始终是空间和类型内部路由 `/project-spaces/{spaceId}/types/{typeId}/fields/{fieldId}`；不能脱离 `spaceId + typeId` 解析，也不能收藏、搜索或作为跨模块对象嵌入。前端目录只投影服务端字段 capability、配置摘要和 `availableActions`，不会注册 `work_item_field` 平台对象或把 option、rule 当作关系节点。

S07-M1 已注册统一 `work_item` 平台对象。resolver 先按 workspace 定位实例所属空间，再复用空间成员边界和绑定版本事实返回摘要；非成员、跨 workspace、伪造空间组合和不存在对象均返回不可用摘要，不泄露标题。实例链接固定为 `/project-spaces/{spaceId}/work-items/{id}` 与 `colla://work-item/{id}`。

S07-M2 的动态值投影、participant 和 activity 是 `work_item` 聚合内部事实，不注册新的平台 objectType，也不生成独立收藏、关系或 deep link。跨模块消费者只能使用 `WorkItemChangedEvent` 的工作项 identity，再经现有 `work_item` resolver 或用户 API 校准；事件、活动与投影均不携带 hidden 字段值。

S07-M3 保留 `project`/`issue` 为有限迁移别名。旧 `issue` resolver 先执行 legacy 项目成员授权，再查显式 `project_legacy_work_item_maps`；映射目标存在时返回规范 `/project-spaces/{spaceId}/work-items/{workItemId}` 与 `colla://work-item/{workItemId}`，否则仍返回旧路径。别名不产生第二个规范对象 identity；跨 workspace、非成员、缺失 map 和目标缺失都不得通过标题、错误或映射元数据泄露对象。

S07-M4 迁移不会引入新的对象类型。每个迁移目标仍注册为唯一 `work_item` identity；即使 legacy UUID 可复用也必须写显式 map，UUID 已占用时用确定性新 UUID 并记录 `uuid_conflict_remapped`。pre-cutover rollback 删除本批次创建的平台链接与规范目标但保留 rolled-back map/provenance；post-cutover 不得删除可能承载新写的对象。

S07-M5 已把 canonical `work_item` 作为用户页面唯一对象身份。列表、详情、评论、附件、活动和分享链接均使用 `/project-spaces/{spaceId}/work-items/{workItemId}`；旧 `/issues/{legacyIssueId}` 只在 resolver 确认显式 active map 后重定向。未迁移对象保留兼容只读入口，已回滚/跨 workspace/无权对象不暴露 map、canonical ID、标题或迁移状态。

S08 的 StateDefinition、ActionDefinition、TransitionDefinition、GuardDefinition，以及 V091-V092 current-state/receipt/history/backfill 行仍属于 `work_item` 聚合的内部配置、运行或恢复事实，不注册 `work_item_state`、`workflow_action`、`workflow_history`、`workflow_backfill` 等新 objectType，不生成独立 deep link、收藏、关系或搜索 identity。current/history/action/correction/binding-upgrade/backfill API 均在既有空间/WorkItem 用户边界重新检查成员与 owner/admin 权限；公共 workflow 事件仍以 aggregateType `work_item` 定位，不把状态、动作或恢复批次提升为可独立枚举对象。

S09 的 StageDefinition、NodeDefinition、EdgeDefinition、BranchDefinition、JoinDefinition、RecoveryCommandDefinition、CompensationDefinition 及 V093-V096 instance/token/task/task-artifact/vote/join/arrival/receipt/history/compensation/backfill 行也属于 `work_item` 聚合内部事实，不注册独立 platform object、deep link、收藏、关系或搜索 identity。节点运行与恢复 API 继续以既有 `work_item` 可见性为前置，只投影调用者可见的 task/token/action/history/inbox/context/recovery/backfill 结果；file/object artifact 必须经公共 resolver 重新鉴权，平台 resolver 和其他模块不得读取节点私表补算摘要，公共节点事件仍以 `aggregateType=work_item` 定位。

S10 的 RelationDefinition、relation edge、command receipt、history、hierarchy path 和 hierarchy rebuild batch 不注册新的 platform objectType。规范端点始终是既有 `work_item` identity；关系事件只携带双端 UUID 和稳定 semantic key，不携带标题、字段值或访问策略。M2-M3 的关系与局部层级 API 在每次查询和命令中重新应用 ProjectSpace/双端 WorkItem 边界，不提供 relation、path 或 rebuild resolver/独立 deep link，不能因一端可见而泄露另一端摘要。面包屑、父子、同级和局部树只是现有 WorkItem 的有界导航投影，不形成 S13 全局树对象。legacy `issue_relations` 中 message/knowledge 等目标继续按原 platform object resolver 语义保留，不转换成虚假 WorkItem。

S11-M1 的 role/policy/selector/data-scope definition、角色绑定、事项角色分配、命令回执和 decision evidence 都是既有 `work_item`/`project_space` 的内部配置、授权或审计事实，不注册 permission、role、policy、decision 等新 objectType，不生成独立 deep link、收藏或搜索 identity。公共 resolver 只能消费安全 decision 后返回原对象摘要，不能通过 evidence 或 role binding 暴露隐藏对象。

S04 Stage 收口后，`FieldDefinition`、`FieldOption`、规则和复杂类型配置的对象归属保持不变：它们都是工作项类型内部的配置图，不具备独立分享、收藏、搜索、关系或平台对象解析能力。S05 的布局只能通过稳定 `fieldId + fieldKey` 引用该图，S06 只能把经校验的图物化进新的不可变类型版本；两者都不能把字段配置提升为独立平台对象。S07-M1 的 `work_item` resolver 只解析真实规范实例，不解析字段或布局配置。

S05-M1-M4 交付的 `WorkItemLayout`、布局节点、条件规则和字段访问策略同样只是 `WorkItemTypeDefinition` 内部配置，不注册 `work_item_layout`、`work_item_field_policy` 或其他平台 objectType。配置预览和成员样本只能投影 synthetic 值与安全选项摘要，不得生成收藏、最近访问、对象链接或搜索索引事实。S07-M1 新增的 `work_item` 必须显式绑定不可变 `type_version_id + config_hash`，发布新配置版本不会改变既有对象解释。

## 事项对象摘要

事项 resolver 读取当前用户的项目成员关系后返回摘要：

- 可访问事项返回标题、项目/类型副标题、状态、`/issues/{id}`、`colla://issue/{id}`。
- `metadata` 当前包含 `projectId`、`issueKey`、`priority`，并在存在时包含 `workflowReason` 和 `resolution`。
- 无项目权限用户返回 `forbidden`，不泄露事项标题和正文。

## 消息对象摘要

消息 resolver 读取当前用户是否仍是会话成员后返回摘要：

- 可访问消息返回“发送人 的消息”、正文预览、消息类型、`/im?conversationId={conversationId}&messageId={id}` 和 `colla://message/{id}`。
- 已撤回消息返回 `deleted`，非会话成员或不存在消息返回不可用摘要，不泄露正文。
- 从 IM 消息创建事项时，事项会通过 `issue_relations(target_type='message')` 关联原消息，事项详情再通过消息 resolver 水合摘要。

## 知识内容嵌入块

知识内容块通过 `embedSummary` 复用平台对象摘要；编辑器和 API 均位于知识库内容模块：

- `base_view` 块以 `base_table` 对象摘要作为当前 Base 视图的权限和跳转入口，并在块 metadata 中保留 `viewId`。
- Web 知识内容编辑器会在 `base_view` 摘要可访问时只读加载目标数据表和保存视图，展示字段显隐、筛选/排序后的前 5 条记录；不可访问时仍只展示权限态。
- `issue_embed`、`message_embed`、`file_embed` 和 `embed` 块通过对应 `objectType/objectId` 调用平台对象 resolver。
- 对象不可见时返回 `forbidden`、`not_found`、`deleted` 或 `invalid` 权限态；前端展示权限提示，不泄露对象标题和正文。
- Markdown/HTML 导出从 active blocks 生成对象指令或安全占位；导出不能把不可访问对象的标题、摘要、路径写入静态文件。
- 知识库治理和迁移检查会扫描嵌入块是否缺少标准 `objectType/objectId`，这类块进入 `invalid_embedded_object` 风险和迁移报告。
- 当前嵌入块仍是只读摘要、只读预览和跳转入口；嵌入对象的内联深度编辑仍属于后续 Base/工作流增强范围。

## Base 记录关系

M37 后 Base 记录可以作为关系源：

- `object_link` 字段保存 `{objectType, objectId, title}`，保存记录时通过平台对象 resolver 校验目标对象是否 `available`。
- 通过对象链接字段生成的关系写入 `base_record_relations`，`relation_type='field_link'`。
- 记录详情手工新增关系时同样先校验目标对象访问态，再写入 `relation_type='manual'`。
- 记录详情读取时会返回直接关系和以当前记录为目标的反向 `base_record` 关系，前端显示目标摘要及权限态。
- 关系本身不绕过权限；目标摘要由对应 resolver 决定可见、无权、删除或不存在状态。

## 当前 Gap

- `file` 暂无完整 resolver 和统一导航页。
- 平台对象类型规则表和 Java resolver 的类型来源仍是两套，需要后续收敛。
- 对象关系图谱仍停留在各模块详情内的局部关系展示，尚未形成全局关系浏览页面。

## S14-M1 看板对象边界

- BoardView、ColumnGroup、Swimlane、CardProjection、MoveIntent、偏好、排序与命令回执不是新的 platform objectType，不注册 resolver、收藏、最近访问、搜索 identity 或独立 deep link。
- 每张卡只引用既有 canonical `work_item` identity；标题、display key、状态、动作与字段投影在每次 render 时经 S13 查询和 S11 当前权限最小化，V110 不复制这些内容。
- 列/泳道 key、WIP 和 rank 是展示配置或可重建排序，不是 WorkItem 状态、节点 token、层级、依赖、日期或权限事实。移动成功后的对象 identity 仍是同一个 `work_item`。

## S14-M2 日历对象边界

- CalendarView、DateBinding、CalendarEvent、RangeWindow、窗口索引、偏好和日期命令回执不是新的 platform objectType，不注册 resolver、收藏、搜索 identity 或独立 deep link。
- 每个 CalendarEvent 只引用既有 canonical `work_item` identity；标题、日期、动作与可见性每次都经 S13 查询、S11 data scope 和 published snapshot capability 最小化，V111 不复制 WorkItem 正文或授权。
- 全天/区间/跨日、timezone、display date 与 overlap lane 都是日期权威字段的有界展示派生。窗口索引可删除重建，不成为日期、关系、层级或权限权威。

## S14-M3 甘特对象边界

- GanttView、ScheduleBar、HierarchyRow、DependencyLine、CriticalPath、展开状态和排期索引不是新的 platform objectType，不注册 resolver、收藏、搜索 identity 或独立对象权限。
- 每一行和排期条只引用既有 canonical `work_item` identity；依赖只引用既有 relation identity/version，层级只引用 S10 公共投影，V112 不复制标题、字段、关系、父子或授权。
- critical/float、可见父级、zoom 和 schedule index 是有界展示派生，可删除重建，不成为 WorkItem、日期、关系、层级或权限事实。

## S14-M4 基线与时间线对象边界

- ScheduleBaseline、BaselineEntry、BaselineDependency、Diff、TimelineEvent 和 timeline index 不是 platform objectType，不注册 resolver、收藏、搜索 identity 或独立对象权限。
- baseline 只持有既有 `work_item`/relation identity、来源版本和日期/可见父级；名称属于创建者个人展示事实，标题和权限每次通过当前投影获得。
- TimelineEvent 只引用现有 activity/audit/workflow/relation 来源 identity；去重与 index 可重建，不创建新的 history、actor 或业务事件。

## S11 WorkItem 权限投影

- `work_item` identity、space/type binding 与对象版本不因权限策略改变；权限是对同一规范对象的服务端 decision，不创建新的平台对象类型。
- resolver、列表、详情、字段、节点、关系和命令必须共享同一绑定 snapshot v5 decision；data scope 外对象返回不可用摘要，不进入标题、计数、游标或搜索候选。
- 平台对象摘要不携带 permission model、角色、selector、字段值或 deny 正文。安全 explanation 只在对象已可见后返回最小来源；enterprise governance 不自动取得内容访问。

## S15-M1 项目计划对象边界

- ProjectPlan、PlanPhase、PlanMilestone、PlanLink 和 PlanChange 是 project 模块治理对象，当前不注册为新的 platform objectType，不创建全局 resolver、收藏、搜索或跨空间导航。
- PlanLink 只引用既有 canonical `work_item` identity 和来源版本；当前权限重投影后不可见的引用被省略。它不复制标题、流程节点、依赖、日期、baseline 或授权事实。
- 阶段/里程碑顺序、日期、责任人和状态属于计划 aggregate；派生完成率、逾期数和可见链接数不是新的权威对象，也不反向改写 WorkItem。

## S15-M2 项目治理台账对象边界

- Risk、Issue、Decision、ChangeRequest、ResponsePlan 和 RegisterHistory 是 project 内治理对象，当前不注册为 platform objectType，不创建全局 resolver、搜索、收藏或跨空间导航。
- RegisterReference 只引用受权 canonical `work_item` 或 M1 plan identity/version；Issue 不冒充 WorkItem/缺陷，ChangeRequest 不冒充 Plan，Decision 不冒充审批结论。
- 风险分、到期提示、状态标签和隐藏引用截断是当前投影；它们不创建新的授权、流程、日期或计划权威。

## S15-M3 交付评审对象边界

- Deliverable、DeliverableVersion、ReviewRound、Signoff 和 Acceptance 是 project 内治理对象，当前不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- MaterialReference 只引用已有 platform/file/knowledge/work_item/plan/milestone identity/version 或受限外部 URI；它不是文件副本、文档版本、WorkItem 或计划对象。
- review item、required signer/quorum 与 acceptance 是交付治理事实；它们不创建新的用户、权限、审批流程或文件权威，隐藏来源完整省略。

## S15-M4 项目详情与健康对象边界

- ProjectDetail、HealthSignal、HealthStatus、Deviation、BlockingSummary、详情偏好和健康投影不是新的 platform objectType，不注册 resolver、搜索、收藏或跨空间 deep link。
- 详情只组合既有 Plan/Register/Deliverable 当前受权 identity/version；信号的 rule/explanation/observedAt 是有界派生，不复制来源标题、正文、ACL、成员或历史。
- V117 健康投影是用户/空间级、五分钟过期的可删除低基数索引；它不授权、不冻结健康、不成为 S16 产能或 S19 管理度量权威。

## S16-M1 工作日历与估分对象边界

- WorkCalendar、CalendarException、Estimate 和 ScheduleProjection 是 project 内资源规划事实或派生，不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- Estimate 只绑定既有 canonical `work_item` identity/version；WorkItem 标题、状态、字段、日期和权限在当前读取时由 owner 服务决定，收权引用完整省略。
- ScheduleProjection 只说明当前日历与估分下的起止日期或不可比/截断原因；它不是计划、甘特 baseline、人员分配、实际工时或承诺日期。

## S16-M2 实际工时对象边界

- Worklog 和 WorklogRevision 是 project 内资源事实，不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- Worklog 只引用 canonical `work_item` 和用户 identity；修订历史不复制事项标题、成员显示名、计划、流程或授权。
- Variance 是 current Estimate/Calendar 与 submitted Worklog 的即时派生，不是新对象、绩效指标、产能或 S19 组织度量。

## S16-M3 人员负荷与产能对象边界

- Allocation 与 CapacityRule 是 project 内资源事实，不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- Allocation 只保存 canonical WorkItem/user identity、窗口、比例、状态和版本；不复制事项标题、assignee、成员、计划、流程、权限或日历。
- LoadBucket、CapacitySignal 与 Conflict 是最多 366 天的当前派生，load index 可重建；它们不是新对象、授权、绩效指标或组织利用率事实。

## S16-M4 人员排期与调整对象边界

- ResourceSchedule、ResourceRow、AssignmentBar、ConflictMarker 和 AdjustmentResult 是 project 内当前投影/命令结果，不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- AssignmentBar 引用既有 Allocation/WorkItem/user identity 与 source version；不复制事项标题、成员显示名、计划、里程碑、流程或权限。
- 排期偏好是个人展示事实；schedule index/stats 是可重建低基数数据。它们均不创建人员、分配、授权、绩效或 S19 组织度量权威。

## S17-M1 自动化规则对象边界

- AutomationRule、RuleVersion、Trigger、Condition、Action、EventCatalog 和 ActionCatalog 是 project 内配置事实，不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- RuleVersion 冻结规则定义与 hash，不冻结事件 payload、WorkItem、流程、关系、通知、成员、权限或凭据；EventCatalog 只引用 S03 公共事件合同。
- 条件解释、目录字段、规则计数和 stats 是有界配置投影，不是授权、执行结果、业务 history 或 S19 组织度量。

## S17-M2 自动化执行对象边界

- AutomationRun、RunStep、ActionReceipt 与 ExecutionContext 是 project 内执行事实，不注册 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- run/step 只保存规则版本、actor、输入指纹、状态、稳定错误和受控结果；不复制 WorkItem、流程、关系、通知内容、成员资料或授权快照。
- execution stats 可删除重建且不授权；运行历史上限 100、每次执行上限 8 步，不构成生产吞吐或 SLO。

## S17-M4 连接器对象边界

- Connector、Delivery、Attempt 与 DeadLetter 是 project 内自动化事实，不注册 platform objectType。
- Connector 仅保存目标 URI 和 credential reference；Delivery 仅保存 payload hash/version/nonce 与最小状态，不复制业务 payload、响应正文或 secret。

## S17-M5 自动化管理对象边界

- AutomationManagement、ManagementPreference、QuotaState、QuotaReceipt、GovernanceReceipt、Health 与 Diagnostic 是 project 内管理/治理事实或派生，不注册新的 platform objectType、resolver、搜索、收藏或跨空间 deep link。
- 管理聚合只引用既有 Rule/Run/Step/Connector/Delivery identity/version；不复制 WorkItem、流程、关系、通知、成员、凭据、payload 或权限快照。
- quota 只记录低基数 type/key/window/count/limit/pause/version；health/diagnostic 可重建且不形成生产容量、可靠性、组织度量或 S18 同步事实。

## S18-M1 跨空间授权对象边界

- CrossSpaceGrant、GrantVersion、GrantScope、GrantParty 和 GrantReceipt 是 project 内跨空间治理事实，不注册新的 platform objectType、全局 resolver、收藏或搜索入口。
- GrantScope 只保存双方 space/type/published-version identity、方向、操作和可选实例 identity；不复制空间/类型标题、字段定义/值、状态、成员、ACL、WorkItem 或关系边。
- GrantVersion 不可变；修订创建新版本并要求双方重新确认。撤销和归档保留历史，但不授权新读取、建链或同步副作用。

## S18-M2 跨空间关系对象边界

- CrossSpaceRelationPolicy、LinkIntent 和 LinkReceipt 是 S18 project 内治理事实，不注册 platform objectType、搜索、收藏或全局 resolver。policy 只引用 grant、space、published definition 和 relation key identity/version/hash。
- CanonicalCrossSpaceRelation 与 RelationHistory 是 S10 relation owner 的 edge/history 扩展；它们引用两个空间的 canonical WorkItem identity/version 和 policy provenance，但不复制标题、字段、状态正文、路径、ACL 或成员。
- EndpointReference 是按当前调用身份计算的最小响应，不持久化内容。intent requested/linked/rejected/cancelled 与 edge active/withdrawn 分离，失败、撤销、收权和升级都不会伪造半边或重写历史。

## S18-M3 跨空间同步对象边界

- SyncRule、RuleVersion、SyncRun、SyncStep、SyncConflict 和 SyncReceipt 是 project 内编排/治理事实，不注册 platform objectType、resolver、搜索、收藏或 deep link。
- RuleVersion 保存声明式 mapping 与 hash；run/step/conflict 只保存 endpoint identity/version、origin/causation、输入指纹、状态和稳定错误，不复制 WorkItem 标题、字段值、流程状态、成员或 ACL。
- 补偿、死信和 resolved 是同步编排终态，不改写 canonical WorkItem 或 relation history；M4 全景只能引用这些有界 identity/version 事实。

## S18-M4 跨团队全景对象边界

- CrossTeamPanorama、CollaborationSlice、CollaborationAuditEntry、Health 与 Diagnostic 是请求期受权投影，不注册 platform objectType、resolver、搜索或收藏。
- Preference 是个人展示事实；SliceStats 可重建。二者都不复制内容、不授权，也不形成组织 KPI、绩效评价或 S19 指标定义。
