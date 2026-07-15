Set-StrictMode -Version Latest

function Test-PilotPlaceholder {
    param([object] $Value)

    if ($null -eq $Value) {
        return $true
    }
    $text = ([string] $Value).Trim()
    return [string]::IsNullOrWhiteSpace($text) -or $text -match '(?i)(replace-with|example\.com|placeholder|\btodo\b)'
}

function Get-PilotPropertyNames {
    param([object] $Value)

    $names = New-Object System.Collections.Generic.List[string]
    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Collections.IDictionary]) {
        foreach ($key in $Value.Keys) {
            $names.Add([string] $key) | Out-Null
            foreach ($nested in @(Get-PilotPropertyNames -Value $Value[$key])) {
                $names.Add($nested) | Out-Null
            }
        }
    } elseif ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string]) {
        foreach ($item in $Value) {
            foreach ($nested in @(Get-PilotPropertyNames -Value $item)) {
                $names.Add($nested) | Out-Null
            }
        }
    } else {
        foreach ($property in $Value.PSObject.Properties) {
            $names.Add($property.Name) | Out-Null
            foreach ($nested in @(Get-PilotPropertyNames -Value $property.Value)) {
                $names.Add($nested) | Out-Null
            }
        }
    }
    return @($names)
}

function Read-PilotManifest {
    param([Parameter(Mandatory = $true)][string] $ManifestPath)

    $resolved = (Resolve-Path -LiteralPath $ManifestPath).Path
    try {
        $manifest = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
    } catch {
        throw "Pilot manifest is not valid JSON: $($_.Exception.Message)"
    }
    $sensitiveNames = @(Get-PilotPropertyNames -Value $manifest | Where-Object { $_ -match '(?i)(password|secret|accessToken|refreshToken|credential)' })
    if ($sensitiveNames.Count -gt 0) {
        throw "Pilot manifest must not contain credentials or secret fields: $($sensitiveNames -join ', ')"
    }
    return $manifest
}

function Get-PilotSourceSnapshot {
    param([Parameter(Mandatory = $true)][string] $Root)

    $resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
    $scopes = @("server", "web", "deploy", "scripts", "package.json", "pnpm-lock.yaml")
    $tracked = @(& git -C $resolvedRoot ls-files -- @scopes)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to enumerate tracked pilot source files"
    }
    $untracked = @(& git -C $resolvedRoot ls-files --others --exclude-standard -- @scopes)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to enumerate untracked pilot source files"
    }

    $entries = New-Object System.Collections.Generic.List[string]
    foreach ($relativePath in @($tracked + $untracked | Sort-Object -Unique)) {
        $fullPath = Join-Path $resolvedRoot $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            continue
        }
        $normalizedPath = $relativePath.Replace("\", "/")
        $stream = [System.IO.File]::OpenRead($fullPath)
        $fileSha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $fileHash = ([System.BitConverter]::ToString($fileSha256.ComputeHash($stream))).Replace("-", "").ToLowerInvariant()
        } finally {
            $fileSha256.Dispose()
            $stream.Dispose()
        }
        $entries.Add("$normalizedPath`t$fileHash") | Out-Null
    }
    if ($entries.Count -eq 0) {
        throw "Pilot source snapshot contains no files"
    }

    $payload = [System.Text.Encoding]::UTF8.GetBytes(($entries.ToArray() -join "`n"))
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha256.ComputeHash($payload))).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Test-PilotDate {
    param([object] $Value)

    if (Test-PilotPlaceholder -Value $Value) {
        return $false
    }
    try {
        [void] [datetimeoffset]::Parse([string] $Value)
        return $true
    } catch {
        return $false
    }
}

