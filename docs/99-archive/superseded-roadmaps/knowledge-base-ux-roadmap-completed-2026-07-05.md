---
title: 知识库内容体验、对象入口与块编辑器路线图
status: archived
scope: knowledge-base-content-experience
last_code_check: 2026-07-04
source_rule: 本路线图是当前唯一执行路线图；历史路线图只作追溯，不作为执行依据。
remote_rule: 本文件位于 docs/，按 .gitignore 保持本地文档，不进入远程仓库。
---

# 知识库内容体验、对象入口与块编辑器路线图

本文定义 Colla Platform 在“知识库唯一入口 v1”冻结后的下一阶段路线：把知识库从“空间/节点元数据工作台”打磨为内容优先的类 Lark 知识库；让知识库目录可以承载文档以外的协作对象入口；并把文档正文从传统富文本字段升级为结构化块内容系统。

已完成路线归档：

- 类 Lark 知识库 v1 KB-M1 到 KB-M8 已完成，归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-v1-roadmap-completed-2026-07-01.md`。
- 知识库唯一入口与文档模块去冗余 KB-CLEAN-M1 到 KB-CLEAN-M8 已完成，归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-clean-roadmap-completed-2026-07-04.md`。

## 1. 核心判断

当前系统已经完成知识库唯一入口收口，但距离类 Lark 知识库体验仍有三类关键差距：

1. 用户从知识库进入后，正文区域优先看到仪表盘、节点元数据或治理信息，而不是直接看到真实内容。
2. 知识库目录仍主要围绕文档内容节点组织，没有形成“目录节点可以引用任意协作对象”的统一入口能力。
3. 文档编辑仍偏传统富文本框，尚未形成块编辑器、Slash 命令、嵌入块、块级评论和结构化内容存储的完整体验。

下一阶段唯一方向：知识库左侧目录是内容导航；右侧正文默认展示当前内容；元数据、治理、设置和统计作为辅助入口存在。知识库目录不只放文档，也可以放多维表格、文件、项目对象、外部链接和后续协作对象。文档能力继续作为知识库内容能力的一部分，但正文必须逐步升级为结构化 blocks。

不采用的方向：

- 不把多维表格做成知识库的子模块；多维表格是独立协作对象，只是可以被知识库目录引用。
- 不把节点元数据页作为普通用户的默认正文；元数据页只作为概览、设置、治理或空目录 fallback。
- 不一次性替换全部编辑、评论、版本和协同能力；块编辑器必须以兼容迁移和可回滚方式逐步接管。
- 不新增第二套知识库目录树；目录节点模型应在现有知识库树能力上演进。
- 不破坏旧 `/docs/:id` deep link、搜索结果、通知、IM 卡片和项目关联。
- 不为追求 M8/T08 形式压缩真实工程复杂度；里程碑数量按风险和依赖拆分。

## 2. 目标形态

完成后系统应满足：

1. 用户进入知识库空间时，默认打开 `homeDocumentId` 对应的首页内容。
2. 点击文档叶子节点时，右侧正文直接展示文档阅读/编辑区。
3. 点击多维表格节点时，右侧正文直接展示多维表格视图或可交互嵌入视图。
4. 点击非叶子目录时，优先打开该目录的默认首页；没有默认首页时展示子内容列表，而不是节点元数据。
5. 知识库概览、权限、治理、统计、节点元数据进入独立入口、右侧抽屉、设置页或管理 tab。
6. 管理员只比普通用户多看到设置、权限、治理、审计等入口；默认内容阅读路径不因管理员身份改变。
7. 知识库目录节点可以引用内部协作对象，例如 `document`、`base`、`file`、`project`、`external_link`。
8. 多维表格保持独立主模型，知识库只提供目录入口、上下文和权限解释。
9. 文档正文以 `document_blocks` 或等价结构化 blocks 为主，支持段落、标题、待办、列表、代码、引用、表格、文件、对象嵌入等块类型。
10. Slash 命令、块级操作、块级评论、嵌入对象、搜索索引和版本历史都以 block schema 为基础。

## 3. 执行顺序

推荐顺序：

