---
title: UI-SPLIT-M2 执行报告
status: archived
milestone: UI-SPLIT-M2
updated_at: 2026-07-05
---

# UI-SPLIT-M2 执行报告

## 本轮范围

UI-SPLIT-M2-T01 到 UI-SPLIT-M2-T08：双 Shell 路由骨架与导航拆分。

本轮目标是把用户工作台和管理后台从同一个 `AppLayout` 中拆开。用户侧主菜单只保留日常协作入口；后台侧通过独立 Shell 承载企业概览、组织架构、成员管理、用户组、角色权限、权限治理和审计日志。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M2-T01 | Done | 新增 `web/src/app/layout/UserWorkspaceShell.tsx`，承载工作台、消息、项目、知识库、表格、审批、通知、设备和搜索。 |
| UI-SPLIT-M2-T02 | Done | 新增 `web/src/app/layout/AdminConsoleShell.tsx` 和 `web/src/modules/admin/pages/AdminOverviewPage.tsx`，`/admin/*` 使用独立后台 Shell。 |
| UI-SPLIT-M2-T03 | Done | 用户侧左侧顶部新增头像/用户菜单；有后台权限时，菜单底部显示“管理后台”。 |
| UI-SPLIT-M2-T04 | Done | `web/src/app/router.tsx` 拆分用户 Shell 和后台 Shell；用户主菜单移除“管理”，后台菜单不复用用户侧协作菜单。 |
| UI-SPLIT-M2-T05 | Done | 用户侧搜索文案保持内容搜索；后台侧搜索文案改为成员、部门、用户组、角色、审计，并跳转后台审计查询语境。 |
| UI-SPLIT-M2-T06 | Done | 用户账号区移入头像菜单；后台头部显示管理员身份、返回工作台和退出。 |
| UI-SPLIT-M2-T07 | Done | 新增 `web/src/modules/auth/authorization.ts` 和 `RequireAdmin`，按 admin 角色或后台权限码守卫 `/admin/*`，无权限进入 `/error/403`。 |
| UI-SPLIT-M2-T08 | Done | 前端 lint/build、工作循环 checkpoint、浏览器冒烟和 finish 均已通过。 |

## 代码变更

- 后端：无。
- 前端：
  - 新增 `AuthenticatedRoot`，避免路由文件直接声明组件触发 Fast Refresh 规则。
  - 新增 `UserWorkspaceShell`，用户主菜单移除“管理”，头像菜单承载后台入口和退出。
  - 新增 `AdminConsoleShell`，后台拥有独立侧栏、后台搜索、返回工作台和退出。
  - 新增 `AdminOverviewPage`，作为管理后台第一版“企业概览”入口。
  - 新增 `RequireAdmin` 和 `canAccessAdmin`，守卫后台路由。
  - 重构 `router.tsx`，把 `/admin/*` 与用户协作路由拆到不同 Shell。
  - 更新 `web/src/index.css`，补充用户头像菜单、后台 Shell、企业概览和窄屏降级样式。
- 数据库：无。
- 脚本：无。

## 文档变更

| 文档 | 动作 | 原因 |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M2-T01 到 T08 标记 Done，并把下一步入口推进到 UI-SPLIT-M3。 | 固定本轮 Shell 和导航拆分结果。 |
| `docs/00-product/current-product-scope.md` | 更新双 UI 产品边界为 M2 后当前事实。 | 说明当前已经有用户工作台 Shell 与管理后台 Shell。 |
| `docs/01-architecture/current-architecture.md` | 更新前端 Shell、后台权限守卫和 Admin Shell 实现事实。 | 从架构层固定当前拆分方式和后台权限判定规则。 |
| `docs/90-reports/m2-execution-report.md` | 覆盖旧 M2 报告为 UI-SPLIT-M2 报告。 | 满足工作循环报告契约。 |

## 验证

- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- `pnpm work:checkpoint -Goal "UI-SPLIT-M2" -GateMode quick`：通过。后端 `mvn -DskipTests test`、前端 lint/build、安全审计、Flyway 顺序、文档结构均 PASS。报告 `.local-reports/quality-gate-20260705-142025.md`。
- `pnpm work:finish -Goal "UI-SPLIT-M2"`：通过。stage profile 执行后端 `mvn -DskipTests test`、`mvn -DskipTests package`、前端 lint/build、安全审计、Flyway 顺序、文档结构和工作循环文档契约，均 PASS。报告 `.local-reports/quality-gate-20260705-143023.md`。
- 浏览器冒烟：通过。使用 `admin / admin123456` 登录本地 `http://127.0.0.1:5173`，验证：
  - `/` 使用 `UserWorkspaceShell`，左侧菜单只有工作台、消息、项目、知识库、表格、审批、通知、设备、搜索，无“管理”主菜单。
  - 左侧顶部头像菜单包含个人项，底部展示“管理后台”。
  - 点击“管理后台”进入 `/admin/overview`，使用 `AdminConsoleShell`，左侧菜单为企业概览、组织架构、成员管理、用户组、角色权限、权限治理、审计日志。
  - `/admin/overview` 刷新后仍保持后台 Shell；`/admin/users` 使用后台 Shell；“返回工作台”回到 `/`。
  - 390px 窄屏下后台侧栏隐藏，顶部菜单按钮可打开后台抽屉菜单。
  - 浏览器控制台仅出现既有 Ant Design `useForm` warning，未发现本轮阻塞错误。

## 遗留 Gap

- 企业概览当前是前端占位页，尚未接入 `/api/admin/overview` 聚合数据；后续 M4 可补组织健康、权限风险、审计摘要和治理待办。
- 管理页面内部仍保留各自页面顶部的管理子导航按钮；本轮先完成 Shell 级拆分，页面级后台 IA 收口属于 UI-SPLIT-M4/M5。
- 后台搜索当前先复用审计日志查询语境；独立后台治理搜索 API 属于 UI-SPLIT-M6/M11。

## 下一步

进入 UI-SPLIT-M3：收口用户工作台信息架构，清理用户侧页面中的后台治理感、后台术语和默认治理面板。
