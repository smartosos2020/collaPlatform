# PROJECT-PLATFORM-S14-M4 Execution Report

## Scope

PROJECT-PLATFORM-S14-M4-T01 到 PROJECT-PLATFORM-S14-M4-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M4-T01 | non-core | static | not-required | not-required | No | M1-M3 36 项、报告、迁移、边界和 gap 审计 |
| PROJECT-PLATFORM-S14-M4-T02 | non-core | unit | not-required | not-required | No | Baseline/Entry/Dependency/Diff/Timeline 生命周期合同 |
| PROJECT-PLATFORM-S14-M4-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V113 fresh/repeat、不可变触发器与 owner |
| PROJECT-PLATFORM-S14-M4-T04 | core-user | e2e-real-isolated | real | isolated | No | baseline create/list/compare/delete、exact replay |
| PROJECT-PLATFORM-S14-M4-T05 | core-user | e2e-real-isolated | real | isolated | No | activity/audit/workflow/relation 最小时间线 |
| PROJECT-PLATFORM-S14-M4-T06 | core-user | e2e-real-isolated | real | isolated | No | 当前权限重校准、隐藏条目/双端/标题失败关闭 |
| PROJECT-PLATFORM-S14-M4-T07 | core-user | e2e-real-isolated | real | isolated | No | Web 基线/diff/时间线、离线输入与三视口 |
| PROJECT-PLATFORM-S14-M4-T08 | core-user | e2e-real-isolated | real | isolated | No | 六身份、并发、收权、删除、离线和恢复 |
| PROJECT-PLATFORM-S14-M4-T09 | core-user | e2e-real-isolated | real | isolated | No | 20 基线/100 条目/200 时间线及组合 DOM 上界 |
| PROJECT-PLATFORM-S14-M4-T10 | non-core | integration | real | isolated | No | full gate、V113、协作/架构/安全与真实 route-final |
| PROJECT-PLATFORM-S14-M4-T11 | non-core | static | not-required | not-required | No | Program rev36、索引、目标/当前/模块/对象/事件与 S15 准入 |
| PROJECT-PLATFORM-S14-M4-T12 | non-core | static | not-required | not-required | No | 四份报告、48 Task、route-final、Stage none 与 Go 一致 |

## Completed Items

- 复核 M1-M3 的 36 项证据，无阻断项或需要 Reopen 的验收缺口。
- 冻结 schema v1 ScheduleBaseline、BaselineEntry、BaselineDependency、Diff 和 TimelineEvent 合同。
- 新增 V113 个人基线、不可变条目/依赖、精确命令回执和可重建时间线索引。
- 实现基线创建、列举、比较、删除、90 天保留和 caller-stable exact replay。
- 时间线通过 project owner 的 activity/workflow/relation 投影与 audit 公共合同组合最小事件。
- 基线 diff 和时间线每次从当前 Gantt 受权 identity 集合重新校准；不冻结标题、字段或权限。
- Web 交付基线、差异摘要、最小时间线、离线输入和响应式恢复体验。
- 修复并行 Gantt/Timeline 读取时三个可重建索引 delete/insert 竞态，改为复合键幂等 upsert。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S14-M4-T01 | 36 项可追溯且阻断必须 Reopen | M1-M3 reports、V110-V112、current/target/contracts 复核 | roadmap/architecture gates | 不需要：历史事实审计 | Done |
| PROJECT-PLATFORM-S14-M4-T02 | 来源版本、生命周期、保留、权限、错误和上限明确 | `WorkItemScheduleModels` + service validation | `WorkItemScheduleServiceTests` | 不需要：合同与纯派生 | Done |
| PROJECT-PLATFORM-S14-M4-T03 | 复合边界、唯一性、不可变、TTL、清理和 owner 完整 | V113 + triggers + cleanup + owner manifest | `WorkItemScheduleFoundationIntegrationTests` | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S14-M4-T04 | 只冻结 identity/date/dependency version 且精确重放 | transactional repository + immutable command JSON | service tests + isolated API | real isolated：create/replay/list/compare/delete PASS | Done |
| PROJECT-PLATFORM-S14-M4-T05 | 四类来源可解释、稳定去重且不成为 history | project projection + `AuditTimelineQuery` public contract | bounded merge/dedupe unit | real isolated：relation/activity/audit timeline 可见 | Done |
| PROJECT-PLATFORM-S14-M4-T06 | 收权后旧条目、数量和标题不泄漏 | current Gantt visible IDs before baseline/timeline projection | hidden baseline unit assertion | real isolated：outsider/enterprise 403/404 且无标题 | Done |
| PROJECT-PLATFORM-S14-M4-T07 | Web 长名、空态/错误、离线和三视口可理解 | typed API + controlled baseline input + panel/CSS | web lint/build | real isolated：offline 输入恢复、1440/1366/820 PASS | Done |
| PROJECT-PLATFORM-S14-M4-T08 | 六身份/并发/收权/删除/恢复无幽灵事实 | extended S14 isolated route-final fixture | one-winner/replay/revoke API sequence | real isolated：六身份闭环 PASS | Done |
| PROJECT-PLATFORM-S14-M4-T09 | 组合规模和交互上界可复现 | frozen constants + request clamps + upsert projections | budget unit assertions | real isolated：固定夹具、bounded DOM；不声明生产容量 | Done |
| PROJECT-PLATFORM-S14-M4-T10 | full gate 无阻断且证据 fresh | route-final profile + V113 system evidence + S14 runner | full backend/frontend/collaboration/static/security/Flyway | real isolated extended S14 route-final PASS | Done |
| PROJECT-PLATFORM-S14-M4-T11 | 当前事实、Program rev36 与 S15 准入同步 | Program/index/target/current/module/object/event/owner | planning/architecture/docs contracts | 不需要：文档同步 | Done |
| PROJECT-PLATFORM-S14-M4-T12 | 四份报告、48 Task、Stage none 一致且仅无阻断 Completed | roadmap completed、Program S14 Completed/current_stage none | work:finish route-final | real isolated route-final fresh PASS | Done |

