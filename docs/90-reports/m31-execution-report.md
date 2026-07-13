---
title: M31 Execution Report
status: archived
milestone: M31
updated_at: 2026-06-17
---

# M31 Execution Report

## Scope

- M31-T01 to M31-T08

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| M31-T01 | Completed | `scripts/m31-collab-simulation.ps1` seeds fixed 10-role accounts: `admin`, `pm_chen`, `product_lin`, `design_wu`, `frontend_zhao`, `backend_wang`, `qa_sun`, `ops_liu`, `business_he`, `viewer_tan`. |
| M31-T02 | Completed | Five deterministic projects are seeded: P1 customer portal, P2 mobile login, P3 dashboard Base, P4 approval release, P5 IM reliability. |
| M31-T03 | Completed | The simulation is script-driven and assertion-driven; roles do not generate free-form data. |
| M31-T04 | Completed | Requirement branches cover confirmed, insufficient info, changed, delayed, and canceled paths. |
| M31-T05 | Completed | BUG branches cover valid, duplicate, cannot reproduce, insufficient info, verification failed, verification passed, and permission denied. |
| M31-T06 | Completed | `pnpm sim:m31` resets the shared local database to the fixed M31 dataset, then seeds and verifies repeatable data. |
| M31-T07 | Completed | `pnpm smoke:m31` verifies project, issue, document, Base, IM, search, and permission-denied browser/API paths. |
| M31-T08 | Completed | This report records validation, gaps, and next milestone inputs. |

## Code Changes

- Backend: no business-code change.
- Frontend: added `web/e2e/m31-collab-simulation.spec.ts` for cross-module browser smoke.
- Database: no migration change; seeded deterministic M31 data through scripts.
- Scripts: added `scripts/m31-collab-simulation.ps1`, `scripts/m31-browser-smoke.ps1`; added `sim:m31` and `smoke:m31` root commands; fixed M12 reset table list for `issue_relations`.

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | Updated | Marked M31 tasks complete and shifted active gap focus to M32/M33 document work. |
| `docs/90-reports/m31-execution-report.md` | Created | Required work-cycle execution report. |
| `scripts/README.md` | Updated | Documented M31 simulation and browser smoke commands. |

## Validation

- Data reset: `scripts/reset-m12-test-data.ps1` completed after adding `issue_relations` to the truncate list.
- M31 reset/seed/verify: `powershell -File scripts/m31-collab-simulation.ps1 -Stage all` passed.
- Root command: `pnpm sim:m31 -- -Stage verify` passed.
- Frontend lint: `pnpm web:lint` passed.
- Frontend build: `pnpm web:build` passed; no 500KB main chunk warning.
- Browser smoke: backend and Vite were started locally; `pnpm smoke:m31` passed.

Post-run correction: full backend quality gates create integration-test fixtures in the shared local Postgres database. `pnpm sim:m31` was updated and rerun after this was observed, restoring the local database to exactly 10 active users and 5 projects for the M31 scenario.

## Remaining Gaps

- Chinese full-text search with `simple` tsquery does not reliably match long Chinese phrases such as `验证码按钮卡死`; M31 smoke uses stable object key `M31P2-1`. This should feed a later search-language improvement.
- Document team space/tree is only represented by `documents.parent_id`; there is no polished tree UI, move/sort/archive workflow, or path breadcrumb yet.
- Document embedded Base/table/object blocks are still represented as relations/cards, not first-class embedded views.

## Next Steps

- M32: implement document team space and tree operations.
- M33: implement document block enhancement and Base/object embedding.
- Keep `pnpm sim:m31` and `pnpm smoke:m31` as regression gates before real team trial data is introduced.
