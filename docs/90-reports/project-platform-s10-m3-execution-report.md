# PROJECT-PLATFORM-S10-M3 Execution Report

## Scope
PROJECT-PLATFORM-S10-M3-T01 到 PROJECT-PLATFORM-S10-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M3-T01 | non-core | static | not-required | not-required | No | M2 报告、relation authority、并发和 projection 逐项复核 |
| PROJECT-PLATFORM-S10-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL canonical parent edge 到可丢弃 closure 的确定重建 |
| PROJECT-PLATFORM-S10-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | attach/detach/reparent/split-child 原子闭包与精确重放 |
| PROJECT-PLATFORM-S10-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | published-v4 跨类型矩阵、冻结 definition version 与最大深度 |
| PROJECT-PLATFORM-S10-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | ancestor/descendant/sibling/local-tree 上限、游标与批量摘要 |
| PROJECT-PLATFORM-S10-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | breadcrumb/parent/children/sibling/local-tree 授权导航 |
| PROJECT-PLATFORM-S10-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | 子项白名单字段继承、显式覆盖与禁止复制运行事实 |
| PROJECT-PLATFORM-S10-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | relation lifecycle 与绑定定义下的 closure 同事务更新 |
| PROJECT-PLATFORM-S10-M3-T09 | core-system | system-real-isolated | not-required | isolated | No | scan/dry-run/rebuild/failure/resume 且不改 canonical edge |
| PROJECT-PLATFORM-S10-M3-T10 | core-system | system-real-isolated | not-required | isolated | No | 成员 hierarchy API 与 owner/admin recovery API 分层 |
| PROJECT-PLATFORM-S10-M3-T11 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 跨类型、深度、并发、授权和故障恢复 |
| PROJECT-PLATFORM-S10-M3-T12 | core-system | system-real-isolated | not-required | isolated | No | 深度 64、节点 200、edge/path 恢复预算与索引计划 |

## Completed Items
- 复核 M2 关系权威、稳定图锁、双端授权、回执与生命周期闭包，没有需要 Reopen 的阻断。
- 以活动 parent-child relation edge 为唯一结构事实激活 closure projection；关系 mutation 在同一事务刷新可重建 path。
- 交付 attach、detach、reparent 和原子 split-child，复用 caller-stable receipt、expected version、history/activity/audit/outbox。
- 交付跨类型 published-definition 规则、最大深度、受控字段继承及祖先/子孙/同级/局部树有界查询。
- 交付 owner/admin scan、dry-run、rebuild、失败批次和 resume；恢复只替换 projection，不修补 canonical edge。
- 交付成员 hierarchy API、治理 recovery API、稳定错误码及真实 PostgreSQL 并发/恢复/SQL plan 测试。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M3-T01 | M2 12 项可追溯且层级不绕过关系权威 | M2 report、relation service/repository、target/current contracts | 本地代码/文档复核 | 不需要：审计任务 | Done |
| PROJECT-PLATFORM-S10-M3-T02 | closure 可丢弃重建且不可直接写边 | projection service、hierarchy repository、V099 | missing/wrong/unexpected path 与 rebuild assertions | 不需要：后端派生投影 | Done |
| PROJECT-PLATFORM-S10-M3-T03 | 层级命令与子项创建全成或全败 | hierarchy service 外层事务、确定性子 request | replay、reparent、detach、rollback assertions | 不需要：后端命令 | Done |
| PROJECT-PLATFORM-S10-M3-T04 | 跨类型与最大深度由冻结定义解释 | stored-binding runtime adapter、edge definition version | task→bug 与 depth rejection assertions | 不需要：配置运行时 | Done |
| PROJECT-PLATFORM-S10-M3-T05 | 深度/节点/游标有硬上限且无 N+1 | joined hierarchy query、stable `depth:uuid` cursor | bounded page 与 EXPLAIN assertions | 不需要：查询 DTO | Done |
| PROJECT-PLATFORM-S10-M3-T06 | 局部导航安全且退化明确 | hierarchy navigation DTO/service | breadcrumb/parent/children/sibling/local-tree assertions | 不需要：M3 无 UI | Done |
| PROJECT-PLATFORM-S10-M3-T07 | 只继承白名单可写可见字段 | split-child allowlist、显式值覆盖 | priority inheritance/replay assertions | 不需要：后端字段投影 | Done |
| PROJECT-PLATFORM-S10-M3-T08 | 生命周期/旧绑定不留下孤儿 projection | relation mutation same-tx refresh、stored definition binding | attach/reparent/detach 与 rollback assertions | 不需要：生命周期服务 | Done |
| PROJECT-PLATFORM-S10-M3-T09 | 扫描/恢复可续跑且不改规范边 | recovery batch repository/service | dry-run、failed cycle、resume、edge count assertions | 不需要：治理 API | Done |
| PROJECT-PLATFORM-S10-M3-T10 | 成员/治理路由、确认和错误稳定 | two controllers、DTO、exception mapping | service/API compile + authorization assertions | 不需要：M3 无 UI | Done |
| PROJECT-PLATFORM-S10-M3-T11 | 跨类型/并发/深层/恢复无半事实 | dedicated integration suite | `WorkItemHierarchyServiceIntegrationTests` 3/3 PASS | 不需要：真实 PostgreSQL 隔离 | Done |
| PROJECT-PLATFORM-S10-M3-T12 | 上限与 SQL plan 可复现 | 64/200/5000/20000 budgets、hierarchy indexes | specific index plan；V001-V099 migration | 不需要：性能基座 | Done |

## Code Changes
- 新增 `WorkItemHierarchyModels`、`WorkItemHierarchyProjectionService`、`WorkItemHierarchyService`。
- 新增 `WorkItemHierarchyRepository` / `JdbcWorkItemHierarchyRepository`，实现 closure、稳定游标查询、扫描与恢复批次。
- 新增成员 hierarchy controller/DTO、owner/admin recovery controller，并扩展 relation runtime/access/exception 边界。
- 新增 V099 hierarchy rebuild batch 与查询索引迁移，更新 migration matrix 和 table-owner 范围。
- 新增 `WorkItemHierarchyServiceIntegrationTests`，覆盖真实 PostgreSQL 跨类型、继承、幂等、并发、授权、恢复和执行计划。
- 同步 roadmap、target/current architecture、module/object/event 合同。

## Validation
- Backend tests: `mvn -q -DskipTests compile` PASS；`mvn -q -DskipTests test` test-compile PASS；`WorkItemHierarchyServiceIntegrationTests` PostgreSQL 真实服务流 3/3 PASS；M2 `WorkItemRelationServiceIntegrationTests` 4/4 PASS；relation foundation、V001-V099 分段升级与空库全迁移 PASS。
- Frontend build: Not required；M3 未修改 Web，关系控件与局部层级 UI 属于 M4-M5。
- Local quality gate: `git diff --check`、architecture inventory/contracts/boundaries 和 work plan PASS；fresh M3 checkpoint `.local-reports/quality-gate-20260727T031100.md` PASS（architecture、planning、toolchain、backend compile、workbench typecheck、work-cycle documents，无 warning）。
- Browser smoke: Not required；M3 只激活后端 hierarchy command/navigation/recovery API，没有浏览器入口。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S10-M4-T01 复核 M1-M3 定义、实例、层级和权限；交付关系控件、反向引用、影响分析与 legacy 承接，不创建旁路关系事实。
