---
title: PROJECT-PLATFORM-S01-M2 Execution Report
status: archived
milestone: PROJECT-PLATFORM-S01-M2
updated_at: 2026-07-18
---

# PROJECT-PLATFORM-S01-M2 Execution Report

## Scope

- PROJECT-PLATFORM-S01-M2-T01 到 PROJECT-PLATFORM-S01-M2-T10。
- 本里程碑冻结目标领域合同 v1，不修改当前业务 API、schema、页面或运行时。
- 输入为 M1 当前事实审计、PROJECT-PLATFORM Program revision 1 和目标架构 revision 1。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M2-T01 | static | not-required | not-required | No | 术语、标识、归属和生命周期合同评审；无运行时或页面变化。 |
| PROJECT-PLATFORM-S01-M2-T02 | static | not-required | not-required | No | 研发、市场、HR、交付模型映射静态评审；无浏览器流程。 |
| PROJECT-PLATFORM-S01-M2-T03 | static | not-required | not-required | No | 配置草稿、发布版本和实例绑定合同静态评审。 |
| PROJECT-PLATFORM-S01-M2-T04 | static | not-required | not-required | No | 字段、表单、详情布局和服务端约束合同静态评审。 |
| PROJECT-PLATFORM-S01-M2-T05 | static | not-required | not-required | No | 状态流/节点流共同协议和差异静态评审。 |
| PROJECT-PLATFORM-S01-M2-T06 | static | not-required | not-required | No | 关系方向、基数、图完整性和跨空间合同静态评审。 |
| PROJECT-PLATFORM-S01-M2-T07 | static | not-required | not-required | No | 分层授权、解释链、缓存失效和留痕责任静态评审。 |
| PROJECT-PLATFORM-S01-M2-T08 | static | not-required | not-required | No | 用户执行、空间配置、企业治理 UI/API/DTO 边界静态评审。 |
| PROJECT-PLATFORM-S01-M2-T09 | static | not-required | not-required | No | 事件 envelope、平台对象和跨模块接入合同静态评审。 |
| PROJECT-PLATFORM-S01-M2-T10 | static | not-required | not-required | No | ADR、替代方案、延后决策和禁止模式静态评审。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PROJECT-PLATFORM-S01-M2-T01 | Done | 冻结 6 个规范对象、稳定 ID/业务键、归属和对象/配置生命周期。 |
| PROJECT-PLATFORM-S01-M2-T02 | Done | 冻结 project 为受保护模板类型；四团队示例共享同一 WorkItem 运行时。 |
| PROJECT-PLATFORM-S01-M2-T03 | Done | 冻结草稿、原子发布、不可变版本、实例绑定、显式升级/回滚和模板 lineage。 |
| PROJECT-PLATFORM-S01-M2-T04 | Done | 冻结 13 类字段、系统字段、服务端默认/校验/授权及 create/detail 布局合同。 |
| PROJECT-PLATFORM-S01-M2-T05 | Done | 冻结共同 WorkflowCommand/event SPI 和 state/node 两套运行时差异。 |
| PROJECT-PLATFORM-S01-M2-T06 | Done | 冻结 4 类关系、方向/基数、DAG/重复边、跨空间授权和非隐式同步规则。 |
| PROJECT-PLATFORM-S01-M2-T07 | Done | 冻结 6 层 decision 顺序、默认空间角色、解释字段、缓存 key/失效和写留痕。 |
| PROJECT-PLATFORM-S01-M2-T08 | Done | 冻结用户执行、空间配置、企业治理三类 Surface 及 API/DTO 隔离。 |
| PROJECT-PLATFORM-S01-M2-T09 | Done | 冻结 `work_item` 平台对象、事件 envelope/目录及七类平台接入边界。 |
| PROJECT-PLATFORM-S01-M2-T10 | Done | 形成 ADR-PP-001 至 ADR-PP-010、拒绝方案和 14 类禁止模式。 |

## Frozen Contract Index

规范合同位于 `docs/01-architecture/project-platform-target-architecture.md` 的“13. S01-M2 冻结领域合同”，版本为 `domain_contract_version: 1`，状态为 `frozen-s01-m2`。

