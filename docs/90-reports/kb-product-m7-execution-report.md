---
title: KB-PRODUCT-M7 Execution Report
status: archived
milestone: KB-PRODUCT-M7
updated_at: 2026-07-16
---

# KB-PRODUCT-M7 Execution Report

## Scope

- KB-PRODUCT-M7-T01 to KB-PRODUCT-M7-T10

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M7-T01 | integration | not-required | isolated | No | Reproduce legacy manual ID/route and split Base-create/attach failure boundaries |
| KB-PRODUCT-M7-T02 | e2e-real-isolated | real | isolated | No | Admin sees owned objects while a real member session cannot discover their titles |
| KB-PRODUCT-M7-T03 | e2e-real-isolated | real | isolated | No | Use search/recent picker UI without UUID or route fields |
| KB-PRODUCT-M7-T04 | e2e-real-isolated | real | isolated | No | Select a project and create-and-attach a Base, then open each immediately |
| KB-PRODUCT-M7-T05 | e2e-real-isolated | real | isolated | No | Resolver filters member choices; integration rejects forged IDs/routes |
| KB-PRODUCT-M7-T06 | e2e-real-isolated | real | isolated | No | Preserve a user alias and expose editable title/display strategies |
| KB-PRODUCT-M7-T07 | integration | not-required | isolated | No | Exercise available, forbidden, disabled, deleted, not_found and invalid decisions |
| KB-PRODUCT-M7-T08 | integration | not-required | isolated | No | Rename, archive and restore targets while resolving current title/path/state |
| KB-PRODUCT-M7-T09 | e2e-real-isolated | real | isolated | No | Create real entries, assert audit rows, and verify permission-filtered reverse references |
| KB-PRODUCT-M7-T10 | e2e-real-isolated | real | isolated | No | Run the M7 API matrix and isolated browser workflow against the full local stack |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M7-T01 | Done | Root cause was the combination of hand-entered IDs/routes, incomplete project/file object registration, and two-request Base create/attach without rollback. |
| KB-PRODUCT-M7-T02 | Done | `/api/platform/object-choices` returns only resolver-approved `available` objects and paginates after permission filtering. |
| KB-PRODUCT-M7-T03 | Done | Reusable `PlatformObjectPicker` supports Base, project, file and knowledge content with all/recent, search and pagination. |
| KB-PRODUCT-M7-T04 | Done | `/items/base-entry` creates Base and entry in one transaction; existing Base attach uses the same path and browser creation opens current targets directly. |
| KB-PRODUCT-M7-T05 | Done | Object entries ignore client routes, validate supported type/ID/access through resolvers and persist the canonical route. |
| KB-PRODUCT-M7-T06 | Done | Manual/follow-target/alias title strategies and default/inline/preview/link modes are visible and editable; user-entered aliases are preserved. |
| KB-PRODUCT-M7-T07 | Done | `disabled` joins the existing state model; unavailable cards use state-specific, title-free text and actions are disabled. |
| KB-PRODUCT-M7-T08 | Done | Hydration uses current resolver title/path, preserves validated Base subroutes, and follows archive/restore lifecycle changes. |
| KB-PRODUCT-M7-T09 | Done | Entry create/update events carry target relation metadata and a protected reverse-reference API returns only legal knowledge locations. |
| KB-PRODUCT-M7-T10 | Done | Dedicated Testcontainers integration coverage and a dynamic Playwright fixture pass for all selectable types, external links, lifecycle states and core UI flows. |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M7-T01 | Identify registration, ID, route, transaction and permission causes | Project/file registration, V053 backfill, canonical resolver flow and atomic endpoint address each cause | `KnowledgeObjectEntryIntegrationTests` reproduces forged route and rollback boundaries | Browser exposed no manual UUID/route fields | Done |
| KB-PRODUCT-M7-T02 | Only currently referenceable objects are returned | `PlatformObjectService.choices` resolves every candidate before paging | Admin/member Testcontainers assertions prove title non-disclosure | Isolated member session cannot discover admin project/Base titles | Done |
| KB-PRODUCT-M7-T03 | Four object types are selectable by search/recent/page | `PlatformObjectPicker` and choice-page API | Integration asserts Base/project/file/knowledge candidates | Real project is searched and selected; all/recent controls are present | Done |
| KB-PRODUCT-M7-T04 | New/existing Base attach is openable and rollback-safe | Transactional `createBaseEntry`; response-driven navigation | Invalid parent leaves zero Base rows; canonical Base target is immediately available | Real project opens after create and new Base renders its preview/open action | Done |
| KB-PRODUCT-M7-T05 | Forged route/ID cannot bypass resolver | Internal object routes are server canonical only | Forged `/admin/users` becomes `/bases/{id}`; unknown/invalid targets return 404/400 | Real member choice request proves permission enforcement | Done |
| KB-PRODUCT-M7-T06 | Alias/follow-target and display modes are coherent/editable | Entry settings API/modal and hydration title strategy | Integration switches alias to follow-target and preview to link | Browser preserves `M7 project entry` instead of overwriting it with target title | Done |
| KB-PRODUCT-M7-T07 | Every access state is safe and actionable | State enum/resolvers and state-specific UI text | Available/forbidden/disabled/deleted/not_found/invalid paths are asserted or rejected before persistence | Unavailable objects are absent from the member picker | Done |
| KB-PRODUCT-M7-T08 | Current target lifecycle is reflected without stale manual paths | Resolver title/path hydration and archived knowledge handling | Base rename plus knowledge archive/restore assertions pass | Browser opens the canonical project route from creation response | Done |
| KB-PRODUCT-M7-T09 | Audit and reverse location are permission safe | Target metadata in audit payload; `/knowledge-object-references/{type}/{id}` | Reverse-reference result contains legal space/item web path | Real project/Base entry creation produces matching audit records | Done |
| KB-PRODUCT-M7-T10 | Object and state matrix passes on real stack | M7-specific integration and Playwright suites | Backend 3/3; frontend lint/build pass | `kb-product-m7-object-entries.spec.ts` passes 1/1 with dynamic cleanup | Done |

## Code Changes

- Backend: added object-choice paging, Base/project/file/knowledge resolvers and registration, atomic Base-entry creation, entry settings, canonical hydration, state handling and reverse references.
- Frontend: added permission-filtered object picker, response-driven open behavior, signed file opening, object-entry detail/state UI and editable display/title strategies.
- Database: V053 backfills project/file object links and adds the partial object-reference lookup index.
- Tests: added `KnowledgeObjectEntryIntegrationTests` and `kb-product-m7-object-entries.spec.ts`.
- Scripts: no M7-specific script changes.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Records M7 completion and advances the unique execution entry to M8. |
| `docs/90-reports/kb-product-m7-execution-report.md` | Added | Preserves task-level implementation, automated and real-browser evidence. |

## Validation

- Backend tests: `mvn -q -Dtest=KnowledgeObjectEntryIntegrationTests test` passed 3/3; `PlatformObjectControllerIntegrationTests` passed 3/3 during the M7 cycle.
- Frontend build: `pnpm lint` and `pnpm build` passed.
- Local quality gate: backend compile, V053 Testcontainers migration, `git diff --check`, lint and production build passed.
- Browser smoke: `COLLA_E2E_ISOLATED=true pnpm exec playwright test -c e2e/playwright.config.ts e2e/kb-product-m7-object-entries.spec.ts` passed 1/1 against local PostgreSQL, Redis, MinIO, backend and Vite.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | N/A |

## Next Steps

- Continue with KB-PRODUCT-M8 content-first navigation and knowledge-base information architecture. Do not start M9 editor interaction work before M8 acceptance closes.
