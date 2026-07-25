---
title: PLATFORM-SCALE-S05 容量、故障、恢复和运维收口暂停归档
status: paused
route: PLATFORM-SCALE-S05
program: PLATFORM-SCALE
program_doc: docs/00-product/initiatives/platform-scale-program.md
program_revision: 9
stage: PLATFORM-SCALE-S05
stage_final_milestone: PLATFORM-SCALE-S05-M5
last_code_check: 2026-07-25
paused_at: 2026-07-25
pause_reason: M1 容量底座完成；M2-M5 延后到核心功能、接口、数据模型和负载模型稳定后恢复。
source_rule: 本文件是暂停历史证据，不是当前执行入口。
---

# PLATFORM-SCALE-S05 容量、故障、恢复和运维收口

## 1. Stage 目标

在 S01-S04 已完成模块边界门禁、双 API、可靠多 Worker、双 Event Gateway、双 collaboration 和客户端事实校准的基础上，为当前单企业部署形态建立可重复、可解释且不夸大的容量与运维承诺。

S05 分阶段交付固定容量环境、确定性数据种子、锁定版本的 HTTP/WebSocket/Yjs/Worker 负载器、单域和混合目标负载、60 分钟目标负载、8 小时低强度 soak、具名节点与依赖故障恢复、发布/扩缩/回退/诊断手册、历史边界例外到期复核，以及专项最终 Go/No-Go。容量结论只对明确记录的硬件、容器、数据和拓扑有效；S05 不把 PostgreSQL、Redis 或 MinIO 单点描述为高可用，也不以调整门槛掩盖未达到的候选目标。当前只完成 M1 容量验证底座；M2-M5 在核心功能、接口、数据模型和负载模型稳定前暂停。

## 2. 固定输入与当前缺口

- 上一 Stage：`PLATFORM-SCALE-S04` 已完成并归档为 `docs/99-archive/superseded-roadmaps/platform-scale-s04-roadmap-completed-2026-07-25.md`。
- 活动专项：`docs/00-product/initiatives/platform-scale-program.md` revision 9。
- 目标架构：`docs/01-architecture/platform-scale-target-architecture.md` revision 9，重点执行容量验收草案、S05 激活合同、观测与运维边界。
- 运行基线：同一 Server artifact 已支持独立 `api`、`worker`、`event-gateway`、`maintenance` 角色；生产模板已有双 API、双 Worker、双 Gateway 和双 collaboration。
- 事实边界：PostgreSQL 是业务、outbox、delivery、realtime signal 与知识 durable state 的事实源；Redis 是跨节点瞬时 fanout，MinIO 是文件对象存储。
- 当前证据边界：S02-S04 的性能数字只证明对应功能和恢复门槛，尚未绑定固定硬件、完整混合负载、60 分钟目标持续负载或 8 小时 soak。
- 候选 C1：2,000 注册成员、500 在线成员、150 HTTP RPS、1,000 普通 WS、100 协同客户端、25 协同房间、30 events/s 持续与 150 events/s 五分钟突发；M1 必须复核后才能冻结为目标。
- 数据候选：1,000,000 工作项、100,000 知识节点、1,000,000 知识块；必须通过可重复 manifest 和清理合同建立，不能依赖手工脏数据。
- 候选服务门槛：HTTP read P95 300 ms、write P95 500 ms、非预期 5xx 低于 0.5%、fanout/协同 P95 1 s、重连校准 10 s、outbox oldest age P95 5 s；未达到时发布实际边界和瓶颈。
- 基础设施边界：PostgreSQL、Redis、MinIO 当前仍是单点故障域；S05 验证中断与恢复，不把集群 HA 作为已实现能力。
- 边界基线：project/shared P0 与 foreign write 保持 0；历史批准的跨 owner read 以当前扫描重新核对，禁止整批自动续期。
- 决策边界：S05 最终决定恢复 `PROJECT-PLATFORM-S05`、新增平台修复 Stage 或停止扩容承诺；当前路线不提前实现项目布局能力。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、Acceptance Evidence 和执行报告行。
2. 所有容量证据绑定提交、镜像、依赖版本、宿主机、容器限制、服务副本、运行参数、数据 manifest 和脚本校验和。
3. 候选 C1 不是预设通过结论。M1 可以基于资源和安全边界冻结目标、分级目标或明确降级，但不能在测试失败后静默改门槛。
4. 数据种子必须确定、幂等、跨 workspace 隔离、可清理且有规模/分布/校验和；禁止把共享开发库或人工数据作为正式容量输入。
5. 单域负载用于定位瓶颈，容量承诺必须来自 HTTP、Worker、普通 WS 和知识协同同时运行的混合场景。
6. 报告必须同时记录延迟分位数、错误率、吞吐、队列、backlog/oldest age、dead letter、连接/收敛、CPU、内存、GC、线程、连接池和依赖资源；平均值不能替代尾延迟。
7. 60 分钟目标负载和 8 小时 soak 必须保留完整时间序列、资源斜率、错误样本及前后数据一致性；进程存活不等于通过。
8. 故障注入必须具名、可重复并记录开始/恢复时间。恢复结论同时检查 RTO、事实缺口、重复副作用、授权隔离和资源泄漏。
9. PostgreSQL、Redis、MinIO 单点必须进入非承诺清单和恢复手册；不得用应用双节点证据推导基础设施高可用。
10. 发布、扩缩、降容和回退使用不可变 artifact、兼容 schema、连接预算和 draining；不得依赖手工改表、数据库回滚或恢复旧 Spring 协同。
11. M1-M4 使用影响范围验证；M5 执行完整后端、迁移、前端、collaboration、工作台、安全、容量证据完整性和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1 | 固定容量环境、数据种子、负载器与证据合同 | S04 归档与 revision 9 | `docs/90-reports/platform-scale-s05-m1-execution-report.md` | Completed |
| PLATFORM-SCALE-S05-M2 | HTTP、Worker、普通 WS 与知识协同目标负载 | M1；需满足恢复前置条件 | 恢复执行时创建 | Deferred |
| PLATFORM-SCALE-S05-M3 | 60 分钟目标负载、8 小时 soak 与故障恢复 | M1-M2；需满足恢复前置条件 | 恢复执行时创建 | Deferred |
| PLATFORM-SCALE-S05-M4 | 发布、扩缩、降容、回退与诊断运行闭环 | M1-M3；需满足恢复前置条件 | 恢复执行时创建 | Deferred |
| PLATFORM-SCALE-S05-M5 | 容量基线、边界例外复核与专项 Go/No-Go | M1-M4；需满足恢复前置条件 | 恢复执行时创建 | Deferred |

