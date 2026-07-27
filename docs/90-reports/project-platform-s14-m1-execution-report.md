# PROJECT-PLATFORM-S14-M1 Execution Report

## Scope

PROJECT-PLATFORM-S14-M1-T01 到 PROJECT-PLATFORM-S14-M1-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M1-T01 | non-core | static | not-required | not-required | No | 本地代码、路线、架构、API、表、owner、调用方和预算审计 |
| PROJECT-PLATFORM-S14-M1-T02 | non-core | unit | not-required | not-required | No | schema v1、稳定 key、空列、WIP、排序、错误与升级合同 |
| PROJECT-PLATFORM-S14-M1-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V110 fresh/repeat migration |
| PROJECT-PLATFORM-S14-M1-T04 | core-user | e2e-real-isolated | real | isolated | No | S13 受权 query 驱动列、泳道、计数与 cursor |
| PROJECT-PLATFORM-S14-M1-T05 | core-system | system-real-isolated | not-required | isolated | No | 系统/动态字段 capability 与 hidden/read-denied 失败关闭 |
| PROJECT-PLATFORM-S14-M1-T06 | core-user | e2e-real-isolated | real | isolated | No | 稳定排序、WIP、并发刷新与来源版本恢复 |
| PROJECT-PLATFORM-S14-M1-T07 | core-user | e2e-real-isolated | real | isolated | No | S08 状态动作和 S09 节点动作公共命令映射 |
| PROJECT-PLATFORM-S14-M1-T08 | core-user | e2e-real-isolated | real | isolated | No | caller-stable 幂等、乐观锁、一胜一冲突、回滚与收权 |
| PROJECT-PLATFORM-S14-M1-T09 | core-user | e2e-real-isolated | real | isolated | No | board、筛选、泳道、拖拽、键盘、长名称与 1440/1366/820 |
| PROJECT-PLATFORM-S14-M1-T10 | core-user | e2e-real-isolated | real | isolated | No | 六身份、跨空间、并发、撤权、离线与恢复 |
| PROJECT-PLATFORM-S14-M1-T11 | core-user | e2e-real-isolated | real | isolated | No | 卡片/列/泳道/端口/投影/DOM 确定性上界 |
| PROJECT-PLATFORM-S14-M1-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/object/event/owner 同步 |

## Completed Items

- 审计并复用 S13 QueryDefinition、S08 state command、S09 node command 和 S11 permission/data scope，不读取其他 owner 私表。
- 冻结 schema v1 BoardRequest/Column/Lane/Card/Action/Preference/Order/MoveIntent/MoveResult 合同。
- 新增 V110 四张 project owner 表及复合 FK、唯一约束、索引、清理顺序和 completed receipt 不可变 trigger。
- 新增受权看板 render、个人偏好、稳定排序、WIP 展示和状态/节点移动 API。
- 新增 Web board、泳道、拖拽、键盘跨列/重排、WIP 输入、错误/离线恢复和响应式布局。
- 新增针对性单元、PostgreSQL foundation 与真实隔离 Playwright 验收。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M1-T01 | API、表、owner、调用方、预算与禁止依赖可定位 | current roadmap/target/current + S08/S09/S11/S13 code/docs 审计 | `rg`、全文读取与 architecture contract | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S14-M1-T02 | schema/key/空列/WIP/排序/错误/升级明确 | `WorkItemBoardModels` + service validation | `WorkItemBoardServiceTests` contract/budget tests | 不需要：服务端合同 | Done |
| PROJECT-PLATFORM-S14-M1-T03 | 复合边界、唯一性、FK、索引、清理和 owner 完整 | V110 + `JdbcProjectSpaceRepository` cleanup + table owner manifest | `WorkItemBoardFoundationIntegrationTests` fresh 110/repeat 0 PASS | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S14-M1-T04 | hidden 不影响列/泳道/计数/cursor/error | query select 扩展后调用 S13 `execute`，仅消费 `QueryResult.items` | hidden diagnostics/unit assertions + isolated API render | real isolated：四个允许身份只看到同两张卡，拒绝身份无标题 | Done |
| PROJECT-PLATFORM-S14-M1-T05 | 未注册/无 capability 失败关闭 | registered board fields + `requireQueryCapability` | dynamic capability fail-close unit test | 不需要：服务端 capability 边界 | Done |
| PROJECT-PLATFORM-S14-M1-T06 | 排序可重建、并发无重复/丢卡、WIP 非流程规则 | source-version guarded order + visual `wipExceeded` | stale order/WIP/unit tests + PostgreSQL conflict | real isolated：空列、WIP、刷新后单卡落目标列 | Done |
| PROJECT-PLATFORM-S14-M1-T07 | 只调用规范状态/节点命令 | `executeWorkflowAction`/`executeNodeTaskCommand` adapter | state replay + node action projection tests | real isolated：状态 action 完成跨列移动 | Done |
| PROJECT-PLATFORM-S14-M1-T08 | 一胜一冲突、精确重放、收权失败关闭 | immutable command receipt + expected WorkItem/order/instance version + transactional service | replay once/conflict-before-mutation unit tests | real isolated：并发响应一项 2xx、一项 409；重放稳定；撤权后 render/move 拒绝 | Done |
| PROJECT-PLATFORM-S14-M1-T09 | 1440/1366/820、键盘、长名称、状态可理解 | `ProjectWorkItemsPanel` board + CSS + typed API | web lint/build | real isolated：ArrowRight、swimlane、长标题、三视口无 body overflow | Done |
| PROJECT-PLATFORM-S14-M1-T10 | 六身份、跨空间、并发、收权、离线恢复闭环 | isolated fixture + API/UI cleanup | `project-platform-s14-m1.spec.ts` 1/1 PASS | real isolated：owner/admin/member/guest 可见；outsider/enterprise denied；跨空间不可移动；离线输入保留 | Done |
| PROJECT-PLATFORM-S14-M1-T11 | SQL/端口/内存/DOM 上界可复现 | 12 columns、24 lanes、100 cards、200 presentation calls、388 projection containers；board owner render SQL 为 order read + stats upsert | frozen constants/unit test + V110 indexes + targeted E2E DOM assertions | real isolated 2-card fixture 和响应式 containment；不声明生产容量 | Done |
| PROJECT-PLATFORM-S14-M1-T12 | 文档只声明 M1，M2-M4 不提前 | target/current/module/object/event/owner/roadmap/report 同步 | planning/architecture/checkpoint gates | 不需要：文档同步 | Done |

