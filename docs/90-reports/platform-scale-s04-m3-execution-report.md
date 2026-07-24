# PLATFORM-SCALE-S04-M3 Execution Report

## Scope

PLATFORM-SCALE-S04-M3-T01 到 PLATFORM-SCALE-S04-M3-T11。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M3-T01 | static | not-required | not-required | No | 当前 hook、页面订阅、缓存副作用和断线缺口形成唯一清单 |
| PLATFORM-SCALE-S04-M3-T02 | unit | not-required | not-required | No | 严格 v1 parser 拒绝非法、未知版本、超限和敏感 payload |
| PLATFORM-SCALE-S04-M3-T03 | unit | not-required | not-required | No | 状态机、退避、抖动、online/offline、retry 和 stop 使用可控时钟验证 |
| PLATFORM-SCALE-S04-M3-T04 | unit | not-required | not-required | No | watermark、duplicate、stale 和 gap 决策以确定性序列验证 |
| PLATFORM-SCALE-S04-M3-T05 | integration | not-required | not-required | No | 通知列表与未读数仅在全部请求成功后原子替换 |
| PLATFORM-SCALE-S04-M3-T06 | integration | not-required | not-required | No | IM 以 afterSeq 分页补齐超过 100 条并防止重复、停滞和无限翻页 |
| PLATFORM-SCALE-S04-M3-T07 | e2e-real-isolated | real | isolated | No | 项目、权限和身份信号只执行固定查询映射；真实资源授权与恢复验证安全校准 |
| PLATFORM-SCALE-S04-M3-T08 | integration | not-required | not-required | No | 账号、workspace、设备和跨标签页上下文变化清理旧连接与缓存 |
| PLATFORM-SCALE-S04-M3-T09 | e2e-real-isolated | real | isolated | No | 真实生产页面持续降级可见、恢复不阻断主流程 |
| PLATFORM-SCALE-S04-M3-T10 | e2e-real-isolated | real | isolated | No | 重复、乱序、gap、离线丢信号和权限变化最终与 REST 一致 |
| PLATFORM-SCALE-S04-M3-T11 | e2e-real-isolated | real | isolated | No | 双标签页断线重连、四域 durable 校准与双 Gateway 单节点回退 |

## Completed Items

