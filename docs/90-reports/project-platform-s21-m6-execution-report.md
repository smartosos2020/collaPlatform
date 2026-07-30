# PROJECT-PLATFORM-S21-M6 Execution Report

## Scope
PROJECT-PLATFORM-S21-M6-T01 到 PROJECT-PLATFORM-S21-M6-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M6-T01 | non-core | integration | not-required | not-required | No | 对账 M4-M5、S20 四场景、配置发布和当前公共 owner；浏览器证据不适用 |
| PROJECT-PLATFORM-S21-M6-T02 | core-user | e2e-real-isolated | real | isolated | No | real 默认状态、版本保存、dismiss/resume/reset、CAS 和跨用户/空间隔离 |
| PROJECT-PLATFORM-S21-M6-T03 | core-user | e2e-real-isolated | real | isolated | No | real 四场景/基础空间选择，选择前后安装、类型和发布事实无副作用 |
| PROJECT-PLATFORM-S21-M6-T04 | core-user | e2e-real-isolated | real | isolated | No | real 管理者预览、确认边界、成员、首项、交接与可恢复 checklist |
| PROJECT-PLATFORM-S21-M6-T05 | core-user | e2e-real-isolated | real | isolated | No | real 成员找到工作、创建/更新、评论、附件、流转和通知入口 |
| PROJECT-PLATFORM-S21-M6-T06 | core-user | e2e-real-isolated | real | isolated | No | real 按任务模板、字段/页面、流程、权限、发布和增强能力顺序渐进导航 |
| PROJECT-PLATFORM-S21-M6-T07 | core-user | e2e-real-isolated | real | isolated | No | real 网络观察只调用既有 owner API，并保留 expected version、稳定 request ID 和 receipt |
| PROJECT-PLATFORM-S21-M6-T08 | core-user | e2e-real-isolated | real | isolated | No | real 草稿、校验、影响、冲突、发布结果和 rollback-as-new-draft 语义 |
| PROJECT-PLATFORM-S21-M6-T09 | core-user | e2e-real-isolated | real | isolated | No | real 业务术语、场景示例、“何时需要”和最小工程诊断 |
| PROJECT-PLATFORM-S21-M6-T10 | core-user | e2e-real-isolated | real | isolated | No | real 六身份、隐藏对象、空间只读、离线和安全求助路径 |
| PROJECT-PLATFORM-S21-M6-T11 | core-user | e2e-real-isolated | real | isolated | No | real 低敏步骤遥测、dismiss、显式 resume 和 reset；不包含内容或个人评分 |
| PROJECT-PLATFORM-S21-M6-T12 | core-user | e2e-real-isolated | real | isolated | No | real 四路径+基础空间、1440/1366/820、键盘、长名称、多标签冲突和清理 |

