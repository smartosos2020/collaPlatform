# PLATFORM-SCALE-S04-M2 Execution Report

## Scope

PLATFORM-SCALE-S04-M2-T01 到 PLATFORM-SCALE-S04-M2-T11。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M2-T01 | static | not-required | not-required | No | `docs/01-architecture/event-side-effect-matrix.md` 覆盖五域和旧 sender |
| PLATFORM-SCALE-S04-M2-T02 | unit | not-required | not-required | No | `RealtimeSignalEnvelopeTests` 校验 payload、sequence、版本与校准路径 |
| PLATFORM-SCALE-S04-M2-T03 | e2e-real-isolated | real | isolated | No | 通知创建经 durable pipeline 到达双 Gateway |
| PLATFORM-SCALE-S04-M2-T04 | e2e-real-isolated | real | isolated | No | 会话与消息创建经 durable pipeline 到达双 Gateway |
| PLATFORM-SCALE-S04-M2-T05 | e2e-real-isolated | real | isolated | No | 项目创建和成员目标经 durable pipeline 到达双 Gateway |
| PLATFORM-SCALE-S04-M2-T06 | e2e-real-isolated | real | isolated | No | 资源授权和身份失效经 durable pipeline 到达双 Gateway |
| PLATFORM-SCALE-S04-M2-T07 | static | not-required | not-required | No | `ModuleArchitectureTests` 证明业务模块无本地 sender/session 依赖 |
| PLATFORM-SCALE-S04-M2-T08 | unit | not-required | not-required | No | `WebSocketMessageSenderTests` 校验 legacy frame 开关和 emitted/blocked 指标 |
| PLATFORM-SCALE-S04-M2-T09 | e2e-real-isolated | real | isolated | No | 双连接验证身份变化、重复连接与 durable 校准 |
| PLATFORM-SCALE-S04-M2-T10 | e2e-real-isolated | real | isolated | No | 定向 Maven 回归与真实双 Gateway 流验证五类信号的幂等、顺序和权限边界 |
| PLATFORM-SCALE-S04-M2-T11 | e2e-real-isolated | real | isolated | No | `platform-scale-s04-m2-business-signals.spec.ts` 验证五域连续真实流 |

## Completed Items

