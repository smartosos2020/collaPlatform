---
title: PROJECT-PLATFORM-S10 工作项关系、层级和依赖当前执行路线
status: active
route: PROJECT-PLATFORM-S10
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 27
stage: PROJECT-PLATFORM-S10
stage_final_milestone: PROJECT-PLATFORM-S10-M5
last_code_check: 2026-07-27
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S10 工作项关系、层级和依赖

## 1. Stage 目标

在 S09 复杂节点流已经完成并归档的基础上，为规范 `work_item` 建立独立、可配置、可审计的关系权威。空间管理员可以定义普通、父子、依赖和阻塞关系的方向、反向名称、适用类型、基数与删除策略；空间成员可以在服务端授权下建立、理解、调整和撤销关系，并通过层级导航、反向引用和有界影响分析识别上下游协作。

S10 的完成标准不是把 legacy `issue_relations` 换表，也不是把 S09 流程 edge 解释为工作项关系。关系定义必须进入唯一版本化配置发布链路，关系实例、不可变历史和层级投影必须拥有独立权威；父子与依赖环、并发写入、删除/归档、跨空间、最小披露、存量映射和恢复必须具备确定语义。S10 不实现 S11 细粒度数据权限、S13 全局树形/保存视图、S14 甘特/关键路径、S17 自动化或 S18 跨空间同步。

## 2. 固定输入与当前事实

