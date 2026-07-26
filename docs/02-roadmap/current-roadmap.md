---
title: PROJECT-PLATFORM-S09 复杂节点流定义与运行时当前执行路线
status: active
route: PROJECT-PLATFORM-S09
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 25
stage: PROJECT-PLATFORM-S09
stage_final_milestone: PROJECT-PLATFORM-S09-M5
last_code_check: 2026-07-27
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S09 复杂节点流定义与运行时

## 1. Stage 目标

在 S08 轻量状态流已经归档的基础上，交付适用于审批、交付和多参与方协作的版本化复杂节点流。空间管理员可以在唯一配置草稿中定义节点、边、阶段、分支、汇聚、处理人、表单、交付物和时限，经 S06 发布后由绑定该不可变版本的 WorkItem 运行；空间成员可以围绕服务端分配的节点任务完成单人、任一人、多人会签和自动节点，并获得可追溯的 token、投票、交付物、历史和恢复能力。

S09 的完成标准不是把 S08 状态转换包装成流程图。复杂节点流必须拥有独立的 workflow instance、active token、node task、vote 和 join 权威，只能通过已经冻结的 command/event SPI 与 WorkItem、活动、审计和 outbox 协作；不得读写 S08 current-state 私表。运行实例必须解释自身绑定的完整 configuration snapshot，分支/汇聚、回退/跳转/终止/补偿和版本升级必须具备确定语义、幂等、并发控制、故障原子性和不可变历史。

## 2. 固定输入与当前事实

