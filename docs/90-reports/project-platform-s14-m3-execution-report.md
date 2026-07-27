# PROJECT-PLATFORM-S14-M3 Execution Report

## Scope

PROJECT-PLATFORM-S14-M3-T01 到 PROJECT-PLATFORM-S14-M3-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M3-T01 | non-core | static | not-required | not-required | No | M1-M2、S10 hierarchy/relation 与权限边界审计 |
| PROJECT-PLATFORM-S14-M3-T02 | non-core | unit | not-required | not-required | No | Gantt、bar、row、dependency、critical path 合同 |
| PROJECT-PLATFORM-S14-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V112 fresh/repeat migration |
| PROJECT-PLATFORM-S14-M3-T04 | core-user | e2e-real-isolated | real | isolated | No | S13 query + M2 dates + S10 hierarchy 公共投影 |
| PROJECT-PLATFORM-S14-M3-T05 | core-user | e2e-real-isolated | real | isolated | No | 双端可见依赖线与隐藏端点失败关闭 |
| PROJECT-PLATFORM-S14-M3-T06 | core-user | e2e-real-isolated | real | isolated | No | 有界关键路径、浮动量、循环/缺日期降级 |
| PROJECT-PLATFORM-S14-M3-T07 | core-user | e2e-real-isolated | real | isolated | No | canonical date mutation、one-winner、exact replay |
| PROJECT-PLATFORM-S14-M3-T08 | core-user | e2e-real-isolated | real | isolated | No | Web 层级/缩放/依赖/键盘/三视口 |
| PROJECT-PLATFORM-S14-M3-T09 | core-user | e2e-real-isolated | real | isolated | No | 查询上下文、偏好、离线输入与恢复 |
| PROJECT-PLATFORM-S14-M3-T10 | core-user | e2e-real-isolated | real | isolated | No | 六身份、循环、收权、并发、离线闭环 |
| PROJECT-PLATFORM-S14-M3-T11 | core-user | e2e-real-isolated | real | isolated | No | 100 行/200 依赖/32 深度/64 展开预算 |
| PROJECT-PLATFORM-S14-M3-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/object/event/owner 同步 |

## Completed Items

- 冻结 schema v1 GanttView、ScheduleBar、HierarchyRow、DependencyLine 和 CriticalPath 合同。
- 新增 V112 个人偏好、可重建排期索引和低基数统计，并纳入 project owner 与清理顺序。
- 组合 S13 受权查询、M2 日期绑定、S10 canonical hierarchy 与公开依赖投影。
- 新增双端可见依赖、最近可见祖先和有界关键路径/浮动量派生。
- 日期移动与拉伸复用 M2 canonical mutation，不建立第二套日期命令或关系权威。
- 新增 Web 甘特、层级展开、缩放、依赖/关键路径提示、键盘排期与响应式体验。
- 新增 service、PostgreSQL foundation 和真实隔离 Playwright 验收。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M3-T01 | 24 项逐项可追溯且不复制权威 | M1-M2 reports + hierarchy/relation/query contracts 审计 | roadmap/architecture gates | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S14-M3-T02 | identity、日期、层级、依赖、错误和上限明确 | `WorkItemGanttModels` + service validation | `WorkItemGanttServiceTests` | 不需要：合同与纯派生 | Done |
| PROJECT-PLATFORM-S14-M3-T03 | 复合边界、索引、清理和 owner 完整 | V112 + cleanup + table owner manifest | `WorkItemGanttFoundationIntegrationTests` | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S14-M3-T04 | 只用公共投影且 hidden ancestor 不泄漏 | calendar render + hierarchy provider + visible item map | unit + isolated API matrix | real isolated：四个受权身份得到同一受权层级 | Done |
| PROJECT-PLATFORM-S14-M3-T05 | 依赖双端分别鉴权且隐藏边无外形 | identity-only `WorkItemDependencyProjectionProvider` | visible-endpoint unit assertions | real isolated：可见依赖为 1，拒绝身份无标题/边 | Done |
| PROJECT-PLATFORM-S14-M3-T06 | 循环、缺日期和截断显式降级 | bounded topological derivation + degradation reasons | cycle/critical/float unit assertions | real isolated：关键路径和依赖提示可解释 | Done |
| PROJECT-PLATFORM-S14-M3-T07 | 不自动改写未授权事项且无半提交 | delegation to M2 transactional canonical mutation | M2 replay/conflict + Gantt delegation tests | real isolated：并发一胜一 409、重放稳定、撤权拒绝 | Done |
| PROJECT-PLATFORM-S14-M3-T08 | Web、键盘、深链和三视口可用 | typed API + panel + responsive CSS | web lint/build | real isolated：1440/1366/820 无 body overflow | Done |
| PROJECT-PLATFORM-S14-M3-T09 | 切换/断线不丢受权查询和输入 | controlled query/date/zoom state + preference + invalidation | web build + service preference tests | real isolated：offline zoom retained then saved | Done |
| PROJECT-PLATFORM-S14-M3-T10 | 六身份/循环/收权/并发闭环 | isolated fixture and cleanup | `project-platform-s14-m3.spec.ts` | real isolated：route-final 六身份闭环 | Done |
| PROJECT-PLATFORM-S14-M3-T11 | 确定性上界可复现 | frozen constants and request clamps | unit budget assertions + bounded DOM | real isolated：fixed fixture 与 bounded DOM；不声明生产容量 | Done |
| PROJECT-PLATFORM-S14-M3-T12 | 文档只声明 M3 事实 | target/current/module/object/event/owner/roadmap/report | planning/architecture/checkpoint gates | 不需要：文档同步 | Done |

