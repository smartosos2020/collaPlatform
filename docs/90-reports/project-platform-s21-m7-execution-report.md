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

## Revision 51 Reopen Closures

- revision 51 的真实管理者配置走查撤销了原 M7 Engineering Go，并把 T10-T12 重开：legacy partial v1 的首次完整发布门禁形成死锁，模板安装只写 draft snapshot 而未水合 live 字段/布局作者态。
- revision 51 的同一修复循环允许 legacy partial v1 首次发布完整 v2，同时对完整 published baseline 保留 compatibility 失败关闭；模板安装/升级通过作者态 hydrator 在同一事务内水合字段、选项、布局节点和策略。
- 重复 requestId 返回已持久化 draft 聚合；字段、选项和布局按永久 key 确定性水合，并以 author-state→draft→installation 固定锁序及最终 installation aggregate version 校验关闭 install/upgrade 双事务死锁窗口；工作流动作 pending 时禁用以防 expected version 连击。
- 状态流编辑器使用稳定 `draft.id` key；dirty 上报父层并阻止校验、发布、放弃及其他配置写入，保存期间冻结输入。远端 stateFlow 基线变化进入显式冲突并保留本地内容，仅其他聚合片段变化时才安全重基。
- 后端定向 10/10、前端 publication helper 4/4、lint/build 与 real-isolated smoke 1/1 通过。Chrome 真实研发团队项目空间发布 requirement v2，创建 `REQUIREMENT-1`（`25c7a28a-c066-4967-8ecc-e1dcb1d1bbd5`，标题“研发需求链路验收：工作台个性化配置上线”），按 cancel→restore→start_progress→submit_test→test_failed→submit_test→test_passed→release→complete 完成，最终为 `done`/`#10`。
- 随后的真实概览走查再次撤销 M7 Engineering Go 并重开 T10-T12：公开摘要只表达类型 `active`，导致 `snapshot schema 0` 的 legacy partial 类型被误标为可创建，用户点击后才看到英文 `Published snapshot schema is not supported by the work item runtime`。
- 第二轮修复让公开类型摘要通过与创建表单相同的 `PublishedSnapshotAdapter.requireComplete` 计算 `configurationReady`；当前 versions 单次批量读取后逐版本 canonical/hash 校验，避免逐类型数据库 N+1。任何缺失、不完整、不支持或完整性失败均为 `false`，同时不暴露 schema、version、hash 或具体失败原因。概览区分“配置就绪/待发布配置”，manager 可进入配置，其他成员只获得最小求助说明；工作项筛选保留历史浏览但标注待配置。
- 创建弹窗继续防御陈旧缓存和旧深链：`unsupported_snapshot_schema` 转为中文可行动提示；发布后清除旧 create-form cache，OK、Enter 与 mutation 函数共享同一 readiness/error/fetching/online 门禁，不再把底层英文异常直接展示给用户或从缓存发出无效创建。拆分子项复用标准类型 query key，且同时要求配置就绪、active 空间和当前事项 `edit` 动作。
- readiness API 集成 9/9、adapter batch 单测 6/6、Web lint/build 与 real-isolated“待配置→管理员配置→发布→配置就绪→上线关单”1/1 通过后，第二轮阻断关闭并恢复 M8 Engineering Go。
- 上述 Chrome 与自动化结果只构成工程证据；没有执行 M8 真人任务、采集 consent 或形成任何人工采用结论。

## Acceptance Evidence

下表只登记已经实际形成的代码、定向自动化、checkpoint 与真实隔离浏览器证据。
原两次浏览器验证使用不同 rollout 启动配置，是两个独立运行而非同一运行内热切换；
revision 51 两轮重开分别增加配置发布/需求闭环以及配置就绪入口的 real-isolated smoke，并保留 Chrome 工程走查。所有这些证据都不构成 M8 真人任务、consent 或人工验收。

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
| PROJECT-PLATFORM-S21-M7-T10 | S20 四类场景在新入口可达且 rollout/kill 前后公共业务事实不丢 | 既有四场景证据保持有效；revision 51 补齐 legacy partial v1→完整 v2、模板 author-state hydration、幂等锁序，并以批量运行时同源 `configurationReady` 关闭未就绪入口 | 首轮后端 10/10、前端 helper 4/4；第二轮 readiness API 9/9、adapter 6/6、lint/build PASS | real-isolated 1/1 PASS 覆盖待配置→发布→配置就绪→需求关单；Chrome 真实研发空间另完成 requirement v2 与首个需求 | Done |
| PROJECT-PLATFORM-S21-M7-T11 | 完整门禁 fresh、无 P0/P1，本地证据不冒充生产批准 | 工作流 pending、状态流 dirty/冲突保护；概览、筛选、拆分子项和创建弹窗共享失败关闭 readiness 语义，批量查询、缓存失效、Enter/command guard 与中文错误兜底 | revision 51 后端 10/10 + readiness 9/9 + adapter 6/6、前端 helper 4/4、lint/build 均为 fresh PASS | real-isolated 1/1 PASS；同一自动化链路覆盖未就绪不请求表单、配置发布、创建、研发、提测、退回、复测、上线和关单，不使用网络 mock | Done |
| PROJECT-PLATFORM-S21-M7-T12 | P0/P1 清零、回退可用、环境可恢复时才给 M8 Engineering Go | implementation audit、第 11 节原证据与 revision 51 两轮 reopen closure 完成对账；公共摘要保持最小披露 | 两轮重开阻断均关闭，定向、构建和隔离 smoke 无 P0/P1；回退、kill 与可恢复基线继续有效 | real isolated 自动闭环 1/1 PASS，real Chrome `REQUIREMENT-1` 最终 `done`/`#10`；没有执行或代签 M8 真人任务 | Done |

