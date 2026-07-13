param(
    [ValidateSet("baseline")]
    [string] $Mode = "baseline",
    [string] $OutputDir = ".local-reports",
    [string] $Database = "colla_platform",
    [string] $Username = "colla",
    [string] $DbPassword = $null,
    [string] $DockerPostgresContainer = "colla-postgres"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "knowledge-naming-inventory-$Timestamp.md"
$JsonPath = Join-Path $ResolvedOutputDir "knowledge-naming-inventory-$Timestamp.json"
$SourcePattern = "(?i)(/api/docs|/docs(?:/|['`"?:])|modules[/\\]docs|\bDocsPage\b|\bdocsApi\b|\bDocument[A-Z][A-Za-z0-9_]*|\bdocId\b|\bdocument_[a-z][a-z0-9_]*\b|\bdocument\.[a-z]|['`"]document['`"]|\bDOCUMENT\b)"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

function Get-RelativePath {
    param([string] $Path)

    $rootPath = $Root.Path
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath += [System.IO.Path]::DirectorySeparatorChar
    }
    $rootUri = [System.Uri]::new($rootPath)
    $pathUri = [System.Uri]::new((Resolve-Path -LiteralPath $Path).Path)
    return [System.Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\", "/")
}

function Get-Files {
    param(
        [string[]] $Paths,
        [string[]] $Extensions
    )

    $files = foreach ($path in $Paths) {
        $resolved = if ([System.IO.Path]::IsPathRooted($path)) { $path } else { Join-Path $Root $path }
        if (Test-Path -LiteralPath $resolved -PathType Container) {
            Get-ChildItem -LiteralPath $resolved -Recurse -File | Where-Object { $Extensions -contains $_.Extension }
        } elseif (Test-Path -LiteralPath $resolved -PathType Leaf) {
            Get-Item -LiteralPath $resolved
        }
    }
    return @($files | Sort-Object FullName -Unique)
}

function Get-MatchRecords {
    param(
        [string] $Category,
        [System.IO.FileInfo[]] $Files
    )

    if (-not $Files -or $Files.Count -eq 0) {
        return @()
    }

    return @($Files | Select-String -Pattern $SourcePattern -AllMatches | ForEach-Object {
        [pscustomobject]@{
            category = $Category
            file = Get-RelativePath $_.Path
            line = $_.LineNumber
            occurrenceCount = $_.Matches.Count
            text = $_.Line.Trim()
        }
    })
}

function Invoke-DbScalar {
    param([string] $Sql)

    $result = & docker exec -e "PGPASSWORD=$DbPassword" $DockerPostgresContainer `
        psql -U $Username -d $Database -X -A -t -v ON_ERROR_STOP=1 -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($result -join [Environment]::NewLine)
    }
    $value = $result | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
    if ($null -eq $value) {
        return ""
    }
    return $value.ToString().Trim()
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $DbPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "colla_dev_password" }
}

$frontendCore = Get-Files `
    -Paths @("web/src/modules/docs", "web/src/app/router.tsx", "web/src/shared/client/deepLinks.ts") `
    -Extensions @(".ts", ".tsx", ".css")
$frontendAll = Get-Files -Paths @("web/src") -Extensions @(".ts", ".tsx", ".css")
$frontendCorePaths = @($frontendCore | ForEach-Object FullName)
$frontendCross = @($frontendAll | Where-Object { $frontendCorePaths -notcontains $_.FullName })

$backendCore = Get-Files -Paths @("server/src/main/java/com/colla/platform/modules/doc") -Extensions @(".java")
$backendAll = Get-Files -Paths @("server/src/main/java") -Extensions @(".java")
$backendCorePaths = @($backendCore | ForEach-Object FullName)
$backendCross = @($backendAll | Where-Object { $backendCorePaths -notcontains $_.FullName })

$tests = Get-Files -Paths @("server/src/test", "web/e2e") -Extensions @(".java", ".ts", ".tsx")
$localScripts = Get-Files -Paths @("scripts") -Extensions @(".ps1", ".psm1", ".psd1")
$migrations = Get-Files -Paths @("server/src/main/resources/db/migration") -Extensions @(".sql")

$groups = [ordered]@{
    frontend_core = $frontendCore
    frontend_cross_module = $frontendCross
    backend_core = $backendCore
    backend_cross_module = $backendCross
    tests = $tests
    local_scripts = $localScripts
    immutable_migrations = $migrations
}

