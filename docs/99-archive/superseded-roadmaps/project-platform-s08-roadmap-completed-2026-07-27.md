---
title: PROJECT-PLATFORM-S08 轻量状态流定义与运行时当前执行路线
status: completed
route: PROJECT-PLATFORM-S08
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 24
stage: PROJECT-PLATFORM-S08
stage_final_milestone: PROJECT-PLATFORM-S08-M4
last_code_check: 2026-07-26
source_rule: 本文件是已归档的历史执行路线快照，不再作为当前执行入口。
---

# PROJECT-PLATFORM-S08 轻量状态流定义与运行时

## 1. Stage 目标

在 S07 已交付的规范 WorkItem、不可变版本绑定、动态字段、命令回执、活动账本和用户侧竖切基础上，交付适用于任务、缺陷、内容等轻量事项的版本化状态流。空间管理员可以在配置草稿中定义状态、动作、转换、守卫、必填字段和动作授权，经 S06 发布后由绑定该版本的 WorkItem 运行；空间成员可以根据服务端 `availableActions` 安全流转、回退、重开、终止和恢复。

S08 的完成标准不是“给 WorkItem 增加 status 字段”。状态定义必须进入完整 published snapshot，运行实例必须只解释自身绑定版本；动作执行必须具备服务端授权、守卫、乐观锁、持久化幂等、不可变历史、审计和事务 outbox。S08 不实现 S09 节点 token、串并行、分支汇聚、会签或节点交付物，也不提前实现 S14 看板拖拽、S17 自动化编排。

## 2. 固定输入与当前事实

