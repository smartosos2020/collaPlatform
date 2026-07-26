# PROJECT-PLATFORM-S09-M2 Execution Report

## Scope

PROJECT-PLATFORM-S09-M2-T01 到 PROJECT-PLATFORM-S09-M2-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M2-T01 | non-core | static | not-required | not-required | No | M1 定义/snapshot/schema/report 与未关闭阻断逐项复核 |
| PROJECT-PLATFORM-S09-M2-T02 | non-core | unit | not-required | not-required | No | 只解释 WorkItem 绑定 snapshot/version/hash，缺能力与未来 schema 失败关闭 |
| PROJECT-PLATFORM-S09-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | instance/token 唯一初始化、行锁、双版本 CAS 与正常 mutation 对齐 |
| PROJECT-PLATFORM-S09-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | presentation/execute 共用候选与授权决策、稳定排序和最小披露 |
| PROJECT-PLATFORM-S09-M2-T05 | core-system | system-real-isolated | not-required | isolated | No | 自动节点逐步持久化、128 步上限、未知路由失败关闭 |
| PROJECT-PLATFORM-S09-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | single/any/all/quorum claim/delegate/complete/vote/withdraw 与迟到命令 |
| PROJECT-PLATFORM-S09-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | exclusive/parallel split、all/any/quorum join、原子 arrival 与提前汇聚取消 |
| PROJECT-PLATFORM-S09-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | request ID/hash 精确重放、异载荷/stale/并发单赢家 |
| PROJECT-PLATFORM-S09-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | history/activity/audit/outbox/receipt 同事务与最小事件 payload |
| PROJECT-PLATFORM-S09-M2-T10 | core-system | system-real-isolated | not-required | isolated | No | 用户 node-workflow presentation/history/start/task-action API 与错误边界 |
| PROJECT-PLATFORM-S09-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | 六身份、并发、重放、split/join、会签、跨空间与不可变负例 |
| PROJECT-PLATFORM-S09-M2-T12 | non-core | integration | not-required | not-required | No | PostgreSQL 代表性任务投影 EXPLAIN、硬上界与隔离延迟 |

## Completed Items

