param(
    [switch] $SkipReport
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$ReportDir = Join-Path $Root ".local-reports"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportPath = Join-Path $ReportDir "security-audit-gate-$Timestamp.md"
$Failures = New-Object System.Collections.Generic.List[string]
$Results = New-Object System.Collections.Generic.List[string]

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

function Add-Result {
    param([string] $Message)
    $Results.Add($Message) | Out-Null
    Write-Host $Message
}

function Add-Failure {
    param([string] $Message)
    $Failures.Add($Message) | Out-Null
    Write-Error $Message -ErrorAction Continue
}

function Assert-FileContains {
    param(
        [string] $Path,
        [string] $Pattern,
        [string] $Message
    )
    $fullPath = Join-Path $Root $Path
    if (-not (Test-Path $fullPath)) {
        Add-Failure "$Path is missing"
        return
    }
    $content = Get-Content -LiteralPath $fullPath -Raw
    if ($content -notmatch $Pattern) {
        Add-Failure $Message
    } else {
        Add-Result "- PASS: $Message"
    }
}

function Assert-FileNotContains {
    param(
        [string] $Path,
        [string] $Pattern,
        [string] $Message
    )
    $fullPath = Join-Path $Root $Path
    if (-not (Test-Path $fullPath)) {
        Add-Failure "$Path is missing"
        return
    }
    $content = Get-Content -LiteralPath $fullPath -Raw
    if ($content -match $Pattern) {
        Add-Failure $Message
    } else {
        Add-Result "- PASS: $Message"
    }
}

Assert-FileContains "server\pom.xml" "<spring\.profiles\.active>test</spring\.profiles\.active>" "Backend tests run with the isolated test profile"
Assert-FileContains "server\src\main\resources\application-test.yml" "jdbc:tc:postgresql:16:///colla_platform_test" "Test profile uses Testcontainers PostgreSQL"
Assert-FileContains "server\src\main\resources\application-prod.yml" '\$\{JWT_ACCESS_SECRET\}' "Production access-token secret must come from environment"
Assert-FileContains "server\src\main\resources\application-prod.yml" '\$\{JWT_REFRESH_SECRET\}' "Production refresh-token secret must come from environment"
Assert-FileNotContains "server\src\main\resources\application-prod.yml" "change-me-|admin123456|colla_dev_password|colla_minio_password" "Production profile does not embed local/default credentials"
Assert-FileContains "server\src\main\java\com\colla\platform\config\SecurityConfig.java" "\.anyRequest\(\)\.authenticated\(\)" "Security config authenticates non-public routes"
Assert-FileContains "server\src\main\java\com\colla\platform\modules\audit\application\AuditService.java" "requireManageUsers" "Audit log query remains admin-managed"

$auditServices = @(
    "server\src\main\java\com\colla\platform\modules\identity\application\AuthService.java",
    "server\src\main\java\com\colla\platform\modules\identity\application\MemberService.java",
    "server\src\main\java\com\colla\platform\modules\project\application\ProjectService.java",
    "server\src\main\java\com\colla\platform\modules\knowledge\application\KnowledgeContentService.java",
    "server\src\main\java\com\colla\platform\modules\base\application\BaseService.java",
    "server\src\main\java\com\colla\platform\modules\approval\application\ApprovalService.java",
    "server\src\main\java\com\colla\platform\modules\file\application\FileService.java"
)
foreach ($service in $auditServices) {
    Assert-FileContains $service "auditService\.log\(" "$service writes audit events"
}

if (-not $SkipReport) {
    $status = if ($Failures.Count -eq 0) { "PASS" } else { "FAIL" }
    $report = @(
        "# Security Audit Gate",
        "",
        "- Status: $status",
        "- Time: $(Get-Date -Format o)",
        "",
        "## Results"
    ) + $Results + @(
        "",
        "## Failures"
    )
    if ($Failures.Count -eq 0) {
        $report += "- None"
    } else {
        $report += $Failures
    }
    Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding UTF8
    Write-Host "Security audit gate report: $ReportPath"
}

if ($Failures.Count -gt 0) {
    exit 1
}
