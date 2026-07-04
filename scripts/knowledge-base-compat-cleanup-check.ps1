param(
    [string] $OutputDir = ".local-reports"
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "kb-compat-cleanup-check-$Timestamp.md"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

$Checks = New-Object System.Collections.Generic.List[object]
$Decisions = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string] $Key,
        [string] $Name,
        [ValidateSet("PASS", "WARN", "FAIL", "SKIP")]
        [string] $Status,
        [string] $Evidence
    )
    $Checks.Add([pscustomobject]@{ Key = $Key; Name = $Name; Status = $Status; Evidence = $Evidence }) | Out-Null
}

function Add-Decision {
    param(
        [string] $Item,
        [string] $Decision,
        [string] $Reason,
        [string] $DeleteCondition
    )
    $Decisions.Add([pscustomobject]@{
        Item = $Item
        Decision = $Decision
        Reason = $Reason
        DeleteCondition = $DeleteCondition
    }) | Out-Null
}

function Get-RelativePath {
    param([string] $Path)
    $rootPath = $Root.Path
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath = $rootPath + [System.IO.Path]::DirectorySeparatorChar
    }
    $rootUri = [System.Uri]::new($rootPath)
    $pathUri = [System.Uri]::new((Resolve-Path -LiteralPath $Path).Path)
    return [System.Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\", "/")
}

function Find-Text {
    param(
        [string[]] $Path,
        [string] $Pattern,
        [string[]] $Include = @("*")
    )
    $items = foreach ($target in $Path) {
        $full = if ([System.IO.Path]::IsPathRooted($target)) { $target } else { Join-Path $Root $target }
        if (Test-Path -LiteralPath $full -PathType Container) {
            Get-ChildItem -LiteralPath $full -Recurse -File -Include $Include -ErrorAction SilentlyContinue
        } elseif (Test-Path -LiteralPath $full -PathType Leaf) {
            Get-Item -LiteralPath $full
        }
    }
    if (-not $items) {
        return @()
    }
    return @($items | Select-String -Pattern $Pattern -AllMatches | ForEach-Object {
        [pscustomobject]@{
            File = Get-RelativePath $_.Path
            Line = $_.LineNumber
            Text = ($_.Line.Trim() -replace "\|", "\|")
        }
    })
}

function Format-Evidence {
    param(
        [object[]] $Matches,
        [int] $Limit = 8
    )
    if (-not $Matches -or $Matches.Count -eq 0) {
        return "count=0"
    }
    $sample = @($Matches | Select-Object -First $Limit | ForEach-Object { "$($_.File):$($_.Line)" })
    $suffix = if ($Matches.Count -gt $Limit) { "; sample=$($sample -join ', '); more=$($Matches.Count - $Limit)" } else { "; sample=$($sample -join ', ')" }
    return "count=$($Matches.Count)$suffix"
}

$routerText = Get-Content -LiteralPath (Join-Path $Root "web/src/app/router.tsx") -Raw
$layoutText = Get-Content -LiteralPath (Join-Path $Root "web/src/app/layout/AppLayout.tsx") -Raw

if ($layoutText -match "to=['`"]/docs['`"]|href=['`"]/docs['`"]") {
    Add-Check "user-nav-docs-entry" "User navigation has no independent /docs entry" "FAIL" "AppLayout still links to /docs."
} else {
    Add-Check "user-nav-docs-entry" "User navigation has no independent /docs entry" "PASS" "No /docs nav link found in AppLayout."
}

if ($routerText -match "path:\s*['`"]docs['`"]\s*,\s*element:\s*<Navigate\s+to=['`"]/knowledge-bases['`"]") {
    Add-Check "docs-root-redirect" "/docs root redirects to knowledge bases" "PASS" "router.tsx redirects /docs to /knowledge-bases."
} else {
    Add-Check "docs-root-redirect" "/docs root redirects to knowledge bases" "FAIL" "Expected /docs root redirect to /knowledge-bases."
}

if ($routerText -match "path:\s*['`"]docs/:docId['`"]") {
    Add-Check "docs-detail-compat-route" "/docs/:docId compatibility route is retained" "PASS" "Historical deep-link route is present."
} else {
    Add-Check "docs-detail-compat-route" "/docs/:docId compatibility route is retained" "FAIL" "Historical deep-link route is missing."
}

$legacyPermissionWrites = Find-Text `
    -Path @("server/src/main/java") `
    -Include @("*.java") `
    -Pattern "(?i)\b(insert\s+into|update|delete\s+from)\s+document_permissions\b"
if ($legacyPermissionWrites.Count -eq 0) {
    Add-Check "legacy-permission-table-write" "Runtime code does not write document_permissions" "PASS" "No insert/update/delete against document_permissions in server runtime code."
} else {
    Add-Check "legacy-permission-table-write" "Runtime code does not write document_permissions" "FAIL" (Format-Evidence $legacyPermissionWrites)
}

$shadowFieldWrites = Find-Text `
    -Path @("server/src/main/java/com/colla/platform/modules/doc") `
    -Include @("*.java") `
    -Pattern "(?i)(description\s*=\s*\?|cover_url\s*=\s*\?|default_permission_level\s*=\s*\?|knowledge_base\s*=\s*\?)"
