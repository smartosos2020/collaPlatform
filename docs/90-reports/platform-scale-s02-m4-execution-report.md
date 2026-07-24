---
title: PLATFORM-SCALE-S02-M4 执行报告
status: archived
milestone: PLATFORM-SCALE-S02-M4
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S02-M4 Execution Report

## Scope

完成多实例认证与撤销、数据库幂等回执、跨节点上传、维护初始化、依赖故障恢复、动态上游刷新和六身份真实浏览器复验。验证环境使用隔离 Compose project `colla-s02-m3`、隔离数据库和端口。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M4-T01 | static | not-required | not-required | no | 多实例状态、命令和事实源矩阵 |
| PLATFORM-SCALE-S02-M4-T02 | e2e-real-isolated | real | isolated | no | api-a 登录、api-b 校验及跨节点撤销 |
| PLATFORM-SCALE-S02-M4-T03 | integration | not-required | not-required | no | 回执覆盖矩阵和非幂等重试阻断 |
| PLATFORM-SCALE-S02-M4-T04 | integration | not-required | not-required | no | 跨节点先后、并发和重放唯一结果 |
| PLATFORM-SCALE-S02-M4-T05 | integration | not-required | not-required | no | 上传创建、对象写入、完成、查询和错误语义 |
| PLATFORM-SCALE-S02-M4-T06 | integration | not-required | not-required | no | maintenance MinIO 初始化和故障降级 |
| PLATFORM-SCALE-S02-M4-T07 | integration | not-required | not-required | no | fresh、V060 升级和 API schema 校验 |
| PLATFORM-SCALE-S02-M4-T08 | integration | not-required | not-required | no | 并发管理员初始化和角色唯一性 |
| PLATFORM-SCALE-S02-M4-T09 | integration | not-required | not-required | no | PostgreSQL、Redis、MinIO 故障矩阵 |
| PLATFORM-SCALE-S02-M4-T10 | integration | not-required | not-required | no | 依赖恢复、节点重建和初始化重放 |
| PLATFORM-SCALE-S02-M4-T11 | integration | not-required | not-required | no | request id、instance 与敏感日志检查 |
| PLATFORM-SCALE-S02-M4-T12 | e2e-real-isolated | real | isolated | no | 六身份关键路径、双节点状态和迁移 rehearsal |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PLATFORM-SCALE-S02-M4-T01 | Done | 本报告“关键状态与命令矩阵” |
| PLATFORM-SCALE-S02-M4-T02 | Done | `multi-instance-state-smoke.mjs` 登录、设备撤销和 refresh 拒绝 |
| PLATFORM-SCALE-S02-M4-T03 | Done | WorkItem 类型/字段数据库回执与非幂等重试阻断决定 |
| PLATFORM-SCALE-S02-M4-T04 | Done | 跨节点串行、并发、audit/outbox 唯一性断言 |
| PLATFORM-SCALE-S02-M4-T05 | Done | MinIO stat、大小校验、一次完成和重放 |
| PLATFORM-SCALE-S02-M4-T06 | Done | maintenance-only bucket 初始化及 MinIO 降级 |
| PLATFORM-SCALE-S02-M4-T07 | Done | 66 个 fresh 迁移、V060 到 V066 升级和 API Flyway 禁用 |
| PLATFORM-SCALE-S02-M4-T08 | Done | PostgreSQL advisory lock、主角色模型唯一索引和并发重放 |
| PLATFORM-SCALE-S02-M4-T09 | Done | PostgreSQL/Redis/MinIO stop/start 自动场景 |
| PLATFORM-SCALE-S02-M4-T10 | Done | 依赖恢复、API health 收敛和动态上游地址刷新 |
| PLATFORM-SCALE-S02-M4-T11 | Done | 结构化 request/instance 日志和 token 泄漏负断言 |
| PLATFORM-SCALE-S02-M4-T12 | Done | 六身份 Playwright、双实例状态 smoke 和维护 rehearsal |

## Key State And Command Matrix

