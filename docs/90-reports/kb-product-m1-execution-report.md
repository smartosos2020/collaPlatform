---
title: KB-PRODUCT-M1 Execution Report
status: archived
milestone: KB-PRODUCT-M1
updated_at: 2026-07-15
---

# KB-PRODUCT-M1 Execution Report

## Scope

- 执行范围：`KB-PRODUCT-M1-T01` 到 `KB-PRODUCT-M1-T09`。
- 本轮只做现状审计、真实浏览器复核、技术 spike、目标合同和退出顺序冻结。
- 本轮未修改知识内容业务实现、API、数据库或 Flyway；未删除兼容编辑器，未提前实现 M2-M6。
- 工作区在启动前已有未提交改动。本轮只新增 M1 报告和浏览器审计用例，并更新当前路线图；未覆盖其他改动。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M1-T01 | 静态调用链审计 | N/A | 本地代码与 V001-V049 schema | 否 | 无；调用链由完整相关函数和迁移交叉核对 |
| KB-PRODUCT-M1-T02 | 字段生产/消费审计 | N/A | 前端类型、Java domain/repository、V043-V045 | 否 | 无；字段结论由写入、读取和迁移三方证据闭环 |
| KB-PRODUCT-M1-T03 | 真实浏览器交互复核 | real | 本地真实前后端、真实 PostgreSQL、私有后归档夹具 | 否 | 连续输入、块按钮、外部关闭、表格工具栏、不可用对象降级 |
| KB-PRODUCT-M1-T04 | 用户任务覆盖审计 | real | M1 浏览器夹具与现有知识库 E2E 清单 | 否 | 创建后打开内容、编辑、对象降级；协同等后续层级入口必须明确 |
| KB-PRODUCT-M1-T05 | 技术 spike | N/A | 本地依赖解析、隔离 Yjs 并发实验、官方技术文档 | 否 | 无；不以 mock 宣称多人协同完成 |
| KB-PRODUCT-M1-T06 | 架构/API 合同评审 | N/A | 本报告冻结合同 | 否 | 无；M2-M6 实现必须遵循合同或先修订路线 |
| KB-PRODUCT-M1-T07 | 兼容退出评审 | N/A | 当前页面、API、适配器和迁移事实 | 否 | 无；删除动作在后续里程碑按观测、迁移、切流、删除执行 |
| KB-PRODUCT-M1-T08 | 测试分层验证 | real | 前端静态检查、M1 real E2E、现有 Testcontainers/E2E 清单 | 否 | 日常 smoke、双用户双上下文、双节点 route-final 分层明确 |
| KB-PRODUCT-M1-T09 | 架构冻结与准入评审 | N/A | 本报告风险登记和准入清单 | 否 | 无；M2 仅在本报告无未决核心合同后准入 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M1-T01 | Done | 下方“当前编辑与保存调用图”覆盖页面、编辑器、适配器、REST、WebSocket、版本和数据库写入 |
| KB-PRODUCT-M1-T02 | Done | 下方“内容字段事实表”逐字段给出当前角色、风险、目标角色和退出动作 |
| KB-PRODUCT-M1-T03 | Done | `web/e2e/kb-product-m1-editor-audit.spec.ts` 在真实浏览器复核五类问题，并保留首次失败证据 |
| KB-PRODUCT-M1-T04 | Done | 下方“真实用户任务矩阵”覆盖创建、导航、编辑、协同、评论、搜索、分享、对象入口和恢复 |
| KB-PRODUCT-M1-T05 | Done | 完成 snapshot-v1 与 Tiptap JSON + Yjs/Hocuspocus 对比；本地并发更新收敛且重复应用幂等 |
| KB-PRODUCT-M1-T06 | Done | 冻结文档、身份、版本、协同更新、权限、持久化投影和错误合同 |
| KB-PRODUCT-M1-T07 | Done | 冻结兼容路径的观测、迁移、切流、回滚窗口和删除条件 |
| KB-PRODUCT-M1-T08 | Done | 冻结六层测试金字塔和各里程碑实际执行入口 |
| KB-PRODUCT-M1-T09 | Done | 形成风险登记、明确不做范围，并给出 M2 有条件准入结论 |

