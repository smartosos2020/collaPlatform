---
title: 工作项配置兼容矩阵
status: active
last_code_check: 2026-07-27
owner: project
---

# 工作项配置兼容矩阵

## 1. 定位

本矩阵冻结 S06-S08 published snapshot 之间的配置影响语义，供版本比较、发布确认、模板升级、WorkItem 迁移与状态流升级使用。它只回答“新配置是否可能改变既有实例语义”，不生成实例迁移批次、值/状态映射或回填计划。

影响等级固定为：

| 等级 | 含义 | S06 行为 |
| --- | --- | --- |
| `compatible` | 不改变既有实例解释 | 可继续发布或升级 |
| `review_required` | 展示或行为变化，需要管理员复核 | 显示稳定 key path、原因和建议 |
| `migration_required` | 既有实例需要显式值映射、回填或重新校验 | 标记实例迁移前置，不自动改写实例 |
| `blocked` | 快照不可解释、引用悬空或无法安全升级 | 拒绝静默消费，修复后重试 |

## 2. 变化矩阵

| 配置域 | 变化 | 影响 | 稳定原因码 | 建议 |
| --- | --- | --- | --- | --- |
| 字段 | 新增非必填字段 | compatible | `field_added` | 新实例可使用，旧实例保持缺省 |
| 字段 | 删除字段 | migration_required | `field_removed` | 明确旧值保留、归档或映射目标 |
| 字段 | 字段类型变化 | migration_required | `field_type_changed` | 提供类型转换和失败清单 |
| 字段 | optional 收紧为 required | migration_required | `required_tightened` | 回填缺失值后再迁移实例 |
| 字段 | 名称、说明、展示属性变化 | review_required | `behavior_changed` | 复核成员可理解性和页面影响 |
| 选项 | 新增选项 | compatible | `option_added` | 无需改写旧值 |
| 选项 | 移除或停用仍被引用的选项 | migration_required | `option_removed` | 映射旧值或保留历史解释 |
| 选项 | 名称、颜色、排序变化 | review_required | `behavior_changed` | 复核报表、自动化和用户认知 |
| 规则 | 校验范围放宽 | compatible | `constraint_relaxed` | 新写入采用新规则 |
| 规则 | 校验范围收紧 | migration_required | `constraint_tightened` | 重新校验并列出不合规实例 |
| 布局 | 新增可选入口或控件 | compatible | `layout_entry_added` | 不改变已有字段值 |
| 布局 | 移除必需入口或字段控件 | review_required | `layout_entry_removed` | 确认字段仍可访问或提供替代入口 |
| 访问策略 | read/write 收窄为 read/hidden | migration_required | `access_narrowed` | 复核责任人与存量编辑流程 |
| 访问策略 | 权限放宽 | review_required | `access_widened` | 执行安全复核后发布 |
| 状态流 | 无状态流版本新增状态流 | migration_required | `state_flow_added` | 旧实例保持 capability missing，使用显式初始化/backfill |
| 状态流 | 删除整个状态流 | blocked | `state_flow_removed` | 保留旧绑定或提供受控停用/迁移方案 |
| 状态 | 删除永久 state key | migration_required | `state_removed` | 提供旧 state key 到目标 key 的显式映射 |
| 状态 | initial/category/terminal/canceled 语义变化 | migration_required | `state_category_changed` | 重新校验实例并提供映射/失败清单 |
| 动作 | 删除 action 或改变授权/必填/patch/副作用 | review_required | `action_removed` / `action_behavior_changed` | 复核客户端、角色和存量流程 |
| 转换 | 删除或重定向 from/to/action | review_required | `transition_removed` / `transition_changed` | 复核可达性并显式迁移受影响状态 |
| Guard | 删除、替换或收紧声明式 guard | migration_required | `guard_removed` / `guard_changed` | 重新校验存量实例；未知 operator 失败关闭 |
| 节点流 | 无节点流版本新增节点流 | migration_required | `node_flow_added` | 旧实例保持 capability missing，使用显式 manifest backfill |
| 节点流 | 删除整个节点流 | blocked | `node_flow_removed` | 保持旧绑定，或发布可解释目标版本后显式终止/升级 |
| 节点 | 删除、改 kind 或永久 node key | migration_required | `node_removed` / `node_kind_changed` | 对全部 active source node 提供显式 one-to-one/split/merge map |
| 边/阶段 | 删除、重定向边或移动阶段改变可达语义 | review_required | `edge_removed` / `edge_changed` / `stage_changed` | 复核 active token 与后续路径，不能按 label 自动映射 |
| 分支/汇聚 | mode、条件、优先级、join policy/quorum 改变 | migration_required | `branch_changed` / `join_changed` | 对非空实例进行映射与并发闭包校验 |
| 节点任务 | assignment/form/artifact/schedule 收紧或移除 | migration_required | `node_task_policy_changed` | 重新校验处理人、必填字段、交付物和到期事实 |
| 恢复/补偿 | 删除恢复命令、改变来源/目标/授权/确认或补偿顺序 | review_required | `node_recovery_changed` | 复核运维入口与 runbook；既有 history/ledger 不改写 |
| 引用 | 字段、选项、节点、策略引用悬空 | blocked | `dangling_reference` | 修复快照闭包，禁止猜测绑定 |
| 模板 | base/upstream/local 无冲突 additive 变化 | compatible | `template_additive_merge` | 可自动合并到草稿 |
| 模板 | 同一稳定 key 双方修改或删改冲突 | review_required | `template_merge_conflict` | 管理员逐项选择 local/upstream |
| 快照 | schema 0 legacy partial | blocked | `legacy_partial_snapshot` | 仅保留历史展示，不进入 runtime |
| 快照 | 未知未来 schema | blocked | `unsupported_snapshot_schema` | 升级解释器后再消费 |
| 快照 | canonical hash 不匹配 | blocked | `snapshot_integrity_failure` | 隔离版本并调查数据完整性 |

