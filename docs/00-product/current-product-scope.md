---
title: 当前产品范围
status: active
last_code_check: 2026-07-24
---

# 当前产品范围

Colla Platform 当前是一个面向研发团队的轻量协同平台。产品形态以 Web 为主，IM 是核心入口，同时已经具备项目、知识库、表格、审批、通知、搜索和管理后台等模块的 MVP 能力。

当前产品开发主线暂时切换为 `PLATFORM-SCALE` 平台模块化与运行扩展专项。S01-S02 已完成模块边界门禁、四运行角色和双 API 基线，S03 已完成可靠事件 envelope、逐 Handler 投递、lease/fencing、dead letter/replay、双 Worker 分流与接管，以及通知、搜索和 realtime signal 的 Handler 化。S03 完成路线等待归档，建议下一步进入 S04 的通用实时 Gateway 与旧协同收口；该建议尚未激活。`PROJECT-PLATFORM` 已完成 S01-S04，布局、完整配置发布和统一 WorkItem 实例继续属于 S05-S07，并在本轮保持暂停。知识库 `KB-PRODUCT` 工程候选已收口，但 3-5 名真实参与者试用尚未完成，当前按暂停状态归档并等待团队反馈。

## 当前可用入口

当前 Web 路由来自 `web/src/app/router.tsx`：

| 入口 | 路径 | 当前能力 |
| --- | --- | --- |
| 登录 | `/login` | 用户登录，JWT token 写入本地状态 |
| 工作台 | `/` | 仪表盘、未读消息、未读通知、我的事项、审批待办、最近对象 |
| IM | `/im` | 单聊、群聊、会话分组、消息发送、编辑、撤回、置顶、表情、已读、成员管理、链接卡片、消息转事项、会话内搜索定位 |
| 项目空间 | `/project-spaces`, `/project-spaces/:spaceId`, `/project-spaces/:spaceId/members`, `/project-spaces/:spaceId/settings`, `/project-spaces/:spaceId/types/:typeId?`, `/project-spaces/:spaceId/types/:typeId/fields/:fieldId?` | 空间列表和最近入口、创建、协作 Shell、成员与邀请治理、角色调整、Owner 转移、空间设置及停用/恢复/归档；owner/admin 可配置工作项类型和字段，执行侧只展示 active 类型摘要 |
| Legacy 项目 | `/projects`, `/projects/:projectId`, `/issues/:issueId` | 现有项目、事项、需求/任务/BUG、评论、附件、状态动作、分支原因、统计和 BUG 验证记录；仍是 legacy project/issue 业务写路径 |
| 知识库 | `/knowledge-bases`, `/knowledge-bases/:spaceId`, `/knowledge-bases/:spaceId/items/:itemId` | 当前唯一知识内容入口；知识库空间列表、创建、设置、停用、恢复、归档；进入空间默认打开首页内容，左侧目录是内容导航，点击内容页直接进入正文，点击目录展示子内容列表和创建入口；折叠区只保留关注、权限和当前节点等轻量空间设置；知识库内容页在空间上下文内承载块编辑、对象嵌入、评论、版本、权限、分享、关系和知识元数据能力 |
| 表格 | `/bases`, `/bases/:baseId/...` | Base、表、字段、记录、筛选、排序、字段显隐、看板、日历、评论、关联对象、导入导出 |
| 审批 | `/approvals`, `/approvals/:approvalId` | 审批表单、发起、审批、拒绝、转交、撤回、待办和统计 |
| 通知 | `/notifications` | 通知列表、状态/来源/对象筛选、未读数、单条/批量/全部已读、目标跳转 |
| 登录设备 | `/devices` | 个人菜单下的设备列表、撤销设备、登记测试 push token、push probe |
| 搜索 | `/search` | 全局搜索，覆盖事项、知识内容、Base、数据表、表格记录、消息，展示权限解释 |
| 管理后台 | `/admin/overview`, `/admin/departments`, `/admin/user-groups`, `/admin/roles`, `/admin/users`, `/admin/permission-governance`, `/admin/knowledge-bases`, `/admin/app-governance`, `/admin/project-spaces`, `/admin/audit-logs` | 独立后台 Shell；项目空间治理只展示目录、状态、风险边界、生命周期和审计入口，不提供协作内容打开入口；企业 `project.manage` 不自动授予空间内容访问 |

