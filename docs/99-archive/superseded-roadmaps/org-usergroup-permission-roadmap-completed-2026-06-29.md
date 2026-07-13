---
title: 组织架构、用户组与权限治理路线图（已完成归档）
status: archived
scope: identity-permission
last_code_check: 2026-06-21
archived_at: 2026-06-29
archived_reason: ORG-M1 到 ORG-M6 已完成，当前执行路线切换为类 Lark 知识库完善与打磨。
source_rule: 本路线图只作历史追溯，不作为当前执行依据。
remote_rule: 本文件位于 docs/，按 .gitignore 保持本地文档，不进入远程仓库。
---

# 组织架构、用户组与权限治理唯一路线图

本文定义 Colla Platform 组织架构、用户组与权限治理的当前唯一执行路线。后续 AI 工作循环、里程碑拆分、验收和返工都以本文为准。

产品需求来源：`docs/00-product/org-usergroup-permission-requirements.md`。

旧路线归档：Lark 文档改造路线 M41-M50 的代码侧任务已全部完成，已归档到 `docs/99-archive/superseded-roadmaps/lark-docs-current-roadmap-completed-2026-06-21.md`。

## 1. 核心判断

当前项目已经有基础成员、角色、权限码、资源权限和审计能力：

1. `users` 表有账号、邮箱、状态、设备、会话和字符串型 `department` 字段。
2. `roles`、`permissions`、`role_permissions`、`user_roles` 已能表达基础 RBAC。
3. `departments`、`department_members`、`department_managers` 已能表达真实部门树、主/兼部门和负责人。
4. `user_groups`、`user_group_members` 已能表达跨部门用户组和部门动态展开成员。
5. `resource_permissions` 已能存储资源级授权主体、来源、过期时间、状态和权限等级。
6. 后台已有成员、组织架构、用户组、审计日志等管理入口。

但当前仍缺少企业协同平台的完整权限治理闭环：

1. 资源权限已有统一决策基础，但缺少面向 owner/manage 用户的通用权限管理界面。
2. 文档已接入部门、用户组和角色授权解释；Base 已同步成员 ACL；项目、审批、通知、工作台仍需继续收敛到通用授权主体。
3. 权限风险巡检、批量治理、停用主体风险和无 owner 资源排查尚未产品化。
4. 搜索已接入文档通用 ACL 过滤；后续新增对象类型仍必须维持同一权限结果。

唯一方向：先建立部门和用户组这两个权限主体，再统一权限决策与解释，然后产品化角色权限和资源授权界面，最后补齐权限风险巡检。

不采用的方向：不把 `users.department` 字符串继续扩展成组织模型；不让各业务模块各自实现部门/用户组权限；不先做 SSO/LDAP/外部组织同步；不在缺少权限解释的情况下扩大搜索召回范围。

## 2. 执行顺序

推荐顺序：

1. ORG-M1：组织架构数据底座。
2. ORG-M2：用户组能力。
3. ORG-M3：统一权限决策与解释。
4. ORG-M4：角色权限产品化。
5. ORG-M5：资源权限管理界面。
6. ORG-M6：权限治理与风险巡检。

排序原因：

- 部门和用户组是权限主体基础，必须先建。
- 权限决策应尽早统一，否则文档、Base、项目会各自实现授权逻辑。
- 角色管理和治理界面可在基础模型稳定后逐步完善。

## 3. 类 Lark 验收口径

本路线不是普通后台账号管理，也不是只给管理员看的 RBAC 配置页。每个里程碑都必须服务于类 Lark 协同平台的统一工作空间：

| 口径 | 验收要求 |
| --- | --- |
| 统一组织 | 部门树、成员、负责人、用户组在后台统一维护，不能继续依赖 `users.department` 字符串。 |
| 统一授权主体 | 文档、Base、项目、审批、通知、搜索和工作台使用同一套成员/部门/用户组/角色主体。 |
| 统一权限解释 | 用户看到 forbidden、not_found、只读、可编辑等状态时，系统能解释来源和阻断原因。 |
| 统一信息入口 | 搜索、工作台、IM 对象卡片展示的内容必须遵循同一权限结果，不允许模块间不一致。 |
| 统一审计 | 组织、用户组、角色、资源授权变化都能在审计日志中追溯操作者、目标和上下文。 |
| 管理台闭环 | 管理员能从管理后台完成组织架构、用户组、角色权限和权限治理，不需要直接操作数据库。 |

后续实现时，如果某个任务只完成了表结构或接口，但没有接入至少一个业务入口或权限解释路径，不应视为该里程碑完整完成。

## 4. ORG-M1 - 组织架构数据底座

目标：让系统具备真实部门树和成员部门关系。

