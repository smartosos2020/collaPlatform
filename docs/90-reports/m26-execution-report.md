---
title: M26 Execution Report
status: archived
milestone: M26
updated_at: 2026-06-16
---

# M26 Execution Report

## Scope

- M26-T01 to M26-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M26-T01 | Done | IM layout keeps the conversation list constrained to viewport height with independent scrolling and collapsed-avatar mode already preserved. |
| M26-T02 | Done | Message context menu and delayed reaction picker remain active; scripted smoke verifies the message context menu opens on a sent message. |
| M26-T03 | Done | Message deep-link context endpoint and frontend focus/highlight behavior were added for `/im?conversationId=...&messageId=...`. |
| M26-T04 | Done | Existing direct/group member management behavior is covered by IM integration tests and retained during this cycle. |
| M26-T05 | Done | Internal link card rendering path remains covered in the IM UI; message context loading does not bypass link permission states. |
| M26-T06 | Done | Frontend now runs reconnect sync by `messageSeq`, merges server pages by message id, and invalidates conversation/detail caches. |
| M26-T07 | Done | Added `scripts/im-browser-smoke.ps1`, root `pnpm smoke:im`, and `web/e2e/im-smoke.spec.ts`. |
| M26-T08 | Done | `ImControllerIntegrationTests` now covers message context; backend/frontend targeted checks and IM browser smoke pass. |

## Code Changes

- Backend: added `GET /api/conversations/{conversationId}/messages/{messageId}/context` and repository/service support for message-context pages.
- Backend: extended IM integration coverage for message-context retrieval.
- Backend: made search index refresh transactional and serialized in `JdbcSearchRepository` after checkpoint exposed partial search-index reads under concurrent refresh.
- Frontend: added message deep-link loading, focus/highlight behavior, reconnect sync, message page merge-by-id, and a visible sync tag.
- Frontend: added Playwright IM smoke coverage under `web/e2e/`.
- Database: none.
- Scripts: added `scripts/im-browser-smoke.ps1` and root `pnpm smoke:im`.
- Scripts: extended `.gitignore` and quality gate generated-artifact exclusions for Playwright output directories.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked M26-T01 to M26-T08 complete. |
| `docs/01-architecture/current-architecture.md` | Updated | Reflected IM message-context endpoint and reconnect sync behavior. |
| `docs/05-runbooks/browser-smoke.md` | Updated | Added scripted IM smoke usage and artifact rule. |
| `scripts/README.md` | Updated | Added `pnpm smoke:im` usage and generated artifact note. |
| `docs/90-reports/m26-execution-report.md` | Updated | Recorded M26 delivery evidence and validation. |

## Validation

- Backend tests: `mvn -Dtest=ImControllerIntegrationTests test` passed.
- Backend regression: `mvn -Dtest=SearchCollaborationIntegrationTests test` passed after search-index refresh was made atomic.
- Frontend lint: `pnpm web:lint` passed.
- Frontend build: `pnpm web:build` passed.
- Scripted browser smoke: `pnpm smoke:im` passed after backend and frontend services were running.
- In-app browser smoke: logged in with `admin / admin123456`, opened `http://127.0.0.1:5173/im`, and verified conversation list, message list, and info bar rendered with no browser console error/warning.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M26-im-deep-polish" -GateMode quick` passed at `.local-reports/quality-gate-20260616-212404.md`.
- Work-cycle finish/full: `pnpm work:finish -- -Goal "M26-im-deep-polish"` passed at `.local-reports/quality-gate-20260616-212522.md`; backend tests, backend package, frontend lint/build, chunk budget, lazy routes, sensitive data scan, generated artifact scan, migration order, and documentation contract passed.

## Remaining Gaps

- IM smoke creates isolated local test users/conversations. It is acceptable for development verification; reset remains manual and only runs when requested.
- IM is still Web-first. Desktop/mobile shell productization remains outside M26.

## Next Steps

- Proceed to M27 project and BUG management polishing in the next independent work cycle.