`Deferred` 不是完成态。M2-M5 在恢复决定生效前不得启动工作循环，也不得生成伪执行报告或以 M1 短时场景替代目标容量、长稳、故障恢复和最终 Go/No-Go 证据。

## 5. 详细任务

### PLATFORM-SCALE-S05-M1 固定容量环境、数据种子、负载器与证据合同

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M1-T01 | 复核当前 Compose、镜像、运行角色、资源限制、连接预算、指标、种子和压测入口 | 每个服务、副本、端口、依赖、资源、连接、观测入口、现有脚本与容量缺口均可定位 | Done |
| PLATFORM-SCALE-S05-M1-T02 | 冻结容量等级、C1 目标、服务指标、非目标和结论用语 | 候选值逐项确认或有依据调整；通过、未通过、部分通过和不可承诺语义唯一 | Done |
| PLATFORM-SCALE-S05-M1-T03 | 冻结宿主机、OS、Docker、CPU/内存/磁盘/网络、时钟和依赖版本清单 | 一条 preflight 命令生成机器可读 manifest；关键输入漂移会阻止结果并入同一基线 | Done |
| PLATFORM-SCALE-S05-M1-T04 | 冻结 API/Worker/Gateway/collaboration 副本、容器限制、JVM/Node 参数和连接预算 | 拓扑与资源公式可重复；总 PostgreSQL/Redis/MinIO 连接不越预算；非法配置启动前失败 | Done |
| PLATFORM-SCALE-S05-M1-T05 | 设计成员、权限、项目、知识、通知、IM、文件和协同的确定性数据分布 | 规模、倾斜、关系、workspace 数、热点/冷点、正文大小和权限组合有版本化 schema | Done |
| PLATFORM-SCALE-S05-M1-T06 | 实现幂等数据种子、校验、续跑和清理工具 | 相同 seed 产生相同标识与校验和；失败可续跑；只清理具名夹具；跨 workspace 无污染 | Done |
| PLATFORM-SCALE-S05-M1-T07 | 建立固定版本、容器化的 HTTP read/write 负载器 | 登录、查询、命令、幂等和上传场景参数化；响应语义与权限同时校验，不只统计状态码 | Done |
| PLATFORM-SCALE-S05-M1-T08 | 建立普通 WebSocket 连接、fanout、重连和校准负载器 | 可控制连接/用户/节点/消息率；验证 sequence、重复、gap 和 REST 收敛并输出分位数 | Done |
| PLATFORM-SCALE-S05-M1-T09 | 建立 Hocuspocus/Yjs 多房间、多客户端和编辑分布负载器 | 真实协议客户端可跨两个节点编辑、断开、重连并校验最终文档状态和收敛延迟 | Done |
| PLATFORM-SCALE-S05-M1-T10 | 建立 Worker 持续/突发生产、backlog、接管和结果校验负载器 | 可按 event/Handler/aggregate 生成具名负载；检测丢失、重复副作用、顺序和 dead letter | Done |
| PLATFORM-SCALE-S05-M1-T11 | 建立统一场景清单、预热/持续/中止规则、指标采集和证据包格式 | 每次运行产生版本、manifest、阈值、原始时序、摘要和校验和；中止原因不会被记为通过 | Done |
| PLATFORM-SCALE-S05-M1-T12 | 完成环境可重复性、种子隔离、负载器自检、安全和 M1 收口 | 两次干净初始化结果一致；负载器失败可被测试识别；目标门禁和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S05-M2 HTTP、Worker、普通 WS 与知识协同目标负载

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M2-T01 | 执行空闲、预热和稳定基线并校准观测开销 | 空闲资源、启动时间、预热收敛和指标抓取成本明确；测试前无历史 backlog 或残留连接 | Deferred |
| PLATFORM-SCALE-S05-M2-T02 | 执行混合 HTTP read 目标负载 | 150 RPS 候选下 read P50/P95/P99、错误、数据库与缓存指标完整；结果按端点和权限态可解释 | Deferred |
| PLATFORM-SCALE-S05-M2-T03 | 执行 HTTP write、幂等命令和文件元数据/对象混合负载 | write P95、冲突、outbox/audit、重复请求和 MinIO 结果一致；无部分业务事实 | Deferred |
| PLATFORM-SCALE-S05-M2-T04 | 执行 Worker 30 events/s 持续与 150 events/s 五分钟突发 | backlog、oldest age、吞吐、lease、retry、dead letter 和恢复时间达到冻结门槛或暴露实际拐点 | Deferred |
| PLATFORM-SCALE-S05-M2-T05 | 执行 1,000 普通 WS 连接、定向/workspace fanout 和重连负载 | fanout P95、连接成功率、队列和校准完整；无跨租户、重复投递或无界慢连接 | Deferred |
| PLATFORM-SCALE-S05-M2-T06 | 执行 100 协同客户端、至少 25 房间和跨节点编辑负载 | update 收敛 P95、持久化、room/connection/queue 指标完整；最终内容一致且无丢块 | Deferred |
| PLATFORM-SCALE-S05-M2-T07 | 执行 HTTP、Worker、Gateway 和 collaboration 同时运行的 C1 混合负载 | 各域同时达到冻结流量；共享依赖竞争可见；单域通过不能掩盖混合场景失败 | Deferred |
| PLATFORM-SCALE-S05-M2-T08 | 复验百万级工作项、知识节点/块规模下的分页、搜索、权限和索引路径 | 关键查询计划、尾延迟、索引命中和内存稳定；无全表回退、越权召回或无界响应 | Deferred |
| PLATFORM-SCALE-S05-M2-T09 | 在目标负载下执行跨 workspace、停用成员、撤权和资源删除隔离校验 | 错误授权为零；缓存和实时提示最终按当前事实收敛；敏感标题/正文不进入负载日志 | Deferred |
| PLATFORM-SCALE-S05-M2-T10 | 执行阶梯增压、饱和与安全降载，定位每个角色和共享依赖的拐点 | 饱和前后吞吐/尾延迟/错误/队列曲线完整；保护机制生效且不会级联耗尽依赖 | Deferred |
| PLATFORM-SCALE-S05-M2-T11 | 只通过版本化配置完成有预算的调优和对照复验 | 每项调优有假设、前后指标和资源代价；不删除安全校验、不扩大未审连接预算、不覆盖失败原始证据 | Deferred |
| PLATFORM-SCALE-S05-M2-T12 | 发布目标负载结果、实际容量包络、瓶颈和 M2 收口结论 | 原始证据可复算；候选目标逐项有 Pass/Fail/Bounded 结论；目标门禁和 checkpoint 通过 | Deferred |

