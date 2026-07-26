# PROJECT-PLATFORM-S07-M5 Execution Report

## Scope

PROJECT-PLATFORM-S07-M5-T01 至 PROJECT-PLATFORM-S07-M5-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M5-T01 | core-system | system-real-isolated | not-required | isolated | No | audit all 60 S07 tasks and reopen blockers |
| PROJECT-PLATFORM-S07-M5-T02 | core-user | e2e-real-isolated | real | isolated | No | type entry, list, create, detail and legacy route |
| PROJECT-PLATFORM-S07-M5-T03 | core-user | e2e-real-isolated | real | isolated | No | bound snapshot create/detail rendering and values |
| PROJECT-PLATFORM-S07-M5-T04 | core-user | e2e-real-isolated | real | isolated | No | participant, activity, comment, MinIO attachment and lifecycle |
| PROJECT-PLATFORM-S07-M5-T05 | core-user | e2e-real-isolated | real | isolated | No | keyboard, 1366/820, long content, offline and conflict |
| PROJECT-PLATFORM-S07-M5-T06 | core-user | e2e-real-isolated | real | isolated | No | six-identity authorization and minimum disclosure |
| PROJECT-PLATFORM-S07-M5-T07 | core-user | e2e-real-isolated | real | isolated | No | unmigrated route, scoped migration and canonical redirect |
| PROJECT-PLATFORM-S07-M5-T08 | core-user | e2e-real-isolated | real | isolated | No | baseline matrix, verify and pre-cutover rollback |
| PROJECT-PLATFORM-S07-M5-T09 | core-user | e2e-real-isolated | real | isolated | No | route-final and full generated gates |
| PROJECT-PLATFORM-S07-M5-T10 | core-system | system-real-isolated | not-required | isolated | No | architecture, Program, contracts and runbook sync |
| PROJECT-PLATFORM-S07-M5-T11 | core-system | system-real-isolated | not-required | isolated | No | freeze S08 reuse and prohibition boundaries |
| PROJECT-PLATFORM-S07-M5-T12 | core-user | e2e-real-isolated | real | isolated | No | S07 Go, route-final and current stage none |

## Completed Items

- Added the user-side canonical WorkItem list, create and detail surface, bound-snapshot dynamic field rendering, participant/activity/comment/attachment interactions and archive/restore.
- Added a legacy issue route that preserves an unmigrated legacy location and redirects an explicitly mapped issue to its canonical WorkItem location.
- Hardened runtime disclosure so hidden fields are removed from values, access projection, bound snapshot layouts/policies, errors and browser output.
- Added online/offline write guards and normalized conflict handling; failed writes preserve local form values and never silently overwrite concurrent server facts.
- Added optional project-scoped migration planning. The default remains workspace-wide, while a canary or acceptance run can freeze an explicit set of project units without unrelated legacy failures.
- V090 corrects the system preset guard: built-in identity and metadata remain protected, while `current_version_id` may advance through the normal validated publication transaction.
- The route-final browser scenario creates fresh identities and data, uses real PostgreSQL/Redis/MinIO services, verifies six authorization identities, runs scoped legacy migration and rolls back both migration batches.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M5-T01 | 60 tasks traceable and blockers reopened | M1-M5 reports plus current roadmap | focused backend, migration and browser failures were repaired, not waived | Not required | Done |
| PROJECT-PLATFORM-S07-M5-T02 | complete user routes and states | router, `ProjectWorkItemsPanel`, `LegacyIssueRoute` | TypeScript/Vite production build PASS | real browser create/list/detail/legacy route PASS | Done |
| PROJECT-PLATFORM-S07-M5-T03 | immutable bound snapshot drives UI | work item API DTO and runtime projection | service integration verifies version/hash and hidden projection | real browser required/read/hidden dynamic fields PASS | Done |
| PROJECT-PLATFORM-S07-M5-T04 | continuous collaboration lifecycle | canonical participant/comment/attachment/activity APIs | transaction and MinIO link assertions | real browser create/edit/comment/participant/upload/download/archive/restore PASS | Done |
| PROJECT-PLATFORM-S07-M5-T05 | accessible resilient editing | labels, online hook and conflict handler | build plus 409 API assertion | real browser keyboard, long title/comment, 1366/820, offline and conflict PASS | Done |
| PROJECT-PLATFORM-S07-M5-T06 | six identities remain isolated | service authorization and filtered snapshot | guest hidden-field integration assertions | real browser owner/admin/member/guest/non-member/enterprise-admin PASS | Done |
| PROJECT-PLATFORM-S07-M5-T07 | mixed mode remains coherent | scoped plan API and compatibility route | scoped migration integration test | real browser unmigrated route, migration, verify and canonical redirect PASS | Done |
| PROJECT-PLATFORM-S07-M5-T08 | migration and rollback are repeatable | V086-V090 plus migration services | V001/V061/V065/V078/V085 matrix, repeat migrate and backup restore PASS | real browser scoped execute/verify and both pre-cutover rollbacks PASS | Done |
| PROJECT-PLATFORM-S07-M5-T09 | no blocking generated gate | route-final spec and gate planner | full work:finish evidence | real browser `project-platform-s07-m5-route-final.spec.ts` PASS | Done |
| PROJECT-PLATFORM-S07-M5-T10 | implemented facts synchronized | current/target architecture, module/object/event docs and Program | planning and architecture checks | Not required | Done |
| PROJECT-PLATFORM-S07-M5-T11 | S08 reuses canonical identity | S08 admission section and Program dependency | architecture contract check | Not required | Done |
| PROJECT-PLATFORM-S07-M5-T12 | S07 closes without hidden gap | completed reports, roadmap and work context | route-final work:finish | real browser full route-final PASS | Done |

