---
title: PROJECT-PLATFORM-S01-M3 Execution Report
status: archived
milestone: PROJECT-PLATFORM-S01-M3
updated_at: 2026-07-18
---

# PROJECT-PLATFORM-S01-M3 Execution Report

## Scope

- PROJECT-PLATFORM-S01-M3-T01 到 PROJECT-PLATFORM-S01-M3-T09。
- 本里程碑冻结物理存储、ID/编号、批迁移、兼容退出、配置升级与双流程运行时方向，不修改生产 API、Flyway、业务表或页面。
- 输入为 M1 当前事实审计与 M2 领域合同 v1；输出供 S02 空间底座、S03 迁移和 S04 规范工作项实施直接使用。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M3-T01 | integration | not-required | not-required | no | compare JSONB, typed rows and hybrid projection in isolated PostgreSQL |
| PROJECT-PLATFORM-S01-M3-T02 | integration | not-required | not-required | no | preserve IDs, aliases and atomic counter watermarks in isolated PostgreSQL |
| PROJECT-PLATFORM-S01-M3-T03 | integration | not-required | not-required | no | rerun one isolated PostgreSQL migration batch and compare counts/checksums |
| PROJECT-PLATFORM-S01-M3-T04 | static | not-required | not-required | no | trace every compatibility phase to owner and exit |
| PROJECT-PLATFORM-S01-M3-T05 | integration | not-required | not-required | no | execute equivalent indexed dynamic-field queries in isolated PostgreSQL |
| PROJECT-PLATFORM-S01-M3-T06 | integration | not-required | not-required | no | reject immutable edit and perform optimistic explicit upgrade in isolated PostgreSQL |
| PROJECT-PLATFORM-S01-M3-T07 | integration | not-required | not-required | no | execute state/node commands with shared history and separate runtime in isolated PostgreSQL |
| PROJECT-PLATFORM-S01-M3-T08 | static | not-required | not-required | no | review every P0/P1 prevention, detection, containment and owner |
| PROJECT-PLATFORM-S01-M3-T09 | static | not-required | not-required | no | trace S02/S03/S04 inputs to frozen decisions |

Browser evidence is not required because M3 changes no UI or production API. The runnable evidence is JDBC against a disposable PostgreSQL container, not mocks and not the developer database.

## Completed Items

- Chose canonical JSONB plus capability-declared typed synchronous projections; pure JSONB, all-EAV and per-field DDL remain rejected as the complete storage model.
- Fixed UUID reuse/collision behavior, mandatory legacy mapping, alias retention and atomic display-number watermarks.
- Defined project-bound migration batches with preflight, dry-run, unit transactions, checksums, failure lists, idempotent retry and cutover-aware rollback.
- Defined canonical-only writes, controlled legacy read fallback, workspace rollout flags, compatibility registry and S21 latest active compatibility removal.
- Proved query/index mechanics, immutable version binding and separate state/node runtime mechanics in four PostgreSQL integration tests.
- Added P0/P1 migration, authorization, data, performance, projection, workflow and cross-module risk controls.
- Added ADR-PP-011 through ADR-PP-016 and direct implementation inputs for S02/S03/S04 and S06-S09.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M3-T01 | 三种存储方案形成基于查询、索引、迁移、校验、扩展和运维证据的唯一选择 | target architecture 14.1; ADR-PP-011/015 | 20,000-row equivalent-result spike | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T02 | 规范 ID、编号和旧标识映射支持稳定定位与重定向 | target architecture 14.2; ADR-PP-012 | 4/4 UUID preserved; counters advanced to 8/13 | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T03 | 迁移批次具备 dry-run、校验和、失败清单、重试和回退合同 | target architecture 14.3; ADR-PP-013 | same batch executed twice; 4 targets/maps; equal checksum; zero duplicates | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T04 | 每个兼容面具备 owner、监控、退出条件和最晚退役 Stage | target architecture 14.4 and 15; ADR-PP-014 | static phase/owner/exit review | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T05 | 常用筛选、排序、分组方案具备可运行证据和性能预算 | target architecture 14.1 | 200 equivalent matches; JSONB 1.072ms, typed 1.631ms, hybrid 2.393ms p95 | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T06 | 旧实例保持旧版本，显式升级具备差异预览和不兼容拒绝 | target architecture 14.5 | immutable edit rejected; one upgrade succeeds; stale upgrade updates zero; rollback is v3 | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T07 | 两类运行时共享事件、授权和历史且保持权威状态分离 | target architecture 14.6; ADR-PP-016 | 2 shared history rows; stale/duplicate rejected; runtime cross-counts zero | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T08 | P0/P1 风险具备预防、探测、回退和责任 Stage | target architecture 14.7 | static risk traceability review | not-required: no UI surface | Done |
| PROJECT-PLATFORM-S01-M3-T09 | 后续 Stage 可直接采用物理与兼容策略，无核心悬而未决项 | target architecture 14.8; ADR-PP-011..016 | S02/S03/S04 and S06-S09 input review | not-required: no UI surface | Done |

