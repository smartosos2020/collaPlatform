---
title: PILOT-V2-M6 Execution Report
status: completed
milestone: PILOT-V2-M6
updated_at: 2026-07-13
---

# PILOT-V2-M6 Execution Report

## Scope

- PILOT-V2-M6-T01 到 PILOT-V2-M6-T08
- 本轮针对审计 Reopened 状态重新执行；隔离 PostgreSQL、后端进程和 MinIO 文件均为一次性验收资源，共享开发数据库未重置。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M6-T01 | e2e-real-isolated | real | isolated | No | 普通成员真实登录后更新资料、上传头像、修改密码并以新密码重新登录，核对设备与安全摘要。 |
| PILOT-V2-M6-T02 | e2e-real-isolated | real | isolated | No | 普通成员修改通知和工作偏好，刷新后核对真实 API 持久化和必要通知保护。 |
| PILOT-V2-M6-T03 | e2e-real-isolated | real | isolated | No | 普通成员使用真实隔离对象数据通过卡片、详情路径和搜索核对标题与路径一致。 |
| PILOT-V2-M6-T04 | e2e-real-isolated | real | isolated | No | 普通成员触发真实 empty、denied、offline 状态并核对解释性反馈。 |
| PILOT-V2-M6-T05 | e2e-real-isolated | real | isolated | No | 普通成员真实遍历用户工作台入口，确认无内部版本、迁移或治理文案及管理导航泄漏。 |
| PILOT-V2-M6-T06 | e2e-real-isolated | real | isolated | No | 真实普通成员流程复核 1366、1440 和窄屏页面，无页面级横向溢出。 |
| PILOT-V2-M6-T07 | e2e-real-isolated | real | isolated | No | 使用键盘完成普通成员核心操作，核对焦点、可读名称和结果。 |
| PILOT-V2-M6-T08 | e2e-real-isolated | real | isolated | No | 普通成员真实完成 IM、项目、知识库、Base、审批、通知和搜索规定任务，全程不访问管理后台。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M6-T01 | Done | 真实创建成员、登录、更新资料、上传头像、改密并用新密码重新登录。 |
| PILOT-V2-M6-T02 | Done | 真实修改通知与紧凑卡片偏好，刷新并由 API 核对持久化，必要开关保持禁用。 |
| PILOT-V2-M6-T03 | Done | 真实创建知识库、项目、Base，通过卡片、详情和搜索核对对象。 |
| PILOT-V2-M6-T04 | Done | 真实核对通知 empty、403 denied 与 offline banner。 |
| PILOT-V2-M6-T05 | Done | 真实普通成员页面无管理搜索、版本、迁移或治理文案。 |
| PILOT-V2-M6-T06 | Done | 1366、1440、390 宽度真实设置页页面级横向溢出为 0。 |
| PILOT-V2-M6-T07 | Done | 真实使用 Tab、Enter、Space 完成保存与开关操作。 |
| PILOT-V2-M6-T08 | Done | 真实普通成员依次访问 IM、项目、知识库、Base、审批、通知和搜索。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M6-T01 | 用户可维护个人信息并查看安全状态 | `UserSettingsPage`、Auth profile/password API、头像文件流程和登录设备摘要 | `AuthControllerIntegrationTests`、`DeviceControllerIntegrationTests`；32 个 M6 定向集成测试全通过 | real isolated：Playwright 真实成员资料、头像、改密、重新登录 | Done |
| PILOT-V2-M6-T02 | 设置即时生效且有合理默认值 | 通知偏好 API 与工作偏好实现 | `NotificationPermissionIntegrationTests`；刷新与 API 持久化核对通过 | real isolated：刷新后偏好保持且必要开关禁用 | Done |
| PILOT-V2-M6-T03 | 相同对象在各入口标题、图标和路径一致 | 对象卡片、项目/知识库/Base 路由与搜索实现 | `WorkspaceControllerIntegrationTests`、`KnowledgeContentControllerIntegrationTests`、`ProjectControllerIntegrationTests`、`BaseControllerIntegrationTests` | real isolated：创建对象后卡片、详情和搜索结果一致 | Done |
| PILOT-V2-M6-T04 | 核心页面无原始异常或无解释空白 | 通知 empty、403 denied、offline banner 状态组件 | 后端定向测试、前端 lint/build 通过 | real isolated：通知 empty、403 和 offline banner 可见 | Done |
| PILOT-V2-M6-T05 | 普通用户界面不展示内部版本和治理术语 | 用户工作台路由与普通成员壳层隔离 | `AuthControllerIntegrationTests`、`WorkspaceControllerIntegrationTests` | real isolated：无管理搜索、版本、迁移或治理文案 | Done |
| PILOT-V2-M6-T06 | 无文字竖排、遮挡、页面级异常滚动 | 响应式设置页布局 | 前端 lint/build、chunk budget、route lazy-loading 门禁通过 | real isolated：1366/1440/390 viewport overflow <= 1px | Done |
| PILOT-V2-M6-T07 | 核心操作可键盘完成，图标按钮有可读名称 | 设置页控件 aria 名称与键盘处理 | 前端 lint/build 通过 | real isolated：Tab、Enter、Space 保存与切换通过 | Done |
| PILOT-V2-M6-T08 | 无需进入管理后台即可完成规定核心任务 | IM、项目、知识库、Base、审批、通知、搜索用户路由 | `ImControllerIntegrationTests`、`ApprovalControllerIntegrationTests`、`SearchCollaborationIntegrationTests` | real isolated：普通成员全链路访问通过，无后台跳转 | Done |