$allMatches = New-Object System.Collections.Generic.List[object]
$sourceSummary = New-Object System.Collections.Generic.List[object]
foreach ($entry in $groups.GetEnumerator()) {
    $records = @(Get-MatchRecords -Category $entry.Key -Files $entry.Value)
    foreach ($record in $records) {
        $allMatches.Add($record) | Out-Null
    }
    $sourceSummary.Add([pscustomobject]@{
        category = $entry.Key
        scannedFiles = $entry.Value.Count
        matchedFiles = @($records.file | Sort-Object -Unique).Count
        matchedLines = $records.Count
        occurrences = ($records | Measure-Object -Property occurrenceCount -Sum).Sum
    }) | Out-Null
}

$databaseAvailable = $false
$databaseError = $null
$databaseMetrics = [ordered]@{}
try {
    $runningContainers = @(& docker ps --format "{{.Names}}" 2>$null)
    if ($runningContainers -notcontains $DockerPostgresContainer) {
        throw "Docker PostgreSQL container '$DockerPostgresContainer' is not running."
    }
    [void](Invoke-DbScalar "select 1;")
    $databaseAvailable = $true

    $metricQueries = [ordered]@{
        active_spaces = "select count(*) from knowledge_base_spaces where deleted_at is null;"
        active_items = "select count(*) from knowledge_base_items where deleted_at is null;"
        active_markdown_items = "select count(*) from knowledge_base_items where deleted_at is null and content_type='markdown';"
        active_directory_items = "select count(*) from knowledge_base_items where deleted_at is null and item_kind='directory';"
        active_object_reference_items = "select count(*) from knowledge_base_items where deleted_at is null and item_kind='object_ref';"
        active_external_link_items = "select count(*) from knowledge_base_items where deleted_at is null and item_kind='external_link';"
        active_blocks = "select count(*) from knowledge_content_blocks where deleted_at is null;"
        block_coverage_gap = "select count(*) from knowledge_base_items d where d.deleted_at is null and d.content_type='markdown' and not exists(select 1 from knowledge_content_blocks b where b.workspace_id=d.workspace_id and b.item_id=d.id and b.deleted_at is null);"
        markdown_with_blocks = "select count(*) from knowledge_base_items d where d.deleted_at is null and d.content_type='markdown' and exists(select 1 from knowledge_content_blocks b where b.workspace_id=d.workspace_id and b.item_id=d.id and b.deleted_at is null);"
        block_coverage_percent = "select coalesce(round(100.0*count(*) filter(where exists(select 1 from knowledge_content_blocks b where b.workspace_id=d.workspace_id and b.item_id=d.id and b.deleted_at is null))/nullif(count(*),0),2),0) from knowledge_base_items d where d.deleted_at is null and d.content_type='markdown';"
        old_content_with_blocks = "select 0;"
        legacy_html_blocks = "select count(*) from knowledge_content_blocks where deleted_at is null and block_type='legacy_html';"
        orphan_blocks = "select count(*) from knowledge_content_blocks b where b.deleted_at is null and b.parent_id is not null and not exists(select 1 from knowledge_content_blocks p where p.workspace_id=b.workspace_id and p.item_id=b.item_id and p.id=b.parent_id and p.deleted_at is null);"
        document_tables = "select count(*) from information_schema.tables where table_schema='public' and table_name like 'document%';"
        document_columns = "select count(*) from information_schema.columns where table_schema='public' and (table_name like 'document%' or column_name like '%item_id');"
        document_indexes = "select count(*) from pg_indexes where schemaname='public' and (tablename like 'document%' or indexname like '%document%');"
        document_constraints = "select count(*) from information_schema.table_constraints where table_schema='public' and (table_name like 'document%' or constraint_name like '%document%');"
        document_triggers = "select count(*) from information_schema.triggers where trigger_schema='public' and (event_object_table like 'document%' or trigger_name like '%document%');"
        orphan_items = "select count(*) from knowledge_base_items d where d.deleted_at is null and d.parent_id is not null and not exists(select 1 from knowledge_base_items p where p.workspace_id=d.workspace_id and p.id=d.parent_id and p.deleted_at is null);"
        malformed_object_reference_items = "select count(*) from knowledge_base_items where deleted_at is null and item_kind='object_ref' and (target_object_type is null or target_object_id is null or coalesce(target_route,'')='');"
        malformed_external_link_items = "select count(*) from knowledge_base_items where deleted_at is null and item_kind='external_link' and coalesce(target_route,'')='';"
        document_resource_permissions = "select count(*) from resource_permissions where resource_type='document';"
        document_permission_requests = "select count(*) from resource_permission_requests where resource_type='document';"
        document_search_rows = "select count(*) from search_index_entries where object_type='document';"
        document_notifications = "select count(*) from notifications where target_type='document';"
        document_object_links = "select count(*) from object_links where object_type='document';"
        document_subscriptions = "select count(*) from knowledge_subscriptions where target_type='document';"
        document_event_rows = "select count(*) from domain_events where event_type like 'document.%' or aggregate_type='document';"
        document_audit_rows = "select count(*) from audit_logs where action like 'document.%' or target_type='document';"
        document_target_references = "select count(*) from knowledge_base_items where target_object_type='document';"
        old_docs_target_routes = "select count(*) from knowledge_base_items where target_route like '/docs/%';"
        old_docs_notification_routes = "select count(*) from notifications where web_path like '/docs/%';"
    }
    foreach ($metric in $metricQueries.GetEnumerator()) {
        $databaseMetrics[$metric.Key] = Invoke-DbScalar $metric.Value
    }
} catch {
    $databaseError = $_.Exception.Message
}

