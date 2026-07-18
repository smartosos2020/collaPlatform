---
title: AI 编程工程治理规范
status: active
---

# AI 编程工程治理规范

## 1. 文档目的

本文档定义本项目在引入 AI 编程后的工程管理规范和执行约束，目标是让 AI 能够在较长时间内持续推进业务实现，同时保持代码质量、测试完整性、审计可追溯和架构一致性。

本规范不是建议清单，而是后续所有业务实现前必须遵守的工作协议。

## 2. 适用范围

适用于本仓库中的全部交付内容：

- 后端 Spring Boot 代码。
- 前端 React 代码。
- 数据库迁移。
- Docker、部署和脚本。
- 产品、架构和技术文档。
- AI 自动生成或人工修改的代码。

## 3. 核心原则

### 3.1 小步闭环

AI 每次实现必须围绕一个明确任务包推进，不允许一次性横跨多个业务模块做大面积修改。

推荐粒度：

- 一个接口和对应测试。
- 一个页面和对应 API 接入。
- 一个数据库迁移和对应实体/Repository。
- 一个跨模块链路的最小竖切。

### 3.2 先读后改

AI 在修改任何模块前必须先读取：

- 当前模块已有代码。
- 相关文档。
- 数据库迁移。
- 已有测试。
- 相邻模块的接口约束。

禁止凭记忆或猜测直接写业务代码。

### 3.3 架构边界优先

本项目采用模块化单体。AI 必须保持模块边界：

- Controller 不写业务逻辑。
- Application service 编排用例、事务和权限。
- Domain 放领域对象、枚举、状态规则。
- Infrastructure 放数据库、外部系统和查询实现。
- 跨模块副作用优先通过领域事件或模块服务完成。
- 权限判断集中在 permission 模块。
- 文件能力集中在 file 模块。
- 对象链接和卡片集中在 platform 模块。

### 3.4 可验证交付

每次工作结束必须说明：

- 改了哪些文件。
- 实现了什么行为。
- 跑了哪些验证命令。
- 有没有失败或跳过的检查。
- 剩余风险是什么。

没有验证的代码不视为完成。

## 4. AI 工作循环

AI 工作循环不是单纯的代码实现流程，而是固定闭环：

```text
代码实现 + 文档同步 + 验证报告
```

当用户输入 `PROJECT-PLATFORM-S01-M1-T01` 这类带 Program/Stage 的活动任务范围时，默认必须执行该闭环。活动 `code-doc-report` 路线不再接受脱离当前 Program/Stage 的裸 `MXX-TYY` 示例；归档材料中的旧编号只作为历史证据。

M31/M40 仿真数据和试运行准入已经归档，不再作为当前标准基线。当前禁止为了普通验证自动重置共享开发数据；若未来需要新的固定数据或试运行基线，必须先按当时活动 schema 重新设计并单独建立路线。

### 4.1 开始前

执行：

```shell
pnpm work:start -- --goal project-platform-s01-m1 --task-range "PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M1-T09"
```

要求：

- 生成完整审计快照。
- 确认当前工程状态。
- 明确本轮目标和不做范围。
- 写入 `.local-reports/work-cycle-current.json`。
- 输出 Document Contract。
- 校验 `Program -> Stage -> Milestone -> Task` 规划合同：当前路线只包含一个 Stage，任务真实存在且未完成，专项索引只有一个匹配的 Active Program，长期专项只有一个匹配的 Active Stage。
- 如果能识别里程碑，自动创建或复用对应执行报告；专题前缀必须保留，例如 `M25` 对应 `m25-execution-report.md`，`KB-NAME-M1` 对应 `kb-name-m1-execution-report.md`。

### 4.2 工作中

每完成一个可运行小闭环，执行：

```shell
pnpm work:checkpoint -- --goal project-platform-s01-m1
```

验证档位由阶段自动派生，checkpoint 固定为 `light`，finish 固定为 `stage`，需要全量时显式传 `--validation-profile route-final`。

要求：

- 默认运行 `light` 验证档位：根据 start 后实际变更范围，只执行受影响后端编译、前端 lint 或 collaboration 测试（`collaboration/` 变更时自动执行 `pnpm collaboration:test`）；不启动 Docker，不重复运行全仓静态审计。
- 修复新增失败。
- 不把失败堆到最后。
- checkpoint 不默认触发 Spring Boot 集成测试，因此不默认触发 Testcontainers PostgreSQL 和 Flyway 空库全量迁移。
- quick 门禁会检查文档结构、任务行和 Contract 格式，但不会要求任务已经 Done，也不会要求浏览器收口证据。

如果工作中确实需要提前验证某个高风险后端竖切，可以显式执行阶段级目标测试：

