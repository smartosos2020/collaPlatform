---
title: 产品参考资料索引
status: reference
last_reviewed: 2026-07-12
---

# 产品参考资料索引

本目录保存外部产品调研和已完成路线的原始需求语义，用于解释设计来源、比较产品形态和追溯验收目标。这里的内容不属于当前实现事实，也不能直接作为执行路线。

## 阅读优先级

1. 先以 `docs/00-product/current-product-scope.md` 确认当前产品能力。
2. 再以 `docs/01-architecture/current-architecture.md` 和代码确认技术现实。
3. 只有需要比较 Lark 或追溯原始需求时才阅读本目录。
4. 发现参考资料与当前代码冲突时，以当前代码和 active 文档为准，并在新路线中记录需要重新决策的差异。

## 资料分类

| 文档 | 类型 | 使用方式 |
| --- | --- | --- |
| `lark-help-center-functional-requirements.md` | 外部产品功能域调研 | 用于理解 Lark 功能边界，不代表本项目承诺全部实现 |
| `lark-product-shape-analysis.md` | 外部产品形态分析 | 用于比较用户端、管理端和协作对象组织方式 |
| `org-usergroup-permission-requirements.md` | 已完成路线的原始需求 | 用于追溯 ORG-M1 至 ORG-M6 的目标和验收语义 |

## 维护规则

- 外部资料必须标明来源、核对日期和参考属性。
- 已完成需求转入本目录后，不再维护任务完成进度；完成态以归档路线和执行报告为证据。
- 新任务计划只能写入 `docs/02-roadmap/current-roadmap.md`。
- 不在参考资料中新增当前 API、schema 或运行命令的唯一说明，避免形成第二事实源。
