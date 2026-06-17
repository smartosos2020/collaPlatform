---
title: M27 Execution Report
status: archived
milestone: M27
updated_at: 2026-06-16
---

# M27 Execution Report

## Scope

- M27-T01 to M27-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M27-T01 | Done | 事项创建和详情展示补充截止日期、负责人、优先级、基础信息区；项目页保留需求/任务/BUG 类型差异展示。 |
| M27-T02 | Done | `issue_verification_logs` 增加验证环境、复现步骤、修复版本；BUG 详情抽屉提供对应输入并展示历史记录。 |
| M27-T03 | Done | 看板卡片支持拖拽，投放到目标状态列时调用后端状态流转接口，后端仍按状态机校验。 |
| M27-T04 | Done | 事项列表 API 和项目页支持状态、类型、优先级、负责人筛选，以及最近更新、最近创建、优先级、截止日期排序。 |
| M27-T05 | Done | 新增 `issue_relations` 表和 `/api/issues/{id}/relations`，事项详情可通过内部链接关联平台对象并显示对象卡片。 |
| M27-T06 | Done | 分配、评论、验证、状态变更路径补充参与人通知和跳转对象。 |
| M27-T07 | Done | 项目创建、成员变更、事项创建/更新/流转、验证、关联等关键路径补充审计记录，测试覆盖审计查询。 |
| M27-T08 | Done | 项目模块集成测试、前端 lint/build、内置浏览器冒烟验证通过。 |

## Code Changes

- Backend:
  - `ProjectController`、`ProjectService`、`ProjectRepository` 支持事项筛选排序、扩展 BUG 验证字段、跨对象关联、通知和审计。
  - `ProjectControllerIntegrationTests` 覆盖筛选、状态流转、验证字段、关联对象、通知和审计路径。
- Frontend:
  - `ProjectsPage` 增加筛选条、看板拖拽、截止日期输入、事项基础信息、关联对象区域和 BUG 验证字段。
  - `projectsApi` 增加 `IssueFilters`、扩展 `IssueVerification`、`IssueRelation` 和 `addIssueRelation`。
- Database:
  - `V023__extend_issue_relations_and_verification_fields.sql` 增加验证字段和 `issue_relations` 表。
- Scripts:
  - 本轮未新增脚本；复用 AI 工作循环、质量门禁和浏览器冒烟规则。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | 标记 M27-T01 到 M27-T08 完成，并调整项目模块事实和剩余 Gap。 |
| `docs/01-architecture/current-architecture.md` | Updated | 同步 V023、项目 API、事项关联、BUG 验证字段和 M27 验证结果。 |
| `docs/90-reports/m27-execution-report.md` | Updated | 记录本轮执行证据、验证结果和剩余风险。 |

## Validation

- Backend tests: `mvn -Dtest=ProjectControllerIntegrationTests test` passed.
- Frontend build: `pnpm web:lint` and `pnpm web:build` passed.
- pnpm verify: M27 checkpoint/finish gate passed in this work cycle.
- Browser smoke: in-app browser login passed; `/projects` rendered filters, board, draggable issue card and table; `/issues/64396ea3-37d0-4c4f-996e-9f58a8d5d30d` rendered issue drawer, relation input, BUG verification environment, fix version, reproduction steps and conclusion fields; console warnings/errors were empty.

## Remaining Gaps

- 未实现筛选保存、批量操作和项目报表视图；这些属于 M27 后续体验增强，不阻塞 M27 主路径。
- 审计后端已覆盖关键项目路径，前端审计页面仍归入后续治理。

## Next Steps

- 进入 M28 文档协作体验完善。
