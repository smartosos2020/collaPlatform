---
title: PROJECT-PLATFORM-S04 动态字段、选项和校验规则当前执行路线
status: active
route: PROJECT-PLATFORM-S04
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 12
stage: PROJECT-PLATFORM-S04
stage_final_milestone: PROJECT-PLATFORM-S04-M5
last_code_check: 2026-07-22
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S04 动态字段、选项和校验规则

## 1. Stage 目标

在 S03 已交付的空间级工作项类型定义和不可变 published v1 骨架之上，建立动态字段的定义、类型能力、选项、默认值和结构化校验规则，使空间 owner/admin 能够编排后续完整配置版本所需的字段图，并为 S05 布局与 S06 发布提供稳定输入。

S04 只交付“字段配置编排底座”，不创建工作项实例或字段值，不迁移 legacy issue，不原地改写 S03 published v1，不交付布局、字段级权限、流程或完整 draft/publish。S06 负责把 S04 字段图物化为新的不可变类型版本，S07 负责显式绑定 `type_version_id` 的规范 WorkItem 实例。

## 2. 固定输入与边界

- 准入输入：`docs/01-architecture/project-platform-target-architecture.md` 第 20 节。
- 空间边界：字段定义归属唯一 workspace/space/type definition，关系使用复合约束；企业 `project.manage` 不授予空间字段配置权。
- 标识边界：`field_key` 在类型定义内永久唯一，创建后不可修改或复用；系统字段与自定义字段共享定义合同但有不同保护策略。
- 类型边界：类型注册表是服务端唯一能力来源，负责 storage kind、配置 schema、operators、默认值和 filter/sort/index capability；客户端不得自行推断。
- 版本边界：S04 编辑的是待发布字段配置图，不更新 published v1；完整 draft、publish、diff、rollback 和 `current_version_id` 切换属于 S06。
- 实例边界：S04 不创建 `project_work_items`、字段值表、实例 API、`work_item` resolver 或 legacy 双写。
- UI 边界：配置 UI 只服务空间 owner/admin；member/guest 可读取后续执行所需的已生效摘要，但本 Stage 不伪造未发布字段为运行时字段。
- 验证边界：每个 Milestone 使用影响范围验证，M5 执行真实隔离浏览器、V001 至最新迁移 rehearsal 和 `route-final`。

## 3. 执行规则

1. 按 Milestone 分轮执行，不跨 Milestone 启动工作循环；每个 Task 必须有实现、自动化证据和执行报告行。
2. 数据库隔离、领域规则和服务端授权是最终防线；UI 隐藏不能代替后端拒绝。
3. 字段配置使用结构化对象和规范序列化，不用任意字符串拼接，不按字段动态创建数据库列。
4. 任何索引或 typed projection 必须由真实查询合同和基准证明，不能预建无边界索引。
5. 复杂字段不得把目标对象标题、附件元数据或用户身份快照当作权限事实；读取时仍由对应模块解析。
6. 配置写操作必须携带 request id 和 aggregate version，接入审计/outbox；重复请求收敛，并发冲突不得静默覆盖。
7. 若实现需要 S05 布局、S06 发布或 S07 实例才能成立，必须停下并回到 Program/目标架构调整，不以临时表或兼容字段绕过。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M1 | 字段定义物理模型、类型注册与配置 API | S03 准入包 | `docs/90-reports/project-platform-s04-m1-execution-report.md` | Pending |
| PROJECT-PLATFORM-S04-M2 | 选项、默认值、必填与结构化校验规则 | M1 | `docs/90-reports/project-platform-s04-m2-execution-report.md` | Pending |
| PROJECT-PLATFORM-S04-M3 | 人员、日期、附件、URL 与工作项引用字段 | M1-M2 | `docs/90-reports/project-platform-s04-m3-execution-report.md` | Pending |
| PROJECT-PLATFORM-S04-M4 | 字段配置 UI、能力查询与性能预算 | M1-M3 | `docs/90-reports/project-platform-s04-m4-execution-report.md` | Pending |
| PROJECT-PLATFORM-S04-M5 | Stage 评审、route-final 与 S05/S06 准入 | M1-M4 | `docs/90-reports/project-platform-s04-m5-execution-report.md` | Pending |

## 5. 详细任务

