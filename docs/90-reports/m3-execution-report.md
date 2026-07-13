---
title: UI-SPLIT-M3 执行报告
status: archived
milestone: UI-SPLIT-M3
updated_at: 2026-07-05
---

# UI-SPLIT-M3 执行报告

## 本轮范围

UI-SPLIT-M3-T01 到 UI-SPLIT-M3-T08：收口用户工作台信息架构。

目标是让用户侧像真实协作工作台，而不是夹带管理后台能力的混合页面。普通用户和管理员进入用户侧时默认看到同一套协作视图；管理员只在头像菜单底部多一个“管理后台”入口。

## 完成项

| 任务 | 状态 | 实现依据 |
| --- | --- | --- |
| UI-SPLIT-M3-T01 | Done | `UserWorkspaceShell` 左侧主导航固定为工作台、消息、项目、知识库、表格、审批、通知、搜索；登录设备移动到头像菜单。 |
| UI-SPLIT-M3-T02 | Done | `KnowledgeBaseDetailPage` 移除用户默认路径的治理查询、统计条和治理面板；`DocsPage` 移除默认 v1 验收报告展示。 |
| UI-SPLIT-M3-T03 | Done | Base 用户侧保留数据表、视图、记录、筛选、排序、评论、关联对象和协作权限入口；未引入全局权限治理或审计导航。 |
| UI-SPLIT-M3-T04 | Done | 项目用户侧保留事项、看板、列表、成员协作、通知和项目内设置；未引入后台组织/权限治理入口。 |
| UI-SPLIT-M3-T05 | Done | 消息和通知用户侧保持会话、提醒、转知识、转任务、筛选和跳转动作；未引入全局审计或治理过滤。 |
| UI-SPLIT-M3-T06 | Done | 用户侧权限不足、对象不可用和知识库空状态保持行动导向文案；后台化验收和治理术语不再默认出现在知识内容正文。 |
| UI-SPLIT-M3-T07 | Done | 浏览器冒烟覆盖用户侧工作台、知识库、表格、项目和消息路径，确认不出现组织管理、权限治理、审计日志等后台治理导航。 |
| UI-SPLIT-M3-T08 | Done | 已同步产品范围、架构和路线图，冻结用户工作台允许入口与展示禁区。 |

## 代码变更

- `web/src/app/layout/UserWorkspaceShell.tsx`
  - 移除左侧主应用菜单里的“设备”项。
  - 在头像菜单中新增“登录设备”，保留 `/devices` 路由作为个人入口。
- `web/src/modules/knowledgeBases/pages/KnowledgeBaseDetailPage.tsx`
  - 停止用户侧空间页默认请求知识库治理仪表盘。
  - 移除空间折叠区里的统计条、治理面板、治理导出和批量治理表单。
  - 折叠区改为“空间设置”，只保留关注、空间权限、节点权限和协同健康等轻量入口。
- `web/src/modules/docs/pages/DocsPage.tsx`
  - 移除正文页默认的 `GET /api/docs/acceptance/v1` 查询。
  - 移除 v1 验收报告卡片和相关展示辅助函数。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M3-T01 到 T08 标记完成；下一轮切换到 UI-SPLIT-M4。 |
| `docs/00-product/current-product-scope.md` | 更新用户工作台入口、管理后台入口、登录设备归属和知识库默认展示禁区。 |
| `docs/01-architecture/current-architecture.md` | 更新 User/Admin Shell 边界和知识库空间页轻量设置契约。 |
| `docs/90-reports/m3-execution-report.md` | 本报告覆盖旧 M3 报告，避免不同路线混淆。 |

## 验证

- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 React Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器用户侧冒烟：通过。验证 `/`、`/knowledge-bases`、`/bases`、`/projects`、`/im`、`/notifications`、`/search` 的左侧主导航只有工作台、消息、项目、知识库、表格、审批、通知、搜索；无组织架构、成员管理、用户组、角色权限、权限治理、审计日志；头像菜单含“登录设备”和“管理后台”；知识库内容页无默认 v1 验收、治理、访问统计或后台管理导航。
- `pnpm work:checkpoint -Goal "UI-SPLIT-M3" -GateMode quick`：通过；报告 `.local-reports/quality-gate-20260705-144738.md`。
- `pnpm work:finish -Goal "UI-SPLIT-M3"`：通过；报告 `.local-reports/quality-gate-20260705-144815.md`。

## 后续边界

- M3 只完成用户侧默认信息架构瘦身，不在本轮新增后端 facade。
- 知识库治理 API 和后台治理页迁移进入 UI-SPLIT-M9。
- 组件样式、页面模式和状态模型进一步拆分进入 UI-SPLIT-M5。