## 用户工作台与管理后台边界契约

UI-SPLIT-M1 冻结第一版双 UI 产品边界，UI-SPLIT-M2 已将前端入口迁移为用户工作台 Shell 与管理后台 Shell，UI-SPLIT-M5 已补充前端组件、导航和样式 token 的职责边界。当前仍是同一个 React SPA 和同一套后端认证，但 `/admin/*` 不再挂在用户工作台左侧主菜单下。

| 界面 | 第一版主入口 | 禁止混入 |
| --- | --- | --- |
| 用户工作台 | 工作台、消息、项目、知识库、表格、审批、通知、搜索、个人菜单、登录设备 | 组织架构、成员管理、用户组、角色权限、权限治理、审计日志、企业级统计、后台治理菜单和默认验收报告 |
| 管理后台 | 企业概览、组织架构、成员管理、用户组、角色权限、权限治理、知识库治理、应用治理、审计日志 | 消息会话、项目事项协作、知识内容正文编辑、Base 数据协作、审批处理、通知消费和用户内容搜索主路径 |
| 共享底座 | 当前用户、权限判定、平台对象、资源 ACL、审计写入、搜索索引、通知投递、文件存储、WebSocket | 直接决定页面信息架构或把后台治理字段塞进用户侧 DTO |

用户工作台的后台入口不再作为左侧主应用菜单项出现。目标交互是：左侧最上方显示头像/用户菜单，点击后展示个人状态、登录设备、帮助、设置、退出等用户项；当且仅当当前用户具备后台权限时，在菜单底部展示“管理后台”入口。普通用户不应看到该入口，也不能通过直接访问后台 API 获取治理数据。

管理后台第一版已经按后台治理视角分组：企业概览、组织与成员、权限与安全、应用配置、内容与数据治理、审计与报表、系统设置。当前可用页面为企业概览、组织架构、成员管理、用户组、角色权限、权限治理、知识库治理、应用治理和审计日志；未接入的系统设置先作为 disabled 治理占位，不跳转到用户真实使用页面。后台出现 Base、项目、消息、审批等名称时，只能是治理、配置、统计、审计或风险排查入口，不能复用用户侧真实使用页面作为后台页面主体。

前端组件边界采用三层：用户侧组件服务内容消费、编辑和协作；后台组件服务组织、权限、安全、审计和配置治理；共享组件只承载头像、状态标签、空状态、卡片和对象入口等基础 UI，不携带用户侧或后台侧业务语义。新增页面必须先判断归属，再选择 `user-*`、`admin-*` 或 `shared` 样式/组件。

API 和 DTO 边界同步采用三层：用户协作 API 返回“当前用户能看什么、能做什么、下一步怎么协作”；管理治理 API 返回“谁能访问、风险来源、审计上下文和治理动作”；共享平台 API 只返回平台对象、权限解释、资源授权、文件等基础原语。新增知识库、Base、项目、消息、审批能力默认属于用户协作 API；只有配置、统计、权限、审计、风险排查和批量治理才能进入 `/api/admin/*`。知识内容 API 已统一到 `/api/knowledge-bases/{spaceId}/items/{itemId}/*`，对外只使用 `KnowledgeBaseItem*` / `KnowledgeContent*` 和 `itemId`；旧 `/api/docs` 与 `Document*` 产品契约已经删除。

UI-SPLIT-M10 后，`/admin/app-governance` 成为 Base、项目、消息和审批的后台治理总入口。该页只展示治理统计、策略、风险、审计深链和权限排查深链；用户侧 `/bases`、`/projects`、`/im`、`/approvals` 仍是协作主路径，不被后台 iframe、嵌入或复用为后台主体页面。

