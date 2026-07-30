# PROJECT-PLATFORM-S21-M7 兼容迁移、安全与工程准入审计

## 1. 审计范围与权威来源

本审计以当前路线、S21 目标架构、M4-M6 已提交合同及本地实现为权威，
把 36 个上游 Task 转换为 M7 可执行的迁移、安全、可观测和工程准入边界。
范围包括：

- legacy `/projects/:id`、现有 `/issues/:id`、类型配置深链、query 和 hash；
- 服务端模式偏好、onboarding 状态、本地最近访问和 React Query 缓存；
- workspace/space/user rollout、kill switch、TTL 与 canonical baseline；
- 六身份、直达 URL、预加载、客户端缓存、错误外形和高级 mutation；
- 低敏体验遥测、性能预算、S20 四场景回归和 M8 Engineering Go。

M7 不恢复已退出的 Project/Issue 产品，不修改 S20 完成历史，也不把自动化写成
真人易用性或采用结论。S02 的 legacy 项目创建/旧页面浏览器规格只保留为历史意图，
不能在 M7 原样重跑。

## 2. M4-M6 三十六项追溯

| 上游 Task | M7 保留的不变量 | 权威证据 |
| --- | --- | --- |
| S21-M4-T01 | 所有入口、Tab、路由、面板、身份、owner 和测试依赖继续有唯一 inventory | M4 IA audit、M4 execution report |
| S21-M4-T02 | owner/admin/member/guest/outsider/enterprise-admin 以当前 capability 决策，不以角色名授权 | M4 capability matrix、M5/M6 isolated E2E |
| S21-M4-T03 | 高频执行、项目管理、低频配置和企业治理边界不被 rollout 改写 | M4 surface classification |
| S21-M4-T04 | 概览、工作项、项目管理、成员、设置保持五个 canonical 一级入口 | M4 navigation contract、M5 registry |
| S21-M4-T05 | 空间、任务模板、工作项类型、字段、页面、流程、权限和发布术语保持统一 | M4 terminology table、M6 help copy |
| S21-M4-T06 | 简洁为安全默认；模式只控制呈现，失败和关闭均不能授权 | M4 mode contract、M5 preference owner |
| S21-M4-T07 | 导航由服务端 `availableActions` 和当前 permission decision 驱动 | M4 capability mapping、M5 Shell |
| S21-M4-T08 | `typeId`、`create`、`savedViewId`、`panel`、`source`、详情与收藏/通知上下文有明确迁移决定 | M4 route matrix、M5 compatibility flow |
| S21-M4-T09 | 上下文入口、返回来源和高级配置解释必须在迁移后可恢复 | M4 context-entry contract |
| S21-M4-T10 | 1440/1366/820、键盘、焦点、长名称和语义规范继续适用 | M4 static contract、M5/M6 E2E |
| S21-M4-T11 | 四场景可达且隐藏入口、预加载和错误外形最小披露 | M4 threat model、S20 reports |
| S21-M4-T12 | M4 的路由、权限、术语结论不可由 M7 实现细节倒推改写 | M4 execution report |
| S21-M5-T01 | M4 十二项仍逐项可追溯，M7 只增加迁移层 | M5 implementation audit |
| S21-M5-T02 | 导航顺序、分组、模式、capability 和路由元数据只有一个注册表 | project-space IA registry |
| S21-M5-T03 | route-context 只有一个解析器，兼容路径不得往返重定向 | project-space route resolver |
| S21-M5-T04 | 无权内容不渲染、不计数、不预取 | role-aware project-space Shell |
| S21-M5-T05 | 项目管理只组合计划、风险、交付、资源和指标 owner 响应 | project management surface |
| S21-M5-T06 | 设置中心只组合既有配置 owner，不复制配置事实 | advanced settings surface |
| S21-M5-T07 | 模式偏好按 workspace/space/user 隔离，版本化且不保存授权快照 | V140 preference owner |
| S21-M5-T08 | 默认落点由当前身份、capability 和空间状态决定 | M5 default-view resolver |
| S21-M5-T09 | 模式、刷新、前进后退和兼容迁移不丢受支持 query、hash 或返回上下文 | M5 deep-link flow |
| S21-M5-T10 | realtime/online/focus/多标签只触发 REST 校准，不产生成功事实 | M5 recovery contract |
| S21-M5-T11 | 直达 URL、缓存、预加载和模式切换不能绕过服务端授权 | M5 tests、M7 threat replay |
| S21-M5-T12 | 三视口、键盘、长名、深链、离线和 CAS 继续进入 route-final | M5 real isolated E2E |
| S21-M6-T01 | onboarding 只消费 S20 和公共 owner，不建立第二配置权威 | M6 implementation audit |
| S21-M6-T02 | onboarding 按 workspace/space/user 隔离，可升级、可重置且不授权 | V141 onboarding owner |
| S21-M6-T03 | 四场景或基础空间选择仍为零业务副作用 | M6 selection contract |
| S21-M6-T04 | 管理者路径仍逐步确认模板、成员、首项和交接 | M6 manager checklist |
| S21-M6-T05 | 成员路径只到达其当前 capability 允许的动作 | M6 member/guest checklist |
| S21-M6-T06 | 基础路径不要求加载全部高级配置 | M6 progressive navigation |
| S21-M6-T07 | 编排只调用公共 API，保留 expected version、request ID 和 receipt | M6 owner-call audit |
| S21-M6-T08 | 草稿、校验、影响、发布和 rollback-as-new-draft 语义保持不变 | configuration owner contracts |
| S21-M6-T09 | 体验层只显示必要业务语言和最小工程诊断 | M6 contextual help |
| S21-M6-T10 | 隐藏对象、只读、离线和错误均失败关闭并最小披露 | M6 security matrix |
| S21-M6-T11 | 体验遥测不含正文、字段值、文件名或个人绩效 | M6 telemetry contract、M7 allowlist |
| S21-M6-T12 | 四路径、基础空间、六身份、三视口和清理继续作为真实 isolated 基线 | M6 real isolated E2E |

