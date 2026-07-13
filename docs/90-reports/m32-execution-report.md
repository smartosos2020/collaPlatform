---
title: M32 Execution Report
status: archived
milestone: M32
updated_at: 2026-06-18
---

# M32 Execution Report

## Scope

- M32-T01 到 M32-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M32-T01 | Done | 复用 `documents.parent_id`，新增 `docType=space/folder/markdown` 使用规则、`sort_order`、`archived_at`。 |
| M32-T02 | Done | 新增 `GET /api/docs/tree` 和 `GET /api/docs/{documentId}/path`。 |
| M32-T03 | Done | 新增移动、排序、归档、恢复后端能力，并拦截循环父子和越权操作。 |
| M32-T04 | Done | `/docs` 左侧从列表升级为团队空间树，编辑区顶部新增路径面包屑。 |
| M32-T05 | Done | 前端提供新建空间/文件夹/文档、上移/下移、移动、归档、恢复入口。 |
| M32-T06 | Done | 新建/移动到父目录时复制父节点文档权限；搜索默认排除已归档文档。 |
| M32-T07 | Done | M31 浏览器冒烟新增文档树断言，覆盖团队空间和 P3 文档分支。 |
| M32-T08 | Done | 已同步路线图、产品范围、技术架构、平台对象文档和本执行报告。 |

## Code Changes

- Backend: 扩展文档领域模型、Repository、Service、Controller，补充树构建、路径查询、权限继承、移动排序、归档恢复。
- Frontend: 重构文档页侧边栏为树形团队空间，补齐路径、创建类型、移动、排序、归档恢复交互。
- Database: 新增 `V025__extend_document_tree_metadata.sql`，为 `documents` 增加 `sort_order`、`archived_at` 和索引。
- Scripts: 更新 M31 Playwright 冒烟断言，使用 M31 数据基线验证文档树入口。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Update | 标记 M32-T01 到 M32-T08 完成，并移除文档团队空间 gap。 |
| `docs/00-product/current-product-scope.md` | Update | 同步当前文档模块已具备团队空间、树、路径、移动排序、归档恢复能力。 |
| `docs/01-architecture/current-architecture.md` | Update | 同步 V025、文档 API、搜索归档过滤和 M32 验证现实。 |
| `docs/01-architecture/platform-object-model.md` | Update | 记录文档对象摘要新增 `docType`、`archived` metadata 和归档状态。 |
| `docs/90-reports/m32-execution-report.md` | Create | 固定闭环执行报告。 |

## Validation

- Backend tests: `mvn -Dtest=DocumentControllerIntegrationTests test` passed.
- Frontend build: `pnpm web:lint` passed; `pnpm web:build` passed.
- Work cycle checkpoint: `pnpm work:checkpoint -- -Goal "M32-document-team-space-tree" -GateMode quick` passed.
- Full finish gate: `pnpm work:finish -- -Goal "M32-document-team-space-tree"` passed.
- Data reset: `pnpm data:reset` passed; database restored to the M31 collaboration simulation baseline.
- Browser smoke: `pnpm smoke:m31` passed.
- Post-report gate: `pnpm verify -- -SkipDocker -SkipFrontend -SkipBackend` passed.

## Remaining Gaps

- 文档仍未实现多人实时协同编辑。
- M32 仅实现目录基础操作，尚未实现拖拽移动、目录级权限解释组件和文档内嵌 Base/事项块。

## Next Steps

- M33 进入文档块增强与嵌入对象，优先复用平台对象摘要和权限态。
