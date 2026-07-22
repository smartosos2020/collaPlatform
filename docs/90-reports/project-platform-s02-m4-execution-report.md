---
title: PROJECT-PLATFORM-S02-M4 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S02-M4
stage: PROJECT-PLATFORM-S02
updated_at: 2026-07-22
---

# PROJECT-PLATFORM-S02-M4 Execution Report

## Scope

- PROJECT-PLATFORM-S02-M4-T01 到 PROJECT-PLATFORM-S02-M4-T10。T01-T03 证据保持首轮收口结论；T04、T05、T09、T10 于 2026-07-22 完成第三轮复审补验。
- 本里程碑交付 legacy project 到 space/member 的数据画像、确定性映射、批次状态机、校验与回退、治理 API、深链兼容和兼容边界冻结。
- legacy project/issue 业务写路径不切换、不双写；完整 WorkItem 迁移由 S07 承接，不在本里程碑范围内。
- 第一轮审计补验（已收口）：verify 空验证、输入指纹不含用户状态、回滚覆盖失败清单、V058 单列外键、失败注入证据不足。
- 第二轮复审补验（已收口）：plan 与指纹跨查询快照不一致；batch verify 与 workspace 收敛混淆；缺真实失败恢复演练；技术选型文档停留 V059。
- 第三轮复审补验（本轮）：resume 会把上一尝试中本批次创建的项目改写为 `REUSED`，导致批次历史归属丢失；快照并发测试未调用真实迁移服务；rehearsal v3 创建并永久保留 legacy 项目/成员夹具。修复后 `summary.manifestProjects` 跨尝试保留 `ownedByBatch`，真实服务测试以 `REQUIRES_NEW` 在 plan 与 fingerprint 间提交写入，无污染 rehearsal v4 复用既有项目并对 legacy 四表做执行前后 SHA-256 校验。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M4-T01 | integration | not-required | not-required | No | 纯后端只读画像 API，无页面、路由或通知触达变化，浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T02 | integration | not-required | not-required | No | 纯后端映射规则计算，集成测试在真实 PostgreSQL 上断言确定性输出，浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T03 | integration | not-required | not-required | No | 纯后端角色映射规则，集成测试断言失败清单与无静默放大，浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T04 | integration | not-required | not-required | No | 批次状态机由真实服务与数据库集成测试驱动（重跑、中断续跑、并发、REPEATABLE_READ 快照、单元过期拒绝），浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T05 | integration | not-required | not-required | No | 批次 manifest 校验与回退由真实服务集成测试驱动，含篡改注入、历史批次取代、回退失败累加断言，浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T06 | integration | not-required | not-required | No | 治理 API 与收敛端点正反矩阵由 MockMvc 真实 HTTP 集成测试驱动，浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T07 | e2e-real-isolated | real | isolated | No | memberA 真实登录打开已迁移旧项目页看到提示条并跳转新空间；memberB 与非迁移项目无提示且不泄露名称 |
| PROJECT-PLATFORM-S02-M4-T08 | integration | not-required | not-required | No | 真实 legacy 写 API 前后对新模型五表做行数与哈希断言，浏览器流程不适用 |
| PROJECT-PLATFORM-S02-M4-T09 | e2e-real-isolated | real | isolated | No | 真实浏览器覆盖旧深链正/负例；后端集成覆盖重跑、事务中途失败、并发批次、stuck running 续跑、跨 workspace 外键负例与回滚失败累加 |
| PROJECT-PLATFORM-S02-M4-T10 | integration | not-required | not-required | No | 运行手册与目标质量门收口，复用本轮全部目标测试与浏览器证据，浏览器流程不适用 |

## Completed Items

