# PROJECT-PLATFORM-S14-M2 Execution Report

## Scope

PROJECT-PLATFORM-S14-M2-T01 到 PROJECT-PLATFORM-S14-M2-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M2-T01 | non-core | static | not-required | not-required | No | M1、query、permission、migration 与 owner 审计 |
| PROJECT-PLATFORM-S14-M2-T02 | non-core | unit | not-required | not-required | No | date/datetime/range/all-day/no-date/DST/timezone 合同 |
| PROJECT-PLATFORM-S14-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V111 fresh/repeat migration |
| PROJECT-PLATFORM-S14-M2-T04 | core-user | e2e-real-isolated | real | isolated | No | published capability + permission-scoped window query |
| PROJECT-PLATFORM-S14-M2-T05 | core-user | e2e-real-isolated | real | isolated | No | 单日/区间/跨日/全天/无日期/DST/跨时区 |
| PROJECT-PLATFORM-S14-M2-T06 | core-user | e2e-real-isolated | real | isolated | No | canonical update、exact replay、one-winner 与收权 |
| PROJECT-PLATFORM-S14-M2-T07 | core-user | e2e-real-isolated | real | isolated | No | 月/周/日、重叠、受权计数与窗口上限 |
| PROJECT-PLATFORM-S14-M2-T08 | core-user | e2e-real-isolated | real | isolated | No | Web 日期选择、无日期、键盘、长名称与三视口 |
| PROJECT-PLATFORM-S14-M2-T09 | core-user | e2e-real-isolated | real | isolated | No | 离线输入、冲突、收权与 REST 校准 |
| PROJECT-PLATFORM-S14-M2-T10 | core-user | e2e-real-isolated | real | isolated | No | 六身份、跨空间、并发、离线和恢复 |
| PROJECT-PLATFORM-S14-M2-T11 | core-user | e2e-real-isolated | real | isolated | No | 62 日/100 事件/8 overlap/163 container 上界 |
| PROJECT-PLATFORM-S14-M2-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/object/event/owner 同步 |

## Completed Items

- 冻结 schema v1 CalendarView/DateBinding/RangeWindow/CalendarEvent/DateMutation 合同。
- 新增 V111 个人偏好、可重建窗口索引、不可变命令回执和低基数统计，并纳入 project owner 与清理顺序。
- 新增基于 S13 受权 QueryDefinition 与 published date capability 的服务端日历 render。
- 新增 date/datetime、全天/跨日/无日期、IANA timezone、DST 和 overlap 派生。
- 新增通过 canonical WorkItem update 的日期移动/拉伸、expected version 与精确重放。
- 新增 Web 月/周/日、日期字段/时区、拖放/键盘、区间调整、无日期区与响应式体验。
- 新增 service、PostgreSQL foundation 和真实隔离 Playwright 验收。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M2-T01 | 12 项逐项可追溯且不复制权威 | M1 report + query/permission/work-item contracts 审计 | roadmap/architecture gates | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S14-M2-T02 | 日期、区间、DST、版本和上限明确 | `WorkItemCalendarModels` + service validation | `WorkItemCalendarServiceTests` | 不需要：合同与纯派生 | Done |
| PROJECT-PLATFORM-S14-M2-T03 | 复合边界、幂等、索引、清理和 owner 完整 | V111 + cleanup + table owner manifest | `WorkItemCalendarFoundationIntegrationTests` | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S14-M2-T04 | capability 失败关闭且 hidden 不影响外形 | `requireQueryCapability` + only `QueryResult.items` | unit + isolated API matrix | real isolated：四身份同一可见事件；拒绝身份无标题 | Done |
| PROJECT-PLATFORM-S14-M2-T05 | 存储语义稳定且 DST 不错日 | local date/instant 双路径、zone display derivation | New York DST 71-hour range assertion | real isolated：跨日事件与无日期区 | Done |
| PROJECT-PLATFORM-S14-M2-T06 | expected version、精确重放、无半区间 | transactional `mutateDate` + canonical WorkItem update | exact replay unit | real isolated：并发一胜一 409、重放稳定、撤权拒绝 | Done |
| PROJECT-PLATFORM-S14-M2-T07 | 窗口、重叠、数量和预算受控 | month/week/day + lane derivation + 62-day validation | budget constants/unit tests | real isolated：31 日窗口和受权计数 | Done |
| PROJECT-PLATFORM-S14-M2-T08 | Web、键盘、无日期、三视口可用 | typed API + `ProjectWorkItemsPanel` + CSS | web lint/build | real isolated：1440/1366/820 无 body overflow | Done |
| PROJECT-PLATFORM-S14-M2-T09 | 断线不丢输入且恢复重读 | controlled inputs + query invalidation + server conflicts | web build + service conflicts | real isolated：offline timezone retained then saved | Done |
| PROJECT-PLATFORM-S14-M2-T10 | 六身份/跨空间/收权/并发闭环 | isolated fixture and cleanup | `project-platform-s14-m2.spec.ts` | real isolated：route-final 六身份闭环 | Done |
| PROJECT-PLATFORM-S14-M2-T11 | 确定性上界可复现 | frozen constants and request clamps | unit budget assertions + bounded DOM | real isolated：31-day fixed fixture 与 bounded DOM；不声明生产容量 | Done |
| PROJECT-PLATFORM-S14-M2-T12 | 文档只声明 M2 事实 | target/current/module/object/event/owner/roadmap/report | planning/architecture/checkpoint gates | 不需要：文档同步 | Done |

