---
title: 项目协作平台目标架构
status: target
program: PROJECT-PLATFORM
program_revision: 23
domain_contract_version: 1
domain_contract_status: frozen-s01-m3
migration_contract_version: 1
stage_review_status: s08-active
updated_at: 2026-07-26
---

# 项目协作平台目标架构

## 1. 文档边界

本文描述 `PROJECT-PLATFORM` 的目标架构和迁移约束，不代表当前代码已经实现。当前事实仍以 `docs/01-architecture/current-architecture.md` 和代码为准；每个 Stage 只有在实现、测试和迁移证据完成后，才能把目标能力同步为当前事实。

## 2. 目标分层

```text
企业治理层
  └─ workspace、组织、企业角色、全局策略、审计

项目空间配置层
  └─ space、成员、工作项类型、字段、页面、流程、角色、关系、视图、自动化

项目协作运行层
  └─ 工作项实例、字段值、流程实例、参与者、关系、评论、附件、工时、交付物、动态

用户聚合层
  └─ 我的工作、搜索、收藏、最近、视图、人员排期、度量
```

企业管理后台不承载工作项执行页面；空间管理员在项目产品的空间配置入口管理协作规则；空间成员在用户工作台执行工作。

## 3. 核心聚合

### 3.1 ProjectSpace

项目空间是配置、成员、数据可见性和协作规则的边界，包含：

- 空间基本信息、状态、可见性和默认策略。
- 空间管理员、成员、访客和邀请关系。
- 已启用的工作项类型版本。
- 空间级角色、视图、自动化和跨空间授权。

空间不是具体项目实例；具体“项目”由工作项类型和工作项实例表达。

### 3.2 WorkItemTypeDefinition

工作项类型定义描述一种业务对象的配置：

- 稳定 type key、名称、图标、状态和配置版本。
- 字段集合与字段规则。
- 新建表单和详情页布局。
- 状态流或节点流定义。
- 角色定义和操作权限。
- 与其他工作项类型的关系定义。
- 默认视图、自动化和模板来源。

系统预置类型与自定义类型使用同一运行合同；预置只表示受保护的初始模板，不表示另一套代码路径。

### 3.3 WorkItem

统一工作项实例至少包含：

- `id`、`workspace_id`、`space_id`、`type_definition_id`、`type_version`。
- 标题、编号、生命周期状态、创建人、创建/更新时间。
- 规范字段值文档或类型化字段值行。
- 当前流程实例、当前节点/状态和版本水位。
- 参与者、角色绑定、关注人和数据可见性投影。
- 平台对象 ID、搜索投影和审计关联。

`requirement`、`task`、`bug`、`iteration`、`campaign`、`candidate` 等是 type key 或模板语义，不是平行的顶层运行模型。

## 4. 配置定义与运行实例分离

```text
配置草稿 -> 校验 -> 发布不可变版本 -> 新实例采用
                                └-> 旧实例保持原版本
                                └-> 显式模板升级/迁移
```

- 草稿可以频繁修改，不影响运行实例。
- 发布版本不可变；修正通过新版本完成。
- 工作项记录创建时采用的配置版本。
- 模板升级必须预览差异、校验兼容性并记录结果。
- 删除字段、状态、节点、角色或关系前必须处理历史引用。
- 不允许后台直接改配置后让运行实例在没有迁移记录的情况下改变语义。

## 5. 字段与页面模型

字段类型注册表负责类型、序列化、校验、查询和展示能力。第一阶段覆盖：文本、富文本、数字、布尔、单/多选、人员、日期/时间、日期区间、URL、附件、工作项引用和计算派生值。

字段定义包含：

- 稳定 field key 和版本。
- 类型配置、选项、默认值、帮助文本。
- 必填、只读、有效性和条件公式。
- 授权角色和数据范围。
- 搜索、排序、分组和索引能力声明。

新建表单与详情页布局分别版本化。布局只引用字段和控件，不复制字段定义；条件显示不能代替服务端权限和校验。

## 6. 流程模型

### 6.1 状态流

适用于任务、缺陷、内容等轻量事项：

- 状态、动作、转换、守卫、授权角色。
- 必填字段和转换副作用。
- 回退、重开、终止、恢复和历史。
- 乐观锁、幂等键和并发冲突。

### 6.2 节点流

适用于项目、评审、招聘、交付等复杂事项：

- 阶段、节点、连线、分支、并行、汇聚和依赖。
- 自动完成、单人确认、任一人确认和多人会签。
- 节点负责人、节点角色、节点表单、交付物、排期、估分和子任务。
- 进入条件、完成条件、限制节点、回滚、跳转、终止和补偿。

流程定义是声明式配置。任意代码插件节点不进入基础运行时；外部扩展通过受控 Webhook/连接器和自动化操作实现。

## 7. 关系与层级

关系定义描述源类型、目标类型、方向、基数、权限和删除策略。第一阶段支持：

- 普通双向关系。
- 有向父子关系。
- 依赖、阻塞和前后置关系。
- 同空间和跨空间关系。
- 关系字段和详情页关系控件。

关系运行时必须检测非法循环、重复边和越权目标。层级视图由关系定义构建，不在工作项表中硬编码固定“项目-需求-任务”层级。

## 8. 权限模型

```text
企业权限
  + 空间成员资格/空间角色
  + 工作项实例角色
  + 节点负责人/节点授权
  + 字段编辑授权
  + 关系与数据范围约束
  = 当前用户的有效权限与解释链
```

- 企业管理员不因后台身份自动成为所有工作项内容协作者。
- 空间管理员拥有空间配置权限，但数据访问仍需明确策略。
- 工作项角色绑定到实例，可由字段、创建人、固定成员或自动化产生。
- 所有写 API 在服务端重新计算权限，前端 available actions 只是投影。
- 权限拒绝不得返回不可见工作项标题、字段值或关系目标。

## 9. 查询、视图和度量

统一查询 DSL 支持系统字段、动态字段、关系、角色、流程状态和时间窗口。保存视图记录查询、列、排序、分组、泳道和展示配置；个人视图与共享视图使用同一模型但权限不同。

视图包括表格、列表、树形、看板、日历、甘特和人员排期。度量使用版本化指标语义层，不能直接把前端临时聚合结果作为企业指标事实。

## 10. 自动化和开放集成

自动化规则采用：

```text
Trigger -> Conditions -> Actions
```

事件通过事务 outbox 产生，执行器保证幂等、重试、退避、死信和审计。操作在执行时重新检查规则权限和目标权限。Webhook 请求签名、限流、超时、重放保护和敏感字段脱敏是基础要求。

## 11. 数据持久化方向

目标表族使用概念名，最终名称以 S01 ADR 和迁移评审为准：

- `project_spaces`、`project_space_members`
- `project_work_item_types`、`project_work_item_type_versions`
- `project_field_definitions`、`project_layout_definitions`
- `project_work_items`、`project_work_item_field_values`
- `project_workflow_definitions`、`project_workflow_instances`、`project_node_instances`
- `project_role_definitions`、`project_role_assignments`
- `project_relation_definitions`、`project_work_item_relations`
- `project_views`、`project_automation_rules`、`project_automation_runs`
- `project_worklogs`、`project_schedules`、`project_metric_snapshots`

S01-M3 已冻结动态字段的混合存储方向：工作项规范值使用 JSONB 原子保存，声明 query/sort/group capability 的字段同步写入类型化查询投影；搜索和分析投影仍由 outbox 异步构建。完整物理表只在对应 Stage 实现时落 Flyway，本文件不把 spike DDL 当生产 schema。

## 12. 模块边界

后端保持模块化单体，建议演进为：

- `project.space`：空间与成员。
- `project.configuration`：类型、字段、布局、流程、角色、关系和发布。
- `project.runtime`：工作项、字段值、流程实例和关系实例。
- `project.view`：查询、保存视图和读模型。
- `project.automation`：规则和执行。
- `project.metrics`：度量和资源聚合。

这些可以先作为同一 `project` 模块中的清晰包边界，不提前拆微服务。跨模块继续复用 permission、file、platform object、search、notification、audit 和 outbox 能力。

## 13. S01-M2 冻结领域合同

本节是 PROJECT-PLATFORM-S01-M2 的目标领域合同 v1。它冻结产品语义和聚合边界，不提前决定 M3 要比较的物理存储方案。后续 Stage 可以扩展合同，但不得静默改变已发布版本和历史实例的语义。

### 13.1 规范术语、标识和生命周期

| 中文术语 | English / canonical name | Stable identity | Ownership | Lifecycle |
| --- | --- | --- | --- | --- |
| 项目空间 | `ProjectSpace` | `spaceId`，workspace 内稳定 UUID；`spaceKey` 在 workspace 内唯一且停用后保留 | `Workspace` | `active -> disabled -> active`；`active/disabled -> archived -> active`；非空空间禁止硬删除 |
| 工作项类型定义 | `WorkItemTypeDefinition` | `typeDefinitionId`；`typeKey` 在 space 内永久唯一 | `ProjectSpace` | `active <-> disabled -> retired`；retired 不可新建实例但历史可读 |
| 工作项类型版本 | `WorkItemTypeVersion` | `typeVersionId` + 单调 `versionNumber` + `configHash` | `WorkItemTypeDefinition` | `published -> superseded`；创建后不可变，不承担可变草稿职责 |
| 工作项 | `WorkItem` | `workItemId`，workspace 内稳定 UUID；展示编号由独立原子序列生成 | `ProjectSpace`，并绑定一个类型版本 | `active <-> archived`；业务流程状态与对象生命周期分离 |
| 配置草稿 | `ConfigurationDraft` | `draftId`，同一 type definition 同时最多一个活动草稿 | `WorkItemTypeDefinition` | `editing/validating/valid/invalid -> abandoned`；发布由 M2 原子关闭草稿 |
| 工作项模板 | `WorkItemTemplate` | `templateId` + `templateVersionId` | workspace 模板目录或平台预置目录 | `draft -> published -> retired`；安装后生成本地配置草稿和 lineage |

约束：

- 所有对象都携带 `workspaceId`；除显式跨空间关系外，命令中的 `spaceId` 必须与对象归属一致。
- `typeKey`、`fieldKey`、`actionKey`、`roleKey` 和 `relationKey` 是稳定业务键；展示名可变，键在已发布后不可复用。
- 工作项显示编号不参与引用、权限或关联；内部关系、平台对象和 API 只使用 `workItemId`。
- 业务流程的 open/done/closed 等状态不是 `WorkItem.lifecycleStatus`；归档不会伪造流程完成，恢复也不会重置流程。
- 硬删除只允许未发布、无实例、无引用且有审计记录的草稿对象；历史版本、实例和关系使用软生命周期。

### 13.2 “项目是工作项类型之一”

`project` 是可安装的受保护初始 type key，和 requirement、task、bug、iteration、campaign、candidate、deliverable 使用相同 `WorkItem` 运行时。受保护仅限制删除系统键和破坏模板升级，不允许出现第二套 Project 实例表、Controller 或权限引擎。

| Team template | Work item type examples | Relations / workflow composition |
| --- | --- | --- |
| 研发 | project、requirement、task、bug、iteration、release | project 父子拆解 requirement/task；bug 关联 requirement；状态流 + 评审节点流 |
| 市场 | campaign、content、asset、channel_delivery、review | campaign 父子拆解内容和投放；素材依赖；创作状态流 + 审批节点流 |
| HR | hiring_plan、position、candidate、interview、onboarding | plan 关联 position；candidate 经过面试节点流；入职任务使用状态流 |
| 交付 | project、deliverable、risk、change、acceptance | project 父子拆解交付物；risk 阻塞；交付和验收使用节点流 |

空间可以直接容纳 task、candidate 或 content，不要求每个工作项存在 project 父项。层级只由 `RelationDefinition(kind=parent_child)` 表达，工作项规范模型不得包含强制 `projectId` 父外键。

### 13.3 配置定义、发布和运行实例

1. 草稿包含字段、布局、流程、角色、关系、默认视图和自动化引用的完整配置图。
2. 发布在单事务内完成结构校验、引用校验、权限校验和 hash 计算，生成不可变 `WorkItemTypeVersion`。
3. 新工作项必须显式绑定 `typeVersionId`；服务端不通过“当前版本”回推历史实例语义。
4. 发布失败不生成半版本，不修改 active version；重试使用幂等 publication key。
5. 回滚不是改写旧版本，而是从目标历史版本复制新草稿并发布更高版本。
6. 旧实例默认保持原版本。升级必须先生成 diff 和影响预览，再以 migration plan 显式执行。
7. diff 至少分类为 `additive`、`conditional`、`breaking`；字段移除、类型变更、状态/节点移除、角色和关系收窄属于 breaking。
8. 实例升级记录 from/to version、映射、默认值、拒绝项、操作者、时间、幂等键和结果；失败实例不改变原版本。
9. 模板安装采用 copy-with-lineage：生成空间本地草稿，记录 template/version 来源，不建立实时继承。
10. 模板升级生成三方差异候选草稿；本地覆盖必须保留或由管理员显式接受，不允许后台静默覆盖。

