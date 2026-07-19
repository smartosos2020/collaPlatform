---
title: PROJECT-PLATFORM-S02 当前执行路线
status: active
route: PROJECT-PLATFORM-S02
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 3
stage: PROJECT-PLATFORM-S02
stage_final_milestone: PROJECT-PLATFORM-S02-M5
last_code_check: 2026-07-18
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S02 项目空间、成员和空间治理

## 1. Stage 目标

在不提前切换现有 `projects/issues` 业务写路径的前提下，建立项目协作平台的空间与成员底座：项目空间生命周期、可见性、内置空间角色、邀请与成员治理、用户执行入口、空间设置入口、企业治理入口，以及 legacy project 到 space/member 的可重试映射。

S02 完成后，空间应成为配置、成员和数据可见性的稳定边界，但“项目”仍暂由 legacy 模型承载；统一工作项类型、动态字段和规范工作项运行时分别由 S03、S04、S07 建设。S02 不建立永久双写，也不让企业管理员权限自动扩张为任意空间内容访问权。

## 2. 固定输入与边界

- 上一 Stage：`PROJECT-PLATFORM-S01` 已完成并归档到 `docs/99-archive/superseded-roadmaps/project-platform-s01-roadmap-completed-2026-07-18.md`。
- 长期专项：`docs/00-product/initiatives/project-platform-program.md` revision 3。
- 目标架构：`docs/01-architecture/project-platform-target-architecture.md` 的 S02 实施输入包。
- 目标 schema：`project_spaces`、`project_space_members`、`project_space_role_assignments`、`project_space_invitations`、`project_legacy_space_maps`、`project_space_migration_batches`。
- 内置空间角色：`owner`、`admin`、`member`、`guest`；必须保护最后一名 owner。
- 可见性：`private`、`discoverable`、`workspace`；可发现不等于可读取空间内容。
- legacy 映射：project owner/member/viewer 映射为 space owner/member/guest；IM 群成员漂移只报告，不自动扩张成员。
- API 分层：用户协作 API、空间设置 API、企业治理/迁移 API 不得混用 DTO、权限或菜单入口。

## 3. 执行规则

1. 每轮 AI 工作循环只推进一个 Milestone；任务必须按编号逐项完成并记录可复核证据。
2. M1-M4 使用目标测试和 `stage` finish；M5 是 Stage 最终里程碑，必须执行 `route-final`。
3. 后端实现必须由 Flyway、Repository、Service、Controller、权限和审计测试共同闭环；仅建表或仅返回 DTO 不算完成。
4. 用户侧、空间设置和管理后台行为变更必须使用真实隔离浏览器验证；不得用 mock 浏览器替代真实主路径。
5. legacy 映射必须支持 dry-run、批次、校验和、失败重试和回退；不得直接修改或删除 legacy project/issue 数据。
6. enterprise RBAC 只能授予治理动作，不得绕过空间成员关系读取协作内容；每个接口都要有正反权限矩阵。
7. 发现 S01 输入与实现事实冲突时，先更新风险和规划变更，不为了保持任务数量压缩设计问题。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 收口证据 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M1 | 空间模型、生命周期与可见性 | S01 准入包 | `docs/90-reports/project-platform-s02-m1-execution-report.md` | Completed |
| PROJECT-PLATFORM-S02-M2 | 空间成员、角色、邀请与成员治理 | M1 | `docs/90-reports/project-platform-s02-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S02-M3 | 用户侧空间入口、空间设置与企业治理 UI | M1-M2 | `docs/90-reports/project-platform-s02-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S02-M4 | legacy project 到 space/member 映射与兼容入口 | M1-M3 | `docs/90-reports/project-platform-s02-m4-execution-report.md` | Planned |
| PROJECT-PLATFORM-S02-M5 | Stage 评审、route-final 与 S03 准入 | M1-M4 | `docs/90-reports/project-platform-s02-m5-execution-report.md` | Planned |

## 5. 详细任务

