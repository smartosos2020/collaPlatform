---
title: PROJECT-PLATFORM-S04-M3 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S04-M3
stage: PROJECT-PLATFORM-S04
updated_at: 2026-07-23
---

# PROJECT-PLATFORM-S04-M3 Execution Report

## Scope

- PROJECT-PLATFORM-S04-M3-T01 to PROJECT-PLATFORM-S04-M3-T11.
- 本里程碑在 M2 字段配置 envelope 上交付 user、date、datetime、URL、attachment 和 work_item_reference 的类型专属配置、规范值与跨模块引用校验。
- 本轮不新增数据库表，不交付配置 UI、布局、发布版本、工作项实例、字段值、反向关系或 legacy 双写。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M3-T01 | unit | not-required | not-required | no | assert stable value/type-config schemas, reference policy and unavailable-without-snapshot contract |
| PROJECT-PLATFORM-S04-M3-T02 | integration | not-required | not-required | no | validate active member/department/user-group scopes and reject missing, disabled and foreign identities |
| PROJECT-PLATFORM-S04-M3-T03 | unit | not-required | not-required | no | normalize date and UTC datetime precision/range/default strategies across valid and invalid timezones |
| PROJECT-PLATFORM-S04-M3-T04 | unit | not-required | not-required | no | normalize safe absolute URLs and reject dangerous schemes, credentials, controls and bounds |
| PROJECT-PLATFORM-S04-M3-T05 | integration | not-required | not-required | no | validate file count, MIME, size, workspace and resolver access without snapshot persistence |
| PROJECT-PLATFORM-S04-M3-T06 | e2e-real-isolated | real | isolated | no | create two same-space target types, configure one reference field and prove instance references remain unavailable before S07 |
| PROJECT-PLATFORM-S04-M3-T07 | unit | not-required | not-required | no | inspect operators, filter/sort/index capability and non-sortable reference types |
| PROJECT-PLATFORM-S04-M3-T08 | e2e-real-isolated | real | isolated | no | read catalog schemas and configure canonical URL/reference payloads through production APIs |
| PROJECT-PLATFORM-S04-M3-T09 | e2e-real-isolated | real | isolated | no | exercise owner/admin positives, four minimum-disclosure negatives, replay and redacted audit |
| PROJECT-PLATFORM-S04-M3-T10 | e2e-real-isolated | real | isolated | no | run malicious URL, invalid identity/file/type, timezone and cross-boundary negative matrices |
| PROJECT-PLATFORM-S04-M3-T11 | e2e-real-isolated | real | isolated | no | run targeted backend suites and one cleaned-up real isolated six-identity complex configuration flow |

## Frozen Complex Field Contract

| Type | Canonical value | Type configuration | Security and boundary |
| --- | --- | --- | --- |
| user | unique UUID array or null | allowed member/department/user_group subjects, selection scope, 1-100 selections | active same-workspace identities only; scoped defaults must resolve inside expanded scope |
| date | ISO-8601 calendar date or null | ISO-8601 calendar, day precision, none/today default, optional min/max | no timezone coercion; min must not exceed max |
| datetime | UTC instant or null | UTC storage, valid display ZoneId, minute/second/millisecond precision, none/now default, optional min/max | incoming offsets normalize to UTC and configured precision |
| url | normalized absolute URI or null | unique http/https scheme list, max length 1-4096, credentials always false | host required; credentials, controls, backslashes and unsupported schemes rejected |
| attachment | unique file UUID array or null | 1-100 files, MIME allowlist/wildcards, positive size up to 10 GiB | completed same-workspace file plus current platform-object access; no key/name/content-type snapshot |
| work_item_reference | empty UUID array or null before S07 | same-space target type UUIDs, 1-100 references, outbound direction, deferred relation capability | retired/foreign targets rejected; no instance default, relation or resolver before S07 |

All configurations are closed schema-version-1 objects. Canonical property/array ordering feeds the existing SHA-256 configuration hash, aggregate version, request-id receipt, audit and outbox transaction.

## Completed Items

