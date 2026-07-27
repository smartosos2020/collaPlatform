# PROJECT-PLATFORM-S13-M1 Execution Report

## Scope
PROJECT-PLATFORM-S13-M1-T01 到 PROJECT-PLATFORM-S13-M1-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M1-T01 | non-core | static | not-required | not-required | No | 本地代码、路线、架构、API、表、索引、owner 与调用方全文审计 |
| PROJECT-PLATFORM-S13-M1-T02 | non-core | unit | not-required | not-required | No | query contract、schema、类型、上限与错误单元测试 |
| PROJECT-PLATFORM-S13-M1-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V106 fresh/repeat migration |
| PROJECT-PLATFORM-S13-M1-T04 | non-core | unit | not-required | not-required | No | AST 注册、规范化、hash、复杂度与注入拒绝测试 |
| PROJECT-PLATFORM-S13-M1-T05 | core-system | system-real-isolated | not-required | isolated | No | published snapshot 动态字段 capability 与无回退边界 |
| PROJECT-PLATFORM-S13-M1-T06 | core-system | system-real-isolated | not-required | isolated | No | participant/state/node/relation/hierarchy 公共投影查询 |
| PROJECT-PLATFORM-S13-M1-T07 | non-core | unit | not-required | not-required | No | 多列排序、null、分组聚合与 HMAC cursor 单元测试 |
| PROJECT-PLATFORM-S13-M1-T08 | core-system | system-real-isolated | not-required | isolated | No | S11 decision 在 item/group/count/cursor 前移除 hidden |
| PROJECT-PLATFORM-S13-M1-T09 | non-core | integration | not-required | not-required | No | execute/explain/dry-run API 编译与低基数指标门禁 |
| PROJECT-PLATFORM-S13-M1-T10 | core-system | system-real-isolated | not-required | isolated | No | 六身份、跨身份 cursor、收权与 hidden 零披露矩阵 |
| PROJECT-PLATFORM-S13-M1-T11 | core-system | system-real-isolated | not-required | isolated | No | 200 candidate/4 sort/24 node 上界与 PostgreSQL 索引基座 |
| PROJECT-PLATFORM-S13-M1-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/event/owner 同步 |

## Completed Items
- 审计规范 WorkItem、类型化 field projection、search、participant、状态/节点流、关系/层级、S11 decision 与既有分页，冻结禁止依赖和 200 candidate 本地预算。
- 冻结 schema v1 查询合同、注册字段/操作符、稳定语义 hash、硬复杂度限制和身份绑定签名 cursor。
- V106 建立 project owner 的查询定义、不可变回执与可重建投影统计，空间清理顺序和 owner manifest 同步。
- 服务端先经 S11 contextual decision 与字段可读投影，再执行 filter/sort/group/aggregate/count/cursor；hidden 对象不改变任何输出。
- 交付 execute/explain/dry-run API、低基数指标和六身份自动化矩阵；表格、树和保存视图保持未实现。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M1-T01 | API、表、索引、owner、调用方、预算与禁止依赖可定位 | roadmap/target/current + WorkItem/field/search/relation/hierarchy/permission 审计 | `rg`、全文读取与 architecture gate | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S13-M1-T02 | schema/key/type/null/time/error/limit 固定 | `WorkItemQueryModels` | canonicalizer contract tests | 不需要：公共合同 | Done |
| PROJECT-PLATFORM-S13-M1-T03 | 复合边界、唯一性、FK、索引、清理和 owner 完整 | V106 + cleanup + table owner manifest | PostgreSQL 16 fresh 106/repeat 0 PASS | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S13-M1-T04 | 未知节点、过深树、任意字段和超预算失败关闭 | `WorkItemQueryCanonicalizer` | hash equivalence/injection/25-node rejection PASS | 不需要：纯 AST | Done |
| PROJECT-PLATFORM-S13-M1-T05 | 动态字段只使用 snapshot capability | `WorkItemService.requireQueryCapability` + query prepare | backend compile + capability path tests | 不需要：服务端能力边界 | Done |
| PROJECT-PLATFORM-S13-M1-T06 | 受控条件只经 owner 投影组合 | `WorkItemQueryContextProvider`/JDBC implementation | compilation + PostgreSQL schema evidence | 不需要：内部公共端口 | Done |
| PROJECT-PLATFORM-S13-M1-T07 | 稳定排序/分组/聚合/cursor 可解释 | query comparator/group + HMAC codec | cursor scope/tamper/hash tests PASS | 不需要：服务端查询语义 | Done |
| PROJECT-PLATFORM-S13-M1-T08 | hidden 不进入 item/group/count/cursor | allowlist before detail/filter/group | `WorkItemQueryServiceTests` hidden matrix PASS | 不需要：真实系统证据覆盖 | Done |
| PROJECT-PLATFORM-S13-M1-T09 | 统一 API、safe plan 与低基数指标 | `UserWorkItemQueryController` + Micrometer | backend compile + architecture gate | 不需要：M1 无 Web 流 | Done |
| PROJECT-PLATFORM-S13-M1-T10 | 六身份无 enterprise/non-member 旁路 | six-identity allowlist matrix + scoped cursor | service/cursor tests PASS | 不需要：M1 只交付服务端查询基座 | Done |
| PROJECT-PLATFORM-S13-M1-T11 | SQL/端口/内存上界和索引可复现 | candidate 200、AST 24、sort 4、V106 indexes | isolated PostgreSQL migration/index assertions PASS | 不需要：本地预算 | Done |
| PROJECT-PLATFORM-S13-M1-T12 | 文档只声明 DSL，M2-M4 不提前 | target/current/module/event/owner/roadmap/report | checkpoint light gate PASS | 不需要：文档同步 | Done |

## Code Changes
- 查询 domain/application/API、公共上下文端口与 JDBC 投影实现。
- V106 query definition/receipt/projection stats 与 project space cleanup。
- canonicalizer/cursor/service 六身份单元测试及 PostgreSQL 16 migration 集成测试。
- target/current/module/event/table-owner/current-roadmap 与本报告同步。

## Validation
- Backend tests: `WorkItemQueryCanonicalizerTests`、`WorkItemQueryServiceTests` PASS；`WorkItemQueryFoundationIntegrationTests` 在 PostgreSQL 16 执行 V001-V106 fresh/repeat PASS。
- Frontend build: M1 未修改 Web；finish stage gate 仍按影响域执行。
- Local quality gate: `.local-reports/quality-gate-20260727T112926.md` light checkpoint PASS；finish 生成 fresh stage 报告。
- Browser smoke: 不需要；M1 仅交付服务端查询 DSL/API，真实隔离系统证据覆盖数据库、权限和六身份服务矩阵。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S13-M2-T01 独立复核 M1 合同与 V106；实现表格/紧凑列表、列投影、批量与导出，不提前实现树或保存视图。
