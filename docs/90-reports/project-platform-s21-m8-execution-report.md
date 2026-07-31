# PROJECT-PLATFORM-S21-M8 Execution Report

## Scope

PROJECT-PLATFORM-S21-M8-T01 到 PROJECT-PLATFORM-S21-M8-T12。

本轮按 Program revision 52 收敛项目空间角色分层 UX：保留五条 canonical route，把入口组织为成员工作区、项目管理、空间管理三类任务分区；普通成员只消费服务端 capability、配置就绪与当前参与范围允许的内容，Owner/受权管理员获得集中管理入口与元数据预览。结论边界仅为 M9 的工程 UX Go，不代表 M9 真人试用或最终产品 Go。

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M8-T01 | non-core | static | not-required | not-required | No | 对 M4-M7、管理者走查、现有 route/capability 与证据边界完成代码和文档审计 |
| PROJECT-PLATFORM-S21-M8-T02 | core-user | e2e-real-isolated | real | isolated | No | 从旧 panel 深链进入唯一 canonical owner，并保留允许的查询参数与 hash |
| PROJECT-PLATFORM-S21-M8-T03 | core-user | e2e-real-isolated | real | isolated | No | 六类真实身份读取同一服务端 surface/type action，custom-role fixture 只按 capability 投影 |
| PROJECT-PLATFORM-S21-M8-T04 | core-user | e2e-real-isolated | real | isolated | No | 发布一个完整类型，成员从首页进入事项详情并看到可行动摘要，未就绪类型失败关闭 |
| PROJECT-PLATFORM-S21-M8-T05 | core-user | e2e-real-isolated | real | isolated | No | 成员首页只显示当前空间个人工作、可用类型和协作动态，并可进入真实事项 |
| PROJECT-PLATFORM-S21-M8-T06 | core-user | e2e-real-isolated | real | isolated | No | Owner 看到唯一项目管理分区，成员与访客不挂载该入口，写能力取服务端 manage_project |
| PROJECT-PLATFORM-S21-M8-T07 | core-user | e2e-real-isolated | real | isolated | No | 管理者两层内进入设置管理首页、任务模板选择后的字段/页面/流程权限及嵌套自动化配置，普通成员不可见 |
| PROJECT-PLATFORM-S21-M8-T08 | core-user | e2e-real-isolated | real | isolated | No | Owner/管理员预览成员与访客元数据入口，隐藏内容请求为零，退出恢复原 URL 与焦点 |
| PROJECT-PLATFORM-S21-M8-T09 | core-user | e2e-real-isolated | real | isolated | No | 成员详情、未配置、离线、只读和拒绝状态使用中文行动说明且不泄漏 UUID/schema/hash |
| PROJECT-PLATFORM-S21-M8-T10 | core-user | e2e-real-isolated | real | isolated | No | 新建空间依次验证空白、克隆、四类场景模板及影响预览，不冒充自动安装或复制 |
| PROJECT-PLATFORM-S21-M8-T11 | core-user | e2e-real-isolated | real | isolated | No | 六类真实身份、custom-role fixture、三视口、键盘焦点、离线、多标签与兼容深链 route-final |
| PROJECT-PLATFORM-S21-M8-T12 | non-core | e2e-real-isolated | real | isolated | No | 对账 P0/P1/P2、自动化和真实隔离证据，验证环境清理后给出 M9 工程 UX Go |

## Completed Items

