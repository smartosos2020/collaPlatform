---
title: PROJECT-PLATFORM-S11 空间角色、工作项角色和数据权限当前执行路线
status: active
route: PROJECT-PLATFORM-S11
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 29
stage: PROJECT-PLATFORM-S11
stage_final_milestone: PROJECT-PLATFORM-S11-M5
last_code_check: 2026-07-27
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S11 空间角色、工作项角色和数据权限

## 1. Stage 目标

在 S10 关系、层级和依赖已完成并归档的基础上，为规范 WorkItem 建立分层、版本化、可解释、可审计的数据权限权威。企业 RBAC 继续负责企业治理；空间角色负责空间级管理上限；工作项角色、参与者、字段/节点/关系策略和 data scope 决定具体对象的查看、编辑、流转、删除及细粒度披露。用户 UI 只执行服务端 capability，管理员可以解释拒绝来源、处理权限申请和审计变更，但企业管理员不会因此自动取得私有内容访问。

S11 的完成标准不是给现有 `owner/admin/member/guest` 再加若干前端判断，也不是把通用 `resource_permissions` 直接套到所有工作项事实。权限定义必须进入唯一版本化 configuration snapshot；运行决策必须显式组合企业、空间、事项角色、参与者、字段、流程节点、关系端点和数据范围，并让 projection 与 execute 共享同一决策。拒绝必须最小披露且可解释；策略变更、角色移交、申请、回收、缓存失效和存量承接必须有确定语义。

## 2. 固定输入与当前事实

