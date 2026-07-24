---
title: 事件副作用与 Handler 矩阵
status: current
updated_at: 2026-07-24
stage: PLATFORM-SCALE-S04
---

# 事件副作用与 Handler 矩阵

## 1. 固定合同

| Handler | 订阅 | 幂等键 | 顺序 | 失败分类 | 最终事实与校准入口 |
| --- | --- | --- | --- | --- | --- |
| `notification.projection` v1 | `notification.created` v1 | delivery receipt + notification `dedupeKey` | 同 aggregate sequence | payload 缺失/UUID 非法为 permanent；数据库暂态错误为 transient/unknown | `notifications`；`GET /api/notifications` |
| `search.projection` v1 | 下表列出的对象事件 v1 | delivery receipt + `(workspace, objectType, objectId)` 版本水位 | 同 aggregate sequence | 不支持对象为 permanent；数据库暂态错误为 transient/unknown | `search_index_entries` + `search_projection_versions`；`GET /api/search` |
| `realtime.signal` v1 | `realtime.signal.requested` v1 | delivery receipt + `source_event_id` unique | 同 source aggregate sequence | 缺失字段、非法 UUID/version、非 `/api/` 校准路径为 permanent | `realtime_signals`；payload 中的 `calibrationPath` |

每个 Handler 独立持有 delivery、attempt、receipt 和 dead-letter 状态。一个 Handler 失败不回滚另一个 Handler 已完成的副作用，也不会重新执行已有 receipt 的 Handler。

## 2. Search 订阅矩阵

| 对象类型 | Upsert 事件 v1 | Delete 事件 v1 | 投影 payload 边界 |
| --- | --- | --- | --- |
| `issue` | `issue.created`、`issue.updated`、`issue.assigned`、`issue.verified` | 当前无物理删除事件 | 只使用 envelope 的 workspace、aggregate id、aggregate sequence 和操作；标题及 ACL 从 owner 表读取 |
| `knowledge_content` | `knowledge.content.created`、`updated`、`blocks.updated`、`knowledge_metadata.updated`、`moved`、`restored`、`copied`、`markdown.imported`、`html.imported`、`version.restored`、`comment.added`、`comment.reply.added`、`comment.resolved`、`comment.reopened` | `knowledge.content.archived` | 同上；归档删除索引，恢复重新从 owner 表投影 |
| `base` | `base.created` | 当前无归档事件 | 同上 |
| `base_table` | `base.table.created` | 当前无归档事件 | 同上 |
| `base_record` | `base.record.created`、`base.record.updated` | `base.record.deleted` | 同上；记录值仅在投影事务中从 Base owner 表读取 |
| `message` | `message.created`、`message.edited` | `message.revoked` | 同上；撤回后不保留消息正文 |

Search 事件不携带 ACL 快照或供无权消费者直接展示的标题。查询时仍以当前用户和 owner 模块权限为准。普通搜索不触发全 workspace 刷新。

## 3. S04 实时业务副作用矩阵

所有表内业务动作都先提交 PostgreSQL 事实和 durable domain event，再由独立 Handler 生成
`realtime.signal.requested`。业务事务不读取 Gateway session，也不直接调用
`WebSocketMessageSender` 或 `WebSocketSessionRegistry`。

| 业务域与动作 | durable fact / source event | signal type v1 | audience | sequence | 最小对象与 REST 校准 | 旧直接发送 |
| --- | --- | --- | --- | --- | --- | --- |
| 通知首次投影 | `notifications` + `notification.realtime.changed` | `notification.created`、`notification.unread.changed` | recipient user | 通知对象、recipient 未读水位 | notification id；`GET /api/notifications`、`GET /api/notifications/unread-count` | 已移除 |
| 单条通知已读 | `notifications.read_at` + `notification.realtime.changed` | `notification.read`、`notification.unread.changed` | recipient user | 通知对象、recipient 未读水位 | notification id；同上 | 已移除 |
| 批量/全部已读 | `notifications.read_at` + `notification.realtime.changed` | `notification.unread.changed` | recipient user | recipient 未读水位 | recipient id；`GET /api/notifications/unread-count` | 已移除 |
| IM 消息创建、编辑、撤回、置顶、反应 | IM owner 表 + `im.realtime.changed` | `message.created`、`message.edited`、`message.revoked`、`message.pinned`、`message.unpinned`、`message.reaction.toggled` | conversation recipient users | `im:{recipientId}` audience sequence | message/conversation id；`GET /api/conversations/{id}/messages?afterSeq={n}` | 已移除 |
| IM 会话成员、已读、未读变化 | IM owner 表 + `im.realtime.changed` | `conversation.updated`、`conversation.read`、`unread.changed` | conversation recipient users | `im:{recipientId}` audience sequence | conversation id；`GET /api/conversations` | 已移除 |
| 项目、成员和状态变化 | project owner 表 + `project.changed` | `project.changed` 或撤权时 `project.invalidated` | current project members + affected user | project object sequence | project id；`GET /api/projects/{id}` | 已移除 |
| 工作项和评论变化 | issue owner 表 + `issue.changed` | `issue.changed` 或撤权时 `issue.invalidated` | current project members + affected user | issue object sequence | issue id；`GET /api/issues/{id}` | 已移除 |
| 项目空间与成员变化 | project-space owner 表 + `project_space.changed` | `project_space.changed` 或撤权时 `project_space.invalidated` | current space members + affected user | project-space object sequence | space id；`GET /api/project-spaces/{id}` | 已移除 |
| 角色、角色分配、资源授权变化 | permission owner 表 + `permission.security.changed` | `permission.invalidated` | workspace | changed permission object sequence | role/assignment id，或授权资源的 type/id；payload 指定 `/api/admin/...` 或 `/api/resource-permissions/...` | 已移除 |
| 成员状态、部门和用户组变化 | identity owner 表 + `identity.security.changed` | `identity.invalidated` | workspace | changed identity object sequence | user/department/group id；payload 指定 `/api/admin/...` | 已移除 |

