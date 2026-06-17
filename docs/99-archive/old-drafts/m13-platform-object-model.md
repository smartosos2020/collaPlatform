# M13 平台对象模型规范

## 1. 目标

平台对象模型用于把 IM、项目、文档、表格、审批、文件等模块统一成可引用、可预览、可跳转、可搜索的对象。

任何核心业务对象进入协作链路前，都必须接入平台对象模型。

## 2. 当前对象类型

| objectType | 名称 | Web 路径 | Deep link |
| --- | --- | --- | --- |
| `issue` | 事项 | `/issues/{id}` | `colla://issue/{id}` |
| `document` | 文档 | `/docs/{id}` | `colla://document/{id}` |
| `base` | 表格空间 | `/bases/{id}` | `colla://base/{id}` |
| `base_table` | 数据表 | `/bases/{baseId}/tables/{id}` | `colla://base_table/{id}` |
| `base_record` | 表格记录 | `/bases/{baseId}/tables/{tableId}/records/{id}` | `colla://base_record/{id}` |
| `message` | 消息 | `/im?conversationId={conversationId}&messageId={id}` | `colla://message/{id}` |
| `approval` | 审批 | `/approvals/{id}` | `colla://approval/{id}` |
| `file` | 文件 | `/files/{id}` | `colla://file/{id}` |

对象类型注册接口：

```http
GET /api/platform/object-types
```

对象摘要接口：

```http
GET /api/platform/objects/{type}/{id}/summary
```

链接解析接口：

```http
POST /api/platform/links/resolve
```

## 3. 摘要结构

所有对象摘要统一返回 `PlatformObjectSummary`：

| 字段 | 说明 |
| --- | --- |
| `objectType` | 对象类型 |
| `objectId` | 对象 ID |
| `accessState` | 访问状态 |
| `title` | 卡片主标题 |
| `subtitle` | 卡片副标题 |
| `status` | 业务状态 |
| `webPath` | Web 跳转路径 |
| `deepLink` | 多端 deep link |
| `metadata` | 模块特有但非敏感的补充字段 |

`accessState` 可选值：

| 值 | 语义 |
| --- | --- |
| `available` | 当前用户可访问 |
| `forbidden` | 对象存在，但当前用户无权限 |
| `deleted` | 对象已删除或已撤回 |
| `not_found` | 对象不存在 |
| `invalid` | 对象类型或链接格式无效 |

无权限、已删除、未找到、无效链接都不得泄露敏感标题、正文、负责人、状态等业务字段。

## 4. 新对象接入要求

新增对象类型必须完成以下事项：

1. 在 Flyway 中写入 `object_type_rules`。
2. 在业务创建或更新时调用 `PlatformObjectRepository.upsertObjectLink(...)`。
3. 实现 `PlatformObjectResolver`。
4. resolver 必须做权限判断，不允许只依赖 `object_links` 快照。
5. resolver 对 403 返回 `forbidden`，对 404 返回 `not_found`。
6. 前端卡片必须使用 `ObjectSummaryCard` 或 `InternalLinkCard`。
7. 搜索结果、通知、IM 链接卡片必须复用同一套摘要语义。
8. 增加平台对象集成测试，覆盖可访问、无权限、链接解析和导航路径。

## 5. 链接解析规则

支持：

- Web path，例如 `/issues/{id}`、`/docs/{id}`、`/im?conversationId={conversationId}&messageId={id}`。
- 完整本地 URL，例如 `http://127.0.0.1:5173/docs/{id}`。
- Deep link，例如 `colla://message/{id}`。

完整 URL 解析时保留 query string，避免 `/im?...messageId=...` 丢失消息定位信息。

## 6. 当前 M13 交付

本轮 M13 已完成：

- 增加 `message` 对象类型。
- 发送 IM 消息时登记 `object_links`。
- 增加 `MessagePlatformObjectResolver`。
- 内部链接解析支持 `colla://message/{id}` 和带 `messageId` 的 `/im` URL。
- 增加 `GET /api/platform/object-types`。
- 前端对象卡片统一对象类型中文文案和不可访问状态文案。
- 增加平台对象集成测试覆盖消息对象。
