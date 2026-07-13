---
title: PILOT-V2-M7 Execution Report
status: archived
milestone: PILOT-V2-M7
updated_at: 2026-07-13
---

# PILOT-V2-M7 Execution Report

## Scope

- PILOT-V2-M7-T01 到 PILOT-V2-M7-T09

前一轮已完成 T01–T08；本轮按审计后的 v2 工作循环单独重开并验收 T09，补齐管理员连续任务闭环。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M7-T01 | e2e-real-isolated | real | isolated | No | 管理员真实登录，从企业概览下钻组织健康、成员治理、权限风险、审计摘要和系统健康入口。 |
| PILOT-V2-M7-T02 | e2e-real-isolated | real | isolated | No | 管理员与仅有 org.view 的自定义角色真实访问系统设置，核对企业信息、策略和运行信息边界。 |
| PILOT-V2-M7-T03 | e2e-real-isolated | real | isolated | No | 管理员真实查看安全策略、登录设备与必要通知，并撤销一个非当前登录设备。 |
| PILOT-V2-M7-T04 | e2e-real-isolated | real | isolated | No | 管理员真实执行成员离职交接，核对停用账号、交接结果和 user.offboarded 审计。 |
| PILOT-V2-M7-T05 | e2e-real-isolated | real | isolated | No | 管理员真实对成员、部门、用户组和角色执行批量预览、权限检查、确认和结果报告。 |
| PILOT-V2-M7-T06 | e2e-real-isolated | real | isolated | No | 管理员在真实过期权限风险数据下查看来源、影响、建议并确认修复，核对风险消失。 |
| PILOT-V2-M7-T07 | e2e-real-isolated | real | isolated | No | 管理员真实导出审计日志，核对最小字段展示和可下载 CSV。 |
| PILOT-V2-M7-T08 | e2e-real-isolated | real | isolated | No | 管理员真实遍历管理模块和新增批量治理页面，并在 390x844 视口核对无横向溢出。 |
| PILOT-V2-M7-T09 | e2e-real-isolated | real | isolated | No | 管理员在同一真实隔离会话连续完成组织、成员、权限风险和审计操作，不伪造 token 或后台 API。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M7-T01 | Done | 真实隔离管理员剧本完成概览五类指标下钻。 |
| PILOT-V2-M7-T02 | Done | 系统设置 API 和只读页面完成管理员及 org.view 边界验证。 |
| PILOT-V2-M7-T03 | Done | 安全策略、设备列表、必要通知和设备撤销完成真实闭环。 |
| PILOT-V2-M7-T04 | Done | 离职交接、账号停用及审计查询完成真实闭环。 |
| PILOT-V2-M7-T05 | Done | 批量治理入口覆盖成员、部门、用户组、角色四类资源。 |
| PILOT-V2-M7-T06 | Done | 过期权限风险来源、建议、确认修复和风险刷新完成真实闭环。 |
| PILOT-V2-M7-T07 | Done | 审计 CSV 最小字段和公式前缀中和完成真实下载验证。 |
| PILOT-V2-M7-T08 | Done | 管理模块路由、页面状态和窄视口布局完成真实遍历验证。 |
| PILOT-V2-M7-T09 | Done | 同一管理员真实隔离会话连续完成组织、账号、权限风险和审计操作。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M7-T01 | 企业概览的组织、成员、风险、审计和健康指标可行动下钻 | `AdminOverviewPage` 五类查询和入口链接；`router.tsx` 管理路由 | 目标后端 36 项回归通过；web lint/build 通过 | real isolated `web/e2e/m7-admin-real.spec.ts`：概览入口和目标页面真实导航通过 | Done |
| PILOT-V2-M7-T02 | 企业信息、默认策略和运行信息按权限边界只读展示 | `AdminSystemSettingsController`、`AdminSystemSettingsService`、只读提示；`PermissionService` 的 org.view 边界 | `AdminSystemSettingsControllerIntegrationTests` 及目标后端回归通过 | real isolated：管理员和 org.view 角色都从真实 API 读取设置并显示只读页 | Done |
| PILOT-V2-M7-T03 | 安全策略、会话设备和必要通知可查看并执行设备撤销 | `AdminSecurityPage`、设备 API 和撤销服务 | `DeviceControllerIntegrationTests` 及目标后端回归通过 | real isolated：真实创建第二设备、点击撤销并收到成功提示 | Done |
| PILOT-V2-M7-T04 | 成员离职交接后账号停用且审计可追溯 | 离职交接事务转移知识库和会话所有权并记录 `user.offboarded` | `AdminSystemSettingsControllerIntegrationTests` 离职交接用例及目标后端回归通过 | real isolated：真实 offboard、核对成员 disabled 和审计记录 | Done |
| PILOT-V2-M7-T05 | 批量动作具备预览、权限检查、确认和结果报告 | `AdminBatchGovernanceController`、`AdminBatchGovernanceService`、批量治理页面；覆盖四类资源 | 批量治理集成测试、角色/组织/用户组回归及 web lint/build 通过 | real isolated：成员、部门、用户组、角色逐项 preview/execute，核对状态 disabled | Done |
| PILOT-V2-M7-T06 | 风险来源、影响范围、建议和处置结果同屏可追溯 | `PermissionGovernanceService` 风险详情与确认修复审计 | `PermissionGovernanceControllerIntegrationTests`、`PermissionDecisionIntegrationTests` 通过 | real isolated：真实过期权限风险确认修复后从 risks 消失 | Done |
| PILOT-V2-M7-T07 | 审计导出受权限控制且 CSV 公式前缀安全 | `AuditService` 导出列最小化并为 `=+-@` 前缀添加安全转义 | CSV 公式中和集成测试和目标后端回归通过 | real isolated：真实下载 `audit-logs.csv` 并核对最小字段提示 | Done |
| PILOT-V2-M7-T08 | 管理模块和新增页面保持一致状态与窄屏布局 | 管理导航、路由、批量治理页面和统一状态文案 | web lint/build 通过；目标后端回归通过 | real isolated：11 个管理路由真实遍历，390x844 scrollWidth 溢出不超过 1px | Done |
| PILOT-V2-M7-T09 | 管理员可独立完成组织、账号、权限和审计操作 | `m7-admin-real.spec.ts` 将真实登录、组织/成员操作、权限风险修复和审计导出串成同一会话剧本 | 目标后端 36 项回归、backend package、web lint/build 通过 | real isolated：同一管理员会话连续执行组织、成员、风险修复、审计导出并通过 | Done |

