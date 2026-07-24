---
title: 长期专项索引
status: active
updated_at: 2026-07-24
tracked_paused_programs: KB-PRODUCT, PROJECT-PLATFORM
---

# 长期专项索引

本文是机器可校验的专项入口，不承载可执行 Task。当前执行任务仍只存在于 `docs/02-roadmap/current-roadmap.md`。

| Program | Status | Current Stage | Remaining Commitment | Source |
| --- | --- | --- | --- | --- |
| PLATFORM-SCALE | Active | PLATFORM-SCALE-S05 | S01-S04 已归档；S05 正在执行容量、长稳、恢复与运维收口 | `platform-scale-program.md` |
| PROJECT-PLATFORM | Paused | none | S05-S21；等待 PLATFORM-SCALE-S05 发布容量/边界 Go/No-Go 后再复核恢复 | `project-platform-program.md` |
| KB-PRODUCT | Paused | none | KB-PRODUCT-M12-T06 至 T10：真实参与者试用、反馈、复验和 Go/No-Go | `../../99-archive/superseded-roadmaps/kb-product-roadmap-paused-2026-07-18.md` |

规则：

- 恰好一个 Program 可处于 `Active`；活动路线执行中其 Stage 必须一致，Stage 路线已完成但尚未归档时 Current Stage 暂为 `none`。
- `tracked_paused_programs` 中的专项必须保留 `Paused` 行、`none` 当前 Stage、剩余承诺和恢复来源。
- 暂停专项恢复时，先基于当时产品、代码和数据事实重建 Program/Stage，再替换当前路线；不得直接执行归档路线。
