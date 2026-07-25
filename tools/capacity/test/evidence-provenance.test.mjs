import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { sha256, stableStringify } from "../src/contract.mjs";
import { createEvidenceBundle, verifyEvidenceBundle } from "../src/evidence.mjs";

const sourceCommit = "a".repeat(40);
const stackInstanceNonce = "n".repeat(32);

function protectedFixture(seedRunId = "s05-m1-evidence-original") {
  const seedIdentity = {
    seedId: "s05-c1",
    checksum: "c".repeat(64),
    fixtureName: "capacity-s05-c1"
  };
  const verification = (cycleStep) => ({
    schemaVersion: "colla.capacity-seed-verification/v1",
    evidenceKind: "verification",
    runId: seedRunId,
    cycleStep,
    ...seedIdentity,
    ok: true,
    runStateMatches: true,
    workspaceIsolationLeaks: 0,
    countMismatches: [],
    registryCountMismatches: [],
    relationshipLeaks: [],
    supportMismatches: [],
    duplicateUsernames: [],
    credentialSource: {
      matchedUsers: 1,
      fixtureFingerprintMatches: true
    }
  });
  const results = {
    cleanBeforeFirstApply: {
      schemaVersion: "colla.capacity-seed-clean-check/v1",
      evidenceKind: "clean-state",
      runId: seedRunId,
      cycleStep: "clean-before-first-apply",
      ...seedIdentity,
      ok: true,
      fixtureRuns: 0,
      fixturePhases: 0,
      fixtureRecords: 0,
      fixtureWorkspaces: 0,
      conflictingRuns: 0,
      businessRecords: 0
    },
    firstInitialization: verification("first-initialization"),
    idempotentReapply: verification("idempotent-reapply"),
    cleanup: {
      schemaVersion: "colla.capacity-seed-cleanup/v1",
      evidenceKind: "cleanup",
      runId: seedRunId,
      cycleStep: "cleanup",
      ...seedIdentity,
      ok: true,
      fixtureRuns: 0,
      fixturePhases: 0,
      fixtureRecords: 0,
      fixtureWorkspaces: 0,
      businessRecords: 0
    },
    secondInitialization: verification("second-initialization")
  };
  const checks = Object.fromEntries(Object.entries(results).map(([name, result]) => {
    const raw = Buffer.from(`${JSON.stringify(result)}\n`);
    return [name, {
      path: `seed-${name}.json`,
      sha256: sha256(raw),
      result,
      raw
    }];
  }));
  const provenanceImmutable = {
    schemaVersion: "colla.capacity-provenance/v1",
    git: { commit: sourceCommit, dirty: false },
    sourceCommit,
    preflight: {
      drifted: false,
      resourceEligibility: {
        hostFreeMemorySatisfied: true,
        repositoryDiskFreeSatisfied: true,
        tempDiskFreeSatisfied: true,
        dockerDataDiskFreeSatisfied: true,
        clockSynchronizationSatisfied: true
      }
    },
    contract: { digest: "b".repeat(64) },
    topology: { digest: "d".repeat(64) },
    seedPlan: { ...seedIdentity, fingerprint: "e".repeat(64) },
    compose: { sha256: "f".repeat(64) },
    images: [{
      name: "api-a",
      id: `sha256:${"1".repeat(64)}`,
      revision: sourceCommit,
      sourceBound: true,
      fingerprint: "2".repeat(64)
    }],
    stack: { instanceNonce: stackInstanceNonce }
  };
  const provenanceFingerprint = sha256(stableStringify(provenanceImmutable));
  const normalizedChecks = Object.fromEntries(Object.entries(checks).map(([name, check]) => [
    name,
    { path: check.path, sha256: check.sha256, result: check.result }
  ]));
  const seedImmutable = {
    schemaVersion: "colla.capacity-seed-cycle/v1",
    runId: seedRunId,
    provenanceFingerprint,
    checks: normalizedChecks
  };
  const seedExecution = {
    ...seedImmutable,
    status: "Pass",
    blocked: false,
    seedExecutionFingerprint: sha256(stableStringify(seedImmutable))
  };
  const provenance = {
    ...provenanceImmutable,
    status: "Pass",
    blocked: false,
    blockers: [],
    provenanceFingerprint,
    seedExecution
  };
  const identity = {
    seedRunId,
    sourceCommit,
    stackInstanceNonce,
    provenanceFingerprint,
    seedExecutionFingerprint: seedExecution.seedExecutionFingerprint
  };
  const checkpoints = Object.fromEntries(Object.entries(checks).map(([name, check]) => [
    name,
    {
      sourcePath: check.path,
      bundlePath: `provenance/checkpoints/${name}.json`,
      sha256: check.sha256
    }
  ]));
  const binding = {
    schemaVersion: "colla.capacity-scenario-provenance-binding/v1",
    required: true,
    ...identity,
    identityDigest: sha256(stableStringify(identity)),
    checkpoints
  };
  return {
    input: {
      run: {
        schemaVersion: "colla.capacity-scenario-run/v1",
        runId: "scenario-current",
        status: "COMPLETED",
        provenanceBindingDigest: binding.identityDigest
      },
      manifest: {
        schemaVersion: "colla.capacity-scenario-manifest/v1",
        runId: "scenario-current",
        provenanceBinding: binding,
        provenance
      },
      threshold: {},
      raw: [],
      summary: { conclusion: "Pass" },
      errors: [],
      attachments: Object.fromEntries(Object.entries(checks).map(([name, check]) => [
        `provenance/checkpoints/${name}.json`,
        check.raw
      ]))
    },
    expected: {
      expectedSeedRunId: seedRunId,
      expectedSourceCommit: sourceCommit,
      expectedStackInstanceNonce: stackInstanceNonce
    }
  };
}

