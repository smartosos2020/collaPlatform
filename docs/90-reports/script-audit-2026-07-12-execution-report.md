---
title: SCRIPT-AUDIT-2026-07-12 Execution Report
status: archived
milestone: SCRIPT-AUDIT-2026-07-12
updated_at: 2026-07-12
---

# SCRIPT-AUDIT-2026-07-12 Execution Report

## Scope

- 核对根脚本、运维脚本、Playwright 脚本与当前 V047 schema、规范知识内容 API、双 UI 路由和远程仓库边界。
- 归档已完成路线的一次性脚本，移除失效公开入口，保留当前可执行工具。

## Findings And Decisions

| 问题 | 处理 |
| --- | --- |
| M12/M31/M40 脚本会重置共享数据或依赖旧固定场景 | 移入 `scripts/archive/`，不再公开命令 |
| 旧性能基线调用 `/docs` | 归档，后续性能任务按当前路由重新设计 |
| 知识库 v1/v2 迁移、兼容、试运行、验收脚本查询旧表或旧 API | 归档，不作为当前发布证据 |
| 旧 migration-check 查询已删除权限表和 `document` 资源类型 | 归档并以只读 `knowledge-consistency-check.ps1` 替代 |
| 对象引用脚本仍查找 `document` 目标 | 改为 `knowledge_content`，按 `item_kind` 校验 |
| 两个 Playwright spec 调用 `/docs` 或依赖 M31 数据 | 从活动 `web/e2e` 删除并保存到本地脚本归档 |
| 命名门禁只扫描 `web/src` | 扩展到 `web/e2e` |
| 受跟踪的 `package.json` 引用不进入远程的本地脚本 | 移除全部本地治理/运维别名，仅保留前端项目命令 |
| 本地 CI 模板调用不存在的 `pnpm verify:full` | 改为 `mvn -B test package`、`pnpm web:lint` 和 `pnpm web:build`；同时明确模板被忽略，当前没有远程 CI |
| 工作循环上下文仍声明 M31 为标准数据基线 | 改为无共享重置基线，测试数据由 Testcontainers 隔离 |

## Active Script Set

- AI 治理：`ai-work-cycle.ps1`、`ai-quality-gate.ps1`、`ai-audit-snapshot.ps1`、`security-audit-gate.ps1`。
- 知识内容：`knowledge-naming-guard.ps1`、`knowledge-consistency-check.ps1`、`inspect-knowledge-object-references.ps1`。
- 浏览器：`im-browser-smoke.ps1`、`ui-split-v1-browser-smoke.ps1`。
- 运维：`deploy/scripts/` 下备份、恢复演练、健康、发布、恢复和回滚脚本。

## Validation

- PowerShell 语法：所有活动根脚本和运维脚本通过 Parser 检查。
- 远程包边界：`package.json` 只保留 `web:dev`、`web:build`、`web:lint`，不再引用被忽略文件。
- 活动脚本旧入口扫描：未发现 `/api/docs`、`/docs/`、M31/M40 数据命令或 `document` 资源查询；命名 guard 自身的禁止模式除外。
- 知识库命名门禁：通过，并已覆盖 `server/src/main/java`、`web/src`、`web/e2e`。
- 当前知识数据一致性：脚本已执行，12 项通过、2 项失败；同一条 `knowledge_content` 引用仍持久化 `/docs/...` 路径，且目标知识内容已经不存在。本轮不修改业务数据。
- CI 模板对应后端命令：`mvn -B test package` 通过，60 tests passed，V001-V047 空库迁移通过。
- CI 模板对应前端命令：lint/build 通过；保留 3 个既有 React Hook dependency warning，无 error。
- 工作循环 `light` 收口：后端跳过测试编译、前端 lint/build、chunk、懒加载、安全、Flyway 顺序、命名、生成物、TODO 和文档结构门禁全部通过。

## Residual Risks

- 当前数据库存在一条悬空且路径非规范的知识内容引用。数据修复任务应判断删除该引用还是重新绑定有效目标，不能只机械改写路径。
- 归档旧协同 Playwright spec 后，当前知识内容多人协同缺少规范 API 的浏览器自动化覆盖；应在后续测试路线中重新实现，而不是恢复旧 spec。
- `scripts/`、`deploy/scripts/`、`.github/workflows/` 和 `docs/` 按约定保持本地，不提交远程；当前远程仓库没有 CI 合并门禁，提交前需要人工执行项目门禁。
