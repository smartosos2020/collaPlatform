---
title: 类 Lark 知识库 v1 完善与打磨路线图（已完成归档）
status: archived
scope: docs-knowledge-base
last_code_check: 2026-06-29
archived_at: 2026-07-01
archived_reason: KB-M1 到 KB-M8 已完成，当前执行路线切换为知识库唯一入口与文档模块去冗余。
source_rule: 本路线图只作历史追溯，不作为当前执行依据。
remote_rule: 本文件位于 docs/，按 .gitignore 保持本地文档，不进入远程仓库。
---

# 类 Lark 知识库完善与打磨唯一路线图

本文定义 Colla Platform 文档模块从“较完整的类 Lark 文档协作底座”继续完善为“相对完整的类 Lark 知识库”的当前唯一执行路线。后续 AI 工作循环、里程碑拆分、验收和返工都以本文为准。

本路线基于当前代码、`docs/00-product/current-product-scope.md`、`docs/01-architecture/current-architecture.md` 和已归档的 Lark 文档改造路线判断。

已完成路线归档：组织架构、用户组与权限治理 ORG-M1 到 ORG-M6 已完成，旧路线已归档到 `docs/99-archive/superseded-roadmaps/org-usergroup-permission-roadmap-completed-2026-06-29.md`。

## 1. 当前基线

当前文档模块已经具备知识库底座所需的大部分基础能力：

1. 文档节点已有 `space`、`folder`、`markdown` 三类，支持团队空间、树形目录、路径面包屑、移动、排序、归档和恢复。
2. `space` 已可作为知识库入口，`documents` 已有 `description`、`cover_url`、`default_permission_level`、`knowledge_base` 字段。
3. 文档编辑已经从 Markdown 文本域升级为 Tiptap/ProseMirror 富文本体验，并有 block v2、对象卡片、文件卡和 Base/项目/消息等跨模块嵌入。
4. 单节点文档协同已有 `snapshot-v1`、presence、远端状态、自动保存和手动检查点版本。
5. 评论已有全文、块、选区、线程、回复、resolve/reopen、提及通知和 deep link。
6. 文档权限已支持 `owner/manage/edit/comment/view`，并接入统一资源 ACL，主体包括成员、部门、用户组和角色。
7. 分享链接、权限申请、继承权限展示、权限解释和审计已经有基础链路。
8. 文档模板、导入导出、版本 diff/restore、收藏、最近访问、搜索索引和跨模块关系已经具备。

核心判断：当前不是从零建设知识库，而是在已有文档协作底座上做知识库产品化、空间治理、导航体验、内容运营和验收收口。

## 2. 目标形态

目标是相对完整的类 Lark 知识库：

1. 用户能在“知识库”入口看到多个知识库空间，而不是只在文档树里识别 `space` 节点。
2. 每个知识库有首页、封面、描述、维护人、导航目录、置顶内容、最近更新、常用模板和设置入口。
3. 知识库有空间级成员、管理员、默认权限、继承策略和权限解释。
4. 文档、目录、知识库空间之间的权限继承、打断继承、授权来源和风险提示清晰可见。
5. 搜索能按知识库、目录、标签、维护人、更新时间、文档状态过滤，并且不泄露无权限内容。
6. 知识库支持知识维护流程，包括负责人、标签、有效期、认证状态、过期提醒、无人维护排查和批量治理。
7. 知识库与 IM、项目、Base、审批、文件保持对象卡片、反向引用、通知和权限态一致。

不采用的方向：

- 不重建第二套编辑器或第二套文档存储。
- 不把知识库做成脱离 `documents`、`document_blocks`、统一 ACL 和平台对象体系的孤岛。
- 不先做复杂外部公开站点；优先完成组织内知识库闭环，外部访问只预留接口边界。
- 不在缺少权限继承解释和搜索 ACL 的情况下扩大知识库召回范围。

## 3. 执行顺序

推荐顺序：

