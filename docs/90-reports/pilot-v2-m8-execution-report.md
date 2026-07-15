---
title: PILOT-V2-M8 Execution Report
status: completed
milestone: PILOT-V2-M8
updated_at: 2026-07-13
---

# PILOT-V2-M8 Execution Report

## Scope

- PILOT-V2-M8-T01 到 PILOT-V2-M8-T08
- 本报告替换首次执行时证据不足的结论；M8 经审计重开后重新实现并复验。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M8-T01 | integration | not-required | isolated-compose | No | 真实 PostgreSQL/MinIO 生成 manifest v2、关键计数、SHA-256；写入期间停止应用/MinIO，保留策略只处理已验证目录。 |
| PILOT-V2-M8-T02 | integration | not-required | isolated-compose | No | 使用独立 Compose project、端口和 volumes 完整恢复 PostgreSQL/MinIO，自动比较关键表与对象计数。 |
| PILOT-V2-M8-T03 | integration | not-required | isolated-compose | No | 校验全部 Compose 服务、API health 语义、Actuator、request-id 回显和服务日志关联。 |
| PILOT-V2-M8-T04 | integration | not-required | isolated-compose | No | 校验生产配置、不可变镜像标签、源码 revision、完整服务、备份门禁、质量门禁和镜像构建；跳过项只能得到 PARTIAL。 |
| PILOT-V2-M8-T05 | integration | not-required | isolated-compose | No | 使用已构建的 server/web 镜像执行 `--no-build` 回滚，核对两镜像 revision 并通过健康检查，不切换 Git 工作树。 |
| PILOT-V2-M8-T06 | static | not-required | not-required | No | docs、治理/运维脚本纳入版本管理；环境、备份、日志、报告和密钥继续排除。 |
| PILOT-V2-M8-T07 | static | not-required | not-required | No | 远程 CI 未启用时明确人工门禁和风险，不把本地模板描述为远程 required check。 |
| PILOT-V2-M8-T08 | integration | not-required | isolated-compose | No | 连续完成备份、restore dry-run、真实隔离 restore、health、release diagnostic 和镜像 rollback，保留本地证据。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M8-T01 | Done | `.local-backups/m8-remediation/20260713-192700`：manifest v2、Flyway V049、PostgreSQL 六类计数、MinIO 17 对象、双文件 SHA-256；未完成目录 `20260713-192036` 未被 retention 误删。 |
| PILOT-V2-M8-T02 | Done | `colla-platform-drill-m8r1` 独立项目在 18080/18443 完成真实恢复；`.local-reports/restore-drill-20260713-194308.md` 与 `restore-20260713-194309.md`。 |
| PILOT-V2-M8-T03 | Done | `.local-reports/health-check-20260713-194420.md` 及 `health-check-20260713-195440.md`：六服务健康、API payload、Actuator 和日志关联通过。 |
| PILOT-V2-M8-T04 | Done | `.local-reports/release-check-20260713-195319.md` 明确为 PARTIAL；镜像构建与 OCI revision 通过，跳过备份/质量门禁没有被误报为正式放行。 |
| PILOT-V2-M8-T05 | Done | `.local-reports/rollback-20260713-195421.md`：版本化镜像 `m8r0` 执行 `--no-build` 回滚，revision 一致且健康通过，当前 Git 工作树未切换。 |
| PILOT-V2-M8-T06 | Done | 当前 `.gitignore` 仅排除 `.local-*`、环境/密钥和运行产物；docs、scripts、deploy scripts 均为版本化交付内容。 |
| PILOT-V2-M8-T07 | Done | runbook 明确远程 CI 未生效，当前采用可复制的人工 release gate，不声称存在远程合并门禁。 |
| PILOT-V2-M8-T08 | Done | 本次重开执行形成完整桌面演练链路；`pnpm ops:contract-check` 8/8 通过，隔离栈最终清理前保持可复核。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M8-T01 | PostgreSQL/MinIO 备份可验证且不会越界清理 | `backup.ps1`、`operations-common.ps1`：应用静默、MinIO 停写、manifest v2、严格子路径和已验证目录 retention | 真实备份成功；manifest 文件大小/哈希复核；不完整目录保留 | not-required：CLI 数据保护流程 | Done |
| PILOT-V2-M8-T02 | 恢复后健康检查和关键对象计数一致 | `restore.ps1` 要求目标名和精确确认文本；`restore-drill.ps1` 只允许 `colla-platform-drill-*` 且禁止源/目标同项目 | users 5、workspaces 1、projects 1、bases 1、knowledge spaces 27、audit logs 296、MinIO 17 全部匹配 | not-required：隔离 Compose 恢复流程 | Done |
| PILOT-V2-M8-T03 | 管理员可从请求定位到服务日志 | `health-check.ps1` 校验 Compose 状态、API 业务字段、request-id、Actuator 和可选日志关联 | 两次隔离 health 实跑通过；自定义 request-id 在 server 日志命中 | not-required：服务诊断流程 | Done |
| PILOT-V2-M8-T04 | 配置、迁移、备份、构建、端口和依赖均有发布门禁 | `release-check.ps1`、镜像 digest、`SERVER_IMAGE/WEB_IMAGE/SOURCE_COMMIT` 契约和 artifact JSON | 镜像构建成功；两镜像 revision 匹配 HEAD；PARTIAL 语义符合跳过项 | not-required：发布门禁无浏览器 UI | Done |
| PILOT-V2-M8-T05 | 区分只回代码、兼容回退和恢复数据 | `rollback.ps1` 改为不可变镜像输入；数据恢复仍为独立显式开关；runbook 三分支决策树 | 镜像回滚重建 server/web 后六服务、API、Actuator、日志关联通过 | not-required：回滚为 CLI 流程 | Done |
| PILOT-V2-M8-T06 | 版本管理边界与仓库事实一致 | `.gitignore`、deploy/scripts、scripts README | `git status` 只显示预期版本化改动，`.local-*` 证据未进入 Git | not-required：仓库治理任务 | Done |
| PILOT-V2-M8-T07 | CI 结论与风险接受可复核 | admin operations runbook 明确当前人工门禁和远程 CI 边界 | 文档不再把被忽略模板称为远程门禁 | not-required：CI 决策任务 | Done |
| PILOT-V2-M8-T08 | 按 runbook 执行并记录问题、修复和证据 | deploy README、admin runbook 和本报告同步真实参数及判断语义 | contract 8/8、checkpoint compile/lint、备份/恢复/构建/回滚/健康均通过 | not-required：运维桌面演练 | Done |

