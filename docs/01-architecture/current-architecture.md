---
title: 当前技术架构
status: active
last_code_check: 2026-06-30
---

# 当前技术架构

本文档按当前代码现实整理。历史长版技术设计已归档到 `docs/99-archive/old-drafts/phase1-technical-design.md`。

## 总体形态

当前架构是单体后端 + Web 前端 + 本地 Docker 依赖：

- 后端：Spring Boot 单体，按模块包组织。
- 前端：Vite + React 单页应用，按业务模块拆分路由和 API client。
- 数据：PostgreSQL + Flyway。
- 实时：Spring WebSocket，统一入口 `/ws/events`。
- 对象存储：MinIO。
- 缓存/预留实时基础：Redis。

## 后端模块

当前 `server/src/main/java/com/colla/platform/modules` 下的模块：

| 模块 | 主要职责 |
| --- | --- |
| `identity` | 登录、JWT、成员、组织架构、管理员用户、设备 |
| `workspace` | 工作台 dashboard |
| `im` | 会话、成员、消息、未读、消息操作、链接卡片、消息上下文定位、会话内消息搜索和链接摘要结果展示 |
| `project` | 项目、事项、状态动作、工作流分支、评论、附件、活动、BUG 验证、事项关联、项目通知和审计 |
| `doc` | 知识库空间产品层、知识库根目录/目录/内容页/对象入口/外部链接、知识库治理统计、树形目录、移动/排序、归档/恢复、blocks、普通表格块、平台对象嵌入块、块编辑、版本、选区/块/全文评论线程、权限、关系；包名和 `Document*` 类名保留历史兼容 |
| `base` | Base、Table、Field、Record、View、记录详情、评论、关联对象、看板、日历、权限、导入导出和平台对象分享 |
| `approval` | 审批表单、实例、任务、动作 |
| `notification` | 站内通知、未读、单条/批量/全部已读、来源和对象筛选、实时推送 |
| `platform` | 平台对象、内部链接、最近访问、收藏、权限解释 |
| `search` | 搜索索引、搜索 API、中文/编号后备匹配、索引重建 |
| `permission` | 统一资源权限决策、授权主体展开、解释模型、角色权限管理、权限码风险元数据、资源权限管理和权限治理风险巡检，支持知识库维度筛选 |
| `event` | domain events 和 worker |
| `audit` | 管理审计日志查询 |
| `file` | 文件上传完成、下载 URL、文件元数据 |

## Web 前端模块

当前 `web/src/modules` 下的业务模块：

| 模块 | 页面/能力 |
| --- | --- |
| `auth` | 登录、认证状态 |
| `dashboard` | 工作台 |
| `messenger` | IM 页面 |
| `projects` | 项目和事项页面 |
| `docs` | 知识库内容页底座；`DocsPage` 同时承载 `/knowledge-bases/:spaceId/items/:docId` 和 `/docs/:docId`，保留编辑器、评论、版本、权限、分享、关系和协同能力，`/docs` 根路径不再作为用户入口 |
| `knowledgeBases` | 知识库唯一产品入口：空间列表、创建、设置、状态操作、内容优先空间入口、首页、目录导航、目录子内容列表、对象入口、外部链接、模板入口、轻量空间设置、移动/排序操作和内容页跳转 |
| `bases` | 表格页面 |
| `approvals` | 审批页面 |
| `notifications` | 通知页面 |
| `devices` | 设备页面 |
| `search` | 搜索页面和权限态提示 |
| `admin` | 组织架构页面、用户组页面、角色权限页面、权限治理页面、用户管理页面、审计日志页面 |
| `platform` | 对象卡片、内部对象链接、对象 API |

Web 路由已使用 `lazyRoute` 懒加载，构建门禁检查 route lazy-loading。UI-SPLIT-M2 后，路由树在认证根下拆分为 `UserWorkspaceShell` 与 `AdminConsoleShell`：用户侧承载协作入口，后台侧承载 `/admin/*` 治理入口。

## 用户工作台与管理后台拆分契约

UI-SPLIT-M1 冻结的是边界契约，不是物理拆仓库或拆服务。当前阶段仍保持单体后端、单一 Web 代码线和同一认证体系；UI-SPLIT-M2 已在同一 SPA 内拆出 `UserWorkspaceShell` 和 `AdminConsoleShell`，后端继续在同一 Spring Boot 单体内通过 URL、DTO、权限和 facade 语义区分用户协作 API 与管理治理 API。

UI-SPLIT-M12 后，双 UI v1 的冻结边界是：继续保留单体后端、单一 Web 代码线和共享认证，但用户工作台、管理后台、共享平台 API 必须在路由、DTO、错误语义、权限语义和验证资产上独立表达。任何新增功能先判断归属；用户协作能力不得进入 `/api/admin/*`，后台治理能力不得复用用户真实使用页面作为主体。

前端归属边界：

| 类型 | 当前路由/模块 | 后续约束 |
| --- | --- | --- |
| 用户工作台 | `/`, `/im`, `/projects`, `/knowledge-bases`, `/bases`, `/approvals`, `/notifications`, `/search`；`/devices` 作为个人菜单入口；模块为 `dashboard`、`messenger`、`projects`、`knowledgeBases`、`docs` 编辑底座、`bases`、`approvals`、`notifications`、`devices`、`search` | 用户侧主菜单只承载日常协作和个人工作流；后台入口放在左侧顶部头像/用户菜单底部，仅对有后台权限用户可见。 |
| 管理后台 | `/admin/*`；当前模块为 `admin`，现有页面包括企业概览、组织架构、成员管理、用户组、角色权限、权限治理、知识库治理、应用治理、审计日志 | 使用独立 `AdminConsoleShell`；第一版后台 IA 按企业概览、组织与成员、权限与安全、应用配置、内容与数据治理、审计与报表、系统设置分组，未接入分组仅显示 disabled 治理占位，不复用用户侧真实使用页面。 |
| 共享前端底座 | `auth`、`platform`、`permissions`、`files`、`shared/api`、`shared/components` 和基础 UI 样式 | 可共享认证、HTTP client、对象卡片、权限提示、头像、状态 badge、空状态和基础卡片；不得让用户侧页面直接消费后台视图模型，反之亦然。 |

后台访问守卫由 `RequireAdmin` 和 `canAccessAdmin` 实现。当前判定规则是：用户拥有 `admin` 角色，或拥有 `admin.access`、`user.manage`、`org.view`、`org.manage`、`usergroup.view`、`usergroup.manage`、`role.view`、`role.manage`、`permission.inspect` 任一后台权限码，才可进入 `/admin/*`；否则跳转 `/error/403`。

后端归属边界：

| 类型 | API/服务 | 后续约束 |
| --- | --- | --- |
| 用户协作 API | `/api/workspace`、`/api/conversations`、`/api/projects`、`/api/issues`、`/api/knowledge-bases` 的内容主路径、`/api/bases`、`/api/approvals`、`/api/notifications`、用户侧 `/api/search` | 返回当前用户可见、可操作、可协作的数据；不得默认夹带后台治理字段和企业级统计。 |
| 管理治理 API | `/api/admin/users`、`/api/admin/departments`、`/api/admin/user-groups`、`/api/admin/roles`、`/api/admin/permissions`、`/api/admin/role-assignments`、`/api/admin/permission-governance`、`/api/admin/knowledge-bases`、`/api/admin/application-governance`、`/api/admin/audit-logs`、`/api/admin/overview` | 必须校验后台访问权限；返回组织、权限、风险、审计、配置和治理视图；普通用户直接请求应拒绝。 |
| 共享内部服务 | 权限判定、资源 ACL、平台对象解析、审计写入、搜索索引、通知投递、文件存储、WebSocket、认证当前用户 | 可以被用户侧和后台侧共同调用，但对外 DTO 必须在各自 API 层重新表达，避免含糊共享模型泄露边界。 |

DTO 命名和目录约束：用户侧读模型优先使用 `User*View`、`*Summary` 或模块内明确的协作视图；管理侧读模型使用 `Admin*View`、`Admin*Summary` 或 `*GovernanceView`；写操作保持 `*Command`/`*Request`；内部共享模型使用 `*Internal` 或 domain model，不直接暴露给页面。旧 DTO 可在迁移期保留，但新增字段必须先判断属于用户协作、管理治理还是内部共享。

前端组件和样式约束：导航定义集中在 `web/src/app/navigation/userWorkspaceNav.tsx`、`adminConsoleNav.tsx` 和 `navigationBoundaries.ts`；共享低语义 UI 位于 `web/src/shared/components`；用户侧 layout token 使用 `--user-*` 和 `user-*` 类名，后台侧 layout token 使用 `--admin-*` 和 `admin-*` 类名。用户侧模块不得引用后台页面样式，后台页面不得复用知识内容编辑/阅读类作为后台布局。

M38 多端基础保持单一 Web 代码线：

- 全局 Shell 在窄屏下切换为顶部菜单按钮、抽屉导航和底部五入口主导航，优先保留 IM、项目、知识库、Base、通知路径。
- IM、项目、知识库内容和 Base 页面通过 CSS 响应式降级为单列/横向滚动/紧凑详情布局，覆盖查看、轻编辑、对象跳转和消息输入。
- Web 提供 `manifest.webmanifest`、PWA 图标、`sw.js` 和 `offline.html`；service worker 只缓存同源静态资源和页面兜底，不缓存 `/api/` 响应。
- `desktop/electron` 是试验性 Electron 薄壳，默认加载 `http://localhost:5173` 或 `COLLA_WEB_URL`，仅用于验证桌面壳边界，不纳入主构建。

## 数据库迁移

当前迁移为 `V001` 到 `V043`：

| 范围 | 迁移 |
| --- | --- |
| workspace/identity | `V001`, `V005`, `V006`, `V034`, `V035` |
| platform object/permission/event/audit | `V002`, `V003`, `V004`, `V007`, `V036`, `V037` |
| IM | `V008`, `V013`, `V017`, `V018`, `V019` |
| project/issue | `V009`, `V016`, `V022`, `V023`, `V027` |
| document | `V010`, `V020`, `V024`, `V025`, `V026`, `V029`, `V030`, `V031`, `V032`, `V033` |
| base | `V011`, `V012` |
| search | `V013`, `V021` |
| approval | `V014` |
| ops/index | `V015` |