## Code Changes
- Backend：提供 canonical legacy resolver、experience rollout/kill evaluation、低敏 telemetry 接收与对应 unit/integration/security 回归。
- Backend reopen closure：新增模板 author-state hydration，保持字段/选项/布局 identity 与空片段非破坏语义；重复 requestId 返回持久化聚合；永久 key 确定性写入、author-state→draft→installation 锁序和最终 installation aggregate version 校验关闭并发死锁/陈旧升级窗口；公开类型摘要增加由 runtime adapter 同源计算的最小布尔 `configurationReady`，并以单次 batch read 避免逐类型数据库 N+1。
- Frontend：提供 legacy route/query adapter、v2 workspace/user/space UX cache migration、canonical baseline、rollout TTL、低敏 telemetry client、lazy boundary 和身份边界失效。
- Frontend reopen closure：修正 legacy partial 首次完整发布 gate；工作流动作 pending 防连击并提供永久 action key 测试定位；状态流 dirty 上报父层、保存期冻结输入、远端基线冲突失败关闭且保留本地内容；概览入口呈现配置就绪/待配置状态和角色化下一步，筛选标注、拆分子项与创建弹窗对未就绪类型失败关闭，统一类型 cache key、发布后清除旧表单，OK/Enter/mutation 三层 guard，底层 schema 错误改为中文可行动提示。
- Browser：新增 `web/e2e/project-platform-s21-m7.spec.ts` real isolated 代表性闭环，不使用 `page.route` 或 `context.route`。
- Browser reopen closure：定向 real-isolated 规格新增概览待配置、未就绪不请求 create-form且创建按钮禁用、管理员配置深链和发布后配置就绪断言，并继续自动覆盖研发→提测→测试退回→复测→测试通过→上线→关单；Chrome 真实研发团队项目空间另行完成 requirement v2、取消/恢复到上线关单的工程链路。
- Documentation：新增 M7 implementation audit 与本 v3 execution report；不改写 S02、S20 或 M4-M6 历史结论。

