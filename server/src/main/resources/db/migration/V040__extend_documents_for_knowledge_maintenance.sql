alter table documents
    add column maintainer_id uuid references users(id),
    add column tags text[] not null default '{}',
    add column category varchar(64),
    add column knowledge_status varchar(32) not null default 'draft',
    add column review_due_at date,
    add column verified_at timestamptz,
    add column review_notified_at timestamptz,
    add constraint chk_documents_knowledge_status
        check (knowledge_status in ('draft', 'verified', 'needs_review', 'outdated', 'archived'));

create index idx_documents_knowledge_metadata
    on documents(workspace_id, knowledge_status, review_due_at)
    where deleted_at is null;

create index idx_documents_tags
    on documents using gin(tags);

alter table document_templates
    add column scope_type varchar(32) not null default 'global',
    add column knowledge_base_id uuid references knowledge_base_spaces(id),
    add constraint chk_document_templates_scope_type check (scope_type in ('global', 'workspace', 'knowledge_base'));

create index idx_document_templates_scope
    on document_templates(workspace_id, scope_type, knowledge_base_id, category, title)
    where status = 'active';

insert into document_templates
    (id, workspace_id, title, description, category, content, blocks, built_in, status, scope_type, created_at)
values
    (
        '00000000-0000-0000-0000-000000004705',
        null,
        'FAQ',
        '问题、答案、适用范围和更新记录',
        'faq',
        '# FAQ

## 问题

## 简短答案

## 详细说明

## 适用范围

## 更新记录',
        null,
        true,
        'active',
        'global',
        now()
    ),
    (
        '00000000-0000-0000-0000-000000004706',
        null,
        'SOP',
        '标准操作流程、前置条件、步骤、异常处理',
        'sop',
        '# SOP

## 目标

## 前置条件

## 操作步骤

1.
2.
3.

## 异常处理

## 复核记录',
        null,
        true,
        'active',
        'global',
        now()
    ),
    (
        '00000000-0000-0000-0000-000000004707',
        null,
        '故障复盘',
        '事故影响、时间线、根因、改进项',
        'incident',
        '# 故障复盘

## 摘要

## 影响范围

## 时间线

## 根因分析

## 改进项

- [ ] 负责人 / 截止时间',
        null,
        true,
        'active',
        'global',
        now()
    ),
    (
        '00000000-0000-0000-0000-000000004708',
        null,
        '项目复盘',
        '目标回顾、结果、经验、后续行动',
        'project_review',
        '# 项目复盘

## 项目目标

## 结果回顾

## 做得好的地方

## 需要改进

## 后续行动',
        null,
        true,
        'active',
        'global',
        now()
    );
