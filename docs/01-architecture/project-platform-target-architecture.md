---
title: 项目协作平台目标架构
status: target
program: PROJECT-PLATFORM
program_revision: 13
domain_contract_version: 1
domain_contract_status: frozen-s01-m3
migration_contract_version: 1
stage_review_status: s04-completed
updated_at: 2026-07-24
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
| 工作项类型版本 | `WorkItemTypeVersion` | `typeVersionId` + 单调 `versionNumber` + `configHash` | `WorkItemTypeDefinition` | `draft -> published -> superseded`；published/superseded 均不可变 |
| 工作项 | `WorkItem` | `workItemId`，workspace 内稳定 UUID；展示编号由独立原子序列生成 | `ProjectSpace`，并绑定一个类型版本 | `active <-> archived`；业务流程状态与对象生命周期分离 |
| 配置草稿 | `ConfigurationDraft` | `draftId`，同一 type definition 同时最多一个活动草稿 | `WorkItemTypeDefinition` | `editing -> validating -> published/abandoned` |
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
| `project_work_item_type_versions` | id UUID PK；workspace_id；space_id；type_definition_id；version_number；config_hash；status；config JSONB；created_by/at；published_by/at；`unique(type_definition_id, version_number)`；published/superseded 行禁止 update/delete | version 归属类型；status 仅 draft/published/superseded；发布在单事务内完成校验与 config_hash；历史版本不可变 |

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
- 生命周期：type `active <-> disabled -> retired`，retired 不可新建实例（S03 无实例）但定义与历史版本可读；version `draft -> published -> superseded`，published/superseded 不可变。
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
