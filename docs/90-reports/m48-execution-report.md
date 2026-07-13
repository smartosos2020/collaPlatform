---
title: M48 Execution Report
status: archived
milestone: M48
updated_at: 2026-06-20
---

# M48 Execution Report

## Scope

- M48-T01 到 M48-T10

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M48-T01 | Done | `POST /api/conversations/{conversationId}/messages/{messageId}/convert-to-document` 可从 IM 消息生成文档，并自动写入 `message` 关系。 |
| M48-T02 | Done | `POST /api/docs/{documentId}/issues/from-selection` 可从文档选区创建项目事项，并建立文档/事项双向关系。 |
| M48-T03 | Done | 文档内 Base view 预览展示视图名、权限态、过滤和排序数量。 |
| M48-T04 | Done | Base 记录详情的关系区使用平台对象卡展示反向引用文档。 |
| M48-T05 | Done | 项目事项详情从关联文档描述中提取并展示文档片段。 |
| M48-T06 | Done | 文档对象关系允许 `approval` 类型，嵌入卡走统一平台对象权限摘要。 |
| M48-T07 | Done | 文档文件卡保留上传、预览、下载入口，并支持在文档上下文中替换文件。 |
| M48-T08 | Done | 文档对象关系和编辑器对象插入扩展到消息、审批、文档等平台对象卡。 |
| M48-T09 | Done | 全局搜索索引聚合文档块与评论文本；块命中返回 `/docs/{id}#doc-block-{blockId}`，评论命中返回 `/docs/{id}?commentId={threadId}`。 |
| M48-T10 | Done | `crossModuleMessageDocumentIssueAndReverseReferenceFlow` 覆盖消息 -> 文档 -> 任务 -> Base -> 搜索闭环，并断言块/评论 deep link。 |

## Code Changes

- Backend: 新增 `DocumentCrossModuleService`，补充消息转文档、文档选区转事项、关系反向同步和跨模块集成测试；搜索仓储扩展块/评论召回与 deep link。
- Frontend: IM 消息菜单新增转文档；Docs 选区评论可转事项；Base/Project/Doc 文件卡展示跨模块上下文。
- Database: 本轮复用既有关系表，无新增迁移。
- Scripts: AI 工作循环移除 T08 数字上限，保留单轮不跨里程碑约束；质量门禁兼容 `unbounded-within-one-milestone` 策略。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 更新 M48 执行锁定 | 记录 M48-T01 到 M48-T10 的实现状态。 |
| `docs/01-architecture/current-architecture.md` | 补充跨模块文档工作台说明 | 说明消息、事项、Base、文件和审批与文档的集成边界。 |
| `docs/90-reports/m48-execution-report.md` | 补充 T09/T10 并归档 | 留存本轮本地执行证据。 |

## Validation

- Backend compile: `mvn -f server/pom.xml -DskipTests compile` passed.
- Backend tests: `mvn -f server/pom.xml -Dtest=DocumentControllerIntegrationTests test` passed, 12 tests.
- Full backend regression: `mvn test` passed, 45 tests.
- Frontend lint: `pnpm web:lint` passed, only existing `useDocumentCollaboration` exhaustive-deps warnings.
- Frontend build: `pnpm web:build` passed.
- Browser E2E: `pnpm --dir web exec -- playwright test -c e2e/playwright.config.ts e2e/docs-collaboration.spec.ts` passed, 3 tests.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M42-M49-T09-T10-completion" -GateMode quick` passed after quality gate accepted unbounded task count within one milestone.

## Remaining Gaps

- 文件卡权限态目前依赖平台对象摘要和文件下载接口，尚未做专门的文档内无权限视觉回归测试。
- 当前 AI 工作循环仍限制单轮只覆盖一个 milestone；如需跨 M45-M50 一次性推进，需要逐 milestone 启动循环或显式改造该约束。

## Next Steps

- 进入 M49：性能、可靠性、移动端、快捷键、可访问性和迁移能力。