1. KB-M1：知识库空间产品层。
2. KB-M2：知识库首页与导航体验。
3. KB-M3：知识库权限、继承和访问申请。
4. KB-M4：知识内容生产与维护。
5. KB-M5：知识发现、搜索和推荐。
6. KB-M6：协作体验与跨模块联动打磨。
7. KB-M7：知识治理、统计和审计。
8. KB-M8：迁移、验收和试运行收口。

排序原因：

- 先明确知识库空间边界，否则首页、权限、统计和治理都缺少聚合对象。
- 首页和导航是用户感知最强的产品层，应早于高级治理。
- 权限继承和搜索 ACL 必须在内容扩散前收口。
- 治理和验收必须基于真实知识库空间、内容和访问链路验证。

## 4. 类 Lark 验收口径

| 口径 | 验收要求 |
| --- | --- |
| 知识库空间 | 知识库是可管理的空间对象，有封面、描述、管理员、默认权限、首页和状态。 |
| 目录导航 | 用户能在知识库内通过目录、面包屑、置顶、最近更新和全文搜索找到内容。 |
| 权限继承 | 空间、目录、文档权限来源清晰；继承、直授、打断继承和申请访问都可解释。 |
| 内容维护 | 文档有维护人、标签、状态、有效期或复核时间，知识过期和无人维护可被发现。 |
| 协作闭环 | 评论、提及、分享、收藏、最近访问、版本和跨模块引用在知识库场景下可用。 |
| 搜索安全 | 无权限用户不能通过搜索、对象卡片、链接预览或最近访问看到不可访问内容。 |
| 治理审计 | 管理员能看到知识库权限风险、内容健康、访问事件和关键操作审计。 |
| 试运行可用 | 3-5 人小团队能完成创建知识库、沉淀文档、授权、搜索、评论和治理闭环。 |

## 5. KB-M1 - 知识库空间产品层

目标：把当前 `space + knowledge_base` 从文档节点标记升级为清晰的知识库空间产品层。

Lark 化结果：用户能看到“知识库列表/空间切换/空间设置”，而不是只能在文档树里创建一个空间节点。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M1-T01 | Done | 已确认旧入口仍基于 `documents.doc_type='space'` 与 `knowledge_base=true`；新 API 采用 `knowledge_base_spaces` 聚合表并以原 document 作为 root/home。 |
| KB-M1-T02 | Done | 新增轻量 `KnowledgeBaseSpaceSummary/Detail` 和 `knowledge_base_spaces`，记录 name、code、description、icon、cover、status、visibility、rootDocumentId、homeDocumentId、ownerId、defaultPermissionLevel。 |
| KB-M1-T03 | Done | `V038__create_knowledge_base_spaces.sql` 将既有 knowledge base space 映射为知识库空间；列表 API 也会补登记旧 API 新建的 knowledge base space。 |
| KB-M1-T04 | Done | 新增 `/api/knowledge-bases` 列表、详情、创建、编辑、停用、恢复、归档和 DELETE 软归档 API。 |
| KB-M1-T05 | Done | 新增 `/knowledge-bases` 前端入口页，展示知识库卡片、状态、维护人、节点数、默认权限、进入根文档和进入首页按钮。 |
| KB-M1-T06 | Done | 保留 `/docs` 与 `/docs/{id}` 原文档树；新知识库 root 仍是 `space` 文档，旧链接和对象卡片继续指向 document。 |
| KB-M1-T07 | Done | 知识库创建、编辑、停用、恢复、归档写入 `knowledge_base.*` 审计动作。 |
| KB-M1-T08 | Done | `DocumentControllerIntegrationTests` 覆盖旧 space 自动补登记、空间创建、停用后普通成员禁止新建、管理员治理、归档/恢复和旧文档树兼容。 |

验收门：

- 已有知识库入口不丢失，旧 `/docs/{id}` 链接仍可打开。
- 用户能从知识库列表进入某个知识库空间。
- 停用知识库后，普通成员不能继续创建新内容，管理员仍可恢复或治理。

