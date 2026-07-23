---
title: 项目协作平台长期专项规划
status: active
program: PROJECT-PLATFORM
revision: 13
updated_at: 2026-07-24
planning_mode: rolling
current_stage: none
initiative_index_doc: docs/00-product/initiatives/README.md
target_architecture_doc: docs/01-architecture/project-platform-target-architecture.md
---

# 项目协作平台长期专项规划

## 1. 文档定位

本文是 `PROJECT-PLATFORM` 长期专项的产品与工程总纲，不是可直接执行的第二份路线图。它负责维护长期目标、能力地图、Stage 顺序、依赖、进入条件、退出条件和规划变更；当前可执行任务始终只存在于 `docs/02-roadmap/current-roadmap.md`。

规划采用滚动维护：当前 Stage 细化到 Task，下一 Stage 细化到 Milestone，后续 Stage 只冻结目标、依赖和退出证据。新调研、技术验证、代码事实或真实用户反馈可以修改未来规划，但不得静默改写已完成 Stage 的历史结论，也不得在没有变更记录的情况下改变正在执行 Stage 的目标。

## 2. 专项目标

将当前以 `projects + requirement/task/bug` 为主的研发事项模块演进为面向多团队的可配置协作平台：

- 以项目空间作为成员、配置、权限和数据协作边界。
- 以统一工作项类型和工作项实例承载项目、需求、任务、缺陷、版本、迭代、活动、候选人、交付物等业务对象。
- 通过字段、表单、详情页、流程、角色、关系、视图、自动化和度量的组合适配研发、市场、HR、交付等团队。
- 保留现有项目功能的可用性，通过观测、迁移、切流和删除逐步退出固定 `issues` 产品合同，不建立永久双写兼容层。
- 用户执行界面与空间配置界面职责分离；企业管理后台只承担企业级治理，不承载项目成员的日常协作。

## 3. 核心产品判断

1. “项目”是可配置工作项类型之一，不是所有业务记录必须依附的唯一顶层容器。
2. “空间”是团队、配置、权限和协作规则的边界；空间可以包含多种工作项类型。
3. 流程只是工作项定义的一部分，不能替代字段、页面、角色、关系、视图、资源和自动化模型。
4. 同一平台必须同时支持轻量状态流和复杂节点流，不能强迫所有团队使用一套流程形态。
5. 工作项定义与运行实例必须分离；已发布配置必须版本化，运行中实例不得被后台修改静默破坏。
6. 组织级 RBAC、空间角色、工作项角色、节点负责人和字段编辑权限是不同层级，不得压缩为单一角色表。
7. 跨空间协作通过显式授权、关系和同步规则实现，不通过绕过权限的全局查询实现。

## 4. 规划维护规则

### 4.1 稳定性层级

| 层级 | 稳定性 | 变更规则 |
| --- | --- | --- |
| 专项目标与核心原则 | 高 | 修改必须记录原因、替代方案和影响 Stage |
| 目标能力地图与目标架构 | 中高 | 调研或技术验证后可演进，必须同步目标架构文档 |
| 当前 Stage | 冻结 | 只允许范围内澄清；目标变化必须登记变更并重新检查当前路线 |
| 下一 Stage | 中 | 可调整 Milestone、依赖和准入条件 |
| 远期 Stage | 低 | 可拆分、合并、重排、延期或取消 |
| 已完成 Stage | 不改历史 | 新问题进入 Reopened 或新的修复 Stage |

### 4.2 滚动规划

- 每个 Stage 收口时重新核对产品事实、代码事实、迁移结果、执行报告和真实反馈。
- Stage 最终里程碑必须更新本文的 Stage 状态、当前 Stage、修订号和变更记录。
- 下一 Stage 只有在依赖满足后才能从 `Planned` 改为 `Active` 并写入当前路线。
- 候选需求先进入“规划候选池”，不得直接塞入正在执行的里程碑。
- Stage 数量不是承诺；按真实复杂度调整，不设固定 M8/T08 或固定总阶段数。

## 5. Stage 总览

