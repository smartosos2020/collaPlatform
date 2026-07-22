---
title: Project Space Migration Runbook
status: active
updated_at: 2026-07-22
---

# Project Space Migration Runbook

## Purpose

This runbook describes how to profile, dry-run, execute, verify, resume, and roll back the S02 legacy `projects` / `project_members` to project space / space member migration.

Scope boundary (frozen by S02-M4):

- The migration only creates project spaces, space members, role assignments, legacy maps, and migration batches. It never modifies or deletes legacy `projects`, `project_members`, `issues`, `conversations`, or `conversation_members` rows.
- Legacy project/issue business writes stay on the legacy path. There is no write cutover and no dual-write. Full WorkItem migration is owned by S07.
- IM group membership drift is reported by the profile but never auto-fixed; migration does not expand members based on IM data.

## Actors and permissions

- Every migration endpoint requires the enterprise `project.manage` governance capability (`requireManageProjects`).
- High-risk operations require an explicit confirmation string in the request body: `EXECUTE` for apply, `ROLLBACK` for rollback.
- Every operation writes an audit event: `project_migration.profiled`, `project_migration.dry_run`, `project_migration.executed`, `project_migration.resumed`, `project_migration.verified`, `project_migration.rolled_back`.

## Step 1 — Profile and precheck

```shell
curl -H "Authorization: Bearer <admin-token>" \
  http://localhost:8080/api/admin/project-migrations/profile
```

The profile reports, per caller workspace: active/archived project and member totals, role distribution, orphan members (user missing, deleted, disabled, or cross-workspace), illegal roles, duplicate owners, shared conversations, projects without an owner, bidirectional IM drift, and missing/archived/type-mismatched conversations. Each category returns a total count plus a truncated item list (200 per category).

Resolve or accept every reported category before apply: orphan users and unknown roles are recorded as batch failure items and are never silently migrated or elevated.

## Step 2 — Dry-run

```shell
curl -X POST -H "Authorization: Bearer <admin-token>" \
  http://localhost:8080/api/admin/project-migrations/spaces:dry-run
```

The dry-run persists a `dry_run=true` batch with the full plan in `summary` and every data-quality item in `failures`. It creates no spaces, members, or maps. Sample summary fragment:

```json
{
  "mode": "dry-run",
  "counts": { "total": 12, "created": 0, "reused": 0, "skipped": 1, "failed": 0, "memberFailures": 2 },
  "projects": [
    { "projectId": "...", "decision": "CREATE_NEW", "resolvedSpaceKey": "alpha" },
    { "projectId": "...", "decision": "KEY_CONFLICT_SUFFIXED", "resolvedSpaceKey": "beta-1a2b3c4d" }
  ]
}
```

## Step 3 — Execute (apply)

```shell
curl -X POST -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"confirmation":"EXECUTE"}' \
  http://localhost:8080/api/admin/project-migrations/spaces:execute
```

Deterministic rules:

- Space id is `UUID.nameUUIDFromBytes("colla:project-legacy-space:" + legacyProjectId)`; reruns produce the same mapping.
- Space key is the normalized legacy `project_key`; on conflict with an unrelated space it receives a deterministic `-<hex8>` suffix.
- Roles map `owner -> owner` (all owners are preserved), `member -> member`, `viewer -> guest`; unknown roles and orphan users go to the failure list and are never written into the space.
- Each project is an independent unit transaction; a failing project rolls back only itself and is recorded in `failures`. Project-scope failures mark the batch `failed`; member-scope findings are recorded but do not fail the batch.
- The mapping plan and source fingerprint are computed inside one REPEATABLE_READ transaction that also holds the workspace migration lock, so both come from a single consistent database snapshot even while legacy writes continue. Dry-run reads use a separate REPEATABLE_READ read-only transaction for the same guarantee.
- The fingerprint digest covers each project's `updated_at`, its active member set including every member's user status/deleted/workspace validity, and the watermark includes member-user update stamps; user disable/restore/delete therefore changes the recorded input checksum.
- Legacy writes are not serialized by the migration lock, so every unit re-validates its project snapshot against the plan-time fingerprint before writing. A drifted project fails with `UNIT_FAILED` ("Legacy source changed during migration; rerun the batch") and can be resumed after review.

## Step 4 — Resume

```shell
curl -X POST -H "Authorization: Bearer <admin-token>" \
  http://localhost:8080/api/admin/project-migrations/batches/{batchId}:resume
```

Resume accepts `failed` batches and stale `running` batches (for example after an interruption). It recomputes the plan and only processes projects that still have no active map; everything else converges to `REUSED`.

The summary has two intentionally different project views:

- `summary.projects` is the latest attempt result and may change after resume (`CREATED` items commonly appear as `REUSED` on the next attempt).
- `summary.manifestProjects` is the batch lifecycle manifest. Once a batch creates a project space, that entry remains `ownedByBatch=true` with its original `spaceId` across later resume attempts. Verification and rollback use this lifecycle ownership rather than inferring ownership from the latest attempt label.

## Step 5 — Verify (batch outcome, manifest-anchored)

```shell
curl -X POST -H "Authorization: Bearer <admin-token>" \
  http://localhost:8080/api/admin/project-migrations/batches/{batchId}:verify
```

Batch verification is anchored to the batch's **lifecycle ownership manifest** in `summary.manifestProjects`. The manifest is merged after each attempt: already owned artifacts keep their original `CREATED`, `spaceId`, and `ownedByBatch=true` provenance even when resume reports them as `REUSED` in `summary.projects`. It answers "is THIS batch's recorded outcome still intact" and is never widened by newly created legacy projects:

- Non-rolled-back batch: every `ownedByBatch=true` project must have an active map owned by this batch with the manifest space id; a missing map is `MAP_MISSING`, a map re-owned by another batch is `MAP_SUPERSEDED`. External `REUSED` projects require the reused map to remain active with the manifest space id. Member sets are compared against the current plan (`MEMBER_SET_MISMATCH`).
- Rolled-back batch: every `ownedByBatch=true` project must have **no** active map; an active map (for example after a later batch re-migrated the project) is `MAP_SUPERSEDED` and the historical batch can no longer verify as successful.
- Maps owned by this batch but missing from its manifest, and maps owned by this batch for `SKIPPED`/`FAILED` projects, are `MAP_UNEXPECTED`.
- `verify` rejects dry-run batches with `409` because dry-runs apply no data.

The result is written back into the same batch's summary and audit log (`project_migration.verified`).

## Step 5b — Workspace convergence verification

```shell
curl -X POST -H "Authorization: Bearer <admin-token>" \
  http://localhost:8080/api/admin/project-migrations/workspaces:verify-convergence
```

Workspace convergence is the batch-independent view: every migratable project (valid owner, no project-scope failure) must have an active map (`MAP_MISSING` otherwise), every active map must belong to a migratable project (`MAP_UNEXPECTED` otherwise, for example after the owner was disabled post-migration), and every map's space and member set must match the current plan. It never writes into any batch summary; it records its own audit event `project_migration.convergence_verified`.

Independent SQL cross-checks:

```sql
-- active maps per batch
select mapping_status, count(*) from project_legacy_space_maps
 where workspace_id = :workspaceId and batch_id = :batchId group by 1;

-- space/member counts versus legacy active projects
select (select count(*) from projects where workspace_id = :workspaceId and archived_at is null) as legacy_projects,
       (select count(*) from project_spaces ps
          join project_legacy_space_maps m on m.space_id = ps.id and m.workspace_id = ps.workspace_id
         where m.workspace_id = :workspaceId and m.mapping_status = 'active') as migrated_spaces;

-- batch terminal state and checksums
select status, dry_run, source_checksum, result_checksum, failure_count
  from (select *, jsonb_array_length(failures) as failure_count
          from project_space_migration_batches where id = :batchId) b;
```

## Step 6 — Rollback

```shell
curl -X POST -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"confirmation":"ROLLBACK"}' \
  http://localhost:8080/api/admin/project-migrations/batches/{batchId}:rollback
```

Rollback is allowed for non-dry-run batches in `completed` or `failed` status. It removes only new-model artifacts that belong to the batch (role assignments, members, space rows, platform object links) and marks its maps `rolled_back`. Legacy data is never touched. Maps belonging to earlier batches are not affected, and a rolled-back project can be migrated again (the map is reactivated with `mapping_version + 1`).

Rollback failure handling:

- A failing rollback unit rolls back only that map; the batch ends `failed` with a `ROLLBACK_FAILED` item and can be rolled back again after the blocker is removed.
- Rollback **appends** rollback failures to the batch's original failure list; orphan-member, unknown-role and unit-failure details are never erased by a rollback.

## Residual data risks

- Orphan members, unknown roles, and ownerless projects stay unmigrated until the underlying identity data is fixed; they are visible in the profile and in batch `failures`.
- IM group drift is intentionally not reconciled; project group membership remains as-is until a later Stage decides on chat-side convergence.
- Migrated spaces are created `private` with the legacy project name; visibility changes follow the normal space settings path after migration.
- Duplicate-owner projects keep every owner as a space owner; review spaces where this widens administration beyond intent.
