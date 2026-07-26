---
title: 事件副作用与 Handler 矩阵
status: current
updated_at: 2026-07-27
stage: PROJECT-PLATFORM-S09
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
| 规范工作项创建、更新、参与者、归档与恢复 | `project_work_items`、字段投影/参与者/活动 + `work_item.changed` | M2 当前不生成 realtime signal | N/A | work item version；命令 request id 参与 outbox 去重；活动按实例独立序号 | work item/space/type/version id；`GET /api/project-spaces/{spaceId}/work-items/{id}` | 从未直接发送 |
| 工作项兼容读取与 shadow compare | 只读 legacy/canonical + `project_work_item_shadow_samples` | 不发送 realtime | N/A | source identity + sampledAt/id；只记录 hash/outcome/latency | 旧链接经公共 resolver 返回 canonical location；无业务 payload | 从未直接发送 |
| 工作项 cutover/kill switch | `project_work_item_cutovers` + `work_item.cutover.changed` 审计 | 不发送 realtime | N/A | scope version 乐观并发；workspace/space 唯一 | 管理 API 返回 stage、write/kill switch、version；不返回对象内容 | 从未直接发送 |
| 角色、角色分配、资源授权变化 | permission owner 表 + `permission.security.changed` | `permission.invalidated` | workspace | changed permission object sequence | role/assignment id，或授权资源的 type/id；payload 指定 `/api/admin/...` 或 `/api/resource-permissions/...` | 已移除 |
| 成员状态、部门和用户组变化 | identity owner 表 + `identity.security.changed` | `identity.invalidated` | workspace | changed identity object sequence | user/department/group id；payload 指定 `/api/admin/...` | 已移除 |
| 工作项配置发布 | `project_work_item_type_versions` + `work_item_configuration.published` | 当前不生成 realtime signal | N/A | published version id；类型行锁内单调 version number | version/type/space id；`GET /api/project-spaces/{spaceId}/work-item-types/{typeId}/configuration/versions` | 从未直接发送 |
| 工作项配置模板创建/安装/升级/解绑 | `project_work_item_configuration_templates`、installation/upgrade history + `work_item_configuration.template_*` | 当前不生成 realtime signal | N/A | template/installation id；命令回执与 aggregate version | template/type/space id、来源版本/hash 与冲突摘要；配置模板 API | 从未直接发送 |
| S08 状态流定义与 M2 forward 运行时 | configuration snapshot + `project_work_item_current_states`/command receipt/history/activity/audit + `workflow.action_executed`/`workflow.state_changed` | M2 不生成 realtime signal | N/A | WorkItem expected version + current-state aggregate version + request id/hash；列表批量投影 | 用户 workflow/current/history/action API；事件仅含绑定、semantic key 和版本 | 从未直接发送 |
| S09 节点流 M2 运行时 | configuration snapshot + node instance/token/task/vote/join/arrival/receipt/history + activity/audit + `node_workflow.changed` | M2 不生成 realtime signal | N/A | WorkItem expected version + instance aggregate version + request id/hash；token/task/join 行锁与 CAS | 用户 node-workflow presentation/history/start/task-action API；事件仅含绑定、operation/node、状态和版本 | 从未直接发送 |
| S09 节点流 M4 恢复/升级/backfill | 绑定 snapshot 的 recovery/compensation 定义 + node instance/token/task/join/compensation/backfill ledger + WorkItem binding/projection/history/activity/audit/receipt + `node_workflow.changed` | M4 不生成 realtime signal | N/A | owner/admin + 精确确认/原因 hash + WorkItem/instance expected version + request/manifest hash；backfill unit 独立事务 | 用户 recovery/upgrade/compensation-resume 与空间 backfill/resume/verify API；公共事件仍只含最小绑定/operation/node/version | 从未直接发送 |

配置发布事件与不可变版本、current pointer、草稿关闭、审计和 publication receipt 同事务提交。payload 只包含 request、space/type/version、schema/hash/source draft 与 breaking 摘要，不复制完整 snapshot、隐藏字段或访问策略正文。

S07-M2 的 `project.contract.WorkItemChangedEvent` 与实例、类型化投影、参与者命令、活动、命令回执、平台链接和审计在对应命令事务中同成同败。payload 只包含 space/type definition/type version/config hash、实例 version、status 和 mutation，不包含标题、动态字段值、参与者列表、访问策略或隐藏字段。当前未注册搜索、通知或 realtime Handler；后续消费者只能依赖该公共最小合同并经 resolver/API 校准，不能跨模块读取命令回执、投影或活动私表。

S07-M3 的 shadow compare 是观测副作用，不是业务双写：主读结果先按调用身份授权，后台只比较安全指纹并追加 outcome/latency。cutover 变更写审计但不伪造工作项领域事件；旧写关闭不会因 kill switch 自动恢复。

