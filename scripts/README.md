# Scripts

Project-level helper scripts live here.

## AI Engineering Workflow

- `ai-quality-gate.ps1` - runs local quality gates for backend, frontend, Docker dependencies, migration order, secret scan, and generated artifact checks.
- `ai-audit-snapshot.ps1` - writes a local audit snapshot under `.local-reports/`.
- `ai-work-cycle.ps1` - wraps the long-running AI work cycle: `start`, `checkpoint`, and `finish`; it also writes the document contract context used by quality gates.
- `performance-baseline.ps1` - measures key API latency paths against a running local server.
- `../deploy/scripts/backup.ps1` - creates PostgreSQL and MinIO backups with manifests and checksums.
- `../deploy/scripts/restore.ps1` - restores a backup after explicit `-ConfirmRestore`.
- `../deploy/scripts/restore-drill.ps1` - validates a backup package and can run a confirmed restore drill.
- `../deploy/scripts/health-check.ps1` - checks `/api/health`, `/actuator/health`, and optional Prometheus metrics.
- `../deploy/scripts/release-check.ps1` - runs the release checklist gates for compose and optional image build.
- `../deploy/scripts/rollback.ps1` - rolls back to a Git ref and optionally restores data after explicit `-ConfirmRollback`.
- `trial-team-template.ps1` - generates the controlled 10-person team initialization CSV and report for M40 trial readiness.
- `team-trial-readiness.ps1` - checks trial readiness evidence and writes a Go/No-Go report.
- `knowledge-base-migration-check.ps1` - checks knowledge-base migration readiness, orphan documents, owner permissions, inheritance sources, and search-index gaps; writes a rollback SQL template.
- `knowledge-base-trial-runbook.ps1` - generates a 3-5 person knowledge-base trial script and checklist.
- `knowledge-base-acceptance-report.ps1` - aggregates knowledge-base migration, trial, quality-gate, and smoke evidence into a v1 acceptance report.

Root commands:

```powershell
pnpm verify
pnpm verify:full
pnpm audit:snapshot
pnpm work:start
pnpm work:checkpoint
pnpm work:finish
pnpm data:reset
pnpm smoke:im
pnpm sim:m31
pnpm smoke:m31
pnpm ops:backup
pnpm ops:health
pnpm ops:release-check
pnpm trial:team-template
pnpm trial:readiness
pnpm kb:migration-check
pnpm kb:trial-runbook
pnpm kb:acceptance
```

Direct quality gate options:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode full
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick -SkipDocker -SkipFrontend -SkipBackend
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick -BackendStrategy targeted -BackendTestPattern "OrganizationControllerIntegrationTests"
```

`Mode quick` defaults to backend compile/test-compile only; it does not run full Surefire tests. `Mode full` defaults to full `mvn test` and is intended for route-final validation, trial readiness, CI, or explicit full regression requests. Use skip switches only when the skipped area is outside the current change. Record the skip reason in the execution report and final response.

Work cycle with milestone scope:

```powershell
pnpm work:start -- -Goal "M25-delivery" -TaskRange "M25-T01 到 M25-T08"
pnpm work:checkpoint -- -Goal "M25-delivery" -GateMode quick
pnpm work:finish -- -Goal "M25-delivery" -BackendTestPattern "DeliveryControllerIntegrationTests"
pnpm work:finish -- -Goal "current-roadmap-final" -ValidationProfile route-final
```

Validation profiles:

- `light`: default for checkpoint; backend compile/test-compile only, frontend lint/build, static gates, compact successful logs.
- `stage`: default for finish; runs targeted backend tests only when `-BackendTestPattern` is supplied, otherwise backend compile only; still enforces strict document contract.
- `route-final`: explicit final validation for the last milestone of the current roadmap; runs full `mvn test`, backend package, frontend lint/build, security, migration order, and document contract gates.

Successful command output is summarized; full logs are written under `.local-reports/`. On failure, the gate prints the failure tail and points to the full log.

Scope limits for the default `DocMode=code-doc-report`:

- one work cycle can cover at most one milestone;
- one work cycle can cover at most eight tasks;
- broad user input must be split into compliant chunks before starting the cycle;
- the quality gate rechecks the active work-cycle context so an oversized range cannot be finished as a valid cycle.

When `work:start` runs with the default `DocMode=code-doc-report`, it writes `.local-reports/work-cycle-current.json` and creates `docs/90-reports/mxx-execution-report.md` when the milestone can be inferred. The fixed work-cycle contract is:

- update `docs/02-roadmap/current-roadmap.md`;
- create or update `docs/90-reports/mxx-execution-report.md`;
- update active docs only when their facts changed;
- do not create new roadmap files;
- do not create Markdown files in `docs/` root except `README.md`;
- do not edit `docs/99-archive/` unless the work is explicitly archive-only.

Context-reading rule for AI work:

- Use `rg` to discover files and references, but do not modify code or governance docs from a single matching line.
- Fully read work-cycle, roadmap, quality-gate, permission, security, and migration governance files before changing them.
- For long code files, read the full class/function/DTO boundary before patching.
- For successful validation logs, read summaries and report paths; expand full logs only on failure or suspicious warnings.

Archive-only documentation cleanup:

```powershell
pnpm work:start -- -Goal "docs-cleanup" -TaskRange "DOCS" -DocMode archive-only
```

Do not use `archive-only` for business implementation. It is only for explicit documentation cleanup, migration, or archival tasks.

Performance baseline:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/performance-baseline.ps1
```

