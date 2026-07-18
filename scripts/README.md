# Scripts

本目录只保留与当前 V049 schema、知识库唯一模型和双 UI 路由一致的活动脚本。`docs/`、`scripts/` 和 `deploy/scripts/` 均纳入版本管理，以本地最新内容为事实源并同步到远程仓库；运行报告、日志、备份和环境文件继续保持本地。

## 稳定脚本

| 脚本 | 用途 | 数据影响 |
| --- | --- | --- |
| `ai-work-cycle.ps1` | start/checkpoint/finish、阶段测试、浏览器证据执行和验收语义门禁 | 写 `.local-reports/` 与本地文档模板；浏览器命令按显式参数执行 |
| `ai-work-cycle-git-state.ps1` | 工作循环内部 Git 基线、文件签名和已提交/未提交变更追踪 | 只读，由工作循环与质量门禁共同加载 |
| `ai-quality-gate.ps1` | 编译、测试、前端、安全、迁移顺序和文档门禁 | 不重置共享数据库；测试使用 Testcontainers |
| `ai-audit-snapshot.ps1` | 保存工具链、Docker 和 Git 本地快照 | 只读 |
| `security-audit-gate.ps1` | 安全配置和审计调用静态检查 | 只读 |
| `sensitive-data-scan.ps1` | 敏感信息扫描，按 `sensitive-scan-allowlist.tsv` 做文件+模式精确豁免 | 只读，报告写入 `.local-reports/` |
| `assert-real-browser-evidence.ps1` | 真实浏览器证据来源检查：递归扫描 spec 及其 import 闭包中的响应拦截类 mock | 只读 |
| `knowledge-naming-guard.ps1` | 阻止活动代码重新引入旧文档产品命名 | 只读 |
| `knowledge-consistency-check.ps1` | 检查当前知识空间、目录、块、权限、搜索和引用一致性 | 只查询 PostgreSQL |
| `inspect-knowledge-object-references.ps1` | 展示知识目录对象引用、重复别名和缺失目标 | 只查询 PostgreSQL |
| `pilot-v2-manifest-check.ps1` | 校验试运行名单、场景、隐私、问题分级、指标和启动确认 | 只读，报告写入 `.local-reports/` |
| `pilot-v2-initialize.ps1` | 以计划或显式确认模式初始化组织、账号、用户组和协作模板 | `-Apply` 时写入指定试运行环境 |
| `pilot-v2-simulation-kickoff.ps1` | 冻结合成人格、备份和源码快照并记录模拟限制 | 只更新 `.local-pilot/` manifest，报告写入 `.local-reports/` |
| `pilot-v2-readiness.ps1` | 汇总初始化、备份、恢复、质量门禁、P0/P1 和冻结提交 | 只读，报告写入 `.local-reports/` |
| `im-browser-smoke.ps1` | 当前 IM Playwright 冒烟 | 通过当前 API 创建隔离测试数据 |
| `ui-split-v1-browser-smoke.ps1` | 用户工作台与管理后台双 UI 冒烟 | 通过当前 API 执行浏览器流程 |

运维脚本位于 `deploy/scripts/`。备份、健康检查和 restore drill 可通过根命令调用；真实 restore/rollback 必须显式传入确认参数。

## 调用方式

根 `package.json` 公开稳定入口，日常优先使用：

```powershell
pnpm audit:snapshot
pnpm work:start -- -Goal "M25-delivery" -TaskRange "M25-T01 到 M25-T12"
pnpm work:checkpoint -- -Goal "M25-delivery"
pnpm work:finish -- -Goal "M25-delivery" -TaskRange "M25-T01 到 M25-T12"
pnpm work:test
pnpm verify
pnpm verify:full
pnpm security:audit
pnpm kb:naming-guard
pnpm kb:consistency-check
pnpm smoke:im
pnpm smoke:ui-split
pnpm ops:backup
pnpm ops:restore-drill -- -BackupPath .local-backups\YYYYMMDD-HHMMSS
pnpm ops:health
pnpm ops:contract-check
pnpm ops:release-check -- -EnvFile deploy/.env.prod
pnpm pilot:contract-check
pnpm pilot:check -- -ManifestPath .local-pilot\pilot.json -Level initialization
pnpm pilot:initialize -- -ManifestPath .local-pilot\pilot.json
pnpm pilot:simulate-kickoff -- -ManifestPath .local-pilot\pilot.json -BackupPath <backup> -ConfirmationText SIMULATE:<pilotId>
pnpm pilot:readiness -- -ManifestPath .local-pilot\pilot.json -InitializationReceiptPath <receipt.json> -BackupPath <backup> -RestoreDrillReportPath <restore.md> -QualityGateReportPath <quality.md>
```

正式试运行配置从 `deploy/pilot-v2/manifest.example.json` 复制到被 Git 忽略的 `.local-pilot/`，不得在 manifest 中保存密码、token 或密钥。初始化写入要求从环境变量读取管理员和初始密码，并要求精确确认串 `INITIALIZE:<pilotId>:<projectName>`。非冻结 readiness 最高只输出 `REHEARSAL-READY`；`-SimulationFreeze` 只输出 `SIMULATION-READY`，必须标记合成人格、承认三项模拟限制并绑定源码快照；正式 `READY` 仍要求真实参与确认、干净提交、备份 commit 与冻结 commit 一致。模拟证据不得冒充真实试运行或生产发布批准。