## 当前编辑与保存调用图

### 1. 块编辑器正式页面当前路径

```text
KnowledgeContentPage
  -> KnowledgeContentWorkspace(blockEditorEnabled=true)
  -> KnowledgeContentEditor
  -> blocksToMarkdown(blocks)
  -> KnowledgeContentEditorCore(markdown string)
  -> Tiptap JSON in browser
  -> tiptapDocumentToMarkdown(on every update)
  -> markdownToBlockDrafts
  -> preserveBlockIdentity(by array index + block type)
  -> KnowledgeContentPage blockDrafts
  -> PATCH /knowledge-bases/{spaceId}/items/{itemId}/blocks
  -> KnowledgeContentService.saveBlocks
  -> contentFromBlocks compatibility projection
  -> addVersion(blockSnapshot)
  -> replaceBlocks
```

结论：页面名义上是块编辑器，实际编辑态仍经两次 Markdown 转换。块 ID 通过数组下标和类型猜测保留，插入、拆分、合并、复杂列表、表格和对象节点无法保证身份稳定。

### 2. 兼容编辑器与实时协同当前路径

```text
KnowledgeContentPage
  -> blockEditorEnabled=false
  -> KnowledgeContentEditorCore(markdown string)
  -> useKnowledgeContentCollaboration(enabled=true)
  -> knowledge.content.update encoding=snapshot-v1
  -> Spring WebSocket /ws/events
  -> KnowledgeContentCollaborationService in-memory room
  -> whole title/content snapshot broadcast
  -> knowledge_content_collaboration_states.snapshot_payload
  -> scheduled flushDirtySnapshots
  -> blocksFromContent(markdown)
  -> replaceBlocks
```

结论：协同只作用于兼容编辑器，传输单位是整篇字符串快照。`stateVector` 实际是递增十进制时钟，客户端 `mergeTextSnapshot` 是按字符串/行启发式合并，不是 CRDT。进程重启依赖最后 payload；跨节点房间、更新去重、乱序和离线合并没有可靠语义。

### 3. REST、版本与数据库当前路径

- `GET item` 的 `content` 由 `JdbcKnowledgeRepository.findContent -> contentFromBlocks(listBlocks)` 动态投影，V045 已删除 `knowledge_base_items.content`。
- 兼容 `PATCH item` 仍接收 Markdown 字符串，由 `blocksFromContent` 重新生成 blocks 和版本快照。
- `PATCH blocks` 先把 blocks 投影成兼容字符串，再写版本 `block_snapshot` 和 active blocks。
- `replaceBlocks` 先软删除全部 active blocks，再按 draft ID upsert；无 ID 的块生成新 UUID。
- 版本恢复以 `block_snapshot` 为输入，再重建 active blocks；版本 `content` 列已由 V045 删除。
- 协同自动保存只更新标题和 active blocks，不增加 `current_version_no`，协同时钟与产品版本号是两套语义。

## 内容字段事实表