### 13.4 字段、表单和详情布局

字段合同：

- `FieldDefinition` 使用稳定 `fieldKey`，声明 type、typeConfig、defaultExpression、validationRules、requiredRule、readOnlyRule、search/sort/group capabilities 和授权规则。
- 第一阶段字段类型为 text、rich_text、number、boolean、single_select、multi_select、user、date_time、date_range、url、attachment、work_item_reference 和 computed。
- 系统字段 id、title、number、lifecycle、created/updated metadata 受保护；模板可以布局和授权，但不能改变存储类型。
- 默认值只在创建命令中由服务端求值；客户端预览不是事实来源。computed 字段服务端派生且不可直接写。
- 必填、有效性、只读和字段授权在每次写命令重新计算；条件显示只控制渲染，不构成服务端授权。
- 新版本移除字段时保留旧实例值和旧版本定义；是否迁移、清空或映射由显式升级计划决定。

布局合同：

- `create_form` 与 `detail_view` 分别版本化，引用 field key，不复制字段定义。
- 布局支持 section、tab、column、field control、relation control 和只读 summary control；控件配置不能改变字段类型和校验。
- 条件表达式只引用同一版本中已声明的字段、角色和流程投影；发布时检测悬空引用和循环计算。
- API 返回 `fieldSchema + layout + values + fieldDecisions`；提交只接受 field key/value 和实例版本，不接受客户端传入授权结果。

### 13.5 状态流与节点流

两类流程共享以下执行协议：

```text
WorkflowCommand(actionKey, workItemId, expectedVersion, idempotencyKey, input)
  -> authorize -> guard -> validate required fields/deliverables
  -> mutate runtime -> append history/outbox -> return availableActions
```

共享原语包括不可变 definition version、稳定 action key、guard、角色要求、字段要求、副作用声明、乐观锁、幂等键、历史、统一事件和权限解释。

| Dimension | State flow | Node flow |
| --- | --- | --- |
| Authoritative runtime | 单一 current state | 一个或多个 active node token |
| Graph | state + transition | stage + node + edge + branch/join |
| Completion | 进入终态 | 所有必需 terminal nodes 满足汇聚规则 |
| Assignment | action role / assignee guard | node owner、candidate role、会签参与者 |
| Concurrency | 单状态乐观锁 | token 级推进 + aggregate version，支持并行汇聚 |
| Projection | state 自身 | 可派生 summary status，但不能反写为真实节点状态 |

禁止把节点流降维保存为一个 status，也禁止为复用而把轻量状态流强制实现成节点 token 图。两者通过共同 command/event SPI 接入 UI、权限、审计、通知和自动化。任意代码插件节点不属于基础合同；扩展只能通过受控连接器、Webhook 和声明式自动化 action。

### 13.6 关系、层级和跨空间引用

`RelationDefinition` 冻结 sourceType、targetType、kind、direction、inverseLabel、source/target cardinality、spacePolicy、deletePolicy 和 permissionPolicy；`WorkItemRelation` 保存 definitionVersion、sourceId、targetId、状态、创建者和版本。

| Kind | Direction | Integrity rule |
| --- | --- | --- |
| `association` | 单向或双向展示 | 规范化端点后拒绝重复边 |
| `parent_child` | parent -> child | 同一 relation definition 下默认单父；必须为 DAG |
| `depends_on` | predecessor -> successor | 拒绝自环和有向循环；支持影响分析 |
| `blocks` | blocker -> blocked | 语义为 depends_on 的受保护视图，不重复写反向边 |

- 建立关系时必须具备 source/target 可见性、source relate action、target accept-link action；跨空间还需显式 `CrossSpaceGrant`。
- 无目标可见性时 API 只返回 forbidden reference，不泄露标题、类型、状态或路径。
- 归档工作项保留关系并投影为 archived；移除关系使用 tombstone/history。硬删除草稿对象前采用 `restrict`。
- 跨空间关系不自动同步字段、状态或成员。同步属于 S18 的独立 versioned rule，具有映射、方向、冲突、循环和补偿合同。
- 关系图按定义版本校验；升级 definition 不静默重写已有边。

### 13.7 分层授权与解释

授权计算固定顺序：

1. workspace 隔离、对象存在性、生命周期和安全策略 hard deny。
2. 企业 RBAC 只决定 create_space、manage_enterprise_templates、inspect_governance 等企业动作，不自动授予内容访问。
3. 空间成员资格、空间可见性和空间角色决定进入空间、配置和基础数据范围。
4. 工作项实例角色与 relation/data-scope 规则决定对象级 view/comment/edit/relate/transition/manage。
5. 当前 node owner/role 和 workflow guard 收窄可执行 action。
6. 字段授权对每个字段再次收窄 read/write；最严格的适用规则获胜。

默认空间角色为 owner、admin、member、guest：owner/admin 可配置空间，member 执行协作，guest 只访问显式范围。实例角色由 type version 定义，允许通过创建人、人员字段、固定成员和自动化绑定。企业管理员不自动成为空间成员，空间管理员也不自动看见策略排除的数据。

统一 `PermissionDecision` 至少返回 allowed、action、currentLevel、requiredLevel、reasonCode、policySources、policyVersion、subjectVersion、evaluatedAt 和 disclosureScope。`availableActions` 只能由同一服务端决策批量投影，前端不得自行补动作。

缓存 key 必须包含 workspace、subject、space、object、action、配置版本和成员/角色水位；组织、空间成员、实例角色、节点、字段规则或跨空间授权变化均发送失效事件。所有写命令记录 decision reference、策略版本和最终结果；拒绝日志不得包含不可见字段值。

### 13.8 三类 UI 与 API 边界

| Surface | Audience | Responsibilities | API / DTO boundary |
| --- | --- | --- | --- |
| 用户执行 UI | space member / guest | 我的工作、空间内容、视图、工作项详情、评论、附件、流转和关系 | `/api/project-spaces/{spaceId}/work-items...`；`UserWorkItem*` DTO |
| 空间配置 UI | space owner / admin | 类型、字段、布局、流程、角色、关系、视图、自动化和发布 | `/api/project-spaces/{spaceId}/configuration...`；`SpaceConfiguration*` DTO |
| 企业管理后台 | enterprise governance roles | 空间目录、企业模板、策略、配额、风险、迁移和审计排查 | `/api/admin/project-governance...`；`AdminProjectGovernance*` DTO |

用户 UI 默认展示业务语言和实例内容，不要求成员理解 type definition、field key 或 workflow graph。空间配置属于项目产品内的设置入口，不进入企业后台。后台不复用用户工作项详情作为治理主体，也不提供日常评论和流转入口。三类 DTO 不相互继承；共享仅限稳定标识、枚举和平台对象摘要。

### 13.9 事件与平台接入合同

规范平台对象类型为 `work_item`：Web path 为 `/project-spaces/{spaceId}/work-items/{workItemId}`，deep link 为 `colla://work-item/{workItemId}`。旧 `project` / `issue` objectType 只在迁移窗口通过 ID map 解析，最晚 S21 删除活动兼容。

统一 outbox 事件 envelope：

```text
eventId, eventType, schemaVersion, occurredAt,
workspaceId, spaceId, workItemId, typeKey, typeVersionId,
aggregateVersion, actorId, correlationId, causationId,
changedFieldKeys, payload, disclosureClass
```

首批事件目录包括 work_item.created/updated/archived/restored、field.changed、workflow.action_executed/state_changed/node_activated/node_completed、relation.added/removed、role.assigned/unassigned 和 configuration.version_published。消费者按 eventId 幂等，未知 schema version 进入 dead letter，不猜测字段。

- Search 消费事件构建 work_item 投影，并在查询和 resolver 两层执行同一 visibility contract。
- Notification 订阅语义事件和用户偏好，不从数据库轮询猜测状态；必要安全/授权通知不可关闭。
- Audit 记录 command、decision reference、before/after 摘要、correlation 和结果；事件不是审计记录替代品。
- File 只通过 file service 和 usage relation 关联，不保存拼接 URL。
- IM 的空间群和工作项会话是可选策略/自动化，不是每个工作项的硬依赖；成员同步只有一个明确 source of truth。
- Knowledge 通过 `work_item` 平台对象和关系 facade 建立引用，不读取 project runtime 私有表。
- Workspace、Search、Admin 等模块使用 query facade 或事件投影，不直接依赖 project Repository/私有表。

### 13.10 ADR 决策与禁止模式

| ADR | Decision | Rejected alternative | Reason |
| --- | --- | --- | --- |
| ADR-PP-001 | `ProjectSpace` 是配置、成员和数据边界 | 继续让 project 实例承担所有容器职责 | 无法容纳无 project 父项的市场、HR 和轻量事项 |
| ADR-PP-002 | project 是受保护模板产生的工作项类型 | 为 project 保留独立运行时 | 会形成两套字段、流程、权限和平台对象路径 |
| ADR-PP-003 | 发布配置不可变，实例绑定明确版本 | 后台修改 active config 即时影响全部实例 | 历史行为不可解释且无法安全回滚 |
| ADR-PP-004 | 状态流/节点流共享协议但运行时分离 | 把两者强制保存为同一图或同一 status | 轻量模型过重，复杂并行语义丢失 |
| ADR-PP-005 | 层级和依赖由 versioned relation graph 表达 | 在 work item 上硬编码 project/parent/requirement FK | 关系类型不可配置，跨团队模型被固定 |
| ADR-PP-006 | 分层授权由统一服务端 decision 解释 | 前端动作、成员表和通用 ACL 各自判定 | 会重现 M1 已证实的授权分裂 |
| ADR-PP-007 | 规范平台对象为 `work_item` | 永久保留 project/issue 对象双轨 | 搜索、链接、通知和关系长期分叉 |
| ADR-PP-008 | 模块化单体内先建 package/facade/event 边界 | 立即拆微服务或继续私有表直读 | 前者增加分布式复杂度，后者阻断可迁移边界 |
| ADR-PP-009 | 模板采用 copy-with-lineage 和显式升级 | 安装后实时继承模板 | 本地差异可能被静默覆盖，历史不可复现 |
| ADR-PP-010 | M2 不提前冻结动态字段物理存储 | 直接选择 JSONB 或 EAV | M2 尚无查询、索引、迁移和性能证据；本决策已由 M3 的 ADR-PP-011 收口 |
| ADR-PP-011 | 动态字段采用规范 JSONB + 按能力声明的类型化同步投影 | 纯 JSONB、全量 EAV、每字段 DDL 列 | 保留原子写与历史值，同时为常用查询提供类型正确的稳定索引，避免全字段写放大和 DDL 抖动 |
| ADR-PP-012 | 能复用时保留旧 UUID，并始终写显式 legacy ID map | 全部换 ID 或只依赖 ID 相同而不建映射 | 深链稳定且碰撞、重定向、批次、校验和退役状态可审计 |
| ADR-PP-013 | 以 legacy project 为迁移单元，批次可 dry-run、暂停、幂等重试和校验 | 一次性大迁移或按表无边界搬运 | project 当前是成员、事项和关联的天然一致性边界 |
| ADR-PP-014 | 新写只进入规范模型，迁移窗口允许受控旧读回退，禁止双写 | 长期双读双写 | 消除双事实源和难以证明的补偿一致性 |
| ADR-PP-015 | 权威查询投影与工作项同事务维护，搜索/分析投影异步且可重建 | 所有投影异步或把投影当事实源 | 交互查询获得读己之写，派生投影漂移可检测并重放 |
| ADR-PP-016 | 状态流与节点流共享命令、历史和 outbox，分别持有 current state 与 token runtime | 强制共享同一运行表 | 统一平台接入但不牺牲两类流程的权威语义 |

禁止重新引入：按团队复制顶层模块；按类型新增独立实例表和 Controller；强制每个工作项存在 projectId；修改 published version；用条件显示代替字段授权；前端补算 availableActions；管理员默认读取全部内容；任意代码插件节点；关系边隐式同步字段；跨模块直读 project 私有表；无限期双读双写；把流程终态等同对象归档；把模板 live link 当版本管理；把 spike DDL 直接复制为生产 schema；让类型化投影成为不可重建的事实源。

