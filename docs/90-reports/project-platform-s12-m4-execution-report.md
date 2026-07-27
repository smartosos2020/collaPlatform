# PROJECT-PLATFORM-S12-M4 Execution Report

## Scope
PROJECT-PLATFORM-S12-M4-T01 到 PROJECT-PLATFORM-S12-M4-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M4-T01 | non-core | static | not-required | not-required | No | M1-M3 36-task code/report/schema audit |
| PROJECT-PLATFORM-S12-M4-T02 | non-core | unit | not-required | not-required | No | activity/reminder/nudge/read-state contracts |
| PROJECT-PLATFORM-S12-M4-T03 | core-user | e2e-real-isolated | real | isolated | No | visible-only activity, pagination and read watermark |
| PROJECT-PLATFORM-S12-M4-T04 | core-system | system-real-isolated | not-required | isolated | No | due reminder preference, dispatch and dedupe |
| PROJECT-PLATFORM-S12-M4-T05 | core-user | e2e-real-isolated | real | isolated | No | permission, recipient, replay and cooldown |
| PROJECT-PLATFORM-S12-M4-T06 | core-user | e2e-real-isolated | real | isolated | No | notification invalidation and REST recalibration |
| PROJECT-PLATFORM-S12-M4-T07 | core-system | system-real-isolated | not-required | isolated | No | dry-run/rebuild without canonical mutation |
| PROJECT-PLATFORM-S12-M4-T08 | core-user | e2e-real-isolated | real | isolated | No | activity/reminder/nudge Web loop |
| PROJECT-PLATFORM-S12-M4-T09 | core-user | e2e-real-isolated | real | isolated | No | six identities, due, nudge, revoke, offline/reload |
| PROJECT-PLATFORM-S12-M4-T10 | core-system | system-real-isolated | not-required | isolated | No | full gate and V001-V105 migration matrix |
| PROJECT-PLATFORM-S12-M4-T11 | non-core | static | not-required | not-required | No | Program/current/target/module/event/report sync |
| PROJECT-PLATFORM-S12-M4-T12 | non-core | static | not-required | not-required | No | 48-task Go and route-final |

## Completed Items
- 复核 M1-M3 的 36 项实现、报告、V102-V104 与 owner 边界，无未关闭阻断。
- 冻结并交付 PersonalActivity、read watermark、Reminder/Preference/Dispatch、NudgeReceipt 与一致性恢复合同。
- V105 建立个人动态已读、提醒偏好、不可变催办回执及 notification 可见性失效基础。
- 个人动态与提醒只来自当前 S11 decision 可见 WorkItem；催办具备精确重放、接收者白名单、冷却、审计和通知 outbox。
- notification 列表/未读数通过公共 provider 重校准 WorkItem target，收权后不回显旧标题、路径或数量。
- Web 与真实隔离浏览器覆盖六身份、临期提醒、催办重放/冷却、收权、离线重载、长名称和三视口。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M4-T01 | 36 项逐项可追溯且阻断关闭 | M1-M3 reports/code/schema/owner audit | architecture/static gates | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S12-M4-T02 | 来源、版本、接收者、去重、隐私明确 | `PersonalCollaborationQuery` + V105 | contract compile + service tests | 不需要：公共合同 | Done |
| PROJECT-PLATFORM-S12-M4-T03 | hidden 不进入动态/计数/游标 | activity ledger query + visible WorkItem allowlist | `PersonalCollaborationServiceTests` | real isolated activity/read/revoke | Done |
| PROJECT-PLATFORM-S12-M4-T04 | 时区/偏好/到期/去重一致 | node schedule + reminder preference/dispatch | service tests + PostgreSQL migration | real isolated due reminder and notification | Done |
| PROJECT-PLATFORM-S12-M4-T05 | 受权、限频、回执和审计闭环 | immutable nudge receipt + 30-minute cooldown | replay/reuse/hidden tests | real isolated replay, 429 and outsider 404 | Done |
| PROJECT-PLATFORM-S12-M4-T06 | 收权、重连和 REST 收敛且 signal 无正文 | notification target batch recalibration + invalidation | backend/architecture regression | real isolated event projection and revoke | Done |
| PROJECT-PLATFORM-S12-M4-T07 | 只恢复可丢弃投影 | personal consistency dry-run/rebuild | service + API assertions | real isolated rebuild | Done |
| PROJECT-PLATFORM-S12-M4-T08 | 动态/提醒/催办 Web 可用 | NotificationsPage + ProjectWorkItemsPanel | lint/build | real isolated 1440/1366/820 and long names | Done |
| PROJECT-PLATFORM-S12-M4-T09 | 六身份、到期、收权、离线无泄漏 | six dynamic identities + private space fixture | isolated Playwright assertions | real isolated `project-platform-s12-m4.spec.ts` PASS | Done |
| PROJECT-PLATFORM-S12-M4-T10 | 完整门禁无阻断 | V001-V105 + 543-test full suite + isolated runner | route-final quality gate | real isolated PostgreSQL/API/Web/Playwright | Done |
| PROJECT-PLATFORM-S12-M4-T11 | 当前事实与 S13 准入同步 | Program revision 32 + architecture contracts | planning/document gates | 不需要：文档同步 | Done |
| PROJECT-PLATFORM-S12-M4-T12 | S12 Go、Stage none、48 Task 一致 | completed roadmap + four reports | `work:finish --validation-profile route-final` | 复用 T09 fresh real evidence | Done |

## Code Changes
- project 个人协作公共合同、API、repository/service、V105 schema 和最小恢复入口。
- notification owner 的 WorkItem target 可见性重校准与失效过滤，不增加跨 owner 私表读取。
- 默认 manual node schedule 使新 project/release WorkItem 产生真实可提醒 node task。
- Web 动态/提醒/偏好/派发与催办闭环，以及六身份真实 isolated Playwright。
- isolated browser runner 启用单实例单并发 event worker，保证通知投影实链路确定性；生产默认值未改变。

## Validation
- Backend tests: `PersonalCollaborationServiceTests` PASS；V001-V105 fresh/upgrade migration matrix 定向复验 PASS；完整套件由最终 route-final 复验。
- Frontend build: lint、TypeScript 与 Vite production build PASS。
- Local quality gate: `.local-reports/quality-gate-20260727T105300.md`，完整代码、PostgreSQL、前端、协作、架构、安全与文档合同 PASS，`work:finish --validation-profile route-final` 完成。
- Browser smoke: `web/e2e/project-platform-s12-m4.spec.ts`，real isolated PostgreSQL/API/Web/Playwright，1 passed。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- S12 Go。通过独立 archive-only 循环归档本路线并激活 S13；本轮不实现 S13 查询 DSL 或视图。
