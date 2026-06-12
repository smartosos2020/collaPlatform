# 项目骨架设计与初始化方案

## 1. 文档目的

本文档定义轻量协同工作平台从文档阶段进入工程阶段前的最后一层设计，覆盖：

- 仓库目录结构。
- 后端 Spring Boot 模块化单体骨架。
- 前端 React 应用骨架。
- 数据库迁移与初始化数据。
- Docker Compose 本地环境。
- 配置、日志、测试和代码规范。
- 第一批初始化任务清单。

本文档不直接创建代码，只作为工程初始化蓝图。

## 2. 关联文档

- [轻量协同工作平台 PRD + 技术架构草案](./light-colla-platform-prd-architecture.md)
- [第一阶段 MVP 需求清单与页面原型说明](./mvp-requirements-and-prototype.md)
- [第一阶段技术设计：数据表、接口、事件与权限](./phase1-technical-design.md)
- [技术选型确认](./technology-selection.md)

## 3. 初始化目标

工程初始化完成后，应达到以下状态：

- 后端可以启动并连接 PostgreSQL、Redis、MinIO。
- 前端可以启动并访问后端健康检查接口。
- IM 后端协议和数据模型支持 Web、桌面端、iOS、Android 多客户端接入。
- 平台对象、内部链接、通知和权限模型支持所有核心组件跨端复用。
- Docker Compose 可以启动本地依赖服务。
- Flyway 可以执行第一批数据库迁移。
- OpenAPI 文档入口可访问。
- 登录、用户、权限、IM、项目、文档、表格等模块有清晰包结构。
- 前端路由、布局、API client、鉴权状态、WebSocket client 有基础框架。
- 后续 Sprint 可以按模块往骨架中填充业务代码。

## 4. 仓库结构

建议采用单仓库多应用结构：

```text
collaPlatform/
  README.md
  .gitignore
  .editorconfig
  .env.example
  docker-compose.yml
  docs/
  server/
  web/
  desktop/
  mobile/
  scripts/
  deploy/
```

目录职责：

| 目录 | 说明 |
| --- | --- |
| `docs/` | 产品、架构、技术设计文档 |
| `server/` | Spring Boot 后端 |
| `web/` | React Web 前端，第一阶段主交付 |
| `desktop/` | 桌面端预留目录，后续 Tauri/Electron |
| `mobile/` | 移动端预留目录，后续 React Native/Expo |
| `scripts/` | 本地开发和维护脚本 |
| `deploy/` | 部署相关配置，后续可放 nginx、systemd、k8s 等 |

第一阶段不建议拆多个 git 仓库。单仓库更利于前后端接口、数据库迁移和 Docker Compose 协同演进。

第一阶段建议只初始化 `server/` 和 `web/`。`desktop/`、`mobile/` 可先保留目录和 README，等 IM Web 端协议稳定后再初始化具体工程。

## 5. 后端工程骨架

### 5.1 技术基线

- Java 21+
- Spring Boot 3.5.x，当前初始化版本锁定 `3.5.15`
- Maven
- Spring Web
- Spring Security
- Spring WebSocket
- Spring Data JPA
- JDBC 或 MyBatis，用于多维表格复杂查询
- Flyway
- PostgreSQL Driver
- Redis Client
- MinIO Java SDK
- springdoc-openapi
- JUnit 5
- Testcontainers

### 5.2 Maven 项目结构

第一阶段建议先使用单 Maven module，保持简单。模块化通过 Java package 和 Spring bean 边界实现。

```text
server/
  pom.xml
  src/
    main/
      java/
        com/
          colla/
            platform/
              CollaPlatformApplication.java
              bootstrap/
              config/
              modules/
              shared/
      resources/
        application.yml
        application-local.yml
        db/
          migration/
    test/
      java/
        com/
          colla/
            platform/
```

暂不建议一开始做 Maven 多模块。原因：

- 第一阶段团队和系统规模还不需要。
- 多模块会增加构建、依赖管理和重构成本。
- 先通过 package-private、模块门面服务和依赖规则控制边界。

后续如果模块膨胀，可以再拆：

