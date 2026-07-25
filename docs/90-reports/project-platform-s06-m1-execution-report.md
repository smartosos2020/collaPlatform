# PROJECT-PLATFORM-S06-M1 Execution Report

## Scope
PROJECT-PLATFORM-S06-M1-T01 至 PROJECT-PLATFORM-S06-M1-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M1-T01 | non-core | static | not-required | not-required | No | 审计 S03-S05 配置表、服务、写入口、授权、迁移与 S07 禁止项 |
| PROJECT-PLATFORM-S06-M1-T02 | core-system | system-real-isolated | not-required | isolated | No | 状态、诊断、版本、错误码与唯一 active draft 合同 |
| PROJECT-PLATFORM-S06-M1-T03 | core-system | system-real-isolated | not-required | isolated | No | V080 空库/升级迁移、约束、触发器、owner 与命令回执 |
| PROJECT-PLATFORM-S06-M1-T04 | core-system | system-real-isolated | not-required | isolated | No | legacy draft 迁移诊断、V081 状态收紧及 published v1 保持 |
| PROJECT-PLATFORM-S06-M1-T05 | core-system | system-real-isolated | not-required | isolated | No | 完整类型、字段、选项、规则、布局、条件和策略组装 |
| PROJECT-PLATFORM-S06-M1-T06 | core-system | system-real-isolated | not-required | isolated | No | 规范排序、schema、SHA-256 与未知 schema 负例 |
| PROJECT-PLATFORM-S06-M1-T07 | core-system | system-real-isolated | not-required | isolated | No | 行锁、复合隔离、乐观冲突和精确命令重放 |
| PROJECT-PLATFORM-S06-M1-T08 | core-system | system-real-isolated | not-required | isolated | No | 成功写入同事务刷新，失败/重放/no-op 不改变草稿 |
| PROJECT-PLATFORM-S06-M1-T09 | core-system | system-real-isolated | not-required | isolated | No | 类型、字段、布局、策略及预置 reconcile 写入口覆盖 |
| PROJECT-PLATFORM-S06-M1-T10 | core-system | system-real-isolated | not-required | isolated | No | 结构、引用、预算、访问策略和稳定 key path 诊断 |
| PROJECT-PLATFORM-S06-M1-T11 | core-user | e2e-real-isolated | real | isolated | No | owner API 全流程及 member/outside 最小披露 |
| PROJECT-PLATFORM-S06-M1-T12 | core-user | e2e-real-isolated | real | isolated | No | 配置页状态、诊断、校验、写后刷新、放弃和新草稿 |

