# PROJECT-PLATFORM-S21-M6 渐进引导实现审计

## 1. 审计范围

本审计把 M4 信息架构、M5 简洁 Shell、S20 四场景模板及 S03-S19
公共 owner 合同转换成 M6 实现约束，覆盖：

- workspace/space/user 隔离的 onboarding 状态；
- 研发、市场、HR、交付和基础空间五条首次进入路径；
- 管理者设置与成员首次任务 checklist；
- 选择、预检、安装、配置、校验、发布和回退的语义边界；
- dismissal、resume、reset、版本冲突、离线和低敏遥测；
- 六身份、三视口、键盘、长名称和真实隔离清理。

S20 保持归档完成状态。M6 只消费其公开目录、validation、installation 和
command API，不复制场景 manifest，也不修改 S20 历史结论。

## 2. 实现不变量

1. onboarding 状态是可重置体验状态，不是授权、角色、空间状态或业务完成事实。
2. 服务端状态按 workspace、space、user 唯一隔离；响应中的动作每次根据当前空间可见性和 capability 重新计算。
3. 选择场景或基础空间只保存路径选择，不创建类型、不安装模板、不发布配置。
4. `dry-run` 只展示计划步骤和影响；只有用户明确确认后才调用既有 install command。
5. S20 install 当前只实际创建 `work_item_type` 组件；其余步骤为公共 owner reference 校验。界面不得描述成流程、视图、自动化或指标均已配置。
6. 保存草稿不等于生效；validate、diff、compatibility、publish 和 rollback-as-new-draft 继续由配置发布 owner 决定。
7. 引导编排不得读取或写入其他模块私表，不得跳过 expected version、稳定 request ID、receipt、确认或当前权限校准。
8. 未决网络结果重试必须复用同一 command intent 的 request ID；收到确定服务端结果后才生成下一 request ID。
9. checklist 的业务步骤只能在对应公共命令成功后推进；手工勾选不能伪造安装、发布、交接或通知成功。
10. dismissal 只停止主动提示，不能删除业务配置，也不能移除显式“继续引导”入口；reset 只清理当前用户的体验状态。
11. 离线或读取失败时不覆盖服务端状态、不发送业务命令、不显示成功；恢复后由 REST 重新校准。
12. 遥测只允许稳定步骤、结果、耗时区间和安全错误码，不保存标题、字段值、文件名、候选人、员工、客户信息或个人评分。

## 3. 状态与 checklist 边界

onboarding 合同至少区分：

- `schemaVersion`：持久化和 API 形状版本；
- `flowVersion`/`currentFlowVersion`：已保存步骤定义版本与当前定义版本；
- `startingPoint`：`unselected`、`blank`，或 `scenario` 加四个稳定 `scenarioKey`；
- `acknowledgedSteps`：稳定 step key 与 `seen`/`skipped` 提示回执；
- `dismissed`：当前 flow 是否暂停主动提示；
- CAS `version` 与 `updatedAt`；
- 当前响应推导的 `track`、`readOnly` 和 checklist。

不得持久化：

- 角色名、capability 或权限快照；
- 空间、工作项、候选人或客户标题；
- 字段值、评论、附件名或文件内容；
- scenario manifest 副本；
- 已安装、已发布、已交接等业务结果快照。

业务完成状态应通过现有公共响应校准。体验 step 仅说明用户已经走过引导，
不能替代对应业务对象、不可变版本或 command receipt。

## 4. 五条路径

| 路径 | 选择阶段 | 基础闭环 | 增强能力 |
| --- | --- | --- | --- |
| development | 展示 13 个计划组件及禁止边界 | 预检、确认安装、成员、`platform-task` 首项、交接 | 流程、看板、计划、自动化和指标按需进入 owner 页面 |
| marketing | 展示 15 个计划组件及文件/渠道边界 | 预检、确认安装、成员、`scenario_marketing_content` 首项、交接 | 日历、投放、通知和复盘按需进入 owner 页面 |
| human-resources | 展示 16 个计划组件及隐私边界 | 预检、确认安装、成员、`scenario_hr_position` 首项、交接 | 候选人、评价、提醒和指标继续逐次受权 |
| delivery | 展示 16 个计划组件及证据/签署边界 | 预检、确认安装、成员、`scenario_delivery_task` 首项、交接 | 风险、交付物、评审、验收和治理按需进入 owner 页面 |
| blank | 明确不调用 scenario install | 任务模板、字段/页面、流程、权限、校验、发布、成员、首项 | 自动化、指标、跨空间和场景模板按需展开 |