当前新增的组织架构迁移包括：

- `V034__create_departments_and_org_permissions.sql`：新增部门树、部门成员关系、部门负责人表，并新增 `org.view`、`org.manage` 权限码授予内置 admin 角色。
- `V035__create_user_groups_and_document_subject_permissions.sql`：新增用户组、用户组成员表，新增 `usergroup.view`、`usergroup.manage` 权限码，并把文档权限扩展为 `subject_type/subject_id` 主体模型以支持用户组授权。
- `V036__extend_resource_permissions_for_unified_decisions.sql`：扩展通用 `resource_permissions`，记录来源、过期时间和状态；支持 user、department、user_group、role 四类主体和 owner/manage/edit/comment/view 五级权限，并把现有文档权限回填到通用 ACL。
- `V037__create_role_assignments_and_permission_metadata.sql`：扩展 `permissions` 元数据，新增风险等级、说明、内置标识和排序；新增 `role_assignments`，支持角色分配给成员、部门、用户组，并新增 `role.view`、`role.manage`、`permission.inspect` 权限码。

当前新增的项目迁移包括：

- `V022__create_issue_verification_logs.sql`：BUG 验证记录表。
- `V023__extend_issue_relations_and_verification_fields.sql`：BUG 验证环境、复现步骤、修复版本字段，以及 `issue_relations` 跨对象关联表。
- `V027__extend_issue_workflow_state.sql`：为事项补充 `workflow_reason`、`workflow_note`、`resolution`、`resolved_at` 和 `closed_at`，用于需求/BUG 分支产品化。

当前新增的文档迁移包括：

- `V024__extend_document_comments_for_block_resolution.sql`：文档评论绑定 block、解决时间和解决人字段。
- `V025__extend_document_tree_metadata.sql`：为内容树补充 `sort_order` 和 `archived_at`，用于同级排序和归档恢复。
- `V026__extend_document_block_types.sql`：扩展 `document_blocks.block_type` 约束，支持表格块和平台对象嵌入块。
- `V029__create_document_collaboration_states.sql`：保存协同 state vector、snapshot payload、server clock 和自动保存时间。
- `V030__extend_document_comment_threads.sql`：把文档评论升级为 thread/reply/anchor 模型，兼容旧评论为 root thread，并记录选区 range、摘录、前后文和 reopen 信息。
- `V031__extend_document_sharing_permissions.sql`：扩展文档权限等级为 `owner/manage/edit/comment/view`，新增权限来源、知识库空间字段和 `document_share_links`。
- `V032__extend_document_versions_templates_import.sql`：扩展文档版本类型、命名版本、来源版本和块快照，并新增文档模板表。
- `V033__extend_document_blocks_v2_metadata.sql`：为文档块增加 `schema_version`、`attrs`、`rich_content`、`plain_text`，扩展 block v2 类型并保留旧 `content` 兼容字段。
- `V038__create_knowledge_base_spaces.sql`：新增 `knowledge_base_spaces` 聚合表，把旧 `documents.doc_type='space' and knowledge_base=true` 映射为知识库空间，root/home 继续指向原文档节点。
- 知识库空间元数据以 `knowledge_base_spaces` 为唯一主模型；`documents.doc_type='space'` 和 `documents.knowledge_base` 仅用于内容树根、历史链接和旧 API 兼容，不再作为新增知识库功能的扩展点。新建知识库根文档使用“根目录”内容节点语义，根文档上的 `description`、`cover_url`、`default_permission_level` 不再承载空间设置。
- `V039__create_resource_permission_requests.sql`：为无权限申请和后续权限工作流保留资源申请记录。
- `V040__extend_documents_for_knowledge_maintenance.sql`：为文档补充维护人、标签、分类、知识状态、复核日期和知识库模板范围。
- `V041__extend_knowledge_search_discovery.sql`：为搜索索引补充 knowledge base、parent、目录路径、标签、维护人、知识状态和命中来源，并新增知识订阅表。
- `V042__extend_documents_for_knowledge_object_entries.sql`：在 `documents` 兼容树上增加 `node_kind`、`target_object_type`、`target_object_id`、`target_route`、`display_mode`、`target_title_strategy`、`entry_alias`，用于知识库目录挂载对象入口和外部链接。
- `V043__extend_document_blocks_for_kb_ux_schema.sql`：为 `document_blocks` 增加父子关系、稳定锚点、块版本和可迁移约束，支撑 block 级版本摘要、搜索定位、评论定位、导入导出和迁移检查。

当前新增的 Base 迁移包括：

- `V028__extend_base_deep_collaboration.sql`：扩展 Base 字段类型约束，给保存视图增加 `visible_field_ids`，并新增记录评论、记录关系和记录活动表。

## API 边界

UI-SPLIT-M6 冻结 API 边界规则：当前不物理拆分后端服务，也不一次性重命名所有接口；先按 URL、DTO、错误语义和权限语义建立 facade 边界，再由 M7/M8 逐步迁移调用方。

API 边界分类：

| 边界 | 前缀 | 当前归属 | DTO 规则 | 权限规则 | 迁移动作 |
| --- | --- | --- | --- | --- | --- |
| 用户协作 | `/api/workspace` | 工作台首页 | `UserWorkspace*View` 或 workspace module summary | 当前用户个人工作状态和可见对象 | 保留 |
| 用户协作 | `/api/conversations` | IM 会话和消息 | `Conversation*`、`Message*`，后续 facade 使用 `UserMessage*View` | 会话成员、消息动作权限 | 保留 |
| 用户协作 | `/api/projects`, `/api/issues` | 项目和事项 | `Project*`、`Issue*`，后续 facade 使用 `UserProject*View` | 项目成员、事项工作流动作 | 保留 |
| 用户协作 | `/api/knowledge-bases` 内容主路径 | 知识库空间、目录、正文入口、模板、导入导出、discovery | 用户侧使用 `UserKnowledge*View`；不得默认返回治理统计和企业风险字段 | 空间成员、内容 ACL、空间管理动作 | 包 facade |
| 兼容/编辑底座 | `/api/docs` | 知识内容编辑、块、评论、版本、协同和旧 deep link | `Document*` 只作为编辑器/兼容 DTO；新知识库产品 DTO 必须包一层 user/admin view | 文档 ACL 与编辑器动作权限 | 废弃扩展、保留兼容 |
| 用户协作 | `/api/bases`, `/api/base-records` | 多维表格、记录、视图、评论和导入导出 | `Base*`、`BaseRecord*`，后续 facade 使用 `UserBase*View` | Base ACL、记录编辑权限 | 保留 |
| 用户协作 | `/api/approvals` | 审批发起、待办、处理和统计 | `Approval*` 用户流程 DTO | 申请人、审批人、参与人权限 | 保留 |
| 用户协作 | `/api/notifications` | 个人通知、未读数、批量已读 | `Notification*` 个人 DTO；返回 `notificationScope=user_collaboration` | 当前用户通知所有权；排除后台治理通知 | 保留 |
| 用户协作 | `/api/search` 用户查询 | 全局用户搜索 | `Search*` 当前用户结果 DTO；返回 `searchScope=user_content` | 结果必须按当前用户可见性过滤，且不得返回后台治理对象 | 包 facade |
| 管理治理 | `/api/admin/users` | 成员管理 | `AdminMember*View`、`AdminUser*Summary` | `admin` 或 `user.manage` | 包 facade |
| 管理治理 | `/api/admin/departments` | 组织架构 | `AdminDepartment*View`、`AdminOrg*Summary` | `org.view`/`org.manage` | 包 facade |
| 管理治理 | `/api/admin/user-groups` | 用户组治理 | `AdminUserGroup*View`、`AdminUserGroupMember*View` | `usergroup.view`/`usergroup.manage` | 包 facade |
| 管理治理 | `/api/admin/roles`, `/api/admin/permissions`, `/api/admin/role-assignments` | 角色权限和分配 | `AdminRole*View`、`AdminPermissionMatrixView`、`AdminRoleAssignment*View` | `role.view`/`role.manage`/`permission.inspect` | 包 facade |
| 管理治理 | `/api/admin/permission-governance` | 权限排查、风险巡检、风险导出 | `PermissionGovernanceView`、`AdminPermissionRiskView` | `permission.inspect` | 保留并强化 |
| 管理治理 | `/api/admin/knowledge-bases` | 知识库空间治理、健康度、访问低效、搜索无结果词、风险和批量治理 | `AdminKnowledgeBase*View`、`AdminKnowledgeBaseGovernanceView` | 后台访问权限，治理动作复用空间 manage/owner 与后台权限兜底 | 新增 facade |
| 管理治理 | `/api/admin/application-governance` | Base、项目、消息、审批的治理总览、策略、风险、审计和权限排查深链 | `AdminApplication*GovernanceView` | 后台访问权限；不暴露用户协作页面主体 | 新增 facade |
| 管理治理 | `/api/admin/search-governance` | 后台治理搜索，覆盖权限、审计、应用、知识库和身份治理入口 | `AdminGovernanceSearch*`；返回 `searchScope=admin_governance` | 后台访问权限；普通用户直接请求拒绝 | 新增 facade |
| 管理治理 | `/api/admin/audit-logs` | 审计查询 | `AdminAuditLogView` | 后台访问权限和审计查询权限 | 保留并强化 |
| 管理治理 | `/api/admin/overview` | 企业概览目标 facade | `AdminOverviewView` | 后台访问权限 | 新增 facade |
| 共享平台 | `/api/platform` | 平台对象、内部链接、权限解释、最近/收藏、user/admin 对象卡 | `PlatformObject*`、`PlatformObjectCard` 和 `PermissionExplanation` 基础 DTO；由 `presentationContext` 区分用户/后台展示 | 对象 resolver + resource permission decision；admin 卡片动作必须校验后台权限 | 保留 |
| 共享平台 | `/api/resource-permissions` | 通用资源授权、申请、继承 | `ResourcePermission*` 基础 DTO；用户/后台各自包装展示 | resource manage/share 权限；后台 inspect 只进 admin facade | 包 facade |
| 共享平台 | `/api/files` | 文件上传、完成、下载 URL | `FileMetadata`、上传/下载命令 | 目标对象权限或文件所有权 | 保留 |
| 共享身份 | `/api/auth`, `/api/devices` | 登录、当前用户、退出、登录设备 | `CurrentUser`、`Device*` | 当前用户身份和设备所有权 | 保留 |
| 系统健康 | `/api/health`, `/actuator/health` | 健康检查 | health DTO/text | 公开或运维级读取 | 保留 |