1. KB-UX-M1：知识库内容优先入口与点击行为。
2. KB-UX-M2：目录节点体验、URL 状态和管理入口分离。
3. KB-UX-M3：知识库目录节点模型扩展为通用对象入口。
4. KB-UX-M4：对象引用权限、生命周期和跨模块上下文。
5. KB-UX-M5：多维表格作为知识库目录入口。
6. KB-UX-M6：结构化块内容模型和迁移边界。
7. KB-UX-M7：块编辑器技术选型 spike 和编辑器适配层。
8. KB-UX-M8：基础块编辑器接入与富文本兼容。
9. KB-UX-M9：Slash 命令、嵌入块和块级操作。
10. KB-UX-M10：块级评论、提及通知和搜索定位。
11. KB-UX-M11：版本、导入导出、治理和迁移工具。
12. KB-UX-M12：体验收口、全量验证和 v2 冻结。

排序原因：

- 先让用户进入知识库就看到内容，解决最明显的主路径体验问题。
- 内容优先体验和 URL 状态需要先稳定，否则后续对象节点和块编辑器都会挂在不稳定的壳上。
- 通用目录模型先于多维表格落地，避免把 base 硬编码成文档变体。
- 对象引用权限和生命周期必须先收口，再开放多维表格入口。
- 块编辑器风险最大，先做数据模型，再做技术 spike，再做基础接入，最后做 Slash、嵌入、评论、搜索、版本和导入导出。
- 最后做全量迁移验证和 route-final，确保旧链接、旧内容和跨模块对象不丢失。

## 4. 类 Lark 验收口径

| 口径 | 验收要求 |
| --- | --- |
| 内容优先 | 进入知识库和点击目录节点时，右侧正文优先展示真实内容，不默认展示元数据仪表盘。 |
| 管理分离 | 概览、设置、权限、治理、审计是辅助入口，不阻断普通阅读和编辑路径。 |
| URL 稳定 | 刷新、浏览器前进后退、分享链接和旧 `/docs/:id` deep link 都能恢复正确内容上下文。 |
| 目录泛化 | 知识库目录节点可以表达目录、文档、对象引用和外部链接，不再只是假设文档。 |
| 对象独立 | 多维表格、文件、项目等对象保持独立主模型，知识库目录只引用对象，不复制对象数据。 |
| 权限一致 | 引用对象在知识库内展示时必须同时遵守知识库目录权限和目标对象权限。 |
| 块化正文 | 文档正文以结构化 blocks 为主，富文本字段仅保留兼容和导入导出用途。 |
| 块级协作 | 评论、提及、搜索命中、版本 diff 和对象嵌入能定位到 block。 |
| 历史兼容 | 旧 `/docs/:id`、旧富文本内容、搜索 deep link、通知和对象卡片不失效。 |

## 5. KB-UX-M1 - 知识库内容优先入口与点击行为

目标：把知识库详情页从“节点元数据/仪表盘优先”调整为“内容优先”。

Lark 化结果：用户一进入知识库就看到首页内容；点击目录节点就打开对应内容；治理和元数据不再挡在正文前。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M1-T01 | Done | 已盘点并重构 `KnowledgeBaseDetailPage`：原默认主区包含 hero、搜索、统计、治理、首页卡和发现列表；现拆为内容优先头部、目录内容卡、搜索和折叠管理区。 |
| KB-UX-M1-T02 | Done | `/knowledge-bases/:spaceId` 在树和空间加载完成后默认 `replace` 到 `/knowledge-bases/:spaceId/items/:homeDocumentId`；无首页时保留轻量创建/选择空状态。 |
| KB-UX-M1-T03 | Done | 目录树和发现列表点击 `markdown` 节点统一进入 `DocsPage` 内容路由，不再先停留节点元数据页。 |
| KB-UX-M1-T04 | Done | 点击 `folder/space` 节点停留知识库详情页并展示子内容列表和新建入口；当前模型没有非根目录独立 `defaultHomepageId`，目录首页字段留到 M2/M3 设计。 |
| KB-UX-M1-T05 | Done | 空间统计、节点权限摘要、关注、协同 health 和治理面板移入“概览、治理和管理信息”折叠区，默认不拦截正文路径。 |
| KB-UX-M1-T06 | Done | 跳转逻辑不区分管理员/普通用户；管理员只通过折叠管理区继续访问权限、治理和批量操作。 |
| KB-UX-M1-T07 | Done | 内容优先路由覆盖空间入口、`?docId=` 刷新恢复、目录点击、返回知识库和旧 `/docs/:id` 兼容内容页；浏览器冒烟记录见 M1 报告。 |
| KB-UX-M1-T08 | Done | 已更新产品范围、架构文档和 `docs/90-reports/m1-execution-report.md`，固定“左侧目录是内容导航，右侧默认是当前内容/目录内容”的交互约束。 |

