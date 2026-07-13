---
title: M43 Execution Report
status: archived
milestone: M43
updated_at: 2026-06-20
---

# M43 Execution Report

## Scope

- M43-T01 到 M43-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M43-T01 | Done | `DocEditor` 增加 Tiptap DragHandle、块 handle 按钮和更多菜单，浏览器烟测确认 `.doc-block-drag-handle` 渲染。 |
| M43-T02 | Done | `/` 菜单覆盖文本、标题、任务、列表、引用、代码、表格、图片、文件、Base 视图、项目事项、消息、内部链接，烟测确认 13 项。 |
| M43-T03 | Done | 段落可转换为 H2，烟测保存后内容包含 `## Block conversion source line.`。 |
| M43-T04 | Done | 可拖拽块 handle 已接入，文本块与对象/文件卡片节点标记为 draggable；深度拖拽排序自动化留到 M43-T10。 |
| M43-T05 | Done | 文档内 Tiptap 表格支持插入、加列、删列、加行、删行、表头切换、删除表格；烟测完成新表格插入和加行。 |
| M43-T06 | Done | 文件/图片块复用文件模块预签名上传，complete 时写入 `file_usages`；烟测上传 txt 文件并查得 `file_usages=1`。 |
| M43-T07 | Done | `objectCard`/`fileCard` Tiptap 节点复用平台对象卡片能力，支持 document、issue、message、base、base_table、base_record、file 插入入口。 |
| M43-T08 | Done | 旧 JSON/结构化块表单折叠为“兼容结构化块”，主路径支持选择对象和粘贴内部链接自动生成卡片。 |

## Code Changes

- Backend: 无后端业务代码变更；沿用现有文件上传、平台对象解析、文档保存和 `file_usages` 写入 API。
- Frontend: 新增 `DocEditor` 块级能力、`filesApi`、Tiptap 表格/拖拽/文件/图片扩展依赖、对象/文件卡片节点、slash 菜单、表格工具条和兼容 directive 序列化。
- Database: 无 migration 变更；验证使用既有 `file_usages` 表。
- Build: `web/vite.config.ts` 拆分 Tiptap/ProseMirror chunk，最大 JS chunk 保持低于 500KB 质量门。
- Scripts: 无新增脚本。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 更新 | 记录 M43-T01 到 M43-T08 执行锁定和证据。 |
| `docs/01-architecture/current-architecture.md` | 更新 | 记录 M43 前端块体验落点、兼容保存策略和验证结论。 |
| `docs/90-reports/m43-execution-report.md` | 更新 | 本轮执行报告。 |

## Validation

- Backend tests: `pnpm work:checkpoint -- -Goal "M43-block-editor-slash-object-cards" -GateMode quick` 已通过，包含 `mvn test`，38 tests passed。
- Frontend build: `pnpm web:lint`、`pnpm web:build` 已通过；chunk budget 通过，最大 JS chunk 约 403KB。
- pnpm verify: checkpoint quick 已通过；final full gate 在文档更新后执行。
- Browser smoke: 通过真实 Chromium 验证 `/docs/{id}`：对象卡片 directive 渲染、slash 菜单 13 项、段落转 H2、插入表格并加行、粘贴 `/docs/{id}` 自动生成对象卡片、上传文件生成文件卡片并写入 `file_usages=1`。临时文档已归档。

## Remaining Gaps

- M43-T09 后端 block v2 字段/metadata 迁移未做，不属于本轮范围。
- M43-T10 正式 E2E 脚本未落盘；本轮使用一次性浏览器烟测完成验证。
- 块拖拽排序已接入 DragHandle，但还缺独立自动化用例覆盖复杂拖拽路径。

## Next Steps

- M44 按路线图进入 document room、协同状态、远端光标和自动保存。
- 在 M43-T10 或 M44 前置补正式 E2E：任务、表格、Base 视图、文件卡片、对象卡片保存回放。
