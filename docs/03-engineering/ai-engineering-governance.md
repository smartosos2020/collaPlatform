---
title: AI 编程工程治理规范
status: active
---

# AI 编程工程治理规范

## 1. 定位

本文是 AI 参与本仓库开发时的默认治理入口，定义不可跳过的工作循环、规划合同、证据要求和完成标准。它适用于后端、前端、协作服务、数据库迁移、部署、脚本和文档。

日常业务开发默认只需读取本文、当前路线及其引用的专项和架构文档。工作台实现、全部参数、报告表格和维护细节按需读取 `docs/03-engineering/ai-workbench-reference.md`，不要把维护手册加入每轮默认上下文。

## 2. 核心原则

### 2.1 先读后改

修改前必须读取当前模块代码、调用合同、迁移、测试和相关 active 文档。允许先用 `rg` 定位，但不能只凭命中片段修改：

- 要编辑的代码必须读取完整文件或完整类/函数边界。
- 路线、专项、权限、安全、迁移和质量规则必须读取完整语义章节；边界不清时读取全文。
- 长日志成功时只读摘要和报告路径，失败时只读错误段、尾部和对应报告。
- 修改后反查旧名称、旧口径和冲突描述。

禁止凭记忆重写未知模块，也禁止为了“顺手整理”扩大修改范围。

### 2.2 小步闭环

一个工作循环只收口一个 Milestone。同一 Milestone 的 Task 数量没有固定上限，是否拆轮由依赖、风险、验证成本和可审计性决定，不使用机械的“最多 8 项”规则。

用户一次给出多个 Milestone 时，必须逐个执行独立的：

```text
start -> implement -> checkpoint(s) -> finish
```

不得跨 Milestone 或跨 Stage 共用上下文、报告或完成证据。

### 2.3 架构和所有权优先

项目采用模块化单体和独立协作服务：

- Controller 只处理协议，Application service 编排用例、事务和权限。
- Domain 承载领域状态和规则，Infrastructure 承载数据库与外部系统。
- 跨模块写入必须经过公开合同、模块服务或领域事件。
- 权限、文件和平台对象能力分别由其 owner 模块维护。
- 不修改已发布 Flyway，只能新增迁移。
- 不恢复 active 产品和架构文档已经删除的兼容模型。

具体对象、模块和表所有权以当前架构文档和机器合同为准，不在本文复制易过期清单。

### 2.4 可验证交付

编译、lint、build、package 或迁移成功只能证明工程健康，不能单独证明业务任务完成。每个 Task 必须具备可复核的实现证据、自动化证据，以及浏览器证据或具体不适用理由。

没有验证、只有口头声明或仍存在验收阻断 Gap 的任务不得标记 `Done`。

## 3. 规划合同

长期工作使用：

```text
Program -> Stage -> Milestone -> Task
```

- `docs/00-product/initiatives/README.md` 维护唯一 Active Program 和需保留的 Paused Program。
- Program 文档维护 Stage 索引、依赖、退出条件、候选池和变更记录，不维护可执行 Task 状态。
- `docs/02-roadmap/current-roadmap.md` 是唯一执行入口，每次只承载一个 Stage。
- 当前路线必须声明 `program`、`program_doc`、`program_revision`、`stage`、`stage_final_milestone`，且 `route = stage`。
- Task 使用 `{PROGRAM}-SXX-MX-TYY`，必须在当前路线恰好出现一次。
- 合法状态为 `Pending`、`In Progress`、`Reopened`、`Done`、`Completed`、`Deferred`、`Paused`、`Blocked`。
- `work:start` 拒绝不存在、跨 Stage、已完成或暂停/阻断中的 Task。恢复暂停任务必须先在规划中改为 `Pending` 或 `Reopened`。

Stage 执行中原则上冻结。目标、依赖或远期规划变化必须更新 Program 变更记录和 revision，并同步目标架构及当前路线。

Stage 最终 Milestone 必须使用 `route-final`。收口时：

- 当前路线全部 Task 为 `Done`，路线状态为 `completed`。
- 当前 Stage 为 `Completed`，Program 和专项索引的 `current_stage` 暂置为 `none`。
- Program、目标架构和当前路线 revision 同步。
- 归档当前路线后，才能激活下一 Stage。

规划合同可独立检查：

```shell
pnpm work:plan-check
```

## 4. 工作循环

### 4.1 Start

```shell
pnpm work:start -- --goal project-platform-s01-m1 --task-range "PROJECT-PLATFORM-S01-M1-T01 到 PROJECT-PLATFORM-S01-M1-T09"
```

Start 必须：

