---
title: PROJECT-PLATFORM-S20 研发、市场、HR 和交付场景模板当前执行路线
status: completed
route: PROJECT-PLATFORM-S20
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 48
stage: PROJECT-PLATFORM-S20
stage_final_milestone: PROJECT-PLATFORM-S20-M5
last_code_check: 2026-07-29
archived_at: 2026-07-29
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S20 研发、市场、HR 和交付场景模板

## 1. Stage 目标

在 S02-S19 的空间、动态配置、统一 WorkItem、流程、关系、权限、查询、计划、资源、自动化、跨空间协作和治理能力完成的基础上，交付研发、市场活动、HR 招聘和客户交付四类可安装场景模板，并完成模板差异化、升级、解绑和真实场景验收。

S20 只拥有场景模板目录、不可变版本、多类型组件清单、安装计划/运行/步骤、差异/升级决策和精确命令回执。类型、字段、布局、流程、关系、权限、视图、计划、资源、自动化、指标和 WorkItem 事实仍由既有 owner 持有；场景安装必须调用公共配置/发布合同，不得直接写 owner 私表或把模板升级静默覆盖本地调整。

## 2. 固定输入与当前事实

- S19 完成路线已归档；当前 schema 为 V134，指标语义、看板、风险和治理报表均已交付并通过真实隔离 route-final。
- S03-S06 已提供类型预置、完整配置快照、草稿/发布、单类型模板安装、三方合并、升级与解绑合同；S20 在其上编排多类型场景包。
- S07-S10 分别拥有 canonical WorkItem、状态/节点流程与关系/层级事实；场景清单只保存稳定配置 identity/key/version。
- S11 当前 decision/data scope、S12-S19 的查询/视图/计划/资源/自动化/同步/指标公共合同持续生效；模板不能创建隐式成员、ACL、内容访问或企业角色旁路。
- 安装、重试、升级、解绑和回滚使用 caller-stable request ID、expected version、request hash、不可变 receipt、audit/outbox 和逐步状态。
- 本地覆盖与上游版本使用三方差异；未知 schema、缺失 owner capability、冲突未决、收权、空间停用和越权动作失败关闭。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份，以及空空间、已有配置空间、本地调整、离线和多标签组合，构成最低真实回归矩阵。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. ScenarioTemplate、TemplateVersion、ComponentManifest、InstallPlan、InstallRun、InstallStep、UpgradeDiff 和 CommandReceipt 使用稳定 identity、不可变版本和显式生命周期。
3. 场景清单只能引用注册 owner capability 与公开模板 key/version；禁止 SQL、脚本、反射、任意表达式、私表名称和内容样本。
4. 安装逐组件预检、执行、验证和记账；重放不得重复创建类型、发布版本、关系、视图、规则或指标配置。
5. 本地差异必须保留；升级通过 base/upstream/local 三方比较生成冲突，未解决冲突不得发布或标记成功。
6. 模板目录和预览不授权；每次安装/升级/解绑重新校准当前 manager、空间状态及各 owner capability。
7. 失败步骤保留安全诊断和补偿建议，不保存凭据、成员、内容或隐藏对象；部分失败不得伪造成已安装。
8. UI 明确显示来源版本、组件范围、本地差异、冲突、运行步骤和可恢复动作；离线只保留输入，不伪造成功。
9. M1-M4 使用影响范围门禁；M5 执行完整 Flyway、后端、前端、协作、架构、安全和六身份真实隔离 route-final。
10. S20 不删除旧 project/issue 模型、不切换 legacy 写、不执行真人团队试用；这些仅属于 S21。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S20-M1 | 研发项目模板 | S19 归档；Program revision 47 | `docs/90-reports/project-platform-s20-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S20-M2 | 市场活动模板 | M1 | `docs/90-reports/project-platform-s20-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S20-M3 | HR 招聘模板 | M1-M2 | `docs/90-reports/project-platform-s20-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S20-M4 | 客户交付模板 | M1-M3 | `docs/90-reports/project-platform-s20-m4-execution-report.md` | Completed |
| PROJECT-PLATFORM-S20-M5 | 模板安装、差异化、升级验证与 Stage 收口 | M1-M4 | `docs/90-reports/project-platform-s20-m5-execution-report.md` | Completed |

## 5. 详细任务

### PROJECT-PLATFORM-S20-M1 研发项目模板

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S20-M1-T01 | 审计 S03-S19 配置模板、类型、流程、关系、权限、查询、计划、自动化和指标公共合同 | API、owner、schema、版本、预算、复用能力和禁止依赖可定位；不依赖旧会话结论 | Done |
| PROJECT-PLATFORM-S20-M1-T02 | 冻结 ScenarioTemplate、TemplateVersion、ComponentManifest 与 ValidationResult 合同 | identity、schema version、场景、组件、依赖、能力、错误、生命周期和上限明确 | Done |
| PROJECT-PLATFORM-S20-M1-T03 | 设计场景目录、不可变版本、组件清单、安装记录、步骤和命令回执 Flyway schema | 复合边界、唯一性、FK、索引、不可变历史、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S20-M1-T04 | 建立注册组件类型、依赖拓扑、稳定 key 和确定性清单哈希 | 未知组件/未来 schema/环/重复 key/越界扇出失败关闭；同版本可重复解释 | Done |
| PROJECT-PLATFORM-S20-M1-T05 | 定义项目、需求、任务、缺陷、版本和迭代类型配置 | 字段、布局、状态/节点流程、角色和动作完整且只引用公开配置合同 | Done |
| PROJECT-PLATFORM-S20-M1-T06 | 定义研发层级、依赖、缺陷关联、版本/迭代关系和约束 | 方向、端点类型、基数、循环与删除语义明确；关系事实仍由 S10 持有 | Done |
| PROJECT-PLATFORM-S20-M1-T07 | 定义研发查询、看板、计划、自动化、风险和度量组件 | 只引用已发布公共能力；查询/规则/指标不含私表、脚本或权限快照 | Done |
| PROJECT-PLATFORM-S20-M1-T08 | 交付研发模板目录、组件预览、依赖解释和响应式 Web | 1440/1366/820、键盘、长名称、loading、空态/错误和来源版本可理解 | Done |
| PROJECT-PLATFORM-S20-M1-T09 | 接入 realtime 失效、离线预览、多标签和 REST 校准 | 缓存不授权；离线不安装，恢复后旧目录/组件/版本不闪现 | Done |
| PROJECT-PLATFORM-S20-M1-T10 | 完成六身份、清单重放、未知组件、环、收权和最小披露测试 | 无成员/内容/隐藏类型/数量泄漏、重复版本或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S20-M1-T11 | 执行清单/组件/依赖、解析端口、SQL/内存和渲染预算 | 固定夹具与上界可复现；不声明生产项目规模、安装吞吐或 SLO | Done |
| PROJECT-PLATFORM-S20-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 只声明研发模板目录事实；市场、HR、交付和统一安装仍由 M2-M5 交付 | Done |

### PROJECT-PLATFORM-S20-M2 市场活动模板

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S20-M2-T01 | 复核 M1 场景合同、市场需求与未关闭阻断 | 12 项逐项可追溯；市场模板不复制研发配置或建立新权限权威 | Done |
| PROJECT-PLATFORM-S20-M2-T02 | 冻结 Campaign、Content、Asset、Channel、Placement 与 Review 组件语义 | 类型、字段、流程、关系、角色、状态、来源和上限明确 | Done |
| PROJECT-PLATFORM-S20-M2-T03 | 定义活动、内容、素材、渠道、投放和复盘类型配置 | 完整 snapshot 可校验、可版本化；附件/文件只保存公共引用 | Done |
| PROJECT-PLATFORM-S20-M2-T04 | 定义内容评审、素材审批、渠道发布和活动关闭流程 | 轻量/节点流边界明确；审批、权限和副作用调用 owner 公共合同 | Done |
| PROJECT-PLATFORM-S20-M2-T05 | 定义活动-内容-素材-渠道-投放关系与可见性 | 端点、方向、基数、归档和隐藏引用语义完整；无关系私表写入 | Done |
| PROJECT-PLATFORM-S20-M2-T06 | 定义市场日历、列表、看板、保存视图和复盘面板 | 查询逐次受权；隐藏内容不进入 facet、计数、游标或空态 | Done |
| PROJECT-PLATFORM-S20-M2-T07 | 定义受控提醒、评审通知、渠道检查和复盘指标引用 | 只使用注册自动化 action 和指标语义；禁止任意 webhook/脚本或凭据 | Done |
| PROJECT-PLATFORM-S20-M2-T08 | 交付市场模板预览、流程/关系图和响应式 Web | 1440/1366/820、键盘、长渠道名、loading、空态/错误可用 | Done |
| PROJECT-PLATFORM-S20-M2-T09 | 接入目录版本失效、离线、多标签和 REST 校准 | 离线不安装或发布；恢复不重复写入或闪现隐藏内容 | Done |
| PROJECT-PLATFORM-S20-M2-T10 | 完成六身份、附件引用、流程、关系、收权和重放测试 | 无内容/文件/渠道/数量泄漏、重复副作用或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S20-M2-T11 | 执行类型/关系/视图/自动化/指标组件和渲染预算 | 固定夹具与上界可复现；不声明生产投放规模、外部可用性或 SLO | Done |
| PROJECT-PLATFORM-S20-M2-T12 | 同步市场场景合同并完成 M2 checkpoint | 当前事实与 HR 模板输入清楚；不提前实现统一安装/升级 | Done |

### PROJECT-PLATFORM-S20-M3 HR 招聘模板

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S20-M3-T01 | 复核 M1-M2 场景合同、HR 隐私边界和未关闭阻断 | 24 项逐项可追溯；候选人信息不进入模板目录、错误或治理旁路 | Done |
| PROJECT-PLATFORM-S20-M3-T02 | 冻结 HiringPlan、Position、Candidate、Interview、Offer 与 Onboarding 语义 | 类型、字段分类、流程、关系、角色、保留、错误和上限明确 | Done |
| PROJECT-PLATFORM-S20-M3-T03 | 定义招聘计划、职位、候选人、面试、Offer 和入职类型配置 | 敏感字段默认受限；完整 snapshot 可校验、版本化和解释 | Done |
| PROJECT-PLATFORM-S20-M3-T04 | 定义职位审批、候选人阶段、面试会签、Offer 和入职流程 | 节点负责人和字段权限分离；流程不扩大候选人可见范围 | Done |
| PROJECT-PLATFORM-S20-M3-T05 | 定义计划-职位-候选人-面试-入职关系与最小引用 | 隐藏候选人只返回 forbidden reference；无姓名、评价、数量泄漏 | Done |
| PROJECT-PLATFORM-S20-M3-T06 | 定义招聘看板、面试日历、待办、保存视图和受限指标 | 服务端当前 data scope 过滤；禁止个人排名、面试官绩效和隐式评分 | Done |
| PROJECT-PLATFORM-S20-M3-T07 | 定义面试提醒、会签通知、Offer 到期和入职检查自动化 | 只使用注册 action；通知接收者在执行时重新校准当前权限 | Done |
| PROJECT-PLATFORM-S20-M3-T08 | 交付 HR 模板预览、敏感字段说明和响应式 Web | 1440/1366/820、键盘、长名称、loading、空态/错误和隐私解释可用 | Done |
| PROJECT-PLATFORM-S20-M3-T09 | 接入收权、realtime、离线、多标签和 REST 校准 | 收权后旧候选人组件/计数不闪现；离线不安装或发布 | Done |
| PROJECT-PLATFORM-S20-M3-T10 | 完成六身份、字段权限、节点职责、收权、归档和重放测试 | 无候选人/评价/成员/数量泄漏、重复通知或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S20-M3-T11 | 执行敏感字段/流程/关系/查询/自动化与渲染预算 | 固定夹具与上界可复现；不声明生产招聘规模、评分准确率或 SLO | Done |
| PROJECT-PLATFORM-S20-M3-T12 | 同步 HR 隐私和场景合同并完成 M3 checkpoint | 当前事实与客户交付模板输入清楚；不提前实现 S21 迁移/试用 | Done |

### PROJECT-PLATFORM-S20-M4 客户交付模板

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S20-M4-T01 | 复核 M1-M3 场景合同、交付/验收公共能力和未关闭阻断 | 36 项逐项可追溯；交付模板不复制计划、风险、文件或验收权威 | Done |
| PROJECT-PLATFORM-S20-M4-T02 | 冻结 DeliveryProject、Task、Risk、Deliverable、Review 与 Acceptance 语义 | 类型、字段、流程、关系、角色、证据、错误和上限明确 | Done |
| PROJECT-PLATFORM-S20-M4-T03 | 定义交付项目、任务、风险、交付物、评审和验收类型配置 | 完整 snapshot 可校验、版本化；文件和验收只保存公共引用 | Done |
| PROJECT-PLATFORM-S20-M4-T04 | 定义交付阶段、里程碑、评审、整改、签署和关闭流程 | 状态/节点流与 S15 计划/交付评审边界明确；无第二事实 | Done |
| PROJECT-PLATFORM-S20-M4-T05 | 定义项目-任务-风险-交付物-验收关系与追溯 | 端点、方向、基数、版本和归档语义完整；隐藏引用失败关闭 | Done |
| PROJECT-PLATFORM-S20-M4-T06 | 定义计划/甘特/风险/交付台账/审计和治理视图 | 只组合 owner 公共响应；无私表 join、全量后过滤或内容旁路 | Done |
| PROJECT-PLATFORM-S20-M4-T07 | 定义到期提醒、评审通知、风险升级和验收检查自动化 | 受控 action、精确 receipt 和当前权限完整；失败不伪造交付成功 | Done |
| PROJECT-PLATFORM-S20-M4-T08 | 交付客户交付模板预览、追溯图和响应式 Web | 1440/1366/820、键盘、长名称、loading、空态/错误和来源可用 | Done |
| PROJECT-PLATFORM-S20-M4-T09 | 接入收权、离线、多标签、realtime 和 REST 校准 | 离线不安装/签署；收权后旧交付物、风险或计数不闪现 | Done |
| PROJECT-PLATFORM-S20-M4-T10 | 完成六身份、文件/验收引用、收权、部分失败和重放测试 | 无客户内容/证据/成员/数量泄漏、重复签署或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S20-M4-T11 | 执行类型/流程/关系/视图/自动化/治理和渲染预算 | 固定夹具与上界可复现；不声明生产项目规模、交付时效或 SLO | Done |
| PROJECT-PLATFORM-S20-M4-T12 | 同步交付场景合同并完成 M4 checkpoint | 四类场景目录完整；统一安装、差异化和升级由 M5 收口 | Done |

### PROJECT-PLATFORM-S20-M5 模板安装、差异化、升级验证与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S20-M5-T01 | 审计 M1-M4 实现、报告、清单、边界和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Done |
| PROJECT-PLATFORM-S20-M5-T02 | 冻结 InstallPlan、InstallRun、InstallStep、UpgradeDiff、Conflict 与 Receipt 合同 | 预检、顺序、状态、版本、重试、补偿、冲突、错误和上限明确 | Done |
| PROJECT-PLATFORM-S20-M5-T03 | 完成安装运行/步骤、差异、升级决策和精确命令回执 schema | 复合边界、fencing、幂等、不可变历史、索引、清理和 owner 完整 | Done |
| PROJECT-PLATFORM-S20-M5-T04 | 实现四类模板预检、依赖拓扑、安装计划和 dry-run | 缺失 capability、未知版本、key 冲突、收权和空间停用在写前失败关闭 | Done |
| PROJECT-PLATFORM-S20-M5-T05 | 通过公共 owner 服务执行多类型配置安装与逐步验证 | 不直接写 owner 私表；每步绑定 exact source/target version，重放无重复事实 | Done |
| PROJECT-PLATFORM-S20-M5-T06 | 实现本地调整、base/upstream/local 差异、冲突解决和升级 | 本地修改不被静默覆盖；未决冲突不发布，升级结果可重复解释 | Done |
| PROJECT-PLATFORM-S20-M5-T07 | 实现部分失败重试、受控解绑、补偿建议和历史 | 已成功 owner 事实不伪删除；恢复从安全步骤继续且 receipt 精确 | Done |
| PROJECT-PLATFORM-S20-M5-T08 | 交付目录/预检/安装/步骤/差异/冲突/升级完整响应式 Web | 1440/1366/820、键盘、长名称、loading、空态/错误和恢复动作可用 | Done |
| PROJECT-PLATFORM-S20-M5-T09 | 完成四场景、六身份、已有配置、本地差异、离线和组合预算验收 | 无内容/成员/隐藏配置/数量泄漏、重复安装、丢失覆盖或 enterprise 旁路 | Done |
| PROJECT-PLATFORM-S20-M5-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和真实隔离浏览器门禁 | full gate 无阻断；四场景、六身份、关键视口和 route-final 证据 fresh | Done |
| PROJECT-PLATFORM-S20-M5-T11 | 同步当前架构、Program、专项索引、模块/对象/事件合同并复核 S21 准入 | 文档只声明已实现事实；S21 旧模型退出和真人试用仍未实现 | Done |
| PROJECT-PLATFORM-S20-M5-T12 | 给出 S20 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 五份报告、60 Task、工作上下文和文档一致；仅无阻断时 Completed | Done |

## 6. Stage 验收

- 研发、市场、HR 和交付四类模板具有版本化清单、稳定组件 key、依赖拓扑和来源解释。
- 四类模板可预检、安装、重放、差异化、升级、冲突解决和解绑；本地调整不被静默覆盖。
- 安装只调用配置、流程、关系、权限、视图、计划、资源、自动化、指标等 owner 公共合同，不写私表或创建第二权威。
- 每次安装/升级/解绑重新校准当前权限；缓存、目录、预览、运行历史和统计均不授权。
- 四场景、六身份、既有配置、本地覆盖、并发、离线、多标签、1440/1366/820 闭环通过。
- 完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全与真实隔离浏览器 route-final 无阻断。
- S20 不删除旧 project/issue 模型、不执行全量迁移或真人团队试用；这些只属于 S21。

## 7. 起始点

S20-M1-M5、60 个 Task、V135-V137 和真实隔离 route-final 已完成，Go S21 成立。Program revision 48 的 `current_stage` 已置 `none`；下一步只能进入独立 archive-only 工作循环，归档 S20 后再激活 S21。S21 的旧模型退出、全量迁移与真人试用尚未实现。
