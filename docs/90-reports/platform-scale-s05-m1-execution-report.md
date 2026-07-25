# PLATFORM-SCALE-S05-M1 Execution Report

## Scope
PLATFORM-SCALE-S05-M1-T01 到 PLATFORM-SCALE-S05-M1-T12

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1-T01 | static | not-required | not-required | No | 逐项盘点容量拓扑、运行角色、端口、资源、连接、指标、种子和负载入口，并把缺口绑定到后续任务 |
| PLATFORM-SCALE-S05-M1-T02 | static | not-required | not-required | No | 容量合同显式冻结 C1、SLO、非目标和唯一结论语义，失败后不得静默改阈值 |
| PLATFORM-SCALE-S05-M1-T03 | integration | not-required | not-required | No | 在具名宿主机执行 preflight，生成 CPU、内存、磁盘、Docker、时钟、依赖和提交 manifest，漂移会被拒绝 |
| PLATFORM-SCALE-S05-M1-T04 | integration | not-required | not-required | No | 容量 Compose 配置解析、资源公式和连接预算通过合同验证，非法预算在启动前失败 |
| PLATFORM-SCALE-S05-M1-T05 | static | not-required | not-required | No | 版本化 seed schema 覆盖成员、权限、项目、知识、通知、IM、文件和协同分布并固定校验和 |
| PLATFORM-SCALE-S05-M1-T06 | system-real-isolated | not-required | isolated | No | 对真实 disposable stack 连续完成两次干净 seed apply/verify/reapply/cleanup，标识与校验和一致且只清理具名夹具 |
| PLATFORM-SCALE-S05-M1-T07 | system-real-isolated | not-required | isolated | No | 容器化 HTTP loader 对真实登录、查询、命令、幂等和 MinIO 上传执行语义与权限断言 |
| PLATFORM-SCALE-S05-M1-T08 | system-real-isolated | not-required | isolated | No | 普通 WebSocket loader 建立真实连接并验证 fanout、sequence、duplicate、gap、重连和 REST ledger 收敛 |
| PLATFORM-SCALE-S05-M1-T09 | system-real-isolated | not-required | isolated | No | Hocuspocus/Yjs loader 以多房间多客户端跨两个 collaboration 节点编辑、断线重连并 durable reload |
| PLATFORM-SCALE-S05-M1-T10 | system-real-isolated | not-required | isolated | No | Worker loader 通过具名 capacity probe 生成持续与突发事件并核对 backlog、接管、顺序、重复副作用和 dead letter |
| PLATFORM-SCALE-S05-M1-T11 | system-real-isolated | not-required | isolated | No | 统一场景同时运行四类 loader，保留 manifest、阈值、原始时序、摘要、校验和与中止原因 |
| PLATFORM-SCALE-S05-M1-T12 | system-real-isolated | not-required | isolated | No | 从干净环境复验 seed 隔离、loader 失败识别、安全扫描、证据校验、checkpoint 和 M1 收口 |

## Completed Items
- Completed T01-T12 against isolated Compose project `colla-s05-capacity`.
- Bound the final run to source commit `b664694`, immutable host/topology/runtime
  manifests, seed evidence, scenario thresholds, raw metrics, summaries and
  SHA-256 checksums.
- Completed two clean deterministic seed initializations with an idempotent
  reapply and an intervening zero-residue cleanup for 2,458,229 expected fixture
  records.
- Completed the real four-loader scenario with zero loader errors, all configured
  thresholds passed and an untampered evidence bundle.
