import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { CONCLUSIONS, sha256, stableStringify } from "./contract.mjs";
import { containsSecret, redactSecrets } from "./preflight.mjs";
import { validateCapacityRunManifest } from "./provenance.mjs";

const baseRequiredFiles = Object.freeze([
  "run.json",
  "manifest.json",
  "threshold.json",
  "raw/metrics.jsonl",
  "summary.json",
  "errors.json"
]);

const SCENARIO_MANIFEST_SCHEMA = "colla.capacity-scenario-manifest/v1";
const PROVENANCE_BINDING_SCHEMA = "colla.capacity-scenario-provenance-binding/v1";
const checkpointNames = Object.freeze([
  "cleanBeforeFirstApply",
  "firstInitialization",
  "idempotentReapply",
  "cleanup",
  "secondInitialization"
]);

function normalizeJson(value, fallback) {
  return value === undefined ? fallback : value;
}

function serializeRaw(raw) {
  if (typeof raw === "string") {
    return raw.endsWith("\n") ? raw : `${raw}\n`;
  }
  if (Array.isArray(raw)) {
    return `${raw.map((entry) => JSON.stringify(redactSecrets(entry))).join("\n")}\n`;
  }
  return `${JSON.stringify(redactSecrets(normalizeJson(raw, {})))}\n`;
}

async function writeExclusive(file, content) {
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, content, { encoding: "utf8", flag: "wx" });
}

async function fileDigest(file) {
  const content = await readFile(file);
  return createHash("sha256").update(content).digest("hex");
}

function isSafeRelativePath(relative) {
  if (typeof relative !== "string" || relative.length === 0) return false;
  const normalized = relative.replaceAll("\\", "/");
  return normalized === relative
    && !normalized.includes("\u0000")
    && !normalized.split("/").includes("..")
    && !path.isAbsolute(normalized)
    && !/^[a-z]:/i.test(normalized);
}

function isSafeCredentialProof(key, value) {
  return key === "credentialSource"
    && value && typeof value === "object" && !Array.isArray(value)
    && stableStringify(Object.keys(value).sort()) === stableStringify([
      "fixtureFingerprintMatches",
      "matchedUsers"
    ])
    && Number.isInteger(value.matchedUsers)
    && typeof value.fixtureFingerprintMatches === "boolean";
}

function isSensitiveEvidenceKey(key) {
  return redactSecrets("__COLLA_SENSITIVITY_PROBE__", key) === "[REDACTED]";
}

function redactEvidenceDocument(value, key = "") {
  if (isSafeCredentialProof(key, value)) return structuredClone(value);
  if (isSensitiveEvidenceKey(key)) return "[REDACTED]";
  if (Array.isArray(value)) {
    return value.map((entry) => redactEvidenceDocument(entry));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([entryKey, nested]) => (
      [entryKey, redactEvidenceDocument(nested, entryKey)]
    )));
  }
  return redactSecrets(value, key);
}

function containsEvidenceSecret(value, key = "") {
  if (isSafeCredentialProof(key, value)) return false;
  if (isSensitiveEvidenceKey(key)) {
    return value !== undefined && value !== null && value !== "[REDACTED]";
  }
  if (Array.isArray(value)) {
    return value.some((entry) => containsEvidenceSecret(entry));
  }
  if (value && typeof value === "object") {
    return Object.entries(value).some(([entryKey, nested]) => (
      containsEvidenceSecret(nested, entryKey)
    ));
  }
  return containsSecret(value, key);
}

function normalizeAttachments(input) {
  const attachments = input ?? {};
  if (!attachments || typeof attachments !== "object" || Array.isArray(attachments)) {
    throw new Error("evidence attachments must be an object");
  }
  const normalized = {};
  for (const [relative, value] of Object.entries(attachments)) {
    if (!isSafeRelativePath(relative) || baseRequiredFiles.includes(relative)
      || relative === "checksums.json") {
      throw new Error(`unsafe evidence attachment path: ${relative}`);
    }
    const content = Buffer.isBuffer(value) ? value : Buffer.from(String(value), "utf8");
    let parsed;
    try {
      parsed = JSON.parse(content.toString("utf8"));
    } catch {
      throw new Error(`evidence attachment must be JSON: ${relative}`);
    }
    if (containsEvidenceSecret(parsed, relative)) {
      throw new Error(`evidence attachment contains sensitive material: ${relative}`);
    }
    normalized[relative] = content;
  }
  return normalized;
}

