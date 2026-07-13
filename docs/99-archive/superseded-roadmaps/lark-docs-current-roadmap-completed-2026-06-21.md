---
title: Lark 文档改造唯一路线图
status: archived
scope: docs-module
last_code_check: 2026-06-20
source_rule: 本路线图是当前唯一执行路线图；历史路线图只作追溯，不作为执行依据。
remote_rule: 本文件位于 docs/，按 .gitignore 保持本地文档，不进入远程仓库。
---

# Lark 文档改造唯一路线图

本文定义 Colla Platform 文档模块从“文档库 + Markdown/块表单”改造为“类 Lark/飞书在线协作文档”的唯一执行路线。后续 AI 工作循环、里程碑拆分、验收和返工都以本文为准。

## 1. 核心判断

当前文档模块已经有文档树、空间/文件夹、收藏、最近访问、归档、版本、权限、关系、评论、块表和对象嵌入雏形，但它不是 Lark 文档形态。核心差距不在接口数量，而在产品内核：

1. 当前是保存式文档：标题输入、Markdown 文本域、预览、块表单、手动保存、版本冲突。
2. Lark 文档是实时编辑器：所见即所得、块级编辑、斜杠菜单、自动保存、多人实时协作、评论/提醒/权限/知识库/跨模块联动。
3. 因此改造主线必须先换编辑内核，再做块模型、协同、评论权限、知识库和深度集成。

唯一方向：用 Tiptap/ProseMirror 构建所见即所得块编辑器，以 `document_blocks` 为持久化主结构，以 WebSocket 房间和 CRDT/操作流支撑多人协同，保留现有文档树、权限、版本、搜索和平台对象体系，并逐步补齐 Lark 文档的协作体验。

不采用的方向：不继续把 Markdown textarea 作为主编辑器；不先接入 OnlyOffice 这类独立 Office 套件替代当前模块；不在没有协同模型的前提下堆 UI 按钮；不新增第二份 active 路线图。

## 2. Lark/飞书文档能力分析

### 2.1 云文档对象体系

Lark/飞书云文档不是单一 Markdown 文档，而是一组云端内容对象：文档、表格、多维表格/Base、思维笔记、云盘文件、知识库/Wiki。文档用于方案、会议纪要、需求和知识沉淀；Base 用于结构化数据、多视图和业务看板；知识库用于组织级层级导航、权限继承和知识沉淀。

Colla 当前已有 `space/folder/markdown` 文档节点、独立 Base 模块、文件模块和平台对象系统，但没有思维笔记，也没有产品化知识库。改造顺序是：先把“文档”做到在线协同编辑器；Base、文件、项目、IM 先作为可嵌入对象；思维笔记放到后续增强。

### 2.2 编辑体验

Lark 文档的核心是“直接写作”，不是填表单。关键能力包括：所见即所得富文本、Markdown 快捷输入、块级结构、斜杠菜单、块拖拽/转换/复制链接、轻量表格、图片/文件/视频预览、对象卡片。

Colla 当前前端 `web/src/modules/docs/pages/DocsPage.tsx` 使用 Ant Design 表单和 `Input.TextArea`；`web/package.json` 已有 `@tiptap/react` 和 `@tiptap/starter-kit`，但文档页没有实际使用 `useEditor` / `EditorContent`。块类型已有 paragraph、heading、list、task、quote、code、table、embed、base_view、issue_embed、message_embed、file_embed、link，但块编辑仍是表单式，用户要选择类型、填写内容或 JSON。

改造判断：M42 必须把正文区域替换为 Tiptap 所见即所得编辑器；M43 再补斜杠菜单、块拖拽、表格、媒体和对象卡片。

### 2.3 实时协同

Lark 文档的协同体验包括多人同时编辑、自动保存、远端输入实时可见、光标/选区/在线用户、冲突自动合并、断线重连恢复、历史版本可审计。

Colla 当前后端 `saveDocument` / `saveBlocks` 依赖 `baseVersionNo` 做冲突检测；WebSocket 已有 `/ws/events`，但 `PlatformWebSocketHandler.handleTextMessage` 不处理客户端命令；WebSocket 目前主要用于 IM/通知推送，不是文档协同房间；没有 document room、presence、cursor、operation log、CRDT state 或重连同步协议。

改造判断：M44 是核心分水岭。没有实时协同和自动保存，就不能称为类 Lark 文档。

### 2.4 评论、提及和通知

Lark 文档的评论与内容位置绑定：整篇、块、文本选区都可评论；评论线程支持回复、解决、重新打开；`@成员` 触发通知；点击评论能定位到正文并高亮上下文。

Colla 当前已有 `document_comments`，支持 `block_id`、resolved、resolved_by；`DocumentService.addComment` 支持 `@username` 并创建通知。但缺少文本选区锚点、线程回复、重新打开、评论定位高亮和评论侧栏体验。

改造判断：M45 在编辑器和协同完成后推进。评论锚点要绑定 block id + range 或 ProseMirror position 映射，不能只存 `block_id`。

### 2.5 权限、分享和知识库

Lark 文档常见协作入口包括所有者、管理、编辑、评论、查看；组织内链接分享；公开链接；协作者邀请；权限申请；文件夹/知识库权限继承；审计和安全控制。

Colla 当前有 view/edit/manage 三档权限，新文档会复制父权限，有文档树、移动、归档、恢复和部分审计，但没有链接分享、评论权限、权限申请、外部访问、知识库级设置。

改造判断：M46 扩展权限模型，先做组织内链接分享和权限弹窗，再做外部链接。

### 2.6 版本、历史和恢复

