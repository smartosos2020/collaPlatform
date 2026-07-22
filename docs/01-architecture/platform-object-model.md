---
title: 平台对象模型
status: active
last_code_check: 2026-07-22
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

统一 `work_item` 平台对象只能在 S07 规范实例落地后注册；届时实例必须显式绑定不可变 `type_version_id`，resolver 才能基于实例、空间成员和版本事实返回摘要。S04 字段定义和 S06 配置版本不得提前伪造实例对象或复用 legacy `issue` resolver。

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
