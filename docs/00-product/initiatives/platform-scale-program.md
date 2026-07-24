---
title: 平台模块化与运行扩展长期专项规划
status: active
program: PLATFORM-SCALE
revision: 7
updated_at: 2026-07-24
planning_mode: rolling
current_stage: PLATFORM-SCALE-S04
activation_after: PROJECT-PLATFORM-S04
initiative_index_doc: docs/00-product/initiatives/README.md
target_architecture_doc: docs/01-architecture/platform-scale-target-architecture.md
source_baseline: docs/90-reports/platform-scale-readiness-baseline-2026-07-24.md
---

# 平台模块化与运行扩展长期专项规划

## 1. 文档定位

本文是已激活的 `PLATFORM-SCALE` 长期专项，不是可直接执行的第二份路线。当前唯一 Active Program 是 `PLATFORM-SCALE`，当前唯一执行任务仍只存在于 `docs/02-roadmap/current-roadmap.md`。

本文维护专项目标、Stage 顺序、依赖、退出条件和规划变更，不包含可执行 Task 或 Task 状态。`PROJECT-PLATFORM` 已在 S04 完成后暂停于 S05 之前；任何恢复、重排或继续平台化的决定都必须先更新专项索引和变更记录。

## 2. 激活背景

PROJECT-PLATFORM 已完成动态字段 S04，尚未进入布局、发布和统一工作项运行时。2026-07-24 在干净主干 `134c370` 上完成复扫：

- 后端 15 个模块、233 个 Java 文件，存在 204 条跨模块 import，其中 47 条直接指向其他模块 infrastructure。
- 11 个核心模块仍处于同一循环依赖分量；S04 新增 18 个 Java 文件、10 条跨模块 import 和 6 条 foreign infrastructure import，但没有新增依赖方向。
- 业务模块存在直接跨模块 Repository、私表和事务依赖；确认的跨 owner SQL 仍为 22 个文件中的 96 处。
- 知识协同已经是独立双节点组件。
- 通用 WebSocket 和异步 Worker 仍与单个 Spring Server 绑定。
- S05-S07 将继续增加 project 对 identity、file、permission、event 和 platform 的需求。

S04 的功能验收和 route-final 证据完整，不需要重开；新增耦合则证明不能等待 PROJECT-PLATFORM-S21 后再治理。专项因此在 S04 归档后、S05 激活前正式切换。

## 3. 专项目标

1. 把包结构意图转化为编译和质量门禁可以验证的模块边界。
2. 保留共享数据库，同时建立唯一表 owner、公共合同和跨模块流程。
3. 使 API、异步 Worker、通用实时网关和知识协同可以分别部署和扩容。
4. 建立应用层容量、故障和恢复基线。
5. 在不引入全面微服务复杂度的前提下，为后续项目、知识、IM、审批和 Base 演进提供稳定平台边界。

## 4. 与 PROJECT-PLATFORM 的关系

- S04 已按原路线独立完成并归档。
- `PROJECT-PLATFORM` 已暂停在 S05 之前，S01-S04 历史结论保持不变。
- 推荐完成 `PLATFORM-SCALE-S01` 和 `S02` 后执行一次 Go/No-Go：
  - 若模块门禁、project 当前依赖和双 API 运行隔离已稳定，可暂停 `PLATFORM-SCALE`，恢复 PROJECT-PLATFORM-S05。
  - 若仍存在会使 S05-S07 明显扩大耦合的阻断问题，则继续执行 `PLATFORM-SCALE-S03`。
- 暂停任一 Program 时必须在专项索引中保留剩余承诺和恢复入口。
- 两个 Program 不得同时标记 Active；并行 worktree 不能绕过单 Active 规划合同。

## 5. Stage 总览

| Stage | 目标 | 主要依赖 | 状态 | 退出证据 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S01 | 模块边界、表归属、公共合同和自动门禁 | S04 完成并合入 | Completed | 干净主干基线、contract 规则、ArchUnit/TS AST/SQL owner 门禁、阻断依赖收敛 |
| PLATFORM-SCALE-S02 | API 运行角色隔离与双实例基线 | S01 | Completed | API 无 Worker/旧协同定时任务、双实例 upstream、会话和幂等复验 |
| PLATFORM-SCALE-S03 | 异步 Worker 独立运行和可靠消费 | S01-S02 | Completed | lease、handler、dead letter、replay、指标和多 Worker 接管 |
| PLATFORM-SCALE-S04 | 通用实时事件网关与知识协同收口 | S02-S03 | Active | Redis fanout、多 Gateway、重连校准、Spring 旧知识协同退出 |
| PLATFORM-SCALE-S05 | 容量、故障、恢复和运维收口 | S02-S04 | Planned | 固定容量环境、负载/长稳/故障证据和 Go/No-Go |

