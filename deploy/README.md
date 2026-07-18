# Deploy

This directory contains the Docker Compose delivery baseline for Colla Platform. PostgreSQL, Redis, MinIO and Spring remain single instances; two collaboration sidecars sit behind Nginx for Yjs room recovery. This is suitable for test and small-team deployments, not full high availability.

## Requirements

- Docker Engine with Compose v2
- Node.js 22 or later
- pnpm 9.4.0
- Java 21 and Maven 3.9 when the release gate runs source verification

All active operational commands use the repository's Node/TypeScript workbench and run identically on Windows, macOS and Linux. Historical Windows implementations are retained only below the deployment script archive and are not supported entry points.

## Test Environment Deployment

1. Create the environment file:

```shell
cp deploy/.env.prod.example deploy/.env.prod
```

2. Replace every secret and configure explicit immutable `SERVER_IMAGE`, `WEB_IMAGE`, and `COLLABORATION_IMAGE` tags. Set `SOURCE_COMMIT` to the full 40-character commit used to build all images.

3. Run the release gate. A verified backup is mandatory for a formal pass:

```shell
pnpm ops:release-check -- --env-file deploy/.env.prod --expected-project-name colla-platform-prod --create-backup
```

4. Start or update the validated images:

```shell
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --no-build --wait
```

5. Verify health and request/log correlation:

```shell
pnpm ops:health -- --env-file deploy/.env.prod --base-url http://localhost --expected-project-name colla-platform-prod --require-log-correlation
```

For TLS, mount certificates under `deploy/certs` and extend `deploy/nginx/colla.conf` with a `listen 443 ssl` server block.

## Backup And Restore

Backups are written below `.local-backups/`. Manifest v2 records source project and commit, consistency mode, Flyway version, database counts, MinIO object count, sizes and SHA-256 hashes.

```shell
pnpm ops:backup -- --env-file deploy/.env.prod
pnpm ops:backup -- --env-file deploy/.env.prod --retention-days 14
```

Dry-run restore validation does not modify data:

```shell
pnpm ops:restore-drill -- --backup-path .local-backups/<timestamp> --env-file deploy/.env.prod
```

An actual drill must use separate ports, volumes and a Compose project matching `colla-platform-drill-<id>`:

```shell
pnpm ops:restore-drill -- --backup-path .local-backups/<timestamp> --env-file .local-reports/restore-drill.env --expected-project-name colla-platform-drill-<id> --base-url http://127.0.0.1:<port> --run-restore --confirm-restore
```

Direct production restore is destructive and requires an exact confirmation:

```shell
pnpm ops:restore -- --backup-path .local-backups/<timestamp> --env-file deploy/.env.prod --expected-project-name colla-platform-prod --confirmation-text RESTORE:colla-platform-prod --confirm-restore
```

After restore, verify API and Actuator health, login, and the IM, Project, Knowledge Base, Base and Notifications paths.

## Health And Monitoring

```shell
pnpm ops:health
pnpm ops:health -- --env-file deploy/.env.prod --base-url http://localhost --expected-project-name colla-platform-prod
```

- `/api/health`: application reachability.
- `/actuator/health`: Spring health.
- `/actuator/prometheus`: internal Prometheus metrics.
- Collaboration sidecars expose `/health`, `/ready`, protected `/metrics`, and protected `POST /internal/invalidate` inside the deployment network.
- Spring JSON logs rotate under `LOG_PATH`; Docker JSON logs are capped in `docker-compose.prod.yml`.

## Controlled Pilot

Use `deploy/pilot-v2/manifest.example.json` as the versioned contract and keep concrete rosters under ignored `.local-pilot/`. Manifests must never contain credentials.

```shell
pnpm pilot:contract-check
pnpm pilot:m10-contract-check
pnpm pilot:check -- --manifest-path .local-pilot/pilot.json --level initialization
pnpm pilot:initialize -- --manifest-path .local-pilot/pilot.json
```

Initialization is read-only unless `--apply` is supplied with `COLLA_PILOT_ADMIN_USERNAME`, `COLLA_PILOT_ADMIN_PASSWORD`, `COLLA_PILOT_INITIAL_PASSWORD`, and exact `--confirmation-text`. The initializer creates or verifies departments, users, participant group, project, knowledge space/template, Base/table, group resource grants and per-participant Base edit grants.

Use `pnpm pilot:readiness` with explicit backup, restore-drill and quality-gate evidence. Simulation evidence can close an engineering rehearsal but cannot serve as human release approval.

The M10 continuous synthetic runner is intentionally destructive to an isolated rehearsal stack and requires a project named `colla-platform-pilot-m10*` plus exact `RUN-SIMULATION:<pilotId>:<projectName>` confirmation. Run it with `pnpm pilot:m10-simulation`; it preserves per-phase Playwright evidence, performs the controlled server restart, and evaluates all frozen metrics.

## Rollback

Rollback uses immutable images carrying the same `org.opencontainers.image.revision`. It never checks out or rebuilds a Git ref.

```shell
pnpm ops:rollback -- --env-file deploy/.env.prod --server-image registry.example.com/colla/server:<version> --web-image registry.example.com/colla/web:<version> --collaboration-image registry.example.com/colla/collaboration:<version> --expected-source-commit <40-char-commit> --expected-project-name colla-platform-prod --confirmation-text 'ROLLBACK:colla-platform-prod:registry.example.com/colla/server:<version>:registry.example.com/colla/web:<version>:registry.example.com/colla/collaboration:<version>' --confirm-rollback
```

Add `--backup-path .local-backups/<timestamp> --restore-data` only when data restoration is required. A normal collaboration node or Redis outage does not require database rollback.

## Contract Verification

```shell
pnpm ops:contract-check
pnpm security:scan -- --skip-report
pnpm pilot:contract-check
```

The quality gate rejects Windows-only scripts outside an `archive/` directory and rejects platform-specific invocations in active package, workflow and runbook entry points.
