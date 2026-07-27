# PROJECT-PLATFORM-S12-M1 Execution Report

## Scope
PROJECT-PLATFORM-S12-M1-T01 到 PROJECT-PLATFORM-S12-M1-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M1-T01 | non-core | static | not-required | not-required | No | 本地代码、路线、架构、API、表与事件全文审计 |
| PROJECT-PLATFORM-S12-M1-T02 | non-core | unit | not-required | not-required | No | public contract、reason 与 cursor 单元测试 |
| PROJECT-PLATFORM-S12-M1-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V102 fresh/repeat migration |
| PROJECT-PLATFORM-S12-M1-T04 | core-system | system-real-isolated | not-required | isolated | No | 真实 PostgreSQL 候选聚合与公共查询端口 |
| PROJECT-PLATFORM-S12-M1-T05 | non-core | unit | not-required | not-required | No | participant/node-task 多 reason 映射测试 |
| PROJECT-PLATFORM-S12-M1-T06 | core-system | system-real-isolated | not-required | isolated | No | S11 contextual decision 分批过滤 |
| PROJECT-PLATFORM-S12-M1-T07 | non-core | unit | not-required | not-required | No | workspace/user HMAC cursor、hard limit 与计数测试 |
| PROJECT-PLATFORM-S12-M1-T08 | core-system | system-real-isolated | not-required | isolated | No | durable invalidation watermark 与投影重读 |
| PROJECT-PLATFORM-S12-M1-T09 | core-user | e2e-real-isolated | real | isolated | No | 四 bucket、空态、长名称、deep link、1440/1366/820 |
| PROJECT-PLATFORM-S12-M1-T10 | core-user | e2e-real-isolated | real | isolated | No | 六身份、non-member/enterprise 零披露 |
| PROJECT-PLATFORM-S12-M1-T11 | core-system | system-real-isolated | not-required | isolated | No | bounded SQL/snapshot/decision/page budget |
| PROJECT-PLATFORM-S12-M1-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/event 同步 |

## Completed Items
- 审计 legacy dashboard、规范 WorkItem、participant、node task、S11 decision、platform/search/notification owner 边界，移除 workspace 对 project 私有实现的新增依赖。
- 冻结 `PersonalWorkQuery` 公共合同、四类 bucket、多 reason、最小摘要、capability、签名 cursor 与规范 deep link。
- V102 建立 project owner 的可重建个人投影、失效水位与不可变命令回执；表只保存 identity/version/time。
- 服务端一次有界 SQL 聚合 participant/node task 候选，按绑定 snapshot 缓存配置，并以最多 200 项一批的 contextual decision 过滤后分组。
- `work_item.changed`/`node_task.lifecycle` 使已知投影失效；读路径从 owner 事实与 S11 decision 补偿重读。
- `/api/personal-work` 与 workspace dashboard 交付我的待办、负责、参与、关注四类 Web 入口、空态与响应式深链。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S12-M1-T01 | API、表、事件、缓存、调用方与禁止依赖可定位 | roadmap/target/current + dashboard/WorkItem/platform/search/notification code audit | 全文件读取、`rg` 与 architecture gate | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S12-M1-T02 | identity/version/sort/time/error/invalid semantics 固定 | `project.contract.PersonalWorkQuery`、`PersonalWorkService` | `PersonalWorkServiceTests` | 不需要：公共合同 | Done |
| PROJECT-PLATFORM-S12-M1-T03 | 复合边界、唯一性、FK、索引、清理和 rebuild 完整 | V102 + table owner manifest | `PersonalWorkFoundationIntegrationTests` fresh 102/repeat 0 | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S12-M1-T04 | 四类来源服务端组合且不由前端拼列表 | `PersonalWorkRepository`/`JdbcPersonalWorkRepository`/service | targeted Maven + PostgreSQL integration | 不需要：系统端口 | Done |
| PROJECT-PLATFORM-S12-M1-T05 | 多 reason 单摘要且来源版本可解释 | participant role + node task mapping | multi-reason unit test | 不需要：映射规则 | Done |
| PROJECT-PLATFORM-S12-M1-T06 | 列表/分组/计数共享 S11 decision | `decideContextBatch` + snapshot/subject/evaluation context | permission + personal work targeted tests | 六身份真实流同时验证 | Done |
| PROJECT-PLATFORM-S12-M1-T07 | 稳定排序、签名游标、硬限与 hidden 后计数 | updated-at/id order、HMAC cursor、100/500/200 limits | cursor 跨用户拒绝与 hidden count unit tests | 六身份返回外形验证 | Done |
| PROJECT-PLATFORM-S12-M1-T08 | 乱序/重复事件经水位失效并重读 | `PersonalWorkInvalidationEventHandler` + V102 watermark | PostgreSQL schema/trigger/index test | 不需要：异步投影 | Done |
| PROJECT-PLATFORM-S12-M1-T09 | API/DTO/Web 四入口、空态、深链和窄屏可用 | `/api/personal-work`、dashboard DTO/page | web lint/build + targeted browser spec | `project-platform-s12-m1.spec.ts` real isolated | Done |
| PROJECT-PLATFORM-S12-M1-T10 | 六身份、跨用户和 hidden 零披露 | member candidate + S11 decision before grouping | unit non-member removal + e2e API matrix | real isolated：owner/admin/member/guest/non-member/enterprise-admin | Done |
| PROJECT-PLATFORM-S12-M1-T11 | SQL/端口调用和内存上界可复现 | 1 candidate SQL；distinct snapshot cache；decision batch 200；page 100；scan 500 | targeted Maven/PostgreSQL + stage gate | 不需要：本地预算，不声明生产容量 | Done |
| PROJECT-PLATFORM-S12-M1-T12 | 文档只声明 M1，M2-M4 不提前 | roadmap、report、target/current/module/event | checkpoint + finish document/architecture gates | 不需要：文档同步 | Done |

## Code Changes
- `server/src/main/java/com/colla/platform/modules/project/contract/PersonalWorkQuery.java`
- `server/src/main/java/com/colla/platform/modules/project/application/PersonalWorkService.java`
- `server/src/main/java/com/colla/platform/modules/project/infrastructure/JdbcPersonalWorkRepository.java`
- `server/src/main/java/com/colla/platform/modules/project/application/PersonalWorkInvalidationEventHandler.java`
- `server/src/main/resources/db/migration/V102__create_personal_work_foundation.sql`
- workspace dashboard contract/service、Web dashboard、targeted unit/PostgreSQL/browser tests。
- S12 isolated browser runner、table owner、当前路线和架构/模块/事件合同。

## Validation
- Backend tests: `PersonalWorkServiceTests` PASS；finish 执行 `PersonalWorkFoundationIntegrationTests` 与 targeted stage tests。
- Frontend build: checkpoint lint PASS；finish 执行 lint/build。
- Local quality gate: checkpoint `.local-reports/quality-gate-20260727T074124.md` PASS；finish 生成 fresh closing report。
- Browser smoke: `web/e2e/project-platform-s12-m1.spec.ts`，real isolated PostgreSQL/API/Web/Playwright。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S12-M2-T01 独立复核本报告与 V102；接入 platform recent/favorite resolver 校准、owner draft summary 和个人 card layout，不提前实现全局搜索或动态。
