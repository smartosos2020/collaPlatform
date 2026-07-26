# PROJECT-PLATFORM-S08-M4 Execution Report

## Scope

PROJECT-PLATFORM-S08-M4-T01 到 PROJECT-PLATFORM-S08-M4-T12

本里程碑完成轻量状态流的空间配置器、成员执行 UI、显式 backfill 管理、真实隔离综合验收和 S08 Stage 收口。它不创建 S09 node instance/token，不实现 S14 看板拖拽或 S17 自动化。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M4-T01 | non-core | integration | not-required | not-required | No | M1-M3 的 36 项报告、实现、V091/V092、边界和 gap 逐项反查 |
| PROJECT-PLATFORM-S08-M4-T02 | core-user | e2e-real-isolated | real | isolated | No | owner 在空间配置中编辑状态/动作/转换/授权/guard/必填并保存校验 |
| PROJECT-PLATFORM-S08-M4-T03 | core-user | e2e-real-isolated | real | isolated | No | preview/diff/compatibility、发布阻断/危险确认、失败保留输入与 rollback 入口 |
| PROJECT-PLATFORM-S08-M4-T04 | core-user | e2e-real-isolated | real | isolated | No | 成员详情执行服务端动作、原子必填 patch、历史和 409/422 恢复 |
| PROJECT-PLATFORM-S08-M4-T05 | core-user | e2e-real-isolated | real | isolated | No | 键盘、长名称、空能力、1366/820、焦点和无横向溢出 |
| PROJECT-PLATFORM-S08-M4-T06 | core-user | e2e-real-isolated | real | isolated | No | owner/admin/member/guest/non-member/enterprise-admin 六身份真实隔离 |
| PROJECT-PLATFORM-S08-M4-T07 | core-user | e2e-real-isolated | real | isolated | No | 双上下文并发、离线保留、重开、终止/恢复、correction、backfill/verify |
| PROJECT-PLATFORM-S08-M4-T08 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16、V001-V092、非空旧实例、显式 manifest、失败续跑与校验 |
| PROJECT-PLATFORM-S08-M4-T09 | core-system | system-real-isolated | not-required | isolated | No | 完整 backend/frontend/collaboration/architecture/security/generated/workbench 门禁 |
| PROJECT-PLATFORM-S08-M4-T10 | non-core | integration | not-required | not-required | No | 当前/目标架构、Program、专项索引、模块/对象/事件/运维合同一致 |
| PROJECT-PLATFORM-S08-M4-T11 | non-core | integration | not-required | not-required | No | S09 只共享 command/event SPI，私表和 token/current-state 权威严格隔离 |
| PROJECT-PLATFORM-S08-M4-T12 | non-core | integration | not-required | not-required | No | S08 Go、Program revision 24、Stage none、48 Task 与四份报告一致 |

## Completed Items