### PROJECT-PLATFORM-S02-M1 空间模型、生命周期与可见性

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M1-T01 | 复核 S01 空间输入与当前 workspace、project、ACL、审计代码边界 | 形成实施定位表；目标能力与当前事实无混写，冲突项有明确决定 | Done |
| PROJECT-PLATFORM-S02-M1-T02 | 新增空间、角色分配、邀请、legacy 映射和迁移批次 Flyway schema | V001 至最新可从空库迁移；外键、唯一约束、状态约束和必要索引完整 | Done |
| PROJECT-PLATFORM-S02-M1-T03 | 实现 `ProjectSpace` 聚合、状态与可见性值对象及生命周期守卫 | 创建、编辑、停用、恢复、归档的合法转换由领域层强制，非法转换有稳定错误 | Done |
| PROJECT-PLATFORM-S02-M1-T04 | 实现空间 Repository、查询投影与 workspace 隔离 | 列表、详情、状态筛选和分页不跨 workspace，归档数据默认不进入活动查询 | Done |
| PROJECT-PLATFORM-S02-M1-T05 | 实现用户协作侧空间列表、详情和创建 API | 普通成员只看到可发现或已加入空间；创建策略、DTO 和错误合同稳定 | Done |
| PROJECT-PLATFORM-S02-M1-T06 | 实现空间设置侧基本信息、可见性和生命周期 API | owner/admin 可编辑；member/guest 和仅企业管理员不能越权修改 | Done |
| PROJECT-PLATFORM-S02-M1-T07 | 实现企业治理侧空间目录、状态治理和权限解释 API | 治理视图不泄露内容；停用/恢复等治理动作有显式企业权限和来源解释 | Done |
| PROJECT-PLATFORM-S02-M1-T08 | 接入审计、平台对象和统一错误/幂等合同 | 空间创建、变更、停用、恢复、归档均有 actor、对象、前后状态和 request id | Done |
| PROJECT-PLATFORM-S02-M1-T09 | 建立空间生命周期、可见性与权限集成测试矩阵 | 覆盖 owner/admin/member/guest/非成员/企业管理员及跨 workspace 负例 | Done |
| PROJECT-PLATFORM-S02-M1-T10 | 完成 M1 文档同步和目标质量门 | schema、API、权限、审计证据写入报告；目标测试和迁移验证通过 | Done |

### PROJECT-PLATFORM-S02-M2 空间成员、角色、邀请与成员治理

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M2-T01 | 实现空间成员与角色分配聚合、Repository 和有效成员投影 | 成员身份与角色分配分离；重复成员、重复角色和失效成员受约束 | Done |
| PROJECT-PLATFORM-S02-M2-T02 | 实现 owner/admin/member/guest 内置角色能力矩阵 | 每个角色的查看、配置、成员治理能力可解释；不复用企业 RBAC 代替空间角色 | Done |
| PROJECT-PLATFORM-S02-M2-T03 | 实现成员列表、直接加入、角色变更和移除 API | owner/admin 权限边界明确；目标用户状态、workspace 归属和重复操作被校验 | Done |
| PROJECT-PLATFORM-S02-M2-T04 | 实现邀请创建、重发、撤销、接受、拒绝和过期合同 | token 不明文持久化；重复接受幂等；撤销/过期邀请不可加入空间 | Done |
| PROJECT-PLATFORM-S02-M2-T05 | 实现最后 owner 保护、owner 转移、自行离开和停用成员规则 | 任何并发路径都不能产生无 owner 活动空间；关键冲突返回可操作错误 | Done |
| PROJECT-PLATFORM-S02-M2-T06 | 实现成员搜索与组织目录选择边界 | 只暴露允许被选择的 workspace 成员；组织查看权限不自动授予空间访问 | Done |
| PROJECT-PLATFORM-S02-M2-T07 | 接入成员/邀请审计、通知与去重 | 邀请、接受、角色变更、移除、owner 转移均可追踪且通知不重复轰炸 | Done |
| PROJECT-PLATFORM-S02-M2-T08 | 处理并发、重复请求、失效用户和空间停用边界 | 乐观/悲观并发策略明确；重复请求幂等，停用空间禁止新增成员和邀请 | Done |
| PROJECT-PLATFORM-S02-M2-T09 | 建立成员治理 API 正反权限矩阵集成测试 | 覆盖角色组合、跨空间、跨 workspace、最后 owner、过期邀请和并发冲突 | Done |
| PROJECT-PLATFORM-S02-M2-T10 | 建立邀请安全、审计完整性和敏感信息测试 | 日志/响应不泄露 token；审计前后值完整；恶意枚举无法确认不可见空间 | Done |
| PROJECT-PLATFORM-S02-M2-T11 | 完成 M2 文档同步和目标质量门 | 成员、角色、邀请和通知闭环有报告证据，目标后端测试通过 | Done |

