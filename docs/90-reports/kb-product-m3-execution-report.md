---
title: KB-PRODUCT-M3 Execution Report
status: archived
milestone: KB-PRODUCT-M3
updated_at: 2026-07-15
---

# KB-PRODUCT-M3 Execution Report

## Scope

本轮按 AI 工作循环分多轮推进 `KB-PRODUCT-M3-T01` 到 `KB-PRODUCT-M3-T11`。目标是把正式知识内容页收敛为唯一 Tiptap 块编辑器主路径，并把兼容能力退回迁移预览、只读降级、导入导出和后端回滚边界。M3 不提前实现 M4 的自动保存冲突状态机，也不提前实现 M5/M6 的实时协同。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M3-T01 | L1 frontend adapter + L2 browser smoke | real | isolated local services | No | editor JSON load/edit/save round-trip |
| KB-PRODUCT-M3-T02 | L1 adapter contract + L2 browser smoke | real | isolated local services | No | stable IDs survive input, refresh and node switch |
| KB-PRODUCT-M3-T03 | L2 browser state regression | real | isolated local services | No | continuous input, refresh and node switch without focus loss |
| KB-PRODUCT-M3-T04 | L2 network contract + browser smoke | real | isolated local services | No | editor writes `/blocks`, never root content PATCH |
| KB-PRODUCT-M3-T05 | L1 renderer contract + L2 browser smoke | real | isolated local services | No | readable canonical blocks in the formal editor path |
| KB-PRODUCT-M3-T06 | L1 source scan + frontend lint | N/A | isolated source | N/A | no editor mode switch or legacy preference |
| KB-PRODUCT-M3-T07 | L1 source/CSS scan + frontend lint | N/A | isolated source | N/A | compatibility panels absent from the user page |
| KB-PRODUCT-M3-T08 | L1 migration contract + L2 page behavior | real | isolated local services | No | safe preview enters editor; unsafe preview is read-only |
| KB-PRODUCT-M3-T09 | L1 source/dependency scan + build/lint | N/A | isolated source | N/A | no active legacy editor refs or dead legacy UI styles |
| KB-PRODUCT-M3-T10 | L2 real Playwright regression | real | isolated local services | No | continuous input, save, reload, switch-node, back/forward, readonly and no-permission flow |
| KB-PRODUCT-M3-T11 | L1 report/roadmap/rollback review | real | isolated local services | No | finish gate, evidence matrix and compatibility exit result |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M3-T01 | Done | `knowledgeContentAdapter.ts` directly maps canonical block drafts to Tiptap JSON and back; no Markdown conversion in the editor path |
| KB-PRODUCT-M3-T02 | Done | `blockId`/`parentBlockId` are editor node attrs; list/task item flattening uses item identity rather than array position or list container identity |
| KB-PRODUCT-M3-T03 | Done | `KnowledgeContentEditorCore` keeps a stable editor instance and synchronizes by item/version/document key, not on each keystroke |
| KB-PRODUCT-M3-T04 | Done | `KnowledgeContentPage` saves through `saveKnowledgeContentBlocks`; root `saveKnowledgeContent` frontend export was removed; browser test asserts no root PATCH |
| KB-PRODUCT-M3-T05 | Done | `KnowledgeContentEditor` is the single formal renderer for editable canonical blocks, read-only blocks and object-card state |
| KB-PRODUCT-M3-T06 | Done | removed editor mode state, local legacy preference, collaboration editor branch and mode bar |
| KB-PRODUCT-M3-T07 | Done | removed fallback Markdown editor, compatibility block panel, old block editor/table/embed UI and related CSS |
| KB-PRODUCT-M3-T08 | Done | migration preview is queried for legacy markdown items; safe content receives canonical JSON, unsafe/error preview shows read-only warning and cannot save |
| KB-PRODUCT-M3-T09 | Done | removed obsolete collaboration hook, legacy codec implementation, unused root-save frontend API and dead editor styles; active source scan is clean |
| KB-PRODUCT-M3-T10 | Done | real Playwright regression covers continuous input, block save response, stable IDs, reload, node switch, browser history, inherited readonly access and anonymous redirect |
| KB-PRODUCT-M3-T11 | Done | compatibility exit list, rollback window, monitoring metrics and M4 gate recorded below |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M3-T01 | 编辑器不经过 Markdown 往返即可读写 blocks | `blocksToTiptapDocument` / `tiptapDocumentToBlockDrafts` direct JSON adapter | `pnpm web:build`、`pnpm web:lint`；canonical API tests 12/12 | `kb-product-m3-editor.spec.ts` real editor flow | Done |
| KB-PRODUCT-M3-T02 | block ID 不因列表、刷新或重排误配 | `BlockIdentityExtension`、node attrs、item-level list identity | build/lint + adapter implementation review | saved blocks all have IDs; reload and switch pass | Done |
| KB-PRODUCT-M3-T03 | 连续输入不失焦、不被请求刷新覆盖 | stable `useEditor` lifecycle and JSON content key | build/lint | sequential input and reload pass | Done |
| KB-PRODUCT-M3-T04 | 正式编辑只走规范 blocks API | page calls `saveKnowledgeContentBlocks`; root save removed from frontend API | request assertion in Playwright | no root content PATCH; `/blocks` response passed | Done |
| KB-PRODUCT-M3-T05 | read/edit use same block and object representation | one `KnowledgeContentEditor` branch; canonical document fallback only at migration boundary | build/lint | editor renders the saved canonical block content | Done |
| KB-PRODUCT-M3-T06 | 用户无编辑器模式选择 | mode state, localStorage preference and collaboration branch removed | active source scan clean | browser page exposes only one editor path | Done |
| KB-PRODUCT-M3-T07 | 用户页不展示兼容内容面板 | fallback/compatibility components and CSS removed | active source/CSS scan clean | browser flow has no fallback editor panel | Done |
| KB-PRODUCT-M3-T08 | 安全迁移进入编辑，失败只读且不静默覆盖 | migration preview query, canonicalDocument injection and `migrationReadOnly` guard | schema/migration tests 10/10 plus API contract 2/2 | real legacy markdown fixture enters the editor path; unsafe branch is guarded by API state | Done |
| KB-PRODUCT-M3-T09 | 主路径无旧编辑器死代码 | deleted collaboration hook/codec and obsolete styles; removed root-save export | frontend lint/build + `rg` active source scan has no legacy editor refs | N/A: source cleanup | Done |
| KB-PRODUCT-M3-T10 | 单编辑器高频主路径可回归 | `web/e2e/kb-product-m3-editor.spec.ts` | 2 browser specs passed in finish gate | real local browser, isolated fixture, no route/API mock; history navigation, inherited view-only user and unauthenticated redirect passed | Done |
| KB-PRODUCT-M3-T11 | 退出、回滚和 M4 准入边界明确 | compatibility exit matrix below and roadmap M3 status update | AI work-cycle finish passed | finish evidence log and report generated | Done |

