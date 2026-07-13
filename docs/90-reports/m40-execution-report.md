---
title: M40 Execution Report
status: archived
milestone: M40
updated_at: 2026-06-18
---

# M40 执行报告

## 本轮范围

- M40-T01 到 M40-T08

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| M40-T01 制定真实试运行准入清单 | 完成 | `docs/05-runbooks/team-trial-readiness.md` |
| M40-T02 设计 10 人角色试运行剧本 | 完成 | `docs/05-runbooks/team-trial-readiness.md`, `scripts/trial-team-template.ps1` |
| M40-T03 准备团队初始化和账号流程 | 完成 | `scripts/trial-team-template.ps1`, `.local-reports/trial-team-template-*.csv` |
| M40-T04 建立问题收集和分级机制 | 完成 | `.github/ISSUE_TEMPLATE/trial-issue.yml` |
| M40-T05 准备数据迁移和清理策略 | 完成 | `docs/05-runbooks/team-trial-readiness.md` |
| M40-T06 完成管理员运维手册 | 完成 | `docs/05-runbooks/admin-operations.md` |
| M40-T07 执行试运行前完整回归 | 完成 | `pnpm verify:full`、`pnpm data:reset`、`pnpm smoke:m31` 通过 |
| M40-T08 输出试运行 Go/No-Go 报告 | 完成 | `.local-reports/team-trial-readiness-20260619-001410.md` 决策 `GO` |

## 代码变更

- 后端：无业务代码变更。
- 前端：IM 会话搜索结果补充链接摘要展示，使按对象筛选命中的消息能显示关联事项/对象标题。
- 数据库：无迁移变更。
- 脚本：新增 `scripts/trial-team-template.ps1`、`scripts/team-trial-readiness.ps1`；新增根命令 `pnpm trial:team-template`、`pnpm trial:readiness`；修复 `scripts/m31-collab-simulation.ps1` 的 Base 记录协同表重置清单；修正 readiness 对 M31 `Stage: all` 报告的证据查找。
- GitHub：新增 `.github/ISSUE_TEMPLATE/trial-issue.yml` 作为试运行问题模板。

## 文档变更

| 文档 | 动作 | 原因 |
| --- | --- | --- |
| `docs/05-runbooks/team-trial-readiness.md` | 新增 | 记录准入标准、10 人角色、5 个项目、问题分级、迁移清理和最终回归命令 |
| `docs/05-runbooks/admin-operations.md` | 新增 | 记录启停、健康检查、备份、恢复演练、日志、账号权限和常见故障处理 |
| `docs/00-product/current-product-scope.md` | 更新 | 补充小团队试运行产品边界和当前 Gap |
| `docs/03-engineering/ai-engineering-governance.md` | 更新 | 补充 M40 试运行准入门禁和 M31 回归例外规则 |
| `docs/02-roadmap/current-roadmap.md` | 更新 | 标记 M40-T01 到 M40-T08 完成，记录试运行准入 GO 后的剩余 Gap |
| `docs/01-architecture/current-architecture.md` | 更新 | 补充 IM 搜索链接摘要和 M40/M31 脚本事实 |
| `docs/README.md`、`scripts/README.md` | 更新 | 补充试运行 runbook 和脚本入口 |

## 验证

- PowerShell 语法检查：`scripts/trial-team-template.ps1`、`scripts/team-trial-readiness.ps1` 通过。
- `pnpm trial:team-template`：通过，生成 `.local-reports/trial-team-template-20260618-235701.csv` 和 `.local-reports/trial-team-template-20260618-235701.md`。
- `pnpm trial:readiness`：通过，生成 `.local-reports/team-trial-readiness-20260618-235701.md`，决策 `GO`。
- `pnpm work:checkpoint -- -Goal "M40-team-trial-readiness" -GateMode quick`：通过，生成 `.local-reports/quality-gate-20260618-235905.md`。
- `pnpm verify:full`：修改后重新执行并通过，生成 `.local-reports/quality-gate-20260619-001210.md`。
- `pnpm data:reset`：首次暴露 `base_record_comments` 外键依赖缺口；补齐 `base_record_comments`、`base_record_relations`、`base_record_activity_logs` 后通过，生成 `.local-reports/m31-collab-simulation-20260619-000323.md`。
- `pnpm smoke:m31`：首次暴露前端 dev server 未启动，随后暴露 M31 smoke 选择器和 IM 搜索结果摘要缺口；启动前端、修正 smoke 定位并补充 IM 搜索链接摘要后通过，生成 `.local-reports/m31-collab-simulation-20260619-001147.md`，Playwright 1 passed。
- 严格 Go/No-Go：`pnpm trial:readiness -- -RequireFullGate -RequireDataReset -M31SmokePassed` 通过，生成 `.local-reports/team-trial-readiness-20260619-001410.md`，决策 `GO`、0 failures、0 warnings。
- `pnpm work:finish -- -Goal "M40-team-trial-readiness"`：通过，生成 `.local-reports/quality-gate-20260619-001554.md` 和 `.local-reports/audit-snapshot-20260619-001714.md`。
- 浏览器冒烟：`pnpm smoke:m31` 已覆盖项目、事项详情、文档、Base 记录、IM 会话搜索、全局搜索和权限拒绝分支。

## 遗留 Gap

- 当前 Go/No-Go 只代表进入小团队真实试运行的准入判断，不代表试运行反馈已经完成。
- PWA/桌面壳正式安装包、自动更新、原生移动客户端和高可用部署仍在 M40+。

## 下一步

- 进入受控小团队真实试运行，按 Issue 模板记录反馈。
- 优先处理试运行中出现的 P0/P1 问题，并在下一轮更新准入结论。
