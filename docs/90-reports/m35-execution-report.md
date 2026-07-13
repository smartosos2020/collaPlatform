---
title: M35 Execution Report
status: archived
milestone: M35
updated_at: 2026-06-18
---

# M35 Execution Report

## Scope

- M35-T01 到 M35-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M35-T01 | Done | Messenger page now groups conversations into pinned/recent sections while preserving pin, mute, close, leave, collapse, and scroll behavior. |
| M35-T02 | Done | Added `POST /api/conversations/{conversationId}/messages/{messageId}/convert-to-issue`; created issues relate back to the source message and write activity, event, audit, and source-conversation receipt message. |
| M35-T03 | Done | IM link parsing now supports `/im?conversationId=...&messageId=...`; object cards self-render unavailable access states. |
| M35-T04 | Done | Existing member `@` suggestions, mention events, unread marker, and full-message-visible read behavior were preserved and covered by IM regression tests. |
| M35-T05 | Done | Message right-click menu now includes copy link, alongside existing pin, convert, edit, revoke, and reaction hover behavior. |
| M35-T06 | Done | Added conversation message search API and UI with text query, object-type filter, and click-to-message context positioning. |
| M35-T07 | Done | IM responsive CSS now constrains narrow screens, search results, message list, and composer for mobile Web core flows. |
| M35-T08 | Done | Roadmap, product scope, architecture, platform object model, E2E smoke specs, and this report were updated. |

## Code Changes

- Backend: added IM message search, message-link parsing for `/im?...messageId=...`, and message-to-issue conversion through `ProjectService`.
- Frontend: added conversation groups, message search/filter UI, click-to-context results, copy message link, message-to-issue conversion through the new API, and stronger object-card access-state rendering.
- Database: no new migration; reused existing `message_links`, `object_links`, and `issue_relations`.
- Scripts: updated IM Playwright smoke and M31 explicit-regression assertions; did not run M31 reset or M31 smoke by default.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Mark M35 tasks complete and reflect IM work-entry capability. |
| `docs/00-product/current-product-scope.md` | Updated | Reflect current IM search, conversion, link-card, and conversation organization behavior. |
| `docs/01-architecture/current-architecture.md` | Updated | Document IM search and message-to-issue API contracts plus validation result. |
| `docs/01-architecture/platform-object-model.md` | Updated | Document message object summaries and issue relations to messages. |
| `docs/90-reports/m35-execution-report.md` | Updated | Record M35 execution evidence and validation. |

## Validation

- Backend tests: `mvn -Dtest=ImControllerIntegrationTests test` passed.
- Cross-module backend tests: `mvn -Dtest=ProjectControllerIntegrationTests test` passed.
- Frontend lint: `pnpm web:lint` passed.
- Frontend build: `pnpm web:build` passed.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M35-im-work-entry" -GateMode quick` passed.
- Work-cycle finish: `pnpm work:finish -- -Goal "M35-im-work-entry"` passed.
- Browser smoke: `pnpm smoke:im` passed after restarting local backend/frontend; covered login, sending a message, current-conversation search, right-click conversion to issue, project selection, success toast, and source-conversation receipt message.
- M31 baseline: not run by default; M31 smoke assertions were updated for explicit future runs only.

## Remaining Gaps

- Message-to-issue conversion uses project editor permission; finer per-action role rules can be aligned with later permission refinement.
- Conversation search is scoped to a single conversation; global search remains in `/search`.
- Attachment upload entry in IM composer is still a future capability.

## Next Steps

- M36 should continue with notification, search, permission explanation, and audit unification.
