---
title: 平台对象模型
status: active
last_code_check: 2026-06-15
---

# 平台对象模型

平台对象模型用于让 IM、搜索、通知、最近访问、收藏和跨模块链接共享同一套对象摘要和权限态。

## 核心数据结构

当前后端统一摘要为 `PlatformObjectSummary`：

| 字段 | 含义 |
| --- | --- |
| `objectType` | 对象类型，例如 `issue`、`document` |
| `objectId` | 对象 ID |
| `accessState` | `available`、`forbidden`、`deleted`、`not_found`、`invalid` |
| `title` | 可展示标题 |
| `subtitle` | 可展示副标题 |
| `status` | 业务状态 |
| `webPath` | Web 跳转路径 |
| `deepLink` | 多端 deep link |
| `metadata` | 模块扩展字段 |

无权限、已删除或不存在对象必须返回不可用摘要，不应返回敏感标题或正文。

## 当前对象类型

当前 resolver 和前端 label 覆盖：

| objectType | 来源模块 | Web path | Deep link | 当前用途 |
| --- | --- | --- | --- | --- |
| `issue` | project | `/issues/{id}` | `colla://issue/{id}` | 事项卡片、搜索、通知、IM 链接 |
| `document` | doc | `/docs/{id}` | `colla://document/{id}` | 文档卡片、搜索、通知、IM 链接 |
| `base` | base | `/bases/{id}` | `colla://base/{id}` | 表格空间卡片、最近访问、收藏 |
| `base_table` | base | `/bases/{baseId}/tables/{id}` | `colla://base_table/{id}` | 数据表卡片 |
| `base_record` | base | `/bases/{baseId}/tables/{tableId}/records/{id}` | `colla://base_record/{id}` | 表格记录卡片、搜索 |
| `message` | im | `/im?conversationId={conversationId}&messageId={id}` | `colla://message/{id}` | 消息搜索、消息对象摘要 |
| `approval` | approval | `/approvals/{id}` | `colla://approval/{id}` | 审批通知、审批卡片 |
| `file` | file | 当前没有专用 resolver | 当前没有稳定 deep link | 文件元数据和附件引用 |

注意：`PlatformObjectType` 枚举中仍有历史大写类型，但当前 resolver 和前端使用小写 `objectType` 字符串。

## 主要 API

| API | 用途 |
| --- | --- |
| `GET /api/platform/objects/{type}/{id}/summary` | 获取对象摘要 |
| `GET /api/platform/object-types` | 获取对象类型规则 |
| `GET /api/platform/objects/{type}/{id}/navigation` | 获取导航信息并记录最近访问 |
| `POST /api/platform/objects/{type}/{id}/access` | 记录访问 |
| `GET /api/platform/recent` | 最近访问对象 |
| `GET /api/platform/favorites` | 收藏对象 |
| `POST /api/platform/objects/{type}/{id}/favorite` | 收藏对象 |
| `POST /api/platform/objects/{type}/{id}/favorite/remove` | 取消收藏 |
| `POST /api/platform/links/resolve` | 解析内部链接 |

## 内部链接解析

当前内部链接解析支持：

- `colla://{objectType}/{id}`
- `/issues/{id}`
- `/docs/{id}`
- `/bases/{id}`
- `/approvals/{id}`
- `/im?conversationId={conversationId}&messageId={id}`
- 包含上述路径的完整 URL

IM 消息发送时会扫描文本中的内部链接，解析后写入 `message_links`，前端展示 `InternalLinkCard`。

## 搜索关系

搜索索引当前覆盖：

- `issue`
- `document`
- `base_record`
- `message`

搜索结果经过 `PlatformObjectResolverRegistry` 解析，返回当前用户视角下的 `accessState`、`title`、`webPath` 和 `deepLink`。

## 最近访问和收藏

平台对象服务会在对象导航或显式 access 时记录最近访问；用户可对平台对象执行收藏/取消收藏。工作台会展示最近文档、表格、最近访问和收藏对象。

## 当前 Gap

- `file` 暂无完整 resolver 和统一导航页。
- 平台对象类型规则表和 Java resolver 的类型来源仍是两套，需要后续收敛。
- 对象关系图谱仍是基础能力，尚未形成跨对象关系浏览页面。
