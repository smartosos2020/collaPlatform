---
title: 平台模块边界与公共合同 ADR
status: active
decision: accepted
revision: 1
updated_at: 2026-07-24
---

# 平台模块边界与公共合同 ADR

## 1. 决策范围

本文冻结模块化单体的术语、依赖方向、table owner、公开合同、组合查询、流程协调器、同步/异步选择、事务和例外审批规则。机器事实来源是：

- `tools/workbench/config/platform-modules.json`
- `tools/workbench/config/platform-table-owners.json`
- `tools/workbench/config/platform-boundary-exceptions.json`
- `pnpm architecture:contracts`

本文不声明现有依赖已经合格。M2 冻结合同，M3 建立失败门禁，M4 才迁移 project/shared 的 P0 私有依赖。

## 2. 规范术语

| 术语 | 可判定定义 |
| --- | --- |
| 模块 | `com.colla.platform.modules.<module>` 下拥有业务能力、代码 owner 和状态的部署内边界 |
| owner | 对模块行为、表事实、合同兼容和例外退出负责的团队或能力 owner |
| 公开合同 | `modules.<module>.contract` 下的 facade、SPI、record、value 或 event；这是唯一允许被其他业务模块依赖的 provider 包 |
| 私有包 | provider 的 `api`、`application`、`domain`、`infrastructure`；其他模块不得直接 import |
| 组合模块 | `search`、`admin`、`workspace` 等只编排多个公开 query facade 或维护自身投影、不拥有来源事实的模块 |
| 流程协调器 | 对跨 owner 长流程保存状态、重试和补偿的明确 application 能力；它不取得参与模块表的 owner 身份 |
| 技术 shared | 不包含业务规则、不 import 任一业务模块，只承载稳定技术机制的 `com.colla.platform.shared` |
| table owner | 对一张当前有效表的 schema、写入语义、生命周期和事实一致性唯一负责的模块 |

“同一 Spring 进程”不构成直接依赖私有包或私表的理由。

## 3. 依赖规则

### 3.1 允许

1. 模块内部可以按 `api -> application -> domain` 和 infrastructure adapter 方向协作。
2. consumer 可以依赖 foreign `contract` 中的稳定 facade/SPI/record/value/event。
3. provider application/infrastructure 可以实现本模块 contract。
4. provider SPI 的接口和值类型归属平台 contract；业务模块实现 SPI 时只依赖该 contract，平台模块不得反向 import provider 私有包。
5. shared 可以被模块依赖，但 shared 不得依赖业务模块。
6. 同一 PostgreSQL schema 可以保留跨 owner 外键；外键不授予 consumer 直接业务读写权限。

### 3.2 禁止

1. foreign `api`、`application`、`domain`、`infrastructure` import。
2. shared -> modules 反向依赖。
3. consumer 直接写 foreign owner 表。
4. 用通配路径、目录、模块或 read/write 混合模式放行历史例外。
5. 通过全局 helper、共享 Repository 或“公共 JdbcTemplate”隐藏跨 owner SQL。
6. contract import provider 私有包、Spring MVC DTO、Repository、存储密钥或认证凭据。

## 4. 事务与同步/异步决策

### 4.1 同事务

- 模块自身聚合和自身 owner 表写入。
- 业务命令与 transactional outbox append。
- 业务命令与必须原子记录的 audit append。

outbox/audit 的公开 port 必须加入调用方现有数据库事务。不得为了消除 import 改成事务提交后的易丢失调用。

### 4.2 同步公开合同

同步调用只用于立即完成当前命令所必需、延迟低、失败可直接返回且不会形成跨 owner 写事务的操作，例如：

- identity 主体状态和认证成员查询。
- file 元数据与访问判定。
- platform object summary/access state。
- IM 可见消息读取。

同步 query 必须批量、workspace scoped、最小披露，并明确 unavailable/hidden。

### 4.3 异步事件

满足任一条件时使用 outbox 事件：

