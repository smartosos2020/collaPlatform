---
title: 知识库唯一入口与文档模块去冗余唯一路线图
status: archived
scope: knowledge-base-consolidation
last_code_check: 2026-07-02
source_rule: 本路线图是当前唯一执行路线图；历史路线图只作追溯，不作为执行依据。
remote_rule: 本文件位于 docs/，按 .gitignore 保持本地文档，不进入远程仓库。
---

# 知识库唯一入口与文档模块去冗余唯一路线图

本文定义 Colla Platform 从“知识库 + 兼容文档模块并存”收口为“知识库是唯一产品入口，文档能力只是知识库内容能力”的当前唯一执行路线。后续 AI 工作循环、里程碑拆分、验收和返工都以本文为准。

已完成路线归档：类 Lark 知识库 v1 KB-M1 到 KB-M8 已完成，旧路线已归档到 `docs/99-archive/superseded-roadmaps/knowledge-base-v1-roadmap-completed-2026-07-01.md`。

## 1. 核心判断

当前知识库 v1 已经具备空间、首页、导航、权限、搜索、协作、治理和迁移收口能力，但系统仍夹带旧“文档模块”设计包袱：

1. 产品导航仍存在独立“文档”入口。
2. `/docs` 仍是一个独立页面，用户可以绕过知识库工作台直接进入全局文档树。
3. `/api/docs` 仍承担产品 API 语义，知识库页面大量调用文档 API。
4. `documents.doc_type='space'` 与 `documents.knowledge_base` 仍表达旧知识库入口。
5. `documents.description`、`cover_url`、`default_permission_level` 与 `knowledge_base_spaces` 字段语义重复。
6. `document_permissions` 与通用 `resource_permissions` 形成历史权限双轨。
7. 搜索、通知、对象卡片、工作台、IM、项目等仍把 `document` 暴露为一等产品对象。

唯一方向：知识库成为唯一产品主语；文档只作为知识库中的内容节点、编辑器能力和平台对象类型存在。用户侧不再有独立文档模块；开发侧逐步把文档 API、字段、权限和路由降级为兼容层，新增能力只能进入知识库语义。

不采用的方向：

- 不立即物理删除 `documents` 表；它仍承载内容节点、编辑器、版本、评论、块和对象引用。
- 不一次性删除 `/docs/{id}` deep link；它必须先重定向或嵌入知识库上下文，避免 IM、搜索、通知和历史对象卡片断链。
- 不在调用迁移完成前删除 `document_permissions` 或 `documents.knowledge_base` 等字段。
- 不新增第二套编辑器、评论、版本或协同实现。

## 2. 目标形态

完成后系统应满足：

1. 主导航只有“知识库”，没有独立“文档”一级入口。
2. 用户从知识库列表进入空间，从空间工作台浏览目录、打开内容页、编辑、评论、分享和治理。
3. `/docs/:id` 只作为历史 deep link 和内部编辑路由存在；可自动定位到所属知识库并给出知识库上下文。
4. 新建、移动、归档、恢复、模板、导入导出、权限、搜索、评论、通知都以知识库语义呈现。
5. `knowledge_base_spaces` 是知识库空间唯一主模型。
6. `documents` 只表达知识库内容节点，不再承担知识库空间元数据。
7. 权限主链路使用通用 `resource_permissions`；旧 `document_permissions` 只保留兼容迁移期。
8. 对外文案逐步从“文档模块”改为“知识库/目录/知识条目/内容页”。

## 3. 执行顺序

推荐顺序：

1. KB-CLEAN-M1：产品入口与路由收口。
2. KB-CLEAN-M2：知识库内容页承载文档编辑能力。
3. KB-CLEAN-M3：API 语义从 docs 迁移到 knowledge-bases。
4. KB-CLEAN-M4：数据模型去冗余与字段降级。
5. KB-CLEAN-M5：权限双轨收口。
6. KB-CLEAN-M6：搜索、通知、对象卡片和跨模块语义统一。
7. KB-CLEAN-M7：文案、文档和开发边界收口。
8. KB-CLEAN-M8：兼容清理、迁移验证和冻结。

排序原因：

