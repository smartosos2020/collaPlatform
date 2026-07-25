import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
export const capacityRoot = path.resolve(moduleDirectory, "..");
export const defaultConfigPaths = Object.freeze({
  contract: path.join(capacityRoot, "config", "c1.v1.json"),
  topology: path.join(capacityRoot, "config", "topology.v1.json"),
  seed: path.join(capacityRoot, "config", "seed.v1.json"),
  keyFiles: path.join(capacityRoot, "config", "key-files.v1.json")
});

export const CONCLUSIONS = Object.freeze([
  "Pass",
  "Fail",
  "Bounded",
  "Not-Committed"
]);

const expectedTargets = Object.freeze({
  registeredMembers: 2000,
  onlineMembers: 500,
  httpRps: 150,
  ordinaryWebSockets: 1000,
  yjsClients: 100,
  yjsRooms: 25,
  workerSustainedEventsPerSecond: 30,
  workerBurstEventsPerSecond: 150,
  workerBurstSeconds: 300,
  workItems: 1000000,
  knowledgeItems: 100000,
  knowledgeBlocks: 1000000
});

const requiredRoles = Object.freeze({
  postgresql: 1,
  redis: 1,
  minio: 1,
  maintenance: 1,
  api: 2,
  worker: 2,
  "event-gateway": 2,
  collaboration: 2,
  web: 1,
  edge: 1,
  "load-source": 1
});

const dependencies = Object.freeze(["postgresql", "redis", "minio"]);

export function stableStringify(value) {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }
  return `{${Object.keys(value).sort().map((key) => (
    `${JSON.stringify(key)}:${stableStringify(value[key])}`
  )).join(",")}}`;
}

export function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

export async function readJson(file) {
  return JSON.parse(await readFile(file, "utf8"));
}

function positiveNumber(value) {
  return typeof value === "number" && Number.isFinite(value) && value > 0;
}

function addError(errors, pathName, message, actual) {
  errors.push({
    path: pathName,
    message,
    ...(actual === undefined ? {} : { actual })
  });
}

