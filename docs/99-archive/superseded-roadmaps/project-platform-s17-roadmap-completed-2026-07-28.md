---
title: PROJECT-PLATFORM-S17 自动化规则、通知和开放连接器当前执行路线
status: completed
archived_at: 2026-07-28
route: PROJECT-PLATFORM-S17
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 42
stage: PROJECT-PLATFORM-S17
stage_final_milestone: PROJECT-PLATFORM-S17-M5
last_code_check: 2026-07-28
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S17 自动化规则、通知和开放连接器

## 1. Stage 目标

在 S16 工作日历、估分、工时、产能和人员排期完成并归档的基础上，交付版本化自动化规则、事件目录、触发器-条件-操作运行时、受控内置操作、可恢复时间触发、Webhook/连接器以及管理 UI、运行历史和限额。S17 只拥有规则定义、执行计划、执行记录、连接器配置引用和投递状态；WorkItem、流程、关系、权限、通知、成员、资源、审计、事件和外部凭据仍由既有 owner 持有。

所有规则触发和操作都必须在执行时重新校准 S11 当前 decision/data scope，并通过 S08/S09/S10/S12/S16 公共命令或事件合同协作。事件、缓存、运行历史和连接器不得形成授权快照；不得执行任意脚本、复制 owner 私表、在错误或日志中泄漏敏感内容，也不得提前实现 S18 跨空间同步或 S19 组织级度量。

## 2. 固定输入与当前事实

- S16 完成路线已归档；当前 schema 为 V121，日历/估分、工时/修订、人员分配/产能和资源排期均已交付。
- S03 可靠事件基线提供版本化 envelope、outbox、handler registry、幂等投递、重试、退避、死信和重放合同；S17 不建立第二套通用事件总线。
- S07 canonical WorkItem runtime 是事项唯一权威；自动化只能使用稳定 identity 和公共 resolver/command，不复制标题、字段或状态事实。
- S08/S09 状态流和节点流分别拥有规范动作与执行历史；自动化流转只能调用公共 command SPI，不读写其私表。
- S10 关系是 canonical edge/hierarchy 权威；创建关联项或关系必须通过规范关系命令，不直接落边。
- S11 snapshot v5 decision/data scope 在规则测试、执行、历史、通知、Webhook payload 和错误响应中均失败关闭；enterprise-admin 不自动获得私有内容。
- S12 notification owner 持有偏好、投递和失效语义；自动化通知操作只提交受控请求，不复制通知事实。
- S13 查询 DSL/保存视图、S14 日期、S15 Plan/Milestone 和 S16 日历/资源可作为触发条件输入，但均不成为自动化权威。
- 凭据、审计、event、notification、realtime 和文件只通过公共合同使用；日志和错误不得包含 token、secret、完整 payload 或隐藏对象。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份仍是最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. AutomationRule、RuleVersion、Trigger、Condition、Action、Run、Step、Connector 和 Delivery 使用版本化合同、稳定 identity、显式生命周期和 workspace/space 复合边界。
3. 规则发布前完成 schema、事件引用、字段/动作 capability、权限和循环风险校验；已发布版本不可变，运行绑定确切版本。
4. 消费 S03 可靠事件合同；触发匹配、执行 claim、step 副作用和 receipt 分层幂等，重放不得重复业务动作或通知。
5. 每个操作执行前重新校准当前 actor/rule authority 与目标 data scope；收权、停用、归档或删除后失败关闭且最小披露。
6. 内置操作仅通过 canonical command/event SPI 更新字段、流转、创建关联项和发送通知；禁止任意代码、SQL、模板执行或私表写入。
7. 时间触发明确 timezone、DST、cursor、lease、fencing、去重、错过窗口、追赶、节流和通知冷却；恢复不得重复轰炸。
8. Webhook/连接器使用凭据引用、签名、重放保护、目标 allow policy、DNS/IP 校验、超时、限流、退避、死信和显式重放；敏感值不入库明文、不进日志。
9. realtime 只失效规则、运行和投递查询，online/focus/reconnect 后 REST 校准；离线输入保留但不伪造发布、测试或重放成功。
10. M1-M4 使用影响范围门禁；M5 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M1 | 事件目录和触发器-条件-操作模型 | S16 归档；Program revision 41 | `docs/90-reports/project-platform-s17-m1-execution-report.md` | Done |
| PROJECT-PLATFORM-S17-M2 | 字段更新、流转、创建关联项和通知操作 | M1 | `docs/90-reports/project-platform-s17-m2-execution-report.md` | Done |
| PROJECT-PLATFORM-S17-M3 | 定时、临期、到期、超期和停留时间触发 | M1-M2 | `docs/90-reports/project-platform-s17-m3-execution-report.md` | Done |
| PROJECT-PLATFORM-S17-M4 | Webhook、连接器、重试和死信 | M1-M3 | `docs/90-reports/project-platform-s17-m4-execution-report.md` | Done |
| PROJECT-PLATFORM-S17-M5 | 自动化管理 UI、运行历史、限额与 Stage 收口 | M1-M4 | `docs/90-reports/project-platform-s17-m5-execution-report.md` | Done |

