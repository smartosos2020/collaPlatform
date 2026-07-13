---
title: 文档入口
status: active
last_code_check: 2026-07-12
---

# 文档入口

本文档是 Colla Platform 当前文档体系的入口。2026-07-12 已按当前代码、路由、V047 schema、脚本和测试重新核对；默认上下文只读取当前事实，历史过程按需查询。

## 默认读取

AI 工作循环默认只读取这些 active 文档：

1. `docs/README.md`
2. `docs/00-product/current-product-scope.md`
3. `docs/01-architecture/current-architecture.md`
4. `docs/01-architecture/technology-selection.md`
5. `docs/01-architecture/platform-object-model.md`
6. `docs/02-roadmap/current-roadmap.md`
7. `docs/03-engineering/ai-engineering-governance.md`

M31/M40 仿真和试运行材料已经归档，不再作为默认数据或发布基线。日常 AI 工作循环只做本轮影响范围内的目标验证；路线最后一个里程碑才运行 `route-final`。

## 目录说明

| 目录 | 用途 | 默认读取 |
| --- | --- | --- |
| `00-product/` | 当前产品范围；`references/` 保存外部产品和历史需求参考 | 只默认读取 `current-product-scope.md` |
| `01-architecture/` | 当前技术架构、技术选型、平台对象模型 | 是 |
| `02-roadmap/` | 当前唯一执行路线图；目录内只保留 `current-roadmap.md` | 是 |
| `03-engineering/` | AI 工程治理、Git、运维安全规范 | 只默认读取 `ai-engineering-governance.md` |
| `04-testing/` | 保留目录；暂无 active 测试文档 | 否 |
| `05-runbooks/` | 当前可执行的本地开发、质量、浏览器和运维手册 | 按需 |
| `90-reports/` | 历史执行记录和检查报告；读取前先看该目录 README | 否 |
| `99-archive/` | 已完成路线、旧草案和失效 runbook | 否 |

## 当前 Active 文档

| 文档 | 说明 |
| --- | --- |
| `00-product/current-product-scope.md` | 当前已经实现、部分实现、未完成的产品范围 |
| `01-architecture/current-architecture.md` | 当前后端、前端、数据库、事件、测试现实 |
| `01-architecture/technology-selection.md` | 当前代码实际采用的技术栈 |
| `01-architecture/platform-object-model.md` | 当前平台对象、链接、卡片、权限态和搜索关系 |
| `02-roadmap/current-roadmap.md` | 当前唯一执行路线入口；没有 active 路线时明确写“等待目标” |
| `03-engineering/ai-engineering-governance.md` | 当前 AI 工作循环与质量门禁 |

## 参考资料

这些文档按需读取，不进入默认 AI 工作循环；先阅读 `00-product/references/README.md` 判断用途和可信边界。

| 文档 | 说明 |
| --- | --- |
| `00-product/references/README.md` | 参考资料分类、阅读优先级和维护规则 |
| `00-product/references/lark-help-center-functional-requirements.md` | 基于 Lark 帮助中心公开内容整理的功能域参考，不代表当前实现 |
| `00-product/references/lark-product-shape-analysis.md` | Lark 产品形态阶段性分析 |
| `00-product/references/org-usergroup-permission-requirements.md` | 已完成 ORG 路线的原始需求参考 |

任何参考资料衍生的里程碑计划必须写入 `02-roadmap/current-roadmap.md`，不得在 `02-roadmap/` 下新增第二份路线图，避免新会话误读执行入口。

## 当前 Runbook

| 文档 | 说明 |
| --- | --- |
| `05-runbooks/local-dev.md` | 本地开发和启动 |
| `05-runbooks/quality-gate.md` | 本地质量门禁 |
| `05-runbooks/browser-smoke.md` | 浏览器冒烟验证 |
| `05-runbooks/admin-operations.md` | 单节点管理员运维手册 |
| `05-runbooks/local-artifacts.md` | 本地生成物和忽略规则 |

## 历史文档读取规则

- `90-reports/` 只用于追溯某个里程碑当时做了什么。
- `99-archive/` 只用于追溯旧决策或旧草案。
- `99-archive/old-runbooks/` 中的命令在执行前必须重新核对当前 schema 和路由，不能直接照抄运行。
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