开发边界：

- `/api/knowledge-bases` 和 `web/src/modules/knowledgeBases` 是知识库产品能力的唯一扩展入口；`/api/docs`、`web/src/modules/docs`、`Document*` 类和 `documents` 表属于知识内容编辑底座与历史兼容层。
- 新增用户协作 API 可以保留模块前缀，不强制迁到 `/api/workbench/*`；但响应必须是当前用户视角的 user view DTO，不能默认夹带后台治理字段。
- 新增管理治理 API 必须在 `/api/admin/*` 下，不能跳转或代理到用户真实使用页面；后台中出现知识库、Base、消息、项目、审批等名称时，只能表示配置、统计、权限、审计或风险治理。
- 共享平台 API 只能返回基础对象、权限解释、文件和授权原语；是否展示为用户体验或后台治理，由 user/admin facade 决定。
- 横切能力必须带边界语义：审计写入需记录 `sourceUi` 和 `apiSurface`；用户搜索只能返回 `user_content`；后台治理搜索只能走 admin facade；个人通知列表和未读数不得混入 `admin_*`、`governance_*` 通知；平台对象 admin 操作入口不得给无后台权限用户返回。

DTO 分层规则：

| 层级 | 命名 | 字段语义 | 禁止事项 |
| --- | --- | --- | --- |
| 用户读模型 | `User*View`、模块内 `*Summary`、`*Detail` | 当前用户可见、可操作、可协作内容；强调内容、状态和下一步动作 | 默认返回组织全量、权限风险、审计上下文、后台可操作项 |
| 管理读模型 | `Admin*View`、`Admin*Summary`、`*GovernanceView` | 组织、身份、权限、策略、来源、风险、审计和治理动作 | 复用用户正文阅读模型解释后台治理页面 |
| 写命令 | `*Command`、`*Request`、动作专用 payload | 明确一个用户动作或后台治理动作；字段按命令最小化 | 直接复用读 DTO 作为写入模型 |
| 内部模型 | `*Internal`、domain model、repository row | 服务内部状态、表结构、聚合计算和迁移中间态 | 直接暴露给前端页面或跨边界共享 |
| 兼容模型 | `Document*`、旧链接 DTO | 保证旧链接、编辑器、块、评论、版本和导入导出不断链 | 承载新增知识库产品能力或后台治理能力 |

错误语义：

| 边界 | 401/403 | 404/410 | 409/422 | 错误展示重点 |
| --- | --- | --- | --- | --- |
| 用户协作 | 当前内容不可见、不可操作；优先给申请权限、返回列表、打开父级等行动 | 对象不存在、已删除、已归档或链接失效 | 当前状态下无法执行协作动作，例如工作流状态不允许 | “我为什么看不到/不能做，下一步能做什么” |
| 管理治理 | 缺少后台权限、缺少具体管理权限或超管权限 | 被治理对象不存在、已被删除或不在当前组织 | 策略冲突、风险未确认、对象仍有关联不能删除 | “哪条策略阻止、来源是什么、是否写审计、如何排查” |
| 共享平台 | 对象 resolver 判定不可见或动作权限不足 | 内部链接无效、对象类型未知、对象已删除 | 权限层级、主体类型、继承关系不合法 | 统一对象状态和权限解释，避免泄露不可见对象细节 |
| 兼容编辑底座 | 旧 deep link 可见性和文档 ACL | 旧文档 ID 不存在或迁移后不可用 | 块版本冲突、导入导出不支持、协同状态冲突 | 保证旧链接和编辑器可恢复，提示进入知识库上下文 |

权限约束：

| 权限面 | 现有入口 | M11 冻结规则 |
| --- | --- | --- |
| 用户动作权限 | `PermissionDecisionService.decide(currentUser, objectType, objectId, action)`、模块内成员/参与人校验 | 用于 view/comment/edit/manage、事项流转、审批处理、消息动作等用户协作动作；`PermissionActionCategory=user_action`；管理员身份不改变用户侧默认内容体验。 |
| 空间/对象管理权限 | 文档/Base/project resource ACL、知识库空间 owner/manage | 用于分享、授权、空间设置、目录移动、归档恢复等对象级管理动作；`PermissionActionCategory=object_management/space_management`；不等同系统后台权限。 |
| 后台管理权限 | `PermissionService.requireManageUsers`、`requireViewOrganization`、`requireManageRoles`、`requireInspectPermissions` 等 | `/api/admin/*` 必须显式调用后台权限服务或等价 facade 守卫；`PermissionActionCategory=admin_management`；仅拥有内容 manage 不代表可进入后台。 |
| 超管能力 | `currentUser.hasRole("admin")` | `PermissionActionCategory=super_admin`；只作为后台权限兜底和系统初始化兜底，具体页面仍应呈现具体管理权限语义。 |

API 迁移矩阵：

| 范围 | 当前状态 | 目标状态 | 动作 | 清理条件 |
| --- | --- | --- | --- | --- |
| `/api/admin/users/departments/user-groups/roles/permissions/audit-logs` | 已在 admin 前缀下 | DTO 明确为 `Admin*`，权限错误带治理语义 | 包 facade | M7 完成后台 facade 和字段命名后清理旧含糊类型名 |
| `/api/admin/permission-governance` | 已具备后台治理语义 | 增强风险、来源、影响范围、建议动作和审计上下文 | 保留并强化 | M7 权限治理 DTO 冻结 |
| `/api/admin/overview` | 前端聚合多个 admin API | 后端专用企业概览 facade | 新增 facade | M7/M10 后台概览服务落地 |
| `/api/knowledge-bases/{spaceId}/governance*` | 兼容旧调用和历史测试 | 治理主入口迁入 `/api/admin/knowledge-bases/*`，用户页不默认消费治理 DTO | 保留兼容、冻结新增 | M9 已完成治理迁移，用户页只保留内容协作和轻量空间能力 |
| `/api/knowledge-bases` 内容主路径 | 已是知识库产品入口 | 用户侧 `UserKnowledge*View` 包装 `Document*` 编辑底座 | 包 facade | M8 用户侧 DTO 瘦身完成 |
| `/api/docs` | 编辑器和历史兼容承载过多能力 | 保留为编辑底座和旧链接兼容，不再扩展产品功能 | 废弃扩展 | M12 旧 deep link、旧字段和兼容入口审计完成 |
| `/api/resource-permissions` | 共享授权原语被用户/后台共同使用 | 用户侧封装为分享/申请，后台侧封装为治理/排查 | 包 facade | M11 权限、审计、通知跨边界规则收口 |
| `/api/search` | 用户搜索并含部分管理索引重建能力 | 用户搜索保留；管理排查进入 `/api/admin/search-governance` 或后台治理 facade | 迁移调用方 | M11 搜索边界收口 |
| `/api/bases/projects/conversations/approvals/notifications` | 用户协作主路径 | 保留模块 API，必要时新增 `User*View` facade | 保留 | M8 用户侧 DTO 瘦身完成 |

M12 兼容清理冻结决策：

| 兼容项 | 决策 | 删除条件 |
| --- | --- | --- |
| `/docs`、`/docs/:docId` | 保留为重定向、历史 deep link 和分享兼容入口 | 平台对象、通知、搜索、分享和外部链接全部迁移到知识库主路径，并有访问日志证据 |
| `/api/docs/*`、`Document*`、`document` objectType | 保留为知识内容编辑器底座和平台对象兼容命名 | 单独完成知识内容编辑器命名迁移、API facade 覆盖和 route-final |
| `documents.content` | 保留为全文兼容快照、回滚和旧导出对照 | active blocks 覆盖率稳定，导出/搜索/评论不依赖旧字段，并有回滚演练 |
| `/api/knowledge-bases/{spaceId}/governance*` | 保留兼容但冻结新增 | 后台 `/api/admin/knowledge-bases/*` 完全覆盖生产调用 |
| `/api/search/reindex` | 暂保留，后续迁移候选 | 迁入 `/api/admin/*` 搜索治理 facade 并完成调用方迁移 |

项目事项 API 当前支持：

- `GET /api/projects/{projectId}/issues` 按状态、类型、优先级、负责人筛选，并支持常用排序。
- `POST /api/issues/{issueId}/transition` 做后端状态动作校验；兼容旧 `status` 请求，也支持 `action`、`reason`、`targetIssueId` 和 `dueAt` 承载信息不足、重复、无法复现、延期、取消、提交修复、验证打回等分支。
- `GET /api/issues/{issueId}` 返回 `availableActions`，前端据此展示可执行流程动作。
- `POST /api/issues/{issueId}/verifications` 写入 BUG 验证结果、环境、复现步骤和修复版本；已解决 BUG 验证通过会关闭，验证失败会退回处理中。
- `POST /api/issues/{issueId}/relations` 通过平台对象 resolver 建立事项到 issue、document、base record、message 等对象的关联。

IM API 当前支持：

- `GET /api/conversations/{conversationId}/messages/search` 在单个会话内按文本搜索消息，并可通过 `targetType` 按消息内链接对象类型过滤。
- `POST /api/conversations/{conversationId}/messages/{messageId}/convert-to-issue` 从消息创建需求、任务或 BUG；后端校验消息可见性和项目编辑权限，创建事项后写入 `issue_relations(target_type='message')`、活动、领域事件、审计和原会话回执消息。

知识内容底座兼容 API 当前支持：

