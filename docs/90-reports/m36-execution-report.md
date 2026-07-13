---
title: M36 执行报告
status: archived
milestone: M36
updated_at: 2026-06-18
---

# M36 执行报告

## 本轮范围

- M36-T01 到 M36-T08

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| M36-T01 | 完成 | 通知返回 `sourceType`，路线图和产品文档记录通知来源矩阵边界。 |
| M36-T02 | 完成 | 通知页支持状态/来源/对象筛选、选择当前页、批量已读和全部已读；后端新增 `/api/notifications/read-batch`。 |
| M36-T03 | 完成 | 搜索索引扩展 Base/Base Table；查询增加 `ILIKE` 后备匹配，覆盖中文短语和对象编号。 |
| M36-T04 | 完成 | 搜索结果经平台对象 resolver 复核，带 `accessState` 和 `permissionExplanation`，不可访问对象不返回原文。 |
| M36-T05 | 完成 | 新增 `PermissionExplanation` 模型和 `/api/platform/objects/{type}/{id}/permission-explanation`；对象卡片展示不可访问原因。 |
| M36-T06 | 完成 | 审计查询支持 `targetId`；新增 `/admin/audit-logs` 页面，可按动作、对象、操作者查询。 |
| M36-T07 | 完成 | 集成测试覆盖中文搜索、权限拒绝解释、通知批量已读和审计 targetId 查询；M31 smoke 仍按显式要求运行。 |
| M36-T08 | 完成 | 已同步路线图、产品范围、当前架构、平台对象模型和本报告。 |

## 代码变更

- 后端：
  - `notification`：通知 DTO 增加 `sourceType`，新增批量已读 repository/service/controller。
  - `search`：索引覆盖 `base`、`base_table`，查询增加全文 + `ILIKE` 混合召回，结果增加权限解释。
  - `platform` / `permission`：新增统一权限解释模型和 API。
  - `audit`：审计列表支持按 `targetId` 过滤。
  - 集成测试补充 M36 横向断言。
- 前端：
  - 通知页增加选择当前页和批量已读。
  - 搜索页展示可访问状态和权限解释，并更新搜索范围文案。
  - 平台对象卡片对不可访问对象展示权限解释来源。
  - 新增审计日志 API client 和 `/admin/audit-logs` 页面，成员管理页提供入口。
- 数据库：
  - 未新增迁移；复用现有 `notifications`、`search_index_documents`、`audit_logs` 和平台对象表。
- 脚本：
  - 未新增脚本；复用 AI 工作循环、质量门禁和浏览器冒烟流程。

## 文档变更

| 文档 | 动作 | 原因 |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 更新 | 标记 M36-T01 到 M36-T08 完成，更新已完成事实和当前 Gap。 |
| `docs/00-product/current-product-scope.md` | 更新 | 记录通知批量处理、搜索覆盖范围、权限解释和审计页面。 |
| `docs/01-architecture/current-architecture.md` | 更新 | 记录搜索策略、权限解释 API、审计 targetId 查询和验证结果。 |
| `docs/01-architecture/platform-object-model.md` | 更新 | 补充权限解释模型、搜索结果权限态和 Base/Base Table 搜索覆盖。 |
| `docs/90-reports/m36-execution-report.md` | 新增 | 记录本轮交付、验证和剩余风险。 |

## 验证

- 后端测试：`mvn -q "-Dtest=SearchCollaborationIntegrationTests,WorkspaceControllerIntegrationTests,ProjectControllerIntegrationTests" test` 通过；`pnpm work:checkpoint -- -Goal "M36-horizontal-foundation" -GateMode quick` 内的完整 `mvn test` 通过，38 tests passed。
- 前端构建：`pnpm --dir web lint` 通过；`pnpm --dir web build` 通过。
- `pnpm verify`：通过，含后端测试、前端 lint/build、chunk budget、lazy route、敏感数据、Flyway、生成物和文档结构检查。
- 浏览器冒烟：使用 `admin / admin123456` 登录 `http://127.0.0.1:5173/login`；验证 `/notifications` 显示批量已读和选择当前页；验证 `/search?q=aurora` 显示搜索结果、可访问状态和新范围文案；验证 `/admin/audit-logs` 显示审计表格和筛选输入；控制台无 error。

## 遗留 Gap

- 通知事件矩阵已形成来源和对象筛选基础，但 Base、权限变更和审批细分事件仍需在后续真实流程中继续补齐。
- 搜索仍基于 PostgreSQL 全文检索 + `ILIKE`，尚未引入专门中文分词或外部搜索引擎。
- 权限解释已统一到平台对象层，但字段级、视图级和记录级更细权限要在 M37/M39 继续深化。

## 下一步

- 进入 M37 Base 深度能力与协同嵌入，优先处理字段校验、记录详情、关联对象字段、视图配置和文档内 Base 体验。
