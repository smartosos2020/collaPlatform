---
title: PILOT-V2-M9 Execution Report
status: completed
milestone: PILOT-V2-M9
updated_at: 2026-07-15
---

# PILOT-V2-M9 Execution Report

## Scope

- PILOT-V2-M9-T01 到 PILOT-V2-M9-T09。
- 项目所有者确认当前无法组织真实参与者，批准使用 5 个合成人格完成 M9 工程试运行基线。
- 本报告的完成结论是 `SIMULATION-READY`，不是人工试运行 `READY`，不构成真实 UX、满意度或生产发布批准。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M9-T01 | integration | real | isolated-compose | No | owner、pilot-admin 和 3 名 member 均具备角色、职责、执行窗口和反馈渠道，并逐一通过真实登录页。 |
| PILOT-V2-M9-T02 | contract | not-required | repository | No | manifest 同时包含 IM、项目、知识、Base、审批和搜索场景。 |
| PILOT-V2-M9-T03 | contract | not-required | repository | No | manifest 禁止敏感数据、标记 synthetic 身份并限制保留期。 |
| PILOT-V2-M9-T04 | integration | real | isolated-compose | No | 当前管理 API 初始化部门、账号、角色和用户组，管理员 UI 可复核。 |
| PILOT-V2-M9-T05 | integration | real | isolated-compose | No | 当前 API 初始化项目、知识模板、Base 和审批入口，普通成员 UI 可访问。 |
| PILOT-V2-M9-T06 | contract | not-required | repository | No | manifest 固化 P0-P3、响应时限、复现字段、负责人和停止条件。 |
| PILOT-V2-M9-T07 | contract | not-required | repository | No | manifest 固化活跃、完成率、错误率、阻塞和满意度的模拟口径。 |
| PILOT-V2-M9-T08 | integration | real | isolated-compose | No | 目标项目静默备份、独立恢复、目标测试、质量门禁及开放 P0/P1 检查全部通过。 |
| PILOT-V2-M9-T09 | integration | real | isolated-compose | No | 合成 kickoff 逐人确认范围、反馈和停止条件，绑定备份 commit 与可复算源码快照。 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| PILOT-V2-M9-T01 | Done | 5 个 `participantKind=synthetic` 人格覆盖 owner、admin 和 3 名 member；5/5 真实登录页认证通过。 |
| PILOT-V2-M9-T02 | Done | 六类必需模块场景已进入版本化 manifest，缺失模块会被契约测试拒绝。 |
| PILOT-V2-M9-T03 | Done | 数据分类、禁止类别、前缀、保留期和三项模拟限制已固化。 |
| PILOT-V2-M9-T04 | Done | 空环境创建 4 个部门、5 个账号、用户组及成员关系，第二次 apply 全部 VERIFIED。 |
| PILOT-V2-M9-T05 | Done | 项目、知识模板、Base 台账、审批入口和 ACL 已创建并由管理员/成员 UI 复核。 |
| PILOT-V2-M9-T06 | Done | P0-P3、SLA、问题字段和停止条件通过契约检查。 |
| PILOT-V2-M9-T07 | Done | 五类指标、数据源、运算符和阈值通过契约检查。 |
| PILOT-V2-M9-T08 | Done | 新鲜静默备份、独立恢复、质量门禁、核心回归及 0 个开放 P0/P1 通过。 |
| PILOT-V2-M9-T09 | Done | `SIMULATION-GO` kickoff 和 `SIMULATION-READY` 准入通过，模拟限制明确记录。 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PILOT-V2-M9-T01 | 角色、职责、参与时间和反馈渠道明确 | `.local-pilot/m9-simulation.json` 定义 5 个合成人格 | initialization 与 simulation-freeze manifest PASS | 5 个合成账号均从登录页进入工作台 | Done |
| PILOT-V2-M9-T02 | 覆盖六类协作任务 | `manifest.example.json` 六场景合同 | `pilot:contract-check` 10/10 | 由 T05 覆盖资源入口 | Done |
| PILOT-V2-M9-T03 | 敏感数据禁止且模拟数据可区分 | `dataPolicy`、`participantKind` 和 limitations | 敏感字段递归检查、初始化检查通过 | not-required：配置契约 | Done |
| PILOT-V2-M9-T04 | 当前管理 UI/API 可初始化并复核 | 幂等初始化器与精确确认串 | 回执 `pilot-v2-initialize-20260715-090759.json` 全部 VERIFIED | 组织、成员、用户组管理员流程通过 | Done |
| PILOT-V2-M9-T05 | 模板真实可访问且无旧固定数据 | 项目、知识、Base、审批及权限初始化 | apply 后复跑无重复资源 | 普通成员访问四类资源通过 | Done |
| PILOT-V2-M9-T06 | 问题可分级、归属、复现和跟踪 | manifest `issuePolicy` | 契约检查和开放 urgent/high 查询通过 | not-required：流程合同 | Done |
| PILOT-V2-M9-T07 | 五类成功标准口径明确 | manifest `metrics` | initialization contract PASS | not-required：指标合同 | Done |
| PILOT-V2-M9-T08 | 可恢复备份、无 P0/P1、核心回归通过 | `pilot-v2-readiness.ps1` 汇总显式证据 | backup `20260715-090918`、restore drill PASS、quality gate PASS、open P0/P1=0 | 隔离管理员/成员流程 3/3 passed | Done |
| PILOT-V2-M9-T09 | 确认范围、反馈、停止条件并冻结模拟基线 | `pilot-v2-simulation-kickoff.ps1` 记录五人格确认和三项限制 | kickoff `SIMULATION-GO`；source snapshot、backup manifest、commit 三项匹配 | 5/5 人格认证作为启动可用性证据 | Done |

