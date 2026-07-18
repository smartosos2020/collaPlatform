---
title: PROJECT-PLATFORM-S01-M1 Execution Report
status: archived
milestone: PROJECT-PLATFORM-S01-M1
updated_at: 2026-07-18
---

# PROJECT-PLATFORM-S01-M1 Execution Report

## Scope

- PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M1-T09。
- 本里程碑只审计和冻结当前实现、存量数据与风险，不实现 S02 之后的目标模型。
- 审计基线 commit：`d78622533c880d206fe437561f3cc5b8b6531113`。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M1-T01 | static | not-required | not-required | No | 后端依赖与公开入口静态审计，不改变浏览器行为。 |
| PROJECT-PLATFORM-S01-M1-T02 | integration | not-required | not-required | No | API、DTO 和状态动作由代码审计及项目集成测试验证，不产生页面改动。 |
| PROJECT-PLATFORM-S01-M1-T03 | integration | not-required | not-required | No | Flyway 和本地 PostgreSQL 只读 schema 查询，不需要浏览器流程。 |
| PROJECT-PLATFORM-S01-M1-T04 | static | not-required | not-required | No | 前端路由、页面状态和 API 可达性静态审计，不修改现有页面。 |
| PROJECT-PLATFORM-S01-M1-T05 | integration | not-required | not-required | No | 权限路径由项目集成测试和权限实现审计验证，不新增浏览器合同。 |
| PROJECT-PLATFORM-S01-M1-T06 | integration | not-required | not-required | No | 跨模块链路由服务、事件消费者和现有集成测试库存验证。 |
| PROJECT-PLATFORM-S01-M1-T07 | integration | not-required | not-required | No | 执行 `ProjectControllerIntegrationTests` 并盘点后端与 E2E 测试库存。 |
| PROJECT-PLATFORM-S01-M1-T08 | integration | not-required | not-required | No | 对本地 shared-readonly `colla_platform` 执行只读画像和一致性 SQL；浏览器流程不适用。 |
| PROJECT-PLATFORM-S01-M1-T09 | static | not-required | not-required | No | 汇总当前能力矩阵和风险登记，不改变用户可见行为。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PROJECT-PLATFORM-S01-M1-T01 | Done | 完成 Controller、Service、Repository、resolver 及 6 类跨模块调用盘点。 |
| PROJECT-PLATFORM-S01-M1-T02 | Done | 登记 15 个项目 API、2 个跨模块写入口、DTO 动作来源及未使用写 API。 |
| PROJECT-PLATFORM-S01-M1-T03 | Done | 登记 9 张项目物理表、V009/V013/V015/V016/V022/V023/V027/V048/V053 演进、索引和约束缺口。 |
| PROJECT-PLATFORM-S01-M1-T04 | Done | 登记 3 个路由、项目列表/看板/表格/抽屉主路径和不可达能力。 |
| PROJECT-PLATFORM-S01-M1-T05 | Done | 冻结成员角色、企业权限码、资源 ACL 和平台权限解释之间的实际冲突。 |
| PROJECT-PLATFORM-S01-M1-T06 | Done | 完成平台对象、搜索、通知、审计、IM、文件、知识库和工作台链路盘点。 |
| PROJECT-PLATFORM-S01-M1-T07 | Done | 完成项目测试矩阵；完整类首次 4/5，失败方法独立复跑 1/1，收口全类复跑 5/5，登记顺序敏感风险。 |
| PROJECT-PLATFORM-S01-M1-T08 | Done | 完成本地数据只读画像；33 个项目、34 个成员关系、2 个事项及 31 条 IM 成员漂移。 |
| PROJECT-PLATFORM-S01-M1-T09 | Done | 输出当前能力矩阵、P1/P2 风险与后续 Stage 归属。 |

## Current Implementation Inventory

### Backend dependency map

```text
ProjectController
  -> ProjectService
     -> ProjectRepository / JdbcProjectRepository
     -> ImRepository / ImService
     -> PlatformObjectRepository / PlatformObjectResolverRegistry
     -> DomainEventRepository
     -> FileRepository
     -> AuditService

ImController -> ProjectService.createIssueFromMessage
KnowledgeContentCrossModuleService -> ProjectService
KnowledgeContentService -> ProjectRepository
WorkspaceDashboardService -> ProjectRepository
IssuePlatformObjectResolver / ProjectPlatformObjectResolver -> ProjectRepository
JdbcSearchRepository -> issues + project_members
AdminApplicationGovernanceService -> projects + project_members + issues (direct SQL)
```

