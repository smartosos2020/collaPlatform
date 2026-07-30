# PROJECT-PLATFORM-S21-M7 Execution Report

## Scope
PROJECT-PLATFORM-S21-M7-T01 到 PROJECT-PLATFORM-S21-M7-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M7-T01 | non-core | integration | not-required | not-required | No | 对账 M4-M6 三十六项、收藏/深链、路由、缓存、偏好和安全 finding；浏览器证据不适用 |
| PROJECT-PLATFORM-S21-M7-T02 | non-core | unit | not-required | not-required | No | 冻结路由、偏好、引导、缓存的版本、幂等、失败、回退、保留和删除边界；浏览器证据不适用 |
| PROJECT-PLATFORM-S21-M7-T03 | core-user | e2e-real-isolated | real | isolated | No | real legacy `/projects/:id` resolver、query/hash 保留、query patch、无循环/开放重定向/越权外形 |
| PROJECT-PLATFORM-S21-M7-T04 | core-user | e2e-real-isolated | real | isolated | No | real 本地/服务端偏好与 cache 幂等迁移、坏值简洁 fallback、跨身份隔离和业务状态保护 |
| PROJECT-PLATFORM-S21-M7-T05 | core-user | e2e-real-isolated | real | isolated | No | real workspace/space/user rollout、baseline、kill/temporarily-disabled、unknown、TTL 和无损恢复 |
| PROJECT-PLATFORM-S21-M7-T06 | core-user | e2e-real-isolated | real | isolated | No | real 六身份在模式、直达 URL、预加载、cache 和错误外形下的当前权限与最小披露 |
| PROJECT-PLATFORM-S21-M7-T07 | core-user | e2e-real-isolated | real | isolated | No | real 高级配置 manager gate、确认、expected version、审计、幂等、receipt 和 enterprise 非成员拒绝 |
| PROJECT-PLATFORM-S21-M7-T08 | core-user | e2e-real-isolated | real | isolated | No | real 低敏入口/模式/帮助/任务/路由错误/恢复遥测、sample limit、freshness、unknown 和 opt-out |
| PROJECT-PLATFORM-S21-M7-T09 | core-user | e2e-real-isolated | real | isolated | No | real 懒加载、请求合并、cache 失效、bundle、1440/1366/820 与无界增长防线 |
| PROJECT-PLATFORM-S21-M7-T10 | core-user | e2e-real-isolated | real | isolated | No | real S20 development/marketing/human-resources/delivery 新入口可达与 rollout/kill 前后业务事实不丢 |
| PROJECT-PLATFORM-S21-M7-T11 | core-user | e2e-real-isolated | real | isolated | No | real 完整测试、安全、离线、响应式、兼容路由和干净 isolated route-final |
| PROJECT-PLATFORM-S21-M7-T12 | core-user | e2e-real-isolated | real | isolated | No | real 迁移/安全/性能/可观测对账、P0/P1 清零、可恢复环境与 M8 Engineering Go |

## Completed Items
- 完成 M4-M6 三十六项追溯和 legacy/偏好/onboarding/cache/rollout/telemetry/性能/S20 兼容面审计，明确 S02 旧 Project 产品规格仅作历史意图。
- 冻结 canonical-only 路由迁移、受支持 query/hash、幂等 query patch、简洁 fallback、业务草稿保护和跨身份 cache 隔离合同。
- 建立 workspace/space/user 服务端 rollout 读取、canonical baseline、kill switch、TTL、六身份失败关闭和恢复演练边界；开关不授权。
- 建立 schema v1 低敏体验 telemetry 严格 JSON allowlist、批量/freshness/unknown 约束；浏览器不做概率采样，服务端在指标写入前只做一次确定性采样。
- 新增 M7 real isolated 浏览器规格，规格覆盖 legacy 解析、迁移/回退、六身份、离线、三视口、S20 四场景可达、业务状态不丢和 finally 清理。

## Acceptance Evidence

