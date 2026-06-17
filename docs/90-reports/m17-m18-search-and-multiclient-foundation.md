# M17-M18 搜索平台化与多端基础执行记录

## 范围

M17 到 M18 的目标是把“能用的模块功能”继续收束为平台能力：

- M17：统一搜索索引、权限过滤、对象摘要复用。
- M18：统一 API 契约、deep link、前端业务 SDK、弱网和移动布局基础。

## M17 搜索平台化一期

已落地：

- 新增 `search_index_documents` 统一索引表。
- 搜索对象统一表达：`objectType/objectId/title/excerpt/webPath/deepLink/searchText/updatedAt`。
- `SearchRepository` 改为查询统一索引，不再在搜索入口拼多个模块 SQL。
- `SearchIndexService` 作为 Postgres 搜索实现的抽象层，后续可替换 OpenSearch、Meilisearch 等外部引擎。
- `DomainEventWorker` 接入搜索索引刷新，业务事件可驱动索引更新。
- `SearchService` 在搜索前刷新 workspace 索引，作为内测阶段兜底，避免异步 worker 时序或历史数据导致搜索缺结果。
- 搜索结果先由索引查询中的权限 join 过滤，再经过平台对象 resolver 做展示增强；resolver 暂时无法增强时，保留 SQL 权限过滤已经确认可访问的结果。

一期取舍：

- 当前采用 workspace 级索引刷新，简单可靠，适合内测和小团队数据规模。
- 后续数据量上来后，将 `SearchIndexService.handleEvent` 改为对象级 upsert/delete，不需要改变搜索 API。
- 权限过滤以数据库 join 为安全边界，resolver 作为卡片字段增强层；后续各模块 resolver 权限统一后，可以进一步收敛为单一权限决策入口。

## M18 多端基础一期

已落地：

- HTTP 客户端增加客户端标识和有限重试：GET/HEAD 对网络错误、502、503、504 做退避重试。
- WebSocket 客户端增加指数退避重连和 `eventId` 去重。
- 新增 `web/src/shared/client/collaClient.ts` 作为前端业务 SDK 入口。
- 新增 deep link 解析工具，统一 `colla://...` 到 Web fallback 的跳转规则。
- 对象卡片和搜索结果跳转改用统一导航解析。
- 移动宽度下补 IM、文档、表格、搜索和全局头部的基础响应式规则。

桌面端方案：

- 一期建议选择 Tauri。
- 原因：团队内部工具以 Web 能力复用为主，Tauri 包体更轻，常驻托盘和系统通知能力足够；Electron 作为复杂插件生态或重型桌面集成时的备选。

移动端方案：

- 一期建议先做 PWA + 响应式 Web。
- 原因：当前核心诉求是 IM、通知、对象详情查看和轻编辑，PWA 可最快复用 Web SDK 和 API 契约。
- 当需要原生推送、通讯录、文件系统深度能力时，再评估 React Native。

## API 契约约束

- API 返回必须以客户端无关字段为主，避免只为 Web UI 暴露私有结构。
- 对象跳转必须同时尽量提供 `webPath` 和 `deepLink`。
- 多端跳转优先级：`webPath`、`mobileFallbackPath`、`deepLink` fallback。
- 实时事件必须保留 `eventId/type/serverTime/payload`，平台对象事件补充 `workspaceId/objectType/objectId`。
- 客户端必须能处理重复事件、断线重连、历史数据补拉。

## 测试覆盖

- 搜索覆盖事项、文档、表格记录和消息。
- 搜索对无权限用户不返回结果。
- 搜索结果必须包含 `accessState=available`。
- 质量门禁覆盖前端 lint/build、chunk、路由懒加载、Flyway 顺序和敏感信息扫描。
