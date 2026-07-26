# PROJECT-PLATFORM-S09-M1 Execution Report

## Scope

PROJECT-PLATFORM-S09-M1-T01 到 PROJECT-PLATFORM-S09-M1-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M1-T01 | non-core | static | not-required | not-required | No | S06-S08 snapshot、WorkItem、command/event SPI、状态私表及现有审批语义审计 |
| PROJECT-PLATFORM-S09-M1-T02 | non-core | unit | not-required | not-required | No | Node/Edge/Stage/Branch/Join 永久 key、枚举与预算合同 |
| PROJECT-PLATFORM-S09-M1-T03 | non-core | integration | not-required | not-required | No | schema v1/v2/v3、canonical hash、无节点流能力和未来 schema 失败关闭 |
| PROJECT-PLATFORM-S09-M1-T04 | core-system | system-real-isolated | not-required | isolated | No | V001-V093、复合 FK、唯一性、索引、清理闭包与不可变保护 |
| PROJECT-PLATFORM-S09-M1-T05 | non-core | integration | not-required | not-required | No | 唯一草稿保存、validate/publish、乐观版本与零 runtime definition 双写 |
| PROJECT-PLATFORM-S09-M1-T06 | non-core | unit | not-required | not-required | No | 单入口、出口、可达性、悬空、非法环与死路 diagnostics |
| PROJECT-PLATFORM-S09-M1-T07 | non-core | unit | not-required | not-required | No | 节点类型、auto/single/any/multi 策略和未知扩展失败关闭 |
| PROJECT-PLATFORM-S09-M1-T08 | non-core | unit | not-required | not-required | No | 声明式条件、边优先级、exclusive/parallel 与 all/any/quorum 语义 |
| PROJECT-PLATFORM-S09-M1-T09 | non-core | integration | not-required | not-required | No | semantic-key diff、compatibility、publish 与 rollback 既有链路 |
| PROJECT-PLATFORM-S09-M1-T10 | non-core | integration | not-required | not-required | No | project/release 确定性 preset、稳定 hash 与本地草稿保留 |
| PROJECT-PLATFORM-S09-M1-T11 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 空库/升级/重复迁移、跨空间、不可变及 S08 私表隔离 |
| PROJECT-PLATFORM-S09-M1-T12 | non-core | static | not-required | not-required | No | 目标/当前架构、模块/对象合同、checkpoint 与 M2 边界 |

## Completed Items

- 审计 S06-S08 的完整配置快照、WorkItem 绑定、共享 command/event SPI 和状态流私表，确认 S09 只复用公开边界，不把 S08 current-state 表扩充为 token/task/vote/join 权威。
- 冻结 Node、Edge、Stage、Branch、Join 领域合同和 schema v3；状态流与节点流互斥，旧 schema 可解释，未来 schema、隐藏字段及未知扩展失败关闭。
- 实现节点图预算、稳定 key、单入口、受控出口、可达性、悬空边、非法环、死路、条件与汇聚闭包校验；diagnostics 确定排序，不执行任意脚本或静默修复。
- 节点流接入 canonicalizer、semantic diff、兼容分析、唯一草稿、发布/rollback 与模板；project/release 使用显式确定性 preset，其他类型不猜测运行语义，已有本地 workflow 定义原样保留。
- V093 建立独立 instance、token、task、vote、join、command receipt 和不可变 history 表，具备 workspace/space/workItem/version 复合边界、唯一性、索引、清理例外和不可变触发器，但 M1 不激活运行时。
- 增加单元、Spring API 和 Testcontainers PostgreSQL 自动化，覆盖节点图、schema、草稿/发布、未来版本、跨空间、不可变、S08 隔离及无 runtime 副作用。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M1-T01 | 复用端口、调用方、存量与禁止依赖可定位 | current/target/module/object architecture 的 S09-M1 边界与本报告审计结论 | `pnpm architecture:boundaries` PASS | Not required：无用户界面 | Done |
| PROJECT-PLATFORM-S09-M1-T02 | 永久 key、类型、阶段、分支与汇聚合同无歧义 | `WorkItemNodeFlowModels` | `WorkItemNodeFlowDefinitionTests` PASS | Not required：领域合同 | Done |
| PROJECT-PLATFORM-S09-M1-T03 | snapshot/hash 覆盖节点图且版本行为明确 | schema v3、canonicalizer、`PublishedSnapshotAdapter` capability | canonicalizer/adapter/assembler focused tests PASS | Not required：配置合同 | Done |
| PROJECT-PLATFORM-S09-M1-T04 | 复合边界、唯一性、FK、索引、清理和不可变完整 | V093 七张独立表及 owner manifest | PostgreSQL 16 Testcontainers migration/invariant suite PASS | Not required：隔离系统证据 | Done |
| PROJECT-PLATFORM-S09-M1-T05 | 唯一草稿权威且不建立 definition/runtime 双写 | draft workflow preservation、既有 Repository/DTO 和 publication service | node-flow draft/validate/publish Spring API test PASS | Not required：M1 未激活 UI | Done |
| PROJECT-PLATFORM-S09-M1-T06 | 非法图稳定拒绝且顺序无关 | `WorkItemNodeFlowValidator` 图闭包与预算 | invalid cycle/reference/join tests PASS | Not required：服务端校验 | Done |
| PROJECT-PLATFORM-S09-M1-T07 | 类型与处理策略可发现，未知扩展失败关闭 | `WorkItemNodeTypeRegistry` | unknown kind/strategy/operator tests PASS | Not required：注册表 | Done |
| PROJECT-PLATFORM-S09-M1-T08 | 分支、条件、优先级与汇聚语义稳定 | declarative condition/branch/join validator 与 canonical order | preset validation + canonical-order tests PASS | Not required：定义层 | Done |
| PROJECT-PLATFORM-S09-M1-T09 | 图变化分级稳定且发布不可绕过 | diff keyed arrays、node-flow compatibility analyzer、既有发布链路 | compatibility + API publication tests PASS | Not required：API 集成 | Done |
| PROJECT-PLATFORM-S09-M1-T10 | preset 重放确定且不覆盖本地修改 | `WorkItemNodeFlowPresetCatalog`、assembler/template/draft preservation | deterministic hash + assembler tests PASS | Not required：模板输入 | Done |
| PROJECT-PLATFORM-S09-M1-T11 | 迁移、校验、草稿、发布、隔离负例完整 | focused unit/API/schema suites | 14 focused unit、9 Spring API、5 PostgreSQL migration tests PASS | Not required：隔离系统证据 | Done |
| PROJECT-PLATFORM-S09-M1-T12 | 文档只声明定义底座且 M2 输入清晰 | current/target/module/object docs 和本报告 | architecture contracts/boundaries + checkpoint PASS | Not required：文档收口 | Done |