UI-SPLIT-M11 后，权限、审计、搜索、通知和平台对象卡片进入跨边界收口状态。用户侧搜索只返回 `searchScope=user_content` 的协作内容，不暴露权限、审计、组织治理等后台对象；后台治理搜索走 `/api/admin/search-governance` 并受后台权限保护。审计事件会记录来源界面和 API 面，至少区分用户工作台、管理后台、系统任务、API 调用和迁移脚本。个人通知只消费用户协作通知，后台治理通知不得进入普通用户未读数。平台对象摘要支持 user/admin 展示上下文，admin 操作入口只对具备后台权限的用户返回。

UI-SPLIT-M12 冻结“双 UI v1”：当前不拆服务、不拆仓库，但用户工作台和管理后台必须能独立解释、独立导航、独立验证。KB-NAME-M10 已进一步删除旧文档路由、API、前后端模块和 `document` 产品 objectType；知识内容只归入 `knowledgeBases/content`。索引重建已迁入 `/api/admin/search-governance/reindex`，用户搜索入口只保留查询能力。

## 多端产品边界

`/docs` 与 `/docs/:itemId` 已删除并统一落入 404；用户只能通过 `/knowledge-bases` 和带 `spaceId + itemId` 的规范内容路由进入知识内容。

| 端形态 | M38 定位 | 已做能力 | 暂不承诺 |
| --- | --- | --- | --- |
| 桌面 Web | 主交付形态 | 用户工作台、IM、项目、知识库内容、Base、审批、通知、搜索、个人菜单和独立管理后台 | 多窗口协同和系统托盘 |
| 移动 Web | 查看、轻编辑、消息和对象跳转 | 移动顶部菜单、底部主导航、IM/项目/知识库内容/Base 窄屏降级、离线提示 | 原生推送、复杂批量管理 |
| PWA | 可安装的 Web 外壳 | manifest、图标、service worker、离线页和运行时离线横幅 | 后台同步、离线编辑队列 |
| 桌面壳 | 试验性薄壳 | `desktop/electron` 最小入口可加载本地 Web | 正式安装包、自动更新、原生菜单 |
| 原生移动 | 后续阶段 | 通过 API、deep link 和移动 Web 保留演进空间 | M38 不建设 React Native/Expo 客户端 |

## 小团队试运行边界

历史 M40 试运行方案已归档，不能再作为当前准入或数据基线。当前代码已演进到 V052 schema、知识库唯一模型和用户端/管理端双 UI，正式试运行前必须重新建立与当前版本匹配的角色剧本、场景数据、初始化流程、回归清单和 Go/No-Go 标准。

当前只保留以下稳定边界：试运行不得使用过时 M31/M40 重置脚本；真实数据与测试数据必须隔离；回退前必须完成备份和恢复演练；部署与协同恢复按 `docs/05-runbooks/admin-operations.md` 和 `docs/05-runbooks/knowledge-collaboration.md` 执行。协同层已提供双节点重连恢复，但整个平台高可用和集中告警仍不在当前产品化范围内。

## 已实现产品能力

### 身份与团队

- 默认 workspace 和内置管理员初始化。
- 登录、刷新、退出、当前用户查询。
- 管理员维护部门树，支持部门创建、编辑、移动、停用和删除空叶子部门。
- 管理员维护部门成员关系，支持主部门、兼部门、成员移除和部门负责人。
- 管理员创建成员、启用/禁用用户、重置密码，并可在创建成员时设置主部门。
- 成员管理支持部门标签展示和按部门筛选。
- 知识内容资源权限支持成员、部门、用户组和角色作为统一授权主体；权限解释能返回最高权限等级和来源主体。
- 设备登录记录、设备撤销、push token 登记和测试。

### IM

