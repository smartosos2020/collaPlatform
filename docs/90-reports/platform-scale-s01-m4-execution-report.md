---
title: PLATFORM-SCALE-S01-M4 Execution Report
status: completed
milestone: PLATFORM-SCALE-S01-M4
stage: PLATFORM-SCALE-S01
updated_at: 2026-07-24
---

# PLATFORM-SCALE-S01-M4 Execution Report

## Scope

- PLATFORM-SCALE-S01-M4-T01 to PLATFORM-SCALE-S01-M4-T16.
- 本轮把 project 的 identity、file、platform、event、audit、permission、IM 私有 Java 依赖迁移到 provider-owned 公共合同。
- M3 已完成的 shared inbound port 一并复验；共享数据库跨 owner 只读查询仍按 S02 精确例外治理，不冒充已完成投影化。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M4-T01 | integration | not-required | not-required | no | 依赖矩阵由边界 artifact 与本报告固定 |
| PLATFORM-SCALE-S01-M4-T02 | integration | not-required | not-required | no | identity provider adapter 与项目成员测试 |
| PLATFORM-SCALE-S01-M4-T03 | integration | not-required | not-required | no | 复杂字段、空间成员和六身份后端负例 |
| PLATFORM-SCALE-S01-M4-T04 | integration | not-required | not-required | no | file provider adapter 与附件元数据测试 |
| PLATFORM-SCALE-S01-M4-T05 | integration | not-required | not-required | no | 复杂附件和 legacy issue 附件路径 |
| PLATFORM-SCALE-S01-M4-T06 | integration | not-required | not-required | no | platform resolver SPI、command/query adapter |
| PLATFORM-SCALE-S01-M4-T07 | integration | not-required | not-required | no | 对象摘要、迁移和最小披露回归 |
| PLATFORM-SCALE-S01-M4-T08 | integration | not-required | not-required | no | transactional outbox 同事务集成测试 |
| PLATFORM-SCALE-S01-M4-T09 | e2e-real-isolated | real | isolated | no | audit port 集成测试与 S04 M1-M4 真实隔离流程共同验证请求边界 |
| PLATFORM-SCALE-S01-M4-T10 | e2e-real-isolated | real | isolated | no | 字段、类型、预置、成员和迁移集成回归与 S04 M1-M4 真实隔离流程 |
| PLATFORM-SCALE-S01-M4-T11 | integration | not-required | not-required | no | IM 消息链接、撤回和可见性回归 |
| PLATFORM-SCALE-S01-M4-T12 | integration | not-required | not-required | no | project foreign private import 计数为 0 |
| PLATFORM-SCALE-S01-M4-T13 | integration | not-required | not-required | no | shared reverse import 计数为 0 |
| PLATFORM-SCALE-S01-M4-T14 | integration | not-required | not-required | no | Maven 目标集验证事务、幂等、隔离和错误兼容 |
| PLATFORM-SCALE-S01-M4-T15 | e2e-real-isolated | real | isolated | no | S04 M1-M4 四条真实隔离 Playwright 流程 |
| PLATFORM-SCALE-S01-M4-T16 | integration | not-required | not-required | no | checkpoint、finish、lint/build 与边界门禁 |

## Completed Items