### PROJECT-PLATFORM-S02-M3 用户侧空间入口、空间设置与企业治理 UI

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M3-T01 | 建立用户侧项目空间路由、列表和最近空间入口 | 普通用户从项目模块直接进入空间；空态、无权限、停用和归档状态可理解 | Done |
| PROJECT-PLATFORM-S02-M3-T02 | 实现空间创建、基本信息和可见性设置交互 | 字段校验、保存反馈、冲突提示和权限禁用态与 API 一致 | Done |
| PROJECT-PLATFORM-S02-M3-T03 | 实现空间 Shell、成员执行视角和设置入口边界 | 日常协作区域不暴露企业治理；设置入口只对 owner/admin 可见 | Done |
| PROJECT-PLATFORM-S02-M3-T04 | 实现空间成员列表、邀请、角色调整、移除和 owner 转移 UI | 危险动作二次确认；最后 owner、停用用户和邀请状态有明确反馈 | Done |
| PROJECT-PLATFORM-S02-M3-T05 | 实现空间停用、恢复、归档及只读状态体验 | 停用/归档后写入口关闭，恢复路径明确；状态 badge 和按钮语义统一 | Done |
| PROJECT-PLATFORM-S02-M3-T06 | 实现管理后台空间治理列表与详情 | 后台只呈现企业治理、风险、状态和审计入口，不承载空间内容协作 | Done |
| PROJECT-PLATFORM-S02-M3-T07 | 统一用户 UI、空间设置 UI、管理后台的导航和深链 | 三类入口菜单无重叠职责；刷新、返回和直接 URL 均保持上下文 | Done |
| PROJECT-PLATFORM-S02-M3-T08 | 完成桌面 1366/1440 与窄屏响应式、加载和错误状态 | 页面不整体溢出；列表、弹窗、抽屉和表格在支持宽度内可操作 | Done |
| PROJECT-PLATFORM-S02-M3-T09 | 建立真实隔离浏览器 smoke | owner 建空间/邀请/改角色/移除，member 访问，企业管理员治理且不能读内容 | Done |
| PROJECT-PLATFORM-S02-M3-T10 | 完成 M3 前端质量、可访问性和报告收口 | lint/build、关键键盘路径、浏览器证据和 API 负例全部通过 | Done |

### PROJECT-PLATFORM-S02-M4 legacy project 到 space/member 映射与兼容入口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M4-T01 | 建立 legacy project、project_members、owner 和 IM 群数据画像/预检 | 数量、孤立成员、非法角色、重复关系、无 owner 和 IM 漂移均可报告 | Planned |
| PROJECT-PLATFORM-S02-M4-T02 | 实现 project 到 space 的确定性 ID/映射与冲突规则 | 重跑得到相同映射；名称/code 冲突、已存在空间和跨 workspace 情况有决定 | Planned |
| PROJECT-PLATFORM-S02-M4-T03 | 实现 owner/member/viewer 到 owner/member/guest 的成员映射 | 角色来源可解释；未知角色进入失败清单，不静默提升权限 | Planned |
| PROJECT-PLATFORM-S02-M4-T04 | 实现迁移批次、dry-run、apply、续跑和幂等状态机 | 批次记录输入水位、结果、失败项和校验和；中断后可安全续跑 | Planned |
| PROJECT-PLATFORM-S02-M4-T05 | 实现迁移校验、差异报告和回退 | 空间/成员计数、角色、归属和映射逐项核对；回退只撤销新模型产物 | Planned |
| PROJECT-PLATFORM-S02-M4-T06 | 实现企业治理迁移 API/操作入口和最小权限 | 仅授权管理员可 dry-run/apply/rollback；高风险操作需要确认和审计 | Planned |
| PROJECT-PLATFORM-S02-M4-T07 | 实现 legacy project 深链到空间入口的兼容解析 | 旧链接可定位新空间；未迁移/失败/无权限状态不错误跳转或泄露名称 | Planned |
| PROJECT-PLATFORM-S02-M4-T08 | 冻结 S02 兼容边界并禁止 project/issue 写切流 | 现有业务写仍走 legacy；无双写；S07 接收完整 WorkItem 迁移责任 | Planned |
| PROJECT-PLATFORM-S02-M4-T09 | 建立迁移集成、失败注入、回退和真实浏览器验证 | 重跑、部分失败、并发批次、回退、旧深链及权限负例均自动化覆盖 | Planned |
| PROJECT-PLATFORM-S02-M4-T10 | 完成 M4 迁移运行手册、证据和目标质量门 | dry-run 样例、校验 SQL/报告、回退步骤和剩余数据风险可复核 | Planned |

