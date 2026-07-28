---
title: PROJECT-PLATFORM-S19 度量、效能、治理和管理驾驶舱当前执行路线
status: completed
archived_at: 2026-07-29
route: PROJECT-PLATFORM-S19
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 46
stage: PROJECT-PLATFORM-S19
stage_final_milestone: PROJECT-PLATFORM-S19-M4
last_code_check: 2026-07-28
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S19 度量、效能、治理和管理驾驶舱

## 1. Stage 目标

在 S18 跨空间授权、关系、同步、全景和协作审计完成并归档的基础上，建立版本化指标语义、维度与时间窗口，交付权限过滤准确的图表/看板/跨空间数据源、可解释且可关闭的风险预警，以及只面向治理事实的管理驾驶舱和审计报表。S19 只拥有指标定义/版本、图表与看板配置、风险策略/信号、治理报表定义/运行和精确治理回执；WorkItem、流程、关系、权限、成员、资源、自动化、审计、事件和跨空间授权/同步事实仍由既有 owner 持有。

所有指标、聚合、预警和治理报表必须在服务端逐项复用 S11 当前 decision/data scope 及各 owner 公共合同。未知、缺失、过期、截断和无权限不能折算为零；enterprise-admin 不自动获得私有内容。个人级排名、绩效评分、隐式授权、私表 join、浏览器补聚合和无来源数字均禁止，S20 场景模板不得提前实现。

## 2. 固定输入与当前事实

- S18 完成路线已归档；当前 schema 为 V130，跨空间 grant、canonical relation 调用、版本化同步/冲突/补偿和当前受权全景已交付。
- S03 可靠事件、S07 canonical WorkItem、S08/S09 流程、S10 关系/层级和 S11 snapshot v5 decision/data scope 继续分别拥有事件、实例、流程、关系和权限权威。
- S12-S14 的个人工作、统一查询、保存视图、看板/日历/甘特/时间线提供当前受权展示与查询合同，不构成组织指标语义。
- S15 计划/里程碑/治理台账/交付评审、S16 日历/估分/工时/产能/排期和 S17 自动化运行/连接器/限额只能通过公共最小合同进入指标或风险证据。
- S18 跨空间 grant、relation、sync 和 panorama 只提供当前受权最小 identity/version/status/source；S19 不读取 V127-V130 私表，也不把 panorama health 直接升级为 KPI。
- identity、permission、audit、event、notification、realtime、file 和 credential 只通过公共合同使用；缓存、物化、索引、统计、图表和报表均不授权。
- 指标必须绑定定义版本、维度版本、时区、日历、窗口、分母、过滤、来源版本、freshness 与 truncation，结果可重复计算和解释。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份，以及单空间/跨空间组合，构成最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. MetricDefinition、MetricVersion、Dimension、Window、DataSource、Chart、Dashboard、RiskPolicy、RiskSignal 和 GovernanceReport 使用稳定 identity、不可变版本和显式生命周期。
3. 指标表达式只允许注册 measure/dimension/aggregation/window 运算，不允许任意 SQL、脚本、模板执行、反射或私表名称。
4. 查询计划在服务端对每个来源重新校准当前权限与 data scope；无权数据不进入分子、分母、facet、游标、图例、健康、风险或错误外形。
5. 未知、缺失、过期、截断、抑制和无样本必须显式表达；禁止用零、正常或成功掩盖不完整证据。
6. 跨空间聚合只消费当前 active grant 下 owner 公共最小投影；撤销、收权、归档后新查询和新报表立即失败关闭。
7. 风险信号绑定策略版本、来源 identity/version、窗口和解释；确认、关闭、抑制、重开和失效不改写来源事实。
8. 管理驾驶舱只展示治理元数据、配置健康、审计覆盖和受保护聚合；不提供个人排名、绩效评分或企业角色内容旁路。
9. 写命令使用 caller-stable request ID、expected version、request hash、持久 receipt、audit/outbox 和精确重放；并发只允许一胜一冲突。
10. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、单/跨空间六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M1 | 指标语义层、维度和时间窗口 | S18 归档；Program revision 45 | `docs/90-reports/project-platform-s19-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S19-M2 | 图表、看板和跨空间数据源 | M1 | `docs/90-reports/project-platform-s19-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S19-M3 | 延期、阻塞、质量和资源风险预警 | M1-M2 | `docs/90-reports/project-platform-s19-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S19-M4 | 空间治理、配置健康、审计报表和 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s19-m4-execution-report.md` | Completed |