export function validateTopology(topology, contract) {
  const errors = [];
  const totals = {
    cpu: 0,
    memoryMiB: 0,
    connections: Object.fromEntries(dependencies.map((name) => [name, 0]))
  };

  if (topology?.schemaVersion !== "colla.capacity-topology/v1") {
    addError(errors, "topology.schemaVersion", "must be colla.capacity-topology/v1", topology?.schemaVersion);
  }
  if (!Number.isInteger(topology?.revision) || topology.revision < 1) {
    addError(errors, "topology.revision", "must be a positive integer", topology?.revision);
  }
  if (topology?.contractId !== "C1") {
    addError(errors, "topology.contractId", "must bind to C1", topology?.contractId);
  }

  const hostCpu = topology?.host?.cpuVcpu;
  const hostMemory = topology?.host?.memoryMiB;
  if (!positiveNumber(hostCpu)) {
    addError(errors, "topology.host.cpuVcpu", "must be positive", hostCpu);
  }
  if (!positiveNumber(hostMemory)) {
    addError(errors, "topology.host.memoryMiB", "must be positive", hostMemory);
  }
  if (contract) {
    if (hostCpu !== contract?.environment?.cpuVcpu) {
      addError(errors, "topology.host.cpuVcpu", "must equal the contract host CPU", hostCpu);
    }
    if (hostMemory !== contract?.environment?.dockerMemoryGiB * 1024) {
      addError(errors, "topology.host.memoryMiB", "must equal the contract Docker memory", hostMemory);
    }
  }

  const roles = topology?.roles ?? {};
  for (const [roleName, expectedReplicas] of Object.entries(requiredRoles)) {
    const role = roles[roleName];
    if (!role) {
      addError(errors, `topology.roles.${roleName}`, "required role is missing");
      continue;
    }
    if (role.replicas !== expectedReplicas) {
      addError(
        errors,
        `topology.roles.${roleName}.replicas`,
        `must equal the frozen replica count ${expectedReplicas}`,
        role.replicas
      );
    }
    if (!["service", "oneshot", "scenario"].includes(role.lifecycle)) {
      addError(errors, `topology.roles.${roleName}.lifecycle`, "must be service, oneshot, or scenario", role.lifecycle);
    }
    if (!positiveNumber(role?.resources?.cpu)) {
      addError(errors, `topology.roles.${roleName}.resources.cpu`, "must be positive", role?.resources?.cpu);
    }
    if (!positiveNumber(role?.resources?.memoryMiB)) {
      addError(errors, `topology.roles.${roleName}.resources.memoryMiB`, "must be positive", role?.resources?.memoryMiB);
    }

    if (positiveNumber(role.replicas) && positiveNumber(role?.resources?.cpu)) {
      totals.cpu += role.replicas * role.resources.cpu;
    }
    if (positiveNumber(role.replicas) && positiveNumber(role?.resources?.memoryMiB)) {
      totals.memoryMiB += role.replicas * role.resources.memoryMiB;
    }

    const runtime = role.runtime;
    if (!runtime || !["jvm", "node", "native"].includes(runtime.kind)) {
      addError(errors, `topology.roles.${roleName}.runtime.kind`, "must be jvm, node, or native", runtime?.kind);
    } else {
      if (!Array.isArray(runtime.parameters) || runtime.parameters.length === 0
        || runtime.parameters.some((parameter) => typeof parameter !== "string" || parameter.length === 0)) {
        addError(errors, `topology.roles.${roleName}.runtime.parameters`, "must contain explicit non-empty parameters");
      }
      if (runtime.kind === "jvm") {
        if (!positiveNumber(runtime.xmsMiB) || !positiveNumber(runtime.xmxMiB) || runtime.xmsMiB > runtime.xmxMiB) {
          addError(errors, `topology.roles.${roleName}.runtime`, "JVM xmsMiB/xmxMiB must be positive and xmsMiB <= xmxMiB");
        }
        if (positiveNumber(runtime.xmxMiB) && positiveNumber(role?.resources?.memoryMiB)
          && runtime.xmxMiB > role.resources.memoryMiB * 0.75) {
          addError(errors, `topology.roles.${roleName}.runtime.xmxMiB`, "must leave at least 25% container memory outside the JVM heap", runtime.xmxMiB);
        }
      }
      if (runtime.kind === "node") {
        if (!positiveNumber(runtime.maxOldSpaceMiB)) {
          addError(errors, `topology.roles.${roleName}.runtime.maxOldSpaceMiB`, "must be positive", runtime.maxOldSpaceMiB);
        } else if (positiveNumber(role?.resources?.memoryMiB)
          && runtime.maxOldSpaceMiB > role.resources.memoryMiB * 0.75) {
          addError(errors, `topology.roles.${roleName}.runtime.maxOldSpaceMiB`, "must leave at least 25% container memory outside the Node heap", runtime.maxOldSpaceMiB);
        }
      }
      if (roleName === "postgresql") {
        if (!positiveNumber(runtime.shmSizeMiB)) {
          addError(errors, `topology.roles.${roleName}.runtime.shmSizeMiB`, "must be positive", runtime.shmSizeMiB);
        } else if (positiveNumber(role?.resources?.memoryMiB)
          && runtime.shmSizeMiB > role.resources.memoryMiB * 0.25) {
          addError(errors, `topology.roles.${roleName}.runtime.shmSizeMiB`, "must not reserve more than 25% of container memory", runtime.shmSizeMiB);
        }
      }
    }

    for (const dependency of dependencies) {
      const allocation = role?.connections?.[dependency];
      if (!Number.isInteger(allocation) || allocation < 0) {
        addError(errors, `topology.roles.${roleName}.connections.${dependency}`, "must be a non-negative integer", allocation);
      } else if (Number.isInteger(role.replicas) && role.replicas > 0) {
        totals.connections[dependency] += role.replicas * allocation;
      }
    }
  }

  for (const roleName of Object.keys(roles)) {
    if (!(roleName in requiredRoles)) {
      addError(errors, `topology.roles.${roleName}`, "role is not part of the frozen C1 topology");
    }
  }

  if (positiveNumber(hostCpu) && totals.cpu > hostCpu) {
    addError(errors, "topology.roles", `CPU allocation ${totals.cpu} exceeds host budget ${hostCpu}`);
  }
  if (positiveNumber(hostMemory) && totals.memoryMiB > hostMemory) {
    addError(errors, "topology.roles", `memory allocation ${totals.memoryMiB} MiB exceeds host budget ${hostMemory} MiB`);
  }

  for (const dependency of dependencies) {
    const budget = topology?.connectionBudgets?.[dependency];
    if (!Number.isInteger(budget?.limit) || budget.limit <= 0
      || !Number.isInteger(budget?.reserve) || budget.reserve < 0 || budget.reserve >= budget.limit) {
      addError(errors, `topology.connectionBudgets.${dependency}`, "limit must be positive and reserve must be smaller than limit");
      continue;
    }
    const allocatable = budget.limit - budget.reserve;
    if (totals.connections[dependency] > allocatable) {
      addError(
        errors,
        `topology.connectionBudgets.${dependency}`,
        `allocated ${totals.connections[dependency]} exceeds allocatable budget ${allocatable} (${budget.reserve} reserved)`
      );
    }
  }

  totals.cpu = Number(totals.cpu.toFixed(3));
  return { ok: errors.length === 0, errors, totals };
}

