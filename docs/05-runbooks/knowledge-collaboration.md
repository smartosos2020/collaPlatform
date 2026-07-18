---
title: Knowledge Collaboration Operations Runbook
status: active
updated_at: 2026-07-16
---

# Knowledge Collaboration Operations Runbook

## Runtime Topology

Production Compose routes `/collaboration` through Nginx to
`collaboration-a` and `collaboration-b` using least-connections balancing.
Both Hocuspocus/Yjs nodes use the same Redis room prefix and the same Spring
backend. PostgreSQL is the durable source for Yjs updates and snapshots;
Redis carries room broadcast, awareness and coordination only.

This protects an active room from one collaboration-process restart. It does
not make the single Spring backend, Redis, PostgreSQL or MinIO highly
available.

## Required Configuration

| Variable | Purpose |
| --- | --- |
| `COLLA_COLLABORATION_NODE_ID` | Unique node identity; never reuse one value for two live nodes |
| `COLLA_COLLABORATION_INTERNAL_SECRET` | Authenticates node-to-backend calls and protected metrics |
| `COLLA_COLLABORATION_REDIS_HOST/PORT` | Shared Redis endpoint |
| `COLLA_COLLABORATION_REDIS_PREFIX` | Shared room namespace |
| `COLLA_COLLABORATION_BACKEND_TIMEOUT_MS` | Per-attempt backend timeout |
| `COLLA_COLLABORATION_BACKEND_RETRIES` | Bounded transient retry count |
| `VITE_COLLABORATION_OFFLINE_MAX_UPDATES/BYTES` | Browser offline editing budget |

The backend retains 100 updates behind the latest snapshot and removes
expired collaboration tickets after the configured retention period.

## Health And Metrics

Run these checks from inside the deployment network for each node:

```shell
curl --fail http://collaboration-a:1234/health
curl --fail http://collaboration-a:1234/ready
curl --fail -H "X-Colla-Collaboration-Secret: $COLLA_COLLABORATION_INTERNAL_SECRET" http://collaboration-a:1234/metrics
```

- `/health` proves the process can answer and reports degraded dependencies.
- `/ready` returns HTTP 503 while Redis is degraded, so new traffic is not
  deliberately assigned to a node that cannot broadcast.
- `/metrics` is not published by Nginx and rejects a missing or invalid secret.
- `POST /internal/invalidate` shares the same internal-secret guard; the
  backend calls it after REST content mutations (save/restore/import) so nodes
  drop the stale in-memory room and reload the canonical document from
  PostgreSQL.
- Use `nodeId`, room `spaceId/itemId`, connection count, update watermark,
  pending updates, persistence latency, recoveries and failure counters to
  isolate the affected room and node.

## Failure Matrix

| Failure | Expected behavior | Operator action |
| --- | --- | --- |
| One collaboration node exits | Existing socket disconnects; client reconnects through Nginx and Yjs exchanges missing state | Confirm the other node is ready, restart the failed node, then compare room watermarks |
| Redis is unavailable | Nodes report degraded readiness; accepted local updates remain database-first, but cross-node live broadcast is unavailable | Restore Redis, wait for both clients to become ready, and verify database recovery/watermarks before reopening traffic |
| Spring backend is slow/unavailable | Calls time out after bounded retries; the editor shows offline/recovery state instead of a false save | Restore backend health, inspect persistence failures, reconnect and verify the canonical content |
| Browser loses network | Editing continues only inside the finite update/byte budget; status and recovery export are visible | Reconnect normally or export the `.yjs` recovery copy before exceeding the budget |
| Node restarts mid-room | Room reloads snapshot plus updates from PostgreSQL and rejoins Redis | Verify no duplicate update IDs and that both clients converge |

Do not flush Redis as a data recovery procedure. Redis messages are not the
durable record; recover from PostgreSQL snapshot and update rows.

## Recovery Procedure

1. Record the affected `spaceId`, `itemId`, client time and both node IDs.
2. Check `/health`, `/ready` and protected `/metrics` on both nodes.
3. Confirm Spring `/actuator/health`, PostgreSQL and Redis health.
4. Restore the failed dependency or restart only the failed collaboration
   node; do not delete collaboration rows or snapshots.
5. Reconnect two clients through Nginx and edit the same item from both.
6. Confirm both clients converge, the update watermark advances, pending
   updates return to zero and a snapshot store succeeds.
7. If the browser cannot reconnect, use the editor's recovery export before
   clearing local state.

## Release And Rollback

The release contract requires immutable `SERVER_IMAGE`, `WEB_IMAGE` and
`COLLABORATION_IMAGE` tags built from the same 40-character source revision.
Compose runs the collaboration image twice with distinct node IDs. Use
`pnpm ops:release-check` before rollout.

Rollback must select all three previous images and use the exact confirmation
string:

```shell
pnpm ops:rollback -- --env-file deploy/.env.prod \
  --server-image "$SERVER_IMAGE" \
  --web-image "$WEB_IMAGE" \
  --collaboration-image "$COLLABORATION_IMAGE" \
  --expected-project-name colla-platform-prod \
  --confirmation-text "ROLLBACK:colla-platform-prod:$SERVER_IMAGE:$WEB_IMAGE:$COLLABORATION_IMAGE" \
  --confirm-rollback
```

Use data restore only when collaboration rows or canonical content are
corrupt. A normal node or Redis outage does not require database rollback.

## Known Limits

- Redis pub/sub is transient and is not an update log.
- The browser offline queue is deliberately finite, not an offline-first
  document store.
- Dual collaboration nodes do not remove the single backend, Redis and
  PostgreSQL failure domains.
- Full platform high availability, automatic scaling and Kubernetes remain
  outside this milestone.
