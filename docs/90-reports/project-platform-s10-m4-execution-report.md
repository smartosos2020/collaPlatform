# PROJECT-PLATFORM-S10-M4 Execution Report

## Scope
PROJECT-PLATFORM-S10-M4-T01 到 PROJECT-PLATFORM-S10-M4-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M4-T01 | non-core | static | not-required | not-required | No | M1-M3 报告、代码、schema 与边界逐项审计 |
| PROJECT-PLATFORM-S10-M4-T02 | core-system | system-real-isolated | not-required | isolated | No | relation layout node canonicalization/projection |
| PROJECT-PLATFORM-S10-M4-T03 | core-system | system-real-isolated | not-required | isolated | No | published type matrix 下的受权候选搜索 |
| PROJECT-PLATFORM-S10-M4-T04 | core-system | system-real-isolated | not-required | isolated | No | joined 正反向摘要、硬限和校准令牌 |
| PROJECT-PLATFORM-S10-M4-T05 | core-system | system-real-isolated | not-required | isolated | No | dependency/blocking upstream/downstream 有界递归 |
| PROJECT-PLATFORM-S10-M4-T06 | core-system | system-real-isolated | not-required | isolated | No | expected version preview 与输入保留 |
| PROJECT-PLATFORM-S10-M4-T07 | core-system | system-real-isolated | not-required | isolated | No | 复用 M3 local navigation，冻结折叠/list/keyboard config |
| PROJECT-PLATFORM-S10-M4-T08 | core-system | system-real-isolated | not-required | isolated | No | relation event 失效提示与 API 重读 |
| PROJECT-PLATFORM-S10-M4-T09 | core-system | system-real-isolated | not-required | isolated | No | legacy manifest/backfill/resume/verify/rollback schema |
| PROJECT-PLATFORM-S10-M4-T10 | core-system | system-real-isolated | not-required | isolated | No | owner/admin、reason/version/confirmation 治理 API |
| PROJECT-PLATFORM-S10-M4-T11 | core-system | system-real-isolated | not-required | isolated | No | layout/search/impact/Flyway 自动化 |
| PROJECT-PLATFORM-S10-M4-T12 | non-core | static | not-required | not-required | No | architecture/event/runbook/report/checkpoint |

## Completed Items
- 复核 M1-M3 36 项实现和证据，没有发现需要 Reopen 的关系权威、并发、层级或权限阻断。
- layout graph 增加独立 `relation` node；config 只保存永久 relation key、模式与有界呈现合同，不引用 field id 或写入 field JSON。
- 新增受权目标搜索、正反向摘要、变更 preview、有界 dependency/blocking impact API；候选只来自已发布 type matrix 和活动同空间 WorkItem。
- 局部树继续复用 M3 hierarchy navigation，relation control 冻结折叠、替代列表和键盘导航开关，不建立 S13 全局树事实。
- 新增 V100 migration batch/unit/verification 权威与 owner/admin plan/execute/resume/verify/rollback API；原因、expected version 和精确危险确认失败关闭。
- legacy 单元按 canonical、非 WorkItem 保留、未解析、跨空间、已删除分类；只有 canonical 单元调用唯一 relation command。
- 同步 current/target architecture、module owner、event boundary 和 relation migration runbook。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S10-M4-T01 | M1-M3 36 项可追溯且无旁路事实 | M1-M3 reports、V097-V099、relation/hierarchy services | 本地审计与 compile | 不需要：审计任务 | Done |
| PROJECT-PLATFORM-S10-M4-T02 | 永久 key 且不伪装字段 JSON | NodeType.relation、strict relation config、access projection | canonicalizer relation tests | 不需要：M5 才交付 UI | Done |
| PROJECT-PLATFORM-S10-M4-T03 | 候选受空间/类型/归档过滤 | target search repository + published binding | target self/type matrix unit test | 不需要：后端合同 | Done |
| PROJECT-PLATFORM-S10-M4-T04 | 双向摘要硬限且无 N+1 | existing joined projection + summary grouping | compile/regression；limit=200 | 不需要：后端合同 | Done |
| PROJECT-PLATFORM-S10-M4-T05 | 上下游有界且不越权推断 | recursive impact repository/service | truncation + batch endpoint test | 不需要：后端合同 | Done |
| PROJECT-PLATFORM-S10-M4-T06 | 冲突刷新保留输入 | ChangePreview current versions/capability/retainedInput | service compile/test | 不需要：M5 UI 验收 | Done |
| PROJECT-PLATFORM-S10-M4-T07 | 局部树可折叠且有 list/keyboard fallback | M3 navigation + relation control config | canonical config assertions | 不需要：M5 UI 验收 | Done |
| PROJECT-PLATFORM-S10-M4-T08 | event 只触发校准 | calibration token + event matrix contract | public payload regression/compile | 不需要：事件合同 | Done |
| PROJECT-PLATFORM-S10-M4-T09 | 显式 manifest/backfill/verify/resume/rollback | V100 + migration repository/service | V001-V100 real PostgreSQL PASS | 不需要：治理后端 | Done |
| PROJECT-PLATFORM-S10-M4-T10 | owner/admin、reason/version/confirmation | migration controller/access/CAS/audit | compile + schema constraints | 不需要：M5 UI 验收 | Done |
| PROJECT-PLATFORM-S10-M4-T11 | 无无限遍历、重复迁移或污染 | hard budgets、stable request ids、unit ledger | focused unit 6/6；Flyway real PASS | 不需要：M4 无 UI | Done |
| PROJECT-PLATFORM-S10-M4-T12 | 合同与 runbook 同步 | architecture/event/runbook/report/table owner | architecture contracts PASS | 不需要：文档任务 | Done |

## Code Changes
- 新增 `WorkItemRelationExperienceModels/Service/Controller` 与 target/summary/impact/preview API。
- 扩展 WorkItem/Relation Repository：受权候选搜索、批量端点读取和有界 impact CTE。
- 扩展 layout canonicalizer/validator/projection 和 Web 可传输 node type contract。
- 新增 V100 relation migration control-plane schema、repository、service、controller 与空间清理闭包。
- 新增 layout/experience 自动化并把 Flyway/migration matrix 基线更新到 V100。
- 同步 table owner、current/target architecture、event matrix 和运维 runbook。

## Validation
- Backend tests: `mvn -q -DskipTests compile` PASS；`mvn -q -DskipTests test` test-compile PASS；`WorkItemLayoutCanonicalizerTests,WorkItemRelationExperienceServiceTests` PASS；`WorkItemRelationFoundationIntegrationTests` 使用真实 PostgreSQL/Testcontainers 验证 V001-V100 100 次迁移及 repeat migrate PASS。
- Architecture: `pnpm architecture:contracts` PASS，`modules=15; activeTables=146; exceptions=93; contractFiles=27`。
- Frontend build: Not required；M4 只交付后端与交互合同，真实配置/成员 UI 属于 M5。
- Browser smoke: Not required；M4 路线明确把真实浏览器闭环留给 M5。
- Local quality gate: M4 checkpoint `.local-reports/quality-gate-20260727T035051.md` PASS；fresh finish targeted backend step `.local-reports/quality-gate-20260727T035529-backend-targeted-tests.log` PASS。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None；真实 UI/浏览器验收是 M5 明确范围，不是 M4 gap | non-blocking | M5 |

## Next Steps
- 从 PROJECT-PLATFORM-S10-M5-T01 审计 M1-M4 后，交付配置、成员、层级、impact 与 migration UI，执行六身份真实浏览器及 route-final。
