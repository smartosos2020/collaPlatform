---
title: M28 Execution Report
status: archived
milestone: M28
updated_at: 2026-06-16
---

# M28 Execution Report

## Scope

- M28-T01 to M28-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M28-T01 | Done | 文档侧栏增加标题搜索、全部/最近/收藏模式；打开文档时写入平台最近访问，收藏按钮调用平台收藏 API。 |
| M28-T02 | Done | 文档详情页结构化块支持新增、编辑、删除、上移、下移和保存；保存走 `PATCH /api/docs/{id}/blocks` 并生成新版本。 |
| M28-T03 | Done | 评论支持绑定 `blockId`、定位到对应块、显示未解决/已解决状态，并可调用解决接口。 |
| M28-T04 | Done | 文档评论提及通知跳转增加 `commentId`，被提及人可直达并高亮评论。 |
| M28-T05 | Done | 保留版本 diff、恢复和冲突提示路径，并在文档页与块保存路径中复用冲突处理。 |
| M28-T06 | Done | 权限 UI 保持查看/编辑/管理授权入口，编辑和管理按钮按权限禁用。 |
| M28-T07 | Done | 文档仍注册平台对象链接，IM 分享 `/docs/{id}` 继续展示文档卡片和权限态；文档页关系卡片继续使用内部链接卡片。 |
| M28-T08 | Done | 文档模块集成测试、前端 lint/build、内置浏览器冒烟验证通过。 |

## Code Changes

- Backend:
  - `DocumentComment` 增加 `blockId`、`resolved`、`resolvedAt`、`resolvedBy`、`resolvedByName`。
  - `DocumentController` 增加块级评论入参和 `POST /api/docs/{documentId}/comments/{commentId}/resolve`。
  - `DocumentService` 校验评论目标块、发送带 `commentId` 的提及通知，并记录评论新增/解决审计。
  - `DocumentControllerIntegrationTests` 覆盖块级评论和解决态。
- Frontend:
  - `DocsPage` 增加文档搜索、最近/收藏模式、收藏切换、块编辑器、块级评论、评论定位和解决入口。
  - `docsApi` 扩展文档评论类型、块级评论请求和解决评论 API。
- Database:
  - `V024__extend_document_comments_for_block_resolution.sql` 增加文档评论 block 绑定和解决字段。
- Scripts:
  - 本轮未新增脚本；复用 AI 工作循环、质量门禁和浏览器冒烟规则。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | 标记 M28-T01 到 M28-T08 完成，并更新文档模块事实和剩余 Gap。 |
| `docs/01-architecture/current-architecture.md` | Updated | 同步 V024、文档 API、文档前端能力和 M28 验证结果。 |
| `docs/90-reports/m28-execution-report.md` | Updated | 记录本轮执行证据、验证结果和剩余风险。 |

## Validation

- Backend tests: `mvn -Dtest=DocumentControllerIntegrationTests test` passed.
- Frontend build: `pnpm web:lint` and `pnpm web:build` passed.
- pnpm verify: M28 checkpoint/finish gate passed in this work cycle.
- Browser smoke: in-app browser login passed; `/docs` rendered search, list modes, favorite buttons, block editors, version panel and comment panel; search for `Block` reduced the list to 13 matching documents; console warnings/errors were empty.

## Remaining Gaps

- 文档仍不是多人实时协同编辑；当前是乐观版本号冲突控制。
- 富文本能力仍保持轻量块模型，未实现完整 Notion/Lark 风格富文本。

## Next Steps

- 进入 M29 Base 多维表格完善。
