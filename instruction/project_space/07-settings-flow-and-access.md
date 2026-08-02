---
title: 项目空间设置（三）：流程与权限
status: active
last_code_check: 2026-08-01
code_commit: 0822e945ddc0
---

# 设置 → 流程与权限

“流程与权限”选择一个任务模板后，集中配置谁能做什么、事项如何关联、怎样流转，并完成校验和发布。

如果空间只有一个模板，页面会自动选中；有多个模板时，必须在“当前任务模板”下拉框中明确选择。页面不再通过“进入任务模板的流程与权限配置”重复弹窗跳转。

页面按纵向顺序包含：

1. 配置草稿状态与发布操作
2. 数据权限策略
3. 关系定义
4. 轻量状态流或节点流
5. 存量实例初始化
6. 配置模板
7. 版本差异、兼容提示和版本历史

## 1. 配置草稿与发布

### 1.1 草稿摘要

顶部显示：

- 草稿状态
- 聚合版本
- 快照 schema 版本
- 更新时间
- 阻断项数量
- 提醒数量

草稿状态：

| 状态 | 含义 |
| --- | --- |
| 编辑中 | 配置仍在修改，尚未形成有效校验结果 |
| 校验中 | 服务端正在校验 |
| 校验通过 | 当前已保存快照可以进入发布判断 |
| 存在阻断 | 有错误，不能发布 |
| 已放弃 | 草稿成为不可变历史，不再编辑 |

### 1.2 校验配置

“校验配置”只校验已经保存到服务端草稿的内容，包括：

- 模板定义
- 字段和选项
- 新建页、详情页布局
- 布局字段引用和显示条件
- 字段访问策略
- 状态流或节点流
- 关系定义
- 数据权限策略

主要规则：

- 字段 key、选项 key、节点 key 存在且唯一。
- 布局不能引用停用字段。
- 建议同时配置新建页和详情页；当前缺一个通常是 warning。
- `stateFlow` 与 `nodeFlow` 不能同时存在。
- 关系控件必须引用已有 relation key。
- 权限的字段、节点、关系限定必须引用现有 key。

点击校验前，要逐块确认“已保存/已同步”。否则服务端校验的是旧草稿，不是当前浏览器中尚未保存的输入。

### 1.3 发布版本

发布条件：

- 空间可写。
- 草稿状态为 `valid`。
- 快照完整且 schema 受支持。
- 兼容分析没有 `blocked`。
- 没有未保存的轻量状态流修改。

发布会创建新的不可变版本，并原子切换任务模板的 current pointer；旧版本保留为历史，不会原地改写。

兼容结论：

- 兼容
- 需复核
- 需迁移
- 阻断

破坏性变化或需要迁移时必须明确确认；`blocked` 没有普通绕过按钮。

### 1.4 放弃草稿

把当前草稿变为不可变的 `abandoned`。之后再次编辑会创建新草稿，当前已发布版本不受影响。

## 2. 数据权限策略

### 2.1 “空间角色 4 / 事项角色 4 / 策略 5”是什么意思

这三个数字是当前选中任务模板的权限模型中，三类定义的数量：

- 空间角色 4：guest、member、admin、owner。
- 事项角色 4：creator、assignee、collaborator、watcher。
- 策略 5：当前有 5 条权限策略规则。

它们不是空间人数、事项参与人数或全平台策略总数。

新建模板的初始权限模型都由同一个确定性系统预置生成，所以未经修改时，每个模板的数量和定义都一样；唯一直接绑定差异是 `boundTypeKey`。保存到各自草稿后，每个模板拥有独立快照，可以新增自定义策略或调整系统策略内容，因此以后数量和值可能不同，模板之间不会自动同步。

### 2.2 四个空间角色

| 角色 | 继承 | 自身基础动作 | 作用 |
| --- | --- | --- | --- |
| guest | 无 | view、comment、permission_request | 最低空间访问基线 |
| member | guest | create、view、edit、archive、restore、comment、attach、transition、relate、accept_link、permission_explain、permission_request | 日常事项协作 |
| admin | member | participant_manage、workflow_manage、relation_manage、role_assign、policy_manage、governance_inspect、migration_manage | 空间治理 |
| owner | admin | delete | 最终负责人和危险操作 |

继承意味着 owner 具备 admin、member、guest 的角色链；admin 具备 member、guest；member 具备 guest。真正动作仍要经过策略匹配，不是看到角色定义就无条件允许。

### 2.3 四个事项角色

