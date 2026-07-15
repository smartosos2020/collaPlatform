---
title: PILOT-V2-M11 Execution Report
status: completed
milestone: PILOT-V2-M11
updated_at: 2026-07-15
---

# PILOT-V2-M11 Execution Report

## Scope

- PILOT-V2-M11-T01 到 PILOT-V2-M11-T08

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M11-T01 | static | not-required | not-required | No | M10 evidence, issue register and current worktree are reconciled into a deduplicated defect ledger |
| PILOT-V2-M11-T02 | e2e-real-isolated | real | isolated | No | P0/P1 are zero; any required P2 has a verified disposition and no unresolved core-flow blocker |
| PILOT-V2-M11-T03 | e2e-real-isolated | real | isolated | No | Every fix-affected module has a passing targeted test and real browser path |
| PILOT-V2-M11-T04 | e2e-real-isolated | real | isolated | No | Knowledge, permission, search and notification consistency checks pass with no unexplained authorization or data violation |
| PILOT-V2-M11-T05 | integration | not-required | not-required | No | Full backend test suite, package, and empty-database Flyway V001-V049 migration pass |
| PILOT-V2-M11-T06 | e2e-real-isolated | real | isolated | No | Frontend lint/build pass and the real route-final browser suite covers user and admin entry points |
| PILOT-V2-M11-T07 | e2e-real-isolated | real | isolated | No | Fresh backup restores into an isolated project and release rollback/health checks complete with evidence |
| PILOT-V2-M11-T08 | static | not-required | not-required | No | Engineering Go/No-Go is based on the completed M11 evidence and explicitly separates synthetic from human evidence |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M11-T01 | Done | M10 official report and `.local-reports/audit-snapshot-20260715-112439.md` reconciled into the defect ledger below |
| PILOT-V2-M11-T02 | Done | One P2 route metadata defect fixed; final ledger has 0 open P0/P1 and 0 open required P2 |
| PILOT-V2-M11-T03 | Done | Full backend suite, focused consistency suite, frontend checks and real route-final browser run passed |
| PILOT-V2-M11-T04 | Done | Knowledge consistency report plus focused permission/search/notification/migration tests passed |
| PILOT-V2-M11-T05 | Done | Full `mvn test`, package and empty route database Flyway V001-V049 application passed |
| PILOT-V2-M11-T06 | Done | Frontend lint/build and isolated real route-final browser suite passed |
| PILOT-V2-M11-T07 | Done | Fresh backup, isolated restore, post-restore browser route closure, rollback and operations contract passed |
| PILOT-V2-M11-T08 | Done | Engineering decision recorded below; human evidence remains explicitly out of scope for synthetic runs |

## Defect Ledger

| ID | Module | Root cause | Severity | Disposition |
| --- | --- | --- | --- | --- |
| M10-H1 | Browser evidence | Playwright suite selection and empty reindex response handling in an early harness run | P2 harness | Fixed before official M10 run; not a product defect |
| M10-H2 | Synthetic pilot | Early harness omitted the synthetic password environment variable | P2 harness | Fixed in runner contract; not a product defect |
| M10-H3 | Search authorization | Early harness assumed one narrow denial status for an authorized search scenario | P2 harness | Fixed to assert the permission contract; not a product defect |
| M10-H4 | Auth evidence pipeline | Early invalid-token run conflated secure 403 behavior with a 401-only assertion and mixed log output | P2 harness | Fixed to accept secure denial and isolate output; not a product defect |
| M11-UI-001 | Admin navigation | `/admin/system-settings` had a router entry but no matching `adminPages` metadata, so the page bar defaulted to 企业概览 | P2 | Added the missing metadata entry and reran every admin route in the real isolated browser suite; closed |

## Engineering Decision

- Decision: `ENGINEERING-GO / REAL-PILOT-NOT-AUTHORIZED`.
- Basis: no open P0/P1, no open required P2, full backend 75/75 pass, focused consistency 10/10 pass, Flyway V001-V049 applied from an empty route database, frontend lint/build pass, real route closure pass, backup/restore/rollback evidence pass.
- Boundary: this is an engineering release-readiness decision. It does not claim human satisfaction, adoption, learning cost, natural collaboration efficiency or willingness to adopt.
- Next controlled step: recruit real participants and run the separately governed small pilot before expanding production use.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M11-T01 | Deduplicated issue ledger identifies module, root cause, severity and disposition for every M10 finding | M10 execution report, M11 audit snapshot and Defect Ledger | M10 official summary and current worktree audit | Not required | Done |
| PILOT-V2-M11-T02 | No open P0/P1 and required P2 items have an accepted disposition | Defect Ledger; `web/src/app/navigation/adminConsoleNav.tsx` fix | Full and focused tests passed | Real isolated route-final pass | Done |
| PILOT-V2-M11-T03 | Fix-affected modules regress without cross-module breakage | Admin navigation metadata fix and route scope | 75/75 full backend; 10/10 focused consistency; frontend lint/build | Real route-final pass on source and restored isolated stacks | Done |
| PILOT-V2-M11-T04 | Knowledge, permission, search and notification data/authorization results are consistent | `.local-reports/knowledge-consistency-20260715-113637.md` | Focused 10-test Maven set passed; all zero-count checks passed | Restored isolated real route pass | Done |
| PILOT-V2-M11-T05 | Full backend tests, package and V001-V049 empty-schema migration pass | Route DB created empty and migrated by current server image | `mvn test` 75/75; `mvn -DskipTests package` success; Flyway 49 migrations ending at v049 confirmed by server log | Not required | Done |
| PILOT-V2-M11-T06 | Frontend checks and all declared user/admin route entry points pass | `web/e2e/pilot-v2-m11-route-final.spec.ts` | `pnpm web:lint` and `pnpm web:build` pass | Real isolated route-final pass | Done |
| PILOT-V2-M11-T07 | Backup, restore, health and rollback evidence is complete | `.local-backups/m11-final/20260715-114616`; restore/rollback reports | Operations contract 8/8 pass; hashes and health checks pass | Real restored drill route-final pass | Done |
| PILOT-V2-M11-T08 | Engineering decision and human-evidence limitation are explicit | Engineering Decision section and Remaining Gaps | Route-final quality gate and document contract checks | Not required | Done |

## Code Changes

- Backend: No product backend changes were required at M11; full route-final verification remains required.
- Frontend: Added a real, no-session-injection route-final Playwright suite.
- Database: No schema change planned; V001-V049 empty-database migration is a route-final acceptance check.
- Scripts: Existing route-final, quality-gate and operations contracts are used without bypassing them.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/90-reports/pilot-v2-m11-execution-report.md` | Activated and replaced placeholders with concrete verification and acceptance contracts | Make the M11 run auditable before execution |

## Validation

- Backend tests: `mvn -B test` passed 75/75; focused consistency set passed 10/10; `mvn -B -DskipTests package` passed.
- Frontend build: `pnpm web:lint` and `pnpm web:build` passed; route-final browser spec passed on source and restored isolated stacks.
- Local quality gate: Final route-final quality gate passed; `.local-reports/quality-gate-20260715-121957.md` records the full backend suite, package, frontend lint/build, security, Flyway order, naming and documentation/work-cycle contract checks.
- Browser smoke: `pilot-v2-m11-route-final.spec.ts` passed with real login and no session injection, request interception or route mocks.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | No human participants are available in this environment, so satisfaction, adoption and natural collaboration cannot be measured | non-blocking | Human satisfaction, adoption and natural collaboration evidence must be collected in a separate real-participant pilot |

## Next Steps

- Engineering Go is complete; recruit real participants before authorizing a human pilot.
