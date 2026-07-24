import assert from 'node:assert/strict'
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import {
  architectureMetrics,
  assertArchitectureExpectations,
  renderArchitectureInventory,
  scanArchitecture,
  writeArchitectureInventory,
} from '../src/architecture/inventory.js'

function write(root: string, path: string, content: string): void {
  const target = join(root, ...path.split('/'))
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, content)
}

function fixture(): string {
  const root = mkdtempSync(join(tmpdir(), 'colla-architecture-'))
  write(root, 'server/src/main/java/com/colla/platform/modules/identity/infrastructure/IdentityRepository.java', `
    package com.colla.platform.modules.identity.infrastructure;
    public class IdentityRepository {}
  `)
  write(root, 'server/src/main/java/com/colla/platform/modules/project/application/ProjectService.java', `
    package com.colla.platform.modules.project.application;
    import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
    import com.colla.platform.modules.identity.contract.SubjectDirectory;
    import org.springframework.scheduling.annotation.Scheduled;
    import org.springframework.transaction.annotation.Transactional;
    import java.util.concurrent.ConcurrentHashMap;
    public class ProjectService {
      private final ConcurrentHashMap<String, String> state = new ConcurrentHashMap<>();
      @Scheduled(fixedDelay = 1000)
      @Transactional
      public void poll() {}
      public void sql(String suffix) {
        String query = "select * from users";
        String update = "update users set status = 'active'";
        String dynamic = "select * from users where name = '" + suffix;
        // select * from roles must not become a SQL access candidate
      }
    }
  `)
  write(root, 'server/src/main/java/com/colla/platform/shared/auth/AuthBridge.java', `
    package com.colla.platform.shared.auth;
    import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
    public class AuthBridge {}
  `)
  write(root, 'web/tsconfig.app.json', JSON.stringify({ compilerOptions: { baseUrl: '.', paths: { '@/*': ['src/*'] } } }))
  write(root, 'web/src/modules/alpha/index.ts', `
    import { beta } from '@/modules/beta'
    import('@/modules/beta')
    export * from '../beta'
    require('../beta')
    export const alpha = beta
  `)
  write(root, 'web/src/modules/beta/index.ts', `
    import { alpha } from '../alpha'
    export const beta = alpha
  `)
  write(root, 'server/src/main/resources/db/migration/V001__create.sql', `
    create table users (id uuid primary key);
    create table project_items (id uuid primary key);
    create table dropped_table (id uuid primary key);
  `)
  write(root, 'server/src/main/resources/db/migration/V002__rename_drop.sql', `
    alter table project_items rename to project_work_items;
    drop table dropped_table;
  `)
  write(root, 'deploy/docker-compose.prod.yml', `
services:
  server:
    image: server:test
  collaboration-a:
    image: collaboration:test
volumes:
  data:
  `)
  return root
}

test('builds deterministic backend, frontend, Flyway, SQL, and runtime inventory', () => {
  const root = fixture()
  const inventory = scanArchitecture(root)

  assert.equal(inventory.schemaVersion, 1)
  assert.equal(inventory.backend.modules.length, 2)
  assert.equal(inventory.backend.javaFiles, 2)
  assert.equal(inventory.backend.crossModuleImportCount, 2)
  assert.equal(inventory.backend.foreignInfrastructureImportCount, 1)
  assert.equal(inventory.backend.transactionalForeignInfrastructureFiles.length, 1)
  assert.equal(inventory.backend.sharedToModuleImports.length, 1)

  assert.equal(inventory.frontend.features.length, 2)
  assert.equal(inventory.frontend.crossFeatureImportCount, 5)
  assert.equal(inventory.frontend.directedEdgeCount, 2)
  assert.deepEqual(inventory.frontend.stronglyConnectedComponents, [['alpha', 'beta']])
  assert.deepEqual(new Set(inventory.frontend.crossFeatureImports.map((item) => item.kind)), new Set(['import', 'dynamic-import', 're-export', 'require']))

  assert.deepEqual(inventory.database.activeTables.map((table) => table.table), ['project_work_items', 'users'])
  assert.equal(inventory.database.crossOwnerCandidateCount, 1)
  assert.deepEqual(inventory.database.crossOwnerCandidates[0].modes, ['read', 'write'])
  assert.equal(inventory.database.dynamicSqlCandidates.length, 1)
  assert.ok(inventory.database.sqlAccesses.every((access) => !access.sourceFile.includes('\\')))

  assert.equal(inventory.runtime.scheduledTasks.length, 1)
  assert.equal(inventory.runtime.inMemoryState.length, 1)
  assert.deepEqual(inventory.runtime.productionServices.map((service) => service.service), ['server', 'collaboration-a'])

  const first = renderArchitectureInventory(inventory)
  const second = renderArchitectureInventory(scanArchitecture(root))
  assert.equal(first, second)
  assert.match(first, /schemaVersion: 1/)
})

test('writes stable JSON and rejects baseline drift', () => {
  const root = fixture()
  const inventory = scanArchitecture(root)
  const output = writeArchitectureInventory(root, inventory, '.local-reports', 'fixture')
  assert.deepEqual(JSON.parse(readFileSync(output.jsonPath, 'utf8')), inventory)

  const metrics = architectureMetrics(inventory)
  write(root, 'tools/workbench/config/expected.json', JSON.stringify({
    schemaVersion: 1,
    baselineId: 'fixture',
    sourceCommit: 'fixture',
    compareRef: '',
    expected: { backend: { modules: (metrics.backend as Record<string, unknown>).modules } },
  }))
  assert.equal(assertArchitectureExpectations(root, 'tools/workbench/config/expected.json', inventory).baselineId, 'fixture')

  write(root, 'tools/workbench/config/drift.json', JSON.stringify({
    schemaVersion: 1,
    baselineId: 'drift',
    sourceCommit: 'fixture',
    compareRef: '',
    expected: { backend: { modules: 99 } },
  }))
  assert.throws(() => assertArchitectureExpectations(root, 'tools/workbench/config/drift.json', inventory), /Architecture baseline mismatch/)
})

test('keeps the platform scale risk register complete and machine-readable', () => {
  const testDirectory = dirname(fileURLToPath(import.meta.url))
  const riskRegisterPath = join(testDirectory, '..', 'config', 'platform-scale-s01-m1-risk-register.json')
  const register = JSON.parse(readFileSync(riskRegisterPath, 'utf8')) as {
    schemaVersion: number
    risks: Array<{
      id: string
      priority: string
      owner: string
      fixStage: string
      blocking: string
      summary: string
      evidence: string[]
    }>
  }

  assert.equal(register.schemaVersion, 1)
  assert.equal(register.risks.length, 9)
  assert.equal(new Set(register.risks.map((risk) => risk.id)).size, register.risks.length)
  for (const risk of register.risks) {
    assert.match(risk.id, /^PS-P[0-2]-\d{3}$/)
    assert.match(risk.priority, /^P[0-2]$/)
    assert.ok(risk.owner.length > 0)
    assert.match(risk.fixStage, /^PLATFORM-SCALE-S\d{2}(?:-M\d+(?:\/M\d+)*)?$/)
    assert.ok(risk.blocking.length > 0)
    assert.ok(risk.summary.length > 0)
    assert.ok(risk.evidence.length > 0)
  }
})