## Completed Items
- 建立 `ConfigurationDraft` 唯一可变权威、完整 snapshot schema v1、稳定 canonical hash、诊断和 aggregate version 合同。
- 新增 V080/V081：草稿、命令回执、legacy 诊断表，唯一 active draft、终态不可变触发器及 legacy version draft 退役。
- 将类型、字段、选项/规则、布局、条件、字段访问策略和预置 reconcile 的成功写入接到同事务草稿刷新；静态守卫阻止漏接入口。
- 提供 GET/PUT draft、validate、abandon API，精确 request replay、乐观冲突、空间 owner/admin 授权和最小披露。
- 在类型、字段、布局配置页共享草稿状态、hash/version、诊断、校验和放弃动作。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M1-T01 | 双权威与复用边界可定位 | 目标架构 22.7、写路径审计和 `WorkItemConfigurationDraftWritePathGuardTests` | 5 type、5 field、1 layout persist、1 reconcile 入口守卫 PASS | Not required | Done |
| PROJECT-PLATFORM-S06-M1-T02 | 唯一草稿状态与错误合同 | `WorkItemConfigurationModels`、exception handler、目标架构状态语义 | domain/canonicalizer/validator tests PASS | 状态标签与动作投影可见 | Done |
| PROJECT-PLATFORM-S06-M1-T03 | 复合隔离、唯一 active、回执和终态不可变 | V080、repository/JDBC、table owner manifest | foundation migration/repository 1/1 PASS | Not required | Done |
| PROJECT-PLATFORM-S06-M1-T04 | legacy draft 可诊断迁移且 version 状态收紧 | V080 legacy diagnostics、V081 status constraint | V001-V079 -> V081、重复 migrate 与约束 PASS | Not required | Done |
| PROJECT-PLATFORM-S06-M1-T05 | snapshot 自包含所有 S03-S05 配置域 | `WorkItemConfigurationSnapshotAssembler` | assembler tests 与类型/字段/布局 19/19 PASS | 页面 hash 随类型写入更新 | Done |
| PROJECT-PLATFORM-S06-M1-T06 | 语义稳定 hash 与 schema 拒绝 | `WorkItemConfigurationSnapshotCanonicalizer` | canonicalizer tests PASS | hash/schema version 可见 | Done |
| PROJECT-PLATFORM-S06-M1-T07 | 并发、隔离、回执确定 | `ConfigurationDraftRepository`、`JdbcConfigurationDraftRepository` | repository、API replay/conflict tests PASS | validate/abandon 请求均带稳定 request id | Done |
| PROJECT-PLATFORM-S06-M1-T08 | 同事务刷新且失败不触碰 | `WorkItemConfigurationDraftService.refreshAfterMutation` MANDATORY | controller integration 与静态负向守卫 PASS | 编辑保存后状态回到 editing、版本递增 | Done |
| PROJECT-PLATFORM-S06-M1-T09 | S03-S05 每条写路径进入同一 aggregate | type/field/layout/preset services | 类型、字段、布局集成 19/19 PASS | 类型编辑即时刷新同一草稿面板 | Done |
| PROJECT-PLATFORM-S06-M1-T10 | warning/error、引用和预算诊断稳定 | `WorkItemConfigurationValidator` | validator budget/reference tests PASS | `missing_layout_kind` warning 和 key path 可见 | Done |
| PROJECT-PLATFORM-S06-M1-T11 | API、授权、动作和最小披露 | `WorkItemConfigurationDraftController`、service | owner flow 200；member 403；outside 404；replay/conflict PASS | real isolated owner 校验和放弃流程 PASS | Done |
| PROJECT-PLATFORM-S06-M1-T12 | UI、前后端、迁移和 checkpoint 闭环 | shared draft panel、query invalidation、执行报告 | lint、build、architecture 97 tables/93 exceptions PASS | real isolated Playwright 1/1；1366 无横向溢出 | Done |

## Code Changes
- `server/src/main/resources/db/migration/V080__create_work_item_configuration_draft_foundation.sql`
- `server/src/main/resources/db/migration/V081__retire_work_item_type_version_draft_status.sql`
- `server/src/main/java/com/colla/platform/modules/project/{domain,application,infrastructure,api}/**`
- `server/src/test/java/com/colla/platform/modules/project/{application,infrastructure,api}/**`
- `web/src/modules/projectSpaces/api/workItemConfigurationApi.ts`
- `web/src/modules/projectSpaces/components/ProjectWorkItemConfigurationDraftPanel.tsx`
- `web/src/modules/projectSpaces/{components,pages}/**`
- `web/e2e/project-platform-s06-m1-configuration-draft.spec.ts`
- `tools/workbench/config/platform-table-owners.json`
- `docs/00-product/**`、`docs/01-architecture/**`、`docs/02-roadmap/current-roadmap.md`

## Validation
- Backend tests: compile/test-compile PASS；snapshot unit/static 6/6；foundation migration 1/1；type/field/layout integration 19/19。
- Frontend build: `pnpm --dir web lint` PASS；production build PASS，3301 modules transformed。
- Local quality gate: checkpoint `quality-gate-20260725T233148.md` PASS；architecture inventory/contracts 同时确认 97 tables、93 exceptions、22 contract files。
- Browser smoke: `project-platform-s06-m1-configuration-draft.spec.ts` real isolated Chromium 1/1 PASS（7.1s）。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M2 | 发布事务、不可变完整版本、diff 和 rollback 尚未实现 | 不影响 M1；M2 阻断项 | Current roadmap |
| PROJECT-PLATFORM-S06-M3 | 模板版本、安装、三方升级与 detach 尚未实现 | 不影响 M1；M3 阻断项 | Current roadmap |

## Scope Clarifications
- M1 不创建 `project_work_items`、动态值或实例迁移，也不让 S07 直接读取 live 配置。
- M1 的 `valid` 表示发布前校验通过；它不是已发布版本。M2 才执行原子 publication。

## Next Steps
- 完成工作台 checkpoint/finish，提交并推送 M1。
- 从新的工作上下文启动 PROJECT-PLATFORM-S06-M2。
