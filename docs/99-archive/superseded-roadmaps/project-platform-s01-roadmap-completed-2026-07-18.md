---
title: PROJECT-PLATFORM-S01 当前事实审计、目标领域模型和迁移决策路线（已完成归档）
status: completed
route: PROJECT-PLATFORM-S01
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 2
stage: PROJECT-PLATFORM-S01
stage_final_milestone: PROJECT-PLATFORM-S01-M4
last_code_check: 2026-07-18
archived_at: 2026-07-18
source_rule: 本文件是已完成路线的历史归档，不再作为执行入口。
---

# PROJECT-PLATFORM-S01 当前事实审计、目标领域模型和迁移决策

## 1. Stage 目标

在新增项目平台能力前，完整审计当前 `projects/issues` 产品合同、代码、数据库、权限、对象集成和用户路径，冻结统一工作项目标模型、配置与运行边界、迁移退出顺序，并为 S02 项目空间建设提供可直接实施的输入。

本 Stage 不批量改造业务模型、不新增复杂流程设计器、不提前迁移生产数据。任何物理表名和 API 只有在审计、ADR、迁移 spike 和退出评审完成后才能进入实现 Stage。

## 2. 上游与下游

- 长期专项：`docs/00-product/initiatives/project-platform-program.md`
- 目标架构：`docs/01-architecture/project-platform-target-architecture.md`
- 当前事实：`docs/00-product/current-product-scope.md`、`docs/01-architecture/current-architecture.md` 和代码/schema
- 上一条路线：KB-PRODUCT 已按暂停状态归档，真人试用任务没有被标记完成。
- 下一 Stage：`PROJECT-PLATFORM-S02`，只有 S01-M4 给出 Go 且专项总纲同步后才能激活。

## 3. 执行规则

1. 每轮 AI 工作循环只推进一个 Milestone，不跨 M1-M4 合并收口。
2. 每个任务必须提供代码/schema 定位、结论、自动化或可复核静态证据；只写概念说明不算完成。
3. 涉及当前页面行为核对时使用真实浏览器和真实 API；纯审计/ADR 任务可明确 browser not required。
4. M1-M3 使用 `stage` finish 和目标测试；M4 是 Stage 最终里程碑，必须使用 `route-final`。
5. M4 收口必须更新长期专项修订号、S01 状态、变更记录和 S02 准入；未同步专项总纲不能完成。
6. 发现当前假设错误时先登记规划变更，不为了保持原任务数量压缩问题。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 收口证据 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M1 | 当前代码、API、表、权限、事件、页面和测试审计 | 当前代码 | `docs/90-reports/project-platform-s01-m1-execution-report.md` | Done |
| PROJECT-PLATFORM-S01-M2 | 产品术语、聚合边界和目标领域合同冻结 | M1 | `docs/90-reports/project-platform-s01-m2-execution-report.md` | Done |
| PROJECT-PLATFORM-S01-M3 | 迁移、兼容退出、风险和技术 spike | M1-M2 | `docs/90-reports/project-platform-s01-m3-execution-report.md` | Done |
| PROJECT-PLATFORM-S01-M4 | Stage 评审、route-final 和 S02 准入 | M1-M3 | `docs/90-reports/project-platform-s01-m4-execution-report.md` | Done |

## 5. 详细任务