- S08 完成路线已归档；当前数据库 schema 为 V092，S09 尚未创建 workflow instance、node token、node task、vote 或 join 运行时权威。
- WorkItem 显式绑定 `type_definition_id + type_version_id + config_hash`；节点流运行时只能经 `PublishedSnapshotAdapter` 解释该绑定版本，不回读 active draft、最新类型版本或 live 配置表。
- S06 提供唯一配置草稿、校验、不可变发布、diff、rollback、模板 lineage 和兼容矩阵；节点图定义必须扩展完整 snapshot，不能建立第二套发布权威。
- S07 提供规范 WorkItem、expected version、持久化 command receipt、活动序列、审计/outbox 和用户侧竖切；S09 只能复用已经冻结的公共 command/event SPI。
- S08 轻量状态流只保存单一 current state；S09 节点流保存 workflow instance 与 active token。两类运行时不得共享私有运行表、Repository 或以同步双写维持一致。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份继续使用空间与内容边界；企业管理员不因治理角色自动获得节点定义、任务或交付物权限。
- S09 前已经存在的 WorkItem 不得被静默初始化或升级；启动、版本映射、backfill、失败清单、续跑和回退必须显式并可审计。
- S09 不提前实现 S10 关系引擎、S14 高级视图、S16 工作量/工时、S17 自动化编排，也不把基础性能证据表述为生产容量结论。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 节点、边、阶段、分支、汇聚及节点配置必须位于同一完整 snapshot；任何从 live repository 或前端文本补算运行语义的实现均为阻断。
3. 图元素、节点任务、投票和事件使用永久 semantic key；展示名可变，运行历史与升级映射不得依赖展示文本定位。
4. workflow instance、token、task、vote、join 和 history 是 S09 独立权威；只能调用公共 SPI，不得查询或写入 S08 私表。
5. 节点命令在同一事务提交 WorkItem aggregate version、token/task/vote/join、历史、活动、审计、outbox 和 receipt；失败不得产生半推进。
6. `availableActions`、任务分配和真实执行必须调用同一服务端决策；列表投影可批量计算，但不能成为授权事实源。
7. 分支、汇聚、回退、跳转、终止、补偿和版本升级必须声明式、确定、可审计；任意代码、动态 SQL 和无限自动推进禁止进入基础合同。
8. M1-M4 使用影响范围门禁；M5 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M1 | 节点图、分支汇聚与版本化定义底座 | S08 归档；Program revision 25 | `docs/90-reports/project-platform-s09-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S09-M2 | 节点 token 运行时、会签、并发与历史 | M1 | `docs/90-reports/project-platform-s09-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S09-M3 | 节点任务、表单、交付物与时限协作 | M1-M2 | `docs/90-reports/project-platform-s09-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S09-M4 | 回退、跳转、终止、补偿与版本升级恢复 | M1-M3 | `docs/90-reports/project-platform-s09-m4-execution-report.md` | Pending |
| PROJECT-PLATFORM-S09-M5 | 可视化设计器、成员执行 UI、综合验收与 Stage 收口 | M1-M4 | `docs/90-reports/project-platform-s09-m5-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S09-M1 节点图、分支汇聚与版本化定义底座

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M1-T01 | 审计 S06-S08 snapshot、WorkItem、command/event SPI、状态私表和现有审批语义 | 可复用端口、调用方、存量数据、迁移风险和禁止依赖可定位；无未经证实的图运行假设 | Pending |
| PROJECT-PLATFORM-S09-M1-T02 | 冻结 NodeDefinition、EdgeDefinition、StageDefinition、BranchDefinition 和 JoinDefinition 领域合同 | 永久 key、节点类型、入口/出口、边优先级、阶段、分支和汇聚错误语义无歧义 | Pending |
| PROJECT-PLATFORM-S09-M1-T03 | 扩展完整 configuration snapshot schema 承载复杂节点流 | snapshot/hash/canonicalizer 覆盖节点图；旧 schema、无节点流类型和未来 schema 行为明确 | Pending |
| PROJECT-PLATFORM-S09-M1-T04 | 设计 workflow instance、token、task、vote、join、receipt 和不可变 history Flyway schema | workspace/space/workItem/version 边界、唯一性、FK、索引、清理闭包和不可变保护完整 | Pending |
| PROJECT-PLATFORM-S09-M1-T05 | 把节点流编辑接入 S06 唯一配置草稿与 Repository/DTO | 定义只存在于草稿 snapshot；不建立 live/published 双写表；乐观版本与幂等沿用现有合同 | Pending |
| PROJECT-PLATFORM-S09-M1-T06 | 实现图结构校验：单一入口、受控出口、可达性、悬空边、非法环和死路 | 非法图产生稳定 diagnostics；校验确定、顺序无关且不静默修复用户定义 | Pending |
| PROJECT-PLATFORM-S09-M1-T07 | 实现节点类型、处理策略和扩展能力注册表 | auto/single/any/multi 能力可版本化发现；未知类型、重复 key 和未注册扩展失败关闭 | Pending |
| PROJECT-PLATFORM-S09-M1-T08 | 实现声明式分支条件、边优先级和汇聚策略 canonicalizer | operator/operand 白名单、exclusive/parallel 分支与 all/any/quorum 汇聚语义稳定；不执行任意代码 | Pending |
| PROJECT-PLATFORM-S09-M1-T09 | 把节点图变化接入配置 diff、兼容矩阵、发布和 rollback | 删除/重定向节点、分支、汇聚及任务配置变化分级稳定；blocked/migration_required 不可绕过 | Pending |
| PROJECT-PLATFORM-S09-M1-T10 | 为系统预置类型提供确定性节点流草稿/模板输入 | 重复安装/升级 hash 稳定；不覆盖 workspace 本地修改；不以隐藏默认值猜测运行语义 | Pending |
| PROJECT-PLATFORM-S09-M1-T11 | 完成 schema、图校验、草稿、发布、兼容和跨空间负向自动化测试 | 空库/升级、并发、幂等、不可变、越权、未知扩展及 S08 私表隔离均通过 | Pending |
| PROJECT-PLATFORM-S09-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 文档只声明定义底座；runtime 尚未激活，M2 输入、证据和剩余风险清晰 | Pending |

