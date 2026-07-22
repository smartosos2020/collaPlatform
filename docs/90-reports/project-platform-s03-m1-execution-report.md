---
title: PROJECT-PLATFORM-S03-M1 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S03-M1
stage: PROJECT-PLATFORM-S03
updated_at: 2026-07-22
---

# PROJECT-PLATFORM-S03-M1 Execution Report

## Scope

- PROJECT-PLATFORM-S03-M1-T01 to PROJECT-PLATFORM-S03-M1-T10.
- 本里程碑交付空间级工作项类型定义、不可变版本、规范配置哈希、生命周期、排序与并发持久化底座。
- 不交付 HTTP API、空间授权动作、审计/outbox 写入、治理投影、前端配置页、研发预置类型或工作项实例；这些分别由 S03-M2 至 M4 及后续 Stage 承担。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M1-T01 | static | not-required | not-required | no | compare S02 aggregate, membership, audit/outbox and platform-object boundaries with S03 admission package |
| PROJECT-PLATFORM-S03-M1-T02 | integration | not-required | not-required | no | apply V061 on empty PostgreSQL and upgrade an isolated V060 database to latest |
| PROJECT-PLATFORM-S03-M1-T03 | unit | not-required | not-required | no | validate type key, status, lifecycle and stable domain error codes |
| PROJECT-PLATFORM-S03-M1-T04 | unit | not-required | not-required | no | canonicalize equivalent skeleton configs and verify stable SHA-256 hashes |
| PROJECT-PLATFORM-S03-M1-T05 | integration | not-required | not-required | no | read definitions and versions only through matching workspace and space scope |
| PROJECT-PLATFORM-S03-M1-T06 | integration | not-required | not-required | no | persist definition and published v1 atomically; roll back on version failure and same-key contention |
| PROJECT-PLATFORM-S03-M1-T07 | integration | not-required | not-required | no | exercise lifecycle graph, terminal retired state and database trigger enforcement |
| PROJECT-PLATFORM-S03-M1-T08 | integration | not-required | not-required | no | race aggregate writers and same-key writers and assert stable conflict outcomes |
| PROJECT-PLATFORM-S03-M1-T09 | integration | not-required | not-required | no | run domain, schema and service suites against real PostgreSQL containers |
| PROJECT-PLATFORM-S03-M1-T10 | integration | not-required | not-required | no | run planning, compile, targeted tests, migration and strict work-cycle document gates |

Browser evidence is not required because M1 changes only schema and backend domain/persistence code. S03-M3 owns the production UI and isolated real-browser closure.

## Implementation Positioning

| Concern | Reused current fact | M1 implementation | Deferred owner |
| --- | --- | --- | --- |
| space aggregate | `project_spaces` remains the collaboration container and lifecycle authority | every type is scoped by workspace and space composite keys; only active spaces accept new definitions | S03-M2 authorization and API |
| membership and authorization | S02 space roles remain authoritative; enterprise governance does not imply content access | no second membership or permission model was introduced | S03-M2 action projection |
| audit and outbox | shared audit/outbox facilities remain the integration boundary | M1 stores actor/time facts but emits no premature public event | S03-M2 audited commands |
| platform objects | `project_space`, legacy `project` and `issue` resolvers remain unchanged | type definitions are configuration records, not independently navigable platform objects | later Stage only if navigation requires it |
| legacy runtime | legacy project/issue writes remain canonical | V061 does not alter legacy tables, controllers or resolver behavior | S07 cutover |
| configuration scope | S03 admission permits only a minimal type skeleton | v1 config contains display metadata, schema version and type key; no fields, layout, workflow or role graph | S04-S06 |

No planning conflict was found. The implementation stays inside the target architecture section 19 admission package.

## Completed Items