## 14. S01-M3 物理模型、迁移与兼容合同

本节是 PROJECT-PLATFORM-S01-M3 的迁移合同 v1。它基于隔离 PostgreSQL 16 spike 冻结实施方向，不声明生产 schema、迁移作业或兼容适配器已经交付。测试只证明候选机制可运行；生产容量、权限和故障演练必须在后续 Stage 继续验收。

### 14.1 动态字段存储与查询投影

选择混合方案：`project_work_items.field_values` JSONB 是规范事实；只有在已发布 `FieldDefinition` 中声明 query、sort 或 group capability 的字段，才同步维护 `project_work_item_field_projections` 类型化行。

| 方案 | 优势 | 主要代价 | 决策 |
| --- | --- | --- | --- |
| 纯 JSONB | 原子写、读取和迁移简单 | 任意动态组合需表达式索引，类型比较和索引治理困难 | 不作为完整查询方案 |
| 全量类型化行 | 类型和单字段索引清晰 | 每次读取需组装，所有字段产生写放大，多值/复合类型复杂 | 拒绝全量使用 |
| JSONB + 能力投影 | 规范值原子保存，热点字段有类型索引，投影可重建 | 需要同事务一致性和漂移巡检 | 采用 |
| 每自定义字段物理列 | SQL 直接 | 发布配置触发 DDL、锁和索引膨胀 | 禁止 |

物理合同：

- 工作项写命令在同一事务内校验字段定义、更新规范 JSONB、替换受影响的类型化投影、递增 aggregate version，并写 outbox；事务失败时两者均不提交。
- 投影键至少包含 workspace、space、work item、type version、field key、value ordinal 和 value type；值落入 text/number/boolean/timestamp/reference 等互斥类型列，并由约束阻止类型错位。
- 多值字段一值一行并保留 ordinal；rich text、attachment 和不可查询复合值只保留规范 JSONB。computed 字段保存计算来源版本，查询投影由服务端计算结果产生。
- capability 只能由发布配置启用。索引采用受控模板 `(workspace_id, space_id, field_key, typed_value, work_item_id)`；不允许用户输入直接拼 DDL、列名或表达式。
- 启用 capability 先建立 projection build job，记录 high-water mark，完成双重校验后开放查询；停用先移除查询入口，观察窗口结束后再清理投影和索引。
- 交互视图只访问系统列和权威类型化投影。搜索、分析和度量读模型由 outbox 异步维护，必须暴露 lag、dead letter 和 rebuild，不得反写规范值。
- 漂移巡检按 type version/field key 比较规范值 hash、投影 count/hash；修复通过幂等重建作业，不在读取路径静默补写。

隔离 spike 使用 20,000 个工作项、team 文本和 score 数值组合过滤，三方案均返回 200 行；最新 15 次热查询测得 JSONB p95 1.072ms、类型化行 p95 1.631ms、混合投影 p95 2.393ms。该结果仅用于排除机制不可行，不代表生产 SLO。S04 尚无规范工作项实例，其容量基线只验证字段配置目录：120 个字段和 2400 个选项在真实 PostgreSQL/Flyway 环境中查询不超过 3 秒，并保持 workspace/space/type/status 复合索引计划。100,000 工作项、至少 5 个可查询字段和并发负载下索引命中查询 p95 <= 200ms 的生产基线由 S07 运行时和 S13 高级查询共同验收；无 capability 的动态过滤应被拒绝或转异步导出。

### 14.2 规范 ID、展示编号和旧标识映射

- 在 workspace 内无冲突时，legacy `projects.id` 和 `issues.id` 直接复用为对应 project/work item 的 `workItemId`；`ProjectSpace` 使用新 UUID。即便 UUID 复用，仍必须写映射记录。
- `project_legacy_id_map` 唯一键为 `(workspace_id, source_type, source_id)`，记录 target type/id、spaceId、batchId、source checksum、target checksum、mapping status、collision reason 和 timestamps。目标冲突时生成新 UUID 并记录原因，禁止覆盖已有目标。
- 旧 `project_key` 和 `issue_key` 作为不可复用 alias/display number 保留，不参与授权、外键或事件 identity。新工作项编号由 `(space_id, type_definition_id)` 原子 counter 分配；迁移预留值必须大于该范围 legacy 数字最大值。
- 旧 Web path、deep link、平台对象、搜索和审计引用先按 source type/id 查 map，再返回规范 `work_item`；同一 source 不得解析为多个 target。未迁移、冲突、不可见和已退役分别返回稳定 reason code。
- map 在 S21 活动兼容退役后仍作为审计归档保留；不得根据“恰好同 UUID”在运行时猜映射。

### 14.3 分批迁移模型

迁移批次 `MigrationBatch` 状态为 `planned -> dry_run_validated -> running <-> paused -> completed`，失败进入 `failed`，写切流前允许进入 `rolled_back`。一个 legacy project 是最小一致性单元：空间、project 工作项、成员、iterations、issues 及其评论、附件、关系、验收和活动记录在单元事务内迁移。

固定步骤：

1. preflight 读取 M1 数据画像，校验 workspace、孤儿、ID/编号冲突、成员身份、附件引用和权限可解释性；记录 source high-water mark。
2. dry-run 只生成计划、源计数/hash、目标预估、映射冲突和失败清单，不写规范业务表。
3. worker 通过 `for update skip locked` 领取 migration unit；每单元单事务执行 upsert，所有写携带 batch/unit id 和 source checksum。
4. source -> target 映射为 project -> ProjectSpace + project WorkItem，member -> space membership/role，issue/iteration -> type/version + WorkItem，comments/attachments/verification/activity/relations -> 规范子模型或平台引用。
5. 单元提交前比较类型计数、稳定字段 hash、关系端点和附件 usage；失败完整回滚该单元并写脱敏错误分类。
6. batch 完成后比较总数、聚合 checksum、ID map、编号水位、权限抽样和跨模块 resolver；不一致不得进入 cutover-ready。

重试使用 batchId + unitId + source checksum 幂等：checksum 未变时 upsert 不产生重复，变化则要求重新 preflight，禁止悄悄覆盖已验收目标。失败清单包含 owner、可重试性、修复动作和关联 unit。写切流前可按 batch provenance 删除未被新模型修改的目标；写切流后回退只切回读取路由，不重新开放旧写，修复使用规范模型补偿命令。

隔离 spike 迁移 2 个 project 与 2 个 issue，二次执行后 target/map 仍为 4，legacy/target UUID 全部一致，源/目标 checksum 一致，编号 counter 分别推进到 8 和 13。生产迁移还需覆盖 M1 的评论、附件、关系、权限和跨模块画像。

### 14.4 读取兼容、写切流与退役

| Phase | Authoritative write | Read behavior | Entry/exit evidence | Owner / latest removal |
| --- | --- | --- | --- | --- |
| observe | legacy | legacy，采集 API/对象/页面使用 | M1 inventory 和零未知调用方 | S01 / architecture |
| shadow migrate | legacy | legacy；后台比对规范投影，不返回双结果 | batch checksum、权限抽样、resolver 对照 | S07 / migration |
| canonical write | canonical only | 已迁移读 canonical，未迁移受控回退 legacy | workspace flag、错误率/延迟/漂移 dashboard、kill switch | S07 / project runtime |
| canonical default | canonical only | canonical；旧 route 经 ID map 重定向 | 旧读回退率趋零，平台对象链路通过 | S07 / API owners |
| old write closed | canonical only | canonical；旧写返回 stable gone/conflict + canonical location | 零旧写调用方、回退演练 | S07 / API owners |
| compatibility retired | canonical only | canonical | 归档 map、删除活动 DTO/path/repository | S21 / platform owners |

- 严禁业务双写。shadow compare 只比较读取结果，不形成第二事实源；canonical write 开启后，kill switch 只能暂停新写或切换 canonical 读取版本，不能恢复 legacy 写。
- feature flag 至少按 workspace，必要时细化到 space；每次变化记录 actor、reason、batch、指标快照和回退点。
- 兼容注册表逐项记录旧 API、DTO、前端 route、object type、search document、notification/audit resolver、file usage 和 IM membership 的 owner、调用量、退出条件、告警与最晚 Stage。
- 授权先于读取适配执行；对无权目标不可通过 map、错误文本、延迟差异或 legacy fallback 泄露存在性。

### 14.5 配置版本绑定与实例升级

- published/superseded type version 由数据库约束和服务合同共同禁止 update/delete；每个 work item 明确保存 `type_version_id` 和 `aggregate_version`。
- 发布新版本不改变旧实例。升级先固定 from/to config hash，生成字段、布局、流程、角色和关系 diff；breaking 项无显式 mapping/default/approval 时拒绝执行。
- 升级命令使用 itemId、fromVersion、toVersion、expected aggregate version 和 idempotency key；单实例事务内转换规范值、重建投影、迁移流程运行时并写 history/outbox。
- 部分失败保持原版本并进入失败清单。回滚通过从历史配置复制并发布更高版本后执行反向升级计划，不修改旧版本或把 version number 倒退。

隔离 spike 证明 published version 更新被拒绝，旧实例在 v2 发布后仍绑定 v1，显式乐观升级仅成功一次，过期升级更新 0 行，回退候选以新 v3 表达。

### 14.6 状态流与节点流运行时边界

共享 `WorkflowCommand`、authorization/guard SPI、aggregate version、idempotency key、history/outbox envelope 和 available action projection。状态流权威表只保存 current state；节点流权威表保存 aggregate instance 与一个或多个 active token。两者不能互相查询私有运行表来决定动作。

隔离 spike 使用同一 history schema 记录两类命令，状态流 stale command 更新 0 行，节点流保留 active token，重复 idempotency key 被拒绝；同时证明 state item 不产生 node token、node item 不产生 current-state row。S08/S09 实现时可共享 orchestration facade，但必须保留独立 repository 和不变量测试。

### 14.7 P0/P1 风险登记

| Priority | Risk | Prevention | Detection | Rollback / containment | Owner Stage |
| --- | --- | --- | --- | --- | --- |
| P0 | 授权放大或跨空间泄露 | 统一 decision、最小披露、迁移权限映射 | legacy/canonical decision 对照与拒绝样本 | 关闭 workspace cutover，保持 canonical 写暂停 | S02/S07/S11 |
| P0 | 数据丢失、孤儿或关系错连 | project 单元事务、FK/端点预检、provenance | count/hash、孤儿与关系抽样 | 写切流前删除 batch target；之后补偿 | S07 |
| P0 | ID/编号冲突破坏深链 | 显式 map、冲突分支、原子 counter | 唯一约束、resolver 对照、counter 水位 | 暂停单元并重映射，不覆盖目标 | S07 |
| P0 | 双事实源产生不可恢复漂移 | canonical-only write，禁止双写 | 旧写调用量和 shadow compare | 关闭旧 route；暂停 canonical 新写 | S07 |
| P0 | 配置升级改变历史语义 | immutable version、显式 diff/mapping | config hash、绑定版本和失败清单 | 保持旧绑定；发布更高回退版本 | S06-S09 |
| P0 | 状态/节点运行时语义串线 | 分离权威 runtime，共享协议而非表 | 不变量、历史和 token/state 对照 | 停止相关 type upgrade，恢复原版本 | S08-S09 |
| P1 | 查询投影漂移 | 同事务权威投影、幂等 rebuild | value hash/count、lag/dead letter | 重建投影并暂时禁用该 capability | S04/S10 |
| P1 | backfill 锁、索引或延迟失控 | 小批次、在线索引、限速和暂停 | DB lock/CPU/replica lag/p95 | pause batch、drop pending index、降速 | S04/S07 |
| P1 | 跨模块仍读 legacy 私有表 | facade/event contract 和兼容注册表 | dependency scan、旧表访问指标 | 保留只读 adapter，阻断移除 | S07-S21 |
| P1 | IM/搜索/通知/审计/文件引用漂移 | 统一 `work_item` resolver 和 outbox | 按 consumer 对账、dead letter | consumer 回退到映射 resolver 并重放 | S07-S15 |
| P1 | 回退流程只存在于文档 | workspace flag、runbook 和隔离演练 | 演练时长、RTO 和告警证据 | No-Go，不进入下一 cutover phase | S07 |

### 14.8 后续 Stage 实施输入

S02 只建立 ProjectSpace、成员/角色、空间 API/DTO/授权边界和 legacy project -> space/member 显式映射；当前 project/issue 业务写仍保持 legacy 权威，不在类型定义和工作项运行时存在前提前切流。S03 建立 WorkItemTypeDefinition 与受保护 project 类型，其冻结准入包见本文第 19 节；S04 落动态字段 JSONB + capability projection 和索引预算；S06 完成不可变配置发布后，S07 才交付 migration batch/unit/map/failure schema、dry-run/preflight、project 单元迁移器、checksum、兼容注册表、workspace cutover flag、resolver、规范工作项写入与回退 runbook。