## Spike Evidence

Test: `server/src/test/java/com/colla/platform/modules/project/spike/ProjectPlatformArchitectureSpikeTests.java`

| Scenario | Result |
| --- | --- |
| Dynamic field storage | 20,000 rows; 200 matches in all variants; JSONB p95 1.072ms; typed p95 1.631ms; hybrid p95 2.393ms |
| Batched migration | source 4; target 4; map 4; rerun duplicates 0; source/target checksum match |
| Configuration version | 3 immutable versions; explicit upgrade 1; stale upgrade 0; rollback represented by new version 3 |
| Workflow runtime | shared history 2; state uses current state; node uses active tokens; stale update 0; duplicate idempotency rejected |

The measurements are mechanism evidence on one disposable local container. They do not establish production capacity. S04 must execute the documented 100,000-item concurrent baseline before adopting the <= 200ms interactive query budget.

## Code Changes

- `server/src/test/java/com/colla/platform/modules/project/spike/ProjectPlatformArchitectureSpikeTests.java`: isolated PostgreSQL spikes for T01/T02/T03/T05/T06/T07; no Flyway and no developer-database writes.
- `docs/01-architecture/project-platform-target-architecture.md`: migration contract v1, ADR-PP-011..016, risk register and downstream implementation inputs.
- `docs/02-roadmap/current-roadmap.md`: M3 task closure and M4 execution entry.
- `docs/90-reports/project-platform-s01-m3-execution-report.md`: task-level verification and evidence closure.

## Validation

- Backend tests: `mvn -Dtest=ProjectPlatformArchitectureSpikeTests test` -> PASS, 4 tests, 0 failures/errors/skips.
- Frontend build: not required; no frontend source or contract changed in M3.
- Flyway/full backend suite: deferred to S01 M4 route-final; M3 spike creates only disposable Testcontainers tables.
- Local quality gate: light checkpoint PASS in `.local-reports/quality-gate-20260718T125251.md`; backend compile evidence is `.local-reports/quality-gate-20260718T125251-backend-compile.log`.
- Browser smoke: not required; M3 has no affected browser behavior.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | Production migration must include comments, attachments, relations, permissions and all M1 cross-module consumers | non-blocking | target architecture 14.3/14.8; required in S03 |
| N/A | Local 20,000-row spike is not a concurrent production capacity result | non-blocking | target architecture 14.1; required in S04 |
| N/A | Real backup/restore and workspace rollback drills are not executed in S01 | non-blocking | target architecture 14.7; required in S03/S04 before cutover |

## Next Steps

- Execute PROJECT-PLATFORM-S01-M4-T01 through T08 as a separate work-cycle milestone.
- M4 must review M1-M3 traceability, run route-final gates, make the S01 Go/No-Go decision and activate S02 only when no blocking input remains.
