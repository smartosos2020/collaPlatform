---
title: PROJECT-PLATFORM-S06 配置草稿、发布、版本和模板复用当前执行路线
status: completed
route: PROJECT-PLATFORM-S06
program: PROJECT-PLATFORM
program_doc: docs/00-product/initiatives/project-platform-program.md
program_revision: 20
stage: PROJECT-PLATFORM-S06
stage_final_milestone: PROJECT-PLATFORM-S06-M4
last_code_check: 2026-07-26
source_rule: 本文件是唯一执行路线入口；长期专项只提供 Stage 索引，不直接执行。
---

# PROJECT-PLATFORM-S06 配置草稿、发布、版本和模板复用

## 1. Stage 目标

在 S03-S05 的类型、字段、选项、规则、布局和字段访问策略基础上，建立唯一可变 `ConfigurationDraft`、完整自包含的不可变发布快照、原子发布、版本差异、回滚生成新草稿，以及具有来源、升级、本地覆盖和解绑语义的配置模板。

S06 的核心不是增加一个“发布”按钮，而是消除 live 配置表与 draft 并列成为双权威的风险。所有配置编辑必须汇聚到唯一 active draft；发布和后续 S07 只能消费不可变 snapshot。S06 不创建 `project_work_items`、动态字段值、实例命令、实例迁移或运行时流程。

## 2. 固定输入与当前事实

- S05 已归档，当前 schema 为 V085；S03 published v1 是 legacy partial 版本，不得伪装为完整 S06 snapshot。
- 类型展示、字段、选项、规则、布局和访问策略当前仍由多个服务修改规范化 live 表，尚无统一 draft aggregate。
- `project_work_item_type_versions` 仍允许 `draft` 状态；S06 必须迁移遗留值并收紧为 `published/superseded`。
- 当前前端有类型、字段和布局配置入口，但无统一草稿状态、发布校验、版本历史、diff、回滚或模板入口。
- S06 发布必须在单事务中完成 type lock、版本号、snapshot、current pointer、旧版本 supersede、draft 状态、审计、outbox 和幂等回执。
- 模板必须保存不可变完整 snapshot；现有 `WorkItemTypePresetCatalog` 只能作为平台预置导入源，不是模板版本权威。
- S07 只允许通过冻结的 published snapshot adapter 解释未来实例，不得读取 active draft 或 S04/S05 live 表。

## 3. 执行规则

1. 每轮只推进一个 Milestone；每个 Task 必须有唯一 Verification Contract、Acceptance Evidence 和执行报告行。
2. 同一 workspace/space/type 最多一个 active draft；`ConfigurationDraft` 是唯一可变配置权威。
3. snapshot 必须自包含类型、字段、选项、规则、复杂引用、create/detail 布局、条件和访问策略，并使用版本化 schema 与规范 hash。
4. published/superseded version 及其 snapshot 禁止更新和删除；回滚只能基于历史 snapshot 生成更高版本的新草稿/新版本。
5. 发布命令必须具备 request ID、规范 request hash、精确响应重放、异载荷冲突、行锁、故障原子性、审计和 outbox。
6. diff 使用稳定 key path，至少区分 additive、behavioral、conditional、breaking；不能只比较 JSON 文本。
7. 模板安装是复制而非 live 引用；升级采用 base/upstream/local 三方比较，冲突必须显式处理，解绑不得删除本地配置或历史 lineage。
8. 所有 API 使用 workspace/space/type 复合隔离和 availableActions；无权身份不得枚举 draft、snapshot、模板来源或隐藏字段。
9. M1-M3 使用影响范围验证；M4 执行 V001/V061/V065/V078 至最新迁移、完整后端、前端、架构、工作台、安全、真实隔离浏览器和 `route-final`。

## 4. Milestone 总览

| Milestone | 目标 | 依赖 | 执行报告 | 状态 |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M1 | 唯一配置草稿、完整快照和发布校验 | S05 归档；Program revision 19 | `docs/90-reports/project-platform-s06-m1-execution-report.md` | Done |
| PROJECT-PLATFORM-S06-M2 | 不可变版本、原子发布、diff 和回滚 | M1 | `docs/90-reports/project-platform-s06-m2-execution-report.md` | Completed |
| PROJECT-PLATFORM-S06-M3 | 配置模板版本、安装、同步、三方合并和解绑 | M1-M2 | `docs/90-reports/project-platform-s06-m3-execution-report.md` | Completed |
| PROJECT-PLATFORM-S06-M4 | 兼容矩阵、S07 adapter、迁移/安全/体验和 Stage 收口 | M1-M3 | `docs/90-reports/project-platform-s06-m4-execution-report.md` | Completed |

