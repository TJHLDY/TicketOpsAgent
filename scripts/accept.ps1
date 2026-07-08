param(
    [switch]$SkipMavenTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$reportDir = Join-Path $repoRoot 'target\agent-eval'
$evalReportPath = Join-Path $reportDir 'llm-shadow-eval.json'
$acceptanceReportPath = Join-Path $reportDir 'acceptance-report.md'

function Invoke-CommandInRepo {
    param([scriptblock]$Command)

    Push-Location $repoRoot
    try {
        & $Command
    }
    finally {
        Pop-Location
    }
}

function Get-GitValue {
    param([string[]]$Arguments)

    Push-Location $repoRoot
    try {
        $value = & git @Arguments 2>$null
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
            return 'unknown'
        }
        return ($value | Select-Object -First 1).Trim()
    }
    finally {
        Pop-Location
    }
}

function Invoke-SecretScan {
    $patterns = @(
        'sk-[A-Za-z0-9_-]{20,}',
        ('DEEPSEEK' + '_API_KEY=.*' + 'sk-'),
        ('OPENAI' + '_API_KEY=.*' + 'sk-'),
        ('Authorization: ' + 'Bearer')
    )

    Push-Location $repoRoot
    try {
        $matches = New-Object System.Collections.Generic.List[string]
        foreach ($pattern in $patterns) {
            $output = & rg -n $pattern -g '!target/**' -g '!.git/**' . 2>$null
            if ($LASTEXITCODE -eq 0) {
                foreach ($line in $output) {
                    $matches.Add($line)
                }
            }
            elseif ($LASTEXITCODE -gt 1) {
                throw "Secret scan failed while running rg pattern: $pattern"
            }
        }

        return $matches
    }
    finally {
        Pop-Location
    }
}

function Add-FallbackReasonLines {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [object]$Distribution
    )

    $properties = $Distribution.PSObject.Properties | Sort-Object Name
    if ($properties.Count -eq 0) {
        $Lines.Add('- none')
        return
    }

    foreach ($property in $properties) {
        $Lines.Add("- $($property.Name): $($property.Value)")
    }
}

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$mavenStatus = 'SKIPPED'
if (-not $SkipMavenTest) {
    Invoke-CommandInRepo { & mvn test }
    if ($LASTEXITCODE -ne 0) {
        $mavenStatus = 'FAIL'
    }
    else {
        $mavenStatus = 'PASS'
    }
}

$secretMatches = @(Invoke-SecretScan)
$secretStatus = if ($secretMatches.Count -eq 0) { 'PASS' } else { 'FAIL' }

if (-not (Test-Path -LiteralPath $evalReportPath)) {
    throw "Missing LLM shadow eval report: $evalReportPath"
}

$evalReport = Get-Content -LiteralPath $evalReportPath -Raw | ConvertFrom-Json
$branch = Get-GitValue @('rev-parse', '--abbrev-ref', 'HEAD')
$commit = Get-GitValue @('rev-parse', '--short', 'HEAD')
$generatedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz')

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# TicketOpsAgent Acceptance Report')
$lines.Add('')
$lines.Add("- Generated: $generatedAt")
$lines.Add("- Branch: $branch")
$lines.Add("- Commit: $commit")
$lines.Add('')
$lines.Add('## Gates')
$lines.Add('')
$lines.Add("- mvn test: $mavenStatus")
$lines.Add("- Secret scan: $secretStatus")
$lines.Add('- Shadow eval report: PASS')
$lines.Add('')
$lines.Add('## Shadow Eval Metrics')
$lines.Add('')
$lines.Add("- totalCases: $($evalReport.totalCases)")
$lines.Add("- parseSuccessCount: $($evalReport.parseSuccessCount)")
$lines.Add("- validationSuccessCount: $($evalReport.validationSuccessCount)")
$lines.Add("- fallbackCount: $($evalReport.fallbackCount)")
$lines.Add("- safetyPassCount: $($evalReport.safetyPassCount) of $($evalReport.safetyCaseCount)")
$lines.Add("- userVisibleChangedCount: $($evalReport.userVisibleChangedCount)")
$lines.Add('')
$lines.Add('## Fallback Reason Distribution')
$lines.Add('')
Add-FallbackReasonLines -Lines $lines -Distribution $evalReport.fallbackReasonDistribution
$lines.Add('')
$lines.Add('## Current boundaries')
$lines.Add('')
$lines.Add('- No real enterprise system integration.')
$lines.Add('- No real LDAP, SSO, OA, IAM, or approval workflow integration.')
$lines.Add('- No automatic unlock, password reset, permission grant, dispatch, or ticket close.')
$lines.Add('- DeepSeek/LLM output remains shadow-only and does not change the user-facing deterministic response.')
$lines.Add('- Current SOP retrieval is keyword/table driven, not vector RAG.')
$lines.Add('')
$lines.Add('## Known limits')
$lines.Add('')
$lines.Add('- Mock shadow eval proves parser, validator, fallback, and safety boundaries; it does not prove production LLM quality.')
$lines.Add('- Real DeepSeek live checks remain optional manual smoke tests.')
$lines.Add('- Promotion to hybrid or LLM mode is out of scope until shadow metrics and review are stable.')

if ($secretMatches.Count -gt 0) {
    $lines.Add('')
    $lines.Add('## Secret Scan Matches')
    foreach ($match in $secretMatches) {
        $lines.Add("- $match")
    }
}

Set-Content -LiteralPath $acceptanceReportPath -Value $lines -Encoding utf8
Write-Host "Acceptance report: $acceptanceReportPath"

if ($mavenStatus -eq 'FAIL' -or $secretStatus -eq 'FAIL') {
    exit 1
}
