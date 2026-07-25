import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { defaultConfigPaths, readJson, sha256, stableStringify } from "./contract.mjs";
import { redactSecrets } from "./preflight.mjs";

const requiredDomains = Object.freeze([
  "workspace",
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

const cleanupDomains = Object.freeze([
  "collaboration-room",
  "im-message",
  "support-conversation-member",
  "support-conversation",
  "notification",
  "permission",
  "support-role-permission",
  "support-role",
  "knowledge-block",
  "support-knowledge-space",
  "knowledge-item",
  "issue",
  "project",
  "file",
  "member",
  "workspace"
]);

function sqlLiteral(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function assertSafeName(value, label) {
  if (typeof value !== "string" || !/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/.test(value)) {
    throw new Error(`${label} must match [a-zA-Z0-9][a-zA-Z0-9_-]{0,63}`);
  }
}

function deterministicUuid(input) {
  const hex = createHash("md5").update(input).digest("hex").split("");
  hex[12] = "5";
  hex[16] = ["8", "9", "a", "b"][Number.parseInt(hex[16], 16) % 4];
  const value = hex.join("");
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}

function postgresMd5Uuid(input) {
  const value = createHash("md5").update(input).digest("hex");
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}

export function validateSeedConfig(config) {
  const errors = [];
  if (config?.schemaVersion !== "colla.capacity-seed/v1" || config?.revision !== 1) {
    errors.push("seed config must be colla.capacity-seed/v1 revision 1");
  }
  if (!/^[a-z][a-z0-9_]{0,62}$/.test(config?.fixtureSchema ?? "")) {
    errors.push("fixtureSchema must be a safe PostgreSQL identifier");
  }
  if (!Number.isInteger(config?.workspaceCount) || config.workspaceCount < 2) {
    errors.push("workspaceCount must be an integer >= 2");
  }
  if (typeof config?.fixedTimestamp !== "string"
    || !Number.isFinite(Date.parse(config.fixedTimestamp))) {
    errors.push("fixedTimestamp must be an ISO-8601 timestamp");
  }
  const credentialSource = config?.credentialSource;
  if (credentialSource?.type !== "initialized-user-password-hash"
    || typeof credentialSource?.username !== "string"
    || !/^[a-zA-Z0-9][a-zA-Z0-9_.@-]{0,127}$/.test(credentialSource.username)
    || credentialSource?.requiredStatus !== "active"
    || credentialSource?.requiredNotDeleted !== true
    || credentialSource?.outputFingerprint !== "md5-of-stored-password-hash") {
    errors.push("credentialSource must bind one active, non-deleted initialized user and a redacted hash fingerprint");
  }
  if (!Array.isArray(config?.workspaceWeights)
    || config.workspaceWeights.length !== config.workspaceCount
    || config.workspaceWeights.some((weight) => !Number.isInteger(weight) || weight <= 0)
    || config.workspaceWeights.reduce((sum, weight) => sum + weight, 0) !== 100) {
    errors.push("workspaceWeights must contain one positive integer per workspace and sum to 100");
  }
  const domains = config?.domains ?? [];
  const names = domains.map((domain) => domain.name);
  if (new Set(names).size !== names.length) {
    errors.push("seed domain names must be unique");
  }
  for (const required of requiredDomains) {
    if (!names.includes(required)) {
      errors.push(`required seed domain is missing: ${required}`);
    }
  }
  for (const domain of domains) {
    if (!requiredDomains.includes(domain.name)
      || !Number.isInteger(domain.count) || domain.count <= 0
      || !Number.isInteger(domain.payloadBytes) || domain.payloadBytes < 0
      || !Number.isInteger(domain.batchSize) || domain.batchSize <= 0
      || domain.batchSize > domain.count) {
      errors.push(`invalid seed domain: ${domain.name ?? "unknown"}`);
    }
  }
  const workspace = domains.find((domain) => domain.name === "workspace");
  if (workspace && workspace.count !== config.workspaceCount) {
    errors.push("workspace domain count must equal workspaceCount");
  }
  for (const domainName of [
    "member",
    "permission",
    "project",
    "issue",
    "knowledge-item",
    "knowledge-block",
    "notification",
    "im-message",
    "file"
  ]) {
    const domain = domains.find((candidate) => candidate.name === domainName);
    if (domain && domain.count % 100 !== 0) {
      errors.push(`${domainName} count must be divisible by 100 to preserve workspace weights`);
    }
  }
  const memberCount = domains.find((domain) => domain.name === "member")?.count ?? 0;
  const permissionCount = domains.find((domain) => domain.name === "permission")?.count ?? 0;
  if (memberCount > 0 && permissionCount !== memberCount * 3) {
    errors.push("permission count must equal member count * 3");
  }
  for (const mixName of ["permissionMix", "temperatureMix"]) {
    const mix = config?.[mixName];
    if (!mix || Object.values(mix).some((value) => typeof value !== "number" || value < 0)
      || Math.abs(Object.values(mix).reduce((sum, value) => sum + value, 0) - 1) > 1e-9) {
      errors.push(`${mixName} values must be non-negative and sum to 1`);
    }
  }
  if (!config?.relationships || Object.keys(config.relationships).length < 8
    || Object.values(config.relationships).some((value) => typeof value !== "string" || value.length === 0)) {
    errors.push("relationships must describe all cross-entity fixture links");
  }
  return { ok: errors.length === 0, errors };
}

export function createSeedPlan(seedId, config) {
  assertSafeName(seedId, "seedId");
  const validation = validateSeedConfig(config);
  if (!validation.ok) {
    throw new Error(`invalid seed config: ${validation.errors.join("; ")}`);
  }
  const workspaceIds = Array.from({ length: config.workspaceCount }, (_, index) => ({
    ordinal: index + 1,
    id: deterministicUuid(`${seedId}:workspace:${index + 1}`),
    weightPercent: config.workspaceWeights[index]
  }));
  const phases = config.domains.map((domain, index) => ({
    ordinal: index + 1,
    domain: domain.name,
    count: domain.count,
    payloadBytes: domain.payloadBytes,
    batchSize: domain.batchSize,
    phaseChecksum: sha256(stableStringify({
      seedId,
      domain: domain.name,
      count: domain.count,
      payloadBytes: domain.payloadBytes,
      batchSize: domain.batchSize,
      workspaceIds
    }))
  }));
  const unsigned = {
    schemaVersion: "colla.capacity-seed-plan/v1",
    seedSchemaVersion: config.schemaVersion,
    seedRevision: config.revision,
    seedId,
    fixtureName: `colla-capacity-${seedId}`,
    fixtureSchema: config.fixtureSchema,
    fixedTimestamp: new Date(config.fixedTimestamp).toISOString(),
    credentialSource: config.credentialSource,
    workspaceIds,
    workspaceWeights: config.workspaceWeights,
    permissionMix: config.permissionMix,
    temperatureMix: config.temperatureMix,
    relationships: config.relationships,
    phases,
    expectedRecordCount: phases.reduce((sum, phase) => sum + phase.count, 0),
    guarantees: {
      deterministic: true,
      idempotent: true,
      resumableByPhase: true,
      cleanupScope: "seedId+checksum",
      workspaceIsolation: "every non-workspace record references a workspace fixture owned by the same seed"
    }
  };
  return { ...unsigned, checksum: sha256(stableStringify(unsigned)) };
}

export function validateSeedPlan(plan) {
  const errors = [];
  if (plan?.schemaVersion !== "colla.capacity-seed-plan/v1") {
    errors.push("plan schemaVersion must be colla.capacity-seed-plan/v1");
  }
  try {
    assertSafeName(plan?.seedId, "seedId");
  } catch (error) {
    errors.push(error.message);
  }
  if (!/^[a-z][a-z0-9_]{0,62}$/.test(plan?.fixtureSchema ?? "")) {
    errors.push("plan fixtureSchema is unsafe");
  }
  if (plan?.credentialSource?.type !== "initialized-user-password-hash"
    || typeof plan?.credentialSource?.username !== "string"
    || plan?.credentialSource?.requiredStatus !== "active"
    || plan?.credentialSource?.requiredNotDeleted !== true
    || plan?.credentialSource?.outputFingerprint !== "md5-of-stored-password-hash") {
    errors.push("plan credentialSource is invalid");
  }
  if (!Array.isArray(plan?.workspaceIds) || plan.workspaceIds.length < 2
    || new Set(plan?.workspaceIds?.map((workspace) => workspace.id)).size !== plan?.workspaceIds?.length) {
    errors.push("plan must contain at least two unique workspaces");
  }
  if (!Array.isArray(plan?.phases) || plan.phases.length !== requiredDomains.length
    || requiredDomains.some((domain) => !plan?.phases?.some((phase) => phase.domain === domain))) {
    errors.push("plan does not contain the complete domain distribution");
  }
  const { checksum, ...unsigned } = plan ?? {};
  const expectedChecksum = sha256(stableStringify(unsigned));
  if (checksum !== expectedChecksum) {
    errors.push("plan checksum does not match plan content");
  }
  return { ok: errors.length === 0, errors, expectedChecksum };
}

export async function planSeed(seedId, options = {}) {
  const config = options.config ?? await readJson(options.configPath ?? defaultConfigPaths.seed);
  const plan = createSeedPlan(seedId, config);
  if (options.output) {
    await writeText(options.output, `${JSON.stringify(plan, null, 2)}\n`);
  }
  if (options.sqlOutput) {
    await writeText(options.sqlOutput, generateApplySql(plan));
  }
  return plan;
}

function countFor(plan, domain) {
  return plan.phases.find((phase) => phase.domain === domain)?.count;
}

function workspaceOrdinalExpression(plan, domain, seriesAlias = "g") {
  if (domain === "workspace") {
    return `${seriesAlias}`;
  }
  const percentile = countFor(plan, domain) < 100
    ? `floor(((${seriesAlias}) - 1) * 100.0 / ${countFor(plan, domain)})::integer`
    : `mod((${seriesAlias}) - 1, 100)`;
  let upper = 0;
  const branches = plan.workspaceWeights.map((weight, index) => {
    upper += weight;
    return `WHEN ${percentile} < ${upper} THEN ${index + 1}`;
  });
  return `(CASE ${branches.join(" ")} ELSE ${plan.workspaceIds.length} END)`;
}

function workspaceUuidExpression(plan, domain, seriesAlias = "g") {
  const ordinal = workspaceOrdinalExpression(plan, domain, seriesAlias);
  const branches = plan.workspaceIds.map(
    (workspace) => `WHEN ${workspace.ordinal} THEN ${sqlLiteral(workspace.id)}::uuid`
  );
  return `(CASE (${ordinal}) ${branches.join(" ")} END)`;
}

function workspaceContractOrdinalExpression(plan, workspaceExpression) {
  const branches = plan.workspaceIds.map(
    (workspace) => `WHEN ${sqlLiteral(workspace.id)}::uuid THEN ${workspace.ordinal}`
  );
  return `(CASE ${workspaceExpression} ${branches.join(" ")} END)`;
}

function workspaceOrdinalForFixtureOrdinal(plan, domain, ordinal) {
  if (domain === "workspace") return ordinal;
  const count = countFor(plan, domain);
  const percentile = count < 100
    ? Math.floor((ordinal - 1) * 100 / count)
    : (ordinal - 1) % 100;
  let upper = 0;
  for (let index = 0; index < plan.workspaceWeights.length; index += 1) {
    upper += plan.workspaceWeights[index];
    if (percentile < upper) return index + 1;
  }
  return plan.workspaceIds.length;
}

function firstFixtureOrdinalForWorkspace(plan, domain, workspaceOrdinal) {
  const count = countFor(plan, domain);
  for (let ordinal = 1; ordinal <= Math.min(count, 100); ordinal += 1) {
    if (workspaceOrdinalForFixtureOrdinal(plan, domain, ordinal) === workspaceOrdinal) {
      return ordinal;
    }
  }
  throw new Error(`domain ${domain} has no fixture row for workspace ${workspaceOrdinal}`);
}

function recordUuidExpression(plan, domain, ordinalExpression) {
  return `md5(${sqlLiteral(`${plan.seedId}:${domain}:`)} || (${ordinalExpression})::text)::uuid`;
}

function recordIdExpression(plan, domain, seriesAlias = "g") {
  return domain === "workspace"
    ? workspaceUuidExpression(plan, domain, seriesAlias)
    : recordUuidExpression(plan, domain, seriesAlias);
}

function localOrdinal(seriesAlias, count) {
  return `(1 + mod(${seriesAlias} - 1, ${count}))`;
}

function timestampExpression(plan, seriesAlias = "g") {
  return `(${sqlLiteral(plan.fixedTimestamp)}::timestamptz + ((${seriesAlias}) - 1) * interval '1 millisecond')`;
}

function domainPayloadSql(plan, domain, seriesAlias = "g") {
  const member = localOrdinal(seriesAlias, countFor(plan, "member"));
  const project = localOrdinal(seriesAlias, countFor(plan, "project"));
  const knowledgeItem = localOrdinal(seriesAlias, countFor(plan, "knowledge-item"));
  const fields = [];
  if (domain === "permission") {
    fields.push(
      `'memberFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${member}::text`,
      `'role', CASE WHEN mod(${seriesAlias} - 1, 100) < 1 THEN 'owner' WHEN mod(${seriesAlias} - 1, 100) < 5 THEN 'admin' WHEN mod(${seriesAlias} - 1, 100) < 75 THEN 'editor' WHEN mod(${seriesAlias} - 1, 100) < 95 THEN 'viewer' ELSE 'disabled' END`
    );
  } else if (domain === "project") {
    fields.push(`'ownerFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${member}::text`);
  } else if (domain === "issue") {
    fields.push(
      `'projectFixture', ${sqlLiteral(`${plan.fixtureName}:project:`)} || ${project}::text`,
      `'assigneeFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${member}::text`
    );
  } else if (domain === "knowledge-block") {
    fields.push(`'knowledgeItemFixture', ${sqlLiteral(`${plan.fixtureName}:knowledge-item:`)} || ${knowledgeItem}::text`);
  } else if (domain === "notification") {
    fields.push(`'recipientFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${member}::text`);
  } else if (domain === "im-message") {
    const recipient = `(1 + mod(${seriesAlias} + 99, ${countFor(plan, "member")}))`;
    fields.push(
      `'senderFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${member}::text`,
      `'recipientFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${recipient}::text`
    );
  } else if (domain === "file") {
    fields.push(`'ownerFixture', ${sqlLiteral(`${plan.fixtureName}:member:`)} || ${member}::text`);
  } else if (domain === "collaboration-room") {
    fields.push(`'knowledgeItemFixture', ${sqlLiteral(`${plan.fixtureName}:knowledge-item:`)} || ${knowledgeItem}::text`);
  }
  return fields.length > 0 ? `,\n    ${fields.join(",\n    ")}` : "";
}

function registrySql(plan) {
  const schema = plan.fixtureSchema;
  const progressRows = plan.phases.map((phase) => `(
    ${sqlLiteral(plan.seedId)},
    ${sqlLiteral(plan.checksum)},
    ${sqlLiteral(phase.domain)},
    ${phase.count},
    0,
    'pending'
  )`).join(",\n  ");
  return `CREATE SCHEMA IF NOT EXISTS ${schema};
CREATE TABLE IF NOT EXISTS ${schema}.fixture_runs (
  seed_id text PRIMARY KEY,
  checksum char(64) NOT NULL,
  fixture_name text NOT NULL,
  status text NOT NULL CHECK (status IN ('applying', 'applied')),
  completed_phase integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE IF NOT EXISTS ${schema}.fixture_records (
  seed_id text NOT NULL,
  checksum char(64) NOT NULL,
  workspace_id uuid NOT NULL,
  domain text NOT NULL,
  ordinal bigint NOT NULL,
  record_id uuid NOT NULL,
  fixture_key text NOT NULL,
  payload jsonb NOT NULL,
  PRIMARY KEY (seed_id, domain, ordinal),
  UNIQUE (seed_id, fixture_key)
);
CREATE INDEX IF NOT EXISTS fixture_records_workspace_domain_idx
  ON ${schema}.fixture_records (seed_id, workspace_id, domain);
CREATE INDEX IF NOT EXISTS fixture_records_seed_domain_record_idx
  ON ${schema}.fixture_records (seed_id, checksum, domain, record_id);
CREATE TABLE IF NOT EXISTS ${schema}.fixture_phase_progress (
  seed_id text NOT NULL,
  checksum char(64) NOT NULL,
  domain text NOT NULL,
  expected_count bigint NOT NULL,
  materialized_count bigint NOT NULL DEFAULT 0,
  status text NOT NULL CHECK (status IN ('pending', 'applying', 'applied')),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (seed_id, domain)
);
CREATE TABLE IF NOT EXISTS ${schema}.fixture_cleanup_progress (
  seed_id text NOT NULL,
  checksum char(64) NOT NULL,
  domain text NOT NULL,
  expected_count bigint NOT NULL,
  completed_ordinal bigint NOT NULL DEFAULT 0,
  status text NOT NULL CHECK (status IN ('pending', 'deleting', 'deleted')),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (seed_id, domain),
  CONSTRAINT fixture_cleanup_progress_domain_chk
    CHECK (domain IN (${cleanupDomains.map(sqlLiteral).join(", ")})),
  CONSTRAINT fixture_cleanup_progress_range_chk
    CHECK (
      expected_count >= 0
      AND completed_ordinal >= 0
      AND completed_ordinal <= expected_count
    ),
  CONSTRAINT fixture_cleanup_progress_status_chk
    CHECK (
      (status = 'pending' AND completed_ordinal = 0 AND expected_count > 0)
      OR (status = 'deleting' AND completed_ordinal < expected_count)
      OR (status = 'deleted' AND completed_ordinal = expected_count)
    )
);

INSERT INTO ${schema}.fixture_runs (seed_id, checksum, fixture_name, status, completed_phase)
VALUES (${sqlLiteral(plan.seedId)}, ${sqlLiteral(plan.checksum)}, ${sqlLiteral(plan.fixtureName)}, 'applying', 0)
ON CONFLICT (seed_id) DO NOTHING;

DO $capacity_fixture_guard$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM ${schema}.fixture_runs
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
      AND checksum = ${sqlLiteral(plan.checksum)}
      AND fixture_name = ${sqlLiteral(plan.fixtureName)}
  ) THEN
    RAISE EXCEPTION 'seed id already belongs to a different capacity fixture checksum';
  END IF;
END
$capacity_fixture_guard$;

INSERT INTO ${schema}.fixture_phase_progress (
  seed_id, checksum, domain, expected_count, materialized_count, status
)
VALUES
  ${progressRows}
ON CONFLICT (seed_id, domain) DO NOTHING;

DO $capacity_fixture_progress_guard$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM ${schema}.fixture_phase_progress
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
      AND (
        checksum <> ${sqlLiteral(plan.checksum)}
        OR expected_count <> CASE domain
          ${plan.phases.map((phase) => `WHEN ${sqlLiteral(phase.domain)} THEN ${phase.count}`).join("\n          ")}
          ELSE -1
        END
      )
  ) THEN
    RAISE EXCEPTION 'seed phase progress belongs to a different fixture contract';
  END IF;
END
$capacity_fixture_progress_guard$;
`;
}

function fixtureRolePrefix(plan) {
  return `cap_${createHash("sha256").update(plan.seedId).digest("hex").slice(0, 8)}_`;
}

function roleIdsValues(plan) {
  const roleNames = ["owner", "admin", "editor", "viewer", "disabled"];
  return plan.workspaceIds.flatMap((workspace) => roleNames.map((roleName) => `(
    ${workspace.ordinal},
    ${sqlLiteral(roleName)},
    md5(${sqlLiteral(`${plan.seedId}:role:${workspace.ordinal}:${roleName}`)})::uuid
  )`)).join(",\n    ");
}

function permissionRoleCase(plan, ordinalExpression) {
  const mix = plan.permissionMix;
  const ownerUpper = Math.round(mix.owner * 100);
  const adminUpper = ownerUpper + Math.round(mix.admin * 100);
  const editorUpper = adminUpper + Math.round(mix.editor * 100);
  const viewerUpper = editorUpper + Math.round(mix.viewer * 100);
  const percentile = `mod((${ordinalExpression}) - 1, 100)`;
  return `(CASE
    WHEN ${percentile} < ${ownerUpper} THEN 'owner'
    WHEN ${percentile} < ${adminUpper} THEN 'admin'
    WHEN ${percentile} < ${editorUpper} THEN 'editor'
    WHEN ${percentile} < ${viewerUpper} THEN 'viewer'
    ELSE 'disabled'
  END)`;
}

function phasePreludeSql(plan, phase) {
  const schema = plan.fixtureSchema;
  if (phase.domain === "permission") {
    const prefix = fixtureRolePrefix(plan);
    return `WITH role_seed(workspace_ordinal, role_name, role_id) AS (
  VALUES
    ${roleIdsValues(plan)}
),
workspace_fixture AS (
  SELECT ordinal::integer workspace_ordinal, record_id workspace_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'workspace'
)
INSERT INTO roles (
  id, workspace_id, code, name, scope, is_builtin, created_at, updated_at,
  description, status, updated_by
)
SELECT role_seed.role_id,
       workspace_fixture.workspace_id,
       ${sqlLiteral(prefix)} || role_seed.role_name,
       'Capacity ' || initcap(role_seed.role_name),
       'system',
       false,
       ${sqlLiteral(plan.fixedTimestamp)}::timestamptz,
       ${sqlLiteral(plan.fixedTimestamp)}::timestamptz,
       'Named capacity fixture role for ${plan.fixtureName}',
       CASE WHEN role_seed.role_name = 'disabled' THEN 'disabled' ELSE 'active' END,
       NULL
FROM role_seed
JOIN workspace_fixture USING (workspace_ordinal)
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  code = EXCLUDED.code,
  name = EXCLUDED.name,
  scope = EXCLUDED.scope,
  description = EXCLUDED.description,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at;

WITH role_seed(workspace_ordinal, role_name, role_id) AS (
  VALUES
    ${roleIdsValues(plan)}
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role_seed.role_id, permissions.id
FROM role_seed
JOIN permissions ON (
  role_seed.role_name IN ('owner', 'admin')
  OR (
    role_seed.role_name = 'editor'
    AND permissions.code IN (
      'project.create', 'project.manage', 'issue.create', 'issue.update',
      'doc.create', 'doc.update', 'base.create', 'base.update'
    )
  )
  OR (
    role_seed.role_name = 'viewer'
    AND permissions.code IN ('org.view', 'usergroup.view', 'role.view')
  )
)
WHERE role_seed.role_name <> 'disabled'
ON CONFLICT (role_id, permission_id) DO NOTHING;

WITH fixture_roles(workspace_ordinal, role_name, role_id) AS (
  VALUES
    ${roleIdsValues(plan)}
),
owned_roles AS (
  SELECT fixture_roles.*,
         roles.workspace_id,
         (fixture_roles.workspace_ordinal - 1) * 5
           + CASE fixture_roles.role_name
               WHEN 'owner' THEN 1 WHEN 'admin' THEN 2 WHEN 'editor' THEN 3
               WHEN 'viewer' THEN 4 ELSE 5
             END AS support_ordinal
  FROM fixture_roles
  JOIN roles ON roles.id = fixture_roles.role_id
)
INSERT INTO ${schema}.fixture_records (
  seed_id, checksum, workspace_id, domain, ordinal, record_id, fixture_key, payload
)
SELECT ${sqlLiteral(plan.seedId)},
       ${sqlLiteral(plan.checksum)},
       owned_roles.workspace_id,
       'support-role',
       owned_roles.support_ordinal,
       owned_roles.role_id,
       ${sqlLiteral(`${plan.fixtureName}:support-role:`)}
         || owned_roles.workspace_ordinal::text || ':' || owned_roles.role_name,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-role',
         'roleId', owned_roles.role_id,
         'roleName', owned_roles.role_name
       )
FROM owned_roles
ON CONFLICT (seed_id, domain, ordinal) DO UPDATE SET
  checksum = EXCLUDED.checksum,
  workspace_id = EXCLUDED.workspace_id,
  record_id = EXCLUDED.record_id,
  fixture_key = EXCLUDED.fixture_key,
  payload = EXCLUDED.payload
WHERE ${schema}.fixture_records.checksum = EXCLUDED.checksum
  AND ${schema}.fixture_records.fixture_key = EXCLUDED.fixture_key;

WITH owned_role_permissions AS (
  SELECT roles.workspace_id,
         role_permissions.role_id,
         role_permissions.permission_id,
         row_number() OVER (
           ORDER BY role_permissions.role_id, role_permissions.permission_id
         ) AS support_ordinal
  FROM role_permissions
  JOIN roles ON roles.id = role_permissions.role_id
  JOIN ${schema}.fixture_records fixture_role
    ON fixture_role.record_id = roles.id
   AND fixture_role.seed_id = ${sqlLiteral(plan.seedId)}
   AND fixture_role.checksum = ${sqlLiteral(plan.checksum)}
   AND fixture_role.domain = 'support-role'
)
INSERT INTO ${schema}.fixture_records (
  seed_id, checksum, workspace_id, domain, ordinal, record_id, fixture_key, payload
)
SELECT ${sqlLiteral(plan.seedId)},
       ${sqlLiteral(plan.checksum)},
       owned_role_permissions.workspace_id,
       'support-role-permission',
       owned_role_permissions.support_ordinal,
       md5(
         ${sqlLiteral(`${plan.seedId}:support-role-permission:`)}
         || owned_role_permissions.role_id::text || ':'
         || owned_role_permissions.permission_id::text
       )::uuid,
       ${sqlLiteral(`${plan.fixtureName}:support-role-permission:`)}
         || owned_role_permissions.role_id::text || ':'
         || owned_role_permissions.permission_id::text,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-role-permission',
         'roleId', owned_role_permissions.role_id,
         'permissionId', owned_role_permissions.permission_id
       )
FROM owned_role_permissions
ON CONFLICT (seed_id, domain, ordinal) DO UPDATE SET
  checksum = EXCLUDED.checksum,
  workspace_id = EXCLUDED.workspace_id,
  record_id = EXCLUDED.record_id,
  fixture_key = EXCLUDED.fixture_key,
  payload = EXCLUDED.payload
WHERE ${schema}.fixture_records.checksum = EXCLUDED.checksum
  AND ${schema}.fixture_records.fixture_key = EXCLUDED.fixture_key;`;
  }
  if (phase.domain === "im-message") {
    const memberCount = countFor(plan, "member");
    return `WITH project_fixture AS (
  SELECT ordinal, workspace_id, record_id project_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'project'
)
INSERT INTO conversations (
  id, workspace_id, conversation_type, title, owner_id, project_id,
  last_message_id, last_message_at, created_by, created_at, updated_at, archived_at
)
SELECT md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || ordinal::text)::uuid,
       workspace_id,
       'group',
       'Capacity project conversation ' || ordinal,
       ${recordUuidExpression(plan, "member", localOrdinal("ordinal", memberCount))},
       project_id,
       NULL,
       NULL,
       ${recordUuidExpression(plan, "member", localOrdinal("ordinal", memberCount))},
       ${timestampExpression(plan, "ordinal")},
       ${timestampExpression(plan, "ordinal")},
       NULL
FROM project_fixture
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  title = EXCLUDED.title,
  owner_id = EXCLUDED.owner_id,
  project_id = EXCLUDED.project_id,
  updated_at = EXCLUDED.updated_at;

WITH project_fixture AS (
  SELECT ordinal,
         workspace_id,
         md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || ordinal::text)::uuid conversation_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'project'
),
member_fixture AS (
  SELECT workspace_id, array_agg(record_id ORDER BY ordinal) member_ids
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'member'
  GROUP BY workspace_id
)
INSERT INTO conversation_members (
  id, workspace_id, conversation_id, user_id, member_role,
  last_read_message_id, last_read_at, joined_at, muted, archived_at, pinned_at
)
SELECT md5(
         ${sqlLiteral(`${plan.seedId}:conversation-member:`)}
         || project_fixture.ordinal::text || ':' || member_offset::text
       )::uuid,
       project_fixture.workspace_id,
       project_fixture.conversation_id,
       member_fixture.member_ids[
         1 + mod(project_fixture.ordinal - 1 + member_offset, array_length(member_fixture.member_ids, 1))
       ],
       CASE WHEN member_offset = 0 THEN 'owner' ELSE 'member' END,
       NULL,
       NULL,
       ${timestampExpression(plan, "project_fixture.ordinal")},
       false,
       NULL,
       NULL
FROM project_fixture
JOIN member_fixture USING (workspace_id)
CROSS JOIN generate_series(0, 1) AS offsets(member_offset)
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  conversation_id = EXCLUDED.conversation_id,
  user_id = EXCLUDED.user_id,
  member_role = EXCLUDED.member_role,
  joined_at = EXCLUDED.joined_at;

WITH project_fixture AS (
  SELECT ordinal, workspace_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'project'
),
owned_conversations AS (
  SELECT project_fixture.ordinal,
         project_fixture.workspace_id,
         conversations.id conversation_id
  FROM project_fixture
  JOIN conversations
    ON conversations.id = md5(
      ${sqlLiteral(`${plan.seedId}:conversation:`)} || project_fixture.ordinal::text
    )::uuid
)
INSERT INTO ${schema}.fixture_records (
  seed_id, checksum, workspace_id, domain, ordinal, record_id, fixture_key, payload
)
SELECT ${sqlLiteral(plan.seedId)},
       ${sqlLiteral(plan.checksum)},
       owned_conversations.workspace_id,
       'support-conversation',
       owned_conversations.ordinal,
       owned_conversations.conversation_id,
       ${sqlLiteral(`${plan.fixtureName}:support-conversation:`)}
         || owned_conversations.ordinal::text,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-conversation',
         'conversationId', owned_conversations.conversation_id
       )
FROM owned_conversations
ON CONFLICT (seed_id, domain, ordinal) DO UPDATE SET
  checksum = EXCLUDED.checksum,
  workspace_id = EXCLUDED.workspace_id,
  record_id = EXCLUDED.record_id,
  fixture_key = EXCLUDED.fixture_key,
  payload = EXCLUDED.payload
WHERE ${schema}.fixture_records.checksum = EXCLUDED.checksum
  AND ${schema}.fixture_records.fixture_key = EXCLUDED.fixture_key;

WITH project_fixture AS (
  SELECT ordinal, workspace_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'project'
),
owned_members AS (
  SELECT project_fixture.ordinal project_ordinal,
         project_fixture.workspace_id,
         member_offset,
         owned_member.id member_id,
         owned_member.conversation_id
  FROM project_fixture
  CROSS JOIN generate_series(0, 1) AS offsets(member_offset)
  CROSS JOIN LATERAL (
    SELECT conversation_members.id, conversation_members.conversation_id
    FROM conversation_members
    WHERE conversation_members.id = md5(
        ${sqlLiteral(`${plan.seedId}:conversation-member:`)}
        || project_fixture.ordinal::text || ':' || member_offset::text
      )::uuid
  ) owned_member
)
INSERT INTO ${schema}.fixture_records (
  seed_id, checksum, workspace_id, domain, ordinal, record_id, fixture_key, payload
)
SELECT ${sqlLiteral(plan.seedId)},
       ${sqlLiteral(plan.checksum)},
       owned_members.workspace_id,
       'support-conversation-member',
       (owned_members.project_ordinal - 1) * 2 + owned_members.member_offset + 1,
       owned_members.member_id,
       ${sqlLiteral(`${plan.fixtureName}:support-conversation-member:`)}
         || owned_members.project_ordinal::text || ':' || owned_members.member_offset::text,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-conversation-member',
         'conversationMemberId', owned_members.member_id,
         'conversationId', owned_members.conversation_id
       )
FROM owned_members
ON CONFLICT (seed_id, domain, ordinal) DO UPDATE SET
  checksum = EXCLUDED.checksum,
  workspace_id = EXCLUDED.workspace_id,
  record_id = EXCLUDED.record_id,
  fixture_key = EXCLUDED.fixture_key,
  payload = EXCLUDED.payload
WHERE ${schema}.fixture_records.checksum = EXCLUDED.checksum
  AND ${schema}.fixture_records.fixture_key = EXCLUDED.fixture_key;`;
  }
  return "";
}

function materializeWorkspaceSql(plan, start, end) {
  const schema = plan.fixtureSchema;
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'workspace'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO workspaces (id, name, slug, status, created_at, updated_at)
SELECT record_id,
       'Capacity Workspace ' || ordinal,
       'cap-${createHash("sha256").update(plan.seedId).digest("hex").slice(0, 10)}-' || ordinal,
       'active',
       ${timestampExpression(plan, "ordinal")},
       ${timestampExpression(plan, "ordinal")}
FROM batch
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  slug = EXCLUDED.slug,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at;`;
}

function materializeMemberSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const credentialSource = plan.credentialSource;
  const usernamePrefix = `cap_${createHash("sha256").update(plan.seedId).digest("hex").slice(0, 10)}_u`;
  return `DO $capacity_fixture_password_guard$
DECLARE
  source_count integer;
BEGIN
  SELECT count(*) INTO source_count
  FROM users
  WHERE username = ${sqlLiteral(credentialSource.username)}
    AND status = ${sqlLiteral(credentialSource.requiredStatus)}
    AND deleted_at IS NULL
    AND password_hash IS NOT NULL;
  IF source_count <> 1 THEN
    RAISE EXCEPTION 'capacity member seed requires exactly one active initialized credential source';
  END IF;
END
$capacity_fixture_password_guard$;

WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'member'
    AND ordinal BETWEEN ${start} AND ${end}
),
password_source AS (
  SELECT password_hash
  FROM users
  WHERE username = ${sqlLiteral(credentialSource.username)}
    AND status = ${sqlLiteral(credentialSource.requiredStatus)}
    AND deleted_at IS NULL
    AND password_hash IS NOT NULL
)
INSERT INTO users (
  id, workspace_id, username, password_hash, display_name, avatar_file_id,
  email, phone, department, status, last_login_at, created_by, created_at,
  updated_by, updated_at, deleted_at
)
SELECT batch.record_id,
       batch.workspace_id,
       ${sqlLiteral(usernamePrefix)} || lpad(batch.ordinal::text, 7, '0'),
       password_source.password_hash,
       'Capacity Member ' || batch.ordinal,
       NULL,
       ${sqlLiteral(usernamePrefix)} || lpad(batch.ordinal::text, 7, '0') || '@capacity.invalid',
       NULL,
       'Capacity',
       CASE WHEN mod(batch.ordinal - 1, 100) >= 95 THEN 'disabled' ELSE 'active' END,
       NULL,
       NULL,
       ${timestampExpression(plan, "batch.ordinal")},
       NULL,
       ${timestampExpression(plan, "batch.ordinal")},
       NULL
FROM batch
CROSS JOIN password_source
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  username = EXCLUDED.username,
  password_hash = EXCLUDED.password_hash,
  display_name = EXCLUDED.display_name,
  email = EXCLUDED.email,
  department = EXCLUDED.department,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at;`;
}

function materializePermissionSql(plan, start, end) {
  const schema = plan.fixtureSchema;
  const memberCount = countFor(plan, "member");
  const roleName = permissionRoleCase(plan, "batch.ordinal");
  const memberOrdinal = localOrdinal("batch.ordinal", memberCount);
  const collaborationPermissionStart = memberCount * 2 + 1;
  const collaborationPermissionEnd = collaborationPermissionStart + 19;
  const collaborationMemberOrdinal =
    `(1 + (batch.ordinal - ${collaborationPermissionStart}) / 4)`;
  const collaborationItemOrdinal =
    `(2 + mod(batch.ordinal - ${collaborationPermissionStart}, 4))`;
  const effectiveMemberOrdinal = `(CASE
    WHEN batch.ordinal BETWEEN ${collaborationPermissionStart} AND ${collaborationPermissionEnd}
      THEN ${collaborationMemberOrdinal}
    ELSE ${memberOrdinal}
  END)`;
  return `WITH batch AS (
  SELECT records.*, workspace.ordinal::integer workspace_ordinal
  FROM ${schema}.fixture_records records
  JOIN ${schema}.fixture_records workspace
    ON workspace.seed_id = records.seed_id
   AND workspace.checksum = records.checksum
   AND workspace.domain = 'workspace'
   AND workspace.record_id = records.workspace_id
  WHERE records.seed_id = ${sqlLiteral(plan.seedId)}
    AND records.checksum = ${sqlLiteral(plan.checksum)}
    AND records.domain = 'permission'
    AND records.ordinal BETWEEN ${start} AND ${end}
    AND records.ordinal <= ${memberCount}
),
role_seed(workspace_ordinal, role_name, role_id) AS (
  VALUES
    ${roleIdsValues(plan)}
)
INSERT INTO role_assignments (
  id, workspace_id, role_id, subject_type, subject_id, scope_type, scope_id,
  effective_at, expires_at, status, created_by, created_at, revoked_by, revoked_at
)
SELECT batch.record_id,
       batch.workspace_id,
       role_seed.role_id,
       'user',
       ${recordUuidExpression(plan, "member", "batch.ordinal")},
       'system',
       NULL,
       ${timestampExpression(plan, "batch.ordinal")},
       NULL,
       CASE WHEN role_seed.role_name = 'disabled' THEN 'revoked' ELSE 'active' END,
       ${recordUuidExpression(plan, "member", "batch.ordinal")},
       ${timestampExpression(plan, "batch.ordinal")},
       CASE WHEN role_seed.role_name = 'disabled'
         THEN ${recordUuidExpression(plan, "member", "batch.ordinal")} ELSE NULL END,
       CASE WHEN role_seed.role_name = 'disabled'
         THEN ${timestampExpression(plan, "batch.ordinal")} ELSE NULL END
FROM batch
JOIN role_seed
  ON role_seed.workspace_ordinal = batch.workspace_ordinal
 AND role_seed.role_name = ${roleName}
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  role_id = EXCLUDED.role_id,
  subject_id = EXCLUDED.subject_id,
  status = EXCLUDED.status,
  revoked_by = EXCLUDED.revoked_by,
  revoked_at = EXCLUDED.revoked_at;

WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'permission'
    AND ordinal BETWEEN ${start} AND ${end}
    AND ordinal > ${memberCount}
),
permission_targets AS (
  SELECT batch.*,
         ${effectiveMemberOrdinal} member_ordinal,
         CASE WHEN batch.ordinal <= ${memberCount * 2}
           THEN 'project' ELSE 'knowledge_content' END resource_type,
         CASE WHEN batch.ordinal <= ${memberCount * 2}
           THEN ${recordUuidExpression(plan, "project", localOrdinal("batch.ordinal", countFor(plan, "project")))}
           WHEN batch.ordinal BETWEEN ${collaborationPermissionStart} AND ${collaborationPermissionEnd}
           THEN ${recordUuidExpression(plan, "knowledge-item", collaborationItemOrdinal)}
           ELSE ${recordUuidExpression(plan, "knowledge-item", localOrdinal("batch.ordinal", countFor(plan, "knowledge-item")))}
         END resource_id,
         CASE
           WHEN batch.ordinal BETWEEN ${collaborationPermissionStart} AND ${collaborationPermissionEnd}
             THEN 'editor'
           ELSE ${permissionRoleCase(plan, effectiveMemberOrdinal)}
         END role_name
  FROM batch
)
INSERT INTO resource_permissions (
  id, workspace_id, resource_type, resource_id, subject_type, subject_id,
  permission_level, source_type, source_id, expires_at, status,
  created_by, created_at, updated_by, updated_at
)
SELECT record_id,
       workspace_id,
       resource_type,
       resource_id,
       'user',
       ${recordUuidExpression(plan, "member", "member_ordinal")},
       CASE role_name
         WHEN 'owner' THEN 'owner'
         WHEN 'admin' THEN 'manage'
         WHEN 'editor' THEN 'edit'
         WHEN 'viewer' THEN 'view'
         ELSE 'view'
       END,
       'direct',
       NULL,
       NULL,
       CASE WHEN role_name = 'disabled' THEN 'revoked' ELSE 'active' END,
       ${recordUuidExpression(plan, "member", "member_ordinal")},
       ${timestampExpression(plan, "ordinal")},
       ${recordUuidExpression(plan, "member", "member_ordinal")},
       ${timestampExpression(plan, "ordinal")}
FROM permission_targets
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  resource_type = EXCLUDED.resource_type,
  resource_id = EXCLUDED.resource_id,
  subject_id = EXCLUDED.subject_id,
  permission_level = EXCLUDED.permission_level,
  status = EXCLUDED.status,
  updated_by = EXCLUDED.updated_by,
  updated_at = EXCLUDED.updated_at;`;
}

function materializeProjectSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const memberOrdinal = localOrdinal("ordinal", countFor(plan, "member"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'project'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO projects (
  id, workspace_id, project_key, name, description, status, conversation_id,
  created_by, created_at, updated_by, updated_at, archived_at
)
SELECT record_id,
       workspace_id,
       'CAP' || lpad(ordinal::text, 7, '0'),
       'Capacity Project ' || ordinal,
       repeat('p', ${phase.payloadBytes}),
       'active',
       NULL,
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       NULL
FROM batch
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  project_key = EXCLUDED.project_key,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  status = EXCLUDED.status,
  updated_by = EXCLUDED.updated_by,
  updated_at = EXCLUDED.updated_at;`;
}

function materializeIssueSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const projectOrdinal = localOrdinal("ordinal", countFor(plan, "project"));
  const memberOrdinal = localOrdinal("ordinal", countFor(plan, "member"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'issue'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO issues (
  id, workspace_id, project_id, issue_key, issue_type, title, description,
  priority, status, assignee_id, reporter_id, iteration_id, due_at,
  created_by, created_at, updated_by, updated_at, deleted_at,
  workflow_reason, workflow_note, resolution, resolved_at, closed_at
)
SELECT record_id,
       workspace_id,
       ${recordUuidExpression(plan, "project", projectOrdinal)},
       'I' || lpad(ordinal::text, 9, '0'),
       CASE WHEN mod(ordinal, 10) = 0 THEN 'bug' ELSE 'task' END,
       'Capacity issue ' || ordinal,
       repeat('i', ${phase.payloadBytes}),
       CASE mod(ordinal, 4) WHEN 0 THEN 'urgent' WHEN 1 THEN 'high' WHEN 2 THEN 'medium' ELSE 'low' END,
       CASE mod(ordinal, 5) WHEN 0 THEN 'done' WHEN 1 THEN 'in_progress' ELSE 'open' END,
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       NULL,
       (${sqlLiteral(plan.fixedTimestamp)}::date + mod(ordinal, 365)::integer),
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       NULL,
       NULL,
       NULL,
       NULL,
       NULL,
       NULL
FROM batch
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  project_id = EXCLUDED.project_id,
  issue_key = EXCLUDED.issue_key,
  title = EXCLUDED.title,
  description = EXCLUDED.description,
  priority = EXCLUDED.priority,
  status = EXCLUDED.status,
  assignee_id = EXCLUDED.assignee_id,
  reporter_id = EXCLUDED.reporter_id,
  updated_by = EXCLUDED.updated_by,
  updated_at = EXCLUDED.updated_at;`;
}

function materializeKnowledgeItemSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const memberOrdinal = localOrdinal("batch.ordinal", countFor(plan, "member"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'knowledge-item'
    AND ordinal BETWEEN ${start} AND ${end}
),
roots AS (
  SELECT DISTINCT ON (workspace_id) workspace_id, record_id root_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'knowledge-item'
  ORDER BY workspace_id, ordinal
)
INSERT INTO knowledge_base_items (
  id, workspace_id, parent_id, title, content_type, current_version_no, status,
  created_by, created_at, updated_by, updated_at, deleted_at, sort_order,
  archived_at, description, cover_url, default_permission_level, knowledge_base,
  maintainer_id, tags, category, knowledge_status, review_due_at, verified_at,
  review_notified_at, item_kind, target_object_type, target_object_id,
  target_route, display_mode, target_title_strategy, entry_alias,
  collaboration_generation
)
SELECT batch.record_id,
       batch.workspace_id,
       CASE WHEN batch.record_id = roots.root_id THEN NULL ELSE roots.root_id END,
       CASE WHEN batch.record_id = roots.root_id
         THEN 'Capacity Knowledge Root'
         ELSE 'Capacity Knowledge Item ' || batch.ordinal END,
       CASE WHEN batch.record_id = roots.root_id THEN 'space' ELSE 'markdown' END,
       1,
       'active',
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "batch.ordinal")},
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "batch.ordinal")},
       NULL,
       batch.ordinal::integer,
       NULL,
       left(repeat('k', ${phase.payloadBytes}), 512),
       NULL,
       'view',
       batch.record_id = roots.root_id,
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ARRAY['capacity', CASE mod(batch.ordinal, 3) WHEN 0 THEN 'hot' WHEN 1 THEN 'warm' ELSE 'cold' END],
       'capacity',
       CASE WHEN mod(batch.ordinal, 10) = 0 THEN 'verified' ELSE 'draft' END,
       NULL,
       CASE WHEN mod(batch.ordinal, 10) = 0 THEN ${timestampExpression(plan, "batch.ordinal")} ELSE NULL END,
       NULL,
       CASE WHEN batch.record_id = roots.root_id THEN 'directory' ELSE 'content' END,
       NULL,
       NULL,
       NULL,
       'default',
       'manual',
       NULL,
       0
FROM batch
JOIN roots USING (workspace_id)
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  parent_id = EXCLUDED.parent_id,
  title = EXCLUDED.title,
  content_type = EXCLUDED.content_type,
  status = EXCLUDED.status,
  updated_by = EXCLUDED.updated_by,
  updated_at = EXCLUDED.updated_at,
  description = EXCLUDED.description,
  knowledge_base = EXCLUDED.knowledge_base,
  item_kind = EXCLUDED.item_kind;`;
}

function materializeKnowledgeBlockSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const itemOrdinal = localOrdinal("ordinal", countFor(plan, "knowledge-item"));
  const memberOrdinal = localOrdinal("ordinal", countFor(plan, "member"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'knowledge-block'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO knowledge_content_blocks (
  id, workspace_id, item_id, block_type, content, sort_order, created_by,
  created_at, updated_by, updated_at, deleted_at, schema_version, attrs,
  rich_content, plain_text, parent_id, anchor_id, block_version
)
SELECT record_id,
       workspace_id,
       ${recordUuidExpression(plan, "knowledge-item", itemOrdinal)},
       CASE WHEN mod(ordinal, 20) = 0 THEN 'heading' ELSE 'paragraph' END,
       repeat('b', ${phase.payloadBytes}),
       ((ordinal - 1) / ${countFor(plan, "knowledge-item")})::integer,
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       NULL,
       2,
       jsonb_build_object('fixture', ${sqlLiteral(plan.fixtureName)}, 'ordinal', ordinal),
       jsonb_build_object('type', 'text', 'text', left(repeat('b', ${phase.payloadBytes}), 128)),
       left(repeat('b', ${phase.payloadBytes}), 128),
       NULL,
       'capacity-' || ordinal,
       1
FROM batch
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  item_id = EXCLUDED.item_id,
  block_type = EXCLUDED.block_type,
  content = EXCLUDED.content,
  sort_order = EXCLUDED.sort_order,
  updated_by = EXCLUDED.updated_by,
  updated_at = EXCLUDED.updated_at,
  attrs = EXCLUDED.attrs,
  rich_content = EXCLUDED.rich_content,
  plain_text = EXCLUDED.plain_text;`;
}

function materializeNotificationSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const memberOrdinal = localOrdinal("ordinal", countFor(plan, "member"));
  const issueOrdinal = localOrdinal("ordinal", countFor(plan, "issue"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'notification'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO notifications (
  id, workspace_id, recipient_id, actor_id, notification_type, title, body,
  target_type, target_id, web_path, dedupe_key, read_at, created_at
)
SELECT record_id,
       workspace_id,
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       'capacity.issue.updated',
       'Capacity notification ' || ordinal,
       repeat('n', ${phase.payloadBytes}),
       'issue',
       ${recordUuidExpression(plan, "issue", issueOrdinal)},
       '/issues/' || ${recordUuidExpression(plan, "issue", issueOrdinal)}::text,
       ${sqlLiteral(`${plan.fixtureName}:notification:`)} || ordinal,
       CASE WHEN mod(ordinal, 4) = 0 THEN ${timestampExpression(plan, "ordinal")} ELSE NULL END,
       ${timestampExpression(plan, "ordinal")}
FROM batch
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  recipient_id = EXCLUDED.recipient_id,
  actor_id = EXCLUDED.actor_id,
  title = EXCLUDED.title,
  body = EXCLUDED.body,
  target_id = EXCLUDED.target_id,
  dedupe_key = EXCLUDED.dedupe_key,
  read_at = EXCLUDED.read_at,
  created_at = EXCLUDED.created_at;`;
}

function materializeMessageSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const projectOrdinal = localOrdinal("ordinal", countFor(plan, "project"));
  const memberOrdinal = localOrdinal("ordinal", countFor(plan, "member"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'im-message'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO messages (
  id, workspace_id, conversation_id, sender_id, client_message_id,
  message_type, content, reply_to_message_id, created_at, deleted_at,
  edited_at, revoked_at, pinned_at, pinned_by
)
SELECT record_id,
       workspace_id,
       md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || (${projectOrdinal})::text)::uuid,
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${sqlLiteral(`${plan.fixtureName}:message:`)} || ordinal,
       'text',
       repeat('m', ${phase.payloadBytes}),
       NULL,
       ${timestampExpression(plan, "ordinal")},
       NULL,
       NULL,
       NULL,
       NULL,
       NULL
FROM batch
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  conversation_id = EXCLUDED.conversation_id,
  sender_id = EXCLUDED.sender_id,
  client_message_id = EXCLUDED.client_message_id,
  content = EXCLUDED.content,
  created_at = EXCLUDED.created_at;`;
}

function materializeFileSql(plan, start, end, phase) {
  const schema = plan.fixtureSchema;
  const memberOrdinal = localOrdinal("ordinal", countFor(plan, "member"));
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'file'
    AND ordinal BETWEEN ${start} AND ${end}
)
INSERT INTO files (
  id, workspace_id, object_key, original_name, content_type, size_bytes,
  status, uploaded_by, created_at, completed_at, deleted_at
)
SELECT record_id,
       workspace_id,
       'capacity/${createHash("sha256").update(plan.seedId).digest("hex").slice(0, 12)}/' || ordinal,
       'capacity-file-' || ordinal || '.bin',
       'application/octet-stream',
       ${phase.payloadBytes},
       'completed',
       ${recordUuidExpression(plan, "member", memberOrdinal)},
       ${timestampExpression(plan, "ordinal")},
       ${timestampExpression(plan, "ordinal")},
       NULL
FROM batch
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  object_key = EXCLUDED.object_key,
  original_name = EXCLUDED.original_name,
  size_bytes = EXCLUDED.size_bytes,
  status = EXCLUDED.status,
  uploaded_by = EXCLUDED.uploaded_by,
  completed_at = EXCLUDED.completed_at;`;
}

function materializeCollaborationSql(plan, start, end) {
  const schema = plan.fixtureSchema;
  return `WITH batch AS (
  SELECT *
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'collaboration-room'
    AND ordinal BETWEEN ${start} AND ${end}
),
item_fixture AS (
  SELECT workspace_id, array_agg(record_id ORDER BY ordinal) item_ids
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'knowledge-item'
  GROUP BY workspace_id
)
INSERT INTO knowledge_content_collaboration_states (
  id, workspace_id, item_id, created_at, updated_at, schema_version,
  canonical_snapshot, yjs_snapshot, yjs_state_vector, snapshot_sequence,
  snapshot_hash, last_audited_at, generation
)
SELECT batch.record_id,
       batch.workspace_id,
       item_fixture.item_ids[
         1 + mod(batch.ordinal - 1, array_length(item_fixture.item_ids, 1))
       ],
       ${timestampExpression(plan, "batch.ordinal")},
       ${timestampExpression(plan, "batch.ordinal")},
       3,
       jsonb_build_object('schemaVersion', 3, 'blocks', jsonb_build_array()),
       decode('0000', 'hex'),
       decode('00', 'hex'),
       0,
       ${sqlLiteral(plan.checksum)},
       ${timestampExpression(plan, "batch.ordinal")},
       0
FROM batch
JOIN item_fixture USING (workspace_id)
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  item_id = EXCLUDED.item_id,
  updated_at = EXCLUDED.updated_at,
  schema_version = EXCLUDED.schema_version,
  canonical_snapshot = EXCLUDED.canonical_snapshot,
  yjs_snapshot = EXCLUDED.yjs_snapshot,
  yjs_state_vector = EXCLUDED.yjs_state_vector,
  snapshot_hash = EXCLUDED.snapshot_hash,
  last_audited_at = EXCLUDED.last_audited_at,
  generation = EXCLUDED.generation;`;
}

function materializePhaseSql(plan, phase, start, end) {
  switch (phase.domain) {
    case "workspace":
      return materializeWorkspaceSql(plan, start, end);
    case "member":
      return materializeMemberSql(plan, start, end, phase);
    case "permission":
      return materializePermissionSql(plan, start, end);
    case "project":
      return materializeProjectSql(plan, start, end, phase);
    case "issue":
      return materializeIssueSql(plan, start, end, phase);
    case "knowledge-item":
      return materializeKnowledgeItemSql(plan, start, end, phase);
    case "knowledge-block":
      return materializeKnowledgeBlockSql(plan, start, end, phase);
    case "notification":
      return materializeNotificationSql(plan, start, end, phase);
    case "im-message":
      return materializeMessageSql(plan, start, end, phase);
    case "file":
      return materializeFileSql(plan, start, end, phase);
    case "collaboration-room":
      return materializeCollaborationSql(plan, start, end);
    default:
      throw new Error(`unsupported seed domain: ${phase.domain}`);
  }
}

function phaseFinalizeSql(plan, phase) {
  const schema = plan.fixtureSchema;
  if (phase.domain === "knowledge-item") {
    return `WITH roots AS (
  SELECT DISTINCT ON (workspace_id)
         workspace_id,
         record_id root_item_id,
         ordinal workspace_ordinal
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'knowledge-item'
  ORDER BY workspace_id, ordinal
),
owners AS (
  SELECT DISTINCT ON (workspace_id) workspace_id, record_id owner_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'member'
  ORDER BY workspace_id, ordinal
)
INSERT INTO knowledge_base_spaces (
  id, workspace_id, name, code, description, icon, cover_url, status,
  visibility, root_item_id, home_item_id, owner_id, default_permission_level,
  created_by, created_at, updated_by, updated_at, deleted_at
)
SELECT md5(${sqlLiteral(`${plan.seedId}:knowledge-space:`)} || roots.workspace_id::text)::uuid,
       roots.workspace_id,
       'Capacity Knowledge Space',
       'cap-${createHash("sha256").update(plan.seedId).digest("hex").slice(0, 10)}-'
         || ${workspaceContractOrdinalExpression(plan, "roots.workspace_id")}::text,
       'Named capacity fixture knowledge space',
       'book',
       NULL,
       'active',
       'workspace',
       roots.root_item_id,
       roots.root_item_id,
       owners.owner_id,
       'view',
       owners.owner_id,
       ${sqlLiteral(plan.fixedTimestamp)}::timestamptz,
       owners.owner_id,
       ${sqlLiteral(plan.fixedTimestamp)}::timestamptz,
       NULL
FROM roots
JOIN owners USING (workspace_id)
ON CONFLICT (id) DO UPDATE SET
  workspace_id = EXCLUDED.workspace_id,
  name = EXCLUDED.name,
  code = EXCLUDED.code,
  root_item_id = EXCLUDED.root_item_id,
  home_item_id = EXCLUDED.home_item_id,
  owner_id = EXCLUDED.owner_id,
  updated_by = EXCLUDED.updated_by,
  updated_at = EXCLUDED.updated_at;

WITH owned_spaces AS (
  SELECT ${workspaceContractOrdinalExpression(plan, "spaces.workspace_id")} workspace_ordinal,
         spaces.workspace_id,
         spaces.id space_id,
         spaces.root_item_id
  FROM (
    SELECT DISTINCT ON (workspace_id)
           workspace_id,
           record_id root_item_id,
           ordinal workspace_ordinal
    FROM ${schema}.fixture_records
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
      AND checksum = ${sqlLiteral(plan.checksum)}
      AND domain = 'knowledge-item'
    ORDER BY workspace_id, ordinal
  ) roots
  JOIN knowledge_base_spaces spaces
    ON spaces.id = md5(
      ${sqlLiteral(`${plan.seedId}:knowledge-space:`)} || roots.workspace_id::text
    )::uuid
)
INSERT INTO ${schema}.fixture_records (
  seed_id, checksum, workspace_id, domain, ordinal, record_id, fixture_key, payload
)
SELECT ${sqlLiteral(plan.seedId)},
       ${sqlLiteral(plan.checksum)},
       owned_spaces.workspace_id,
       'support-knowledge-space',
       owned_spaces.workspace_ordinal,
       owned_spaces.space_id,
       ${sqlLiteral(`${plan.fixtureName}:support-knowledge-space:`)}
         || owned_spaces.workspace_ordinal::text,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-knowledge-space',
         'spaceId', owned_spaces.space_id,
         'rootItemId', owned_spaces.root_item_id
       )
FROM owned_spaces
ON CONFLICT (seed_id, domain, ordinal) DO UPDATE SET
  checksum = EXCLUDED.checksum,
  workspace_id = EXCLUDED.workspace_id,
  record_id = EXCLUDED.record_id,
  fixture_key = EXCLUDED.fixture_key,
  payload = EXCLUDED.payload
WHERE ${schema}.fixture_records.checksum = EXCLUDED.checksum
  AND ${schema}.fixture_records.fixture_key = EXCLUDED.fixture_key;`;
  }
  if (phase.domain === "im-message") {
    return `WITH fixture_conversations AS (
  SELECT md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || ordinal::text)::uuid conversation_id
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
    AND domain = 'project'
),
latest AS (
  SELECT DISTINCT ON (messages.conversation_id)
         messages.conversation_id,
         messages.id last_message_id,
         messages.created_at last_message_at
  FROM messages
  JOIN fixture_conversations USING (conversation_id)
  ORDER BY messages.conversation_id, messages.message_seq DESC
)
UPDATE conversations
SET last_message_id = latest.last_message_id,
    last_message_at = latest.last_message_at,
    updated_at = latest.last_message_at
FROM latest
WHERE conversations.id = latest.conversation_id;`;
  }
  return "";
}

function registryChunkSql(plan, phase, start, end) {
  const schema = plan.fixtureSchema;
  const workspaceId = workspaceUuidExpression(plan, phase.domain);
  const recordId = recordIdExpression(plan, phase.domain);
  const fixturePrefix = `${plan.fixtureName}:${phase.domain}:`;
  return `INSERT INTO ${schema}.fixture_records (
  seed_id, checksum, workspace_id, domain, ordinal, record_id, fixture_key, payload
)
SELECT
  ${sqlLiteral(plan.seedId)},
  ${sqlLiteral(plan.checksum)},
  ${workspaceId},
  ${sqlLiteral(phase.domain)},
  g,
  ${recordId},
  ${sqlLiteral(fixturePrefix)} || g::text,
  jsonb_build_object(
    'fixture', ${sqlLiteral(plan.fixtureName)},
    'domain', ${sqlLiteral(phase.domain)},
    'ordinal', g,
    'payloadBytes', ${phase.payloadBytes},
    'temperature', CASE WHEN mod(g - 1, 10) = 0 THEN 'hot' WHEN mod(g - 1, 10) < 4 THEN 'warm' ELSE 'cold' END${domainPayloadSql(plan, phase.domain)}
  )
FROM generate_series(${start}, ${end}) AS generated(g)
ON CONFLICT (seed_id, domain, ordinal) DO UPDATE SET
  checksum = EXCLUDED.checksum,
  workspace_id = EXCLUDED.workspace_id,
  record_id = EXCLUDED.record_id,
  fixture_key = EXCLUDED.fixture_key,
  payload = EXCLUDED.payload
WHERE ${schema}.fixture_records.checksum = EXCLUDED.checksum
  AND ${schema}.fixture_records.fixture_key = EXCLUDED.fixture_key;`;
}

export function generateApplySql(plan) {
  const validation = validateSeedPlan(plan);
  if (!validation.ok) {
    throw new Error(`invalid seed plan: ${validation.errors.join("; ")}`);
  }
  const schema = plan.fixtureSchema;
  const sections = [
    "-- Generated by @colla/capacity. Business rows are deterministic; each committed chunk is resumable.",
    "\\set ON_ERROR_STOP on",
    registrySql(plan)
  ];
  for (const phase of plan.phases) {
    const prelude = phasePreludeSql(plan, phase);
    if (prelude) {
      sections.push(`-- phase ${phase.ordinal} prelude: ${phase.domain}
BEGIN;
${prelude}
COMMIT;
`);
    }
    for (let start = 1; start <= phase.count; start += phase.batchSize) {
      const end = Math.min(phase.count, start + phase.batchSize - 1);
      sections.push(`-- phase ${phase.ordinal}: ${phase.domain}, chunk ${start}-${end}
SELECT (materialized_count >= ${end}) AS capacity_skip_chunk
FROM ${schema}.fixture_phase_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(phase.domain)}
\\gset
\\if :capacity_skip_chunk
\\echo 'skip committed ${phase.domain} chunk ${start}-${end}'
\\else
BEGIN;
UPDATE ${schema}.fixture_phase_progress
SET status = 'applying', updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(phase.domain)};

${registryChunkSql(plan, phase, start, end)}

${materializePhaseSql(plan, phase, start, end)}

UPDATE ${schema}.fixture_phase_progress
SET materialized_count = ${end},
    updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(phase.domain)}
  AND materialized_count < ${end};
COMMIT;
\\endif
`);
    }
    const finalize = phaseFinalizeSql(plan, phase);
    sections.push(`-- phase ${phase.ordinal} finalization: ${phase.domain}
BEGIN;
${finalize}
UPDATE ${schema}.fixture_phase_progress
SET status = 'applied', materialized_count = ${phase.count}, updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(phase.domain)}
  AND materialized_count = ${phase.count};

UPDATE ${schema}.fixture_runs
SET completed_phase = GREATEST(completed_phase, ${phase.ordinal}),
    updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND EXISTS (
    SELECT 1
    FROM ${schema}.fixture_phase_progress
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
      AND checksum = ${sqlLiteral(plan.checksum)}
      AND domain = ${sqlLiteral(phase.domain)}
      AND status = 'applied'
      AND materialized_count = ${phase.count}
  );
COMMIT;
`);
  }
  sections.push(`UPDATE ${schema}.fixture_runs
SET status = 'applied', updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND completed_phase = ${plan.phases.length};
`);
  return `${sections.join("\n")}\n`;
}

function supportRolePermissionExpectedCountSql(plan) {
  return `(
      SELECT count(*)::bigint
      FROM (VALUES ${roleIdsValues(plan)}) AS role_seed(workspace_ordinal, role_name, role_id)
      JOIN permissions ON (
        role_seed.role_name IN ('owner', 'admin')
        OR (
          role_seed.role_name = 'editor'
          AND permissions.code IN (
            'project.create', 'project.manage', 'issue.create', 'issue.update',
            'doc.create', 'doc.update', 'base.create', 'base.update'
          )
        )
        OR (
          role_seed.role_name = 'viewer'
          AND permissions.code IN ('org.view', 'usergroup.view', 'role.view')
        )
      )
      WHERE role_seed.role_name <> 'disabled'
    )`;
}

function supportExpectedRowsSql(plan) {
  return [
    `('support-role', ${plan.workspaceIds.length * 5}::bigint)`,
    `('support-role-permission', ${supportRolePermissionExpectedCountSql(plan)})`,
    `('support-conversation', ${countFor(plan, "project")}::bigint)`,
    `('support-conversation-member', ${countFor(plan, "project") * 2}::bigint)`,
    `('support-knowledge-space', ${plan.workspaceIds.length}::bigint)`
  ];
}

export function generateVerifySql(plan) {
  const validation = validateSeedPlan(plan);
  if (!validation.ok) {
    throw new Error(`invalid seed plan: ${validation.errors.join("; ")}`);
  }
  const schema = plan.fixtureSchema;
  const credentialSource = plan.credentialSource;
  const expectedRows = [
    ...plan.phases.map((phase) => `(${sqlLiteral(phase.domain)}, ${phase.count}::bigint)`),
    ...supportExpectedRowsSql(plan)
  ].join(",\n    ");
  return `-- Verify exact registry and business-table counts, checksums, and workspace relationships.
\\set ON_ERROR_STOP on
WITH expected(domain, expected_count) AS (
  VALUES
    ${expectedRows}
),
registry_actual AS (
  SELECT domain, count(*)::bigint AS actual_count
  FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
    AND checksum = ${sqlLiteral(plan.checksum)}
  GROUP BY domain
),
registry_count_mismatches AS (
  SELECT coalesce(e.domain, a.domain) domain,
         coalesce(e.expected_count, 0) expected_count,
         coalesce(a.actual_count, 0) AS actual_count
  FROM expected e
  FULL JOIN registry_actual a USING (domain)
  WHERE coalesce(e.expected_count, 0) <> coalesce(a.actual_count, 0)
),
business_actual AS (
  SELECT 'workspace'::text domain, count(*)::bigint actual_count
  FROM workspaces target
  JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'workspace'
  UNION ALL
  SELECT 'member', count(*) FROM users target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'member'
  UNION ALL
  SELECT 'permission', count(*) FROM (
    SELECT id FROM role_assignments
    UNION ALL
    SELECT id FROM resource_permissions
  ) target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'permission'
  UNION ALL
  SELECT 'project', count(*) FROM projects target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'project'
  UNION ALL
  SELECT 'issue', count(*) FROM issues target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'issue'
  UNION ALL
  SELECT 'knowledge-item', count(*) FROM knowledge_base_items target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'knowledge-item'
  UNION ALL
  SELECT 'knowledge-block', count(*) FROM knowledge_content_blocks target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'knowledge-block'
  UNION ALL
  SELECT 'notification', count(*) FROM notifications target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'notification'
  UNION ALL
  SELECT 'im-message', count(*) FROM messages target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'im-message'
  UNION ALL
  SELECT 'file', count(*) FROM files target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'file'
  UNION ALL
  SELECT 'collaboration-room', count(*) FROM knowledge_content_collaboration_states target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'collaboration-room'
  UNION ALL
  SELECT 'support-role', count(*) FROM roles target JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)} AND fixture.domain = 'support-role'
  UNION ALL
  SELECT 'support-role-permission', count(*)
  FROM role_permissions target
  JOIN ${schema}.fixture_records fixture
    ON target.role_id = (fixture.payload ->> 'roleId')::uuid
   AND target.permission_id = (fixture.payload ->> 'permissionId')::uuid
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)}
    AND fixture.domain = 'support-role-permission'
  UNION ALL
  SELECT 'support-conversation', count(*) FROM conversations target
  JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)}
    AND fixture.domain = 'support-conversation'
  UNION ALL
  SELECT 'support-conversation-member', count(*) FROM conversation_members target
  JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)}
    AND fixture.domain = 'support-conversation-member'
  UNION ALL
  SELECT 'support-knowledge-space', count(*) FROM knowledge_base_spaces target
  JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)} AND fixture.checksum = ${sqlLiteral(plan.checksum)}
    AND fixture.domain = 'support-knowledge-space'
),
count_mismatches AS (
  SELECT coalesce(e.domain, a.domain) domain,
         coalesce(e.expected_count, 0) expected_count,
         coalesce(a.actual_count, 0) actual_count
  FROM expected e
  FULL JOIN business_actual a USING (domain)
  WHERE coalesce(e.expected_count, 0) <> coalesce(a.actual_count, 0)
),
relationship_leaks AS (
  SELECT 'registry.workspace' leak
  FROM ${schema}.fixture_records candidate
  WHERE candidate.seed_id = ${sqlLiteral(plan.seedId)}
    AND candidate.checksum = ${sqlLiteral(plan.checksum)}
    AND candidate.domain <> 'workspace'
    AND NOT EXISTS (
      SELECT 1
      FROM ${schema}.fixture_records workspace
      WHERE workspace.seed_id = candidate.seed_id
        AND workspace.checksum = candidate.checksum
        AND workspace.domain = 'workspace'
        AND workspace.record_id = candidate.workspace_id
    )
  UNION ALL
  SELECT 'member.workspace' FROM users u
  JOIN ${schema}.fixture_records r ON r.record_id = u.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (u.workspace_id <> r.workspace_id OR u.username !~ '^cap_[0-9a-f]{10}_u[0-9]{7}$')
  UNION ALL
  SELECT 'role-assignment.relations' FROM role_assignments a
  JOIN roles role ON role.id = a.role_id
  JOIN users subject ON subject.id = a.subject_id
  JOIN ${schema}.fixture_records r ON r.record_id = a.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (a.workspace_id <> r.workspace_id OR role.workspace_id <> a.workspace_id
      OR subject.workspace_id <> a.workspace_id OR a.subject_type <> 'user')
  UNION ALL
  SELECT 'resource-permission.relations' FROM resource_permissions permission
  JOIN users subject ON subject.id = permission.subject_id
  JOIN ${schema}.fixture_records r ON r.record_id = permission.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (
      permission.workspace_id <> r.workspace_id
      OR subject.workspace_id <> permission.workspace_id
      OR permission.subject_type <> 'user'
      OR (
        permission.resource_type = 'project'
        AND NOT EXISTS (
          SELECT 1 FROM projects project
          WHERE project.id = permission.resource_id
            AND project.workspace_id = permission.workspace_id
        )
      )
      OR (
        permission.resource_type = 'knowledge_content'
        AND NOT EXISTS (
          SELECT 1 FROM knowledge_base_items item
          WHERE item.id = permission.resource_id
            AND item.workspace_id = permission.workspace_id
        )
      )
    )
  UNION ALL
  SELECT 'project.creator' FROM projects p
  JOIN users u ON u.id = p.created_by
  JOIN ${schema}.fixture_records r ON r.record_id = p.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (p.workspace_id <> r.workspace_id OR u.workspace_id <> p.workspace_id)
  UNION ALL
  SELECT 'issue.relations' FROM issues i
  JOIN projects p ON p.id = i.project_id
  JOIN users assignee ON assignee.id = i.assignee_id
  JOIN users reporter ON reporter.id = i.reporter_id
  JOIN ${schema}.fixture_records r ON r.record_id = i.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (i.workspace_id <> r.workspace_id OR p.workspace_id <> i.workspace_id
      OR assignee.workspace_id <> i.workspace_id OR reporter.workspace_id <> i.workspace_id)
  UNION ALL
  SELECT 'knowledge-item.relations' FROM knowledge_base_items item
  JOIN users creator ON creator.id = item.created_by
  LEFT JOIN knowledge_base_items parent ON parent.id = item.parent_id
  JOIN ${schema}.fixture_records r ON r.record_id = item.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (item.workspace_id <> r.workspace_id OR creator.workspace_id <> item.workspace_id
      OR (item.parent_id IS NOT NULL AND (parent.id IS NULL OR parent.workspace_id <> item.workspace_id)))
  UNION ALL
  SELECT 'knowledge-space.relations' FROM knowledge_base_spaces space
  JOIN knowledge_base_items root ON root.id = space.root_item_id
  JOIN users owner ON owner.id = space.owner_id
  JOIN ${schema}.fixture_records r ON r.record_id = root.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (space.workspace_id <> r.workspace_id OR root.workspace_id <> space.workspace_id
      OR owner.workspace_id <> space.workspace_id)
  UNION ALL
  SELECT 'knowledge-block.item' FROM knowledge_content_blocks b
  JOIN knowledge_base_items i ON i.id = b.item_id
  JOIN users u ON u.id = b.created_by
  JOIN ${schema}.fixture_records r ON r.record_id = b.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (b.workspace_id <> r.workspace_id OR i.workspace_id <> b.workspace_id OR u.workspace_id <> b.workspace_id)
  UNION ALL
  SELECT 'notification.relations' FROM notifications n
  JOIN users u ON u.id = n.recipient_id
  JOIN issues i ON i.id = n.target_id
  JOIN ${schema}.fixture_records r ON r.record_id = n.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (n.workspace_id <> r.workspace_id OR u.workspace_id <> n.workspace_id OR i.workspace_id <> n.workspace_id)
  UNION ALL
  SELECT 'message.relations' FROM messages m
  JOIN conversations c ON c.id = m.conversation_id
  JOIN users u ON u.id = m.sender_id
  JOIN ${schema}.fixture_records r ON r.record_id = m.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (m.workspace_id <> r.workspace_id OR c.workspace_id <> m.workspace_id OR u.workspace_id <> m.workspace_id)
  UNION ALL
  SELECT 'file.uploader' FROM files f
  JOIN users u ON u.id = f.uploaded_by
  JOIN ${schema}.fixture_records r ON r.record_id = f.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (f.workspace_id <> r.workspace_id OR u.workspace_id <> f.workspace_id)
  UNION ALL
  SELECT 'collaboration.item' FROM knowledge_content_collaboration_states c
  JOIN knowledge_base_items i ON i.id = c.item_id
  JOIN ${schema}.fixture_records r ON r.record_id = c.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND (c.workspace_id <> r.workspace_id OR i.workspace_id <> c.workspace_id)
  UNION ALL
  SELECT 'support-role.relations' FROM roles role
  JOIN ${schema}.fixture_records r ON r.record_id = role.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND r.domain = 'support-role'
    AND role.workspace_id <> r.workspace_id
  UNION ALL
  SELECT 'support-role-permission.relations' FROM role_permissions role_permission
  JOIN roles role ON role.id = role_permission.role_id
  JOIN ${schema}.fixture_records r
    ON role_permission.role_id = (r.payload ->> 'roleId')::uuid
   AND role_permission.permission_id = (r.payload ->> 'permissionId')::uuid
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND r.domain = 'support-role-permission'
    AND role.workspace_id <> r.workspace_id
  UNION ALL
  SELECT 'support-conversation.relations' FROM conversations conversation
  JOIN projects project ON project.id = conversation.project_id
  JOIN ${schema}.fixture_records r ON r.record_id = conversation.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND r.domain = 'support-conversation'
    AND (conversation.workspace_id <> r.workspace_id OR project.workspace_id <> r.workspace_id)
  UNION ALL
  SELECT 'support-conversation-member.relations' FROM conversation_members member
  JOIN conversations conversation ON conversation.id = member.conversation_id
  JOIN users fixture_user ON fixture_user.id = member.user_id
  JOIN ${schema}.fixture_records r ON r.record_id = member.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND r.domain = 'support-conversation-member'
    AND (
      member.workspace_id <> r.workspace_id
      OR conversation.workspace_id <> r.workspace_id
      OR fixture_user.workspace_id <> r.workspace_id
    )
  UNION ALL
  SELECT 'support-knowledge-space.relations' FROM knowledge_base_spaces space
  JOIN knowledge_base_items root ON root.id = space.root_item_id
  JOIN ${schema}.fixture_records r ON r.record_id = space.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND r.domain = 'support-knowledge-space'
    AND (space.workspace_id <> r.workspace_id OR root.workspace_id <> r.workspace_id)
),
support_mismatches AS (
  SELECT domain support, expected_count, actual_count
  FROM count_mismatches
  WHERE domain LIKE 'support-%'
),
fixture_usernames AS (
  SELECT u.username
  FROM users u
  JOIN ${schema}.fixture_records r ON r.record_id = u.id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)}
    AND r.checksum = ${sqlLiteral(plan.checksum)}
    AND r.domain = 'member'
),
username_duplicates AS (
  SELECT fixture_usernames.username
  FROM fixture_usernames
  JOIN users ON users.username = fixture_usernames.username
  GROUP BY fixture_usernames.username
  HAVING count(*) <> 1
),
run_state AS (
  SELECT count(*) FILTER (
    WHERE checksum = ${sqlLiteral(plan.checksum)}
      AND fixture_name = ${sqlLiteral(plan.fixtureName)}
      AND status = 'applied'
      AND completed_phase = ${plan.phases.length}
  )::integer AS valid_runs
  FROM ${schema}.fixture_runs
  WHERE seed_id = ${sqlLiteral(plan.seedId)}
),
credential_source AS (
  SELECT count(*)::integer AS matched_users,
         min(md5(password_hash)) AS password_hash_fingerprint
  FROM users
  WHERE username = ${sqlLiteral(credentialSource.username)}
    AND status = ${sqlLiteral(credentialSource.requiredStatus)}
    AND deleted_at IS NULL
    AND password_hash IS NOT NULL
),
fixture_credential_fingerprints AS (
  SELECT count(DISTINCT md5(target.password_hash))::integer AS distinct_fingerprints,
         min(md5(target.password_hash)) AS password_hash_fingerprint
  FROM users target
  JOIN ${schema}.fixture_records fixture ON fixture.record_id = target.id
  WHERE fixture.seed_id = ${sqlLiteral(plan.seedId)}
    AND fixture.checksum = ${sqlLiteral(plan.checksum)}
    AND fixture.domain = 'member'
)
SELECT json_build_object(
  'schemaVersion', 'colla.capacity-seed-verification/v1',
  'evidenceKind', 'verification',
  'seedId', ${sqlLiteral(plan.seedId)},
  'checksum', ${sqlLiteral(plan.checksum)},
  'fixtureName', ${sqlLiteral(plan.fixtureName)},
  'ok', (
    (SELECT count(*) FROM count_mismatches) = 0
    AND (SELECT count(*) FROM registry_count_mismatches) = 0
    AND (SELECT count(*) FROM relationship_leaks) = 0
    AND (SELECT count(*) FROM support_mismatches) = 0
    AND (SELECT count(*) FROM username_duplicates) = 0
    AND (SELECT valid_runs FROM run_state) = 1
    AND (SELECT matched_users FROM credential_source) = 1
    AND (SELECT distinct_fingerprints FROM fixture_credential_fingerprints) = 1
    AND (SELECT password_hash_fingerprint FROM fixture_credential_fingerprints)
      = (SELECT password_hash_fingerprint FROM credential_source)
  ),
  'countMismatches', coalesce((SELECT json_agg(count_mismatches) FROM count_mismatches), '[]'::json),
  'registryCountMismatches', coalesce((SELECT json_agg(registry_count_mismatches) FROM registry_count_mismatches), '[]'::json),
  'workspaceIsolationLeaks', (SELECT count(*) FROM relationship_leaks),
  'relationshipLeaks', coalesce((SELECT json_agg(relationship_leaks) FROM relationship_leaks), '[]'::json),
  'supportMismatches', coalesce((SELECT json_agg(support_mismatches) FROM support_mismatches), '[]'::json),
  'duplicateUsernames', coalesce((SELECT json_agg(username_duplicates) FROM username_duplicates), '[]'::json),
  'runStateMatches', (SELECT valid_runs FROM run_state) = 1,
  'credentialSource', json_build_object(
    'type', ${sqlLiteral(credentialSource.type)},
    'username', ${sqlLiteral(credentialSource.username)},
    'requiredStatus', ${sqlLiteral(credentialSource.requiredStatus)},
    'matchedUsers', (SELECT matched_users FROM credential_source),
    'fingerprintAlgorithm', ${sqlLiteral(credentialSource.outputFingerprint)},
    'passwordHashFingerprint', (SELECT password_hash_fingerprint FROM credential_source),
    'fixtureFingerprintMatches',
      (SELECT distinct_fingerprints FROM fixture_credential_fingerprints) = 1
      AND (SELECT password_hash_fingerprint FROM fixture_credential_fingerprints)
        = (SELECT password_hash_fingerprint FROM credential_source)
  )
)::text;
`;
}

function businessResidueExpression(plan) {
  const workspaceIds = plan.workspaceIds
    .map((workspace) => `${sqlLiteral(workspace.id)}::uuid`)
    .join(", ");
  const roleIds = roleIdsValues(plan);
  const workspaceScopedTables = [
    "users",
    "user_roles",
    "roles",
    "resource_permissions",
    "role_assignments",
    "projects",
    "issues",
    "knowledge_base_items",
    "knowledge_base_spaces",
    "knowledge_content_blocks",
    "knowledge_content_collaboration_tickets",
    "knowledge_content_collaboration_updates",
    "knowledge_content_collaboration_states",
    "notifications",
    "conversations",
    "conversation_members",
    "messages",
    "files",
    "domain_events",
    "audit_logs"
  ];
  return [
    `(SELECT count(*) FROM workspaces WHERE id IN (${workspaceIds}))`,
    ...workspaceScopedTables.map(
      (table) => `(SELECT count(*) FROM ${table} WHERE workspace_id IN (${workspaceIds}))`
    ),
    `(SELECT count(*) FROM role_permissions WHERE role_id IN (
      SELECT role_id FROM (VALUES ${roleIds}) AS fixture_roles(workspace_ordinal, role_name, role_id)
    ))`
  ].join("\n    + ");
}

export function generateCleanCheckSql(plan) {
  const validation = validateSeedPlan(plan);
  if (!validation.ok) {
    throw new Error(`invalid seed plan: ${validation.errors.join("; ")}`);
  }
  const schema = plan.fixtureSchema;
  const workspaceIds = plan.workspaceIds
    .map((workspace) => `${sqlLiteral(workspace.id)}::uuid`)
    .join(", ");
  const businessResidue = businessResidueExpression(plan);
  const jsonResult = (fixtureCounts) => `SELECT json_build_object(
  'schemaVersion', 'colla.capacity-seed-clean-check/v1',
  'evidenceKind', 'clean-state',
  'seedId', ${sqlLiteral(plan.seedId)},
  'checksum', ${sqlLiteral(plan.checksum)},
  'fixtureName', ${sqlLiteral(plan.fixtureName)},
  'ok', fixture_runs = 0
    AND fixture_phases = 0
    AND fixture_cleanup = 0
    AND fixture_records = 0
    AND fixture_workspaces = 0
    AND conflicting_runs = 0
    AND business_records = 0,
  'fixtureRegistryExists', ${fixtureCounts},
  'fixtureRuns', fixture_runs,
  'fixturePhases', fixture_phases,
  'fixtureCleanupProgress', fixture_cleanup,
  'fixtureRecords', fixture_records,
  'fixtureWorkspaces', fixture_workspaces,
  'conflictingRuns', conflicting_runs,
  'businessRecords', business_records
)::text
FROM clean_counts;`;
  return `-- Prove the named seed starts from zero without mutating fixture or business data.
\\set ON_ERROR_STOP on
SELECT to_regclass(${sqlLiteral(`${schema}.fixture_runs`)}) IS NOT NULL AS fixture_registry_exists
\\gset
SELECT to_regclass(${sqlLiteral(`${schema}.fixture_cleanup_progress`)}) IS NOT NULL AS fixture_cleanup_registry_exists
\\gset
\\if :fixture_cleanup_registry_exists
SELECT count(*) AS fixture_cleanup_rows
FROM ${schema}.fixture_cleanup_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
\\gset
\\else
\\set fixture_cleanup_rows 0
\\endif
\\if :fixture_registry_exists
WITH clean_counts AS (
  SELECT
    (SELECT count(*) FROM ${schema}.fixture_runs
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
         OR fixture_name = ${sqlLiteral(plan.fixtureName)}) AS fixture_runs,
    (SELECT count(*) FROM ${schema}.fixture_phase_progress
      WHERE seed_id = ${sqlLiteral(plan.seedId)}) AS fixture_phases,
    :fixture_cleanup_rows::bigint AS fixture_cleanup,
    (SELECT count(*) FROM ${schema}.fixture_records
      WHERE seed_id = ${sqlLiteral(plan.seedId)}) AS fixture_records,
    (SELECT count(*) FROM workspaces
      WHERE id IN (${workspaceIds})) AS fixture_workspaces,
    (SELECT count(*) FROM ${schema}.fixture_runs
      WHERE (
        seed_id = ${sqlLiteral(plan.seedId)}
        OR fixture_name = ${sqlLiteral(plan.fixtureName)}
      )
      AND NOT (
        seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}
        AND fixture_name = ${sqlLiteral(plan.fixtureName)}
      )) AS conflicting_runs,
    (${businessResidue}) AS business_records
)
${jsonResult("true")}
\\else
WITH clean_counts AS (
  SELECT
    0::bigint AS fixture_runs,
    0::bigint AS fixture_phases,
    :fixture_cleanup_rows::bigint AS fixture_cleanup,
    0::bigint AS fixture_records,
    (SELECT count(*) FROM workspaces
      WHERE id IN (${workspaceIds})) AS fixture_workspaces,
    0::bigint AS conflicting_runs,
    (${businessResidue}) AS business_records
)
${jsonResult("false")}
\\endif
`;
}

function workspaceContractValues(plan) {
  return plan.workspaceIds
    .map((workspace) => `(${workspace.ordinal}, ${sqlLiteral(workspace.id)}::uuid)`)
    .join(",\n    ");
}

function primaryOwnershipExpectedSql(plan, domain, start, end) {
  const phase = plan.phases.find((candidate) => candidate.domain === domain);
  const fixturePrefix = `${plan.fixtureName}:${domain}:`;
  return `SELECT ${workspaceUuidExpression(plan, domain, "g")} workspace_id,
       ${sqlLiteral(domain)}::text domain,
       g::bigint ordinal,
       ${recordIdExpression(plan, domain, "g")} record_id,
       ${sqlLiteral(fixturePrefix)} || g::text fixture_key,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', ${sqlLiteral(domain)},
         'ordinal', g,
         'payloadBytes', ${phase.payloadBytes},
         'temperature', CASE WHEN mod(g - 1, 10) = 0 THEN 'hot'
           WHEN mod(g - 1, 10) < 4 THEN 'warm' ELSE 'cold' END${domainPayloadSql(plan, domain)}
       ) payload
FROM generate_series(${start}, ${end}) AS generated(g)`;
}

function supportOwnershipExpectedSql(plan, domain, start, end) {
  const projectCount = countFor(plan, "project");
  if (domain === "support-role") {
    return `SELECT workspace_contract.workspace_id,
       'support-role'::text domain,
       role_seed.support_ordinal::bigint ordinal,
       role_seed.role_id record_id,
       ${sqlLiteral(`${plan.fixtureName}:support-role:`)}
         || role_seed.workspace_ordinal::text || ':' || role_seed.role_name fixture_key,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-role',
         'roleId', role_seed.role_id,
         'roleName', role_seed.role_name
       ) payload
FROM (
  SELECT fixture_roles.*,
         (fixture_roles.workspace_ordinal - 1) * 5
           + CASE fixture_roles.role_name
               WHEN 'owner' THEN 1 WHEN 'admin' THEN 2 WHEN 'editor' THEN 3
               WHEN 'viewer' THEN 4 ELSE 5
             END support_ordinal
  FROM (VALUES ${roleIdsValues(plan)})
    AS fixture_roles(workspace_ordinal, role_name, role_id)
) role_seed
JOIN (VALUES ${workspaceContractValues(plan)})
  AS workspace_contract(workspace_ordinal, workspace_id)
  USING (workspace_ordinal)
WHERE role_seed.support_ordinal BETWEEN ${start} AND ${end}`;
  }
  if (domain === "support-role-permission") {
    return `SELECT expected_permissions.workspace_id,
       'support-role-permission'::text domain,
       expected_permissions.support_ordinal::bigint ordinal,
       md5(
         ${sqlLiteral(`${plan.seedId}:support-role-permission:`)}
         || expected_permissions.role_id::text || ':'
         || expected_permissions.permission_id::text
       )::uuid record_id,
       ${sqlLiteral(`${plan.fixtureName}:support-role-permission:`)}
         || expected_permissions.role_id::text || ':'
         || expected_permissions.permission_id::text fixture_key,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-role-permission',
         'roleId', expected_permissions.role_id,
         'permissionId', expected_permissions.permission_id
       ) payload
FROM (
  SELECT workspace_contract.workspace_id,
         role_seed.role_id,
         permissions.id permission_id,
         row_number() OVER (ORDER BY role_seed.role_id, permissions.id) support_ordinal
  FROM (VALUES ${roleIdsValues(plan)})
    AS role_seed(workspace_ordinal, role_name, role_id)
  JOIN (VALUES ${workspaceContractValues(plan)})
    AS workspace_contract(workspace_ordinal, workspace_id)
    USING (workspace_ordinal)
  JOIN permissions ON (
    role_seed.role_name IN ('owner', 'admin')
    OR (
      role_seed.role_name = 'editor'
      AND permissions.code IN (
        'project.create', 'project.manage', 'issue.create', 'issue.update',
        'doc.create', 'doc.update', 'base.create', 'base.update'
      )
    )
    OR (
      role_seed.role_name = 'viewer'
      AND permissions.code IN ('org.view', 'usergroup.view', 'role.view')
    )
  )
  WHERE role_seed.role_name <> 'disabled'
) expected_permissions`;
  }
  if (domain === "support-conversation") {
    return `SELECT ${workspaceUuidExpression(plan, "project", "g")} workspace_id,
       'support-conversation'::text domain,
       g::bigint ordinal,
       md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || g::text)::uuid record_id,
       ${sqlLiteral(`${plan.fixtureName}:support-conversation:`)} || g::text fixture_key,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-conversation',
         'conversationId',
           md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || g::text)::uuid
       ) payload
FROM generate_series(${start}, ${end}) AS generated(g)`;
  }
  if (domain === "support-conversation-member") {
    const projectOrdinal = `(1 + floor((g - 1) / 2.0))::bigint`;
    const memberOffset = `mod(g - 1, 2)::bigint`;
    return `SELECT ${workspaceUuidExpression(plan, "project", projectOrdinal)} workspace_id,
       'support-conversation-member'::text domain,
       g::bigint ordinal,
       md5(
         ${sqlLiteral(`${plan.seedId}:conversation-member:`)}
         || ${projectOrdinal}::text || ':' || ${memberOffset}::text
       )::uuid record_id,
       ${sqlLiteral(`${plan.fixtureName}:support-conversation-member:`)}
         || ${projectOrdinal}::text || ':' || ${memberOffset}::text fixture_key,
       jsonb_build_object(
         'fixture', ${sqlLiteral(plan.fixtureName)},
         'domain', 'support-conversation-member',
         'conversationMemberId', md5(
           ${sqlLiteral(`${plan.seedId}:conversation-member:`)}
           || ${projectOrdinal}::text || ':' || ${memberOffset}::text
         )::uuid,
         'conversationId',
           md5(${sqlLiteral(`${plan.seedId}:conversation:`)}
             || ${projectOrdinal}::text)::uuid
       ) payload
FROM generate_series(${start}, ${end}) AS generated(g)
WHERE ${projectOrdinal} BETWEEN 1 AND ${projectCount}`;
  }
  if (domain === "support-knowledge-space") {
    const rows = plan.workspaceIds.map((workspace) => {
      const rootOrdinal = firstFixtureOrdinalForWorkspace(
        plan,
        "knowledge-item",
        workspace.ordinal
      );
      const rootId = postgresMd5Uuid(`${plan.seedId}:knowledge-item:${rootOrdinal}`);
      const spaceId = postgresMd5Uuid(`${plan.seedId}:knowledge-space:${workspace.id}`);
      return `(
        ${sqlLiteral(workspace.id)}::uuid,
        'support-knowledge-space'::text,
        ${workspace.ordinal}::bigint,
        ${sqlLiteral(spaceId)}::uuid,
        ${sqlLiteral(`${plan.fixtureName}:support-knowledge-space:${workspace.ordinal}`)},
        jsonb_build_object(
          'fixture', ${sqlLiteral(plan.fixtureName)},
          'domain', 'support-knowledge-space',
          'spaceId', ${sqlLiteral(spaceId)}::uuid,
          'rootItemId', ${sqlLiteral(rootId)}::uuid
        )
      )`;
    }).join(",\n      ");
    return `SELECT expected.*
FROM (VALUES
      ${rows}
) AS expected(workspace_id, domain, ordinal, record_id, fixture_key, payload)
WHERE expected.ordinal BETWEEN ${start} AND ${end}`;
  }
  throw new Error(`unsupported support ownership domain: ${domain}`);
}

function ownershipExpectedSql(plan, domain, start, end) {
  return requiredDomains.includes(domain)
    ? primaryOwnershipExpectedSql(plan, domain, start, end)
    : supportOwnershipExpectedSql(plan, domain, start, end);
}

function ownershipIdentityGuardSql(plan, domain, start, end) {
  const schema = plan.fixtureSchema;
  const range = domain === "support-role-permission"
    ? ""
    : `\n    AND owned.ordinal BETWEEN ${start} AND ${end}`;
  return `DO $capacity_fixture_ownership_identity_guard$
BEGIN
  IF EXISTS (
    WITH expected AS (
      ${ownershipExpectedSql(plan, domain, start, end)}
    ),
    actual AS (
      SELECT owned.workspace_id,
             owned.domain,
             owned.ordinal,
             owned.record_id,
             owned.fixture_key,
             owned.payload
      FROM ${schema}.fixture_records owned
      WHERE owned.seed_id = ${sqlLiteral(plan.seedId)}
        AND owned.checksum = ${sqlLiteral(plan.checksum)}
        AND owned.domain = ${sqlLiteral(domain)}${range}
    )
    SELECT 1
    FROM expected
    FULL JOIN actual USING (domain, ordinal)
    WHERE expected.ordinal IS NULL
       OR actual.ordinal IS NULL
       OR actual.workspace_id IS DISTINCT FROM expected.workspace_id
       OR actual.record_id IS DISTINCT FROM expected.record_id
       OR actual.fixture_key IS DISTINCT FROM expected.fixture_key
       OR actual.payload IS DISTINCT FROM expected.payload
  ) THEN
    RAISE EXCEPTION 'capacity fixture ownership identity mismatch for domain ${domain}';
  END IF;
END
$capacity_fixture_ownership_identity_guard$;`;
}

function cleanupSpecs(plan) {
  const phase = (domain) => plan.phases.find((candidate) => candidate.domain === domain);
  const project = phase("project");
  return [
    { domain: "collaboration-room", ...phase("collaboration-room") },
    { domain: "im-message", ...phase("im-message") },
    {
      domain: "support-conversation-member",
      count: project.count * 2,
      batchSize: Math.max(1, project.batchSize * 2)
    },
    { domain: "support-conversation", count: project.count, batchSize: project.batchSize },
    { domain: "notification", ...phase("notification") },
    { domain: "permission", ...phase("permission") },
    { domain: "support-role-permission", dynamic: true },
    {
      domain: "support-role",
      count: plan.workspaceIds.length * 5,
      batchSize: plan.workspaceIds.length * 5
    },
    { domain: "knowledge-block", ...phase("knowledge-block") },
    {
      domain: "support-knowledge-space",
      count: plan.workspaceIds.length,
      batchSize: plan.workspaceIds.length
    },
    { domain: "knowledge-item", ...phase("knowledge-item"), reverse: true },
    { domain: "issue", ...phase("issue") },
    { domain: "project", ...project },
    { domain: "file", ...phase("file") },
    { domain: "member", ...phase("member") },
    { domain: "workspace", ...phase("workspace") }
  ];
}

function ownedChunkPredicate(plan, domain, start, end, alias = "owned") {
  return `${alias}.seed_id = ${sqlLiteral(plan.seedId)}
    AND ${alias}.checksum = ${sqlLiteral(plan.checksum)}
    AND ${alias}.domain = ${sqlLiteral(domain)}
    AND ${alias}.ordinal BETWEEN ${start} AND ${end}`;
}

function cleanupChunkStatements(plan, domain, start, end) {
  const schema = plan.fixtureSchema;
  const owned = ownedChunkPredicate(plan, domain, start, end);
  const deleteById = (table) => `DELETE FROM ${table} target
USING ${schema}.fixture_records owned
WHERE target.id = owned.record_id
  AND ${owned};`;
  switch (domain) {
    case "collaboration-room":
      return deleteById("knowledge_content_collaboration_states");
    case "im-message":
      return `UPDATE conversations target
SET last_message_id = NULL,
    last_message_at = NULL
FROM ${schema}.fixture_records conversation_owner,
     ${schema}.fixture_records message_owner
WHERE target.id = conversation_owner.record_id
  AND conversation_owner.seed_id = ${sqlLiteral(plan.seedId)}
  AND conversation_owner.checksum = ${sqlLiteral(plan.checksum)}
  AND conversation_owner.domain = 'support-conversation'
  AND target.last_message_id = message_owner.record_id
  AND ${ownedChunkPredicate(plan, domain, start, end, "message_owner")};
${deleteById("messages")}`;
    case "support-conversation-member":
      return deleteById("conversation_members");
    case "support-conversation":
      return deleteById("conversations");
    case "notification":
      return deleteById("notifications");
    case "permission":
      return `${deleteById("resource_permissions")}
${deleteById("role_assignments")}`;
    case "support-role-permission":
      return `DELETE FROM role_permissions target
USING ${schema}.fixture_records owned
WHERE target.role_id = (owned.payload ->> 'roleId')::uuid
  AND target.permission_id = (owned.payload ->> 'permissionId')::uuid
  AND owned.seed_id = ${sqlLiteral(plan.seedId)}
  AND owned.checksum = ${sqlLiteral(plan.checksum)}
  AND owned.domain = 'support-role-permission';`;
    case "support-role":
      return deleteById("roles");
    case "knowledge-block":
      return deleteById("knowledge_content_blocks");
    case "support-knowledge-space":
      return deleteById("knowledge_base_spaces");
    case "knowledge-item":
      return deleteById("knowledge_base_items");
    case "issue":
      return deleteById("issues");
    case "project":
      return deleteById("projects");
    case "file":
      return deleteById("files");
    case "member":
      return deleteById("users");
    case "workspace":
      return deleteById("workspaces");
    default:
      throw new Error(`unsupported cleanup domain: ${domain}`);
  }
}

function cleanupChunkSql(plan, spec, start, end, completed) {
  const schema = plan.fixtureSchema;
  return `-- cleanup ${spec.domain} chunk ${start}-${end}
SELECT (
  status = 'deleted' OR completed_ordinal >= ${completed}
) AS capacity_skip_cleanup_chunk
FROM ${schema}.fixture_cleanup_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)}
\\gset
\\if :capacity_skip_cleanup_chunk
\\echo 'skip committed cleanup ${spec.domain} chunk ${start}-${end}'
\\else
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
UPDATE ${schema}.fixture_cleanup_progress
SET status = 'deleting', updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)};

${ownershipIdentityGuardSql(plan, spec.domain, start, end)}

${cleanupChunkStatements(plan, spec.domain, start, end)}

DELETE FROM ${schema}.fixture_records owned
WHERE ${ownedChunkPredicate(plan, spec.domain, start, end)};

UPDATE ${schema}.fixture_cleanup_progress
SET completed_ordinal = ${completed},
    status = CASE WHEN ${completed} = expected_count THEN 'deleted' ELSE 'deleting' END,
    updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)}
  AND completed_ordinal < ${completed};
COMMIT;
\\endif
`;
}

function dynamicCleanupSql(plan, spec) {
  const schema = plan.fixtureSchema;
  return `-- cleanup ${spec.domain} in its own bounded support transaction
SELECT (status = 'deleted') AS capacity_skip_cleanup_chunk
FROM ${schema}.fixture_cleanup_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)}
\\gset
\\if :capacity_skip_cleanup_chunk
\\echo 'skip committed cleanup ${spec.domain}'
\\else
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
UPDATE ${schema}.fixture_cleanup_progress
SET status = 'deleting', updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)};

${ownershipIdentityGuardSql(plan, spec.domain, 0, 0)}

${cleanupChunkStatements(plan, spec.domain, 0, 0)}

DELETE FROM ${schema}.fixture_records
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)};

UPDATE ${schema}.fixture_cleanup_progress
SET completed_ordinal = expected_count,
    status = 'deleted',
    updated_at = clock_timestamp()
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND domain = ${sqlLiteral(spec.domain)};
COMMIT;
\\endif
`;
}

export function generateCleanupSql(plan) {
  const validation = validateSeedPlan(plan);
  if (!validation.ok) {
    throw new Error(`invalid seed plan: ${validation.errors.join("; ")}`);
  }
  {
    const schema = plan.fixtureSchema;
    const specs = cleanupSpecs(plan);
    const workspaceIds = plan.workspaceIds
      .map((workspace) => `${sqlLiteral(workspace.id)}::uuid`)
      .join(", ");
    const expectedRows = specs.map((spec) => spec.dynamic
      ? `(${sqlLiteral(spec.domain)}, ${supportRolePermissionExpectedCountSql(plan)})`
      : `(${sqlLiteral(spec.domain)}, ${spec.count}::bigint)`).join(",\n    ");
    const sections = [`-- Cleanup is checksum guarded, directly owned, chunk committed, and resumable.
\\set ON_ERROR_STOP on
SET lock_timeout = '5s';
SET statement_timeout = '120s';
CREATE TABLE IF NOT EXISTS ${schema}.fixture_cleanup_progress (
  seed_id text NOT NULL,
  checksum char(64) NOT NULL,
  domain text NOT NULL,
  expected_count bigint NOT NULL,
  completed_ordinal bigint NOT NULL DEFAULT 0,
  status text NOT NULL CHECK (status IN ('pending', 'deleting', 'deleted')),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (seed_id, domain),
  CONSTRAINT fixture_cleanup_progress_domain_chk
    CHECK (domain IN (${cleanupDomains.map(sqlLiteral).join(", ")})),
  CONSTRAINT fixture_cleanup_progress_range_chk
    CHECK (
      expected_count >= 0
      AND completed_ordinal >= 0
      AND completed_ordinal <= expected_count
    ),
  CONSTRAINT fixture_cleanup_progress_status_chk
    CHECK (
      (status = 'pending' AND completed_ordinal = 0 AND expected_count > 0)
      OR (status = 'deleting' AND completed_ordinal < expected_count)
      OR (status = 'deleted' AND completed_ordinal = expected_count)
    )
);

DO $capacity_fixture_cleanup_schema_guard$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = ${sqlLiteral(`${schema}.fixture_cleanup_progress`)}::regclass
      AND conname = 'fixture_cleanup_progress_domain_chk'
  ) THEN
    ALTER TABLE ${schema}.fixture_cleanup_progress
      ADD CONSTRAINT fixture_cleanup_progress_domain_chk
      CHECK (domain IN (${cleanupDomains.map(sqlLiteral).join(", ")}));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = ${sqlLiteral(`${schema}.fixture_cleanup_progress`)}::regclass
      AND conname = 'fixture_cleanup_progress_range_chk'
  ) THEN
    ALTER TABLE ${schema}.fixture_cleanup_progress
      ADD CONSTRAINT fixture_cleanup_progress_range_chk
      CHECK (
        expected_count >= 0
        AND completed_ordinal >= 0
        AND completed_ordinal <= expected_count
      );
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = ${sqlLiteral(`${schema}.fixture_cleanup_progress`)}::regclass
      AND conname = 'fixture_cleanup_progress_status_chk'
  ) THEN
    ALTER TABLE ${schema}.fixture_cleanup_progress
      ADD CONSTRAINT fixture_cleanup_progress_status_chk
      CHECK (
        (status = 'pending' AND completed_ordinal = 0 AND expected_count > 0)
        OR (status = 'deleting' AND completed_ordinal < expected_count)
        OR (status = 'deleted' AND completed_ordinal = expected_count)
      );
  END IF;
END
$capacity_fixture_cleanup_schema_guard$;

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
DO $capacity_fixture_cleanup_guard$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM ${schema}.fixture_runs
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
      AND checksum = ${sqlLiteral(plan.checksum)}
      AND fixture_name = ${sqlLiteral(plan.fixtureName)}
  ) THEN
    RAISE EXCEPTION 'named capacity fixture does not exist or checksum differs';
  END IF;
END
$capacity_fixture_cleanup_guard$;

DO $capacity_fixture_cleanup_ownership_guard$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM ${schema}.fixture_cleanup_progress
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
  ) AND EXISTS (
    WITH expected(domain, expected_count) AS (
      VALUES
        ${expectedRows}
    ),
    actual AS (
      SELECT domain, count(*)::bigint actual_count
      FROM ${schema}.fixture_records
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}
      GROUP BY domain
    )
    SELECT 1
    FROM expected
    FULL JOIN actual USING (domain)
    WHERE expected.domain IS NULL
       OR actual.domain IS NULL
       OR expected.expected_count <> actual.actual_count
  ) THEN
    RAISE EXCEPTION 'fixture ownership registry domain contract is incomplete or contains unexpected domains';
  END IF;
END
$capacity_fixture_cleanup_ownership_guard$;

INSERT INTO ${schema}.fixture_cleanup_progress (
  seed_id, checksum, domain, expected_count, completed_ordinal, status
)
SELECT ${sqlLiteral(plan.seedId)},
       ${sqlLiteral(plan.checksum)},
       expected.domain,
       expected.expected_count,
       0,
       CASE WHEN expected.expected_count = 0 THEN 'deleted' ELSE 'pending' END
FROM (VALUES
    ${expectedRows}
) AS expected(domain, expected_count)
ON CONFLICT (seed_id, domain) DO NOTHING;

DO $capacity_fixture_cleanup_progress_guard$
BEGIN
  IF EXISTS (
    WITH expected(domain, expected_count) AS (
      VALUES
        ${expectedRows}
    ),
    actual AS (
      SELECT domain, checksum, expected_count, completed_ordinal, status
      FROM ${schema}.fixture_cleanup_progress
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
    )
    SELECT 1
    FROM expected
    FULL JOIN actual USING (domain)
    WHERE expected.domain IS NULL
       OR actual.domain IS NULL
       OR actual.checksum <> ${sqlLiteral(plan.checksum)}
       OR actual.expected_count <> expected.expected_count
       OR actual.completed_ordinal < 0
       OR actual.completed_ordinal > actual.expected_count
       OR (
         actual.status = 'pending'
         AND (actual.completed_ordinal <> 0 OR actual.expected_count = 0)
       )
       OR (
         actual.status = 'deleting'
         AND actual.completed_ordinal >= actual.expected_count
       )
       OR (
         actual.status = 'deleted'
         AND actual.completed_ordinal <> actual.expected_count
       )
  ) THEN
    RAISE EXCEPTION 'cleanup progress does not match the exact domain and expected-count contract';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM ${schema}.fixture_cleanup_progress progress
    WHERE progress.seed_id = ${sqlLiteral(plan.seedId)}
      AND (
        progress.expected_count - progress.completed_ordinal <> (
          SELECT count(*)
          FROM ${schema}.fixture_records owned
          WHERE owned.seed_id = progress.seed_id
            AND owned.checksum = progress.checksum
            AND owned.domain = progress.domain
        )
        OR EXISTS (
          SELECT 1
          FROM ${schema}.fixture_records owned
          WHERE owned.seed_id = progress.seed_id
            AND owned.domain = progress.domain
            AND (
              owned.checksum <> progress.checksum
              OR owned.ordinal < 1
              OR owned.ordinal > progress.expected_count
              OR (
                progress.domain = 'knowledge-item'
                AND owned.ordinal > progress.expected_count - progress.completed_ordinal
              )
              OR (
                progress.domain <> 'knowledge-item'
                AND owned.ordinal <= progress.completed_ordinal
              )
            )
        )
      )
  ) THEN
    RAISE EXCEPTION 'cleanup progress does not match the exact remaining ownership ordinal set';
  END IF;

  IF EXISTS (
    WITH expected(domain) AS (
      VALUES ${cleanupDomains.map((domain) => `(${sqlLiteral(domain)})`).join(", ")}
    )
    SELECT 1
    FROM ${schema}.fixture_records owned
    LEFT JOIN expected USING (domain)
    WHERE owned.seed_id = ${sqlLiteral(plan.seedId)}
      AND (
        expected.domain IS NULL
        OR owned.checksum <> ${sqlLiteral(plan.checksum)}
      )
  ) THEN
    RAISE EXCEPTION 'fixture ownership contains an unexpected domain or checksum';
  END IF;
END
$capacity_fixture_cleanup_progress_guard$;
COMMIT;
`];

    for (const spec of specs) {
      if (spec.dynamic) {
        sections.push(dynamicCleanupSql(plan, spec));
        continue;
      }
      let completed = 0;
      for (let offset = 0; offset < spec.count; offset += spec.batchSize) {
        const size = Math.min(spec.batchSize, spec.count - offset);
        const start = spec.reverse ? spec.count - offset - size + 1 : offset + 1;
        const end = spec.reverse ? spec.count - offset : offset + size;
        completed += size;
        sections.push(cleanupChunkSql(plan, spec, start, end, completed));
      }
    }

    sections.push(`SELECT count(*) = ${specs.length} AS capacity_cleanup_complete
FROM ${schema}.fixture_cleanup_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND status = 'deleted'
  AND completed_ordinal = expected_count
\\gset
\\if :capacity_cleanup_complete
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
DO $capacity_fixture_cleanup_residue_guard$
BEGIN
  IF (
    SELECT count(*)
    FROM ${schema}.fixture_records
    WHERE seed_id = ${sqlLiteral(plan.seedId)}
  ) <> 0 THEN
    RAISE EXCEPTION 'capacity fixture registry residue remains; cleanup anchors were retained';
  END IF;
  IF (${businessResidueExpression(plan)}) <> 0 THEN
    RAISE EXCEPTION 'capacity fixture business residue remains; derived rows are check-only and cleanup anchors were retained';
  END IF;
END
$capacity_fixture_cleanup_residue_guard$;

DELETE FROM ${schema}.fixture_phase_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)};
DELETE FROM ${schema}.fixture_runs
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND fixture_name = ${sqlLiteral(plan.fixtureName)};
DELETE FROM ${schema}.fixture_cleanup_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)};
COMMIT;
\\else
DO $capacity_fixture_cleanup_incomplete$
BEGIN
  RAISE EXCEPTION 'capacity fixture cleanup is incomplete';
END
$capacity_fixture_cleanup_incomplete$;
\\endif

WITH cleanup_counts AS (
  SELECT
    (SELECT count(*) FROM ${schema}.fixture_runs
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_runs,
    (SELECT count(*) FROM ${schema}.fixture_phase_progress
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_phases,
    (SELECT count(*) FROM ${schema}.fixture_cleanup_progress
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_cleanup,
    (SELECT count(*) FROM ${schema}.fixture_records
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_records,
    (SELECT count(*) FROM workspaces
      WHERE id IN (${workspaceIds})) AS fixture_workspaces,
    (${businessResidueExpression(plan)}) AS business_records
)
SELECT json_build_object(
  'schemaVersion', 'colla.capacity-seed-cleanup/v1',
  'evidenceKind', 'cleanup',
  'seedId', ${sqlLiteral(plan.seedId)},
  'checksum', ${sqlLiteral(plan.checksum)},
  'fixtureName', ${sqlLiteral(plan.fixtureName)},
  'ok', fixture_runs = 0
    AND fixture_phases = 0
    AND fixture_cleanup = 0
    AND fixture_records = 0
    AND fixture_workspaces = 0
    AND business_records = 0,
  'fixtureRuns', fixture_runs,
  'fixturePhases', fixture_phases,
  'fixtureCleanupProgress', fixture_cleanup,
  'fixtureRecords', fixture_records,
  'fixtureWorkspaces', fixture_workspaces,
  'businessRecords', business_records
)::text
FROM cleanup_counts;
`);
    return `${sections.join("\n")}\n`;
  }
}

async function writeText(file, content) {
  const resolved = path.resolve(file);
  await mkdir(path.dirname(resolved), { recursive: true });
  await writeFile(resolved, content, "utf8");
}

function executePsql(sql, database) {
  const result = spawnSync("psql", [
    "-X",
    "--set", "ON_ERROR_STOP=1",
    "--no-psqlrc",
    "--tuples-only",
    "--no-align",
    "--quiet",
    "--dbname", database
  ], {
    input: sql,
    encoding: "utf8",
    windowsHide: true,
    maxBuffer: 32 * 1024 * 1024
  });
  if (result.error || result.status !== 0) {
    const detail = redactSecrets(result.stderr || result.error?.message || "psql failed");
    throw new Error(`psql execution failed: ${String(detail).trim().slice(0, 1000)}`);
  }
  return result.stdout.trim();
}

async function executeSeedCommand(plan, sql, options = {}) {
  if (options.sqlOutput) {
    await writeText(options.sqlOutput, sql);
  }
  if (!options.database) {
    return { executed: false, sqlOutput: options.sqlOutput ? path.resolve(options.sqlOutput) : null, sql };
  }
  const stdout = executePsql(sql, options.database);
  return { executed: true, sqlOutput: options.sqlOutput ? path.resolve(options.sqlOutput) : null, stdout };
}

export async function applySeed(plan, options = {}) {
  return executeSeedCommand(plan, generateApplySql(plan), options);
}

export async function verifySeed(plan, options = {}) {
  const result = await executeSeedCommand(plan, generateVerifySql(plan), options);
  if (!result.executed) {
    return result;
  }
  const lines = result.stdout.split(/\r?\n/).filter(Boolean);
  let verification;
  try {
    verification = JSON.parse(lines.at(-1));
  } catch {
    throw new Error("psql verification did not return the expected JSON result");
  }
  return { ...result, verification, ok: verification.ok === true };
}

export async function cleanCheckSeed(plan, options = {}) {
  const result = await executeSeedCommand(plan, generateCleanCheckSql(plan), options);
  if (!result.executed) {
    return result;
  }
  const lines = result.stdout.split(/\r?\n/).filter(Boolean);
  let cleanCheck;
  try {
    cleanCheck = JSON.parse(lines.at(-1));
  } catch {
    throw new Error("psql clean-check did not return the expected JSON result");
  }
  return { ...result, cleanCheck, ok: cleanCheck.ok === true };
}

export async function cleanupSeed(plan, options = {}) {
  const result = await executeSeedCommand(plan, generateCleanupSql(plan), options);
  if (!result.executed) {
    return result;
  }
  const lines = result.stdout.split(/\r?\n/).filter(Boolean);
  let cleanup;
  try {
    cleanup = JSON.parse(lines.at(-1));
  } catch {
    throw new Error("psql cleanup did not return the expected JSON result");
  }
  return { ...result, cleanup, ok: cleanup.ok === true };
}