- 复核 M1 的 schema v3 节点图、绑定 snapshot、V093 基础表与架构边界，确认 M2 没有复用或读取 S08 current-state/history/backfill 私表。
- 实现只消费 WorkItem 绑定不可变 snapshot/version/hash 的 `WorkItemNodeRuntimeAdapter`，无 nodeFlow 返回明确 capability，未知 schema/节点类型/路由失败关闭。
- 实现独立 node instance/token/task/vote/join/arrival/command/history Repository；WorkItem、instance、token、task、join 行锁与乐观版本 CAS 保证唯一启动、并发单赢家和正常 WorkItem mutation 版本对齐。
- 实现 presentation 与 execute 共用的候选/动作决策，以及 single/any/all/quorum 的领取、委派、完成、投票、撤票与不可变 supersession 链。
- 实现有界非递归自动推进、exclusive/parallel split 与 all/any/quorum join；嵌套 correlation 在汇聚后恢复父级，any/quorum 提前释放时原子取消未完成分支，避免残留任务/token。
- 接入规范 request hash、持久回执、精确重放、不可变 history、WorkItem activity、audit 与 `node_workflow.changed` v1 transactional outbox。
- 交付用户 presentation/history/start/task-action API 和最小 DTO；候选角色、条件、quorum、token lineage、split/join/correlation 与隐藏字段不出接口。
- 增加 PostgreSQL 16 自动化，覆盖六身份、all/any join、会签撤票重投、并发领取、重放、版本对齐、跨空间/不可变边界和代表性索引预算。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M2-T01 | M1 证据与阻断逐项可追溯 | M1 report、current/target/module/object architecture 与独立 V093/V094 表族 | architecture contracts/boundaries PASS | Not required：服务端里程碑 | Done |
| PROJECT-PLATFORM-S09-M2-T02 | 只解释绑定 snapshot，不猜最新配置或读 S08 私表 | `WorkItemNodeRuntimeAdapter`、`PublishedSnapshotAdapter` | adapter/API contract/ArchUnit focused tests PASS | Not required：运行 adapter | Done |
| PROJECT-PLATFORM-S09-M2-T03 | 唯一 instance、原子启动、行锁和双版本不漂移 | node Repository、`WorkItemService` version alignment | PostgreSQL initialization + normal mutation alignment PASS | Not required：事务系统证据 | Done |
| PROJECT-PLATFORM-S09-M2-T04 | 投影与命令共享决策且披露稳定 | `WorkItemNodeWorkflowService.presentation/decide`、安全 DTO | six-identity presentation/action assertions PASS | Not required：API 集成 | Done |
| PROJECT-PLATFORM-S09-M2-T05 | 自动推进有界、可恢复且未知扩展失败关闭 | 迭代 queue、`MAX_INTERNAL_STEPS=128`、逐 token history | bound snapshot end-to-end flow + adapter negatives PASS | Not required：后端引擎 | Done |
| PROJECT-PLATFORM-S09-M2-T06 | single/any/all/quorum 与会签撤销确定 | task/vote command paths、immutable vote chain | claim/complete/vote/withdraw/revote flow PASS | Not required：M5 承接 UI | Done |
| PROJECT-PLATFORM-S09-M2-T07 | split/join 不丢失、不双放行、不残留 | 嵌套 correlation、immutable arrival、early-join cancellation | parallel all join + any early join PostgreSQL tests PASS | Not required：引擎语义 | Done |
| PROJECT-PLATFORM-S09-M2-T08 | 重放、异载荷、stale 与并发败者受控 | command receipt + canonical hash + row locks/CAS | exact replay + two-thread claim one-winner PASS | Not required：事务证据 | Done |
| PROJECT-PLATFORM-S09-M2-T09 | 历史和副作用同事务且最小披露 | node history/activity/audit/`WorkItemNodeWorkflowEvent` | event/receipt/history/vote/join row assertions PASS | Not required：事件合同 | Done |
| PROJECT-PLATFORM-S09-M2-T10 | 用户 API、错误和 DTO 边界稳定 | `UserWorkItemNodeWorkflowController`、exception handler | OpenAPI route + DTO non-leak contract tests PASS | Not required：M2 无 UI | Done |
| PROJECT-PLATFORM-S09-M2-T11 | 六身份、并发、重放、split/join、会签与隔离完整 | focused service/migration/architecture suites | PostgreSQL 16 node runtime and invariant suite PASS | Not required：系统证据为真实隔离后端 | Done |
| PROJECT-PLATFORM-S09-M2-T12 | SQL plan、批量上界与延迟可复现 | open task `(workspace,space,instance,status,node_key,id)` index；task 200/token 256 上界 | representative projection EXPLAIN/index + latency budget PASS | Not required：容量边界测试 | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/{application,domain,runtime,infrastructure}/**WorkItemNode*`
- `server/src/main/java/com/colla/platform/modules/project/api/UserWorkItemNodeWorkflowController.java`
- `server/src/main/java/com/colla/platform/modules/project/contract/WorkItemNodeWorkflowEvent.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemService.java`
- `server/src/main/resources/db/migration/V094__activate_node_workflow_runtime.sql`
- `server/src/test/java/com/colla/platform/{architecture,modules/project}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture,platform-module-contracts,platform-object-model,event-side-effect-matrix}.md`

## Validation

- Backend tests: adapter/API/ArchUnit focused suite PASS；PostgreSQL 16 node runtime 主流程、六身份、all/any join、会签、精确重放、两线程并发单赢家、版本对齐与代表性查询预算 PASS。
- Migration/invariant matrix: V001-V094、历史基线升级、重复 migrate、vote supersession/withdrawal、join arrival 不可变、跨空间复合边界和 S08 私表隔离 PASS。
- Architecture inventory: `modules=15; java=398; backendImports=256; frontendImports=64; crossOwnerSql=93`，PASS。
- Architecture contracts: `modules=15; activeTables=133; exceptions=93; contractFiles=25`，PASS。
- Architecture boundaries: `backendPrivate=140; sharedReverse=0; frontendImports=64; crossOwnerReads=93`，PASS。
- Frontend build: Not required；M2 未修改 `web/**`，节点成员执行和设计器 UI 属于 M5。
- Browser smoke: Not required；M2 交付用户 API 和真实 PostgreSQL 运行时，未交付浏览器 UI。
- Local quality gate: light checkpoint PASS（`.local-reports/quality-gate-20260726T182943.md`）；finish 使用 stage 档位与真实隔离系统证据再次收口。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M3 | 节点表单快照、交付物、等待/服务/脚本节点及时限升级尚未实现 | 不阻断 M2 token/会签运行时 | M3 |
| PROJECT-PLATFORM-S09-M4 | 回退、跳转、终止、补偿、版本升级与恢复治理尚未实现 | 不阻断 forward-only 节点推进 | M4 |
| PROJECT-PLATFORM-S09-M5 | 可视化设计器、成员执行 UI、可访问性和 route-final 尚未完成 | M2 不完成 S09 Stage | M5 |

## Next Steps

- 从 PROJECT-PLATFORM-S09-M3-T01 复核 M2 运行时证据，再增加节点表单快照、交付物引用、等待/服务/脚本受控能力及时限升级。
- 保持 `node_workflow.changed` v1 最小公共合同和 S08/S09 私表隔离；M3 不提前实现 M4 恢复治理或 M5 UI。
