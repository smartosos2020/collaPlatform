# PROJECT-PLATFORM-S09-M5 Execution Report

## Scope

PROJECT-PLATFORM-S09-M5-T01 到 PROJECT-PLATFORM-S09-M5-T12

本里程碑完成复杂节点流的空间配置设计器、成员执行面、显式 backfill 入口、真实隔离浏览器验收和 S09 Stage 收口。它不实现 S10 关系引擎、S14 高级视图、S16 工时/容量或 S17 自动化。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M5-T01 | non-core | integration | not-required | not-required | No | M1-M4 的 48 项报告、V093-V096、边界和 gap 逐项反查 |
| PROJECT-PLATFORM-S09-M5-T02 | core-user | e2e-real-isolated | real | isolated | No | owner 在空间配置中创建/选择/移动/删除节点和边，使用缩放、键盘与诊断 |
| PROJECT-PLATFORM-S09-M5-T03 | core-user | e2e-real-isolated | real | isolated | No | 图选中节点配置处理人、表单、交付物、时限、分支/汇聚、恢复与补偿 |
| PROJECT-PLATFORM-S09-M5-T04 | core-user | e2e-real-isolated | real | isolated | No | 草稿保存、校验、diff、compatibility、发布阻断/确认与 rollback 入口 |
| PROJECT-PLATFORM-S09-M5-T05 | core-user | e2e-real-isolated | real | isolated | No | 成员实例 token、任务、动作、历史、刷新、409/422/超时与离线恢复 |
| PROJECT-PLATFORM-S09-M5-T06 | core-user | e2e-real-isolated | real | isolated | No | claim/vote/withdraw/complete/transfer、表单、交付物 manifest、候选与到期展示 |
| PROJECT-PLATFORM-S09-M5-T07 | core-user | e2e-real-isolated | real | isolated | No | 1440/1366/820、长名称、密集图、空态、键盘、焦点与替代列表导航 |
| PROJECT-PLATFORM-S09-M5-T08 | core-user | e2e-real-isolated | real | isolated | No | owner/admin/member/guest/non-member/enterprise-admin 六身份真实最小披露 |
| PROJECT-PLATFORM-S09-M5-T09 | core-user | e2e-real-isolated | real | isolated | No | split/join、会签、并发、离线、恢复/补偿/升级/backfill 后刷新一致 |
| PROJECT-PLATFORM-S09-M5-T10 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16、V001-V096、后端/前端/协作/架构/安全/生成物完整门禁 |
| PROJECT-PLATFORM-S09-M5-T11 | non-core | integration | not-required | not-required | No | 当前/目标架构、Program、专项索引、模块/对象/事件/运维合同与 S10 准入一致 |
| PROJECT-PLATFORM-S09-M5-T12 | non-core | integration | not-required | not-required | No | S09 Go、Program revision 26、Stage none、60 Task 与五份报告一致 |

## Completed Items