- 支持 `group`、`direct`、`project` 类型会话。
- 支持单聊创建、群聊创建、成员添加/移除、退出群聊、关闭单聊。
- 支持置顶、静音、会话右键菜单、折叠侧栏和置顶/最近会话分组。
- 消息支持 `clientMessageId` 幂等、`messageSeq` 补拉、编辑、撤回、置顶/取消置顶、表情 reaction。
- 粘贴内部链接可解析为对象卡片，覆盖事项、知识内容、Base、审批和消息链接；无权限对象展示明确权限态。
- 支持从消息创建需求、任务或 BUG，创建后事项会自动关联原消息并写入审计和动态。
- 支持当前会话消息搜索、按链接对象类型过滤，并从搜索结果定位到具体消息。
- WebSocket 推送消息、会话、未读和通知变化。

### 项目与 BUG 管理

- S03 已建立空间级工作项类型定义底座：自定义类型支持创建、编辑、复制、排序、停用/恢复和 retire；系统类型支持停用/恢复、排序和复制，禁止改键、覆盖、retire 和删除。每个类型创建时生成一个不可变 published 骨架版本，当前不含动态字段、布局和流程。
- S04-M1 已建立 `project_work_item_field_definitions` 字段定义聚合：字段按 workspace/space/type definition 隔离，`field_key` 在类型内永久唯一；owner/admin 可通过配置 API 创建、更新展示属性、排序、停用/恢复和 retire，member/guest 被拒绝，非成员和仅企业管理员获得隐藏结果。首批 11 类字段能力由服务端目录统一返回，M1 配置只允许规范空对象并生成稳定 SHA-256 hash。
- S04-M2 已建立 `project_work_item_field_options` 和字段聚合配置命令：single/multi select 选项使用不可复用的稳定 key，支持名称、颜色、排序与 active/disabled；text/number/boolean/select/url 的基础默认值与 required、长度、数值范围、精度、regex、格式、允许值规则采用 schema version 1 的结构化 JSON 合同。配置更新按字段 aggregate version 原子提交，并复用 request id、审计、outbox 和六类身份最小披露边界。
- S04-M3 在同一版本化配置 envelope 中增加 `typeConfig`，并由服务端目录返回每类复杂字段的 value schema、type-config schema、引用责任和失效引用策略。user 支持成员/部门/用户组候选范围与数量限制；date/datetime 分离日历日期和 UTC instant，限定展示时区、精度、范围及 today/now 相对默认；URL 限定 http/https、长度、规范化且拒绝凭据和控制字符；attachment 限定数量、MIME、大小并复用文件访问事实；work_item_reference 只配置同空间目标类型、数量和 outbound/deferred 能力。
- S04-M4 已提供 owner/admin 字段配置深链：可按服务端 capability 创建 11 类字段，编辑展示属性，配置稳定选项、类型化默认值、结构化规则及 user/date/datetime/url/attachment/work_item_reference 专属参数，并执行排序、停用/恢复和 retire。目录支持名称/key、状态、字段类型和顺序筛选投影；控件和动作严格由 catalog 与 `availableActions` 驱动，member/guest 不出现配置入口，非成员和仅企业管理员不能通过深链读取私有空间配置。
- S04-M5 已完成 Stage 复核：V001-V065 空库迁移及 V063 升级回放可重复，升级不改写 legacy project/issue，也不产生工作项实例或字段值；配置规模基线为 120 个字段和 2400 个选项的目录查询不超过 3 秒。10 万工作项动态值查询不属于 S04，待 S07 形成规范实例、S13 形成高级查询后验收。
- 复杂字段配置只持久化规范 ID 或标量，不复制用户名称、部门名称、文件元数据、目标类型标题或 URL 凭据作为权限事实。跨 workspace、失效、已删除、不可访问或越界引用统一返回最小披露错误；审计继续只保存配置 hash 和数量摘要。字段定义当前仍是待发布配置图，不会写回 S03 published v1，也没有字段值；发布物化属于 S06，工作项实例和值属于 S07，S07 前 work_item_reference 默认实例数组只能为空。
- 新空间和 legacy 迁移空间在创建事务内安装 `development-v1` 六类研发预置 `project/requirement/task/bug/iteration/release`；既有 active 空间在启动时逐空间幂等补齐。自定义同 key 不被覆盖并进入明确冲突清单，重复和并发补齐不产生重复系统类型或审计事件。
- 空间 owner/admin 使用 `/project-spaces/:spaceId/types` 配置类型；member/guest 只在空间执行首页看到 active 类型名称、key、图标和顺序。企业项目治理只读取类型状态计数，不获得空间配置写权限或内容访问权。
- S02-M1 至 M4 已建立项目空间后端、成员治理和前端闭环：用户“项目”主入口进入 `/project-spaces`，提供最近空间、创建、协作概览、成员治理和空间设置；legacy `/projects` 与 `/issues` 深链继续保留，但不再承担新空间入口。
- 空间设置和成员治理只对 owner/admin 展示；member/guest 进入同一空间 Shell 时只看到执行视角。停用/归档空间关闭写入口并展示明确只读原因，owner/admin 可从设置恢复。
- 空间成员身份与当前角色分离，内置 `owner/admin/member/guest` 能力矩阵可解释；owner/admin 可按角色边界直接加入、调整或移除成员，成员可自行离开，唯一 owner 不允许离开或被停用。成员离职交接会把其唯一 owner 空间交给指定的活动成员。
- 邀请支持创建、重发、撤销、接受、拒绝和自动过期；邀请随机 token 只以 SHA-256 哈希保存且不进入 API DTO、审计或通知。重复邀请、重发和并发接受具有幂等保护，停用空间不能增加成员或邀请。
- 企业 `project.manage` 只允许查看和治理空间状态，不自动授予私有空间内容访问或成员治理能力；用户必须是有效空间成员，才能读取私有空间摘要和成员目录。项目空间已注册为 `project_space` 平台对象，无权解析不泄露名称。
- 管理后台 `/admin/project-spaces` 提供空间筛选、详情、状态治理、权限来源解释和审计深链；后台不渲染空间协作内容，也不提供绕过成员权限的内容入口。
- 迁移治理 API `GET /api/admin/project-migrations/profile` 提供 legacy project 数据画像/预检：数量与角色分布、孤立成员、非法角色、重复 owner 与共享会话、无 owner 项目、IM 双向漂移和缺失会话，仅企业 `project.manage` 可调用且画像动作写审计；该能力只读 legacy 数据，不执行映射写。
- S02-M4 已交付 legacy 到空间的迁移执行：`/api/admin/project-migrations` 支持 dry-run、execute（确认串 `EXECUTE`）、resume、verify、rollback（确认串 `ROLLBACK`）、批次查询和 workspace 收敛验证；project 到 space 使用确定性 ID 与 key 冲突后缀，owner/member/viewer 映射为 owner/member/guest，未知角色与孤立成员只进失败清单；批次 verify 锚定 `summary.manifestProjects` 生命周期归属清单，resume 只更新最近尝试视图 `summary.projects`，不会把本批次已创建产物降格为外部 `REUSED`（缺映射 `MAP_MISSING`、被后继批次取代 `MAP_SUPERSEDED`、dry-run 409），workspace 收敛验证独立于批次且不写批次结论，rollback 只撤销本批次新模型产物并保留原失败清单，legacy project/issue 写路径不切换、无双写，完整 WorkItem 迁移属于 S07。
- 已迁移的 legacy 项目页会展示"已迁移到项目空间"提示条并可跳转新空间；未迁移、映射失败或无权限用户不看到提示，也不泄露空间名称。
- 现有项目运行入口、API 和数据源仍是 `/projects`、`projects/project_members/issues` 与固定 Java workflow；S02 全程没有切换 legacy project/issue 业务写，也没有建立双写；legacy 映射执行能力已由 S02-M4 交付并可按批次 dry-run、执行、校验和回退。
- 项目成员与项目群联动。
- 事项类型包含 requirement、task、bug。
- 事项支持负责人、优先级、截止日期、状态动作、分支原因、处理结论、评论、附件、活动记录。
- 状态动作由后端统一校验，当前覆盖开始处理、退回待处理、标记已解决、关闭、重新处理、信息不足、需求变更、延期、取消需求、重复 BUG、无法复现、提交修复、验证失败和验证通过。
- BUG 支持验证记录；已解决 BUG 验证通过后自动关闭，验证失败自动退回处理中，阻塞验证只记录不自动流转。
- 重复 BUG 可以关联已有 BUG 后关闭；无法复现、信息不足、需求延期/取消等分支会保留结构化原因和结论。
- 项目页面提供统计、看板、列表视图和事项详情抽屉；详情中可执行流程动作并查看关联对象、验证记录和动态。

