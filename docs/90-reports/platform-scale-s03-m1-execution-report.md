---
title: PLATFORM-SCALE-S03-M1 执行报告
status: completed
milestone: PLATFORM-SCALE-S03-M1
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S03-M1 Execution Report

## Scope

本轮建立版本化事件 envelope、按聚合单调 sequence、Handler registry、逐 Handler delivery/receipt、payload 防线与结构化追踪。旧 `DomainEventWorker` 在 M4 迁移前继续兼容消费，M1 不提前切换业务 Handler。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M1-T01 | static | not-required | not-required | no | producer/consumer inventory |
| PLATFORM-SCALE-S03-M1-T02 | unit | not-required | not-required | no | envelope defaults and validation |
| PLATFORM-SCALE-S03-M1-T03 | integration | not-required | not-required | no | V001-V067 fresh migration |
| PLATFORM-SCALE-S03-M1-T04 | unit | not-required | not-required | no | canonical payload, secret and size rejection |
| PLATFORM-SCALE-S03-M1-T05 | unit | not-required | not-required | no | registry positive/negative startup matrix |
| PLATFORM-SCALE-S03-M1-T06 | integration | not-required | not-required | no | append, idempotency and transaction rollback |
| PLATFORM-SCALE-S03-M1-T07 | integration | not-required | not-required | no | matching delivery materialization and duplicate suppression |
| PLATFORM-SCALE-S03-M1-T08 | integration | not-required | not-required | no | stable receipt replay result |
| PLATFORM-SCALE-S03-M1-T09 | static | not-required | not-required | no | compatibility and unknown-version policy |
| PLATFORM-SCALE-S03-M1-T10 | integration | not-required | not-required | no | identifier-only structured event logs |
| PLATFORM-SCALE-S03-M1-T11 | integration | not-required | not-required | no | targeted tests and architecture checkpoint |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PLATFORM-SCALE-S03-M1-T01 | Done | 本报告 `Event Producer and Consumer Inventory` |
| PLATFORM-SCALE-S03-M1-T02 | Done | `TransactionalOutbox.EventEnvelope` 与 `DomainEventModels.DomainEvent` |
| PLATFORM-SCALE-S03-M1-T03 | Done | `V067__create_reliable_event_envelope_and_deliveries.sql` |
| PLATFORM-SCALE-S03-M1-T04 | Done | `EventEnvelopePolicy` 规范化、递归敏感键与 256 KiB 防线 |
| PLATFORM-SCALE-S03-M1-T05 | Done | `DomainEventHandler` 与 `DomainEventHandlerRegistry` |
| PLATFORM-SCALE-S03-M1-T06 | Done | `TransactionalOutboxAdapter` 与事务回滚测试 |
| PLATFORM-SCALE-S03-M1-T07 | Done | `DomainEventDeliveryMaterializer` 和 delivery 唯一约束 |
| PLATFORM-SCALE-S03-M1-T08 | Done | `DomainEventReceiptStore` 和 receipt 唯一结果 |
| PLATFORM-SCALE-S03-M1-T09 | Done | 当前架构中的兼容窗口与 M4 隔离切换规则 |
| PLATFORM-SCALE-S03-M1-T10 | Done | append/materialize/receipt 无 payload 结构化日志 |
| PLATFORM-SCALE-S03-M1-T11 | Done | 8 个定向测试、V001-V067 空库迁移与架构门禁 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M1-T01 | 全链路可定位 | inventory 覆盖 6 个生产模块和 3 类副作用 | `rg eventRepository.append`/`DomainEventWorker` | not-required: 无 UI 行为 | Done |
| PLATFORM-SCALE-S03-M1-T02 | envelope 唯一 | 公共 record 与数据库映射字段一致 | `EventEnvelopePolicyTests` | not-required | Done |
| PLATFORM-SCALE-S03-M1-T03 | fresh/legacy 兼容 | V067 回填 sequence/occurred/correlation 后加约束 | Flyway validated 67 migrations | not-required | Done |
| PLATFORM-SCALE-S03-M1-T04 | 稳定且不泄密 | key 递归排序、数组扫描、敏感键和大小拒绝 | `EventEnvelopePolicyTests` 4 PASS | not-required | Done |
| PLATFORM-SCALE-S03-M1-T05 | 注册唯一 | handler identity 唯一、订阅非空、版本正数 | `DomainEventHandlerRegistryTests` 2 PASS | not-required | Done |
| PLATFORM-SCALE-S03-M1-T06 | 原子与幂等 | advisory lock sequence、workspace/idempotency 唯一 | integration append/duplicate/rollback PASS | not-required | Done |
| PLATFORM-SCALE-S03-M1-T07 | 精确物化 | registry matching + delivery unique(event,handler,version) | integration inserted 1 then 0 | not-required | Done |
| PLATFORM-SCALE-S03-M1-T08 | 回执幂等 | receipt store 在调用事务中写入，重复返回原结果 | integration receipt created true then false | not-required | Done |
| PLATFORM-SCALE-S03-M1-T09 | 未知版本不静默 | 兼容期 warn；M4 dispatcher 后转 unsupported | 架构合同检查 | not-required | Done |
| PLATFORM-SCALE-S03-M1-T10 | 可追踪且不泄密 | event/workspace/handler/correlation 标识日志 | integration 日志包含标识且无 payload | not-required | Done |
| PLATFORM-SCALE-S03-M1-T11 | 正反证据闭环 | 测试、迁移、表 owner、路线和报告同步 | targeted Maven + checkpoint | not-required | Done |

