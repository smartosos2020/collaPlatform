---
title: PROJECT-PLATFORM-S16 估分、工时、产能和人员排期当前执行路线
status: active
route: PROJECT-PLATFORM-S16
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 39
stage: PROJECT-PLATFORM-S16
stage_final_milestone: PROJECT-PLATFORM-S16-M4
last_code_check: 2026-07-28
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S16 估分、工时、产能和人员排期

## 1. Stage 目标

在 S15 项目计划、里程碑、治理台账、交付评审和健康聚合完成并归档的基础上，交付工作日历、估分单位、实际工时、人员负荷、产能冲突和资源排期。S16 只拥有工时、日历、分配和产能规则自身的权威；WorkItem、计划、里程碑、成员、权限、日期、关系、流程、审计和文件仍由既有 owner 持有。

S16 的跨事项和跨空间聚合必须以 S11 当前 decision/data scope、S15 计划/里程碑稳定 identity 和显式人员可见性为边界。不得通过负荷、空隙、冲突、计数、时间轴或导出泄漏隐藏事项；不得把本地确定性预算当成生产容量，也不得提前实现 S17 自动化、S18 跨空间同步或 S19 组织级效能度量。

## 2. 固定输入与当前事实

- S15 完成路线已归档；当前 schema 为 V117，计划/里程碑、治理台账、交付评审和可解释项目健康均已交付。
- S07 canonical WorkItem identity/runtime 是事项唯一权威；估分和工时只能稳定引用 WorkItem，不复制标题、动态字段、流程或活动。
- S08/S09 流程状态、节点任务和参与者动作是执行唯一权威；工时登记、负荷和排期不得冒充流程执行。
- S10 relation/hierarchy 与 S14 甘特/日期/基线是依赖、层级和排期展示输入；S16 不直接读写其私表。
- S11 snapshot v5 decision/data scope 对人员、事项、日期、工时、负荷、计数、冲突和导出继续失败关闭；enterprise-admin 不自动获得私有内容。
- S12 个人入口、通知和 realtime 只提供受权导航与失效信号；S16 不把通知或缓存当排期成功。
- S13 查询/保存视图和 S14 日历/甘特可作为受权集合与展示输入，不成为人员分配、工时或产能权威。
- S15 Plan/Milestone 是项目治理来源；S16 通过公共合同引用，不改写计划阶段、里程碑日期或健康信号。
- identity、成员、审计、event 和文件只通过公共合同使用；所有可重建索引均过期、可删除且不授权。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份仍是最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. WorkCalendar、Estimate、Worklog、Allocation、Capacity、Conflict 与 ResourceSchedule 使用版本化合同、稳定 identity、显式生命周期和 workspace/space 复合边界。
3. 工作日历必须明确时区、工作周、节假日、例外和部分可用性；DST/locale 只影响解释，不静默改写事实。
4. 估分单位、工时和分配必须显式区分；不得从故事点伪造小时、从实际工时反写估算或从排期反写计划日期。
5. 写命令包含 caller-stable request ID、expected version、持久 receipt、audit/outbox 和稳定重放；并发只允许一胜一冲突。
6. 跨事项/人员/空间聚合先逐项取得当前受权最小事实；隐藏对象不进入标题、数量、负荷、空隙、冲突、导出或错误外形。
7. 产能、利用率和冲突均为有界可解释派生；来源版本、规则、窗口和截断明确，不冒充生产容量或 S19 指标。
8. 资源调整调用规范 allocation/计划公共命令并保留 provenance；浏览器不得直接写 WorkItem、Plan、Milestone 或成员私表。
9. realtime 只失效查询与索引，online/focus/reconnect 后 REST 校准；离线输入保留但不伪造工时、分配或调整成功。
10. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S16-M1 | 工作日历、估分单位和排期规则 | S15 归档；Program revision 39 | `docs/90-reports/project-platform-s16-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S16-M2 | 实际工时、登记和修订审计 | M1 | `docs/90-reports/project-platform-s16-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S16-M3 | 人员负荷、产能和冲突识别 | M1-M2 | `docs/90-reports/project-platform-s16-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S16-M4 | 人员排期甘特、资源调整与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s16-m4-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S16-M1 工作日历、估分单位和排期规则

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S16-M1-T01 | 审计 S11 权限、S14 日期/甘特、S15 计划/里程碑及现有工时排期事实 | API、表、owner、调用方、迁移、预算、复用能力和禁止依赖可定位；不依赖旧会话结论 | Pending |
| PROJECT-PLATFORM-S16-M1-T02 | 冻结 WorkCalendar、CalendarException、EstimateUnit、Estimate 与 SchedulingRule 合同 | schema version、identity、时区、单位、精度、来源、错误、生命周期和上限明确 | Pending |
| PROJECT-PLATFORM-S16-M1-T03 | 设计工作日历、例外、估分、规则和命令回执 Flyway schema | workspace/space 复合边界、唯一性、FK、索引、清理与 owner 完整 | Pending |
| PROJECT-PLATFORM-S16-M1-T04 | 实现工作周、时区、节假日、例外和部分可用性生命周期 | IANA timezone、DST、跨日、半日与覆盖顺序确定；变更可追溯 | Pending |
| PROJECT-PLATFORM-S16-M1-T05 | 实现估分单位、精度、上下限、创建、修订和归档 | 点数/小时/天不隐式换算；旧估算不可静默覆盖，版本可解释 | Pending |
| PROJECT-PLATFORM-S16-M1-T06 | 通过公共合同绑定受权 WorkItem、Plan、Milestone 和成员 | 无私表 join 或复制标题/日期/成员/权限；收权引用完整省略 | Pending |
| PROJECT-PLATFORM-S16-M1-T07 | 实现排期窗口、工作日折算和可解释完成日期派生 | 只消费当前日历/估算/计划事实；缺失、截断和冲突显式降级 | Pending |
| PROJECT-PLATFORM-S16-M1-T08 | 交付日历、例外、估分和规则 Web 配置与成员交互 | 1440/1366/820、长名称、键盘、loading、空态/错误和单位来源可理解 | Pending |
| PROJECT-PLATFORM-S16-M1-T09 | 接入 realtime 失效、离线输入、多标签冲突和 REST 校准 | 断线不丢输入；恢复不重复估分，收权后旧引用不闪现 | Pending |
| PROJECT-PLATFORM-S16-M1-T10 | 完成六身份、跨空间、并发、归档、DST、离线和恢复测试 | 无日历/估分/成员/日期/数量泄漏、重复命令或 enterprise 旁路 | Pending |
| PROJECT-PLATFORM-S16-M1-T11 | 执行日历、例外、估分、排期端口和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产人员容量或 SLO | Pending |
| PROJECT-PLATFORM-S16-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明日历/估分事实；实际工时、负荷与资源调整仍由 M2-M4 交付 | Pending |

### PROJECT-PLATFORM-S16-M2 实际工时、登记和修订审计

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S16-M2-T01 | 复核 M1 日历/估分、权限、命令、迁移和未关闭阻断 | 12 项逐项可追溯；工时不复制 WorkItem、计划、流程或成员权威 | Pending |
| PROJECT-PLATFORM-S16-M2-T02 | 冻结 Worklog、WorklogRevision、TimeEntrySource 与 ApprovalState 合同 | 日期、时长、人员、来源、修订、状态、错误、保留和上限明确 | Pending |
| PROJECT-PLATFORM-S16-M2-T03 | 设计工时、不可变修订、当前指针和命令回执 Flyway schema | 复合边界、唯一性、约束、索引、不可变历史、清理和 owner 完整 | Pending |
| PROJECT-PLATFORM-S16-M2-T04 | 实现工时创建、编辑、提交、撤回、作废和精确重放 | current 指针原子；旧修订保留，expected version、audit/outbox 完整 | Pending |
| PROJECT-PLATFORM-S16-M2-T05 | 实现日期、时长、重叠、未来时间、跨日和工作日校验 | 服务端使用当前日历；非法或冲突登记不留下幽灵工时 | Pending |
| PROJECT-PLATFORM-S16-M2-T06 | 实现本人、代理和治理修订边界及不可变原因 | 代理必须显式授权；治理不旁路内容，修订人/原因/来源可追溯 | Pending |
| PROJECT-PLATFORM-S16-M2-T07 | 实现估算与实际的有界偏差比较 | 不反写估算或计划；来源版本、缺失、截断和单位不可比显式 | Pending |
| PROJECT-PLATFORM-S16-M2-T08 | 交付工时列表、录入、修订历史和偏差 Web | 1440/1366/820、键盘、长名称、日期/时长错误和权限来源可理解 | Pending |
| PROJECT-PLATFORM-S16-M2-T09 | 接入受权事项、realtime、离线草稿、多标签冲突和 REST 校准 | 缓存不授权；断线不丢草稿，恢复不重复登记或覆盖新修订 | Pending |
| PROJECT-PLATFORM-S16-M2-T10 | 完成六身份、跨空间、代理、并发、收权、作废、离线和恢复测试 | 无事项/人员/日期/时长/修订泄漏、重复工时或 enterprise 旁路 | Pending |
| PROJECT-PLATFORM-S16-M2-T11 | 执行工时数、修订数、查询端口、导出和渲染预算 | SQL/端口/内存/DOM 上界可复现；不冒充生产工时吞吐或 SLO | Pending |
| PROJECT-PLATFORM-S16-M2-T12 | 同步工时/修订/审计/事件合同并完成 M2 checkpoint | 当前事实与 M3 负荷输入清楚；不提前声明人员产能或资源排期 | Pending |

### PROJECT-PLATFORM-S16-M3 人员负荷、产能和冲突识别

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S16-M3-T01 | 复核 M1-M2 日历/估分/工时、成员、权限和未关闭阻断 | 24 项逐项可追溯；负荷不复制人员、事项、计划或权限权威 | Pending |
| PROJECT-PLATFORM-S16-M3-T02 | 冻结 Allocation、CapacityWindow、LoadBucket、CapacitySignal 与 Conflict 合同 | identity、窗口、比例、来源、规则、解释、截断、错误和上限明确 | Pending |
| PROJECT-PLATFORM-S16-M3-T03 | 设计人员分配、产能规则、可重建负荷索引和回执 Flyway schema | 复合边界、唯一性、FK、索引、清理、过期与 owner 完整 | Pending |
| PROJECT-PLATFORM-S16-M3-T04 | 实现人员分配创建、调整、拆分、结束、归档和精确重放 | 比例/日期服务端校验；并发一胜一冲突，不改写 WorkItem assignee | Pending |
| PROJECT-PLATFORM-S16-M3-T05 | 实现日历与分配的人员可用产能窗口派生 | 工作日、例外、部分可用性和重叠规则可解释且有界 | Pending |
| PROJECT-PLATFORM-S16-M3-T06 | 聚合估分、实际、分配和计划的当前受权最小事实 | 只调用公共合同；无私表 join、跨空间旁路或全量客户端过滤 | Pending |
| PROJECT-PLATFORM-S16-M3-T07 | 实现欠载、满载、超载、日期冲突和不可比分配信号 | 每个信号有来源版本/窗口/规则；缺失或截断返回 unknown | Pending |
| PROJECT-PLATFORM-S16-M3-T08 | 交付人员负荷、产能窗口、冲突解释和筛选 Web | 1440/1366/820、长名称、键盘、loading、空态/错误和来源可理解 | Pending |
| PROJECT-PLATFORM-S16-M3-T09 | 接入 realtime、离线输入、多标签冲突、收权和 REST 校准 | 索引不授权；旧人员/事项/数量不闪现，不伪造资源调整成功 | Pending |
| PROJECT-PLATFORM-S16-M3-T10 | 完成六身份、跨空间、并发、收权、删除、离线和恢复测试 | 无人员/事项/负荷/空隙/冲突泄漏、幽灵分配或 enterprise 旁路 | Pending |
| PROJECT-PLATFORM-S16-M3-T11 | 执行人员数、事项数、窗口桶、信号、端口和渲染预算 | 固定夹具和上界可复现；不声明生产 headcount、容量或利用率 SLO | Pending |
| PROJECT-PLATFORM-S16-M3-T12 | 同步分配/负荷/产能/信号合同并完成 M3 checkpoint | 当前事实与 M4 资源排期输入清楚；不提前实现 S17 或 S19 | Pending |

### PROJECT-PLATFORM-S16-M4 人员排期甘特、资源调整与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S16-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S16-M4-T02 | 冻结 ResourceSchedule、ResourceRow、AssignmentBar、ConflictMarker 与 Adjustment 合同 | 来源、窗口、顺序、解释、截断、版本、错误、权限和上限明确 | Pending |
| PROJECT-PLATFORM-S16-M4-T03 | 设计排期偏好、可重建窗口索引、调整回执和统计 Flyway schema | 只保存低基数派生/identity/version；复合边界、索引、清理和 owner 完整 | Pending |
| PROJECT-PLATFORM-S16-M4-T04 | 聚合人员、日历、估分、工时、分配、计划和里程碑最小事实 | 只调用 owner 公共合同；无私表 join、重复事实、全量后过滤或跨空间旁路 | Pending |
| PROJECT-PLATFORM-S16-M4-T05 | 实现人员排期行、分配条、负荷曲线和冲突标记有界派生 | 每个条/标记有来源/窗口/规则；缺失、截断和冲突显式降级 | Pending |
| PROJECT-PLATFORM-S16-M4-T06 | 实现资源调整预览、提交、部分失败恢复和精确重放 | 只调用 canonical allocation/plan 命令；provenance、冲突与回滚完整 | Pending |
| PROJECT-PLATFORM-S16-M4-T07 | 对排期、计数、负荷、冲突、导出和深链执行当前权限重校准 | 收权后旧标题、人员、数量、空隙和原因不泄漏；enterprise 无旁路 | Pending |
| PROJECT-PLATFORM-S16-M4-T08 | 交付人员排期甘特、资源调整、解释和响应式 Web | 1440/1366/820、长名称、键盘、loading、空态/错误和来源可用 | Pending |
| PROJECT-PLATFORM-S16-M4-T09 | 完成六身份、跨空间、并发、收权、删除、离线和组合规模预算验收 | 固定夹具与 SQL/端口/内存/DOM 上界可复现；无人员/事项/冲突泄漏 | Pending |
| PROJECT-PLATFORM-S16-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；关键视口、六身份和 route-final 证据 fresh | Pending |
| PROJECT-PLATFORM-S16-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S17 准入 | 文档只声明已实现事实；S17 继续复用 S11/S16 权限和幂等合同 | Pending |
| PROJECT-PLATFORM-S16-M4-T12 | 给出 S16 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、48 Task、工作上下文和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 工作日历、估分、工时、人员分配和产能规则拥有明确唯一权威，并与 WorkItem、流程、计划日期和成员事实分离。
- 时区、工作周、节假日、DST、部分可用性、估分单位、工时修订和估实偏差语义明确且可追溯。
- 人员负荷、产能、冲突与资源排期只聚合当前受权最小事实，每个信号可解释、有界、可失效重建。
- 幂等、乐观锁、audit/outbox、realtime、离线、多标签、长名称、1440/1366/820 和六身份闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 `route-final` 无阻断。
- S16 不实现 S17 自动化、S18 跨空间同步或 S19 管理度量，也不把本地预算表述为生产 headcount、容量、利用率或 SLO。

## 7. 起始点

S15 四个 Milestone、48 个 Task、V114-V117 与 route-final 已完成并归档。当前 schema 为 V117，唯一执行入口为 `PROJECT-PLATFORM-S16-M1-T01`；S16 必须从 M1 独立工作循环开始，不得从计划健康推断人员产能，也不得提前实现 S17。