## 3. Legacy 路由与规范上下文

兼容适配器只解析到当前 canonical project-space 路由：

| 输入 | 允许结果 | 禁止结果 |
| --- | --- | --- |
| `/projects` | `/project-spaces` | 恢复旧项目列表或旧写入口 |
| `/projects/:legacyId` | 服务端当前成员决策返回的 canonical project-space 相对路径 | 客户端猜测空间、跨 origin、绝对 URL、隐藏空间名称 |
| `/issues/:issueId` | 既有 location-only resolver 返回的 canonical WorkItem 相对路径 | 恢复 Issue 页面、Issue 内容 API 或写 API |
| `/project-spaces/:id/types/**` | 现有设置/高级配置 owner 与原对象上下文 | 复制类型/字段/布局页面 |

legacy project 只读解析合同固定为
`GET /project-spaces/legacy-resolve/{legacyProjectId}`，响应只允许
`mapped|unmigrated|failed|unavailable` 和受权 `spaceId`。其中 mapped 正向、
private non-member unavailable、无 map unmigrated 与 rollback failed 由后端集成
测试覆盖；isolated 浏览器不通过已退休 `/api/projects` 伪造 mapped fixture。

适配器必须满足：

1. 只接受以 `/project-spaces` 开始的站内相对目标；协议、host、`//`、反斜线、
   编码绕过和路径穿越一律失败关闭。
2. `typeId`、`create`、`savedViewId`、`panel`、`source`、`metricPanel`、
   `metricConfig` 按目标路由白名单保留；非法或重复控制参数由单次 query patch
   规范化。不得接受 `redirect`、`returnUrl`、`next` 或其他开放重定向参数。
3. hash 仅作为页面内上下文原样附着到受权 canonical 目标，不参与授权或服务端解析。
4. resolver 的 404/403 外形统一为“不可用或不可访问”，不披露对象、空间、成员、
   角色、目标路径或 rollout 命中原因。
5. replace 后再次解析必须稳定，不能出现 legacy/canonical 往返或 query patch 循环。
6. rollout 关闭只回到 canonical baseline，绝不重新挂载已退出的 Project/Issue 产品。

## 4. 偏好、引导与缓存迁移

迁移责任按 owner 分离：

| 状态 | 当前 owner | 迁移/回滚规则 | 删除边界 |
| --- | --- | --- | --- |
| 模式偏好 | V140 experience preference | 服务端 schema/version 与 CAS 保持权威；未知版本或读取失败使用 `simple` | 不因客户端迁移删除服务端记录 |
| onboarding | V141 onboarding state | 保留 schema/flow/version、dismiss/resume/reset 和精确 replay | reset 只重置当前用户体验状态 |
| 最近空间 | legacy `colla.project-spaces.recent` | 首次读取后幂等转换为 `colla.project-space-ui.v2.{workspaceId}.{userId}.recent`；只迁移当前可访问 ID，最多 8 个，TTL 30 天 | legacy source 至少保留到 S21-M9 route-final，不自动删除 |
| React Query | 当前 query client | key 必须包含 owner 要求的 workspace/space/user 维度；身份切换和鉴权失败清空 | 只清内存 query，不删除业务数据 |
| rollout | `GET /project-spaces/{spaceId}/experience-rollout` | 遵守服务端 `cacheMaxAgeSeconds`；过期、读取失败或 `unknown` 使用 canonical baseline | 不持久化成授权或业务事实 |
| 业务草稿 | 配置、指标及其公共 owner | 迁移前后保持字节/版本事实，不参与体验 cache 清理 | 禁止删除或改写 |