- 审计 M1-M3 的 36 项任务、两版 Flyway、运行/发布边界和报告；发现并修复“发布含状态流版本后，新 active draft 从 live 表重建而丢失已发布 `stateFlow`”缺陷，新草稿现在从完整 published snapshot 继承状态流。
- 在项目空间设置交付状态/动作/转换/guard 编辑器，支持永久 key、分类、排序、连接、授权角色、必填字段、声明式 guard、诊断、预览、diff、兼容提示、发布确认和 backfill 管理。
- 在 WorkItem 详情交付 current state、服务端 `availableActions`、必填字段提示、原子字段 patch、不可变历史和 owner/admin correction。失败保留请求 ID 与输入，离线禁用提交并可恢复。
- 后端发布再次强制兼容分析：`blocked` 拒绝发布，`migration_required` 只有显式危险确认才允许创建新版本，既有实例仍保持旧绑定。
- 使用真实 API、PostgreSQL、Redis、MinIO 和六个独立浏览器身份验证完整流；并发动作精确得到一个 200 与一个 409，失败方保留请求，刷新后状态/历史一致。
- 真实验证 1366/820 响应式、超长状态名、键盘动作、能力缺失、guest 最小披露、non-member/enterprise-admin 统一 404，以及 terminal/reopen/terminate/restore/correction/backfill。
- 冻结 S09 准入：只可复用公共 command/event/authorization/guard/aggregate-version/idempotency/outbox SPI；不得复用 S08 current-state/history/backfill 私表或把 token 图降维成 status。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M4-T01 | 48 项可追溯且阻断已修复 | M1-M4 reports、V091/V092、draft publication inheritance 修复 | focused backend + route audit PASS | Not required：审计结论由真实系统流复验 | Done |
| PROJECT-PLATFORM-S08-M4-T02 | 配置器全要素可操作且位于空间设置 | `ProjectWorkItemStateFlowEditor`、DraftPanel | lint/build + API contract PASS | Real isolated: owner 编辑超长状态、动作/转换并保存校验 PASS | Done |
| PROJECT-PLATFORM-S08-M4-T03 | 兼容级别不可绕过且失败保留输入 | publication service + diff/compatibility UI | publication integration PASS | Real isolated: preview/diff/migration confirmation/输入保留 PASS | Done |
| PROJECT-PLATFORM-S08-M4-T04 | 只执行服务端动作且原子提交必填 patch | `WorkItemWorkflowPanel` + workflow API | decision/service/controller integration PASS | Real isolated: member start/complete/reopen/terminate/restore 与历史 PASS | Done |
| PROJECT-PLATFORM-S08-M4-T05 | 键盘、长名称、窄屏和空状态可用 | workflow/editor CSS 与语义化 label/button | frontend lint/build PASS | Real isolated: 1366/820 无横向溢出、长名称完整、键盘执行 PASS | Done |
| PROJECT-PLATFORM-S08-M4-T06 | 六身份配置/执行/纠错/披露准确 | 服务端 projection 与 owner/admin correction UI | identity/access integration PASS | Real isolated: 六个独立身份浏览器矩阵 PASS | Done |
| PROJECT-PLATFORM-S08-M4-T07 | 并发/离线/恢复/backfill 不丢事实 | caller-stable request、backfill panel、refresh | concurrency/replay/backfill integration PASS | Real isolated: 双 context 200/409、offline、reopen/restore/backfill/verify PASS | Done |
| PROJECT-PLATFORM-S08-M4-T08 | 完整迁移与非空恢复有 fresh 证据 | V091/V092、backfill batch/unit、binding guard | PostgreSQL 16 Testcontainers V001-V092 + migration matrix PASS | backfill manifest/execute/verify 真实浏览器 PASS | Done |
| PROJECT-PLATFORM-S08-M4-T09 | 全门禁无阻断且证据可复现 | workbench route-final | full quality gate PASS | `project-platform-s08-m4-route-final.spec.ts` 1 PASS，无 route mock | Done |
| PROJECT-PLATFORM-S08-M4-T10 | 真相文档只声明已实现范围 | Program rev24、roadmap、target/current/module/object/event/matrix | plan/document/architecture checks PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M4-T11 | S09 公共 SPI 与私表禁止项冻结 | target architecture §25、module/event contracts、table owners | architecture/security/schema negative checks PASS | Not required：未实现 S09 UI/runtime | Done |
| PROJECT-PLATFORM-S08-M4-T12 | S08 Go 且 Stage none、S09 Planned | Program/initiative/roadmap/report 同步 | `work:finish --route-final` PASS | route-final 真实浏览器 PASS | Done |

## Code Changes

- `web/src/modules/projectSpaces/components/ProjectWorkItemStateFlowEditor.tsx`
- `web/src/modules/projectSpaces/components/ProjectWorkItemStateBackfillPanel.tsx`
- `web/src/modules/projectSpaces/components/WorkItemWorkflowPanel.tsx`
- `web/src/modules/projectSpaces/components/ProjectWorkItemConfigurationDraftPanel.tsx`
- `web/src/modules/projectSpaces/components/ProjectWorkItemsPanel.tsx`
- `web/src/modules/projectSpaces/api/workItemWorkflowApi.ts`
- `web/src/index.css`
- `web/e2e/project-platform-s08-m4-route-final.spec.ts`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemConfiguration{Draft,Publication}Service.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemStateFlowDecisionService.java`
- `server/src/main/java/com/colla/platform/modules/project/domain/WorkItemStateRuntimeModels.java`
- `server/src/test/java/com/colla/platform/modules/project/{api,application}/WorkItem*Tests.java`
- `docs/{00-product,01-architecture,02-roadmap,90-reports}/**`

## Validation

- Backend tests: `mvn test` 全量 476 项，以及 configuration publication、WorkItem runtime、state foundation/decision/API contract、migration matrix 定向 PostgreSQL 16 Testcontainers，PASS。
- Frontend build: `pnpm --dir web lint` 与 `pnpm web:build`，PASS。
- Local quality gate: `quality-gate-20260726T165322.md` 文档/规划/架构/工作台 preflight PASS；随后 route-final 复验。
- Browser smoke: Real isolated `project-platform-s08-m4-route-final.spec.ts`，1 passed（约 34 秒），无 route mock。
- Migration/system rehearsal: Flyway 完整校验 92 个 migration，空库 V001-V092、历史基线、重复 migrate、非空 WorkItem、显式 backfill/resume/verify，PASS。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

生产切流、目标环境容量/长稳和基础设施 HA 仍需要独立变更窗口与证据；它们不属于 S08 范围，也不由本地 route-final 自动授权。

## Next Steps

- S08 Go；当前 Stage 置 `none`，完成路线等待后续独立归档。
- S09 保持 `Planned`，不得在本次收口中激活或实现；归档 S08 后才能按 revision 24 冻结的准入包生成新的唯一当前路线。