## 6. KB-M2 - 知识库首页与导航体验

目标：让知识库具备清晰的信息架构、首页和阅读导航。

Lark 化结果：每个知识库都有可感知的首页和目录，而不是只显示一棵普通文档树。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M2-T01 | Done | 新建知识库会自动创建 `首页` markdown 文档并登记为 `homeDocumentId`；空间设置支持指定同知识库内文档为首页，PATCH 不再因省略首页字段回退到 root。 |
| KB-M2-T02 | Done | 新增 `/knowledge-bases/:spaceId` 工作台，顶部展示封面、名称、描述、维护人、状态、可见性、默认权限和文档/目录/归档/收藏统计。 |
| KB-M2-T03 | Done | 工作台左侧提供知识库空间切换、当前知识库目录树、目录内搜索和按空间 `localStorage` 记忆展开状态。 |
| KB-M2-T04 | Done | 工作台首页区展示当前首页、置顶入口、最近更新、常用目录、收藏内容和模板快捷入口。 |
| KB-M2-T05 | Done | 目录节点展示空间/目录/文档类型、权限态、归档态、收藏态和最近更新时间。 |
| KB-M2-T06 | Done | 知识库内提供新建文档、新建文件夹、从模板创建，默认落到当前选中的目录或根目录。 |
| KB-M2-T07 | Done | 知识库内支持操作式移动、上移、下移和设为首页，继续复用现有 `/api/docs/{documentId}/move` 与排序语义。 |
| KB-M2-T08 | Done | 新增响应式样式：桌面端采用固定侧栏 + 内容工作台，窄屏下侧栏和内容区改为纵向独立滚动。 |

验收门：

- 从知识库列表进入后，首屏能明确识别当前知识库、首页和目录。
- 用户可在知识库内完成创建、移动、排序和打开文档。
- 旧文档树能力没有回退。

## 7. KB-M3 - 知识库权限、继承和访问申请

目标：把空间级权限、目录继承、文档直授和访问申请做成可解释闭环。

Lark 化结果：owner/manage 用户能理解“谁因为什么能看/编辑这个知识库或文档”，普通用户能申请访问。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M3-T01 | Done | 知识库空间权限统一映射到 `owner/manage/edit/comment/view`，前端显示为拥有者、可管理、可编辑、可评论、可查看，后端复用统一 ACL 等级排序。 |
| KB-M3-T02 | Done | 资源权限管理支持成员、部门、用户组、角色四类主体；权限列表返回并展示 `expandedMemberCount`，部门/用户组/角色按成员展开计数。 |
| KB-M3-T03 | Done | `knowledge_base` 已纳入 `resource_permissions` 管理类型；空间授权写入知识库资源，并递归传播为文档树 inherited 授权，保留旧文档 ACL 和 `/docs/{id}` 兼容。 |
| KB-M3-T04 | Done | 权限弹窗展示直授、继承、来源 ID、过期/撤销/有效状态、主体详情和展开人数；文档面板继续展示继承来源标题。 |
| KB-M3-T05 | Done | 新增 `/resource-permissions/{resourceType}/{resourceId}/inheritance/break|restore`，断开继承需要二次确认并写审计，恢复从父目录或知识库根授权重建 inherited 权限。 |
| KB-M3-T06 | Done | 新增 `resource_permission_requests`、访问申请创建/列表/批准/拒绝 API；知识库 403 页面可提交申请，审批通过后写入资源权限并通知申请人。 |
| KB-M3-T07 | Done | 文档分享链接响应补充 `knowledgeBaseId/name/code`，前端分享链接行显示所属知识库、组织内链接状态、链接权限和过期时间。 |
| KB-M3-T08 | Done | 搜索继续基于文档 `resource_permissions` 过滤；知识库授权会同步到文档树，平台最近访问/收藏过滤不可访问对象，链接预览仍走 resolver ACL。 |