验收门：

- 进入知识库空间默认看到首页内容，而不是仪表盘。
- 点击文档节点一跳到正文内容区。
- 非叶子目录有明确的默认首页或子内容列表 fallback。
- 管理员和普通用户默认路径一致，管理员功能只是附加入口。

## 6. KB-UX-M2 - 目录节点体验、URL 状态和管理入口分离

目标：把内容优先体验做稳定，补齐空状态、URL 状态、刷新恢复、前进后退和管理入口。

Lark 化结果：知识库像一个稳定的内容空间，而不是点击后状态容易丢失的管理面板。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M2-T01 | Done | 固定知识库壳 URL 状态：`docId` 表示当前目录节点，`view=directory/management` 控制目录内容或管理信息，`mode/blockId/commentId` 保留给内容页和后续定位。 |
| KB-UX-M2-T02 | Done | 目录节点、`view=management`、目录展开和详情页滚动位置支持刷新恢复；内容页继续由 `/knowledge-bases/:spaceId/items/:docId` 和旧 `/docs/:id` 恢复。 |
| KB-UX-M2-T03 | Done | 补充无首页、空目录、目标不存在/无权限节点、归档标签等低噪音状态；无效 `docId` 不再悄悄落回首页。 |
| KB-UX-M2-T04 | Done | “概览、治理和管理信息”改为显式 URL 入口 `view=management`，默认正文不落回仪表盘。 |
| KB-UX-M2-T05 | Done | `kb-detail-page` 改为 100% 高度和内部滚动；目录树、右侧内容区各自滚动，浏览器全局滚动冒烟通过。 |
| KB-UX-M2-T06 | Done | 响应式继续保持单列降级，右侧内容区内部滚动；1440 冒烟通过，CSS 保留 980px 以下单列规则。 |
| KB-UX-M2-T07 | Done | Playwright 浏览器冒烟覆盖目录 URL 刷新、管理入口刷新、无效节点空状态和全局滚动边界，截图 `.local-reports/kb-ux-m2-browser-smoke.png`。 |
| KB-UX-M2-T08 | Done | 更新当前路线图、产品/架构文档和 `docs/90-reports/m2-execution-report.md`，固定页面状态管理规范。 |

验收门：

- URL 可分享并恢复到同一内容上下文。
- 管理入口不会抢占默认正文。
- 空目录和异常状态不会误展示节点元数据仪表盘。

## 7. KB-UX-M3 - 知识库目录节点模型扩展为通用对象入口

目标：让知识库目录节点不只表示文档，也可以表示目录、对象引用和外部链接。

Lark 化结果：知识库目录是知识组织入口，可以挂载不同协作对象；对象本身仍由各自模块负责。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M3-T01 | Done | 统一模型已落到 `documents` 兼容树：新增 `node_kind`、`target_object_type`、`target_object_id`、`target_route`、`display_mode`、`target_title_strategy`、`entry_alias`，继续复用 `parent_id/sort_order/doc_type`。 |
| KB-UX-M3-T02 | Done | 本轮明确不新增第二套 `knowledge_base_nodes`；复用 `documents` 树以继承现有权限、移动、排序、归档、搜索和旧链接兼容，V042 只增加通用入口字段。 |
| KB-UX-M3-T03 | Done | `/api/knowledge-bases/{spaceId}/items` 扩展支持 `markdown/folder/object_ref/external_link`；后端校验对象引用必须有目标类型、目标 ID、目标路由，外链必须有目标路由。 |
| KB-UX-M3-T04 | Done | 前端知识库树、目录内容卡、发现列表和内容页侧边树按类型展示内容页、目录、对象入口、外部链接；对象入口按 `targetRoute` 跳转，外链新窗口打开。 |
| KB-UX-M3-T05 | Done | 已支持 `manual/alias/follow_target` 字段和 `entry_alias` 入库返回；本轮只保存策略，不做目标标题监听同步，监听同步归入 M4/M5 生命周期处理。 |
| KB-UX-M3-T06 | Done | 目录入口仍是独立 document-shell 节点，归档/移除入口只作用于入口节点；集成测试覆盖归档对象引用后目标首页内容仍可访问。 |
| KB-UX-M3-T07 | Done | 集成测试覆盖同一 `targetObjectId` 在同一知识库不同目录下创建多个 `object_ref`，树接口能同时返回多个入口。 |
| KB-UX-M3-T08 | Done | 后端限定集成测试、前端 lint/build、浏览器冒烟均通过；冒烟覆盖对象入口/外链展示、对象入口点击进入目标内容路由和中文类型文案。 |

