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
ON CONFLICT (role_id, permission_id) DO NOTHING;`;
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
  joined_at = EXCLUDED.joined_at;`;
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
         ${memberOrdinal} member_ordinal,
         CASE WHEN batch.ordinal <= ${memberCount * 2}
           THEN 'project' ELSE 'knowledge_content' END resource_type,
         CASE WHEN batch.ordinal <= ${memberCount * 2}
           THEN ${recordUuidExpression(plan, "project", localOrdinal("batch.ordinal", countFor(plan, "project")))}
           ELSE ${recordUuidExpression(plan, "knowledge-item", localOrdinal("batch.ordinal", countFor(plan, "knowledge-item")))}
         END resource_id,
         ${permissionRoleCase(plan, memberOrdinal)} role_name
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
       'cap-${createHash("sha256").update(plan.seedId).digest("hex").slice(0, 10)}-' || roots.workspace_ordinal,
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
  updated_at = EXCLUDED.updated_at;`;
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

export function generateVerifySql(plan) {
  const validation = validateSeedPlan(plan);
  if (!validation.ok) {
    throw new Error(`invalid seed plan: ${validation.errors.join("; ")}`);
  }
  const schema = plan.fixtureSchema;
  const credentialSource = plan.credentialSource;
  const expectedRows = plan.phases.map((phase) => `(${sqlLiteral(phase.domain)}, ${phase.count}::bigint)`).join(",\n    ");
  const rolePrefix = fixtureRolePrefix(plan);
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
  SELECT e.domain, e.expected_count, coalesce(a.actual_count, 0) AS actual_count
  FROM expected e
  LEFT JOIN registry_actual a USING (domain)
  WHERE e.expected_count <> coalesce(a.actual_count, 0)
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
),
count_mismatches AS (
  SELECT e.domain, e.expected_count, coalesce(a.actual_count, 0) actual_count
  FROM expected e
  LEFT JOIN business_actual a USING (domain)
  WHERE e.expected_count <> coalesce(a.actual_count, 0)
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
),
support_mismatches AS (
  SELECT 'knowledge_base_spaces' support, ${plan.workspaceIds.length}::bigint expected_count, count(*)::bigint actual_count
  FROM knowledge_base_spaces s
  JOIN ${schema}.fixture_records r ON r.record_id = s.root_item_id
  WHERE r.seed_id = ${sqlLiteral(plan.seedId)} AND r.checksum = ${sqlLiteral(plan.checksum)} AND r.domain = 'knowledge-item'
  HAVING count(*) <> ${plan.workspaceIds.length}
  UNION ALL
  SELECT 'roles', ${(plan.workspaceIds.length * 5)}::bigint, count(*)::bigint
  FROM roles
  WHERE code LIKE ${sqlLiteral(`${rolePrefix}%`)}
  HAVING count(*) <> ${plan.workspaceIds.length * 5}
  UNION ALL
  SELECT 'conversations', ${countFor(plan, "project")}::bigint, count(*)::bigint
  FROM conversations c
  JOIN ${schema}.fixture_records p ON p.record_id = c.project_id
  WHERE p.seed_id = ${sqlLiteral(plan.seedId)} AND p.checksum = ${sqlLiteral(plan.checksum)} AND p.domain = 'project'
  HAVING count(*) <> ${countFor(plan, "project")}
  UNION ALL
  SELECT 'conversation_members', ${(countFor(plan, "project") * 2)}::bigint, count(*)::bigint
  FROM conversation_members cm
  JOIN conversations c ON c.id = cm.conversation_id
  JOIN ${schema}.fixture_records p ON p.record_id = c.project_id
  WHERE p.seed_id = ${sqlLiteral(plan.seedId)} AND p.checksum = ${sqlLiteral(plan.checksum)} AND p.domain = 'project'
  HAVING count(*) <> ${countFor(plan, "project") * 2}
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

function deterministicResidueCount(plan, table, column, domain, count = countFor(plan, domain)) {
  return `(SELECT count(*)
    FROM ${table} target
    JOIN generate_series(1, ${count}) AS fixture(ordinal)
      ON target.${column} = ${recordIdExpression(plan, domain, "fixture.ordinal")})`;
}

function businessResidueExpression(plan) {
  const workspaceIds = plan.workspaceIds
    .map((workspace) => `${sqlLiteral(workspace.id)}::uuid`)
    .join(", ");
  const roleIds = roleIdsValues(plan);
  const projectCount = countFor(plan, "project");
  const conversationId = `md5(${sqlLiteral(`${plan.seedId}:conversation:`)} || fixture.ordinal::text)::uuid`;
  return [
    `(SELECT count(*) FROM workspaces WHERE id IN (${workspaceIds}))`,
    deterministicResidueCount(plan, "users", "id", "member"),
    deterministicResidueCount(plan, "resource_permissions", "id", "permission"),
    deterministicResidueCount(plan, "role_assignments", "id", "permission"),
    `(SELECT count(*) FROM roles WHERE id IN (
      SELECT role_id FROM (VALUES ${roleIds}) AS fixture_roles(workspace_ordinal, role_name, role_id)
    ))`,
    `(SELECT count(*) FROM role_permissions WHERE role_id IN (
      SELECT role_id FROM (VALUES ${roleIds}) AS fixture_roles(workspace_ordinal, role_name, role_id)
    ))`,
    deterministicResidueCount(plan, "projects", "id", "project"),
    deterministicResidueCount(plan, "issues", "id", "issue"),
    deterministicResidueCount(plan, "knowledge_base_items", "id", "knowledge-item"),
    deterministicResidueCount(plan, "knowledge_base_spaces", "root_item_id", "knowledge-item"),
    deterministicResidueCount(plan, "knowledge_content_blocks", "id", "knowledge-block"),
    deterministicResidueCount(plan, "knowledge_content_collaboration_tickets", "item_id", "knowledge-item"),
    deterministicResidueCount(plan, "knowledge_content_collaboration_updates", "item_id", "knowledge-item"),
    deterministicResidueCount(plan, "knowledge_content_collaboration_states", "id", "collaboration-room"),
    deterministicResidueCount(plan, "notifications", "id", "notification"),
    deterministicResidueCount(plan, "messages", "id", "im-message"),
    deterministicResidueCount(plan, "files", "id", "file"),
    `(SELECT count(*)
      FROM conversations target
      JOIN generate_series(1, ${projectCount}) AS fixture(ordinal)
        ON target.id = ${conversationId})`,
    `(SELECT count(*)
      FROM conversation_members target
      JOIN generate_series(1, ${projectCount}) AS fixture(ordinal)
        ON target.conversation_id = ${conversationId})`
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
    AND fixture_records = 0
    AND fixture_workspaces = 0
    AND conflicting_runs = 0
    AND business_records = 0,
  'fixtureRegistryExists', ${fixtureCounts},
  'fixtureRuns', fixture_runs,
  'fixturePhases', fixture_phases,
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
\\if :fixture_registry_exists
WITH clean_counts AS (
  SELECT
    (SELECT count(*) FROM ${schema}.fixture_runs
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
         OR fixture_name = ${sqlLiteral(plan.fixtureName)}) AS fixture_runs,
    (SELECT count(*) FROM ${schema}.fixture_phase_progress
      WHERE seed_id = ${sqlLiteral(plan.seedId)}) AS fixture_phases,
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

export function generateCleanupSql(plan) {
  const validation = validateSeedPlan(plan);
  if (!validation.ok) {
    throw new Error(`invalid seed plan: ${validation.errors.join("; ")}`);
  }
  const schema = plan.fixtureSchema;
  const roleIds = roleIdsValues(plan);
  const workspaceIds = plan.workspaceIds
    .map((workspace) => `${sqlLiteral(workspace.id)}::uuid`)
    .join(", ");
  const businessResidue = businessResidueExpression(plan);
  return `-- Cleanup is restricted to rows owned by the exact named seed id and checksum.
