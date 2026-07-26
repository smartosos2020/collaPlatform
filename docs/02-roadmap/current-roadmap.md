---
title: PROJECT-PLATFORM-S07 统一工作项运行时与第一阶段迁移当前执行路线
status: completed
route: PROJECT-PLATFORM-S07
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 22
stage: PROJECT-PLATFORM-S07
stage_final_milestone: PROJECT-PLATFORM-S07-M5
last_code_check: 2026-07-26
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S07 统一工作项运行时与第一阶段迁移

## 1. Stage 目标

在 S02-S06 已交付的空间、类型、字段、布局、字段访问、不可变发布版本和 `PublishedSnapshotAdapter` 基础上，建立统一的规范 WorkItem 运行时，把 project、requirement、task、bug 等类型实例收敛到同一持久模型、命令和读取合同，并完成 legacy `projects/issues` 的第一阶段可恢复迁移。

S07 的完成标准不是“新增一张工作项表”。它必须证明新实例只绑定并解释不可变发布快照，动态值写入原子且可审计，旧链接和平台对象可稳定解析，迁移具备不可变 manifest、校验、续跑与回滚，用户侧真实创建、编辑、评论和附件形成闭环。S07 不实现 S08 状态流配置、S09 节点流、S10 关系图或 S13 高级保存视图。

## 2. 固定输入与当前事实