验收门：

- 同一知识库目录可以同时出现目录、内容页、对象引用和外部链接。
- 移除目录入口不会误删目标对象。
- 一个对象可被多处引用，并能区分入口标题和目标标题。

## 8. KB-UX-M4 - 对象引用权限、生命周期和跨模块上下文

目标：收口目录引用对象后的权限、生命周期和跨模块展示边界。

Lark 化结果：用户在知识库里打开对象入口时，系统能清楚解释“为什么能看/不能看”，且不会泄露无权限对象信息。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M4-T01 | Done | 知识库入口列表先按当前用户的知识库节点权限过滤，再对 `object_ref` 通过 `PlatformObjectResolverRegistry` 校验目标对象；只有入口可见且目标 `accessState=available` 时才保留目标标题和路由。 |
| KB-UX-M4-T02 | Done | `DocumentSummary.targetSummary` 返回目标对象类型、标题、路由、来源模块和 `accessState`；搜索结果在目标不可用时返回 `permissionExplanation`，形成节点权限与目标权限的解释底座。 |
| KB-UX-M4-T03 | Done | 目标对象不存在、无权限或无效时，知识库目录节点改写为“无权限对象入口/不存在对象入口/无效对象入口”，清空目标路由并只返回安全状态，不泄露原始标题、摘要和路径。 |
| KB-UX-M4-T04 | Done | 搜索结果针对 `object_ref` 二次解析目标对象，目标不可用时隐藏摘要、链接和原始标题；知识库目录卡片和树节点展示目标状态，IM/通知/项目卡片继续通过平台对象摘要扩展。 |
| KB-UX-M4-T05 | Done | 创建、移动、归档、恢复对象入口写入 `knowledge.node.*` 审计事件；目标不可用读取写入 `knowledge.node.target_unavailable`，便于治理排查。 |
| KB-UX-M4-T06 | Done | 新增 `scripts/inspect-knowledge-object-references.ps1`，只读输出对象引用汇总、无效引用形态、缺失文档目标、同目录重复别名和重复目标引用。 |
| KB-UX-M4-T07 | Done | 前端对象入口和外链卡片统一使用低噪音 badge；目标不可访问时展示“目标无权限/目标不存在/目标无效”等状态，点击不再跳转。 |
| KB-UX-M4-T08 | Done | 后端限定集成测试、引用检查脚本、前端 lint/build 和浏览器冒烟均通过；冒烟验证缺失目标不泄露标题和路由，点击保持在当前目录。 |

验收门：

- 引用对象必须通过知识库入口和目标对象双重权限。
- 无权限或失效目标不泄露敏感信息。
- 跨模块入口都能显示知识库目录上下文。

## 9. KB-UX-M5 - 多维表格作为知识库目录入口

目标：把多维表格作为独立协作对象挂入知识库目录，并可在知识库正文区直接打开。

