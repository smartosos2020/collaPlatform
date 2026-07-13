---
title: 知识库与文档兼容命名清理路线图
status: archived
scope: knowledge-content-compatibility-naming-cleanup
last_code_check: 2026-07-10
source_rule: 本路线已完成归档，只作追溯，不作为执行依据。
predecessor: UI-SPLIT-M1 到 UI-SPLIT-M12 已完成并归档。
---

# 知识库与文档兼容命名清理路线图

本文定义 Colla Platform 在“知识库是唯一产品入口、结构化块是正文主事实来源”已经成立之后，清理历史独立文档模块命名和兼容负担的当前执行路线。

本路线不是一次机械重命名。它要完成的是产品语义、前端路由与模块、后端 API 与类型、平台对象、跨模块链接、事件与搜索、数据库表字段和迁移工具的一致切换，并在证据充分后删除旧兼容面。

已完成路线归档：

- 类 Lark 知识库 v1 已归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-v1-roadmap-completed-2026-07-01.md`。
- 知识库唯一入口与旧文档产品入口去冗余已归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-clean-roadmap-completed-2026-07-04.md`。
- 知识库内容优先、对象目录与块编辑器 v2 已归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-ux-roadmap-completed-2026-07-05.md`。
- 用户工作台与管理后台双 UI v1 已归档到 `docs/99-archive/superseded-roadmaps/ui-split-roadmap-completed-2026-07-10.md`。

## 1. 当前判断

当前产品已经没有独立“文档模块”入口，但核心代码和数据仍保留大量旧模型：

1. 前端仍有 `/docs/:docId`、`DocsPage`、`web/src/modules/docs`、`docsApi` 和大量 `Document*` 类型。
2. 后端仍以 `modules.doc`、`DocumentController`、`DocumentService`、`DocumentRepository`、`DocumentModels` 组织知识库目录和正文能力。
3. `/api/docs/*` 仍承载目录项、正文块、评论、版本、分享、权限、导入导出、协同和迁移接口，新调用方容易继续扩展旧语义。
4. 平台对象仍使用 `DOCUMENT`/`document`；搜索、通知、IM 卡片、项目/Base 关系、权限资源和 deep link 都依赖该类型。
5. 审计与领域事件仍大量使用 `document.*`；历史数据与新知识库语义混在一起。
6. 数据库仍以 `documents`、`document_blocks`、`document_versions`、`document_comments`、`document_*` 外键和 `root_document_id/home_document_id` 表达知识库事实。
7. `documents.content` 仍是 blocks 之外的兼容快照和双写负担；版本、导出、搜索和回滚仍有部分兼容依赖。
8. 历史 Flyway 文件、历史审计记录和归档报告不可篡改，因此清理必须区分“当前核心模型”“边缘兼容”“不可变历史”。

下一阶段唯一方向：以 `knowledge_base_spaces` 为知识库空间唯一主模型，以“知识库目录项 + 知识内容块”为当前领域语言，迁移所有内部调用方并删除独立文档模块兼容负担。

## 2. 目标领域语言

| 层次 | 当前旧名 | 目标名 | 说明 |
| --- | --- | --- | --- |
| 产品入口 | 文档、Docs | 知识库 | 用户和管理员只从知识库进入。 |
| 空间 | `KnowledgeBaseSpace` | `KnowledgeBaseSpace` | 保持不变，`knowledge_base_spaces` 是唯一空间主模型。 |
| 目录节点 | `DocumentSummary`、document row | `KnowledgeBaseItem` | 统一表示内容、目录、对象引用和外部链接节点。 |
| 可编辑正文 | `DocumentDetail`、document content | `KnowledgeContent` | 只表示可阅读/编辑的知识内容，不代表独立产品模块。 |
| 正文块 | `DocumentBlock` | `KnowledgeContentBlock` | blocks 是正文主事实来源。 |
| 版本/评论/分享 | `DocumentVersion/Comment/...` | `KnowledgeContentVersion/Comment/...` | 从属知识内容或知识库目录项。 |
| 平台对象 | `DOCUMENT` / `document` | `KNOWLEDGE_CONTENT` / `knowledge_content` | 新写入统一使用目标类型，旧类型只在迁移期读取。 |
| Web 路由 | `/docs/:docId` | `/knowledge-bases/:spaceId/items/:itemId` | 目标路由必须携带知识库上下文。 |
| 用户 API | `/api/docs/*` | `/api/knowledge-bases/{spaceId}/items/*` | 内容操作归入知识库 item 资源。 |
| 后端模块 | `modules.doc` | `modules.knowledge` | 空间、目录、内容协作在同一知识领域模块内。 |
| 数据主表 | `documents` | `knowledge_base_items` | 一行表示一个知识库目录项，不再伪装成独立文档产品。 |

命名边界：

- `item` 用于知识库树中的节点身份；`content` 用于可编辑正文及其块、评论和版本。
- 目录、对象引用和外链是 `KnowledgeBaseItem`，但不是 `KnowledgeContent`。
- 通用技术语义中的 document 可以保留，例如“搜索索引文档”或第三方编辑器 document model；必须能证明它不代表产品中的旧文档模块。
- 历史 Flyway 文件名、归档报告和已经落库的历史审计 action 不改写，只提供当前语义映射。

## 3. 核心清理原则

1. **先建新语义，再迁调用方，最后删旧入口。** 不允许边改名边让新旧核心模型长期双写。
2. **兼容只存在于边缘。** 旧路由、旧对象类型和旧事件读取只能进入明确的 `compat` 适配层，不得渗回领域服务。
3. **新代码零旧命名。** 路线开始后新增知识库能力不得再新增 `/api/docs`、`Document*` 产品类型、`document.*` 事件或 `documents.content` 写入。
4. **数据库迁移不可伪装完成。** 只改 Java/TypeScript 类型而继续保留旧表字段，不视为完成。
5. **blocks 单一事实来源。** 删除兼容内容字段前，导出、搜索、版本、评论定位和回滚必须完全从 blocks/块快照工作。
6. **历史证据不可破坏。** 已执行 Flyway、历史审计和归档报告保持不可变；新代码通过别名读取或展示映射解释历史值。
7. **删除由证据驱动。** 旧 API、deep link 和 objectType 的删除必须有调用方扫描、兼容命中计数、数据迁移报告和回滚演练证据。
8. **不借清理扩大产品范围。** 本路线不新增编辑器大功能、不重做知识库 UI、不拆微服务，只清理边界和兼容负担。

## 4. 完成态

路线完成后必须满足：

1. 用户、前端和公开 API 不再出现独立“文档模块”概念。
2. `web/src/modules/docs`、`DocsPage`、`docsApi` 和产品语义 `Document*` 类型被删除。
3. 后端核心代码归入 `modules.knowledge`，领域模型使用 `KnowledgeBaseItem`/`KnowledgeContent*`。
4. 新用户 API 全部位于 `/api/knowledge-bases/{spaceId}/items/*`；`/api/docs/*` 不再是核心 API。
5. 新平台对象、权限资源、搜索对象、通知目标和对象卡统一使用 `knowledge_content`。
6. 新事件和审计 action 统一使用 `knowledge.*` 或 `knowledge.content.*`；历史 `document.*` 只读兼容。
7. 当前数据库使用 `knowledge_base_items` 和 `knowledge_content_*` 命名，不保留活动的 `documents`/`document_*` 核心表或视图。
8. `content` 兼容快照字段和无用途的双写逻辑被删除；blocks 和块版本快照覆盖全部活动知识内容。
9. 旧浏览器链接若仍需支持，只保留一个无业务逻辑的定位/重定向适配器；旧 API 和旧领域模型不得因该重定向继续存在。
10. 静态门禁能阻止旧命名重新进入核心代码。

## 5. 执行顺序

1. KB-NAME-M1：兼容面审计与目标契约冻结。
2. KB-NAME-M2：知识库 item/content 规范 API 建立。
3. KB-NAME-M3：前端知识内容模块与路由语义迁移。
4. KB-NAME-M4：后端知识领域 API、DTO 与服务语义迁移。
5. KB-NAME-M5：平台对象、权限资源与 deep link 类型迁移。
6. KB-NAME-M6：搜索、通知、IM、审计和事件语义迁移。
7. KB-NAME-M7：数据库知识库 item/content 物理命名迁移。
8. KB-NAME-M8：旧正文快照与双写字段退役。
9. KB-NAME-M9：兼容适配隔离、存量链接迁移与观察窗。
10. KB-NAME-M10：旧 API、旧模块、旧类型和兼容结构删除。
11. KB-NAME-M11：全量迁移演练、回归验证和命名冻结。

排序依据：API 和调用方必须先能使用新语义，数据库才能安全切换；平台对象和跨模块链接必须在旧 objectType 删除前完成迁移；`content` 字段必须在 blocks 覆盖、版本回滚和导出链路全部稳定后退役；最终删除必须晚于兼容观察和存量数据迁移。

## 6. KB-NAME-M1 - 兼容面审计与目标契约冻结

目标：建立完整、可执行的旧命名依赖图和目标映射，冻结“不改什么、何时删除、如何回滚”。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M1-T01 | Done | `knowledge-naming-inventory.ps1` 扫描前端核心/跨模块/测试并输出逐行 JSON；基线为前端核心 426 行、跨模块 250 行、测试 427 行。 |
| KB-NAME-M1-T02 | Done | 库存脚本扫描后端知识核心与跨模块依赖；基线为核心 1,895 行、跨模块 140 行，并覆盖 Controller、DTO、Service、Repository、resolver、权限、事件、搜索和通知。 |
| KB-NAME-M1-T03 | Done | PostgreSQL 只读探针盘点 9 个 document 表、150 个相关列、54 个索引、168 个约束、0 个 trigger；架构文档固定逐表目标名和删除边界。 |
| KB-NAME-M1-T04 | Done | 冻结 `KnowledgeBaseItem`、`KnowledgeContent`、`KnowledgeContentBlock`、`KNOWLEDGE_CONTENT/knowledge_content` 领域词汇和允许保留的通用 technical document 边界。 |
| KB-NAME-M1-T05 | Done | 库存报告与架构文档固定 canonical Web/API/DTO 矩阵，覆盖目录、正文、blocks、评论、版本、分享、权限、导入导出、协同和后台治理。 |
| KB-NAME-M1-T06 | Done | 建立 6 类兼容登记：旧 Web 路由、旧 API、objectType、事件/审计、content 双写和旧 schema，逐项记录 owner、观测、删除门和回滚方式。 |
| KB-NAME-M1-T07 | Done | 数据基线为 5 个空间、19 个 item、7 个 Markdown、31 个 block、100% blocks 覆盖；旧兼容存量和失效入口计数已写入 JSON/架构文档。 |
| KB-NAME-M1-T08 | Done | 新增本地只读库存脚本及 Markdown/JSON 报告，同步产品、架构、平台对象模型、路线图和 `kb-name-m1-execution-report.md`；未改业务行为。 |

验收门：目标词汇和删除条件无歧义；每个旧命名都能归类；数据库破坏性迁移已有回滚策略；后续任务不再依赖猜测。

## 7. KB-NAME-M2 - 知识库 item/content 规范 API 建立

目标：在不立即删除旧 API 的前提下，建立覆盖完整能力的知识库规范 API，使后续调用方不再依赖 `/api/docs/*`。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M2-T01 | Done | `/api/knowledge-bases/{spaceId}/items` 已覆盖列表、树、创建、详情、移动、归档和恢复，路径参数统一为 `itemId`。 |
| KB-NAME-M2-T02 | Done | 已建立正文读取/保存和 blocks 查询、批量保存、插入、更新、重排、删除 API；所有动作执行 `spaceId + itemId` 归属校验。 |
| KB-NAME-M2-T03 | Done | 评论/回复/resolve/reopen、版本/checkpoint/named/diff/restore、分享链接、权限申请和直接授权均有 canonical 路径。 |
| KB-NAME-M2-T04 | Done | 模板、Markdown/HTML 导入导出、关系、选区转事项、路径、协同健康、性能和迁移预览均已迁移；验收报告移至 `/api/admin/knowledge-content/acceptance/v1`。 |
| KB-NAME-M2-T05 | Done | 新增 `KnowledgeBaseItemView`、`KnowledgeContentDetailView`、`KnowledgeContentBlockView` 等规范 DTO；canonical 响应不再暴露 `Document*` 返回类型或 `documentId` 字段。 |
| KB-NAME-M2-T06 | Done | 复用现有权限决策，新增 `getItem/requireItemAccess`；跨空间详情、保存和生命周期探测统一返回不含对象信息的 404。 |
| KB-NAME-M2-T07 | Done | `DocumentController` 标记为待删除兼容入口；`/api/docs/*` 统一返回 `Deprecation`、`Sunset`、successor `Link` 并写入 Micrometer 低基数计数。 |
| KB-NAME-M2-T08 | Done | 新增静态 API 契约测试和目标集成测试，OpenAPI 暴露 canonical schema/path；架构与产品范围已同步当前事实。 |

验收门：规范 API 覆盖当前所有有效编辑/协作能力；新旧接口行为一致；新 API 不调用旧 Controller；普通用户不能跨空间猜测 item。

## 8. KB-NAME-M3 - 前端知识内容模块与路由语义迁移

目标：删除前端的独立 docs 模块心智，所有知识内容页面和 API 都归入 `knowledgeBases` 模块。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M3-T01 | Done | 前端知识内容实现已整体迁入 `web/src/modules/knowledgeBases/content`，编辑器、协同 hook、adapter 和页面边界保持独立。 |
| KB-NAME-M3-T02 | Done | 页面改为 `KnowledgeContentPage`；编辑器壳层为 `KnowledgeContentEditor`，核心为 `KnowledgeContentEditorCore`，协同 hook 同步使用知识内容命名。 |
| KB-NAME-M3-T03 | Done | `knowledgeContentApi` 已全面调用 M2 `spaceId/itemId` API，公开类型统一为 `KnowledgeBaseItem*`/`KnowledgeContent*`，无旧 API 类型别名。 |
| KB-NAME-M3-T04 | Done | 规范路由改为 `/knowledge-bases/:spaceId/items/:itemId`；搜索、通知、IM、平台对象和存量知识库 query 链接统一解析为携带空间的 canonical path。 |
| KB-NAME-M3-T05 | Done | `/docs` 仅重定向知识库，`/docs/:docId` 仅加载 `LegacyKnowledgeContentLocator`，定位后 replace 到规范内容路由。 |
| KB-NAME-M3-T06 | Done | 内容状态、React Query key、错误文本和可访问名称已使用 knowledge content/item 语义；`document.*` 只留在线上协议和平台对象兼容边界。 |
| KB-NAME-M3-T07 | Done | 前端 lint/build、后端目标编译、静态契约检查和浏览器冒烟通过；已覆盖树、编辑器、评论/版本/权限入口、刷新、搜索/通知/IM 深链及旧链接定位。 |

验收门：`web/src/modules/docs` 不再承载业务代码；规范页面不请求 `/api/docs`；页面能力和编辑体验不回退。

## 9. KB-NAME-M4 - 后端知识领域 API、DTO 与服务语义迁移

目标：把后端核心从 `modules.doc`/`Document*` 迁为知识库 item/content 领域，不再让旧名主导代码结构。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M4-T01 | Done | 后端知识实现整体迁至 `modules.knowledge`，建立 space、item、content、collaboration、governance 与单向 compat 边界。 |
| KB-NAME-M4-T02 | Done | 空间、目录项和正文 API 分别由 `KnowledgeBaseSpaceController`、`KnowledgeBaseItemController`、`KnowledgeContentController` 承载；旧控制器仅留在 compat。 |
| KB-NAME-M4-T03 | Done | `DocumentModels` 已拆为 `KnowledgeBaseItemModels`/`KnowledgeContentModels`，规范模型和 DTO 使用 `itemId/itemKind/contentType`。 |
| KB-NAME-M4-T04 | Done | 应用入口拆为 item、content、collaboration、version、comment、import/export 服务；底层 workflow 继续保有事务和审计原子性。 |
| KB-NAME-M4-T05 | Done | 新增 item/content 仓储端口，`JdbcKnowledgeSchemaAdapter` 在 M7 前负责旧 `documents*` 物理表适配。 |
| KB-NAME-M4-T06 | Done | 跨模块 service、协同 room 和 resolver 已使用知识内容命名；`DocumentController` 及旧 JSON 映射只存在于 compat。 |
| KB-NAME-M4-T07 | Done | 规范 API 契约/集成测试与 `LegacyDocumentApiCompatibilityTests` 已分离；历史断言同步为当前 item/content 路由语义。 |
| KB-NAME-M4-T08 | Done | 干净编译、规范契约 2/2、规范 API 3/3、旧 API 兼容 22/22 通过，架构事实已更新。 |

验收门：核心业务调用栈从 Controller 到 Repository 使用新领域语言；`modules.doc` 不再是新增代码落点；兼容包与核心包依赖方向单向。

## 10. KB-NAME-M5 - 平台对象、权限资源与 deep link 类型迁移

目标：将跨模块对象身份从 `document` 切换为 `knowledge_content`，同时不破坏存量链接和 ACL。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M5-T01 | Done | 新增 `PlatformObjectTypes.KNOWLEDGE_CONTENT`，`document/doc` 仅作为有限读取别名，序列化统一返回规范类型。 |
| KB-NAME-M5-T02 | Done | `KnowledgeContentPlatformObjectResolver` 输出规范类型、`contentType` metadata 和空间限定路径。 |
| KB-NAME-M5-T03 | Done | 权限决策与资源权限管理规范化为 `knowledge_content`，旧 ACL 在无规范命中时只读回退。 |
| KB-NAME-M5-T04 | Done | 知识核心新对象链接、关系、收藏、最近访问和订阅目标改用规范类型，前端保留旧值显示别名。 |
| KB-NAME-M5-T05 | Done | 新增 `KnowledgeContentLocator`，统一解析 `itemId -> spaceId -> canonicalPath/deepLink`。 |
| KB-NAME-M5-T06 | Done | 定位器和 resolver 对 forbidden/not-found 返回无标题、无路径安全摘要，跨空间仍由核心 ACL 拦截。 |
| KB-NAME-M5-T07 | Done | 增加对象类型别名契约测试并复用权限、平台对象和规范知识 API 集成测试。 |
| KB-NAME-M5-T08 | Done | 执行报告记录旧值库存、规范写入策略和回滚读取别名；物理存量值迁移归 M9。 |

验收门：所有新对象身份使用 `knowledge_content`；旧 ACL 与对象链接可读；规范路径携带空间上下文；无权限语义不回退。

## 11. KB-NAME-M6 - 搜索、通知、IM、审计和事件语义迁移

目标：清理横切能力中的旧命名，避免核心代码改名后仍通过事件和索引继续传播 `document`。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M6-T01 | Done | 搜索索引新写入和结果对象类型改为 `knowledge_content`，块/评论定位和知识库路径保持。 |
| KB-NAME-M6-T02 | Done | 新通知 targetType、类型与去重键使用知识内容语义；前端 deep-link 解析继续兼容历史通知。 |
| KB-NAME-M6-T03 | Done | 新增 IM `convert-to-knowledge-content` 路径，前端及关系写入使用规范类型，旧路径暂作兼容。 |
| KB-NAME-M6-T04 | Done | 知识领域事件切为 `knowledge.content.*`，搜索消费者对历史 aggregate type 使用只读别名。 |
| KB-NAME-M6-T05 | Done | 新审计 action/targetType 使用知识语义；后台筛选同时保留历史对象中文标签。 |
| KB-NAME-M6-T06 | Done | 协同命令、响应帧与 payload 使用 `knowledge.content.*`/`itemId`，仅输入侧兼容旧命令。 |
| KB-NAME-M6-T07 | Done | 搜索、IM、规范知识 API 共 12 个跨边界目标测试通过。 |

验收门：新事件、索引、通知和关系不再写入旧类型；历史记录仍可解释和跳转；事件重放不会重复副作用。

## 12. KB-NAME-M7 - 数据库知识库 item/content 物理命名迁移

目标：让数据库真实反映知识库唯一模型，清理活动 schema 中的旧文档主模型命名。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M7-T01 | Done | V044 将主表迁为 `knowledge_base_items`，并同步重命名相关约束和索引。 |
| KB-NAME-M7-T02 | Done | 根节点、首页、正文从属和来源引用列已统一迁为 item 语义。 |
| KB-NAME-M7-T03 | Done | blocks、versions、comments、collaboration、template 迁为 `knowledge_content_*`，权限、关系和分享迁为 `knowledge_item_*`。 |
| KB-NAME-M7-T04 | Done | 活动 schema 已使用 `item_kind/content_type`，并增加 item kind 约束及存量值归一化。 |
| KB-NAME-M7-T05 | Done | Repository、治理脚本和测试 SQL 已切换至新物理名，未建立旧名兼容视图。 |
| KB-NAME-M7-T06 | Done | V043 开发库副本升级后 item 19、block 141、ACL 25，旧表与旧列均为 0。 |
| KB-NAME-M7-T07 | Done | Testcontainers 验证 V001→V044 空库路径；独立副本验证 V043→V044 存量路径。 |
| KB-NAME-M7-T08 | Done | 已输出 M7 执行报告和迁移检查报告，历史 V001-V043 保持不可变。 |

验收门：当前 schema 不再以 `documents` 作为主表；外键和索引无孤儿/重复；空库与存量升级均可重复执行；历史 Flyway 文件不改写。

## 13. KB-NAME-M8 - 旧正文快照与双写字段退役

目标：彻底确立 blocks/块快照为正文与版本事实来源，删除旧 content 字段和双写路径。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M8-T01 | Done | 存量副本 9 个活动正文 item 的 block 缺口为 0，23 个版本快照缺口为 0；模板缺口由 V045 一次性补齐。 |
| KB-NAME-M8-T02 | Done | 正文读取、预览、导出、性能、迁移预览和搜索索引均从 active blocks 生成，不再查询 item content。 |
| KB-NAME-M8-T03 | Done | 版本兼容文本由 `block_snapshot` 投影，diff/restore 和新版本写入均以块快照为来源。 |
| KB-NAME-M8-T04 | Done | Repository 已停止 item/version content 与 collaboration snapshot_content 双写，schema 测试阻止旧列回归。 |
| KB-NAME-M8-T05 | Done | 新增离线全库快照/校验/恢复工具，并在隔离库完成 19/141/23 数据恢复演练。 |
| KB-NAME-M8-T06 | Done | V045 迁移模板和协同块快照后删除四个旧 content/snapshot 列并设置 block snapshot 非空约束。 |
| KB-NAME-M8-T07 | Done | 知识内容、版本、模板、导入导出、搜索和 schema 定向测试 5/5 通过，存量 block checksum 不变。 |
| KB-NAME-M8-T08 | Done | 迁移检查脚本和当前架构已明确 blocks 单一事实来源及仅限隔离环境的恢复边界。 |

验收门：线上读取和写入不依赖旧 content；版本可从块快照恢复；数据库不保留活动双写字段；离线恢复证据可用。

## 14. KB-NAME-M9 - 兼容适配隔离、存量链接迁移与观察窗

目标：将剩余兼容压缩到可观测的边缘层，并用真实命中证据决定最终删除。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M9-T01 | Done | `/api/docs` controller/response/filter 均位于 `knowledge.compat`；核心与 Web 规范调用不再访问旧 API。 |
| KB-NAME-M9-T02 | Done | 兼容 filter 记录 surface、method、归一化 source/client version、计数和最后命中 epoch，不记录路径参数。 |
| KB-NAME-M9-T03 | Done | V046 迁移对象链接、最近访问、收藏、通知、ACL、订阅、搜索和跨模块关系到 canonical type/path。 |
| KB-NAME-M9-T04 | Done | Web 收藏、关注、权限、通知、搜索、编辑器上传和本地 M31 fixture 不再主动生成旧类型或链接。 |
| KB-NAME-M9-T05 | Done | 工程观察窗扫描核心旧入口调用 0；存量副本七类旧引用 0，locator 仍经权限解析。 |
| KB-NAME-M9-T06 | Done | 兼容观察报告结论为 GO，替代入口和非预期调用判定规则已记录。 |
| KB-NAME-M9-T07 | Done | `colla.compat.docs-api-enabled` 关闭/恢复测试通过，仅影响边缘三组件，不恢复旧表、字段或领域。 |
| KB-NAME-M9-T08 | Done | 兼容台账、V046 数据证据、恢复边界和 M10 删除输入已在架构与执行报告中收口。 |

验收门：核心代码零旧入口调用；存量链接完成迁移；兼容命中来源可解释；删除门和恢复门都有证据。

## 15. KB-NAME-M10 - 旧 API、旧模块、旧类型和兼容结构删除

目标：在 M9 Go 之后执行实际删除，不把兼容适配永久留在核心工程。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M10-T01 | Done | 已物理删除 `/api/docs/*` Controller、兼容响应/filter、旧请求响应 DTO 和旧客户端；规范契约测试确认旧 API 返回 404。 |
| KB-NAME-M10-T02 | Done | 已删除 `web/src/modules/docs`、旧页面/API/编辑器别名、`docId` 查询参数和旧 locator；前端只保留知识库规范路由。 |
| KB-NAME-M10-T03 | Done | 已删除后端 `modules.doc` 全包、`Document*` 核心类和 schema adapter，知识核心 Repository/Service 方法统一为 item/content 标识符。 |
| KB-NAME-M10-T04 | Done | 已删除 `document` resolver/read alias、权限回退、IM 旧命令和 `document.*` 协同输入分支；新事件、通知和对象关系只接受规范类型。 |
| KB-NAME-M10-T05 | Done | V047 删除旧主体权限表和残余 `document` 活动引用，并为对象、ACL、申请和搜索表增加禁止旧类型约束。 |
| KB-NAME-M10-T06 | Done | 已删除 `/docs`、`/docs/:docId` 与临时 `/knowledge-content/:itemId` locator；无空间上下文的安全回退统一到 `/knowledge-bases`。 |
| KB-NAME-M10-T07 | Done | 已更新规范 API、搜索、后台知识治理 DTO、OpenAPI 契约和目标测试，并删除只验证旧兼容面的测试。 |
| KB-NAME-M10-T08 | Done | 已增加 `knowledge-naming-guard` 并接入质量门禁；扫描限定产品源码，允许 DOM/editor document 等通用技术术语。 |

验收门：旧产品模块代码已物理删除；新功能无法再误用旧 API/类型；保留项仅限不可变历史和经批准的无业务逻辑重定向。

## 16. KB-NAME-M11 - 全量迁移演练、回归验证和命名冻结

目标：对升级、数据、权限、协作和用户路径做最终收口，冻结知识库唯一命名 v1。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-NAME-M11-T01 | Done | V043 存量副本依次应用 V045-V047；5 空间、19 item、141 block、23 版本、2 评论保持，孤儿和旧活动引用均为 0。 |
| KB-NAME-M11-T02 | Done | 已完成迁移前后 SQL 备份、隔离库恢复和 checksum 对照；恢复库计数一致，block checksum 保持不变。 |
| KB-NAME-M11-T03 | Done | 知识内容、Base、用户组、权限、搜索、工作台等跨模块目标集 17/17 通过，规范空间/item ACL 生效。 |
| KB-NAME-M11-T04 | Done | 浏览器完成知识库树、正文、块、版本、评论、分享权限、搜索、通知、后台治理及旧路由 404 冒烟。 |
| KB-NAME-M11-T05 | Done | `route-final` 通过：全量 60/60、空库 V001-V047、package、lint/build、安全、Flyway、文档契约和命名门禁全部通过。 |
| KB-NAME-M11-T06 | Done | 本地 5 次采样 tree/content/blocks/performance/search 最大 29/42/12/29/86ms；安全门禁和跨空间/无权限目标测试通过。 |
| KB-NAME-M11-T07 | Done | 已同步当前产品范围、架构、平台对象模型和执行报告；旧词只允许保留于不可变 Flyway、归档证据和通用技术语义。 |
| KB-NAME-M11-T08 | Done | 数据校验、备份恢复、兼容删除、浏览器主路径和完整门禁均通过，知识库唯一命名 v1 正式冻结。 |

验收门：存量升级和空库安装均通过；核心代码和当前 schema 满足新命名；旧兼容不会被新调用；完整 route-final 通过。

## 17. 全局验收标准

1. `knowledge_base_spaces` 始终是知识库空间唯一主模型，不建立第二套空间或独立文档产品主模型。
2. `KnowledgeBaseItem` 表达目录节点，`KnowledgeContent` 表达可编辑正文，两者不得再次混成含糊的 `Document`。
3. 用户入口、路由、API、前端模块和后端核心代码不得出现独立“文档模块”语义。
4. 目录、对象引用、外部链接不得被误当成正文；内容块、评论和版本只能挂在可编辑内容 item 上。
5. 新写入的平台对象、权限资源、搜索对象、通知目标和事件统一使用知识语义。
6. 历史 Flyway 文件、归档文档和历史审计记录保持不可变；允许通过读取映射解释，但不得继续产生旧值。
7. 旧 API 兼容不能调用独立旧领域实现；兼容只能委托规范用例并具备命中观测和删除期限。
8. 删除旧数据库字段前必须完成 blocks 覆盖、版本恢复、导出、搜索和回滚证据，不允许凭代码扫描直接删除。
9. 数据库迁移必须同时验证空库安装和 V043 存量升级，且外键、索引、ACL、block checksum 和目录树一致。
10. 无权限内容、历史链接和失效对象不得泄露标题、空间、路径、维护人、正文或 object id。
11. 每个里程碑只跑本轮目标测试和必要浏览器冒烟；完整 `mvn test` 与 Flyway 空库全量验证只在 KB-NAME-M11 路线收口执行。
12. 静态命名门禁必须区分产品旧名和通用技术术语，不能误伤搜索索引 document、编辑器 document model 或不可变历史文件。
13. 不新增第二份 active 路线图；`docs/02-roadmap/` 只保留 `current-roadmap.md`。

## 18. 不做范围

- 不重做块编辑器，不在本路线新增大规模编辑体验功能。
- 不把模块化单体拆成微服务或拆成多个仓库。
- 不重写历史 Flyway 迁移文件，不篡改历史审计记录。
- 不因改名改变现有 ACL、分享、评论、版本、搜索和协同业务规则。
- 不删除与产品旧文档模块无关的通用 technical document 概念，除非 M1 证明其会造成实际误读。
- 不在兼容调用方和数据证据不完整时直接执行破坏性删除。

## 19. 下一步入口

KB-NAME-M1 到 KB-NAME-M11 已全部完成，知识库唯一命名 v1 已冻结。后续新路线必须以 `knowledge_base_spaces`、`KnowledgeBaseItem`、`KnowledgeContent`、`itemId`、`knowledge_content` 和规范知识库路由为前提，不得恢复旧文档产品兼容层。

执行前必须完整读取：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/01-architecture/platform-object-model.md`
5. `docs/02-roadmap/current-roadmap.md`
6. `docs/03-engineering/ai-engineering-governance.md`

阶段验证沿用 AI 工作循环：每轮优先执行本轮影响范围内的目标测试、lint/build 和必要浏览器冒烟；完整历史测试、完整集成测试和 Flyway 空库全量验证放在 KB-NAME-M11 收口执行。
