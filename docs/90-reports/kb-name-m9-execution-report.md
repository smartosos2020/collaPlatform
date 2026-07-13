---
title: KB-NAME-M9 Execution Report
status: archived
milestone: KB-NAME-M9
updated_at: 2026-07-11
---

# KB-NAME-M9 Execution Report

## Scope

- KB-NAME-M9-T01 到 KB-NAME-M9-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M9-T01 | Done | 旧 API 三组件仅位于 `knowledge.compat`；核心源码旧 API 调用为 0。 |
| KB-NAME-M9-T02 | Done | Micrometer counter 增加 source/version tags，gauge 保存最后命中 epoch；敏感路径不作为 tag。 |
| KB-NAME-M9-T03 | Done | V046 在存量副本迁移 19 个 object link、19 个 recent、19 个 search、19 个 ACL 等引用。 |
| KB-NAME-M9-T04 | Done | 前端和本地 fixture 改用 `knowledge_content`、`/knowledge-content` 和 canonical deep link。 |
| KB-NAME-M9-T05 | Done | `.local-reports/kb-name-m9-compat-observation.md`：内部旧入口 0，存量旧引用 0。 |
| KB-NAME-M9-T06 | Done | 删除判定为 GO，替代入口明确为 canonical space/item API 与 knowledge-content deep link。 |
| KB-NAME-M9-T07 | Done | 开关关闭/恢复单测通过，三类 adapter 使用同一条件属性。 |
| KB-NAME-M9-T08 | Done | 路线图遗漏项已补齐并记录 M10 删除输入、观察证据与恢复边界。 |

## Code Changes

- Backend: 兼容命中可观测并可配置关闭；核心 ACL、订阅、搜索和通知只读写 canonical type；版本恢复直接消费 block snapshot。
- Frontend: canonical locator、deep link、收藏、关注、权限、通知、关系和文件目标统一使用 `knowledge_content`。
- Database: 新增 V046，将活动旧引用和链接迁为 canonical type/path，并收紧订阅约束。
- Scripts: 新增本地兼容观察脚本，输出内部调用和存量引用 Go/No-Go 证据。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | Update | 记录兼容指标、开关、V046 与删除门。 |
| `docs/02-roadmap/current-roadmap.md` | Update | 补齐 M9-T08、标记 M9 完成并切换到 M10。 |

## Validation

- Backend tests: `KnowledgeContentControllerIntegrationTests,KnowledgeSchemaMigrationIntegrationTests,LegacyDocsCompatibilityFilterTests,LegacyCompatibilitySwitchTests`，6/6 通过。
- Frontend build: `pnpm web:build` 通过。
- pnpm verify: 由 M9 工作循环收口门禁执行。
- Browser smoke: 规范 locator 结构不改变知识内容页面 UI，本轮以路由 build 和权限解析集成测试验收。

## Remaining Gaps

- 旧 `/api/docs` 和 `/docs/:docId` 仍在 compat 边缘层，按 GO 结论由 M10 删除。
- 历史审计和不可变 Flyway 中的旧事件/类型不改写，仅由历史读取语义解释。

## Next Steps

- 进入 KB-NAME-M10，删除旧 API、DTO、模块别名、旧类型规则和 compat 开关。