Stage 数量不是固定承诺。S04 合入后的新事实可以拆分、合并或重排未来 Stage，但必须保留变更记录，不能压缩真实复杂度。

## 6. Stage 与 Milestone 规划

### PLATFORM-SCALE-S01 模块边界、表归属、公共合同和自动门禁

目标：先阻止新耦合继续增长，再修复会直接影响 PROJECT-PLATFORM-S05-S07 的阻断边界。

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PLATFORM-SCALE-S01-M1 | S04 后干净主干重扫、依赖图和风险分级 | import、SCC、跨 owner SQL、事务和 S04 增量均可重复定位 |
| PLATFORM-SCALE-S01-M2 | 模块 contract、table owner、组合查询和例外合同 | 允许/禁止依赖、owner、只读例外、退出条件和 ADR 无歧义 |
| PLATFORM-SCALE-S01-M3 | 后端 ArchUnit、前端 TS AST 和数据 owner 门禁 | 历史 baseline 可控；任何新增违规自动失败；动态/别名路径有测试 |
| PLATFORM-SCALE-S01-M4 | project 当前 P0 跨模块边界和 shared 反向依赖收敛 | project 只经 foreign contract 访问 identity/file/platform/event/audit/IM；shared 不再 import 业务模块 |
| PLATFORM-SCALE-S01-M5 | Stage 评审、route-final 和 S02 准入 | 门禁 fresh 通过，例外有 owner/期限，S02 运行角色输入冻结 |

S01 已按干净主干事实拆为 5 个 Milestone。Task 只存在于当前 `current-roadmap.md`，不得从本文直接执行。

### PLATFORM-SCALE-S02 API 运行角色隔离与双实例基线

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PLATFORM-SCALE-S02-M1 | `api/worker/event-gateway/maintenance` 角色配置和 Bean 边界 | 每个角色启动职责唯一，默认生产配置不混合启用 |
| PLATFORM-SCALE-S02-M2 | 从 API 移除事件轮询、旧知识协同定时任务和本地通用 WS | API 实例只承载 HTTP 业务；健康和 readiness 可区分角色 |
| PLATFORM-SCALE-S02-M3 | 双 API upstream、优雅停机和无状态验证 | 两实例并行服务，停止单节点后新请求继续成功 |
| PLATFORM-SCALE-S02-M4 | 认证、幂等、上传和启动初始化多实例复验 | token/session、重复命令、MinIO 初始化和 admin 初始化无节点依赖或重复副作用 |
| PLATFORM-SCALE-S02-M5 | 运行隔离评审和 PROJECT-PLATFORM 恢复 Go/No-Go | 明确恢复 S05、继续 S03 或补充 S02；专项索引状态可执行 |

### PLATFORM-SCALE-S03 异步 Worker 独立运行和可靠消费

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PLATFORM-SCALE-S03-M1 | 事件 envelope、版本、handler registry 和幂等合同 | 生产者、消费者和敏感字段边界稳定 |
| PLATFORM-SCALE-S03-M2 | claim lease、超时回收、重试、dead letter 和 replay | Worker 崩溃后事件可接管，无丢失和重复副作用 |
| PLATFORM-SCALE-S03-M3 | 独立 Worker service、并发和背压 | Worker 数量可单独调整，不改变 API 实例数 |
| PLATFORM-SCALE-S03-M4 | Notification、Search 和 realtime signal handler 迁移 | DomainEventWorker 不再硬编码消费者；Search 使用增量投影 |
| PLATFORM-SCALE-S03-M5 | 多 Worker 故障、积压和恢复验收 | backlog、oldest age、失败和接管指标满足冻结门槛 |

### PLATFORM-SCALE-S04 通用实时事件网关与知识协同收口

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PLATFORM-SCALE-S04-M1 | 通用 event gateway、Redis fanout 和 session registry | 两个 gateway 节点都能向本地连接正确投递 |
| PLATFORM-SCALE-S04-M2 | IM、通知、项目和权限事件迁移 | API/Worker 不直接操作本地 WebSocket session |
| PLATFORM-SCALE-S04-M3 | 客户端 sequence、去重、重连和 REST 校准 | 丢失瞬时 Redis 信号不会形成永久事实缺口 |
| PLATFORM-SCALE-S04-M4 | Spring 旧知识协同观测、关闭和删除 | Hocuspocus 是唯一知识实时协议；旧定时任务和 room 状态退出 |
| PLATFORM-SCALE-S04-M5 | 多 Gateway、多 collaboration 节点故障验收 | 节点退出、Redis 降级和重连恢复均有自动证据 |