S07-M4 的迁移在单个 legacy project 单元事务中创建规范 WorkItem、平台对象链接、参与者、评论、附件、活动和来源 provenance；任一写失败时该单元全部回滚。迁移不伪造用户命令 receipt，也不对每条历史事实重发 `work_item.changed`，避免下游把历史导入误判为在线变更。plan、pause、execute、verify、convergence、rollback 和 post-cutover compensation-required 均写治理审计；审计只含 batch、计数、状态和稳定错误码，不复制 manifest 业务正文。

S08-M1 只把声明式状态流定义纳入配置 snapshot，并预建运行事实 schema；保存、校验、发布与 rollback 仍只产生既有配置事件。S08-M2 按动作 request id/hash 激活 `forward`；S08-M3 扩展 `return/reopen/terminate/restore/correction`、binding upgrade 和 explicit backfill。current state、WorkItem version/binding、field projection、history、activity、audit、两个 outbox event 与 completed receipt/单元状态在各自事务中同成同败。公共 payload 固定 `eventSchemaVersion=1`，只含 space/type/version/hash、action/from/to、WorkItem/aggregate version 和 decision reference，不含标题、字段值、guard、参与者、原因正文或策略正文。

S08-M4 的配置器、成员动作、correction 和 backfill UI 不增加浏览器推算事件或旁路副作用；所有事实仍由上述服务端事务产生。409/422/timeout/offline 只保留 caller-stable request 与用户输入，恢复后重新读取 current/history，不在客户端补写 history/activity/outbox。S09 若复用公共 envelope 必须使用新的明确 event type/version，禁止读取 S08 私表补全 node token、分支或会签事实。

S09-M2 注册 `project.contract.WorkItemNodeWorkflowEvent`，固定 `node_workflow.changed` v1。节点命令成功时，WorkItem/instance version、token/task/vote/join、不可变 history、WorkItem activity、audit、completed receipt 与 outbox 在同一事务中成败；精确 request ID/hash 重放不重复追加。payload 只含 space/type definition/type version/config hash、operation、node key、WorkItem/instance version、instance status 和 decision reference，不含候选角色、条件、quorum、父 token、split/join/correlation、字段值或原因正文。当前不注册 realtime/search/notification Handler；消费者若后续接入只能经 `work_item` resolver/用户 API 校准，不能读取节点私表。

S09-M4 的 return/jump/terminate/correct 与 binding upgrade 继续复用上述 v1 event，不扩充私有恢复原因、映射、补偿步骤或 backfill manifest。成功恢复/升级时 WorkItem、instance、旧开放 work 的关闭、新 token、history/activity/audit/receipt/outbox 同事务成败；outbox 故障回滚全部运行事实。backfill 以 unit 独立事务提交同一原子闭包，batch/failure/verify 只属于 project 私有运维账本；续跑不能伪造公共事件或直接把 failed 标为 completed。

S08-M3 注册 `project.workflow.consumer-contract` v1，订阅 `workflow.action_executed/state_changed/initialized/binding_changed` v1。该 Handler 不读取 project 状态、命令、history 或 backfill 私表，也不直接创建通知正文或搜索索引；它只验证公共最小 payload，使 delivery receipt/replay 有稳定消费者身份，并把未知 payload schema 分类为 permanent/dead-letter。通知/搜索若后续选择产品投影，只能消费同一公共合同并经 `work_item` resolver/用户 API 重新授权校准，不能通过跨模块 SQL 补全状态事实。

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

## S07 WorkItem 事件边界

- 规范实例创建、字段更新、参与者变化、归档和恢复在业务事务内追加 `work_item.changed` v1；载荷只含 canonical identity、空间/类型/绑定版本、实例版本、状态和 mutation，不复制字段值、评论、附件或访问策略。
- 评论与附件属于 WorkItem 聚合的用户协作子资源；其可见内容通过用户 API 重新鉴权，事件和 outbox 只携带最小对象引用。
- legacy 解析、shadow compare、迁移 plan/verify/rollback 是治理事实，不伪装成用户业务事件。迁移成功后的 canonical 对象仍使用唯一 `work_item` identity。
- hidden 字段不进入事件、活动差异、搜索、错误或浏览器投影；消费者不得读取 project 私表补全事件。

## S09 Node task 事件边界

- claim/delegate/transfer/complete/submit/vote/withdraw 成功后继续在业务事务内追加 `node_workflow.changed` v1；字段 patch、artifact、候选集合、条件、quorum 和 token correlation 不复制到事件。
- 到期扫描使用 `FOR UPDATE SKIP LOCKED` 和 `timed_out_at is null` 产生单一 timeout 事实，并在同一事务追加 `node_task.lifecycle` v1。稳定幂等键为 `node-task:timed-out:{taskId}`。
- `project.node-task.consumer-contract` 只验证公共 v1 envelope，依赖 durable delivery receipt 去重；未知 schema 永久失败并由通用 Worker 进入 dead letter。通知/搜索正文若后续启用，必须经 `work_item` resolver/用户 API 重新授权，不得读取 node task/artifact 私表。

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
