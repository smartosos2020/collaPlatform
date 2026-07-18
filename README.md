# Colla Platform

Colla Platform 是面向研发团队的 Web 协同平台，提供统一的用户工作空间和企业管理后台。平台覆盖即时沟通、项目协作、知识库、多维表格、审批、通知、搜索，以及组织与权限治理等能力。

## 产品定位

项目采用一个前端应用和一个后端服务承载两套使用界面：

- **用户工作台**：面向日常协作人员，提供业务协作和内容生产能力。
- **管理后台**：面向企业管理员，提供企业概览、组织架构、成员、用户组、角色、权限、应用和审计治理能力。

两套界面共享登录、权限、对象、通知、审计和文件存储等基础能力，但菜单和使用场景相互隔离。普通用户默认进入用户工作台，管理员通过管理入口进入管理后台。

## 功能范围

### 用户工作台

- **工作台**：个人待办、最近访问和协作入口。
- **即时沟通**：会话、消息、联系人和实时事件通知。
- **项目协作**：项目、任务、问题、状态和项目成员协作。
- **知识库**：知识库空间、目录树、内容节点、结构化块编辑、评论、版本、协同编辑和内容权限。
- **多维表格**：Base、数据表、视图、字段、记录和基础协作能力；多维表格可以作为独立协作对象被知识库目录引用。
- **审批**：审批实例、审批详情和处理动作。
- **通知**：系统通知、业务通知和通知偏好。
- **设备**：登录设备和设备管理。
- **全局搜索**：跨协作内容和业务对象的搜索入口。
- **个人设置**：个人资料和账号相关设置。

### 管理后台

- **企业概览**：企业成员、组织、内容和治理状态的汇总视图。
- **组织架构**：根部门、子部门、部门成员、负责人、部门状态和组织关系维护。
- **成员管理**：成员信息、头像、部门、角色、账号状态和密码管理。
- **用户组**：用户组、直接成员、展开成员和组状态管理。
- **角色与权限**：角色、权限分类、风险等级、权限配置和角色分配。
- **权限治理**：权限排查、风险识别、授权关系和治理操作。
- **知识库管理**：知识库空间、内容治理和管理侧可见性控制。
- **应用治理**：应用对象、应用状态和平台接入治理。
- **批量治理**：批量管理和批量治理任务入口。
- **审计日志**：登录、成员、组织、用户组、角色、权限和业务对象变更记录。
- **系统设置与安全**：平台级设置和安全相关管理入口。

## 核心架构

```text
Browser
  |
  +-- Vite / React Web Application
  |     +-- UserWorkspaceShell
  |     +-- AdminConsoleShell
  |     +-- REST API client
  |     +-- WebSocket client
  |
  +-- Nginx (production reverse proxy)
        +-- /       -> Web static files
        +-- /api/   -> Spring Boot server
        +-- /ws/    -> Spring WebSocket endpoint

Spring Boot server
  +-- PostgreSQL  : transactional data and Flyway migrations
  +-- Redis       : cache and runtime coordination
  +-- MinIO       : file and object storage
```

### 关键设计边界

- 知识库空间模型是知识内容的唯一主模型；旧的独立文档模块不作为单独产品入口维护。
- 知识库内容采用结构化块模型，支持文本、标题、列表、任务、表格、对象引用等内容块。
- 多维表格是独立的协作对象，可在知识库目录中作为引用入口使用。
- 组织架构描述成员归属和组织关系；角色描述可复用的权限集合；用户组描述成员集合。三者在授权时承担不同职责，不互相替代。
- 管理后台的授权判断由后端统一执行，前端页面权限仅用于导航和交互展示控制。
- 实时能力通过 WebSocket 提供，后端端点为 `/ws/events`。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端语言与框架 | TypeScript、React 19、Vite 8 |
| 前端 UI | Ant Design 6、`@ant-design/icons` |
| 前端路由 | React Router 7 |
| 前端状态与数据 | Zustand 5、TanStack Query 5 |
| 前端数据展示 | TanStack Table 8 |
| 内容编辑 | TipTap 3 及其表格、图片、任务列表、拖拽扩展 |
| 前端质量工具 | TypeScript build、ESLint、Playwright |
| 后端语言与框架 | Java 21、Spring Boot 3.5、Spring MVC |
| 安全 | Spring Security、JWT、密码策略和权限治理 |
| 实时通信 | Spring WebSocket |
| 数据访问 | Spring Data JPA、Hibernate |
| 数据库迁移 | Flyway，当前迁移脚本为 `V001` 至 `V049` |
| 关系数据库 | PostgreSQL 16 |
| 缓存与运行时协调 | Redis 7 |
| 文件存储 | MinIO |
| API 文档与运行监控 | springdoc OpenAPI、Spring Boot Actuator、Micrometer Prometheus |
| 后端测试 | JUnit 5、Spring Boot Test、Spring Security Test、Testcontainers PostgreSQL |
| 容器化 | Docker、Docker Compose、Nginx |

