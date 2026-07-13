---
title: KB-NAME-M1 执行报告
status: archived
milestone: KB-NAME-M1
updated_at: 2026-07-10
---

# KB-NAME-M1 执行报告

## 本轮范围

KB-NAME-M1-T01 到 KB-NAME-M1-T08：全量盘点旧文档命名依赖，冻结知识库 item/content 目标词汇、规范 API、数据库映射、兼容登记和删除门。本轮不做大规模业务重命名，不修改数据库和用户行为。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| KB-NAME-M1-T01 | Done | `.local-reports/knowledge-naming-inventory-20260710-221152.json` 全量记录前端核心、跨模块和测试匹配；前端核心 426 行、跨模块 250 行、测试 427 行。 |
| KB-NAME-M1-T02 | Done | 同一库存 JSON 记录后端知识核心 1,895 行和跨模块 140 行，覆盖 `modules.doc`、Controller、DTO、Service、Repository、resolver、权限、事件、审计、搜索、通知和关系。 |
| KB-NAME-M1-T03 | Done | 数据库探针记录 9 个 document 表、150 个相关列、54 个相关索引、168 个相关约束、0 个 trigger；`current-architecture.md` 固定逐表迁移目标。 |
| KB-NAME-M1-T04 | Done | `current-roadmap.md`、产品和架构文档固定 `KnowledgeBaseItem`、`KnowledgeContent`、`KnowledgeContentBlock` 与 `knowledge_content` 边界。 |
| KB-NAME-M1-T05 | Done | 库存报告输出 canonical contract matrix；规范用户 API 固定为 `/api/knowledge-bases/{spaceId}/items/*`，后台治理固定为 `/api/admin/knowledge-bases/*`。 |
| KB-NAME-M1-T06 | Done | 库存报告建立旧 Web、旧 API、objectType、事件/审计、content 双写、旧 schema 六类兼容登记及 owner、观测、删除门、回滚方式。 |
| KB-NAME-M1-T07 | Done | 数据基线：5 个空间、19 个 item、7 个 Markdown、31 个 block、100% blocks 覆盖；旧 content 双写 7、document ACL 19、索引 19、对象链接 19、通知 1、事件 26、审计 38、对象引用 2、旧目标路由 1。孤儿 item/block、legacy HTML、结构缺失对象入口/外链均为 0。 |
| KB-NAME-M1-T08 | Done | 新增本地只读库存脚本与 Markdown/JSON 报告；同步产品、架构、平台对象模型、路线图和本报告。 |

## 代码变更

- 后端：无业务代码变更。
- 前端：无业务代码变更。
- 数据库：只读查询，没有写入、迁移或 reset。
- 脚本：新增本地 `scripts/knowledge-naming-inventory.ps1`，支持 `baseline` 模式，扫描源码并通过 Docker PostgreSQL 生成数据基线；输出 `.local-reports/knowledge-naming-inventory-*.md/.json`。

## 文档变更

| 文档 | 动作 | 原因 |
| --- | --- | --- |
| `docs/00-product/current-product-scope.md` | 增加 KB-NAME-M1 产品术语与当前数据基线 | 防止后续会话重新把文档理解为独立产品模块 |
| `docs/01-architecture/current-architecture.md` | 增加源码库存、API、schema 映射和兼容删除顺序 | 为 M2-M10 提供可执行架构契约 |
| `docs/01-architecture/platform-object-model.md` | 增加 `document -> knowledge_content` 目标类型和只读别名规则 | 固定跨模块对象迁移边界 |
| `docs/02-roadmap/current-roadmap.md` | M1 T01-T08 标记 Done，下一入口切到 M2 | 保持唯一执行入口准确 |
| `docs/90-reports/kb-name-m1-execution-report.md` | 完成本报告 | 记录证据、验证和剩余风险 |

## 验证

- 库存脚本 PowerShell Parser：通过。
- `scripts/knowledge-naming-inventory.ps1 -Mode baseline`：通过；数据库探针可用，最终报告 `.local-reports/knowledge-naming-inventory-20260710-221152.md`，JSON `.local-reports/knowledge-naming-inventory-20260710-221152.json`。
- `pnpm work:checkpoint -- -Goal "KB-NAME-M1" -TaskRange "KB-NAME-M1-T01 到 KB-NAME-M1-T08" -GateMode quick`：通过。
- checkpoint 覆盖后端 `mvn -DskipTests test`、前端 lint/build、chunk budget、懒加载、安全审计、迁移顺序和文档结构；前端 lint 保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning，无新增 error。
- `pnpm work:finish -- -Goal "KB-NAME-M1" -TaskRange "KB-NAME-M1-T01 到 KB-NAME-M1-T08"`：通过；stage profile 完成后端 compile/package、前端 lint/build、安全审计、迁移顺序、文档结构和工作循环契约，质量报告为 `.local-reports/quality-gate-20260710-221507.md`。
- 后端目标集成测试：本轮无业务/API/数据库改动，finish 不指定 `BackendTestPattern`，按阶段规则只编译。
- 浏览器冒烟：未执行；本轮没有 UI、路由或接口行为变更。

## 遗留 Gap

- 源码库存 3,498 个匹配行包含产品旧名、兼容入口、测试、不可变迁移和通用 technical document，M2 起必须按分类逐步下降，不能把总数直接当缺陷数。
- 7 个 Markdown 内容虽然 blocks 覆盖为 100%，仍全部保留旧 content 快照；必须等 M8 的 block-only 版本/导出/搜索/恢复证据完成后删除。
- 旧 objectType 在 ACL、搜索、对象链接、通知、事件和审计中仍有存量；M5/M6 前不能直接删除。
- 历史 Flyway 和历史审计/事件记录不可改写，只能通过读取映射解释。

## 下一步

- 按 AI 工作循环推进 KB-NAME-M2-T01 到 KB-NAME-M2-T08，建立覆盖完整能力的知识库 item/content 规范 API。