## Code Changes

- Backend: 新增批量治理 capabilities、preview、confirm execute API，支持 users、departments、user_groups、roles；修正 roles 表停用 SQL；保留系统设置、离职交接、设备撤销和风险治理闭环；审计 CSV 中和公式前缀。
- Frontend: 新增批量治理 API、页面、导航和路由；补目标 ID 可访问标签及真实管理员页面状态。
- E2E: `web/e2e/m7-admin-real.spec.ts` 无 `page.route`、`route.fulfill` 或伪造 API/token；加入真实设备撤销、部门/用户组/角色批量验证、响应等待防线和 T09 连续管理员任务剧本。
- Tests: 新增 `AdminSystemSettingsControllerIntegrationTests` 批量与 CSV 安全覆盖。
- Database: 无新增迁移，使用隔离 PostgreSQL 数据库验证现有表结构。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | M7 milestone and T01–T09 set Done | 与两轮真实隔离证据一致 |
| `docs/90-reports/pilot-v2-m7-execution-report.md` | Replaced reopened template with v2 contract and fresh evidence | 防止 mock 浏览器证据或未声明范围通过验收 |

## Validation

- Backend tests: targeted Maven integration suite, Tests run 36, Failures 0, Errors 0, BUILD SUCCESS。
- Frontend build: `pnpm web:lint` passed and `pnpm web:build` passed。
- Local quality gate: T09 formal finish stage passed；backend targeted tests, backend package, frontend lint/build, chunk/route/security/migration/documentation gates all PASS (`.local-reports/quality-gate-20260713-151452.md`)。
- Browser smoke: T01–T08 finish 与本轮 T09 均使用 `COLLA_E2E_ISOLATED=true`、隔离 PostgreSQL/backend 8081 和 Vite 5174；T09 finish 的 `m7-admin-real.spec.ts` 1 passed（日志 `.local-reports/work-cycle-browser-20260713-151429.log`），real browser and no mocks。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 本轮 T01–T09 无验收阻塞缺口；M8 不因本轮自动启动。 | non-blocking | 等待下一轮明确范围。 |

## Next Steps

- M7 T01–T09 已完成；后续如推进 M8，仍需另起 AI 工作循环并声明范围。
- 继续保留隔离数据库和真实浏览器证据要求，不复用旧 mock 剧本。
