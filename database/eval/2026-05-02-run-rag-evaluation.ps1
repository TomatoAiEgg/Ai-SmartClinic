param(
    [string]$BaseUrl = "http://127.0.0.1:10084",
    [string]$CasesPath = (Join-Path $PSScriptRoot "rag-evaluation-cases-2026-05-02.json"),
    [string]$AdminToken = $env:APP_AI_KNOWLEDGE_ADMIN_TOKEN,
    [string]$OutputPath = "",
    [string]$ResultUploadUrl = "",
    [string[]]$Group = @(),
    [string[]]$CaseId = @(),
    [switch]$ListCases,
    [switch]$UploadResult,
    [switch]$NoFailOnMismatch
)

$ErrorActionPreference = "Stop"

function Get-PropertyValue {
    param(
        [object]$Object,
        [string]$Name,
        [object]$Default = $null
    )

    if ($null -eq $Object) {
        return $Default
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $Default
    }
    return $property.Value
}

function ConvertTo-ObjectArray {
    param([object]$Value)

    if ($null -eq $Value) {
        return @()
    }
    if ($Value -is [System.Array]) {
        return $Value
    }
    return @($Value)
}

function Expand-RagEvaluationCases {
    param([object]$Definition)

    $defaults = Get-PropertyValue -Object $Definition -Name "defaults" -Default ([pscustomobject]@{})
    $cases = New-Object System.Collections.Generic.List[object]

    foreach ($suite in (ConvertTo-ObjectArray (Get-PropertyValue -Object $Definition -Name "suites" -Default @()))) {
        $suiteGroup = [string](Get-PropertyValue -Object $suite -Name "group" -Default "")
        $suiteNamespace = [string](Get-PropertyValue -Object $suite -Name "namespace" -Default "")
        foreach ($expectation in (ConvertTo-ObjectArray (Get-PropertyValue -Object $suite -Name "expectations" -Default @()))) {
            $expectationName = [string](Get-PropertyValue -Object $expectation -Name "name" -Default "case")
            $queries = ConvertTo-ObjectArray (Get-PropertyValue -Object $expectation -Name "queries" -Default @())
            $index = 1
            foreach ($queryItem in $queries) {
                if ($queryItem -is [string]) {
                    $query = $queryItem
                    $queryId = "{0:D2}" -f $index
                } else {
                    $query = [string](Get-PropertyValue -Object $queryItem -Name "query" -Default "")
                    $queryId = [string](Get-PropertyValue -Object $queryItem -Name "id" -Default ("{0:D2}" -f $index))
                }

                $case = [pscustomobject]@{
                    id = "{0}-{1}-{2}" -f $suiteGroup, $expectationName, $queryId
                    group = $suiteGroup
                    namespace = $suiteNamespace
                    query = $query
                    topK = [int](Get-PropertyValue -Object $expectation -Name "topK" -Default (Get-PropertyValue -Object $suite -Name "topK" -Default (Get-PropertyValue -Object $defaults -Name "topK" -Default 5)))
                    minScore = [double](Get-PropertyValue -Object $expectation -Name "minScore" -Default (Get-PropertyValue -Object $suite -Name "minScore" -Default (Get-PropertyValue -Object $defaults -Name "minScore" -Default 0.0)))
                    expectedStatus = [string](Get-PropertyValue -Object $expectation -Name "expectedStatus" -Default (Get-PropertyValue -Object $suite -Name "expectedStatus" -Default (Get-PropertyValue -Object $defaults -Name "expectedStatus" -Default "HIT")))
                    expectedAnyHitAttributes = Get-PropertyValue -Object $expectation -Name "expectedAnyHitAttributes" -Default $null
                    maxExpectedRank = [int](Get-PropertyValue -Object $expectation -Name "maxExpectedRank" -Default (Get-PropertyValue -Object $suite -Name "maxExpectedRank" -Default (Get-PropertyValue -Object $defaults -Name "maxExpectedRank" -Default 5)))
                    minBestScore = [double](Get-PropertyValue -Object $expectation -Name "minBestScore" -Default (Get-PropertyValue -Object $suite -Name "minBestScore" -Default (Get-PropertyValue -Object $defaults -Name "minBestScore" -Default 0.0)))
                }
                $cases.Add($case)
                $index++
            }
        }
    }

    return $cases
}

