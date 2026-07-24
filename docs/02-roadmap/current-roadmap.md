---
title: PLATFORM-SCALE-S04 通用实时事件网关与知识协同收口当前执行路线
status: active
route: PLATFORM-SCALE-S04
program: PLATFORM-SCALE
program_doc: docs/00-product/initiatives/platform-scale-program.md
program_revision: 7
stage: PLATFORM-SCALE-S04
stage_final_milestone: PLATFORM-SCALE-S04-M5
last_code_check: 2026-07-24
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PLATFORM-SCALE-S04 通用实时事件网关与知识协同收口

## 1. Stage 目标

在 S02 已隔离 Event Gateway 运行角色、S03 已交付可靠多 Worker 和 durable realtime signal 的基础上，把当前只在单 Spring 进程内查找 session 并发送消息的通用 WebSocket 升级为可独立部署、可横向增加节点的实时事件网关。

S04 交付版本化实时信号 envelope、Redis pub/sub fanout、本地 session registry、双 Gateway 部署、IM/通知/项目/权限信号迁移、客户端 sequence/去重/重连/REST 校准，以及旧 Spring 知识 room/presence/autosave 链路退出。知识内容继续由独立 Hocuspocus/Yjs 组件负责实时协同，PostgreSQL snapshot/update 保持 durable source。S04 不把 Redis 作为业务事实，不拆业务微服务，也不提前发布 S05 的生产容量、长稳或基础设施高可用承诺。

## 2. 固定输入与当前缺口

- 上一 Stage：`PLATFORM-SCALE-S03` 已完成并归档为 `docs/99-archive/superseded-roadmaps/platform-scale-s03-roadmap-completed-2026-07-24.md`。
- 活动专项：`docs/00-product/initiatives/platform-scale-program.md` revision 7。
- 目标架构：`docs/01-architecture/platform-scale-target-architecture.md` revision 7，重点执行通用实时事件、知识协同、观测和故障矩阵章节。
- 运行基线：同一 Server artifact 已支持 `event-gateway` 角色；API/Worker 不创建本地 session registry，`combined` 只用于 local/test。
- 可靠信号基线：S03 已将业务事实转为版本化 Handler delivery，并把低延迟提示持久化为可观察 realtime signal；尚无 Redis transport publisher/consumer。
- 当前 Gateway 事实：`WebSocketSessionRegistry` 只保存当前进程 user-session 映射；`WebSocketMessageSender` 直接遍历本地 session；没有跨节点 topic、订阅、sequence watermark、慢客户端隔离或双节点 fanout。
- 当前客户端事实：WebSocket 能自动连接，但各业务域的去重、水位、退避、切换 workspace 和 REST 校准合同尚未统一；瞬时信号丢失可能只能依赖用户刷新。
- 当前知识协同事实：Hocuspocus 已有 ticket、Redis 和 durable update 能力；Spring 仍保留 `CollaborationMessageHandler`、旧 room/presence/autosave/maintenance 路径，存在双协议理解负担。
- 数据基线：V001-V069；S04 如需新 schema 必须兼容空库和旧库升级，不修改既有迁移。
- 边界基线：project/shared P0 与 foreign write 保持 0；批准的跨 owner read 不能因 Gateway 或协同收口扩散。
- 决策边界：S03 Go/No-Go 已批准 S04；`PROJECT-PLATFORM` 继续暂停在 S05 之前，S04 不恢复项目专项。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、Acceptance Evidence 和执行报告行。
2. PostgreSQL 中的通知、消息、项目、权限和知识 durable state 是事实源；Redis 与 WebSocket 只提供低延迟失效提示。
3. 实时 envelope 必须具备稳定 type/version、workspace、recipient/audience、object、sequence、occurredAt、correlation 和最小 payload；禁止携带 token、正文、隐藏标题或 ACL 快照。
4. Worker 只发布 transport-neutral signal；Gateway 只订阅、筛选并发送本地 session，不直接访问业务私有 Repository 或执行业务写事务。
5. 每个 Gateway 节点只保存本地连接。跨节点广播通过 Redis channel 完成，不引入粘性会话，也不在 Redis 保存唯一未读或消息事实。
6. 客户端必须按 domain/object sequence 去重；连接成功、断线重连、sequence gap、workspace 切换和权限变化均触发显式 REST 校准。
7. Redis 中断不得破坏业务写入；Gateway 明确降级 readiness/指标，恢复后客户端通过校准收敛，不伪造补发历史 Redis 消息。
8. 慢客户端、发送异常和无效 session 必须隔离并有界处理，不能阻塞订阅线程或其他用户。
9. Hocuspocus/Yjs 是知识实时协议唯一主链路；Spring 仅保留权限 ticket、load/store/invalidate gateway，不保留第二套 room/presence/autosave 协议。
10. M1-M4 使用影响范围验证；M5 执行完整后端、迁移、前端、collaboration、工作台、安全、真实双 Gateway/双 collaboration 浏览器与 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M1 | 通用实时 envelope、Redis fanout 与双 Gateway 基础 | S03 归档与 revision 7 | `docs/90-reports/platform-scale-s04-m1-execution-report.md` | Pending |
| PLATFORM-SCALE-S04-M2 | IM、通知、项目与权限信号迁移 | M1 | `docs/90-reports/platform-scale-s04-m2-execution-report.md` | Pending |
| PLATFORM-SCALE-S04-M3 | 客户端 sequence、去重、重连与 REST 校准 | M1-M2 | `docs/90-reports/platform-scale-s04-m3-execution-report.md` | Pending |
| PLATFORM-SCALE-S04-M4 | Spring 旧知识协同观测、关闭与删除 | M1-M3 | `docs/90-reports/platform-scale-s04-m4-execution-report.md` | Pending |
| PLATFORM-SCALE-S04-M5 | 多 Gateway、多 collaboration 故障验收与 Stage 收口 | M1-M4 | `docs/90-reports/platform-scale-s04-m5-execution-report.md` | Pending |

