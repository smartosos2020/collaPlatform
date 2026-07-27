# PROJECT-PLATFORM-S11-M2 Execution Report

## Scope
PROJECT-PLATFORM-S11-M2-T01 到 PROJECT-PLATFORM-S11-M2-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M2-T01 | non-core | static | not-required | not-required | No | M1 report/snapshot/schema trace |
| PROJECT-PLATFORM-S11-M2-T02 | non-core | unit | not-required | not-required | No | bound snapshot adapter and normalized subject |
| PROJECT-PLATFORM-S11-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | single/batch decision equivalence |
| PROJECT-PLATFORM-S11-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | WorkItem create/read/update/archive/restore |
| PROJECT-PLATFORM-S11-M2-T05 | core-system | system-real-isolated | not-required | isolated | No | participant/comment/attachment/activity |
| PROJECT-PLATFORM-S11-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | S08 presentation/execute/recovery |
| PROJECT-PLATFORM-S11-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | S09 task/action/recovery/upgrade |
| PROJECT-PLATFORM-S11-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | S10 dual-endpoint relation decision |
| PROJECT-PLATFORM-S11-M2-T09 | non-core | unit | not-required | not-required | No | cache-free versioned decision/event contract |
| PROJECT-PLATFORM-S11-M2-T10 | non-core | unit | not-required | not-required | No | server availableActions/minimal errors |
| PROJECT-PLATFORM-S11-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | real PostgreSQL six-identity regression |
| PROJECT-PLATFORM-S11-M2-T12 | core-system | system-real-isolated | not-required | isolated | No | bounded 200 decision/list budgets |

## Completed Items
- 新增只解释 WorkItem 绑定 snapshot 的 `WorkItemPermissionRuntimeAdapter`；v5 使用分层 policy，v1-v4 使用冻结 legacy space-role ceiling，不回读 live policy。
- 新增统一单项/批量 `WorkItemPermissionDecisionService`，固定 deny 优先、稳定 policy source、policy version/hash、subject version、200 项硬限。
- WorkItem create/list/get/update/archive/restore、participant/comment/attachment/activity、状态流和节点流入口接入同一 decision；`availableActions` 由服务端决策产生。
- S10 relation create 对 source `relate` 与 target `accept_link` 分别决策，查询对双端分别执行 `view`，隐藏任一端即失败关闭。
- 当前实现不缓存授权结果，因而策略/subject/version 变化不会留下陈旧 capability；最小 permission changed event 仍只用于失效和重读。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M2-T01 | 12 项输入可追溯 | M1 report + v5/V101 review | work-cycle context | 不需要：复核 | Done |
| PROJECT-PLATFORM-S11-M2-T02 | 只解释绑定版本 | runtime adapter + normalized context | legacy/v5 unit tests | 不需要：服务合同 | Done |
| PROJECT-PLATFORM-S11-M2-T03 | 单项批量一致且 deny 稳定 | decision service | exact equality/deny tests | 不需要：后端决策 | Done |
| PROJECT-PLATFORM-S11-M2-T04 | 核心 CRUD 同一授权 | WorkItemService permission gates | WorkItemService integration | 不需要：M2 无 UI | Done |
| PROJECT-PLATFORM-S11-M2-T05 | 子资源不扩大对象权限 | participant/comment/attachment/activity gates | real service regression | 不需要：后端接管 | Done |
| PROJECT-PLATFORM-S11-M2-T06 | S08 projection/execute 一致 | workflow view/action/manage gates | workflow integration regression | 不需要：后端接管 | Done |
| PROJECT-PLATFORM-S11-M2-T07 | S09 task/recovery 一致 | node view/transition/manage gates | node runtime regression | 不需要：后端接管 | Done |
| PROJECT-PLATFORM-S11-M2-T08 | 双端最小披露 | relation source/target decisions | relation service regression | 不需要：后端接管 | Done |
| PROJECT-PLATFORM-S11-M2-T09 | 收权无陈旧缓存 | cache-free versioned decision | subject/config version tests | 不需要：无缓存 UI | Done |
| PROJECT-PLATFORM-S11-M2-T10 | UI 不猜 action | server-derived availableActions | WorkItem response assertions | 不需要：M5 承接 UI | Done |
| PROJECT-PLATFORM-S11-M2-T11 | 六身份无 enterprise bypass | membership ceiling + policy layer | six-identity real DB suite | 不需要：M2 系统证据 | Done |
| PROJECT-PLATFORM-S11-M2-T12 | 上限可复现 | batch 200 hard limit/config reuse | batch and projection budgets | 不需要：后端预算 | Done |

## Code Changes
- `WorkItemPermissionRuntimeAdapter`
- `WorkItemPermissionDecisionService`
- WorkItem and relation authorization integration
- `WorkItemPermissionDecisionTests`

## Validation
- Backend tests: permission decision unit suite and full WorkItemService integration PASS。
- Frontend build: Not required；M2 未修改 Web。
- Local quality gate: stage checkpoint `.local-reports/quality-gate-20260727T055339.md`；finish fresh report 由工作台收口生成。
- Browser smoke: Not required；M2 只接管服务端授权，成员 UI 在 M5。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- M3 在同一 decision 上组合 field/node/relation/data scope；不得建立第二套授权服务或扩大对象级上限。
