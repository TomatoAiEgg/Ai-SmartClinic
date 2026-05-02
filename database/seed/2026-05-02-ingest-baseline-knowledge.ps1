param(
    [string]$BaseUrl = "http://127.0.0.1:10084",
    [string]$AdminToken = $env:APP_AI_KNOWLEDGE_ADMIN_TOKEN
)

$ErrorActionPreference = "Stop"

function New-Chunk {
    param(
        [int]$Index,
        [string]$Title,
        [string]$Content,
        [hashtable]$Metadata,
        [string]$ChunkType = "TEXT"
    )

    return @{
        chunkIndex = $Index
        chunkType = $ChunkType
        title = $Title
        content = $Content
        metadata = $Metadata
    }
}

function Invoke-KnowledgeIngest {
    param(
        [hashtable]$Payload
    )

    $json = $Payload | ConvertTo-Json -Depth 20
    $headers = @{ "X-Trace-Id" = "seed-knowledge-2026-05-02" }
    if (-not [string]::IsNullOrWhiteSpace($AdminToken)) {
        $headers["X-Knowledge-Admin-Token"] = $AdminToken
    }
    Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/knowledge/ingest" `
        -ContentType "application/json; charset=utf-8" `
        -Headers $headers `
        -Body $json
}

$triageChunks = @(
    New-Chunk 0 "呼吸内科分诊：咳嗽发热咽痛" "患者主诉咳嗽、发热、咽痛、痰多、胸闷或气喘，且没有胸痛、意识障碍、大出血等急症红线时，优先建议呼吸内科。若发热伴持续高热、明显气促或血氧异常，应提示尽快线下急诊评估。" @{ departmentCode = "RESP"; departmentName = "呼吸内科"; emergency = $false; symptomGroup = "respiratory" } "TRIAGE"
    New-Chunk 1 "急诊分诊：胸痛呼吸困难意识异常" "出现胸痛、严重呼吸困难、意识不清、抽搐、大出血、突发肢体无力或疑似卒中等高风险症状时，不建议普通门诊挂号，应提示立即前往急诊或拨打急救电话。急诊只作为安全兜底，不参与普通号源预约。" @{ departmentCode = "ER"; departmentName = "急诊"; emergency = $true; symptomGroup = "emergency-redline" } "TRIAGE"
    New-Chunk 2 "消化内科分诊：腹痛腹泻呕吐反酸" "胃痛、腹痛、腹泻、呕吐、反酸、胃胀、食欲下降等消化系统症状，未出现呕血、黑便、剧烈持续腹痛或脱水休克表现时，优先建议消化内科。出现黑便、呕血或剧烈腹痛时需提示急诊评估。" @{ departmentCode = "GI"; departmentName = "消化内科"; emergency = $false; symptomGroup = "digestive" } "TRIAGE"
    New-Chunk 3 "皮肤科分诊：皮疹瘙痒过敏湿疹" "皮疹、瘙痒、过敏、湿疹、荨麻疹、痤疮、皮肤红肿疼痛等皮肤问题，未出现喉头水肿、呼吸困难或全身严重过敏反应时，优先建议皮肤科。伴呼吸困难或面唇舌肿胀时应提示急诊。" @{ departmentCode = "DERM"; departmentName = "皮肤科"; emergency = $false; symptomGroup = "skin" } "TRIAGE"
    New-Chunk 4 "儿科分诊：儿童常见病" "当就诊对象为儿童、婴幼儿或家长描述孩子发热、咳嗽、腹泻、皮疹等常见问题时，优先建议儿科。若儿童出现精神反应差、抽搐、呼吸困难、持续高热不退等情况，应提示急诊或儿科急诊。" @{ departmentCode = "PED"; departmentName = "儿科"; emergency = $false; symptomGroup = "pediatric" } "TRIAGE"
    New-Chunk 5 "妇科分诊：月经异常孕期咨询" "月经异常、痛经、阴道分泌物异常、妇科炎症、备孕咨询、孕早期普通咨询等女性健康相关问题，优先建议妇科。孕期出现剧烈腹痛、大量出血、晕厥等情况应提示急诊或产科急诊。" @{ departmentCode = "GYN"; departmentName = "妇科"; emergency = $false; symptomGroup = "gynecology" } "TRIAGE"
    New-Chunk 6 "眼科分诊：眼痛眼红视物模糊" "眼痛、眼红、流泪、视物模糊、视力下降、异物感等眼部问题，优先建议眼科。突发视力明显下降、眼外伤、化学品入眼或剧烈眼痛伴头痛恶心时，应提示尽快线下急诊或眼科急诊。" @{ departmentCode = "OPH"; departmentName = "眼科"; emergency = $false; symptomGroup = "ophthalmology" } "TRIAGE"
    New-Chunk 7 "神经内科分诊：头痛头晕眩晕麻木" "头痛、头晕、眩晕、偏头痛、肢体麻木、睡眠相关神经系统不适，未出现突发偏瘫、言语不清、意识障碍或抽搐时，优先建议神经内科。疑似卒中或意识异常应提示急诊。" @{ departmentCode = "NEURO"; departmentName = "神经内科"; emergency = $false; symptomGroup = "neurology" } "TRIAGE"
    New-Chunk 8 "全科兜底：症状不明确或慢病复诊" "症状不明确、多个轻症混杂、慢病复诊、体检指标咨询或用户无法判断科室时，可先建议全科医学科进行初步评估。全科建议不能覆盖急症红线，出现高风险症状仍需急诊。" @{ departmentCode = "GEN"; departmentName = "全科医学科"; emergency = $false; symptomGroup = "general" } "TRIAGE"
)