- `GET /api/knowledge-bases` 查询当前用户可见的知识库空间；服务会补登记旧 API 新建的 `space + knowledge_base` 文档。
- `POST /api/knowledge-bases` 创建知识库空间，同时创建 `doc_type='space'` 且 `knowledge_base=true` 的根文档，并自动创建 `首页` markdown 文档登记为 `homeDocumentId`；保留 `/docs/{id}` 旧链接兼容。
- `GET/PATCH /api/knowledge-bases/{spaceId}` 查询和编辑空间名称、编号、描述、图标、封面、可见性、首页文档和默认权限。
- `/docs` 根路径已重定向到 `/knowledge-bases`；`/knowledge-bases/:spaceId` 是知识库空间壳路由，默认根据 `homeDocumentId` `replace` 到 `/knowledge-bases/:spaceId/items/:docId` 正文；`/knowledge-bases/:spaceId/items/:docId` 是知识库内容页主路由，复用 `DocsPage` 的编辑、评论、版本、分享、权限、关系和知识元数据能力；`/docs/:docId` 只保留为历史 deep link 和分享兼容入口，并在有 `knowledgeContext` 时展示返回知识库主操作。新建、编辑、停用、恢复和归档知识库空间统一从 `/knowledge-bases` 进入。
- `KnowledgeBaseDetailPage` 不再把空间统计、节点元数据、v1 验收报告和治理面板作为默认正文；点击 markdown 节点直接进入内容路由，点击 `space/folder` 节点展示子内容列表和创建入口，折叠区只保留关注、空间权限、节点权限和协同健康等轻量空间设置。当前模型只有空间级 `homeDocumentId`，非根目录默认首页字段留给后续目录节点模型扩展。
- 知识库空间壳 URL 状态规范：`docId` 指向当前目录节点；`view=directory` 为默认目录内容视图；`view=management` 展开轻量空间设置；`mode`、`blockId`、`commentId` 暂保留给内容页或后续定位。目录展开状态保存在 localStorage，详情页滚动位置按 `spaceId/docId/view` 保存在 sessionStorage。
- `GET /api/knowledge-bases/{spaceId}/items` 和 `/items/tree` 查询当前知识库内容节点列表和目录树，替代知识库工作台对全局 `/api/docs/tree` 的直接依赖。
- `POST /api/knowledge-bases/{spaceId}/items` 支持创建 `markdown` 内容页、`folder` 目录、`object_ref` 对象入口和 `external_link` 外部链接。对象入口保存目标类型、目标 ID、目标路由、展示模式、标题策略和入口别名；服务端读取知识库目录时通过 `PlatformObjectResolverRegistry` 解析目标对象并返回 `DocumentSummary.targetSummary`。目标不可访问时，服务端清空目标路由并改写为安全占位，前端禁止跳转。
- `target_object_type='base'` 的知识库入口复用 Base 模块主模型和 `BasePlatformObjectResolver`。前端 `KnowledgeBaseDetailPage` 支持选择已有 Base、从知识库目录新建 Base 并挂载、保存 `/bases/:baseId/tables/:tableId?viewId=` 路由，以及在知识库壳内渲染 `KnowledgeBaseBasePreview`；完整编辑仍跳转 `/bases`。
- `POST /api/knowledge-bases/{spaceId}/items/from-template`、`/items/{documentId}/move`、`/archive`、`/restore` 支持知识库内模板创建、移动/排序、归档和恢复；服务会校验内容节点和目标父级必须属于当前知识库。
- `GET/POST /api/knowledge-bases/{spaceId}/templates` 管理空间模板；`POST /api/knowledge-bases/{spaceId}/import/markdown-batch` 和 `GET /api/knowledge-bases/{spaceId}/export/markdown` 处理知识库级导入导出。
- `GET /api/knowledge-bases/{spaceId}/governance` 返回知识库健康度、风险列表、访问统计、热门/低访问文档和搜索无结果词；健康度包含 block 覆盖缺口、空内容块、失效嵌入对象和 block 覆盖率。
- `POST /api/knowledge-bases/{spaceId}/governance/bulk` 支持批量设置维护人、追加/替换标签、归档和发起复核。
- `GET /api/knowledge-bases/{spaceId}/governance/export` 输出治理报告 CSV，复用现有文档、权限、最近访问和审计表。
- `POST /api/knowledge-bases/{spaceId}/disable`、`/restore`、`/archive` 和 `DELETE /api/knowledge-bases/{spaceId}` 支持停用、恢复和软归档；归档会同步归档根内容树，恢复会恢复根内容树。
- 知识库创建、编辑、停用、恢复和归档写入 `knowledge_base.*` 审计动作；停用知识库后，普通成员不能在其根内容树下新建内容，owner/manage 仍可治理。
- 知识库对象入口创建、移动、归档、恢复和目标不可用写入 `knowledge.node.*` 审计动作；搜索命中 `object_ref` 时会二次解析目标对象，目标不可用则隐藏标题、摘要、链接和 deep link，并返回权限解释文案。
- `GET /api/docs/tree` 查询历史全局内容树，只作为兼容和编辑器底座保留；知识库工作台必须使用 `/api/knowledge-bases/{spaceId}/items/tree`。
- `GET /api/docs/{documentId}/path` 查询当前文档路径面包屑。
- `POST /api/docs/{documentId}/move` 支持移动父节点和同级排序，并阻止循环父子关系。
- `POST /api/docs/{documentId}/archive` 和 `POST /api/docs/{documentId}/restore` 支持文档子树归档和恢复。
- `PATCH /api/docs/{documentId}/blocks` 保存结构化块并生成新版本；版本摘要按 block id 比较新增、删除、修改、移动和类型转换。当前块类型包括文本块、普通表格块、Base 视图、事项/BUG、消息、文件和通用平台对象嵌入块。
- `GET /api/docs/{documentId}` 和 `GET /api/docs/{documentId}/blocks` 会对嵌入块按当前用户水合 `embedSummary`，权限态复用平台对象 resolver。
- `POST /api/docs/{documentId}/comments` 支持全文、块和选区评论；`comment` 及以上权限可评论，选区锚点保存 range、摘录和前后文。
- `POST /api/docs/{documentId}/comments/{commentId}/replies` 在线程下新增回复。
- `POST /api/docs/{documentId}/comments/{commentId}/resolve` 和 `/reopen` 标记评论线程已解决或重新打开。
- `POST /api/docs/{documentId}/share-link`、`/share-link/enable`、`/share-link/disable` 管理组织内链接分享，支持 view/comment/edit、启停和过期时间。
- `POST /api/docs/{documentId}/knowledge-base` 仅作旧根目录兼容接口；若根目录已登记到 `knowledge_base_spaces`，该接口不再改写空间描述、封面和默认权限，空间设置必须走 `/api/knowledge-bases/{spaceId}`。
- `POST /api/docs/{documentId}/permission-requests` 提供无权限申请占位流程，向 owner/manage 用户投递通知。
- `POST /api/docs/{documentId}/permissions` 是旧文档授权入口的兼容 API，但运行时只写通用 `resource_permissions`；成员、部门、用户组和角色的内容访问、权限列表、权限解释、分享链路排除和权限申请通知都以通用 ACL 为准。
- `GET /api/docs/{documentId}/versions/diff` 和 `POST /api/docs/{documentId}/versions/{versionNo}/restore` 支持版本对比和恢复。
- `POST /api/docs/{documentId}/import/markdown` 和 `/import/html` 支持把 Markdown/HTML 转换为 blocks；复杂 HTML 降级为 `legacy_html`，危险 HTML 只保留安全占位。
- `GET /api/docs/{documentId}/export/markdown` 和 `/export/html` 支持按 view 权限从 active blocks 导出 Markdown/HTML；嵌入对象输出安全 directive 或占位，不依赖不可访问对象的标题、摘要和路径。
- `GET /api/docs/{documentId}/performance` 返回大文档性能画像，供前端选择 full editor 或 lazy preview。
- `GET /api/docs/{documentId}/migration-preview` 只读评估旧 content 到 block projection 的迁移状态和回滚可用性。
- `GET /api/docs/{documentId}/collaboration/health` 返回当前协同 server clock、active users、dirty 状态和保存时间。
- `GET /api/docs/acceptance/v1` 返回知识内容底座兼容验收报告，包含 10 个真实场景、8 个验收门、P0/P1 收口状态和冻结标准。
- 文档前端使用平台对象最近访问和收藏 API 提供最近/收藏入口；文档内部链接卡片仍依赖平台对象摘要。
- 知识库前端 `/knowledge-bases/:spaceId` 工作台和 `/knowledge-bases/:spaceId/items/:docId` 内容页使用 `/api/knowledge-bases/{spaceId}/items/tree`、`/items`、`/items/from-template`、`/items/{documentId}/move` 和 `/templates` 承载知识库语义；编辑器详情、保存、块、评论、版本、分享、权限、关系和协同仍复用 `/api/docs` 底层能力。

Base API 当前支持：

- 字段类型：text、number、member、date、attachment、single_select、multi_select、status、url、object_link。
- `POST /api/bases/{baseId}/tables/{tableId}/records/query` 支持筛选、排序和分页。
- `POST /api/bases/{baseId}/tables/{tableId}/views` 保存筛选、排序和字段显隐视图。
- `GET /api/bases/{baseId}/tables/{tableId}/views/kanban` 和 `/views/calendar` 支持看板/日历视图，状态字段可作为看板分组字段。
- `GET /api/base-records/{recordId}` 支持从搜索、IM 卡片或直接链接读取记录摘要；`GET /api/base-records/{recordId}/detail` 聚合字段、评论、关联对象和最近活动。
- `POST /api/base-records/{recordId}/comments` 和 `/relations` 支持记录讨论和跨对象关系；关系目标通过平台对象 resolver 校验访问态。
- `GET /api/bases/{baseId}/tables/{tableId}/export.csv` 与 `POST /api/bases/{baseId}/tables/{tableId}/import.csv` 支持 UTF-8 CSV 导出和小规模导入。
- Base 和 base_record 已注册平台对象链接，内部链接和 IM 分享可走统一对象卡片。

## 实时事件

WebSocket 入口：

