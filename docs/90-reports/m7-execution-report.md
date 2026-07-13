---
title: UI-SPLIT-M7 执行报告
status: archived
milestone: UI-SPLIT-M7
updated_at: 2026-07-05
---

# UI-SPLIT-M7 执行报告

## 本轮范围

UI-SPLIT-M7-T01 到 UI-SPLIT-M7-T08：管理后台 API facade 与权限治理迁移。

目标是在不破坏现有 `/api/admin/*` 路径和前端页面调用的前提下，把成员、组织架构、用户组、角色权限、权限治理和审计日志的响应契约改成后台管理语义 DTO。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M7-T01 | Done | 后台六类接口继续固定在 `/api/admin/*`，新增 `AdminIdentityDtos`、`AdminPermissionDtos`、`AdminAuditDtos` facade。 |
| UI-SPLIT-M7-T02 | Done | `AdminMemberView` 区分成员档案、登录账号、组织归属、管理状态和后台可操作项。 |
| UI-SPLIT-M7-T03 | Done | 用户组 DTO 区分成员展开、授权主体、治理状态、审计快照和展开来源。 |
| UI-SPLIT-M7-T04 | Done | 角色权限 DTO 区分角色分类、权限矩阵、内置角色、分配主体和可操作项。 |
| UI-SPLIT-M7-T05 | Done | 权限治理 DTO 增加风险、影响范围、建议动作和审计上下文。 |
| UI-SPLIT-M7-T06 | Done | 审计日志 DTO 增加操作者、对象、上下文、风险标签和快捷筛选。 |
| UI-SPLIT-M7-T07 | Done | 复用现有 permission service 后台守卫；目标测试覆盖普通 member 不能读取后台治理数据。 |
| UI-SPLIT-M7-T08 | Done | 后端目标集成测试覆盖 DTO 字段、权限拒绝和旧字段兼容。 |

## 代码变更

- `server/src/main/java/com/colla/platform/modules/identity/api/AdminIdentityDtos.java`
  - 新增后台身份治理 DTO 映射：成员、部门树、部门成员、负责人、用户组、用户组成员、展开成员。
- `server/src/main/java/com/colla/platform/modules/permission/api/AdminPermissionDtos.java`
  - 新增后台权限治理 DTO 映射：权限目录、角色摘要、角色详情、角色分配、权限排查、风险汇总。
- `server/src/main/java/com/colla/platform/modules/audit/api/AdminAuditDtos.java`
  - 新增后台审计 DTO 映射：actor、target、context、riskTag、quickFilters。
- `server/src/main/java/com/colla/platform/modules/identity/api/AdminUserController.java`
- `server/src/main/java/com/colla/platform/modules/identity/api/OrganizationController.java`
- `server/src/main/java/com/colla/platform/modules/identity/api/UserGroupController.java`
- `server/src/main/java/com/colla/platform/modules/permission/api/RoleController.java`
- `server/src/main/java/com/colla/platform/modules/permission/api/PermissionGovernanceController.java`
- `server/src/main/java/com/colla/platform/modules/audit/api/AuditController.java`
  - 控制器返回值从通用 domain summary/detail 切换到后台 `Admin*View`。
  - 原有顶层字段保留，确保旧页面和旧断言不回退。
- `web/src/modules/admin/api/*.ts`
  - 前端管理 API 类型新增 `Admin*View` 命名，并保留旧 `*Summary` / `*Entry` 类型别名。

## 测试变更

- `server/src/test/java/com/colla/platform/modules/identity/api/AdminUserControllerIntegrationTests.java`
- `server/src/test/java/com/colla/platform/modules/identity/api/OrganizationControllerIntegrationTests.java`
- `server/src/test/java/com/colla/platform/modules/identity/api/UserGroupControllerIntegrationTests.java`
- `server/src/test/java/com/colla/platform/modules/permission/api/RoleControllerIntegrationTests.java`
- `server/src/test/java/com/colla/platform/modules/permission/api/PermissionGovernanceControllerIntegrationTests.java`

新增断言覆盖：

- 旧字段仍存在：`id`、`code`、`name`、`status`、`permissionCodes` 等。
- 新后台语义字段存在：`profile`、`organization`、`governance`、`permissionMatrix`、`impactScope`、`riskTag` 等。
- 普通 member 直接访问后台成员、组织、用户组、角色接口被拒绝。

## 验证

- `mvn -DskipTests compile`：通过。
- `mvn "-Dtest=AdminUserControllerIntegrationTests,OrganizationControllerIntegrationTests,UserGroupControllerIntegrationTests,RoleControllerIntegrationTests,PermissionGovernanceControllerIntegrationTests" test`：通过，18 个测试。
- `pnpm web:lint`：通过；仍有既有 `useDocumentCollaboration.ts` 3 个 Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。
- 浏览器冒烟：本轮主要为 API facade/DTO 契约迁移，未改页面交互路径；不执行浏览器冒烟。

## 剩余风险

- 本轮保留旧顶层字段做兼容，没有强制前端页面立即只读取嵌套 `Admin*View` 字段；后续可在页面层逐步切换展示语义。
- `/api/resource-permissions` 仍同时服务用户协作和后台治理，需要 M11/M12 继续拆用户侧分享视图和后台治理视图。
- 后台知识库/Base 治理 facade 未纳入 M7 范围，仍按 M9/M11 处理。

## 下一步

进入 UI-SPLIT-M8：用户侧 API facade 与内容主路径瘦身。重点把用户工作台、知识库、Base、项目、消息和通知的响应收敛到用户协作视角，避免夹带后台治理字段。
