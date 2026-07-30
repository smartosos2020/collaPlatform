# PROJECT-PLATFORM-S21-M5 Execution Report

## Scope
PROJECT-PLATFORM-S21-M5-T01 到 PROJECT-PLATFORM-S21-M5-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M5-T01 | non-core | integration | not-required | not-required | No | 对账 M4 的 12 项信息架构、角色、兼容与安全边界，并冻结 M5 实现不变量 |
| PROJECT-PLATFORM-S21-M5-T02 | non-core | unit | not-required | not-required | No | 验证五入口顺序、路径、任务层、capability、状态与空态只来自集中注册表 |
| PROJECT-PLATFORM-S21-M5-T03 | core-user | e2e-real-isolated | real | isolated | No | 旧 type/field/layout/sample 深链直达原组件，一级上下文落在设置且 query 不丢失 |
| PROJECT-PLATFORM-S21-M5-T04 | core-user | e2e-real-isolated | real | isolated | No | owner/admin/member/guest 仅看到服务端 `availableActions` 允许的五入口子集 |
| PROJECT-PLATFORM-S21-M5-T05 | core-user | e2e-real-isolated | real | isolated | No | 项目管理聚合定位计划、风险、交付、资源和指标 owner，不复制业务事实 |
| PROJECT-PLATFORM-S21-M5-T06 | core-user | e2e-real-isolated | real | isolated | No | 设置中心定位五组高级配置，并与工作项执行视图分离 |
| PROJECT-PLATFORM-S21-M5-T07 | core-user | e2e-real-isolated | real | isolated | No | 用户+空间偏好保存、刷新、恢复默认、CAS 冲突、跨用户/空间隔离和离线失败关闭 |
| PROJECT-PLATFORM-S21-M5-T08 | core-user | e2e-real-isolated | real | isolated | No | owner/member/guest 与 active/disabled/archived 的落点、只读提示和下一步可解释 |
| PROJECT-PLATFORM-S21-M5-T09 | core-user | e2e-real-isolated | real | isolated | No | 模式切换及旧深链保留 `typeId/savedViewId/panel/source`，前进后退仍由 URL 恢复 |
| PROJECT-PLATFORM-S21-M5-T10 | core-user | e2e-real-isolated | real | isolated | No | realtime/online/focus 只使 REST query 失效；多标签版本冲突和离线写不产生陈旧成功 |
| PROJECT-PLATFORM-S21-M5-T11 | core-user | e2e-real-isolated | real | isolated | No | 六身份验证 capability、直达 URL、无预加载、模式隔离、403/404 与最小披露 |
| PROJECT-PLATFORM-S21-M5-T12 | core-user | e2e-real-isolated | real | isolated | No | 1440/1366/820、键盘 Tab、长名称、深链、离线、CAS 多标签语义及自动清理 |