function declaredCheckpointFiles(manifest, findings) {
  if (manifest?.schemaVersion !== SCENARIO_MANIFEST_SCHEMA
    || manifest?.provenanceBinding?.required !== true) {
    return [];
  }
  const binding = manifest.provenanceBinding;
  if (binding.schemaVersion !== PROVENANCE_BINDING_SCHEMA) {
    findings.push("manifest.json: invalid provenance binding schema");
    return [];
  }
  const names = Object.keys(binding.checkpoints ?? {}).sort();
  if (stableStringify(names) !== stableStringify([...checkpointNames].sort())) {
    findings.push("manifest.json: provenance binding must declare the exact five checkpoints");
    return [];
  }
  const bundlePaths = [];
  const seen = new Set();
  for (const name of checkpointNames) {
    const descriptor = binding.checkpoints[name];
    if (!isSafeRelativePath(descriptor?.sourcePath)
      || !isSafeRelativePath(descriptor?.bundlePath)
      || !descriptor.bundlePath.startsWith("provenance/checkpoints/")
      || !/^[0-9a-f]{64}$/.test(descriptor?.sha256 ?? "")) {
      findings.push(`manifest.json: invalid provenance checkpoint descriptor ${name}`);
      continue;
    }
    if (seen.has(descriptor.bundlePath)) {
      findings.push(`manifest.json: duplicate provenance checkpoint path ${descriptor.bundlePath}`);
      continue;
    }
    seen.add(descriptor.bundlePath);
    bundlePaths.push(descriptor.bundlePath);
  }
  return bundlePaths;
}

function provenanceBindingIdentity(binding) {
  return {
    seedRunId: binding?.seedRunId,
    sourceCommit: binding?.sourceCommit,
    stackInstanceNonce: binding?.stackInstanceNonce,
    provenanceFingerprint: binding?.provenanceFingerprint,
    seedExecutionFingerprint: binding?.seedExecutionFingerprint
  };
}

