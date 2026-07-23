---
title: PROJECT-PLATFORM-S04-M2 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S04-M2
stage: PROJECT-PLATFORM-S04
updated_at: 2026-07-22
---

# PROJECT-PLATFORM-S04-M2 Execution Report

## Scope

- PROJECT-PLATFORM-S04-M2-T01 to PROJECT-PLATFORM-S04-M2-T11.
- 本里程碑交付稳定字段选项、基础类型默认值、required 与结构化校验规则，并把配置作为字段聚合的一次原子命令接入权限、幂等、乐观锁、审计和 outbox。
- 本轮不交付复杂 user/date/datetime/attachment/work-item reference 语义、生产配置 UI、布局、发布版本、工作项实例或字段值；不改写 S03 published v1。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M2-T01 | unit | not-required | not-required | no | assert versioned configuration and rule schemas reject unknown properties/versions |
| PROJECT-PLATFORM-S04-M2-T02 | integration | not-required | not-required | no | migrate V001-V065 and inspect option scope, permanent key, indexes and trigger |
| PROJECT-PLATFORM-S04-M2-T03 | integration | real | isolated | no | create/update/disable select options and reject removal or foreign value references |
| PROJECT-PLATFORM-S04-M2-T04 | unit | not-required | not-required | no | normalize text/number/boolean/select defaults and reject incompatible values |
| PROJECT-PLATFORM-S04-M2-T05 | unit | not-required | not-required | no | validate required, length, range, precision, regex, format and allowed-values matrices |
| PROJECT-PLATFORM-S04-M2-T06 | unit | not-required | not-required | no | prove canonical hash stability and reject unknown rule/schema versions |
| PROJECT-PLATFORM-S04-M2-T07 | e2e-real-isolated | real | isolated | no | race same-version configuration commands and assert failed writes leave no partial state |
| PROJECT-PLATFORM-S04-M2-T08 | e2e-real-isolated | real | isolated | no | exercise owner/admin positives and four minimum-disclosure negatives through canonical API |
| PROJECT-PLATFORM-S04-M2-T09 | e2e-real-isolated | real | isolated | no | replay one command and inspect single audit/event with non-sensitive option diff |
| PROJECT-PLATFORM-S04-M2-T10 | e2e-real-isolated | real | isolated | no | cover type compatibility, rule failures, option lifecycle, permissions, concurrency and rollback |
| PROJECT-PLATFORM-S04-M2-T11 | e2e-real-isolated | real | isolated | no | run targeted backend suites and the complete isolated configuration flow |

## Frozen Configuration Contract

| Concern | M2 contract | Limit |
| --- | --- | --- |
| configuration envelope | `schemaVersion=1`, `required`, `defaultValue`, `validationRules[]` | unknown properties and versions are rejected |
| rule identity | stable `ruleKey`, registered `kind`, `schemaVersion=1`, typed `config` | one key and one kind per field; no script, SQL or client-private rule |
| options | stable option key, name, `#RRGGBB`, sort order, active/disabled | maximum 200; existing keys cannot be omitted, deleted, moved or renamed |
| text/url | string default; length, regex and registered format rules | text formats email/uuid; URL format url; bounded pattern/length |
| number | canonical decimal default; range, precision/scale and allowed values | precision 1-38, scale within precision, min not above max |
| boolean | boolean default and allowed values | no string coercion |
| select | active same-field option key or sorted unique key array | disabled, unknown and cross-field-only keys rejected |
| user/date/datetime/attachment/reference | canonical UUID arrays, ISO date or UTC instant defaults; no M2-specific rules | candidate scope, object existence, timezone policy and invalid-reference semantics deferred to M3 |

## Completed Items

