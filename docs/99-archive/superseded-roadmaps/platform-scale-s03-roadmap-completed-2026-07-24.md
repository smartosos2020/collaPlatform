---
title: PLATFORM-SCALE-S03 异步 Worker 独立运行与可靠消费当前执行路线
status: completed
route: PLATFORM-SCALE-S03
program: PLATFORM-SCALE
program_doc: docs/00-product/initiatives/platform-scale-program.md
program_revision: 6
stage: PLATFORM-SCALE-S03
stage_final_milestone: PLATFORM-SCALE-S03-M5
last_code_check: 2026-07-24
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PLATFORM-SCALE-S03 异步 Worker 独立运行与可靠消费

## 1. Stage 目标

在 S02 已把 API、Worker、Event Gateway 和 Maintenance 运行职责隔离，并交付双 API 无状态基线的基础上，把当前单进程、单状态、硬编码分发的 `DomainEventWorker` 升级为可独立部署和横向增加实例的可靠异步消费组件。

S03 交付版本化事件 envelope、逐 Handler 投递与幂等回执、带 fencing 的 claim lease、超时接管、重试、dead letter、受控 replay、并发与背压、Notification/Search/realtime signal Handler 迁移，以及多 Worker 故障和积压恢复证据。S03 不实现 S04 的 Redis 多 Gateway fanout、客户端实时重连校准或旧 Spring 知识协同彻底退出，也不发布 S05 容量承诺。

## 2. 固定输入与当前缺口

- 上一 Stage：`PLATFORM-SCALE-S02` 已完成并归档为 `docs/99-archive/superseded-roadmaps/platform-scale-s02-roadmap-completed-2026-07-24.md`。
- 活动专项：`docs/00-product/initiatives/platform-scale-program.md` revision 5。
- 目标架构：`docs/01-architecture/platform-scale-target-architecture.md` revision 5，重点执行事件、Worker、观测和演进顺序章节。
- 运行基线：同一 Server artifact 以 `COLLA_RUNTIME_ROLE=worker` 独立运行；API 不创建 Worker Bean，`combined` 只用于 local/test。
- 当前事件事实：`domain_events` 只有 event/status/retry/next-attempt 等基础字段，claim 后只写 `processing`，没有 worker identity、lease expiry、fencing token、逐 Handler 结果或 dead-letter/replay 审计。
- 当前消费事实：`DomainEventWorker` 在单类中硬编码通知处理，并无条件调用搜索索引；一个 Handler 失败会使整个事件重试，无法独立记录副作用。
- 当前部署事实：生产模板只有一个 `worker` 服务；尚未证明两个 Worker 的 claim 排他、崩溃接管、缩容和连接预算。
- 数据基线：V001-V066；S03 新迁移必须兼容空库与旧库升级，不修改既有迁移。
- 边界基线：15 个后端模块、85 张有效表、93 条批准的跨 owner read；project/shared P0 与 foreign write 保持 0。
- 决策变化：S02 M5 曾建议暂停本专项并重新评估 PROJECT-PLATFORM-S05；用户在 S02 归档时明确选择继续 S03，本路线以 revision 5 记录该决定，不改写 S02 历史报告。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、Acceptance Evidence 和执行报告行。
2. PostgreSQL 是事件、投递、回执、dead letter 和 replay 的事实源；Redis 不作为可靠消费前提。
3. claim 必须原子、排他并带有 worker identity、lease deadline 和 fencing；过期 Worker 不能覆盖新 owner 的处理结果。
4. 多 Handler 结果分别持久化。一个 Handler 的失败不能抹去其他 Handler 已完成的幂等副作用，也不能永久阻塞无关事件。
5. 所有 Handler 必须声明 event type/version、幂等键、重试分类和敏感字段边界；未知类型或版本不能被静默标记成功。
6. dead letter 和 replay 必须受权限保护、可审计、可限流；不得通过直接改表或无限自动重试恢复。
7. 需要聚合顺序的事件必须有显式 sequence/partition 合同；不得依赖 `created_at` 或 UUID 的偶然顺序。
8. Worker 并发、batch、lease、heartbeat、retry 和连接池均需有界配置；扩容不能线性耗尽 PostgreSQL 连接。
9. S03 的 realtime signal Handler 只建立可靠信号发布合同和持久事实关联，不提前实现 S04 Redis fanout 或 Gateway 多节点行为。
10. M1-M4 使用影响范围验证；M5 执行完整后端、迁移、前端、协作、工作台、安全、真实隔离浏览器与 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M1 | 版本化事件 envelope、Handler registry 与逐 Handler 幂等合同 | S02 归档与 revision 5 | `docs/90-reports/platform-scale-s03-m1-execution-report.md` | Completed |
| PLATFORM-SCALE-S03-M2 | Claim lease、超时接管、重试、dead letter 与 replay | M1 | `docs/90-reports/platform-scale-s03-m2-execution-report.md` | Completed |
| PLATFORM-SCALE-S03-M3 | 多 Worker 独立部署、并发、背压、健康与扩缩 | M1-M2 | `docs/90-reports/platform-scale-s03-m3-execution-report.md` | Completed |
| PLATFORM-SCALE-S03-M4 | Notification、Search 与 realtime signal Handler 迁移 | M1-M3 | `docs/90-reports/platform-scale-s03-m4-execution-report.md` | Completed |
| PLATFORM-SCALE-S03-M5 | 多 Worker 故障、积压恢复、运行手册与 Stage 收口 | M1-M4 | `docs/90-reports/platform-scale-s03-m5-execution-report.md` | Completed |