| 字段/表示 | 当前生产位置 | 当前消费位置 | 当前结论 | 目标角色与退出动作 |
| --- | --- | --- | --- | --- |
| `KnowledgeContentDetail.content` | repository 从 active blocks 投影 | 页面兼容编辑、协同 Hook、导出和性能统计 | 兼容派生，不是数据库主数据 | 保留为只读 Markdown/纯文本交换投影；M3 后禁止正式编辑写回 |
| block `content` | Markdown 解析、服务端规范化、对象/表格 JSON 字符串 | 导出、搜索投影、旧编辑链、对象解析 | 当前实际主写字段，但同时承载文本、JSON 和 HTML，语义混杂 | M2 后降为可重建兼容/交换投影；不得承载唯一结构 |
| block `attrs` | 服务端默认值或客户端透传 | heading/task/embed 等少量结构 | 部分结构来源；当前可能因按下标合并而过期 | M2 纳入规范 Tiptap node attrs；必须 schema 校验并由 canonical JSON 生成 |
| block `richContent` | 服务端生成浅层 `{type,text/data}` 或客户端透传 | 变化摘要和持久化，编辑器主路径不读取 | 名义富文本、实际未成为事实来源，存在旧值保留风险 | M2 选为每块规范 Tiptap JSON 投影；由完整文档快照生成，不接受双主写 |
| block `plainText` | 服务端从 block 内容派生 | 搜索、预览、统计 | 派生字段 | 继续作为可重建投影；写入时忽略不可信客户端值或校验后重建 |
| block `id` | repository 为新块生成 UUID | 评论 blockId、版本 diff、前端 DOM anchor | 设计上是身份主键，但前端当前按数组位置猜测 | M2/M3 写入 Tiptap 顶层 node `blockId`，创建后永不因排序/格式改变 |
| block `anchorId` | 客户端透传或服务端用 `block-{sortOrder}`/`block-{uuid}` 回填 | DOM 定位和评论高亮 | 辅助锚点；无 ID 新块使用顺序值，不稳定 | 默认等于稳定 block ID 的可读编码；禁止以 sortOrder 生成长期锚点 |
| version `block_snapshot` | `saveBlocks`/导入/恢复时序列化 drafts | diff、版本恢复 | 当前版本事实来源 | M2 改为同一 schema 的 canonical Tiptap JSON；记录 schemaVersion 和协同 state vector |
| collaboration `snapshot_payload` | snapshot-v1 服务端写入 blocks 数组 | 进程重启房间恢复 | 当前协同恢复来源，但不保留真正更新历史 | M4-M6 替换为 Yjs binary snapshot + update log；JSON 仅作派生视图 |
| Markdown | 前后端双向转换、导入导出 | 编辑器、协同、版本展示 | 当前隐藏主路径，导致结构损失 | 只保留导入、导出、只读降级和诊断，不进入正式编辑状态循环 |

## 编辑器问题复核

真实命令：

```powershell
$env:COLLA_E2E_ISOLATED='true'
pnpm --dir web exec playwright test e2e/kb-product-m1-editor-audit.spec.ts --config=e2e/playwright.config.ts --grep "@kb-product-m1"
```

| 问题 | 稳定复现步骤 | 预期 | 2026-07-15 实际 | 结论/归属 |
| --- | --- | --- | --- | --- |
| 连续输入 | 打开块编辑器，在第二段末尾逐键输入 ` ABC` | 焦点和光标保持，最终文本完整位于原段落 | DOM 焦点保持，但首次真实运行只留下 `A`；后续运行可见字符漂移到表格单元格 | 未修复；父状态/Markdown 投影回灌覆盖选择，M3/M4 必须先解决，M9 做完整交互收口 |
| 插入/操作按钮越界 | hover 第二个正文块，读取按钮与 `.doc-editor-canvas` 边界 | 两按钮跟随当前块且完全位于编辑框内 | M1 E2E 边界断言通过 | 当前修复有效；保留 M9 视觉回归 |
| 表格工具栏 | 点击表格单元格，比较 toolbar 与 table 几何位置 | 仅表格激活时显示并贴近表格 | `BubbleMenu` 已贴近表格，几何断言通过；浮层缺少 `role/aria-label` | 定位已修复；可访问性缺口归 M9/M11 |
| 插入菜单外部关闭 | 点击块前 `+`，再点击标题输入框 | 菜单关闭且不残留遮挡 | M1 E2E 通过 | 当前修复有效 |
| 对象不可用 | 保存一个不存在的 Base ID 并打开页面 | 安全显示不可用/不可访问，不泄露标题；有效新 Base 应立即可用 | 初始安全降级可见；连续编辑后对象块可能随转换链消失；“新建 Base 后不可用”尚未完成有效对象路径根因定位 | 降级有效但主流程未闭环；M2 保结构、M7 查创建/挂载事务与 resolver |

