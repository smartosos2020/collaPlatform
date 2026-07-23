---
title: 平台模块化与运行扩展长期专项提案
status: proposed
program: PLATFORM-SCALE
revision: 0
updated_at: 2026-07-24
planning_mode: rolling
current_stage: none
activation_after: PROJECT-PLATFORM-S04
initiative_index_doc: docs/00-product/initiatives/README.md
target_architecture_doc: docs/01-architecture/platform-scale-target-architecture-proposal.md
source_baseline: docs/90-reports/platform-scale-readiness-baseline-2026-07-24.md
---

# 平台模块化与运行扩展长期专项提案

## 1. 文档定位

本文是尚未激活的 `PLATFORM-SCALE` 长期专项提案，不是第二份执行路线。当前唯一 Active Program 仍是 `PROJECT-PLATFORM`，当前唯一执行路线仍是 `docs/02-roadmap/current-roadmap.md`。

本文只维护拟议目标、Stage 顺序、依赖、退出条件和激活规则，不包含可执行 Task 或 Task 状态。S04 完成前不得把本文加入专项索引的 Active 行，不得修改当前路线来执行本文 Milestone。

## 2. 提案背景

PROJECT-PLATFORM 已进入动态字段、布局、发布和统一工作项运行时前的关键阶段。准备期审计发现：

- 后端有清晰模块目录，但 11 个核心模块处于同一循环依赖分量。
- 业务模块存在直接跨模块 Repository、私表和事务依赖。
- 知识协同已经是独立双节点组件。
- 通用 WebSocket 和异步 Worker 仍与单个 Spring Server 绑定。
- S05-S07 将继续增加 project 对 identity、file、permission、event 和 platform 的需求。

因此不应等待 PROJECT-PLATFORM-S21 后再治理，也不应在 S04 中途插入大规模重构。提案选择在 S04 完成后、S05 激活前切换专项。

## 3. 专项目标

1. 把包结构意图转化为编译和质量门禁可以验证的模块边界。
2. 保留共享数据库，同时建立唯一表 owner、公共合同和跨模块流程。
3. 使 API、异步 Worker、通用实时网关和知识协同可以分别部署和扩容。
4. 建立应用层容量、故障和恢复基线。
5. 在不引入全面微服务复杂度的前提下，为后续项目、知识、IM、审批和 Base 演进提供稳定平台边界。

## 4. 与 PROJECT-PLATFORM 的关系

- S04 必须按原路线独立完成和归档。
- `PLATFORM-SCALE` 激活时，`PROJECT-PLATFORM` 应暂停在 S05 之前，不改写 S01-S04 的历史结论。
- 推荐完成 `PLATFORM-SCALE-S01` 和 `S02` 后执行一次 Go/No-Go：
  - 若模块门禁、project 当前依赖和双 API 运行隔离已稳定，可暂停 `PLATFORM-SCALE`，恢复 PROJECT-PLATFORM-S05。
  - 若仍存在会使 S05-S07 明显扩大耦合的阻断问题，则继续执行 `PLATFORM-SCALE-S03`。
- 暂停任一 Program 时必须在专项索引中保留剩余承诺和恢复入口。
- 两个 Program 不得同时标记 Active；并行 worktree 不能绕过单 Active 规划合同。

## 5. Stage 总览

| Stage | 目标 | 主要依赖 | 提案状态 | 退出证据 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S01 | 模块边界、表归属、公共合同和自动门禁 | S04 完成并合入 | Proposed | 干净主干基线、contract 规则、ArchUnit/TS AST/SQL owner 门禁、阻断依赖收敛 |
| PLATFORM-SCALE-S02 | API 运行角色隔离与双实例基线 | S01 | Proposed | API 无 Worker/旧协同定时任务、双实例 upstream、会话和幂等复验 |
| PLATFORM-SCALE-S03 | 异步 Worker 独立运行和可靠消费 | S01-S02 | Proposed | lease、handler、dead letter、replay、指标和多 Worker 接管 |
| PLATFORM-SCALE-S04 | 通用实时事件网关与知识协同收口 | S02-S03 | Proposed | Redis fanout、多 Gateway、重连校准、Spring 旧知识协同退出 |
| PLATFORM-SCALE-S05 | 容量、故障、恢复和运维收口 | S02-S04 | Proposed | 固定容量环境、负载/长稳/故障证据和 Go/No-Go |

