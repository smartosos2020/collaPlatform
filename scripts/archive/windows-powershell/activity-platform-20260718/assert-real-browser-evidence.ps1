param(
    [Parameter(Mandatory = $true)]
    [string] $Command,

    [string] $Root = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

# Real browser evidence semantics (aligned with ai-engineering-governance 4.4.2):
# - Banned anywhere in the evidence closure: API/response interception mocks
#   (page.route, context.route, route.fulfill, route.abort) and any form of forged
#   token or fabricated API response.
# - Allowed: installing a REAL session obtained through a real API login. The one
#   sanctioned place for page.addInitScript is web/e2e/support/api.ts, which persists
#   a genuinely issued token into the browser context.
# The closure of a spec is the spec file itself plus every local file it imports
# (recursively), so mocks cannot be hidden inside helpers.

$referencePattern = "(?i)(?:[A-Za-z0-9_.-]+[\\/])*[A-Za-z0-9_.-]+(?:\.spec\.(?:ts|tsx|js)|\.ps1)"
$references = [regex]::Matches($Command, $referencePattern) |
    ForEach-Object { $_.Value } | Select-Object -Unique
if (@($references).Count -eq 0) {
    throw "Real browser evidence must name a concrete Playwright spec or browser script; --grep alone is not sufficient."
}

$basePaths = @($Root, (Join-Path $Root "web"), (Join-Path $Root "web/e2e"))
function Resolve-EvidenceReference {
    param([string] $Reference, [string[]] $Bases)
    $resolved = New-Object System.Collections.Generic.List[string]
    foreach ($basePath in $Bases) {
        $candidate = Join-Path $basePath $Reference
        if ((Test-Path -LiteralPath $candidate) -and -not $resolved.Contains($candidate)) {
            $resolved.Add((Resolve-Path -LiteralPath $candidate).Path) | Out-Null
        }
    }
    return $resolved.ToArray()
}

$specAndScriptFiles = New-Object System.Collections.Generic.List[string]
foreach ($reference in $references) {
    foreach ($resolved in @(Resolve-EvidenceReference -Reference $reference -Bases $basePaths)) {
        if (-not $specAndScriptFiles.Contains($resolved)) {
            $specAndScriptFiles.Add($resolved) | Out-Null
        }
    }
}
if ($specAndScriptFiles.Count -eq 0) {
    throw "Real browser evidence must reference an existing Playwright spec or browser script."
}

# Browser scripts (.ps1) launch specs indirectly; pull the specs they name into the set.
foreach ($scriptSource in @($specAndScriptFiles | Where-Object { $_ -match "\.ps1$" })) {
    $scriptContent = Get-Content -LiteralPath $scriptSource -Raw
    $nestedReferences = [regex]::Matches($scriptContent, "(?i)(?:[A-Za-z0-9_.-]+[\\/])*[A-Za-z0-9_.-]+\.spec\.(?:ts|tsx|js)") |
        ForEach-Object { $_.Value } | Select-Object -Unique
    foreach ($reference in $nestedReferences) {
        foreach ($resolved in @(Resolve-EvidenceReference -Reference $reference -Bases $basePaths)) {
            if (-not $specAndScriptFiles.Contains($resolved)) {
                $specAndScriptFiles.Add($resolved) | Out-Null
            }
        }
    }
}

$e2eRoot = (Resolve-Path -LiteralPath (Join-Path $Root "web/e2e")).Path
$importPattern = "(?im)^\s*import(?:\s+[^'""]+?\s+from)?\s*['""]([^'""]+)['""]"

function Resolve-ImportPath {
    param([string] $ImporterFile, [string] $ImportSpecifier)

    if (-not $ImportSpecifier.StartsWith(".")) {
        return $null
    }
    $importerDir = Split-Path $ImporterFile -Parent
    $candidateBase = Join-Path $importerDir $ImportSpecifier
    foreach ($candidate in @($candidateBase, "$candidateBase.ts", "$candidateBase.tsx", "$candidateBase.js", (Join-Path $candidateBase "index.ts"))) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Get-EvidenceClosure {
    param([string] $SpecFile)

    $closure = New-Object System.Collections.Generic.List[string]
    $queue = New-Object System.Collections.Generic.Queue[string]
    $queue.Enqueue($SpecFile)
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        if ($closure.Contains($current)) {
            continue
        }
        $closure.Add($current) | Out-Null
        if ($current -notmatch "\.(ts|tsx|js)$") {
            continue
        }
        $content = Get-Content -LiteralPath $current -Raw
        foreach ($match in [regex]::Matches($content, $importPattern)) {
            $resolvedImport = Resolve-ImportPath -ImporterFile $current -ImportSpecifier $match.Groups[1].Value
            if ($null -ne $resolvedImport -and $resolvedImport.StartsWith($e2eRoot) -and -not $closure.Contains($resolvedImport)) {
                $queue.Enqueue($resolvedImport)
            }
        }
    }
    return $closure.ToArray()
}

$interceptionMarkers = @(
    "\bpage\.route\s*\(",
    "\bcontext\.route\s*\(",
    "\broute\.fulfill\s*\(",
    "\broute\.abort\s*\("
)
$sessionInstallMarker = "\bpage\.addInitScript\s*\("
$sanctionedSessionInstaller = (Join-Path $e2eRoot "support\api.ts")

$violations = New-Object System.Collections.Generic.List[string]
$scannedFiles = 0
foreach ($specFile in @($specAndScriptFiles | Where-Object { $_ -match "\.spec\.(ts|tsx|js)$" })) {
    foreach ($closureFile in @(Get-EvidenceClosure -SpecFile $specFile)) {
        $scannedFiles++
        $content = Get-Content -LiteralPath $closureFile -Raw
        foreach ($marker in $interceptionMarkers) {
            if ($content -match $marker) {
                $relative = $closureFile.Substring($Root.Length + 1).Replace("\", "/")
                $violations.Add("$relative uses intercepted mock marker $($marker.Trim())") | Out-Null
            }
        }
        if ($content -match $sessionInstallMarker -and $closureFile -ne $sanctionedSessionInstaller) {
            $relative = $closureFile.Substring($Root.Length + 1).Replace("\", "/")
            $violations.Add("$relative uses page.addInitScript outside the sanctioned real-session installer (web/e2e/support/api.ts)") | Out-Null
        }
    }
}

if ($violations.Count -gt 0) {
    throw "Browser evidence is declared real but mocks browser/API state: $($violations -join '; ')"
}

Write-Host "Real browser evidence sources verified: $($specAndScriptFiles.Count) top-level reference(s), $scannedFiles file(s) scanned including imported helpers."