- Added V065 field-option table with composite workspace/space/type/field ownership, permanent option key, lifecycle constraints, query index and immutable identity trigger.
- Expanded the server registry with `supportsOptions`, registered validation-rule kinds and a machine-readable configuration schema/default.
- Replaced the M1 empty-config gate with versioned canonicalization for defaults and structured rules, including deterministic array/property ordering and SHA-256 hash.
- Added option domain normalization and scoped JDBC repository; no delete operation exists.
- Added one transactional field-configuration command that locks through aggregate version, updates config/options together and rejects omitted historical keys.
- Added `PUT .../fields/{fieldId}/configuration`, typed request DTOs, option projections, `configure` availableAction and stable error codes.
- Reused M1 manager policy, command receipts and transactional audit/outbox; audit metadata contains hashes and count-only option diff, not default/rule payloads.
- Added unit, PostgreSQL schema/API/concurrency and real isolated Playwright coverage.
- Reopened T04 once during final semantic review and closed it in a dedicated follow-up cycle after extending storage-kind normalization to date, datetime and UUID-array defaults; the M3 boundary remains object existence, candidate scope and timezone policy.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M2-T01 | schemas are structured, versioned and closed | canonicalizer and registry schema | unknown property/version tests | not-required: contract unit evidence | Done |
| PROJECT-PLATFORM-S04-M2-T02 | option key is permanent and scoped | V065 plus option repository | migration, constraint, trigger and delete/key-update negative assertions | not-required: database contract | Done |
| PROJECT-PLATFORM-S04-M2-T03 | select value domain is stable and operable | option normalizer and aggregate service | update/disable/removal/foreign-reference API cases | real flow creates and reads two options | Done |
| PROJECT-PLATFORM-S04-M2-T04 | defaults follow storage kind | canonicalizer default matrix | all 11 types have null/basic value behavior; string/decimal/boolean/select/date/instant/UUID-array positives and mismatch negatives | real flow persists select default | Done |
| PROJECT-PLATFORM-S04-M2-T05 | rules are bounded and compatible | six registered rule normalizers | range/precision/length/regex/format/allowed-values cases | real allowed-values rule | Done |
| PROJECT-PLATFORM-S04-M2-T06 | known v1 is deterministic and future versions fail | sorted canonical JSON/hash | reordered properties/values yield equal hash; v2 fails | real catalog advertises v1 | Done |
| PROJECT-PLATFORM-S04-M2-T07 | aggregate update is atomic and optimistic | transactional configure service | simultaneous expected-version race yields one success; invalid write preserves version/options | real stale command returns version_conflict | Done |
| PROJECT-PLATFORM-S04-M2-T08 | actions/errors/RBAC are server-owned | controller DTO, action policy, exception advice | MockMvc covers six identities and OpenAPI | real: owner/admin 200, member/guest 403, outsider/governor 404 | Done |
| PROJECT-PLATFORM-S04-M2-T09 | side effects are idempotent and non-sensitive | command receipt and count-only option diff | replay leaves one audit/event; metadata excludes defaultValue | real: immediate replay keeps version | Done |
| PROJECT-PLATFORM-S04-M2-T10 | compatibility/lifecycle/concurrency/rollback are reproducible | seven focused backend classes | 19 targeted tests pass on PostgreSQL | real: isolated API flow passes without interception | Done |
| PROJECT-PLATFORM-S04-M2-T11 | report, schema, API and gates agree | this report and current facts | targeted backend, ESLint, planning and formal stage gate | real: M2 Playwright spec passes isolated | Done |

## Code Changes

- `V065__add_work_item_field_options.sql`: option persistence, composite constraints, index and immutable identity trigger.
- `WorkItemFieldOptionModels`, `WorkItemFieldOptionRepository` and JDBC implementation: option domain and scoped persistence.
- `WorkItemFieldConfigCanonicalizer` and `WorkItemFieldTypeRegistry`: versioned defaults/rules, type matrices and deterministic hash.
- `WorkItemFieldConfigurationService`: atomic configure command, lifecycle validation, option diff, idempotency and optimistic concurrency.
- Field API DTO/controller/action policy/error advice: typed configuration endpoint, option projection and stable actions/errors.
- Seven focused backend test classes and `project-platform-s04-m2-field-rules.spec.ts`: M1 regression plus M2 unit/schema/API/real-browser closure.

## Validation

- Backend tests: 19 tests, 0 failures/errors/skips across field domain, option domain, registry, canonicalizer, schema, M1 API regression and M2 API suites; final concrete log: `.local-reports/project-platform-s04-m2-final-targeted-tests.log`.
- Empty PostgreSQL test schema: Flyway validates and applies V001-V065; option constraints/index/trigger are inspected in `.local-reports/project-platform-s04-m2-final-targeted-tests.log`.
- Browser smoke: `project-platform-s04-m2-field-rules.spec.ts` passes one real isolated owner/admin/member/guest/outsider/governor flow without route interception; latest concrete log: `.local-reports/work-cycle-browser-20260722T155856.log`.
- Frontend build: lint/build, chunk budget and route lazy-loading pass; concrete logs: `.local-reports/quality-gate-20260722T155141-frontend-lint.log` and `.local-reports/quality-gate-20260722T155141-frontend-build.log`.
- Local quality gate: `.local-reports/quality-gate-20260722T155141.md` is the complete M2 PASS; `.local-reports/quality-gate-20260722T155925.md` is the T04 semantic-review closure PASS. Both contain no warnings or failures.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M3-T01 | complex user/date/datetime/attachment/reference candidate scope, timezone policy, object existence and invalid-reference semantics remain unimplemented | non-blocking for M2 storage-kind normalization | current roadmap M3 |
| PROJECT-PLATFORM-S04-M4-T01 | production field configuration UI is not delivered | non-blocking for backend/API milestone | current roadmap M4 |
| PROJECT-PLATFORM-S04-M5-T04 | route-final upgrade rehearsal and full historical suite remain Stage-final | non-blocking for focused M2 closure | current roadmap M5 |
| N/A | configuration is not published and no field values/work-item instances exist | non-blocking | fixed Stage boundary; S06/S07 |

## Next Steps

- Start PROJECT-PLATFORM-S04-M3 at its complex-type contract task.
- Reuse the M2 envelope, aggregate update and stable error model; extend only registered type-specific semantics.
- Keep S03 published v1 and absent work-item instance boundary unchanged until S06/S07.