async function rewriteChecksums(directory) {
  const checksumsPath = path.join(directory, "checksums.json");
  const checksums = JSON.parse(await readFile(checksumsPath, "utf8"));
  for (const relative of Object.keys(checksums.files)) {
    checksums.files[relative] = sha256(await readFile(path.join(directory, relative)));
  }
  checksums.bundleDigest = sha256(stableStringify(checksums.files));
  await writeFile(checksumsPath, `${JSON.stringify(checksums, null, 2)}\n`);
}

test("protected evidence revalidates provenance checkpoints after checksums are rewritten", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "colla-capacity-evidence-provenance-"));
  const fixture = protectedFixture();
  try {
    await createEvidenceBundle(directory, fixture.input);
    assert.deepEqual(await verifyEvidenceBundle(directory, fixture.expected), {
      ok: true,
      tampered: false,
      errors: []
    });

    const replay = await verifyEvidenceBundle(directory, {
      ...fixture.expected,
      expectedSeedRunId: "s05-m1-evidence-current"
    });
    assert.equal(replay.ok, false);
    assert.equal(replay.tampered, true);
    assert.match(replay.errors.join("\n"), /expected seed runId/);

    const manifestPath = path.join(directory, "manifest.json");
    const originalManifest = JSON.parse(await readFile(manifestPath, "utf8"));
    const downgraded = structuredClone(originalManifest);
    downgraded.provenanceBinding.required = false;
    await writeFile(manifestPath, `${JSON.stringify(downgraded, null, 2)}\n`);
    await rewriteChecksums(directory);
    const downgradeResult = await verifyEvidenceBundle(directory, fixture.expected);
    assert.equal(downgradeResult.ok, false);
    assert.match(downgradeResult.errors.join("\n"), /cannot disable provenance binding/);

    const manifest = structuredClone(originalManifest);
    manifest.provenance.seedExecution.checks.cleanup.result.fixtureRuns = 1;
    await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
    await rewriteChecksums(directory);

    const tampered = await verifyEvidenceBundle(directory, fixture.expected);
    assert.equal(tampered.ok, false);
    assert.equal(tampered.tampered, true);
    assert.match(
      tampered.errors.join("\n"),
      /fixtureRuns must be zero|seedExecutionFingerprint|evidence content does not match/
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("credential proof preservation cannot collide with ordinary evidence strings", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "colla-capacity-evidence-marker-"));
  const fixture = protectedFixture();
  fixture.input.summary.note = "__COLLA_SAFE_PROOF_0__";
  fixture.input.manifest.provenance.audit = {
    credentialSource: {
      matchedUsers: 1,
      fixtureFingerprintMatches: true
    },
    password: "must-not-be-written"
  };
  try {
    await createEvidenceBundle(directory, fixture.input);
    const manifest = JSON.parse(await readFile(path.join(directory, "manifest.json"), "utf8"));
    const summary = JSON.parse(await readFile(path.join(directory, "summary.json"), "utf8"));
    assert.equal(summary.note, "__COLLA_SAFE_PROOF_0__");
    assert.deepEqual(manifest.provenance.audit.credentialSource, {
      matchedUsers: 1,
      fixtureFingerprintMatches: true
    });
    assert.equal(manifest.provenance.audit.password, "[REDACTED]");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
