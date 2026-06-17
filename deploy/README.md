# Deploy

This directory contains the single-node delivery baseline for Colla Platform.
It is intended for a test or small-team production environment before a later
Kubernetes or managed-cloud deployment is justified.

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

3. Start or update the stack:

```powershell
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --build
```

4. Check health:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/health-check.ps1 -EnvFile deploy/.env.prod -BaseUrl http://localhost
```

5. For TLS, put certificates under `deploy/certs` and extend
`deploy/nginx/colla.conf` with a `listen 443 ssl` server block.

## Backup

The backup script captures PostgreSQL and MinIO data into `.local-backups/`.
Each backup includes `manifest.json`, `manifest.md`, file sizes, and SHA-256
checksums.

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

Actual restore drill:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore-drill.ps1 -BackupPath .local-backups/<timestamp> -EnvFile deploy/.env.prod -RunRestore -ConfirmRestore
```

Direct restore:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore.ps1 -BackupPath .local-backups/<timestamp> -EnvFile deploy/.env.prod -ConfirmRestore
```

After restore, confirm:

- `/api/health` returns normally.
- `/actuator/health` returns `UP`.
- Login works with the expected admin account.
- IM, Project, Docs, Base, and Notifications pages can open.

## Health And Monitoring

Runtime health signals:

- `/api/health` for lightweight application reachability.
- `/actuator/health` for Spring Boot health status.
- `/actuator/prometheus` for Prometheus metrics when checking the backend
  directly.

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
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1 -EnvFile deploy/.env.prod
```

For a dry run with the example env file:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1 -EnvFile deploy/.env.prod.example -AllowDirty -SkipQualityGate -SkipImageBuild
```

## Rollback

Rollback is explicit and requires `-ConfirmRollback`.

Application-only rollback to a known Git ref:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/rollback.ps1 -EnvFile deploy/.env.prod -GitRef <tag-or-commit> -ConfirmRollback
```

Application and data rollback:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/rollback.ps1 -EnvFile deploy/.env.prod -GitRef <tag-or-commit> -BackupPath .local-backups/<timestamp> -RestoreData -ConfirmRollback
```

The rollback script rebuilds and starts the compose stack, optionally restores
data, and runs the health check afterward.