if ($shadowFieldWrites.Count -eq 0) {
    Add-Check "deprecated-root-shadow-field-write" "Deprecated root shadow fields are not written by runtime code" "PASS" "No runtime write pattern found."
} else {
    Add-Check "deprecated-root-shadow-field-write" "Deprecated root shadow fields are not written by runtime code" "WARN" ((Format-Evidence $shadowFieldWrites) + "; retained only as compatibility shadow handling until deletion conditions are met.")
}

$productCopyMatches = Find-Text `
    -Path @(
        "docs/00-product/current-product-scope.md",
        "docs/01-architecture/current-architecture.md",
        "docs/01-architecture/platform-object-model.md",
        "docs/03-engineering/ai-engineering-governance.md",
        "docs/05-runbooks/browser-smoke.md",
        "web/src"
    ) `
    -Include @("*.md", "*.ts", "*.tsx") `
    -Pattern "文档模块|团队空间|文档树|文档资源|文档搜索|最近文档|文档协同|文档权限"
$unexpectedCopyMatches = @($productCopyMatches | Where-Object {
    $_.Text -notmatch "历史|兼容|不得|不再|旧|V0\d+|M\d+|Document\*|docs"
})
if ($unexpectedCopyMatches.Count -eq 0) {
    Add-Check "old-product-copy" "Old document-module copy is absent from user-facing/current truth text" "PASS" (Format-Evidence $productCopyMatches)
} else {
    Add-Check "old-product-copy" "Old document-module copy is absent from user-facing/current truth text" "WARN" (Format-Evidence $unexpectedCopyMatches)
}

$docsReferences = Find-Text `
    -Path @("server/src/main/java", "web/src", "scripts", "docs/00-product/current-product-scope.md", "docs/01-architecture/current-architecture.md", "docs/01-architecture/platform-object-model.md") `
    -Include @("*.java", "*.ts", "*.tsx", "*.ps1", "*.md") `
    -Pattern "/docs|/api/docs|document"
Add-Check "compat-reference-inventory" "Compatibility references are inventoried" "PASS" (Format-Evidence $docsReferences 12)

Add-Decision "/docs root route" "Remove now" "User entry is no longer needed and must redirect to knowledge bases." "Already satisfied when /docs redirects to /knowledge-bases and no nav link points to /docs."
Add-Decision "/docs/:docId route" "Retain" "Historical links, share links, notifications and search deep links still depend on it." "Delete only after telemetry confirms no old-link traffic and all stored links are migrated or proxied."
Add-Decision "/api/docs" "Retain" "Editor substrate still serves detail, save, blocks, comments, versions, sharing, permissions and collaboration." "Delete only after equivalent /api/knowledge-bases content APIs cover every editor path and old clients are migrated."
Add-Decision "document objectType" "Retain" "Search index, notifications, recent/favorite, IM cards and object relations still use this stable type." "Delete only after a versioned objectType migration and backfill covers all persisted references."
Add-Decision "documents table" "Retain" "Content nodes, blocks, versions, comments and root/home pointers still depend on this storage model." "Delete only after a separate table migration, rollback plan and full route-final test pass."
Add-Decision "document_permissions table" "Retain read/backfill only" "Runtime authorization uses resource_permissions; legacy rows may exist for migration and audit comparison." "Drop only after migration checks show zero dependencies and rollback no longer needs the table."
Add-Decision "documents.description/cover_url/default_permission_level on root nodes" "Retain deprecated shadow" "Local data may still contain shadow metadata; knowledge_base_spaces is authoritative." "Drop only after root shadow fields are cleared in production data and compatibility readers are removed."
Add-Decision "documents.knowledge_base and doc_type='space'" "Retain deprecated compatibility flag" "Old root detection and deep-link compatibility still need it." "Drop only after all legacy space roots are represented solely by knowledge_base_spaces and fallback detection is removed."

$failures = @($Checks | Where-Object { $_.Status -eq "FAIL" })
$warnings = @($Checks | Where-Object { $_.Status -eq "WARN" })
$decision = if ($failures.Count -gt 0) {
    "NO-GO"
} elseif ($warnings.Count -gt 0) {
    "GO-WITH-REVIEW"
} else {
    "GO"
}

$report = @(
    "# Knowledge Base Compatibility Cleanup Check",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Decision: $decision",
    "- Scope: routes, runtime writes, compatibility references, and active/user-facing copy",
    "",
    "## Checks",
    "",
    "| Key | Check | Status | Evidence |",
    "| --- | --- | --- | --- |"
)
foreach ($check in $Checks) {
    $report += "| $($check.Key) | $($check.Name) | $($check.Status) | $($check.Evidence) |"
}

$report += @(
    "",
    "## Deprecated Compatibility Decision Table",
    "",
    "| Item | Decision | Reason | Delete condition |",
    "| --- | --- | --- | --- |"
)
foreach ($decisionRow in $Decisions) {
    $report += "| $($decisionRow.Item) | $($decisionRow.Decision) | $($decisionRow.Reason) | $($decisionRow.DeleteCondition) |"
}

$report += @(
    "",
    "## Notes",
    "",
    "- This script is read-only and does not mutate source files or databases.",
    "- WARN means compatibility remains intentionally present or needs data cleanup review before deletion.",
    "- NO-GO means a user-facing independent document-module entry or legacy permission write path is still present."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge-base compatibility cleanup check report: $ReportPath"
Write-Host "Decision: $decision"