Lark 化结果：知识库可以沉淀结构化知识，例如需求池、FAQ 库、资产台账和风险库；多维表格不是知识库子模块，而是被知识库引用的对象。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M5-T01 | Done | Base 主模型保持 `bases/base_tables/base_views/base_records` 独立；可引用最小摘要为 `id/name/status/permissionLevel/tableCount/recordCount`，路由为 `/bases/:id[/tables/:tableId]?viewId=`，权限由 `BasePlatformObjectResolver` 裁决。 |
| KB-UX-M5-T02 | Done | 知识库创建对象入口弹窗支持选择已有多维表格，自动写入 `target_object_type='base'`、目标 ID、目标路由、`displayMode='inline'` 和入口别名。 |
| KB-UX-M5-T03 | Done | 知识库目录支持“新建多维表格并挂载”，先调用 Base 创建 API 建立独立 Base 并授予 owner/manage，再创建知识库对象入口。 |
| KB-UX-M5-T04 | Done | 点击 `base` 对象入口时停留在知识库壳 `view=directory`，右侧渲染 `KnowledgeBaseBasePreview`，可查看 Base 摘要、数据表、记录预览，并保留“打开完整表格”。 |
| KB-UX-M5-T05 | Done | Base 入口选择器支持默认数据表和默认视图，目标路由保存 `tableId` 与 `viewId`；预览读取 `viewId` 对应筛选、排序和可见字段，不复制 Base 数据。 |
| KB-UX-M5-T06 | Done | 搜索路径可按 `docType=object_ref` 召回 Base 入口，并返回知识库 ID、目录路径和对象入口类型；最近/收藏/IM/项目后续沿用平台对象摘要和知识库路径扩展。 |
| KB-UX-M5-T07 | Done | Base 入口通过 M4 `targetSummary` 暴露目标 `accessState`、标题、路由和权限等级元数据，形成知识库入口权限 + Base 对象权限的解释底座。 |
| KB-UX-M5-T08 | Done | 后端限定集成测试、前端 lint/build 和浏览器冒烟通过；覆盖已有 Base 引用、Base view 路由、新建 Base 并挂载、知识库内预览和搜索上下文。 |

验收门：

- 知识库目录中可以出现多维表格节点。
- 点击多维表格节点无需跳出知识库就能查看主要内容。
- Base 数据仍归 Base 模块，知识库只提供组织入口和上下文。

## 10. KB-UX-M6 - 结构化块内容模型和迁移边界

目标：定义并落地文档 blocks 主模型，为块编辑器接入做数据准备。

Lark 化结果：文档不再只是一个富文本字段，而是一组可独立渲染、移动、评论、搜索和嵌入对象的内容块。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M6-T01 | Done | 已盘点并保持 `documents.content` 兼容快照、`document_blocks` 主结构、评论 block_id、版本 block_snapshot、导入导出、协同投影和搜索索引依赖不断链。 |
| KB-UX-M6-T02 | Done | `document_blocks` 类型约束扩展到 `paragraph/heading/todo/bulleted_list/numbered_list/quote/code/table/image/file/embed_object/legacy_html/divider` 等，并保留旧 `list/task/code/embed/link` 兼容类型。 |
| KB-UX-M6-T03 | Done | `DocumentBlock` 暴露 `parentId/schemaVersion/attrs/richContent/plainText/anchorId/blockVersion/createdBy/updatedBy`，V043 补齐 `parent_id/anchor_id/block_version`。 |
| KB-UX-M6-T04 | Done | 旧 `documents.content` 继续通过 `blocksFromContent` 投影为初始 blocks；保存 blocks 时同步生成文本兼容快照，`legacy_html` 作为无法结构化内容的保留类型。 |
| KB-UX-M6-T05 | Done | `/api/docs/{id}/blocks` 支持读取、批量保存、插入、局部更新、重排和删除；内部复用版本冲突、版本快照、审计和旧保存兼容链路。 |
| KB-UX-M6-T06 | Done | 搜索索引优先聚合 `document_blocks.plain_text`，fallback 到旧 `content`；搜索命中 block 仍返回 `#doc-block-{blockId}` 深链。 |
| KB-UX-M6-T07 | Done | `knowledge-base-migration-check.ps1` 增加 block 覆盖率、legacy_html、空块、孤儿 parent、排序冲突和 block 回滚预览 SQL。 |
| KB-UX-M6-T08 | Done | 集成测试覆盖旧内容投影、v2 字段读写、局部更新、重排、插入、删除、搜索索引和兼容保存。 |

验收门：

- 新内容可以以 blocks 形式保存和读取。
- 旧富文本内容不会丢失。
- 搜索可以命中 block，并保留旧内容兼容。

## 11. KB-UX-M7 - 块编辑器技术选型 spike 和编辑器适配层

目标：在正式替换编辑器前，用可验证 spike 锁定编辑器技术方案和适配边界。

