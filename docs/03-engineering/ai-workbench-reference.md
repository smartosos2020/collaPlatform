---
title: AI 工作循环工作台实现参考
status: active
last_code_check: 2026-07-25
---

# AI 工作循环工作台实现参考

## 1. 文档定位

本文记录 `tools/workbench` 的实现边界、门禁选择和证据格式，供维护工作台时按需读取。日常业务开发仍以 `ai-engineering-governance.md` 为唯一默认治理入口，不需要默认加载本文。

## 2. 设计边界

工作台采用“轻核心、可组合门禁、按需命令域”结构：

| 层 | 当前职责 |
| --- | --- |
| 工作循环核心 | 规划合同、单里程碑上下文、Git 基线、证据合同、完成状态 |
| 门禁规划 | 根据验证档位和受影响区域选择检查，不使用一个总开关代表所有审计 |
| 证据执行 | 浏览器证据与真实隔离系统证据，输出本地日志和机器可校验元数据 |
| 命令域 | architecture、security、knowledge、operations、pilot、browser 按命令动态加载 |
| 适配层 | Git、进程、路径、文件系统及 Docker 命令的跨平台调用 |

根 `package.json` 的稳定命令名保持兼容。命令实现可以拆分，但不能要求业务路线修改已有调用方式。

## 3. 受影响区域

工作循环按路径计算受影响区域：

| 路径 | 区域 |
| --- | --- |
| `server/**` | backend |
| `web/**` | frontend |
| `collaboration/**` | collaboration |
| `tools/workbench/**` | workbench |
| `docs/**`、`scripts/**` | governance |
| `deploy/**` | operations |
| 根 workspace/lock 文件 | workspace |

未知路径回退为 workspace，以保留保守检查。`tools/workbench/**` 不属于 frontend。仅调整工作台时不得自动触发 Web lint/build。

## 4. 门禁规划

### 4.1 Light

用于 checkpoint：

- 工具链、活动规划、架构边界和工作循环文档合同。
- 受影响区域对应的编译或 lint。
- 工作台受影响时执行工作台 typecheck。
- 不启动 Docker，不执行 Flyway 顺序、全仓审计或历史完整测试。

### 4.2 Stage

用于普通里程碑 finish：

- Light 的全部合同。
- 后端目标测试、前端完整构建、collaboration 测试按影响域执行。
- `git diff --check`。
- 敏感信息、生成产物和实现标记检查。
- backend 受影响时增加 Mockito、安全和知识命名检查。
- workbench/governance 受影响时增加活动脚本平台或文档结构检查。
- 工作台受影响时执行工作台 typecheck 和完整契约测试。
- 不执行 Docker 依赖启动和 Flyway 全仓顺序检查。

### 4.3 Route Final

用于 Stage 最终里程碑和明确要求的完整回归：

- 后端历史测试与 package、前端 lint/build、collaboration 测试。
- Docker 依赖健康等待。
- 全部静态、安全、迁移、知识命名、文档和工作台合同。
- 真实浏览器或明确的非浏览器理由，以及任务声明要求的真实隔离系统证据。

`--skip-audit` 只保留为直接 `verify` 的兼容参数。工作循环不得用它代替门禁规划。

## 5. 真实隔离系统证据

当执行报告存在 `system-real-isolated` 任务时，`work:finish` 必须执行真实命令：

```shell
pnpm work:finish -- \
  --browser-not-required-reason "本里程碑使用真实 API、Worker 和隔离数据库验证，不包含浏览器流程" \
  --system-evidence-command node \
  --system-evidence-arg deploy/verification/isolated-flow.mjs
```

以 `-` 开头的命令参数使用内联格式，例如：

```shell
--system-evidence-arg=--configuration
```

工作台从执行报告自动提取所有 `system-real-isolated` Task，并记录：

- `environment = isolated`
- 实际 executable 与参数
- 覆盖的 Task ID
- 本轮新生成的 `.local-reports/work-cycle-system-*.log`
- 日志 SHA-256
- 完成时间

finish 会拒绝缺失证据、Task 覆盖不一致、旧日志、越界工作目录和校验和不一致。证据命令默认在仓库根目录执行；`--system-evidence-cwd` 只能指向仓库内部。

新工作循环使用 Verification Contract v3，必须显式填写 `Closure class`：

| Closure class | 要求 |
| --- | --- |
| `non-core` | 根据任务风险使用 static、unit 或 integration |
| `core-user` | 必须使用 `e2e-real-isolated` |
| `core-system` | 必须使用 `system-real-isolated` |

旧 v2 执行上下文继续兼容原六列表格和关键词推断，但不会再生成新的 v2 报告。

## 6. 上下文与输出

