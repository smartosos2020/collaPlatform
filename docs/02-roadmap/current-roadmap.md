---
title: PROJECT-PLATFORM-S12 个人工作台、搜索、收藏和动态当前执行路线
status: active
route: PROJECT-PLATFORM-S12
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 31
stage: PROJECT-PLATFORM-S12
stage_final_milestone: PROJECT-PLATFORM-S12-M4
last_code_check: 2026-07-27
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S12 个人工作台、搜索、收藏和动态

## 1. Stage 目标

在 S11 分层权限已经完成并归档的基础上，为用户建立跨空间但不越权的个人工作入口。工作台聚合我的待办、负责、参与和关注，统一最近、收藏、草稿和个性化卡片；全局搜索召回规范 WorkItem 并提供安全深链；动态、提醒、催办和个人通知共享稳定来源、去重与已读语义。

S12 的完成标准不是把多个列表在浏览器拼接，也不是从 search index、通知正文或缓存反推内容权限。每个聚合项、计数、游标、搜索命中和动态来源都必须在服务端按 S11 统一 decision 与 data scope 校准；平台、搜索、通知和 project 模块继续各自拥有事实，只通过公共合同、resolver 和有界批量端口协作。S12 不提前实现 S13 查询 DSL/保存视图、S14 甘特日历、S16 人员产能、S17 自动化或 S18 跨空间同步。

## 2. 固定输入与当前事实

