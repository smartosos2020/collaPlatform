---
title: UI-SPLIT-M12 执行报告
status: completed
milestone: UI-SPLIT-M12
updated_at: 2026-07-06
---

# UI-SPLIT-M12 执行报告

## 本轮范围

UI-SPLIT-M12-T01 到 UI-SPLIT-M12-T08：兼容清理、全量验证和双 UI v1 冻结。

目标是把 UI-SPLIT 路线从“拆出用户工作台和管理后台”收束为可解释、可验证、可继续演进的 v1 边界：用户工作台承载真实协作，管理后台承载组织、权限、安全、应用、内容治理和审计。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| UI-SPLIT-M12-T01 | Done | 本报告输出旧路由、旧菜单、旧 API 和旧 DTO 字段兼容清理清单。 |
| UI-SPLIT-M12-T02 | Done | 本报告记录兼容项保留原因、调用方、删除条件和风险。 |
| UI-SPLIT-M12-T03 | Done | 新增 `pnpm smoke:ui-split`，覆盖用户侧工作台、消息、项目、知识库、表格、审批、通知、搜索主入口。 |
| UI-SPLIT-M12-T04 | Done | `pnpm smoke:ui-split` 同时覆盖管理后台企业概览、组织架构、成员管理、用户组、角色权限、权限治理、知识库治理、审计日志主入口。 |
| UI-SPLIT-M12-T05 | Done | route-final 执行完整后端测试、后端打包、前端 lint/build、安全扫描、迁移顺序和文档契约。 |
| UI-SPLIT-M12-T06 | Done | 当前路线图、产品范围、架构、平台对象模型、AI 工程治理和本报告已同步。 |
| UI-SPLIT-M12-T07 | Done | 本报告冻结“双 UI v1”验收结论，并列出二期能力。 |
| UI-SPLIT-M12-T08 | Done | 当前路线图标记完成，下一步入口改为提交/归档或新路线图编排。 |

## 代码变更

- `web/e2e/ui-split-v1-smoke.spec.ts`
  - 新增双 UI v1 浏览器冒烟：API 登录后访问用户工作台和管理后台主入口，验证 Shell 隔离、后台入口、返回工作台和 5xx API 响应。
- `scripts/ui-split-v1-browser-smoke.ps1`
  - 新增 Playwright 冒烟包装脚本，默认使用 `admin/admin123456` 或 `COLLA_E2E_PASSWORD`。
- `package.json`
  - 新增 `pnpm smoke:ui-split`。