首次失败被保留在本轮命令输出中：先暴露连续输入丢字符，再暴露工具栏缺少可访问角色定位，最后暴露对象卡在编辑后消失。最终测试只断言当前可观察事实，不把缺陷伪装成通过的产品行为。

## 真实用户任务矩阵

| 用户任务 | 主要角色 | 核心成功结果 | M1 基线入口 | 后续闭环里程碑 |
| --- | --- | --- | --- | --- |
| 创建私有知识库和首页 | owner | 创建后直接打开首页，目录和 URL 一致 | fixture API + 内容页真实打开 | M8、M12 |
| 创建/移动/归档目录和内容 | owner/editor | 树、面包屑、刷新和恢复一致 | 现有 space/item API 与 E2E helper | M8、M12 |
| 连续创建和编辑结构化内容 | editor | 中文/英文/IME 连续输入不丢字符和块身份 | M1 editor audit | M2-M4、M9 |
| 两人同时编辑 | 两名 editor | 不同位置并发修改最终一致，无静默覆盖 | 现有双上下文 E2E 仅验证 snapshot 路径 | M5-M6、M12 |
| 评论、回复、解决、重开 | commenter/editor | 评论锚点随块变化且通知可追踪 | `knowledge-content-core.spec.ts` | M10、M12 |
| 搜索并定位正文/评论 | viewer | 只返回有权结果并定位 block/comment | 现有 search API/浏览器基线 | M10-M11 |
| 分享和权限申请 | owner/viewer | 授权、只读、撤销、过期和申请闭环 | 现有权限 E2E/API | M11-M12 |
| 挂载 Base/项目/文件/知识内容 | editor | 通过选择器选择，不输入 UUID；有效对象立即打开 | object-card route-final + M1 不可用降级 | M7、M12 |
| 版本、冲突和恢复 | editor | 失败/冲突可恢复，恢复产生新版本 | 现有版本 API/E2E | M4、M10、M12 |
| 导入、导出和离线恢复 | editor | 结构转换有报告，离线内容可重连或导出 | 当前 Markdown/HTML API | M6、M10、M12 |

## 技术 Spike 与选型

### 对比结论

| 维度 | 当前 snapshot-v1 | Tiptap JSON + Yjs/Hocuspocus | 决策 |
| --- | --- | --- | --- |
| 并发合并 | 整篇字符串 + 行级启发式；顺序相关 | Yjs CRDT update 可乱序、重复应用并最终收敛 | 选择 Yjs |
| 块结构 | 每次转 Markdown 后重建 | Tiptap/ProseMirror JSON 原生结构，顶层 node 可携带稳定 blockId | 选择 Tiptap JSON |
| 光标/选区 | 自定义绝对位置，编辑后易漂移 | Hocuspocus Awareness + Tiptap CollaborationCaret | 选择标准 awareness |
| Java 边界 | Java 同时做业务和编辑合并 | Java 负责身份、权限、知识业务、版本与数据库；Node 负责 Yjs 协议和转换 | 增加受控 Node sidecar |
| 多节点 | 进程内 room，无跨节点广播 | Hocuspocus Redis extension 同步 update/awareness | M6 引入 Redis 扩展 |
| 持久化 | JSON payload + active blocks | Yjs binary 为协同主存储；Tiptap JSON/blocks 为业务投影 | 二进制不可由 JSON 重建 |
| 离线 | 手工 pending snapshot | `y-indexeddb` + provider 重连补更新 | M6 有限离线队列 |
| 部署成本 | 单 Java 进程 | 增加 Node 22 sidecar、WS 代理、健康检查和备份对象 | 接受，按 M3-M6 分段交付 |
| 迁移成本 | 已运行但不可靠 | 需要 blocks -> Tiptap JSON -> initial Y.Doc、校验和与回滚 | M2 先完成确定性迁移 |

