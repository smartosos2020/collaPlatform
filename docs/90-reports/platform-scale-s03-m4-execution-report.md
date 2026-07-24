---
title: PLATFORM-SCALE-S03-M4 执行报告
status: completed
milestone: PLATFORM-SCALE-S03-M4
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S03-M4 Execution Report

## Scope

本轮完成 `PLATFORM-SCALE-S03-M4-T01 到 PLATFORM-SCALE-S03-M4-T11`。Notification、Search 与 realtime signal 已从旧 `DomainEventWorker` 业务分支迁移为独立版本化 Handler；搜索切换为对象级增量投影；实时信号形成独立 durable fact，S04 transport 仍未提前实现。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M4-T01 | static | not-required | not-required | no | event/type/version and side-effect matrix |
| PLATFORM-SCALE-S03-M4-T02 | e2e-real-isolated | real | isolated | no | permission notification reaches recipient |
| PLATFORM-SCALE-S03-M4-T03 | unit | not-required | not-required | no | minimal search payload and owner hydration |
| PLATFORM-SCALE-S03-M4-T04 | e2e-real-isolated | real | isolated | no | incremental upsert/delete and stale-version rejection |
| PLATFORM-SCALE-S03-M4-T05 | unit | not-required | not-required | no | protected cursor rebuild with audit/rate limit |
| PLATFORM-SCALE-S03-M4-T06 | unit | not-required | not-required | no | minimal signal and REST calibration path |
| PLATFORM-SCALE-S03-M4-T07 | integration | real | isolated | no | durable signal without transport dependency |
| PLATFORM-SCALE-S03-M4-T08 | static | not-required | not-required | no | generic registry dispatcher only |
| PLATFORM-SCALE-S03-M4-T09 | integration | not-required | not-required | no | Handler isolation, receipt idempotency and disclosure |
| PLATFORM-SCALE-S03-M4-T10 | e2e-real-isolated | real | isolated | no | role-correct behavior compatibility |
| PLATFORM-SCALE-S03-M4-T11 | e2e-real-isolated | real | isolated | no | six search object types plus notification inbox |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PLATFORM-SCALE-S03-M4-T01 | Done | `event-side-effect-matrix.md` freezes subscriptions, idempotency, ordering, failure and calibration |
| PLATFORM-SCALE-S03-M4-T02 | Done | `NotificationDomainEventHandler` respects preferences/dedupe and appends realtime signal only on first create |
| PLATFORM-SCALE-S03-M4-T03 | Done | Search Handler consumes envelope identity/version only and hydrates content from owner tables |
| PLATFORM-SCALE-S03-M4-T04 | Done | object-level projection plus `search_projection_versions` anti-regression waterline |
| PLATFORM-SCALE-S03-M4-T05 | Done | protected cursor batch rebuild, advisory lock, 250 cap, reason, rate limit and audit |
| PLATFORM-SCALE-S03-M4-T06 | Done | `realtime.signal.requested` contains no notification title/body and always supplies REST calibration |
| PLATFORM-SCALE-S03-M4-T07 | Done | `RealtimeSignalDomainEventHandler` writes source-event-unique pending durable facts |
| PLATFORM-SCALE-S03-M4-T08 | Done | `DomainEventWorker` contains no notification/search/websocket imports or event-type branches |
| PLATFORM-SCALE-S03-M4-T09 | Done | independent deliveries/receipts, permanent validation errors and sensitivity tests |
| PLATFORM-SCALE-S03-M4-T10 | Done | real API + dedicated Worker flow, test-compatible combined dispatcher and existing API behavior |
| PLATFORM-SCALE-S03-M4-T11 | Done | targeted backend, V001-V069, architecture and two real browser flows passed |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M4-T01 | mapping is explicit | `docs/01-architecture/event-side-effect-matrix.md` | subscription descriptors inspected by registry tests | not-required | Done |
| PLATFORM-SCALE-S03-M4-T02 | deduped notification, preference and isolation | Notification Handler + public outbox | handler tests and NotificationPermission integration PASS | real isolated recipient notification page PASS | Done |
| PLATFORM-SCALE-S03-M4-T03 | minimal payload, no ACL snapshot | Search Handler uses envelope ids/sequence; repository reads owner tables | search handler tests PASS | six object categories visible | Done |
| PLATFORM-SCALE-S03-M4-T04 | object upsert/delete and version guard | V069 waterline + conditional upsert | stale version PostgreSQL test PASS | real isolated cross-module search PASS | Done |
| PLATFORM-SCALE-S03-M4-T05 | explicit batched maintenance only | maintenance service/controller and repository cursor page | maintenance authorization/audit/rate-limit tests PASS | not-required | Done |
| PLATFORM-SCALE-S03-M4-T06 | durable minimal signal contract | `realtime.signal.requested` + `/api/notifications` calibration | signal validation tests PASS | REST-calibrated notification PASS | Done |
| PLATFORM-SCALE-S03-M4-T07 | no transport dependency | source-event-unique `realtime_signals` | handler idempotency tests and real Worker completion PASS | real isolated notification remains usable without S04 transport | Done |
| PLATFORM-SCALE-S03-M4-T08 | generic Worker dispatcher | registry-only compatibility path | role/registry and architecture checks PASS | not-required | Done |
| PLATFORM-SCALE-S03-M4-T09 | independent failure/idempotency/disclosure | per-Handler delivery/receipt and public failure contract | Handler unit/integration set PASS | read-only member flow PASS | Done |
| PLATFORM-SCALE-S03-M4-T10 | role-compatible behavior | API appends, Worker consumes, combined only supports local/test | real API/Worker logs and integration tests PASS | real isolated frontend PASS | Done |
| PLATFORM-SCALE-S03-M4-T11 | product and closeout evidence | all M4 implementation and docs | targeted suite, migration and quality gate | real isolated Playwright suite, 2 tests PASS | Done |