### PLATFORM-SCALE-S05-M3 60 分钟目标负载、8 小时 soak 与故障恢复

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M3-T01 | 冻结长稳场景、故障时间线、RTO/RPO、允许失败和数据一致性清单 | 每个故障的触发、持续、恢复、观测和阻断条件唯一；禁止临场改变成功口径 | Deferred |
| PLATFORM-SCALE-S05-M3-T02 | 执行一轮 60 分钟目标混合负载 | 全时段阈值、资源斜率、错误样本、GC/线程/连接和前后事实校验完整，无隐藏重启或手工清障 | Deferred |
| PLATFORM-SCALE-S05-M3-T03 | 执行一轮 8 小时低强度 soak | 内存、连接、线程、room/session、队列、backlog 和存储增长有界；结束后数据与权限一致 | Deferred |
| PLATFORM-SCALE-S05-M3-T04 | 在负载中分别执行 API 优雅退出、强制退出和恢复 | 新请求继续；只允许定义的在途失败；幂等结果、登录状态和上传事实无节点依赖 | Deferred |
| PLATFORM-SCALE-S05-M3-T05 | 在 processing 与突发积压中执行 Worker 崩溃、lease 接管和恢复 | 旧 fencing 写入被拒绝；冻结 RTO 内接管；无丢失、双副作用或永久 processing | Deferred |
| PLATFORM-SCALE-S05-M3-T06 | 在连接和 fanout 负载中执行 Gateway 节点退出、重连和恢复 | 客户端切换节点并校准；无永久未读/消息/项目/权限缺口；恢复后无重复订阅 | Deferred |
| PLATFORM-SCALE-S05-M3-T07 | 中断并恢复 Redis，覆盖 Gateway 与 collaboration 跨节点路径 | durable 写入按合同继续或明确降级；恢复后订阅、awareness 和校准收敛，无伪造补发 | Deferred |
| PLATFORM-SCALE-S05-M3-T08 | 在多房间编辑中执行 collaboration 节点退出、重建和 durable reload | 内容最终一致；awareness 允许短暂丢失；pending update、snapshot 和 room 资源恢复有界 | Deferred |
| PLATFORM-SCALE-S05-M3-T09 | 中断并恢复 PostgreSQL，覆盖 API、Worker、Gateway 和 collaboration | readiness 正确降级；无部分提交；有界宽限/队列不越预算；恢复后 durable facts 一致 | Deferred |
| PLATFORM-SCALE-S05-M3-T10 | 中断并恢复 MinIO，覆盖上传、完成、下载和非文件业务 | 文件操作明确失败或续跑；无孤立已完成对象；非文件能力按合同继续且恢复后可校验 | Deferred |
| PLATFORM-SCALE-S05-M3-T11 | 重复关键故障并汇总 RTO/RPO、方差、永久缺口、重复副作用和资源泄漏 | 至少三次关键恢复结果可比较；异常轮次不删除；实际置信度与剩余单点明确 | Deferred |
| PLATFORM-SCALE-S05-M3-T12 | 完成长稳证据完整性、安全、数据清理和 M3 收口 | 原始时序、日志、manifest 与摘要校验和一致；夹具可清理；目标门禁和 checkpoint 通过 | Deferred |

