---
title: M29 Execution Report
status: archived
milestone: M29
updated_at: 2026-06-16
---

# M29 Execution Report

## Scope

- M29-T01 to M29-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M29-T01 | Done | Base 后端已支持 text、number、member、date、attachment、single_select、multi_select；前端日期字段改为原生日期输入，选择字段继续按配置渲染。 |
| M29-T02 | Done | 记录表格保持双击编辑和弹窗保存；记录值按字段类型展示，成员字段显示成员名，多选显示标签。 |
| M29-T03 | Done | URL 带 `recordId` 时右侧面板展示记录详情、字段值、创建/更新信息、打开链接、编辑按钮和评论预留区。 |
| M29-T04 | Done | 筛选/排序状态可保存为视图，右侧面板可应用已保存视图。 |
| M29-T05 | Done | 看板和日历视图保持分组字段/日期字段选择，空状态清楚。 |
| M29-T06 | Done | 右侧面板显示成员权限，授权入口按 manage 权限启用，记录详情编辑按 edit 权限启用。 |
| M29-T07 | Done | Base/base_record 平台对象链接继续支持 IM 分享和搜索打开；记录详情可从 `/bases/{baseId}/tables/{tableId}/records/{recordId}` 打开。 |
| M29-T08 | Done | Base 集成测试、前端 lint/build、内置浏览器冒烟验证通过。 |

## Code Changes

- Backend:
  - 本轮未新增后端能力；复用既有 Base API、平台对象和权限能力。
- Frontend:
  - `basesApi` 增加 `getBaseRecord(recordId)`。
  - `BasesPage` 增加记录详情面板、成员权限展示、记录详情链接和评论预留区。
  - 日期字段输入使用原生 `date` 控件，附件字段提示当前轻量版的 fileId 输入约束。
- Database:
  - 本轮未新增迁移。
- Scripts:
  - 本轮未新增脚本；复用 AI 工作循环、质量门禁和浏览器冒烟规则。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | 标记 M29-T01 到 M29-T08 完成，并更新 Base 当前事实和剩余 Gap。 |
| `docs/01-architecture/current-architecture.md` | Updated | 同步 Base API 能力、记录详情打开路径和 M29 验证结果。 |
| `docs/90-reports/m29-execution-report.md` | Updated | 记录本轮执行证据、验证结果和剩余风险。 |

## Validation

- Backend tests: `mvn -Dtest=BaseControllerIntegrationTests test` passed.
- Frontend build: `pnpm web:lint` and `pnpm web:build` passed.
- pnpm verify: M29 checkpoint/finish gate passed in this work cycle.
- Browser smoke: in-app browser login passed; `/bases` rendered table rows, field panel, saved view button and member permissions; opening a record URL rendered record detail, 7 field rows, edit/open buttons and comment placeholder; console warnings/errors were empty.

## Remaining Gaps

- Base 仍未实现批量编辑、批量删除、字段高级配置和真正的记录评论。
- 移动端当前以响应式可用为目标，还不是正式移动端产品形态。

## Next Steps

- 进入 M30 正式交付与运维收口。
