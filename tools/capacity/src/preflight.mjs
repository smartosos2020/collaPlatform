import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { stat, statfs, readFile, writeFile, mkdir } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { defaultConfigPaths, readJson, sha256, stableStringify } from "./contract.mjs";

const sensitiveKeyPattern = /(secret|password|passwd|token|credential|authorization|private.?key|api.?key)/i;
const sensitiveValuePatterns = [
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/i,
  /\bBearer\s+[A-Za-z0-9._~+/=-]{12,}/i,
  /\b(?:postgres(?:ql)?|redis|https?):\/\/[^/\s:@]+:[^@\s/]+@/i,
  /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{8,}\b/
];

function cleanText(value) {
  let result = String(value ?? "").replaceAll("\u0000", "").trim();
  result = result.replace(
    /((?:postgres(?:ql)?|redis|https?):\/\/)[^/\s:@]+:[^@\s/]+@/gi,
    "$1[REDACTED]@"
  );
  result = result.replace(/\bBearer\s+[A-Za-z0-9._~+/=-]{12,}/gi, "Bearer [REDACTED]");
  result = result.replace(/-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----/gi, "[REDACTED PRIVATE KEY]");
  return result;
}

export function redactSecrets(value, key = "") {
  if (sensitiveKeyPattern.test(key)) {
    return "[REDACTED]";
  }
  if (Array.isArray(value)) {
    return value.map((item) => redactSecrets(item));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([entryKey, entryValue]) => (
      [entryKey, redactSecrets(entryValue, entryKey)]
    )));
  }
  return typeof value === "string" ? cleanText(value) : value;
}

export function containsSecret(value, key = "") {
  if (sensitiveKeyPattern.test(key) && value !== undefined && value !== null && value !== "[REDACTED]") {
    return true;
  }
  if (Array.isArray(value)) {
    return value.some((item) => containsSecret(item));
  }
  if (value && typeof value === "object") {
    return Object.entries(value).some(([entryKey, entryValue]) => containsSecret(entryValue, entryKey));
  }
  return typeof value === "string" && sensitiveValuePatterns.some((pattern) => pattern.test(value));
}

function command(file, args = [], options = {}) {
  const result = spawnSync(file, args, {
    cwd: options.cwd,
    encoding: "utf8",
    timeout: options.timeout ?? 5000,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"]
  });
  const output = cleanText(result.stdout || result.stderr);
  if (!result.error && result.status === 0) {
    return { available: true, value: output.split(/\r?\n/, 1)[0] };
  }
  return {
    available: false,
    value: null,
    error: (output || result.error?.code || "command unavailable").split(/\r?\n/, 1)[0].slice(0, 300)
  };
}

function gitValue(repoRoot, args) {
  const result = command("git", args, { cwd: repoRoot });
  return result.available ? result.value : null;
}

async function findRepositoryRoot(start) {
  let current = path.resolve(start);
  while (true) {
    try {
      await stat(path.join(current, ".git"));
      return current;
    } catch {
      const parent = path.dirname(current);
      if (parent === current) {
        return path.resolve(start);
      }
      current = parent;
    }
  }
}

async function gitCommitFallback(repoRoot) {
  try {
    const head = (await readFile(path.join(repoRoot, ".git", "HEAD"), "utf8")).trim();
    if (!head.startsWith("ref: ")) {
      return /^[0-9a-f]{40,64}$/i.test(head) ? head : null;
    }
    const ref = head.slice(5);
    try {
      const value = (await readFile(path.join(repoRoot, ".git", ...ref.split("/")), "utf8")).trim();
      return /^[0-9a-f]{40,64}$/i.test(value) ? value : null;
    } catch {
      const packed = await readFile(path.join(repoRoot, ".git", "packed-refs"), "utf8");
      const match = packed.split(/\r?\n/).find((line) => line.endsWith(` ${ref}`));
      return match?.split(" ", 1)[0] ?? null;
    }
  } catch {
    return null;
  }
}

