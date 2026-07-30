# PROJECT-PLATFORM-S21-M4 Execution Report

## Scope
PROJECT-PLATFORM-S21-M4-T01 到 PROJECT-PLATFORM-S21-M4-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M4-T01 | non-core | integration | not-required | not-required | No | 对账一级入口、二级 Tab、路由、查询参数、面板 owner 与现有 E2E 依赖 |
| PROJECT-PLATFORM-S21-M4-T02 | non-core | unit | not-required | not-required | No | 以六身份及未来自定义角色夹具验证任务矩阵与拒绝边界 |
| PROJECT-PLATFORM-S21-M4-T03 | non-core | unit | not-required | not-required | No | 验证每个二级内容组只归入一个产品层 |
| PROJECT-PLATFORM-S21-M4-T04 | non-core | unit | not-required | not-required | No | 验证五入口顺序、状态规则、空态和逐入口 capability |
| PROJECT-PLATFORM-S21-M4-T05 | non-core | unit | not-required | not-required | No | 验证旧界面词到唯一业务术语的无冲突映射 |
| PROJECT-PLATFORM-S21-M4-T06 | non-core | static | not-required | not-required | No | 复核简洁默认、用户+空间偏好及离线安全回退合同 |
| PROJECT-PLATFORM-S21-M4-T07 | non-core | unit | not-required | not-required | No | 验证导航只消费服务端 capability，角色/模式不参与授权 |
| PROJECT-PLATFORM-S21-M4-T08 | non-core | unit | not-required | not-required | No | 验证工作项及旧配置深链落入唯一导航上下文并保留 query |
| PROJECT-PLATFORM-S21-M4-T09 | non-core | static | not-required | not-required | No | 复核来源、面包屑、返回任务和高级配置解释合同 |
| PROJECT-PLATFORM-S21-M4-T10 | non-core | integration | not-required | not-required | No | 校验 1440/1366/820、键盘、焦点、长名称和语义规范 |
| PROJECT-PLATFORM-S21-M4-T11 | core-user | e2e-real-isolated | real | isolated | No | 真实隔离浏览器走查场景模板/计划入口、查询保留、键盘、三视口与 outsider 最小披露 |
| PROJECT-PLATFORM-S21-M4-T12 | non-core | integration | not-required | not-required | No | 对账合同、架构、报告与 M5 Go 条件 |

## Completed Items
- 冻结“概览、工作项、项目管理、成员、设置”五入口，以及工作模型、流程与权限、自动化与协同、度量治理、场景模板五类高级配置。
- 建立完整 surface inventory、内容层归属、业务术语、模式、上下文返回、响应式和四场景威胁模型。
- 导航注册表的每个入口都声明服务端 capability；自定义角色可按 capability 工作，enterprise-admin 非成员明确留在治理 Shell。
- 建立旧配置路径与 `typeId/create/savedViewId/panel` 保留合同，并用真实隔离浏览器验证当前兼容基线。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M4-T01 | 所有现有入口、深链、owner、身份和测试依赖可定位 | `project-platform-s21-m4-information-architecture-audit.md` surface inventory | IA/secondary-tab 合同测试 | not-required：inventory 为静态治理证据 | Done |
| PROJECT-PLATFORM-S21-M4-T02 | 六身份及自定义角色能力/拒绝边界明确 | persona task matrix 与 capability-only 导航合同 | 自定义管理角色和 enterprise governance 单测 | not-required：角色矩阵由 T11 代表性闭环验证 | Done |
| PROJECT-PLATFORM-S21-M4-T03 | 每个内容块有唯一产品层和主入口 | content layer map 与 audit 内容目录 | 全量 secondary group 分类断言 | not-required：内容分类合同 | Done |
| PROJECT-PLATFORM-S21-M4-T04 | 五入口顺序、状态、空态和无权降级明确 | primary navigation registry | 五入口/状态/capability 单测 | not-required：M4 只冻结可验证原型，运行 Shell 属于 M5 | Done |
| PROJECT-PLATFORM-S21-M4-T05 | 术语无同义冲突且稳定 key 不变 | terminology migration table | legacy term 唯一性断言 | not-required：文案合同 | Done |
| PROJECT-PLATFORM-S21-M4-T06 | 简洁默认且模式不授权 | audit 的 mode/offline/fallback contract | 架构与合同检查 | not-required：偏好运行实现属于 M5 | Done |
| PROJECT-PLATFORM-S21-M4-T07 | 入口只由当前服务端 capability 决定 | 所有入口 `requiredAction`；enterprise governance 分流 | capability-only、自定义角色测试 | not-required：授权合同 | Done |
| PROJECT-PLATFORM-S21-M4-T08 | 旧深链与查询参数均有决定 | route context resolver 与兼容矩阵 | work-item/type/field/layout route 单测 | T11 验证 `panel` 与 `savedViewId` 保留 | Done |
| PROJECT-PLATFORM-S21-M4-T09 | 进入设置可解释并能返回来源 | audit 的 context entry/return contract | 文档合同检查 | not-required：运行面包屑属于 M5 | Done |
| PROJECT-PLATFORM-S21-M4-T10 | 三视口与无障碍规范可验证 | viewport/accessibility contract | 820/1366/1440 注册表断言 | T11 验证无横向溢出与键盘 Tab | Done |
| PROJECT-PLATFORM-S21-M4-T11 | 四场景可达且无权不泄漏 | scenario paths 与 threat model | 前端合同测试、真实 API outsider 404 | real isolated `project-platform-s21-m4.spec.ts` | Done |
| PROJECT-PLATFORM-S21-M4-T12 | 12 项证据齐备并给出 M5 Go | current/target architecture、本报告 | workbench checkpoint/finish 与 report gate | not-required：治理收口 | Done |

## Code Changes
- 新增集中信息架构静态合同与单元测试；保留现有二级 Tab 基底，不提前实现 M5 运行 Shell。
- 新增 M4 信息架构审计和真实隔离浏览器规格，同步 current/target architecture。

## Validation
- Backend tests: M4 不修改后端运行代码；stage profile 复验后端门禁。
- Frontend build: `pnpm web:lint`、`pnpm web:build`。
- Local quality gate: IA/secondary-tab 16 项 Node 合同测试；fresh workbench stage report `.local-reports/quality-gate-20260730T013713.md`。
- Browser smoke: real isolated `project-platform-s21-m4.spec.ts`。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M5 | 五入口、模式偏好、项目管理和设置中心尚未接入运行 Shell | 不阻断 M4 合同 Go；阻断任何 M5 完成声明 | M5-T01-T12 |

## Next Steps
- Engineering Go：独立启动 PROJECT-PLATFORM-S21-M5，将本合同接入运行时；不得用前端角色名或模式扩大授权。
- 不启动 M6，不生成 M8 真人反馈，不归档 S21。
