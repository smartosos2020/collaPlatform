---
title: PLATFORM-SCALE-S02-M1 执行报告
status: archived
milestone: PLATFORM-SCALE-S02-M1
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S02-M1 Execution Report

## Scope

本轮建立同一 Spring Boot 构建产物的 `api`、`worker`、`event-gateway`、`maintenance` 四种生产运行角色，以及只允许 local/test 的显式 `combined` 模式。边界在 Bean 创建和 scheduling 启用前生效。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M1-T01 | static | not-required | not-required | no | runtime responsibility inventory |
| PLATFORM-SCALE-S02-M1-T02 | integration | not-required | not-required | no | role parse and startup failure matrix |
| PLATFORM-SCALE-S02-M1-T03 | integration | not-required | not-required | no | conditional bean matrix |
| PLATFORM-SCALE-S02-M1-T04 | integration | not-required | not-required | no | API bean whitelist |
| PLATFORM-SCALE-S02-M1-T05 | integration | not-required | not-required | no | Worker bean whitelist |
| PLATFORM-SCALE-S02-M1-T06 | integration | not-required | not-required | no | Gateway bean whitelist |
| PLATFORM-SCALE-S02-M1-T07 | integration | not-required | not-required | no | Maintenance runner boundary |
| PLATFORM-SCALE-S02-M1-T08 | integration | not-required | not-required | no | scheduling and controller pruning |
| PLATFORM-SCALE-S02-M1-T09 | integration | not-required | not-required | no | actuator and metric metadata |
| PLATFORM-SCALE-S02-M1-T10 | integration | not-required | not-required | no | five-role condition matrix |
| PLATFORM-SCALE-S02-M1-T11 | static | not-required | not-required | no | explicit configuration examples |
| PLATFORM-SCALE-S02-M1-T12 | integration | not-required | not-required | no | targeted tests and architecture gate |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PLATFORM-SCALE-S02-M1-T01 | Done | 混合职责清单记录于本报告和当前架构 |
| PLATFORM-SCALE-S02-M1-T02 | Done | `RuntimeRole`、属性绑定和 fail-closed validator |
| PLATFORM-SCALE-S02-M1-T03 | Done | `ConditionalOnRuntimeRole` 在 Bean 定义阶段判定 |
| PLATFORM-SCALE-S02-M1-T04 | Done | API 无 Worker、通用 WS、知识协同 scheduler 和维护 runner |
| PLATFORM-SCALE-S02-M1-T05 | Done | Worker 只启用 worker/scheduler 边界且移除业务 Controller |
| PLATFORM-SCALE-S02-M1-T06 | Done | Gateway 启用 `/ws/events`，移除业务 Controller 和事件 consumer |
| PLATFORM-SCALE-S02-M1-T07 | Done | Maintenance 执行 Flyway/初始化后关闭上下文 |
| PLATFORM-SCALE-S02-M1-T08 | Done | 全局 `@EnableScheduling` 已移除，非 API Controller 在实例化前裁剪 |
| PLATFORM-SCALE-S02-M1-T09 | Done | Actuator info 和 Micrometer common tags 暴露 role/instance/version/commit |
| PLATFORM-SCALE-S02-M1-T10 | Done | 条件矩阵、失败配置和真实 Bean 注解合同测试 |
| PLATFORM-SCALE-S02-M1-T11 | Done | local/test 显式 combined，prod 强制 `COLLA_RUNTIME_ROLE` |
| PLATFORM-SCALE-S02-M1-T12 | Done | 编译、目标测试、checkpoint 和架构边界通过 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M1-T01 | 职责可定位 | 本报告 `Runtime Responsibility Inventory` | `rg -n "@Scheduled" server/src` 与 WebSocket/runner 清单 | not-required: 静态职责清单无页面行为 | Done |
| PLATFORM-SCALE-S02-M1-T02 | 唯一角色且错误配置失败 | `server/src/main/java/com/colla/platform/config/runtime/RuntimeRole.java` | `RuntimeRoleContractTests` PASS | not-required: 启动配置无页面行为 | Done |
| PLATFORM-SCALE-S02-M1-T03 | 角色条件在定义评估阶段生效 | `ConditionalOnRuntimeRole.java`、`RuntimeRoleCondition.java` | `RuntimeRoleConditionTests` PASS | not-required: Bean 条件无页面行为 | Done |
| PLATFORM-SCALE-S02-M1-T04 | API 禁止后台职责 | `DomainEventWorker`、`WebSocketConfig`、initializers 的角色条件 | `RuntimeRoleBeanContractTests` PASS | not-required: 后端 Bean 白名单无页面行为 | Done |
| PLATFORM-SCALE-S02-M1-T05 | Worker 无业务入口 | `RuntimeRoleWebBoundaryPostProcessor.java` | `RuntimeRoleConditionTests` worker matrix PASS | not-required: Worker 不提供用户 UI | Done |
| PLATFORM-SCALE-S02-M1-T06 | Gateway 只开放 WS 角色入口 | `WebSocketConfig.java` 和 controller pruner | `RuntimeRoleBeanContractTests` gateway contract PASS | not-required: 本轮只验证启动边界 | Done |
| PLATFORM-SCALE-S02-M1-T07 | Maintenance 完成后退出 | `MaintenanceApplicationRunner.java` | `RuntimeRoleBeanContractTests` maintenance contract PASS | not-required: 一次性维护进程无页面 | Done |
| PLATFORM-SCALE-S02-M1-T08 | scheduling 不全局泄漏 | `RuntimeRoleSchedulingConfiguration.java` | `RuntimeRoleBeanContractTests#schedulingIsNotEnabledGlobally` PASS | not-required: scheduler 合同无页面 | Done |
| PLATFORM-SCALE-S02-M1-T09 | 元数据不含密钥 | `RuntimeRoleConfiguration.java` info/tags | `mvn -q -DskipTests compile` PASS | not-required: Actuator 元数据无业务页面 | Done |
| PLATFORM-SCALE-S02-M1-T10 | 五角色正反矩阵 | `RuntimeRoleConditionTests.java` | `mvn -q '-Dtest=RuntimeRole*Tests' test` PASS | not-required: ApplicationContext 矩阵无页面 | Done |
| PLATFORM-SCALE-S02-M1-T11 | 配置示例显式 | `application-local.yml`、`application-test.yml`、`application-prod.yml` | `RuntimeRoleConditionTests#combinedIsRejectedOutsideLocalAndTest` PASS | not-required: 配置示例无页面 | Done |
| PLATFORM-SCALE-S02-M1-T12 | 门禁闭环 | `.local-reports/quality-gate-20260724T014216.md` | `pnpm work:checkpoint -- --goal platform-scale-s02-m1` PASS | not-required: 工程门禁无页面行为 | Done |

