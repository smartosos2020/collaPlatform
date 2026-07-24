---
title: PLATFORM-SCALE-S02 API 运行角色隔离与双实例基线当前执行路线
status: completed
route: PLATFORM-SCALE-S02
program: PLATFORM-SCALE
program_doc: docs/00-product/initiatives/platform-scale-program.md
program_revision: 4
stage: PLATFORM-SCALE-S02
stage_final_milestone: PLATFORM-SCALE-S02-M5
last_code_check: 2026-07-24
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PLATFORM-SCALE-S02 API 运行角色隔离与双实例基线

## 1. Stage 目标

在 S01 已建立模块 contract、table owner、架构门禁并清理 project/shared P0 边界的基础上，把当前单个 Spring Server 中混合存在的 HTTP API、事件轮询、通用 WebSocket、旧知识协同任务和维护初始化职责拆成可判定的运行角色，并交付两个 API 实例共享 PostgreSQL、Redis、MinIO 时的无状态运行基线。

S02 继续使用同一 Server 源码和构建产物，不拆业务微服务、不拆数据库。S02 不实现 S03 的 Worker lease/dead-letter/replay，不实现 S04 的多 Gateway Redis fanout和旧 Spring 协同彻底删除，也不发布 S05 容量承诺。S02 完成时必须以真实双 API 和隔离数据证明单 API 节点退出后新请求可继续完成。

## 2. 固定输入与边界

- 上一 Stage：`PLATFORM-SCALE-S01` 已完成，归档路线为 `docs/99-archive/superseded-roadmaps/platform-scale-s01-roadmap-completed-2026-07-24.md`。
- 活动专项：`docs/00-product/initiatives/platform-scale-program.md` revision 4。
- 目标架构：`docs/01-architecture/platform-scale-target-architecture.md` revision 4，重点执行第 5、11、14、16 节。
- S01 边界事实：15 个后端模块、268 个 Java 文件、153 条历史 private import、29 条 foreign infrastructure；project private/infra 与 shared reverse 均为 0。
- 数据边界：V001-V066、85 张有效表、93 条精确跨 owner read 例外、0 条 foreign write；S02 不以运行拆分为由扩大例外。
- 角色合同：生产只允许 `api`、`worker`、`event-gateway`、`maintenance`；`combined` 只允许 local/test。
- 部署合同：`api-a`、`api-b` 使用相同镜像和 schema，共享 PostgreSQL、Redis、MinIO，不使用粘性会话或节点内业务事实。
- 回退合同：允许从双 API 摘除一个节点回退为单 API，不回滚 schema，不恢复 API 内事件轮询、通用 WS 或旧知识协同任务。
- 验证边界：M1-M4 使用影响范围验证；M5 执行完整后端、迁移、前端、协作、工作台、安全、真实双实例浏览器和 `route-final`。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须在本路线恰好出现一次，并有唯一 Verification Contract 和 Acceptance Evidence。
2. 角色隔离必须通过条件配置和 Bean 缺失证明，不能只在方法体内通过 `if (role)` 跳过执行。
3. 生产缺失、未知或组合多个运行角色必须启动失败；测试兼容模式必须显式，不能成为生产默认值。
4. API 角色只承载 HTTP 业务、事务命令、查询、outbox append 和文件访问，不执行事件消费、通用 WS session 或旧知识协同定时任务。
5. 双 API 不依赖粘性会话；认证、撤销、幂等回执、上传会话和初始化结果必须来自共享事实。
6. liveness 不因短暂依赖故障制造重启风暴；readiness 必须按角色承诺反映依赖并支持先摘流量后停机。
7. Redis 不能成为唯一业务事实；MinIO 故障不能无差别拖垮非文件 API；PostgreSQL 故障时不得继续宣称可写。
8. 不提前实现 Worker 多实例 lease、Gateway 多节点 fanout、旧知识协同删除或容量门槛；发现依赖时回到 Program 修订。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M1 | 运行角色配置、Bean 边界与启动合同 | S01 归档与 revision 3 | `docs/90-reports/platform-scale-s02-m1-execution-report.md` | Completed |
| PLATFORM-SCALE-S02-M2 | API 职责净化、角色健康与优雅生命周期 | M1 | `docs/90-reports/platform-scale-s02-m2-execution-report.md` | Completed |
| PLATFORM-SCALE-S02-M3 | 双 API upstream、无状态与单节点退出 | M1-M2 | `docs/90-reports/platform-scale-s02-m3-execution-report.md` | Completed |
| PLATFORM-SCALE-S02-M4 | 认证、幂等、上传与初始化多实例复验 | M2-M3 | `docs/90-reports/platform-scale-s02-m4-execution-report.md` | Completed |
| PLATFORM-SCALE-S02-M5 | Stage 评审、route-final 与 Program Go/No-Go | M1-M4 | `docs/90-reports/platform-scale-s02-m5-execution-report.md` | Completed |

