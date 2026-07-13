# Colla Desktop Shell

M38 keeps Colla Web-first and treats the desktop shell as a thin wrapper around the existing Web app.

## Decision

- Short term: use a minimal Electron-compatible shell for local validation because it can load the existing Vite/Web deployment with little product risk.
- Later hardening: evaluate Tauri when native packaging, auto-update, OS integration, and smaller footprint become release blockers.
- The shell must not fork product behavior. Authentication, routing, permissions, device sessions, and PWA fallback remain owned by `web` and `server`.

## Local Smoke

1. Start the normal Web and API services.
2. Launch the shell with an Electron runtime from this folder.
3. Confirm login, global navigation, `/im`, `/projects`, `/docs`, and `/bases` load through `http://localhost:5173`.

This folder is intentionally excluded from the main build until M40 decides whether desktop packaging is in the trial scope.
