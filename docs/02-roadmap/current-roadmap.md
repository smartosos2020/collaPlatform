---
title: 用户工作台与管理后台拆分路线图
status: active
scope: user-workbench-admin-console-split
last_code_check: 2026-07-05
source_rule: 本路线图是当前唯一执行路线图；历史路线图只作追溯，不作为执行依据。
remote_rule: 本文件位于 docs/，按 .gitignore 保持本地文档，不进入远程仓库。
---

# 用户工作台与管理后台拆分路线图

本文定义 Colla Platform 从“用户协作 UI 与管理治理 UI 混合”演进为“用户工作台 + 管理后台”双界面、双边界的当前执行路线。

已完成路线归档：

- 类 Lark 知识库 v1 KB-M1 到 KB-M8 已完成，归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-v1-roadmap-completed-2026-07-01.md`。
- 知识库唯一入口与文档模块去冗余 KB-CLEAN-M1 到 KB-CLEAN-M8 已完成，归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-clean-roadmap-completed-2026-07-04.md`。
- 知识库内容体验、对象入口与块编辑器 KB-UX-M1 到 KB-UX-M12 已完成，归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-ux-roadmap-completed-2026-07-05.md`。

## 1. 核心判断

当前项目已经具备知识库、消息、项目、多维表格、审批、搜索、通知、组织架构、用户组、角色权限、权限治理和审计日志等模块，但产品边界仍存在明显混合：

1. 用户工作台和管理后台共用一个 App Shell，左侧主菜单同时承载日常协作入口和管理员入口。
2. 管理后台子页面已经具备 SaaS 后台风格，但仍嵌在用户工作台导航和搜索框架内。
3. 知识库用户侧页面仍可见部分治理、统计、元数据和验收类信息，容易干扰内容阅读和编辑主路径。
4. 后端 API 以模块能力组织，尚未清晰区分用户协作 API、管理治理 API 和内部共享服务。
5. DTO 和权限语义存在混用风险：普通用户所需的“我能看/能编辑”，管理员所需的“谁能看/如何治理/如何审计”，可能通过同一响应模型暴露。
6. Lark 类产品通常以用户工作台服务日常协作，以管理后台服务组织、权限、安全和审计治理，两者是不同信息架构和不同操作心智。
7. 对 Lark Web 的实际观察进一步确认：用户侧主应用栏是消息、会议、日历、云文档、通讯录、邮箱、任务等协作入口；Admin Console 是企业概览、成员与部门、角色、用户组、安全、审计、应用配置和数据治理等后台入口。后台即使出现“消息、云文档、任务”等名称，也属于产品配置、数据统计、权限策略或审计治理，不是用户实际使用入口。

下一阶段唯一方向：保留同一产品和同一后端代码仓库，但在产品、前端路由、页面布局、API 语义、DTO、权限解释和测试门禁上明确拆分“用户工作台”和“管理后台”。

不采用的方向：

- 不把管理后台做成完全独立仓库或独立服务，当前阶段先做清晰边界和可演进分层。
- 不删除现有 `/admin` 能力；先迁移入口、Shell、导航和 API 语义，再逐步清理兼容。
- 不把知识库治理能力塞回用户阅读/编辑主路径；治理入口应进入管理后台、空间设置或显式管理抽屉。
- 不在管理后台复用用户工作台主菜单；管理后台不能展示消息、项目、知识库、表格、审批、通知、搜索等用户使用型菜单。
- 不把后台里的“知识库治理、Base 治理、消息治理、任务治理”做成跳转到用户侧真实页面的入口；它们只能是配置、权限、审计、统计或风险治理视角。
- 不把所有接口物理改名作为第一步；先建立 facade 和 DTO 边界，再迁移调用方，最后清理旧接口。
- 不为追求固定 M8/T08 形式压缩真实工程复杂度；里程碑数量按实际风险和依赖拆分。

## 2. 目标形态

完成后系统应满足：

1. 用户工作台有独立 Shell，服务普通成员的消息、知识库、表格、项目、审批、通知、搜索和个人设置。
2. 管理后台有独立 Shell，只展示后台管理菜单，服务企业概览、组织架构、成员、用户组、角色权限、权限治理、审计日志、应用配置、安全配置、数据治理和内容治理。
3. `/admin/*` 不再只是用户工作台里的一个页面，而是管理后台信息架构的根。
4. 用户工作台默认不展示组织治理、权限审计、全局配置和后台统计，只保留与当前内容协作直接相关的轻量入口。
5. 管理后台默认不承担内容消费和编辑体验，只提供配置、治理、审计、迁移、风险排查和策略设置。
6. 管理后台不出现用户侧主应用菜单；后台中的知识库、Base、消息、任务、审批等能力只能以治理页、配置页、数据页或审计页出现。
7. 后端对外 API 能清楚区分：
   - 用户协作 API：以当前用户视角返回可见、可操作、可协作内容。
   - 管理治理 API：以管理员视角返回组织、权限、审计、风险和配置数据。
   - 内部共享服务：权限判定、平台对象解析、审计写入、搜索索引、通知投递、文件存储。
8. DTO 不跨边界泄露：用户侧 DTO 不夹带后台治理字段；管理侧 DTO 不复用用户阅读模型造成语义不清。
9. 权限判定分为用户动作权限和管理动作权限；管理员身份不会自动改变用户侧内容主路径展示。
10. 知识库、Base、项目、消息等模块的“内容能力”留在用户工作台，“治理能力”进入管理后台或设置入口。
11. 旧链接、旧菜单、旧 API 调用在迁移期有兼容和重定向，不破坏现有可用功能。

## 3. 执行顺序

推荐顺序：

1. UI-SPLIT-M1：现状盘点与边界契约冻结。
2. UI-SPLIT-M2：双 Shell 路由骨架与导航拆分。
3. UI-SPLIT-M3：用户工作台信息架构收口。
4. UI-SPLIT-M4：管理后台信息架构收口。
5. UI-SPLIT-M5：前端组件和页面职责拆分。
6. UI-SPLIT-M6：API 边界与 DTO 分层设计。
7. UI-SPLIT-M7：管理后台 API facade 与权限治理迁移。
8. UI-SPLIT-M8：用户侧 API facade 与内容主路径瘦身。
9. UI-SPLIT-M9：知识库治理从用户页迁移到后台/设置。
10. UI-SPLIT-M10：Base、项目、消息、审批的管理能力归位。
11. UI-SPLIT-M11：权限、审计、搜索和通知跨边界规则收口。
12. UI-SPLIT-M12：兼容清理、全量验证和双 UI v1 冻结。

排序原因：

- 先冻结边界契约，避免一边改 UI 一边争论页面归属。
- 先拆 Shell 和导航，再拆页面，否则页面改造仍会被混合 App Shell 限制。
- 前端页面归属清楚后，再做 API facade 和 DTO 分层，能避免过早大改后端。
- 管理后台 API 先收口，因为组织、权限、审计和治理是最容易泄露到用户侧的能力。
- 用户侧 API 再瘦身，减少对管理字段和治理 DTO 的依赖。
- 知识库治理是当前最明显的混合点，单列里程碑迁移。
- 最后做跨模块治理和兼容清理，确保旧链接、旧接口、权限和审计不断链。

## 4. 类 Lark 验收口径

| 口径 | 验收要求 |
| --- | --- |
| 双 UI 边界 | 用户工作台和管理后台有不同 Shell、导航、信息架构和默认操作心智。 |
| 菜单零重叠 | 管理后台不展示用户侧主应用菜单；用户工作台不展示后台治理菜单。 |
| 后台首版菜单 | 管理后台第一版必须包含企业概览、组织架构、成员管理、用户组、角色权限、权限治理和审计日志。 |
| 用户侧后台入口 | 用户工作台不在主菜单直接展示“管理”；有后台权限的用户通过左侧顶部头像/用户菜单底部的“管理后台”入口进入后台。 |
| 用户侧内容优先 | 普通用户进入知识库、表格、项目、消息时优先看到内容和协作，不被治理面板打断。 |
| 管理侧治理优先 | 管理员进入后台时优先看到组织、权限、审计、风险和配置，不承担日常内容编辑主路径。 |
| 后台产品项语义 | 后台中出现知识库、Base、消息、任务等名称时，只表示配置、统计、权限、审计或治理，不表示用户使用入口。 |
| API 语义清晰 | 用户协作 API 与管理治理 API 的 URL、DTO、权限和错误语义可区分。 |
| DTO 不泄露 | 用户侧响应不夹带后台治理字段，管理侧响应不复用含糊的用户阅读 DTO。 |
| 权限分层 | 用户动作权限、空间管理权限、系统管理权限和超管能力有清晰边界。 |
| 兼容稳定 | 旧 `/admin/*`、旧知识库链接、旧对象链接和现有菜单迁移后不产生 404 或权限绕过。 |
| 渐进迁移 | 每个里程碑都保持系统可运行，不以一次性大拆作为前提。 |

## 5. UI-SPLIT-M1 - 现状盘点与边界契约冻结

目标：盘点当前页面、路由、API、DTO 和权限混合点，冻结第一版用户工作台 / 管理后台边界契约。

Lark 化结果：所有后续拆分都有明确归属表，不再凭页面感觉临时决定放哪里。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M1-T01 | Done | 已盘点 `web/src/app/router.tsx`、`AppLayout` 和 `web/src/modules/*`：当前所有用户路由和 `/admin/*` 共用 `AppLayout`，左侧主菜单直接包含“管理”；归属清单已写入产品、架构和 M1 执行报告。 |
| UI-SPLIT-M1-T02 | Done | 已盘点 Controller/API 前缀：`/api/admin/*` 属管理治理，`/api/workspace`、`/api/conversations`、`/api/projects`、`/api/knowledge-bases`、`/api/bases`、`/api/approvals`、`/api/notifications`、`/api/search` 属用户协作或混合待拆，`/api/platform`、`/api/resource-permissions`、`/api/files`、`/ws/events` 属共享底座。 |
| UI-SPLIT-M1-T03 | Done | 已定义用户工作台边界：工作台、消息、项目、知识库、表格、审批、通知、搜索、设备、个人设置和头像菜单是用户主路径；组织治理、权限治理、审计日志、后台统计不得进入用户主菜单。 |
| UI-SPLIT-M1-T04 | Done | 已定义管理后台边界：第一版菜单为企业概览、组织架构、成员管理、用户组、角色权限、权限治理和审计日志；知识库/Base/项目/消息/审批后续只以治理、配置、统计或审计形态进入后台。 |
| UI-SPLIT-M1-T05 | Done | 已定义共享底座边界：平台对象、权限判定、资源 ACL、审计写入、搜索索引、通知投递、文件存储、WebSocket 和当前用户认证可共享，但 UI DTO 和页面导航不得混用。 |
| UI-SPLIT-M1-T06 | Done | 已输出 DTO 命名和目录规范：用户侧优先 `User*View`/`*Summary`，管理侧使用 `Admin*View`/`Admin*Summary`，写操作使用 `*Command`/`*Request`，内部模型使用 `*Internal`。 |
| UI-SPLIT-M1-T07 | Done | 已输出迁移兼容策略：旧 `/admin/*` 保持 URL 兼容但迁入 Admin Shell；用户侧“管理”主菜单入口迁到头像菜单底部；旧 `/docs/:id`、对象 deep link 和现有 API 先兼容后清理。 |
| UI-SPLIT-M1-T08 | Done | 已同步 `docs/00-product/current-product-scope.md`、`docs/01-architecture/current-architecture.md`、`docs/02-roadmap/current-roadmap.md` 和 `docs/90-reports/m1-execution-report.md`，冻结双 UI 边界契约。 |

验收门：

- 有完整页面归属表。
- 有完整 API/DTO 归属表。
- 有明确禁止事项：哪些管理信息不能出现在用户主路径，哪些内容编辑不能进入后台主路径。

## 6. UI-SPLIT-M2 - 双 Shell 路由骨架与导航拆分

目标：建立用户工作台 Shell 和管理后台 Shell，拆分导航、布局、全局搜索和账号区域。

Lark 化结果：用户侧和管理侧从入口、布局和导航上就是两套界面。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M2-T01 | Done | 新增 `web/src/app/layout/UserWorkspaceShell.tsx`，承载工作台、消息、项目、知识库、表格、审批、通知、设备和搜索等用户侧路由；用户主菜单不再包含“管理”。 |
| UI-SPLIT-M2-T02 | Done | 新增 `web/src/app/layout/AdminConsoleShell.tsx` 和 `AdminOverviewPage`，`/admin/*` 进入独立后台 Shell，菜单覆盖企业概览、组织架构、成员管理、用户组、角色权限、权限治理、审计日志。 |
| UI-SPLIT-M2-T03 | Done | 用户侧左侧顶部新增头像/用户菜单入口；具备后台权限时，菜单底部展示“管理后台”，普通用户由 `canAccessAdmin` 控制不可见。 |
| UI-SPLIT-M2-T04 | Done | `web/src/app/router.tsx` 拆成用户 Shell 和后台 Shell；后台左侧菜单只显示后台治理入口，不复用用户侧消息、项目、知识库、表格、审批、通知、搜索菜单。 |
| UI-SPLIT-M2-T05 | Done | 用户侧全局搜索保留“搜索事项、知识内容、表格、消息”；后台 Shell 搜索改为“搜索成员、部门、用户组、角色、审计”，默认跳转后台审计查询语境。 |
| UI-SPLIT-M2-T06 | Done | 用户侧账号区域放到头像菜单，提供状态、设置、帮助、退出和后台入口；后台侧头部显示管理员身份、返回工作台和退出。 |
| UI-SPLIT-M2-T07 | Done | 新增 `RequireAdmin` 和 `authorization.ts`，按 admin 角色或后台权限码守卫 `/admin/*`；无权限用户跳转 `/error/403`。 |
| UI-SPLIT-M2-T08 | Done | 前端 lint/build、工作循环 checkpoint 和 finish 已通过；浏览器冒烟覆盖工作台入口、后台入口、刷新恢复、头像菜单入口和后台菜单。 |

验收门：

- `/knowledge-bases/*` 和 `/admin/*` 明显使用不同 Shell。
- 管理后台不再像用户工作台里的一个普通内容页面。
- 管理后台菜单与用户工作台菜单没有功能重叠；只有“返回工作台”这类切换入口。
- 无后台权限不能看到后台导航。

## 7. UI-SPLIT-M3 - 用户工作台信息架构收口

目标：清理用户侧页面中的后台治理感，让用户工作台聚焦内容、协作和个人工作流。

Lark 化结果：普通成员进入系统后看到的是工作、内容和协作，而不是配置台。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M3-T01 | Done | 用户侧主导航固定为工作台、消息、项目、知识库、表格、审批、通知、搜索；登录设备降级到头像个人菜单。 |
| UI-SPLIT-M3-T02 | Done | 知识库空间页移除默认治理查询、统计条和治理面板；文档正文页移除默认 v1 验收卡，只保留内容、目录、评论、分享、版本、权限和轻量空间设置。 |
| UI-SPLIT-M3-T03 | Done | Base 用户侧仍保留数据表、视图、记录、筛选、排序、评论、关联对象和协作权限入口；未引入全局权限治理或审计导航。 |
| UI-SPLIT-M3-T04 | Done | 项目用户侧仍保留事项、看板、列表、成员协作、通知和项目内设置；未引入后台组织/权限治理入口。 |
| UI-SPLIT-M3-T05 | Done | 消息和通知用户侧保持会话、提醒、转知识、转任务、筛选和跳转动作；未引入全局审计或治理过滤。 |
| UI-SPLIT-M3-T06 | Done | 用户侧权限不足、对象不可用和知识库目录空状态保持行动导向文案；后台化验收和治理术语不再默认出现在知识内容正文。 |
| UI-SPLIT-M3-T07 | Done | 浏览器冒烟覆盖用户侧工作台、知识库、表格、项目和消息路径，确认不出现组织管理、权限治理、审计日志等后台治理导航。 |
| UI-SPLIT-M3-T08 | Done | 已同步产品范围、架构和执行报告，冻结用户工作台允许入口与展示禁区。 |

验收门：

- 普通用户主路径不出现组织管理、权限治理、审计日志等后台入口。
- 管理员进入用户侧时默认体验与普通用户一致，只多轻量设置入口。

## 8. UI-SPLIT-M4 - 管理后台信息架构收口

目标：把管理后台整理成面向管理员的治理系统，而不是多个零散管理页集合。

Lark 化结果：管理员进入后台后能按组织、权限、安全、审计和应用治理完成配置。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M4-T01 | Done | `AdminConsoleShell` 左侧导航改为企业概览、组织与成员、权限与安全、应用配置、内容与数据治理、审计与报表、系统设置分组；未接入项以 disabled 后台治理占位呈现。 |
| UI-SPLIT-M4-T02 | Done | 企业概览、组织架构、成员管理、用户组、角色权限、权限治理、审计日志均在统一 Admin Shell 下访问，页内旧横向模块切换栏已移除。 |
| UI-SPLIT-M4-T03 | Done | 企业概览页接入现有 admin API，展示组织健康、成员治理、权限风险、审计摘要、待处理治理事项和最近审计。 |
| UI-SPLIT-M4-T04 | Done | Admin Shell 增加统一面包屑、页面标题、分组说明和顶部操作区；页面自身只保留业务操作按钮、筛选和列表。 |
| UI-SPLIT-M4-T05 | Done | 后台顶部搜索文案改为后台治理搜索，权限治理和审计日志继续使用组织、权限、审计专用筛选，不复用用户内容搜索体验。 |
| UI-SPLIT-M4-T06 | Done | 应用配置、内容与数据治理、系统设置只作为 disabled 后台治理占位，不链接到消息、知识库、Base、项目等用户真实使用页面。 |
| UI-SPLIT-M4-T07 | Done | 现有后台列表页继续复用已统一的 SaaS 卡片、分页、滚动和启停/危险操作样式；移除重复导航后可用宽度更稳定。 |
| UI-SPLIT-M4-T08 | Done | 浏览器冒烟覆盖六个现有后台子页面和企业概览，确认 Admin Shell 分组导航可访问且不显示用户工作台主应用菜单。 |

验收门：

- 管理后台具备独立 IA。
- 六个现有管理子页面全部在后台 Shell 内工作。
- 后台 UI 不再依赖用户侧左侧主菜单解释当前位置。

## 9. UI-SPLIT-M5 - 前端组件和页面职责拆分

目标：避免用户侧和后台侧继续共用不合适的页面组件、样式和状态模型。

Lark 化结果：用户侧组件服务内容协作，后台组件服务配置治理，视觉语言可以共享基础 tokens 但不混用页面模式。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M5-T01 | Done | 新增 `frontendModuleBoundaries`，将 `dashboard/messenger/projects/knowledgeBases/docs/bases/approvals/notifications/search/devices` 标为 user，`admin` 标为 admin，`auth/platform/permissions/files/shared` 标为 shared，并记录禁止跨边界职责。 |
| UI-SPLIT-M5-T02 | Done | 抽取 `EntityAvatar`、`StatusBadge`、`SoftBadge`、`TableEmptyState` 和 `content-card` 基础样式；后台成员、组织、用户组、角色页开始复用这些低语义 shared 组件。 |
| UI-SPLIT-M5-T03 | Done | 在全局 CSS 增加用户侧 layout tokens：内容画布、目录树、阅读区、编辑器、评论侧栏和对象入口变量，并给 `UserWorkspaceShell` 内容区挂载 `user-workspace-content`。 |
| UI-SPLIT-M5-T04 | Done | 在全局 CSS 增加后台侧 layout tokens：管理列表宽度、详情卡圆角、筛选条高度、表格行高、治理面板最小高度和设置页/治理页基础卡片类。 |
| UI-SPLIT-M5-T05 | Done | 扫描用户侧模块，未发现 `admin-*` 样式依赖；后台页面已从本地状态/标签/空表格实现迁移到 shared 基础组件，避免复制内容页模式。 |
| UI-SPLIT-M5-T06 | Done | 拆分导航定义为 `userWorkspaceNav.tsx`、`adminConsoleNav.tsx` 和 `navigationBoundaries.ts`，Shell 只负责渲染和交互，不再内嵌页面归属表。 |
| UI-SPLIT-M5-T07 | Done | 新增 `adminViewModels.ts` 和 `userKnowledgeViewModels.ts` 作为前端类型边界占位；新增 admin/user/shared 归属类型，给后续 DTO facade 迁移提供落点。 |
| UI-SPLIT-M5-T08 | Done | 前端 lint/build、浏览器样式冒烟、工作循环 checkpoint 和 finish 通过；执行报告记录剩余可复用组件清单。 |

验收门：

- 页面组件归属清晰。
- 共享组件是基础能力，不携带用户侧或后台侧业务语义。
- 样式类名不再跨边界混用。

## 10. UI-SPLIT-M6 - API 边界与 DTO 分层设计

目标：在不一次性拆服务的前提下，建立用户协作 API 与管理治理 API 的边界。

Lark 化结果：前端调用 API 时，从 URL、DTO 和权限语义就能判断它属于工作台还是后台。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M6-T01 | Done | 已盘点并归类 `/api/workspace`、`/api/conversations`、`/api/projects`、`/api/issues`、`/api/knowledge-bases`、`/api/docs`、`/api/bases`、`/api/admin/*`、`/api/platform`、`/api/resource-permissions`、`/api/search`、`/api/files` 等前缀，分为用户协作、管理治理、共享平台和兼容编辑底座四类。 |
| UI-SPLIT-M6-T02 | Done | 已定义用户协作 API URL 规范：可保留模块前缀，不强制迁到 `/api/workbench/*`，但必须返回当前用户视角的 user view DTO，不默认夹带后台治理字段。 |
| UI-SPLIT-M6-T03 | Done | 已定义管理治理 API URL 规范：新增组织、身份、权限、审计、应用、内容和数据治理能力必须进入 `/api/admin/*`，后台中的知识库/Base/消息/项目/审批只表示配置、统计、权限、审计或风险治理。 |
| UI-SPLIT-M6-T04 | Done | 已冻结 DTO 分层：用户读模型 `User*View`/协作 summary，管理读模型 `Admin*View`/`Admin*Summary`/`*GovernanceView`，写命令 `*Command`/`*Request`，内部模型 `*Internal`/domain model，兼容模型 `Document*`。 |
| UI-SPLIT-M6-T05 | Done | 已定义错误语义：用户侧强调不可见、不可操作、申请权限和下一步动作；后台侧强调策略、来源、风险确认、审计上下文和治理原因；共享平台负责统一对象不可用状态。 |
| UI-SPLIT-M6-T06 | Done | 已定义权限约束：用户动作权限、空间/对象管理权限、后台管理权限和超管能力分层；`/api/admin/*` 必须显式使用后台权限服务或等价 facade 守卫。 |
| UI-SPLIT-M6-T07 | Done | 已输出 API 迁移矩阵：admin 现有接口包 facade，`/api/admin/overview` 新增 facade，知识库治理迁到后台，`/api/docs` 废弃扩展并保留兼容，resource permissions/search 包装分层。 |
| UI-SPLIT-M6-T08 | Done | 已同步产品范围、架构文档、前端 `apiBoundaryRules` 常量和执行报告，冻结 API 分层契约。 |

验收门：

- 有 API 归属和迁移矩阵。
- DTO 命名和响应字段有明确边界。
- 迁移不会要求一次性改完所有 Controller。

## 11. UI-SPLIT-M7 - 管理后台 API facade 与权限治理迁移

目标：优先把组织、成员、用户组、角色权限、权限治理和审计日志统一到后台 API 边界。

Lark 化结果：后台页面只调用后台治理语义 API，不再借用户侧模型解释管理操作。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M7-T01 | Done | 组织架构、成员、用户组、角色权限、权限治理、审计日志继续固定在 `/api/admin/*`，新增 `AdminIdentityDtos`、`AdminPermissionDtos`、`AdminAuditDtos` 作为后台 facade 响应契约。 |
| UI-SPLIT-M7-T02 | Done | `AdminMemberView` 同时保留旧字段并新增 `profile`、`account`、`organization`、`management`、`availableActions`。 |
| UI-SPLIT-M7-T03 | Done | `AdminUserGroupView`、`AdminUserGroupMemberView`、`AdminExpandedUserGroupMemberView` 区分成员展开、授权主体、治理状态和审计快照。 |
| UI-SPLIT-M7-T04 | Done | `AdminRoleView`、`AdminRoleDetailView`、`AdminRoleAssignmentView` 区分角色分类、权限矩阵、内置角色、分配主体和可操作项。 |
| UI-SPLIT-M7-T05 | Done | `AdminPermissionInspectionView`、`AdminPermissionRiskSummaryView` 聚合风险、来源、影响范围、建议动作和审计上下文。 |
| UI-SPLIT-M7-T06 | Done | `AdminAuditLogEntryView` 提供 `actor`、`target`、`context`、`riskTag`、`quickFilters`，保留旧审计字段兼容。 |
| UI-SPLIT-M7-T07 | Done | 复用现有后台 permission service 守卫，并在成员、组织、用户组、角色测试中明确覆盖普通 member 直接调用后台 API 被拒绝。 |
| UI-SPLIT-M7-T08 | Done | 目标集成测试覆盖后台 facade DTO 字段、权限拒绝和旧字段兼容；未执行完整历史 `mvn test`。 |

验收门：

- 管理后台六个现有子页面调用后台语义 API。
- 普通用户直接请求后台 API 被拒绝。
- 旧管理页面功能不回退。

## 12. UI-SPLIT-M8 - 用户侧 API facade 与内容主路径瘦身

目标：让用户侧页面只消费内容协作所需的数据，不夹带后台治理信息。

Lark 化结果：用户侧 API 响应围绕“我能看到什么、能做什么、如何协作”，而不是“系统如何治理它”。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M8-T01 | Done | 已为用户工作台首页、知识库、Base、项目、消息和通知新增用户侧 view DTO facade：`UserWorkspaceDtos`、`UserKnowledgeDtos`、`UserBaseDtos`、`UserProjectDtos`、`UserImDtos`、`UserNotificationDtos`。 |
| UI-SPLIT-M8-T02 | Done | 知识库 `/api/knowledge-bases` 用户侧响应切到 `UserKnowledge*View`，保留空间、目录、内容、评论、分享、版本、对象入口和协作权限；治理端点仍留在显式 governance/admin 路径。 |
| UI-SPLIT-M8-T03 | Done | Base `/api/bases` 用户侧响应切到 `UserBase*View`，保留表、视图、记录、字段、成员协作和可操作提示，未把后台风险或审计上下文放入默认数据表响应。 |
| UI-SPLIT-M8-T04 | Done | 项目 `/api/projects` 与事项 API 响应切到 `UserProject*View`/`UserIssue*View`，保留事项、成员协作、会话关联和用户可执行动作；后台治理继续独立于用户项目主路径。 |
| UI-SPLIT-M8-T05 | Done | 消息和通知响应切到 `UserConversation*View`、`UserMessage*View`、`UserNotificationView`，保留会话、提醒、跳转和协作动作；默认响应不返回组织治理或审计上下文。 |
| UI-SPLIT-M8-T06 | Done | 用户侧权限解释收敛为行动导向文案：知识库/Base 返回可管理、可编辑、可评论、可查看、可申请权限等展示语义，供前端表达“我能做什么”。 |
| UI-SPLIT-M8-T07 | Done | 前端用户侧 API 类型迁移到 user view DTO，同时保留旧类型别名和旧字段兼容，避免一次性改动页面状态和旧调用方。 |
| UI-SPLIT-M8-T08 | Done | `mvn -DskipTests compile`、7 个定向 API 集成测试、`pnpm web:lint`、`pnpm web:build` 和用户侧浏览器冒烟已通过；冒烟覆盖工作台、知识库、表格、项目、消息、通知。 |

验收门：

- 用户侧页面不依赖后台治理字段。
- 用户侧权限文案不暴露后台策略细节。
- 管理员在用户侧看到的仍是用户协作视图。

## 13. UI-SPLIT-M9 - 知识库治理从用户页迁移到后台/设置

目标：把知识库治理、统计、风险和批量管理从普通内容页迁移到管理后台或显式空间设置。

Lark 化结果：知识库用户页像内容空间；知识库管理页像治理控制台。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M9-T01 | Done | 用户侧知识库保留空间列表、内容树、正文、对象入口、评论、版本、权限分享和轻量空间设置；默认内容页不展示治理统计。 |
| UI-SPLIT-M9-T02 | Done | 管理后台新增知识库治理页，按空间列表、启停/恢复/归档、健康度、风险、低访问内容、搜索无结果词、批量复核和审计/导出组织。 |
| UI-SPLIT-M9-T03 | Done | `知识库治理` 接入 `/admin/knowledge-bases`，挂到管理后台 `内容与数据治理` 分组，替换原 disabled 内容治理占位。 |
| UI-SPLIT-M9-T04 | Done | 用户侧保留 owner/manage 的轻量空间设置与内容管理入口，不把 owner 直接带入后台治理面板；管理员在用户侧仍看到内容协作视图。 |
| UI-SPLIT-M9-T05 | Done | 后端新增 `/api/admin/knowledge-bases` facade 和 `AdminKnowledgeBase*View` DTO；治理数据由后台语义接口承载，用户内容主路径不消费该 DTO。 |
| UI-SPLIT-M9-T06 | Done | 权限风险、维护人缺失、低访问内容、搜索无结果词和治理批处理只在后台知识库治理页展示；用户侧知识库列表/正文不默认展示。 |
| UI-SPLIT-M9-T07 | Done | 浏览器冒烟验证：系统管理员后台 `/admin/knowledge-bases` 显示知识库治理；普通用户没有后台入口且直连后台跳 403；用户 `/knowledge-bases` 与具体内容页显示内容树/正文能力且不出现治理风险、访问治理面板。 |
| UI-SPLIT-M9-T08 | Done | 已同步产品范围、架构边界、当前路线图和 `docs/90-reports/m9-execution-report.md`，冻结知识库用户/后台边界。 |

验收门：

- 普通知识库内容页不再默认出现治理/统计仪表盘。
- 管理员仍能在后台完成知识库治理。
- 空间管理和系统后台管理边界清楚。

## 14. UI-SPLIT-M10 - Base、项目、消息、审批的管理能力归位

目标：把其他协作模块中的管理配置和治理能力归位，形成统一后台治理面。

Lark 化结果：各协作对象保留用户侧生产力体验，同时后台具备治理视角。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M10-T01 | Done | Base 用户侧保留 `/bases` 数据协作；后台 `/admin/app-governance?module=base` 登记空间、数据表、记录统计、权限、风险、导入导出策略和模板治理。 |
| UI-SPLIT-M10-T02 | Done | 项目用户侧保留 `/projects` 和 `/issues/*` 事项协作；后台应用治理登记项目成员、权限策略、归档、审计和模板配置。 |
| UI-SPLIT-M10-T03 | Done | 消息用户侧保留 `/im` 会话和 `/notifications` 通知消费；后台应用治理登记留存策略、敏感词、转知识审计和通知策略。 |
| UI-SPLIT-M10-T04 | Done | 审批用户侧保留 `/approvals` 发起和处理；后台应用治理登记流程模板、权限、审计和异常排查。 |
| UI-SPLIT-M10-T05 | Done | 新增 `/admin/application-governance` facade 和 `/admin/app-governance` 页面；后台只展示治理策略、统计、审计/权限深链，不复用用户侧真实页面作为主体。 |
| UI-SPLIT-M10-T06 | Done | 后台应用治理深链进入 `/admin/permission-governance` 和 `/admin/audit-logs`，为后续后台搜索/治理查询提供对象类型、动作和主体筛选入口。 |
| UI-SPLIT-M10-T07 | Done | 深链规则收口：用户侧路由只作为协作入口说明，用户对象卡不新增后台入口；后台治理深链只指向后台权限、审计和策略语义。 |
| UI-SPLIT-M10-T08 | Done | 目标测试、前端 lint/build 和浏览器冒烟覆盖后台应用治理与用户侧 Base/项目/消息/审批入口差异，确认不是同一页面复用。 |

验收门：

- 各协作模块都有用户侧主路径和后台治理归属。
- 无权限用户看不到后台治理深链。
- 旧入口不会直接失效。

## 15. UI-SPLIT-M11 - 权限、审计、搜索和通知跨边界规则收口

目标：收口双 UI 之后最容易出错的横切能力：权限、审计、搜索、通知和平台对象链接。

Lark 化结果：同一个对象在用户侧和后台侧可有不同视图，但权限、安全和审计一致。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M11-T01 | Done | `PermissionActionCategory` 将权限判定统一分为用户动作、对象管理、空间管理、后台管理和超管动作，并由权限解释返回。 |
| UI-SPLIT-M11-T02 | Done | `RequestBoundaryContext` 和审计 enrich 记录 `sourceUi`、`apiSurface`、`client`、`requestPath`，可区分用户工作台、管理后台、系统任务、API 调用和迁移脚本。 |
| UI-SPLIT-M11-T03 | Done | `/api/search` 固定为 `searchScope=user_content` 且过滤后台治理对象；新增 `/api/admin/search-governance` 承载后台治理搜索并受后台权限保护。 |
| UI-SPLIT-M11-T04 | Done | 通知 DTO 增加 `notificationScope`；个人通知列表和未读数排除 `admin_*`、`governance_*` 后台治理通知，避免普通用户接收治理噪音。 |
| UI-SPLIT-M11-T05 | Done | 新增 `/api/platform/objects/{type}/{id}/card?context=user/admin`；admin 上下文只对具备后台权限用户返回治理操作入口。 |
| UI-SPLIT-M11-T06 | Done | 权限解释增加 `presentationContext`、`actionAdvice`、`policySourceDetail`；用户侧只返回行动建议，后台侧可看策略来源细节。 |
| UI-SPLIT-M11-T07 | Done | 新增 `CrossBoundaryRulesIntegrationTests`，覆盖后台 API 越权、用户搜索治理泄露、对象卡后台链接泄露、通知治理噪音隔离和审计来源。 |
| UI-SPLIT-M11-T08 | Done | 同步产品范围、架构文档和 `docs/90-reports/ui-split-m11-execution-report.md`，固定跨边界规则。 |

验收门：

- 用户侧搜索不泄露后台治理对象。
- 后台审计能区分操作来源。
- 对象卡片不会给无权限用户展示后台入口。

## 16. UI-SPLIT-M12 - 兼容清理、全量验证和双 UI v1 冻结

目标：清理迁移期兼容负担，完成双 UI 和 API 边界的全量验证。

Lark 化结果：Colla Platform 具备稳定的用户工作台和管理后台 v1 分层。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| UI-SPLIT-M12-T01 | Done | `docs/90-reports/m12-execution-report.md` 已输出旧路由、旧菜单、旧 API 和旧 DTO 字段兼容清理清单。 |
| UI-SPLIT-M12-T02 | Done | 兼容清理决策表记录 `/docs`、`/api/docs/*`、`Document*`、`documents.content`、知识库治理兼容 API、`/api/search/reindex` 等保留原因、调用方、删除条件和风险。 |
| UI-SPLIT-M12-T03 | Done | 新增并执行 `pnpm smoke:ui-split`，覆盖用户侧工作台、消息、项目、知识库、表格、审批、通知、搜索主入口。 |
| UI-SPLIT-M12-T04 | Done | `pnpm smoke:ui-split` 覆盖管理后台企业概览、组织架构、成员管理、用户组、角色权限、权限治理、知识库治理、审计日志主入口和 Shell 隔离。 |
| UI-SPLIT-M12-T05 | Done | route-final 执行完整 `mvn test`、后端 package、前端 lint/build、安全扫描、迁移顺序和文档契约；首次发现事件队列积压导致通知测试失败，已修复 `DomainEventWorker` 批量 drain 后通过。 |
| UI-SPLIT-M12-T06 | Done | 产品范围、架构、平台对象模型、AI 工程治理和执行报告已同步双 UI v1 冻结边界。 |
| UI-SPLIT-M12-T07 | Done | `docs/90-reports/m12-execution-report.md` 冻结“双 UI v1”验收结论，并列出后台治理搜索、`Document*` 命名迁移、安全策略中心等二期能力。 |
| UI-SPLIT-M12-T08 | Done | 当前路线图标记完成；下一步为提交/推送、归档当前路线图或编排新的阶段路线图。 |

验收门：

- 用户工作台和管理后台可独立解释、独立验证、独立导航。
- 旧链接和旧 API 兼容项都有清理决策。
- 完整 route-final 通过。

## 17. 全局验收标准

1. 不得破坏已完成的组织架构、用户组、权限治理、审计日志和知识库 v2 能力。
2. 不得把用户侧内容主路径改成后台配置体验。
3. 不得把后台治理能力隐藏到普通用户页面里作为唯一入口。
4. 管理后台不得展示用户工作台主应用菜单；用户工作台不得展示后台治理菜单。
5. 管理后台第一版菜单必须固定覆盖企业概览、组织架构、成员管理、用户组、角色权限、权限治理和审计日志；后续扩展项只能以后台治理语义加入。
6. 用户工作台的后台入口必须位于左侧顶部头像/用户菜单的底部，并且只对具备后台权限的用户显示；用户主应用菜单不得直接出现“管理”。
7. 后台产品治理项不得直接复用用户侧真实使用页面作为后台页面主体。
8. 用户侧和后台侧可以共享底层权限判定、平台对象、审计、搜索和通知服务，但不能共享含糊 DTO。
9. 普通用户不能通过前端隐藏入口或直接请求后台 API 获取治理数据。
10. 管理员在用户工作台里默认仍看到用户内容视图，不因管理员身份改变正文主路径。
11. 旧 `/admin/*`、`/knowledge-bases/*`、`/docs/:id`、对象 deep link 和搜索结果必须有兼容策略。
12. 每个里程碑至少补充目标后端测试或明确记录跳过原因；涉及页面主路径的里程碑必须补充前端构建或浏览器验证。
13. 完整 `mvn test`、完整集成测试和全量迁移验证放在 UI-SPLIT-M12 阶段收口执行。
14. 不新增第二份 active 路线图；当前执行入口始终是 `docs/02-roadmap/current-roadmap.md`。

## 18. 下一步入口

UI-SPLIT-M12 已完成，双 UI v1 路线进入收口状态。下一步不应继续在本路线图追加任务；应提交/推送当前改动，或在确认当前路线图可归档后编排新的阶段路线图。新的路线图建议从以下方向选择：兼容命名清理、后台治理搜索深化、安全策略中心、用户个人设置完善、真实团队试运行反馈修复。

执行前必须先读取：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/02-roadmap/current-roadmap.md`
5. `docs/03-engineering/ai-engineering-governance.md`

阶段验证策略沿用 AI 工作循环：每轮优先做本轮影响范围内的目标测试、lint/build 或浏览器验证；完整 `mvn test`、完整集成测试和全量迁移验证放在阶段收口里程碑执行。