项目模块的用户入口集中在 `ProjectController` 和 `ProjectService`，但工作台、知识内容、搜索与后台治理仍存在直接依赖项目 Repository 或私有表的路径。它们是当前事实，不符合目标架构的稳定 application facade / event 边界，后续迁移必须先提供替代合同再移除直读。

### API and action inventory

`ProjectController` 当前公开 15 个端点：项目列表、创建、详情、统计、增加成员；项目事项列表、创建；我的事项；事项详情、更新、状态动作、评论、附件、验证和关系。另有两个跨模块写入口：IM 消息转事项和知识内容选区转事项。

| Area | Current fact | Audit conclusion |
| --- | --- | --- |
| Project actions | DTO 固定返回 `open/create_issue/open_conversation` | 是硬编码投影，不是逐用户权限计算。 |
| Issue list actions | DTO 固定返回 `open/comment/transition` | 是硬编码投影。 |
| Issue detail actions | `ProjectService` 按 14 个 Java 内置动作和事项状态生成 | 有状态语义，但仅叠加项目成员编辑判断。 |
| Frontend workflow | 页面另有 4 个通用 fallback 动作 | 与后端重复，可能漂移。 |
| Exported writes | `updateIssue`、`addAttachment` 已导出 | `ProjectsPage` 未调用。 |
| Missing UI/API | 添加项目成员、iteration 管理、项目编辑/归档/恢复、移除成员 | 当前用户主路径不可达或不存在。 |
| Listing | 项目与事项列表无分页 | 数据增长后存在负载和交互风险。 |
| Issue number | `count(*) + 1` 生成项目内编号 | 并发创建可能冲突。 |

### Schema inventory

| Table | Main responsibility | Constraint/index conclusion |
| --- | --- | --- |
| `projects` | 项目、workspace、会话和状态 | 缺 workspace/user FK、状态 CHECK 和版本列。 |
| `project_members` | owner/member/viewer 成员关系 | 有 project FK 和唯一关系；user、created_by 无 FK。 |
| `iterations` | iteration 名称、时间和状态 | 只有 project FK；当前无业务写入口且存量为 0。 |
| `issues` | 事项、工作流状态、负责人、iteration | 有 project FK 和搜索/负责人/状态索引；用户、iteration 无 FK。 |
| `issue_comments` | 事项评论 | 有 issue FK；author 无 FK。 |
| `issue_attachments` | 文件附件 | 有 issue/file FK；created_by 无 FK。 |
| `issue_activity_logs` | 事项活动及序号 | 有 issue FK；actor 无 FK。 |
| `issue_verification_logs` | BUG 验证结果 | 有 issue/verifier FK；相同时间写入只以随机 UUID 兜底排序。 |
| `issue_relations` | polymorphic 事项关系 | 有 source issue FK；目标对象无通用 FK。 |

项目 schema 主要由 V009 建立，V013/V015/V016/V022/V023/V027 增强搜索、活动、验证、关系和工作流，V048/V053 增加项目平台对象注册及回填。9 张表均未建立状态/角色 CHECK，也没有可发布配置版本或 optimistic lock。

### Frontend route and interaction inventory

- `/projects`、`/projects/:projectId`、`/issues/:issueId` 均渲染 `ProjectsPage`。
- 页面同时展示项目侧栏、6 个统计卡、筛选、4 列看板、表格和事项抽屉，没有保存视图或用户可切换视图模型。
- 已连通创建项目、创建事项、筛选、拖拽状态、工作流弹窗、评论、验证和关系。
- 项目成员、iteration、项目生命周期和附件上传没有完整 UI 主路径。
- 直接打开 `/issues/:issueId` 时详情查询使用路由 ID，但评论、验证和关系 mutation 使用 `selectedIssueId`；未先从列表选中时可能向空 ID 写入。
- 看板拖拽使用前端 fallback 动作；已解决 BUG 拖到 closed 可映射为 `verify_passed`，但不会创建验证记录。

### Permission and governance inventory

