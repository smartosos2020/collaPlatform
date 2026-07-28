# PROJECT-PLATFORM-S17-M1 Execution Report

## Scope

PROJECT-PLATFORM-S17-M1-T01 到 PROJECT-PLATFORM-S17-M1-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M1-T01 | non-core | static | not-required | not-required | yes | event/command/table/owner boundary audit |
| PROJECT-PLATFORM-S17-M1-T02 | non-core | unit | not-required | not-required | yes | schema v1 identity/lifecycle/bound validation |
| PROJECT-PLATFORM-S17-M1-T03 | core-system | system-real-isolated | not-required | isolated | no | PostgreSQL 16 V001-V122 fresh/repeat |
| PROJECT-PLATFORM-S17-M1-T04 | core-user | e2e-real-isolated | real | isolated | no | rule save/publish/lifecycle/exact replay |
| PROJECT-PLATFORM-S17-M1-T05 | core-system | system-real-isolated | not-required | isolated | no | public event catalog/version/field allowlist |
| PROJECT-PLATFORM-S17-M1-T06 | core-system | system-real-isolated | not-required | isolated | no | bounded declarative condition validation |
| PROJECT-PLATFORM-S17-M1-T07 | core-system | system-real-isolated | not-required | isolated | no | canonical action catalog and side-effect metadata |
| PROJECT-PLATFORM-S17-M1-T08 | core-user | e2e-real-isolated | real | isolated | no | rule editor at 1440/1366/820 |
| PROJECT-PLATFORM-S17-M1-T09 | core-user | e2e-real-isolated | real | isolated | no | offline input retention and REST recalibration |
| PROJECT-PLATFORM-S17-M1-T10 | core-user | e2e-real-isolated | real | isolated | no | six identities/cross-space/concurrency/unsafe DSL |
| PROJECT-PLATFORM-S17-M1-T11 | non-core | integration | real | isolated | no | deterministic rule/catalog/DSL/render budgets |
| PROJECT-PLATFORM-S17-M1-T12 | non-core | static | not-required | not-required | yes | roadmap/report/architecture contract gates |

## Completed Items

- T01-T02 audited S03/S07-S12/S16 public boundaries and froze AutomationRule, RuleVersion, Trigger, Condition, Action and catalogs at schema v1.
- T03-T07 delivered V122, immutable published definitions, exact command receipts, public event references, bounded declarative conditions and a controlled action catalog without script/SQL execution.
- T08-T10 delivered the typed rule editor and fresh isolated six-identity coverage for replay, publication, cross-space hiding, concurrency, unsafe DSL rejection, responsive widths and offline input.
- T11-T12 froze deterministic bounds, assigned every V122 table to project owner and synchronized current architecture contracts.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M1-T01 | Existing public events, commands, owners and forbidden dependencies are locatable | route/target/module/object/event audit and current source | architecture and owner gates | not-required: static boundary closure | Done |
| PROJECT-PLATFORM-S17-M1-T02 | Versioned identity, lifecycle, references, errors and bounds are explicit | `AutomationRuleModels` schema v1 and service validation | `AutomationRuleServiceTests` | catalogs and rule status rendered in real UI | Done |
| PROJECT-PLATFORM-S17-M1-T03 | Composite FK, indexes, immutable versions, receipts and owner complete | V122 and owner manifest | real PostgreSQL `AutomationRuleFoundationIntegrationTests` | not-required: real system database flow | Done |
| PROJECT-PLATFORM-S17-M1-T04 | Draft/save/publish/enable/disable/archive use version and exact replay | service/repository/controller transaction | unit plus isolated API E2E | real create/replay/publish/concurrent update | Done |
| PROJECT-PLATFORM-S17-M1-T05 | Only supported versioned public events and fields may trigger | static event catalog plus V122 catalog rows | service/catalog and migration assertions | real catalog returned to current members | Done |
| PROJECT-PLATFORM-S17-M1-T06 | Conditions are declarative, typed and bounded | recursive validator: 64 nodes, 8 levels, safe references/operators | deep-condition and script rejection tests | real unsafe script-shaped condition returns 400 | Done |
| PROJECT-PLATFORM-S17-M1-T07 | Actions declare owner, version and side-effect class | controlled action catalog, no executor in M1 | service catalog assertions | real action selector shows owner-backed actions | Done |
| PROJECT-PLATFORM-S17-M1-T08 | Responsive accessible rule editor | typed API and `AutomationRulesPanel` | web lint/build | real 1440/1366/820 no overflow | Done |
| PROJECT-PLATFORM-S17-M1-T09 | Offline input is retained without fake publication | local-only design note plus query invalidation/refetch | isolated Playwright | real offline draft retained; REST calibration available | Done |
| PROJECT-PLATFORM-S17-M1-T10 | Six identities, cross-space, replay, concurrency and unsafe DSL fail closed | isolated S17 runner/spec and member gate | one fresh Playwright pass | real owner/admin/member/guest/outsider/enterprise matrix | Done |
| PROJECT-PLATFORM-S17-M1-T11 | Reproducible rule, condition, action and rendering budgets | 100 rules, 64/8 conditions, 8 actions, 65,536-byte command bound | unit, migration, lint/build and smoke | real bounded list/editor DOM | Done |
| PROJECT-PLATFORM-S17-M1-T12 | Active truth and M2 boundary synchronized | roadmap plus current/module/object/event docs | planning/docs/architecture gates | not-required: static documentation closure | Done |

## Deterministic Budget

- At most 100 active rules are returned, each rule has at most 64 condition nodes, 8 condition levels and 8 actions.
- A rule command serializes to at most 65,536 characters; references and request IDs have fixed patterns and lengths.
- The isolated S17 runner uses API port 18170, Web port 15270 and database prefix `colla_s17_e2e`.
- These are repeatable local verification bounds, not production automation throughput, capacity or SLO claims.

## Code Changes

- Added V122 plus project-owned rule, immutable version, catalog, receipt and low-cardinality statistics tables.
- Added rule domain/repository/service/API with current space permission, exact replay, audit/outbox and declarative DSL validation.
- Added typed Web API/editor, offline input, responsive layout, S17 isolated runner, unit, migration and real E2E tests.

## Validation

- Backend tests: `mvn -q -f server/pom.xml -Dtest=AutomationRuleServiceTests test` — passed.
- Real database: `mvn -q -f server/pom.xml -Dtest=AutomationRuleFoundationIntegrationTests test` — PostgreSQL 16 V001-V122 fresh/repeat passed.
- Frontend build: `pnpm --dir web lint` and `pnpm --dir web build` — passed.
- Workbench: `pnpm --dir tools/workbench typecheck` — passed.
- Browser smoke: `pnpm workbench browser smoke-project-platform-s17-isolated --spec project-platform-s17-m1.spec.ts` — real isolated passed 1/1.
- Local quality gate: `.local-reports/quality-gate-20260728T052512.md` — strict stage finish passed.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None within M1 | non-blocking | Closed |

## Next Steps

- Start a separate AI work cycle at `PROJECT-PLATFORM-S17-M2-T01`; M1 does not execute business actions, create run/step history or claim worker deliveries.