```shell
pnpm work:checkpoint -- --goal project-platform-s01-m1 --validation-profile stage --backend-test-pattern ProjectControllerIntegrationTests
```

### 4.3 结束前

执行：

```shell
pnpm work:finish -- --goal project-platform-s01-m1 --backend-test-pattern ProjectControllerIntegrationTests --browser-not-required-reason "本里程碑只调整项目领域合同，不包含用户可见界面或浏览器交互"
```

要求：

- 默认运行 `stage` 验证档位，而不是全量历史回归。
- `code-doc-report` 的 stage finish 必须传入 `--backend-test-pattern` 并运行本里程碑相关后端集成测试；不再允许用“仅编译 + 报告说明”替代里程碑目标测试。
- finish 必须二选一传入一个或多个 `--browser-spec`，或传入 `--browser-not-required-reason`。前者由工作循环以结构化 Playwright 参数执行并写入新鲜日志；后者必须给出至少 20 个字符的具体不适用理由。
- stage finish 只运行目标后端测试、受影响前端 lint/build 和严格工作循环契约；不启动 compose 健康检查，不运行后端 package 或重复全仓静态审计。目标集成测试仍需自行通过 Testcontainers 检查 Docker daemon。
- 只有 route-final 运行后端 package、安全扫描、迁移顺序、Mockito javaagent、生成产物、活动文档结构、TODO/FIXME inventory 和完整后端测试；完整前端构建同时检查路由页面懒加载。
- stage finish 生成轻量审计快照，route-final 生成完整审计快照。
- 输出最终交付说明。
- full 门禁必须通过文档闭环检查。

当前执行路线图的最后一个里程碑完成后，必须显式执行路线级最终验证：

```shell
pnpm work:finish -- --goal project-platform-s01 --validation-profile route-final --browser-spec e2e/cross-module-route-final.spec.ts --browser-evidence-kind real --browser-evidence-environment isolated
```

`route-final` 才运行完整 `mvn test`、完整 Spring Boot 集成测试集合和由测试触发的 Flyway 空库迁移验证。

当当前路线属于长期专项 Stage 时，`stage_final_milestone` 指定的最终里程碑必须使用 `route-final`。该里程碑 finish 前必须把路线全部 Task 标记为 `Done`，把路线状态改为 `completed`，把长期专项中的当前 Stage 改为 `Completed`、把 `current_stage` 暂置为 `none`，把专项索引中的当前 Stage 暂置为 `none`，递增专项和目标架构的同一修订号并更新规划变更记录；工作台会把专项索引、`program_doc` 和目标架构加入该轮必更文档并阻断未同步收口。归档当前路线并生成下一路线时，才把下一 Stage 改为 `Active`。

### 4.4 文档同步契约

默认 `DocMode` 为 `code-doc-report`。除非用户明确要求归档整理，否则不得切换为其他模式。

每轮工作循环必须：

- 更新 `docs/02-roadmap/current-roadmap.md`。
- 创建或更新 `docs/90-reports/{qualified-milestone}-execution-report.md`；带专题前缀的里程碑不得退化为裸 `mXX` 报告名。
- 在执行报告中记录完成项、代码变更、文档变更、验证结果、遗留 Gap 和下一步。
- 按影响范围更新 active 真相文档：
  - 产品能力变化：`docs/00-product/current-product-scope.md`
  - 架构、API、数据库、WebSocket、搜索、权限变化：`docs/01-architecture/current-architecture.md`
  - 平台对象、内部链接、对象卡片、搜索对象类型变化：`docs/01-architecture/platform-object-model.md`
  - 技术栈变化：`docs/01-architecture/technology-selection.md`
  - AI 工作规则、脚本、门禁变化：`docs/03-engineering/ai-engineering-governance.md`
  - 长期专项 Stage 最终里程碑：当前路线 `program_doc` 指向的专项规划和对应目标架构

每轮工作循环禁止：

- 新建 roadmap 文件。
- 在 `docs/` 根目录新增 Markdown，`README.md` 除外。
- 在非归档任务中编辑 `docs/99-archive/`。
- 创建自由格式的 `mXX-*.md`、`next-plan.md`、`post-mXX-roadmap.md`。

固定执行报告格式：

```md
---
title: {QUALIFIED-MILESTONE} 执行报告
status: archived
milestone: {QUALIFIED-MILESTONE}
updated_at: YYYY-MM-DD
---

# {QUALIFIED-MILESTONE} Execution Report

## Scope

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |

## Code Changes

- 后端：
- 前端：
- 数据库：
- 脚本：

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |

## Validation

- Backend tests:
- Frontend build:
- Local quality gate:
- Browser smoke:

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | N/A |

## Next Steps
```