## Code Changes

- Backend: no new endpoint or migration; existing canonical migration preview and blocks API are consumed by the formal page. The legacy root PATCH remains on the server as a compatibility/rollback boundary.
- Frontend: direct Tiptap JSON adapter, stable node identity extension, single editor state path, migration read-only guard, removal of Markdown fallback/mode state/collaboration hook, and block-editor regression spec.
- Database: no schema change in M3; V050/V051 from M2 remain the canonical migration/snapshot contract.
- Scripts: no work-cycle script change; checkpoint and finish used the existing scoped validation contract.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | M3 T01-T11 标记 Done，执行入口切换到 M4 | 反映本轮真实收口结果 |
| `docs/90-reports/kb-product-m3-execution-report.md` | 新建并归档 | 保留实现、证据、退出边界和 M4 准入条件 |

## Validation

- Backend tests: `mvn "-Dtest=KnowledgeContentSchemaServiceTests,KnowledgeContentMigrationBatchServiceTests,KnowledgeContentApiContractTests" test`，12 tests，0 failures/errors。
- Frontend build: `pnpm build`，通过；`pnpm lint`，通过。
- Local quality gate: AI 工作循环 checkpoint 通过；finish stage 通过 toolchain、定向后端测试、frontend lint/build、chunk budget 和 route lazy-loading。
- Browser smoke: finish 执行 `pnpm --dir web exec playwright test --config e2e/playwright.config.ts e2e/kb-product-m3-editor.spec.ts e2e/knowledge-content-core.spec.ts`，2/2 通过；真实本地服务和隔离 fixture，无 route/API mock。M3 编辑器用例额外通过前进/后退、知识库空间继承的 `view` 只读用户和未登录重定向验证。
- Diff hygiene: `git diff --check` 通过；工作区其他已有修改未被覆盖。
- Full route-final: 未执行。本轮不是路线最终收口，按工作台规则只跑 M3 影响范围测试；完整 `mvn test`、Flyway 全量和 route-final 留给 M12。