### PLATFORM-SCALE-S05-M4 发布、扩缩、降容、回退与诊断运行闭环

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M4-T01 | 复核现有发布、备份恢复、Worker、Gateway、collaboration 和故障 runbook | 所有命令、权限、前置条件、危险操作、过期内容和手工私表步骤可定位 | Deferred |
| PLATFORM-SCALE-S05-M4-T02 | 冻结不可变 artifact、镜像摘要、配置/密钥、schema 和版本兼容矩阵 | 每个角色使用同一受审版本；配置漂移可检测；密钥不进入镜像、日志或证据包 | Deferred |
| PLATFORM-SCALE-S05-M4-T03 | 建立发布 preflight、Maintenance migration、兼容检查和中止合同 | 依赖/容量/备份/schema 不满足时在流量切换前失败；API/Worker/Gateway 不并发执行 Flyway | Deferred |
| PLATFORM-SCALE-S05-M4-T04 | 实现并演练 Web、API、Worker、Gateway、collaboration 与 Nginx 滚动发布顺序 | 逐角色 readiness/draining 生效；用户事实连续；失败步骤有明确停止点和回退入口 | Deferred |
| PLATFORM-SCALE-S05-M4-T05 | 实现并演练 API/Worker/Gateway/collaboration 扩容 | 扩容前连接与资源预算通过；新节点完成就绪再接流量；容量变化与指标可验证 | Deferred |
| PLATFORM-SCALE-S05-M4-T06 | 实现并演练各角色降容、draining 和单节点回退 | 停止 claim/接流量/接连接顺序正确；无未归属 delivery、僵尸 session 或未持久化 update | Deferred |
| PLATFORM-SCALE-S05-M4-T07 | 实现并演练应用版本回退与前后向兼容 | 不回滚已应用 schema；旧版本只在兼容窗口内恢复；不恢复旧 Spring 知识协议或本地事实 | Deferred |
| PLATFORM-SCALE-S05-M4-T08 | 复验备份、独立恢复、对象一致性和恢复后的容量前置条件 | PostgreSQL/MinIO/配置恢复点一致；恢复环境通过完整性检查后才允许重新承载目标流量 | Deferred |
| PLATFORM-SCALE-S05-M4-T09 | 将容量结果转化为 dashboard、告警、降级和升级阈值 | 告警绑定实际基线与持续窗口；覆盖尾延迟、错误、资源、backlog、连接、收敛和依赖状态 | Deferred |
| PLATFORM-SCALE-S05-M4-T10 | 建立按 correlation/instance/role 的诊断入口和安全操作者流程 | 无需读私表即可定位；危险命令要求确认、理由、权限和审计；证据不泄露租户数据 | Deferred |
| PLATFORM-SCALE-S05-M4-T11 | 完成发布、扩缩、降容、回退、恢复与告警综合 rehearsal 和 M4 收口 | 新操作者可按文档完成具名场景；自动断言识别失败；目标门禁和 checkpoint 通过 | Deferred |