function Get-AttributeValue {
    param(
        [object]$Attributes,
        [string]$Key
    )

    if ($null -eq $Attributes) {
        return $null
    }
    if ($Attributes -is [System.Collections.IDictionary]) {
        return $Attributes[$Key]
    }
    $property = $Attributes.PSObject.Properties[$Key]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Test-ExpectedAttributes {
    param(
        [object]$Hit,
        [object]$Expected
    )

    if ($null -eq $Expected) {
        return $true
    }
    foreach ($property in $Expected.PSObject.Properties) {
        $actual = Get-AttributeValue -Attributes $Hit.attributes -Key $property.Name
        if ([string]$actual -ne [string]$property.Value) {
            return $false
        }
    }
    return $true
}

function Find-MatchedRank {
    param(
        [object[]]$Hits,
        [object]$Expected,
        [int]$MaxRank
    )

    if ($null -eq $Expected) {
        return 1
    }
    $limit = [Math]::Min($Hits.Count, $MaxRank)
    for ($index = 0; $index -lt $limit; $index++) {
        if (Test-ExpectedAttributes -Hit $Hits[$index] -Expected $Expected) {
            return $index + 1
        }
    }
    return $null
}

function Invoke-RagEvaluationCase {
    param(
        [object]$Case,
        [string]$TraceId
    )

    $headers = @{
        "X-Trace-Id" = $TraceId
        "X-Chat-Id" = "rag-eval-$($Case.id)"
    }
    if (-not [string]::IsNullOrWhiteSpace($AdminToken)) {
        $headers["X-Knowledge-Admin-Token"] = $AdminToken
    }

    $body = @{
        namespace = $Case.namespace
        query = $Case.query
        topK = $Case.topK
        minScore = $Case.minScore
    } | ConvertTo-Json -Depth 10

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/knowledge/search" `
            -ContentType "application/json; charset=utf-8" `
            -Headers $headers `
            -Body $body
    } catch {
        return [pscustomobject]@{
            id = $Case.id
            group = $Case.group
            namespace = $Case.namespace
            passed = $false
            status = "TRANSPORT_ERROR"
            expectedStatus = $Case.expectedStatus
            hitCount = 0
            matchedRank = $null
            bestScore = $null
            message = $_.Exception.Message
        }
    }

    $hits = ConvertTo-ObjectArray $response.hits
    $bestScore = $null
    if ($hits.Count -gt 0 -and $null -ne $hits[0].score) {
        $bestScore = [double]$hits[0].score
    }
    $matchedRank = Find-MatchedRank -Hits $hits -Expected $Case.expectedAnyHitAttributes -MaxRank $Case.maxExpectedRank

    $messages = New-Object System.Collections.Generic.List[string]
    if ([string]$response.status -ne [string]$Case.expectedStatus) {
        $messages.Add("expected status $($Case.expectedStatus), got $($response.status)")
    }
    if ($Case.expectedStatus -eq "HIT" -and $hits.Count -le 0) {
        $messages.Add("expected at least one hit")
    }
    if ($Case.expectedStatus -eq "HIT" -and $null -eq $matchedRank) {
        $messages.Add("expected attributes not found within top $($Case.maxExpectedRank)")
    }
    if ($null -ne $bestScore -and $bestScore -lt $Case.minBestScore) {
        $messages.Add("best score $bestScore below threshold $($Case.minBestScore)")
    }

    return [pscustomobject]@{
        id = $Case.id
        group = $Case.group
        namespace = $Case.namespace
        passed = $messages.Count -eq 0
        status = [string]$response.status
        expectedStatus = $Case.expectedStatus
        hitCount = $hits.Count
        matchedRank = $matchedRank
        bestScore = $bestScore
        message = ($messages -join "; ")
    }
}

