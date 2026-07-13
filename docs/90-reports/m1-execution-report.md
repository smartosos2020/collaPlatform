---
title: UI-SPLIT-M1 执行报告
status: archived
milestone: UI-SPLIT-M1
updated_at: 2026-07-05
---

# UI-SPLIT-M1 执行报告

## 本轮范围

UI-SPLIT-M1-T01 到 UI-SPLIT-M1-T08：现状盘点与用户工作台 / 管理后台边界契约冻结。

本轮只做文档和边界契约，不改业务代码、接口、数据库或权限逻辑。目标是在进入 UI-SPLIT-M2 之前，先明确哪些入口属于用户工作台，哪些入口属于管理后台，哪些能力是共享底座。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M1-T01 | Done | 前端路由来自 `web/src/app/router.tsx`；当前 `/`、`/im`、`/projects`、`/knowledge-bases`、`/bases`、`/approvals`、`/notifications`、`/devices`、`/search` 和 `/admin/*` 都挂在同一个 `AppLayout` 下。`web/src/app/layout/AppLayout.tsx` 的左侧菜单直接包含“管理”。 |
| UI-SPLIT-M1-T02 | Done | 后端 Controller 盘点覆盖 `/api/admin/*`、`/api/workspace`、`/api/conversations`、`/api/projects`、`/api/knowledge-bases`、`/api/docs`、`/api/bases`、`/api/approvals`、`/api/notifications`、`/api/platform`、`/api/search`、`/api/files` 和 `/api/health`。 |
| UI-SPLIT-M1-T03 | Done | 用户工作台边界写入 `docs/00-product/current-product-scope.md`：工作台、消息、项目、知识库、表格、审批、通知、搜索、设备、个人设置是用户主路径；后台治理项不得进入用户主菜单。 |
| UI-SPLIT-M1-T04 | Done | 管理后台边界写入路线图和产品文档：首版菜单固定为企业概览、组织架构、成员管理、用户组、角色权限、权限治理、审计日志。 |
| UI-SPLIT-M1-T05 | Done | 共享底座边界写入 `docs/01-architecture/current-architecture.md`：认证、权限判定、资源 ACL、平台对象、审计写入、搜索索引、通知投递、文件存储和 WebSocket 可共享。 |
| UI-SPLIT-M1-T06 | Done | DTO 规范写入架构文档：用户侧 `User*View`/`*Summary`，管理侧 `Admin*View`/`Admin*Summary`/`*GovernanceView`，写操作 `*Command`/`*Request`，内部模型 `*Internal`。 |
| UI-SPLIT-M1-T07 | Done | 迁移策略写入路线图：旧 `/admin/*` URL 兼容但迁入 Admin Shell；用户侧“管理”入口迁到头像菜单底部；旧 `/docs/:id`、对象 deep link 和现有 API 先兼容后清理。 |
| UI-SPLIT-M1-T08 | Done | 同步 `docs/02-roadmap/current-roadmap.md`、`docs/00-product/current-product-scope.md`、`docs/01-architecture/current-architecture.md` 和本报告。 |

## 代码变更

- 后端：无。
- 前端：无。
- 数据库：无。
- 脚本：无。

## 文档变更

| 文档 | 动作 | 原因 |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 补充用户确认的“企业概览”和“头像菜单底部管理后台入口”，并将 UI-SPLIT-M1-T01 到 T08 标记 Done。 | 固定 UI-SPLIT-M2 的入口和菜单验收口径，避免后续会话继续把“管理”放在用户主菜单。 |
| `docs/00-product/current-product-scope.md` | 新增“用户工作台与管理后台边界契约”。 | 从产品视角说明用户侧、后台侧、共享底座和禁止混入项。 |
| `docs/01-architecture/current-architecture.md` | 新增双 UI 拆分契约、前端归属表、后端归属表和 DTO 命名约束；补充 `/api/admin/overview` 目标前缀。 | 从架构视角明确当前仍是单体和单 SPA，但后续按 Shell、API、DTO 和权限语义拆边界。 |
| `docs/90-reports/m1-execution-report.md` | 覆盖旧 M1 报告为 UI-SPLIT-M1 报告。 | 当前工作循环要求更新 M1 报告，避免旧报告误导下一轮。 |

## 验证

- 后端测试：未运行。本轮未改后端代码、接口、数据库迁移或权限逻辑。
- `pnpm work:start -Goal "UI-SPLIT-M1" -TaskRange "UI-SPLIT-M1-T01 到 UI-SPLIT-M1-T08"`：通过，生成 `.local-reports/work-cycle-current.json` 和审计快照。
- `pnpm work:checkpoint -Goal "UI-SPLIT-M1" -GateMode quick`：通过。后端 `mvn -DskipTests test` 编译成功；`pnpm web:lint` 通过但保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning；`pnpm web:build` 通过；安全审计、Flyway 顺序、文档结构和工作循环文档契约均 PASS。报告为 `.local-reports/quality-gate-20260705-134700.md`。
- `pnpm work:finish -Goal "UI-SPLIT-M1"`：通过。stage profile 执行后端 `mvn -DskipTests test`、`mvn -DskipTests package`、前端 lint/build、安全审计、Flyway 顺序、文档结构和工作循环文档契约，均 PASS。报告为 `.local-reports/quality-gate-20260705-134745.md`。
- 文档结构检查：已检查 `docs/02-roadmap/` 只保留 `current-roadmap.md`。
- 浏览器冒烟：未执行。本轮未改前端代码、路由运行逻辑或页面样式；UI-SPLIT-M2 开始改 Shell 和导航时必须执行浏览器冒烟。

## 遗留 Gap

- 当前代码仍是单个 `AppLayout`，用户主菜单仍直接包含“管理”；这是 UI-SPLIT-M2 的实现范围。
- 当前管理后台没有企业概览页；`/api/admin/overview` 也只是架构目标前缀，需在后续里程碑实现。
- 当前前端 API type 多为模块内 `Summary`/`Request`，尚未系统性拆成 `User*View` 与 `Admin*View`；这是 UI-SPLIT-M6/M7/M8 的范围。

## 下一步

进入 UI-SPLIT-M2：落地 `UserWorkspaceShell` 和 `AdminConsoleShell`，把用户侧“管理”入口从主菜单移到左侧顶部头像菜单底部，并新增管理后台企业概览与后台首版菜单。