- Preserved failed attempts as diagnostic evidence and fixed database-time lease
  handling, local clock-step tolerance, achievable phase timing, aggregate-lane
  serialization, WebSocket fanout lanes and exact REST receipt calibration before
  accepting the final result.

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1-T01 | Capacity topology and entry-point inventory is complete | `tools/capacity` contracts, Compose topology and command entry points are versioned | Final run completed contract, topology and health preflight before mutation | Not required: this task validates CLI and Compose contracts without a browser surface | Done |
| PLATFORM-SCALE-S05-M1-T02 | C1 candidate, SLO, non-goals and conclusion vocabulary are frozen | `tools/capacity/config/contracts/s05-c1.v1.json` and scenario conclusion schema | Threshold evidence concluded `Pass`; zero-error policy remained unchanged | Not required: capacity policy is a machine-readable contract | Done |
| PLATFORM-SCALE-S05-M1-T03 | Host and dependency inputs are immutable and drift-sensitive | Preflight and provenance manifests bind host, Docker, source and dependencies | Final manifest passed provenance verification with fingerprint `a16a29cc259e2cb4d24f4433f33ce393a9cf1282278806889bfd69d77917d2c3` | Not required: environment provenance has no browser interaction | Done |
| PLATFORM-SCALE-S05-M1-T04 | Replica, resource and connection budgets fail before startup when invalid | Versioned topology and capacity Compose configuration cover every runtime role | `pnpm capacity:contract` passed declared CPU, memory and PostgreSQL, Redis and MinIO budgets | Not required: startup and budget validation is CLI-based | Done |
| PLATFORM-SCALE-S05-M1-T05 | Deterministic cross-domain seed distribution has a stable schema and checksum | Seed manifest covers identity, permissions, projects, knowledge, IM, notifications, files and collaboration | Final seed checksum `51974cedfb3415506a46c4ec7d7ae6705ac49459a33d4863cfd30f03c866ba99` represented 2,458,229 expected records | Not required: deterministic data generation is not a browser flow | Done |
| PLATFORM-SCALE-S05-M1-T06 | Two clean seed cycles, idempotent reapply and scoped cleanup are proven | Seed apply, verify, resume, clean-state and cleanup commands are guarded by fixture and run identifiers | `seed-cycle-evidence.json` passed all five checkpoints with zero workspace, relationship or business-record leakage | Not required: isolated database lifecycle is verified by machine evidence | Done |
| PLATFORM-SCALE-S05-M1-T07 | Real HTTP read, write, idempotency and file semantics run containerized | HTTP loader uses real auth, API commands and MinIO-backed file flow | Final measured phase completed 1,200 scheduled requests, 900 reads and 300 writes at 4.000 RPS with zero errors | Not required: the HTTP loader calls product APIs directly and asserts response semantics | Done |
| PLATFORM-SCALE-S05-M1-T08 | Real WebSocket fanout, sequence, reconnect and REST convergence are exact | WebSocket loader uses four deterministic aggregate lanes and bounded exact-receipt polling | Final measured phase produced 300 expected and 300 fanout events with zero missing, gaps, duplicates, calibration failures or reconnect failures | Not required: the protocol loader validates WebSocket and REST ledgers directly | Done |
| PLATFORM-SCALE-S05-M1-T09 | Real cross-node Yjs editing, reconnect and durable reload converge | Collaboration loader spans both Hocuspocus nodes with room isolation and durable reload assertions | Final measured phase ran 4 rooms, 8 clients, 600 edits, 4 reconnects and 4 durable reloads with zero convergence, reload or isolation failures | Not required: the Yjs protocol client is the required real flow | Done |
| PLATFORM-SCALE-S05-M1-T10 | Worker load proves produced, processed and side-effect identity with zero residue | Capacity probe, deterministic parallel aggregate lanes and database-time leases are isolated to capacity mode | Final measured phase produced and processed 599 events with 599 side effects, zero backlog, retries, dead letters, processing or pending records | Not required: asynchronous worker correctness is verified through API and durable ledgers | Done |
| PLATFORM-SCALE-S05-M1-T11 | Unified scenario emits complete immutable evidence and detects aborts | Runner emits run, manifest, threshold, raw metrics, summary, errors and checksum documents | Run `s05-m1-20260725-1954-final` completed in 457,611 ms; all 5,478 measured samples passed and bundle digest `92825fdd2db50da04286b52db33276370318bd58dc6c8d16117f007340e6b753` verified | Not required: evidence packaging is a non-UI runtime contract | Done |
| PLATFORM-SCALE-S05-M1-T12 | Reproducibility, isolation, negative detection and M1 closure pass | Unit, backend, dry-run and real isolated runtime paths cover the M1 closure contract | `pnpm capacity:test` passed 149 tests; final run conclusion and checksum verification both passed | Not required: M1 is a CLI and service-level capacity foundation | Done |

## Code Changes
- Added the versioned C1 capacity contract, topology, deterministic seed model,
  preflight, provenance, guarded disposable Compose stack, and containerized
  load-runner entry points.
- Added HTTP, Worker, WebSocket, and Hocuspocus/Yjs loaders with semantic,
  ledger, reconnect, durable-reload, abort, and evidence assertions.
- Added environment-only runtime bootstrap, deterministic fixture derivation,
  per-connection one-time collaboration tickets, explicit node identity checks,
  safe deep request templates, and a checked-in
  M1 runtime that exercises real auth, profile, idempotency, MinIO upload,
  realtime fanout/calibration, collaboration, and Worker endpoints.
- Added an isolated, capacity-only event probe with workspace/run scoping,
  semantic replay conflict detection, signed and bound membership-watermark
  pagination, missing-delivery/receipt/side-effect backlog accounting, no-store
  responses, and a realtime expectation descriptor.
