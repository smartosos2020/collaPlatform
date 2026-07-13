---
title: KB-NAME-M10 Execution Report
status: archived
milestone: KB-NAME-M10
updated_at: 2026-07-11
---

# KB-NAME-M10 Execution Report

## Scope

- KB-NAME-M10-T01 到 KB-NAME-M10-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| T01 | Done | `/api/docs` 控制器、DTO、响应适配和 filter 删除；规范契约测试断言旧路径 404。 |
| T02 | Done | `web/src/modules/docs` 与旧 locator 删除，路由和 query 不再接收 `docId`。 |
| T03 | Done | `modules.doc` 删除，知识核心方法及参数改为 item/content 语义。 |
| T04 | Done | `document` 对象别名、ACL 回退、旧 IM/协同命令分支删除。 |
| T05 | Done | V047 删除旧权限表/活动引用并增加 no-document 约束。 |
| T06 | Done | 旧浏览器路由全部删除；未保留 redirect 例外。 |
| T07 | Done | 规范 API、搜索和后台治理 DTO/测试更新。 |
| T08 | Done | `knowledge-naming-guard` 接入质量门禁。 |

## Code Changes

- Backend: 删除旧模块和兼容边缘；规范化 knowledge/search/IM/permission/platform 边界。
- Frontend: 删除 docs 模块和旧路由；搜索、后台知识治理字段改为 `contentType/itemId/itemCount`。
- Database: 新增 V047，存量副本执行后 item/block/version 仍为 19/141/23，旧权限表、对象规则和活动引用均为 0。
- Scripts: 新增本地 `knowledge-naming-guard.ps1`，接入 `ai-quality-gate.ps1` 和 `package.json`。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `current-roadmap.md` | 更新 M10 为 Done | 记录逐项实现与 M11 入口。 |
| 本报告 | 完成 | 固化删除范围、数据证据和验证结果。 |

## Validation

- Backend tests: `KnowledgeContentControllerIntegrationTests,KnowledgeSchemaMigrationIntegrationTests,PlatformObjectTypesTests,ImControllerIntegrationTests`，13/13 通过。
- Frontend build: `pnpm web:build` 通过。
- Naming guard: `pnpm kb:naming-guard` 通过。
- Migration: Testcontainers V001-V047 通过；存量 V043 副本手工顺序执行 V044-V047 通过。
- Browser smoke: 本里程碑只删除兼容入口，主路径浏览器全量冒烟后置 M11。

## Remaining Gaps

- 不可变历史迁移、归档报告和历史审计仍可出现旧词；活动产品源码由门禁约束。

## Next Steps

- 执行 KB-NAME-M11 全量迁移、恢复、跨模块测试、浏览器冒烟和 route-final。