- 先收用户入口，避免继续产生“文档模块”认知。
- 再收页面承载，让知识库工作台成为唯一业务入口。
- API 和数据清理必须在调用迁移后进行，否则会破坏旧链接和跨模块对象。
- 权限、搜索、通知和对象卡片依赖广，必须在主链路稳定后分阶段收口。

## 4. 类 Lark 验收口径

| 口径 | 验收要求 |
| --- | --- |
| 唯一入口 | 用户侧主导航只出现知识库，不再出现独立文档模块。 |
| 内容归属 | 所有内容页必须能解释所属知识库、目录路径和权限来源。 |
| API 语义 | 新增知识库相关能力不再扩展 `/api/docs`，而进入 `/api/knowledge-bases` 或知识库内容 API。 |
| 数据主模型 | 知识库空间元数据只来自 `knowledge_base_spaces`，不再依赖 `documents.knowledge_base`。 |
| 权限主链路 | 知识库和内容节点统一走 `resource_permissions`，旧权限表不再作为新功能依据。 |
| 跨模块一致 | 搜索、通知、IM 卡片、项目引用和工作台都以知识库语境展示内容。 |
| 历史兼容 | 旧 `/docs/:id` 链接、对象卡片、通知和搜索 deep link 不失效。 |
| 可回滚 | 字段删除或 API 废弃必须有迁移检查、回滚脚本和阶段报告。 |

## 5. KB-CLEAN-M1 - 产品入口与路由收口

目标：从用户侧移除独立文档模块，把知识库设为唯一入口。

Lark 化结果：用户进入的是知识库空间与知识内容，而不是一个全局文档树。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M1-T01 | Done | `AppLayout` 移除主导航独立“文档”入口；移动导航原本只保留知识库入口，本轮继续保持知识库为唯一内容入口。 |
| KB-CLEAN-M1-T02 | Done | `web/src/app/router.tsx` 将 `/docs` 根路径改为重定向 `/knowledge-bases`，不再提供全局文档树用户入口。 |
| KB-CLEAN-M1-T03 | Done | `/docs/:docId` deep link 继续进入兼容编辑页；`DocsPage` 在有 `knowledgeContext` 时顶部展示“返回知识库”主操作和知识库路径。 |
| KB-CLEAN-M1-T04 | Done | 知识库列表、详情页和协同状态中的“打开文档/新建文档”改为“打开内容页/新建内容页/知识内容”。 |
| KB-CLEAN-M1-T05 | Done | Dashboard、全局搜索、IM、通知、对象卡片的 document 用户标签改为“知识内容”，旧 `/docs/:id` 跳转仍作为兼容 deep link 并进入带知识库上下文的编辑页。 |
| KB-CLEAN-M1-T06 | Done | Dashboard “最近文档和表格”改为“最近知识内容和表格”，最近内容标签显示“知识内容”。 |
| KB-CLEAN-M1-T07 | Done | 前端路由和菜单通过 lint/build 验证；`rg` 反查确认用户导航中不再有独立“文档”入口。 |
| KB-CLEAN-M1-T08 | Done | 更新产品、架构、平台对象和执行报告，记录“文档模块不再作为一级产品模块，document 为兼容内容对象类型”。 |

验收门：

- 主导航、移动导航和首页不再出现“文档”一级入口。
- 旧 `/docs/:id` 链接仍可访问。
- 打开旧链接时能看到知识库所属空间和路径。

## 6. KB-CLEAN-M2 - 知识库内容页承载文档编辑能力

目标：把原 DocsPage 的编辑、评论、版本、分享能力纳入知识库内容页语境。