### PROJECT-PLATFORM-S01-M1 当前实现与数据事实审计

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M1-T01 | 盘点项目后端包、Controller、Service、Domain、Repository 和跨模块调用 | 形成完整调用与依赖图，每个公开入口可定位到实现和测试 | Done |
| PROJECT-PLATFORM-S01-M1-T02 | 盘点项目 API、DTO、状态动作和 `availableActions` | 区分真实权限计算、硬编码投影、未使用写 API 和兼容合同 | Done |
| PROJECT-PLATFORM-S01-M1-T03 | 盘点 V001 至当前 Flyway 中 projects、members、iterations、issues、comments、attachments、relations、verification 数据 | 每张表、字段、索引、外键和真实读写方有登记 | Done |
| PROJECT-PLATFORM-S01-M1-T04 | 盘点前端路由、页面、抽屉、列表、看板、筛选、统计和 API 调用 | 用户主路径、不可达能力、重复状态和缺失交互有证据 | Done |
| PROJECT-PLATFORM-S01-M1-T05 | 盘点项目成员、组织 RBAC、资源权限和后台治理边界 | 明确当前 owner/member/viewer、管理员和 ACL 的实际语义与冲突 | Done |
| PROJECT-PLATFORM-S01-M1-T06 | 盘点平台对象、搜索、通知、审计、IM 群、文件和知识库引用 | 每条跨模块链路有对象类型、ID、权限和生命周期结论 | Done |
| PROJECT-PLATFORM-S01-M1-T07 | 盘点项目单元、集成和浏览器测试及真实覆盖缺口 | 测试矩阵区分存在、有效、陈旧、缺失和无法重复运行 | Done |
| PROJECT-PLATFORM-S01-M1-T08 | 建立存量数据画像和迁移风险查询 | 输出类型、状态、空值、孤立关系、成员角色和 iteration 使用统计方案 | Done |
| PROJECT-PLATFORM-S01-M1-T09 | 输出当前能力矩阵、缺口和 S01 风险登记 | 结论与代码/schema 一致，不把目标能力写成已实现 | Done |

### PROJECT-PLATFORM-S01-M2 产品术语与目标领域合同冻结

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M2-T01 | 冻结项目空间、工作项类型、工作项实例、配置版本和模板术语 | 中英文术语、ID、生命周期和归属关系无歧义 | Done |
| PROJECT-PLATFORM-S01-M2-T02 | 冻结“项目是工作项类型之一”的产品边界 | 研发、市场、HR、交付示例可由同一模型表达 | Done |
| PROJECT-PLATFORM-S01-M2-T03 | 冻结配置定义与运行实例分离合同 | 草稿、发布、版本、升级、回滚和历史实例行为明确 | Done |
| PROJECT-PLATFORM-S01-M2-T04 | 冻结动态字段、表单和详情页布局合同 | 字段类型、校验、默认值、条件、布局引用和权限边界明确 | Done |
| PROJECT-PLATFORM-S01-M2-T05 | 冻结状态流与节点流的共同原语和差异 | 轻量事项与复杂项目均可表达，不以单一流程形态强行统一 | Done |
| PROJECT-PLATFORM-S01-M2-T06 | 冻结普通、父子、依赖和跨空间关系合同 | 方向、基数、循环、删除、权限和同步边界明确 | Done |
| PROJECT-PLATFORM-S01-M2-T07 | 冻结企业 RBAC、空间角色、实例角色、节点和字段授权层级 | 权限计算、解释、缓存和审计责任清楚 | Done |
| PROJECT-PLATFORM-S01-M2-T08 | 冻结用户执行 UI、空间配置 UI 和企业管理后台边界 | 菜单、API 语义和 DTO 不再混用治理与协作视角 | Done |
| PROJECT-PLATFORM-S01-M2-T09 | 冻结统一工作项事件和平台对象合同 | 搜索、通知、审计、文件、IM 和知识库引用有稳定接入点 | Done |
| PROJECT-PLATFORM-S01-M2-T10 | 形成目标领域 ADR 和反例清单 | 记录选择、替代方案、拒绝原因和禁止重新引入的固定模型 | Done |