| Stage | 目标 | 主要依赖 | 状态 | 退出证据 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01 | 当前事实审计、目标领域模型和迁移决策 | 当前项目模块 | Completed | 审计、ADR、迁移分层和 S02 准入包 |
| PROJECT-PLATFORM-S02 | 项目空间、成员和空间治理 | S01 | Completed | 空间生命周期、成员角色、可见性和治理 API 已交付；三轮审计补验完成，M5 评审、无污染迁移 rehearsal v4 与 route-final 通过 |
| PROJECT-PLATFORM-S03 | 工作项类型定义底座 | S01-S02 | Completed | 类型定义、不可变首发版本、分层 API/UI、六类预置、既有空间补齐和 route-final 证据 |
| PROJECT-PLATFORM-S04 | 动态字段、选项和校验规则 | S03 | Completed | 字段类型注册、定义、选项、默认值、结构化规则、复杂类型、配置 UI、六身份验收与 S05/S06 准入已交付 |
| PROJECT-PLATFORM-S05 | 表单、详情页布局和字段权限 | S04 | Planned | 新建页/详情页布局、分组、条件显示和字段授权 |
| PROJECT-PLATFORM-S06 | 配置草稿、发布、版本和模板复用 | S03-S05 | Planned | 配置发布事务、版本升级、回滚和复用/解绑 |
| PROJECT-PLATFORM-S07 | 统一工作项运行时与第一阶段迁移 | S03-S06 | Planned | 规范实例 API、旧项目读取适配和迁移校验 |
| PROJECT-PLATFORM-S08 | 轻量状态流定义与运行时 | S06-S07 | Planned | 状态、动作、守卫、回退、终止和历史 |
| PROJECT-PLATFORM-S09 | 节点流定义与运行时 | S06-S08 | Planned | 串并行、分支汇聚、会签、交付物和节点任务 |
| PROJECT-PLATFORM-S10 | 工作项关系、层级和依赖 | S07-S09 | Planned | 普通/父子/依赖关系、层级树和关系一致性 |
| PROJECT-PLATFORM-S11 | 空间角色、工作项角色和数据权限 | S02-S10 | Planned | 分层权限、字段/节点授权、权限解释和审计 |
| PROJECT-PLATFORM-S12 | 个人工作台、搜索、收藏和动态 | S07-S11 | Planned | 我的工作、关注、最近、草稿和跨空间搜索 |
| PROJECT-PLATFORM-S13 | 查询模型与表格、列表、树形视图 | S04-S12 | Planned | 保存视图、筛选、排序、分组、列配置和共享 |
| PROJECT-PLATFORM-S14 | 看板、日历、甘特和时间线 | S08-S13 | Planned | 泳道、拖拽流转、时间排期、层级甘特和基线 |
| PROJECT-PLATFORM-S15 | 计划、里程碑、风险、交付物和评审 | S09-S14 | Planned | 项目计划闭环、风险台账、里程碑变更和验收 |
| PROJECT-PLATFORM-S16 | 估分、工时、产能和人员排期 | S11-S15 | Planned | 工作日历、负荷、估算/实际偏差和跨事项排期 |
| PROJECT-PLATFORM-S17 | 自动化规则、通知和开放连接器 | S08-S16 | Planned | 触发器-条件-操作、幂等执行、Webhook 和重试 |
| PROJECT-PLATFORM-S18 | 跨空间授权、关系和数据同步 | S10-S17 | Planned | 跨空间可见性、单/双向同步、冲突和审计 |
| PROJECT-PLATFORM-S19 | 度量、效能、治理和管理驾驶舱 | S13-S18 | Planned | 指标语义、跨空间图表、风险预警和治理视图 |
| PROJECT-PLATFORM-S20 | 研发、市场、HR 和交付场景模板 | S02-S19 | Planned | 四类模板可安装、可调整、可升级并完成场景验收 |
| PROJECT-PLATFORM-S21 | 旧模型退出、全量验证和真实团队试用 | S01-S20 | Planned | 固定模型删除、全量迁移、route-final 和真人 Go/No-Go |

## 6. Stage 与 Milestone 规划

### PROJECT-PLATFORM-S01 当前事实审计、目标领域模型和迁移决策