### 知识库内容

- 知识库支持根目录、目录、内容页、对象入口和外部链接节点，保留在同一棵树中组织；对象入口通过目标类型、目标 ID 和目标路由引用独立协作对象，不复制目标对象数据。
- 知识库空间已有独立入口和轻量空间产品层，基于 `knowledge_base_spaces` 管理名称、编号、封面、状态、可见性、根目录内容节点、首页、维护人和默认权限；旧 `space + knowledge_base` 内容根节点可自动补登记为知识库空间。
- `knowledge_base_spaces` 是知识库空间唯一主模型，`knowledge_base_items` 是目录项唯一主模型；空间根和首页只通过 `root_item_id`、`home_item_id` 关联，不保留旧空间影子字段。
- 新建知识库会自动生成 `首页` 内容页，用户也可以把同知识库内的内容页设为首页；进入知识库空间时优先打开 `homeItemId` 对应正文，左侧目录承担内容导航职责，右侧默认展示当前内容或当前目录的子内容列表。
- 知识库详情页默认不展示统计、节点元数据、治理健康度和 v1 验收报告；空间折叠区只作为轻量空间设置，提供关注、空间权限、节点权限和协同健康等入口。
- `/knowledge-bases/{spaceId}` 只负责解析有效首页并进入规范 `/knowledge-bases/{spaceId}/items/{itemId}` 路由；内容页直接展示正文，目录展示子内容列表，对象入口和外链执行各自唯一主动作。空间管理只从显式辅助入口进入 `view=management`，无效或无权 `itemId` 使用不泄露标题的统一状态页，不静默跳回首页。
- 当前仅知识库空间有 `homeItemId`；非根目录暂无独立默认首页字段，点击目录时展示子内容列表和创建内容入口，目录默认首页能力进入后续目录节点模型里程碑。
- 支持知识库内容树查询、路径面包屑、目录移动、同级排序、归档和恢复。
- 知识库目录节点模型保存 `node_kind`、`target_object_type`、`target_object_id`、`target_route`、`display_mode`、`target_title_strategy` 和 `entry_alias`；本阶段支持手动挂载对象入口和外部链接。对象入口采用保守双权限规则：用户必须同时具备知识库入口可见权限和目标对象访问权限，系统才展示目标标题、摘要和路由。
- 对象入口返回 `targetSummary` 作为权限解释底座，包含目标对象类型、标题、来源模块、路由和访问状态；目标对象无权限、不存在或无效时，知识库只展示安全占位和状态 badge，不泄露原始标题、摘要和路径。
- 多维表格是独立协作对象，不是知识库子模块；知识库目录可以选择已有 Base 或从当前目录新建 Base 并自动挂载入口。点击 Base 入口时，知识库正文区展示只读/轻交互预览和“打开完整表格”入口，Base 数据、表、视图、记录和权限仍归 Base 模块。
- Base 入口可以保存默认数据表和默认视图路由，预览时应用该视图的筛选、排序和可见字段偏好；知识库只保存引用关系和上下文，不复制 Base 数据。
- 支持通过知识库入口创建、编辑、停用、恢复和归档空间；停用后普通成员不能继续在该知识库下新建内容，管理员或 owner/manage 仍可治理和恢复。
- 知识库治理面板、健康度、权限风险、访问统计、热门/低访问内容和搜索无结果词不再放在用户默认内容路径；系统级治理入口已迁入管理后台 `/admin/knowledge-bases`，用户侧仅保留内容协作和轻量空间设置。
- 新建子内容节点会复制父节点内容权限，避免目录树出现无权限幽灵节点。
- 支持结构化 blocks 读取、批量保存、插入、局部更新、重排和删除；版本历史会记录 block 级新增、删除、修改、移动和类型转换摘要。V045 后 active blocks、版本 `block_snapshot`、模板 blocks 和协同 payload blocks 是唯一持久化正文来源。
- Markdown/HTML 导入会投影为 blocks；简单 HTML 转为标题、段落、列表、引用、代码和分割线，复杂结构降级到 `legacy_html`，危险 HTML 只保留安全占位。Markdown/HTML 导出从 blocks 生成，可嵌入对象降级为安全指令或占位，不以目标对象私有标题作为导出依赖。
- 知识库治理健康度补充 block 覆盖缺口、空内容块、失效嵌入对象和 block 覆盖率，迁移检查脚本输出旧富文本覆盖、legacy block、孤儿 block、失效嵌入对象和回滚模板。
- Block 主字段包含稳定块 ID、父块 ID、schema version、attrs、rich content、plain text、锚点 ID、块版本、作者和更新时间，可支撑块级评论、搜索命中、版本快照和后续协同。
- 支持普通表格块，能够在块编辑器内增删行列并保存。
- 支持 Base 视图、事项/BUG、消息、文件和通用平台对象嵌入块；当前嵌入以只读摘要卡片和跳转为主，权限态复用平台对象 resolver。
- 支持知识内容版本、版本 diff、版本恢复。
- 支持知识内容权限、关系、评论和评论提及通知。
- `/api/docs`、后端 `Document*` 产品类、前端 `web/src/modules/docs`、`/docs` 路由和 `document` 产品 objectType 均已删除；不可变历史迁移、归档报告和编辑器 DOM/document model 技术词不属于产品契约。