## 5. 详细任务

### PLATFORM-SCALE-S04-M1 通用实时 envelope、Redis fanout 与双 Gateway 基础

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M1-T01 | 复核 realtime signal 生产、持久化、当前 WebSocket 发送、session registry、认证和部署路径 | 每个 signal type、生产 Handler、recipient/audience、当前 sender、事实校准入口和跨节点缺口均可定位 | Pending |
| PLATFORM-SCALE-S04-M1-T02 | 冻结版本化 realtime envelope、topic、channel 和兼容策略 | type/version、workspace、recipient/audience、object、sequence、occurredAt、correlation、payload 与未知版本行为定义唯一 | Pending |
| PLATFORM-SCALE-S04-M1-T03 | 建立 transport-neutral publisher/consumer 公共合同与模块边界 | Worker 不 import Redis/Gateway 私有实现；Gateway 不 import业务 Repository；shared 不新增业务反向依赖 | Pending |
| PLATFORM-SCALE-S04-M1-T04 | 实现 realtime signal 到 Redis 的可靠发布适配与结果观测 | durable signal 成功后发布；Redis 失败不回滚业务事实并可重试/诊断；重复发布由 sequence 去重 | Pending |
| PLATFORM-SCALE-S04-M1-T05 | 实现 Gateway Redis subscriber、channel 生命周期和本地 fanout dispatcher | 每个 Gateway 都接收目标 channel；只向本地匹配 session 发送；重订阅不重复注册监听器 | Pending |
| PLATFORM-SCALE-S04-M1-T06 | 升级本地 session registry 的连接、用户、workspace、设备和生命周期合同 | 注册/注销/重复连接/关闭竞态安全；workspace 目标筛选明确；节点退出关闭连接并要求重连 | Pending |
| PLATFORM-SCALE-S04-M1-T07 | 实现 recipient/workspace audience 解析、权限安全降级和最小披露 | 用户定向与 workspace 广播无串租户；删除/无权对象不泄露标题、正文、ACL 或目标成员清单 | Pending |
| PLATFORM-SCALE-S04-M1-T08 | 实现发送队列、慢客户端隔离、失败清理和有界资源保护 | 单连接发送串行且有界；慢/坏连接不阻塞订阅线程；达到预算后安全关闭并记录原因 | Pending |
| PLATFORM-SCALE-S04-M1-T09 | 增加 Redis、连接、订阅、fanout、丢弃、发送失败、慢客户端和重连指标 | 指标按 role/instance/type 聚合，不使用 user/workspace 高基数或 payload 标签；日志可按 correlation 定位 | Pending |
| PLATFORM-SCALE-S04-M1-T10 | 配置 `gateway-a`/`gateway-b` 同镜像、独立 instance id、共享 Redis 的生产模板 | 两节点不承载业务 Controller/Worker/知识协议；upstream 无粘性会话；可独立扩缩和回退单 Gateway | Pending |
| PLATFORM-SCALE-S04-M1-T11 | 建立 envelope、边界、双 Gateway fanout、重复、隔离、慢客户端和角色矩阵测试并收口 M1 | 同一用户连接不同节点均收到一次信号；Redis/发送异常不破坏事实；目标门禁和 checkpoint 通过 | Pending |