- T01：legacy 数据画像/预检（数量、孤立成员、非法角色、重复关系、无 owner、IM 漂移），`GET /api/admin/project-migrations/profile`。
- T02/T03：确定性映射规则与冲突决策（确定性 space id、key 规范化与后缀、REUSE 既有映射；owner/member/viewer 到 owner/member/guest，未知角色与孤立成员进失败清单）。
- T04/T05（三轮补验修正）：迁移批次状态机（dry-run/execute/resume/verify/rollback；计划与指纹在同一 REPEATABLE_READ 事务内读取；单元写前拒绝过期快照）；`summary.projects` 记录最近尝试，`summary.manifestProjects` 跨 resume 保留本批次创建产物的 `CREATED`、`spaceId` 和 `ownedByBatch=true`；批次 verify 依据生命周期归属报告 `MAP_MISSING`/`MAP_SUPERSEDED`，workspace 收敛验证独立且不写批次，rollback 追加保留未决失败清单。
- T06：治理迁移 API（批次查询、dry-run、execute/resume/verify/rollback、`workspaces:verify-convergence`，`EXECUTE`/`ROLLBACK` 确认串，统一 `project.manage` 最小权限与批次级审计）。
- T07：`GET /api/project-spaces/legacy-resolve/{legacyProjectId}` 深链兼容解析与 legacy 项目页迁移提示条。
- T08：兼容边界冻结的可执行证据（legacy 写不触碰新模型五表的哈希断言）；V060 把 map->batch 引用升级为 workspace 复合外键并有跨 workspace 负例。
- T09（三轮补验修正）：迁移集成、真实故障注入、stuck running 续跑、真实服务 REPEATABLE_READ 写入竞态、跨 workspace map-batch 拒绝、部分失败后 resume 的完整归属、A 回退 B 重迁全部历史归属项失败、回退与真实浏览器深链验证。
- T10：迁移运行手册（`docs/05-runbooks/project-space-migration.md`）与目标质量门。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M4-T01 | 数量、孤立成员、非法角色、重复关系、无 owner 和 IM 漂移均可报告 | `ProjectLegacyProfileService` 汇总六类口径；`AdminProjectMigrationController` 暴露只读画像端点并写审计 | `AdminProjectMigrationControllerIntegrationTests` 3 用例通过（全口径、权限负例、空 workspace 归零） | not-required：纯后端只读画像，无界面变化 | Done |
| PROJECT-PLATFORM-S02-M4-T02 | reruns yield identical mapping; key conflicts, pre-existing spaces and cross-workspace rows carry explicit decisions | `ProjectLegacyMappingService` 确定性 space id、key 规范化与冲突后缀、REUSE 决策；V058 批次归属列 | `ProjectLegacyMappingServiceIntegrationTests` 9 用例通过，含两次 plan 完全一致与 archived 空间占 key 冲突 | not-required：纯后端映射规则，无界面变化 | Done |
| PROJECT-PLATFORM-S02-M4-T03 | legacy owner/member/viewer map to owner/member/guest with explainable origin; unknown roles land in failure list without silent elevation | `MemberMapping.explanation` 记录角色来源；`MemberFailure` 覆盖 UNKNOWN_ROLE 与 ORPHAN_USER，绝不写入空间 | 同一测试类覆盖双 owner 全保留、viewer→guest、未知角色与四类孤立用户进失败清单 | not-required：纯后端角色规则，无界面变化 | Done |
| PROJECT-PLATFORM-S02-M4-T04 | batches record input watermark, results, failure items and checksums; interrupted batches resume safely and reruns stay idempotent | `ProjectSpaceMigrationService` 状态机、source/result SHA-256、单元事务、workspace 行锁；计划与指纹在同一 REPEATABLE_READ 快照读取，指纹含用户状态；最近尝试与生命周期归属分别写入 `summary.projects` / `summary.manifestProjects` | `ProjectSpaceMigrationServiceIntegrationTests` 22 用例通过：幂等、failed/stuck resume、并发、真实服务 `REQUIRES_NEW` 写入竞态、单元故障恢复；resume 后断言全部生命周期项仍为 `CREATED` 且 `ownedByBatch=true` | not-required：批次状态机为后端能力 | Done |
| PROJECT-PLATFORM-S02-M4-T05 | space/member counts, roles, attribution and maps are verified item by item; rollback reverts only new-model artifacts | 批次 verify 锚定 `summary.manifestProjects` 生命周期归属：本批次拥有项要求本批次活跃映射，外部 REUSED 只要求原映射仍有效；已回退拥有项要求无活跃映射；workspace 收敛验证独立；rollback 按 batch_id 只删本批次产物 | 同测试类覆盖 verify、篡改、dry-run 409、convergence、回退失败；新增“部分失败→resume→回退 A→执行 B”断言，verify(A) 对 A 的全部归属项逐项给出 `MAP_SUPERSEDED`，不再只命中最后一次 CREATED 项 | not-required：校验与回退为后端能力 | Done |
| PROJECT-PLATFORM-S02-M4-T06 | only enterprise governance holders run dry-run/apply/rollback; high-risk operations demand explicit confirmation and leave traceable records | `AdminProjectMigrationController` 八个端点（含 `workspaces:verify-convergence`）；确认串缺失 400；服务层统一 `requireManageProjects` 与批次级审计事件 | `AdminProjectMigrationApiIntegrationTests` 4 用例通过：全链路（resume 后批次 verify 与 convergence 各 2 matches 且审计独立）、400/403/404 矩阵（含 convergence 403）、六条审计行断言 | not-required：治理 API 无界面变化 | Done |
| PROJECT-PLATFORM-S02-M4-T07 | legacy project links resolve to the migrated space entry; unmigrated, failed and denied states never misroute or disclose names | `ProjectSpaceService.resolveLegacySpace` 四态语义（mapped 只返 spaceId）；legacy 项目页 `ProjectsPage` 在 mapped 时渲染提示条与跳转按钮 | `ProjectSpaceLegacyResolveIntegrationTests` 7 用例通过（mapped/unavailable/unmigrated/failed/未知 id/无 token） | real isolated Chromium：`project-platform-s02-m4-legacy-deep-link.spec.ts` 场景 1-3 通过，memberB 页面无提示条且无空间名 | Done |
| PROJECT-PLATFORM-S02-M4-T08 | legacy project/issue business writes stay on legacy tables with no dual-write and no cutover; S07 owns full WorkItem migration | 迁移代码不调用任何 legacy 写方法；`project_spaces` 等五表只在迁移服务内写入；V060 复合外键强化 map->batch 的 workspace 归属 | `ProjectLegacyWriteBoundaryIntegrationTests` 2 用例通过：迁移前后 legacy 写落 legacy 表且新模型五表行数与内容哈希不变；跨 workspace 更新 map 的 batch_id 被数据库拒绝（DataIntegrityViolationException） | not-required：写边界为后端数据断言 | Done |
| PROJECT-PLATFORM-S02-M4-T09 | rerun, partial failure, concurrent batches, rollback, legacy deep links and negative cases are all covered by automation | 真实故障注入、stuck running、生产服务事务竞态、跨 workspace 外键、历史批次完整取代、回退、深链与权限负例分布在六个测试类与一个浏览器 spec | 后端 55 用例全绿（4+4+3+4+9+22+7+2）；rehearsal v4（`.local-logs/m5-t04-rehearsal-v4.log`）在 59 项目样本完成真实 UNIT_FAILED、同批 resume、59/59 归属保留、回退 A、执行 B、verify(A) 59 个 `MAP_SUPERSEDED` 和确定性校验 | real isolated Chromium：既有同一 spec 1 passed；本轮无 UI 行为变化 | Done |
| PROJECT-PLATFORM-S02-M4-T10 | migration runbook with dry-run sample, verification SQL/report, rollback steps and residual data risks is reviewable | `docs/05-runbooks/project-space-migration.md` 覆盖画像、dry-run 样例、execute/resume/verify/rollback 步骤、收敛验证、校验 SQL、剩余数据风险；本轮更新 REPEATABLE_READ 快照、manifest 校验语义、convergence 端点与回退失败保留 | 本里程碑 55 个目标后端用例与真实浏览器 spec 在 stage finish 复核 | not-required：文档与质量门收口，无界面变化 | Done |