### PLATFORM-SCALE-S05-M5 容量基线、边界例外复核与专项 Go/No-Go

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S05-M5-T01 | 复核 M1-M4 的 47 项实现任务与 M5 的 12 项收口合同 | 路线共 59 项均有唯一可重复证据；无静默跳过、过期长稳结果或越界项目功能实现 | Deferred |
| PLATFORM-SCALE-S05-M5-T02 | 复验提交、镜像、环境、资源、拓扑、种子和负载脚本 provenance | 所有发布结论能还原到不可变输入；漂移结果被隔离，不混入最终容量基线 | Deferred |
| PLATFORM-SCALE-S05-M5-T03 | 执行最终短时混合置信复验并核对 M2/M3 原始长稳证据 | 关键指标与已发布区间一致；长稳时序和校验和完整；异常或方差扩大触发阻断评审 | Deferred |
| PLATFORM-SCALE-S05-M5-T04 | 抽样重放节点/依赖故障、回退和独立恢复路径 | RTO/RPO、事实一致性、无重复副作用和操作者步骤与 M3/M4 结论一致 | Deferred |
| PLATFORM-SCALE-S05-M5-T05 | 执行完整后端、V001 至最新迁移、前端、collaboration、工作台和安全回归 | 全部门禁使用 fresh 证据通过；失败、跳过、豁免或生成物污染形成阻断决定 | Deferred |
| PLATFORM-SCALE-S05-M5-T06 | 发布带适用条件、分位数、置信度和实际拐点的容量基线 | 支持负载、环境、数据、SLO、资源、扩容公式和不支持场景清晰；不只给单一最大数字 | Deferred |
| PLATFORM-SCALE-S05-M5-T07 | 发布 PostgreSQL、Redis、MinIO 单点及其他非承诺清单 | 每个单点有影响、检测、恢复、RPO/RTO 证据和升级入口；应用双节点不被表述为基础设施 HA | Deferred |
| PLATFORM-SCALE-S05-M5-T08 | 重新生成跨模块/表 owner 边界清单并删除已失效例外 | 当前计数、精确路径/SQL、owner、方向和风险可重复；不存在的例外从 baseline 移除 | Deferred |
| PLATFORM-SCALE-S05-M5-T09 | 对仍存在的跨 owner read 逐项修复或重新批准 | 每项有业务原因、精确范围、责任 owner、风险和新的退出决定；foreign write 和新增未批例外为 0 | Deferred |
| PLATFORM-SCALE-S05-M5-T10 | 完成 PLATFORM-SCALE 专项和 PROJECT-PLATFORM 恢复 Go/No-Go | 明确恢复 PROJECT-PLATFORM-S05、增加平台修复 Stage 或暂停；依据、阻断和下一入口可执行 | Deferred |
| PLATFORM-SCALE-S05-M5-T11 | 更新 Program、目标架构、当前架构、产品范围、专项索引和路线状态 | revision 与事实一致；S05 完成时 current_stage 暂置 none；不得在同一路线提前激活下一 Program/Stage | Deferred |
| PLATFORM-SCALE-S05-M5-T12 | 完成 S05 报告、影响审计、证据归档和路线级 `route-final` | 59 项逐 Task 闭环；容量/长稳/故障/运维/边界证据 fresh 且可追溯；最终门禁通过 | Deferred |

