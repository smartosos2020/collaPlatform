---
title: PROJECT-PLATFORM-S03-M2 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S03-M2
stage: PROJECT-PLATFORM-S03
updated_at: 2026-07-22
---

# PROJECT-PLATFORM-S03-M2 Execution Report

## Scope

- PROJECT-PLATFORM-S03-M2-T01 to PROJECT-PLATFORM-S03-M2-T11.
- 本里程碑交付空间工作项类型配置 API、服务端动作策略、空间角色与企业治理分层、请求幂等、审计/outbox、用户 active 摘要和治理计数。
- 不交付前端配置页面、研发预置目录、动态字段、表单布局、工作流、工作项实例或 legacy project/issue 写切流；这些仍由 S03-M3、M4 和后续 Stage 承担。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M2-T01 | integration | not-required | not-required | no | verify separate configuration, user-summary and governance DTOs plus stable HTTP errors and request replay |
| PROJECT-PLATFORM-S03-M2-T02 | integration | not-required | not-required | no | read ordered configuration collections and details as space owner/admin |
| PROJECT-PLATFORM-S03-M2-T03 | integration | not-required | not-required | no | create a custom type, revise display metadata and reject stale aggregate versions |
| PROJECT-PLATFORM-S03-M2-T04 | integration | not-required | not-required | no | copy to an independent v1 and atomically reorder one status group |
| PROJECT-PLATFORM-S03-M2-T05 | integration | not-required | not-required | no | traverse active/disabled/retired transitions and enforce system/terminal guards |
| PROJECT-PLATFORM-S03-M2-T06 | integration | not-required | not-required | no | compare server-projected actions across space role, space state, type state and system flag |
| PROJECT-PLATFORM-S03-M2-T07 | integration | not-required | not-required | no | exercise owner/admin/member/guest/nonmember/enterprise-governor access matrix |
| PROJECT-PLATFORM-S03-M2-T08 | integration | not-required | not-required | no | replay one request id and inspect one receipt, audit row and outbox event per committed command |
| PROJECT-PLATFORM-S03-M2-T09 | integration | not-required | not-required | no | compare minimal active summaries with aggregate-only governance counts |
| PROJECT-PLATFORM-S03-M2-T10 | integration | not-required | not-required | no | run role, isolation, lifecycle, concurrency, rollback, idempotency and OpenAPI scenarios on PostgreSQL |
| PROJECT-PLATFORM-S03-M2-T11 | integration | not-required | not-required | no | run planning, compilation, focused suites and strict work-cycle report gates |

Browser evidence is not required because M2 changes backend APIs and projections only. S03-M3 owns production UI, interaction states and isolated real-browser verification.

## API Contract

### Surfaces

| Audience | Method and path | Response boundary | Authorization |
| --- | --- | --- | --- |
| space configuration | `GET /api/project-spaces/{spaceId}/configuration/types` | full definition, current version and server actions | space owner/admin |
| space configuration | `GET /api/project-spaces/{spaceId}/configuration/types/{typeId}` | one full configured type | space owner/admin |
| space configuration | `POST /api/project-spaces/{spaceId}/configuration/types` | newly created custom type with published v1 | active-space owner/admin |
| space configuration | `PATCH /api/project-spaces/{spaceId}/configuration/types/{typeId}` | revised display metadata and next aggregate version | active-space owner/admin; custom non-retired type |
| space configuration | `POST .../types/{typeId}:copy` | independent custom type and published v1 | active-space owner/admin |
| space configuration | `PUT .../types:reorder` | refreshed ordered collection | active-space owner/admin; one non-retired status group |
| space configuration | `POST .../types/{typeId}:disable` | disabled type | active-space owner/admin |
| space configuration | `POST .../types/{typeId}:restore` | active type | active-space owner/admin |
| space configuration | `POST .../types/{typeId}:retire` | terminal retired type | active-space owner/admin; custom type only |
| user execution | `GET /api/project-spaces/{spaceId}/work-item-types` | active `typeKey/name/icon/sortOrder` only | any space member, including member/guest |
| enterprise governance | existing `GET /api/admin/project-spaces[/{spaceId}]` | `total/active/disabled/retired` counts only | enterprise `project.manage` |