Lark 化结果：编辑内容时仍处在知识库空间内，用户不会感知自己离开到另一个“文档模块”。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M2-T01 | Done | 新增 `/knowledge-bases/:spaceId/items/:docId` 前端路由，承载现有内容编辑页能力。 |
| KB-CLEAN-M2-T02 | Done | `DocsPage` 作为唯一内容页组件同时服务新知识库路由和 `/docs/:docId` 兼容路由，编辑器、评论、版本、权限、分享、关系和知识元数据入口不复制分叉。 |
| KB-CLEAN-M2-T03 | Done | 知识库详情页、知识库列表首页入口、搜索结果、治理卡片、协同健康卡片和知识列表打开内容时优先进入 `/knowledge-bases/:spaceId/items/:docId`。 |
| KB-CLEAN-M2-T04 | Done | 内容页顶部固定展示返回知识库、空间名称、目录路径、内容状态、权限态、维护人和更新时间。 |
| KB-CLEAN-M2-T05 | Done | 新路由复用原内容页数据查询、富文本编辑、自动保存/版本、评论、关系、分享、权限和知识元数据表单。 |
| KB-CLEAN-M2-T06 | Done | `/docs/:docId` 继续指向同一个 `DocsPage`，仅作为历史 deep link 和分享兼容入口。 |
| KB-CLEAN-M2-T07 | Done | 上下文条在窄屏下自动换行，知识库目录、评论、分享入口沿用原内容页响应式布局，不新增页面级水平溢出。 |
| KB-CLEAN-M2-T08 | Done | 浏览器冒烟覆盖知识库内容页新路由打开、上下文条、返回知识库和旧 `/docs/:id` 进入；编辑/评论入口通过同页组件可见性确认。 |

验收门：

- 用户从知识库打开内容后，仍看到知识库空间和目录上下文。
- 编辑、评论、版本和分享能力不回退。
- `/docs/:id` 与新内容页复用同一套组件。

## 7. KB-CLEAN-M3 - API 语义从 docs 迁移到 knowledge-bases

目标：把知识库内容相关 API 从产品语义上迁出 `/api/docs`。

Lark 化结果：后续开发者扩展知识库能力时不会再误把 `/api/docs` 当成主 API。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M3-T01 | Done | 盘点并固化分类：`/api/docs` 保留编辑器底层和历史兼容；知识库工作台、内容节点生命周期、模板、导入导出、discovery 和治理进入 `/api/knowledge-bases`。 |
| KB-CLEAN-M3-T02 | Done | 新增 `/api/knowledge-bases/{spaceId}/items`、`/items/tree`、`/items/from-template`、`/items/{documentId}/move|archive|restore` 和 `/templates` 内容 API 前缀。 |
| KB-CLEAN-M3-T03 | Done | 知识库内创建、模板创建、移动/排序、归档、恢复改由 `KnowledgeBaseSpaceController` 和 `KnowledgeBaseSpaceService` 承载，并校验内容节点归属当前知识库。 |
| KB-CLEAN-M3-T04 | Done | 知识库模板、批量 Markdown 导入、整库 Markdown 导出、discovery 和治理均在 `/api/knowledge-bases` 前缀下暴露；前端 knowledgeBases API client 提供对应函数。 |
| KB-CLEAN-M3-T05 | Done | `/api/docs` 继续保留详情、保存、块、评论、版本、协同、分享、权限、单页导入导出和旧 deep link 兼容能力。 |
| KB-CLEAN-M3-T06 | Done | `DocumentController` 增加兼容边界注释，架构文档明确禁止新增知识库空间、导航、治理能力继续扩展 `/api/docs`。 |
| KB-CLEAN-M3-T07 | Done | 前端 `KnowledgeBaseDetailPage` 改用 `listKnowledgeBaseItemTree`、`listKnowledgeBaseTemplates`、`createKnowledgeBaseItem`、`createKnowledgeBaseItemFromTemplate` 和 `moveKnowledgeBaseItem`，不再直接调用全局 `listDocumentTree`。 |
| KB-CLEAN-M3-T08 | Done | `DocumentControllerIntegrationTests#knowledgeBaseItemApisMirrorLegacyDocumentCompatibility` 覆盖新知识库内容 API 与旧 `/api/docs/{id}` 兼容读取，并验证跨知识库移动被拒绝。 |

验收门：

- 知识库工作台创建、移动、归档内容时不直接调用全局文档树 API。
- `/api/docs` 不再承载知识库空间产品能力。
- 旧 API 仍能服务历史链接和编辑器底层能力。

## 8. KB-CLEAN-M4 - 数据模型去冗余与字段降级

目标：从数据语义上切断“文档空间即知识库空间”的旧模型。

