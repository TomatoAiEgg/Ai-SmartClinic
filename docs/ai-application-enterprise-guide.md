# AI SmartClinic 学习、扩展与企业级落地指南

这份文档是本项目的主文档。后续学习 AI 应用、扩展业务功能、判断代码应该写在哪一层、以及把项目演进成企业级系统，都优先看这一份。

本项目不要理解成“一个 AI 聊天项目”。更准确的定位是：

```text
自然语言入口 + 多 Agent 编排 + MCP 工具执行 + 真实业务系统适配 + 企业级治理
```

也就是说，AI 的价值不是“会聊天”，而是把患者自然表达转成可执行的业务动作：分诊、导诊、查号源、生成挂号预览、确认挂号、查询、取消、改约。

## 1. 先建立正确认知

### 1.1 AI 应用不是只调大模型

一个能落地的 AI 应用通常由这些部分组成：

```text
用户输入
  -> 意图识别
  -> 信息抽取
  -> 多轮补问
  -> 工具调用
  -> 业务规则校验
  -> 用户确认
  -> 写入真实系统
  -> 审计与监控
```

大模型只负责其中一部分：理解、抽取、生成、解释、辅助决策。

真正决定系统能不能企业级落地的是：

- 真实数据从哪里来
- 写操作是否可确认、可审计、可补偿
- 模型失败时系统是否还能稳定运行
- 业务规则是否可控
- 服务是否可观测、可配置、可扩展

### 1.2 这个项目适合学什么

通过这个项目，你应该重点学习 6 件事：

- 如何把自然语言入口接到真实业务流程
- 如何用 `supervisor-agent` 做业务路由
- 如何把具体能力拆到不同 Agent
- 如何用 MCP Server 承接真实系统工具调用
- 如何做模型配置、模型降级、Nacos 配置治理
- 如何把演示项目逐步改成企业级项目

### 1.3 不要一开始就追复杂概念

学习顺序建议是：

```text
先跑通文本挂号
  -> 再补会话状态
  -> 再接真实数据库
  -> 再接真实 HIS / 排班 / 患者系统
  -> 再增强 LLM Routing / ReAct / RAG / 语音
  -> 最后做监控、审计、安全、限流、容灾
```

不要一开始就把所有概念都堆上去。企业级不是概念多，而是边界清楚、行为稳定、数据真实、可维护。

## 2. 当前项目结构怎么理解

### 2.1 总体链路

当前项目的核心链路是：

```text
frontend-miniapp
  -> gateway-chat-service
  -> supervisor-agent
      -> triage-agent
      -> registration-agent
      -> guide-agent
  -> MCP Server
      -> patient-mcp-server
      -> schedule-mcp-server
      -> registration-mcp-server
```

更接近企业落地后的结构是：

```text
患者 / 小程序
    |
    v
gateway-chat-service
    |
    v
supervisor-agent
    |
    +-- triage-agent
    |     |
    |     +-- 医学分诊规则 / 知识库
    |
    +-- guide-agent
    |     |
    |     +-- 医院知识库 / 科室位置 / 就诊流程
    |
    +-- registration-agent
          |
          +-- patient-mcp-server
          +-- schedule-mcp-server
          +-- registration-mcp-server
```

### 2.2 每个模块的职责

`frontend-miniapp`

- 小程序前端
- 负责聊天输入、语音输入、卡片展示、确认按钮、预约记录展示
- 不写核心分诊规则，不写挂号业务判断

`gateway-chat-service`

- 对外统一入口
- 当前主要接口是 `/api/chat`
- 适合放鉴权、限流、日志、链路追踪、语音转文字、统一异常包装
- 不应该写复杂挂号规则

`supervisor-agent`

- 业务路由 Agent
- 判断用户请求应该交给哪个 Agent
- 例如分到 `triage-agent`、`guide-agent`、`registration-agent`
- 后续可以升级成 LLM Routing

`triage-agent`