Lark 化结果：块编辑器不是盲目引入依赖，而是基于当前项目约束选择可落地方案。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M7-T01 | Done | 选型结论固定为继续 Tiptap；BlockNote 因 UI 栈过重作为否决项，Lexical 作为后备方案但当前迁移成本高于收益。 |
| KB-UX-M7-T02 | Done | 新增隔离组件 `KnowledgeContentEditor`，通过现有 `DocEditor` 验证段落、标题、列表、待办、代码、引用、表格、legacy_html 的适配入口。 |
| KB-UX-M7-T03 | Done | 新增 `knowledgeContentAdapter`，支持 blocks -> Tiptap JSON、Tiptap JSON -> block drafts、blocks -> Markdown、Markdown -> block drafts。 |
| KB-UX-M7-T04 | Done | 适配层透传 `canEdit/saving/conflictVisible/collaboration` 等状态，M8 接入时复用现有自动保存、只读、禁用、加载和错误态。 |
| KB-UX-M7-T05 | Done | 风险清单覆盖中文输入法、复制粘贴、undo/redo、快捷键、拖拽、移动端和长文档性能；保留大文档 lazy-preview 阈值。 |
| KB-UX-M7-T06 | Done | 抽象 `KnowledgeContentEditor` 适配层，外部传 blocks，内部暂用 Tiptap/Markdown 兼容画布，输出 block drafts。 |
| KB-UX-M7-T07 | Done | M7 报告和架构文档记录 Tiptap 选型、后备方案、风险、不可做项和 M8/M9/M10/M11 迁移边界。 |
| KB-UX-M7-T08 | Done | `pnpm web:lint`、`pnpm web:build` 和 Node adapter spike 冒烟通过，未接入生产知识库正文路由。 |

验收门：

- 有明确编辑器选型结论和备选方案。
- block schema 与编辑器模型转换可跑通。
- 基础输入、保存、只读、移动端和中文输入风险已验证。

## 12. KB-UX-M8 - 基础块编辑器接入与富文本兼容

目标：用块编辑器接管知识库内容正文区域，同时保留旧内容兼容渲染。

Lark 化结果：用户看到的是连续页面式块编辑体验，而不是传统表单富文本框。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M8-T01 | Done | 知识库内容页正式接入 `KnowledgeContentEditor`，按权限透传只读/编辑状态。 |
| KB-UX-M8-T02 | Done | 复用现有 Tiptap 工具条支持段落、标题、列表、待办、引用、代码、分割线等基础块编辑。 |
| KB-UX-M8-T03 | Done | `knowledgeContentAdapter` 完成 blocks 与兼容 Markdown/Tiptap 模型双向转换，旧 `legacy_html` 仍可渲染。 |
| KB-UX-M8-T04 | Done | 块编辑模式手动保存只走 `/docs/{id}/blocks`，关闭旧协作自动保存通道，避免整篇富文本覆盖结构化块。 |
| KB-UX-M8-T05 | Done | 评论、分享权限、关联对象、知识元数据、版本面板继续保留在内容页周边区域。 |
| KB-UX-M8-T06 | Done | 正文继续使用页面式 Tiptap 画布和现有响应式样式，移动端保留目录/评论/分享快捷动作。 |
| KB-UX-M8-T07 | Done | 增加 `colla.kb.block-editor.mode` 本地回滚开关，可在块编辑器和兼容编辑器之间切换。 |
| KB-UX-M8-T08 | Done | 浏览器冒烟覆盖编辑保存、刷新恢复、旧内容兼容、块/兼容切换；无权限 API 仍返回 403。 |

验收门：

- 正文体验不再像传统富文本框。
- 基础块可以编辑、保存、刷新恢复。
- 旧内容仍可打开，不出现空白或丢失。

## 13. KB-UX-M9 - Slash 命令、嵌入块和块级操作

目标：补齐类 Lark/Notion 式块编辑常用交互。

Lark 化结果：用户可以通过 `/` 插入不同内容块和业务对象，把文档作为协作对象编排画布使用。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M9-T01 | Done | Slash 菜单支持搜索并插入文本、标题、待办、列表、代码、引用、表格、分割线、图片、文件和对象嵌入。 |
| KB-UX-M9-T02 | Done | 拖拽手柄的块级菜单补充上移、下移、复制、删除、转换类型和分割线转换。 |
| KB-UX-M9-T03 | Done | `object-card`/`file-card` 与 `embed_object`、`base_view`、`issue_embed`、`message_embed`、`file_embed`、`link_card` 双向转换。 |
| KB-UX-M9-T04 | Done | Base 视图仍通过对象摘要和 Base API 按权限加载，不复制 Base 数据到文档块。 |
| KB-UX-M9-T05 | Done | 正文中的 `@member`、`#tag`、日期和链接保留为可搜索文本，并渲染为轻量 inline object chip；通知深链在 M10 继续。 |
| KB-UX-M9-T06 | Done | `saveBlocks` 写入 `document.blocks.updated` 审计事件和“块保存自动快照”版本记录。 |
| KB-UX-M9-T07 | Done | 对象卡无权限或解析失败时显示安全占位，只暴露对象类型，不泄露标题、摘要、路径和 ID。 |
| KB-UX-M9-T08 | Done | 补充 adapter 冒烟、lint/build 和浏览器冒烟，覆盖 Slash 菜单、分割线插入、保存恢复和嵌入块转换。 |

