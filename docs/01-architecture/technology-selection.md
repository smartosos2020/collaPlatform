---
title: 当前技术选型
status: active
last_code_check: 2026-07-24
---

# 当前技术选型

本文档只记录当前代码实际采用的技术栈。历史选型草案已归档到 `docs/99-archive/old-drafts/technology-selection.md`。

## 后端

| 方向 | 当前选型 | 代码依据 |
| --- | --- | --- |
| 语言 | Java 21 | `server/pom.xml` 的 `java.version` |
| 框架 | Spring Boot 3.5.15 | `spring-boot-starter-parent` |
| Web | Spring MVC | `spring-boot-starter-web` |
| WebSocket | Spring WebSocket | `spring-boot-starter-websocket`, `/ws/events` |
| 安全 | Spring Security + JWT | `SecurityConfig`, `JwtTokenService` |
| 数据库 | PostgreSQL 16 | `docker-compose.yml`, Flyway migrations |
| 数据库迁移 | Flyway | `server/src/main/resources/db/migration/V001...V065` |
| Redis | Redis 7 | `docker-compose.yml`, Spring Data Redis, collaboration Redis extension |
| 对象存储 | MinIO | `docker-compose.yml`, `minio` dependency |
| OpenAPI | springdoc-openapi | `springdoc-openapi-starter-webmvc-ui` |
| 测试 | JUnit 5、Spring Boot Test、Spring Security Test、Testcontainers PostgreSQL | `server/pom.xml`, `server/src/test/java`, `application-test.yml` |

S03 类型配置使用 PostgreSQL 关系约束承载 workspace/space 归属、唯一键和生命周期，配置骨架使用规范 JSONB + SHA-256 hash。published/superseded 不可变与系统预置保护由数据库触发器作为最终防线；Java 领域服务负责可操作错误、幂等、审计/outbox 和乐观并发。S04-M1-M3 动态字段继续沿用“关系身份 + 规范 JSONB + capability typed projection”：字段和稳定选项使用复合隔离关系行，required/default/rules/typeConfig 使用可版本化规范 JSONB/hash，服务端 registry 统一提供 storage、value/type-config schema、operator、filter/sort/index、options/rule 和引用策略能力。复杂引用不复制跨模块快照，写入时通过既有 identity/file/platform-object/type Repository 与 resolver 校验当前事实；不按字段动态建列，也不在 S07 前创建工作项实例或字段值存储。

S04-M4 前端继续使用 React、TypeScript、Ant Design 与 TanStack Query，不引入 schema-form 或重型低代码依赖。字段类型选择器和专属表单按服务端 descriptor/capability 渲染，缓存键显式包含 space/type/field，写操作使用服务端 `availableActions`、乐观版本和失败回滚；Playwright 在真实隔离环境覆盖六类身份和 1366/1440/窄屏。

S04-M5 保持上述选型并完成迁移与规模复核：Flyway 可从 V063 保留 legacy sentinel 数据升级至 V065，重复 migrate 为零变更；字段配置规模使用关系索引和规范 JSONB/hash 承载，120 字段、2400 选项目录预算为 3 秒。S05/S06 继续复用关系 ID、规范 JSON 和不可变版本，不引入按字段动态 DDL；10 万工作项查询只能在 S07 运行时与 S13 查询层具备后再建立生产 SLO。

## 前端 Web

| 方向 | 当前选型 | 代码依据 |
| --- | --- | --- |
| 构建 | Vite 8 | `web/package.json` |
| 包管理 | pnpm 9.4.0 | 根 `package.json` |
| UI | React 19.2 + Ant Design 6.4 | `web/package.json` |
| 路由 | React Router 7.17 | `web/src/app/router.tsx` |
| 服务端状态 | TanStack Query 5 | `web/package.json` 和各模块页面 |
| 本地状态 | Zustand 5 | `authStore.ts` |
| 表格引擎 | TanStack Table 8 | `web/package.json` |
| 结构化块编辑基础 | TipTap 3.26 | `web/package.json`, `web/src/modules/knowledgeBases/content` |
| 图标 | `@ant-design/icons` | `web/package.json` |
| 质量 | ESLint, TypeScript build, Vite build | `pnpm web:lint`, `pnpm web:build` |

## Knowledge Collaboration Runtime

| 方向 | 当前选型 | 代码依据 |
| --- | --- | --- |
| Runtime | Node.js 22 | root `Dockerfile`, `collaboration/package.json` |
| Collaboration server | Hocuspocus 4.4 | `collaboration/src/server.js` |
| CRDT | Yjs 13.6 | collaboration and web dependencies |
| Cross-node rooms | Hocuspocus Redis extension | `collaboration/src/server.js` |
| Durable recovery | PostgreSQL Yjs snapshot and update log | Flyway V052, knowledge collaboration gateway |
| Browser transport | Hocuspocus provider over WebSocket | realtime collaboration hook |

## 本地依赖

`docker-compose.yml` 当前提供：

- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`
- MinIO API：`localhost:9000`
- MinIO Console：`localhost:9001`

后端默认本地配置来自 `application-local.yml`：

- datasource：`jdbc:postgresql://localhost:5432/colla_platform`
- username：`colla`
- password：`colla_dev_password`
- MinIO access key：`colla_minio`
- MinIO secret key：`colla_minio_password`

## 脚本入口

根 `package.json` 提供前端、质量、运维和知识库合同检查入口；具体实现位于已跟踪的 `scripts/` 与 `deploy/scripts/`。

| 命令 | 用途 |
| --- | --- |
| `pnpm web:dev` | 前端开发服务 |
| `pnpm web:build` | 前端构建 |
| `pnpm web:lint` | 前端 lint |

完整脚本入口和验证层级见 `scripts/README.md`。本地运行产生的日志、报告缓存、测试账号和备份仍保存在忽略目录中，不应提交。

## 当前质量门禁

`pnpm verify` 根据 validation profile 执行跨平台门禁：

- 工具链检查。
- Docker 依赖启动。
- light/quick 默认后端编译；stage 可指定本轮目标测试；route-final 才执行完整后端测试。
- 前端 lint。
- 前端构建。
- 前端 chunk budget。
- 路由懒加载检查。
- Mockito javaagent 配置检查。
- 敏感数据扫描。
- Flyway migration 顺序检查。
- 知识命名守卫。
- 生成物扫描。
- 实现标记库存检查。

## 当前约束

- 仍是单体优先架构，模块按包拆分，后续可按模块边界演进。
- PWA 和 Electron 仅是基础/试验能力，正式桌面端和原生移动端不是当前交付物。
- 本地密钥和默认密码只用于本地开发环境。