- 分诊 Agent
- 根据症状推荐科室
- 注意它不是医学诊断系统，不能给确诊结论
- 高风险症状要分流到人工或急诊提示

`guide-agent`

- 导诊 Agent
- 负责医院位置、就诊流程、医保规则、注意事项等问答
- 后续适合接 RAG 知识库

`registration-agent`

- 智能挂号核心 Agent
- 负责收集信息、补问、查号源、生成挂号预览、等待确认、创建挂号单
- 后续最适合升级成 ReAct Agent，因为它需要调用多个工具

`patient-mcp-server`

- 患者和就诊人相关工具服务
- 后续对接患者主数据、实名信息、就诊人绑定关系

`schedule-mcp-server`

- 科室、医生、排班、号源相关工具服务
- 后续对接真实排班系统

`registration-mcp-server`

- 挂号交易相关工具服务
- 后续对接 HIS 挂号交易接口、订单系统、审计系统

`common-domain`

- 跨模块共享 DTO 和业务契约
- 例如 `ChatRequest`、`ChatResponse`、`RegistrationCommand`、`RegistrationResult`
- 新的跨服务请求/响应对象优先放这里

`common-ai`

- 公共 AI 能力模块
- 当前适合承接模型路由、模型降级、通用模型调用包装
- 不放具体业务流程

## 3. 请求是怎么流转的

以“我咳嗽发烧三天，想挂明天下午的号”为例：

```text
1. frontend-miniapp 发送文本到 gateway-chat-service
2. gateway-chat-service 转发给 supervisor-agent
3. supervisor-agent 判断这是挂号/分诊相关请求
4. 如果科室不明确，先交给 triage-agent 推荐科室
5. registration-agent 收集就诊人、科室、日期、时间等信息
6. registration-agent 调 schedule-mcp-server 查号源
7. 系统返回候选号源和挂号预览
8. 用户明确确认
9. registration-agent 调 registration-mcp-server 创建挂号单
10. 返回挂号结果
```

关键点是：模型不能凭空生成号源，号源必须来自 `schedule-mcp-server`。模型也不能绕过确认直接挂号，写操作必须有明确确认。

## 4. 怎么通过这个项目学习 AI 应用

### 4.1 第一阶段：看懂普通请求链路

先不要管 ReAct、RAG、LLM Routing。先把这条链路看懂：

```text
frontend-miniapp
  -> gateway-chat-service
  -> supervisor-agent
  -> registration-agent
```

你要搞清楚：

- 前端传了什么 JSON
- `gateway-chat-service` 怎么转发
- `supervisor-agent` 怎么判断路由
- `registration-agent` 返回了什么
- `ChatResponse` 里哪些字段会影响前端展示

### 4.2 第二阶段：看懂业务契约

重点看 `common-domain`。

你要形成一个习惯：跨服务交互先定义契约，再写业务逻辑。

例如智能挂号至少需要这些字段：

```text
userId
patientId
departmentCode
doctorId
clinicDate
startTime
confirmed
```

如果缺少字段，Agent 应该追问，而不是硬猜。

### 4.3 第三阶段：看懂 Agent 和 MCP 的边界

Agent 做理解和编排，MCP 做真实动作。

```text
Agent 负责：
  理解用户意图
  抽取结构化参数
  判断缺什么信息
  生成补问
  决定调用哪个工具
  解释工具返回结果

MCP 负责：
  查患者
  查就诊人
  查医生
  查排班
  查号源
  锁号
  创建挂号单
  取消挂号
  改约
```

不要让 Agent 直接写数据库，也不要让前端决定挂哪个科。

### 4.4 第四阶段：学习模型能力

这个项目里模型能力可以拆成几类：

```text
LLM Routing
  supervisor-agent 判断请求该交给哪个业务 Agent

Model Routing / Fallback
  common-ai 判断当前应该用哪个模型，失败后怎么降级

ReAct Agent
  具体业务 Agent 内部边推理边调工具

RAG
  guide-agent 或 triage-agent 从知识库检索资料后回答

ASR / TTS
  语音转文字、文字转语音
```