Lark 化结果：管理后台出现可维护的组织架构；成员不再只是平铺用户列表，而是能按部门树管理、筛选和进入后续授权。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| ORG-M1-T01 | Done | `V034__create_departments_and_org_permissions.sql` 新增 `departments`、`department_members`、`department_managers`。 |
| ORG-M1-T02 | Done | `MemberSummary.departments` 返回主/兼部门关系；成员查询和后台页面不再依赖字符串部门。 |
| ORG-M1-T03 | Done | 新增 `OrganizationRepository`、`JdbcOrganizationRepository`、`OrganizationService`、`OrganizationController`。 |
| ORG-M1-T04 | Done | `/api/admin/departments/tree` 和部门创建、编辑、移动、停用、删除接口已实现；移动阻止循环父子关系。 |
| ORG-M1-T05 | Done | 支持成员加入/移出部门、主部门唯一、部门负责人添加/移除。 |
| ORG-M1-T06 | Done | `AdminUsersPage` 展示部门标签，支持按部门筛选，创建成员时可选择主部门。 |
| ORG-M1-T07 | Done | 新增 `org.view`、`org.manage` 权限码，并授予内置 admin 角色。 |
| ORG-M1-T08 | Done | 部门创建/更新/移动/停用/删除、成员和负责人变更写入审计日志。 |

验收门：

- 可在后台创建部门树并把成员分配到部门。
- 禁用成员后，其历史部门关系仍可用于审计。
- 非授权用户不能访问组织管理接口。

## 5. ORG-M2 - 用户组能力

目标：让跨部门成员集合成为统一授权主体。

Lark 化结果：项目组、职能组、权限组可以独立维护，并可作为后续文档、Base、项目、通知和工作台权限主体。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| ORG-M2-T01 | Done | `V035__create_user_groups_and_document_subject_permissions.sql` 新增 `user_groups`、`user_group_members`。 |
| ORG-M2-T02 | Done | 新增 `UserGroupService`、`UserGroupController`、`UserGroupRepository`，支持用户组 CRUD、成员维护和部门成员动态展开。 |
| ORG-M2-T03 | Done | 用户组支持 `active`、`disabled`，停用后不能继续变更成员或作为新文档授权主体。 |
| ORG-M2-T04 | Done | 新增 `usergroup.view`、`usergroup.manage` 权限码，并授予内置 admin 角色。 |
| ORG-M2-T05 | Done | 后台新增 `/admin/user-groups` 用户组页面，支持创建、编辑、停用、删除、加入成员/部门和查看展开成员。 |
| ORG-M2-T06 | Done | 文档分享与权限弹窗的资源授权主体选择器支持 active 用户组。 |
| ORG-M2-T07 | Done | 用户组创建、更新、停用、删除和成员变更写入审计日志。 |
| ORG-M2-T08 | Done | 文档权限真实链路支持 `subjectType=user_group`，部门成员可通过用户组授权访问文档列表和详情。 |

验收门：

- 用户组可包含成员和部门。
- 用户组展开成员数量准确。
- 停用用户组不能被新增到资源授权。

## 6. ORG-M3 - 统一权限决策与解释

目标：让资源访问统一经过成员、部门、用户组、角色、继承规则计算。

Lark 化结果：用户从搜索、工作台、IM 卡片、文档、Base 或项目进入同一对象时，看到一致的权限状态和解释。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| ORG-M3-T01 | Done | `V036__extend_resource_permissions_for_unified_decisions.sql` 扩展 `resource_permissions`，新增 `source_type`、`source_id`、`expires_at`、`status`、更新人和更新时间，并回填文档现有授权。 |
| ORG-M3-T02 | Done | `PermissionDecisionService` 通过 `ResourcePermissionRepository` 计算资源最高权限，返回 allowed、currentLevel、requiredLevel、source 和 permissionId。 |
| ORG-M3-T03 | Done | 通用资源权限支持 `user`、`department`、`user_group`、`role` 主体；文档授权 API 已可写入部门和角色主体到通用 ACL。 |
| ORG-M3-T04 | Done | 统一等级比较支持 `owner`、`manage`、`edit`、`comment`、`view`，文档 comment 级别可被解释和校验。 |
| ORG-M3-T05 | Done | `/api/platform/objects/{type}/{id}/permission-explanation` 优先使用统一资源权限解释，返回来源主体名称和阻断原因。 |
| ORG-M3-T06 | Done | 文档访问优先接入统一资源权限；Base 创建和成员授权同步写入通用 ACL；项目事项继续以项目成员 resolver 解释作为兼容路径。 |
| ORG-M3-T07 | Done | 文档搜索过滤改为识别通用 ACL 的成员、部门、用户组和角色主体；无权限成员不会召回文档标题、摘要和正文。 |

验收门：

- 同一资源多来源授权取最高权限。
- 用户通过用户组获得文档 view 权限时，解释接口能显示来源用户组。
- 无权限对象在搜索结果中不可见或只返回不可泄露状态。


## 7. ORG-M4 - 角色权限产品化

目标：将现有 RBAC 从内置种子数据升级为可管理能力。