- Added a guarded single M1 run entry that requires healthy disposable
  containers, freezes immutable provenance before database mutation, proves
  zero named-fixture residue before the first clean seed apply, performs an
  idempotent reapply, checksum-guarded zero-residue cleanup, and a second clean
  seed apply. Each seed checkpoint is persisted and checksum-bound into the
  combined run manifest before containerized four-loader execution and evidence
  verification, without targeting the long-lived stack.
- Bound every source-built API, Worker, Gateway, collaboration, Web, maintenance,
  and runner image revision label to the declared `SOURCE_COMMIT`; external
  PostgreSQL, Redis, MinIO, and Nginx images remain digest-bound.
- Made the initial clean-state proof work before the fixture registry exists and
  scan deterministic business identifiers as well as registry/workspace
  residue. Scenario startup revalidates provenance, seed semantics, both
  fingerprints, and all five persisted JSON hashes before producing load.
- Isolated warmup and measured probe UUIDs, added a WebSocket readiness barrier,
  excluded unrelated workspace broadcasts from the expected-event ledger, and
  made capacity REST calibration require processed delivery, receipt, exact
  side effect, aggregate, source event, and sequence.
- Switched Worker lease comparisons to database time, tolerated bounded local
  clock steps, aligned scenario phase duration with its execution budget, and
  modeled deterministic parallel Worker aggregate lanes.
- Split WebSocket trigger traffic over four deterministic aggregate lanes and
  replaced fallback calibration with bounded polling for the exact side effect
  and source event.
- Added versioned M1/M2 scenario contracts and targeted Node tests. M2 scenario
  execution remains deferred by the current roadmap decision.

## Validation
- Capacity platform targeted tests: `pnpm capacity:test` - 149 passed, 0 failed
  on 2026-07-25 after database-time leases, phase timing, parallel aggregate
  lanes, exact WebSocket receipt polling, collaboration ticket, seed
  clean-state/cleanup, provenance and evidence-binding hardening.
- Capacity contract: `pnpm capacity:contract` - passed; 20 vCPU, 28,416 MiB,
  PostgreSQL 52, Redis 50, and MinIO 30 declared connections.
- Backend affected compile and tests:
  `mvn -q -DskipTests test-compile` and
  `mvn -q -Dtest=CapacityEventProbeServiceTests,CapacityEventProbeControllerTests test`
  - passed.
- Capacity stack dry run:
  `pnpm capacity:stack run --confirm --reason "Review isolated S05 M1 capacity commands" --run-id s05-m1-dry-run --dry-run`
  - passed and exposed health validation, pre-mutation provenance, both clean
  seed cycles, persisted seed evidence, combined run manifest, runner, and
  evidence verification without invoking Docker.
- Real isolated run:
  `pnpm capacity:stack run --confirm --reason "Execute final isolated S05 M1 validation after websocket convergence fixes" --run-id s05-m1-20260725-1954-final`
  - passed two clean seed cycles and the four-loader scenario; all configured
  zero-error thresholds passed and evidence verification reported no tampering.
- Diff whitespace validation: `git diff --check` - passed.
- Backend tests: targeted CapacityEventProbe compile and tests passed; full
  backend regression remains assigned to the deferred Stage-final contract.
- Frontend build: the final stage profile runs the full frontend gate because
  the workbench is part of the affected path set; checkpoint frontend lint passed.
- Local quality gate: checkpoint `quality-gate-20260725T122940.md` passed
  toolchain, planning, architecture boundaries, backend compile, frontend lint
  and the documentation contract before the final stage profile.
- The first stage closeout attempt `quality-gate-20260725T123112.md` preserved a
  documentation-contract failure because not-required browser rows incorrectly
  declared an isolated browser environment; the rows were corrected rather than
  weakening the gate.
- The second attempt `quality-gate-20260725T123225.md` preserved a taxonomy
  failure because `e2e-real-isolated` is reserved by the workbench for browser
  E2E. T06-T12 now use `integration` while retaining the real isolated Compose
  and protocol flows stated in their contracts and evidenced by the final run.
- Final stage closeout `quality-gate-20260725T123337.md` passed toolchain,
  planning, architecture boundaries, targeted backend tests, frontend lint and
  build, chunk budget, route lazy loading, strict documentation evidence and Git
  whitespace/conflict checks.
- Browser smoke: not required because the M1 acceptance surface is the
  CLI/Compose/API/WebSocket/Yjs/Worker harness.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None for M1 acceptance; M2-M5 are separately marked Deferred in the active roadmap | non-blocking | Closed by final isolated run `s05-m1-20260725-1954-final` |

## Next Steps
- Keep M2-M5 Deferred until the roadmap recovery prerequisites are explicitly
  satisfied; do not run 60-minute or 8-hour scenarios from this M1 closeout.
- Re-run the complete M1 preflight, seed cycle, four-loader scenario and evidence
  verification against the then-current code and environment before resuming M2.
