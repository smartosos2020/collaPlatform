---
title: PROJECT-PLATFORM-S15 计划、里程碑、风险、交付物和评审当前执行路线
status: completed
route: PROJECT-PLATFORM-S15
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 38
stage: PROJECT-PLATFORM-S15
stage_final_milestone: PROJECT-PLATFORM-S15-M4
last_code_check: 2026-07-28
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S15 计划、里程碑、风险、交付物和评审

## 1. Stage 目标

在 S14 看板、日历、甘特、基线和时间线已经完成并归档的基础上，交付项目计划、阶段、里程碑、风险/问题/决策/变更台账、交付物版本、评审会签、验收结论和项目详情健康聚合。S15 建立这些项目治理对象自身的唯一权威，但必须通过规范 identity 和公共合同引用 WorkItem、流程、关系、日期、文件、审计与用户，不得复制或改写既有 owner 的事实。

计划与里程碑必须复用 S14 日期、依赖和基线合同以及 S11 当前 decision/data scope；风险、交付物、评审和健康状态只披露当前调用者可见的最小事实。S15 不把流程节点、甘特条、baseline、统计缓存或浏览器表单当成新的 WorkItem/流程/日期/关系/权限权威，不提前实现 S16 估分工时与人员产能、S17 自动化、S18 跨空间同步或 S19 管理度量。

## 2. 固定输入与当前事实