#### 4.4.1 验收证据契约

路线图状态和工程门禁是两类不同证据。编译、lint、build、package 或迁移成功只能证明工程健康，不能单独证明业务任务完成。

每个当前工作范围内的任务必须在执行报告 `Acceptance Evidence` 表中提供且只提供一行：

- `Acceptance criterion`：逐字或等价复述路线图验收标准，不能写成笼统的“功能完成”。
- `Implementation evidence`：列出实现行为及对应类、API、页面、迁移或脚本。
- `Automated evidence`：列出实际执行的测试类、测试用例或命令及通过结果；仅写“已有测试覆盖”无效。
- `Browser evidence`：涉及页面、路由、通知触达或用户剧本时必须记录实际浏览器命令和结果；纯后端任务可写 `N/A`，但必须附具体原因。
- `Status`：只有前四列证据均成立时才能写 `Done`。

执行报告还必须提供 `Verification Contract`，当前范围内每个任务一行：

- `Required verification level`：只能是 `static`、`unit`、`integration`、`e2e-real` 或 `e2e-real-isolated`。
- `Browser evidence kind`：只能是 `real`、`mock` 或 `not-required`，必须与实际运行命令一致。
- `Environment`：只能是 `isolated`、`shared-readonly`、`mock` 或 `not-required`。
- `Mock browser allowed`：只能是 `Yes` 或 `No`。
- `Required real flow`：记录真实角色、真实 API、数据准备和清理边界；纯后端或无浏览器任务必须写具体不适用原因，不能只写 `N/A`。

finish 的严格门禁必须：

1. 检查当前 `TaskRange` 中每个任务都有六列表格证据。
2. 检查执行报告和路线图中的任务状态均为 `Done`。
3. 拒绝 `TODO`、`TBD`、`Pending`、“待执行”和空占位。
4. 检查验证章节不存在未完成描述，Remaining Gaps 明确写 `None` 或具体风险。
5. 检查浏览器证据生成时间晚于本轮 start，且通过日志仍存在。

若事后发现证据不满足验收标准，必须把路线图任务改为 `Reopened`，不得只在报告 Gap 中弱化说明；补验必须重新执行独立的 `start -> checkpoint -> finish`。

#### 4.4.2 证据真实性与完成阻断

`mock` 浏览器测试可以验证页面状态、异常分支和视觉交互，但不能证明真实接口、认证、权限或数据闭环。执行报告和质量门禁必须明确区分 `mock` 与 `real`，不得把 mock 测试称为 E2E 或作为真实闭环完成证据。

下列任务默认使用 `e2e-real-isolated`：登录/认证、权限、创建或修改资源、删除/启用/停用、密码与安全策略、会话/设备、资源交接、导出和审计。该证据必须使用真实登录态、真实后端 API、隔离数据库或具名隔离数据与清理策略；不得通过响应拦截类 mock（`page.route`、`context.route`、`route.fulfill`、`route.abort`）或伪造 token/API 响应替代。通过真实 API 登录获得真实 token 后再注入浏览器上下文是合法真实证据；该模式只允许存在于 `web/e2e/support/api.ts` 的会话安装辅助中。工作台使用 TypeScript AST 扫描静态导入、动态导入、CommonJS 引用、重导出、路径别名及仓库内完整本地依赖闭包，隐藏在辅助文件中的 mock 同样会被拒绝。

`finish` 的 `--browser-spec` 必须同时传入：

```shell
--browser-evidence-kind real --browser-evidence-environment isolated
```

真实浏览器命令必须指向具体 Playwright spec 或浏览器脚本，不能只传 `--grep`。工作循环会拒绝将包含响应拦截类 mock 标记的 spec 声明为 `real`，并会递归扫描 spec 引用的本地辅助文件。页面级 mock 测试必须显式传入：

```shell
--browser-evidence-kind mock --browser-evidence-environment mock
```

`Remaining Gaps` 必须使用四列表，并标明关联任务和对验收的影响。任何关联当前任务、包含未实现/缺失/仅支持旧单项能力等阻断描述的 Gap，都不能标记 `non-blocking`；任务必须保持 `In Progress` 或改为 `Reopened`，路线图和六列表状态不得写 `Done`。

同一报告用于后续连续任务时，可以保留路线图中仍未完成的非当前任务阻断 Gap；它不会阻塞当前任务收口。已标记 `Done` 的非当前任务仍不得保留阻断 Gap，必须在后续任务 finish 前解决或删除陈旧条目。

质量门在浏览器命令和目标测试完成后执行自动独立证据核对：验证任务级 Contract、真实/mock 类型、隔离环境、路线图/报告状态与 Gap 表。该核对失败即表示本里程碑未收口。

