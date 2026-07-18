import { mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { createHash } from 'node:crypto'
import { runSync } from '../lib/process.js'
import { psql, scalar, type DatabaseOptions } from './database.js'

export interface RepairOptions extends DatabaseOptions { referenceId: string; action?: 'preview' | 'repair'; backupPath?: string; createBackup?: boolean; confirm?: boolean; outputDir?: string }
function hash(path: string): string { return createHash('sha256').update(readFileSync(path)).digest('hex').toUpperCase() }
function snapshot(id: string, database: DatabaseOptions): any {
  const text = psql(`select row_to_json(x)::text from (select id,workspace_id,parent_id,title,item_kind,content_type,target_object_type,target_object_id,target_route,created_by,created_at,updated_at,deleted_at from knowledge_base_items where id='${id}'::uuid) x;`, database, true)
  if (!text) throw new Error(`Knowledge reference '${id}' does not exist`)
  const reference = JSON.parse(text); const targetExists = reference.target_object_id ? scalar(`select count(*) from knowledge_base_items where workspace_id='${reference.workspace_id}'::uuid and id='${reference.target_object_id}'::uuid and deleted_at is null;`, database) > 0 : false
  const dependencies = JSON.parse(psql(`select json_object_agg(source,count) from (select 'blocks' source,count(*)::int count from knowledge_content_blocks where item_id='${id}'::uuid and deleted_at is null union all select 'versions',count(*)::int from knowledge_content_versions where item_id='${id}'::uuid union all select 'permissions',count(*)::int from resource_permissions where resource_type='knowledge_content' and resource_id='${id}'::uuid and status='active' union all select 'share_links',count(*)::int from knowledge_item_share_links where item_id='${id}'::uuid and enabled union all select 'subscriptions',count(*)::int from knowledge_subscriptions where target_type='knowledge_content' and target_id='${id}'::uuid and deleted_at is null union all select 'search',count(*)::int from search_index_entries where object_type='knowledge_content' and object_id='${id}'::uuid) d;`, database, true))
  return { reference, targetExists, dependencies, repairEligible: !reference.deleted_at && reference.item_kind === 'object_ref' && reference.target_object_type === 'knowledge_content' && reference.target_object_id && !targetExists }
}
function createBackup(root: string, options: RepairOptions, stamp: string): any {
  const directory = join(root, '.local-backups'); mkdirSync(directory, { recursive: true }); const path = resolve(root, options.backupPath || join('.local-backups', `knowledge-reference-repair-${stamp}.dump`)); const containerPath = `/tmp/knowledge-reference-repair-${stamp}.dump`
  runSync('docker', ['exec', options.container, 'pg_dump', '-U', options.user, '--format=custom', `--file=${containerPath}`, options.database]); runSync('docker', ['cp', `${options.container}:${containerPath}`, path]); runSync('docker', ['exec', options.container, 'rm', '-f', containerPath])
  if (!statSync(path).size) throw new Error('Database backup is empty')
  const manifest = { backupPath: path, sha256: hash(path), createdAt: new Date().toISOString(), database: options.database, referenceId: options.referenceId }; writeFileSync(`${path}.manifest.json`, JSON.stringify(manifest, null, 2)); return { path, manifestPath: `${path}.manifest.json`, sha256: manifest.sha256, validated: true }
}
function existingBackup(root: string, options: RepairOptions): any {
  if (!options.backupPath) throw new Error('Repair requires --create-backup or --backup-path')
  const path = resolve(root, options.backupPath); const manifestPath = `${path}.manifest.json`; const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
  if (!statSync(path).size || manifest.sha256 !== hash(path) || manifest.referenceId !== options.referenceId) throw new Error('Backup checksum or reference id does not match')
  return { path, manifestPath, sha256: manifest.sha256, validated: true }
}
export function repairKnowledgeReference(root: string, options: RepairOptions): { report: string; json: string } {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(options.referenceId)) throw new Error('reference-id must be a UUID')
  const value = snapshot(options.referenceId, options); let backup: any = null; let applied = false
  if (options.action === 'repair') {
    if (!value.repairEligible || !options.confirm) throw new Error('Repair requires eligible orphan reference, reviewed preview, and --confirm')
    backup = options.createBackup ? createBackup(root, options, Date.now().toString()) : existingBackup(root, options)
    psql(`begin; update knowledge_base_items set deleted_at=now(),updated_at=now() where id='${options.referenceId}'::uuid and deleted_at is null; update knowledge_content_blocks set deleted_at=now(),updated_at=now() where item_id='${options.referenceId}'::uuid and deleted_at is null; delete from knowledge_content_versions where item_id='${options.referenceId}'::uuid; update resource_permissions set status='revoked',updated_at=now() where resource_type='knowledge_content' and resource_id='${options.referenceId}'::uuid and status='active'; update knowledge_item_share_links set enabled=false,disabled_at=now(),updated_at=now() where item_id='${options.referenceId}'::uuid and enabled; update knowledge_subscriptions set deleted_at=now() where target_type='knowledge_content' and target_id='${options.referenceId}'::uuid and deleted_at is null; update knowledge_item_relations set deleted_at=now() where item_id='${options.referenceId}'::uuid and deleted_at is null; delete from search_index_entries where object_type='knowledge_content' and object_id='${options.referenceId}'::uuid; delete from object_recent_accesses where object_type='knowledge_content' and object_id='${options.referenceId}'::uuid; delete from object_favorites where object_type='knowledge_content' and object_id='${options.referenceId}'::uuid; delete from object_links where object_type='knowledge_content' and object_id='${options.referenceId}'::uuid; delete from notifications where target_type='knowledge_content' and target_id='${options.referenceId}'::uuid; commit;`, options); applied = true
  }
  const directory = resolve(root, options.outputDir ?? '.local-reports'); mkdirSync(directory, { recursive: true }); const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, ''); const json = join(directory, `knowledge-reference-repair-${stamp}.json`); const report = join(directory, `knowledge-reference-repair-${stamp}.md`)
  writeFileSync(json, JSON.stringify({ action: options.action ?? 'preview', referenceId: options.referenceId, checkedAt: new Date().toISOString(), snapshot: value, backup, applied, outcome: applied ? 'repaired' : 'preview' }, null, 2)); writeFileSync(report, ['# Knowledge Reference Repair', '', `- Action: ${options.action ?? 'preview'}`, `- Reference: ${options.referenceId}`, `- Outcome: ${applied ? 'repaired' : 'preview'}`, `- Repair eligible: ${value.repairEligible}`, `- Target exists: ${value.targetExists}`, ...(backup ? [`- Validated backup: ${backup.path}`] : []), '', '## Dependency Impact', '```json', JSON.stringify(value.dependencies), '```', ''].join('\n')); return { report, json }
}