Lark 化结果：知识库空间元数据只在知识库空间模型里，内容节点只表达内容。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M4-T01 | Done | 依赖清单确认旧字段读写集中在 `JdbcDocumentRepository`、`JdbcKnowledgeBaseSpaceRepository`、旧 `/api/docs/{id}/knowledge-base` 兼容接口、V031/V038 迁移和 migration check 脚本；前端知识库空间设置读取 `knowledgeBasesApi` 的 `KnowledgeBaseSpaceSummary`。 |
| KB-CLEAN-M4-T02 | Done | `JdbcKnowledgeBaseSpaceRepository.updateSpace` 只更新 `knowledge_base_spaces`，不再把名称、描述、封面和默认权限双写回根文档。 |
| KB-CLEAN-M4-T03 | Done | 新建知识库根文档不再写入空间描述、封面和空间默认权限；根文档旧字段保持 `null/null/view` 的兼容默认值。 |
| KB-CLEAN-M4-T04 | Done | `documents.knowledge_base` 仅保留为根目录旧链接/旧数据补登记兼容标记；空间详情、查找、更新和治理均以 `knowledge_base_spaces` 为主。 |
| KB-CLEAN-M4-T05 | Done | 保留 `doc_type='space'` 作为知识库内容树根节点类型；新建根文档标题为“根目录”，前端兼容区文案改为“知识库根目录/普通根目录”。 |
| KB-CLEAN-M4-T06 | Done | `scripts/knowledge-base-migration-check.ps1` 新增根目录文档存在性、首页存在性、根文档 deprecated 影子字段和兼容 flag 检查。 |
| KB-CLEAN-M4-T07 | Done | active 文档标记 `documents.description/cover_url/default_permission_level` 在根目录上的空间语义为 deprecated；删除字段留到 KB-CLEAN-M8 决策表，不在调用完全收口前物理删除。 |
| KB-CLEAN-M4-T08 | Done | 新增 `DocumentControllerIntegrationTests#knowledgeBaseSpaceSettingsUseSpaceTableAndRootMetadataIsDeprecated`，覆盖空库迁移后新建/更新知识库、旧 docs 配置 no-op 和 DB 字段断言。 |

验收门：

- 知识库空间详情不再依赖 documents 上的空间元数据。
- 旧 space 能通过知识库空间表找到归属。
- 冗余字段有明确 deprecated 或删除计划。

## 9. KB-CLEAN-M5 - 权限双轨收口

目标：让知识库和内容权限统一走通用资源权限，旧文档权限表不再作为主链路。

Lark 化结果：权限解释只看统一 ACL，不需要理解文档旧权限表和资源权限双轨。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M5-T01 | Done | 已盘点运行时代码中 `document_permissions` 的读写点，集中收口 `JdbcDocumentRepository`、`DocumentService` 和平台对象权限来源。 |
| KB-CLEAN-M5-T02 | Done | 文档/内容访问校验继续优先使用 `PermissionDecisionService` + `resource_permissions`，失败分支只保留通用 ACL/分享链接结果，不再读取旧表。 |
| KB-CLEAN-M5-T03 | Done | 文档权限列表、平台对象权限解释和权限治理均统一读取 `resource_permissions`；`PlatformObjectService` 的 document 权限来源改为 `resource_permissions`。 |
| KB-CLEAN-M5-T04 | Done | `scripts/knowledge-base-migration-check.ps1` 新增 `legacy-document-permission-backfill-gap`，检查活动旧授权是否已完整回填到通用 ACL。 |
| KB-CLEAN-M5-T05 | Done | 旧 `/api/docs/{documentId}/permissions` 授权入口改为只写 `resource_permissions`，新建文档 owner 权限也只写通用 ACL。 |
| KB-CLEAN-M5-T06 | Done | 分享链接访问标记、权限申请管理人通知、父子继承复制、断开/恢复继承均按知识库/内容 `resource_permissions` 计算。 |
| KB-CLEAN-M5-T07 | Done | 架构文档明确旧 `document_permissions` 仅为迁移补偿源，权限治理语义面向知识库/内容通用资源权限。 |
| KB-CLEAN-M5-T08 | Done | `DocumentControllerIntegrationTests` 新增旧授权入口不写旧表回归；既有 `ResourcePermissionControllerIntegrationTests` 覆盖成员、部门、用户组、角色、继承、断开继承和访问申请链路。 |

