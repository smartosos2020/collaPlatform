# PROJECT-PLATFORM-S07-M1 Execution Report

## Scope
PROJECT-PLATFORM-S07-M1-T01 至 PROJECT-PLATFORM-S07-M1-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M1-T01 | non-core | static | not-required | not-required | No | legacy/project-space/config/runtime 调用方、表 owner 与禁止模式审计 |
| PROJECT-PLATFORM-S07-M1-T02 | non-core | unit | not-required | not-required | No | WorkItem identity、状态、版本与错误合同 |
| PROJECT-PLATFORM-S07-M1-T03 | core-system | system-real-isolated | not-required | isolated | No | V001-V086 空库迁移、复合约束和命令回执 |
| PROJECT-PLATFORM-S07-M1-T04 | core-system | system-real-isolated | not-required | isolated | No | workspace/space scoped repository、行锁、分页和乐观冲突 |
| PROJECT-PLATFORM-S07-M1-T05 | non-core | integration | not-required | not-required | No | published snapshot binding 与 live/draft 负向架构门禁 |
| PROJECT-PLATFORM-S07-M1-T06 | core-system | system-real-isolated | not-required | isolated | No | 创建、默认值、原子 receipt/audit/outbox/object link 与精确重放 |
| PROJECT-PLATFORM-S07-M1-T07 | non-core | integration | not-required | not-required | No | 详情/列表投影、hidden 零披露与 availableActions |
| PROJECT-PLATFORM-S07-M1-T08 | core-system | system-real-isolated | not-required | isolated | No | update/archive/restore、expected version 与失败回滚 |
| PROJECT-PLATFORM-S07-M1-T09 | non-core | integration | not-required | not-required | No | 用户协作 Controller、DTO、错误和 keyset 合同 |
| PROJECT-PLATFORM-S07-M1-T10 | core-system | system-real-isolated | not-required | isolated | No | work_item resolver、object rule/link、审计和最小 outbox |
| PROJECT-PLATFORM-S07-M1-T11 | core-system | system-real-isolated | not-required | isolated | No | 六身份、跨空间、幂等、回滚和真实 PostgreSQL 正反例 |
| PROJECT-PLATFORM-S07-M1-T12 | non-core | static | not-required | not-required | No | 当前架构、对象、事件、table owner 与路线同步 |

## Completed Items
- 审计 legacy project/issue、S02 空间映射、S03-S06 发布快照、平台对象、审计、outbox 和现有用户 API，确认 M1 不切换 legacy 读写，也不建立双写。
- V086 建立规范 WorkItem、编号计数器和持久命令回执，使用 workspace/space/type/version 复合外键、稳定展示编号、乐观版本、不可变完成回执和空间清理闭包。
- 实现 workspace/space 显式范围的 Repository、类型行锁、原子编号分配、无类型/按类型 keyset 列表及乐观更新；真实测试修复了 PostgreSQL 对 nullable type 参数无法推断的缺陷。
- 创建、读取、列表、更新、归档和恢复只经绑定的 published snapshot 解释默认值、required/write/hidden；实例保存 type version 与 config hash，完整性不符失败关闭。
- 用户协作 API、脱敏 DTO、稳定错误映射、`work_item` resolver/object rule/link、审计和 `work_item.changed` outbox 已接入。
- 真实 PostgreSQL 测试覆盖精确重放、异载荷 request id、冲突回滚、六身份、跨空间组合 ID、隐藏字段和副作用原子性；ArchUnit 阻止 runtime 回读 live/draft 配置。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M1-T01 | owner、写入口、依赖与禁止模式可定位 | 当前架构审计；legacy 边界与 M2-M5 分工 | planning/document checks | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T02 | identity/version/status/error 无歧义 | `WorkItemModels`、API DTO、exception handler | compile + lifecycle integration | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T03 | 复合 FK、唯一性、版本、索引和清理闭包 | V086；`JdbcProjectSpaceRepository.deleteSpace` | Flyway 86/86 + integration PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T04 | 所有查询显式 scoped，分页和冲突稳定 | `WorkItemRepository`、`JdbcWorkItemRepository` | cross-space/list/version tests PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T05 | 绑定 current immutable snapshot，无 live 注入 | `PublishedSnapshotAdapter` + runtime projection | `ModuleArchitectureTests` 9 rules PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T06 | 默认值、访问和副作用同事务，重放精确 | `WorkItemService.create` | create/replay/rollback assertions PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T07 | hidden 零披露、列表/详情/actions 稳定 | `WorkItemRuntimeProjection`、service view/page | guest/owner/list assertions PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T08 | expected version 与状态转换原子 | service update/transition + repository CAS | stale rollback + archive/restore PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T09 | 用户路由、DTO、错误和 cursor 稳定 | `UserWorkItemController`、`WorkItemApiDtos` | compile + service/API boundary tests PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T10 | canonical identity、link/audit/outbox 最小化 | resolver、object rule、platform link、event payload | object/audit/event row assertions PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T11 | 六身份、跨空间、幂等和故障正反例 | `WorkItemServiceIntegrationTests` | 6 tests + ArchUnit 9 rules PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M1-T12 | 文档只声明 M1 已实现事实 | current architecture/object/event/owner/roadmap | architecture/planning/diff checks | Not required | Done |

## Code Changes
- `server/src/main/resources/db/migration/V086__create_canonical_project_work_items.sql`
- `server/src/main/java/com/colla/platform/modules/project/{api,application,domain,infrastructure}/**`
- `server/src/test/java/com/colla/platform/{architecture,modules/project/application}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/{current-architecture,platform-object-model,platform-module-contracts,event-side-effect-matrix}.md`

## Validation
- Backend compile: PASS.
- Backend tests: `mvn -Dtest=WorkItemServiceIntegrationTests,ModuleArchitectureTests test`，15/15 PASS。
- Frontend build: Not required；M1 未修改 `web/**`，用户侧工作项页面明确归属 M5。
- Migration: isolated PostgreSQL 16，Flyway V001-V086 86/86 PASS。
- Browser smoke: Not required；M1 只交付后端规范实例、API 与公共对象合同，用户侧工作项页面属于 M5。
- Local quality gate: light checkpoint PASS（`.local-reports/quality-gate-20260726T055916.md`）；stage finish 的 backend/workbench/architecture/security/planning/diff 执行项已通过。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M2 | 类型化动态值、查询投影、参与者和不可变活动尚未实现 | 不阻断 M1；不得把 M1 称为完整工作项产品 | M2 |
| PROJECT-PLATFORM-S07-M3 | legacy resolver、ID map 和切流尚未实现 | legacy 继续保持唯一旧业务事实源 | M3 |
| PROJECT-PLATFORM-S07-M4 | 批次迁移、verify/resume/rollback 尚未实现 | 禁止生产切流 | M4 |
| PROJECT-PLATFORM-S07-M5 | 用户侧列表/详情/评论/附件竖切尚未实现 | M1 无浏览器验收 | M5 |

## Next Steps
- 进入 PROJECT-PLATFORM-S07-M2，建立动态字段 codec、受控查询投影、参与者和活动账本。
- 保持 legacy projects/issues 读写路径不变，直至 M3-M4 显式切流合同与迁移证据完成。