### PROJECT-PLATFORM-S04-M1 字段定义物理模型、类型注册与配置 API

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M1-T01 | 复核 S03 类型版本、动作策略、引用守卫和第 20 节 S04 准入包 | 输出实现定位表；复用点、缺口、S05-S07 承接点和禁止提前实现项均可定位 | Pending |
| PROJECT-PLATFORM-S04-M1-T02 | 冻结首批字段类型、storage kind、operator 与 capability 矩阵 | text/number/boolean/select/user/date/datetime/url/attachment/work_item_reference 的输入、输出和能力定义唯一 | Pending |
| PROJECT-PLATFORM-S04-M1-T03 | 新增字段定义与配置编排 Flyway schema、复合约束和索引 | V001 至最新可空库迁移；workspace/space/type 复合关系、永久 key、状态、排序和乐观版本约束完整 | Pending |
| PROJECT-PLATFORM-S04-M1-T04 | 实现服务端字段类型注册表与配置 schema 描述 | 未注册类型不能写入；每类类型返回稳定 storage/config/operator/filter/sort/index capability | Pending |
| PROJECT-PLATFORM-S04-M1-T05 | 实现 FieldDefinition、FieldKey、状态和系统保护领域合同 | key 创建后不可改；active/disabled/retired 转换、系统字段保护和错误码稳定 | Pending |
| PROJECT-PLATFORM-S04-M1-T06 | 实现字段配置规范序列化、默认空配置和稳定 hash | 相同语义产生相同结果；配置不含布局、流程、实例值或未声明扩展属性 | Pending |
| PROJECT-PLATFORM-S04-M1-T07 | 实现字段定义 Repository、查询投影和 workspace/space/type 隔离 | 类型内列表、详情、状态和排序查询稳定；跨 workspace/空间/type 关系被拒绝 | Pending |
| PROJECT-PLATFORM-S04-M1-T08 | 实现字段创建、展示属性更新、生命周期和排序原子服务 | request id 幂等、aggregate version、同 key 并发和批量重排不留下半成品或静默覆盖 | Pending |
| PROJECT-PLATFORM-S04-M1-T09 | 实现字段类型目录及空间字段配置 API/DTO/错误合同 | owner/admin 可配置；member/guest、非成员和企业管理员遵守最小披露；DTO 不暴露实现类 | Pending |
| PROJECT-PLATFORM-S04-M1-T10 | 接入统一 action policy、审计、outbox 和命令回执 | 全部字段写操作记录 actor、对象、前后状态、request id；重放不产生重复事件 | Pending |
| PROJECT-PLATFORM-S04-M1-T11 | 建立领域、schema、Repository、API、权限、幂等和并发测试并完成 M1 收口 | PostgreSQL 约束正反例、跨边界、事务回滚和 OpenAPI 可复核；目标门禁通过 | Pending |

### PROJECT-PLATFORM-S04-M2 选项、默认值、必填与结构化校验规则

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M2-T01 | 冻结 option、default 与 validation rule 的结构化 schema 和版本字段 | 配置对象可机器校验和演进；不接受脚本文本、SQL 或客户端私有规则 | Pending |
| PROJECT-PLATFORM-S04-M2-T02 | 新增字段选项持久化、稳定 option key、排序和状态约束 | option key 在字段内永久唯一；颜色、名称、排序可改，停用不复用 key | Pending |
| PROJECT-PLATFORM-S04-M2-T03 | 实现 single_select/multi_select 选项目录和值域配置 | 重复 key、非法颜色、空名称、越界数量和跨字段选项引用返回稳定错误 | Pending |
| PROJECT-PLATFORM-S04-M2-T04 | 实现各字段类型默认值规范化与兼容校验 | 默认值按 storage kind 规范化；类型不匹配、未知选项和越界值在服务端拒绝 | Pending |
| PROJECT-PLATFORM-S04-M2-T05 | 实现 required、长度、数值范围、格式和允许值规则 | 规则组合无矛盾；min/max、regex、precision/scale 和 allowed values 有界且可解释 | Pending |
| PROJECT-PLATFORM-S04-M2-T06 | 实现规则注册、解析、规范序列化和前向兼容策略 | 未知规则版本拒绝写入；已知旧版本可稳定读取；hash 不受属性顺序影响 | Pending |
| PROJECT-PLATFORM-S04-M2-T07 | 实现选项、默认值和规则的聚合原子更新服务 | 一次命令同成同败；并发修改返回 version conflict；失败不污染既有配置 | Pending |
| PROJECT-PLATFORM-S04-M2-T08 | 实现选项/规则配置 API、availableActions 与可操作错误 | owner/admin 写，其他身份只获得合同允许的摘要或隐藏结果；客户端无需解析错误文本 | Pending |
| PROJECT-PLATFORM-S04-M2-T09 | 接入选项和规则变更审计、outbox、幂等与差异摘要 | 审计不泄露敏感默认值；重复请求不重复事件；前后差异可追踪 | Pending |
| PROJECT-PLATFORM-S04-M2-T10 | 建立类型兼容、组合规则、生命周期、权限、并发和回滚测试 | 覆盖 select/number/text/boolean 正反例、跨字段注入、重复命令和真实并发 | Pending |
| PROJECT-PLATFORM-S04-M2-T11 | 完成 M2 配置合同、错误矩阵、迁移证据和目标质量门 | 执行报告逐 Task 闭环；schema/API 文档与实现一致，影响范围门禁通过 | Pending |

