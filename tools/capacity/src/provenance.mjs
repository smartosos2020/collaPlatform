import { spawnSync } from "node:child_process";
import path from "node:path";
import { sha256, stableStringify } from "./contract.mjs";
import { redactSecrets } from "./preflight.mjs";

const SCHEMA_VERSION = "colla.capacity-provenance/v1";
const gitCommitPattern = /^[0-9a-f]{40,64}$/i;
const imageIdPattern = /^(?:sha256:)?[0-9a-f]{64}$/i;
const seedRunIdPattern = /^[a-z0-9](?:[a-z0-9.-]{0,126}[a-z0-9])?$/;
const stackInstanceNoncePattern = /^[A-Za-z0-9_-]{32,128}$/;

function normalizeText(value) {
  return String(value ?? "").replaceAll("\u0000", "").trim();
}

function normalizeCommit(value) {
  const commit = normalizeText(value).toLowerCase();
  return gitCommitPattern.test(commit) ? commit : null;
}

function normalizeSeedRunId(value) {
  const runId = normalizeText(value);
  return seedRunIdPattern.test(runId) ? runId : null;
}

function normalizeStackInstanceNonce(value) {
  const nonce = normalizeText(value);
  return stackInstanceNoncePattern.test(nonce) ? nonce : null;
}

function normalizeCompose(value) {
  const normalized = String(value ?? "").replace(/\r\n?/g, "\n");
  return normalized.length === 0 ? "" : `${normalized.replace(/\n*$/, "")}\n`;
}

function normalizeCommandResult(result) {
  if (typeof result === "string") {
    return { ok: true, stdout: result };
  }
  const exitCode = result?.exitCode ?? result?.status ?? result?.code ?? 0;
  return {
    ok: !result?.error && exitCode === 0,
    stdout: String(result?.stdout ?? result?.value ?? "")
  };
}

function defaultCommandRunner(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    encoding: "utf8",
    timeout: options.timeout ?? 5000,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"]
  });
  return {
    exitCode: result.status,
    stdout: result.stdout ?? "",
    error: result.error
  };
}

async function runCommand(runner, command, args, options) {
  try {
    return normalizeCommandResult(await runner(command, args, options));
  } catch {
    return { ok: false, stdout: "" };
  }
}

async function resolveGitState(options, blockers) {
  if (options.git) {
    const commit = normalizeCommit(options.git.commit);
    const dirty = typeof options.git.dirty === "boolean" ? options.git.dirty : null;
    if (!commit) {
      blockers.push({ code: "GIT_COMMIT_MISSING", path: "git.commit" });
    }
    if (dirty === null) {
      blockers.push({ code: "GIT_DIRTY_STATE_MISSING", path: "git.dirty" });
    }
    return { commit, dirty };
  }

  const runner = options.commandRunner ?? defaultCommandRunner;
  const cwd = path.resolve(options.repoRoot ?? process.cwd());
  const [commitResult, statusResult] = await Promise.all([
    runCommand(runner, "git", ["rev-parse", "HEAD"], { cwd }),
    runCommand(
      runner,
      "git",
      ["status", "--porcelain", "--untracked-files=normal"],
      { cwd }
    )
  ]);
  const commit = commitResult.ok ? normalizeCommit(commitResult.stdout.split(/\r?\n/, 1)[0]) : null;
  const dirty = statusResult.ok ? normalizeText(statusResult.stdout).length > 0 : null;
  if (!commit) {
    blockers.push({ code: "GIT_COMMIT_MISSING", path: "git.commit" });
  }
  if (dirty === null) {
    blockers.push({ code: "GIT_DIRTY_STATE_MISSING", path: "git.dirty" });
  }
  return { commit, dirty };
}

function fingerprintOf(value, preferredKeys = []) {
  for (const key of preferredKeys) {
    const candidate = normalizeText(value?.[key]);
    if (candidate) {
      return candidate;
    }
  }
  if (value?.critical) {
    return sha256(stableStringify(redactSecrets(value.critical)));
  }
  return value && typeof value === "object"
    ? sha256(stableStringify(redactSecrets(value)))
    : null;
}