## Validation
- Backend tests: `.local-reports/quality-gate-20260730T165538-backend-tests.log` 为当前代码的 full PASS，完整后端套件包含 readiness API 9/9 与 adapter 6/6。
- Frontend build: `.local-reports/quality-gate-20260730T165538-frontend-lint.log` 与 `.local-reports/quality-gate-20260730T165538-frontend-build.log` 为当前代码的 PASS，chunk budget 与 route lazy-loading 同步通过。
- Local quality gate: `.local-reports/quality-gate-20260730T165538.md` 已执行完整技术门禁且各技术步骤均 PASS；总状态仅因本节此前缺少工作台要求的固定证据标签而 FAIL。补齐标签并登记浏览器证据后，`.local-reports/quality-gate-20260730T174157.md` quick checkpoint 与 `.local-reports/quality-gate-20260730T174226.md` stage 文档/证据合同复核均为 PASS；最终 `.local-reports/quality-gate-20260730T174558.md` stage `work:finish` PASS，Warnings/Failures 均为 None。
- Browser smoke: `project-platform-s21-m7-reopened-real-isolated.spec.ts` 使用真实 API、隔离身份/空间且无网络 mock，fresh 1/1 PASS；既有 `project-platform-s21-m7.spec.ts` 的 kill-switch `temporarily_disabled` 与默认 `enabled` 两次独立运行各 1/1 PASS，二者不是同一运行内热切换。
- Revision 51 backend targeted tests: 10/10 PASS，覆盖模板字段/选项/布局作者态水合、重复 requestId、并发版本、真实 install/upgrade 双事务锁回归和跨空间非空 UUID 重绑定。
- Revision 51 readiness API tests: `WorkItemTypeConfigurationControllerIntegrationTests` 9/9 PASS，覆盖 legacy partial 摘要 `configurationReady=false`、完整 v2 发布后变为 `true`，并确认普通成员/访客响应不泄露 version、schema、hash 或 status。
- Revision 51 readiness adapter tests: `PublishedSnapshotAdapterTests` 6/6 PASS；新增 batch read 单次调用、完整/legacy/missing 三类逐绑定失败关闭，并继续覆盖 schema v1-v3、future schema 与 hash integrity。
- Revision 51 frontend helper tests: 4/4 PASS，覆盖无 baseline、legacy partial v1→完整 v2、完整 baseline compatibility 和版本列表失败关闭；frontend lint 与 build PASS。
- Revision 51 browser smoke: `project-platform-s21-m7-reopened-real-isolated.spec.ts` 使用真实 API、隔离空间且无网络 mock，1/1 PASS；覆盖概览待配置→未就绪不请求 create-form且创建按钮禁用→管理员配置→完整 v2 发布→概览配置就绪→需求研发/测试/上线/关单。
- Revision 51 Chrome engineering flow: requirement v2 已发布；`REQUIREMENT-1` 经 cancel→restore→start_progress→submit_test→test_failed→submit_test→test_passed→release→complete 后为 `done`/`#10`。
- First reopen backend tests: `.local-reports/quality-gate-20260730T131234-backend-targeted-tests.log` 为首轮重开时的 PASS，覆盖 `WorkItemConfigurationTemplateServiceIntegrationTests` 10/10；既有 telemetry controller 5/5、rollout service 4/4 证据保持有效。
- First reopen frontend build: `.local-reports/quality-gate-20260730T131234-frontend-lint.log` 与 `.local-reports/quality-gate-20260730T131234-frontend-build.log` 均为首轮重开时的 PASS，chunk budget 与 route lazy-loading 同步通过；publication helper 4/4 另行通过。
- First reopen quality gate: `.local-reports/quality-gate-20260730T131234.md` 为首轮配置发布/作者态修复的历史 checkpoint PASS，覆盖 architecture、planning、backend、frontend、documentation 与 Git diff 门禁；不冒充第二轮 readiness 的 fresh checkpoint。
- Second reopen evidence checkpoint: `.local-reports/quality-gate-20260730T174157.md` 为当前 readiness 修复的 quick PASS，并正式登记 real-isolated 浏览器证据；`.local-reports/quality-gate-20260730T174226.md` 为固定证据标签修正后的 stage 合同复核 PASS。
- Historical full gates: `.local-reports/quality-gate-20260730T032408.md` 与 `.local-reports/quality-gate-20260730T050825.md` 保持 FAIL 历史。前者暴露 V139 latest-version 旧断言；后者只剩一次 PostgreSQL Testcontainers 启动超时和尚未登记 browserEvidence。失败测试随后在同一代码下定向重跑 1/1 PASS，browser evidence 由 `work:finish` 真实执行后登记；不得把历史失败报告改写为 PASS。
- Original M7 historical full gate: `.local-reports/quality-gate-20260730T055932.md` 为本轮重开前的 full PASS，仅作为历史基线，不替代 readiness 修复后的 fresh checkpoint/finish 证据。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps

- 保留既有 full gate/stage finish 作为历史基线；revision 51 第二轮 readiness 的 fresh 证据以当前定向测试、构建、real-isolated smoke 及本轮 `work:finish` 为准。任何后续回归必须 Reopen，不得改写历史结果。
- M8 只进入真人试用准备点。继续等待五名真实参与者、真人主持、逐人 consent、隔离环境与恢复门禁，不启动 M8-T01..T12。
- 不执行 M9，不归档 S21，不激活 S22。

## M8 Engineering Go

- Decision: `GO`，允许进入 `PROJECT-PLATFORM-S21-M8` 真人试用准备点。
- Evidence basis: revision 51 首轮后端 10/10、前端 helper 4/4、Chrome `REQUIREMENT-1` 到 `done`/`#10`，第二轮 readiness API 9/9、adapter 6/6、lint/build、待配置至发布并自动研发关单 real-isolated 1/1，以及原始 M7 历史 Web 39/39、rollout/telemetry 后端 9/9、stage `work:finish`、full gate 和两次 rollout 独立 real-isolated PASS；本轮 readiness 已由 `.local-reports/quality-gate-20260730T174558.md` stage PASS 形成当前收口证据。
- Authorization boundary: 此 Go 不代表同一运行内热切换、生产切流、产品 Go、真人试用开始或人工结论。`project-platform-s21-m8-human-trial-preparation.md` 仍只是环境、脚本、consent 和恢复基线的准备材料；当前没有真实参与者登记、consent、主持执行或真人签署。
