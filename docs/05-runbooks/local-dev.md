---
title: Local Development Runbook
status: active
updated_at: 2026-06-16
---

# Local Development Runbook

## Purpose

This runbook defines the standard local startup path for Colla Platform. Use it when preparing a development machine, restarting the local stack, or validating that the project can run end to end.

## Prerequisites

- Docker Desktop is running.
- Java 21 is available.
- Maven is available.
- Node.js 24 is available.
- pnpm 9.4.0 is available.

The quality gate checks these tools automatically.

## Local Configuration

Copy the example environment file when a local `.env` file does not exist:

```powershell
Copy-Item .env.example .env
```

Default local connection values:

| Component | Value |
| --- | --- |
| PostgreSQL host | `localhost:5432` |
| PostgreSQL database | `colla_platform` |
| PostgreSQL username | `colla` |
| PostgreSQL password | `colla_dev_password` |
| Redis | `localhost:6379` |
| MinIO S3 endpoint | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |
| MinIO access key | `colla_minio` |
| MinIO secret key | `colla_minio_password` |
| Initial admin | `admin / admin123456` |

Local-only connection notes can be kept in `.local-connection-info.md`; the file is ignored by git.

## Start Dependencies

```powershell
docker compose up -d postgres redis minio
```

Check service state:

```powershell
docker compose ps
```

Expected services:

- `colla-postgres`
- `colla-redis`
- `colla-minio`

## Start Backend

```powershell
cd server
mvn spring-boot:run
```

Default backend URL:

```text
http://localhost:8080
```

The backend runs Flyway migrations on startup.

## Start Frontend

```powershell
pnpm install
pnpm web:dev
```

Default frontend URL:

```text
http://127.0.0.1:5173
```

The web client calls:

- API: `http://localhost:8080/api`
- WebSocket: `ws://localhost:8080/ws/events`

## Smoke Path

After both services are running:

1. Open `http://127.0.0.1:5173/login`.
2. Log in as `admin / admin123456`.
3. Open Workspace, IM, Project, Docs, Base, Notifications pages from the left navigation.
4. Confirm no page shows a blank screen or a blocking runtime error.

For frontend behavior changes, follow `docs/05-runbooks/browser-smoke.md`.

## Stop Local Stack

Stop app processes from their terminals, then stop dependencies when no longer needed:

```powershell
docker compose down
```

Do not delete Docker volumes unless a clean database is explicitly required.
