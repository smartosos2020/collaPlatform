---
title: KB-PRODUCT-M9 Execution Report
status: archived
milestone: KB-PRODUCT-M9
updated_at: 2026-07-16
---

# KB-PRODUCT-M9 Execution Report

## Scope

- KB-PRODUCT-M9-T01 to KB-PRODUCT-M9-T12

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M9-T01 | e2e-real-isolated | real | isolated | No | Type Chinese IME and sequential English while parent autosave updates; focus and text must remain stable |
| KB-PRODUCT-M9-T02 | e2e-real-isolated | real | isolated | No | Select text near editor edges; bubble toolbar appears only for a non-empty selection and stays inside the canvas |
| KB-PRODUCT-M9-T03 | e2e-real-isolated | real | isolated | No | Hover different top-level blocks; the combined line control follows the block and its menu closes outside without overflow |
| KB-PRODUCT-M9-T04 | e2e-real-isolated | real | isolated | No | Open Slash at the caret, search and navigate with arrows/Enter/Escape, then dismiss by outside click |
| KB-PRODUCT-M9-T05 | e2e-real-isolated | real | isolated | No | Duplicate, move, convert and delete real blocks; block identities remain unique and undo restores the operation |
| KB-PRODUCT-M9-T06 | e2e-real-isolated | real | isolated | No | Exercise keyboard and button undo/redo across typing and block operations without losing focus |
| KB-PRODUCT-M9-T07 | e2e-real-isolated | real | isolated | No | Paste plain text, sanitized HTML, Markdown list and tabular text and verify deterministic safe nodes |
| KB-PRODUCT-M9-T08 | e2e-real-isolated | real | isolated | No | Select a table cell, mutate rows/columns, undo, and assert the table toolbar follows the table within the canvas |
| KB-PRODUCT-M9-T09 | e2e-real-isolated | real | isolated | No | Upload a real image and file through MinIO, observe progress, edit caption, download, replace/retry behavior and readonly controls |
| KB-PRODUCT-M9-T10 | e2e-real-isolated | real | isolated | No | Insert and edit link, code, quote, task, callout and divider blocks, reload them and inspect readonly rendering |
| KB-PRODUCT-M9-T11 | e2e-real-isolated | real | isolated | No | Verify 1366px and narrow viewport containment, accessible names and keyboard-visible focus |
| KB-PRODUCT-M9-T12 | e2e-real-isolated | real | isolated | No | Run the M9 Chromium matrix for short/long content and owner/viewer identities with dynamic fixture cleanup |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M9-T01 | Done | Stable editor lifecycle and composition guard preserve continuous Chinese and English input focus. |
| KB-PRODUCT-M9-T02 | Done | Selection bubble appears only for non-empty selections and remains inside the editor canvas. |
| KB-PRODUCT-M9-T03 | Done | Combined insert/operation controls follow the active block; dropdown direction and height use canvas bounds. |
| KB-PRODUCT-M9-T04 | Done | Slash supports search, arrows, Enter, Escape, outside dismissal and caret-relative placement. |
| KB-PRODUCT-M9-T05 | Done | Duplicate, move, convert and delete use top-level blocks, fresh IDs and undo/redo. |
| KB-PRODUCT-M9-T06 | Done | Button and keyboard history work across typing, table mutations and block operations. |
| KB-PRODUCT-M9-T07 | Done | Paste sanitizes HTML and converts plain text, Markdown lists and tabular text deterministically. |
| KB-PRODUCT-M9-T08 | Done | Contextual table tools mutate rows/columns, undo and stay inside the canvas. |
| KB-PRODUCT-M9-T09 | Done | Real MinIO uploads support progress, retry, caption, replace, download and readonly controls. |
| KB-PRODUCT-M9-T10 | Done | Link, code, quote, task, callout, divider and legacy embed nodes persist through canonical schema. |
| KB-PRODUCT-M9-T11 | Done | Desktop/narrow layouts avoid document overflow and expose named, focus-visible controls. |
| KB-PRODUCT-M9-T12 | Done | Dynamic short/long documents and owner/viewer identities pass the isolated Chromium matrix. |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M9-T01 | Continuous IME/input preserves focus | Stable `useEditor` dependencies and composition guard | Frontend lint/build; M3 focus regression | Real composition, sequential English input and API persistence pass | Done |
| KB-PRODUCT-M9-T02 | Selection toolbar is contextual and bounded | Bounded `SelectionBubbleToolbar` | Production build | Non-empty/empty selection visibility and containment pass | Done |
| KB-PRODUCT-M9-T03 | Line controls follow the active block | `BlockDragHandle` computes DOM, placement and menu height | Frontend lint/build | Controls follow Beta; menu closes outside and stays inside canvas | Done |
| KB-PRODUCT-M9-T04 | Slash is searchable and keyboard complete | Caret-positioned `SlashMenu` with active index | Frontend lint/build | Search, arrows, Enter, Escape and outside dismissal pass | Done |
| KB-PRODUCT-M9-T05 | Block operations preserve identity and undo | Top-level block helpers clone IDs, move and delete exact blocks | Frontend lint/build | Duplicate uniqueness, move, convert, delete and history pass | Done |
| KB-PRODUCT-M9-T06 | Undo/redo works without focus loss | Toolbar controls and ProseMirror/Yjs history | Collaboration suite 14/14 | Keyboard/button history across typing, table and blocks passes | Done |
| KB-PRODUCT-M9-T07 | Pasted content is safe and deterministic | Structured paste parser and HTML sanitizer | Frontend lint/build | Plain, safe HTML, list and TSV table paste pass | Done |
| KB-PRODUCT-M9-T08 | Table tools follow and mutate the table | Contextual accessible `TableToolbar` | Frontend lint/build | Containment, add row/column and two-step undo pass | Done |
| KB-PRODUCT-M9-T09 | Media lifecycle uses real storage and permissions | XHR progress, top-level insertion, caption/replace/retry/download | Signed download API and canonical DB inspection | Offline retry, real image/file, replacement and readonly controls pass | Done |
| KB-PRODUCT-M9-T10 | Common blocks persist consistently | Web/collaboration node schemas and canonical top-level projection | Schema and gateway tests pass | All target blocks survive persistence, reload and readonly render | Done |
| KB-PRODUCT-M9-T11 | Desktop/narrow interactions remain usable | Responsive styles and focus-visible rules | Frontend lint/build | 1366px and 560px have no document/canvas overflow | Done |
| KB-PRODUCT-M9-T12 | Matrix is repeatable with real identities | Dynamic fixtures and explicit owner/viewer grants | M9 1/1 plus M1/M3 2/2 regressions | Short/long, owner/viewer and desktop/narrow Chromium pass | Done |

