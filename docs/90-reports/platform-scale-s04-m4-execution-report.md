# PLATFORM-SCALE-S04-M4 Execution Report

## Scope
PLATFORM-SCALE-S04-M4-T01 到 PLATFORM-SCALE-S04-M4-T11

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M4-T01 | static | not-required | not-required | No | 静态盘点 Spring/Hocuspocus 入口、owner、存储和替代路径，无页面行为 |
| PLATFORM-SCALE-S04-M4-T02 | static | not-required | not-required | No | 架构合同证明唯一协议、durable source 和组件责任，无页面行为 |
| PLATFORM-SCALE-S04-M4-T03 | e2e-real-isolated | real | isolated | No | 隔离双节点指标区分旧流量和真实协作连接且无高基数标签 |
| PLATFORM-SCALE-S04-M4-T04 | e2e-real-isolated | real | isolated | No | 旧知识 WebSocket 帧收到升级语义且不能写入第二份状态 |
| PLATFORM-SCALE-S04-M4-T05 | e2e-real-isolated | real | isolated | No | 真实 ticket、权限收紧、update 和 snapshot sequence 保持一致 |
| PLATFORM-SCALE-S04-M4-T06 | e2e-real-isolated | real | isolated | No | 两个 collaboration 节点编辑收敛、awareness 正常且可 durable recovery |
| PLATFORM-SCALE-S04-M4-T07 | static | not-required | not-required | No | 源码、Bean 和架构合同证明旧 handler、scheduler、room 均已移除 |
| PLATFORM-SCALE-S04-M4-T08 | e2e-real-isolated | real | isolated | No | fresh/upgrade schema 后真实 API、协作和 UI 仅使用活动 Yjs 字段 |
| PLATFORM-SCALE-S04-M4-T09 | static | not-required | not-required | No | 角色 Bean、架构边界、命名和文档门禁证明唯一活动协同边界 |
| PLATFORM-SCALE-S04-M4-T10 | e2e-real-isolated | real | isolated | No | 真实单人/双用户编辑、撤权、刷新、持久化和版本流程无回退 |
| PLATFORM-SCALE-S04-M4-T11 | e2e-real-isolated | real | isolated | No | 双 collaboration 分流与旧协议拒绝浏览器流程形成里程碑闭环 |

## Completed Items

- 盘点并冻结知识协同责任边界：Hocuspocus/Yjs 是唯一实时协议，PostgreSQL update/snapshot 是 durable source，Spring 仅保留 ticket、load、store、invalidate 内部网关。
- `/ws/events` 不再处理知识编辑命令；旧命令只返回 `protocol.upgrade_required`，并以低基数指标区分 legacy、malformed 和 unsupported 输入。
- 删除 Spring 旧 handler、内存 room/presence、autosave/cleanup maintenance 链路及其 repository/DTO 活动面。
- 引入 collaboration generation 与 snapshot sequence 水位线；更新、快照和 invalidate 均带 generation fence，拒绝陈旧写且不会覆盖较新事实。
- collaboration sidecar 支持多 API 地址、故障切换和协议/schema readiness，双节点通过 Redis 收敛并从 PostgreSQL 恢复。
- 修复并发空快照初始加载导致内容重复、generation 唯一键冲突目标不一致、持久化暂时失败导致 sidecar 退出三项真实运行问题。
- 节点指标保留累计接入量，并将预期的陈旧快照冲突与真正 store/backend failure 分开。
- 真实双用户浏览器流程验证跨节点编辑、awareness、块操作、权限收紧、PostgreSQL 持久化和旧协议拒绝。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M4-T01 | 两条链路、schema、任务、ticket 和客户端入口 owner 完整 | 静态调用图与运行拓扑确认旧 Spring handler/maintenance 和活动 Hocuspocus gateway 的全部入口 | architecture、runtime role 与 collaboration targeted tests | not-required：静态与运行边界盘点 | Done |
| PLATFORM-SCALE-S04-M4-T02 | 唯一协议、事实源和责任矩阵冻结 | `current-architecture.md`、`platform-scale-target-architecture.md` 明确 Yjs、PostgreSQL 与 Spring 内部 API 边界 | `ModuleArchitectureTests` 和 `RuntimeRoleBeanContractTests` 通过 | not-required：架构合同 | Done |
| PLATFORM-SCALE-S04-M4-T03 | 旧流量可区分且无高基数标签 | `PlatformWebSocketHandler` 的 `legacy/malformed/unsupported` 低基数计数；collaboration 节点级指标 | `PlatformWebSocketHandlerTests` 与 collaboration metrics test 通过 | real：两个节点指标均记录真实接入且无用户/文档标签 | Done |
| PLATFORM-SCALE-S04-M4-T04 | 旧协议关闭后明确升级且不写状态 | `/ws/events` 返回 `protocol.upgrade_required`、`colla-yjs-v1` 和 `/collaboration`，通用 socket 保持可用 | handler unit contract + cutover spec | real：旧 `knowledge.content.update` 仅收到升级合同 | Done |
| PLATFORM-SCALE-S04-M4-T05 | ticket、撤权、更新和 snapshot sequence 一致 | generation-aware authenticate/load/update/snapshot/invalidate；陈旧快照分类并触发恢复 | gateway service、schema migration、server/protocol tests | real：成员撤权后 ticket 403、编辑转只读；持久快照 sequence 单调 | Done |
| PLATFORM-SCALE-S04-M4-T06 | 双节点收敛、awareness 短暂且可 durable recovery | Redis extension、periodic recovery、API failover/readiness、累计节点接入指标 | multi-node integration 覆盖跨节点、Redis 降级、重启和恢复 | real：A/B 各 `acceptedConnections=1`，Redis ready，内容与 awareness 收敛 | Done |
| PLATFORM-SCALE-S04-M4-T07 | Event Gateway、API、Worker 无旧运行链 | 删除 `CollaborationMessageHandler`、`KnowledgeContentCollaborationService`、`KnowledgeCollaborationMaintenanceWorker` | source absence、bean role 和 architecture contracts | not-required：运行角色无旧 Bean | Done |
| PLATFORM-SCALE-S04-M4-T08 | 活动 API/schema/frontend 只保留 Yjs 必需面 | V071 增加 generation、删除 legacy columns/constraint；repository 与 diagnostics 收敛 | V001→V071、V049→V071 migration integration 均通过 | real：生产镜像在 v070 库执行 V071 后协作正常 | Done |
| PLATFORM-SCALE-S04-M4-T09 | 角色 Bean、架构命名和文档一致 | collaboration internal API 仅 API/COMBINED；知识模块禁止依赖 shared websocket | runtime role、architecture、planning/documentation gate 通过 | not-required：角色与文档门禁 | Done |
| PLATFORM-SCALE-S04-M4-T10 | 单人/多人/权限/刷新/恢复/版本无回退 | deterministic canonical seed、generation CAS、append conflict key 和非致命持久化失败处理 | collaboration 19/19；backend targeted tests 通过 | real：双用户标题、并发文本、格式、块移动/删除、持久化和版本流程通过 | Done |
| PLATFORM-SCALE-S04-M4-T11 | 双 collaboration 与旧链路关闭形成闭环 | 生产 Compose 双节点、Nginx least_conn、旧协议 cutover spec | light checkpoint `.local-reports/quality-gate-20260724T141234.md` 通过 | real：2/2 Playwright passed；两节点 failure 均为 0 | Done |