function New-Summary {
    param([object[]]$Results)

    $total = $Results.Count
    $passed = (ConvertTo-ObjectArray ($Results | Where-Object { $_.passed })).Count
    $failed = $total - $passed
    $passRate = if ($total -eq 0) { 0.0 } else { [Math]::Round($passed / $total, 4) }
    return [pscustomobject]@{
        total = $total
        passed = $passed
        failed = $failed
        passRate = $passRate
    }
}

function New-GroupSummary {
    param([object[]]$Results)

    foreach ($item in ($Results | Group-Object group)) {
        $groupResults = ConvertTo-ObjectArray $item.Group
        $summary = New-Summary -Results $groupResults
        [pscustomobject]@{
            group = $item.Name
            total = $summary.total
            passed = $summary.passed
            failed = $summary.failed
            passRate = $summary.passRate
        }
    }
}

$definition = Get-Content -Path $CasesPath -Raw | ConvertFrom-Json
$cases = ConvertTo-ObjectArray (Expand-RagEvaluationCases -Definition $definition)
if ($Group.Count -gt 0) {
    $cases = ConvertTo-ObjectArray ($cases | Where-Object { $Group -contains $_.group })
}
if ($CaseId.Count -gt 0) {
    $cases = ConvertTo-ObjectArray ($cases | Where-Object { $CaseId -contains $_.id })
}

if ($ListCases) {
    Write-Output ("total={0}" -f $cases.Count)
    Write-Output ("casesPath={0}" -f (Resolve-Path $CasesPath).Path)
    foreach ($item in ($cases | Group-Object group | Sort-Object Name)) {
        Write-Output ("group.{0}={1}" -f $item.Name, $item.Count)
    }
    return
}

$traceId = "rag-eval-" + (Get-Date -Format "yyyyMMddHHmmss")
$results = foreach ($case in $cases) {
    Invoke-RagEvaluationCase -Case $case -TraceId $traceId
}

$resultArray = ConvertTo-ObjectArray $results
$summary = New-Summary -Results $resultArray
$byGroup = ConvertTo-ObjectArray (New-GroupSummary -Results $resultArray)

$summary
$byGroup | Sort-Object group | Format-Table -AutoSize
$failedResults = ConvertTo-ObjectArray ($resultArray | Where-Object { -not $_.passed })
if ($failedResults.Count -gt 0) {
    $failedResults | Select-Object id, status, hitCount, matchedRank, bestScore, message | Format-Table -AutoSize
}

$resultPayload = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    traceId = $traceId
    baseUrl = $BaseUrl
    casesPath = (Resolve-Path $CasesPath).Path
    summary = $summary
    byGroup = $byGroup
    results = $resultArray
    metadata = @{
        runner = "database/eval/2026-05-02-run-rag-evaluation.ps1"
        casesVersion = [string](Get-PropertyValue -Object $definition -Name "version" -Default "")
        description = [string](Get-PropertyValue -Object $definition -Name "description" -Default "")
    }
}

if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $directory = Split-Path -Path $OutputPath -Parent
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    $resultPayload | ConvertTo-Json -Depth 20 | Set-Content -Path $OutputPath -Encoding UTF8
}

if ($UploadResult) {
    $uploadUrl = if ([string]::IsNullOrWhiteSpace($ResultUploadUrl)) {
        "$BaseUrl/api/knowledge/evaluations"
    } else {
        $ResultUploadUrl
    }
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($AdminToken)) {
        $headers["X-Knowledge-Admin-Token"] = $AdminToken
    }
    $savedRun = Invoke-RestMethod `
        -Method Post `
        -Uri $uploadUrl `
        -ContentType "application/json; charset=utf-8" `
        -Headers $headers `
        -Body ($resultPayload | ConvertTo-Json -Depth 20)
    Write-Output ("uploadedEvaluationRunId={0}" -f $savedRun.id)
}

if ($summary.failed -gt 0 -and -not $NoFailOnMismatch) {
    exit 1
}