## Completed Items
- 建立服务端 `view_overview`、`view_work_items`、`view_project_management`、`view_members`、`view_settings` 投影，Web 只消费当前响应中的 capability。
- 将项目空间收敛为概览、工作项、项目管理、成员、设置五入口；项目管理复用计划/风险/交付/资源/指标 owner，设置中心按五类高级能力渐进展开。
- 新增用户+空间维度的版本化简洁/高级模式偏好，支持恢复默认、CAS 冲突、跨用户/空间隔离和管理 capability 校准。
- 保留旧类型、字段、布局和样例深链；模式切换不重写 URL，原 query 和执行上下文继续由路由恢复。
- 增加六身份真实隔离 E2E，覆盖三视口、键盘、长名称、离线失败关闭、只读状态和清理闭环。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M5-T01 | M4 12 项可追溯且实现不改写冻结合同 | M4 execution/audit 与 `project-platform-s21-m5-implementation-audit.md` | 实现审计逐项映射导航、路由、身份、偏好和浏览器约束 | not-required：本项为实现前治理对账 | Done |
| PROJECT-PLATFORM-S21-M5-T02 | 导航元数据只有一个权威注册表 | `projectSpaceInformationArchitecture.ts` 的 `PROJECT_SPACE_PRIMARY_NAVIGATION` | `projectSpaceInformationArchitecture.test.ts` 验证顺序、capability、状态和 persona 子集 | T11/T12 从真实 Shell 验证注册表输出 | Done |
| PROJECT-PLATFORM-S21-M5-T03 | 旧配置深链可直达且无循环 | `resolveProjectSpaceRouteContext` 与 router 的兼容路径；原 owner 组件继续复用 | route-context 单测覆盖 type/field/layout/sample/work-item 路径 | real isolated M5 E2E 验证 `/types/:typeId`、`panel/source` 和设置一级上下文 | Done |
| PROJECT-PLATFORM-S21-M5-T04 | 五入口按 capability 显隐且无权内容不挂载 | `ProjectSpaceShell`、`getVisibleProjectSpacePrimaryNavigation`、`ProjectSpaceDtos` | DTO 与 IA 单测覆盖 owner/admin/member/guest capability 集合 | real isolated M5 E2E 逐身份验证导航 DOM 和受限请求不存在 | Done |
| PROJECT-PLATFORM-S21-M5-T05 | 项目管理聚合现有 owner 且不复制事实 | `ProjectSpaceManagementPanel` 复用 `ProjectWorkItemsPanel`、Metric panels | secondary-tab 顺序测试与前端 build/lint | real isolated E2E 定位计划、风险、交付、资源、指标及真实 panel | Done |
| PROJECT-PLATFORM-S21-M5-T06 | 五类高级配置可定位且执行/配置分离 | `ProjectSpaceSettingsPanel`、`ProjectSpaceSecondaryTabs`、高级配置注册表 | settings tab 配置顺序与 manager-only 单测 | real isolated E2E 在 advanced 模式验证五组 tab 和 `project-space-advanced-settings` | Done |
| PROJECT-PLATFORM-S21-M5-T07 | 偏好版本化、隔离、不授权且可恢复默认 | V140、preference controller/service/repository、Web API client 与 mode switch | service/repository 集成测试覆盖默认、保存、reset、409、隔离与 capability 校准 | real isolated E2E 验证刷新保存、跨用户/空间隔离、reset、冲突及离线禁用 | Done |
| PROJECT-PLATFORM-S21-M5-T08 | 身份与状态均有安全落点和解释 | `defaultProjectSpacePath`、导航注册表状态规则、Shell 只读 Alert | IA persona/status 单测与 DTO capability 测试 | real isolated E2E 验证 member/guest 入口、disabled/archived 概览及语义 Alert | Done |
| PROJECT-PLATFORM-S21-M5-T09 | 模式、刷新、历史和深链不丢上下文 | React Router URL 为上下文权威；secondary tabs 只改目标 query；mode mutation 不导航 | route resolver 与 secondary-tab query 单测 | real isolated E2E 验证 `typeId/savedViewId/panel/source` 在切换和兼容深链中保留 | Done |
| PROJECT-PLATFORM-S21-M5-T10 | 事件只触发 REST 校准且离线不伪造成功 | `AppRealtimeBoundary` 失效 `project-spaces` query；React Query online/focus 校准；preference CAS | service/repository 409 测试与 E2E stale version 断言 | real isolated E2E 验证断网时写控件禁用且无请求，恢复 online/focus 后从 REST 确认 advanced | Done |
| PROJECT-PLATFORM-S21-M5-T11 | 六身份、导航、路由和偏好不能越权 | capability DTO、visible-space gate、advanced `canManage` gate | DTO/service/repository 测试与 `project-platform-s21-m5.spec.ts` | real isolated E2E 覆盖 owner/admin/member/guest/outsider/enterprise-admin API flow | Done |
| PROJECT-PLATFORM-S21-M5-T12 | 三视口、键盘、长名、深链、离线、多标签语义通过 | 响应式 Shell、语义 nav/alert、版本化 preference | Playwright 规格 discovery/compile 与 isolated fixture cleanup | real isolated E2E 覆盖 1440/1366/820 无横向溢出、Tab 焦点、长名称、截图、离线和 CAS | Done |

## Code Changes
- 新增 V140 偏好表、preference controller/service/repository/domain 与单元/集成测试。
- 扩展项目空间 DTO 的五入口 capability，并新增集中 IA/route/persona/viewport 合同。
- 重构项目空间运行 Shell、项目管理聚合、设置中心、高级模式和二级 Tab 组合。
- 新增 M5 implementation audit 与真实隔离 E2E 规格。

## Validation
- Backend compile: `mvn -q -DskipTests compile`。
- Backend tests: `mvn -q -f server/pom.xml -Dtest=ProjectSpaceExperiencePreferenceServiceTests test` — PASS；默认 simple、advanced capability、reset、409 和五入口投影均通过，fresh log `.local-reports/quality-gate-20260730T021511-backend-targeted-tests.log`。
- PostgreSQL/Testcontainers: `ProjectSpaceExperiencePreferenceRepositoryIntegrationTests`；空库 V001-V140、CAS、reset 与跨用户隔离通过。
- Frontend build: `pnpm web:lint`、`pnpm web:build` — PASS；fresh logs `.local-reports/quality-gate-20260730T021847-frontend-lint.log`、`.local-reports/quality-gate-20260730T021847-frontend-build.log`。
- Local quality gate: fresh stage report `.local-reports/quality-gate-20260730T021847.md`。
- E2E specification: `playwright test e2e/project-platform-s21-m5.spec.ts --config e2e/playwright.config.ts --list` 发现 1 个 smoke flow；专项 ESLint 通过。
- Browser smoke: real isolated `project-platform-s21-m5.spec.ts`，使用 API 创建/归档空间并下线临时身份。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- Engineering Go：完成 M5 checkpoint 后独立启动 PROJECT-PLATFORM-S21-M6；M6 只消费现有公共 owner，不创建第二配置权威。
- 不提前启动 M8 真人试用，不把自动化或维护者证据写成人工结论。
