---
title: UI-SPLIT-M10 执行报告
status: archived
milestone: UI-SPLIT-M10
updated_at: 2026-07-06
---

# UI-SPLIT-M10 执行报告

## 本轮范围

UI-SPLIT-M10-T01 到 UI-SPLIT-M10-T08：Base、项目、消息、审批的管理能力归位。

目标是把四类协作模块的后台语义统一迁入应用治理页，让用户侧继续承担真实协作，后台侧只承担配置、策略、统计、审计、风险和权限排查。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M10-T01 | Done | Base 用户侧继续使用 `/bases`；后台应用治理登记空间、数据表、记录统计、权限排查、导入导出策略和模板治理。 |
| UI-SPLIT-M10-T02 | Done | 项目用户侧继续使用 `/projects` 和 `/issues/*`；后台应用治理登记项目成员、权限策略、归档、审计和模板配置。 |
| UI-SPLIT-M10-T03 | Done | 消息用户侧继续使用 `/im` 和 `/notifications`；后台应用治理登记留存策略、敏感词、转知识审计和通知策略。 |
| UI-SPLIT-M10-T04 | Done | 审批用户侧继续使用 `/approvals`；后台应用治理登记流程模板、权限、审计和异常排查。 |
| UI-SPLIT-M10-T05 | Done | 新增 `/admin/app-governance` 页面和 `/api/admin/application-governance` facade，后台不复用用户侧真实页面作为主体。 |
| UI-SPLIT-M10-T06 | Done | 后台治理页提供权限治理和审计日志深链，作为后续后台搜索/治理查询的收口入口。 |
| UI-SPLIT-M10-T07 | Done | 用户侧路由只作为协作入口说明；后台深链只进入后台权限、审计和策略语义；用户对象卡不新增后台入口。 |
| UI-SPLIT-M10-T08 | Done | 目标集成测试、前端 lint/build 和浏览器冒烟覆盖后台应用治理与用户侧入口差异。 |

## 代码变更

- `server/src/main/java/com/colla/platform/modules/admin/application/AdminApplicationGovernanceService.java`
  - 新增后台应用治理聚合服务，直接统计 Base、项目、消息、审批的治理指标，并返回策略、风险、后台深链和边界规则。
- `server/src/main/java/com/colla/platform/modules/admin/api/AdminApplicationGovernanceController.java`
  - 新增 `/api/admin/application-governance` facade，要求后台访问权限。
- `server/src/test/java/com/colla/platform/modules/admin/api/AdminApplicationGovernanceControllerIntegrationTests.java`
  - 覆盖管理员读取四类模块治理数据、普通成员访问 403。
- `web/src/modules/admin/api/applicationGovernanceApi.ts`
  - 新增后台应用治理 API client 和类型。
- `web/src/modules/admin/pages/AdminApplicationGovernancePage.tsx`
  - 新增后台应用治理页，支持 Base、项目、消息、审批分段切换，展示统计、策略、风险、后台深链和边界规则。
- `web/src/app/navigation/adminConsoleNav.tsx`
  - “应用配置”分组新增“应用治理”真实入口。
- `web/src/app/router.tsx`
  - 注册 `/admin/app-governance` 懒加载路由。
- `web/src/modules/admin/pages/AdminOverviewPage.tsx`
  - 常用治理入口新增“应用治理”。
- `web/src/app/navigation/navigationBoundaries.ts`、`web/src/shared/api/apiBoundary.ts`
  - 前端导航边界和 API 边界登记 application governance。
- `web/src/index.css`
  - 新增后台应用治理页面布局样式。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/00-product/current-product-scope.md` | 管理后台可用页面增加应用治理，明确后台不复用用户侧协作页面。 |
| `docs/01-architecture/current-architecture.md` | API 边界增加 `/api/admin/application-governance` facade。 |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M10-T01 到 T08 标记完成，并把下一轮推进到 UI-SPLIT-M11。 |
| `docs/90-reports/m10-execution-report.md` | 新增本报告。 |

## 验证

- `mvn -DskipTests compile`：通过。
- `mvn -Dtest="AdminApplicationGovernanceControllerIntegrationTests" test`：通过。
- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器冒烟：
  - `/admin/app-governance` 在管理后台加载“应用治理”，可切换 Base、项目、消息、审批。
  - 后台页面展示治理策略、风险、权限排查和审计日志深链，不嵌入 `/bases`、`/projects`、`/im`、`/approvals` 用户页面。
  - 普通用户没有后台入口，直连 `/admin/app-governance` 跳转 403。

## 遗留 Gap

- 应用治理目前是治理总览和策略登记，具体策略表单（例如消息留存天数、Base 导入大小限制、项目模板发布、审批流程版本）后续按模块拆分实现。
- 后台搜索治理仍以权限治理和审计日志深链承接，M11 继续收口搜索、审计、通知和平台对象链接的跨边界规则。

## 下一步

进入 UI-SPLIT-M11：权限、审计、搜索和通知跨边界规则收口。
