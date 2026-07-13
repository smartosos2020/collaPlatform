---
title: M46 Execution Report
status: archived
milestone: M46
updated_at: 2026-06-20
---

# M46 Execution Report

## Scope

- M46-T01 到 M46-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M46-T01 | Done | `PermissionDecisionService` rank 扩展为 owner/manage/edit/comment/view；`DocumentService.requireComment` 改为 comment 级。 |
| M46-T02 | Done | `V031` 扩展 check constraint 保留 view/edit/manage，创建者权限升级为 owner，旧授权 API 继续兼容。 |
| M46-T03 | Done | `DocsPage` 分享弹窗支持复制链接、邀请成员和设置权限。 |
| M46-T04 | Done | 组织内链接启用后 workspace 成员可按 link permission 访问；禁用后无直授用户恢复 forbidden。 |
| M46-T05 | Done | 新增 `document_share_links`，支持 enabled、scope、permissionLevel、expiresAt 和 token。 |
| M46-T06 | Done | `document_permissions.source_type/source_document_id` 标记 inherited，前端权限面板显示继承来源。 |
| M46-T07 | Done | `space` 复用为知识库入口，补充 description、coverUrl、defaultPermissionLevel、knowledgeBase。 |
| M46-T08 | Done | 新增 permission request 占位 API；无权限页面可提交申请并通知 owner/manage。 |

## Code Changes

- Backend: extended document permission rank, share-link APIs, knowledge-base settings and permission request notifications.
- Frontend: upgraded sharing modal, added knowledge-base creation/settings UI, permission-source display and no-access request form.
- Database: added `V031__extend_document_sharing_permissions.sql`.
- Scripts: none.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked M46-T01 through M46-T08 execution evidence. |
| `docs/01-architecture/current-architecture.md` | Updated | Documented V031, share-link APIs, knowledge-base fields and permission request flow. |
| `docs/90-reports/m46-execution-report.md` | Updated | Captured implementation and validation evidence. |

## Validation

- Backend targeted tests: `mvn -f server/pom.xml -Dtest=DocumentControllerIntegrationTests test` passed; 7 tests, 0 failures, 0 errors.
- Frontend lint: `pnpm web:lint` passed with 3 existing exhaustive-deps warnings in `useDocumentCollaboration`.
- Frontend build: `pnpm web:build` passed.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M46-document-sharing-permissions" -GateMode quick` passed; backend 40 tests, frontend lint/build, chunk budget, route lazy-loading, security audit, Flyway order, generated artifact scan, TODO inventory and documentation contract all passed.
- Work-cycle finish: `pnpm work:finish -- -Goal "M46-document-sharing-permissions"` passed; backend 40 tests, backend package, frontend lint/build, chunk budget, route lazy-loading, security audit, Flyway order, generated artifact scan, TODO inventory and documentation contract all passed.
- Browser smoke: not run separately; M46-T10 is outside this task range.

## Remaining Gaps

- M46-T09 audit deepening and M46-T10 dedicated E2E remain outside this cycle.
- Share links are organization-internal only; external/public links are intentionally deferred.

## Next Steps

- Proceed to M47-T01 through M47-T08.
