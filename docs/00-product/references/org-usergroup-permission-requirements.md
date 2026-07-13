---
title: 组织架构、用户组与权限治理需求
status: reference
last_code_check: 2026-07-12
scope: identity-permission
source_rule: 本文是组织架构、用户组与权限治理的产品需求；实施任务以 docs/02-roadmap/current-roadmap.md 为准。
remote_rule: 本文件位于 docs/，纳入版本管理并随项目同步到远程仓库。
---

# 组织架构、用户组与权限治理需求

> 实施参考：ORG-M1 到 ORG-M6 已完成，本文保留原始需求和验收语义，不再代表“待实施计划”。当前实现与剩余 Gap 以产品范围、当前架构和代码为准。

## 1. 立项时背景与现状

本节记录 ORG 路线启动时的历史基线，其中“没有”“缺少”和旧字段描述不代表 2026-07-12 当前实现。

当前项目已具备基础身份与权限能力：

- `users` 表已有成员账号、邮箱、状态、设备、会话等基础字段，并有一个字符串型 `department` 字段。
- 已有 `roles`、`permissions`、`role_permissions`、`user_roles` 表，支持系统角色和权限码。
- 已有 `resource_permissions` 表，支持资源级授权主体与权限等级。
- 后台已有成员列表、新增成员、禁用/启用成员、重置密码。
- 权限服务目前主要覆盖 `admin.access`、`user.manage` 等少量后台能力。

当前主要缺口：

- 真实部门树、部门负责人、成员主/兼部门关系已在 ORG-M1 落地，后续需要继续接入通讯录可见性、导入导出和资源授权主体。
- 没有用户组，无法把跨部门项目组、职能组、权限组作为统一授权主体。
- 角色管理、权限码管理和成员授权还没有后台产品化。
- 资源权限可存储，但缺少统一授权策略、继承规则、冲突解释和批量治理能力。
- 搜索、工作台、文档、Base、审批、项目等模块没有完整依赖组织/用户组进行权限控制。

当前实现状态：

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| ORG-M1 组织架构数据底座 | 已完成 | 后台可维护部门树、部门成员、主部门和负责人；成员列表可展示/筛选部门；组织变更写审计。 |
| ORG-M2 用户组能力 | 已完成 | 后台可维护跨部门用户组和权限组；知识内容授权主体选择器支持 active 用户组。 |
| ORG-M3 至 ORG-M6 权限治理 | 已完成 | 部门、用户组和角色已接入统一资源权限决策、解释、治理和审计能力；后续缺口以当前产品范围为准。 |

## 2. 产品目标

目标是补齐类 Lark 协同平台的企业组织、成员分组与权限治理底座，为 IM、知识内容、多维表格、审批、项目、工作台、搜索等模块提供统一授权基础。

业务目标：

- 建立企业组织架构，支持部门层级、成员归属、部门负责人和组织可见范围。
- 建立用户组能力，支持跨部门成员集合复用到权限、通知、应用、审批、文档等场景。
- 建立统一权限治理能力，支持系统角色、模块权限、资源权限、继承权限、权限解释和审计。
- 让管理员能在后台完成日常组织与权限管理，不依赖数据库操作。

非目标：

- 不做 SSO、LDAP、企业微信/飞书组织同步。
- 不做外部租户互联、跨企业组织架构。
- 不做零信任设备准入、DLP、敏感词审计等高级安全能力。
- 不做复杂审批组织角色，如矩阵上级、虚线上级、动态汇报线。

## 3. 类 Lark 对齐原则

本需求不是通用 IAM/RBAC 后台，而是类 Lark 协同平台的组织和权限底座。判断是否“像 Lark”，以以下产品原则为准：