需要传入复杂参数或执行未公开为根命令的维护操作时，可直接调用 PowerShell 脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-audit-snapshot.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-work-cycle.ps1 -Stage start
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-work-cycle.ps1 -Stage checkpoint
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-work-cycle.ps1 -Stage finish
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode quick
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-quality-gate.ps1 -Mode full
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/security-audit-gate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/knowledge-naming-guard.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/knowledge-consistency-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/im-browser-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ui-split-v1-browser-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/backup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore-drill.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/health-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/operations-contract-check.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/release-check.ps1
```

直接质量门禁 `quick` 仍用于仓库级快速检查；`full` 用于当前路线最后一个里程碑、发布门禁或明确要求的全量验证。工作循环会按变更范围自动选择：`checkpoint/light` 只验证受影响的后端编译、前端 lint 或 collaboration 测试（`collaboration/` 变更时），`stage finish` 执行当前里程碑目标测试和受影响前端 build，只有 `route-final` 才运行完整后端测试、package、collaboration 测试和全套静态门禁。`-GateMode` 参数已废弃（仅为兼容保留），档位由阶段自动派生。

工作循环的 `checkpoint` 和阶段 `finish` 会跳过 Docker Compose 健康检查与重复的全仓静态审计；目标集成测试仍会由 Testcontainers 自己检查 Docker daemon，路线最终收口仍会执行完整依赖检查和静态审计。成功命令只输出摘要，完整日志写入 `.local-reports/`。审计快照同样分为 `light` 和 `full`，轻量快照只记录 Git 状态，不枚举整个源码树。

`code-doc-report` 的 stage finish 必须显式传入目标测试，并提供浏览器执行命令或具体不适用理由，例如：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-work-cycle.ps1 -Stage finish -Goal "PILOT-V2-M5" -TaskRange "PILOT-V2-M5-T01 到 PILOT-V2-M5-T10" -BackendTestPattern "PermissionGovernanceControllerIntegrationTests" -BrowserTestCommand "powershell -NoProfile -ExecutionPolicy Bypass -File web/e2e/m5-permission-notification-e2e.ps1" -BrowserEvidenceKind real -BrowserEvidenceEnvironment isolated
```

stage finish 和 route-final finish 会验证执行报告中的 `Acceptance Evidence` 六列表、任务级 `Verification Contract`、路线图状态、验证章节、结构化 Remaining Gaps 和本轮新鲜浏览器日志。start 同时记录基线提交、启动前脏文件和必更文档签名；后续影响范围同时覆盖 `baselineCommit..HEAD` 的已提交变更与当前工作区变更。checkpoint 只验证报告结构和任务行，不要求任务已经标记为 Done，也不要求浏览器收口证据。Git 基线回归测试可通过 `pnpm work:test` 独立运行。

`-BrowserTestCommand` 必须显式声明证据类型和环境：

- 真实闭环：`-BrowserEvidenceKind real -BrowserEvidenceEnvironment isolated`。命令必须命名具体 Playwright spec 或浏览器脚本；声明为 `real` 的 spec 不得使用 `page.route`、`route.fulfill` 或 `page.addInitScript` 伪造认证/API。
- 页面 mock：`-BrowserEvidenceKind mock -BrowserEvidenceEnvironment mock`。只可用于 Contract 明确允许的 UI 状态或视觉交互，不能关闭认证、权限、创建/修改资源、安全、交接、导出或审计任务。
- 无浏览器任务：不传以上参数，改用至少 20 个字符的 `-BrowserNotRequiredReason`。

## 历史脚本

`scripts/archive/` 保存已经完成路线的一次性脚本，包括 M12/M31/M40 数据、旧性能基线、知识库 v1/v2 迁移验收、V045 前快照和 KB-NAME 兼容观察。

历史脚本存在以下一种或多种风险：

- 查询或写入已经删除的表、字段和 `document` 资源类型；
- 调用已经删除的 `/docs`、`/api/docs` 或旧兼容路由；
- 依赖 M31/M40 固定数据和旧导航；
- 重置共享数据库或生成只适用于旧版本的回滚 SQL；
- 聚合已经失效的历史报告作为当前 Go/No-Go 证据。

归档脚本不在 `package.json` 暴露命令，也不得直接运行。确需复现历史证据时，先复制到独立临时目录，核对当前 schema/API，并使用数据库副本和明确备份。

## 数据安全

- 普通验证不得自动重置共享开发数据库。
- 集成测试使用 Testcontainers PostgreSQL。
- 任何写数据的浏览器脚本必须使用隔离命名并在报告中说明清理策略。
- 涉及真实用户角色、认证、权限或资源状态的浏览器验证必须使用隔离环境；共享开发环境只允许无写入的只读验证。
- `restore.ps1`、`rollback.ps1` 和 restore drill 的执行恢复模式必须保留显式确认开关。
- `rollback.ps1` 只部署已构建的版本化镜像，不得切换当前 Git 工作树或在回滚时临时重建旧提交。
- 脚本输出统一写入 `.local-reports/`、`.local-logs/` 或 `.local-backups/`，不得进入远程仓库。
