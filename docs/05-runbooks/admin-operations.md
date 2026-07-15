---
title: Admin Operations Runbook
status: active
updated_at: 2026-07-13
---

# Admin Operations Runbook

## Start And Stop

Start dependencies:

```powershell
docker compose up -d postgres redis minio
```

Start backend and frontend in separate terminals:

```powershell
cd server
mvn spring-boot:run
```

```powershell
pnpm web:dev
```

Stop app terminals first, then stop dependencies:

```powershell
docker compose down
```

## Health And Metrics

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/health-check.ps1
```

The check validates `/api/health`, `/actuator/health`, and direct-backend `/actuator/prometheus`. Production Nginx exposes only `/actuator/health` by default.

## Backup

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/backup.ps1
```

The backup script writes a timestamped directory under `.local-backups/` containing `postgres.sql`, `minio-data.tgz`, `manifest.json`, and `manifest.md`. Manifest v2 records source project/commit, Flyway version, key row counts, MinIO object count, sizes and hashes. The application is quiesced and MinIO is stopped while its raw volume is copied.

## Restore Drill

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore-drill.ps1 -BackupPath .local-backups\YYYYMMDD-HHMMSS
```

This validates hashes and compose config only. A destructive drill additionally requires `-RunRestore`, `-ConfirmRestore`, an explicit `-ExpectedProjectName`, and a separate target named `colla-platform-drill-<id>`. Never point a drill at the source project, its ports, or its volumes.

## Knowledge Base Checks

Before freezing or migrating knowledge-base block content, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/knowledge-consistency-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/knowledge-naming-guard.ps1
```

`kb:consistency-check` queries the current knowledge space, item, block, version, permission, search and object-reference model without writing data. `kb:naming-guard` prevents active source and browser tests from reintroducing the deleted document product model, routes and compatibility types. Historical migration, block-v2 trial and compatibility-cleanup scripts are archived and are not current release checks.

Treat `Decision: GO-WITH-REVIEW` as acceptable only when each WARN has an explicit compatibility or cleanup decision in the current execution report. Treat any FAIL as a release blocker.

## Logs

- Local backend logs: `.local-logs/server/app.log` and `.local-logs/server/error.log`.
- Test logs: `.local-logs/server-test/`.
- Production server logs: `server_logs` Docker volume mounted at `/app/logs`.
- Every HTTP request includes or receives `X-Colla-Request-Id`; use it to correlate app logs with browser/API reports.

Request correlation drill:

```powershell
$response = Invoke-WebRequest http://127.0.0.1:8080/api/health -UseBasicParsing
$requestId = $response.Headers['X-Colla-Request-Id']
rg $requestId .local-logs/server .local-reports
```

The health script also validates `/actuator/health` and, when requested,
`/actuator/prometheus`; a timeout or exhausted database pool is a release
blocker rather than a successful health result.

## Release And Rollback Decision Tree

1. Code-only rollback: use when the release is incompatible only at the application layer, no migration or data corruption is observed, and the previous immutable server/web image pair is available. Run `rollback.ps1` without `-RestoreData`, then run health and smoke checks.
2. Compatibility rollback: use when a migration is backward-compatible and the previous application can still read the current schema. Keep the database, roll back to the version-tagged application images, and verify health, login, and the affected route before deciding whether a data restore is needed.
3. Data rollback: use only for corruption, deletion, or an unrecoverable migration. Select a manifest-verified backup, run the restore drill, obtain explicit confirmation, restore PostgreSQL and MinIO, compare key object counts, then run health and smoke checks.

Never combine a code rollback and data restore by default. A data restore is
destructive and requires `-ConfirmRollback`, `-RestoreData`, and a concrete
`-BackupPath`.

Rollback images must not use `latest`; both must expose the same full OCI
`org.opencontainers.image.revision`. The script deploys with `--no-build` and
does not switch or modify the Git worktree.

## Release Gate And CI Boundary

The current release gate is a manually executed single-node gate:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1 -EnvFile deploy/.env.prod
```

A formal pass additionally requires a clean worktree, full quality gate, a
recent manifest-v2 backup matching the target project, immutable app image
tags, a full source commit, and successful image build. `-AllowDirty` or any
skip flag requires `-AllowPartial` and must report `PARTIAL`, never `PASS`.

Run the non-destructive safety contract before release or restore work:

```powershell
pnpm ops:contract-check
```

`.github/workflows/ci.yml` is a local reference template excluded by the
repository ignore policy; it is not a remote required check or merge gate.
Until a user explicitly approves publishing that workflow, operators must
run the local quality gate, compose validation, backup, deploy, health check,
and smoke steps and retain their reports under `.local-reports/`.

## Desktop Drill Record

For a release/rollback rehearsal, record the operator, start/end timestamps,
source commit, immutable image IDs, backup manifest, commands, elapsed time, health result, key object
counts, and every warning. A drill is successful only when the runbook reaches
health plus smoke verification; a dry-run hash check alone is not a restore
success.

## Accounts And Permissions

- Use `/admin/users` to create, disable, enable, and reset members.
- Use the audit log page to review user and permission operations.
- Disable trial accounts after the trial window.
- Keep one administrator account separate from day-to-day trial personas.

## Common Failures

| Symptom | First check | Action |
| --- | --- | --- |
| Login fails | `/api/health`, backend logs | Verify backend is running and admin account exists |
| Blank frontend page | Browser console and `pnpm web:build` | Rebuild frontend and check API base URL |
| API 401/403 | User role and object membership | Check project/knowledge content/Base membership and permissions |
| File upload/download fails | MinIO health and storage config | Run `docker compose ps`, inspect MinIO env vars |
| Slow API | Request logs and current metrics | Inspect the affected endpoint and establish a route-specific baseline before comparing results |
| Need rollback | Latest backup and restore drill | Run restore only with explicit confirmation |