本地最小实验使用锁文件已解析的 Yjs 13.6.31：两个文档从同一 seed 分叉，分别追加 `-left` 和 `-right`，交换 update 并重复应用左侧 update；两端均得到 `base-left-right`，且完整 update 长度一致，证明该实验下并发收敛和重复应用幂等。项目当前未直接声明 `yjs` 依赖，普通 `import 'yjs'` 失败，因此 M3 不得依赖偶然的传递依赖，必须显式锁定 Yjs、Tiptap Collaboration 和 Hocuspocus provider 版本。

官方依据：

- Tiptap 推荐 WebSocket + Hocuspocus，并说明 Yjs 可合并乱序与离线修改：https://tiptap.dev/docs/hocuspocus/guides/collaborative-editing
- Hocuspocus 持久化要求保存原始 Yjs `Uint8Array`，禁止只保存 JSON 后重建：https://tiptap.dev/docs/hocuspocus/guides/persistence
- Hocuspocus Redis extension 用于多实例 update/awareness 同步，但不负责持久化：https://tiptap.dev/docs/hocuspocus/server/extensions/redis
- `onAuthenticate` 支持只读连接，适合映射 view/comment 与 edit/manage/owner：https://tiptap.dev/docs/hocuspocus/guides/authentication
- hooks/transformer 可从 Y.Doc 生成 Tiptap JSON 业务投影：https://tiptap.dev/docs/hocuspocus/server/hooks

### 冻结的服务边界

1. Spring Boot 保持唯一业务 API 和数据库 owner；权限、空间归属、对象 resolver、评论、版本、搜索、审计均不迁入 Node。
2. 新增独立 Hocuspocus Node 22 sidecar，只处理 Yjs WebSocket、awareness、binary load/store 和 Tiptap JSON 投影。
3. 浏览器先向 Java 获取短期、单文档、带权限等级的 collaboration ticket；Node `onAuthenticate` 向 Java 校验或验证 Java 签名，不信任客户端 workspaceId/permission。
4. room name 使用不可枚举的规范标识，逻辑形式为 `knowledge:{workspaceId}:{itemId}`；workspaceId 必须来自 ticket，而不是 URL 自报。
5. Node 不直接读取业务表。binary load/store 和投影提交通过受保护的 Java 内部 API 完成，Java 负责事务、审计和 schema 校验。
6. Redis 只承担多节点消息同步，不作为协同持久化事实来源；PostgreSQL 保存 binary snapshot/update log。

## 冻结合同

### 规范文档

- 一个知识内容对应一个 Y.Doc。
- `title` 使用独立 `Y.Text` 字段；正文使用 Tiptap Collaboration 的 `default` `Y.XmlFragment`。
- 正文逻辑格式是带 `schemaVersion` 的 Tiptap JSON；每个顶层可编辑 node 必须有稳定 UUID `blockId`。
- `blockId` 在创建时生成，移动、格式变化、父子调整、协同合并和版本恢复均不得按数组下标重配。
- `plainText`、Markdown、HTML、搜索文本和 relational blocks 都是从 canonical 文档生成的投影。
- 未知 node/mark 必须以可恢复占位结构保留原 payload，不得静默删除。

### 版本与持久化

- 产品 `versionNo` 只在明确自动检查点、命名版本或恢复时增长，不等同于每个 Yjs update。
- 协同更新使用单调 update sequence/数据库主键做持久化顺序；Yjs state vector 仅作为同步向量，不伪装成产品版本号。
- 持久化至少包含 latest binary snapshot、snapshot state vector、增量 update、actor/client、时间和 schemaVersion。
- `knowledge_content_blocks`、搜索和对象关系投影必须携带产生它们的协同 state vector/sequence；过期投影不得覆盖更新状态。
- 版本快照保存 canonical Tiptap JSON、schemaVersion 和对应 state vector；恢复以新 Yjs transaction 产生新产品版本，不改写旧历史。

