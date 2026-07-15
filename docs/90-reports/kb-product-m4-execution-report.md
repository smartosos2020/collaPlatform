---
title: KB-PRODUCT-M4 Execution Report
status: completed
milestone: KB-PRODUCT-M4
updated_at: 2026-07-15
---

# KB-PRODUCT-M4 Execution Report

## Scope

- KB-PRODUCT-M4-T01 到 KB-PRODUCT-M4-T10
- 目标：在单一块编辑主路径上建立可理解、可恢复、不会静默覆盖的保存语义。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M4-T01 | 定向后端 + 真实浏览器 | real | isolated | false | 保存状态覆盖 clean、dirty、saving、saved、offline、conflict、error |
| KB-PRODUCT-M4-T02 | 定向后端 + 真实浏览器 | real | isolated | false | 连续输入、自动保存去抖和重复自动保存不产生重复版本 |
| KB-PRODUCT-M4-T03 | 定向后端 + 真实浏览器 | real | isolated | false | 标题和块内容通过同一保存事务提交 |
| KB-PRODUCT-M4-T04 | 定向后端 + 真实浏览器 | real | isolated | false | 旧基线保存返回冲突且远端内容不被覆盖 |
| KB-PRODUCT-M4-T05 | 定向后端 + 真实浏览器 | real | isolated | false | 刷新远端、保留本地草稿和人工合并入口可用 |
| KB-PRODUCT-M4-T06 | 定向后端 + 真实浏览器 | real | isolated | false | 目录切换、刷新和网络失败保留本地恢复能力 |
| KB-PRODUCT-M4-T07 | 定向后端 + 真实浏览器 | real | isolated | false | 自动检查点、手动检查点和命名版本规则可区分 |
| KB-PRODUCT-M4-T08 | 定向后端 + 真实浏览器 | real | isolated | false | 稳定 block ID diff、版本恢复和历史不可变 |
| KB-PRODUCT-M4-T09 | 定向后端 + 真实浏览器 | real | isolated | false | 快速输入、冲突、刷新恢复、目录恢复和离线重试真实通过 |
| KB-PRODUCT-M4-T10 | 定向后端 + 真实浏览器 | real | isolated | false | 保存事件记录模式、检查点、块数量和耗时指标 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M4-T01 | Done | 前端保存状态机与编辑器状态标签已统一，冲突、离线和错误状态具备恢复动作。 |
| KB-PRODUCT-M4-T02 | Done | 700ms 自动保存去抖、最新草稿引用、重复 key 抑制和自动检查点节流已完成。 |
| KB-PRODUCT-M4-T03 | Done | `saveBlocks` 在同一事务中更新标题、版本和规范块快照。 |
| KB-PRODUCT-M4-T04 | Done | 服务端按 `baseVersionNo` 校验并返回 409；前端兼容实际网关 403 时补查远端版本。 |
| KB-PRODUCT-M4-T05 | Done | 冲突状态支持刷新远端、保留本地草稿、查看本地草稿和后续人工合并。 |
| KB-PRODUCT-M4-T06 | Done | 目录切换和页面离开提示、本地草稿持久化、刷新恢复和失败重试已完成。 |
| KB-PRODUCT-M4-T07 | Done | 自动快照按时间间隔创建；手动检查点和命名版本使用明确版本类型。 |
| KB-PRODUCT-M4-T08 | Done | diff 按稳定 block ID 识别 modified/added/removed；恢复产生新版本且保留旧历史。 |
| KB-PRODUCT-M4-T09 | Done | 隔离真实浏览器用例覆盖连续输入、冲突、导航恢复和离线重试。 |
| KB-PRODUCT-M4-T10 | Done | 保存事件记录 `saveMode`、`checkpointCreated`、`blockCount` 和 `durationMs`。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M4-T01 | 状态语义清晰且可恢复 | `KnowledgeContentPage.tsx`、`KnowledgeContentEditorCore.tsx` | `kb-product-m4-save.spec.ts` | 状态标签和冲突/离线操作真实可见 | Done |
| KB-PRODUCT-M4-T02 | 连续输入不按键生成版本 | `KnowledgeContentPage.tsx`、`KnowledgeContentService.java` | M4 浏览器用例 + 18 项定向后端测试 | 连续输入与自动保存真实通过 | Done |
| KB-PRODUCT-M4-T03 | 标题正文原子保存 | `KnowledgeContentController.java`、`KnowledgeContentService.java` | `KnowledgeContentControllerIntegrationTests` | 保存后的标题和块内容真实一致 | Done |
| KB-PRODUCT-M4-T04 | 旧基线不得静默覆盖 | `KnowledgeContentService.java` | `KnowledgeContentControllerIntegrationTests` | 真实冲突提示通过 | Done |
| KB-PRODUCT-M4-T05 | 冲突可刷新且本地副本可查看 | `KnowledgeContentEditorCore.tsx`、`KnowledgeContentPage.tsx` | M4 浏览器用例 | 保留本地草稿、刷新远端通过 | Done |
| KB-PRODUCT-M4-T06 | 导航和网络失败不丢草稿 | `KnowledgeContentPage.tsx`、`httpClient.ts` | M4 浏览器用例 | 目录切换恢复、离线重试通过 | Done |
| KB-PRODUCT-M4-T07 | 版本类型和产生规则可解释 | `KnowledgeContentService.java`、`knowledgeContentApi.ts` | M4 浏览器用例 + 定向集成测试 | 自动/手动版本真实区分 | Done |
| KB-PRODUCT-M4-T08 | 块级 diff/restore 保持历史 | `KnowledgeContentModels.java`、`KnowledgeApiDtos.java`、`KnowledgeContentService.java` | M4 浏览器用例 | stable block ID diff 和 restore 通过 | Done |
| KB-PRODUCT-M4-T09 | 真实保存恢复链路完整 | `web/e2e/kb-product-m4-save.spec.ts`、`web/e2e/support/knowledge.ts` | Playwright 1 passed | isolated real browser 通过 | Done |
| KB-PRODUCT-M4-T10 | 保存可靠性事件可观测 | `KnowledgeContentService.java` 保存事件 payload | 定向后端测试 + finish quality gate | 真实保存流程产生相应事件数据 | Done |