## 5. 详细任务

### PLATFORM-SCALE-S03-M1 版本化事件 envelope、Handler registry 与逐 Handler 幂等合同

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M1-T01 | 复核全部事件生产者、`DomainEventWorker`、Repository、通知/搜索/实时副作用和 S02 Worker 角色 | 每个 event type、生产事务、payload、当前消费者、重复风险、顺序要求和 S04 承接点均可定位 | Done |
| PLATFORM-SCALE-S03-M1-T02 | 冻结版本化 event envelope 和兼容策略 | event id/type/version、workspace、aggregate、sequence、occurredAt、actor、correlation、causation、idempotency 与 payload 定义唯一 | Done |
| PLATFORM-SCALE-S03-M1-T03 | 设计并新增事件 envelope、逐 Handler delivery/receipt 所需 Flyway schema、约束与索引 | V001 至最新可空库迁移；旧 pending/processed 事件兼容；workspace、事件和 Handler 关系由数据库约束 | Done |
| PLATFORM-SCALE-S03-M1-T04 | 实现事件 envelope 领域对象、规范序列化和 payload 大小/敏感字段防线 | 相同语义稳定序列化；密码、token、文件密钥和隐藏资源标题不能进入事件；超限 payload 被拒绝 | Done |
| PLATFORM-SCALE-S03-M1-T05 | 实现版本化 `DomainEventHandler` 公共合同与启动期 Handler registry | event type/version 绑定唯一；重复注册、空声明和不兼容版本启动失败；业务 Handler 不进入 shared | Done |
| PLATFORM-SCALE-S03-M1-T06 | 升级事务 outbox append 合同与主要生产者 | 生产事务原子写业务事实和完整 envelope；correlation/causation 贯通；相同 idempotency key 不重复建事件 | Done |
| PLATFORM-SCALE-S03-M1-T07 | 实现按 registry 物化逐 Handler delivery 的事务边界 | 新事件只生成匹配版本的 delivery；重复调度不重复；新增 Handler 不静默回放全部历史事件 | Done |
| PLATFORM-SCALE-S03-M1-T08 | 实现逐 Handler 幂等 receipt 和副作用完成合同 | event/handler/version 唯一回执；重复调用返回既有结果；完成回执与 Handler 业务事务边界明确 | Done |
| PLATFORM-SCALE-S03-M1-T09 | 冻结未知 event type/version、无 Handler 和兼容升级策略 | 未知类型/版本进入可观察隔离状态而非成功或无限重试；兼容窗口和下线步骤可执行 | Done |
| PLATFORM-SCALE-S03-M1-T10 | 增加事件/Handler 结构化日志、correlation 和基础审计字段 | 生产、物化、开始、完成、拒绝均可按 event/handler/workspace/correlation 定位且不泄密 | Done |
| PLATFORM-SCALE-S03-M1-T11 | 建立 envelope、schema、registry、生产事务、delivery 和幂等正反测试并完成 M1 收口 | 空库/升级、重复、版本、敏感字段、事务回滚和模块边界测试通过；执行报告与 checkpoint 完整 | Done |