- 调用不是当前命令提交的必要条件。
- consumer 可以稍后收敛。
- 存在一对多消费者、重试、积压或独立扩容需求。
- 搜索、通知、派生投影等可重建结果。

event envelope 固定 event type/version、workspace、aggregate、幂等、correlation/causation 和发生时间。payload 不得包含密码、token、MinIO key、私密正文或无界对象快照。

### 4.4 可恢复流程

跨 owner 写入、人员交接、跨模块删除或需要补偿的流程必须由显式流程协调器执行：

1. 每个参与模块只通过自己的 command contract 修改自己的表。
2. 协调器记录步骤、幂等键、结果和补偿状态。
3. 不扩大为一个跨多个私有 Repository 的同步数据库事务。
4. 失败必须可重试、可观察，且不会泄露被隐藏对象。

## 5. 公开合同规则与兼容

contract 当前版本为 1。允许在同版本中做向后兼容的新增；删除、重命名、收窄枚举、改变 null/hidden/错误语义或事务保证，必须：

1. 新增并行版本化合同。
2. 给出 consumer 清单和迁移 Stage。
3. 保持旧版本到退出 Stage。
4. 通过正反 fixture 和 provider/consumer 集成测试后删除。

contract 只允许 JDK 类型、同 contract 包类型或另一个经过批准的公共 contract。禁止把 application service、domain aggregate、Repository、Controller DTO、JDBC row 或存储实现作为合同值。

## 6. Table Owner

V001-V069 当前 90 张有效表在 `platform-table-owners.json` 中恰好归属一个 owner。规则如下：

- `foreignWrite = forbidden`：foreign write 没有例外通道。
- foreign read 必须匹配精确文件、精确表、read 模式和有效退出 Stage。
- 新建、rename、drop 表必须在同一变更同步 owner manifest。
- `domain_events`、`audit_logs`、`search_index_entries` 是有明确 owner 的技术表，不是无 owner shared 表。
- `realtime_signals` 由 event owner 管理，`search_projection_versions` 由 search owner 管理；二者都是技术事实表，不是跨模块共享写入点。
- migration/map 表仍归属对应业务模块；“临时迁移”不等于无人负责。
- history/log 表仍由产生该事实的模块拥有。
- 当前 `shared`、`ownerless`、`retired` 列表均为空；出现新条目必须有明确 ADR。

共享数据库和跨模块外键继续保留。本规则治理业务读写入口，不要求每个模块独立 schema。

## 7. 边界例外审批

每项例外必须具有唯一 ID、kind、source/target module、精确 source file、精确 target、模式、原因、owner、引入 Stage、退出 Stage、到期决定和批准状态。

- 审批人：source owner、target owner、platform architecture。
- 通配符和目录级放行无效。
- foreign write 不能审批。
- 修改旧违规文件时只能保持或减少既有范围；新增目标、表、模式、次数或方向均失败。
- 退出 Stage 到达时必须选择 remove、replace-with-contract 或 replace-with-projection，不能自动续期。

M2 只批准两个代表性只读入口用于锁定 schema；M3 会把完整历史 import/SQL 基线转成精确、可收敛的机器条目。

## 8. 组合查询与投影政策

### 8.1 Search

- 目标是消费事件维护自身 `search_index_entries` 投影。
- 现有在线读取业务表只允许精确 read 例外。
- 搜索不得写任何来源模块表，也不得成为业务事实 owner。
- 退出条件：对应对象类型具有增量事件、重建命令、延迟/积压指标和权限删除收敛。

### 8.2 Admin

- 企业治理页面只使用治理 query facade 或专用只读投影。
- 现有聚合 SQL 只允许精确 read 例外。
- 写操作必须调用目标模块 command contract；admin 不直接写目标私表。
- 治理 DTO 必须执行最小披露，不能返回凭据、存储 key 或用户不可见业务正文。

### 8.3 Workspace

- workspace 是用户体验组合层，不拥有 project、IM、knowledge、notification 等业务事实。
- 只允许批量 query facade 或自身投影，不允许直接 Repository/私表访问。
- 一个来源失败时返回明确的局部降级，不通过跨 owner 事务制造全有或全无。

