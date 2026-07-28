---
title: PROJECT-PLATFORM-S18 跨空间授权、关系和数据同步当前执行路线
status: completed
archived_at: 2026-07-28
route: PROJECT-PLATFORM-S18
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 44
stage: PROJECT-PLATFORM-S18
stage_final_milestone: PROJECT-PLATFORM-S18-M4
last_code_check: 2026-07-28
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S18 跨空间授权、关系和数据同步

## 1. Stage 目标

在 S17 自动化规则、时间触发、连接器、运行治理和限额完成并归档的基础上，交付工作项类型跨空间授权、跨空间关系建立与最小可见性、单向/双向字段和状态同步，以及跨团队全景视图与协作审计。S18 只拥有跨空间授权规则、同步规则/运行/冲突/补偿和治理回执；WorkItem、类型定义、关系边、流程状态、成员、权限、自动化、审计、事件和凭据仍由既有 owner 持有。

跨空间能力必须由双方显式授权并在每次读取、建链和同步执行时重新校准 S11 当前 decision/data scope。S17 自动化、Webhook 或连接器不能充当授权或同步权威；S10 继续持有 canonical relation edge。撤销、收权、归档、映射变化、冲突和补偿必须失败关闭且不泄漏标题、字段、状态、成员、关系数量或空间存在性，也不得提前实现 S19 组织级度量。

## 2. 固定输入与当前事实

- S17 完成路线已归档；当前 schema 为 V126，规则/不可变版本、受控执行、时间调度、Webhook/连接器、管理历史与四维限额均已交付。
- S02 ProjectSpace 与成员边界是空间身份和成员事实权威；跨空间授权不能创建隐式成员或绕过最后管理员保护。
- S03 可靠事件基线提供版本化 envelope、outbox、handler registry、幂等投递、重试、死信和重放；S18 不建立第二套事件总线。
- S06 published snapshot、S07 canonical WorkItem identity/runtime 和 S08/S09 状态/节点流分别拥有类型、实例与流程权威；同步只通过公共 resolver/command/event 合同协作。
- S10 relation definition/edge/history/hierarchy 是关系唯一权威；S18 只决定跨空间端点是否可建立关系，并调用规范关系命令。
- S11 snapshot v5 decision/data scope 在授权、关系、同步、全景和审计投影中持续失败关闭；enterprise-admin 不自动获得私有内容。
- S12-S16 的工作台、查询/保存视图、高级视图、项目治理和资源事实可作为当前受权展示或同步输入，但均不成为 S18 权威。
- S17 caller-stable receipt、audit/outbox、可靠执行和受控 connector security boundary 可复用；禁止读取 rule/run/connector/quota 私表。
- identity、permission、audit、event、notification、realtime 和 credential 只通过公共合同使用；缓存、索引、统计和审计投影均不授权。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份，以及 source/target 双空间组合，构成最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. CrossSpaceGrant、RelationPolicy、SyncRule、SyncRun、Conflict 和 Compensation 使用版本化合同、稳定 identity、显式生命周期和 workspace/source-space/target-space 复合边界。
3. 授权由 source/target 双方显式同意，范围只能收窄；grant 不创建成员、不复制 ACL、不缓存授权，撤销后新访问和新副作用立即失败关闭。
4. 类型、实例、字段、状态和关系只保存稳定 identity/version/mapping，不复制标题、正文、动态值、成员、流程历史或关系边。
5. 跨空间建链同时校验 source 可见/relate、target 可见/accept-link、当前 grant 和 S10 relation definition；无目标可见性只返回 forbidden reference。
6. 字段/状态同步必须冻结方向、映射、触发、冲突、循环、重试和补偿；每步执行前重新校准双方权限并调用 canonical command。
7. 写命令包含 caller-stable request ID、expected version、request hash、持久 receipt、audit/outbox 和精确重放；并发只允许一胜一冲突。
8. 撤销、收权、归档和删除不静默删除历史关系或同步运行；新执行停止，既有事实按版本化保留/脱敏/补偿合同处理。
9. realtime 只失效授权、关系、同步和全景查询；online/focus/reconnect 后 REST 校准，离线输入保留但不伪造授权、建链或同步成功。
10. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、双空间六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M1 | 工作项类型跨空间授权 | S17 归档；Program revision 43 | `docs/90-reports/project-platform-s18-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S18-M2 | 跨空间关系建立和可见性 | M1 | `docs/90-reports/project-platform-s18-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S18-M3 | 单向/双向字段与状态同步 | M1-M2 | `docs/90-reports/project-platform-s18-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S18-M4 | 跨团队全景视图、协作审计与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s18-m4-execution-report.md` | Completed |

