---
title: PLATFORM-SCALE-S01 模块边界、表归属、公共合同和自动门禁当前执行路线
status: completed
route: PLATFORM-SCALE-S01
program: PLATFORM-SCALE
program_doc: docs/00-product/initiatives/platform-scale-program.md
program_revision: 2
stage: PLATFORM-SCALE-S01
stage_final_milestone: PLATFORM-SCALE-S01-M5
last_code_check: 2026-07-24
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PLATFORM-SCALE-S01 模块边界、表归属、公共合同和自动门禁

## 1. Stage 目标

在 PROJECT-PLATFORM-S04 已完成并归档的基础上，把现有“按目录分模块”的意图升级为可执行的模块化单体边界：建立可重复依赖清单、唯一表 owner、跨模块公共合同、历史例外台账和自动失败门禁，并优先清理会直接阻碍 PROJECT-PLATFORM-S05-S07 的 project P0 依赖。

S01 完成后，新代码不能继续增加 foreign infrastructure、shared 反向业务依赖或无 owner 私表访问；project 对 identity、file、platform、event、audit 和 IM 的交互必须通过公开 contract。S01 不拆业务微服务、不拆数据库、不交付双 API、独立 Worker、通用实时网关或容量承诺；这些分别属于 PLATFORM-SCALE-S02 至 S05。

## 2. 固定输入与边界

- 上一产品 Stage：`PROJECT-PLATFORM-S04` 已完成并归档到 `docs/99-archive/superseded-roadmaps/project-platform-s04-roadmap-completed-2026-07-24.md`。
- 活动专项：`docs/00-product/initiatives/platform-scale-program.md` revision 2。
- 目标架构：`docs/01-architecture/platform-scale-target-architecture.md` revision 2。
- 冻结基线：`docs/90-reports/platform-scale-readiness-baseline-2026-07-24.md`，主干提交 `134c370`。
- 后端基线：15 个模块、233 个 Java 文件、204 条跨模块 import、47 条 foreign infrastructure import、58 个依赖方向、11 模块强连通分量。
- 数据基线：22 个文件、96 个跨 owner SQL 候选；共享 PostgreSQL、单 Flyway 链和 workspace/user 复合外键继续保留。
- 前端基线：16 个 feature、64 条跨 feature import、23 个涉及文件、40 个方向，存在 `knowledgeBases <-> search` 循环。
- P0 修复边界：project 和 shared 当前直接依赖；其他历史违规进入有 owner、原因、模式和退出 Stage 的受控 baseline，不要求 S01 一次清零全部 204 条跨模块 import。
- 运行边界：S01 只冻结 `api/worker/event-gateway/maintenance` 输入，不改变当前生产运行拓扑。
- 验证边界：M1-M4 使用影响范围门禁；M5 执行全量后端、迁移、前端、协同、工作台、安全、真实隔离浏览器和 `route-final`。

## 3. 执行规则