## Deterministic Budget

| Surface | Reproducible upper bound | Enforcement |
| --- | --- | --- |
| Active personal baselines | 20 per user/space | list/create service clamp |
| Entries per baseline | 100 | current Gantt row bound |
| Dependencies per baseline | 200 | current Gantt dependency bound |
| Timeline events | 200 | request/service/repository/public-audit clamps |
| Retention | 90 days | immutable `expires_at`; expired rows are not listed/read |
| Timeline rebuild | one bounded replacement per user/view render | composite key, transactional delete + idempotent upsert |
| Browser DOM | at most 100 schedule rows, 200 dependencies and 100 displayed timeline events in current UI | API bounds and scroll containment |

这些是本地确定性门禁，不是生产吞吐、延迟、并发、SLO 或 S16 人员产能承诺。

## Code Changes

- Backend：schedule baseline/timeline models、repository/JDBC、service、controller 和 audit timeline 公共合同。
- Database：V113 baseline/entry/dependency/command/timeline index、不可变触发器、空间清理与 owner manifest。
- Concurrency：calendar/gantt/timeline 可重建索引写入改为复合键幂等 upsert。
- Web：typed schedule API、基线创建/比较/删除、时间线、离线恢复与响应式样式。
- Tests/workbench：service/foundation tests、扩展 S14 六身份真实隔离 route-final 与 V113 assertions。
- Docs：Program、专项索引、目标/当前架构、模块/对象/事件合同、路线与本报告。

## Validation

- Backend tests: `mvn -q -Dtest=WorkItemScheduleServiceTests test` PASS.
- PostgreSQL/Flyway: `WorkItemScheduleFoundationIntegrationTests` V001-V113 fresh/repeat PASS.
- Frontend build: `pnpm web:lint` PASS；`pnpm web:build` PASS.
- Local quality gate: `.local-reports/quality-gate-20260727T190827.md` records the successful fresh formal `route-final`; `.local-reports/quality-gate-20260727T184624.md` records the preceding run that exposed and drove correction of the V79→V113 exact migration count.
- Browser smoke: `pnpm smoke:s14-isolated -- --spec project-platform-s14-m3.spec.ts` extended M4 real isolated Chromium 1/1 PASS.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps

- 独立执行 archive-only 工作循环归档 S14，并在 Program revision 37 生成 S15 当前路线；不得在归档前提前实现 S15。