## Code Changes

- `server/src/main/resources/db/migration/V086__create_canonical_project_work_items.sql` through `V090__allow_system_work_item_type_publication.sql`
- `server/src/main/java/com/colla/platform/modules/project/{api,application,contract,domain,infrastructure,runtime}/**WorkItem*`
- `server/src/test/java/com/colla/platform/modules/project/{application,infrastructure}/**WorkItem*`
- `web/src/modules/projectSpaces/api/workItemsApi.ts`
- `web/src/modules/projectSpaces/components/ProjectWorkItemsPanel.tsx`
- `web/src/modules/projectSpaces/pages/LegacyIssueRoute.tsx`
- `web/e2e/project-platform-s07-m5-route-final.spec.ts`
- project Program, current/target architecture, module, object, event, runbook and roadmap documents

## Validation

- Backend tests: focused WorkItem/publication/migration/FileAccess integration PASS; final `work:finish` reruns the full backend suite.
- Frontend build: lint and production TypeScript/Vite build PASS after removing cross-feature imports and effect state resets.
- Local quality gate: `quality-gate-20260726T101109.md` PASS, including architecture boundaries, security, Flyway, workbench and documentation checks.
- Browser smoke: real isolated `project-platform-s07-m5-route-final.spec.ts` PASS against the current API, PostgreSQL, Redis and MinIO.
- Focused runtime integration: `mvn -q -Dtest=WorkItemServiceIntegrationTests test` PASS.
- Publication and migration regression: `WorkItemConfigurationPublicationServiceIntegrationTests` and `WorkItemMigrationServiceIntegrationTests` PASS against PostgreSQL 16/Testcontainers.
- Migration rehearsal: `mvn -q '-Dtest=WorkItemMigrationServiceIntegrationTests,WorkItemConfigurationMigrationMatrixIntegrationTests' test`; V001/V061/V065/V078/V085 to V090, repeat migrate, sentinel and backup restore PASS.
- Full-regression repair: the first final gate exposed nine pre-S07 assertions that still expected no canonical WorkItem table, six post-V079 migrations and latest V085. The affected seven test classes now assert zero scoped/isolated instance rows or the canonical table contract, eleven post-V079 migrations and latest V090; the focused regression rerun PASS.
- Frontend production build: `pnpm --dir web build` PASS.
- Real browser: `project-platform-s07-m5-route-final.spec.ts`; 1/1 PASS with real API, PostgreSQL, Redis and MinIO.
- Full closure gate: recorded by final `work:finish`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None inside S07 implementation scope | non-blocking | Closed |

Production cutover still requires a target-environment backup/restore rehearsal, approved maintenance and observation window, monitoring, capacity evidence and operator approval. This release boundary does not reopen S07 implementation.

## Next Steps

- Keep S08 Planned until a separate archive/activation action creates its own current roadmap.
- S08 may attach state history and commands to canonical WorkItems, but must not recreate WorkItem identity, field values, snapshot binding, participants, activities or legacy migration authority.