## 9. Identity 公共合同

`SubjectDirectory` 批量解析 member、department、user-group，输入必须包含 workspace 和 actor。输出状态仅为 active/disabled/hidden；hidden 不返回 display name。

`AuthenticationQuery` 只返回 active member 的 workspace/user/username/displayName。它不公开密码、session、device token、角色私表或 Repository。

语义：

- 跨 workspace 一律 hidden。
- disabled 可在调用方确需区分“存在但不可用”时返回 disabled。
- actor 无查看权限、对象不存在或已删除统一 hidden，防止枚举。
- consumer 不得缓存身份权限事实作为长期授权依据。

## 10. File 公共合同

`FileAccess` 批量返回 available/unavailable/hidden。只有 available 才携带 file id、workspace、状态、size 和 MIME。

- 不公开 object key、bucket、签名 URL、上传 token 或后端实现。
- workspace 不匹配、无权和不存在统一 hidden。
- pending/deleted 可按调用场景收敛为 unavailable。
- 下载 URL 仍由 file 模块的用户 API 在权限判定后短期签发。

## 11. Platform Object 公共合同

- `PlatformObjectResolver` 是业务 provider 实现的 SPI。
- `PlatformObjectRegistry` 负责注册、summary 和 access state 查询。
- `PlatformObjectCommands` 负责 link/favorite 命令。
- `PlatformObjectSummary` 只包含 object type/id、title、route、access state 和有界 attributes。

依赖方向固定为 provider implementation -> platform contract；platform registry 只发现 SPI Bean，不 import provider application/domain/infrastructure。

## 12. Transactional Outbox

`TransactionalOutbox.append(EventEnvelope)` 是同事务 append port。event id 和 idempotency key 由调用方提供并稳定重试；event type/version 不从 Java 类名推断。

M2 不实现 Worker lease、dead-letter、replay、handler registry 或独立进程，这些属于后续 Stage。

## 13. Audit

`AuditAppender.append(AuditEntry)` 接收 actor/action/object/request boundary、correlation、时间、已脱敏上下文、before/after hash 和有界 diff。

- 原始密码、token、文件 key、富文本正文和完整配置快照禁止进入审计。
- 大对象只记录 hash、数量和允许字段的差异摘要。
- 调用方负责在 contract 边界前脱敏；audit provider 再执行防御性过滤。
- 必须审计的业务命令与 audit append 保持同事务。

## 14. Project 与 IM

`ProjectMessaging` 只暴露：

- 建立项目会话。
- 增加会话成员。
- 按 actor 读取可见且未越权的消息摘要。
- 发送具有幂等键的系统消息。

撤回时间通过最小 `MessageSnapshot` 返回；project 不读取 `messages`、`conversation_members` 或 `ImRepository`。项目创建时 IM 会话是同步必要步骤；活动广播是 best-effort 或异步，不得回滚已提交项目事实。消息转事项先通过可见消息 query 验证，再只在 project owner 表记录来源引用。

## 15. 非目标

本 ADR 的非目标：

- 不拆微服务、数据库 schema 或前后端仓库。
- 不在 M2 修改现有 API、错误码、权限或用户界面。
- 不在 M2 实现 provider adapter 或迁移 consumer。
- 不引入分布式事务。
- 不交付 API/Worker/event-gateway 运行角色拆分。
- 不形成吞吐量、并发数、高可用或容量承诺。

## 16. 验证

`pnpm architecture:contracts` 必须验证：

1. 代码中的 15 个模块与 manifest 完全一致，未知模块失败。
2. 90 张当前有效表与 owner manifest 完全一致，重复、缺失、未知 owner 或 ownerless 失败。
3. 例外没有通配符、foreign write、缺失 owner/Stage/决定或未知模块。
4. contract Java 源文件不 import provider 私有包。
5. 本文保留全部必要决策词和非目标。
