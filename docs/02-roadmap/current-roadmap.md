---
title: PROJECT-PLATFORM-S21 旧模型退出、项目空间易用性收敛、全量验证和真实团队试用当前执行路线
status: active
route: PROJECT-PLATFORM-S21
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 52
stage: PROJECT-PLATFORM-S21
stage_final_milestone: PROJECT-PLATFORM-S21-M10
last_code_check: 2026-07-31
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S21 旧模型退出、项目空间易用性收敛、全量验证和真实团队试用

## 1. Stage 目标

在 S01-S20 的空间、动态配置、规范 WorkItem、流程、关系、权限、查询、计划、资源、自动化、跨空间、治理和四类场景模板全部完成并归档的基础上，审计存量迁移与活动 legacy 使用，删除旧 `issues` 产品 API、DTO、页面和写路径，完成性能、安全、备份恢复与工程 route-final；在真人试用前收敛项目空间的信息架构、角色化简洁模式、渐进式配置、首次使用引导和兼容迁移，再由研发、市场、HR、交付真实参与者完成规定任务，最终给出产品 Go/No-Go。

S21 是退出、易用性收敛与验证 Stage，不建立新的业务元模型。简洁/高级模式和“成员工作区、项目管理、空间管理”任务分区只重组入口和解释，不成为授权依据，也不复制工作项、流程、权限、计划、资源、自动化或指标权威。旧表和不可变迁移/审计证据是否保留必须由 M1 数据审计与 M2 删除评审决定；禁止先删数据、绕过备份恢复、用本地合成证据冒充生产切流批准，或用 AI/自动化浏览器冒充 M9 真人参与者。

## 2. 固定输入与当前事实

- S20 的 5 个 Milestone、60 个 Task 已全部完成并归档，不存在待后移的 S20 Task；四类场景模板作为 M6 引导、M7 回归和 M9 真人试用输入，不重开 S20。
- 当前 schema 为 V141：V139 只作为旧 `issues` 产品合同退出的历史锚点，V140 承载项目空间体验偏好，V141 承载首次使用引导状态。revision 51 两轮重开已依次关闭完整配置发布/作者态水合阻断，以及概览误把 `legacy partial` 类型展示为可创建并暴露英文运行时错误的体验阻断。公开类型摘要现按同一运行时完整性门禁投影 `configurationReady`；未就绪类型在入口、筛选、拆分子项和创建弹窗中失败关闭，管理者获得配置路径，普通成员只获得最小说明。M7-T10 至 T12 已再次完成并给出当时的 Engineering Go。
- 2026-07-31 项目空间管理者走查进一步确认：当前界面仍把高频执行、项目管理和低频配置混在同层；管理员默认落入配置概览，普通成员仍缺少稳定的“我的工作”主线，工作项当前行动被状态流和技术标识遮蔽。revision 52 因此在真人试用前新增 M8 角色分层 UX 收敛，原真人试用和最终 Go/No-Go 分别顺延为 M9/M10；M7 历史工程证据保持有效，但不能替代新 M8 的产品体验门禁。
- S07 交付的 migration batch/unit/map/failure、canonical-only write、兼容 resolver、受控 cutover 和可恢复迁移已作为 S21 历史输入；M1 已完成完整 inventory 与数据对账，M2 已退出 `/projects`、旧 Issue 产品 DTO/API/页面/搜索/写路径及活动 `project`/`issue` 注册。
- 生产 canonical write cutover 仍需目标环境画像、备份恢复、观测窗口、容量证据和批准；本地 PostgreSQL/Testcontainers rehearsal 不自动授权生产切流。
- 活动旧产品兼容已在 S21-M2 退出；只保留经批准的历史 map、批次、provenance、verification、audit、outbox 和恢复证据，不得丢失可追溯性，也不得借恢复或 rollout 重新激活旧产品合同。
- revision 49 的 M4 真人试用准备包和 revision 51 的 M8 准备材料只作为历史输入，不是人工结论；revision 52 将真人试用顺延为 M9，并要求按角色分层改造后的界面重新冻结协议与任务脚本。
- M8 已完成角色分层 UX 收敛并给出工程 UX Go：三类任务分区、五条 canonical route、成员工作区、项目管理、空间管理、成员视图预览、中文最小披露与兼容深链均由 fresh 自动化和真实隔离浏览器证据关闭。M9 仍为 Pending；启动 M9 Task 还必须落实 5 位真人参与者、知情同意（consent）、隔离试用环境和真人主持，自动化只用于准备环境和复验，不替代人工执行或结论。

