param(
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

$containers = & docker ps --format "{{.Names}}"
if ($LASTEXITCODE -ne 0) {
    throw "Docker is not available. Start Docker Desktop and retry."
}
if ($containers -notcontains $Container) {
    throw "Postgres container '$Container' is not running. Run 'docker compose up -d postgres' first."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $NoBackup) {
    $backupPath = Join-Path $ReportPath "pre-m12-cleanup-$timestamp.sql"
    Write-Host "Creating backup: $backupPath"
    $dumpArgs = @("exec", $Container, "pg_dump", "-U", $DbUser, "-d", $Database, "--no-owner", "--no-privileges")
    $dump = & docker @dumpArgs
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed with code $LASTEXITCODE"
    }
    $dump | Set-Content -LiteralPath $backupPath -Encoding UTF8
}

$sql = @'
begin;

create temp table m12_context as
select
    w.id as workspace_id,
    u.id as admin_id
from workspaces w
join users u on u.workspace_id = w.id and u.username = 'admin'
where w.slug = 'default'
limit 1;

do $$
begin
    if not exists (select 1 from m12_context) then
        raise exception 'Default workspace and admin user are required before M12 reset.';
    end if;
end $$;

truncate table
    approval_action_logs,
    approval_tasks,
    approval_instances,
    audit_logs,
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
where workspace_id in (select workspace_id from m12_context)
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
from m12_context c
where u.id = c.admin_id;

insert into users (
    id,
    workspace_id,
    username,
    password_hash,
    display_name,
    avatar_file_id,
    email,
    phone,
    department,
    status,
    last_login_at,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted_at
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
    now(),
    c.admin_id,
    now(),
    null
from m12_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000001201', 'm12_alice', 'M12 Alice', 'm12_alice@colla.local', 'Product'),
        ('00000000-0000-0000-0000-000000001202', 'm12_bob', 'M12 Bob', 'm12_bob@colla.local', 'Engineering')
) as v(id, username, display_name, email, department);

insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at)
select '00000000-0000-0000-0000-000000001300'::uuid, c.workspace_id, c.admin_id, r.id, c.admin_id, now()
from m12_context c
join roles r on r.workspace_id = c.workspace_id and r.code = 'admin';

insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at)
select assignment.id::uuid, c.workspace_id, u.id, r.id, c.admin_id, now()
from m12_context c
cross join (
    values
        ('00000000-0000-0000-0000-000000001301', 'm12_alice'),
        ('00000000-0000-0000-0000-000000001302', 'm12_bob')
) as assignment(id, username)
join roles r on r.workspace_id = c.workspace_id and r.code = 'member'
join users u on u.workspace_id = c.workspace_id and u.username = assignment.username;

commit;

select username, display_name, email, status
from users
where username in ('admin', 'm12_alice', 'm12_bob')
order by username;
'@

$psqlArgs = @(
    "exec",
    "-i",
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
    $Database
)

$sql | & docker @psqlArgs
if ($LASTEXITCODE -ne 0) {
    throw "psql reset command failed with code $LASTEXITCODE"
}

$adminCredential = "admin" + "123456"
$memberCredential = "member" + "123456"

Write-Host ""
Write-Host "M12 test data reset completed."
Write-Host "Retained accounts:"
Write-Host "  admin / $adminCredential"
Write-Host "  m12_alice / $memberCredential"
Write-Host "  m12_bob / $memberCredential"
