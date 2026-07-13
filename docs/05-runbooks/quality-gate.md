---
title: Quality Gate Runbook
status: active
updated_at: 2026-07-13
---

# Quality Gate Runbook

## Purpose

This runbook defines when and how to run local quality gates. The gate is the standard verification mechanism for AI work cycles and local development.

## Quick Gate

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick
```

The direct quick gate checks:

- toolchain availability;
- Docker dependency health;
- backend compile and test-compile by default;
- frontend lint;
- frontend build;
- frontend chunk budget;
- frontend route lazy-loading;
- Mockito javaagent configuration;
- sensitive data patterns;
- security audit guardrails;
- Flyway migration order;
- generated artifact tracking;
- open implementation marker inventory;
- documentation structure;
- active work-cycle documentation and completion-evidence contract.

Use the direct quick gate for repository-wide verification. During an AI work cycle, the `light` checkpoint validates only changed backend/frontend areas; the `stage` finish runs targeted backend tests and changed frontend build; `route-final` retains the complete repository-wide gate.

Run the quick gate:

- after completing a small implementation slice;
- before saying a task is functionally complete;
- before asking the user to manually verify a changed page.

## Full Gate

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode full
```

The full gate includes the quick gate plus backend packaging. In an active AI work cycle, `stage` intentionally omits backend packaging and global static audits; it still requires the mandatory work-cycle documents and strict completion evidence. `full` requires the mandatory work-cycle documents to be changed.

Run the full gate:

- at the final milestone of the current route;
- before commit or push when practical;
- after changing shared architecture, database migrations, security, permissions, or build scripts.

Intermediate milestones run targeted tests for the changed scope. Complete backend tests, backend packaging, repository-wide static audits and full Flyway validation are deferred to route-final unless the risk of the current change requires them earlier. A stage target integration test may still start an isolated Testcontainers database and its Flyway migrations as part of that test.

## Completion Evidence Review

For work cycles started with evidence-contract v2, a `stage` or `full` finish gate independently checks the execution report after target tests and browser evidence complete. It requires one `Verification Contract` row per current task and verifies:

- the declared verification level, browser evidence type and environment are valid;
- `real` and `mock` browser evidence are not conflated;
- core closures such as authentication, permissions, resource mutations, security, handover, export and audit use `e2e-real-isolated` evidence;
- an isolated environment is actually declared for those closures;
- every expected task is Done in both the report and roadmap;
- structured Remaining Gaps do not hide unfinished acceptance work as non-blocking.

`mock` browser tests remain useful for UI-only state coverage, but never close a real API or role-based workflow. A failure in this review means the milestone remains `In Progress` or must be reopened; do not advance to the next milestone.

## Test Database Isolation

Backend integration tests run with `spring.profiles.active=test`, set by Maven Surefire. The test profile uses Testcontainers PostgreSQL through `jdbc:tc:postgresql:16:///colla_platform_test`, so quality gates do not write test fixtures into the shared local `colla_platform` database.

Do not automatically reset shared development data after a gate. There is no current standard reset fixture; M31/M40 reset and smoke scripts are historical. Integration tests create isolated data in Testcontainers.

## Security Audit Gate

The quick and full gates run `scripts/security-audit-gate.ps1`. It currently checks:

- backend tests are isolated to the test profile;
- production secrets come from environment variables;
- production config does not embed local default credentials;
- non-public routes remain authenticated;
- audit log queries require admin/manage-user permission;
- key write-heavy services call `auditService.log(...)`.

You can run it directly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/security-audit-gate.ps1
```

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
4. Do not finish the final milestone of a route while the full local quality gate is failing. For an intermediate milestone, do not finish while its required targeted checks are failing.

Quality reports are written under `.local-reports/` and must not be committed.
