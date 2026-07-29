---
title: PROJECT-PLATFORM-S21 旧模型退出、全量验证和真实团队试用当前执行路线
status: active
route: PROJECT-PLATFORM-S21
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 49
stage: PROJECT-PLATFORM-S21
stage_final_milestone: PROJECT-PLATFORM-S21-M5
last_code_check: 2026-07-29
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S21 旧模型退出、全量验证和真实团队试用

## 1. Stage 目标

在 S01-S20 的空间、动态配置、规范 WorkItem、流程、关系、权限、查询、计划、资源、自动化、跨空间、治理和四类场景模板全部完成并归档的基础上，审计存量迁移与活动 legacy 使用，删除旧 `issues` 产品 API、DTO、页面和写路径，完成性能、安全、备份恢复与 route-final，并由研发、市场、HR、交付真实参与者完成规定任务，最终给出产品 Go/No-Go。

S21 是退出与验证 Stage，不建立新的业务元模型。旧表和不可变迁移/审计证据是否保留必须由 M1 数据审计与 M2 删除评审决定；禁止先删数据、绕过备份恢复、用本地合成证据冒充生产切流批准，或用 AI/自动化浏览器冒充 M4 真人参与者。

## 2. 固定输入与当前事实

- S20 完成路线已归档；当前 schema 为 V139。S21-M1 的不可变审计基线与 S21-M2 的旧产品合同退出均已完成，四类场景继续由规范 WorkItem 承载。
- S07 已交付 migration batch/unit/map/failure、canonical-only write、兼容 resolver、受控 cutover 和可恢复迁移；当前代码仍存在 `/projects`、`/issues`、legacy DTO/API、平台对象、搜索、工作台和跨模块 legacy 引用，M1 必须先形成完整 inventory。
- 生产 canonical write cutover 仍需目标环境画像、备份恢复、观测窗口、容量证据和批准；本地 PostgreSQL/Testcontainers rehearsal 不自动授权生产切流。
- 活动兼容最晚在 S21 删除；历史 map、批次、provenance、verification、audit 和 outbox 不得因删除产品入口而丢失可追溯性。
- M4 必须由真实研发、市场、HR、交付参与者执行规定任务并保存原始反馈、身份边界、环境、时间和 consent；自动化只用于准备环境和复验，不替代人工结论。

## 3. 不做范围

- 不恢复 legacy 写，不建立永久双读双写或新的兼容层。
- 不把旧 `projects`/`issues` 私表读取迁入其他模块，不以视图、别名或 adapter 隐藏残留依赖。
- 不在未完成 inventory、迁移校验、备份恢复和 rollback 决策前物理删除历史数据。
- 不把合成性能数据描述为生产容量、SLO 或真实团队采用结论。
- 不在 M1/M2 提前宣告产品 Go；不在 M3 用工程门禁替代 M4 真人试用。
- 不提前实现 Program 之外的新业务功能或 PLATFORM-SCALE Deferred 工作。

## 4. Milestone