模式偏好与路径选择相互独立。引导不得静默切换简洁/高级模式；简洁模式可直接
完成基础闭环，高级设置仅作为按需入口。

## 5. 角色和失败关闭

| 身份 | 引导形态 | 禁止行为 |
| --- | --- | --- |
| owner | 管理者完整路径 | 不绕过 owner-only 确认或公共命令 |
| admin | 当前 capability 允许的管理者路径 | 不因文案取得 owner-only 动作 |
| member | 找工作、创建/更新、评论、附件、流转、通知路径 | 不加载成员治理、安装或发布写入口 |
| guest | 当前受权只读步骤和求助路径 | 不显示或预取 mutation |
| outsider | 无私有空间 onboarding 内容 | 404 不泄漏空间名、路径或 step |
| enterprise-admin 非成员 | 企业治理 Shell | 不以治理身份旁路空间成员边界 |

disabled/archived 空间只显示状态解释、受权恢复入口和求助路径。错误信息必须可行动，
但不得披露隐藏对象标题、成员、策略、路径选择或 checklist 状态。

## 6. 公共 owner 调用

引导只可组合当前公开合同：

- `GET /api/project-spaces/{spaceId}/onboarding`；
- `POST /api/project-spaces/{spaceId}/onboarding/commands`，命令 envelope 固定为
  `requestId`、`schemaVersion=1`、`flowVersion=s21-m6-v1`、`expectedVersion`
  与白名单 action；
- `POST /api/project-spaces/{spaceId}/onboarding/telemetry`，只接受至多 20 个
  稳定低敏事件；
- S20 scenario catalog、validation、installation、dry-run/install/retry/upgrade/detach；
- project-space members；
- WorkItem type、field、layout、state/node flow、relation 和 permission；
- configuration draft validate/diff/compatibility/publish/rollback；
- canonical WorkItem create/update/comment/attachment/workflow；
- notification 与 personal-work 当前用户响应。

引导服务本身只拥有体验状态和低敏事件，不增加安装、配置、成员、工作项、文件或通知的第二写入口。

命令白名单为 `select_starting_point`、`acknowledge_step`、`dismiss`、
`resume`、`upgrade_flow`、`set_telemetry_opt_out` 和 `reset`。选择基础空间时
`startingPoint=blank` 且不能携带 scenario key；选择场景时
`startingPoint=scenario` 且 `scenarioKey` 只能为 `development`、`marketing`、
`human-resources` 或 `delivery`。所有响应固定返回
`selectionEffect=experience_only`、`installationRequested=false` 和
`publicationRequested=false`，避免把体验选择描述成业务写入。

## 7. 真实隔离浏览器设计

`web/e2e/project-platform-s21-m6.spec.ts` 使用随机身份和私有空间，禁止
`page.route`/`context.route`。代表性 flow 应覆盖：

1. 四场景和基础空间的默认状态与跨空间隔离；
2. 选择前后类型数量、installation 和 published facts 不变；
3. dismissal、显式 resume、reset 与 CAS one-winner；
4. owner/admin/member/guest/outsider/enterprise-admin 的当前边界；
5. 管理者与成员 checklist、业务语言帮助和安全失败解释；
6. offline 不发送 onboarding/business command，恢复后 REST 校准；
7. 1440/1366/820、键盘、焦点、长名称和无横向溢出；
8. finally 恢复网络、关闭额外 context、归档空间并下线临时身份。

稳定定位点以最终实现为准，至少保留：

- `project-space-onboarding`
- `project-space-onboarding-open`
- `project-space-onboarding-paths`

## 8. 准入判断

M6 只有在状态迁移/服务测试、Web lint/build、真实隔离浏览器和 stage finish
同时通过后才可写 Go。自动化证明工程闭环，不是真人易用性、学习成本或采用结论；
真人试用仍由 M8 独立执行。
