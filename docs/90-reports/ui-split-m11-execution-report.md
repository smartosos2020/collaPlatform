---
title: UI-SPLIT-M11 执行报告
status: archived
milestone: UI-SPLIT-M11
updated_at: 2026-07-06
---

# UI-SPLIT-M11 执行报告

## 本轮范围

UI-SPLIT-M11-T01 到 UI-SPLIT-M11-T08：权限、审计、搜索、通知和平台对象链接的跨边界规则收口。

目标是让用户工作台和管理后台虽然共享同一后端单体和基础服务，但横切能力不再混淆用户协作视角和后台治理视角。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M11-T01 | Done | `PermissionActionCategory` 将动作归类为 user_action、object_management、space_management、admin_management、super_admin。 |
| UI-SPLIT-M11-T02 | Done | `RequestBoundaryContext` 识别来源 UI 和 API 面，`AuditService` 写入 `sourceUi`、`apiSurface`、`client`、`requestPath`。 |
| UI-SPLIT-M11-T03 | Done | `/api/search` 固定用户内容搜索；新增 `/api/admin/search-governance` 作为后台治理搜索 facade。 |
| UI-SPLIT-M11-T04 | Done | 通知 DTO 增加 `notificationScope`；个人通知列表和未读数排除后台治理通知。 |
| UI-SPLIT-M11-T05 | Done | 平台对象新增 `card` 接口，支持 user/admin 展示上下文和上下文动作。 |
| UI-SPLIT-M11-T06 | Done | 权限解释增加展示上下文、动作建议和后台策略来源细节。 |
| UI-SPLIT-M11-T07 | Done | `CrossBoundaryRulesIntegrationTests` 覆盖后台 API 越权、搜索泄露、对象卡链接泄露、通知隔离和审计来源。 |
| UI-SPLIT-M11-T08 | Done | 当前路线图、产品范围和架构文档已同步 M11 边界。 |

## 代码变更

- `server/src/main/java/com/colla/platform/shared/request/RequestBoundaryContext.java`
  - 新增请求边界上下文，按路径、`X-Colla-Client` 和 `X-Colla-Ui` 推断 `sourceUi` 与 `apiSurface`。
- `server/src/main/java/com/colla/platform/config/RequestLoggingFilter.java`
  - 每个 HTTP 请求绑定并清理 `RequestBoundaryContext`。
- `server/src/main/java/com/colla/platform/config/SecurityConfig.java`
  - CORS 允许 `X-Colla-Ui`。
- `server/src/main/java/com/colla/platform/modules/audit/application/AuditService.java`
  - 审计 metadata 增加来源 UI、API 面、客户端和请求路径。
- `server/src/main/java/com/colla/platform/modules/audit/api/AdminAuditDtos.java`
  - 管理审计 DTO 暴露 `sourceUi`、`apiSurface` 和扩展 context。
- `server/src/main/java/com/colla/platform/modules/permission/domain/PermissionModels.java`
  - 新增权限动作分类和更完整的权限解释字段。
- `server/src/main/java/com/colla/platform/modules/permission/application/PermissionService.java`
  - 新增动作分类逻辑。
- `server/src/main/java/com/colla/platform/modules/permission/application/PermissionDecisionService.java`
  - 权限解释返回分类、展示上下文和行动建议。
- `server/src/main/java/com/colla/platform/modules/platform/domain/PlatformModels.java`
  - 新增 `PlatformObjectCard` 和 `PlatformObjectAction`。
- `server/src/main/java/com/colla/platform/modules/platform/application/PlatformObjectService.java`
  - 新增 user/admin 对象卡；admin 动作只对具备后台权限用户返回。
- `server/src/main/java/com/colla/platform/modules/platform/api/PlatformObjectController.java`
  - 新增 `/api/platform/objects/{type}/{id}/card`，权限解释支持 `context`。
- `server/src/main/java/com/colla/platform/modules/search/domain/SearchModels.java`
  - 搜索响应增加 `searchScope`，新增后台治理搜索响应模型。
- `server/src/main/java/com/colla/platform/modules/search/application/SearchService.java`
  - 用户搜索限制为协作对象；后台治理搜索走独立方法并要求后台权限。
- `server/src/main/java/com/colla/platform/modules/search/api/AdminSearchGovernanceController.java`
  - 新增 `/api/admin/search-governance`。
- `server/src/main/java/com/colla/platform/modules/notification/domain/NotificationModels.java`
  - 通知项增加 `notificationScope`。
- `server/src/main/java/com/colla/platform/modules/notification/infrastructure/JdbcNotificationRepository.java`
  - 用户通知列表和未读数过滤后台治理通知。
- `server/src/main/java/com/colla/platform/modules/notification/api/UserNotificationDtos.java`
  - 用户通知 DTO 暴露 `notificationScope`。
- `web/src/modules/search/api/searchApi.ts`
  - 前端搜索响应声明 `searchScope=user_content`。
- `web/src/modules/platform/api/platformObjectsApi.ts`
  - 前端平台对象 API 声明对象卡、动作和增强权限解释字段。
- `web/src/modules/notifications/api/notificationsApi.ts`
  - 前端通知 DTO 声明 `notificationScope`。
- `web/src/shared/api/apiBoundary.ts`
  - 补充 `/admin/search-governance` 后台治理 API 分类。
- `server/src/test/java/com/colla/platform/modules/platform/api/CrossBoundaryRulesIntegrationTests.java`
  - 新增 M11 跨边界集成测试。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M11-T01 到 T08 标记完成，并把下一步入口推进到 UI-SPLIT-M12。 |
| `docs/00-product/current-product-scope.md` | 固定搜索、审计、通知和平台对象卡的用户/后台边界。 |
| `docs/01-architecture/current-architecture.md` | 同步 API 边界、权限分类、后台治理搜索和横切能力约束。 |
| `docs/90-reports/ui-split-m11-execution-report.md` | 新增本报告。 |

## 验证

- `mvn -f server/pom.xml -DskipTests compile`：通过。
- `mvn -f server/pom.xml -Dtest="CrossBoundaryRulesIntegrationTests" test`：通过。
- `mvn -f server/pom.xml -Dtest="CrossBoundaryRulesIntegrationTests,PermissionDecisionIntegrationTests,PlatformObjectControllerIntegrationTests,SearchCollaborationIntegrationTests" test`：通过，共 6 个测试。
- `pnpm web:lint`：通过；保留既有 `web/src/modules/docs/hooks/useDocumentCollaboration.ts` 3 个 React Hook dependency warning，非本轮新增。
- `pnpm web:build`：通过。

## 遗留 Gap

- 完整 `mvn test`、完整集成测试和全量迁移验证按当前 AI 工作循环策略后置到 UI-SPLIT-M12 阶段收口。
- `/api/search/reindex` 仍在用户搜索前缀下但受管理权限保护，M12 需要在兼容清理清单中判断是否迁入 `/api/admin/*`。
- 后台治理搜索当前是治理入口 catalog，不是全文搜索后台对象明细；后续如扩展审计、权限风险、应用治理全文检索，应继续保持 `/api/admin/search-governance` 或其他 admin facade。

## 下一步

进入 UI-SPLIT-M12：兼容清理、全量验证和双 UI v1 冻结。
