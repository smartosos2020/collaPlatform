---
title: PILOT-V2-M1 Execution Report
status: archived
milestone: PILOT-V2-M1
updated_at: 2026-07-12
---

# PILOT-V2-M1 Execution Report

## Scope

- PILOT-V2-M1-T01 到 PILOT-V2-M1-T07

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| T01 | Done | `scripts/knowledge-consistency-check.ps1` covers space, tree, block, version, permission, search, object-reference and retired-model rules with risk and remediation metadata. |
| T02 | Done | The checker writes Markdown and JSON reports. Exit `0` is clean, `2` is a data inconsistency, and `1` is an execution failure. |
| T03 | Done | The sole failure was `fc80fd4b-1f5f-4cfb-a4e1-b417ce88c53f`, an M4 smoke-style `object_ref` created on 2026-07-04. Its target `c46d26d7-c7ab-4873-9573-8920beeb35bd` no longer existed and its route used retired `/docs/{id}` navigation. No source fixture still contains the id. Decision: remove the orphan entry rather than invent a replacement target. |
| T04 | Done | `scripts/repair-knowledge-reference.ps1` defaults to preview. Repair refuses non-orphans, requires `-Confirm`, and requires a validated backup dump plus SHA-256 manifest. |
| T05 | Done | A local full database backup was created and verified before the repair. The orphan entry was soft-deleted; its active block, version, active grant, search row, recent access and object-link rows were removed or revoked in one transaction. Post-repair reference, search, recent, favorite and notification counts are zero. |
| T06 | Done | `KnowledgeContentControllerIntegrationTests` now verifies that object references persist resolver-provided canonical routes and reject missing targets. The scoped Testcontainers run passed 4 tests. |
| T07 | Done | The post-repair consistency check contains zero failures. No remaining M1 data exceptions were found. |

## Code Changes

- Backend:
- Backend: `KnowledgeBaseSpaceService` validates object-reference targets through the platform resolver and persists the resolver's canonical web route instead of caller-supplied legacy routes.
- Tests: `KnowledgeContentControllerIntegrationTests` covers successful canonical route persistence and 404 for a missing `knowledge_content` target.
- Frontend:
- Database:
- Database: local runtime data only; no migration was added. The repair backup is `.local-backups/knowledge-reference-repair-20260712-030054.dump` with its SHA-256 manifest, both ignored from version control.
- Scripts: upgraded `knowledge-consistency-check.ps1`; added `repair-knowledge-reference.ps1` preview/repair tooling.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked all M1 tasks done and advanced the current entry to M2. |
| `docs/90-reports/pilot-v2-m1-execution-report.md` | Updated | Recorded baseline, repair decision, evidence and validation. |

## Validation

- Backend tests: `mvn -B -Dtest=KnowledgeContentControllerIntegrationTests test` in `server/` passed: 4 tests, 0 failures, 0 errors.
- Frontend build:
- Local quality gate: PowerShell parser validation passed for the consistency, repair and reference-inspection scripts. The post-repair consistency check passed all rules.
- Browser smoke:

## Remaining Gaps

- No M1 exceptions remain. This milestone did not change a user-facing frontend flow, so browser smoke was not applicable.

## Next Steps

- Start `PILOT-V2-M2-T01` with the minimum role matrix and isolated route-specific fixture design.