下表只登记本轮已经实际形成的代码、定向自动化、checkpoint 与真实隔离浏览器证据。
两次浏览器验证是使用不同 rollout 启动配置的两个独立运行，不是同一运行内热切换；
它们也不构成 M8 真人任务、consent 或人工验收。

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M7-T01 | M4-M6 三十六项及全部兼容面可追溯，未知调用方和未决安全项进入阻断判断 | `project-platform-s21-m7-implementation-audit.md` 第 1-2 节与 M4-M6 报告逐项对账 | 04:54 checkpoint 的 planning、architecture 与 work-cycle document gates 均 PASS | not-required：治理与公共合同对账不需要浏览器 | Done |
| PROJECT-PLATFORM-S21-M7-T02 | 版本、幂等、失败、回退、保留期和删除边界明确 | implementation audit 第 3-5 节冻结 route/preference/onboarding/cache/rollout 合同 | Web 定向单测 39/39 PASS，覆盖 route、cache、rollout 与 telemetry 合同 | not-required：unit/integration 合同证据完成验收 | Done |
| PROJECT-PLATFORM-S21-M7-T03 | 受支持 query、hash 和对象上下文不丢，无循环、开放重定向或权限外形泄漏 | canonical-only legacy resolver、route adapter 和 query patch | Web 定向单测 39/39 PASS；既有 resolver integration 覆盖 mapped/unavailable/unmigrated/failed 与非法目标 | real isolated：`project-platform-s21-m7.spec.ts` 两次独立运行均 1/1 PASS，验证 canonical/legacy 路由且无网络 mock | Done |
| PROJECT-PLATFORM-S21-M7-T04 | 偏好/cache 迁移幂等，失败回简洁默认且不删业务配置、草稿、收藏或审计 | v2 workspace/user/space UX cache envelope、30 天 recent TTL、显式 legacy draft recovery 和受保护业务键清单 | Web 定向单测 39/39 PASS，覆盖 legacy/坏值/过期/重复执行/quota/跨用户与 draft preservation | real isolated：两个独立 1/1 PASS 运行均完成偏好、onboarding、cache 恢复及业务快照保护 | Done |
| PROJECT-PLATFORM-S21-M7-T05 | 分阶段开放、kill 和恢复不授权且数据无损 | `GET /project-spaces/{spaceId}/experience-rollout`、canonical baseline、客户端规范化与 TTL gate | controller/service 定向测试 9/9 PASS；Web 定向单测 39/39 PASS | real isolated：kill-switch `temporarily_disabled` 独立运行 1/1 PASS；默认 `enabled` 独立运行 1/1 PASS；未宣称同一运行热切换 | Done |
| PROJECT-PLATFORM-S21-M7-T06 | 六身份在模式、直达、预加载、缓存和错误外形下均不能绕过当前 decision | capability-driven Shell、server-side member gate、identity-bound cache | 04:54 checkpoint backend targeted、frontend lint/build 与 architecture boundaries 均 PASS | real isolated：两个独立运行均覆盖六身份、直达 URL、缓存和最小披露，分别 1/1 PASS | Done |
| PROJECT-PLATFORM-S21-M7-T07 | 高级写操作保留 manager gate、确认、审计、幂等和 receipt | 现有 configuration/scenario/member/work-item 公共 owner 与 M7 presentation gate | owner 回归合同、Web 39/39 与 04:54 checkpoint 均 PASS | real isolated：两个独立运行均确认 rollout 状态不放宽 manager mutation gate，分别 1/1 PASS | Done |
| PROJECT-PLATFORM-S21-M7-T08 | 观测低敏，unknown、sample limit 和 freshness 显式，不成为权限或个人评分 | `POST /project-space-experience/telemetry` schema v1 严格 allowlist、client sanitizer 与 rollout telemetry policy | telemetry controller 与 rollout service 定向测试合计 9/9 PASS；Web 定向单测 39/39 PASS | real isolated：两个独立运行均验证 allowlist、offline/opt-out 和低敏 payload，分别 1/1 PASS | Done |
| PROJECT-PLATFORM-S21-M7-T09 | 隐藏面板不全量预取，固定夹具无未解释退化或无界增长 | `ProjectSpacesPage` optional surface `React.lazy`/`Suspense`、query merge/invalidation 与 rollout TTL cache | 04:54 checkpoint 的 frontend lint、build、chunk budget 和 route lazy-loading 均 PASS | real isolated：两个独立运行均覆盖 1440/1366/820、请求集合和溢出复核，分别 1/1 PASS | Done |
| PROJECT-PLATFORM-S21-M7-T10 | S20 四类场景在新入口可达且 rollout/kill 前后公共业务事实不丢 | 既有 scenario catalog/install/diff/upgrade、WorkItem 和 configuration owner | S20 历史回归保持不变；Web 定向单测 39/39 与 04:54 checkpoint PASS | real isolated：两个独立运行分别在 `temporarily_disabled` 与默认 `enabled` 下覆盖四场景业务快照，均 1/1 PASS | Done |
| PROJECT-PLATFORM-S21-M7-T11 | 完整门禁 fresh、无 P0/P1，本地证据不冒充生产批准 | M7 backend/frontend/browser 变更与 route-final 规格 | 04:54 checkpoint PASS；后端 9/9、Web 39/39、lint/build/chunk/lazy-loading 均为 fresh PASS | real isolated：两个独立环境运行各 1/1 PASS；不使用网络 mock，不冒充生产或真人试用证据 | Done |
| PROJECT-PLATFORM-S21-M7-T12 | P0/P1 清零、回退可用、环境可恢复时才给 M8 Engineering Go | implementation audit 第 11 节、当前 checkpoint 与独立浏览器结果完成对账 | 当前定向与 checkpoint 证据无 P0/P1；回退、kill 与可恢复基线均有验证 | real isolated：`temporarily_disabled` 与默认 `enabled` 两次独立运行各 1/1 PASS；没有执行或代签 M8 真人任务 | Done |