## Code Changes

- Backend: 复用既有个人资料、头像引用、密码变更、通知、对象和工作台 API；验证日志使用语句级时间戳并稳定按最新记录返回，避免同一事务时间戳导致验收顺序抖动。
- Frontend: 复用既有设置、状态、对象路由和可访问性实现；新增无 mock 的 `web/e2e/m6-member-workspace-real.spec.ts`。
- Database: 一次性 PostgreSQL 隔离库执行既有 49 条 Flyway 迁移，无新增迁移、无共享库重置。
- Scripts: `scripts/ai-quality-gate.ps1` 将 Acceptance Evidence 限定在对应报告分节，避免与 Verification Contract 行重复计数；真实 E2E 不放入 AI 工作循环脚本目录。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | M6 T01–T08 更新为 Done | 真实隔离浏览器闭环与定向质量门禁通过。 |
| `docs/90-reports/pilot-v2-m6-execution-report.md` | 完成报告并保留 v2 Verification Contract | 为每项任务记录 real isolated 证据和无阻塞 Gap。 |

## Validation

- Backend tests: `mvn -Dtest=AuthControllerIntegrationTests,DeviceControllerIntegrationTests,NotificationPermissionIntegrationTests,WorkspaceControllerIntegrationTests,KnowledgeContentControllerIntegrationTests,ProjectControllerIntegrationTests,BaseControllerIntegrationTests,ImControllerIntegrationTests,ApprovalControllerIntegrationTests,SearchCollaborationIntegrationTests test`；32 tests, 0 failures。
- Frontend build: `pnpm web:lint` 与 `pnpm web:build` 通过，chunk budget、route lazy-loading、security audit、Flyway order 和文档契约门禁通过。
- Local quality gate: `scripts/ai-work-cycle.ps1 -Stage finish` stage profile 通过；浏览器和后端日志写入 `.local-reports`。
- Browser smoke: `pnpm --dir web exec playwright test e2e/m6-member-workspace-real.spec.ts --config e2e/playwright.config.ts --reporter=line`；real isolated 1 passed；无 `page.route` 或 API mock。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 无与 M6 预期任务相关的剩余缺口；一次性隔离数据库和后端进程已在验收后销毁，共享开发数据未重置。 | non-blocking | 本轮 finish 已闭环。 |

## Next Steps

- M6 已按 v2 AI 工作循环完成 `PILOT-V2-M6-T01` 到 `PILOT-V2-M6-T08`；M7 仍以审计 Reopened 状态保留，待下一轮单独推进。
