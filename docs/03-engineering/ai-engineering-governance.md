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

当用户输入“按 AI 工作循环推进 MXX-T01 到 MXX-T08”时，默认必须执行该闭环。

当前保留的标准协同仿真基线为 M31 多角色协同仿真。后续只有在用户明确要求“重置数据库”“恢复 M31 测试基线”“执行 M31 跨模块流程回归”时，才运行 `pnpm data:reset` 或 `pnpm smoke:m31`；日常 AI 工作循环不自动重置数据，也不自动跑 M31 全链路回归。

M40 试运行准入是一个例外：当里程碑任务本身明确要求“试运行前完整回归”时，必须显式执行 `pnpm verify:full`、`pnpm data:reset`、`pnpm smoke:m31`，并用 `pnpm trial:readiness -- -RequireFullGate -RequireDataReset -M31SmokePassed` 生成 Go/No-Go 证据。

### 4.1 开始前

执行：

```powershell
pnpm work:start -- -Goal "M25-delivery" -TaskRange "M25-T01 到 M25-T08"
```

要求：

- 生成审计快照。
- 确认当前工程状态。
- 明确本轮目标和不做范围。
- 写入 `.local-reports/work-cycle-current.json`。
- 输出 Document Contract。
- 如果能识别 MXX，自动创建或复用 `docs/90-reports/mxx-execution-report.md`。

### 4.2 工作中

每完成一个可运行小闭环，执行：

```powershell
pnpm work:checkpoint -- -Goal "M1-auth" -GateMode quick
```

要求：

- 默认运行 `light` 验证档位：后端只编译不运行测试，前端 lint/build 和静态门禁使用紧凑输出。
- 修复新增失败。
- 不把失败堆到最后。
- checkpoint 不默认触发 Spring Boot 集成测试，因此不默认触发 Testcontainers PostgreSQL 和 Flyway 空库全量迁移。
- quick 门禁会检查文档结构，并对尚未更新的工作循环文档给出警告。

如果工作中确实需要提前验证某个高风险后端竖切，可以显式执行阶段级目标测试：

```powershell
pnpm work:checkpoint -- -Goal "M1-auth" -ValidationProfile stage -BackendTestPattern "AuthControllerIntegrationTests"
```

### 4.3 结束前

执行：

```powershell
pnpm work:finish -- -Goal "M1-auth"
```

要求：

- 默认运行 `stage` 验证档位，而不是全量历史回归。
- 如果传入 `-BackendTestPattern`，只运行本里程碑相关后端集成测试；如果未传入，则后端只编译并必须在执行报告记录未跑集成测试的原因。
- 运行严格文档闭环检查、前端 lint/build、后端打包、安全扫描、迁移顺序、生成产物和工作循环契约检查。
- 生成结束审计快照。
- 输出最终交付说明。
- full 门禁必须通过文档闭环检查。

当前执行路线图的最后一个里程碑完成后，必须显式执行路线级最终验证：

```powershell
pnpm work:finish -- -Goal "identity-permission-route" -ValidationProfile route-final
```

`route-final` 才运行完整 `mvn test`、完整 Spring Boot 集成测试集合和由测试触发的 Flyway 空库迁移验证。

### 4.4 文档同步契约

默认 `DocMode` 为 `code-doc-report`。除非用户明确要求归档整理，否则不得切换为其他模式。

每轮工作循环必须：

- 更新 `docs/02-roadmap/current-roadmap.md`。
- 创建或更新 `docs/90-reports/mxx-execution-report.md`。
- 在执行报告中记录完成项、代码变更、文档变更、验证结果、遗留 Gap 和下一步。
- 按影响范围更新 active 真相文档：
  - 产品能力变化：`docs/00-product/current-product-scope.md`
  - 架构、API、数据库、WebSocket、搜索、权限变化：`docs/01-architecture/current-architecture.md`
  - 平台对象、内部链接、对象卡片、搜索对象类型变化：`docs/01-architecture/platform-object-model.md`
  - 技术栈变化：`docs/01-architecture/technology-selection.md`
  - AI 工作规则、脚本、门禁变化：`docs/03-engineering/ai-engineering-governance.md`

每轮工作循环禁止：

- 新建 roadmap 文件。
- 在 `docs/` 根目录新增 Markdown，`README.md` 除外。
- 在非归档任务中编辑 `docs/99-archive/`。
- 创建自由格式的 `mXX-*.md`、`next-plan.md`、`post-mXX-roadmap.md`。

固定执行报告格式：