- S10 完成路线已归档；当前 schema 为 V100，规范 WorkItem、状态流、节点流、关系/层级均已交付，但权限上限仍主要由 ProjectSpace 的 owner/admin/member/guest 与局部参与者规则决定。
- 企业 RBAC、部门、用户组、角色分配、通用资源 ACL/申请和审计已有公共能力；它们可以作为来源或公共端口，不能与空间/事项角色压缩成同一张角色表。
- S06 的唯一草稿、校验、不可变发布、diff、compatibility、rollback 和模板链路是权限定义的唯一发布入口；不得建立 live policy 与 published snapshot 双权威。
- S07 的 WorkItem identity、绑定版本、参与者、字段投影、活动和 resolver，S08/S09 的服务端 action decision，S10 的双端关系授权与 capability 是现有运行输入；S11 不得读取其他模块私表补算身份或内容。
- `resource_permissions` 继续属于通用 permission 模块；project 可以调用公共 decision/request/audit 合同，但不得让 permission 模块反向读取 project 私表或持有 WorkItem 业务策略。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份是最低回归矩阵，不代表 S11 只有六种角色；自定义空间/事项角色和参与者来源必须使用永久 semantic key。
- hidden 字段、不可见节点、不可见关系端点和 data scope 外对象不得出现在列表、计数、错误、搜索候选、影响图、事件正文或治理详情中。
- S11 不实现 S12 个人工作台、S13 保存视图/查询 DSL、S17 自动化或 S18 跨空间同步；跨空间授权继续保持关闭。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 权限来源使用永久 semantic key 和显式优先级；任何按显示名、前端路由、最近角色或错误差异猜授权的实现均为阻断。
3. 企业 RBAC、空间角色、事项角色、参与者、字段/节点/关系策略和 data scope 必须分层建模；拒绝优先且不能以更宽上层角色绕过更窄内容边界。
4. projection、query、execute、export、resolver 和事件消费者必须调用同一服务端决策或其批量等价实现；缓存只保存可校准结果，不成为授权事实源。
5. 策略命令使用 expected version、caller-stable request ID、持久 receipt、审计和事务 outbox；失败不产生半策略、半角色或陈旧授权。
6. owner/关键职责移交、角色回收、权限申请、临时授权和存量承接必须显式、可审计、可续跑；不得静默扩大可见范围。
7. 404/403/409/422 边界稳定：不可见对象统一最小披露，已定位但动作不足才返回受控拒绝；解释 API 也不能泄露隐藏策略或对象。
8. M1-M4 使用影响范围门禁；M5 执行完整 Flyway、后端、前端、协作、架构、安全、六身份与自定义角色真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M1 | 权限来源、版本化定义与持久化底座 | S10 归档；Program revision 29 | `docs/90-reports/project-platform-s11-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S11-M2 | 创建、查看、编辑、流转和删除运行授权 | M1 | `docs/90-reports/project-platform-s11-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S11-M3 | 字段、节点、关系和数据范围细粒度权限 | M1-M2 | `docs/90-reports/project-platform-s11-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S11-M4 | 权限解释、申请、治理矩阵与存量恢复 | M1-M3 | `docs/90-reports/project-platform-s11-m4-execution-report.md` | Pending |
| PROJECT-PLATFORM-S11-M5 | 配置/成员/治理 UI、真实验收及 Stage 收口 | M1-M4 | `docs/90-reports/project-platform-s11-m5-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S11-M1 权限来源、版本化定义与持久化底座

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M1-T01 | 审计企业 RBAC、空间角色、WorkItem 参与者、字段策略、状态/节点动作、关系授权、通用 ACL 和现有 UI | 来源、调用方、数据表、缓存、事件、迁移风险和禁止依赖可定位；无未经证实的授权语义 | Pending |
| PROJECT-PLATFORM-S11-M1-T02 | 冻结 EnterpriseRole、SpaceRole、WorkItemRole、PermissionPolicy、DataScope 与 SubjectSelector 领域合同 | 永久 key、来源、优先级、allow/deny、作用域、生命周期和错误语义无歧义 | Pending |
| PROJECT-PLATFORM-S11-M1-T03 | 扩展完整 configuration snapshot 承载空间/事项角色与权限策略 | 定义进入唯一草稿/发布权威；旧 schema、无策略和未来 schema 行为明确 | Pending |
| PROJECT-PLATFORM-S11-M1-T04 | 设计角色绑定、事项角色分配、策略命令回执、解释证据和审计索引 Flyway schema | workspace/space/WorkItem/version 复合边界、唯一性、FK、索引、清理和不可变保护完整 | Pending |
| PROJECT-PLATFORM-S11-M1-T05 | 把角色/策略编辑接入 S06 草稿、DTO、canonical hash 和乐观版本 | 不建立 live policy/published 双写；跨空间、未知 subject/role/action 失败关闭 | Pending |
| PROJECT-PLATFORM-S11-M1-T06 | 实现角色继承、deny 优先、职责分离、最小 owner 和策略冲突校验 | 非法组合产生稳定 diagnostics；校验确定且不静默改写用户策略 | Pending |
| PROJECT-PLATFORM-S11-M1-T07 | 接入配置 diff、compatibility、发布、rollback 和模板 lineage | 扩权/收权、删除角色、改变 scope/subject/deny 分级稳定；阻断不可绕过 | Pending |
| PROJECT-PLATFORM-S11-M1-T08 | 冻结企业/空间/事项角色映射与系统预置 | 重复安装/升级 hash 稳定；不覆盖本地修改，不把企业管理员映射为内容 owner | Pending |
| PROJECT-PLATFORM-S11-M1-T09 | 冻结存量空间成员、WorkItem participant、字段策略和通用 ACL 的显式承接 manifest | 每类来源和保留/转换规则明确；未知来源进入失败清单，不静默扩权 | Pending |
| PROJECT-PLATFORM-S11-M1-T10 | 冻结权限 decision/explanation/request/event 与模块所有权公共合同 | payload 最小披露；消费者不得读 project/permission/identity 私表拼授权 | Pending |
| PROJECT-PLATFORM-S11-M1-T11 | 完成 schema、定义校验、草稿、发布、兼容、跨空间和私表边界自动化测试 | 空库/升级、幂等、不可变、越权、冲突、未知版本和模块隔离均通过 | Pending |
| PROJECT-PLATFORM-S11-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 文档只声明定义底座；运行授权未激活，M2 输入、证据和风险清晰 | Pending |

