# PROJECT-PLATFORM-S03-M3 Execution Report

## Scope

- PROJECT-PLATFORM-S03-M3-T01 到 PROJECT-PLATFORM-S03-M3-T10。
- 本里程碑交付项目空间内的工作项类型配置界面和成员执行侧 active 类型摘要；不提前实现动态字段、布局、发布流水线或工作项实例。
- 前端只消费 M2 的用户协作 API 和服务端 `availableActions`，不从角色名推导配置权限，也不向企业治理身份泄露空间配置。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M3-T01 | integration | real | isolated | no | call canonical list/detail/command endpoints and verify write request ids |
| PROJECT-PLATFORM-S03-M3-T02 | e2e-real-isolated | real | isolated | no | owner opens list, detail and direct type deep link |
| PROJECT-PLATFORM-S03-M3-T03 | e2e-real-isolated | real | isolated | no | create/edit against the real API and retain stale-writer input |
| PROJECT-PLATFORM-S03-M3-T04 | e2e-real-isolated | real | isolated | no | copy a type, reject duplicate key and preserve form input |
| PROJECT-PLATFORM-S03-M3-T05 | e2e-real-isolated | real | isolated | no | reorder by focusable controls, then force a stale batch and verify rollback |
| PROJECT-PLATFORM-S03-M3-T06 | e2e-real-isolated | real | isolated | no | confirm disable/restore/retire lifecycle transitions |
| PROJECT-PLATFORM-S03-M3-T07 | e2e-real-isolated | real | isolated | no | prove owner/admin positives and member/guest/governor negatives |
| PROJECT-PLATFORM-S03-M3-T08 | e2e-real-isolated | real | isolated | no | member and guest read active-only execution summaries |
| PROJECT-PLATFORM-S03-M3-T09 | e2e-real-isolated | real | isolated | no | inspect loading/error/empty states and 1366/1440/390 viewport overflow |
| PROJECT-PLATFORM-S03-M3-T10 | e2e-real-isolated | real | isolated | no | run the complete isolated role, lifecycle, conflict and viewport chain |

## Completed Items

- Added typed work-item type DTOs, API methods, query keys and space-scoped invalidation.
- Added manager-only type configuration routes with list, detail and direct deep-link selection.
- Added create, edit, copy, status filtering, keyboard/pointer ordering and lifecycle confirmations.
- Added stable write request IDs, structured API error codes and persistent version-conflict recovery without clearing form input.
- Added optimistic ordering with exact rollback and synchronized detail versions after successful reorder.
- Rendered actions only from server-projected `availableActions`; protected system types receive an explicit explanation.
- Added active-only type summaries to the normal project-space overview for owner/admin/member/guest.
- Added responsive local scrolling, loading/error/empty states and an isolated five-identity browser suite.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M3-T01 | DTO、client、query key 和失效范围保持 space/type 隔离 | `workItemTypesApi.ts`, `httpClient.ts`, `apiBoundary.ts` | lint/build；E2E 验证 POST/PATCH request id | real: isolated API/browser chain | Done |
| PROJECT-PLATFORM-S03-M3-T02 | manager 可从路由进入列表、详情并保持深链上下文 | router, `ProjectSpacesPage`, `ProjectWorkItemTypesPanel` | E2E 断言 `/types/{typeId}` | real: owner/admin direct navigation | Done |
| PROJECT-PLATFORM-S03-M3-T03 | 创建、编辑、校验、保存和过期版本反馈可用 | modal form, structured `version_conflict`, persistent alert | real POST/PATCH and forced 409 | real: stale input remains visible after refresh | Done |
| PROJECT-PLATFORM-S03-M3-T04 | 复制结果独立且 key 冲突不清空输入 | copy mutation and preserved Ant Form state | duplicate-key POST returns 409; input value assertion passes | real: copied type becomes selected | Done |
| PROJECT-PLATFORM-S03-M3-T05 | 顺序仅在同状态内调整，失败恢复原顺序 | same-status adjacency, optimistic cache and rollback | success reorder plus externally-staled reorder 409 | real: focusable arrow controls work by keyboard/pointer | Done |
| PROJECT-PLATFORM-S03-M3-T06 | 停用、恢复、退役具备确认和明确保护说明 | lifecycle confirm dialogs, danger buttons, system/read-only alerts | real disable/restore/retire responses pass | real: retired type exposes no restore action | Done |
| PROJECT-PLATFORM-S03-M3-T07 | 动作完全来自服务端投影且角色边界无绕过 | every action checks `availableActions`; manager route guard | admin positive; member/guest UI negatives; governor API 404 | real: five isolated identities | Done |
| PROJECT-PLATFORM-S03-M3-T08 | 执行侧只呈现 active 最小摘要 | overview active type cards and empty/error states | member/guest see active bug and not retired copy | real: ordinary overview remains execution entry | Done |
| PROJECT-PLATFORM-S03-M3-T09 | 状态、焦点、布局和滚动边界可用 | Skeleton/Alert/Empty, ARIA listbox/options, responsive CSS | document overflow <= 1px at 1366, 1440 and 390 | real: in-app browser visual inspection passed | Done |
| PROJECT-PLATFORM-S03-M3-T10 | 真实 API/数据库主链路和前端质量门通过 | isolated Playwright spec and production build | 1 E2E passed; lint/build/diff check passed | real: no route interception or mock browser | Done |

