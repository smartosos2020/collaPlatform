param(
    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,
    [string] $ApiBaseUrl = "",
    [string] $ReportDirectory = ".local-reports",
    [switch] $Apply,
    [string] $ConfirmationText = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "pilot-v2-common.ps1")

function Invoke-PilotApi {
    param(
        [ValidateSet("GET", "POST", "PATCH", "PUT", "DELETE")][string] $Method,
        [string] $Path,
        [object] $Body = $null,
        [hashtable] $Headers = @{}
    )

    $parameters = @{
        Uri = "$script:ApiRoot$Path"
        Method = $Method
        Headers = $Headers
        ContentType = "application/json"
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 12
    }
    try {
        $response = Invoke-RestMethod @parameters
        if ($response -is [System.Array] -and $response.Count -eq 0) { return }
        return $response
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $detail = $_.ErrorDetails.Message }
        throw "$Method $Path failed: $detail"
    }
}

function Get-DepartmentList {
    param([object[]] $Nodes)
    $items = New-Object System.Collections.Generic.List[object]
    foreach ($node in @($Nodes)) {
        if ($null -eq $node) { continue }
        if (@($node.PSObject.Properties.Name) -notcontains "department") { continue }
        $items.Add($node.department) | Out-Null
        $children = if (@($node.PSObject.Properties.Name) -notcontains "children" -or $null -eq $node.children) { @() } else { @($node.children) }
        foreach ($child in @(Get-DepartmentList -Nodes $children)) {
            $items.Add($child) | Out-Null
        }
    }
    return $items.ToArray()
}

function Add-Receipt {
    param([string] $Kind, [string] $Code, [string] $Status, [object] $Id, [string] $Detail)
    $script:Entries.Add([ordered]@{ kind = $Kind; code = $Code; status = $Status; id = $Id; detail = $Detail }) | Out-Null
    Write-Host "- $Status [$Kind] $Code`: $Detail"
}

function Ensure-GroupResourcePermission {
    param(
        [string] $ResourceType,
        [string] $ResourceId,
        [string] $PermissionLevel,
        [object] $Group,
        [hashtable] $Headers
    )
    $permissions = @(Invoke-PilotApi -Method GET -Path "/resource-permissions/$ResourceType/$ResourceId" -Headers $Headers)
    $existing = $permissions | Where-Object {
        $_.subjectType -eq "user_group" -and $_.subjectId -eq $Group.id -and $_.effectiveStatus -eq "active"
    } | Select-Object -First 1
    if ($null -ne $existing) {
        if ([string] $existing.permissionLevel -ne $PermissionLevel) {
            throw "Existing $ResourceType permission for group '$($Group.code)' is '$($existing.permissionLevel)', expected '$PermissionLevel'"
        }
        Add-Receipt -Kind "$ResourceType-permission" -Code $Group.code -Status "VERIFIED" -Id $existing.id -Detail "group has $PermissionLevel permission"
        return
    }
    $created = Invoke-PilotApi -Method POST -Path "/resource-permissions/$ResourceType/$ResourceId" -Headers $Headers -Body @{
        subjectType = "user_group"
        subjectId = $Group.id
        permissionLevel = $PermissionLevel
        confirmHighRisk = $true
    }
    Add-Receipt -Kind "$ResourceType-permission" -Code $Group.code -Status "CREATED" -Id $created.id -Detail "granted $PermissionLevel to participant group"
}

$resolvedManifest = if ([System.IO.Path]::IsPathRooted($ManifestPath)) { $ManifestPath } else { Join-Path $Root $ManifestPath }
$manifest = Read-PilotManifest -ManifestPath $resolvedManifest
$validation = Test-PilotManifest -Manifest $manifest -Level "initialization"
if (-not $validation.valid) {
    throw "Pilot manifest is not initialization-ready: $($validation.errors -join '; ')"
}
if ($Apply -and $ConfirmationText -cne "INITIALIZE:$($manifest.pilotId):$($manifest.environment.projectName)") {
    throw "Confirmation text must exactly match INITIALIZE:$($manifest.pilotId):$($manifest.environment.projectName)"
}
$adminUsername = [string] $env:COLLA_PILOT_ADMIN_USERNAME
$adminPassword = [string] $env:COLLA_PILOT_ADMIN_PASSWORD
if ([string]::IsNullOrWhiteSpace($adminUsername) -or [string]::IsNullOrWhiteSpace($adminPassword)) {
    throw "COLLA_PILOT_ADMIN_USERNAME and COLLA_PILOT_ADMIN_PASSWORD are required"
}
$initialPassword = [string] $env:COLLA_PILOT_INITIAL_PASSWORD
if ($Apply -and -not [string]::IsNullOrWhiteSpace($initialPassword) -and $initialPassword.Length -lt 12) {
    throw "COLLA_PILOT_INITIAL_PASSWORD must contain at least 12 characters"
}

