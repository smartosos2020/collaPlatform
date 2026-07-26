# PROJECT-PLATFORM-S06-M2 Execution Report

## Scope
PROJECT-PLATFORM-S06-M2-T01 至 PROJECT-PLATFORM-S06-M2-T13

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M2-T01 | non-core | static | not-required | not-required | No | 发布、版本、指针、回执、diff 和 rollback 合同冻结 |
| PROJECT-PLATFORM-S06-M2-T02 | core-system | system-real-isolated | not-required | isolated | No | V082 完整版本、回执、lineage 与不可变触发器 |
| PROJECT-PLATFORM-S06-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | 类型行锁、锁内版本分配和 current pointer 原子切换 |
| PROJECT-PLATFORM-S06-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | validate/version/pointer/draft/audit/outbox/receipt 同成同败 |
| PROJECT-PLATFORM-S06-M2-T05 | core-system | system-real-isolated | not-required | isolated | No | 精确重放、异载荷冲突、并发和五个故障边界 |
| PROJECT-PLATFORM-S06-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | 版本列表/详情、legacy partial 标识及最小披露 |
| PROJECT-PLATFORM-S06-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | 稳定 key path 与四级语义 diff |
| PROJECT-PLATFORM-S06-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | version/version 与 draft/current diff API |
| PROJECT-PLATFORM-S06-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | 历史完整版本准备 rollback draft，不移动指针 |
| PROJECT-PLATFORM-S06-M2-T10 | core-system | system-real-isolated | not-required | isolated | No | rollback draft 重校验并发布更高版本 |
| PROJECT-PLATFORM-S06-M2-T11 | core-user | e2e-real-isolated | real | isolated | No | 版本历史、diff、breaking 确认和回滚 UI |
| PROJECT-PLATFORM-S06-M2-T12 | core-user | e2e-real-isolated | real | isolated | No | owner 真实发布/编辑/回滚链路及 1366 响应式 |
| PROJECT-PLATFORM-S06-M2-T13 | non-core | static | not-required | not-required | No | 当前架构、事件矩阵、表 owner 和 checkpoint 一致 |

## Completed Items
- 新增 V082 完整 published snapshot 元数据、publication receipt 和数据库不可变保护。
- 以类型行锁串行化发布，在同一事务完成版本分配、pointer 切换、草稿关闭、审计、outbox 和精确回执。
- 提供版本列表/详情、version/version diff、draft/current diff、prepare rollback 和普通再发布 API。
- 提供稳定 key path 的 additive/behavioral/conditional/breaking 差异分类和 breaking 显式确认。
- 配置工作台增加版本历史、差异摘要、发布确认和 rollback-as-new-version 操作。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M2-T01 | 合同无双义 | 目标架构 22.7/22.8、版本/回执/domain records | planning/architecture check | Not required | Done |
| PROJECT-PLATFORM-S06-M2-T02 | 历史和完成回执不可改写 | V082 triggers/constraints | migration + mutation negative assertions | Not required | Done |
| PROJECT-PLATFORM-S06-M2-T03 | 并发无重复版本或丢失指针 | `JdbcConfigurationPublicationRepository` | 两发布者竞争：一成功、一受控冲突、current=v2 | Not required | Done |
| PROJECT-PLATFORM-S06-M2-T04 | 发布事务无半完成 | `WorkItemConfigurationPublicationService` | 五个 failure point 全部回滚 | Not required | Done |
| PROJECT-PLATFORM-S06-M2-T05 | request id 语义稳定 | publication command receipt | exact replay、异载荷 409、失败无 receipt | Not required | Done |
| PROJECT-PLATFORM-S06-M2-T06 | 历史最小披露 | publication controller/service | owner 200、member 403、outside 404、legacy partial 标识 | 版本列表不展示无权入口 | Done |
| PROJECT-PLATFORM-S06-M2-T07 | diff 可解释且有预算 | `WorkItemConfigurationDiffEngine` | 排序、摘要、required/access/removal 和 1000 项预算 tests | 差异摘要与 breaking 提示可见 | Done |
| PROJECT-PLATFORM-S06-M2-T08 | 两类 diff API 稳定 | controller endpoints | API integration tests | 发布前后摘要刷新 | Done |
| PROJECT-PLATFORM-S06-M2-T09 | rollback 不改历史/指针 | `prepareRollback` lineage | source v2 -> rollback draft，current 保持 v3 | 回滚按钮生成待发布草稿 | Done |
| PROJECT-PLATFORM-S06-M2-T10 | rollback 产生更高版本 | rollback lineage + normal publish | v2/v3 后回滚发布 v4，current=v4 | 真实回滚发布链路 | Done |
| PROJECT-PLATFORM-S06-M2-T11 | UI 可操作、危险动作明确 | draft panel history/diff/modals | lint + production build | real isolated Chromium | Done |
| PROJECT-PLATFORM-S06-M2-T12 | 正反例、隔离、浏览器闭环 | integration/e2e suites | publication service/API suite PASS | real isolated 1/1；1366 无横向溢出 | Done |
| PROJECT-PLATFORM-S06-M2-T13 | 文档与 owner 清单同步 | current/product/target/event/table owner docs | workbench checkpoint/finish | Not required | Done |

## Code Changes
- `server/src/main/resources/db/migration/V082__publish_work_item_configuration_snapshots.sql`
- `server/src/main/java/com/colla/platform/modules/project/{domain,application,infrastructure,api}/**`
- `server/src/test/java/com/colla/platform/modules/project/{application,api,infrastructure}/**`
- `web/src/modules/projectSpaces/api/workItemConfigurationApi.ts`
- `web/src/modules/projectSpaces/components/ProjectWorkItemConfigurationDraftPanel.tsx`
- `web/e2e/project-platform-s06-m2-configuration-publication.spec.ts`
- `tools/workbench/config/platform-table-owners.json`
- `docs/00-product/**`、`docs/01-architecture/**`、`docs/02-roadmap/current-roadmap.md`

## Validation
- Backend tests: `WorkItemConfigurationPublicationServiceIntegrationTests`、diff engine 和 publication API 目标测试 PASS。
- Frontend build: lint 与 production build PASS，3301 modules transformed。
- Local quality gate: checkpoint `quality-gate-20260726T002241.md` PASS；首次 finish 的全部实现门禁 PASS，仅报告标签合同要求重跑。
- Browser smoke: `project-platform-s06-m2-configuration-publication.spec.ts` real isolated Chromium 1/1 PASS。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M3 | 模板版本、安装、升级三方合并与 detach 尚未实现 | 不影响 M2；M3 阻断项 | Current roadmap |
| PROJECT-PLATFORM-S06-M4 | 完整兼容矩阵和 S07 published snapshot adapter 尚未收口 | 不影响 M2；S07 准入阻断项 | Current roadmap |

## Scope Clarifications
- S06 diff 只解释配置变化，不冒充尚不存在的 WorkItem 实例迁移计划。
- legacy partial v1 可列出和审计，但不能 diff、rollback 或被未来 S07 adapter 消费。
- S06-M2 不创建 `project_work_items`、字段值、流程实例或 legacy cutover。

## Next Steps
- 完成 M2 checkpoint/finish，提交并推送。
- 从新工作上下文启动 PROJECT-PLATFORM-S06-M3。