## Deterministic Budget

| Surface | Reproducible upper bound | Enforcement |
| --- | --- | --- |
| Visible hierarchy rows | 100 | query limit and `MAX_ROWS` |
| Visible dependency lines | 200 | provider limit and both-endpoint visibility |
| Hierarchy depth | 32 | deterministic ancestor walk clamp |
| Expanded nodes | 64 | validated preference command |
| Dependency query identities | 200; 400 raw-edge safety limit | public provider contract |
| Schedule index | at most one row per visible work item and view rebuild | replaceable composite-key projection |
| Browser DOM | at most 100 hierarchy/schedule rows plus 200 dependency labels | same API bounds and horizontal containment |

这些是本地确定性门禁，不是生产吞吐、延迟、并发、SLO 或 S16 产能承诺。

## Code Changes

- Backend：Gantt models、repository/JDBC、service、controller、异常映射和公开 dependency projection。
- Database：V112 preference/schedule index/projection stats、project-space cleanup 与 owner manifest。
- Web：typed Gantt API、层级展开、缩放、依赖/关键路径、键盘排期和响应式样式。
- Tests/workbench：service/foundation tests、S14-M3 isolated route-final Playwright 与 V112 migration assertions。
- Docs：目标/当前架构、模块/对象/事件合同、当前路线与本报告。

## Validation

- Backend tests: `mvn -q -Dtest=WorkItemGanttServiceTests test` PASS.
- PostgreSQL/Flyway: `WorkItemGanttFoundationIntegrationTests` V001-V112 fresh/repeat PASS.
- Frontend build: `pnpm web:lint` PASS；`pnpm web:build` PASS.
- Workbench: all tests PASS.
- Local quality gate: `.local-reports/quality-gate-20260727T180922.md` light checkpoint PASS；正式 stage finish 重验。
- Browser smoke: `pnpm smoke:s14-isolated -- --spec project-platform-s14-m3.spec.ts` real isolated Chromium 1/1 PASS.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M4-T01 | 基线、时间线和组合 route-final 尚未实现 | 后续路线，不影响 M3 | PROJECT-PLATFORM-S14-M4 |

## Next Steps

- 下一轮从 `PROJECT-PLATFORM-S14-M4-T01` 开始；M3 完成不授权跳过 M4，也不提前实现 S15。