| Contract area | Frozen conclusion | Target architecture section |
| --- | --- | --- |
| Terms | Space、type definition/version、work item、draft、template 各有稳定 ID、owner 和生命周期 | 13.1 |
| Product boundary | project 只是工作项类型；space 才是成员、配置和数据边界 | 13.2 |
| Configuration | published version 不可变；实例显式绑定版本；升级/回滚均产生记录 | 13.3 |
| Field/layout | 字段定义为事实来源；create/detail 布局只引用字段；服务端重新校验 | 13.4 |
| Workflow | state/node 共享命令、授权、事件和历史，不共享权威运行结构 | 13.5 |
| Relation | 层级、依赖和跨空间引用由 versioned relation graph 表达 | 13.6 |
| Authorization | enterprise/space/item/node/field/data scope 分层并由统一 decision 解释 | 13.7 |
| UI/API | 用户执行、空间配置、企业治理使用不同入口和 DTO | 13.8 |
| Platform | canonical objectType=`work_item`，outbox envelope 和 facade/event 边界稳定 | 13.9 |
| ADR | 10 项决策、替代方案、延后项和禁止模式 | 13.10 |

## Terminology And Ownership

| Aggregate/entity | Identity rule | Parent boundary | State rule |
| --- | --- | --- | --- |
| `ProjectSpace` | `spaceId` 稳定；`spaceKey` workspace 内不可复用 | Workspace | active/disabled/archived 可显式恢复；非空不硬删 |
| `WorkItemTypeDefinition` | `typeDefinitionId` + space-local 永久 `typeKey` | ProjectSpace | active/disabled/retired，历史实例不受停用影响 |
| `WorkItemTypeVersion` | `typeVersionId` + monotonic version + config hash | TypeDefinition | draft 发布后为 immutable published/superseded |
| `WorkItem` | `workItemId` 作为唯一引用；展示编号不是 ID | ProjectSpace + exact typeVersion | active/archived 与 workflow state 分离 |
| `ConfigurationDraft` | 同类型同时最多一个活动 draft | TypeDefinition | editing/validating/published/abandoned |
| `WorkItemTemplate` | template/version ID，安装记录 lineage | Workspace/platform catalog | draft/published/retired；安装后本地拥有配置 |

业务键 `typeKey/fieldKey/actionKey/roleKey/relationKey` 在发布后不可复用。展示名、图标和说明可演进，但不能改变历史引用含义。

## Cross-Team Model Proof

| Scenario | Types | Proof of common model |
| --- | --- | --- |
| R&D | project, requirement, task, bug, iteration, release | 状态流、评审节点流、parent_child/depends_on 关系和研发模板组合 |
| Marketing | campaign, content, asset, channel_delivery, review | 同一字段、关系、流程和素材 file relation 组合，无市场专用 runtime |
| HR | hiring_plan, position, candidate, interview, onboarding | candidate 以节点流推进，人员/日期字段和关系表达招聘，无 HR 顶层模块 |
| Delivery | project, deliverable, risk, change, acceptance | 交付物、风险和验收用同一 work item/flow/relation runtime |

任何 type 均可直接属于 space，不要求存在 project 父项；固定 project-requirement-task 层级被明确拒绝。

## Configuration And Runtime Contract

- 发布原子生成完整 immutable type version；失败不影响 active version。
- WorkItem 创建命令必须携带或由服务端解析后固化 `typeVersionId`，之后不以 latest config 解释旧实例。
- additive/conditional/breaking diff 在升级前分类；breaking 变更必须提供映射和拒绝项。
- rollback 通过从旧版本复制新草稿再发布更高版本实现，不回写旧版本。
- template install 使用 copy-with-lineage；template upgrade 形成三方 diff 草稿，不实时覆盖本地配置。
- 配置发布、实例升级和模板升级均需要 idempotency key、操作者、before/after version、结果和审计关联。

## Field, Layout And Workflow Contract