#### 4.4.3 Program、Stage 与当前路线合同

大型长期方向采用四级结构：

```text
Program -> Stage -> Milestone -> Task
```

- `docs/00-product/initiatives/README.md` 是机器可校验的专项索引，维护唯一 Active Program 和不能遗忘的 Paused Program；它不承载可执行 Task。
- Program 文档位于 `docs/00-product/initiatives/`，维护长期目标、Stage 索引、依赖、退出条件、候选池和规划变更，并通过 `initiative_index_doc` 指向专项索引、通过 `target_architecture_doc` 指向目标架构；它不直接维护可执行 Task 状态。
- `docs/02-roadmap/current-roadmap.md` 仍是唯一执行入口，每次只承载一个 Stage，可包含该 Stage 内多个 Milestone。
- 当前路线 front matter 必须声明 `program`、`program_doc`、`program_revision`、`stage` 和 `stage_final_milestone`；`route` 必须等于 `stage`。
- Program 文档必须存在且修订号一致，目标架构的 `program_revision` 必须与当前路线和 Program 一致；专项索引中恰好一个 Active Program，Stage 总览中恰好一个 `Active`，并与当前路线和 `current_stage` 一致。
- Task 使用 `{PROGRAM}-SXX-MX-TYY`，必须在当前路线表格中恰好出现一次；`work:start` 拒绝未声明、跨 Stage 或已经 Done 的 Task，补验前必须先标记 `Reopened`。
- 当前 Stage 执行中原则上冻结。范围内澄清可更新当前路线；目标、依赖或远期规划变化必须更新 Program revision 和变更记录，并同步当前路线引用。
- Stage 最终 Milestone 使用 `route-final`，并把专项索引、Program 和目标架构纳入必更合同。收口时路线全部 Task 必须 Done，Program/目标架构 revision 必须同步递增，当前 Stage 必须完成，Program 和专项索引的当前 Stage 必须暂置为 `none`；任一条件不满足时不能 finish。
- Stage 完成后先归档当前路线，再激活下一 Stage 并生成新的 `current-roadmap.md`。已完成 Stage 的历史结论不直接改写；缺陷进入 `Reopened` 或新的修复 Stage。

可随时独立检查规划合同：

```shell
pnpm work:plan-check
```

### 4.5 文档模式

| DocMode | 用途 | 允许行为 |
| --- | --- | --- |
| `code-doc-report` | 默认迭代闭环 | 代码实现、active 文档同步、路线图更新、执行报告更新 |
| `archive-only` | 专门文档整理 | 只整理、迁移、归档文档，不改业务代码 |

`archive-only` 只能在用户明确要求“文档整理、归档、迁移”时使用。

### 4.6 单轮范围边界

用户可以一次性描述较大的目标范围，但 `code-doc-report` 工作循环只能收口一个里程碑。

单个工作循环上限：

- 最多覆盖 1 个里程碑。
- 同一里程碑的任务数量没有固定上限；例如 `PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M1-T09` 是合法范围。
- 是否拆成多轮由任务依赖、修改风险、真实验证成本和可审计性决定，不得因习惯性“8 项上限”机械拆分。
- 不允许用一个工作循环直接覆盖 `PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M2-T09` 这类跨里程碑范围。
- 不允许跨 Stage，例如 `PROJECT-PLATFORM-S01-M4` 与 `PROJECT-PLATFORM-S02-M1` 必须分别建立工作循环和当前路线。

当用户输入范围跨多个里程碑时，AI 必须只启动第一个里程碑，并根据路线图明确该里程碑的完整任务范围。例如：

- 用户输入 `按 AI 工作循环推进 PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M4-T09`。
- AI 应先启动 `PROJECT-PLATFORM-S01-M1` 的完整路线图任务范围。
- M1 完成代码、文档、验证报告闭环后，才可进入 M2。

同一个会话不等于同一个工作循环。会话上下文可以连续保留，但每个里程碑都必须重新经历 `start -> checkpoint -> finish`，并生成或更新对应的执行报告和质量门禁结果。

连续推进多个里程碑时，不得以“上一轮报告已写”直接进入下一里程碑。前一里程碑必须先通过 `finish` 的完成证据核对；若发现验收证据、Gap 或路线图状态不一致，先将相关任务 `Reopened` 并补验，再允许推进下一里程碑。

### 4.7 浏览器冒烟验证

当前端页面、路由、API 契约、认证会话、WebSocket 或影响布局的 CSS 发生变化时，完成代码验证后必须进行浏览器冒烟验证。

最低要求：