- S07 完成路线已归档；`project_work_items` 是唯一规范实例模型，S08 起始 schema 为 V090，M1 完成后的当前 schema 为 V091。
- WorkItem 显式绑定 `type_definition_id + type_version_id + config_hash`；状态流运行时只经 `PublishedSnapshotAdapter` 读取绑定版本，不回读 active draft 或 live 配置表。
- S06 提供唯一配置草稿、校验、不可变发布、diff、rollback、模板 lineage 和兼容矩阵；状态流定义必须扩展这些合同，不能建立第二套发布权威。
- S07 提供 expected version、持久化 command receipt、活动序列、审计/outbox 和 `availableActions` 投影；状态动作复用这些协议，不复制第二套 WorkItem 命令框架。
- 轻量状态流权威只保存单一 current state；S09 节点流权威保存 workflow instance 与 active token。两类运行时可共享 command/event SPI，但不得共用私有运行表或把一方降维成另一方。
- owner、space-admin、member、guest、non-member、enterprise-admin 六身份继续使用空间与内容边界；企业管理员不因治理角色自动获得状态流配置或工作项内容权限。
- S07 前已存在且绑定旧 snapshot 的 WorkItem 不得被静默补状态；初始化、升级、映射和失败清单必须显式、可校验、可回退。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 定义、草稿、发布和 runtime 必须保持单一版本链；任何从 live repository、最新类型版本或前端文本补算状态动作的实现均为阻断。
3. 状态、动作和转换使用永久 semantic key；展示名可变，运行历史和事件不得依赖展示文本定位。
4. 转换守卫只允许声明式、可审计、可确定求值的表达式；任意代码执行、动态 SQL、前端 guard 和未注册副作用禁止进入基础合同。
5. 动作命令在同一事务提交 current state、WorkItem aggregate version、活动、workflow history、审计、outbox 和 receipt；失败不得产生半转换。
6. `availableActions` 与真实执行必须调用同一服务端决策；列表投影可以批量计算，但不能成为授权事实源。
7. 终止、恢复、回退和重开必须是显式动作并保留完整历史；禁止删除历史或直接改 current state 纠错。
8. M1-M3 使用影响范围门禁；M4 执行完整 Flyway、后端、前端、协作、架构、安全、六身份真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M1 | 状态、动作、转换、守卫与版本化定义底座 | S07 归档；Program revision 23 | `docs/90-reports/project-platform-s08-m1-execution-report.md` | Done |
| PROJECT-PLATFORM-S08-M2 | 状态运行时、权限、幂等与不可变历史 | M1 | `docs/90-reports/project-platform-s08-m2-execution-report.md` | Done |
| PROJECT-PLATFORM-S08-M3 | 回退、重开、终止、恢复与存量实例承接 | M1-M2 | `docs/90-reports/project-platform-s08-m3-execution-report.md` | Done |
| PROJECT-PLATFORM-S08-M4 | 状态配置器、成员执行 UI、综合验收与 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s08-m4-execution-report.md` | Done |

## 5. 详细任务

### PROJECT-PLATFORM-S08-M1 状态、动作、转换、守卫与版本化定义底座

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M1-T01 | 审计 S06 published snapshot、S07 WorkItem/活动/命令合同、现有 issue 状态语义和 S09 边界 | 可复用端口、旧状态数据、调用方、迁移风险和禁止依赖可定位；无未经证实的定义假设 | Done |
| PROJECT-PLATFORM-S08-M1-T02 | 冻结 StateDefinition、ActionDefinition、TransitionDefinition、GuardDefinition 和状态分类领域合同 | 永久 key、展示语义、initial/active/terminal/canceled、版本和错误语义无歧义 | Done |
| PROJECT-PLATFORM-S08-M1-T03 | 扩展完整 configuration snapshot schema 承载轻量状态流定义 | snapshot/hash/canonicalizer 覆盖状态流；旧 schema、无状态流类型和未来 schema 行为明确 | Done |
| PROJECT-PLATFORM-S08-M1-T04 | 落地 current state、状态命令回执和不可变 workflow history Flyway schema | workspace/space/workItem/version 复合边界、唯一性、FK、索引、清理闭包和不可变保护完整 | Done |
| PROJECT-PLATFORM-S08-M1-T05 | 把状态流编辑接入 S06 唯一配置草稿与 Repository/DTO | 定义只存在于草稿 snapshot；不建立 live/published 双写表；乐观版本和幂等沿用现有合同 | Done |
| PROJECT-PLATFORM-S08-M1-T06 | 实现结构校验：唯一 initial、可达性、终态、重复 key、悬空转换和死路检测 | 非法图产生稳定 diagnostics；校验确定、顺序无关且不静默修复用户定义 | Done |
| PROJECT-PLATFORM-S08-M1-T07 | 实现声明式 guard、字段条件、参与者/角色条件注册表和 canonicalizer | 只接受白名单 operator/operand；类型错误、隐藏字段和未知扩展失败关闭 | Done |
| PROJECT-PLATFORM-S08-M1-T08 | 定义动作授权、转换必填字段和受控副作用合同 | 授权角色、required field、字段 patch 和副作用引用均可版本化；不执行任意代码 | Done |
| PROJECT-PLATFORM-S08-M1-T09 | 把状态流变化接入配置 diff、兼容矩阵、发布和 rollback | key 删除/重定向、initial/terminal/guard 变化分级稳定；blocked/migration_required 不被普通发布绕过 | Done |
| PROJECT-PLATFORM-S08-M1-T10 | 为系统预置类型提供确定性轻量状态流草稿/模板输入 | 重复安装/升级 hash 稳定；不覆盖 workspace 本地修改；无默认状态猜测 | Done |
| PROJECT-PLATFORM-S08-M1-T11 | 完成 schema、校验、草稿、发布、兼容和跨空间负向自动化测试 | 空库/升级、并发、幂等、不可变、越权、未知 guard 和 S09 私表隔离均通过 | Done |
| PROJECT-PLATFORM-S08-M1-T12 | 同步目标/当前架构、模块/对象/事件合同并完成 M1 checkpoint | 文档只声明定义底座；runtime 尚未激活，M2 输入、证据和剩余风险清晰 | Done |

### PROJECT-PLATFORM-S08-M2 状态运行时、权限、幂等与不可变历史

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M2-T01 | 复核 M1 定义、snapshot、schema、报告和未关闭阻断 | 12 项任务逐项可追溯；阻断项 Reopen，不以文档结论代替实现 | Done |
| PROJECT-PLATFORM-S08-M2-T02 | 实现绑定 snapshot 驱动的 state runtime adapter 与初始状态解析 | 只解释 WorkItem 绑定版本/hash；无状态流类型返回显式能力缺失；不查询最新配置 | Done |
| PROJECT-PLATFORM-S08-M2-T03 | 实现 current state Repository、行锁、乐观版本和原子初始化 | 单实例只有一个 current state；并发初始化唯一；WorkItem/state 版本不漂移 | Done |
| PROJECT-PLATFORM-S08-M2-T04 | 实现统一 action/transition/guard 服务端决策与批量 `availableActions` | 投影和执行共享同一 decision；reason code、披露范围、策略版本和动作排序稳定 | Done |
| PROJECT-PLATFORM-S08-M2-T05 | 实现字段/参与者/空间角色 guard 与转换 required field 校验 | hidden 字段零披露；guest/member/admin 边界准确；失败不暴露不可见值或目标状态 | Done |
| PROJECT-PLATFORM-S08-M2-T06 | 实现状态动作命令和同事务字段 patch | expected version、from state、guard、字段 patch、current state、WorkItem version 原子提交 | Done |
| PROJECT-PLATFORM-S08-M2-T07 | 接入持久化幂等回执与规范 request hash | 相同 request ID 精确重放；异载荷冲突；并发败者不重复历史、事件或副作用 | Done |
| PROJECT-PLATFORM-S08-M2-T08 | 实现不可变 workflow history 与调用者可见历史投影 | 单调序号、from/to/action/actor/version/decision 可追溯；敏感 guard 输入不落正文 | Done |
| PROJECT-PLATFORM-S08-M2-T09 | 接入 WorkItem 活动、审计和事务 outbox 公共合同 | `workflow.action_executed/state_changed` 与 activity/receipt 同事务；事件 schema 可重放且最小披露 | Done |
| PROJECT-PLATFORM-S08-M2-T10 | 交付用户状态读取、可用动作和执行 API/DTO/错误合同 | 用户路由不混入治理 API；404/403/409/422 稳定；DTO 不暴露策略正文或表结构 | Done |
| PROJECT-PLATFORM-S08-M2-T11 | 完成六身份、并发、重放、guard、故障注入和跨空间自动化测试 | stale command 更新零行；无双转换、半历史、越权枚举、重复事件或隐藏值泄漏 | Done |
| PROJECT-PLATFORM-S08-M2-T12 | 执行代表性状态动作/列表动作投影预算并完成 M2 checkpoint | SQL plan、批量上界和延迟可复现；不冒充复杂节点流或生产容量结论 | Done |

### PROJECT-PLATFORM-S08-M3 回退、重开、终止、恢复与存量实例承接

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M3-T01 | 冻结 forward、return、reopen、terminate、restore 和 correction 动作语义 | 每类动作的来源、目标、授权、终态和历史语义明确；不以直接改状态代替命令 | Done |
| PROJECT-PLATFORM-S08-M3-T02 | 实现显式回退转换与最近状态/指定状态边界 | 只允许版本中声明的回退；目标可达、guard 和 required field 重新校验；历史不删除 | Done |
| PROJECT-PLATFORM-S08-M3-T03 | 实现终态、重开和重复终止幂等语义 | terminal/canceled 分类稳定；重开目标显式；重复命令精确重放且不产生伪历史 | Done |
| PROJECT-PLATFORM-S08-M3-T04 | 实现终止、恢复与 WorkItem archive/restore 生命周期协同 | archive 不伪装业务终止；恢复不自动猜测状态；非法组合受控拒绝 | Done |
| PROJECT-PLATFORM-S08-M3-T05 | 设计并实现 pre-S08 WorkItem 显式状态初始化/批量 backfill | manifest、目标版本、初始状态、失败清单、幂等和校验可追溯；不静默修改旧实例 | Done |
| PROJECT-PLATFORM-S08-M3-T06 | 实现实例配置版本升级时的 state key 映射与兼容阻断 | 删除/合并/重命名有显式 map；无映射或 blocked 变化失败关闭；旧绑定仍可运行 | Done |
| PROJECT-PLATFORM-S08-M3-T07 | 实现空间管理员受控恢复/纠错入口 | 仅空间 owner/admin 可执行；需要原因、expected version、审计和危险确认；企业管理员不自动可见内容 | Done |
| PROJECT-PLATFORM-S08-M3-T08 | 实现恢复失败、并发纠错和部分副作用故障原子性 | current state、history、activity、audit、outbox、receipt 全回滚或全提交 | Done |
| PROJECT-PLATFORM-S08-M3-T09 | 完善状态生命周期事件、通知/搜索消费合同和 replay 边界 | 事件可去重重放；消费者不读取状态私表；未知版本进入 dead letter | Done |
| PROJECT-PLATFORM-S08-M3-T10 | 完成回退/重开/终止/恢复/升级/纠错六身份与故障自动化测试 | 终态、stale、重复、越权、跨空间、隐藏 guard、映射缺失和回滚负例完整 | Done |
| PROJECT-PLATFORM-S08-M3-T11 | 执行 V001/V061/V078/V085/V090 至最新迁移与状态 backfill rehearsal | 空库、历史基线、重复 migrate、非空实例、失败续跑和恢复校验通过 | Done |
| PROJECT-PLATFORM-S08-M3-T12 | 交付状态恢复 runbook、兼容矩阵并完成 M3 checkpoint | 操作步骤、停止条件、回退边界和 S09 接口清晰；不宣称节点流已实现 | Done |

### PROJECT-PLATFORM-S08-M4 状态配置器、成员执行 UI、综合验收与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M4-T01 | 审计 M1-M3 实现、报告、迁移、边界和未关闭 gap | 48 个任务逐项可追溯；阻断项 Reopen，不以 Remaining Gap 弱化完成标准 | Done |
| PROJECT-PLATFORM-S08-M4-T02 | 交付空间配置侧状态/动作/转换编辑器 | 编辑、排序、连接、终态、授权、guard、必填和 diagnostics 可操作；不进入企业管理后台 | Done |
| PROJECT-PLATFORM-S08-M4-T03 | 交付配置预览、diff、兼容提示、发布阻断和回滚交互 | 草稿/发布边界清晰；blocked/migration_required 不可被前端绕过；失败保留输入 | Done |
| PROJECT-PLATFORM-S08-M4-T04 | 在 WorkItem 详情接入当前状态、历史和可用动作执行 UI | 只展示服务端动作；成功后事实一致；409/422/超时/离线可恢复且不静默覆盖 | Done |
| PROJECT-PLATFORM-S08-M4-T05 | 完成键盘、焦点、长名称、空状态、窄屏和无障碍交互 | 1440/1366/820 关键视口可用；状态/动作文本不截断或越界；键盘路径完整 | Done |
| PROJECT-PLATFORM-S08-M4-T06 | 执行 owner/admin/member/guest/non-member/enterprise-admin 真实隔离浏览器验收 | 配置、查看、执行、回退、纠错和最小披露符合服务端决策 | Done |
| PROJECT-PLATFORM-S08-M4-T07 | 执行并发动作、离线重试、终态重开、恢复和 backfill 真实浏览器验收 | 冲突不丢输入；重试不重复动作；刷新后 state/history/activity 一致 | Done |
| PROJECT-PLATFORM-S08-M4-T08 | 执行完整 PostgreSQL/Flyway、非空实例升级与故障恢复 rehearsal | 多历史基线、重复 migrate、显式 map、失败清单、续跑和恢复结果有 fresh 证据 | Done |
| PROJECT-PLATFORM-S08-M4-T09 | 执行完整后端、前端、collaboration、架构、工作台、安全和生成物门禁 | full gate 无阻断；日志 fresh 可复现；mock 不冒充真实浏览器/数据库证据 | Done |
| PROJECT-PLATFORM-S08-M4-T10 | 同步当前架构、Program、专项索引、模块/事件/运维合同 | 文档只声明已实现事实；状态流与 S09 节点流、S14/S17 后续边界清晰 | Done |
| PROJECT-PLATFORM-S08-M4-T11 | 复核 S09 准入和共享 command/event SPI | S09 不复用 state 私表，不把 token 图降维成 status；共同协议和禁止项冻结 | Done |
| PROJECT-PLATFORM-S08-M4-T12 | 给出 S08 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 四份报告、工作上下文、48 Task 和文档一致；仅无阻断时 Completed，S09 保持 Planned | Done |

## 6. Stage 验收

- 状态、动作、转换、守卫、必填字段和授权进入完整不可变 configuration snapshot，并经过草稿、校验、diff、发布、rollback 和模板链路。
- 轻量状态运行时只保存单一 current state，且只解释 WorkItem 绑定的 type version/config hash；不存在对 live/draft 配置的回读。
- 动作决策、`availableActions` 和执行使用同一服务端授权/guard；六身份、跨 workspace/space 和 hidden 字段最小披露通过。
- 转换命令具备 expected version、持久化幂等、不可变 history、活动、审计和事务 outbox，故障不产生半转换。
- 回退、重开、终止、恢复和纠错均为显式命令并保留历史；archive/restore 与业务终态不混淆。
- pre-S08 实例初始化及配置版本升级使用显式 manifest/mapping/failure/verify，不静默改变旧实例语义。
- 空间配置 UI 与用户执行 UI 完成真实闭环；企业管理后台不承载日常状态配置或流转。
- S08 不创建 S09 node token、并行汇聚或会签权威，不冒充 S14 看板和 S17 自动化能力。