- Added V061 with type-definition and type-version tables, composite isolation keys, deferred current-version reference, lifecycle checks, indexes and immutable-row triggers.
- Added domain records, stable validation codes and lifecycle rules for definition and version status.
- Added recursive canonical JSON serialization and SHA-256 config hashing for the published v1 skeleton.
- Added JDBC repository projections and workspace/space-scoped lookups.
- Added transactional definition service for atomic v1 persistence, lifecycle transitions, reordering and optimistic conflict handling.
- Added an empty reference-guard extension point without fabricating references before work-item instances exist.
- Added 15 focused unit/integration scenarios, including isolated Flyway upgrade and real concurrent writers.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M1-T01 | reuse boundaries and deferred capabilities are explicit | Implementation Positioning table maps S02 contracts and S03 exclusions | static source/schema and target-architecture review | not-required: backend boundary review | Done |
| PROJECT-PLATFORM-S03-M1-T02 | schema is scope-safe, constrained and upgradeable | `V061__create_project_work_item_type_foundation.sql` | schema suite validates latest empty migration and isolated V060-to-V061 upgrade | not-required: database-only change | Done |
| PROJECT-PLATFORM-S03-M1-T03 | domain identifiers, states and validation failures are stable | `WorkItemTypeModels` | 3 domain tests cover normalization, invalid values and lifecycle graph | not-required: domain-only contract | Done |
| PROJECT-PLATFORM-S03-M1-T04 | semantically equal skeletons yield an equal hash and published payloads stay immutable | `WorkItemTypeConfigCanonicalizer` plus V061 version trigger | domain hash test and schema mutation rejection pass | not-required: backend configuration contract | Done |
| PROJECT-PLATFORM-S03-M1-T05 | all definition/version projections preserve workspace and space isolation | repository interface and JDBC implementation use composite scope predicates | foreign workspace reads return empty and composite constraints reject invalid ownership | not-required: repository-only contract | Done |
| PROJECT-PLATFORM-S03-M1-T06 | definition, published v1 and current pointer commit or roll back together | transactional `WorkItemTypeDefinitionService` with deferred current-version FK | success, same-key contention and forced version failure scenarios leave no partial aggregate | not-required: service-only transaction | Done |
| PROJECT-PLATFORM-S03-M1-T07 | lifecycle graph and terminal state are enforced with a future reference extension point | domain transition rules, database guard and `WorkItemTypeReferenceGuard` | service and direct-SQL scenarios reject illegal restoration and identity mutation | not-required: no lifecycle UI in M1 | Done |
| PROJECT-PLATFORM-S03-M1-T08 | aggregate and key contention never silently overwrite data | optimistic aggregate version predicates and unique-key error mapping | concurrent reorder has one winner; concurrent same-key writers yield one success and one `TYPE_KEY_CONFLICT` | not-required: concurrency is below HTTP layer | Done |
| PROJECT-PLATFORM-S03-M1-T09 | focused suites cover migration, rollback, isolation, immutability, hash and concurrency | three M1 test classes provide 15 scenarios | Maven reports 15 tests, 0 failures, 0 errors and 0 skipped against PostgreSQL containers | not-required: no web route changed | Done |
| PROJECT-PLATFORM-S03-M1-T10 | implementation decisions and stage evidence are reproducible | current roadmap and this execution report | planning check, compile, targeted Maven suites and work-cycle gates pass | not-required: S03-M3 owns real browser closure | Done |

## Code Changes

- `server/src/main/resources/db/migration/V061__create_project_work_item_type_foundation.sql`: physical model, constraints, indexes and immutable/lifecycle triggers.
- `server/src/main/java/com/colla/platform/modules/project/domain/WorkItemTypeModels.java`: definition/version records, statuses, normalization and stable exceptions.
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemTypeConfigCanonicalizer.java`: minimal config skeleton, canonical ordering and hash.
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemTypeDefinitionService.java`: atomic persistence, lifecycle, ordering and conflict mapping.
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemTypeReferenceGuard.java`: future instance-reference protection seam.
- `server/src/main/java/com/colla/platform/modules/project/infrastructure/WorkItemTypeRepository.java` and `JdbcWorkItemTypeRepository.java`: scoped persistence contract and implementation.
- M1 domain, schema and service test classes: focused validation of all acceptance paths.

## Validation

- Backend tests: `mvn -q "-Dtest=WorkItemTypeModelsTests,ProjectWorkItemTypeSchemaIntegrationTests,WorkItemTypeDefinitionServiceIntegrationTests" test` passed; 15 tests, 0 failures, 0 errors, 0 skipped.
- Frontend build: not run because M1 changes no frontend source, route or browser behavior; S03-M3 owns frontend delivery.
- Local quality gate: light checkpoint passed in `quality-gate-20260722T075242.md`; stage finish reruns targeted backend and strict report/roadmap checks.
- Browser smoke: not required because this milestone exposes no HTTP endpoint or production UI and changes no existing user flow.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M2-T01 | API DTO, authorization, audited commands and governance projections are deliberately outside the persistence milestone | non-blocking for M1; no API delivery is claimed | current roadmap M2 |
| PROJECT-PLATFORM-S03-M3-T01 | production configuration and execution-side UI are scheduled after backend contracts stabilize | non-blocking for M1; no browser delivery is claimed | current roadmap M3 |
| PROJECT-PLATFORM-S03-M4-T01 | preset catalogs and existing-space backfill depend on the M1 aggregate but are separate rollout work | non-blocking for M1; no preset data is claimed | current roadmap M4 |
| N/A | legacy project/issue writes remain authoritative by the fixed Stage boundary | non-blocking | Program S07 |

## Next Steps

- Start PROJECT-PLATFORM-S03-M2 only after the M1 stage finish gate succeeds.
- Build API, authorization, audit/outbox and governance projections on the M1 aggregate without expanding its config payload.
- Preserve legacy project/issue runtime behavior until the explicit later-stage migration boundary.