## 3. 稳定输出合同

- key path 使用字段、选项、布局和策略的稳定 semantic key，不使用数组位置。
- 报告返回 `fromHash`、`toHash`、`overallImpact`、findings、各等级计数和 `instanceMigrationRequired`。
- findings 上限为 1000；超过预算必须显式失败，不能截断后宣称兼容。
- 未识别变化至少归入 `review_required`，不得默认 compatible。
- `PublishedSnapshotAdapter` 接受完整且 hash 一致的 schema v1-v2 published/superseded snapshot；v1 或 v2 无 `stateFlow` 时返回显式 `not_configured` capability，未来 schema 拒绝。
- S07 runtime 只能经 `PublishedSnapshotReader` 与 adapter 获取冻结快照；active draft、live 字段/选项/布局/策略和模板目录不得成为运行事实来源。

## 4. S07-S08 准入

S07 可以基于本矩阵建立实例创建和字段迁移计划，S08 可据此建立状态初始化和版本升级计划；两者都必须另外提供目标版本绑定、显式映射、逐实例校验、失败清单、幂等批次和回滚。兼容报告不能替代实例级证据，普通发布也不能绕过 `blocked` 或 `migration_required`。

S08-M4 已把同一矩阵接入空间配置 UI 和 publication service：`blocked` 在服务端无条件拒绝，`migration_required` 要求精确危险确认后才可创建新版本；确认不会改写任何既有 WorkItem 绑定。前端隐藏按钮或改请求都不能绕过服务端分析，失败后的草稿输入保持可编辑。

## 5. S08 状态恢复与升级 Runbook

### 5.1 计划与停止条件

1. 确认目标 published/superseded snapshot 的 canonical hash、schema、stateFlow 和唯一 initial state 均可解释。
2. 冻结待处理 WorkItem ID manifest；单批 1-500 个、同 workspace/space/type，禁止依赖“所有旧实例”隐式扫描。
3. 对无 current state 的 pre-S08 实例使用 backfill；对已有 current state 且切换绑定版本的实例使用 binding upgrade；单实例事实错误才使用 correction。
4. 任一 `blocked` finding、缺失 state-key map、来源 binding/version 漂移、字段规范化失败、跨空间对象、未知 snapshot/event schema 或权限不满足时立即停止，不允许直接改表绕过。

### 5.2 执行

- backfill 请求必须提供目标 type version、目标 initial state、manifest、10-500 字原因、request ID 和确认串 `INITIALIZE_EXISTING_WORKFLOW_STATES`。批次先冻结 source binding/config/work-item version，再逐单元提交 WorkItem binding、字段投影、current state、system initialization history、activity、audit 和两个 outbox event。
- binding upgrade 必须提供目标 type version、显式目标 state key、expected WorkItem version、原因、request ID 和确认串 `UPGRADE_WORKFLOW_BINDING`。state rename/delete/merge 不能由服务端按 label 或最近历史猜测。
- correction 必须提供目标 state key、expected version、原因、request ID 和确认串 `CORRECT_WORKFLOW_STATE`。它追加 correction 历史，不删除、覆盖或伪造既有用户动作。
- 失败批次只通过 `RESUME_WORKFLOW_STATE_BACKFILL` 续跑 failed/pending 单元。相同 request ID/相同输入精确重放；异载荷复用稳定拒绝。

### 5.3 验证、恢复与回退边界

- 每次执行后调用 verify；只有 completed 数等于 manifest 数、所有 WorkItem/current-state binding/hash/state/version 与目标一致且 failure 列表为空时才可宣布完成。
- outbox、history、activity、audit、字段投影或约束失败会回滚当前单元；排除根因后续跑。不得删除 batch/unit/history 或手工把 failed 改为 completed。
- backfill/upgrade 提交后不提供“删除历史并恢复旧绑定”的自动回退。若目标语义错误，使用新的显式 binding upgrade/correction 追加补偿事实；生产操作另需备份、观测和变更批准。
- archive/restore 只改变 WorkItem 对象生命周期并保留 current state；terminate/restore 才改变 canceled 业务状态。两类恢复不可互相替代。
- S09 只能消费公共 command/event SPI；本 runbook 不授权创建 node token、并行分支、汇聚、会签或节点交付物。

## 6. S09 节点恢复与升级

S09-M4 使用同一兼容分析作为实例升级前置，但分析结果不代替 node map。只有目标 snapshot 可解释且总体影响不是 `blocked` 时，owner/admin 才能用覆盖全部 active source node 的显式 `one_to_one`、`split` 或 `merge` map 执行升级；缺失、额外、重复或指向非 executable node 的映射全部失败关闭。

pre-S09 WorkItem 不按“当前类型版本”静默启动节点实例，只能用冻结 source binding/version 的显式 manifest backfill。return/jump/terminate/correct 只执行绑定 snapshot 声明的 recovery command；补偿只运行注册动作。完整操作、停止、续跑、验证和回退边界见 `docs/05-runbooks/project-platform-s09-node-workflow-recovery.md`。