async function validateProtectedScenarioEvidence(root, manifest, run, findings, options) {
  const externallyBound = [
    options.expectedSeedRunId,
    options.expectedSourceCommit,
    options.expectedStackInstanceNonce
  ].some((value) => value !== undefined);
  if (!manifest || manifest.schemaVersion !== SCENARIO_MANIFEST_SCHEMA) {
    if (externallyBound) {
      findings.push(`manifest.json: schemaVersion must be ${SCENARIO_MANIFEST_SCHEMA}`);
    }
    return;
  }
  const binding = manifest.provenanceBinding;
  if (binding?.required !== true) {
    if (externallyBound) {
      findings.push("manifest.json: protected evidence cannot disable provenance binding");
    }
    return;
  }
  const identity = provenanceBindingIdentity(binding);
  if (binding.schemaVersion !== PROVENANCE_BINDING_SCHEMA
    || binding.identityDigest !== sha256(stableStringify(identity))) {
    findings.push("manifest.json: provenance binding identity is invalid");
  }
  if (run?.provenanceBindingDigest !== binding?.identityDigest) {
    findings.push("run.json: provenance binding digest does not match manifest.json");
  }
  for (const [name, value] of [
    ["expectedSeedRunId", options.expectedSeedRunId],
    ["expectedSourceCommit", options.expectedSourceCommit],
    ["expectedStackInstanceNonce", options.expectedStackInstanceNonce]
  ]) {
    if (typeof value !== "string" || value.length === 0) {
      findings.push(`protected evidence verification requires ${name}`);
    }
  }

  const provenance = manifest.provenance;
  if (binding.seedRunId !== provenance?.seedExecution?.runId
    || binding.sourceCommit !== provenance?.sourceCommit
    || binding.stackInstanceNonce !== provenance?.stack?.instanceNonce
    || binding.provenanceFingerprint !== provenance?.provenanceFingerprint
    || binding.seedExecutionFingerprint !== provenance?.seedExecution?.seedExecutionFingerprint) {
    findings.push("manifest.json: provenance binding does not match the embedded run manifest");
  }

  const evidenceFiles = {};
  for (const name of checkpointNames) {
    const descriptor = binding.checkpoints?.[name];
    if (!isSafeRelativePath(descriptor?.sourcePath)
      || !isSafeRelativePath(descriptor?.bundlePath)) {
      continue;
    }
    try {
      const raw = await readFile(path.join(root, descriptor.bundlePath));
      evidenceFiles[descriptor.sourcePath] = raw;
      if (sha256(raw) !== descriptor.sha256) {
        findings.push(`${descriptor.bundlePath}: provenance checkpoint hash mismatch`);
      }
      const expectedCheck = provenance?.seedExecution?.checks?.[name];
      if (descriptor.sourcePath !== expectedCheck?.path
        || descriptor.sha256 !== expectedCheck?.sha256) {
        findings.push(`manifest.json: provenance checkpoint ${name} does not match the run manifest`);
      }
    } catch (error) {
      findings.push(`${descriptor.bundlePath}: ${error.code === "ENOENT" ? "missing" : "unreadable"}`);
    }
  }

  const validation = validateCapacityRunManifest(provenance, {
    evidenceFiles,
    requireEvidenceFiles: true,
    expectedRunId: options.expectedSeedRunId,
    expectedSourceCommit: options.expectedSourceCommit,
    expectedStackInstanceNonce: options.expectedStackInstanceNonce
  });
  findings.push(...validation.errors.map((error) => `manifest.json provenance: ${error}`));
}

function stateErrors(run, summary, errors) {
  const findings = [];
  const state = run?.status ?? run?.state;
  if (!["RUNNING", "COMPLETED", "ABORTED", "FAILED"].includes(state)) {
    findings.push("run status must be RUNNING, COMPLETED, ABORTED, or FAILED");
  }
  if (!CONCLUSIONS.includes(summary?.conclusion)) {
    findings.push(`summary conclusion must be one of ${CONCLUSIONS.join(", ")}`);
  }
  if (state === "ABORTED" && summary?.conclusion === "Pass") {
    findings.push("ABORTED run can never have a Pass conclusion");
  }
  if (state !== "COMPLETED" && summary?.conclusion === "Pass") {
    findings.push("only a COMPLETED run can have a Pass conclusion");
  }
  if (Array.isArray(errors) && errors.length > 0 && summary?.conclusion === "Pass") {
    findings.push("a run with recorded errors cannot have a Pass conclusion");
  }
  return findings;
}

export function validateEvidenceState(run, summary, errors = []) {
  const findings = stateErrors(run, summary, errors);
  return { ok: findings.length === 0, errors: findings };
}