```text
server/
  platform-app/
  platform-shared/
  platform-modules/
```

### 5.3 后端包结构

```text
com.colla.platform
  bootstrap/
    DataInitializer.java
    BuiltinRoleInitializer.java
  config/
    SecurityConfig.java
    WebConfig.java
    WebSocketConfig.java
    OpenApiConfig.java
    RedisConfig.java
    StorageConfig.java
  modules/
    platform/
    identity/
    permission/
    im/
    project/
    doc/
    base/
    notification/
    file/
    audit/
  shared/
    auth/
    database/
    domain/
    events/
    errors/
    pagination/
    security/
    storage/
    time/
    validation/
    websocket/
```

### 5.4 单个业务模块内部结构

以 `project` 模块为例：

```text
modules/project/
  api/
    ProjectController.java
    IssueController.java
    dto/
  application/
    ProjectService.java
    IssueService.java
    IssueTransitionService.java
  domain/
    Project.java
    Issue.java
    IssueStatus.java
    IssueType.java
    ProjectRole.java
  infrastructure/
    ProjectRepository.java
    IssueRepository.java
    IssueQueryRepository.java
  events/
    IssueCreatedEvent.java
    IssueAssignedEvent.java
```

职责约束：

- `api`：只处理 HTTP 入参、出参和状态码。
- `application`：编排用例、事务和权限判断。
- `domain`：领域对象、枚举、状态流转规则。
- `infrastructure`：数据库、外部存储、查询实现。
- `events`：模块发布的领域事件定义。

### 5.5 后端模块清单

| 模块 | 说明 | 第一批职责 |
| --- | --- | --- |
| `platform` | 平台对象与链接 | 对象摘要、内部链接、deep link、跨端路由 |
| `identity` | 用户、登录、会话 | 登录、当前用户、成员管理 |
| `permission` | 权限判断 | 系统角色、项目权限、资源权限 |
| `im` | 会话和消息 | 会话、消息、@、内部链接卡片 |
| `project` | 项目和事项 | 项目、任务、Bug、评论、状态流转 |
| `doc` | 文档 | 文档、版本、权限 |
| `base` | 多维表格 | 表格空间、数据表、字段、记录 |
| `notification` | 通知 | 通知生成、未读数、系统通知 |
| `file` | 文件 | 上传登记、下载授权、文件引用 |
| `audit` | 审计 | 关键操作审计 |

### 5.6 共享模块约束

`shared` 只放跨模块基础能力，不放业务逻辑。

允许放：

- 统一错误模型。
- 分页模型。
- 当前登录用户上下文。
- 领域事件基础接口。
- 存储客户端接口。
- 时间工具。
- 通用校验。

不允许放：

- 项目业务规则。
- 文档权限特判。
- 表格字段业务逻辑。
- IM 消息解析业务逻辑。

## 6. 后端关键基础设施

### 6.1 认证与安全

第一阶段采用：

- Access Token + Refresh Token。
- Spring Security 过滤器解析 access token。
- `CurrentUser` 注入当前用户上下文。
- Refresh token hash 存库。
- 登录和 WebSocket 连接都需要携带设备信息，支持 `web`、`desktop`、`ios`、`android`。

包结构：

```text
shared/auth/
  CurrentUser.java
  CurrentUserHolder.java
  JwtTokenService.java
  AuthFilter.java
  PasswordHasher.java
  DeviceContext.java
```

### 6.2 权限服务

集中放在 `permission` 模块：

```text
modules/permission/
  application/
    PermissionService.java
  domain/
    PermissionCode.java
    ResourceType.java
    PermissionLevel.java
```

核心接口：

```text
canAccessAdmin(userId)
canManageUsers(userId)
canViewProject(userId, projectId)
canEditIssue(userId, issueId)
canViewDocument(userId, documentId)
canEditDocument(userId, documentId)
canViewBase(userId, baseId)
canEditBase(userId, baseId)
canViewMessageCard(userId, targetType, targetId)
canUseDevice(userId, deviceId)
```

原则：

