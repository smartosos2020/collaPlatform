---
title: KB-PRODUCT-M10 Execution Report
status: archived
milestone: KB-PRODUCT-M10
updated_at: 2026-07-16
---

# KB-PRODUCT-M10 Execution Report

## Scope

- KB-PRODUCT-M10-T01 to KB-PRODUCT-M10-T10

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M10-T01 | e2e-real-isolated | real | isolated | No | Create a selection comment, edit before/inside/after its text from two real clients, and verify the anchor follows the intended text |
| KB-PRODUCT-M10-T02 | e2e-real-isolated | real | isolated | No | Create block and selection comments, reply, mention a real member, resolve/reopen, inspect notification/audit and open the deep link without duplicate reminders |
| KB-PRODUCT-M10-T03 | e2e-real-isolated | real | isolated | No | Delete an anchored block and restore an older version; comments retain quoted context and report detached/stale state instead of attaching elsewhere |
| KB-PRODUCT-M10-T04 | integration | not-required | isolated | No | Rebuild/search canonical title, block plain-text projection, tags and permitted comments while excluding deleted/private text |
| KB-PRODUCT-M10-T05 | e2e-real-isolated | real | isolated | No | Search Chinese terms and exact phrases with space/type/tag/maintainer/status filters and inspect deterministic highlights |
| KB-PRODUCT-M10-T06 | e2e-real-isolated | real | isolated | No | Open block/comment search results into highlighted anchors; remove permission and prove title/snippet/path no longer leak |
| KB-PRODUCT-M10-T07 | e2e-real-isolated | real | isolated | No | Create, navigate and remove forward/reverse relations, attempt a cycle, and verify bounded rendering and permission-filtered locations |
| KB-PRODUCT-M10-T08 | e2e-real-isolated | real | isolated | No | Import Markdown and HTML containing tables, lists, code, images and unsupported content; inspect conversion/degradation report and readonly failure behavior |
| KB-PRODUCT-M10-T09 | e2e-real-isolated | real | isolated | No | Export Markdown, HTML and a space package with structure, attachments and object references; re-import and compare canonical content |
| KB-PRODUCT-M10-T10 | e2e-real-isolated | real | isolated | No | Run dynamic real-stack comment/search/relation/import-export flows with permissions, concurrent edits, malformed input, large content and cleanup |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M10-T01 | Done | Selection anchors re-map by quoted text plus prefix/suffix context and advance their version/position after canonical or collaborative saves. |
| KB-PRODUCT-M10-T02 | Done | Existing thread reply/resolve/reopen/audit/deep-link flow is retained; real-member mention notification is permission checked and deduplicated. |
| KB-PRODUCT-M10-T03 | Done | Anchor lifecycle persists `active/detached`, invalid reason and update time; block deletion detaches and version restore reactivates the original anchor. |
| KB-PRODUCT-M10-T04 | Done | Search rebuild uses title, canonical block `plain_text`, tags and permitted comments; archived/deleted rows are excluded. |
| KB-PRODUCT-M10-T05 | Done | Chinese/phrase fallback, space/type/tag/maintainer/status filters and client-visible deterministic highlighting are complete. |
| KB-PRODUCT-M10-T06 | Done | Search resolves matching block/comment deep links and re-checks access on every result; revoked subjects disappear after index refresh. |
| KB-PRODUCT-M10-T07 | Done | Knowledge-content relations create a bounded direct reverse edge and support idempotent soft-delete of both directions. |
| KB-PRODUCT-M10-T08 | Done | Markdown/HTML import converts tables, lists, fenced code and images, sanitizes unsafe HTML and returns conversion/degradation previews. |
| KB-PRODUCT-M10-T09 | Done | Markdown/HTML exports retain table rows/images/object directives; manifests and space export include structure, counts and fingerprints. |
| KB-PRODUCT-M10-T10 | Done | Dynamic PostgreSQL and real Chromium scenarios cover concurrent clients, permission revocation, malformed/large input and fixture cleanup. |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M10-T01 | Anchor follows intended text after edits | Context-scored remapping updates thread range/state/version | M10 knowledge integration test | Two live Chromium clients insert before the anchor and observe the mapped range | Done |
| KB-PRODUCT-M10-T02 | Thread lifecycle, mentions and deep links close | Existing comment service plus persisted lifecycle fields and dedupe event keys | M10 search test verifies one real-member mention; canonical regression covers reply/resolve/reopen | Comment thread and deep link remain visible in real browser | Done |
| KB-PRODUCT-M10-T03 | Deleted/restored anchors retain context | V054 lifecycle columns; delete marks `block_deleted`; restore reanchors original block | M10 knowledge integration test | Real browser shows `锚点已失效`, keeps quote/comment, then clears it after restore | Done |
| KB-PRODUCT-M10-T04 | Canonical searchable projection | Search index aggregates block plain text, tags and allowed comments | M10 search integration test | Search result is produced from the current canonical block | Done |
| KB-PRODUCT-M10-T05 | Chinese, phrase, filters and highlights | Existing SQL fallback/filters plus search filter bar and `mark` rendering | Chinese/combined-filter integration assertions | Chromium verifies highlighted phrase and visible filter controls | Done |
| KB-PRODUCT-M10-T06 | Block/comment deep links with no leak | Lateral hit location plus per-result object resolver and SQL permission predicate | Block/comment paths and post-revoke empty result asserted | Chromium opens the block hash from the result | Done |
| KB-PRODUCT-M10-T07 | Forward/reverse relations are removable and bounded | Symmetric direct knowledge edges, soft-delete API and relation-panel remove action | Both sides asserted before/after delete | Chromium expands the governance panel, confirms UI removal and verifies the reverse side disappears | Done |
| KB-PRODUCT-M10-T08 | Structured imports report conversion/degradation | Stateful Markdown parser, HTML table/image parser and preview endpoint | Table/code/image and unsafe HTML assertions | Chromium verifies preview and oversized input rejection | Done |
| KB-PRODUCT-M10-T09 | Exports retain structure and are comparable | Table rows, image URLs, object directives, transfer manifest and space summary/path metadata | Markdown round-trip content and manifest assertions | Real-stack export APIs exercised in M10 flow | Done |
| KB-PRODUCT-M10-T10 | Repeatable end-to-end matrix | Isolated fixtures, cleanup and layered validation | Two focused Testcontainers methods pass | M10 isolated Chromium 1/1 passes | Done |

