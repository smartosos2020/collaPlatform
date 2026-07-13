---
title: UI-SPLIT-M8 执行报告
milestone: UI-SPLIT-M8
status: archived
date: 2026-07-05
---

# UI-SPLIT-M8 执行报告

## 范围

UI-SPLIT-M8-T01 到 UI-SPLIT-M8-T08：用户侧 API facade 与内容主路径瘦身。

目标是在不拆仓库、不物理拆服务的前提下，把用户工作台、知识库、Base、项目、消息和通知的默认响应收敛到用户协作视角，避免用户侧 DTO 继续夹带后台治理、审计和组织管理语义。

## 任务结果

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M8-T01 | Done | 新增 `UserWorkspaceDtos`、`UserKnowledgeDtos`、`UserBaseDtos`、`UserProjectDtos`、`UserImDtos`、`UserNotificationDtos`，为用户工作台首页、知识库、Base、项目、消息和通知建立 user view facade。 |
| UI-SPLIT-M8-T02 | Done | `KnowledgeBaseSpaceController` 默认用户侧空间、目录、内容、发现页响应切到 `UserKnowledge*View`；评论、分享、版本和对象入口仍保留，治理端点仍在显式 governance/admin 路径。 |
| UI-SPLIT-M8-T03 | Done | `BaseController` 列表、详情、授权、记录查询响应切到 `UserBase*View`，保留表、视图、记录、字段和协作权限提示。 |
| UI-SPLIT-M8-T04 | Done | `ProjectController` 项目、事项列表和事项详情响应切到 `UserProject*View`/`UserIssue*View`，保留事项状态、成员协作、会话关联和用户可执行动作。 |
| UI-SPLIT-M8-T05 | Done | `ImController` 与 `NotificationController` 响应切到 `UserConversation*View`、`UserMessage*View`、`UserNotificationView`，保留提醒、跳转和协作动作。 |
| UI-SPLIT-M8-T06 | Done | 知识库/Base 用户视图新增行动导向权限解释：可管理、可编辑、可评论、可查看、可申请权限。 |
| UI-SPLIT-M8-T07 | Done | 前端 `workspaceApi`、`knowledgeBasesApi`、`docsApi`、`basesApi`、`projectsApi`、`messengerApi`、`notificationsApi` 迁移到 user view 类型，同时保留旧类型别名和字段兼容。 |
| UI-SPLIT-M8-T08 | Done | 后端编译、定向 API 测试、前端 lint/build 和用户侧浏览器冒烟通过。 |

## 主要改动

| 文件 | 变化 |
| --- | --- |
| `server/src/main/java/com/colla/platform/modules/workspace/api/UserWorkspaceDtos.java` | 新增工作台用户视图，保留近期对象、收藏、通知和导航摘要。 |
| `server/src/main/java/com/colla/platform/modules/doc/api/UserKnowledgeDtos.java` | 新增知识库用户视图，封装空间导航、内容入口、协作权限和可用动作。 |
| `server/src/main/java/com/colla/platform/modules/base/api/UserBaseDtos.java` | 新增 Base 用户视图，封装协作权限、表视图摘要和记录页提示。 |
| `server/src/main/java/com/colla/platform/modules/project/api/UserProjectDtos.java` | 新增项目/事项用户视图，封装成员协作、会话关联和事项动作。 |
| `server/src/main/java/com/colla/platform/modules/im/api/UserImDtos.java` | 新增会话/消息用户视图，封装提醒状态和协作动作。 |
| `server/src/main/java/com/colla/platform/modules/notification/api/UserNotificationDtos.java` | 新增通知用户视图，封装未读提醒和跳转动作。 |
| `server/src/main/java/com/colla/platform/modules/*/api/*Controller.java` | 用户侧 Controller 返回 user view facade，显式 admin/governance 端点不混入用户主路径。 |
| `web/src/modules/*/api/*.ts` | 用户侧 API 类型迁移到 user view DTO，旧类型作为兼容别名保留。 |
| `server/src/test/java/com/colla/platform/modules/*/api/*IntegrationTests.java` | 增加用户视图字段断言，覆盖知识库、Base、项目、IM、通知和工作台。 |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M8-T01 到 T08 标记完成，下一步指向 UI-SPLIT-M9。 |
| `docs/90-reports/m8-execution-report.md` | 本报告覆盖旧 KB-UX M8 报告，避免当前路线误读。 |

## 验证

- `mvn -DskipTests compile`：通过。
- `mvn -Dtest="DocumentControllerIntegrationTests#knowledgeBaseSpaceProductLayerKeepsLegacyDocumentTreeCompatibility+knowledgeBaseItemApisMirrorLegacyDocumentCompatibility+knowledgeSearchDiscoverySubscriptionAndAclFlow,BaseControllerIntegrationTests#baseFieldRecordViewAndShareCardFlow,ProjectControllerIntegrationTests#projectIssueTransitionCommentMentionAndNotificationFlow,ImControllerIntegrationTests#conversationMessageMentionLinkAndUnreadFlow,WorkspaceControllerIntegrationTests#dashboardNavigationNotificationAndCrossModuleFlow" test`：通过，7 个测试，0 失败。
- `pnpm web:lint`：通过；仍有既有 `useDocumentCollaboration.ts` React hooks 依赖 warning 3 条。
- `pnpm web:build`：通过。
- 用户侧浏览器冒烟：通过。使用 `admin / admin123456` 登录本地 `http://127.0.0.1:5173/login`，打开工作台、知识库、表格、项目、消息、通知 6 条用户侧路径，页面均加载到有效内容；控制台仅出现既有 Ant Design `destroyOnClose` 弃用提示。

## 剩余风险

- URL 前缀尚未物理拆分为 `/api/user/*` 与 `/api/admin/*`，当前阶段采用 facade/DTO 语义拆分，物理兼容清理留到 UI-SPLIT-M12。
- `/api/docs` 仍承担编辑器兼容底座和 deep link 能力；后续不得在该前缀新增后台治理能力。
- 知识库治理端点仍在知识库 Controller 内，但只通过显式 governance 路径访问；UI-SPLIT-M9 需要迁移到后台/设置视角。
- 前端仍保留旧类型别名，便于兼容；后续清理阶段要逐步删除旧 DTO 语义。

## 下一步

进入 UI-SPLIT-M9：知识库治理从用户页迁移到后台/设置，重点清理用户知识库页面的治理入口、统计噪音和后台化动作。