## Compatibility Exit and Rollback Window

### Removed from the formal user path

- 编辑器模式切换、legacy 本地偏好、兼容 Markdown 编辑器和“兼容结构化块”面板。
- 旧的块编辑器、表格/对象兼容编辑组件、Markdown codec、前端 root content save export 和旧协同 snapshot Hook。
- 与上述 UI/状态无引用的编辑器 CSS。

### Retained intentionally

- 后端 root content PATCH、Markdown import/export 和历史版本/回滚能力保留一个 M4 观察窗口，不能作为正式编辑器入口。
- migration preview API 和原始内容字段保留，用于按需迁移、失败只读、导出和诊断。
- blocks API 继续作为 M3 编辑器入口；M4 将把 canonical snapshot、自动保存、冲突和版本语义进一步收口。

### Monitoring and rollback

- 监控指标：root content PATCH 从正式编辑器发出的请求数、blocks save 成功/失败率、迁移 preview unsafe/error 数、canonical block ID 缺失数、编辑器刷新后内容不一致数。
- 回滚窗口：M3 发布后至 M4 canonical snapshot 正式保存完成前。若出现结构化内容回读异常，保留后端兼容写路径和原始字段，停止进一步切流，不删除历史数据。
- 删除条件：M4 完成 canonical snapshot 正式保存、冲突/恢复验证和一轮真实隔离回归后，再单独评审后端 root PATCH 与旧字段的删除任务；M3 不删除数据库历史字段。

## M4 Gate

- 自动保存必须基于 canonical blocks/document，不恢复 Markdown 往返。
- 保存必须等待并处理成功、失败、冲突、重试和页面离开，不以请求发出作为已保存。
- 版本、恢复、评论锚点和搜索投影必须使用同一块身份和 schemaVersion。
- M4 结束前保留本报告的回滚窗口；不能把 M3 的单用户 blocks save 误报为实时协同或发布完成。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| M4 | canonical snapshot 尚未接入正式自动保存和冲突状态机 | 不阻塞 M3；这是 M4 的主目标 | KB-PRODUCT-M4 |
| M5-M6 | 尚未实现 CRDT/等价并发合并、重连、双节点广播 | 不阻塞 M3；M3 只保证单编辑器主路径 | KB-PRODUCT-M5/M6 |
| M7-M12 | 对象入口、导航、评论搜索、性能、无障碍和真实参与者验证仍待后续里程碑 | 不属于 M3 完成条件 | 后续路线 |

## Next Steps

- 当前唯一执行入口切换为 `KB-PRODUCT-M4-T01` 到 `KB-PRODUCT-M4-T10`。
- M4 先建立 canonical 保存状态机和冲突/恢复合同，再决定后端兼容 root PATCH 的删除窗口。
- M5/M6 才推进实时协同、多用户、多节点和离线恢复；M12 才执行完整 route-final 与真实用户发布判定。