字段第一阶段覆盖 text、rich_text、number、boolean、single/multi select、user、date_time、date_range、URL、attachment、work_item_reference 和 computed。默认值、required/validation/readOnly、computed 和 field decision 由服务端执行；条件显示不构成访问授权。

`create_form` 与 `detail_view` 分别版本化，只引用字段/关系控件。API 返回 schema、layout、values 和 fieldDecisions；写 API 不接受客户端权限结论。

状态流和节点流统一接受 `WorkflowCommand(actionKey, workItemId, expectedVersion, idempotencyKey, input)`，共用授权、guard、history、outbox 和 availableActions。状态流只有一个 current state；节点流维护一个或多个 active token、分支和汇聚。节点 summary status 只能派生，不能替代 token 事实。

## Relation Contract

| Kind | Required invariant | Removal/lifecycle |
| --- | --- | --- |
| association | 规范化端点后唯一 | tombstone + history |
| parent_child | 默认单父、无自环、DAG | 子项不级联硬删，归档保留边 |
| depends_on | 有向无环，可影响分析 | 移除显式记录，不反向写第二条边 |
| blocks | depends_on 的受保护语义视图 | blocker/blocked 使用同一事实边 |

跨空间建立边要求双方可见、source relate、target accept-link 和 `CrossSpaceGrant`。无目标可见性时只返回 forbidden reference。关系不自动同步字段或状态；同步是 S18 独立规则。

## Authorization Contract

有效 decision 顺序为 workspace/security hard deny -> enterprise governance -> space membership/role -> item role/data scope -> node guard -> field decision。企业管理员不自动获得内容访问，空间管理员也不能绕过数据范围。

`PermissionDecision` 固定输出 allowed、action、current/required level、reason code、policy sources/version、subject version、evaluatedAt 和 disclosure scope。availableActions 必须由同一服务端批量 decision 产生。缓存包含 subject/space/object/config/member-role 水位，并由组织、成员、角色、节点、字段和跨空间授权事件失效。

该合同直接针对 M1 发现的三轨分裂：`project_members` 运行权限、未消费的企业 permission code、未被 ProjectService 使用的 project resource ACL。M3 只设计迁移顺序，不得继续让三者成为并行事实来源。

## UI, API And DTO Contract

- 用户执行：`/api/project-spaces/{spaceId}/work-items...` + `UserWorkItem*`，负责实例协作。
- 空间配置：`/api/project-spaces/{spaceId}/configuration...` + `SpaceConfiguration*`，位于项目产品空间设置。
- 企业治理：`/api/admin/project-governance...` + `AdminProjectGovernance*`，负责目录、策略、风险、迁移和审计排查。

三类 DTO 不继承、不夹带其他视角字段。后台不承载评论/流转等日常执行，普通成员默认不接触元模型。共享仅限 ID、稳定枚举和平台对象摘要。

## Event And Platform Contract

规范平台对象为 `work_item`，路由 `/project-spaces/{spaceId}/work-items/{workItemId}`，deep link `colla://work-item/{workItemId}`。旧 `project`/`issue` objectType 在迁移窗口经 ID map 解析，最晚 S21 删除活动兼容。

事件 envelope 固定 event/schema version、workspace/space/item/type identity、aggregate version、actor、correlation/causation、changedFieldKeys、payload 和 disclosureClass。首批事件覆盖实例生命周期、字段变化、流程动作/状态/节点、关系、角色和配置发布。

Search、Notification、Audit、File、IM、Knowledge 和 Workspace/Admin 只能通过 resolver、query facade 或 outbox projection 接入。尤其禁止继续由 Knowledge、Workspace、Search 或 Admin 直接读取 project Repository/私有表。空间群或工作项会话是策略/自动化，不是 WorkItem 硬依赖。

## Current-To-Target Traceability