S07 不得在没有 M1 全量数据画像和真实备份恢复演练时执行生产写迁移。S08/S09 接收双流程运行时合同，S21 删除活动兼容。生产 Flyway、Repository、API 和 UI 应在各 Stage 落地，S01 只交付决策和可运行机制证据。

## 15. 总体迁移顺序

1. 观测旧 API、表、对象链接和页面使用情况。
2. 建立规范模型、ID map、兼容注册表和只读 resolver。
3. dry-run 后按 project 单元批量迁移，生成校验和失败清单。
4. 新写入切到规范模型，旧读取保留受控回退窗口。
5. 校验平台对象、搜索、通知、审计、附件、评论、权限和 IM 引用。
6. 关闭旧写入并监控，完成回退演练。
7. 按注册表删除旧 API、DTO、前端路径和运行代码。
8. 历史 Flyway、ID map 和归档报告保持不可变。

禁止无限期双读或双写；每个兼容面必须有 owner、指标、退出条件和最晚删除 Stage。

## 16. 非功能约束

- 所有写操作具备乐观锁或幂等语义。
- 配置发布、流程流转、关系同步和自动化执行可审计。
- 动态字段查询和视图有明确性能预算。
- 跨空间和搜索结果遵守最小披露。
- 迁移支持 dry-run、分批、校验、暂停、重试和回退。
- 浏览器关键闭环使用真实 API 和隔离数据验证。
- S21 前不得宣称多团队项目平台完成；模板可配置和真实团队使用是最终证据。

## 17. S01-M4 冻结的 S02 准入包

S02 的交付边界是“空间、成员和空间治理”，不是提前实现类型、动态字段或规范工作项。以下名称和语义是 S02 拆 Task 的固定输入；实现可以按模块内部命名约定调整 Java 类名，但不得改变所有权、唯一性、授权和迁移边界。

### 17.1 Schema 输入

| Table | Required columns / constraints | Ownership and invariant |
| --- | --- | --- |
| `project_spaces` | id UUID PK；workspace_id；space_key；name；description；visibility；lifecycle_status；created_by；aggregate_version；created/updated_at；`unique(workspace_id, space_key)` | workspace 隔离；space_key 发布后不可复用；生命周期仅 active/disabled/archived，非空空间禁止硬删 |
| `project_space_members` | id UUID PK；workspace_id；space_id；user_id；membership_status；source_kind；source_id；joined/removed_at；aggregate_version；`unique(space_id, user_id)` | 成员资格是空间内容访问前置事实；removed 保留审计，不能通过删除行抹去历史 |
| `project_space_role_assignments` | id UUID PK；space_id；member_id；role_key；created_by/at；`unique(member_id, role_key)` | S02 只启用 owner/admin/member/guest 内置角色；至少一名 active owner，由事务内约束服务保护 |
| `project_space_invitations` | id UUID PK；space_id；invitee_user_id；target_role_key；status；expires_at；invited_by；idempotency_key；created/responded_at | 同一活动邀请幂等；过期、接受、拒绝和撤销可审计；接受时重新授权和校验空间状态 |
| `project_legacy_space_maps` | workspace_id；legacy_project_id；space_id；migration_batch_id；source/target_checksum；status；created/verified_at；PK(workspace_id, legacy_project_id)，unique(space_id) | 一个 legacy project 在第一阶段映射一个 space；不得根据相同 UUID 猜映射；只映射空间，不创建规范 WorkItem |
| `project_space_migration_batches` | id UUID PK；workspace_id；status；high_water_mark；source/target_count/checksum；failure_count；started/completed_at | 只负责 project/member -> space/member 的 S02 迁移；完整 issue/work-item 批次属于 S07 |

所有表必须有 workspace 一致性约束或仓储级强校验、必要 FK、审计/outbox 写入和乐观版本。S02 不创建 `project_work_items`、动态字段投影或 type version 表；这些分别由 S03/S04/S07 按依赖交付。

### 17.2 API 和 DTO 输入

| Surface | Canonical API | DTO family | Required behavior |
| --- | --- | --- | --- |
| 用户空间目录 | `GET/POST /api/project-spaces` | `UserProjectSpaceSummary/CreateRequest` | 只返回当前用户可发现或可进入空间；创建重新检查 enterprise `project_space.create` |
| 用户空间详情 | `GET/PATCH /api/project-spaces/{spaceId}` | `UserProjectSpaceDetail/UpdateRequest` | 返回 availableActions；expectedVersion 乐观锁；不可泄露不可见空间名称 |
| 生命周期 | `POST .../{spaceId}:disable`、`:restore`、`:archive` | `ProjectSpaceLifecycleRequest/Result` | 幂等键、原因、版本和稳定 reason code；disabled 禁止普通协作写，archived 默认只读 |
| 成员与邀请 | `GET/POST .../{spaceId}/members|invitations`，`PATCH/DELETE .../members/{memberId}` | `ProjectSpaceMember*`、`ProjectSpaceInvitation*` | 邀请、接受、角色调整和移除均服务端授权；最后 owner 不可移除/降级 |
| 空间设置 | `GET/PATCH /api/project-spaces/{spaceId}/settings` | `SpaceConfigurationSettings*` | 只承载空间名称、可见性和成员策略，不提前暴露 type/field/workflow 配置 |
| 企业治理 | `GET /api/admin/project-governance/spaces` 及 `/{spaceId}` | `AdminProjectSpaceGovernance*` | 目录、策略、风险、迁移状态和审计入口；不返回评论、事项正文等日常协作内容 |
| 迁移治理 | `POST /api/admin/project-governance/migrations/spaces:dry-run|:execute|:pause|:resume` | `AdminProjectSpaceMigration*` | 仅管理员治理角色；dry-run、批次、失败清单和 checksum，不开放普通用户调用 |

错误合同至少区分 not_found_or_hidden、disabled、archived、version_conflict、last_owner、already_member、invitation_expired、migration_conflict。客户端不得根据 HTTP 文本补算授权；服务端 `PermissionDecision` 和 availableActions 是唯一动作来源。

### 17.3 授权、可见性和生命周期输入

- enterprise action 只包含 `project_space.create`、`project_space.inspect`、`project_space.migrate` 和企业策略治理，不自动授予空间内容访问。
- space action 至少包含 view、update、disable、restore、archive、member_view、member_invite、member_remove、role_assign 和 settings_manage；owner/admin/member/guest 的默认矩阵由服务端种子固定并可解释。
- visibility 采用 private、discoverable、workspace：private 仅 active member 可发现；discoverable 可见最小摘要但进入仍需成员/邀请；workspace 允许 workspace 用户以 guest 进入。任一模式都不能绕过 hard deny 或字段/对象级后续授权。
- disabled 空间允许 owner/admin 治理和恢复，普通成员只读最小状态；archived 空间默认全员只读，仅 owner/admin 可恢复。生命周期变化不伪造工作项流程状态。
- 成员邀请、移除、角色变更和生命周期命令必须写 decision reference、before/after、reason、actor、correlation 和 outbox；IM 群只消费明确同步策略，不能反向成为成员事实源。

### 17.4 Legacy project -> space/member 映射输入

1. 每个 legacy project 生成一个新 ProjectSpace，并写显式 map；project 自身仍由 legacy API/表承载，直到 S07 把它迁为 project WorkItem。
2. project owner -> space owner，member -> member，viewer -> guest；无法解析用户、重复角色和最后 owner 异常进入失败清单，不自动扩大权限。
3. S02 先 shadow build 和 checksum，对照 33 个项目、34 条成员关系及 31 条 IM 漂移；IM 漂移只登记，不把群成员并入空间。
4. 用户空间目录切换与 legacy project 业务写切换分离。S02 可以展示映射空间入口，但当前 `/projects`、issue API、平台 `project/issue` 对象和业务写仍保持原权威来源。
5. S02 回退只关闭空间入口/读取 flag 并保留 map；不得删除 legacy project/member，也不得启用双写。完整对象映射、规范写切流和旧写关闭在 S07 执行。

## 18. 项目平台验证分层

| Change class | Milestone checkpoint | Stage finish | Browser / migration evidence |
| --- | --- | --- | --- |
| 纯合同或 ADR | planning + documentation + affected compile | stage targeted checks | 明确 not-required 原因，不伪造 UI 证据 |
| schema/repository | backend compile + targeted unit | Testcontainers integration + empty Flyway migration | 迁移前后 count/hash/constraint；不写共享开发库 |
| API/permission | targeted controller/service tests | 正反权限矩阵、幂等、乐观锁和审计/outbox | 核心动作使用真实 API，mock 只允许非核心外部依赖 |
| 用户或配置 UI | frontend lint + affected tests/build | real isolated browser critical flow | 登录、刷新、直接深链、无权/停用状态和恢复路径 |
| migration/cutover | dry-run fixture + idempotent batch test | 备份/恢复、重复执行、失败重试、checksum 和 rollback drill | 治理 UI 只验证触发/状态，数据正确性由数据库证据判定 |
| Stage final | affected evidence complete | `route-final` full backend, package, frontend, collaboration and static/security/Flyway gates | 该 Stage 所有用户闭环的 fresh real evidence；无 UI Stage 可具体说明 not-required |

S02 最小真实浏览器集合包括：创建空间；private/discoverable/workspace 可见性；停用/恢复/归档；邀请与接受；角色调整；最后 owner 拒绝；无权用户最小披露；legacy 映射空间入口。其后每个 Stage 按新增用户闭环扩展，不在中间 Milestone 重复跑全仓全量测试。

## 19. S02-M5 冻结的 S03 准入包

S03 的交付边界是“工作项类型定义底座”：类型 schema、标识、生命周期、类型管理 API、空间配置 UI 和研发预置类型模板。S03 不交付动态字段（S04）、表单与详情布局（S05）、配置草稿/发布/版本升级完整流水线（S06）、工作项实例与迁移（S07）。以下名称和语义是 S03 拆 Task 的固定输入；实现可以按模块内部命名约定调整 Java 类名，但不得改变所有权、唯一性、授权和迁移边界。空间归属、空间角色边界和 legacy 责任已在 S01/S02 冻结，S03 不重新讨论。

### 19.1 Schema 输入

| Table | Required columns / constraints | Ownership and invariant |
| --- | --- | --- |
| `project_work_item_types` | id UUID PK；workspace_id；space_id；type_key；name；icon；description；sort_order；status；is_system；current_version_id；created_by/at；updated_at；aggregate_version；`unique(space_id, type_key)` | 类型归属空间；typeKey 在 space 内永久唯一且发布后不可复用；status 仅 active/disabled/retired；is_system 受保护类型禁止删除和改键 |
| `project_work_item_type_versions` | id UUID PK；workspace_id；space_id；type_definition_id；version_number；config_hash；status；config JSONB；created_by/at；published_by/at；`unique(type_definition_id, version_number)`；published/superseded 行禁止 update/delete | S03 既有 schema 暂时允许 draft/published/superseded；其中 draft 是 S06 必须迁移或拒绝的遗留状态，不是后续草稿权威。S06 收紧后只允许 published/superseded，版本创建后不可变 |

约束：

- 两张表均携带 workspace_id，并使用 `(workspace_id, space_id)` 复合外键指向 `project_spaces`，不跨 workspace 或跨空间建立关系。
- `type_key` 使用 `[a-z][a-z0-9_]*`，长度有界；展示名可变，键不可变。
- S03 的版本只有“创建类型时同时生成首个 published 骨架版本”一种来源；config 骨架只含标识与展示语义，不含字段、布局、流程或角色图。草稿、后续版本、diff 和升级流水线由 S06 交付。
- S03 不创建 `project_work_items`、动态字段投影、布局或流程表；这些分别由 S07、S04、S05、S08/S09 按依赖交付。

### 19.2 API 和 DTO 输入

| Surface | Canonical API | DTO family | Required behavior |
| --- | --- | --- | --- |
| 空间类型配置 | `GET/POST /api/project-spaces/{spaceId}/configuration/types`，`PATCH .../types/{typeId}`，`POST .../types/{typeId}:disable|:restore|:retire|:copy`，`PUT .../types:reorder` | `SpaceConfigurationWorkItemType*` | 仅空间 owner/admin 可写；复制生成新 typeKey 的本地副本；排序只影响展示顺序 |
| 用户执行侧类型摘要 | `GET /api/project-spaces/{spaceId}/work-item-types` | `UserWorkItemTypeSummary` | 只返回 active 类型的展示语义（key、名称、图标、排序）；不暴露 config、版本或停用类型 |
| 企业治理 | 不开放类型配置写入口；治理目录只读类型计数 | `AdminProjectSpaceGovernance*`（只读扩展） | enterprise `project.manage` 不因治理身份获得空间类型配置权 |