### PROJECT-PLATFORM-S02-M5 Stage 评审、route-final 与 S03 准入

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M5-T01 | 复核 M1-M4 任务、实现、迁移和浏览器证据 | 每个任务有实现、自动化、浏览器适用性和未决项；无证据任务不得关闭 | Planned |
| PROJECT-PLATFORM-S02-M5-T02 | 更新当前产品范围、当前架构、对象模型和技术选型事实 | 只登记已实现能力；目标能力、兼容层和 legacy 写路径状态清楚分离 | Planned |
| PROJECT-PLATFORM-S02-M5-T03 | 执行空间/成员权限、安全、并发和数据隔离专项验收 | 无跨 workspace/空间泄露、无最后 owner 缺口、邀请 token 和审计满足安全合同 | Planned |
| PROJECT-PLATFORM-S02-M5-T04 | 执行迁移 rehearsal、校验、失败恢复和回退演练 | 可从真实形态样本 dry-run 到 apply，再回退并重复执行，结果一致 | Planned |
| PROJECT-PLATFORM-S02-M5-T05 | 执行用户、空间管理员、企业管理员真实隔离浏览器全链路 | 三类角色完成规定流程，权限边界、深链和生命周期状态均符合预期 | Planned |
| PROJECT-PLATFORM-S02-M5-T06 | 执行路线级完整测试、V001 至最新空库迁移、安全和构建门禁 | `route-final` 全部通过，任何失败或跳过有明确阻断/豁免决定 | Planned |
| PROJECT-PLATFORM-S02-M5-T07 | 输出 S02 Go/No-Go、兼容债务和剩余风险 | 明确进入 S03、补充 S02 或暂停三选一；风险有 owner Stage 和退出条件 | Planned |
| PROJECT-PLATFORM-S02-M5-T08 | 固定 S03 类型定义的 schema、API、权限和迁移输入 | S03 可直接拆 Task，不重新讨论空间归属、角色边界和 legacy 责任 | Planned |
| PROJECT-PLATFORM-S02-M5-T09 | 更新专项状态、修订号、目标架构和下一 Stage | S02 完成态、规划变更和 S03 准入同步；当前路线保持 completed 等待归档 | Planned |

## 6. Stage 全局验收标准

- 空间是成员、配置和可见性的稳定边界，生命周期转换由后端强制并完整审计。
- owner/admin/member/guest 与企业 RBAC 分层明确；企业管理员不自动获得空间内容访问。
- 邀请、成员变更、owner 转移和移除具备幂等、并发和最后 owner 保护。
- 用户执行入口、空间设置入口和企业管理后台职责分离，菜单、API 和 DTO 不混用。
- legacy project/member 映射可 dry-run、校验、续跑和回退，不切换 project/issue 业务写，不建立永久双写。
- M1-M4 使用影响范围验证；M5 使用真实隔离浏览器和 `route-final` 完成 Stage 收口。

## 7. 当前执行入口

当前下一执行入口是 `PROJECT-PLATFORM-S02-M4-T01`。每轮只执行一个 Milestone；M4 未完成前不得进入 M5，Stage 最终完成前不得激活 S03。