$guideChunks = @(
    New-Chunk 0 "医保和身份证材料" "普通门诊就诊建议携带身份证、医保卡或医保电子凭证。儿童、老人或代办场景建议同时准备监护人或代办人身份证明。医保政策以当地医保部门和医院窗口解释为准。" @{ sourceId = "guide-baseline"; sourceName = "院内导诊基础知识"; topic = "insurance-materials" } "GUIDE"
    New-Chunk 1 "预约后到院流程" "预约成功后，患者应按预约日期和时段提前到院。到院后先完成签到或取号，再根据现场叫号前往对应诊区候诊。若迟到，实际就诊顺序可能受到现场规则影响。" @{ sourceId = "guide-baseline"; sourceName = "院内导诊基础知识"; topic = "arrival-flow" } "GUIDE"
    New-Chunk 2 "取消和改约说明" "如需取消或改约，应尽量在就诊前通过线上入口或医院窗口处理。已经过号、已签到或已产生费用的订单，是否允许取消、退费或改约需以实际业务状态和医院规则为准。" @{ sourceId = "guide-baseline"; sourceName = "院内导诊基础知识"; topic = "cancel-reschedule" } "GUIDE"
    New-Chunk 3 "检查检验和报告查询" "医生开具检查检验后，患者应按指引到对应科室完成检查。报告出具时间因项目不同而不同，线上可查询的报告以系统开放范围为准；异常结果应回到医生处解读。" @{ sourceId = "guide-baseline"; sourceName = "院内导诊基础知识"; topic = "lab-report" } "GUIDE"
    New-Chunk 4 "急症安全提示" "导诊问答中如果用户描述胸痛、严重呼吸困难、意识不清、抽搐、大出血、突发偏瘫等症状，应优先提示急诊或急救，不应只给普通门诊路线。" @{ sourceId = "guide-baseline"; sourceName = "院内导诊基础知识"; topic = "safety" } "GUIDE"
)