1. 每轮只推进一个 Milestone，不跨 Milestone 启动工作循环；每个 Task 必须有唯一 Verification Contract 和 Acceptance Evidence。
2. 先建立可重复清单，再冻结政策，再实现失败门禁，最后修复 P0；不得用手工数字替代机器证据。
3. 静态 import、动态 import、重导出、`require()`、路径别名、多行 SQL 和迁移生成表都必须纳入对应扫描器测试。
4. 历史 baseline 只能保留已确认违规；触及旧违规必须保持或减少，新增违规立即失败。
5. table owner 约束业务读写入口，不删除共享数据库中的 workspace/user 外键，不把模块化误解为每模块独立 schema。
6. outbox 和 audit 公共 port 必须保留与业务命令同事务的语义；不能为消除 import 改成易丢失的事务后调用。
7. project P0 修复保持 S04 的隔离、幂等、并发、错误、审计脱敏和真实 UI 行为，不借重构改变 API 合同。
8. 若需要双 API、Worker lease、Redis fanout 或退出旧协同，记录为 S02-S04 输入，不在 S01 偷跑。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M1 | 干净主干可重复基线、依赖图和风险分级 | S04 归档与复验 | `docs/90-reports/platform-scale-s01-m1-execution-report.md` | Completed |
| PLATFORM-SCALE-S01-M2 | 模块 contract、table owner、组合查询和例外合同 | M1 | `docs/90-reports/platform-scale-s01-m2-execution-report.md` | Completed |
| PLATFORM-SCALE-S01-M3 | 后端、前端和数据边界自动门禁 | M1-M2 | `docs/90-reports/platform-scale-s01-m3-execution-report.md` | Completed |
| PLATFORM-SCALE-S01-M4 | project P0 边界与 shared 反向依赖收敛 | M2-M3 | `docs/90-reports/platform-scale-s01-m4-execution-report.md` | Completed |
| PLATFORM-SCALE-S01-M5 | Stage 评审、route-final 与 S02 准入 | M1-M4 | `docs/90-reports/platform-scale-s01-m5-execution-report.md` | Completed |

## 5. 详细任务

