# PROJECT-PLATFORM-S05-M1 Execution Report

## Scope
PROJECT-PLATFORM-S05-M1-T01 至 PROJECT-PLATFORM-S05-M1-T11

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M1-T01 | static | not-required | not-required | No | 代码、迁移、测试与 owner 事实审计 |
| PROJECT-PLATFORM-S05-M1-T02 | system-real-isolated | not-required | isolated | No | 目标架构合同与真实布局保存读取一致 |
| PROJECT-PLATFORM-S05-M1-T03 | system-real-isolated | not-required | isolated | No | V001-V077 空库迁移和 V076-V077 升级 |
| PROJECT-PLATFORM-S05-M1-T04 | unit | not-required | not-required | No | 领域模型和未知 schema 失败关闭 |
| PROJECT-PLATFORM-S05-M1-T05 | system-real-isolated | not-required | isolated | No | 真实 PostgreSQL 图保存与稳定读取 |
| PROJECT-PLATFORM-S05-M1-T06 | unit | not-required | not-required | No | 规范 hash、图结构与预算校验 |
| PROJECT-PLATFORM-S05-M1-T07 | system-real-isolated | not-required | isolated | No | 同域引用拒绝与失效诊断 |
| PROJECT-PLATFORM-S05-M1-T08 | system-real-isolated | not-required | isolated | No | owner/admin/member/non-member/enterprise admin |
| PROJECT-PLATFORM-S05-M1-T09 | system-real-isolated | not-required | isolated | No | 幂等重放、异载荷、旧版本、审计与 outbox |
| PROJECT-PLATFORM-S05-M1-T10 | system-real-isolated | not-required | isolated | No | MockMvc 真实 API、错误码、动作投影与 OpenAPI |
| PROJECT-PLATFORM-S05-M1-T11 | system-real-isolated | not-required | isolated | No | 定向测试、架构门禁和工作循环收口 |

## Completed Items
- 完成 V077 布局、节点、字段访问策略和命令回执 schema，登记 project table owner。
- 完成 create/detail 布局领域模型、规范序列化、图预算、永久身份和字段引用诊断。
- 完成 JDBC Repository、事务保存、aggregate version、request ID 幂等、审计和 outbox。
- 完成 owner/admin 空间授权、最小披露异常和服务端 `availableActions`。
- 完成 `GET/PUT .../layouts/{layoutKind}` DTO、Controller 与 OpenAPI 合同。
- 保持 S05-M2 UI、S05-M3 运行时访问决策、S06 发布和 S07 实例边界未实现。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M1-T01 | 可复用合同和禁止提前实现项可定位 | S03/S04 服务、仓储、API、测试与 V061-V076 审计；当前/目标架构同步 | `mvn -DskipTests test` | 后端配置合同，无浏览器入口 | Done |
| PROJECT-PLATFORM-S05-M1-T02 | create/detail、节点、策略、诊断无歧义 | `WorkItemLayoutModels`；目标架构 22.2 | canonicalizer tests 3/3 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T03 | 复合隔离、约束、索引、身份保护完整 | `V077__create_project_work_item_layout_foundation.sql`；table owner manifest | schema integration tests 4/4 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T04 | 不复制字段事实，未知 schema 拒绝 | `WorkItemLayoutModels`、`WorkItemLayoutCanonicalizer` | canonicalizer tests 3/3 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T05 | 范围化、稳定、无 N+1 保存读取 | `JdbcWorkItemLayoutRepository` 单次节点/策略查询与范围化写入 | API integration tests 3/3 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T06 | 稳定 hash 与图预算失败关闭 | `WorkItemLayoutCanonicalizer` | 稳定顺序、孤儿、重复字段、深度测试 3/3 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T07 | 跨域拒绝，失效诊断不改图 | `WorkItemLayoutFieldReferenceValidator` | 跨空间字段 400；disabled 诊断且节点保留 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T08 | 空间角色与最小披露正确 | `WorkItemLayoutActionPolicy`、service context guard | admin 200、member 403、outsider/root 404 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T09 | 幂等、并发版本和脱敏副作用正确 | command receipt、aggregate version、audit/outbox metadata | 重放单命令；异载荷/旧版本 409；两次有效保存两条副作用 | 不需要 | Done |
| PROJECT-PLATFORM-S05-M1-T10 | API、错误和动作投影稳定 | `WorkItemLayoutApiDtos`、Controller、exception handler | MockMvc/OpenAPI integration tests 3/3 | M2 前无 UI | Done |
| PROJECT-PLATFORM-S05-M1-T11 | 空库/升级与目标验证通过 | schema/API/unit tests 与本报告 | unit 3/3、schema 4/4、API 3/3 | 后端里程碑，不需要浏览器 | Done |

## Code Changes
- `server/src/main/resources/db/migration/V077__create_project_work_item_layout_foundation.sql`
- `server/src/main/java/com/colla/platform/modules/project/{domain,application,infrastructure,api}/WorkItemLayout*`
- `server/src/test/java/com/colla/platform/modules/project/{application,api,infrastructure}/*Layout*`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/current-architecture.md`
- `docs/01-architecture/project-platform-target-architecture.md`
- `docs/02-roadmap/current-roadmap.md`

## Validation
- Backend tests: targeted unit 3/3, real isolated schema 4/4 and real isolated API 3/3 passed.
- Frontend build: stage finish gate lint, build, chunk budget and route lazy-loading passed.
- Local quality gate: `quality-gate-20260725T135708.md` passed checkpoint.
- Browser smoke: not required; M1 only exposes backend configuration contracts and no user-visible UI.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M2 | 布局编辑器、条件配置和共享渲染器未实现 | 不影响 M1 后端合同 | Current roadmap M2 |
| PROJECT-PLATFORM-S05-M3 | 运行时 read/write/required/hidden 决策未实现 | 不影响 M1 配置持久化 | Current roadmap M3 |
| N/A | 发布版本与 WorkItem 实例未实现 | non-blocking | Program stage index S06/S07; M1 明确禁止提前交付 |

## Next Steps
- 从 `PROJECT-PLATFORM-S05-M2-T01` 开始下一轮工作循环。