## 5. 详细任务

### PROJECT-PLATFORM-S19-M1 指标语义层、维度和时间窗口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M1-T01 | 审计 S11-S18 权限、查询、视图、计划、资源、自动化和跨空间公共事实 | API、owner、schema、版本、时间语义、预算、复用能力和禁止依赖可定位；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S19-M1-T02 | 冻结 MetricDefinition、MetricVersion、Measure、Dimension、Window 与 MetricResult 合同 | identity、schema version、单位、分子/分母、维度、窗口、时区、freshness、错误和上限明确 | Done |
| PROJECT-PLATFORM-S19-M1-T03 | 设计指标定义、不可变版本、维度目录、命令回执和可重建结果索引 Flyway schema | 复合边界、唯一性、FK、索引、不可变历史、过期、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S19-M1-T04 | 实现指标草稿、校验、发布、修订、停用、归档和精确重放 | 已发布版本不可变；expected version、request hash、audit/outbox 与精确回放完整 | Done |
| PROJECT-PLATFORM-S19-M1-T05 | 实现注册 measure/dimension、日历、时区、滚动/固定窗口和比较区间 | 窗口边界、DST、空样本、迟到事实和版本切换可复现；禁止隐式时区或分母 | Done |
| PROJECT-PLATFORM-S19-M1-T06 | 实现受限指标表达式、类型/单位检查和确定性计算计划 | 只允许注册运算；未知字段、私表、SQL、脚本、非确定函数和未来 schema 失败关闭 | Done |
| PROJECT-PLATFORM-S19-M1-T07 | 接入逐来源权限、数据范围、最小样本、抑制、缺失与截断语义 | 隐藏对象不进入任何聚合外形；unknown/suppressed/stale/truncated 不折算为零 | Done |
| PROJECT-PLATFORM-S19-M1-T08 | 交付指标目录、语义编辑、窗口预览、版本 diff 和来源解释 Web | 1440/1366/820、键盘、长名称、loading、空态/错误、单位/窗口/来源可理解 | Done |
| PROJECT-PLATFORM-S19-M1-T09 | 接入 realtime 失效、离线草稿、多标签冲突和 REST 校准 | 缓存不授权；恢复不重复发布，旧指标结果/维度/数量不闪现 | Done |
| PROJECT-PLATFORM-S19-M1-T10 | 完成六身份、跨空间、并发、DST、空样本、收权、截断和重放测试 | 无对象/成员/维度/分母/数量泄漏、重复版本、错误归零或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S19-M1-T11 | 执行定义/维度/窗口、查询计划、SQL/端口/内存和渲染预算 | 固定夹具与上界可复现；不声明生产数据规模、指标延迟或 SLO | Done |
| PROJECT-PLATFORM-S19-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明指标语义事实；图表、风险预警和管理驾驶舱仍由 M2-M4 交付 | Done |