- 逐项反查 M1-M4 的 48 个 Task、四份报告、V093-V096、独立 table owner、S08 私表禁止依赖与恢复 runbook；没有以 Remaining Gap 弱化关闭标准。
- 在项目空间配置页交付节点流设计器：阶段/节点/边的创建、选择、移动、删除、缩放、键盘排序、稳定 key、本地引用诊断，以及分支、汇聚、恢复、补偿高级策略面板。
- 节点选中态承载处理策略、候选角色、quorum、表单、指派、交付物与时限 snapshot JSON；保存继续写 S06 唯一草稿，移除互斥 `stateFlow`，不创建第二发布权威。
- 复用现有配置草稿的校验、diff、compatibility、blocked/migration_required 发布门禁、危险确认、版本历史和 rollback；失败不清空设计器本地状态。
- 在 WorkItem 详情交付服务端 capability 驱动的节点实例图、active token、任务上下文、动作、候选数、时限、交付物策略/manifest、恢复、显式升级和分页不可变历史。
- 409/422 会刷新服务端事实但保留 field patch、decision、transfer、artifact、recovery 和 node-map 输入；超时和离线不提交命令，联网后可以继续。
- 交付显式 node backfill manifest、续跑和 verify 面板，不扫描或静默初始化 pre-S09 WorkItem。
- 新增可重复的隔离 route-final runner。真实浏览器门禁发现并修复 Windows 后台日志句柄、预置 schema 显式发布、前端 capability 猜测和历史分页 DTO 解包四个实际问题。
- 使用独立 PostgreSQL/Flyway、独立后端/前端和六个动态身份通过真实浏览器验收；验证 owner/admin/member/guest 内容投影以及 outsider/enterprise-admin 统一 404。
- 冻结 S10 准入：关系/层级/依赖只能复用规范 WorkItem identity、空间授权和公共 command/event SPI，不得读取 node instance/token/task/vote/join/history/backfill 私表，也不得把流程边当工作项关系。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M5-T01 | 48 项可追溯且无未处理阻断 | M1-M4 reports、V093-V096、table owners、runbook | roadmap/report/architecture audit PASS | Not required；实现由后续真实流反证 | Done |
| PROJECT-PLATFORM-S09-M5-T02 | 图元素可视化编辑、缩放、键盘与诊断可用 | `ProjectWorkItemNodeFlowDesigner`、响应式 CSS | lint/build PASS | Real isolated：8 节点预置、125% 缩放、键盘移动与 dirty 状态 PASS | Done |
| PROJECT-PLATFORM-S09-M5-T03 | 节点配置与图选中态一致且未知值失败关闭 | designer node/policy/recovery panels + server validator | configuration definition tests PASS | Real isolated：空间配置页读取完整 v3 snapshot PASS | Done |
| PROJECT-PLATFORM-S09-M5-T04 | 草稿/发布边界与兼容阻断不可绕过 | DraftPanel、diff/compatibility/version/rollback | publication integration PASS | Real isolated：显式 validate/publish schema v3 后才允许实例化 PASS | Done |
| PROJECT-PLATFORM-S09-M5-T05 | 只呈现服务端实例/task/history 事实并可恢复 | `WorkItemNodeWorkflowPanel`、node API adapter | service/controller + lint/build PASS | Real isolated：plan token/task/history、offline/refresh PASS | Done |
| PROJECT-PLATFORM-S09-M5-T06 | 会签/表单/交付物/转交/到期交互不泄露 | TaskExecution、candidate/due/artifact policy/manifest | node collaboration tests PASS | Real isolated：member 可见 claim；guest 无 task/action；私有策略不披露 PASS | Done |
| PROJECT-PLATFORM-S09-M5-T07 | 关键视口、长名称、键盘、焦点与替代导航可用 | semantic button/tab/list、820 media rules | frontend lint/build PASS | Real isolated：1440/1366/820、长标题、无页面横向溢出、截图 PASS | Done |
| PROJECT-PLATFORM-S09-M5-T08 | 六身份授权和最小披露准确 | server decision/capability DTO + UI | six-identity integration PASS | Real isolated：owner/admin/member/guest/outsider/enterprise-admin 矩阵 PASS | Done |
| PROJECT-PLATFORM-S09-M5-T09 | 并发/分支汇聚/恢复升级后事实一致 | node workflow/recovery/backfill services + UI refresh | split/join/vote/concurrency/fault/recovery/upgrade/backfill integration PASS | Real isolated：实例/task/history/离线刷新；无 route mock PASS | Done |
| PROJECT-PLATFORM-S09-M5-T10 | full gate、迁移和生成物无阻断 | V093-V096、isolated runner、workbench | `work:finish --validation-profile route-final` 全量门禁 PASS | 独立系统证据由 PostgreSQL 集成命令生成 | Done |
| PROJECT-PLATFORM-S09-M5-T11 | 文档只声明实现事实且冻结 S10 禁止项 | Program rev26、target/current/module/object/event/matrix/runbook | planning/document/architecture checks PASS | Not required | Done |
| PROJECT-PLATFORM-S09-M5-T12 | S09 Go、Stage none、60 Task/五报告一致 | Program/roadmap/index/report 同步 | route-final PASS | real isolated route-final PASS | Done |

## Code Changes

- `web/src/modules/projectSpaces/components/ProjectWorkItemNodeFlowDesigner.tsx`
- `web/src/modules/projectSpaces/components/ProjectWorkItemNodeBackfillPanel.tsx`
- `web/src/modules/projectSpaces/components/WorkItemNodeWorkflowPanel.tsx`
- `web/src/modules/projectSpaces/components/ProjectWorkItemConfigurationDraftPanel.tsx`
- `web/src/modules/projectSpaces/components/ProjectWorkItemsPanel.tsx`
- `web/src/modules/projectSpaces/api/workItemNodeWorkflowApi.ts`
- `web/e2e/project-platform-s09-m5-route-final.spec.ts`
- `tools/workbench/src/browser/smoke.ts`
- `tools/workbench/src/commands/browser.ts`
- `server/src/test/java/com/colla/platform/modules/event/application/ReliableDomainEventWorkerIntegrationTests.java`
- `package.json`
- `web/src/index.css`
- `docs/{00-product,01-architecture,02-roadmap,90-reports}/**`

## Validation

- Backend tests: `mvn test` 全量 502 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS；可靠事件 worker 的虚拟化时钟压力窗口加固后专项 6/6 PASS；最终证据 `.local-reports/quality-gate-20260726T214020-backend-tests.log`。
- Frontend build: `pnpm web:lint` 与 `pnpm web:build` PASS；最终证据 `.local-reports/quality-gate-20260726T214020-frontend-lint.log`、`.local-reports/quality-gate-20260726T214020-frontend-build.log`。
- Local quality gate: `.local-reports/quality-gate-20260726T214020.md`，full/route-final 全部门禁 PASS，无 warning、无 failure。
- Browser smoke: `pnpm smoke:s09-m5-isolated` 与 route-final browser evidence 均 1 passed；独立 PostgreSQL/Flyway、独立服务、六动态身份，无 route mock；最终证据 `.local-reports/work-cycle-browser-20260726T214020.log`。
- Architecture: `architecture:contracts` 与 `architecture:boundaries` PASS；15 modules、138 active tables、26 public contracts、93 精确只读例外。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

生产切流、目标环境容量/长稳和基础设施 HA 仍需要独立变更窗口、备份、观测和批准；它们不属于 S09，也不由本地 route-final 自动授权。

## Next Steps

- S09 Go；当前 Stage 置 `none`，完成路线等待独立归档。
- S10 保持 `Planned`，只在 S09 路线归档后依据冻结准入包生成并激活唯一当前路线。