```text
ws://localhost:8080/ws/events?token={accessToken}
```

当前主要事件类型：

- `message.created`
- `message.edited`
- `message.revoked`
- `message.pinned`
- `message.unpinned`
- `message.reaction.toggled`
- `conversation.updated`
- `conversation.read`
- `unread.changed`
- `notification.created`
- `notification.read`
- `notification.unread.changed`

前端 `useWebSocketConnection` 负责连接、重连、事件去重和分发。

IM 消息定位使用 `GET /api/conversations/{conversationId}/messages/{messageId}/context` 拉取目标消息之前的上下文，前端打开 `/im?conversationId={conversationId}&messageId={id}` 后滚动并高亮目标消息。会话内搜索使用 `GET /api/conversations/{conversationId}/messages/search`，返回同一 `MessagePage` 结构，前端从搜索结果切换到 `messageId` 上下文。WebSocket 断线重连后，前端按当前会话最大 `messageSeq` 补拉、合并并去重。

## M41 文档编辑器改造设计基线

本节到 M50 是历史里程碑命名，当前按“知识库内容编辑器底座”理解；这里的“文档”仅指 `Document*` 兼容代码、`documents` 数据表和 `document` 平台 objectType，不代表独立产品模块。

M41 历史上锁定 `docs` 编辑器底座从保存式 Markdown/块表单体验升级到类 Lark 在线知识内容编辑体验。本节是 M42-M50 的架构入口；实现时不得绕开当前底座边界，也不得恢复独立文档产品模块。

### 当前基线清单

| 层级 | 当前文件 | 现状 | M41 判断 |
| --- | --- | --- | --- |
| 前端页面 | `web/src/modules/docs/pages/DocsPage.tsx` | 内容树、详情、标题输入、块表单、版本、分享权限、知识库设置、关系、评论集中在单页 | M46 原授权弹窗升级为分享与权限入口，无权限页可申请访问 |
| 前端 API | `web/src/modules/docs/api/docsApi.ts` | REST client 覆盖文档、树、块、版本、权限、分享链接、知识库、权限申请、关系、评论 | M46 增加 `shareLinks`、权限来源和知识库字段 |
| 后端 API | `DocumentController.java` | `/api/docs` 下提供列表、树、详情、保存、块、移动、归档、版本、权限、分享链接、知识库、权限申请、关系、评论、版本检查点 | M44 增加协同 API/WebSocket 命令和 `versions/checkpoint`；M46 增加分享权限 API |
| 应用服务 | `DocumentService.java` | `baseVersionNo` 冲突检测、整篇/整组块保存、版本生成、检查点生成、块水合、分享权限、权限申请和权限校验 | M46 权限 rank 为 owner/manage/edit/comment/view，comment 可评论 |
| 数据库 | `documents`, `document_blocks`, `document_versions`, `document_comments` | `documents.content` 与 `document_blocks` 双写，块内容为 text/JSON 混合 | M43 起明确 `document_blocks` 为主结构，`documents.content` 为搜索/兼容快照 |
| WebSocket | `shared/websocket` | `/ws/events` 只注册用户 session，客户端消息暂不处理 | M44 增加文档 room、presence、cursor、update、snapshot 协议 |

### `DocEditor` 组件边界

M42 的 `DocEditor` 是知识内容主编辑器，不再让 `DocsPage` 直接承载编辑规则。组件边界如下：

| 输入 | 说明 |
| --- | --- |
| `document` | `DocumentSummary`，包含 id、title、version、permissionLevel、知识库字段和 archived |
| `content` | 旧文档兼容内容，M42 用于生成初始 ProseMirror doc |
| `blocks` | 当前结构化块，M43 起作为编辑器块结构来源 |
| `comments` | M45 后支持 root thread、replies、全文/块/选区 anchor、未解决锚点高亮和 deep link |
| `permissions/shareLinks` | M46 后用于分享弹窗、权限来源可视化和组织内链接状态展示 |
| `collaboration` | M44 起注入 room 状态、presence、remote cursors、sync state |

| 输出 | 说明 |
| --- | --- |
| `onTitleChange` | 标题编辑，M42 仍走 `saveDocument` |
| `onContentChange` | 编辑器 JSON/HTML/plain text 快照变化 |
| `onSave` | M42 手动保存；M44 协同文档变为生成检查点，非协同路径保留兜底保存 |
| `onCommentAnchorCreate` | M45 选区评论入口 |
| `onInsertObject` | M43 对象卡片插入入口 |

`DocsPage` 保留路由、内容树、数据查询、权限弹窗、关系弹窗和右侧面板组装；`DocEditor` 只处理写作画布和编辑交互。

### KB-UX-M7 编辑器选型结论

当前项目继续选择 Tiptap 作为类 Lark 块编辑器底座。原因：

| 方案 | 结论 | 理由 |
| --- | --- | --- |
| Tiptap | Adopt | 已在项目内运行，React/ProseMirror/Tables/Task/DragHandle/Slash/ObjectCard 已接入，bundle 已按 chunk 拆分，迁移成本最低。 |
| BlockNote | Reject for now | 块模型友好，但会引入更重、更强约束的 UI 层，与当前 Ant Design + 自定义知识库风格重叠。 |
| Lexical | Fallback | 编辑核心强且长期可选，但现阶段需要重建表格、对象卡片、Slash、拖拽和 Markdown 兼容链路。 |

M7 新增 `KnowledgeContentEditor` 适配层和 `knowledgeContentAdapter`：

1. 外部协议以 `DocumentBlock[]` 和 `DocumentBlockDraft[]` 为主，不暴露 Tiptap 细节。
2. 内部暂复用现有 `DocEditor`，通过 Markdown 兼容快照过渡。
3. Adapter 提供 blocks -> Tiptap JSON、Tiptap JSON -> block drafts、blocks -> Markdown、Markdown -> block drafts。
4. 自动保存、只读、禁用、冲突、协同状态和周边按钮通过适配层透传，M8 再接入知识库内容页。
5. 中文输入法、HTML 粘贴、移动端输入、拖拽和长文档性能列为 watch 风险；Yjs/实时协同不在 M7 引入。

### M42 实现落点（2026-06-20）

M42 已完成 `DocEditor` 首个产品实现，落点如下：

| 文件 | 责任 |
| --- | --- |
| `web/src/modules/docs/components/DocEditor.tsx` | Tiptap 编辑器实例、标题画布、toolbar、bubble menu、Markdown 兼容转换、调试 fallback。 |
| `web/src/modules/docs/pages/DocsPage.tsx` | 保留内容树、详情查询、保存 mutation、结构化块、版本、权限、关系和评论面板，并把标题/正文编辑委托给 `DocEditor`。 |
| `web/src/index.css` | 定义文档编辑器画布、ProseMirror 内容区、任务列表、引用、代码块、bubble menu 和 fallback 样式。 |
| `web/package.json` / `pnpm-lock.yaml` | 在既有 Tiptap 依赖基础上补充 `@tiptap/extension-task-list` 与 `@tiptap/extension-task-item`，版本与现有 Tiptap 3.26.1 对齐。 |
| `.gitignore` | 将本地 `/docs/`、`/scripts/`、`/deploy/scripts/` 规则锚定到仓库根目录，避免误忽略 `web/src/modules/docs` 产品代码。 |

M42 仍走兼容保存路径：`DocEditor` 把 `documents.content` 转为 Tiptap JSON 初始化编辑器，编辑后序列化为 Markdown 兼容文本并通过 `saveDocument` 保存。`document_blocks` 在 M42 仍由原结构化块面板维护，M43 再升级为主编辑结构。

### M43 实现落点（2026-06-20）

M43 已把文档主体验升级为块级插入和对象卡片路径，并在 V033 补齐 block v2 兼容字段；当前仍沿用 M42 的兼容保存策略，后续再逐步把富文本块作为主写入结构。

| 文件 | 责任 |
| --- | --- |
| `web/src/modules/docs/components/DocEditor.tsx` | 增加块 drag handle、slash 菜单、表格编辑工具条、`objectCard`/`fileCard` Tiptap 节点、内部链接粘贴识别和图片/文件上传插入。 |
| `web/src/modules/files/api/filesApi.ts` | 前端文件上传 client：创建预签名上传 URL、PUT 文件、complete 上传，并传入 `targetType=document` 与 `targetId` 写入 `file_usages`。 |
| `web/src/modules/docs/pages/DocsPage.tsx` | 旧结构化块编辑器折叠为“兼容结构化块”，主编辑体验不再暴露 JSON 嵌入表单。 |
| `web/src/index.css` | 补充块 handle、slash menu、表格工具条、对象卡片、文件卡片和兼容块面板样式。 |
| `web/vite.config.ts` | 将 Tiptap/ProseMirror 依赖按核心、块、标记、表格、媒体、拖拽、菜单等拆分 chunk，避免编辑器能力扩展后超过 500KB chunk 预算。 |

M43 的对象和文件卡片使用 Markdown-like directive 作为兼容快照：`::object-card{...}` 和 `::file-card{...}`。打开旧文档时前端将 directive 转为 Tiptap 节点；保存时再序列化回兼容文本。该策略保证现有 `saveDocument`、版本、搜索和旧 `document_blocks` 生成链路不被破坏。

M43 浏览器烟测覆盖：块 handle 可见、`/` 菜单 13 个入口、段落转 H2、插入表格并加行、粘贴 `/docs/{id}` 生成对象卡片、上传文件生成文件卡片且 `file_usages` 写入 1 条。烟测还暴露并修复了两个实现问题：directive 解析过度转义，以及程序化插入对象/文件后保存状态未同步。

### 块 Schema v2

M43-T09/M44 后块结构以 block v2 为主。V033 已在 `document_blocks` 中落地兼容字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | uuid | 块稳定 ID，评论、选区和协同定位依赖它 |
| `type` | string | 块类型 |
| `attrs` | object | 类型属性，例如 level、checked、language、objectType、objectId |
| `content` | rich json | ProseMirror/Tiptap 内容片段 |
| `plainText` | string | 搜索、版本摘要和 fallback 使用 |
| `sortOrder` | integer | 非协同 fallback 排序 |
| `parentId` | uuid/null | 父块 ID；普通文档为 null，列表、折叠块、嵌套内容可使用 |
| `anchorId` | string | 稳定锚点；评论、搜索深链和后续协同定位使用 |
| `schemaVersion` | integer | 块结构版本 |
| `blockVersion` | integer | 单块更新版本；当前 upsert 时递增 |
| `createdBy/updatedBy` | uuid | 作者和最近修改人 |

