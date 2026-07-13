---
title: M37 Execution Report
status: archived
milestone: M37
updated_at: 2026-06-18
---

# M37 Execution Report

## Scope

- M37-T01 到 M37-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M37-T01 | Completed | `BaseService` 支持 status、url、object_link 字段类型，并对数字、日期、人员、附件、选项和对象链接做保存校验。 |
| M37-T02 | Completed | 新增 `GET /api/base-records/{recordId}/detail`，聚合记录字段、评论、关联对象和最近活动；前端详情面板支持评论与关系入口。 |
| M37-T03 | Completed | 新增 `base_record_relations`，对象链接字段写入 `field_link` 关系，手工关系写入 `manual` 关系，并通过平台对象 resolver 返回摘要和权限态。 |
| M37-T04 | Completed | `base_views.visible_field_ids` 支持字段显隐保存；Base 页面支持字段显示控制、状态字段看板分组、保存/应用视图配置。 |
| M37-T05 | Completed | Base 记录详情、对象链接和手工关系均复用 Base 成员权限与平台对象 resolver，越权对象不返回业务正文。 |
| M37-T06 | Completed | 文档 `base_view` 嵌入块在对象可访问时只读加载目标表和保存视图，展示筛选/排序/字段显隐后的前 5 条记录。 |
| M37-T07 | Completed | 新增 UTF-8 CSV 导出和 CSV 导入接口，导入返回成功行数与失败行列表；Base 页面提供导入/导出入口。 |
| M37-T08 | Completed | 同步产品范围、架构、平台对象模型、路线图和本执行报告。 |

## Code Changes

- Backend:
  - `BaseModels` 增加记录详情、评论、关系、活动、导入结果和视图字段显隐模型。
  - `BaseService` 增加字段校验、对象链接解析、记录详情、评论、关系、CSV 导入导出和活动记录。
  - `BaseController` 增加记录详情、评论、关系、CSV 导入导出接口。
  - `JdbcBaseRepository` 增加视图显隐、评论、关系、反向关系和活动表读写。
- Frontend:
  - `basesApi` 增加新字段类型、记录详情、评论、关系、导入导出和文本 GET 支持。
  - `BasesPage` 增加状态/URL/对象链接字段、字段显隐、记录评论/关系/活动、CSV 导入导出。
  - `DocsPage` 为 `base_view` 嵌入块增加只读 Base 视图预览。
- Database:
  - `V028__extend_base_deep_collaboration.sql` 扩展字段类型约束，新增 `base_views.visible_field_ids`、`base_record_comments`、`base_record_relations`、`base_record_activity_logs`。
- Scripts:
  - 无新增脚本。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/00-product/current-product-scope.md` | Updated | 更新 Base 产品范围、字段类型、记录详情、CSV 和高级能力边界。 |
| `docs/01-architecture/current-architecture.md` | Updated | 更新 Base 模块职责、迁移、API 和验证历史。 |
| `docs/01-architecture/platform-object-model.md` | Updated | 补充 Base 记录关系、对象链接字段和文档 Base 视图预览规则。 |
| `docs/02-roadmap/current-roadmap.md` | Updated | 将 M37-T01 到 M37-T08 标记为完成，并更新当前事实与下一阶段优先级。 |
| `docs/90-reports/m37-execution-report.md` | Updated | 记录 M37 执行闭环。 |

## Validation

- Backend tests: `mvn -q "-Dtest=BaseControllerIntegrationTests" test` passed.
- Frontend lint: `pnpm --dir web lint` passed.
- Frontend build: `pnpm --dir web build` passed.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M37-base-deep-collaboration" -GateMode quick` passed.
- Work-cycle finish: `pnpm work:finish -- -Goal "M37-base-deep-collaboration"` passed.
- Browser smoke: passed after restarting local dev services on current code; verified `/bases/{baseId}/tables/{tableId}/records/{recordId}` renders fields, object link relation, comments and activities, and `/docs/{docId}` renders Base view preview with saved visible fields and records. Browser console error log was empty.

## Remaining Gaps

- Base 仍未实现公式、自动化、字段级权限和记录级权限策略。
- CSV 导入是小规模维护入口，不是大批量异步导入任务。
- 文档内 Base 视图为只读预览，不支持文档内直接编辑记录。

## Next Steps

- 进入 M38 多端体验基础。