- 新增并实现 identity `SubjectDirectory`、file `FileAccess`、platform object SPI/commands/registry、transactional outbox、audit、project authorization 和 project-IM 公共端口。
- project resolver 只实现 platform contract SPI；平台注册器负责发现、内部模型转换和 link fallback。
- 项目复杂字段、空间成员、旧项目、空间迁移、字段/类型/预置服务均不再 import foreign 私有包。
- IM adapter 保留原 `ImService.sendMessage` 链路，继续解析消息链接、mention、事件和对象摘要。
- project foreign private import 从 51 降为 0；后端历史 private import 总基线从 204 收紧到 153；shared reverse import 保持 0。
- 9 条 project 跨 owner read 仍是共享数据库组合查询，按 M3 决策精确保留到 S02；未新增表、方向、写入或通配例外。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M4-T01 | P0 文件逐项映射合同和回归 | contract/provider/consumer 清单与本报告 | `architecture-boundaries.md` 与 targeted Maven 集 | not-required: 清单本身无 UI | Done |
| PLATFORM-SCALE-S01-M4-T02 | identity 查询由 provider 实现 | `SubjectDirectoryService`, `AuthenticationQueryService` | 项目空间成员与复杂字段测试通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T03 | 两服务无 identity 私有 import | `WorkItemFieldComplexReferenceValidator`, `ProjectSpaceMembershipService` | 复杂字段、空间成员控制器测试通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T04 | file 合同不暴露存储实现 | `FileAccess`, `FileAccessService` | 编译与复杂字段测试通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T05 | project 无 file 私有 import | `ProjectService` 与复杂附件校验迁移 | `ProjectControllerIntegrationTests` 5/5 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T06 | SPI 发现无 provider 私有反向依赖 | platform contract 与三个 project resolver | Spring context、对象摘要和架构测试通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T07 | platform 私有 import 为 0 | space/migration/legacy 使用 commands/registry | 项目、空间、迁移测试通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T08 | outbox 保持调用方事务 | `TransactionalOutboxAdapter` 委托既有 repository | 字段、类型、预置、迁移集成测试通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T09 | audit 保持请求边界和脱敏链路 | `AuditLogAdapter` 委托既有 audit service/repository | 项目审计断言、目标测试及 S04 M1-M4 真实隔离流程通过 | work:finish fresh real/isolated evidence | Done |
| PLATFORM-SCALE-S01-M4-T10 | event/audit 只依赖 contract | 7 个 project application service 完成替换 | Maven 目标集 64 个测试及 S04 M1-M4 真实隔离流程通过 | work:finish fresh real/isolated evidence | Done |
| PLATFORM-SCALE-S01-M4-T11 | IM 行为和失败语义兼容 | `ProjectMessagingAdapter`, `ProjectService` | 消息链接 summary、权限和撤回路径通过 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T12 | project foreign private import 为 0 | project 全模块 import 扫描 | boundary baseline `project=0` | not-required | Done |
| PLATFORM-SCALE-S01-M4-T13 | shared 不依赖业务模块 | authentication/collaboration inbound port | ArchUnit 2/2，shared reverse 0 | not-required | Done |
| PLATFORM-SCALE-S01-M4-T14 | 事务、幂等、隔离和错误兼容 | 保留原事务入口、错误和 provider 调用 | 10 组后端目标测试 fresh PASS | not-required | Done |
| PLATFORM-SCALE-S01-M4-T15 | 架构清零且四条真实流程通过 | 精确收紧 baseline 与 S04 specs | `pnpm architecture:boundaries` PASS | work:finish fresh real/isolated evidence | Done |
| PLATFORM-SCALE-S01-M4-T16 | 文档、门禁和影响范围闭环 | 路线、ADR、报告、例外退出阶段同步 | checkpoint/finish quality report | T15 提供浏览器证据 | Done |

## Code Changes

- `server/.../identity|file|platform|event|audit|permission|im/contract`: 跨模块公共 facade、SPI 和最小值对象。
- provider application adapters：公共合同到现有 owner service/repository 的事务内适配。
- `server/.../project/application` 与 `domain/ProjectModels`: consumer 全量迁移到合同。
- `PlatformObjectResolverRegistry`: 同时发现内部和公共 SPI，统一模型转换和 link fallback。
- `platform-boundary-baseline.json`: 后端 private import 基线由 204 收紧到 153。
- `platform-boundary-exceptions.json`: 9 条非 P0 跨 owner read 的退出阶段与 M3 的 S02 决策对齐。

## Validation

- Backend compile: PASS - `mvn -q -DskipTests compile`.
- Backend tests: PASS - 10 组受影响目标测试；`ProjectControllerIntegrationTests` 5/5，其他目标集 59/59。
- Architecture: PASS - project foreign private 0、shared reverse 0、foreign write 0。
- Frontend build: work:finish 执行 lint、TypeScript/Vite build、chunk 与 lazy route 门禁。
- Local quality gate: PASS - M4 checkpoint 报告 `quality-gate-20260723T191204.md`。
- Browser smoke: work:finish 执行 S04 M1-M4 四个 spec，`real + isolated`。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | project 仍有 9 条精确跨 owner read；全仓共 93 条 | non-blocking | PLATFORM-SCALE-S02 query facade/projection；禁止扩大或写入 |
| N/A | 运行角色、lease、fanout 和双 API 尚未实现 | non-blocking | PLATFORM-SCALE-S02 |

## Next Steps

- M5 执行 Stage 全量 route-final、文档修订一致性、Go/No-Go 和 S02 准入冻结。
