# PROJECT-PLATFORM-S21-M3 Execution Report

## Scope
PROJECT-PLATFORM-S21-M3-T01 到 PROJECT-PLATFORM-S21-M3-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M3-T01 | non-core | integration | not-required | not-required | No | 对账 M1-M2 共 24 项、V139 运行拓扑、规范模型与未关闭阻断 |
| PROJECT-PLATFORM-S21-M3-T02 | non-core | static | not-required | not-required | No | 校验版本化四场景、预算、安全、备份集、恢复点及非生产声明 |
| PROJECT-PLATFORM-S21-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | 在真实 PostgreSQL 16 建立四类空间及 1000 个确定性 synthetic WorkItem |
| PROJECT-PLATFORM-S21-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | 执行索引读、聚合读、写入及数据库占用本地隔离预算 |
| PROJECT-PLATFORM-S21-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | 完整安全门禁并验证 legacy 权限/对象注册为零、跨空间约束失败关闭 |
| PROJECT-PLATFORM-S21-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | 执行 PostgreSQL custom archive、SHA-256 和恢复集登记校验 |
| PROJECT-PLATFORM-S21-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | 在新隔离数据库恢复并对账 V139、规范事实数量与 digest |
| PROJECT-PLATFORM-S21-M3-T08 | core-system | system-real-isolated | not-required | isolated | No | 在恢复库演练中断事务 rollback，确认无部分删除且不恢复旧写 |
| PROJECT-PLATFORM-S21-M3-T09 | core-user | e2e-real-isolated | real | isolated | No | 真实管理员浏览器验证受控证据索引、最小披露、离线与关键视口 |
| PROJECT-PLATFORM-S21-M3-T10 | core-user | e2e-real-isolated | real | isolated | No | 完整 route-final 后以真实浏览器复验 canonical 空间与旧 API 404 |
| PROJECT-PLATFORM-S21-M3-T11 | non-core | integration | not-required | not-required | No | 对账工程门禁、容量、安全、恢复结果，并区分本地与生产结论 |
| PROJECT-PLATFORM-S21-M3-T12 | non-core | integration | not-required | not-required | No | 给出仅允许进入真人试用准备的 M4 Engineering Go |