## Code Changes

- 后端（第三轮补验）：`ProjectSpaceMigrationService` 拆分最近尝试结果与跨 resume 生命周期归属清单，verify/rollback 读取 `manifestProjects.ownedByBatch`；`ProjectSpaceMigrationServiceIntegrationTests` 改为通过真实迁移服务验证 REPEATABLE_READ 竞态，并覆盖部分失败续跑后的完整历史归属。
- 后端（第二轮补验）：`ProjectSpaceMigrationService`（verify 锚定批次 manifest + `MAP_SUPERSEDED`；新增 `verifyWorkspaceConvergence` 不写批次、独立审计；execute/resume/dryRun 在同一 REPEATABLE_READ 快照读取 plan 与指纹）；`ProjectSpaceMigrationModels`（新增 `MAP_SUPERSEDED`）；`AdminProjectMigrationController`（新增 `workspaces:verify-convergence`）。
- 后端（第一轮补验）：`ProjectSpaceMigrationService`（锁内快照、单元过期拒绝、rollback 追加保留失败清单）；`ProjectSpaceMigrationModels`（`MAP_MISSING`/`MAP_UNEXPECTED`）；`JdbcProjectLegacyMappingRepository`（指纹含用户状态、水位含成员用户时间、单项目指纹查询）；`ProjectLegacyMappingRepository`、`ProjectLegacySpaceMapRepository`(+Jdbc)（workspace 活跃映射查询）。
- 后端（首轮保留）：画像/映射/批次全部领域、仓储、服务与控制器（详见首轮清单）。
- 前端：`web/src/modules/projectSpaces/api/projectSpacesApi.ts` 的 `resolveLegacyProjectSpace()`；`web/src/modules/projects/pages/ProjectsPage.tsx` 迁移提示条与跳转（首轮）。
- 数据库：`V058`、`V059`（首轮）；`V060__enforce_workspace_scoped_batch_link.sql`（map->batch workspace 复合外键）。
- 测试：`ProjectSpaceMigrationServiceIntegrationTests` 增至 22 用例（新增快照并发、历史批次取代、跨 workspace 外键负例、convergence 不写批次等）；`AdminProjectMigrationApiIntegrationTests` 补 convergence 正反用例。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 更新 | 登记第三轮补验完成并同步 program revision 9 |
| `docs/01-architecture/current-architecture.md` | 更新 | 登记 REPEATABLE_READ 快照、manifest 校验、convergence 端点与 V060 |
| `docs/00-product/current-product-scope.md` | 更新 | 登记修正后的 verify/convergence/rollback 语义 |
| `docs/01-architecture/technology-selection.md` | 更新 | P2 修正：迁移范围 V001...V060 |
| `docs/05-runbooks/project-space-migration.md` | 更新 | REPEATABLE_READ 快照、manifest 校验、convergence、回退失败保留与失败清单"未决"语义 |
| 本报告 | 更新 | 绑定 M4 T04/T05/T09/T10 第三轮补验后的实现与证据 |