### PLATFORM-SCALE-S03-M2 Claim lease、超时接管、重试、dead letter 与 replay

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M2-T01 | 冻结 delivery 状态机、lease、attempt、失败分类、dead-letter 和 replay 不变量 | pending/processing/processed/dead-letter 的合法转换、owner、时间、次数和终态无歧义 | Done |
| PLATFORM-SCALE-S03-M2-T02 | 增加 worker id、claimedAt、leaseUntil、fencing token、attempt 和 dead-letter/replay schema | 约束阻止无 owner processing、负 attempt 和非法终态；claim/backlog/lease 查询有匹配索引 | Done |
| PLATFORM-SCALE-S03-M2-T03 | 实现基于 `FOR UPDATE SKIP LOCKED` 的原子批量 claim 与 fencing | 并发 Worker 不重复持有同一 delivery；每次接管递增 fencing；旧 owner 不能完成新 lease | Done |
| PLATFORM-SCALE-S03-M2-T04 | 实现长任务 heartbeat/lease 延长与最大执行预算 | 只有当前 owner/fencing 可续约；续约有界；超预算任务停止续约并进入可恢复状态 | Done |
| PLATFORM-SCALE-S03-M2-T05 | 实现过期 lease 扫描、原子回收和 Worker 崩溃接管 | processing 超时可由其他 Worker 接管；未过期不抢占；接管不丢失既有 attempt/error 证据 | Done |
| PLATFORM-SCALE-S03-M2-T06 | 实现可配置错误分类、指数退避、抖动、最大重试和错误摘要 | transient/permanent/unknown 策略唯一；无紧密重试风暴；错误截断、指纹化且不记录敏感数据 | Done |
| PLATFORM-SCALE-S03-M2-T07 | 实现逐 Handler 完成和事件整体完成聚合 | 事件仅在所有必需 delivery 成功或按策略终结后完成；单 Handler 重试不重复执行已成功 Handler | Done |
| PLATFORM-SCALE-S03-M2-T08 | 实现 dead-letter 进入、隔离、保留和查询合同 | 达阈值或永久错误进入 dead letter；毒事件不阻塞后续事件；查询按 workspace/handler/时间隔离 | Done |
| PLATFORM-SCALE-S03-M2-T09 | 实现受保护的 dead-letter inspect/replay/abandon 维护命令 | replay 需要理由、操作者、目标 Handler 和限额；生成审计及新 attempt，不直接删除历史失败 | Done |
| PLATFORM-SCALE-S03-M2-T10 | 冻结聚合顺序、并行分区和 poison-event 绕行策略 | 需要顺序的同 aggregate 按 sequence 推进；无顺序事件可并行；缺序/重复序号有明确处理 | Done |
| PLATFORM-SCALE-S03-M2-T11 | 建立双 Worker claim、fencing、heartbeat、崩溃、退避、死信、回放和顺序并发测试 | 使用真实 PostgreSQL 证明排他、旧 owner 拒绝、超时接管、一次副作用和毒事件隔离 | Done |
| PLATFORM-SCALE-S03-M2-T12 | 完成 M2 影响范围、迁移 rehearsal、运维安全和执行报告收口 | 目标测试、架构/安全/Flyway 门禁和 checkpoint 通过；无直接改表回放入口 | Done |

