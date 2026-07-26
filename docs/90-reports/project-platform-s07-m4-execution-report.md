# PROJECT-PLATFORM-S07-M4 Execution Report

## Scope

PROJECT-PLATFORM-S07-M4-T01 至 PROJECT-PLATFORM-S07-M4-T13

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M4-T01 | core-system | system-real-isolated | not-required | isolated | No | state, error, authority, stop and rollback contract |
| PROJECT-PLATFORM-S07-M4-T02 | core-system | system-real-isolated | not-required | isolated | No | REPEATABLE_READ plan/fingerprint, stale-source rejection and dry-run |
| PROJECT-PLATFORM-S07-M4-T03 | core-system | system-real-isolated | not-required | isolated | No | immutable project-unit manifest and append-only history |
| PROJECT-PLATFORM-S07-M4-T04 | core-system | system-real-isolated | not-required | isolated | No | project/issue fields, published binding, numbering and UUID conflict remap |
| PROJECT-PLATFORM-S07-M4-T05 | core-system | system-real-isolated | not-required | isolated | No | participants, comments, attachments, activities and provenance |
| PROJECT-PLATFORM-S07-M4-T06 | core-system | system-real-isolated | not-required | isolated | No | lease/fencing, bounded claim, pause, same-batch resume and failed-unit isolation |
| PROJECT-PLATFORM-S07-M4-T07 | core-system | system-real-isolated | not-required | isolated | No | original-manifest batch verification and old-batch negative result |
| PROJECT-PLATFORM-S07-M4-T08 | core-system | system-real-isolated | not-required | isolated | No | independent workspace convergence and compatibility shadow contract |
| PROJECT-PLATFORM-S07-M4-T09 | core-system | system-real-isolated | not-required | isolated | No | pre-cutover rollback and post-cutover compensation/kill switch |
| PROJECT-PLATFORM-S07-M4-T10 | core-system | system-real-isolated | not-required | isolated | No | governance API, failure download, cross-platform CLI and runbook |
| PROJECT-PLATFORM-S07-M4-T11 | core-system | system-real-isolated | not-required | isolated | No | V001/V061/V065/V078/V085 to V089, repeat migrate and restored backup |
| PROJECT-PLATFORM-S07-M4-T12 | core-system | system-real-isolated | not-required | isolated | No | source drift, lease conflict, sibling failure, UUID conflict, false-positive and rollback faults |
| PROJECT-PLATFORM-S07-M4-T13 | core-system | system-real-isolated | not-required | isolated | No | six-role governance boundary, workspace hiding, SQL index plan and architecture gate |

## Completed Items

- V089 completes the migration runtime with immutable plan payload/fingerprint, lease/fencing/heartbeat, unit outcome fields, canonical comments/attachments, migration provenance and independent append-only verification.
- Planning reads the complete legacy project lifecycle and target published bindings in one `REPEATABLE_READ` snapshot. Dry-run preserves governance evidence without canonical business writes.
- Execution uses one `REQUIRES_NEW` transaction per legacy project, bounded claim with `FOR UPDATE SKIP LOCKED`, lease token/fence/heartbeat, throttle, pause and same-batch resume.
- Each unit migrates project/issue WorkItems, explicit identity maps, published snapshot binding, dynamic values/projections, participants, comments, attachments, activities, provenance and platform object links. A failed unit rolls back in full while a sibling unit continues.
- Batch verification remains tied to the original immutable manifest. Workspace convergence is a separate append-only observation and cannot make an old batch pass.
- Pre-cutover rollback removes only targets created by the batch and retains map/provenance/failure/verification history. Post-cutover rollback enables the kill switch and requires forward compensation.
- Enterprise migration endpoints provide plan, execute, pause, status, failure download, batch verify, convergence verify and rollback. The Node CLI is platform-neutral and the runbook freezes stop/RTO/rollback handling.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M4-T01 | unambiguous migration contract | migration models/service + M4 runbook | state/error/confirmation assertions | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T02 | one snapshot, stale rejection, no dry-run writes | snapshot transaction + immutable plan payload | dry-run and source drift integration cases | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T03 | complete immutable project unit | V088/V089 manifest and history triggers | mutation rejection and source-count assertions | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T04 | exact bindings and explicit identities | migration service + identity resolver | item binding/count and occupied UUID remap | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T05 | lifecycle facts remain traceable | canonical comment/attachment/provenance tables | six migrated source facts and checksum evidence | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T06 | bounded, resumable single-owner execution | repository lease/fence/claim + service loop | competing owner, pause/resume and sibling-unit cases | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T07 | immutable batch conclusion | batch observation and verification history | matched, rollback mismatch and append-only checks | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T08 | independent current convergence | workspace observation + M3 shadow service | convergence matched independently of batch history | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T09 | rollback does not overwrite new facts | cleanup transaction + compensation guard | pre/post-cutover positive and negative cases | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T10 | operable governance surface | admin controller, Node CLI and runbook | backend compile, controller context and Node syntax | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T11 | all required baselines upgrade repeatably | Flyway V086-V089 + matrix fixture | five baselines, sentinel, repeat and backup restore PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T12 | no half-unit, overwritten history or false positive | independent unit transactions and append history | nine fault/security migration scenarios PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M4-T13 | secure bounded control path | manage-projects boundary + indexed claim | role/workspace negatives, EXPLAIN and architecture contracts | Not required | Done |

## Code Changes

- `server/src/main/resources/db/migration/V089__complete_work_item_migration_runtime.sql`
- `server/src/main/java/com/colla/platform/modules/project/{api,application,domain,infrastructure}/**Migration*`
- `server/src/test/java/com/colla/platform/modules/project/application/WorkItemMigrationServiceIntegrationTests.java`
- `server/src/test/java/com/colla/platform/modules/project/infrastructure/WorkItemConfigurationMigrationMatrixIntegrationTests.java`
- `deploy/ops/project-work-item-migration.mjs` and root `project:migrate-work-items`
- `tools/workbench/config/platform-table-owners.json`
- project current/target architecture, module, object, event and migration runbook documents

## Validation

- Backend compile: `mvn -q -DskipTests compile` PASS.
- Backend tests: `WorkItemMigrationServiceIntegrationTests`; 9/9 isolated PostgreSQL scenarios PASS.
- Migration integration: `mvn -q -Dtest=WorkItemMigrationServiceIntegrationTests test`; 9 scenarios PASS against Testcontainers PostgreSQL 16.
- Flyway matrix: `mvn -q -Dtest=WorkItemConfigurationMigrationMatrixIntegrationTests test`; V001/V061/V065/V078/V085 to V089, repeat migrate, sentinel and backup restore PASS.
- Architecture: `pnpm architecture:contracts`; 15 modules, 120 active tables, 93 exact exceptions and 23 contract files PASS.
- CLI: `node --check deploy/ops/project-work-item-migration.mjs` PASS.
- Frontend build: Not required; M4 has no frontend source change and activates no user route.
- Browser smoke: Not required; M4 exposes enterprise governance API/CLI and does not activate a user route. User route evidence belongs to M5.
- Local quality gate: stage checkpoint PASS (`.local-reports/quality-gate-20260726T082045.md`) and final finish PASS (`.local-reports/quality-gate-20260726T083045.md`).

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None inside M4 scope | non-blocking | Closed |

Production execution remains subject to target-environment backup/restore, monitoring, approved capacity window and operator approval. This is a release boundary, not an unfinished M4 implementation.

## Next Steps

- M5 uses the canonical comment/attachment facts and migration control plane to deliver the end-user vertical slice.
- Do not change a production space to canonical write based only on local M4 evidence.
