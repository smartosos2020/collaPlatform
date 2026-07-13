---
title: Browser Smoke Runbook
status: active
updated_at: 2026-07-12
---

# Browser Smoke Runbook

## Purpose

This runbook defines the minimum browser verification expected after frontend or user-flow changes.

Browser smoke verification is required when a work cycle changes:

- React pages or components;
- frontend routing;
- API contracts used by the browser;
- authentication or session behavior;
- WebSocket behavior;
- CSS that can affect layout, scrolling, or responsive behavior.

## Preparation

Start dependencies:

```powershell
docker compose up -d postgres redis minio
```

Start backend:

```powershell
cd server
mvn spring-boot:run
```

Start frontend:

```powershell
pnpm web:dev
```

Default browser target:

```text
http://127.0.0.1:5173
```

## Baseline Smoke Path

1. Open `/login`.
2. Log in with `admin / admin123456`.
3. Confirm the workspace shell renders.
4. Navigate through the workspace left menu to:
   - Workspace
   - IM
   - Projects
   - Knowledge Base
   - Base
   - Approvals
   - Notifications
   - Search
5. Open the avatar menu and use the management entry to enter the separate management console.
6. Confirm the management console shows enterprise overview and management modules, without workspace product menus.
7. Confirm each touched page renders without blank screen, blocking error, or obvious layout break.

## Module-specific Smoke

Use the module path touched by the current work cycle:

| Module | Minimum browser checks |
| --- | --- |
| IM | conversation list scrolls, open a conversation, send a message, message list remains usable |
| Projects and BUG | open project page, create or inspect an issue, change a visible workflow action when in scope |
| Knowledge Base | open a knowledge content page, edit title/content when in scope, verify save state |
| Base | open base/table, edit a record or field when in scope |
| Notifications | open notification list, mark an item read when in scope |
| Search | search a known keyword and open a result when in scope |
| Management console | enter through the avatar menu, open enterprise overview and the touched management module |

## Historical Flow Scripts

M31/M40 cross-module scripts are retained only as historical evidence. They are not a current browser baseline and may reference removed document routes, old schema or obsolete navigation. Do not run them without first rewriting and reviewing them against the current product model.

## Scripted IM Smoke

When only the IM module changes and a narrower check is enough, run the scripted IM smoke after backend and frontend services are already running:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/im-browser-smoke.ps1
```

The script logs in, creates an isolated smoke member and group conversation through local APIs, opens the IM page, sends a message, and verifies the message context menu.

Playwright output directories such as `web/test-results` and `web/playwright-report` are local generated artifacts and must stay ignored by git.

Use the direct script form when custom URLs or headed mode are required:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/im-browser-smoke.ps1 -Headed
```

## Reporting

Record browser smoke results in the current execution report:

- target URL;
- account used;
- pages checked;
- pass/fail result;
- any known residual issue.

If browser smoke is skipped, the final response and execution report must state the reason.