- Controller 不直接查权限表。
- Application service 在事务入口处做权限判断。
- 文件下载、搜索结果、消息卡片都必须经过权限服务。

### 6.3 领域事件

第一阶段用数据库 outbox。

包结构：

```text
shared/events/
  DomainEvent.java
  DomainEventPublisher.java
  DomainEventRepository.java
  DomainEventWorker.java
```

实现策略：

- 业务事务内写 `domain_events`。
- Worker 定时拉取 pending 事件。
- 事件处理器生成通知、推送 WebSocket、写审计。
- 事件处理必须幂等。

### 6.4 WebSocket

包结构：

```text
shared/websocket/
  WebSocketSessionRegistry.java
  WebSocketMessageSender.java
  WebSocketAuthInterceptor.java
  WebSocketEventPayload.java
  ClientType.java
```

第一阶段职责：

- 认证连接。
- 按用户和设备维护连接。
- 推送 `message.created`。
- 推送 `conversation.updated`。
- 推送 `notification.created`。
- 推送 `conversation.read` 和 `unread.changed`，用于跨设备同步。

消息发送仍走 REST。

### 6.5 文件存储

包结构：

```text
shared/storage/
  ObjectStorageClient.java
  MinioObjectStorageClient.java
  PresignedUrlService.java
```

原则：

- 业务模块不直接调用 MinIO SDK。
- 文件上传、下载、引用关系都经 `file` 模块。
- 下载 URL 发放前必须做业务对象权限判断。

## 7. 数据库迁移设计

### 7.1 Flyway 目录

```text
server/src/main/resources/db/migration/
  V001__create_workspace_and_identity.sql
  V002__create_platform_object_tables.sql
  V003__create_permission_tables.sql
  V004__create_im_tables.sql
  V005__create_project_tables.sql
  V006__create_doc_tables.sql
  V007__create_base_tables.sql
  V008__create_notification_and_event_tables.sql
  V009__create_file_tables.sql
  V010__create_audit_tables.sql
  V011__seed_builtin_roles_permissions.sql
```

### 7.2 迁移规范

- 已合并迁移文件不修改，只新增版本。
- 表名使用复数小写蛇形。
- 字段名使用小写蛇形。
- 所有业务表包含 `workspace_id`。
- 所有关键外键字段建立索引。
- 外键约束第一阶段可以谨慎使用：核心关系加外键，消息和动态类大表可先用索引和服务层约束。
- 枚举值用 varchar，不用数据库 enum，方便迭代。

### 7.3 初始化数据

第一批初始化数据：

- 默认 workspace。
- 内置系统角色：`admin`、`member`。
- 内置项目角色：`project_owner`、`project_member`、`project_viewer`。
- 权限编码。
- 管理员账号。
- 系统通知会话。
- 设备类型枚举使用应用层常量，不使用数据库 enum。

管理员账号策略：

- 本地开发通过环境变量设置初始账号和密码。
- 生产环境首次启动必须要求修改默认密码。

## 8. 前端工程骨架

### 8.1 技术基线

- React
- TypeScript
- Vite
- Ant Design
- React Router
- TanStack Query
- Zustand
- TanStack Table
- TipTap
- Vitest
- React Testing Library
- Playwright

### 8.2 前端目录结构

```text
web/
  package.json
  vite.config.ts
  tsconfig.json
  index.html
  src/
    main.tsx
    app/
      App.tsx
      router.tsx
      providers.tsx
      layout/
        AppLayout.tsx
        Sidebar.tsx
        Topbar.tsx
    modules/
      platform/
      auth/
      dashboard/
      messenger/
      projects/
      docs/
      bases/
      notifications/
      admin/
    shared/
      api/
      components/
      constants/
      hooks/
      icons/
      permissions/
      stores/
      styles/
      types/
      websocket/
    test/
```

### 8.3 单个前端模块结构

以 `messenger` 模块为例：

```text
modules/messenger/
  api/
    messengerApi.ts
  components/
    ConversationList.tsx
    MessageList.tsx
    MessageComposer.tsx
    MessageCard.tsx
    InternalLinkCard.tsx
  hooks/
    useConversations.ts
    useMessages.ts
  pages/
    MessengerPage.tsx
  types/
    messenger.types.ts
```