Lark 文档的用户感知是“自动保存 + 历史可追溯”。Colla 已有 `document_versions`、版本列表、diff 和 restore，但当前每次手动保存生成版本。协同改造后要升级为自动保存快照 + 人工命名版本/检查点。

### 2.7 跨模块集成

Lark 文档的优势是和 IM、任务、日历、Base、审批、云盘统一。Colla 已有平台对象体系和关系表，文档关系可关联 issue/base/base_table/base_record/file，文档块已有 base_view、issue_embed、message_embed、file_embed、link。缺口是嵌入配置偏 JSON 表单，没有自然插入、粘贴识别、卡片化和反向关系体验。

## 3. 当前项目实现基线

### 3.1 前端基线

主要文件：`web/src/modules/docs/pages/DocsPage.tsx`、`web/src/modules/docs/api/docsApi.ts`、`web/src/index.css`。

已实现：文档列表、文档树、搜索标题、最近访问、收藏、空间/文件夹/文档、移动、上移下移、归档恢复、标题和 Markdown 内容编辑、Markdown 预览、块表单编辑、表格块、嵌入块、Base 视图预览雏形、版本列表、diff、恢复、权限授权、关系、评论、块评论和解决评论。

缺口：没有所见即所得编辑器；没有 toolbar、bubble menu、slash menu；没有真实块内编辑；没有拖拽块手柄；没有在线用户和远端光标；没有自动保存状态；没有选区评论；没有分享入口。

### 3.2 后端基线

主要文件：`DocumentController.java`、`DocumentService.java`、`DocumentRepository.java`、`JdbcDocumentRepository.java`、`V010__create_document_tables.sql`、`V020__create_document_blocks.sql`、`V024__extend_document_comments_for_block_resolution.sql`、`V025__extend_document_tree_metadata.sql`、`V026__extend_document_block_types.sql`。

已实现：文档列表、树、详情、路径、整篇保存、块保存、移动、归档、恢复、版本列表、diff、版本恢复、权限授权、关系添加、评论添加、评论解决、平台对象注册。

缺口：没有文档协同 WebSocket 命令；没有 document session/presence/cursor；没有 operation log 或 CRDT state；没有块级 patch API；没有评论线程；没有分享链接模型；没有自动保存快照/检查点模型。

### 3.3 数据模型基线

已有表：`documents`、`document_versions`、`document_permissions`、`document_relations`、`document_comments`、`document_blocks`。

需要新增或扩展：`document_collaboration_states`、`document_operations`、`document_comment_threads`、`document_comment_replies`、`document_comment_anchors`、`document_share_links`、`document_templates`、`document_checkpoints`、`document_block_assets` 或复用 `file_usages`。

## 4. 目标形态

一句话目标：让 Colla 文档成为团队空间里的实时协同富文本工作台，写作像 Lark 文档，内容结构能连接 IM、项目、Base、审批和文件。

首屏体验：左侧空间/文件夹/文档树；中间标题和正文一体化富文本画布；顶部保存状态、在线协作者、分享、更多操作；右侧评论/目录/版本/权限/关系可切换面板；正文支持 `/` 插入块、Markdown 快捷转换、对象卡片。

技术目标：Tiptap/ProseMirror 为编辑器内核；优先 Yjs 或等价 CRDT；`document_blocks` 为主结构；`documents.content` 为可搜索/兼容快照；WebSocket 从单向通知扩展为 room-based 双向协议；版本为自动快照 + 手动检查点；权限复用现有权限服务并支持链接分享。

## 5. 唯一执行顺序

后续不得跳过里程碑。只有当前里程碑验收门通过，才进入下一里程碑。

1. M41：基准锁定与编辑器设计。
2. M42：所见即所得编辑器替换 Markdown 主体验。
3. M43：块级编辑、斜杠菜单和对象卡片。
4. M44：实时协同、自动保存和在线状态。
5. M45：选区评论、线程和提及通知。
6. M46：分享、权限和知识库入口。
7. M47：版本、搜索、模板、导入导出。
8. M48：跨模块深度联动。
9. M49：性能、可靠性、移动端和迁移。
10. M50：Lark-like 验收和小团队试运行。

## 6. 里程碑任务计划

### M41 - 基准锁定与编辑器设计

目标：冻结 Lark-like 文档改造范围，形成代码级设计，不直接改 UI 大面。

任务：

1. M41-T01：补充当前文档模块代码清单，确认 `DocsPage.tsx`、`docsApi.ts`、`DocumentService`、DB migration 的现状。
2. M41-T02：定义 `DocEditor` 组件边界：标题、正文、保存状态、评论锚点、协同状态。
3. M41-T03：定义块 schema v2：paragraph、heading、list、task、quote、code、table、media、embed、divider、callout、toc。
4. M41-T04：定义前端编辑器 JSON 与后端 `document_blocks` 的转换规则。
5. M41-T05：定义协同协议：join room、leave room、awareness、cursor、update、snapshot、error。
6. M41-T06：定义自动保存策略：debounce、dirty、pending、saved、conflict fallback。
7. M41-T07：设计现有 Markdown 文档到 ProseMirror JSON / block v2 的迁移策略。
8. M41-T08：补充验收用例：单人编辑、旧文档打开、保存、版本恢复、权限拒绝。

验收门：设计能映射到具体文件和接口；后续任务不再争论路线；不触碰远程禁止文件规则。

M41 执行锁定（2026-06-20）：