- S09 完成路线已归档；当前 schema 为 V096，规范 WorkItem、轻量状态流和复杂节点流均已交付，但尚无规范 WorkItem↔WorkItem 关系权威。
- legacy `issue_relations` 只以 `issue_id + target_type + target_id` 保存软删除关系，缺少规范关系类型版本、双端 WorkItem 约束、反向语义、父子一致性和依赖环保护；它只能作为显式迁移输入。
- S07 的迁移 manifest 已记录 legacy relation，但没有把它转换成 S10 事实；任何迁移必须显式生成映射、失败清单、续跑和 verify，不得读取路径静默补写。
- 关系定义只能进入 S06 唯一配置草稿、校验、不可变发布、diff、compatibility、rollback 和模板链路，不建立 live relation-definition 双权威。
- 关系实例只能引用规范 WorkItem identity、space/type binding 和已发布关系定义；不得外键耦合或读取 S08 current-state/history/backfill 与 S09 instance/token/task/vote/join/history/backfill 私表。
- 流程 edge 是同一 workflow instance 内的执行路径，node token lineage/join correlation 也不是 WorkItem 业务关系；关系变化不得反向推进任何流程。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份继续使用空间与内容边界；企业管理员不因治理角色自动获得关系端点内容访问。
- 平台对象 resolver 可以用于目标摘要和深链，但 resolver 的 `forbidden/deleted/missing` 状态不能绕过源、目标 WorkItem 的关系授权。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 关系类型使用永久 semantic key；显示名、正反向文案可变，已创建边不得依赖展示文本解释方向和语义。
3. 普通、父子、依赖和阻塞关系共享规范 relation envelope，但父子与依赖拥有独立结构约束；不得用 UI 展开树代替服务端一致性。
4. 关系命令必须使用 expected WorkItem/relation version、caller-stable request ID、持久 receipt、审计、活动和事务 outbox；失败不得产生单端边、半历史或陈旧投影。
5. 父子环、依赖环、基数和重复边在服务端事务内失败关闭；并发胜者唯一，败者返回稳定冲突事实。
6. 正向、反向、层级和影响投影只能从规范关系权威重建；缓存或 closure/path 表不得成为第二套可写事实源。
7. 列表、详情、选择器、反向引用和影响分析必须逐端点应用空间与内容授权；不可见端点不泄露标题、类型、层级位置或关系数量。
8. M1-M4 使用影响范围门禁；M5 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M1 | 关系定义、版本化配置与持久化底座 | S09 归档；Program revision 27 | `docs/90-reports/project-platform-s10-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S10-M2 | 关系实例、并发一致性、循环与生命周期 | M1 | `docs/90-reports/project-platform-s10-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S10-M3 | 自定义层级、子项拆解、查询与一致性恢复 | M1-M2 | `docs/90-reports/project-platform-s10-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S10-M4 | 关系控件、反向引用、影响分析与存量承接 | M1-M3 | `docs/90-reports/project-platform-s10-m4-execution-report.md` | Pending |
| PROJECT-PLATFORM-S10-M5 | 配置与成员 UI、真实验收及 Stage 收口 | M1-M4 | `docs/90-reports/project-platform-s10-m5-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S10-M1 关系定义、版本化配置与持久化底座

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M1-T01 | 审计规范 WorkItem、配置 snapshot、resolver、legacy `issue_relations`、S08/S09 私表和现有关系 UI | 可复用端口、调用方、存量形态、迁移风险和禁止依赖可定位；无未经证实的关系语义 | Pending |
| PROJECT-PLATFORM-S10-M1-T02 | 冻结 RelationDefinition 与 normal/parent-child/dependency/blocking 领域合同 | 永久 key、方向、反向名称、端点角色、基数、重复和删除策略无歧义 | Pending |
| PROJECT-PLATFORM-S10-M1-T03 | 扩展完整 configuration snapshot 承载关系定义和类型适用矩阵 | 定义进入唯一草稿/发布权威；旧 schema、无关系定义和未来 schema 行为明确 | Pending |
| PROJECT-PLATFORM-S10-M1-T04 | 设计 relation、command receipt、不可变 history 与可重建投影 Flyway schema | workspace/space/双端 WorkItem/definition version 复合约束、索引和清理闭包完整 | Pending |
| PROJECT-PLATFORM-S10-M1-T05 | 把关系定义编辑接入 S06 草稿 Repository、DTO、canonical hash 和乐观版本 | 不建立 live definition 表或 published 双写；跨空间/跨类型引用失败关闭 | Pending |
| PROJECT-PLATFORM-S10-M1-T06 | 实现方向、反向、端点类型、基数、删除策略和保留规则校验 | 非法组合产生稳定 diagnostics；校验确定且不静默改写用户定义 | Pending |
| PROJECT-PLATFORM-S10-M1-T07 | 接入配置 diff、compatibility、发布、rollback 和模板 lineage | 删除/改向/收紧基数/改变类型矩阵分级稳定；blocked/migration_required 不可绕过 | Pending |
| PROJECT-PLATFORM-S10-M1-T08 | 提供普通、父子、依赖和阻塞的确定性系统预置 | 重复安装/升级 hash 稳定；不覆盖空间本地修改，不用隐藏默认值猜实例语义 | Pending |
| PROJECT-PLATFORM-S10-M1-T09 | 冻结 legacy relation 分类、端点解析和显式迁移 manifest 合同 | issue/message/knowledge 等旧目标分流明确；不可映射项进入失败清单而非伪造 WorkItem | Pending |
| PROJECT-PLATFORM-S10-M1-T10 | 冻结 relation command/event、resolver 和模块所有权公共合同 | 公共 payload 最小披露；消费者不得读关系私表或流程私表；未知版本失败关闭 | Pending |
| PROJECT-PLATFORM-S10-M1-T11 | 完成 schema、定义校验、草稿、发布、兼容和边界自动化测试 | 空库/升级、幂等、不可变、越权、未知类型和 S08/S09 私表隔离均通过 | Pending |
| PROJECT-PLATFORM-S10-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 文档只声明定义底座；实例写入尚未激活，M2 输入、证据和风险清晰 | Pending |

