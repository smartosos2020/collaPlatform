# PROJECT-PLATFORM-S13-M3 Execution Report

## Scope
PROJECT-PLATFORM-S13-M3-T01 到 PROJECT-PLATFORM-S13-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M3-T01 | non-core | static | not-required | not-required | No | M1-M2 查询、列、批量、导出、权限与报告逐项复核 |
| PROJECT-PLATFORM-S13-M3-T02 | non-core | unit | not-required | not-required | No | tree/node/path/aggregate/cursor 合同、上限与失败语义 |
| PROJECT-PLATFORM-S13-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V108 fresh/repeat migration |
| PROJECT-PLATFORM-S13-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | S10 identity/depth 公共投影上的 root/child/path/lazy page |
| PROJECT-PLATFORM-S13-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | hidden parent 折叠、visible count/path/aggregate 零披露 |
| PROJECT-PLATFORM-S13-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | matched/context、稳定排序和无全树扫描预算 |
| PROJECT-PLATFORM-S13-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | cycle/self-edge 失败关闭、孤儿、漂移和可丢弃状态 |
| PROJECT-PLATFORM-S13-M3-T08 | core-user | e2e-real-isolated | real | isolated | No | 懒展开、键盘/选择/深链、长名称与三个视口 |
| PROJECT-PLATFORM-S13-M3-T09 | core-user | e2e-real-isolated | real | isolated | No | tree/table/list 切换选择保持、批量与导出复用 |
| PROJECT-PLATFORM-S13-M3-T10 | core-user | e2e-real-isolated | real | isolated | No | 六身份、三层树、循环、收权、离线与恢复 |
| PROJECT-PLATFORM-S13-M3-T11 | core-system | system-real-isolated | not-required | isolated | No | 200 node/32 depth/50 page/64 expansion 与索引预算 |
| PROJECT-PLATFORM-S13-M3-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/event/owner 同步 |

## Completed Items
- 复核 M1-M2 权威，新增 identity/depth-only S10 hierarchy 公共投影，树服务不依赖 relation/hierarchy 私表。
- 冻结 schema v1 tree/node/path/aggregate 合同与身份绑定签名 cursor；落实节点、深度、展开页和展开状态硬限。
- V108 建立用户树偏好和可重建低基数统计，保存 identity/version 而不复制 parent/edge/title/result/permission。
- 根、子、祖先、matched/context、排序和聚合全部从当前允许集合生成；隐藏中间节点无标记折叠，循环失败关闭。
- Web 交付懒展开、键盘/勾选/深链、切换上下文保持和响应式；真实隔离六身份三层树验收通过。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M3-T01 | 24 项可追溯且不复制 parent/edge | M1/M2 reports、V106/V107、query/view/hierarchy 审计 | checkpoint/architecture contracts | 不需要：事实复核 | Done |
| PROJECT-PLATFORM-S13-M3-T02 | identity/depth/sort/truncate/orphan/cycle/permission 固定 | `WorkItemTreeViewModels` + signed cursor reuse | tree service unit tests PASS | real API serialization covered | Done |
| PROJECT-PLATFORM-S13-M3-T03 | 只存用户状态/版本与可重建统计 | V108 + JDBC preference repository + cleanup/owner | PostgreSQL fresh 108/repeat 0 PASS | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S13-M3-T04 | 只通过 S10 公共端口根/子/path/懒加载 | `WorkItemHierarchyProjectionProvider` + tree service | compile + three-level path E2E | real isolated root/expand/path PASS | Done |
| PROJECT-PLATFORM-S13-M3-T05 | hidden 不进入存在、子数、断点或标题 | allowed-set intersection + nearest-visible-parent flatten | hidden-parent unit test PASS | outsider/enterprise 404 且正文无标题 PASS | Done |
| PROJECT-PLATFORM-S13-M3-T06 | matched/context 可解释且无全树扫描 | structural/matched bounded queries + visible ancestry intersection | unit + 200 candidate bound | real visible aggregate/path PASS | Done |
| PROJECT-PLATFORM-S13-M3-T07 | 异常失败关闭，恢复不改 canonical fact | cycle guards + live rebuild from canonical projection + S10 recovery boundary | self-cycle unit + canonical cycle 422 PASS | real cycle rejection PASS | Done |
| PROJECT-PLATFORM-S13-M3-T08 | 懒展开/键盘/选择/深链/长名称/窄屏 | Ant Tree + responsive CSS + REST lazy loader | web lint/build PASS | real isolated 1440/1366/820 PASS | Done |
| PROJECT-PLATFORM-S13-M3-T09 | 切换保持查询/选择，动作仍逐项授权 | shared query/selection + M2 bulk/export services | web build + service authorization | real tree-to-table selection count preserved PASS | Done |
| PROJECT-PLATFORM-S13-M3-T10 | 六身份、深树、循环、离线无泄漏或输入丢失 | isolated fixture + three-level canonical relation setup | Playwright single real flow PASS | real owner/admin/member/guest allow；outsider/enterprise hidden PASS | Done |
| PROJECT-PLATFORM-S13-M3-T11 | SQL/端口/内存/DOM 上界可复现 | 200 nodes、32 depth、50 sibling、64 expansion、V108 indexes | migration/unit/build PASS | real lazy DOM and three viewports PASS | Done |
| PROJECT-PLATFORM-S13-M3-T12 | 当前事实/owner/M4 输入清楚且不实现 S14 | target/current/module/event/owner/roadmap/report | checkpoint light gate | 不需要：文档同步 | Done |

## Code Changes
- tree domain/application/API、S10 identity-only hierarchy projection contract 与 JDBC provider。
- V108 tree preference/projection stats、JDBC preference repository、space cleanup 与 table owner。
- tree permission intersection、hidden flatten、matched/context、cycle defense、aggregate 与 signed pagination。
- Web tree API、懒展开/选择/深链/偏好、table/list/tree 上下文保持与响应式 CSS。
- tree unit/migration integration、六身份真实隔离 E2E 与架构/路线/报告同步。

## Validation
- Backend tests: `WorkItemTreeViewServiceTests` PASS；`WorkItemTreeViewFoundationIntegrationTests` 在 PostgreSQL 16 执行 V001-V108 fresh/repeat PASS。
- Frontend build: `pnpm --dir web lint` PASS；`pnpm --dir web build` PASS。
- Local quality gate: `.local-reports/quality-gate-20260727T123557.md` light checkpoint PASS；`.local-reports/quality-gate-20260727T123833.md` fresh stage finish PASS。
- Browser smoke: `pnpm workbench browser smoke-project-platform-s13-isolated --spec project-platform-s13-m3.spec.ts`，六身份真实隔离 1 PASS。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S13-M4-T01 独立复核 M1-M3；保存视图只保存版本化查询/展示配置，不复制树、结果或授权快照。