目标：在新增平台能力前冻结当前事实，确定唯一目标模型、兼容退出顺序和首批可验证竖切。

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S01-M1 | 当前代码、API、表、权限、事件、页面和测试审计 | 当前能力与缺口均可定位到代码和 schema |
| PROJECT-PLATFORM-S01-M2 | 产品术语、聚合边界和目标领域合同冻结 | 空间、类型、实例、流程、关系、角色和视图语义无冲突 |
| PROJECT-PLATFORM-S01-M3 | 迁移、兼容退出、风险和技术 spike | 迁移可分批、可校验、可回退，不保留永久双写 |
| PROJECT-PLATFORM-S01-M4 | Stage 评审、route-final 和 S02 准入 | 专项总纲更新，S02 输入和 Go/No-Go 明确 |

### PROJECT-PLATFORM-S02 项目空间、成员和空间治理

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S02-M1 | 空间模型、生命周期与可见性 | 创建、编辑、停用、恢复、归档语义完整 |
| PROJECT-PLATFORM-S02-M2 | 空间成员、管理员与邀请/移除 | 成员变化可审计，最后管理员受保护 |
| PROJECT-PLATFORM-S02-M3 | 用户侧空间入口与空间设置边界 | 成员执行 UI 和空间配置 UI 分离 |
| PROJECT-PLATFORM-S02-M4 | legacy project -> space/member 映射与兼容入口 | 存量成员可解释且可回退，不提前切换 project/issue 业务写 |
| PROJECT-PLATFORM-S02-M5 | Stage 评审、route-final 和 S03 准入 | 空间闭环与迁移证据通过，专项总纲和下一 Stage 输入同步 |

S02 固定输入见目标架构 17：落 `project_spaces`、成员、角色分配、邀请、legacy space map 和空间迁移批次；API 分用户协作、空间设置和企业治理三类；enterprise 权限不自动授予内容访问。S02 只迁移空间与成员语义，完整 project/issue -> WorkItem 迁移、规范写切流和旧写关闭归 S07。S02 激活前必须先归档已完成 S01 路线并生成新的唯一当前路线。

### PROJECT-PLATFORM-S03 工作项类型定义底座

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S03-M1 | 工作项类型 schema、标识和生命周期 | 系统类型与自定义类型共享同一合同 |
| PROJECT-PLATFORM-S03-M2 | 类型配置 API、授权、审计与治理投影 | 配置动作、用户摘要和企业治理计数使用分层合同 |
| PROJECT-PLATFORM-S03-M3 | 空间配置 UI 与用户执行侧类型入口 | 配置和执行视角分离，关键正反路径经真实浏览器验证 |
| PROJECT-PLATFORM-S03-M4 | 研发预置类型、既有空间补齐与兼容边界 | 六类预置成为配置，补齐幂等且不改变 legacy 业务写 |
| PROJECT-PLATFORM-S03-M5 | Stage 评审、route-final 与 S04 准入 | 类型定义闭环通过，动态字段输入和 Go/No-Go 明确 |

### PROJECT-PLATFORM-S04 动态字段、选项和校验规则

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S04-M1 | 字段定义、类型注册和存储决策 | 核心字段类型及索引策略确定 |
| PROJECT-PLATFORM-S04-M2 | 选项、默认值、必填和有效性条件 | 规则可版本化且服务端强校验 |
| PROJECT-PLATFORM-S04-M3 | 人员、日期、附件、URL 和工作项引用字段 | 权限、生命周期和序列化一致 |
| PROJECT-PLATFORM-S04-M4 | 字段配置 UI、能力查询和性能验证 | 字段定义目录可筛选、排序且达到配置规模预算，不伪造实例查询 |
| PROJECT-PLATFORM-S04-M5 | Stage 评审、route-final 与 S05/S06 准入 | 字段配置闭环通过，布局引用和新版本发布输入明确 |

### PROJECT-PLATFORM-S05 表单、详情页布局和字段权限

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S05-M1 | 新建表单与详情布局 schema | 两类布局独立配置、共享字段定义 |
| PROJECT-PLATFORM-S05-M2 | 分组、标签页、控件与条件显示 | 页面可按业务类型组合且稳定渲染 |
| PROJECT-PLATFORM-S05-M3 | 字段只读、编辑授权和角色条件 | 前后端权限一致，伪造写入被拒绝 |
| PROJECT-PLATFORM-S05-M4 | 配置预览、可访问性和响应式收口 | 管理员可预览，用户页面可用 |

