---
title: PROJECT-PLATFORM-S05 表单、详情页布局和字段访问当前执行路线
status: active
route: PROJECT-PLATFORM-S05
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 17
stage: PROJECT-PLATFORM-S05
stage_final_milestone: PROJECT-PLATFORM-S05-M5
last_code_check: 2026-07-25
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S05 表单、详情页布局和字段访问

## 1. Stage 目标

在 S03 工作项类型定义和 S04 动态字段目录基础上，交付新建表单与详情页的独立布局图、稳定布局节点、条件显示、字段访问策略、空间配置编辑器和可复用渲染器。所有配置继续属于待发布配置图，复用 S04 的字段定义、永久 key、规范 hash、aggregate version、幂等、审计、复合隔离和最小披露合同。

S05 不创建规范 WorkItem 实例、字段值或 legacy 迁移，不修改已发布 v1，不切换 current version，也不实现 S06 的 draft/publish/diff/rollback/template 流水线。管理员预览和用户侧只读样本只能使用合成上下文，不得冒充正式工作项运行闭环。

## 2. 固定输入与当前事实

- 上一业务 Stage：`PROJECT-PLATFORM-S04` 已完成并归档，交付 V064/V065 字段定义与选项、字段类型注册、结构化规则、复杂引用、空间配置 UI 和 120 字段/2400 选项目录预算。
- 平台底座：`PLATFORM-SCALE-S01-S04` 已交付模块边界、table owner、独立 API/Worker/Gateway、可靠事件消费、双 Gateway/双 collaboration 和客户端事实校准；`PLATFORM-SCALE-S05-M1` 已完成容量验证工具后暂停。
- 当前 schema：项目空间、成员、类型定义、不可变首发骨架版本、字段定义、字段选项和命令回执已存在；布局、布局节点和字段访问策略表尚不存在。
- 当前前端：空间配置页已有类型和字段配置入口；尚无布局编辑器、访问策略编辑器或共享表单渲染器。
- 目标架构：执行 `docs/01-architecture/project-platform-target-architecture.md` 第 21、22 节；布局只引用 `fieldId + fieldKey`，不复制字段事实。
- 边界基线：project/shared P0、foreign write 和新增未批准跨 owner 访问保持 0；S05 不能恢复其他模块私有 infrastructure 依赖。
- 容量边界：本 Stage 只验证配置目录、渲染和策略求值预算，不发布生产容量、60 分钟/8 小时长稳或基础设施 HA 结论。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、Acceptance Evidence 和执行报告行。
2. 布局图分 `create` 与 `detail` 两类，节点使用永久 ID 和稳定 key；重排、改名或移动不得改变节点身份。
3. field control 只保存同 workspace/space/type 的 `fieldId + fieldKey`；字段名称、类型、选项和规则始终从 S04 字段目录读取。
4. section、tab、column、field、summary 是 S05 可执行控件；relation 控件只冻结可扩展 schema，不提前实现 S10 关系运行能力。
5. 条件显示只控制渲染；`read/write/required/hidden` 必须由服务端策略求值，客户端隐藏不能授予权限。
6. 所有写命令必须具备复合隔离、aggregate version、request id、规范 request hash、幂等回放、审计/outbox 和并发冲突。
7. 缺失、停用、retired、跨类型或 key 不一致字段必须产生可操作诊断，禁止静默删除、自动重绑或泄露不可见字段。
8. 预览上下文必须显式标记 synthetic，不写业务表、不创建平台对象、不触发正式通知/搜索/实时副作用。
9. M1-M4 使用影响范围验证；M5 执行后端、V001 至最新迁移、前端、collaboration、工作台、安全、真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M1 | 布局与字段访问配置持久化、领域和 API 合同 | S04 归档；Program revision 17 | `docs/90-reports/project-platform-s05-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S05-M2 | 布局图编辑、条件显示和共享渲染器 | M1 | `docs/90-reports/project-platform-s05-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S05-M3 | 服务端字段访问策略与最小披露 | M1-M2 | `docs/90-reports/project-platform-s05-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S05-M4 | 配置预览、用户形态、可访问性与规模收口 | M1-M3 | `docs/90-reports/project-platform-s05-m4-execution-report.md` | Pending |
| PROJECT-PLATFORM-S05-M5 | Stage 评审、route-final 与 S06 准入 | M1-M4 | `docs/90-reports/project-platform-s05-m5-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S05-M1 布局与字段访问配置持久化、领域和 API 合同

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M1-T01 | 审计 S03/S04 类型、字段、命令回执、授权、API、UI、测试和 V001-V069 schema 事实 | 所有可复用合同、缺口、owner、跨模块依赖和禁止提前实现项可定位到代码、表与测试 | Pending |
| PROJECT-PLATFORM-S05-M1-T02 | 冻结 create/detail 布局图、节点类型、永久标识、访问策略和诊断领域合同 | 节点身份、父子关系、顺序、字段引用、条件、策略优先级和错误语义无歧义并同步目标架构 | Pending |
| PROJECT-PLATFORM-S05-M1-T03 | 设计并落地布局、布局节点、字段访问策略和命令回执 Flyway schema | 表由 project owner 唯一拥有；复合 workspace/space/type 外键、唯一键、检查约束、索引和不可变身份完整 | Pending |
| PROJECT-PLATFORM-S05-M1-T04 | 实现布局图、节点、条件、字段访问策略和诊断领域模型 | 模型不复制字段权威事实；create/detail 独立；未知节点/条件/策略版本明确拒绝 | Pending |
| PROJECT-PLATFORM-S05-M1-T05 | 实现 Repository 与规范查询、保存、重排和状态读取 | 图读取顺序稳定且无 N+1；所有读写带 workspace/space/type 边界；不存在跨 owner 私表访问 | Pending |
| PROJECT-PLATFORM-S05-M1-T06 | 实现布局图规范序列化、hash、结构校验和限制预算 | 相同语义产生相同 hash；循环、孤儿、重复 key、非法深度/列数/节点数和不兼容控件自动拒绝 | Pending |
| PROJECT-PLATFORM-S05-M1-T07 | 实现 fieldId + fieldKey 同域引用校验与失效诊断 | 跨 workspace/space/type、key 不一致、disabled/retired/缺失字段均拒绝或返回明确诊断，绝不静默重绑 | Pending |
| PROJECT-PLATFORM-S05-M1-T08 | 实现布局配置动作策略与空间角色授权 | owner/admin 可配置，member/guest/non-member/enterprise admin 按合同最小披露；最后 owner 与空间状态约束不回归 | Pending |
| PROJECT-PLATFORM-S05-M1-T09 | 实现 aggregate version、request id、幂等回放、并发冲突、审计和 outbox | 同请求同结果、异载荷冲突、旧版本拒绝；审计不泄露隐藏字段配置，事件 envelope 符合公共合同 | Pending |
| PROJECT-PLATFORM-S05-M1-T10 | 实现空间配置布局/策略 DTO、Controller、异常映射和 availableActions | API 路径、入出参、错误码、最小披露和动作投影稳定；无权用户不能通过 ID 枚举配置 | Pending |
| PROJECT-PLATFORM-S05-M1-T11 | 完成空库/升级迁移、Repository、服务、API、授权、并发和隔离验证及 M1 checkpoint | V001 至最新和 V065 升级均可重复；目标测试、架构边界、工作台与执行报告通过 | Pending |