\\set ON_ERROR_STOP on
BEGIN;
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

DELETE FROM knowledge_content_collaboration_tickets
WHERE item_id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'knowledge-item'
);
DELETE FROM knowledge_content_collaboration_updates
WHERE item_id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'knowledge-item'
);
DELETE FROM knowledge_content_collaboration_states
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'collaboration-room'
);

UPDATE conversations
SET last_message_id = NULL, last_message_at = NULL
WHERE project_id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'project'
);
DELETE FROM messages
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'im-message'
);
DELETE FROM conversation_members
WHERE conversation_id IN (
  SELECT id FROM conversations
  WHERE project_id IN (
    SELECT record_id FROM ${schema}.fixture_records
    WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'project'
  )
);
DELETE FROM conversations
WHERE project_id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'project'
);

DELETE FROM notifications
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'notification'
);
DELETE FROM resource_permissions
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'permission'
);
DELETE FROM role_assignments
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'permission'
);
WITH fixture_roles(workspace_ordinal, role_name, role_id) AS (
  VALUES
    ${roleIds}
)
DELETE FROM role_permissions
WHERE role_id IN (SELECT role_id FROM fixture_roles);
WITH fixture_roles(workspace_ordinal, role_name, role_id) AS (
  VALUES
    ${roleIds}
)
DELETE FROM roles
WHERE id IN (SELECT role_id FROM fixture_roles);

