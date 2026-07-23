---
title: PROJECT-PLATFORM-S04-M1 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S04-M1
stage: PROJECT-PLATFORM-S04
updated_at: 2026-07-22
---

# PROJECT-PLATFORM-S04-M1 Execution Report

## Scope

- PROJECT-PLATFORM-S04-M1-T01 to PROJECT-PLATFORM-S04-M1-T11.
- 本里程碑交付工作项字段定义物理模型、首批字段类型能力目录、规范空配置、字段配置 API、空间授权、幂等、乐观锁、审计/outbox 和隔离验证。
- 本轮不交付选项、默认值、校验规则、复杂类型专属配置、字段配置 UI、布局、发布、工作项实例或字段值；不改写 S03 published v1，也不改变 legacy project/issue 写路径。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M1-T01 | static | not-required | not-required | no | compare S03 type/action/reference/idempotency seams with S04 admission and deferred S05-S07 owners |
| PROJECT-PLATFORM-S04-M1-T02 | unit | not-required | not-required | no | assert the ordered 11-type storage/operator/capability matrix |
| PROJECT-PLATFORM-S04-M1-T03 | integration | not-required | not-required | no | migrate V001-V064 on PostgreSQL and inspect composite constraints, indexes, triggers and absent instance schema |
| PROJECT-PLATFORM-S04-M1-T04 | unit | not-required | not-required | no | query every registered descriptor and reject an unregistered type |
| PROJECT-PLATFORM-S04-M1-T05 | integration | real | isolated | no | exercise immutable key/type boundaries, lifecycle, system protection and stable errors through the real API |
| PROJECT-PLATFORM-S04-M1-T06 | unit | not-required | not-required | no | canonicalize null/empty config to one SHA-256 result and reject undeclared properties |
| PROJECT-PLATFORM-S04-M1-T07 | integration | real | isolated | no | list/detail only through matching workspace, space and type scope and reject a cross-space field id |
| PROJECT-PLATFORM-S04-M1-T08 | e2e-real-isolated | real | isolated | no | create, update, reorder, disable, restore and retire with request ids and aggregate versions against real services |
| PROJECT-PLATFORM-S04-M1-T09 | e2e-real-isolated | real | isolated | no | prove owner/admin positives and member/guest/nonmember/governor negatives on canonical APIs |
| PROJECT-PLATFORM-S04-M1-T10 | e2e-real-isolated | real | isolated | no | replay a write without duplicate aggregate/audit/event and inspect rollback-safe side effects |
| PROJECT-PLATFORM-S04-M1-T11 | e2e-real-isolated | real | isolated | no | run targeted domain/schema/API suites plus the complete isolated identity flow |

## Implementation Positioning

| Concern | Reused S03 fact | M1 implementation | Deferred owner |
| --- | --- | --- | --- |
| type aggregate | `project_work_item_types` remains the owning definition and published v1 stays immutable | fields reference one type through workspace/space/type composite keys and live in an independent authoring aggregate | S06 materializes a new immutable type version |
| action policy | S03 computes actions on the server from space role and lifecycle | `WorkItemFieldActionPolicy` projects collection/field actions; controller never infers role rights | S04-M4 renders these actions |
| reference protection | S03 exposes a no-fabrication guard before instances exist | `WorkItemFieldReferenceGuard` blocks future unsafe retirement without inventing current references | S05 layouts, S06 versions and S07 values add concrete guards |
| idempotency/concurrency | S03 commands use workspace request ids and aggregate versions | field commands persist request hash/response, replay responses and reject stale writers; reorder is one transaction | later field subaggregates reuse the contract |
| audit/outbox | shared PostgreSQL repositories are the integration boundary | field audit rows, domain events, command receipt and aggregate mutation commit or roll back together | downstream consumers remain separate |
| legacy runtime | `/projects`, `/issues` and fixed workflow remain canonical | no legacy table/controller/resolver mutation and no `project_work_items` table/API | S07 cutover |

No admission conflict was found. M1 uses the S03 extension points without introducing layout, publication or instance concepts early.

