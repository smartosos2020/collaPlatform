# PROJECT-PLATFORM-S08-M2 Execution Report

## Scope

PROJECT-PLATFORM-S08-M2-T01 到 PROJECT-PLATFORM-S08-M2-T12

本里程碑激活轻量状态流的绑定快照读取、forward 决策/执行、持久幂等、不可变历史和用户 API。它不实现 M3 的 return/reopen/terminate/restore/correction、存量 backfill 或版本状态映射，也不创建 S09 node instance/token。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M2-T01 | non-core | static | not-required | not-required | No | M1 12 Task、snapshot/schema/report/gap 与本地实现逐项复核 |
| PROJECT-PLATFORM-S08-M2-T02 | non-core | unit | not-required | not-required | No | 只解析绑定 snapshot；initial 与 capability missing 稳定 |
| PROJECT-PLATFORM-S08-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL current-state 唯一初始化、行锁、双版本 CAS 与版本同步 |
| PROJECT-PLATFORM-S08-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | availableActions/execute 共用 decision；批量 state/actor-role 投影 |
| PROJECT-PLATFORM-S08-M2-T05 | core-system | system-real-isolated | not-required | isolated | No | field/participant/space-role/composite guard、required 与隐藏值零披露 |
| PROJECT-PLATFORM-S08-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | expected version/from state/field patch/WorkItem/current state 原子推进 |
| PROJECT-PLATFORM-S08-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | request hash、精确重放、异载荷/stale/并发败者 |
| PROJECT-PLATFORM-S08-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | 单调不可变 history 与脱敏用户投影 |
| PROJECT-PLATFORM-S08-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | activity/audit/two outbox events/receipt 同事务与故障回滚 |
| PROJECT-PLATFORM-S08-M2-T10 | non-core | integration | not-required | not-required | No | 用户 current/history/action 路由及 404/403/409/422 |
| PROJECT-PLATFORM-S08-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | 六身份、跨空间、guard、重放、并发和 outbox 故障注入 |
| PROJECT-PLATFORM-S08-M2-T12 | core-system | system-real-isolated | not-required | isolated | No | 251 实例 current-state SQL plan、limit 50 批量投影与隔离延迟预算 |

## Completed Items