- 冻结三类任务分区与五条 canonical route；每个二级 panel 只有一个 owner，旧 panel 深链执行幂等兼容迁移。
- 2026-07-31 真人试用回归修复主导航跨 surface 携带旧 `panel` 后被 canonical owner 拉回概览的问题；主导航与切换空间现在只保留跨 surface 的 `source` 与安全 hash。
- 成员默认落到概览，只看到当前空间的待办、负责、参与、关注、可用工作项与协作动态；个人工作在服务端按 `spaceId` 先过滤再分页。
- 类型摘要新增与运行时权限一致的 `availableActions`；未发布或运行时不完整的类型返回空动作并从成员入口失败关闭。
- 详情首屏新增当前状态、负责人、截止时间和下一步动作，普通流程不显示 UUID、hash、schema 或内部英文状态 key。
- 项目管理成为计划、资源、风险、交付与指标的唯一聚合面；空间管理集中基本信息、工作模型、流程权限、自动化、度量、模板与生命周期。
- 修复空间设置入口重复落点：工作模型按任务模板、字段、表单与页面、发布配置拆分，流程与权限先显式选择任务模板并聚焦同一配置草稿内的专属编辑区。
- 新增 Owner/管理员可用的 member/guest surface preview；返回固定八字段元数据，禁止内容加载，预览不授权且退出恢复上下文。
- 新建空间明确区分空白、场景模板与克隆预检；场景选择只保存引导，克隆不暗示复制成员、事项、附件或权限。
- 完成六类真实身份边界与 custom-role capability-only fixture；custom-role 不被描述为已交付的后端 membership role。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M8-T01 | 路径、角色任务、重复入口、finding 与证据边界完整 | revision 52 路线图、信息架构合同与本报告对账；不使用审计图替代实现事实 | `planning check` 通过 revision 52、10 Milestone、120 Task；quick checkpoint PASS | Browser not required；该项以代码/文档静态审计关闭 | Done |
| PROJECT-PLATFORM-S21-M8-T02 | 三类任务分区与五 route 兼容，深链上下文无损且无循环 | `projectSpaceInformationArchitecture.ts`、`projectSpaceSurfaceContract.ts`、`projectSpaceCrossSurfaceLocation` 与主导航/切空间接线 | route/IA/surface 定向合同 26/26，frontend lint/build PASS | real shared 页面五入口可切换；real isolated 从 `panel=activity` 点击工作项后稳定停留 canonical `/work-items`，保留 source、清除旧 panel，1/1 PASS | Done |
| PROJECT-PLATFORM-S21-M8-T03 | 导航、按钮、类型与动作只取服务端 capability 和 readiness | `ProjectSpaceDtos` surface action assembler、`WorkItemTypeConfigurationService`、`projectSpaceMemberContent.ts` | 后端相关测试 31/31；custom-role capability fixture 通过 | real isolated：owner/admin/member/guest/outsider/enterprise-admin surface 与类型动作边界正确 | Done |
| PROJECT-PLATFORM-S21-M8-T04 | 未就绪类型不可创建，成员两次内到事项，详情给出行动摘要且不泄漏技术标识 | `ProjectWorkItemsPanel.tsx`、`WorkItemWorkflowPanel.tsx`、类型 readiness/action 投影 | frontend lint/build PASS；类型权限集成 9/9 | real isolated：发布前动作为空，发布后 member 可创建、guest 只读；详情摘要与技术字段负断言通过 | Done |
| PROJECT-PLATFORM-S21-M8-T05 | 成员首页只含当前参与工作、可用类型与动态，不进入配置后台 | scoped personal-work/activity controller、service、repository 与 `ProjectSpaceOverview` | PersonalWork/Collaboration/Snapshot 测试 15/15 | real isolated：成员仅见概览/工作项，当前 watcher 事项与深链可达，管理分区均未挂载 | Done |
| PROJECT-PLATFORM-S21-M8-T06 | 项目管理为唯一管理聚合面，成员面只保留执行上下文 | management secondary tabs、`ProjectSpaceManagementPanel`、`manage_project` 写门禁 | secondary-tab 与 IA 合同测试通过；frontend build PASS | real isolated：Owner 导航精确为五入口，member/guest 精确为两入口且无额外管理按钮 | Done |
| PROJECT-PLATFORM-S21-M8-T07 | 空间管理集中健康、待办、最近入口、危险操作和全部配置 | `ProjectSpaceSettingsPanel`、显式任务模板选择、配置落点合同与 flow-access 聚焦区 | 配置落点合同 2/2、route/tab 合同、frontend lint/build PASS | real isolated：设置默认管理首页四分区、自动化规则两层及任务模板/字段/表单页面/流程权限四条实际点击落点均通过 | Done |
| PROJECT-PLATFORM-S21-M8-T08 | 预览只含目标身份入口元数据，不授权、不加载隐藏内容，退出恢复上下文 | `/surface-preview`、固定八字段 DTO、`ProjectSpaceShell` preview modal | Controller/DTO 7/7；owner/admin 200、member/guest 403、非成员 404、非法角色 400 | real isolated：member/guest 预览元数据切换，内容请求数组为零，Escape 后 URL 与触发器焦点恢复 | Done |
| PROJECT-PLATFORM-S21-M8-T09 | 中文术语与可行动状态统一，隐藏对象和技术信息最小披露 | secondary tab 文案、action summary、offline/read-only/error copy | frontend 43/43 合同测试、lint/build PASS | real isolated：body/详情不含 space/type UUID，离线前输入可提交、离线后禁用且输入保留，无英文网络错误 | Done |
| PROJECT-PLATFORM-S21-M8-T10 | 空白、模板、克隆影响可预览，场景选择不冒充安装或复制 | create modal impact preview、onboarding path、session-scoped clone reference | onboarding 合同测试 8/8；frontend build PASS | real isolated：基础空间、克隆来源与不复制边界、四类模板选项、研发场景影响预览全部通过 | Done |
| PROJECT-PLATFORM-S21-M8-T11 | 六类真实身份与 custom-role fixture、三视口、键盘焦点、离线、多标签和深链均通过 | `project-platform-s21-m8-role-surface.spec.ts` 与 IA custom-role fixture | 后端 31/31、前端 43/43、Playwright list 1/1 | real isolated route-final：1440/1366/820、第二标签页、offline、Escape/focus、六类身份和截图证据 1/1 | Done |
| PROJECT-PLATFORM-S21-M8-T12 | P0/P1 清零，P2 无未决验收项，回退与环境恢复可核对 | 路线图全部 Done、本执行报告、canonical redirect 可逆合同 | stage gate `quality-gate-20260731T075713.md` 与 `git diff --check` PASS | real isolated smoke 1/1；每轮动态空间/账号/数据库均在 finally 清理，工程 UX Go 不代签真人结论 | Done |

