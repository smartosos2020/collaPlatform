# Colla Platform

Lightweight internal collaboration workspace for IM, project/Bug management, documents, multidimensional tables, notifications, and future OA workflows.

## Structure

- `docs/` - product and architecture documents
- `server/` - Spring Boot backend
- `web/` - React web client
- `desktop/` - reserved desktop client workspace
- `mobile/` - reserved mobile client workspace
- `scripts/` - local development scripts
- `deploy/` - deployment assets

## Planning Documents

- [Documentation index](docs/README.md)
- [Current product scope](docs/00-product/current-product-scope.md)
- [Current architecture](docs/01-architecture/current-architecture.md)
- [Technology selection](docs/01-architecture/technology-selection.md)
- [Current roadmap](docs/02-roadmap/current-roadmap.md)
- [AI engineering governance](docs/03-engineering/ai-engineering-governance.md)
- [Local development runbook](docs/05-runbooks/local-dev.md)
- [Quality gate runbook](docs/05-runbooks/quality-gate.md)

## Local Development

Follow the full runbook in [docs/05-runbooks/local-dev.md](docs/05-runbooks/local-dev.md).

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

## Quality Gates

See [docs/05-runbooks/quality-gate.md](docs/05-runbooks/quality-gate.md) for quick/full gate rules and failure handling.

Run the quick AI engineering gate:

```powershell
pnpm verify
```

Run the full gate before milestone handoff:

```powershell
pnpm verify:full
```
