---
title: PROJECT-PLATFORM-S02-M2 Execution Report
status: completed
milestone: PROJECT-PLATFORM-S02-M2
stage: PROJECT-PLATFORM-S02
updated_at: 2026-07-18
---

# PROJECT-PLATFORM-S02-M2 Execution Report

## Scope

- PROJECT-PLATFORM-S02-M2-T01 到 PROJECT-PLATFORM-S02-M2-T11。
- 本里程碑交付 ProjectSpace 成员、角色、邀请、最后 owner、成员目录、通知和并发幂等后端闭环。
- 不交付项目空间页面、成员管理 UI、企业治理 UI 或 legacy project 映射执行器；这些分别属于 S02-M3 和 M4。

## Verification Contract

| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M2-T01 | integration | not-required | not-required | no | exercise separated member identity, active role projection and reactivation constraints |
| PROJECT-PLATFORM-S02-M2-T02 | integration | not-required | not-required | no | inspect owner/admin/member/guest capability output and enforce assignment boundaries |
| PROJECT-PLATFORM-S02-M2-T03 | integration | not-required | not-required | no | list, directly join, change role and remove under owner/admin/member combinations |
| PROJECT-PLATFORM-S02-M2-T04 | integration | not-required | not-required | no | exercise invitation issue, resend, revoke, accept, reject and persisted expiry states |
| PROJECT-PLATFORM-S02-M2-T05 | integration | not-required | not-required | no | verify owner invariant, ownership transfer, self leave and identity lifecycle integration |
| PROJECT-PLATFORM-S02-M2-T06 | integration | not-required | not-required | no | search active same-workspace candidates without deriving space access from enterprise roles |
| PROJECT-PLATFORM-S02-M2-T07 | integration | not-required | not-required | no | consume member/invitation events into deduplicated notifications and inspect trace metadata |
| PROJECT-PLATFORM-S02-M2-T08 | integration | not-required | not-required | no | submit duplicate and concurrent mutations while serializing each space with a database row lock |
| PROJECT-PLATFORM-S02-M2-T09 | integration | not-required | not-required | no | execute role, cross-space, cross-workspace, owner, expiry and concurrency negative matrix |
| PROJECT-PLATFORM-S02-M2-T10 | integration | not-required | not-required | no | inspect response, event and trace payloads for invitation confidentiality and before/after state |
| PROJECT-PLATFORM-S02-M2-T11 | integration | not-required | not-required | no | run M1 plus M2 targeted regression, migration and strict stage documentation gates |

Browser evidence is not required because M2 changes only backend schema, services and APIs and does not add or alter a production web route or component. S02-M3 owns the user workspace, settings and admin-console UI and has an explicit isolated real-browser gate.

## Completed Items

- Added V057 same-space membership foreign key, active role lifecycle constraint, invitation version/request fields and one-pending-invitation index.
- Added owner/admin/member/guest capability matrix and effective member projection with active identity enforcement.
- Added member list, candidate search, direct join, role change, removal, self-leave and owner-transfer APIs.
- Added invitation issue, resend, revoke, accept, reject and expiry state machine without returning or logging token material.
- Added last-owner checks to identity disable and ownership handover to member offboarding.
- Added space-level pessimistic serialization, natural repeat convergence and request-ID resend/event deduplication.
- Added member/invitation audit events and notification outbox delivery through the existing domain-event worker.

## Concurrency And Security Decisions

