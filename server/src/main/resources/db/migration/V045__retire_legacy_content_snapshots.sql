-- Blocks and block snapshots are the only persisted knowledge-content sources after this migration.

update knowledge_content_versions v
set block_snapshot = jsonb_build_array(
    jsonb_build_object(
        'id', null,
        'parentId', null,
        'blockType', 'legacy_html',
        'content', coalesce(v.content, ''),
        'sortOrder', 0,
        'schemaVersion', 2,
        'attrs', '{}'::jsonb,
        'richContent', jsonb_build_object('text', coalesce(v.content, '')),
        'plainText', coalesce(v.content, ''),
        'anchorId', null,
        'deleted', false
    )
)
where v.block_snapshot is null;

update knowledge_content_templates t
set blocks = jsonb_build_array(
    jsonb_build_object(
        'id', null,
        'parentId', null,
        'blockType', 'legacy_html',
        'content', coalesce(t.content, ''),
        'sortOrder', 0,
        'schemaVersion', 2,
        'attrs', '{}'::jsonb,
        'richContent', jsonb_build_object('text', coalesce(t.content, '')),
        'plainText', coalesce(t.content, ''),
        'anchorId', null,
        'deleted', false
    )
)
where t.blocks is null;

update knowledge_content_collaboration_states s
set snapshot_payload = jsonb_set(
    coalesce(s.snapshot_payload, '{}'::jsonb),
    '{blocks}',
    coalesce((
        select jsonb_agg(
            jsonb_build_object(
                'id', b.id,
                'parentId', b.parent_id,
                'blockType', b.block_type,
                'content', b.content,
                'sortOrder', b.sort_order,
                'schemaVersion', b.schema_version,
                'attrs', b.attrs,
                'richContent', b.rich_content,
                'plainText', b.plain_text,
                'anchorId', b.anchor_id,
                'deleted', false
            ) order by b.sort_order, b.created_at, b.id
        )
        from knowledge_content_blocks b
        where b.workspace_id = s.workspace_id
          and b.item_id = s.item_id
          and b.deleted_at is null
    ), '[]'::jsonb),
    true
)
where not (coalesce(s.snapshot_payload, '{}'::jsonb) ? 'blocks');

do $$
begin
    if exists (
        select 1
        from knowledge_base_items i
        where i.item_kind = 'content'
          and i.deleted_at is null
          and not exists (
              select 1
              from knowledge_content_blocks b
              where b.workspace_id = i.workspace_id
                and b.item_id = i.id
                and b.deleted_at is null
          )
    ) then
        raise exception 'Cannot retire legacy content: active content items without blocks exist';
    end if;

    if exists (select 1 from knowledge_content_versions where block_snapshot is null) then
        raise exception 'Cannot retire legacy version content: null block snapshots exist';
    end if;

    if exists (select 1 from knowledge_content_templates where blocks is null) then
        raise exception 'Cannot retire legacy template content: null block snapshots exist';
    end if;
end $$;

alter table knowledge_content_versions alter column block_snapshot set not null;
alter table knowledge_content_templates alter column blocks set not null;

alter table knowledge_base_items drop column content;
alter table knowledge_content_versions drop column content;
alter table knowledge_content_collaboration_states drop column snapshot_content;
alter table knowledge_content_templates drop column content;

