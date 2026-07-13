# M14-M16 平台化底座执行记录

## 范围

M14 到 M16 在业务实现前先补齐三个底座能力：

- M14：统一权限决策层一期。
- M15：统一实时事件信封一期。
- M16：文档结构化块模型一期。

## M14 统一权限决策层一期

已落地：

- 新增 `PermissionDecision`，统一表达 workspace、对象类型、对象 ID、动作、是否允许、拒绝原因。
- 新增 `PermissionDecisionService`，沉淀通用权限等级判断：`view < edit < manage`。
- 文档 ACL 的等级判断已接入 `PermissionDecisionService`，避免文档模块继续自带私有权限等级实现。

保留边界：

- 当前没有强行改造所有模块权限入口，避免一次性扩大回归面。
- 后续项目、表格、文件、审批可以逐步接入同一决策服务。

## M15 统一实时事件信封一期

已落地：

- WebSocket 事件信封保留原有 `type/eventId/serverTime/payload`。
- 新增可选字段：`workspaceId/objectType/objectId`。
- IM 的消息、会话、未读事件已开始携带对象元数据。
- 前端 WebSocket 事件类型已同步扩展，现有监听逻辑无需变更。

设计约束：

- 旧 payload 不破坏，兼容当前 IM 页面。
- 新字段面向后续通知中心、跨端同步、对象订阅、增量刷新。

## M16 文档结构化块模型一期

已落地：

- 新增 `document_blocks` 表。
- 文档详情新增 `blocks` 返回字段。
- 文档创建、保存、版本恢复会根据 Markdown 内容同步 blocks。
- 新增 `GET /api/docs/{documentId}/blocks`。
- 新增 `PATCH /api/docs/{documentId}/blocks`，支持按 block 保存并生成新版本。
- 前端文档详情展示结构化块列表，保留现有 Markdown 编辑体验。

一期取舍：

- 当前不是完整多人协同编辑器。
- 当前 block 保存采用整篇替换策略，便于先稳定数据模型和 API。
- 后续可在此基础上升级为块级操作、增量事件、冲突合并或 CRDT/Yjs 协同层。

## 测试覆盖

- 文档创建自动生成 blocks。
- 文档保存后 blocks 同步更新。
- blocks 独立读取。
- blocks 独立保存并生成新版本。
- WebSocket 信封保持兼容字段。
