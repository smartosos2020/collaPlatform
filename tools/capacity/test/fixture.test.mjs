import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import test from "node:test";
import {
  MAX_FIXTURE_DERIVATIONS,
  MAX_FIXTURE_ORDINAL,
  deriveCollaborationRooms,
  deriveEntityUuid,
  deriveFixtureUsername,
  deriveFixtureUsers,
  deriveKnowledgeSpaceUuid,
  deriveWorkspaceUuid,
  validateFixtureConfig,
  workspaceOrdinalFor
} from "../src/fixture.mjs";

const config = Object.freeze({
  seedId: "s05-business-seed-test",
  workspaceWeights: Object.freeze([50, 25, 15, 10])
});

test("fixture config validation accepts only the bounded public derivation contract", () => {
  assert.deepEqual(validateFixtureConfig(config), { ok: true, errors: [] });
  assert.equal(validateFixtureConfig(null).ok, false);
  assert.equal(validateFixtureConfig({ ...config, password: "secret" }).ok, false);
  assert.equal(validateFixtureConfig({ ...config, credentialSource: {} }).ok, false);
  assert.equal(validateFixtureConfig({ seedId: "bad:seed", workspaceWeights: [50, 50] }).ok, false);
  assert.equal(validateFixtureConfig({ seedId: "seed", workspaceWeights: [99, 0, 1] }).ok, false);
  assert.equal(validateFixtureConfig({ seedId: "seed", workspaceWeights: [49, 50] }).ok, false);
  assert.equal(validateFixtureConfig({ seedId: "seed", workspaceWeights: [100] }).ok, false);
  assert.equal(
    validateFixtureConfig({
      seedId: "seed",
      workspaceWeights: Object.assign([50, 50], { password: "secret" })
    }).ok,
    false
  );
  assert.equal(
    validateFixtureConfig({
      seedId: "seed",
      workspaceWeights: Object.assign(Array(3), { 0: 50, 2: 50 })
    }).ok,
    false
  );
});

test("workspace UUIDs match seed deterministicUuid version and variant handling", () => {
  assert.equal(deriveWorkspaceUuid(config, 1), "cd628430-ceef-595b-8171-0e557dd97f77");
  assert.equal(deriveWorkspaceUuid(config, 4), "9e8d37b2-0d59-5a66-803d-5f9b7e47cc01");
  assert.equal(deriveWorkspaceUuid(config, 1)[14], "5");
  assert.match(deriveWorkspaceUuid(config, 1)[19], /[89ab]/);
});

test("raw entity and knowledge-space UUIDs preserve PostgreSQL md5(text)::uuid bits", () => {
  const workspaceId = deriveWorkspaceUuid(config, 2);
  assert.equal(deriveEntityUuid(config, "member", 1), "f9afdf16-4b61-475e-704a-d11d0dd69215");
  assert.equal(
    deriveEntityUuid(config, "knowledge-item", 51),
    "d5af2744-23c5-1708-537b-dc43ac41adfb"
  );
  assert.equal(workspaceId, "3703e821-3658-55fe-b74e-61359ce8f393");
  assert.equal(
    deriveKnowledgeSpaceUuid(config, workspaceId),
    "7f20d8d6-6419-b6f2-c8fa-92ab61e73a41"
  );
  assert.notEqual(deriveEntityUuid(config, "member", 1)[14], "5");
});

test("fixture usernames use the seed SHA-256 prefix and exactly seven ordinal digits", () => {
  assert.equal(deriveFixtureUsername(config, 1), "cap_71c4a20583_u0000001");
  assert.equal(
    deriveFixtureUsername(config, MAX_FIXTURE_ORDINAL),
    "cap_71c4a20583_u9999999"
  );
});

test("workspace assignment follows cumulative weights over modulo-100 percentiles", () => {
  for (const [ordinal, expected] of [
    [1, 1],
    [50, 1],
    [51, 2],
    [75, 2],
    [76, 3],
    [90, 3],
    [91, 4],
    [100, 4],
    [101, 1]
  ]) {
    assert.equal(workspaceOrdinalFor(config, ordinal), expected, String(ordinal));
  }
});

test("fixture users start at the requested ordinal and preserve disabled boundaries", () => {
  const users = deriveFixtureUsers(config, {
    count: 7,
    startOrdinal: 94,
    activeOnly: false
  });
  assert.deepEqual(users.map((user) => user.ordinal), [94, 95, 96, 97, 98, 99, 100]);
  assert.deepEqual(
    users.map((user) => user.status),
    ["active", "active", "disabled", "disabled", "disabled", "disabled", "disabled"]
  );
  assert.equal(users[0].workspaceOrdinal, 4);
  assert.equal(users[0].username, "cap_71c4a20583_u0000094");
  assert.equal(users[0].email, "cap_71c4a20583_u0000094@capacity.invalid");
  assert.equal(users[0].id, deriveEntityUuid(config, "member", 94));
});

