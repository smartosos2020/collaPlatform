# PROJECT-PLATFORM-S09-M3 Execution Report

## Scope

PROJECT-PLATFORM-S09-M3-T01 到 PROJECT-PLATFORM-S09-M3-T12。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M3-T01 | non-core | static | not-required | not-required | No | M2 runtime、责任边界、公共 file/object port 与阻断复核 |
| PROJECT-PLATFORM-S09-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | frozen form policy、hidden 零披露和 WorkItem patch |
| PROJECT-PLATFORM-S09-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | frozen candidate、active member、空候选恢复与角色漂移 |
| PROJECT-PLATFORM-S09-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | claim/delegate/transfer/complete/close 并发单事实 |
| PROJECT-PLATFORM-S09-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | FileAccess/PlatformObjectRegistry 实鉴权与不可变 artifact |
| PROJECT-PLATFORM-S09-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | UTC elapsed planned/due/timeout 与幂等 due sweep |
| PROJECT-PLATFORM-S09-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | field/artifact/task/token/receipt 全提交或全回滚 |
| PROJECT-PLATFORM-S09-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | 最小 task/due event、delivery receipt 与永久失败 |
| PROJECT-PLATFORM-S09-M3-T09 | core-system | system-real-isolated | not-required | isolated | No | task inbox/context 聚合、稳定游标与服务端投影 |
| PROJECT-PLATFORM-S09-M3-T10 | core-system | system-real-isolated | not-required | isolated | No | 六身份、hidden、对象越权、并发、重放与事务负例 |
| PROJECT-PLATFORM-S09-M3-T11 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL EXPLAIN、硬上界与公共端口批量调用 |
| PROJECT-PLATFORM-S09-M3-T12 | non-core | integration | not-required | not-required | No | 架构/事件/对象/路线文档与 stage checkpoint |

M3 是后端协作里程碑；成员视觉执行面仍由 M5 `route-final` 验收，因此本里程碑不伪造浏览器证据。

## Completed Items

