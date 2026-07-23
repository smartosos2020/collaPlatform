---
title: PROJECT-PLATFORM-S04-M4 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S04-M4
stage: PROJECT-PLATFORM-S04
updated_at: 2026-07-23
---

# PROJECT-PLATFORM-S04-M4 Execution Report

## Scope

- PROJECT-PLATFORM-S04-M4-T01 to PROJECT-PLATFORM-S04-M4-T11.
- 本里程碑交付 owner/admin 字段配置 UI、字段能力目录消费、稳定查询键、目录投影、合成性能基线和六类身份真实浏览器闭环。
- 本轮不交付字段布局、完整类型版本发布、工作项实例、字段值、实例关系或 legacy issue 双写。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M4-T01 | static | not-required | not-required | no | TypeScript compiles typed DTO/API/query keys with space/type/field isolation |
| PROJECT-PLATFORM-S04-M4-T02 | e2e-real-isolated | real | isolated | no | owner opens field list/detail/create, refreshes a field deep link and returns to type context |
| PROJECT-PLATFORM-S04-M4-T03 | e2e-real-isolated | real | isolated | no | create, reorder, disable and restore fields through production UI/API |
| PROJECT-PLATFORM-S04-M4-T04 | e2e-real-isolated | real | isolated | no | choose field types from the server catalog and render only declared configuration capability |
| PROJECT-PLATFORM-S04-M4-T05 | e2e-real-isolated | real | isolated | no | save stable select options, typed text default and structured length rule |
| PROJECT-PLATFORM-S04-M4-T06 | integration | not-required | not-required | no | map user/date/datetime/url/attachment/reference form values to canonical server contracts |
| PROJECT-PLATFORM-S04-M4-T07 | e2e-real-isolated | real | isolated | no | owner/admin see projected actions; member/guest are forbidden; outsider/governor are hidden |
| PROJECT-PLATFORM-S04-M4-T08 | e2e-real-isolated | real | isolated | no | search/filter/sort the definition catalog without querying instance values |
| PROJECT-PLATFORM-S04-M4-T09 | integration | not-required | not-required | no | list 120 fields and 2400 options within 3 seconds and verify scoped indexed query/no dynamic value tables |
| PROJECT-PLATFORM-S04-M4-T10 | e2e-real-isolated | real | isolated | no | cover loading/empty/not-found states and 1366/1440/390 layouts without document overflow |
| PROJECT-PLATFORM-S04-M4-T11 | e2e-real-isolated | real | isolated | no | close owner/admin/member/guest/outsider/governor matrix against real API/database and pass focused gates |

## Completed Items