function resolvePreflight(options, blockers) {
  const current = options.preflight?.current ?? options.preflight;
  const baseline = options.preflight?.baseline ?? options.baselinePreflight;
  const currentFingerprint = fingerprintOf(
    current,
    ["criticalFingerprint", "fingerprint"]
  );
  const baselineFingerprint = normalizeText(options.expectedPreflightFingerprint)
    || fingerprintOf(baseline, ["criticalFingerprint", "fingerprint"]);

  if (!currentFingerprint) {
    blockers.push({ code: "PREFLIGHT_CURRENT_MISSING", path: "preflight.currentFingerprint" });
  }
  if (!baselineFingerprint) {
    blockers.push({ code: "PREFLIGHT_BASELINE_MISSING", path: "preflight.baselineFingerprint" });
  }
  if (currentFingerprint && baselineFingerprint && currentFingerprint !== baselineFingerprint) {
    blockers.push({ code: "PREFLIGHT_DRIFT", path: "preflight.currentFingerprint" });
  }
  const requiredEligibilityFields = [
    "hostFreeMemorySatisfied",
    "repositoryDiskFreeSatisfied",
    "tempDiskFreeSatisfied",
    "dockerDataDiskFreeSatisfied",
    "clockSynchronizationSatisfied"
  ];
  const resourceEligibility = Object.fromEntries(requiredEligibilityFields.map((field) => [
    field,
    current?.resourceEligibility?.[field] === true
  ]));
  for (const field of requiredEligibilityFields) {
    if (current?.resourceEligibility?.[field] !== true) {
      blockers.push({ code: "PREFLIGHT_REQUIREMENT_FAILED", path: `preflight.resourceEligibility.${field}` });
    }
  }
  return {
    baselineFingerprint: baselineFingerprint || null,
    currentFingerprint: currentFingerprint || null,
    drifted: Boolean(
      currentFingerprint
      && baselineFingerprint
      && currentFingerprint !== baselineFingerprint
    ),
    resourceEligibility
  };
}

function digestRequired(value, pathName, code, blockers) {
  if (!value || typeof value !== "object") {
    blockers.push({ code, path: pathName });
    return null;
  }
  return sha256(stableStringify(redactSecrets(value)));
}

function resolveSeedPlan(seedPlan, blockers) {
  const seedId = normalizeText(seedPlan?.seedId);
  const fixtureName = normalizeText(seedPlan?.fixtureName);
  const checksum = normalizeText(seedPlan?.checksum);
  if (!seedId) {
    blockers.push({ code: "SEED_ID_MISSING", path: "seedPlan.seedId" });
  }
  if (!fixtureName) {
    blockers.push({ code: "SEED_FIXTURE_NAME_MISSING", path: "seedPlan.fixtureName" });
  }
  if (!checksum) {
    blockers.push({ code: "SEED_CHECKSUM_MISSING", path: "seedPlan.checksum" });
    return { seedId: seedId || null, fixtureName: fixtureName || null, checksum: null, fingerprint: null };
  }
  const { checksum: ignored, ...unsigned } = seedPlan;
  const fingerprint = sha256(stableStringify(unsigned));
  if (checksum !== fingerprint) {
    blockers.push({ code: "SEED_CHECKSUM_MISMATCH", path: "seedPlan.checksum" });
  }
  return { seedId: seedId || null, fixtureName: fixtureName || null, checksum, fingerprint };
}

function imageMap(imageInspect) {
  if (!imageInspect) {
    return new Map();
  }
  if (!Array.isArray(imageInspect)) {
    return new Map(Object.entries(imageInspect));
  }
  return new Map(imageInspect.flatMap((entry) => {
    const labels = entry?.Config?.Labels ?? {};
    const key = entry?.key
      ?? entry?.service
      ?? entry?.name
      ?? labels["com.docker.compose.service"]
      ?? entry?.RepoTags?.[0];
    return key ? [[key, entry]] : [];
  }));
}

function resolveImageId(inspect) {
  const value = normalizeText(inspect?.Id ?? inspect?.id ?? inspect?.ImageID);
  return imageIdPattern.test(value) ? value.toLowerCase() : null;
}