### PROJECT-PLATFORM-S11-M2 创建、查看、编辑、流转和删除运行授权

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M2-T01 | 复核 M1 定义、snapshot、schema、报告和未关闭阻断 | 12 项逐项可追溯；运行授权不绕过版本化策略或公共身份端口 | Pending |
| PROJECT-PLATFORM-S11-M2-T02 | 实现绑定 snapshot 驱动的 PermissionRuntimeAdapter 与 subject/context 规范化 | 只解释 WorkItem/类型绑定版本；身份、组织、组、空间角色经公共端口校准 | Pending |
| PROJECT-PLATFORM-S11-M2-T03 | 实现统一 WorkItemPermissionDecisionService 与批量等价决策 | 单项/批量结果一致；deny/来源/策略版本稳定；无 N+1 或前端补算 | Pending |
| PROJECT-PLATFORM-S11-M2-T04 | 接管 create/list/get/update/archive/restore/delete 的服务端授权 | query 与 command 同一语义；404/403 稳定；最后 owner 与危险删除失败关闭 | Pending |
| PROJECT-PLATFORM-S11-M2-T05 | 接管参与者、评论、附件、活动和分享深链的对象/子资源授权 | 子资源不能扩大对象权限；hidden 内容不进入活动、附件元数据或 resolver | Pending |
| PROJECT-PLATFORM-S11-M2-T06 | 接管 S08 状态动作与纠错/升级/backfill 授权 | `availableActions` 与 execute 共用 decision；不读取状态私表或复制 guard | Pending |
| PROJECT-PLATFORM-S11-M2-T07 | 接管 S09 task/action/recovery/compensation/upgrade/backfill 授权 | 候选、会签、owner 恢复和节点可见性由同一分层决策裁剪 | Pending |
| PROJECT-PLATFORM-S11-M2-T08 | 接管 S10 relation/hierarchy/impact/migration 授权 | 双端、方向、关系动作和治理入口逐端点校验；不可见端点零泄漏 | Pending |
| PROJECT-PLATFORM-S11-M2-T09 | 实现策略/角色变化后的缓存失效、会话校准和公共事件 | 权限收紧先失效再重读；事件最小披露、可重放且不携带策略正文 | Pending |
| PROJECT-PLATFORM-S11-M2-T10 | 交付用户 capability、批量列表裁剪和稳定错误 DTO | UI 不猜动作；列表/计数/搜索候选/深链一致；分页不因裁剪泄漏总量 | Pending |
| PROJECT-PLATFORM-S11-M2-T11 | 完成六身份、自定义角色、组/部门、并发收权、跨空间和故障原子性测试 | 无越权枚举、陈旧 capability、半角色、重复事件或企业管理员旁路 | Pending |
| PROJECT-PLATFORM-S11-M2-T12 | 执行代表性列表/详情/动作决策预算并完成 M2 checkpoint | SQL/公共端口调用上界和延迟可复现；不冒充 S13 全局查询或生产容量 | Pending |

### PROJECT-PLATFORM-S11-M3 字段、节点、关系和数据范围细粒度权限

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M3-T01 | 复核 M2 统一决策、批量裁剪、事件和未关闭阻断 | 12 项逐项可追溯；细粒度规则不建立第二 decision 或扩大对象上限 | Pending |
| PROJECT-PLATFORM-S11-M3-T02 | 实现字段 hidden/read/write/required 与对象动作的组合决策 | 字段策略不授予对象访问；projection/patch/validation/export 使用同一裁剪 | Pending |
| PROJECT-PLATFORM-S11-M3-T03 | 实现 create/detail/node form 字段策略和动态 required | hidden 零披露；required 只对可写可见字段生效；错误不暴露 field key/value | Pending |
| PROJECT-PLATFORM-S11-M3-T04 | 实现状态/节点可见、处理、转交、恢复和管理动作的细粒度策略 | 节点策略进入 snapshot；task/token/候选/历史按调用者裁剪且执行一致 | Pending |
| PROJECT-PLATFORM-S11-M3-T05 | 实现关系定义/端点/创建/撤销/层级/影响/迁移权限 | 双端 accept-link 与 source action 明确；反向/影响/计数不泄漏隐藏端点 | Pending |
| PROJECT-PLATFORM-S11-M3-T06 | 实现 owner/assignee/collaborator/watcher 与自定义事项角色解析 | 显式、字段派生、空间角色和组来源可解释；离场/禁用 subject 失败关闭 | Pending |
| PROJECT-PLATFORM-S11-M3-T07 | 实现同空间 data scope：全部、本人创建、参与、角色、字段匹配和显式集合 | 声明式 operator/operand 白名单、深度/集合硬限；无动态 SQL 或任意表达式 | Pending |
| PROJECT-PLATFORM-S11-M3-T08 | 把 data scope 接入列表、详情、resolver、relation target、层级和 impact | 所有入口结果一致；裁剪后游标/计数不泄漏，不能靠错误差异枚举 | Pending |
| PROJECT-PLATFORM-S11-M3-T09 | 实现权限安全的历史、活动、审计和配置 diff 投影 | 历史策略版本可定位但正文按当前治理权限裁剪；hidden 值永不落公共事件 | Pending |
| PROJECT-PLATFORM-S11-M3-T10 | 交付批量 field/node/relation/data-scope decision API/DTO | 上限稳定、无 N+1、无表结构泄漏；调用者不能提交服务端决策结果 | Pending |
| PROJECT-PLATFORM-S11-M3-T11 | 完成字段/节点/关系/data scope 的组合、负向、并发和性能自动化测试 | 无 hidden 字段、不可见 task/edge/ancestor、范围外计数或缓存漂移 | Pending |
| PROJECT-PLATFORM-S11-M3-T12 | 执行代表性宽策略/深层级/密集关系预算并完成 M3 checkpoint | 策略数、subject 数、节点/边硬限和延迟可复现；不实现 S13 保存视图 | Pending |