| 角色 | 显示名 | 来源 | 是否多人 | 作用 |
| --- | --- | --- | --- | --- |
| creator | 创建人 | 创建事实 | 否 | 标识事项创建者 |
| assignee | 负责人 | 显式指定或字段 | 是 | 承担事项责任 |
| collaborator | 协作者 | 显式、参与者或组 | 是 | 参与协作 |
| watcher | 关注者 | 显式或参与者 | 是 | 关注变化 |

事项角色本身只描述人与当前事项的关系。是否因此获得动作，取决于策略。例如默认只有 creator 被一条系统策略直接引用；assignee、collaborator、watcher 主要供流程、守卫、个人工作和自定义策略使用。

### 2.4 五条系统预置策略

所有五条初始策略都是 `allow`、数据范围 `all`、字段/节点/关系限定为空。它们随模板权限模型发布。

#### 1. `space_guest_baseline`

- 主体：空间角色 guest。
- 动作：view、comment、permission_request、field_read。
- 优先级：100。
- 作用：访客最低查看、评论、申请和字段读取基线。

#### 2. `space_member_baseline`

- 主体：空间角色 member。
- 动作：create、view、edit、archive、restore、comment、attach、transition、relate、accept_link、permission_explain、permission_request、field_read、field_write。
- 优先级：200。
- 作用：普通成员的事项创建、编辑、协作、流转和字段读写基线。

#### 3. `space_admin_governance`

- 主体：空间角色 admin。
- 动作：participant_manage、workflow_manage、relation_manage、role_assign、policy_manage、governance_inspect、migration_manage。
- 优先级：300。
- 作用：空间管理员的参与人、流程、关系、角色、策略、治理检查和迁移能力。

#### 4. `space_owner_dangerous_actions`

- 主体：空间角色 owner。
- 动作：delete。
- 优先级：400。
- 作用：只把删除这类危险动作留给空间所有者。

#### 5. `creator_collaboration`

- 主体：事项角色 creator。
- 动作：view、edit、comment、attach、transition、relate。
- 优先级：250。
- 作用：即使从事项角色角度判断，也让创建人保持基础协作能力。

“系统预置”表示 UI 保护该规则的永久 key 和删除操作，不表示其内容永远不可改，也不表示它高于自定义 deny。系统策略仍可修改效果、动作、主体、范围和限定；修改后必须保存、校验和发布。

### 2.5 一条策略的各项值

#### 永久 policy key

策略的稳定标识，用于版本差异、诊断和审计。系统策略不能改 key；自定义策略创建后也应保持稳定。

#### 效果 effect

- `allow`：匹配时提供允许候选。
- `deny`：匹配时拒绝。

全局采用 deny 优先：任何命中的 deny 都覆盖所有 allow；没有任何策略匹配时默认拒绝。

#### 优先级 priority

范围 0–10,000。当前主要决定命中解释和展示顺序，不是冲突胜负。高优先级 allow 不能战胜低优先级 deny。

#### 动作 action keys

| 动作 | 含义 |
| --- | --- |
| create | 创建事项 |
| view | 查看事项 |
| edit | 编辑事项 |
| archive / restore / delete | 归档、恢复、删除 |
| comment / attach | 评论、附件 |
| participant_manage | 管理参与者 |
| transition | 执行状态动作 |
| workflow_manage | 管理流程和纠错 |
| relate / accept_link | 建立关系、接受跨空间建链 |
| relation_manage | 管理关系定义或治理动作 |
| field_read / field_write | 字段读写 |
| role_assign / policy_manage | 分配角色、管理策略 |
| permission_explain / permission_request | 查看权限解释、申请权限 |
| governance_inspect | 治理检查 |
| migration_manage | 迁移和 legacy 承接 |

#### Subject 类型和 key

Subject 表示“这条策略针对谁”。可选类型：

- `enterprise_role`
- `space_role`
- `work_item_role`
- `participant_role`
- `user`
- `department`
- `user_group`
- `everyone`

key 用于角色类主体，例如 `member`、`creator`；user/department/user_group 还需要 subjectId。模型支持多个 selector，含义为命中任一主体，但当前页面只编辑第一个 selector。

#### Data scope

表示策略作用于哪些事项：

| 值 | 含义 |
| --- | --- |
| `all` | 所有满足其他条件的事项 |
| `created_by_subject` | 由主体创建的事项 |
| `participating` | 主体参与的事项 |
| `work_item_role` | 主体在事项中承担指定角色 |
| `field_match` | 指定字段满足条件 |
| `explicit_set` | 明确列出的事项集合 |

Scope values 用于补充具体角色、值或事项标识。`all` 时不需要。