## 5. 详细任务

### PLATFORM-SCALE-S02-M1 运行角色配置、Bean 边界与启动合同

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M1-T01 | 复核 S01 准入包并建立当前 Bean、Controller、Listener、Scheduler、WebSocket 和初始化职责清单 | 每个运行职责定位到配置类/Bean/任务/端口；当前混合点、目标角色和 S03/S04 承接点无遗漏 | Done |
| PLATFORM-SCALE-S02-M1-T02 | 实现 `colla.runtime.role`/`COLLA_RUNTIME_ROLE` 枚举、绑定和启动校验 | 四个生产角色唯一；生产缺失、未知、逗号组合和 `combined` 均失败，local/test 显式 combined 可用 | Done |
| PLATFORM-SCALE-S02-M1-T03 | 建立可复用 role condition 与 role-specific configuration 边界 | 条件在 Bean 创建前生效；不存在只实例化后跳过执行的伪隔离，条件结果可由测试枚举 | Done |
| PLATFORM-SCALE-S02-M1-T04 | 冻结并实现 API 角色 Bean/端口白名单 | MVC、安全、事务命令、查询和 outbox append 可用；Worker、通用 WS、旧知识协同任务和维护命令不存在 | Done |
| PLATFORM-SCALE-S02-M1-T05 | 冻结并实现 Worker 角色 Bean/端口白名单 | outbox worker 及临时归属任务可启动；用户业务 Controller、通用 WS 和知识协同协议不存在 | Done |
| PLATFORM-SCALE-S02-M1-T06 | 冻结并实现 Event Gateway 角色 Bean/端口白名单 | `/ws/events`、认证查询和校准依赖可用；业务写 Controller、outbox consumer 和业务 Repository 写入口不存在 | Done |
| PLATFORM-SCALE-S02-M1-T07 | 冻结并实现 Maintenance 角色命令边界 | Flyway/初始化/修复命令显式执行后退出；不加入业务 upstream，不运行用户流量、WS 或长期 Worker | Done |
| PLATFORM-SCALE-S02-M1-T08 | 约束 Spring scheduling、component scan 和自动配置按角色启用 | 不因全局 `@EnableScheduling` 或 component scan 让禁止 Bean 在错误角色中出现 | Done |
| PLATFORM-SCALE-S02-M1-T09 | 暴露 runtime role、版本、commit 和 instance id 元数据 | Actuator info、结构化日志和指标标签可区分角色/实例，且不泄露密钥或内部连接串 | Done |
| PLATFORM-SCALE-S02-M1-T10 | 建立四生产角色与 combined 的 ApplicationContext/Bean 矩阵测试 | 每个角色的必须/禁止 Bean、端口、Scheduler 数量和失败配置均有正反自动断言 | Done |
| PLATFORM-SCALE-S02-M1-T11 | 更新本地测试、IDE 和生产配置示例 | 开发者可显式选择 combined 或单角色；生产示例无隐式混合模式，环境变量命名唯一 | Done |
| PLATFORM-SCALE-S02-M1-T12 | 完成 M1 影响范围、架构门禁和执行报告收口 | 无新增 private/infra/shared reverse/foreign write；编译、角色上下文测试、文档合同和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S02-M2 API 职责净化、角色健康与优雅生命周期

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M2-T01 | 复核 `DomainEventWorker`、旧知识协同和通用 WS 的当前事实与临时归属 | 每个任务的触发器、事实源、内存状态、关闭语义和 S03/S04 最终归属可定位 | Done |
| PLATFORM-SCALE-S02-M2-T02 | 将 DomainEventWorker 及事件轮询从 API 角色移出 | API 上下文无事件 poll/claim；Worker/combined 保持现有消费行为、幂等和失败语义 | Done |
| PLATFORM-SCALE-S02-M2-T03 | 将旧知识协同定时任务和进程内 room/presence 状态从 API 角色移出 | API 不再持有旧协同状态或 autosave/cleanup 任务；Hocuspocus 主链路不受影响 | Done |
| PLATFORM-SCALE-S02-M2-T04 | 将 `/ws/events` endpoint、session registry 和本地广播从 API 角色移出 | API 不接受通用 WS upgrade；Event Gateway/combined 保持认证、连接、推送和关闭行为 | Done |
| PLATFORM-SCALE-S02-M2-T05 | 为 Event Gateway 收敛最小认证与 REST 校准依赖 | Gateway 不导入业务私有 Repository 或开放业务写 Controller，拒绝/隐藏语义与 API 一致 | Done |
| PLATFORM-SCALE-S02-M2-T06 | 实现 role-aware liveness/readiness 和依赖健康组 | API、Worker、Gateway 分别暴露承诺依赖；maintenance 不进入流量 readiness | Done |
| PLATFORM-SCALE-S02-M2-T07 | 实现 API 优雅停机和 readiness 先摘除 | 终止信号后拒绝新流量并给予最多 30 秒完成在途事务；超时和取消有结构化日志 | Done |
| PLATFORM-SCALE-S02-M2-T08 | 实现 Worker/Gateway 的角色化停止顺序 | Worker 停止 claim 并完成/释放当前处理；Gateway 停止新连接并要求客户端重连校准 | Done |
| PLATFORM-SCALE-S02-M2-T09 | 增加角色、实例和生命周期观测 | 请求、事件、连接、启动、not-ready 和 shutdown 日志/指标都可按 role/instance 定位 | Done |
| PLATFORM-SCALE-S02-M2-T10 | 建立错误角色端口、任务和内存状态负例测试 | API 发现 Worker/WS/旧协同任务或 Gateway 发现业务写入口时测试失败 | Done |
| PLATFORM-SCALE-S02-M2-T11 | 执行 API/Worker/Gateway/combined 上下文与现有行为回归 | 现有 HTTP、事件消费、WebSocket、知识协同和权限错误合同无回退 | Done |
| PLATFORM-SCALE-S02-M2-T12 | 完成 M2 实现定位、运行清单更新和影响范围门禁 | 当前架构、目标架构和执行报告一致；相关后端/协作测试及 checkpoint 通过 | Done |