- `work-cycle-current.json` 使用独立 `schemaVersion`，旧上下文在读取时迁移默认字段，未来版本必须显式拒绝。
- 默认 `compact` 模式只在控制台显示失败和最终报告路径；完整步骤结果、警告和命令输出保存在 `.local-reports`。
- full 审计快照只遍历一次源码清单，并复用同一份结果计算数量和输出明细。
- AI 默认读取质量报告摘要；只有失败时读取相关 step log 的末段或定位片段。

## 7. 执行报告合同

`work:start` 生成以下结构，调用方不应手工创建另一种模板：

```md
# {QUALIFIED-MILESTONE} Execution Report

## Scope

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |

## Completed Items

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |

## Code Changes

## Validation

- Backend tests:
- Frontend build:
- Local quality gate:
- Browser smoke:

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
```

严格 finish 会逐项检查：

1. Task 在两张证据表中各出现一次。
2. 验收标准、实现、自动化和浏览器字段不是占位。
3. 报告与路线图 Task 同时为 `Done`。
4. Validation 引用本轮 start 后生成的质量日志。
5. Remaining Gaps 不包含当前 Task 的验收阻断。
6. 浏览器或系统证据类型、环境、时间、文件和 Task 覆盖与 Contract 一致。
7. 本轮必更文档相对 start 基线确实发生变化。

### 7.1 浏览器证据

真实证据不得通过 `page.route`、`context.route`、`route.fulfill`、`route.abort` 或伪造 token/API 响应代替。安全检查扫描静态导入、动态导入、CommonJS、重导出、路径别名及仓库内本地依赖闭包。

真实浏览器：

```shell
--browser-spec e2e/example.spec.ts \
--browser-evidence-kind real \
--browser-evidence-environment isolated
```

页面级 mock：

```shell
--browser-spec e2e/example.mock.spec.ts \
--browser-evidence-kind mock \
--browser-evidence-environment mock
```

无浏览器流程必须使用至少 20 个字符的具体 `--browser-not-required-reason`。

### 7.2 审计快照

`work:start` 自动生成 full 快照；checkpoint 和普通 finish 生成 light 快照；route-final 生成 full 快照。手工入口：

```shell
pnpm audit:snapshot -- --label <label>
```

报告只写入 `.local-reports/`，包括工具链、Compose、Git、源文件清单、时间和标签。

## 8. 命令域

| 命令 | 用途 |
| --- | --- |
| `pnpm verify` / `pnpm verify:full` | 直接质量门禁 |
| `pnpm architecture:inventory` | 生成后端、前端、Flyway、SQL 和运行组件清单 |
| `pnpm architecture:contracts` | 校验模块、公开合同、table owner、例外和 ADR |
| `pnpm architecture:boundaries` | 校验模块依赖、SCC、共享反向依赖和跨 owner SQL |
| `pnpm security:audit` / `pnpm security:scan` | 安全 guardrail 和敏感信息扫描 |
| `pnpm kb:naming-guard` / `pnpm kb:consistency-check` | 知识库命名和数据一致性 |
| `pnpm ops:backup` / `pnpm ops:restore-drill` / `pnpm ops:health` | 运维验证 |
| `pnpm pilot:contract-check` / `pnpm pilot:initialize` / `pnpm pilot:readiness` | 受控试点 |
| `pnpm work:test` | 工作台单元和契约测试 |

旧 Windows 实现只允许存在于归档目录。活动入口、文档和脚本不得依赖 PowerShell、批处理或平台专用调用。

架构清单使用 AST/语法级解析处理 Java import、TypeScript 静态/动态导入、重导出、CommonJS、路径别名和 tsconfig paths；Flyway 清单按版本顺序应用表创建、重命名和删除语义。机器基线不能被不同口径的手工数字覆盖。

## 9. CI 边界

`.github/workflows/ci.yml` 在 Linux 执行应用验证，并使用 Windows、macOS、Linux 矩阵验证工作台：

- Java 21、Node.js、pnpm 和基础设施依赖。
- 后端测试/package、前端 lint/build、协作服务测试。
- 工作台 typecheck、契约测试、敏感扫描和试点合同。
- Surefire 与构建产物作为 CI artifacts。

CI 不能替代破坏性运维的人工确认，也不能替代真实浏览器或业务验收。

## 10. 并行工作树

业务路线与工作台维护并行时：

- 业务工作树持有当前路线、执行报告和业务代码。
- 工作台工作树持有 `tools/workbench/src/**` 与 `tools/workbench/test/**`。
- 根 `package.json`、治理文档和工作台配置属于共享文件，修改前按文件同步。
- 不对另一工作树执行 stash、reset、checkout 或删除。
- 双方先形成独立干净提交，再由集成分支同步主干。