- 复核 M1 定义、schema v1/v2、V091、执行报告与未关闭项，确认没有阻断需 Reopen；M2 直接复用 S06/S07 权威，不查询 current/latest/draft 配置。
- 实现 `WorkItemStateRuntimeAdapter`、`WorkItemStateFlowRepository` 与 PostgreSQL adapter；新 stateFlow WorkItem 唯一初始化 initial state，既有无流/未初始化实例显式返回 capability missing。
- 实现统一 `WorkItemStateFlowDecisionService`，列表/详情和执行共享授权、field/participant/space-role/composite guard 与 required-field 判断；M2 只开放 forward 动作。
- 实现原子动作命令：WorkItem/current state 行锁与版本前置、用户 field patch、配置 patch、字段 canonical/projection、current-state CAS、history/activity/audit/outbox/receipt 同事务。
- 实现持久 request receipt 和 canonical hash；相同 ID/输入精确语义重放，异载荷、stale 与并发败者不产生重复历史、活动或事件。
- 交付用户 current/actions、history 和 execute API；移除用户 runtime 中的原始 `stateFlow` 策略正文，错误合同稳定映射 404/403/409/422。
- 列表/查询批量读取 current states 与 actor participant roles，并按绑定 version/hash 缓存 snapshot 解释；真实 PostgreSQL 验证 current-state 投影索引和代表性延迟预算。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M2-T01 | M1 12 项可追溯且不以文档替代实现 | M1 report、V091、adapter/validator/preset 与 S07 runtime 审计 | local diff + focused M1 regression | Not required：后端里程碑 | Done |
| PROJECT-PLATFORM-S08-M2-T02 | 只解释绑定版本/hash；无流显式缺失 | `WorkItemStateRuntimeAdapter` + `PublishedSnapshotAdapter.requireComplete` | `WorkItemStateRuntimeAdapterTests` PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T03 | 唯一 current state、锁/CAS、版本不漂移 | `WorkItemStateFlowRepository`、`lock/workflowUpdate/alignWorkItemVersion` | foundation + service PostgreSQL tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T04 | decision 共用、排序/策略版本/批量稳定 | `WorkItemStateFlowDecisionService`、batch state/roles、binding cache | decision + list integration tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T05 | 三类 guard/required 与最小披露 | recursive guard evaluator、generic denied errors、raw policy removal | decision/access/cross-space/history tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T06 | patch/WorkItem/state/version 原子提交 | `WorkItemStateFlowService.execute` + field codec/projection + CAS | forward/stale integration tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T07 | receipt/hash/replay/conflict/concurrency | workflow command repository + canonical request hash | exact replay + concurrent one-winner tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T08 | history 单调不可变且无敏感输入 | V091 trigger + `pageHistory` safe DTO | history count/immutability/hidden-value tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T09 | activity/audit/outbox/receipt 同事务 | `WorkItemWorkflowEvent` + transactional command | two-event count + DB trigger fault rollback PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T10 | 用户路由与 404/403/409/422 稳定 | `UserWorkItemStateFlowController`、exception handler | `WorkItemStateFlowApiContractTests` PASS | Not required：无前端 UI | Done |
| PROJECT-PLATFORM-S08-M2-T11 | 身份/并发/重放/guard/故障/跨空间完整 | service integration matrix + decision unit | PostgreSQL 16 Testcontainers focused suite PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M2-T12 | plan、上界和延迟可复现 | current-state projection index + limit 200 + binding cache | 251 rows/limit 50/`idx_project_work_item_current_states_projection`/<5s PASS | Not required | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/{domain,runtime,application}/WorkItemState*`
- `server/src/main/java/com/colla/platform/modules/project/infrastructure/{WorkItemStateFlowRepository,JdbcWorkItemStateFlowRepository,WorkItemRepository,JdbcWorkItemRepository}.java`
- `server/src/main/java/com/colla/platform/modules/project/{api/UserWorkItemStateFlowController.java,contract/WorkItemWorkflowEvent.java}`
- `server/src/main/java/com/colla/platform/modules/project/application/{WorkItemService,WorkItemRuntimeProjection}.java`
- `server/src/test/java/com/colla/platform/modules/project/{application,runtime,api,infrastructure}/**WorkItemState*`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture,platform-module-contracts,platform-object-model,event-side-effect-matrix}.md`

## Validation

- Backend tests: `mvn -q -Dtest=WorkItemServiceIntegrationTests,WorkItemStateFlowFoundationIntegrationTests` 与 Stage 定向套件 PASS；compile/test-compile PASS。
- Focused unit/API contract：runtime adapter、统一 decision/guard/required、用户路由与错误映射，PASS。
- Real system integration：Spring Boot + PostgreSQL 16 Testcontainers，覆盖 forward、绑定 snapshot、exact replay、stale、六身份/跨空间、并发 one-winner、不可变历史、两个 outbox event 和数据库 trigger 故障整体回滚，PASS。
- Performance evidence：251 个同绑定实例、状态 `open`、limit 50；`EXPLAIN (ANALYZE, BUFFERS)` 命中 `idx_project_work_item_current_states_projection`，批量 state/actor-role 查询与绑定缓存生效，隔离预算 `<5s`，PASS。不是生产容量结论。
- Frontend build: Not required；M2 未修改 `web/**`，成员执行 UI 属于 M4。
- Local quality gate: `quality-gate-20260726T143507.md` checkpoint PASS；finish 将生成新的 Stage 证据。
- Browser smoke: Not required；M2 交付后端用户 API/DTO，没有新增或修改浏览器 UI，不能用 mock 浏览器冒充 M4 真实用户流。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M3 | return/reopen/terminate/restore/correction、archive 协同、存量 backfill、版本 state-key mapping 与 recovery 尚未实现 | 不阻断 M2；M2 明确只执行 forward，旧实例不静默初始化 | M3 |
| PROJECT-PLATFORM-S08-M4 | 状态配置器、成员执行 UI、真实浏览器与 route-final 尚未实现 | 不阻断 M2；不能宣称 S08 Stage 完成 | M4 |
| PROJECT-PLATFORM-S08-M4-T11 | node instance/token、并行、汇聚和会签不属于轻量状态流 | M2 Repository/事件不能被 S09 当作 node runtime | S09 准入复核 |

## Next Steps

- 从 PROJECT-PLATFORM-S08-M3-T01 冻结 return/reopen/terminate/restore/correction 语义，再实现显式命令、存量初始化与版本映射；不得用直接状态更新绕过历史和 guard。
- 保持 S09 Planned，不创建 node token/instance，也不把当前 state 投影冒充复杂节点流。