职责约束：

- `pages` 负责页面组装。
- `components` 负责局部 UI。
- `hooks` 封装请求和交互状态。
- `api` 只封装接口调用。
- 跨模块组件进入 `shared/components`。

### 8.4 前端路由

第一批路由：

```text
/login
/
/im
/projects
/projects/:projectId
/issues/:issueId
/docs
/docs/:docId
/bases
/bases/:baseId
/bases/:baseId/tables/:tableId
/notifications
/admin/users
/admin/roles
```

默认：

- `/` 指向工作台。
- 未登录访问业务路由跳转 `/login`。
- 无权限访问显示 403 页面。

### 8.5 API Client

目录：

```text
shared/api/
  httpClient.ts
  apiError.ts
  queryClient.ts
```

规范：

- 统一注入 access token。
- 统一处理 401，尝试 refresh。
- 统一错误结构。
- 列表请求统一分页模型。
- 不在组件里拼接复杂 URL。

### 8.6 WebSocket Client

目录：

```text
shared/websocket/
  websocketClient.ts
  websocketEvents.ts
  useWebSocketConnection.ts
```

职责：

- 登录后建立连接。
- token 过期后重连。
- 收到 `message.created` 后刷新对应会话。
- 收到 `notification.created` 后刷新通知角标。
- 断线后根据技术设计补拉消息和通知。

### 8.7 Web 端适配策略

Web 端第一阶段需要覆盖桌面浏览器和移动浏览器的基本可用性，但不是完整移动 App。

要求：

- 消息页在窄屏下切换为会话列表/消息详情双状态。
- 输入框、附件、@选择、内部链接卡片在移动浏览器中可用。
- 项目、文档、表格复杂编辑仍以桌面 Web 为主。
- Web 端可作为桌面端封装的基础 UI。

### 8.8 桌面端预留

目录：

```text
desktop/
  README.md
```

后续候选结构：

```text
desktop/
  src-tauri/
  package.json
```

或：

```text
desktop/
  electron/
  package.json
```

第一阶段不初始化桌面端工程，但 Web 前端需要避免写死浏览器专属假设，便于后续封装。

### 8.9 移动端预留

目录：

```text
mobile/
  README.md
```

后续候选结构：

```text
mobile/
  app.json
  package.json
  src/
    app/
    modules/
      auth/
      messenger/
      notifications/
      platform/
    shared/
      api/
      stores/
      websocket/
      push/
```

移动端第一阶段不初始化工程，但后端需要预留：

- 设备注册。
- refresh token。
- push token。
- 离线消息补拉。
- 未读数同步。
- 平台对象摘要。
- deep link 解析。

## 9. 前端 UI 骨架

### 9.1 全局布局

使用 Ant Design 的 Layout 思路，但需要控制视觉密度，避免后台系统过重。

布局：

- 左侧主导航。
- 顶部搜索、创建、通知、用户菜单。
- 中间主内容。
- 右侧上下文抽屉按页面需要打开。

### 9.2 主题策略

第一阶段：

- 使用 Ant Design token 做基础主题。
- 保持中性色为主。
- 模块色只用于状态、优先级、标签和提醒。

不要第一阶段做复杂品牌视觉。内部工具优先清晰、稳定、信息密度合适。

### 9.3 核心共享组件

```text
shared/components/
  AppPageHeader/
  EmptyState/
  ErrorState/
  LoadingState/
  UserAvatar/
  UserSelect/
  FileUpload/
  FileList/
  PermissionGuard/
  StatusTag/
  PriorityTag/
  InternalObjectLink/
  ConfirmAction/
```

## 10. Docker Compose 设计

### 10.1 服务清单

```text
services:
  postgres
  redis
  minio
  server
  web
```

第一阶段可以先让 `server` 和 `web` 本地命令启动，只用 Docker Compose 启动依赖服务。等工程稳定后再补应用容器。

