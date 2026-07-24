---
title: PLATFORM-SCALE-S02-M3 执行报告
status: archived
milestone: PLATFORM-SCALE-S02-M3
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S02-M3 Execution Report

## Scope

完成双 API 生产编排、Nginx upstream、readiness、优雅与强制退出、恢复接流、单 API 回退和真实跨模块浏览器流程。验证环境使用隔离 Compose project `colla-s02-m3`、隔离数据库和端口。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M3-T01 | static | not-required | not-required | no | Compose、Nginx、端口、实例和共享依赖合同 |
| PLATFORM-SCALE-S02-M3-T02 | integration | not-required | not-required | no | 双 API 与四角色容器启动 |
| PLATFORM-SCALE-S02-M3-T03 | integration | not-required | not-required | no | 双 upstream 分流、readiness 和 Actuator 隐藏 |
| PLATFORM-SCALE-S02-M3-T04 | integration | not-required | not-required | no | graceful shutdown、超时和 retry 合同 |
| PLATFORM-SCALE-S02-M3-T05 | integration | not-required | not-required | no | API 节点切换后共享业务事实保持 |
| PLATFORM-SCALE-S02-M3-T06 | integration | not-required | not-required | no | 双 API 独立连接池和数据库预算 |
| PLATFORM-SCALE-S02-M3-T07 | integration | not-required | not-required | no | 连续请求命中两个实例且产品响应不泄露拓扑 |
| PLATFORM-SCALE-S02-M3-T08 | integration | not-required | not-required | no | api-a 优雅停止时 api-b 继续服务 |
| PLATFORM-SCALE-S02-M3-T09 | integration | not-required | not-required | no | api-b 强制终止、摘除、恢复并重新接流 |
| PLATFORM-SCALE-S02-M3-T10 | integration | not-required | not-required | no | 单 API 回退和双节点恢复 |
| PLATFORM-SCALE-S02-M3-T11 | e2e-real-isolated | real | isolated | no | 登录、项目、知识、Base、消息、搜索和权限路线 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PLATFORM-SCALE-S02-M3-T01 | Done | `deploy/docker-compose.prod.yml` 和 `deploy/nginx/colla.conf` |
| PLATFORM-SCALE-S02-M3-T02 | Done | api-a、api-b、worker、event-gateway、maintenance 实际容器 |
| PLATFORM-SCALE-S02-M3-T03 | Done | 双 upstream、readiness probe 和 Nginx Actuator 404 |
| PLATFORM-SCALE-S02-M3-T04 | Done | Spring graceful shutdown 与 Nginx retry/timeout 合同 |
| PLATFORM-SCALE-S02-M3-T05 | Done | 双节点真实跨模块读写和节点退出后续用 |
| PLATFORM-SCALE-S02-M3-T06 | Done | API 12/2、Worker 6/1、Gateway 4/1 连接池配置 |
| PLATFORM-SCALE-S02-M3-T07 | Done | Nginx upstream IP 与 request id 日志分布 |
| PLATFORM-SCALE-S02-M3-T08 | Done | `dual-api-smoke.mjs` graceful stop 场景 |
| PLATFORM-SCALE-S02-M3-T09 | Done | `dual-api-smoke.mjs` kill/recovery 场景 |
| PLATFORM-SCALE-S02-M3-T10 | Done | `dual-api-smoke.mjs` single-node rollback 场景 |
| PLATFORM-SCALE-S02-M3-T11 | Done | Playwright route-final 和构建门禁 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M3-T01 | 双实例合同无歧义 | Compose 服务、Nginx upstream、独立 instance id | `DualApiDeploymentContractTests` PASS | not-required: 部署合同无产品页面 | Done |
| PLATFORM-SCALE-S02-M3-T02 | 同镜像双 API 共享依赖 | `api-a`、`api-b` 均使用 api role 且无业务数据卷 | 隔离栈四运行角色全部 healthy | not-required: 容器拓扑无产品页面 | Done |
| PLATFORM-SCALE-S02-M3-T03 | 两健康节点接流且健康端点不外露 | `least_conn` upstream、readiness probe、Actuator 404 | 双节点日志分布和 Compose health PASS | not-required: 入口代理合同由真实 HTTP 验证 | Done |
| PLATFORM-SCALE-S02-M3-T04 | 摘流、停机和重试不重复非幂等请求 | graceful shutdown、30 秒预算、非幂等不重试 | graceful stop 场景 PASS | not-required: 生命周期由自动故障测试验证 | Done |
| PLATFORM-SCALE-S02-M3-T05 | 节点退出不丢共享事实 | API 无本地 session/权限/业务事实，依赖共享 PostgreSQL/Redis/MinIO | 节点切换后跨模块请求和 route-final 持续成功 | not-required: 共享事实由真实 API 与 T11 浏览器路线共同验证 | Done |
| PLATFORM-SCALE-S02-M3-T06 | 连接池预算固定 | Compose role-specific Hikari limits | `docker compose config --quiet` PASS | not-required: 数据库预算无页面 | Done |
| PLATFORM-SCALE-S02-M3-T07 | 请求可按实例追踪且产品响应无拓扑 | Nginx upstream、request id 和实例结构化日志 | 双节点分布断言及产品响应检查 PASS | not-required: 实例追踪由代理日志和 API 响应验证 | Done |
| PLATFORM-SCALE-S02-M3-T08 | 优雅停止 api-a 时新请求继续 | smoke graceful stop/start | `node deploy/smoke/dual-api-smoke.mjs` PASS | not-required: 故障动作由真实 API 自动验证 | Done |
| PLATFORM-SCALE-S02-M3-T09 | 强制终止后摘除并恢复接流 | smoke kill/wait/recovery | 同一 smoke 强制终止与恢复 PASS | not-required: 故障动作由真实 API 自动验证 | Done |
| PLATFORM-SCALE-S02-M3-T10 | 单节点回退不改变 schema 或角色 | 只停止 api-b，api-a 继续 api role 服务 | 同一 smoke 单节点回退与恢复 PASS | not-required: 回退操作无独立产品页面 | Done |
| PLATFORM-SCALE-S02-M3-T11 | 真实产品路线和影响范围门禁通过 | route-final 稳定角色定位与单场景预算 | 后端定向测试、Web build、Compose config PASS | real: Playwright 1 passed，登录、项目、知识、Base、消息、搜索和事项页面通过 | Done |