### PLATFORM-SCALE-S05 容量、故障、恢复和运维收口

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PLATFORM-SCALE-S05-M1 | 固定容量环境、数据种子和负载合同 | 硬件、容器、数据、脚本和阈值可重复 |
| PLATFORM-SCALE-S05-M2 | HTTP、Worker、通用 WS 和协同目标负载 | P95、错误率、backlog、fanout 和收敛达到门槛 |
| PLATFORM-SCALE-S05-M3 | 长稳、节点退出、Redis 中断和 Worker 崩溃 | 60 分钟目标负载、8 小时 soak 和故障恢复有 fresh 证据 |
| PLATFORM-SCALE-S05-M4 | 发布、扩容、降容、回退和诊断 runbook | 操作者无需理解模块私有实现即可执行 |
| PLATFORM-SCALE-S05-M5 | 专项 Go/No-Go、边界例外到期复核和容量基线发布 | 93 条历史只读例外移除或重新逐项批准；明确可承诺范围、剩余单点和 PROJECT-PLATFORM 后续约束 |

## 7. S01 激活与完成记录

2026-07-24 已逐项满足：

1. PROJECT-PLATFORM-S04-M5 完成。
2. S04 路线已经归档。
3. S04 变更已通过提交 `134c370` 合入主干，主干与远程一致且切换前工作区干净。
4. 准备期基线已在主干重新执行并记录 S04 增量。
5. S04 的 53 个 Task、53 条 Verification Contract、53 条 Acceptance Evidence、220 个后端测试和 4 条真实隔离浏览器流程完成复核。
6. PROJECT-PLATFORM Program 和专项索引已明确暂停在 S05 之前。
7. 本文已提升为 `active`，`current_stage` 为 `PLATFORM-SCALE-S01`。
8. 目标架构已提升为 `target`，Program、目标架构和当前路线统一使用 revision 1。
9. 专项索引仍恰好只有一个 Active Program。

2026-07-24 完成 S01 收口：

1. M1-M4 的 52 个实现 Task 均有唯一 Verification Contract 和 Done 证据，M5 的 10 个收口 Task 完成路线级复核。
2. 当前清单为 15 个模块、268 个 Java 文件、205 条跨模块 import；历史 private import 从 204 收敛到 153，foreign infrastructure 从 47 收敛到 29。
3. project foreign private/infrastructure import 与 shared 业务反向依赖均为 0。
4. 85 张当前有效表均有唯一 owner；93 条跨 owner 访问全部为精确只读例外，foreign write 为 0。
5. S02 单运行角色、Bean/定时任务、health/readiness、双 API、初始化、幂等、故障和回退输入已冻结。
6. S01 路线标记 Completed 等待归档；`current_stage` 为 `none`，S02 仍为 Planned，不在同一条路线提前激活。

## 8. S01 全局验收标准

- 后端和前端边界门禁覆盖静态 import、动态 import、重导出和路径别名。
- 历史例外都有唯一 owner、理由、受影响文件、允许方向、退出 Stage 和到期决定。
- 新增 foreign infrastructure import 为零。
- `shared` 不依赖业务模块。
- project 只通过公开 contract 访问身份、文件、平台对象、IM、outbox 和 audit；仍需同事务的 outbox/audit 写入语义不变。
- table owner 清单覆盖当前有效 schema；search/admin 例外只读且不能扩散。
- 当前测试、S04 回归和 route-final 均通过。
- S02 运行角色、部署变化和回退输入可直接拆 Task。

## 9. 候选决策池

以下事项在对应 Stage 冻结，不提前塞进 S01：

| 候选 | 最晚决定 Stage | 推荐方向 |
| --- | --- | --- |
| 内部合同包命名 | S01-M2 | `modules.<module>.contract` |
| 模块边界工具 | S01-M3 | ArchUnit 先行，Spring Modulith 后评估 |
| 前端边界工具 | S01-M3 | 复用 TypeScript AST 的跨平台工作台检查 |
| Worker 运行形态 | S02-M1 | 同一 Server artifact，不同 Spring role/profile |
| 通用实时广播 | S04-M1 | Redis pub/sub + REST 事实校准 |
| HTTP/WS 压测 | S05-M1 | 固定版本容器化 k6 |
| Yjs 压测 | S05-M1 | Node Hocuspocus 协议客户端 |
| 基础设施集群 HA | 专项之后 | 先记录单点和恢复目标，不阻断应用层扩展 |

## 10. 专项初始激活动作

本次激活同步完成：

1. 归档 S04 当前路线。
2. 更新 `docs/00-product/initiatives/README.md`：
   - `PROJECT-PLATFORM` 改为 Paused，Current Stage 为 none，剩余承诺从 S05 开始。
   - `PLATFORM-SCALE` 改为唯一 Active，Current Stage 为 S01。
   - `tracked_paused_programs` 保留 KB-PRODUCT 并加入 PROJECT-PLATFORM。