### 协同更新与权限

- 客户端只发送标准 Yjs update/awareness，不发送整篇 Markdown 替换正文。
- view/comment 连接为 read-only；edit/manage/owner 可写。权限撤销后当前连接必须在 token 同步或服务端通知时转只读/断开。
- awareness 不持久化为正文历史，不包含正文、敏感权限解释或不可见对象标题。
- 重复、乱序和断线重放必须幂等；多节点通过 Redis 传播 update/awareness，持久化仍由数据库扩展/Java 内部 API 完成。

### 错误语义

| Code | 语义 | 客户端行为 |
| --- | --- | --- |
| `COLLAB_AUTH_EXPIRED` | ticket 过期 | 刷新 ticket；失败则转只读并保留本地更新 |
| `COLLAB_FORBIDDEN` | 无查看权限 | 关闭文档，不展示标题/正文/路径 |
| `COLLAB_READ_ONLY` | 可读不可写或权限被撤销 | 停止发送，提示导出本地未提交内容 |
| `COLLAB_SCHEMA_UNSUPPORTED` | 客户端/服务端 schema 不兼容 | 只读降级，不转换覆盖原文档 |
| `COLLAB_PERSISTENCE_UNAVAILABLE` | binary 无法持久化 | 保持本地队列，显示未保存，不宣称 synced |
| `COLLAB_PROJECTION_STALE` | 业务投影落后于 Yjs state | 重建投影，不用旧 blocks 回写 Y.Doc |
| `COLLAB_DOCUMENT_CORRUPT` | binary/JSON 校验失败 | 隔离文档，使用最近有效快照恢复并保留原始证据 |

## 兼容退出计划

| 阶段 | 动作 | 进入条件 | 回滚/退出条件 |
| --- | --- | --- | --- |
| 观测 | 统计兼容编辑器打开、`PATCH item` 正文写入、Markdown fallback 使用、转换损失 | M1 报告完成 | 指标可按 item/客户端版本定位，不记录正文 |
| 迁移 | blocks -> canonical Tiptap JSON -> initial Y.Doc；生成校验和、失败清单和原始快照 | M2 schema/转换器/迁移测试通过 | 任一损坏数据不覆盖原 blocks；可按 item 回滚 |
| 默认切流 | 页面只加载规范块编辑器；正式保存只走 Yjs/规范 blocks | M3 单编辑器 E2E 和 M4 保存状态机通过 | feature flag 可在限定窗口回到只读旧内容，不恢复双写 |
| 回滚窗口 | 保留旧 blocks 和只读 Markdown 导出，不允许用户写兼容路径 | 线上观测期无不可恢复丢失 | 回滚只能停用新写入并恢复最近有效快照 |
| 删除 | 删除模式开关、compat editor、Markdown fallback、正式 `PATCH item` 字符串写入和快照合并代码 | 零兼容写入窗口、迁移失败清零、备份恢复演练通过 | 删除后不再恢复双编辑器；导入导出 API继续保留 |

明确退出对象：`blockEditorEnabled`/`colla.kb.block-editor.mode`、页面模式条、`KnowledgeContentEditor` 的 `blocksToMarkdown -> markdownToBlockDrafts` 循环、编辑区“Markdown 兼容内容”、兼容结构化块面板、`useKnowledgeContentCollaboration` 的 `snapshot-v1` 和 `mergeTextSnapshot`、正式编辑调用 `saveKnowledgeContent(content)`。

## 测试分层