验收门：

- 用户通过部门或用户组获得知识库权限时，权限解释能显示来源。
- 子文档能展示继承自哪个知识库或目录。
- 无权限用户能提交访问申请，管理员能收到并处理。

## 8. KB-M4 - 知识内容生产与维护

目标：让知识库不仅能写文档，还能持续维护高质量知识。

Lark 化结果：知识条目有负责人、标签、状态和复核机制，模板能支撑常见知识沉淀场景。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M4-T01 | Done | `documents` 增加 `maintainer_id`、`tags`、`category`、`knowledge_status`、`review_due_at`、`verified_at`、`review_notified_at`，文档摘要/API 返回维护元数据。 |
| KB-M4-T02 | Done | 文档详情信息区新增“知识元数据”面板，展示并可编辑维护人、标签、分类、知识状态、复核日期和认证时间。 |
| KB-M4-T03 | Done | 模板表增加 `scope_type`、`knowledge_base_id`，`/docs/templates?knowledgeBaseId=` 合并返回全局、工作区和知识库级模板，知识库模板要求 manage 权限。 |
| KB-M4-T04 | Done | 内置 FAQ、SOP、故障复盘、项目复盘模板，并支持通过 `/docs/templates` 创建工作区或知识库自定义模板。 |
| KB-M4-T05 | Done | 复用文档目标目录能力和既有 IM 转文档、文档转项目事项、Base/审批/消息关系沉淀链路，知识库模板与导入接口可选择目标知识库目录。 |
| KB-M4-T06 | Done | 新增知识库 Markdown 批量导入和目录 Markdown 导出接口，导入时写入标签、分类、状态等知识元数据。 |
| KB-M4-T07 | Done | 新增复核提醒执行接口，按 `review_due_at` 找到到期知识，通知维护人并将已认证文档置为 `needs_review`。 |
| KB-M4-T08 | Done | `DocumentControllerIntegrationTests` 覆盖知识元数据、知识库模板、批量导入导出和复核提醒；`pnpm web:lint`、`pnpm web:build` 和文档详情浏览器冒烟通过。 |

验收门：

- 新建知识条目可以选择模板并落到指定知识库目录。
- 文档维护人、标签、状态可以被编辑并用于后续搜索过滤。
- 过期知识能被系统识别并提醒。

## 9. KB-M5 - 知识发现、搜索和推荐

目标：让用户能在大量知识中快速找到正确内容。

Lark 化结果：搜索不是全局粗搜，而是具备知识库范围、目录、标签、负责人和更新时间过滤。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M5-T01 | Done | `/api/search` 支持 `knowledgeBaseId`、`directoryId`、`docType`、`tags`、`maintainerId`、`knowledgeStatus`、`updatedFrom/updatedTo`，知识库详情页提供同维度筛选。 |
| KB-M5-T02 | Done | `search_index_documents` 扩展 `knowledge_base_id`、`parent_document_id`、`directory_path`、`tags`、`maintainer_id`、`knowledge_status`、`doc_type`、`hit_source` 并补索引。 |
| KB-M5-T03 | Done | 搜索结果返回并展示命中来源、目录路径、标签、维护人、知识状态和权限态；正文块/评论命中继续跳转到块或评论锚点。 |
| KB-M5-T04 | Done | 知识库首页新增 discovery API，返回最近访问、我的收藏、我维护的文档、待复核文档，并在前端首页展示。 |
| KB-M5-T05 | Done | 新增 `knowledge_subscriptions`，支持关注知识库或目录/文档；文档更新后向订阅者产生 `knowledge_subscription_updated` 通知。 |
| KB-M5-T06 | Done | discovery API 基于收藏、最近访问、更新时间、标签相似和当前 ACL 生成热门知识与推荐阅读。 |
| KB-M5-T07 | Done | 知识库内搜索无结果时提供创建文档、扩大范围和清除筛选入口，避免高噪音空态。 |
| KB-M5-T08 | Done | `DocumentControllerIntegrationTests#knowledgeSearchDiscoverySubscriptionAndAclFlow` 覆盖知识库搜索筛选、ACL 不泄露、discovery、关注和更新通知。 |