## Code Changes

- Backend：个人工作与活动新增空间范围过滤；类型摘要新增精确 `view/create` 动作；新增 member/guest surface preview 与共享 surface capability assembler。
- Frontend：重组项目空间主/次导航，新增成员工作首页、空间管理首页、成员视图预览、创建影响预览、详情行动摘要与 canonical panel 迁移合同；回归修复新增跨 surface 查询隔离、显式任务模板选择与差异化配置落点，并让未配置状态流程显示中文可行动提示而非空白面板。
- Tests：新增 PersonalWork controller 集成测试、成员内容/surface ownership 单测与 S21-M8 真实隔离 E2E；更新 M7 角色入口回归；追加“空间动态 → 工作项”跨 surface 稳定切换断言。
- Documentation：路线图将 M8-T01 至 T12 对账为 Done，并保留 M9 真人参与者、consent、隔离环境和主持人前置门禁。

## 2026-07-31 Navigation Regression Reopen

- 现象：进入项目空间显示概览后，点击工作项、项目管理、成员或设置，URL 短暂变化但内容立即回到概览。
- 根因：旧导航函数默认保留全部查询参数；概览的 `panel=member-home/activity/collaboration-boundary` 被带入目标 route 后，M8 canonical surface owner 规则按该 panel 的唯一归属 replace 回概览。
- 修复：新增 `projectSpaceCrossSurfaceLocation`，主导航和切换空间仅保留 `source` 与安全 hash；surface 内导航仍保留各自合法局部上下文，canonical owner 规则不放松。
- 防回归：E2E 先真实点击“空间动态”生成 `panel=activity`，再点击“工作项”，验证目标内容、`aria-current`、稳定 pathname、source 保留和旧 panel 清除。
- 复验补充：首次完整隔离运行在既有“状态流程”空白面板处失败；trace 证明接口 200 返回 `not_configured`，组件直接 `return null`。改为中文可行动 Alert 后，同一完整隔离用例 1/1 PASS，未降低断言。

