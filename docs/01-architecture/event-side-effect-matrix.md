---
title: 事件副作用与 Handler 矩阵
status: current
updated_at: 2026-07-24
stage: PLATFORM-SCALE-S03
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

## 3. Notification 与 realtime 信号

1. 业务命令在同一事务追加 `notification.created`，payload 包含 recipient、notification type、展示内容、目标标识、web path 和业务 dedupe key。
2. Notification Handler 先检查接收者偏好，再以 dedupe key 创建通知；重复 delivery 返回既有事实，不重复通知。
3. 仅当通知首次创建时，Handler 在同一事务追加最小化 `realtime.signal.requested`。信号不复制通知标题或正文。
4. Realtime Handler 以 source event 唯一键写入 durable pending signal，保存 recipient、signal type、对象、版本和 `/api/notifications` 校准路径。
5. S03 不承诺 transport 已发送。S04 才消费 pending signal 并接入跨节点 fanout；无 transport 时通知和其他业务事实仍完成。

## 4. 显式维护边界

- 批量搜索重建：`POST /api/admin/search-governance/reindex/batches`，必须提供对象类型、可选 cursor、1-250 limit 和理由。
- 兼容全量重建：`POST /api/admin/search-governance/reindex`，仅管理员显式调用并写审计。
- Worker、普通搜索和 realtime Handler 均不得调用全量重建。
- 历史事件补投、dead-letter replay 和 realtime transport 恢复不得通过直接改表执行。