## 5. 详细任务

### PROJECT-PLATFORM-S06-M1 唯一配置草稿、完整快照和发布校验

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M1-T01 | 审计 S03-S05 类型、字段、布局、版本、写服务、API/UI、授权、迁移和测试事实 | 双权威风险、可复用合同、owner、跨模块依赖和 S07 禁止项可定位到代码、表与测试 | Done |
| PROJECT-PLATFORM-S06-M1-T02 | 冻结 ConfigurationDraft、snapshot schema、状态机、诊断和 aggregate version 领域合同 | 唯一 active draft、editing/validating/valid/invalid/abandoned 语义及错误码无歧义 | Done |
| PROJECT-PLATFORM-S06-M1-T03 | 设计并落地 draft、draft command receipt 和 legacy draft diagnostics Flyway schema | 复合隔离、唯一 active draft、乐观版本、hash、状态约束、索引和 owner 完整 | Done |
| PROJECT-PLATFORM-S06-M1-T04 | 迁移 legacy type version draft 并收紧版本状态为 published/superseded | 无遗留 draft 版本；未知/冲突数据阻断并留下可操作诊断；历史 published v1 不改写 | Done |
| PROJECT-PLATFORM-S06-M1-T05 | 实现完整配置 snapshot assembler | 类型、字段、选项、规则、复杂引用、两类布局、条件和访问策略均进入自包含 snapshot | Done |
| PROJECT-PLATFORM-S06-M1-T06 | 实现 snapshot canonicalizer、schema version、稳定排序和 SHA-256 hash | 相同语义同 hash；顺序敏感项不被误归一；未知 schema 拒绝 | Done |
| PROJECT-PLATFORM-S06-M1-T07 | 实现 ConfigurationDraft Repository、行锁、唯一 active 查询和幂等命令回执 | 所有读写带复合边界；并发创建/更新确定冲突；同请求精确重放 | Done |
| PROJECT-PLATFORM-S06-M1-T08 | 实现统一 draft service 和触碰/刷新策略 | 所有成功配置写入在同事务刷新 draft；失败写入不改变 draft；不存在第二可变权威 | Done |
| PROJECT-PLATFORM-S06-M1-T09 | 接入类型、字段、选项、规则、布局和访问策略既有写服务 | 每条 S03-S05 写路径均更新同一 draft aggregate/version/hash，并有静态负向守卫 | Done |
| PROJECT-PLATFORM-S06-M1-T10 | 实现跨域引用、结构、预算、访问策略和发布前 validator | 错误定位稳定 key path；warning/error 分级；跨空间、悬空、隐藏依赖和超预算阻断 | Done |
| PROJECT-PLATFORM-S06-M1-T11 | 实现 draft 获取、更新、validate、abandon API/DTO/授权/availableActions | owner/admin 按合同操作；其他身份最小披露；abandon 幂等且不修改已发布版本 | Done |
| PROJECT-PLATFORM-S06-M1-T12 | 实现统一草稿状态 UI、诊断面板和 M1 目标验证/checkpoint | 配置页展示 hash/version/status/诊断；并发、隔离、迁移、API、前端和工作循环通过 | Done |