```md
---
title: MXX 执行报告
status: archived
milestone: MXX
updated_at: YYYY-MM-DD
---

# MXX 执行报告

## 本轮范围

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |

## 代码变更

- 后端：
- 前端：
- 数据库：
- 脚本：

## 文档变更

| 文档 | 动作 | 原因 |
| --- | --- | --- |

## 验证

- 后端测试：
- 前端构建：
- `pnpm verify`：
- 浏览器冒烟：

## 遗留 Gap

## 下一步
```

### 4.5 文档模式

| DocMode | 用途 | 允许行为 |
| --- | --- | --- |
| `code-doc-report` | 默认迭代闭环 | 代码实现、active 文档同步、路线图更新、执行报告更新 |
| `archive-only` | 专门文档整理 | 只整理、迁移、归档文档，不改业务代码 |

`archive-only` 只能在用户明确要求“文档整理、归档、迁移”时使用。

### 4.6 单轮范围限制

用户可以一次性描述较大的目标范围，但真正进入 `code-doc-report` 工作循环时，必须拆成可验证的小范围。

单个工作循环上限：

- 最多覆盖 1 个里程碑。
- 最多覆盖 8 个任务，例如 `M25-T01 到 M25-T08`。
- 不允许用一个工作循环直接覆盖 `M25-T01 到 M26-T08` 这类跨里程碑范围。
- 不允许用一个工作循环直接覆盖 `M25-T01 到 M25-T12` 这类超过 8 个任务的范围。

当用户输入范围超过上限时，AI 必须自动拆分，并且只启动第一个合规工作块。例如：

- 用户输入 `按 AI 工作循环推进 M26-T01 到 M30-T08`。
- AI 应先启动 `M26-T01 到 M26-T08`。
- 完成代码、文档、验证报告闭环后，再继续下一个合规工作块。

同一个会话不等于同一个工作循环。会话上下文可以连续保留，但每个合规工作块都必须重新经历 `start -> checkpoint -> finish`，并生成或更新对应的执行报告和质量门禁结果。

### 4.7 浏览器冒烟验证

当前端页面、路由、API 契约、认证会话、WebSocket 或影响布局的 CSS 发生变化时，完成代码验证后必须进行浏览器冒烟验证。

最低要求：

- 启动本地依赖、后端和前端。
- 使用 `http://127.0.0.1:5173/login` 登录。
- 使用 `admin / admin123456` 或本轮明确指定的测试账号。
- 打开本轮影响的页面和至少一个相关主导航入口。
- 在执行报告中记录目标 URL、账号、检查页面、结果和跳过原因。

详细步骤见 `docs/05-runbooks/browser-smoke.md`。

该要求指本轮影响范围内的局部浏览器冒烟，不等同于 M31 全链路回归。只有用户明确要求 M31 基线回归时，才执行 `pnpm smoke:m31`。

双 UI、Shell、路由、用户菜单、后台导航、搜索/通知/平台对象边界发生变化时，优先运行：

```powershell
pnpm smoke:ui-split
```

该脚本覆盖用户工作台主入口、管理后台主入口、Shell 隔离、头像菜单中的后台入口和后台返回工作台入口。

### 4.8 M31 数据基线与流程回归

M31 是当前保留的标准协同仿真数据集，但不是每轮工作循环的默认动作。

显式数据重置命令：

```powershell
pnpm data:reset
```

等价于执行 M31 初始化：

```powershell
pnpm sim:m31
```

该命令会清理共享本地业务测试数据，保留默认工作区、内置角色和权限，并生成固定 10 人角色团队与 5 个项目场景。

### 4.9 试运行准入门禁

M40 引入小团队真实试运行准入脚本：

```powershell
pnpm trial:team-template
pnpm trial:readiness
pnpm trial:readiness -- -RequireFullGate -RequireDataReset -M31SmokePassed
```

`trial:team-template` 生成 10 人试运行账号/角色 CSV 和初始化报告；`trial:readiness` 检查准入 runbook、管理员运维手册、Issue 模板、质量门禁、安全审计、性能基线、恢复演练、健康检查和 M31 回归证据。带强制参数的 readiness 只应在试运行前完整回归完成后执行。

显式 M31 跨模块流程回归命令：

```powershell
pnpm smoke:m31
```

使用规则：

- 只有用户明确要求重置，或明确要求恢复干净 M31 演示/回归数据时，才运行 `pnpm data:reset`。
- `pnpm verify` 和 `pnpm verify:full` 会运行后端集成测试；M39 起后端测试默认使用 `application-test.yml` + Testcontainers PostgreSQL，不再写入共享本地 M31 演示库。即使如此，也不要自动重置 M31 数据，除非用户明确要求恢复干净基线。
- 前端、路由、权限、内部链接、搜索、IM、项目、知识库内容、Base、审批等变更后，默认做本轮影响范围内的局部测试和必要浏览器冒烟。
- 只有用户明确要求“跑 M31 回归”“执行 M31 基线”“重置到 M31 并冒烟”等语义时，才运行 `pnpm smoke:m31`。
- M12 reset 脚本只用于历史 M12 报告复现，不作为日常 AI 工作循环数据基线。