M40 team trial readiness:

```powershell
pnpm trial:team-template
pnpm trial:readiness
pnpm trial:readiness -- -RequireFullGate -RequireDataReset -M31SmokePassed
```

The strict readiness command requires a full quality gate report, M31 data reset evidence, and an explicit successful M31 smoke run. It is intended for trial-entry Go/No-Go decisions, not for ordinary local development.

Knowledge-base v1 migration and acceptance:

```powershell
pnpm kb:migration-check
pnpm kb:trial-runbook
pnpm kb:acceptance
```

`pnpm kb:migration-check` is read-only against PostgreSQL and writes a rollback SQL template under `.local-reports/`; it does not reset, migrate, or delete data. Use it before and after knowledge-base cutover, then keep the generated report with the backup manifest.

Explicit M31 local data reset and flow regression:

```powershell
pnpm data:reset
pnpm sim:m31
pnpm sim:m31 -- -Stage verify
pnpm smoke:m31
```

`pnpm data:reset` and `pnpm sim:m31` both create a local SQL backup, reset the local database to the fixed M31 10-role, 5-project collaboration scenario, then seed and verify deterministic data. Use them only when the user explicitly asks to reset or restore the M31 baseline. Use `pnpm sim:m31 -- -Stage verify` only to verify an existing M31 dataset without resetting. Full backend quality gates can create integration-test fixtures in the shared local database, but that does not require an automatic reset; rerun `pnpm data:reset` only when the user asks for the browser to show the clean M31 scenario. `pnpm smoke:m31` expects Docker dependencies, backend, and frontend to be running, verifies M31 data, then runs Playwright across project, issue, document, Base, IM, search, and permission-denied paths; run it only when the user explicitly asks for M31 baseline regression.

Legacy M12 reset:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-m12-test-data.ps1
```

The M12 reset script is retained only for historical M12 report reproduction. It is no longer the default manual-test or AI regression baseline.

Delivery backups generated by `deploy/scripts/backup.ps1` are written under `.local-backups/`. The directory is git-ignored and excluded from quality-gate source scans.

Runbooks:

- `docs/05-runbooks/local-dev.md`
- `docs/05-runbooks/quality-gate.md`
- `docs/05-runbooks/data-reset.md`
- `docs/05-runbooks/local-artifacts.md`
- `docs/05-runbooks/browser-smoke.md`

IM browser smoke:

```powershell
pnpm smoke:im
```

The IM smoke script expects Docker dependencies, backend, and frontend to already be running. Use `-Headed` with `scripts/im-browser-smoke.ps1` when visual inspection is needed.
Generated Playwright artifacts under `web/test-results` and `web/playwright-report` are ignored and must not be committed.

Ops delivery scripts:

```powershell
pnpm ops:backup
pnpm ops:health
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore-drill.ps1 -BackupPath .local-backups/<timestamp> -EnvFile deploy/.env.prod
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore.ps1 -BackupPath .local-backups/<timestamp> -EnvFile deploy/.env.prod -ConfirmRestore
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1 -EnvFile deploy/.env.prod
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/rollback.ps1 -EnvFile deploy/.env.prod -GitRef <tag-or-commit> -ConfirmRollback
```

`restore.ps1` and `rollback.ps1` are destructive. Do not run them during ordinary AI verification unless the user explicitly requests a restore or rollback.