$vocabulary = @(
    [pscustomobject]@{ legacy = "DocumentSummary / knowledge_base_items row"; target = "KnowledgeBaseItem"; boundary = "Any tree item: content, directory, object reference, or external link" },
    [pscustomobject]@{ legacy = "DocumentDetail / document content"; target = "KnowledgeContent"; boundary = "Editable content only" },
    [pscustomobject]@{ legacy = "DocumentBlock"; target = "KnowledgeContentBlock"; boundary = "Primary body fact" },
    [pscustomobject]@{ legacy = "DOCUMENT / document"; target = "KNOWLEDGE_CONTENT / knowledge_content"; boundary = "Platform object type for editable knowledge content" },
    [pscustomobject]@{ legacy = "knowledge_base_items"; target = "knowledge_base_items"; boundary = "Knowledge-base tree storage" },
    [pscustomobject]@{ legacy = "modules.doc / modules/docs"; target = "modules.knowledge / knowledgeBases/content"; boundary = "Knowledge domain and frontend content implementation" }
)

$contracts = @(
    [pscustomobject]@{ capability = "Tree and item lifecycle"; canonical = "/api/knowledge-bases/{spaceId}/items"; legacy = "/api/docs, /api/docs/tree" },
    [pscustomobject]@{ capability = "Content detail and save"; canonical = "/api/knowledge-bases/{spaceId}/items/{itemId}"; legacy = "/api/docs/{documentId}" },
    [pscustomobject]@{ capability = "Blocks"; canonical = "/api/knowledge-bases/{spaceId}/items/{itemId}/blocks"; legacy = "/api/docs/{documentId}/blocks" },
    [pscustomobject]@{ capability = "Comments and versions"; canonical = "/api/knowledge-bases/{spaceId}/items/{itemId}/{comments|versions}"; legacy = "/api/docs/{documentId}/{comments|versions}" },
    [pscustomobject]@{ capability = "Share and permissions"; canonical = "/api/knowledge-bases/{spaceId}/items/{itemId}/{share-links|permissions}"; legacy = "/api/docs/{documentId}/{share-link|permissions}" },
    [pscustomobject]@{ capability = "Web route"; canonical = "/knowledge-bases/{spaceId}/items/{itemId}"; legacy = "/docs/{docId}" },
    [pscustomobject]@{ capability = "Admin governance"; canonical = "/api/admin/knowledge-bases/*"; legacy = "/api/knowledge-bases/{spaceId}/governance*" }
)

$compatibility = @(
    [pscustomobject]@{ item = "/docs/{docId}"; owner = "Web edge locator"; observation = "request count, source UI, last hit"; deleteGate = "All stored paths canonical and zero unexplained hits"; rollback = "Re-enable locator redirect only" },
    [pscustomobject]@{ item = "/api/docs/*"; owner = "Knowledge compat controller"; observation = "endpoint/client/user-agent/last hit"; deleteGate = "Canonical API parity and zero internal callers"; rollback = "Re-enable delegating adapter only" },
    [pscustomobject]@{ item = "document objectType"; owner = "Platform compat resolver"; observation = "persisted rows by table and new-write counter"; deleteGate = "Backfill complete and new writes remain zero"; rollback = "Read alias only" },
    [pscustomobject]@{ item = "document.* events/audit"; owner = "Event and audit read aliases"; observation = "new events and historical row counts"; deleteGate = "No old publishers/consumers; history display mapped"; rollback = "Historical read mapping" },
    [pscustomobject]@{ item = "knowledge_base_items.content and version content"; owner = "Offline migration tooling"; observation = "block coverage, snapshot parity, restore drills"; deleteGate = "100% coverage and block-only export/search/restore"; rollback = "Offline backup restore, never runtime dual write" },
    [pscustomobject]@{ item = "document_* schema"; owner = "Flyway migration"; observation = "schema inventory, row/FK/index checksums"; deleteGate = "V043 upgrade and empty-schema tests pass"; rollback = "Database backup and tested restore" }
)

