---
title: PILOT-V2-M8 Execution Report
status: archived
milestone: PILOT-V2-M8
updated_at: 2026-07-13
---

# PILOT-V2-M8 Execution Report

## Scope

- PILOT-V2-M8-T01 到 PILOT-V2-M8-T08

本轮只推进 M8 T01–T08；T09 不在本轮范围，继续 Pending。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M8-T01 | integration | not-required | not-required | No | 真实 PostgreSQL/MinIO 备份生成 manifest、SHA-256 和保留策略检查，确认清理路径留在仓库备份根目录。 |
| PILOT-V2-M8-T02 | integration | not-required | not-required | No | 真实隔离 Compose 目标执行 PostgreSQL/MinIO restore，比较关键表计数和非系统对象数。 |
| PILOT-V2-M8-T03 | integration | not-required | not-required | No | 真实新启动服务检查 API health、Actuator、Prometheus，并用 X-Colla-Request-Id 关联服务日志。 |
| PILOT-V2-M8-T04 | static | not-required | not-required | No | 真实执行 release-check，验证环境配置、compose、质量门禁、备份、构建和健康步骤均有明确入口。 |
| PILOT-V2-M8-T05 | static | not-required | not-required | No | 按真实 runbook 决策树区分代码回退、兼容回退和数据恢复，不默认同时回退代码与数据。 |
| PILOT-V2-M8-T06 | static | not-required | not-required | No | 真实检查本地脚本、被忽略的 CI 模板和远程仓库边界，确认人工步骤可复制。 |
| PILOT-V2-M8-T07 | static | not-required | not-required | No | 真实评估 `.github/workflows/ci.yml` 模板与本地门禁差异，输出保留人工门禁的风险接受决定。 |
| PILOT-V2-M8-T08 | integration | not-required | not-required | No | 真实桌面演练执行备份、dry-run、隔离恢复、health、发布检查并记录耗时、问题和证据。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M8-T01 | Done | `.local-backups/m8-cycle/20260713-175116` 生成 PostgreSQL/MinIO 包、manifest 和双文件 SHA-256；RetentionDays=1 检查通过。 |
| PILOT-V2-M8-T02 | Done | 隔离 `local-reports` Compose restore 完成，PostgreSQL 和 MinIO 关键计数一致。 |
| PILOT-V2-M8-T03 | Done | 新服务 8081 的 API、Actuator、Prometheus 和 request-id 日志关联通过；旧长驻进程异常已记录为 blocker。 |
| PILOT-V2-M8-T04 | Done | `release-check.ps1` 使用示例生产环境配置通过，发布前清单已写入 runbook。 |
| PILOT-V2-M8-T05 | Done | admin-operations runbook 固化三分支回退决策树和显式确认规则。 |
| PILOT-V2-M8-T06 | Done | 本地 `scripts/`、`deploy/scripts/` 与被忽略 `.github` 模板边界已写明。 |
| PILOT-V2-M8-T07 | Done | 远程 CI 保持模板状态，未声称存在远程合并门禁；人工门禁风险接受已记录。 |
| PILOT-V2-M8-T08 | Done | 桌面演练记录备份、restore、health、release gate 耗时、计数、警告和清理结果。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M8-T01 | PostgreSQL/MinIO 备份可验证且不会越界清理 | `deploy/scripts/backup.ps1` 生成双介质 manifest、大小和 SHA-256，并限制 retention 根目录 | backup 实跑成功；restore-drill 对两个文件 hash 通过；RetentionDays=1 运行通过 | not-required：PowerShell/Docker 运维任务无浏览器界面 | Done |
| PILOT-V2-M8-T02 | 恢复后健康检查和关键对象计数一致 | `restore.ps1` 显式确认、manifest hash 校验、PostgreSQL/MinIO 恢复；`.local-reports/m8-restore-evidence.md` | users 5→5、workspaces 1→1、bases 1→1、projects 1→1、knowledge spaces 27→27、audit logs 296→296；MinIO 对象 17→17 | not-required：隔离恢复目标无浏览器界面 | Done |
| PILOT-V2-M8-T03 | 管理员可从请求定位到服务日志 | `health-check.ps1` 检查 API/Actuator/Prometheus；admin runbook 固化 request-id 命令 | 8081 health-check 三项 PASS；X-Colla-Request-Id 在后端 JSON 日志中命中 | not-required：服务排障命令无浏览器界面 | Done |
| PILOT-V2-M8-T04 | 配置、迁移、备份、构建、端口和依赖均有发布门禁 | `release-check.ps1` 与 deploy/admin runbook 发布清单 | release-check 使用 `.env.prod.example`、AllowDirty、SkipQualityGate/SkipImageBuild 通过；正式质量门禁另有记录 | not-required：发布门禁无浏览器界面 | Done |
| PILOT-V2-M8-T05 | 区分只回代码、兼容回退和恢复数据 | admin-operations runbook 三分支决策树；rollback/restore 均要求显式确认 | 静态检查 rollback/restore 的 Confirm 参数和 BackupPath 防线；restore 实演成功 | not-required：回退决策为 runbook/CLI 流程 | Done |
| PILOT-V2-M8-T06 | 远程不引用缺失脚本，人工门禁步骤可复制执行 | scripts README、admin-operations runbook 和 `.github` 忽略边界 | `git check-ignore .github/workflows/ci.yml` 与本地脚本入口核对通过 | not-required：仓库与脚本边界无浏览器界面 | Done |
| PILOT-V2-M8-T07 | 远程 CI 评估结论与人工门禁风险接受可复核 | CI 模板仅作本地参考；runbook 明确 Java/Node/pnpm/Docker/测试/build 人工步骤 | `.github/workflows/ci.yml` 模板内容与路线图远程门禁声明一致；本地 release-check 通过 | not-required：CI 决策无浏览器界面 | Done |
| PILOT-V2-M8-T08 | 按 runbook 执行成功并记录耗时、问题和证据 | `.local-reports/m8-desktop-drill.md` 记录备份、dry-run、隔离 restore、health、release gate 和 warning | 桌面演练各阶段成功；隔离恢复计数和对象数一致；共享数据未恢复或重置 | not-required：桌面运维演练无浏览器界面 | Done |

