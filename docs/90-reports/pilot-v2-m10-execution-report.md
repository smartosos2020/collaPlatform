---
title: PILOT-V2-M10 Execution Report
status: completed
milestone: PILOT-V2-M10
updated_at: 2026-07-15
---

# PILOT-V2-M10 Execution Report

## Scope

- PILOT-V2-M10-T01 到 PILOT-V2-M10-T09

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M10-T01 | contract/static | none | repository | no | Validate versioned round/persona/module/metric contract |
| PILOT-V2-M10-T02 | stage/static | none | repository | no | Validate real-API/UI runner, evidence schema, isolated-project guard and parser |
| PILOT-V2-M10-T03 | real browser + API | real | isolated | no | Five personas authenticate; six baseline module scenarios execute |
| PILOT-V2-M10-T04 | real API | real | isolated | Duplicate client message and repeated module reads remain idempotent |
| PILOT-V2-M10-T05 | real API + infrastructure | real | isolated | Invalid/forbidden access is rejected; server restarts and all modules recover |
| PILOT-V2-M10-T06 | evidence recomputation | real | isolated | Metrics are recomputed from first-attempt phase JSON with failure classes |
| PILOT-V2-M10-T07 | evidence recomputation | real | isolated | Each round records CONTINUE/STOP from P0/P1 and stop conditions |
| PILOT-V2-M10-T08 | operations drill | real | isolated | Final counts, backup integrity and independent restore match |
| PILOT-V2-M10-T09 | stage | real | isolated | Report contains matrices, metrics, failures, limitations and M11 input |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M10-T01 | Done | `deploy/pilot-v2/m10-simulation-contract.json`; contract checker PASS |
| PILOT-V2-M10-T02 | Done | `scripts/pilot-v2-m10-simulation-run.ps1`, `web/e2e/pilot-v2-m10-simulation.spec.ts`; parser and frontend lint PASS |
| PILOT-V2-M10-T03 | Done | Official `baseline.json`: 5/5 API and 5/5 real UI login, six module writes PASS |
| PILOT-V2-M10-T04 | Done | Official `retry.json`: duplicate IM client ID resolves to one message; five repeated reads are stable |
| PILOT-V2-M10-T05 | Done | Official `fault.json`, `service-restart.json`, `recovery.json`: all boundaries and recovery PASS |
| PILOT-V2-M10-T06 | Done | `summary.json`: all nine recomputed metrics pass; product and harness failures remain separate |
| PILOT-V2-M10-T07 | Done | All three round decisions are `CONTINUE`; no P0/P1 or stop condition triggered |
| PILOT-V2-M10-T08 | Done | Backup `20260715-110943`, restore drill `20260715-111058`, restore recovery E2E PASS |
| PILOT-V2-M10-T09 | Done | This report records the official run, prior harness failures, limitations and M11 input |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M10-T01 | Contract fixes five personas, six modules, three rounds, nine metrics and stop conditions | Versioned JSON contract and dedicated validator | `pilot-v2-m10-contract-20260715-105748.json`: PASS | Not required for contract | Done |
| PILOT-V2-M10-T02 | Runner uses real API/UI and preserves structured first-attempt evidence | Guarded orchestration, phase E2E, state/evidence JSON and metric aggregation | PowerShell parser PASS; `pnpm --dir web lint` PASS | Real execution is T03-T05 | Done |
| PILOT-V2-M10-T03 | 5/5 login and six module baseline | Baseline phase creates traceable IM, issue, knowledge, Base, approval and search evidence | Official baseline: 16/16 steps PASS | Five personas reached real workspace; no route mock | Done |
| PILOT-V2-M10-T04 | Retry/idempotency without duplicate or state regression | Retry phase repeats client ID and stable reads | Official retry: 11/11 steps PASS | Real API, no mock | Done |
| PILOT-V2-M10-T05 | Expected fault boundaries and service restart recovery | Fault phase plus guarded Docker service restart and recovery phase | Invalid session, admin boundary, private knowledge and six recovery reads PASS | Real API/infrastructure, no mock | Done |
| PILOT-V2-M10-T06 | Objective metrics recomputable and failure classes separated | Aggregator reads immutable phase JSON | Nine official metrics PASS; prior harness failures retained separately | Derived from real phase evidence | Done |
| PILOT-V2-M10-T07 | Per-round stop decision recorded | Aggregator evaluates P0/P1 and stop conditions | Three rounds `CONTINUE`, open critical issues 0 | Derived from real phase evidence | Done |
| PILOT-V2-M10-T08 | Final consistency plus backup/restore | Application-quiesced backup and guarded independent restore | Flyway 049; key DB counts and two SHA-256 files match; restore recovery E2E PASS | Isolated source and drill projects | Done |
| PILOT-V2-M10-T09 | Complete synthetic observation report | Roadmap, product scope and this report synchronized | `SYNTHETIC-CONTINUOUS-RUN-PASS`; readiness `REHEARSAL-READY` | Official real evidence referenced | Done |

