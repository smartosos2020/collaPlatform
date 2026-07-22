---
title: PROJECT-PLATFORM-S03 工作项类型定义底座当前执行路线
status: completed
route: PROJECT-PLATFORM-S03
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 11
stage: PROJECT-PLATFORM-S03
stage_final_milestone: PROJECT-PLATFORM-S03-M5
last_code_check: 2026-07-22
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S03 工作项类型定义底座

## 1. Stage 目标

在 S02 已交付的项目空间、成员和空间治理边界内，建立统一的工作项类型定义底座，使“项目、需求、任务、缺陷、迭代、版本”成为空间内受保护、可配置、可排序和可停用的类型定义，而不是继续扩展固定业务模块。

S03 交付类型定义与首个不可变发布骨架版本、空间配置 API/UI、用户执行侧 active 类型摘要、研发预置类型和完整授权/审计/并发合同。S03 不交付动态字段、表单布局、完整草稿发布流水线、工作项实例、legacy project/issue 数据迁移或业务写切流；这些能力分别属于 S04、S05、S06 和 S07。

## 2. 固定输入与边界

- 上一 Stage：`PROJECT-PLATFORM-S02` 已完成并归档到 `docs/99-archive/superseded-roadmaps/project-platform-s02-roadmap-completed-2026-07-22.md`。
- 长期专项：`docs/00-product/initiatives/project-platform-program.md` revision 10。
- 冻结准入：`docs/01-architecture/project-platform-target-architecture.md` 第 19 节。
- 目标 schema：`project_work_item_types` 与 `project_work_item_type_versions`；两者携带 `workspace_id`、`space_id` 并受复合外键隔离。
- 类型标识：`type_key` 在空间内永久唯一，符合 `[a-z][a-z0-9_]*`；展示名可改，键不可改也不可复用。
- 生命周期：类型 `active <-> disabled -> retired`；版本首期只创建首个 `published` 骨架版本，published/superseded 不可变。
- 研发预置类型：`project`、`requirement`、`task`、`bug`、`iteration`、`release`；系统类型不可删除、改键或不可逆 retire，但允许空间级停用、恢复和排序。
- 授权边界：空间 owner/admin 可配置；member/guest 只读 active 摘要；非成员最小披露；企业 `project.manage` 不自动获得配置或内容权限。
- 兼容边界：现有 `/projects`、`/issues`、平台对象 resolver、IM 项目群和 legacy 写路径保持不变；S03 不建立双写和第二套工作项实例运行时。

## 3. 执行规则