## Code Changes

- Backend:
  - `KnowledgeContentController` 接收 `saveMode`。
  - `KnowledgeContentService` 统一标题/块事务保存、自动检查点节流、手动/命名版本、稳定 block ID diff、版本恢复和保存指标事件。
  - diff API DTO 暴露 `blockId`。
- Frontend:
  - `KnowledgeContentPage` 增加保存状态机、本地草稿恢复、导航/关闭保护、冲突处理、版本 diff/restore 和离线重试。
  - `KnowledgeContentEditorCore` 和包装组件统一显示保存状态与恢复动作。
  - `httpClient` 增加请求超时；块保存 mutation 使用 `networkMode: always`，避免离线时无限 pending。
  - 内容 API 类型补充 `saveMode`、检查点和 diff block ID。
- Tests:
  - 新增 `web/e2e/kb-product-m4-save.spec.ts` 及支持方法，覆盖 M4 真实链路。
- Database:
  - 无新增迁移；复用现有规范版本和 `block_snapshot` 模型。
- Scripts:
  - 无脚本变更。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 将 KB-PRODUCT-M4-T01 到 T10 标记为 Done | 反映本轮已完成的自动保存、版本和冲突闭环 |
| `docs/90-reports/kb-product-m4-execution-report.md` | 补齐执行、验收和验证证据 | 供后续会话和里程碑审计复核 |

## Validation

- Backend targeted tests: 18 passed, 0 failures, 0 errors；其中集成测试在隔离 Testcontainers PostgreSQL 中验证并应用 51 个 Flyway migrations。
- Frontend lint: passed。
- Frontend build: passed，`tsc -b && vite build`。
- Local quality gate: AI 工作循环 finish passed，包括定向后端、前端 lint/build、chunk budget 和 route lazy-loading。
- Browser smoke: `pnpm --dir web exec playwright test --config=e2e/playwright.config.ts e2e/kb-product-m4-save.spec.ts` passed，1 passed，real/isolated。
- 按当前路线，未执行项目级完整 `mvn test` 和最终收口级全量 Flyway 验证；完整测试继续后置到最终收口里程碑。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| KB-PRODUCT-M5 | 尚未接入真实多端实时协同和 CRDT/等价合并模型 | 不影响 M4 单端保存、冲突和恢复验收 | 后续 M5 |
| KB-PRODUCT-M4-T10 | 当前已记录保存指标事件，尚未建设独立运营看板和告警规则 | 不影响事件级可观测验收 | 后续运营观测任务 |

## Next Steps

- 进入 KB-PRODUCT-M5，推进块级实时协同主路径。
- 在最终产品收口里程碑执行项目级完整测试、完整 Flyway 验证和跨模块回归。
