# PROJECT-PLATFORM-S08-M3 Execution Report

## Scope

PROJECT-PLATFORM-S08-M3-T01 到 PROJECT-PLATFORM-S08-M3-T12

本里程碑交付轻量状态流的 return/reopen/terminate/restore、对象 archive 协同、受控 correction、实例 binding upgrade、pre-S08 显式 backfill 与恢复治理。它不交付 M4 状态配置器/成员执行 UI，也不创建 S09 node instance/token。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M3-T01 | non-core | static | not-required | not-required | No | 六类命令来源/目标/分类/历史/授权语义与 S09 边界冻结 |
| PROJECT-PLATFORM-S08-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | snapshot 明示 return、guard/required 复核与历史保留 |
| PROJECT-PLATFORM-S08-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | terminal/reopen/canceled、重复终止精确重放 |
| PROJECT-PLATFORM-S08-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | terminate/restore 与 archive/restore 独立生命周期 |
| PROJECT-PLATFORM-S08-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | 显式 manifest backfill、失败清单、续跑与 verify |
| PROJECT-PLATFORM-S08-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | 版本 binding/state-key 映射、缺失映射失败关闭 |
| PROJECT-PLATFORM-S08-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | owner/admin correction、原因/确认/version/审计与 enterprise 隔离 |
| PROJECT-PLATFORM-S08-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | 并发纠偏 one-winner、outbox 故障全回滚、回填单元隔离 |
| PROJECT-PLATFORM-S08-M3-T09 | core-system | system-real-isolated | not-required | isolated | No | 四类 lifecycle event、receipt/replay、未知 schema permanent/dead-letter |
| PROJECT-PLATFORM-S08-M3-T10 | core-system | system-real-isolated | not-required | isolated | No | 六身份/跨空间、终态/stale/replay/guard/map/rollback 负例 |
| PROJECT-PLATFORM-S08-M3-T11 | core-system | system-real-isolated | not-required | isolated | No | 空库、V001/V061/V078/V085/V090、重复 migrate、失败续跑 |
| PROJECT-PLATFORM-S08-M3-T12 | non-core | integration | not-required | not-required | No | runbook、兼容矩阵、架构/路线/报告/checkpoint 一致 |

## Completed Items