## 5. 详细任务

### PROJECT-PLATFORM-S18-M1 工作项类型跨空间授权

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M1-T01 | 审计 S02 空间/成员、S06 类型快照、S07 实例、S10 关系、S11 权限及 S17 幂等事实 | API、表、owner、调用方、迁移、预算、复用能力和禁止依赖可定位；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S18-M1-T02 | 冻结 CrossSpaceGrant、GrantVersion、GrantScope、GrantParty 与 GrantReceipt 合同 | schema version、identity、双方、类型范围、生命周期、错误、历史和上限明确 | Done |
| PROJECT-PLATFORM-S18-M1-T03 | 设计授权、不可变版本、双方确认、撤销历史和命令回执 Flyway schema | workspace/source/target-space 复合边界、唯一性、FK、索引、清理与 owner 完整 | Done |
| PROJECT-PLATFORM-S18-M1-T04 | 实现草稿、请求、双方确认、激活、修订、暂停、撤销和归档 | 双方显式 owner/admin 同意；expected version、request hash、audit/outbox 和精确重放完整 | Done |
| PROJECT-PLATFORM-S18-M1-T05 | 实现最小类型、方向、操作和实例范围授权 | 只引用 published type/version；范围不能隐式扩张，未知/未来 schema 失败关闭 | Done |
| PROJECT-PLATFORM-S18-M1-T06 | 建立双空间当前权限与成员重校准 | grant 不创建成员或 ACL；任一方收权、停用、归档后访问立即失败关闭 | Done |
| PROJECT-PLATFORM-S18-M1-T07 | 定义既有实例、历史活动、撤销和重新授权语义 | 撤销不删除 owner 事实；新访问/副作用停止，历史按最小披露和版本解释 | Done |
| PROJECT-PLATFORM-S18-M1-T08 | 交付授权请求、双方确认、范围预览和历史 Web | 1440/1366/820、键盘、长名称、loading、空态/错误和授权来源可理解 | Done |
| PROJECT-PLATFORM-S18-M1-T09 | 接入 realtime 失效、离线输入、多标签冲突和 REST 校准 | 缓存不授权；恢复不重复确认，收权后旧类型/空间/数量不闪现 | Done |
| PROJECT-PLATFORM-S18-M1-T10 | 完成六身份、双空间组合、并发、撤销、归档、重放和最小披露测试 | 无类型/空间/实例/成员/数量泄漏、单方激活、重复版本或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S18-M1-T11 | 执行授权数、类型范围、历史、权限端口和渲染预算 | SQL/端口/内存/DOM 上界可复现；不声明生产跨团队规模或 SLO | Done |
| PROJECT-PLATFORM-S18-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明授权事实；跨空间关系、字段/状态同步和全景仍由 M2-M4 交付 | Done |