### PLATFORM-SCALE-S02-M3 双 API upstream、无状态与单节点退出

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M3-T01 | 冻结双 API Compose/Nginx/端口/实例 ID 与共享依赖合同 | `api-a`/`api-b` 同镜像同 schema、独立连接池和 instance id；依赖、网络和健康路径无歧义 | Done |
| PLATFORM-SCALE-S02-M3-T02 | 在生产部署模板增加两个 API 角色服务 | 两实例使用 `COLLA_RUNTIME_ROLE=api` 并共享 PostgreSQL/Redis/MinIO；不复制业务数据卷 | Done |
| PLATFORM-SCALE-S02-M3-T03 | 将 Nginx API upstream 改为双节点和 readiness 感知 | 新请求分配到两个健康节点；失败节点被摘除，管理健康端点不暴露给普通用户 | Done |
| PLATFORM-SCALE-S02-M3-T04 | 配置 Spring/Nginx 优雅停机、连接和超时预算 | readiness 摘除先于进程退出；in-flight、keepalive、proxy retry 不造成非幂等请求重复提交 | Done |
| PLATFORM-SCALE-S02-M3-T05 | 审计并清理 API 节点内会话、权限和业务事实依赖 | 本地缓存仅为可丢失加速；重启或切换节点不丢登录、撤销、权限、未读或业务状态 | Done |
| PLATFORM-SCALE-S02-M3-T06 | 验证共享 PostgreSQL 连接池与并发预算 | 两节点连接上限、启动峰值和事务隔离有固定配置，不因翻倍实例耗尽数据库连接 | Done |
| PLATFORM-SCALE-S02-M3-T07 | 建立双节点请求分布和实例追踪证据 | 响应/日志可证明同一用户连续请求到达不同实例，且不向产品 API 泄露内部拓扑 | Done |
| PLATFORM-SCALE-S02-M3-T08 | 建立单 API 节点优雅退出自动化 | 停止 api-a 后 api-b 继续完成新请求；只允许已建立且未完成的在途连接失败 | Done |
| PLATFORM-SCALE-S02-M3-T09 | 建立非优雅 API 进程终止与 upstream 恢复自动化 | 强制终止单节点后在冻结时间内摘除；恢复节点通过 readiness 后重新接流量 | Done |
| PLATFORM-SCALE-S02-M3-T10 | 冻结并验证单 API 回退操作 | 摘除一个 upstream 即可回退，不回滚 schema、不启用 combined、不恢复 API 内 Worker/WS | Done |
| PLATFORM-SCALE-S02-M3-T11 | 完成真实双 API 浏览器/API 流程和 M3 门禁 | 真实登录、项目/知识/Base 读写和权限路径跨节点通过；部署、后端、前端和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S02-M4 认证、幂等、上传与初始化多实例复验

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M4-T01 | 建立多实例关键状态和命令矩阵 | token/session、撤销、request id、上传会话、管理员初始化、bucket 和 Flyway 的事实源/唯一约束明确 | Done |
| PLATFORM-SCALE-S02-M4-T02 | 复验登录、JWT、设备会话和撤销跨节点一致性 | api-a 登录的凭证可在 api-b 使用；撤销后两个节点一致拒绝，不依赖 JVM session | Done |
| PLATFORM-SCALE-S02-M4-T03 | 清点并统一关键写命令的数据库幂等回执 | 项目、知识、Base、管理操作的 request id 唯一约束和响应重放可定位；缺口有阻断决定 | Done |
| PLATFORM-SCALE-S02-M4-T04 | 建立跨节点重复命令、并发和重试回归 | 同 request id 先后/并发落到不同 API 只产生一个业务结果、outbox 和 audit | Done |
| PLATFORM-SCALE-S02-M4-T05 | 复验上传会话、完成确认和下载跨节点行为 | api-a 创建的上传可由 api-b 完成/查询；对象缺失、重复完成和越权错误稳定 | Done |
| PLATFORM-SCALE-S02-M4-T06 | 将 MinIO bucket/policy 初始化收敛到 maintenance 幂等命令 | 重复执行结果一致；API 不竞态创建，MinIO 故障只让文件能力明确降级 | Done |
| PLATFORM-SCALE-S02-M4-T07 | 将生产 Flyway 执行收敛到 maintenance，API 只做 schema 兼容检查 | 双 API 不并发承担迁移；旧库升级、版本不兼容和失败恢复路径有自动证据 | Done |
| PLATFORM-SCALE-S02-M4-T08 | 收敛管理员、系统角色和默认配置初始化 | 唯一约束/upsert 保证多次和并发启动无重复副作用，执行版本与结果可审计 | Done |
| PLATFORM-SCALE-S02-M4-T09 | 建立 PostgreSQL、Redis、MinIO 故障矩阵自动化 | DB 故障使 API not-ready；Redis 不造成永久事实缺口；MinIO 不拖垮非文件 API | Done |
| PLATFORM-SCALE-S02-M4-T10 | 验证恢复、节点重启和初始化重放 | 依赖恢复后 readiness、缓存和业务行为收敛；无孤立 outbox/audit/文件记录 | Done |
| PLATFORM-SCALE-S02-M4-T11 | 增加安全、审计和实例关联证据 | 登录、撤销、重复命令、初始化和故障动作可关联 request/correlation/instance，敏感数据不入日志 | Done |
| PLATFORM-SCALE-S02-M4-T12 | 完成真实双实例端到端、迁移 rehearsal 和 M4 收口 | 六身份关键路径、跨节点命令/上传/撤销、空库与升级迁移 fresh 通过 | Done |

