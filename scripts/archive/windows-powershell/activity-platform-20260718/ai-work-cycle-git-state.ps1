function Get-WorkCycleGitStatusPaths {
    param([Parameter(Mandatory = $true)][string] $RepositoryRoot)

    if (-not (Get-Command git -ErrorAction SilentlyContinue) -or -not (Test-Path (Join-Path $RepositoryRoot ".git"))) {
        return @()
    }

    Push-Location $RepositoryRoot
    try {
        $status = git status --porcelain -uall
        if ($LASTEXITCODE -ne 0) {
            throw "git status failed with exit code $LASTEXITCODE"
        }
        if (-not $status) {
            return @()
        }
        return @($status | ForEach-Object {
            $path = $_.Substring(3).Trim()
            if ($path -match " -> ") {
                $path = ($path -split " -> ")[1]
            }
            $path.Replace("\", "/")
        })
    } finally {
        Pop-Location
    }
}

function Get-WorkCycleHeadCommit {
    param([Parameter(Mandatory = $true)][string] $RepositoryRoot)

    if (-not (Get-Command git -ErrorAction SilentlyContinue) -or -not (Test-Path (Join-Path $RepositoryRoot ".git"))) {
        return ""
    }

    Push-Location $RepositoryRoot
    try {
        $commit = git rev-parse HEAD
        if ($LASTEXITCODE -ne 0) {
            throw "git rev-parse HEAD failed with exit code $LASTEXITCODE"
        }
        return ([string] $commit).Trim()
    } finally {
        Pop-Location
    }
}

function Get-WorkCycleCommittedPaths {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [string] $BaselineCommit
    )

    if ([string]::IsNullOrWhiteSpace($BaselineCommit)) {
        return @()
    }

    Push-Location $RepositoryRoot
    try {
        git cat-file -e "$BaselineCommit^{commit}" 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "Work-cycle baseline commit is unavailable: $BaselineCommit"
        }
        $paths = git diff --name-only --diff-filter=ACDMRTUXB "$BaselineCommit..HEAD" --
        if ($LASTEXITCODE -ne 0) {
            throw "git diff from work-cycle baseline failed with exit code $LASTEXITCODE"
        }
        return @($paths | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_.Replace("\", "/") })
    } finally {
        Pop-Location
    }
}

function Get-WorkCycleFileSignature {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [Parameter(Mandatory = $true)][string] $Path
    )

    $fullPath = Join-Path $RepositoryRoot $Path
    if (-not (Test-Path -LiteralPath $fullPath)) {
        return "<missing>"
    }

    $item = Get-Item -LiteralPath $fullPath
    if ($item.PSIsContainer) {
        return "<directory>"
    }
    if ($item.Length -le 2MB -and $item.Extension -notin @(".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".class", ".zip", ".gz")) {
        $stream = [System.IO.File]::OpenRead($fullPath)
        try {
            $sha256 = [System.Security.Cryptography.SHA256]::Create()
            try {
                return ([System.BitConverter]::ToString($sha256.ComputeHash($stream))).Replace("-", "")
            } finally {
                $sha256.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    }
    return "$($item.Length):$($item.LastWriteTimeUtc.Ticks)"
}

function Get-WorkCycleFileSignatures {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [string[]] $Paths = @()
    )

    $signatures = [ordered]@{}
    foreach ($path in @($Paths | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)) {
        $normalized = $path.Replace("\", "/")
        $signatures[$normalized] = Get-WorkCycleFileSignature -RepositoryRoot $RepositoryRoot -Path $normalized
    }
    return $signatures
}

function Get-ChangedWorkCyclePaths {
    param(
        [Parameter(Mandatory = $true)][string] $RepositoryRoot,
        [object] $Context = $null
    )

    $currentPaths = @(Get-WorkCycleGitStatusPaths -RepositoryRoot $RepositoryRoot)
    $baselineCommit = if ($null -ne $Context -and ($Context.PSObject.Properties.Name -contains "baselineCommit")) {
        [string] $Context.baselineCommit
    } else {
        ""
    }
    $committedPaths = @(Get-WorkCycleCommittedPaths -RepositoryRoot $RepositoryRoot -BaselineCommit $baselineCommit)

    if ($null -eq $Context -or -not ($Context.PSObject.Properties.Name -contains "baselineFileSignatures")) {
        return @($currentPaths + $committedPaths | Sort-Object -Unique)
    }

    $baseline = $Context.baselineFileSignatures
    $baselinePaths = @($baseline.PSObject.Properties.Name)
    $candidatePaths = @($currentPaths + $committedPaths + $baselinePaths | Sort-Object -Unique)
    return @($candidatePaths | Where-Object {
        $path = $_
        if ($baseline.PSObject.Properties.Name -contains $path) {
            $currentSignature = Get-WorkCycleFileSignature -RepositoryRoot $RepositoryRoot -Path $path
            $baselineSignature = [string] $baseline.PSObject.Properties[$path].Value
            return $currentSignature -ne $baselineSignature
        }
        return ($currentPaths -contains $path) -or ($committedPaths -contains $path)
    })
}