### PROJECT-PLATFORM-S10-M2 关系实例、并发一致性、循环与生命周期

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M2-T01 | 复核 M1 定义、snapshot、schema、报告和未关闭阻断 | 12 项逐项可追溯；阻断项 Reopen，不以文档结论代替实现 | Pending |
| PROJECT-PLATFORM-S10-M2-T02 | 实现 canonical endpoint、稳定方向、唯一活动边和 relation Repository | 相同语义边唯一；反向展示不复制事实；workspace/space/软删除边界正确 | Pending |
| PROJECT-PLATFORM-S10-M2-T03 | 实现创建、撤销、恢复关系的原子命令与持久幂等回执 | expected 双端版本和 request hash 精确重放；异载荷、stale、并发败者不追加事实 | Pending |
| PROJECT-PLATFORM-S10-M2-T04 | 实现双端 WorkItem、关系定义和操作授权的服务端统一决策 | projection 与 execute 共享决策；六身份和不可见端点使用稳定最小披露 | Pending |
| PROJECT-PLATFORM-S10-M2-T05 | 实现普通有向/无向关系及正反向投影 | 单一事实可生成双方一致视图；重命名定义不改变历史方向；排序和分页稳定 | Pending |
| PROJECT-PLATFORM-S10-M2-T06 | 实现父子关系基数、单/多父策略和服务端环检测 | 自环、祖先环、超基数和跨空间父子失败关闭；并发 reparent 只有一个结果 | Pending |
| PROJECT-PLATFORM-S10-M2-T07 | 实现依赖/阻塞方向、重复归一和并发环检测 | dependency/blocking 反向语义一致；并发成环不能双成功；不计算 S14 关键路径 | Pending |
| PROJECT-PLATFORM-S10-M2-T08 | 实现 WorkItem archive/restore/delete 与 relation 生命周期策略 | restrict/detach/retain-history 由定义决定；恢复不静默重建已撤销边 | Pending |
| PROJECT-PLATFORM-S10-M2-T09 | 接入不可变关系历史、活动、审计和事务 outbox | 双端、定义版本、操作者、版本和结果可追溯；事件不泄露不可见端点正文 | Pending |
| PROJECT-PLATFORM-S10-M2-T10 | 交付用户关系列表、正反向查询、命令和 capability API/DTO | 用户路由不混入治理 API；404/403/409/422 稳定；无 N+1 或表结构泄漏 | Pending |
| PROJECT-PLATFORM-S10-M2-T11 | 完成六身份、跨空间、幂等、并发、父子/依赖环和故障原子性测试 | 无单端边、双成功、半历史、越权枚举、重复事件或陈旧反向投影 | Pending |
| PROJECT-PLATFORM-S10-M2-T12 | 执行代表性关系写入、双向列表和环检测预算并完成 M2 checkpoint | SQL plan、锁范围、批量上界和延迟可复现；不冒充生产图规模结论 | Pending |

### PROJECT-PLATFORM-S10-M3 自定义层级、子项拆解、查询与一致性恢复

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M3-T01 | 复核 M2 关系权威、并发合同、投影和未关闭阻断 | 12 项逐项可追溯；层级能力不绕过关系定义、双端授权或一致性约束 | Pending |
| PROJECT-PLATFORM-S10-M3-T02 | 实现由父子关系权威重建的 hierarchy path/closure 投影 | 投影可丢弃重建且不可直接写；relation edge 始终是唯一结构事实 | Pending |
| PROJECT-PLATFORM-S10-M3-T03 | 实现 attach、detach、reparent 与原子子工作项创建 | 子项创建/绑定、字段写入、关系、活动、审计、outbox 和 receipt 全成或全败 | Pending |
| PROJECT-PLATFORM-S10-M3-T04 | 实现发布定义驱动的跨类型层级规则 | source/target type version、允许层级和最大深度稳定；live 配置变化不破坏旧事实 | Pending |
| PROJECT-PLATFORM-S10-M3-T05 | 实现祖先、子孙、同级和局部树的有界查询 | 深度/节点硬上限、稳定游标和批量摘要无 N+1；超限返回明确 continuation | Pending |
| PROJECT-PLATFORM-S10-M3-T06 | 交付面包屑、父项、子项、同级和局部层级导航 DTO | 每个端点独立授权；不可见祖先不泄露标题/类型且导航退化可解释 | Pending |
| PROJECT-PLATFORM-S10-M3-T07 | 实现受控子项拆解模板和字段继承 | 只复制白名单字段与可见值；不复制状态/node token、任务、历史或隐藏字段 | Pending |
| PROJECT-PLATFORM-S10-M3-T08 | 实现归档、恢复、类型版本升级对层级和查询投影的影响 | 结构保留/脱离策略确定；旧绑定可解释；失败不留下孤儿 projection | Pending |
| PROJECT-PLATFORM-S10-M3-T09 | 实现层级一致性扫描、rebuild、dry-run、失败清单和续跑 | 可检测缺边、错 path、环、越界与漂移；修复只重建投影，不改规范边 | Pending |
| PROJECT-PLATFORM-S10-M3-T10 | 交付层级查询、拆解、reparent 和恢复 API/DTO | expected version、危险确认和错误码稳定；治理入口与成员入口分层 | Pending |
| PROJECT-PLATFORM-S10-M3-T11 | 完成跨类型、深层、并发 reparent、权限截断和 rebuild 故障测试 | 无环、孤儿、越权祖先、重复子项、半拆解或错误修复规范边 | Pending |
| PROJECT-PLATFORM-S10-M3-T12 | 执行代表性深度/宽度层级预算并完成 M3 checkpoint | SQL plan、节点/深度上限和恢复时长可复现；不提前实现 S13 全局树视图 | Pending |

