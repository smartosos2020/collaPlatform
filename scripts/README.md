# Activity Script Platform

The only active automation runtime is the Node.js + TypeScript package at `tools/workbench`. Public commands are exposed through the root `package.json`; `scripts/` now contains only scanner policy data and this orientation document.

## Runtime Contract

- Node.js 22 or newer and pnpm are required on Windows, macOS, and Linux.
- Host commands use structured argument arrays, never interpolated shell command strings.
- Docker-internal `sh -c` is allowed only inside Linux containers where redirection or environment expansion is required.
- Generated reports, logs, pilot manifests, backups, and environment files remain under ignored `.local-*` paths.
- Any `.ps1` outside an `archive/` directory fails the full quality gate.
- Historical PowerShell is read-only evidence and must not be called by an active command or current runbook.

## Public Commands

```text
pnpm audit:snapshot -- --profile light --label before-change
pnpm work:start -- --goal M25-delivery --task-range "M25-T01 to M25-T12"
pnpm work:checkpoint -- --goal M25-delivery
pnpm work:finish -- --goal M25-delivery --backend-test-pattern ExampleIntegrationTests --browser-spec e2e/example.spec.ts --browser-evidence-kind real --browser-evidence-environment isolated
pnpm work:plan-check
pnpm work:test
pnpm verify
pnpm verify:full
pnpm security:audit
pnpm security:scan
pnpm kb:naming-guard
pnpm kb:consistency-check
pnpm kb:inspect-object-references
pnpm kb:repair-reference -- --reference-id <uuid>
pnpm smoke:im
pnpm smoke:ui-split
pnpm smoke:m5-isolated
pnpm ops:backup
pnpm ops:restore-drill -- --backup-path .local-backups/<timestamp>
pnpm ops:health
pnpm ops:contract-check
pnpm ops:release-check -- --env-file deploy/.env.prod
pnpm ops:restore -- --backup-path <path> --expected-project-name <name> --confirmation-text RESTORE:<name> --confirm-restore
pnpm ops:rollback -- --server-image <image> --web-image <image> --collaboration-image <image> --expected-project-name <name> --confirmation-text <exact-value> --confirm-rollback
pnpm pilot:contract-check
pnpm pilot:m10-contract-check
pnpm pilot:m10-simulation -- --manifest-path .local-pilot/m10.json --env-file .local-pilot/m10.env --confirmation-text RUN-SIMULATION:<pilotId>:<projectName>
pnpm pilot:check -- --manifest-path .local-pilot/pilot.json --level initialization
pnpm pilot:initialize -- --manifest-path .local-pilot/pilot.json
pnpm pilot:simulate-kickoff -- --manifest-path .local-pilot/pilot.json --backup-path <backup> --confirmation-text SIMULATE:<pilotId>
pnpm pilot:readiness -- --manifest-path .local-pilot/pilot.json --initialization-receipt-path <receipt.json> --backup-path <backup> --restore-drill-report-path <restore.md> --quality-gate-report-path <quality.md>
```

Options use lowercase kebab-case. Legacy PowerShell-style `-PascalCase` names are normalized by the CLI parser during the migration window, but new documentation and automation must use `--kebab-case`.

## Program And Stage Contract

The active planning hierarchy is `Program -> Stage -> Milestone -> Task`.

- `docs/02-roadmap/current-roadmap.md` remains the only executable route and contains exactly one Stage.
- Its front matter declares `program`, `program_doc`, `program_revision`, `stage`, and `stage_final_milestone`.
- The Program document lives under `docs/00-product/initiatives/`, contains the Stage index, points to its target architecture, and has exactly one Active Stage matching the current route.
- `work:start` rejects task IDs that are absent, already complete, or outside the active Stage.
- The final Milestone of a Stage must use `--validation-profile route-final`, mark the current route completed, and update the Program document.
- `pnpm work:plan-check` validates the contract without starting a work cycle. Quick and full quality gates run the same planning check.

## Evidence And Security

Work-cycle browser commands are structured as repeatable `--browser-spec` values plus optional `--browser-grep`. The removed free-form `BrowserTestCommand` is intentionally unsupported.

Real evidence follows static imports, dynamic imports, CommonJS `require`, re-exports, relative paths and `tsconfig` aliases, including files outside `web/e2e`. Dot and bracket forms of route interception are rejected. The only sanctioned `page.addInitScript` location remains `web/e2e/support/api.ts`.

`pnpm security:scan` recognizes source assignments plus `.env`, YAML and JSON credential forms. Environment references remain valid; fixtures require an exact path-and-rule entry in `scripts/sensitive-scan-allowlist.tsv`.

## Historical PowerShell

- `scripts/archive/windows-powershell/activity-platform-20260718/`: former work cycle, knowledge, pilot, security and smoke implementations.
- `deploy/scripts/archive/windows-powershell/activity-platform-20260718/`: former operational implementations.
- `web/e2e/archive/windows-powershell/`: former M5 isolated launcher.

These files are retained only for behavior comparison and incident archaeology. They are not supported entrypoints.