- 启动本地依赖、后端和前端。
- 使用 `http://127.0.0.1:5173/login` 登录。
- 使用 `admin / admin123456` 或本轮明确指定的测试账号。
- 打开本轮影响的页面和至少一个相关主导航入口。
- 在执行报告中记录目标 URL、账号、检查页面、结果和跳过原因。
- `finish` 的浏览器 spec 必须声明 `--browser-evidence-kind` 与 `--browser-evidence-environment`；真实闭环使用 `real + isolated`，mock 只用于明确允许的页面测试。

详细步骤见 `docs/05-runbooks/browser-smoke.md`。

该要求只指本轮影响范围内的局部浏览器冒烟，不等同于历史全链路仿真。

双 UI、Shell、路由、用户菜单、后台导航、搜索/通知/平台对象边界发生变化时，优先运行：

```shell
pnpm smoke:ui-split
```

该命令覆盖用户工作台主入口、管理后台主入口、Shell 隔离、头像菜单中的后台入口和后台返回工作台入口。

### 4.8 数据与历史场景脚本

- 后端集成测试使用 `application-test.yml` + Testcontainers PostgreSQL，不依赖共享本地数据库。
- 普通验证不得重置共享开发数据。
- 旧 M31、M40、试运行和性能/知识库兼容脚本是归档历史工具，不属于当前保证可执行的工作循环入口。
- 需要复现历史报告时，必须先审计脚本中的表名、API、路由和权限前提；发现 `/docs`、`/api/docs`、`Document*` 或旧表名时不得直接运行。
- 新的数据初始化、跨模块回归或试运行准入必须基于当前 schema 和规范路由重新设计，不能把历史脚本重新标记为 active。

### 4.10 知识库唯一入口约束

`knowledge_base_spaces` 是知识库空间唯一主模型；`/knowledge-bases`、`/api/knowledge-bases` 和 `web/src/modules/knowledgeBases` 是知识库产品能力的唯一入口。后续路线、需求文档和前端导航不得再扩展独立“文档模块”。

`/api/docs`、`web/src/modules/docs`、`Document*` 产品类、`documents/document_*` 活动表和 `document` 产品 objectType 已删除。活动代码、文档和新迁移不得恢复这些兼容面；旧词只允许存在于不可变 Flyway、归档证据或 DOM/editor/search document 等通用技术语义。

结构化 `knowledge_content_blocks`、版本 `block_snapshot`、模板 blocks 和协同 payload blocks 是知识内容事实来源。涉及正文编辑、版本、导入导出、搜索定位、评论定位和治理指标时，必须使用 `KnowledgeBaseItem` / `KnowledgeContent` 与 `spaceId + itemId` 规范。

### 4.11 上下文读取与 Token 控制

允许使用 `rg` 做发现和定位，但不能把 `rg` 命中的几行当作完整理解。为降低 token 消耗同时规避局部读取风险，按以下规则执行：

| 文件类型 | 读取要求 |
| --- | --- |
| 工作循环、长期专项、路线图、质量门禁、权限、安全、迁移规范 | 必须完整读取目标规范文件，不能只读 `rg` 命中片段。 |
| 本轮要编辑的文档 | 至少完整读取 front matter、目录结构和将要修改的完整语义章节；如果章节边界不清，读取全文。 |
| 本轮要编辑的代码文件 | 读取完整文件或完整类/函数边界；不能只看单个匹配行就修改。 |
| 相邻调用方/被调用方 | 可先用 `rg` 定位，再读取完整函数、接口或 DTO 定义。 |
| 长日志和构建输出 | 成功时只读摘要和报告路径；失败时读取错误段、尾部日志和相关 report。 |

修改规范或脚本时，必须完整读取 `docs/03-engineering/ai-engineering-governance.md`、相关脚本和 `scripts/README.md`。修改路线图时，必须完整读取 `docs/02-roadmap/current-roadmap.md` 及其 `program_doc`。修改长期专项时，必须读取当前路线、目标架构和专项变更记录。修改执行报告时，必须完整读取对应 `docs/90-reports/*-execution-report.md`。

局部读取后的安全补偿：

1. 修改前确认文件头、状态、适用范围和相邻章节。
2. 修改后用 `rg` 反查旧口径、命令名和冲突表述。
3. 对规范、路线图、报告执行文档结构门禁。
4. 如果发现同一主题有多个口径，以 active 真相文档为准，并同步修正文档冲突。

## 5. 质量门禁

### 5.1 快速门禁

命令：

```shell
pnpm verify
```

覆盖：