### PROJECT-PLATFORM-S04-M3 人员、日期、附件、URL 与工作项引用字段

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M3-T01 | 冻结复杂字段的规范值、配置、失效引用与最小披露合同 | user/date/datetime/url/attachment/reference 均有稳定 JSON 形态和安全降级语义 | Pending |
| PROJECT-PLATFORM-S04-M3-T02 | 实现 user 字段的选择范围、主体类型和状态校验配置 | 可限制成员/部门/用户组及数量；跨 workspace 主体和失效主体不泄露身份信息 | Pending |
| PROJECT-PLATFORM-S04-M3-T03 | 实现 date/datetime 的时区、精度、范围和默认策略 | 日期与时间语义分离；UTC 存储/展示时区明确；相对默认值受白名单约束 | Pending |
| PROJECT-PLATFORM-S04-M3-T04 | 实现 URL 字段协议、长度、规范化和危险 scheme 拒绝 | 只允许规定协议；控制字符、凭据泄露和超长输入被服务端拒绝 | Pending |
| PROJECT-PLATFORM-S04-M3-T05 | 实现 attachment 字段的文件数量、类型、大小和引用配置 | 只保存规范 file id；不复制文件权限；越权、跨 workspace 和已删除文件使用安全状态 | Pending |
| PROJECT-PLATFORM-S04-M3-T06 | 实现 work_item_reference 的目标类型、数量和关系能力配置 | 只描述未来可引用类型与方向；S07 前不创建实例引用、反向关系或平台对象 | Pending |
| PROJECT-PLATFORM-S04-M3-T07 | 为复杂字段注册 operator、filter/sort/index capability | 不可排序类型明确返回 false；引用型操作符不绕过目标模块权限 | Pending |
| PROJECT-PLATFORM-S04-M3-T08 | 实现复杂字段配置 API/DTO 与统一序列化适配 | API 不返回用户/文件/目标标题快照作为权限事实；错误合同跨类型一致 | Pending |
| PROJECT-PLATFORM-S04-M3-T09 | 接入复杂配置的权限、安全、审计和敏感字段脱敏 | 非成员/企业管理员无空间配置访问；审计不记录 URL 凭据、文件密钥或个人敏感值 | Pending |
| PROJECT-PLATFORM-S04-M3-T10 | 建立复杂类型单元、集成、权限和恶意输入测试 | 覆盖时区边界、失效主体、危险 URL、文件越权、跨空间引用和未知类型 | Pending |
| PROJECT-PLATFORM-S04-M3-T11 | 完成 M3 类型目录、序列化样例、安全说明和目标质量门 | 文档与 OpenAPI 可直接供 UI 使用；目标后端、安全和迁移检查通过 | Pending |

### PROJECT-PLATFORM-S04-M4 字段配置 UI、能力查询与性能预算

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M4-T01 | 建立前端字段类型/定义/选项/规则 DTO、API client 和查询键 | 类型安全；空间/type 切换不复用错误缓存；错误码映射统一 | Pending |
| PROJECT-PLATFORM-S04-M4-T02 | 在工作项类型配置中增加字段列表、详情和创建入口 | owner/admin 可进入；深链、刷新、返回和 type 上下文稳定；不冒充运行时字段 | Pending |
| PROJECT-PLATFORM-S04-M4-T03 | 实现字段创建、编辑、停用/恢复/retire 和排序交互 | 校验与服务端一致；保存中、冲突、失败回滚和不可逆确认可理解 | Pending |
| PROJECT-PLATFORM-S04-M4-T04 | 实现字段类型选择器与类型专属配置面板 | 只按服务端 capability 渲染；切换类型不残留不兼容配置；键盘和读屏可用 | Pending |
| PROJECT-PLATFORM-S04-M4-T05 | 实现选项编辑器、默认值和结构化规则表单 | 选项排序/状态、类型化默认值和规则冲突即时提示；服务端仍做最终校验 | Pending |
| PROJECT-PLATFORM-S04-M4-T06 | 实现 user/date/attachment/url/reference 的专属配置体验 | 范围、时区、协议、文件限制和目标类型配置低噪音且不泄露隐藏对象 | Pending |
| PROJECT-PLATFORM-S04-M4-T07 | 严格按 `availableActions` 与只读摘要渲染角色和生命周期状态 | 前端不硬编码角色；member/guest、非成员、企业管理员及停用空间无越权入口 | Pending |
| PROJECT-PLATFORM-S04-M4-T08 | 实现字段类型能力查询与配置目录筛选/排序投影 | 只查询字段定义及 capability，不伪造实例值查询；分页、排序和过滤结果稳定 | Pending |
| PROJECT-PLATFORM-S04-M4-T09 | 建立独立合成数据性能基准与索引预算 | 大类型/多字段/多选项配置列表达到预算；查询计划可复核，无按字段动态 DDL | Pending |
| PROJECT-PLATFORM-S04-M4-T10 | 完成加载、空态、错误、可访问性和 1366/1440/窄屏适配 | 页面不整体溢出；局部滚动、焦点、键盘、错误播报和确认框可用 | Pending |
| PROJECT-PLATFORM-S04-M4-T11 | 建立 owner/admin/member/guest/非成员/企业管理员真实隔离浏览器闭环并完成 M4 收口 | 真实 API/数据库覆盖字段、选项、规则、排序、生命周期和权限负例；lint/build 通过 | Pending |

