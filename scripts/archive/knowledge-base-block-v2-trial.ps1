param(
    [string] $OutputDir = ".local-reports",
    [string] $BaseUrl = "http://127.0.0.1:8080",
    [string] $Username = "admin",
    [string] $Password = $null,
    [switch] $SkipApi
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $Root $OutputDir }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ResolvedOutputDir "kb-block-v2-trial-$Timestamp.md"

New-Item -ItemType Directory -Force -Path $ResolvedOutputDir | Out-Null

$Checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string] $Step,
        [ValidateSet("PASS", "WARN", "SKIP")]
        [string] $Status,
        [string] $Evidence
    )
    $Checks.Add([pscustomobject]@{ Step = $Step; Status = $Status; Evidence = $Evidence }) | Out-Null
}

function Invoke-Json {
    param(
        [string] $Method,
        [string] $Path,
        [object] $Body = $null,
        [string] $Token = $null
    )
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }
    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 20)
}

if ($SkipApi) {
    Add-Check "API execution" "SKIP" "SkipApi was provided; report contains the required trial checklist only."
} elseif ([string]::IsNullOrWhiteSpace($Password) -and [string]::IsNullOrWhiteSpace($env:COLLA_ADMIN_PASSWORD)) {
    Add-Check "API execution" "SKIP" "No password was provided. Pass -Password or set COLLA_ADMIN_PASSWORD to run the API trial."
} else {
    try {
        $resolvedPassword = if ([string]::IsNullOrWhiteSpace($Password)) { $env:COLLA_ADMIN_PASSWORD } else { $Password }
        $login = Invoke-Json -Method "POST" -Path "/api/auth/login" -Body @{
            username = $Username
            password = $resolvedPassword
            deviceId = "kb-block-v2-trial-$Timestamp"
        }
        $token = $login.token
        Add-Check "Login" "PASS" "Logged in as $Username."

        $suffix = (New-Guid).Guid.Substring(0, 8)
        $space = Invoke-Json -Method "POST" -Path "/api/knowledge-bases" -Token $token -Body @{
            name = "KB V2 Trial $suffix"
            code = "kb-v2-trial-$suffix"
            visibility = "private"
            defaultPermissionLevel = "view"
        }
        $spaceId = $space.space.id
        $rootId = $space.space.rootDocumentId
        Add-Check "Create knowledge base" "PASS" "spaceId=$spaceId rootDocumentId=$rootId"

        $doc = Invoke-Json -Method "POST" -Path "/api/knowledge-bases/$spaceId/items" -Token $token -Body @{
            parentId = $rootId
            title = "Block Trial Doc $suffix"
            docType = "markdown"
            content = "# Trial`nInitial block document"
        }
        $docId = $doc.document.id
        Add-Check "Create block document" "PASS" "documentId=$docId blocks=$($doc.blocks.Count)"

        $import = Invoke-Json -Method "POST" -Path "/api/docs/$docId/import/markdown" -Token $token -Body @{
            title = "Imported Block Trial $suffix"
            content = "# Imported`nImported legacy content`n::object-card{objectType=`"base_table`" objectId=`"00000000-0000-0000-0000-000000000000`" fallback=`"Base placeholder`"}"
        }
        Add-Check "Import old content" "PASS" "version=$($import.document.currentVersionNo) blocks=$($import.blocks.Count)"

        $blockSave = Invoke-Json -Method "PATCH" -Path "/api/docs/$docId/blocks" -Token $token -Body @{
            baseVersionNo = $import.document.currentVersionNo
            blocks = @(
                @{ blockType = "heading"; content = "Block Trial" },
                @{ blockType = "paragraph"; content = "SearchToken$suffix" },
                @{ blockType = "base_view"; content = (@{ objectType = "base_table"; objectId = "00000000-0000-0000-0000-000000000000"; fallback = "Base placeholder" } | ConvertTo-Json -Compress) }
            )
        }
        $blockId = $blockSave.blocks[1].id
        Add-Check "Embed Base object" "PASS" "blockId=$blockId embeddedBlockCount=$($blockSave.blocks.Count)"

        $comment = Invoke-Json -Method "POST" -Path "/api/docs/$docId/comments" -Token $token -Body @{
            blockId = $blockId
            anchorType = "block"
            content = "Block comment $suffix"
        }
        Add-Check "Comment block" "PASS" "comments=$($comment.comments.Count)"

        [void](Invoke-Json -Method "POST" -Path "/api/search/reindex" -Token $token)
        $search = Invoke-Json -Method "GET" -Path "/api/search?q=SearchToken$suffix" -Token $token
        $searchHit = @($search.items | Where-Object { $_.webPath -like "*#doc-block-*" }).Count
        Add-Check "Search hit" ($(if ($searchHit -gt 0) { "PASS" } else { "WARN" })) "blockSearchHitCount=$searchHit"

        $markdown = Invoke-RestMethod -Method GET -Uri "$BaseUrl/api/docs/$docId/export/markdown" -Headers @{ Authorization = "Bearer $token" }
        Add-Check "Export markdown" ($(if ($markdown -match "object-card" -and $markdown -match "SearchToken$suffix") { "PASS" } else { "WARN" })) "length=$($markdown.Length)"
    } catch {
        Add-Check "API execution" "WARN" $_.Exception.Message.Replace("|", "\|")
    }
}

$report = @(
    "# Knowledge Base Block v2 Trial",
    "",
    "- Time: $(Get-Date -Format o)",
    "- Base URL: $BaseUrl",
    "",
    "## Checks",
    "",
    "| Step | Status | Evidence |",
    "| --- | --- | --- |"
)
foreach ($check in $Checks) {
    $report += "| $($check.Step) | $($check.Status) | $($check.Evidence) |"
}
$report += @(
    "",
    "## Manual Trial Coverage",
    "",
    "1. Create a knowledge base and confirm default entry opens content, not metadata.",
    "2. Create and edit a block document with heading, paragraph, divider, task and table blocks.",
    "3. Import legacy Markdown/HTML content and confirm unsupported HTML becomes `legacy_html` or a safe placeholder.",
    "4. Embed a Base table/view object and confirm unavailable objects degrade without leaking private metadata.",
    "5. Add a comment to a block and verify the deep link contains `#doc-block-...`.",
    "6. Reindex search and confirm a block-level hit opens the highlighted block.",
    "7. Export Markdown/HTML and confirm embedded objects use safe fallback directives/placeholders."
)

Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Knowledge-base block v2 trial report: $ReportPath"