## 3. 不做范围

- 不恢复 legacy 写，不建立永久双读双写或新的兼容层。
- 不把旧 `projects`/`issues` 私表读取迁入其他模块，不以视图、别名或 adapter 隐藏残留依赖。
- 不在未完成 inventory、迁移校验、备份恢复和 rollback 决策前物理删除历史数据。
- 不把合成性能数据描述为生产容量、SLO 或真实团队采用结论。
- 不在 M1/M2 提前宣告产品 Go；不在 M3/M7/M8 用工程门禁替代 M9 真人试用。
- 不因重排导航删除现有受支持配置路由、收藏或深链；不以 CSS 隐藏、客户端角色名、模式偏好、引导状态、缓存或遥测代替服务端权限。
- 不把三类任务分区实现为三个互不兼容的新产品，不删除“概览、工作项、项目管理、成员、设置”五条 canonical route，不让成员视图预览、模式切换或企业治理身份扩大空间授权。
- 不提前实现 Program 之外的新业务功能或 PLATFORM-SCALE Deferred 工作。

## 4. Milestone

| Milestone | 交付目标 | 依赖 | 报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M1 | 存量迁移完成度与数据一致性审计 | S20 归档；Program revision 49 | `docs/90-reports/project-platform-s21-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M2 | 删除旧 issues 产品 API、DTO、页面和写路径 | M1 Go 与删除清单 | `docs/90-reports/project-platform-s21-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M3 | 性能、安全、备份、恢复和 route-final | M2 完成；保留/删除边界冻结 | `docs/90-reports/project-platform-s21-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M4 | 信息架构、角色视图与术语收敛 | M1-M3 完成；Program revision 50 | `docs/90-reports/project-platform-s21-m4-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M5 | 简洁模式与角色化项目空间 | M4 信息架构与兼容合同 | `docs/90-reports/project-platform-s21-m5-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M6 | 渐进式配置与首次使用引导 | M5 简洁 Shell；S20 四类模板 | `docs/90-reports/project-platform-s21-m6-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M7 | 兼容迁移、安全、可观测与工程复验 | M4-M6 完成 | `docs/90-reports/project-platform-s21-m7-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M8 | 项目空间角色分层 UX 收敛 | M4-M7 合同与工程证据；2026-07-31 管理者走查 | `docs/90-reports/project-platform-s21-m8-execution-report.md` | Completed |
| PROJECT-PLATFORM-S21-M9 | 研发、市场、HR、交付真人任务试用 | M8 UX Go；5 位真人参与者、知情同意、隔离环境与真人主持就绪 | `docs/90-reports/project-platform-s21-m9-execution-report.md` | Pending |
| PROJECT-PLATFORM-S21-M10 | 缺陷修复、复验和产品 Go/No-Go | M9 原始反馈与缺陷分级 | `docs/90-reports/project-platform-s21-m10-execution-report.md` | Pending |

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

### PROJECT-PLATFORM-S21-M4 信息架构、角色视图与术语收敛

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M4-T01 | 审计当前一级导航、二级 Tab、路由、面板、角色、API 和测试依赖 | surface inventory 覆盖所有入口、深链、owner、身份和现有行为；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S21-M4-T02 | 冻结 owner/admin、自定义管理角色、member、guest、non-member 和 enterprise-admin 任务能力矩阵 | 高频任务、受权动作、只读与拒绝边界逐项可验证；不只按角色名称判断 | Done |
| PROJECT-PLATFORM-S21-M4-T03 | 按高频执行、项目管理、低频配置和企业治理四层分类现有内容 | 每个内容块有唯一主入口和必要上下文入口；不按现有组件位置反推产品结构 | Done |
| PROJECT-PLATFORM-S21-M4-T04 | 冻结“概览、工作项、项目管理、成员、设置”五入口与高级配置分组 | 默认顺序、能力驱动显隐、空态、只读态和无权限降级规则明确 | Done |
| PROJECT-PLATFORM-S21-M4-T05 | 建立项目空间统一术语表与界面文案迁移表 | 空间、任务模板/工作项类型、字段、表单与页面、流程、权限和发布配置无同义冲突 | Done |
| PROJECT-PLATFORM-S21-M4-T06 | 冻结简洁/高级模式的默认值、可切换身份、偏好作用域、离线与回退合同 | 简洁模式默认启用；模式只影响呈现，不保存或扩大授权 | Done |
| PROJECT-PLATFORM-S21-M4-T07 | 冻结角色化导航与服务端 capability 映射合同 | 入口与动作由当前 `availableActions`/权限事实决定；客户端配置不创建权限 | Done |
| PROJECT-PLATFORM-S21-M4-T08 | 建立现有配置路由与深链兼容矩阵 | `typeId`、`create`、`savedViewId`、`panel`、工作项详情及收藏/通知链接均有保留或显式迁移决定 | Done |
| PROJECT-PLATFORM-S21-M4-T09 | 设计上下文入口、面包屑、返回路径和高级配置解释 | 从执行页进入设置后可返回原任务；用户能理解何时需要高级配置 | Done |
| PROJECT-PLATFORM-S21-M4-T10 | 冻结 1440/1366/820、键盘、焦点、屏幕阅读器、长名称和导航规范 | 三视口无横向溢出或不可达操作；焦点顺序与语义可验证 | Done |
| PROJECT-PLATFORM-S21-M4-T11 | 用可验证原型/静态合同走查四类场景并完成路由、权限和最小披露威胁建模 | 研发、市场、HR、交付关键路径可达；隐藏入口、预加载和错误外形不泄漏 | Done |
| PROJECT-PLATFORM-S21-M4-T12 | 同步目标/当前架构、术语与兼容合同，给出 M5 Go/Reopen 并完成 checkpoint | 12 项证据齐备；未决定路由、权限或信息架构冲突必须 Reopen | Done |

### PROJECT-PLATFORM-S21-M5 简洁模式与角色化项目空间

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M5-T01 | 复核 M4 信息架构、角色矩阵、兼容合同和未关闭 finding | 12 项逐项可追溯；阻断项 Reopen，不以实现细节改写合同 | Done |
| PROJECT-PLATFORM-S21-M5-T02 | 建立集中式项目空间导航注册表 | 顺序、分组、模式、capability、路由和响应式元数据只有一个权威来源 | Done |
| PROJECT-PLATFORM-S21-M5-T03 | 实现路由到导航上下文的唯一解析器 | 旧配置深链可直达并落入“设置/高级配置”上下文；无重定向循环 | Done |
| PROJECT-PLATFORM-S21-M5-T04 | 实现概览、工作项、项目管理、成员、设置五入口简洁 Shell | 默认只展示当前身份可用入口；无权内容不加载、不计数、不预取 | Done |
| PROJECT-PLATFORM-S21-M5-T05 | 建立项目管理聚合页 | 计划/里程碑、风险/决策、交付/验收、资源/排期和受权指标归位且不复制 owner 事实 | Done |
| PROJECT-PLATFORM-S21-M5-T06 | 建立设置中心与高级配置分组 | 工作模型、流程与权限、自动化与协同、度量治理、场景模板可定位；执行视图与配置视图分离 | Done |
| PROJECT-PLATFORM-S21-M5-T07 | 实现用户+空间维度的模式偏好、恢复默认和跨设备/离线降级 | 偏好版本化且不保存授权快照；读取失败安全回到简洁默认 | Done |
| PROJECT-PLATFORM-S21-M5-T08 | 实现按身份、capability 和空间状态确定的默认落点 | owner/member/guest/归档或停用空间均有可解释落点、空态和下一步 | Done |
| PROJECT-PLATFORM-S21-M5-T09 | 保持模式切换、刷新、前进后退和旧深链的用户上下文 | 查询条件、未提交草稿、滚动与返回位置不被静默丢弃 | Done |
| PROJECT-PLATFORM-S21-M5-T10 | 接入 realtime、online/focus、多标签与离线恢复 | 事件只触发 REST 校准；隐藏页面与离线状态不产生陈旧成功 | Done |
| PROJECT-PLATFORM-S21-M5-T11 | 补齐导航、路由、偏好和六身份自动化测试 | 直达 URL、缓存、预加载和模式切换均不能越权；禁止仅靠 CSS 隐藏 | Done |
| PROJECT-PLATFORM-S21-M5-T12 | 完成真实隔离浏览器验收并完成 checkpoint | 三视口、键盘、长名称、深链、离线和多标签通过；否则 Reopen | Done |

### PROJECT-PLATFORM-S21-M6 渐进式配置与首次使用引导

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M6-T01 | 复核 M4-M5 证据、S20 四类模板合同和当前配置发布依赖 | 新引导只消费现有公共合同；不重开 S20 或建立第二配置权威 | Done |
| PROJECT-PLATFORM-S21-M6-T02 | 冻结 OnboardingState、Checklist、Dismissal、Version 和 Reset 合同 | 状态按 workspace/space/user 隔离、可升级可重置且不授权 | Done |
| PROJECT-PLATFORM-S21-M6-T03 | 实现首次进入的研发、市场、HR、交付模板或空白空间选择 | 选择前展示影响与权限；选择不等于已安装或已发布 | Done |
| PROJECT-PLATFORM-S21-M6-T04 | 实现管理者首次设置向导 | 模板预览、安装确认、成员、首个工作项和首次交接形成可恢复路径 | Done |
| PROJECT-PLATFORM-S21-M6-T05 | 实现成员首次任务引导 | 找到工作、新建/更新、评论、附件、状态推进和通知确认均可按 capability 到达 | Done |
| PROJECT-PLATFORM-S21-M6-T06 | 按依赖呈现任务模板、字段、表单、流程、权限、发布和增强能力 | 基础路径不要求理解完整元模型；高级能力按需展开 | Done |
| PROJECT-PLATFORM-S21-M6-T07 | 实现受控配置向导编排 | 只调用既有 owner 公共 API；不读写其他模块私表、不绕过 expected version/receipt | Done |
| PROJECT-PLATFORM-S21-M6-T08 | 在发布前展示草稿、校验、影响、冲突与回退 | 保存草稿不描述为生效；发布/回滚结果逐次从服务端确认 | Done |
| PROJECT-PLATFORM-S21-M6-T09 | 增加上下文帮助、术语解释、示例和“何时需要”说明 | 默认使用业务语言；工程内部 key/schema 只在必要诊断中最小披露 | Done |
| PROJECT-PLATFORM-S21-M6-T10 | 为权限不足、对象隐藏、空间只读和离线提供安全解释与求助路径 | 错误可行动且不泄漏对象存在性、标题、成员或策略细节 | Done |
| PROJECT-PLATFORM-S21-M6-T11 | 接入最小化引导遥测与退出控制 | 只记录步骤、结果、耗时区间和错误码；不记录正文、字段值、文件名或个人绩效 | Done |
| PROJECT-PLATFORM-S21-M6-T12 | 完成四模板引导 E2E、真实浏览器验收和 checkpoint | 新用户无需先理解完整配置体系即可完成首个闭环；阻断项 Reopen | Done |

### PROJECT-PLATFORM-S21-M7 兼容迁移、安全、可观测与工程复验

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M7-T01 | 审计 M4-M6、现有收藏/深链、路由测试、缓存键、偏好和安全 finding | 36 项与所有兼容面可追溯；未知调用方或未决安全项为阻断 | Done |
| PROJECT-PLATFORM-S21-M7-T02 | 冻结路由、导航偏好、引导状态和缓存 schema 的版本化迁移/回滚合同 | 版本、幂等、失败、回退、保留期和删除边界明确 | Done |
| PROJECT-PLATFORM-S21-M7-T03 | 实现旧路由适配与规范导航上下文 | 受支持查询参数和目标对象不丢失；无循环、开放重定向或权限外形泄漏 | Done |
| PROJECT-PLATFORM-S21-M7-T04 | 幂等迁移本地/服务端偏好与缓存 | 失败回到简洁默认且不删除业务配置、草稿、收藏或审计 | Done |
| PROJECT-PLATFORM-S21-M7-T05 | 建立 workspace/space/user 分阶段开放、kill switch 和恢复演练 | 开关不授权；关闭后可恢复旧导航上下文且数据无损 | Done |
| PROJECT-PLATFORM-S21-M7-T06 | 执行六身份权限和最小披露复验 | 模式切换、直达 URL、预加载、缓存和错误均不能绕过 S11 当前 decision | Done |
| PROJECT-PLATFORM-S21-M7-T07 | 复核高级配置 mutation 的 manager gate、确认、审计、幂等与回执 | 所有写逐次服务端校准；enterprise-admin 非成员无内容旁路 | Done |
| PROJECT-PLATFORM-S21-M7-T08 | 建立匿名化入口、模式、帮助、任务结果、路由错误与恢复观测 | 无正文/身份绩效数据；unknown、sample limit 和 freshness 显式表达 | Done |
| PROJECT-PLATFORM-S21-M7-T09 | 完成懒加载、请求合并、缓存失效、bundle 和三视口性能预算 | 隐藏面板不全量预取；固定夹具下无未解释退化或无界增长 | Done |
| PROJECT-PLATFORM-S21-M7-T10 | 回归 S20 四类模板的安装、差异、升级、首项和配置发布 | 四类场景在新入口下可完成；只复验公共合同，不改写 S20 历史 | Done |
| PROJECT-PLATFORM-S21-M7-T11 | 执行完整单测、集成、六身份 E2E、安全、离线、响应式、兼容路由与干净隔离 route-final | 全量门禁 fresh 且无 P0/P1；本地结果不冒充生产批准 | Done |
| PROJECT-PLATFORM-S21-M7-T12 | 对账迁移、安全、性能与可观测证据，给出 M8 Engineering Go/Reopen | 只有 P0/P1 清零、回退可用和试用环境可恢复时 Go | Done |

### PROJECT-PLATFORM-S21-M8 项目空间角色分层 UX 收敛

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M8-T01 | 复核 M4-M7 信息架构、capability、canonical route、深链、六身份和 2026-07-31 管理者走查 finding | 现状路径、角色任务、重复入口、P0/P1/P2、owner 和证据边界完整；不把审计图或旧会话当实现事实 | Done |
| PROJECT-PLATFORM-S21-M8-T02 | 冻结“成员工作区、项目管理、空间管理”三类任务分区与五条 canonical route 的兼容映射 | 成员工作区承接概览/工作项，项目管理保持唯一管理聚合，空间管理承接成员/设置；`typeId/create/savedViewId/panel/source` 可恢复且无循环或上下文丢失 | Done |
| PROJECT-PLATFORM-S21-M8-T03 | 冻结成员内容判定、默认落点和服务端 capability 合同 | 导航、按钮、可创建类型和详情动作统一取 `availableActions`；成员内容满足 capability、配置就绪和当前参与范围，不按角色名、模式或客户端缓存授权 | Done |
| PROJECT-PLATFORM-S21-M8-T04 | 消除默认未发布类型、不可用创建、无动作空态和详情行动埋藏等 P0 死路 | 只展示已发布且运行时完整的类型；成员最多两次点击到首个可执行事项，详情首屏给出当前状态、负责人、截止时间和下一步，不暴露 UUID/schema/hash/内部英文编码 | Done |
| PROJECT-PLATFORM-S21-M8-T05 | 建设成员工作区的空间首页、我的工作、工作项、视图和动态主线 | 普通成员默认只看到用途、重点、待办/参与/关注、可用工作项和协作动态；无任务时给出其有权执行的下一步，不进入配置后台 | Done |
| PROJECT-PLATFORM-S21-M8-T06 | 收敛项目管理为计划、里程碑、排期、容量、风险、决策、交付、验收和结果指标唯一主入口 | 项目管理按 capability 显示；工作项和概览只保留必要摘要或上下文跳转，每项管理事实只有一个可编辑 owner | Done |
| PROJECT-PLATFORM-S21-M8-T07 | 建设空间管理首页并集中成员、工作模型、流程权限、自动化、度量、模板和生命周期 | 配置健康、配置待办、最近入口和危险操作分区明确；管理员最多两层到达任一配置，未就绪类型只在配置待办出现 | Done |
| PROJECT-PLATFORM-S21-M8-T08 | 实现 Owner/受权管理员的成员视图预览和角色切换解释 | 预览准确反映目标身份可见入口且不挂载/预取隐藏内容；预览、模式切换和 enterprise-admin 身份不授权，退出后恢复原上下文 | Done |
| PROJECT-PLATFORM-S21-M8-T09 | 统一面向成员与管理员的术语、空态、错误、状态和技术信息最小披露 | 清理旧同义词、英文运行时错误和技术标识；权限不足、隐藏对象、只读、离线和未配置状态均有中文可行动解释且不泄漏存在性 | Done |
| PROJECT-PLATFORM-S21-M8-T10 | 优化空间创建、模板/克隆/预览、上下文帮助和首次起步路径 | 空白、模板、克隆的影响可预览；管理者按依赖完成配置、发布、成员、首项和交接，普通成员无需理解完整元模型 | Done |
| PROJECT-PLATFORM-S21-M8-T11 | 执行六类真实身份、四场景、三视口、键盘、焦点、离线、多标签、深链、缓存、最小披露和真实隔离 route-final，并补充 custom-role capability fixture | owner/admin/member/guest/non-member/enterprise-admin 六类真实身份边界正确；custom-role 由 capability-only fixture 证明前端不依赖角色名白名单；隐藏内容不加载，canonical 深链可恢复，P0/P1 自动化与真实浏览器证据 fresh | Done |
| PROJECT-PLATFORM-S21-M8-T12 | 对账 P0/P1/P2、兼容、安全、性能和可访问性证据，给出 M9 真人试用 Go/Reopen | P0/P1 为零；P2 已完成或有 owner/影响/决定，回退可用、环境可恢复；工程结论不替代真人结论 | Done |

### PROJECT-PLATFORM-S21-M9 研发、市场、HR、交付真人任务试用

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M9-T01 | 复核 M8 UX Go、隔离试用环境、5 位真人参与者、真人主持、数据边界和未关闭风险 | 工程证据可追溯；参与者、角色、主持人、场景和停止条件明确 | Pending |
| PROJECT-PLATFORM-S21-M9-T02 | 按角色分层后的界面重新冻结试用协议、任务脚本、观察项、原始反馈、知情同意（consent）和缺陷分级合同 | 研发/市场/HR/交付/管理者口径一致；不诱导答案、不采集无关隐私 | Pending |
| PROJECT-PLATFORM-S21-M9-T03 | 准备隔离试用 workspace、四类合成场景、最小身份、成员默认视图和可恢复基线 | 环境与日常/开发数据隔离，合成数据可重置；项目管理或空间管理只在脚本和 capability 要求时开放 | Pending |
| PROJECT-PLATFORM-S21-M9-T04 | 由真实研发参与者完成需求、任务、缺陷、版本和迭代闭环 | 规定任务独立完成；原始成功/失败、时间、困惑和建议留证 | Pending |
| PROJECT-PLATFORM-S21-M9-T05 | 由真实市场参与者完成活动、内容、素材、渠道、投放和复盘闭环 | 规定任务独立完成；文件/渠道/审批边界和原始反馈留证 | Pending |
| PROJECT-PLATFORM-S21-M9-T06 | 由真实 HR 参与者完成招聘计划、职位、候选人、面试和入职闭环 | 规定任务独立完成；敏感字段和角色最小披露经真人确认 | Pending |
| PROJECT-PLATFORM-S21-M9-T07 | 由真实交付参与者完成项目、任务、风险、交付物、评审和验收闭环 | 规定任务独立完成；文件/验收/风险边界和原始反馈留证 | Pending |
| PROJECT-PLATFORM-S21-M9-T08 | 由真实管理者完成模板安装、成员配置、必要空间管理、成员视图预览、交接、收权、深链和恢复 | 用户可理解三类任务分区与失败恢复；无旧页面、隐藏内容、权限扩大或陈旧成功 | Pending |
| PROJECT-PLATFORM-S21-M9-T09 | 汇总完成率、时间区间、关键路径、帮助请求、迷失点、视图区切换和原始定性反馈 | 原始记录与汇总分离；不做个人绩效、隐式评分或小样本外推 | Pending |
| PROJECT-PLATFORM-S21-M9-T10 | 对反馈去重、复现并映射 owner、严重度、影响身份、路径和证据 | 每项 finding 可复现或说明限制；P0/P1 立即停止相关试用 | Pending |
| PROJECT-PLATFORM-S21-M9-T11 | 执行试用后数据清理、权限撤销、会话终止、证据保留和环境恢复 | 无参与者残留权限或隐私数据；批准证据不可变且访问受控 | Pending |
| PROJECT-PLATFORM-S21-M9-T12 | 给出四场景人工验收结论并完成 checkpoint | 四类均有真实参与者原始证据；AI、维护者和自动化不得代签 | Pending |

### PROJECT-PLATFORM-S21-M10 缺陷修复、复验和产品 Go/No-Go

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M10-T01 | 审计 M1-M9 报告、工程证据、真人反馈、迁移决定、缺陷和未关闭 gap | 108 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化 | Pending |
| PROJECT-PLATFORM-S21-M10-T02 | 冻结缺陷 owner、优先级、修复批次、复验范围和 Go/No-Go 判定 | P0/P1/P2/P3、工程/产品、人/自动化证据和停止条件明确 | Pending |
| PROJECT-PLATFORM-S21-M10-T03 | 修复迁移一致性、legacy 残留、权限、数据安全和最小披露缺陷 | 不恢复旧合同或扩大权限；每项修复绑定根因、测试和回归面 | Pending |
| PROJECT-PLATFORM-S21-M10-T04 | 修复导航、术语、引导、任务分区、四场景、响应式、离线和恢复缺陷 | 只改验收阻断行为；不借收口扩展新产品范围 | Pending |
| PROJECT-PLATFORM-S21-M10-T05 | 修复性能、可靠性、备份恢复和可观测证据缺陷 | 预算与恢复对账通过；不以增大超时、关闭门禁或删除证据掩盖问题 | Pending |
| PROJECT-PLATFORM-S21-M10-T06 | 为每项 P0/P1/P2 建立自动化回归与防回归门禁 | P0/P1 全覆盖；legacy、权限、路由、任务分区和模式禁止项机器可校验 | Pending |
| PROJECT-PLATFORM-S21-M10-T07 | 在干净隔离环境重跑迁移对账、完整工程、安全、恢复和兼容 route-final | 全量门禁 fresh 且无阻断；历史报告不替代当前修复证据 | Pending |
| PROJECT-PLATFORM-S21-M10-T08 | 邀请原问题场景真实参与者完成定向复验 | 原始反馈确认修复或明确仍阻断；AI/维护者不得代签人工结论 | Pending |
| PROJECT-PLATFORM-S21-M10-T09 | 完成四场景、六身份、三类任务分区、三视口、键盘、离线、收权和深链最终验收 | 无 legacy 入口、数据/权限泄漏、幽灵缓存或未解释退化 | Pending |
| PROJECT-PLATFORM-S21-M10-T10 | 对账缺陷、风险、工程与人工证据，冻结发布候选 | P0/P1 为零；未关闭 P2/P3 有 owner、影响、决定和跟踪来源 | Pending |
| PROJECT-PLATFORM-S21-M10-T11 | 同步架构、Program、专项索引、用户/管理员/运行手册和最终产品边界 | 文档只声明已实现及真人验证事实；生产批准与本地证据分离 | Pending |
| PROJECT-PLATFORM-S21-M10-T12 | 给出 S21 产品 Go/No-Go、完成 route-final 并把当前 Stage 置 none | 十份报告、120 Task、工程/人工结论和文档一致；仅无阻断时 Completed | Pending |

## 6. 未执行任务顺延映射

revision 49 的 S21-M4/M5 均未启动、全部为 Pending。revision 50 因首次易用性门禁将其分别顺延为 M8/M9；revision 52 又在真人试用前插入角色分层 UX 收敛，因此未执行的真人试用与最终收口再次整体顺延：

| revision 49 原范围 | revision 50 承接范围 | revision 52 当前承接范围 | 说明 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M4-T01 至 T12 | PROJECT-PLATFORM-S21-M8-T01 至 T12 | PROJECT-PLATFORM-S21-M9-T01 至 T12 | 四场景真人试用保持 12 项顺序，并改为按角色分层后的界面重新冻结协议 |
| PROJECT-PLATFORM-S21-M5-T01 至 T12 | PROJECT-PLATFORM-S21-M9-T01 至 T12 | PROJECT-PLATFORM-S21-M10-T01 至 T12 | 缺陷修复、真人复验和最终 Go/No-Go 保持 12 项顺序 |

`docs/90-reports/project-platform-s21-m4-human-trial-preparation.md` 与 `docs/90-reports/project-platform-s21-m8-human-trial-preparation.md` 分别保留为 revision 49 和 revision 51 的准备历史输入；M9-T02 必须创建 revision 52 的新协议与准备材料，不得把旧准备包当成人工结论或直接改名为 M9 执行报告。

## 7. Stage 验收

- 存量 project/issue 迁移覆盖、数据一致性、权限对照和活动 legacy 使用可完整解释，未知项不被隐藏。
- 旧 `issues` 产品 API、DTO、页面和写路径退出；只保留经批准的不可变历史迁移与审计证据。
- 性能、安全、备份、恢复和完整 route-final 无 P0/P1 阻断，环境与生产声明边界明确。
- 项目空间按成员工作区、项目管理、空间管理三类任务分区呈现，同时保留概览、工作项、项目管理、成员、设置五条 canonical route；普通成员无需理解完整元模型即可完成高频任务。
- 普通成员最多两次点击找到首个可执行事项，不加载或展示未发布配置、UUID、schema/hash 或内部英文编码；成员内容只来自当前 capability、配置就绪和参与范围。
- 项目管理只承载计划、资源、风险、交付和结果指标；空间管理按工作模型、流程与权限、自动化与协同、度量治理、场景模板和生命周期渐进展开，管理员最多两层到达任一配置能力。
- Owner 可预览普通成员视图，但预览、模式切换和 enterprise-admin 身份均不授权；隐藏内容不挂载、不预取，旧深链兼容可回退。
- 简洁/高级模式、偏好、引导、缓存、开关和遥测均不授权；owner/admin/member/guest/non-member/enterprise-admin 六类真实身份、custom-role capability fixture、最小披露、离线、三视口和键盘边界通过。
- 研发、市场、HR、交付四类真实参与者在 M9 完成规定任务并保存原始反馈；自动化不冒充人工验收。
- 所有 P0/P1 清零，P2/P3 有明确决定；工程与人工证据分别可复核。
- 产品 Go/No-Go 由完成的 120 个 Task 和新鲜证据支持，不以计划、合成样本或口头结论替代。

## 8. 当前入口

S20 已完成并归档；S21-M1 至 M7 已完成，当前 schema 为 V141。Program revision 51 的第二轮重开已让公开类型摘要与 `PublishedSnapshotAdapter.requireComplete` 使用同一配置就绪判断，并通过单次批量快照读取避免逐类型 N+1：概览“工作入口”区分“配置就绪/待发布配置”，管理者可直接进入配置，普通成员得到安全说明；工作项筛选、拆分子项与创建弹窗同步失败关闭，旧深链/陈旧缓存触发的 `unsupported_snapshot_schema` 只显示中文可行动提示，OK/Enter/提交函数共享同一就绪门禁。后端 readiness 集成 9/9、前端 lint/build、真实隔离“待配置→发布→配置就绪→上线关单”1/1 均通过，M7-T10 至 T12 再次 Done。

Program revision 52 新增的 S21-M8 角色分层 UX 收敛已完成：三类任务分区与五条 canonical route 保持兼容，普通成员只看到服务端 capability、配置就绪与当前参与范围允许的内容，项目管理与空间管理分别收口，Owner/受权管理员可在不授权、不加载隐藏内容的前提下预览成员/访客入口。前后端合同、43 项前端单测、31 项后端测试、lint/build 与真实隔离 route-final 均通过，M8-T01 至 T12 已 Done，并给出仅限工程 UX 的 M9 Go。下一候选入口是 `PROJECT-PLATFORM-S21-M9-T01`；M9 仍为 Pending，只有 5 位真人参与者、知情同意、隔离环境和真人主持全部落实后方可启动，不得提前执行 M10、归档 S21 或激活 S22。

2026-07-31 16:30 真人试用发现概览二级 `panel` 被主导航跨 surface 保留，触发 canonical owner 将 `/work-items` 等目标立即拉回概览；M8-T02 已按 Reopened 流程修复为跨主 surface/空间只保留 `source` 与安全 hash，并补充“空间动态 → 工作项”的真实点击回归。隔离复验同时发现未配置状态流程原先返回空白面板，已改为中文可行动提示；定向合同、lint/build、共享真实页面五入口与完整真实隔离 E2E fresh 通过后，M8-T02 恢复 Done，M8 恢复 Completed。

2026-07-31 18:58 真人试用发现“工作模型”的任务模板/字段与页面入口及“流程与权限”入口全部落到同一任务模板目录；M8-T07 已按 Reopened 流程修复为显式模板选择和任务模板、字段、表单与页面、发布配置、流程与权限五类有区分的落点。流程与权限使用同一配置草稿事实源并在异步加载后聚焦专属区域，不复制编辑器或业务数据；路由合同 2/2、frontend lint/build、共享动态 fixture 1/1 和独立数据库真实 E2E 1/1 fresh 通过后，M8-T07 恢复 Done，M8 恢复 Completed。
