---
title: Admin Operations Runbook
status: active
updated_at: 2026-07-16
---

# Admin Operations Runbook

## Start And Stop

Start dependencies:

```shell
docker compose up -d postgres redis minio
```

Start backend and frontend in separate terminals:

```shell
cd server
mvn spring-boot:run
```

```shell
pnpm web:dev
```

Stop app terminals first, then stop dependencies:

```shell
docker compose down
```

## Health And Metrics

```shell
pnpm ops:health
```

The check validates `/api/health`, `/actuator/health`, and direct-backend `/actuator/prometheus`. Production Nginx exposes only `/actuator/health` by default.

## Backup

```shell
pnpm ops:backup
```

The backup script writes a timestamped directory under `.local-backups/` containing `postgres.sql`, `minio-data.tgz`, `manifest.json`, and `manifest.md`. Manifest v2 records source project/commit, Flyway version, key row counts, MinIO object count, sizes and hashes. The application is quiesced and MinIO is stopped while its raw volume is copied.

## Restore Drill

```shell
pnpm ops:restore-drill -- --backup-path .local-backups/YYYYMMDD-HHMMSS
```

This validates hashes and compose config only. A destructive drill additionally requires `--run-restore`, `--confirm-restore`, an explicit `--expected-project-name`, and a separate target named `colla-platform-drill-<id>`. Never point a drill at the source project, its ports, or its volumes.

## Knowledge Base Checks

Before freezing or migrating knowledge-base block content, run:

```shell
pnpm kb:consistency-check
pnpm kb:naming-guard
```

`kb:consistency-check` queries the current knowledge space, item, block, version, permission, search and object-reference model without writing data. `kb:naming-guard` prevents active source and browser tests from reintroducing the deleted document product model, routes and compatibility types. Historical migration, block-v2 trial and compatibility-cleanup scripts are archived and are not current release checks.

Treat `Decision: GO-WITH-REVIEW` as acceptable only when each WARN has an explicit compatibility or cleanup decision in the current execution report. Treat any FAIL as a release blocker.

## Logs

- Local backend logs: `.local-logs/server/app.log` and `.local-logs/server/error.log`.
- Test logs: `.local-logs/server-test/`.
- Production server logs: `server_logs` Docker volume mounted at `/app/logs`.
- Every HTTP request includes or receives `X-Colla-Request-Id`; use it to correlate app logs with browser/API reports.

Request correlation drill:

```shell
curl --include http://127.0.0.1:8080/api/health
# Use the returned X-Colla-Request-Id value:
rg '<request-id>' .local-logs/server .local-reports
```

The health script also validates `/actuator/health` and, when requested,
`/actuator/prometheus`; a timeout or exhausted database pool is a release
blocker rather than a successful health result.

## Release And Rollback Decision Tree

1. Code-only rollback: use when the release is incompatible only at the application layer, no migration or data corruption is observed, and the previous immutable server/web/collaboration image set is available. Run `pnpm ops:rollback` without `--restore-data`, then run health and smoke checks.
2. Compatibility rollback: use when a migration is backward-compatible and the previous application can still read the current schema. Keep the database, roll back to the version-tagged application images, and verify health, login, and the affected route before deciding whether a data restore is needed.
3. Data rollback: use only for corruption, deletion, or an unrecoverable migration. Select a manifest-verified backup, run the restore drill, obtain explicit confirmation, restore PostgreSQL and MinIO, compare key object counts, then run health and smoke checks.

Never combine a code rollback and data restore by default. A data restore is
destructive and requires `--confirm-rollback`, `--restore-data`, and a concrete
`--backup-path`.

Rollback images must not use `latest`; all three must expose the same full OCI
`org.opencontainers.image.revision`. The script deploys with `--no-build` and
does not switch or modify the Git worktree.

## Release Gate And CI Boundary

The current release gate validates the single-backend Compose baseline and its
two collaboration sidecars:

```shell
pnpm ops:release-check -- --env-file deploy/.env.prod
```

A formal pass additionally requires a clean worktree, full quality gate, a
recent manifest-v2 backup matching the target project, immutable app image
tags, a full source commit, and successful image build. `--allow-dirty` or any
skip flag requires `--allow-partial` and must report `PARTIAL`, never `PASS`.

Run the non-destructive safety contract before release or restore work:

```shell
pnpm ops:contract-check
```

`.github/workflows/ci.yml` validates the workbench on Windows, macOS and Linux in addition to the Linux application build. Operators still retain local release evidence under `.local-reports/`.

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
