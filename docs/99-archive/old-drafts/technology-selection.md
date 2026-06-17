# 技术选型确认

## 1. 文档目的

本文档用于确认轻量协同工作平台第一阶段的主技术栈，并说明各项选择的理由、风险和替代方案。

进入代码前的项目目录、模块骨架、Docker Compose 和初始化任务见：[项目骨架设计与初始化方案](./project-skeleton-initialization.md)。

Lark/飞书产品形态对标分析见：[Lark 产品形态分析与本平台调整建议](./lark-product-shape-analysis.md)。

本次选型服务于以下目标：

- 内部研发团队自用。
- 模块化单体优先。
- IM、项目/Bug、文档、多维表格为第一阶段核心组件。
- 平台级对象、链接、权限和通知需要跨 Web、桌面端、移动端复用。
- OA 后置。
- 预留后续拆分 IM、协同编辑、通知、搜索等服务的空间。

## 2. 结论总览

| 领域 | 主选型 | 结论 |
| --- | --- | --- |
| 后端框架 | Spring Boot 3.x + Java | 作为第一阶段默认后端方案 |
| 后端语言 | Java 21+ | 使用当前 LTS Java 生态 |
| 后端构建 | Gradle 或 Maven | 团队无偏好时优先 Maven，简单稳定 |
| 前端框架 | React + TypeScript + Vite | 默认前端方案 |
| 桌面端 | Tauri 或 Electron，优先 Tauri 评估 | 复用 Web 前端，后续封装桌面客户端 |
| 移动端 | React Native + Expo | 后续 iOS/Android IM 客户端 |
| UI 组件 | Ant Design | 第一阶段默认 UI 体系 |
| 服务端状态 | TanStack Query | 默认请求缓存和服务端状态管理 |
| 客户端状态 | Zustand | 默认轻量全局状态 |
| 文档编辑器 | TipTap | 默认富文本编辑器 |
| 文档存储格式 | ProseMirror JSON + 文本摘要 | 第一阶段默认格式 |
| 表格渲染 | TanStack Table + 自定义单元格 | 默认多维表格前端方案 |
| 数据库 | PostgreSQL | 主数据库 |
| 缓存/实时状态 | Redis | 缓存、在线状态、轻量队列 |
| 文件存储 | MinIO / S3 协议 | 本地和私有化部署友好 |
| 实时通信 | WebSocket | IM 和通知实时推送 |
| 搜索 | PostgreSQL 起步，Meilisearch 后置 | 第一阶段不强依赖独立搜索服务 |
| 部署 | Docker Compose 起步 | 内部团队部署优先简单可靠 |

## 3. 后端选型

### 3.1 选择：Spring Boot + Java

第一阶段建议选择 Spring Boot 作为后端主框架。

原因：

- 平台包含权限、IM、文件、项目、文档、多维表格、通知和后续 OA，属于长期维护型业务系统。
- Spring 生态在事务、权限、安全、ORM、数据库迁移、定时任务、后台 worker、运维观测方面更成熟。
- 模块化单体在 Spring Boot 中边界清晰，后续拆服务也自然。
- 对 PostgreSQL、Redis、对象存储、WebSocket、OpenAPI、测试工具的集成成熟。
- 团队后续招聘和维护成本相对可控。

### 3.2 版本策略

截至 2026-06-13，Spring Boot 4.x 已经可用，但本项目第一阶段采用 Spring Boot 3.x 稳定线，优先降低生态兼容风险。

推荐策略：

- 第一阶段使用 Spring Boot 3.5.x。
- 初始工程锁定 `3.5.15`。
- 后续只做 3.5.x 补丁升级，暂不跨到 4.x。
- 等核心业务稳定后，再单独评估 Spring Boot 4.x 迁移。

### 3.3 Java 版本

建议：

- Java 21 或更新 LTS。

原因：

- 企业服务端生态成熟。
- 性能、容器支持、GC 和语言特性足够。
- 与当前 Spring Boot 主线匹配度高。

### 3.4 构建工具

建议默认 Maven。

原因：