## Code Changes

- 部署：生产 Compose 增加 maintenance、双 API、Worker、Event Gateway 和双 collaboration 实例，API 关闭 Flyway 并设置独立连接池。
- 入口：Nginx API upstream 使用两个节点，记录 upstream/request id，配置健康失败摘除和安全 retry。
- 后端：维护角色在 Ready 事件返回后正常退出；健康子路径匿名放行但其他 Actuator 仍受保护；生产镜像构建使用 Maven 缓存并跳过测试编译。
- 自动化：新增双 API 分流、优雅退出、强制退出、恢复和单节点回退脚本；修正 route-final 的稳定可访问定位。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | update | 记录 M3 已验证的双 API 当前事实 |
| `docs/02-roadmap/current-roadmap.md` | update | M3 全任务完成 |
| 本报告 | create | M3 逐任务验收与真实环境证据 |

## Validation

- Backend tests: PASS - `mvn -q '-Dtest=RuntimeRoleBeanContractTests,RuntimeRoleHealthIndicatorTests,DualApiDeploymentContractTests' test`.
- Production compose: PASS - `docker compose -f deploy/docker-compose.prod.yml config --quiet`.
- Maintenance: PASS - Flyway 65 个迁移校验后以状态 0 退出。
- Runtime roles: PASS - api-a、api-b、worker、event-gateway、collaboration-a、collaboration-b 和共享依赖全部 healthy。
- Dual API smoke: PASS - 分流、优雅退出、强制退出、恢复接流和单节点回退。
- Frontend build: PASS - `pnpm web:build`.
- Browser smoke: PASS - real isolated `cross-module-route-final.spec.ts`, 1 passed。
- Local quality gate: PASS - checkpoint report `.local-reports/quality-gate-20260724T024503.md`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M4 | 跨节点撤销、幂等、上传和初始化故障矩阵尚未收口 | non-blocking for M3 | PLATFORM-SCALE-S02-M4 |
| N/A | Worker 多实例 lease/dead-letter/replay 尚未交付 | non-blocking | PLATFORM-SCALE-S03 |
| N/A | Event Gateway 多节点 Redis fanout 尚未交付 | non-blocking | PLATFORM-SCALE-S04 |

## Next Steps

推进 `PLATFORM-SCALE-S02-M4` 的认证、幂等、上传、初始化与依赖故障多实例复验。