## 6. 暂停决定与恢复条件

### 6.1 当前决定

- `PLATFORM-SCALE-S05-M1` 已完成容量环境、确定性种子、HTTP/WebSocket/Yjs/Worker 负载器和不可变证据合同。
- `PLATFORM-SCALE-S05-M2` 至 `PLATFORM-SCALE-S05-M5` 状态为 `Deferred`，不是 `Completed`，也不是可直接执行的 `Pending`。
- 暂停原因是系统核心功能、接口、数据模型和真实负载模型仍在持续演进；当前本地 Docker 资源适合验证底座和失败识别，不适合发布真实目标容量、60 分钟/8 小时长稳、故障恢复或最终 Go/No-Go 承诺。
- 暂停期间不执行 60 分钟目标负载、8 小时 soak、目标容量压测、故障恢复综合演练和最终 `route-final`。

### 6.2 恢复前置条件

1. 核心功能、关键接口、主要数据模型和代表性负载模型达到可冻结状态。
2. 产品与工程负责人明确作出 S05 恢复决定，并把待执行任务从 `Deferred` 改回 `Pending`。
3. 重新确认 C1、SLO、故障时间线、RTO/RPO、允许失败和非承诺范围，禁止沿用已失真的候选值。
4. 提供资源稳定且足以承载目标负载与长稳测试的隔离环境，以及可代表真实协作方式的参与者或流量模型。
5. 在执行 M2 前，以届时代码、镜像和环境重新运行 M1 preflight、种子双周期、四类加载器和证据校验；历史 M1 结果只证明本轮底座，不自动证明未来环境。

## 7. Stage 全局验收标准

- 容量基线绑定不可变代码、镜像、宿主机、容器限制、服务副本、运行参数、依赖版本、数据 manifest 和负载脚本。
- 数据种子确定、幂等、隔离、可清理并覆盖现实规模与分布；共享开发数据不进入正式结论。
- HTTP、Worker、普通 WS 和知识协同负载器固定版本、可自动断言业务语义，失败不会只被记录为性能样本。
- C1 候选逐项有 Pass、Fail 或 Bounded 结论；未达到目标时发布实际拐点和瓶颈，不回写历史门槛制造通过。
- 混合负载同时覆盖 read/write、异步消费、实时 fanout 和协同编辑，并保持 workspace、权限、幂等、顺序和敏感字段边界。
- 指标覆盖 P50/P95/P99、错误率、吞吐、backlog/oldest age、dead letter、fanout/协同收敛、CPU、内存、线程、GC、连接池和依赖资源。
- 恢复 M2-M5 后，至少一轮 60 分钟目标混合负载和一轮 8 小时低强度 soak 有完整原始时序、资源斜率、错误样本和前后数据校验；暂停期间不得以 M1 短时场景替代。
- API、Worker、Gateway、collaboration、Redis、PostgreSQL 和 MinIO 故障有具名自动证据；RTO、事实缺口、重复副作用和恢复后泄漏明确。
- 发布、扩容、降容、回退、备份恢复和诊断无需访问业务私表；危险操作受权限、确认、理由和审计约束。
- PostgreSQL、Redis、MinIO 单点和未验证负载进入非承诺清单，不冒充集群高可用或无限水平扩展。
- 历史跨 owner read 例外按当前事实逐项删除、修复或重新批准；project/shared P0 与 foreign write 保持 0。
- M5 恢复并执行时，完成后端、迁移、前端、collaboration、工作台、安全、容量证据完整性和 `route-final`。
- 最终 Go/No-Go 明确唯一下一入口；当前路线完成和归档前不激活新的 Program 或 Stage。

## 8. 归档状态

本路线于 2026-07-25 暂停归档。`PLATFORM-SCALE-S05-M1-T01` 至 `PLATFORM-SCALE-S05-M1-T12` 已完成；M2-M5 保持 `Deferred`，只有满足第 6.2 节并重新激活 PLATFORM-SCALE 后才能进入新的当前路线。M1 通过不代表候选 C1 已达到，不代表 PostgreSQL、Redis、MinIO 已具备高可用，也不构成 S05 最终 Go/No-Go。