- 团队协作认知成本低。
- 企业 Java 项目中稳定、直观。
- 初期工程不复杂，Maven 足够。

如果团队更熟 Gradle，可用 Gradle，但不要为了构建工具引入额外复杂度。

### 3.5 备选：NestJS + TypeScript

NestJS 的优势：

- 前后端统一 TypeScript。
- 模块化、DI、装饰器风格清晰。
- WebSocket 支持直接。
- 对前端团队更友好。

不作为默认选择的原因：

- 本项目后端长期会涉及复杂权限、事务边界、领域事件、后台任务和稳定运维。
- 如果团队没有 Node.js 后端生产运维经验，后期复杂度会集中暴露。
- 对企业内部协作平台而言，后端稳定性和长期维护优先级高于语言统一。

适合改选 NestJS 的情况：

- 团队 Java 经验弱，TypeScript 后端经验强。
- 第一阶段需要极快原型，而不是优先长期维护。
- 团队已有成熟 NestJS 脚手架、鉴权、ORM、部署和监控体系。

## 4. 前端选型

### 4.1 选择：React + TypeScript + Vite

原因：

- 生态成熟。
- 与 TipTap、TanStack Table、TanStack Query、Ant Design 集成顺畅。
- 对复杂工作台、IM、看板、文档编辑、多维表格这类交互界面支撑能力强。
- Vite 开发体验和构建速度适合中大型前端应用起步。

### 4.1.1 多客户端策略

平台需要支持 Web、桌面端和移动端。IM 是核心入口，但项目、文档、表格、通知、文件等组件也需要按跨端可访问设计。客户端策略如下：

- Web：React + TypeScript + Vite，第一阶段主交付。
- 桌面端：复用 Web 前端，优先评估 Tauri；如果团队更重视成熟生态和统一 Chromium 渲染，可选择 Electron。
- 移动端：React Native + Expo，后续独立移动 App，不建议只用 WebView 包一层。

选择依据：

- Tauri 官方支持使用现有 Web 技术栈，并支持跨平台桌面；适合追求轻量安装包。
- Electron 官方定位是用 JavaScript、HTML、CSS 构建跨平台桌面应用，生态成熟，适合需要稳定一致渲染的团队。
- React Native 官方推荐 Expo 作为生产级框架，适合 iOS/Android 原生能力、推送通知、后台恢复和系统集成。

第一阶段不强制交付桌面和移动 UI，但后端协议、平台对象、设备模型、推送 token、WebSocket 事件和 REST API 必须按多客户端设计。

### 4.2 UI 组件：Ant Design

第一阶段建议选择 Ant Design，而不是先用 shadcn/ui。

原因：

- 本平台是内部协作和管理工具，不是营销站或高度定制消费级产品。
- Ant Design 的表单、表格、弹窗、抽屉、菜单、树、上传、日期、选择器等组件完整。
- 项目管理、管理后台、权限配置、多维表格配置等页面需要大量密集型业务组件。
- 第一阶段更需要交付效率和一致性。

shadcn/ui 的优势：

- 更灵活，更容易做出定制风格。
- 组件源码可控。
- 适合设计系统要求更高的产品。

不作为第一阶段默认的原因：

- 需要更多自行装配工作。
- 对复杂后台工具类页面，效率不如 Ant Design 直接。
- 多维表格、表单、树、上传等组合场景需要更多工程投入。

### 4.3 状态管理

服务端状态：

- 使用 TanStack Query。

用途：

- API 请求。
- 缓存。
- 重试。
- 分页。
- 乐观更新。
- 数据失效刷新。

客户端状态：

- 使用 Zustand。

用途：

- 当前用户 UI 偏好。
- 当前打开会话。
- 全局弹窗。
- WebSocket 连接状态。
- 局部跨页面 UI 状态。

不建议第一阶段上 Redux Toolkit，除非团队已有成熟规范。当前需求下 Zustand 更轻。

## 5. 文档编辑器选型

### 5.1 选择：TipTap

第一阶段建议选择 TipTap 作为文档编辑器。

原因：

