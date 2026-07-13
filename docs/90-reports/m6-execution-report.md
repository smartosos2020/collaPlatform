---
title: UI-SPLIT-M6 执行报告
status: archived
milestone: UI-SPLIT-M6
updated_at: 2026-07-05
---

# UI-SPLIT-M6 执行报告

## 本轮范围

UI-SPLIT-M6-T01 到 UI-SPLIT-M6-T08：API 边界与 DTO 分层设计。

目标是在不一次性拆服务、不批量改名现有接口的前提下，冻结用户协作 API、管理治理 API、共享平台 API 和兼容编辑底座的边界，给后续 M7/M8 的 facade 与 DTO 迁移提供明确规则。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M6-T01 | Done | `docs/01-architecture/current-architecture.md` 新增 API 边界分类矩阵，覆盖用户协作、管理治理、共享平台、兼容编辑底座。 |
| UI-SPLIT-M6-T02 | Done | 用户协作 API 规范固定为保留模块前缀但输出当前用户视角 DTO，不强制迁到 `/api/workbench/*`。 |
| UI-SPLIT-M6-T03 | Done | 管理治理 API 规范固定为 `/api/admin/*`，后台中知识库/Base/消息/项目/审批只能表示配置、统计、权限、审计或风险治理。 |
| UI-SPLIT-M6-T04 | Done | DTO 分层固定为 `User*View`、`Admin*View`/`Admin*Summary`/`*GovernanceView`、`*Command`/`*Request`、`*Internal` 和兼容 `Document*`。 |
| UI-SPLIT-M6-T05 | Done | 错误语义按用户协作、管理治理、共享平台、兼容编辑底座四类定义。 |
| UI-SPLIT-M6-T06 | Done | 权限约束按用户动作权限、空间/对象管理权限、后台管理权限和超管能力分层。 |
| UI-SPLIT-M6-T07 | Done | API 迁移矩阵输出保留、包 facade、迁移调用方、废弃扩展和清理条件。 |
| UI-SPLIT-M6-T08 | Done | 同步产品范围、架构文档、当前路线图和 `apiBoundaryRules` 前端契约常量。 |

## 代码变更

- `web/src/shared/api/apiBoundary.ts`
  - 新增 `ApiBoundaryKind`、`ApiMigrationAction`、`ApiBoundaryRule`。
  - 固定 `/workspace`、`/conversations`、`/projects`、`/issues`、`/knowledge-bases`、`/docs`、`/bases`、`/admin`、`/platform`、`/resource-permissions`、`/files` 等前缀的归属、DTO 规则、权限规则、错误规则和迁移动作。
  - 输出 user/admin/command/internal DTO 命名规则常量。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/01-architecture/current-architecture.md` | 重写 API 边界段落，补齐 URL 规范、DTO 分层、错误语义、权限约束和迁移矩阵，并修复原 API 表格被段落打断的问题。 |
| `docs/00-product/current-product-scope.md` | 补充用户协作 API、管理治理 API、共享平台 API 和 `/api/docs` 兼容底座的产品口径。 |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M6-T01 到 T08 标记完成，下一步指向 UI-SPLIT-M7。 |
| `docs/90-reports/m6-execution-report.md` | 改写为本轮 UI-SPLIT-M6 执行报告。 |

## 验证

- `pnpm web:lint`：通过；仍保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器冒烟：本轮未改页面运行路径，仅新增 API 边界常量和文档契约；不执行浏览器冒烟。
- AI 工作循环 checkpoint：通过，质量门报告 `.local-reports/quality-gate-20260705-205207.md`。
- AI 工作循环 finish：通过，质量门报告 `.local-reports/quality-gate-20260705-205301.md`；按当前优化策略执行后端编译/package，未运行完整 `mvn test`。

## 剩余风险

- `/api/knowledge-bases/{spaceId}/governance*` 仍在用户协作前缀下，M9 必须迁到后台内容治理或 `/api/admin/knowledge-bases/*` facade。
- `/api/docs` 仍承载大量编辑器能力和旧 deep link，M8/M12 必须继续避免新增产品能力到该前缀。
- `/api/resource-permissions` 同时服务用户分享和后台治理，M11 需要收口用户展示与后台排查的 facade。
- `/api/search` 包含用户搜索和管理索引重建语义，M11 需要拆出后台搜索治理语义。

## 下一步

进入 UI-SPLIT-M7：管理后台 API facade 与权限治理迁移。优先让组织、成员、用户组、角色权限、权限治理和审计日志的 DTO 字段、错误语义和权限守卫符合本轮 M6 契约。