Stage 数量不是固定承诺。S04 合入后的新事实可以拆分、合并或重排未来 Stage，但必须保留变更记录，不能压缩真实复杂度。

## 6. Stage 与 Milestone 提案

### PLATFORM-SCALE-S01 模块边界、表归属、公共合同和自动门禁

目标：先阻止新耦合继续增长，再修复会直接影响 PROJECT-PLATFORM-S05-S07 的阻断边界。

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PLATFORM-SCALE-S01-M1 | S04 后干净主干重扫、依赖图和风险分级 | import、SCC、跨 owner SQL、事务和 S04 增量均可重复定位 |
| PLATFORM-SCALE-S01-M2 | 模块 contract、table owner、组合查询和例外合同 | 允许/禁止依赖、owner、只读例外、退出条件和 ADR 无歧义 |
| PLATFORM-SCALE-S01-M3 | 后端 ArchUnit、前端 TS AST 和数据 owner 门禁 | 历史 baseline 可控；任何新增违规自动失败；动态/别名路径有测试 |
| PLATFORM-SCALE-S01-M4 | project 当前复杂字段及 P0 跨模块边界收敛 | project 不再 import identity/file/platform infrastructure；outbox/audit 使用公共 port |
| PLATFORM-SCALE-S01-M5 | Stage 评审、route-final 和 S02 准入 | 门禁 fresh 通过，例外有 owner/期限，S02 运行角色输入冻结 |

S01 激活时再按实际复杂度拆 Task。Task 必须只写入当时的 `current-roadmap.md`，不得从本文直接执行。

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
| PLATFORM-SCALE-S05-M5 | 专项 Go/No-Go 和容量基线发布 | 明确可承诺范围、剩余单点和 PROJECT-PLATFORM 后续约束 |

## 7. S01 激活准入

只有全部满足时才生成 S01 当前路线：

1. PROJECT-PLATFORM-S04-M5 完成。
2. S04 路线已经归档。
3. S04 变更已经合入主干。
4. 主干工作区干净。
5. 准备期基线已在主干重新执行并记录差异。
6. PROJECT-PLATFORM Program 和专项索引明确暂停在 S05 之前。
7. 本文从 `proposed` 提升为 `active`，`current_stage` 改为 `PLATFORM-SCALE-S01`。
8. 目标架构提案提升为 `target`，Program、目标架构和当前路线修订号一致。
9. 专项索引仍恰好只有一个 Active Program。

## 8. S01 全局验收标准提案

- 后端和前端边界门禁覆盖静态 import、动态 import、重导出和路径别名。
- 历史例外都有唯一 owner、理由、受影响文件、允许方向、退出 Stage 和到期决定。
- 新增 foreign infrastructure import 为零。
- `shared` 不依赖业务模块。
- project 的 S04 复杂字段引用通过合同访问身份、文件和平台对象。
- outbox 和 audit 通过公共 port 写入，仍与业务命令同事务。
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

## 10. 激活时文档动作

S04 后若决定激活本专项：

1. 归档 S04 当前路线。
2. 更新 `docs/00-product/initiatives/README.md`：
   - `PROJECT-PLATFORM` 改为 Paused，Current Stage 为 none，剩余承诺从 S05 开始。
   - `PLATFORM-SCALE` 改为唯一 Active，Current Stage 为 S01。
   - `tracked_paused_programs` 保留 KB-PRODUCT 并加入 PROJECT-PLATFORM。
3. 提升并重命名本 Program/目标架构，或明确保留 proposal 文件名但更新 status。
4. 生成只包含 PLATFORM-SCALE-S01 的唯一 `current-roadmap.md`。
5. 运行规划合同和文档结构门禁后才能开始 S01-M1。

## 11. 提案结论

建议在 S04 收口后激活 `PLATFORM-SCALE-S01`，完成边界门禁后继续 `S02` 的运行角色和双 API 基线。S02 收口时再根据真实风险决定恢复 PROJECT-PLATFORM-S05，避免一次性架构改造拖成无边界重写，也避免在长程项目能力上持续累积难以拆解的耦合。