验收门：

- 新增权限不再依赖 `document_permissions` 主链路。
- 权限解释能显示知识库空间、目录或内容直授来源。
- 权限治理结果不再要求用户理解旧表。

## 10. KB-CLEAN-M6 - 搜索、通知、对象卡片和跨模块语义统一

目标：把跨模块暴露的“document”产品语义统一改为知识库内容语境。

Lark 化结果：从 IM、搜索、通知、项目、工作台进入内容时，看到的都是知识库路径和知识内容。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M6-T01 | Done | 搜索结果已把 `document` 展示为知识内容，并返回知识库名称、目录路径、内容类型和知识库页路径。 |
| KB-CLEAN-M6-T02 | Done | 平台对象摘要保留 document objectType，但卡片展示知识库路径、知识库名称和知识内容打开入口。 |
| KB-CLEAN-M6-T03 | Done | IM 内部链接 `/docs/:id` 已通过平台对象摘要展示为知识内容卡片，主跳转进入知识库内容页。 |
| KB-CLEAN-M6-T04 | Done | 评论提及、复核提醒、订阅更新和通用授权通知已优先使用知识库通知语境和知识库 webPath。 |
| KB-CLEAN-M6-T05 | Done | 项目事项侧用户可见“关联文档”文案已改为“关联知识内容”，底层 objectType 继续兼容 document。 |
| KB-CLEAN-M6-T06 | Done | 工作台最近知识内容改为平台对象卡渲染，收藏/最近对象复用知识库路径和卡片文案。 |
| KB-CLEAN-M6-T07 | Done | 搜索结果不可访问态清空标题、摘要、知识库路径和维护人等上下文字段，继续复用统一权限决策。 |
| KB-CLEAN-M6-T08 | Done | 浏览器冒烟已覆盖搜索、IM 卡片、通知、项目关联、工作台展示和工作台跳转。 |

验收门：

- 用户从任意模块进入内容时，都能看到知识库上下文。
- 对外文案不再把文档作为独立产品模块。
- 无权限内容不会通过卡片或搜索泄露。

## 11. KB-CLEAN-M7 - 文案、文档和开发边界收口

目标：让产品文案、开发文档和路线图统一表达知识库主语。

Lark 化结果：新会话、新开发者和用户都不会再理解为“知识库 + 文档两个模块并存”。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M7-T01 | Done | `docs/00-product/current-product-scope.md` 已把产品能力、搜索、权限、试运行和 Gap 统一改为知识库/知识内容口径，并说明 `docs`/`document` 仅为兼容底座。 |
| KB-CLEAN-M7-T02 | Done | `docs/01-architecture/current-architecture.md` 已明确 `/api/knowledge-bases` 与 `knowledgeBases` 是产品入口，`/api/docs`、`web/src/modules/docs` 和 `Document*` 是编辑底座与历史兼容层。 |
| KB-CLEAN-M7-T03 | Done | `docs/01-architecture/platform-object-model.md` 已说明 `document` 是历史 objectType，用户侧展示为知识内容，知识库上下文优先跳转 `/knowledge-bases/{spaceId}/items/{id}`。 |
| KB-CLEAN-M7-T04 | Done | `docs/03-engineering/ai-engineering-governance.md` 新增“知识库唯一入口约束”，要求后续路线不得扩展独立文档模块。 |
| KB-CLEAN-M7-T05 | Done | 前端可见中文文案已从“文档”收口到“知识内容/内容节点”，保留内部 `document` 值和兼容路由不变。 |
| KB-CLEAN-M7-T06 | Done | `DocumentController` 已有兼容注释，并为 `KnowledgeBaseSpaceController`、`DocumentService`、`JdbcDocumentRepository`、`DocumentPlatformObjectResolver` 补充底座/兼容边界说明。 |
| KB-CLEAN-M7-T07 | Done | `docs/05-runbooks/browser-smoke.md`、`scripts/README.md`、知识库验收和迁移检查脚本输出已切换到知识库验收/知识内容节点口径。 |
| KB-CLEAN-M7-T08 | Done | `docs/90-reports/m7-execution-report.md` 已输出本轮报告，列出保留兼容命名和删除条件。 |