async function hashFile(repoRoot, relativePath) {
  const fullPath = path.resolve(repoRoot, relativePath);
  const relative = path.relative(repoRoot, fullPath);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new Error(`key file escapes repository root: ${relativePath}`);
  }
  try {
    const fileStat = await stat(fullPath);
    if (!fileStat.isFile()) {
      return { exists: false, sha256: null, size: null };
    }
    const content = await readFile(fullPath);
    return {
      exists: true,
      sha256: createHash("sha256").update(content).digest("hex"),
      size: fileStat.size
    };
  } catch (error) {
    if (error.code === "ENOENT") {
      return { exists: false, sha256: null, size: null };
    }
    throw error;
  }
}

async function storageSummary(targetPath) {
  if (!targetPath) return { detected: false, volumeRoot: null, totalBytes: null, freeBytesAtCapture: null }
  try {
    const resolved = path.resolve(targetPath)
    const filesystem = await statfs(resolved)
    const blockSize = Number(filesystem.bsize)
    return {
      detected: true,
      volumeRoot: path.parse(resolved).root || resolved,
      totalBytes: Number(filesystem.blocks) * blockSize,
      freeBytesAtCapture: Number(filesystem.bavail) * blockSize
    }
  } catch {
    return { detected: false, volumeRoot: null, totalBytes: null, freeBytesAtCapture: null }
  }
}

async function dockerDataPath(options = {}) {
  if (options.dockerDataPath) return path.resolve(options.dockerDataPath)
  if (process.env.COLLA_CAPACITY_DOCKER_DATA_PATH) {
    return path.resolve(process.env.COLLA_CAPACITY_DOCKER_DATA_PATH)
  }
  if (process.platform === "win32") {
    const settingsPath = path.join(process.env.APPDATA ?? os.homedir(), "Docker", "settings-store.json")
    try {
      const settings = JSON.parse(await readFile(settingsPath, "utf8"))
      if (settings.CustomWslDistroDir) return path.resolve(settings.CustomWslDistroDir)
    } catch {
      return null
    }
  }
  if (process.platform === "darwin") {
    return path.join(os.homedir(), "Library", "Containers", "com.docker.docker", "Data")
  }
  return "/var/lib/docker"
}

function networkSummary() {
  return Object.entries(os.networkInterfaces())
    .map(([name, addresses]) => ({
      name,
      families: [...new Set((addresses ?? []).map((address) => String(address.family)))].sort(),
      internal: (addresses ?? []).every((address) => address.internal),
      addressCount: (addresses ?? []).length,
      externalAddressCount: (addresses ?? []).filter((address) => !address.internal).length
    }))
    .sort((left, right) => left.name.localeCompare(right.name));
}

function clockSummary() {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone ?? "unknown";
  const probe = process.platform === "win32"
    ? command("w32tm", ["/query", "/status"])
    : command("timedatectl", ["show", "--property=NTPSynchronized", "--value"]);
  return {
    timezone,
    utcOffsetMinutes: -new Date().getTimezoneOffset(),
    synchronizationProbe: probe.available
      ? { available: true, status: probe.value }
      : { available: false, status: "unavailable" }
  };
}

function toolVersions() {
  return {
    docker: command("docker", ["--version"]),
    compose: command("docker", ["compose", "version"]),
    dockerEngine: dockerEngineSummary(),
    node: { available: true, value: process.version },
    java: command("java", ["-version"]),
    maven: command("mvn", ["--version"])
  };
}

function dockerEngineSummary() {
  const probe = command("docker", ["info", "--format", "{{json .}}"], { timeout: 15000 });
  if (!probe.available) {
    return probe;
  }
  try {
    const value = JSON.parse(probe.value);
    return {
      available: true,
      value: {
        serverVersion: value.ServerVersion,
        operatingSystem: value.OperatingSystem,
        architecture: value.Architecture,
        cpuCount: value.NCPU,
        memoryBytes: value.MemTotal,
        storageDriver: value.Driver,
        cgroupDriver: value.CgroupDriver,
        dockerRootDir: value.DockerRootDir
      }
    };
  } catch {
    return { available: false, value: null, error: "docker info did not return valid JSON" };
  }
}