### 4.10 知识库唯一入口约束

`knowledge_base_spaces` 是知识库空间唯一主模型；`/knowledge-bases`、`/api/knowledge-bases` 和 `web/src/modules/knowledgeBases` 是知识库产品能力的唯一入口。后续路线、需求文档和前端导航不得再扩展独立“文档模块”。

`/api/docs`、`web/src/modules/docs`、`Document*` 类、`documents` 表和 `document` objectType 是历史兼容命名与知识内容编辑底座，只能用于内容页编辑、块、评论、版本、分享、协同、旧 deep link 和跨模块对象兼容。新增用户侧能力必须以“知识库/知识内容”命名；只有在描述数据库、API 兼容、objectType 或历史里程碑时才使用 `document`/“文档”。

结构化 `document_blocks` 是知识内容正文 v2 的主事实来源。后续涉及正文编辑、版本、导入导出、搜索定位、评论定位和治理指标的实现，必须优先围绕 blocks 设计；`documents.content` 只作为旧 deep link、回滚、全文兼容快照和迁移对照保留。不得新增只写旧富文本字段的新功能。

知识库 v2 收口前必须保留迁移证据：`scripts/knowledge-base-migration-check.ps1` 覆盖旧富文本、legacy/html block、孤儿 block、失效嵌入对象和回滚模板；`scripts/knowledge-base-block-v2-trial.ps1` 覆盖创建块文档、导入旧内容、嵌入 Base、评论 block、搜索命中和导出。任一 FAIL 必须先修复，GO-WITH-REVIEW 必须在执行报告中说明。

### 4.11 上下文读取与 Token 控制

允许使用 `rg` 做发现和定位，但不能把 `rg` 命中的几行当作完整理解。为降低 token 消耗同时规避局部读取风险，按以下规则执行：

| 文件类型 | 读取要求 |
| --- | --- |
| 工作循环、路线图、质量门禁、权限、安全、迁移规范 | 必须完整读取目标规范文件，不能只读 `rg` 命中片段。 |
| 本轮要编辑的文档 | 至少完整读取 front matter、目录结构和将要修改的完整语义章节；如果章节边界不清，读取全文。 |
| 本轮要编辑的代码文件 | 读取完整文件或完整类/函数边界；不能只看单个匹配行就修改。 |
| 相邻调用方/被调用方 | 可先用 `rg` 定位，再读取完整函数、接口或 DTO 定义。 |
| 长日志和构建输出 | 成功时只读摘要和报告路径；失败时读取错误段、尾部日志和相关 report。 |

修改规范或脚本时，必须完整读取 `docs/03-engineering/ai-engineering-governance.md`、相关脚本和 `scripts/README.md`。修改路线图时，必须完整读取 `docs/02-roadmap/current-roadmap.md`。修改执行报告时，必须完整读取对应 `docs/90-reports/*-execution-report.md`。

局部读取后的安全补偿：

1. 修改前确认文件头、状态、适用范围和相邻章节。
2. 修改后用 `rg` 反查旧口径、命令名和冲突表述。
3. 对规范、路线图、报告执行文档结构门禁。
4. 如果发现同一主题有多个口径，以 active 真相文档为准，并同步修正文档冲突。

## 5. 质量门禁

### 5.1 快速门禁

命令：

```powershell
pnpm verify
```

覆盖：

- 工具链检查。
- Docker 依赖服务检查。
- 后端默认执行 `mvn -DskipTests test`，只做编译和测试编译，不运行 Surefire 测试。
- 前端 `pnpm web:lint`。
- 前端 `pnpm web:build`。
- 敏感信息扫描。
- 安全审计 guardrails：测试库隔离、生产密钥外置、安全路由、关键服务审计调用。
- Flyway 迁移顺序检查。
- 生成产物跟踪检查。
- 实现标记库存提示。

快速门禁不默认运行 Spring Boot 集成测试，因此通常不会触发 Testcontainers PostgreSQL 和 Flyway 空库全量迁移。需要目标集成测试时，使用 `-BackendStrategy targeted -BackendTestPattern "..."`。

### 5.2 完整门禁

命令：

```powershell
pnpm verify:full
```

在快速门禁基础上增加：