### PROJECT-PLATFORM-S04-M5 Stage 评审、route-final 与 S05/S06 准入

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S04-M5-T01 | 复核 M1-M4 实现、schema、API、权限、性能和浏览器证据 | 53 项路线任务均有可重复证据；无证据、跳过和未决问题不得静默关闭 | Pending |
| PROJECT-PLATFORM-S04-M5-T02 | 更新当前产品范围、当前架构、对象模型和技术选型事实 | 只登记已实现字段配置能力；S05-S07 目标与当前事实清晰分离 | Pending |
| PROJECT-PLATFORM-S04-M5-T03 | 执行隔离、永久 key、规则安全、并发、幂等和敏感信息专项验收 | 无跨 workspace/空间/type 泄露，无静默覆盖、危险 URL 或审计敏感值 | Pending |
| PROJECT-PLATFORM-S04-M5-T04 | 执行 V001 至最新空库/升级迁移和配置规模 rehearsal | 空库与升级可重复；约束/索引/hash 可核对；legacy 与实例数据零污染 | Pending |
| PROJECT-PLATFORM-S04-M5-T05 | 执行六类身份真实隔离浏览器和配置性能复验 | 字段/选项/规则正反路径、深链、最小披露和性能预算均符合合同 | Pending |
| PROJECT-PLATFORM-S04-M5-T06 | 复核 S05 布局/字段权限与 S06 发布扩展点 | 字段图能被布局引用并由新 draft/version 物化；published v1 保持不可变 | Pending |
| PROJECT-PLATFORM-S04-M5-T07 | 执行路线级完整测试、安全、迁移、构建和 `route-final` 门禁 | 全部门禁使用本轮 fresh 证据通过；任何失败或豁免形成明确阻断决定 | Pending |
| PROJECT-PLATFORM-S04-M5-T08 | 输出 S04 Go/No-Go、剩余风险和下一 Stage 准入包 | 明确进入 S05/S06、补充 S04 或暂停；schema/API/权限/版本输入可直接拆 Task | Pending |
| PROJECT-PLATFORM-S04-M5-T09 | 更新专项状态、修订号、目标架构和下一 Stage | S04 完成态、规划变更与下一 Stage 准入同步；路线 completed 等待归档 | Pending |

## 6. Stage 全局验收标准

- 字段定义归属唯一 workspace/space/type definition，`field_key` 永久唯一；跨边界关系由数据库、Repository 和 API 三层拒绝。
- 服务端类型注册表是 storage/config/operator/capability 唯一来源；未注册类型、未知规则版本和不兼容默认值不能写入。
- 选项、默认值和规则使用结构化、可版本化、规范序列化合同；聚合更新原子、幂等且有乐观并发保护。
- owner/admin 可配置；member/guest 只获得合同允许的摘要；非成员与仅企业治理管理员遵守最小披露。
- 配置 UI 遵从 `availableActions`，支持完整错误、冲突、键盘、可访问性和响应式状态；六类身份关键路径有真实隔离浏览器证据。
- 配置查询和索引有独立合成数据基准；不按字段动态建列，不用 legacy issue 冒充规范 WorkItem。
- S03 published v1 不被修改；S04 不创建实例、字段值、布局、流程、完整发布流水线或 legacy 双写。
- M5 使用空库/升级 migration rehearsal、性能复验、真实隔离浏览器和 `route-final` 完成 Stage 收口。

## 7. 当前执行入口

`PROJECT-PLATFORM-S04` 已激活。下一轮从 `PROJECT-PLATFORM-S04-M1-T01` 开始，并且只能在单个 Milestone 内按 AI 工作循环推进。S04 固定输入以目标架构第 20 节为准；任何实例值、布局、发布切换或 legacy 迁移需求必须回到 S05-S07，不得塞入本路线。