验收门：

- active 文档中不再把文档列为独立产品模块。
- 代码注释明确 docs 相关 API 的兼容或底座定位。
- 新会话只读 active 文档即可理解知识库唯一入口目标。

## 12. KB-CLEAN-M8 - 兼容清理、迁移验证和冻结

目标：在确保历史链接和数据不丢失的前提下，冻结知识库唯一入口模型。

Lark 化结果：系统完成从旧文档模块到知识库内容平台的产品收口。

任务：

| Task | Status | Implementation |
| --- | --- | --- |
| KB-CLEAN-M8-T01 | Done | 新增 `scripts/knowledge-base-compat-cleanup-check.ps1`，扫描 `/docs` 入口、旧字段读写、旧权限表写入、旧文案残留和兼容引用清单。 |
| KB-CLEAN-M8-T02 | Done | `DocumentControllerIntegrationTests` 覆盖 IM、通知、搜索、项目关联、Dashboard 和历史 `/docs/:id` 兼容链接；目标测试已通过。 |
| KB-CLEAN-M8-T03 | Done | `scripts/knowledge-base-migration-check.ps1` 验证 `knowledge_base_spaces`、根内容节点、owner 权限、通用 ACL、搜索索引和知识库上下文一致。 |
| KB-CLEAN-M8-T04 | Done | 兼容清理检查报告已生成 deprecated 决策表，明确 `/docs` 根入口可移除，`/docs/:docId`、`/api/docs`、`document` objectType、`documents` 表和 shadow 字段仍需保留条件。 |
| KB-CLEAN-M8-T05 | Done | 已执行目标后端测试、兼容/迁移脚本；route-final 完整门禁已通过并完成最终冻结验证。 |
| KB-CLEAN-M8-T06 | Done | `scripts/knowledge-base-acceptance-report.ps1` 聚合迁移、兼容清理、试运行和质量门禁证据，输出知识库 v1 验收报告。 |
| KB-CLEAN-M8-T07 | Done | 当前路线图已标记 KB-CLEAN-M1 到 M8 完成，遗留兼容项记录在 M8 执行报告和兼容清理决策表。 |
| KB-CLEAN-M8-T08 | Done | `docs/90-reports/m8-execution-report.md` 归档本轮执行结果，并冻结“知识库唯一入口 v1”作为后续增强基线。 |

验收门：

- 用户侧无法再从导航进入独立文档模块。
- 旧链接和跨模块对象不失效。
- 当前 active 文档、代码边界和验收报告一致表达知识库唯一入口。

## 13. 全局验收标准

1. 不得破坏现有知识库 v1 功能：空间、首页、导航、权限、搜索、协作、治理和迁移。
2. 不得破坏历史 `/docs/:id` 链接、对象卡片、通知和搜索 deep link。
3. 新增或修改知识库能力不得继续扩展旧 `documents.knowledge_base` 或 `/docs` 产品语义。
4. 字段删除必须先有依赖扫描、迁移检查、回滚方案和测试。
5. 用户侧文案必须以知识库为主语，文档只作为内容类型出现。
6. 权限和搜索必须继续遵守无权限不泄露标题、摘要、路径和正文。
7. 每个里程碑至少补充目标后端测试；涉及页面入口的里程碑必须补充前端构建或浏览器验证。
8. 不新增第二份 active 路线图；当前执行入口始终是 `docs/02-roadmap/current-roadmap.md`。

## 14. 下一步入口

从 KB-CLEAN-M1 开始执行，第一轮推进 KB-CLEAN-M1-T01 到 KB-CLEAN-M1-T08。

执行前必须先读取：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/02-roadmap/current-roadmap.md`
5. `docs/03-engineering/ai-engineering-governance.md`

阶段验证策略沿用 AI 工作循环：每轮优先做本轮影响范围内的目标测试、lint/build 或浏览器验证；完整 `mvn test`、完整集成测试和全量迁移验证放在阶段收口里程碑执行。
