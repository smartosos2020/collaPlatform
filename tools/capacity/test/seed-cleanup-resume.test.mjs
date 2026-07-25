import assert from "node:assert/strict";
import test from "node:test";
import { loadCapacityConfig } from "../src/contract.mjs";
import {
  createSeedPlan,
  generateApplySql,
  generateCleanupSql
} from "../src/seed.mjs";

const { seed } = await loadCapacityConfig();
const plan = createSeedPlan("s05-cleanup-resume-test", seed);
const applySql = generateApplySql(plan);
const cleanupSql = generateCleanupSql(plan);

test("support rows receive direct checksum-guarded ownership records", () => {
  for (const domain of [
    "support-role",
    "support-role-permission",
    "support-conversation",
    "support-conversation-member",
    "support-knowledge-space"
  ]) {
    assert.match(applySql, new RegExp(`'${domain}'`));
    assert.match(cleanupSql, new RegExp(`'${domain}'`));
  }

  assert.match(applySql, /'roleId', owned_role_permissions\.role_id/);
  assert.match(applySql, /'permissionId', owned_role_permissions\.permission_id/);
  assert.match(cleanupSql, /target\.role_id = \(owned\.payload ->> 'roleId'\)::uuid/);
  assert.match(cleanupSql, /target\.permission_id = \(owned\.payload ->> 'permissionId'\)::uuid/);
  assert.doesNotMatch(cleanupSql, /WHERE project_id IN/);
  assert.doesNotMatch(cleanupSql, /WHERE conversation_id IN/);
  assert.doesNotMatch(cleanupSql, /DELETE FROM role_permissions\s+WHERE role_id IN/);
});

test("large cleanup domains are committed in bounded resumable chunks", () => {
  const issueChunks = cleanupSql.match(/-- cleanup issue chunk /g) ?? [];
  const blockChunks = cleanupSql.match(/-- cleanup knowledge-block chunk /g) ?? [];
  const registryDeletes = cleanupSql.match(/DELETE FROM capacity_fixture\.fixture_records/g) ?? [];

  assert.equal(issueChunks.length, 100);
  assert.equal(blockChunks.length, 100);
  assert.ok(registryDeletes.length > 200);
  assert.match(cleanupSql, /SET lock_timeout = '5s'/);
  assert.match(cleanupSql, /SET statement_timeout = '120s'/);
  assert.match(cleanupSql, /SET LOCAL lock_timeout = '5s'/);
  assert.match(cleanupSql, /SET LOCAL statement_timeout = '120s'/);

  assert.match(
    cleanupSql,
    /-- cleanup issue chunk 1-10000[\s\S]*?BEGIN;[\s\S]*?DELETE FROM issues target[\s\S]*?owned\.ordinal BETWEEN 1 AND 10000[\s\S]*?DELETE FROM capacity_fixture\.fixture_records owned[\s\S]*?completed_ordinal = 10000[\s\S]*?COMMIT;/
  );
  assert.match(cleanupSql, /skip committed cleanup issue chunk 1-10000/);
});