### PROJECT-PLATFORM-S10-M4 关系控件、反向引用、影响分析与存量承接

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M4-T01 | 复核 M1-M3 定义、实例、层级、权限和未关闭阻断 | 36 项逐项可追溯；UI/迁移不创建旁路关系事实或弱化循环约束 | Pending |
| PROJECT-PLATFORM-S10-M4-T02 | 把关系控件配置接入 create/detail layout 与字段访问投影 | 控件引用永久 relation key；显示/编辑能力由服务端决定，不把关系伪装普通字段 JSON | Pending |
| PROJECT-PLATFORM-S10-M4-T03 | 实现基于 WorkItem resolver/受权搜索的关系目标选择器合同 | 类型/空间/归档过滤稳定；不可见目标不进入候选、计数或错误正文 | Pending |
| PROJECT-PLATFORM-S10-M4-T04 | 实现正向/反向引用批量摘要与详情聚合 | 双向文案、类型、状态、删除态与深链一致；列表硬限且无 N+1 | Pending |
| PROJECT-PLATFORM-S10-M4-T05 | 实现依赖上下游、直接/传递阻塞和有界影响分析 | 路径方向、截断、环失败与权限裁剪可解释；不计算工期、关键路径或自动流转 | Pending |
| PROJECT-PLATFORM-S10-M4-T06 | 实现关系变更预览、冲突刷新和输入保留合同 | 409/422/timeout/offline 不丢目标选择和原因；刷新后以服务端事实为准 | Pending |
| PROJECT-PLATFORM-S10-M4-T07 | 实现层级局部树、折叠、替代列表和键盘导航合同 | 只承载当前 WorkItem 局部层级；不提前实现 S13 保存/共享全局树视图 | Pending |
| PROJECT-PLATFORM-S10-M4-T08 | 实现反向引用与影响分析的最小披露、缓存失效和校准事件 | relation event 仅触发校准；消费者经 resolver/API 重读，不读取关系私表 | Pending |
| PROJECT-PLATFORM-S10-M4-T09 | 实现 legacy `issue_relations` 显式 backfill、verify、resume 和 rollback 边界 | manifest 锚定旧事实与 WorkItem map；非 WorkItem 目标分流保留，不伪造规范边 | Pending |
| PROJECT-PLATFORM-S10-M4-T10 | 交付 owner/admin 的迁移失败清单、重试与一致性恢复入口 | 需要原因、expected version 和危险确认；企业管理员不自动获得内容可见性 | Pending |
| PROJECT-PLATFORM-S10-M4-T11 | 完成关系控件、反向引用、影响裁剪和 legacy backfill 自动化测试 | 无隐藏端点泄漏、陈旧计数、无限图遍历、重复迁移或不可回退污染 | Pending |
| PROJECT-PLATFORM-S10-M4-T12 | 同步对象/事件/迁移/runbook 合同并完成 M4 checkpoint | 文档只声明后端与交互合同；真实配置/成员 UI 和 Stage 结论仍属于 M5 | Pending |

