param(
    [ValidateSet("seed", "verify", "all")]
    [string] $Stage = "all",
    [string] $Container = "colla-postgres",
    [string] $Database = "colla_platform",
    [string] $DbUser = "colla",
    [string] $ReportDir = ".local-reports",
    [string] $AdminCredentialHash = '$2a$10$hUraCmC6pR2dDqkvakbUyOVoG77YYvVnFLreO0Eo4O80MqoCRj5gi',
    [string] $MemberCredentialHash = '$2a$10$ZdaFPKva5wb0s1OU4BQd1.Xl.WWbp5NyKCMeEGp3UN1rxQ0SFKeom',
    [switch] $NoBackup
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([System.IO.Path]::IsPathRooted($ReportDir)) {
    $ReportPath = $ReportDir
} else {
    $ReportPath = Join-Path $Root $ReportDir
}
New-Item -ItemType Directory -Force -Path $ReportPath | Out-Null

trap {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $failureReportPath = Join-Path $ReportPath "m31-collab-simulation-failed-$timestamp.md"
    $failureReport = @(
        "# M31 Collaboration Simulation Failure",
        "",
        "- Stage: $Stage",
        "- GeneratedAt: $(Get-Date -Format o)",
        "- Error: $($_.Exception.Message)"
    )
    $failureReport | Set-Content -LiteralPath $failureReportPath -Encoding UTF8
    Write-Host "M31 failure report: $failureReportPath"
    break
}

$containers = & docker ps --format "{{.Names}}"
if ($LASTEXITCODE -ne 0) {
    throw "Docker is not available. Start Docker Desktop and retry."
}
if ($containers -notcontains $Container) {
    throw "Postgres container '$Container' is not running. Run 'docker compose up -d postgres' first."
}

function Backup-CollaDatabase {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $backupPath = Join-Path $ReportPath "pre-m31-reset-$timestamp.sql"
    Write-Host "Creating backup: $backupPath"
    $dumpArgs = @("exec", $Container, "pg_dump", "-U", $DbUser, "-d", $Database, "--no-owner", "--no-privileges")
    $dump = & docker @dumpArgs
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed with code $LASTEXITCODE"
    }
    $dump | Set-Content -LiteralPath $backupPath -Encoding UTF8
}

function Invoke-CollaPsql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql,
        [switch] $Capture
    )

    $tempSqlPath = Join-Path $ReportPath ("m31-psql-" + [Guid]::NewGuid().ToString("N") + ".sql")
    $containerSqlPath = "/tmp/" + [IO.Path]::GetFileName($tempSqlPath)
    try {
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tempSqlPath, $Sql, $utf8NoBom)

        & docker cp $tempSqlPath "$Container`:$containerSqlPath"
        if ($LASTEXITCODE -ne 0) {
            throw "docker cp failed with code $LASTEXITCODE"
        }

        $psqlArgs = @(
            "exec",
            $Container,
            "psql",
            "-v",
            "ON_ERROR_STOP=1",
            "-v",
            "admin_hash=$AdminCredentialHash",
            "-v",
            "member_hash=$MemberCredentialHash",
            "-U",
            $DbUser,
            "-d",
            $Database,
            "-f",
            $containerSqlPath
        )
        if ($Capture) {
            return & docker @psqlArgs
        }

        & docker @psqlArgs
        if ($LASTEXITCODE -ne 0) {
            $failedSqlPath = Join-Path $ReportPath "m31-psql-failed.sql"
            Copy-Item -LiteralPath $tempSqlPath -Destination $failedSqlPath -Force
            Write-Host "Failed SQL retained at: $failedSqlPath"
            throw "psql command failed with code $LASTEXITCODE"
        }
    } finally {
        & docker exec $Container rm -f $containerSqlPath 2>$null | Out-Null
        if (Test-Path -LiteralPath $tempSqlPath) {
            Remove-Item -LiteralPath $tempSqlPath -Force
        }
    }
}

$resetSql = @'
begin;

create temp table m31_reset_context as
select
    w.id as workspace_id,
    u.id as admin_id
from workspaces w
join users u on u.workspace_id = w.id and u.username = 'admin'
where w.slug = 'default'
limit 1;

do $$
begin
    if not exists (select 1 from m31_reset_context) then
        raise exception 'Default workspace and admin user are required before M31 reset.';
    end if;
end $$;

truncate table
    approval_action_logs,
    approval_tasks,
    approval_instances,
    audit_logs,
    base_record_activity_logs,
    base_record_relations,
    base_record_comments,
    base_record_values,
    base_records,
    base_fields,
    base_views,
    base_tables,
    base_members,
    bases,
    knowledge_content_comments,
    knowledge_content_blocks,
    knowledge_item_relations,
    knowledge_content_versions,
    knowledge_item_permissions,
    knowledge_base_items,
    domain_events,
    file_usages,
    files,
    issue_relations,
    issue_verification_logs,
    issue_attachments,
    issue_comments,
    issue_activity_logs,
    issues,
    iterations,
    project_members,
    projects,
    message_reactions,
    message_mentions,
    message_links,
    messages,
    conversation_members,
    conversations,
    notifications,
    object_favorites,
    object_recent_accesses,
    object_links,
    push_tokens,
    resource_permissions,
    search_index_entries,
    sessions,
    user_devices,
    user_roles
restart identity;

delete from users
where workspace_id in (select workspace_id from m31_reset_context)
  and username <> 'admin';

update users u
set
    password_hash = :'admin_hash',
    display_name = 'Administrator',
    email = 'admin@colla.local',
    phone = null,
    department = 'Platform',
    status = 'active',
    avatar_file_id = null,
    last_login_at = null,
    updated_by = c.admin_id,
    updated_at = now(),
    deleted_at = null
from m31_reset_context c
where u.id = c.admin_id;

insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at)
select '00000000-0000-0000-0000-000000031090'::uuid, c.workspace_id, c.admin_id, r.id, c.admin_id, now()
from m31_reset_context c
join roles r on r.workspace_id = c.workspace_id and r.code = 'admin';

commit;
'@

$seedSql = @'
begin;

create temp table m31_context as
select
    w.id as workspace_id,
    u.id as admin_id,
    timestamptz '2026-06-17 09:00:00+08' as base_at
from workspaces w
join users u on u.workspace_id = w.id and u.username = 'admin'
where w.slug = 'default'
limit 1;

do $$
begin
    if not exists (select 1 from m31_context) then
        raise exception 'Default workspace and admin user are required before M31 seed.';
    end if;
end $$;

insert into users (
    id, workspace_id, username, password_hash, display_name, avatar_file_id,
    email, phone, department, status, last_login_at, created_by, created_at,
    updated_by, updated_at, deleted_at
)
select
    v.id::uuid,
    c.workspace_id,
    v.username,
    :'member_hash',
    v.display_name,
    null,
    v.email,
    null,
    v.department,
    'active',
    null,
    c.admin_id,
    c.base_at,
    c.admin_id,
    c.base_at,
    null
from m31_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000031001', 'pm_chen', 'PM Chen', 'pm_chen@colla.local', 'Project'),
        ('00000000-0000-0000-0000-000000031002', 'product_lin', 'Product Lin', 'product_lin@colla.local', 'Product'),
        ('00000000-0000-0000-0000-000000031003', 'design_wu', 'Design Wu', 'design_wu@colla.local', 'Design'),
        ('00000000-0000-0000-0000-000000031004', 'frontend_zhao', 'Frontend Zhao', 'frontend_zhao@colla.local', 'Engineering'),
        ('00000000-0000-0000-0000-000000031005', 'backend_wang', 'Backend Wang', 'backend_wang@colla.local', 'Engineering'),
        ('00000000-0000-0000-0000-000000031006', 'qa_sun', 'QA Sun', 'qa_sun@colla.local', 'Quality'),
        ('00000000-0000-0000-0000-000000031007', 'ops_liu', 'Ops Liu', 'ops_liu@colla.local', 'Operations'),
        ('00000000-0000-0000-0000-000000031008', 'business_he', 'Business He', 'business_he@colla.local', 'Business'),
        ('00000000-0000-0000-0000-000000031009', 'viewer_tan', 'Viewer Tan', 'viewer_tan@colla.local', 'Observer')
) as v(id, username, display_name, email, department)
on conflict (workspace_id, username)
do update set
    password_hash = excluded.password_hash,
    display_name = excluded.display_name,
    email = excluded.email,
    department = excluded.department,
    status = 'active',
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    deleted_at = null;

create temp table m31_users as
select username, id
from users
where workspace_id = (select workspace_id from m31_context)
  and username in (
      'admin', 'pm_chen', 'product_lin', 'design_wu', 'frontend_zhao',
      'backend_wang', 'qa_sun', 'ops_liu', 'business_he', 'viewer_tan'
  );

insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at)
select
    v.id::uuid,
    c.workspace_id,
    u.id,
    r.id,
    c.admin_id,
    c.base_at
from m31_context c
join roles r on r.workspace_id = c.workspace_id and r.code = 'member'
join (
    values
        ('00000000-0000-0000-0000-000000031091', 'pm_chen'),
        ('00000000-0000-0000-0000-000000031092', 'product_lin'),
        ('00000000-0000-0000-0000-000000031093', 'design_wu'),
        ('00000000-0000-0000-0000-000000031094', 'frontend_zhao'),
        ('00000000-0000-0000-0000-000000031095', 'backend_wang'),
        ('00000000-0000-0000-0000-000000031096', 'qa_sun'),
        ('00000000-0000-0000-0000-000000031097', 'ops_liu'),
        ('00000000-0000-0000-0000-000000031098', 'business_he'),
        ('00000000-0000-0000-0000-000000031099', 'viewer_tan')
) as v(id, username) on true
join m31_users u on u.username = v.username
on conflict (id)
do update set user_id = excluded.user_id, role_id = excluded.role_id;

insert into projects (
    id, workspace_id, project_key, name, description, status, conversation_id,
    created_by, created_at, updated_by, updated_at, archived_at
)
select
    v.id::uuid,
    c.workspace_id,
    v.project_key,
    v.name,
    v.description,
    'active',
    null,
    c.admin_id,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    c.admin_id,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    null
from m31_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000031101', 'M31P1', 'M31 客户门户改版', '需求提出、IM 讨论、PRD、任务拆分。', 10),
        ('00000000-0000-0000-0000-000000031102', 'M31P2', 'M31 移动端登录优化', 'BUG 提交、修复、验证失败打回、二次验证。', 20),
        ('00000000-0000-0000-0000-000000031103', 'M31P3', 'M31 数据看板建设', 'Base 表格、视图、记录详情、权限边界。', 30),
        ('00000000-0000-0000-0000-000000031104', 'M31P4', 'M31 审批流程上线', '审批、发布协同、审计、通知。', 40),
        ('00000000-0000-0000-0000-000000031105', 'M31P5', 'M31 IM 稳定性专项', '长会话、置顶、撤回、reaction、已读、多成员群聊。', 50)
) as v(id, project_key, name, description, offset_minutes)
on conflict (id)
do update set
    name = excluded.name,
    description = excluded.description,
    status = 'active',
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    archived_at = null;

insert into conversations (
    id, workspace_id, conversation_type, title, owner_id, project_id,
    last_message_id, last_message_at, created_by, created_at, updated_at, archived_at
)
select
    v.conversation_id::uuid,
    c.workspace_id,
    'group',
    v.title,
    c.admin_id,
    p.id,
    null,
    null,
    c.admin_id,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    null