test("active and workspace filters derive the requested number of matching users", () => {
  const active = deriveFixtureUsers(config, {
    count: 4,
    startOrdinal: 94,
    activeOnly: true
  });
  assert.deepEqual(active.map((user) => user.ordinal), [94, 95, 101, 102]);
  assert.ok(active.every((user) => user.status === "active"));

  const workspaceTwo = deriveFixtureUsers(config, {
    count: 4,
    startOrdinal: 74,
    activeOnly: true,
    workspaceOrdinal: 2
  });
  assert.deepEqual(workspaceTwo.map((user) => user.ordinal), [74, 75, 151, 152]);
  assert.ok(workspaceTwo.every((user) => user.workspaceOrdinal === 2));
});

test("collaboration rooms contain canonical space/item metadata without documents", () => {
  const rooms = deriveCollaborationRooms(config, {
    workspaceOrdinal: 2,
    knowledgeItemOrdinals: [51, 75, 151]
  });
  assert.equal(rooms.length, 3);
  assert.deepEqual(rooms[0], {
    name: "knowledge:3703e821-3658-55fe-b74e-61359ce8f393:d5af2744-23c5-1708-537b-dc43ac41adfb",
    workspaceOrdinal: 2,
    workspaceId: "3703e821-3658-55fe-b74e-61359ce8f393",
    spaceId: "7f20d8d6-6419-b6f2-c8fa-92ab61e73a41",
    knowledgeItemOrdinal: 51,
    itemId: "d5af2744-23c5-1708-537b-dc43ac41adfb"
  });
  assert.ok(rooms.every((room) => !Object.hasOwn(room, "document")));
  assert.ok(rooms.every((room) => !Object.hasOwn(room, "documentId")));
});

test("derivation inputs fail closed on domains, ownership, options, and secrets", () => {
  assert.throws(() => deriveEntityUuid(config, "workspace", 1), /domain must be one of/);
  assert.throws(() => deriveEntityUuid(config, "unknown", 1), /domain must be one of/);
  assert.throws(() => deriveKnowledgeSpaceUuid(config, randomUUID()), /does not belong/);
  assert.throws(
    () => deriveFixtureUsers(config, { count: 1, token: "secret" }),
    /unsupported fields: token/
  );
  assert.throws(
    () => deriveCollaborationRooms(config, {
      workspaceOrdinal: 2,
      knowledgeItemOrdinals: [50]
    }),
    /assigned to another workspace/
  );
  assert.throws(
    () => deriveCollaborationRooms(config, {
      workspaceOrdinal: 2,
      knowledgeItemOrdinals: [51, 51]
    }),
    /must not contain duplicates/
  );
  assert.throws(
    () => deriveCollaborationRooms(config, {
      workspaceOrdinal: 2,
      knowledgeItemOrdinals: Object.assign([51], { token: "secret" })
    }),
    /dense array without extra fields/
  );
});

test("counts, ordinals, filters, and arrays are bounded", () => {
  assert.deepEqual(deriveFixtureUsers(config, { count: 0 }), []);
  assert.deepEqual(
    deriveCollaborationRooms(config, {
      workspaceOrdinal: 1,
      knowledgeItemOrdinals: []
    }),
    []
  );
  assert.throws(
    () => deriveFixtureUsers(config, { count: MAX_FIXTURE_DERIVATIONS + 1 }),
    /count must be an integer/
  );
  assert.throws(
    () => deriveFixtureUsers(config, {
      count: 1,
      startOrdinal: MAX_FIXTURE_ORDINAL,
      activeOnly: true
    }),
    /exceed the maximum fixture ordinal/
  );
  assert.throws(
    () => deriveFixtureUsers(config, { count: 1, activeOnly: "true" }),
    /activeOnly must be a boolean/
  );
  assert.throws(
    () => deriveFixtureUsers(
      { seedId: "seed", workspaceWeights: [95, 5] },
      { count: 1, activeOnly: true, workspaceOrdinal: 2 }
    ),
    /cannot match any fixture user/
  );
  assert.throws(
    () => deriveCollaborationRooms(config, {
      workspaceOrdinal: 1,
      knowledgeItemOrdinals: Array(MAX_FIXTURE_DERIVATIONS + 1).fill(1)
    }),
    /at most/
  );
});