### PROJECT-PLATFORM-S06 配置草稿、发布、版本和模板复用

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S06-M1 | 配置草稿和发布校验 | 未发布配置不影响运行实例 |
| PROJECT-PLATFORM-S06-M2 | 不可变版本、差异和回滚 | 发布和回滚可审计、可恢复 |
| PROJECT-PLATFORM-S06-M3 | 工作项配置模板复用、同步和解绑 | 来源、升级和本地覆盖边界明确 |
| PROJECT-PLATFORM-S06-M4 | 实例模板升级与兼容矩阵 | 运行实例不会被静默破坏 |

### PROJECT-PLATFORM-S07 统一工作项运行时与第一阶段迁移

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S07-M1 | `project_work_items` 规范持久模型和 API | 所有类型实例共享创建、读取、更新合同 |
| PROJECT-PLATFORM-S07-M2 | 动态字段值、参与者和活动历史 | 写入原子、审计完整、查询可用 |
| PROJECT-PLATFORM-S07-M3 | 旧 projects/issues 读取适配与 ID 映射 | 旧链接和平台对象可定位规范实例 |
| PROJECT-PLATFORM-S07-M4 | 分批迁移、校验和回滚 | 存量数据不丢失，失败可重试 |
| PROJECT-PLATFORM-S07-M5 | 用户侧第一条规范工作项竖切 | 真实创建、编辑、评论、附件闭环通过 |

### PROJECT-PLATFORM-S08 轻量状态流定义与运行时

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S08-M1 | 状态、动作、转换和守卫 schema | 定义可校验、可版本化 |
| PROJECT-PLATFORM-S08-M2 | 状态运行时、权限和幂等 | 并发流转不产生非法状态 |
| PROJECT-PLATFORM-S08-M3 | 回退、重开、终止和恢复 | 历史可追溯，终态语义明确 |
| PROJECT-PLATFORM-S08-M4 | 状态配置器与执行 UI | 管理员配置和成员流转闭环通过 |

### PROJECT-PLATFORM-S09 节点流定义与运行时

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S09-M1 | 节点、连线、阶段、分支和汇聚模型 | 图结构合法且可发布 |
| PROJECT-PLATFORM-S09-M2 | 自动、单人、任一人和多人会签 | 完成规则、负责人和并发语义明确 |
| PROJECT-PLATFORM-S09-M3 | 节点表单、交付物、排期和任务 | 节点可承载真实协作职责 |
| PROJECT-PLATFORM-S09-M4 | 回滚、跳转、终止、补偿和版本升级 | 异常流转可解释、可审计 |
| PROJECT-PLATFORM-S09-M5 | 可视化设计器与项目执行视图 | 配置与运行图均通过真实浏览器验收 |

### PROJECT-PLATFORM-S10 工作项关系、层级和依赖

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S10-M1 | 普通、父子、依赖和阻塞关系定义 | 方向、基数和删除策略明确 |
| PROJECT-PLATFORM-S10-M2 | 关系实例、循环检测和一致性 | 并发关系写入不会产生非法图 |
| PROJECT-PLATFORM-S10-M3 | 自定义层级树和子工作项拆解 | 多类型层级可查询、可导航 |
| PROJECT-PLATFORM-S10-M4 | 关系控件、反向引用和影响分析 | 用户可建立、理解和撤销关系 |

### PROJECT-PLATFORM-S11 空间角色、工作项角色和数据权限

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S11-M1 | 企业 RBAC、空间角色和实例角色边界 | 权限来源无混用 |
| PROJECT-PLATFORM-S11-M2 | 创建、查看、编辑、流转和删除权限 | API 与 UI 动作一致 |
| PROJECT-PLATFORM-S11-M3 | 字段、节点、关系和数据范围权限 | 细粒度授权可解释、可审计 |
| PROJECT-PLATFORM-S11-M4 | 权限检查、申请和治理矩阵 | 无权状态不泄露数据，管理员可排查 |

### PROJECT-PLATFORM-S12 个人工作台、搜索、收藏和动态

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S12-M1 | 我的待办、负责、参与和关注 | 跨空间任务聚合准确 |
| PROJECT-PLATFORM-S12-M2 | 最近、收藏、草稿和个性化卡片 | 用户可配置聚焦视图 |
| PROJECT-PLATFORM-S12-M3 | 跨空间、跨类型全局搜索 | 权限过滤、筛选和深链完整 |
| PROJECT-PLATFORM-S12-M4 | 动态、提醒、催办和个人通知 | 去重、已读和来源解释一致 |

