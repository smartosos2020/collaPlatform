---
title: 工作项配置兼容矩阵
status: active
last_code_check: 2026-07-26
owner: project
---

# 工作项配置兼容矩阵

## 1. 定位

本矩阵冻结 S06 published snapshot 之间的配置影响语义，供版本比较、发布确认、模板升级和 S07 准入使用。它只回答“新配置是否可能改变既有实例语义”，不生成实例迁移批次、值映射或回填计划；后者属于 S07。

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
- `PublishedSnapshotAdapter` 只接受精确 schema v1、完整且 hash 一致的 published/superseded snapshot。
- S07 runtime 只能经 `PublishedSnapshotReader` 与 adapter 获取冻结快照；active draft、live 字段/选项/布局/策略和模板目录不得成为运行事实来源。

## 4. S07 准入

S07 可以基于本矩阵建立实例创建和迁移计划，但必须另外提供目标版本绑定、值映射、默认值回填、逐实例校验、失败清单、幂等批次和回滚。S06 的兼容报告不能替代这些实例级证据。
