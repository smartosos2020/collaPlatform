import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import test from 'node:test'
import { checkArchitectureContracts } from '../src/architecture/contracts.js'

function write(root: string, path: string, content: string): void {
  const target = join(root, ...path.split('/'))
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, content)
}

function writeJson(root: string, path: string, value: unknown): void {
  write(root, path, `${JSON.stringify(value, null, 2)}\n`)
}

function fixture(): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-contracts-'))
  write(root, 'server/src/main/java/com/colla/platform/modules/alpha/application/AlphaService.java', `
    package com.colla.platform.modules.alpha.application;
    public class AlphaService {}
  `)
  write(root, 'server/src/main/java/com/colla/platform/modules/alpha/contract/AlphaFacade.java', `
    package com.colla.platform.modules.alpha.contract;
    import java.util.UUID;
    public interface AlphaFacade { UUID id(); }
  `)
  write(root, 'server/src/main/resources/db/migration/V001__alpha.sql', 'create table alpha_items (id uuid primary key);')
  write(root, 'docs/01-architecture/platform-module-contracts.md', '模块 公开合同 table owner 组合查询 流程协调器 同步 异步 非目标 最小披露 事务')
  writeJson(root, 'tools/workbench/config/platform-modules.json', {
    schemaVersion: 1,
    contractVersion: 1,
    rootPackage: 'com.colla.platform.modules',
    contractPolicy: {
      publicPackage: 'contract',
      allowedKinds: ['facade', 'spi', 'record', 'value', 'event'],
      forbiddenProviderLayers: ['api', 'application', 'domain', 'infrastructure'],
      compatibility: 'additive',
    },
    modules: [{
      name: 'alpha',
      owner: 'alpha-owner',
      rootPackage: 'com.colla.platform.modules.alpha',
      publicContract: 'com.colla.platform.modules.alpha.contract',
      privatePackages: ['api', 'application', 'domain', 'infrastructure'],
      compositionRole: 'none',
      compositionPermission: 'not-applicable',
      status: 'active',
    }],
  })
  writeJson(root, 'tools/workbench/config/platform-table-owners.json', {
    schemaVersion: 1,
    migrationRange: 'V001',
    defaultKind: 'business',
    owners: { alpha: ['alpha_items'] },
    specialTreatment: { technical: [], mapping: [], history: [], shared: [], ownerless: [], retired: [] },
    rules: {
      foreignWrite: 'forbidden',
      foreignRead: 'requires-exact-approved-exception',
      foreignKey: 'allowed',
      schema: 'shared',
      newTable: 'must-add-owner',
    },
  })
  writeJson(root, 'tools/workbench/config/platform-boundary-exceptions.json', {
    schemaVersion: 1,
    approvalPolicy: { wildcards: 'forbidden' },
    exceptions: [],
  })
  return root
}

test('accepts complete module, table owner, exception and contract documents', () => {
  assert.deepEqual(checkArchitectureContracts(fixture()), {
    modules: 1,
    activeTables: 1,
    exceptions: 0,
    contractFiles: 1,
  })
})

test('rejects an unknown or missing module manifest entry', () => {
  const root = fixture()
  const modulesPath = join(root, 'tools/workbench/config/platform-modules.json')
  const manifest = JSON.parse(readFileSync(modulesPath, 'utf8'))
  manifest.modules[0].name = 'unknown'
  writeFileSync(modulesPath, JSON.stringify(manifest))
  assert.throws(() => checkArchitectureContracts(root), /Module manifest mismatch/)
})

test('rejects wildcard and foreign-write boundary exceptions', () => {
  const root = fixture()
  writeJson(root, 'tools/workbench/config/platform-boundary-exceptions.json', {
    schemaVersion: 1,
    approvalPolicy: { wildcards: 'forbidden' },
    exceptions: [{
      id: 'BOUNDARY-SQL-001',
      kind: 'sql',
      sourceModule: 'alpha',
      targetModule: 'alpha',
      sourceFile: 'server/**/AlphaService.java',
      target: 'alpha_items',
      modes: ['write'],
      reason: 'invalid fixture',
      owner: 'alpha-owner',
      introducedStage: 'S01',
      exitStage: 'S02',
      expiryDecision: 'remove',
      status: 'approved',
    }],
  })
  assert.throws(() => checkArchitectureContracts(root), /exact file and target|foreign writes/)
})

test('rejects provider-private imports from a public contract', () => {
  const root = fixture()
  write(root, 'server/src/main/java/com/colla/platform/modules/alpha/contract/AlphaFacade.java', `
    package com.colla.platform.modules.alpha.contract;
    import com.colla.platform.modules.alpha.infrastructure.AlphaRepository;
    public interface AlphaFacade { AlphaRepository repository(); }
  `)
  assert.throws(() => checkArchitectureContracts(root), /provider private package/)
})

test('rejects missing and duplicate table ownership', () => {
  const root = fixture()
  const ownerPath = join(root, 'tools/workbench/config/platform-table-owners.json')
  const manifest = JSON.parse(readFileSync(ownerPath, 'utf8'))
  manifest.owners.alpha = []
  writeFileSync(ownerPath, JSON.stringify(manifest))
  assert.throws(() => checkArchitectureContracts(root), /Table owner manifest mismatch/)
})

test('rejects an incomplete module boundary decision document', () => {
  const root = fixture()
  write(root, 'docs/01-architecture/platform-module-contracts.md', '模块 公开合同')
  assert.throws(() => checkArchitectureContracts(root), /missing required term/)
})