## 目录结构

```text
.
├── server/                 # Spring Boot 后端
│   ├── src/main/java/      # API、领域服务、权限、持久化和基础设施
│   ├── src/main/resources/ # 配置和 Flyway 迁移
│   ├── src/test/           # 后端单元测试和集成测试
│   └── Dockerfile          # 后端生产镜像
├── web/                    # React + Vite 前端
│   ├── src/app/             # 路由、应用布局和两套界面壳
│   ├── src/modules/         # 用户工作台和管理后台业务模块
│   ├── e2e/                 # Playwright 端到端测试
│   ├── Dockerfile           # 前端生产镜像
│   └── nginx.conf           # 前端静态资源 SPA 回退配置
├── deploy/                  # 单节点部署配置和运维说明
│   ├── docker-compose.prod.yml
│   ├── .env.prod.example
│   └── nginx/colla.conf
├── desktop/                 # 桌面端试验性工作区
├── mobile/                  # 移动端试验性工作区
├── docker-compose.yml       # 本地 PostgreSQL、Redis、MinIO
├── package.json             # 根目录 pnpm 命令
└── README.md
```

`desktop/` 和 `mobile/` 当前属于预留或试验性工作区，现阶段主要交付物是 Web 应用和 Spring Boot 后端。

## 环境要求

- Docker Desktop，启用 Docker Compose。
- Java 21。
- Maven 3.9 或更高版本。
- Node.js 22 或更高版本；容器和 CI 使用 Node.js 24。
- pnpm 9.4.0。仓库通过 `packageManager` 固定 pnpm 版本。

## 本地开发

### 1. 启动基础设施

在项目根目录执行：

```shell
docker compose up -d postgres redis minio
```

本地服务默认端口：

| 服务 | 地址 | 默认用途 |
| --- | --- | --- |
| PostgreSQL | `localhost:5432` | 业务数据库 |
| Redis | `localhost:6379` | 缓存和运行时协调 |
| MinIO API | `http://localhost:9000` | 文件对象存储 |
| MinIO Console | `http://localhost:9001` | MinIO 管理控制台 |

本地默认数据库和 MinIO 凭据只适用于开发环境：

- PostgreSQL：数据库 `colla_platform`，用户 `colla`，密码 `colla_dev_password`
- MinIO：Access Key `colla_minio`，Secret Key `colla_minio_password`
- 初始管理员：用户名 `admin`，密码 `admin123456`

生产环境不得继续使用这些默认值。

### 2. 启动后端

在单独的终端执行：

```shell
cd server
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。应用会在启动时连接 PostgreSQL、Redis 和 MinIO，并执行 Flyway 数据库迁移。

### 3. 启动前端

在另一个终端执行：

```shell
pnpm install
pnpm web:dev
```

前端默认运行在 `http://localhost:5173`，登录入口为 `http://localhost:5173/login`。

如需覆盖前端服务地址，可在 `web/.env.local` 中配置实际使用的变量：

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_BASE_URL=ws://localhost:8080/ws/events
```

### 常用访问地址

- 用户工作台：`http://localhost:5173/`
- 管理后台：`http://localhost:5173/admin/overview`
- OpenAPI UI：`http://localhost:8080/swagger-ui`
- 应用健康检查：`http://localhost:8080/api/health`
- Spring Boot 健康检查：`http://localhost:8080/actuator/health`

