---
title: PILOT-V2-M4 Execution Report
status: archived
milestone: PILOT-V2-M4
updated_at: 2026-07-12
---

# PILOT-V2-M4 Execution Report

## Scope

- PILOT-V2-M4-T01 to PILOT-V2-M4-T09

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M4-T01 | Done | `ImControllerIntegrationTests.convertsMessageToIssueAndSearchesMessageWorkItems` covers conversation, send, text/object search, source-message relation, issue receipt and message context; `im-smoke.spec.ts` now provides the isolated browser route. |
| PILOT-V2-M4-T02 | Done | `ProjectControllerIntegrationTests` covers project creation, member access, issue states, comments, mentions and non-member rejection. |
| PILOT-V2-M4-T03 | Done | Added `recordsFailedVerificationBeforeTheBugIsFixedAndVerified`, proving `resolved -> failed verification -> in_progress -> resolved -> passed -> closed` preserves ordered verification history. |
| PILOT-V2-M4-T04 | Done | `BaseControllerIntegrationTests` already covers Base/table/field/view/record and successful import; it now asserts malformed numeric CSV produces `created=0` with one row error. |
| PILOT-V2-M4-T05 | Done | `ApprovalControllerIntegrationTests.approvalTodoNotificationImCardAndActionsFlow` covers submission, todo, notification, approve, withdraw, transfer and reject. |
| PILOT-V2-M4-T06 | Done | `SearchCollaborationIntegrationTests` covers knowledge, issue, Base/table/record and message search results, object availability and outsider filtering. |
| PILOT-V2-M4-T07 | Done | `WorkspaceControllerIntegrationTests` covers object summaries, navigation, internal links, recent access and favorites. |
| PILOT-V2-M4-T08 | Done | `CrossBoundaryRulesIntegrationTests` verifies user/admin API separation; `ui-split-v1-smoke.spec.ts` now clicks `返回工作台` and reasserts the user workspace. |
| PILOT-V2-M4-T09 | Done | Added `cross-module-route-final.spec.ts`; isolated `@route-final M4` runs cover global search across knowledge, issue, Base and message, message-to-issue conversion, and user/admin UI boundaries. The three-test collection passed twice against disposable services. |

## Code Changes

- Backend: Added BUG re-verification and CSV partial-failure assertions to existing isolated integration suites.
- Frontend: Reclassified the IM conversion browser flow as isolated `@route-final`; completed the real admin-to-workspace return assertion; added deterministic cross-module search and issue-navigation coverage with index-readiness polling.
- Database: None.
- Scripts: None.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked M4 T01-T09 complete, recorded isolated route-final evidence and synchronized the migration baseline to V048. |
| `docs/90-reports/pilot-v2-m4-execution-report.md` | Updated | Recorded task evidence, validations and isolated-browser execution details. |

## Validation

- Backend tests: `mvn -q "-Dtest=ImControllerIntegrationTests,ProjectControllerIntegrationTests,BaseControllerIntegrationTests,ApprovalControllerIntegrationTests,SearchCollaborationIntegrationTests,WorkspaceControllerIntegrationTests,CrossBoundaryRulesIntegrationTests" test` passed, 18 tests; Testcontainers migrated an empty database through V048.
- Frontend build: `pnpm --dir web lint` and `pnpm --dir web build` passed.
- Local quality gate: M4 checkpoint (`light`) and finish (`stage`) passed. Finish also completed `mvn -DskipTests package`, migration ordering, security audit, knowledge naming and work-cycle document-contract gates.
- Browser smoke: `COLLA_E2E_SUITE=smoke pnpm exec playwright test --config e2e/playwright.config.ts` from `web` passed, 4/4. Route-final test listing confirms the IM conversion path is excluded from normal smoke and included only in route-final.
- Isolated route-final: started disposable PostgreSQL `15432`, Redis `16379`, MinIO `19000/19001`, backend `18080` and frontend `15173`; with `COLLA_E2E_ISOLATED=true`, `COLLA_E2E_API_BASE_URL=http://127.0.0.1:18080/api`, `COLLA_E2E_WEB_BASE_URL=http://127.0.0.1:15173`, `pnpm exec playwright test --config e2e/playwright.config.ts --grep M4` passed 3/3 twice.
- Final checkpoint: `scripts/ai-work-cycle.ps1 -Stage checkpoint -Goal PILOT-V2-M4 -TaskRange "PILOT-V2-M4-T09" -ValidationProfile light` passed, including backend compile, frontend lint/build, migration ordering and security gates.

## Remaining Gaps

- None for M4. Route-final fixtures only ran against the disposable environment and did not use shared development data.

## Next Steps

- Start `PILOT-V2-M5-T01` and preserve the M4 route-final isolation contract for future cross-module browser changes.
