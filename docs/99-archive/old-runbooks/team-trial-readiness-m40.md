---
title: Team Trial Readiness Runbook
status: archived
updated_at: 2026-06-18
---

# Team Trial Readiness Runbook

> 历史说明：该手册对应 M40 试运行阶段，其 M31 reset/smoke 前置条件不再代表当前发布准入流程。

## Go/No-Go Criteria

| Area | Go condition | No-Go condition |
| --- | --- | --- |
| P0/P1 defects | No open P0 or P1 issue | Any unresolved P0/P1 |
| Quality gate | `pnpm verify:full` passes | Backend, frontend, security, migration, or docs gate fails |
| M31 regression | `pnpm data:reset` and `pnpm smoke:m31` pass for final readiness | Data reset or M31 smoke fails |
| Data safety | Backup and restore dry-run reports exist and pass | No recent backup or restore drill evidence |
| Observability | Health, Prometheus, request logs, and error logs are available | Cannot inspect health, metrics, or logs |
| Rollback | Admin can identify latest backup and rollback command | Restore/rollback procedure is unclear |

Decision rule: enter trial only when all Go conditions are met. P2 issues may remain only with owner, workaround, and target milestone.

## Trial Roles

Use `pnpm trial:team-template` to generate the current 10-person account CSV and checklist under `.local-reports/`.

| Role | Main workflow |
| --- | --- |
| Trial Admin | Users, permissions, audit, backup, restore |
| Trial PM | Requirements, task split, meeting follow-up |
| Trial Product | PRD, requirement changes, Base definitions |
| Trial Design | Document comments and design handoff |
| Trial Frontend | Task work, IM collaboration, fix feedback |
| Trial Backend | API fixes, Base data, release support |
| Trial QA | Bug filing, verification failed/pass |
| Trial Ops | Release approval, health check, rollback |
| Trial Business | Acceptance, change confirmation |
| Trial Viewer | Permission boundary checks |

## Five Trial Projects

| Project | Required flow |
| --- | --- |
| P1 Customer Portal | Requirement proposal, PRD, IM discussion, task split, acceptance |
| P2 Mobile Login | Bug report, fix, failed verification, second fix, pass |
| P3 Data Dashboard | Base fields, saved views, record comments, docs embed |
| P4 Release Approval | Approval, notification, audit, backup before release |
| P5 IM Reliability | Long conversation, pin/revoke/reaction/read state, object links |

## Team Initialization

1. Run `pnpm trial:team-template`.
2. Create accounts from the generated CSV through `/admin/users`.
3. Use one-time passwords outside the repository; do not write real credentials into docs or scripts.
4. Create or verify the five trial projects and project groups.
5. Create the default team document space and Base for P3.
6. Confirm `trial_viewer` cannot access restricted P2/P3 objects.
7. Record the initialization report path in the M40 execution report.

## Issue Intake

Use `.github/ISSUE_TEMPLATE/trial-issue.yml` for every trial issue.

| Severity | Definition | Trial action |
| --- | --- | --- |
| P0 | Login, data safety, startup, backup, or security blocker | Stop trial until fixed |
| P1 | Core IM/project/docs/Base/approval workflow blocked | No-Go unless fixed |
| P2 | Workaround exists but disrupts team work | Go only with owner and deadline |
| P3 | Polish or low-risk follow-up | Track outside Go/No-Go |

## Data Migration And Cleanup

- Before real data entry, either reset to a clean M31 baseline for final regression or initialize a separate trial dataset from the admin account.
- Never mix real trial data with archived M12 data.
- Before importing real CSV data into Base, export a backup from the source system and keep mapping notes in `.local-reports/`.
- After trial, export issues and Base data needed for follow-up, then disable trial accounts.
- To return to a demo baseline, run `pnpm data:reset` only when explicitly choosing to discard trial data.

## Final Regression

Run these commands for trial readiness:

```powershell
pnpm verify:full
pnpm data:reset
pnpm smoke:m31
pnpm trial:readiness -- -RequireFullGate -RequireDataReset -M31SmokePassed
```

Record the generated readiness report and use its GO/NO-GO decision as the M40 final evidence.