## Frozen Field Type Matrix

| Type | Canonical value/storage meaning | Operators | Filter | Sort | Index capability |
| --- | --- | --- | --- | --- | --- |
| `text` | JSON string / `string` | eq, neq, contains, is_empty | yes | yes | text |
| `number` | JSON number / `decimal` | eq, neq, gt, gte, lt, lte, is_empty | yes | yes | numeric |
| `boolean` | JSON boolean / `boolean` | eq, is_empty | yes | yes | scalar |
| `single_select` | stable option key / `option_key` | eq, neq, in, is_empty | yes | yes | keyword |
| `multi_select` | stable option-key array / `option_keys` | contains_any, contains_all, is_empty | yes | no | keyword_array |
| `user` | principal reference array / `principal_refs` | contains_any, contains_all, is_empty | yes | no | principal_array |
| `date` | calendar date / `date` | eq, before, after, between, is_empty | yes | yes | date |
| `datetime` | instant / `instant` | eq, before, after, between, is_empty | yes | yes | timestamp |
| `url` | normalized URL string / `string` | eq, contains, is_empty | yes | yes | text |
| `attachment` | file reference array / `file_refs` | is_empty | yes | no | file_array |
| `work_item_reference` | work-item reference array / `work_item_refs` | contains_any, is_empty | yes | no | reference_array |

M1 freezes these names and capabilities but does not persist values. Every descriptor currently exposes config schema version 1 with an empty object schema; M2/M3 extend only declared type-specific configuration contracts.

## Completed Items

- Added V064 field-definition and command-receipt tables with composite ownership, permanent key, lifecycle, JSON/hash, ordering, optimistic-version and identity guards.
- Added field domain records, normalization, lifecycle and stable exception codes.
- Added deterministic 11-type registry and canonical empty-config hashing.
- Added workspace/space/type-scoped JDBC repositories and future reference-guard extension point.
- Added transactional create/update/reorder/lifecycle service, server-projected actions, request replay and rollback-safe audit/outbox writes.
- Added field type catalog and field configuration REST DTO/controllers plus unified structured error mapping.
- Added focused unit, PostgreSQL schema/API integration and real isolated Playwright identity coverage.
- Updated current product, architecture, object-model and technology facts without claiming M2-M4/S06/S07 capabilities.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M1-T01 | reuse, gaps, downstream owners and prohibited scope are locatable | Implementation Positioning table and unchanged S03 aggregate APIs | source/schema review plus negative OpenAPI/schema assertions | not-required: architecture boundary review | Done |
| PROJECT-PLATFORM-S04-M1-T02 | initial field input/storage/operator/capability definitions are unique | Frozen Field Type Matrix and `WorkItemFieldTypeRegistry` | registry test asserts all 11 ordered descriptors and capabilities | not-required: deterministic backend catalog | Done |
| PROJECT-PLATFORM-S04-M1-T03 | schema is composite-scope safe, constrained and migration-ready | `V064__create_project_work_item_field_foundation.sql` | schema integration inspects tables, four core constraints, three indexes, trigger and absent instance table | not-required: database-only evidence | Done |
| PROJECT-PLATFORM-S04-M1-T04 | registry is the only accepted type/config capability source | registry descriptors and catalog DTO | unit tests reject `currency` and verify object config schema | not-required: pure registry contract | Done |
| PROJECT-PLATFORM-S04-M1-T05 | permanent identity, lifecycle and system protection have stable failures | `WorkItemFieldModels`, definition service and V064 trigger | domain/API cases cover transitions, terminal retire and `system_field_protected` | real: isolated API executes lifecycle on persisted fields | Done |
| PROJECT-PLATFORM-S04-M1-T06 | equal empty semantics hash equally and undeclared config is rejected | `WorkItemFieldConfigCanonicalizer` | null/empty equality plus array/non-empty rejection tests pass | not-required: serialization is tested directly | Done |
| PROJECT-PLATFORM-S04-M1-T07 | reads and relationships cannot cross workspace/space/type | scoped repository predicates and composite foreign keys | API integration rejects field id under a second space/type; schema verifies ownership FKs | real: role flow reads only the owning type URL | Done |
| PROJECT-PLATFORM-S04-M1-T08 | write commands are atomic, idempotent and optimistic | configuration service, command repository and aggregate versions | replay, simultaneous same-key contention, stale update and failed second-item reorder rollback assertions pass | real: isolated API performs full write/lifecycle chain with request ids | Done |
| PROJECT-PLATFORM-S04-M1-T09 | owner/admin configure; other identities follow minimum disclosure | controller DTOs, manager checks and action policy | MockMvc covers six identity categories and OpenAPI paths | real: owner/admin 200, member/guest 403, outsider/governor 404 | Done |
| PROJECT-PLATFORM-S04-M1-T10 | every successful write is correlated and replay does not duplicate effects | transactional audit repository, domain event repository and command receipt | audit/event set, single replay audit and failed-reorder zero-side-effect assertions pass | real: request replay returns one field id against real persistence | Done |
| PROJECT-PLATFORM-S04-M1-T11 | focused domain/schema/API/permission/concurrency evidence is reproducible | five backend test classes and isolated E2E spec | targeted Maven, ESLint/spec discovery and formal stage finish gate pass | real: `project-platform-s04-m1-work-item-fields.spec.ts` passes isolated | Done |