## 构建与测试

### 前端

在项目根目录执行：

```shell
pnpm install --frozen-lockfile
pnpm web:lint
pnpm web:build
```

也可以直接在 `web/` 目录执行对应的 `lint`、`build` 或 `preview` 命令。

### 后端

```shell
cd server
mvn test
mvn -DskipTests package
```

`mvn test` 会运行当前后端测试集；其中使用 Testcontainers 的测试需要 Docker Desktop 正常运行。后端测试日志默认写入 `.local-logs/server-test`，不会进入版本库。

### 生产镜像构建

```shell
docker build -t colla-platform-server:local ./server
docker build -t colla-platform-web:local -f web/Dockerfile .
```

前端镜像会先执行 TypeScript 检查和 Vite 构建，再由 Nginx 提供静态资源；后端镜像会在构建阶段打包 Spring Boot JAR。

## 部署

`deploy/` 提供面向测试环境或小规模生产环境的单节点 Docker Compose 方案，包含：

- PostgreSQL、Redis、MinIO 持久化服务。
- Spring Boot 后端容器。
- Nginx 反向代理和前端静态资源容器。
- `/api/`、`/ws/` 到后端的代理，以及前端 SPA 路由回退。

### 单节点部署

1. 创建生产环境变量文件：

   ```shell
   cp deploy/.env.prod.example deploy/.env.prod
   ```

2. 编辑 `deploy/.env.prod`，至少替换数据库密码、MinIO 密钥、JWT 密钥、初始管理员密码、允许的来源和站点地址。

3. 构建并启动完整服务：

   ```shell
   docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --build
   ```

4. 验证服务状态：

   ```shell
   curl --fail http://localhost/api/health
   curl --fail http://localhost/actuator/health
   ```

默认 HTTP 入口为 `http://localhost`。如需启用 HTTPS，应将证书挂载到 `deploy/certs`，并在 `deploy/nginx/colla.conf` 中补充 HTTPS server 配置。

### 数据与日志

生产 Compose 使用命名卷保存 PostgreSQL、Redis、MinIO 数据和后端日志。部署前应完成数据库和对象存储备份，并在升级后验证：

- `/api/health` 和 `/actuator/health` 正常。
- 登录和管理员初始化配置符合预期。
- 用户工作台和管理后台可以打开。
- 知识库、项目、即时沟通、多维表格、审批和通知等核心入口可正常访问。

单节点方案适合测试或小规模部署；高可用、水平扩展、托管数据库和 Kubernetes 编排不属于当前仓库的默认部署方案。

## 配置说明

### 本地配置

后端默认使用 `local` profile，并从 `server/src/main/resources/application-local.yml` 读取本地依赖配置。可以通过环境变量覆盖数据库、Redis、MinIO、JWT、管理员初始化和上传大小等配置。

### 生产配置

生产 profile 通过环境变量读取配置，主要变量包括：

- `COLLA_DATASOURCE_URL`
- `COLLA_DATASOURCE_USERNAME`
- `COLLA_DATASOURCE_PASSWORD`
- `REDIS_HOST`、`REDIS_PORT`
- `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`
- `JWT_ACCESS_SECRET`、`JWT_REFRESH_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `INIT_ADMIN_USERNAME`、`INIT_ADMIN_PASSWORD`、`INIT_ADMIN_DISPLAY_NAME`、`INIT_ADMIN_EMAIL`
- `MAX_UPLOAD_SIZE_BYTES`

JWT 密钥、数据库密码、对象存储密钥和管理员初始密码必须使用随机高强度值，并通过部署环境的密钥管理方式注入。

## 版本与迁移

数据库模式由 Flyway 管理，迁移文件位于 `server/src/main/resources/db/migration`。应用启动时会校验并执行未应用的迁移；生产环境升级前应先备份数据库，禁止直接修改已经执行过的迁移文件。

当前后端、前端和基础设施按单仓库协同演进。接口、权限和数据模型的变更应同时检查用户工作台与管理后台的影响范围。
