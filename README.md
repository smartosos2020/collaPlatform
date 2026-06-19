# Colla Platform

Lightweight internal collaboration workspace for IM, project/Bug management, documents, multidimensional tables, notifications, and future OA workflows.

## Structure

- `server/` - Spring Boot backend
- `web/` - React web client
- `desktop/` - reserved desktop client workspace
- `mobile/` - reserved mobile client workspace
- `deploy/` - deployment assets

Documentation, reports, logs, and operational scripts are local-only and are ignored by Git.

## Local Development

1. Copy `.env.example` to `.env` and adjust values if needed.
2. Start dependencies:

```powershell
docker compose up -d postgres redis minio
```

3. Start backend:

```powershell
cd server
mvn spring-boot:run
```

4. Start frontend:

```powershell
cd web
pnpm install
pnpm dev
```
