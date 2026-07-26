# PROJECT-PLATFORM-S06-M3 Execution Report

## Scope
PROJECT-PLATFORM-S06-M3-T01 至 PROJECT-PLATFORM-S06-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M3-T01 | non-core | static | not-required | not-required | No | 平台/workspace 模板、版本、installation、lineage、升级和 detach 合同冻结 |
| PROJECT-PLATFORM-S06-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | V083/V084 模板、不可变版本、installation、升级历史和命令回执 |
| PROJECT-PLATFORM-S06-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | 复合隔离 Repository、平台/workspace 分层查询和撤回历史保留 |
| PROJECT-PLATFORM-S06-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | 平台预置确定性导入、稳定版本/hash 和数据库运行时权威 |
| PROJECT-PLATFORM-S06-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | 完整 published snapshot 创建 workspace 模板 |
| PROJECT-PLATFORM-S06-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | 跨空间安装复制、稳定 key 重绑定和 active draft 更新 |
| PROJECT-PLATFORM-S06-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | base/upstream/local 三方合并、additive 自动合并和显式冲突 |
| PROJECT-PLATFORM-S06-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | 无写 preview、选择性 apply、原子 lineage 和精确幂等回执 |
| PROJECT-PLATFORM-S06-M3-T09 | core-system | system-real-isolated | not-required | isolated | No | detach 精确重放并保留 draft/hash/最后 lineage |
| PROJECT-PLATFORM-S06-M3-T10 | core-user | e2e-real-isolated | real | isolated | No | 模板目录、安装、升级冲突决策和解绑 UI |
| PROJECT-PLATFORM-S06-M3-T11 | core-user | e2e-real-isolated | real | isolated | No | owner 六身份、并发安装、跨空间、detach 和 1366px 真实浏览器闭环 |
| PROJECT-PLATFORM-S06-M3-T12 | non-core | static | not-required | not-required | No | 产品、架构、事件和表 owner 合同同步并完成 checkpoint |

## Completed Items
- 新增平台/workspace 两级配置模板、不可变模板版本、每类型唯一 installation、升级历史和精确命令回执。
- 平台预置目录成为确定性导入源；首次目录访问幂等导入数据库，后续模板运行读取以数据库版本为权威。
- workspace 模板只从完整 published snapshot 创建；安装复制到目标 active draft，并按稳定 key 重绑定目标类型、字段、选项、布局和访问策略身份。
- 三方合并以 base/upstream/local 为输入，自动接纳无冲突 additive 变化，所有冲突必须逐项选择 local 或 upstream 后才能 apply。
- 安装、升级和解绑均具备 request ID、payload hash、乐观版本、审计、outbox、lineage 与精确重放。
- 配置工作台提供模板目录、安装确认、升级预览、冲突决策、应用升级和解绑入口。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M3-T01 | 来源、版本与三方语义无歧义 | `WorkItemConfigurationTemplateModels`、目标架构 22.9 | planning/architecture check | Not required | Done |
| PROJECT-PLATFORM-S06-M3-T02 | schema 隔离、不可变和 owner 完整 | V083/V084、table owner manifest | 空库 migrate、重复 migrate、immutable update/delete 负例 | Not required | Done |
| PROJECT-PLATFORM-S06-M3-T03 | 无跨空间枚举或历史删除 | `ConfigurationTemplateRepository` / JDBC 实现 | service integration 六身份与跨空间断言 | Not required | Done |
| PROJECT-PLATFORM-S06-M3-T04 | 导入可重复且运行时数据库权威 | `ensurePlatformTemplates` + preset import | 重复 catalog 不生成重复版本 | catalog 来自真实 API | Done |
| PROJECT-PLATFORM-S06-M3-T05 | 只接受完整发布快照 | `createWorkspaceTemplate` | partial/无权/冲突负例及完整快照正例 | 发布后创建入口按动作显示 | Done |
| PROJECT-PLATFORM-S06-M3-T06 | 复制、重绑定且本地可编辑 | `install` / `rebindSnapshot` | 跨空间 UUID 不泄漏、draft hash/version 更新 | 真实 UI 安装 PASS | Done |
| PROJECT-PLATFORM-S06-M3-T07 | additive 自动合并、冲突显式 | `WorkItemConfigurationThreeWayMerge` | 3/3 单元场景 PASS | 冲突卡逐项提供本地/上游选择 | Done |
| PROJECT-PLATFORM-S06-M3-T08 | preview 无写、apply 原子且幂等 | preview/apply service + receipts | preview 不变、apply/replay、异载荷冲突 PASS | UI 预览与应用入口可操作 | Done |
| PROJECT-PLATFORM-S06-M3-T09 | 解绑保留本地事实 | detach + detached installation summary | draft hash 保留、重放和异载荷冲突 PASS | 真实 UI 解绑后显示已解绑 | Done |
| PROJECT-PLATFORM-S06-M3-T10 | 模板 UI 信息和危险操作可访问 | `ProjectWorkItemConfigurationTemplatePanel` | lint + production build PASS | real isolated Chromium PASS | Done |
| PROJECT-PLATFORM-S06-M3-T11 | 正反例、并发和身份闭环 | service integration + e2e spec | 并发安装稳定 200/409；owner/admin/member/guest/outside/governor 断言 | real isolated 1/1；1366 无横向溢出 | Done |
| PROJECT-PLATFORM-S06-M3-T12 | 文档、事件、表 owner 和工作循环一致 | current/product/target/event/table owner docs | checkpoint/finish | Not required | Done |

