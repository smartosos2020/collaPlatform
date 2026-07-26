# PROJECT-PLATFORM-S09-M4 Execution Report

## Scope

PROJECT-PLATFORM-S09-M4-T01 到 PROJECT-PLATFORM-S09-M4-T12。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M4-T01 | core-system | system-real-isolated | not-required | isolated | No | snapshot recovery/compensation/upgrade 语义与失败关闭 |
| PROJECT-PLATFORM-S09-M4-T02 | core-system | system-real-isolated | not-required | isolated | No | 声明式 return/correct 关闭旧 work、重建目标并保留历史 |
| PROJECT-PLATFORM-S09-M4-T03 | core-system | system-real-isolated | not-required | isolated | No | 同 snapshot 合法 jump、来源/目标/关闭边界 |
| PROJECT-PLATFORM-S09-M4-T04 | core-system | system-real-isolated | not-required | isolated | No | terminate 与 WorkItem archive/restore 分离 |
| PROJECT-PLATFORM-S09-M4-T05 | core-system | system-real-isolated | not-required | isolated | No | 白名单 compensation ledger、顺序、幂等与 resume |
| PROJECT-PLATFORM-S09-M4-T06 | core-system | system-real-isolated | not-required | isolated | No | 非空实例显式 node map upgrade 与 blocked/missing map 拒绝 |
| PROJECT-PLATFORM-S09-M4-T07 | core-system | system-real-isolated | not-required | isolated | No | pre-S09 manifest backfill、失败清单、resume、verify |
| PROJECT-PLATFORM-S09-M4-T08 | core-system | system-real-isolated | not-required | isolated | No | owner/admin 恢复入口与 member/guest/outsider/enterprise-admin 边界 |
| PROJECT-PLATFORM-S09-M4-T09 | core-system | system-real-isolated | not-required | isolated | No | outbox 故障时 runtime/history/activity/audit/receipt 全回滚 |
| PROJECT-PLATFORM-S09-M4-T10 | core-system | system-real-isolated | not-required | isolated | No | 六身份、stale、重放、跨空间、缺失映射与注入故障 |
| PROJECT-PLATFORM-S09-M4-T11 | core-system | system-real-isolated | not-required | isolated | No | V001/V061/V078/V085/V090/V093/V095 到 V096 与重复 migrate |
| PROJECT-PLATFORM-S09-M4-T12 | non-core | integration | not-required | not-required | No | runbook、兼容矩阵、架构合同与 checkpoint |

M4 是后端恢复与运维里程碑；可视化恢复、升级和 backfill 交互由 M5 `route-final` 统一验收，因此本里程碑不伪造浏览器证据。

## Completed Items