## 5. 详细任务

### PROJECT-PLATFORM-S17-M1 事件目录和触发器-条件-操作模型

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M1-T01 | 审计 S03 事件、S07-S10 命令、S11 权限、S12 通知及 S16 资源事实 | API、事件、表、owner、调用方、迁移、预算、复用能力和禁止依赖可定位；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S17-M1-T02 | 冻结 AutomationRule、RuleVersion、Trigger、Condition、Action 与 EventCatalog 合同 | schema version、identity、引用、生命周期、错误、解释和上限明确 | Done |
| PROJECT-PLATFORM-S17-M1-T03 | 设计规则、不可变版本、事件目录、命令回执和低基数索引 Flyway schema | workspace/space 复合边界、唯一性、FK、索引、清理与 owner 完整 | Done |
| PROJECT-PLATFORM-S17-M1-T04 | 实现规则草稿、校验、发布、启停、修订、归档和精确重放 | 发布版本不可变；expected version、request hash、audit/outbox 完整 | Done |
| PROJECT-PLATFORM-S17-M1-T05 | 建立内置事件目录、版本兼容和稳定字段引用 | 只消费公共 event envelope；未知版本/字段失败关闭，不读取 producer 私表 | Done |
| PROJECT-PLATFORM-S17-M1-T06 | 实现声明式条件树、类型校验、短路和有界解释 | 禁止任意脚本/SQL；深度、节点、值大小、字段权限和错误最小化明确 | Done |
| PROJECT-PLATFORM-S17-M1-T07 | 建立受控操作目录和 canonical command/event SPI | capability、输入 schema、权限前置、幂等和副作用分类可查询 | Done |
| PROJECT-PLATFORM-S17-M1-T08 | 交付规则草稿、触发器、条件和操作基础 Web 编辑器 | 1440/1366/820、键盘、长名称、loading、空态/错误和 capability 来源可理解 | Done |
| PROJECT-PLATFORM-S17-M1-T09 | 接入 realtime 失效、离线草稿、多标签冲突和 REST 校准 | 断线不丢草稿；恢复不重复发布，收权后旧引用不闪现 | Done |
| PROJECT-PLATFORM-S17-M1-T10 | 完成六身份、跨空间、并发、归档、未知事件和非法条件测试 | 无规则/字段/对象/数量泄漏、重复版本或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S17-M1-T11 | 执行规则数、版本数、条件深度、目录端口和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产自动化吞吐或 SLO | Done |
| PROJECT-PLATFORM-S17-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明规则模型事实；业务操作、时间触发和连接器仍由 M2-M4 交付 | Done |