- `server/src/main/java/com/colla/platform/modules/event/application/DomainEventWorker.java`
  - route-final 首次暴露事件队列积压问题：单次手动处理只 claim 20 条 pending event，导致全量测试中当前通知事件可能被历史事件挤到下一批。
  - 改为单次调度最多 drain 100 个 batch，每批 100 条，保留上限避免异常循环，同时让测试和运维手动 flush pending events 的语义更稳定。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/02-roadmap/current-roadmap.md` | UI-SPLIT-M12-T01 到 T08 标记完成，当前路线图进入收口状态。 |
| `docs/00-product/current-product-scope.md` | 固定双 UI v1 产品边界、兼容保留项和二期方向。 |
| `docs/01-architecture/current-architecture.md` | 同步兼容清理决策、验证资产和最近一次 route-final 结果。 |
| `docs/01-architecture/platform-object-model.md` | 同步 platform object card、user/admin presentation context 和搜索/权限解释边界。 |
| `docs/03-engineering/ai-engineering-governance.md` | 同步 `pnpm smoke:ui-split` 作为双 UI 变更的局部冒烟脚本。 |
| `docs/90-reports/m12-execution-report.md` | 新增本报告。 |

## 兼容清理决策

| 兼容项 | 当前决策 | 调用方/原因 | 删除条件 | 风险 |
| --- | --- | --- | --- | --- |
| `/docs` 根路由 | 保留重定向到 `/knowledge-bases` | 历史入口、浏览器收藏、旧搜索跳转 | 外部链接和旧导航全部迁移，访问日志连续一个试运行周期无有效命中 | 过早删除会造成旧链接 404 |
| `/docs/:docId` | 保留为知识内容 deep link 兼容入口 | IM 卡片、通知、搜索结果、分享链接、历史 deep link | 所有平台对象、通知、搜索和分享链接都改为 `/knowledge-bases/:spaceId/items/:docId`，并提供迁移重定向策略 | 用户可能绕过知识库空间上下文 |
| `/api/docs/*` | 保留为知识内容编辑器底座和历史 API | `DocsPage`、blocks、评论、版本、分享、导入导出、协同 | 新增 user/admin facade 覆盖所有调用方，并完成旧 API 调用审计 | 继续扩展会重新形成独立“文档模块”包袱 |
| `Document*` DTO 和 `document` objectType | 保留为内部兼容命名 | 平台对象、搜索、通知、IM 链接和数据库表仍依赖 | 需要单独的数据/对象类型迁移方案，且 full route-final 通过 | 误读为独立产品模块 |
| `documents.content` | 保留为兼容快照和回滚字段 | 导出、旧链接、全文检索、旧版本回滚仍依赖 | active blocks 覆盖率稳定为 100%，导出/搜索/评论不依赖旧字段，并有回滚演练 | 继续写入会增加双写复杂度 |
| `/api/knowledge-bases/{spaceId}/governance*` | 保留兼容但冻结新增 | 历史知识库治理调用和测试 | 后台 `/api/admin/knowledge-bases/*` 调用方完全覆盖，旧调用无生产流量 | 用户侧误消费治理 DTO |
| `/api/search/reindex` | 暂保留，列入 M12 后续清理候选 | 当前在用户搜索前缀下但受管理权限保护 | 迁移到 `/api/admin/search-governance` 或专用 `/api/admin/search/reindex` facade | 路径语义容易被误认为用户 API |
| `web/src/modules/docs` | 保留为知识内容编辑器底座 | `/knowledge-bases/:spaceId/items/:docId` 复用编辑器、评论、版本和分享 | 完成知识内容编辑器目录重命名和导入路径迁移 | 包名容易误导为独立产品模块 |
| `AdminModuleNav` 旧组件 | 删除 | 后台导航已归入 `AdminConsoleShell` 和 `adminConsoleNav` | 已满足；不再恢复页面内二级顶部按钮作为后台主导航 | 页面局部按钮若复活会破坏后台 IA |
| 用户侧左侧“管理”菜单 | 删除，不保留 | Lark 化要求后台入口只在头像菜单底部 | 已满足；仅具备后台权限用户看到头像菜单中的管理后台入口 | 恢复后会再次混淆用户/后台 UI |

## 双 UI v1 冻结结论

| 维度 | v1 冻结规则 |
| --- | --- |
| 用户工作台 | 左侧主菜单只放工作台、消息、项目、知识库、表格、审批、通知、搜索；后台入口只在头像菜单底部出现。 |
| 管理后台 | `/admin/*` 使用独立 Shell；菜单覆盖企业概览、组织架构、成员管理、用户组、角色权限、权限治理、知识库治理、应用治理、审计日志。 |
| 后端边界 | 当前不拆服务；通过 URL、DTO、权限和 facade 区分用户协作 API、管理治理 API 和共享平台 API。 |
| 横切能力 | 搜索、通知、审计、平台对象卡、权限解释必须携带 user/admin 或 source/scope 边界语义。 |
| 兼容策略 | 旧文档入口和旧 API 可保留，但只作为知识内容编辑底座或历史 deep link，不再扩展为独立产品模块。 |

## 验证

- `pnpm smoke:ui-split`：通过。
- `mvn -f server/pom.xml -Dtest="DeviceControllerIntegrationTests,WorkspaceControllerIntegrationTests,ProjectControllerIntegrationTests,DocumentControllerIntegrationTests" test`：通过，27 个测试，验证事件队列和通知相关修复。
- `pnpm work:finish -Goal "UI-SPLIT-M12" -TaskRange "UI-SPLIT-M12-T01 到 UI-SPLIT-M12-T08" -ValidationProfile route-final`：通过。
- route-final 覆盖完整 `mvn test` 74 个测试、Testcontainers PostgreSQL 空库应用 43 个 Flyway 迁移到 V043、后端 package、前端 lint/build、安全扫描、Flyway 迁移顺序、文档结构和工作循环契约。
- `pnpm web:lint` 仍保留既有 `web/src/modules/docs/hooks/useDocumentCollaboration.ts` 3 个 React Hook dependency warning，非本轮新增。

route-final 失败记录：第一次失败为 `DeviceControllerIntegrationTests.multiClientDevicePushDeepLinkAndCatchUpFlow` 在全量测试顺序下未读通知为空；后续补文档后复跑暴露 `DocumentControllerIntegrationTests.knowledgeSearchDiscoverySubscriptionAndAclFlow` 订阅更新通知为空。共同原因是事件 worker 单次处理 pending event 上限偏低，历史测试事件积压会延后当前测试通知事件。修复 `DomainEventWorker` 批量 drain 后，相关目标测试和 route-final 均通过。

## 二期方向

- 后台治理搜索从入口 catalog 扩展为审计、权限风险、应用治理和知识库治理的全文检索。
- `/api/search/reindex` 迁入 admin facade。
- `web/src/modules/docs` 和 `Document*` 命名迁移为知识内容编辑器语义，降低后续会话误读。
- 管理后台补系统设置、安全策略中心、应用市场治理和更细的企业审计报表。
- 用户工作台补更完整的个人设置、个人资料卡、通知策略和跨对象关系图谱。

## 遗留 Gap

- 本轮没有物理拆分后端服务和前端仓库；当前冻结的是单体内的产品/路由/API/facade 边界。
- 浏览器冒烟是主路径渲染与边界冒烟，不替代真实多人试运行。
- 兼容项删除需要单独路线图，不能在双 UI v1 冻结时一次性删除。

## 下一步

- 当前 UI-SPLIT 路线已完成；下一步应提交/推送，或归档当前路线图后编排新的阶段路线图。