function resolveRepoDigests(inspect) {
  const candidates = [
    ...(Array.isArray(inspect?.RepoDigests) ? inspect.RepoDigests : []),
    ...(Array.isArray(inspect?.repoDigests) ? inspect.repoDigests : []),
    inspect?.Digest,
    inspect?.digest
  ];
  return [...new Set(candidates.map(normalizeText).filter((value) => (
    /@sha256:[0-9a-f]{64}$/i.test(value) || /^sha256:[0-9a-f]{64}$/i.test(value)
  )))].sort();
}

function resolveImages(requiredImages, imageInspect, sourceBoundImages, sourceCommit, blockers) {
  const required = [...new Set((requiredImages ?? []).map(normalizeText).filter(Boolean))].sort();
  const sourceBound = new Set((sourceBoundImages ?? []).map(normalizeText).filter(Boolean));
  if (required.length === 0) {
    blockers.push({ code: "REQUIRED_IMAGES_MISSING", path: "requiredImages" });
    return [];
  }
  const byName = imageMap(imageInspect);
  return required.map((name) => {
    const inspect = byName.get(name);
    if (!inspect) {
      blockers.push({ code: "IMAGE_MISSING", path: `images.${name}` });
      return { name, id: null, repoDigests: [], fingerprint: null };
    }
    const id = resolveImageId(inspect);
    const repoDigests = resolveRepoDigests(inspect);
    const revision = normalizeCommit(inspect?.Config?.Labels?.["org.opencontainers.image.revision"]);
    if (!id) {
      blockers.push({ code: "IMAGE_HASH_MISSING", path: `images.${name}.id` });
    }
    if (sourceBound.has(name) && !revision) {
      blockers.push({ code: "IMAGE_SOURCE_REVISION_MISSING", path: `images.${name}.revision` });
    } else if (sourceBound.has(name) && sourceCommit && revision !== sourceCommit) {
      blockers.push({ code: "IMAGE_SOURCE_REVISION_DRIFT", path: `images.${name}.revision` });
    }
    return {
      name,
      id,
      repoDigests,
      revision,
      sourceBound: sourceBound.has(name),
      fingerprint: id
        ? sha256(stableStringify({ id, repoDigests, revision }))
        : null
    };
  });
}

function sortBlockers(blockers) {
  return [...new Map(blockers.map((blocker) => (
    [`${blocker.code}:${blocker.path}`, blocker]
  ))).values()].sort((left, right) => (
    left.code.localeCompare(right.code) || left.path.localeCompare(right.path)
  ));
}

const seedCheckContracts = {
  cleanBeforeFirstApply: {
    schemaVersion: "colla.capacity-seed-clean-check/v1",
    evidenceKind: "clean-state",
    cycleStep: "clean-before-first-apply"
  },
  firstInitialization: {
    schemaVersion: "colla.capacity-seed-verification/v1",
    evidenceKind: "verification",
    cycleStep: "first-initialization"
  },
  idempotentReapply: {
    schemaVersion: "colla.capacity-seed-verification/v1",
    evidenceKind: "verification",
    cycleStep: "idempotent-reapply"
  },
  cleanup: {
    schemaVersion: "colla.capacity-seed-cleanup/v1",
    evidenceKind: "cleanup",
    cycleStep: "cleanup"
  },
  secondInitialization: {
    schemaVersion: "colla.capacity-seed-verification/v1",
    evidenceKind: "verification",
    cycleStep: "second-initialization"
  }
};