错误合同至少区分 not_found_or_hidden、type_key_conflict、version_conflict、system_type_protected、retired_type、invalid_type_key。服务端权限决策与 availableActions 是唯一动作来源；客户端不得根据 HTTP 文本补算授权。

### 19.3 授权和生命周期输入

- 空间 owner/admin：创建、编辑、复制、排序、停用、恢复、retire 类型；member/guest：只读 active 类型摘要；非成员：最小披露，不确认空间与类型存在性。
- 企业 RBAC（`project.manage`）只治理空间状态，不授予类型配置或类型内容访问。
- 系统预置类型 `project`、`requirement`、`task`、`bug`、`iteration`、`release` 作为受保护初始模板按空间启用/停用：不允许删除 typeKey，不允许改变存储语义，允许空间级停用与排序。
- 生命周期：type `active <-> disabled -> retired`，retired 不可新建实例（S03 无实例）但定义与历史版本可读；S03 物理 schema 中的 version draft 仅是待 S06 收紧的兼容状态，规范 version 生命周期为 `published -> superseded` 且创建后不可变。可变编辑只发生在 `ConfigurationDraft`。
- 全部类型写操作记录 actor、对象、前后状态、request id 并写审计/outbox；幂等重复请求收敛。

### 19.4 迁移和兼容输入

- S03 不迁移任何 legacy project/issue 数据，不改变 legacy 业务写路径，不建立双写；legacy 兼容边界维持 S02-M4 冻结口径。
- 研发预置类型映射只表示“类型定义成为配置”：在空间内启用受保护模板，不产生任何工作项实例，不影响 `/projects`、`/issues` 运行时、平台对象 `project`/`issue` resolver 或 IM 项目群。
- 兼容约束以负例测试固化：停用/retire 类型不得破坏既有引用（S03 引用集合为空，约束防止后续 Stage 回归）；不得出现第二套 Project 实例表、Controller 或权限引擎（ADR-PP-002）。
- S03 准入评审时需复核：S04 字段定义挂在类型版本 config 图内的挂载点、S06 草稿/发布流水线的版本边界、S07 实例绑定 `type_version_id` 的合同不被 S03 实现堵死。

### 19.5 S03-M4 已实现的预置安装合同

- 研发预置目录版本为 `development-v1`，顺序为 `project/requirement/task/bug/iteration/release`；目录只携带展示语义，不携带动态字段、布局、流程或角色图。
- 常规新空间和 legacy 迁移空间在空间创建事务内安装完整目录；既有 active 空间在应用启动后逐空间事务补齐。空间行锁保证并发收敛，已存在系统类型不覆盖，自定义同 key 返回结构化冲突清单。
- 系统类型来源通过配置 DTO 的 `source=development_preset` 与 `presetCatalogVersion` 解释；企业治理面仍只有状态计数，不获得配置写权限。
- 数据库保护允许系统类型启停和排序，拒绝改键、覆盖展示定义、retire 与物理删除。legacy 迁移回滚仅通过 transaction-local 仓储清理通道移除整个迁移空间所属定义，不开放为 API 或普通 SQL 能力。
- 首次安装写一条空间级审计和 outbox 事件；无变更重放不重复。legacy 回滚后重迁使用新的类型生命周期事件标识，避免把合法重迁误判为重复。
- 兼容测试把类型表纳入 legacy 写路径 hash，且确认不存在 `project_work_items` 或第二套实例 API；S03 对现有 `/projects`、`/issues` 和 resolver 保持零切流。

## 20. S03-M5 冻结的 S04 准入包

S03 评审结论为 **Go S04**。S04 的唯一交付边界是动态字段定义、类型注册、选项、默认值、校验规则及其可查询投影合同；S04 不创建工作项实例，不迁移 legacy issue，不实现表单布局、流程、完整配置草稿/发布或版本升级。S03 已发布的 v1 骨架保持不可变，S04 的配置编辑结果必须等到 S06 由新版本发布事务物化，不能原地修改 v1。

### 20.1 Schema 输入

| Concern | S04 required contract | Boundary |
| --- | --- | --- |
| 字段定义 | 字段有永久 UUID、workspace_id、space_id、type_definition_id、field_key、名称、类型、状态、排序和 aggregate_version；`field_key` 在类型内永久唯一 | 不使用动态 DDL 为每个字段建列，不把字段值写入 legacy `issues` |
| 类型注册 | 首批至少覆盖 text、number、boolean、single_select、multi_select、user、date、datetime、url、attachment、work_item_reference；每类声明 storage kind、operators、sort/filter/index capability | 类型能力由服务端注册表解释，客户端不得自行推断序列化或操作符 |
| 选项与默认值 | 选项使用稳定 option key、显示名、颜色、排序和启停状态；默认值按字段类型规范化并校验 | 删除或停用选项不得静默改写历史值；S04 无实例时只冻结合同与定义行为 |
| 校验规则 | required、长度/范围、格式、允许值和引用约束使用结构化规则，规则 schema 可版本化 | 条件显示、布局和字段级授权属于 S05；跨字段复杂公式不进入 S04 |
| 查询投影 | 为字段定义列表、type/version 挂载、key/status/sort 查询建立稳定索引；JSONB 配置使用可控 GIN/表达式索引或 capability typed projection | 只有真实查询计划和基准证明需要时才增加投影；禁止无边界地为每个字段生成索引 |

字段定义的 workspace/space/type 关系必须使用复合约束，任何跨 workspace 或跨空间挂载均由数据库和 Repository 双重拒绝。字段配置需要稳定规范序列化与哈希输入，但 S04 不直接产生新的 published type version。

### 20.2 API 与 DTO 输入

| Surface | Required behavior | Authorization |
| --- | --- | --- |
| 字段类型目录 | 返回可用字段类型、capability、操作符和配置 schema，不返回内部实现类名 | 有效空间成员可读取执行所需摘要；配置细节只对 owner/admin |
| 字段定义配置 | 列表、创建、编辑、停用/恢复、排序；请求携带 aggregate version 与 request id | 仅空间 owner/admin 可写；member/guest 只读已生效摘要；非成员和企业管理员最小披露 |
| 选项与规则 | 以字段定义为聚合边界原子修改，服务端执行类型兼容、重复 key、默认值和规则校验 | 与字段定义相同，不新增第二套权限引擎 |
| 查询能力描述 | 返回字段支持的 filter/sort/operator，不返回工作项值 | 只描述能力；真实实例查询属于 S07/S13 |

错误合同至少区分 field_key_conflict、field_type_unsupported、invalid_field_configuration、invalid_default_value、invalid_validation_rule、version_conflict 和 not_found_or_hidden。动作能力继续由服务端 `availableActions`/policy 计算，全部写操作接入审计、outbox 和幂等回执。

### 20.3 S06/S07 扩展合同

- S04 字段定义是配置编排输入；不得修改 S03 published v1。S06 建立 draft 后，把字段定义、选项和规则物化为完整 config graph，校验后发布新的不可变 type version，并切换 `current_version_id`。
- S07 的 `project_work_items` 必须显式保存 `type_definition_id` 与 `type_version_id`；创建实例时锁定当时 published version，后续发布不能静默改变既有实例解释。
- `WorkItemTypeReferenceGuard` 是后续字段/实例引用阻止 retire 的扩展点；S04 可接入字段定义引用，但不得伪造实例引用。
- 平台对象只在 S07 有规范 WorkItem 后注册；字段、选项和类型配置都不是独立平台对象。

### 20.4 S04 Milestone 拆分输入

1. M1：字段定义 schema、类型注册表、规范序列化、复合隔离约束与索引决策。
2. M2：选项、默认值、required 与结构化校验规则，覆盖生命周期、并发、审计和幂等。
3. M3：user/date/datetime/attachment/url/work_item_reference 的权限、序列化和失效引用合同；引用只校验目标可访问性，不创建实例值。
4. M4：空间字段配置 UI、capability 查询投影和隔离性能基准；基准使用独立合成数据，不把 legacy issue 当成规范 WorkItem。

### 20.5 禁止提前实现与剩余风险

- S04 不创建 `project_work_items`、字段值表、实例 API 或 `work_item` resolver；这些属于 S07。
- S04 不交付布局、条件显示或字段级授权；这些属于 S05。
- S04 不交付完整 draft/publish/diff/rollback/template 流水线，也不修改 published v1；这些属于 S06。
- S04 不交付状态流、节点流或自动化；这些属于 S08、S09 和 S17。
- 既有空间出现自定义类型占用预置 key 时，当前采用显式冲突报告和人工治理，不自动改名或覆盖。该策略不阻断 S04，但 S04 路线必须保留冲突可观测性，不能把它描述为自动恢复。

## 21. S04-M5 冻结的 S05/S06 准入包

S04 评审结论为 **Go S05，S06 保持依赖准入**。S04 已交付字段定义、稳定选项、结构化规则、复杂类型配置、六类身份生产配置 UI、V001-V065 迁移和配置目录规模证据；没有创建实例、字段值、布局或发布版本。S05 先交付布局与字段访问合同，S06 在 S05 完成后物化完整配置版本。

### 21.1 S05 布局与字段访问输入

- 新建表单与详情视图使用独立布局图，布局节点具有永久 ID、稳定 key、顺序、分组与显示条件；条件只能控制展示，不能授予读取或写入权限。
- 布局字段节点只保存 `fieldId + fieldKey`，保存和读取时重新校验同 workspace/space/type 归属、字段状态和 key 一致性；不得复制字段名称、类型配置、选项或规则作为权威事实。
- 字段访问策略由服务端按空间角色、工作项状态和字段策略计算 `read/write/required/hidden`；客户端不得通过隐藏控件替代授权。布局引用缺失、停用或 retired 字段时必须返回可操作诊断，禁止静默删除或跨类型重绑。
- S05 不创建工作项实例、不修改 published v1、不实现发布切换；其输出仍是待发布配置图，并保留 aggregate version、规范 hash、幂等、审计与并发保护。

### 21.2 S06 发布与不可变版本输入

- draft 以类型定义为聚合根，引用 S04 字段图和 S05 布局图；发布事务必须重新校验全部引用、权限、状态、规则和布局闭包。
- 每次发布创建新的不可变 type version，快照包含字段定义、选项、规则、复杂类型配置、布局和字段访问策略的规范序列化及 hash；既有 published v1 和历史版本永不原地更新。
- 发布成功后原子切换 `current_version_id`，失败不得产生半版本或切换指针；同 request id 重放返回同一结果，并记录审计/outbox。diff、rollback 和模板复用必须基于版本快照，不回写历史版本。
- S06 不迁移实例。S07 创建或迁移 WorkItem 时显式绑定当时的 `type_version_id`；后续发布不能静默改变既有实例解释。

### 21.3 已验收输入与后续性能责任

- V001-V065 空库迁移和 V063 升级回放可重复，S04 升级只增加字段配置表、约束与索引，legacy project/issue sentinel 保持不变，且不存在 `project_work_items`。
- 字段配置目录基线为 120 字段、2400 选项、3 秒预算；隔离、永久 key、规则安全、并发、幂等、审计脱敏和六类身份路径均由自动化证据覆盖。
- S05/S06 必须复用 S04 的复合隔离约束、规范 hash、`availableActions` 和最小披露错误，不得建立第二套字段模型。
- 100,000 工作项动态查询、投影 rebuild、并发过滤和 p95 <= 200ms 由 S07/S13 验收，不得在 S04/S05/S06 报告中冒充已完成能力。

## 22. 平台底座完成后的 S05 恢复合同

S04 已完成并归档，S05 准入合同继续有效。暂停期间 PLATFORM-SCALE-S01-S04 已交付模块边界与 table owner 门禁、公共合同、独立 API/Worker/Event Gateway 运行角色、可靠多 Worker、双 Gateway/双 collaboration 和客户端事实校准；S05-M1 另行交付容量验证环境、确定性种子和真实协议加载器。PROJECT-PLATFORM-S05 因此恢复，但必须继续满足：

