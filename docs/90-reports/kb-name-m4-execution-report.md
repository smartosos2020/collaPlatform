---
title: KB-NAME-M4 Execution Report
status: archived
milestone: KB-NAME-M4
updated_at: 2026-07-11
---

# KB-NAME-M4 Execution Report

## Scope

- KB-NAME-M4-T01 到 KB-NAME-M4-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M4-T01 | Done | `modules.knowledge` 成为后端知识领域唯一代码根，六类边界已建立。 |
| KB-NAME-M4-T02 | Done | space/item/content 三个规范控制器职责分离，兼容控制器迁入 `compat.api`。 |
| KB-NAME-M4-T03 | Done | 领域模型拆为 `KnowledgeBaseItemModels` 与 `KnowledgeContentModels`。 |
| KB-NAME-M4-T04 | Done | item、content、collaboration、version、comment、import/export 应用入口已建立。 |
| KB-NAME-M4-T05 | Done | item/content 仓储端口与 `JdbcKnowledgeSchemaAdapter` 已落地。 |
| KB-NAME-M4-T06 | Done | 核心 `Document*` 类型清零，旧类仅在 compat；跨模块调用已改用知识内容服务命名。 |
| KB-NAME-M4-T07 | Done | 规范契约/集成测试与旧 API 兼容测试分开维护。 |
| KB-NAME-M4-T08 | Done | 目标编译、静态边界、规范 API 和兼容 API 测试通过。 |

## Code Changes

- Backend: 将 `modules.doc` 迁为 `modules.knowledge`；拆分 API、模型、应用服务和仓储端口；增加旧 JSON 响应适配器。
- Frontend: 本里程碑未新增前端改动，沿用 M3 规范路由完成态。
- Database: 无迁移；旧 `documents*` 物理结构由 schema adapter 兼容至 M7。
- Scripts: 无产品脚本改动。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | M4 标记 Done，当前执行点推进到 M5 | 保持新会话执行入口唯一。 |
| `docs/01-architecture/current-architecture.md` | 更新知识模块、前端切换和后端领域边界事实 | 删除 M3 前状态和旧核心类描述。 |

## Validation

- Backend compile: `mvn -DskipTests test-compile` 通过；干净构建编译 179 个主源码、23 个测试源码。
- Canonical contract: `KnowledgeContentApiContractTests` 2/2 通过。
- Canonical integration: `KnowledgeContentControllerIntegrationTests` 3/3 通过。
- Legacy compatibility: `LegacyDocumentApiCompatibilityTests` 22/22 通过。
- Static boundaries: `modules.doc` 源码 0，compat 外 `Document*` 类型 0，核心到 compat import 0，规范控制器 `/api/docs` 路由 0。
- Diff hygiene: `git diff --check` 通过。
- Frontend build: 未执行；M4 无新增前端实现，完整前端和全量测试按 M11 收口规则执行。

## Remaining Gaps

- 数据库物理表/列、审计事件和平台对象 `document` 仍是迁移库存，分别由 M5-M8 处理。
- 内容 workflow 内仍保留部分局部变量和旧物理协议词汇；不构成公开类型，后续随 object/event/schema 迁移收敛。

## Next Steps

- 按 AI 工作循环推进 `KB-NAME-M5-T01` 到 `KB-NAME-M5-T08`。
