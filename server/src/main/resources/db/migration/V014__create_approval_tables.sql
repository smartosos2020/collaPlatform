insert into object_type_rules (id, object_type, web_path_pattern, deep_link_pattern, created_at)
values ('00000000-0000-0000-0000-000000000214', 'approval', '/approvals/{id}', 'colla://approval/{id}', now())
on conflict (object_type) do nothing;

create table approval_forms (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    form_key varchar(64) not null,
    name varchar(128) not null,
    description text,
    category varchar(64) not null,
    schema_json jsonb not null,
    enabled boolean not null default true,
    created_by uuid,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    archived_at timestamptz,
    constraint uk_approval_forms_key unique (workspace_id, form_key)
);

create index idx_approval_forms_workspace_enabled
    on approval_forms (workspace_id, enabled, category);

create table approval_flow_nodes (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    form_id uuid not null references approval_forms(id),
    node_order int not null,
    name varchar(128) not null,
    approver_type varchar(32) not null,
    approver_value varchar(128),
    created_at timestamptz not null,
    constraint uk_approval_flow_nodes_order unique (form_id, node_order)
);

create index idx_approval_flow_nodes_form
    on approval_flow_nodes (workspace_id, form_id, node_order);

create table approval_instances (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    form_id uuid not null references approval_forms(id),
    form_key varchar(64) not null,
    title varchar(255) not null,
    applicant_id uuid not null references users(id),
    status varchar(32) not null,
    current_node_order int not null default 0,
    payload_json jsonb not null,
    submitted_at timestamptz not null,
    completed_at timestamptz,
    created_at timestamptz not null,
    updated_by uuid,
    updated_at timestamptz not null,
    withdrawn_at timestamptz
);

create index idx_approval_instances_applicant_status
    on approval_instances (workspace_id, applicant_id, status, updated_at desc);

create index idx_approval_instances_status
    on approval_instances (workspace_id, status, updated_at desc);

create table approval_tasks (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    instance_id uuid not null references approval_instances(id),
    node_order int not null,
    assignee_id uuid not null references users(id),
    status varchar(32) not null,
    comment text,
    acted_at timestamptz,
    transferred_to uuid references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_approval_tasks_assignee_status
    on approval_tasks (workspace_id, assignee_id, status, created_at desc);

create index idx_approval_tasks_instance
    on approval_tasks (workspace_id, instance_id, node_order, status);

create table approval_action_logs (
    id uuid primary key,
    workspace_id uuid not null references workspaces(id),
    instance_id uuid not null references approval_instances(id),
    actor_id uuid references users(id),
    action varchar(64) not null,
    from_status varchar(32),
    to_status varchar(32),
    comment text,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_approval_action_logs_instance
    on approval_action_logs (workspace_id, instance_id, created_at desc);

insert into approval_forms
    (id, workspace_id, form_key, name, description, category, schema_json, enabled, created_by, created_at, updated_by, updated_at)
values
    (
        '00000000-0000-0000-0000-000000001001',
        '00000000-0000-0000-0000-000000000001',
        'leave',
        '请假申请',
        '团队成员请假、调休、外出登记',
        'hr',
        '{"fields":[{"key":"leaveType","label":"请假类型","type":"select","options":["年假","病假","事假","调休"],"required":true},{"key":"startAt","label":"开始时间","type":"datetime","required":true},{"key":"endAt","label":"结束时间","type":"datetime","required":true},{"key":"reason","label":"原因","type":"textarea","required":true}]}'::jsonb,
        true,
        null,
        now(),
        null,
        now()
    ),
    (
        '00000000-0000-0000-0000-000000001002',
        '00000000-0000-0000-0000-000000000001',
        'expense',
        '报销申请',
        '差旅、采购、日常支出的报销流转',
        'finance',
        '{"fields":[{"key":"amount","label":"金额","type":"number","required":true},{"key":"expenseType","label":"费用类型","type":"select","options":["差旅","办公","招待","软件服务"],"required":true},{"key":"description","label":"说明","type":"textarea","required":true},{"key":"attachmentFileId","label":"票据附件","type":"file","required":false}]}'::jsonb,
        true,
        null,
        now(),
        null,
        now()
    ),
    (
        '00000000-0000-0000-0000-000000001003',
        '00000000-0000-0000-0000-000000000001',
        'purchase',
        '采购申请',
        '设备、软件、服务采购申请',
        'finance',
        '{"fields":[{"key":"itemName","label":"采购项","type":"text","required":true},{"key":"amount","label":"预算金额","type":"number","required":true},{"key":"vendor","label":"供应商","type":"text","required":false},{"key":"reason","label":"采购原因","type":"textarea","required":true}]}'::jsonb,
        true,
        null,
        now(),
        null,
        now()
    ),
    (
        '00000000-0000-0000-0000-000000001004',
        '00000000-0000-0000-0000-000000000001',
        'permission_request',
        '权限申请',
        '申请文档、表格、项目或系统权限',
        'it',
        '{"fields":[{"key":"targetType","label":"对象类型","type":"select","options":["项目","文档","表格","系统"],"required":true},{"key":"targetName","label":"对象名称","type":"text","required":true},{"key":"permissionLevel","label":"权限级别","type":"select","options":["查看","编辑","管理"],"required":true},{"key":"reason","label":"申请原因","type":"textarea","required":true}]}'::jsonb,
        true,
        null,
        now(),
        null,
        now()
    )
on conflict (workspace_id, form_key) do nothing;

insert into approval_flow_nodes
    (id, workspace_id, form_id, node_order, name, approver_type, approver_value, created_at)
values
    ('00000000-0000-0000-0000-000000001101', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000001001', 1, '管理员审批', 'role', 'admin', now()),
    ('00000000-0000-0000-0000-000000001102', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000001002', 1, '管理员审批', 'role', 'admin', now()),
    ('00000000-0000-0000-0000-000000001103', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000001003', 1, '管理员审批', 'role', 'admin', now()),
    ('00000000-0000-0000-0000-000000001104', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000001004', 1, '管理员审批', 'role', 'admin', now())
on conflict (form_id, node_order) do nothing;