这些不是一回事，不要混在一起。

## 5. 怎么思考一个新功能

以后新增功能，不要先问“代码写哪”。先按这个模板拆：

```text
1. 用户是谁
2. 用户输入是什么
3. 系统要识别什么意图
4. 需要收集哪些字段
5. 哪些字段必须追问
6. 是否需要调用真实工具
7. 是否涉及写操作
8. 写操作是否需要确认
9. 成功后返回什么
10. 失败后怎么补救
11. 是否需要审计
12. 是否需要人工介入
```

然后再决定代码放哪一层：

```text
交互展示 -> frontend-miniapp
统一入口、鉴权、语音识别 -> gateway-chat-service
意图路由 -> supervisor-agent
业务理解和流程编排 -> 具体业务 Agent
真实数据查询和写操作 -> MCP Server
共享 DTO -> common-domain
模型路由和降级 -> common-ai
配置 -> application.yml 或 Nacos
```

## 6. 智能挂号应该怎么继续开发

智能挂号不是一句话返回一个科室，而是一个业务闭环。

推荐状态机：

```text
INIT
COLLECTING_PATIENT
COLLECTING_DEPARTMENT
COLLECTING_TIME
SEARCHING_SLOT
WAITING_USER_SELECTION
WAITING_CONFIRMATION
BOOKING
COMPLETED
FAILED
HUMAN_REVIEW
```

含义如下：

- `INIT`：新会话开始
- `COLLECTING_PATIENT`：还不知道给谁挂号
- `COLLECTING_DEPARTMENT`：还不知道挂什么科
- `COLLECTING_TIME`：还不知道日期或时间
- `SEARCHING_SLOT`：信息足够，开始查号源
- `WAITING_USER_SELECTION`：多个号源，等待用户选择
- `WAITING_CONFIRMATION`：已生成预览，等待用户确认
- `BOOKING`：执行锁号和创建挂号单
- `COMPLETED`：挂号完成
- `FAILED`：业务失败，需要重试或补充信息
- `HUMAN_REVIEW`：高风险或规则冲突，需要人工介入

会话状态建议放 Redis：

```text
conversation:{chatId}
```

建议字段：

```text
chatId
userId
currentIntent
currentStage
patientId
patientName
symptoms
departmentCode
departmentName
doctorId
doctorName
clinicDate
startTime
slotCandidates
selectedSlot
confirmed
riskLevel
expiresAt
```

第一版开发优先级：

```text
1. ConversationState
2. registration-agent 状态机
3. schedule-mcp-server 多候选号源
4. patient-mcp-server 就诊人列表和关系校验
5. registration-mcp-server 幂等、审计、补偿
6. 前端号源卡片和确认按钮
7. 大模型结构化抽取
```

## 7. 语音能力怎么接

语音不是让每个 Agent 都处理音频，而是在入口层先转文字。

推荐链路：

```text
患者语音
  -> frontend-miniapp 录音
  -> gateway-chat-service 上传音频
  -> DashScope / 阿里云 ASR 转文字
  -> 复用现有 /api/chat 文本链路
  -> supervisor-agent / registration-agent 继续处理
```

第一版只做：

```text
按住说话 -> 上传音频 -> 识别文本 -> AI 按文本挂号
```

暂时不要做实时语音对话。实时语音对延迟、流式传输、前端录音分片、服务端并发处理要求更高，不适合作为第一版。

如果以后要让 AI 也说话，再加 TTS：

```text
AI 文本回复 -> TTS -> 前端播放语音
```

语音能力应该放在：

```text
frontend-miniapp：录音、上传、展示识别文本
gateway-chat-service：音频接收、ASR 调用、转成 ChatRequest
Agent：仍然只处理文本
```

## 8. LLM Routing、Model Routing、ReAct、RAG 怎么放