### PLATFORM-SCALE-S02-M5 Stage 评审、route-final 与 Program Go/No-Go

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S02-M5-T01 | 复核 M1-M4 的 47 项实现任务与 M5 的 10 项收口合同 | 路线共 57 项均有唯一可重复证据；无空占位、静默豁免或未决验收阻断 | Done |
| PLATFORM-SCALE-S02-M5-T02 | 复跑角色 Bean/端口/Scheduler、架构边界和 SQL owner 门禁 | 四生产角色和 combined 矩阵稳定；无新增 private/infra/shared reverse/foreign write | Done |
| PLATFORM-SCALE-S02-M5-T03 | 执行四生产角色构建、启动、health/readiness 和优雅停机矩阵 | 每个角色只承载冻结职责，错误配置失败，生命周期和观测证据 fresh 通过 | Done |
| PLATFORM-SCALE-S02-M5-T04 | 执行真实双 API 分流、优雅/强制退出、恢复和单节点回退 | 不依赖粘性会话；节点退出后新请求继续，回退不改变 schema 或启用混合职责 | Done |
| PLATFORM-SCALE-S02-M5-T05 | 执行认证、撤销、幂等、上传、初始化和依赖故障完整回归 | 跨节点结果唯一、权限一致、初始化幂等；DB/Redis/MinIO 故障符合冻结矩阵 | Done |
| PLATFORM-SCALE-S02-M5-T06 | 执行路线级后端、迁移、前端、协作、工作台、安全和 `route-final` | 全部门禁使用本轮 fresh 证据通过；失败或跳过形成阻断决定，不能静默关闭 | Done |
| PLATFORM-SCALE-S02-M5-T07 | 冻结运行隔离部署、观测、回退和故障处置 runbook | 操作者可启动四角色、扩缩 API、摘除节点、诊断 readiness 并回退单 API | Done |
| PLATFORM-SCALE-S02-M5-T08 | 输出剩余例外和 PROJECT-PLATFORM 恢复 Go/No-Go | 明确恢复 PROJECT-PLATFORM-S05、继续 PLATFORM-SCALE-S03 或补充 S02；理由和阻断项可复核 | Done |
| PLATFORM-SCALE-S02-M5-T09 | 更新 Program、目标架构、当前事实、专项索引和 Stage 状态 | 修订号一致；S02 completed 等待归档，不在同一路线提前激活下一 Stage/Program | Done |
| PLATFORM-SCALE-S02-M5-T10 | 完成 S02 执行报告、影响范围审计和最终收口 | 57 项逐 Task 闭环，真实双实例浏览器和 route-final 通过，容量草案不冒充承诺 | Done |