- project 访问 identity、file、platform、event、audit 和 IM 时只依赖显式 contract，不新增 foreign infrastructure import 或 foreign write。
- layout 与 field access 表由 project owner 独占；跨 owner read 只能使用已有精确例外，不能整批续期。
- API 角色不混合运行 Worker、旧知识协同定时任务或通用 WebSocket session。
- S05 只保存待发布布局图和访问策略，复用 S04 字段定义、永久 key、规范 hash、aggregate version、幂等、审计和最小披露合同。
- S05 不创建 `project_work_items` 或字段值，不修改 published v1，不切换 current version，不实现 S06 的 draft/publish/diff/rollback/template 流水线。
- PLATFORM-SCALE-S05-M2 至 M5 仍 Deferred；业务路线中的性能测试只验证配置目录和渲染预算，不冒充生产容量、长稳、故障恢复或基础设施 HA。

### 22.1 S05 执行拆分

1. M1：布局与访问策略 schema、永久标识、规范序列化、同域引用、并发/幂等和 API 合同。
2. M2：section/tab/column/field/summary 控件、条件显示、独立 create/detail 图、配置编辑与可复用渲染器。
3. M3：服务端 `read/write/required/hidden` 访问策略、空间角色/上下文条件、最小披露和伪造请求拒绝。
4. M4：管理员预览、用户侧只读形态、诊断、可访问性、响应式、配置规模和真实隔离浏览器闭环。
5. M5：迁移、隔离、安全、并发、规范 hash、边界、全量回归、目标架构同步和 S06 Go/No-Go。

### 22.2 M1 已冻结的布局配置合同

- V077 由 project 模块唯一拥有 `project_work_item_layouts`、`project_work_item_layout_nodes`、`project_work_item_field_access_policies` 和 `project_work_item_layout_commands`；全部写路径携带 workspace/space/type 复合边界。
- create/detail 使用独立聚合和规范 hash。M1 节点类型固定为 `section/tab/column/field/summary`，节点 ID/key 与策略 ID/key 为永久身份；物理删除和身份改写由触发器拒绝。
- 图限制为 120 节点、4 层深度、每父节点最多 4 列和 120 条策略；父子类型、孤儿、循环、重复 key/字段、顺序冲突以及未知条件/策略 schema 均失败关闭。
- 字段节点与策略只保存同域 `fieldId + fieldKey`。保存拒绝缺失、key 不一致、disabled/retired 或跨域引用；读取保留原图并投影诊断，不复制或替代字段权威事实。
- 配置 API 固定为 `GET/PUT /api/project-spaces/{spaceId}/configuration/types/{typeId}/layouts/{layoutKind}`。owner/admin 可读写，member/guest forbidden，non-member/enterprise admin not-found；响应由服务端投影 `availableActions`。
- 保存使用 aggregate version、request ID、规范载荷 hash、命令回执、同事务审计和 outbox。审计/事件只记录 hash、数量、kind 和版本，不记录策略 JSON。
- M1 不提供编辑器、共享渲染器、运行时 `read/write/required/hidden` 决策、发布版本或 WorkItem 实例；分别由 M2、M3、S06、S07 承接。

### 22.3 M2 已冻结的布局编辑与条件合同

- create/detail 继续使用独立聚合；前端缓存、路由和命令均携带 space/type/layoutKind，切换布局不会覆盖另一张图。
- 节点局部操作统一提交到 `POST .../layouts/{layoutKind}/nodes:command`。服务端对候选完整图执行规范化、引用与版本校验后原子持久化；复制含字段子树明确拒绝，避免违反字段在单布局内唯一的规范模型。
- 条件 DSL schema v1 只允许受控 field/context predicate 与 `all/any/not`，限制深度和表达式数量，不执行代码、不访问网络或数据库。字段依赖必须位于同布局，类型化操作符和值、退役引用和依赖循环失败关闭。
- 管理 UI 提供控件、布局树、属性与组合条件三类紧凑面板，支持拖放、键盘移动/删除、并发意图保留和刷新重试；空间 member/guest 不显示配置入口。
- 共享渲染器只消费布局、S04 字段目录和显式访问投影，不复制字段类型事实。hidden/read/write/required 在渲染边界生效，未知字段或控件显示安全诊断；M2 不在客户端计算正式授权。
- M2 仍是待发布配置与预览，不创建工作项或字段值。服务端访问策略求值、脱敏布局投影和 synthetic preview context 由 M3 承接。

### 22.4 M3 已冻结的字段访问与最小披露合同

- 字段访问策略固定为 schema v1：每字段一个 `default` 与最多 64 条规则，效果仅允许 `hidden/read/write + required`。效果按 `hidden > read > write` 收窄，required 只在最终 write 时成立；规则只能引用 owner/admin/member/guest/non_member/enterprise_admin、受控上下文和同类型字段。
- 角色能力上限由服务端固定：owner/admin/member 最高 write，guest 最高 read，non_member/enterprise_admin 为 hidden。disabled 空间/类型最多 read，archived/retired 空间、类型或字段为 hidden；企业管理员不因后台身份自动获得空间内容访问。
- 正式读取使用 `GET /api/project-spaces/{spaceId}/types/{typeId}/layouts/{layoutKind}/projection`；返回已过滤节点、安全字段 DTO、逐字段决策和脱敏诊断，不返回原策略。hidden 字段的名称、key、节点、配置、条件、选项和诊断身份均不进入响应，空容器被裁剪。
- 管理配置使用 `PUT .../configuration/types/{typeId}/layouts/{layoutKind}/policies`，复用布局 aggregate version、规范 hash、request ID 回执、幂等、审计和 outbox。owner/admin 可写，其他身份由服务端拒绝；拒绝审计只记录操作、原因和请求边界，不记录策略正文或字段身份。
- 合成预览使用 `POST .../configuration/types/{typeId}/layouts/{layoutKind}/preview`，仅 owner/admin 可调用。角色、空间/类型/字段状态和字段样本必须属于受控集合；预览不写布局、策略、命令、工作项、审计、outbox、通知或搜索事实。
- 管理 UI 的策略编辑器只编辑完整配置视图，支持默认效果、六身份覆盖、必填约束、条件规则保留和危险收窄确认。共享渲染器只消费服务端投影；缺少决策时按 hidden 失败关闭，不从客户端策略推导或补全权限。
- 120 字段并发求值、规范 hash、策略写幂等、六身份/资源状态矩阵、停用身份、跨边界、伪造角色和最小披露均有定向自动化证据。该预算只代表配置策略求值，不代表生产工作项容量。

### 22.5 M4 已冻结的配置集合读模型与共享渲染合同

- 管理配置集合读模型在一个 repeatable-read 快照内组合类型、字段目录、create/detail 布局和服务端访问投影；配置与投影的 aggregate version/hash 必须一致，否则按可重试冲突失败，不允许拼接混合版本。
- 用户侧样本入口只向有效空间成员返回当前身份的 synthetic projection。它不提供保存或创建动作，不返回管理诊断、策略正文、选项归属和审计元数据；non-member 与仅企业管理员继续按 not-found 最小披露。
- S07 必须复用 `WorkItemLayoutRenderer` 的输入边界：注入已发布布局投影、服务端字段访问决策、实例值和显式 change handler。不得复制字段注册表、条件求值器或权限策略到第二套运行组件。
- 当前 S04 注册的 11 类字段必须有明确编辑/只读行为；附件和工作项引用在 S07 前只能显示规范占位。未知类型失败关闭。interval/computed 只有在后续 Stage 先扩展字段注册表、存储和校验合同时才可进入运行映射。
- 配置规模基线为 120 字段、2400 选项、3 秒集合读取预算；布局图上限仍为 120 节点，因此一个根 section 下最多承载 119 个字段节点。该预算不代表 WorkItem 实例查询、生产容量或长稳承诺。
- 配置 UI 的离线/失败恢复必须保留当前选择；并发重放前需读取最新 version/hash 并由用户确认重新应用，目标节点已删除时不得静默重建。刷新脏策略草稿必须显式确认。

### 22.6 恢复时仍未实现的能力

S05 的表单和详情页是“配置与渲染合同”，不是已经可创建真实 WorkItem 的运行页面。真实实例创建、字段值持久化和 legacy 迁移属于 S07；已发布不可变配置版本和发布切换属于 S06。预览必须明确使用合成上下文或配置样本，不能把合成记录写入业务表或表述为正式实例。

### 22.7 S05-M5 冻结的 S06/S07 准入合同

本节是 S05-M5 对 S06 与 S07 的固定工程输入，不代表以下发布、模板或 WorkItem 运行能力已经实现。S06 必须先完成草稿与不可变版本流水线，S07 才能创建或迁移真实 WorkItem；两者不得借用 S04/S05 的 live 配置表绕过版本边界。

#### 22.7.1 唯一草稿权威与遗留状态收紧

- `ConfigurationDraft` 是 S06 唯一可变草稿权威，以 `WorkItemTypeDefinition` 为聚合根；同一 type definition 同时最多一个 active draft。保存使用 aggregate version 乐观并发，禁止以 `WorkItemTypeVersion(status=draft)` 建立第二条编辑路径。
- `WorkItemTypeVersion` 只允许 `published` 或 `superseded`，创建后 config、schema version、config hash、version number 和归属均不可修改或删除。更正只能创建更高版本。
- S03 既有 `project_work_item_type_versions.status=draft` 是待收紧的物理兼容状态。S06 migration 必须先画像其数量、归属和 payload hash：可无损转换的行迁入 `ConfigurationDraft`，冲突、重复 active draft 或不可解析 payload 必须输出稳定诊断并阻止切换；完成后数据库约束和应用枚举都只接受 published/superseded。
- draft 校验状态固定为 `editing -> validating -> valid|invalid`，任何 active 状态都可在授权下进入 `abandoned`；配置写入会把 valid/invalid 草稿重新置为 editing。M2 发布成功后以原子事务关闭 active draft 并关联 published version，不把 `published` 伪装成草稿状态；发布失败继续保留原 active draft 和 aggregate version，不产生半版本。

#### 22.7.2 发布快照、规范序列化与 hash

- published snapshot 必须自包含当时的类型展示语义、S04 字段定义、选项、默认值、校验规则、复杂类型配置，以及 S05 create/detail 布局、节点、条件 DSL 和字段访问策略。快照不得只保存 live 表 ID 后在运行时回查当前配置。
- 快照携带显式 `snapshotSchemaVersion`。规范序列化统一 UTF-8、JSON 属性顺序、稳定业务 key、节点父子顺序、选项顺序、规则优先级、空值表达和数值表达；数据库行顺序、创建时间、临时 ID 和 UI 折叠状态不得影响结果。
- canonical order 至少按配置域、稳定 key、显式 sort order 和永久 ID 依次打破平局；同一语义的重排必须生成相同 canonical payload。`configHash` 由完整 canonical payload 计算 SHA-256，并与 snapshot schema version 一起持久化和返回。
- 发布前必须在同一一致性视图内重新校验 workspace/space/type 归属、永久 key、字段状态、布局闭包、条件引用、策略引用、数量/深度预算和 unknown schema fail-closed。保存 draft 时的历史校验结果不能代替发布时校验。

#### 22.7.3 原子发布事务

- 发布先取得 type definition 级互斥锁，并在锁内再次读取 active draft、`current_version_id`、aggregate version、request id 和 payload hash。version number 在锁内单调分配，禁止客户端指定或用无锁 `max + 1`。
- 单一数据库事务必须原子完成：创建不可变 version、把上一 current version 标记为 superseded、切换 `current_version_id`、终结 draft、递增聚合版本、写审计、写事务 outbox 和写幂等 receipt。任一步失败必须整体回滚。
- audit/outbox 记录 type/version/draft 身份、schema version、config hash、actor、request id 和差异摘要，不复制敏感字段策略正文。outbox 事件至少包含稳定 event id、`configuration.version_published`、schema version 和新旧 version/hash。
- receipt 以 workspace + type definition + command + request id 为作用域，并绑定请求 payload hash。相同 key 与相同 payload 重放必须返回首次提交保存的原始 version/result，不读取并伪装成最新 aggregate；相同 key 与不同 payload 返回稳定冲突。

#### 22.7.4 Diff 与 rollback-as-new-version

- diff 只比较两个不可变 canonical snapshot，按 `additive`、`behavioral`、`conditional`、`breaking` 分类，并给出字段、布局、策略和引用的稳定 key 路径。顺序变化与语义变化分开报告，未知 schema version 不猜测差异。
- 字段删除/类型变化、必填收紧、选项移除、访问收窄、布局必需入口移除和悬空引用至少属于 breaking；发布 API 必须返回分类计数、受影响 key 和确认要求，不能只返回一段文本。
- rollback 不修改历史 version，也不把 version number 或 current pointer 直接倒退。系统从目标历史 snapshot 复制出新的 `ConfigurationDraft`，按当前 schema 重新校验，再发布为更高 version；审计和 lineage 记录 rollback source version。
- S06 的 diff 是配置影响说明，不冒充 S07 的实例迁移预览。真实 WorkItem 值映射、默认值回填、拒绝清单和批次回滚仍由 S07 的显式 migration plan 承担。