$payload = [ordered]@{
    generatedAt = (Get-Date -Format o)
    mode = $Mode
    sourcePattern = $SourcePattern
    sourceSummary = @($sourceSummary | ForEach-Object { $_ })
    matches = @($allMatches | ForEach-Object { $_ })
    database = [ordered]@{
        available = $databaseAvailable
        error = $databaseError
        metrics = $databaseMetrics
    }
    vocabulary = $vocabulary
    contracts = $contracts
    compatibility = $compatibility
}
$payload | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $JsonPath -Encoding UTF8

$report = @(
    "# Knowledge Naming Inventory",
    "",
    "- Generated: $($payload.generatedAt)",
    "- Mode: $Mode",
    "- Database available: $databaseAvailable",
    "- JSON evidence: $JsonPath",
    "",
    "## Source Inventory",
    "",
    "| Category | Scanned files | Matched files | Matched lines | Occurrences |",
    "| --- | ---: | ---: | ---: | ---: |"
)
foreach ($row in $sourceSummary) {
    $report += "| $($row.category) | $($row.scannedFiles) | $($row.matchedFiles) | $($row.matchedLines) | $($row.occurrences) |"
}

$report += @("", "## Representative Dependencies", "")
foreach ($row in $sourceSummary) {
    $report += "### $($row.category)"
    $report += ""
    $samples = @($allMatches | Where-Object category -eq $row.category | Select-Object -First 10)
    if ($samples.Count -eq 0) {
        $report += "- None"
    } else {
        foreach ($sample in $samples) {
            $report += "- ``$($sample.file):$($sample.line)`` - $($sample.text.Replace('|', '\|'))"
        }
    }
    $report += ""
}

$report += @(
    "## Database Baseline",
    "",
    "| Metric | Value |",
    "| --- | ---: |"
)
if ($databaseAvailable) {
    foreach ($metric in $databaseMetrics.GetEnumerator()) {
        $report += "| $($metric.Key) | $($metric.Value) |"
    }
} else {
    $report += "| database_error | $($databaseError.Replace('|', '\|')) |"
}

$report += @("", "## Frozen Vocabulary", "", "| Legacy | Target | Boundary |", "| --- | --- | --- |")
foreach ($row in $vocabulary) {
    $report += "| $($row.legacy) | $($row.target) | $($row.boundary) |"
}

$report += @("", "## Canonical Contract Matrix", "", "| Capability | Canonical | Legacy |", "| --- | --- | --- |")
foreach ($row in $contracts) {
    $report += "| $($row.capability) | ``$($row.canonical)`` | ``$($row.legacy)`` |"
}

$report += @("", "## Compatibility Registry", "", "| Item | Owner | Observation | Delete gate | Rollback |", "| --- | --- | --- | --- | --- |")
foreach ($row in $compatibility) {
    $report += "| ``$($row.item)`` | $($row.owner) | $($row.observation) | $($row.deleteGate) | $($row.rollback) |"
}

$report += @(
    "",
    "## Classification Rules",
    "",
    "- Core product legacy: current routes, APIs, domain classes, platform types, events, active schema, and runtime writes.",
    "- Edge compatibility: explicitly registered old routes, object aliases, and historical readers that delegate to canonical use cases.",
    "- Immutable history: applied Flyway files, archived reports, and historical audit/event rows; these are mapped, not rewritten.",
    "- Allowed technical language: generic search-index document or editor document model only when it cannot be confused with the old product module.",
    "- Baseline mode is read-only and never fails because legacy references exist; a later guard mode will compare core references against an approved allowlist."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge naming inventory report: $ReportPath"
Write-Host "Knowledge naming inventory JSON: $JsonPath"
Write-Host "Source matches: $($allMatches.Count) lines"
Write-Host "Database available: $databaseAvailable"