## Validation

- Backend tests: 第三轮同一 8 类目标矩阵通过，55 tests、0 failures、0 errors；其中迁移服务 22 用例直接覆盖真实服务快照竞态与完整历史归属。Testcontainers PostgreSQL 16 从 V001 迁移到 V060。
- Migration rehearsal: v4 全链路 PASS（`.local-logs/m5-t04-rehearsal-v4.log`）：59 活跃项目、79 活跃成员、19 条孤立成员、31 条 IM 漂移；dry-run verify 409、真实 UNIT_FAILED、convergence `MAP_MISSING`、同批 resume 后 59/59 `ownedByBatch`、A 回退 B 重迁后 verify(A) 59 个 `MAP_SUPERSEDED`、verify(B) 全匹配、59 个确定性映射与两轮校验和一致。legacy `projects`、`project_members`、`conversations`、`conversation_members` 执行前后 SHA-256 均为 `7488c9fbe4469e9093ec8c650dc15958615de461e824d65cdd968b1ab4d8c5c3`，画像总数完全不变。
- Frontend lint: `pnpm --dir web lint` 通过（首轮与本轮 checkpoint）。
- Frontend build: `pnpm --dir web build` 通过（首轮 route-final）。
- Local quality gate: 第三轮 `pnpm verify:full` 通过，报告 `.local-reports/quality-gate-20260722T063825.md`：后端 161/161、前端 lint/build、协作服务 15/15、安全、Flyway、文档与 diff 均通过。
- Browser smoke: 本轮后端/演练修复无 UI 行为变化；M5 Stage 的 real isolated Chromium 全链路连续两次通过，最新证据 `.local-logs/m5-third-review-browser.log`。
- Diff hygiene: `git diff --check` 于 stage finish 复核。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 共享开发库的孤立成员（已停用用户）与 IM 群漂移保持登记不迁移，等待身份数据修复与后续 Stage 决定 | non-blocking | S07 迁移窗口前的数据治理 |
| N/A | 批次 failures 记录最近一次尝试的未决失败项；已解决项经 summary.attempt 与审计事件追踪，不在批次行内长期保留 | non-blocking | 运行手册语义章节 |
| N/A | 迁移期间 legacy 并发写由 REPEATABLE_READ 快照加单元级指纹重算兜底（失败可续跑），不追求对 legacy 写的强串行化 | non-blocking | 运行手册残留风险章节 |

## Next Steps

- 以第三轮补验后的生命周期 manifest 与无污染 rehearsal v4 证据更新 M5 Stage 结论，并重新执行 route-final。
