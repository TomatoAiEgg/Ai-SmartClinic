# AI挂号平台

这是一个面向“聊天式挂号”场景的 Java 多模块项目。

当前仓库包含两部分：

- `后端`：Spring Boot 多模块骨架，后续演进到 `Spring AI Alibaba + Multi-Agent + MCP`
- `前端`：`uni-app + Vue 3 + Pinia` 小程序前端，已能对接现有网关接口

## 当前模块

后端模块：

- `common-domain`
- `gateway-chat-service`
- `supervisor-agent`
- `triage-agent`
- `registration-agent`
- `guide-agent`
- `patient-mcp-server`
- `schedule-mcp-server`
- `registration-mcp-server`

前端模块：

- `frontend-miniapp`

## 当前能力

已完成：

- Java 多模块工程骨架
- 网关到 Agent 的基础请求链路
- `patient/schedule/registration` 三个 MCP 服务占位
- 挂号创建、查询、取消、改约的基础契约
- 小程序前端页面骨架
- 小程序前端与 `/api/chat` 网关联调
- 本地预约记录缓存
- 确认流按钮和挂号结果回显

未完成：

- `LlmRoutingAgent`
- `ReactAgent`
- `A2A + Nacos`
- 真实 HIS / 排班 / 患者系统适配
- 完整的写操作审计和人工审核

## 技术文档

- AI 应用学习、扩展与企业级落地指南：[docs/ai-application-enterprise-guide.md](/D:/workspace/包装项目/Ai-SmartClinic/docs/ai-application-enterprise-guide.md)

## 后端启动顺序

1. `patient-mcp-server`
2. `schedule-mcp-server`
3. `registration-mcp-server`
4. `triage-agent`
5. `registration-agent`
6. `guide-agent`
7. `supervisor-agent`
8. `gateway-chat-service`

## 小程序前端

目录：

- [frontend-miniapp](/D:/workspace/包装项目/Ai-milkTea/frontend-miniapp)

页面：

- `pages/index`：聊天主入口
- `pages/appointments/index`：预约记录
- `pages/guide/index`：就诊指引
- `pages/settings/index`：网关地址和本地缓存设置

本地开发：

```bash
cd frontend-miniapp
pnpm install
pnpm dev mp-weixin
```

构建微信小程序：

```bash
cd frontend-miniapp
pnpm build mp-weixin
```

构建产物目录：

- `frontend-miniapp/dist/build/mp-weixin`

默认网关地址：

- `http://127.0.0.1:8080`

如果用真机调试，需要把它改成你电脑在局域网内可访问的 IP，例如 `http://192.168.1.10:8080`。

## 校验状态

已验证：

- `mvn -q -DskipTests compile`
- `cd frontend-miniapp && pnpm type-check`
- `cd frontend-miniapp && pnpm build mp-weixin`

## 下一步

1. 把 `supervisor-agent` 替换成真正的 `LlmRoutingAgent`
2. 把 `triage-agent` 和 `registration-agent` 改成 `ReactAgent`
3. 把三个 MCP 服务接到真实业务系统
4. 给挂号、取消、改约加入更严格的确认和审计链路