| Milestone | 交付目标 | 依赖 | 报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M1 | 存量迁移完成度与数据一致性审计 | S20 归档；Program revision 49 | `docs/90-reports/project-platform-s21-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M2 | 删除旧 issues 产品 API、DTO、页面和写路径 | M1 Go 与删除清单 | `docs/90-reports/project-platform-s21-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M3 | 性能、安全、备份、恢复和 route-final | M2 完成；保留/删除边界冻结 | `docs/90-reports/project-platform-s21-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M4 | 研发、市场、HR、交付真人任务试用 | M3 工程 Go；试用参与者与环境就绪 | `docs/90-reports/project-platform-s21-m4-execution-report.md` | Active |
| PROJECT-PLATFORM-S21-M5 | 缺陷修复、复验和产品 Go/No-Go | M4 原始反馈与缺陷分级 | `docs/90-reports/project-platform-s21-m5-execution-report.md` | Pending |

## 5. Task

### PROJECT-PLATFORM-S21-M1 存量迁移完成度与数据一致性审计

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M1-T01 | 审计 S01-S20 迁移合同、当前代码、schema、运行角色和未关闭风险 | owner、API、表、map、batch、cutover、resolver、审计和禁止边界可定位；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S21-M1-T02 | 冻结 LegacyUsage、MigrationCoverage、ConsistencyFinding、RemovalDecision 与 EvidenceSnapshot 合同 | identity、来源、严重度、状态、证据、决定、保留、错误和上限明确 | Done |
| PROJECT-PLATFORM-S21-M1-T03 | 建立代码/API/路由/事件/平台对象/数据库 legacy 使用 inventory | 全仓引用按 owner、读/写、用户可见性和删除阶段分类；无未知活动调用方 | Done |
| PROJECT-PLATFORM-S21-M1-T04 | 建立 project/issue 到 space/work-item map、batch、unit 和 provenance 完整性审计 | 未映射、重复、碰撞、悬空、跨 workspace/space 和状态矛盾逐项可解释 | Done |
| PROJECT-PLATFORM-S21-M1-T05 | 实现源/目标 count、checksum、字段、参与者、评论、附件、关系和活动对账 | 每个迁移单元可重复校验；未知、缺失和截断不折算为一致 | Done |
| PROJECT-PLATFORM-S21-M1-T06 | 实现 legacy/canonical 权限、可见性、deep link、搜索和平台对象对照 | 六身份结果一致或有显式拒绝决定；map、错误和时序不泄漏存在性 | Done |
| PROJECT-PLATFORM-S21-M1-T07 | 审计所有 legacy 写入口、fallback、kill switch 和运行时流量 | canonical-only write 可证明；未知写、双写和不可解释 fallback 为阻断 | Done |
| PROJECT-PLATFORM-S21-M1-T08 | 交付迁移完成度、legacy 使用、finding 和 removal decision 治理视图 | 1440/1366/820、键盘、长名称、loading、空态/错误、来源与 freshness 可用 | Done |
| PROJECT-PLATFORM-S21-M1-T09 | 接入可重复快照、导出、审计、离线和权限重校准 | 证据不可变且逐次受权；离线不确认决定，隐藏对象不进入数量或导出 | Done |
| PROJECT-PLATFORM-S21-M1-T10 | 完成空库、升级库、混合迁移、碰撞、失败、收权和重放测试 | 无漏报、重复 finding、错误一致、权限偏差或 enterprise 内容旁路 | Done |
| PROJECT-PLATFORM-S21-M1-T11 | 执行 inventory、对账、查询、内存、导出和审计预算 | 固定夹具与上界可复现；不声明生产数据已完成迁移或允许删除 | Done |
| PROJECT-PLATFORM-S21-M1-T12 | 同步迁移审计合同，给出 M2 删除 Go/Reopen 并完成 checkpoint | 未迁移、重复、悬空和权限偏差为零或有逐项决定；删除清单冻结 | Done |

### PROJECT-PLATFORM-S21-M2 删除旧 issues 产品 API、DTO、页面和写路径

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M2-T01 | 复核 M1 inventory、removal decision、备份前置和阻断 finding | 12 项及删除清单逐项可追溯；未决定依赖 Reopen，不扩大删除范围 | Done |
| PROJECT-PLATFORM-S21-M2-T02 | 冻结删除顺序、兼容终止 reason、历史保留、回滚点和不可逆门禁 | API、DTO、路由、事件、对象、搜索、表和证据的先后关系明确 | Done |
| PROJECT-PLATFORM-S21-M2-T03 | 删除 legacy issue 写 Controller/service/repository 与跨模块写入口 | `/issues` 不再创建、修改、流转、评论或附件；无双写或隐藏 adapter | Done |
| PROJECT-PLATFORM-S21-M2-T04 | 删除 legacy issue 用户读 API、DTO 和项目聚合读取 | 用户事项只走规范 WorkItem；旧读不再访问 `issues` 私表或返回旧 DTO | Done |
| PROJECT-PLATFORM-S21-M2-T05 | 删除旧 issue 页面、路由、API client、缓存和 realtime reconciliation | Web 无活动 `/issues` 产品页面/query key；旧深链只按冻结决定终止或规范跳转 | Done |
| PROJECT-PLATFORM-S21-M2-T06 | 迁移搜索、通知、工作台、消息转事项和平台对象消费者 | 所有活动消费者使用 `work_item` 公共合同；无 legacy 私表、事件或 objectType 写入 | Done |
| PROJECT-PLATFORM-S21-M2-T07 | 删除旧 project/issue 固定 DTO、状态枚举和模块间类型泄漏 | 新业务模型不 import legacy domain DTO；允许的历史证据类型有显式清单 | Done |
| PROJECT-PLATFORM-S21-M2-T08 | 收紧 schema/table owner 与架构门禁，禁止活动 legacy 依赖回归 | ArchUnit、owner manifest、SQL/route/event 扫描同时阻断新旧入口恢复 | Done |
| PROJECT-PLATFORM-S21-M2-T09 | 执行历史表保留/只读/删除 migration 与恢复锚点 | 只按 M1 决定处理；migration/map/provenance/verification/audit 证据不可丢失 | Done |
| PROJECT-PLATFORM-S21-M2-T10 | 完成六身份、深链、搜索、通知、消息转换、附件和收权回归 | 无 404/410 外形泄漏、幽灵缓存、重复对象、断链或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S21-M2-T11 | 全仓反查 legacy API/DTO/页面/写路径与运行时 SQL | 活动残留为零；允许历史符号逐项登记 owner、原因、只读性和删除时点 | Done |
| PROJECT-PLATFORM-S21-M2-T12 | 同步当前/目标架构和迁移矩阵，完成 M2 checkpoint | 旧 issues 产品合同退出；只保留批准的不可变历史迁移与审计证据 | Done |

### PROJECT-PLATFORM-S21-M3 性能、安全、备份、恢复和 route-final

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M3-T01 | 复核 M1-M2 证据、当前运行拓扑、数据模型和未关闭阻断 | 24 项逐项可追溯；性能/恢复不掩盖删除或权限缺陷 | Done |
| PROJECT-PLATFORM-S21-M3-T02 | 冻结容量场景、安全矩阵、备份集、恢复点、RTO/RPO 测量和失败判定 | 环境、数据、身份、操作、采样、预算、误差和非生产声明明确 | Done |
| PROJECT-PLATFORM-S21-M3-T03 | 建立规范 WorkItem 与四类场景的确定性规模数据和负载协议 | seed 可重放、无真实隐私；读写/查询/流程/关系/自动化/指标组合可解释 | Done |
| PROJECT-PLATFORM-S21-M3-T04 | 执行 API、数据库、Worker、realtime、Web 和组合资源预算 | 固定环境无未解释退化、泄漏或无界增长；结果不冒充生产 SLO | Done |
| PROJECT-PLATFORM-S21-M3-T05 | 执行认证、授权、最小披露、注入、SSRF、上传下载和敏感数据安全复核 | P0/P1 安全发现为零；六身份与收权逐入口失败关闭 | Done |
| PROJECT-PLATFORM-S21-M3-T06 | 实现并演练数据库、对象存储、Redis 可重建状态和配置备份 | 备份集、版本、加密、校验、保留、审计和恢复依赖完整 | Done |
| PROJECT-PLATFORM-S21-M3-T07 | 在隔离环境执行全量恢复、迁移重放、索引重建和一致性校验 | V001-V137+ 可恢复；规范事实、附件、map、audit/outbox 和配置 hash 一致 | Done |
| PROJECT-PLATFORM-S21-M3-T08 | 演练中断、部分失败、只读、回退和灾后重新开放 | fencing、幂等、顺序、丢失窗口和人工决定可解释；不恢复 legacy 写 | Done |
| PROJECT-PLATFORM-S21-M3-T09 | 交付运行/恢复状态、证据索引和受控运维入口 | 当前角色、理由、审批、审计和最小披露完整；浏览器不直接持有运维凭据 | Done |
| PROJECT-PLATFORM-S21-M3-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离 route-final | full gate 无阻断；关键用户/系统闭环和恢复证据 fresh | Done |
| PROJECT-PLATFORM-S21-M3-T11 | 对账工程门禁、容量、安全和恢复结果并登记残余风险 | 结论区分本地/预生产/生产；未知项不伪装通过或转为无关 gap | Done |
| PROJECT-PLATFORM-S21-M3-T12 | 给出 M4 工程 Go/Reopen 并完成 checkpoint | 只有 P0/P1 清零且试用环境可恢复时 Go；工程结论不替代真人结论 | Done |

### PROJECT-PLATFORM-S21-M4 研发、市场、HR、交付真人任务试用

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M4-T01 | 复核 M3 工程 Go、试用环境、参与者、数据边界和未关闭风险 | 工程证据可追溯；参与者、角色、场景和停止条件明确 | Pending |
| PROJECT-PLATFORM-S21-M4-T02 | 冻结试用协议、任务脚本、观察项、原始反馈、consent 和缺陷分级合同 | 研发/市场/HR/交付口径一致；不诱导答案、不采集无关隐私 | Pending |
| PROJECT-PLATFORM-S21-M4-T03 | 准备隔离试用 workspace、四类场景、身份和可恢复基线 | 数据合成且可重置；每位参与者只获完成任务所需最小权限 | Pending |
| PROJECT-PLATFORM-S21-M4-T04 | 由真实研发参与者完成项目、需求、任务、缺陷、版本和迭代闭环 | 规定任务独立完成；原始成功/失败、时间、困惑和建议留证 | Pending |
| PROJECT-PLATFORM-S21-M4-T05 | 由真实市场参与者完成活动、内容、素材、渠道、投放和复盘闭环 | 规定任务独立完成；文件/渠道/审批边界和原始反馈留证 | Pending |
| PROJECT-PLATFORM-S21-M4-T06 | 由真实 HR 参与者完成招聘计划、职位、候选人、面试和入职闭环 | 规定任务独立完成；敏感字段和角色最小披露经真人确认 | Pending |
| PROJECT-PLATFORM-S21-M4-T07 | 由真实交付参与者完成项目、任务、风险、交付物、评审和验收闭环 | 规定任务独立完成；文件/验收/风险边界和原始反馈留证 | Pending |
| PROJECT-PLATFORM-S21-M4-T08 | 执行跨角色交接、收权、离线、恢复、深链和通知真人任务 | 用户可理解失败与恢复；无旧页面、隐藏内容或陈旧成功状态 | Pending |
| PROJECT-PLATFORM-S21-M4-T09 | 汇总完成率、关键路径、错误、帮助请求和原始定性反馈 | 原始记录与汇总分离；不做个人绩效、隐式评分或小样本外推 | Pending |
| PROJECT-PLATFORM-S21-M4-T10 | 对反馈去重、复现并映射到 owner、严重度、影响身份和证据 | 每项 finding 可复现或明确无法复现；P0/P1 立即停止相关试用 | Pending |
| PROJECT-PLATFORM-S21-M4-T11 | 执行试用后数据清理、权限撤销、证据保留和环境恢复 | 无参与者残留权限或隐私数据；批准证据不可变且访问受控 | Pending |
| PROJECT-PLATFORM-S21-M4-T12 | 给出四场景人工验收结论并完成 checkpoint | 四类均有真实参与者原始证据；自动化与人工结论明确分离 | Pending |

### PROJECT-PLATFORM-S21-M5 缺陷修复、复验和产品 Go/No-Go

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M5-T01 | 审计 M1-M4 报告、工程证据、真人反馈、缺陷和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化 | Pending |
| PROJECT-PLATFORM-S21-M5-T02 | 冻结缺陷 owner、优先级、修复批次、复验范围和 Go/No-Go 判定 | P0/P1/P2/P3、工程/产品、人/自动化证据和停止条件明确 | Pending |
| PROJECT-PLATFORM-S21-M5-T03 | 修复迁移一致性、legacy 残留、权限或数据安全缺陷 | 不恢复旧合同或扩大权限；每项修复绑定根因、测试和回归面 | Pending |
| PROJECT-PLATFORM-S21-M5-T04 | 修复四场景任务、可用性、响应式、离线和恢复缺陷 | 只改验收阻断行为；不借收口扩展新产品范围 | Pending |
| PROJECT-PLATFORM-S21-M5-T05 | 修复性能、可靠性、备份恢复和运维证据缺陷 | 预算与恢复对账通过；不以增大超时、关闭门禁或删除证据掩盖问题 | Pending |
| PROJECT-PLATFORM-S21-M5-T06 | 为每项 P0/P1/P2 建立自动化回归与防回归门禁 | P0/P1 全覆盖；legacy route/SQL/event/objectType 禁止项机器可校验 | Pending |
| PROJECT-PLATFORM-S21-M5-T07 | 在干净隔离环境重跑迁移对账、完整工程和恢复 route-final | 全量门禁 fresh 且无阻断；历史报告不替代当前修复证据 | Pending |
| PROJECT-PLATFORM-S21-M5-T08 | 邀请原问题场景真实参与者完成定向复验 | 原始反馈确认修复或明确仍阻断；AI/维护者不得代签人工结论 | Pending |
| PROJECT-PLATFORM-S21-M5-T09 | 完成四场景、六身份、关键视口、离线、收权和组合规模最终验收 | 无 legacy 产品入口、数据/权限泄漏、幽灵缓存或未解释退化 | Pending |
| PROJECT-PLATFORM-S21-M5-T10 | 对账缺陷、风险、工程与人工证据，冻结发布候选 | P0/P1 为零；未关闭 P2/P3 有 owner、影响、决定和跟踪来源 | Pending |
| PROJECT-PLATFORM-S21-M5-T11 | 同步当前架构、Program、专项索引、运行手册和最终产品边界 | 文档只声明已实现及真人验证事实；生产批准与本地证据明确分离 | Pending |
| PROJECT-PLATFORM-S21-M5-T12 | 给出 S21 产品 Go/No-Go、完成 route-final 并把当前 Stage 置 none | 五份报告、60 Task、工程/人工结论和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 存量 project/issue 迁移覆盖、数据一致性、权限对照和活动 legacy 使用可完整解释，未知项不被隐藏。
- 旧 `issues` 产品 API、DTO、页面和写路径退出；只保留经批准的不可变历史迁移与审计证据。
- 性能、安全、备份、恢复和完整 route-final 无 P0/P1 阻断，环境与生产声明边界明确。
- 研发、市场、HR、交付四类真实参与者完成规定任务并保存原始反馈；自动化不冒充人工验收。
- 所有 P0/P1 清零，P2/P3 有明确决定；工程与人工证据分别可复核。
- 产品 Go/No-Go 由完成的 60 个 Task 和新鲜证据支持，不以计划、合成样本或口头结论替代。

## 7. 当前入口

S20 已完成并归档，Program revision 49 激活 S21。M1-M3 已分别完成独立工作循环；当前停在 `PROJECT-PLATFORM-S21-M4` 真人试用准备点。只有真实参与者、consent、隔离环境与主持时间窗就绪后，才可从 `PROJECT-PLATFORM-S21-M4-T01` 启动新的工作循环；不得用 AI、自动化浏览器或维护者代签 M4 证据，也不得提前执行 M5、归档 S21 或激活 S22。
