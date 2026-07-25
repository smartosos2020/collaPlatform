---
title: 长期专项索引
status: active
updated_at: 2026-07-26
tracked_paused_programs: KB-PRODUCT, PLATFORM-SCALE
---

# 长期专项索引

本文是机器可校验的专项入口，不承载可执行 Task。当前执行任务仍只存在于 `docs/02-roadmap/current-roadmap.md`。

| Program | Status | Current Stage | Remaining Commitment | Source |
| --- | --- | --- | --- | --- |
| PROJECT-PLATFORM | Active | none | S01-S05 已完成；S05 待归档，S06 已 Go 但尚未激活 | `project-platform-program.md` |
| PLATFORM-SCALE | Paused | none | S05-M1 已完成；M2-M5 待核心功能、接口、数据模型和负载模型稳定后恢复 | `platform-scale-program.md` |
| KB-PRODUCT | Paused | none | KB-PRODUCT-M12-T06 至 T10：真实参与者试用、反馈、复验和 Go/No-Go | `../../99-archive/superseded-roadmaps/kb-product-roadmap-paused-2026-07-18.md` |

规则：

- 恰好一个 Program 可处于 `Active`；活动路线执行中其 Stage 必须一致，Stage 路线已完成但尚未归档时 Current Stage 暂为 `none`。
- `tracked_paused_programs` 中的专项必须保留 `Paused` 行、`none` 当前 Stage、剩余承诺和恢复来源。
- 暂停专项恢复时，先基于当时产品、代码和数据事实重建 Program/Stage，再替换当前路线；不得直接执行归档路线。
