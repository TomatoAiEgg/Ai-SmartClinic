# Ai-SmartClinic A2A 升级方案

日期：2026-04-25

## 1. 当前判断

Ai-SmartClinic 当前已经不是“玩具 Demo”，但也还不是“成熟的标准化 A2A 平台”。

更准确的定位是：

```text
frontend-miniapp
  -> gateway-chat-service
  -> supervisor-agent
  -> triage-agent / guide-agent / registration-agent
  -> patient-mcp-server / schedule-mcp-server / registration-mcp-server
  -> PostgreSQL / Redis
```

它现在本质上是一个“可运行的多 Agent 业务系统”：

- `gateway-chat-service` 负责微信登录、鉴权、会话 token 和用户侧 API。
- `supervisor-agent` 负责路由和分发。
- `triage-agent / guide-agent / registration-agent` 负责具体业务智能。
- `*-mcp-server` 负责确定性工具能力和数据访问。
- `common-ai` 负责模型调用、降级、重试等 AI 基础设施。
- `common-agent` 已经开始承载 workflow 抽象，但还处在轻量阶段。

当前形态是合理的第一阶段，不需要推翻重来。

## 2. 当前已经做对的部分

### 2.1 common-ai 方向是对的

当前 `common-ai` 已经在做模型调用基础设施，而不是把业务 Prompt 塞进公共层。

当前代表实现：

- `common-ai/.../AiChatClient.java`
- `common-ai/.../FallbackChatClient.java`
- `common-ai/.../AiModelFallbackAutoConfiguration.java`

这说明公共 AI 层已经具备以下职责雏形：

- 模型调用入口
- fallback
- retry / route
- 基础日志

这是后续继续演进的正确方向。

### 2.2 supervisor + sub-agent 的业务拆分方向也是对的

当前代表实现：

- `supervisor-agent/.../SupervisorOrchestratorService.java`
- `supervisor-agent/.../SupervisorRoutePolicy.java`
- `registration-agent/.../RegistrationWorkflowService.java`

这说明当前主链路已经是：

- 总控路由
- 领域 agent 编排
- 确定性工具调用

这和成熟 A2A 系统的内核思路并不冲突。

### 2.3 registration-agent 是最适合继续升级的模块

`registration-agent` 已经具备最像状态机的结构：

- intent 分类
- 字段提取
- 规则补全
- 号源查询
- preview
- confirmation
- write action
- rollback / release slot

它是后续 workflow runtime 升级最适合的试点。

## 3. 当前还不成熟的地方

当前最大的不足，不是“没上某个流行框架”，而是以下边界还没完全定型：

1. Agent 间协议还不统一  
当前更像定制 RPC/HTTP 调用，不是统一的 Agent contract。

2. 上下文透传约束还不够强  
`traceId / chatId / userId / confirmationId / routeReason` 还没有形成统一 envelope。

3. Agent 能力没有标准化暴露  
当前知道某个 agent 能做什么，更多依赖代码和配置，而不是能力描述。

4. Workflow 可观测性不够  
出了问题之后，难以从统一视角看到：
哪个 agent 决定了什么、是否发生 fallback、是否命中过规则、是否进入确认流。

## 4. 目标架构

后续目标不应该是“全仓重写成某个框架”，而应该是：

```text
内部运行时:
  gateway -> supervisor -> domain agents -> MCP

内部协议:
  统一 Agent Request / Response Envelope
  统一 RouteDecision / Capability / ExecutionMeta

外部兼容层:
  A2A adapter

复杂流程增强:
  common-agent runtime 演进
  registration-agent 试点图工作流
```

一句话概括：

- `A2A`：解决 agent 和 agent 之间怎么说话
- `MCP`：解决 agent 怎么调用工具
- `workflow runtime`：解决 agent 内部复杂流程怎么跑
- `Harness`：如果要用，也只应该用于研发交付，不进入在线业务链路

## 5. 模块边界建议

### 5.1 common-ai

`common-ai` 只保留模型基础设施：

- chat client
- embedding client
- fallback
- retry
- model route
- AI telemetry

不要放入：

- 业务 Prompt
- triage / registration 专属规则
- 领域字段提取逻辑

### 5.2 common-agent

`common-agent` 应该升级为 Agent 运行时抽象层，而不是只停留在流程图定义。

当前已有：

- `AgentWorkflowDefinition`
- `AgentWorkflowNode`
- `AgentWorkflowEdge`

后续建议补齐：

- `AgentRequestEnvelope`
- `AgentResponseEnvelope`
- `AgentExecutionContext`
- `AgentExecutionMeta`
- `AgentCapability`
- `Checkpoint`
- `ResumeToken`
- `HumanConfirmationStep`
- `ToolExecutionStep`
- `RetryPolicy`
- `CompensationAction`

### 5.3 supervisor-agent

`supervisor-agent` 后续应该从“规则路由器”升级为“标准化 Agent Router”。

建议新增抽象：

- `RouteDecision`
  - `targetAgent`
  - `reason`
  - `confidence`
  - `requiredSlots`
  - `handoffMetadata`

- `AgentRegistry`
  - 注册各 agent 的能力
  - 注册调用地址
  - 注册支持的输入/输出 schema

### 5.4 业务 agent

`triage-agent / guide-agent / registration-agent` 继续负责业务智能本身：

- prompt
- policy
- workflow
- rag
- tool facade

