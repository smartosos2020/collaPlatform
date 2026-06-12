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

- [PRD and architecture draft](docs/light-colla-platform-prd-architecture.md)
- [Milestone execution roadmap](docs/milestone-execution-roadmap.md)
- [AI engineering governance](docs/ai-engineering-governance.md)
- [Phase 1 technical design](docs/phase1-technical-design.md)
- [Technology selection](docs/technology-selection.md)
- [Project skeleton initialization plan](docs/project-skeleton-initialization.md)

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

## Quality Gates

Run the quick AI engineering gate:

```powershell
pnpm verify
```

Run the full gate before milestone handoff:

```powershell
pnpm verify:full
```