- Expanded the 11-type registry with stable value schemas, type-config schemas, reference ownership and invalid-reference policies.
- Added canonical `typeConfig` to the existing configuration envelope while retaining backward-compatible server defaults for M1/M2 callers.
- Implemented bounded user scope, date/datetime timezone and precision, URL normalization, attachment constraints and deferred work-item-reference semantics.
- Added a cross-module validator that resolves current identity, organization, user-group, file/platform-object and work-item-type facts at write time.
- Kept complex values as IDs or scalar facts; no personal, file, target-title or credential snapshot is persisted or emitted in audit metadata.
- Reused the existing owner/admin action policy, request-id replay, optimistic aggregate version, transaction, audit and outbox path.
- Added focused unit, PostgreSQL/Flyway integration, authorization, malicious-input and real isolated Playwright coverage.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M3-T01 | all complex types have stable JSON and safe invalid-reference semantics | registry value/type-config/reference descriptors and canonical envelope | registry and canonicalizer schema/unknown-property cases | not-required: contract-level unit evidence | Done |
| PROJECT-PLATFORM-S04-M3-T02 | user scope limits subject types/count and hides invalid identities | user type-config normalizer and identity/org/group resolver | active department/group expansion plus disabled, missing and foreign negatives | not-required: no production configuration UI in M3 | Done |
| PROJECT-PLATFORM-S04-M3-T03 | date and datetime semantics are explicit and bounded | temporal canonicalizers with UTC, ZoneId, precision, range and relative defaults | valid offset normalization and invalid timezone/range/strategy cases | not-required: API contract is covered by unit/integration tests | Done |
| PROJECT-PLATFORM-S04-M3-T04 | dangerous or credential-bearing URLs cannot persist | safe URI parser, normalization and protocol/length controls | scheme, credentials, controls, host and length positive/negative cases | real: safe URL normalizes and credential URL returns 400 | Done |
| PROJECT-PLATFORM-S04-M3-T05 | attachment configuration is bounded and stores only canonical file IDs | attachment normalizer and file/platform-object reference validator | completed/accessible positive plus inaccessible, size and MIME negatives | not-required: object-storage metadata path is covered by integration tests | Done |
| PROJECT-PLATFORM-S04-M3-T06 | reference config names target types but creates no instances | same-space type resolver and deferred outbound-only contract | active/retired/foreign type and non-empty instance-default negatives | real: same-space target type config persists with empty default | Done |
| PROJECT-PLATFORM-S04-M3-T07 | operators and capabilities are server-owned | expanded registry descriptor | all 11 stable-order descriptors and non-sortable complex types asserted | real: catalog returns URL/file/reference capability metadata | Done |
| PROJECT-PLATFORM-S04-M3-T08 | DTO/API serialize canonical complex config without snapshots | configure DTO accepts typeConfig; response uses canonical field projection | MockMvc covers every complex type and OpenAPI | real: catalog, URL and reference payloads round-trip through canonical APIs | Done |
| PROJECT-PLATFORM-S04-M3-T09 | permissions, replay and audit remain minimal and redacted | existing action policy/receipt/audit/outbox plus reference validator | six identities, single replay effect and hash-only audit assertions | real: owner/admin 200, member/guest 403, outsider/governor 404, replay stable | Done |
| PROJECT-PLATFORM-S04-M3-T10 | edge, malicious and cross-boundary matrices are reproducible | six focused canonical/reference paths | 29 targeted tests pass with V001-V065 on fresh PostgreSQL | real: isolated malicious URL and four role negatives pass | Done |
| PROJECT-PLATFORM-S04-M3-T11 | docs, OpenAPI, code and quality evidence agree | product/architecture/object/technology/roadmap updates and this report | targeted backend, ESLint, TypeScript and production build pass | real: fresh isolated Playwright flow passes and cleans all fixtures | Done |

## Code Changes

- `WorkItemFieldTypeRegistry`: machine-readable value/type-config schemas, canonical defaults, reference and invalid-reference policies.
- `WorkItemFieldConfigCanonicalizer`: complex type configuration/value normalization and bounded security checks.
- `WorkItemFieldComplexReferenceValidator`: current-fact validation across identity, organization, user group, file/platform object and work-item type modules.
- `WorkItemFieldConfigurationService`, API DTO/controller and exception advice: canonical complex configuration on create/update/configure with stable HTTP errors.
- `WorkItemFieldComplexConfigCanonicalizerTests` and `WorkItemFieldComplexTypesControllerIntegrationTests`: unit, PostgreSQL/Flyway, OpenAPI, RBAC, audit and malicious-input coverage.
- `project-platform-s04-m3-complex-fields.spec.ts`: real isolated six-identity complex configuration flow with dynamic cleanup.

## Validation

- Backend tests: 29 tests, 0 failures/errors/skips; includes M1/M2 regression, M3 unit/API/reference suites and V001-V065 fresh PostgreSQL migration. Evidence: `quality-gate-20260723T151303-backend-targeted-tests.log`.
- Frontend build: ESLint passed in `quality-gate-20260723T151303-frontend-lint.log`; TypeScript and Vite production build passed in `quality-gate-20260723T151303-frontend-build.log`.
- Local quality gate: stage profile passed with no warnings or failures; final report `quality-gate-20260723T151303.md`.
- Browser smoke: real isolated `project-platform-s04-m3-complex-fields.spec.ts` passed 1/1 with no route interception; evidence `work-cycle-browser-20260723T151303.log`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M4-T01 | no production field configuration client or UI exists | non-blocking for M3 API/domain scope | current roadmap M4 |
| PROJECT-PLATFORM-S04-M5-T04 | upgrade rehearsal, performance recheck and full historical suite remain Stage-final | non-blocking for focused M3 closure | current roadmap M5 |
| N/A | S04 field graph is not materialized into a new immutable type version | non-blocking | Program S06 |
| N/A | no work-item instances, values, instance references or `work_item` resolver exist | non-blocking | Program S07 |

## Next Steps

- Start PROJECT-PLATFORM-S04-M4 at its typed API client and query-key task.
- Build the owner/admin field configuration UI strictly from registry schemas, capabilities and `availableActions`.
- Preserve the M3 no-snapshot and minimum-disclosure rules in selectors, errors and type-specific configuration panels.
