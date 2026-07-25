import { createHash } from "node:crypto";

export const MAX_FIXTURE_DERIVATIONS = 10_000;
export const MAX_FIXTURE_ORDINAL = 9_999_999;

const allowedConfigKeys = new Set(["seedId", "workspaceWeights"]);
const entityDomains = new Set([
  "member",
  "permission",
  "project",
  "issue",
  "knowledge-item",
  "knowledge-block",
  "notification",
  "im-message",
  "file",
  "collaboration-room"
]);
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function isRecord(value) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function unknownKeys(value, allowed) {
  return Reflect.ownKeys(value)
    .filter((key) => typeof key !== "string" || !allowed.has(key))
    .map(String);
}

function isDensePlainArray(value) {
  if (!Array.isArray(value)) {
    return false;
  }
  const keys = Reflect.ownKeys(value);
  return keys.length === value.length + 1 && keys.every((key) => {
    if (key === "length") {
      return true;
    }
    if (typeof key !== "string" || !/^(0|[1-9][0-9]*)$/.test(key)) {
      return false;
    }
    return Number(key) < value.length;
  });
}

export function validateFixtureConfig(config) {
  const errors = [];
  if (!isRecord(config)) {
    return { ok: false, errors: ["fixture config must be a plain object"] };
  }
  const unexpected = unknownKeys(config, allowedConfigKeys);
  if (unexpected.length > 0) {
    errors.push(`fixture config contains unsupported fields: ${unexpected.sort().join(", ")}`);
  }
  if (typeof config.seedId !== "string"
    || !/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(config.seedId)) {
    errors.push("seedId must match [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}");
  }
  if (!isDensePlainArray(config.workspaceWeights)
    || config.workspaceWeights.length < 2
    || Array.from(config.workspaceWeights).some(
      (weight) => !Number.isInteger(weight) || weight <= 0
    )
    || config.workspaceWeights.reduce((sum, weight) => sum + weight, 0) !== 100) {
    errors.push("workspaceWeights must contain at least two positive integers and sum to 100");
  }
  return { ok: errors.length === 0, errors };
}

function requireConfig(config) {
  const validation = validateFixtureConfig(config);
  if (!validation.ok) {
    throw new TypeError(`invalid fixture config: ${validation.errors.join("; ")}`);
  }
  return config;
}

function requireRecord(value, label, allowed) {
  if (!isRecord(value)) {
    throw new TypeError(`${label} must be a plain object`);
  }
  const unexpected = unknownKeys(value, allowed);
  if (unexpected.length > 0) {
    throw new TypeError(`${label} contains unsupported fields: ${unexpected.sort().join(", ")}`);
  }
  return value;
}

function requireOrdinal(value, label = "ordinal") {
  if (!Number.isSafeInteger(value) || value < 1 || value > MAX_FIXTURE_ORDINAL) {
    throw new RangeError(`${label} must be an integer between 1 and ${MAX_FIXTURE_ORDINAL}`);
  }
  return value;
}

function requireCount(value) {
  if (!Number.isSafeInteger(value) || value < 0 || value > MAX_FIXTURE_DERIVATIONS) {
    throw new RangeError(`count must be an integer between 0 and ${MAX_FIXTURE_DERIVATIONS}`);
  }
  return value;
}