### PROJECT-PLATFORM-S05-M2 布局图编辑、条件显示和共享渲染器

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M2-T01 | 实现 create/detail 布局配置聚合读取与独立保存服务 | 两类图可分别创建、读取和更新，共享同一字段目录但互不覆盖；响应包含 hash、版本和诊断 | Pending |
| PROJECT-PLATFORM-S05-M2-T02 | 实现 section、tab、column、field 和 summary 节点命令 | 每类节点的父级、子级、数量、配置和可移动范围受 schema 校验；永久 ID/key 在编辑中不变 | Pending |
| PROJECT-PLATFORM-S05-M2-T03 | 实现节点新增、复制、移动、重排、配置更新和删除的原子图命令 | 任一失败不产生半图；删除含引用节点需显式确认；并发冲突返回最新版本而非覆盖 | Pending |
| PROJECT-PLATFORM-S05-M2-T04 | 冻结并实现版本化条件显示 DSL 与安全求值器 | 支持明确的字段/上下文比较和 all/any/not 组合；无任意代码执行、隐式网络/数据库查询或类型猜测 | Pending |
| PROJECT-PLATFORM-S05-M2-T05 | 实现条件引用闭包、循环/不可见依赖和失效字段诊断 | 条件只引用允许上下文与同类型字段；循环、隐藏依赖、退役字段和类型不兼容可定位到具体节点 | Pending |
| PROJECT-PLATFORM-S05-M2-T06 | 建立前端布局/策略 API client、query keys、类型与错误映射 | 与后端 DTO 一致；缓存按 workspace/space/type/layoutKind 隔离；冲突和最小披露错误可区分 | Pending |
| PROJECT-PLATFORM-S05-M2-T07 | 在空间配置入口增加“页面布局”导航、create/detail 分段和状态摘要 | 入口只对有配置权限角色显示；返回类型/字段列表保留选择；无权身份不出现伪可用按钮 | Pending |
| PROJECT-PLATFORM-S05-M2-T08 | 实现紧凑的布局树/画布、控件面板和属性面板 | section/tab/column/field/summary 层级清楚，选中与拖放稳定，长名称和 1366px 桌面不挤压 | Pending |
| PROJECT-PLATFORM-S05-M2-T09 | 实现节点添加、复制、移动、删除、键盘操作和并发冲突恢复 | UI 命令与原子后端命令一致；失败保留本地意图并可刷新重试，不以重载掩盖数据覆盖 | Pending |
| PROJECT-PLATFORM-S05-M2-T10 | 实现条件编辑器、字段选择、类型化操作符和即时诊断 | 只展示合法字段/操作符；错误定位到条件分支；条件关闭不会修改服务端访问策略 | Pending |
| PROJECT-PLATFORM-S05-M2-T11 | 建立共享表单渲染器和字段控件注册边界 | 渲染器消费布局 + 字段目录 + 访问投影，不复制类型逻辑；未知控件显示安全诊断而非崩溃 | Pending |
| PROJECT-PLATFORM-S05-M2-T12 | 完成图命令、条件 DSL、编辑器、渲染器和 create/detail 隔离验证及 M2 checkpoint | 单元/集成、前端 lint/build 和真实隔离管理员配置浏览器流程通过；执行报告无阻断 Gap | Pending |

