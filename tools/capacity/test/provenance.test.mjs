import assert from "node:assert/strict";
import test from "node:test";
import {
  CAPACITY_PROVENANCE_SCHEMA_VERSION,
  createCapacityProvenance,
  validateCapacityRunManifest
} from "../src/provenance.mjs";
import { sha256, stableStringify } from "../src/contract.mjs";

const commit = "a".repeat(40);
const generatedAt = "2026-07-25T08:00:00.000Z";
const imageId = `sha256:${"b".repeat(64)}`;

function seedPlan() {
  const unsigned = {
    schemaVersion: "colla.capacity-seed-plan/v1",
    seedId: "s05-provenance",
    fixtureName: "capacity-s05-provenance",
    credentialSource: {
      type: "initialized-user-password-hash",
      username: "admin",
      requiredStatus: "active",
      requiredNotDeleted: true,
      outputFingerprint: "md5-of-stored-password-hash"
    },
    expectedRecordCount: 2458229
  };
  return {
    ...unsigned,
    checksum: sha256(stableStringify(unsigned))
  };
}

function cleanInput(overrides = {}) {
  return {
    repoRoot: "C:\\workspace\\collaPlatform",
    git: { commit, dirty: false },
    sourceCommit: commit,
    stackInstanceNonce: "n".repeat(32),
    preflight: {
      baseline: { criticalFingerprint: "preflight-fingerprint" },
      current: {
        criticalFingerprint: "preflight-fingerprint",
        resourceEligibility: {
          hostFreeMemorySatisfied: true,
          repositoryDiskFreeSatisfied: true,
          tempDiskFreeSatisfied: true,
          dockerDataDiskFreeSatisfied: true,
          clockSynchronizationSatisfied: true
        }
      }
    },
    contract: { schemaVersion: "contract/v1", id: "C1" },
    topology: { schemaVersion: "topology/v1", contractId: "C1" },
    seedPlan: seedPlan(),
    renderedCompose: "services:\r\n  api:\r\n    image: colla-api\r\n",
    requiredImages: ["collaboration", "api"],
    sourceBoundImages: ["collaboration", "api"],
    imageInspect: {
      api: {
        Id: imageId,
        RepoDigests: [`colla-api@sha256:${"c".repeat(64)}`],
        Config: {
          Env: ["PASSWORD=must-not-leak", "TOKEN=must-not-leak"],
          Labels: {
            secret: "must-not-leak",
            "org.opencontainers.image.revision": commit
          }
        }
      },
      collaboration: {
        Id: `sha256:${"d".repeat(64)}`,
        RepoDigests: [],
        Config: {
          Labels: {
            "org.opencontainers.image.revision": commit
          }
        }
      }
    },
    generatedAt,
    ...overrides
  };
}

function blockerCodes(result) {
  return result.blockers.map((blocker) => blocker.code);
}

test("clean immutable inputs produce a stable Pass provenance manifest", async () => {
  const result = await createCapacityProvenance(cleanInput());
  assert.equal(result.schemaVersion, CAPACITY_PROVENANCE_SCHEMA_VERSION);
  assert.equal(result.status, "Pass");
  assert.equal(result.blocked, false);
  assert.deepEqual(result.blockers, []);
  assert.equal(result.git.commit, commit);
  assert.equal(result.git.dirty, false);
  assert.equal(result.sourceCommit, commit);
  assert.equal(result.preflight.drifted, false);
  assert.match(result.contract.digest, /^[0-9a-f]{64}$/);
  assert.match(result.topology.digest, /^[0-9a-f]{64}$/);
  assert.match(result.compose.sha256, /^[0-9a-f]{64}$/);
  assert.equal(result.seedPlan.fingerprint, result.seedPlan.checksum);
  assert.deepEqual(result.images.map((image) => image.name), ["api", "collaboration"]);
  assert.ok(result.images.every((image) => image.revision === commit));
  assert.equal(result.generatedAt, generatedAt);
});

test("dirty git state and SOURCE_COMMIT drift are blocking", async () => {
  const input = cleanInput({
    git: { commit, dirty: true },
    sourceCommit: "e".repeat(40)
  });
  input.imageInspect.api.Config.Labels["org.opencontainers.image.revision"] = "e".repeat(40);
  input.imageInspect.collaboration.Config.Labels["org.opencontainers.image.revision"] = "e".repeat(40);
  const result = await createCapacityProvenance(input);
  assert.equal(result.status, "Blocked");
  assert.equal(result.blocked, true);
  assert.deepEqual(
    blockerCodes(result),
    ["GIT_DIRTY", "SOURCE_COMMIT_DRIFT"]
  );
});

