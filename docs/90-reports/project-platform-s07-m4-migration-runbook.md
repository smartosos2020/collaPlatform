# PROJECT-PLATFORM-S07-M4 Work Item Migration Runbook

## 1. Scope And Authority

This runbook operates the first-stage legacy `projects/issues` to canonical WorkItem migration. It does not authorize production cutover. Production execution additionally requires a current target-environment profile, verified backup restore, monitoring, an approved change window and named operators.

Only an authenticated enterprise administrator or principal with `project.manage` may use the governance API. Space ownership alone does not grant migration control. Every lookup is scoped by the caller workspace; a foreign batch is reported as not found.

## 2. State Model

| Object | States | Allowed transition |
| --- | --- | --- |
| Batch | `planned`, `running`, `paused`, `completed`, `failed`, `rolled_back` | plan creates `planned`; execute/resume enters `running`; operator pause enters `paused`; all units successful enters `completed`; one or more failed units enters `failed`; confirmed pre-cutover rollback enters `rolled_back` |
| Unit | `planned`, `running`, `paused`, `completed`, `failed`, `rolled_back` | claim changes planned/paused to running; the project transaction changes it to completed or failed; rollback changes completed units to rolled_back |
| Map | `active`, `rolled_back` | successful unit appends active map; pre-cutover rollback retains identity history and marks it rolled_back |
| Verification | `matched`, `mismatched` | every invocation appends a new immutable result; no transition or overwrite exists |

A retry of a failed batch resets failed units to paused before claiming. Failure rows remain append-only. A changed source cannot resume against an old manifest; generate a new plan after reviewing the drift.

## 3. Planning And Preconditions

1. Confirm legacy writes are still authoritative and the target workspace/space is not at `canonical_write` or `complete`.
2. Capture a current legacy profile and resolve all blocking findings:
   - active project-to-space map exists;
   - exact published `project`, `requirement`, `task` or `bug` binding exists;
   - published snapshot schema/hash is valid;
   - legacy issue types are supported;
   - the source is not already actively mapped.
3. Create a dry-run plan. It may write batch/manifest/failure governance evidence but must not write WorkItems, maps, links, comments or attachments. The request may include an explicit `projectIds` set for a canary or bounded acceptance batch; omitted `projectIds` means all eligible projects in the caller workspace. Unknown requested projects produce `LEGACY_PROJECT_NOT_FOUND` and block execution.
4. Review the immutable manifest fingerprint, unit count, warning list and blocking failures.
5. Create the executable plan immediately before the approved window. Plan data and fingerprints come from one `REPEATABLE_READ` snapshot.

Example:

```powershell
$env:COLLA_API_BASE_URL = "https://colla.example"
$env:COLLA_ACCESS_TOKEN = "<operator-session-token>"
pnpm project:migrate-work-items -- --action plan --dry-run true --throttle-millis 0
pnpm project:migrate-work-items -- --action plan --dry-run false --throttle-millis 50
```

For a scoped governance API plan, send `projectIds` in the JSON request body. Record the approved scope with the resulting immutable manifest; never use scope filtering to hide unrelated workspace failures from a claimed full-workspace cutover.

Never place access tokens in command history, source files, reports or screenshots. Prefer an ephemeral environment variable from the approved secret/session provider.

## 4. Execute, Observe And Resume

```powershell
pnpm project:migrate-work-items -- --action execute --batch-id <batch-id> --worker-id operator-a
pnpm project:migrate-work-items -- --action status --batch-id <batch-id>
pnpm project:migrate-work-items -- --action failures --batch-id <batch-id>
```

The executor owns a batch through lease owner, random token, monotonic fence and heartbeat. A second owner receives `MIGRATION_LEASE_UNAVAILABLE`. Project units use `FOR UPDATE SKIP LOCKED` and an independent transaction; one failed project cannot leave a partial unit or block a sibling project.

Pause:

```powershell
pnpm project:migrate-work-items -- --action pause --batch-id <batch-id> --reason "change-window checkpoint"
```

An orderly pause is observed before the next unit. If a process crashes, the current project transaction rolls back and a new worker may acquire the lease after the two-minute lease timeout. Resume uses the same execute command and batch ID.

## 5. Verification

Run both checks after a completed execution:

```powershell
pnpm project:migrate-work-items -- --action verify --batch-id <batch-id>
pnpm project:migrate-work-items -- --action convergence
```

