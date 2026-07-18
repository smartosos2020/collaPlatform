import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { extname, join, relative, resolve } from 'node:path'
import { type DatabaseOptions, psql, scalar } from './database.js'

export function namingGuard(root: string): string[] {
  const findings: string[] = []
  const fixed = ['/api/docs', 'colla://document/', 'convert-to-document', 'modules.doc', 'modules/docs', 'LEGACY_DOCUMENT', ' from documents', ' join documents', 'document_blocks', 'documents.content', 'snapshot_content']
  const patterns = [/\b(?:class|record|interface)\s+Document[A-Z][A-Za-z0-9_]*/, /"document\.[a-zA-Z0-9_.-]+"/]
  const visit = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) visit(path)
      else if (['.java', '.ts', '.tsx'].includes(extname(path))) {
        const content = readFileSync(path, 'utf8')
        for (const value of fixed) if (content.includes(value)) findings.push(`${relative(root, path)} :: ${value}`)
        for (const pattern of patterns) if (pattern.test(content)) findings.push(`${relative(root, path)} :: ${pattern}`)
      }
    }
  }
  for (const path of ['server/src/main/java', 'web/src', 'web/e2e']) visit(join(root, path))
  return [...new Set(findings)]
}

export function inspectObjectReferences(options: DatabaseOptions): string[] {
  const queries = [
    ['Object reference summary', `select item_kind, target_object_type, count(*) as count from knowledge_base_items where deleted_at is null and item_kind in ('object_ref', 'external_link') group by item_kind, target_object_type order by item_kind, target_object_type;`],
    ['Invalid object reference shape', `select id, title, content_type, target_object_type, target_object_id, target_route from knowledge_base_items where deleted_at is null and ((item_kind = 'object_ref' and (target_object_type is null or target_object_id is null or target_route is null)) or (item_kind = 'external_link' and (target_route is null or target_object_type <> 'external_link'))) order by updated_at desc;`],
    ['Missing knowledge content targets', `select ref.id as reference_id, ref.title as reference_title, ref.target_object_id from knowledge_base_items ref left join knowledge_base_items target on target.id = ref.target_object_id and target.workspace_id = ref.workspace_id and target.deleted_at is null where ref.deleted_at is null and ref.item_kind = 'object_ref' and ref.target_object_type = 'knowledge_content' and target.id is null order by ref.updated_at desc;`],
    ['Duplicate aliases under the same parent', `select parent_id, lower(coalesce(entry_alias, title)) as alias_key, count(*) as count, string_agg(id::text, ', ' order by updated_at desc) as reference_ids from knowledge_base_items where deleted_at is null and item_kind in ('object_ref', 'external_link') group by parent_id, lower(coalesce(entry_alias, title)) having count(*) > 1 order by count desc, alias_key;`],
    ['Repeated target references', `select target_object_type, target_object_id, count(*) as count, string_agg(id::text, ', ' order by updated_at desc) as reference_ids from knowledge_base_items where deleted_at is null and item_kind = 'object_ref' group by target_object_type, target_object_id having count(*) > 1 order by count desc, target_object_type;`],
  ] as const
  return queries.map(([title, sql]) => `== ${title} ==\n${psql(sql, options)}`)
}