| 层级 | 频率 | 数据/环境 | 负责行为 | 入口 |
| --- | --- | --- | --- | --- |
| L1 schema/adapter 单测 | 每次相关改动 | 内存固定样例 | node/mark、稳定 blockId、JSON/投影、损坏输入 | M2 新增前端/后端目标单测 |
| L2 模块集成 | 每里程碑 | Testcontainers PostgreSQL | 迁移、版本、投影、权限、binary/update 持久化 | `KnowledgeContentApiContractTests` 等目标测试 |
| L3 编辑器组件/静态检查 | 每次前端改动 | 本地 DOM/TypeScript | 不重建 editor、菜单、表格、粘贴、IME 状态 | frontend lint/typecheck + 后续组件测试 |
| L4 单用户 real smoke | 每个 UI 里程碑 | 私有并归档夹具 | 创建、导航、编辑、保存、对象降级、恢复 | `kb-product-m1-editor-audit.spec.ts` 及范围 spec |
| L5 双用户 real collaboration | M5/M6 与相关回归 | 两个独立 browser context、真实 WS | 并发、光标、只读、撤权、断线重连 | 扩展 `knowledge-collaboration-permissions.spec.ts` |
| L6 route-final | M12 | 隔离 disposable 环境、双应用节点、Redis | 全流程、故障注入、备份恢复、权限与无泄漏 | `COLLA_E2E_SUITE=route-final` + 双节点编排 |

日常里程碑不得用完整 `mvn test` 代替目标验证；M12 才执行完整后端、Flyway 空库/存量、完整浏览器、双节点和恢复演练。

## 风险与 M2 准入

| Risk | Severity | Mitigation/owner milestone | M2 admission effect |
| --- | --- | --- | --- |
| 连续输入字符丢失/选择漂移 | P0 编辑体验 | M3 状态机直接适配；M4 保存；M9 交互矩阵 | 不阻止 M2 模型工作，但禁止在修复前宣称编辑器可用 |
| Markdown 往返丢失表格/对象/mark/ID | P0 数据完整性 | M2 canonical schema/迁移，M3 删除编辑循环 | M2 必须优先建立黄金样例和失败不覆盖规则 |
| `richContent` 与 `content` 双主风险 | P0 数据一致性 | M2 指定唯一结构来源，投影带来源版本 | 未冻结唯一来源时不得写迁移 |
| `anchorId=block-{sortOrder}` 不稳定 | P1 评论/定位 | M2 stable blockId，M10 映射评论位置 | M2 schema 必须包含稳定 UUID |
| Hocuspocus 增加 Node 22 运行单元 | P1 运维复杂度 | M3 最小 sidecar，M6 健康/双节点，M12 发布回退 | 接受；未完成部署/备份合同前不得生产启用 |
| Node 越过 Java 直接读业务表 | P0 安全/边界 | collaboration ticket + Java internal API | 明确禁止，M3 评审必须检查 |
| Yjs binary 被 JSON 重建导致重复内容 | P0 数据损坏 | 原样持久化 Uint8Array，JSON 仅投影 | M4 持久化测试必须覆盖重连和重复加载 |
| 有效新 Base 仍不可用 | P1 主流程 | M7 原子创建/挂载、resolver/事务/缓存检查 | 不阻止 M2；M7 完成前对象入口不算闭环 |
| 当前表格工具栏缺少可访问名称 | P2 可访问性 | M9/M11 添加 role/name 和键盘测试 | 不阻止 M2 |

M2 准入决定：**Go，带冻结条件**。

