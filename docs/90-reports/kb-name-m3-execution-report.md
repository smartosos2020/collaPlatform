---
title: KB-NAME-M3 Execution Report
status: archived
milestone: KB-NAME-M3
updated_at: 2026-07-11
---

# KB-NAME-M3 Execution Report

## Scope

- KB-NAME-M3-T01 到 KB-NAME-M3-T07

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M3-T01 | Done | `web/src/modules/docs` 已迁至 `web/src/modules/knowledgeBases/content`。 |
| KB-NAME-M3-T02 | Done | `KnowledgeContentPage`、`KnowledgeContentEditor`、`KnowledgeContentEditorCore` 和知识内容协同 hook 命名收口。 |
| KB-NAME-M3-T03 | Done | `knowledgeContentApi` 使用 M2 canonical API 和 item/content DTO。 |
| KB-NAME-M3-T04 | Done | 规范路由、搜索、通知、IM、平台对象和旧 query link 均解析到空间上下文路径。 |
| KB-NAME-M3-T05 | Done | `/docs/:docId` 仅使用兼容定位器。 |
| KB-NAME-M3-T06 | Done | 状态、缓存 key、错误和 a11y 命名收口；兼容协议名有意保留。 |
| KB-NAME-M3-T07 | Done | lint/build/compile/static/browser smoke 通过。 |

## Code Changes

- Backend: 规范化知识内容 webPath/deepLink 生成，搜索和权限通知路径改为 `/knowledge-bases/{spaceId}/items/{itemId}`。
- Frontend: 删除独立 docs 模块；迁移页面、编辑器、API、路由、类型、缓存 key 和旧链接定位器。
- Database:
- Scripts:

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `current-product-scope.md` | Update | 同步前端 docs 模块删除和兼容定位行为。 |
| `current-architecture.md` | Update | 同步 canonical 页面、API 和路由事实。 |
| `current-roadmap.md` | Update | M3 完成并切换下一入口到 M4。 |

## Validation

- Backend tests: `mvn -DskipTests compile` 通过；按阶段策略未运行完整历史/集成测试。
- Frontend build: `pnpm build` 通过。
- Frontend lint: `pnpm lint` 通过，保留 3 条既有 hook dependency warning、0 error。
- Static contract: 无 `web/src/modules/docs`、无旧 docs import，canonical 页面/API 不含 `/api/docs`。
- Browser smoke: 树打开、编辑器可写、评论/版本/权限入口、旧链接定位、搜索、通知和 IM 深链通过。

## Remaining Gaps

- `document` objectType、`document.*` WebSocket 协议和后端 `Document*` 类型按 M5/M6/M4 计划继续保留。
- 完整历史测试、集成测试和 Flyway 空库验证按约定后置到 KB-NAME-M11。

## Next Steps

- 按 AI 工作循环推进 KB-NAME-M4-T01 到 KB-NAME-M4-T08。
