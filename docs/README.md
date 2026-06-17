---
title: 文档入口
status: active
last_code_check: 2026-06-15
---

# 文档入口

本文档是 Colla Platform 当前文档体系的入口。本次整理已经按当前代码、路由、迁移、脚本和测试做过一次对照；后续日常迭代只要求更新本轮相关文档，下次集中整理时再做全量代码对照。

## 默认读取

AI 工作循环默认只读取这些 active 文档：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/01-architecture/technology-selection.md`
5. `docs/01-architecture/platform-object-model.md`
6. `docs/02-roadmap/current-roadmap.md`
7. `docs/03-engineering/ai-engineering-governance.md`

当前统一测试基线会在后续文档规范阶段单独收敛。已过期的 M12 测试用例已归档到 `docs/90-reports/`。

## 目录说明

| 目录 | 用途 | 默认读取 |
| --- | --- | --- |
| `00-product/` | 当前产品范围、产品形态参考 | 只默认读取 `current-product-scope.md` |
| `01-architecture/` | 当前技术架构、技术选型、平台对象模型 | 是 |
| `02-roadmap/` | 当前路线图 | 是 |
| `03-engineering/` | AI 工程治理、Git、运维安全规范 | 只默认读取 `ai-engineering-governance.md` |
| `04-testing/` | 预留目录；当前测试基线后续再统一 | 按需 |
| `05-runbooks/` | 后续沉淀运行手册 | 按需 |
| `90-reports/` | 阶段执行记录、检查报告 | 否 |
| `99-archive/` | 被替代路线图和旧草案 | 否 |

## 当前 Active 文档

| 文档 | 说明 |
| --- | --- |
| `00-product/current-product-scope.md` | 当前已经实现、部分实现、未完成的产品范围 |
| `01-architecture/current-architecture.md` | 当前后端、前端、数据库、事件、测试现实 |
| `01-architecture/technology-selection.md` | 当前代码实际采用的技术栈 |
| `01-architecture/platform-object-model.md` | 当前平台对象、链接、卡片、权限态和搜索关系 |
| `02-roadmap/current-roadmap.md` | 当前已完成事实、未完成 gap 和 M31 执行路线 |
| `03-engineering/ai-engineering-governance.md` | 当前 AI 工作循环与质量门禁 |

## 历史文档读取规则

- `90-reports/` 只用于追溯某个里程碑当时做了什么。
- `99-archive/` 只用于追溯旧决策或旧草案。
- 历史文档中的 API、表结构、路线图和完成状态不自动代表当前事实。
- 当前事实以 active 文档和代码为准。

## 本次代码对照范围

- `package.json`
- `docker-compose.yml`
- `server/pom.xml`
- `server/src/main/java`
- `server/src/main/resources/db/migration`
- `server/src/test/java`
- `web/src/app/router.tsx`
- `web/src/modules`
- `web/src/shared`
- `scripts`
