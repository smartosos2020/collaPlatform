# PROJECT-PLATFORM-S07-M3 Execution Report

## Scope
PROJECT-PLATFORM-S07-M3-T01 至 PROJECT-PLATFORM-S07-M3-T11

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M3-T01 | core-system | system-real-isolated | not-required | isolated | No | legacy project/issue/member/comment/attachment/activity/relation profile |
| PROJECT-PLATFORM-S07-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | map, stage, no-dual-write, kill switch and old-write closure |
| PROJECT-PLATFORM-S07-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | V088 batch/unit/manifest/failure/map/cutover/shadow schema |
| PROJECT-PLATFORM-S07-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | UUID reuse and deterministic conflict remap |
| PROJECT-PLATFORM-S07-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | unmigrated issue read without canonical writes |
| PROJECT-PLATFORM-S07-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | legacy alias returns canonical platform location through public resolver |
| PROJECT-PLATFORM-S07-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | legacy/shadow/canonical routing, drift and kill switch |
| PROJECT-PLATFORM-S07-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | canonical-only write boundary and 410/Location |
| PROJECT-PLATFORM-S07-M3-T09 | core-system | system-real-isolated | not-required | isolated | No | mapped/unmapped/hidden/conflict/stage positive and negative cases |
| PROJECT-PLATFORM-S07-M3-T10 | non-core | integration | not-required | not-required | No | caller inventory, metrics, stop conditions and recovery |
| PROJECT-PLATFORM-S07-M3-T11 | core-system | system-real-isolated | not-required | isolated | No | architecture, owner, event and roadmap contracts |

## Completed Items

- V088 adds seven project-owned control-plane tables. Manifest and failure history are append-only; lifecycle rows carry workspace ownership and compound references.
- The profile covers projects, issues, members, comments, attachments, activities and relations with a common watermark/fingerprint plus orphan/cross-workspace counters.
- Every legacy source uses an explicit map even when the UUID is reused. Occupied IDs use a deterministic remap branch.
- The compatibility service implements legacy, shadow, canonical-read, canonical-write and complete stages. Canonical-stage missing maps fail closed; the kill switch only changes read preference.
- Legacy issue reads are projected without writing a canonical item or fabricating a published snapshot.
- Legacy issue platform links return canonical route/deep link when a valid map/target exists. Consumers retain public resolver/API boundaries.
- ProjectService legacy mutation paths share one cutover guard, including IM and knowledge calls. Closed routes return stable `legacy_write_closed` plus `Location`.

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M3-T01 | complete traceable profile | compatibility profile repository | profile count/fingerprint integration assertion | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T02 | stage/write/kill-switch contract | CutoverState, V088, runbook | optimistic stage and kill-switch flow | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T03 | immutable control schema | V088 | Flyway V001-V088 and owner contract | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T04 | explicit collision branch | identity resolver | reuse/remap deterministic unit tests | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T05 | read-only legacy adapter | compatibility service | unmigrated legacy response | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T06 | unified public location | issue/work-item resolvers | mapped target and membership coverage | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T07 | observable routing | compatibility service + shadow repository | shadow/canonical/kill-switch assertions | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T08 | no dual write | shared ProjectService guard + handler | legacy write closure assertion | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T09 | identity and stage matrix | integration suite | hidden outsider/map/stage cases PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T10 | monitoring/runbook | compatibility runbook | documentation contract | Not required | Done |
| PROJECT-PLATFORM-S07-M3-T11 | synchronized contracts | architecture/object/event/module docs | boundaries/contracts PASS | Not required | Done |

## Code Changes

- `server/src/main/resources/db/migration/V088__create_work_item_compatibility_control_plane.sql`
- `server/src/main/java/com/colla/platform/modules/project/{api,application,domain,infrastructure}/**`
- `server/src/test/java/com/colla/platform/modules/project/application/*WorkItem*Tests.java`
- `tools/workbench/config/platform-table-owners.json`
- project architecture, object, event and module contract documents

## Validation

- Backend compile: PASS.
- Backend tests: `mvn -Dtest=WorkItemLegacyIdentityResolverTests,WorkItemServiceIntegrationTests,ModuleArchitectureTests test`; focused suites PASS.
- Frontend build: Not required; M3 changes backend compatibility/control-plane contracts, while end-user routes belong to M5.
- Migration: Testcontainers PostgreSQL 16.14; Flyway V001-V088 88/88 PASS.
- Browser smoke: Not required; no user UI route is activated in M3.
- Local quality gate: stage checkpoint PASS (`.local-reports/quality-gate-20260726T072817.md`) and final finish PASS (`.local-reports/quality-gate-20260726T073317.md`).

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M4-T02 | immutable manifest planning and real migration execution are not implemented | blocks migration/cutover | M4 |
| PROJECT-PLATFORM-S07-M4-T07 | independent verification and compensation are not implemented | blocks production cutover | M4 |
| PROJECT-PLATFORM-S07-M5-T02 | user routes and legacy browser redirect are not delivered | blocks user rollout | M5 |

## Next Steps

- M4 consumes the frozen profile/map/cutover contracts and implements project-unit migration, verification, resume and rollback.
- Do not enter canonical-write or production migration from M3 alone.