## 6. Stage 全局验收标准

- 同一 Server artifact 通过单值 `COLLA_RUNTIME_ROLE` 运行；生产缺失、未知、组合和 combined 均启动失败。
- `api`、`worker`、`event-gateway`、`maintenance` 的必须/禁止 Bean、端口、Scheduler 和停止顺序由自动矩阵验证。
- API 角色无 DomainEventWorker、通用 WS session、旧知识 room/presence/autosave 和维护命令。
- 两个 API 实例使用相同镜像、schema 和共享依赖，不依赖粘性会话或节点内认证、权限、幂等和业务事实。
- liveness/readiness、runtimeRole/instanceId、结构化日志和指标足以定位角色与节点；优雅停机先摘流量后结束在途工作。
- 单 API 优雅/强制退出后新请求继续成功；恢复节点 readiness 通过后可重新接流量；可只摘除一个节点回退。
- 跨节点登录/撤销、重复命令、上传会话、管理员初始化、MinIO 初始化和 Flyway 入口无节点依赖或重复副作用。
- PostgreSQL、Redis、MinIO 故障符合冻结矩阵；Redis 不成为唯一事实，MinIO 不无差别拖垮非文件 API。
- S01 架构边界继续生效：project/shared P0 保持 0，foreign write 保持 0，历史例外只能保持或减少。
- S02 不交付 Worker lease、多 Gateway fanout、旧协同彻底退出或容量承诺；这些仍属于 S03-S05。
- M5 执行完整后端、迁移、前端、协作、工作台、安全、真实双 API 浏览器和 `route-final`，并输出明确 Go/No-Go。

## 7. 当前执行入口

S02 的 5 个 Milestone、57 个 Task 已全部完成，当前路线等待归档。本路线不提前激活下一 Stage 或 Program；归档后按 M5 Go/No-Go 结论重新评估激活 `PROJECT-PLATFORM-S05`。