以下本地键属于业务草稿保护集，任何 M7 fallback、kill、logout 或 cache migration
都不得删除：

- `colla.metric-dashboard-draft.{spaceId}`
- `colla.metric-draft.{spaceId}`
- `colla.metric-risk-policy.{spaceId}`

新草稿缓存只在用户显式选择恢复后写入
`colla.project-space-ui.v2.{workspaceId}.{userId}.{spaceId}.{kind}`，其中 `kind` 为
`metric-semantics-draft`、`metric-risk-policy-draft` 或
`metric-dashboard-draft`。value envelope 固定包含 `schemaVersion=2`、
`createdAt`、`updatedAt`、`expiresAt` 和 `value`；恢复完成标记也必须带同一
workspace/user/space 作用域。坏 envelope、未知 schema、过期值、超长值或 storage
异常均作为 miss，不自动删除 legacy source，也不跨账号恢复。

收藏、配置草稿、published version、场景 installation、工作项、审计和不可变 receipt
同样不属于体验迁移删除范围。迁移失败只产生一次低敏恢复状态并回到简洁 baseline，
不得以“修复缓存”为由清空 origin storage。

## 5. Rollout、kill switch 与恢复

服务端冻结的读取合同为：

`GET /project-spaces/{spaceId}/experience-rollout`

响应最小字段为：

- `schemaVersion=1`、`policyVersion`、`enabled`；
- `state=enabled|baseline|temporarily_disabled|unknown`；
- `fallbackContext=canonical_project_space`；
- `evaluatedAt`、`cacheMaxAgeSeconds`；
- `telemetry.schemaVersion=1`、`enabled`、`sampleBasisPoints`、`maxBatchSize`。

workspace/space/user 的合并优先级和命中详情只由服务端计算；客户端不得重建策略。
只有 fresh、`enabled=true` 且 `state=enabled` 才启用新呈现。baseline、kill、
unknown、过期或读取失败均恢复 canonical project-space baseline。开关：

- 不新增或缓存 capability；
- 不改变 `availableActions`、current manager gate 或 S11 permission decision；
- 不绕过 expected version、确认、审计、receipt 或幂等；
- 不清理 preference、onboarding、query/hash、收藏、草稿或业务对象；
- 对 outsider 和 enterprise-admin 非成员仍返回同一最小披露外形。

## 6. 六身份与高级 mutation 复验

| 身份 | 可见范围 | M7 必须复验的拒绝边界 |
| --- | --- | --- |
| owner | 当前五入口及受权高级设置 | rollout 不跳过高风险确认、expected version、审计和 receipt |
| admin/自定义管理角色 | capability 允许的管理面 | 不能因模式或 rollout 获得 owner-only 动作 |
| member | 概览、工作项、项目管理中的当前受权内容 | 不预取成员治理、设置或高级 mutation |
| guest | 受权只读内容 | 不渲染或发送 mutation |
| outsider | 无私有空间内容 | legacy/canonical、cache 和错误均不得泄漏对象存在性 |
| enterprise-admin 非成员 | 企业治理 Shell | 不能通过治理身份旁路空间成员边界 |

每个高级配置写操作仍需逐次服务端校准当前 decision 和 manager gate，稳定 request ID
表示同一 intent，expected version 保护并发，确认只覆盖当前风险动作，成功以 owner
响应和不可变 receipt 为准。浏览器显隐、rollout、缓存和 onboarding step 均不是授权。

## 7. 低敏可观测合同

写入合同为：

`POST /project-space-experience/telemetry`

请求固定 `schemaVersion=1`，每批不超过服务端 `maxBatchSize` 且绝不超过 20。事件只允许：

- `eventKind`: `entry|mode|help|task_result|route_error|recovery`
- `routeKey`: `overview|work_items|management|members|settings|advanced_configuration|notifications|unknown`
- `mode`: `simple|advanced|baseline|unknown`
- `outcome`: `shown|opened|changed|succeeded|blocked|failed|recovered|unknown`
- `durationBucket`: `under_5s|5_to_30s|30_to_120s|2_to_10m|over_10m|unknown`
- `errorCode`: `none|not_found_or_hidden|capability_denied|space_read_only|offline|timeout|version_conflict|server_error|unknown`
- `freshness`: `fresh|stale|unknown`

