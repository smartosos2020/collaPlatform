---
title: PROJECT-PLATFORM-S13 查询模型与表格、列表、树形视图当前执行路线
status: active
route: PROJECT-PLATFORM-S13
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 33
stage: PROJECT-PLATFORM-S13
stage_final_milestone: PROJECT-PLATFORM-S13-M4
last_code_check: 2026-07-27
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S13 查询模型与表格、列表、树形视图

## 1. Stage 目标

在 S12 个人工作台、受权搜索和对象引用已经完成并归档的基础上，建立由服务端解释、版本化且权限失败关闭的统一 WorkItem 查询模型，交付表格、紧凑列表、树形层级以及个人/共享保存视图。动态字段、关系、角色、流程和时间条件只能使用已发布 snapshot 与注册 capability；查询、facet、分组、聚合、导出和视图分享必须逐项复用 S11 decision/data scope 与 S12 最小披露合同。

S13 不把 search index、personal projection、浏览器列配置或保存视图当作内容和授权权威，不接受任意 SQL、脚本或客户端表达式，也不提前实现 S14 看板/日历/甘特、S16 产能、S17 自动化或 S18 跨空间同步。

## 2. 固定输入与当前事实

- S12 完成路线已归档；当前 schema 为 V105，个人工作聚合、recent/favorite/draft/card、规范 WorkItem 搜索、动态、提醒、催办和通知重校准已交付。
- S11 snapshot v5 decision/data scope 仍是查询、列、分组、批量动作、导出和共享视图的权限权威；enterprise-admin 不自动获得私有内容。
- 规范 WorkItem、动态值及类型化字段投影由 project owner 持有；关系/层级、状态流、节点流和 participant 继续保持各自既有权威。
- search index 只提供最小全文召回，不承载 S13 动态字段/关系查询 DSL，也不证明对象可见。
- platform recent/favorite 和 S12 dashboard card 可复用对象引用，但不得成为保存视图事实或复制查询结果。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份仍是最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 查询 AST 只接受注册节点、类型化值、深度/集合/页大小硬限制与稳定版本；禁止动态 SQL、任意字段名和脚本。
3. 召回、过滤、排序、分组、计数、聚合、游标、列值、批量动作与导出必须在服务端权限校准后形成，不由浏览器过滤 hidden 对象。
4. 字段能力来自绑定 published snapshot；无 query/sort/group capability 的条件必须失败关闭，不能退化为 JSONB 全表扫描。
5. 关系和树查询只通过 project 公共服务与规范 WorkItem identity，不读取 S08/S09/S10 私表拼接第二权威。
6. 保存视图只保存版本化查询和展示配置，不保存结果、标题、字段值、角色或授权快照；分享和移交必须可撤销、可审计。
7. 写命令使用 caller-stable request ID、expected version、持久 receipt、audit/outbox 与稳定重放。
8. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M1 | 统一筛选、排序、分组和分页查询 DSL | S12 归档；Program revision 33 | `docs/90-reports/project-platform-s13-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S13-M2 | 表格与紧凑列表视图 | M1 | `docs/90-reports/project-platform-s13-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S13-M3 | 树形层级视图 | M1-M2 | `docs/90-reports/project-platform-s13-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S13-M4 | 个人/共享视图、收藏、权限与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s13-m4-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S13-M1 统一筛选、排序、分组和分页查询 DSL

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M1-T01 | 审计 WorkItem 查询、字段投影、search、关系/层级、流程、参与者、权限与分页现状 | API、表、索引、owner、调用方、预算和禁止依赖可定位；不依赖旧会话结论 | Pending |
| PROJECT-PLATFORM-S13-M1-T02 | 冻结 QueryDefinition、FilterNode、SortSpec、GroupSpec、QueryCursor 与结果合同 | 永久 key、schema version、类型、空值、时间、错误、上限和升级语义明确 | Pending |
| PROJECT-PLATFORM-S13-M1-T03 | 设计查询定义、命令回执、版本与可重建统计所需 Flyway schema | workspace/space/type/user 复合边界、唯一性、FK、索引、清理与 owner 完整 | Pending |
| PROJECT-PLATFORM-S13-M1-T04 | 实现注册式 filter AST 校验、规范化、hash 与复杂度预算 | 未知节点、过深树、超大集合、类型错配和任意表达式失败关闭 | Pending |
| PROJECT-PLATFORM-S13-M1-T05 | 接入系统字段与 snapshot 声明的动态字段 query capability | 比较、集合、空值和时间语义类型正确；无 capability 不回退 JSONB 扫描 | Pending |
| PROJECT-PLATFORM-S13-M1-T06 | 接入受控关系、层级、角色、状态流、节点流和时间窗口条件 | 仅调用公共 owner 端口；无跨模块私表、流程边冒充关系或隐藏 subject 泄漏 | Pending |
| PROJECT-PLATFORM-S13-M1-T07 | 实现稳定多列排序、分组、聚合与签名 keyset cursor | 相等值、null、跨页更新和篡改可解释；不使用泄漏内部序号的 offset 游标 | Pending |
| PROJECT-PLATFORM-S13-M1-T08 | 在召回、计数、分组、facet、cursor 和聚合阶段接入 S11 批量 decision/data scope | hidden 对象不改变任何可观察数量、分组、下一页或错误外形 | Pending |
| PROJECT-PLATFORM-S13-M1-T09 | 交付统一查询 API/DTO、低基数指标和治理 explain/dry-run | explain 不返回策略正文或 hidden 样本；指标不以 query、identity、标题作 tag | Pending |
| PROJECT-PLATFORM-S13-M1-T10 | 完成 AST、类型、游标、乱序、权限收紧和六身份自动化测试 | 无注入、越权、重复、漏页、幽灵分组或 enterprise 旁路 | Pending |
| PROJECT-PLATFORM-S13-M1-T11 | 执行代表性事项/字段/条件/分组密度的 PostgreSQL 查询预算 | SQL/端口调用/内存上界和索引计划可复现；不冒充生产 SLO | Pending |
| PROJECT-PLATFORM-S13-M1-T12 | 同步目标/当前架构、模块/对象/查询合同并完成 M1 checkpoint | 文档只声明查询 DSL；表格、树和共享保存视图仍由 M2-M4 交付 | Pending |

### PROJECT-PLATFORM-S13-M2 表格与紧凑列表视图

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M2-T01 | 复核 M1 查询 DSL、权限、迁移、报告与未关闭阻断 | 12 项逐项可追溯；视图不复制查询权威或绕过 data scope | Pending |
| PROJECT-PLATFORM-S13-M2-T02 | 冻结 TableView、ListView、ColumnSpec、CellProjection、BulkAction 与 ExportJob 合同 | 列 key、宽度、顺序、格式、版本、能力和生命周期无歧义 | Pending |
| PROJECT-PLATFORM-S13-M2-T03 | 设计列配置、用户偏好、批量命令回执和导出任务 Flyway schema | 复合边界、不可变输入、状态机、幂等、索引、TTL 与清理责任完整 | Pending |
| PROJECT-PLATFORM-S13-M2-T04 | 实现服务端列目录与字段/流程/关系最小 CellProjection | hidden/read-denied 列和值不进入响应、排序、导出或占位提示 | Pending |
| PROJECT-PLATFORM-S13-M2-T05 | 实现表格与紧凑列表的稳定分页、列排序、密度和冻结列配置 | 长名称、null、宽列和窄屏可用；配置冲突显式刷新且不丢输入 | Pending |
| PROJECT-PLATFORM-S13-M2-T06 | 实现批量选择、动作 capability 与逐对象原子/部分失败合同 | 选择集有硬限；每个对象重新鉴权；失败清单可续跑且不扩权 | Pending |
| PROJECT-PLATFORM-S13-M2-T07 | 实现受控导出任务、快照水位、下载授权与过期清理 | 只导出当前允许列/行；重试不重复任务，下载再次校准且有界 | Pending |
| PROJECT-PLATFORM-S13-M2-T08 | 交付表格/列表 Web、列配置、筛选排序、批量反馈和导出入口 | 键盘、焦点、loading/空态/错误、长名称及 1440/1366/820 可用 | Pending |
| PROJECT-PLATFORM-S13-M2-T09 | 接入 realtime 失效、离线输入保留、多标签冲突与 REST 重校准 | 信号无正文；断线不丢查询/列输入，收权后旧 cell 不闪现 | Pending |
| PROJECT-PLATFORM-S13-M2-T10 | 完成六身份、hidden 列、并发批量、导出收权和恢复真实验收 | 无越权列/行/文件、重复动作、幽灵选择或错误身份泄漏 | Pending |
| PROJECT-PLATFORM-S13-M2-T11 | 执行宽表、长列表、批量上限、导出规模和渲染预算 | 后端/前端预算与索引计划可复现；不声明 S14 或生产容量 | Pending |
| PROJECT-PLATFORM-S13-M2-T12 | 同步对象/模块/事件/运维合同并完成 M2 checkpoint | owner、导出、重校准和回退清楚；不提前声明树或共享视图完成 | Pending |

### PROJECT-PLATFORM-S13-M3 树形层级视图

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M3-T01 | 复核 M1-M2 查询、列、批量、导出、权限和未关闭阻断 | 24 项逐项可追溯；树不建立第二套 parent/edge 权威 | Pending |
| PROJECT-PLATFORM-S13-M3-T02 | 冻结 TreeView、TreeNode、ExpansionCursor、AncestorPath 与 TreeAggregate 合同 | identity、depth、排序、截断、孤儿、循环、权限和删除语义明确 | Pending |
| PROJECT-PLATFORM-S13-M3-T03 | 设计树视图偏好、展开状态与可重建聚合所需 Flyway schema | 只保存用户展示状态和版本；不复制 canonical hierarchy 或标题 | Pending |
| PROJECT-PLATFORM-S13-M3-T04 | 通过 S10 公共层级端口实现根、子节点、祖先路径和懒加载查询 | 不读 relation/hierarchy 私表；深度/节点/分支硬限且稳定分页 | Pending |
| PROJECT-PLATFORM-S13-M3-T05 | 对根、子级、祖先、计数和聚合逐层执行 S11 decision/data scope | hidden 节点不暴露存在、子数、路径断点或不可见祖先标题 | Pending |
| PROJECT-PLATFORM-S13-M3-T06 | 实现树内筛选、匹配路径、排序与局部聚合语义 | filtered/matched/context 节点可解释；无全树扫描或错误计数旁路 | Pending |
| PROJECT-PLATFORM-S13-M3-T07 | 处理循环防御、孤儿、归档、并发 reparent 与投影漂移恢复 | 权威异常失败关闭；扫描/rebuild 只修可丢弃聚合和展开状态 | Pending |
| PROJECT-PLATFORM-S13-M3-T08 | 交付树形 Web、懒展开、键盘导航、选择与详情深链 | 焦点、展开态、长名称、loading/错误及 1440/1366/820 可用 | Pending |
| PROJECT-PLATFORM-S13-M3-T09 | 接入批量动作、导出与表格/列表切换的上下文保持 | 切换不丢受权查询与选择；动作/导出继续逐对象鉴权 | Pending |
| PROJECT-PLATFORM-S13-M3-T10 | 完成六身份、深树、宽树、收权、循环、孤儿、并发和离线自动化测试 | 无 hidden 路径/计数、重复节点、无限展开或输入丢失 | Pending |
| PROJECT-PLATFORM-S13-M3-T11 | 执行树深度、分支、展开页、聚合与渲染预算 | SQL/端口/内存/DOM 上界可复现；不冒充 S14 甘特或生产 SLO | Pending |
| PROJECT-PLATFORM-S13-M3-T12 | 同步层级/查询/恢复合同并完成 M3 checkpoint | 当前事实、owner 与 M4 保存视图输入清晰；不实现 S14 拖拽视图 | Pending |

### PROJECT-PLATFORM-S13-M4 个人/共享视图、收藏、权限与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S13-M4-T02 | 冻结 SavedView、ViewOwner、ViewShare、ViewFavorite、ViewVersion 与迁移合同 | 查询/展示版本、个人/共享、复制、移交、撤销、删除和失效语义明确 | Pending |
| PROJECT-PLATFORM-S13-M4-T03 | 设计保存视图、版本、共享授权、收藏引用和命令回执 Flyway schema | workspace/space/owner 复合边界、FK、唯一性、索引、不可变版本与清理完整 | Pending |
| PROJECT-PLATFORM-S13-M4-T04 | 实现个人视图创建、更新、复制、删除与稳定重放 | caller-stable request ID、expected version、不可变历史和冲突刷新一致 | Pending |
| PROJECT-PLATFORM-S13-M4-T05 | 实现共享、撤销、移交和可管理/可使用权限决策 | 分享不扩张底层内容权限；enterprise、non-member 和失效 subject 无旁路 | Pending |
| PROJECT-PLATFORM-S13-M4-T06 | 接入 S12 收藏/最近与规范 view resolver、deep-link 和失效清理 | 只保存 view 引用；不可见/删除视图不回显旧名称、路径或数量 | Pending |
| PROJECT-PLATFORM-S13-M4-T07 | 实现保存视图执行时的 DSL/列/树版本升级、权限重校准和失败恢复 | 旧版本显式迁移或失败关闭；恢复不改 WorkItem、权限或层级事实 | Pending |
| PROJECT-PLATFORM-S13-M4-T08 | 交付个人/共享视图目录、编辑、复制、收藏、分享、移交和撤销 Web 闭环 | 权限来源、反馈、键盘、长名称、窄屏、离线和冲突可理解 | Pending |
| PROJECT-PLATFORM-S13-M4-T09 | 完成六身份、跨空间、并发编辑、收权、删除、移交、离线和恢复真实验收 | 无视图/标题/查询泄漏、重复事实、幽灵收藏、越权结果或输入丢失 | Pending |
| PROJECT-PLATFORM-S13-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；mock 不冒充数据库/浏览器证据；关键视口日志 fresh | Pending |
| PROJECT-PLATFORM-S13-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S14 准入 | 文档只声明已实现事实；S14 继续复用 S11-S13 权限与查询合同 | Pending |
| PROJECT-PLATFORM-S13-M4-T12 | 给出 S13 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、工作上下文、48 Task 和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 统一查询 DSL 对系统字段、动态字段、关系、角色、流程与时间条件使用注册类型、硬限制和稳定版本，不接受任意 SQL/脚本。
- 过滤、排序、分组、聚合、计数、游标、列值、批量动作和导出在服务端复用 S11 decision/data scope，hidden 对象零披露。
- 表格、紧凑列表和树形层级使用同一查询模型；层级继续以 S10 canonical hierarchy 为唯一权威。
- 个人/共享保存视图只保存查询和展示配置；创建、复制、分享、移交、撤销、收藏与删除幂等、可审计且不扩权。
- 六身份、跨空间、并发、收权、离线、长名称、1440/1366/820 与真实 PostgreSQL/Flyway 证据通过。
- S13 不实现 S14 看板/日历/甘特、S16 产能、S17 自动化或 S18 跨空间同步，也不把本地预算表述为生产容量。

## 7. 起始点

当前入口是 `PROJECT-PLATFORM-S13-M1-T01`。S13 固定为 4 个 Milestone、48 个 Task；每个 Milestone 必须单独执行 AI 工作循环，不得跨 Milestone 合并收口。