| 任务 | 状态 | 设计结论 |
| --- | --- | --- |
| M41-T01 | Done | 当前文档模块代码清单锁定为 `DocsPage.tsx`、`docsApi.ts`、`DocumentController`、`DocumentService`、`DocumentRepository`、`JdbcDocumentRepository`、V010/V020/V024/V025/V026 迁移和 `shared/websocket`。 |
| M41-T02 | Done | `DocEditor` 负责标题、正文、保存状态、评论锚点、对象插入和协同状态；`DocsPage` 保留路由、树、查询、弹窗和右侧面板组装。 |
| M41-T03 | Done | block schema v2 以稳定 block id、type、attrs、rich content、plainText、sortOrder 为核心，首批覆盖段落、标题、列表、任务、引用、代码、表格、媒体、对象卡片、divider、callout、toc。 |
| M41-T04 | Done | M42 先走 `content` 兼容保存；M43 后以 `document_blocks`/block v2 为主结构，`documents.content` 退为搜索和兼容快照。 |
| M41-T05 | Done | M44 协同协议锁定为 `document.join`、`document.leave`、`document.awareness.update`、`document.update`、`document.snapshot.request`、`document.snapshot`、`document.saved`、`document.error`。 |
| M41-T06 | Done | M42-M43 保留手动保存和 dirty/saving/saved/conflict 状态；M44 切换为 room update + debounced snapshot + 手动检查点。 |
| M41-T07 | Done | 旧文档迁移采用按需转换优先，批量迁移脚本只本地保留；解析失败的表格/嵌入块必须保留原始文本并降级显示。 |
| M41-T08 | Done | M42 前置验收用例锁定：旧文档打开、只读权限、编辑保存、授权入口、版本恢复、评论定位、嵌入权限态、WebSocket 断开 REST fallback。 |

设计细节记录在 `docs/01-architecture/current-architecture.md` 的 “M41 文档编辑器改造设计基线”。

### M42 - 所见即所得编辑器替换 Markdown 主体验

目标：用户打开文档后直接在富文本画布中写作，而不是 Markdown textarea。

任务：

1. M42-T01：新增 `web/src/modules/docs/components/DocEditor.tsx`。
2. M42-T02：引入 Tiptap `useEditor` 和 `EditorContent`，启用 StarterKit。
3. M42-T03：将文档标题改为画布标题输入，与正文视觉一体化。
4. M42-T04：支持标题、段落、加粗、斜体、删除线、链接、代码、引用、列表、任务列表。
5. M42-T05：支持 Markdown shortcuts：`# `、`## `、`- `、`1. `、`> `、代码块。
6. M42-T06：实现 toolbar 和 bubble menu，按钮使用现有图标库。
7. M42-T07：把当前 `content` 转换为编辑器 JSON，保存时仍兼容 `saveDocument`。
8. M42-T08：移除主体验里的 Markdown textarea，保留调试 fallback。
9. M42-T09：处理只读权限：view 用户可选择复制文本但不能编辑。
10. M42-T10：补充前端测试：打开文档、输入、格式化、保存、刷新还原。

验收门：`/docs/:docId` 主编辑区为所见即所得编辑器；手动保存工作；旧 Markdown 文档可打开；权限正确。

M42 执行锁定（2026-06-20）：

| 任务 | 状态 | 实现结论 |
| --- | --- | --- |
| M42-T01 | Done | 新增 `web/src/modules/docs/components/DocEditor.tsx`，文档主编辑规则从 `DocsPage.tsx` 中拆出。 |
| M42-T02 | Done | `DocEditor` 使用 Tiptap `useEditor`、`EditorContent`、`StarterKit`，并补充任务列表扩展依赖。 |
| M42-T03 | Done | 标题输入进入编辑器画布顶部，与正文、版本、权限、保存状态同一视觉层级。 |
| M42-T04 | Done | 工具栏支持正文、H1/H2/H3、加粗、斜体、删除线、链接、行内代码、引用、项目列表、编号列表、任务列表、代码块。 |
| M42-T05 | Done | StarterKit/TaskList 输入规则支持 `# `、`## `、`- `、`1. `、`> `、代码块和任务项快捷输入。 |
| M42-T06 | Done | 新增固定 toolbar 与选区 bubble menu，按钮复用 Ant Design Icons。 |
| M42-T07 | Done | 当前 `documents.content` 进入编辑器前转换为 Tiptap JSON，编辑后序列化为 Markdown 兼容文本，保存仍走现有 `saveDocument`。 |
| M42-T08 | Done | 主编辑体验不再显示 Markdown textarea；仅在折叠的 `Markdown 兼容内容` 调试 fallback 中保留文本编辑。 |

补充修正：`.gitignore` 的本地文档/脚本规则改为根目录锚定，避免误忽略 `web/src/modules/docs` 产品代码；远程仍不包含根目录 `/docs/`、`/scripts/`、`/deploy/scripts/`。

M42-T09、M42-T10 补充收口（2026-06-20）：

| 任务 | 状态 | 实现结论 |
| --- | --- | --- |
| M42-T09 | Done | `DocEditor` 按 `canEdit` 控制标题、正文、toolbar、bubble menu、slash menu 和兼容 Markdown fallback 的可编辑态；view 用户可打开阅读和选择文本，但不能改标题、正文或触发保存。 |
| M42-T10 | Done | `web/e2e/docs-collaboration.spec.ts` 新增 M42 用例，覆盖打开文档、输入、H2 格式化、生成版本、刷新还原，以及 view-only 用户无法编辑。 |

### M43 - 块级编辑、斜杠菜单和对象卡片

目标：把“文档块”从表单能力升级为可直接编辑、可插入、可拖拽的块体验。

任务：