- schema v3 snapshot 新增受控 recovery command 与 compensation 定义，冻结来源、目标、授权、关闭模式、危险确认、动作白名单和确定顺序。
- return/jump/terminate/correct 使用 WorkItem/instance 双版本、持久 receipt 和同事务 task/token/join 关闭；旧历史只追加，archive/restore 与业务终止保持分离。
- V096 新增 compensation run/step、node backfill batch/unit、instance recovery 水位、binding upgrade 受控会话保护和 manifest 不可变触发器。
- binding upgrade 只接受 published/superseded 目标 snapshot、非 blocked 兼容结果，以及覆盖全部 active source 的显式 one-to-one/split/merge map。
- pre-S09 backfill 冻结 source/target binding、entry、manifest/request/reason hash；unit 独立事务失败完整回滚，失败账本可由 owner/admin 显式续跑并 verify。
- recovery/upgrade/backfill API 保持用户空间边界；owner/admin 可执行，member/guest 拒绝，non-member 与 enterprise-admin-only 隐藏。
- 修正节点事件长 request ID 导致超过 128 字符的问题，以完整来源生成确定性 UUID 幂等键；修正 V096 session flag 的 NULL 三值逻辑，未设置升级标志时数据库失败关闭。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M4-T01 | 六类语义冻结 | `RecoveryCommandDefinition`、`CompensationDefinition`、validator/canonicalizer/runtime adapter | definition/adapter tests | Not required：服务端合同 | Done |
| PROJECT-PLATFORM-S09-M4-T02 | 显式回退/重做 | snapshot command + `recover` 原子重建目标 token/task | recovery integration + exact replay | Not required：M5 承接 UI | Done |
| PROJECT-PLATFORM-S09-M4-T03 | 受控跳转 | 同绑定 snapshot 来源集合与 executable target 校验 | validator/source mismatch negative | Not required：后端决策 | Done |
| PROJECT-PLATFORM-S09-M4-T04 | 终止与对象生命周期分离 | terminate 关闭开放 work；archive/restore 仅对齐版本 | archive/restore/recovery integration | Not required：系统事实 | Done |
| PROJECT-PLATFORM-S09-M4-T05 | 补偿白名单与续跑 | V096 run/step ledger、registered actions、resume API | completed ledger/replay tests | Not required：运维入口 | Done |
| PROJECT-PLATFORM-S09-M4-T06 | 显式版本映射 | compatibility fail-closed + one-to-one/split/merge map + guarded instance binding | non-empty upgrade、missing map、cross-space tests | Not required：M5 承接 UI | Done |
| PROJECT-PLATFORM-S09-M4-T07 | 显式 backfill | immutable batch/unit manifest、REQUIRES_NEW unit、failure/resume/verify | injected failure → partial → admin resume → verified | Not required：后端 rehearsal | Done |
| PROJECT-PLATFORM-S09-M4-T08 | 管理员恢复入口 | reason hash、confirmation、双 version、audit、用户空间 API | 六身份恢复矩阵 | Not required：权限集成 | Done |
| PROJECT-PLATFORM-S09-M4-T09 | 原子失败/重放 | 单实例 Spring transaction；unit 独立事务；deterministic event key | injected outbox failure leaves versions/task/history/receipt unchanged | Not required：故障证据 | Done |
| PROJECT-PLATFORM-S09-M4-T10 | 身份/故障负例 | stable 403/404/409/422 mapping 与最小披露 | focused unit + PostgreSQL integration | Not required：真实隔离后端 | Done |
| PROJECT-PLATFORM-S09-M4-T11 | migration/rehearsal | V096 与历史 baseline test；direct binding trigger | 96 migrations、7 baselines、repeat 0、non-empty upgrade/backfill PASS | Not required：数据库证据 | Done |
| PROJECT-PLATFORM-S09-M4-T12 | runbook/矩阵/checkpoint | S09 recovery runbook、compatibility/current/target/module/object/event docs | architecture contracts/boundaries + stage gate | Not required：治理闭环 | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/{api,application,domain,infrastructure,runtime}/**Node*`
- `server/src/main/resources/db/migration/V096__activate_node_workflow_recovery.sql`
- `server/src/test/java/com/colla/platform/modules/project/{api,application,infrastructure,runtime}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/*`、`docs/05-runbooks/project-platform-s09-node-workflow-recovery.md`
- `docs/02-roadmap/current-roadmap.md`

## Validation

- Backend tests: `WorkItemNodeFlowDefinitionTests`、`WorkItemNodeRuntimeAdapterTests`、`WorkItemStateFlowApiContractTests`、`ModuleArchitectureTests` PASS。
- System evidence: PostgreSQL 16 上 recovery/terminate/archive separation、non-empty upgrade、missing map、exact replay、outbox 故障全回滚、backfill 注入失败/失败清单/admin resume/verify 共 3 条 focused integration PASS。
- Migration evidence: PostgreSQL 16 上 V001-V096 共 96 个迁移 fresh validate/apply PASS；V001/V061/V078/V085/V090/V093/V095 七个历史基线升级到 V096 与重复 migrate 0 PASS；未设置升级 session flag 的 direct instance binding update 被数据库拒绝。
- Architecture contracts: `modules=15; activeTables=138; exceptions=93; contractFiles=26`，PASS。
- Architecture boundaries: `backendPrivate=140; sharedReverse=0; frontendImports=64; crossOwnerReads=93`，PASS。
- Frontend build: Not required；M4 未修改 `web/**`，恢复/升级/backfill UI 由 M5 完成。
- Browser smoke: Not required；M4 为后端恢复里程碑，M5 负责真实隔离 `route-final`。
- Local quality gate: stage PASS（`.local-reports/quality-gate-20260726T201628.md`）；真实隔离系统证据 `.local-reports/work-cycle-system-20260726T201628.log` PASS。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M5-T01 | designer、成员执行、恢复/升级/backfill UI 与真实浏览器闭环尚未实现 | non-blocking for M4 | route-final milestone |
| N/A | 生产 cutover、容量与 HA 不由隔离 recovery rehearsal 授权 | non-blocking | later production validation |

## Next Steps

- 从 `PROJECT-PLATFORM-S09-M5-T01` 开始可视化设计器、成员执行 UI、综合门禁和 route-final，不提前实现 S10。
