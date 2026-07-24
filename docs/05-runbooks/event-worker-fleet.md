# Event Worker Fleet Runbook

## Scope

This runbook covers the S03 reliable domain-event Worker fleet. It does not move event consumption back into API nodes and it never edits delivery tables directly.

## Fixed production contract

- Services: `worker-a` and `worker-b`, built from the same immutable Server image.
- Runtime role: `worker`; neither service is published through nginx or exposes a host port.
- Default execution: concurrency `4`, queue `16`, claim batch `20`, lease `30s`, heartbeat `10s`.
- Graceful stop: the Worker enters draining, stops claiming, waits up to `25s`, and releases queued work with owner/fencing checks. Compose allows `30s`.
- Flyway remains disabled on both Workers. `maintenance` owns migrations.

## Connection and resource budget

For one Worker:

`worker_pool >= concurrency + 2`

The two reserved connections cover polling/lease maintenance and health/metrics while Handler tasks use at most one connection each. The fleet guard is:

`expected_instances * worker_pool <= postgresql_connection_budget`

Production defaults are `2 * 6 = 12`, within the explicit budget of `100`. Before adding an instance, increase `COLLA_EVENT_WORKER_EXPECTED_INSTANCES` and verify the formula against PostgreSQL `max_connections` after reserving API, gateway, maintenance, observability and operator capacity. Default limits per Worker are 1 CPU and 768 MiB.

## Observe

Readiness is `/actuator/health/readiness`. A Worker is not ready while draining or after a poll failure. Liveness remains process-level.

Prometheus metrics:

- `colla_event_delivery_pending`, `processing`, `expired`, `retries`, `dead_letter`
- `colla_event_delivery_oldest_age_seconds`
- `colla_event_worker_queue_depth`, `active`, `backpressure_total`
- `colla_event_worker_claimed_total`, `completed_total`, `failed_total`, `recovered_total`
- `colla_event_worker_processing_seconds`

Only bounded `handler`, `outcome`, runtime role, instance id and version labels are used. Workspace ids and payload values are excluded.

## Scale and roll

Preview every command:

```bash
pnpm worker:fleet -- --action scale --target 2 --dry-run
```

Scale to two Workers:

```bash
pnpm worker:fleet -- --action scale --target 2
```

Fallback to one Worker:

```bash
pnpm worker:fleet -- --action scale --target 1
```

Rolling replacement:

```bash
pnpm worker:fleet -- --action rollout --target 2
```

Confirm readiness, queue depth and expired leases after each step. Do not roll both instances simultaneously. Rollback uses the previous immutable image and the same schema; never reverse V067/V068 or re-enable event consumption on API.

## Backlog and failure response

1. Check pending count, oldest age, queue depth, processing latency and PostgreSQL saturation.
2. If queue is full, the Worker stops claiming. Correct Handler or database pressure before increasing concurrency.
3. If leases expire, verify heartbeat, task duration and database reachability. Fencing prevents the old owner from completing after takeover.
4. Inspect dead letters through the protected maintenance API. Replay or abandon requires a reason and leaves immutable history and an audit record.
5. During PostgreSQL failure, readiness becomes down and claims stop. Restore PostgreSQL, then verify expired leases are recovered and backlog age converges.

S03 results are operational thresholds for this deployment shape, not a general capacity promise.

## Frozen S03 operating thresholds

The closeout fixture used 24 independent deliveries and a fixed 40 ms Handler delay against one shared PostgreSQL instance. One Worker drained the burst in 1541 ms; two Workers drained it in 755 ms, a 52% improvement. The acceptance floor is a 20% improvement, both instance ids owning work, zero duplicate receipts, and no delivery left pending or processing after the test window. This named fixture proves distribution and recovery behavior only. It is not a production throughput, latency, tenant-count, or infrastructure HA promise.

Use these initial alarms until S05 replaces them with capacity evidence:

| Signal | Warning | Critical / action |
| --- | --- | --- |
| readiness | one failed poll | still down after PostgreSQL is reachable: stop rollout and inspect the pool |
| oldest pending age | above 30 seconds for 5 minutes | above 120 seconds: freeze deploys and run backlog triage |
| dead letter | any new row | inspect immediately; replay only after the cause is repaired |
| expired lease | non-zero for two poll intervals | rising count: stop scale-down and inspect heartbeat/database latency |
| queue depth | at configured capacity for three polls | reduce claim pressure; do not raise concurrency before checking PostgreSQL |
| recovery fixture | slower than 15 seconds | fail the release; investigate claim, Handler, or database regression |