$targetBase = if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) { [string] $manifest.environment.baseUrl } else { $ApiBaseUrl }
$script:ApiRoot = $targetBase.TrimEnd("/")
if (-not $script:ApiRoot.EndsWith("/api")) { $script:ApiRoot += "/api" }
$reportRoot = if ([System.IO.Path]::IsPathRooted($ReportDirectory)) { $ReportDirectory } else { Join-Path $Root $ReportDirectory }
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$script:Entries = New-Object System.Collections.Generic.List[object]
$startedAt = Get-Date
$decision = "FAIL"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$receiptPath = Join-Path $reportRoot "pilot-v2-initialize-$timestamp.json"
$reportPath = Join-Path $reportRoot "pilot-v2-initialize-$timestamp.md"

try {
    $session = Invoke-PilotApi -Method POST -Path "/auth/login" -Body @{
        username = $adminUsername
        password = $adminPassword
        deviceType = "web"
        deviceFingerprint = "pilot-v2-initializer-$($manifest.pilotId)"
        deviceName = "PILOT-V2 initializer"
        appVersion = "pilot-v2"
    }
    $headers = @{ Authorization = "Bearer $($session.accessToken)" }
    Add-Receipt -Kind "authentication" -Code $adminUsername -Status "VERIFIED" -Id $null -Detail "admin API session established"

    $departmentTree = @(Invoke-PilotApi -Method GET -Path "/admin/departments/tree" -Headers $headers)
    $departments = @(Get-DepartmentList -Nodes $departmentTree)
    $departmentByCode = @{}
    foreach ($department in $departments) { $departmentByCode[[string] $department.code] = $department }
    $pendingDepartments = New-Object System.Collections.Generic.List[object]
    foreach ($department in @($manifest.organization.departments)) { $pendingDepartments.Add($department) | Out-Null }
    while ($pendingDepartments.Count -gt 0) {
        $progress = $false
        foreach ($department in @($pendingDepartments.ToArray())) {
            $code = [string] $department.code
            if ($departmentByCode.ContainsKey($code)) {
                Add-Receipt -Kind "department" -Code $code -Status "VERIFIED" -Id $departmentByCode[$code].id -Detail "existing department"
                $pendingDepartments.Remove($department) | Out-Null
                $progress = $true
                continue
            }
            $parentCode = [string] $department.parentCode
            if (-not [string]::IsNullOrWhiteSpace($parentCode) -and -not $departmentByCode.ContainsKey($parentCode)) {
                if (-not $Apply) {
                    Add-Receipt -Kind "department" -Code $code -Status "PLANNED" -Id $null -Detail "create after parent '$parentCode'"
                    $pendingDepartments.Remove($department) | Out-Null
                    $progress = $true
                }
                continue
            }
            if (-not $Apply) {
                Add-Receipt -Kind "department" -Code $code -Status "PLANNED" -Id $null -Detail "create department"
            } else {
                $created = Invoke-PilotApi -Method POST -Path "/admin/departments" -Headers $headers -Body @{
                    parentId = if ([string]::IsNullOrWhiteSpace($parentCode)) { $null } else { $departmentByCode[$parentCode].id }
                    code = $code
                    name = [string] $department.name
                    sortOrder = [int] $department.sortOrder
                }
                $departmentByCode[$code] = $created
                Add-Receipt -Kind "department" -Code $code -Status "CREATED" -Id $created.id -Detail ([string] $department.name)
            }
            $pendingDepartments.Remove($department) | Out-Null
            $progress = $true
        }
        if (-not $progress) { throw "Department hierarchy could not be resolved" }
    }

    $users = @(Invoke-PilotApi -Method GET -Path "/admin/users" -Headers $headers)
    $userByUsername = @{}
    foreach ($user in $users) { $userByUsername[[string] $user.username] = $user }
    foreach ($participant in @($manifest.participants)) {
        $username = [string] $participant.username
        if ($userByUsername.ContainsKey($username)) {
            $existing = $userByUsername[$username]
            if ([string] $existing.status -ne "active") { throw "Pilot participant '$username' is not active" }
            if (@($existing.roles) -notcontains [string] $participant.platformRole) {
                throw "Existing participant '$username' does not have required platform role '$($participant.platformRole)'"
            }
            Add-Receipt -Kind "participant" -Code $username -Status "VERIFIED" -Id $existing.id -Detail "existing active account"
            continue
        }
        if (-not $Apply) {
            Add-Receipt -Kind "participant" -Code $username -Status "PLANNED" -Id $null -Detail "create $($participant.platformRole) account"
            continue
        }
        if ([string]::IsNullOrWhiteSpace($initialPassword)) {
            throw "COLLA_PILOT_INITIAL_PASSWORD is required because participant '$username' does not exist"
        }
        $created = Invoke-PilotApi -Method POST -Path "/admin/users" -Headers $headers -Body @{
            username = $username
            password = $initialPassword
            displayName = [string] $participant.displayName
            email = [string] $participant.email
            roleCode = [string] $participant.platformRole
            primaryDepartmentId = $departmentByCode[[string] $participant.departmentCode].id
        }
        $userByUsername[$username] = $created
        Add-Receipt -Kind "participant" -Code $username -Status "CREATED" -Id $created.id -Detail "$($participant.platformRole) account"
    }
    if ($Apply) {
        $users = @(Invoke-PilotApi -Method GET -Path "/admin/users" -Headers $headers)
        $userByUsername = @{}
        foreach ($user in $users) { $userByUsername[[string] $user.username] = $user }
    }

    $groups = @(Invoke-PilotApi -Method GET -Path "/admin/user-groups" -Headers $headers)
    $groupTemplate = $manifest.organization.userGroup
    $group = $groups | Where-Object { $_.code -eq $groupTemplate.code } | Select-Object -First 1
    if ($null -eq $group) {
        if (-not $Apply) {
            Add-Receipt -Kind "user-group" -Code $groupTemplate.code -Status "PLANNED" -Id $null -Detail "create and add all participants"
        } else {
            $group = Invoke-PilotApi -Method POST -Path "/admin/user-groups" -Headers $headers -Body @{
                code = [string] $groupTemplate.code
                name = [string] $groupTemplate.name
                description = [string] $groupTemplate.description
                groupType = [string] $groupTemplate.groupType
            }
            Add-Receipt -Kind "user-group" -Code $group.code -Status "CREATED" -Id $group.id -Detail ([string] $group.name)
        }
    } else {
        Add-Receipt -Kind "user-group" -Code $group.code -Status "VERIFIED" -Id $group.id -Detail "existing group"
    }
    if ($Apply) {
        $groupMembers = @(Invoke-PilotApi -Method GET -Path "/admin/user-groups/$($group.id)/members" -Headers $headers)
        foreach ($participant in @($manifest.participants)) {
            $user = $userByUsername[[string] $participant.username]
            if (@($groupMembers | Where-Object { $_.subjectType -eq "user" -and $_.subjectId -eq $user.id }).Count -eq 0) {
                [void] (Invoke-PilotApi -Method POST -Path "/admin/user-groups/$($group.id)/members" -Headers $headers -Body @{ subjectType = "user"; subjectId = $user.id })
                Add-Receipt -Kind "group-member" -Code $participant.username -Status "CREATED" -Id $user.id -Detail "added to $($group.code)"
            } else {
                Add-Receipt -Kind "group-member" -Code $participant.username -Status "VERIFIED" -Id $user.id -Detail "already in $($group.code)"
            }
        }
    }

    $participantUserIds = @($manifest.participants | ForEach-Object { if ($userByUsername.ContainsKey([string] $_.username)) { $userByUsername[[string] $_.username].id } })
    $projects = @(Invoke-PilotApi -Method GET -Path "/projects" -Headers $headers)
    $projectTemplate = $manifest.templates.project
    $project = $projects | Where-Object { $_.projectKey -eq $projectTemplate.projectKey } | Select-Object -First 1
    if ($null -eq $project) {
        if ($Apply) {
            $project = Invoke-PilotApi -Method POST -Path "/projects" -Headers $headers -Body @{
                projectKey = [string] $projectTemplate.projectKey
                name = [string] $projectTemplate.name
                description = [string] $projectTemplate.description
                memberIds = $participantUserIds
            }
            Add-Receipt -Kind "project" -Code $project.projectKey -Status "CREATED" -Id $project.id -Detail ([string] $project.name)
        } else { Add-Receipt -Kind "project" -Code $projectTemplate.projectKey -Status "PLANNED" -Id $null -Detail "create pilot project" }
    } else { Add-Receipt -Kind "project" -Code $project.projectKey -Status "VERIFIED" -Id $project.id -Detail "existing pilot project" }

    $spaces = @(Invoke-PilotApi -Method GET -Path "/knowledge-bases" -Headers $headers)
    $knowledgeTemplate = $manifest.templates.knowledge
    $space = $spaces | Where-Object { $_.code -eq $knowledgeTemplate.code } | Select-Object -First 1
    if ($null -eq $space) {
        if ($Apply) {
            $spaceDetail = Invoke-PilotApi -Method POST -Path "/knowledge-bases" -Headers $headers -Body @{
                name = [string] $knowledgeTemplate.name
                code = [string] $knowledgeTemplate.code
                description = [string] $knowledgeTemplate.description
                icon = "book"
                coverUrl = $null
                visibility = "private"
                defaultPermissionLevel = "view"
            }
            $space = $spaceDetail.space
            Add-Receipt -Kind "knowledge-space" -Code $space.code -Status "CREATED" -Id $space.id -Detail ([string] $space.name)
        } else { Add-Receipt -Kind "knowledge-space" -Code $knowledgeTemplate.code -Status "PLANNED" -Id $null -Detail "create pilot knowledge space and template" }
    } else { Add-Receipt -Kind "knowledge-space" -Code $space.code -Status "VERIFIED" -Id $space.id -Detail "existing pilot knowledge space" }
    if ($Apply) {
        $templates = @(Invoke-PilotApi -Method GET -Path "/knowledge-bases/$($space.id)/templates" -Headers $headers)
        $knowledgeContentTemplate = $templates | Where-Object { $_.title -eq $knowledgeTemplate.templateTitle } | Select-Object -First 1
        if ($null -eq $knowledgeContentTemplate) {
            $knowledgeContentTemplate = Invoke-PilotApi -Method POST -Path "/knowledge-bases/$($space.id)/templates" -Headers $headers -Body @{
                title = [string] $knowledgeTemplate.templateTitle
                description = [string] $knowledgeTemplate.templateDescription
                category = [string] $knowledgeTemplate.templateCategory
                content = [string] $knowledgeTemplate.templateContent
            }
            Add-Receipt -Kind "knowledge-template" -Code $knowledgeTemplate.templateTitle -Status "CREATED" -Id $knowledgeContentTemplate.id -Detail "daily note template"
        } else { Add-Receipt -Kind "knowledge-template" -Code $knowledgeTemplate.templateTitle -Status "VERIFIED" -Id $knowledgeContentTemplate.id -Detail "existing template" }
        Ensure-GroupResourcePermission -ResourceType "knowledge_base" -ResourceId $space.id -PermissionLevel "edit" -Group $group -Headers $headers
    } else {
        Add-Receipt -Kind "knowledge_base-permission" -Code $groupTemplate.code -Status "PLANNED" -Id $null -Detail "grant edit permission to participant group"
    }

    $bases = @(Invoke-PilotApi -Method GET -Path "/bases" -Headers $headers)
    $baseTemplate = $manifest.templates.base
    $base = $bases | Where-Object { $_.name -eq $baseTemplate.name } | Select-Object -First 1
    if ($null -eq $base) {
        if ($Apply) {
            $baseDetail = Invoke-PilotApi -Method POST -Path "/bases" -Headers $headers -Body @{ name = [string] $baseTemplate.name; description = [string] $baseTemplate.description }
            $base = $baseDetail.base
            Add-Receipt -Kind "base" -Code $baseTemplate.name -Status "CREATED" -Id $base.id -Detail "feedback register"
        } else { Add-Receipt -Kind "base" -Code $baseTemplate.name -Status "PLANNED" -Id $null -Detail "create feedback Base and table" }
    } else { Add-Receipt -Kind "base" -Code $baseTemplate.name -Status "VERIFIED" -Id $base.id -Detail "existing feedback Base" }
    if ($Apply) {
        $baseDetail = Invoke-PilotApi -Method GET -Path "/bases/$($base.id)" -Headers $headers
        $table = @($baseDetail.tables | Where-Object { $_.name -eq $baseTemplate.tableName }) | Select-Object -First 1
        if ($null -eq $table) {
            $table = Invoke-PilotApi -Method POST -Path "/bases/$($base.id)/tables" -Headers $headers -Body @{ name = [string] $baseTemplate.tableName }
            Add-Receipt -Kind "base-table" -Code $baseTemplate.tableName -Status "CREATED" -Id $table.table.id -Detail "pilot observation table"
        } else { Add-Receipt -Kind "base-table" -Code $baseTemplate.tableName -Status "VERIFIED" -Id $table.id -Detail "existing observation table" }
        Ensure-GroupResourcePermission -ResourceType "base" -ResourceId $base.id -PermissionLevel "edit" -Group $group -Headers $headers
        $baseDetail = Invoke-PilotApi -Method GET -Path "/bases/$($base.id)" -Headers $headers
        foreach ($participant in @($manifest.participants)) {
            $user = $userByUsername[[string] $participant.username]
            $existingMember = @($baseDetail.members | Where-Object { $_.userId -eq $user.id }) | Select-Object -First 1
            if ($null -ne $existingMember -and [string] $existingMember.permissionLevel -eq "edit") {
                Add-Receipt -Kind "base-member" -Code $participant.username -Status "VERIFIED" -Id $existingMember.id -Detail "member has edit permission"
                continue
            }
            $baseDetail = Invoke-PilotApi -Method POST -Path "/bases/$($base.id)/members" -Headers $headers -Body @{
                userId = $user.id
                permissionLevel = "edit"
            }
            $grantedMember = @($baseDetail.members | Where-Object { $_.userId -eq $user.id }) | Select-Object -First 1
            Add-Receipt -Kind "base-member" -Code $participant.username -Status "CREATED" -Id $grantedMember.id -Detail "granted edit permission"
        }
    } else {
        Add-Receipt -Kind "base-permission" -Code $groupTemplate.code -Status "PLANNED" -Id $null -Detail "grant edit permission to participant group"
        Add-Receipt -Kind "base-members" -Code $baseTemplate.name -Status "PLANNED" -Id $null -Detail "grant edit permission to every participant"
    }

    $forms = @(Invoke-PilotApi -Method GET -Path "/approvals/forms" -Headers $headers)
    $approvalTemplate = $manifest.templates.approval
    $form = $forms | Where-Object { $_.formKey -eq $approvalTemplate.formKey -and $_.enabled } | Select-Object -First 1
    if ($null -eq $form) { throw "Required approval form '$($approvalTemplate.formKey)' is unavailable" }
    Add-Receipt -Kind "approval-form" -Code $form.formKey -Status "VERIFIED" -Id $form.id -Detail ([string] $form.name)

    $decision = if ($Apply) { "APPLIED" } else { "PLAN-READY" }
} finally {
    $receipt = [ordered]@{
        schemaVersion = 1
        pilotId = [string] $manifest.pilotId
        targetProject = [string] $manifest.environment.projectName
        apiBaseUrl = $script:ApiRoot
        mode = if ($Apply) { "apply" } else { "plan" }
        decision = $decision
        startedAt = $startedAt.ToUniversalTime().ToString("o")
        finishedAt = (Get-Date).ToUniversalTime().ToString("o")
        entries = $script:Entries.ToArray()
    }
    $receipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $receiptPath -Encoding UTF8
    $lines = @(
        "# PILOT-V2 Initialization",
        "",
        "- Pilot: $($manifest.pilotId)",
        "- Target: $($manifest.environment.projectName)",
        "- Mode: $(if ($Apply) { 'apply' } else { 'plan' })",
        "- Decision: $decision",
        "",
        "| Kind | Code | Status | Id | Detail |",
        "| --- | --- | --- | --- | --- |"
    )
    $lines += @($script:Entries.ToArray() | ForEach-Object { "| $($_.kind) | $($_.code) | $($_.status) | $($_.id) | $($_.detail) |" })
    Set-Content -LiteralPath $reportPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Initialization receipt: $receiptPath"
    Write-Host "Initialization report: $reportPath"
}