### PLATFORM-SCALE-S04-M2 IM、通知、项目与权限信号迁移

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M2-T01 | 建立 IM、通知、项目、权限及遗留 sender 调用的实时副作用矩阵 | 每个业务动作、durable fact、signal type/version、recipient、sequence、REST 校准和现有直接发送路径明确 | Pending |
| PLATFORM-SCALE-S04-M2-T02 | 冻结各业务域最小 payload、sequence 来源、失效语义和兼容窗口 | payload 只含安全定位字段；sequence 可比较；旧/未知事件安全忽略并触发校准，不靠时间戳猜顺序 | Pending |
| PLATFORM-SCALE-S04-M2-T03 | 将通知创建、已读和未读数变化迁移到通用 signal pipeline | 通知事实先落库；多节点只提示变化；重复 Handler/发布不重复增加未读数 | Pending |
| PLATFORM-SCALE-S04-M2-T04 | 将 IM 消息、会话更新和消息转任务提示迁移到通用 signal pipeline | 消息事实和会话水位来自 PostgreSQL；发送者/接收者目标正确；重复信号不重复插入消息 | Pending |
| PLATFORM-SCALE-S04-M2-T05 | 将项目工作项、评论、成员和状态变化迁移到通用 signal pipeline | 项目事件只携带对象标识/version；权限仍按当前事实判断；跨 workspace 不广播 | Pending |
| PLATFORM-SCALE-S04-M2-T06 | 将权限、角色、成员状态和资源授权失效提示迁移到通用 signal pipeline | 权限收紧后客户端及时校准；signal 不复制完整授权关系；无权用户只收到安全失效提示 | Pending |
| PLATFORM-SCALE-S04-M2-T07 | 从业务模块和 Worker 移除对本地 `WebSocketSessionRegistry`/sender 的直接依赖 | API/Worker 无本地 session 操作；业务模块只依赖公开 signal contract；架构门禁无新增例外 | Pending |
| PLATFORM-SCALE-S04-M2-T08 | 建立旧 WebSocket event type 到新 envelope 的短期兼容与退出开关 | 升级窗口内旧客户端可安全工作；兼容层可观测、默认期限明确，不形成第二事实源 | Pending |
| PLATFORM-SCALE-S04-M2-T09 | 复验用户多设备、离线、重复连接、workspace 切换和删除/停用场景 | 目标设备与用户行为一致；离线不丢 durable fact；停用/删除不会继续暴露资源内容 | Pending |
| PLATFORM-SCALE-S04-M2-T10 | 建立四业务域 Handler、幂等、顺序、权限、敏感字段和跨节点集成测试 | 任一 signal 发布失败不阻塞其他 Handler；重复/乱序安全；两个 Gateway 收敛到同一业务事实 | Pending |
| PLATFORM-SCALE-S04-M2-T11 | 完成真实 IM/通知/项目/权限流程、指标、边界和 M2 收口 | 四域真实产品流均经通用 pipeline 到达正确客户端；目标测试、浏览器 smoke 和 checkpoint 通过 | Pending |

