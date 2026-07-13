---
title: UI-SPLIT-M4 执行报告
status: archived
milestone: UI-SPLIT-M4
updated_at: 2026-07-05
---

# UI-SPLIT-M4 执行报告

## 本轮范围

UI-SPLIT-M4-T01 到 UI-SPLIT-M4-T08：管理后台信息架构收口。

目标是让 `/admin/*` 成为可独立解释的管理后台，而不是用户工作台里的几个管理页面集合。后台按组织、权限、安全、审计、应用配置和数据治理组织信息；用户真实协作页面不作为后台页面主体复用。

## 完成项

| 任务 | 状态 | 实现依据 |
| --- | --- | --- |
| UI-SPLIT-M4-T01 | Done | `AdminConsoleShell` 左侧导航改为企业概览、组织与成员、权限与安全、应用配置、内容与数据治理、审计与报表、系统设置分组。 |
| UI-SPLIT-M4-T02 | Done | 企业概览、组织架构、成员管理、用户组、角色权限、权限治理、审计日志都在统一 Admin Shell 下访问；旧页内横向模块切换栏已移除。 |
| UI-SPLIT-M4-T03 | Done | `AdminOverviewPage` 接入现有 admin API，聚合组织健康、成员治理、权限风险、审计摘要、待处理治理事项和最近审计。 |
| UI-SPLIT-M4-T04 | Done | Admin Shell 增加统一面包屑、页面标题、分组说明和顶部操作区；页面内部只承载业务操作、筛选和列表。 |
| UI-SPLIT-M4-T05 | Done | 后台顶部搜索文案固定为后台治理搜索；权限治理和审计日志继续使用组织/权限/审计专用筛选。 |
| UI-SPLIT-M4-T06 | Done | 应用配置、内容与数据治理和系统设置仅作为 disabled 后台治理占位，不跳转到消息、知识库、Base、项目等用户使用页面。 |
| UI-SPLIT-M4-T07 | Done | 现有后台列表页继续复用统一 SaaS 卡片、分页、滚动和启停/危险操作样式；移除重复导航后可用宽度更稳定。 |
| UI-SPLIT-M4-T08 | Done | 浏览器冒烟覆盖企业概览和六个现有后台子页面，确认 Admin Shell 分组导航可访问且不出现用户工作台主应用菜单。 |

## 代码变更

- `web/src/app/layout/AdminConsoleShell.tsx`
  - 后台主导航改为分组菜单。
  - 增加统一面包屑、页面标题和页面说明。
  - 应用配置、内容与数据治理、系统设置先作为 disabled 后台治理占位。
- `web/src/modules/admin/pages/AdminOverviewPage.tsx`
  - 从现有 admin API 聚合概览数据。
  - 展示组织健康、成员治理、权限风险、审计摘要、待处理治理事项和最近审计。
- `web/src/modules/admin/pages/AdminUsersPage.tsx`
- `web/src/modules/admin/pages/AdminDepartmentsPage.tsx`
- `web/src/modules/admin/pages/AdminUserGroupsPage.tsx`
- `web/src/modules/admin/pages/AdminRolesPage.tsx`
- `web/src/modules/admin/pages/AdminPermissionGovernancePage.tsx`
- `web/src/modules/admin/pages/AdminAuditLogsPage.tsx`
  - 移除页内重复的 `AdminModuleNav` 顶部横向切换栏。
- `web/src/modules/admin/components/AdminModuleNav.tsx`
  - 删除旧页内横向后台导航组件。
- `web/src/index.css`
  - 增加后台分组菜单、统一页面条、概览卡片和响应式样式。
  - 删除旧 `admin-module-nav` 样式。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M4-T01 到 T08 标记完成；下一轮切换到 UI-SPLIT-M5。 |
| `docs/00-product/current-product-scope.md` | 更新管理后台分组 IA 和未接入治理占位规则。 |
| `docs/01-architecture/current-architecture.md` | 更新 `AdminConsoleShell` IA 契约和企业概览 facade 过渡策略。 |
| `docs/90-reports/m4-execution-report.md` | 新增本报告。 |

## 验证

- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 React Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器后台冒烟：通过。覆盖 `/admin/overview`、`/admin/departments`、`/admin/users`、`/admin/user-groups`、`/admin/roles`、`/admin/permission-governance`、`/admin/audit-logs`；确认侧边栏分组包含企业概览、组织与成员、权限与安全、应用配置、内容与数据治理、审计与报表、系统设置；配置中心、内容治理、系统设置为 disabled；旧 `AdminModuleNav` 不再出现；后台页面不显示用户工作台主应用菜单。
- `pnpm work:checkpoint -Goal "UI-SPLIT-M4" -GateMode quick`：通过；报告 `.local-reports/quality-gate-20260705-150510.md`。
- `pnpm work:finish -Goal "UI-SPLIT-M4"`：通过；报告 `.local-reports/quality-gate-20260705-150602.md`。

## 后续边界

- M4 不新增后端 overview facade，先用现有 admin API 聚合；专用 `/api/admin/overview` facade 进入后续 API 边界阶段。
- 应用配置、内容与数据治理和系统设置只是后台 IA 占位，不代表对应治理页已经实现。
- 组件、样式和状态模型的进一步拆分进入 UI-SPLIT-M5。