const consistencyChecks = [
  ['Space root or home target is missing', `select count(*) from knowledge_base_spaces s where s.deleted_at is null and (not exists (select 1 from knowledge_base_items i where i.workspace_id=s.workspace_id and i.id=s.root_item_id and i.deleted_at is null) or not exists (select 1 from knowledge_base_items i where i.workspace_id=s.workspace_id and i.id=s.home_item_id and i.deleted_at is null));`, 'critical', 'Restore the missing space node from backup before serving the space.'],
  ['Active item has a missing parent', `select count(*) from knowledge_base_items i where i.deleted_at is null and i.parent_id is not null and not exists (select 1 from knowledge_base_items p where p.workspace_id=i.workspace_id and p.id=i.parent_id and p.deleted_at is null);`, 'high', 'Repair the parent relationship or soft-delete the detached node after backup.'],
  ['Editable markdown item has no active block', `select count(*) from knowledge_base_items i where i.deleted_at is null and i.archived_at is null and i.content_type='markdown' and not exists (select 1 from knowledge_content_blocks b where b.workspace_id=i.workspace_id and b.item_id=i.id and b.deleted_at is null);`, 'high', 'Restore canonical blocks from the latest version snapshot.'],
  ['Active block has no active item', `select count(*) from knowledge_content_blocks b where b.deleted_at is null and not exists (select 1 from knowledge_base_items i where i.workspace_id=b.workspace_id and i.id=b.item_id and i.deleted_at is null);`, 'high', 'Soft-delete orphan blocks only after their owning item is confirmed absent.'],
  ['Block parent is invalid', `select count(*) from knowledge_content_blocks b where b.deleted_at is null and b.parent_id is not null and not exists (select 1 from knowledge_content_blocks p where p.workspace_id=b.workspace_id and p.item_id=b.item_id and p.id=b.parent_id and p.deleted_at is null);`, 'medium', 'Reattach the block to a valid parent and preserve sort order.'],
  ['Sibling block sort order is duplicated', `select count(*) from (select workspace_id,item_id,parent_id,sort_order from knowledge_content_blocks where deleted_at is null group by workspace_id,item_id,parent_id,sort_order having count(*) > 1) x;`, 'medium', 'Reorder affected siblings through the canonical blocks API.'],
  ['Version has no active item', `select count(*) from knowledge_content_versions v where not exists (select 1 from knowledge_base_items i where i.workspace_id=v.workspace_id and i.id=v.item_id and i.deleted_at is null);`, 'medium', 'Retain only versions recoverable through an active item, or restore the item first.'],
  ['Knowledge permission has no active resource', `select count(*) from resource_permissions rp where rp.status='active' and rp.resource_type='knowledge_content' and not exists (select 1 from knowledge_base_items i where i.workspace_id=rp.workspace_id and i.id=rp.resource_id and i.deleted_at is null);`, 'high', 'Deactivate orphan grants so access decisions cannot resolve stale resources.'],
  ['Knowledge search row has no active item', `select count(*) from search_index_entries s where s.object_type='knowledge_content' and not exists (select 1 from knowledge_base_items i where i.workspace_id=s.workspace_id and i.id=s.object_id and i.deleted_at is null);`, 'high', 'Remove stale index rows and rebuild the affected workspace search index.'],
  ['Active item is missing its knowledge search row', `select count(*) from knowledge_base_items i where i.deleted_at is null and i.archived_at is null and not exists (select 1 from search_index_entries s where s.workspace_id=i.workspace_id and s.object_type='knowledge_content' and s.object_id=i.id);`, 'medium', 'Rebuild the affected workspace search index.'],
  ['Object entry has an incomplete target', `select count(*) from knowledge_base_items i where i.deleted_at is null and i.item_kind in ('object_ref','external_link') and (i.target_object_type is null or (i.item_kind='object_ref' and i.target_object_id is null) or (i.item_kind='external_link' and coalesce(i.target_route,'')=''));`, 'high', 'Repair the target metadata, or remove the invalid entry after backup.'],
  ['Knowledge content reference uses a non-canonical route', `select count(*) from knowledge_base_items i where i.deleted_at is null and i.item_kind='object_ref' and i.target_object_type='knowledge_content' and coalesce(i.target_route,'') !~ '^/knowledge-bases/[^/]+/items/[^/]+$';`, 'medium', 'Recompute the route from the active target; remove the entry if the target is absent.'],
  ['Knowledge content reference target is missing', `select count(*) from knowledge_base_items r where r.deleted_at is null and r.item_kind='object_ref' and r.target_object_type='knowledge_content' and not exists (select 1 from knowledge_base_items t where t.workspace_id=r.workspace_id and t.id=r.target_object_id and t.deleted_at is null);`, 'high', 'Preview a knowledge reference repair, validate backup, then explicitly remove the orphan entry.'],
  ['Retired document compatibility remains active', `select (select count(*) from resource_permissions where resource_type='document') + (select count(*) from search_index_entries where object_type='document') + (select count(*) from information_schema.tables where table_schema='public' and table_name in ('documents','document_blocks','document_permissions','knowledge_item_permissions'));`, 'high', 'Complete the retirement path; no active document compatibility model may remain.'],
] as const

export function consistencyCheck(root: string, options: DatabaseOptions, outputDir: string): { failures: number; report: string; json: string } {
  const checks = consistencyChecks.map(([name, sql, severity, remediation]) => {
    const count = scalar(sql, options)
    return { name, status: count === 0 ? 'PASS' : 'FAIL', count, severity, remediation }
  })
  const directory = resolve(root, outputDir)
  mkdirSync(directory, { recursive: true })
  const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')
  const report = join(directory, `knowledge-consistency-${timestamp}.md`)
  const json = join(directory, `knowledge-consistency-${timestamp}.json`)
  const failures = checks.filter((check) => check.status === 'FAIL').length
  writeFileSync(json, JSON.stringify({ checkedAt: new Date().toISOString(), database: options.database, decision: failures ? 'FAIL' : 'PASS', exitCode: failures ? 2 : 0, checks }, null, 2))
  writeFileSync(report, ['# Knowledge Consistency Check', '', `- Decision: ${failures ? 'FAIL' : 'PASS'}`, `- JSON output: ${json}`, '', '| Check | Status | Count | Risk | Remediation |', '| --- | --- | ---: | --- | --- |', ...checks.map((check) => `| ${check.name} | ${check.status} | ${check.count} | ${check.severity} | ${check.remediation} |`), ''].join('\n'))
  return { failures, report, json }
}