### PLATFORM-SCALE-S04-M3 客户端 sequence、去重、重连与 REST 校准

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M3-T01 | 复核当前 WebSocket hook、业务页面订阅、缓存更新、错误提示和重连行为 | 每个 event type 的消费者、缓存副作用、断线行为、重复风险和校准缺口可定位 | Pending |
| PLATFORM-SCALE-S04-M3-T02 | 实现统一客户端 realtime envelope parser、版本校验和类型路由 | 非法/未知/超限 payload 不影响连接；已知版本类型安全；业务页面不重复解析原始 JSON | Pending |
| PLATFORM-SCALE-S04-M3-T03 | 实现连接状态机、指数退避、抖动、在线恢复和显式停止 | connecting/ready/degraded/reconnecting/stopped 状态唯一；无重连风暴；登出后不再自动连接 | Pending |
| PLATFORM-SCALE-S04-M3-T04 | 实现按 domain/object 的 sequence watermark、去重和 gap 检测 | 重复/旧信号不重复更新；sequence gap 不猜测缺失内容并立即标记对应域待校准 | Pending |
| PLATFORM-SCALE-S04-M3-T05 | 建立通知未读/列表 REST 校准和缓存原子替换 | 首连、重连、gap 和窗口重新聚焦后未读数/列表与服务端事实一致 | Pending |
| PLATFORM-SCALE-S04-M3-T06 | 建立 IM 会话/消息增量校准与分页水位合同 | 不重复消息、不跳页；断线期间新消息通过 REST 补齐；删除/撤回按服务端事实收敛 | Pending |
| PLATFORM-SCALE-S04-M3-T07 | 建立项目对象和权限失效校准合同 | 工作项变化按对象 version 刷新；权限收紧清除缓存并安全退出资源页；无权状态不显示旧内容 | Pending |
| PLATFORM-SCALE-S04-M3-T08 | 实现 workspace、账号、设备和标签页切换时的订阅与水位重置 | 旧 workspace/账号事件不能污染新上下文；多标签页行为可解释且不会无限重复校准 | Pending |
| PLATFORM-SCALE-S04-M3-T09 | 提供低噪音连接状态、降级和恢复交互 | 短暂抖动不打断主流程；持续降级可见且可重试；恢复后提示与事实状态一致 | Pending |
| PLATFORM-SCALE-S04-M3-T10 | 建立重复、乱序、gap、Redis 丢信号、Gateway 切换和权限变化前端测试 | 缓存最终与 REST 一致；无永久未读、消息、项目或权限缺口；计时器和连接正确清理 | Pending |
| PLATFORM-SCALE-S04-M3-T11 | 完成真实浏览器断线/重连/校准、多标签页和 M3 收口 | 停止单 Gateway 后客户端连接另一节点并在冻结时间内完成四域校准；构建和 checkpoint 通过 | Pending |