## Code Changes

- Migration V069 adds search projection waterlines and durable realtime signals.
- Notification Handler replaces legacy notification branch and emits a minimal follow-up signal transactionally.
- Search Handler and repository provide object-level upsert/delete, version anti-regression and explicit cursor rebuild.
- Realtime Handler persists pending transport facts without Redis or local WebSocket coupling.
- `DomainEventWorker` is now a generic registry dispatcher; production scheduling remains in `ReliableDomainEventWorker`.
- Approval, Base, IM, Knowledge and permission producers now use the public transactional outbox contract.
- Added focused Handler, maintenance, real PostgreSQL and real browser regression tests.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/event-side-effect-matrix.md` | create | freeze event/version/Handler and calibration contract |
| `docs/01-architecture/current-architecture.md` | update | replace expired M3-M4 compatibility facts with current M4 baseline |
| `docs/05-runbooks/event-worker-fleet.md` | update | add Handler diagnosis, search rebuild and signal calibration |
| `docs/02-roadmap/current-roadmap.md` | update | close M4 tasks without activating M5 |
| this report | create | unique task-level evidence |

## Validation

- Backend tests: PASS, 12 targeted Handler/unit tests plus 5 real PostgreSQL integration tests covering notification, search, realtime, maintenance, registry and role contracts.
- Real PostgreSQL integration: PASS, 5 tests covering NotificationPermission, SearchCollaboration and stale search projection protection.
- Migration: PASS, fresh V001-V069 on PostgreSQL 16; local V066-V069 upgrade also completed.
- Browser smoke: PASS, `cross-module-route-final.spec.ts` and `platform-scale-s03-m4-notification.spec.ts`, 2 tests in 18.1 seconds against isolated API + dedicated Worker + frontend.
- Frontend build: PASS.
- Architecture boundaries: PASS; backend private imports 140, frontend cross-feature imports 65, cross-owner reads 93, reverse imports 0 and foreign writes 0.
- Local quality gate: PASS, `.local-reports/quality-gate-20260724T072838.md`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S03-M5 | production-shaped failure injection, backlog recovery thresholds and route-final remain | non-blocking for M4 | M5-T02 to M5-T10 |
| N/A | `realtime_signals` transport/fanout and transported acknowledgement are not implemented | non-blocking | S04 |

## Next Steps

Run the M4 completion gate, then start a new work cycle for `PLATFORM-SCALE-S03-M5`. M5 must not reinterpret the browser result as a capacity promise.