## Completed Items
- 新增 workspace/space/user 隔离的版本化 onboarding 状态、CAS command、精确 request replay、dismiss/resume/reset、flow upgrade 与低敏遥测。
- 建立研发、市场、HR、交付和基础空间五个起步方式；路径选择只写体验状态，服务端明确返回“不安装、不发布”事实。
- 新增管理者、成员和访客三类渐进 checklist；所有业务步骤只链接现有公共 owner，提示回执不替代安装、配置、发布、交接或通知结果。
- 在项目空间 Shell 增加可恢复 Drawer、页面语境帮助、权限/只读/离线失败关闭和匿名遥测退出开关。
- 新增六身份、五空间真实隔离 E2E 规格，覆盖业务零副作用、跨用户/空间隔离、CAS、离线、三视口、键盘、长名称和清理闭环。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M6-T01 | M4/M5、S20 与公共 owner 边界逐项可追溯，不创建第二业务权威 | M4/M5 execution/audit、S20 M1-M5 reports 与 `project-platform-s21-m6-implementation-audit.md` | 实现审计冻结状态、路径、身份、owner、离线和遥测不变量 | not-required：本项为实现前治理与公共合同对账 | Done |
| PROJECT-PLATFORM-S21-M6-T02 | 状态按 workspace/space/user 隔离，版本化、可恢复且 CAS one-winner | V141、`ProjectSpaceOnboardingController`、service、repository 与 domain | service/repository 测试覆盖默认、命令迁移、精确 replay、409、reset 和隔离 | real isolated M6 E2E 覆盖默认状态、跨用户/空间、dismiss/resume/reset 与并发 200/409 | Done |
| PROJECT-PLATFORM-S21-M6-T03 | 五个起步方式可选，选择不安装、不创建类型、不发布 | `PROJECT_SPACE_ONBOARDING_STARTING_POINTS`、starting-point command 与 chooser | 前端合同测试验证五项和“不安装/不发布”；E2E 比较类型版本与 installation 前后事实 | real isolated M6 E2E 在五个私有空间保存四场景+基础空间并验证业务事实完全不变 | Done |
| PROJECT-PLATFORM-S21-M6-T04 | 管理者按选择、预览、配置、成员、首项和交接渐进推进 | `ProjectSpaceOnboardingCatalog.managerSteps` 与 Drawer checklist | service 测试验证 manager track、依赖、owner contract 与提示回执边界 | real isolated owner/admin Drawer 显示空间设置清单，场景与基础空间产生相应步骤 | Done |
| PROJECT-PLATFORM-S21-M6-T05 | 成员从找工作到通知，访客只有受权只读步骤 | catalog member/guest steps、`canOpenOnboardingStep` 和 owner path resolver | 前端单测覆盖 capability、online、readOnly、blocked 与安全 route gate | real isolated member/guest Drawer 验证无路径选择、差异化清单和访客只读文案 | Done |
| PROJECT-PLATFORM-S21-M6-T06 | 高级配置按工作模型、流程权限、发布、自动化和指标顺序按需进入 | M4 IA 注册表、M5 secondary tabs 与 onboarding contextual help | IA/secondary-tab/onboarding 单测验证 canonical route 和兼容深链 | real isolated checklist 只导航当前空间 owner 页面，不复制配置视图或业务状态 | Done |
| PROJECT-PLATFORM-S21-M6-T07 | 编排只写 onboarding 状态；安装、配置、成员和工作项仍由既有 owner 鉴权 | onboarding API client 仅调用 GET/command/telemetry；catalog 保存 owner contract/path | service tests 固定 `experience_only/false/false`；E2E 快照类型与安装事实 | real isolated UI 保存仅观察到 `/onboarding/commands`，未出现 install/config/publish 业务写请求 | Done |
| PROJECT-PLATFORM-S21-M6-T08 | 草稿、校验、影响、发布和 rollback-as-new-draft 语义不被引导改写 | contextual help 与 publish checklist 链接现有 configuration draft owner | 既有 configuration draft/publication tests；onboarding test 禁止把路由访问当完成 | real isolated manager 清单将用户带到公共 owner，并明确“草稿不等于生效” | Done |
| PROJECT-PLATFORM-S21-M6-T09 | 每个页面提供业务术语、“何时需要”和安全下一步 | `contextualOnboardingHelp`、step copy、fallback copy 与 error presentation | 前端单测验证工作项、工作模型、CAS 和隐藏对象说明 | real isolated Drawer 显示当前页面帮助、业务语言步骤和可行动的安全解释 | Done |
| PROJECT-PLATFORM-S21-M6-T10 | 六身份、只读、隐藏对象与离线均失败关闭且最小披露 | member-space gate、track/readOnly catalog、UI online/capability/write gates | service/frontend tests 覆盖 404、role track、blocked 和 offline gates | real isolated 六身份 API/UI 验证；member/guest install 被拒，outsider/enterprise 404 且无名称/路径泄漏 | Done |
| PROJECT-PLATFORM-S21-M6-T11 | 遥测仅含稳定 step/outcome/duration/error，并支持显式 opt-out | telemetry batch endpoint、枚举 allowlist、TTL repository 与匿名分享开关 | service/repository tests 覆盖 allowlist、批量上限、去重、opt-out 和清理 | real isolated M6 E2E 发送低敏事件、启用 opt-out，并验证 command 精确 replay | Done |
| PROJECT-PLATFORM-S21-M6-T12 | 真实隔离代表性闭环覆盖路径、身份、恢复、视口、键盘和清理 | `project-platform-s21-m6.spec.ts` 与动态 identity/private-space fixtures | 专项 ESLint 与 Playwright discovery/compile 发现 1 个 `@smoke` flow | real isolated E2E 覆盖四路径+基础空间、1440/1366/820、键盘、长名、离线、CAS 和 finally 清理 | Done |

## Code Changes
- Backend：新增 V141、onboarding domain/catalog/controller/service/repository、低敏 telemetry 与 service/PostgreSQL 集成测试。
- Frontend：新增 onboarding contract/API client/Drawer、起步方式、角色 checklist、语境帮助、离线/只读/错误 gate 与单元测试。
- Browser：新增 `project-platform-s21-m6.spec.ts` 真实隔离代表性闭环，未使用 `page.route` 或 `context.route`。
- Documentation：新增 M6 implementation audit 与本 v3 execution report；未改写 S20 历史结论。

## Validation
- Backend tests: `mvn -q -f server/pom.xml -Dtest=ProjectSpaceOnboardingServiceTests test` 与 `mvn -q -f server/pom.xml -Dtest=ProjectSpaceOnboardingRepositoryIntegrationTests test` — PASS；后者以 PostgreSQL/Testcontainers 验证 V001-V141。
- Frontend build: onboarding 定向单测 8/8、`pnpm web:lint`、`pnpm web:build` — PASS；checkpoint logs `.local-reports/quality-gate-20260730T023459-frontend-lint.log`、`.local-reports/quality-gate-20260730T023459-frontend-build.log`。
- Local quality gate: stage checkpoint PASS；fresh report `.local-reports/quality-gate-20260730T023459.md`。
- Browser smoke: `pnpm smoke:s21-isolated -- --spec project-platform-s21-m6.spec.ts` — PASS，real isolated 1/1（32.2 秒），未使用路由 mock。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 完成 M6 专项执行和 stage checkpoint 后，独立启动 PROJECT-PLATFORM-S21-M7；不越过 M7 提前开始 M8 真人试用。
- 自动化只证明工程闭环，不把维护者观察或浏览器测试写成真人易用性、学习成本或采用结论。