### PLATFORM-SCALE-S03-M3 多 Worker 独立部署、并发、背压、健康与扩缩

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M3-T01 | 冻结 Worker 实例、poll、batch、concurrency、lease、heartbeat、retry 和 shutdown 配置合同 | 环境变量唯一、有界、可观测；非法组合启动失败；local/test 默认不进入生产证据 | Done |
| PLATFORM-SCALE-S03-M3-T02 | 重构 Worker 调度器、执行器和 Handler dispatcher | claim 与执行解耦；并发上限固定；单任务异常不终止调度；线程和队列名称可定位 | Done |
| PLATFORM-SCALE-S03-M3-T03 | 实现有界队列、背压和数据库保护 | 队列满时停止 claim 而非无限堆积；batch/并发与连接池预算联动；不忙轮询 | Done |
| PLATFORM-SCALE-S03-M3-T04 | 配置 `worker-a`/`worker-b` 独立服务和相同不可变镜像 | 两实例只使用 Worker role、独立 instance id、共享 PostgreSQL；不开放用户业务端口或 WS | Done |
| PLATFORM-SCALE-S03-M3-T05 | 冻结多 Worker 数据库连接、CPU、内存和启动预算 | 默认两实例不会耗尽 PostgreSQL 连接；每增加实例的连接预算和上限有公式与保护 | Done |
| PLATFORM-SCALE-S03-M3-T06 | 实现 Worker readiness、liveness、draining 和优雅停止 | 依赖/调度/lease 能力反映 readiness；停止后不 claim，新任务可接管，当前任务完成或安全释放 | Done |
| PLATFORM-SCALE-S03-M3-T07 | 暴露 backlog、oldest age、processing、expired lease、吞吐、延迟、retry 和 dead-letter 指标 | 指标含 role/instance/handler，不含 workspace 高基数或敏感 payload；异常有结构化日志 | Done |
| PLATFORM-SCALE-S03-M3-T08 | 建立扩容、缩容、滚动替换和回退单 Worker 自动化 | 调整 Worker 数量不改 API；缩容先 draining；回退不回滚 schema、不把消费放回 API | Done |
| PLATFORM-SCALE-S03-M3-T09 | 收敛 Worker Bean、端口和模块依赖最小面 | Worker 无 MVC 业务 Controller、WS session 或维护 runner；Handler 只经公开 contract 访问其他模块 | Done |
| PLATFORM-SCALE-S03-M3-T10 | 执行真实双 Worker 分布、背压、扩缩、停止和 M3 收口 | 事件由两个实例排他处理；扩缩期间无丢失/重复副作用；角色、部署、后端和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S03-M4 Notification、Search 与 realtime signal Handler 迁移

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M4-T01 | 建立当前硬编码事件到 Notification/Search/realtime 副作用矩阵 | 每个 event type/version、目标 Handler、幂等键、顺序、失败分类和事实校准入口明确 | Done |
| PLATFORM-SCALE-S03-M4-T02 | 将通知落库迁移为独立版本化 Notification Handler | 用户偏好、权限、dedupe 和目标降级保持；重试不重复通知；失败不阻塞 Search | Done |
| PLATFORM-SCALE-S03-M4-T03 | 冻结 Search 增量投影事件和最小安全 payload | owner 事件只携带对象标识、版本和操作；不复制 ACL 作为授权事实，不携带隐藏标题给无权消费者 | Done |
| PLATFORM-SCALE-S03-M4-T04 | 实现 Search upsert/delete 增量 Handler 和版本防倒退 | 按对象增量更新/删除；旧事件不能覆盖新投影；重复处理幂等；不再每事件全 workspace refresh | Done |
| PLATFORM-SCALE-S03-M4-T05 | 将全量搜索重建收敛为显式 Maintenance 命令 | 重建可按 workspace/对象类型分批、续跑、限流和审计；不由普通 Worker 自动触发 | Done |
| PLATFORM-SCALE-S03-M4-T06 | 定义 realtime signal 发布 contract、最小 payload 和 durable fact 校准关系 | 信号只提示事实变化；sequence/version 和 REST 校准入口明确；不直接依赖本地 WS session | Done |
| PLATFORM-SCALE-S03-M4-T07 | 实现 Realtime Signal Handler 与 S04 transport 适配边界 | S03 可生成可观察的信号发布结果；无 transport 时业务事实仍完成；不提前引入 Redis fanout | Done |
| PLATFORM-SCALE-S03-M4-T08 | 从 `DomainEventWorker` 移除业务类型分支并只保留通用调度 | 新 Handler 通过 registry 生效；Worker 不 import notification/search 私有 Repository 或 WebSocket session | Done |
| PLATFORM-SCALE-S03-M4-T09 | 建立 Handler 级失败隔离、幂等、权限和敏感字段回归 | 任一 Handler 失败只重试自身；已成功副作用不重复；删除/无权对象保持最小披露 | Done |
| PLATFORM-SCALE-S03-M4-T10 | 复验 API、combined、本地开发和现有用户流程 | 通知、搜索结果和低延迟提示无行为回退；API 无消费 Bean；combined 仅测试兼容 | Done |
| PLATFORM-SCALE-S03-M4-T11 | 完成真实产品流、Handler 指标、架构边界和 M4 收口 | 项目/知识/Base/IM 变化形成正确通知与增量索引；目标测试、浏览器 smoke 和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S03-M5 多 Worker 故障、积压恢复、运行手册与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M5-T01 | 复核 M1-M4 的 44 项实现任务与 M5 的 10 项收口合同 | 路线共 54 项均有唯一可重复证据；无静默跳过、未决验收阻断或越界 S04/S05 实现 | Done |
| PLATFORM-SCALE-S03-M5-T02 | 执行 V001 至最新空库、旧库升级、并发启动和事件兼容 migration rehearsal | schema、历史事件迁移、索引和约束 fresh 通过；两个 Worker 不承担 Flyway | Done |
| PLATFORM-SCALE-S03-M5-T03 | 执行真实双 Worker 正常分流、Handler 隔离和幂等完整回归 | 多类型事件分布到两个实例；每个 delivery 只有一个有效 owner 和一次业务副作用 | Done |
| PLATFORM-SCALE-S03-M5-T04 | 执行 claim 后进程崩溃、lease 过期、接管和旧 owner 回写故障注入 | 冻结时间内接管；旧 fencing 完成被拒绝；无丢失、双完成或永久 processing | Done |
| PLATFORM-SCALE-S03-M5-T05 | 执行 transient/permanent/poison 失败、退避、dead-letter 和受控 replay | 重试次数/间隔符合策略；毒事件不阻塞队列；replay 可审计且不重复已成功 Handler | Done |
| PLATFORM-SCALE-S03-M5-T06 | 执行具名突发积压、背压、扩容和恢复测试并冻结运行阈值 | pending/oldest age/吞吐/队列/连接指标可解释；扩容提升恢复速度；结果只作为 S03 运行门槛而非容量承诺 | Done |
| PLATFORM-SCALE-S03-M5-T07 | 执行 PostgreSQL 中断、Worker 重启、滚动缩容和单 Worker 回退 | DB 故障停止 claim 并降 readiness；恢复后收敛；缩容/回退不改 API、schema 或业务事实 | Done |
| PLATFORM-SCALE-S03-M5-T08 | 编写 Worker 部署、扩缩、指标、告警、dead-letter、replay、故障和回退 runbook | 操作者无需改表或理解 Handler 私有实现即可诊断和恢复；危险命令有确认与审计 | Done |
| PLATFORM-SCALE-S03-M5-T09 | 更新 Program、目标架构、当前事实和专项索引并输出 S04/PROJECT Go/No-Go | revision 与状态一致；明确继续 S04、暂停专项恢复项目或补充 S03，不提前激活下一 Stage | Done |
| PLATFORM-SCALE-S03-M5-T10 | 完成 S03 报告、影响审计和路线级 `route-final` | 完整后端、迁移、前端、协作、工作台、安全、真实隔离浏览器及多 Worker 故障证据 fresh 通过 | Done |

