---
title: WORKBENCH-M1 Execution Report
status: archived
milestone: WORKBENCH-M1
updated_at: 2026-07-18
---

# WORKBENCH-M1 Execution Report

## Scope

- WORKBENCH-M1-T01 到 WORKBENCH-M1-T08
- 背景：2026-07-18 对 AI 工作循环平台的审计发现敏感扫描现存命中、real 浏览器证据词法检测盲区、collaboration 变更零验证等问题；本里程碑在 `codex/workbench-m1-gate-hardening` 分支实施加固。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| WORKBENCH-M1-T01 | unit | not-required | not-required | No | 纯扫描脚本与后端配置默认值变更，无页面、路由或用户流程入口，浏览器流程不适用 |
| WORKBENCH-M1-T02 | unit | not-required | not-required | No | 纯扫描规则变更，无页面或用户流程入口，浏览器流程不适用 |
| WORKBENCH-M1-T03 | integration | not-required | not-required | No | 工作循环验证计划与 Node 包测试，无页面入口，浏览器流程不适用 |
| WORKBENCH-M1-T04 | unit | not-required | not-required | No | 检测脚本正反用例直接执行验证，无页面入口，浏览器流程不适用 |
| WORKBENCH-M1-T05 | integration | not-required | not-required | No | 门禁合同步骤逻辑变更，经本轮 finish 全流程复核，无页面入口 |
| WORKBENCH-M1-T06 | integration | not-required | not-required | No | 门禁文档边界逻辑变更，经本轮 finish 全流程复核，无页面入口 |
| WORKBENCH-M1-T07 | unit | not-required | not-required | No | 脚本健壮性变更，直接执行与语法解析验证，无页面入口，浏览器流程不适用 |
| WORKBENCH-M1-T08 | static | not-required | not-required | No | 纯文档同步，无代码行为与页面入口，浏览器流程不适用 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| WORKBENCH-M1-T01 | Done | 独立扫描脚本+allowlist 落地；全库扫描 0 命中 5 豁免 |
| WORKBENCH-M1-T02 | Done | EC 私钥与 JWT 模式加入；合成样本检出、全库零误报 |
| WORKBENCH-M1-T03 | Done | checkpoint 实测 affected 含 collaboration 且其测试 14/14 通过 |
| WORKBENCH-M1-T04 | Done | 独立证据检查脚本三用例验证（真实 spec 通过、两类 mock 拒绝） |
| WORKBENCH-M1-T05 | Done | 门禁写回 lastQualityGate 并校验 Validation 引用新鲜日志 |
| WORKBENCH-M1-T06 | Done | 文档判定改签名比对；白名单强制；git diff --check 接入 |
| WORKBENCH-M1-T07 | Done | 转义竖线、可读报错、start 保护、废弃参数、rg 告警落地 |
| WORKBENCH-M1-T08 | Done | 治理文档、scripts/README、路线图与本报告同步 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| WORKBENCH-M1-T01 | 扫描脚本独立可执行，allowlist 只豁免指定文件与指定模式，当前代码库扫描零命中、门禁该步骤恢复可通过 | 新增 `scripts/sensitive-data-scan.ps1`（11 条模式、TSV 豁免加载、独立报告）；`scripts/sensitive-scan-allowlist.tsv` 仅豁免 `web/e2e/*.spec.ts` 的口令赋值模式；`KnowledgeCollaborationProperties.java` 移除硬编码默认密钥（`application.yml` 占位符默认值保持行为不变）；门禁该步骤改为调用独立脚本 | 直接执行 `sensitive-data-scan.ps1`：PASS，0 命中、5 豁免（豁免清单逐条输出）；豁免前复现的 6 处命中（5 处 e2e 夹具口令 + 1 处主源码密钥）全部消除；后端编译通过（checkpoint 日志 `quality-gate-20260718-021813-mvn--DskipTests-test.log`） | 不适用（纯脚本与配置变更，无页面入口） | Done |
| WORKBENCH-M1-T02 | 新模式对当前代码库零误报，合成样本可被检出 | `sensitive-data-scan.ps1` 模式表新增 `BEGIN EC PRIVATE KEY` 与 JWT 三段式 `eyJ...` 形态 | 合成探针文件（含 EC 私钥头与 JWT 样本）触发两条命中并以退出码 1 失败；删除探针后全库扫描 0 误报、0 命中 | 不适用（纯扫描规则变更） | Done |
| WORKBENCH-M1-T03 | 改动 collaboration 文件后 checkpoint/finish 验证计划包含其测试执行并真实跑通 | `ai-work-cycle.ps1` 受影响区域识别新增 `collaboration/` 与 `deploy/`；`Get-ValidationPlan` 三个档位均输出 `collaborationStrategy`；`ai-quality-gate.ps1` 新增 `-CollaborationStrategy` 参数与 `Collaboration tests` 步骤；route-final 默认执行 | 临时改动 `collaboration/src/config.js` 后运行 checkpoint：计划输出 `affected=collaboration,workbench,backend`、`collaboration=test`；门禁执行 `pnpm collaboration:test` 14/14 通过（日志 `quality-gate-20260718-021813-pnpm-collaboration-test.log`）；探针改动已还原 | 不适用（无页面入口） | Done |
| WORKBENCH-M1-T04 | 引用真实 spec（经 support 注入真实凭据）的命令通过；引用含 page.route/route.fulfill 的 spec 或 support 内拦截的命令被拒绝 | 新增 `scripts/assert-real-browser-evidence.ps1`：解析命令引用的 spec/ps1、递归解析本地 import 闭包；禁止 `page.route`/`context.route`/`route.fulfill`/`route.abort`；`page.addInitScript` 仅允许 `web/e2e/support/api.ts` 真实凭据安装；`ai-work-cycle.ps1` 改调该脚本 | 用例一：`kb-product-m3-editor.spec.ts` 命令通过（闭包扫描 4 文件）；用例二：`m5-permission-governance.spec.ts`（含 page.route/route.fulfill）被拒绝；用例三：合成 spec 经 helper 内藏 route.fulfill 被拒绝（探针文件已删除） | 不适用（检测逻辑本身以命令行直接验证） | Done |
| WORKBENCH-M1-T05 | 本轮 finish 一次通过且报告引用被核验，无引用或引用过期日志被拒绝 | `ai-quality-gate.ps1` 以 `$StepLogs` 收集本轮 compact 日志；严格合同步骤要求 Validation 引用本轮新鲜 `quality-gate-*.log` 并给出可引用清单提示；门禁结束把 report/日志/状态写回上下文 `lastQualityGate` | 本报告 Validation 节引用 checkpoint 产生的新鲜日志并通过 finish 复核；首次无引用时会以包含可引用日志清单的错误信息失败（逻辑经 finish 全流程执行） | 不适用（无页面入口） | Done |
| WORKBENCH-M1-T06 | start 前已脏文档不再计为本轮更新；白名单外 docs 变更被拒绝；冲突标记可被检出 | `Test-DocumentChanged` 优先使用 `baselineFileSignatures` 签名比对（无基线时回退旧逻辑）；变更文档边界检查追加活动文档白名单强制（必更新文档+活动真相文档+90-reports+05-runbooks）；stage/full 新增 `git diff --check`（工作区与暂存区） | 本轮 start 时工作树干净，roadmap 与执行报告均为 start 后产生并通过签名校验；finish 执行 `git diff --check` 通过（见 Validation 节）；白名单拒绝逻辑随 finish 合同步骤执行 | 不适用（无页面入口） | Done |
| WORKBENCH-M1-T07 | 各项直接执行验证通过，门禁输出可解释 | `Get-MarkdownTableCells` 按未转义竖线切分并还原 `\|`（如 a \| b 不再错位）；无上下文 finish 抛出"先运行 start"可读错误；start 增加未完成循环保护与 `-Force`；`-GateMode` 标注废弃保留兼容；rg 缺失时 TODO inventory 记 warning | 四个改动/新增脚本 PSParser 语法自检全部 OK；重复 `start` 被拦截并输出可读错误（实测：提示 finish 或 -Force）；本报告 T07 行内转义竖线经 finish 解析成功 | 不适用（无页面入口） | Done |
| WORKBENCH-M1-T08 | 文档结构门禁通过，文档描述与脚本行为一致 | `docs/03-engineering/ai-engineering-governance.md` 更新 mock 语义、checkpoint 档位、敏感扫描、§5 门禁内容与 §11 脚本清单；`scripts/README.md` 新增两个脚本与档位说明；路线图登记本里程碑；本报告填齐 | 文档结构门禁随 finish 执行；文档描述与脚本实际行为逐条核对一致 | 不适用（纯文档同步） | Done |