- 统一应用级 realtime provider，页面不再各自建立 WebSocket 或解析原始事件。
- 实现严格 v1 parser、敏感字段和 payload 尺寸防线，并分别保留 `serverTime` 与 `occurredAt`。
- 实现 connecting/ready/degraded/reconnecting/stopped 状态机、指数退避、抖动、在线恢复、显式 retry/stop 和 ready 超时。
- 实现 event id 有界去重以及按 sequence scope/key 的 watermark、stale、duplicate 和 gap 决策。
- 通知列表/未读数采用原子 REST 校准；IM 采用 afterSeq 增量分页并覆盖超过 100 条消息。
- 项目、权限和身份信号使用固定客户端查询映射；权限收紧先清理受保护缓存，再以 401/403/404 决定退出。
- 登录、登出、workspace、账号、设备和 storage 跨标签页变化会重建 realtime 上下文并清理旧 QueryClient 状态。
- 提供延迟显示的低噪音降级横幅、重试操作和短暂恢复提示。
- 生产构建的双标签页浏览器离线恢复、持久事实校准及双 Gateway 故障回退均已真实验证。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M3-T01 | 消费者、副作用和缺口可定位 | `AppRealtimeBoundary` 取代通知/IM 页面本地 socket，`collaClient` 保留兼容 facade | shared websocket/realtime import 与页面订阅复核 | not-required: 静态消费边界 | Done |
| PLATFORM-SCALE-S04-M3-T02 | 原始 JSON 只解析一次且未知输入安全 | `protocol.ts`、`RealtimeProvider.tsx`、`WebSocketEventPayload` | realtime core contract 与 3 个 WebSocket payload/backend tests 通过 | not-required: parser 合同 | Done |
| PLATFORM-SCALE-S04-M3-T03 | 状态唯一且无重连风暴 | `connection.ts` 的 generation、ready timer、backoff/jitter 与 online source | realtime core contract 覆盖 ready、retry、offline、stop 和 timer cleanup | not-required: 可控时钟单元证据 | Done |
| PLATFORM-SCALE-S04-M3-T04 | duplicate/stale/gap 不错误更新 | `sequencer.ts` 的 LRU event id 与 scope/key watermark | realtime core contract 覆盖 accepted、duplicate、stale、gap | not-required: 确定性序列证据 | Done |
| PLATFORM-SCALE-S04-M3-T05 | 通知列表和未读数原子一致 | `notificationReconciliation.ts` 与通知 mutation 收口 | reconciliation contract 验证 partial failure 不污染缓存 | real: 离线授权通知恢复后以未读状态出现 | Done |
| PLATFORM-SCALE-S04-M3-T06 | IM 可补齐超过 100 条且不重复 | `messageReconciliation.ts` 与可配置 1..100 的 `listMessages` | reconciliation contract 覆盖多页、停滞和 max-page 防线 | real: 离线消息恢复后出现在原会话 | Done |
| PLATFORM-SCALE-S04-M3-T07 | 项目和权限按服务端事实收敛 | `projectReconciliation.ts`、active resource 复核和 protected cache 清理 | reconciliation contract 验证固定 query mapping、禁止执行 calibrationPath | real: 离线项目工作项和资源授权先经 API 证明 durable | Done |
| PLATFORM-SCALE-S04-M3-T08 | 上下文切换不串数据 | `authStore` contextVersion、`AuthenticatedRoot` storage listener、provider context key | realtime contract 验证 context reset；前端 build/typecheck 通过 | real: 同一用户两个标签页独立重连且路由不漂移 | Done |
| PLATFORM-SCALE-S04-M3-T09 | 短抖动低噪音、持续故障可重试 | `RealtimeHealthBanner` 的延迟显示、恢复提示和 retry | lint/build 通过，真实握手故障诊断中确认降级横幅 | real: 真实 Origin 拒绝时横幅可见，恢复后页面继续使用 | Done |
| PLATFORM-SCALE-S04-M3-T10 | 各异常最终与 REST 一致 | core、notification、message、project 三组合同与全局 calibration router | 前端合同 9/9、后端 WebSocket 定向 6/6 | real: 浏览器断网期间三项写入均先校验 durable，恢复后通知/IM 可见 | Done |
| PLATFORM-SCALE-S04-M3-T11 | 浏览器重连和 Gateway 回退闭环 | 生产 Compose 双 Gateway、统一客户端与 route-final spec | frontend lint/build、dual Gateway smoke、工作循环 checkpoint/finish | real: route-final 1/1 passed；双 Gateway 分布、优雅/强制退出、单节点回退通过 | Done |

## Code Changes

- `web/src/shared/realtime/*`：新增 parser、sequencer、连接状态机、Provider、Context 和状态组件。
- `web/src/app/realtime/*`：新增全局业务信号路由、按域 REST 校准和低噪音健康横幅。
- `web/src/modules/{notifications,messenger,projects}/realtime/*`：新增通知原子替换、IM 增量分页、项目/权限固定映射。
- `web/src/modules/auth/*` 与应用布局：绑定账号/workspace/设备上下文并处理跨标签页 storage 变化。
- `server/.../WebSocketEventPayload`：同时发送 ISO `serverTime` 与原始 `occurredAt`，避免客户端丢弃合法 v1 frame。
- `web/e2e/platform-scale-s04-m3-*.spec.ts`：增加 core、reconciliation 与真实浏览器恢复证据。

## Validation

- Backend tests: 6 targeted WebSocket tests, 0 failures, 0 errors, 0 skipped.
- Frontend build: production build and lint passed.
- Local quality gate: realtime core and reconciliation contracts passed 9/9；通过的 checkpoint 报告为 `.local-reports/quality-gate-20260724T130433.md`，finish 在本报告完成后重验同一门禁。
- Browser smoke: real isolated `platform-scale-s04-m3-client-recovery.spec.ts` passed 1/1 against production Web、Nginx、双 API/Worker/Gateway；`dual-gateway-smoke.mjs` passed distribution, graceful/forced exit, recovery and single-node fallback.
- Browser diagnosis: first run exposed stale Nginx upstream after container replacement; second exposed production-only diagnostics misuse; third exposed isolated Origin omission; final black-box test uses actual `connection.ready` and socket close frames instead of production internals.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M4-T04 | legacy v0 frame 与旧 Spring 知识协同仍在兼容窗口 | deferred | M4 观测、关闭与删除 |
| PLATFORM-SCALE-S04-M5-T04 | 四域在单 Gateway 退出瞬间的最终组合演练留到 Stage 收口重跑 | deferred | M5 route-final 故障矩阵 |

## Next Steps

- 按独立工作循环推进 PLATFORM-SCALE-S04-M4-T01 到 T11。