test("cleanup progress is checksum guarded and validates remaining ownership on resume", () => {
  assert.match(cleanupSql, /CREATE TABLE IF NOT EXISTS capacity_fixture\.fixture_cleanup_progress/);
  assert.match(cleanupSql, /PRIMARY KEY \(seed_id, domain\)/);
  assert.match(cleanupSql, /fixture_cleanup_progress_domain_chk/);
  assert.match(cleanupSql, /fixture_cleanup_progress_range_chk/);
  assert.match(cleanupSql, /fixture_cleanup_progress_status_chk/);
  assert.match(cleanupSql, /expected_count >= 0/);
  assert.match(cleanupSql, /completed_ordinal <= expected_count/);
  assert.match(cleanupSql, /ON CONFLICT \(seed_id, domain\) DO NOTHING/);
  assert.match(
    cleanupSql,
    /progress\.expected_count - progress\.completed_ordinal <> \([\s\S]*?FROM capacity_fixture\.fixture_records owned/
  );
  assert.match(cleanupSql, /FULL JOIN actual USING \(domain\)/);
  assert.match(cleanupSql, /actual\.expected_count <> expected\.expected_count/);
  assert.match(cleanupSql, /expected\.domain IS NULL/);
  assert.match(cleanupSql, /actual\.domain IS NULL/);
  assert.match(cleanupSql, /cleanup progress does not match the exact domain and expected-count contract/);
  assert.match(cleanupSql, /cleanup progress does not match the exact remaining ownership ordinal set/);
  assert.match(cleanupSql, new RegExp(plan.checksum));
  assert.match(cleanupSql, /status = 'deleted' OR completed_ordinal >=/);
});

test("equal-count ownership replacement fails identity validation before deletion", () => {
  const issueChunk = cleanupSql.slice(
    cleanupSql.indexOf("-- cleanup issue chunk 1-10000"),
    cleanupSql.indexOf("-- cleanup issue chunk 10001-20000")
  );
  const identityGuard = issueChunk.indexOf("capacity_fixture_ownership_identity_guard");
  const businessDelete = issueChunk.indexOf("DELETE FROM issues target");

  assert.ok(identityGuard >= 0);
  assert.ok(businessDelete > identityGuard);
  assert.match(issueChunk, /md5\('s05-cleanup-resume-test:issue:' \|\| \(g\)::text\)::uuid record_id/);
  assert.match(issueChunk, /actual\.workspace_id IS DISTINCT FROM expected\.workspace_id/);
  assert.match(issueChunk, /actual\.record_id IS DISTINCT FROM expected\.record_id/);
  assert.match(issueChunk, /actual\.fixture_key IS DISTINCT FROM expected\.fixture_key/);
  assert.match(issueChunk, /actual\.payload IS DISTINCT FROM expected\.payload/);
  assert.match(issueChunk, /RAISE EXCEPTION 'capacity fixture ownership identity mismatch for domain issue'/);
});

test("support knowledge-space ownership uses contiguous workspace ordinals", () => {
  const supportSection = cleanupSql.slice(
    cleanupSql.indexOf("-- cleanup support-knowledge-space chunk"),
    cleanupSql.indexOf("-- cleanup knowledge-item chunk")
  );
  assert.match(supportSection, /1::bigint/);
  assert.match(supportSection, /2::bigint/);
  assert.match(supportSection, /3::bigint/);
  assert.match(supportSection, /4::bigint/);
  assert.doesNotMatch(supportSection, /41::bigint|71::bigint|91::bigint/);
});

test("self-referencing knowledge items are deleted from high ordinals to roots", () => {
  const first = cleanupSql.indexOf("-- cleanup knowledge-item chunk 95001-100000");
  const last = cleanupSql.indexOf("-- cleanup knowledge-item chunk 1-5000");

  assert.ok(first >= 0);
  assert.ok(last > first);
  assert.match(
    cleanupSql,
    /-- cleanup knowledge-item chunk 95001-100000[\s\S]*?completed_ordinal = 5000/
  );
  assert.match(
    cleanupSql,
    /-- cleanup knowledge-item chunk 1-5000[\s\S]*?completed_ordinal = 100000/
  );
});

test("final registry teardown only runs after every cleanup domain is complete", () => {
  assert.match(
    cleanupSql,
    /SELECT count\(\*\) = 16 AS capacity_cleanup_complete[\s\S]*?status = 'deleted'[\s\S]*?completed_ordinal = expected_count/
  );
  assert.match(cleanupSql, /\\if :capacity_cleanup_complete/);
  assert.match(cleanupSql, /RAISE EXCEPTION 'capacity fixture cleanup is incomplete'/);
  const residueGuard = cleanupSql.indexOf("capacity_fixture_cleanup_residue_guard");
  const runDelete = cleanupSql.indexOf("DELETE FROM capacity_fixture.fixture_runs");
  assert.ok(residueGuard >= 0);
  assert.ok(runDelete > residueGuard);
  assert.match(cleanupSql, /capacity fixture registry residue remains; cleanup anchors were retained/);
  assert.match(cleanupSql, /capacity fixture business residue remains; derived rows are check-only and cleanup anchors were retained/);
  assert.match(cleanupSql, /SELECT count\(\*\) FROM user_roles WHERE workspace_id IN/);
  assert.match(cleanupSql, /SELECT count\(\*\) FROM domain_events WHERE workspace_id IN/);
  assert.match(cleanupSql, /SELECT count\(\*\) FROM audit_logs WHERE workspace_id IN/);
  assert.match(cleanupSql, /'fixtureCleanupProgress', fixture_cleanup/);
  assert.match(cleanupSql, /AND fixture_cleanup = 0/);
});