### PROJECT-PLATFORM-S05-M3 服务端字段访问策略与最小披露

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M3-T01 | 冻结字段 `read/write/required/hidden` 策略语义、优先级和求值上下文 | deny/hidden 优先级、required 与 write 关系、默认策略、空间角色和合成状态上下文无冲突 | Pending |
| PROJECT-PLATFORM-S05-M3-T02 | 实现版本化字段访问策略 schema、规范化和静态校验 | 策略只能引用允许角色、上下文和同类型字段；未知版本、空授权、冲突规则和越权默认值被拒绝 | Pending |
| PROJECT-PLATFORM-S05-M3-T03 | 实现服务端访问策略求值器和可解释决策链 | 相同输入确定输出；每个结果可解释规则来源；hidden 不返回字段标题、配置、选项或条件值 | Pending |
| PROJECT-PLATFORM-S05-M3-T04 | 将空间成员资格、空间角色、空间/类型/字段状态接入求值上下文 | owner/admin/member/guest/non-member/enterprise admin 六身份结果符合分层权限，企业管理员不自动获得内容访问 | Pending |
| PROJECT-PLATFORM-S05-M3-T05 | 实现布局读取过滤与诊断脱敏 | 不可读字段节点从有效图移除或变为安全占位且结构仍合法；诊断不泄露隐藏字段身份 | Pending |
| PROJECT-PLATFORM-S05-M3-T06 | 实现字段策略配置 API、availableActions 和伪造写入服务端拒绝 | 无权角色即使直接调用 API、篡改 fieldId/role/condition 或省略 UI 限制也被一致拒绝并审计 | Pending |
| PROJECT-PLATFORM-S05-M3-T07 | 实现 synthetic preview context API 与严格非持久化边界 | 预览可切换合法角色/状态/字段样本；请求不写业务表、不创建实例、不触发正式 outbox/通知/搜索 | Pending |
| PROJECT-PLATFORM-S05-M3-T08 | 实现前端字段访问策略编辑器和规则冲突提示 | 策略控件按权限启用；read/write/required/hidden 关系清楚；危险收窄需确认且不暴露不可见角色数据 | Pending |
| PROJECT-PLATFORM-S05-M3-T09 | 将访问投影接入共享渲染器的隐藏、只读、可写和必填状态 | UI 只消费服务端决策，不自行扩权；禁用控件、必填标识、错误摘要和提交候选保持一致 | Pending |
| PROJECT-PLATFORM-S05-M3-T10 | 覆盖六身份、空间状态、字段状态、条件组合、最小披露和直接 API 负向测试 | 权限矩阵正反例完整；跨 workspace/space/type、停用成员和企业管理员越权均为零泄露 | Pending |
| PROJECT-PLATFORM-S05-M3-T11 | 完成策略性能、规范 hash、并发、幂等、安全扫描和 M3 checkpoint | 120 字段策略求值在冻结预算内且结果确定；目标后端、前端和真实隔离浏览器权限流程通过 | Pending |