- 激活 snapshot 明示的 return/reopen/terminate/restore；动作继续复用统一 decision、guard、required field、expected version、幂等回执与不可变历史。
- 固定 terminal、canceled 与 WorkItem archived 三类语义：重开/业务恢复必须有明示目标，对象归档不改 current state，恢复对象不猜业务状态。
- 交付 owner/space-admin 受控 correction 与 binding upgrade；原因只存 hash，要求精确危险确认、expected version、目标 state key、审计、activity、history、receipt 与 lifecycle outbox。
- 交付 V092 backfill batch/unit 与用户空间管理 API。manifest、目标/来源绑定、初始状态、attempt/failure 固定可追溯；每个单元 `REQUIRES_NEW`，可失败续跑并 verify。
- 增加 current-state binding 数据库保护；只有受控 Repository 事务可更新版本/hash，普通 SQL 失败关闭。
- 扩展 lifecycle event 为 action/state/initialized/binding-changed v1，并注册不读私表的 consumer-contract Handler；通用 delivery receipt/replay 去重，未知 payload schema 永久失败。
- 在 PostgreSQL 16 Testcontainers 验证并发纠偏、outbox 故障整体回滚、backfill 故障续跑、历史基线迁移和直接 binding 改写阻断。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M3-T01 | 六类命令不是直接改状态 | validator/decision/service operation 与 target architecture 24.10 | definition/decision tests PASS | Not required：后端语义冻结 | Done |
| PROJECT-PLATFORM-S08-M3-T02 | 只执行声明 return，重新校验且不删历史 | `WorkItemStateFlowService.execute` + return transition | lifecycle PostgreSQL test PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T03 | terminal/canceled/reopen/重复幂等稳定 | category validator + receipt replay | lifecycle + exact replay tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T04 | 业务终止/恢复不等同对象 archive | archived presentation 隐藏动作但保留 state/version | terminate/restore/archive integration PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T05 | 显式 backfill 可追溯、续跑、验证 | V092、backfill service/repository/controller | success/replay/fault/resume/verify PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T06 | state map 显式，缺失失败关闭 | controlled `upgradeBinding` + DB binding guard | rename map/missing map/direct SQL guard PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T07 | owner/admin + reason/version/confirm/audit | correction API/service、reason hash、manager boundary | member forbidden/wrong confirm/replay/history test PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T08 | 所有事实全提交或全回滚 | WorkItem/current/history/activity/audit/outbox/receipt 单事务；backfill unit REQUIRES_NEW | concurrent correction + outbox trigger + backfill fault PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T09 | lifecycle contract 可 replay；未知 schema dead letter | `WorkItemWorkflowEvent` v1 + consumer-contract Handler | descriptor/version/permanent classification tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T10 | 身份、终态、stale、重复、跨空间、guard/map/rollback | service integration identity/access/fault matrix | PostgreSQL focused suite PASS | Not required：M4 才有真实 UI | Done |
| PROJECT-PLATFORM-S08-M3-T11 | 指定基线、重复 migrate、非空/失败续跑 | Flyway V092 + backfill rehearsal | V001/061/078/085/090/latest + resume/verify PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M3-T12 | runbook/矩阵/架构一致且不越界 S09 | compatibility matrix §5、roadmap、current/target/module/object/event docs | workbench checkpoint/finish | Not required | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemStateFlow*.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemStateBackfillService.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemWorkflowConsumerContractHandler.java`
- `server/src/main/java/com/colla/platform/modules/project/infrastructure/*WorkItemState{Flow,Backfill}Repository.java`
- `server/src/main/java/com/colla/platform/modules/project/api/UserWorkItemState{Flow,Backfill}Controller.java`
- `server/src/main/java/com/colla/platform/modules/project/contract/WorkItemWorkflowEvent.java`
- `server/src/main/resources/db/migration/V092__add_work_item_state_recovery.sql`
- `server/src/test/java/com/colla/platform/modules/project/{application,api,infrastructure,runtime}/**WorkItemState*`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture,project-work-item-configuration-compatibility-matrix,platform-module-contracts,platform-object-model,event-side-effect-matrix}.md`

## Validation

- Backend tests: `mvn -q -Dtest=WorkItemServiceIntegrationTests,WorkItemStateFlowFoundationIntegrationTests test`（隔离 PostgreSQL 16 system evidence）与定向 unit/API contract 套件 PASS；证据日志 `.local-reports/work-cycle-system-20260726T152533.log`。
- Compile/test-compile: PASS。
- Focused unit/API contract：状态 definition/decision/runtime、consumer payload schema、用户 recovery 路由和错误映射，PASS。
- Real system integration：Spring Boot + PostgreSQL 16 Testcontainers，覆盖完整 lifecycle、correction、binding upgrade、backfill/replay/verify、六身份与跨空间继承边界、并发 one-winner、outbox 故障整体回滚，PASS。
- Migration：空库和 V001/V061/V078/V085/V090 到 V092；latest 重复 migrate 为 0；V092 私表/trigger/manifest 不变量，PASS。
- Frontend build: Not required；M3 未修改 `web/**`，状态配置器和成员执行 UI 属于 M4。
- Browser smoke: Not required；M3 没有新增浏览器 UI，不能用 mock 浏览器替代 M4 六身份真实隔离流。
- Local quality gate: `quality-gate-20260726T152245.md` checkpoint PASS（无 warning）；stage finish 的 backend/architecture/security/documentation/workbench 门禁继续作为最终收口证据。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M4 | 状态配置器、成员执行 UI、真实浏览器、完整 Stage 回归与 route-final 尚未交付 | 不阻断 M3；不能宣称 S08 Stage Completed | M4 |
| PROJECT-PLATFORM-S08-M4-T11 | S09 node instance/token、串并行、汇聚和会签仍不存在 | M3 current-state/backfill 私表不得被节点流复用 | S09 准入复核 |
| PROJECT-PLATFORM-S08-M4-T01 | M3 只冻结并验证通知/搜索可消费的公共 lifecycle contract，未启用用户通知正文或 WorkItem 搜索索引 | 不阻断 M3 合同验收；M4 审计保留该边界，任何产品投影需另有明确路线与权限/内容/校准设计 | M4 gap audit |

## Next Steps

- 从 PROJECT-PLATFORM-S08-M4-T01 审计 36 项已有交付，再实现空间状态配置器、成员执行 UI、真实浏览器与 Stage route-final。
- 保持 S09 Planned；不得把单一 current state、backfill batch 或 correction history 解释为节点 token、并行、汇聚或会签。