test("preflight drift is blocking", async () => {
  const input = cleanInput();
  input.preflight.current.criticalFingerprint = "changed-fingerprint";
  const result = await createCapacityProvenance(input);
  assert.equal(result.status, "Blocked");
  assert.ok(blockerCodes(result).includes("PREFLIGHT_DRIFT"));
  assert.equal(result.preflight.drifted, true);
});

test("failed preflight resource requirements are blocking", async () => {
  const input = cleanInput();
  input.preflight.current.resourceEligibility.tempDiskFreeSatisfied = false;
  const result = await createCapacityProvenance(input);
  assert.equal(result.status, "Blocked");
  assert.ok(blockerCodes(result).includes("PREFLIGHT_REQUIREMENT_FAILED"));
});

test("missing required image or image hash is blocking", async () => {
  const input = cleanInput();
  delete input.imageInspect.collaboration;
  input.imageInspect.api.Id = "";
  const result = await createCapacityProvenance(input);
  assert.equal(result.status, "Blocked");
  assert.deepEqual(
    blockerCodes(result),
    ["IMAGE_HASH_MISSING", "IMAGE_MISSING"]
  );
});

test("missing or drifting source revision labels are blocking only for source-built images", async () => {
  const input = cleanInput();
  delete input.imageInspect.api.Config.Labels["org.opencontainers.image.revision"];
  input.imageInspect.collaboration.Config.Labels["org.opencontainers.image.revision"] = "e".repeat(40);
  const result = await createCapacityProvenance(input);
  assert.equal(result.status, "Blocked");
  assert.deepEqual(
    blockerCodes(result).filter((code) => code.startsWith("IMAGE_SOURCE_")),
    ["IMAGE_SOURCE_REVISION_DRIFT", "IMAGE_SOURCE_REVISION_MISSING"]
  );

  const externalInput = cleanInput({
    requiredImages: ["postgres"],
    sourceBoundImages: [],
    imageInspect: {
      postgres: {
        Id: imageId,
        RepoDigests: []
      }
    }
  });
  const externalResult = await createCapacityProvenance(externalInput);
  assert.equal(externalResult.status, "Pass");
  assert.equal(externalResult.images[0].revision, null);
});

test("command runner can provide cross-platform git state", async () => {
  const calls = [];
  const input = cleanInput();
  delete input.git;
  input.commandRunner = async (command, args, options) => {
    calls.push({ command, args, cwd: options.cwd });
    return args[0] === "rev-parse"
      ? { exitCode: 0, stdout: `${commit}\n` }
      : { exitCode: 0, stdout: "" };
  };
  const result = await createCapacityProvenance(input);
  assert.equal(result.status, "Pass");
  assert.equal(calls.length, 2);
  assert.ok(calls.every((call) => call.command === "git"));
});

test("secret-bearing inspect and configuration input never reaches output", async () => {
  const input = cleanInput({
    contract: {
      schemaVersion: "contract/v1",
      password: "contract-secret"
    },
    topology: {
      schemaVersion: "topology/v1",
      token: "topology-secret"
    },
    renderedCompose: [
      "services:",
      "  api:",
      "    environment:",
      "      PASSWORD: compose-secret",
      ""
    ].join("\n")
  });
  const serialized = JSON.stringify(await createCapacityProvenance(input));
  assert.doesNotMatch(
    serialized,
    /must-not-leak|contract-secret|topology-secret|compose-secret|PASSWORD=|TOKEN=/i
  );
});

test("stable sorting and hashing make identical inputs deterministic", async () => {
  const firstInput = cleanInput();
  const secondInput = cleanInput({
    requiredImages: ["api", "collaboration"],
    imageInspect: {
      collaboration: firstInput.imageInspect.collaboration,
      api: {
        ...firstInput.imageInspect.api,
        RepoDigests: [...firstInput.imageInspect.api.RepoDigests].reverse()
      }
    }
  });
  const first = await createCapacityProvenance(firstInput);
  const second = await createCapacityProvenance(secondInput);
  assert.deepEqual(second, first);
  assert.equal(second.provenanceFingerprint, first.provenanceFingerprint);
});

test("run manifest validation rejects schema, seed run id, and trusted identity drift", () => {
  const manifest = {
    schemaVersion: "colla.capacity-provenance/v0",
    sourceCommit: commit,
    stack: { instanceNonce: "n".repeat(32) },
    seedExecution: { runId: "" }
  };
  const result = validateCapacityRunManifest(manifest, {
    expectedRunId: "s05-m1-current",
    expectedSourceCommit: "b".repeat(40),
    expectedStackInstanceNonce: "x".repeat(32)
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join("\n"), /schemaVersion must be colla\.capacity-provenance\/v1/);
  assert.match(result.errors.join("\n"), /seedExecution\.runId is missing or invalid/);
  assert.match(result.errors.join("\n"), /expected seed runId/);
  assert.match(result.errors.join("\n"), /expected sourceCommit/);
  assert.match(result.errors.join("\n"), /expected runtime/);
});
