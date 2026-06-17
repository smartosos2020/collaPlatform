param(
    [string] $ComposeFile = "deploy/docker-compose.prod.yml",
    [string] $EnvFile = "deploy/.env.prod",
    [ValidateSet("quick", "full")]
    [string] $GateMode = "full",
    [switch] $AllowDirty,
    [switch] $SkipQualityGate,
    [switch] $SkipImageBuild
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Resolve-ProjectPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $Root $Path
}

$ComposePath = Resolve-ProjectPath $ComposeFile
$EnvPath = Resolve-ProjectPath $EnvFile

if (-not (Test-Path $ComposePath)) {
    throw "Compose file not found: $ComposePath"
}
if (-not (Test-Path $EnvPath)) {
    throw "Environment file not found: $EnvPath"
}

Push-Location $Root
try {
    $status = git status --porcelain
    if ($status -and -not $AllowDirty) {
        throw "Working tree is dirty. Commit or stash changes, or pass -AllowDirty for a non-release dry run."
    }

    if (-not $SkipQualityGate) {
        if ($GateMode -eq "full") {
            pnpm verify:full
        } else {
            pnpm verify
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Quality gate failed"
        }
    }

    docker compose --env-file $EnvPath -f $ComposePath config -q
    if ($LASTEXITCODE -ne 0) {
        throw "Production compose config is invalid"
    }

    if (-not $SkipImageBuild) {
        docker compose --env-file $EnvPath -f $ComposePath build server web
        if ($LASTEXITCODE -ne 0) {
            throw "Production image build failed"
        }
    }

    Write-Host "Release check passed"
} finally {
    Pop-Location
}
