# Ai-SmartClinic 项目路线

## 项目定位

Ai-SmartClinic 要做成一个可联调、可部署、可解释的 AI 智能挂号助手，而不是单纯 Demo。

核心目标：

- 用户通过微信小程序输入自然语言诉求。
- 后端通过多 Agent 协作完成意图路由、科室分诊、导诊问答、号源查询和预约挂号。
- RAG 用于增强科室知识、导诊规则、医保材料、预约规则等知识问答和结构化决策。
- PostgreSQL 承载业务数据，pgvector 承载向量知识库，Redis 承载登录态、确认态和热点缓存。

核心链路：

```text
微信小程序
 -> gateway-chat-service
 -> supervisor-agent
 -> triage-agent / registration-agent / guide-agent
 -> patient-mcp-server / schedule-mcp-server / registration-mcp-server
 -> PostgreSQL / pgvector / Redis
```

## 模块职责

### frontend-miniapp

负责微信登录、token 保存、请求拦截、页面交互、挂号入口、预约记录展示。

前端不直接做业务判断，只负责收集用户输入、展示后端结果和处理登录态。

### gateway-chat-service

统一入口，负责：

- 微信登录换取用户身份。
- token 生成、校验和 Redis 缓存。
- traceId 生成和透传。
- 请求转发到 supervisor。

### supervisor-agent

负责意图路由，不处理具体业务。

目标路由：

- `TRIAGE`：病情描述、科室推荐。
- `REGISTRATION`：挂号、查询、取消、改约。
- `GUIDE`：导诊、医保、材料、院内流程。
- `HUMAN_REVIEW`：低置信度、高风险或无法处理。

路由应采用规则兜底 + LLM 结构化分类，输出必须可校验。

### triage-agent

负责把用户病情描述转成科室建议。

长期目标：

```text
急症安全规则
 -> pgvector RAG 检索科室知识
 -> LLM 基于 evidence 输出结构化 JSON
 -> 后端校验科室白名单、置信度、急症标记
 -> 低置信度时追问或人工兜底
```

普通科室知识不应该写死在 Java 里。Java 里只保留安全兜底、白名单和结构化校验。

### guide-agent

负责导诊问答，包括：

- 医保材料
- 就诊流程
- 到院准备
- 签到规则
- 院内指引
- 取消/改约规则解释

长期目标同样是 pgvector RAG + LLM 回复。没有 evidence 时，只允许给通用建议，不允许编造院内规则。

### registration-agent

负责挂号工作流，不能让模型直接编造结果。

它应该调用 MCP 服务完成：

- 查询患者信息
- 查询号源
- 创建挂号订单
- 查询预约记录
- 取消/改约

模型只负责理解用户意图和生成自然语言回复，真实业务状态必须来自 MCP/数据库。

### MCP servers

承接真实业务数据：

- `patient-mcp-server`：患者信息。
- `schedule-mcp-server`：科室、医生、号源。
- `registration-mcp-server`：挂号订单和预约记录。

MCP 服务要保持确定性，不能依赖模型判断业务成功失败。

### common-ai

统一 AI 能力：

- Chat 模型调用
- Embedding 模型调用
- fallback 模型切换
- transient retry
- 模型异常分类
- 统一日志

后续不应在各个 Agent 里重复写模型容错逻辑。

## RAG 建设路线

当前方向：

```text
知识来源
 -> 文档解析
 -> 切片
 -> metadata 标注
 -> embedding
 -> pgvector 入库
 -> 用户问题 embedding
 -> pgvector topK 检索
 -> 可选 rerank
 -> evidence 拼 prompt
 -> LLM 输出
 -> 后端校验
```

原则：

- pgvector 是主向量库。
- Redis 只做缓存，不做主向量库。
- 不在 Java 代码里内置大量医疗知识。
- 不自动灌模拟知识数据。
- 真实知识、模拟数据、脱敏数据后续通过导入流程进入知识库。

建议后续抽出一个 `common-rag` 或 `knowledge-service`：

```text
triage-agent / guide-agent
 -> common-rag / knowledge-service
 -> PostgreSQL + pgvector
```

统一能力：

- 文档上传
- 文档解析
- chunk 切片
- metadata 管理
- embedding 批处理
- pgvector 检索
- 知识版本管理
- 命中日志
- 检索效果评估

## 数据建设

后续需要准备三类数据。

### 科室分诊知识

用于 `triage-agent`：

- 症状描述
- 同义词
- 推荐科室
- 急症红线
- 追问问题
- 不确定时的处理策略

### 导诊知识

用于 `guide-agent`：