3. 将本 Program 和目标架构移除 proposal 后缀并提升为正式文档。
4. 生成只包含 PLATFORM-SCALE-S01 的唯一 `current-roadmap.md`。
5. 运行规划合同和文档结构门禁；通过后才能开始 S01-M1。

## 11. 当前结论

`PLATFORM-SCALE-S01`、`S02`、`S03` 已完成并归档，`PLATFORM-SCALE-S04` 是唯一 Active Stage。S03 已交付版本化 envelope、逐 Handler delivery/receipt、lease/fencing、dead letter/replay、有界背压、双 Worker 部署与接管，以及 Notification、Search 和 realtime signal Handler 迁移。真实双 Worker 的 24 项具名突发在冻结夹具中由 1541 ms 降至 755 ms，提升 52%；该数据仅证明横向分流和恢复门槛，不构成生产容量承诺。

S04 当前执行路线包含 5 个 Milestone、54 个 Task，依次完成通用实时 envelope 与双 Gateway Redis fanout、IM/通知/项目/权限信号迁移、客户端 sequence/重连/REST 校准、Spring 旧知识协同退出，以及双 Gateway/双 collaboration 故障收口。`PROJECT-PLATFORM` 继续暂停在 S05 之前，`PLATFORM-SCALE-S05` 保持 Planned；两者均不因 S04 激活而提前启动。

## 12. 变更记录

| Revision | 日期 | 变更 | 原因 | 影响 |
| --- | --- | --- | --- | --- |
| 1 | 2026-07-24 | 复核 S04 完成证据和干净主干架构基线；将专项与目标架构从 proposed 提升为 active/target，暂停 PROJECT-PLATFORM 并激活 S01 | S04 新增 10 条跨模块 import 和 6 条 foreign infrastructure import，继续 S05-S07 会扩大现有边界债；先建立可执行门禁与公共合同 | PLATFORM-SCALE-S01 成为唯一活动路线；PROJECT-PLATFORM 暂停在 S05 之前 |
| 2 | 2026-07-24 | 完成 S01 的 62 项路线合同，收口 project/shared P0 边界并冻结 S02 运行与部署准入输入 | 自动门禁、table owner、公共 contract 和 route-final 已形成可重复证据，具备进入运行隔离 Stage 的条件 | S01 Completed 等待归档；S02 保持 Planned，PROJECT-PLATFORM 继续暂停 |
| 3 | 2026-07-24 | 归档 S01 完成路线并激活 S02 运行角色隔离与双 API 基线 | S01 route-final、边界门禁和 S02 准入输入已完成；需要用真实双实例验证无状态和运行职责隔离 | S02 成为唯一活动 Stage；新路线包含 5 个 Milestone、57 个 Task，PROJECT-PLATFORM 继续暂停 |
| 4 | 2026-07-24 | 完成 S02 的四生产角色、双 API、跨节点状态、维护初始化、故障恢复、运行手册和 route-final | 57 项任务与真实隔离证据闭环，当前没有阻断 PROJECT-PLATFORM-S05 的运行耦合问题 | S02 Completed 等待归档；建议归档后暂停 PLATFORM-SCALE 并重新评估激活 PROJECT-PLATFORM-S05 |
| 5 | 2026-07-24 | 归档 S02 完成路线并激活 S03 异步 Worker 独立运行与可靠消费 | 用户在复核 S02 Go/No-Go 后明确选择先消除单 Worker、无 lease、硬编码 Handler 和无 dead-letter/replay 的运行风险 | S03 成为唯一活动 Stage；路线包含 5 个 Milestone、54 个 Task，PROJECT-PLATFORM 继续暂停 |
| 6 | 2026-07-24 | 完成 S03 的 54 项合同、真实双 Worker 故障/恢复演练、迁移并发演练和 route-final 收口 | 可靠消费、故障接管、逐 Handler 隔离及运维入口已形成可重复证据；下一风险集中于通用实时 Gateway 和旧协同收口 | S03 Completed 等待归档；建议归档后激活 S04，PROJECT-PLATFORM 继续暂停 |
| 7 | 2026-07-24 | 归档 S03 完成路线并激活 S04 通用实时事件网关与知识协同收口 | S03 route-final、双 Worker 故障恢复和可靠 signal 合同已通过；单 Gateway、本地 session、客户端校准缺口和旧 Spring 协同链路成为下一阻断风险 | S04 成为唯一活动 Stage；路线包含 5 个 Milestone、54 个 Task，S05 保持 Planned，PROJECT-PLATFORM 继续暂停 |