- TipTap 基于 ProseMirror，适合做结构化富文本。
- 支持 React 集成。
- 扩展生态较好。
- 后续需要实时协同时，可以与 Yjs/协同后端衔接。
- 比纯 Markdown 编辑器更适合后续评论、@、嵌入任务卡片、嵌入表格记录等能力。

### 5.2 存储格式

建议第一阶段使用：

- `content_json`：存 ProseMirror JSON。
- `content_text` 或摘要字段：存纯文本摘要，用于搜索和预览。
- 可选导出 Markdown，但不要把 Markdown 作为唯一主格式。

原因：

- 后续做评论定位、块级引用、任务卡片、协同编辑时，结构化 JSON 更稳。
- Markdown 对复杂富文本和嵌入对象不够自然。

### 5.3 协同编辑策略

第一阶段：

- 不做实时协同。
- 使用版本号冲突检测。
- 保存时生成 `document_versions`。

后续阶段：

- 引入 Yjs。
- 文档协同服务从单体中独立出来。

### 5.4 备选：Markdown 编辑器

Markdown 优势：

- 实现简单。
- 适合技术团队。
- 存储和 diff 简单。

不作为默认选择的原因：

- 后续做富文本协同、评论锚点、嵌入业务对象会更别扭。
- 非研发角色使用体验弱。

## 6. 多维表格选型

### 6.1 选择：TanStack Table + 自定义单元格

第一阶段建议使用 TanStack Table 作为表格渲染和交互底座。

原因：

- TanStack Table 是 headless 表格库，不强制 UI，适合和 Ant Design 或自定义样式结合。
- 支持排序、过滤、分页、列配置、受控状态。
- 多维表格需要大量自定义字段类型和单元格编辑器，headless 模型更适合。
- 可以把字段模型、行数据、筛选和排序逻辑控制在业务层。

### 6.2 不直接选择 AG Grid 的原因

AG Grid 很强，但第一阶段不作为默认。

原因：

- 功能重，学习和定制成本更高。
- 部分高级能力涉及商业版。
- 多维表格的核心不是传统数据网格，而是字段类型、记录模型、权限、视图和业务引用。

适合改选 AG Grid 的情况：

- 第一阶段就需要大数据量虚拟滚动、复杂冻结列、复杂单元格操作。
- 团队已有 AG Grid 经验。
- 可以接受其样式和授权边界。

### 6.3 单元格编辑器策略

第一阶段字段类型与组件：

| 字段类型 | 编辑组件 |
| --- | --- |
| 文本 | Input |
| 长文本 | TextArea / 抽屉编辑 |
| 数字 | InputNumber |
| 单选 | Select |
| 多选 | Select multiple |
| 日期 | DatePicker |
| 成员 | UserSelect |
| 附件 | Upload + FileList |
| 复选框 | Checkbox |
| URL | Input + 链接展示 |

### 6.4 查询策略

第一阶段：

- 服务端分页。
- 服务端筛选。
- 服务端排序。
- 前端只负责展示和交互状态。

不建议把完整数据拉到前端再筛选，因为多维表格未来增长后会很快失控。

## 7. 数据库与存储

### 7.1 主数据库：PostgreSQL

原因：

- 关系模型、事务、索引和 JSONB 能力适合该平台。
- 多维表格可以用关系表 + JSONB 混合建模。
- 文档、表格、消息、权限、通知都需要可靠事务。
- 后续可以利用全文搜索能力起步。

### 7.2 Redis

用途：

- WebSocket 在线状态。
- 会话未读数缓存。
- 短期 token/session 辅助。
- 轻量事件 worker 锁。
- 高频热点缓存。

第一阶段不要把核心业务数据只放 Redis。消息、通知、任务状态必须先落 PostgreSQL。

### 7.3 文件存储：MinIO / S3

原因：

- 本地和私有化部署简单。
- 与云对象存储协议兼容。
- 文件生命周期可以从数据库引用关系控制。

策略：

- 文件元数据存在 PostgreSQL。
- 文件内容存在对象存储。
- 下载时通过业务对象权限判断后发放临时 URL。