### PROJECT-PLATFORM-S01-M3 迁移、兼容退出与技术 spike

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M3-T01 | 比较动态字段 JSONB、类型化行和混合投影存储方案 | 以查询、索引、迁移、校验、扩展和运维证据形成决策 | Done |
| PROJECT-PLATFORM-S01-M3-T02 | 设计规范 ID、编号和旧 project/issue ID 映射 | 旧深链、平台对象、搜索和审计可稳定重定向 | Done |
| PROJECT-PLATFORM-S01-M3-T03 | 设计 projects/issues 到 space/type/work-item 的分批迁移 | 每批有 dry-run、校验和、失败清单、重试和回退 | Done |
| PROJECT-PLATFORM-S01-M3-T04 | 设计读取适配、新写切流和旧写关闭顺序 | 每个兼容面有 owner、监控、退出条件和最晚删除 Stage | Done |
| PROJECT-PLATFORM-S01-M3-T05 | 验证动态字段查询和索引最小 spike | 常用筛选、排序、分组方案有可运行证据和性能预算 | Done |
| PROJECT-PLATFORM-S01-M3-T06 | 验证配置版本绑定与实例升级最小 spike | 旧实例保持旧版本，显式升级可预览差异和拒绝不兼容变更 | Done |
| PROJECT-PLATFORM-S01-M3-T07 | 验证状态流/节点流共同运行时边界 spike | 共享事件、权限和历史，不把两者强行实现为同一图结构 | Done |
| PROJECT-PLATFORM-S01-M3-T08 | 建立迁移安全、性能、数据和发布风险清单 | P0/P1 风险有预防、探测、回退和责任 Stage | Done |
| PROJECT-PLATFORM-S01-M3-T09 | 输出迁移 ADR、实施分层和 S02/S03 输入 | 下一阶段不需要重新讨论核心物理与兼容策略 | Done |

### PROJECT-PLATFORM-S01-M4 Stage 评审与 S02 准入

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S01-M4-T01 | 复核 M1-M3 证据和未决问题 | 所有结论可追溯，阻断项未被降级为建议 | Done |
| PROJECT-PLATFORM-S01-M4-T02 | 更新当前产品范围与当前架构事实 | 只登记已确认事实和决策，不提前宣称目标能力完成 | Done |
| PROJECT-PLATFORM-S01-M4-T03 | 更新目标架构与专项 Stage 编排 | 新认识进入规划变更记录，后续依赖与顺序同步调整 | Done |
| PROJECT-PLATFORM-S01-M4-T04 | 固定 S02 项目空间的 API、schema、权限和迁移输入 | S02 可直接拆 Task，不遗留核心“后续再定”字段 | Done |
| PROJECT-PLATFORM-S01-M4-T05 | 建立项目平台目标测试、浏览器 smoke 和迁移验证分层 | 中间 Stage 不跑全量，关键闭环和最终 route-final 有入口 | Done |
| PROJECT-PLATFORM-S01-M4-T06 | 执行路线级完整测试、迁移、安全和构建门禁 | `route-final` 全部通过，失败和跳过均有明确决定 | Done |
| PROJECT-PLATFORM-S01-M4-T07 | 输出 S01 Go/No-Go 和剩余风险 | 明确进入 S02、补充 S01 或暂停三选一及依据 | Done |
| PROJECT-PLATFORM-S01-M4-T08 | 更新长期专项状态、修订号和下一 Stage | S01 标记 Completed，变更记录完整；若 Go，S02 可激活 | Done |

## 6. Stage 全局验收标准

- 当前事实审计覆盖代码、schema、API、UI、权限、事件、跨模块和测试，不以文件存在代替行为验证。
- 目标模型能够表达研发、市场、HR 和交付，不新增团队专属顶层运行模型。
- 状态流、节点流、字段、页面、角色、关系、视图和自动化是工作项定义的组合能力。
- 迁移策略明确旧合同的观测、迁移、切流、关闭和删除，不保留永久双写。
- 目标架构与当前架构严格区分，规划文档不会把未来能力写成当前事实。
- S01-M4 使用 `route-final`，并在 finish 前同步长期专项；否则 Stage 不能完成。

## 7. 当前执行入口

PROJECT-PLATFORM-S01-M1 至 M4 已完成，S01 结论为 **Go S02**。本路线保持 completed 并等待归档；归档和生成新路线后，才能把 PROJECT-PLATFORM-S02 从 Planned 改为 Active。当前不得继续在本文件追加 S02 Task，也不得在已完成路线仍占用 `current-roadmap.md` 时执行下一工作循环。
