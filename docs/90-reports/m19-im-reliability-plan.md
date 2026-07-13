# M19 IM 多端可靠性与体验收敛

## 目标

让 IM 成为平台稳定核心入口，优先解决消息发送可靠性、断线恢复、多端状态一致性和核心操作回归。

## 任务拆解

| 任务 | 交付内容 | 验收标准 |
| --- | --- | --- |
| M19-T01 | 梳理 IM 当前能力、缺口、风险清单 | 明确发送、重连、已读、成员、消息操作、多端契约的风险点 |
| M19-T02 | 消息发送状态 | 前端显示发送中、失败、可重试；后端用 clientMessageId 保证幂等 |
| M19-T03 | WebSocket 重连与断线恢复 | 重连后主动刷新会话和消息；服务端提供 messageSeq 同步游标 |
| M19-T04 | 多端会话排序、未读、已读一致性 | 已读游标必须属于当前会话；会话更新和未读事件可驱动多端刷新 |
| M19-T05 | 成员变更实时同步 | 成员增删、退出、关闭会话继续通过 conversation.updated 推送 |
| M19-T06 | 消息操作回归 | 编辑、撤回、置顶、取消置顶、表情反应使用准确 WebSocket 事件类型 |
| M19-T07 | 多端 API 契约整理 | 消息响应包含 messageSeq、clientMessageId；前端发送可传 clientMessageId |
| M19-T08 | 自动化测试与质量门禁 | 覆盖发送幂等、afterSeq 补拉、非法已读游标；通过 pnpm verify |

## 当前落地范围

- 后端消息响应新增 `messageSeq`。
- `GET /api/conversations/{conversationId}/messages` 支持 `afterSeq` 补拉。
- `POST /api/conversations/{conversationId}/messages` 对同一发送人、同一会话、同一 `clientMessageId` 幂等返回已有消息。
- 已读接口拒绝使用其他会话的消息作为 read marker。
- 消息编辑、撤回、置顶、取消置顶、表情反应推送准确事件类型。
- 前端消息发送增加本地 `sending` / `failed` 状态，失败消息可重试。
- WebSocket 重新连接成功后主动刷新 IM 会话和消息。

## 后续关注

- 移动端和桌面端接入时应复用 `clientMessageId` 与 `messageSeq` 契约。
- 后续如做离线消息队列，应以 `messageSeq` 为补偿游标，不依赖客户端时间。
- WebSocket 事件只负责驱动刷新，REST 历史数据仍是最终事实来源。