- Batch verification compares source counts/checksums, active maps, target checksums, fields, comments, attachments, provenance and orphan conditions against that batch's original immutable manifest.
- Workspace convergence compares current legacy sources and active canonical maps/targets. It is independent and cannot change an earlier batch conclusion.
- A batch verification after rollback must be mismatched. A later successful batch cannot make an earlier failed or rolled-back batch matched.

Do not proceed to a write cutover unless both checks are matched and the shadow comparison has no unexplained drift.

## 6. Stop Conditions

Pause immediately when any of these occurs:

- source fingerprint drift, missing or invalid published binding/snapshot;
- lease/fence conflict or repeated unit failure;
- unexpected UUID/map collision rate;
- batch verify mismatch, workspace convergence mismatch or shadow drift;
- attachment/file reference loss, orphan target or provenance mismatch;
- database lock pressure, error rate or latency exceeds the approved window budget;
- audit, monitoring or failure-download evidence becomes unavailable.

The rehearsal stop objective is one unit boundary under normal operation. Crash takeover is bounded by the two-minute lease timeout plus current transaction rollback. These are control-plane objectives, not a production latency or availability commitment.

## 7. Rollback And Compensation

Before canonical write cutover:

```powershell
pnpm project:migrate-work-items -- --action rollback --batch-id <batch-id>
```

Rollback requires the literal `ROLLBACK` confirmation at the API. It deletes this batch's platform links, canonical comments/attachments/participants/activities/projections and WorkItems, marks its active maps/units/batch rolled back, and preserves manifest, failure, provenance, verification and audit history. The unit rollback transaction is atomic; an error must leave the batch non-rolled-back for diagnosis.

At or after `canonical_write`, destructive rollback is prohibited because canonical objects may contain new user facts. The service enables the kill switch and returns `POST_CUTOVER_COMPENSATION_REQUIRED`. Operators must stop new writes, preserve both facts, produce a reviewed compensation plan, and apply a forward corrective command. Never restore legacy data over canonical new writes.

Rehearsal objectives:

- RPO for a failed project unit: zero partial canonical objects due to the unit transaction.
- orderly stop: before the next unit;
- crashed worker takeover: no earlier than the two-minute stale-lease threshold;
- pre-cutover rollback: complete within the approved local rehearsal window and verify no active maps/targets remain;
- production RTO/RPO: unset until target-volume backup/restore and capacity rehearsal is approved.

## 8. Stable Failure Codes

| Code | Operator action |
| --- | --- |
| `ACTIVE_SPACE_MAP_MISSING`, `PUBLISHED_TYPE_BINDING_MISSING`, `PUBLISHED_SNAPSHOT_INVALID`, `UNSUPPORTED_LEGACY_ISSUE_TYPE` | Fix preflight data/configuration, then create a new plan |
| `LEGACY_SOURCE_CHANGED` | Do not retry the old manifest; review drift and create a new immutable plan |
| `SOURCE_ALREADY_MAPPED` | Inspect the active map and prior batch; never force a duplicate target |
| `MIGRATION_LEASE_UNAVAILABLE`, `MIGRATION_FENCE_CONFLICT`, `MIGRATION_UNIT_FENCE_CONFLICT` | Stop the competing worker, wait for lease expiry if crashed, then resume |
| `MIGRATION_PREFLIGHT_BLOCKED`, `MIGRATION_MANIFEST_MISSING` | Do not execute; repair planning evidence |
| `MIGRATION_REQUIRED_FIELD_MISSING`, `MIGRATION_SOURCE_INVALID`, `SNAPSHOT_INTEGRITY_FAILURE` | Treat as a failed unit; correct source or published snapshot and re-plan |
| `POST_CUTOVER_COMPENSATION_REQUIRED` | Keep canonical facts, enable incident/change process and use forward compensation |

Failure detail is bounded and contains stable codes plus safe diagnostics. Manifests are administrator-only evidence and must be handled as business data.

## 9. Required Closure Evidence

- empty V001 and V061/V065/V078/V085 upgrades to latest, followed by a zero-change repeat migrate;
- a restored pre-upgrade database migrated to latest with sentinel facts unchanged;
- non-empty project with issue, participant, comment, attachment and activity provenance;
- dry-run with zero canonical business writes;
- source drift with zero partial unit;
- two project units where one fails and the sibling completes;
- lease/fencing conflict and same-batch pause/resume;
- occupied legacy UUID with explicit deterministic remap;
- batch verification, workspace convergence and old-batch false-positive negative case;
- pre-cutover rollback and post-cutover compensation-required/kill-switch case;
- append-only manifest/provenance/verification mutation rejection;
- governance identity and cross-workspace negative cases.
