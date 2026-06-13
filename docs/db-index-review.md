# DB Index Review

M11 adds explicit indexes for operational queries introduced through M1-M10.

## Covered Paths

- IM message history: `(workspace_id, conversation_id, created_at desc)`.
- Project issue list and assignment filters: `(workspace_id, assignee_id, status, updated_at desc)`.
- Base record table scans: `(workspace_id, table_id, updated_at desc)`.
- Notifications by target object.
- Audit logs by actor, action, target type.
- Approval instances and tasks were indexed in V014 for applicant/status and assignee/status.

## Review Rule

Any new list endpoint must document its dominant filter and sort order before a migration is accepted. Query plans should be sampled once realistic test data exists.