| State or command | Source of truth / constraint | Cross-node decision |
| --- | --- | --- |
| JWT identity and device revocation | JWT signature + PostgreSQL users/devices/sessions | 每次请求解析当前用户和设备；任一节点撤销后全节点拒绝 |
| WorkItem type/field commands | PostgreSQL command receipt, workspace + request id unique | 支持响应重放；并发只允许一个业务结果/audit/outbox |
| Other project, knowledge, Base and admin writes | Domain aggregate constraints + request id correlation | 当前不声明通用响应重放；Nginx 禁止非幂等 retry，客户端自动重试被阻断 |
| Upload session and completion | PostgreSQL file row + MinIO object/stat | 任一 API 可完成；对象不存在、大小错误、越权和重复完成语义固定 |
| Initial administrator and role | PostgreSQL advisory lock + users + active direct role unique index | maintenance 并发/重放只创建一个管理员和一个有效管理员授权 |
| Bucket initialization | MinIO bucket existence | maintenance-only 幂等执行；API 不竞态创建 |
| Schema migration | Flyway history | maintenance-only；API 关闭 Flyway，仅以 Hibernate validate 检查兼容 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M4-T01 | 状态事实源和唯一约束明确 | 状态矩阵、V066、V062、文件状态转换 | 部署与 schema 定向测试 PASS | not-required: 状态矩阵无产品页面 | Done |
| PLATFORM-SCALE-S02-M4-T02 | 登录和撤销不依赖 JVM session | JWT 解析、数据库设备/session 校验 | api-a 登录、api-b 使用、api-a 撤销、api-b access 403/refresh 401 | real: 六身份经双 API 入口完成真实登录与角色访问，跨节点撤销由同一隔离环境 API smoke 补验 | Done |
| PLATFORM-SCALE-S02-M4-T03 | 回执覆盖和缺口决定可定位 | WorkItem receipts；代理禁止 non-idempotent retry | 回执 schema 与 Nginx 部署合同 PASS | not-required: 命令合同由 API/DB 断言 | Done |
| PLATFORM-SCALE-S02-M4-T04 | 重复/并发只产生唯一副作用 | request id unique、receipt replay、event idempotency key | 两节点并发后 receipt/audit/outbox 均为 1 | not-required: 并发由真实 API/SQL 验证 | Done |
| PLATFORM-SCALE-S02-M4-T05 | 上传跨节点且错误稳定 | 完成前 stat、大小/uploader/status 校验和原子 pending 转换 | 跨节点完成、重复完成、缺对象 409、MinIO down 503 PASS | not-required: 文件协议由真实 MinIO/API 验证 | Done |
| PLATFORM-SCALE-S02-M4-T06 | MinIO 初始化仅由 maintenance 幂等执行 | role condition、bucket exists/make bucket 和结构化结果日志 | 重放 skipped、MinIO 故障时非文件 API 200 | not-required: 维护命令无页面 | Done |
| PLATFORM-SCALE-S02-M4-T07 | 双 API 不迁移，fresh/升级可恢复 | API `SPRING_FLYWAY_ENABLED=false`、maintenance 单次退出、Hibernate validate | fresh 66、V060 到 V066、当前库 065 到 066 PASS | not-required: 迁移由真实数据库验证 | Done |
| PLATFORM-SCALE-S02-M4-T08 | 并发初始化无重复管理员或授权 | advisory lock、current `role_assignments` 写入、V066 唯一索引 | 两 maintenance 并发 + 重放后 user=1、active admin=1 | not-required: 初始化无产品页面 | Done |
| PLATFORM-SCALE-S02-M4-T09 | 三依赖按矩阵降级 | 5s/3s DB timeout、Redis 非事实源、文件能力独立 | DB readiness 503；Redis/MinIO down 非相关 API 200；文件 503 | not-required: 故障由自动化验证 | Done |
| PLATFORM-SCALE-S02-M4-T10 | 恢复后行为与路由收敛 | Docker DNS 动态 upstream、readiness 和恢复等待 | stop/start 后双 API 认证成功，入口连续 8 次 200 且命中两个 IP | not-required: 恢复由真实入口/API 验证 | Done |
| PLATFORM-SCALE-S02-M4-T11 | 动作可关联且密钥不入日志 | request logging、instance id、maintenance result logs | 两个 request id 均出现在实例日志，access/refresh token 不出现 | not-required: 日志证据无页面 | Done |
| PLATFORM-SCALE-S02-M4-T12 | 六身份、双节点和迁移 rehearsal 全通过 | 隔离 Compose 双 API 入口和可重复 smoke 脚本 | 双实例状态 smoke、maintenance rehearsal、定向测试 PASS | real: `project-platform-s04-m4-field-configuration-ui.spec.ts`, 1 passed | Done |

## Code Changes

- 文件：完成上传前核验 MinIO 对象、大小和上传者，仅允许 pending 状态原子完成，重复完成不重复 audit/usage/link。
- 初始化：管理员初始化使用 PostgreSQL advisory lock；直接角色同时写当前 `role_assignments` 主模型和兼容读模型；V066 约束有效直接用户角色唯一。
- 部署：四角色显式连接/校验超时；Nginx 使用 Docker DNS 动态刷新 API、协作和 Event Gateway 地址。
- 自动化：新增维护并发/重放 rehearsal 和跨节点认证、幂等、上传、依赖故障恢复 smoke。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | update | 记录多实例状态、初始化和动态入口当前事实 |
| `docs/02-roadmap/current-roadmap.md` | update | M4 全任务完成 |
| 本报告 | create | M4 逐任务验收与真实环境证据 |

## Validation

- Backend tests: PASS - `DualApiDeploymentContractTests`, `RuntimeRoleBeanContractTests`, `FileServiceTests`, `AdminInitializerTests`, `RoleAssignmentCommandServiceTests`, `ProjectWorkItemTypeSchemaIntegrationTests`.
- Frontend build: PASS - work-cycle finish completed frontend lint, Vite build, chunk budget and route lazy-loading checks.
- Migration rehearsal: PASS - fresh 66 migrations, concurrent maintenance, replay, V060 upgrade and current schema upgrade.
- Multi-instance state: PASS - auth/revocation, command uniqueness, upload, PostgreSQL/Redis/MinIO failure and recovery.
- Dynamic upstream: PASS - API containers recreated, Nginx refreshed addresses, 8/8 requests returned 200 across two upstream IPs.
- Browser smoke: PASS - real isolated six-identity project field configuration, 1 passed.
- Local quality gate: PASS - checkpoint report `.local-reports/quality-gate-20260724T032802.md`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 非 WorkItem 配置写命令尚无统一数据库响应回执；自动代理/客户端重试继续禁止 | non-blocking | PLATFORM-SCALE backlog |
| N/A | Worker 多实例 lease/dead-letter/replay 尚未交付 | non-blocking | PLATFORM-SCALE-S03 |
| N/A | Event Gateway 多节点 Redis fanout 尚未交付 | non-blocking | PLATFORM-SCALE-S04 |

## Next Steps

推进 `PLATFORM-SCALE-S02-M5` 阶段总验收、运行手册、Program Go/No-Go 和文档状态收口。
