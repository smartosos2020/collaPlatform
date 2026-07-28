# PROJECT-PLATFORM-S19-M3 Execution Report

## Scope
PROJECT-PLATFORM-S19-M3-T01 到 PROJECT-PLATFORM-S19-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M3-T01 | non-core | static | not-required | not-required | yes | M1-M2 and S15-S16 public-contract/private-table audit |
| PROJECT-PLATFORM-S19-M3-T02 | non-core | unit | not-required | not-required | yes | versioned policy/signal/evidence/action contracts and limits |
| PROJECT-PLATFORM-S19-M3-T03 | core-system | system-real-isolated | not-required | isolated | no | PostgreSQL 16 V001-V133 fresh/repeat and immutable schema |
| PROJECT-PLATFORM-S19-M3-T04 | core-user | e2e-real-isolated | real | isolated | no | overdue milestone produces currently authorized source evidence |
| PROJECT-PLATFORM-S19-M3-T05 | core-user | e2e-real-isolated | real | isolated | no | bounded stagnation/blocking evidence without hidden path disclosure |
| PROJECT-PLATFORM-S19-M3-T06 | core-user | e2e-real-isolated | real | isolated | no | restricted quality/resource aggregation without personal scoring |
| PROJECT-PLATFORM-S19-M3-T07 | core-user | e2e-real-isolated | real | isolated | no | publish, dedupe, acknowledge, close, suppress, reopen and exact receipt |
| PROJECT-PLATFORM-S19-M3-T08 | core-user | e2e-real-isolated | real | isolated | no | responsive policy/signal/evidence/action workbench |
| PROJECT-PLATFORM-S19-M3-T09 | core-user | e2e-real-isolated | real | isolated | no | REST invalidation, offline fail-closed controls and current recalibration |
| PROJECT-PLATFORM-S19-M3-T10 | core-user | e2e-real-isolated | real | isolated | no | six identities, dual spaces, replay, stale version and closure isolation |
| PROJECT-PLATFORM-S19-M3-T11 | non-core | unit | not-required | not-required | yes | fixed policy/signal/evidence/chain/fan-out and DOM bounds |
| PROJECT-PLATFORM-S19-M3-T12 | non-core | static | not-required | not-required | yes | current/target/module/object/event contract synchronization |

