# Scripts

Project-level helper scripts live here.

## AI Engineering Workflow

- `ai-quality-gate.ps1` - runs local quality gates for backend, frontend, Docker dependencies, migration order, secret scan, and generated artifact checks.
- `ai-audit-snapshot.ps1` - writes a local audit snapshot under `.local-reports/`.
- `ai-work-cycle.ps1` - wraps the long-running AI work cycle: `start`, `checkpoint`, and `finish`.

Root commands:

```powershell
pnpm verify
pnpm verify:full
pnpm audit:snapshot
pnpm work:start
pnpm work:checkpoint
pnpm work:finish
```