| 原则 | Lark 化含义 | 本项目落点 |
| --- | --- | --- |
| 企业先于个人 | 用户进入的是企业工作空间，权限、通讯录、资源都以租户为边界 | 所有组织、用户组、角色、资源授权保留 `workspace_id` |
| 组织架构是协同入口 | 部门不只是成员字段，而是通讯录、审批、权限、通知、搜索的基础主体 | 建立部门树、主/兼部门、部门负责人、组织可见性 |
| 用户组是跨部门协作单元 | 项目组、职能组、权限组可复用于文档、Base、项目、通知、工作台 | 建立用户组，并允许成员/部门作为组成员 |
| 权限要能解释 | Lark 类产品必须告诉用户“为什么能看/为什么不能看”，避免黑盒权限 | 统一权限决策与权限解释接口，返回来源、等级和阻断原因 |
| 授权对象要统一 | 文档、Base、项目、审批等不能各自做一套权限主体 | 成员、部门、用户组、角色成为统一授权主体 |
| 管理动作可审计 | 企业后台所有组织和权限变更必须可追溯 | 部门、用户组、角色、资源授权变更写审计日志 |
| 搜索不得泄露 | 全局搜索是 Lark 类平台核心入口，但不能泄露无权限对象 | 搜索召回和摘要展示必须经过统一权限决策 |
| 工作台按权限聚合 | 工作台展示的是“我能处理/能访问”的事项、审批、文档和表格 | Dashboard、最近对象、收藏对象、待办都应接入统一权限 |

## 4. 用户角色

| 角色 | 说明 | 关键权限 |
| --- | --- | --- |
| 超级管理员 | 租户最高权限管理员 | 管理所有部门、成员、用户组、角色、权限策略 |
| 组织管理员 | 负责组织架构维护 | 管理部门、成员归属、部门负责人 |
| 用户组管理员 | 负责用户组维护 | 创建用户组、维护成员、设置用户组管理员 |
| 权限管理员 | 负责角色和授权策略 | 管理角色、权限码、资源授权、权限排查 |
| 部门负责人 | 管理本部门成员和部分资源 | 查看本部门成员、参与部门权限继承和审批规则 |
| 普通成员 | 企业成员 | 查看授权范围内通讯录和资源 |

## 5. 功能范围

| 编号 | 模块 | 范围 |
| --- | --- | --- |
| ORG | 组织架构 | 部门树、部门成员、部门负责人、成员部门关系、组织可见性 |
| UG | 用户组 | 用户组创建、成员维护、嵌套限制、授权主体、管理员 |
| RBAC | 角色权限 | 角色、权限码、角色授权、成员/用户组/部门角色分配 |
| ACL | 资源权限 | 资源授权、权限等级、继承、冲突处理、权限解释 |
| GOV | 治理审计 | 权限变更审计、风险提示、权限排查、批量操作 |

## 6. 组织架构需求

### 6.1 部门模型

- FR-ORG-001：系统应支持租户内创建多级部门树。
- FR-ORG-002：部门应包含名称、编码、父部门、负责人、排序、状态、创建/更新时间。
- FR-ORG-003：部门编码在同一租户内应唯一，用于导入、集成和权限策略引用。
- FR-ORG-004：系统应支持移动部门到新的父部门，并校验不能形成循环引用。
- FR-ORG-005：部门停用后，默认不可再加入新成员，但历史成员关系保留。
- FR-ORG-006：删除部门前必须满足无子部门、无有效成员、无绑定权限策略，或由管理员选择迁移成员和策略。

### 6.2 成员部门关系

- FR-ORG-007：成员应支持一个主部门和多个兼部门。
- FR-ORG-008：成员列表应支持按部门、状态、角色、关键词筛选。
- FR-ORG-009：新增成员时应可选择所属主部门、角色和初始密码。
- FR-ORG-010：管理员应可批量调整成员部门。
- FR-ORG-011：成员离职/禁用时，应保留其历史部门归属，用于审计和历史对象展示。
- FR-ORG-012：部门负责人应可配置多名，负责人可以是本部门成员，也可以由超级管理员允许跨部门指定。

### 6.3 通讯录与可见性

- FR-ORG-013：普通成员应可查看自己有权限可见的部门树和成员。
- FR-ORG-014：管理员应可配置组织架构可见范围，例如全员可见、仅本部门可见、按部门/用户组白名单可见。
- FR-ORG-015：成员搜索应遵循通讯录可见性，不返回不可见成员的敏感信息。
- FR-ORG-016：成员名片应展示姓名、账号、邮箱、部门、角色、状态等基础信息。

### 6.4 导入导出

- FR-ORG-017：管理员应可通过 CSV 导入部门和成员部门关系。
- FR-ORG-018：导入前应提供校验结果，包括重复编码、父部门不存在、成员不存在、循环层级等错误。
- FR-ORG-019：管理员应可导出部门树和成员清单。

