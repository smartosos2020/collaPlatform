---
title: M45 Execution Report
status: archived
milestone: M45
updated_at: 2026-06-20
---

# M45 Execution Report

## Scope

- M45-T01 到 M45-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M45-T01 | Done | Added `V030__extend_document_comment_threads.sql`; old rows become root threads and new rows support `thread_id`, `parent_comment_id`, anchor fields and reopen metadata. |
| M45-T02 | Done | `AddDocumentCommentRequest` accepts selection range, excerpt and context; `DocEditor` captures Tiptap selection into a comment anchor. |
| M45-T03 | Done | `DocumentDetail.comments` returns unified root threads for full-document, block and selection comments. |
| M45-T04 | Done | `DocEditor` uses a ProseMirror decoration extension to highlight unresolved selection anchors. |
| M45-T05 | Done | Comment panel prioritizes comments overlapping the current selection and clicking a thread locates its selection/block. |
| M45-T06 | Done | Added reply, resolve and reopen APIs; replies render nested under the root thread. |
| M45-T07 | Done | Mention notification deep links now point to the thread id: `/docs/{documentId}?commentId={threadId}`. |
| M45-T08 | Done | Added `requireComment`; this milestone maps comment capability to edit/manage, leaving the standalone comment level for M46. |

## Code Changes

- Backend: extended comment domain records, repository SQL, service rules and controller routes for thread/reply/anchor/reopen.
- Frontend: extended `docsApi`, `DocsPage` comment panel and `DocEditor` selection anchor/highlight behavior.
- Database: added `V030__extend_document_comment_threads.sql`.
- Scripts: none.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked M45-T01 through M45-T08 execution evidence. |
| `docs/01-architecture/current-architecture.md` | Updated | Documented comment thread/anchor API and migration changes. |
| `docs/90-reports/m45-execution-report.md` | Updated | Captured implementation and validation evidence. |

## Validation

- Backend targeted tests: `mvn -f server/pom.xml -Dtest=DocumentControllerIntegrationTests test` passed; 6 tests, 0 failures, 0 errors. First sandbox attempt failed because Testcontainers could not access Docker, then passed with Docker access.
- Frontend lint: `pnpm web:lint` passed with 3 existing exhaustive-deps warnings in `useDocumentCollaboration`.
- Frontend build: `pnpm web:build` passed.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M45-document-comments" -GateMode quick` passed; backend 39 tests, frontend lint/build, chunk budget, route lazy-loading, security audit, Flyway order, generated artifact scan, TODO inventory and documentation contract all passed.
- Work-cycle finish: `pnpm work:finish -- -Goal "M45-document-comments"` passed; backend 39 tests, backend package, frontend lint/build, chunk budget, route lazy-loading, security audit, Flyway order, generated artifact scan, TODO inventory and documentation contract all passed.
- Browser smoke: not run separately in this sub-step; frontend behavior is covered by build and backend comment API integration, with M45-T10 E2E explicitly outside this task range.

## Remaining Gaps

- Selection anchors are range-based against the current ProseMirror document; collaborative anchor drift across concurrent text edits remains M45-T09.
- Dedicated Playwright coverage for selection comment, reply, resolve, mention and deep link remains M45-T10.

## Next Steps

- Proceed to M46-T01 through M46-T08.
