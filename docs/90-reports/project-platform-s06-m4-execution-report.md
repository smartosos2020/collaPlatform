# PROJECT-PLATFORM-S06-M4 Execution Report

## Scope
PROJECT-PLATFORM-S06-M4-T01 至 PROJECT-PLATFORM-S06-M4-T11

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M4-T01 | non-core | static | not-required | not-required | No | M1-M3 任务、代码、迁移、报告和 gap 审计 |
| PROJECT-PLATFORM-S06-M4-T02 | non-core | static | not-required | not-required | No | 字段/选项/规则/布局/访问/模板兼容矩阵 |
| PROJECT-PLATFORM-S06-M4-T03 | core-system | system-real-isolated | not-required | isolated | No | 稳定 key path 的版本/草稿兼容分析 API |
| PROJECT-PLATFORM-S06-M4-T04 | core-system | system-real-isolated | not-required | isolated | No | 精确 schema、hash 完整性和只读 snapshot adapter |
| PROJECT-PLATFORM-S06-M4-T05 | non-core | integration | not-required | not-required | No | runtime 禁止回读 active draft/live 配置的负向架构门禁 |
| PROJECT-PLATFORM-S06-M4-T06 | core-system | system-real-isolated | not-required | isolated | No | V001/V061/V065/V078 至 V085、重复迁移和备份恢复 |
| PROJECT-PLATFORM-S06-M4-T07 | core-system | system-real-isolated | not-required | isolated | No | 120 字段/2400 选项 snapshot/hash/分析/合并预算 |
| PROJECT-PLATFORM-S06-M4-T08 | core-system | system-real-isolated | not-required | isolated | No | 六身份和跨空间 API 最小披露 |
| PROJECT-PLATFORM-S06-M4-T09 | core-user | e2e-real-isolated | real | isolated | No | 发布、兼容、回滚、模板、离线、键盘、双视口和身份边界 |
| PROJECT-PLATFORM-S06-M4-T10 | non-core | e2e-real-isolated | real | isolated | No | 后端、迁移、前端、协作、架构、工作台、安全和生成物门禁 |
| PROJECT-PLATFORM-S06-M4-T11 | non-core | static | not-required | not-required | No | Program/索引/架构同步、S06 决策和 S07 准入 |

## Completed Items
- 逐项审计 M1-M3 的 37 个任务和三份执行报告；未知 schema、窄只读 adapter 和 runtime/live 依赖三个阻断项均在 M4 关闭，无任务需要 Reopen。
- 冻结字段、选项、规则、布局、访问策略和模板兼容矩阵，统一输出 compatible/review_required/migration_required/blocked。
- 实现版本对版本、草稿对当前版本的兼容分析服务/API，并在配置 UI 展示只读兼容结论。
- 实现 `PublishedSnapshotReader` 与 `PublishedSnapshotAdapter`；只接受精确 schema v1、完整且 hash 一致的冻结版本。
- 增加 runtime 负向 ArchUnit 门禁，禁止依赖 active draft、live 字段/选项/布局/策略 repository/command/service 及模板运行服务。
- 完成 V001/V061/V065/V078 至 V085、重复 migrate、legacy draft 诊断和真实 pg_dump/pg_restore 恢复演练。
- 全量回归发现 legacy migration rollback 会遗留 S06 配置草稿/回执；V085 与 repository cleanup 按外键顺序补齐空间清理闭包，正常写路径仍保持不可变。
- 完成 120 字段/2400 选项 snapshot/hash/兼容分析/三方合并预算及批量 repository 调用上界。
- 完成六身份 API 和统一真实 Chromium route-final；未使用 page.route 模拟核心响应。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M4-T01 | M1-M3 可追溯且阻断显式关闭 | 三份报告、审计结论、M4 修复 | planning/代码审计 | Not required | Done |
| PROJECT-PLATFORM-S06-M4-T02 | 所有要求变化均有分类 | `project-work-item-configuration-compatibility-matrix.md` | analyzer 分类测试 | Not required | Done |
| PROJECT-PLATFORM-S06-M4-T03 | 稳定影响、key path、原因和建议 | analyzer、publication service/controller | analyzer + 六身份 API PASS | UI 展示兼容结论 | Done |
| PROJECT-PLATFORM-S06-M4-T04 | 只消费完整冻结 snapshot | reader/adapter、精确 schema 判定 | schema0/schema2/hash mismatch 正反例 PASS | 版本链路真实消费 | Done |
| PROJECT-PLATFORM-S06-M4-T05 | runtime 无 live 配置旁路 | runtime package + `ModuleArchitectureTests` | ArchUnit 8/8 PASS | Not required | Done |
| PROJECT-PLATFORM-S06-M4-T06 | 四起点升级、重复和恢复稳定 | migration matrix integration test | 3/3 PASS；V085；pg_dump/restore PASS | Not required | Done |
| PROJECT-PLATFORM-S06-M4-T07 | 配置规模预算且无 N+1 | scale budget test | 2/2 PASS；120/2400；单项小于 3 秒 | 窄屏配置页无溢出 | Done |
| PROJECT-PLATFORM-S06-M4-T08 | 六身份与跨空间最小披露 | controller integration +既有 template/hidden suites | owner/admin 200；member/guest 403；outside/governor 404 | 同一真实 fixture 复核 | Done |
| PROJECT-PLATFORM-S06-M4-T09 | 统一用户链路可用 | route-final e2e spec | real API/DB，无核心 mock | real Chromium 1/1；1440/820、键盘、离线 PASS | Done |
| PROJECT-PLATFORM-S06-M4-T10 | 完整 route-final 无阻断 | workbench Gate Planner | fresh quality-gate 见 Validation | real isolated evidence persisted | Done |
| PROJECT-PLATFORM-S06-M4-T11 | 48 Task 和唯一结论一致 | Program revision 20、当前/目标架构、路线 | planning/document/diff checks | Not required | Done |