- 工具链检查。
- Docker 依赖服务启动并等待 healthcheck 通过。
- 后端默认执行 `mvn -DskipTests test`，只做编译和测试编译，不运行 Surefire 测试。
- 前端 `pnpm web:lint`。
- 前端 `pnpm web:build`。
- 敏感信息扫描（工作台 TypeScript 实现，精确豁免清单 `scripts/sensitive-scan-allowlist.tsv`）。
- 安全审计 guardrails：测试库隔离、生产密钥外置、安全路由、关键服务审计调用。
- Flyway 迁移顺序检查。
- 生成产物跟踪检查。
- 实现标记库存提示。
- Mockito javaagent、活动文档 front matter 和前端路由懒加载合同检查。

快速门禁不默认运行 Spring Boot 集成测试，因此通常不会触发 Testcontainers PostgreSQL 和 Flyway 空库全量迁移。需要目标集成测试时，使用 `--backend-strategy targeted --backend-test-pattern "..."`。

### 5.2 完整门禁

命令：

```shell
pnpm verify:full
```

在快速门禁基础上增加：

- 后端 `mvn test`，运行历史单元测试和集成测试。
- 后端 `mvn -DskipTests package`。
- collaboration 包测试 `pnpm collaboration:test`。
- 由 Spring Boot 集成测试触发 Testcontainers PostgreSQL 和 Flyway 空库迁移验证。
- 只用于当前执行路线图最终收口、试运行准入、CI 或用户明确要求的完整回归。

### 5.3 阶段门禁

阶段门禁用于单个里程碑收口，介于快速门禁和完整门禁之间：

```shell
pnpm work:finish -- --goal ORG-M1-organization-foundation --backend-test-pattern "OrganizationControllerIntegrationTests,AdminUserControllerIntegrationTests" --browser-not-required-reason "该里程碑只调整后端组织领域合同，不包含用户可见界面"
```

阶段门禁只运行本里程碑相关后端集成测试。由于 Spring Boot 集成测试仍会启动测试上下文，Flyway 可能仍会对临时测试库从 `V001` 跑到最新版本；因此不要在每个小任务中反复运行阶段门禁。

阶段与完整门禁在收口时还执行以下检查：

- `git diff --check` 空白与冲突标记检查。
- proof-of-run：执行报告 Validation 必须引用本轮工作循环内产生的质量门禁日志（`quality-gate-*.log`），不接受纯文字声明。
- 文档边界强制：必更新文档以 start 基线签名判定真实变更（start 前已脏不算本轮更新）；非必需的 docs 变更只允许落在活动真相文档、`docs/90-reports` 与 `docs/05-runbooks` 内。
- 变更范围以 start 时的 `baselineCommit`、启动前脏文件签名和必更文档签名共同判定；即使本轮代码已经提交，`baselineCommit..HEAD` 仍必须进入受影响模块验证、文档边界和 `git diff --check`。
- stage finish 与 route-final finish 使用同一套任务完成契约；两者只在测试广度和全仓静态审计范围上不同，stage 不得降级为仅警告后完成。

### 5.4 门禁失败处理

门禁失败时：

1. 优先修复本轮引入的问题。
2. 如果失败来自已有问题，记录为已知风险。
3. 不允许忽略安全、权限、数据迁移、编译失败。
4. 不允许以“后续处理”为由交付不可启动代码。

## 6. 代码质量规范

### 6.1 通用规范

- 新代码默认 ASCII，中文仅用于文档和明确的 UI 文案。
- 不提交 `.env`、日志、构建产物、本地报告。
- 不引入无用途依赖。
- 不做与任务无关的重构。
- 不修改已发布 Flyway 迁移文件，只新增迁移。
- 不绕过权限服务读取业务对象。
- 不在前端组件中直接写裸 `fetch`。

### 6.2 后端规范

- Java 版本：21。
- Spring Boot：3.5.x，当前锁定 3.5.15。
- 所有写操作必须有权限判断。
- 所有关键业务写操作必须能产生审计或领域事件。
- 状态流转必须通过专门服务，不允许普通 update 绕过规则。
- 对外 DTO 不直接暴露 Entity。
- 时间字段统一使用带时区语义。
- 业务异常使用统一错误响应。

### 6.3 前端规范

- 页面组件只做组装，不直接承载复杂业务规则。
- API 调用统一放在模块 `api/` 或 `shared/api/`。
- 远程状态优先使用 TanStack Query。
- 本地跨组件状态才使用 Zustand。
- 权限显示必须有统一组件或 hook。
- UI 必须保持可扫描、稳定、信息密度合理。
- 复杂页面必须考虑窄屏降级，至少保证查看和返回路径可用。

### 6.4 数据库规范

- 表名和字段名使用小写蛇形。
- 迁移文件命名：`V001__create_xxx.sql`。
- 已执行迁移不修改。
- 所有业务表保留 `workspace_id`。
- 外键、查询条件、排序字段要有索引设计。
- 软删除表必须在查询层统一过滤。

## 7. 测试规范