All write requests use `X-Colla-Request-Id` from the shared request boundary. Replaying the same workspace/request id, actor, operation and canonical payload returns the committed result without another mutation, audit row or outbox event. Reusing the id with a different command returns `idempotency_conflict`; an observed unfinished command returns `idempotency_in_progress`.

### Stable Errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `not_found_or_hidden` | 404 | missing, foreign-workspace, cross-space or concealed nonmember resource |
| `type_key_conflict` | 409 | the permanent space-local key is already occupied |
| `version_conflict` | 409 | aggregate version is stale |
| `system_type_protected` | 409 | a protected system type received a forbidden operation |
| `retired_type` | 409 | a terminal type received a non-applicable operation |
| `invalid_type_key` | 400 | key does not match the canonical identifier contract |

Validation failures use `invalid_input`; denied member roles use `forbidden`. Every error body includes the shared request id.

### Version and Event Semantics

- The M1 published v1 remains immutable. M2 display revision updates definition metadata only and does not rewrite the published config/hash; later version authoring belongs to S04-S06.
- `availableActions` is generated by `WorkItemTypeActionPolicy`. The client must not infer permissions from role names or error text.
- Every committed type command records actor, space, type key, before/after snapshots and request id in audit metadata and emits a deterministic-idempotency outbox event.
- Batch reorder validates the complete set before mutation and records audit/outbox only after every optimistic update succeeds, so stale entries roll the transaction back without orphan evidence.
- Governance list counts are loaded in one workspace-scoped aggregate query; the enterprise surface receives no configuration, version or membership-derived content rights.

## Completed Items

- Added V062 command receipts with workspace/request uniqueness, scoped foreign keys and pending/completed consistency checks.
- Added distinct API DTOs for configuration details, user execution summaries and enterprise governance counts.
- Added list, detail, create, display revision, copy, reorder, disable, restore and retire endpoints.
- Added one service-side action policy and enforced owner/admin, member/guest, nonmember and enterprise-governor boundaries.
- Added request-id command replay plus stable conflict behavior.
- Added audit/outbox snapshots and deterministic event keys for every committed command.
- Added bulk governance count projection without per-space query multiplication.
- Added five HTTP integration scenarios covering the M2 positive and negative matrix; combined M1+M2 focused suites contain 20 passing tests.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M2-T01 | three response families and six principal failure codes remain distinct | `WorkItemTypeApiDtos`, `WorkItemTypeExceptionHandler`, V062 receipt schema | DTO exposure, error body and replay assertions pass in controller integration suite | not-required: HTTP contract has no UI in M2 | Done |
| PROJECT-PLATFORM-S03-M2-T02 | manager collection/detail responses are ordered and version-complete | configuration controller and service projections | owner reads status-filtered collection and full current-version detail from PostgreSQL | not-required: API-only milestone | Done |
| PROJECT-PLATFORM-S03-M2-T03 | custom definition metadata follows validation and optimistic version rules | configuration controller, definition service and scoped repository update | invalid key, duplicate key and concurrent stale-writer cases return stable outcomes | not-required: UI belongs to M3 | Done |
| PROJECT-PLATFORM-S03-M2-T04 | copied aggregate owns a separate v1 and batch ordering is atomic | copy/reorder service commands and transactional repository writes | independent copy, duplicate order and stale batch rollback assertions pass | not-required: interaction delivery belongs to M3 | Done |
| PROJECT-PLATFORM-S03-M2-T05 | lifecycle graph, terminal state and system guard are consistent | transition command plus domain/database guards | disable/restore/retire, replay and system guard cases pass | not-required: backend lifecycle contract | Done |
| PROJECT-PLATFORM-S03-M2-T06 | action projection is fully server-derived | `WorkItemTypeActionPolicy` used by collection and detail mapping | role/state/system combinations and inactive-space empty actions pass | not-required: M3 consumes this projection | Done |
| PROJECT-PLATFORM-S03-M2-T07 | space membership and enterprise governance grants remain independent | manager/member guards plus `PermissionService.requireManageProjects` | owner/admin/member/guest/nonmember/root and foreign-scope matrix passes | not-required: service authorization evidence | Done |
| PROJECT-PLATFORM-S03-M2-T08 | one canonical request yields one durable command trace | command repository, audit service and domain event repository integration | replay preserves one aggregate, receipt, audit row and event; batch failure leaves none | not-required: persistence evidence | Done |
| PROJECT-PLATFORM-S03-M2-T09 | execution summaries are minimal while governance remains aggregate-only | user controller and admin project-space count projection | active-only four-field summaries and count-only admin JSON assertions pass | not-required: response-boundary evidence | Done |
| PROJECT-PLATFORM-S03-M2-T10 | real database scenarios cover role, scope, concurrency and rollback risks | four focused M1/M2 test classes | Maven reports 20 tests, 0 failures, 0 errors and 0 skipped against PostgreSQL containers | not-required: no frontend source changed | Done |
| PROJECT-PLATFORM-S03-M2-T11 | documented paths and quality checks match the executable contract | API Contract section, controller mappings and current roadmap | OpenAPI path assertions, compile, planning check and work-cycle gates | not-required: M2 has no browser surface | Done |

