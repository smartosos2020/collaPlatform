# PLATFORM-SCALE-S04-M1 Execution Report

## Scope
PLATFORM-SCALE-S04-M1-T01 到 PLATFORM-SCALE-S04-M1-T11

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M1-T01 | static | not-required | not-required | No | No browser flow; code, deployment and dependency inventory is verified from the repository |
| PLATFORM-SCALE-S04-M1-T02 | unit | not-required | not-required | No | No browser flow; envelope validation and unknown-version behavior are deterministic contracts |
| PLATFORM-SCALE-S04-M1-T03 | static | not-required | not-required | No | No browser flow; architecture tests verify the transport-neutral dependency boundary |
| PLATFORM-SCALE-S04-M1-T04 | integration | not-required | not-required | No | No browser flow; PostgreSQL persistence and transport retry behavior are backend facts |
| PLATFORM-SCALE-S04-M1-T05 | unit | not-required | not-required | No | No browser flow; subscriber lifecycle, validation, dedupe and consumer isolation are component contracts |
| PLATFORM-SCALE-S04-M1-T06 | unit | not-required | not-required | No | No browser flow; local connection indexes and lifecycle races are component contracts |
| PLATFORM-SCALE-S04-M1-T07 | unit | not-required | not-required | No | No browser flow; audience selection and payload disclosure limits are component contracts |
| PLATFORM-SCALE-S04-M1-T08 | unit | not-required | not-required | No | No browser flow; bounded per-connection queues and failure isolation are component contracts |
| PLATFORM-SCALE-S04-M1-T09 | unit | not-required | not-required | No | No browser flow; metric names, outcomes and low-cardinality labels are component contracts |
| PLATFORM-SCALE-S04-M1-T10 | e2e-real-isolated | real | isolated | No | Real dual Gateway distribution, graceful stop, forced stop, recovery and single-node fallback |
| PLATFORM-SCALE-S04-M1-T11 | e2e-real-isolated | real | isolated | No | Real administrator grants a knowledge-base view right and two browser connections receive one durable notification signal each through different Gateway nodes |