from m31_context c
join (
    values
        ('M31P1', '00000000-0000-0000-0000-000000031201', 'M31 客户门户改版群', 11),
        ('M31P2', '00000000-0000-0000-0000-000000031202', 'M31 移动端登录优化群', 21),
        ('M31P3', '00000000-0000-0000-0000-000000031203', 'M31 数据看板建设群', 31),
        ('M31P4', '00000000-0000-0000-0000-000000031204', 'M31 审批流程上线群', 41),
        ('M31P5', '00000000-0000-0000-0000-000000031205', 'M31 IM 稳定性专项群', 51)
) as v(project_key, conversation_id, title, offset_minutes) on true
join projects p on p.workspace_id = c.workspace_id and p.project_key = v.project_key
on conflict (id)
do update set
    title = excluded.title,
    project_id = excluded.project_id,
    updated_at = excluded.updated_at,
    archived_at = null;

update projects p
set conversation_id = c.id, updated_at = now()
from conversations c
where p.workspace_id = c.workspace_id
  and p.project_key in ('M31P1', 'M31P2', 'M31P3', 'M31P4', 'M31P5')
  and c.project_id = p.id;

with rows(project_key, username, project_role, offset_minutes) as (
    values
        ('M31P1', 'admin', 'owner', 1),
        ('M31P1', 'pm_chen', 'owner', 2),
        ('M31P1', 'product_lin', 'member', 3),
        ('M31P1', 'design_wu', 'member', 4),
        ('M31P1', 'frontend_zhao', 'member', 5),
        ('M31P1', 'backend_wang', 'member', 6),
        ('M31P1', 'business_he', 'viewer', 7),
        ('M31P1', 'viewer_tan', 'viewer', 8),
        ('M31P2', 'admin', 'owner', 11),
        ('M31P2', 'pm_chen', 'owner', 12),
        ('M31P2', 'frontend_zhao', 'member', 13),
        ('M31P2', 'backend_wang', 'member', 14),
        ('M31P2', 'qa_sun', 'member', 15),
        ('M31P2', 'ops_liu', 'viewer', 16),
        ('M31P3', 'admin', 'owner', 21),
        ('M31P3', 'pm_chen', 'owner', 22),
        ('M31P3', 'product_lin', 'member', 23),
        ('M31P3', 'backend_wang', 'member', 24),
        ('M31P3', 'qa_sun', 'member', 25),
        ('M31P3', 'business_he', 'viewer', 26),
        ('M31P4', 'admin', 'owner', 31),
        ('M31P4', 'pm_chen', 'owner', 32),
        ('M31P4', 'ops_liu', 'member', 33),
        ('M31P4', 'backend_wang', 'member', 34),
        ('M31P4', 'qa_sun', 'member', 35),
        ('M31P4', 'business_he', 'viewer', 36),
        ('M31P5', 'admin', 'owner', 41),
        ('M31P5', 'pm_chen', 'owner', 42),
        ('M31P5', 'frontend_zhao', 'member', 43),
        ('M31P5', 'backend_wang', 'member', 44),
        ('M31P5', 'qa_sun', 'member', 45),
        ('M31P5', 'ops_liu', 'member', 46),
        ('M31P5', 'product_lin', 'viewer', 47)
),
numbered as (
    select row_number() over(order by project_key, username) as rn, *
    from rows
)
insert into project_members (id, workspace_id, project_id, user_id, project_role, joined_at, created_by, archived_at)
select
    ('00000000-0000-0000-0000-' || lpad((313000 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    p.id,
    u.id,
    n.project_role,
    c.base_at + (n.offset_minutes || ' minutes')::interval,
    c.admin_id,
    null
from numbered n
join m31_context c on true
join projects p on p.workspace_id = c.workspace_id and p.project_key = n.project_key
join m31_users u on u.username = n.username
on conflict (project_id, user_id)
do update set project_role = excluded.project_role, archived_at = null;

with numbered as (
    select row_number() over(order by c.id, u.username) as rn,
           c.workspace_id, c.id as conversation_id, u.id as user_id,
           case when pm.project_role = 'owner' then 'owner' else 'member' end as member_role,
           pm.joined_at
    from conversations c
    join projects p on p.id = c.project_id
    join project_members pm on pm.project_id = p.id and pm.archived_at is null
    join users u on u.id = pm.user_id
    where p.project_key like 'M31P%'
)
insert into conversation_members (
    id, workspace_id, conversation_id, user_id, member_role,
    last_read_message_id, last_read_at, joined_at, muted, archived_at, pinned_at
)
select
    ('00000000-0000-0000-0000-' || lpad((313500 + rn)::text, 12, '0'))::uuid,
    workspace_id,
    conversation_id,
    user_id,
    member_role,
    null,
    null,
    joined_at,
    false,
    null,
    null
from numbered
on conflict (conversation_id, user_id)
do update set member_role = excluded.member_role, archived_at = null;

insert into iterations (id, workspace_id, project_id, name, status, starts_at, ends_at, created_at, updated_at)
select
    v.id::uuid,
    c.workspace_id,
    p.id,
    v.name,
    v.status,
    v.starts_at::date,
    v.ends_at::date,
    c.base_at,
    c.base_at
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031301', 'M31P1', 'M31-P1 Sprint', 'active', '2026-06-10', '2026-06-28'),
        ('00000000-0000-0000-0000-000000031302', 'M31P2', 'M31-P2 Fix Sprint', 'active', '2026-06-10', '2026-06-24'),
        ('00000000-0000-0000-0000-000000031303', 'M31P3', 'M31-P3 Data Sprint', 'active', '2026-06-12', '2026-06-30'),
        ('00000000-0000-0000-0000-000000031304', 'M31P4', 'M31-P4 Release Sprint', 'closed', '2026-06-01', '2026-06-16'),
        ('00000000-0000-0000-0000-000000031305', 'M31P5', 'M31-P5 Reliability Sprint', 'active', '2026-06-15', '2026-07-05')
) as v(id, project_key, name, status, starts_at, ends_at) on true
join projects p on p.workspace_id = c.workspace_id and p.project_key = v.project_key
on conflict (id)
do update set name = excluded.name, status = excluded.status, starts_at = excluded.starts_at, ends_at = excluded.ends_at, updated_at = excluded.updated_at;

insert into knowledge_base_items (
    id, workspace_id, parent_id, title, content_type, current_version_no, status,
    created_by, created_at, updated_by, updated_at, deleted_at
)
select
    v.id::uuid,
    c.workspace_id,
    nullif(v.parent_id, '')::uuid,
    v.title,
    v.content_type,
    v.version_no,
    'active',
    coalesce(author.id, c.admin_id),
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    coalesce(updater.id, c.admin_id),
    c.base_at + ((v.offset_minutes + 5) || ' minutes')::interval,
    null
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031401', '', 'M31 团队空间（仿真）', 'space', 'M31 固定 10 人、5 项目协同仿真入口。当前系统仅有 parent_id 数据层，尚未提供团队空间树形 UI。', 1, 'admin', 'admin', 60),
        ('00000000-0000-0000-0000-000000031402', '00000000-0000-0000-0000-000000031401', 'M31 P1 客户门户 PRD', 'markdown', '目标：门户首页改版。范围：需求确认、设计补充、任务拆分。缺口：团队空间树形目录仍待 M32。', 2, 'product_lin', 'pm_chen', 61),
        ('00000000-0000-0000-0000-000000031403', '00000000-0000-0000-0000-000000031401', 'M31 P2 登录 BUG 分诊记录', 'markdown', '记录有效、重复、无法复现、信息不足、验证失败和二次通过分支。', 1, 'qa_sun', 'qa_sun', 62),
        ('00000000-0000-0000-0000-000000031404', '00000000-0000-0000-0000-000000031401', 'M31 P3 数据看板口径说明', 'markdown', '经营漏斗看板需要 Base 记录承载指标口径；文档嵌入 Base 视图仍待 M33。', 2, 'product_lin', 'backend_wang', 63),
        ('00000000-0000-0000-0000-000000031405', '00000000-0000-0000-0000-000000031401', 'M31 P4 审批发布清单', 'markdown', '发布前检查：审批通过、通知到达、审计可追溯、回滚预案已确认。', 1, 'ops_liu', 'ops_liu', 64),
        ('00000000-0000-0000-0000-000000031406', '00000000-0000-0000-0000-000000031401', 'M31 P5 IM 稳定性笔记', 'markdown', '覆盖长会话、置顶、撤回、reaction、已读、多成员群聊。', 1, 'pm_chen', 'backend_wang', 65)
) as v(id, parent_id, title, content_type, content, version_no, author_username, updater_username, offset_minutes) on true
left join m31_users author on author.username = v.author_username
left join m31_users updater on updater.username = v.updater_username
on conflict (id)
do update set
    parent_id = excluded.parent_id,
    title = excluded.title,
    content_type = excluded.content_type,
    current_version_no = excluded.current_version_no,
    status = 'active',
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    deleted_at = null;

insert into knowledge_content_versions (id, workspace_id, item_id, version_no, title, block_snapshot, created_by, created_at)
select
    v.id::uuid,
    c.workspace_id,
    v.item_id::uuid,
    v.version_no,
    d.title,
    jsonb_build_array(jsonb_build_object(
        'id', null, 'parentId', null, 'blockType', 'legacy_html', 'content', v.content,
        'sortOrder', 0, 'schemaVersion', 2, 'attrs', '{}'::jsonb,
        'richContent', jsonb_build_object('text', v.content), 'plainText', v.content,
        'anchorId', null, 'deleted', false
    )),
    coalesce(u.id, c.admin_id),
    c.base_at + (v.offset_minutes || ' minutes')::interval
from m31_context c
join knowledge_base_items d on d.workspace_id = c.workspace_id
join (
    values
        ('00000000-0000-0000-0000-000000031451', '00000000-0000-0000-0000-000000031401', 1, 'M31 固定协同空间初始版本。', 'admin', 70),
        ('00000000-0000-0000-0000-000000031452', '00000000-0000-0000-0000-000000031402', 1, '门户 PRD v1：只描述首页改版。', 'product_lin', 71),
        ('00000000-0000-0000-0000-000000031453', '00000000-0000-0000-0000-000000031402', 2, '门户 PRD v2：补充业务方变更和任务拆分。', 'pm_chen', 72),
        ('00000000-0000-0000-0000-000000031454', '00000000-0000-0000-0000-000000031403', 1, '登录 BUG 分诊记录 v1。', 'qa_sun', 73),
        ('00000000-0000-0000-0000-000000031455', '00000000-0000-0000-0000-000000031404', 1, '数据看板口径 v1：字段草案。', 'product_lin', 74),
        ('00000000-0000-0000-0000-000000031456', '00000000-0000-0000-0000-000000031404', 2, '数据看板口径 v2：补充 Base 视图和权限边界。', 'backend_wang', 75),
        ('00000000-0000-0000-0000-000000031457', '00000000-0000-0000-0000-000000031405', 1, '审批发布清单 v1。', 'ops_liu', 76),
        ('00000000-0000-0000-0000-000000031458', '00000000-0000-0000-0000-000000031406', 1, 'IM 稳定性笔记 v1。', 'pm_chen', 77)
) as v(id, item_id, version_no, content, username, offset_minutes) on d.id = v.item_id::uuid
left join m31_users u on u.username = v.username
on conflict (item_id, version_no)
do update set title = excluded.title, block_snapshot = excluded.block_snapshot, created_by = excluded.created_by, created_at = excluded.created_at;

insert into knowledge_content_blocks (
    id, workspace_id, item_id, block_type, content, sort_order, created_by, created_at, updated_by, updated_at, deleted_at
)
select
    v.id::uuid,
    c.workspace_id,
    v.item_id::uuid,
    v.block_type,
    v.content,
    v.sort_order,
    coalesce(u.id, c.admin_id),
    c.base_at + (v.sort_order || ' minutes')::interval,
    coalesce(u.id, c.admin_id),
    c.base_at + ((v.sort_order + 1) || ' minutes')::interval,
    null
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031461', '00000000-0000-0000-0000-000000031402', 'heading', '目标与范围', 1, 'product_lin'),
        ('00000000-0000-0000-0000-000000031462', '00000000-0000-0000-0000-000000031402', 'paragraph', '门户首页改版必须同步业务方验收口径和前后端拆分任务。', 2, 'product_lin'),
        ('00000000-0000-0000-0000-000000031463', '00000000-0000-0000-0000-000000031402', 'task', '补充资料字段、设计稿、后端 API 契约。', 3, 'pm_chen'),
        ('00000000-0000-0000-0000-000000031464', '00000000-0000-0000-0000-000000031403', 'heading', 'BUG 分支覆盖', 1, 'qa_sun'),
        ('00000000-0000-0000-0000-000000031465', '00000000-0000-0000-0000-000000031403', 'list', '有效；重复；无法复现；信息不足；验证失败；验证通过。', 2, 'qa_sun'),
        ('00000000-0000-0000-0000-000000031466', '00000000-0000-0000-0000-000000031404', 'heading', 'Base 口径', 1, 'product_lin'),
        ('00000000-0000-0000-0000-000000031467', '00000000-0000-0000-0000-000000031404', 'paragraph', '文档已嵌入 Base 视图、BUG 卡片和 IM 消息，作为 M33 文档嵌入回归基线。', 2, 'backend_wang'),
        ('00000000-0000-0000-0000-000000031471', '00000000-0000-0000-0000-000000031404', 'table', '{"columns":["对象","口径","状态"],"rows":[["北区漏斗看板","转化率 76%","进行中"],["M31P2-1","验证码按钮回归","closed"]]}', 3, 'product_lin'),
        ('00000000-0000-0000-0000-000000031472', '00000000-0000-0000-0000-000000031404', 'base_view', '{"objectType":"base_table","objectId":"00000000-0000-0000-0000-000000031502","viewId":"00000000-0000-0000-0000-000000031532"}', 4, 'product_lin'),
        ('00000000-0000-0000-0000-000000031473', '00000000-0000-0000-0000-000000031404', 'issue_embed', '{"objectType":"issue","objectId":"00000000-0000-0000-0000-000000031604"}', 5, 'qa_sun'),
        ('00000000-0000-0000-0000-000000031474', '00000000-0000-0000-0000-000000031404', 'message_embed', '{"objectType":"message","objectId":"00000000-0000-0000-0000-000000031841"}', 6, 'pm_chen'),
        ('00000000-0000-0000-0000-000000031468', '00000000-0000-0000-0000-000000031405', 'task', '审批通过后再发布，并写入审计。', 1, 'ops_liu'),
        ('00000000-0000-0000-0000-000000031469', '00000000-0000-0000-0000-000000031406', 'quote', '置顶消息只保留在顶部 bar，不占据原最新位置。', 1, 'pm_chen'),
        ('00000000-0000-0000-0000-000000031470', '00000000-0000-0000-0000-000000031406', 'paragraph', 'reaction、撤回、已读和长会话滚动需要联动验证。', 2, 'backend_wang')
) as v(id, item_id, block_type, content, sort_order, username) on true
left join m31_users u on u.username = v.username
on conflict (id)
do update set
    block_type = excluded.block_type,
    content = excluded.content,
    sort_order = excluded.sort_order,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    deleted_at = null;