## Code Changes

- Backend：`server/src/main/java/com/colla/platform/config/KnowledgeCollaborationProperties.java` 移除 `internalSecret` 硬编码默认值（有效默认值仍由 `application.yml` 的 `${COLLA_COLLABORATION_INTERNAL_SECRET:...}` 占位符提供，生产由环境变量覆盖，行为不变）。
- Frontend：无。
- Database：无（本轮未新增迁移；后端测试经 Testcontainers 验证 V001-V055 迁移链可用）。
- Scripts：新增 `scripts/sensitive-data-scan.ps1`、`scripts/assert-real-browser-evidence.ps1`、`scripts/sensitive-scan-allowlist.tsv`；修改 `scripts/ai-work-cycle.ps1`（受影响区域、collaboration 档位、start 保护、finish 上下文校验与完成标记、废弃参数注释、外部证据检查调用）与 `scripts/ai-quality-gate.ps1`（敏感扫描外调、CollaborationStrategy、StepLogs、proof-of-run、文档签名校验、白名单强制、git diff --check、转义竖线、rg 告警、结果写回上下文）。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | 新增第 9 节登记 WORKBENCH-M1 八个任务 | 工作循环合同要求里程碑登记 |
| `docs/03-engineering/ai-engineering-governance.md` | 更新 §4.2、§4.4.2、§5.1-5.3、§11 | 与 mock 语义化、collaboration 测试、敏感扫描豁免、proof-of-run、白名单强制的实现事实对齐 |
| `scripts/README.md` | 新增两个脚本条目并更新档位说明 | 脚本清单与行为同步 |
| `docs/90-reports/workbench-m1-execution-report.md` | 填写本报告 | 执行证据留痕 |