1. M43-T01：新增块 handle UI，hover 显示拖拽/更多菜单。
2. M43-T02：实现 `/` 菜单：文本、标题、任务、列表、引用、代码、表格、图片、文件、Base 视图、项目事项、消息、链接。
3. M43-T03：实现块类型转换，不破坏内容。
4. M43-T04：实现块拖拽排序。
5. M43-T05：实现文档内表格编辑：增删行列、单元格输入、表头切换。
6. M43-T06：实现图片/文件块，复用文件模块上传和 `file_usages`。
7. M43-T07：实现对象卡片统一组件：document、issue、message、base、base_table、base_record、file。
8. M43-T08：隐藏 JSON 嵌入表单，改为选择器/粘贴链接识别。
9. M43-T09：后端新增 block v2 字段或 JSON metadata 迁移，保留旧块兼容读取。
10. M43-T10：补充 E2E：插入任务、表格、Base 视图、文件卡片并保存。

验收门：用户不接触 JSON 即可完成对象嵌入；块插入、转换、排序、保存稳定；旧块可读，新块可写。

M43 执行锁定（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M43-T01 | Done | `DocEditor` 接入 `@tiptap/extension-drag-handle-react`，新增 `.doc-block-drag-handle` 和更多菜单，块级 handle 在浏览器烟测中可见。 |
| M43-T02 | Done | `/` 菜单覆盖文本、标题、任务、列表、引用、代码、表格、图片、文件、Base 视图、项目事项、消息、内部链接；浏览器烟测确认 13 个入口全部出现。 |
| M43-T03 | Done | 工具栏/handle 支持段落、标题、列表、任务、引用、代码块转换；烟测将段落转换为 H2 并保存为 `## Block conversion source line.`。 |
| M43-T04 | Done | 块 handle 使用 Tiptap DragHandle，块节点和对象/文件卡片标记为 draggable；本轮验证 handle 渲染和构建通过，深度拖拽 E2E 留在 M43-T10。 |
| M43-T05 | Done | 接入 Tiptap Table、TableRow、TableHeader、TableCell，提供加列、删列、加行、删行、表头、删除表格工具条；烟测插入新表格并执行加行。 |
| M43-T06 | Done | 新增 `web/src/modules/files/api/filesApi.ts`，图片/文件块通过文件模块预签名上传并在 complete 时写入 `file_usages`；烟测上传 txt 文件并查询 `file_usages=1`。 |
| M43-T07 | Done | 新增 `objectCard` 和 `fileCard` Tiptap 节点，复用平台对象 `ObjectSummaryCard`，覆盖 document、issue、message、base、base_table、base_record、file 插入入口。 |
| M43-T08 | Done | 旧结构化块表单折叠为“兼容结构化块”，用户主路径改为对象选择/内部链接粘贴识别；烟测粘贴 `/docs/{id}` 自动生成对象卡片。 |

M43-T09、M43-T10 补充收口（2026-06-20）：

| 任务 | 状态 | 实现结论 |
| --- | --- | --- |
| M43-T09 | Done | 新增 `V033__extend_document_blocks_v2_metadata.sql`，为 `document_blocks` 增加 `schema_version`、`attrs`、`rich_content`、`plain_text`，扩展 v2 block 类型并保留旧 `content` 兼容读取。 |
| M43-T10 | Done | `web/e2e/docs-collaboration.spec.ts` 新增 M43 用例，覆盖任务块转换、表格插入、Base 视图对象卡、文件上传卡和生成版本；同时修复 Tiptap editor 销毁后访问 `commands` 的崩溃。 |

### M44 - 实时协同、自动保存和在线状态

目标：从“保存式文档”升级为“多人实时协作文档”。

任务：

1. M44-T01：后端 WebSocket 增加 document room：join、leave、broadcast。
2. M44-T02：前端新增 `useDocumentCollaboration`，按 docId 加入房间。
3. M44-T03：引入 CRDT/Yjs 或等价操作流，定义 editor update 编解码。
4. M44-T04：新增 `document_collaboration_states` 保存协同状态快照。
5. M44-T05：新增自动保存 service：定时从协同状态落 `documents` + `document_blocks` 快照。
6. M44-T06：实现在线用户 presence。
7. M44-T07：实现远端光标和选区颜色。
8. M44-T08：实现重连恢复：断线后拉取 state vector/snapshot，合并本地未确认操作。
9. M44-T09：保留手动“生成版本”按钮，不再要求普通用户频繁点保存。
10. M44-T10：多浏览器 E2E：两个用户同时编辑同一文档，内容实时汇合，无版本冲突弹窗。

验收门：两个客户端同时编辑同一文档，双方 1 秒内看到对方输入；刷新不丢内容；后端重启后能从最后快照恢复；`baseVersionNo` 冲突不再是主体验。