## Code Changes

- `server/src/main/resources/db/migration/V062__create_project_work_item_type_command_receipts.sql`: idempotency receipt model and scope constraints.
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemTypeConfigurationController.java`: configuration query and command API.
- `server/src/main/java/com/colla/platform/modules/project/api/UserWorkItemTypeController.java`: minimal active execution summary.
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemTypeApiDtos.java`: audience-specific response families.
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemTypeExceptionHandler.java`: stable scoped HTTP errors.
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemTypeConfigurationService.java`: authorization, commands, replay, audit/outbox and projections.
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemTypeActionPolicy.java`: centralized action calculation.
- Work-item type definition/repository classes: display revision, status counts, bulk governance counts and low-level guards.
- Admin project-space DTO/controller: aggregate type counts without configuration disclosure or per-row count queries.
- `WorkItemTypeConfigurationControllerIntegrationTests`: five broad HTTP/database scenarios for all M2 tasks.

## Validation

- Backend compile: `mvn -q -DskipTests test-compile` passed.
- Backend tests: `mvn -q "-Dtest=WorkItemTypeModelsTests,ProjectWorkItemTypeSchemaIntegrationTests,WorkItemTypeDefinitionServiceIntegrationTests,WorkItemTypeConfigurationControllerIntegrationTests" test` passed; 20 tests, 0 failures, 0 errors, 0 skipped.
- Migration: the focused schema suite applies the latest empty schema and an isolated V060-to-V062 upgrade against PostgreSQL.
- Frontend build: not run because M2 changes no frontend source, route or behavior; M3 owns frontend delivery.
- Local quality gate: light checkpoint passed in `quality-gate-20260722T083637.md`; stage finish reruns focused tests and strict report/roadmap checks.
- Browser smoke: not required because this milestone exposes backend contracts only.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M3-T01 | production configuration UI and execution-side entry have not consumed these API contracts | non-blocking for M2; no frontend delivery is claimed | current roadmap M3 |
| PROJECT-PLATFORM-S03-M4-T01 | the six development preset definitions and existing-space backfill are not installed yet | non-blocking for M2; system-type policy is ready but no preset catalog is claimed | current roadmap M4 |
| PROJECT-PLATFORM-S03-M5-T04 | published v1 does not yet contain dynamic field definitions | non-blocking; M2 deliberately preserves the M1 skeleton boundary | program S04, reviewed again at S03-M5 |
| PROJECT-PLATFORM-S03-M5-T04 | layout, full authoring/publish pipeline and canonical work-item instances remain deferred | non-blocking; no premature runtime or dual-write path was added | program S05/S06, reviewed again at S03-M5 |

## Next Steps

- Start PROJECT-PLATFORM-S03-M3 from its API client and cache contract task.
- Render every configuration action from server-provided `availableActions` and prove role/state negatives in isolated browser flows.
- Keep legacy project/issue writes unchanged while M3 adds only type configuration and active-summary UI.
