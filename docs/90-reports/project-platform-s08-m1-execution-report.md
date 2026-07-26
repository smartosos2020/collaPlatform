# PROJECT-PLATFORM-S08-M1 Execution Report

## Scope

PROJECT-PLATFORM-S08-M1-T01 到 PROJECT-PLATFORM-S08-M1-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M1-T01 | non-core | static | not-required | not-required | No | S06/S07/legacy issue/S09 调用方、表 owner、复用端口与禁止依赖审计 |
| PROJECT-PLATFORM-S08-M1-T02 | non-core | unit | not-required | not-required | No | State/Action/Transition/Guard key、分类、动作类型与错误合同 |
| PROJECT-PLATFORM-S08-M1-T03 | non-core | integration | not-required | not-required | No | schema v1/v2、canonical hash、无状态流能力和未来 schema 失败关闭 |
| PROJECT-PLATFORM-S08-M1-T04 | core-system | system-real-isolated | not-required | isolated | No | V001-V091、复合 FK、唯一性、索引、回执与历史不可变 |
| PROJECT-PLATFORM-S08-M1-T05 | non-core | integration | not-required | not-required | No | 唯一配置草稿保存状态流、live mutation 保留、无定义双写表 |
| PROJECT-PLATFORM-S08-M1-T06 | non-core | unit | not-required | not-required | No | 唯一 initial、可达性、终态、重复 key、悬空和死路 diagnostics |
| PROJECT-PLATFORM-S08-M1-T07 | non-core | unit | not-required | not-required | No | guard/operator/operand 注册表、隐藏字段和未知扩展失败关闭 |
| PROJECT-PLATFORM-S08-M1-T08 | non-core | unit | not-required | not-required | No | 授权角色、required field、field patch 与副作用 key 白名单 |
| PROJECT-PLATFORM-S08-M1-T09 | non-core | integration | not-required | not-required | No | semantic-key diff、compatibility、publish、rollback 与模板链路 |
| PROJECT-PLATFORM-S08-M1-T10 | non-core | integration | not-required | not-required | No | 六类显式 preset 的稳定 hash、模板输入与本地状态流保留 |
| PROJECT-PLATFORM-S08-M1-T11 | core-system | system-real-isolated | not-required | isolated | No | 空库/升级/重复迁移、草稿/发布、跨空间、幂等、不可变、未知 guard 与 S09 隔离 |
| PROJECT-PLATFORM-S08-M1-T12 | non-core | static | not-required | not-required | No | 目标/当前架构、模块/对象/事件/技术/兼容矩阵与 checkpoint |

## Completed Items

