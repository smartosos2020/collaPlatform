---
title: Data Reset Runbook
status: active
updated_at: 2026-06-16
---

# Data Reset Runbook

## Purpose

This runbook defines when local test data may be reset and what the reset affects.

Data reset is not part of ordinary verification. Run it only when the user explicitly asks for a clean baseline or when a test scenario requires known accounts and empty business data.

## Current Reset Script

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-m12-test-data.ps1
```

Default target:

| Item | Value |
| --- | --- |
| Postgres container | `colla-postgres` |
| Database | `colla_platform` |
| Database user | `colla` |
| Backup directory | `.local-reports/` |

The script creates a SQL backup before cleanup unless `-NoBackup` is passed.

## Retained Accounts

After reset, the default workspace keeps these accounts:

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `admin123456` | admin |
| `m12_alice` | `member123456` | member |
| `m12_bob` | `member123456` | member |

## Cleared Data

The reset clears volatile development data including:

- sessions and devices;
- IM conversations, members, messages, reactions, mentions, and links;
- projects, iterations, issues, comments, attachments, verification logs, and activity logs;
- documents, versions, blocks, comments, permissions, and relations;
- bases, tables, fields, records, views, values, and members;
- approvals, notifications, files, object links, search index documents, audit logs, and domain events.

The script preserves the default workspace, built-in roles, built-in permissions, and the retained accounts.

## Rules

- Do not run reset after every implementation or verification cycle.
- Do not run reset just to make a flaky test pass.
- Do not run reset against non-local data.
- Always mention the reset in the execution report when it is used.

## Restore Backup

Backups are stored as `.local-reports/pre-m12-cleanup-*.sql`.

Manual restore should be done deliberately from a known backup file. Do not automate restore inside the AI work cycle unless the user asks for it.