- S11 完成路线已归档；当前 schema 为 V101，规范 WorkItem、状态/节点流、关系/层级与 snapshot v5 分层权限均已交付。
- `/api/workspace/dashboard`、`/api/platform/recent`、`/api/platform/favorites`、`/api/search` 和 `/api/notifications` 已有通用能力，但尚未形成受 S11 data scope 统一校准的规范 WorkItem 个人工作台。
- project 拥有 WorkItem、参与者、node task、活动和权限决策；platform 拥有对象最近/收藏；search 拥有可重建索引；notification 拥有通知、偏好与已读事实。任何模块不得读其他模块私表拼装个人聚合。
- 最近和收藏只保存规范对象引用，不是对象标题或权限快照权威；返回前必须经 resolver/用户 API 重校准，失效或不可见对象不得泄漏旧标题。
- 搜索索引是可重建投影，不是内容或授权事实；索引内存在对象不能证明调用者可见，分页、分组和总量同样必须按权限失败关闭。
- 草稿存在于各 owner 模块；S12 只能通过公共草稿摘要合同聚合当前用户可见草稿，不建立跨模块通用草稿表。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份仍是最低回归矩阵；enterprise-admin 不自动获得私有空间、WorkItem、收藏或个人动态。
- S12 不实现 S13 保存视图/任意动态查询、S14 时间视图、S16 资源排期、S17 自动化规则或 S18 跨空间授权同步。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 个人聚合以 workspace 与当前 user 为边界；企业治理身份不映射为内容主体，也不能查询其他用户的个人工作台。
3. source fact 继续由 project、platform、search、notification 等 owner 持有；聚合投影可重建，不成为 WorkItem、收藏、索引或通知的第二权威。
4. 列表、计数、分组、游标、搜索高亮、深链和动态来源必须复用 S11 decision/data scope 或等价批量结果；禁止先全量返回再由前端过滤。
5. 事件只触发最小失效与重读；缓存、搜索索引和个人卡片不得携带 hidden 字段、策略正文、subject 私有信息或不可见标题。
6. 收藏、卡片配置、催办、已读等写命令使用 caller-stable request ID、expected version、持久 receipt、audit/outbox 与稳定重放。
7. 不可见对象统一最小披露；权限收紧后先丢弃受保护缓存，再经 REST 校准，不能从空分组、计数差异或错误类型枚举对象。
8. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M1 | 我的待办、负责、参与和关注 | S11 归档；Program revision 31 | `docs/90-reports/project-platform-s12-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S12-M2 | 最近、收藏、草稿和个性化卡片 | M1 | `docs/90-reports/project-platform-s12-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S12-M3 | 跨空间、跨类型全局搜索 | M1-M2 | `docs/90-reports/project-platform-s12-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S12-M4 | 动态、提醒、催办、个人通知与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s12-m4-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S12-M1 我的待办、负责、参与和关注

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M1-T01 | 审计 workspace dashboard、WorkItem 列表、参与者、node task、权限决策、搜索、最近/收藏和通知现状 | API、表、事件、缓存、调用方、权限缺口和禁止依赖可定位；不依赖旧会话结论 | Pending |
| PROJECT-PLATFORM-S12-M1-T02 | 冻结 PersonalWorkItem、WorkBucket、BucketReason、PersonalCursor 与聚合来源合同 | 永久 key、身份、版本、排序、时间、最小摘要、错误和失效语义无歧义 | Pending |
| PROJECT-PLATFORM-S12-M1-T03 | 设计个人聚合、命令回执、失效水位和可重建投影 Flyway schema | workspace/user/object 复合边界、唯一性、FK、索引、清理和 rebuild 责任完整 | Pending |
| PROJECT-PLATFORM-S12-M1-T04 | 实现我的待办、负责、参与和关注服务端聚合与有界批量端口 | 四类来源明确且可组合；不读 project 私表外泄，也不由前端拼列表 | Pending |
| PROJECT-PLATFORM-S12-M1-T05 | 把 S08/S09 待处理状态、node task、assignee、participant 与 watcher 规范映射为 bucket | 同一对象可有多个原因但只返回一份摘要；原因、待办状态和来源版本可解释 | Pending |
| PROJECT-PLATFORM-S12-M1-T06 | 接入 S11 对象、字段、节点、关系和 data scope 的批量 decision | 列表、分组、计数和命令入口一致；权限层只收紧，不产生新的内容授权 | Pending |
| PROJECT-PLATFORM-S12-M1-T07 | 实现稳定排序、游标、分组计数、时间窗口和硬限制 | hidden 对象不改变可观察总量；游标不可伪造、跨用户复用或泄漏内部序号 | Pending |
| PROJECT-PLATFORM-S12-M1-T08 | 实现事件驱动的个人投影失效、补偿重读和 subject/version 校准 | 收权先失效后重读；乱序、重复、gap、禁用用户和空间归档均收敛 | Pending |
| PROJECT-PLATFORM-S12-M1-T09 | 交付个人工作台 API/DTO 与 Web 的四类入口、空态和刷新 | UI 只使用服务端 bucket/capability；错误、loading、窄屏和深链可理解 | Pending |
| PROJECT-PLATFORM-S12-M1-T10 | 完成六身份、跨空间、自定义角色、并发收权和 hidden 零披露自动化测试 | enterprise/non-member 无旁路；guest、member、owner 的列表和动作与详情一致 | Pending |
| PROJECT-PLATFORM-S12-M1-T11 | 执行代表性空间数、事项数、bucket 密度和批量决策预算 | SQL/端口调用上界、索引计划和延迟可复现；不冒充 S13 或生产容量 | Pending |
| PROJECT-PLATFORM-S12-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 文档只声明个人工作聚合；最近/收藏/搜索/动态仍由 M2-M4 交付 | Pending |

### PROJECT-PLATFORM-S12-M2 最近、收藏、草稿和个性化卡片

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M2-T01 | 复核 M1 聚合、权限、投影、报告和未关闭阻断 | 12 项逐项可追溯；个性化能力不复制 WorkItem 或绕过 data scope | Pending |
| PROJECT-PLATFORM-S12-M2-T02 | 冻结 RecentObject、FavoriteObject、DraftSummary、DashboardCard 与 CardLayout 合同 | 对象引用、来源、排序、固定/隐藏、版本、生命周期和删除语义明确 | Pending |
| PROJECT-PLATFORM-S12-M2-T03 | 收敛 platform 最近/收藏的幂等记录、取消、排序和规范对象类型 | caller-stable request ID、唯一性和重放稳定；不保存内容正文或授权快照 | Pending |
| PROJECT-PLATFORM-S12-M2-T04 | 对最近/收藏逐项执行 resolver 与 S11 decision 重校准 | 不可见、删除、归档和权限收紧对象不回显旧标题、数量或路径 | Pending |
| PROJECT-PLATFORM-S12-M2-T05 | 建立 project 及其他 owner 的公共 DraftSummary 聚合端口 | 只返回当前用户可见草稿；状态、更新时间和恢复入口由来源模块解释 | Pending |
| PROJECT-PLATFORM-S12-M2-T06 | 实现个人卡片目录、布局、显隐、排序和乐观版本命令 | 卡片 key 稳定；未知/失效卡片失败关闭；冲突刷新不丢本地输入 | Pending |
| PROJECT-PLATFORM-S12-M2-T07 | 实现最近、收藏、草稿和卡片的分页、上限、清理与恢复 | 过期和失效引用可清理；恢复不覆盖后续用户配置或重新暴露对象 | Pending |
| PROJECT-PLATFORM-S12-M2-T08 | 交付工作台卡片、收藏切换、最近和草稿 Web 交互 | 键盘、焦点、长名称、空态、错误、1440/1366/820 和无障碍可用 | Pending |
| PROJECT-PLATFORM-S12-M2-T09 | 接入 realtime 失效、离线输入保留和多标签页校准 | 收藏/卡片变更可重读；断线不丢配置，陈旧标题不闪现 | Pending |
| PROJECT-PLATFORM-S12-M2-T10 | 完成跨用户隔离、重复点击、冲突、收权、删除和恢复自动化测试 | 无他人收藏/草稿泄漏、重复事实、幽灵卡片或权限回退 | Pending |
| PROJECT-PLATFORM-S12-M2-T11 | 执行最近/收藏/草稿规模、resolver 批量和卡片渲染预算 | 调用与内存上界可复现；索引和清理计划明确，不声明生产容量 | Pending |
| PROJECT-PLATFORM-S12-M2-T12 | 同步对象/模块/事件/运维合同并完成 M2 checkpoint | owner 边界、重校准与回退清楚；不提前声明全局搜索和动态闭环 | Pending |

### PROJECT-PLATFORM-S12-M3 跨空间、跨类型全局搜索

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M3-T01 | 复核 M1-M2 个人聚合、对象引用、权限和未关闭阻断 | 24 项逐项可追溯；搜索索引不成为内容、角色或可见性权威 | Pending |
| PROJECT-PLATFORM-S12-M3-T02 | 冻结 WorkItemSearchDocument、SearchHit、SearchFacet、SearchCursor 与 deep-link 合同 | 最小字段、来源版本、排序、片段、权限态和删除语义明确 | Pending |
| PROJECT-PLATFORM-S12-M3-T03 | 实现规范 WorkItem 的最小 search projection 与事务 outbox consumer | 索引可重建、幂等、乱序安全；不含 hidden 字段、策略正文或 subject 信息 | Pending |
| PROJECT-PLATFORM-S12-M3-T04 | 实现跨空间/类型关键字检索与受控空间、类型、状态、角色、时间筛选 | 白名单参数、深度/集合硬限；不接受动态 SQL、任意字段或 S13 查询 DSL | Pending |
| PROJECT-PLATFORM-S12-M3-T05 | 在召回、facet、计数、分页和高亮阶段接入 S11 批量 decision/data scope | 不可见命中不影响总量、分组、游标、分数或错误；禁止客户端二次过滤 | Pending |
| PROJECT-PLATFORM-S12-M3-T06 | 实现安全排序、相关性、精确匹配、长文本截断和高亮 | 排序稳定可复现；片段只来自允许字段，不从隐藏内容生成命中信号 | Pending |
| PROJECT-PLATFORM-S12-M3-T07 | 实现 permission/subject 变化后的索引校准、删除、重建和失败续跑 | 索引漂移可扫描；rebuild 不修改业务事实，失败清单可续跑且不扩权 | Pending |
| PROJECT-PLATFORM-S12-M3-T08 | 实现规范 WorkItem 深链、旧 issue 显式 map 和不可见/已删除外形 | 链接经 resolver 校准；无 legacy 双写、开放重定向或标题泄漏 | Pending |
| PROJECT-PLATFORM-S12-M3-T09 | 交付全局搜索 API/DTO、可观察指标和治理恢复入口 | 指标低基数；管理员只能看投影健康，enterprise 治理不能读取私有命中 | Pending |
| PROJECT-PLATFORM-S12-M3-T10 | 交付搜索页的跨空间/类型结果、筛选、键盘导航和深链 | loading/空态/错误、长名称、1440/1366/820、焦点和返回上下文可用 | Pending |
| PROJECT-PLATFORM-S12-M3-T11 | 完成六身份、hidden 字段、索引陈旧、乱序、重建和查询预算测试 | 零泄漏、稳定分页、无重复/幽灵命中；预算不冒充 S13 或生产 SLO | Pending |
| PROJECT-PLATFORM-S12-M3-T12 | 同步搜索/对象/事件/恢复合同并完成 M3 checkpoint | 当前事实、owner 边界、失效与 M4 动态输入清晰；不实现保存视图 | Pending |

### PROJECT-PLATFORM-S12-M4 动态、提醒、催办、个人通知与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S12-M4-T02 | 冻结 PersonalActivity、Reminder、Nudge、NotificationSource 与 read-state 合同 | 来源事件、对象版本、接收者、去重、时间、状态、隐私和失效语义明确 | Pending |
| PROJECT-PLATFORM-S12-M4-T03 | 实现受权个人动态聚合、来源解释、分页和已读水位 | 只聚合当前可见最小事件；hidden 对象不进入计数、分组、摘要或游标 | Pending |
| PROJECT-PLATFORM-S12-M4-T04 | 实现待办临期/到期/超期提醒与 notification 去重路由 | 时间语义、时区、重复调度、偏好和已读一致；不提前实现 S17 任意规则 | Pending |
| PROJECT-PLATFORM-S12-M4-T05 | 实现催办权限、频率限制、幂等回执、审计和撤销边界 | 只能催办可见且可操作对象；无重复轰炸、身份泄漏或 enterprise 旁路 | Pending |
| PROJECT-PLATFORM-S12-M4-T06 | 收敛个人通知、动态和 realtime 的失效/REST 重校准 | 乱序、重复、gap、重连、收权和离线均收敛；信号不携带正文 | Pending |
| PROJECT-PLATFORM-S12-M4-T07 | 实现动态/提醒一致性扫描、dry-run、rebuild、失败清单和续跑 | 只重建可丢弃投影；不改 WorkItem、node task、收藏或通知规范事实 | Pending |
| PROJECT-PLATFORM-S12-M4-T08 | 交付动态、提醒、催办和个人通知 Web 闭环 | 来源、已读、偏好、反馈、键盘、长名称、窄屏与无权态清晰 | Pending |
| PROJECT-PLATFORM-S12-M4-T09 | 执行六身份、跨空间、并发催办、到期、收权、离线和恢复真实验收 | 无重复通知、陈旧动态、hidden 标题、越权深链或输入丢失 | Pending |
| PROJECT-PLATFORM-S12-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；mock 不冒充数据库/浏览器证据；关键视口与恢复日志 fresh | Pending |
| PROJECT-PLATFORM-S12-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S13 准入 | 文档只声明已实现事实；S13 查询/视图继续复用 S11/S12 最小披露 | Pending |
| PROJECT-PLATFORM-S12-M4-T12 | 给出 S12 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、工作上下文、48 Task 和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 我的待办、负责、参与和关注由服务端按 workspace/user 聚合，来源、原因、排序、游标和计数可解释且不越权。
- 最近、收藏、草稿和个性化卡片保持 owner 边界；对象引用在返回前经 resolver 与 S11 decision 重校准。
- 规范 WorkItem 进入可重建 search projection；召回、facet、计数、分页、高亮和深链不会泄漏 data scope 外对象。
- 动态、提醒、催办和通知的来源、去重、已读、偏好、频率限制、realtime 与恢复语义一致。
- projection、cache、index 和 personal card 都是可丢弃投影，不成为 WorkItem、权限、收藏、搜索或通知第二权威。
- 六身份、跨空间、并发、收权、离线、长名称、1440/1366/820 与真实 PostgreSQL/Flyway 证据通过。
- S12 不实现 S13 任意查询/保存视图、S14 甘特日历、S16 产能、S17 自动化或 S18 跨空间同步，也不把本地预算表述为生产容量。

## 7. 起始点

当前入口是 `PROJECT-PLATFORM-S12-M1-T01`。S12 固定为 4 个 Milestone、48 个 Task；每个 Milestone 必须单独执行 AI 工作循环，不得跨 Milestone 合并收口。
