---
title: PROJECT-PLATFORM-S14 看板、日历、甘特和时间线当前执行路线
status: active
route: PROJECT-PLATFORM-S14
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 35
stage: PROJECT-PLATFORM-S14
stage_final_milestone: PROJECT-PLATFORM-S14-M4
last_code_check: 2026-07-27
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S14 看板、日历、甘特和时间线

## 1. Stage 目标

在 S13 统一查询、表格/列表/树和保存视图已经完成并归档的基础上，交付由同一受权查询集合驱动的看板、日历、甘特和时间线。看板分组、泳道和拖拽只能调用 S08/S09 规范流程命令；日期区间、依赖线、层级展开、基线和时间线只投影 S07/S10 已有事实或本 Stage 明确拥有的视图配置，不能建立第二套 WorkItem、流程、层级、关系或权限权威。

S14 不把浏览器布局、拖拽位置、日历事件、甘特条、关键路径缓存或基线快照当作业务事实。所有行、列、计数、日期、依赖、动作、导出和保存视图都必须在服务端复用 S11 decision/data scope 与 S13 查询/列/树合同。S14 不提前实现 S15 项目计划、S16 人员产能、S17 自动化、S18 跨空间同步或 S19 管理度量。

## 2. 固定输入与当前事实

- S13 完成路线已归档；当前 schema 为 V109，统一查询 DSL、表格/列表、permission-scoped 树和个人/共享保存视图已交付。
- S08 current state/action 和 S09 node task/action 是拖拽流转的唯一运行权威；S14 只能调用其公共命令，不能直接改状态、token、task 或 history。
- S10 relation/hierarchy 是依赖线与层级展开的唯一权威；S14 不创建平行 edge、parent、closure 或关键路径事实。
- S11 snapshot v5 decision/data scope 对视图行、字段、泳道、计数、依赖端点和动作继续失败关闭；enterprise-admin 不自动获得私有内容。
- S13 QueryDefinition、ColumnSpec、TreeView 与 SavedView 是查询和展示配置入口；S14 通过版本化 presentation 扩展复用，不复制结果或权限快照。
- 日期字段能力来自绑定 published snapshot；无 date/range/schedule capability 的字段不能被浏览器猜测或回退为任意 JSON。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份仍是最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 看板列、泳道、日历事件、甘特条和时间线节点都来自同一服务端受权 QueryDefinition，不允许客户端先取全量再过滤。
3. 拖拽写命令必须包含 caller-stable request ID、expected WorkItem/流程版本、持久 receipt、audit/outbox 和稳定重放；失败不遗留半流转或伪布局。
4. 时间语义固定存储 instant/local date/zone/区间边界与无日期状态；DST、跨日、全天和 locale 展示不得改变权威值。
5. 依赖与层级只通过 S10 公共投影；隐藏端点、祖先和相邻边不得通过线条、数量、空隙、关键路径或错误外形泄漏。
6. 基线只保存明确受权的视图计划快照与来源版本，不冻结权限、标题或隐藏对象；读取、比较和导出必须重新鉴权。
7. realtime 只失效查询和时间投影，online/focus/reconnect 后经 REST 校准；离线拖拽与日期编辑保留输入但不伪造成功。
8. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M1 | 看板分组、泳道和拖拽动作 | S13 归档；Program revision 35 | `docs/90-reports/project-platform-s14-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S14-M2 | 日历和日期字段视图 | M1 | `docs/90-reports/project-platform-s14-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S14-M3 | 甘特、依赖线和层级展开 | M1-M2 | `docs/90-reports/project-platform-s14-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S14-M4 | 基线、时间线、性能与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s14-m4-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S14-M1 看板分组、泳道和拖拽动作

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M1-T01 | 审计 S13 查询/保存视图、S08/S09 流程动作、S11 权限与现有看板 | API、表、owner、调用方、预算、可复用能力和禁止依赖可定位；不依赖旧会话结论 | Pending |
| PROJECT-PLATFORM-S14-M1-T02 | 冻结 BoardView、ColumnGroup、Swimlane、CardProjection 与 MoveIntent 合同 | schema version、稳定 key、空列、WIP、排序、错误和升级语义明确 | Pending |
| PROJECT-PLATFORM-S14-M1-T03 | 设计看板偏好、排序投影、命令回执和可重建统计所需 Flyway schema | workspace/space/user/view 复合边界、唯一性、FK、索引、清理与 owner 完整 | Pending |
| PROJECT-PLATFORM-S14-M1-T04 | 实现由 S13 QueryDefinition 驱动的列、泳道、计数和签名分页 | hidden 对象不改变列、泳道、计数、空态、游标或错误外形 | Pending |
| PROJECT-PLATFORM-S14-M1-T05 | 接入系统/动态字段的注册式分组、泳道和卡片最小投影 | 无 group/board capability 失败关闭；read-denied 字段和值不进入响应 | Pending |
| PROJECT-PLATFORM-S14-M1-T06 | 实现卡片稳定顺序、WIP 上限、并发刷新和排序恢复 | 排序是可重建视图事实；并发无重复/丢卡，WIP 不冒充流程规则 | Pending |
| PROJECT-PLATFORM-S14-M1-T07 | 将拖拽意图映射为 S08 状态动作或 S09 节点动作 | 只调用规范公共命令；无隐式状态写、token 写或客户端 guard 解释 | Pending |
| PROJECT-PLATFORM-S14-M1-T08 | 实现拖拽幂等、乐观锁、失败回滚和权限重校准 | 一胜一冲突、精确重放、收权失败关闭，失败不遗留排序或半流转 | Pending |
| PROJECT-PLATFORM-S14-M1-T09 | 交付看板 Web、键盘移动、筛选、泳道、长名称和响应式交互 | 1440/1366/820 可用；焦点、loading、空态、错误和权限来源可理解 | Pending |
| PROJECT-PLATFORM-S14-M1-T10 | 完成六身份、跨空间、并发拖拽、收权、离线和恢复自动化测试 | 无隐藏卡片/计数/泳道、重复动作、enterprise 旁路或输入丢失 | Pending |
| PROJECT-PLATFORM-S14-M1-T11 | 执行卡片量、列/泳道密度、移动和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产容量或 S16 产能 | Pending |
| PROJECT-PLATFORM-S14-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明看板事实；日历、甘特、基线和时间线仍由 M2-M4 交付 | Pending |

### PROJECT-PLATFORM-S14-M2 日历和日期字段视图

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M2-T01 | 复核 M1 看板、查询、动作、权限、迁移和未关闭阻断 | 12 项逐项可追溯；日历不复制 WorkItem 或日期权威 | Pending |
| PROJECT-PLATFORM-S14-M2-T02 | 冻结 CalendarView、DateBinding、CalendarEvent、RangeWindow 与 TimeZone 合同 | date/instant/range、全天、无日期、DST、locale、版本和上限明确 | Pending |
| PROJECT-PLATFORM-S14-M2-T03 | 设计日历偏好、日期投影、命令回执和可重建窗口索引 Flyway schema | 复合边界、来源版本、幂等、索引、清理和 owner 完整 | Pending |
| PROJECT-PLATFORM-S14-M2-T04 | 实现绑定 snapshot 日期 capability 的服务端窗口查询 | 未注册/类型错配失败关闭；窗口外和 hidden 对象不影响计数或游标 | Pending |
| PROJECT-PLATFORM-S14-M2-T05 | 处理单日、区间、跨日、全天、无日期和跨时区展示 | 存储语义稳定；DST 跳变和 locale 只影响显示不改权威值 | Pending |
| PROJECT-PLATFORM-S14-M2-T06 | 实现日期拖放/拉伸的规范字段更新与精确重放 | expected version、request ID、校验、audit/outbox 完整；无半区间 | Pending |
| PROJECT-PLATFORM-S14-M2-T07 | 实现月/周/日窗口、稳定分页、重叠布局和受权计数 | 大跨度有硬限；隐藏事件不产生空隙、数量或时间旁路 | Pending |
| PROJECT-PLATFORM-S14-M2-T08 | 交付日历 Web、日期选择、无日期区、键盘与响应式交互 | 1440/1366/820、长名称、loading/空态/错误和时区解释可用 | Pending |
| PROJECT-PLATFORM-S14-M2-T09 | 接入 realtime 失效、离线输入、多标签冲突和 REST 校准 | 断线不丢日期输入；收权后旧事件不闪现，不伪造成功 | Pending |
| PROJECT-PLATFORM-S14-M2-T10 | 完成六身份、DST、跨日、收权、并发、离线和恢复真实验收 | 无日期/标题/计数泄漏、重复写、错日或 enterprise 旁路 | Pending |
| PROJECT-PLATFORM-S14-M2-T11 | 执行窗口跨度、事件密度、重叠布局和渲染预算 | 查询/内存/DOM 上界可复现；不冒充生产日历容量 | Pending |
| PROJECT-PLATFORM-S14-M2-T12 | 同步日期/时间/字段/事件合同并完成 M2 checkpoint | 当前事实与 M3 输入清楚；不提前声明甘特、关键路径或基线 | Pending |

### PROJECT-PLATFORM-S14-M3 甘特、依赖线和层级展开

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M3-T01 | 复核 M1-M2 看板/日历、S10 关系层级、查询权限和阻断 | 24 项逐项可追溯；甘特不建立第二套 edge/parent/date 权威 | Pending |
| PROJECT-PLATFORM-S14-M3-T02 | 冻结 GanttView、ScheduleBar、DependencyLine、HierarchyRow 与 CriticalPath 合同 | identity、日期、依赖、层级、截断、版本、错误和上限明确 | Pending |
| PROJECT-PLATFORM-S14-M3-T03 | 设计甘特偏好、展开状态、可重建排期投影和统计 Flyway schema | 只保存展示与可重建数据；不复制关系、层级、标题或权限 | Pending |
| PROJECT-PLATFORM-S14-M3-T04 | 组合 S13 受权查询、M2 日期绑定和 S10 canonical hierarchy | 只用公共投影；无私表 join、隐藏祖先/端点或重复 WorkItem | Pending |
| PROJECT-PLATFORM-S14-M3-T05 | 通过 S10 公共关系投影生成受权依赖线与影响提示 | 双端分别鉴权；隐藏边不以线、空隙、计数或原因泄漏 | Pending |
| PROJECT-PLATFORM-S14-M3-T06 | 实现服务端关键路径/浮动量的有界、可解释派生 | 循环、缺日期、截断和隐藏端点显式降级；派生不成为关系权威 | Pending |
| PROJECT-PLATFORM-S14-M3-T07 | 实现日期移动/拉伸与依赖约束的规范命令编排 | 不自动改写未授权事项；冲突/部分失败可解释且无半提交 | Pending |
| PROJECT-PLATFORM-S14-M3-T08 | 交付甘特 Web、层级展开、缩放、依赖线、键盘和深链 | 1440/1366/820、长名称、焦点、loading/错误和收权可用 | Pending |
| PROJECT-PLATFORM-S14-M3-T09 | 接入筛选、保存视图、导出、realtime 和上下文保持 | table/list/tree/board/calendar/gantt 切换不丢受权查询或输入 | Pending |
| PROJECT-PLATFORM-S14-M3-T10 | 完成六身份、深树、依赖环、收权、并发、离线和恢复测试 | 无 hidden path/edge/date、无限展开、重复写或错序 | Pending |
| PROJECT-PLATFORM-S14-M3-T11 | 执行行数、依赖密度、层级深度、缩放和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产 SLO | Pending |
| PROJECT-PLATFORM-S14-M3-T12 | 同步关系/层级/时间/恢复合同并完成 M3 checkpoint | 当前事实与 M4 基线输入清楚；不提前实现 S15 项目计划 | Pending |

### PROJECT-PLATFORM-S14-M4 基线、时间线、性能与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S14-M4-T02 | 冻结 ScheduleBaseline、BaselineEntry、TimelineEvent、Diff 与生命周期合同 | 来源版本、创建/比较/删除、保留、权限、错误和升级语义明确 | Pending |
| PROJECT-PLATFORM-S14-M4-T03 | 设计基线、不可变条目、命令回执和可重建时间线 Flyway schema | 复合边界、唯一性、不可变、索引、TTL/清理和 owner 完整 | Pending |
| PROJECT-PLATFORM-S14-M4-T04 | 实现受权基线创建、列举、比较、删除和精确重放 | 只冻结允许 identity/date/dependency 版本；不冻结标题、字段或权限 | Pending |
| PROJECT-PLATFORM-S14-M4-T05 | 实现由 activity/audit/流程/关系公共事实组成的最小时间线 | 来源可解释、去重稳定；不读私表、不复制正文、不成为新 history | Pending |
| PROJECT-PLATFORM-S14-M4-T06 | 对基线读取、diff、时间线和导出执行当前权限重校准 | 收权后旧条目/数量/标题不泄漏；enterprise/non-member 无旁路 | Pending |
| PROJECT-PLATFORM-S14-M4-T07 | 交付基线/差异/时间线 Web、保存视图与恢复体验 | 长名称、键盘、空态/错误、离线和 1440/1366/820 可理解 | Pending |
| PROJECT-PLATFORM-S14-M4-T08 | 完成六身份、跨空间、并发、收权、删除、离线和恢复真实验收 | 无时间/依赖/标题泄漏、重复基线、幽灵事件或输入丢失 | Pending |
| PROJECT-PLATFORM-S14-M4-T09 | 执行看板/日历/甘特/时间线组合规模和交互预算 | 固定夹具与上界可复现；本地预算不表述为生产容量 | Pending |
| PROJECT-PLATFORM-S14-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；关键视口与六身份 route-final 证据 fresh | Pending |
| PROJECT-PLATFORM-S14-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S15 准入 | 文档只声明已实现事实；S15 继续复用 S14 时间与 S11 权限合同 | Pending |
| PROJECT-PLATFORM-S14-M4-T12 | 给出 S14 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、48 Task、工作上下文和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 看板、日历、甘特和时间线使用同一 S13 受权查询集合，行、列、泳道、计数、日期、依赖和导出均在服务端最小披露。
- 拖拽只调用 S08/S09 规范动作或规范字段命令；幂等、乐观锁、冲突、收权和失败回滚完整。
- 日期/区间/时区/DST、S10 依赖/层级、关键路径派生和基线语义明确，不建立第二权威。
- 保存视图、realtime、离线、多标签、长名称、1440/1366/820 和六身份闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 `route-final` 无阻断。
- S14 不实现 S15 项目计划、S16 人员产能、S17 自动化、S18 跨空间同步或 S19 管理度量，也不把本地预算表述为生产容量。

## 7. 起始点

从 `PROJECT-PLATFORM-S14-M1-T01` 开始。当前只允许推进 S14-M1，不得跳到后续 Milestone，也不得提前实现 S15。