function validateSeedCheckResult(name, result, seedPlan, runId, errors) {
  const contract = seedCheckContracts[name];
  if (!contract) {
    errors.push(`seedExecution.checks.${name} is not a recognized seed checkpoint`);
    return;
  }
  if (result?.schemaVersion !== contract.schemaVersion) {
    errors.push(`seedExecution.checks.${name}.result.schemaVersion is invalid`);
  }
  if (result?.evidenceKind !== contract.evidenceKind) {
    errors.push(`seedExecution.checks.${name}.result.evidenceKind is invalid`);
  }
  if (result?.runId !== runId || result?.cycleStep !== contract.cycleStep) {
    errors.push(`seedExecution.checks.${name}.result stage identity is invalid`);
  }
  for (const field of ["seedId", "checksum", "fixtureName"]) {
    if (result?.[field] !== seedPlan?.[field]) {
      errors.push(`seedExecution.checks.${name}.result.${field} does not match provenance`);
    }
  }
  if (result?.ok !== true) {
    errors.push(`seedExecution.checks.${name}.result.ok must be true`);
  }
  if (contract.evidenceKind === "clean-state") {
    for (const field of [
      "fixtureRuns",
      "fixturePhases",
      "fixtureRecords",
      "fixtureWorkspaces",
      "conflictingRuns",
      "businessRecords"
    ]) {
      if (result?.[field] !== 0) {
        errors.push(`seedExecution.checks.${name}.result.${field} must be zero`);
      }
    }
  } else if (contract.evidenceKind === "cleanup") {
    for (const field of [
      "fixtureRuns",
      "fixturePhases",
      "fixtureRecords",
      "fixtureWorkspaces",
      "businessRecords"
    ]) {
      if (result?.[field] !== 0) {
        errors.push(`seedExecution.checks.${name}.result.${field} must be zero`);
      }
    }
  } else {
    if (result?.runStateMatches !== true || result?.workspaceIsolationLeaks !== 0) {
      errors.push(`seedExecution.checks.${name}.result verification state is incomplete`);
    }
    for (const field of [
      "countMismatches",
      "registryCountMismatches",
      "relationshipLeaks",
      "supportMismatches",
      "duplicateUsernames"
    ]) {
      if (!Array.isArray(result?.[field]) || result[field].length !== 0) {
        errors.push(`seedExecution.checks.${name}.result.${field} must be an empty array`);
      }
    }
    if (result?.credentialSource?.matchedUsers !== 1
      || result?.credentialSource?.fixtureFingerprintMatches !== true) {
      errors.push(`seedExecution.checks.${name}.result credential proof is incomplete`);
    }
  }
}