- 审计 S06 完整配置快照、S07 WorkItem 绑定/命令/活动、legacy issue 状态与动作语义，确认状态流定义只进入既有草稿与不可变快照，M1 不启用运行时，也不创建 S09 节点权威。
- 冻结 State/Action/Transition/Guard 领域合同与 schema v2；schema v1 保持可解释但显式 `not_configured`，未来 schema 失败关闭。
- 实现声明式结构、guard、授权、required field、field patch 和受控副作用校验，所有 diagnostics 稳定排序，不执行任意脚本或静默修复图。
- 状态流变化接入 semantic-key canonicalizer/diff/compatibility、发布、rollback 与模板；六个研发预置来自显式确定性 catalog，自定义类型不猜默认流程，live 配置刷新保留本地状态流。
- V091 建立单一 current state、持久状态命令回执和不可变 workflow history，完成 workspace/space/WorkItem/version/hash 复合边界、唯一性、索引、清理闭包与 S09 私表隔离。
- 增加单元、API、服务和 Testcontainers PostgreSQL 自动化，覆盖 schema、非法图、未知 guard/副作用、草稿保留、发布兼容、跨空间、幂等、不可变和多历史基线升级。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M1-T01 | 复用端口、风险与禁止依赖可定位 | 本报告审计结论；current/target architecture S08-M1 边界 | planning/document/diff review | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T02 | key、分类、版本和错误无歧义 | `WorkItemStateFlowModels` 与两个受控 registry | `WorkItemStateFlowDefinitionTests` PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T03 | snapshot/hash 覆盖 flow，旧/未来 schema 明确 | schema v2 canonicalizer、`PublishedSnapshotAdapter` capability | canonicalizer/adapter/validator tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T04 | 复合边界、唯一性、索引、清理和不可变完整 | V091 三表及保护触发器；owner manifest | isolated Flyway/invariant tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T05 | 唯一草稿权威，无 definition 双写 | draft save + `preserveDraftOnlyConfiguration`；无 definition table | real API draft/live mutation test PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T06 | 非法图稳定拒绝且顺序无关 | `WorkItemStateFlowValidator` 图闭包与预算 | invalid graph diagnostics tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T07 | 只接受声明式白名单并保护 hidden | guard registry、typed operand 与 hidden field 检查 | unknown operator/type/hidden tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T08 | 授权/必填/patch/副作用可版本化且无任意代码 | action definition validator + side-effect registry | unknown role/field/side-effect tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T09 | diff/兼容/发布/rollback 不绕过影响等级 | semantic keyed diff + state-flow compatibility analyzer | compatibility/publication tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T10 | preset 重放确定且不覆盖本地修改 | `WorkItemStateFlowPresetCatalog`、assembler/template 接入 | six-preset hash + template/draft tests PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T11 | 迁移、草稿、发布、跨空间和隔离负例完整 | focused unit/API/service/schema suites | PostgreSQL 16 Testcontainers matrix PASS | Not required | Done |
| PROJECT-PLATFORM-S08-M1-T12 | 文档只声明定义/schema 底座 | current/target/module/object/event/technology/compatibility docs | architecture inventory/contracts + checkpoint | Not required | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/{application,domain,runtime}/**WorkItemStateFlow*`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemConfiguration*`
- `server/src/main/java/com/colla/platform/modules/project/runtime/PublishedSnapshotAdapter.java`
- `server/src/main/resources/db/migration/V091__create_work_item_state_flow_foundation.sql`
- `server/src/test/java/com/colla/platform/modules/project/{api,application,infrastructure,runtime}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture,platform-module-contracts,platform-object-model,event-side-effect-matrix,project-work-item-configuration-compatibility-matrix,technology-selection}.md`

## Validation

- Backend tests: compile/test-compile，以及 state-flow definition/validator/canonicalizer/diff/compatibility/adapter focused suite，PASS。
- Real draft/publish/template integration: Spring Boot + PostgreSQL 16 Testcontainers，PASS。
- Migration/invariant matrix: V001/V061/V078/V085/V090 -> V091、空库、重复 migrate、复合隔离、命令回执与 history 不可变，PASS。
- Architecture: inventory `modules=15, java=374, backendImports=248, frontendImports=64, crossOwnerSql=93`；contracts `activeTables=123, exceptions=93, contractFiles=23`，PASS。
- Frontend build: Not required；M1 未修改 `web/**`，状态配置器和成员执行 UI 属于 M4。
- Browser smoke: Not required；M1 只交付配置定义、服务端校验和数据库 schema，没有用户可见界面或激活的状态执行 API。
- Local quality gate: light checkpoint PASS（`.local-reports/quality-gate-20260726T131938.md`）；final workbench gate 继续记录完整收口证据。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S08-M2 | current-state Repository、绑定快照 runtime adapter、动作决策/执行、history/activity/audit/outbox/API 尚未激活 | 不阻断 M1；不得把 V091 表称为已运行状态引擎 | M2 |
| PROJECT-PLATFORM-S08-M3 | return/reopen/terminate/restore、存量 backfill 与版本升级映射尚未实现 | 旧无状态流实例保持 capability missing | M3 |
| PROJECT-PLATFORM-S08-M4 | 状态配置器、成员执行 UI、真实浏览器和 route-final 尚未实现 | M1 无浏览器验收，不完成 S08 Stage | M4 |
| PROJECT-PLATFORM-S08-M4-T11 | node instance/token、并行、汇聚和会签不在 S08-M1 范围 | V091 和 M1 定义不得被解释为节点流 | S09 准入复核 |

## Next Steps

- 从 PROJECT-PLATFORM-S08-M2-T01 复核本里程碑证据，再实现只解释 WorkItem 绑定 snapshot 的状态 runtime adapter 与 current-state Repository。
- 保持 S09 Planned；不得在 S08 私表中预存 node token、并行或会签语义。