## Code Changes

- Backend: comment anchor lifecycle/remapping, relation removal/reverse edges, import preview/export manifest, structured Markdown/HTML conversion and space export manifest.
- Frontend: detached-anchor status, search filter bar, deterministic result highlighting, block/comment deep-link consumption and confirmed relation removal.
- Database: `V054__add_knowledge_comment_anchor_lifecycle.sql` adds lifecycle state, invalid reason, timestamp, constraint and lookup index.
- Tests: focused knowledge/search Testcontainers coverage and `kb-product-m10-content-workflows.spec.ts` real Chromium coverage.
- Scripts: no M10-specific script changes.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Closes M10 and moves the unique execution entry to M11. |
| `docs/90-reports/kb-product-m10-execution-report.md` | Added | Preserves task-level implementation, automated and browser evidence. |

## Validation

- Backend tests: focused M10 methods in `KnowledgeContentControllerIntegrationTests` and `SearchCollaborationIntegrationTests` passed against real PostgreSQL/Flyway.
- Frontend build: `pnpm lint` and `pnpm build` passed.
- Local quality gate: final stage gate passed in `quality-gate-20260716-185304.md` with no warnings or failures; both focused backend methods, frontend lint/build, chunk budget and route lazy-loading passed.
- Browser smoke: isolated real Chromium M10 passed 1/1 in 22.3 seconds (`work-cycle-browser-20260716-185241.log`) after UI relation removal, fixture and collaboration-session cleanup.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | N/A |

## Next Steps

- Continue with KB-PRODUCT-M11 permission, governance, performance and accessibility closure. Do not start M12 before M11 acceptance closes.