| M1 current fact | Target contract | Migration decision ownership |
| --- | --- | --- |
| `projects` 同时承担容器和 project 业务对象 | ProjectSpace + optional project WorkItem 分离 | M3 ID/mapping ADR，S02/S07 实现 |
| `project_members` 是唯一运行权限且与群成员漂移 | ProjectSpace membership 为成员事实；IM 仅消费明确同步策略 | M3 修复/豁免方案，S02 迁移 |
| `issues.issue_type` 固定枚举 | WorkItemTypeDefinition/Version | S03/S07 |
| `iterations` 是空的独立表 | iteration 是普通 work item type | M3 mapping，S03/S07 |
| Java 14 动作 + 前端 fallback | versioned workflow + server availableActions | S08/S09 |
| `issue_relations` polymorphic 且目标无合同 | versioned RelationDefinition/WorkItemRelation | S10 |
| project ACL、企业 code、成员权限分裂 | single layered PermissionDecision | M3 顺序，S11 实现 |
| project/issue 平台对象双轨 | canonical `work_item` + temporary ID aliases | M3/S07/S21 |
| 跨模块 Repository/私有表直读 | query facade + resolver + outbox projection | M3 退出清单，后续逐 Stage 替换 |

## ADR And Rejected Alternatives

| ADR | Decision | Rejected |
| --- | --- | --- |
| ADR-PP-001 | Space 是边界 | project 实例继续兼任容器 |
| ADR-PP-002 | project 是 WorkItemType | project 独立 runtime |
| ADR-PP-003 | immutable published config | 修改 active config 影响历史实例 |
| ADR-PP-004 | 双 workflow runtime，共享协议 | 同一 status 或同一 token graph 强行统一 |
| ADR-PP-005 | versioned relation graph | 固定 project/parent 外键层级 |
| ADR-PP-006 | unified layered decision | 前端、成员、ACL 各自计算 |
| ADR-PP-007 | canonical `work_item` platform object | 永久 project/issue 双轨 |
| ADR-PP-008 | modular monolith facade/event first | 立即微服务或继续私有表直读 |
| ADR-PP-009 | template copy-with-lineage | template live inheritance |
| ADR-PP-010 | 字段物理存储由 M3 spike 决定 | M2 直接选 JSONB/EAV/typed rows |