### PROJECT-PLATFORM-S09-M2 节点 token 运行时、会签、并发与历史

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M2-T01 | 复核 M1 定义、snapshot、schema、报告和未关闭阻断 | 12 项任务逐项可追溯；阻断项 Reopen，不以文档结论代替实现 | Pending |
| PROJECT-PLATFORM-S09-M2-T02 | 实现绑定 snapshot 驱动的 NodeRuntimeAdapter 与实例启动决策 | 只解释 WorkItem 绑定版本/hash；无节点流返回显式能力缺失；不查询最新配置或 S08 私表 | Pending |
| PROJECT-PLATFORM-S09-M2-T03 | 实现 workflow instance、active token Repository、行锁、乐观版本和原子初始化 | 单 WorkItem/绑定版本实例唯一；并发启动唯一；WorkItem/instance 版本不漂移 | Pending |
| PROJECT-PLATFORM-S09-M2-T04 | 实现节点命令决策、`availableActions` 和任务候选服务端投影 | 投影与执行共享同一 decision；reason code、披露范围、策略版本和动作排序稳定 | Pending |
| PROJECT-PLATFORM-S09-M2-T05 | 实现自动节点执行和有界内部推进 | 每步持久化可恢复；循环/步数上限和未知扩展失败关闭；无递归失控或未审计副作用 | Pending |
| PROJECT-PLATFORM-S09-M2-T06 | 实现 single、any、all 和 quorum 多人处理/会签语义 | 候选、领取、投票、撤销、完成阈值和迟到命令确定；身份与重复投票受控 | Pending |
| PROJECT-PLATFORM-S09-M2-T07 | 实现 exclusive/parallel token split 与 all/any/quorum join | token lineage、join correlation 和并发到达原子；不丢 token、不重复放行、不跨实例汇聚 | Pending |
| PROJECT-PLATFORM-S09-M2-T08 | 接入持久化幂等回执、规范 request hash 和并发胜者合同 | 相同 request ID 精确重放；异载荷冲突；败者不重复任务、投票、历史、事件或副作用 | Pending |
| PROJECT-PLATFORM-S09-M2-T09 | 实现不可变节点历史并接入活动、审计和事务 outbox | instance/token/node/task/vote/join 序列可追溯；事件 schema 可重放且不暴露私有策略 | Pending |
| PROJECT-PLATFORM-S09-M2-T10 | 交付用户实例、当前 token、任务、投票、历史和命令 API/DTO | 用户路由不混入治理 API；404/403/409/422 稳定；DTO 不暴露表结构或隐藏分支输入 | Pending |
| PROJECT-PLATFORM-S09-M2-T11 | 完成六身份、并发、重放、split/join、会签、故障注入和跨空间自动化测试 | stale command 更新零行；无双放行、半历史、越权枚举、重复事件或隐藏值泄漏 | Pending |
| PROJECT-PLATFORM-S09-M2-T12 | 执行代表性实例推进/任务列表预算并完成 M2 checkpoint | SQL plan、批量上界和延迟可复现；不冒充生产容量或 S17 自动化结论 | Pending |

### PROJECT-PLATFORM-S09-M3 节点任务、表单、交付物与时限协作

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M3-T01 | 复核 M2 运行时、责任边界、文件合同和未关闭阻断 | 12 项任务逐项可追溯；节点协作不绕过 snapshot、权限、token 或公共 SPI | Pending |
| PROJECT-PLATFORM-S09-M3-T02 | 冻结节点表单、字段可见/可编辑/必填策略和提交合同 | 字段策略进入 snapshot；hidden 字段零披露；节点提交与 WorkItem patch 语义无歧义 | Pending |
| PROJECT-PLATFORM-S09-M3-T03 | 实现处理人解析：显式参与者、空间角色、字段参与者和受控动态规则 | 解析基于绑定 snapshot 与当前可见事实；空候选、离场成员和未知规则失败可恢复 | Pending |
| PROJECT-PLATFORM-S09-M3-T04 | 实现 node task 候选、领取、转交、委派、完成和关闭生命周期 | task/token 版本一致；授权与可见性统一；并发领取、转交和完成只有一个事实结果 | Pending |
| PROJECT-PLATFORM-S09-M3-T05 | 接入节点交付物与公开文件/对象端口 | 交付物类型、数量、必填和引用可版本化；不读取文件私表，不把临时上传冒充已提交产物 | Pending |
| PROJECT-PLATFORM-S09-M3-T06 | 实现节点计划时间、到期时间和超时状态合同 | 时区、日历输入、暂停/恢复与展示稳定；不提前实现 S16 工作量、工时或资源容量 | Pending |
| PROJECT-PLATFORM-S09-M3-T07 | 实现表单 patch、交付物校验、投票/完成与 token 推进原子命令 | expected version、授权、字段、文件、task、vote、token、历史和 receipt 全提交或全回滚 | Pending |
| PROJECT-PLATFORM-S09-M3-T08 | 接入任务/到期事件及通知、搜索消费者公共合同 | 事件最小披露、可去重重放；消费者不读节点私表；未知版本进入 dead letter | Pending |
| PROJECT-PLATFORM-S09-M3-T09 | 交付成员任务箱、节点详情和处理上下文后端聚合 DTO | 列表无 N+1；表单、交付物、候选和动作只按服务端决策披露；分页/排序稳定 | Pending |
| PROJECT-PLATFORM-S09-M3-T10 | 完成六身份、隐藏字段、文件越权、并发任务和故障原子性自动化测试 | 无跨空间交付物枚举、重复完成、孤儿文件引用、半 patch、半投票或半推进 | Pending |
| PROJECT-PLATFORM-S09-M3-T11 | 执行任务箱、节点详情、表单/交付物提交代表性预算 | SQL plan、文件端口调用上界和延迟可复现；不以 mock 冒充真实对象权限 | Pending |
| PROJECT-PLATFORM-S09-M3-T12 | 同步协作对象/事件/运维合同并完成 M3 checkpoint | 文档只声明已交付节点协作；恢复与升级仍属于 M4，视觉闭环仍属于 M5 | Pending |

