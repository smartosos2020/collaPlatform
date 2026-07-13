---
title: KB-NAME-M2 Execution Report
status: archived
milestone: KB-NAME-M2
updated_at: 2026-07-10
---

# KB-NAME-M2 Execution Report

## Scope

- KB-NAME-M2-T01 到 KB-NAME-M2-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M2-T01 | Done | `KnowledgeBaseSpaceController` canonical item list/tree/create/detail/move/archive/restore；参数统一 `itemId`。 |
| KB-NAME-M2-T02 | Done | `KnowledgeContentController` 正文和 blocks 全操作；`KnowledgeBaseSpaceService.requireItemAccess` 负责空间归属。 |
| KB-NAME-M2-T03 | Done | canonical comments、versions、share-link、permission request/grant 路径与契约测试。 |
| KB-NAME-M2-T04 | Done | canonical templates、import/export、relations、issue selection、path、performance、migration preview、collaboration health；验收端点迁入 admin。 |
| KB-NAME-M2-T05 | Done | `KnowledgeApiDtos` 隔离底层模型，响应字段使用 `itemId/rootItemId/homeItemId/contentType/itemKind`。 |
| KB-NAME-M2-T06 | Done | 跨空间详情、保存、归档探测均返回 404，响应不含标题或目标 ID。 |
| KB-NAME-M2-T07 | Done | `LegacyDocsCompatibilityFilter` 输出弃用/日落/替代链接并记录 `colla.api.compatibility.requests`。 |
| KB-NAME-M2-T08 | Done | `KnowledgeContentApiContractTests` 与 `KnowledgeContentControllerIntegrationTests` 通过；OpenAPI 和架构文档同步。 |

## Code Changes

- Backend: 新增 canonical 知识内容 Controller、规范 DTO、兼容请求 Filter 和后台验收 Controller；空间 Controller 改用规范响应。
- Frontend: 本里程碑不切换前端调用方，留给 KB-NAME-M3。
- Database: 无 schema、数据或 Flyway 文件改动。
- Scripts: 无新增业务脚本。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/00-product/current-product-scope.md` | 更新 | 冻结 canonical API/DTO 与旧 `/api/docs` 兼容定位。 |
| `docs/01-architecture/current-architecture.md` | 更新 | 记录 M2 当前接口、归属校验、兼容观测和 M3 待切换事实。 |
| `docs/02-roadmap/current-roadmap.md` | 更新 | M2-T01 到 T08 标记完成，当前入口推进到 M3。 |

## Validation

- Backend tests: `mvn -q -Dtest=KnowledgeContentApiContractTests test` 通过；`mvn -q -Dtest=KnowledgeContentControllerIntegrationTests test` 通过（3 个目标场景）；既有 `DocumentControllerIntegrationTests` 中受 canonical 响应改名影响的断言已同步，`mvn -q -DskipTests test-compile` 通过。
- Backend compile: `mvn -q -DskipTests compile` 与 `mvn -q -DskipTests test-compile` 通过。
- Frontend build: 本轮无前端改动，留给 M3，不重复执行。
- AI 工作循环 checkpoint: `pnpm work:checkpoint -- -Goal "KB-NAME-M2" -TaskRange "KB-NAME-M2-T01 到 KB-NAME-M2-T08" -GateMode quick` 通过；后端 compile、前端 lint/build、安全门禁、迁移顺序和文档契约均通过。前端仅保留既有 3 个 React Hook warnings。
- AI 工作循环 finish: `pnpm work:finish -- -Goal "KB-NAME-M2" -TaskRange "KB-NAME-M2-T01 到 KB-NAME-M2-T08"` 通过；stage profile 执行后端 compile/package、前端 lint/build、安全门禁、迁移顺序和文档契约，未运行完整历史测试。
- Browser smoke: 本轮只建立后端契约，前端尚未切换 canonical API，不执行浏览器冒烟。

## Remaining Gaps

- 前端 `web/src/modules/docs` 和 `docsApi` 仍调用 `/api/docs`，由 KB-NAME-M3 切换。
- 后端底层仍使用 `modules.doc`、`DocumentService`、`DocumentModels` 和历史数据库物理名，分别由 M4、M7 迁移。
- 平台 objectType、目标路由和历史 deep link 中的 `document` 语义留给 M5/M6/M9，不在 M2 提前混改。

## Next Steps

- 按 AI 工作循环推进 KB-NAME-M3-T01 到 KB-NAME-M3-T07。