### 10.2 端口规划

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| web | 5173 | Vite dev server |
| desktop | 待定 | 后续桌面端开发服务 |
| mobile | 待定 | 后续 Expo dev server |
| server | 8080 | Spring Boot API |
| postgres | 5432 | 数据库 |
| redis | 6379 | 缓存 |
| minio | 9000 | S3 API |
| minio-console | 9001 | MinIO 控制台 |

### 10.3 环境变量

`.env.example`：

```text
POSTGRES_DB=colla_platform
POSTGRES_USER=colla
POSTGRES_PASSWORD=colla_dev_password

REDIS_HOST=localhost
REDIS_PORT=6379

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=colla_minio
MINIO_SECRET_KEY=colla_minio_password
MINIO_BUCKET=colla-files

JWT_ACCESS_SECRET=change-me-access
JWT_REFRESH_SECRET=change-me-refresh

INIT_ADMIN_USERNAME=admin
INIT_ADMIN_PASSWORD=admin123456
```

真实 `.env` 不提交仓库。

## 11. 配置文件设计

### 11.1 后端配置

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

配置分层：

- `application.yml`：默认结构和非敏感默认值。
- `application-local.yml`：本地开发。
- `application-test.yml`：测试。
- `application-prod.yml`：生产环境占位，不提交真实密钥。

### 11.2 前端配置

```text
web/.env.example
web/.env.local
```

变量：

```text
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws
```

后续桌面端和移动端应使用独立环境变量文件，不复用 Web 的 Vite 变量命名。

## 12. API 文档与契约

### 12.1 OpenAPI

后端启用 OpenAPI：

```text
/swagger-ui
/v3/api-docs
```

要求：

- Controller DTO 加清晰字段说明。
- 错误码统一枚举。
- 分页模型统一。
- 登录、消息、项目、文档、表格接口优先补注释。

### 12.2 前后端协作

第一阶段可先手写 TypeScript API 类型，不急于引入自动生成。

后续可考虑：

- 从 OpenAPI 生成 TypeScript client。
- 或引入契约测试。

## 13. 测试骨架

### 13.1 后端测试

目录：

```text
server/src/test/java/com/colla/platform/
  modules/
  shared/
```

第一批测试：

- `PasswordHasherTest`
- `JwtTokenServiceTest`
- `PermissionServiceTest`
- `IssueTransitionServiceTest`
- `DocumentVersionConflictTest`
- `BaseFieldValueValidatorTest`

集成测试：

- 登录流程。
- 创建项目自动生成项目群。
- 发送消息落库并生成事件。
- 内部业务链接解析权限。

### 13.2 前端测试

第一批测试：

- 登录页表单。
- AppLayout 导航。
- PermissionGuard。
- InternalLinkCard。
- MessageComposer。
- IssueStatusTag。

E2E 第一批：

- 登录后进入工作台。
- 创建项目。
- 发送消息。
- 粘贴 Bug 链接显示卡片。

## 14. 代码规范

### 14.1 通用规范

- 统一使用 UTF-8。
- Markdown 文档使用中文，代码和标识符使用英文。
- 提交前至少运行格式化和基础测试。
- 不提交 `.env`、日志、构建产物。

### 14.2 后端规范

- Controller 不写业务逻辑。
- Service 方法表达业务用例。
- 状态流转不允许散落在 Controller 或 Repository。
- 所有写操作必须记录 `created_by`、`updated_by`。
- 所有跨模块副作用通过领域事件或模块服务完成。

### 14.3 前端规范

- 页面组件不直接调用 `fetch`。
- API 调用统一走 `shared/api/httpClient.ts`。
- 权限显示走 `PermissionGuard` 或模块 hook。
- 表格字段编辑器按字段类型注册。
- WebSocket 事件只触发缓存失效或局部状态更新，不直接绕过 API 修改复杂业务状态。

## 15. 初始化任务清单

### 15.1 仓库基础

- 创建 `.gitignore`。
- 创建 `.editorconfig`。
- 创建根 `README.md`。
- 创建 `.env.example`。
- 创建 `docker-compose.yml`。
- 创建 `scripts/` 和 `deploy/` 目录。