## Code Changes

- `server/src/main/java/com/colla/platform/modules/project/{application,domain}/**WorkItemNodeFlow*`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemConfiguration*`
- `server/src/main/java/com/colla/platform/modules/project/runtime/PublishedSnapshotAdapter.java`
- `server/src/main/resources/db/migration/V093__create_node_flow_foundation.sql`
- `server/src/test/java/com/colla/platform/modules/project/{api,application,infrastructure,runtime}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture,platform-module-contracts,platform-object-model}.md`

## Validation

- Backend tests: node-flow definition、validator、canonicalizer、diff、compatibility、assembler 与 adapter focused suite 14 项，Spring API 9 项，PostgreSQL migration/invariant 5 项，均 PASS。
- Real draft/publish integration: Spring Boot + PostgreSQL 16 Testcontainers，包含 node-flow save/validate/publish、零 runtime 实例和原有配置回归，9 项 PASS。
- Migration/invariant matrix: V001-V093、重复 migrate、复合隔离、task/vote/receipt/history 不可变和 S08 current-state 私表隔离，5 项 PASS。
- Architecture contracts: `modules=15; activeTables=132; exceptions=93; contractFiles=24`，PASS。
- Architecture boundaries: `backendPrivate=140; sharedReverse=0; frontendImports=64; crossOwnerReads=93`，PASS。
- Frontend build: Not required；M1 未修改 `web/**`，节点设计器与成员执行 UI 属于 M5。
- Browser smoke: Not required；M1 只交付节点图定义、唯一配置发布合同和独立数据库底座，没有用户可见节点运行时。
- Local quality gate: light checkpoint PASS（`.local-reports/quality-gate-20260726T174010.md`）；finish 使用 stage 档位与真实隔离系统证据再次收口。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S09-M2 | runtime adapter、instance/token/task/vote/join Repository、推进算法、历史和 command/event SPI 尚未激活 | 不阻断 M1；V093 不能被解释为已运行节点引擎 | M2 |
| PROJECT-PLATFORM-S09-M3 | 自动节点、条件分支、超时/升级和运行中版本兼容尚未实现 | 不阻断定义底座 | M3 |
| PROJECT-PLATFORM-S09-M4 | 模板治理、升级/迁移、批量运维、监控告警和恢复工具尚未实现 | 不阻断定义底座 | M4 |
| PROJECT-PLATFORM-S09-M5 | 可视化设计器、成员执行 UI、真实浏览器综合验收和尚未执行 | M1 无浏览器验收，不完成 S09 Stage | M5 |

## Next Steps

- 从 PROJECT-PLATFORM-S09-M2-T01 复核本里程碑证据，再实现只解释 WorkItem 绑定不可变 snapshot 的节点 runtime adapter 与独立 Repository。
- 保持 S08 私表只归 S08 owner；节点 command/event 必须经共享 SPI，且 M2 前不对外声称 runtime capability。
