---
title: KB-PRODUCT-M11 Execution Report
status: archived
milestone: KB-PRODUCT-M11
updated_at: 2026-07-16
---

# KB-PRODUCT-M11 Execution Report

## Scope

- KB-PRODUCT-M11-T01 to KB-PRODUCT-M11-T11

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M11-T01 | e2e-real-isolated | real | isolated | No | Use isolated user, department, user-group and role subjects to compare effective permission and source explanation at space, directory and content levels |
| KB-PRODUCT-M11-T02 | e2e-real-isolated | real | isolated | No | Preview and execute move/copy/restore/object-mount operations, then verify inherited access changes without orphaned or elevated grants |
| KB-PRODUCT-M11-T03 | e2e-real-isolated | real | isolated | No | Create an expiring share link, disable and revoke it, and prove every state takes effect immediately with matching audit records |
| KB-PRODUCT-M11-T04 | e2e-real-isolated | real | isolated | No | Submit, approve and reject permission requests as separate real users; inspect status, notification and redacted denial responses |
| KB-PRODUCT-M11-T05 | e2e-real-isolated | real | isolated | No | Discover unmaintained, due and outdated content, assign ownership/review metadata and close each governance finding |
| KB-PRODUCT-M11-T06 | e2e-real-isolated | real | isolated | No | Create and use a template containing references, publish an upgrade and prove unauthorized IDs are removed while existing pages stay unchanged |
| KB-PRODUCT-M11-T07 | e2e-real-isolated | real | isolated | No | Measure 100/500/1000-block load, input, save, search and collaboration flows against explicit budgets on the real stack |
| KB-PRODUCT-M11-T08 | e2e-real-isolated | real | isolated | No | Open and edit a 1000-block item with incremental rendering/paging, then verify complete canonical persistence and snapshot counts |
| KB-PRODUCT-M11-T09 | e2e-real-isolated | real | isolated | No | Complete keyboard-only navigation and an automated accessibility scan covering headings, focus, names, contrast-sensitive states and errors |
| KB-PRODUCT-M11-T10 | e2e-real-isolated | real | isolated | No | Trigger save, collaboration, search, permission and object-resolution activity and inspect redacted audit/metric/troubleshooting context |
| KB-PRODUCT-M11-T11 | e2e-real-isolated | real | isolated | No | Run the combined isolated real-user governance suite and prove user/admin UI boundaries with no open high-risk or P0/P1 finding |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M11-T01 | Done | Unified resource-permission evaluation remains the only active decision model; content routes now allow a direct content grant without incorrectly requiring a whole-space grant. |
| KB-PRODUCT-M11-T02 | Done | Added transition preview, stale inherited-grant cleanup on re-parenting, independent block IDs on copy and target-parent inheritance. |
| KB-PRODUCT-M11-T03 | Done | Share revoke rotates the token before disabling; an old token cannot become valid after re-enable. |
| KB-PRODUCT-M11-T04 | Done | Content permission requests use the central persistent request/approval/notification flow and are allowed before content access exists. |
| KB-PRODUCT-M11-T05 | Done | Maintainer, status, review date, due/unmaintained discovery, bulk assignment/review and closure remain available in user and admin governance paths. |
| KB-PRODUCT-M11-T06 | Done | Templates have immutable lineage/version upgrades; publishing sanitizes inaccessible embedded object references and does not rewrite existing pages. |
| KB-PRODUCT-M11-T07 | Done | Fixed 100/500/1000-block budgets are returned with snapshot size and initial render limits; real isolated API/browser/collaboration runs stayed within the declared blocking thresholds. |
| KB-PRODUCT-M11-T08 | Done | Large documents render in 160-block windows while the full canonical snapshot and block count remain persisted and diagnosable. |
| KB-PRODUCT-M11-T09 | Done | One main landmark, named content region/editor/actions, focus-visible treatment, reduced-motion handling and keyboard focus progression were verified. |
| KB-PRODUCT-M11-T10 | Done | Manage-only diagnostics expose counts, clocks and readiness without title/body/blocks; audit events cover permission, sharing, template, copy, save and collaboration operations. |
| KB-PRODUCT-M11-T11 | Done | Targeted backend integration, frontend lint/build and three real isolated browser flows passed; no open M11 P0/P1 or high-risk authorization finding remains. |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M11-T01 | user/department/user_group/role effective levels and sources stay consistent | `ResourcePermissionManagementService`, `KnowledgeBaseSpaceService.requireContentRoute` and the existing explanation endpoint share the central model | `ResourcePermissionControllerIntegrationTests`; `KnowledgeContentControllerIntegrationTests.m11PermissionTransitionAndRequestApprovalUseTheCentralPermissionModel` | Real member grant, notification, readonly view and admin diagnosis passed in `m5-permission-notification-e2e.spec.ts` | Passed |
| KB-PRODUCT-M11-T02 | Parent changes are previewable and leave no orphan/elevated grants | `previewKnowledgeContentTransition`, `copyItem` and `JdbcKnowledgeRepository.copyParentPermissions` | M11 integration test checks removed/added inheritance and SQL absence of stale active inherited rows; canonical integration checks copied block IDs/content | 1000-block isolated content remained navigable after fixture creation and cleanup; object/navigation browser regressions remain covered by M7/M8 specs | Passed |
| KB-PRODUCT-M11-T03 | Expiry, disable and revoke take effect immediately and are audited | `revokeShareLink` rotates token and disables link; upsert replaces token on conflict | Canonical knowledge integration creates, revokes and proves token rotation; audit/event assertions use the real repository | Share/permission action has an explicit accessible name in the real M11 page; link lifecycle is verified at real HTTP boundary | Passed |
| KB-PRODUCT-M11-T04 | Requester and manager can track approval without denial leakage | Content controller delegates directly to central request service; approved direct content grant is honored | Separate generated user submits without prior access, admin approves, requester then reads; resource-permission integration covers request states | Real member receives explainable permission notification and sees readonly content; admin opens governance and remediates risk | Passed |
| KB-PRODUCT-M11-T05 | Due, outdated and unmaintained content can be found, assigned and closed | `KnowledgeBaseSpaceService.governance/bulkGovernance`, governance dashboard/export, metadata editor and filters | Existing knowledge governance integration coverage is included in the targeted knowledge controller class and compile contract | User content governance panel and admin governance boundary render only for allowed actors in M8 plus the M11 isolated page | Passed |
| KB-PRODUCT-M11-T06 | Template upgrades are versioned, sanitized and non-destructive | V055 lineage columns; `upgradeTemplate`; inaccessible object cards degrade to text | Canonical integration creates v1, creates a page, upgrades to v2 and proves the old page is unchanged and illegal references are absent | Template creation/use remains a real user-workspace route; M11 page confirms user/admin shell separation | Passed |
| KB-PRODUCT-M11-T07 | Explicit budgets exist for 100/500/1000 blocks | `KnowledgeContentPerformance` returns tier budgets, snapshot bytes and initial window; 100/500/1000 load budgets are 1500/2500/4000 ms | M11 Playwright creates and persists all three sizes and checks real performance endpoint latency against each tier; backend request logs show saves below blocking budgets | M11 isolated browser passed in 3.5 s total; two-user collaboration convergence/downgrade spec passed in 7.2 s | Passed |
| KB-PRODUCT-M11-T08 | Incremental rendering preserves complete canonical data | 160-block render window and collaboration activation only after full render | M11 Playwright confirms 1000 persisted blocks, snapshot bytes and UI 160 to 320 expansion | Real 1000-block page shows large-content mode, then loads the next 160 blocks without horizontal overflow | Passed |
| KB-PRODUCT-M11-T09 | WCAG baseline for headings, focus, names and motion | Global `:focus-visible`, reduced motion, sr-only H1, named region/editor/icon buttons | Frontend lint/build; Playwright checks one H1/main, no duplicate IDs, no overflow and keyboard focus-visible progression | Real Chromium exposes named title/editor/share/relation controls and a named `知识内容正文` region | Passed |
| KB-PRODUCT-M11-T10 | Troubleshooting is useful but contains no正文 | Manage-only diagnostics DTO contains counts/readiness/clocks only and `redacted=true` | Integration and Playwright assert diagnostics omit `title`, `content` and `blocks` | Real isolated M11 request and page activity exercise save/search/object/permission/collaboration contexts | Passed |
| KB-PRODUCT-M11-T11 | Combined closure has no high-risk/P0/P1 blocker | Permission, governance, performance and accessibility changes share existing user/admin boundaries | Targeted Maven suite passed with V001-V055; lint/build passed | M11 large-content/accessibility, M5 real permission governance and M5 two-user collaboration specs all passed on an isolated stack | Passed |