### PROJECT-PLATFORM-S19-M2 图表、看板和跨空间数据源

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M2-T01 | 复核 M1 语义层、S13 查询、S14 视图、S18 grant/全景和未关闭阻断 | 12 项逐项可追溯；图表不建立第二套指标、权限或跨空间授权权威 | Done |
| PROJECT-PLATFORM-S19-M2-T02 | 冻结 DataSourceBinding、ChartDefinition、Dashboard、Layout、Filter 与 QueryResult 合同 | identity、版本、来源、指标、维度、可视化、过滤、截断、错误和上限明确 | Done |
| PROJECT-PLATFORM-S19-M2-T03 | 设计数据源绑定、图表/看板版本、个人偏好、回执和可重建缓存 Flyway schema | 只保存配置、identity/version 与低基数派生；复合边界、过期、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S19-M2-T04 | 实现 permission-scoped 数据源解析和有界查询计划 | 只调用 owner 公共合同；逐来源授权、窗口和指标版本固定，无私表 join 或全量后过滤 | Done |
| PROJECT-PLATFORM-S19-M2-T05 | 实现单空间/跨空间聚合、去重、分子分母和来源对账 | active grant、当前 data scope、最小样本与截断一致；收权后结果立即失败关闭 | Done |
| PROJECT-PLATFORM-S19-M2-T06 | 实现表格、指标卡、折线、柱状、堆叠与分布图的来源解释和安全钻取 | 图例、facet、tooltip、游标、空态和 drilldown 不泄漏隐藏对象或数量 | Done |
| PROJECT-PLATFORM-S19-M2-T07 | 实现看板草稿、发布、布局、过滤、个人偏好、分享和精确回放 | 发布版本不可变；分享只引用受权配置，不复制数据或扩大接收者权限 | Done |
| PROJECT-PLATFORM-S19-M2-T08 | 交付图表设计器、跨空间数据源和响应式管理看板 Web | 1440/1366/820、键盘、长标签、loading、空态/错误、freshness/truncation 可用 | Done |
| PROJECT-PLATFORM-S19-M2-T09 | 接入 realtime、离线布局、多标签冲突、收权和 REST 校准 | 离线不伪造保存/分享；恢复不重复写入，旧 series/facet/count 不闪现 | Done |
| PROJECT-PLATFORM-S19-M2-T10 | 完成六身份、单/跨空间、并发、分享、撤销、截断和钻取测试 | 无标题/成员/维度/series/数量泄漏、错分母、重复数据或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S19-M2-T11 | 执行数据源/图表/看板、聚合、缓存、查询端口和 DOM 预算 | 固定夹具与上界可复现；不声明生产看板并发、刷新延迟或 SLO | Done |
| PROJECT-PLATFORM-S19-M2-T12 | 同步语义/数据源/图表/看板合同并完成 M2 checkpoint | 当前事实与 M3 风险输入清楚；图表结果不成为权限或风险结论权威 | Done |

### PROJECT-PLATFORM-S19-M3 延期、阻塞、质量和资源风险预警

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M3-T01 | 复核 M1-M2 指标/看板、计划/流程/关系/资源公共合同和未关闭阻断 | 24 项逐项可追溯；风险信号不复制 WorkItem、流程、资源、权限或指标事实 | Done |
| PROJECT-PLATFORM-S19-M3-T02 | 冻结 RiskPolicy、PolicyVersion、RiskSignal、EvidenceReference、Ack 与 Closure 合同 | identity、版本、严重度、窗口、来源、解释、生命周期、错误和上限明确 | Done |
| PROJECT-PLATFORM-S19-M3-T03 | 设计风险策略、不可变版本、信号、证据引用、回执和可重建统计 Flyway schema | 复合边界、唯一性、去重、索引、历史、过期、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S19-M3-T04 | 实现计划/里程碑/日历驱动的延期和临期风险 | 日期、基线、窗口、时区和当前权限可解释；无日期/收权/归档后不伪造正常 | Done |
| PROJECT-PLATFORM-S19-M3-T05 | 实现流程停滞、关系阻塞和依赖传播风险 | 只读公共状态/关系快照；环、隐藏端点、链深和扇出有界且不泄漏路径 | Done |
| PROJECT-PLATFORM-S19-M3-T06 | 实现质量与资源风险的受限证据组合 | 只组合已注册指标及当前缺陷/评审/产能公共事实；禁止个人绩效和隐式利用率评分 | Done |
| PROJECT-PLATFORM-S19-M3-T07 | 实现策略发布、信号去重、冷却、确认、关闭、抑制、重开和失效 | 每个动作绑定策略/来源版本与 receipt；关闭不改写来源，证据变化可重算 | Done |
| PROJECT-PLATFORM-S19-M3-T08 | 交付风险策略、信号列表、证据解释、确认/关闭和深链 Web | 1440/1366/820、键盘、长解释、loading、空态/错误和来源版本可理解 | Done |
| PROJECT-PLATFORM-S19-M3-T09 | 接入 realtime、离线输入、多标签冲突、收权和 REST 校准 | 离线不伪造关闭；恢复不重复动作，旧风险/严重度/数量不闪现 | Done |
| PROJECT-PLATFORM-S19-M3-T10 | 完成六身份、跨空间、并发、环、迟到事实、收权、关闭和重放测试 | 无对象/成员/路径/证据/数量泄漏、重复信号、幽灵关闭或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S19-M3-T11 | 执行策略/信号/证据、评估端口、链深/扇出、worker 和渲染预算 | 固定夹具与上界可复现；不声明生产风险覆盖、预测准确率或 SLO | Done |
| PROJECT-PLATFORM-S19-M3-T12 | 同步风险策略/信号/证据/事件合同并完成 M3 checkpoint | 当前事实与 M4 治理输入清楚；预警不成为来源事实或人员评价 | Done |