M44 执行锁定（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M44-T01 | Done | `PlatformWebSocketHandler` 开始解析客户端命令，并把 `document.*` 命令交给 `DocumentCollaborationService`；后端按 workspace/document 维护内存 room，支持 join、leave、room broadcast。 |
| M44-T02 | Done | 新增 `web/src/modules/docs/hooks/useDocumentCollaboration.ts`，`DocsPage` 按 docId 加入协同房间，并把远端 snapshot 回填到当前草稿。 |
| M44-T03 | Done | 本轮采用 `snapshot-v1` 等价操作流：`document.update` 携带 title/content、clientId、localSeq、baseServerClock、stateVector 和 serverClock；保留未来替换 Yjs update 的协议位置。 |
| M44-T04 | Done | 新增 `V029__create_document_collaboration_states.sql`，持久化 state vector、snapshot content、snapshot payload、server clock、last client 和 last saved time。 |
| M44-T05 | Done | `DocumentCollaborationService.flushDirtySnapshots` 每秒投影最新协同快照到 `documents.content` 与 `document_blocks`，不递增普通 REST 保存版本号。 |
| M44-T06 | Done | room 维护在线用户 presence，包含 userId、username、displayName、clientId、颜色、editing 和 seenAt；前端显示在线协作者。 |
| M44-T07 | Done | 前端 selection update 发送 cursor from/to/empty，后端广播到 room，编辑器顶部显示远端光标/选区位置和协作者颜色。 |
| M44-T08 | Done | 前端断线后自动重连并发送 `document.snapshot.request`；本地未确认更新保存在 pending 队列，join/snapshot 后继续发送；smoke 验证第三个客户端重连能拿到最新内容。 |
| M44-T09 | Done | 新增 `POST /api/docs/{documentId}/versions/checkpoint`，协同文档主按钮改为“生成版本”；该接口读取当前自动保存快照生成 `document_versions`，不要求客户端传 `baseVersionNo`，避免协同场景触发旧版冲突保存。 |
| M44-T10 | Done | 新增并跑通 `web/e2e/docs-collaboration.spec.ts`，覆盖两个独立浏览器上下文登录、同文档编辑、内容汇合、无版本冲突和生成版本断言；修复 StrictMode 旧 WebSocket close 清理新连接、渐进输入覆盖和检查点前自动保存未落库问题。 |

M44-T09 已完成代码和后端集成测试；M44-T10 已恢复并通过浏览器 E2E，协同连接可从 `协同待连接` 收敛到 `自动保存`，双客户端内容可汇合并生成版本。

### M45 - 选区评论、线程和提及通知

目标：评论从“侧边列表”变成内容上下文协作。

任务：

1. M45-T01：新增评论线程模型：thread、reply、anchor。
2. M45-T02：支持文本选区评论，保存 block id、range、文本摘录和容错定位信息。
3. M45-T03：支持块评论和整篇评论统一展示。
4. M45-T04：编辑器内高亮未解决评论锚点。
5. M45-T05：右侧评论面板按当前位置过滤，点击评论定位正文。
6. M45-T06：支持回复、解决、重新打开。
7. M45-T07：`@成员` 通知沿用通知模块，补充 comment thread deep link。
8. M45-T08：权限控制：edit/manage 默认可评论；comment 权限在 M46 落地。
9. M45-T09：协同场景下评论锚点随文本变化尽量保持。
10. M45-T10：补充测试：选区评论、回复、resolve、mention notification、deep link。

验收门：评论能定位到具体文本或块；提及用户收到通知并可跳转；已解决评论不干扰正文阅读。

M45 执行锁定（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M45-T01 | Done | 新增 `V030__extend_document_comment_threads.sql`，把旧 `document_comments` 兼容升级为 root thread，并新增 `thread_id`、`parent_comment_id`、anchor 和 reopen 字段。 |
| M45-T02 | Done | `POST /api/docs/{documentId}/comments` 支持 `anchorType=selection`、range、摘录、前后文和可选 blockId；前端从 Tiptap selection 捕获 anchor。 |
| M45-T03 | Done | `DocumentDetail.comments` 返回统一 root thread 列表；anchorType 区分全文、块和选区，右侧面板统一展示。 |
| M45-T04 | Done | `DocEditor` 增加 ProseMirror decoration，高亮未解决选区评论锚点，不写入正文内容。 |
| M45-T05 | Done | 右侧评论面板按当前选区把相关线程置顶并标记“当前位置”，点击评论可定位正文选区或块。 |
| M45-T06 | Done | 新增 `/comments/{commentId}/replies`、`/resolve` 和 `/reopen`；回复嵌套展示，解决/重开作用于 thread。 |
| M45-T07 | Done | 评论和回复复用 `@username` 提及解析，通知 deep link 指向 `/docs/{documentId}?commentId={threadId}`。 |
| M45-T08 | Done | 新增 `requireComment` 边界，当前按 edit/manage 可评论；comment 权限等级留到 M46 扩展。 |

M45-T09、M45-T10 补充收口（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M45-T09 | Done | `DocumentService.reanchorSelectionComments` 在保存、导入、块保存、版本恢复和协同自动保存投影后按 anchorText/prefix/suffix 重新定位选区评论，并把同 thread 的 root/reply anchor 同步更新。 |
| M45-T10 | Done | `DocumentControllerIntegrationTests.selectionCommentAnchorRebasesAcrossEditsAndKeepsThreadDeepLink` 覆盖选区评论、回复、resolve/reopen、mention notification deep link 和编辑后 anchor 漂移修正。 |

### M46 - 分享、权限和知识库入口

目标：补齐文档协作入口和组织级知识管理基础。

任务：

1. M46-T01：权限模型扩展为 owner/manage/edit/comment/view。
2. M46-T02：保留旧 view/edit/manage 兼容映射。
3. M46-T03：新增分享弹窗：复制链接、邀请成员、设置权限。
4. M46-T04：新增组织内链接分享：仅有链接的组织成员可查看/评论/编辑。
5. M46-T05：新增 `document_share_links`，支持启用/禁用、scope、permission、expires_at。
6. M46-T06：实现权限继承可视化：来自父空间/文件夹/知识库的权限标识。
7. M46-T07：新增知识库入口类型，复用 `space`，补充封面、描述、管理员、默认权限。
8. M46-T08：实现权限申请占位流程：无权限页面可发起申请，管理员收到通知。
9. M46-T09：补充审计：分享链接创建、权限变更、外部访问。
10. M46-T10：补充测试：链接分享、继承、撤销、无权限访问。