- 校验 Program、Stage、Milestone 和 Task。
- 记录 Git 基线、启动前脏文件签名和必更文档签名。
- 创建本地工作上下文和审计快照。
- 创建或复用 `docs/90-reports/{qualified-milestone}-execution-report.md`。
- 明确本轮目标、不做范围和验证级别。

如果已有进行中的上下文，不得覆盖；先完成、取消或明确重开。

### 4.2 Checkpoint

```shell
pnpm work:checkpoint -- --goal project-platform-s01-m1
```

每个可运行小闭环后执行 checkpoint：

- 根据 start 后的真实变更选择受影响区域。
- 只做当前小闭环需要的编译、lint、类型和结构检查。
- 不启动 Docker，不运行完整历史测试，不重复全仓审计。
- 发现本轮新增失败立即修复，不把失败堆到 finish。

高风险后端竖切可提前显式执行目标测试：

```shell
pnpm work:checkpoint -- --goal project-platform-s01-m1 --validation-profile stage --backend-test-pattern ProjectControllerIntegrationTests
```

### 4.3 Finish

普通 Milestone：

```shell
pnpm work:finish -- --goal project-platform-s01-m1 --backend-test-pattern ProjectControllerIntegrationTests --browser-not-required-reason "本里程碑只调整服务合同，不包含用户可见界面或浏览器交互"
```

Finish 必须：

- 默认使用 `stage`，只验证本里程碑和直接影响范围。
- 有后端变更时运行对应目标测试，不允许用“仅编译”代替。
- 有前端变更时运行 lint/build 和本轮相关页面验证。
- 在 `--browser-spec` 与具体的 `--browser-not-required-reason` 中二选一。
- 更新当前路线、执行报告和受影响的 active 真相文档。
- 通过任务证据、文档边界、Gap、Git diff 和所选质量门禁。

Stage 最终 Milestone：

```shell
pnpm work:finish -- --goal project-platform-s01 --validation-profile route-final --browser-spec e2e/cross-module-route-final.spec.ts --browser-evidence-kind real --browser-evidence-environment isolated
```

`route-final` 才执行完整历史后端测试、package、完整前端构建、collaboration 测试、Docker 依赖、Flyway 顺序和全仓静态审计。普通 checkpoint 和 stage finish 不重复这些高成本检查。

## 5. 文档和证据合同

默认 `DocMode` 为 `code-doc-report`。只有用户明确要求文档整理、迁移或归档时才使用 `archive-only`。

每轮 `code-doc-report` 必须更新：

- `docs/02-roadmap/current-roadmap.md`
- `docs/90-reports/{qualified-milestone}-execution-report.md`
- 受影响的产品、架构、技术选型或治理真相文档
- Stage 最终 Milestone 对应的 Program 和目标架构

禁止：

- 新建第二份 active roadmap。
- 在 `docs/` 根目录新增自由格式 Markdown。
- 在非归档任务中编辑 `docs/99-archive/`。
- 用新的兼容文档复制 active 真相。

### 5.1 Task 证据

执行报告由工作台生成模板。当前范围内每个 Task 必须且只能有：

1. 一行 `Verification Contract`。
2. 一行六列 `Acceptance Evidence`。
3. 与路线图一致的 `Done` 状态。

`Acceptance Evidence` 必须包含具体验收标准、实现位置、实际自动化命令与结果，以及浏览器证据或不适用理由。`TODO`、`TBD`、`Pending`、“待执行”和空占位会阻断 finish。

### 5.2 闭包类型

新工作循环使用显式 `Closure class`：

| 类型 | 最低验证 |
| --- | --- |
| `non-core` | 按风险使用 static、unit 或 integration |
| `core-user` | `e2e-real-isolated` |
| `core-system` | `system-real-isolated` |

认证、权限、创建/修改/删除资源、安全策略、会话、交接、导出和审计等用户可见核心闭环不得用 mock 证明完成。纯 API、WebSocket、Yjs、Worker 或数据库闭环可使用真实隔离系统证据。

存在 `system-real-isolated` Task 时，finish 必须传入结构化 executable 和参数：

```shell
pnpm work:finish -- --goal project-platform-s01-m1 \
  --browser-not-required-reason "本里程碑使用真实服务和隔离数据库验证，不包含浏览器交互" \
  --system-evidence-command node \
  --system-evidence-arg deploy/verification/isolated-flow.mjs
```

工作台会校验任务覆盖、环境、日志新鲜度和 SHA-256。完整字段和表格格式见工作台参考文档。

### 5.3 Remaining Gaps

- `Remaining Gaps` 必须明确写 `None` 或列出具体风险。
- 当前 Task 存在未实现、缺失或仅支持旧能力等验收阻断时，不能写 `non-blocking`。
- 事后发现证据不足时，必须把 Task 改为 `Reopened`，并重新执行独立工作循环。