#### 字段、节点、关系限定

- 字段 key：只在相关字段动作上匹配。
- 节点 key：只在指定流程节点匹配。
- 关系 key：只在指定关系上匹配。
- 空数组：不限制该维度。

这些限定只能收紧策略匹配范围，不能单独产生授权。

#### system 与 sortOrder

- `system`：UI 是否按系统预置保护删除和永久 key；运行时不会因为它是系统规则就提高权限。
- `sortOrder`：稳定显示和规范化顺序，不决定 allow/deny 冲突。

### 2.6 完整判定方式

一条策略需要同时满足：

```text
动作匹配
AND 主体匹配
AND 数据范围匹配
AND 字段限定匹配
AND 节点限定匹配
AND 关系限定匹配
```

然后汇总所有命中规则：

1. 有任一 deny → 拒绝。
2. 没有 deny 且有 allow → 允许。
3. 没有匹配 → 拒绝。

保存权限模型只写入配置草稿；必须重新校验和发布后才成为新版本运行时事实。

### 2.7 当前权限编辑器边界

以下能力目前不适合仅靠 UI 配置到生产：

- 页面只编辑第一个 Subject，模型虽支持多个。
- user、department、user_group 需要 subjectId，但页面没有相应输入。
- department/user_group 运行时尚未完整支持。
- `field_match` 缺少字段和操作符编辑控件，并存在校验端 `eq`、运行时 `equals` 的命名差异。
- `work_item_role` scope 的校验字段和运行时读取字段不一致。
- `explicit_set` 需要 UUID，但当前语义 key 归一化可能破坏 UUID 连字符。

在这些边界修复前，优先使用 space_role、work_item_role 主体，`all`、created_by_subject、participating 等已验证范围，并通过实际测试账号验收。

## 3. 关系定义

### 3.1 作用

定义工作项之间允许建立什么关系、方向、基数和删除行为。发布后在“工作项详情 → 事项关系”中使用。

### 3.2 字段

- 永久 relation key
- 关系类型
- 方向
- 源端/目标端基数
- 正向/反向名称
- 源类型 key
- 目标类型 key
- 删除策略
- 最大图深度
- 是否允许自关联

关系类型：

| 类型 | 语义 |
| --- | --- |
| `normal` | 普通关系，可有向/无向，可配置自关联 |
| `parent_child` | 父子层级，强制有向，禁止自关联 |
| `dependency` | 依赖，强制有向，禁止自关联 |
| `blocking` | 阻塞，强制有向，禁止自关联 |

基数为 one 或 many。删除策略：

- `restrict`：存在关系时限制删除。
- `detach`：删除时解除关系。
- `retain_history`：保留历史关系语义。

最大深度为 1–64。relation key 必须唯一；正反向名称必填；源类型必须包含当前模板；目标类型不能为空。

关系保存到草稿后仍需校验和发布。

## 4. 轻量状态流

当快照包含 `stateFlow` 且不含 `nodeFlow` 时显示。适合一次只处于一个状态的生命周期。

内部子 Tab 顺序：

1. 状态
2. 动作
3. 转换
4. 守卫
5. 预览

### 4.1 状态

字段：永久 key、展示名、分类、颜色、说明。

分类：initial、active、terminal、canceled。必须恰好一个 initial；没有 terminal 会产生提醒。

### 4.2 动作

字段：永久 key、展示名、类型、授权角色、必填字段、说明。

类型：forward、return、reopen、terminate、restore。

授权角色可选空间角色 owner/admin/member/guest，以及事项角色 assignee/collaborator/watcher。模型还包含 fieldPatch 和 sideEffectKeys，当前页面没有编辑控件。

### 4.3 转换

定义“哪个动作把哪个来源状态推进到哪个目标状态”，字段包括转换 key、动作、来源、目标和可选守卫。

### 4.4 声明式守卫

类型：field、participant、space_role、all、any、not。

可配置守卫 key、操作符、字段、参与者角色、空间角色、组合守卫和比较值 JSON。守卫是额外业务条件，不能替代动作授权或数据权限。

### 4.5 预览与未保存保护

预览以状态为节点展示出向动作、目标和守卫。

轻量状态流有本地未保存修改时，会锁住校验、发布、放弃草稿、模板操作和其他配置编辑。必须先“保存到草稿”或“放弃本地修改”。

## 5. 节点流设计器

当快照含 `nodeFlow` 时显示节点流，并不再显示轻量状态流。当前没有在页面中从状态流一键切换到节点流的按钮，通常由配置模板或已有快照带入。

### 5.1 阶段