## 7. 用户组需求

### 7.1 用户组模型

- FR-UG-001：系统应支持创建用户组，用户组归属于租户。
- FR-UG-002：用户组应包含名称、编码、描述、状态、可见范围、管理员、创建/更新时间。
- FR-UG-003：用户组编码在同一租户内应唯一。
- FR-UG-004：用户组应支持普通组和权限组两类用途；权限组可作为资源授权主体。
- FR-UG-005：用户组停用后，不再作为新授权主体，但历史授权应进入风险检查清单。

### 7.2 用户组成员

- FR-UG-006：用户组成员主体应支持成员、部门。
- FR-UG-007：用户组暂不支持组嵌套，避免权限计算复杂度和循环依赖。
- FR-UG-008：当部门作为用户组成员时，应动态包含该部门当前有效成员。
- FR-UG-009：管理员应可批量添加、移除用户组成员。
- FR-UG-010：用户组详情应展示直接成员、部门来源成员和最终展开成员数量。

### 7.3 用户组管理权限

- FR-UG-011：超级管理员可管理所有用户组。
- FR-UG-012：用户组管理员可管理自己负责的用户组成员和基础信息。
- FR-UG-013：用户组管理员不得提升自己权限，不能把用户组授权到自己无权管理的资源。

### 7.4 用户组使用场景

- FR-UG-014：文档、知识库、多维表格、项目、审批、工作台应用、通知订阅应支持选择用户组作为授权对象。
- FR-UG-015：搜索权限过滤应能识别用户组带来的资源访问权限。
- FR-UG-016：权限解释应说明访问来自哪个用户组、用户组中哪个成员关系。

## 8. 角色与权限需求

### 8.1 权限码

- FR-RBAC-001：系统应维护权限码清单，权限码由模块、动作和资源范围组成。
- FR-RBAC-002：权限码应包含编码、名称、模块、说明、风险等级、是否内置。
- FR-RBAC-003：内置权限码不可删除，但可调整展示名称和说明。
- FR-RBAC-004：权限码应覆盖后台管理、组织管理、用户组管理、角色管理、文档管理、Base 管理、项目管理、审批管理、搜索重建、审计查看等能力。

建议新增权限码：

| 权限码 | 模块 | 说明 |
| --- | --- | --- |
| `org.view` | identity | 查看组织架构 |
| `org.manage` | identity | 管理部门和成员部门关系 |
| `usergroup.view` | identity | 查看用户组 |
| `usergroup.manage` | identity | 管理用户组 |
| `role.view` | permission | 查看角色和权限 |
| `role.manage` | permission | 管理角色和权限分配 |
| `permission.inspect` | permission | 查看权限解释和权限排查 |
| `resource.grant` | permission | 管理资源级授权 |
| `audit.view` | audit | 查看审计日志 |
| `audit.export` | audit | 导出审计日志 |

### 8.2 角色

- FR-RBAC-005：系统应支持内置角色和自定义角色。
- FR-RBAC-006：角色应包含编码、名称、范围、权限码集合、状态、是否内置。
- FR-RBAC-007：内置角色不可删除，可由系统迁移升级。
- FR-RBAC-008：自定义角色可绑定给成员、部门、用户组。
- FR-RBAC-009：角色范围至少包括系统级、组织级、模块级、资源级。
- FR-RBAC-010：角色变更后，用户的权限集合应及时生效。

建议内置角色：

| 角色 | 范围 | 说明 |
| --- | --- | --- |
| `super_admin` | system | 全部管理权限 |
| `org_admin` | organization | 管理部门、成员部门关系 |
| `user_admin` | identity | 管理成员账号 |
| `usergroup_admin` | identity | 管理用户组 |
| `permission_admin` | permission | 管理角色、权限和资源授权 |
| `audit_admin` | audit | 查看和导出审计 |
| `member` | system | 普通成员 |

### 8.3 角色分配

- FR-RBAC-011：管理员应可给成员分配角色。
- FR-RBAC-012：管理员应可给部门分配角色，部门内有效成员继承该角色。
- FR-RBAC-013：管理员应可给用户组分配角色，用户组最终展开成员继承该角色。
- FR-RBAC-014：角色分配应支持生效时间和失效时间。
- FR-RBAC-015：高风险角色分配应二次确认，并写入审计日志。