### PROJECT-PLATFORM-S11-M4 权限解释、申请、治理矩阵与存量恢复

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M4-T01 | 复核 M1-M3 定义、运行、细粒度决策和未关闭阻断 | 36 项逐项可追溯；治理入口不旁路内容授权或直接改私表 | Pending |
| PROJECT-PLATFORM-S11-M4-T02 | 实现面向用户的安全 permission explanation | 只解释可披露来源、命中层级和申请入口；不返回隐藏角色/策略/对象 | Pending |
| PROJECT-PLATFORM-S11-M4-T03 | 实现管理员治理 explanation 与可复现 decision trace | 需要治理权限和内容访问的交集；版本、subject/context、deny 来源可审计 | Pending |
| PROJECT-PLATFORM-S11-M4-T04 | 接入通用权限申请、审批、授予、拒绝、撤回和到期 | 申请不自动扩权；审批者范围、临时授权、重复请求和到期回收确定 | Pending |
| PROJECT-PLATFORM-S11-M4-T05 | 实现空间/事项角色分配、移交、批量回收和最后 owner 保护 | expected version、危险确认、原因、receipt/audit/outbox 同成同败 | Pending |
| PROJECT-PLATFORM-S11-M4-T06 | 实现策略变更预览、影响计数、冲突刷新和输入保留 | 预览逐对象受权且有硬限；不泄露标题/总量；409/timeout 不丢配置 | Pending |
| PROJECT-PLATFORM-S11-M4-T07 | 实现权限一致性扫描、dry-run、rebuild、失败清单和续跑 | 检测孤儿绑定、失效 subject、角色漂移、缓存/索引漂移；不改业务事实 | Pending |
| PROJECT-PLATFORM-S11-M4-T08 | 实现 legacy 空间成员/参与者/ACL 承接、verify 和 rollback 边界 | manifest 锚定；扩权/无法映射项失败；每项可续跑且回退不覆盖后续授权 | Pending |
| PROJECT-PLATFORM-S11-M4-T09 | 实现权限观测指标、审计查询和高风险变更告警合同 | 低基数指标、脱敏日志、稳定事件；告警不包含对象正文或 subject 隐私 | Pending |
| PROJECT-PLATFORM-S11-M4-T10 | 交付 owner/admin/enterprise governance 分层治理 API/DTO | 企业治理不等于内容可见；空间治理不授予企业角色；错误最小披露 | Pending |
| PROJECT-PLATFORM-S11-M4-T11 | 完成解释、申请、到期、移交、扫描、迁移与故障恢复自动化测试 | 无审批旁路、过期残留、最后 owner 缺口、越权 trace 或回退污染 | Pending |
| PROJECT-PLATFORM-S11-M4-T12 | 交付权限治理/恢复 runbook、同步合同并完成 M4 checkpoint | 操作步骤、停止条件、回退边界和 M5 输入清晰；不宣称 UI 闭环 | Pending |