## 8. 实时通信选型

### 8.1 选择：WebSocket

用途：

- IM 新消息推送。
- 通知推送。
- 当前页面业务对象变更提醒。
- 在线状态，P1。
- Web、桌面端在线消息推送。
- 移动端前台消息推送，后台通过系统推送补偿。

### 8.2 IM 内部业务链接卡片

当前技术栈可以直接支撑 IM 中的内部业务链接缩略卡片，不需要增加新的基础设施。

职责划分：

- Spring Boot 负责解析内部链接、聚合卡片数据、执行权限判断。
- PostgreSQL 保存消息、业务对象引用和卡片快照。
- React + Ant Design 渲染需求、Bug、文档、表格记录等卡片。
- WebSocket 推送新消息和卡片快照。
- 权限服务在卡片展示时按当前用户动态判断可见内容。

第一阶段只支持内部业务对象卡片。外部网页链接 metadata 预览涉及 SSRF 防护、抓取超时、图片缓存和安全白名单，明确后置。

### 8.3 消息发送链路

建议：

- 发送消息走 REST。
- 推送消息走 WebSocket。

原因：

- REST 发送更容易复用统一鉴权、事务、错误码和日志。
- WebSocket 只承担实时推送，复杂度更低。
- 后续要支持移动端或外部 API 时更自然。

如果后续 IM 要做更强实时体验，可以允许 WebSocket 发送，但第一阶段没有必要。

### 8.4 移动推送策略

移动端不能假设 WebSocket 在后台长期存活。

第一阶段预留：

- `user_devices`
- `push_tokens`
- 通知事件 payload
- 设备撤销

后续移动端实现：

- iOS 通过 APNs。
- Android 优先 FCM；如面向国内 Android 分发，需要评估厂商推送通道。
- 移动推送只发提醒和摘要，打开 App 后通过 REST 拉取权威消息内容。

## 9. 搜索选型

### 9.1 第一阶段：PostgreSQL 起步

第一阶段搜索不是 P0 主闭环，可以先用：

- 标题模糊搜索。
- 简单全文搜索。
- 按模块搜索。

搜索对象：

- 项目事项。
- 文档标题和摘要。
- 表格空间和表名。
- 消息摘要，P1。

### 9.2 第二阶段：Meilisearch

第二阶段建议优先考虑 Meilisearch，而不是直接上 Elasticsearch。

原因：

- 部署轻。
- 搜索体验好。
- 适合中小团队内部系统。

选择 Elasticsearch 的情况：

- 数据规模明显变大。
- 需要复杂日志分析、聚合查询、多字段高级检索。
- 团队已有 ES 运维经验。

## 10. 部署与运维选型

### 10.1 第一阶段：Docker Compose

服务：

- `web`
- `server`
- `postgres`
- `redis`
- `minio`

原因：

- 内部团队起步简单。
- 本地开发和测试环境一致。
- 后续迁移 Kubernetes 不影响代码模块边界。

### 10.2 日志与监控

第一阶段建议：

- 应用结构化日志。
- 请求 ID。
- API 错误日志。
- WebSocket 连接日志。
- 事件 worker 处理日志。

P1 引入：

- OpenTelemetry。
- Prometheus。
- Grafana。

## 11. 安全选型

### 11.1 登录与鉴权

第一阶段：

- 用户名密码登录。
- Access Token + Refresh Token。
- 密码使用 bcrypt 或 Argon2。
- 服务端保存 refresh token hash。

后续：

- LDAP。
- 企业微信/飞书/钉钉单点登录。
- MFA。

### 11.2 权限实现

建议：

- 后端集中实现 `PermissionService`。
- 前端只做显示控制，不作为安全边界。
- 所有文件下载、搜索结果、引用对象都必须后端鉴权。

## 12. 测试选型

### 12.1 后端

建议：

- 单元测试：JUnit 5。
- 集成测试：Spring Boot Test + Testcontainers。
- API 文档：OpenAPI。
- 数据库迁移：Flyway。

### 12.2 前端