## Code Changes

- `web/src/modules/projectSpaces/api/workItemTypesApi.ts`: audience-specific DTOs, API commands and query-key factory.
- `web/src/modules/projectSpaces/components/ProjectWorkItemTypesPanel.tsx`: complete configuration surface, optimistic ordering, lifecycle and conflict recovery.
- `web/src/modules/projectSpaces/pages/ProjectSpacesPage.tsx`: manager configuration tab and active execution summary.
- `web/src/app/router.tsx`: collection/detail deep-link routes.
- `web/src/shared/api/httpClient.ts`: one request ID per write command and structured API error code propagation.
- `web/src/shared/api/apiBoundary.ts`: work-item type collaboration DTO boundary.
- `web/src/index.css`: responsive list/detail layout, badges, facts, scrolling and focus styles.
- `web/e2e/project-platform-s03-m3-work-item-types.spec.ts`: isolated role, mutation, conflict, rollback and viewport acceptance chain.

## Validation

- Backend tests: not-required for M3 because no backend production or test source changed after the M2 baseline; M2 already closed the focused work-item type API suites and M5 owns full Stage regression.
- Browser E2E: `COLLA_E2E_ISOLATED=true pnpm exec playwright test e2e/project-platform-s03-m3-work-item-types.spec.ts --config playwright.config.ts` -> PASS; formal finish rerun passed 1 test in 27.6s.
- Frontend lint: `pnpm lint` -> PASS.
- Frontend production build: `pnpm build` -> PASS, 3,280 modules transformed.
- Frontend build: `pnpm build` -> PASS, including TypeScript project build and Vite production bundle.
- Patch hygiene: `git diff --check` -> PASS.
- Manual browser: local in-app browser created a real type, verified detail/lifecycle rendering and inspected the desktop layout; automated viewports covered 1366x768, 1440x900 and 390x844.
- Browser smoke: real isolated Playwright flow passed without route interception; in-app browser visual inspection also passed.
- Local quality gate: stage finish PASS in `quality-gate-20260722T112224.md`; browser, lint, build, chunk budget, lazy routes, documentation contract and diff checks all passed.
- Backend history suite and full Flyway rehearsal were not run in M3 because this milestone changes frontend code only; the Stage-final M5 route-final gate owns full regression and migration rehearsal.
- Failed-run residue was removed through product APIs: 3 interrupted test spaces archived and 12 active test identities offboarded.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M4-T01 | no development preset/system type catalog is installed yet | non-blocking for M3; protection UI is ready but awaits real preset data | current roadmap M4 |
| PROJECT-PLATFORM-S03-M4-T03 | existing active spaces are not yet backfilled with presets | non-blocking for custom-type configuration | current roadmap M4 |
| PROJECT-PLATFORM-S03-M5-T04 | dynamic fields, layouts, publication authoring and work-item instances remain intentionally absent | no premature S04-S06 scope claimed | program S04-S06 and M5 review |

## Next Steps

- Execute PROJECT-PLATFORM-S03-M4-T01 through T08 in a separate work cycle.
- Install and backfill the development preset catalog without weakening permanent key, system protection or idempotency contracts.
- Keep the M3 UI server-driven while M4 introduces real system-type data and compatibility evidence.