### PLATFORM-SCALE-S01-M1 干净主干可重复基线、依赖图和风险分级

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M1-T01 | 复核 S04 归档、合入提交、执行报告和平台化准入输入 | S04 的 53 个 Task、最终门禁、真实浏览器、主干提交和非阻断架构债均可重复定位 | Done |
| PLATFORM-SCALE-S01-M1-T02 | 冻结架构清单计数语义、路径解析和版本化输出格式 | Java/TS/SQL/Flyway 的文件范围、去重规则、owner 推断、误报边界和 schemaVersion 无歧义 | Done |
| PLATFORM-SCALE-S01-M1-T03 | 实现跨平台后端模块 import、foreign infrastructure、边和 SCC 清单命令 | Node/TypeScript 命令可在 Windows/macOS/Linux 运行，输出稳定 JSON/Markdown，当前计数与冻结基线一致 | Done |
| PLATFORM-SCALE-S01-M1-T04 | 清点跨模块事务、shared 反向依赖和 provider 私有包暴露 | `@Transactional` + foreign infrastructure、shared -> modules、非 contract import 均定位到文件、来源、目标和类型 | Done |
| PLATFORM-SCALE-S01-M1-T05 | 实现前端 feature 依赖清单和循环图 | TypeScript AST + tsconfig 解析静态/动态/重导出/require/别名，复现 16/64/23/40 和现有 SCC | Done |
| PLATFORM-SCALE-S01-M1-T06 | 从 V001-V065 构建当前有效表、迁移来源和候选 owner 清单 | create/rename/drop 后的有效表唯一；每张表有来源迁移、候选 owner 和未决状态，不把历史已删除表计入 | Done |
| PLATFORM-SCALE-S01-M1-T07 | 建立 Java SQL 访问清单并区分 read/write/DDL/dynamic candidate | Repository/服务到表的访问可追溯；96 个跨 owner 候选可复现，动态 SQL 和函数封装明确标记人工复核 | Done |
| PLATFORM-SCALE-S01-M1-T08 | 清点运行角色、内存状态、定时任务、部署实例和事实源 | API、Worker、通用 WS、旧知识协同、Hocuspocus、Redis/PostgreSQL/MinIO 当前责任和单点可机器/人工复核 | Done |
| PLATFORM-SCALE-S01-M1-T09 | 输出 S04 增量、P0/P1/P2 风险和历史例外候选 | +18 Java、+10 跨模块、+6 infrastructure、0 新方向解释准确；每项风险有 owner、修复 Stage 和阻断级别 | Done |
| PLATFORM-SCALE-S01-M1-T10 | 完成 M1 基线报告、命令测试和影响范围质量门 | 清单命令具备正反 fixture；报告数字与 artifact 一致，工作台测试、文档合同和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S01-M2 模块 contract、table owner、组合查询和例外合同

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M2-T01 | 冻结模块、contract、owner、组合模块、流程协调器和技术 shared 术语 ADR | 允许依赖、禁止依赖、事务边界、同步/异步选择和非目标均有可判定规则 | Done |
| PLATFORM-SCALE-S01-M2-T02 | 建立机器可读后端模块清单 | 15 个模块均有 owner、根包、public contract、私有包、组合权限和状态；未知模块使门禁失败 | Done |
| PLATFORM-SCALE-S01-M2-T03 | 冻结 `modules.<module>.contract` 公开合同规则 | contract 只含 facade/SPI/record/value/event，不依赖 provider api/application/infrastructure，版本和兼容策略明确 | Done |
| PLATFORM-SCALE-S01-M2-T04 | 建立覆盖 V001-V065 当前有效 schema 的 table owner 清单 | 每张业务表恰好一个 owner；共享技术表、历史表、映射表和无 owner 表有明确处理，清单可自动校验 | Done |
| PLATFORM-SCALE-S01-M2-T05 | 建立边界例外清单 schema 和审批规则 | 例外包含唯一 ID、方向、文件/表、read/write 模式、原因、owner、引入/退出 Stage、到期决定；禁止通配放行 | Done |
| PLATFORM-SCALE-S01-M2-T06 | 冻结 search、admin、workspace 组合查询和投影政策 | 只读例外、query facade、治理投影、在线联查退出条件和禁止跨 owner 写规则无歧义 | Done |
| PLATFORM-SCALE-S01-M2-T07 | 设计 identity `SubjectDirectory`/认证查询公共合同 | member/department/user-group 批量状态、最小披露、workspace 隔离和禁用语义满足 project/permission/shared 需求 | Done |
| PLATFORM-SCALE-S01-M2-T08 | 设计 file 元数据与访问判定公共合同 | file id、状态、大小、MIME、workspace、available/hidden 结果足以替换 foreign FileRepository，且不泄露存储密钥 | Done |
| PLATFORM-SCALE-S01-M2-T09 | 设计 platform object resolver/registry 公共 SPI | resolver 注册、summary、access state、link/favorite 命令和反向依赖方向稳定，provider 不依赖 platform 私有实现 | Done |
| PLATFORM-SCALE-S01-M2-T10 | 设计 transactional outbox 公共 append port 和事件 envelope 最小合同 | 同事务 append、event type/version、幂等、correlation/causation 和敏感 payload 边界明确，不提前实现 Worker lease | Done |
| PLATFORM-SCALE-S01-M2-T11 | 设计 audit 公共 append port 和脱敏合同 | actor、action、object、request boundary、hash/差异摘要和敏感字段规则可替换 foreign AuditRepository/AuditService | Done |
| PLATFORM-SCALE-S01-M2-T12 | 设计 project 与 IM 的最小协作合同及跨模块流程 ADR | 消息关联/撤回等当前需求不暴露 ImRepository；跨 owner 写使用显式命令或可恢复流程，不扩大同步事务 | Done |
| PLATFORM-SCALE-S01-M2-T13 | 建立合同兼容、最小披露和所有权文档测试并完成 M2 收口 | manifest/ADR/contract schema 有正反 fixture；未决项不能伪装 Done，影响范围门禁和执行报告通过 | Done |