## Completed Items
- 冻结 `colla.s21-engineering-readiness/v1`：四类场景、1000 个 synthetic WorkItem、本地预算、恢复集、RPO 与非生产声明。
- 新增真实 PostgreSQL 16 工程测试，覆盖 V001-V139、确定性规模数据、性能预算、安全边界、备份、SHA-256、隔离恢复、digest 对账和事务回退。
- 旧 capacity-v1 明确标记 history-only 且禁止作为 S21 证据；工程脚本拒绝 legacy project/issue 活动域。
- 完整门禁和真实隔离浏览器覆盖 canonical 空间、旧 API 404、受控证据索引、1440/1366/820 与离线失败关闭。
- 冻结 M4 真人试用准备协议；未生成参与者、consent、原始反馈或人工结论。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M3-T01 | M1-M2 24 项可追溯且无被预算掩盖的阻断 | M1/M2 report、V138/V139 与 current roadmap | report/documentation gate | not-required：治理对账无独立用户闭环 | Done |
| PROJECT-PLATFORM-S21-M3-T02 | 环境、数据、身份、操作、预算、RTO/RPO 和失败判定冻结 | `s21-engineering-readiness.v1.json` | `pnpm s21:readiness-contract` | not-required：版本化静态合同 | Done |
| PROJECT-PLATFORM-S21-M3-T03 | 四类 canonical seed 可重复且无真实隐私 | `S21EngineeringReadinessIntegrationTests` deterministic fixture | real PostgreSQL 16，4 space/1000 WorkItem assertions | not-required：系统规模夹具 | Done |
| PROJECT-PLATFORM-S21-M3-T04 | 固定环境预算内无无界增长 | indexed/aggregate/write P95 与 relation bytes observations | `pnpm s21:engineering-evidence` | not-required：系统资源预算 | Done |
| PROJECT-PLATFORM-S21-M3-T05 | P0/P1 为零且关键边界失败关闭 | security gate、legacy permission/object assertions、cross-space FK | route-final security audit/scan + real PostgreSQL assertions | not-required：完整安全门禁与系统测试 | Done |
| PROJECT-PLATFORM-S21-M3-T06 | 备份格式、版本、校验、恢复依赖完整 | custom-format `pg_dump`、SHA-256、recovery-set contract | real container backup command and checksum assertions | not-required：基础设施系统闭环 | Done |
| PROJECT-PLATFORM-S21-M3-T07 | 隔离恢复的 schema 与规范事实一致 | new restored database、Flyway version/count/digest checks | real `pg_restore --single-transaction` | not-required：恢复系统闭环 | Done |
| PROJECT-PLATFORM-S21-M3-T08 | 中断不留部分事实且不恢复 legacy 写 | restored database rollback drill | transaction rollback count assertion + legacy prohibition | not-required：恢复系统闭环 | Done |
| PROJECT-PLATFORM-S21-M3-T09 | 证据入口受控、最小披露且无浏览器运维凭据 | admin legacy exit audit page/API | frontend build + isolated API assertions | real isolated M3 spec 覆盖证据状态、无 password、离线和三视口 | Done |
| PROJECT-PLATFORM-S21-M3-T10 | 完整门禁无阻断且 fresh | workbench route-final profile | PostgreSQL/Flyway/backend/frontend/collaboration/architecture/security full gate | real isolated `project-platform-s21-m3-route-final.spec.ts` 验证 canonical 产品面和旧 API 404 | Done |
| PROJECT-PLATFORM-S21-M3-T11 | 结果区分本地/生产且无未知伪 PASS | contract `productionSloClaim=false`、current/target architecture | contract validator + report consistency gate | not-required：工程结论对账 | Done |
| PROJECT-PLATFORM-S21-M3-T12 | 仅在 P0/P1 清零且环境可恢复时进入 M4 准备 | 本报告 Engineering Go 与 M4 preparation 文档 | M3 checkpoint/route-final | not-required：不替代真人试用 | Done |

## Code Changes
- 新增 S21 工程就绪合同、Node 校验器、package scripts 和真实 PostgreSQL 16 容量/安全/备份恢复集成测试。
- 修正退出旧产品合同后仍引用 `/api/projects`、Issue object/link/conversion 的历史集成测试，删除已失效的 legacy write/cutover 补偿用例。
- 新增 M3 真实隔离 route-final Playwright spec，并同步 current/target/module/object/event 架构边界。
- 新增 M4 真人试用准备文档，明确 consent、合成数据、四场景脚本、原始证据、停止/清理条件和 AI 禁止代签。

## Validation
- Backend tests: 完整 Maven 后端门禁及 real isolated `S21EngineeringReadinessIntegrationTests`；fresh step log `.local-reports/quality-gate-20260729T042735-backend-tests.log`
- Frontend build: route-final frontend lint/test/build；fresh step logs `.local-reports/quality-gate-20260729T042735-frontend-lint.log`、`.local-reports/quality-gate-20260729T042735-frontend-build.log`
- Local quality gate: `.local-reports/quality-gate-20260729T042735.md`；architecture/security/collaboration/backend/frontend 等技术门禁全部 PASS，报告治理发现的非路线风险关联字段已按合同修正后重新执行 route-final
- Browser smoke: real isolated `project-platform-s21-m3-route-final.spec.ts`

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M4 | 尚无真人研发、市场、HR、交付参与者的 consent、原始任务记录或反馈 | 不阻断 M3 Engineering Go；阻断任何 S21 产品 Go/归档 | M4-T01-T12 |
| N/A | 本地隔离预算、备份恢复和浏览器证据不等同于生产 SLO、生产恢复或切流批准 | non-blocking | 后续生产变更流程 |

## Next Steps
- 停在 PROJECT-PLATFORM-S21-M4 真人试用准备点；由真人确认参与者、consent、隔离环境和时间窗后再独立启动 M4 工作循环。
- 不启动 M4 任务、不生成自动化替代反馈、不归档 S21、不激活 S22。