with permission_rows(item_id, username, permission_level) as (
    values
        ('00000000-0000-0000-0000-000000031401', 'admin', 'manage'),
        ('00000000-0000-0000-0000-000000031401', 'viewer_tan', 'view'),
        ('00000000-0000-0000-0000-000000031402', 'admin', 'manage'),
        ('00000000-0000-0000-0000-000000031402', 'pm_chen', 'manage'),
        ('00000000-0000-0000-0000-000000031402', 'product_lin', 'edit'),
        ('00000000-0000-0000-0000-000000031402', 'design_wu', 'edit'),
        ('00000000-0000-0000-0000-000000031402', 'frontend_zhao', 'view'),
        ('00000000-0000-0000-0000-000000031403', 'admin', 'manage'),
        ('00000000-0000-0000-0000-000000031403', 'qa_sun', 'manage'),
        ('00000000-0000-0000-0000-000000031403', 'backend_wang', 'edit'),
        ('00000000-0000-0000-0000-000000031404', 'admin', 'manage'),
        ('00000000-0000-0000-0000-000000031404', 'product_lin', 'manage'),
        ('00000000-0000-0000-0000-000000031404', 'backend_wang', 'edit'),
        ('00000000-0000-0000-0000-000000031404', 'business_he', 'view'),
        ('00000000-0000-0000-0000-000000031405', 'admin', 'manage'),
        ('00000000-0000-0000-0000-000000031405', 'ops_liu', 'manage'),
        ('00000000-0000-0000-0000-000000031405', 'pm_chen', 'view'),
        ('00000000-0000-0000-0000-000000031406', 'admin', 'manage'),
        ('00000000-0000-0000-0000-000000031406', 'pm_chen', 'manage'),
        ('00000000-0000-0000-0000-000000031406', 'backend_wang', 'edit'),
        ('00000000-0000-0000-0000-000000031406', 'qa_sun', 'view')
),
numbered as (
    select row_number() over(order by item_id, username) rn, *
    from permission_rows
)
insert into knowledge_item_permissions (
    id, workspace_id, item_id, user_id, permission_level, created_by, created_at, updated_by, updated_at, revoked_at
)
select
    ('00000000-0000-0000-0000-' || lpad((314800 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    n.item_id::uuid,
    u.id,
    n.permission_level,
    c.admin_id,
    c.base_at,
    c.admin_id,
    c.base_at,
    null
from numbered n
join m31_context c on true
join m31_users u on u.username = n.username
on conflict (item_id, user_id)
do update set permission_level = excluded.permission_level, updated_by = excluded.updated_by, updated_at = excluded.updated_at, revoked_at = null;

insert into bases (id, workspace_id, name, description, status, created_by, created_at, updated_by, updated_at, archived_at)
select
    '00000000-0000-0000-0000-000000031501'::uuid,
    c.workspace_id,
    'M31 数据看板 Base',
    'P3 经营数据看板的固定仿真表格，覆盖字段、视图、记录和权限。',
    'active',
    c.admin_id,
    c.base_at + interval '80 minutes',
    c.admin_id,
    c.base_at + interval '80 minutes',
    null
from m31_context c
on conflict (id)
do update set name = excluded.name, description = excluded.description, status = 'active', updated_by = excluded.updated_by, updated_at = excluded.updated_at, archived_at = null;

insert into base_tables (id, workspace_id, base_id, name, primary_field_id, created_by, created_at, updated_by, updated_at, archived_at)
select
    '00000000-0000-0000-0000-000000031502'::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031501'::uuid,
    '看板需求池',
    null,
    c.admin_id,
    c.base_at + interval '81 minutes',
    c.admin_id,
    c.base_at + interval '81 minutes',
    null
from m31_context c
on conflict (id)
do update set name = excluded.name, updated_by = excluded.updated_by, updated_at = excluded.updated_at, archived_at = null;

insert into base_fields (
    id, workspace_id, table_id, field_key, name, field_type, config, required, sort_order,
    created_by, created_at, updated_by, updated_at, archived_at
)
select
    v.id::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031502'::uuid,
    v.field_key,
    v.name,
    v.field_type,
    v.config::jsonb,
    v.required,
    v.sort_order,
    c.admin_id,
    c.base_at + ((82 + v.sort_order) || ' minutes')::interval,
    c.admin_id,
    c.base_at + ((82 + v.sort_order) || ' minutes')::interval,
    null
from m31_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000031511', 'title', '需求名称', 'text', '{}'::text, true, 1),
        ('00000000-0000-0000-0000-000000031512', 'owner', '负责人', 'member', '{}'::text, true, 2),
        ('00000000-0000-0000-0000-000000031513', 'status', '状态', 'single_select', '{"options":["待确认","进行中","已完成","阻塞"]}'::text, true, 3),
        ('00000000-0000-0000-0000-000000031514', 'metric', '指标值', 'number', '{}'::text, false, 4),
        ('00000000-0000-0000-0000-000000031515', 'due_date', '截止日期', 'date', '{}'::text, false, 5),
        ('00000000-0000-0000-0000-000000031516', 'tags', '标签', 'multi_select', '{"options":["漏斗","权限","发布","风险"]}'::text, false, 6)
) as v(id, field_key, name, field_type, config, required, sort_order)
on conflict (id)
do update set name = excluded.name, field_type = excluded.field_type, config = excluded.config, required = excluded.required, sort_order = excluded.sort_order, updated_at = excluded.updated_at, archived_at = null;

update base_tables
set primary_field_id = '00000000-0000-0000-0000-000000031511'::uuid,
    updated_at = now()
where id = '00000000-0000-0000-0000-000000031502'::uuid;

with member_rows(username, permission_level) as (
    values
        ('admin', 'manage'),
        ('pm_chen', 'manage'),
        ('product_lin', 'edit'),
        ('backend_wang', 'edit'),
        ('qa_sun', 'view'),
        ('business_he', 'view')
),
numbered as (
    select row_number() over(order by username) rn, *
    from member_rows
)
insert into base_members (id, workspace_id, base_id, user_id, permission_level, created_by, created_at, updated_by, updated_at, revoked_at)
select
    ('00000000-0000-0000-0000-' || lpad((315200 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031501'::uuid,
    u.id,
    n.permission_level,
    c.admin_id,
    c.base_at + interval '90 minutes',
    c.admin_id,
    c.base_at + interval '90 minutes',
    null
from numbered n
join m31_context c on true
join m31_users u on u.username = n.username
on conflict (base_id, user_id)
do update set permission_level = excluded.permission_level, updated_by = excluded.updated_by, updated_at = excluded.updated_at, revoked_at = null;

insert into base_records (id, workspace_id, table_id, record_no, created_by, created_at, updated_by, updated_at, deleted_at)
select
    v.id::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031502'::uuid,
    v.record_no,
    c.admin_id,
    c.base_at + ((90 + v.record_no) || ' minutes')::interval,
    c.admin_id,
    c.base_at + ((95 + v.record_no) || ' minutes')::interval,
    null
from m31_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000031521', 1),
        ('00000000-0000-0000-0000-000000031522', 2),
        ('00000000-0000-0000-0000-000000031523', 3),
        ('00000000-0000-0000-0000-000000031524', 4)
) as v(id, record_no)
on conflict (id)
do update set updated_by = excluded.updated_by, updated_at = excluded.updated_at, deleted_at = null;

with values_rows(record_id, field_id, value_json, value_text, value_number, value_date) as (
    values
        ('00000000-0000-0000-0000-000000031521', '00000000-0000-0000-0000-000000031511', '"北区漏斗看板"', '北区漏斗看板', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031521', '00000000-0000-0000-0000-000000031512', '"00000000-0000-0000-0000-000000031002"', '00000000-0000-0000-0000-000000031002', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031521', '00000000-0000-0000-0000-000000031513', '"进行中"', '进行中', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031521', '00000000-0000-0000-0000-000000031514', '76', '76', 76::numeric, null::date),
        ('00000000-0000-0000-0000-000000031521', '00000000-0000-0000-0000-000000031515', '"2026-06-24"', '2026-06-24', null::numeric, '2026-06-24'::date),
        ('00000000-0000-0000-0000-000000031521', '00000000-0000-0000-0000-000000031516', '["漏斗","风险"]', '漏斗 风险', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031522', '00000000-0000-0000-0000-000000031511', '"权限视图回归"', '权限视图回归', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031522', '00000000-0000-0000-0000-000000031512', '"00000000-0000-0000-0000-000000031005"', '00000000-0000-0000-0000-000000031005', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031522', '00000000-0000-0000-0000-000000031513', '"阻塞"', '阻塞', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031522', '00000000-0000-0000-0000-000000031514', '42', '42', 42::numeric, null::date),
        ('00000000-0000-0000-0000-000000031522', '00000000-0000-0000-0000-000000031515', '"2026-06-26"', '2026-06-26', null::numeric, '2026-06-26'::date),
        ('00000000-0000-0000-0000-000000031522', '00000000-0000-0000-0000-000000031516', '["权限","风险"]', '权限 风险', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031523', '00000000-0000-0000-0000-000000031511', '"发布转化日报"', '发布转化日报', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031523', '00000000-0000-0000-0000-000000031512', '"00000000-0000-0000-0000-000000031007"', '00000000-0000-0000-0000-000000031007', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031523', '00000000-0000-0000-0000-000000031513', '"已完成"', '已完成', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031523', '00000000-0000-0000-0000-000000031514', '91', '91', 91::numeric, null::date),
        ('00000000-0000-0000-0000-000000031523', '00000000-0000-0000-0000-000000031515', '"2026-06-20"', '2026-06-20', null::numeric, '2026-06-20'::date),
        ('00000000-0000-0000-0000-000000031523', '00000000-0000-0000-0000-000000031516', '["发布"]', '发布', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031524', '00000000-0000-0000-0000-000000031511', '"业务验收样本"', '业务验收样本', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031524', '00000000-0000-0000-0000-000000031512', '"00000000-0000-0000-0000-000000031008"', '00000000-0000-0000-0000-000000031008', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031524', '00000000-0000-0000-0000-000000031513', '"待确认"', '待确认', null::numeric, null::date),
        ('00000000-0000-0000-0000-000000031524', '00000000-0000-0000-0000-000000031514', '18', '18', 18::numeric, null::date),
        ('00000000-0000-0000-0000-000000031524', '00000000-0000-0000-0000-000000031515', '"2026-06-29"', '2026-06-29', null::numeric, '2026-06-29'::date),
        ('00000000-0000-0000-0000-000000031524', '00000000-0000-0000-0000-000000031516', '["漏斗"]', '漏斗', null::numeric, null::date)
),
numbered as (
    select row_number() over(order by record_id, field_id) rn, *
    from values_rows
)
insert into base_record_values (
    id, workspace_id, record_id, field_id, value_json, value_text, value_number, value_date, updated_by, updated_at
)
select
    ('00000000-0000-0000-0000-' || lpad((315500 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    record_id::uuid,
    field_id::uuid,
    value_json::jsonb,
    value_text,
    value_number,
    value_date,
    c.admin_id,
    c.base_at + interval '100 minutes'
from numbered
join m31_context c on true
on conflict (record_id, field_id)
do update set value_json = excluded.value_json, value_text = excluded.value_text, value_number = excluded.value_number, value_date = excluded.value_date, updated_by = excluded.updated_by, updated_at = excluded.updated_at;

insert into base_views (id, workspace_id, table_id, name, filters, sorts, created_by, created_at, updated_by, updated_at, archived_at)
select
    v.id::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031502'::uuid,
    v.name,
    v.filters::jsonb,
    v.sorts::jsonb,
    c.admin_id,
    c.base_at + interval '110 minutes',
    c.admin_id,
    c.base_at + interval '110 minutes',
    null
from m31_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000031531', '风险视图', '[{"fieldId":"00000000-0000-0000-0000-000000031516","operator":"contains","value":"风险"}]', '[{"fieldId":"00000000-0000-0000-0000-000000031515","direction":"asc"}]'),
        ('00000000-0000-0000-0000-000000031532', '进行中视图', '[{"fieldId":"00000000-0000-0000-0000-000000031513","operator":"eq","value":"进行中"}]', '[{"fieldId":"00000000-0000-0000-0000-000000031514","direction":"desc"}]'),
        ('00000000-0000-0000-0000-000000031533', '业务验收视图', '[{"fieldId":"00000000-0000-0000-0000-000000031511","operator":"contains","value":"业务"}]', '[]')
) as v(id, name, filters, sorts)
on conflict (id)
do update set name = excluded.name, filters = excluded.filters, sorts = excluded.sorts, updated_at = excluded.updated_at, archived_at = null;

insert into issues (
    id, workspace_id, project_id, issue_key, issue_type, title, description, priority, status,
    workflow_reason, workflow_note, resolution,
    assignee_id, reporter_id, iteration_id, due_at, created_by, created_at, updated_by, updated_at,
    resolved_at, closed_at, deleted_at
)
select
    v.id::uuid,
    c.workspace_id,
    p.id,
    v.issue_key,
    v.issue_type,
    v.title,
    v.description,
    v.priority,
    v.status,
    v.workflow_reason,
    v.workflow_note,
    v.resolution,
    assignee.id,
    reporter.id,
    it.id,
    v.due_at::date,
    reporter.id,
    c.base_at + (v.created_offset || ' minutes')::interval,
    coalesce(updater.id, reporter.id),
    c.base_at + (v.updated_offset || ' minutes')::interval,
    case when v.status = 'resolved' then c.base_at + (v.updated_offset || ' minutes')::interval else null end,
    case when v.status = 'closed' then c.base_at + (v.updated_offset || ' minutes')::interval else null end,
    null
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031601', 'M31P1', 'M31P1-1', 'requirement', '客户门户首页改版需求已确认', '正常确认并拆任务。关联 PRD 和 IM 讨论。', 'high', 'closed', 'closed', '业务验收通过，需求关闭。', 'done', 'frontend_zhao', 'product_lin', 'pm_chen', '2026-06-24', 120, 180),
        ('00000000-0000-0000-0000-000000031602', 'M31P1', 'M31P1-2', 'task', '门户首页组件拆分', '前端拆分导航、卡片、统计区。', 'medium', 'in_progress', 'started', '前端已领取组件拆分。', null, 'frontend_zhao', 'pm_chen', 'frontend_zhao', '2026-06-25', 121, 181),
        ('00000000-0000-0000-0000-000000031603', 'M31P1', 'M31P1-3', 'requirement', '资料字段补充不完整', '需求信息不足，被打回产品补充。', 'medium', 'open', 'info_required', '缺少业务字段口径。', 'info_required', 'product_lin', 'business_he', 'product_lin', '2026-06-27', 122, 182),
        ('00000000-0000-0000-0000-000000031604', 'M31P2', 'M31P2-1', 'bug', '移动端验证码按钮卡死', '有效 BUG：修复后验证失败，二次修复验证通过。', 'urgent', 'closed', 'verified', '二次修复后验证通过。', 'verified', 'backend_wang', 'qa_sun', 'qa_sun', '2026-06-21', 123, 200),
        ('00000000-0000-0000-0000-000000031605', 'M31P2', 'M31P2-2', 'bug', '登录失败截图缺少环境信息', '信息不足，QA 阻塞验证并要求补充设备和版本。', 'high', 'open', 'info_required', '缺少设备型号、App 版本和网络截图。', 'info_required', 'qa_sun', 'qa_sun', 'qa_sun', '2026-06-23', 124, 201),
        ('00000000-0000-0000-0000-000000031606', 'M31P2', 'M31P2-3', 'bug', '重复：验证码倒计时异常', '重复 BUG，关联 M31P2-1 后关闭。', 'medium', 'closed', 'duplicate', '与 M31P2-1 重复。', 'duplicate', 'backend_wang', 'qa_sun', 'qa_sun', '2026-06-22', 125, 202),
        ('00000000-0000-0000-0000-000000031607', 'M31P2', 'M31P2-4', 'bug', '无法复现：扫码回跳空白', '无法复现，记录环境后关闭。', 'low', 'closed', 'cannot_reproduce', '三台设备均无法复现，保留日志后关闭。', 'cannot_reproduce', 'frontend_zhao', 'qa_sun', 'frontend_zhao', '2026-06-24', 126, 203),
        ('00000000-0000-0000-0000-000000031608', 'M31P3', 'M31P3-1', 'requirement', '经营数据看板字段口径变更', '需求中途变更，产生文档版本和 Base 记录关系。', 'high', 'in_progress', 'scope_changed', '新增北区漏斗看板，影响 Base 字段。', 'scope_changed', 'backend_wang', 'product_lin', 'backend_wang', '2026-06-28', 127, 204),
        ('00000000-0000-0000-0000-000000031609', 'M31P3', 'M31P3-2', 'task', 'Base 视图权限校验', '验证 viewer_tan 不可见 P3 Base。', 'medium', 'open', null, null, null, 'qa_sun', 'pm_chen', 'qa_sun', '2026-06-29', 128, 205),
        ('00000000-0000-0000-0000-000000031610', 'M31P4', 'M31P4-1', 'requirement', '审批发布流程上线', '审批通过后发布，并记录通知和审计。', 'high', 'closed', 'closed', '发布窗口审批通过。', 'done', 'ops_liu', 'pm_chen', 'ops_liu', '2026-06-18', 129, 206),
        ('00000000-0000-0000-0000-000000031611', 'M31P4', 'M31P4-2', 'task', '延期发布通知业务方', '延期分支：通知业务方并保留审计。', 'medium', 'closed', 'delayed', '发布延期已同步业务方。', 'delayed', 'ops_liu', 'pm_chen', 'ops_liu', '2026-06-19', 130, 207),
        ('00000000-0000-0000-0000-000000031612', 'M31P5', 'M31P5-1', 'bug', 'IM 长会话置顶与撤回回归', '验证置顶 bar、撤回占位、reaction、已读和长会话滚动。', 'high', 'resolved', 'fixed', '长会话修复已提交，等待最终验证。', 'fixed', 'backend_wang', 'qa_sun', 'backend_wang', '2026-06-30', 131, 208),
        ('00000000-0000-0000-0000-000000031613', 'M31P1', 'M31P1-4', 'requirement', '业务活动入口取消', '需求取消分支：保留讨论和关闭原因。', 'low', 'closed', 'canceled', '业务活动取消，关闭需求。', 'canceled', 'pm_chen', 'business_he', 'pm_chen', '2026-06-26', 132, 209)
) as v(id, project_key, issue_key, issue_type, title, description, priority, status, workflow_reason, workflow_note, resolution, assignee_username, reporter_username, updater_username, due_at, created_offset, updated_offset) on true
join projects p on p.workspace_id = c.workspace_id and p.project_key = v.project_key
left join iterations it on it.project_id = p.id
left join m31_users assignee on assignee.username = v.assignee_username
join m31_users reporter on reporter.username = v.reporter_username
left join m31_users updater on updater.username = v.updater_username
on conflict (id)
do update set
    title = excluded.title,
    description = excluded.description,
    priority = excluded.priority,
    status = excluded.status,
    assignee_id = excluded.assignee_id,
    reporter_id = excluded.reporter_id,
    iteration_id = excluded.iteration_id,
    due_at = excluded.due_at,
    workflow_reason = excluded.workflow_reason,
    workflow_note = excluded.workflow_note,
    resolution = excluded.resolution,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    resolved_at = excluded.resolved_at,
    closed_at = excluded.closed_at,
    deleted_at = null;

insert into issue_comments (id, workspace_id, issue_id, author_id, content, created_at, updated_at, deleted_at)
select
    v.id::uuid,
    c.workspace_id,
    v.issue_id::uuid,
    u.id,
    v.content,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    null,
    null
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031641', '00000000-0000-0000-0000-000000031601', 'pm_chen', '需求已确认，拆前端和后端任务。', 220),
        ('00000000-0000-0000-0000-000000031642', '00000000-0000-0000-0000-000000031603', 'product_lin', '补充字段口径后再进入排期。', 221),
        ('00000000-0000-0000-0000-000000031643', '00000000-0000-0000-0000-000000031604', 'qa_sun', '首次验证失败：验证码按钮仍然偶发卡死。', 222),
        ('00000000-0000-0000-0000-000000031644', '00000000-0000-0000-0000-000000031604', 'backend_wang', '已二次修复 session 锁竞争。', 223),
        ('00000000-0000-0000-0000-000000031645', '00000000-0000-0000-0000-000000031605', 'qa_sun', '缺少设备型号、App 版本和网络截图，暂不进入修复。', 224),
        ('00000000-0000-0000-0000-000000031646', '00000000-0000-0000-0000-000000031606', 'qa_sun', '与 M31P2-1 重复，关闭本单。', 225),
        ('00000000-0000-0000-0000-000000031647', '00000000-0000-0000-0000-000000031607', 'frontend_zhao', '三台设备均无法复现，保留日志后关闭。', 226),
        ('00000000-0000-0000-0000-000000031648', '00000000-0000-0000-0000-000000031608', 'business_he', '新增北区漏斗看板，影响 Base 字段和文档版本。', 227),
        ('00000000-0000-0000-0000-000000031649', '00000000-0000-0000-0000-000000031610', 'ops_liu', '审批已通过，发布窗口确认。', 228),
        ('00000000-0000-0000-0000-000000031650', '00000000-0000-0000-0000-000000031611', 'pm_chen', '发布延期已同步业务方。', 229),
        ('00000000-0000-0000-0000-000000031651', '00000000-0000-0000-0000-000000031612', 'qa_sun', '长会话定位和置顶 bar 需要浏览器冒烟。', 230)
) as v(id, issue_id, username, content, offset_minutes) on true
join m31_users u on u.username = v.username
on conflict (id)
do update set content = excluded.content, deleted_at = null;

insert into issue_verification_logs (
    id, workspace_id, issue_id, verifier_id, result, note, created_at, environment, reproduction_steps, fix_version
)
select
    v.id::uuid,
    c.workspace_id,
    v.issue_id::uuid,
    u.id,
    v.result,
    v.note,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    v.environment,
    v.steps,
    v.fix_version
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031661', '00000000-0000-0000-0000-000000031604', 'qa_sun', 'failed', '首次修复未完全解决，验证码按钮仍卡住。', 'Android 14 / Chrome WebView / test-db', '1. 打开登录页；2. 快速点击验证码；3. 倒计时卡住。', 'm31-fix-1', 240),
        ('00000000-0000-0000-0000-000000031662', '00000000-0000-0000-0000-000000031604', 'qa_sun', 'passed', '二次修复通过，连续 20 次未复现。', 'Android 14 / Chrome WebView / test-db', '重复首次步骤。', 'm31-fix-2', 250),
        ('00000000-0000-0000-0000-000000031663', '00000000-0000-0000-0000-000000031605', 'qa_sun', 'blocked', '缺少环境信息，无法继续验证。', 'unknown', '截图缺少设备与版本。', null, 241),
        ('00000000-0000-0000-0000-000000031664', '00000000-0000-0000-0000-000000031607', 'qa_sun', 'passed', '无法复现路径已关闭，保留日志。', 'iOS 18 / Safari / test-db', '扫码回跳 10 次。', null, 242),
        ('00000000-0000-0000-0000-000000031665', '00000000-0000-0000-0000-000000031612', 'qa_sun', 'passed', '置顶、撤回、reaction、已读路径通过脚本基线。', 'Windows / Chrome / local', '打开 P5 群，定位置顶和撤回消息。', 'm31-im-smoke', 260)
) as v(id, issue_id, username, result, note, environment, steps, fix_version, offset_minutes) on true
join m31_users u on u.username = v.username
on conflict (id)
do update set result = excluded.result, note = excluded.note, environment = excluded.environment, reproduction_steps = excluded.reproduction_steps, fix_version = excluded.fix_version, created_at = excluded.created_at;

insert into issue_activity_logs (
    id, workspace_id, issue_id, actor_id, action, from_value, to_value, metadata, created_at
)
select
    v.id::uuid,
    c.workspace_id,
    v.issue_id::uuid,
    u.id,
    v.action,
    v.from_value,
    v.to_value,
    v.metadata::jsonb,
    c.base_at + (v.offset_minutes || ' minutes')::interval
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031671', '00000000-0000-0000-0000-000000031601', 'pm_chen', 'status.changed', 'open', 'closed', '{"branch":"requirement.confirmed"}', 270),
        ('00000000-0000-0000-0000-000000031672', '00000000-0000-0000-0000-000000031603', 'product_lin', 'requirement.returned', 'in_progress', 'open', '{"branch":"requirement.insufficient"}', 271),
        ('00000000-0000-0000-0000-000000031673', '00000000-0000-0000-0000-000000031604', 'qa_sun', 'verification.failed', 'resolved', 'in_progress', '{"branch":"bug.failed_then_returned"}', 272),
        ('00000000-0000-0000-0000-000000031674', '00000000-0000-0000-0000-000000031604', 'qa_sun', 'verification.passed', 'resolved', 'closed', '{"branch":"bug.second_fix_passed"}', 273),
        ('00000000-0000-0000-0000-000000031675', '00000000-0000-0000-0000-000000031605', 'qa_sun', 'verification.blocked', 'open', 'open', '{"branch":"bug.insufficient_info"}', 274),
        ('00000000-0000-0000-0000-000000031676', '00000000-0000-0000-0000-000000031606', 'qa_sun', 'bug.duplicate', 'open', 'closed', '{"branch":"bug.duplicate"}', 275),
        ('00000000-0000-0000-0000-000000031677', '00000000-0000-0000-0000-000000031607', 'frontend_zhao', 'bug.cannot_reproduce', 'open', 'closed', '{"branch":"bug.cannot_reproduce"}', 276),
        ('00000000-0000-0000-0000-000000031678', '00000000-0000-0000-0000-000000031608', 'backend_wang', 'requirement.changed', 'open', 'in_progress', '{"branch":"requirement.changed","documentVersion":2}', 277),
        ('00000000-0000-0000-0000-000000031679', '00000000-0000-0000-0000-000000031611', 'ops_liu', 'requirement.delayed', 'in_progress', 'closed', '{"branch":"requirement.delayed"}', 278),
        ('00000000-0000-0000-0000-000000031680', '00000000-0000-0000-0000-000000031613', 'pm_chen', 'requirement.canceled', 'open', 'closed', '{"branch":"requirement.canceled"}', 279)
) as v(id, issue_id, username, action, from_value, to_value, metadata, offset_minutes) on true
join m31_users u on u.username = v.username
on conflict (id)
do update set action = excluded.action, from_value = excluded.from_value, to_value = excluded.to_value, metadata = excluded.metadata, created_at = excluded.created_at;

insert into issue_relations (id, workspace_id, issue_id, target_type, target_id, created_by, created_at, deleted_at)
select
    v.id::uuid,
    c.workspace_id,
    v.issue_id::uuid,
    v.target_type,
    v.target_id::uuid,
    coalesce(u.id, c.admin_id),
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    null
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031691', '00000000-0000-0000-0000-000000031601', 'document', '00000000-0000-0000-0000-000000031402', 'pm_chen', 290),
        ('00000000-0000-0000-0000-000000031692', '00000000-0000-0000-0000-000000031604', 'document', '00000000-0000-0000-0000-000000031403', 'qa_sun', 291),
        ('00000000-0000-0000-0000-000000031693', '00000000-0000-0000-0000-000000031606', 'issue', '00000000-0000-0000-0000-000000031604', 'qa_sun', 292),
        ('00000000-0000-0000-0000-000000031694', '00000000-0000-0000-0000-000000031608', 'base_record', '00000000-0000-0000-0000-000000031521', 'backend_wang', 293),
        ('00000000-0000-0000-0000-000000031695', '00000000-0000-0000-0000-000000031610', 'approval', '00000000-0000-0000-0000-000000031701', 'ops_liu', 294)
) as v(id, issue_id, target_type, target_id, username, offset_minutes) on true
left join m31_users u on u.username = v.username
on conflict (workspace_id, issue_id, target_type, target_id)
do update set deleted_at = null, created_by = excluded.created_by, created_at = excluded.created_at;

insert into knowledge_item_relations (id, workspace_id, item_id, target_type, target_id, created_by, created_at, deleted_at)
select
    v.id::uuid,
    c.workspace_id,
    v.item_id::uuid,
    v.target_type,
    v.target_id::uuid,
    coalesce(u.id, c.admin_id),
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    null
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031741', '00000000-0000-0000-0000-000000031402', 'issue', '00000000-0000-0000-0000-000000031601', 'pm_chen', 300),
        ('00000000-0000-0000-0000-000000031742', '00000000-0000-0000-0000-000000031403', 'issue', '00000000-0000-0000-0000-000000031604', 'qa_sun', 301),
        ('00000000-0000-0000-0000-000000031743', '00000000-0000-0000-0000-000000031404', 'base', '00000000-0000-0000-0000-000000031501', 'product_lin', 302),
        ('00000000-0000-0000-0000-000000031744', '00000000-0000-0000-0000-000000031404', 'base_record', '00000000-0000-0000-0000-000000031521', 'backend_wang', 303),
        ('00000000-0000-0000-0000-000000031745', '00000000-0000-0000-0000-000000031405', 'issue', '00000000-0000-0000-0000-000000031610', 'ops_liu', 304)
) as v(id, item_id, target_type, target_id, username, offset_minutes) on true
left join m31_users u on u.username = v.username
on conflict (workspace_id, item_id, target_type, target_id)
do update set deleted_at = null, created_by = excluded.created_by, created_at = excluded.created_at;

insert into approval_instances (
    id, workspace_id, form_id, form_key, title, applicant_id, status, current_node_order,
    payload_json, submitted_at, completed_at, created_at, updated_by, updated_at, withdrawn_at
)
select
    '00000000-0000-0000-0000-000000031701'::uuid,
    c.workspace_id,
    f.id,
    f.form_key,
    'M31 P4 生产发布权限申请',
    u.id,
    'approved',
    1,
    '{"targetType":"项目","targetName":"M31 审批流程上线","permissionLevel":"发布","reason":"P4 发布窗口审批"}'::jsonb,
    c.base_at + interval '310 minutes',
    c.base_at + interval '320 minutes',
    c.base_at + interval '310 minutes',
    c.admin_id,
    c.base_at + interval '320 minutes',
    null
from m31_context c
join approval_forms f on f.workspace_id = c.workspace_id and f.form_key = 'permission_request'
join m31_users u on u.username = 'ops_liu'
on conflict (id)
do update set status = excluded.status, payload_json = excluded.payload_json, completed_at = excluded.completed_at, updated_by = excluded.updated_by, updated_at = excluded.updated_at;

insert into approval_tasks (
    id, workspace_id, instance_id, node_order, assignee_id, status, comment, acted_at, transferred_to, created_at, updated_at
)
select
    '00000000-0000-0000-0000-000000031711'::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031701'::uuid,
    1,
    c.admin_id,
    'approved',
    'M31 发布窗口同意。',
    c.base_at + interval '320 minutes',
    null,
    c.base_at + interval '311 minutes',
    c.base_at + interval '320 minutes'
from m31_context c
on conflict (id)
do update set status = excluded.status, comment = excluded.comment, acted_at = excluded.acted_at, updated_at = excluded.updated_at;

insert into approval_action_logs (
    id, workspace_id, instance_id, actor_id, action, from_status, to_status, comment, metadata, created_at
)
select
    '00000000-0000-0000-0000-000000031721'::uuid,
    c.workspace_id,
    '00000000-0000-0000-0000-000000031701'::uuid,
    c.admin_id,
    'approved',
    'pending',
    'approved',
    'M31 发布审批通过。',
    '{"taskId":"00000000-0000-0000-0000-000000031711"}'::jsonb,
    c.base_at + interval '320 minutes'
from m31_context c
on conflict (id)
do update set action = excluded.action, from_status = excluded.from_status, to_status = excluded.to_status, comment = excluded.comment, metadata = excluded.metadata, created_at = excluded.created_at;

insert into messages (
    id, workspace_id, conversation_id, sender_id, client_message_id, message_type, content,
    reply_to_message_id, created_at, deleted_at, edited_at, revoked_at, pinned_at, pinned_by
)
select
    v.id::uuid,
    c.workspace_id,
    conv.id,
    sender.id,
    v.client_message_id,
    'text',
    v.content,
    nullif(v.reply_to, '')::uuid,
    c.base_at + (v.offset_minutes || ' minutes')::interval,
    null,
    case when v.edited then c.base_at + ((v.offset_minutes + 3) || ' minutes')::interval else null end,
    case when v.revoked then c.base_at + ((v.offset_minutes + 4) || ' minutes')::interval else null end,
    case when v.pinned then c.base_at + ((v.offset_minutes + 5) || ' minutes')::interval else null end,
    case when v.pinned then coalesce(pinner.id, sender.id) else null end
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031801', 'M31P1', 'product_lin', 'm31-p1-01', '门户首页 PRD 已补到 /docs/00000000-0000-0000-0000-000000031402，请 @pm_chen 确认范围。', '', false, false, true, 'pm_chen', 330),
        ('00000000-0000-0000-0000-000000031802', 'M31P1', 'pm_chen', 'm31-p1-02', '确认范围，M31P1-1 关闭，M31P1-2 拆给前端。', '00000000-0000-0000-0000-000000031801', false, false, false, '', 331),
        ('00000000-0000-0000-0000-000000031803', 'M31P1', 'business_he', 'm31-p1-03', '业务活动入口取消，保留关闭原因。', '', false, false, false, '', 332),
        ('00000000-0000-0000-0000-000000031811', 'M31P2', 'qa_sun', 'm31-p2-01', 'M31P2-1 首次验证失败，已记录 failed。', '', false, false, true, 'qa_sun', 340),
        ('00000000-0000-0000-0000-000000031812', 'M31P2', 'backend_wang', 'm31-p2-02', '二次修复已发布，请复测。', '00000000-0000-0000-0000-000000031811', true, false, false, '', 341),
        ('00000000-0000-0000-0000-000000031813', 'M31P2', 'qa_sun', 'm31-p2-03', 'M31P2-3 重复，关联 M31P2-1 后关闭。', '', false, false, false, '', 342),
        ('00000000-0000-0000-0000-000000031821', 'M31P3', 'product_lin', 'm31-p3-01', 'P3 Base 记录北区漏斗看板已建立：/bases/00000000-0000-0000-0000-000000031501/tables/00000000-0000-0000-0000-000000031502/records/00000000-0000-0000-0000-000000031521', '', false, false, true, 'product_lin', 350),
        ('00000000-0000-0000-0000-000000031822', 'M31P3', 'backend_wang', 'm31-p3-02', 'viewer_tan 不在 P3 Base 权限中，用来验证拒绝访问。', '', false, false, false, '', 351),
        ('00000000-0000-0000-0000-000000031831', 'M31P4', 'ops_liu', 'm31-p4-01', 'P4 发布审批已通过：/approvals/00000000-0000-0000-0000-000000031701', '', false, false, true, 'ops_liu', 360),
        ('00000000-0000-0000-0000-000000031832', 'M31P4', 'pm_chen', 'm31-p4-02', '延期通知已同步业务方，并写入审计。', '', false, false, false, '', 361),
        ('00000000-0000-0000-0000-000000031841', 'M31P5', 'pm_chen', 'm31-p5-01', 'IM 稳定性专项：置顶消息只出现在顶部 bar，不保留在最新位置。', '', false, false, true, 'pm_chen', 370),
        ('00000000-0000-0000-0000-000000031842', 'M31P5', 'qa_sun', 'm31-p5-02', '长会话定位 messageId，需要浏览器冒烟。', '', false, false, false, '', 371),
        ('00000000-0000-0000-0000-000000031843', 'M31P5', 'backend_wang', 'm31-p5-03', '临时调试指令已撤回', '', false, true, false, '', 372),
        ('00000000-0000-0000-0000-000000031844', 'M31P5', 'frontend_zhao', 'm31-p5-04', '@qa_sun reaction、撤回、已读联动已经补齐。', '', true, false, false, '', 373)
) as v(id, project_key, sender_username, client_message_id, content, reply_to, edited, revoked, pinned, pinned_by_username, offset_minutes) on true
join projects p on p.workspace_id = c.workspace_id and p.project_key = v.project_key
join conversations conv on conv.project_id = p.id
join m31_users sender on sender.username = v.sender_username
left join m31_users pinner on pinner.username = v.pinned_by_username
on conflict (id)
do update set
    content = excluded.content,
    reply_to_message_id = excluded.reply_to_message_id,
    edited_at = excluded.edited_at,
    revoked_at = excluded.revoked_at,
    pinned_at = excluded.pinned_at,
    pinned_by = excluded.pinned_by;

with latest as (
    select distinct on (conversation_id) conversation_id, id, created_at
    from messages
    where client_message_id like 'm31-%'
    order by conversation_id, created_at desc, id desc
)
update conversations c
set last_message_id = l.id,
    last_message_at = l.created_at,
    updated_at = l.created_at
from latest l
where c.id = l.conversation_id;

with mention_rows(message_id, username) as (
    values
        ('00000000-0000-0000-0000-000000031801', 'pm_chen'),
        ('00000000-0000-0000-0000-000000031844', 'qa_sun'),
        ('00000000-0000-0000-0000-000000031821', 'backend_wang')
),
numbered as (
    select row_number() over(order by message_id, username) rn, *
    from mention_rows
)
insert into message_mentions (id, workspace_id, conversation_id, message_id, mentioned_user_id, created_at)
select
    ('00000000-0000-0000-0000-' || lpad((318800 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    m.conversation_id,
    m.id,
    u.id,
    m.created_at
from numbered n
join m31_context c on true
join messages m on m.id = n.message_id::uuid
join m31_users u on u.username = n.username
on conflict (message_id, mentioned_user_id)
do update set created_at = excluded.created_at;

insert into message_links (
    id, workspace_id, conversation_id, message_id, source_url, target_type, target_id,
    web_path, deep_link, card_snapshot, created_at
)
select
    v.id::uuid,
    c.workspace_id,
    m.conversation_id,
    m.id,
    v.source_url,
    v.target_type,
    v.target_id::uuid,
    v.web_path,
    v.deep_link,
    v.card_snapshot::jsonb,
    m.created_at
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031881', '00000000-0000-0000-0000-000000031801', '/docs/00000000-0000-0000-0000-000000031402', 'document', '00000000-0000-0000-0000-000000031402', '/docs/00000000-0000-0000-0000-000000031402', 'colla://document/00000000-0000-0000-0000-000000031402', '{"title":"M31 P1 客户门户 PRD","status":"active"}'),
        ('00000000-0000-0000-0000-000000031882', '00000000-0000-0000-0000-000000031811', '/issues/00000000-0000-0000-0000-000000031604', 'issue', '00000000-0000-0000-0000-000000031604', '/issues/00000000-0000-0000-0000-000000031604', 'colla://issue/00000000-0000-0000-0000-000000031604', '{"title":"M31P2-1 移动端验证码按钮卡死","status":"closed"}'),
        ('00000000-0000-0000-0000-000000031883', '00000000-0000-0000-0000-000000031821', '/bases/00000000-0000-0000-0000-000000031501/tables/00000000-0000-0000-0000-000000031502/records/00000000-0000-0000-0000-000000031521', 'base_record', '00000000-0000-0000-0000-000000031521', '/bases/00000000-0000-0000-0000-000000031501/tables/00000000-0000-0000-0000-000000031502/records/00000000-0000-0000-0000-000000031521', 'colla://base_record/00000000-0000-0000-0000-000000031521', '{"title":"北区漏斗看板","status":"进行中"}'),
        ('00000000-0000-0000-0000-000000031884', '00000000-0000-0000-0000-000000031831', '/approvals/00000000-0000-0000-0000-000000031701', 'approval', '00000000-0000-0000-0000-000000031701', '/approvals/00000000-0000-0000-0000-000000031701', 'colla://approval/00000000-0000-0000-0000-000000031701', '{"title":"M31 P4 生产发布权限申请","status":"approved"}'),
        ('00000000-0000-0000-0000-000000031885', '00000000-0000-0000-0000-000000031841', '/issues/00000000-0000-0000-0000-000000031612', 'issue', '00000000-0000-0000-0000-000000031612', '/issues/00000000-0000-0000-0000-000000031612', 'colla://issue/00000000-0000-0000-0000-000000031612', '{"title":"M31P5-1 IM 长会话置顶与撤回回归","status":"resolved"}')
) as v(id, message_id, source_url, target_type, target_id, web_path, deep_link, card_snapshot) on true
join messages m on m.id = v.message_id::uuid
on conflict (id)
do update set source_url = excluded.source_url, target_type = excluded.target_type, target_id = excluded.target_id, web_path = excluded.web_path, deep_link = excluded.deep_link, card_snapshot = excluded.card_snapshot;

with reaction_rows(message_id, username, emoji) as (
    values
        ('00000000-0000-0000-0000-000000031801', 'pm_chen', '👍'),
        ('00000000-0000-0000-0000-000000031811', 'backend_wang', '😮'),
        ('00000000-0000-0000-0000-000000031821', 'business_he', '🎉'),
        ('00000000-0000-0000-0000-000000031841', 'qa_sun', '✅'),
        ('00000000-0000-0000-0000-000000031844', 'pm_chen', '👍')
),
numbered as (
    select row_number() over(order by message_id, username, emoji) rn, *
    from reaction_rows
)
insert into message_reactions (id, workspace_id, conversation_id, message_id, user_id, emoji, created_at)
select
    ('00000000-0000-0000-0000-' || lpad((318900 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    m.conversation_id,
    m.id,
    u.id,
    n.emoji,
    m.created_at + interval '1 minute'
from numbered n
join m31_context c on true
join messages m on m.id = n.message_id::uuid
join m31_users u on u.username = n.username
on conflict (id)
do update set message_id = excluded.message_id, user_id = excluded.user_id, emoji = excluded.emoji, created_at = excluded.created_at;

with object_rows(object_type, object_id, web_path, deep_link, title_snapshot, offset_minutes) as (
    values
        ('document', '00000000-0000-0000-0000-000000031402', '/docs/00000000-0000-0000-0000-000000031402', 'colla://document/00000000-0000-0000-0000-000000031402', 'M31 P1 客户门户 PRD', 400),
        ('document', '00000000-0000-0000-0000-000000031403', '/docs/00000000-0000-0000-0000-000000031403', 'colla://document/00000000-0000-0000-0000-000000031403', 'M31 P2 登录 BUG 分诊记录', 401),
        ('document', '00000000-0000-0000-0000-000000031404', '/docs/00000000-0000-0000-0000-000000031404', 'colla://document/00000000-0000-0000-0000-000000031404', 'M31 P3 数据看板口径说明', 402),
        ('issue', '00000000-0000-0000-0000-000000031604', '/issues/00000000-0000-0000-0000-000000031604', 'colla://issue/00000000-0000-0000-0000-000000031604', 'M31P2-1 移动端验证码按钮卡死', 403),
        ('issue', '00000000-0000-0000-0000-000000031612', '/issues/00000000-0000-0000-0000-000000031612', 'colla://issue/00000000-0000-0000-0000-000000031612', 'M31P5-1 IM 长会话置顶与撤回回归', 404),
        ('base', '00000000-0000-0000-0000-000000031501', '/bases/00000000-0000-0000-0000-000000031501', 'colla://base/00000000-0000-0000-0000-000000031501', 'M31 数据看板 Base', 405),
        ('base_table', '00000000-0000-0000-0000-000000031502', '/bases/00000000-0000-0000-0000-000000031501/tables/00000000-0000-0000-0000-000000031502', 'colla://base_table/00000000-0000-0000-0000-000000031502', '看板需求池', 406),
        ('base_record', '00000000-0000-0000-0000-000000031521', '/bases/00000000-0000-0000-0000-000000031501/tables/00000000-0000-0000-0000-000000031502/records/00000000-0000-0000-0000-000000031521', 'colla://base_record/00000000-0000-0000-0000-000000031521', '北区漏斗看板', 407),
        ('message', '00000000-0000-0000-0000-000000031841', '/im?conversationId=00000000-0000-0000-0000-000000031205&messageId=00000000-0000-0000-0000-000000031841', 'colla://message/00000000-0000-0000-0000-000000031841', 'M31 IM 稳定性专项消息', 408),
        ('approval', '00000000-0000-0000-0000-000000031701', '/approvals/00000000-0000-0000-0000-000000031701', 'colla://approval/00000000-0000-0000-0000-000000031701', 'M31 P4 生产发布权限申请', 409)
),
numbered as (
    select row_number() over(order by object_type, object_id) rn, *
    from object_rows
)
insert into object_links (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at, deleted_at)
select
    ('00000000-0000-0000-0000-' || lpad((319100 + rn)::text, 12, '0'))::uuid,
    c.workspace_id,
    object_type,
    object_id::uuid,
    web_path,
    deep_link,
    title_snapshot,
    c.base_at + (offset_minutes || ' minutes')::interval,
    c.base_at + (offset_minutes || ' minutes')::interval,
    null
from numbered
join m31_context c on true
on conflict (workspace_id, object_type, object_id)
do update set web_path = excluded.web_path, deep_link = excluded.deep_link, title_snapshot = excluded.title_snapshot, updated_at = excluded.updated_at, deleted_at = null;

insert into notifications (
    id, workspace_id, recipient_id, actor_id, notification_type, title, body,
    target_type, target_id, web_path, dedupe_key, read_at, created_at
)
select
    v.id::uuid,
    c.workspace_id,
    recipient.id,
    actor.id,
    v.notification_type,
    v.title,
    v.body,
    v.target_type,
    v.target_id::uuid,
    v.web_path,
    v.dedupe_key,
    null,
    c.base_at + (v.offset_minutes || ' minutes')::interval
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031901', 'frontend_zhao', 'pm_chen', 'issue.assigned', 'M31P1-2 已分配给你', '门户首页组件拆分。', 'issue', '00000000-0000-0000-0000-000000031602', '/issues/00000000-0000-0000-0000-000000031602', 'm31:p1:task-assigned', 420),
        ('00000000-0000-0000-0000-000000031902', 'backend_wang', 'qa_sun', 'bug.verification_failed', 'M31P2-1 验证失败', '验证码按钮仍卡住，需要二次修复。', 'issue', '00000000-0000-0000-0000-000000031604', '/issues/00000000-0000-0000-0000-000000031604', 'm31:p2:verification-failed', 421),
        ('00000000-0000-0000-0000-000000031903', 'qa_sun', 'backend_wang', 'bug.fix_ready', 'M31P2-1 可复测', '二次修复已发布。', 'issue', '00000000-0000-0000-0000-000000031604', '/issues/00000000-0000-0000-0000-000000031604', 'm31:p2:fix-ready', 422),
        ('00000000-0000-0000-0000-000000031904', 'business_he', 'product_lin', 'document.updated', 'P3 口径文档已更新', '补充北区漏斗看板口径。', 'document', '00000000-0000-0000-0000-000000031404', '/docs/00000000-0000-0000-0000-000000031404', 'm31:p3:doc-updated', 423),
        ('00000000-0000-0000-0000-000000031905', 'business_he', 'ops_liu', 'release.delayed', 'P4 发布延期已同步', '延期通知已记录。', 'issue', '00000000-0000-0000-0000-000000031611', '/issues/00000000-0000-0000-0000-000000031611', 'm31:p4:release-delayed', 424),
        ('00000000-0000-0000-0000-000000031906', 'pm_chen', 'ops_liu', 'approval.approved', 'P4 发布审批通过', '审批已通过，可以发布。', 'approval', '00000000-0000-0000-0000-000000031701', '/approvals/00000000-0000-0000-0000-000000031701', 'm31:p4:approval-approved', 425),
        ('00000000-0000-0000-0000-000000031907', 'qa_sun', 'pm_chen', 'message.pinned', 'P5 置顶消息已设置', 'IM 稳定性专项置顶消息。', 'message', '00000000-0000-0000-0000-000000031841', '/im?conversationId=00000000-0000-0000-0000-000000031205&messageId=00000000-0000-0000-0000-000000031841', 'm31:p5:message-pinned', 426),
        ('00000000-0000-0000-0000-000000031908', 'viewer_tan', 'admin', 'permission.denied_baseline', 'P3 Base 无权限基线', 'viewer_tan 不应看到 P3 Base。', 'base', '00000000-0000-0000-0000-000000031501', '/bases/00000000-0000-0000-0000-000000031501', 'm31:p3:viewer-denied', 427)
) as v(id, recipient_username, actor_username, notification_type, title, body, target_type, target_id, web_path, dedupe_key, offset_minutes) on true
join m31_users recipient on recipient.username = v.recipient_username
left join m31_users actor on actor.username = v.actor_username
on conflict (id)
do update set title = excluded.title, body = excluded.body, target_type = excluded.target_type, target_id = excluded.target_id, web_path = excluded.web_path, dedupe_key = excluded.dedupe_key, created_at = excluded.created_at;

insert into audit_logs (id, workspace_id, actor_id, action, target_type, target_id, ip_address, user_agent, metadata, created_at)
select
    v.id::uuid,
    c.workspace_id,
    coalesce(actor.id, c.admin_id),
    v.action,
    v.target_type,
    v.target_id::uuid,
    '127.0.0.1',
    'm31-simulation',
    v.metadata::jsonb,
    c.base_at + (v.offset_minutes || ' minutes')::interval
from m31_context c
join (
    values
        ('00000000-0000-0000-0000-000000031921', 'pm_chen', 'issue.transition', 'issue', '00000000-0000-0000-0000-000000031601', '{"branch":"requirement.confirmed"}', 430),
        ('00000000-0000-0000-0000-000000031922', 'qa_sun', 'bug.verification.failed', 'issue', '00000000-0000-0000-0000-000000031604', '{"branch":"bug.failed"}', 431),
        ('00000000-0000-0000-0000-000000031923', 'qa_sun', 'bug.verification.passed', 'issue', '00000000-0000-0000-0000-000000031604', '{"branch":"bug.passed"}', 432),
        ('00000000-0000-0000-0000-000000031924', 'product_lin', 'document.version.created', 'document', '00000000-0000-0000-0000-000000031404', '{"version":2}', 433),
        ('00000000-0000-0000-0000-000000031925', 'backend_wang', 'base.record.updated', 'base_record', '00000000-0000-0000-0000-000000031521', '{"field":"metric"}', 434),
        ('00000000-0000-0000-0000-000000031926', 'ops_liu', 'approval.submitted', 'approval', '00000000-0000-0000-0000-000000031701', '{"formKey":"permission_request"}', 435),
        ('00000000-0000-0000-0000-000000031927', 'admin', 'approval.approved', 'approval', '00000000-0000-0000-0000-000000031701', '{"taskId":"00000000-0000-0000-0000-000000031711"}', 436),
        ('00000000-0000-0000-0000-000000031928', 'pm_chen', 'message.pinned', 'message', '00000000-0000-0000-0000-000000031841', '{"conversationId":"00000000-0000-0000-0000-000000031205"}', 437),
        ('00000000-0000-0000-0000-000000031929', 'backend_wang', 'message.revoked', 'message', '00000000-0000-0000-0000-000000031843', '{"conversationId":"00000000-0000-0000-0000-000000031205"}', 438),
        ('00000000-0000-0000-0000-000000031930', 'viewer_tan', 'permission.denied', 'base', '00000000-0000-0000-0000-000000031501', '{"expected":true}', 439)
) as v(id, actor_username, action, target_type, target_id, metadata, offset_minutes) on true
left join m31_users actor on actor.username = v.actor_username
on conflict (id)
do update set action = excluded.action, target_type = excluded.target_type, target_id = excluded.target_id, metadata = excluded.metadata, created_at = excluded.created_at;

insert into search_index_entries
    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
select i.workspace_id,
       'issue',
       i.id,
       i.issue_key || ' ' || i.title,
       left(coalesce(i.description, i.title), 240),
       '/issues/' || i.id::text,
       'colla://issue/' || i.id::text,
       coalesce(i.issue_key, '') || ' ' || coalesce(i.title, '') || ' ' || coalesce(i.description, ''),
       i.updated_at,
       now()
from issues i
join projects p on p.id = i.project_id
where p.project_key like 'M31P%' and i.deleted_at is null
on conflict (workspace_id, object_type, object_id)
do update set title = excluded.title, excerpt = excluded.excerpt, web_path = excluded.web_path, deep_link = excluded.deep_link, search_text = excluded.search_text, updated_at = excluded.updated_at, indexed_at = now();

insert into search_index_entries
    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
select d.workspace_id,
       'knowledge_content',
       d.id,
       d.title,
       left(d.title, 240),
       '/knowledge-content/' || d.id::text,
       'colla://knowledge-content/' || d.id::text,
       coalesce(d.title, ''),
       d.updated_at,
       now()
from knowledge_base_items d
where d.id between '00000000-0000-0000-0000-000000031401'::uuid and '00000000-0000-0000-0000-000000031406'::uuid
  and d.deleted_at is null
on conflict (workspace_id, object_type, object_id)
do update set title = excluded.title, excerpt = excluded.excerpt, web_path = excluded.web_path, deep_link = excluded.deep_link, search_text = excluded.search_text, updated_at = excluded.updated_at, indexed_at = now();

insert into search_index_entries
    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
select br.workspace_id,
       'base_record',
       br.id,
       coalesce(max(case when bf.id = bt.primary_field_id then brv.value_text end), '记录 #' || br.record_no::text),
       left(string_agg(coalesce(brv.value_text, ''), ' ' order by bf.sort_order), 240),
       '/bases/' || b.id::text || '/tables/' || bt.id::text || '/records/' || br.id::text,
       'colla://base_record/' || br.id::text,
       string_agg(coalesce(brv.value_text, ''), ' ' order by bf.sort_order),
       br.updated_at,
       now()
from base_records br
join base_tables bt on bt.id = br.table_id and bt.archived_at is null
join bases b on b.id = bt.base_id and b.archived_at is null
join base_record_values brv on brv.record_id = br.id
join base_fields bf on bf.id = brv.field_id and bf.archived_at is null
where b.id = '00000000-0000-0000-0000-000000031501'::uuid
  and br.deleted_at is null
group by br.workspace_id, br.id, br.record_no, b.id, bt.id, br.updated_at
on conflict (workspace_id, object_type, object_id)
do update set title = excluded.title, excerpt = excluded.excerpt, web_path = excluded.web_path, deep_link = excluded.deep_link, search_text = excluded.search_text, updated_at = excluded.updated_at, indexed_at = now();

insert into search_index_entries
    (workspace_id, object_type, object_id, title, excerpt, web_path, deep_link, search_text, updated_at, indexed_at)
select m.workspace_id,
       'message',
       m.id,
       coalesce(c.title, '会话消息'),
       left(m.content, 240),
       '/im?conversationId=' || c.id::text || '&messageId=' || m.id::text,
       'colla://message/' || m.id::text,
       coalesce(c.title, '') || ' ' || coalesce(m.content, ''),
       m.created_at,
       now()
from messages m
join conversations c on c.id = m.conversation_id and c.archived_at is null
where m.client_message_id like 'm31-%'
  and m.deleted_at is null
  and m.revoked_at is null
  and coalesce(m.content, '') <> ''
on conflict (workspace_id, object_type, object_id)
do update set title = excluded.title, excerpt = excluded.excerpt, web_path = excluded.web_path, deep_link = excluded.deep_link, search_text = excluded.search_text, updated_at = excluded.updated_at, indexed_at = now();

commit;
'@

$verifySql = @'
create temp table m31_context as
select w.id as workspace_id, u.id as admin_id
from workspaces w
join users u on u.workspace_id = w.id and u.username = 'admin'
where w.slug = 'default'
limit 1;

create temp table m31_failures (message text);

insert into m31_failures
select 'M31 must have fixed 10 active role users'
where (
    select count(*)
    from users u, m31_context c
    where u.workspace_id = c.workspace_id
      and u.status = 'active'
      and u.deleted_at is null
      and u.username in (
        'admin', 'pm_chen', 'product_lin', 'design_wu', 'frontend_zhao',
        'backend_wang', 'qa_sun', 'ops_liu', 'business_he', 'viewer_tan'
      )
) <> 10;

insert into m31_failures
select 'M31 must have exactly 5 active projects'
where (
    select count(*)
    from projects p, m31_context c
    where p.workspace_id = c.workspace_id
      and p.project_key like 'M31P%'
      and p.archived_at is null
) <> 5;

insert into m31_failures
select 'Every M31 project must have a project conversation'
where exists (
    select 1
    from projects p, m31_context c
    where p.workspace_id = c.workspace_id
      and p.project_key like 'M31P%'
      and p.conversation_id is null
);

insert into m31_failures
select 'Every M31 project must have at least 6 members except P2/P3 with explicit permission boundary'
where exists (
    select 1
    from projects p, m31_context c
    where p.workspace_id = c.workspace_id
      and p.project_key like 'M31P%'
      and (
          select count(*)
          from project_members pm
          where pm.project_id = p.id and pm.archived_at is null
      ) < 6
);

insert into m31_failures
select 'P2 valid bug must include failed and passed verification and be closed'
where not exists (
    select 1
    from issues i
    join issue_verification_logs vf on vf.issue_id = i.id and vf.result = 'failed'
    join issue_verification_logs vp on vp.issue_id = i.id and vp.result = 'passed'
    where i.issue_key = 'M31P2-1'
      and i.status = 'closed'
      and i.workflow_reason = 'verified'
      and i.resolution = 'verified'
);

insert into m31_failures
select 'P2 insufficient-info bug must be blocked and remain open'
where not exists (
    select 1
    from issues i
    join issue_verification_logs v on v.issue_id = i.id and v.result = 'blocked'
    where i.issue_key = 'M31P2-2'
      and i.status = 'open'
      and i.workflow_reason = 'info_required'
);

insert into m31_failures
select 'Duplicate bug must relate to the original bug'
where not exists (
    select 1
    from issues duplicate_issue
    join issue_relations r on r.issue_id = duplicate_issue.id and r.target_type = 'issue'
    join issues original_issue on original_issue.id = r.target_id
    where duplicate_issue.issue_key = 'M31P2-3'
      and original_issue.issue_key = 'M31P2-1'
      and duplicate_issue.status = 'closed'
      and duplicate_issue.resolution = 'duplicate'
);

insert into m31_failures
select 'Requirement change branch must have document v2 and Base relation'
where not exists (
    select 1
    from issues i
    join issue_relations r on r.issue_id = i.id and r.target_type = 'base_record'
    join knowledge_base_items d on d.id = '00000000-0000-0000-0000-000000031404'::uuid and d.current_version_no = 2
    where i.issue_key = 'M31P3-1' and i.status = 'in_progress'
);

insert into m31_failures
select 'Requirement cancellation branch must be closed with activity'
where not exists (
    select 1
    from issues i
    join issue_activity_logs a on a.issue_id = i.id and a.action = 'requirement.canceled'
    where i.issue_key = 'M31P1-4'
      and i.status = 'closed'
      and i.resolution = 'canceled'
);

insert into m31_failures
select 'M31 document workspace must have one root and five child knowledge_base_items'
where (
    select count(*)
    from knowledge_base_items d
    where d.parent_id = '00000000-0000-0000-0000-000000031401'::uuid
      and d.deleted_at is null
) <> 5;

insert into m31_failures
select 'M31 P3 document must include M33 table, Base view, issue and message embed blocks'
where not exists (
    select 1
    from knowledge_content_blocks b
    where b.item_id = '00000000-0000-0000-0000-000000031404'::uuid
      and b.deleted_at is null
    group by b.item_id
    having count(*) filter (where b.block_type = 'table') >= 1
       and count(*) filter (where b.block_type = 'base_view') >= 1
       and count(*) filter (where b.block_type = 'issue_embed') >= 1
       and count(*) filter (where b.block_type = 'message_embed') >= 1
);

insert into m31_failures
select 'M31 P3 Base must have 1 table, 6 fields, 4 records and 3 views'
where not exists (
    select 1
    from bases b
    where b.id = '00000000-0000-0000-0000-000000031501'::uuid
      and (select count(*) from base_tables bt where bt.base_id = b.id and bt.archived_at is null) = 1
      and (select count(*) from base_fields bf join base_tables bt on bt.id = bf.table_id where bt.base_id = b.id and bf.archived_at is null) = 6
      and (select count(*) from base_records br join base_tables bt on bt.id = br.table_id where bt.base_id = b.id and br.deleted_at is null) = 4
      and (select count(*) from base_views bv join base_tables bt on bt.id = bv.table_id where bt.base_id = b.id and bv.archived_at is null) = 3
);

insert into m31_failures
select 'viewer_tan must not be a P2 project member and must not have P3 Base permission'
where exists (
    select 1
    from m31_context c
    join users viewer on viewer.workspace_id = c.workspace_id and viewer.username = 'viewer_tan'
    left join projects p2 on p2.workspace_id = c.workspace_id and p2.project_key = 'M31P2'
    left join project_members pm on pm.project_id = p2.id and pm.user_id = viewer.id and pm.archived_at is null
    left join base_members bm on bm.base_id = '00000000-0000-0000-0000-000000031501'::uuid and bm.user_id = viewer.id and bm.revoked_at is null
    where pm.id is not null or bm.id is not null
);

insert into m31_failures
select 'M31 must have issue relations to document, issue, base_record and approval'
where (
    select count(distinct target_type)
    from issue_relations r
    join issues i on i.id = r.issue_id
    where i.issue_key like 'M31P%'
      and r.deleted_at is null
      and r.target_type in ('document', 'issue', 'base_record', 'approval')
) <> 4;

insert into m31_failures
select 'M31 must have message pins, mentions, links, reactions and one revoked message'
where not exists (
    select 1
    where
      (select count(*) from messages where client_message_id like 'm31-%' and pinned_at is not null) >= 5
      and (select count(*) from message_mentions mm join messages m on m.id = mm.message_id where m.client_message_id like 'm31-%') >= 3
      and (select count(*) from message_links ml join messages m on m.id = ml.message_id where m.client_message_id like 'm31-%') >= 5
      and (select count(*) from message_reactions mr join messages m on m.id = mr.message_id where m.client_message_id like 'm31-%') >= 5
      and (select count(*) from messages where client_message_id like 'm31-%' and revoked_at is not null) = 1
);

insert into m31_failures
select 'M31 approval branch must be approved with task and action log'
where not exists (
    select 1
    from approval_instances ai
    join approval_tasks t on t.instance_id = ai.id and t.status = 'approved'
    join approval_action_logs l on l.instance_id = ai.id and l.action = 'approved'
    where ai.id = '00000000-0000-0000-0000-000000031701'::uuid and ai.status = 'approved'
);

insert into m31_failures
select 'M31 must have at least 8 notifications and 10 audit logs'
where not exists (
    select 1
    where
      (select count(*) from notifications where dedupe_key like 'm31:%') >= 8
      and (select count(*) from audit_logs where metadata::text like '%m31%' or user_agent = 'm31-simulation') >= 10
);

insert into m31_failures
select 'M31 search index must cover issue, document, base_record and message'
where (
    select count(distinct object_type)
    from search_index_entries
    where (
        title like '%M31%'
        or search_text like '%M31%'
        or object_id in (
            '00000000-0000-0000-0000-000000031521'::uuid,
            '00000000-0000-0000-0000-000000031841'::uuid
        )
    )
      and object_type in ('issue', 'document', 'base_record', 'message')
) <> 4;

do $$
declare
    failures text;
begin
    select string_agg(message, E'\n') into failures from m31_failures;
    if failures is not null then
        raise exception 'M31 verification failed:%', E'\n' || failures;
    end if;
end $$;

select 'M31 verification passed' as result;
'@

$summarySql = @'
\pset tuples_only on
\pset format unaligned
select 'M31 users,' || count(*)
from users
where username in (
    'admin', 'pm_chen', 'product_lin', 'design_wu', 'frontend_zhao',
    'backend_wang', 'qa_sun', 'ops_liu', 'business_he', 'viewer_tan'
)
union all
select 'M31 projects,' || count(*) from projects where project_key like 'M31P%'
union all
select 'M31 issues,' || count(*) from issues where issue_key like 'M31P%'
union all
select 'M31 knowledge_base_items,' || count(*) from knowledge_base_items where id between '00000000-0000-0000-0000-000000031401'::uuid and '00000000-0000-0000-0000-000000031406'::uuid
union all
select 'M31 base records,' || count(*) from base_records where table_id = '00000000-0000-0000-0000-000000031502'::uuid and deleted_at is null
union all
select 'M31 messages,' || count(*) from messages where client_message_id like 'm31-%'
union all
select 'M31 notifications,' || count(*) from notifications where dedupe_key like 'm31:%'
union all
select 'M31 audit logs,' || count(*) from audit_logs where user_agent = 'm31-simulation';
'@

if ($Stage -eq "seed" -or $Stage -eq "all") {
    if (-not $NoBackup) {
        Backup-CollaDatabase
    }
    Write-Host "Resetting database to clean M31 simulation baseline..."
    Invoke-CollaPsql -Sql $resetSql
    Write-Host "Seeding M31 collaboration simulation data..."
    Invoke-CollaPsql -Sql $seedSql
}

if ($Stage -eq "verify" -or $Stage -eq "all") {
    Write-Host "Verifying M31 collaboration simulation data..."
    Invoke-CollaPsql -Sql $verifySql
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$summary = Invoke-CollaPsql -Sql $summarySql -Capture
$reportPath = Join-Path $ReportPath "m31-collab-simulation-$timestamp.md"
$report = @(
    "# M31 Collaboration Simulation Summary",
    "",
    "- Stage: $Stage",
    "- GeneratedAt: $(Get-Date -Format o)",
    "",
    "| Metric | Count |",
    "| --- | --- |"
)
foreach ($line in $summary) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("(")) {
        continue
    }
    $parts = $line.Split(",", 2)
    if ($parts.Length -eq 2) {
        $report += "| $($parts[0]) | $($parts[1]) |"
    }
}
$report | Set-Content -LiteralPath $reportPath -Encoding UTF8

Write-Host ""
Write-Host "M31 collaboration simulation completed."
Write-Host "Local summary: $reportPath"
Write-Host "Accounts:"
Write-Host "  admin / admin123456"
Write-Host "  pm_chen / member123456"
Write-Host "  product_lin / member123456"
Write-Host "  design_wu / member123456"
Write-Host "  frontend_zhao / member123456"
Write-Host "  backend_wang / member123456"
Write-Host "  qa_sun / member123456"
Write-Host "  ops_liu / member123456"
Write-Host "  business_he / member123456"
Write-Host "  viewer_tan / member123456"
