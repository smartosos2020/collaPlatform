# PROJECT-PLATFORM-S10-M2 Execution Report

## Scope
PROJECT-PLATFORM-S10-M2-T01 到 PROJECT-PLATFORM-S10-M2-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M2-T01 | non-core | static | not-required | not-required | No | M1 报告、snapshot v4、V097 与本地实现逐项复核 |
| PROJECT-PLATFORM-S10-M2-T02 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL canonical endpoint、唯一活动边与稳定 projection |
| PROJECT-PLATFORM-S10-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | create/withdraw/restore、双 expected version 与 receipt 精确重放 |
| PROJECT-PLATFORM-S10-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | 六身份、双端授权、跨空间与最小披露 |
| PROJECT-PLATFORM-S10-M2-T05 | core-system | system-real-isolated | not-required | isolated | No | normal 有向/无向单事实正反向列表 |
| PROJECT-PLATFORM-S10-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | parent 基数、自环、祖先环与并发写 |
| PROJECT-PLATFORM-S10-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | dependency/blocking 方向归一与并发成环 |
| PROJECT-PLATFORM-S10-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | archive restrict/detach/retain-history 与 restore |
| PROJECT-PLATFORM-S10-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | edge/history/activity/audit/receipt/outbox 原子闭包 |
| PROJECT-PLATFORM-S10-M2-T10 | core-system | system-real-isolated | not-required | isolated | No | 用户 list/detail/capability/create/withdraw/restore API 服务链 |
| PROJECT-PLATFORM-S10-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 六身份、跨空间、并发、故障回滚集成测试 |
| PROJECT-PLATFORM-S10-M2-T12 | core-system | system-real-isolated | not-required | isolated | No | bounded list、索引计划、图锁范围与 V001-V098 migration |

## Completed Items
- 复核 M1 的 12 项定义、snapshot v4、V097 schema 与执行证据，没有发现需要 Reopen 的阻断。
- 激活 relation runtime adapter、Repository 和用户 API；相同语义边只有一个 canonical 活动事实，正反向显示不复制关系。
- create/withdraw/restore 使用双端稳定锁序、space/key advisory lock、双 expected version 与 request hash receipt，精确重放不重复产生副作用。
- normal、parent-child、dependency、blocking 共享 published definition 决策，完成类型矩阵、self、基数、重复和并发环控制。
- archive 接入 restrict/detach/retain-history；restore 不重建已撤销边，规范 WorkItem 的历史硬删除保持失败关闭。
- mutation 与 immutable history、双端 activity、audit、completed receipt、最小 outbox 同事务提交，并提供有界 list/detail/capability/command DTO。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M2-T01 | M1 12 项逐项可追溯且无未关闭阻断 | M1 report、snapshot v4、V097、target/current contracts | 本地代码/文档复核 | 不需要：审计任务 | Done |
| PROJECT-PLATFORM-S10-M2-T02 | canonical endpoint、唯一活动边和隔离正确 | runtime models、Repository、V098 canonical check | relation service PostgreSQL integration | 不需要：后端运行时 | Done |
| PROJECT-PLATFORM-S10-M2-T03 | 精确重放；stale/并发败者不追加事实 | service transaction、stable locks、command receipt | create/withdraw/restore/replay assertions | 不需要：后端命令 | Done |
| PROJECT-PLATFORM-S10-M2-T04 | projection/execute 共用双端授权 | `WorkItemRelationAccessDecisionService` | 六身份、跨空间、hidden/forbidden assertions | 不需要：服务端授权 | Done |
| PROJECT-PLATFORM-S10-M2-T05 | 单事实生成稳定正反向视图 | projection SQL lateral definition binding | directed/undirected forward/reverse assertions | 不需要：JSON DTO | Done |
| PROJECT-PLATFORM-S10-M2-T06 | parent 基数与并发环失败关闭 | graph lock、cardinality counts、recursive path query | parent cardinality/self/cycle/concurrency assertions | 不需要：数据库约束 | Done |
| PROJECT-PLATFORM-S10-M2-T07 | dependency/blocking 语义和环一致 | normalized graph validation | dependency concurrent cycle + blocking reverse-cycle assertions | 不需要：数据库约束 | Done |
| PROJECT-PLATFORM-S10-M2-T08 | archive 策略确定且 restore 不暗中恢复 | `beforeEndpointArchive`、delete fail-close guard | detach/restrict/retain-history/restore assertions | 不需要：生命周期服务 | Done |
| PROJECT-PLATFORM-S10-M2-T09 | 全部副作用同成同败 | history/activity/audit/outbox/receipt transaction | row-count、payload 与 rollback assertions | 不需要：事务证据 | Done |
| PROJECT-PLATFORM-S10-M2-T10 | 用户路由、错误语义、有界查询稳定 | controller、DTO、exception mapping、joined projection | service/API compile + list/capability integration | 不需要：M2 无 UI | Done |
| PROJECT-PLATFORM-S10-M2-T11 | 身份/隔离/幂等/并发/原子性回归通过 | dedicated integration suite | `WorkItemRelationServiceIntegrationTests` PASS | 不需要：真实 PostgreSQL 隔离 | Done |
| PROJECT-PLATFORM-S10-M2-T12 | 上界、锁与 SQL plan 可复现 | list max 200、graph advisory lock、endpoint indexes | EXPLAIN 使用索引；V001-V098 fresh migration PASS | 不需要：性能基座 | Done |

## Code Changes
- 新增 `WorkItemRelationRuntimeModels`、`WorkItemRelationRuntimeAdapter`、`WorkItemRelationAccessDecisionService` 与 `WorkItemRelationService`。
- 新增 `WorkItemRelationRepository` / `JdbcWorkItemRelationRepository`，实现 canonical projection、事务锁、幂等回执、递归 path、history 和有界查询。
- 新增 `UserWorkItemRelationController`、API DTO 与稳定异常映射，并把 archive lifecycle 接入 `WorkItemService`。
- 新增 V098 canonical endpoint/环查询索引迁移，更新 migration matrix 和 table-owner 范围。
- 新增 `WorkItemRelationServiceIntegrationTests`，覆盖 PostgreSQL 真实服务流、六身份、并发、生命周期、原子性与执行计划。
- 同步 roadmap、target/current architecture、module/object/event 合同。

## Validation
- Backend tests: `mvn -q -DskipTests compile` PASS；`mvn -q -DskipTests test` test-compile PASS；`WorkItemRelationServiceIntegrationTests` PostgreSQL 真实服务流 PASS；`WorkItemRelationFoundationIntegrationTests` 与单独重跑的 `WorkItemConfigurationMigrationMatrixIntegrationTests` V001-V098 PASS（组合首跑曾遇 Testcontainers 冷启动探针瞬时超时，无测试断言失败）。
- Frontend build: Not required；M2 未修改 Web，关系控件和层级 UI 属于 M3-M5。
- Local quality gate: `git diff --check`、architecture contracts/boundaries、work plan/checkpoint PASS；fresh checkpoint 报告 `.local-reports/quality-gate-20260727T023401.md`。
- Browser smoke: Not required；M2 只激活后端 relation command/projection API，没有用户浏览器入口。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S10-M3-T01 复核 M2 edge authority、并发合同与 projection；以 relation edge 为唯一结构事实实现可重建 hierarchy closure，不直接写 projection。
