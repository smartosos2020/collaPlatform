import assert from "node:assert/strict";
import test from "node:test";
import { loadCapacityConfig } from "../src/contract.mjs";
import {
  createSeedPlan,
  generateApplySql,
  generateCleanupSql,
  generateVerifySql,
  validateSeedConfig
} from "../src/seed.mjs";

const { seed } = await loadCapacityConfig();
const plan = createSeedPlan("s05-business-seed-test", seed);
const applySql = generateApplySql(plan);
const verifySql = generateVerifySql(plan);
const cleanupSql = generateCleanupSql(plan);

test("checked-in seed contract freezes the required business scale", () => {
  assert.deepEqual(validateSeedConfig(seed), { ok: true, errors: [] });
  assert.equal(seed.workspaceCount, 4);
  assert.deepEqual(
    Object.fromEntries(seed.domains.map((domain) => [domain.name, domain.count])),
    {
      workspace: 4,
      member: 2000,
      permission: 6000,
      project: 200,
      issue: 1_000_000,
      "knowledge-item": 100_000,
      "knowledge-block": 1_000_000,
      notification: 100_000,
      "im-message": 200_000,
      file: 50_000,
      "collaboration-room": 25
    }
  );
  assert.equal(plan.expectedRecordCount, 2_458_229);
  assert.equal(plan.fixedTimestamp, "2026-01-01T00:00:00.000Z");
  assert.deepEqual(plan.credentialSource, {
    type: "initialized-user-password-hash",
    username: "admin",
    requiredStatus: "active",
    requiredNotDeleted: true,
    outputFingerprint: "md5-of-stored-password-hash"
  });
  assert.ok(plan.phases.every((phase) => phase.batchSize > 0));
});

test("apply SQL materializes registry records into every required business table", () => {
  for (const table of [
    "workspaces",
    "users",
    "roles",
    "role_permissions",
    "role_assignments",
    "resource_permissions",
    "projects",
    "issues",
    "knowledge_base_items",
    "knowledge_base_spaces",
    "knowledge_content_blocks",
    "notifications",
    "conversations",
    "conversation_members",
    "messages",
    "files",
    "knowledge_content_collaboration_states"
  ]) {
    assert.match(applySql, new RegExp(`INSERT INTO ${table}\\b`, "i"), table);
  }
  assert.match(applySql, /fixture_phase_progress/);
  assert.match(applySql, /requires exactly one active initialized credential source/);
  assert.match(applySql, /WHERE username = 'admin'/);
  assert.doesNotMatch(applySql, /ORDER BY \(username = 'admin'\)/);
  assert.match(applySql, /materialized_count >= 10000/);
  assert.match(applySql, /\\if :capacity_skip_chunk/);
  assert.match(applySql, /ON CONFLICT \(id\) DO UPDATE/);
  assert.doesNotMatch(applySql, /INSERT INTO\s+(?:domain_events|event_outbox|outbox_events)\b/i);
});

test("member identities are globally unique and relationships stay workspace scoped", () => {
  assert.match(applySql, /cap_[0-9a-f]{10}_u/);
  assert.match(verifySql, /fixture_usernames/);
  assert.match(verifySql, /JOIN users ON users\.username = fixture_usernames\.username/);
  assert.match(verifySql, /role-assignment\.relations/);
  assert.match(verifySql, /resource-permission\.relations/);
  assert.match(verifySql, /issue\.relations/);
  assert.match(verifySql, /knowledge-item\.relations/);
  assert.match(verifySql, /knowledge-block\.item/);
  assert.match(verifySql, /message\.relations/);
  assert.match(verifySql, /collaboration\.item/);
  for (const workspace of plan.workspaceIds) {
    assert.match(applySql, new RegExp(workspace.id));
  }
});

test("verification checks exact business counts and required support rows", () => {
  assert.match(verifySql, /business_actual AS/);
  assert.match(verifySql, /registryCountMismatches/);
  assert.match(verifySql, /knowledge_base_spaces/);
  assert.match(verifySql, /conversation_members/);
  assert.match(verifySql, /duplicateUsernames/);
  assert.match(verifySql, /workspaceIsolationLeaks/);
  assert.match(verifySql, /credential_source AS/);
  assert.match(verifySql, /fixture_credential_fingerprints AS/);
  assert.match(verifySql, /passwordHashFingerprint/);
  assert.match(verifySql, /fixtureFingerprintMatches/);
});

test("cleanup is named, checksum guarded, and ordered from children to fixture workspaces", () => {
  assert.match(cleanupSql, new RegExp(plan.checksum));
  assert.match(cleanupSql, /colla\.capacity-seed-cleanup\/v1/);
  assert.match(cleanupSql, /'evidenceKind', 'cleanup'/);
  assert.match(cleanupSql, new RegExp(`'fixtureName', '${plan.fixtureName}'`));
  assert.match(cleanupSql, /'businessRecords', business_records/);
  assert.match(cleanupSql, /AND business_records = 0/);
  assert.doesNotMatch(cleanupSql, /\b(?:TRUNCATE|DROP SCHEMA|DELETE FROM domain_events)\b/i);

  const order = [
    "DELETE FROM knowledge_content_collaboration_tickets",
    "DELETE FROM knowledge_content_collaboration_states",
    "DELETE FROM messages",
    "DELETE FROM conversation_members",
    "DELETE FROM conversations",
    "DELETE FROM resource_permissions",
    "DELETE FROM role_assignments",
    "DELETE FROM role_permissions",
    "DELETE FROM roles",
    "DELETE FROM knowledge_content_blocks",
    "DELETE FROM knowledge_base_spaces",
    "DELETE FROM knowledge_base_items",
    "DELETE FROM issues",
    "DELETE FROM projects",
    "DELETE FROM files",
    "DELETE FROM users",
    "DELETE FROM workspaces",
    "DELETE FROM capacity_fixture.fixture_records",
    "DELETE FROM capacity_fixture.fixture_runs"
  ];
  let previous = -1;
  for (const statement of order) {
    const current = cleanupSql.indexOf(statement);
    assert.ok(current > previous, `${statement} must follow its dependent cleanup`);
    previous = current;
  }
});