#### 22.7.5 模板复制、lineage 与升级

- 模板安装采用 copy-with-lineage：从不可变 template version snapshot 创建空间本地 `ConfigurationDraft`，记录 template id/version/hash、安装目标、安装者和时间；安装后本地草稿与后续 published version 都不是 live link。
- 模板升级必须以“上次安装的 base snapshot、模板新的 upstream snapshot、空间当前 local snapshot”执行三方差异。自动合并只允许无冲突 additive 变化；本地覆盖、删除、重命名和权限收窄必须保留或要求管理员逐项决策。
- lineage 记录来源和升级历史，不授予模板提供者跨空间读取或修改权限。模板被撤回、retired 或不可见时，已复制的本地 published snapshot 仍可解释，但不能静默获取后续版本。
- detach 是显式、可审计且幂等的本地操作：停止后续升级提示并冻结最后 lineage 摘要，不删除本地草稿、published versions 或历史来源。detach 后重新关联必须按新的安装命令处理，不能复用旧 receipt 绕过冲突。

#### 22.7.6 API、DTO、错误、授权与幂等边界

| Surface | Canonical shape | DTO / required result |
| --- | --- | --- |
| 草稿 | `GET/PUT .../configuration/types/{typeId}/draft`，`POST .../draft:validate|abandon` | `ConfigurationDraftDetail/SaveRequest/ValidationResult`；返回 draft identity、aggregate version、canonical hash、诊断和服务端 `availableActions` |
| 发布与版本 | `POST .../draft:publish`，`GET .../versions`，`GET .../versions/{versionId}` | `ConfigurationPublishRequest/Result`、`WorkItemTypeVersionSummary/Detail`；publish request 必须携带 request id、expected draft version 和确认的 diff hash |
| Diff 与回滚 | `GET .../versions/{from}:diff?to={to}`，`POST .../versions/{versionId}:prepare-rollback` | `ConfigurationDiffResult`、`ConfigurationRollbackDraftResult`；回滚只创建/复用受控 draft，不直接切指针 |
| 模板 | `POST .../templates/{templateVersionId}:install`，`POST .../draft:merge-template|detach-template` | `TemplateInstallResult/TemplateMergePreview/TemplateDetachResult`；全部返回 lineage、冲突和 receipt |

- 写权限只授予目标空间 owner/admin；member/guest 无配置写权限，non-member 与仅 enterprise admin 使用 `not_found_or_hidden`，企业治理身份不自动获得空间内容访问。读取 draft、完整 snapshot、diff 和模板冲突同样受空间配置权限约束。
- 错误合同至少区分 `not_found_or_hidden`、`active_draft_conflict`、`draft_version_conflict`、`invalid_configuration_graph`、`unsupported_snapshot_schema`、`publication_conflict`、`idempotency_key_reused`、`source_version_not_found`、`template_lineage_conflict` 和 `breaking_change_confirmation_required`。
- DTO 不暴露数据库约束名、内部表名、策略敏感正文或隐藏字段身份。服务端 reason code、`availableActions` 和最小披露是唯一授权解释来源，前端不得根据状态文本补算发布、回滚或模板动作。
- 所有写命令都使用持久化 receipt、payload hash、乐观版本和审计/outbox；重试、超时恢复和并发发布必须有正反自动化证据。进程内缓存、只返回“当前最新状态”或按 UI 防重复都不构成幂等。

#### 22.7.7 S07 只消费已发布快照

- S07 创建 WorkItem 时在事务内解析 type definition 的 `current_version_id`，并把明确的 `type_version_id` 和 `config_hash` 绑定到实例；迁移 legacy 数据时也必须由 migration unit 明确指定目标 published version。
- S07 的字段校验、默认值、布局渲染、条件求值和字段访问决策只消费该实例绑定的 published snapshot 或由其构建的可重建投影。运行路径不得直接读取 S04 字段 live 表、S05 layout/policy live 表或 active draft。
- S04/S05 后续编辑和 S06 新版本发布不得改变既有实例语义。旧实例升级只能经显式 diff、mapping/default、授权、幂等和失败清单驱动；失败实例继续绑定原 version。
- S07 可以复用 S05 的 renderer、条件求值和字段注册实现，但输入必须来自 published snapshot adapter。不得让共享代码退化为对 live 配置 repository 的隐式依赖。

#### 22.7.8 未实现边界与准入判定

- 截至 S05-M5，已实现的是字段、布局、访问策略、规范 hash、配置预览和共享渲染合同；`ConfigurationDraft` 持久化、legacy draft 迁移、发布锁、不可变完整 snapshot、版本 diff/rollback、模板 lineage API 和原子 publication receipt 仍属于 S06。
- `project_work_items`、字段值、实例命令、实例版本升级、legacy project/issue 数据迁移、运行查询容量和生产 cutover 仍属于 S07 及后续 Stage。S06 不得创建合成实例来提前宣称这些能力。
- S06 Go 的最低条件是：唯一 active draft 约束和遗留状态迁移可回放；发布事务故障注入证明无半版本；相同 request id 返回首次 receipt；published version update/delete 被数据库拒绝；canonical hash、diff、rollback-as-new-version、模板三方差异和六类身份矩阵均有自动化证据。
- S07 Go 的最低条件是：所有运行读取均可证明来自绑定的 published snapshot，静态依赖与负向测试阻止直接读取 live S04/S05 配置；在此之前不得激活规范 WorkItem 写入或 legacy cutover。

### 22.8 S06-M2 已实现发布事实

- V082 为完整发布版本记录 `snapshot_schema_version`、`source_draft_id` 和可选 `rollback_source_version_id`，并以数据库触发器拒绝 published/superseded update/delete。
- publication service 先取得类型行锁，再在锁内创建/读取幂等回执、重读 active draft、分配版本号并执行版本插入、旧 current supersede、pointer 切换、草稿关闭、审计、outbox 和回执完成；任一失败全部回滚。
- 版本列表允许展示 legacy partial 历史，但 diff、rollback 和未来 S07 adapter 只接受完整 schema v1 snapshot。rollback 只创建带 lineage 的新草稿，再走普通发布产生更高版本。
- 当前 diff 输出稳定 key path、前后值、影响等级和摘要；它只解释配置变化，不预测或迁移尚不存在的 WorkItem 实例。

### 22.9 S06-M3 已实现模板事实

- `project_work_item_configuration_templates` 区分 platform 与 workspace 来源；模板版本保存完整规范 snapshot/schema/hash 并受数据库不可变触发器保护，撤回模板只关闭后续安装，不删除历史版本或 installation lineage。
- `WorkItemTypePresetCatalog` 只作为平台模板的确定性导入源。导入按稳定 template key、版本号和 snapshot hash 幂等落库，模板目录、版本、安装和升级读取均以数据库为运行时权威。
- workspace 模板只能从调用空间中完整 published configuration version 创建；legacy partial、跨空间来源、隐藏版本和冲突 key 均被拒绝。模板版本不查询 live 类型、字段、布局或策略表补齐内容。
- 安装在目标类型 active draft 中复制并重绑定 snapshot；类型、字段、选项、布局节点和访问策略身份由目标 UUID 加稳定 semantic key 生成，不携带来源空间 UUID，也不建立上游 live 引用。
- installation 保存 base template version/hash、当前 upstream、local draft hash、状态和 aggregate version。升级预览只做 base/upstream/local 三方比较，不写 draft；无冲突 additive 变化可自动合并，冲突必须逐项选择 local/upstream 后才能原子 apply。
- install、apply-upgrade 和 detach 使用持久化 command receipt、payload hash、乐观版本、审计和 outbox；相同 request ID 精确重放首次响应，异载荷重放和并发败者返回受控冲突。
- detach 保留当前 draft、hash 和最后 lineage 摘要，仅把 installation 标记为 detached；后续不再宣称与上游同步。重新安装是新的显式命令。
- 这些模板能力仍属于配置编排。S07 运行实例必须只消费 M4 冻结的 `PublishedSnapshotAdapter`；任何 runtime 对 S04/S05 live repository 或 active draft 的依赖都属于架构违规。

### 22.10 S06-M4 已冻结的兼容与 runtime adapter 合同

- 配置兼容矩阵以稳定 semantic key 比较字段、选项、规则、布局、访问策略和模板变化，影响等级固定为 `compatible`、`review_required`、`migration_required`、`blocked`。完整矩阵见 `docs/01-architecture/project-work-item-configuration-compatibility-matrix.md`。
- `GET .../versions:compatibility` 和 `GET .../draft:compatibility` 返回 from/to hash、总体影响、稳定 key path、原因码、建议、分类计数和实例迁移标记。该结果不创建值映射、回填或迁移批次。
- `PublishedSnapshotReader` 是 S07 唯一允许注入的冻结版本读取端口；`PublishedSnapshotAdapter` 只接受精确 schema v1、published/superseded、完整且 canonical hash 一致的 snapshot。schema 0、未来 schema、边界不匹配和完整性失败均失败关闭。
- runtime package 由 ArchUnit 禁止依赖 draft、字段、选项、布局、策略的 live repository/command repository，以及 publication/template/type/field/layout 配置服务。模板目录、preset catalog 和 installation lineage 也不是实例运行事实来源。
- V001/V061/V065/V078 到 V085、重复 migrate、legacy draft 诊断、published sentinel/hash/current pointer 保留及真实 `pg_dump/pg_restore` 恢复演练已通过；V085 补齐配置草稿、回执、字段和布局的空间清理闭包。
- 120 字段、2400 选项的 snapshot/hash/兼容分析/三方 merge 在 3 秒配置预算内，repository 调用保持批量上界。该预算不扩张为 S07 实例容量、生产长稳或基础设施 HA。
- owner/space-admin 可读取配置兼容结果；member/guest 为 403，non-member/enterprise-admin 为统一 404，跨空间组合 ID 不披露存在性。真实浏览器统一覆盖发布、兼容提示、diff、回滚、模板安装/解绑、键盘、离线、1440/820 和六身份边界。
- S06 已满足 S07 准入：S07 创建或迁移实例时必须显式绑定 `type_version_id + config_hash`，只经 adapter 解释 snapshot；任何实例升级仍需独立 migration plan、失败清单、幂等和回滚。

## 23. S06-M4 冻结并在 revision 21 激活的 S07 准入包

S07 的交付边界是“统一规范 WorkItem 运行时与 legacy 第一阶段迁移”。它建立实例权威、动态值、参与者、活动、兼容解析和迁移能力，但不提前实现 S08 状态流、S09 节点流、S10 关系图或 S13 高级保存视图。以下合同是 S07 拆 Task 和实现评审的固定输入。

### 23.1 规范实例与配置绑定

- `project_work_items` 是所有类型实例的唯一规范模型。最小身份为 `workspace_id + space_id + work_item_id`，并显式保存 `type_definition_id`、`type_version_id`、`config_hash`、展示编号、规范字段值、乐观版本、归档状态和审计时间。
- 创建实例时在同一事务中解析并锁定 type 的 current published version，经 `PublishedSnapshotAdapter` 校验完整 schema/hash 后写入明确绑定。新版本发布不改变既有实例解释。
- 实例运行包只允许依赖 `PublishedSnapshotReader`、公共空间授权和平台公共合同。对 active draft、S04/S05 live repository、publication/template command service 的依赖由 ArchUnit 和负向测试阻断。
- 创建、更新、归档/恢复等命令使用持久化 receipt、规范 payload hash 和 expected version；实例、字段值、查询投影、活动、审计和 outbox 在同一事务提交。

### 23.2 动态值、投影、参与者和活动

- `project_work_items.field_values` JSONB 保存规范动态值。只有绑定 snapshot 明确声明 query/sort/group capability 的字段，才同步维护 `project_work_item_field_projections` 类型化投影；投影可重建，不成为第二事实源。
- 字段 codec、默认值、required/write/read 决策、条件和布局均由实例绑定 snapshot 派生。hidden 字段不得进入 DTO、错误、活动差异、审计正文、搜索、通知或 outbox。
- 参与者以实例内稳定角色和用户身份保存，处理用户停用、重复添加、并发变更和最后责任人约束。参与者变更必须产生不可变活动和审计事实。
- 活动账本使用实例内单调序号，保存稳定 action、actor、object/version 和脱敏摘要；面向调用者的活动 DTO 再按当前访问决策投影，不直接回放敏感原值。

### 23.3 Legacy 显式映射、读取阶段和禁止双写

