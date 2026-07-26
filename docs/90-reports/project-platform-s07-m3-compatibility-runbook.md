# PROJECT-PLATFORM-S07-M3 Compatibility Runbook

## Authority

- `legacy` / `shadow`: legacy `projects/issues` is read authority; shadow never writes canonical facts.
- `canonical_read`: mapped reads use `project_work_items`; legacy writes require the explicit flag.
- `canonical_write` / `complete`: canonical is the only write authority. Legacy writes return `410 legacy_write_closed` and `Location`.
- Kill switch changes read preference only. It never re-enables legacy writes.

## Known Callers

| Caller | M3 route |
| --- | --- |
| `ProjectController` | mutations pass the shared write boundary |
| `ImController#createIssueFromMessage` | `ProjectService` enforces project cutover |
| `KnowledgeContentCrossModuleService` | `ProjectService` enforces issue cutover |
| `IssuePlatformObjectResolver` | explicit map returns canonical route/deep link after authorization |
| `WorkItemPlatformObjectResolver` | canonical item and space membership only |
| Audit, file, search and notification consumers | use platform resolver/API; no migration private-table reads |

## Signals And Stop Conditions

| Severity | Condition | Action |
| --- | --- | --- |
| P0 | authorization leak, cross-workspace map, or dual business fact | enable read kill switch, pause canonical writes and migration |
| P0 | source/target count or immutable manifest mismatch | pause batch; do not mark unit verified |
| P1 | shadow drift/error or canonical target missing | remain on legacy read and repair map/codec |
| P1 | legacy write attempts remain non-zero | do not enter `complete`; identify caller from request/audit |
| P1 | consumer dead letter or unresolved alias | retain adapter and replay after repair |

Track legacy fallback responses, `project_work_item_shadow_samples` outcomes, `legacy_write_closed`, active-map target drift and `work_item.changed` dead letters without logging business payloads.

## Operations

1. Freeze workspace profile watermark/fingerprint.
2. Keep `legacy` while M4 creates immutable manifests and migrates project units.
3. Enable `shadow`; inspect drift without returning shadow data.
4. Enter `canonical_read` only after unit verification and identity checks.
5. Close legacy writes before `canonical_write`.
6. Use kill switch only for read recovery; after canonical writes, repair forward or use M4 compensation.
7. Enter `complete` only after workspace convergence, zero unknown callers and M5 acceptance.

M3 does not execute data migration or grant production cutover approval. Those belong to M4; browser replacement belongs to M5.