workspace audience 只表示“该 workspace 的已连接客户端需要丢弃相关缓存并重新鉴权”，不携带
角色、成员、ACL、标题或正文。被撤权用户也只能收到对象定位和失效提示，不能从 signal 恢复已
无权访问的内容。

## 4. Signal 合同与顺序

- envelope 固定为 `envelopeVersion=1`、`signalVersion=1`，并包含 workspace、audience、
  object、sequence、occurredAt、correlationId 和本地 `/api/` 校准路径。
- notification 对象变化使用 object sequence，未读数使用 recipient audience sequence；IM
  使用 recipient audience sequence，保证同一用户跨会话变化可比较。
- project、permission、identity 使用 source aggregate sequence。客户端只能比较同一
  `sequenceScope + sequenceKey`，不能跨 key 排序，也不能用时间戳猜测缺失事件。
- payload 只允许安全定位/状态字段；`title`、`body`、`content`、`acl`、`members`、
  token/secret/password 等键由公共合同拒绝。
- 重复 domain delivery 由 receipt 与 outbox idempotency key 截断；重复 transport 由
  signal id 与 sequence 截断。Redis 失败只留下可重试 transport，不回滚业务事实。
- 未知 envelope/signal version、非法 payload、旧 sequence 或 sequence gap 均不得直接修改
 业务缓存；客户端应忽略内容并执行对应 REST 校准。

## 5. 兼容窗口与退出开关

- `COLLA_REALTIME_LEGACY_FRAMES_ENABLED=true` 仅在 S04 M2-M3 升级窗口保留 v0
  `WebSocketEventPayload.of(...)`；它不是事实源，也不允许业务模块重新依赖 sender。
- legacy frame 发出和被阻止分别记录
  `colla.realtime.websocket.legacy.frame{outcome=emitted|blocked}`，不得以 user/workspace
  作为指标标签。
- M4 关闭旧 Spring 知识协同入口时先演练 `false`；M5 route-final 必须证明业务实时流全部
  使用 v1 envelope，且关闭 legacy 不影响 IM、通知、项目、权限或身份校准。
- 架构门禁禁止业务模块依赖 `WebSocketMessageSender`/
  `WebSocketSessionRegistry`。兼容实现只允许存在于 shared transport 边界。

### 5.1 客户端消费与校准

- 浏览器只由应用级 `RealtimeProvider` 解析 v1 envelope；业务页面不再各自建立
  WebSocket 或解析原始 JSON。
- watermark 使用服务端声明的 `sequenceScope + sequenceKey`，event id 另做有界去重；
  stale/duplicate 不触发重复副作用，gap 立即转为对应域 REST 校准。
- 首次 ready、重连、gap、未知/旧协议和窗口重新聚焦均以 REST 收敛。通知列表与未读数
  只有在两个请求都成功后原子替换；IM 使用 `afterSeq` 和最多 100 条分页继续拉取直到水位稳定。
- project、permission、identity 信号只选择固定客户端查询映射，不执行服务端传来的路径。
  权限收紧先丢弃受保护缓存，再复核当前资源；只有明确 401/403/404 才安全退出页面。

## 6. Notification 与 realtime 信号

1. 业务命令在同一事务追加 `notification.created`，payload 包含 recipient、notification type、展示内容、目标标识、web path 和业务 dedupe key。
2. Notification Handler 先检查接收者偏好，再以 dedupe key 创建通知；重复 delivery 返回既有事实，不重复通知。
3. 仅当通知首次创建时，Handler 在同一事务追加最小化 `realtime.signal.requested`。信号不复制通知标题或正文。
4. Realtime Handler 以 source event 唯一键写入 durable pending signal，保存 recipient、signal type、对象、版本和 `/api/notifications` 校准路径。
5. S04 Worker 消费 pending signal 并发布到 Redis；每个 Gateway 只向本地匹配 session
   fanout。无 transport 或 Redis 中断时，通知和其他业务事实仍完成并可通过 REST 校准。

## 7. 显式维护边界

- 批量搜索重建：`POST /api/admin/search-governance/reindex/batches`，必须提供对象类型、可选 cursor、1-250 limit 和理由。
- 兼容全量重建：`POST /api/admin/search-governance/reindex`，仅管理员显式调用并写审计。
- Worker、普通搜索和 realtime Handler 均不得调用全量重建。
- 历史事件补投、dead-letter replay 和 realtime transport 恢复不得通过直接改表执行。
