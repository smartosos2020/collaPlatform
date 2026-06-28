alter table document_versions
    add column version_name varchar(128),
    add column version_type varchar(32) not null default 'manual_checkpoint',
    add column summary varchar(512),
    add column source_version_no integer,
    add column block_snapshot jsonb,
    add constraint chk_document_version_type check (version_type in ('auto_snapshot', 'manual_checkpoint', 'named', 'restore', 'import'));

create index idx_document_versions_type
    on document_versions(document_id, version_type, created_at desc);

create table document_templates (
    id uuid primary key,
    workspace_id uuid references workspaces(id),
    title varchar(128) not null,
    description varchar(512),
    category varchar(64) not null,
    content text not null,
    blocks jsonb,
    built_in boolean not null default false,
    status varchar(32) not null default 'active',
    created_by uuid references users(id),
    created_at timestamptz not null default now(),
    updated_by uuid references users(id),
    updated_at timestamptz
);

create index idx_document_templates_workspace_category
    on document_templates(workspace_id, category, title)
    where status = 'active';

insert into document_templates (id, workspace_id, title, description, category, content, blocks, built_in, status, created_at)
values
    (
        '00000000-0000-0000-0000-000000004701',
        null,
        '会议纪要',
        '会议目标、议题、结论和行动项',
        'meeting',
        '# 会议纪要

## 目标

## 议题

## 结论

## 行动项
- [ ] 负责人 / 截止时间',
        null,
        true,
        'active',
        now()
    ),
    (
        '00000000-0000-0000-0000-000000004702',
        null,
        '需求文档',
        '背景、目标、范围、方案和验收标准',
        'prd',
        '# 需求文档

## 背景

## 目标

## 范围

## 方案

## 验收标准',
        null,
        true,
        'active',
        now()
    ),
    (
        '00000000-0000-0000-0000-000000004703',
        null,
        '项目计划',
        '里程碑、风险、人员和交付物',
        'project',
        '# 项目计划

## 里程碑

## 风险

## 人员

## 交付物',
        null,
        true,
        'active',
        now()
    ),
    (
        '00000000-0000-0000-0000-000000004704',
        null,
        '知识条目',
        '适合沉淀标准流程、FAQ 和长期知识',
        'knowledge',
        '# 知识条目

## 摘要

## 适用场景

## 详细说明

## 关联对象',
        null,
        true,
        'active',
        now()
    );
