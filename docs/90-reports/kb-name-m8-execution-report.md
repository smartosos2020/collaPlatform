---
title: KB-NAME-M8 Execution Report
status: archived
milestone: KB-NAME-M8
updated_at: 2026-07-11
---

# KB-NAME-M8 Execution Report

## Scope

- KB-NAME-M8-T01 到 KB-NAME-M8-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-NAME-M8-T01 | Done | 9 个活动正文 item block 覆盖 100%；23 个版本 snapshot 完整；8 个模板由 V045 补齐。 |
| KB-NAME-M8-T02 | Done | Repository 和搜索 SQL 已移除 item content 读取，API content 由 blocks 投影。 |
| KB-NAME-M8-T03 | Done | 版本读、diff、restore 使用 `block_snapshot`；新版本仅写块快照。 |
| KB-NAME-M8-T04 | Done | 停止四类旧字段双写，并由 schema 集成测试断言旧列为 0。 |
| KB-NAME-M8-T05 | Done | 离线全库快照 402918 bytes；隔离恢复库 item/block/version 为 19/141/23。 |
| KB-NAME-M8-T06 | Done | V045 删除 item content、version content、collaboration snapshot_content、template content。 |
| KB-NAME-M8-T07 | Done | 知识内容、版本、模板、导出、搜索和迁移测试共 5/5 通过。 |
| KB-NAME-M8-T08 | Done | 架构文档和迁移检查脚本已记录 blocks 单一来源与恢复边界。 |

## Code Changes

- Backend: 正文兼容文本从 active blocks 生成；版本和模板兼容文本从 block snapshot 生成；协同 payload 保存 blocks。
- Frontend: 无 UI 结构变化，继续消费 API 的兼容 `content` 投影和规范 `blocks`。
- Database: 新增 V045，迁移 template/collaboration block payload，强制快照非空并删除四个旧列。
- Scripts: 新增本地离线全库快照/恢复工具；迁移检查、命名盘点和 M31 SQL 同步 block-only schema。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/01-architecture/current-architecture.md` | Update | 明确 blocks 与 block snapshot 是唯一持久化内容来源。 |
| `docs/02-roadmap/current-roadmap.md` | Update | 标记 M8 完成并切换下一入口到 M9。 |

## Validation

- Backend tests: `KnowledgeContentControllerIntegrationTests,KnowledgeSchemaMigrationIntegrationTests,SearchCollaborationIntegrationTests`，5/5 通过。
- Frontend build: 由 M8 工作循环收口门禁执行。
- pnpm verify: 由 M8 工作循环收口门禁执行。
- Browser smoke: 无 UI 改动，本里程碑不重复执行。

## Remaining Gaps

- API 的 `content` 字段暂时保留为 blocks 的兼容投影，待 M10 删除旧 API 后再评估外部字段收敛；数据库中不再持久化该投影。
- 历史 V001-V044 和归档资料仍包含旧字段名称，按不可变历史保留。

## Next Steps

- 进入 KB-NAME-M9，迁移存量类型/链接并建立兼容命中观察窗。