- 冻结通知、IM、项目、权限和身份的 source event、signal type、audience、sequence 与 REST 校准矩阵。
- 通知首次创建、单条已读、批量/全部已读均从 PostgreSQL 事实追加 durable realtime event。
- IM 消息、会话、已读和未读变化按 recipient audience sequence 进入通用 signal pipeline。
- 项目、工作项和项目空间变化按当前成员解析目标；撤权用户只收到安全 invalidation。
- 角色、角色分配、资源授权、成员、部门和用户组变化发出 workspace 级失效提示，不复制 ACL。
- 业务模块移除 `WebSocketMessageSender` 和 `WebSocketSessionRegistry` 直接依赖，并增加 ArchUnit 门禁。
- 增加 legacy v0 frame 显式开关与低基数 emitted/blocked 指标，冻结 M4/M5 退出期限。
- 增加真实双 Gateway 四域浏览器用例，并将 M1 断言升级为正式 `notification.created` v1 合同。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M2-T01 | 动作、事实、信号、目标、顺序和校准唯一 | `docs/01-architecture/event-side-effect-matrix.md` 的 S04 五域矩阵 | `ModuleArchitectureTests` 与业务 sender/session 静态扫描通过 | not-required: 文档与静态边界证据 | Done |
| PLATFORM-SCALE-S04-M2-T02 | 最小 payload 与可比较 sequence | `RealtimeSignalEnvelope` 及五域 signal Handler | `RealtimeSignalEnvelopeTests`、五域 Handler tests 通过 | not-required: 合同与单元测试证据 | Done |
| PLATFORM-SCALE-S04-M2-T03 | 通知事实先持久化且重复投影安全 | notification projection 与 `NotificationRealtimeSignalDomainEventHandler` | `NotificationDomainEventHandlerTests`、`NotificationRealtimeSignalDomainEventHandlerTests` 通过 | real: 双 Gateway 各收到一次 `notification.created` | Done |
| PLATFORM-SCALE-S04-M2-T04 | IM 事实与接收者目标一致 | `ImService` 与 `ImRealtimeSignalDomainEventHandler` | `ImRealtimeSignalDomainEventHandlerTests`、`ImControllerIntegrationTests` 通过 | real: 双 Gateway 各收到一次目标成员 `message.created` | Done |
| PLATFORM-SCALE-S04-M2-T05 | 项目信号只携带安全定位字段 | `ProjectRealtimeDomainEventHandler` | `ProjectRealtimeDomainEventHandlerTests`、`ProjectControllerIntegrationTests` 通过 | real: 双 Gateway 各收到一次 `project.changed` | Done |
| PLATFORM-SCALE-S04-M2-T06 | 权限与身份变化只发安全失效提示 | permission/identity publishers 与 event handlers | permission、role、identity、device 定向集成测试通过 | real: 双 Gateway 收到 `permission.invalidated` 与 `identity.invalidated` | Done |
| PLATFORM-SCALE-S04-M2-T07 | 业务模块仅经公共 signal contract 发出实时提示 | 业务服务构造器移除 sender，业务模块仅依赖公开 signal contract | `ModuleArchitectureTests` 禁止业务模块依赖 sender/session registry | not-required: ArchUnit 与 import scan 证据 | Done |
| PLATFORM-SCALE-S04-M2-T08 | 兼容层可观测且可关闭 | `legacyFramesEnabled` 与 emitted/blocked Micrometer counter | `WebSocketMessageSenderTests` 校验开关和两类指标 | not-required: 配置与指标单元测试证据 | Done |
| PLATFORM-SCALE-S04-M2-T09 | 多连接、身份变化和 durable 校准一致 | connection/user/workspace/device registry 与身份失效 publisher | session、device、identity 定向集成测试通过 | real: 同一用户连接两个 Gateway 后收到同 eventId/sequence | Done |
| PLATFORM-SCALE-S04-M2-T10 | 幂等、顺序、权限边界和敏感字段断言通过 | 五域 Handler 与公共 durable transport | 66 项定向测试为 0 failure、0 error、0 skipped | real: 五类 frame 的 eventId/sequence 一致且敏感键为零 | Done |
| PLATFORM-SCALE-S04-M2-T11 | 四域真实流程和架构边界完成收口 | isolated prod compose 的双 API、双 Worker、双 Gateway | 前端 build/lint、架构边界和工作循环 checkpoint 通过 | real: `platform-scale-s04-m2-business-signals.spec.ts` 1 passed | Done |

## Code Changes

- `server/.../notification`、`im`、`project`、`permission`、`identity`：业务变化改为 durable realtime domain event 和最小化 signal Handler。
- `server/.../event`、`shared/realtime`、`shared/websocket`：source sequence fallback、legacy 开关/指标和业务依赖门禁。
- `web/e2e/platform-scale-s04-m1-dual-gateway.spec.ts`：更新正式通知 signal type。
- `web/e2e/platform-scale-s04-m2-business-signals.spec.ts`：新增双 Gateway 五类信号真实流程。
- `docs/01-architecture/event-side-effect-matrix.md` 与当前架构：记录 S04-M2 已实现合同。

## Validation

- Backend tests: 66 targeted tests, 0 failures, 0 errors, 0 skipped.
- Frontend build: production build and lint passed.
- Local quality gate: `.local-reports/quality-gate-20260724T121106.md` is the corrected passing finish report; the earlier `120037` attempt exposed the report contract issue and is not acceptance evidence.
- Browser smoke: real isolated `platform-scale-s04-m2-business-signals.spec.ts` passed against dual API/Worker/Gateway.
- Browser diagnosis: first run exposed isolated Origin 不在 Gateway CORS 列表；以环境级 Origin 覆盖重建后，第二次发现测试误把授权资源 type 断言为内部 aggregate type；收口重跑期间 Docker VM 时钟发生约 48 秒全局回拨，使 future-dated event 延迟至等待窗口外，时钟稳定后的 fresh rerun 在 16.5 秒内通过。
- Work-cycle checkpoint/finish: recorded by the workbench after this report.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M3-T01 | 客户端 parser、watermark、gap 与 REST 校准属于后续客户端里程碑 | deferred | `PLATFORM-SCALE-S04-M3-T01` 到 `M3-T11` |
| PLATFORM-SCALE-S04-M4-T04 | legacy v0 frame 的退出开关与旧 Spring 知识协同关闭属于后续协议收敛里程碑 | deferred | `PLATFORM-SCALE-S04-M4-T04` 到 `M4-T11` |

## Next Steps

- 按独立工作循环推进 PLATFORM-SCALE-S04-M3-T01 到 T11。