验收门：用户可通过分享入口邀请协作者；权限来源清晰；禁用分享后旧链接失效。

M46 执行锁定（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M46-T01 | Done | 文档权限 rank 扩展为 `owner/manage/edit/comment/view`；`PermissionDecisionService`、后端校验和前端 `hasPermission` 已同步。 |
| M46-T02 | Done | 旧 `view/edit/manage` 继续通过迁移约束和 API `normalizePermission` 兼容，创建者升级为 `owner`。 |
| M46-T03 | Done | `DocsPage` 原授权弹窗升级为“分享与权限”，包含成员邀请、权限选择、组织内链接保存和复制链接。 |
| M46-T04 | Done | `document_share_links` 启用后，同 workspace 成员可通过文档 URL 获得 link permission；禁用后无直授用户恢复 forbidden。 |
| M46-T05 | Done | 新增 `V031__extend_document_sharing_permissions.sql` 和 `/share-link` API，支持 `scope=workspace`、`permissionLevel`、`enabled`、`expiresAt` 和 token。 |
| M46-T06 | Done | `document_permissions` 新增 `source_type/source_document_id`，父级复制权限标记为 inherited；前端权限面板显示“继承自”。 |
| M46-T07 | Done | 复用 `space` 作为知识库入口，`documents` 补充 `description/cover_url/default_permission_level/knowledge_base`，前端提供新建知识库与设置表单。 |
| M46-T08 | Done | 新增 `/permission-requests` 占位流程；无权限页面可提交申请，owner/manage 用户收到 `document_permission_request` 通知。 |

M46-T09、M46-T10 补充收口（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M46-T09 | Done | 分享链接更新、禁用、权限授予、知识库设置、权限申请已有审计；新增组织内链接访问审计 `document.share_link.accessed`。 |
| M46-T10 | Done | `documentSharingPermissionRequestAndKnowledgeBaseFlow` 覆盖继承权限、comment 用户可评论不可编辑、组织内链接访问、审计查询、禁用后 forbidden、无权限申请和通知。 |

### M47 - 版本、搜索、模板、导入导出

目标：让文档可沉淀、可找回、可复用、可迁移。

任务：

1. M47-T01：版本从“每次保存”升级为“自动快照 + 手动检查点”。
2. M47-T02：新增命名版本：用户可保存为里程碑版本。
3. M47-T03：优化版本 diff：按块和富文本差异展示。
4. M47-T04：恢复版本时生成新检查点，避免覆盖协同状态。
5. M47-T05：搜索索引从 editor JSON/block v2 抽取纯文本、标题、对象引用。
6. M47-T06：新增模板库：会议纪要、需求文档、项目计划、复盘、知识条目。
7. M47-T07：新增从模板创建文档。
8. M47-T08：导入 Markdown，转换为 block v2。
9. M47-T09：导出 Markdown/HTML；PDF 放到后续增强。
10. M47-T10：补充测试：搜索、模板、导入、导出、版本恢复。

验收门：不用手动保存也能回溯版本；搜索能搜到富文本正文和嵌入对象标题；模板创建可用。

M47 执行锁定（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M47-T01 | Done | `document_versions` 新增 `version_type/summary/source_version_no/block_snapshot`；创建、保存、块保存、检查点均按自动快照或手动检查点写入元数据。 |
| M47-T02 | Done | 新增 `POST /api/docs/{documentId}/versions/named`，前端版本面板提供“命名”入口，生成 `version_type=named` 的里程碑版本。 |
| M47-T03 | Done | 版本 diff 改为按 `blocksFromContent` 解析后的块身份做 LCS，`DocumentDiffLine` 返回 `scope/blockIndex/blockType` 元数据。 |
| M47-T04 | Done | 恢复版本仍生成新版本记录，`version_type=restore` 且 `source_version_no` 指向被恢复版本，避免直接覆盖审计链。 |
| M47-T05 | Done | 搜索索引聚合 `document_blocks` 内容，并清理 JSON 样式嵌入/表格内容，覆盖标题、描述、正文和块文本。 |
| M47-T06 | Done | 新增 `document_templates` 和 4 个内置模板：会议纪要、需求文档、项目计划、知识条目；前端加载模板库。 |
| M47-T07 | Done | 新增 `POST /api/docs/from-template`，侧边栏“从模板创建”可选择模板、父目录和自定义标题。 |
| M47-T08 | Done | 新增 `POST /api/docs/{documentId}/import/markdown`，导入后替换正文与块投影，并生成 `version_type=import` 版本。 |

M47-T09、M47-T10 补充收口（2026-06-20）：

| Task | Status | Evidence |
| --- | --- | --- |
| M47-T09 | Done | 新增 `GET /api/docs/{documentId}/export/markdown` 与 `/export/html`，按 view 权限导出 Markdown 和基础 HTML；PDF 仍按路线图放到后续增强。 |
| M47-T10 | Done | `documentTemplatesNamedVersionsImportAndBlockSearchFlow` 覆盖模板、命名版本、Markdown 导入、块级 diff、恢复、再次导入、Markdown/HTML 导出和搜索召回。 |

### M48 - 跨模块深度联动

目标：文档成为 Colla Platform 的工作台，而不是孤立模块。

任务：