### PLATFORM-SCALE-S04-M4 Spring 旧知识协同观测、关闭与删除

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M4-T01 | 复核 Spring 与 Hocuspocus 两条知识协同链路、schema、任务、ticket 和客户端入口 | room/presence/update/snapshot/autosave/cleanup/权限/连接的当前 owner、调用量和替代路径完整 | Pending |
| PLATFORM-SCALE-S04-M4-T02 | 冻结知识协同唯一协议、durable source、权限和组件责任矩阵 | Hocuspocus/Yjs 负责实时协议；PostgreSQL snapshot/update 是事实；Spring 只保留 ticket/load/store/invalidate | Pending |
| PLATFORM-SCALE-S04-M4-T03 | 为旧 Spring 协同入口、room、presence、update 和 maintenance 增加迁移观测 | 可区分真实旧流量、测试和内部调用；指标无用户/文档高基数；零使用判断有明确观察窗口 | Pending |
| PLATFORM-SCALE-S04-M4-T04 | 实现旧协议关闭开关、拒绝语义和安全回退演练 | 关闭后旧连接明确升级提示，不静默写第二份状态；回退只恢复兼容入口，不改变 durable 数据 | Pending |
| PLATFORM-SCALE-S04-M4-T05 | 验证 Hocuspocus ticket、权限收紧、load/store/invalidate 与版本一致性 | ticket 单次/短时且绑定文档权限；撤权及时断开/拒写；更新和 snapshot sequence 单调 | Pending |
| PLATFORM-SCALE-S04-M4-T06 | 验证 collaboration Redis 多节点广播、awareness 和 durable recovery | 两节点同文档编辑收敛；awareness 不持久化为业务事实；Redis 丢失后从 PostgreSQL 恢复内容 | Pending |
| PLATFORM-SCALE-S04-M4-T07 | 移除 Spring `CollaborationMessageHandler`、旧 room/presence/autosave/cleanup 运行链路 | Event Gateway 不再处理知识编辑命令；API/Worker 无旧 scheduler 或内存 room 状态 | Pending |
| PLATFORM-SCALE-S04-M4-T08 | 收敛旧配置、Controller/DTO/service/repository/schema 活动引用和前端兼容入口 | 活动代码只保留 Hocuspocus gateway 必需面；旧表按数据策略迁移/冻结/删除且历史 Flyway 不改写 | Pending |
| PLATFORM-SCALE-S04-M4-T09 | 更新角色 Bean 矩阵、架构合同、命名门禁和运行文档 | 生产角色不再实例化旧协议 Bean；知识协同唯一入口自动可验证；无新增跨 owner write | Pending |
| PLATFORM-SCALE-S04-M4-T10 | 建立单人、多用户、权限、刷新、离线恢复、版本和双节点协同回归 | 内容无丢失/重复/回退；评论/版本/权限主流程不受退出旧链路影响 | Pending |
| PLATFORM-SCALE-S04-M4-T11 | 完成真实双 collaboration 浏览器流程、旧链路零调用证明和 M4 收口 | Hocuspocus 是唯一知识实时协议；目标后端/collaboration/浏览器/架构门禁和 checkpoint 通过 | Pending |