## Code Changes

- Backend: no production backend behavior changed; M10 exercised the existing V049 APIs.
- Frontend: added the real, no-route-mock M10 phased Playwright scenario and suite selector.
- Database: no migration added; isolated execution and target tests used Flyway V001-V049.
- Scripts: added contract validation plus guarded baseline/retry/fault/restart/recovery orchestration and metric aggregation.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `deploy/pilot-v2/m10-simulation-contract.json` | Added | Freeze the synthetic evidence contract and prohibit human-outcome inference. |
| `docs/02-roadmap/current-roadmap.md` | Updated | Record all M10 tasks complete and move the current execution entry to M11. |
| `docs/90-reports/pilot-v2-m10-execution-report.md` | Updated | Record verification contract and incremental evidence. |
| `docs/00-product/current-product-scope.md` | Updated | Record M10 synthetic completion without claiming human outcomes. |

## Validation

- Backend tests: 23/23 targeted tests PASS across project, knowledge, Base, approval, IM, search and permission decision; the target Testcontainers database applied V001-V049.
- Frontend build: lint and production build PASS; chunk budget and route lazy-loading checks PASS.
- Local quality gate: checkpoint `quality-gate-20260715-111706.md` and final stage `quality-gate-20260715-111838.md` PASS.
- Browser smoke: official run `m10-20260715T030841Z` has baseline/retry/fault/recovery 4/4 PASS; restore target recovery 1/1 PASS; fresh work-cycle baseline `work-cycle-browser-20260715-111826.log` PASS.

## Official Metrics

| Metric | Value | Threshold | Decision |
| --- | ---: | ---: | --- |
| `personaAuthenticationRate` | 100% | 100% | PASS |
| `scenarioAttemptRate` | 100% | 100% | PASS |
| `scenarioSuccessRate` | 100% | >= 95% | PASS |
| `moduleRoundCoverage` | 100% each round | 100% each round | PASS |
| `unexpectedAuthorizationCount` | 0 | 0 | PASS |
| `dataConsistencyViolationCount` | 0 | 0 | PASS |
| `openCriticalIssueCount` | 0 | 0 | PASS |
| `faultRecoveryRate` | 100% | 100% | PASS |
| `automationFlakeRate` | 0% in frozen official run | <= 5% | PASS |

The official metric denominator is the first execution after the contract and runner were frozen. Earlier development attempts are retained below and are not rewritten as product failures.

## Preserved Harness Failures

| Evidence | Classification | Finding | Disposition |
| --- | --- | --- | --- |
| `m10-20260715T030208Z` | automation harness | Playwright config was not explicitly selected; empty reindex response was parsed as JSON | Fixed runner config and response assertion |
| `m10-20260715T030225Z` | automation environment | Diagnostic invocation omitted the synthetic password | Input supplied; no business mutation occurred |
| `m10-20260715T030352Z` | scenario design | Search was checked only as an owner who was not a member of the created private resources | Search now aggregates each authorized scenario owner |
| `m10-20260715T030608Z` | scenario/runner harness | Invalid token safely returned 403 rather than the overly narrow 401 expectation; log output polluted phase return values | Accept secure 401/403 denial and isolate log output |

No preserved attempt identified a confirmed product defect. The local evidence directories remain ignored and are not release artifacts.

## Backup And Restore

- Backup: `.local-backups/m10-simulation/20260715-110943`, `application-quiesced`, Flyway `049`.
- Database counts: users 6, workspaces 1, projects 6, bases 6, knowledge spaces 6, audit logs 137.
- Backup files: PostgreSQL 532738 bytes and MinIO 6771 bytes; both SHA-256 checks passed.
- Independent target: `colla-platform-drill-m10r1`; restore counts and MinIO object count matched the manifest.
- Post-restore: health checks PASS and the six-module recovery Playwright phase PASS.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PILOT-V2-M10 | Evidence is synthetic and cannot measure satisfaction, adoption, learning cost or natural collaboration behavior | Blocks claims of real-user readiness, but not M10 engineering completion | A future real small-team pilot must collect human evidence |
| PILOT-V2-M11 | Full historical backend suite and V001-V049 empty-database migration have not run in M10 by route policy | M10 stage completion only | Execute at M11 route-final |

## Next Steps

- Enter PILOT-V2-M11 for defect deduplication, route-final regression, V001-V049 migration verification and engineering Go/No-Go.
- Do not present `SYNTHETIC-CONTINUOUS-RUN-PASS` as real participant acceptance or production release approval.
