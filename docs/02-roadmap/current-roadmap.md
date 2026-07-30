---
title: PROJECT-PLATFORM-S21 旧模型退出、项目空间易用性收敛、全量验证和真实团队试用当前执行路线
status: active
route: PROJECT-PLATFORM-S21
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 50
stage: PROJECT-PLATFORM-S21
stage_final_milestone: PROJECT-PLATFORM-S21-M9
last_code_check: 2026-07-30
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S21 旧模型退出、项目空间易用性收敛、全量验证和真实团队试用

## 1. Stage 目标

在 S01-S20 的空间、动态配置、规范 WorkItem、流程、关系、权限、查询、计划、资源、自动化、跨空间、治理和四类场景模板全部完成并归档的基础上，审计存量迁移与活动 legacy 使用，删除旧 `issues` 产品 API、DTO、页面和写路径，完成性能、安全、备份恢复与工程 route-final；在真人试用前收敛项目空间的信息架构、角色化简洁模式、渐进式配置、首次使用引导和兼容迁移，再由研发、市场、HR、交付真实参与者完成规定任务，最终给出产品 Go/No-Go。

S21 是退出、易用性收敛与验证 Stage，不建立新的业务元模型。简洁/高级模式只重组入口和解释，不成为授权依据，也不复制工作项、流程、权限、计划、资源、自动化或指标权威。旧表和不可变迁移/审计证据是否保留必须由 M1 数据审计与 M2 删除评审决定；禁止先删数据、绕过备份恢复、用本地合成证据冒充生产切流批准，或用 AI/自动化浏览器冒充 M8 真人参与者。

## 2. 固定输入与当前事实

- S20 的 5 个 Milestone、60 个 Task 已全部完成并归档，不存在待后移的 S20 Task；四类场景模板作为 M6 引导、M7 回归和 M8 真人试用输入，不重开 S20。
- 当前 schema 为 V141：V139 只作为旧 `issues` 产品合同退出的历史锚点，V140 承载项目空间体验偏好，V141 承载首次使用引导状态。S21-M1 至 M7 已完成；M8 尚未执行或签署。
- S07 已交付 migration batch/unit/map/failure、canonical-only write、兼容 resolver、受控 cutover 和可恢复迁移；当前代码仍存在 `/projects`、`/issues`、legacy DTO/API、平台对象、搜索、工作台和跨模块 legacy 引用，M1 必须先形成完整 inventory。
- 生产 canonical write cutover 仍需目标环境画像、备份恢复、观测窗口、容量证据和批准；本地 PostgreSQL/Testcontainers rehearsal 不自动授权生产切流。
- 活动兼容最晚在 S21 删除；历史 map、批次、provenance、verification、audit 和 outbox 不得因删除产品入口而丢失可追溯性。
- revision 49 的 M4 真人试用准备包只作为历史输入，不是人工结论；revision 50 将真人试用顺延为 M8，并要求按改造后的界面重新冻结协议与任务脚本。
- M8 当前只激活真人试用准备点，M8-T01 至 T12 仍全部为 Pending。启动任何 M8 Task 前必须落实 5 位真人参与者、知情同意（consent）、隔离试用环境和真人主持；自动化只用于准备环境和复验，不替代人工执行或结论。

## 3. 不做范围

- 不恢复 legacy 写，不建立永久双读双写或新的兼容层。
- 不把旧 `projects`/`issues` 私表读取迁入其他模块，不以视图、别名或 adapter 隐藏残留依赖。
- 不在未完成 inventory、迁移校验、备份恢复和 rollback 决策前物理删除历史数据。
- 不把合成性能数据描述为生产容量、SLO 或真实团队采用结论。
- 不在 M1/M2 提前宣告产品 Go；不在 M3/M7 用工程门禁替代 M8 真人试用。
- 不因重排导航删除现有受支持配置路由、收藏或深链；不以 CSS 隐藏、客户端角色名、模式偏好、引导状态、缓存或遥测代替服务端权限。
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
| PROJECT-PLATFORM-S21-M8 | 研发、市场、HR、交付真人任务试用 | M7 Engineering Go；5 位真人参与者、知情同意、隔离环境与真人主持就绪 | `docs/90-reports/project-platform-s21-m8-execution-report.md` | Active |
| PROJECT-PLATFORM-S21-M9 | 缺陷修复、复验和产品 Go/No-Go | M8 原始反馈与缺陷分级 | `docs/90-reports/project-platform-s21-m9-execution-report.md` | Pending |

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