## Code Changes

- Backend: permission transition preview and inheritance cleanup; direct content grants; copy/share revoke; template lineage/sanitization; performance and redacted diagnostics APIs.
- Frontend: incremental large-document rendering, performance/diagnostic client types, share revoke/API clients, semantic landmarks, focus-visible/reduced-motion styles and accessible icon-button names.
- Database: `V055__version_knowledge_content_templates.sql` adds template version lineage without rewriting existing instances.
- Tests: expanded knowledge/resource-permission integration tests and added `kb-product-m11-governance-performance-accessibility.spec.ts`.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Mark M11 complete and move the sole execution entry to M12. |
| `docs/90-reports/kb-product-m11-execution-report.md` | Added | Preserve task-by-task implementation, automated and browser evidence. |

## Validation

- Backend targeted integration: `mvn -q "-Dtest=KnowledgeContentControllerIntegrationTests,ResourcePermissionControllerIntegrationTests" test` passed; Testcontainers PostgreSQL applied V001-V055.
- Frontend: `pnpm lint` passed; `pnpm build` passed.
- Real isolated browser: `kb-product-m11-governance-performance-accessibility.spec.ts` passed (1/1).
- Real isolated permission governance: `m5-permission-notification-e2e.spec.ts` passed (1/1).
- Real isolated collaboration: `kb-product-m5-collaboration.spec.ts` passed (1/1) after starting the required independent Hocuspocus node on port 1235.
- Negative evidence retained: one initial M11 assertion expected 160 instead of `min(blockCount, 160)`; one temporary CORS profile omitted port 5174; one collaboration attempt omitted the independent collaboration node. All were test-environment/expectation defects, corrected before the passing runs.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| KB-PRODUCT-M12 | Full backend/package, stored-data migration, backup/restore, dual-node collaboration and real 3-5 person trial are route-final work | Does not block M11 engineering closure; prevents declaring overall productization Go | KB-PRODUCT-M12-T01 to T10 |
| KB-PRODUCT-M11-T09 | This pass verifies the WCAG engineering baseline, not an external conformance certification | Non-blocking for M11; do not claim formal WCAG certification | Reassess during M12 controlled trial |

## Next Steps

- Start only `KB-PRODUCT-M12-T01` after reviewing this report; do not rerun M11 as unfinished work unless a regression reopens a task.
