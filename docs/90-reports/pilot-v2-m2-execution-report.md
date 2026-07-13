---
title: PILOT-V2-M2 Execution Report
status: archived
milestone: PILOT-V2-M2
updated_at: 2026-07-12
---

# PILOT-V2-M2 Execution Report

## Scope

- PILOT-V2-M2-T01 到 PILOT-V2-M2-T09

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| T01 | Done | `web/e2e/support/roles.ts` defines administrator, member, editor, viewer and outsider credentials. Non-admin roles require explicit environment configuration. |
| T02 | Done | `web/e2e/support/fixtures.ts` generates unique `PW_` names, archives each knowledge-space fixture, verifies it is absent from active listings, and rejects dynamic identity fixtures unless `COLLA_E2E_ISOLATED=true`. |
| T03 | Done | `web/e2e/support/api.ts` centralizes API login, browser-token installation and bearer headers. Fixture create/archive helpers centralize workspace cleanup. |
| T04 | Done | `web/e2e/support/pageObjects.ts` provides login, user-workspace and admin-console page objects using semantic labels/placeholders with optional test ids for new builds. |
| T05 | Done | Playwright now retains trace, video and screenshot on failure. `diagnostics.ts` attaches console errors, API 4xx/5xx request summaries and final URL for failed tests. |
| T06 | Done | `permissions.ts` provides common API denial, login-required and forced session-expiry assertions. |
| T07 | Done | The UI-split smoke no longer directly uses page-state CSS selectors or ad hoc response collectors; it uses page objects, condition waits and shared diagnostics. |
| T08 | Done | `COLLA_E2E_SUITE=smoke|route-final` selects tagged suites. The default is `@smoke`, runs serially and does not execute archived M31/M40 scenarios. |
| T09 | Done | `knowledge-fixture-lifecycle.spec.ts` creates a unique private knowledge space, verifies it appears in the active list, archives it in `finally`, verifies it is absent, and compares the final active-space ID set to the pre-test baseline. |

## Code Changes

- Backend:
- Frontend: added stable `data-testid` anchors to the user and admin shell boundaries and the user account trigger for current builds; configured Playwright with one worker so cleanup-bearing fixture scenarios cannot overlap.
- Database:
- Scripts: none. The E2E harness is project test code under `web/e2e/`.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked M2 T01-T08 complete; T09 remains the explicit cleanup verification step. |
| `docs/90-reports/pilot-v2-m2-execution-report.md` | Updated | Recorded automation design and first smoke evidence. |

## Validation

- Backend tests:
- Frontend build: `pnpm --dir web build` passed.
- Local quality gate: `pnpm --dir web lint` passed with the existing 3 collaboration Hook dependency warnings, which are scheduled for M3-T03.
- Browser smoke: `pnpm --dir web exec playwright test --config=e2e/playwright.config.ts` passed 2 `@smoke` tests against the default `http://127.0.0.1:5173` UI and API. The fixture lifecycle test confirmed the active-space ID set returned to its baseline after archive cleanup.

## Remaining Gaps

- An independent Vite instance on `5174` could not authenticate because the current backend CORS allow-list only accepts the default frontend origin. This is an environment configuration limitation, not a regression in the default route. Archived fixture rows are intentionally retained as audit history; the active user-visible data set returns to its baseline.

## Next Steps

- Start M3 with the canonical knowledge-space navigation flow.