#### KB-NAME-M1 命名清理契约

- 产品层固定使用“知识库”“知识库目录项”“知识内容”“知识内容块”，不再把“文档”作为独立产品或一级能力名称。
- `KnowledgeBaseItem` 统一表示目录树中的内容、目录、对象引用和外部链接；只有可编辑正文节点才是 `KnowledgeContent`，目录和对象入口不得伪装为正文。
- 目标用户路径固定为 `/knowledge-bases/{spaceId}/items/{itemId}`；目标用户 API 固定归入 `/api/knowledge-bases/{spaceId}/items/*`，后台治理继续归入 `/api/admin/knowledge-bases/*`。
- 当前活动产品契约不再接受 `/docs/{itemId}`、`/api/docs/*`、`Document*` 或 `document` objectType；历史事实只允许出现在不可变迁移和归档证据中。
- M1 基线显示 5 个活动知识库空间、19 个活动目录项、7 个 Markdown 内容和 31 个活动 blocks；blocks 覆盖率为 100%，但 7 个 Markdown 内容仍保留旧 `content` 快照，后续必须在版本、导出、搜索和恢复全部切到 blocks 后再删除。
- 本轮只冻结术语、规范接口和删除门，不改变现有页面行为、权限、分享、评论、版本和协同规则。

### 多维表格 Base