禁止模式完整列表保存在目标架构 13.10，包含团队专用顶层模块、每类型独立表/Controller、强制 projectId、改写发布版本、条件显示替代授权、前端补动作、管理员内容 bypass、代码插件节点、关系隐式同步、私有表直读、永久双写、流程终态等同归档、模板 live link 和无证据冻结字段存储。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M2-T01 | 术语、稳定 ID、生命周期和归属关系具备唯一解释 | 目标架构 13.1 与本报告 Terminology 表冻结 6 个规范对象及业务键规则。 | 静态合同检查核对 `domain_contract_version: 1`、6 个 canonical name 和 lifecycle 规则。 | 不需要浏览器：术语合同不改变运行页面。 | Done |
| PROJECT-PLATFORM-S01-M2-T02 | 研发、市场、HR、交付由统一模型表达 | 目标架构 13.2 和 Cross-Team Model Proof 映射四场景，明确 project 非强制父项。 | 静态合同检查核对四团队模板、`project` type 和禁止强制 `projectId`。 | 不需要浏览器：产品边界为目标模型评审。 | Done |
| PROJECT-PLATFORM-S01-M2-T03 | 草稿、发布、版本、升级、回滚和历史实例规则完整 | 目标架构 13.3 冻结 10 条发布/升级/模板规则和 diff 分类。 | 静态合同检查核对 immutable、configHash、migration plan、copy-with-lineage 关键词。 | 不需要浏览器：当前配置 UI/运行时未实现。 | Done |
| PROJECT-PLATFORM-S01-M2-T04 | 字段、校验、默认值、条件、布局引用和访问边界规则完整 | 目标架构 13.4 冻结 13 类字段、系统字段、两类布局和服务端决策。 | 静态合同检查核对字段类型、create_form/detail_view、fieldDecisions 和 server validation。 | 不需要浏览器：只冻结未来渲染与写入合同。 | Done |
| PROJECT-PLATFORM-S01-M2-T05 | 状态流和节点流的共同原语、权威状态与差异无冲突 | 目标架构 13.5 冻结 WorkflowCommand、共享 SPI、两类运行时和禁止降维规则。 | 静态合同检查核对 state/node、active token、idempotency 和 aggregate version。 | 不需要浏览器：本轮没有流程设计器或执行 UI。 | Done |
| PROJECT-PLATFORM-S01-M2-T06 | 方向、基数、循环、移除策略、访问边界和同步规则完整 | 目标架构 13.6 冻结 4 kind、DAG、CrossSpaceGrant、forbidden reference 和独立同步。 | 静态合同检查核对 relation kind、cycle、cardinality、spacePolicy 和 tombstone。 | 不需要浏览器：关系合同没有运行时变更。 | Done |
| PROJECT-PLATFORM-S01-M2-T07 | 分层授权的计算、解释、缓存和留痕责任完整 | 目标架构 13.7 冻结 6 层顺序、默认角色、decision 字段和失效事件。 | 静态合同检查核对 PermissionDecision、availableActions、policyVersion、cache key 和 disclosure。 | 不需要浏览器：目标授权模型未切换当前 API。 | Done |
| PROJECT-PLATFORM-S01-M2-T08 | 菜单、API 语义和 DTO 不再混用治理与协作视角 | 目标架构 13.8 冻结三类 Surface、路径前缀、DTO 和职责边界。 | 静态合同检查核对 UserWorkItem、SpaceConfiguration、AdminProjectGovernance 和三个 API scope。 | 不需要浏览器：本轮不调整现有菜单和路由。 | Done |
| PROJECT-PLATFORM-S01-M2-T09 | 平台接入点、事件 envelope 和消费归属稳定 | 目标架构 13.9 冻结 work_item 对象、事件字段/目录及七类接入规则。 | 静态合同检查核对 objectType、event fields、outbox、facade/projection 和 S21 alias 退出。 | 不需要浏览器：平台事件与对象仅为目标合同。 | Done |
| PROJECT-PLATFORM-S01-M2-T10 | 选择、替代方案、延后项和禁止模式可追溯 | 目标架构 13.10 与本报告冻结 ADR-PP-001 至 ADR-PP-010 及禁止清单。 | `pnpm work:plan-check` 和文档结构/合同静态检查通过。 | 不需要浏览器：ADR 评审无用户可见行为。 | Done |

## Code Changes

- 未修改业务代码、数据库、API、权限判断或前端行为。
- `project-platform-target-architecture.md` 增加目标领域合同 v1、10 项 ADR 和禁止模式。
- `current-roadmap.md` 记录 M2 十项完成，并把下一执行入口切换到 M3。
- 本报告记录 M1 当前事实到目标合同的逐项追踪和 M3 延后决策。

## Validation

- Backend tests: 不需要；本里程碑没有业务代码或 schema 变更，M1 的项目集成测试基线保持 5/5。
- Frontend build: 不需要；本里程碑没有前端代码、路由、组件或样式变更。
- Local quality gate: `.local-reports/quality-gate-20260718T120906.md` 为 PASS；`pnpm work:plan-check` 通过，领域合同 28 个关键标记静态断言通过。
- Browser smoke: 不需要；本里程碑只冻结目标领域和 ADR，不改变当前页面、API 或用户交互。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 动态字段采用 JSONB、类型化行或混合投影尚未选择 | non-blocking | 按 ADR-PP-010 进入 S01-M3-T01/T05 spike，以查询和性能证据决策。 |
| N/A | 旧 projects/issues 到 space/work_item 的 ID、批次和切流方案尚未冻结 | non-blocking | 进入 S01-M3-T02 至 T04，不属于 M2 领域语义合同。 |
| N/A | 当前三轨项目授权到统一 decision 的迁移顺序尚未设计 | non-blocking | S01-M3 风险/迁移输入，目标语义已由 13.7 冻结。 |

## Next Steps

- 进入 PROJECT-PLATFORM-S01-M3，以本合同为约束比较字段存储、ID 映射、迁移批次、切流和状态/节点运行时 spike。
- M3 只能决定物理实现和迁移策略，不得重新引入本报告拒绝的固定项目模型、可变发布配置或多权限事实来源。