- Added typed field DTOs, field-type catalog, configuration API client and cache keys scoped by space, type and field.
- Added field routes and an owner/admin configuration panel with list, detail, create, edit, reorder and lifecycle operations.
- Added capability-driven field-type selection, stable option editing, typed defaults, structured rules and complex type panels.
- Kept persisted option keys immutable and corrected the UI contract to use `#RRGGBB`, canonical length-rule keys and typed user scope entries.
- Rendered all write controls from service-projected `availableActions`; no frontend role-name authorization is used.
- Added deterministic local definition-catalog search/status/type/sort projection and explicit loading, empty, error and conflict states.
- Added a real PostgreSQL/Flyway synthetic performance test for 120 fields and 2400 options.
- Added one real isolated Playwright flow covering six identities, deep links, field configuration, permissions and three viewport classes.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M4-T01 | typed contracts and cache isolation | `workItemFieldsApi.ts` DTOs/API/query-key factory | ESLint, TypeScript and production build | not-required | Done |
| PROJECT-PLATFORM-S04-M4-T02 | stable list/detail/create deep links | router, `ProjectSpacesPage`, type panel field entry and field panel | route compiles and API detail/list suites pass | real create, detail URL, reload, empty type and missing field | Done |
| PROJECT-PLATFORM-S04-M4-T03 | safe edit/lifecycle/reorder behavior | mutations, confirmations, optimistic reorder rollback and conflict refresh | backend aggregate-version/lifecycle suites | real create, reorder, disable and restore | Done |
| PROJECT-PLATFORM-S04-M4-T04 | capability-driven type UI | catalog-backed picker and descriptor-based sections | 11-type registry/catalog tests plus TypeScript | real text/select choices and capability summaries | Done |
| PROJECT-PLATFORM-S04-M4-T05 | stable options/defaults/rules | option editor and structured rule form | canonicalizer/controller tests | real two-option select and text default/length rule round trip | Done |
| PROJECT-PLATFORM-S04-M4-T06 | complex configuration matches server contract | user scope, temporal bounds, URL, attachment and reference editors | M3 complex canonical/reference regression suites | form capability is present; semantic writes remain covered by M3 real/API evidence | Done |
| PROJECT-PLATFORM-S04-M4-T07 | actions and disclosure come from server | `availableActions` checks in collection/detail controls | authorization integration tests | real owner/admin positives, member/guest 403, outsider/governor 404 | Done |
| PROJECT-PLATFORM-S04-M4-T08 | definition catalog projection is stable | search/status/type/sort projection over field definitions and descriptors | deterministic projection/build checks | real search hides nonmatching field and restores list | Done |
| PROJECT-PLATFORM-S04-M4-T09 | performance and index budget is reproducible | synthetic integration test plus V064 scoped index | 120 fields/2400 options under 3 seconds; indexed plan; no dynamic tables | not-required | Done |
| PROJECT-PLATFORM-S04-M4-T10 | states and responsive layout are usable | skeleton, empty/error alerts, local scroll and responsive CSS | ESLint/build | real empty/not-found and 1366/1440/390 no-overflow checks | Done |
| PROJECT-PLATFORM-S04-M4-T11 | focused quality and six identities close | docs/roadmap/report plus dedicated Playwright spec | targeted backend, lint and build gates | real isolated spec passes 1/1 and cleans fixtures | Done |

## Code Changes

- `web/src/modules/projectSpaces/api/workItemFieldsApi.ts`: field catalog/configuration DTOs, API methods and scoped query keys.
- `web/src/modules/projectSpaces/components/ProjectWorkItemFieldsPanel.tsx`: field configuration shell, catalog projection, detail/actions, forms and lifecycle.
- `web/src/modules/projectSpaces/components/WorkItemFieldConfigDrawer.tsx`: options, defaults, rules and complex type configuration.
- `web/src/modules/projectSpaces/components/ProjectWorkItemTypesPanel.tsx`, `ProjectSpacesPage.tsx`, `router.tsx`: field entry and deep-link routing.
- `web/src/index.css`: desktop/narrow layout, local scroll, form, list, summary and state styles.
- `WorkItemFieldConfigurationControllerIntegrationTests`: synthetic catalog budget and indexed-query evidence.
- `project-platform-s04-m4-field-configuration-ui.spec.ts`: real isolated six-identity browser acceptance.

## Validation

- Backend tests: PASS - nine focused field domain, schema, canonicalization and API suites passed; `quality-gate-20260723T161007-backend-targeted-tests.log` includes the 120-field/2400-option performance probe.
- Frontend build: PASS - ESLint, TypeScript, Vite production build, chunk budget and route lazy-loading checks passed; see `quality-gate-20260723T161007-frontend-build.log`.
- Local quality gate: PASS - the M4 stage gate passed all executable checks and this report uses the corrected contract-v2 evidence schema; the final `work:finish` rerun records the closing report.
- Browser smoke: PASS - the real isolated M4 field-configuration flow passed 1/1 in 28.5 seconds without route interception; see `work-cycle-browser-20260723T161007.log`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M5 | Stage-wide review, full historical regression and route-final evidence remain | non-blocking for focused M4 closure | current roadmap M5 |
| N/A | field layout and form/view composition are outside the S04 field-definition boundary | non-blocking | Program S05 |
| N/A | immutable type-version publication is outside the S04 field-definition boundary | non-blocking | Program S06 |
| N/A | work-item instances, field values and instance references are outside the S04 field-definition boundary | non-blocking | Program S07 |

## Next Steps

- Start PROJECT-PLATFORM-S04-M5 with a fresh audit of M1-M4 evidence and schema/API/UI boundaries.
- Re-run upgrade rehearsal, performance budget and full historical suites at the Stage-final gate.
- Produce S05 layout and S06 publication admission decisions without moving instances or values into S04.