## 9. 资源权限治理需求

### 9.1 授权主体

- FR-ACL-001：资源授权主体应支持成员、部门、用户组、系统角色。
- FR-ACL-002：授权主体应统一使用 `subject_type` 和 `subject_id` 表达。
- FR-ACL-003：资源权限应支持 `owner`、`manage`、`edit`、`comment`、`view` 等等级。
- FR-ACL-004：不同资源类型可定义自己支持的权限等级。

### 9.2 权限来源与继承

- FR-ACL-005：资源权限应记录来源类型，例如直接授权、父级继承、部门继承、用户组继承、角色权限、所有者权限。
- FR-ACL-006：文档树、知识库、项目、Base 可定义父级继承规则。
- FR-ACL-007：资源可选择是否继承父级权限；关闭继承应写入审计日志。
- FR-ACL-008：当用户通过多个来源获得权限时，最终权限取最高等级。
- FR-ACL-009：当资源显式拒绝某主体访问时，应优先于继承权限；MVP 可暂不实现拒绝规则。

### 9.3 权限解释

- FR-ACL-010：系统应提供权限解释接口，说明用户是否可访问资源、当前权限等级、所需权限等级、权限来源。
- FR-ACL-011：权限解释应能显示来自成员直接授权、部门、用户组、角色、所有者或继承。
- FR-ACL-012：搜索结果中返回的 `accessState` 应由统一权限决策生成。
- FR-ACL-013：无权限资源不得泄露标题、摘要、正文、成员列表等敏感信息。

### 9.4 权限管理界面

- FR-ACL-014：资源详情页应提供“权限管理”入口。
- FR-ACL-015：管理员应可添加、修改、移除成员/部门/用户组的资源权限。
- FR-ACL-016：权限管理界面应展示直接授权和继承授权。
- FR-ACL-017：继承授权不可直接删除，只能跳转到来源对象调整。
- FR-ACL-018：高风险操作，例如开放给全员、赋予管理权限、关闭继承，应二次确认。

## 10. 权限审计与治理

- FR-GOV-001：部门创建、移动、删除、停用应写入审计日志。
- FR-GOV-002：成员部门调整、负责人变更应写入审计日志。
- FR-GOV-003：用户组创建、成员变更、停用应写入审计日志。
- FR-GOV-004：角色创建、权限变更、角色分配应写入审计日志。
- FR-GOV-005：资源授权新增、修改、删除、继承开关变更应写入审计日志。
- FR-GOV-006：审计日志应支持按操作者、目标类型、目标 ID、动作、时间范围筛选。
- FR-GOV-007：权限治理页应提供风险清单，例如离职成员仍为 owner、停用部门仍被授权、公开范围过大、管理员过多。
- FR-GOV-008：权限治理页应支持导出风险清单。

## 11. 数据模型建议

建议新增核心表：

- `departments`
- `department_members`
- `department_managers`
- `user_groups`
- `user_group_members`
- `role_assignments`
- `permission_catalog_metadata` 或扩展 `permissions`
- `resource_permission_sources` 或扩展 `resource_permissions`

关键字段：

| 表 | 关键字段 |
| --- | --- |
| `departments` | `id`, `workspace_id`, `parent_id`, `code`, `name`, `path`, `depth`, `sort_order`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_at` |
| `department_members` | `id`, `workspace_id`, `department_id`, `user_id`, `relation_type`, `started_at`, `ended_at`, `created_by`, `created_at` |
| `department_managers` | `id`, `workspace_id`, `department_id`, `user_id`, `manager_type`, `created_by`, `created_at` |
| `user_groups` | `id`, `workspace_id`, `code`, `name`, `description`, `group_type`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at` |
| `user_group_members` | `id`, `workspace_id`, `group_id`, `subject_type`, `subject_id`, `created_by`, `created_at` |
| `role_assignments` | `id`, `workspace_id`, `role_id`, `subject_type`, `subject_id`, `scope_type`, `scope_id`, `effective_at`, `expires_at`, `created_by`, `created_at` |

## 12. API 建议

组织架构 API：