### PROJECT-PLATFORM-S21-M8 研发、市场、HR、交付真人任务试用

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M8-T01 | 复核 M7 Engineering Go、隔离试用环境、5 位真人参与者、真人主持、数据边界和未关闭风险 | 工程证据可追溯；参与者、角色、主持人、场景和停止条件明确 | Pending |
| PROJECT-PLATFORM-S21-M8-T02 | 按新界面重新冻结试用协议、任务脚本、观察项、原始反馈、知情同意（consent）和缺陷分级合同 | 研发/市场/HR/交付/管理者口径一致；不诱导答案、不采集无关隐私 | Pending |
| PROJECT-PLATFORM-S21-M8-T03 | 准备隔离试用 workspace、四类合成场景、最小身份、简洁默认和可恢复基线 | 环境与日常/开发数据隔离，合成数据可重置；高级模式只在脚本要求时开放 | Pending |
| PROJECT-PLATFORM-S21-M8-T04 | 由真实研发参与者完成需求、任务、缺陷、版本和迭代闭环 | 规定任务独立完成；原始成功/失败、时间、困惑和建议留证 | Pending |
| PROJECT-PLATFORM-S21-M8-T05 | 由真实市场参与者完成活动、内容、素材、渠道、投放和复盘闭环 | 规定任务独立完成；文件/渠道/审批边界和原始反馈留证 | Pending |
| PROJECT-PLATFORM-S21-M8-T06 | 由真实 HR 参与者完成招聘计划、职位、候选人、面试和入职闭环 | 规定任务独立完成；敏感字段和角色最小披露经真人确认 | Pending |
| PROJECT-PLATFORM-S21-M8-T07 | 由真实交付参与者完成项目、任务、风险、交付物、评审和验收闭环 | 规定任务独立完成；文件/验收/风险边界和原始反馈留证 | Pending |
| PROJECT-PLATFORM-S21-M8-T08 | 由真实管理者完成模板安装、成员配置、必要高级设置、交接、收权、深链和恢复 | 用户可理解模式边界与失败恢复；无旧页面、隐藏内容或陈旧成功 | Pending |
| PROJECT-PLATFORM-S21-M8-T09 | 汇总完成率、时间区间、关键路径、帮助请求、迷失点、模式切换和原始定性反馈 | 原始记录与汇总分离；不做个人绩效、隐式评分或小样本外推 | Pending |
| PROJECT-PLATFORM-S21-M8-T10 | 对反馈去重、复现并映射 owner、严重度、影响身份、路径和证据 | 每项 finding 可复现或说明限制；P0/P1 立即停止相关试用 | Pending |
| PROJECT-PLATFORM-S21-M8-T11 | 执行试用后数据清理、权限撤销、会话终止、证据保留和环境恢复 | 无参与者残留权限或隐私数据；批准证据不可变且访问受控 | Pending |
| PROJECT-PLATFORM-S21-M8-T12 | 给出四场景人工验收结论并完成 checkpoint | 四类均有真实参与者原始证据；AI、维护者和自动化不得代签 | Pending |

