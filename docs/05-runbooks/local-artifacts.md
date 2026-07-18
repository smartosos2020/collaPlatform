---
title: Local Artifacts Runbook
status: active
updated_at: 2026-06-16
---

# Local Artifacts Runbook

## Purpose

This runbook defines how local logs, reports, screenshots, and generated artifacts are handled during AI-assisted development.

## Local-only Directories

| Path | Purpose | Commit? |
| --- | --- | --- |
| `.local-logs/` | local service logs and ad hoc runtime output | no |
| `.local-reports/` | audit snapshots, quality reports, local backups | no |
| `server/target/` | Maven build output | no |
| `web/dist/` | Vite build output | no |
| `node_modules/` | Node dependencies | no |
| `.data/` | local Docker or runtime data | no |

These paths are ignored by git and checked by the quality gate.

## Audit Snapshots

Command:

```shell
pnpm audit:snapshot -- --label before-M25
```

Snapshots are written under `.local-reports/` and capture:

- toolchain versions;
- Docker service state;
- git status;
- source file inventory;
- timestamp and label.

## Quality Reports

Every quality gate run writes:

```text
.local-reports/quality-gate-YYYYMMDD-HHMMSS.md
```

These reports are local evidence. Summarize the result in the milestone execution report; do not commit the generated local report file.

## Browser Evidence

Screenshots and browser notes can be kept in `.local-reports/` during verification. Commit only durable runbook or test documentation, not temporary screenshots.

## Rules

- Do not commit local logs, generated reports, database dumps, build outputs, or dependency folders.
- Do not use local artifacts as the only durable record of a milestone; summarize important results in `docs/90-reports/mxx-execution-report.md`.
- Clean local artifacts only when they are obsolete or when the user requests cleanup.