准入条件已经满足：唯一知识模型不变；canonical Tiptap JSON、稳定 blockId、派生字段、Yjs binary、Java/Node 边界、兼容退出顺序和错误语义均已明确。M2 不得实现 Hocuspocus 生产服务或删除兼容路径；只实现规范 schema、确定性转换、迁移预览/回滚和模型测试。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M1-T01 | 调用图明确全部读写版本协同路径 | 本报告三条调用链；对应 page/editor/adapter/service/repository/migration | `rg` + 完整相关函数读取；字段与迁移交叉核对 | N/A：静态架构审计 | Done |
| KB-PRODUCT-M1-T02 | 五字段有主/派生/兼容和退出结论 | 本报告内容字段事实表 | 前端类型、Java records、JDBC、V043-V045 一致性核对 | N/A：字段审计 | Done |
| KB-PRODUCT-M1-T03 | 五类问题均有步骤、预期、实际和证据 | 本报告编辑器问题复核 | M1 spec 首次失败与最终通过均保留命令证据 | real isolated：1 passed；确认当前缺陷和已修复项 | Done |
| KB-PRODUCT-M1-T04 | 覆盖九类真实用户任务 | 本报告真实用户任务矩阵 | 现有 knowledge core/object/collaboration specs 入口核对 | M1 real smoke 覆盖打开、编辑、表格、对象降级 | Done |
| KB-PRODUCT-M1-T05 | 技术方案覆盖并发、边界、部署、持久化、测试、迁移 | 本报告技术 spike 与服务边界 | Yjs 13.6.31 分叉更新收敛并重复应用幂等 | N/A：算法/架构 spike | Done |
| KB-PRODUCT-M1-T06 | 核心字段和事件无后续再定项 | 本报告冻结合同 | 与 Tiptap/Hocuspocus 官方协议能力交叉核对 | N/A：合同冻结 | Done |
| KB-PRODUCT-M1-T07 | 退出含观测、迁移、切流、回滚和删除条件 | 本报告兼容退出计划 | 当前 legacy 引用和 API 写路径清单核对 | N/A：退出计划 | Done |
| KB-PRODUCT-M1-T08 | 日常、双用户和 route-final 分层明确 | 本报告六层测试表；新增 M1 real smoke | spec lint、TypeScript、real Playwright | real isolated：浏览器夹具创建后归档 | Done |
| KB-PRODUCT-M1-T09 | 风险、不做范围和 M2 决策明确 | 本报告风险表和 Go 条件 | 工作循环 stage gate | N/A：架构准入评审 | Done |

## Code Changes

- Backend: 无。
- Frontend product code: 无；未把审计中发现的缺陷混入 M1 修复。
- Browser tests: 新增 `web/e2e/kb-product-m1-editor-audit.spec.ts`，使用真实 API 创建私有夹具并在 finally 归档。
- Database: 无；未新增/修改 Flyway。
- Scripts: 无。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/90-reports/kb-product-m1-execution-report.md` | 新建并完成 | 固化 T01-T09 的事实、合同、证据和风险 |
| `docs/02-roadmap/current-roadmap.md` | 更新 M1 状态 | 将 T01-T09 标记完成并指向本报告，下一入口切到 M2 |

## Validation

- Backend tests: `mvn -f server/pom.xml -Dtest=KnowledgeContentApiContractTests test`，2/2 通过；finish stage 重跑同一目标测试。
- Frontend lint: `pnpm --dir web exec eslint e2e/kb-product-m1-editor-audit.spec.ts`，通过。
- Frontend typecheck: `pnpm --dir web exec tsc --noEmit --pretty false`，通过。
- Browser smoke: M1 real isolated Playwright，`1 passed`；真实 API、真实页面和真实 PostgreSQL，夹具已归档。
- Yjs spike: 两个并发分支收敛为 `base-left-right`，重复 update 应用幂等。
- Local quality gate: checkpoint light 已通过 toolchain 与完整 frontend lint；finish stage 记录见 `.local-reports/`。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None for M1 contract/audit scope | non-blocking | 产品实现风险已完整登记到 M2-M11，不冒充本轮实现完成 |

## Next Steps

1. 从 `KB-PRODUCT-M2-T01` 开始定义 canonical Tiptap block schema 和 node/mark 白名单。
2. 先建立黄金样例、损坏输入、未知节点和稳定 blockId 测试，再写迁移代码。
3. M2 只完成结构模型与迁移闭环；Hocuspocus sidecar、单编辑器切流和兼容删除分别留在 M3-M6。