export function validateContract(contract, topology) {
  const errors = [];
  if (contract?.schemaVersion !== "colla.capacity-contract/v1") {
    addError(errors, "contract.schemaVersion", "must be colla.capacity-contract/v1", contract?.schemaVersion);
  }
  if (contract?.id !== "C1" || contract?.revision !== 1) {
    addError(errors, "contract", "must identify frozen C1 revision 1");
  }
  if (contract?.environment?.cpuVcpu !== 20
    || contract?.environment?.dockerMemoryGiB !== 32
    || contract?.environment?.workloadPlacement !== "co-located-in-docker"
    || contract?.environment?.dedicatedHostRequired !== true) {
    addError(errors, "contract.environment", "must freeze a dedicated 20 vCPU / Docker 32 GiB co-located environment");
  }

  for (const [name, expected] of Object.entries(expectedTargets)) {
    if (contract?.targets?.[name] !== expected) {
      addError(errors, `contract.targets.${name}`, `must equal frozen C1 value ${expected}`, contract?.targets?.[name]);
    }
  }
  const unexpectedTargets = Object.keys(contract?.targets ?? {}).filter((name) => !(name in expectedTargets));
  if (unexpectedTargets.length > 0) {
    addError(errors, "contract.targets", `contains unknown targets: ${unexpectedTargets.join(", ")}`);
  }

  const thresholdIds = new Set();
  const thresholdMetrics = new Set();
  const validOperators = new Set(["lt", "lte", "eq", "gte", "gt"]);
  if (!Array.isArray(contract?.thresholds) || contract.thresholds.length === 0) {
    addError(errors, "contract.thresholds", "must contain frozen thresholds");
  } else {
    for (const [index, threshold] of contract.thresholds.entries()) {
      if (!threshold?.id || thresholdIds.has(threshold.id)) {
        addError(errors, `contract.thresholds.${index}.id`, "must be present and unique", threshold?.id);
      }
      if (!threshold?.metric || thresholdMetrics.has(threshold.metric)) {
        addError(errors, `contract.thresholds.${index}.metric`, "must be present and unique", threshold?.metric);
      }
      thresholdIds.add(threshold?.id);
      thresholdMetrics.add(threshold?.metric);
      if (!validOperators.has(threshold?.operator) || typeof threshold?.value !== "number" || !threshold?.unit) {
        addError(errors, `contract.thresholds.${index}`, "must define operator, numeric value, and unit");
      }
    }
  }

  if (stableStringify(contract?.conclusions?.allowed) !== stableStringify(CONCLUSIONS)) {
    addError(errors, "contract.conclusions.allowed", "must be exactly Pass, Fail, Bounded, Not-Committed in that order");
  }
  for (const conclusion of CONCLUSIONS) {
    if (typeof contract?.conclusions?.definitions?.[conclusion] !== "string"
      || contract.conclusions.definitions[conclusion].trim().length === 0) {
      addError(errors, `contract.conclusions.definitions.${conclusion}`, "must have one non-empty definition");
    }
  }
  if (Object.keys(contract?.conclusions?.definitions ?? {}).some((value) => !CONCLUSIONS.includes(value))) {
    addError(errors, "contract.conclusions.definitions", "must not define additional conclusion vocabulary");
  }
  if (!Array.isArray(contract?.nonCommitments) || contract.nonCommitments.length === 0) {
    addError(errors, "contract.nonCommitments", "must explicitly list non-commitments");
  }

  let topologyResult = { ok: true, errors: [], totals: undefined };
  if (topology) {
    topologyResult = validateTopology(topology, contract);
    errors.push(...topologyResult.errors);
    if (contract?.topology?.schemaVersion !== topology.schemaVersion
      || contract?.topology?.revision !== topology.revision) {
      addError(errors, "contract.topology", "must bind the supplied topology schema and revision");
    }
  }

  return {
    ok: errors.length === 0,
    errors,
    contractDigest: sha256(stableStringify(contract)),
    topologyDigest: topology ? sha256(stableStringify(topology)) : null,
    totals: topologyResult.totals
  };
}

export async function loadCapacityConfig(paths = {}) {
  const resolved = { ...defaultConfigPaths, ...paths };
  const [contract, topology, seed] = await Promise.all([
    readJson(resolved.contract),
    readJson(resolved.topology),
    readJson(resolved.seed)
  ]);
  return { contract, topology, seed, paths: resolved };
}

export async function validateCapacityConfig(paths = {}) {
  const loaded = await loadCapacityConfig(paths);
  return {
    ...validateContract(loaded.contract, loaded.topology),
    paths: loaded.paths
  };
}