### 8.1 LLM Routing

LLM Routing 是业务路由。

在本项目里对应：

```text
supervisor-agent 判断请求交给哪个 Agent
```

例如：

```text
“我头疼发烧挂什么科” -> triage-agent
“心内科在哪层” -> guide-agent
“帮我挂明天下午张医生的号” -> registration-agent
```

### 8.2 Model Routing / Fallback

Model Routing 是模型选择和降级。

在本项目里对应：

```text
common-ai 统一处理模型调用、失败重试、模型降级
```

例如：

```text
qwen-plus 不可用
  -> qwen-turbo
  -> qwen-max
  -> 返回失败兜底
```

配置建议集中到 Nacos 的公共配置里：

```yaml
ai:
  service:
    model-router:
      chat:
        default-model: ${AI_CHAT_DEFAULT_MODEL:qwen-plus}
        fallback-models:
          - ${AI_CHAT_FALLBACK_MODEL_1:qwen-turbo}
          - ${AI_CHAT_FALLBACK_MODEL_2:qwen-max}
        fallback-enabled: true
        exhausted-ttl: ${AI_CHAT_EXHAUSTED_TTL:PT1H}
        transient-retries: ${AI_CHAT_TRANSIENT_RETRIES:1}
      embedding:
        default-model: ${AI_EMBEDDING_DEFAULT_MODEL:text-embedding-v4}
        fallback-models:
          - ${AI_EMBEDDING_FALLBACK_MODEL_1:text-embedding-v3}
        fallback-enabled: true
```

### 8.3 ReAct Agent

ReAct 是具体 Agent 内部的工作方式：

```text
Reason -> Act -> Observation -> Reason -> Final Answer
```

在本项目里最适合放在：

```text
registration-agent
```

因为它需要：

- 判断缺什么字段
- 查患者
- 查号源
- 生成候选项
- 等用户确认
- 创建挂号单
- 失败后补偿

不要一开始给所有 Agent 都加 ReAct。先把 `registration-agent` 做扎实。

### 8.4 RAG

RAG 是检索增强生成，适合知识问答。

在本项目里最适合放在：

```text
guide-agent：医院规则、楼层、医保、就诊流程
triage-agent：科室规则、非诊断性质的分诊知识
```

RAG 不适合替代挂号交易。挂号交易必须走 MCP 和真实系统。

## 9. Nacos 配置怎么用

当前项目已经预留 Nacos Config，但默认不开启。

开启方式：

```text
NACOS_CONFIG_ENABLED=true
NACOS_SERVER_ADDR=127.0.0.1:8848
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
NACOS_CONFIG_GROUP=AI_SMARTCLINIC
```

每个接入 Nacos 的服务会加载：

```text
ai-smartclinic-common.yml
${spring.application.name}.yml
```

推荐分工：

```text
ai-smartclinic-common.yml
  放公共模型配置、DashScope 开关、模型降级配置

gateway-chat-service.yml
  放网关限流、上游地址、语音识别配置

supervisor-agent.yml
  放下游 Agent 地址、路由策略

registration-agent.yml
  放 MCP 地址、挂号策略、确认策略
```

不要把真实 `DASHSCOPE_API_KEY` 写死进代码或 Git。继续用环境变量注入。

DashScope 当前推荐只显式开启需要的能力，避免自动装配不需要的模型 Bean：

```yaml
spring:
  ai:
    model:
      chat: dashscope
      embedding: dashscope
      image: none
      audio:
        speech: none
        transcription: none
      rerank: none
      video: none
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      agent:
        enabled: false
      image:
        enabled: false
      chat:
        enabled: true
        options:
          model: ${DASHSCOPE_MODEL:qwen-plus}
      embedding:
        enabled: true
```

如果后续只在网关接语音，就只让 `gateway-chat-service` 开启 ASR，不要让所有 Agent 都开启音频能力。

## 10. 企业级技术栈建议