export async function createEvidenceBundle(directory, input) {
  const root = path.resolve(directory);
  const run = redactSecrets(input?.run ?? {});
  const manifest = redactEvidenceDocument(input?.manifest ?? {});
  const threshold = redactSecrets(input?.threshold ?? input?.thresholds ?? {});
  const summary = redactSecrets(input?.summary ?? {});
  const errors = redactSecrets(input?.errors ?? []);
  const raw = serializeRaw(input?.raw);
  const attachments = normalizeAttachments(input?.attachments);

  const stateValidation = validateEvidenceState(run, summary, errors);
  if (!stateValidation.ok) {
    throw new Error(`invalid evidence state: ${stateValidation.errors.join("; ")}`);
  }
  const values = { run, manifest, threshold, summary, errors, raw };
  if (Object.entries(values).some(([key, value]) => containsEvidenceSecret(value, key))) {
    throw new Error("evidence input contains sensitive material after redaction");
  }

  await mkdir(root, { recursive: true });
  await Promise.all([
    writeExclusive(path.join(root, "run.json"), `${JSON.stringify(run, null, 2)}\n`),
    writeExclusive(path.join(root, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`),
    writeExclusive(path.join(root, "threshold.json"), `${JSON.stringify(threshold, null, 2)}\n`),
    writeExclusive(path.join(root, "raw", "metrics.jsonl"), raw),
    writeExclusive(path.join(root, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`),
    writeExclusive(path.join(root, "errors.json"), `${JSON.stringify(errors, null, 2)}\n`),
    ...Object.entries(attachments).map(([relative, content]) =>
      writeExclusive(path.join(root, relative), content))
  ]);

  const files = {};
  for (const relative of [...baseRequiredFiles, ...Object.keys(attachments).sort()]) {
    files[relative] = await fileDigest(path.join(root, relative));
  }
  const checksums = {
    schemaVersion: "colla.capacity-evidence-checksums/v1",
    algorithm: "sha256",
    files,
    bundleDigest: sha256(stableStringify(files))
  };
  await writeExclusive(path.join(root, "checksums.json"), `${JSON.stringify(checksums, null, 2)}\n`);
  return { directory: root, checksums };
}

async function safeReadJson(root, relative, findings) {
  try {
    return JSON.parse(await readFile(path.join(root, relative), "utf8"));
  } catch (error) {
    findings.push(`${relative}: ${error.code === "ENOENT" ? "missing" : "invalid JSON"}`);
    return null;
  }
}

export async function verifyEvidenceBundle(directory, options = {}) {
  const root = path.resolve(directory);
  const findings = [];
  const manifest = await safeReadJson(root, "manifest.json", findings);
  const declaredAttachments = declaredCheckpointFiles(manifest, findings);
  const requiredFiles = [...baseRequiredFiles, ...declaredAttachments].sort();
  const checksums = await safeReadJson(root, "checksums.json", findings);
  if (!checksums || checksums.schemaVersion !== "colla.capacity-evidence-checksums/v1"
    || checksums.algorithm !== "sha256" || !checksums.files) {
    findings.push("checksums.json: invalid checksum contract");
  } else {
    const listed = Object.keys(checksums.files).sort();
    if (stableStringify(listed) !== stableStringify(requiredFiles)) {
      findings.push("checksums.json: file list differs from the required evidence contract");
    }
    for (const relative of listed) {
      const resolved = path.resolve(root, relative);
      const withinRoot = path.relative(root, resolved);
      if (withinRoot.startsWith("..") || path.isAbsolute(withinRoot)) {
        findings.push(`checksums.json: unsafe path ${relative}`);
        continue;
      }
      try {
        const actual = await fileDigest(resolved);
        if (actual !== checksums.files[relative]) {
          findings.push(`${relative}: checksum mismatch`);
        }
      } catch (error) {
        findings.push(`${relative}: ${error.code === "ENOENT" ? "missing" : "unreadable"}`);
      }
    }
    if (checksums.bundleDigest !== sha256(stableStringify(checksums.files))) {
      findings.push("checksums.json: bundle digest mismatch");
    }
  }

  const [run, summary, errors] = await Promise.all([
    safeReadJson(root, "run.json", findings),
    safeReadJson(root, "summary.json", findings),
    safeReadJson(root, "errors.json", findings)
  ]);
  if (run && summary && errors) {
    findings.push(...stateErrors(run, summary, errors));
  }

  await validateProtectedScenarioEvidence(root, manifest, run, findings, options);

  for (const relative of requiredFiles) {
    try {
      const fileStat = await stat(path.join(root, relative));
      if (!fileStat.isFile()) {
        findings.push(`${relative}: is not a regular file`);
      }
    } catch {
      // Missing files are already reported by checksum verification.
    }
  }
  try {
    const topLevel = await readdir(root);
    if (!topLevel.includes("raw")) {
      findings.push("raw: missing directory");
    }
  } catch {
    findings.push("evidence directory is unreadable");
  }

  return {
    ok: findings.length === 0,
    tampered: findings.length > 0,
    errors: [...new Set(findings)]
  };
}