不要把业务策略重新收回 `common-ai` 或 `common-agent`。

### 5.5 MCP 服务

`patient-mcp-server / schedule-mcp-server / registration-mcp-server` 继续保持“确定性工具层”定位：

- 只做数据访问和业务能力暴露
- 不生成聊天回复
- 不承载 Agent 状态机

## 6. 推荐升级路线

### Phase 1：先做内部协议标准化

这一阶段不引入新框架，只把你现在这套内核打磨稳定。

建议先统一以下模型：

- `AgentRequestEnvelope`
  - `traceId`
  - `chatId`
  - `userId`
  - `message`
  - `metadata`
  - `sessionContext`

- `AgentResponseEnvelope`
  - `route`
  - `message`
  - `structuredData`
  - `requiresConfirmation`
  - `confirmationId`
  - `nextAction`
  - `executionMeta`

- `AgentExecutionMeta`
  - `agentName`
  - `model`
  - `latencyMs`
  - `fallbackUsed`
  - `retryCount`

这一阶段的目标是：

- 所有 agent 都吃同一种 request envelope
- 所有 agent 都返回同一种 response envelope
- `gateway -> supervisor -> sub-agent` 的上下文透传完全一致

### Phase 2：做 A2A-lite 内部标准化

这一阶段仍然不需要直接接外部 A2A 协议。

建议先给各 agent 暴露统一接口：

- `POST /api/agent/execute`
- `GET /api/agent/capabilities`
- `GET /api/agent/health`

这样 `supervisor-agent` 调其他 agent 时，就不是“我知道你是 triage，所以我写一个专用调用器”，而是“我按统一 agent contract 调你”。

这一阶段完成后，项目会从“多服务协作”升级为“内部标准化多 Agent 系统”。

### Phase 3：再加真正的 A2A adapter

这一步不要改核心业务逻辑，而是在现有 agent 外面加 adapter。

做法：

- 外部 A2A 请求
- 转内部 `AgentRequestEnvelope`
- 调当前 orchestrator
- 再转回 A2A 响应

这样做的好处：

- 内核代码基本不动
- 当前 REST/HTTP smoke tests 可以继续保留
- 后面可以和其他 Agent 平台对接

建议顺序：

1. `supervisor-agent`
2. `triage-agent`
3. `guide-agent`
4. `registration-agent`

### Phase 4：只在 registration-agent 试点 workflow runtime

如果后面要引入 LangGraph 一类的图工作流思路，不要全仓上。

最合适的试点模块只有 `registration-agent`。

理由：

- 它已经有完整的 preview -> confirmation -> execute 写操作链路
- 它已经有 Redis confirmation checkpoint
- 它已经有工具调用、回滚、错误处理

推荐两种路线：

#### 路线 A：先增强 common-agent

把 `common-agent` 从“定义流程图”升级到“能执行、能恢复、能记录”的轻量运行时。

这是最稳的路线，也最符合当前 Java 主栈。

#### 路线 B：单独做 LangGraph POC

只做一个试点工程或试点模块，例如：

- create registration
- cancel registration

验证：

- 状态节点是否更清晰
- 人工确认节点是否更自然
- checkpoint / resume 是否更易维护

不要直接把全项目切成 LangGraph 多运行时。

### Phase 5：补生产级能力

这一步已经不属于“架构补课”，而是交付层建设。

包括：

- 幂等
- 审计日志
- tracing
- 指标监控
- 限流
- 熔断
- 降级策略可观测
- HIS / 外部系统适配

## 7. 关于 LangGraph 和 Harness 的判断

### 7.1 LangGraph

LangGraph 可以学，也可以试点，但不要直接作为当前项目的第一优先级。

原因：

- 当前主栈是 `Spring Boot + Spring AI + Java`
- LangGraph 主力生态不在 Java
- 一上来全量引入，会把系统变成多运行时架构

最合理的方式是：

- 先把内部协议和 runtime 边界补齐
- 再在 `registration-agent` 做试点

### 7.2 Harness

Harness 不应该进入在线业务运行时。

如果要用，正确位置是：

- PR review
- 自动修复建议
- 单测补齐
- 依赖升级
- CI/CD pipeline agent

结论：

`Harness 适合研发交付，不适合挂号主链路。`

## 8. 不建议做的事情

后续升级时，明确不建议：

1. 把业务 Prompt 回收进 `common-ai`
2. 把业务规则回收进 `common-agent`
3. 为了追热门，直接全仓切 LangGraph
4. 把 Harness 放进在线业务请求链路
5. 在没有统一 agent envelope 之前就强上外部 A2A 协议

## 9. 建议的实际执行顺序

按性价比排序，建议这样做：

1. 统一 `AgentRequestEnvelope / AgentResponseEnvelope`
2. 给 `supervisor-agent` 增加 `RouteDecision + AgentRegistry`
3. 给各 agent 暴露统一执行接口
4. 增加统一 execution meta 和 tracing
5. 再做 A2A adapter
6. 最后只在 `registration-agent` 试点图工作流 runtime

## 10. 这份方案的结论

Ai-SmartClinic 后续最合理的升级方向不是“换一个流行框架重做”，而是：

`先把现有多 Agent 体系标准化，再逐步补 A2A 兼容和 workflow runtime。`

用一句最短的话概括：

- 现在要补的是“协议、上下文、可观测性”
- 不是先补“框架名气”