当前后端写入仍同步旧 `content` 字段，`plain_text` 和 `rich_content` 已进入 Java 模型、REST 返回和保存路径；旧块读取、版本和搜索链路不因 V033/V043 中断。

首批类型：`paragraph`、`heading`、`bullet_list`、`ordered_list`、`task_item`、`quote`、`code_block`、`table`、`image`、`file`、`embed_object`、`base_view`、`issue_embed`、`message_embed`、`link_card`、`legacy_html`、`divider`、`callout`、`toc`。旧 `list/task/code/embed/link` 仍作为兼容类型读取和保存。

旧类型兼容映射：

| 旧类型 | v2 类型 |
| --- | --- |
| `list` | `bullet_list` |
| `task` | `task_item` |
| `code` | `code_block` |
| `file_embed` | `file` 或 `embed`，取决于是否需要文件预览 |
| `link` | `link_card` |

### 编辑器 JSON 与后端转换规则

M42 先使用兼容保存路径：

1. REST 读取 `DocumentDetail.content`。
2. 前端将 Markdown/plain text 转换为 Tiptap doc。
3. 保存时导出 `plainText` 或 Markdown-like 文本，调用 `saveDocument`。
4. 后端继续用 `blocksFromContent` 生成旧块，保证旧版本和搜索不破。

KB-UX-M6 起 block v2 后端路径可用：

1. REST 读取 `DocumentDetail.blocks`。
2. 前端将 block v2 转为编辑器内部文档模型。
3. 编辑器变更导出 block v2 draft。
4. 后端可通过批量保存、插入、局部更新、重排和删除接口保存 block v2，并同步生成 `documents.content` 纯文本快照。
5. `document_versions` 仍保存用户可理解的文本快照；富文本 diff 在 M47 再升级。

### 知识内容协同协议草案

M44 在现有 `/ws/events` 上扩展客户端命令。消息统一 JSON，至少包含 `type`、`requestId`、`documentId`、`payload`。

| 命令/事件 | 方向 | 说明 |
| --- | --- | --- |
| `document.join` | client -> server | 加入文档房间；后端校验 view 权限 |
| `document.leave` | client -> server | 离开文档房间 |
| `document.awareness.update` | client <-> server | 在线用户、光标、选区、编辑状态 |
| `document.update` | client <-> server | CRDT/Yjs update 或等价操作流；后端校验 edit 权限 |
| `document.snapshot.request` | client -> server | 重连或初次进入时请求状态快照 |
| `document.snapshot` | server -> client | 返回协同状态、版本、server clock |
| `document.saved` | server -> client | 自动保存完成 |
| `document.error` | server -> client | 权限、版本、协议和解析错误 |

单节点阶段可先在内存维护房间和 awareness；持久化落在 `document_collaboration_states`。多实例阶段再通过 Redis pub/sub 广播 room 事件。

M44 实现落点（2026-06-20）：

| 文件 | 责任 |
| --- | --- |
| `server/src/main/java/com/colla/platform/modules/doc/application/DocumentCollaborationService.java` | 文档 room、join/leave、presence、cursor、snapshot/update、自动保存投影和 WebSocket 帧发送。 |
| `server/src/main/java/com/colla/platform/modules/doc/api/DocumentController.java` | 暴露文档 REST 能力；M44-T09 增加 `POST /api/docs/{documentId}/versions/checkpoint` 手动生成版本检查点。 |
| `server/src/main/java/com/colla/platform/shared/websocket/PlatformWebSocketHandler.java` | `/ws/events` 从只读推送升级为可解析客户端命令，并把 `document.*` 分发到协同服务。 |
| `server/src/main/resources/db/migration/V029__create_document_collaboration_states.sql` | 保存 `document_collaboration_states`，包含 `state_vector`、`snapshot_content`、`snapshot_payload`、`server_clock`、`last_client_id`、`last_saved_at`。 |
| `web/src/modules/docs/hooks/useDocumentCollaboration.ts` | 前端 room 连接、join/snapshot request、断线重连、pending update、presence 和远端 snapshot 合并。 |
| `web/src/modules/docs/components/DocEditor.tsx` | 注入协同状态、在线用户、远端光标位置和 selection awareness 上报。 |
| `web/e2e/docs-collaboration.spec.ts` | M44-T10 多客户端协同浏览器 E2E；覆盖两个浏览器上下文登录、同文档编辑、内容汇合、无版本冲突和生成版本。 |

本轮未引入 Yjs 依赖，采用 `snapshot-v1` 等价操作流作为单节点阶段的协同编码。`document.update` 携带整篇 title/content 快照、`clientId`、`localSeq`、`baseServerClock`、`serverClock` 和 `stateVector`；服务端校验 edit 权限后广播，并把快照写入 `document_collaboration_states`。该编码保证现有 Tiptap -> Markdown 兼容快照、搜索、版本和 `document_blocks` 投影链路不被打断；未来可在同一协议字段下把 payload 替换为 Yjs update。

### 自动保存策略

M42-M43：保留手动保存，显示 `dirty`、`saving`、`saved`、`conflict`。

M44：改为自动保存：

1. 编辑器变更先进入本地 `dirty`。
2. CRDT update 发到 room，远端实时可见。
3. 后端按文档 debounce 或固定间隔落 `document_collaboration_states`。
4. 后端将稳定快照投影到 `documents.content` 和 `document_blocks`。
5. 用户点击“生成版本”时写入 `document_versions`，形成可审计检查点。
6. REST `baseVersionNo` 冲突保留为非协同 fallback，不作为主体验。

M44 当前实现中，自动保存不递增 `documents.current_version_no`，只更新文档当前内容、更新时间和块投影；`document.saved` 事件用于前端把状态从 saving/dirty 收敛到 synced。M44-T09 增加 `POST /api/docs/{documentId}/versions/checkpoint`，由后端读取当前文档快照生成下一条 `document_versions` 记录并递增 `current_version_no`；前端协同文档主按钮显示“生成版本”，等待自动保存同步后才允许点击，避免普通用户频繁手动保存和旧 `baseVersionNo` 冲突弹窗。

### M47 版本、搜索、模板和导入实现落点

M47 已把版本从单一文本快照扩展为可审计的类型化历史，同时补齐模板创建、Markdown 导入和 Markdown/HTML 导出入口。

| 能力 | 实现 |
| --- | --- |
| 类型化版本 | `V032__extend_document_versions_templates_import.sql` 为 `document_versions` 增加 `version_name`、`version_type`、`summary`、`source_version_no`、`block_snapshot`。 |
| 版本类型 | 当前支持 `auto_snapshot`、`manual_checkpoint`、`named`、`restore`、`import`；恢复版本会新建 `restore` 记录并保留来源版本号。 |
| 块级 diff 和版本摘要 | `DocumentService.diffLines` 基于 `blocksFromContent` 做块级 LCS，`DocumentDiffLine` 返回 `scope=block`、`blockIndex`、`blockType`；`saveBlocks` 额外按稳定 block id 生成新增/删除/修改/移动/类型转换摘要，写入 `document_versions.summary`。 |
| 搜索索引 | `JdbcSearchRepository#indexDocuments` 聚合 `documents` 与 `document_blocks`，把表格、嵌入块和 JSON-like 内容转换为可搜索纯文本。 |
| 模板库 | 新增 `document_templates`，内置会议纪要、需求文档、项目计划、知识条目；`GET /api/docs/templates` 返回可创建模板。 |
| 从模板创建 | `POST /api/docs/from-template` 复用 `createDocument`，继承父级权限并写入初始手动检查点。 |
| Markdown 导入 | `POST /api/docs/{documentId}/import/markdown` 需要 edit 权限，导入后替换正文和块投影，并写入 `version_type=import`。 |

前端 `DocsPage` 在版本面板增加“命名”和“导入”入口，在内容树操作区增加“从模板创建”；`docsApi.ts` 同步暴露模板、命名版本和导入 API。M47 仍保持 `documents.content` 与 `document_blocks` 双写，确保搜索、版本和旧结构化块兼容路径不断裂。

### M48 跨模块文档工作台

M48 将文档从独立写作模块推进为跨模块工作台：

- IM 消息可以通过 `ImController` 转换为文档，文档正文保留消息作者、时间、会话和消息链接，并写入 `message` 来源关系。
- 文档选区创建项目事项由 `DocumentCrossModuleService` 编排，避免 `DocumentService` 直接依赖 `ProjectService` 后形成 `DocumentService -> ProjectService -> PlatformObjectResolverRegistry -> DocumentPlatformObjectResolver -> DocumentService` 循环。
- 文档关系入口仍由 `DocumentService.addRelation` 统一校验文档编辑权限和目标对象可见性；对 issue 和 base record 写入反向关系，保证项目事项和 Base 记录可以回看引用文档。
- 前端文档工作台支持从评论选区创建事项、从消息菜单创建文档、Base view 权限态/筛选/排序摘要、文件卡替换和平台对象关系扩展。
- 全局搜索命中文档块时返回 `#doc-block-{blockId}`，命中文档评论时返回 `?commentId={threadId}`；`SearchService` 保留已有 webPath，避免平台对象水合覆盖深链。
- 事项详情以关联描述中的引用片段作为轻量上下文展示，后续若需要更强一致性，应升级为显式 document anchor relation，而不是继续解析描述文本。

### M49 性能、可靠性和迁移边界

M49 不改变文档主存储模型，优先补可观测、可降级和可回滚能力：