### PROJECT-PLATFORM-S06-M2 不可变版本、原子发布、diff 和回滚

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M2-T01 | 冻结 published snapshot、current pointer、发布回执、diff 和 rollback 合同 | legacy partial 与完整 snapshot 可区分；版本号、schema、hash、操作者和时间语义稳定 | Done |
| PROJECT-PLATFORM-S06-M2-T02 | 扩展版本与发布回执 schema 并保护历史不可变 | published/superseded snapshot update/delete 由数据库拒绝；回执作用域和精确响应完整 | Done |
| PROJECT-PLATFORM-S06-M2-T03 | 实现类型行锁、锁内版本号分配、版本插入和 current pointer 切换 Repository | 并发发布无重复版本/丢失更新；旧 current 在同事务 supersede | Done |
| PROJECT-PLATFORM-S06-M2-T04 | 实现原子 publication service | validate、lock、version、pointer、draft、audit、outbox、receipt 全部成功或全部回滚 | Done |
| PROJECT-PLATFORM-S06-M2-T05 | 实现发布幂等、异载荷冲突和故障注入 | 同 request ID 精确重放首次响应；不同 hash 冲突；每个事务边界故障均无半发布 | Done |
| PROJECT-PLATFORM-S06-M2-T06 | 实现版本列表、详情和 published snapshot 最小披露 API | 无权身份不能枚举历史或隐藏配置；有权响应不依赖 live 表补齐 | Done |
| PROJECT-PLATFORM-S06-M2-T07 | 实现语义 diff engine 与稳定 key path | additive/behavioral/conditional/breaking 分类覆盖字段、选项、规则、布局和访问策略 | Done |
| PROJECT-PLATFORM-S06-M2-T08 | 实现版本对版本、draft 对 current diff API | 输入边界、schema 兼容、摘要计数、分页/预算和错误码稳定 | Done |
| PROJECT-PLATFORM-S06-M2-T09 | 实现 prepare rollback：历史 snapshot 生成新 active draft | 不移动 pointer、不修改历史；来源版本和新 draft lineage 可审计 | Done |
| PROJECT-PLATFORM-S06-M2-T10 | 实现 rollback draft 校验与再发布 | 回滚发布产生更高版本；breaking 变化需显式确认；同样经过原子发布 | Done |
| PROJECT-PLATFORM-S06-M2-T11 | 实现版本历史、diff、breaking 确认和回滚 UI | 键盘/响应式可用；危险动作信息充分；无权身份无入口 | Done |
| PROJECT-PLATFORM-S06-M2-T12 | 完成并发发布、不可变触发器、diff、回滚、审计/outbox 和浏览器目标验证 | 正反例、故障恢复、复合隔离和真实管理链路通过 | Done |
| PROJECT-PLATFORM-S06-M2-T13 | 同步当前架构/事件矩阵并完成 M2 checkpoint | 文档只声明已实现发布事实；S07 仍无实例运行能力 | Done |

### PROJECT-PLATFORM-S06-M3 配置模板版本、安装、同步、三方合并和解绑

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M3-T01 | 冻结平台/workspace 模板、模板版本、installation、lineage、升级和 detach 合同 | 来源、可见性、版本、base/upstream/local、撤回和解绑语义无歧义 | Done |
| PROJECT-PLATFORM-S06-M3-T02 | 落地 template、template version、installation、upgrade history 和命令回执 schema | 完整 snapshot 不可变；复合隔离、唯一安装、索引、状态和 owner 完整 | Done |
| PROJECT-PLATFORM-S06-M3-T03 | 实现模板 Repository、不可变版本和授权查询 | 平台预置/workspace 模板分层；无跨空间枚举；撤回不删除历史版本 | Done |
| PROJECT-PLATFORM-S06-M3-T04 | 将 WorkItemTypePresetCatalog 转为平台模板导入源 | 导入可重复、版本/hash 稳定；静态 catalog 不再承担运行时模板权威 | Done |
| PROJECT-PLATFORM-S06-M3-T05 | 实现从 published configuration version 创建 workspace 模板 | 只复制完整 snapshot；名称/code 冲突、隐藏字段和来源授权严格校验 | Done |
| PROJECT-PLATFORM-S06-M3-T06 | 实现模板安装到目标类型 active draft | 安装复制而非 live 引用；记录 source version/hash；本地 draft 可继续编辑 | Done |
| PROJECT-PLATFORM-S06-M3-T07 | 实现 base/upstream/local 三方 diff 与冲突模型 | 无冲突 additive 变化可自动合并；重命名/删除/访问收窄等冲突显式呈现 | Done |
| PROJECT-PLATFORM-S06-M3-T08 | 实现 upgrade preview、选择性 apply 和幂等回执 | 预览不写入；apply 原子更新 draft/lineage；异载荷重放冲突 | Done |
| PROJECT-PLATFORM-S06-M3-T09 | 实现 detach 幂等命令 | 保留本地 draft、版本和最后 lineage 摘要；后续不再提示上游升级 | Done |
| PROJECT-PLATFORM-S06-M3-T10 | 实现模板目录、安装、升级冲突处理和解绑 UI | 来源/版本/本地覆盖清晰；冲突可逐项决策；危险操作可访问 | Done |
| PROJECT-PLATFORM-S06-M3-T11 | 完成模板撤回、跨空间、并发、冲突、detach 和六身份目标验证 | 无越权枚举、无 live 引用、无静默覆盖；API/浏览器正反例通过 | Done |
| PROJECT-PLATFORM-S06-M3-T12 | 同步产品/架构/模块合同并完成 M3 checkpoint | 模板 lineage 事实、限制和 S07 边界清晰，工作循环通过 | Done |