| Layer | Current behavior | Conflict |
| --- | --- | --- |
| Project membership | active member 可查看；owner/member 可编辑；viewer 只读 | 这是项目运行时真正执行的权限来源。 |
| Enterprise permission codes | 已 seed `project.create/project.manage/issue.create/issue.update` | Java/TS 运行时没有引用；任意登录用户可创建项目。 |
| Resource ACL | 后台可对 `resourceType=project` 写 `resource_permissions` | `ProjectService` 和项目 resolver 不读取 ACL，授权不会改变项目运行时访问。 |
| Platform explanation | 若有 ACL 则按 ACL，否则 resolver 可访问态默认约为 view | 可能把 owner/member 解释为不可 edit，也可能把 ACL edit 解释为允许但项目服务拒绝。 |
| Admin | 管理员可管理 ACL | 管理员并不自动获得项目内容权限；创建者因 owner 身份获得权限。 |

当前存在成员关系、资源 ACL、平台权限解释三套不一致来源，是 S01/S02 必须显式冻结并在后续 Stage 收敛的 P1 风险。

### Cross-module lifecycle inventory

| Capability | Current chain | Lifecycle conclusion |
| --- | --- | --- |
| Platform object | project/issue resolver + object link | 创建/更新会注册；项目无归档/删除 API，生命周期闭环不完整。 |
| Search | issue 索引与 project_members 可见性过滤 | 可搜事项，不索引项目；项目仅可作为对象选择项。 |
| Notification | 指派、状态动作、验证、评论 mention -> domain event -> worker | 评论无 mention、附件和关系没有完整通知矩阵。 |
| Audit | 项目创建/加成员、事项创建/更新/动作/验证/关系 | 评论和附件没有项目审计记录。 |
| IM | 创建项目建群；加项目成员同步群成员 | 通用 IM 可独立增删项目群成员，没有反向同步或项目会话守卫。 |
| File | 附件校验文件并登记 usage | 无项目附件删除与专用下载入口。 |
| Knowledge | 选区创建事项并写双向关系 | 知识服务直依赖项目 Repository；活动 payload 仍有 `document:` 历史文本。 |
| Workspace | 聚合我的事项 | 直接依赖项目 Repository，尚无稳定查询 facade。 |

## Test Matrix

| Area | Existing evidence | Gap |
| --- | --- | --- |
| Project integration | `ProjectControllerIntegrationTests` 5 个测试覆盖主流程、非成员拒绝、平台摘要、BUG 双次验证和需求/BUG 分支 | 项目列表/统计/成员、更新、附件、viewer、ACL 冲突、并发编号与生命周期缺少独立断言。 |
| Cross-module integration | IM 转事项、搜索、平台对象、工作台、知识选区有跨模块测试 | 未形成统一项目端到端剧本。 |
| Frontend unit/component | 无项目页面专用测试 | 深链 mutation、fallback workflow 和拖拽验证语义未自动保护。 |
| Browser E2E | 广域 spec 覆盖创建/打开/搜索/IM 转事项片段 | 没有项目专属、隔离且可重复的主流程 spec。 |
| Reliability observation | 完整测试类首次 5 个中 1 个失败；失败方法独立复跑 1/1、收口全类复跑 5/5 通过 | `verification_logs` 最新顺序存在偶发/顺序敏感风险，后续需用稳定性测试保护。 |

## Persisted Data Profile

2026-07-18 对本地 Docker PostgreSQL `colla_platform` 执行只读查询：

| Measure | Value |
| --- | ---: |
| projects / active projects | 33 / 33 |
| project_members / active memberships | 34 / 34 |
| owner / member / viewer memberships | 33 / 1 / 0 |
| iterations | 0 |
| issues / active issues | 2 / 2 |
| bug / task issues | 1 / 1 |
| open issues | 2 |
| comments / attachments / activity logs | 0 / 0 / 5 |
| verification logs / relations | 0 / 2 |
| issue relation targets | message 1, knowledge_content 1 |
| missing assignee / iteration / resolution | 1 / 2 / 2 |
| orphan project/member/issue/subordinate/object-link rows | 0 |
| project missing conversation / wrong conversation type | 0 / 0 |
| conversation members absent from project membership | 0 |
| project members absent from project conversation | 31 |
| project domain events / project audit rows / project ACL rows | 37 / 37 / 0 |

可重复查询按以下口径执行，全部为 `SELECT`：

