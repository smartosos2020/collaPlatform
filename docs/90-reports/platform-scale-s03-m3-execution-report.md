---
title: PLATFORM-SCALE-S03-M3 执行报告
status: completed
milestone: PLATFORM-SCALE-S03-M3
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S03-M3 Execution Report

## Scope

本轮完成 `PLATFORM-SCALE-S03-M3-T01 到 PLATFORM-SCALE-S03-M3-T10`，把 M2 delivery coordinator 接入显式启用、有界并发、可 draining 的可靠 Worker runtime，并交付两个生产 Worker、资源预算、健康、指标及扩缩自动化。旧业务分支保留到 M4 Handler 迁移，可靠 runtime 不 import 业务模块私有实现。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M3-T01 | unit | not-required | not-required | no | bounded configuration validation |
| PLATFORM-SCALE-S03-M3-T02 | integration | not-required | not-required | no | dispatcher execution with real ledger |
| PLATFORM-SCALE-S03-M3-T03 | integration | not-required | not-required | no | queue-full claim protection |
| PLATFORM-SCALE-S03-M3-T04 | static | not-required | not-required | no | production Compose dual Worker contract |
| PLATFORM-SCALE-S03-M3-T05 | unit | not-required | not-required | no | fleet connection budget rejection |
| PLATFORM-SCALE-S03-M3-T06 | integration | not-required | not-required | no | draining prevents new claims |
| PLATFORM-SCALE-S03-M3-T07 | integration | not-required | not-required | no | bounded metrics populated by polling |
| PLATFORM-SCALE-S03-M3-T08 | static | not-required | not-required | no | scale, rollback and rollout dry-run |
| PLATFORM-SCALE-S03-M3-T09 | unit | not-required | not-required | no | runtime role and explicit enable conditions |
| PLATFORM-SCALE-S03-M3-T10 | integration | not-required | not-required | no | real PostgreSQL two-Worker distribution |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PLATFORM-SCALE-S03-M3-T01 | Done | `DomainEventWorkerProperties` validates all runtime limits and formulas |
| PLATFORM-SCALE-S03-M3-T02 | Done | `ReliableDomainEventWorker` separates poll, claim, queue and Handler execution |
| PLATFORM-SCALE-S03-M3-T03 | Done | claim limit equals free executor capacity and rejected claims are fenced releases |
| PLATFORM-SCALE-S03-M3-T04 | Done | production Compose contains `worker-a` and `worker-b` from one Server image |
| PLATFORM-SCALE-S03-M3-T05 | Done | per-instance and fleet PostgreSQL connection formulas plus CPU/memory limits |
| PLATFORM-SCALE-S03-M3-T06 | Done | readiness, draining, graceful wait and queued delivery release |
| PLATFORM-SCALE-S03-M3-T07 | Done | backlog, age, processing, lease, retry, dead-letter, throughput and latency metrics |
| PLATFORM-SCALE-S03-M3-T08 | Done | `worker-fleet.mjs` supports scale 1/2, rolling replacement and dry-run |
| PLATFORM-SCALE-S03-M3-T09 | Done | explicit Worker role/property guards and no published Worker ports |
| PLATFORM-SCALE-S03-M3-T10 | Done | 9 targeted tests plus Compose and automation validation |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M3-T01 | Worker 配置唯一、有界且非法组合启动失败 | worker and delivery properties validation | property positive/negative tests PASS | not-required: runtime configuration | Done |
| PLATFORM-SCALE-S03-M3-T02 | claim、队列和 Handler 执行解耦且异常隔离 | reliable worker executor and registry dispatcher | real ledger dispatch test PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T03 | 容量满后停止 claim 且无无限堆积 | free-capacity claim limit and fenced release | processing 2, pending 3 assertion PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T04 | 两实例同镜像、独立 id 且无业务端口 | worker-a/worker-b Compose services | deployment contract and compose config PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T05 | 两实例连接预算不耗尽 PostgreSQL | `instances * pool <= budget` guard and runbook | budget rejection tests PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T06 | draining 后不再 claim 且 readiness 下线 | SmartLifecycle stop and health indicator | stopped Worker pending delivery test PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T07 | 运行指标低基数且覆盖积压与处理结果 | repository stats, gauges, counters and timer | integration polling metrics path PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T08 | 单/双实例与滚动替换可自动执行 | `deploy/worker-fleet.mjs` | scale/rollout dry-run PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T09 | Worker 角色、开关和依赖面受门禁约束 | role condition, property condition and public Handler registry | role/deployment contracts PASS | not-required | Done |
| PLATFORM-SCALE-S03-M3-T10 | 两 Worker 排他分流且 receipt 唯一 | real Testcontainers PostgreSQL and two runtimes | 12 deliveries, 2 owners, 12 receipts PASS | not-required: backend worker flow | Done |

## Code Changes

- 后端：新增有界可靠 Worker、配置、健康指标、backlog 快照、fenced release、heartbeat 和低基数 Micrometer 指标。
- 部署：单 `worker` 改为同镜像 `worker-a`/`worker-b`，增加连接/CPU/内存预算、优雅停止及显式可靠 Worker 开关。
- 自动化：新增 `pnpm worker:fleet`，支持单/双实例、滚动替换、状态和 dry-run。
- 测试：新增配置负例、部署/角色合同、真实双 Worker 分流、背压和 draining 集成测试。
- 前端与数据库 schema：无变更。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | update | 固化 M3 多 Worker 当前事实和 M4 兼容边界 |
| `docs/05-runbooks/event-worker-fleet.md` | create | 连接预算、指标、扩缩、故障和回退操作 |
| `docs/02-roadmap/current-roadmap.md` | update | M3 的 10 项任务完成 |
| 本报告 | create | 逐任务验证证据 |

## Validation

- Backend tests: PASS - 9 targeted tests, 0 failures/errors/skips.
- Frontend build: not-required - 无前端改动。
- Local quality gate: PASS - `.local-reports/quality-gate-20260724T061255.md`.
- Browser smoke: not-required - 本轮只有 Worker runtime、部署和运维脚本，无用户或管理页面。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M4 | 旧 `DomainEventWorker` 仍承担 Notification/Search 兼容业务分支 | non-blocking for M3; reliable runtime is isolated | M4-T02 to M4-T08 |
| PLATFORM-SCALE-S03-M5 | 生产形态的故障、积压与 PostgreSQL 中断 rehearsal 尚未执行 | non-blocking for M3 | M5 |

## Next Steps

推进 `PLATFORM-SCALE-S03-M4`，将 Notification、Search 和 realtime signal 迁移为版本化 Handler，并移除旧 Worker 的业务类型分支。
