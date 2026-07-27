# PROJECT-PLATFORM-S13-M2 Execution Report

## Scope
PROJECT-PLATFORM-S13-M2-T01 到 PROJECT-PLATFORM-S13-M2-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M2-T01 | non-core | static | not-required | not-required | No | M1 查询、权限、迁移、报告和未关闭阻断逐项复核 |
| PROJECT-PLATFORM-S13-M2-T02 | non-core | unit | not-required | not-required | No | view/column/cell/bulk/export 合同与边界单测 |
| PROJECT-PLATFORM-S13-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V107 fresh/repeat migration |
| PROJECT-PLATFORM-S13-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | hidden/read-denied cell 在响应和导出前移除 |
| PROJECT-PLATFORM-S13-M2-T05 | core-user | e2e-real-isolated | real | isolated | No | table/list、密度、列配置、稳定刷新和响应式 UI |
| PROJECT-PLATFORM-S13-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | 100 选择硬限、逐对象授权/版本/幂等与部分失败 |
| PROJECT-PLATFORM-S13-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | 导出输入重放、下载重新授权、TTL 与 CSV 防注入 |
| PROJECT-PLATFORM-S13-M2-T08 | core-user | e2e-real-isolated | real | isolated | No | 表格/列表 Web、偏好、批量反馈、导出和 1440/1366/820 |
| PROJECT-PLATFORM-S13-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | realtime/online/focus 只失效并 REST 重校准 |
| PROJECT-PLATFORM-S13-M2-T10 | core-user | e2e-real-isolated | real | isolated | No | 六身份、hidden、批量、导出收权和恢复 |
| PROJECT-PLATFORM-S13-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | 100 row/100 selection/200 candidate 本地上界与索引 |
| PROJECT-PLATFORM-S13-M2-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/event/owner 同步 |

## Completed Items
- 复核 M1 schema v1 查询、S11 decision/data scope、V106 与报告，确认视图不复制查询或授权权威。
- 冻结 table/list、column/cell、preference、bulk 和 export schema v1 合同；所有 key、格式、版本、上限和生命周期注册化。
- V107 建立 project owner 的用户偏好、稳定命令回执和受控导出任务，补齐空间清理顺序与 owner manifest。
- 服务端只从 M1 允许行/字段生成 cell；批量逐对象重新授权并支持安全部分失败；导出下载重新执行当前 query/decision。
- Web 交付模式、密度、列、偏好、批量与导出；真实隔离六身份 E2E 覆盖隐藏、重放、收权及响应式操作。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S13-M2-T01 | 24 项边界可追溯且不复制查询权威 | M1 report/V106/query service 与 M2 route 审计 | plan/checkpoint + architecture contracts | 不需要：事实复核 | Done |
| PROJECT-PLATFORM-S13-M2-T02 | key/width/order/format/version/capability/lifecycle 固定 | `WorkItemViewModels` | `WorkItemViewServiceTests` PASS | E2E 实际序列化 table/list 合同 | Done |
| PROJECT-PLATFORM-S13-M2-T03 | 复合边界、回执、索引、TTL、清理和 owner 完整 | V107 + JDBC repository + cleanup/owner manifest | PostgreSQL 16 fresh 107/repeat 0 PASS | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S13-M2-T04 | hidden/read-denied 不进入 cell、排序或导出 | `WorkItemViewService.row` 只读 M1 allowed fields | denied dynamic cell omission test PASS | outsider/enterprise-admin 响应无隐藏内容 | Done |
| PROJECT-PLATFORM-S13-M2-T05 | table/list、列、密度、窄屏和刷新稳定 | `ProjectWorkItemsPanel` + responsive CSS | web lint/build PASS | real isolated Playwright PASS | Done |
| PROJECT-PLATFORM-S13-M2-T06 | 逐对象原子、部分失败、选择硬限且不扩权 | bulk service + stable short child request ID | success/forbidden safe reason tests PASS | archive/restore real flow PASS | Done |
| PROJECT-PLATFORM-S13-M2-T07 | 当前允许行列、重放、重新授权和过期边界 | export repository/service/download + CSV escaping | repository migration + service tests PASS | owner download/outsider 404 PASS | Done |
| PROJECT-PLATFORM-S13-M2-T08 | 用户可操作完整视图闭环 | view API client + Ant Design collection surface | lint/build PASS | real isolated table/list/preferences/export/bulk PASS | Done |
| PROJECT-PLATFORM-S13-M2-T09 | 信号无正文且最终 REST 校准 | application realtime protected query invalidation + React Query | web build + protocol/static gates | E2E 刷新后重新取当前授权投影 | Done |
| PROJECT-PLATFORM-S13-M2-T10 | 六身份无越权列、行、文件或错误身份泄漏 | controller advice + query scope + per-entry auth | isolated six-identity setup | real isolated owner/admin/member/guest allow；outsider/enterprise hidden PASS | Done |
| PROJECT-PLATFORM-S13-M2-T11 | 上界与索引可复现且不冒充生产 SLO | query 200 candidate、page 100、bulk 100、V107 indexes | integration assertions + web build PASS | responsive render PASS | Done |
| PROJECT-PLATFORM-S13-M2-T12 | owner、导出、校准和回退清楚；不提前树/共享视图 | target/current/module/event/owner/roadmap/report | checkpoint light gate | 不需要：文档同步 | Done |

## Code Changes
- 新增 view domain/application/API、偏好/导出 repository 与 V107 migration。
- 查询结果增加服务端 capability，异常映射和空间 query scope 覆盖新 route。
- 表格/列表 Web、列和密度偏好、批量反馈、CSV 导出与响应式样式。
- service/migration 单元与集成测试、六身份真实隔离 Playwright 和工作台 S13 smoke route。
- target/current/module/event/table-owner/current-roadmap 与本报告同步。

## Validation
- Backend tests: `WorkItemViewServiceTests`、`WorkItemQueryServiceTests`、`WorkItemQueryCanonicalizerTests` PASS；`WorkItemViewFoundationIntegrationTests` 在 PostgreSQL 16 执行 V001-V107 fresh/repeat PASS。
- Frontend build: `pnpm --dir web lint` PASS；`pnpm --dir web build` PASS。
- Local quality gate: `.local-reports/quality-gate-20260727T120804.md` light checkpoint PASS；`.local-reports/quality-gate-20260727T121332.md` fresh stage finish PASS。
- Browser smoke: `pnpm workbench browser smoke-project-platform-s13-isolated --spec project-platform-s13-m2.spec.ts`，六身份真实隔离环境 1 PASS。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S13-M3-T01 独立复核 M1-M2；树只读取 S10 canonical hierarchy，不提前实现个人/共享保存视图。
