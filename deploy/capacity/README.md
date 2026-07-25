# S05 Capacity Environment

This directory defines the disposable, fixed-capacity environment used only by
`PLATFORM-SCALE-S05`. It is an override of `deploy/docker-compose.prod.yml`;
it must use a unique Compose project and must never target the long-lived
`collaplatform` development stack.

## Contract

- Capacity level: `C1-candidate`, validated by `pnpm capacity:contract`.
- Application topology: two API, two Worker, two Event Gateway and two
  collaboration nodes.
- Dependency topology: one PostgreSQL, one Redis and one MinIO instance. These
  are explicit single points and are not an HA commitment.
- The load source is co-located in the same Docker Desktop VM. Published
  results must preserve this limitation.
- PostgreSQL pool demand is validated before startup against the declared
  `100` connection budget, including maintenance and operator reserve.
- Every service has a CPU/memory limit and every JVM/Node role has frozen
  runtime options.

## Lifecycle

Prerequisites are Git, Node.js 22 or newer, Docker Engine with Compose v2, and
enough host resources to pass the checked-in C1 preflight contract. Start from a
clean checkout at the commit that will be measured. Run each line from the
repository root:

```bash
corepack pnpm install --frozen-lockfile
pnpm capacity:stack init
pnpm capacity:stack up --build
pnpm capacity:stack run --confirm --reason "Execute isolated S05 M1 capacity validation"
```

The protected `run` action is the single M1 orchestration command. It validates
the contract and rendered topology, requires every disposable service to report
`running` and `healthy`, builds the explicitly named capacity-runner image, and
creates the deterministic seed plan. It then captures adjacent baseline/current
preflight snapshots and creates a passing immutable provenance manifest before
any database mutation. The seed cycle proves zero named-fixture residue before
the first apply, performs apply/verify, idempotent reapply/verify,
checksum-guarded cleanup with zero-residue verification, and a second clean
apply/verify against the disposable PostgreSQL container. Every seed result is
written as JSON and bound by checksum to `seed-cycle-evidence.json` and
`run-manifest.json`. Finally, the checked-in M1 scenario uses that combined
manifest and the workflow verifies the resulting evidence bundle. Any failed
step stops the workflow.

The generated run id and all host evidence are below
`.local-reports/capacity/runs`. A caller may supply a stable id with
`--run-id s05-m1-local-validation`. The command rejects a dirty checkout,
`SOURCE_COMMIT` drift, preflight drift, missing image hashes, unhealthy
containers, source-built image revision drift, pre-existing registry or
deterministic business fixtures, failed seed verification, altered seed JSON,
and missing evidence.

Inspect the complete command plan without invoking Docker or changing data:

```bash
pnpm capacity:stack run --confirm --reason "Review isolated S05 M1 capacity commands" --run-id s05-m1-dry-run --dry-run
```

`init --force` is rejected while the capacity project has active containers,
because rotating credentials while preserving its volumes makes the run
non-reproducible.

`up` intentionally does not keep `capacity-runner` running. The `run` action
executes it as `docker compose --profile capacity run --rm`. Inside that
container it passes the real checked-in runtime config at
`/workspace/tools/capacity/config/runtime/s05-m1.v1.json` and the generated
manifest at `/evidence/runs/<run-id>/run-manifest.json`.

`deploy/capacity/capacity.env` is a host-side Compose interpolation file, not a
runner env file. The Compose service maps credentials and internal service DNS
URLs into `capacity-runner`; host loopback metric URLs are not passed through.
`stack.mjs` also resolves the host evidence directory to an absolute path before
mounting it at `/evidence`. Do not invoke the two Compose files directly because
that bypasses these guards and mappings.

Always remove this disposable project after validation:

```bash
pnpm capacity:stack down --confirm --reason "S05 capacity validation is complete"
```

Both `run` and destructive lifecycle actions require explicit confirmation and
a specific reason. The M1 run requires the exact project name
`colla-s05-capacity`; `collaplatform` is rejected before any Docker command.
