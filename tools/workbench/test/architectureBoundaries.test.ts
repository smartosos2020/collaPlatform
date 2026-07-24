import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import test from 'node:test'
import { checkArchitectureBoundaries } from '../src/architecture/boundaries.js'

function write(root: string, path: string, content: string): void {
  const target = join(root, ...path.split('/'))
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, content)
}

function json(root: string, path: string, value: unknown): void {
  write(root, path, `${JSON.stringify(value, null, 2)}\n`)
}

function fixture(): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-boundaries-'))
  write(root, 'server/src/main/java/com/colla/platform/modules/identity/application/IdentityService.java', `
    package com.colla.platform.modules.identity.application;
    import com.colla.platform.modules.project.application.ProjectService;
    public class IdentityService {}
  `)
  write(root, 'server/src/main/java/com/colla/platform/modules/identity/contract/IdentityContract.java', `
    package com.colla.platform.modules.identity.contract;
    public interface IdentityContract {}
  `)
  write(root, 'server/src/main/java/com/colla/platform/modules/project/application/ProjectService.java', `
    package com.colla.platform.modules.project.application;
    public class ProjectService {}
  `)
  write(root, 'server/src/main/java/com/colla/platform/modules/project/contract/ProjectContract.java', `
    package com.colla.platform.modules.project.contract;
    public interface ProjectContract {}
  `)
  write(root, 'web/tsconfig.app.json', JSON.stringify({ compilerOptions: { baseUrl: '.', paths: { '@/*': ['src/*'] } } }))
  write(root, 'web/src/modules/identity/index.ts', 'export const identity = true')
  write(root, 'web/src/modules/project/index.ts', 'export const project = true')
  write(root, 'server/src/main/resources/db/migration/V001__tables.sql', `
    create table users (id uuid primary key);
    create table projects (id uuid primary key);
  `)
  write(root, 'docs/01-architecture/platform-module-contracts.md', '模块 公开合同 table owner 组合查询 流程协调器 同步 异步 非目标 最小披露 事务')
  json(root, 'tools/workbench/config/platform-modules.json', {
    schemaVersion: 1,
    contractVersion: 1,
    rootPackage: 'com.colla.platform.modules',
    contractPolicy: {
      publicPackage: 'contract',
      allowedKinds: ['facade'],
      forbiddenProviderLayers: ['api', 'application', 'domain', 'infrastructure'],
      compatibility: 'additive',
    },
    modules: ['identity', 'project'].map((name) => ({
      name,
      owner: `${name}-owner`,
      rootPackage: `com.colla.platform.modules.${name}`,
      publicContract: `com.colla.platform.modules.${name}.contract`,
      privatePackages: ['api', 'application', 'domain', 'infrastructure'],
      compositionRole: 'none',
      compositionPermission: 'not-applicable',
      status: 'active',
    })),
  })
  json(root, 'tools/workbench/config/platform-table-owners.json', {
    schemaVersion: 1,
    migrationRange: 'V001',
    defaultKind: 'business',
    owners: { identity: ['users'], project: ['projects'] },
    specialTreatment: { technical: [], mapping: [], history: [], shared: [], ownerless: [], retired: [] },
    rules: {
      foreignWrite: 'forbidden',
      foreignRead: 'requires-exact-approved-exception',
      foreignKey: 'allowed',
      schema: 'shared',
      newTable: 'must-add-owner',
    },
  })
  json(root, 'tools/workbench/config/platform-boundary-exceptions.json', {
    schemaVersion: 1,
    approvalPolicy: { wildcards: 'forbidden' },
    exceptions: [],
  })
  return root
}

function seedBaseline(root: string): void {
  const baseline = {
    schemaVersion: 1,
    baselineId: 'PLATFORM-SCALE-S01-M3',
    sourceCommit: 'fixture',
    backend: {
      foreignPrivateImports: [
        'server/src/main/java/com/colla/platform/modules/identity/application/IdentityService.java|identity|project|com.colla.platform.modules.project.application.ProjectService|import',
      ],
      sharedToModuleImports: [],
      directedEdges: ['identity|project'],
      stronglyConnectedComponents: [],
    },
    frontend: {
      crossFeatureImports: [],
      sharedToFeatureImports: [],
      directedEdges: [],
      stronglyConnectedComponents: [],
    },
    database: { crossOwnerReads: [], dynamicSqlFiles: [] },
  }
  json(root, 'tools/workbench/config/platform-boundary-baseline.json', baseline)
}

test('accepts an exact historical baseline and emits a report', () => {
  const root = fixture()
  seedBaseline(root)
  const result = checkArchitectureBoundaries(root)
  assert.equal(result.metrics.backendPrivate, 1)
  assert.match(readFileSync(result.report, 'utf8'), /status: PASS/)
})

test('rejects additions and requires stale violations to ratchet down', () => {
  const added = fixture()
  seedBaseline(added)
  write(added, 'web/src/modules/identity/private.ts', `import('../project')`)
  assert.throws(() => checkArchitectureBoundaries(added), /Frontend import additions/)

  const removed = fixture()
  seedBaseline(removed)
  write(removed, 'server/src/main/java/com/colla/platform/modules/identity/application/IdentityService.java', `
    package com.colla.platform.modules.identity.application;
    public class IdentityService {}
  `)
  assert.throws(() => checkArchitectureBoundaries(removed), /baseline must ratchet after removals/)
})

test('rejects shared reverse imports and bracket require alias bypasses', () => {
  const shared = fixture()
  seedBaseline(shared)
  write(shared, 'web/src/shared/bridge.ts', `import { project } from '@/modules/project'; export { project }`)
  assert.throws(() => checkArchitectureBoundaries(shared), /Frontend shared reverse import additions|shared code/)

  const bracket = fixture()
  seedBaseline(bracket)
  write(bracket, 'web/src/modules/identity/private.ts', `globalThis['require']('@/modules/project')`)
  assert.throws(() => checkArchitectureBoundaries(bracket), /Frontend import additions/)
})

test('rejects every cross-owner write and Flyway owner drift', () => {
  const writeFixture = fixture()
  seedBaseline(writeFixture)
  write(writeFixture, 'server/src/main/java/com/colla/platform/modules/identity/application/IdentityWriter.java', `
    package com.colla.platform.modules.identity.application;
    public class IdentityWriter { String sql = "update projects set id = id"; }
  `)
  assert.throws(() => checkArchitectureBoundaries(writeFixture), /Foreign writes are forbidden/)

  const migrationFixture = fixture()
  seedBaseline(migrationFixture)
  write(migrationFixture, 'server/src/main/resources/db/migration/V002__rename.sql', 'alter table projects rename to project_records;')
  assert.throws(() => checkArchitectureBoundaries(migrationFixture), /Table owner manifest mismatch/)
})