包含永久 stage key、名称、说明和顺序。当前 UI 只能改阶段名称，不能改 key、说明或删除阶段。

### 5.2 节点

类型：start、manual、automatic、branch、join、end。

处理策略：automatic、single、any、all、quorum。

字段包括名称、所属阶段、类型、处理策略、候选角色、法定人数和配置 JSON。

### 5.3 连线和高级配置

连线包含永久 edge key、来源节点、目标节点；优先级和条件存在于模型，但当前列表没有可视化编辑控件。

“表单、指派、产物、时限”通过节点配置 JSON 编辑；branches、joins、recoveryCommands、compensations 通过高级 JSON 编辑。非法 JSON 只保留最后一次合法值，最终由服务端校验。

重要边界：顶层发布面板只会被轻量状态流的 dirty 状态锁住，不会感知节点流未保存。发布前必须先在节点流中确认“已保存”并点击“保存节点流”，否则发布的仍是旧草稿快照。

## 6. 存量实例初始化

发布新流程不会静默迁移旧工作项。

### 6.1 轻量状态流初始化

仅用于没有 current state 的旧实例：

1. 显式选择工作项 manifest。
2. 填写 10–500 字原因。
3. 确认初始化。
4. 验证批次。
5. 失败时续跑失败单元。

目标固定为当前不可变发布版本的 initial 状态，不覆盖已初始化实例。

### 6.2 节点流初始化

输入每行一个 WorkItem UUID，创建批次、续跑、验证。只处理显式列出的 UUID，不静默切换绑定版本。

两种 backfill 使用的是当前已发布版本，不是正在编辑的草稿。因此顺序必须是先发布目标版本，再初始化存量实例。

## 7. 配置模板

配置模板复用“一个任务模板的完整已发布配置”，与跨多个模块的“场景模板”不同。

### 7.1 安装

选择平台模板或工作区模板后安装。快照复制到当前配置草稿，本地后续修改不会反向修改模板；安装不会直接发布。

### 7.2 升级

关联模板出现新版本时，先做三方升级预览。冲突逐项选择保留本地或采用上游，再把合并结果应用到当前草稿，最后重新校验和发布。

### 7.3 解绑

解除上游引用，但保留本地草稿、配置版本和最后来源摘要；以后不再提示上游升级。

### 7.4 保存为模板

只有当前任务模板已有完整发布版本时可用。填写模板名称、模板代码和说明。保存的是当前已发布不可变版本，不是未发布草稿。

## 8. 版本差异、兼容与历史

页面显示：

- 当前发布版本
- 草稿 diff 数量
- 兼容结论
- 每个变化的 keyPath、changeType、impact
- 兼容与迁移建议
- 历史版本号、哈希、schema、发布时间
- legacy partial 标识
- 回滚发布标识

### 8.1 回滚

回滚不会立即把 current pointer 指回旧版本，而是：

1. 放弃当前草稿。
2. 用历史完整版本生成一个新回滚草稿。
3. 重新校验。
4. 重新发布成新版本。

当前版本和 legacy partial 版本不能直接生成回滚草稿。

## 9. 发布后在哪里看到变化

发布只切换任务模板的当前版本：

| 配置 | 运行位置 |
| --- | --- |
| 完整可用模板 | 概览 → 可用工作项；工作项模板筛选 |
| create 布局和字段权限 | 工作项 → 新建工作项 |
| detail 布局和字段权限 | 工作项详情 → 事项详情 |
| 状态流 | 工作项详情 → 状态流程 |
| 节点流 | 工作项详情 → 审批与协作流程 |
| 关系定义 | 工作项详情 → 事项关系 |
| 数据权限 | 工作项能力、字段投影、详情 → 权限 |

已有工作项保存自己的 `typeVersionId + configHash`，继续使用创建时的旧快照。发布不会自动改写已有实例的字段、状态、流程或权限，也不会静默迁移。

## 10. 发布前检查清单

1. 工作模型中的字段、create/detail 布局和字段权限都已保存。
2. 权限模型显示“已同步”，自定义策略避开当前未接线范围。
3. relation key 合法，页面关系节点引用一致。
4. 状态流或节点流只存在一种。
5. 节点流明确点击过“保存节点流”。
6. 校验结果没有阻断。
7. 已阅读兼容和迁移提示。
8. 明确发布只影响新 current version，不迁移旧事项。
9. 发布后用 owner、member、guest 创建测试事项并检查“权限”。

[上一章：工作模型](./06-settings-work-model.md) · [下一章：自动化与协同](./08-settings-automation.md) · [返回手册首页](./README.md)