## Completed Items
- Added schema-v1 RiskPolicy, immutable PolicyVersion, RiskSignal, EvidenceReference and governed signal-action contracts.
- Added V133 policy/version/signal/action/command/stats schema with composite boundaries, immutable receipts, dedupe, cooldown, expiry indexes and cleanup.
- Added current-authority evidence resolution through S15 ProjectDetail and S16 ResourceCapacity public services only.
- Added server-side overdue, blocking, stagnation, quality and aggregate resource signals without personal performance or hidden utilization scoring.
- Added exact policy save/publish and signal acknowledge/close/suppress/reopen/invalidate replay, optimistic concurrency, audit and outbox.
- Added responsive risk policy/signal/evidence Web, offline fail-closed behavior and realtime/online/focus/storage REST recalibration.
- Fixed explicit PostgreSQL timestamptz binding found by the real isolated risk evaluation flow.

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S19-M3-T01 | M1-M2 and S15-S16 boundaries are traceable with no copied source authority | `MetricRiskEvidenceResolver`, service imports and architecture owner contracts | planning, architecture and table-owner checks passed | Not required; static public-service/private-table audit | Done |
| PROJECT-PLATFORM-S19-M3-T02 | identities, versions, severity, source, lifecycle, error and bounds are explicit | `MetricRiskModels` and typed Web API | risk service unit tests and TypeScript build passed | Not required; contracts verified below UI | Done |
| PROJECT-PLATFORM-S19-M3-T03 | V133 has composite boundaries, immutable versions/receipts, dedupe, expiry and cleanup | V133 migration, JDBC repository, cleanup and owner manifest | real PostgreSQL 16 V001-V133 fresh/repeat passed | Not required; real system evidence used | Done |
| PROJECT-PLATFORM-S19-M3-T04 | current visible overdue milestone produces explainable source evidence | ProjectDetail deviation adapter and `overdue` candidate | resolver/service tests and migration checks passed | Real overdue plan yielded `ProjectPlanService` identity/version evidence | Done |
| PROJECT-PLATFORM-S19-M3-T05 | stagnation/blocking propagation remains bounded and hides endpoint paths | public health-signal adapter, chainDepth 8 and fanOut 50 contracts | truncated and registered-type unit assertions passed | Real response exposed only opaque source evidence; dual-space access hidden | Done |
| PROJECT-PLATFORM-S19-M3-T06 | quality/resource evidence uses public current facts without personal evaluation | delivery blocking summary and aggregate conflict count | invalid `utilization` policy rejected before persistence | Real policy stated and UI preserved no-personal-performance boundary | Done |
| PROJECT-PLATFORM-S19-M3-T07 | publish, dedupe and lifecycle are optimistic, versioned and replayable | repository fingerprints, immutable actions/commands and service receipts | exact save replay and stale 409 browser assertions passed | Real publish, acknowledge, close and stale-version rejection | Done |
| PROJECT-PLATFORM-S19-M3-T08 | policy/signal/evidence workbench is responsive and understandable | `MetricRisksPanel`, typed API and responsive CSS | frontend production build passed | Real policy/signal/evidence at 1440/1366/820 with no overflow | Done |
| PROJECT-PLATFORM-S19-M3-T09 | invalidation/offline never authorizes or fakes evaluation/closure | realtime invalidation, online/focus/storage listeners and disabled controls | frontend build and browser offline assertions passed | Real offline warning and disabled evaluation control | Done |
| PROJECT-PLATFORM-S19-M3-T10 | six identities, cross-space and stale actions have no bypass or ghost closure | isolated Playwright scenario and current membership gates | 1/1 real isolated scenario passed | Real owner/admin/member/guest visible; outsider/enterprise/other space hidden | Done |
| PROJECT-PLATFORM-S19-M3-T11 | fixed local bounds are reproducible without production accuracy/SLO claims | 50 policies, 200 signals, 20 evidence, depth 8, fan-out 50 | unit bounds, migration indexes and three-width DOM check passed | Real three-width no-horizontal-overflow assertion | Done |
| PROJECT-PLATFORM-S19-M3-T12 | active truth matches M3 and clearly bounds M4 | current/target/module/object/event updates and roadmap checkpoint | documentation, planning and architecture checkpoint passed | Not required; documentation checkpoint | Done |

## Code Changes
- Backend: metric risk domain models, public evidence resolver, service, controller, JDBC repository and error boundary.
- Database: `V133__create_metric_risk_foundation.sql`, project-space cleanup and table-owner range `V001-V133`.
- Frontend: typed risk API, responsive policy/signal/evidence panel and realtime/offline REST calibration.
- Tests: risk service unit tests, PostgreSQL migration integration and real isolated six-identity Playwright scenario.
- Documentation: current/target architecture, module/object/event contracts, roadmap and this report.

## Validation
- Backend tests: `mvn -q -f server/pom.xml "-Dtest=MetricRiskServiceTests" test` — PASS.
- System evidence: `mvn -q -f server/pom.xml -Dtest=MetricRiskFoundationIntegrationTests test` — PASS on PostgreSQL 16.14; V001-V133 fresh/repeat, six tables and three immutable triggers.
- Frontend build: `pnpm --dir web build` — PASS.
- Browser smoke: `pnpm workbench browser smoke-project-platform-s19-isolated --spec project-platform-s19-m3.spec.ts` — PASS, 1/1 real isolated.
- Local quality gate: `.local-reports/quality-gate-20260728T173736.md` — PASS checkpoint; stage finish will refresh the final evidence.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- M3 checkpoint is complete. The next legal entry is `PROJECT-PLATFORM-S19-M4-T01`.
- M4 governance overview/config health/audit report/export remains unimplemented and must consume only M1-M3 public governance metadata.