### PROJECT-PLATFORM-S17-M2 字段更新、流转、创建关联项和通知操作

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M2-T01 | 复核 M1 规则模型、事件目录、权限、迁移和未关闭阻断 | 12 项逐项可追溯；操作不复制 WorkItem、流程、关系或通知权威 | Done |
| PROJECT-PLATFORM-S17-M2-T02 | 冻结 AutomationRun、RunStep、ActionReceipt、ExecutionContext 和失败合同 | identity、actor、版本、输入指纹、状态、错误、保留和上限明确 | Done |
| PROJECT-PLATFORM-S17-M2-T03 | 设计运行、步骤、claim/lease、回执、失败和可重建统计 Flyway schema | 复合边界、唯一性、fencing、索引、不可变历史、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S17-M2-T04 | 实现事件触发匹配、run claim、step 编排和分层精确重放 | 重复 delivery/run/step 不重复副作用；崩溃后可由 lease/fencing 恢复 | Done |
| PROJECT-PLATFORM-S17-M2-T05 | 实现受控字段更新操作 | 调用 canonical WorkItem command；当前 snapshot/capability/字段权限失败关闭 | Done |
| PROJECT-PLATFORM-S17-M2-T06 | 实现状态流/节点流动作 | 调用 S08/S09 公共 command；守卫、参与者、expected version 和结果可追溯 | Done |
| PROJECT-PLATFORM-S17-M2-T07 | 实现创建关联项、规范关系和通知操作 | 创建/关系/通知各走 owner 公共合同；部分失败不留下幽灵边或重复通知 | Done |
| PROJECT-PLATFORM-S17-M2-T08 | 交付规则测试、执行预览、单步结果和错误解释 Web | 测试不产生业务副作用；真实执行与模拟显式区分，隐藏输入不披露 | Done |
| PROJECT-PLATFORM-S17-M2-T09 | 接入重试、取消、收权、offline/reconnect 和 REST 校准 | 缓存不授权；执行中收权安全终止，恢复不重复业务命令 | Done |
| PROJECT-PLATFORM-S17-M2-T10 | 完成六身份、跨空间、并发、重复事件、崩溃和部分失败测试 | 无字段/状态/关系/通知泄漏、重复副作用或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S17-M2-T11 | 执行每事件匹配数、run/step 数、命令端口和 worker 预算 | 固定夹具与上界可复现；不冒充生产吞吐、延迟或容量承诺 | Done |
| PROJECT-PLATFORM-S17-M2-T12 | 同步运行/步骤/操作/事件/审计合同并完成 M2 checkpoint | 当前事实与 M3 调度输入清楚；不提前声明时间触发或外部连接器 | Done |

### PROJECT-PLATFORM-S17-M3 定时、临期、到期、超期和停留时间触发

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M3-T01 | 复核 M1-M2 规则/运行、S14 日期、S15 里程碑、S16 日历和未关闭阻断 | 24 项逐项可追溯；调度不复制日期、流程、资源或权限权威 | Done |
| PROJECT-PLATFORM-S17-M3-T02 | 冻结 ScheduleTrigger、DueWindow、DwellWindow、ScheduleCursor 与 FireReceipt 合同 | timezone、窗口、cursor、错过策略、解释、错误、保留和上限明确 | Done |
| PROJECT-PLATFORM-S17-M3-T03 | 设计调度游标、lease/fencing、触发回执和低基数统计 Flyway schema | 复合边界、唯一性、索引、过期、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S17-M3-T04 | 实现 cron/固定时间定时触发与 timezone/DST 解释 | 重复/缺失本地时间、跨日和时钟回拨确定；同一窗口最多触发一次 | Done |
| PROJECT-PLATFORM-S17-M3-T05 | 实现临期、到期和超期触发 | 只读取 owner 当前日期公共合同；日期变更/归档/收权后失效且不轰炸 | Done |
| PROJECT-PLATFORM-S17-M3-T06 | 实现状态/节点停留时间触发 | 只消费 canonical transition/node history 公共投影；回退/重开语义明确 | Done |
| PROJECT-PLATFORM-S17-M3-T07 | 实现多实例调度 claim、追赶、节流、冷却和故障恢复 | lease/fencing 防双触发；错过窗口有界追赶，通知和操作有稳定去重 | Done |
| PROJECT-PLATFORM-S17-M3-T08 | 交付时间触发编辑、下次运行、错过策略和诊断 Web | 1440/1366/820、键盘、时区/DST、loading、空态/错误可理解 | Done |
| PROJECT-PLATFORM-S17-M3-T09 | 接入 realtime、离线草稿、多标签冲突、收权和 REST 校准 | 旧时间/标题/运行数不闪现，不伪造触发或取消成功 | Done |
| PROJECT-PLATFORM-S17-M3-T10 | 完成六身份、跨空间、DST、时钟回拨、双实例、崩溃和恢复测试 | 无对象/日期/停留/数量泄漏、双触发、重复通知或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S17-M3-T11 | 执行规则数、候选对象数、窗口、claim、追赶和渲染预算 | 固定夹具和上界可复现；不声明生产调度容量或 SLO | Done |
| PROJECT-PLATFORM-S17-M3-T12 | 同步调度/时间/恢复/通知合同并完成 M3 checkpoint | 当前事实与 M4 外部投递输入清楚；不提前实现任意连接器 | Done |

