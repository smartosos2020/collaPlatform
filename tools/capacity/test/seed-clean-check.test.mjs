import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  main,
  seedActionExitCode,
  seedActionRequiresPass
} from "../src/cli.mjs";
import { loadCapacityConfig } from "../src/contract.mjs";
import {
  createSeedPlan,
  generateCleanCheckSql
} from "../src/seed.mjs";

const { seed } = await loadCapacityConfig();
const plan = createSeedPlan("s05-clean-check-test", seed);

test("clean-check SQL proves every named seed registry and workspace count is zero", () => {
  const sql = generateCleanCheckSql(plan);

  assert.match(sql, /colla\.capacity-seed-clean-check\/v1/);
  assert.match(sql, /to_regclass\('capacity_fixture\.fixture_runs'\)/);
  assert.match(sql, /\\if :fixture_registry_exists/);
  assert.match(sql, /\\else/);
  assert.match(sql, /'evidenceKind', 'clean-state'/);
  assert.match(sql, /'ok', fixture_runs = 0/);
  assert.match(sql, /fixture_phases = 0/);
  assert.match(sql, /fixture_cleanup = 0/);
  assert.match(sql, /fixture_records = 0/);
  assert.match(sql, /fixture_workspaces = 0/);
  assert.match(sql, /conflicting_runs = 0/);
  assert.match(sql, /'fixtureRuns', fixture_runs/);
  assert.match(sql, /'fixturePhases', fixture_phases/);
  assert.match(sql, /'fixtureCleanupProgress', fixture_cleanup/);
  assert.match(sql, /'fixtureRecords', fixture_records/);
  assert.match(sql, /'fixtureWorkspaces', fixture_workspaces/);
  assert.match(sql, /'conflictingRuns', conflicting_runs/);
  assert.match(sql, /'businessRecords', business_records/);
  assert.match(sql, /knowledge_base_items/);
  assert.match(sql, /knowledge_content_collaboration_states/);
  assert.match(sql, /conversation_members/);
  assert.match(sql, /role_permissions/);
  assert.match(sql, /roles/);
  assert.match(sql, /conversations/);
  assert.match(sql, /knowledge_base_spaces/);
  assert.match(sql, /user_roles/);
  assert.match(sql, /domain_events/);
  assert.match(sql, /audit_logs/);
  assert.doesNotMatch(sql, /generate_series/i);
  for (const workspace of plan.workspaceIds) {
    assert.match(sql, new RegExp(workspace.id));
  }
  assert.doesNotMatch(sql, /\b(?:INSERT|UPDATE|DELETE|TRUNCATE|DROP)\b/i);
});

test("cleanup is a checked CLI action and failed cleanup returns a nonzero exit", () => {
  assert.equal(seedActionRequiresPass("cleanup"), true);
  assert.equal(seedActionExitCode("cleanup", { executed: true, ok: false }), 4);
  assert.equal(seedActionExitCode("cleanup", { executed: true, ok: true }), 0);
  assert.equal(seedActionExitCode("cleanup", { executed: false }), 0);
});

test("clean-check treats checksum and fixture-name collisions as conflicting runs", () => {
  const sql = generateCleanCheckSql(plan);

  assert.match(
    sql,
    new RegExp(`seed_id = '${plan.seedId}'\\s+OR fixture_name = '${plan.fixtureName}'`)
  );
  assert.match(sql, new RegExp(`checksum = '${plan.checksum}'`));
  assert.match(
    sql,
    new RegExp(`AND NOT \\(\\s*seed_id = '${plan.seedId}'[\\s\\S]*fixture_name = '${plan.fixtureName}'`)
  );
});

test("seed clean-check CLI writes the generated SQL for a plan", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "colla-seed-clean-check-"));
  try {
    const planPath = path.join(directory, "seed-plan.json");
    const sqlPath = path.join(directory, "seed-clean-check.sql");
    await writeFile(planPath, `${JSON.stringify(plan, null, 2)}\n`, "utf8");

    const output = [];
    const originalWrite = process.stdout.write;
    process.stdout.write = (chunk) => {
      output.push(String(chunk));
      return true;
    };
    try {
      await main([
        "seed",
        "clean-check",
        "--plan",
        planPath,
        "--sql",
        sqlPath
      ]);
    } finally {
      process.stdout.write = originalWrite;
    }

    assert.deepEqual(JSON.parse(output.join("")), {
      ok: true,
      executed: false,
      cleanCheckPending: true,
      sqlOutput: sqlPath
    });
    assert.equal(await readFile(sqlPath, "utf8"), generateCleanCheckSql(plan));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
