# PLATFORM-SCALE-S05-M1 Execution Report

## Scope
PLATFORM-SCALE-S05-M1-T01 到 PLATFORM-SCALE-S05-M1-T12

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1-T01 | static | not-required | not-required | No | 逐项盘点容量拓扑、运行角色、端口、资源、连接、指标、种子和负载入口，并把缺口绑定到后续任务 |
| PLATFORM-SCALE-S05-M1-T02 | static | not-required | not-required | No | 容量合同显式冻结 C1、SLO、非目标和唯一结论语义，失败后不得静默改阈值 |
| PLATFORM-SCALE-S05-M1-T03 | integration | not-required | isolated | No | 在具名宿主机执行 preflight，生成 CPU、内存、磁盘、Docker、时钟、依赖和提交 manifest，漂移会被拒绝 |
| PLATFORM-SCALE-S05-M1-T04 | integration | not-required | isolated | No | 容量 Compose 配置解析、资源公式和连接预算通过合同验证，非法预算在启动前失败 |
| PLATFORM-SCALE-S05-M1-T05 | static | not-required | not-required | No | 版本化 seed schema 覆盖成员、权限、项目、知识、通知、IM、文件和协同分布并固定校验和 |
| PLATFORM-SCALE-S05-M1-T06 | e2e-real-isolated | not-required | isolated | No | 对 disposable stack 连续完成两次干净 seed apply/verify/reapply/cleanup，标识与校验和一致且只清理具名夹具 |
| PLATFORM-SCALE-S05-M1-T07 | e2e-real-isolated | not-required | isolated | No | 容器化 HTTP loader 对真实登录、查询、命令、幂等和 MinIO 上传执行语义与权限断言 |
| PLATFORM-SCALE-S05-M1-T08 | e2e-real-isolated | not-required | isolated | No | 普通 WebSocket loader 建立真实连接并验证 fanout、sequence、duplicate、gap、重连和 REST ledger 收敛 |
| PLATFORM-SCALE-S05-M1-T09 | e2e-real-isolated | not-required | isolated | No | Hocuspocus/Yjs loader 以多房间多客户端跨两个 collaboration 节点编辑、断线重连并 durable reload |
| PLATFORM-SCALE-S05-M1-T10 | e2e-real-isolated | not-required | isolated | No | Worker loader 通过具名 capacity probe 生成持续与突发事件并核对 backlog、接管、顺序、重复副作用和 dead letter |
| PLATFORM-SCALE-S05-M1-T11 | e2e-real-isolated | not-required | isolated | No | 统一场景同时运行四类 loader，保留 manifest、阈值、原始时序、摘要、校验和与中止原因 |
| PLATFORM-SCALE-S05-M1-T12 | e2e-real-isolated | not-required | isolated | No | 从干净环境复验 seed 隔离、loader 失败识别、安全扫描、证据校验、checkpoint 和 M1 收口 |

## Completed Items
- Implementation is in progress. No task is marked complete until the disposable
  capacity stack, two clean seed cycles, and the real four-loader smoke have
  produced immutable evidence.

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1-T01 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T02 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T03 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T04 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T05 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T06 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T07 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T08 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T09 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T10 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T11 | Pending | Pending | Pending | Pending | Pending |
| PLATFORM-SCALE-S05-M1-T12 | Pending | Pending | Pending | Pending | Pending |

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
- Added versioned M1/M2 scenario contracts and targeted Node tests. M2 scenario
  execution remains out of scope until M1 is closed.

## Validation
- Capacity platform targeted tests: `pnpm capacity:test` - 127 passed, 0 failed
  on 2026-07-25 after collaboration ticket, phase isolation, WebSocket ledger,
  seed clean-state/cleanup, health-state, provenance-order, evidence-binding,
  and guarded entrypoint hardening.
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
- Work-cycle light checkpoint passed after the initial implementation; another
  checkpoint remains required after the real runtime evidence is attached.
- Diff whitespace validation: `git diff --check` - passed.
- Testcontainers repository integration remains deferred until the disposable
  Docker engine is available; full backend tests remain deferred to route-final.
- Frontend build: deferred; M1 does not change product frontend code.
- Browser smoke: not applicable to the M1 CLI/Compose capacity harness.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1-T03/T04 | Docker Engine stopped responding after the host system drive became full. Stale Docker Desktop update caches were removed, but restarting Docker Desktop requires explicit approval because it temporarily interrupts the long-lived `collaplatform` PostgreSQL/Redis/MinIO stack. | Blocks fresh preflight, image, topology, and runtime provenance evidence. | Open |
| PLATFORM-SCALE-S05-M1-T06/T12 | The first million-scale seed apply was interrupted with the Docker CLI. Two clean apply/verify/reapply/cleanup cycles have not completed. | Blocks deterministic initialization and isolation acceptance. | Open |
| PLATFORM-SCALE-S05-M1-T07-T11 | The real containerized four-loader smoke and raw metric bundle have not run against a fresh, provenance-approved stack. The runtime and real server probe path are implemented and unit-tested, but are not yet runtime evidence. | Blocks loader runtime and evidence-contract acceptance. | Open |

## Next Steps
- Obtain explicit Docker Desktop restart approval, then verify the long-lived
  `collaplatform` infrastructure before touching the disposable S05 stack.
- Recreate only `colla-s05-capacity`, complete two clean seed cycles, checkpoint
  immutable inputs, produce a passing provenance manifest, and run the real
  four-loader M1 smoke.