Lark 化结果：管理员可以在后台配置组织管理员、用户组管理员、权限管理员等角色，并把角色授予成员、部门或用户组。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| ORG-M4-T01 | Done | `V037__create_role_assignments_and_permission_metadata.sql` 为 `permissions` 增加 description、risk_level、is_builtin、display_order，并回填内置权限说明和风险等级。 |
| ORG-M4-T02 | Done | `V037` 新增 `role_assignments`，保留并回填 `user_roles`，支持角色分配给成员、部门、用户组。 |
| ORG-M4-T03 | Done | 新增 `RoleRepository`、`RoleService`、`RoleController`，提供角色创建/更新、权限替换、角色分配和撤销 API。 |
| ORG-M4-T04 | Done | 新增 `GET /api/admin/permissions` 权限码目录 API，返回模块、说明、风险等级和展示排序。 |
| ORG-M4-T05 | Done | 新增 `/admin/roles` 角色权限页面，支持角色列表、角色编辑、权限勾选、风险确认、成员/部门/用户组分配和撤销。 |
| ORG-M4-T06 | Done | 新增 `role.view`、`role.manage`、`permission.inspect` 权限码，并授予内置 admin 角色。 |
| ORG-M4-T07 | Done | 绑定高风险权限或分配高风险角色时后端强制 `confirmHighRisk`，并写入 `role.permissions.updated`、`role.assignment.created` 等审计日志。 |

验收门：

- 管理员可创建自定义角色并绑定权限码。
- 成员可通过部门/用户组继承角色权限。
- 修改角色权限后，用户重新请求接口时权限即时生效。


## 8. ORG-M5 - 资源权限管理界面

目标：让业务对象的权限可被管理员和对象 owner 自助管理。

Lark 化结果：文档、Base、项目等对象的 owner/manage 用户可以直接授予成员、部门、用户组权限，并理解继承来源。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| ORG-M5-T01 | Done | 文档、Base、项目详情入口接入通用 `ResourcePermissionsModal`，统一从业务对象进入权限管理。 |
| ORG-M5-T02 | Done | 通用资源权限面板复用成员、部门、用户组主体选择器，支持 active 用户组和部门树选择。 |
| ORG-M5-T03 | Done | `GET /api/resource-permissions/{resourceType}/{resourceId}` 展示 direct、inherited、revoked、expired 等授权状态和来源。 |
| ORG-M5-T04 | Done | `POST /api/resource-permissions/{resourceType}/{resourceId}` 支持新增/修改授权；`POST /api/resource-permissions/{permissionId}/revoke` 支持撤销 direct 授权。 |
| ORG-M5-T05 | Done | manage/owner 授权和撤销要求 `confirmHighRisk`；inherited 授权不可在子资源直接撤销。 |
| ORG-M5-T06 | Done | 资源权限授予和撤销写入 `resource.permission.granted`、`resource.permission.revoked` 审计日志。 |

验收门：

- 文档可授权给某部门 edit 权限。
- Base 可授权给某用户组 view 权限。
- 继承权限不可在子资源直接删除，只能展示来源并跳转。

## 9. ORG-M6 - 权限治理与风险巡检

目标：让管理员能发现和修复权限风险。

Lark 化结果：管理员能像企业管理后台一样排查“谁为什么能看某资源”，并发现停用主体仍有权限、公开范围过大、资源缺 owner 等风险。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| ORG-M6-T01 | Done | 新增 `/admin/permission-governance` 权限治理页，提供排查表单、风险列表和导出入口。 |
| ORG-M6-T02 | Done | 新增 `/api/admin/permission-governance/inspect`，支持输入用户、资源类型、资源 ID 和动作，返回 allowed、当前等级、需要等级、来源和原因。 |
| ORG-M6-T03 | Done | 新增风险规则：停用成员仍有权限、停用部门仍被授权、停用用户组仍被授权、资源无 owner、部门/用户组持有 manage/owner、管理员过多。 |
| ORG-M6-T04 | Done | 新增 `/api/admin/permission-governance/risks/export`，导出 CSV 风险列表。 |
| ORG-M6-T05 | Done | 审计日志页增加权限授予、权限撤销、角色权限快捷筛选，并支持 `permissionOnly=true` 默认进入资源权限事件。 |

验收门：

- 管理员可输入用户和资源，查看为什么有/没有权限。
- 系统能列出停用成员仍拥有权限的资源。
- 风险结果可导出。

## 10. 全局验收标准

1. 数据库迁移必须可从空库执行。
2. 新增权限码必须同步种子数据和权限说明。
3. 所有组织、用户组、角色、资源授权变更必须写审计日志。
4. 搜索结果不得泄露无权限对象标题、摘要或正文。
5. 非授权管理员不能访问组织、用户组、角色和权限治理接口。
6. 前端管理页必须展示明确的加载、空状态、错误状态和权限不足状态。
7. 每个里程碑至少补充后端集成测试；涉及页面的里程碑补充前端构建或局部 E2E 验证。
8. 至少接入一个真实业务对象链路验证权限一致性，不能只验证管理后台自身。

## 11. 下一步入口

ORG-M1 到 ORG-M6 已完成。下一轮应先进行阶段收口复核：确认组织架构、用户组、角色权限、资源权限管理、权限治理和审计链路是否满足类 Lark 协同平台的最小闭环，再决定是否进入新路线图。