## Event Producer and Consumer Inventory

| Producer module | Event families | Current transaction/payload | Current consumer and risk | M4 owner |
| --- | --- | --- | --- | --- |
| approval | `notification.created` | 审批事务内写 recipient/target/dedupe | Notification + workspace search；整事件重试会重复副作用 | Notification/Search Handler |
| base | `base.*`, `notification.created` | Base/table/授权事务内写对象标识与通知字段 | 搜索当前全 workspace refresh；通知与搜索失败耦合 | Search/Notification Handler |
| im | `message.*`, `notification.created` | 消息事务内写 conversation/message/mention 标识 | 通知落库后本地 push；搜索无条件执行 | Notification/Realtime Handler |
| knowledge | `knowledge.content.*`, `notification.created` | 内容、评论、权限、版本事务内写 item/version/target | 搜索刷新成本高；隐藏标题必须在 M4 改为按 ID 重读 | Search/Notification/Realtime Handler |
| permission | `notification.created` | 授权事务内写 resource/recipient/dedupe | 通知重试与其他投影耦合 | Notification Handler |
| project | `project.*`, `issue.*`, `work_item_*`, `notification.created` | 项目/事项/配置事务内写对象 ID、变更摘要和 dedupe | 通知、搜索、实时信号尚未独立 | 三类 Handler |

聚合顺序由 `(workspace, aggregate_type, aggregate_id, aggregate_sequence)` 唯一约束冻结。无需顺序的 Handler 可在 M2/M3 并行；声明 `orderedByAggregate=true` 的 Handler 必须等待同聚合较小 sequence 终结。

## Code Changes

- 后端：新增版本化 Handler contract、registry、envelope policy、delivery materializer、receipt store，并升级 outbox/repository。
- 数据库：V067 扩展 `domain_events`，新增 delivery/receipt 两张 event owner 表及索引约束。
- 测试：新增 envelope policy、registry 和真实 PostgreSQL envelope/delivery/receipt/rollback 测试。
- 前端：无。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | update | 记录 M1 已交付事实和兼容窗口 |
| `docs/02-roadmap/current-roadmap.md` | update | M1 的 11 项任务完成 |
| 本报告 | create | 逐任务验证合同与证据闭环 |

## Validation

- Backend tests: PASS - 8 targeted tests, 0 failures/errors/skips.
- Flyway fresh migration: PASS - PostgreSQL 16 从 V001 到 V067。
- Architecture contracts/boundaries: PASS - checkpoint 证据。
- Frontend build: not-required - 无前端改动。
- Local quality gate: PASS - `.local-reports/quality-gate-20260724T052012.md`.
- Browser smoke: not-required - M1 只改变后端事件合同、schema 和内部追踪，无用户可见浏览器流程。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | lease/fencing、重试、dead letter 和 replay 尚未实现 | non-blocking | PLATFORM-SCALE-S03-M2 |
| N/A | Worker 并发、背压和双实例部署尚未实现 | non-blocking | PLATFORM-SCALE-S03-M3 |
| N/A | 业务 Handler 与未知版本终态切换尚未启用 | non-blocking | PLATFORM-SCALE-S03-M4 |

## Next Steps

推进 `PLATFORM-SCALE-S03-M2`，在 delivery 账本上实现带 fencing 的 claim lease、超时接管、错误分类、dead letter 和受控 replay。