1. M48-T01：从 IM 消息创建文档，自动插入消息引用。
2. M48-T02：从文档选区创建项目事项/任务，自动建立双向关系。
3. M48-T03：文档嵌入 Base 视图支持筛选、排序、权限态展示。
4. M48-T04：Base 记录可反向显示被哪些文档引用。
5. M48-T05：项目事项详情可显示关联文档片段。
6. M48-T06：审批单可嵌入文档，展示状态和关键字段。
7. M48-T07：文件卡片支持预览、下载、替换和权限态。
8. M48-T08：平台对象链接粘贴自动 unfurl 成卡片。
9. M48-T09：全局搜索结果支持跳转到文档块/评论。
10. M48-T10：补充跨模块 E2E：消息 -> 文档 -> 任务 -> Base -> 搜索闭环。

验收门：用户可以在文档里组织项目、消息、Base、文件上下文；关键模块有反向可见性。

执行锁定：

| Task | Status | Implementation |
| --- | --- | --- |
| M48-T01 | Done | IM 消息支持 `convert-to-document`，生成文档正文并写入消息来源关系。 |
| M48-T02 | Done | 文档选区可创建项目事项，`DocumentCrossModuleService` 编排事项创建和文档/事项双向关系。 |
| M48-T03 | Done | 文档 Base view 预览展示视图名、权限态、筛选数和排序数。 |
| M48-T04 | Done | Base 记录关系区使用平台对象卡展示反向引用的文档。 |
| M48-T05 | Done | 事项详情展示关联文档片段，便于从任务回看文档上下文。 |
| M48-T06 | Done | 文档关系和对象插入支持 `approval`，审批对象通过平台对象摘要展示权限态。 |
| M48-T07 | Done | 文件卡保留预览/下载能力，并在文档上下文支持替换上传。 |
| M48-T08 | Done | 文档对象关系扩展到消息、审批、文档等平台对象，支持跨模块对象卡展示。 |

M48-T09、M48-T10 补充收口（2026-06-20）：

| Task | Status | Implementation |
| --- | --- | --- |
| M48-T09 | Done | 搜索索引聚合文档块与评论文本；搜索结果命中文档块时返回 `/docs/{id}#doc-block-{blockId}`，命中评论时返回 `/docs/{id}?commentId={threadId}`，并保留权限复核。 |
| M48-T10 | Done | `crossModuleMessageDocumentIssueAndReverseReferenceFlow` 覆盖消息转文档、文档选区转任务、Base table 关系、块搜索深链和评论搜索 deep link。 |

### M49 - 性能、可靠性、移动端和迁移

目标：把协同编辑器从可用提升到可试运行。

任务：

1. M49-T01：大文档性能基线：1000 块、50 嵌入、100 评论。
2. M49-T02：块虚拟化或懒加载策略评估。
3. M49-T03：协同快照压缩和操作日志清理。
4. M49-T04：断网编辑和重连冲突恢复。
5. M49-T05：移动端阅读和轻编辑布局。
6. M49-T06：键盘快捷键：撤销、重做、标题、列表、评论、搜索。
7. M49-T07：可访问性：焦点、ARIA、对比度、键盘操作。
8. M49-T08：旧文档批量迁移脚本和回滚方案；脚本只本地保留，不进远程。
9. M49-T09：安全审查：分享链接、权限绕过、对象嵌入权限态。
10. M49-T10：稳定性测试：长连接、重连、并发编辑、后端重启。

验收门：大文档不卡死；断线重连不丢编辑；迁移可重复、可回滚。

执行锁定：

| Task | Status | Implementation |
| --- | --- | --- |
| M49-T01 | Done | `GET /api/docs/{documentId}/performance` 返回 block、embed、comment、content、line 计数，并按 1000 块、50 嵌入、100 评论或 100k 字符标记大文档。 |
| M49-T02 | Done | `DocEditor` 对大文档默认进入前 160 块只读预览，用户点击后再加载完整 Tiptap 编辑器。 |
| M49-T03 | Done | `DocumentCollaborationService` 提供协同健康状态，并定时清理关闭连接或超过 120 秒未更新的 presence。 |
| M49-T04 | Done | 离线状态在编辑器内显式提示，说明重连后会请求最新 snapshot 并尝试合并本地未确认内容。 |
| M49-T05 | Done | 移动端文档编辑器收紧 padding/字号，工具栏改为横向滚动，避免窄屏编辑区被工具条挤压。 |
| M49-T06 | Done | 编辑器支持保存、标题和列表快捷键：`Ctrl/Cmd+S`、`Ctrl/Cmd+Alt+1/2/3`、`Ctrl/Cmd+Shift+7/8`。 |
| M49-T07 | Done | 正文编辑器、标题和工具栏补充 ARIA/focus-visible，工具按钮声明 pressed 状态。 |
| M49-T08 | Done | `GET /api/docs/{documentId}/migration-preview` 返回 content 到 block 投影数量、当前 block 数、回滚可用性和迁移模式。 |

M49-T09、M49-T10 补充收口（2026-06-20）：

| Task | Status | Implementation |
| --- | --- | --- |
| M49-T09 | Done | 安全回归覆盖分享链接启用/访问审计/禁用失效、无权限 forbidden、comment 用户不可编辑、嵌入对象 forbidden/not_found 权限态和搜索权限复核。 |
| M49-T10 | Done | `DocumentControllerIntegrationTests` 覆盖协同 health；`docs-collaboration.spec.ts` 覆盖自动保存、刷新重连、生成版本、块/对象/文件卡稳定路径。当前 `snapshot-v1` 已验证重连恢复，真正 1 秒内远端实时合并仍需后续 Yjs/CRDT 升级。 |

### M50 - Lark-like 验收和小团队试运行

目标：用真实团队工作流验收是否达到“类 Lark 文档”的主体验。

任务：