当前项目已经使用：

```text
Java 17
Spring Boot 3.2.x
Spring WebFlux
Spring AI
Spring AI Alibaba
DashScope
Maven 多模块
uni-app / Vue 3 / Pinia
```

继续企业级改造时，建议补齐：

```text
PostgreSQL
  主业务数据、挂号单、审计日志

Redis
  会话状态、幂等键、热点缓存、限流状态

Nacos
  配置中心、后续服务发现

Spring Security
  网关鉴权、内部服务调用鉴权

Resilience4j
  超时、重试、熔断、隔离

Micrometer + Prometheus + Grafana
  指标监控

OpenTelemetry
  分布式链路追踪

Loki / ELK
  日志检索和审计排查

Springdoc OpenAPI
  接口文档和联调

XXL-JOB / Quartz
  超时未确认清理、补偿、对账
```

第一阶段不建议上消息队列。等通知、补偿、对账、异步任务明显变多，再考虑 Kafka 或 RabbitMQ。

## 11. 企业级必须补哪些能力

### 11.1 数据持久化

当前项目还有 mock 或内存性质的能力。企业级必须落库：

```text
用户
就诊人
科室
医生
排班
号源
挂号单
操作审计
工具调用记录
会话快照
```

### 11.2 会话状态

智能挂号一定是多轮的，必须有状态。

不要只靠前端历史消息，也不要只靠模型上下文。

推荐：

```text
短期会话状态 -> Redis
关键业务快照 -> PostgreSQL
```

### 11.3 写操作确认

以下动作必须明确确认：

```text
创建挂号
取消挂号
改约
支付相关操作
就诊人信息变更
```

后端判断标准：

```text
没有 confirmed=true，就永远只返回预览，不执行写操作。
```

### 11.4 审计

至少记录：

```text
谁发起
什么时间
输入内容摘要
模型抽取结果
调用了哪个工具
工具入参
工具出参摘要
是否写操作
是否用户确认
最终结果
traceId
```

医疗场景不要随意保存完整原始敏感文本。需要按合规要求脱敏、加密、设置保留周期。

### 11.5 风险分流

高风险症状不能走普通自动挂号闭环。

例如：

```text
胸痛
呼吸困难
意识不清
抽搐
大出血
严重外伤
持续高热伴危险征象
```

处理方式：

```text
停止自动挂号流程
提示急诊或人工协助
记录风险分流事件
必要时进入人工审核
```

### 11.6 可观测性

生产环境至少要能看到：

```text
每个服务是否健康
每个接口耗时
模型调用耗时
模型失败率
模型降级次数
MCP 工具调用失败率
挂号成功率
确认转化率
高风险分流数量
异常日志和 traceId
```

没有监控的 AI 应用不能算企业级。

## 12. 推荐演进路线

### 阶段 1：跑通业务闭环

目标：

```text
自然语言发起挂号
  -> 识别意图
  -> 补齐信息
  -> 查候选号源
  -> 返回预览
  -> 用户确认
  -> 创建挂号单
```

重点任务：

- 补 `ConversationState`
- 完善 `registration-agent` 状态机
- 前端展示号源候选卡片
- 写操作必须确认

### 阶段 2：从 mock 改成真实数据库

目标：

```text
MCP Server 不再只返回固定数据，而是查 PostgreSQL
```

重点任务：

- 建患者表、医生表、科室表、排班表、挂号单表
- `schedule-mcp-server` 查真实号源
- `registration-mcp-server` 写真实挂号单
- 增加幂等键和审计日志

### 阶段 3：接真实医院系统

目标：

```text
MCP Server 变成真实系统适配层
```

重点任务：

- `patient-mcp-server` 接患者主数据
- `schedule-mcp-server` 接排班/号源系统
- `registration-mcp-server` 接 HIS 挂号交易
- 做外部系统错误码映射、超时、重试、补偿

### 阶段 4：增强 AI 能力

目标：

