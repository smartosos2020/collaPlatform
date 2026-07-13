---
title: M47 Execution Report
status: archived
milestone: M47
updated_at: 2026-06-20
---

# M47 Execution Report

## Scope

- M47-T01 到 M47-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M47-T01 | Done | `document_versions` 扩展版本名称、类型、摘要、来源版本和块快照；保存路径写入自动快照，手动检查点保留审计入口。 |
| M47-T02 | Done | 新增命名版本 API 和前端“命名”入口，版本列表展示名称、类型、摘要和来源版本。 |
| M47-T03 | Done | 版本 diff 改为块级 LCS，diff 行携带 block scope/type/index 元数据。 |
| M47-T04 | Done | 恢复版本生成新的 `restore` 版本，`sourceVersionNo` 指向被恢复版本。 |
| M47-T05 | Done | 搜索索引聚合 `document_blocks`，清理 JSON-like 块内容并纳入标题、描述和正文快照。 |
| M47-T06 | Done | 新增 `document_templates` 表和内置模板数据。 |
| M47-T07 | Done | 新增从模板创建 API，前端侧边栏可选择模板、父级目录和标题。 |
| M47-T08 | Done | 新增 Markdown 导入 API 和前端导入弹窗，导入后替换正文与块投影。 |

## Code Changes

- Backend: `DocumentService`、`DocumentController`、`JdbcDocumentRepository`、`JdbcSearchRepository`、`DocumentModels`、`DocumentRepository`。
- Frontend: `DocsPage.tsx`、`docsApi.ts`、`index.css`。
- Database: `V032__extend_document_versions_templates_import.sql`。
- Scripts: no product script changes.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | 锁定 M47-T01 到 M47-T08 的完成证据和 T09/T10 延后边界。 |
| `docs/01-architecture/current-architecture.md` | Updated | 记录版本元数据、模板、导入和搜索索引实现落点。 |
| `docs/90-reports/m47-execution-report.md` | Updated | 汇总本轮实现、验证和剩余风险。 |

## Validation

- Backend tests: `mvn -f server/pom.xml -Dtest=DocumentControllerIntegrationTests test` passed, 8 tests.
- Frontend lint: `pnpm web:lint` passed with existing `useDocumentCollaboration` exhaustive-deps warnings.
- Frontend build: `pnpm web:build` passed.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M47-document-versions-search-templates-import" -GateMode quick` passed. Backend 41 tests, frontend lint/build, chunk budget, lazy-loading, sensitive scan, security audit, Flyway order, generated artifact scan, TODO inventory, documentation contract.
- Work-cycle finish: `pnpm work:finish -- -Goal "M47-document-versions-search-templates-import"` passed. Backend 41 tests, backend package, frontend lint/build, chunk budget, lazy-loading, sensitive scan, security audit, Flyway order, generated artifact scan, TODO inventory, documentation contract.
- Browser smoke: not run in this local-only round; covered by API integration test and build gate.

## Remaining Gaps

- M47-T09/T10 were outside the requested range; Markdown/HTML/PDF export and dedicated E2E remain queued.
- Lint still reports pre-existing collaboration hook dependency warnings; no new lint errors were introduced.

## Next Steps

- Continue M48-T01 到 M50-T08.