### PROJECT-PLATFORM-S19-M4 空间治理、配置健康、审计报表和 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Done |
| PROJECT-PLATFORM-S19-M4-T02 | 冻结 GovernanceOverview、ConfigHealth、AuditReport、ReportRun、Export 与 Diagnostic 合同 | 来源、窗口、排序、解释、截断、版本、权限、保留、错误和上限明确 | Done |
| PROJECT-PLATFORM-S19-M4-T03 | 设计治理偏好、报表定义/运行、导出回执和可重建健康统计 Flyway schema | 只保存治理配置/identity/version/低基数派生；索引、过期、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S19-M4-T04 | 聚合空间生命周期、成员治理、配置、权限、自动化和跨空间公共元数据 | 只调用 owner 公共合同；无内容私表 join、全量后过滤、个人排名或企业内容旁路 | Done |
| PROJECT-PLATFORM-S19-M4-T05 | 实现配置完整性、版本漂移、过期策略、失败运行和审计覆盖健康 | 每个结论绑定 source identity/version；unknown/stale/truncated 显式且不伪造 healthy | Done |
| PROJECT-PLATFORM-S19-M4-T06 | 实现审计报表、过滤、保留、脱敏、导出和来源校验 | 导出逐行重校准当前权限；隐藏内容不进入字段、数量、文件名、facet 或错误外形 | Done |
| PROJECT-PLATFORM-S19-M4-T07 | 实现治理报表草稿/发布/运行、受控确认/关闭和精确回放 | 危险动作需理由、当前权限、expected version、request hash、receipt 与审计 | Done |
| PROJECT-PLATFORM-S19-M4-T08 | 交付完整管理驾驶舱、配置健康、风险、审计报表和导出响应式 Web | 1440/1366/820、长名称、键盘、loading、空态/错误、来源/freshness 可用 | Done |
| PROJECT-PLATFORM-S19-M4-T09 | 完成六身份、单/跨空间、并发、收权、离线和组合规模预算验收 | 固定夹具与 SQL/端口/内存/DOM 上界可复现；无空间/成员/配置/审计泄漏 | Done |
| PROJECT-PLATFORM-S19-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；单/跨空间、六身份、关键视口和 route-final 证据 fresh | Done |
| PROJECT-PLATFORM-S19-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S20 准入 | 文档只声明已实现事实；S20 模板只能消费已发布公共能力 | Done |
| PROJECT-PLATFORM-S19-M4-T12 | 给出 S19 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、48 Task、工作上下文和文档一致；仅无阻断时 Completed | Done |

## 6. Stage 验收

- 指标定义、不可变版本、measure/dimension/window/timezone、单位、分子分母、freshness 和来源拥有唯一可重复语义。
- 单空间/跨空间数据源、图表和看板逐来源执行当前权限与最小样本/截断规则；无标题、成员、维度、series 或数量泄漏。
- 延期、阻塞、质量和资源风险绑定策略/来源版本，证据可解释，确认/关闭/抑制/重开可追溯且不改写来源。
- 管理驾驶舱只展示治理元数据、配置健康、风险和审计覆盖，不提供企业内容旁路、个人排名或绩效评分。
- 未知、缺失、过期、截断、抑制和无样本显式表达，不折算为零、正常或成功。
- 幂等、乐观锁、realtime、离线、多标签、长名称、1440/1366/820、单/跨空间和六身份闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 `route-final` 无阻断。
- S19 不实现 S20 场景模板或 S21 旧模型退出，也不把本地预算表述为生产吞吐、指标延迟、预测准确率或 SLO。

## 7. 起始点

S18 完成路线已归档，S19 在 Program revision 45 激活，并已于 revision 46 完成 M1-M4、48 个 Task、V131-V134 与真实隔离 route-final。当前 Stage 为 `none`；下一步只能通过独立 archive-only 工作循环归档 S19 并激活 S20。
