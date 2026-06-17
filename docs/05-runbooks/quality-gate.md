---
title: Quality Gate Runbook
status: active
updated_at: 2026-06-16
---

# Quality Gate Runbook

## Purpose

This runbook defines when and how to run local quality gates. The gate is the standard verification mechanism for AI work cycles and local development.

## Quick Gate

Command:

```powershell
pnpm verify
```

Equivalent direct script call:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick
```

The quick gate checks:

- toolchain availability;
- Docker dependency health;
- backend tests;
- frontend lint;
- frontend build;
- frontend chunk budget;
- frontend route lazy-loading;
- Mockito javaagent configuration;
- sensitive data patterns;
- Flyway migration order;
- generated artifact tracking;
- open implementation marker inventory;
- documentation structure;
- active work-cycle documentation contract.

Run the quick gate:

- after completing a small implementation slice;
- before saying a task is functionally complete;
- before asking the user to manually verify a changed page.

## Full Gate

Command:

```powershell
pnpm verify:full
```

Equivalent direct script call:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode full
```

The full gate includes the quick gate plus backend packaging. In an active AI work cycle, full mode also requires the mandatory work-cycle documents to be changed.

Run the full gate:

- before finishing a milestone work cycle;
- before commit or push when practical;
- after changing shared architecture, database migrations, security, permissions, or build scripts.

## Useful Local Skips

For script-only or documentation-only checks, use explicit skips:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick -SkipDocker -SkipFrontend -SkipBackend
```

Skips are allowed only when the skipped area is not affected by the current change. The final response must state what was skipped.

## Failure Handling

When the gate fails:

1. Fix failures introduced by the current work before adding more feature code.
2. If the failure is pre-existing, document the evidence and risk in the execution report.
3. Do not ignore compile, migration, security, permission, or startup failures.
4. Do not finish an AI work cycle while `pnpm verify:full` is failing.

Quality reports are written under `.local-reports/` and must not be committed.