export function validateCapacityRunManifest(manifest, options = {}) {
  const errors = [];
  if (manifest?.schemaVersion !== SCHEMA_VERSION) {
    errors.push(`provenance schemaVersion must be ${SCHEMA_VERSION}`);
  }
  if (manifest?.status !== "Pass" || manifest?.blocked === true) {
    errors.push("provenance status is not Pass");
  }
  if (!Array.isArray(manifest?.blockers) || manifest.blockers.length !== 0) {
    errors.push("provenance blockers must be empty");
  }
  if (!gitCommitPattern.test(manifest?.sourceCommit ?? "")
    || !/^[0-9a-f]{64}$/.test(manifest?.contract?.digest ?? "")
    || !/^[0-9a-f]{64}$/.test(manifest?.topology?.digest ?? "")
    || !/^[0-9a-f]{64}$/.test(manifest?.compose?.sha256 ?? "")
    || !/^[0-9a-f]{64}$/.test(manifest?.seedPlan?.checksum ?? "")
    || !/^[0-9a-f]{64}$/.test(manifest?.seedPlan?.fingerprint ?? "")
    || !manifest?.seedPlan?.seedId
    || !manifest?.seedPlan?.fixtureName) {
    errors.push("provenance immutable contract fields are incomplete");
  }
  if (manifest?.git?.dirty !== false || manifest?.git?.commit !== manifest?.sourceCommit) {
    errors.push("provenance Git state does not match sourceCommit");
  }
  const expectedSourceCommit = options.expectedSourceCommit === undefined
    ? null
    : normalizeCommit(options.expectedSourceCommit);
  if (options.expectedSourceCommit !== undefined && !expectedSourceCommit) {
    errors.push("expected sourceCommit is invalid");
  } else if (expectedSourceCommit && manifest?.sourceCommit !== expectedSourceCommit) {
    errors.push("provenance sourceCommit does not match the expected sourceCommit");
  }
  const stackInstanceNonce = normalizeStackInstanceNonce(manifest?.stack?.instanceNonce);
  if (!stackInstanceNonce) {
    errors.push("provenance stack instance nonce is missing or invalid");
  }
  const expectedStackInstanceNonce = options.expectedStackInstanceNonce === undefined
    ? null
    : normalizeStackInstanceNonce(options.expectedStackInstanceNonce);
  if (options.expectedStackInstanceNonce !== undefined && !expectedStackInstanceNonce) {
    errors.push("expected stack instance nonce is invalid");
  } else if (expectedStackInstanceNonce
    && stackInstanceNonce !== expectedStackInstanceNonce) {
    errors.push("provenance stack instance nonce does not match the expected runtime");
  }
  if (manifest?.preflight?.drifted !== false
    || Object.values(manifest?.preflight?.resourceEligibility ?? {}).length !== 5
    || Object.values(manifest.preflight.resourceEligibility).some((value) => value !== true)) {
    errors.push("provenance preflight is not eligible");
  }
  if (!Array.isArray(manifest?.images) || manifest.images.length === 0) {
    errors.push("provenance images are missing");
  }
  for (const image of manifest?.images ?? []) {
    if (!image?.id || !image?.fingerprint) {
      errors.push(`provenance image ${image?.name ?? "unknown"} is not immutable`);
    }
    if (typeof image?.sourceBound !== "boolean") {
      errors.push(`provenance image ${image?.name ?? "unknown"} source binding is missing`);
    }
    if (image?.sourceBound === true && image?.revision !== manifest?.sourceCommit) {
      errors.push(`provenance image ${image?.name ?? "unknown"} revision does not match sourceCommit`);
    }
  }
  const provenanceImmutable = {
    schemaVersion: manifest?.schemaVersion,
    git: manifest?.git,
    sourceCommit: manifest?.sourceCommit,
    preflight: manifest?.preflight,
    contract: manifest?.contract,
    topology: manifest?.topology,
    seedPlan: manifest?.seedPlan,
    compose: manifest?.compose,
    images: manifest?.images,
    stack: manifest?.stack
  };
  const expectedProvenanceFingerprint = sha256(stableStringify(provenanceImmutable));
  if (manifest?.provenanceFingerprint !== expectedProvenanceFingerprint) {
    errors.push("provenanceFingerprint does not match immutable provenance");
  }

  const seedExecution = manifest?.seedExecution;
  if (seedExecution?.schemaVersion !== "colla.capacity-seed-cycle/v1"
    || seedExecution?.status !== "Pass"
    || seedExecution?.blocked === true) {
    errors.push("seedExecution contract is not passing");
  }
  if (seedExecution?.provenanceFingerprint !== manifest?.provenanceFingerprint) {
    errors.push("seedExecution provenanceFingerprint does not match provenance");
  }
  const seedRunId = normalizeSeedRunId(seedExecution?.runId);
  if (!seedRunId) {
    errors.push("seedExecution.runId is missing or invalid");
  }
  const expectedRunId = options.expectedRunId === undefined
    ? null
    : normalizeSeedRunId(options.expectedRunId);
  if (options.expectedRunId !== undefined && !expectedRunId) {
    errors.push("expected seed runId is invalid");
  } else if (expectedRunId && seedRunId !== expectedRunId) {
    errors.push("seedExecution.runId does not match the expected seed runId");
  }
  const checkNames = Object.keys(seedExecution?.checks ?? {}).sort();
  const expectedCheckNames = Object.keys(seedCheckContracts).sort();
  if (stableStringify(checkNames) !== stableStringify(expectedCheckNames)) {
    errors.push("seedExecution does not contain the exact five checkpoint names");
  }
  const evidencePaths = new Set();
  for (const name of expectedCheckNames) {
    const check = seedExecution?.checks?.[name];
    if (!check || typeof check !== "object") continue;
    if (typeof check.path !== "string"
      || check.path.length === 0
      || check.path.includes("..")
      || /^[a-z]:|^\//i.test(check.path)) {
      errors.push(`seedExecution.checks.${name}.path is unsafe`);
    }
    if (evidencePaths.has(check.path)) {
      errors.push(`seedExecution.checks.${name}.path is not unique`);
    }
    evidencePaths.add(check.path);
    if (!/^[0-9a-f]{64}$/.test(check.sha256 ?? "")) {
      errors.push(`seedExecution.checks.${name}.sha256 is invalid`);
    }
    validateSeedCheckResult(name, check.result, manifest?.seedPlan, seedRunId, errors);
    if (options.requireEvidenceFiles === true) {
      const raw = options.evidenceFiles?.[check.path];
      if (raw === undefined) {
        errors.push(`seedExecution.checks.${name} evidence file is missing`);
      } else {
        const rawBuffer = Buffer.isBuffer(raw) ? raw : Buffer.from(raw);
        if (sha256(rawBuffer) !== check.sha256) {
          errors.push(`seedExecution.checks.${name} evidence hash does not match`);
        }
        try {
          const parsed = JSON.parse(rawBuffer.toString("utf8"));
          if (stableStringify(parsed) !== stableStringify(check.result)) {
            errors.push(`seedExecution.checks.${name} evidence content does not match`);
          }
        } catch {
          errors.push(`seedExecution.checks.${name} evidence is not JSON`);
        }
      }
    }
  }
  const seedImmutable = {
    schemaVersion: seedExecution?.schemaVersion,
    runId: seedExecution?.runId,
    provenanceFingerprint: seedExecution?.provenanceFingerprint,
    checks: seedExecution?.checks
  };
  const expectedSeedFingerprint = sha256(stableStringify(seedImmutable));
  if (seedExecution?.seedExecutionFingerprint !== expectedSeedFingerprint) {
    errors.push("seedExecutionFingerprint does not match seed evidence");
  }
  return { ok: errors.length === 0, errors };
}