## Remediation Findings

首次 M8 执行的以下结论不成立，已在本轮修复：

- 恢复时应用和 MinIO 仍可能写入，原始 volume 覆盖不具备一致性保证。
- restore drill 没有强制独立项目，可能误指向源环境。
- health 只看可达性，没有验证 payload、request-id 回显和日志关联。
- release check 可跳过质量、镜像和备份后仍被描述为通过。
- rollback 在当前开发仓库切换 Git ref 并临时重建，既会污染工作树，也无法保证旧构建链仍可用。
- Web Dockerfile 的复制/安装顺序导致生产镜像无法从干净上下文构建。
- Compose 应用服务缺少健康检查，且基础镜像存在漂移标签。
- 工作循环 `FrontendStrategy=lint` 成功执行后仍被错误判为不支持。

## Code Changes

- Deployment: 固定依赖镜像 digest，应用镜像使用版本化 tag 和源码 revision label，server/web/nginx 增加健康检查。
- Backup/restore: 增加一致性停写、manifest v2、哈希/计数、隔离目标、精确确认和恢复日志。
- Release/rollback: 正式与部分门禁分离；回滚改为不可变镜像和 `--no-build`，不再操作 Git 工作树。
- Verification: 新增 `operations-contract-check.ps1` 及 `pnpm ops:contract-check`，修复工作循环 lint 策略分支。
- Backend/frontend business logic: 无业务接口、数据结构或页面行为变更。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `deploy/README.md` | 重写发布、隔离恢复和镜像回滚命令 | 参数与脚本真实契约一致 |
| `docs/05-runbooks/admin-operations.md` | 增加 manifest v2、不可变镜像和 PARTIAL 规则 | 防止把诊断演练当正式放行 |
| `scripts/README.md` | 增加运维契约入口并更新 V049 | 活动脚本与当前 schema 一致 |
| `docs/02-roadmap/current-roadmap.md` | 移除不存在的 M8-T09，修正 V049 基线 | 路线状态与代码事实一致 |

## Validation

- Operations contract: `pnpm ops:contract-check`，8/8 passed。
- Work-cycle finish (`stage`): `KnowledgeSchemaMigrationIntegrationTests` 1/1 passed，Testcontainers 从空库成功应用 49 个迁移至 V049；前端 lint、build、chunk budget 和 route lazy-loading 全部 passed。
- Image build: server/web 生产镜像从干净 Docker context 构建成功，均带 revision `dd0d9a9b1b14785bfaf84f4618393647378721f6`。
- Restore: 独立 Compose project 真实恢复成功，Flyway V049、六类数据库计数和 MinIO 17 对象一致。
- Rollback: 使用本地版本化镜像标签执行 `--no-build` 应用回滚，健康和日志关联通过。
- Browser smoke: not-required；本轮没有用户界面或业务行为改动，真实证据来自 Docker/PowerShell 隔离运维流程。
- Full `mvn test` / Flyway empty-db suite: 按当前路线规则留在 PILOT-V2-M11，不在 M8 重复执行。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PILOT-V2-M9-T08 | 本轮工作树有改动，因此 release check 正确输出 PARTIAL；没有形成可正式发布的 clean-commit 制品。 | 不影响 M8 门禁机制验收；阻止将本次镜像当正式发布物。 | 试运行冻结提交后，以 clean HEAD、完整质量门禁和目标环境新备份执行正式 release check。 |
| PILOT-V2-M11-T07 | 回滚演练的 `m8r0/m8r1` 为相同内容的两个标签，验证了制品选择、重建和健康链路，没有验证真实旧版本的业务兼容性。 | 不阻塞 M8 机制准入；正式 Go/No-Go 前必须用连续两个真实 release artifact 复验。 | M11 最终发布回退复核。 |
| N/A | 远程 CI 仍未启用。 | 人工操作遗漏风险继续存在，但已明确接受且不再误报。 | 由项目所有者决定是否另立 CI 路线。 |

## Next Steps

- 进入 PILOT-V2-M9，先冻结干净提交并生成第一组正式 release artifact，不复用本轮 dirty-worktree 镜像。
- M9 准入备份必须来自试运行目标项目，并通过 manifest v2 与隔离 restore drill。
- M11 使用两个真实相邻版本再次执行镜像回滚和兼容性验证。