### PROJECT-PLATFORM-S05-M4 配置预览、用户形态、可访问性与规模收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M4-T01 | 建立布局配置集合读模型，组合类型、字段、布局、策略、诊断和 availableActions | 一次读取可稳定驱动编辑与预览；数据来源、版本、hash 和 synthetic 标识清楚，无 N+1 | Pending |
| PROJECT-PLATFORM-S05-M4-T02 | 完成管理员 create/detail 双模式预览和角色/状态上下文切换 | 预览与保存配置使用同一渲染器；上下文切换不写事实；当前角色、条件和字段状态可解释 | Pending |
| PROJECT-PLATFORM-S05-M4-T03 | 建立用户侧只读布局样本入口与未来 S07 运行时组件边界 | 用户入口只展示有权类型和 synthetic preview，不提供伪创建/伪保存；组件 API 可由 S07 传入真实实例值 | Pending |
| PROJECT-PLATFORM-S05-M4-T04 | 完成文本、富文本、数字、布尔、单/多选、人员、日期、区间、URL、附件、引用和 computed 控件映射 | 每种 S04 字段类型有明确编辑/只读/不支持状态；引用和附件不提前创建正式对象或上传事实 | Pending |
| PROJECT-PLATFORM-S05-M4-T05 | 完成条件变化、隐藏字段清值策略和错误摘要的预览行为 | 条件切换结果确定；隐藏不等于删除；潜在清值需显式提示且预览不修改持久配置或样本 | Pending |
| PROJECT-PLATFORM-S05-M4-T06 | 完成失效字段、非法布局、未知控件和过期版本的可操作诊断 UI | 管理员可定位并修复具体节点；普通用户只见最小安全提示；系统不自动删除或重绑 | Pending |
| PROJECT-PLATFORM-S05-M4-T07 | 完成键盘导航、焦点管理、标签关联、错误朗读和对比度 | 编辑器和预览核心路径无需鼠标可操作；无焦点陷阱、无重复标签、状态变化可被辅助技术感知 | Pending |
| PROJECT-PLATFORM-S05-M4-T08 | 完成 1366/1440 桌面和窄屏响应式、内部滚动与文本溢出 | 页面主体不横向崩坏；配置面板按最小宽度重排；滚动发生在内容块且高度足够时不显示无效滚动条 | Pending |
| PROJECT-PLATFORM-S05-M4-T09 | 完成加载、空状态、保存中、冲突、离线/重试和错误恢复 | 状态不引发布局跳动或丢失选择；重复提交受幂等保护；冲突可比较最新版本后重新应用 | Pending |
| PROJECT-PLATFORM-S05-M4-T10 | 执行 120 字段、2400 选项、深层分组和复杂条件的配置/渲染预算 | 列表、读取、编辑、策略求值和预览达到冻结预算；慢点有可复算数据，不冒充 S05 平台容量承诺 | Pending |
| PROJECT-PLATFORM-S05-M4-T11 | 执行 owner/admin/member/guest/non-member/enterprise admin 真实隔离浏览器矩阵 | 配置、预览、只读样本、最小披露、停用/退役字段和直接 URL/API 负例均使用真实后端与隔离数据 | Pending |
| PROJECT-PLATFORM-S05-M4-T12 | 完成前端 lint/build、目标后端、可访问性、响应式、证据闭包和 M4 checkpoint | 自动与真实浏览器证据 fresh 可追溯；无 mock 冒充真实闭环；执行报告无验收阻断项 | Pending |

