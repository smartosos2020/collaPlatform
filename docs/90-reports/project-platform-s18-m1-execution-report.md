# PROJECT-PLATFORM-S18-M1 Execution Report

## Scope

PROJECT-PLATFORM-S18-M1-T01 到 PROJECT-PLATFORM-S18-M1-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M1-T01 | non-core | static | not-required | not-required | yes | S02/S06/S07/S10/S11/S17 API/table/owner audit |
| PROJECT-PLATFORM-S18-M1-T02 | non-core | unit | not-required | not-required | yes | schema v1 grant/version/scope/party/receipt validation |
| PROJECT-PLATFORM-S18-M1-T03 | core-system | system-real-isolated | not-required | isolated | no | PostgreSQL 16 V001-V127 fresh/repeat |
| PROJECT-PLATFORM-S18-M1-T04 | core-user | e2e-real-isolated | real | isolated | no | draft/request/dual-confirm/pause/revoke/replay |
| PROJECT-PLATFORM-S18-M1-T05 | core-system | system-real-isolated | not-required | isolated | no | published type/version and bounded minimal scope |
| PROJECT-PLATFORM-S18-M1-T06 | core-user | e2e-real-isolated | real | isolated | no | dual-space current manager/member recalibration |
| PROJECT-PLATFORM-S18-M1-T07 | core-system | system-real-isolated | not-required | isolated | no | revision/revoke/archive history retention |
| PROJECT-PLATFORM-S18-M1-T08 | core-user | e2e-real-isolated | real | isolated | no | grant UI at 1440/1366/820 |
| PROJECT-PLATFORM-S18-M1-T09 | core-user | e2e-real-isolated | real | isolated | no | offline input guard and online/focus REST recalibration |
| PROJECT-PLATFORM-S18-M1-T10 | core-user | e2e-real-isolated | real | isolated | no | six identities/two spaces/replay/minimal disclosure |
| PROJECT-PLATFORM-S18-M1-T11 | non-core | integration | real | isolated | no | deterministic grant/scope/history/render budgets |
| PROJECT-PLATFORM-S18-M1-T12 | non-core | static | not-required | not-required | yes | roadmap/report/target/current/module/object/event gates |

## Completed Items