### PROJECT-PLATFORM-S11-M5 配置/成员/治理 UI、真实验收及 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M5-T01 | 审计 M1-M4 实现、报告、迁移、边界和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S11-M5-T02 | 交付空间配置侧角色、subject、动作、字段/节点/关系和 data scope 策略 UI | 草稿/发布/兼容/rollback 清晰；冲突、未知和危险扩权即时诊断 | Pending |
| PROJECT-PLATFORM-S11-M5-T03 | 交付成员详情侧 capability、无权态、解释和申请 UI | 只展示服务端决策；拒绝不泄露对象；申请/刷新/离线保留上下文 | Pending |
| PROJECT-PLATFORM-S11-M5-T04 | 交付事项角色分配、移交、临时授权、撤回和到期 UI | 最后 owner、危险确认、expected version 与服务端拒绝可理解 | Pending |
| PROJECT-PLATFORM-S11-M5-T05 | 交付企业/空间治理矩阵、decision trace、审计和恢复 UI | 治理权限与内容可见交集正确；失败清单、续跑和回退显式 | Pending |
| PROJECT-PLATFORM-S11-M5-T06 | 完成长名称、密集策略、键盘、焦点、窄屏和无障碍交互 | 1440/1366/820 关键视口可用；矩阵与列表可替代导航；错误关联完整 | Pending |
| PROJECT-PLATFORM-S11-M5-T07 | 执行企业/空间/事项/字段/节点/关系/data scope 正向真实浏览器验收 | 允许动作、字段、任务、端点和列表与服务端 explanation 一致 | Pending |
| PROJECT-PLATFORM-S11-M5-T08 | 执行 owner/admin/member/guest/non-member/enterprise-admin 与自定义角色负向验收 | 404/403、hidden 零披露、企业管理员无旁路、收权即时生效 | Pending |
| PROJECT-PLATFORM-S11-M5-T09 | 执行并发分配/收权、申请/到期、离线、迁移/恢复真实验收 | 无双 owner、过期残留、陈旧 capability、输入丢失或回退污染 | Pending |
| PROJECT-PLATFORM-S11-M5-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和生成物门禁 | full gate 无阻断；迁移/恢复日志 fresh；mock 不冒充真实浏览器/数据库证据 | Pending |
| PROJECT-PLATFORM-S11-M5-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S12 准入 | 文档只声明已实现事实；S12 聚合不得绕过 S11 数据范围与最小披露 | Pending |
| PROJECT-PLATFORM-S11-M5-T12 | 给出 S11 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 五份报告、工作上下文、60 Task 和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 企业 RBAC、空间角色、事项角色、参与者、字段/节点/关系策略和 data scope 分层明确，定义进入唯一不可变 configuration snapshot 和发布链路。
- projection、query、execute、export、resolver 与事件消费者共享同一权限决策或批量等价实现；前端不补算 capability。
- 创建、查看、编辑、流转、删除、参与者、评论、附件、状态流、节点流、关系/层级和迁移均按绑定策略与当前 subject/context 失败关闭。
- hidden 字段、不可见节点、关系端点和 data scope 外对象不进入列表、计数、游标、错误、事件、审计正文或治理解释。
- 权限申请、临时授权、到期、角色移交/回收、影响预览、一致性扫描和存量承接显式、可审计、可续跑、可校验。
- 企业治理、空间治理和内容访问边界独立；enterprise-admin 不自动取得私有空间/WorkItem 内容。
- 配置、成员和治理 UI 完成真实闭环；六身份、自定义角色、并发收权、离线、窄屏和真实 PostgreSQL/Flyway 证据通过。
- S11 不实现 S12 工作台、S13 保存视图/查询 DSL、S17 自动化或 S18 跨空间同步，也不把本地预算表述为生产容量。

## 7. 起始点

当前从 `PROJECT-PLATFORM-S11-M1-T01` 开始。激活只建立执行授权和验收合同，不代表任何 S11 schema、策略、API、迁移或 UI 已实现。