## Code Changes

- Backend: 本轮无业务代码变更；用新启动实例验证现有 health、Actuator、Prometheus 和 request logging。
- Frontend: 无前端变更；M8 验收对象是单节点运维、发布和回退流程。
- Runbooks: 扩展 `docs/05-runbooks/admin-operations.md`，加入 request-id 排障、三分支回退决策树、发布/CI 边界和桌面演练记录要求。
- Scripts: 复核并实跑 `deploy/scripts/backup.ps1`、`restore-drill.ps1`、`restore.ps1`、`health-check.ps1`、`release-check.ps1`、`rollback.ps1` 的确认和路径边界。
- Database/Storage: 使用共享数据生成只读备份，恢复写入隔离 PostgreSQL 和 MinIO；共享数据未执行 restore/reset。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | M8 T01–T08 set Done; T09 remains Pending | 与本轮连续范围和运维证据一致 |
| `docs/05-runbooks/admin-operations.md` | Added request-id troubleshooting, rollback tree, CI boundary, desktop drill record | 让人工发布/回退步骤可复制、可审计 |
| `docs/90-reports/pilot-v2-m8-execution-report.md` | Replaced template with v2 contract and evidence matrix | 任务级门禁和残余风险可复核 |

## Validation

- Backend tests: M8 无业务代码变更；`release-check` 和正式 finish stage 使用目标后端集成测试集合，Tests run 36, Failures 0, Errors 0。
- Frontend build: stage finish detected no frontend changes (`affected=none`) and therefore skipped frontend lint/build；本轮无前端变更。
- Local quality gate: finish stage `.local-reports/quality-gate-20260713-180202.md` reports PASS；目标后端测试 36/36 通过，工具链检查通过，无 warning/failure；报告修订后 full 静态/文档契约复核 `.local-reports/quality-gate-20260713-180538.md` 亦 PASS（后端验证沿用前述 finish 证据）。
- Browser smoke: not-required；M8 是 PowerShell/Docker 运维流程，没有浏览器界面；真实证据由隔离 restore、health、release-check 和桌面演练提供。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 本轮 T01–T08 无验收阻塞缺口；旧长驻共享后端的 Actuator 连接池卡住已记录为需重启/诊断的运行风险。 | non-blocking | `.local-reports/m8-desktop-drill.md`；M8-T09 仍待后续范围。 |

## Next Steps

- M8-T09 仍 Pending；如继续推进需另起 AI 工作循环。
- 共享后端 Actuator 连接池异常应在下一次服务重启/运行观察中确认原因，不得作为健康通过信号复用。
