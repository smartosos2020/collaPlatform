---
title: KB-PRODUCT-M8 Execution Report
status: archived
milestone: KB-PRODUCT-M8
updated_at: 2026-07-16
---

# KB-PRODUCT-M8 Execution Report

## Scope

- KB-PRODUCT-M8-T01 to KB-PRODUCT-M8-T09

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M8-T01 | e2e-real-isolated | real | isolated | No | Open a space URL and land on its effective home item; verify stale home falls back safely |
| KB-PRODUCT-M8-T02 | e2e-real-isolated | real | isolated | No | Open content, directory, object and external-link nodes through their single primary actions |
| KB-PRODUCT-M8-T03 | e2e-real-isolated | real | isolated | No | Open a non-leaf directory and an empty directory, then act on their child/empty states |
| KB-PRODUCT-M8-T04 | e2e-real-isolated | real | isolated | No | Read content without governance panels blocking the body; open management only from an auxiliary control |
| KB-PRODUCT-M8-T05 | e2e-real-isolated | real | isolated | No | Deep-link, refresh, use breadcrumbs and browser history while URL and tree selection stay aligned |
| KB-PRODUCT-M8-T06 | e2e-real-isolated | real | isolated | No | Search and switch all/recent/favorites/subscribed/archive filters without losing the current legal item |
| KB-PRODUCT-M8-T07 | e2e-real-isolated | real | isolated | No | Exercise loading, empty, forbidden, archived and unavailable-target states without title disclosure |
| KB-PRODUCT-M8-T08 | e2e-real-isolated | real | isolated | No | Verify desktop/narrow layouts, internal scrolling and keyboard focus/navigation |
| KB-PRODUCT-M8-T09 | e2e-real-isolated | real | isolated | No | Admin, editable member and read-only member follow the same content path with permission-specific actions only |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M8-T01 | Done | Space DTO and route wrapper resolve the effective home; a stale `home_item_id` falls back to the root item and emits the canonical item path. |
| KB-PRODUCT-M8-T02 | Done | Content, directory, object and external-link nodes each expose one primary action without a metadata dashboard detour. |
| KB-PRODUCT-M8-T03 | Done | Directories render actionable child cards; empty directories render creation guidance only to editable identities. |
| KB-PRODUCT-M8-T04 | Done | Management is an explicit auxiliary route and content governance is collapsed by default and permission gated. |
| KB-PRODUCT-M8-T05 | Done | Canonical item URLs drive breadcrumbs, tree selection, reload, browser history and legacy `itemId` migration. |
| KB-PRODUCT-M8-T06 | Done | Search plus all/recent/favorites/subscribed/archive filters preserve the active legal item URL. |
| KB-PRODUCT-M8-T07 | Done | Loading, empty, archived, disabled, forbidden, missing, network and unavailable-target states have safe actions and do not disclose private titles. |
| KB-PRODUCT-M8-T08 | Done | Desktop and narrow layouts use bounded sidebars and internal scrolling; tree items remain keyboard navigable. |
| KB-PRODUCT-M8-T09 | Done | Admin, editor and viewer use the same content route; management and mutation actions are shown only at the permitted level, while outsiders receive a safe denial. |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M8-T01 | Space URL resolves effective home with safe fallback | `KnowledgeBaseSpaceService`, `KnowledgeApiDtos` and `KnowledgeBaseSpaceRoute` | `KnowledgeNavigationIntegrationTests` verifies canonical home and stale-home fallback | Real space URL redirects to its generated home item | Done |
| KB-PRODUCT-M8-T02 | Every node kind has one content-first primary behavior | `KnowledgeContentPage` dispatches content, directory, object and external nodes by kind | Frontend lint/build and backend ownership checks pass | Real content opens inline; project and external nodes open their canonical targets | Done |
| KB-PRODUCT-M8-T03 | Non-leaf and empty directories remain actionable | `KnowledgeDirectorySurface` renders children or permission-aware empty actions | Production build validates the directory component path | Real child opens by click/Enter; empty directory shows the expected empty state | Done |
| KB-PRODUCT-M8-T04 | Governance does not block the reading path | Explicit `view=management` route and closed `doc-management-details` | Route and permission assertions pass in the M8 suites | Body is immediately visible; editor sees auxiliary management while viewer does not | Done |
| KB-PRODUCT-M8-T05 | URL, breadcrumb, tree and history identify one item | Canonical item navigation and selected-key derivation use route params | Navigation integration verifies one web path for owner/editor/viewer | Deep link, reload, breadcrumb, Back and tree selection retain the same item | Done |
| KB-PRODUCT-M8-T06 | Discovery filters do not discard current legal context | Discovery query backs subscribed state; filter mode only changes tree projection | Typecheck/build validates all filter branches | All/recent/favorites/subscribed/archive controls retain the current item URL | Done |
| KB-PRODUCT-M8-T07 | Exceptional states are actionable and non-disclosing | Dedicated loading/error/object-state surfaces and disabled/archived alerts | Backend outsider assertions prove title non-disclosure | Missing/forbidden, disabled, archived and unavailable-target states pass on the real stack | Done |
| KB-PRODUCT-M8-T08 | Layout scrolls internally and remains keyboard usable | Bounded `docs-workspace`, sidebar/main overflow rules and responsive grid | CSS is covered by lint/build | 1366 px and 900 px assertions show no horizontal/body overflow; Enter navigation passes | Done |
| KB-PRODUCT-M8-T09 | Identity changes actions, not information architecture | `canEdit`/`canManage` gate mutations and auxiliary controls only | Backend verifies equal canonical paths and outsider denial | Admin, editor, viewer and outsider sessions pass the isolated role matrix | Done |

## Code Changes

- Backend: resolves missing home items to the root and emits a canonical item navigation path from space DTOs.
- Frontend: adds a space entry resolver; content-first directory/object/error surfaces; canonical navigation; discovery filters; permission-aware governance/actions; responsive internal scrolling.
- Database: no M8 schema changes.
- Tests: adds `KnowledgeNavigationIntegrationTests` and `kb-product-m8-navigation.spec.ts`.
- Scripts: no M8-specific script changes.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/00-product/current-product-scope.md` | Updated | Records canonical content-first routes and the auxiliary management path. |
| `docs/01-architecture/current-architecture.md` | Updated | Freezes effective-home resolution and content/governance route boundaries. |
| `docs/02-roadmap/current-roadmap.md` | Updated | Closes M8 and advances the unique execution entry to M9. |
| `docs/90-reports/kb-product-m8-execution-report.md` | Added | Preserves task-level implementation, automated and real-browser evidence. |

## Validation

- Backend tests: `mvn -q -Dtest=KnowledgeNavigationIntegrationTests test` passed 2/2 against Testcontainers.
- Frontend build: `pnpm lint` and `pnpm build` passed after the final permission-action adjustment.
- Local quality gate: `quality-gate-20260716-145914.md` passed targeted backend tests, all 53 Flyway migrations, frontend lint/build, chunk budget and route lazy-loading checks; `git diff --check` passed.
- Browser smoke: `COLLA_E2E_ISOLATED=true pnpm exec playwright test -c e2e/playwright.config.ts e2e/kb-product-m8-navigation.spec.ts` passed 1/1 with dynamic data and cleanup against the local full stack.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | N/A |

## Next Steps

- Continue with KB-PRODUCT-M9 editor high-frequency interaction and content-block work. Do not start M10 closure work before M9 acceptance closes.