验收门：

- 用户能在某个知识库内按标签和目录找到文档。
- 无权限文档不会出现在搜索结果或推荐列表。
- 最近访问、收藏和待复核列表只展示当前用户可访问内容。

## 10. KB-M6 - 协作体验与跨模块联动打磨

目标：让知识库中的协作体验接近类 Lark 工作流。

Lark 化结果：用户能围绕知识内容评论、提及、订阅、转任务、嵌入对象，并在其他模块回看引用。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M6-T01 | Done | 文档评论面板支持未解决、提及我、当前选区、全部评论过滤，当前选区评论继续优先排序。 |
| KB-M6-T02 | Done | `DocumentDetail` 新增 `knowledgeContext`；评论提及通知标题和链接带知识库空间、文档路径和评论锚点。 |
| KB-M6-T03 | Done | 选区转任务弹窗支持项目、负责人、截止时间；后端在事项描述和事项关系中保留知识库反向引用。 |
| KB-M6-T04 | Done | 统一对象卡片展示权限态、来源模块、更新时间、知识路径和反向引用入口；文档/事项摘要补齐 metadata。 |
| KB-M6-T05 | Done | 知识库详情页展示选中文档协同健康状态，异常时提供刷新状态和打开文档重连路径。 |
| KB-M6-T06 | Done | 执行报告记录 `snapshot-v1` 单节点边界，并规划 Redis pub/sub 与 Yjs update 的升级切入点。 |
| KB-M6-T07 | Done | 文档页补齐窄屏快捷入口：目录、评论、分享；只读/评论模式给出明确恢复和权限路径。 |
| KB-M6-T08 | Done | `DocumentControllerIntegrationTests#knowledgeCollaborationContextMentionIssueAndObjectCardFlow` 覆盖评论提及、转任务反向引用和对象卡片权限态；浏览器烟测覆盖评论筛选、协同健康和对象卡片展示。 |

验收门：

- 被提及用户能从通知进入具体知识库文档和评论位置。
- 知识库文档能自然转任务，并在项目事项中回看来源。
- 协同异常不会导致内容静默丢失。

## 11. KB-M7 - 知识治理、统计和审计

目标：让管理员和知识库 owner 能治理内容质量与权限风险。

Lark 化结果：知识库不是文档堆积，而是能持续运营、审计和治理的知识资产。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M7-T01 | Done | 新增 `/api/knowledge-bases/{spaceId}/governance`，返回文档数、活跃文档、过期/待复核文档、无人维护文档、无 owner 文档和高风险权限数。 |
| KB-M7-T02 | Done | 知识库详情页增加治理面板，展示健康度指标、风险列表、访问统计、处理入口和治理报告导出。 |
| KB-M7-T03 | Done | 新增 `/api/knowledge-bases/{spaceId}/governance/bulk`，支持批量转移维护人、追加/替换标签、批量归档和批量发起复核。 |
| KB-M7-T04 | Done | 权限治理 `/api/admin/permission-governance/risks` 支持 `knowledgeBaseId` 筛选，覆盖停用主体授权、无 owner 和高风险范围规则。 |
| KB-M7-T05 | Done | 审计日志页增加知识库设置、知识库成员、权限继承变更和批量治理快捷筛选；批量治理写入 `knowledge_base.governance.bulk_updated`。 |
| KB-M7-T06 | Done | 治理面板返回访问人数、访问次数、热门文档、低访问文档；知识库搜索无结果时写入审计并聚合无结果词。 |
| KB-M7-T07 | Done | 新增 `/api/knowledge-bases/{spaceId}/governance/export`，导出健康、风险、热门文档和无结果词 CSV。 |
| KB-M7-T08 | Done | `DocumentControllerIntegrationTests#knowledgeGovernanceMetricsBulkAuditAndPermissionRiskFlow` 覆盖停用主体、无人维护、过期知识、权限过宽、批量治理、CSV 和审计。 |