### 7.1 测试分层

| 层级 | 目标 | 示例 |
| --- | --- | --- |
| 单元测试 | 领域规则和纯逻辑 | 状态流转、字段校验、权限判断 |
| 集成测试 | Spring 上下文、数据库、事务 | 登录、创建项目、发送消息 |
| 前端组件测试 | UI 状态和交互 | 登录表单、消息输入框、卡片权限态 |
| Mock 浏览器测试 | UI 状态和交互分支 | 空态、错误态、视觉布局、客户端异常提示 |
| 真实隔离 E2E 测试 | 关键业务闭环、认证和权限 | 真实登录、创建资源、权限拒绝、数据交接、导出审计 |

### 7.1.1 AI 工作循环验证节奏

| 时机 | 后端验证 | 前端验证 | 说明 |
| --- | --- | --- | --- |
| 单个任务开发中 | 编译或局部纯单元测试 | 类型/lint 局部检查 | 不默认触发集成测试。 |
| checkpoint | 受影响模块的 `mvn -DskipTests test`，无后端变更则跳过 | 受影响前端的 `pnpm web:lint`，无前端变更则跳过 | 不启动 Docker，不重复全仓静态审计；只验证当前小闭环。 |
| 单个里程碑 finish | 相关集成测试，需显式 `--backend-test-pattern` | 受影响前端 lint/build + 本轮页面冒烟 | 验证本阶段 API、权限、迁移和 JSON 合约，不运行后端 package。 |
| 路线图最终 finish | 完整 `mvn test` | 完整 lint/build + 必要 E2E/冒烟 | 验证历史功能不被破坏。 |

集成测试可以后置到里程碑收口，但不能无限后置到整条路线结束；每个里程碑至少要在收口时说明已跑的目标集成测试，或明确记录跳过原因和风险。

### 7.2 后端必须测试的内容

- 认证和 token。
- 权限判断。
- 状态流转。
- 文档版本冲突。
- 多维表格字段值校验。
- 内部链接卡片权限。
- 文件下载授权。
- outbox 幂等处理。

### 7.3 前端必须测试的内容

- 登录态和路由守卫。
- 主导航。
- IM 消息发送和失败态。
- 内部链接卡片不同权限态。
- 事项状态流转。
- 文档保存冲突提示。
- 表格字段编辑和记录保存。

### 7.4 E2E 第一批链路

- 管理员登录，新增成员。
- 成员登录，进入 IM。
- 创建项目，自动生成项目群。
- 发送消息，另一个用户收到。
- 创建 Bug，评论 @ 成员，收到通知。
- 分享 Bug/文档/表格记录链接，生成卡片。

## 8. 安全与审计规范

### 8.1 敏感信息

禁止提交：

- 明文密码。
- 真实 token。
- 私钥。
- 云服务密钥。
- 生产数据库连接串。
- 真实用户隐私数据。

本地脚本会扫描常见模式，但 AI 不能只依赖脚本，生成代码时必须主动避免。

### 8.2 权限审计

以下行为必须可审计：

- 登录成功和失败。
- 用户创建、禁用、重置密码。
- 角色和权限变更。
- 项目成员变更。
- 文件下载授权。
- 知识内容权限变更。
- 表格权限变更。
- 关键业务对象删除。

### 8.3 AI 审计快照

命令：

```shell
pnpm audit:snapshot -- --label before-project-platform-s01-m1
```

输出目录：

```text
.local-reports/
```

报告内容：

- 工具链版本。
- Docker Compose 状态。
- Git 状态。
- 源文件清单。
- 时间戳和标签。

`.local-reports/` 不提交仓库，只用于本地追踪。

`work:start` 自动生成 full 快照；checkpoint 和普通 finish 自动生成 light 快照；route-final finish 自动生成 full 快照。质量门还会把最后一次报告、模式、状态、步骤日志和完成时间写入 `work-cycle-current.json.lastQualityGate`。

## 9. 长时间自动工作的约束

AI 可以长时间推进，但必须遵守以下节奏：

- 每个任务包开始前生成审计快照。
- 每完成一个小闭环运行轻量 checkpoint；不要在中间小任务反复运行完整 `mvn test`。
- 每个里程碑收口运行本阶段目标集成测试或记录跳过原因。
- 当前执行路线图最后一个里程碑完成后运行 `route-final` 全量质量门禁。
- 每 30 到 60 分钟应形成一个可描述的 checkpoint。
- 如果连续三次被同一问题阻断，停止并报告阻断点。
- 不在测试失败状态下继续叠加新功能。
- 不在不理解已有代码的情况下大范围重写。
- 不删除用户未要求删除的文件或改动。

## 10. 完成定义