## Code Changes

- 后端：新增 runtime role 枚举、条件、配置、Controller 边界裁剪、角色 scheduling、维护退出和元数据。
- 前端：无。
- 数据库：无。
- 脚本：无。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | update | 固化当前运行角色和临时职责归属 |
| `docs/02-roadmap/current-roadmap.md` | update | M1 全任务完成 |
| 本报告 | create | M1 验收证据闭环 |

## Runtime Responsibility Inventory

| Current responsibility | Previous trigger/state | S02 role | Later owner |
| --- | --- | --- | --- |
| HTTP business API and outbox append | MVC Controller and transaction | api/combined | API |
| Domain event polling | `DomainEventWorker @Scheduled` | worker/combined | S03 Worker lease |
| Legacy knowledge autosave/cleanup | knowledge service scheduler and process memory | worker/combined temporarily | S04 removal/Hocuspocus |
| Generic `/ws/events` | Spring WebSocket and process sessions | event-gateway/combined | S04 Redis fanout |
| Flyway, MinIO bucket and initial admin | startup runners | maintenance/combined | Maintenance job |

## Validation

- Backend compile: PASS - `mvn -q -DskipTests compile`.
- Backend tests: PASS - `mvn -q '-Dtest=RuntimeRoleContractTests,RuntimeRoleConditionTests,RuntimeRoleBeanContractTests' test`.
- Architecture: PASS - `pnpm work:checkpoint -- --goal platform-scale-s02-m1` 中 architecture boundaries 通过。
- Frontend build: not-required - 本轮没有前端文件变更。
- Local quality gate: PASS - `.local-reports/quality-gate-20260724T014216.md`.
- Browser smoke: not-required - 本里程碑只调整后端运行角色和 Bean 启动合同，不包含用户可见页面或浏览器交互。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | Worker lease/dead-letter/replay 尚未实现 | non-blocking | PLATFORM-SCALE-S03 |
| N/A | Gateway 多节点 Redis fanout 与旧 Spring 知识协同删除尚未实现 | non-blocking | PLATFORM-SCALE-S04 |
| N/A | 双 API upstream、依赖 readiness 和节点退出实证尚未完成 | non-blocking | PLATFORM-SCALE-S02-M2 到 M4 |

## Next Steps

推进 `PLATFORM-SCALE-S02-M2`，净化 API 角色并建立健康与优雅生命周期。