## Code Changes
- `server/src/main/resources/db/migration/V083__create_work_item_configuration_templates.sql`
- `server/src/main/resources/db/migration/V084__complete_work_item_configuration_template_ownership.sql`
- `server/src/main/java/com/colla/platform/modules/project/{domain,application,infrastructure,api}/**`
- `server/src/test/java/com/colla/platform/modules/project/{application,infrastructure}/**`
- `web/src/modules/projectSpaces/api/workItemConfigurationApi.ts`
- `web/src/modules/projectSpaces/components/ProjectWorkItemConfiguration{Draft,Template}Panel.tsx`
- `web/e2e/project-platform-s06-m3-configuration-templates.spec.ts`
- `tools/workbench/config/platform-table-owners.json`
- `docs/00-product/**`、`docs/01-architecture/**`、`docs/02-roadmap/current-roadmap.md`

## Validation
- Backend tests: `WorkItemConfigurationThreeWayMergeTests`、`WorkItemConfigurationTemplateServiceIntegrationTests`、`ConfigurationTemplateFoundationIntegrationTests` PASS。
- Frontend build: lint 与 production build PASS，3302 modules transformed。
- Local quality gate: `.local-reports/quality-gate-20260726T011839.md` 的 backend compile、frontend lint、workbench typecheck/tests 实现门禁 PASS；报告标签修正后由 finish 复核。
- Browser smoke: `project-platform-s06-m3-configuration-templates.spec.ts` real isolated Chromium 1/1 PASS；并发败者标准化为 409。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M4 | 完整兼容矩阵、PublishedSnapshotAdapter、迁移矩阵和 route-final 尚未收口 | 不影响 M3；S07 准入阻断项 | Current roadmap |

## Scope Clarifications
- 模板安装只复制完整 snapshot 到 active draft；installation 不是 live 引用，上游模板改变不会静默改写本地配置。
- `WorkItemTypePresetCatalog` 仅保留为平台模板导入源和 legacy 预置引导来源；模板目录、版本、installation 和升级的运行时权威均在数据库。
- 当前专用字段/布局编辑器仍属于 S04/S05 live 配置适配路径；S06 模板命令只操作统一 draft。S07 运行读取必须通过 M4 的 `PublishedSnapshotAdapter`，不得回读这些 live repository。
- S06-M3 不创建 WorkItem、字段值、实例升级或 legacy 实例迁移能力。

## Next Steps
- 完成 M3 checkpoint/finish，提交并推送。
- 从新工作上下文启动 PROJECT-PLATFORM-S06-M4，并以 route-final 给出 S06 唯一 Go/Reopen 结论。