## 2026-07-31 Settings Entry Regression Reopen

- 现象：“工作模型”的“任务模板”“字段、表单与页面”和“流程与权限”的 CTA 全部打开同一个 `/types` 任务模板目录，入口文案不同但业务落点不可区分。
- 根因：三个按钮硬编码同一 URL，且字段、布局和流程权限都依赖具体 `typeId`；现有测试只验证设置分组可见与自动化深链，没有从这些 CTA 实际点击。
- 修复：集中定义配置入口 URL；任务模板直达目录，字段、表单与页面、发布配置及流程与权限复用配置侧类型查询，多模板时要求管理员明确选择且不静默选择第一项。流程与权限进入 `panel=configuration-draft#flow-access`，继续复用唯一草稿/API/editor，并在异步加载完成后滚动和聚焦专属区域。
- 防回归：新增 2 项纯路由合同；M8 真实用例从设置实际点击四类入口，断言四个落点不同、`source/panel/hash` 正确、字段/布局组件可见，以及流程权限区域获得焦点并包含数据权限与状态流或节点流编辑器。
- 复验：共享开发栈动态 fixture 1/1 PASS（40.6s）；独立数据库/独立端口真实隔离 route-final 1/1 PASS（41.2s）。一次环境重试因外层短超时留下并发启动进程而未进入产品断言，确认端口和临时数据库清理后以 fresh 单实例结果收口。

## Validation

- Backend tests: `mvn -Dtest=ProjectSpaceControllerIntegrationTests,ProjectSpaceDtosTests,PersonalWorkControllerIntegrationTests,PersonalWorkServiceTests,PersonalCollaborationServiceTests,PublishedSnapshotAdapterTests,WorkItemTypeConfigurationControllerIntegrationTests test`，31/31 PASS。
- Frontend build: `pnpm --dir web lint` 与 `pnpm --dir web build` PASS；route/IA/surface 定向合同 26/26 PASS。
- Local quality gate: 回归 `work:finish` stage PASS，fresh 证据为 `.local-reports/quality-gate-20260731T083410.md`；checkpoint `.local-reports/quality-gate-20260731T082955.md` 与原 M8 stage gate 继续保留。
- Browser smoke: `work:finish` real isolated `project-platform-s21-m8-role-surface.spec.ts` 1/1 PASS（31.6s），证据为 `.local-reports/work-cycle-browser-20260731T083410.log`；共享真实页面额外验证概览、工作项、项目管理、成员、设置五入口均可稳定切换。
- Settings-entry regression: `projectSpaceConfigurationNavigation.test.ts` 2/2、frontend lint/build PASS；共享动态 fixture 1/1 PASS（40.6s）；checkpoint real isolated 1/1 PASS（41.2s），证据为 `.local-reports/work-cycle-browser-20260731T105726.log` 与 `.local-reports/quality-gate-20260731T105726.md`。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | M8 工程范围无未决 P0/P1/P2；M9 真人试用尚未执行，自动化结论不代签 | non-blocking | `PROJECT-PLATFORM-S21-M9-T01` 保持 Pending，按真人、consent、隔离环境和主持人门禁启动 |

## M9 UX Go

- Decision：Go，仅授权进入 M9-T01 的真人试用准备门禁。
- Basis：服务端 capability/readiness 合同、43 项前端合同测试、31 项后端测试、lint/build、真实隔离 route-final 与环境清理均通过。
- Boundary：不代表 5 位真人已就绪，不代表四类真人任务通过，也不代表 S21 产品 Go。

## Next Steps

- 保持 M9 全部 Task 为 Pending，先由 M9-T01 核对 5 位真人参与者、知情同意、隔离环境、真人主持和停止条件。
- M9-T02 重新冻结 revision 52 角色分层界面的试用协议；不得把 revision 49/51 准备包或本报告当真人原始反馈。