### PROJECT-PLATFORM-S10-M5 配置与成员 UI、真实验收及 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M5-T01 | 审计 M1-M4 实现、报告、迁移、边界和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Pending |
| PROJECT-PLATFORM-S10-M5-T02 | 交付空间配置侧关系定义、方向、反向、类型矩阵、基数和删除策略 UI | 草稿/发布/兼容/rollback 边界清晰；未知定义与非法组合即时诊断 | Pending |
| PROJECT-PLATFORM-S10-M5-T03 | 交付成员详情侧关系查看、选择、创建、撤销和冲突恢复 UI | 只执行服务端 capability；刷新一致；失败保留目标与原因，不泄露不可见端点 | Pending |
| PROJECT-PLATFORM-S10-M5-T04 | 交付父子导航、局部树、拆解和 reparent UI | 面包屑/父子/同级、折叠、替代列表和危险确认可用；服务端拒绝环和超基数 | Pending |
| PROJECT-PLATFORM-S10-M5-T05 | 交付依赖/阻塞、反向引用和有界影响分析 UI | 方向、截断、权限裁剪和删除态可理解；不呈现未实现的关键路径/工期结论 | Pending |
| PROJECT-PLATFORM-S10-M5-T06 | 交付 legacy backfill、失败清单、续跑、verify 和恢复 UI | owner/admin 操作显式且可审计；非 WorkItem 目标保留原语义，不静默丢弃 | Pending |
| PROJECT-PLATFORM-S10-M5-T07 | 完成长名称、密集关系、键盘、焦点、窄屏和无障碍交互 | 1440/1366/820 关键视口可用；树与列表可替代导航；错误关联完整 | Pending |
| PROJECT-PLATFORM-S10-M5-T08 | 执行 owner/admin/member/guest/non-member/enterprise-admin 真实隔离浏览器验收 | 定义、关系、层级、反向、影响和最小披露符合服务端决策 | Pending |
| PROJECT-PLATFORM-S10-M5-T09 | 执行并发建边/reparent、环、删除、离线和 backfill 真实浏览器验收 | 无单端边、双成功、非法环、输入丢失；刷新后关系/历史/活动一致 | Pending |
| PROJECT-PLATFORM-S10-M5-T10 | 执行完整 PostgreSQL/Flyway、后端、前端、协作、架构、安全和生成物门禁 | full gate 无阻断；迁移/恢复日志 fresh；mock 不冒充真实浏览器/数据库证据 | Pending |
| PROJECT-PLATFORM-S10-M5-T11 | 同步当前架构、Program、专项索引、模块/对象/事件/运维合同并复核 S11 准入 | 文档只声明已实现事实；S11 权限边界与禁止提前项冻结 | Pending |
| PROJECT-PLATFORM-S10-M5-T12 | 给出 S10 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 五份报告、工作上下文、60 Task 和文档一致；仅无阻断时 Completed | Pending |

## 6. Stage 验收

- 关系定义进入唯一不可变 configuration snapshot 和草稿/校验/diff/发布/rollback/template 链路，永久 key、方向、反向、端点类型、基数和删除策略稳定。
- 规范 relation edge、命令回执和不可变历史拥有独立权威；正向、反向、层级 path/closure 和影响投影均可从边重建，不读写流程私表。
- 普通、父子、依赖和阻塞关系具备 expected version、幂等、并发单赢家、重复归一、父子/依赖环检测和确定生命周期。
- 双端授权和最小披露覆盖六身份、跨 workspace/space、归档/删除端点、反向引用、层级截断和影响分析。
- 子项拆解、attach/detach/reparent、局部层级导航、一致性扫描/rebuild 和恢复具备原子、可审计、可续跑语义。
- legacy `issue_relations` 通过显式 manifest/backfill/verify 承接；非 WorkItem 目标保留原对象关系语义，不被伪造成 WorkItem 边。
- 空间配置 UI 与成员关系/层级/影响 UI 完成真实闭环；企业管理后台不承载日常关系配置或内容操作。
- S10 不实现 S11 细粒度权限、S13 全局树/保存视图、S14 甘特/关键路径、S17 自动化或 S18 跨空间同步，也不把基础预算表述为生产容量。

## 7. 当前起点

S09 路线已归档，S10 在 Program revision 27 激活为唯一当前 Stage。首个允许推进的任务是 `PROJECT-PLATFORM-S10-M1-T01`；本次激活只建立五个 Milestone、60 个 Task 的执行与验收合同，不声明任何 S10 关系 schema、API、迁移或 UI 已实现。