### PROJECT-PLATFORM-S05-M5 Stage 评审、route-final 与 S06 准入

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M5-T01 | 逐项复核 M1-M4 的 46 项实现任务、Verification Contract、Acceptance Evidence 和 Gap | 每项任务状态与代码、测试、浏览器、迁移和报告一致；无跳项、占位、Deferred 冒充 Done 或陈旧证据 | Pending |
| PROJECT-PLATFORM-S05-M5-T02 | 复验 V001 至最新空库迁移、V065 升级、约束、索引和回滚边界 | 迁移可重复；layout/policy 表 owner 与复合隔离正确；legacy 表、published v1 和既有字段 hash 不被改写 | Pending |
| PROJECT-PLATFORM-S05-M5-T03 | 复验永久节点身份、规范 hash、幂等、并发冲突、图原子性和失效引用 | 重排/复制/删除/冲突/重放/字段退役均有确定结果；无半图、静默重绑或历史身份复用 | Pending |
| PROJECT-PLATFORM-S05-M5-T04 | 复验六身份字段访问、最小披露、synthetic preview 和伪造请求负例 | hidden/read/write/required 与服务端决策一致；企业管理员、非成员、跨空间请求和日志均零泄露 | Pending |
| PROJECT-PLATFORM-S05-M5-T05 | 复验 create/detail 编辑、共享渲染、可访问性、响应式和 120 字段配置预算 | 管理员配置和用户只读样本闭环通过；性能结论限定配置范围，不描述真实 WorkItem 或平台容量 | Pending |
| PROJECT-PLATFORM-S05-M5-T06 | 运行架构 inventory/boundaries/contracts 并核对 project/shared 与 table owner | project/shared P0、foreign write、新增未批准跨 owner read 和 shared reverse import 为 0；历史基线不扩散 | Pending |
| PROJECT-PLATFORM-S05-M5-T07 | 执行完整后端测试、V001 至最新 Flyway、前端 lint/build、collaboration、工作台和安全门禁 | 全部使用 fresh 日志通过；失败、跳过、豁免、生成物污染或 mock 证据形成阻断决定 | Pending |
| PROJECT-PLATFORM-S05-M5-T08 | 更新当前架构、产品范围、技术选择、模块合同、Program、目标架构和专项索引 | 文档只声明已实现布局/访问事实；PLATFORM-SCALE Deferred、S06/S07 未实现和非容量承诺保持清楚 | Pending |
| PROJECT-PLATFORM-S05-M5-T09 | 冻结 S06 draft/publish/version/template 准入包和 S07 渲染器承接合同 | S06 输入包含字段/布局/策略规范快照与 hash；S07 只注入实例值和上下文，不复制策略或字段模型 | Pending |
| PROJECT-PLATFORM-S05-M5-T10 | 完成 PROJECT-PLATFORM-S05 Go/No-Go 和下一 Stage 决策 | 明确 Go S06、Reopen S05 或新增修复 Stage；依据、阻断、迁移和第一入口可直接执行，不在本路线提前激活 | Pending |
| PROJECT-PLATFORM-S05-M5-T11 | 完成 Stage 报告、影响审计、真实隔离浏览器复验和路线级 `route-final` | 57 项逐 Task 闭环；当前路线标记 completed、Program revision 递增且 current_stage 置 none；最终门禁通过 | Pending |

## 6. Stage 全局验收标准

- create/detail 布局图独立配置，节点具有永久 ID/key、确定顺序和合法父子结构；相同语义产生相同规范 hash。
- 布局字段节点只保存 `fieldId + fieldKey`，字段事实由 S04 目录读取；跨域、key 不一致和失效引用不会静默重绑。
- section/tab/column/field/summary、条件显示、编辑器和共享渲染器可用；relation 只保留未来扩展边界，不冒充 S10。
- 条件显示不授予权限；服务端对每个字段计算 `read/write/required/hidden` 并提供最小披露的解释链。
- owner/admin/member/guest/non-member/enterprise admin 六身份、空间/类型/字段状态和直接 API 负例均有自动与真实隔离证据。
- 所有写命令具备复合隔离、aggregate version、request id、规范 hash、幂等、原子图、审计/outbox 和并发保护。
- synthetic preview 不写业务表、不创建 WorkItem/平台对象、不触发正式通知、搜索或实时副作用。
- 管理员配置与用户只读样本复用同一渲染器；未来 S07 通过稳定组件合同注入真实实例值。
- 1366/1440 桌面、窄屏、键盘和辅助技术路径可用；文本、面板、菜单、滚动和错误状态不越界或遮挡。
- 120 字段/2400 选项的配置与渲染预算有可复算证据；不把本 Stage 结果描述为生产容量或长稳承诺。
- V001 至最新空库和 V065 升级迁移可重复；published v1、legacy project/issue 和既有字段身份/hash 不被改写。
- project/shared P0、foreign write、新增未批准跨 owner read 和 shared reverse import 为 0。
- S05 不创建 `project_work_items`、字段值或 legacy 迁移，不实现 S06 发布流水线，也不把预览称为真实实例。
- M5 完成完整后端、迁移、前端、collaboration、工作台、安全、真实隔离浏览器和 `route-final`，并输出唯一下一入口。

## 7. 当前执行入口

从 `PROJECT-PLATFORM-S05-M1-T01` 开始。每次只启动一个 Milestone 的完整任务范围；前一 Milestone 未 finish 前不启动后一 Milestone。M5 完成和当前路线归档前不得激活 S06。