- `GET /api/admin/departments/tree`
- `POST /api/admin/departments`
- `PATCH /api/admin/departments/{departmentId}`
- `POST /api/admin/departments/{departmentId}/move`
- `POST /api/admin/departments/{departmentId}/disable`
- `DELETE /api/admin/departments/{departmentId}`
- `GET /api/admin/departments/{departmentId}/members`
- `POST /api/admin/departments/{departmentId}/members`
- `DELETE /api/admin/departments/{departmentId}/members/{userId}`
- `POST /api/admin/departments/{departmentId}/managers`
- `DELETE /api/admin/departments/{departmentId}/managers/{userId}`

用户组 API：

- `GET /api/admin/user-groups`
- `POST /api/admin/user-groups`
- `GET /api/admin/user-groups/{groupId}`
- `PATCH /api/admin/user-groups/{groupId}`
- `POST /api/admin/user-groups/{groupId}/disable`
- `DELETE /api/admin/user-groups/{groupId}`
- `GET /api/admin/user-groups/{groupId}/members`
- `POST /api/admin/user-groups/{groupId}/members`
- `DELETE /api/admin/user-groups/{groupId}/members/{memberId}`
- `GET /api/admin/user-groups/{groupId}/expanded-members`

角色与权限 API：

- `GET /api/admin/roles`
- `POST /api/admin/roles`
- `GET /api/admin/roles/{roleId}`
- `PATCH /api/admin/roles/{roleId}`
- `POST /api/admin/roles/{roleId}/permissions`
- `DELETE /api/admin/roles/{roleId}/permissions/{permissionCode}`
- `GET /api/admin/permissions`
- `GET /api/admin/role-assignments`
- `POST /api/admin/role-assignments`
- `DELETE /api/admin/role-assignments/{assignmentId}`

权限治理 API：

- `GET /api/admin/permissions/explain`
- `GET /api/admin/permissions/risks`
- `GET /api/admin/permissions/subjects/resolve`
- `GET /api/platform/objects/{type}/{id}/permissions`
- `POST /api/platform/objects/{type}/{id}/permissions`
- `PATCH /api/platform/objects/{type}/{id}/permissions/{permissionId}`
- `DELETE /api/platform/objects/{type}/{id}/permissions/{permissionId}`

## 13. 前端页面建议

后台管理应拆分为：

- 成员管理
- 组织架构
- 用户组
- 角色权限
- 权限治理
- 审计日志

页面形态：

| 页面 | 建议布局 | 核心操作 |
| --- | --- | --- |
| 组织架构 | 左侧部门树，中间成员表格，右侧部门信息抽屉 | 新建/编辑/移动/停用部门，添加成员，设置主部门，设置负责人 |
| 用户组 | 用户组列表，详情抽屉，成员来源表 | 新建用户组，添加成员或部门，设置管理员，查看被授权资源 |
| 角色权限 | 角色列表，权限矩阵，角色分配列表 | 创建自定义角色，勾选权限码，分配角色 |
| 权限治理 | 权限排查工具，高风险授权清单 | 输入用户 + 资源排查权限，跳转资源授权，导出风险结果 |

## 14. 验收标准

组织架构：

- 管理员可创建三层以上部门树。
- 成员可被设置主部门和兼部门。
- 移动部门时不能形成循环。
- 部门负责人变更后，审计日志可查。
- 成员搜索结果遵循组织可见性。

用户组：

- 管理员可创建用户组，并添加成员和部门。
- 部门作为用户组成员时，展开成员随部门成员变化自动更新。
- 用户组可作为文档、Base、项目等资源授权主体。
- 用户组停用后，新授权主体选择器不再展示该组。

角色权限：

- 自定义角色可绑定权限码。
- 成员通过直接角色、部门角色、用户组角色获得的权限均可生效。
- 高风险角色分配写入审计。

资源权限：

- 文档/Base/项目对象可授权给成员、部门、用户组。
- 同一用户多来源权限取最高权限等级。
- 权限解释接口能说明最终权限来源。
- 搜索结果不泄露无权限资源。

类 Lark 体验：

- 管理员能在一个后台内完成成员、部门、用户组、角色和权限治理，不需要进入各业务模块逐个维护。
- 普通成员看到的通讯录、搜索结果、工作台聚合内容和对象卡片都与其组织/用户组/角色权限一致。
- 从 IM、文档、Base、项目、审批进入同一对象时，权限判断结果一致，并能解释来源。