## Code Changes

- 新增 `simulation-freeze` 契约和 `SIMULATION-READY` 判定，真实 `-Freeze` 的人工确认与干净提交要求保持不变。
- 新增合成 kickoff 脚本，仅允许更新 `.local-pilot/`，使用精确确认串并绑定备份与源码快照。
- M9 Playwright 扩展为管理员复核、普通成员资源访问和 5 个合成人格真实登录三条流程，不注入会话或 mock 路由。
- 延续生产构建同源 `/api` 与当前 origin WebSocket 修复，保证隔离端口下 UI 访问真实后端。

## Validation

- Contract: `pnpm pilot:contract-check`，10/10 passed；模拟证据不能通过真实 freeze。
- Initialization: 空 Compose 项目真实 apply，第二次 apply 全部 VERIFIED。
- Frontend/browser: lint passed；M9 isolated Playwright 3/3 passed，其中合成人格登录 5/5。
- Backup: `.local-backups/m9-simulation/20260715-090918`，application-quiesced、Flyway V049、文件 SHA-256 完整。
- Restore: `.local-reports/restore-drill-20260715-091030.md`，独立 `colla-platform-drill-m9r1` PASS。
- Kickoff: `.local-reports/pilot-v2-simulation-kickoff-20260715-091321.md`，`SIMULATION-GO`。
- Readiness: `.local-reports/pilot-v2-readiness-20260715-091335.md`，全部检查 PASS，判定 `SIMULATION-READY`。
- Work-cycle finish: `.local-reports/quality-gate-20260715-091959.md`，9 个目标测试类共 30/30、Flyway V001-V049、前端 lint/build、chunk budget 和路由懒加载全部通过；新鲜浏览器日志为 `work-cycle-browser-20260715-091942.log`。
- 首次 finish 因项目约定的本地测试 MinIO `localhost:9000` 未运行而在 Spring Context 启动阶段失败；启动精确命名的临时测试 MinIO 后原命令复跑通过，没有修改业务代码或降低测试范围。
- 路线级全量 Maven 和 package 仍按计划留在 M11；M9 仅执行范围化集成测试，Flyway V001-V049 随目标测试空库启动完成。

## Residual Limitations

- 没有真实用户反馈，不能判断学习成本、自然协作节奏或主观满意度。
- satisfaction 指标当前只验证采集合同，不提供人工满意度结论。
- `SIMULATION-READY` 不是生产发布批准，也不能在后续报告中改写成真实团队试运行。

## Next Step

- 项目所有者已选择继续模拟。M10 已调整为“合成连续运行与故障注入”，从 `PILOT-V2-M10-T01` 到 `PILOT-V2-M10-T09` 执行至少 3 个独立轮次；结果不得表述为真实用户试运行。