### 15.2 后端初始化

- 使用 Spring Initializr 或等价方式创建 `server/`。
- 设置 Java 21+。
- 设置 Maven。
- 引入 Spring Web、Security、WebSocket、Data JPA、Validation、Flyway、PostgreSQL、Redis、OpenAPI、Testcontainers。
- 创建基础 package。
- 创建健康检查接口。
- 创建统一错误响应。
- 创建分页响应模型。
- 创建 Flyway 迁移目录。
- 创建第一批迁移脚本。
- 创建本地配置。

### 15.3 前端初始化

- 使用 Vite 创建 `web/`。
- 设置 React + TypeScript。
- 引入 Ant Design、React Router、TanStack Query、Zustand、TanStack Table、TipTap。
- 创建 App providers。
- 创建基础路由。
- 创建全局布局。
- 创建 API client。
- 创建登录页占位。
- 创建工作台占位。
- 创建消息页占位。

### 15.4 本地环境初始化

- Docker Compose 启动 PostgreSQL、Redis、MinIO。
- 后端本地启动并执行 Flyway。
- 前端本地启动并访问后端健康检查。
- MinIO 创建默认 bucket。
- 创建默认 workspace 和管理员。

### 15.5 第一批模块占位

后端：

- `platform`
- `identity`
- `permission`
- `im`
- `project`
- `doc`
- `base`
- `notification`
- `file`
- `audit`

前端：

- `platform`
- `auth`
- `dashboard`
- `messenger`
- `projects`
- `docs`
- `bases`
- `notifications`
- `admin`

## 16. 初始化顺序

建议按以下顺序执行：

1. 初始化仓库基础文件。
2. 创建 Docker Compose 依赖服务。
3. 初始化后端 Spring Boot 工程。
4. 接入 PostgreSQL、Flyway、Redis。
5. 创建第一批数据库表和初始化数据。
6. 初始化前端 Vite 工程。
7. 接入 Ant Design、路由、QueryClient。
8. 打通前端健康检查请求。
9. 实现登录骨架。
10. 实现全局布局。
11. 再进入 Sprint 1 业务开发。

不要先做复杂页面，也不要先做 IM WebSocket。先把基础工程、数据库迁移、配置和鉴权跑通。

## 17. 最小可运行验收

骨架初始化完成后，需要满足：

- `docker compose up -d postgres redis minio` 成功。
- 后端启动成功。
- Flyway 迁移成功。
- `GET /api/health` 返回正常。
- OpenAPI 页面可访问。
- 前端启动成功。
- 前端可请求 `GET /api/health`。
- 登录页可打开。
- 未登录访问业务页跳转登录页。
- 管理员初始化成功。
- 登录接口可登记 `web` 设备。
- WebSocket 连接可绑定 `deviceId`。

## 18. 不在初始化阶段做的事

- 不实现完整 IM。
- 不初始化桌面端完整工程。
- 不初始化移动端完整工程。
- 不实现完整项目看板。
- 不实现文档编辑器复杂能力。
- 不实现多维表格字段编辑。
- 不做 OA。
- 不接入真实 SSO。
- 不做 Kubernetes。
- 不做 CI/CD 完整流水线。
- 不做性能优化。

初始化阶段只负责把工程骨架立稳，业务能力进入后续 Sprint。

## 19. 待确认问题

- 是否立即把当前目录初始化为 git 仓库。
- Spring Boot 当前锁定 `3.5.15`，后续仅做 3.5.x 补丁升级，暂不升级 4.x。
- Maven groupId 是否使用 `com.colla.platform`。
- 前端包管理器使用 npm、pnpm 还是 yarn。建议 pnpm。
- 桌面端后续使用 Tauri 还是 Electron。
- 移动端后续是否使用 React Native + Expo。
- 移动推送是否需要 APNs、FCM 和国内厂商通道。
- 是否要求第一版支持 Windows 本地开发和 Linux 部署双环境。
- 管理员初始密码策略是否允许环境变量注入。
- 是否需要从第一天加入 GitHub Actions 或其他 CI。