验收门：

- 管理员能看到某知识库的内容健康和权限风险。
- owner 能批量处理过期或无人维护文档。
- 关键治理动作可在审计日志追溯。

## 12. KB-M8 - 迁移、验收和试运行收口

目标：完成从现有文档空间到知识库产品层的平滑迁移，并通过真实场景验收。

Lark 化结果：小团队能把知识库作为日常知识沉淀、查找、协作和治理入口。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-M8-T01 | Done | 新增 `scripts/knowledge-base-migration-check.ps1`，检查旧 space 映射、孤立文档、owner 权限、继承来源、搜索索引缺口和索引上下文。 |
| KB-M8-T02 | Done | 迁移检查脚本输出 `.local-reports/kb-migration-rollback-template-*.sql`，采用备份后人工确认、软归档知识库空间、保留权限和搜索历史的可回滚流程。 |
| KB-M8-T03 | Done | `docs/00-product/current-product-scope.md` 明确知识库 v1 已实现范围、试运行/迁移边界和剩余增强项。 |
| KB-M8-T04 | Done | `docs/01-architecture/current-architecture.md` 补充知识库 V038-V041 迁移、权限继承、搜索索引、治理统计和 M8 本地收口脚本边界。 |
| KB-M8-T05 | Done | 新增 `scripts/knowledge-base-acceptance-report.ps1`，生成本地知识库 v1 验收报告，覆盖空间、导航、权限、搜索、协作、治理和迁移。 |
| KB-M8-T06 | Done | 新增 `scripts/knowledge-base-trial-runbook.ps1`，输出 3-5 人小团队试运行剧本和 CSV checklist，覆盖创建、SOP、分享、评论、搜索和过期治理。 |
| KB-M8-T07 | Done | 执行目标集成测试、前端 lint/build、工作循环 checkpoint/finish；Flyway 空库迁移和搜索 ACL 回归由 `DocumentControllerIntegrationTests` 与质量门禁覆盖。 |
| KB-M8-T08 | Done | `docs/90-reports/m8-execution-report.md` 记录完成能力、验证结果、`GO-WITH-REVIEW` 迁移探测限制、回滚方案和 v1 冻结标准。 |

验收门：

- 旧文档空间迁移后内容、链接、权限和搜索不丢失。
- 试运行剧本能覆盖知识库核心闭环。
- 质量门禁通过后，知识库 v1 可冻结为后续增强基线。

## 13. 全局验收标准

1. 数据库迁移必须可从空库执行，并提供旧数据兼容路径。
2. 所有新权限码、资源类型和审计事件必须同步说明和测试。
3. 文档、目录、知识库空间的权限解释必须一致，不允许模块间判断冲突。
4. 搜索、推荐、最近访问、收藏、对象卡片和链接预览不得泄露无权限内容。
5. 知识库停用、归档、恢复和删除必须有审计和可恢复策略。
6. 前端必须覆盖加载、空状态、错误、权限不足、无搜索结果和协同异常状态。
7. 每个里程碑至少补充后端目标测试；涉及页面或交互的里程碑补充前端构建或浏览器验证。
8. 不新增第二份 active 路线图；当前执行入口始终是 `docs/02-roadmap/current-roadmap.md`。

## 14. 下一步入口

从 KB-M1 开始执行，第一轮应推进 KB-M1-T01 到 KB-M1-T08。执行前必须先读取：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/02-roadmap/current-roadmap.md`
5. `docs/03-engineering/ai-engineering-governance.md`

阶段验证策略沿用 AI 工作循环：每轮优先做本轮影响范围内的目标测试、lint/build 或浏览器验证；完整 `mvn test`、完整集成测试和全量迁移验证放在阶段收口里程碑执行。