验收门：

- `/` 命令可插入常用块。
- 文档可以嵌入多维表格和平台对象卡片。
- 块级操作可保存、可追溯、可权限保护。

## 14. KB-UX-M10 - 块级评论、提及通知和搜索定位

目标：把协作与发现从整篇文档提升到 block 粒度。

Lark 化结果：评论、提及和搜索结果都能准确回到具体段落、表格、图片或嵌入对象。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M10-T01 | Done | 评论模型已支持 `blockId` 和 `anchorType=block`，前端评论面板可选择具体 block。 |
| KB-UX-M10-T02 | Done | block 删除后评论标签降级为“已删除块”，移动后通过 block id 继续定位，选区评论继续按文本 reanchor。 |
| KB-UX-M10-T03 | Done | 评论跳转保留 `commentId`，block 评论链接使用 `#doc-block-{id}` 定位；旧文档级评论仍定位到文档。 |
| KB-UX-M10-T04 | Done | 搜索服务命中 `document_blocks.plain_text` 时生成 block hash，前端正文块挂载 DOM id 并高亮滚动。 |
| KB-UX-M10-T05 | Done | 搜索不可访问态由 `hydrateResult` 清空标题、摘要、路径、标签和维护人等敏感字段。 |
| KB-UX-M10-T06 | Done | 块编辑模式显示“协同待连接”并关闭旧自动保存，`saveBlocks` 单独写 `document.blocks.updated`。 |
| KB-UX-M10-T07 | Done | 评论定位、搜索 hash 和正文 block 高亮统一使用 `doc-block-*` DOM 锚点和 `doc-editor-block-highlight` 样式。 |
| KB-UX-M10-T08 | Done | 前端 lint/build 和浏览器 hash 冒烟通过，验证正文 block id 挂载与 hash 高亮。 |

验收门：

- 评论可以挂到具体 block。
- 搜索能定位并高亮 block。
- block 被移动或删除后，评论链接有可解释降级。

## 15. KB-UX-M11 - 版本、导入导出、治理和迁移工具

目标：让 blocks 成为可版本化、可导入导出、可治理和可迁移的稳定内容系统。

Lark 化结果：块编辑不是只停留在编辑体验，而是进入知识沉淀、治理和迁移闭环。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M11-T01 | Done | `DocumentService.saveBlocks` 按稳定 block id 生成新增、删除、修改、移动和类型转换摘要，并写入 `document_versions.summary`。 |
| KB-UX-M11-T02 | Done | `POST /api/docs/{documentId}/import/markdown` 继续投影 blocks；新增 `/import/html`，基础 HTML 转 blocks，复杂 HTML 进入 `legacy_html`，危险 HTML 写安全占位。 |
| KB-UX-M11-T03 | Done | `exportMarkdown`、`exportHtml` 和知识库级 Markdown 导出优先从 active blocks 生成，嵌入对象降级为安全 directive/占位。 |
| KB-UX-M11-T04 | Done | 知识库治理健康度和风险补充 block 覆盖缺口、空内容块、失效嵌入对象和 block 覆盖率。 |
| KB-UX-M11-T05 | Done | `scripts/knowledge-base-migration-check.ps1` 输出旧富文本回滚覆盖、legacy block、孤儿 block、失效嵌入对象和回滚模板。 |
| KB-UX-M11-T06 | Done | 新增 `scripts/knowledge-base-block-v2-trial.ps1` 和 `pnpm kb:block-v2-trial`，覆盖创建块文档、导入旧内容、嵌入 Base、评论 block、搜索命中和导出。 |
| KB-UX-M11-T07 | Done | 产品范围、架构、平台对象模型、runbook 和 AI 工程治理已同步 blocks 主事实来源和迁移边界。 |
| KB-UX-M11-T08 | Done | 新增 M11 集成测试覆盖版本摘要、HTML 导入、导出降级、治理指标；脚本验证已生成 M11 证据报告。 |