## Code Changes

- Backend: canonical collaboration projection persists only top-level business blocks while retaining nested table/list/callout nodes in rich content.
- Frontend: stabilizes input and contextual tools; adds structured paste, callout/legacy embed support, robust media insertion, progress/retry/replace/caption and bounded menus.
- Database: no migration; existing canonical, block and file tables retain the new node attributes.
- Tests: adds the M9 real-browser matrix and schema regression coverage; aligns M1/M3 tests with the single-editor collaboration contract.
- Scripts: no M9-specific script changes.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Closes M9 and advances the unique execution entry to M10. |
| `docs/90-reports/kb-product-m9-execution-report.md` | Added | Preserves task-level implementation, automated and browser evidence. |

## Validation

- Backend tests: `mvn -q "-Dtest=KnowledgeContentSchemaServiceTests,KnowledgeCollaborationGatewayServiceTests" test` passed.
- Collaboration tests: `pnpm test` passed 14/14, including convergence, Redis degradation, restart recovery and duplicate/out-of-order updates.
- Frontend build: `pnpm lint` and `pnpm build` passed.
- Local quality gate: light checkpoint passed in `quality-gate-20260716-162001.md`; AI work cycle stage finish passed with targeted backend, full frontend and real isolated browser verification.
- Browser smoke: isolated real Chromium M9 passed 1/1 in 24.1 seconds; M1/M3 editor regressions passed 2/2.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | N/A |

## Next Steps

- Continue with KB-PRODUCT-M10 comments, search, relations and import/export closure. Do not start M11 before M10 acceptance closes.