- S14 完成路线已归档；当前 schema 为 V117，S15 计划、治理台账、交付评审与详情健康聚合均已交付，S14 受权排期能力继续作为输入。
- S07 canonical WorkItem identity/runtime 是工作项唯一权威；计划对象只能稳定引用 WorkItem，不得复制标题、字段或运行状态。
- S08/S09 状态流与节点流是执行进度和动作唯一权威；计划阶段与流程节点必须显式区分，不能互相冒充。
- S10 relation/hierarchy 是工作项关系、层级和依赖唯一权威；计划链接只能通过公共投影读取或规范命令写入。
- S11 snapshot v5 decision/data scope 对计划、台账、交付、评审、计数和健康聚合继续失败关闭；enterprise-admin 不自动获得私有内容。
- S12 个人入口、通知、动态与对象 resolver 可作为受权导航和失效入口；S15 不直接读写其私表。
- S13 QueryDefinition/SavedView 与 S14 日期、甘特、基线、时间线是计划查询和排期展示输入，不成为 S15 治理事实。
- 文件、审计、通知、搜索和 realtime 只能通过各 owner 的公共合同或事件接入；缓存与索引均可重建且不授权。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份仍是最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. Plan、Phase、Milestone、RegisterEntry、Deliverable、Review 与 Acceptance 使用版本化合同、稳定 identity、显式生命周期和 workspace/space 复合边界。
3. 所有外部对象引用先经 owner 公共 resolver 与当前权限重校准；隐藏对象不得通过标题、数量、空隙、健康分、错误或导出泄漏。
4. 写命令必须包含 caller-stable request ID、expected version、持久 receipt、audit/outbox 和稳定重放；并发只允许一胜一冲突。
5. 计划阶段不同于 S08/S09 流程节点；里程碑日期不同于 WorkItem 日期。需要联动时必须调用规范公共命令并留下明确 provenance。
6. 风险、问题、决策和变更台账必须保留责任人、响应、状态和不可变历史；关闭、重开、替代和撤销均为显式命令。
7. 交付物版本、评审轮次、会签和验收结论可追溯但不复制文件正文、文档内容或外部对象权限。
8. 健康状态只能由当前受权、可解释、有界的计划偏差、未闭风险和验收阻断派生；不是 S19 组织级指标或生产 SLO。
9. realtime 只失效聚合与索引，online/focus/reconnect 后经 REST 校准；离线编辑保留输入但不伪造成功。
10. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S15-M1 | 项目计划、阶段和里程碑 | S14 归档；Program revision 37 | `docs/90-reports/project-platform-s15-m1-execution-report.md` | Done |
| PROJECT-PLATFORM-S15-M2 | 风险、问题、决策和变更台账 | M1 | `docs/90-reports/project-platform-s15-m2-execution-report.md` | Done |
| PROJECT-PLATFORM-S15-M3 | 交付物、评审要素和验收结论 | M1-M2 | `docs/90-reports/project-platform-s15-m3-execution-report.md` | Done |
| PROJECT-PLATFORM-S15-M4 | 项目详情聚合、健康状态与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s15-m4-execution-report.md` | Done |

## 5. 详细任务

### PROJECT-PLATFORM-S15-M1 项目计划、阶段和里程碑

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S15-M1-T01 | 审计 S14 排期/基线、S09 节点、S10 依赖、S11 权限与现有项目计划事实 | API、表、owner、调用方、迁移、预算、可复用能力和禁止依赖可定位；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S15-M1-T02 | 冻结 ProjectPlan、PlanPhase、PlanMilestone、PlanLink 与 PlanChange 合同 | schema version、identity、顺序、日期、状态、来源、错误、升级和生命周期明确 | Done |
| PROJECT-PLATFORM-S15-M1-T03 | 设计计划、阶段、里程碑、链接、历史和命令回执 Flyway schema | workspace/space 复合边界、唯一性、FK、索引、不可变历史、清理与 owner 完整 | Done |
| PROJECT-PLATFORM-S15-M1-T04 | 实现计划创建、编辑、发布、归档、恢复和精确命令重放 | expected version、request hash、receipt、audit/outbox 完整；并发一胜一冲突 | Done |
| PROJECT-PLATFORM-S15-M1-T05 | 实现阶段与里程碑顺序、日期、责任人、状态和显式变更历史 | 阶段不冒充流程节点；日期不改写 WorkItem；关闭/重开/改期均可追溯 | Done |
| PROJECT-PLATFORM-S15-M1-T06 | 通过公共合同链接受权 WorkItem、节点、依赖、日期与基线 | 无私表 join 或复制事实；隐藏引用不进入名称、计数、错误或导出 | Done |
| PROJECT-PLATFORM-S15-M1-T07 | 实现里程碑范围、进度和排期偏差的有界可解释派生 | 只消费当前可见 canonical facts；缺失、截断和收权显式降级且可重建 | Done |
| PROJECT-PLATFORM-S15-M1-T08 | 交付计划/阶段/里程碑 Web、排序、深链、键盘和响应式交互 | 1440/1366/820、长名称、loading、空态、错误和权限来源可理解 | Done |
| PROJECT-PLATFORM-S15-M1-T09 | 接入 realtime 失效、离线输入、多标签冲突和 REST 校准 | 断线不丢编辑输入；收权后旧计划不闪现，不伪造发布或改期成功 | Done |
| PROJECT-PLATFORM-S15-M1-T10 | 完成六身份、跨空间、并发、收权、归档、离线和恢复自动化测试 | 无计划/标题/日期/数量泄漏、重复事实、错序或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S15-M1-T11 | 执行阶段数、里程碑数、链接数、派生端口和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产容量或 S16 人员产能 | Done |
| PROJECT-PLATFORM-S15-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明计划事实；风险、交付评审和健康聚合仍由 M2-M4 交付 | Done |

### PROJECT-PLATFORM-S15-M2 风险、问题、决策和变更台账

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S15-M2-T01 | 复核 M1 计划、迁移、权限、命令和未关闭阻断 | 12 项逐项可追溯；台账不复制计划、WorkItem 或流程权威 | Done |
| PROJECT-PLATFORM-S15-M2-T02 | 冻结 Risk、Issue、Decision、ChangeRequest、ResponsePlan 与 RegisterHistory 合同 | 分类、概率/影响、责任、响应、状态、替代/撤销、版本和上限明确 | Done |
| PROJECT-PLATFORM-S15-M2-T03 | 设计统一台账、类型明细、响应、引用、历史和命令回执 Flyway schema | 复合边界、唯一性、约束、索引、不可变历史、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S15-M2-T04 | 实现风险识别、评估、责任、响应、监控、关闭和重开闭环 | 当前责任人与到期语义明确；变化留痕，关闭不删除历史或伪造低风险 | Done |
| PROJECT-PLATFORM-S15-M2-T05 | 实现问题确认、升级、处置、解决、验证与重开闭环 | 问题与 WorkItem/缺陷显式区分；验证结论、责任和阻断可追溯 | Done |
| PROJECT-PLATFORM-S15-M2-T06 | 实现决策提议、采纳、替代、撤销及不可变依据摘要 | 旧结论不可覆盖；替代链无环，正文/附件由 owner 保存且当前鉴权 | Done |
| PROJECT-PLATFORM-S15-M2-T07 | 实现变更申请、影响分析、审批结论与计划规范命令编排 | 未批准不改计划；批准后精确重放、部分失败可恢复且 provenance 完整 | Done |
| PROJECT-PLATFORM-S15-M2-T08 | 交付台账 Web、筛选、责任/响应、详情、历史和响应式交互 | 1440/1366/820、键盘、长名称、空态/错误和状态来源可理解 | Done |
| PROJECT-PLATFORM-S15-M2-T09 | 接入受权引用、realtime、离线输入、提醒失效和 REST 校准 | 不读 owner 私表；收权后旧引用/数量不闪现，离线不伪造处置成功 | Done |
| PROJECT-PLATFORM-S15-M2-T10 | 完成六身份、跨空间、并发、收权、替代/撤销、离线和恢复测试 | 无风险/问题/决策/变更标题或计数泄漏、重复命令和 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S15-M2-T11 | 执行台账条目、历史、引用、响应计划和渲染预算 | 查询/端口/内存/DOM 上界可复现；不冒充生产风险容量或 SLO | Done |
| PROJECT-PLATFORM-S15-M2-T12 | 同步台账/命令/审计/事件合同并完成 M2 checkpoint | 当前事实与 M3 输入清楚；不提前声明交付评审或项目健康 | Done |

### PROJECT-PLATFORM-S15-M3 交付物、评审要素和验收结论

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S15-M3-T01 | 复核 M1-M2 计划/台账、文件对象、权限、事件和阻断 | 24 项逐项可追溯；交付评审不复制文件、文档、计划或权限权威 | Done |
| PROJECT-PLATFORM-S15-M3-T02 | 冻结 Deliverable、DeliverableVersion、ReviewRound、Signoff 与 Acceptance 合同 | identity、版本、物料引用、评审项、参与者、结论、错误和上限明确 | Done |
| PROJECT-PLATFORM-S15-M3-T03 | 设计交付物、不可变版本、评审轮次、会签、验收和回执 Flyway schema | 复合边界、唯一性、FK、索引、不可变结论、清理与 owner 完整 | Done |
| PROJECT-PLATFORM-S15-M3-T04 | 实现交付物创建、版本提交、替代、撤回、归档和精确重放 | 已提交版本不可原地覆盖；current 指针原子，失败不遗留幽灵版本 | Done |
| PROJECT-PLATFORM-S15-M3-T05 | 通过公共 resolver 绑定文件、知识、WorkItem、里程碑和外部物料引用 | 只保存稳定 identity/type/version；不复制正文、路径、标题或授权 | Done |
| PROJECT-PLATFORM-S15-M3-T06 | 实现评审轮次、评审要素、意见结论、修改要求和关闭/重开 | 轮次与要素版本固定；意见事实可追溯，隐藏参与者/物料最小披露 | Done |
| PROJECT-PLATFORM-S15-M3-T07 | 实现会签策略、并发签署、拒绝、撤销和验收结论 | required signer/quorum 服务端判定；一人一结论，最终结论不可静默改写 | Done |
| PROJECT-PLATFORM-S15-M3-T08 | 交付交付物目录、版本、评审、会签、验收 Web 和响应式交互 | 1440/1366/820、长名称、键盘、状态、错误和权限来源可理解 | Done |
| PROJECT-PLATFORM-S15-M3-T09 | 接入计划/风险/变更追踪、realtime、离线输入和 REST 校准 | 链路可解释且不反向扩权；断线不丢意见，恢复不重复签署 | Done |
| PROJECT-PLATFORM-S15-M3-T10 | 完成六身份、跨空间、并发签署、收权、替代、离线和恢复测试 | 无物料/标题/参与者/结论泄漏、重复版本、幽灵签署或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S15-M3-T11 | 执行交付物、版本、评审项、签署、引用和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产吞吐或评审 SLO | Done |
| PROJECT-PLATFORM-S15-M3-T12 | 同步交付/评审/验收/对象合同并完成 M3 checkpoint | 当前事实与 M4 聚合输入清楚；不提前实现 S16 或 S19 指标 | Done |

### PROJECT-PLATFORM-S15-M4 项目详情聚合、健康状态与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S15-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Done |
| PROJECT-PLATFORM-S15-M4-T02 | 冻结 ProjectDetail、HealthSignal、HealthStatus、Deviation 与 BlockingSummary 合同 | 来源、阈值、解释、截断、版本、错误、权限和生命周期明确 | Done |
| PROJECT-PLATFORM-S15-M4-T03 | 设计详情偏好、可重建聚合索引、健康信号和命令回执 Flyway schema | 只保存低基数派生与来源版本；复合边界、索引、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S15-M4-T04 | 聚合计划、里程碑、风险、问题、变更、交付和验收最小事实 | 只调用 owner 公共合同；无私表 join、重复事实、全量后过滤或跨空间旁路 | Done |
| PROJECT-PLATFORM-S15-M4-T05 | 实现进度、排期偏差、未闭风险、阻断和验收健康的有界可解释派生 | 每个信号有来源/时间/规则；缺失、截断和冲突显式降级，不成为 S19 指标 | Done |
| PROJECT-PLATFORM-S15-M4-T06 | 对详情、计数、健康、导出和深链执行当前权限重校准 | 收权后旧标题、数量、信号和原因不泄漏；enterprise/non-member 无旁路 | Done |
| PROJECT-PLATFORM-S15-M4-T07 | 交付项目详情、里程碑、台账、交付评审和健康 Web 聚合 | 1440/1366/820、长名称、键盘、loading、空态/错误和解释可用 | Done |
| PROJECT-PLATFORM-S15-M4-T08 | 接入 realtime 失效、离线输入、多标签冲突、恢复和 REST 校准 | 缓存不授权；收权无闪现，断线不丢输入，不伪造健康或验收成功 | Done |
| PROJECT-PLATFORM-S15-M4-T09 | 完成六身份、跨空间、并发、收权、删除、离线及组合规模预算验收 | 固定夹具与 SQL/端口/内存/DOM 上界可复现；无标题/计数/信号泄漏 | Done |
| PROJECT-PLATFORM-S15-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；关键视口、六身份和 route-final 证据 fresh | Done |
| PROJECT-PLATFORM-S15-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S16 准入 | 文档只声明已实现事实；S16 继续复用 S11/S15 权限和计划合同 | Done |
| PROJECT-PLATFORM-S15-M4-T12 | 给出 S15 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、48 Task、工作上下文和文档一致；仅无阻断时 Completed | Done |

## 6. Stage 验收

- 项目计划、阶段、里程碑拥有明确唯一权威并与 S08/S09 流程节点、S14 甘特条/基线语义分离。
- 风险、问题、决策和变更台账具备责任、响应、状态、关闭/重开/替代/撤销及不可变历史闭环。
- 交付物版本、物料引用、评审轮次、会签和验收结论可追溯，不复制文件/知识正文或扩展底层权限。
- 项目详情与健康状态只聚合当前受权最小事实，每个信号可解释、有界、可失效重建且不冒充 S19 组织级指标。
- 幂等、乐观锁、审计/outbox、realtime、离线、多标签、长名称、1440/1366/820 和六身份闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 `route-final` 无阻断。
- S15 不实现 S16 估分工时/人员产能、S17 自动化、S18 跨空间同步或 S19 管理度量，也不把本地预算表述为生产容量。

## 7. 起始点

S15-M1/M2/M3/M4、48 个 Task 均已完成，当前 schema 为 V117，四份执行报告与 route-final 无阻断。本路线已通过独立 archive-only 工作循环归档；S16 已在 Program revision 39 激活，后续执行以新的 `docs/02-roadmap/current-roadmap.md` 为唯一入口。