- 性能画像由后端按块数、嵌入数、评论数、正文长度和行数计算；前端使用同样阈值在大文档默认进入只读预览，避免初次加载完整 Tiptap 树导致卡顿。
- 大文档当前采用“预览后加载完整编辑器”的保守策略，不是完整块级虚拟滚动；如果后续文档规模继续增长，应把块数据拆成分页/窗口化接口。
- 协同服务继续使用 `snapshot-v1` 和自动保存投影；新增 health 接口用于观察 server clock、active users、dirty 状态和保存时间，presence 定期清理关闭连接和过期会话。
- 离线恢复仍以前端 pending update、重连 snapshot request 和文本合并为主；后续如引入 Yjs，应复用现有 state vector / server clock 字段做协议升级。
- 迁移预览是只读接口，用于评估 `documents.content` 到 `document_blocks` 的投影数量、回滚可用性和迁移模式；批量迁移脚本仍只允许作为本地工具，不进入远程仓库。
- M49-T10 当前自动化验证的是 `snapshot-v1` 的自动保存、刷新重连恢复和协同 health；真正多端 1 秒内 CRDT 合并仍是后续 Yjs/CRDT 升级项。

### M50 v1 验收报告和冻结边界

M50 把知识内容编辑底座 v1 验收标准产品化为只读报告，而不是只停留在本地执行记录：

| 能力 | 实现 |
| --- | --- |
| 验收报告 API | `GET /api/docs/acceptance/v1` 返回 `version=document-v1`、`status=frozen`、`frozen=true`、`openP0=0`、`openP1=0`。 |
| 真实场景 | 报告包含会议纪要、需求、项目计划、复盘、知识库、Base 看板、问题排查、审批说明、文件说明、跨模块工作台 10 个场景。 |
| 验收门 | 报告包含 3-5 人协同、权限分享、评论通知、消息转文档、文档转任务、P0/P1 缺陷、v1 冻结和质量门禁 8 个门。 |
| 前端展示 | 文档页元信息区展示 v1 状态、冻结标签、场景/验收门数量、P0/P1 状态、冻结说明和明细。 |
| 试运行边界 | 自动化可覆盖双客户端协同和接口门禁；3-5 人真人编辑延迟、误操作和体验反馈必须由团队在产品环境执行。 |

该报告冻结的是文档 v1 验收标准；后续 M48-T09、M49-T09 等增强项进入 v1.x，不反向改变 v1 冻结口径。

### 旧文档迁移策略

迁移必须可重复、可回滚、可灰度：

1. 打开旧文档时按需把 `documents.content` 转换成 Tiptap doc。
2. 若 `document_blocks` 已存在，优先使用块顺序和 `block_type` 生成 v2 块。
3. 表格和嵌入块继续解析当前 JSON 内容；解析失败时降级为 code/paragraph 并保留原始文本。
4. 首次保存 v2 时写入新字段或 metadata，旧字段仍保留兼容快照。
5. 批量迁移脚本只作为本地工具，不进入远程仓库。

### M41 验收用例

M42 开始实现前至少覆盖以下用例：

1. 旧 Markdown 文档打开后内容完整显示在富文本编辑器。
2. view 权限打开文档只读，不能编辑正文和标题。
3. edit 权限修改正文和标题后保存，刷新后内容保留。
4. manage 权限仍可打开授权入口。
5. 版本恢复后编辑器内容更新。
6. 块评论 deep link 仍能定位到对应块或 fallback 到评论面板。
7. 嵌入对象无权限时不泄露标题和正文。
8. WebSocket 断开时 M42/M43 仍能通过 REST 手动保存。

## 搜索

当前搜索实现：

- `SearchIndexService` 负责刷新 workspace 索引。
- `SearchService` 查询前刷新索引并使用平台对象 resolver 水合结果；用户搜索响应带 `searchScope=user_content`。
- 用户搜索覆盖 issue、document、base、base_table、base_record、message、approval 等协作对象；权限、审计、组织治理和后台配置对象不得从 `/api/search` 返回。
- 后台治理搜索由 `GET /api/admin/search-governance` 承载，响应带 `searchScope=admin_governance`，当前返回权限、审计、应用、知识库和身份治理入口，并要求后台访问权限。
- 已归档文档不进入默认搜索结果。
- 知识内容搜索在 SQL 过滤阶段识别通用 `resource_permissions` 的成员、部门、用户组和角色主体，避免先召回无权限标题/摘要再由应用层丢弃。
- 知识内容搜索命中内容块或评论时保留块锚点/评论 deep link，便于从全局搜索跳回具体上下文。
- 查询同时使用 PostgreSQL `simple` 全文检索和小写 `ILIKE` 后备匹配，用于召回中文短语、对象编号、标题和记录值。
- 搜索结果在返回前经过对象 resolver 复核；可访问结果会带 `permissionExplanation`，不可访问或已删除对象不返回原始标题和正文。
- 知识库范围搜索无结果时，`SearchService` 写入 `knowledge.search.no_result` 审计事件，知识库治理统计按词聚合展示。
- `POST /api/search/reindex` 可手动重建索引，受管理权限保护；后续如继续保留，应在 M12 兼容清理清单中记录是否迁入 admin facade。
- 索引刷新在事务内串行执行，避免后台事件刷新和查询前刷新并发时暴露半成品索引。

## 交付与运维

当前交付基线位于 `deploy/`：

- `deploy/docker-compose.prod.yml`：单节点生产/测试环境 Docker Compose 编排，包含 PostgreSQL、Redis、MinIO、后端、Web 和 Nginx。
- `deploy/nginx/colla.conf`：API、WebSocket、健康检查和 Web 前端反向代理。
- `deploy/scripts/backup.ps1`：生成 PostgreSQL SQL dump、MinIO 数据归档、manifest 和 SHA-256 校验。
- `deploy/scripts/restore.ps1`：带 `-ConfirmRestore` 的破坏性恢复脚本，恢复后执行健康检查。
- `deploy/scripts/restore-drill.ps1`：默认 dry-run 的恢复演练脚本，可校验 manifest 和 compose 配置。
- `deploy/scripts/health-check.ps1`：校验 `/api/health`、`/actuator/health`，直连后端时可校验 `/actuator/prometheus`。
- `deploy/scripts/release-check.ps1`：发布前检查工作树、质量门禁、compose 配置和可选镜像构建。
- `deploy/scripts/rollback.ps1`：带 `-ConfirmRollback` 的应用回滚和可选数据回滚入口。
- `scripts/performance-baseline.ps1`：对登录、IM、项目、知识内容、Base、审批、搜索等关键 API 输出本地性能基线和阈值状态。
- `scripts/security-audit-gate.ps1`：检查测试隔离、生产密钥外置、安全路由和关键服务审计覆盖，并已接入质量门禁。
- `scripts/m31-collab-simulation.ps1`：重置、播种和校验 M31 10 人 5 项目协同基线，重置清单覆盖 Base 记录评论、关系和活动日志。
- `scripts/trial-team-template.ps1`：生成 M40 试运行 10 人团队初始化 CSV 和报告。
- `scripts/team-trial-readiness.ps1`：聚合质量门禁、安全审计、性能、恢复演练、健康检查、M31 reset/smoke 和试运行文档，输出 Go/No-Go 报告。
- `scripts/knowledge-base-migration-check.ps1`：只读检查旧 space 映射、孤立知识内容节点、owner 权限、继承来源、知识库搜索索引缺口、block 覆盖缺口、legacy HTML block、孤儿 block、失效嵌入对象和旧富文本回滚覆盖，并输出软回滚 SQL 模板。
- `scripts/knowledge-base-block-v2-trial.ps1`：生成知识库 v2 blocks 试运行报告，覆盖创建块文档、导入旧内容、嵌入 Base、评论 block、搜索命中和导出。
- `scripts/knowledge-base-compat-cleanup-check.ps1`：只读扫描 `/docs` 入口、旧字段读写、旧权限表写入、旧文案和兼容命名删除条件，输出知识库唯一入口冻结决策表。
- `scripts/knowledge-base-trial-runbook.ps1`：生成 3-5 人知识库试运行剧本和 checklist，覆盖创建、沉淀、分享、评论、搜索、治理和迁移复核。
- `scripts/knowledge-base-acceptance-report.ps1`：聚合迁移检查、试运行剧本、质量门禁和浏览器冒烟证据，输出知识库 v1 本地验收报告。
- `scripts/inspect-knowledge-object-references.ps1`：只读扫描知识库对象入口，输出引用汇总、无效引用形态、缺失文档目标、同目录重复别名和重复目标引用。
- `scripts/ui-split-v1-browser-smoke.ps1`：运行 `web/e2e/ui-split-v1-smoke.spec.ts`，覆盖用户工作台和管理后台主入口渲染、Shell 隔离、后台入口和返回工作台。

日志保留策略：

- `RequestLoggingFilter` 为每个 HTTP 请求生成/透传 `X-Colla-Request-Id`，记录 method、path、status、durationMs、client 和 username，不记录请求体、token 或权限列表。
- Spring Boot 通过 `logback-spring.xml` 输出 JSON app/error rolling logs。
- 生产 compose 将后端 `LOG_PATH` 指向 `/app/logs`，挂载到 `server_logs` volume。
- Docker json logs 在生产 compose 中限制为单文件 100 MB、每服务 14 个文件。
- Nginx 只代理 `/actuator/health`；`/actuator/prometheus` 允许直连后端或容器内运维检查使用，不通过生产 Nginx 默认暴露。

## 权限与对象可见性

当前系统存在两层权限：