### PROJECT-PLATFORM-S17-M4 Webhook、连接器、重试和死信

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M4-T01 | 复核 M1-M3 规则/运行/调度、平台网络与凭据边界及未关闭阻断 | 36 项逐项可追溯；连接器不复制凭据、授权或业务对象权威 | Done |
| PROJECT-PLATFORM-S17-M4-T02 | 冻结 ConnectorDefinition、WebhookEndpoint、Delivery、Attempt、DeadLetter 合同 | identity、目标、签名、payload、状态、错误、保留和上限明确 | Done |
| PROJECT-PLATFORM-S17-M4-T03 | 设计连接器元数据、凭据引用、投递、尝试、死信和回执 Flyway schema | 不存凭据明文；复合边界、唯一性、索引、不可变尝试、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S17-M4-T04 | 实现 Webhook 目标策略、DNS/IP 校验、超时、限流和重定向约束 | 防 SSRF、内网/元数据访问和 DNS rebinding；每次连接前后均校验 | Done |
| PROJECT-PLATFORM-S17-M4-T05 | 实现版本化 payload 模板、字段 allowlist、签名和重放保护 | 无任意模板代码；隐藏/敏感字段省略，签名/时间戳/nonce 可验证 | Done |
| PROJECT-PLATFORM-S17-M4-T06 | 实现连接器凭据引用、轮换、撤销和最小日志 | secret owner 管理真实值；应用只持引用，日志/错误/audit 均脱敏 | Done |
| PROJECT-PLATFORM-S17-M4-T07 | 实现投递 claim、分类重试、退避、死信、重放和放弃 | fencing 防双投；永久/瞬时错误稳定，历史保留且重放不重复成功副作用 | Done |
| PROJECT-PLATFORM-S17-M4-T08 | 交付连接器配置、测试投递、尝试历史和死信管理 Web | 1440/1366/820、键盘、长 URL、loading、空态/错误和权限来源可理解 | Done |
| PROJECT-PLATFORM-S17-M4-T09 | 接入受权 payload、realtime、离线输入、多标签冲突和 REST 校准 | 缓存不授权；收权后旧 payload/目标/错误不闪现，测试与真实投递区分 | Done |
| PROJECT-PLATFORM-S17-M4-T10 | 完成六身份、跨空间、SSRF、签名、超时、双实例、重试和重放测试 | 无 secret/payload/标题/目标泄漏、重复投递或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S17-M4-T11 | 执行 connector/投递/尝试数、payload、并发、网络和渲染预算 | 固定夹具与上界可复现；不声明生产外部服务可用性或 SLO | Done |
| PROJECT-PLATFORM-S17-M4-T12 | 同步连接器/安全/运维/事件合同并完成 M4 checkpoint | 当前事实与 M5 管理收口输入清楚；S18 跨空间同步仍未实现 | Done |