## Deterministic Budget

| Surface | Reproducible upper bound | Enforcement |
| --- | --- | --- |
| Calendar window | 62 inclusive local dates | `MAX_WINDOW_DAYS` before query |
| Visible events | 100 per request | query limit clamped by `MAX_EVENTS` |
| Overlap lanes | 8 | deterministic lane derivation |
| Window projection containers | 62 days + 100 events + no-date list = at most 163 primary containers | bounded inputs and immutable result |
| Calendar-owner render SQL | optional scoped preference read, replaceable index write, one low-cardinality stats upsert | composite indexes and user-scoped rows |
| Date mutation | one canonical WorkItem update per new request | immutable receipt + exact replay |
| Browser DOM | at most 62 day cells and 100 unique visible events before cross-day rendering duplicates | same API bounds and responsive containment |

这些是本地确定性门禁，不是生产吞吐、延迟、并发、SLO 或 S16 产能承诺。

## Code Changes

- Backend：calendar models、repository/JDBC、service、controller、异常映射与 canonical WorkItem date mutation。
- Database：V111 preference/window index/command receipt/projection stats、project-space cleanup 与 owner manifest。
- Web：typed calendar API、月/周/日模式、时区/字段选择、拖放/键盘、区间调整、无日期区与响应式样式。
- Tests/workbench：service/foundation tests、S14-M2 isolated route-final Playwright 与 V111 migration assertions。
- Docs：目标/当前架构、模块/对象/事件合同、当前路线与本报告。

## Validation

- Backend tests: `mvn -q -Dtest=WorkItemCalendarServiceTests test` PASS.
- PostgreSQL/Flyway: `WorkItemCalendarFoundationIntegrationTests` V001-V111 fresh/repeat PASS.
- Frontend build: `pnpm web:lint` PASS；`pnpm web:build` PASS.
- Workbench: all tests PASS.
- Local quality gate: `.local-reports/quality-gate-20260727T172816.md` light checkpoint PASS；正式 stage finish 重验。
- Browser smoke: `pnpm smoke:s14-isolated -- --spec project-platform-s14-m2.spec.ts` real isolated Chromium 1/1 PASS.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M3-T01 | 甘特、依赖线、层级展开和关键路径尚未实现 | 后续路线，不影响 M2 | PROJECT-PLATFORM-S14-M3 |
| PROJECT-PLATFORM-S14-M4-T01 | 基线、时间线和组合 route-final 尚未实现 | 后续路线，不影响 M2 | PROJECT-PLATFORM-S14-M4 |

## Next Steps

- 下一轮从 `PROJECT-PLATFORM-S14-M3-T01` 开始；M2 完成不授权跳过 M3，也不提前实现 S15。