### PROJECT-PLATFORM-S09-M4 回退、跳转、终止、补偿与版本升级恢复

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M4-T01 | 冻结 return、jump、terminate、compensate、correct 和 upgrade 语义 | 每类命令的来源、目标、token/task/join 处理、授权和历史语义明确；不直接改表纠错 | Pending |
| PROJECT-PLATFORM-S09-M4-T02 | 实现显式回退与节点重做语义 | 只允许 snapshot 声明的回退；目标、表单、交付物和处理人重新校验；旧历史不删除 | Pending |
| PROJECT-PLATFORM-S09-M4-T03 | 实现受控跳转与跳过边界 | 目标必须合法且可映射；未完成 token/task/join 有确定关闭语义；禁止跨实例或任意节点写入 | Pending |
| PROJECT-PLATFORM-S09-M4-T04 | 实现流程终止、取消及 WorkItem archive/restore 生命周期协同 | archive 不伪装业务终止；终止原子关闭活动任务/token；恢复不自动猜测流程位置 | Pending |
| PROJECT-PLATFORM-S09-M4-T05 | 实现声明式补偿动作注册、执行和失败恢复 | 补偿白名单、顺序、幂等和审计明确；不执行任意代码；部分失败可安全续跑 | Pending |
| PROJECT-PLATFORM-S09-M4-T06 | 实现实例配置版本升级的节点/边/阶段映射与兼容阻断 | 删除、拆分、合并和重命名使用显式 map；blocked 变化失败关闭；旧绑定仍可运行 | Pending |
| PROJECT-PLATFORM-S09-M4-T07 | 实现 pre-S09 WorkItem 显式启动与批量 backfill | manifest、目标版本、入口节点、失败清单、幂等和校验可追溯；不静默修改旧实例 | Pending |
| PROJECT-PLATFORM-S09-M4-T08 | 实现空间管理员受控恢复、纠错和 dead-letter 续跑入口 | 仅 owner/admin 可执行；需要原因、expected version、危险确认与审计；企业管理员不自动可见内容 | Pending |
| PROJECT-PLATFORM-S09-M4-T09 | 完善恢复失败、并发纠错、补偿及消费者重放原子性 | instance/token/task/vote/join/history/activity/audit/outbox/receipt 全回滚或全提交 | Pending |
| PROJECT-PLATFORM-S09-M4-T10 | 完成回退/跳转/终止/补偿/升级/纠错六身份与故障自动化测试 | stale、重复、越权、跨空间、映射缺失、补偿失败和恢复负例完整 | Pending |
| PROJECT-PLATFORM-S09-M4-T11 | 执行 V001 至最新迁移、非空实例升级和 backfill/recovery rehearsal | 空库、历史基线、重复 migrate、失败续跑、映射校验和恢复结果通过 | Pending |
| PROJECT-PLATFORM-S09-M4-T12 | 交付复杂节点流恢复 runbook、兼容矩阵并完成 M4 checkpoint | 操作步骤、停止条件、回退边界和 M5 输入清晰；不宣称视觉闭环已完成 | Pending |