### PROJECT-PLATFORM-S13 查询模型与表格、列表、树形视图

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S13-M1 | 统一筛选、排序、分组和分页查询 DSL | 动态字段和关系条件可组合 |
| PROJECT-PLATFORM-S13-M2 | 表格与紧凑列表视图 | 列配置、批量操作和导出可用 |
| PROJECT-PLATFORM-S13-M3 | 树形层级视图 | 父子结构、展开和聚合正确 |
| PROJECT-PLATFORM-S13-M4 | 个人/共享视图、收藏和权限 | 视图可复制、共享、移交和撤销 |

### PROJECT-PLATFORM-S14 看板、日历、甘特和时间线

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S14-M1 | 看板分组、泳道和拖拽动作 | 拖拽受流程与权限约束 |
| PROJECT-PLATFORM-S14-M2 | 日历和日期字段视图 | 跨时区、区间和无日期状态正确 |
| PROJECT-PLATFORM-S14-M3 | 甘特、依赖线和层级展开 | 排期、依赖和关键路径可解释 |
| PROJECT-PLATFORM-S14-M4 | 基线、时间线和视图性能 | 大数据量达到交互预算 |

### PROJECT-PLATFORM-S15 计划、里程碑、风险、交付物和评审

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S15-M1 | 项目计划、阶段和里程碑 | 计划与流程节点边界清楚 |
| PROJECT-PLATFORM-S15-M2 | 风险、问题、决策和变更台账 | 风险责任、响应和关闭闭环 |
| PROJECT-PLATFORM-S15-M3 | 交付物、评审要素和验收结论 | 物料、结论、会签和版本可追踪 |
| PROJECT-PLATFORM-S15-M4 | 项目详情聚合与健康状态 | 负责人可看到进度、偏差和阻塞 |

### PROJECT-PLATFORM-S16 估分、工时、产能和人员排期

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S16-M1 | 工作日历、估分单位和排期规则 | 时区、节假日和部分排期一致 |
| PROJECT-PLATFORM-S16-M2 | 实际工时、登记和修订审计 | 估算与实际可比较 |
| PROJECT-PLATFORM-S16-M3 | 人员负荷、产能和冲突识别 | 跨空间事项聚合且不越权 |
| PROJECT-PLATFORM-S16-M4 | 人员排期甘特与资源调整 | 管理者可发现并处理超载 |

### PROJECT-PLATFORM-S17 自动化规则、通知和开放连接器

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S17-M1 | 事件目录和触发器-条件-操作模型 | 内置事件和字段引用合同稳定 |
| PROJECT-PLATFORM-S17-M2 | 字段更新、流转、创建关联项和通知操作 | 执行幂等、权限受控 |
| PROJECT-PLATFORM-S17-M3 | 定时、临期、到期、超期和停留时间触发 | 调度可恢复，不重复轰炸 |
| PROJECT-PLATFORM-S17-M4 | Webhook、连接器、重试和死信 | 外部失败可诊断、可重放 |
| PROJECT-PLATFORM-S17-M5 | 自动化管理 UI、运行历史和限额 | 管理员可测试、启停和审计规则 |

### PROJECT-PLATFORM-S18 跨空间授权、关系和数据同步

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S18-M1 | 工作项类型跨空间授权 | 最小范围、撤销和历史实例语义明确 |
| PROJECT-PLATFORM-S18-M2 | 跨空间关系建立和可见性 | 双方权限校验且不泄露标题 |
| PROJECT-PLATFORM-S18-M3 | 单向/双向字段与状态同步 | 映射、冲突、循环和补偿策略完整 |
| PROJECT-PLATFORM-S18-M4 | 跨团队全景视图和协作审计 | 横向协作可观察、可治理 |

### PROJECT-PLATFORM-S19 度量、效能、治理和管理驾驶舱

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S19-M1 | 指标语义层、维度和时间窗口 | 指标口径可复现、可版本化 |
| PROJECT-PLATFORM-S19-M2 | 图表、看板和跨空间数据源 | 权限过滤和聚合准确 |
| PROJECT-PLATFORM-S19-M3 | 延期、阻塞、质量和资源风险预警 | 预警可解释、可关闭 |
| PROJECT-PLATFORM-S19-M4 | 空间治理、配置健康和审计报表 | 管理后台只展示治理视角 |

