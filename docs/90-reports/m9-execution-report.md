---
title: UI-SPLIT-M9 执行报告
status: archived
milestone: UI-SPLIT-M9
updated_at: 2026-07-06
---

# UI-SPLIT-M9 执行报告

## 本轮范围

UI-SPLIT-M9-T01 到 UI-SPLIT-M9-T08：知识库治理从用户内容主路径迁移到管理后台/显式设置边界。

目标是让用户侧知识库保持内容空间心智，默认进入目录、正文、评论、版本、分享和对象入口；系统级健康度、权限风险、低访问内容、搜索无结果词和批量治理进入管理后台。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M9-T01 | Done | 用户侧 `/knowledge-bases` 和内容详情保留内容树、正文、评论、版本、权限分享和轻量空间设置，不展示后台治理仪表盘。 |
| UI-SPLIT-M9-T02 | Done | 后台新增知识库治理页，展示空间列表、健康度、风险、低访问内容、无结果搜索词、批量复核、审计和导出治理动作。 |
| UI-SPLIT-M9-T03 | Done | 管理后台导航新增 `/admin/knowledge-bases`，挂到“内容与数据治理 / 知识库治理”。 |
| UI-SPLIT-M9-T04 | Done | owner/manage 在用户侧仍走内容视图和轻量设置；管理员进入用户侧知识库也不会默认看到治理控制台。 |
| UI-SPLIT-M9-T05 | Done | 后端新增 `/api/admin/knowledge-bases` facade 和 `AdminKnowledgeBase*View` DTO；权限治理页改用后台知识库列表。 |
| UI-SPLIT-M9-T06 | Done | 权限风险、维护人缺失、低访问内容、搜索无结果词和治理批处理只在后台页展示。 |
| UI-SPLIT-M9-T07 | Done | 浏览器冒烟覆盖系统管理员后台治理页、普通用户后台 403、用户知识库列表和用户内容详情页的入口差异。 |
| UI-SPLIT-M9-T08 | Done | 同步产品范围、架构边界、当前路线图和本执行报告。 |

## 代码变更

- `server/src/main/java/com/colla/platform/modules/doc/api/AdminKnowledgeBaseController.java`
  - 新增 `/api/admin/knowledge-bases` 后台 facade，覆盖列表、详情、更新、停用、恢复、归档、治理、批量治理和导出。
- `server/src/main/java/com/colla/platform/modules/doc/api/AdminKnowledgeBaseDtos.java`
  - 新增 `AdminKnowledgeBaseSpaceView`、`AdminKnowledgeBaseGovernanceView`、健康度、风险、访问统计和审计范围 DTO。
- `server/src/test/java/com/colla/platform/modules/doc/api/DocumentControllerIntegrationTests.java`
  - 在既有知识库治理集成流中补充后台 `/api/admin/knowledge-bases/{spaceId}/governance` DTO 断言。
- `web/src/modules/admin/api/adminKnowledgeBasesApi.ts`
  - 新增后台知识库治理 API client 和类型。
- `web/src/modules/admin/pages/AdminKnowledgeBasesPage.tsx`
  - 新增后台知识库治理页面：空间列表、健康卡片、风险表格、批量复核、访问与搜索治理。
- `web/src/app/navigation/adminConsoleNav.tsx`
  - 管理后台“内容与数据治理”分组新增“知识库治理”真实入口。
- `web/src/app/router.tsx`
  - 注册 `/admin/knowledge-bases` 懒加载路由。
- `web/src/modules/admin/pages/AdminOverviewPage.tsx`
  - 企业概览新增知识库治理快捷入口。
- `web/src/modules/admin/pages/AdminPermissionGovernancePage.tsx`
  - 知识库筛选改用后台知识库 API。
- `web/src/modules/docs/pages/DocsPage.tsx`
  - 兼容文档入口文案改为“空间设置”，避免把治理设置带回用户主路径。
- `web/src/index.css`
  - 增加后台知识库治理页布局、空间列表、hero、健康卡片和治理网格样式。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/00-product/current-product-scope.md` | 管理后台可用页面增加知识库治理，用户知识库主路径明确不展示治理统计。 |
| `docs/01-architecture/current-architecture.md` | API 边界增加 `/api/admin/knowledge-bases` facade，并把旧治理接口标为兼容。 |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M9-T01 到 T08 标记完成，并把下一轮推进到 UI-SPLIT-M10。 |
| `docs/90-reports/m9-execution-report.md` | 替换为本报告。 |

## 验证

- `mvn -DskipTests compile`：通过。
- `mvn -Dtest="DocumentControllerIntegrationTests#knowledgeGovernanceMetricsBulkAuditAndPermissionRiskFlow" test`：通过。
- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器冒烟：
  - `/admin/knowledge-bases` 在管理后台加载“知识库治理”，显示治理风险、健康度和访问搜索治理区域。
  - 临时普通成员 `ui_m9_user_003628` 登录后没有“管理后台/企业概览”入口，直连 `/admin/knowledge-bases` 跳转 `/error/403`，且不展示治理内容；验证后已停用该临时账号。
  - `/knowledge-bases` 用户侧知识库列表不展示后台治理面板。
  - `/knowledge-bases/:spaceId/items/:docId` 用户侧详情直接显示知识内容树、正文、版本、权限和评论能力，不出现“知识库治理风险”“访问与搜索治理”等后台面板。

## 遗留 Gap

- `/api/knowledge-bases/{spaceId}/governance*` 仍保留兼容入口，后续 M12 做旧入口审计和清理条件判断。
- 本轮普通用户验证为浏览器手工冒烟；M12 全路径验收仍应补自动化脚本，固定覆盖普通用户无后台入口和后台 403。
- 知识库空间成员、维护人批量改派和标签治理的深度编辑仍复用既有服务能力，后续可在后台页继续扩展批量操作表单。

## 下一步

进入 UI-SPLIT-M10：Base、项目、消息、审批的管理能力归位。
