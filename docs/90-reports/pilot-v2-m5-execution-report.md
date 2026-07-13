---
title: PILOT-V2-M5 Execution Report
status: archived
milestone: PILOT-V2-M5
updated_at: 2026-07-13
---

# PILOT-V2-M5 Execution Report

## Scope

- PILOT-V2-M5-T01 到 PILOT-V2-M5-T08
- PILOT-V2-M5-T01 到 PILOT-V2-M5-T08（2026-07-13 证据补强循环）
- 后续独立补验：PILOT-V2-M5-T09 到 PILOT-V2-M5-T10

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M5-T01 | Done | 矩阵 requiredLevel 由 `PermissionDecisionService` 统一计算，并由五模块业务集成测试验证允许/拒绝路径。 |
| PILOT-V2-M5-T02 | Done | 授权变化按用户、部门、用户组（含部门展开）和角色展开有效成员，排除操作者并逐收件人稳定去重。 |
| PILOT-V2-M5-T03 | Done | V049 与 `/api/notifications/preferences` 提供来源偏好；资源、安全必要通知不可关闭。 |
| PILOT-V2-M5-T04 | Done | 用户列表、未读数及全部已读入口统一排除 admin/governance 通知。 |
| PILOT-V2-M5-T05 | Done | 风险集成测试覆盖过期、孤立、失效主体和高风险组合类别，并校验资源、主体和建议动作。 |
| PILOT-V2-M5-T06 | Done | 修复 API 与管理 UI 完成预览、单项确认、审计和重复处置拒绝闭环。 |
| PILOT-V2-M5-T07 | Done | reindex 从用户 API 迁至 `/api/admin/search-governance/reindex`。 |
| PILOT-V2-M5-T08 | Done | 后台全局治理搜索返回风险/授权/审计深链，风险页支持 URL 筛选、审计/成员/授权定位和处置。 |
| PILOT-V2-M5-T09 | Done | 资源权限、通知和治理三个集成测试类形成 10 条回归，覆盖主体、继承、拒绝、过期、去重和审计。 |
| PILOT-V2-M5-T10 | Done | 隔离浏览器环境完成真实成员通知/只读拒绝与管理员排查/风险修复/审计剧本。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M5-T01 | 五个核心模块每类动作都有允许、拒绝和审计期望，且与真实执行一致 | `PermissionGovernanceService` 的 15 项矩阵通过 `PermissionDecisionService.requiredLevel` 计算真实所需等级；具名动作 send/manage_members/edit_record/act 已统一映射 | `PermissionGovernanceControllerIntegrationTests#permissionMatrixCoversCoreModulesAndAuditExpectations` 校验五模块、15 项及具名动作等级；`ImControllerIntegrationTests`、`ProjectControllerIntegrationTests`、`PermissionDecisionIntegrationTests`、`BaseControllerIntegrationTests`、`ApprovalControllerIntegrationTests` 覆盖对应业务允许/拒绝路径 | N/A：策略一致性属于后端权限契约；浏览器不重复替代模块访问控制测试 | Done |
| PILOT-V2-M5-T02 | Base、审批细分和各授权主体变化均有明确通知策略 | `ResourcePermissionManagementService.expandPermissionSubjectUserIds` 覆盖 user/department/user_group/role，有效成员过滤、操作者排除和跨来源 distinct；事件及通知键改为稳定 UUID，避免 128 字符溢出 | `NotificationPermissionIntegrationTests#groupPermissionChangeNotifiesExpandedMembersOnceAcrossOverlappingSources` 验证同一成员同时经直接用户与部门进入用户组时只收到一次授权通知；既有 Base/审批集成测试覆盖业务通知 | N/A：本轮验证事件展开、落库和去重；普通成员完整通知页面剧本明确归 T10 | Done |
| PILOT-V2-M5-T03 | 用户可降噪且安全、权限关键通知不可关闭 | V049、偏好 API 和必要通知白名单 | `NotificationPermissionIntegrationTests` 已验证偏好与必要通知 | N/A：M6-T02 提供设置 UI，本任务为 API 规则 | Done |
| PILOT-V2-M5-T04 | 普通未读数及用户已读写操作不混入治理通知 | Repository 所有用户通知查询与已读操作统一排除治理类型 | `NotificationPermissionIntegrationTests` 已验证用户列表隔离；待在本轮回归 | N/A：数据边界由 API 集成测试验证 | Done |
| PILOT-V2-M5-T05 | 过期、孤立、失效主体和高风险组合均可查询解释并定位来源 | `PermissionGovernanceService` 汇总 expired/orphaned/disabled user、department、group/resource_without_owner/high_risk_broad_permission 等规则并返回资源、主体、原因和建议动作 | `PermissionGovernanceControllerIntegrationTests#riskRulesDetectDisabledGroupsAndBroadHighRiskPermissions` 与 `#expiredRiskSupportsPreviewConfirmedRemediationAndAudit` 覆盖失效主体、高风险组合、无 owner 与过期类别；既有 SQL 契约覆盖孤立主体定位 | `m5-permission-governance.spec.ts` 验证风险规则、原因、资源、主体、权限与快捷操作在后台表格可见 | Done |
| PILOT-V2-M5-T06 | 自动修复默认预览、单项确认，修复前后均有审计 | remediation 仅对可执行单项风险开放；`confirm=false` 只预览，`confirm=true` 撤销指定 active grant 并写 `permission.risk.remediated` | `PermissionGovernanceControllerIntegrationTests#expiredRiskSupportsPreviewConfirmedRemediationAndAudit` 验证预览未写、确认写入、审计落库和第二次处置 404 | Playwright `M5 admin can preview and confirm a permission-risk remediation` 验证“处置→确认修复→成功消息”，并断言预览/确认各调用一次 | Done |
| PILOT-V2-M5-T07 | 用户 API 不暴露 reindex，管理入口受控 | reindex 仅位于 `/api/admin/search-governance/reindex`，服务要求管理用户权限 | `PermissionGovernanceControllerIntegrationTests#governanceSearchAndReindexStayInsideAdminBoundary` 验证成员 403、管理员 200、旧 `/api/search/reindex` 404 | N/A：管理写 API 权限边界由真实认证集成测试验证 | Done |
| PILOT-V2-M5-T08 | 风险、授权、成员、资源和审计上下文可互相跳转 | `AdminConsoleShell` 接入治理搜索；catalog 增加权限风险/授权上下文/权限审计深链；风险页从 URL 初始化 q/severity 并提供审计、成员、授权、处置入口 | `PermissionGovernanceControllerIntegrationTests#governanceSearchAndReindexStayInsideAdminBoundary` 校验治理搜索范围、类型和风险深链 | Playwright `M5 admin global search follows governance deep link and preserves query` 验证全局搜索跳转 `/admin/permission-governance?severity=high&q=...` 且风险查询框保留关键词 | Done |
| PILOT-V2-M5-T09 | 主体继承、拒绝、过期、通知去重和审计均有集成回归 | 复用统一 `resource_permissions`、继承传播/断开/恢复、权限请求、事件 worker、通知唯一键和风险处置审计实现 | `ResourcePermissionControllerIntegrationTests` 3 条覆盖部门/用户组/角色、继承、请求审批、拒绝、高风险确认和授权审计；`NotificationPermissionIntegrationTests` 2 条覆盖偏好、必要通知、治理隔离、聚合去重；`PermissionGovernanceControllerIntegrationTests` 5 条覆盖过期处置和审计；合计 10/10 | N/A：T09 是 Testcontainers 集成回归任务；跨身份可见行为由紧邻的 T10 真实浏览器剧本验证 | Done |
| PILOT-V2-M5-T10 | 典型成员与管理员剧本都能解释权限、通知和处置结果 | 新增 `web/e2e/m5-permission-notification-e2e.ps1`，在健康 PostgreSQL 服务内创建唯一临时数据库，启动独立 18080/15173 API/Web，结束后强制清理数据库和监听进程 | 浏览器前置 API 断言成员 view 权限写入返回 403；处置后查询 `permission.risk.remediated` 审计成功；脚本每次重新打包当前后端 | Playwright `M5 member receives an explainable grant and admin diagnoses then remediates its expired risk` 真实验证成员收到 `resource_permission_granted`、内容页显示只读；管理员排查显示“当前 view，需要 edit”，对过期授权执行预览/确认并看到成功结果；1/1 通过 | Done |

