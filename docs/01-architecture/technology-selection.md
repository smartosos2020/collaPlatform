---
title: 当前技术选型
status: active
last_code_check: 2026-06-15
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
| 数据库迁移 | Flyway | `server/src/main/resources/db/migration/V001...V022` |
| Redis | Redis 7 | `docker-compose.yml`, `spring-boot-starter-data-redis` |
| 对象存储 | MinIO | `docker-compose.yml`, `minio` dependency |
| OpenAPI | springdoc-openapi | `springdoc-openapi-starter-webmvc-ui` |
| 测试 | JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers dependency | `server/pom.xml`, `server/src/test/java` |

## 前端 Web

| 方向 | 当前选型 | 代码依据 |
| --- | --- | --- |
| 构建 | Vite 8 | `web/package.json` |
| 包管理 | pnpm 9.4.0 | 根 `package.json` |
| UI | React 19 + Ant Design 6 | `web/package.json` |
| 路由 | React Router 7 | `web/src/app/router.tsx` |
| 服务端状态 | TanStack Query 5 | `web/package.json` 和各模块页面 |
| 本地状态 | Zustand 5 | `authStore.ts` |
| 表格引擎 | TanStack Table 8 | `web/package.json` |
| 富文本基础 | TipTap 3 | `web/package.json` |
| 图标 | `@ant-design/icons` | `web/package.json` |
| 质量 | ESLint, TypeScript build, Vite build | `pnpm web:lint`, `pnpm web:build` |

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

根 `package.json` 当前提供：

| 命令 | 用途 |
| --- | --- |
| `pnpm audit:snapshot` | 生成 AI 审计快照 |
| `pnpm work:start` | AI 工作循环开始 |
| `pnpm work:checkpoint` | AI 工作循环检查点 |
| `pnpm work:finish` | AI 工作循环结束 |
| `pnpm verify` | 快速质量门禁 |
| `pnpm verify:full` | 完整质量门禁 |
| `pnpm web:dev` | 前端开发服务 |
| `pnpm web:build` | 前端构建 |
| `pnpm web:lint` | 前端 lint |

## 当前质量门禁

`scripts/ai-quality-gate.ps1` 的 quick 模式当前覆盖：

- 工具链检查。
- Docker 依赖启动。
- 后端测试。
- 前端 lint。
- 前端构建。
- 前端 chunk budget。
- 路由懒加载检查。
- Mockito javaagent 配置检查。
- 敏感数据扫描。
- Flyway migration 顺序检查。
- 生成物扫描。
- 实现标记库存检查。

## 当前约束

- 仍是单体优先架构，模块按包拆分，后续可按模块边界演进。
- 桌面端和移动端不是当前正式交付物。
- 本地密钥和默认密码只用于本地开发环境。