export async function createCapacityProvenance(options = {}) {
  const blockers = [];
  const git = await resolveGitState(options, blockers);
  const sourceCommit = normalizeCommit(options.sourceCommit ?? process.env.SOURCE_COMMIT);
  if (!sourceCommit) {
    blockers.push({ code: "SOURCE_COMMIT_MISSING", path: "sourceCommit" });
  }
  if (git.dirty === true) {
    blockers.push({ code: "GIT_DIRTY", path: "git.dirty" });
  }
  if (git.commit && sourceCommit && git.commit !== sourceCommit) {
    blockers.push({ code: "SOURCE_COMMIT_DRIFT", path: "sourceCommit" });
  }
  const stackInstanceNonce = normalizeStackInstanceNonce(
    options.stackInstanceNonce ?? process.env.CAPACITY_STACK_INSTANCE_NONCE
  );
  if (!stackInstanceNonce) {
    blockers.push({ code: "STACK_INSTANCE_NONCE_MISSING", path: "stack.instanceNonce" });
  }

  const preflight = resolvePreflight(options, blockers);
  const contractDigest = digestRequired(
    options.contract,
    "contract",
    "CONTRACT_MISSING",
    blockers
  );
  const topologyDigest = digestRequired(
    options.topology,
    "topology",
    "TOPOLOGY_MISSING",
    blockers
  );
  const seedPlan = resolveSeedPlan(options.seedPlan, blockers);
  const renderedCompose = normalizeCompose(options.renderedCompose);
  const composeSha256 = renderedCompose ? sha256(renderedCompose) : null;
  if (!composeSha256) {
    blockers.push({ code: "COMPOSE_HASH_MISSING", path: "compose.sha256" });
  }
  const images = resolveImages(
    options.requiredImages,
    options.imageInspect,
    options.sourceBoundImages,
    sourceCommit,
    blockers
  );
  const sortedBlockers = sortBlockers(blockers);
  const generatedAt = options.generatedAt
    ? new Date(options.generatedAt).toISOString()
    : new Date().toISOString();

  const immutable = {
    schemaVersion: SCHEMA_VERSION,
    git,
    sourceCommit,
    preflight,
    contract: { digest: contractDigest },
    topology: { digest: topologyDigest },
    seedPlan,
    compose: { sha256: composeSha256 },
    images,
    stack: { instanceNonce: stackInstanceNonce }
  };
  const result = {
    ...immutable,
    generatedAt,
    status: sortedBlockers.length === 0 ? "Pass" : "Blocked",
    blocked: sortedBlockers.length > 0,
    blockers: sortedBlockers,
    provenanceFingerprint: sha256(stableStringify(immutable))
  };
  return redactSecrets(result);
}

export {
  SCHEMA_VERSION as CAPACITY_PROVENANCE_SCHEMA_VERSION,
  seedRunIdPattern as CAPACITY_SEED_RUN_ID_PATTERN,
  stackInstanceNoncePattern as CAPACITY_STACK_INSTANCE_NONCE_PATTERN
};