$policyChunks = @(
    New-Chunk 0 "挂号创建前确认规则" "创建挂号前必须确认就诊人、科室、医生或号源、就诊日期和时段。用户未明确确认时，只能返回挂号预览，不得直接创建正式挂号订单。" @{ sourceId = "registration-policy-baseline"; sourceName = "挂号策略基础规则"; policyType = "CONFIRMATION"; actionTag = "CREATE"; validFrom = "2026-01-01"; validTo = "" } "POLICY"
    New-Chunk 1 "号源查询规则" "查询号源时应以 schedule MCP 或数据库中的真实号源为准。RAG 只提供解释性规则，不得编造医生、余号、费用或排班时间。" @{ sourceId = "registration-policy-baseline"; sourceName = "挂号策略基础规则"; policyType = "DATA_BOUNDARY"; actionTag = "QUERY"; validFrom = "2026-01-01"; validTo = "" } "POLICY"
    New-Chunk 2 "取消挂号规则" "取消挂号必须校验订单归属、订单状态和用户确认。未确认取消时只说明影响和下一步；已完成、已取消或不存在的订单不能重复取消。" @{ sourceId = "registration-policy-baseline"; sourceName = "挂号策略基础规则"; policyType = "CANCEL"; actionTag = "CANCEL"; validFrom = "2026-01-01"; validTo = "" } "POLICY"
    New-Chunk 3 "改约规则" "改约应先确认原订单可改约，再确认新号源可用。改约过程中不得丢失原订单上下文；释放旧号源或锁定新号源失败时，应明确告诉用户当前状态并保留可追踪结果。" @{ sourceId = "registration-policy-baseline"; sourceName = "挂号策略基础规则"; policyType = "RESCHEDULE"; actionTag = "RESCHEDULE"; validFrom = "2026-01-01"; validTo = "" } "POLICY"
    New-Chunk 4 "通用业务边界" "挂号 Agent 只能依据结构化业务数据和 MCP 返回结果生成结论。RAG 命中的政策内容用于补充解释和风险提示，不能替代订单状态、患者身份、真实号源和支付退费结果。" @{ sourceId = "registration-policy-baseline"; sourceName = "挂号策略基础规则"; policyType = "BOUNDARY"; actionTag = "ALL"; validFrom = "2026-01-01"; validTo = "" } "POLICY"
)

$requests = @(
    @{
        namespace = "default-triage-knowledge"
        sourceId = "triage-baseline-2026-05"
        sourceName = "分诊基础知识 2026-05"
        embeddingModel = "text-embedding-v2"
        embeddingDimensions = 1536
        documents = @(
            @{
                sourceId = "triage-baseline-2026-05"
                sourceName = "分诊基础知识 2026-05"
                documentType = "TRIAGE"
                title = "分诊基础知识"
                version = "2026-05-02"
                metadata = @{ sourceId = "triage-baseline-2026-05"; sourceName = "分诊基础知识 2026-05" }
                chunks = $triageChunks
            }
        )
        metadata = @{ batch = "baseline-knowledge-2026-05-02"; owner = "knowledge-service" }
    },
    @{
        namespace = "default-guide-knowledge"
        sourceId = "guide-baseline-2026-05"
        sourceName = "导诊基础知识 2026-05"
        embeddingModel = "text-embedding-v2"
        embeddingDimensions = 1536
        documents = @(
            @{
                sourceId = "guide-baseline-2026-05"
                sourceName = "导诊基础知识 2026-05"
                documentType = "GUIDE"
                title = "导诊基础知识"
                version = "2026-05-02"
                metadata = @{ sourceId = "guide-baseline-2026-05"; sourceName = "导诊基础知识 2026-05" }
                chunks = $guideChunks
            }
        )
        metadata = @{ batch = "baseline-knowledge-2026-05-02"; owner = "knowledge-service" }
    },
    @{
        namespace = "default-registration-policy"
        sourceId = "registration-policy-baseline-2026-05"
        sourceName = "挂号策略基础规则 2026-05"
        embeddingModel = "text-embedding-v2"
        embeddingDimensions = 1536
        documents = @(
            @{
                sourceId = "registration-policy-baseline-2026-05"
                sourceName = "挂号策略基础规则 2026-05"
                documentType = "REGISTRATION_POLICY"
                title = "挂号策略基础规则"
                version = "2026-05-02"
                metadata = @{ sourceId = "registration-policy-baseline-2026-05"; sourceName = "挂号策略基础规则 2026-05" }
                chunks = $policyChunks
            }
        )
        metadata = @{ batch = "baseline-knowledge-2026-05-02"; owner = "knowledge-service" }
    }
)

foreach ($request in $requests) {
    $result = Invoke-KnowledgeIngest -Payload $request
    [pscustomobject]@{
        namespace = $request.namespace
        jobId = $result.jobId
        status = $result.status
        documents = $result.documentCount
        chunks = $result.chunkCount
    }
}