## 6. Stage 全局验收标准

- 事件 envelope 具备稳定 type/version、aggregate sequence、correlation/causation、idempotency 和敏感字段边界。
- Handler registry 在启动时验证唯一 type/version；通知、搜索和 realtime signal 不再硬编码在 `DomainEventWorker`。
- 每个事件按 Handler 独立持久化 delivery 和幂等 receipt；单 Handler 失败不重复已完成副作用。
- Claim 使用 PostgreSQL 原子排他与 fencing；lease 可续约、过期可接管，旧 owner 无法覆盖新 owner。
- 重试有错误分类、有界退避和最大次数；dead letter、inspect、replay、abandon 均受保护、可限流和可审计。
- 同 aggregate 需要顺序的事件按显式 sequence 推进，不依赖时间戳或 UUID 偶然顺序。
- 两个 Worker 使用相同 artifact、独立 instance id 和共享事实源，可单独扩缩；API 数量不随 Worker 改变。
- Worker 具备有界并发、背压、连接预算、role-aware health/readiness、draining 和优雅停止。
- backlog、oldest age、processing、expired lease、Handler 延迟、retry 和 dead-letter 指标可按角色/实例/Handler 定位。
- Search 采用对象级增量 upsert/delete；全量重建只通过 Maintenance；权限判断不依赖陈旧 ACL 快照。
- realtime signal 只作为 durable fact 的低延迟提示，S03 不把 Redis 信号当唯一事实，也不提前实现 S04 多 Gateway fanout。
- S01/S02 边界保持：project/shared P0 与 foreign write 为 0，93 条历史 read 例外不能扩散。
- M5 使用真实双 Worker和共享 PostgreSQL 完成崩溃、接管、毒事件、积压、扩缩、依赖中断和回退验证。
- Stage 最终执行完整后端、迁移、前端、协作、工作台、安全、真实隔离浏览器和 `route-final`。

## 7. 当前执行入口

从 M1 的第一项任务开始，按 Milestone 分轮执行。S03 激活不代表 S04/S05 或 PROJECT-PLATFORM-S05 已启动。