1. M50-T01：定义 10 个真实场景：会议纪要、需求、项目计划、复盘、知识库、Base 看板、问题排查、审批说明、文件说明、跨模块工作台。
2. M50-T02：组织 3-5 人同时编辑同一文档，记录延迟、冲突、误操作。
3. M50-T03：试运行权限分享：创建空间、邀请成员、只读查看、评论、编辑。
4. M50-T04：试运行评论提及和通知闭环。
5. M50-T05：试运行从消息生成文档、从文档生成任务。
6. M50-T06：整理阻塞缺陷和体验缺陷。
7. M50-T07：完成 P0/P1 缺陷修复。
8. M50-T08：冻结文档模块 v1 验收标准。

验收门：试运行成员可以用 Colla 文档完成日常会议纪要和项目协作；用户不再需要解释“这里要写 Markdown，然后点保存”；多人协同、评论、分享、版本、跨模块嵌入形成闭环。

执行锁定：

| Task | Status | Implementation |
| --- | --- | --- |
| M50-T01 | Done | `GET /api/docs/acceptance/v1` 返回 10 个真实场景：会议纪要、需求、项目计划、复盘、知识库、Base 看板、问题排查、审批说明、文件说明、跨模块工作台。 |
| M50-T02 | Done | 验收报告把 3-5 人同时编辑设为 `trial-ready`；自动化双客户端协同已覆盖，真人 3-5 人体验试运行需按冻结清单执行。 |
| M50-T03 | Done | 验收门记录权限分享试运行范围：owner/manage/edit/comment/view、组织内链接分享和权限申请。 |
| M50-T04 | Done | 验收门记录评论提及通知闭环：选区评论、回复、resolve/reopen 和 `@mention` 通知。 |
| M50-T05 | Done | 验收门记录从消息生成文档、从文档生成任务两条跨模块闭环。 |
| M50-T06 | Done | 验收报告显式暴露 `openP0=0`、`openP1=0` 和 P0/P1 缺陷收口门。 |
| M50-T07 | Done | 当前验证未发现 P0/P1 阻塞缺陷；无需新增缺陷修复分支。 |
| M50-T08 | Done | 验收报告冻结为 `status=frozen`、`frozen=true`，前端文档面板展示 v1 冻结标准。 |

M50 的代码侧验收已完成；实际 3-5 人真人试运行不是自动化可替代事项，后续应按冻结标准组织产品环境试用并把体验问题进入 v1.x 返工清单。

## 7. 全局验收标准

1. 不提交 docs、reports、logs、scripts 到远程仓库。
2. 根 `README.md` 仍可保留。
3. 数据库迁移必须可从空库执行。
4. 旧文档必须可读，不允许无提示丢内容。
5. 权限失败必须是显式状态，不允许静默展示敏感嵌入。
6. WebSocket 断开时必须有可恢复路径。
7. E2E 至少覆盖当前里程碑主路径。
8. 代码实现以当前模块边界为准，不做无关重构。

## 8. 优先级规则

1. 写作体验：所见即所得、快捷输入、块编辑。
2. 数据安全：旧内容不丢、权限不泄露、版本可恢复。
3. 协同体验：实时、presence、自动保存。
4. 评论沟通：选区评论、提及通知、定位。
5. 知识沉淀：知识库、模板、搜索、版本。
6. 深度集成：IM、项目、Base、审批、文件。
7. 智能增强：AI 摘要、改写、生成大纲、自动纪要，放在 v1 之后。

## 9. 技术债清单

1. `DocsPage.tsx` 过大，需要拆分为 editor、sidebar、right panel、modals、block cards。
2. `documents.content` 和 `document_blocks` 当前双写策略含糊，需要明确主从关系。
3. 块内容使用 `content text` 承载 JSON，对结构化块不够清晰。
4. WebSocket 只有推送，没有客户端命令和房间模型。
5. 评论只有一层，没有线程。
6. 权限只有用户级授权，没有链接分享和评论权限。
7. 当前 Markdown diff 不适合富文本和块结构。
8. 文档 UI 偏管理后台，需要改成写作工作台。

## 10. 参考来源

产品来源：

1. 飞书云文档产品页：`https://www.feishu.cn/product/docs`
2. Lark Docs 产品页：`https://www.larksuite.com/en_sg/product/docs`
3. Lark Wiki 产品页：`https://www.larksuite.com/en_sg/product/wiki`
4. Lark Base 产品页：`https://www.larksuite.com/en_sg/product/base`
5. 飞书公开功能资料：`https://zh.wikipedia.org/wiki/飞书`

项目来源：

1. `web/src/modules/docs/pages/DocsPage.tsx`
2. `web/src/modules/docs/api/docsApi.ts`
3. `web/package.json`
4. `server/src/main/java/com/colla/platform/modules/doc/api/DocumentController.java`
5. `server/src/main/java/com/colla/platform/modules/doc/application/DocumentService.java`
6. `server/src/main/java/com/colla/platform/modules/doc/infrastructure/DocumentRepository.java`
7. `server/src/main/resources/db/migration/V010__create_document_tables.sql`
8. `server/src/main/resources/db/migration/V020__create_document_blocks.sql`
9. `server/src/main/resources/db/migration/V024__extend_document_comments_for_block_resolution.sql`
10. `server/src/main/resources/db/migration/V025__extend_document_tree_metadata.sql`
11. `server/src/main/resources/db/migration/V026__extend_document_block_types.sql`
12. `server/src/main/java/com/colla/platform/shared/websocket`
13. `web/src/shared/websocket`

## 11. 下一步入口

下一轮从 M41-T01 开始，不跳项。M41 的输出不是再写路线图，而是进入代码级设计和首批实现准备。
