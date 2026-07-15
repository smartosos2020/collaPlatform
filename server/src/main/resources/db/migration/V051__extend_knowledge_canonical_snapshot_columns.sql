alter table knowledge_content_versions
    add column if not exists canonical_snapshot jsonb;

alter table knowledge_content_templates
    add column if not exists canonical_snapshot jsonb;

alter table knowledge_content_collaboration_states
    add column if not exists canonical_snapshot jsonb;