DELETE FROM knowledge_content_blocks
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'knowledge-block'
);
DELETE FROM knowledge_base_spaces
WHERE root_item_id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'knowledge-item'
);
DELETE FROM knowledge_base_items
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'knowledge-item'
);

DELETE FROM issues
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'issue'
);
DELETE FROM projects
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'project'
);
DELETE FROM files
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'file'
);
DELETE FROM users
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'member'
);
DELETE FROM workspaces
WHERE id IN (
  SELECT record_id FROM ${schema}.fixture_records
  WHERE seed_id = ${sqlLiteral(plan.seedId)} AND checksum = ${sqlLiteral(plan.checksum)} AND domain = 'workspace'
);

DELETE FROM ${schema}.fixture_records
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)};
DELETE FROM ${schema}.fixture_phase_progress
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)};
DELETE FROM ${schema}.fixture_runs
WHERE seed_id = ${sqlLiteral(plan.seedId)}
  AND checksum = ${sqlLiteral(plan.checksum)}
  AND fixture_name = ${sqlLiteral(plan.fixtureName)};
COMMIT;

WITH cleanup_counts AS (
  SELECT
    (SELECT count(*) FROM ${schema}.fixture_runs
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_runs,
    (SELECT count(*) FROM ${schema}.fixture_phase_progress
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_phases,
    (SELECT count(*) FROM ${schema}.fixture_records
      WHERE seed_id = ${sqlLiteral(plan.seedId)}
        AND checksum = ${sqlLiteral(plan.checksum)}) AS fixture_records,
    (SELECT count(*) FROM workspaces
      WHERE id IN (${workspaceIds})) AS fixture_workspaces,
    (${businessResidue}) AS business_records
)
SELECT json_build_object(
  'schemaVersion', 'colla.capacity-seed-cleanup/v1',
  'evidenceKind', 'cleanup',
  'seedId', ${sqlLiteral(plan.seedId)},
  'checksum', ${sqlLiteral(plan.checksum)},
  'fixtureName', ${sqlLiteral(plan.fixtureName)},
  'ok', fixture_runs = 0
    AND fixture_phases = 0
    AND fixture_records = 0
    AND fixture_workspaces = 0
    AND business_records = 0,
  'fixtureRuns', fixture_runs,
  'fixturePhases', fixture_phases,
  'fixtureRecords', fixture_records,
  'fixtureWorkspaces', fixture_workspaces,
  'businessRecords', business_records
)::text
FROM cleanup_counts;
`;
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
  return executeSeedCommand(plan, generateCleanupSql(plan), options);
}