## Code Changes
- Backend：提供 canonical legacy resolver、experience rollout/kill evaluation、低敏 telemetry 接收与对应 unit/integration/security 回归。
- Frontend：提供 legacy route/query adapter、v2 workspace/user/space UX cache migration、canonical baseline、rollout TTL、低敏 telemetry client、lazy boundary 和身份边界失效。
- Browser：新增 `web/e2e/project-platform-s21-m7.spec.ts` real isolated 代表性闭环，不使用 `page.route` 或 `context.route`。
- Documentation：新增 M7 implementation audit 与本 v3 execution report；不改写 S02、S20 或 M4-M6 历史结论。

## Validation
- Backend tests: `.local-reports/quality-gate-20260730T045410-backend-targeted-tests.log` 为 PASS；telemetry controller 5/5、rollout service 4/4，合计 9/9。
- Frontend build: Web 定向单测 39/39 PASS；04:54 checkpoint 中 frontend lint、build、chunk budget 与 route lazy-loading 全部 PASS。
- Local quality gate: `.local-reports/quality-gate-20260730T055841.md` 为 stage PASS；`work:finish` 已登记 checksummed real-isolated browser evidence，并把当前工作循环置为 `complete`。
- Browser smoke: `project-platform-s21-m7.spec.ts` 使用真实 API、隔离身份/空间且无网络 mock；kill-switch `temporarily_disabled` 独立运行 1/1 PASS，默认 `enabled` 独立运行 1/1 PASS。两次为独立启动配置，不是同一运行内热切换。
- Historical full gates: `.local-reports/quality-gate-20260730T032408.md` 与 `.local-reports/quality-gate-20260730T050825.md` 保持 FAIL 历史。前者暴露 V139 latest-version 旧断言；后者只剩一次 PostgreSQL Testcontainers 启动超时和尚未登记 browserEvidence。失败测试随后在同一代码下定向重跑 1/1 PASS，browser evidence 由 `work:finish` 真实执行后登记；不得把历史失败报告改写为 PASS。
- Final full gate: `.local-reports/quality-gate-20260730T055932.md` 为 full PASS，覆盖 backend tests/package、frontend lint/build/chunk/lazy-loading、collaboration、workbench、architecture、Flyway、security、documentation 与 Git diff；Warnings/Failures 均为 None。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps

- 保留 `.local-reports/quality-gate-20260730T055932.md`、stage finish 报告和 real-isolated browser log 作为 M7 fresh 工程证据；任何后续回归必须 Reopen，不得改写本次结果。
- M8 只进入真人试用准备点。继续等待五名真实参与者、真人主持、逐人 consent、隔离环境与恢复门禁，不启动 M8-T01..T12。
- 不执行 M9，不归档 S21，不激活 S22。

## M8 Engineering Go

- Decision: `GO`，允许进入 `PROJECT-PLATFORM-S21-M8` 真人试用准备点。
- Evidence basis: Web 定向单测 39/39、后端 controller/service 定向测试 9/9、stage `work:finish` PASS、最终 full gate PASS，以及 kill-switch `temporarily_disabled` 与默认 `enabled` 两次独立 real isolated 浏览器运行各 1/1 PASS。
- Authorization boundary: 此 Go 不代表同一运行内热切换、生产切流、产品 Go、真人试用开始或人工结论。`project-platform-s21-m8-human-trial-preparation.md` 仍只是环境、脚本、consent 和恢复基线的准备材料；当前没有真实参与者登记、consent、主持执行或真人签署。