## Code Changes
- `server/src/main/java/com/colla/platform/modules/project/{application,api,domain,infrastructure,runtime}/**`
- `server/src/test/java/com/colla/platform/{architecture,modules/project}/**`
- `web/src/modules/projectSpaces/api/workItemConfigurationApi.ts`
- `web/src/modules/projectSpaces/components/ProjectWorkItemConfigurationDraftPanel.tsx`
- `web/e2e/project-platform-s06-m4-route-final.spec.ts`
- `docs/00-product/**`、`docs/01-architecture/**`、`docs/02-roadmap/current-roadmap.md`

## Validation
- Backend tests: full Maven 417/417 PASS；compatibility/scale/adapter/architecture 15/15 PASS；六身份 API 1/1 PASS。
- Migration: `WorkItemConfigurationMigrationMatrixIntegrationTests` 3/3 PASS，覆盖 V001/V061/V065/V078、V085、重复 migrate 和备份恢复。
- Frontend build: production build PASS，3302 modules transformed。
- Browser smoke: `project-platform-s06-m4-route-final.spec.ts` real isolated Chromium 1/1 PASS（20.9s），独立 API/Web 端口与可丢弃数据库，无核心响应 mock。
- Local quality gate: `.local-reports/quality-gate-20260726T034903.md` route-final PASS；后端 417/417、工作台 78/78、前端构建、架构、安全、规划、文档和 diff 门禁全部通过。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None in S06 scope | non-blocking | Closed |

## Scope Clarifications
- 兼容报告只描述配置变化，不生成 S07 实例值映射、回填、失败清单或迁移批次。
- 120 字段/2400 选项是配置装配/分析预算，不代表工作项实例容量、生产长稳或基础设施 HA。
- S06 不创建 `project_work_items`、动态字段值、实例命令、运行时流程或 legacy cutover。

## Go/No-Go
- **GO PROJECT-PLATFORM-S07 after S06 route archive and explicit activation.**
- S07 必须为每个实例显式绑定 `type_version_id + config_hash`，只经 `PublishedSnapshotAdapter` 解释快照。
- 任何 runtime 回读 active draft 或 S04/S05 live 配置、接受未知 schema、静默升级实例的实现都必须阻断。

## Next Steps
- 提交并推送 S06 完成态。
- 当前 `current_stage` 保持 `none`；归档 S06 和激活 S07 必须由后续明确指令执行。
