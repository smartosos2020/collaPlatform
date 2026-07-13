param([string]$Root = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = "Stop"
$findings = New-Object System.Collections.Generic.List[string]
$sourceRoots = @(
    (Join-Path $Root "server/src/main/java"),
    (Join-Path $Root "web/src"),
    (Join-Path $Root "web/e2e")
)
$fixedPatterns = @(
    "/api/docs",
    "colla://document/",
    "convert-to-document",
    "modules.doc",
    "modules/docs",
    "LEGACY_DOCUMENT",
    " from documents",
    " join documents",
    "document_blocks",
    "documents.content",
    "snapshot_content"
)
$regexPatterns = @(
    '\b(class|record|interface)\s+Document[A-Z][A-Za-z0-9_]*',
    '"document\.[a-zA-Z0-9_.-]+"'
)

foreach ($sourceRoot in $sourceRoots) {
    Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
        Where-Object { $_.Extension -in @(".java", ".ts", ".tsx") } |
        ForEach-Object {
            $content = [System.IO.File]::ReadAllText($_.FullName)
            $relativePath = $_.FullName.Substring($Root.TrimEnd("\\").Length + 1)
            foreach ($pattern in $fixedPatterns) {
                if ($content.Contains($pattern)) {
                    $findings.Add("$relativePath :: $pattern") | Out-Null
                }
            }
            foreach ($pattern in $regexPatterns) {
                if ([System.Text.RegularExpressions.Regex]::IsMatch($content, $pattern)) {
                    $findings.Add("$relativePath :: regex $pattern") | Out-Null
                }
            }
        }
}

if ($findings.Count -gt 0) {
    Write-Host "Knowledge naming guard found retired product vocabulary:"
    $findings | Sort-Object -Unique | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "Knowledge naming guard passed."
