# PROJECT-PLATFORM-S11-M4 Execution Report

## Scope
PROJECT-PLATFORM-S11-M4-T01 到 PROJECT-PLATFORM-S11-M4-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M4-T01 | non-core | static | not-required | not-required | No | M1-M3 trace review |
| PROJECT-PLATFORM-S11-M4-T02 | non-core | unit | not-required | not-required | No | safe user explanation |
| PROJECT-PLATFORM-S11-M4-T03 | core-system | system-real-isolated | not-required | isolated | No | governance/content intersection |
| PROJECT-PLATFORM-S11-M4-T04 | core-system | system-real-isolated | not-required | isolated | No | request/expiry validation |
| PROJECT-PLATFORM-S11-M4-T05 | core-system | system-real-isolated | not-required | isolated | No | owner mutation guard |
| PROJECT-PLATFORM-S11-M4-T06 | non-core | unit | not-required | not-required | No | bounded policy preview |
| PROJECT-PLATFORM-S11-M4-T07 | core-system | system-real-isolated | not-required | isolated | No | consistency scan |
| PROJECT-PLATFORM-S11-M4-T08 | core-system | system-real-isolated | not-required | isolated | No | legacy classification |
| PROJECT-PLATFORM-S11-M4-T09 | non-core | unit | not-required | not-required | No | low-cardinality metrics |
| PROJECT-PLATFORM-S11-M4-T10 | core-system | system-real-isolated | not-required | isolated | No | owner/admin governance boundary |
| PROJECT-PLATFORM-S11-M4-T11 | core-system | system-real-isolated | not-required | isolated | No | negative governance regression |
| PROJECT-PLATFORM-S11-M4-T12 | non-core | static | not-required | not-required | No | runbook/contract review |

## Completed Items
- 用户 explanation 只返回 decision 的安全来源；治理 trace 同时要求治理能力与内容可见，失败统一为隐藏外形。
- permission request adapter 校验 request id、原因、重复提交和最长 365 天临时授权，不把提交直接当成审批或授予。
- 角色 mutation 校验最后 owner、危险确认、原因与到期；策略预览校验 expected version、200 项硬限并折叠隐藏候选数量。
- 一致性扫描识别失效 subject、角色漂移和过期授权；legacy 分类对扩权、未知映射和人工复核失败关闭。
- owner/admin 治理 API 复用空间 manager 边界；enterprise admin 仅有企业治理身份时不能借此读取空间内容。
- 提供低基数 decision/finding 指标合同、恢复运行手册与自动化负向矩阵。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M4-T01 | 36 项输入可追溯 | M1-M3 reports + bound decision review | work context | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T02 | 安全来源且不泄露 | `explainForUser` | governance tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T03 | 治理/内容权限交集 | `explainForGovernance` | hidden trace negative test | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T04 | 申请不自动扩权且到期有界 | `validatePermissionRequest` + common workflow adapter contract | duplicate/expiry tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T05 | 最后 owner 与危险操作关闭 | `validateRoleMutation` | owner/confirmation tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T06 | 预览有界且冲突稳定 | `previewPolicyChange` | version/hidden-count tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T07 | 扫描不改业务事实 | `scan` + V101 private authorities | deterministic scan test | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T08 | legacy 不静默扩权 | `classifyLegacy` | expansion/review tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T09 | 指标低基数且无正文 | `GovernanceMetrics` | service compilation/tests | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T10 | 分层治理无 enterprise 旁路 | manager-gated governance controller | Spring/backend gate | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T11 | 负向矩阵通过 | governance/fine-grained suites | targeted + real DB finish | 不需要 | Done |
| PROJECT-PLATFORM-S11-M4-T12 | runbook/合同同步 | governance runbook + architecture 27.7 | checkpoint/architecture gate | 不需要 | Done |

## Code Changes
- `WorkItemPermissionGovernanceService`
- `WorkItemPermissionGovernanceController`
- `WorkItemPermissionGovernanceTests`
- `docs/05-runbooks/work-item-permission-governance.md`

## Validation
- Backend tests: governance/fine-grained targeted suites PASS；finish 使用 WorkItem PostgreSQL integration 作为真实系统回归。
- Frontend build: Not required；M4 不交付 Web UI。
- Local quality gate: checkpoint `.local-reports/quality-gate-20260727T061000.md` PASS；finish fresh report `.local-reports/quality-gate-20260727T061156.md`。
- Browser smoke: Not required；M5 承接 UI 与真实隔离浏览器。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- M5 只消费服务端 capability/explanation/preview/governance DTO，交付配置、成员和治理 UI。
- M5 必须以真实隔离浏览器验证六身份、自定义角色、hidden 零披露、离线输入保留与窄屏，不能用 route mock 冒充系统证据。