### PROJECT-PLATFORM-S17-M5 自动化管理 UI、运行历史、限额与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M5-T01 | 审计 M1-M4 实现、报告、迁移、边界和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Done |
| PROJECT-PLATFORM-S17-M5-T02 | 冻结 AutomationManagement、RunHistory、Quota、Health 与 Diagnostic 合同 | 来源、窗口、排序、解释、截断、版本、错误、权限和上限明确 | Done |
| PROJECT-PLATFORM-S17-M5-T03 | 设计管理偏好、可重建统计、限额状态和治理回执 Flyway schema | 只保存低基数派生/identity/version；复合边界、索引、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S17-M5-T04 | 聚合规则、运行、步骤、调度、连接器和投递的当前受权最小事实 | 只调用 owner 公共合同；无私表 join、重复事实、全量后过滤或跨空间旁路 | Done |
| PROJECT-PLATFORM-S17-M5-T05 | 实现规则列表、运行历史、步骤诊断、过滤和深链 | 每个状态/错误有来源和版本；缺失、截断、收权和归档显式降级 | Done |
| PROJECT-PLATFORM-S17-M5-T06 | 实现空间/规则/actor/action 限额、节流、暂停和恢复 | 限额在 claim 前后执行；不丢历史、不绕过权限，恢复不会形成突发轰炸 | Done |
| PROJECT-PLATFORM-S17-M5-T07 | 实现受控测试、启停、重放、放弃和治理审计 | 测试默认无副作用；危险动作需理由、当前权限、expected version 和 receipt | Done |
| PROJECT-PLATFORM-S17-M5-T08 | 交付完整自动化管理、运行历史、诊断、限额和响应式 Web | 1440/1366/820、长名称、键盘、loading、空态/错误和来源可用 | Done |
| PROJECT-PLATFORM-S17-M5-T09 | 完成六身份、跨空间、并发、收权、离线和组合规模预算验收 | 固定夹具与 SQL/端口/内存/DOM 上界可复现；无规则/运行/错误泄漏 | Done |
| PROJECT-PLATFORM-S17-M5-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；关键视口、六身份和 route-final 证据 fresh | Done |
| PROJECT-PLATFORM-S17-M5-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S18 准入 | 文档只声明已实现事实；S18 继续复用 S11/S17 权限、幂等和连接器边界 | Done |
| PROJECT-PLATFORM-S17-M5-T12 | 给出 S17 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 五份报告、60 Task、工作上下文和文档一致；仅无阻断时 Completed | Done |

## 6. Stage 验收

- 规则定义、不可变版本、事件目录、运行/步骤、调度、连接器和投递拥有明确唯一权威，并与业务对象、权限、通知、凭据和通用事件基线分离。
- 触发器-条件-操作只消费版本化公共合同；任意脚本/SQL/私表访问被禁止，所有业务副作用经 canonical command/event SPI。
- 事件、时间和外部投递具备分层幂等、lease/fencing、重试、退避、死信、重放、限额、审计和最小披露。
- Webhook 具备凭据引用、签名、重放保护、SSRF 防护、超时、限流、脱敏和失败诊断。
- 幂等、乐观锁、realtime、离线、多标签、长名称、1440/1366/820 和六身份闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 `route-final` 无阻断。
- S17 不实现 S18 跨空间同步或 S19 组织级管理度量，也不把本地预算表述为生产吞吐、容量、外部可用性或 SLO。

## 7. 起始点

S17 五个 Milestone、60 个 Task、V122-V126 与真实隔离 route-final 已完成，Go 结论成立，Program `current_stage` 已置 `none`。下一步只能进入独立 archive-only 工作循环，归档本路线后再激活 S18；S17 的管理聚合、限额和连接器不构成跨空间授权或同步事实。