function formatUuid(hex) {
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function rawMd5Uuid(input) {
  return formatUuid(createHash("md5").update(input).digest("hex"));
}

export function deriveWorkspaceUuid(config, ordinal) {
  const valid = requireConfig(config);
  requireOrdinal(ordinal, "workspace ordinal");
  if (ordinal > valid.workspaceWeights.length) {
    throw new RangeError(`workspace ordinal must be between 1 and ${valid.workspaceWeights.length}`);
  }
  const hex = createHash("md5")
    .update(`${valid.seedId}:workspace:${ordinal}`)
    .digest("hex")
    .split("");
  hex[12] = "5";
  hex[16] = ["8", "9", "a", "b"][Number.parseInt(hex[16], 16) % 4];
  return formatUuid(hex.join(""));
}

export const deriveWorkspaceId = deriveWorkspaceUuid;

export function deriveEntityUuid(config, domain, ordinal) {
  const valid = requireConfig(config);
  if (typeof domain !== "string" || !entityDomains.has(domain)) {
    throw new TypeError(`domain must be one of: ${[...entityDomains].join(", ")}`);
  }
  requireOrdinal(ordinal);
  return rawMd5Uuid(`${valid.seedId}:${domain}:${ordinal}`);
}

export const deriveEntityId = deriveEntityUuid;

export function deriveKnowledgeSpaceUuid(config, workspaceUuid) {
  const valid = requireConfig(config);
  if (typeof workspaceUuid !== "string" || !uuidPattern.test(workspaceUuid)) {
    throw new TypeError("workspaceUuid must be a lowercase UUID");
  }
  const expectedWorkspaceIds = valid.workspaceWeights.map(
    (_weight, index) => deriveWorkspaceUuid(valid, index + 1)
  );
  if (!expectedWorkspaceIds.includes(workspaceUuid)) {
    throw new RangeError("workspaceUuid does not belong to this fixture config");
  }
  return rawMd5Uuid(`${valid.seedId}:knowledge-space:${workspaceUuid}`);
}

export const deriveKnowledgeSpaceId = deriveKnowledgeSpaceUuid;

export function deriveFixtureUsername(config, ordinal) {
  const valid = requireConfig(config);
  requireOrdinal(ordinal);
  const prefix = createHash("sha256").update(valid.seedId).digest("hex").slice(0, 10);
  return `cap_${prefix}_u${String(ordinal).padStart(7, "0")}`;
}

export function workspaceOrdinalFor(config, ordinal) {
  const valid = requireConfig(config);
  requireOrdinal(ordinal);
  const percentile = (ordinal - 1) % 100;
  let upper = 0;
  for (let index = 0; index < valid.workspaceWeights.length; index += 1) {
    upper += valid.workspaceWeights[index];
    if (percentile < upper) {
      return index + 1;
    }
  }
  throw new Error("workspaceWeights did not cover the complete percentile range");
}

function isActiveOrdinal(ordinal) {
  return (ordinal - 1) % 100 < 95;
}

function requireWorkspaceOrdinal(config, workspaceOrdinal) {
  if (!Number.isSafeInteger(workspaceOrdinal)
    || workspaceOrdinal < 1
    || workspaceOrdinal > config.workspaceWeights.length) {
    throw new RangeError(
      `workspaceOrdinal must be an integer between 1 and ${config.workspaceWeights.length}`
    );
  }
  return workspaceOrdinal;
}

function eligibleResidues(config, activeOnly, workspaceOrdinal) {
  return Array.from({ length: 100 }, (_value, residue) => residue + 1).filter((ordinal) =>
    (!activeOnly || isActiveOrdinal(ordinal))
    && (workspaceOrdinal === undefined
      || workspaceOrdinalFor(config, ordinal) === workspaceOrdinal));
}

export function deriveFixtureUsers(config, options) {
  const valid = requireConfig(config);
  const input = requireRecord(
    options,
    "fixture user options",
    new Set(["count", "startOrdinal", "activeOnly", "workspaceOrdinal"])
  );
  const count = requireCount(input.count);
  const startOrdinal = requireOrdinal(input.startOrdinal ?? 1, "startOrdinal");
  const activeOnly = input.activeOnly ?? false;
  if (typeof activeOnly !== "boolean") {
    throw new TypeError("activeOnly must be a boolean");
  }
  const workspaceFilter = input.workspaceOrdinal === undefined
    ? undefined
    : requireWorkspaceOrdinal(valid, input.workspaceOrdinal);
  if (count === 0) {
    return [];
  }
  if (eligibleResidues(valid, activeOnly, workspaceFilter).length === 0) {
    throw new RangeError("the requested filters cannot match any fixture user");
  }

  const users = [];
  for (let ordinal = startOrdinal; users.length < count; ordinal += 1) {
    if (ordinal > MAX_FIXTURE_ORDINAL) {
      throw new RangeError("the requested fixture users exceed the maximum fixture ordinal");
    }
    const workspaceOrdinal = workspaceOrdinalFor(valid, ordinal);
    const active = isActiveOrdinal(ordinal);
    if ((!activeOnly || active)
      && (workspaceFilter === undefined || workspaceOrdinal === workspaceFilter)) {
      const workspaceId = deriveWorkspaceUuid(valid, workspaceOrdinal);
      const username = deriveFixtureUsername(valid, ordinal);
      users.push({
        ordinal,
        id: deriveEntityUuid(valid, "member", ordinal),
        workspaceOrdinal,
        workspaceId,
        username,
        email: `${username}@capacity.invalid`,
        status: active ? "active" : "disabled"
      });
    }
  }
  return users;
}

export function deriveCollaborationRooms(config, options) {
  const valid = requireConfig(config);
  const input = requireRecord(
    options,
    "collaboration room options",
    new Set(["workspaceOrdinal", "knowledgeItemOrdinals"])
  );
  const workspaceOrdinal = requireWorkspaceOrdinal(valid, input.workspaceOrdinal);
  if (!Array.isArray(input.knowledgeItemOrdinals)) {
    throw new TypeError("knowledgeItemOrdinals must be an array");
  }
  if (input.knowledgeItemOrdinals.length > MAX_FIXTURE_DERIVATIONS) {
    throw new RangeError(
      `knowledgeItemOrdinals must contain at most ${MAX_FIXTURE_DERIVATIONS} entries`
    );
  }
  if (!isDensePlainArray(input.knowledgeItemOrdinals)) {
    throw new TypeError("knowledgeItemOrdinals must be a dense array without extra fields");
  }
  const ordinals = Array.from(input.knowledgeItemOrdinals, (ordinal) =>
    requireOrdinal(ordinal, "knowledge item ordinal"));
  if (new Set(ordinals).size !== ordinals.length) {
    throw new TypeError("knowledgeItemOrdinals must not contain duplicates");
  }
  for (const ordinal of ordinals) {
    if (workspaceOrdinalFor(valid, ordinal) !== workspaceOrdinal) {
      throw new RangeError(
        `knowledge item ordinal ${ordinal} is assigned to another workspace`
      );
    }
  }

  const workspaceId = deriveWorkspaceUuid(valid, workspaceOrdinal);
  const spaceId = deriveKnowledgeSpaceUuid(valid, workspaceId);
  return ordinals.map((knowledgeItemOrdinal) => {
    const itemId = deriveEntityUuid(valid, "knowledge-item", knowledgeItemOrdinal);
    return {
      name: `knowledge:${workspaceId}:${itemId}`,
      workspaceOrdinal,
      workspaceId,
      spaceId,
      knowledgeItemOrdinal,
      itemId
    };
  });
}
