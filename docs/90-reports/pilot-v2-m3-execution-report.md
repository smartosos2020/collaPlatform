---
title: PILOT-V2-M3 Execution Report
status: archived
milestone: PILOT-V2-M3
updated_at: 2026-07-12
---

# PILOT-V2-M3 Execution Report

## Scope

- PILOT-V2-M3-T01 到 PILOT-V2-M3-T10

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| T01 | Done | `knowledge-content-core.spec.ts` creates an isolated knowledge space, folder and Markdown item, then verifies tree navigation, breadcrumbs and content-first routing in the browser. |
| T02 | Done | The block-save API accepts an optional title and persists title plus blocks atomically. The content page debounces structured-block/title auto-save and the browser test verifies both after refresh. |
| T03 | Done | `useKnowledgeContentCollaboration.ts` uses stable callbacks for WebSocket commands, awareness and updates. `pnpm --dir web lint` completes without the former Hook dependency warnings. |
| T04 | Done (environment-gated) | The `@route-final` two-context scenario verifies presence and remote update propagation with configured editor credentials. It is skipped locally when those isolated credentials are absent. |
| T05 | Done | The smoke flow creates a named version, writes a temporary revision, restores the named version, and verifies version history remains available. |
| T06 | Done | The smoke flow covers block-anchored comment, reply, resolve/reopen and item-scoped audit. The configured two-user flow additionally verifies an `@mention` notification. |
| T07 | Done (environment-gated) | The route-final permission scenario grants direct user, department, user-group and role subjects, asserts their records, and uses configured subject IDs instead of creating identity data. |
| T08 | Done (environment-gated) | The smoke flow rejects unauthenticated and wrong-space access without leaking title or ID. The route-final scenario verifies a configured readonly user receives `403` on write. |
| T09 | Done (isolated environment-gated) | Project now has a platform resolver and registered object type; the editor exposes a project card option. Smoke verifies available knowledge-content and unavailable Base cards, while the isolated route-final scenario creates Base/project fixtures and verifies all three available cards plus safe fallback. |
| T10 | Done (route-final environment-gated) | Default `@smoke` remains a serial, cleanup-safe 4-test suite. `COLLA_E2E_SUITE=route-final` discovers the two multi-identity scenarios and the isolated object-card scenario without executing them against shared data. |

## Code Changes

- Backend: block-save request/service now persists an optional title together with structured blocks; added a project platform-object resolver and targeted integration coverage for Base/project/knowledge embeds and missing-object downgrade.
- Frontend: stabilized collaboration Hook callbacks; added key-based structured-block/title auto-save, refresh-safe local draft synchronization and project object-card insertion/label support.
- Database: `V048__add_project_platform_object_type.sql` registers the project route/deep-link metadata.
- Scripts: none. Browser coverage is project test code under `web/e2e/`.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked T01-T08 complete and made the next entry T09. |
| `docs/90-reports/pilot-v2-m3-execution-report.md` | Updated | Recorded implementation, local evidence and environment-gated scenarios. |

## Validation

- Backend tests: `mvn -B -Dtest=KnowledgeContentControllerIntegrationTests test` passed: 6 tests, 0 failures. The isolated Testcontainers database validated and applied V001-V048.
- Frontend build: pending final stage gate after the object-card changes.
- Local quality gate: `pnpm --dir web lint` passed with no warnings.
- Browser smoke: `pnpm --dir web exec playwright test --config=e2e/playwright.config.ts` passed 4 `@smoke` tests, including tree/content, structured-block auto-save, versions, comments, object-card downgrade and access isolation.
- Configured collaboration suite: `COLLA_E2E_SUITE=route-final` discovered three scenarios and skipped all three because isolated credentials/share-subject IDs and `COLLA_E2E_ISOLATED=true` were not supplied. This is expected fixture policy, not a pass result.

## Remaining Gaps

- Execute the two identity/permission scenarios after setting `COLLA_E2E_EDITOR_USERNAME`, `COLLA_E2E_EDITOR_PASSWORD`, `COLLA_E2E_VIEWER_USERNAME`, `COLLA_E2E_VIEWER_PASSWORD`, `COLLA_E2E_SHARE_DEPARTMENT_ID`, `COLLA_E2E_SHARE_USER_GROUP_ID`, and `COLLA_E2E_SHARE_ROLE_ID` for isolated, non-production accounts/subjects.
- Execute the object-card scenario with `COLLA_E2E_ISOLATED=true` only against a disposable database. It creates Base and project objects because those modules currently do not expose an archive/delete operation.

## Next Steps

- Start M4 with cross-module collaboration flows.
