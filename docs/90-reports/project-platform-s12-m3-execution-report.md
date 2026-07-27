# PROJECT-PLATFORM-S12-M3 Execution Report

## Scope
PROJECT-PLATFORM-S12-M3-T01 到 PROJECT-PLATFORM-S12-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M3-T01 | non-core | static | not-required | not-required | No | M1-M2 reports/code/schema/owner audit |
| PROJECT-PLATFORM-S12-M3-T02 | non-core | unit | not-required | not-required | No | document/hit/facet/cursor/deep-link contracts |
| PROJECT-PLATFORM-S12-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | V104 projection, outbox consumer and version waterline |
| PROJECT-PLATFORM-S12-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | keyword and controlled filter query |
| PROJECT-PLATFORM-S12-M3-T05 | core-user | e2e-real-isolated | real | isolated | No | S11 decision before facet/page/cursor/highlight |
| PROJECT-PLATFORM-S12-M3-T06 | core-user | e2e-real-isolated | real | isolated | No | stable page, long title and allowed-field highlight |
| PROJECT-PLATFORM-S12-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | archive deletion, stale waterline and resumable rebuild |
| PROJECT-PLATFORM-S12-M3-T08 | core-user | e2e-real-isolated | real | isolated | No | resolver-calibrated WorkItem deep link and zero-disclosure shape |
| PROJECT-PLATFORM-S12-M3-T09 | core-system | system-real-isolated | not-required | isolated | No | API/DTO, low-cardinality metrics and admin rebuild |
| PROJECT-PLATFORM-S12-M3-T10 | core-user | e2e-real-isolated | real | isolated | No | filters/deep-link/long name at 1440/1366/820 |
| PROJECT-PLATFORM-S12-M3-T11 | core-user | e2e-real-isolated | real | isolated | No | six identities, stale/order/rebuild/cursor/budget |
| PROJECT-PLATFORM-S12-M3-T12 | non-core | static | not-required | not-required | No | roadmap/target/current/module/event/report sync |

## Completed Items
- 复核 M1-M2 的 24 项实现与 owner 边界，无未关闭阻断。
- 冻结 WorkItem 最小索引、SearchFacet、签名 cursor、筛选和规范深链合同。
- V104 与 `work_item.changed` consumer 交付幂等、乱序安全、可删除和可续跑投影。
- WorkItem 召回经公共端口批量执行 S11 contextual decision；无权命中不影响响应 facet/page/cursor。
- 搜索页交付跨空间/类型/状态/参与角色筛选、分页、长名称和规范 WorkItem 深链。
- 管理员恢复入口只重建投影；低基数指标不携带 query、identity、标题或权限信息。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M3-T01 | 24 项可追溯且索引非权威 | M1-M2 reports/contracts/schema audit | architecture/static gates | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S12-M3-T02 | 最小字段、版本、排序、权限态明确 | `SearchModels`、V104、search API | compile + contract test | 不需要：公共合同 | Done |
| PROJECT-PLATFORM-S12-M3-T03 | 可重建、幂等、乱序安全且无 hidden | `JdbcSearchRepository` + event handler | PostgreSQL V001-V104 + projection waterline tests | 不需要：系统投影 | Done |
| PROJECT-PLATFORM-S12-M3-T04 | 白名单筛选与硬限 | controller/service/repository placeholders | targeted Maven + API e2e | real isolated combined filters | Done |
| PROJECT-PLATFORM-S12-M3-T05 | decision 后才生成 facet/page/cursor | platform search provider + search service | six-identity API matrix | real isolated owner/admin/member/guest/outsider/enterprise | Done |
| PROJECT-PLATFORM-S12-M3-T06 | 稳定排序、允许片段和长文本 | bounded index fields + stable SQL order + Web mark | cursor uniqueness/assertions | real isolated long title 1440/1366/820 | Done |
| PROJECT-PLATFORM-S12-M3-T07 | 收权/归档/乱序/rebuild 收敛 | single-object delete-before-upsert + batch cursor | persistence integration + archive/rebuild e2e | real isolated archive zero disclosure | Done |
| PROJECT-PLATFORM-S12-M3-T08 | resolver 校准且无标题泄漏 | WorkItem resolver path; legacy issue unchanged | knowledge anchor compatibility regression | real isolated deep-link navigation | Done |
| PROJECT-PLATFORM-S12-M3-T09 | API/DTO/指标/恢复入口低披露 | facets/cursor DTO, Micrometer scope-only tags, admin batch API | compile + PostgreSQL rebuild | 不需要：治理入口 | Done |
| PROJECT-PLATFORM-S12-M3-T10 | 页面交互和三视口可用 | `SearchPage` and `searchApi` | lint/build | `project-platform-s12-m3.spec.ts` real isolated | Done |
| PROJECT-PLATFORM-S12-M3-T11 | 零泄漏、稳定分页、无幽灵命中 | 500 scan/200 decision/50 page/20 spaces hard limits | targeted system tests + tampered cursor | real isolated six identities/rebuild/archive | Done |
| PROJECT-PLATFORM-S12-M3-T12 | owner/失效/M4 输入同步 | roadmap/target/current/module/event/report | checkpoint/document gates | 不需要：文档同步 | Done |

## Code Changes
- search WorkItem projection、受控筛选、批量权限校准、facet、签名 cursor、指标与治理重建。
- platform 横向搜索 provider 合同与 project owner 的最小文档/绑定 snapshot/S11 decision 实现；search 零 project 私表读取。
- V104 search metadata/index、table owner、事件订阅与投影测试。
- Web 搜索筛选、分页、对象标签、规范深链与 isolated Playwright 流程。

## Validation
- Backend tests: `SearchProjectionDomainEventHandlerTests`、`SearchProjectionPersistenceIntegrationTests`、`SearchCollaborationIntegrationTests` 共 7 项 PASS。
- PostgreSQL/Flyway: PostgreSQL 16，V001-V104 fresh/repeat validation PASS。
- Frontend build: lint PASS；TypeScript/Vite production build PASS。
- Local quality gate: `.local-reports/quality-gate-20260727T091157.md` 首次正式收口发现并阻断跨 owner 读取；重构后 architecture boundary 独立复验 PASS，最终 `work:finish` 生成 fresh stage report。
- Browser smoke: `web/e2e/project-platform-s12-m3.spec.ts`，real isolated PostgreSQL/API/Web/Playwright。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S12-M4-T01 独立复核 M1-M3；交付动态、提醒、催办、个人通知和 S12 route-final，不提前实现 S13。
