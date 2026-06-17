---
title: 当前技术架构
status: active
last_code_check: 2026-06-16
---

# 当前技术架构

本文档按当前代码现实整理。历史长版技术设计已归档到 `docs/99-archive/old-drafts/phase1-technical-design.md`。

## 总体形态

当前架构是单体后端 + Web 前端 + 本地 Docker 依赖：

- 后端：Spring Boot 单体，按模块包组织。
- 前端：Vite + React 单页应用，按业务模块拆分路由和 API client。
- 数据：PostgreSQL + Flyway。
- 实时：Spring WebSocket，统一入口 `/ws/events`。
- 对象存储：MinIO。
- 缓存/预留实时基础：Redis。

## 后端模块

当前 `server/src/main/java/com/colla/platform/modules` 下的模块：

| 模块 | 主要职责 |
| --- | --- |
| `identity` | 登录、JWT、成员、管理员用户、设备 |
| `workspace` | 工作台 dashboard |
| `im` | 会话、成员、消息、未读、消息操作、链接卡片、消息上下文定位 |
| `project` | 项目、事项、评论、附件、活动、BUG 验证、事项关联、项目通知和审计 |
| `doc` | 文档、blocks、块编辑、版本、块级评论/解决、权限、关系 |
| `base` | Base、Table、Field、Record、View、记录详情、看板、日历、权限和平台对象分享 |
| `approval` | 审批表单、实例、任务、动作 |
| `notification` | 站内通知、未读、已读、实时推送 |
| `platform` | 平台对象、内部链接、最近访问、收藏 |
| `search` | 搜索索引、搜索 API、索引重建 |
| `permission` | 权限决策服务 |
| `event` | domain events 和 worker |
| `audit` | 管理审计日志查询 |
| `file` | 文件上传完成、下载 URL、文件元数据 |

## Web 前端模块

当前 `web/src/modules` 下的业务模块：

| 模块 | 页面/能力 |
| --- | --- |
| `auth` | 登录、认证状态 |
| `dashboard` | 工作台 |
| `messenger` | IM 页面 |
| `projects` | 项目和事项页面 |
| `docs` | 文档页面 |
| `bases` | 表格页面 |
| `approvals` | 审批页面 |
| `notifications` | 通知页面 |
| `devices` | 设备页面 |
| `search` | 搜索页面 |
| `admin` | 用户管理页面 |
| `platform` | 对象卡片、内部对象链接、对象 API |

Web 路由已使用 `lazyRoute` 懒加载，构建门禁检查 route lazy-loading。

## 数据库迁移

当前迁移为 `V001` 到 `V024`：

| 范围 | 迁移 |
| --- | --- |
| workspace/identity | `V001`, `V005`, `V006` |
| platform object/permission/event/audit | `V002`, `V003`, `V004`, `V007` |
| IM | `V008`, `V013`, `V017`, `V018`, `V019` |
| project/issue | `V009`, `V016`, `V022`, `V023` |
| document | `V010`, `V020`, `V024` |
| base | `V011`, `V012` |
| search | `V013`, `V021` |
| approval | `V014` |
| ops/index | `V015` |

当前新增的项目迁移包括：

- `V022__create_issue_verification_logs.sql`：BUG 验证记录表。
- `V023__extend_issue_relations_and_verification_fields.sql`：BUG 验证环境、复现步骤、修复版本字段，以及 `issue_relations` 跨对象关联表。

当前新增的文档迁移包括：

- `V024__extend_document_comments_for_block_resolution.sql`：文档评论绑定 block、解决时间和解决人字段。

## API 边界

主要 REST 前缀：

| 前缀 | 模块 |
| --- | --- |
| `/api/auth` | 登录、刷新、退出、当前用户 |
| `/api/admin/users` | 用户管理 |
| `/api/admin/audit-logs` | 审计日志查询 |
| `/api/devices` | 登录设备和 push token |
| `/api/workspace/dashboard` | 工作台 |
| `/api/conversations` | IM |
| `/api/projects`, `/api/issues` | 项目和事项 |
| `/api/docs` | 文档 |
| `/api/bases`, `/api/base-records` | 多维表格 |
| `/api/approvals` | 审批 |
| `/api/notifications` | 通知 |
| `/api/platform` | 平台对象和内部链接 |
| `/api/search` | 搜索 |
| `/api/files` | 文件 |
| `/api/health` | 健康检查 |

项目事项 API 当前支持：

- `GET /api/projects/{projectId}/issues` 按状态、类型、优先级、负责人筛选，并支持常用排序。
- `POST /api/issues/{issueId}/transition` 做后端状态流转校验。
- `POST /api/issues/{issueId}/verifications` 写入 BUG 验证结果、环境、复现步骤和修复版本。
- `POST /api/issues/{issueId}/relations` 通过平台对象 resolver 建立事项到 issue、document、base record、message 等对象的关联。

文档 API 当前支持：

- `PATCH /api/docs/{documentId}/blocks` 保存结构化块并生成新版本。
- `POST /api/docs/{documentId}/comments` 可绑定 `blockId`，用于块级评论定位。
- `POST /api/docs/{documentId}/comments/{commentId}/resolve` 标记评论已解决。
- `GET /api/docs/{documentId}/versions/diff` 和 `POST /api/docs/{documentId}/versions/{versionNo}/restore` 支持版本对比和恢复。
- 文档前端使用平台对象最近访问和收藏 API 提供最近/收藏入口；文档内部链接卡片仍依赖平台对象摘要。

Base API 当前支持：