一个任务只有同时满足以下条件才算完成：

- 行为符合需求。
- 架构边界符合文档。
- 数据库迁移可执行。
- 执行报告存在该任务唯一的六列 `Acceptance Evidence` 记录，验收标准、实现证据、自动化证据和浏览器证据/不适用理由均具体且可复核。
- 执行报告存在该任务唯一的 `Verification Contract` 记录；核心闭环标记为 `e2e-real-isolated`，mock 证据不会被当作真实 E2E。
- 后端验证符合当前阶段要求：中间 checkpoint 至少编译通过，里程碑 stage finish 必须跑相关目标集成测试，路线图最终 finish 跑完整 `mvn test`。
- 受影响前端的 lint/build 通过；路线最终 finish 执行完整前端 lint/build。
- 安全扫描无阻断问题。
- 权限和审计点已考虑。
- 文档或 README 已同步必要变化。
- 最终回复包含验证结果和剩余风险。
- 路线图与执行报告状态一致，且不存在 `TODO`、`TBD`、`Pending`、“待执行”或空占位。
- `Remaining Gaps` 不包含关联当前任务的验收阻断项，质量门的独立完成证据核对通过。

## 11. 活动工作台命令

唯一活动实现位于 `tools/workbench`，由 Node.js 22+ 和 TypeScript 驱动。根 `package.json` 提供稳定入口：

| 命令 | 用途 |
| --- | --- |
| `pnpm verify` / `pnpm verify:full` | 快速或路线级完整质量门禁 |
| `pnpm audit:snapshot` | 生成本地审计快照 |
| `pnpm work:start` / `work:checkpoint` / `work:finish` | 工作循环三个阶段 |
| `pnpm work:plan-check` | 校验 Program、Stage、当前路线和 Task 规划合同 |
| `pnpm security:audit` / `security:scan` | 安全 guardrail 与敏感信息扫描 |
| `pnpm kb:naming-guard` / `kb:consistency-check` | 知识库命名和数据一致性检查 |
| `pnpm ops:backup` / `ops:restore-drill` / `ops:health` | 备份、恢复演练和语义健康检查 |
| `pnpm pilot:contract-check` / `pilot:initialize` / `pilot:readiness` | 受控试点合同、初始化和准入 |
| `pnpm work:test` | 工作台单元与契约测试 |

旧 Windows 实现只保留在显式归档目录作为回溯证据，不属于稳定工具清单。完整质量门禁会递归拒绝任何非归档 Windows 脚本，也会拒绝活动入口重新调用平台专用命令。

```shell
pnpm work:plan-check
pnpm work:start -- --goal PROJECT-PLATFORM-S01-M1 --task-range "PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M1-T09"
pnpm work:checkpoint -- --goal project-platform-s01-m1
pnpm work:finish -- --goal project-platform-s01-m1 --backend-test-pattern ProjectControllerIntegrationTests --browser-spec e2e/cross-module-route-final.spec.ts --browser-evidence-kind real --browser-evidence-environment isolated
pnpm verify:full
```

## 12. CI 模板边界

仓库维护生效中的 GitHub Actions：

```text
.github/workflows/ci.yml
```

CI 的应用验证在 Linux 执行，同时以 Windows、macOS、Linux 矩阵验证工作台本身：

- 安装 Java 21。
- 安装 Node.js 24 和 pnpm 9.4.0。
- 启动 PostgreSQL、Redis、MinIO。
- 在 `server/` 执行 `mvn -B test package`。
- 执行 `pnpm web:lint` 和 `pnpm web:build`。
- 三系统执行工作台 typecheck、契约测试、敏感扫描和试点合同检查。
- 上传 Surefire 报告和 `web/dist` 作为 CI artifacts。

本地 AI 门禁额外检查安全规则、迁移顺序、知识库命名和文档契约。`.local-reports/`、`.local-logs/`、`.local-backups/`、环境文件和密钥不提交远程。CI 不能替代破坏性运维操作的人工确认，也不能替代真实浏览器和业务验收。

## 13. 下一路线执行要求

新路线开始前必须先阅读当前产品范围、当前架构、平台对象模型、当前路线引用的长期专项、对应目标架构和唯一活动路线图；只有目标明确后才把任务写入 `docs/02-roadmap/current-roadmap.md`。执行前先运行 `pnpm work:plan-check`，再使用 `pnpm work:start` 建立审计记录，并按任务风险选择 `light`、`stage` 或 `route-final` 验证档位。

普通路线开始前不要求先运行全量测试。完整 `pnpm verify:full`、完整后端测试和全量迁移验证只在当前路线最终收口、发布门禁或用户明确要求时执行；中间里程碑只验证本阶段改动及其直接影响范围。