### PROJECT-PLATFORM-S18-M2 跨空间关系建立和可见性

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M2-T01 | 复核 M1 grant、S10 relation/hierarchy、S11 端点权限和未关闭阻断 | 12 项逐项可追溯；S18 不复制 relation edge/history 或类型/实例权威 | Done |
| PROJECT-PLATFORM-S18-M2-T02 | 冻结 CrossSpaceRelationPolicy、LinkIntent、EndpointReference 与 LinkReceipt 合同 | identity、方向、定义版本、双方 capability、生命周期、错误和上限明确 | Done |
| PROJECT-PLATFORM-S18-M2-T03 | 设计关系策略、建链意图、双方确认和治理回执 Flyway schema | 只保存授权/意图/版本/回执；复合边界、唯一性、索引、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S18-M2-T04 | 实现 source relate、target accept-link 与当前 grant 的原子判定 | 双方权限、类型矩阵、方向、基数和定义版本均失败关闭；无客户端过滤 | Done |
| PROJECT-PLATFORM-S18-M2-T05 | 通过 S10 canonical command 创建、读取和撤销跨空间关系 | S18 不直接写 relation 私表；并发单赢家，无单端边、幽灵边或双重历史 | Done |
| PROJECT-PLATFORM-S18-M2-T06 | 实现端点最小引用、反向可见性和 404/403 外形 | 不可见目标只返回 forbidden reference；不泄露标题、类型、状态、路径或关系数 | Done |
| PROJECT-PLATFORM-S18-M2-T07 | 实现撤销、收权、归档、删除和 definition 升级语义 | 既有边按 S10 历史保留/投影；新建链停止，不静默重写端点或方向 | Done |
| PROJECT-PLATFORM-S18-M2-T08 | 交付跨空间关系选择、确认、反向引用和治理 Web | 1440/1366/820、键盘、长名称、loading、空态/错误和双方授权来源可理解 | Done |
| PROJECT-PLATFORM-S18-M2-T09 | 接入 realtime、离线选择、多标签冲突、收权和 REST 校准 | 离线不丢意图；恢复不重复建边，旧目标/反向/计数不闪现 | Done |
| PROJECT-PLATFORM-S18-M2-T10 | 完成六身份、双空间、并发、重复边、环、撤销、收权和归档测试 | 无端点/标题/状态/路径/数量泄漏、半边、重复历史或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S18-M2-T11 | 执行关系候选、端点解析、建链端口、影响和渲染预算 | 固定夹具与上界可复现；不声明生产关系图容量、延迟或 SLO | Done |
| PROJECT-PLATFORM-S18-M2-T12 | 同步授权/关系/最小披露/审计合同并完成 M2 checkpoint | 当前事实与 M3 同步输入清楚；关系边继续由 S10 唯一持有 | Done |

### PROJECT-PLATFORM-S18-M3 单向/双向字段与状态同步

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M3-T01 | 复核 M1-M2 授权/关系、字段/流程公共命令、S03/S17 可靠执行和未关闭阻断 | 24 项逐项可追溯；同步不复制 WorkItem、字段、状态、关系或权限权威 | Done |
| PROJECT-PLATFORM-S18-M3-T02 | 冻结 SyncRule、RuleVersion、FieldMapping、StateMapping、Direction 与 Trigger 合同 | identity、版本、方向、转换、触发、错误、生命周期、保留和上限明确 | Done |
| PROJECT-PLATFORM-S18-M3-T03 | 设计同步规则、不可变版本、运行/步骤、回执、冲突和补偿 Flyway schema | 复合边界、唯一性、fencing、索引、不可变历史、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S18-M3-T04 | 实现规则草稿、校验、双方发布、启停、修订和归档 | 映射/type/capability/grant 校验完整；已发布版本不可变且运行绑定确切版本 | Done |
| PROJECT-PLATFORM-S18-M3-T05 | 实现单向字段和状态同步 | 只调用 canonical field/state/node command；expected version、权限和输入指纹可追溯 | Done |
| PROJECT-PLATFORM-S18-M3-T06 | 实现双向同步、origin/causation、循环检测和稳定去重 | 同一变化不会回响；链深、扇出和重试有界，禁止任意脚本或隐式转换 | Done |
| PROJECT-PLATFORM-S18-M3-T07 | 实现冲突分类、人工选择、自动策略、补偿、重试和死信 | 冲突不静默覆盖；补偿不伪造原事实，重放不重复已成功副作用 | Done |
| PROJECT-PLATFORM-S18-M3-T08 | 交付映射编辑、预览、运行历史、冲突和补偿 Web | 1440/1366/820、键盘、长字段、loading、空态/错误和来源版本可理解 | Done |
| PROJECT-PLATFORM-S18-M3-T09 | 接入收权/撤销、realtime、离线草稿、多标签冲突和 REST 校准 | 每步重校准双方权限；旧字段/状态/冲突数不闪现，不伪造同步成功 | Done |
| PROJECT-PLATFORM-S18-M3-T10 | 完成六身份、双空间、并发、循环、部分失败、收权、重试和补偿测试 | 无字段/状态/错误/数量泄漏、丢更新、无限回响、重复副作用或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S18-M3-T11 | 执行规则/映射/扇出、run/step、worker、冲突和渲染预算 | 固定夹具与上界可复现；不声明生产同步吞吐、延迟或 SLO | Done |
| PROJECT-PLATFORM-S18-M3-T12 | 同步规则/运行/冲突/补偿/事件合同并完成 M3 checkpoint | 当前事实与 M4 全景/治理输入清楚；S19 指标仍未实现 | Done |

