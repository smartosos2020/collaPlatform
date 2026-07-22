# PROJECT-PLATFORM-S03-M4 Execution Report

## Scope

- PROJECT-PLATFORM-S03-M4-T01 to PROJECT-PLATFORM-S03-M4-T10.
- 本里程碑交付版本化研发预置类型目录、新旧空间的可靠安装与补齐、系统类型保护和 legacy 兼容守卫。
- 本轮不引入动态字段、布局、完整发布流水线、工作项实例、第二套 Project Controller 或第二套权限引擎。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M4-T01 | unit | not-required | not-required | no | catalog order, stable keys and display semantics are deterministic domain behavior |
| PROJECT-PLATFORM-S03-M4-T02 | integration | not-required | not-required | no | reconcile existing, repeated, concurrent and conflicting spaces against PostgreSQL |
| PROJECT-PLATFORM-S03-M4-T03 | e2e-real-isolated | real | isolated | no | create a project space through the real API and observe all six presets |
| PROJECT-PLATFORM-S03-M4-T04 | e2e-real-isolated | real | isolated | no | inspect protected actions and exercise allowed disable and restore operations |
| PROJECT-PLATFORM-S03-M4-T05 | e2e-real-isolated | real | isolated | no | display preset provenance while integration assertions verify audit and outbox convergence |
| PROJECT-PLATFORM-S03-M4-T06 | integration | not-required | not-required | no | assert legacy hashes remain stable and no work-item instance schema or API exists |
| PROJECT-PLATFORM-S03-M4-T07 | e2e-real-isolated | real | isolated | no | open the governance count and existing legacy project user entry without regression |
| PROJECT-PLATFORM-S03-M4-T08 | integration | not-required | not-required | no | exercise new, existing, concurrent, conflict, inactive and rollback cases in PostgreSQL |
| PROJECT-PLATFORM-S03-M4-T09 | e2e-real-isolated | real | isolated | no | run preset lifecycle and legacy project compatibility in one real browser chain |
| PROJECT-PLATFORM-S03-M4-T10 | e2e-real-isolated | real | isolated | no | close targeted backend, frontend and browser evidence under the stage quality gate |

## Completed Items

- Added the versioned `development-v1` catalog with project, requirement, task, bug, iteration and release presets.
- Added transactional per-space reconciliation, startup backfill, conflict reporting and row locking for convergence.
- Installed presets in both normal project-space creation and legacy migration transactions.
- Added V063 database enforcement for system display/key, retire and delete protection, with a transaction-local migration cleanup path.
- Added preset provenance to configuration DTOs and the project-space configuration UI.
- Extended legacy boundary tests with type-table hashes and explicit negative assertions for a work-item instance table and API.
- Added isolated browser coverage for new/existing space presets, lifecycle actions, governance counts and legacy project navigation.
- Updated product, architecture, technology-selection, target architecture and preset operations documentation.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M4-T01 | 六类研发预置具有稳定 key、顺序、版本和展示语义 | `WorkItemTypePresetCatalog` | `WorkItemTypePresetCatalogTests` passes | not-required: deterministic domain catalog | Done |
| PROJECT-PLATFORM-S03-M4-T02 | 既有空间只补缺项，重复、并发和冲突执行可收敛 | reconciler, space lock and startup backfill service | existing/replay, concurrent and custom-key conflict integration cases pass | not-required: persistence convergence is verified directly | Done |
| PROJECT-PLATFORM-S03-M4-T03 | 新空间创建事务结束后具有完整预置目录且无半初始化状态 | shared reconciler in normal and legacy space services | new-space and 22 migration lifecycle cases pass | real: isolated API-created space shows six presets | Done |
| PROJECT-PLATFORM-S03-M4-T04 | 系统类型修改、退役和删除保护不可绕过，允许停用、恢复与排序 | service action projection plus V063 trigger guards | direct SQL bypass and service lifecycle assertions pass | real: protected actions are absent and disable/restore succeeds | Done |
| PROJECT-PLATFORM-S03-M4-T05 | 预置来源、状态和审计事件可解释且重放不重复 | source/catalog DTOs and lifecycle-scoped event idempotency | audit/outbox no-op replay and rollback/remigration assertions pass | real: preset source and lifecycle state are visible | Done |
| PROJECT-PLATFORM-S03-M4-T06 | legacy 写路径保持原模型且不存在第二套工作项实例模型 | legacy table hashes and OpenAPI/schema negative contract | legacy boundary tests plus `/api/work-items` and `project_work_items` absence assertions pass | not-required: architecture boundary is asserted from DB and OpenAPI | Done |
| PROJECT-PLATFORM-S03-M4-T07 | 治理计数准确且原项目用户入口兼容 | admin summary projection and unchanged legacy routes | governance count assertions and M3 regression spec pass | real: governance count and `/projects` navigation succeed | Done |
| PROJECT-PLATFORM-S03-M4-T08 | 新旧空间、并发、冲突、inactive 空间和回滚场景零污染 | focused preset, migration and boundary test suites | 7 target classes, 50 tests, 0 failures | not-required: transactional failure modes require direct integration evidence | Done |
| PROJECT-PLATFORM-S03-M4-T09 | 真实浏览器覆盖预置目录、生命周期和 legacy 深链 | isolated Playwright fixture and API cleanup | M4 spec and M3 regression spec each pass | real: isolated browser flow passes without route interception | Done |
| PROJECT-PLATFORM-S03-M4-T10 | 运行说明、目标验证和质量门证据可复核 | preset runbook, architecture updates and this report | lint, build, targeted backend tests and diff checks pass | real: formal finish browser rerun passes | Done |