1. 每轮 AI 工作循环只推进一个 Milestone，任务按编号逐项完成；不得用文件存在、DTO 返回或静态 mock 代替行为闭环。
2. M1-M4 使用影响范围验证和 `stage` finish；M5 是 Stage 最终里程碑，必须使用 `route-final`。
3. Schema 改动必须由 Flyway、领域约束、Repository、Service、Controller 和 Testcontainers 集成测试共同闭环。
4. 权限以服务端动作计算和 `availableActions` 为唯一来源；前端不得根据角色名或错误文本补算授权。
5. 用户或配置 UI 行为必须使用真实登录态、真实 API、真实 PostgreSQL 的隔离浏览器验证；拦截核心 API 的浏览器测试不算完成证据。
6. 首个 published 骨架版本必须稳定哈希且不可变；不得提前引入 S04 字段、S05 布局、S08/S09 流程或 S11 细粒度权限配置。
7. 研发预置类型的安装和既有空间补齐必须幂等、可重试、可审计，不得创建 legacy project/issue 或规范工作项实例。
8. 发现第 19 节输入与实现事实冲突时，先登记规划变更和影响 Stage，不为维持任务数量压缩设计问题。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 收口证据 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M1 | 类型定义物理模型、领域合同与持久化 | S02 准入包 | `docs/90-reports/project-platform-s03-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S03-M2 | 类型配置 API、授权、审计与治理投影 | M1 | `docs/90-reports/project-platform-s03-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S03-M3 | 空间配置 UI 与用户执行侧类型入口 | M1-M2 | `docs/90-reports/project-platform-s03-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S03-M4 | 研发预置类型、既有空间补齐与兼容边界 | M1-M3 | `docs/90-reports/project-platform-s03-m4-execution-report.md` | Completed |
| PROJECT-PLATFORM-S03-M5 | Stage 评审、route-final 与 S04 准入 | M1-M4 | `docs/90-reports/project-platform-s03-m5-execution-report.md` | Completed |

## 5. 详细任务

### PROJECT-PLATFORM-S03-M1 类型定义物理模型、领域合同与持久化

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M1-T01 | 复核 S02 空间聚合、成员授权、审计/outbox、平台对象与 S03 第 19 节准入包 | 形成实现定位表；复用边界、缺口和禁止提前实现项均可定位到代码/schema | Done |
| PROJECT-PLATFORM-S03-M1-T02 | 新增类型定义与类型版本 Flyway schema、约束和索引 | V001 至最新可从空库迁移；workspace/space 复合外键、唯一键、状态检查和查询索引完整 | Done |
| PROJECT-PLATFORM-S03-M1-T03 | 实现 `WorkItemTypeDefinition`、`WorkItemTypeVersion`、状态和 `typeKey` 值对象 | 标识、状态、系统保护和生命周期规则由领域层强制，非法输入返回稳定错误 | Done |
| PROJECT-PLATFORM-S03-M1-T04 | 定义首个 published 骨架 config、规范序列化与 `config_hash` | 相同语义产生相同哈希；config 不含字段、布局、流程、角色图；发布行不可变 | Done |
| PROJECT-PLATFORM-S03-M1-T05 | 实现类型定义/版本 Repository、查询投影和 workspace 隔离 | 按空间列表、详情、状态和排序查询不跨 workspace；版本只能从所属类型读取 | Done |
| PROJECT-PLATFORM-S03-M1-T06 | 实现创建类型与首个 published v1 的原子事务 | 类型、v1 和 `current_version_id` 同成同败；重复请求与唯一冲突不会留下半成品 | Done |
| PROJECT-PLATFORM-S03-M1-T07 | 实现 active、disabled、retired 生命周期和引用保护接口 | 合法转换明确；retired 不可恢复；未来引用检查具备扩展点且当前空引用不伪造数据 | Done |
| PROJECT-PLATFORM-S03-M1-T08 | 实现 aggregate version、排序更新和并发冲突保护 | 并发编辑、创建同键和重排不会静默覆盖；冲突映射为稳定 version/type_key 错误 | Done |
| PROJECT-PLATFORM-S03-M1-T09 | 建立 schema、Repository、原子创建、不可变版本和并发集成测试 | 覆盖空库迁移、约束负例、跨 workspace、事务回滚、哈希稳定和真实并发 | Done |
| PROJECT-PLATFORM-S03-M1-T10 | 完成 M1 实现说明、schema 决策和目标质量门 | 执行报告可逐项复核；目标后端测试、Flyway 与 planning 合同通过 | Done |

### PROJECT-PLATFORM-S03-M2 类型配置 API、授权、审计与治理投影

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M2-T01 | 固定空间配置、用户摘要和治理计数 DTO、错误与幂等合同 | DTO 家族不混用；六类规定错误有稳定 code/status；请求重放语义明确 | Done |
| PROJECT-PLATFORM-S03-M2-T02 | 实现空间配置侧类型列表和详情 API | owner/admin 可读完整配置；排序稳定；停用/retired 状态和当前版本信息准确 | Done |
| PROJECT-PLATFORM-S03-M2-T03 | 实现类型创建和可编辑展示属性更新 API | 服务端校验 key/name/icon/description；typeKey 创建后不可改；乐观锁生效 | Done |
| PROJECT-PLATFORM-S03-M2-T04 | 实现复制和批量重排 API | 复制要求新的本地 typeKey 并生成独立 v1；重排原子、无重复且只影响同一空间 | Done |
| PROJECT-PLATFORM-S03-M2-T05 | 实现 disable、restore 和 retire 动作 API | 生命周期守卫、幂等和系统类型保护一致；错误可操作且不泄露隐藏资源 | Done |
| PROJECT-PLATFORM-S03-M2-T06 | 实现服务端类型动作计算与 `availableActions` | owner/admin/member/guest、状态、系统属性和空间状态组合均由统一策略解释 | Done |
| PROJECT-PLATFORM-S03-M2-T07 | 落实空间角色与企业 RBAC 分层授权 | owner/admin 可写；member/guest 仅 active 摘要；非成员隐藏；仅企业管理员不能配置 | Done |
| PROJECT-PLATFORM-S03-M2-T08 | 接入类型写操作审计、outbox、request id 和幂等去重 | 创建、编辑、复制、排序及生命周期动作均有 actor、对象、前后状态和请求关联 | Done |
| PROJECT-PLATFORM-S03-M2-T09 | 扩展用户 active 类型摘要与企业治理只读类型计数 | 用户接口不暴露 config/版本/停用类型；治理接口只有计数且不授予详情访问 | Done |
| PROJECT-PLATFORM-S03-M2-T10 | 建立 API 正反权限、并发、幂等、审计和最小披露集成测试 | 覆盖四类空间角色、非成员、企业管理员、跨空间/跨 workspace 和生命周期组合 | Done |
| PROJECT-PLATFORM-S03-M2-T11 | 完成 M2 API 合同、权限矩阵和目标质量门 | OpenAPI/调用文档与实现一致；目标测试和安全静态门禁通过 | Done |

### PROJECT-PLATFORM-S03-M3 空间配置 UI 与用户执行侧类型入口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M3-T01 | 建立前端类型 DTO、API client、查询键和失效策略 | 类型列表、详情和动作调用类型安全；空间切换不会复用错误缓存 | Done |
| PROJECT-PLATFORM-S03-M3-T02 | 在空间设置中增加工作项类型路由、列表和详情结构 | owner/admin 可进入配置；刷新、返回和直接深链保持 space/type 上下文 | Done |
| PROJECT-PLATFORM-S03-M3-T03 | 实现创建和编辑类型交互 | key/name/icon/描述校验与服务端一致；保存中、成功、冲突和版本过期反馈明确 | Done |
| PROJECT-PLATFORM-S03-M3-T04 | 实现复制类型与新 key 冲突处理 | 复制结果为独立自定义类型；冲突不丢输入；成功后选中正确类型 | Done |
| PROJECT-PLATFORM-S03-M3-T05 | 实现类型排序交互及并发失败恢复 | 键盘与指针均可排序；失败回滚本地顺序；不跨状态或跨空间移动 | Done |
| PROJECT-PLATFORM-S03-M3-T06 | 实现停用、恢复和 retire 危险动作体验 | 不可逆动作有确认；系统保护、无权和空间停用状态禁用原因可解释 | Done |
| PROJECT-PLATFORM-S03-M3-T07 | 严格按 `availableActions` 渲染配置动作和只读状态 | 前端不硬编码角色授权；member/guest、企业管理员及只读空间无越权入口 | Done |
| PROJECT-PLATFORM-S03-M3-T08 | 在用户执行侧接入 active 类型摘要和无可用类型状态 | 只展示 active 类型的名称、key、图标和排序；不把类型配置页当日常执行首页 | Done |
| PROJECT-PLATFORM-S03-M3-T09 | 完成加载、空态、错误、可访问性和 1366/1440/窄屏适配 | 焦点、键盘、确认框和错误播报可用；页面不整体溢出，局部滚动边界清晰 | Done |
| PROJECT-PLATFORM-S03-M3-T10 | 建立 owner/admin/member/guest/企业管理员真实隔离浏览器链路并完成 M3 收口 | 真实 API/数据库完成创建、编辑、复制、排序、停用/恢复和权限负例；lint/build 通过 | Done |

### PROJECT-PLATFORM-S03-M4 研发预置类型、既有空间补齐与兼容边界

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M4-T01 | 建立六类研发预置模板目录、稳定 key 和展示语义 | project/requirement/task/bug/iteration/release 定义版本化、可测试且不含动态字段或流程配置 | Done |
| PROJECT-PLATFORM-S03-M4-T02 | 实现既有空间预置类型幂等补齐机制 | 只补缺失系统类型；重复、并发和中断重跑收敛；自定义同 key 冲突进入明确失败清单 | Done |
| PROJECT-PLATFORM-S03-M4-T03 | 将新空间创建与预置类型初始化组成可靠业务事务 | 新空间最终具备完整预置目录；失败可恢复且不产生半初始化可用空间 | Done |
| PROJECT-PLATFORM-S03-M4-T04 | 固化系统类型不可删除、改键、覆盖或 retire 的保护 | 允许停用、恢复和排序；复制产生自定义类型；绕过 UI 的请求同样被拒绝 | Done |
| PROJECT-PLATFORM-S03-M4-T05 | 实现预置类型启停、排序和重新补齐的审计/幂等语义 | 系统类型不会重复；管理员可解释来源和当前状态；重放不产生重复事件 | Done |
| PROJECT-PLATFORM-S03-M4-T06 | 固化 legacy project/issue 写路径与 resolver 不变的架构守卫 | S03 操作不创建/更新 legacy 数据或工作项实例，不新增第二套 Project Controller/权限引擎 | Done |
| PROJECT-PLATFORM-S03-M4-T07 | 验证治理计数、空间目录和现有项目用户入口兼容 | 类型计数准确；治理后台不出现配置写入口；原 `/projects`、`/issues` 主路径无回归 | Done |
| PROJECT-PLATFORM-S03-M4-T08 | 建立既有/新空间补齐、并发、冲突、失败重试和数据零污染集成测试 | 补齐前后 count/hash 可核对；失败可重试；legacy 四表与规范实例表保持零写入 | Done |
| PROJECT-PLATFORM-S03-M4-T09 | 建立预置类型和现有项目入口真实浏览器兼容验证 | 新旧空间均显示正确预置类型；停用/恢复/排序可见；legacy 深链和项目操作不受影响 | Done |
| PROJECT-PLATFORM-S03-M4-T10 | 完成 M4 预置目录、补齐说明、兼容证据和目标质量门 | 运行手册与报告可复核；目标后端、前端和浏览器验证通过 | Done |

### PROJECT-PLATFORM-S03-M5 Stage 评审、route-final 与 S04 准入

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M5-T01 | 复核 M1-M4 任务、实现、schema、API、权限和浏览器证据 | 每个任务有实现与可重复证据；无证据、跳过和未决问题不得静默关闭 | Done |
| PROJECT-PLATFORM-S03-M5-T02 | 更新当前产品范围、当前架构、对象模型和技术选型事实 | 只登记已实现能力；S04-S07 目标能力与 S03 当前事实清晰分离 | Done |
| PROJECT-PLATFORM-S03-M5-T03 | 执行类型生命周期、版本不可变、权限、安全、并发和隔离专项验收 | 无跨 workspace/空间泄露、无 published 改写、无企业管理员越权及静默覆盖 | Done |
| PROJECT-PLATFORM-S03-M5-T04 | 复核 S04、S06、S07 扩展点和禁止提前实现边界 | 字段挂载点、版本发布边界和实例 `type_version_id` 合同可直接承接且未被堵死 | Done |
| PROJECT-PLATFORM-S03-M5-T05 | 执行 V001 至最新空库迁移及既有空间预置补齐 rehearsal | 空库与升级路径均可重复；补齐可校验、失败可恢复，legacy/实例数据零污染 | Done |
| PROJECT-PLATFORM-S03-M5-T06 | 执行空间 owner/admin/member/guest、非成员和企业管理员真实隔离浏览器全链路 | 配置、用户摘要、系统保护、生命周期、深链和最小披露均符合预期 | Done |
| PROJECT-PLATFORM-S03-M5-T07 | 执行路线级完整测试、安全、迁移、构建和 `route-final` 门禁 | 全部门禁通过；任何失败或豁免有明确阻断决定，不以旧日志代替新鲜证据 | Done |
| PROJECT-PLATFORM-S03-M5-T08 | 输出 S03 Go/No-Go、剩余风险和 S04 动态字段准入包 | 明确进入 S04、补充 S03 或暂停三选一；schema/API/权限/索引输入可直接拆 Task | Done |
| PROJECT-PLATFORM-S03-M5-T09 | 更新专项状态、修订号、目标架构和下一 Stage | S03 完成态、规划变更和 S04 准入同步；路线 completed 等待归档 | Done |

## 6. Stage 全局验收标准

- 所有工作项类型归属唯一 workspace/space，typeKey 永久唯一，跨 workspace/空间访问和关系均被后端拒绝。
- 创建类型时原子生成首个 published 骨架版本；规范 config hash 稳定，published/superseded 数据不可更新或删除。
- owner/admin、member/guest、非成员和企业管理员边界由服务端统一计算；用户摘要不泄露 config、版本或停用类型。
- 六类研发预置类型可幂等安装、补齐、停用、恢复和排序，但不可删除、改键、覆盖或 retire。
- 空间配置 UI 与用户执行入口职责分离，全部动作遵从 `availableActions`，真实浏览器覆盖关键正反路径。
- 现有 project/issue 业务写、resolver、IM 群和 legacy 深链保持权威；S03 不创建工作项实例、不迁移 legacy 数据、不建立双写。
- M1-M4 使用影响范围验证；M5 使用空库/升级 rehearsal、真实隔离浏览器和 `route-final` 完成 Stage 收口。

## 7. 当前执行入口

`PROJECT-PLATFORM-S03` 已完成并通过 Go S04 评审，Program revision 为 11。当前路线只等待归档，不再接收新的执行任务；下一步先归档本路线，再依据目标架构第 20 节生成并激活 `PROJECT-PLATFORM-S04` 路线。归档前不得直接在本文件追加 S04 Task，也不得提前实现 S06 发布或 S07 实例能力。