- S06 完成路线已归档；当前 schema 为 V085，`PublishedSnapshotReader/PublishedSnapshotAdapter` 是运行时唯一允许使用的配置读取端口。
- 每个规范实例必须显式绑定 `type_definition_id + type_version_id + config_hash`；发布新版本不得静默改变既有实例解释。
- 规范字段值采用 WorkItem 原子 JSONB 权威值与受控类型化 query projection 的混合模型；只有发布快照声明 query/sort/group capability 的字段可建立投影。
- legacy `projects/issues` 仍是当前业务事实源；S07 必须先画像、映射和 shadow compare，再按 workspace/space 分阶段切换，不允许双写。
- S02 已有 legacy project -> space/member 映射；S07 必须复用该显式映射，不得按 UUID 相同推断归属。
- 旧链接、搜索、IM、通知、审计、文件和平台对象必须经统一 `work_item` resolver 与显式 ID map 定位，不能跨模块读取 legacy 私有表。
- 配置规模预算不等于实例容量。S07 负责规范运行路径和代表性查询预算；10 万工作项复杂保存视图与生产容量结论仍由 S13/PLATFORM-SCALE 后续稳定负载复核。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、fresh Acceptance Evidence 和执行报告行。
2. 所有实例读写均带 workspace/space/type 复合边界；owner、space-admin、member、guest、non-member、enterprise-admin 六身份必须正反验证。
3. 运行时不得注入 active draft、S04/S05 live repository 或配置 command service；未知 snapshot schema、hash 不一致和 legacy partial 必须失败关闭。
4. 创建、更新、评论、附件和迁移命令均使用持久化 receipt、规范 request hash、乐观版本、审计和事务 outbox；相同请求精确重放，异载荷冲突。
5. legacy 切流按 `legacy -> shadow migrate -> canonical write -> canonical default -> old write closed` 推进；每阶段有 workspace flag、观测、kill switch 和回退证据。
6. 迁移计划和批次基于同一一致性快照，manifest 不可变；批次校验与 workspace 收敛校验分离，失败清单不得被续跑覆盖。
7. M1-M4 使用影响范围门禁；M5 执行 V001/V061/V065/V078/V085 至最新迁移、完整后端/前端/collaboration/架构/安全/真实隔离浏览器和 `route-final`。
8. 若任务依赖、真实代码或迁移画像推翻当前假设，先更新 Program/目标架构并记录 revision，不得以“Remaining Gap”弱化阻断项。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M1 | 规范 WorkItem 持久模型、命令与 API | S06 归档；Program revision 21 | `docs/90-reports/project-platform-s07-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S07-M2 | 动态字段值、参与者、活动账本与查询投影 | M1 | `docs/90-reports/project-platform-s07-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S07-M3 | legacy 读取适配、ID map、统一 resolver 与受控切流 | M1-M2 | `docs/90-reports/project-platform-s07-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S07-M4 | 分批迁移、独立校验、续跑、回滚与演练 | M1-M3 | `docs/90-reports/project-platform-s07-m4-execution-report.md` | Completed |
| PROJECT-PLATFORM-S07-M5 | 用户侧规范工作项竖切、综合验收与 Stage 收口 | M1-M4 | `docs/90-reports/project-platform-s07-m5-execution-report.md` | Completed |

## 5. 详细任务

### PROJECT-PLATFORM-S07-M1 规范 WorkItem 持久模型、命令与 API

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M1-T01 | 审计 legacy project/issue 模型、S02 space map、S03-S06 发布合同、现有 API/UI、评论/附件和平台对象链路 | 表、代码 owner、写入口、依赖、可复用合同、迁移风险和禁止模式可定位；无未经证实的切流假设 | Done |
| PROJECT-PLATFORM-S07-M1-T02 | 冻结 WorkItem 标识、展示编号、类型绑定、生命周期、版本和错误领域合同 | UUID/number 唯一性、type version/config hash 绑定、active/archived 边界、404/403/409 语义无歧义 | Done |
| PROJECT-PLATFORM-S07-M1-T03 | 设计并落地 `project_work_items`、编号计数器和实例命令回执 Flyway schema | workspace/space/type/version 复合 FK、唯一约束、乐观版本、索引、owner 和清理闭包完整 | Done |
| PROJECT-PLATFORM-S07-M1-T04 | 实现 WorkItem Repository、复合边界查询、行锁和 keyset 分页 | 所有查询显式带 workspace/space；单条与列表不越权枚举；并发更新稳定冲突 | Done |
| PROJECT-PLATFORM-S07-M1-T05 | 实现运行时 snapshot binding 与静态依赖守卫 | 创建事务锁定 current published version 并保存 hash；运行包只依赖 adapter；live/draft 注入被测试阻断 | Done |
| PROJECT-PLATFORM-S07-M1-T06 | 实现创建命令、默认值应用、服务端字段访问和原子回执 | 默认值/required/write 决策来自绑定 snapshot；实例、审计、outbox、receipt 同事务；重放精确 | Done |
| PROJECT-PLATFORM-S07-M1-T07 | 实现读取详情、列表摘要与 `availableActions` 投影 | hidden 字段零披露；create/detail layout 输入来自绑定 snapshot；未知字段或 schema 失败关闭 | Done |
| PROJECT-PLATFORM-S07-M1-T08 | 实现基础更新、归档和恢复命令 | expected version、字段 write 决策、状态约束、审计/outbox、幂等和失败原子性完整 | Done |
| PROJECT-PLATFORM-S07-M1-T09 | 交付用户协作 API、DTO、错误和分页合同 | 路由不混入管理后台；DTO 不暴露表名/策略正文；错误码和 canonical location 稳定 | Done |
| PROJECT-PLATFORM-S07-M1-T10 | 接入平台对象注册、导航 identity 和事务 outbox | canonical `work_item` object identity 可解析；事件含稳定 event/object/config version，不复制隐藏值 | Done |
| PROJECT-PLATFORM-S07-M1-T11 | 完成 Repository/API/并发/幂等/故障注入与六身份自动化测试 | 正反例覆盖跨 workspace、跨 space、伪造组合 ID、未知 snapshot、回滚和精确重放 | Done |
| PROJECT-PLATFORM-S07-M1-T12 | 同步当前架构、模块/事件合同和数据库 owner，完成 M1 checkpoint | 文档只声明已实现规范实例底座；legacy 仍未切流，M2 输入与证据可复用 | Done |

### PROJECT-PLATFORM-S07-M2 动态字段值、参与者、活动账本与查询投影

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M2-T01 | 冻结动态值 canonical JSON、unset/null、多值、用户、附件、引用、interval/computed 的运行语义 | 每类值的编码、校验、比较、脱敏、升级和不支持行为有稳定合同 | Done |
| PROJECT-PLATFORM-S07-M2-T02 | 落地字段投影、参与者、活动历史和相关命令回执 schema | 规范值与投影同事务；参与者唯一性/角色约束、不可变活动序列和复合隔离完整 | Done |
| PROJECT-PLATFORM-S07-M2-T03 | 实现 snapshot 驱动的字段 codec、默认值、校验与 canonicalizer | 11 类已注册字段行为明确；未知/禁用/隐藏字段失败关闭；相同语义同 hash | Done |
| PROJECT-PLATFORM-S07-M2-T04 | 实现动态值 Repository 和原子 patch 命令 | JSONB 权威值、实例 version、投影、活动、审计/outbox/receipt 同事务提交 | Done |
| PROJECT-PLATFORM-S07-M2-T05 | 实现 capability 驱动的类型化查询投影与重建 | 仅已发布 query/sort/group capability 可投影；受控 SQL 模板、漂移检测和可重建性通过 | Done |
| PROJECT-PLATFORM-S07-M2-T06 | 实现服务端字段级 read/write/required 与最小披露 | hidden 字段不进入值、错误、差异、活动、搜索或事件；只读/必填规则不可由前端绕过 | Done |
| PROJECT-PLATFORM-S07-M2-T07 | 实现参与者添加、变更、移除和角色语义 | 权限、最后责任人约束、用户状态、幂等、并发和活动记录完整 | Done |
| PROJECT-PLATFORM-S07-M2-T08 | 实现不可变活动账本和用户可见投影 | 创建/更新/参与者/归档等事件有稳定序号与 actor；敏感前后值按调用身份投影 | Done |
| PROJECT-PLATFORM-S07-M2-T09 | 实现基础 filter/sort 查询合同和索引预算 | 只接受 capability 白名单；无能力字段受控拒绝；无动态 SQL 注入和明显 N+1 | Done |
| PROJECT-PLATFORM-S07-M2-T10 | 接入搜索/通知/协作消费所需稳定事件，不提前实现 S12/S17 产品能力 | outbox schema、去重键和 replay 合同稳定；消费者只能使用公共合同/解析器 | Done |
| PROJECT-PLATFORM-S07-M2-T11 | 完成字段类型、投影一致性、参与者、活动、权限、并发和故障自动化测试 | 正反例、projection rebuild、事务回滚、隐藏值零泄漏和跨空间负例通过 | Done |
| PROJECT-PLATFORM-S07-M2-T12 | 执行代表性字段/实例查询预算并完成 M2 checkpoint | 结果标注数据规模、SQL plan 和限制；不冒充 10 万复杂视图或生产容量结论 | Done |

### PROJECT-PLATFORM-S07-M3 legacy 读取适配、ID map、统一 resolver 与受控切流

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M3-T01 | 画像 legacy projects/issues、成员、评论、附件、状态、引用和所有读写调用方 | 数量/hash/水位、脏数据、ID 冲突、孤儿、跨模块消费者和 P0/P1 风险可追溯 | Done |
| PROJECT-PLATFORM-S07-M3-T02 | 冻结显式 ID map、兼容读取、workspace cutover flag、阶段和 kill switch 合同 | source/target identity、冲突分支、读优先级、禁止双写、回退和旧写关闭语义明确 | Done |
| PROJECT-PLATFORM-S07-M3-T03 | 落地 S07 migration batch/unit/manifest/failure、legacy ID map 和 cutover schema | 不可变 manifest、生命周期归属、复合 FK、唯一性、失败追加和索引完整 | Done |
| PROJECT-PLATFORM-S07-M3-T04 | 实现 project/issue -> WorkItem 显式映射 resolver | UUID 复用也写 map；冲突生成新 ID；旧 route 返回 canonical location；跨空间最小披露 | Done |
| PROJECT-PLATFORM-S07-M3-T05 | 实现 legacy project/issue 只读投影适配器 | 未迁移对象可按规范 DTO 读取；适配器不写 canonical、不伪造 published snapshot 或字段能力 | Done |
| PROJECT-PLATFORM-S07-M3-T06 | 实现统一 `work_item` resolver 与平台对象兼容注册 | 新旧链接、搜索、IM、通知、审计、文件引用均走公共 resolver；无私表跨模块读取 | Done |
| PROJECT-PLATFORM-S07-M3-T07 | 实现 workspace/space 分阶段读取路由与 shadow compare | 每阶段可观测命中、漂移、错误和延迟；批次与收敛状态不混淆；kill switch 可恢复 | Done |
| PROJECT-PLATFORM-S07-M3-T08 | 实现 canonical-only 新写和 legacy 旧写受控关闭合同 | 不双写；切流后旧写返回 stable gone/conflict 与 canonical location；回退不覆盖新事实 | Done |
| PROJECT-PLATFORM-S07-M3-T09 | 完成 resolver、旧链接、读取对照、切流、回退、六身份和跨模块契约测试 | 已迁移/未迁移/冲突/隐藏/跨空间/消费者 replay 正反例完整 | Done |
| PROJECT-PLATFORM-S07-M3-T10 | 交付兼容监测、告警、runbook 和调用方清单 | 旧读回退率、旧写调用、map 漂移和消费者死信可定位；P0 有明确停止条件 | Done |
| PROJECT-PLATFORM-S07-M3-T11 | 同步兼容注册表、当前架构和事件矩阵，完成 M3 checkpoint | legacy 与 canonical 权威阶段清晰；M4 迁移输入冻结且无静默双事实源 | Done |

### PROJECT-PLATFORM-S07-M4 分批迁移、独立校验、续跑、回滚与演练

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M4-T01 | 冻结 preflight、plan、manifest、批次/单元状态、failure、verify、resume 和 rollback 合同 | 状态机、命令、稳定错误、权限、RTO/停止条件和证据归属无歧义 | Done |
| PROJECT-PLATFORM-S07-M4-T02 | 实现同一 REPEATABLE_READ 快照的数据画像、目标版本选择、plan 和输入 fingerprint | plan 与 hash 同视图；锁外变化被过期检测拒绝；dry-run 不写业务事实 | Done |
| PROJECT-PLATFORM-S07-M4-T03 | 实现不可变 migration manifest 和 project 单元边界 | manifest 保存完整生命周期归属；单元覆盖 project、issue 及附属对象；后续尝试不覆盖历史 | Done |
| PROJECT-PLATFORM-S07-M4-T04 | 实现 project/issue 基础字段、类型、空间、编号和 snapshot binding 迁移 | 每个目标实例绑定完整 published version/hash；ID 冲突显式映射；无默认猜测 | Done |
| PROJECT-PLATFORM-S07-M4-T05 | 实现动态字段、参与者、评论、附件和活动 provenance 迁移 | 值转换/拒绝清单稳定；附件引用不丢失；来源 ID/checksum/batch 可追溯 | Done |
| PROJECT-PLATFORM-S07-M4-T06 | 实现小批量执行、限速、暂停、续跑和并发所有权 | 同批次 resume 幂等；lease/fencing 或数据库所有权防双执行；失败单元隔离 | Done |
| PROJECT-PLATFORM-S07-M4-T07 | 实现批次 manifest verify | count/hash/map/字段/附件/孤儿按原 manifest 校验；后续批次不能让旧批次假成功 | Done |
| PROJECT-PLATFORM-S07-M4-T08 | 实现独立 workspace convergence verify 和 shadow compare | 收敛校验不改历史批次结论；已迁移/未迁移/回退数据均可解释 | Done |
| PROJECT-PLATFORM-S07-M4-T09 | 实现写切流前 rollback 与写切流后补偿/kill switch | pre-cutover 可删除本批次目标且保留审计；post-cutover 不覆盖 canonical 新写，走显式补偿 | Done |
| PROJECT-PLATFORM-S07-M4-T10 | 交付管理员迁移 API/CLI、进度、失败下载和操作 runbook | owner 边界、危险确认、最小披露、重试入口和 canonical location 清晰 | Done |
| PROJECT-PLATFORM-S07-M4-T11 | 执行真实 PostgreSQL/Flyway 空库和多历史基线迁移 rehearsal | V001/V061/V065/V078/V085 至最新及重复 migrate 通过；legacy sentinel 与历史快照不越界 | Done |
| PROJECT-PLATFORM-S07-M4-T12 | 执行竞态、崩溃、失败单元、resume、map 冲突、verify 假阳性和 rollback 故障注入 | 每类失败有 fresh 非空证据；无半单元、清单覆盖、历史结论改写或孤儿 | Done |
| PROJECT-PLATFORM-S07-M4-T13 | 完成安全/性能预算、迁移报告与 M4 checkpoint | 六身份、跨空间、日志脱敏、锁/SQL plan/批量预算达标；生产 cutover 仍需真实备份与批准 | Done |

### PROJECT-PLATFORM-S07-M5 用户侧规范工作项竖切、综合验收与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M5-T01 | 审计 M1-M4 实现、报告、迁移、边界和未关闭 gap | 60 个任务逐项可追溯；阻断项 Reopen，不以文档结论或 Remaining Gap 替代实现 | Done |
| PROJECT-PLATFORM-S07-M5-T02 | 交付用户侧类型入口、工作项列表、创建和详情路由 | 只展示有权空间/类型；空/加载/错误/旧链接重定向完整；不混入企业管理 UI | Done |
| PROJECT-PLATFORM-S07-M5-T03 | 用绑定 snapshot 接入 create/detail renderer 和动态值编辑 | 布局、条件、访问、默认值和校验均来自实例版本；无 live 配置回读 | Done |
| PROJECT-PLATFORM-S07-M5-T04 | 交付参与者、活动、评论和附件真实交互闭环 | 创建、编辑、参与、评论、上传/下载、归档/恢复可连续操作且刷新后事实一致 | Done |
| PROJECT-PLATFORM-S07-M5-T05 | 完成键盘、窄屏、焦点、长内容、离线/超时和冲突恢复 | 1440/1366/820 关键视口可用；失败保留用户输入；并发冲突不静默覆盖 | Done |
| PROJECT-PLATFORM-S07-M5-T06 | 执行 owner/admin/member/guest/non-member/enterprise-admin 真实隔离浏览器验收 | 页面入口、字段可见/可写、附件/评论、旧链接和最小披露符合服务端决策 | Done |
| PROJECT-PLATFORM-S07-M5-T07 | 执行 legacy 与 canonical 混合期真实浏览器和消费者链路验收 | 已迁移/未迁移对象、旧链接、平台对象、搜索/通知合同及 kill switch 可验证 | Done |
| PROJECT-PLATFORM-S07-M5-T08 | 执行完整迁移 rehearsal、备份恢复、切流和回退演练 | 非空真实形态夹具、失败 resume、批次 verify、收敛 verify、RTO 和回退结果有 fresh 证据 | Done |
| PROJECT-PLATFORM-S07-M5-T09 | 执行完整后端、迁移、前端、collaboration、架构、工作台、安全和生成物门禁 | full gate 无阻断；日志 fresh 且可复现；mock 不冒充真实浏览器/数据库证据 | Done |
| PROJECT-PLATFORM-S07-M5-T10 | 同步当前架构、Program、专项索引、模块/事件/兼容合同和运维 runbook | 文档只声明已实现事实；legacy 剩余边界、生产 cutover 限制和 owner 清晰 | Done |
| PROJECT-PLATFORM-S07-M5-T11 | 复核 S08 准入：规范实例身份、版本绑定和基础活动可被轻量状态流复用 | S08 不重建 WorkItem、字段值或迁移权威；状态流挂载点和禁止项冻结 | Done |
| PROJECT-PLATFORM-S07-M5-T12 | 给出 S07 Go/Reopen，完成 route-final 并把当前 Stage 置 none | 五份报告、工作上下文、60 Task 和文档一致；仅无阻断时 Completed，S08 保持独立激活 | Done |

## 6. Stage 验收

- `project_work_items` 是所有类型实例的唯一规范模型；实例显式绑定完整不可变 `type_version_id + config_hash`。
- 动态字段 JSONB 权威值、受控查询投影、参与者和不可变活动账本在实例事务中保持一致。
- 运行时只经 `PublishedSnapshotAdapter` 解释配置，静态和负向测试阻止 active draft/live repository 依赖。
- 创建、更新、参与者、评论、附件和迁移命令具备乐观版本、持久化幂等、审计/outbox 和故障原子性。
- legacy ID map、统一 resolver、旧链接、平台对象和跨模块消费者在混合期可稳定定位规范实例，且不存在双写。
- migration manifest 不可变；批次 verify 与 workspace convergence verify 分离；resume、回滚和故障演练不会覆盖历史或产生孤儿。
- 第一条用户侧规范工作项竖切完成创建、编辑、参与、评论、附件、归档/恢复及六身份真实隔离验收。
- S07 不冒充 S08 状态流、S10 关系或 S13 高级查询能力；生产切流仍受真实备份、观测和批准约束。