验收门：

- 版本记录能解释 block 级变化。
- 导入导出不丢失主要内容，嵌入对象有安全降级。
- 迁移工具能报告 blocks 覆盖和遗留风险。

## 16. KB-UX-M12 - 体验收口、全量验证和 v2 冻结

目标：完成知识库内容优先、对象目录和块编辑器 v2 的冻结验证。

Lark 化结果：知识库成为内容优先的协作知识空间，既能组织文档，也能组织多维表格和其他对象；文档正文具备块编辑底座。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-UX-M12-T01 | Done | 新增 `scripts/knowledge-base-v2-acceptance-report.ps1` 和 `pnpm kb:v2-acceptance`，聚合内容优先入口、对象目录、Base 引用、block 覆盖、搜索、评论和导入导出证据。 |
| KB-UX-M12-T02 | Done | `knowledge-base-migration-check.ps1` 和 M12 迁移报告覆盖旧富文本、legacy/html block、孤儿节点、失效嵌入对象、搜索上下文和旧 deep link 风险。 |
| KB-UX-M12-T03 | Done | `knowledge-base-block-v2-trial.ps1` 固化 3-5 人试运行路径：创建知识库、块文档、导入旧内容、嵌入 Base、评论 block、搜索命中和导出。 |
| KB-UX-M12-T04 | Done | 浏览器冒烟报告 `.local-reports/kb-v2-browser-smoke-20260704-181800.md` 证明管理员从知识库根路径进入后默认落到内容主路径。 |
| KB-UX-M12-T05 | Done | route-final 执行完整后端测试、后端 package、前端 lint/build、迁移顺序、安全审计和文档契约；最终结果记录在 M12 执行报告。 |
| KB-UX-M12-T06 | Done | `knowledge-base-compat-cleanup-check.ps1` 输出兼容保留决策表，M12 报告固定旧富文本、legacy block、旧 `/docs/:id`、旧评论锚点和旧导入导出的删除条件。 |
| KB-UX-M12-T07 | Done | 当前路线图、产品范围、架构、平台对象模型、runbook 和 AI 工程治理已固定知识库 v2 开发边界。 |
| KB-UX-M12-T08 | Done | `docs/90-reports/m12-execution-report.md` 输出 M12 收口报告，冻结“知识库内容优先 + 通用对象目录 + 结构化块内容系统 v2”。 |

验收门：

- 用户进入知识库默认看到内容。
- 知识库目录可挂载多维表格等对象。
- 文档正文以 blocks 为主并通过迁移检查。
- 旧链接、旧内容、搜索、通知、评论和对象卡片不失效。

## 17. 全局验收标准

1. 不得破坏知识库唯一入口 v1：空间、首页、导航、权限、搜索、协作、治理和迁移检查继续有效。
2. 不得重新引入独立“文档模块”产品入口。
3. 默认阅读路径必须内容优先，节点元数据和治理面板只能作为辅助入口。
4. 知识库目录引用外部对象时，不得复制目标对象数据或绕过目标对象权限。
5. 多维表格保持独立主模型，知识库只是组织入口和上下文承载方。
6. block schema 必须可迁移、可回滚、可搜索、可版本化，并保留旧内容兼容。
7. 无权限内容和无权限嵌入对象不得泄露标题、摘要、路径、维护人或正文。
8. 每个里程碑至少补充目标后端测试；涉及页面主路径和编辑体验的里程碑必须补充前端构建或浏览器验证。
9. 完整 `mvn test`、完整集成测试和全量迁移验证放在 KB-UX-M12 阶段收口执行。
10. 不新增第二份 active 路线图；当前执行入口始终是 `docs/02-roadmap/current-roadmap.md`。

## 18. 下一步入口

从 KB-UX-M1 开始执行，第一轮推进 KB-UX-M1-T01 到 KB-UX-M1-T08。

执行前必须先读取：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/02-roadmap/current-roadmap.md`
5. `docs/03-engineering/ai-engineering-governance.md`

阶段验证策略沿用 AI 工作循环：每轮优先做本轮影响范围内的目标测试、lint/build 或浏览器验证；完整 `mvn test`、完整集成测试和全量迁移验证放在阶段收口里程碑执行。