The default lease recovery objective is one lease window plus one recovery scan. With the shipped `30s` lease and `15s` scan ceiling, takeover must occur within 45 seconds. A stale owner completion must always fail regardless of timing.

## PostgreSQL outage and Worker restart

1. Confirm the outage is PostgreSQL-specific; do not replay deliveries while connectivity is unknown.
2. Verify Worker readiness is down and no new claims are issued. Liveness may remain up.
3. Restore PostgreSQL and confirm a successful poll changes readiness to up.
4. Watch expired leases, pending age and completed totals until the backlog converges.
5. If a Worker process was lost after claim, wait for lease expiry and verify another instance owns the incremented fencing token.
6. Restart only one Worker at a time. If recovery does not converge, scale to one known-good Worker with the fleet command and preserve the schema.

## Dead-letter inspect, replay and abandon

Use only the protected maintenance API or its approved operator client:

1. Inspect by workspace, Handler and time window. Record the delivery id, error fingerprint and business fact calibration result.
2. Repair the permanent or poison cause before replay. A replay reason must identify the incident/change and the target Handler.
3. Preview the bounded selection and request limit. Never replay an unbounded workspace result.
4. Submit replay once and verify the audit row, new attempt and unchanged receipts for already completed Handlers.
5. Use abandon only when the business owner accepts that Handler outcome. Abandon also requires a reason and preserves history.

Direct updates or deletes in `domain_event_handler_deliveries`, receipts, replay history or `domain_events` are prohibited. Never lower a fencing token, reset an attempt counter or delete a receipt to force execution.

## Rolling scale-down and fallback

1. Preview the target and connection formula with `--dry-run`.
2. Drain one instance; verify it stops claiming and its active count reaches zero or leases are safely released.
3. Remove that instance and confirm the remaining Worker completes a fresh named batch.
4. For fallback, keep one known-good Worker, the existing V067-V069 schema and API topology unchanged.
5. Do not enable legacy consumption in API or reverse migrations. Return to two Workers only after readiness, expired leases and pending age are stable.

Every scale, replay, abandon and fallback operation must carry an operator identity, reason and incident/change reference. Commands that change state require an explicit target after a dry run; screenshots or copied SQL are not an authorization mechanism.

## Handler operations

The active business handlers are:

- `notification.projection`: calibrate with `GET /api/notifications` as the intended recipient.
- `search.projection`: calibrate with `GET /api/search`; inspect object-level waterlines before replaying an older event.
- `realtime.signal`: inspect pending durable signals and calibrate through each row's `/api/...` path. A pending transport does not mean the business fact failed.

The complete event/type/version and data-disclosure contract is in `docs/01-architecture/event-side-effect-matrix.md`.

### Search rebuild

Normal searches and event Workers never run a full workspace rebuild. Use the protected batch command:

```text
POST /api/admin/search-governance/reindex/batches
objectType=<supported type>
afterId=<optional cursor>
limit=<1..250>
reason=<operator reason>
```

Continue with the returned cursor until `done=true`. Each batch is serialized per workspace/object type, rate limited and audited. The legacy `POST /api/admin/search-governance/reindex` is an explicit compatibility maintenance action only; do not put it in request, Worker or scheduled paths.

If an old event is ignored, compare its aggregate sequence with `search_projection_versions.source_version`. Do not lower or delete the waterline manually. Rebuild the affected object type or replay a newer canonical event.

### Notification and realtime calibration

When a user reports a missing notification:

1. Locate the `notification.created` event and `notification.projection` delivery.
2. Confirm the user's notification preference and business dedupe key.
3. If the delivery completed, query `/api/notifications` as the recipient before inspecting transport.
4. Locate the derived `realtime.signal.requested` event and `realtime.signal` delivery.
5. A row in `realtime_signals` with no `transported_at` is expected in S03. The REST calibration path remains authoritative until S04 supplies cross-node transport.

Never replay the already completed Notification Handler just to regenerate realtime transport. Replay only the failed Handler delivery through the protected maintenance API.