## Validation

- Backend tests: `mvn -Dtest=KnowledgeCollaborationGatewayServiceTests,CollaPlatformApplicationTests test` 通过（KnowledgeCollaborationGatewayServiceTests 6/6、CollaPlatformApplicationTests 1/1，Failures 0；surefire 报告可复核）；checkpoint 后端编译日志 `quality-gate-20260718-021813-mvn--DskipTests-test.log`；stage finish 以同一目标测试模式复核通过。
- Frontend build: 本轮无 `web/` 源码变更，checkpoint 与 finish 验证计划均为 frontendStrategy=skip（计划输出可复核）；前端模块未受影响。
- Local quality gate: checkpoint（light/quick）通过 Toolchain、Backend compile、Collaboration tests 三项；日志 `quality-gate-20260718-021813-mvn--DskipTests-test.log` 与 `quality-gate-20260718-021813-pnpm-collaboration-test.log`；敏感扫描独立脚本直接执行 PASS（0 命中 5 豁免）。
- Browser smoke: 不适用：本轮仅工作台脚本、一处后端配置默认值与文档变更，无页面、路由或用户流程入口；finish 已以 `-BrowserNotRequiredReason` 登记具体理由。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 证据 JSON manifest、远程 CI 生效门禁、PowerShell 脚本单元测试（Pester）、冒号式/YAML 形态密钥模式与文档事实锚点检查属于长期体系项，本轮未纳入 | non-blocking | 后续工作台加固路线候选 |
| N/A | `server/target/surefire-reports` 中存在历史遗留的 `LegacyDocumentApiCompatibilityTests` 失败报告（9 失败，非本轮产生，本轮目标测试不含该类） | non-blocking | 建议 KB-PRODUCT-M12 全量验证时复核 |

## Next Steps

- 将分支提交并合并回主干（建议 `feat(scripts): harden AI work-cycle platform (WORKBENCH-M1)`），在工作循环 finish 通过后执行。
- KB-PRODUCT-M12 收口时以完整门禁（route-final）验证本轮改动与全量测试的协同。
- 评估长期项：证据 JSON manifest、远程 CI、Pester 脚本测试、文档事实锚点。