### PROJECT-PLATFORM-S20 研发、市场、HR 和交付场景模板

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S20-M1 | 研发项目模板 | 项目-需求-任务-缺陷-版本-迭代闭环 |
| PROJECT-PLATFORM-S20-M2 | 市场活动模板 | 活动-内容-素材-渠道-投放-复盘闭环 |
| PROJECT-PLATFORM-S20-M3 | HR 招聘模板 | 招聘计划-职位-候选人-面试-入职闭环 |
| PROJECT-PLATFORM-S20-M4 | 客户交付模板 | 项目-任务-风险-交付物-验收闭环 |
| PROJECT-PLATFORM-S20-M5 | 模板安装、差异化和升级验证 | 四类模板可调整而不修改平台代码 |

### PROJECT-PLATFORM-S21 旧模型退出、全量验证和真实团队试用

| Milestone | 交付目标 | 退出条件 |
| --- | --- | --- |
| PROJECT-PLATFORM-S21-M1 | 存量迁移完成度与数据一致性审计 | 未迁移、重复、悬空和权限偏差为零或有决定 |
| PROJECT-PLATFORM-S21-M2 | 删除旧 issues 产品 API、DTO、页面和写路径 | 只保留不可变历史迁移与审计证据 |
| PROJECT-PLATFORM-S21-M3 | 性能、安全、备份、恢复和 route-final | 全量工程门禁通过 |
| PROJECT-PLATFORM-S21-M4 | 研发、市场、HR、交付真人任务试用 | 真实参与者完成规定场景并记录原始反馈 |
| PROJECT-PLATFORM-S21-M5 | 缺陷修复、复验和产品 Go/No-Go | P0/P1 清零，工程与人工结论分离 |

## 7. 全局验收标准

- 统一模型：新业务类型不需要新增一套独立表、Controller 和固定状态枚举才能工作。
- 配置安全：草稿不影响运行；发布、升级、回滚和实例模板版本均可追溯。
- 权限正确：组织、空间、工作项、字段、节点和关系权限分层清晰，无标题或数据泄露。
- 运行可靠：状态流、节点流、关系、自动化和同步均具备幂等、并发、失败补偿和审计。
- 用户可用：普通成员通过工作台、搜索和视图完成工作，不必进入配置后台理解元模型。
- 多团队适配：研发、市场、HR 和交付模板只通过配置差异实现核心流程，不复制业务模块。
- 迁移收口：旧 `issues` 合同按观测、迁移、切流、删除退出，不保留永久双读双写。
- 证据完整：每个 Stage 有逐 Milestone 报告，Stage 最终里程碑执行 `route-final` 并更新本文。
- 真实验证：最终产品判定必须包含真实参与者，不以合成人格替代满意度、学习成本和采用意愿。

## 8. 规划候选池

候选项只有在 Stage 收口评审后才能进入正式 Stage：

| 候选项 | 当前判断 | 处理规则 |
| --- | --- | --- |
| 任意代码插件节点 | 高风险、暂缓 | 先完成声明式节点、Webhook 和连接器；需要沙箱与签名治理后再评估 |
| 原生移动端项目执行 | 非当前主线 | Web 响应式和 API 稳定后另立专项 |
| AI 自动规划与风险预测 | 数据和治理前置不足 | S19 指标可靠并具备真实数据后再评估 |
| 跨租户项目协作 | 超出当前 workspace 边界 | 身份、数据隔离和合规完成专项评审后再进入 |

## 9. 规划变更记录

