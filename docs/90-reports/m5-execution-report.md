---
title: UI-SPLIT-M5 执行报告
status: archived
milestone: UI-SPLIT-M5
updated_at: 2026-07-05
---

# UI-SPLIT-M5 执行报告

## 本轮范围

UI-SPLIT-M5-T01 到 UI-SPLIT-M5-T08：前端组件和页面职责拆分。

目标是在不改业务接口、数据结构和权限判断的前提下，明确用户工作台、管理后台和共享基础组件的前端边界，减少页面样式、导航配置和视图模型继续混用。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M5-T01 | Done | `web/src/app/navigation/navigationBoundaries.ts` 固定 user/admin/shared 模块归属和禁止职责。 |
| UI-SPLIT-M5-T02 | Done | 新增 `EntityAvatar`、`StatusBadge`、`SoftBadge`、`TableEmptyState`；后台成员、组织、用户组、角色页开始复用 shared 基础 UI。 |
| UI-SPLIT-M5-T03 | Done | `web/src/index.css` 增加 `--user-*` layout tokens 和 `user-workspace-content`、`user-content-canvas`、`user-directory-tree`、`user-reading-surface`、`user-editor-surface`、`user-comment-sidebar`、`user-object-entry`。 |
| UI-SPLIT-M5-T04 | Done | `web/src/index.css` 增加 `--admin-*` layout tokens 和 `admin-list-layout`、`admin-management-list`、`admin-filter-bar`、`admin-governance-panel`、`admin-settings-panel`。 |
| UI-SPLIT-M5-T05 | Done | 扫描用户侧模块未发现 `admin-*` 样式依赖；后台页移除重复的表格头像、状态 badge、标签和空状态实现。 |
| UI-SPLIT-M5-T06 | Done | 导航配置拆为 `userWorkspaceNav.tsx` 与 `adminConsoleNav.tsx`；`UserWorkspaceShell` 和 `AdminConsoleShell` 只负责渲染与搜索/账号交互。 |
| UI-SPLIT-M5-T07 | Done | 新增 `adminViewModels.ts` 与 `userKnowledgeViewModels.ts`，为后续 Admin/User DTO facade 分层提供前端类型落点。 |
| UI-SPLIT-M5-T08 | Done | 前端 lint/build、浏览器样式冒烟、工作循环 checkpoint 和 finish 通过。 |

## 代码变更

- `web/src/shared/components/EntityAvatar.tsx`
  - 新增实体头像和首字母生成工具。
- `web/src/shared/components/StatusBadge.tsx`
  - 新增 active/disabled 状态 badge。
- `web/src/shared/components/SoftBadge.tsx`
  - 新增紫、蓝、灰三类轻量标签。
- `web/src/shared/components/TableEmptyState.tsx`
  - 新增低语义表格空状态。
- `web/src/shared/components/PagePlaceholder.tsx`
  - 接入 `content-card` 基础卡片样式。
- `web/src/app/navigation/userWorkspaceNav.tsx`
  - 抽离用户工作台主导航和移动端主入口。
- `web/src/app/navigation/adminConsoleNav.tsx`
  - 抽离管理后台页面归属、分组菜单和 disabled 治理占位。
- `web/src/app/navigation/navigationBoundaries.ts`
  - 固定 user/admin/shared 前端归属表。
- `web/src/app/layout/UserWorkspaceShell.tsx`
  - 改为消费用户导航配置，内容区挂载用户侧 layout class。
- `web/src/app/layout/AdminConsoleShell.tsx`
  - 改为消费后台导航配置，Shell 不再内嵌页面归属表。
- `web/src/modules/admin/pages/AdminUsersPage.tsx`
  - 表格头像、角色/部门标签、状态和空状态切到 shared 基础组件。
- `web/src/modules/admin/pages/AdminDepartmentsPage.tsx`
  - 成员/负责人头像、关系标签、状态和空状态切到 shared 基础组件。
- `web/src/modules/admin/pages/AdminUserGroupsPage.tsx`
  - 主体/成员头像、类型/来源标签和状态切到 shared 基础组件。
- `web/src/modules/admin/pages/AdminRolesPage.tsx`
  - 分配对象头像、范围标签、角色状态、built-in 标签和空状态切到 shared 基础组件。
- `web/src/modules/admin/types/adminViewModels.ts`
  - 新增后台前端视图模型边界占位。
- `web/src/modules/knowledgeBases/types/userKnowledgeViewModels.ts`
  - 新增用户知识库前端视图模型边界占位。
- `web/src/index.css`
  - 新增 shared UI、user layout token 和 admin layout token；保留旧 `admin-*` 兼容选择器，避免一次性改动大量页面样式。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M5-T01 到 T08 标记完成，下一步指向 UI-SPLIT-M6。 |
| `docs/00-product/current-product-scope.md` | 补充前端组件三层边界和共享组件语义。 |
| `docs/01-architecture/current-architecture.md` | 补充导航配置、shared components、user/admin 样式 token 和 DTO 类型落点。 |
| `docs/90-reports/m5-execution-report.md` | 改写为本轮 UI-SPLIT-M5 执行报告。 |

## 验证

- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器样式冒烟：通过。覆盖 `/admin/overview`、`/admin/users`、`/admin/departments`、`/admin/user-groups`、`/admin/roles` 和 `/knowledge-bases`；确认后台页均在 `AdminConsoleShell` 下，用户侧知识库在 `UserWorkspaceShell` 下，旧 `.admin-module-nav` 无残留，用户侧未发现 `admin-*` 页面样式依赖。
- AI 工作循环 checkpoint：通过，质量门报告 `.local-reports/quality-gate-20260705-190829.md`。
- AI 工作循环 finish：通过，质量门报告 `.local-reports/quality-gate-20260705-190922.md`；按当前优化策略执行后端编译/package，未运行完整 `mvn test`。

## 剩余可复用组件清单

- `PrimaryButton` / `OutlineButton` / `DangerOutlineButton` 仍主要通过 Ant Design class 组合表达，后续可在后台页面进一步抽取。
- `PaginationBar` 仍依赖 Ant Design Table/Pagination 配置，后续可统一页脚布局。
- `PermissionHint` 当前分散在权限弹窗和无权限页，后续可抽为共享权限提示。
- `ObjectCard` 当前由 platform/knowledge/doc 各自实现，后续应统一平台对象卡片基础壳。
- `ErrorState` 当前由 `AppErrorPage` 和模块内 Alert/Empty 组合实现，后续可统一不可见、不可用、已删除三种状态。

## 下一步

进入 UI-SPLIT-M6：API 边界与 DTO 分层设计。重点是盘点现有接口归属，冻结用户协作 API、管理治理 API、内部共享服务和 DTO 命名/迁移矩阵。