### PLATFORM-SCALE-S04-M5 多 Gateway、多 collaboration 故障验收与 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S04-M5-T01 | 复核 M1-M4 的 44 项实现任务与 M5 的 10 项收口合同 | 路线共 54 项均有唯一可重复证据；无静默跳过、阻断残留或越界 S05 容量实现 | Pending |
| PLATFORM-SCALE-S04-M5-T02 | 执行 V001 至最新空库、旧库升级、并发启动和兼容 migration rehearsal | API/Worker/Gateway/collaboration 使用同一最新 schema；迁移只由 Maintenance 执行；fresh/upgrade 通过 | Pending |
| PLATFORM-SCALE-S04-M5-T03 | 执行双 Gateway 跨节点用户/workspace fanout、重复和隔离回归 | 同一信号在每个目标连接最多一次；非目标/跨租户为零；两个节点指标和 correlation 可定位 | Pending |
| PLATFORM-SCALE-S04-M5-T04 | 执行 Gateway 优雅/强制退出、客户端重连和四域 REST 校准 | 新连接切换节点；断线期间 durable 变化全部收敛；无永久未读、消息、项目或权限缺口 | Pending |
| PLATFORM-SCALE-S04-M5-T05 | 执行 Redis 中断、恢复、重订阅和信号丢失故障注入 | 业务写入继续；Gateway 显式降级；恢复后无订阅泄漏/重复 fanout，客户端校准到事实状态 | Pending |
| PLATFORM-SCALE-S04-M5-T06 | 执行慢客户端、发送异常、连接突发和有界资源恢复测试 | 慢连接被隔离；健康连接延迟不被拖垮；队列、线程、内存和连接预算不无限增长 | Pending |
| PLATFORM-SCALE-S04-M5-T07 | 执行双 collaboration 节点编辑、节点退出、Redis 中断和 PostgreSQL 恢复 | Yjs 内容最终一致；awareness 可短暂降级；节点恢复后从 durable update/snapshot 收敛且不双写 | Pending |
| PLATFORM-SCALE-S04-M5-T08 | 编写 Gateway/collaboration 部署、扩缩、指标、告警、故障、回退和校准 runbook | 操作者无需访问私表即可诊断和恢复；危险操作有确认/审计；可回退单节点但不恢复旧 Spring 协议 | Pending |
| PLATFORM-SCALE-S04-M5-T09 | 更新 Program、目标架构、当前事实和专项索引并输出 S05/PROJECT Go/No-Go | revision 与状态一致；明确进入容量收口、暂停专项恢复项目或补充 S04，不提前激活下一 Stage | Pending |
| PLATFORM-SCALE-S04-M5-T10 | 完成 S04 报告、影响审计和路线级 `route-final` | 完整后端、迁移、前端、collaboration、工作台、安全、真实双 Gateway/双协同故障证据 fresh 通过 | Pending |

## 6. Stage 全局验收标准

- realtime envelope 具备稳定 type/version、workspace、recipient/audience、object、sequence、correlation 和最小敏感字段边界。
- Worker 只产生 transport-neutral signal；Gateway 只订阅 Redis 并向本地 session fanout，API/Worker 不直接操作本地连接。
- 两个 Gateway 使用相同 artifact、独立 instance id 和共享 Redis，可单独扩缩，不依赖粘性会话。
- Redis 不是业务事实；Redis 中断不回滚 HTTP/Worker durable fact，客户端恢复后通过 REST 校准收敛。
- IM、通知、项目和权限全部经通用 signal pipeline；重复/乱序信号不重复业务副作用或泄露资源。
- 客户端有统一连接状态机、退避、sequence watermark、去重、gap 检测和按域 REST 校准。
- Gateway 节点退出后客户端连接另一节点，并在冻结时间内恢复未读、消息、项目和权限事实。
- 慢客户端、坏连接和发送失败有界隔离；订阅线程、内存、队列和连接不会无限增长。
- Hocuspocus/Yjs 是唯一知识实时协议；Spring 只保留 ticket/load/store/invalidate gateway，无旧 room/presence/autosave 运行链路。
- 双 collaboration 节点通过 Redis 收敛实时 update/awareness，并能从 PostgreSQL durable update/snapshot 恢复。
- Event Gateway 与 collaboration 分别提供 role/instance 级连接、fanout、重连、校准、room、update、恢复和 Redis 指标。
- S01-S03 边界保持：project/shared P0 与 foreign write 为 0，批准的历史 read 例外不能扩散。
- S04 的多节点与故障数据只证明功能和恢复门槛，不构成 S05 生产容量、长稳或基础设施 HA 承诺。
- M5 最终执行完整后端、迁移、前端、collaboration、工作台、安全、真实隔离浏览器和 `route-final`。

## 7. 当前执行入口

从 `PLATFORM-SCALE-S04-M1-T01` 开始，按 Milestone 分轮执行。S04 激活不代表 S05 或 `PROJECT-PLATFORM-S05` 已启动。