- 节点配置新增冻结 form/assignment/artifact/schedule 合同，未知动态规则、隐藏字段提升、非法文件/对象策略和未来时间语义均失败关闭。
- task 创建时冻结显式用户、空间角色、事项参与者角色和字段参与者产生的 active member 集合；空候选保留可恢复事实，离场或后续入组不会漂移 all/quorum 阈值。
- single/any task 支持 claim/delegate/transfer/complete/submit；并发写仍由 task/instance/WorkItem version 与事务 receipt 产生单一事实。
- V095 增加 task candidate/form/artifact/time 快照、不可变 task artifact、file/object 部分唯一索引、到期与收件箱索引及扩展历史/命令约束。
- submit 在同一事务完成节点 form patch、字段 canonicalization/projection、FileAccess/PlatformObjectRegistry 鉴权、artifact 追加、task/token/instance/WorkItem 推进、history/activity/audit/outbox/receipt。
- 新增成员 task inbox、task context 和 due processing API；hidden 字段及原始 nodeFlow 策略不进入用户 runtime。
- `node_task.lifecycle` v1 与 `node_workflow.changed` v1 由 replay-safe consumer contract 校验；消费者不读取 project 私表。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M3-T01 | M2/边界/阻断复核 | 独立 node repository；只依赖 file/platform/event/audit 公共端口 | M2 六条回归 | Not required：服务端复核 | Done |
| PROJECT-PLATFORM-S09-M3-T02 | form 策略与 hidden 零披露 | frozen `form_snapshot`、task context 投影、runtime 移除 `nodeFlow` | collaboration integration + validator tests | Not required：M5 承接 UI | Done |
| PROJECT-PLATFORM-S09-M3-T03 | 受控候选解析 | frozen `candidate_user_ids`、active member 交集、`assignment_empty` | 六身份与冻结候选并发测试 | Not required：后端决策 | Done |
| PROJECT-PLATFORM-S09-M3-T04 | task 生命周期 | claim/delegate/transfer/complete/submit + CAS | concurrent claim one-winner | Not required：后端事务 | Done |
| PROJECT-PLATFORM-S09-M3-T05 | 公共交付物端口 | immutable artifact table；FileAccess/ObjectRegistry only | real project_space object authorization | Not required：公共端口集成 | Done |
| PROJECT-PLATFORM-S09-M3-T06 | planned/due/timeout | UTC elapsed instants、bounded due sweep、timed_out fact；pause 明示 unsupported | PostgreSQL due event integration | Not required：后端时间合同 | Done |
| PROJECT-PLATFORM-S09-M3-T07 | 原子提交 | single Spring transaction + WorkItem/task/instance expected versions | field/object submit and exact replay | Not required：系统事务证据 | Done |
| PROJECT-PLATFORM-S09-M3-T08 | task/due event | minimal v1 contracts + durable delivery handler | consumer contract tests | Not required：事件合同 | Done |
| PROJECT-PLATFORM-S09-M3-T09 | inbox/context DTO | stable cursor, hard limit 200, aggregate query, server actions | inbox/context integration | Not required：M5 承接 UI | Done |
| PROJECT-PLATFORM-S09-M3-T10 | isolation/fault/concurrency | hidden zero disclosure, public object auth, duplicate artifact constraints, rollback | unit + Testcontainers suite | Not required：真实隔离后端 | Done |
| PROJECT-PLATFORM-S09-M3-T11 | representative budget | open-task, assignee, candidate and due indexes; bounded artifact/file batches | EXPLAIN regression and hard limits | Not required：容量边界测试 | Done |
| PROJECT-PLATFORM-S09-M3-T12 | docs/checkpoint | architecture/roadmap/report/table-owner synchronized | stage quality gate | Not required：治理闭环 | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/{application,api,contract,domain,infrastructure}/**NodeTask*`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemNode{FlowValidator,WorkflowService}.java`
- `server/src/main/resources/db/migration/V095__extend_node_task_collaboration.sql`
- `server/src/test/java/com/colla/platform/modules/project/{application,api}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/*`、`docs/02-roadmap/current-roadmap.md`

## Validation

- Backend tests: `ModuleArchitectureTests`、`WorkItemStateFlowApiContractTests`、`WorkItemNodeFlowDefinitionTests`、`WorkItemNodeRuntimeAdapterTests`、`NodeTaskConsumerContractHandlerTests` PASS；M3 collaboration submit/inbox/due 与六条 M2 node regression 共 7/7 PASS。
- System evidence: PostgreSQL 16 上 V001-V095 共 95 个迁移全部 validate/apply PASS；真实 `project_space` platform object 授权、due event delivery、exact replay 与 transaction commit PASS。
- Architecture contracts: `modules=15; activeTables=134; exceptions=93; contractFiles=26`，PASS。
- Architecture boundaries: `backendPrivate=140; sharedReverse=0; frontendImports=64; crossOwnerReads=93`，PASS。
- Frontend build: Not required；M3 未修改 `web/**`，成员执行 UI 与视觉证据属于 M5。
- Browser smoke: Not required；M3 为后端协作里程碑，M5 负责真实隔离 `route-final`。
- Local quality gate: light checkpoint PASS（`.local-reports/quality-gate-20260726T191452.md`）；finish 使用 stage 档位、targeted backend 与真实隔离系统命令再次收口。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M4-T01 | recovery/jump/terminate/compensate/upgrade remains unimplemented | non-blocking for M3 | next milestone |
| PROJECT-PLATFORM-S09-M5-T01 | visual designer/member execution UI/browser evidence remains unimplemented | non-blocking for M3 | route-final milestone |
| N/A | workload, business calendar, effort and capacity are excluded | non-blocking | future stage S16 |

## Next Steps

- 从 `PROJECT-PLATFORM-S09-M4-T01` 开始恢复、升级与运维闭环，不提前实现 M5 UI。