## Code Changes

- Backend：`WorkItemBoardModels`、repository/JDBC、service、controller、异常映射和 QueryDefinition null-safe selected projection。
- Database：V110 board preference/order/command/projection stats，project-space cleanup 与 owner manifest。
- Web：typed board API、board mode、泳道、拖拽/键盘、WIP、错误/离线/响应式样式。
- Tests/workbench：service/foundation tests、S14 isolated Playwright、隔离端口/数据库 runner 和 cycle route mapping。
- Docs：目标/当前架构、模块/对象/事件合同、当前路线与本报告。

## Deterministic Budget

| Surface | Reproducible upper bound | Enforcement |
| --- | --- | --- |
| S13 visible cards | 100 per board page | query page size clamped by `MAX_CARDS` |
| Columns / visible swimlanes | 12 / 24 | request validation and lane fail-close |
| State/node presentation ports | 2 per visible card, at most 200 | one state and one node presentation per authorized card |
| In-memory/serialized card+lane containers | 100 + 12×24 = 388 | immutable result construction from bounded inputs |
| Board-owner render SQL | one scoped order SELECT + one low-cardinality stats UPSERT | JDBC repository; indexed workspace/space/user/view access |
| Move orchestration | one canonical S08 or S09 mutation per new request | immutable request receipt and exact replay |
| Browser DOM | at most 100 card nodes and 288 lane containers before fixed controls | same API bounds; responsive E2E asserts document containment |

这些是本地确定性门禁，不是生产吞吐、延迟、并发、SLO 或 S16 人员产能承诺。

## Validation

- Backend tests: `mvn -q -Dtest=WorkItemBoardServiceTests test` PASS.
- PostgreSQL/Flyway: `WorkItemBoardFoundationIntegrationTests` V001-V110 fresh/repeat PASS.
- Frontend build: `pnpm web:lint` PASS；`pnpm web:build` PASS.
- Workbench: 80/80 tests PASS.
- Browser smoke: `pnpm smoke:s14-isolated -- --spec project-platform-s14-m1.spec.ts` real isolated Chromium 1/1 PASS.
- Local quality gate: `.local-reports/quality-gate-20260727T164153.md` light checkpoint PASS；finish 生成 fresh stage 报告。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M2-T01 | 日历、日期 capability、时区/DST 和日期拖放尚未实现 | 后续路线任务，不影响 M1 验收 | PROJECT-PLATFORM-S14-M2 |
| PROJECT-PLATFORM-S14-M3-T01 | 甘特、依赖线、层级展开和关键路径尚未实现 | 后续路线任务，不影响 M1 验收 | PROJECT-PLATFORM-S14-M3 |
| PROJECT-PLATFORM-S14-M4-T01 | 基线、时间线、组合预算和 Stage route-final 尚未实现 | 后续路线任务，不影响 M1 验收 | PROJECT-PLATFORM-S14-M4 |

## Next Steps

- 下一轮从 `PROJECT-PLATFORM-S14-M2-T01` 开始；未获新范围前不实现 M2，不跳到 M3-M4，不提前实现 S15。