- T01-T02 audited the existing space, published snapshot, WorkItem, relation, permission and exact-receipt boundaries, then froze schema v1 grant/version/scope/party/receipt contracts.
- T03-T07 delivered V127, immutable scope versions, dual confirmation, exact command replay, current membership/role/identity recalibration, and retained revoke/archive history.
- T08-T10 delivered the responsive grant panel and fresh isolated two-space/six-identity coverage for replay, single-party non-activation, current visibility, minimal errors and offline calibration.
- T11-T12 froze deterministic local bounds, registered every V127 table to project owner, added S18 isolated runner support and synchronized active architecture truth.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S18-M1-T01 | Existing APIs, tables, owners, callers, migrations and forbidden dependencies are locatable | route/target/current/module/object/event audit plus current source | checkpoint architecture/owner gates passed | not-required: static boundary closure | Done |
| PROJECT-PLATFORM-S18-M1-T02 | Versioned identity, parties, scope, lifecycle, errors, history and limits are explicit | `CrossSpaceGrantModels` and service validators | `CrossSpaceGrantServiceTests` passed | real contract exercised through API/UI | Done |
| PROJECT-PLATFORM-S18-M1-T03 | Composite FK, immutable versions, confirmation history, receipts, indexes, cleanup and owner complete | V127, cleanup and table-owner manifest | real PostgreSQL `CrossSpaceGrantFoundationIntegrationTests` passed V001-V127/repeat 0 | not-required: real system database flow | Done |
| PROJECT-PLATFORM-S18-M1-T04 | Draft/request/dual-confirm/pause/resume/revoke/archive use expected version and exact replay | service/repository/controller transaction | unit plus isolated API E2E | real create/replay/request/source-confirm/target-confirm flow | Done |
| PROJECT-PLATFORM-S18-M1-T05 | Only explicit bounded directions, operations and complete published versions are accepted | scope validator plus `PublishedSnapshotAdapter` | invalid bounds/unit and real published snapshots | real source/target project snapshots published before grant | Done |
| PROJECT-PLATFORM-S18-M1-T06 | Both spaces and confirming managers are recalibrated; grant creates no membership/ACL | membership/active-role SQL plus public `IdentityGovernance` | architecture boundary and service tests | real different source/target owners; outsider/enterprise hidden | Done |
| PROJECT-PLATFORM-S18-M1-T07 | Revision clears confirmation; revoke/archive retain owner history and stop new authorization | immutable GrantVersion and lifecycle state machine | migration/service state assertions | real active lifecycle; destructive controls rendered with reasons | Done |
| PROJECT-PLATFORM-S18-M1-T08 | Accessible responsive request/confirmation/scope/history UI | typed API and `CrossSpaceGrantsPanel` | web lint/build and isolated smoke | real 1440/1366/820 no overflow | Done |
| PROJECT-PLATFORM-S18-M1-T09 | Realtime/cache do not authorize and offline cannot fake success | online/offline/focus invalidation and disabled writes | isolated Playwright | real offline banner and disabled new-grant action | Done |
| PROJECT-PLATFORM-S18-M1-T10 | Six identities, dual spaces, replay and disclosure boundaries fail closed | isolated fixture/spec and unified 404/403 handler | fresh Playwright 1/1 passed | real: owner/admin/member/guest visible; outsider/enterprise hidden | Done |
| PROJECT-PLATFORM-S18-M1-T11 | Reproducible grant, scope, history, port and render limits | 100 grants, 32 type scopes, 100 instance scopes, 50 history rows | unit/migration/lint/build/smoke | real: bounded list and panel DOM on port 15280 | Done |
| PROJECT-PLATFORM-S18-M1-T12 | Active truth and M2 boundary are synchronized | roadmap plus target/current/module/object/event docs | planning/docs/architecture checkpoint passed | not-required: static documentation closure | Done |

## Deterministic Budget

- A response returns at most 100 grants, each scope accepts at most 32 type pairs and 100 explicit instance identities, and history returns at most 50 immutable versions.
- Directions are limited to three values and operations to five public capability keys; request IDs are at most 120 characters and grant names at most 160.
- The isolated S18 runner uses API port 18180, Web port 15280 and database prefix `colla_s18_e2e`.
- These are reproducible local validation bounds, not production cross-team scale, throughput, capacity or SLO claims.

## Code Changes

- Added V127, project-owned grant/version/receipt schema, cleanup ordering and table-owner declarations.
- Added grant domain/repository/service/API with published snapshot validation, dual-space current authorization, exact replay, audit/outbox and minimal disclosure.
- Added typed Web API/panel, offline/focus calibration, S18 isolated workbench runner, unit, migration and real Playwright coverage.

## Validation

- Backend tests: `mvn -q -f server/pom.xml -Dtest=CrossSpaceGrantServiceTests test` — passed.
- Real database: `mvn -q -f server/pom.xml -Dtest=CrossSpaceGrantFoundationIntegrationTests test` — PostgreSQL 16 V001-V127 fresh/repeat passed.
- Frontend build: `pnpm --dir web lint` and `pnpm --dir web build` — passed in the real repository workbench flow.
- Workbench: `pnpm --dir tools/workbench typecheck` and M1 checkpoint — passed.
- Browser smoke: `pnpm workbench browser smoke-project-platform-s18-isolated --spec project-platform-s18-m1.spec.ts` — real isolated passed 1/1.
- Local quality gate: `.local-reports/quality-gate-20260728T093104.md` — light checkpoint passed; stage finish report follows.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None within M1 | non-blocking | Closed |

## Next Steps

- Start a separate AI work cycle at `PROJECT-PLATFORM-S18-M2-T01`; M1 does not create relation edges, execute sync rules or aggregate S19 metrics.