建议：

- 单元测试：Vitest。
- 组件测试：React Testing Library。
- E2E：Playwright。

第一阶段测试重点：

- 登录。
- 权限。
- 消息发送。
- 任务状态流转。
- 文档版本冲突。
- 表格字段校验。

## 13. 最终推荐组合

### 13.1 第一阶段默认技术栈

后端：

- Java 21+
- Spring Boot 3.5.x
- Spring Security
- Spring Web
- Spring WebSocket
- Spring Data JPA 或 MyBatis
- Flyway
- PostgreSQL
- Redis
- MinIO Java SDK

前端：

- React
- TypeScript
- Vite
- Ant Design
- TanStack Query
- Zustand
- TanStack Table
- TipTap
- Playwright

桌面端，后续：

- Tauri，优先评估
- Electron，成熟备选

移动端，后续：

- React Native
- Expo
- 移动推送 SDK

基础设施：

- Docker Compose
- PostgreSQL
- Redis
- MinIO

### 13.2 JPA 还是 MyBatis

建议：

- 常规业务 CRUD 用 Spring Data JPA。
- 多维表格复杂筛选查询可单独用 jOOQ 或 MyBatis。

不要为了一个查询风格强行覆盖所有场景。

原因：

- 用户、项目、文档、通知等模块适合 JPA。
- 多维表格的动态字段查询更适合手写 SQL 或 SQL DSL。
- 分层允许后续优化性能热点。

## 14. 决策记录

| 编号 | 决策 | 结果 |
| --- | --- | --- |
| ADR-001 | 后端框架 | Spring Boot 3.5.x |
| ADR-002 | 前端框架 | React + TypeScript + Vite |
| ADR-003 | UI 组件 | Ant Design |
| ADR-004 | 文档编辑器 | TipTap |
| ADR-005 | 文档主存储格式 | ProseMirror JSON |
| ADR-006 | 多维表格前端 | TanStack Table + 自定义单元格 |
| ADR-007 | 主数据库 | PostgreSQL |
| ADR-008 | 文件存储 | MinIO / S3 |
| ADR-009 | 实时通信 | REST 发送 + WebSocket 推送 |
| ADR-010 | 搜索 | PostgreSQL 起步，Meilisearch 后置 |
| ADR-011 | 部署 | Docker Compose 起步 |
| ADR-012 | 桌面端 | 优先评估 Tauri，Electron 作为成熟备选 |
| ADR-013 | 移动端 | React Native + Expo |

## 15. 待确认问题

- 团队是否有强 Spring Boot 经验。如果没有，需要重新评估 NestJS。
- 团队是否接受 Ant Design 的默认视觉风格。
- 文档第一阶段是否需要 Markdown 导入/导出。
- 多维表格第一阶段数据规模上限。
- 是否从第一版就要求 HTTPS 和内网证书配置。
- 是否需要对接已有账号系统。
- 桌面端优先支持 Windows，还是 Windows/macOS/Linux 同时支持。
- 移动端是否需要同时支持 iOS 和 Android，国内 Android 是否需要厂商推送。

## 16. 参考资料

- [Spring Boot 官方项目页](https://spring.io/projects/spring-boot)
- [Spring Boot 官方文档入口](https://docs.spring.io/spring-boot/documentation.html)
- [NestJS 官方文档](https://docs.nestjs.com/)
- [NestJS WebSocket Gateways 文档](https://docs.nestjs.com/websockets/gateways)
- [TipTap Collaboration 文档](https://tiptap.dev/docs/collaboration/getting-started/install)
- [Yjs TipTap 绑定文档](https://docs.yjs.dev/ecosystem/editor-bindings/tiptap2)
- [TanStack Table 官方文档](https://tanstack.com/table/latest/docs/introduction)
- [Tauri 官方文档](https://v2.tauri.app/)
- [Electron 官方文档](https://electronjs.org/)
- [React Native 官方文档](https://reactnative.dev/docs/environment-setup)
- [Expo 官方网站](https://expo.dev/)
- [MDN Service Worker API](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API)