- 业务模块内权限校验，例如项目成员、知识内容权限、Base 成员、审批参与人。
- 平台对象 resolver 返回 `available`、`forbidden`、`deleted`、`not_found`、`invalid` 权限态。
- `GET /api/platform/objects/{type}/{id}/permission-explanation?action=view` 返回统一解释模型，包含允许状态、当前权限级别、需要权限级别、权限来源和原因。
- `PermissionDecisionService` 对通用 `resource_permissions` 做最高权限计算，支持成员、部门、用户组和角色主体展开；文档访问和 Base 创建/成员授权已同步接入该通用 ACL。
- `RoleService` 产品化 RBAC 管理：权限码有风险等级和说明，角色可绑定权限码并通过 `role_assignments` 授予成员、部门或用户组；`JdbcIdentityRepository` 每次请求会展开直接角色、部门角色、用户组直接成员角色和用户组内部门成员角色。
- 高风险权限或高风险角色分配必须由请求体传入 `confirmHighRisk=true`，后端会写入角色权限和角色分配审计事件。
- `ResourcePermissionManagementService` 提供 document/base/project/knowledge_base 通用资源权限管理：owner/manage 或 admin 可列出授权、授予成员/部门/用户组/角色权限、撤销 direct 授权；inherited 授权只展示来源，不允许在子资源直接删除。
- `PermissionGovernanceService` 提供权限排查和风险巡检：管理员可输入用户、资源类型、资源 ID 和动作查看当前权限来源；风险规则覆盖停用成员仍有权限、停用部门/用户组仍被授权、资源无 owner、部门/用户组持有 manage/owner 和管理员过多，并支持 CSV 导出。
- 旧 `document_permissions` 已降级为迁移补偿源，不再作为新增授权或运行时权限判断依据；`scripts/knowledge-base-migration-check.ps1` 会检查活动旧授权是否已完整回填到 `resource_permissions`。

搜索、内部链接、对象卡片依赖平台对象摘要和权限解释，避免直接泄露无权限对象详情。

## 当前测试现实

当前后端测试位于 `server/src/test/java`，覆盖：

- Auth
- Admin user
- Device
- Workspace dashboard
- IM
- Project
- Document
- Base
- Approval
- Platform object
- Search collaboration
- Auth shared support

后端测试默认由 Maven Surefire 注入 `spring.profiles.active=test`，使用 `application-test.yml` 的 Testcontainers PostgreSQL URL `jdbc:tc:postgresql:16:///colla_platform_test`，不再向共享本地 `colla_platform` 演示库写入集成测试夹具。

最近一次模块验证结果：

- UI-SPLIT-M12：`pnpm smoke:ui-split` 通过；`pnpm work:finish -Goal "UI-SPLIT-M12" -TaskRange "UI-SPLIT-M12-T01 到 UI-SPLIT-M12-T08" -ValidationProfile route-final` 通过，覆盖完整 `mvn test`、后端 package、前端 lint/build、安全扫描、Flyway 迁移顺序、文档结构和工作循环契约。`pnpm web:lint` 仍保留既有 `useDocumentCollaboration.ts` 3 个 hook dependency warning，非 UI-SPLIT 新增。
- M26：`mvn -Dtest=ImControllerIntegrationTests test`、`mvn -Dtest=SearchCollaborationIntegrationTests test`、`pnpm web:lint`、`pnpm web:build`、`pnpm smoke:im` 均通过；内置浏览器验证 `/im` 可登录并渲染会话列表、消息区和信息栏；M26 checkpoint 与 finish/full 工作循环门禁通过。
- M27：`mvn -Dtest=ProjectControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 均通过；内置浏览器验证 `/projects` 和 `/issues/{id}` 可登录并渲染筛选条、看板、事项详情、关联对象入口和 BUG 验证字段。
- M28：`mvn -Dtest=DocumentControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 均通过；内置浏览器验证 `/docs` 可登录并渲染搜索、最近/收藏入口、块编辑器、版本、评论定位/解决入口，搜索过滤生效且控制台无错误。
- M29：`mvn -Dtest=BaseControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 均通过；内置浏览器验证 `/bases` 可登录并渲染表格、字段、保存视图、成员权限和记录详情面板，记录链接打开后展示字段值与评论预留区，控制台无错误。
- M30：运维脚本语法检查、生产 compose config、备份生成、恢复演练 dry-run、健康检查、release-check dry-run、`pnpm verify` 和 `pnpm work:finish` 门禁通过。
- M32：`mvn -Dtest=DocumentControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build`、`pnpm work:checkpoint -GateMode quick` 已通过；历史 `docs` 底座新增旧空间根节点、树形目录、路径、移动/排序、归档/恢复和 M31 内容树冒烟断言。
- M33：`mvn -Dtest=DocumentControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 已通过；历史 `docs` 底座新增普通表格块、Base 视图嵌入、事项/BUG 嵌入、消息嵌入、嵌入块评论/版本恢复和 M31 嵌入场景冒烟断言。
- M34：`mvn -Dtest=ProjectControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build`、`pnpm work:checkpoint -GateMode quick`、`pnpm work:finish` 已通过；项目模块新增状态动作、工作流分支原因/结论、BUG 验证打回/关闭、重复/无法复现/信息不足/延期/取消分支，并通过内置浏览器验证 `/projects` 事项详情流程动作弹窗和提交成功。
- M36：`mvn -Dtest=SearchCollaborationIntegrationTests,WorkspaceControllerIntegrationTests,ProjectControllerIntegrationTests test`、`pnpm --dir web lint`、`pnpm --dir web build`、`pnpm work:checkpoint -GateMode quick` 已通过；搜索、通知、权限解释和审计查询横向能力完成本轮补强。
- M37：`mvn -Dtest=BaseControllerIntegrationTests test`、`pnpm --dir web lint`、`pnpm --dir web build` 已通过；Base 模块新增字段校验、对象链接字段、记录评论/关系/活动、视图字段显隐、CSV 导入导出和文档内 Base 视图只读预览。
- M38：`pnpm work:checkpoint -GateMode quick`、`pnpm --dir web lint`、`pnpm --dir web build` 已通过；内置浏览器 390x844 视口验证 `/im`、`/projects`、`/docs`、`/bases` 移动 Shell 无水平溢出，PWA manifest/service worker/offline/icon 资源可访问，修复 Drawer 废弃属性后无新增 error 级日志。
- M39：`mvn test` 已在 `test` profile 下通过，日志确认 Testcontainers PostgreSQL 运行 28 个迁移；`pnpm security:audit`、`pnpm perf:baseline -Iterations 2 -AllowThresholdFailure`、`deploy/scripts/health-check.ps1 -SkipCompose -RequirePrometheus`、备份生成和 `restore-drill` dry-run 均已通过。
- M35：`mvn -Dtest=ImControllerIntegrationTests test`、`mvn -Dtest=ProjectControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 已通过；IM 模块新增消息转事项/BUG 后端闭环、会话内搜索和按对象类型过滤、消息链接卡片解析、会话分组、复制消息链接和窄屏体验优化。
- ORG-M1：`mvn -f server/pom.xml '-Dtest=OrganizationControllerIntegrationTests,AdminUserControllerIntegrationTests' test`、`pnpm web:lint`、`pnpm web:build` 已通过；组织架构模块新增部门树、部门成员、负责人、组织权限码、审计和后台组织架构页面。`pnpm web:lint` 仍保留既有文档协作 Hook dependency warning，非本轮新增。
- ORG-M3：`mvn -DskipTests test`、`mvn -Dtest=PermissionDecisionIntegrationTests test`、`pnpm work:checkpoint -GateMode quick` 已通过；统一资源权限决策接入文档、Base 和搜索，目标测试覆盖部门授权、角色授权、权限解释来源和搜索不可见性。首次目标测试因本地 MinIO 未启动失败，启动 `docker compose up -d minio redis` 后通过。
- ORG-M4：`mvn -Dtest=RoleControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 和 `/admin/roles` 局部 Playwright 冒烟已通过；角色权限产品化覆盖权限码目录、角色 CRUD、权限绑定、部门/用户组角色继承、撤销后失效、高风险二次确认和审计。
- ORG-M5：`mvn -Dtest=ResourcePermissionControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build` 已通过；资源权限管理覆盖文档部门授权继承、Base 用户组授权、direct 授权撤销、inherited 不可子资源撤销、高风险 manage/owner 二次确认和审计。
- ORG-M6：`mvn -Dtest=PermissionGovernanceControllerIntegrationTests test`、`pnpm web:lint`、`pnpm web:build`、`pnpm work:finish -- -Goal "ORG-M6-permission-governance-risk" -BackendTestPattern "PermissionGovernanceControllerIntegrationTests"` 已通过；阶段收口在 `server` 目录执行 `mvn test` 通过 59 个测试，并确认 Testcontainers PostgreSQL 从空库应用 V001 到 V037 共 37 个 Flyway 迁移；权限治理覆盖用户资源权限排查、风险巡检、风险 CSV 导出、权限审计快捷筛选和 `/admin/permission-governance` 局部浏览器冒烟。
- KB-M1：`mvn -Dtest=DocumentControllerIntegrationTests test` 通过 13 个测试，并确认 Testcontainers PostgreSQL 从空库应用 V001 到 V038 共 38 个 Flyway 迁移；`pnpm --filter web lint` 通过但保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning；`pnpm --filter web build` 通过。知识库空间产品层覆盖旧 space 自动补登记、新空间创建、停用后普通成员禁止新建、管理员治理、归档/恢复、审计和 `/knowledge-bases` 前端入口。
- KB-M2：`mvn -Dtest=DocumentControllerIntegrationTests test` 通过 13 个测试，并确认 Testcontainers PostgreSQL 从空库应用 V001 到 V038 共 38 个 Flyway 迁移；`pnpm web:lint` 通过但保留既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning；`pnpm web:build` 通过。知识库首页与导航覆盖自动首页、保留/指定 `homeDocumentId`、`/knowledge-bases/:spaceId` 工作台、目录搜索、展开记忆、最近/收藏/模板入口、新建、移动、排序和窄屏布局。

## 当前架构 Gap

- 仍是单体应用，没有拆微服务。
- 实时事件已统一入口，但不是完整事件订阅中心。
- 文档已有单节点 `snapshot-v1` 多人协同、presence、远端选区和自动保存；尚未升级到多节点 Redis pub/sub 和 Yjs CRDT update。
- 审计日志已有后端查询和 Web 管理页面，M39 已加入脚本化安全审计门禁；审计语义完整性仍需随新业务动作持续维护。
- M38 已具备移动 Web、PWA 和桌面薄壳基础；正式原生移动端、正式桌面安装包、自动更新和原生系统集成仍未交付。
- 生产部署仍是单节点 Compose 基线，尚未进入高可用、多节点、自动扩缩容或 Kubernetes 形态。