### PROJECT-PLATFORM-S18-M4 跨团队全景视图、协作审计与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 36 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Done |
| PROJECT-PLATFORM-S18-M4-T02 | 冻结 CrossTeamPanorama、CollaborationSlice、AuditEntry、Health 与 Diagnostic 合同 | 来源、窗口、排序、解释、截断、版本、错误、权限和上限明确 | Done |
| PROJECT-PLATFORM-S18-M4-T03 | 设计全景偏好、可重建索引/统计和治理回执 Flyway schema | 只保存低基数派生/identity/version；复合边界、索引、过期、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S18-M4-T04 | 聚合授权、关系、同步、冲突和补偿的当前受权最小事实 | 只调用 owner 公共合同；无私表 join、重复事实、全量后过滤或跨空间旁路 | Done |
| PROJECT-PLATFORM-S18-M4-T05 | 实现跨团队全景、过滤、深链、截断和来源解释 | 隐藏空间/端点/字段不进入标题、数量、facet、游标、健康或错误外形 | Done |
| PROJECT-PLATFORM-S18-M4-T06 | 实现协作审计、授权链、关系来源、同步 lineage 和治理诊断 | 审计可追溯但不复制内容；每个结论绑定 source identity/version 与当前权限 | Done |
| PROJECT-PLATFORM-S18-M4-T07 | 实现受控暂停/恢复、撤销、冲突处理、补偿和治理回放 | 危险动作需理由、当前双方权限、expected version、request hash 和 receipt | Done |
| PROJECT-PLATFORM-S18-M4-T08 | 交付完整全景、授权/关系/同步历史、冲突与治理响应式 Web | 1440/1366/820、长名称、键盘、loading、空态/错误和来源可用 | Done |
| PROJECT-PLATFORM-S18-M4-T09 | 完成六身份、双空间、并发、收权、离线和组合规模预算验收 | 固定夹具与 SQL/端口/内存/DOM 上界可复现；无空间/关系/同步/错误泄漏 | Done |
| PROJECT-PLATFORM-S18-M4-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；双空间、六身份、关键视口和 route-final 证据 fresh | Done |
| PROJECT-PLATFORM-S18-M4-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S19 准入 | 文档只声明已实现事实；S19 继续复用当前权限、审计和有界聚合边界 | Done |
| PROJECT-PLATFORM-S18-M4-T12 | 给出 S18 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、48 Task、工作上下文和文档一致；仅无阻断时 Completed | Done |

## 6. Stage 验收

- 跨空间 grant、双方确认、撤销、历史与类型/实例范围拥有唯一 versioned authority，不创建隐式成员或 ACL。
- 跨空间关系在双方权限和最小披露下调用 S10 canonical relation command；无半边、重复边、标题/状态/路径或数量泄漏。
- 单向/双向字段和状态同步具有不可变规则版本、映射、origin/causation、循环防护、冲突、补偿、重试、死信和精确回执。
- 每次读取、建链和同步步骤都重新执行双方当前 decision/data scope；收权、撤销、归档和删除后失败关闭。
- 全景和协作审计只聚合当前受权最小事实；索引、统计、缓存、realtime 和审计投影均不授权或成为 S19 指标。
- 幂等、乐观锁、realtime、离线、多标签、长名称、1440/1366/820、双空间和六身份闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 `route-final` 无阻断。
- S18 不实现 S19 组织级度量/驾驶舱或 S20 场景模板，也不把本地预算表述为生产吞吐、容量或 SLO。

## 7. 起始点

S17 完成路线已归档，S18 在 Program revision 43 激活。当前唯一合法入口为 `PROJECT-PLATFORM-S18-M1-T01`；M1 完成前不得推进 M2，且不得提前实现 S19。
