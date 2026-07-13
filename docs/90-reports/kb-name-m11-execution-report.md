---
title: KB-NAME-M11 Execution Report
status: archived
milestone: KB-NAME-M11
updated_at: 2026-07-11
---

# KB-NAME-M11 Execution Report

## Scope

- KB-NAME-M11-T01 到 KB-NAME-M11-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M11-T01 | Done | `colla_platform_kb_m7_rehearsal` 副本升级至 V047；空间/item/block/version/comment 为 5/19/141/23/2，孤儿与无效根引用为 0。 |
| KB-NAME-M11-T02 | Done | 迁移前后 SQL 导出并恢复到 `colla_platform_kb_m11_restore`；计数一致，block checksum 均为 `bd65ce39aefcc7569f205ea63f19e3ba`。 |
| KB-NAME-M11-T03 | Done | 9 个知识与跨模块测试类共 17 个测试通过。 |
| KB-NAME-M11-T04 | Done | 浏览器验证知识树、正文编辑、块、版本、评论、分享、搜索、通知、后台治理和旧路由 404。 |
| KB-NAME-M11-T05 | Done | `route-final` 全部通过：后端 60/60、空库 47 个迁移、package、lint/build、安全、命名和文档门禁通过。 |
| KB-NAME-M11-T06 | Done | tree/content/blocks/performance/search 五次本地采样最大 29/42/12/29/86ms；`pnpm security:audit` 通过。 |
| KB-NAME-M11-T07 | Done | 当前产品范围、架构和平台对象模型已校准到 V047 完成态。 |
| KB-NAME-M11-T08 | Done | 删除门、数据门、浏览器门和完整质量门均通过，知识库唯一命名 v1 冻结。 |

## Code Changes

- Backend: 删除旧 doc/compat 领域和 API，核心统一到 `modules.knowledge`、`KnowledgeBaseItem`、`KnowledgeContent`、`itemId` 与 `knowledge_content`。
- Frontend: 删除旧 docs 模块、旧 locator 和旧路由；知识内容编辑器、跨模块链接与 workspace DTO 只使用规范知识语义。
- Database: V044-V047 完成物理改名、旧正文快照退役、活动引用回填和兼容结构删除。
- Scripts: 增加本地 `knowledge-naming-guard` 并接入质量门；迁移备份和恢复产物仅保存在 `.local-reports`。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `current-product-scope.md` | 更新 | 当前产品入口、API、主模型和删除态与代码一致。 |
| `current-architecture.md` | 更新 | 记录 V044-V047、规范 API、物理模型和 M11 数据证据。 |
| `platform-object-model.md` | 更新 | `knowledge_content` 成为唯一知识内容 objectType。 |
| `current-roadmap.md` | 更新 | 记录 M11 逐项执行状态和最终冻结入口。 |

## Validation

- Backend targeted tests: 17/17 passed；Testcontainers 从空库验证 47 个 Flyway 迁移。
- Route final: `mvn test` 60/60 passed，`mvn -DskipTests package` passed，V001-V047 空库迁移 passed。
- Frontend build: `pnpm web:build` passed。
- Frontend lint: passed with 3 existing React Hook dependency warnings and 0 errors。
- Naming/security: `pnpm kb:naming-guard`、`pnpm security:audit` passed。
- Browser smoke: canonical knowledge route and admin governance passed；old `/docs/{id}` renders unified 404。

## Remaining Gaps

- 编辑器控制台仍有既有 Tiptap empty text node、Ant Design Alert deprecated 属性和未连接 useForm 警告，不阻塞本轮命名冻结。
- `search_index_documents` 属于通用搜索索引表名；编辑器 DOM `document`、Tiptap schema `documentId` 属于技术语义，不是产品兼容入口。

## Freeze Conclusion

- `knowledge_base_spaces`、`KnowledgeBaseItem`、`KnowledgeContent`、`itemId` 和 `knowledge_content` 构成知识库唯一命名 v1。
- 活动代码、路由、API、schema 和新写入不再提供旧文档产品兼容层。
- 旧词只允许存在于不可变 Flyway、归档历史以及 DOM/editor/search document 等通用技术语义。

## Next Steps

- 后续产品增强以规范知识库模型为基础另立路线，不恢复 `/docs`、`/api/docs`、`Document*` 或 `document` 产品 objectType。