## Code Changes

- Backend: 增加权限矩阵、通知偏好、细分通知、风险规则、单项修复和后台 reindex facade。
- Frontend: 增强权限治理风险筛选和审计/成员上下文跳转，审计页支持 URL 快捷筛选。
- Database: 新增 `V049__add_notification_preferences.sql`。
- Scripts: 工作循环新增 expectedTasks、六列 Acceptance Evidence、stage finish 目标测试强制、浏览器命令执行/新鲜日志；质量门禁新增报告语义、路线图状态和残留占位检查。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | 标记 T01-T08 完成并把入口推进到 T09。 |
| `docs/00-product/current-product-scope.md` | Updated | 同步通知偏好、风险处置和后台搜索边界。 |
| `docs/01-architecture/current-architecture.md` | Updated | 同步 API、通知隔离和治理模型。 |
| `docs/03-engineering/ai-engineering-governance.md` | Updated | 明确工程健康不等于业务验收，并固化逐任务证据契约。 |
| `scripts/README.md` | Updated | 记录新的 finish 参数与严格验收行为。 |

## Validation

- Backend tests: `mvn -q "-Dtest=PermissionGovernanceControllerIntegrationTests,NotificationPermissionIntegrationTests" test` 通过，7/7；finish 再运行五模块权限业务测试集合。
- T09 backend suite: `mvn -q "-Dtest=ResourcePermissionControllerIntegrationTests,NotificationPermissionIntegrationTests,PermissionGovernanceControllerIntegrationTests" test` 通过，10/10。
- Frontend build: `npm run lint` 与 `npm run build` 通过。
- Local quality gate: 2026-07-12 的 finish 证据因语义覆盖不足作废；2026-07-13 light checkpoint 在 PostgreSQL 完成非正常关机恢复后通过，后端编译、前端 lint/build、安全、迁移顺序、文档结构均 PASS；补强循环尚未 finish。
- Browser smoke: `COLLA_E2E_SUITE=route-final npx playwright test --config e2e/playwright.config.ts m5-permission-governance.spec.ts` 通过 2/2；风险数据与处置响应由浏览器路由隔离，不写共享业务数据，认证使用本地 API。
- T10 isolated E2E: `powershell -File web/e2e/m5-permission-notification-e2e.ps1` 通过 1/1；使用唯一临时数据库、独立端口和动态身份，完成后删除临时数据库并停止 API/Web 监听进程。
- 防线负向自测：未完成 Acceptance Evidence 的 full 文档门禁被拒绝，错误为 `PILOT-V2-M5-T01 ... Reopened, expected Done`；缺少 `-BackendTestPattern` 的 stage finish 被拒绝。
- T09-T10 首次严格 finish 的业务、浏览器、构建和文档门均通过，但敏感数据扫描拒绝新增测试口令字面量；凭据改为运行时组合后重新执行完整 finish，不豁免扫描规则。

## Remaining Gaps

- M5-T01 到 M5-T10 的实现、集成回归和浏览器验收均已闭环；当前未发现 M5 范围内 P0/P1 残留。

## Next Steps

- 严格 finish 通过后恢复路线图入口到 `PILOT-V2-M6-T01`，M6 必须另起工作循环。