### PROJECT-PLATFORM-S21-M9 缺陷修复、复验和产品 Go/No-Go

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M9-T01 | 审计 M1-M8 报告、工程证据、真人反馈、迁移决定、缺陷和未关闭 gap | 96 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化 | Pending |
| PROJECT-PLATFORM-S21-M9-T02 | 冻结缺陷 owner、优先级、修复批次、复验范围和 Go/No-Go 判定 | P0/P1/P2/P3、工程/产品、人/自动化证据和停止条件明确 | Pending |
| PROJECT-PLATFORM-S21-M9-T03 | 修复迁移一致性、legacy 残留、权限、数据安全和最小披露缺陷 | 不恢复旧合同或扩大权限；每项修复绑定根因、测试和回归面 | Pending |
| PROJECT-PLATFORM-S21-M9-T04 | 修复导航、术语、引导、模式、四场景、响应式、离线和恢复缺陷 | 只改验收阻断行为；不借收口扩展新产品范围 | Pending |
| PROJECT-PLATFORM-S21-M9-T05 | 修复性能、可靠性、备份恢复和可观测证据缺陷 | 预算与恢复对账通过；不以增大超时、关闭门禁或删除证据掩盖问题 | Pending |
| PROJECT-PLATFORM-S21-M9-T06 | 为每项 P0/P1/P2 建立自动化回归与防回归门禁 | P0/P1 全覆盖；legacy、权限、路由和模式禁止项机器可校验 | Pending |
| PROJECT-PLATFORM-S21-M9-T07 | 在干净隔离环境重跑迁移对账、完整工程、安全、恢复和兼容 route-final | 全量门禁 fresh 且无阻断；历史报告不替代当前修复证据 | Pending |
| PROJECT-PLATFORM-S21-M9-T08 | 邀请原问题场景真实参与者完成定向复验 | 原始反馈确认修复或明确仍阻断；AI/维护者不得代签人工结论 | Pending |
| PROJECT-PLATFORM-S21-M9-T09 | 完成四场景、六身份、简洁/高级模式、三视口、键盘、离线、收权和深链最终验收 | 无 legacy 入口、数据/权限泄漏、幽灵缓存或未解释退化 | Pending |
| PROJECT-PLATFORM-S21-M9-T10 | 对账缺陷、风险、工程与人工证据，冻结发布候选 | P0/P1 为零；未关闭 P2/P3 有 owner、影响、决定和跟踪来源 | Pending |
| PROJECT-PLATFORM-S21-M9-T11 | 同步架构、Program、专项索引、用户/管理员/运行手册和最终产品边界 | 文档只声明已实现及真人验证事实；生产批准与本地证据分离 | Pending |
| PROJECT-PLATFORM-S21-M9-T12 | 给出 S21 产品 Go/No-Go、完成 route-final 并把当前 Stage 置 none | 九份报告、108 Task、工程/人工结论和文档一致；仅无阻断时 Completed | Pending |

## 6. 未执行任务顺延映射

revision 49 的 S21-M4/M5 均未启动、全部为 Pending。revision 50 不改写其完成状态，而是因真人试用前新增易用性门禁按语义整体顺延：

| revision 49 范围 | revision 50 承接范围 | 说明 |
| --- | --- | --- |
| PROJECT-PLATFORM-S21-M4-T01 至 T12 | PROJECT-PLATFORM-S21-M8-T01 至 T12 | 四场景真人试用保持 12 项顺序，并补充简洁/高级模式和管理者任务 |
| PROJECT-PLATFORM-S21-M5-T01 至 T12 | PROJECT-PLATFORM-S21-M9-T01 至 T12 | 缺陷修复、真人复验和最终 Go/No-Go 保持 12 项顺序 |

`docs/90-reports/project-platform-s21-m4-human-trial-preparation.md` 保留为 revision 49 的准备历史输入；M8-T02 必须基于改造后的界面重新冻结试用协议，不得把旧准备包当成人工结论或直接改名为 M8 执行报告。

## 7. Stage 验收

- 存量 project/issue 迁移覆盖、数据一致性、权限对照和活动 legacy 使用可完整解释，未知项不被隐藏。
- 旧 `issues` 产品 API、DTO、页面和写路径退出；只保留经批准的不可变历史迁移与审计证据。
- 性能、安全、备份、恢复和完整 route-final 无 P0/P1 阻断，环境与生产声明边界明确。
- 项目空间默认以概览、工作项、项目管理、成员、设置五入口呈现；普通成员无需理解完整元模型即可完成高频任务。
- 高级配置按工作模型、流程与权限、自动化与协同、度量治理、场景模板渐进展开；执行与配置职责清楚，旧深链兼容可回退。
- 简洁/高级模式、偏好、引导、缓存、开关和遥测均不授权；六身份、最小披露、离线、三视口和键盘边界通过。
- 研发、市场、HR、交付四类真实参与者完成规定任务并保存原始反馈；自动化不冒充人工验收。
- 所有 P0/P1 清零，P2/P3 有明确决定；工程与人工证据分别可复核。
- 产品 Go/No-Go 由完成的 108 个 Task 和新鲜证据支持，不以计划、合成样本或口头结论替代。

## 8. 当前入口

S20 已完成并归档；S21-M1 至 M7 已分别完成独立工作循环，当前 schema 为 V141。Program revision 仍为 50。当前唯一入口是 `PROJECT-PLATFORM-S21-M8` 真人试用准备点：M8 为 Active 只表示允许准备，M8-T01 至 T12 仍全部为 Pending，不表示已执行或已签署。只有 5 位真人参与者、知情同意（consent）、隔离试用环境和真人主持全部就绪后，才可从 M8-T01 启动新的工作循环；不得用 AI、自动化浏览器或维护者代签真人证据，也不得提前执行 M9、归档 S21 或激活 S22。