- `project_legacy_id_map` 以 `(workspace_id, source_type, source_id)` 唯一，保存 target type/id、space、batch、source/target checksum、状态和冲突原因。即使 UUID 可复用也必须写 map；冲突生成新 UUID，禁止覆盖目标。
- S02 `project_legacy_space_maps` 是 legacy project 的空间归属权威输入；S07 不重新推断空间或成员。旧 project/issue route、平台对象、搜索、IM、通知、审计和文件引用统一经公共 `work_item` resolver。
- 读取阶段固定为 `legacy -> shadow migrate -> canonical write -> canonical default -> old write closed`。阶段由 workspace/space cutover flag 控制，具有命中/漂移/延迟观测和 kill switch。
- 任一阶段只有一个写权威。切流前写 legacy，切流后只写 canonical；不允许同步双写。旧写关闭后返回稳定 gone/conflict 和 canonical location，回退不得覆盖 canonical 新事实。

### 23.4 迁移批次、校验与回滚

- S07 独立落地 migration batch、unit、immutable manifest、attempt、append-only failure、ID map 和 cutover 状态。manifest 保存批次生命周期归属，后续 resume 或重迁不能从“最近尝试”反推或覆盖历史。
- preflight、plan 和 input fingerprint 来自同一 `REPEATABLE_READ` 快照。dry-run 只读；执行前再次检查水位/fingerprint，过期计划受控拒绝。
- 迁移以 legacy project 为事务单元，覆盖 project WorkItem、issue WorkItem、动态值、参与者、评论、附件、活动 provenance 和显式 map。失败单元回滚且保留追加失败清单，成功单元可独立续跑。
- batch verify 只对原 immutable manifest 校验 count/hash/map/字段/附件/孤儿；workspace convergence verify 是独立只读操作，不修改任何历史批次结论。
- canonical write 前允许按 manifest 删除本批次目标并保留审计；canonical write 后回退只能关闭入口、启用 resolver fallback 或执行显式补偿，不能删除或覆盖 canonical 新写。

### 23.5 API、授权、平台接入和用户竖切

- 用户协作 API 负责 WorkItem 列表、创建、详情、更新、参与者、评论、附件和活动；企业治理 API 仅提供迁移控制与审计，不因 enterprise 权限自动获得空间内容访问。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份均由服务端返回最小 DTO 和 `availableActions`。跨 workspace/space/type 组合 ID、hidden 字段和迁移对象身份不得通过 403/404、计数、错误或事件枚举。
- 平台对象统一注册为 `work_item`，canonical identity 与 legacy aliases 由 resolver 管理。跨模块消费者只能依赖公共对象/事件合同，不得读取 `projects/issues` 或项目平台私有表。
- 用户侧第一条竖切必须复用 S05 `WorkItemLayoutRenderer`，输入为绑定 snapshot 投影、服务端字段访问决策、实例值和显式 change handler；完成真实创建、编辑、参与、评论、附件、归档/恢复和刷新一致性。

### 23.6 验证边界与 S08 准入

- M1-M4 分别形成规范实例、动态值、兼容切流和迁移恢复的 fresh 证据；M5 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 route-final。
- 实例查询需记录代表性规模、SQL plan、索引和延迟，但不得把配置规模或局部查询结果表述为 10 万复杂保存视图、8 小时长稳、基础设施 HA 或生产容量结论。
- 生产写迁移前仍需目标环境真实数据画像、备份恢复、观测告警、操作批准和 kill switch 演练；本地/隔离 rehearsal 不自动授权生产 cutover。
- S08 只能在 S07 的规范 identity、snapshot binding、活动和 command contract 上挂接状态流，不得重建第二套 WorkItem、字段值或 legacy migration 权威。

### 23.7 S07-M4 已实现迁移恢复边界

- V089 扩展批次 plan payload/fingerprint、lease/fencing/heartbeat、单元迁移计数，新增规范评论、附件、迁移 provenance 和独立 verification；manifest、failure、provenance、verification 均由数据库触发器保护为追加历史。
- `WorkItemMigrationService` 在 `REPEATABLE_READ` 中冻结非空 project 单元 manifest，并在每个 `REQUIRES_NEW` 单元写入规范 project/issue、绑定 published snapshot、显式 map、参与者、评论、附件、活动、provenance 和平台链接。执行前重新加载 manifest 比对 fingerprint，源漂移稳定失败且不产生半单元。
- 批次 lease 使用 owner/token/递增 fence/heartbeat；claim 使用 `FOR UPDATE SKIP LOCKED`，限速、暂停、失败隔离和同批次续跑不允许两个 worker 同时拥有批次。失败尝试追加清单，不覆盖前次失败。
- batch verify 与 workspace convergence verify 分别追加不可变结论。旧批次在回滚或后续批次后不会因当前 workspace 收敛而变成成功；pre-cutover rollback 保留历史并清理本批次目标，post-cutover 进入 kill switch 和显式补偿。
- 管理 API 与 `project:migrate-work-items` CLI 只提供治理操作，不提供空间内容浏览。V001/V061/V065/V078/V085 到 V089、重复 migrate、备份恢复、竞态、源漂移、UUID 冲突、兄弟单元隔离、回滚和历史不可变均有隔离 PostgreSQL 证据。
- 该实现授权 M5 在隔离环境执行完整 rehearsal，不授权直接生产 cutover。生产运行仍要求目标环境画像、备份恢复、容量窗口、观测告警、变更审批和 RTO 复核。

### 23.8 S07-M5 已实现用户竖切与 Stage 边界

- 用户侧 `/project-spaces/{spaceId}/work-items` 已形成类型入口、列表、创建、详情、动态值编辑、参与者、活动、评论、附件和归档/恢复闭环；渲染输入只来自实例绑定的不可变 published snapshot 与服务端访问投影。
- `GET /issues/{legacyIssueId}` 通过显式 map 和公共 resolver 处理未迁移、已迁移及无权对象；已迁移对象只重定向到 canonical WorkItem，兼容入口不恢复 legacy 写或建立双写。
- 迁移计划支持可选 `projectIds` 范围，供 canary、验收和受控批次冻结明确 project 单元；省略时仍按 workspace 全量规划，未知 project 以稳定失败项阻断，不被静默忽略。
- V090 允许系统预置类型在正常校验/发布事务中推进 `current_version_id`，同时继续禁止修改 built-in identity、名称、图标、描述或退休状态。
- 隔离 route-final 已覆盖六身份、PostgreSQL/Redis/MinIO、离线、409 冲突、窄屏、旧链接、迁移/校验和 pre-cutover rollback。该证据关闭 S07 实现范围，不等价于生产切流批准或容量承诺。
- S08 只能复用 canonical WorkItem identity、绑定版本、命令回执、活动序列和可用动作挂接轻量状态流；不得创建第二套工作项、字段值、参与者、活动或迁移权威。

## 24. S07-M5 冻结并在 revision 23 激活的 S08 准入包

S08 的交付边界是“轻量状态流定义与运行时”。它允许任务、缺陷、内容等 WorkItem 在单一 current state 上执行版本化动作，但不实现 S09 节点 token、串并行、分支汇聚、会签、交付物或节点任务，也不提前实现 S14 看板拖拽和 S17 自动化编排。

### 24.1 定义与发布权威

- StateDefinition、ActionDefinition、TransitionDefinition 和 GuardDefinition 使用永久 semantic key；展示名、颜色和说明可变，但历史、事件和兼容映射不得依赖展示文本。
- 状态分类至少区分 initial、active、terminal 和 canceled。每份有效定义恰有一个 initial；无意外死路、悬空转换、重复 key 或不可达活动状态。
- 轻量状态流是完整 configuration snapshot 的一部分，沿用 S06 唯一 active draft、canonical hash、校验、diff、compatibility、publish、rollback 和模板 lineage。禁止建立 live definition + snapshot 双权威。
- published snapshot 变化按 semantic key 比较。删除当前状态、改变 initial/terminal、收紧 guard 或 required field 至少为 migration_required；无映射的运行中状态删除必须 blocked。

### 24.2 声明式守卫、授权和副作用

- Guard 只允许注册表中的声明式 operator/operand，输入来自绑定 snapshot、当前 WorkItem 可见字段、参与者、空间角色和稳定上下文。未知 operator、类型不匹配、隐藏字段依赖和未来 schema 失败关闭。
- Action 定义授权角色、来源/目标状态、required fields、可选原子 field patch 和受控 side-effect key。任意代码、动态 SQL、前端 guard 或未经注册的网络调用不属于基础合同。
- `availableActions` 与 execute 必须调用同一服务端 decision/guard；投影包含稳定 action key、label、reason code 和输入要求，但不返回策略正文、隐藏值或不可见目标。
- 企业治理角色不自动获得空间配置或内容流转权。owner/space-admin/member/guest/non-member/enterprise-admin 的最小披露继续服从 S02/S05/S07 空间与字段边界。

### 24.3 运行时权威与事务

- 轻量状态运行时只保存 `(workspace_id, space_id, work_item_id, type_version_id, config_hash, current_state_key, aggregate_version)` 单一权威；状态定义只从 WorkItem 绑定 snapshot 读取，不查询类型 latest version、active draft 或 live repository。
- 动作命令使用 WorkItem expected version、from-state 前置条件、持久化 request receipt 和 canonical request hash。current state、WorkItem aggregate version、可选字段 patch、workflow history、activity、audit、outbox 和 receipt 在同一事务提交。
- workflow history 追加保存单调序号、from/to/action、actor、绑定版本、decision reference、correlation/causation 和脱敏摘要。数据库和 Repository 均禁止更新/删除历史事实。
- `workflow.action_executed`、`workflow.state_changed` 采用公共事件 envelope；消费者按 eventId 幂等，未知 schema 进入 dead letter，不读取状态流私表补算事实。

### 24.4 回退、重开、终止与恢复

- forward、return、reopen、terminate、restore 和 correction 都是显式 Action；不存在绕过 guard/history 直接更新 current state 的普通接口。
- return 只能到版本定义允许的目标；reopen 的目标状态显式定义；terminate/canceled 与业务 terminal 语义分离。重复命令精确重放，不追加伪历史。
- WorkItem archive/restore 是对象生命周期，不等于业务终止/恢复。归档时状态事实保留；恢复后不自动猜测或改写业务状态。
- 空间 owner/admin 可使用受控 recovery/correction 命令，必须提供原因、expected version、危险确认、审计和事件；enterprise-admin 仍需显式空间内容授权。

### 24.5 存量实例和版本升级

- S07 及更早创建且绑定无状态流 snapshot 的 WorkItem 不被静默初始化。状态初始化/backfill 使用显式 manifest、目标版本、initial key、失败清单、幂等、verify 和回退边界。
- 实例升级到含新状态流的 configuration version 必须提供 state key mapping；删除、合并、重命名或终态变化无完整映射时失败关闭。旧实例继续按原绑定运行或明确报告 capability 缺失。
- backfill 与 upgrade 不改写既有 activity/history，不伪造用户动作；系统初始化事实带稳定 provenance、actor class、版本和 correlation。
- 空库、V001/V061/V078/V085/V090 历史基线、非空实例、重复 migrate、失败续跑、并发和恢复必须在隔离 PostgreSQL 中形成 fresh 证据。

### 24.6 状态流与节点流隔离

- S08 current-state repository 与 S09 workflow-instance/node-token repository 分离。状态流实例不创建 token，节点流实例不创建 current-state row。
- 两类运行时只共享 WorkflowCommand、authorization/guard SPI、aggregate version、idempotency、history/outbox envelope 和 available-action projection，不互相查询私有运行表。
- 轻量状态流不得为“未来扩展”预先保存伪 token/graph；S09 也不得把并行 token 汇总反写为真实 current state。跨类型统一展示只能使用派生 summary。
- ArchUnit、schema 和不变量测试必须阻断私表串线，S08 route-final 必须把该负向合同作为 S09 准入证据。

### 24.7 UI 与最终验收

- 状态配置器属于项目空间设置，不进入企业管理后台。它支持状态、动作、转换、授权、guard、required field、diagnostics、预览、diff、发布阻断和 rollback。
- 用户执行 UI 在 WorkItem 详情展示 current state、可见历史和服务端 `availableActions`；409、422、超时和离线保留用户输入，刷新后 state/history/activity 一致。
- M4 执行六身份真实隔离浏览器、键盘/焦点/窄屏、并发动作、终态重开、恢复、backfill、完整 Flyway、后端、前端、协作、架构、安全和 route-final。
- S08 完成只授权进入 S09 节点流设计与运行时，不构成 S14 看板、S17 自动化或生产容量承诺。