### PLATFORM-SCALE-S01-M3 后端、前端和数据边界自动门禁

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M3-T01 | 引入并固定 ArchUnit 测试依赖、测试入口和报告输出 | 后端架构测试进入 Maven/CI，版本固定，不依赖 IDE 或本机路径 | Done |
| PLATFORM-SCALE-S01-M3-T02 | 实现模块根包、foreign infrastructure 和 Controller/HTTP DTO 依赖规则 | 新增 foreign infrastructure、foreign api/controller DTO 立即失败；同模块内部依赖不误报 | Done |
| PLATFORM-SCALE-S01-M3-T03 | 实现 shared -> modules 和 contract 独立性规则 | shared 不依赖业务模块；contract 不依赖 provider application/infrastructure 或其他模块私有包 | Done |
| PLATFORM-SCALE-S01-M3-T04 | 实现 foreign import 只能指向公开 contract 的规则 | 新代码只可依赖 foreign contract；历史非 contract 依赖必须匹配精确 baseline，通配/目录级豁免被拒绝 | Done |
| PLATFORM-SCALE-S01-M3-T05 | 实现历史 allowlist、触及即收敛和数量 ratchet | 新违规为零；修改旧违规文件不能扩大方向/数量；删除违规后 baseline 必须同步缩减，过期项失败 | Done |
| PLATFORM-SCALE-S01-M3-T06 | 输出模块图、SCC 和基线差异 artifact 并纳入门禁 | 依赖方向/SCC 意外扩大失败；报告可定位新增、删除、来源文件和 baseline revision | Done |
| PLATFORM-SCALE-S01-M3-T07 | 实现前端 TypeScript AST 与 tsconfig 路径解析器 | 覆盖静态 import、`import()`、`require()`、`export ... from`、`@/`/相对路径和 index 解析，不以正则作为唯一解析器 | Done |
| PLATFORM-SCALE-S01-M3-T08 | 实现 feature public entry、shared 和懒路由边界规则 | 跨 feature 深路径、shared -> feature、静态页面路由和循环新增失败；现有例外精确可收敛 | Done |
| PLATFORM-SCALE-S01-M3-T09 | 实现 Flyway 当前表清单与 owner manifest 漂移门禁 | 新表/rename/drop 必须同步 owner；重复/未知 owner、历史幽灵表和未登记迁移失败 | Done |
| PLATFORM-SCALE-S01-M3-T10 | 实现 SQL owner 候选扫描、read/write 判定和例外门禁 | 明确 foreign write 一律失败；foreign read 必须匹配精确例外；多行 SQL、别名、CTE 和常见 JDBC 形态有测试 | Done |
| PLATFORM-SCALE-S01-M3-T11 | 实现例外 owner、范围、模式和退出 Stage 完整性检查 | 缺 owner/原因/文件/表/模式/退出 Stage、过期或扩大到 write 的例外失败，search/admin 不能借只读例外写表 | Done |
| PLATFORM-SCALE-S01-M3-T12 | 建立门禁绕过和误报对抗 fixture | bracket/dynamic/re-export/alias、注释字符串、同名表、动态 SQL、迁移 rename/drop 和 Windows/macOS 路径均有正反例 | Done |
| PLATFORM-SCALE-S01-M3-T13 | 集成 quick/full、CI 和工作台文档并完成 M3 收口 | Windows/macOS/Linux 工作台矩阵可执行；门禁日志可审计，现有测试、文档结构和 checkpoint 通过 | Done |

