# PROJECT-PLATFORM-S17-M2 Execution Report

## Scope

PROJECT-PLATFORM-S17-M2-T01 到 PROJECT-PLATFORM-S17-M2-T12

## Verification Contract

| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M2-T01 | core-system | system-real-isolated | not-required | isolated | no | M1/V123 boundary review |
| PROJECT-PLATFORM-S17-M2-T02 | core-system | system-real-isolated | not-required | isolated | no | run/step/receipt contract |
| PROJECT-PLATFORM-S17-M2-T03 | core-system | system-real-isolated | not-required | isolated | no | PostgreSQL V001-V123 |
| PROJECT-PLATFORM-S17-M2-T04 | core-system | system-real-isolated | not-required | isolated | no | layered exact replay |
| PROJECT-PLATFORM-S17-M2-T05 | non-core | integration | not-required | not-required | no | canonical WorkItem command |
| PROJECT-PLATFORM-S17-M2-T06 | non-core | integration | not-required | not-required | no | canonical workflow commands |
| PROJECT-PLATFORM-S17-M2-T07 | core-user | e2e-real-isolated | real | isolated | no | relation and notification action |
| PROJECT-PLATFORM-S17-M2-T08 | core-user | e2e-real-isolated | real | isolated | no | preview/run/step UI |
| PROJECT-PLATFORM-S17-M2-T09 | non-core | integration | not-required | not-required | no | authority and REST recalibration |
| PROJECT-PLATFORM-S17-M2-T10 | core-user | e2e-real-isolated | real | isolated | no | six identities and cross-space |
| PROJECT-PLATFORM-S17-M2-T11 | non-core | unit | not-required | not-required | yes | bounded fixture budgets |
| PROJECT-PLATFORM-S17-M2-T12 | core-system | system-real-isolated | not-required | isolated | no | architecture and checkpoint |

## Completed Items

- V123 creates project-owned run, step, action receipt and disposable stats tables with composite boundaries, source uniqueness, lease/fencing metadata and bounded indexes.
- Event handler matches five public version-1 event types and at most 20 enabled rules.
- Execution binds an immutable rule version, evaluates the bounded declarative condition tree and creates at most eight ordered steps.
- Field, workflow/node, related-item/relation and notification actions use canonical public commands/events.
- Manual execution, dry-run, exact replay, stable errors, audit/outbox and minimal run history are exposed through REST and Web.

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S17-M2-T01 | M1 boundary rechecked | V122/V123 + owner manifest | architecture boundary gate | Browser not required; isolated system evidence | Done |
| PROJECT-PLATFORM-S17-M2-T02 | versioned execution contracts | `AutomationExecutionModels` | service tests | Browser not required; isolated system evidence | Done |
| PROJECT-PLATFORM-S17-M2-T03 | durable bounded schema | `V123__create_project_automation_execution.sql` | real PostgreSQL foundation test | Browser not required; isolated system evidence | Done |
| PROJECT-PLATFORM-S17-M2-T04 | match/claim/steps/replay | execution repository/service/handler | replay unit + foundation tests | isolated API replay | Done |
| PROJECT-PLATFORM-S17-M2-T05 | canonical field update | `WorkItemService.update` branch | backend compile/service tests | isolated execution API | Done |
| PROJECT-PLATFORM-S17-M2-T06 | canonical state/node flow | workflow/node public command branches | backend compile/service tests | isolated execution API | Done |
| PROJECT-PLATFORM-S17-M2-T07 | related item/relation/notification | public services + `notification.created` | service tests | Real isolated notification inbox flow | Done |
| PROJECT-PLATFORM-S17-M2-T08 | safe preview and step explanation | `AutomationExecutionPanel` | web build/lint | Real isolated browser at 1440/1366/820 | Done |
| PROJECT-PLATFORM-S17-M2-T09 | authority and recovery | current membership gate + exact receipts | service tests | REST recalibration | Done |
| PROJECT-PLATFORM-S17-M2-T10 | isolation matrix | API gates and composite queries | isolated E2E | Real isolated owner/admin/member/guest/outsider/enterprise flow | Done |
| PROJECT-PLATFORM-S17-M2-T11 | deterministic limits | 20 matches/8 steps/100 runs/32 KiB event | unit constants and fixtures | DOM bounded list | Done |
| PROJECT-PLATFORM-S17-M2-T12 | docs and checkpoint | five architecture docs + roadmap | workbench checkpoint | Browser not required; isolated system evidence | Done |

## Code Changes

- Backend: automation execution models, JDBC repository, service, event handler and REST endpoints.
- Database: V123 run/step/action-receipt/stats schema and owner manifest revision.
- Web: execution preview, controlled execute, run history and step diagnosis panel.
- Tests: service, PostgreSQL foundation and isolated six-identity E2E.

## Validation

- Backend tests: `AutomationExecutionServiceTests` passed.
- Frontend build: `pnpm web:build` passed.
- Frontend lint: `pnpm web:lint` passed.
- PostgreSQL: `AutomationExecutionFoundationIntegrationTests` passed against PostgreSQL 16.
- Browser smoke: `project-platform-s17-m2.spec.ts` isolated real flow.
- Local quality gate: `.local-reports/quality-gate-20260728T054329.md`.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps

- Start PROJECT-PLATFORM-S17-M3-T01 in a new work cycle; do not implement external connectors before M4.
