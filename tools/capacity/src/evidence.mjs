import { createHash } from "node:crypto";
import { mkdir, readFile, readdir, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { CONCLUSIONS, sha256, stableStringify } from "./contract.mjs";
import { containsSecret, redactSecrets } from "./preflight.mjs";

const requiredFiles = Object.freeze([
  "run.json",
  "manifest.json",
  "threshold.json",
  "raw/metrics.jsonl",
  "summary.json",
  "errors.json"
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
  const manifest = redactSecrets(input?.manifest ?? {});
  const threshold = redactSecrets(input?.threshold ?? input?.thresholds ?? {});
  const summary = redactSecrets(input?.summary ?? {});
  const errors = redactSecrets(input?.errors ?? []);
  const raw = serializeRaw(input?.raw);

  const stateValidation = validateEvidenceState(run, summary, errors);
  if (!stateValidation.ok) {
    throw new Error(`invalid evidence state: ${stateValidation.errors.join("; ")}`);
  }
  const values = { run, manifest, threshold, summary, errors, raw };
  if (Object.entries(values).some(([key, value]) => containsSecret(value, key))) {
    throw new Error("evidence input contains sensitive material after redaction");
  }

  await mkdir(root, { recursive: true });
  await Promise.all([
    writeExclusive(path.join(root, "run.json"), `${JSON.stringify(run, null, 2)}\n`),
    writeExclusive(path.join(root, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`),
    writeExclusive(path.join(root, "threshold.json"), `${JSON.stringify(threshold, null, 2)}\n`),
    writeExclusive(path.join(root, "raw", "metrics.jsonl"), raw),
    writeExclusive(path.join(root, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`),
    writeExclusive(path.join(root, "errors.json"), `${JSON.stringify(errors, null, 2)}\n`)
  ]);

  const files = {};
  for (const relative of requiredFiles) {
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

export async function verifyEvidenceBundle(directory) {
  const root = path.resolve(directory);
  const findings = [];
  const checksums = await safeReadJson(root, "checksums.json", findings);
  if (!checksums || checksums.schemaVersion !== "colla.capacity-evidence-checksums/v1"
    || checksums.algorithm !== "sha256" || !checksums.files) {
    findings.push("checksums.json: invalid checksum contract");
  } else {
    const listed = Object.keys(checksums.files).sort();
    if (stableStringify(listed) !== stableStringify([...requiredFiles].sort())) {
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
    tampered: findings.some((finding) => finding.includes("checksum") || finding.includes("digest")),
    errors: [...new Set(findings)]
  };
}
