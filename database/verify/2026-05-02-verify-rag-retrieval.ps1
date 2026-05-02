param(
    [string]$BaseUrl = "http://127.0.0.1:10084",
    [string]$AdminToken = $env:APP_AI_KNOWLEDGE_ADMIN_TOKEN
)

$ErrorActionPreference = "Stop"

function Get-AttributeValue {
    param(
        [object]$Attributes,
        [string]$Key
    )

    if ($null -eq $Attributes) {
        return $null
    }
    $property = $Attributes.PSObject.Properties[$Key]
    if ($null -eq $property) {
        return $null
    }
    return [string]$property.Value
}

function Test-ExpectedAttributes {
    param(
        [object]$Hit,
        [hashtable]$Expected
    )

    foreach ($key in $Expected.Keys) {
        $actual = Get-AttributeValue -Attributes $Hit.attributes -Key $key
        if ($actual -ne [string]$Expected[$key]) {
            return $false
        }
    }
    return $true
}

function Invoke-KnowledgeSearch {
    param(
        [hashtable]$Case
    )

    $headers = @{
        "X-Trace-Id" = "verify-rag-retrieval-2026-05-02"
        "X-Chat-Id" = "verify-rag"
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

    Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/knowledge/search" `
        -ContentType "application/json; charset=utf-8" `
        -Headers $headers `
        -Body $body
}

$cases = @(
    @{
        name = "triage-respiratory"
        namespace = "default-triage-knowledge"
        query = "fever cough sore throat which department"
        topK = 4
        minScore = 0.0
        expected = @{ departmentCode = "RESP" }
    },
    @{
        name = "guide-materials"
        namespace = "default-guide-knowledge"
        query = "outpatient visit ID card insurance card materials"
        topK = 3
        minScore = 0.0
        expected = @{ sourceId = "guide-baseline" }
    },
    @{
        name = "registration-create-policy"
        namespace = "default-registration-policy"
        query = "what information must be confirmed before creating an appointment"
        topK = 4
        minScore = 0.0
        expected = @{ actionTag = "CREATE" }
    }
)

$results = foreach ($case in $cases) {
    $response = Invoke-KnowledgeSearch -Case $case
    if ($response.status -ne "HIT") {
        throw "RAG case [$($case.name)] expected HIT but got [$($response.status)]."
    }
    if ($response.hits.Count -le 0) {
        throw "RAG case [$($case.name)] returned no hits."
    }
    $matched = $false
    foreach ($hit in $response.hits) {
        if (Test-ExpectedAttributes -Hit $hit -Expected $case.expected) {
            $matched = $true
            break
        }
    }
    if (-not $matched) {
        $expectedJson = $case.expected | ConvertTo-Json -Compress
        $hitIds = ($response.hits | ForEach-Object { $_.id }) -join ","
        throw "RAG case [$($case.name)] did not match expected attributes $expectedJson. hitIds=[$hitIds]"
    }

    [pscustomobject]@{
        name = $case.name
        namespace = $case.namespace
        status = $response.status
        hitCount = $response.hits.Count
        bestHitId = $response.hits[0].id
        bestScore = $response.hits[0].score
    }
}

$results