### PROJECT-PLATFORM-S09-M5 可视化设计器、成员执行 UI、综合验收与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M5-T01 | 审计 M1-M4 实现、报告、迁移、边界和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S09-M5-T02 | 交付空间配置侧节点、边、阶段、分支和汇聚可视化设计器 | 创建、连接、移动、删除、诊断、缩放和键盘操作可用；不进入企业管理后台 | Pending |
| PROJECT-PLATFORM-S09-M5-T03 | 交付处理人、表单、交付物、时限、回退和补偿配置面板 | 配置与图选中态一致；未知扩展和非法组合即时诊断；不以 UI 默认值替代 snapshot 事实 | Pending |
| PROJECT-PLATFORM-S09-M5-T04 | 交付配置预览、diff、兼容提示、发布阻断和 rollback 交互 | 草稿/发布边界清晰；blocked/migration_required 不可绕过；失败保留图与表单输入 | Pending |
| PROJECT-PLATFORM-S09-M5-T05 | 交付成员实例图、当前 token、任务、动作和不可变历史执行 UI | 只展示服务端事实；刷新后一致；409/422/超时/离线可恢复且不静默覆盖 | Pending |
| PROJECT-PLATFORM-S09-M5-T06 | 交付会签、表单、交付物、转交和到期交互 | 投票阈值、候选/已处理、必填/隐藏、上传状态和错误可理解且不泄露不可见信息 | Pending |
| PROJECT-PLATFORM-S09-M5-T07 | 完成长名称、空态、密集图、键盘、焦点、窄屏和无障碍交互 | 1440/1366/820 关键视口可用；图与表格可替代导航；焦点与错误关联完整 | Pending |
| PROJECT-PLATFORM-S09-M5-T08 | 执行 owner/admin/member/guest/non-member/enterprise-admin 真实隔离浏览器验收 | 配置、任务、投票、交付物、恢复和最小披露符合服务端决策 | Pending |
| PROJECT-PLATFORM-S09-M5-T09 | 执行 split/join、会签、并发、离线、回退、补偿、升级和 backfill 真实浏览器验收 | 无丢 token/重复放行；冲突不丢输入；刷新后 instance/task/history/activity 一致 | Pending |
| PROJECT-PLATFORM-S09-M5-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和生成物门禁 | full gate 无阻断；非空恢复和日志 fresh 可复现；mock 不冒充真实浏览器/数据库证据 | Pending |
| PROJECT-PLATFORM-S09-M5-T11 | 同步当前架构、Program、专项索引、模块/事件/运维合同并复核 S10 准入 | 文档只声明已实现事实；S10 关系引擎边界与禁止复用项冻结 | Pending |
| PROJECT-PLATFORM-S09-M5-T12 | 给出 S09 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 五份报告、工作上下文、60 Task 和文档一致；仅无阻断时 Completed，S10 保持 Planned | Pending |

## 6. Stage 验收

- 节点、边、阶段、分支、汇聚、处理人、表单、交付物和时限进入完整不可变 configuration snapshot，并经过草稿、校验、diff、发布、rollback 和模板链路。
- 节点运行时只解释 WorkItem 绑定的 type version/config hash，拥有独立 workflow instance、token、task、vote 和 join 权威，不读写 S08 current-state 私表。
- single、any、all、quorum、auto、exclusive/parallel split 与 all/any/quorum join 具备确定并发和幂等语义。
- 命令决策、`availableActions`、候选任务和执行使用同一服务端授权；六身份、跨 workspace/space、hidden 字段和文件最小披露通过。
- 节点推进具备 expected version、持久化 receipt、不可变 history、活动、审计和事务 outbox；故障不产生半 token、半投票、半交付物或重复放行。
- 回退、跳转、终止、补偿、纠错、pre-S09 backfill 和版本升级均为显式命令/manifest，具备映射、失败清单、续跑、验证和回退边界。
- 空间配置设计器与成员执行 UI 完成真实闭环；企业管理后台不承载日常节点配置或执行。
- S09 不实现 S10 关系引擎、S14 高级视图、S16 工作量/工时或 S17 自动化编排，也不把基础预算表述为生产容量。