除随机 `eventId` 外不得发送 user/workspace/space/object ID、URL query/hash、标题、
正文、字段值、文件名、成员、角色、场景业务内容、候选人、员工、客户或个人评分。
批次和事件请求采用局部严格 JSON allowlist；任何未声明属性，包括 `title`、`body`、
`spaceId`、`userId`，都必须在 controller 边界返回 400，不能依赖全局 Jackson
兼容设置将其静默忽略。
浏览器只执行 opt-out、disabled、offline、allowlist 与 batch gate，不做概率采样；
服务端在写入低基数指标前按 `eventId + policyVersion + evaluationSalt` 做一次确定性采样，
避免客户端和服务端重复采样形成概率平方。任何 telemetry gate 或观测失败均不得阻塞产品
功能。观测结果只用于工程健康，不是权限、业务完成、用户采用或个人绩效事实。

## 8. 性能与缓存预算

1. 项目空间初始 Shell 不预取隐藏的 members、settings、configuration、
   scenario-template、automation 或 metrics 私有接口。
2. 同一 owner/key 的并发读必须合并；mutation、realtime、online/focus 后只失效
   受影响 query，不能无界扩大 key 或重复请求。
3. members、onboarding、WorkItem 配置、CrossSpace、Scenario 和 Metric 等可选
   surface 通过 `React.lazy`/`Suspense` 按首次挂载加载；route chunk 继续受
   500 KiB 质量预算约束。
4. rollout cache 只在 fresh TTL 内复用，身份边界变化立即失效。
5. 1440、1366、820 三视口的 document 级横向溢出不超过 1 px，主动作、模式、
   帮助、错误恢复和键盘焦点均可达。

## 9. S20 四场景回归

`development`、`marketing`、`human-resources`、`delivery` 在新入口和 canonical
baseline 下都必须可到达现有 catalog、validate、dry-run/install、diff、upgrade、
首个 WorkItem 和 configuration publish owner。M7 只复验公共合同：

- rollout/kill 前后 installation、published version、类型、工作项和 receipt 不丢；
- query patch、偏好/cache migration 和 onboarding reset 不触发业务安装或发布；
- 四场景隐私、确认、权限和 rollback 边界保持 S20 原结论；
- 本报告不修改 S20 路线状态或历史执行报告。

## 10. 真实隔离 E2E 与清理

`web/e2e/project-platform-s21-m7.spec.ts` 必须使用真实 API、随机身份和私有空间，
禁止 `page.route`、`context.route` 或静态 API fixture。代表性流程覆盖：

1. legacy `/projects/:id` resolver、query/hash 保留、非法 query patch 和无循环；
2. 偏好/onboarding/最近访问/cache 的幂等迁移、坏值 fallback 和跨身份隔离；
3. enabled、baseline、kill/temporarily-disabled、unknown 和 TTL 恢复；
4. owner/admin/member/guest/outsider/enterprise-admin 的 UI/API 最小披露；
5. 高级入口与 mutation gate 不因 rollout 放宽；
6. telemetry allowlist、批量上限、freshness/unknown 和敏感字段拒绝；
7. 四场景入口可达且 rollout/kill 前后业务事实不变；
8. offline、1440/1366/820、键盘、请求预算和无横向溢出；
9. `finally` 恢复 online，关闭额外 context，恢复 rollout，归档空间并下线临时身份。

## 11. M8 Engineering Go 边界

只有以下条件同时成立才可给出 Engineering Go：

- route/preference/onboarding/cache migration 幂等且回滚到 canonical baseline；
- P0/P1 权限、泄漏、开放重定向、数据丢失和不可恢复缺陷为零；
- rollout/kill 恢复演练、低敏 telemetry、性能预算和 S20 四场景回归通过；
- backend、frontend、quality gate 和 real isolated browser 证据均为当前工作循环 fresh；
- 试用环境可恢复，且报告明确自动化没有代签 M8 真人试用。

任一条件不满足即 Reopen M7；不得以“测试规格已编写”或“维护者已浏览页面”替代
实际 fresh 运行证据。

截至本审计事实快照，M8 只有隔离环境、脚本和恢复基线的准备材料，没有真实参与者、
consent、主持执行或真人试用结果。M7 自动化证据不得被解释为 M8 已启动或已完成。
