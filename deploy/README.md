# Deploy

This directory contains the Docker Compose delivery baseline for Colla Platform.
The Spring backend, PostgreSQL, Redis, and MinIO remain single instances, while
two collaboration sidecars sit behind Nginx for cross-node Yjs room recovery.
This is suitable for a test or small-team environment, but it is not a complete
high-availability deployment.

## Test Environment Deployment

1. Prepare the environment file:

```powershell
Copy-Item deploy/.env.prod.example deploy/.env.prod
```

2. Replace every secret in `deploy/.env.prod`.

Required values:

- `POSTGRES_PASSWORD`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `JWT_ACCESS_SECRET`
- `JWT_REFRESH_SECRET`
- `INIT_ADMIN_PASSWORD`
- `CORS_ALLOWED_ORIGINS`
- `APP_BASE_URL`
- `SERVER_IMAGE`, `WEB_IMAGE`, and `COLLABORATION_IMAGE`, each with an explicit version tag (never `latest`)
- `COLLA_COLLABORATION_INTERNAL_SECRET`
- `SOURCE_COMMIT`, as the full 40-character commit used to build all three images

3. Run the release gate. A verified backup is mandatory for a formal pass:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1 -EnvFile deploy/.env.prod -ExpectedProjectName colla-platform-prod -CreateBackup
```

4. Start or update the stack from the validated image tags, without rebuilding:

```powershell
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --no-build --wait
```

5. Check health and request/log correlation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/health-check.ps1 -EnvFile deploy/.env.prod -BaseUrl http://localhost -ExpectedProjectName colla-platform-prod -RequireLogCorrelation
```

6. For TLS, put certificates under `deploy/certs` and extend
`deploy/nginx/colla.conf` with a `listen 443 ssl` server block.

## Backup

The backup script captures PostgreSQL and MinIO data into `.local-backups/`.
Each manifest-v2 backup includes source project/commit, consistency mode,
Flyway version, key PostgreSQL counts, MinIO object count, file sizes, and
SHA-256 checksums. The script quiesces the application and stops MinIO while
copying its volume, then restores service health.

Local development backup:

```powershell
pnpm ops:backup
```

Production or test-environment backup:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/backup.ps1 -EnvFile deploy/.env.prod
```

Optional retention pruning is available with `-RetentionDays N`. The script
refuses to prune outside the repository-local backup directory.

## Restore Drill

Restore is destructive and is never run by default.

Dry-run validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore-drill.ps1 -BackupPath .local-backups/<timestamp> -EnvFile deploy/.env.prod
```

Actual restore drill must use a separate Compose project, ports, and volumes.
The project name must match `colla-platform-drill-<id>` and differ from the
backup source:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore-drill.ps1 -BackupPath .local-backups/<timestamp> -EnvFile .local-reports/restore-drill.env -ExpectedProjectName colla-platform-drill-<id> -BaseUrl http://127.0.0.1:<port> -RunRestore -ConfirmRestore
```

Direct restore:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore.ps1 -BackupPath .local-backups/<timestamp> -EnvFile deploy/.env.prod -ExpectedProjectName colla-platform-prod -ConfirmationText RESTORE:colla-platform-prod -ConfirmRestore
```

After restore, confirm:

- `/api/health` returns normally.
- `/actuator/health` returns `UP`.
- Login works with the expected admin account.
- IM, Project, Knowledge Base, Base, and Notifications pages can open.

## Health And Monitoring

Runtime health signals:

- `/api/health` for lightweight application reachability.
- `/actuator/health` for Spring Boot health status.
- `/actuator/prometheus` for Prometheus metrics when checking the backend
  directly.
- Each collaboration sidecar exposes `/health`, `/ready`, and an
  internal-secret-protected `/metrics` endpoint on port `1234`. Inspect these
  endpoints directly inside the deployment network; Nginx does not publish
  them.

Local health check:

```powershell
pnpm ops:health
```

Production health check through nginx:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/health-check.ps1 -EnvFile deploy/.env.prod -BaseUrl http://localhost
```

## Logs And Retention

- Spring Boot JSON logs are written under `LOG_PATH`.
- In the production compose file, `server` uses `LOG_PATH=/app/logs` mounted on
  the `server_logs` volume.
- Logback rotates `app.log` daily and by size, keeps 14 days, and caps total
  app log size at 2 GB.
- Logback rotates `error.log` daily and by size, keeps 30 days, and caps total
  error log size at 2 GB.
- Docker json logs are capped by `docker-compose.prod.yml` at 100 MB x 14 files
  per service.

## Release Checklist

Before deployment:

1. Confirm the worktree is clean.
2. Run the full quality gate.
3. Validate production compose configuration.
4. Build production images.
5. Take a backup of the current environment.
6. Deploy with Docker Compose.
7. Run health checks and manual smoke paths.

Scripted pre-release check:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1 -EnvFile deploy/.env.prod -ExpectedProjectName colla-platform-prod -CreateBackup
```

Any dirty-worktree or skipped-gate run requires `-AllowPartial` and produces
`Decision: PARTIAL`; it is diagnostic evidence, never release approval.

## Controlled Pilot Baseline

Use `deploy/pilot-v2/manifest.example.json` as the versioned contract, and keep
the concrete participant roster under ignored `.local-pilot/`. The manifest
contains no credentials. Validate it before any write:

```powershell
pnpm pilot:contract-check
pnpm pilot:check -- -ManifestPath .local-pilot\pilot.json -Level initialization
pnpm pilot:initialize -- -ManifestPath .local-pilot\pilot.json
```

The initializer defaults to a read-only plan. Applying requires credential
environment variables and the exact confirmation string shown by the manifest.
After initialization, take a target-project backup, complete an isolated restore
drill, run the milestone quality gate, and pass those explicit evidence paths to
`pnpm pilot:readiness`. `-SimulationFreeze` can close an engineering rehearsal
only when every persona is synthetic, the limitations are acknowledged, and the
backup plus source snapshot match; it returns `SIMULATION-READY`. Rehearsal or
simulation evidence cannot be used as a formal human kickoff approval. `-Freeze`
returns `READY` only with human confirmations, a clean release commit, and a
backup produced from that same commit.

Validate the script safety contracts without touching Docker data:

```powershell
pnpm ops:contract-check
```

## Rollback

Rollback deploys previously validated, version-tagged images. It never checks
out or rebuilds a Git ref in the active worktree. The server, web, and
collaboration images must carry the same full
`org.opencontainers.image.revision` label. It requires an exact typed
confirmation string.

Application-only rollback:

```powershell
$serverImage = "registry.example.com/colla/server:<version>"
$webImage = "registry.example.com/colla/web:<version>"
$collaborationImage = "registry.example.com/colla/collaboration:<version>"
$confirmation = "ROLLBACK:colla-platform-prod:$serverImage`:$webImage`:$collaborationImage"
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/rollback.ps1 -EnvFile deploy/.env.prod -ServerImage $serverImage -WebImage $webImage -CollaborationImage $collaborationImage -ExpectedSourceCommit <40-char-commit> -ExpectedProjectName colla-platform-prod -ConfirmationText $confirmation -ConfirmRollback
```

Application and data rollback:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/rollback.ps1 -EnvFile deploy/.env.prod -ServerImage $serverImage -WebImage $webImage -CollaborationImage $collaborationImage -ExpectedSourceCommit <40-char-commit> -ExpectedProjectName colla-platform-prod -ConfirmationText $confirmation -BackupPath .local-backups/<timestamp> -RestoreData -ConfirmRollback
```

The rollback script uses `--no-build`, optionally performs a manifest-verified
data restore, and runs semantic health and request/log correlation afterward.