### PROJECT-PLATFORM-S06-M4 兼容矩阵、S07 adapter、迁移/安全/体验和 Stage 收口

| 任务 | 内容 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S06-M4-T01 | 审计 M1-M3 全部实现、报告、迁移、边界和未关闭 gap | 每个任务可追溯到代码与 fresh evidence；阻断项 Reopen，不以 Remaining Gap 弱化 | Done |
| PROJECT-PLATFORM-S06-M4-T02 | 冻结字段/选项/规则/布局/访问策略/模板变化兼容矩阵 | 删除、变型、必填收紧、选项移除、访问收窄、入口移除、悬空引用和模板冲突均分类 | Done |
| PROJECT-PLATFORM-S06-M4-T03 | 实现配置兼容性分析服务和 API | 输出稳定影响等级、key path、原因和建议；不冒充 S07 实例迁移计划 | Done |
| PROJECT-PLATFORM-S06-M4-T04 | 冻结并实现 PublishedSnapshotAdapter | adapter 只消费完整不可变 snapshot；legacy partial 显式拒绝/降级；不查询 active draft/live 配置 | Done |
| PROJECT-PLATFORM-S06-M4-T05 | 增加静态边界守卫和负向架构测试 | 未来 runtime 不得注入 S04/S05 live repositories；foreign write/P0 不扩张 | Done |
| PROJECT-PLATFORM-S06-M4-T06 | 执行 V001/V061/V065/V078 至最新、legacy draft、重复 migrate 和回滚 rehearsal | 空库/升级路径稳定；published v1、legacy sentinel 和历史 hash 不被越界改写 | Done |
| PROJECT-PLATFORM-S06-M4-T07 | 执行 120 字段/2400 选项 snapshot/hash/diff/模板合并预算 | 配置预算达标且无 N+1；不扩张为工作项运行容量或基础设施 HA 声明 | Done |
| PROJECT-PLATFORM-S06-M4-T08 | 执行 owner/admin/member/guest/non-member/enterprise-admin API 安全矩阵 | draft/version/template/hidden field 均最小披露；跨空间 ID 负例稳定 | Done |
| PROJECT-PLATFORM-S06-M4-T09 | 执行统一配置、版本、diff、回滚和模板真实隔离浏览器体验验收 | 桌面/窄屏、键盘、错误恢复、离线和六身份入口/禁用状态符合合同 | Done |
| PROJECT-PLATFORM-S06-M4-T10 | 执行完整后端、迁移、前端、collaboration、架构、工作台、安全和生成物门禁 | full gate 无阻断；报告引用 fresh 日志；禁止 mock 冒充真实浏览器证据 | Done |
| PROJECT-PLATFORM-S06-M4-T11 | 同步 Program/索引/当前与目标架构，给出 S06 Go/Reopen 与 S07 准入并完成 route-final | 48 Task、四份报告、工作上下文和文档一致；唯一结论可执行 | Done |

## 6. Stage 验收

- `ConfigurationDraft` 是每个类型唯一可变配置权威，所有 S03-S05 写路径在同事务更新它。
- published/superseded version 保存完整自包含 snapshot，数据库和服务均禁止历史更新/删除。
- 发布具备行锁、原子 pointer 切换、审计/outbox、精确幂等回执和故障恢复证据。
- diff/rollback 不修改历史；回滚通过新草稿和更高版本完成。
- 模板是版本化 snapshot 复制，具有 installation lineage、三方升级、本地覆盖和幂等 detach。
- 六身份、跨空间、隐藏字段、迁移、规模、可访问性和真实隔离浏览器证据完整。
- S07 只能通过 `PublishedSnapshotAdapter` 消费完整发布快照；S06 不创建实例或实例迁移能力。
