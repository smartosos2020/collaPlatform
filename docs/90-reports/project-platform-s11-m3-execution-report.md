# PROJECT-PLATFORM-S11-M3 Execution Report

## Scope
PROJECT-PLATFORM-S11-M3-T01 到 PROJECT-PLATFORM-S11-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M3-T01 | non-core | static | not-required | not-required | No | M2 trace review |
| PROJECT-PLATFORM-S11-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | field projection/patch decision |
| PROJECT-PLATFORM-S11-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | detail/create/node form zero disclosure |
| PROJECT-PLATFORM-S11-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | node qualifier decision |
| PROJECT-PLATFORM-S11-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | relation qualifier/dual endpoint |
| PROJECT-PLATFORM-S11-M3-T06 | non-core | unit | not-required | not-required | No | creator/role resolution |
| PROJECT-PLATFORM-S11-M3-T07 | non-core | unit | not-required | not-required | No | declarative data-scope matrix |
| PROJECT-PLATFORM-S11-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | list/detail/resolver scope |
| PROJECT-PLATFORM-S11-M3-T09 | non-core | unit | not-required | not-required | No | safe decision sources |
| PROJECT-PLATFORM-S11-M3-T10 | non-core | unit | not-required | not-required | No | bounded scoped batch contract |
| PROJECT-PLATFORM-S11-M3-T11 | core-system | system-real-isolated | not-required | isolated | No | field/scope negative regression |
| PROJECT-PLATFORM-S11-M3-T12 | core-system | system-real-isolated | not-required | isolated | No | policy/subject/batch hard limits |

## Completed Items
- 在 M2 唯一 evaluator 上增加 field/node/relation qualifier，空限定为对象级规则，显式限定只能收紧对象访问。
- 实现 all、created-by-subject、participating、work-item-role、field-match、explicit-set 六类声明式 data scope；operator 白名单固定为 equals/not-equals/in/contains。
- WorkItem 对象 decision 使用创建人、当前字段值和事项身份上下文；详情投影移除拒绝字段，patch 对不可写字段返回统一拒绝且不回显 key/value。
- 关系继续逐端点决策；节点和关系 key 通过同一 scoped evaluation contract 供运行入口复用。
- 单项/批量上限仍为 200，policy/selector/value 预算沿用 v5 validator，不引入动态 SQL 或任意表达式。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M3-T01 | M2 输入可追溯 | M2 report/runtime review | work context | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T02 | hidden/read/write 组合一致 | scoped evaluator + projection/patch gates | fine-grained unit + WorkItem DB | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T03 | hidden 零披露 | field removal/generic rejection | projection regression | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T04 | node action 可限定 | nodeKey qualifier | qualifier tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T05 | relation 双端不泄漏 | relationKey + endpoint gates | relation DB regression | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T06 | 事项角色可解释 | creator/role context | role scope tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T07 | 六类 scope 白名单 | EvaluationContext/scope matcher | creator/field/operator tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T08 | 入口使用对象 scope | WorkItem requirePermission | service integration | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T09 | 历史来源最小安全 | safe policy keys only | decision tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T10 | 批量合同有界 | existing 200 decision bound | batch tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T11 | 组合负向通过 | fine-grained suite | targeted + real DB | 不需要 | Done |
| PROJECT-PLATFORM-S11-M3-T12 | 预算可复现 | validator/runtime hard limits | checkpoint/finish | 不需要 | Done |

## Code Changes
- scoped `WorkItemPermissionRuntimeAdapter.EvaluationContext`
- field/data-scope integration in `WorkItemService`
- `WorkItemFineGrainedPermissionTests`

## Validation
- Backend tests: fine-grained/decision/definition targeted suites PASS；WorkItem real PostgreSQL regression by finish。
- Frontend build: Not required；M3 未修改 Web。
- Local quality gate: stage checkpoint `.local-reports/quality-gate-20260727T055957.md`；finish fresh report 由工作台收口生成。
- Browser smoke: Not required；M5 承接真实 UI。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- M4 在同一 decision/evidence 上交付安全 explanation、申请、角色治理、扫描与 legacy 承接。