```sql
select count(*) from projects;
select project_role, count(*) from project_members group by project_role;
select issue_type, status, count(*) from issues group by issue_type, status;
select count(*) from iterations;
select target_type, count(*) from issue_relations group by target_type;
select count(*) from project_members pm
where not exists (
  select 1 from projects p
  join conversation_members cm on cm.conversation_id = p.conversation_id
  where p.id = pm.project_id and cm.user_id = pm.user_id and cm.status = 'active'
);
```

当前数据没有检测到孤儿关系，但 31/34 项目成员未在对应项目群中，是需要在后续迁移前先澄清历史创建策略和同步规则的真实漂移。

## Current Capability Matrix And Risk Register

| Capability | Current level | Main gap | Priority | Planned ownership |
| --- | --- | --- | --- | --- |
| Project/member aggregate | 固定项目 + 固定角色 | 多权限来源冲突、成员/群漂移 | P1 | S01-M2/M3, S02 |
| Issue model | 4 个固定类型和 Java 内置流程 | 无可发布类型/字段/流程配置版本 | P1 | S02-S04 |
| Workflow execution | 支持动作、理由、活动和 BUG 验证 | 前后端动作重复，验证与拖拽语义不一致 | P1 | S03-S04 |
| Data integrity | 核心父子 FK 和常用索引 | 用户/iteration FK、CHECK、并发编号与版本控制不足 | P1 | S01-M3, S02 |
| User UI | 列表、看板、详情抽屉和筛选 | 无视图模型、深链写入风险、若干能力不可达 | P1 | S05-S06 |
| Platform integration | issue/project 对象、事件、IM、KB、文件 | 搜索/通知/审计/生命周期和 facade 边界不完整 | P2 | S07-S08 |
| Testability | 有核心集成测试和广域 E2E 片段 | 缺项目组件测试、隔离 E2E、并发和权限矩阵 | P1 | 每 Stage 收口 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M1-T01 | 形成完整调用与依赖图，每个公开入口可定位到实现和测试 | 本报告 Backend dependency map 登记 `ProjectController`、`ProjectService`、`JdbcProjectRepository`、resolver 与 6 类外部调用方。 | `rg` 与完整类读取核对 Controller、Service、Repository、IM、Knowledge、Workspace、Search、Admin 调用路径。 | 不需要浏览器：任务只冻结后端依赖事实且未改变运行时行为。 | Done |
| PROJECT-PLATFORM-S01-M1-T02 | 登记运行时动作来源、硬编码投影、未使用写 API 和兼容合同 | API and action inventory 登记 15 个端点、2 个跨模块入口、硬编码动作、后端状态动作和前端未使用写 API。 | `ProjectControllerIntegrationTests` 验证事项动作与状态分支；静态核对 `UserProjectDtos`、`projectsApi.ts` 和 `ProjectsPage.tsx`。 | 不需要浏览器：本轮不修改 API 或页面合同。 | Done |
| PROJECT-PLATFORM-S01-M1-T03 | 每张表、字段、索引、外键和真实读写方有登记 | Schema inventory 登记 9 张表及 V009/V013/V015/V016/V022/V023/V027/V048/V053 演进和约束结论。 | 本地 PostgreSQL `information_schema`、`pg_indexes`、`pg_constraint` 只读查询与 Flyway 文件核对完成。 | 不需要浏览器：schema 审计不包含页面行为。 | Done |
| PROJECT-PLATFORM-S01-M1-T04 | 用户主路径、不可达能力、重复状态和缺失交互有证据 | Frontend inventory 登记 3 个路由、页面区域、已连通能力、不可达能力、深链写入和重复 workflow 风险。 | 静态核对 `router.tsx`、`ProjectsPage.tsx`、`projectsApi.ts`、API boundary 和项目样式；盘点现有 E2E 引用。 | 不需要浏览器：当前页面未改动，任务目标是现状静态审计。 | Done |
| PROJECT-PLATFORM-S01-M1-T05 | 登记当前 owner/member/viewer、管理员和 ACL 的实际语义与冲突 | Permission inventory 冻结成员权限、未消费企业权限码、未生效项目 ACL 和平台解释冲突。 | 项目非成员集成测试通过；静态核对 `ProjectService`、`PermissionDecisionService`、`ResourcePermissionManagementService` 和治理矩阵。 | 不需要浏览器：权限现状由后端执行路径和集成测试验证。 | Done |
| PROJECT-PLATFORM-S01-M1-T06 | 每条跨模块链路登记对象类型、ID、访问边界和生命周期结论 | Cross-module inventory 覆盖平台对象、搜索、通知、审计、IM、文件、知识内容和工作台。 | 静态核对 resolver、`DomainEventWorker`、`SearchIndexService`、IM/File/Knowledge/Workspace 服务及跨模块测试库存。 | 不需要浏览器：本轮不改变跨模块用户流程。 | Done |
| PROJECT-PLATFORM-S01-M1-T07 | 测试矩阵区分存在、有效、陈旧、缺失和无法重复运行 | Test Matrix 登记项目 5 个集成测试、跨模块测试、前端/E2E 缺口和顺序敏感观察。 | `mvn -Dtest=ProjectControllerIntegrationTests test` 首次 4/5；失败方法 `#recordsFailedVerificationBeforeTheBugIsFixedAndVerified` 独立复跑 1/1；收口全类复跑 5/5 通过。 | 不需要浏览器：本轮盘点测试覆盖且无页面变更。 | Done |
| PROJECT-PLATFORM-S01-M1-T08 | 输出类型、状态、空值、孤立关系、成员角色和 iteration 使用统计方案 | Persisted Data Profile 给出计数、空值、孤儿、会话同步、事件/审计/ACL 口径及可重复 SQL。 | 对本地 `colla_platform` 执行只读 SQL；9 类孤儿为 0，检出 31 条项目成员/群成员漂移。 | 不需要浏览器：共享环境仅执行数据库只读画像。 | Done |
| PROJECT-PLATFORM-S01-M1-T09 | 结论与代码/schema 一致，不把目标能力写成已实现 | Capability Matrix 区分当前固定模型、真实缺口、P1/P2 风险及后续 Stage 归属。 | `pnpm work:plan-check` 通过；架构、路线、代码、Flyway 和数据画像交叉核对。 | 不需要浏览器：风险登记和事实冻结没有用户界面变化。 | Done |