| Concern | Decision | Database backstop |
| --- | --- | --- |
| same-space mutations | lock the `project_spaces` row before member, role, invite or owner changes | membership/role/invitation unique indexes |
| owner transfer | promote target to owner before demoting source; both statements share one transaction and space lock | one active role per member; same-space composite FK |
| identity disable | reject when the user is sole active owner | sole-owner query joins active users and roles |
| offboarding | enterprise flow transfers sole-owner spaces to the selected active handover user before identity disable | workspace/same-space foreign keys |
| invitation secret | generate 32 random bytes, persist only SHA-256, accept by authenticated invitation ID | token hash unique constraint |
| invitation expiry | persist expiration in `REQUIRES_NEW` before returning terminal conflict | status check plus pending unique index |
| repeat requests | unchanged member/state returns existing projection; resend records bounded request ID; notification key uses fixed UUID hash | event idempotency key and notification dedupe key |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M2-T01 | member aggregate and effective-role projection are constraint backed | domain records plus membership repository and V057 same-space FK | rejoin, duplicate member, active role, removed and disabled identity paths pass | not-required: backend aggregate only | Done |
| PROJECT-PLATFORM-S02-M2-T02 | built-in role capability matrix is explicit and independent of enterprise RBAC | `ProjectSpaceRole` capabilities and assignment rules | role capability endpoint plus owner/admin/member/guest positive and negative cases pass | not-required: no role UI in M2 | Done |
| PROJECT-PLATFORM-S02-M2-T03 | member governance API boundary is stable | membership controller and service operations | list/direct join/role transition/removal/repeat/cross-space and actor-role cases pass | not-required: API-only milestone | Done |
| PROJECT-PLATFORM-S02-M2-T04 | invitation state machine reaches every terminal state safely | invitation controller, repository versioning and expiry transaction | issue/resend/revoke/accept/reject/expired and repeat paths pass | not-required: no invitation UI in M2 | Done |
| PROJECT-PLATFORM-S02-M2-T05 | owner invariant and identity lifecycle integration remain atomic | space lock, owner guard, transfer and MemberService handover integration | unique owner cannot leave or become disabled; transfer and offboarding ownership pass | not-required: backend identity integration | Done |
| PROJECT-PLATFORM-S02-M2-T06 | candidate directory scope exposes only eligible active workspace users | identity projection filtering plus space-manager guard | existing, disabled, foreign-workspace and enterprise-only actors are excluded or rejected | not-required: no selector UI in M2 | Done |
| PROJECT-PLATFORM-S02-M2-T07 | trace and notification delivery are complete and deduplicated | audit metadata, outbox events and DomainEventWorker | worker consumption produces two invitee notifications for issue plus resend; repeated actions add none | not-required: asynchronous backend path | Done |
| PROJECT-PLATFORM-S02-M2-T08 | pessimistic serialization and repeat convergence protect state | project-space row lock, request marker and event UUID dedupe | two concurrent accepts return the same member, one row and one acceptance event | not-required: concurrency is API/database scoped | Done |
| PROJECT-PLATFORM-S02-M2-T09 | positive and negative integration matrix covers all required boundaries | four M2 scenarios plus four M1 lifecycle scenarios | 8 tests, 0 failures/errors; role, space, workspace, owner, expiry and concurrency covered | not-required: real services and PostgreSQL used without UI | Done |
| PROJECT-PLATFORM-S02-M2-T10 | invitation confidentiality and before/after trace are verifiable | token-free DTO/event/audit payloads and hash-only repository | response/event payload scans contain no token; hash is 64 hex; accepted trace has pending to accepted | not-required: sensitive backend contract | Done |
| PROJECT-PLATFORM-S02-M2-T11 | current facts and stage evidence match the implementation | scope, architecture, roadmap and this report | combined M1/M2 targeted tests, V001-V057 migration and work-cycle gates | not-required: M3 owns browser evidence | Done |

## Code Changes

- `V057__harden_project_space_membership.sql`: same-space role FK, invitation lifecycle/version/request columns and indexes.
- `ProjectSpaceModels`: roles, capabilities, member, invitation and candidate projections.
- `ProjectSpaceMembershipRepository` / `JdbcProjectSpaceMembershipRepository`: locked member and invitation persistence.
- `ProjectSpaceMembershipService`: role matrix, governance, invitation lifecycle, owner invariant, audit and notification outbox.
- `ProjectSpaceMembershipController` / `ProjectSpaceInvitationController`: authenticated member and invitation API surfaces.
- `MemberService`: last-owner disable guard and offboarding ownership handover.
- `ProjectSpaceMembershipControllerIntegrationTests`: M2 permission, security, lifecycle, notification and concurrency matrix.

## Validation

- Backend tests: `mvn -q "-Dtest=ProjectSpaceControllerIntegrationTests,ProjectSpaceMembershipControllerIntegrationTests" test` passed; 8 tests, 0 failures, 0 errors, 0 skipped.
- Frontend build: not run because M2 changes no frontend source, component or route; frontend implementation belongs to M3.
- Local quality gate: light checkpoint passed in `quality-gate-20260718T154547.md`; stage finish reruns targeted backend and strict documentation gates.
- Browser smoke: not required because M2 has no production browser surface; M3 requires isolated real-browser verification.
- Flyway: fresh Testcontainers PostgreSQL 16 validated and applied V001 through V057.
- Diff hygiene: `git diff --check` passed before stage finish.

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S02-M3-T01 | project-space production routes and list/detail experience belong to the UI milestone | non-blocking for M2 backend acceptance | current roadmap M3 |
| PROJECT-PLATFORM-S02-M3-T04 | member and invitation interaction components belong to the UI milestone | non-blocking for M2 backend acceptance | current roadmap M3 |
| PROJECT-PLATFORM-S02-M4-T04 | legacy project/member mapping runner is outside the member-governance contract | non-blocking; legacy data remains untouched | current roadmap M4 |
| N/A | invitation delivery currently uses authenticated in-app notification by invitation ID; external email delivery is not part of S02 | non-blocking | future notification channel planning |

## Next Steps

- Start PROJECT-PLATFORM-S02-M3 only after this stage gate succeeds.
- Build user-space, member-settings and enterprise-governance UI directly against the M1/M2 API contracts.
- Keep enterprise governance separate from private space membership and content access in all M3 navigation and browser scenarios.
