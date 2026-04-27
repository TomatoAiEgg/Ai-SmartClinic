# Ai-SmartClinic

Ai-SmartClinic 是一个面向微信小程序场景的智能挂号原型。项目的核心目标不是让大模型直接改数据库，而是让 AI 负责理解用户意图、分诊、生成自然语言回复和组织下一步动作，真正的写操作仍然由后端工作流和业务规则控制。

## 核心架构

```text
frontend-miniapp
  -> gateway-chat-service
  -> supervisor-agent
  -> triage-agent / guide-agent / registration-agent
  -> patient-mcp-server / schedule-mcp-server / registration-mcp-server
  -> PostgreSQL / Redis
```

- `gateway-chat-service`：统一入口，负责微信登录、Token、鉴权和 `/api/chat` 转发。
- `supervisor-agent`：总控路由，只决定请求进入分诊、导诊、挂号还是人工复核。
- `triage-agent`：按症状给出科室建议。
- `guide-agent`：处理地址、医保、材料、流程等导诊问答，并带轻量 RAG。
- `registration-agent`：处理挂号创建、查询、取消、改约，以及确认式写操作。
- `*-mcp-server`：只提供工具能力和数据访问，不直接生成聊天回复。

## 当前已完成的主线能力

- 微信登录链路已接通：前端获取 `code`，网关调用 `code2Session`，登录态写入 Redis。
- 聊天主链路已接通：前端 -> 网关 -> supervisor -> triage / guide / registration。
- 挂号工作流已成型：
  - `CREATE`
  - `QUERY`
  - `CANCEL`
  - `RESCHEDULE`
- 所有写操作都先返回预览，再由用户确认后执行。
- 症状驱动的挂号意图已做分流优化：
  - “挂号 + 病情描述”优先进入 `triage-agent`
  - 缺少科室信息时，`registration-agent` 会引导先按症状分诊，而不是死板拒绝
- `guide-agent` 已具备基于知识片段的轻量 RAG 检索能力。
- 患者、号源、挂号账本 3 个 MCP 服务都已接入 PostgreSQL。

## AI 架构

这个项目主要使用的是：

- `Spring AI`
- `Spring AI Alibaba`
- 自定义的 Agent 工作流抽象 `common-agent`
- 模块内的 Prompt / RAG / 规则 / Workflow 组合编排

设计原则是：

- `common-ai` 只做模型基础设施，不放业务 Prompt。
- 业务 Agent 自己维护 Prompt、规则、RAG 和结构化结果解析。
- 大模型可以参与理解和回复生成，但不能直接决定写操作落库。
- MCP 服务只做工具和数据层，不做聊天生成。

## 工程技术栈

AI 之外，工程栈保持常规、克制：

- 后端：Spring Boot 3、WebFlux、MyBatis-Plus
- 数据层：PostgreSQL、Redis
- 配置中心：Nacos
- 前端：uni-app、Vue 3、Pinia

## 关键业务约束

- 挂号、取消、改约都必须经过“预览 -> 确认 -> 执行”。
- AI 回复只能基于结构化业务数据生成，不能编造医生、科室、单号或状态。
- `supervisor-agent` 负责路由，不负责具体挂号业务执行。
- `registration-agent` 负责业务编排，不直接持有底层数据。

## 本地运行

最小联调通常需要这些依赖：

- PostgreSQL
- Redis
- Nacos
- DashScope Key
- 微信小程序 `AppId/AppSecret`

主要端口：

```text
gateway-chat-service       8080
supervisor-agent           10080
triage-agent               10081
registration-agent         10082
guide-agent                10083
patient-mcp-server         10101
schedule-mcp-server        10102
registration-mcp-server    10103
```

前端小程序产物目录：

```text
frontend-miniapp/dist/build/mp-weixin
```

## 当前状态

当前核心业务主线已经打通到“可联调”阶段。重点不是继续大改架构，而是围绕真实环境把微信登录、Redis、PostgreSQL、MCP 服务和前端小程序联调稳定下来。

## 相关文档

- [项目记录](docs/项目记录.md)
- [架构重构路线图](docs/architecture-refactor-roadmap-2026-04-24.md)
- [会话上下文记录](docs/conversation-context-2026-04-24.md)