## Code Changes

- `collaboration/src/*`：多 API failover/readiness、generation/watermark 持久化、确定性 canonical seed、Redis/durable recovery 与节点指标。
- `server/.../knowledge/*`：generation-aware internal gateway、repository 和 diagnostics；移除旧协同 service/worker。
- `server/.../shared/websocket/*`：旧知识命令升级响应与低基数迁移观测；移除旧 handler。
- `server/src/main/resources/db/migration/V071__retire_legacy_spring_collaboration_state.sql`：迁移活动 schema 并兼容 PostgreSQL 截断约束名。
- `deploy/docker-compose.prod.yml`、`deploy/nginx/colla.conf`：双 collaboration 与多 API 后端配置。
- `web/e2e/platform-scale-s04-m4-collaboration-cutover.spec.ts`：真实旧协议关闭合同。
- 架构文档：记录唯一知识协同协议、责任边界、generation 和恢复语义。

## Validation

- Backend tests: `PlatformWebSocketHandlerTests`、`KnowledgeCollaborationGatewayServiceTests`、`KnowledgeSchemaMigrationIntegrationTests`、`RuntimeRoleBeanContractTests`、`ModuleArchitectureTests` 通过；后端 compile 通过。
- Frontend build: production lint、TypeScript/Vite build、chunk budget 与 lazy route 检查均通过。
- Migration: fresh V001→V071 与 V049→V071 upgrade 均通过；隔离生产库由 Maintenance 从 v070 升到 v071。
- Collaboration: 19 tests passed，覆盖 API failover、协议、generation、双节点、Redis 降级、节点重启、invalidate、确定性初始加载和持久化失败隔离。
- Local quality gate: light checkpoint `.local-reports/quality-gate-20260724T141605.md` 与 stage finish `.local-reports/quality-gate-20260724T142358.md` 均通过。
- Browser smoke: `kb-product-m5-collaboration.spec.ts` 与 `platform-scale-s04-m4-collaboration-cutover.spec.ts` 2/2 passed against `http://127.0.0.1:28080`。
- Runtime: collaboration-a/b 均 healthy、Redis ready、各累计接入 1、真实 failure 0；B 正确记录 1 次被水位线抑制的 stale snapshot。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M5-T05 | Redis 整体中断期间的业务写入与四域 REST 校准尚未执行最终组合演练 | deferred | M5 fault matrix |
| PLATFORM-SCALE-S04-M5-T06 | 慢客户端、连接突发和资源预算尚未执行 Stage 级验收 | deferred | M5 bounded-resource drill |
| PLATFORM-SCALE-S04-M5-T07 | collaboration 节点退出、Redis 整体中断与 PostgreSQL 恢复需在同一最终矩阵重跑 | deferred | M5 route-final |

## Next Steps

- 按独立工作循环推进 PLATFORM-SCALE-S04-M5-T01 到 T10，执行完整测试、故障矩阵、runbook 和 Stage route-final 收口。