- 后端 `mvn test`，运行历史单元测试和集成测试。
- 后端 `mvn -DskipTests package`。
- 由 Spring Boot 集成测试触发 Testcontainers PostgreSQL 和 Flyway 空库迁移验证。
- 只用于当前执行路线图最终收口、试运行准入、CI 或用户明确要求的完整回归。

### 5.3 阶段门禁

阶段门禁用于单个里程碑收口，介于快速门禁和完整门禁之间：

```powershell
pnpm work:finish -- -Goal "ORG-M1-organization-foundation" -BackendTestPattern "OrganizationControllerIntegrationTests,AdminUserControllerIntegrationTests"
```

阶段门禁只运行本里程碑相关后端集成测试。由于 Spring Boot 集成测试仍会启动测试上下文，Flyway 可能仍会对临时测试库从 `V001` 跑到最新版本；因此不要在每个小任务中反复运行阶段门禁。

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
| E2E 测试 | 关键业务链路 | 登录、发消息、创建 Bug、收到通知 |

### 7.1.1 AI 工作循环验证节奏

| 时机 | 后端验证 | 前端验证 | 说明 |
| --- | --- | --- | --- |
| 单个任务开发中 | 编译或局部纯单元测试 | 类型/lint 局部检查 | 不默认触发集成测试。 |
| checkpoint | `mvn -DskipTests test` | `pnpm web:lint`、`pnpm web:build` | 快速发现编译、类型、路由和静态问题。 |
| 单个里程碑 finish | 相关集成测试，需显式 `-BackendTestPattern` | lint/build + 本轮页面冒烟 | 验证本阶段 API、权限、迁移和 JSON 合约。 |
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

```powershell
pnpm audit:snapshot -- -Label "before-M1-auth"
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
- 后端验证符合当前阶段要求：中间 checkpoint 至少编译通过，里程碑 finish 跑相关目标集成测试或记录跳过原因，路线图最终 finish 跑完整 `mvn test`。
- 前端 lint/build 通过。
- 安全扫描无阻断问题。
- 权限和审计点已考虑。
- 文档或 README 已同步必要变化。
- 最终回复包含验证结果和剩余风险。

## 11. 脚本清单

| 脚本 | 用途 |
| --- | --- |
| `scripts/ai-quality-gate.ps1` | 执行快速或完整质量门禁 |
| `scripts/ai-audit-snapshot.ps1` | 生成本地审计快照 |
| `scripts/ai-work-cycle.ps1` | 包装 start/checkpoint/finish 工作循环 |
| `scripts/m31-collab-simulation.ps1` | 显式要求时的标准本地数据重置，生成 M31 固定协同仿真数据 |
| `scripts/m31-browser-smoke.ps1` | 显式要求时基于 M31 数据执行跨模块浏览器流程回归 |
| `scripts/performance-baseline.ps1` | 生成关键 API 性能基线和阈值状态报告 |
| `scripts/security-audit-gate.ps1` | 执行安全与审计 guardrail 检查，已接入质量门禁 |
| `deploy/scripts/backup.ps1` | 生成 Postgres/MinIO 备份和 manifest |
| `deploy/scripts/restore-drill.ps1` | 校验备份 manifest、文件哈希和 compose 配置，默认 dry-run |
| `deploy/scripts/health-check.ps1` | 校验 API、Actuator health 和可选 Prometheus 指标 |

根目录命令：

```powershell
pnpm verify
pnpm verify:full
pnpm audit:snapshot
pnpm work:start
pnpm work:checkpoint
pnpm work:finish
pnpm data:reset
pnpm sim:m31
pnpm smoke:m31
pnpm perf:baseline
pnpm security:audit
pnpm ops:backup
pnpm ops:restore-drill
pnpm ops:health
```

## 12. CI 约束

仓库提供 GitHub Actions 模板：

```text
.github/workflows/ci.yml
```

CI 当前执行：

- 安装 Java 21。
- 安装 Node.js 24 和 pnpm 9.4.0。
- 启动 PostgreSQL、Redis、MinIO。
- 执行 `pnpm verify:full`，与本地完整质量门禁保持同一入口。
- 上传 `.local-reports`、Surefire 报告和 `web/dist` 作为 CI artifacts。

本地 `pnpm verify` 是 AI 长时间工作的主要门禁，CI 是远程合并门禁。两者必须保持口径一致。

## 13. 下一步执行要求

进入 M1 业务实现前，必须先执行：

```powershell
pnpm verify:full
pnpm audit:snapshot -- -Label "before-M1"
```

如果任一命令失败，先修复治理基线，再进入业务实现。