## 6. 验证档位

| 档位 | 使用时机 | 默认边界 |
| --- | --- | --- |
| `light` | checkpoint | 受影响区域编译/lint/typecheck、规划、架构和文档合同 |
| `stage` | 普通 Milestone finish | 目标测试、受影响构建、任务证据、必要静态检查 |
| `route-final` | Stage 最终收口、发布或用户明确要求 | 完整历史回归、Docker、迁移、全仓审计 |

门禁由路径影响域选择，不用单一 `skipAudit` 开关代表全部检查：

- `server/**` -> backend
- `web/**` -> frontend
- `collaboration/**` -> collaboration
- `tools/workbench/**` -> workbench
- `docs/**`、`scripts/**` -> governance
- `deploy/**` -> operations
- workspace 和 lock 文件 -> workspace

未知路径保守归入 workspace，不能静默成为“无影响”变更。`tools/workbench/**` 不属于 frontend，工作台改动不能触发无关 Web build。`--skip-audit` 只保留为直接 `verify` 的兼容参数，不得用于工作循环降级。

门禁失败时：

1. 优先修复本轮引入的问题。
2. 已有问题必须记录来源和影响，不能伪装为本轮通过。
3. 安全、权限、迁移、编译和验收阻断不得忽略。
4. 同一阻断连续三次仍无法解决时停止叠加功能并报告。

## 7. 工程底线

### 7.1 通用

- 新代码默认 ASCII；中文用于文档和明确 UI 文案。
- 不提交 `.env`、日志、构建产物、本地报告或真实个人数据。
- 不引入无用途依赖，不做无关重构，不撤销其他工作树的改动。
- 结构化数据使用解析器和类型合同，不用脆弱字符串替换。

### 7.2 后端和数据库

- Java 21，Spring Boot 3.5.x。
- 写操作必须经过权限判断，关键写操作必须可审计或产生领域事件。
- 状态流转通过专门服务；DTO 不直接暴露 Entity。
- 表和字段使用小写蛇形，业务表保留 `workspace_id`。
- 外键、查询和排序字段考虑索引，软删除由查询层统一过滤。

### 7.3 前端

- 页面负责组装，复杂业务规则放入模块服务、hook 或状态层。
- API 调用统一放在模块 `api/` 或 `shared/api/`，不在组件写裸 `fetch`。
- 远程状态优先 TanStack Query，本地跨组件状态才使用 Zustand。
- 权限显示使用统一组件或 hook。
- 页面保持可扫描、稳定、无重叠，并提供基本窄屏降级。

### 7.4 安全

禁止提交明文密码、真实 token、私钥、云密钥、生产连接串和真实隐私数据。扫描脚本只是兜底，不能代替主动审查。

认证、成员和权限变更、项目成员变更、文件下载授权、知识内容/表格权限和关键对象删除必须可审计。

## 8. 并行工作树

并行开发必须从已知主干提交创建独立分支和 worktree，并按文件所有权隔离：

- 业务会话持有业务代码、当前路线和执行报告。
- 专项会话只持有已声明的模块和测试。
- 根配置、治理文档和工作台配置是共享文件，修改前必须同步。
- 不对另一工作树执行 stash、reset、checkout、clean 或删除。
- 合并前双方重新检查共享路径和主干漂移。

共享文件发生冲突时先停止修改并协商 owner，不能用覆盖式合并解决。

## 9. 完成定义

Task 只有同时满足以下条件才算完成：

- 行为符合路线验收标准，架构、权限、审计和数据边界正确。
- 对应实现和迁移可执行。
- Verification Contract 与真实验证一致。
- 自动化和浏览器/系统证据真实、新鲜、可复核。
- 路线图和报告状态一致，无占位或验收阻断 Gap。
- 受影响文档已同步。
- 所选质量门禁通过，失败和跳过项已明确。
- 最终交付说明列出文件、行为、验证和剩余风险。

## 10. 稳定入口

唯一活动实现位于 `tools/workbench`，使用 Node.js 和 TypeScript。日常稳定入口：

```shell
pnpm work:plan-check
pnpm work:start -- --goal <goal> --task-range "<qualified-task-range>"
pnpm work:checkpoint -- --goal <goal>
pnpm work:finish -- --goal <goal> <verification-options>
pnpm verify
pnpm verify:full
pnpm work:test
```

活动命令支持无副作用 `--help`。完整命令目录、CI、审计快照、浏览器证据、系统证据、报告表格和工作台维护规则见：

- `docs/03-engineering/ai-workbench-reference.md`
- `scripts/README.md`

本地报告位于 `.local-reports/`，不提交仓库。