- 医保材料
- 就诊流程
- 到院准备
- 签到方式
- 退号改约规则
- 院内位置说明

### 业务测试数据

用于 MCP 服务：

- 患者数据
- 科室数据
- 医生数据
- 号源数据
- 挂号订单数据

这些数据可以先用模拟数据，但要放在数据库初始化脚本或导入脚本里，不要散落在业务代码里。

## 工程化路线

### 第一阶段：稳定核心链路

目标：小程序能完成一条完整挂号流程。

范围：

- 微信登录和 token 流程稳定。
- gateway 到 supervisor 到 registration 链路稳定。
- 挂号、查询、取消、改约接口稳定。
- traceId 能贯穿前端、gateway、agent、MCP。
- 控制台能看到完整链路日志。

### 第二阶段：RAG 基建统一

目标：去掉模拟关键词检索，形成真正 pgvector RAG。

范围：

- 统一 RAG 表结构。
- 建立知识导入流程。
- 支持 chunk、metadata、embedding、topK 检索。
- triage 和 guide 统一使用 RAG 检索结果。
- 空知识库时可回退，不影响主链路。

### 第三阶段：数据和评估

目标：能量化说明项目效果。

范围：

- 构建 50-100 条分诊测试集。
- 统计科室推荐准确率。
- 统计低置信度和人工兜底比例。
- 统计核心接口响应时间。
- 统计挂号链路成功率。

### 第四阶段：部署和运维

目标：从本地联调升级为可部署项目。

范围：

- Docker Compose 或部署脚本。
- PostgreSQL、pgvector、Redis、Nacos 初始化。
- 服务健康检查。
- 日志目录和日志级别。
- 异常告警或最小化监控。
- 数据库初始化脚本。

### 第五阶段：项目包装

目标：能作为 AI 工程落地项目用于简历和面试。

需要材料：

- README 架构说明。
- 部署文档。
- 测试报告。
- RAG 检索说明。
- 核心链路截图。
- 运行日志样例。
- 可量化结果。

## 当前优先级

短期不要继续堆新 AI 框架。优先把现有主线做扎实：

```text
A2A 多 Agent
 + Spring AI Alibaba
 + MCP
 + PostgreSQL
 + pgvector RAG
 + Redis
 + 微信小程序
```

等核心链路、RAG、部署和量化结果都稳定后，再考虑是否引入 LangGraph、Dify、AgentScope、Haystack 或其他编排框架。

## 近期实现记录

### 2026-05-01

- 聊天模型配置恢复为阿里 DashScope `qwen-plus`，小米 MiMo 暂不接入主链路。
- Embedding 配置保留 `text-embedding-v2` 和 `1024` 维，当前 pgvector 知识表也是 `vector(1024)`。
- supervisor-agent 增加第一版编排能力：当用户请求同时包含症状描述和挂号意图时，先调用 triage-agent 获取科室建议，再把 `departmentCode`、`departmentName`、`triageReason` 等上下文透传给 registration-agent 继续挂号预览。
- 该编排仍保持业务事实边界：科室建议来自 triage-agent，真实号源、就诊人和挂号预览仍由 registration-agent 通过 MCP/PostgreSQL 决定。
- 增加 `common-rag` 基础模块，统一 embedding、pgvector topK 检索、检索状态和检索日志；triage、guide、registration-policy 的 RAG 检索先接入公共检索服务，现有知识表暂不迁移。
- 增加 `common-agent` 内部协议基础类型：`AgentRequestEnvelope`、`AgentResponseEnvelope`、`AgentExecutionMeta`、`AgentCapability`，作为后续 A2A-lite 标准化的前置结构。
- 各 agent 和 Nacos 示例的 embedding 默认值统一为 `text-embedding-v2`，避免未加载 env 时回退到不一致的 embedding 模型。
- 增加统一 RAG schema 草案 `database/schema/2026-05-01-unified-rag-schema.sql`，包含 `knowledge_document`、`knowledge_chunk`、`knowledge_ingest_job`、`knowledge_retrieval_log`；`common-rag` 增加文档/chunk 导入服务、简单文本切片器和检索日志写入能力。

## 判断标准

这个项目达到“实战项目”的最低标准时，应满足：

- 前端能完整走通挂号链路。
- 后端能稳定处理登录、路由、分诊、挂号、查询。
- RAG 使用 pgvector，而不是关键词模拟。
- 业务数据来自 PostgreSQL，而不是代码写死。
- Redis 承担登录态和缓存职责。
- 有测试、日志、部署文档和可量化结果。
- 模型失败、低置信度、知识库为空时都有兜底。