```text
让 AI 更自然、更准确、更可控
```

重点任务：

- `supervisor-agent` 升级 LLM Routing
- `registration-agent` 升级 ReAct
- `guide-agent` 接 RAG 知识库
- 网关接语音识别
- `common-ai` 完善模型降级和调用审计

### 阶段 5：企业级治理

目标：

```text
能稳定部署、可观测、可审计、可维护
```

重点任务：

- Nacos 管理多环境配置
- Spring Security 做鉴权
- Resilience4j 做熔断限流
- Micrometer + OpenTelemetry 做监控和链路
- 日志脱敏和审计归档
- CI/CD、自动化测试、灰度发布

## 13. 新功能开发模板

以后你要加任何功能，都按这个格式写设计，再动代码。

```text
功能名称：

用户目标：

入口：
  文本 / 语音 / 按钮 / 卡片

涉及模块：
  frontend-miniapp
  gateway-chat-service
  supervisor-agent
  xxx-agent
  xxx-mcp-server
  common-domain

需要识别的意图：

需要抽取的字段：

缺字段时怎么追问：

需要调用的工具：

是否写操作：

是否需要用户确认：

成功返回：

失败兜底：

审计字段：

测试用例：
```

示例：新增“语音智能挂号”

```text
功能名称：
  语音智能挂号

用户目标：
  患者说一句话，系统识别成文字并继续挂号流程

入口：
  frontend-miniapp 录音按钮

涉及模块：
  frontend-miniapp
  gateway-chat-service
  supervisor-agent
  registration-agent

需要识别的意图：
  CREATE_REGISTRATION

需要抽取的字段：
  症状、科室、医生、日期、时间、就诊人

缺字段时怎么追问：
  一次只追问一个关键字段

需要调用的工具：
  ASR
  schedule-mcp-server
  registration-mcp-server

是否写操作：
  只有最终创建挂号是写操作

是否需要用户确认：
  需要

成功返回：
  识别文本、候选号源、挂号预览或挂号结果

失败兜底：
  识别失败提示用户重录或改用文字

审计字段：
  识别文本摘要、ASR 耗时、traceId、最终意图
```

## 14. 当前最应该做的事

如果目标是把这个项目真正做起来，建议按这个顺序：

```text
1. 完善 registration-agent 的多轮状态机
2. 给 schedule-mcp-server 和 registration-mcp-server 接 PostgreSQL
3. 前端增加号源卡片和确认按钮闭环
4. 增加 Redis 会话状态
5. 增加写操作审计
6. 再接语音输入
7. 再做 ReAct / RAG / 更复杂的模型路由
8. 最后做完整监控、安全、限流、部署治理
```

原因很简单：智能挂号的核心不是模型有多强，而是能不能完成一个可确认、可审计、可补偿的挂号闭环。

## 15. 判断一个功能是否企业级

每个新功能上线前，用这几个问题检查：

- 用户输入不完整时，系统会不会追问
- 模型返回异常时，系统会不会兜底
- 真实数据是否来自数据库或外部系统
- 写操作是否必须确认
- 重复提交是否幂等
- 失败后是否可重试或补偿
- 是否有日志、指标、traceId
- 是否有审计记录
- 是否有权限校验
- 是否保护患者隐私
- 是否能配置化，不需要改代码上线
- 是否有自动化测试

只要这些问题答不上来，就还只是演示功能，不是企业级功能。

## 16. 一句话总结

这个项目的学习主线是：

```text
先理解业务闭环
再理解 Agent 编排
再理解 MCP 工具调用
再理解模型路由、ReAct、RAG、语音
最后补企业级治理
```

这个项目的扩展原则是：

```text
前端负责交互
网关负责入口治理
Supervisor 负责路由
业务 Agent 负责理解和编排
MCP 负责真实动作
common-domain 负责契约
common-ai 负责模型公共能力
Nacos / Redis / PostgreSQL / 监控 / 审计负责企业级落地
```

