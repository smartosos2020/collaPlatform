alter table knowledge_content_templates
    add column version_no integer not null default 1,
    add column supersedes_template_id uuid references knowledge_content_templates(id);

create index idx_knowledge_content_templates_lineage
    on knowledge_content_templates(supersedes_template_id, version_no desc);