function criticalProjection(manifest) {
  return {
    schemaVersion: manifest.schemaVersion,
    git: manifest.git,
    os: {
      platform: manifest.os.platform,
      release: manifest.os.release,
      architecture: manifest.os.architecture
    },
    cpu: manifest.cpu,
    memory: {
      totalBytes: manifest.memory.totalBytes
    },
    disk: {
      totalBytes: manifest.disk.totalBytes
    },
    storage: {
      repository: {
        detected: manifest.storage.repository.detected,
        volumeRoot: manifest.storage.repository.volumeRoot,
        totalBytes: manifest.storage.repository.totalBytes
      },
      temporary: {
        detected: manifest.storage.temporary.detected,
        volumeRoot: manifest.storage.temporary.volumeRoot,
        totalBytes: manifest.storage.temporary.totalBytes
      },
      dockerData: {
        detected: manifest.storage.dockerData.detected,
        volumeRoot: manifest.storage.dockerData.volumeRoot,
        totalBytes: manifest.storage.dockerData.totalBytes
      }
    },
    resourceEligibility: manifest.resourceEligibility,
    network: manifest.network,
    clock: {
      timezone: manifest.clock.timezone,
      utcOffsetMinutes: manifest.clock.utcOffsetMinutes,
      synchronizationProbe: manifest.clock.synchronizationProbe
    },
    tools: manifest.tools,
    keyFiles: manifest.keyFiles
  };
}