## Completed Items
- `PLATFORM-SCALE-S04-M1-T01`: traced durable notification signal production, PostgreSQL storage, legacy local sender, WebSocket authentication, registry and production routing.
- `PLATFORM-SCALE-S04-M1-T02`: froze envelope v1, signal v1, environment-isolated Redis channel, 16 KiB transport budget, calibration path and unknown-version drop behavior.
- `PLATFORM-SCALE-S04-M1-T03`: introduced transport-neutral publisher and consumer ports with architecture tests preventing Worker/Gateway private coupling.
- `PLATFORM-SCALE-S04-M1-T04`: added signal persistence plus transport delivery, Redis publisher result observation and retry without rolling back the source fact.
- `PLATFORM-SCALE-S04-M1-T05`: added role-scoped Redis subscriber, bounded recent-signal dedupe and local fanout dispatch.
- `PLATFORM-SCALE-S04-M1-T06`: indexed local connections by id, user, workspace and device with race-safe registration and shutdown.
- `PLATFORM-SCALE-S04-M1-T07`: implemented user/workspace audience filtering and safe minimal payload validation.
- `PLATFORM-SCALE-S04-M1-T08`: implemented bounded serial per-connection queues, slow-client close and independent send failure cleanup.
- `PLATFORM-SCALE-S04-M1-T09`: added low-cardinality Redis, connection, queue and send outcome metrics plus role-aware Redis readiness.
- `PLATFORM-SCALE-S04-M1-T10`: deployed `event-gateway-a` and `event-gateway-b` behind non-sticky Nginx routing with explicit production channel and independent instance ids.
- `PLATFORM-SCALE-S04-M1-T11`: added unit, PostgreSQL, migration, architecture, deployment and real isolated dual-browser/dual-Gateway regression coverage.

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M1-T01 | Current signal and transport paths have one traceable inventory | `RealtimeSignalDomainEventHandler`, `WebSocketConfig`, runtime role configuration and production Compose were traced and documented | Architecture scan and role matrix tests pass | Not required; repository and runtime inventory has no browser behavior | Done |
| PLATFORM-SCALE-S04-M1-T02 | Envelope, channel and version behavior are frozen | `RealtimeSignalEnvelope`, `RealtimeProperties`, V070 and `application-*.yml` define one v1 contract | `RealtimeSignalEnvelopeTests` and migration rehearsal pass | Not required; deterministic contract validation is covered below the UI | Done |
| PLATFORM-SCALE-S04-M1-T03 | Publisher and consumer boundaries have no reverse private dependency | `RealtimeSignalPublisher` and `RealtimeSignalConsumer` use JDK/shared types only | `ModuleArchitectureTests` passes with shared reverse dependencies at zero | Not required; dependency direction is a static backend property | Done |
| PLATFORM-SCALE-S04-M1-T04 | Durable signal and Redis transport remain separate and retryable | `realtime_signals`, `RealtimeSignalTransportDomainEventHandler` and Redis publisher form a two-delivery chain | PostgreSQL persistence regression and transport Handler tests pass; JDBC timestamp defect found by real flow is covered | Not required; backend fact and retry semantics are exercised directly | Done |
| PLATFORM-SCALE-S04-M1-T05 | Every Gateway consumes once and fans only to local targets | `RedisRealtimeSignalSubscriber` validates versions, bounds payload and deduplicates before consumer dispatch | Subscriber tests cover duplicate, malformed, unknown-version and consumer failure paths | Not required; real cross-node delivery is consolidated under T10 and T11 | Done |
| PLATFORM-SCALE-S04-M1-T06 | Connection indexes and shutdown races are controlled | `WebSocketSessionRegistry` owns id/user/workspace/device indexes behind lifecycle locking | `WebSocketSessionRegistryTests` covers replacement, filtering and close-all behavior | Not required; registry correctness is deterministic and local to a Gateway | Done |
| PLATFORM-SCALE-S04-M1-T07 | Audience isolation and minimum disclosure hold | `WebSocketMessageSender` filters user targets by workspace; envelope rejects sensitive keys | Envelope, registry and sender tests cover tenant filtering and payload rejection | Not required; real recipient routing is consolidated under T11 | Done |
| PLATFORM-SCALE-S04-M1-T08 | Slow connections are bounded without blocking healthy connections | Per-connection queues, bounded executor and close reasons isolate overload and send faults | `WebSocketMessageSenderTests` covers ordering, overflow and independent failure cleanup | Not required; queue behavior is timing-controlled below the UI | Done |
| PLATFORM-SCALE-S04-M1-T09 | Low-cardinality metrics cover publish, consume and send outcomes | Micrometer counters and gauges use role/instance/outcome rather than user or workspace labels | Publisher, subscriber, sender and readiness tests pass | Not required; metric registration is verified in backend tests | Done |
| PLATFORM-SCALE-S04-M1-T10 | Dual Gateway routing supports non-sticky distribution and single-node fallback | Compose, Nginx and handshake `instanceId` expose two independent nodes without query-string logging | Deployment contracts and `dual-gateway-smoke.mjs` pass graceful stop, forced stop and restoration | real isolated WebSocket smoke observed both instance ids and both single-node fallback paths | Done |
| PLATFORM-SCALE-S04-M1-T11 | Real grant flow reaches two target connections once through different nodes | Durable notification signal is persisted, transported through Redis and delivered by each local Gateway registry | 39 targeted backend tests, 70-migration rehearsal, frontend lint/build and architecture gates pass | real isolated Playwright spec passed with `event-gateway-a` and `event-gateway-b`, one matching frame per browser | Done |

## Code Changes
- Added the shared realtime v1 contract, publisher/consumer ports, Redis adapters, health indicator and role-scoped configuration.
- Upgraded realtime persistence with V070, transport delivery, explicit JDBC timestamps and a real PostgreSQL regression test.
- Upgraded WebSocket registry, fanout queues, safe event payloads, connection readiness frames and Origin configuration.
- Added dual Event Gateway production services, non-sticky Nginx upstream, operations support and fault smoke coverage.
- Added dual-browser route-final coverage and made the workbench Playwright launcher use the local cross-platform Node CLI.
- Updated target/current architecture and the active S04 roadmap with verified M1 facts.

## Validation
- Backend tests: `quality-gate-20260724T105832.md` passed 39 targeted tests with 0 failures; `RealtimeSignalPersistenceIntegrationTests` ran against PostgreSQL 16 with V001-V070, and the post-configuration `DualGatewayDeploymentContractTests` rerun passed.
- Frontend build: `quality-gate-20260724T105832.md` passed frontend lint, production build, chunk budget and route lazy-loading.
- Local quality gate: `quality-gate-20260724T105832.md` passed toolchain, Program/Stage contract, architecture boundaries and work-cycle documentation checks.
- Browser smoke: real isolated `platform-scale-s04-m1-dual-gateway.spec.ts` passed 1/1; `dual-gateway-smoke.mjs` passed distribution, graceful/forced exit, recovery and single-node fallback.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- Start `PLATFORM-SCALE-S04-M2` and migrate IM, notification, project and permission-domain realtime side effects onto the common pipeline.