- 支持 Base、Table、Field、Record、View。
- 字段类型支持 text、number、member、date、attachment、single_select、multi_select、status、url、object_link，并在保存时做基础校验。
- 支持记录查询、筛选、排序、字段显隐、保存视图、看板视图、日历视图和记录详情。
- 记录详情支持字段值、评论、关联对象摘要、权限态和最近活动。
- 支持 CSV 导出和小规模 CSV 导入，导入返回成功行数和失败行报告。
- Base/Table/Record 已接入平台对象和搜索。

### 审批

- 支持审批表单、流程节点、审批实例、审批任务、审批动作日志。
- 支持提交、审批、拒绝、转交、撤回。
- 审批事件可写入通知。

### 通知、搜索和平台对象

- 通知支持列表、未读数、状态/来源/对象筛选、单条已读、批量已读、全部已读和来源偏好；个人通知 DTO 带 `notificationScope`，后台治理通知不进入普通用户列表、未读数和已读写操作，资源授权与安全类必要通知不可关闭。
- 通知创建、已读和未读数变化可推送到 WebSocket。
- 全局搜索覆盖事项、知识内容、Base、数据表、表格记录、消息；中文短语和对象编号可通过全文检索与 `ILIKE` 后备匹配召回；知识库范围搜索无结果词会进入治理统计；用户搜索响应固定为 `searchScope=user_content`，后台治理搜索独立为 `/api/admin/search-governance`。
- 搜索结果通过平台对象摘要返回权限态和权限解释；知识内容搜索在召回阶段识别成员、部门、用户组和角色授权，不展示不可访问对象标题、摘要或正文。
- 平台对象支持 summary、navigation、recent、favorite、internal link resolve 和 user/admin 对象卡片；admin 卡片只在当前用户具备后台权限时返回审计、权限排查等治理入口。
- 平台对象支持统一权限解释接口，内部链接卡片可展示不可访问原因和权限来源；权限解释区分用户侧行动建议和后台侧策略来源细节。

