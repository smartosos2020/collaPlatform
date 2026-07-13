---
title: KB-NAME-M7 Execution Report
status: archived
milestone: KB-NAME-M7
updated_at: 2026-07-11
---

# KB-NAME-M7 Execution Report

## Scope

- KB-NAME-M7-T01 到 KB-NAME-M7-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M7-T01 | Done | V044 将 `documents` 迁为 `knowledge_base_items`，动态重命名旧约束和索引。 |
| KB-NAME-M7-T02 | Done | 根、首页、从属和来源外键列迁为 item 语义。 |
| KB-NAME-M7-T03 | Done | 正文从属表使用 `knowledge_content_*`，item 级权限、关系和分享表使用 `knowledge_item_*`。 |
| KB-NAME-M7-T04 | Done | `item_kind/content_type` 成为活动 schema 唯一分类列并受约束保护。 |
| KB-NAME-M7-T05 | Done | Java Repository、测试和本地治理脚本 SQL 已同步迁移；无旧名兼容视图。 |
| KB-NAME-M7-T06 | Done | `colla_platform_kb_m7_rehearsal` 升级前后 item 19、block 141、ACL 25，计数一致。 |
| KB-NAME-M7-T07 | Done | Testcontainers 空库 V001→V044 与开发库副本 V043→V044 两条路径通过。 |
| KB-NAME-M7-T08 | Done | schema 自动断言旧核心表/列为 0；迁移检查结论为 GO-WITH-REVIEW。 |

## Code Changes

- Backend: Repository SQL 与 schema 集成测试改用 item/content 物理命名；`JdbcKnowledgeSchemaAdapter` 收敛为 `JdbcKnowledgeRepository`。
- Frontend: 本轮无业务 UI 改动。
- Database: 新增 V044，迁移 10 张核心表、从属列、约束、索引及 canonical object type rule。
- Scripts: 本地迁移检查、重置、模拟和清单 SQL 同步新物理名；治理脚本不进入远程仓库。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Update | 标记 M7 T01-T08 完成并切换下一入口到 M8。 |
| `.local-reports/kb-migration-check-20260711-160010.md` | Generate | 保存 V043 副本升级后的树、ACL、block 和搜索一致性检查证据。 |

## Validation

- Backend tests: `KnowledgeSchemaMigrationIntegrationTests,KnowledgeContentControllerIntegrationTests`，4/4 通过。
- Frontend build: 由 M7 工作循环收口门禁执行。
- pnpm verify: 由 M7 工作循环收口门禁执行。
- Browser smoke: 本轮为物理 schema 迁移，无 UI 行为变化，不重复执行。

## Remaining Gaps

- 迁移检查发现 2 个知识库根 item 的 metadata 仍有旧影子字段，属于 M8/M9 清理范围，不影响正文、树和 ACL 一致性。
- V001-V043 中的旧命名作为不可变迁移历史保留，不属于活动 schema。

## Next Steps

- 进入 KB-NAME-M8，退役旧正文快照字段和双写路径。
