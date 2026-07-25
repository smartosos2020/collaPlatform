import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  loadCapacityConfig,
  validateContract,
  validateTopology
} from "../src/contract.mjs";
import {
  comparePreflight,
  containsSecret,
  redactSecrets
} from "../src/preflight.mjs";
import {
  createSeedPlan,
  generateApplySql,
  generateCleanupSql,
  generateVerifySql
} from "../src/seed.mjs";
import {
  createEvidenceBundle,
  validateEvidenceState,
  verifyEvidenceBundle
} from "../src/evidence.mjs";

test("frozen C1 contract and topology validate", async () => {
  const { contract, topology } = await loadCapacityConfig();
  const result = validateContract(contract, topology);
  assert.equal(result.ok, true, JSON.stringify(result.errors));
  assert.deepEqual(result.totals, {
    cpu: 20,
    memoryMiB: 28416,
    connections: {
      postgresql: 52,
      redis: 50,
      minio: 30
    }
  });
});

test("critical preflight drift blocks baseline reuse", () => {
  const baseline = {
    criticalFingerprint: "baseline",
    critical: {
      git: { commit: "abc", dirty: false },
      memory: { totalBytes: 34359738368 },
      keyFiles: { "package.json": { sha256: "one" } }
    }
  };
  const current = structuredClone(baseline);
  current.critical.keyFiles["package.json"].sha256 = "two";
  current.criticalFingerprint = "current";
  const result = comparePreflight(baseline, current);
  assert.equal(result.ok, false);
  assert.equal(result.blocked, true);
  assert.equal(result.drifts[0].path, "critical.keyFiles.package.json.sha256");
});

test("topology rejects aggregate connection budget overflow", async () => {
  const { contract, topology } = await loadCapacityConfig();
  const invalid = structuredClone(topology);
  invalid.roles.api.connections.postgresql = 40;
  const result = validateTopology(invalid, contract);
  assert.equal(result.ok, false);
  assert.match(result.errors.map((error) => error.message).join("\n"), /exceeds allocatable budget/);
});

test("topology requires bounded PostgreSQL shared memory", async () => {
  const { contract, topology } = await loadCapacityConfig();
  const missing = structuredClone(topology);
  delete missing.roles.postgresql.runtime.shmSizeMiB;
  const missingResult = validateTopology(missing, contract);
  assert.equal(missingResult.ok, false);
  assert.match(missingResult.errors.map((error) => error.path).join("\n"), /shmSizeMiB/);

  const excessive = structuredClone(topology);
  excessive.roles.postgresql.runtime.shmSizeMiB = 2048;
  const excessiveResult = validateTopology(excessive, contract);
  assert.equal(excessiveResult.ok, false);
  assert.match(excessiveResult.errors.map((error) => error.message).join("\n"), /25%/);
});

test("topology requires a non-negative load-source Docker socket GID", async () => {
  const { contract, topology } = await loadCapacityConfig();
  const missing = structuredClone(topology);
  delete missing.roles["load-source"].runtime.dockerSocketGid;
  const missingResult = validateTopology(missing, contract);
  assert.equal(missingResult.ok, false);
  assert.match(missingResult.errors.map((error) => error.path).join("\n"), /dockerSocketGid/);

  const negative = structuredClone(topology);
  negative.roles["load-source"].runtime.dockerSocketGid = -1;
  const negativeResult = validateTopology(negative, contract);
  assert.equal(negativeResult.ok, false);
  assert.match(negativeResult.errors.map((error) => error.message).join("\n"), /non-negative integer/);
});

test("seed plan is deterministic and SQL is idempotent, resumable, isolated, and named", async () => {
  const { seed } = await loadCapacityConfig();
  const first = createSeedPlan("s05-c1-core", seed);
  const second = createSeedPlan("s05-c1-core", structuredClone(seed));
  assert.deepEqual(first, second);
  assert.equal(first.checksum, second.checksum);
  assert.equal(first.expectedRecordCount, 2458229);

  const applySql = generateApplySql(first);
  assert.match(applySql, /ON CONFLICT \(seed_id, domain, ordinal\) DO UPDATE/);
  assert.match(applySql, /completed_phase = GREATEST/);
  assert.match(applySql, /workspace_id/);
  assert.match(applySql, /projectFixture/);
  assert.match(applySql, /knowledgeItemFixture/);
  assert.match(applySql, /recipientFixture/);
  assert.match(applySql, new RegExp(first.checksum));

  const verifySql = generateVerifySql(first);
  assert.match(verifySql, /workspaceIsolationLeaks/);
  assert.match(verifySql, /count_mismatches/);

  const cleanupSql = generateCleanupSql(first);
  assert.match(cleanupSql, /seed_id = 's05-c1-core'/);
  assert.match(cleanupSql, new RegExp(`checksum = '${first.checksum}'`));
  assert.match(cleanupSql, /fixtureWorkspaces/);
  assert.match(cleanupSql, /'ok', fixture_runs = 0/);
  assert.doesNotMatch(cleanupSql, /DROP SCHEMA|TRUNCATE/i);
});

test("different seed ids have distinct workspace identities and checksums", async () => {
  const { seed } = await loadCapacityConfig();
  const left = createSeedPlan("fixture-left", seed);
  const right = createSeedPlan("fixture-right", seed);
  assert.notEqual(left.checksum, right.checksum);
  assert.equal(
    left.workspaceIds.some((workspace) => right.workspaceIds.some((other) => workspace.id === other.id)),
    false
  );
});

test("ABORTED evidence can never be Pass", async () => {
  const validation = validateEvidenceState(
    { status: "ABORTED" },
    { conclusion: "Pass" },
    []
  );
  assert.equal(validation.ok, false);
  assert.match(validation.errors.join("\n"), /ABORTED/);

  const directory = await mkdtemp(path.join(os.tmpdir(), "colla-capacity-abort-"));
  try {
    await assert.rejects(
      createEvidenceBundle(directory, {
        run: { status: "ABORTED" },
        manifest: {},
        threshold: {},
        raw: [],
        summary: { conclusion: "Pass" },
        errors: []
      }),
      /ABORTED/
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("evidence verification detects tampering", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "colla-capacity-evidence-"));
  try {
    await createEvidenceBundle(directory, {
      run: { status: "COMPLETED", runId: "run-001" },
      manifest: { criticalFingerprint: "abc" },
      threshold: { contractId: "C1" },
      raw: [{ metric: "http.read.latency.p95_ms", value: 250 }],
      summary: { conclusion: "Pass" },
      errors: []
    });
    assert.equal((await verifyEvidenceBundle(directory)).ok, true);
    await writeFile(
      path.join(directory, "summary.json"),
      `${await readFile(path.join(directory, "summary.json"), "utf8")} `,
      "utf8"
    );
    const tampered = await verifyEvidenceBundle(directory);
    assert.equal(tampered.ok, false);
    assert.equal(tampered.tampered, true);
    assert.match(tampered.errors.join("\n"), /checksum mismatch/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("secret-like values are redacted before manifest serialization", () => {
  const redacted = redactSecrets({
    password: "not-for-output",
    endpoint: "postgresql://capacity:secret-value@database/colla",
    nested: { authorization: "Bearer abcdefghijklmnop" }
  });
  assert.equal(redacted.password, "[REDACTED]");
  assert.equal(redacted.nested.authorization, "[REDACTED]");
  assert.doesNotMatch(redacted.endpoint, /secret-value/);
  assert.equal(containsSecret(redacted), false);
});