### PLATFORM-SCALE-S01-M4 project P0 边界与 shared 反向依赖收敛

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M4-T01 | 冻结 project/shared P0 依赖清单和行为回归矩阵 | project 当前 foreign identity/file/platform/event/audit/IM import 与 shared 三处反向依赖逐文件映射到替代合同和测试 | Done |
| PLATFORM-SCALE-S01-M4-T02 | 实现 identity SubjectDirectory/认证合同 provider adapter | 批量主体、成员、部门、用户组、认证用户查询由 identity 实现；workspace/禁用/隐藏语义与旧路径一致 | Done |
| PLATFORM-SCALE-S01-M4-T03 | 将复杂字段和项目成员服务迁移到 identity contract | `WorkItemFieldComplexReferenceValidator`、`ProjectSpaceMembershipService` 不再 import identity domain/infrastructure；六身份负例不回退 | Done |
| PLATFORM-SCALE-S01-M4-T04 | 实现 file 元数据与访问合同 provider adapter | 文件状态、大小、MIME 和访问判定由 file contract 提供；MinIO key/私有 Repository 不暴露 | Done |
| PLATFORM-SCALE-S01-M4-T05 | 将复杂字段和 legacy ProjectService 文件路径迁移到 file contract | project 不再 import file domain/infrastructure；附件配置、上传关联、越权和已删除文件行为保持 | Done |
| PLATFORM-SCALE-S01-M4-T06 | 实现 platform object 公共 SPI/command/query adapter | resolver、registry、object summary、link/favorite 访问通过 contract；反向发现不依赖 provider 私有包 | Done |
| PLATFORM-SCALE-S01-M4-T07 | 迁移 project resolver、空间、迁移和 legacy 服务的 platform 依赖 | project 不再 import platform application/domain/infrastructure 私有类型；对象导航、收藏、迁移和最小披露回归通过 | Done |
| PLATFORM-SCALE-S01-M4-T08 | 实现 event transactional outbox contract 与 provider adapter | project 可同事务 append 事件且不 import event infrastructure；幂等键、payload、回滚和现有 Worker 消费兼容 | Done |
| PLATFORM-SCALE-S01-M4-T09 | 实现 audit append contract 与 provider adapter | project 可记录统一审计且不 import audit application/infrastructure；请求边界和 S04 hash-only 脱敏保持 | Done |
| PLATFORM-SCALE-S01-M4-T10 | 迁移字段、类型、预置、空间成员和 legacy 项目的 event/audit 调用 | project 全部 event/audit 依赖只指向 contract；事务失败不留下孤立审计/事件，重放不重复副作用 | Done |
| PLATFORM-SCALE-S01-M4-T11 | 实现 project-IM 最小合同并迁移 legacy ProjectService | project 不再 import ImRepository；消息关联行为、权限、撤回/删除边界和失败语义有集成测试 | Done |
| PLATFORM-SCALE-S01-M4-T12 | 清理 project 剩余 foreign application/domain 私有类型 | project 的 foreign import 只指向合同包；DTO/value 转换在边界 adapter 完成，不复制对方权限事实 | Done |
| PLATFORM-SCALE-S01-M4-T13 | 通过技术 SPI/adapter 清理 shared 对 identity 和 knowledge 的反向依赖 | Jwt/WebSocket 认证和旧协同分派行为不变，shared 源码不再 import 任一业务模块 | Done |
| PLATFORM-SCALE-S01-M4-T14 | 建立事务、并发、幂等、回滚、最小披露和错误兼容回归 | 公共合同替换不改变 S02-S04 API 状态码/错误码，不破坏同事务 outbox/audit 或跨 workspace 隔离 | Done |
| PLATFORM-SCALE-S01-M4-T15 | 执行 project 零 foreign private import、shared 零业务 import 和 S04 真实回归 | 架构门禁无 project/shared P0 例外；S04 后端目标集与 4 条真实隔离浏览器流程 fresh 通过 | Done |
| PLATFORM-SCALE-S01-M4-T16 | 完成 M4 实现定位、例外收缩和影响范围质量门 | 基线准确减少且无替代性全局 helper；执行报告逐 Task 闭环，checkpoint、lint/build 和相关集成测试通过 | Done |