## Code Changes

- `server/.../WorkItemTypePresetCatalog.java`: versioned six-type development catalog.
- `server/.../WorkItemTypePresetReconciliationService.java`: transactional idempotent installation and conflict results.
- `server/.../WorkItemTypePresetBackfillService.java`: per-space application-start reconciliation with failure isolation.
- `server/.../ProjectSpaceService.java` and `ProjectSpaceMigrationService.java`: shared preset installation in space creation transactions.
- `server/src/main/resources/db/migration/V063__protect_system_work_item_type_presets.sql`: database-level system preset guards.
- `server/.../WorkItemTypeApiDtos.java` and `web/.../ProjectWorkItemTypesPanel.tsx`: source and catalog-version projection.
- Focused domain, integration and browser suites cover catalog, reconciliation, migration, compatibility and UI behavior.
- `docs/05-runbooks/project-work-item-type-presets.md` and current product/architecture documents record operations and boundaries.

## Validation

- Backend tests: 7 target classes, 50 tests, 0 failures, 0 errors and 0 skipped; the final contract test rerun also passed 5/5.
- Frontend build: `pnpm lint` and `pnpm build` passed; chunk-budget and lazy-route checks also passed.
- Local quality gate: final stage gate passed in `quality-gate-20260722T130051.md`; earlier `125530` and `125900` attempts exposed and then verified corrections to the report structure and evidence-level wording.
- Browser smoke: real isolated `project-platform-s03-m4-presets-compatibility.spec.ts` passed 1/1 without route interception; M3 multi-role regression also passed 1/1.
- Database: empty PostgreSQL applied V001 through V063; local V062 to V063 upgrade succeeded and startup backfill installed 42 missing presets with an empty failure list.
- Cleanup: active `s03-m3-*` and `s03-m4-*` test spaces equal zero after evidence collection.
- Correction: a migration regression exposed a reused outbox key after legal rollback/remigration; lifecycle-scoped preset IDs now preserve replay idempotence without blocking a new lifecycle.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 自定义类型占用预置 key 时进入明确的人工治理清单，不自动覆盖业务数据 | non-blocking | `docs/05-runbooks/project-work-item-type-presets.md` |
| PROJECT-PLATFORM-S03-M5-T01 | S03 全阶段回归、迁移演练和下一 Stage 准入由最终里程碑统一执行 | stage closure only | current roadmap M5 |

## Next Steps

- Run PROJECT-PLATFORM-S03-M5 as the Stage-final cycle with the `route-final` profile.
- Reconcile M1-M4 evidence, run the full regression and Flyway rehearsal, then record S04 Go/No-Go.
- Keep dynamic fields, layouts, publication authoring and work-item instances in their approved later stages.