- 字段类型：text、number、member、date、attachment、single_select、multi_select。
- `POST /api/bases/{baseId}/tables/{tableId}/records/query` 支持筛选、排序和分页。
- `POST /api/bases/{baseId}/tables/{tableId}/views` 保存筛选/排序视图。
- `GET /api/bases/{baseId}/tables/{tableId}/views/kanban` 和 `/views/calendar` 支持看板/日历视图。
- `GET /api/base-records/{recordId}` 支持从搜索、IM 卡片或直接链接打开记录详情。
- Base 和 base_record 已注册平台对象链接，内部链接和 IM 分享可走统一对象卡片。

## 实时事件

WebSocket 入口：

```text
ws://localhost:8080/ws/events?token={accessToken}
```

当前主要事件类型：

- `message.created`
- `message.edited`
- `message.revoked`
- `message.pinned`
- `message.unpinned`
- `message.reaction.toggled`
- `conversation.updated`
- `conversation.read`
- `unread.changed`
- `notification.created`
- `notification.read`
- `notification.unread.changed`

前端 `useWebSocketConnection` 负责连接、重连、事件去重和分发。

IM 消息定位使用 `GET /api/conversations/{conversationId}/messages/{messageId}/context` 拉取目标消息之前的上下文，前端打开 `/im?conversationId={conversationId}&messageId={id}` 后滚动并高亮目标消息。WebSocket 断线重连后，前端按当前会话最大 `messageSeq` 补拉、合并并去重。

## 搜索

当前搜索实现：

- `SearchIndexService` 负责刷新 workspace 索引。
- `SearchService` 查询前刷新索引并使用平台对象 resolver 水合结果。
- 搜索覆盖 issue、document、base_record、message。
- `POST /api/search/reindex` 可手动重建索引，受管理权限保护。
- 索引刷新在事务内串行执行，避免后台事件刷新和查询前刷新并发时暴露半成品索引。

## 交付与运维

当前交付基线位于 `deploy/`：

- `deploy/docker-compose.prod.yml`：单节点生产/测试环境 Docker Compose 编排，包含 PostgreSQL、Redis、MinIO、后端、Web 和 Nginx。
- `deploy/nginx/colla.conf`：API、WebSocket、健康检查和 Web 前端反向代理。
- `deploy/scripts/backup.ps1`：生成 PostgreSQL SQL dump、MinIO 数据归档、manifest 和 SHA-256 校验。
- `deploy/scripts/restore.ps1`：带 `-ConfirmRestore` 的破坏性恢复脚本，恢复后执行健康检查。
- `deploy/scripts/restore-drill.ps1`：默认 dry-run 的恢复演练脚本，可校验 manifest 和 compose 配置。
- `deploy/scripts/health-check.ps1`：校验 `/api/health`、`/actuator/health`，直连后端时可校验 `/actuator/prometheus`。
- `deploy/scripts/release-check.ps1`：发布前检查工作树、质量门禁、compose 配置和可选镜像构建。
- `deploy/scripts/rollback.ps1`：带 `-ConfirmRollback` 的应用回滚和可选数据回滚入口。

日志保留策略：

- Spring Boot 通过 `logback-spring.xml` 输出 JSON app/error rolling logs。
- 生产 compose 将后端 `LOG_PATH` 指向 `/app/logs`，挂载到 `server_logs` volume。
- Docker json logs 在生产 compose 中限制为单文件 100 MB、每服务 14 个文件。

## 权限与对象可见性

当前系统存在两层权限：

- 业务模块内权限校验，例如项目成员、文档权限、Base 成员、审批参与人。
- 平台对象 resolver 返回 `available`、`forbidden`、`deleted`、`not_found`、`invalid` 权限态。

搜索、内部链接、对象卡片依赖平台对象摘要，避免直接泄露无权限对象详情。

## 当前测试现实

当前后端测试位于 `server/src/test/java`，覆盖：

- Auth
- Admin user
- Device
- Workspace dashboard
- IM
- Project
- Document
- Base
- Approval
- Platform object
- Search collaboration
- Auth shared support

最近一次模块验证结果：

- M26：`mvn -Dtest=ImControllerIntegrationTests test`、`mvn -Dtest=SearchCollaborationIntegrationTests test`、`pnpm web:lint`、`pnpm web:build`、`pnpm smoke:im` 均通过；内置浏览器验证 `/im` 可登录并渲染会话列表、消息区和信息栏；M26 checkpoint 与 finish/full 工作循环门禁通过。
- M27：`mvn -Dtest=ProjectControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 均通过；内置浏览器验证 `/projects` 和 `/issues/{id}` 可登录并渲染筛选条、看板、事项详情、关联对象入口和 BUG 验证字段。
- M28：`mvn -Dtest=DocumentControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 均通过；内置浏览器验证 `/docs` 可登录并渲染搜索、最近/收藏入口、块编辑器、版本、评论定位/解决入口，搜索过滤生效且控制台无错误。
- M29：`mvn -Dtest=BaseControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 均通过；内置浏览器验证 `/bases` 可登录并渲染表格、字段、保存视图、成员权限和记录详情面板，记录链接打开后展示字段值与评论预留区，控制台无错误。
- M30：运维脚本语法检查、生产 compose config、备份生成、恢复演练 dry-run、健康检查、release-check dry-run、`pnpm verify` 和 `pnpm work:finish` 门禁通过。

## 当前架构 Gap

- 仍是单体应用，没有拆微服务。
- 实时事件已统一入口，但不是完整事件订阅中心。
- 文档没有多人实时协同编辑。
- 审计后端已有，前端审计页面未完成。
- 桌面端和移动端还不是正式交付形态。
- 生产部署仍是单节点 Compose 基线，尚未进入高可用、多节点、自动扩缩容或 Kubernetes 形态。