## Code Changes

- `server/src/main/resources/db/migration/V064__create_project_work_item_field_foundation.sql`: field aggregate, command receipts, composite constraints, indexes and guard trigger.
- `server/src/main/java/com/colla/platform/modules/project/domain/WorkItemFieldModels.java`: field identity, status and validation contract.
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemField*`: registry, canonicalizer, action policy, services, configuration models and reference guard.
- `server/src/main/java/com/colla/platform/modules/project/infrastructure/*WorkItemField*`: scoped JDBC aggregate and command persistence.
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemField*`: REST DTO and controller; type exception advice now maps field errors.
- M1 backend tests and `web/e2e/project-platform-s04-m1-work-item-fields.spec.ts`: focused automated closure.
- Current product/architecture/object-model/technology documents: S04-M1 facts and deferred boundaries.

## Validation

- Backend tests: focused field domain, registry, canonicalizer, schema and controller integration suites pass against Testcontainers PostgreSQL; 11 tests, 0 failures/errors/skips in fresh step log `.local-reports/quality-gate-20260722T142317-backend-targeted-tests.log`.
- Frontend build: no production frontend source or route changed; lint/build pass in `.local-reports/quality-gate-20260722T142317-frontend-lint.log` and `.local-reports/quality-gate-20260722T142317-frontend-build.log` while the new E2E spec also passes isolated.
- Local quality gate: `.local-reports/quality-gate-20260722T142317.md` is PASS for the formal `stage` finish with no warnings or failures.
- Browser smoke: real isolated Playwright API flow covers field CRUD/lifecycle/reorder/replay and six-role authorization without route interception.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M2-T01 | option, default and validation-rule schemas are intentionally rejected by M1 | non-blocking; prevents premature unvalidated config | current roadmap M2 |
| PROJECT-PLATFORM-S04-M3-T01 | user/date/attachment/URL/reference type-specific semantics are not implemented | non-blocking; registry capability names only | current roadmap M3 |
| PROJECT-PLATFORM-S04-M4-T01 | production field configuration UI is not delivered | non-blocking for backend foundation | current roadmap M4 |
| PROJECT-PLATFORM-S04-M5-T04 | full V001-latest upgrade rehearsal remains Stage-final work | non-blocking for M1; active schema migration is covered | current roadmap M5 |
| N/A | field definitions are not published config and no field values or work-item instances exist | non-blocking | fixed Stage boundary; S06/S07 |

## Next Steps

- Start PROJECT-PLATFORM-S04-M2 only after the M1 stage finish gate succeeds.
- Extend the empty v1 field config through registered option/default/rule schemas without weakening permanent keys, isolation or transaction semantics.
- Keep S03 published v1, legacy project/issue writes and the absent work-item instance boundary unchanged.