export async function capturePreflight(options = {}) {
  const repoRoot = options.repoRoot
    ? path.resolve(options.repoRoot)
    : await findRepositoryRoot(process.cwd());
  const keyFilesConfig = await readJson(options.keyFiles ?? defaultConfigPaths.keyFiles);
  const contract = await readJson(options.contract ?? defaultConfigPaths.contract);
  const preflightRequirements = contract.environment?.preflightRequirements ?? {};
  if (keyFilesConfig.schemaVersion !== "colla.capacity-key-files/v1"
    || !Array.isArray(keyFilesConfig.files) || keyFilesConfig.files.length === 0) {
    throw new Error("invalid capacity key-files configuration");
  }

  const fileEntries = await Promise.all(keyFilesConfig.files.map(async (relativePath) => (
    [relativePath.replaceAll("\\", "/"), await hashFile(repoRoot, relativePath)]
  )));
  const filesystem = await statfs(repoRoot);
  const blockSize = Number(filesystem.bsize);
  const cpuModels = [...new Set(os.cpus().map((cpu) => cpu.model))].sort();
  const porcelain = gitValue(repoRoot, ["status", "--porcelain", "--untracked-files=normal"]);

  const gitCommit = gitValue(repoRoot, ["rev-parse", "HEAD"]) ?? await gitCommitFallback(repoRoot);
  const freeMemoryBytes = os.freemem();
  const repositoryStorage = await storageSummary(repoRoot);
  const temporaryStorage = await storageSummary(os.tmpdir());
  const dockerStorage = await storageSummary(await dockerDataPath(options));
  const freeDiskBytes = repositoryStorage.freeBytesAtCapture;
  const minimumHostFreeMemoryBytes = Number(preflightRequirements.minimumHostFreeMemoryGiB ?? 0)
    * 1024 * 1024 * 1024;
  const minimumRepositoryDiskFreeBytes = Number(preflightRequirements.minimumRepositoryDiskFreeGiB ?? 0)
    * 1024 * 1024 * 1024;
  const minimumTempDiskFreeBytes = Number(preflightRequirements.minimumTempDiskFreeGiB ?? 0)
    * 1024 * 1024 * 1024;
  const minimumDockerDataDiskFreeBytes = Number(preflightRequirements.minimumDockerDataDiskFreeGiB ?? 0)
    * 1024 * 1024 * 1024;
  const clock = clockSummary();
  const clockSynchronizationRequired = preflightRequirements.clockSynchronizationRequired === true;
  const manifest = redactSecrets({
    schemaVersion: "colla.capacity-preflight/v1",
    capturedAt: new Date().toISOString(),
    git: {
      commit: gitCommit,
      dirty: porcelain === null ? null : porcelain.length > 0
    },
    os: {
      platform: os.platform(),
      release: os.release(),
      architecture: os.arch()
    },
    cpu: {
      logicalCount: os.cpus().length,
      models: cpuModels
    },
    memory: {
      totalBytes: os.totalmem(),
      freeBytesAtCapture: freeMemoryBytes
    },
    disk: {
      totalBytes: Number(filesystem.blocks) * blockSize,
      freeBytesAtCapture: freeDiskBytes
    },
    storage: {
      repository: repositoryStorage,
      temporary: temporaryStorage,
      dockerData: dockerStorage
    },
    resourceEligibility: {
      minimumHostFreeMemoryBytes,
      minimumRepositoryDiskFreeBytes,
      minimumTempDiskFreeBytes,
      minimumDockerDataDiskFreeBytes,
      clockSynchronizationRequired,
      hostFreeMemorySatisfied: freeMemoryBytes >= minimumHostFreeMemoryBytes,
      repositoryDiskFreeSatisfied: repositoryStorage.detected
        && repositoryStorage.freeBytesAtCapture >= minimumRepositoryDiskFreeBytes,
      tempDiskFreeSatisfied: temporaryStorage.detected
        && temporaryStorage.freeBytesAtCapture >= minimumTempDiskFreeBytes,
      dockerDataDiskFreeSatisfied: dockerStorage.detected
        && dockerStorage.freeBytesAtCapture >= minimumDockerDataDiskFreeBytes,
      clockSynchronizationSatisfied: !clockSynchronizationRequired
        || clock.synchronizationProbe.available === true
    },
    network: networkSummary(),
    clock,
    tools: toolVersions(),
    keyFiles: Object.fromEntries(fileEntries)
  });
  manifest.critical = criticalProjection(manifest);
  manifest.criticalFingerprint = sha256(stableStringify(manifest.critical));

  if (containsSecret(manifest)) {
    throw new Error("preflight manifest contains sensitive material");
  }
  if (options.output) {
    const output = path.resolve(options.output);
    await mkdir(path.dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify(manifest, null, 2)}\n`, { encoding: "utf8", flag: "w" });
  }
  return manifest;
}

function diffValues(baseline, current, currentPath, drifts) {
  if (stableStringify(baseline) === stableStringify(current)) {
    return;
  }
  if (baseline && current && typeof baseline === "object" && typeof current === "object"
    && !Array.isArray(baseline) && !Array.isArray(current)) {
    const keys = [...new Set([...Object.keys(baseline), ...Object.keys(current)])].sort();
    for (const key of keys) {
      diffValues(baseline[key], current[key], currentPath ? `${currentPath}.${key}` : key, drifts);
    }
    return;
  }
  drifts.push({ path: currentPath, baseline, current });
}

export function comparePreflight(baseline, current) {
  const drifts = [];
  if (!baseline?.critical || !current?.critical) {
    return {
      ok: false,
      blocked: true,
      drifts: [{ path: "critical", baseline: Boolean(baseline?.critical), current: Boolean(current?.critical) }]
    };
  }
  diffValues(baseline.critical, current.critical, "critical", drifts);
  return {
    ok: drifts.length === 0,
    blocked: drifts.length > 0,
    baselineFingerprint: baseline.criticalFingerprint ?? sha256(stableStringify(baseline.critical)),
    currentFingerprint: current.criticalFingerprint ?? sha256(stableStringify(current.critical)),
    drifts
  };
}

export async function comparePreflightFiles(baselinePath, currentPath) {
  const [baseline, current] = await Promise.all([
    readJson(baselinePath),
    readJson(currentPath)
  ]);
  return comparePreflight(baseline, current);
}
