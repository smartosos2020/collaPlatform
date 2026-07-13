---
title: Data Reset Runbook
status: archived
updated_at: 2026-06-18
---

# Data Reset Runbook

> 历史说明：该手册对应 M31 固定仿真数据。V044-V047 完成知识库物理模型迁移后，不再把它作为当前数据重置契约；执行脚本前必须重新审计其 SQL 与当前 schema。

## Purpose

This runbook defines when local test data may be reset and what the reset affects.

Data reset is not part of ordinary verification. Run it only when the user explicitly asks for a clean baseline, explicitly asks to restore M31 data, or explicitly asks to execute an M31 regression scenario. Do not reset automatically just because backend gates have created integration-test fixtures in the shared local database.

## Current Reset Script

Command:

```powershell
pnpm data:reset
```

Equivalent direct script call:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/m31-collab-simulation.ps1
```

Default target:

| Item | Value |
| --- | --- |
| Postgres container | `colla-postgres` |
| Database | `colla_platform` |
| Database user | `colla` |
| Local report directory | `.local-reports/` |

The script creates a SQL backup under `.local-reports/pre-m31-reset-*.sql` unless `-NoBackup` is passed. It then resets shared local business data to the deterministic M31 scenario, seeds the data, verifies it, and writes a local summary under `.local-reports/`.

## Retained Accounts

After reset, the default workspace has exactly these active accounts:

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `admin123456` | admin |
| `pm_chen` | `member123456` | member |
| `product_lin` | `member123456` | member |
| `design_wu` | `member123456` | member |
| `frontend_zhao` | `member123456` | member |
| `backend_wang` | `member123456` | member |
| `qa_sun` | `member123456` | member |
| `ops_liu` | `member123456` | member |
| `business_he` | `member123456` | member |
| `viewer_tan` | `member123456` | member |

The reset also creates exactly five active projects: `M31P1` to `M31P5`.

## Cleared Data

The reset clears volatile development data including:

- sessions and devices;
- IM conversations, members, messages, reactions, mentions, and links;
- projects, iterations, issues, comments, attachments, verification logs, and activity logs;
- documents, versions, blocks, comments, permissions, and relations;
- bases, tables, fields, records, views, values, and members;
- approvals, notifications, files, object links, search index documents, audit logs, and domain events.

The script preserves the default workspace, built-in roles, and built-in permissions. It removes non-admin users in the default workspace, then recreates the fixed M31 role team.

## Rules

- Do not run reset after every implementation or verification cycle.
- Do not run reset just to make a flaky test pass.
- Do not run reset against non-local data.
- Always mention the reset in the execution report when it is used.
- `pnpm verify` and `pnpm verify:full` use Testcontainers PostgreSQL for backend integration tests and should not pollute the shared local M31 database.
- Rerun `pnpm data:reset` only when the user explicitly asks for the browser to show the clean M31 scenario or asks to restore the M31 baseline.
- If M31 seed or verify fails, the script writes `m31-collab-simulation-failed-*.md` under `.local-reports/` before exiting.

## Flow Regression

M31 is the explicit cross-module process regression baseline:

```powershell
pnpm smoke:m31
```

Run it after backend and frontend services are running only when the user explicitly requests M31 regression. It verifies project, issue, document, Base, IM, search, and permission-denied paths against the M31 dataset.

## Legacy M12 Reset

The old M12 reset script is retained only to reproduce archived M12 reports:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-m12-test-data.ps1
```

Do not use it as the default manual-test or AI regression baseline.

## Restore Backup

Backups are stored as `.local-reports/pre-m31-reset-*.sql`.

Manual restore should be done deliberately from a known backup file. Do not automate restore inside the AI work cycle unless the user asks for it.