| Revision | 日期 | 变更 | 原因 | 影响 |
| --- | --- | --- | --- | --- |
| 1 | 2026-07-18 | 建立 PROJECT-PLATFORM 长期专项，拆分 21 个 Stage，并将流程从中心模型调整为工作项配置能力之一 | Lark/飞书项目官方能力调研及当前项目代码审计 | S01-S21 初始基线 |
| 2 | 2026-07-18 | S01 审计、领域合同、物理/迁移 spike 和准入评审完成；冻结 S02 schema/API/权限/legacy 映射，新增 S02-M5 收口；把完整事项迁移和写切流从错误的 S03/S04 归属修正到 S07 | S03/S04 尚无类型、字段和规范工作项运行时，无法承载完整迁移；S02 只能先建立空间与成员底座 | S01 Completed；S02 可在归档 S01 路线后激活；S07 接收完整迁移和切流责任 |
| 3 | 2026-07-18 | 归档已完成 S01 路线，激活 S02，并把空间生命周期、成员邀请、三类 UI、legacy 映射和 Stage 收口细化为 5 个 Milestone、50 个 Task | S01 已给出 Go S02，且归档后满足唯一活动路线约束；S02 需要覆盖后端、前端、迁移和治理的完整闭环 | S02 Active；当前执行入口切换为 PROJECT-PLATFORM-S02；S03 保持 Planned |
| 4 | 2026-07-19 | S02 五个 Milestone 全部收口：M5 完成证据复核、真相文档同步、权限/安全/并发/隔离专项验收、真实形态样本迁移 rehearsal、三角色真实隔离浏览器全链路和 route-final；Go 进入 S03，并在目标架构第 19 节固定 S03 schema/API/权限/迁移准入包 | S02-M5 Stage 评审确认空间闭环与迁移证据完整，无跨 workspace/空间泄露、无最后 owner 缺口；S03 需要冻结输入以避免重新讨论空间归属、角色边界和 legacy 责任 | S02 Completed；current_stage 暂置 none，当前路线保持 completed 等待归档；归档后激活 S03 并生成新当前路线 |
| 5 | 2026-07-19 | 外部审计在 S02-M5 收口后发现 M4 迁移合同缺陷：verify 可空验证假阳性、输入校验和/水位不含用户状态且快照在锁外计算、回滚覆盖原失败清单、批次外键缺 workspace 复合约束、失败注入证据不足。M4-T04 至 T09 改为 Reopened 并重新打开 Stage；S03 准入包保持有效但暂停激活 | 缺陷直接削弱 S02 Go/No-Go 所依赖的迁移校验与审计证据，按治理规范不得以 Gap 弱化，必须 Reopened 并独立补验 | S02 回到 Active、current_stage 恢复 PROJECT-PLATFORM-S02；M4 补验后 M5 评审任务重新执行并再次 route-final 收口 |
| 6 | 2026-07-19 | M4 补验完成：verify 改为跨批次全量核对（dry-run 409、MAP_MISSING/MAP_UNEXPECTED）、输入指纹覆盖用户状态且快照在锁内计算、单元写入前拒绝过期快照、回滚追加保留失败清单、V060 workspace 复合外键、8 个故障注入用例补齐；M5 评审九个任务重新执行，迁移 rehearsal 证明非空校验与失败保留，route-final 再次通过 | 审计发现全部消解，S02 Go/No-Go 所依赖的迁移校验、审计链和故障证据恢复完整 | S02 Completed；current_stage 暂置 none，当前路线保持 completed 等待归档；归档后激活 S03 |
| 7 | 2026-07-19 | 第二轮复审撤销 Go 结论：第一轮补验仍有 P1 阻断——plan 与指纹由多条 SQL 在 READ_COMMITTED 下分别读取，不能保证同一快照（dry-run 更完全在锁外）；batch verify 实为 workspace 全局校验并改写历史批次结论（A 回退、B 重迁后 verify(A) 假成功）；rehearsal 缺真实 failed batch 与 resume 失败恢复演练；技术选型文档停留 V059。M4-T04 至 T10、M5-T01 至 T09 Reopened | 批次验证必须基于不可变批次 manifest，workspace 收敛验证必须独立；plan 与指纹必须来自同一 REPEATABLE_READ 快照；不得在未修复前归档 S02 或激活 S03 | S02 回到 Active、current_stage 恢复 PROJECT-PLATFORM-S02；修复后 M5 重新评审并 route-final 收口 |
| 8 | 2026-07-19 | 第二轮补验完成：plan 与指纹在同一 REPEATABLE_READ 快照读取（dry-run 只读快照同适用）；批次 verify 锚定不可变 manifest，workspace 收敛验证独立为 workspaces:verify-convergence 且不写批次；rehearsal v3 注入真实 UNIT_FAILED 并经同批次 resume 收敛、verify(A) 在 B 重迁后 MAP_SUPERSEDED 必失败；技术选型修正 V060；M5 评审九个任务重新执行，route-final 再次通过 | 第二轮复审阻断全部消解，批次验证语义、快照一致性与失败恢复证据完整 | S02 Completed；current_stage 暂置 none，当前路线保持 completed 等待归档；归档后激活 S03 |
| 9 | 2026-07-22 | 第三轮复审发现 resume 会用最新尝试的 REUSED 覆盖批次历史归属、事务快照测试未调用真实迁移服务、rehearsal v3 创建并保留 legacy 夹具。补验后新增跨尝试 `manifestProjects` 生命周期归属、真实服务 REQUIRES_NEW 竞态测试和不创建 legacy 数据的 rehearsal v4；A 批次 59 个归属项在 B 重迁后全部报告 MAP_SUPERSEDED，legacy 四表执行前后哈希一致 | 批次验证必须保存生命周期归属而非从最近尝试推断；Stage 演练不得污染作为迁移输入的 legacy 权威数据 | S02 保持 Completed；revision 9 记录纠偏，S03 准入不变；当前路线继续等待归档 |
| 10 | 2026-07-22 | S02 完成路线归档并激活 S03；将原四个概括 Milestone 细化为模型持久化、服务/API/授权、配置与用户 UI、预置类型及兼容边界、Stage 收口五个 Milestone，共 50 个可执行 Task | S03 同时涉及不可变版本、空间授权、双视角 UI、既有空间补齐和 legacy 零写入约束，必须把最终 `route-final` 与 S04 准入从实现 Milestone 中独立出来 | S03 Active；当前执行入口切换为 PROJECT-PLATFORM-S03-M1；S02 归档；S04 保持 Planned |
| 11 | 2026-07-22 | S03 五个 Milestone、50 个 Task 全部完成；补齐普通非成员最小披露和启动补齐逐空间隔离证据，完成 V001-V063/升级迁移 rehearsal、真实隔离浏览器与 `route-final`；在目标架构第 20 节冻结 S04 字段定义准入包 | S03 已形成类型定义、不可变 v1、分层授权、预置补齐和 legacy 零切流闭环；S04 必须复用该版本边界，且不得提前实现 S06 发布或 S07 实例 | S03 Completed；`current_stage` 暂置 none，当前路线 completed 等待归档；S04 Go 但保持 Planned，归档后再激活 |
| 12 | 2026-07-22 | 归档 S03 完成路线并激活 S04；把原四个概括 Milestone 扩展为字段定义底座、选项与规则、复杂类型、配置 UI/能力查询/性能和 Stage 收口五个 Milestone，共 53 个 Task | S04 同时涉及 schema、类型系统、结构化规则、复杂引用、安全、六角色 UI 和 S05/S06 承接，必须独立保留最终 route-final 与准入评审，不能压入实现 Milestone | S04 Active；当前执行入口切换为 PROJECT-PLATFORM-S04-M1；S03 路线归档；S05/S06 保持 Planned |
| 13 | 2026-07-24 | S04 五个 Milestone、53 个 Task 完成；复核隔离、永久 key、规则安全、并发、幂等、审计脱敏、V001-V065 空库与 V063 升级迁移、六类身份真实隔离浏览器及字段配置规模，并在目标架构第 21 节冻结 S05 布局和 S06 发布准入 | S04 只拥有字段配置目录，不拥有规范工作项实例；因此把 120 字段/2400 选项/3 秒作为 S04 已验收预算，把 10 万工作项查询预算纠正到 S07/S13 | S04 Completed；`current_stage` 暂置 none，当前路线 completed 等待归档；Go S05，S06 需等待 S05 完成后激活 |

## 10. 主要产品参考

- 飞书项目核心功能：https://www.feishu.cn/content/4ek1avnv
- 飞书项目平台介绍：https://www.feishu.cn/content/1afedyby
- 飞书项目创建和复用工作项：https://www.feishu.cn/content/h4hp7js9
- 飞书项目字段配置：https://www.feishu.cn/content/17mi85qv
- 飞书项目节点流程规则：https://www.feishu.cn/content/3bv61iew
- 飞书项目权限指南：https://www.feishu.cn/content/7rgg2vro
- 飞书项目工作台：https://www.feishu.cn/content/3c6y1qwl
- 飞书项目工作项父子关系：https://www.feishu.cn/content/hu5sma8o
- 飞书项目空间关联规则：https://www.feishu.cn/content/4g4laxgf
- 飞书项目自动化连接器：https://www.feishu.cn/content/ef835n76