function Test-PilotManifest {
    param(
        [Parameter(Mandatory = $true)] $Manifest,
        [ValidateSet("structural", "initialization", "freeze", "simulation-freeze")]
        [string] $Level = "structural"
    )

    $errors = New-Object System.Collections.Generic.List[string]
    $warnings = New-Object System.Collections.Generic.List[string]
    $requiredRoot = @("schemaVersion", "pilotId", "mode", "environment", "schedule", "participants", "organization", "templates", "scenarios", "dataPolicy", "issuePolicy", "metrics", "kickoffApproval")
    foreach ($name in $requiredRoot) {
        if (-not ($Manifest.PSObject.Properties.Name -contains $name)) {
            $errors.Add("Missing root field: $name") | Out-Null
        }
    }
    if ($errors.Count -gt 0) {
        return [pscustomobject]@{ valid = $false; level = $Level; errors = @($errors); warnings = @($warnings) }
    }

    if ([int] $Manifest.schemaVersion -ne 1) {
        $errors.Add("schemaVersion must be 1") | Out-Null
    }
    if ([string] $Manifest.mode -notin @("real", "rehearsal")) {
        $errors.Add("mode must be real or rehearsal") | Out-Null
    }
    if ($Level -ne "structural" -and (Test-PilotPlaceholder -Value $Manifest.pilotId)) {
        $errors.Add("pilotId must be concrete before initialization") | Out-Null
    }

    foreach ($field in @("projectName", "baseUrl", "dataPrefix", "workspaceLabel")) {
        if (-not ($Manifest.environment.PSObject.Properties.Name -contains $field) -or [string]::IsNullOrWhiteSpace([string] $Manifest.environment.$field)) {
            $errors.Add("environment.$field is required") | Out-Null
        }
    }
    if (-not ([string] $Manifest.environment.dataPrefix).EndsWith("-")) {
        $errors.Add("environment.dataPrefix must end with '-' for unambiguous data labeling") | Out-Null
    }
    if ($Level -ne "structural" -and (Test-PilotPlaceholder -Value $Manifest.environment.baseUrl)) {
        $errors.Add("environment.baseUrl must identify the real or isolated pilot target") | Out-Null
    }

    foreach ($field in @("kickoffAt", "startAt", "endAt")) {
        if (-not (Test-PilotDate -Value $Manifest.schedule.$field)) {
            $errors.Add("schedule.$field must be an ISO-8601 timestamp") | Out-Null
        }
    }
    if ((Test-PilotDate $Manifest.schedule.kickoffAt) -and (Test-PilotDate $Manifest.schedule.startAt) -and
        [datetimeoffset]::Parse([string] $Manifest.schedule.kickoffAt) -gt [datetimeoffset]::Parse([string] $Manifest.schedule.startAt)) {
        $errors.Add("schedule.kickoffAt must not be after startAt") | Out-Null
    }
    if ((Test-PilotDate $Manifest.schedule.startAt) -and (Test-PilotDate $Manifest.schedule.endAt) -and
        [datetimeoffset]::Parse([string] $Manifest.schedule.startAt) -ge [datetimeoffset]::Parse([string] $Manifest.schedule.endAt)) {
        $errors.Add("schedule.endAt must be after startAt") | Out-Null
    }
    foreach ($field in @("dailyWindow", "feedbackChannel")) {
        if (Test-PilotPlaceholder -Value $Manifest.schedule.$field) {
            if ($Level -eq "structural") { $warnings.Add("schedule.$field is still a placeholder") | Out-Null }
            else { $errors.Add("schedule.$field must be concrete") | Out-Null }
        }
    }

    $participants = @($Manifest.participants)
    if ($participants.Count -lt 5 -or $participants.Count -gt 10) {
        $errors.Add("participants must contain 5-10 people") | Out-Null
    }
    foreach ($field in @("participantId", "username", "displayName", "email")) {
        $values = @($participants | ForEach-Object { [string] $_.$field })
        if (@($values | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
            $errors.Add("Every participant requires $field") | Out-Null
        }
        if (@($values | Sort-Object -Unique).Count -ne $values.Count) {
            $errors.Add("Participant $field values must be unique") | Out-Null
        }
    }
    foreach ($participant in $participants) {
        if ([string] $participant.pilotRole -notin @("owner", "admin", "member")) {
            $errors.Add("Participant '$($participant.participantId)' has invalid pilotRole") | Out-Null
        }
        if ([string] $participant.platformRole -notin @("admin", "member")) {
            $errors.Add("Participant '$($participant.participantId)' has invalid platformRole") | Out-Null
        }
        if (@($participant.responsibilities).Count -eq 0 -or [string]::IsNullOrWhiteSpace([string] $participant.availability) -or [string]::IsNullOrWhiteSpace([string] $participant.feedbackChannel)) {
            $errors.Add("Participant '$($participant.participantId)' requires responsibilities, availability, and feedbackChannel") | Out-Null
        } elseif ((Test-PilotPlaceholder $participant.availability) -or (Test-PilotPlaceholder $participant.feedbackChannel)) {
            if ($Level -eq "structural") { $warnings.Add("Participant '$($participant.participantId)' still has placeholder availability or feedbackChannel") | Out-Null }
            else { $errors.Add("Participant '$($participant.participantId)' requires concrete availability and feedbackChannel") | Out-Null }
        }
        if ($Level -ne "structural") {
            foreach ($field in @("username", "displayName", "email")) {
                if (Test-PilotPlaceholder -Value $participant.$field) {
                    $errors.Add("Participant '$($participant.participantId)' has placeholder $field") | Out-Null
                }
            }
            if (-not [bool] $participant.consentConfirmed) {
                $errors.Add("Participant '$($participant.participantId)' has not confirmed participation/data consent") | Out-Null
            }
        }
        if ($Level -eq "freeze" -and [string] $participant.participantKind -ne "human") {
            $errors.Add("Participant '$($participant.participantId)' must be marked human for a real freeze") | Out-Null
        }
        if ($Level -eq "simulation-freeze" -and [string] $participant.participantKind -ne "synthetic") {
            $errors.Add("Participant '$($participant.participantId)' must be marked synthetic for a simulation freeze") | Out-Null
        }
    }
    if (@($participants | Where-Object { $_.pilotRole -eq "owner" }).Count -ne 1) {
        $errors.Add("Exactly one pilot owner is required") | Out-Null
    }
    if (@($participants | Where-Object { $_.pilotRole -eq "admin" }).Count -lt 1) {
        $errors.Add("At least one pilot admin is required") | Out-Null
    }
    if (@($participants | Where-Object { $_.pilotRole -eq "member" }).Count -lt 3) {
        $errors.Add("At least three pilot members are required") | Out-Null
    }

    $departmentCodes = @($Manifest.organization.departments | ForEach-Object { [string] $_.code })
    if ($departmentCodes.Count -eq 0 -or @($departmentCodes | Sort-Object -Unique).Count -ne $departmentCodes.Count) {
        $errors.Add("organization.departments requires unique department codes") | Out-Null
    }
    foreach ($participant in $participants) {
        if ($departmentCodes -notcontains [string] $participant.departmentCode) {
            $errors.Add("Participant '$($participant.participantId)' references unknown department '$($participant.departmentCode)'") | Out-Null
        }
    }
    foreach ($department in @($Manifest.organization.departments)) {
        if ($null -ne $department.parentCode -and -not [string]::IsNullOrWhiteSpace([string] $department.parentCode) -and
            $departmentCodes -notcontains [string] $department.parentCode) {
            $errors.Add("Department '$($department.code)' references unknown parent '$($department.parentCode)'") | Out-Null
        }
    }
    $userGroup = $Manifest.organization.userGroup
    if ($null -eq $userGroup -or (Test-PilotPlaceholder $userGroup.code) -or (Test-PilotPlaceholder $userGroup.name)) {
        $errors.Add("organization.userGroup requires concrete code and name") | Out-Null
    } elseif (@("normal", "permission") -notcontains [string] $userGroup.groupType) {
        $errors.Add("organization.userGroup.groupType must be normal or permission") | Out-Null
    }
    foreach ($templatePath in @(
        @("project", "projectKey"), @("project", "name"), @("project", "description"),
        @("knowledge", "code"), @("knowledge", "name"), @("knowledge", "templateTitle"), @("knowledge", "templateContent"),
        @("base", "name"), @("base", "tableName"), @("approval", "formKey"), @("approval", "formName")
    )) {
        $section = [string] $templatePath[0]
        $field = [string] $templatePath[1]
        if (-not ($Manifest.templates.PSObject.Properties.Name -contains $section) -or
            -not ($Manifest.templates.$section.PSObject.Properties.Name -contains $field) -or
            [string]::IsNullOrWhiteSpace([string] $Manifest.templates.$section.$field)) {
            $errors.Add("templates.$section.$field is required") | Out-Null
        }
    }

    $requiredModules = @("im", "project", "knowledge", "base", "approval", "search")
    $participantIds = @($participants | ForEach-Object { [string] $_.participantId })
    $scenarioIds = @($Manifest.scenarios | ForEach-Object { [string] $_.scenarioId })
    if (@($scenarioIds | Sort-Object -Unique).Count -ne $scenarioIds.Count) {
        $errors.Add("Scenario ids must be unique") | Out-Null
    }
    foreach ($module in $requiredModules) {
        if (@($Manifest.scenarios | Where-Object { $_.module -eq $module }).Count -eq 0) {
            $errors.Add("At least one '$module' scenario is required") | Out-Null
        }
    }
    foreach ($scenario in @($Manifest.scenarios)) {
        if ($participantIds -notcontains [string] $scenario.ownerParticipantId) {
            $errors.Add("Scenario '$($scenario.scenarioId)' references unknown owner") | Out-Null
        }
        if ((Test-PilotPlaceholder $scenario.title) -or (Test-PilotPlaceholder $scenario.successCriteria) -or [int] $scenario.targetMinutes -le 0) {
            $errors.Add("Scenario '$($scenario.scenarioId)' requires title, successCriteria, and positive targetMinutes") | Out-Null
        }
    }

    $forbidden = @($Manifest.dataPolicy.forbiddenCategories)
    foreach ($requiredCategory in @("production credentials", "access tokens", "government identifiers", "payment data", "medical data", "unapproved customer data")) {
        if ($forbidden -notcontains $requiredCategory) {
            $errors.Add("dataPolicy.forbiddenCategories must include '$requiredCategory'") | Out-Null
        }
    }
    if ([int] $Manifest.dataPolicy.retentionDays -lt 1 -or [int] $Manifest.dataPolicy.retentionDays -gt 90) {
        $errors.Add("dataPolicy.retentionDays must be between 1 and 90") | Out-Null
    }
    if (-not [bool] $Manifest.dataPolicy.testDataPrefixRequired) {
        $errors.Add("dataPolicy.testDataPrefixRequired must be true") | Out-Null
    }
    if ($participantIds -notcontains [string] $Manifest.dataPolicy.cleanupOwnerParticipantId) {
        $errors.Add("dataPolicy.cleanupOwnerParticipantId must reference a participant") | Out-Null
    }

    $levels = @($Manifest.issuePolicy.levels)
    if ((@($levels | ForEach-Object { $_.code }) -join ",") -ne "P0,P1,P2,P3") {
        $errors.Add("issuePolicy.levels must define P0,P1,P2,P3 in order") | Out-Null
    }
    $lastResponse = 0
    foreach ($issueLevel in $levels) {
        if ([int] $issueLevel.responseMinutes -lt $lastResponse -or [int] $issueLevel.resolutionHours -le 0) {
            $errors.Add("Issue response targets must become no stricter from P0 to P3 and resolutionHours must be positive") | Out-Null
        }
        $lastResponse = [int] $issueLevel.responseMinutes
    }
    if (@($Manifest.issuePolicy.requiredFields).Count -lt 8 -or @($Manifest.issuePolicy.stopConditions).Count -lt 3) {
        $errors.Add("issuePolicy requires a complete issue template and at least three stop conditions") | Out-Null
    }

    $requiredMetrics = @("activeParticipantRate", "scenarioCompletionRate", "criticalErrorCount", "blockedParticipantCount", "satisfactionScore")
    foreach ($metric in $requiredMetrics) {
        if (@($Manifest.metrics | Where-Object { $_.code -eq $metric }).Count -ne 1) {
            $errors.Add("Metric '$metric' must be defined exactly once") | Out-Null
        }
    }
    foreach ($metric in @($Manifest.metrics)) {
        if ([string] $metric.operator -notin @(">=", "<=", "=")) {
            $errors.Add("Metric '$($metric.code)' has unsupported operator") | Out-Null
        }
        if (Test-PilotPlaceholder $metric.description -or Test-PilotPlaceholder $metric.source) {
            $errors.Add("Metric '$($metric.code)' requires description and source") | Out-Null
        }
    }

    if ($Level -in @("freeze", "simulation-freeze")) {
        foreach ($field in @("scopeConfirmedBy", "feedbackConfirmedBy", "stopConditionsConfirmedBy")) {
            $confirmed = @($Manifest.kickoffApproval.$field)
            foreach ($participantId in $participantIds) {
                if ($confirmed -notcontains $participantId) {
                    $errors.Add("kickoffApproval.$field is missing '$participantId'") | Out-Null
                }
            }
        }
        if (-not (Test-PilotDate $Manifest.kickoffApproval.acceptedAt)) {
            $errors.Add("kickoffApproval.acceptedAt must be an ISO-8601 timestamp") | Out-Null
        }
        if ([string] $Manifest.kickoffApproval.releaseCommit -notmatch '^[0-9a-fA-F]{40}$') {
            $errors.Add("kickoffApproval.releaseCommit must be a full Git commit") | Out-Null
        }
        if (Test-PilotPlaceholder $Manifest.kickoffApproval.backupManifest) {
            $errors.Add("kickoffApproval.backupManifest must identify a verified backup") | Out-Null
        }
        if ([string] $Manifest.kickoffApproval.decision -ne "go") {
            $errors.Add("kickoffApproval.decision must be 'go'") | Out-Null
        }
    }
    if ($Level -eq "freeze") {
        if ([string] $Manifest.mode -ne "real") {
            $errors.Add("A real freeze requires mode 'real'") | Out-Null
        }
        if ([string] $Manifest.kickoffApproval.confirmationBasis -ne "human-participants") {
            $errors.Add("A real freeze requires kickoffApproval.confirmationBasis 'human-participants'") | Out-Null
        }
    }
    if ($Level -eq "simulation-freeze") {
        if ([string] $Manifest.mode -ne "rehearsal") {
            $errors.Add("A simulation freeze requires mode 'rehearsal'") | Out-Null
        }
        if ([string] $Manifest.kickoffApproval.confirmationBasis -ne "synthetic-personas") {
            $errors.Add("A simulation freeze requires kickoffApproval.confirmationBasis 'synthetic-personas'") | Out-Null
        }
        if ([string] $Manifest.kickoffApproval.sourceSnapshot -notmatch '^[0-9a-fA-F]{64}$') {
            $errors.Add("A simulation freeze requires a 64-character kickoffApproval.sourceSnapshot") | Out-Null
        }
        $limitations = @($Manifest.kickoffApproval.limitationsAcknowledged)
        foreach ($limitation in @("no-real-user-feedback", "no-human-satisfaction-evidence", "not-production-release-approval")) {
            if ($limitations -notcontains $limitation) {
                $errors.Add("A simulation freeze must acknowledge limitation '$limitation'") | Out-Null
            }
        }
    }

    return [pscustomobject]@{
        valid = $errors.Count -eq 0
        level = $Level
        errors = @($errors)
        warnings = @($warnings)
    }
}

function Write-PilotValidationReport {
    param(
        [Parameter(Mandatory = $true)] $Manifest,
        [Parameter(Mandatory = $true)] $Validation,
        [Parameter(Mandatory = $true)][string] $ReportDirectory,
        [string] $Label = "manifest"
    )

    New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $jsonPath = Join-Path $ReportDirectory "pilot-v2-$Label-$timestamp.json"
    $markdownPath = Join-Path $ReportDirectory "pilot-v2-$Label-$timestamp.md"
    [ordered]@{
        pilotId = [string] $Manifest.pilotId
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        level = $Validation.level
        decision = if ($Validation.valid) { "PASS" } else { "BLOCKED" }
        participantCount = @($Manifest.participants).Count
        scenarioCount = @($Manifest.scenarios).Count
        errors = @($Validation.errors)
        warnings = @($Validation.warnings)
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
    $content = @(
        "# PILOT-V2 Manifest Check",
        "",
        "- Pilot: $($Manifest.pilotId)",
        "- Level: $($Validation.level)",
        "- Decision: $(if ($Validation.valid) { 'PASS' } else { 'BLOCKED' })",
        "- Participants: $(@($Manifest.participants).Count)",
        "- Scenarios: $(@($Manifest.scenarios).Count)",
        "",
        "## Errors",
        ""
    )
    if (@($Validation.errors).Count -eq 0) { $content += "- None" }
    else { $content += @($Validation.errors | ForEach-Object { "- $_" }) }
    $content += @("", "## Warnings", "")
    if (@($Validation.warnings).Count -eq 0) { $content += "- None" }
    else { $content += @($Validation.warnings | ForEach-Object { "- $_" }) }
    Set-Content -LiteralPath $markdownPath -Value ($content -join [Environment]::NewLine) -Encoding UTF8
    return [pscustomobject]@{ jsonPath = $jsonPath; markdownPath = $markdownPath }
}