## 当前明确 Gap

| 方向 | 当前差距 |
| --- | --- |
| 多端 | M38 已补齐移动 Web 响应式 Shell、核心页面窄屏降级、PWA 基础和 Electron 试验壳；桌面端、移动端仍不是正式产品交付形态 |
| 组织与权限治理 | 已有部门树、主/兼部门、负责人、用户组、角色权限管理、统一资源 ACL 决策、核心模块动作矩阵、授权解释、过期/孤立/失效主体/高风险组合巡检、风险导出、单项修复预览确认和审计快捷跳转 |
| 知识内容协同 | 已有块编辑、表格块、对象嵌入、评论、版本、Hocuspocus/Yjs 多人协同、双节点 Redis 广播、数据库恢复、有限离线队列和 presence；整个平台高可用仍未交付 |
| Base 高级能力 | 已支持对象链接字段和视图字段显隐；尚未实现公式、自动化、字段级权限和记录级权限策略 |
| 通知矩阵 | 已覆盖 IM、项目、知识内容、Base 授权、审批细分和直接权限变更事件，并支持来源偏好与必要通知保护；待 M5-T09/T10 完成集成和端到端验收 |
| 试运行 | M9 已建立 `SIMULATION-READY` 基线；M10 已由 5 个合成人格完成三轮六模块运行、重试幂等、权限拒绝、服务重启恢复、应用静默备份和独立恢复，结论为 `SYNTHETIC-CONTINUOUS-RUN-PASS`。尚无真实用户满意度、学习成本、自然协作节奏和采用意愿证据，进入真实使用前仍需补人工验证 |
| 运维交付 | 本地 Docker 依赖、质量门禁、单后端/双协作节点 Compose、三镜像发布回退、备份恢复脚本、恢复演练和协同运行手册可用；平台级高可用和更完整发布自动化仍未完成 |
| 双 UI 二期 | 双 UI v1 已冻结；后台风险可按级别、规则、资源和主体筛选并跳转审计/成员上下文，后续补系统设置、安全策略中心、应用市场治理和个人设置 |

## 参考

- 产品形态参考：`docs/00-product/references/lark-product-shape-analysis.md`
- 当前路线图：`docs/02-roadmap/current-roadmap.md`