### PLATFORM-SCALE-S01-M5 Stage 评审、route-final 与 S02 准入

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PLATFORM-SCALE-S01-M5-T01 | 复核 M1-M4 的 52 项实现任务与 M5 的 10 项收口合同 | 路线共 62 项均有唯一可重复证据；无空占位、静默豁免、过期例外或未决验收阻断 | Done |
| PLATFORM-SCALE-S01-M5-T02 | 复跑后端/前端依赖图、SCC、allowlist 和触及即收敛门禁 | 新增 foreign infrastructure、shared 反向依赖和前端深路径为零；project P0 已清零，历史差异可解释 | Done |
| PLATFORM-SCALE-S01-M5-T03 | 复跑 V001 至最新表清单、owner、SQL read/write 和例外验收 | 当前有效表全部有唯一 owner；foreign write 为零；只读例外精确、有 owner/退出 Stage 且未扩散 | Done |
| PLATFORM-SCALE-S01-M5-T04 | 验收公共合同、provider adapter、事务和最小披露 | contract 不反向依赖 provider；outbox/audit 同事务，identity/file/platform/IM 访问不泄露隐藏事实 | Done |
| PLATFORM-SCALE-S01-M5-T05 | 执行 S02-S04 项目空间、类型和字段完整回归 | 空间、成员、类型、字段、规则、复杂引用、配置 UI 和六身份真实路径无行为回退 | Done |
| PLATFORM-SCALE-S01-M5-T06 | 执行路线级后端、迁移、前端、协同、工作台、安全和 `route-final` | 全部门禁使用本轮 fresh 证据通过；失败、跳过或豁免形成阻断决定，不能静默关闭 | Done |
| PLATFORM-SCALE-S01-M5-T07 | 冻结 S02 的运行角色、Bean/定时任务和配置准入 | `api/worker/event-gateway/maintenance` 当前责任、默认值、禁止组合、health/readiness 和回退点可直接拆 Task | Done |
| PLATFORM-SCALE-S01-M5-T08 | 冻结双 API 部署、Nginx、认证、幂等、初始化和故障验收输入 | 两实例拓扑、依赖、优雅停机、单节点退出、数据库/Redis/MinIO 边界和回滚方案明确，不冒充已实现 | Done |
| PLATFORM-SCALE-S01-M5-T09 | 输出 Go/No-Go、剩余例外和 PROJECT-PLATFORM 恢复条件 | 明确进入 S02、补充 S01 或停止；每个剩余例外有 owner、期限和后续 Stage，不把容量草案当承诺 | Done |
| PLATFORM-SCALE-S01-M5-T10 | 更新专项、目标架构、当前事实、修订号和 Stage 状态 | Program/target/index/roadmap 一致；S01 completed 等待归档，S02 不在同一路线提前激活 | Done |

## 6. Stage 全局验收标准

- 后端模块、前端 feature 和 SQL/table owner 清单由跨平台命令生成，计数语义、schemaVersion 和差异可重复。
- 后端 ArchUnit 与工作台门禁覆盖 foreign infrastructure、foreign private package、shared -> modules、contract 反向依赖和历史 ratchet。
- 前端门禁使用 TypeScript AST 和 tsconfig 解析静态/动态 import、重导出、require、别名和 public entry，不依赖单纯文本正则。
- 每张当前有效业务表只有一个 owner；foreign write 为零，search/admin/workspace 只读例外精确且有退出 Stage。
- 新增 foreign infrastructure import 为零；触及历史违规只能保持或减少，不能扩大方向、文件或数量。
- project 只经公开 contract 访问 identity、file、platform、event、audit 和 IM；shared 不再 import 业务模块。
- outbox/audit 通过公共 port 写入但仍与业务命令同事务；幂等、回滚、敏感信息和最小披露语义不回退。
- PROJECT-PLATFORM-S02-S04 的后端和真实隔离浏览器关键路径通过；API/错误/权限/数据结构不因边界重构改变。
- S01 不交付双 API、独立 Worker、通用实时网关、旧协同退出或容量承诺；S02 准入包可直接生成下一路线。
- M5 执行完整迁移、后端、前端、协同、工作台、安全、浏览器和 `route-final`，并输出明确 Go/No-Go。

## 7. 当前执行入口

从 `PLATFORM-SCALE-S01-M1` 开始，按 Milestone 分轮推进。不得直接执行长期专项中的后续 Stage，也不得在 S01 当前路线内启动 PROJECT-PLATFORM-S05。
