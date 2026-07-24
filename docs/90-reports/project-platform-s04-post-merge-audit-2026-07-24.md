---
title: PROJECT-PLATFORM-S04 合入主干后收口审计
status: reference
audited_at: 2026-07-24
audited_commit: 134c3706db280f364eeffd7ae44526b6b1d6180d
compared_with: ee8fb6883ac5868976cb261a25ab6d4972c33981
stage: PROJECT-PLATFORM-S04
verdict: completed-no-reopen
---

# PROJECT-PLATFORM-S04 合入主干后收口审计

## 1. 审计结论

`PROJECT-PLATFORM-S04` 可以保持 Completed，不需要重开。

S04 的 5 个 Milestone、53 个 Task 均有任务级验证合同和验收证据；字段定义、选项、规则、复杂类型、配置 UI、权限隔离、迁移、幂等、并发、审计和性能边界与路线目标一致。发现的新增跨模块依赖属于平台架构治理债务，不是否定 S04 功能完成度的缺陷，已转入 `PLATFORM-SCALE-S01`。

## 2. 审计范围

- 当前路线及 M1-M5 五份执行报告。
- S04 后端 schema、领域、应用、API、权限和审计实现。
- S04 前端字段配置入口、状态和真实浏览器规格。
- 空库与 V063 升级迁移、完整后端测试及最终工作循环证据。
- 合入前后后端依赖、前端 feature 依赖、跨 owner SQL 和运行组件差异。

## 3. 完成度证据

| 检查项 | 结果 |
| --- | --- |
| 路线状态 | 5/5 Milestone Completed，53/53 Task Done |
| 任务合同 | 五份执行报告共 53 条 Verification Contract |
| 验收闭环 | 五份执行报告共 53 条 Acceptance Evidence |
| 规划合同 | `work:plan-check` 通过，revision 13、S04、5 个 Milestone、53 个 Task、最终 M5 一致 |
| 后端最终回归 | 220 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS |
| 前端静态门禁 | ESLint 通过 |
| 浏览器闭环 | 4 个 S04 专用真实隔离规格全部通过，未使用 route interception |
| 最终质量门 | route-final 报告全部 PASS，无 warning、failure 或 waiver |

## 4. 代码合同复核

- 数据库使用 workspace/space/type 复合约束、永久 key 保护和生命周期约束。
- 写服务使用 aggregate version、request-id receipt 和原子事务，失败不会留下部分配置。
- 审计只记录 hash 和结构化摘要，不保存复杂字段的敏感原值。
- API 返回服务端 `availableActions`，六类身份的允许、拒绝和隐藏语义有自动化证据。
- S04 未创建 WorkItem 实例、字段值、布局、发布流水线或按字段动态 DDL，未越过既定 Stage 边界。

## 5. 合入后架构变化

| 指标 | S04 前 | S04 后 | 增量 |
| --- | ---: | ---: | ---: |
| Java 文件 | 215 | 233 | +18 |
| 跨模块 import | 194 | 204 | +10 |
| 涉及跨模块 import 的文件 | 57 | 59 | +2 |
| foreign infrastructure import | 41 | 47 | +6 |
| 涉及 foreign infrastructure 的文件 | 20 | 22 | +2 |
| 有向模块依赖边 | 58 | 58 | 0 |
| 含 foreign infrastructure 的事务应用文件 | 15 | 16 | +1 |

新增依赖集中在：

- `WorkItemFieldComplexReferenceValidator` 直接使用 file、identity、organization、user-group 的 infrastructure Repository。
- `WorkItemFieldConfigurationService` 直接使用 audit 和 event infrastructure。

前端 feature 图未因 S04 增加新的跨 feature 边；S04 migration 只创建 project owner 的表，没有新增直接跨 owner SQL。后端 11 模块循环依赖分量仍未改善。

## 6. 决策

1. 归档 S04 路线，保留其历史 Go S05 功能结论。
2. 不直接激活 PROJECT-PLATFORM-S05，避免布局、发布和 WorkItem 运行时继续放大现有依赖。
3. 暂停 `PROJECT-PLATFORM` 于 S05 之前，激活 `PLATFORM-SCALE-S01`。
4. 在 S01 建立公共合同、表 owner、自动门禁并清理 project P0 依赖；架构债务不能回写为 S04 未完成项。

详细计数、方法和运行组件基线见 `docs/90-reports/platform-scale-readiness-baseline-2026-07-24.md`。
