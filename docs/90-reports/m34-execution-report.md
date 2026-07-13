---
title: M34 Execution Report
status: archived
milestone: M34
updated_at: 2026-06-18
---

# M34 Execution Report

## Scope

- M34-T01 到 M34-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M34-T01 | Done | `ProjectService` defines action-based workflow rules for requirement/task/bug statuses and required fields. |
| M34-T02 | Done | `POST /api/issues/{issueId}/transition` accepts action-based requests and still supports legacy status-only requests. |
| M34-T03 | Done | BUG verification now enforces resolved-before-pass/fail and writes workflow reason/resolution for pass/fail branches. |
| M34-T04 | Done | Duplicate, cannot reproduce, and information-required branches persist structured reason/resolution and duplicate relations. |
| M34-T05 | Done | Requirement scope change, delay, and cancellation actions persist reason/resolution and optional due date. |
| M34-T06 | Done | Projects UI now uses workflow actions, a reason/target/date modal, workflow metadata, and fixed due-date sorting. |
| M34-T07 | Done | Workflow actions emit domain events, notifications, audit logs, project IM messages, and M31 seed/smoke assertions were extended. |
| M34-T08 | Done | Roadmap, product scope, architecture, platform object model, and this report were updated. |

## Code Changes

- Backend: added issue workflow action definitions, action validation, workflow metadata, richer issue summaries, and platform object metadata.
- Frontend: added action-based project UI, workflow action modal, branch metadata display, and readable activity labels.
- Database: added `V027__extend_issue_workflow_state.sql` for issue workflow reason, note, resolution, resolved time, and closed time.
- Scripts: extended M31 seed/verify data and M31 browser smoke assertions for M34 workflow semantics.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Mark M34 tasks complete. |
| `docs/00-product/current-product-scope.md` | Updated | Reflect action-based project/BUG workflow behavior. |
| `docs/01-architecture/current-architecture.md` | Updated | Document V027 and transition API semantics. |
| `docs/01-architecture/platform-object-model.md` | Updated | Document issue workflow metadata in object summaries. |
| `docs/90-reports/m34-execution-report.md` | Updated | Record M34 execution evidence and validation. |

## Validation

- Backend tests: `mvn -Dtest=ProjectControllerIntegrationTests test` passed.
- Frontend lint: `pnpm web:lint` passed.
- Frontend build: `pnpm web:build` passed.
- Work-cycle checkpoint: `pnpm work:checkpoint -- -Goal "M34-project-workflow" -GateMode quick` passed.
- Work-cycle finish: `pnpm work:finish -- -Goal "M34-project-workflow"` passed.
- Browser smoke: local backend/frontend restarted; logged in as `admin / admin123456`, opened `/projects`, opened a bug issue, verified workflow actions, submitted the "信息不足" action with a reason, and confirmed the drawer refreshed with workflow reason/resolution and no browser console warnings/errors.

## Remaining Gaps

- Workflow action permission is currently project editor based; finer role rules such as QA-only verification remain future work.
- M31 full baseline regression was not run by default, per explicit-request-only policy.

## Next Steps

- M35 should connect IM message-to-issue creation with the new workflow actions.