## Code Changes

- 未修改业务代码、API、schema 或前端行为。
- 新增本执行报告，并同步当前路线和现行架构事实。
- 修正当前架构与技术选型中的 Flyway 基线为 V055。
- 补登记已存在的 `project` 平台对象类型及其当前能力边界。
- 修复 TypeScript 工作台只接受旧 `quality-gate-*.log`、无法使用新 `quality-gate-*.md` 总结收口纯文档里程碑的问题，并增加回归测试。

## Validation

- Backend tests: `mvn -Dtest=ProjectControllerIntegrationTests test` 收口复跑 5/5 通过，Surefire 用时 39.70 秒。
- Frontend build: `pnpm web:build` 通过；checkpoint 的 `pnpm web:lint` 也通过，未改变页面、路由、组件或样式。
- Local quality gate: `.local-reports/quality-gate-20260718T102653.md` 为 PASS；工作台回归测试 44/44、`tsc --noEmit` 通过。
- Browser smoke: 不需要；本里程碑仅审计和记录当前实现，不改变页面、路由或交互行为。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 项目运行时成员权限、资源 ACL 和平台权限解释存在 P1 分裂 | non-blocking | 已登记到本报告风险矩阵，S01-M2/M3 冻结合同和迁移方案。 |
| N/A | 本地数据存在 31 条项目成员未同步到对应项目群的漂移 | non-blocking | S01-M3 迁移画像和修复/豁免策略输入。 |
| N/A | 项目内事项编号使用 `count + 1`，并发创建存在冲突风险 | non-blocking | S01-M3 技术 spike 与 S02 数据模型输入。 |
| N/A | 事项深链写 mutation、前端 fallback workflow 和 BUG 拖拽验证语义存在 P1 风险 | non-blocking | S01-M2 冻结动作合同，S03/S05 实现收敛。 |
| N/A | 项目测试类观察到验证列表顺序敏感，且缺项目专属组件/E2E/并发测试 | non-blocking | 收口全类复跑；后续每个实现 Stage 补齐对应测试层。 |

## Next Steps

- 进入 PROJECT-PLATFORM-S01-M2，冻结产品术语、聚合、配置版本、动作、权限和平台事件合同。
- M2 不得把当前硬编码项目模型直接提升为目标模型，也不得绕过本报告登记的 P1 冲突。
