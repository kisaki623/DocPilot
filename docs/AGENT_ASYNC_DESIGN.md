# Agent Async Design

本文档记录 DocPilot Agent 从当前同步模式演进到异步模式的设计草案。它不是已实现功能说明，也不代表当前已经接入异步 Agent、MQ 调度或 SSE 推送。

## 当前同步流程

当前 `/api/ai/agent/run` 是同步执行：

1. 校验登录用户、documentId 和 task。
2. 创建 `AgentTask` 记录。
3. 固定先执行 `document_status_tool`。
4. 如果文档尚未解析完成，直接返回 `status_only`。
5. 如果文档已解析完成，`DocumentToolSelector` 根据 task 规则选择 `status_only`、`summary_tool` 或 `qa_tool`。
6. 执行对应工具并记录 `AgentStep`。
7. 更新 `AgentTask` 为 `SUCCESS` 或 `FAILED`。
8. 前端根据返回的 `taskId` 查询持久化 task / steps 并展示 trace。

## 同步模式瓶颈

- HTTP 请求需要等待工具链执行完成，长任务容易受到超时影响。
- 前端只能在一次 run 完成后看到最终响应，无法天然表达 PENDING / RUNNING 状态。
- 如果未来工具数量变多，单次请求内串行执行会放大延迟。
- 失败恢复能力有限，当前更适合 demo 和短任务，不适合长耗时 Agent 工作流。

## 为什么现在不直接实现异步

- 当前 Agent 仍是最小工具链闭环，核心价值是可解释路由、执行 trace 和前后端展示。
- 直接接入异步会扩大改动范围，涉及 MQ 消息、状态机、幂等、重试、轮询和前端交互。
- 项目已经有 task / step 查询 API，可以作为未来异步轮询的基础，不需要在本阶段冒进重构。
- 当前目标是保持可验证的小步演进，不把未完成的异步能力写成已实现。

## 方案对比

### RocketMQ

优点：

- 与项目已有 RocketMQ / Outbox 技术栈一致。
- 适合长任务、重试、削峰和后台执行。
- 可以复用解析任务链路中的幂等、状态流转和失败记录经验。

代价：

- 需要新增消息类型、consumer、幂等 key、重试策略和死信处理。
- 需要明确 AgentTask 状态机和并发保护。
- 前端需要轮询或订阅执行进度。

### Spring `@Async`

优点：

- 接入成本低，适合作为本地演示或过渡方案。
- 不需要新增 MQ topic。

代价：

- 进程内执行，服务重启后任务容易丢失。
- 不适合多实例部署下的可靠调度。
- 重试、限流和积压治理能力弱。

### DB Polling

优点：

- 只依赖数据库，调试简单。
- 可以直接围绕 `tb_agent_task` 状态轮询。

代价：

- 定时扫描效率较低。
- 高并发下容易增加数据库压力。
- 需要额外处理抢占、锁和重复执行。

## 推荐未来方案

推荐以 RocketMQ 为主线演进：

1. `/api/ai/agent/run` 只创建 `AgentTask`，初始状态为 `PENDING`。
2. 后端投递 Agent 执行消息，消息中只包含必要的 taskId 和用户上下文引用。
3. Consumer 抢占任务并将状态更新为 `RUNNING`。
4. Consumer 执行当前同步 service 中的工具链逻辑，逐步写入 `AgentStep`。
5. 成功时将 `AgentTask` 更新为 `SUCCESS`，失败时更新为 `FAILED` 并记录错误摘要。
6. 前端通过现有 task / step 查询接口轮询 `PENDING -> RUNNING -> SUCCESS / FAILED`。
7. 可选增加 SSE 推送，把 task / step 状态变化主动推给前端。

## 状态机草案

- `PENDING`：任务已创建，等待执行。
- `RUNNING`：后台 worker 已开始执行。
- `SUCCESS`：工具链执行成功，已有最终回答。
- `FAILED`：执行失败，`errorMsg` 记录摘要。

## 当前明确未实现

- 未实现异步 Agent。
- 未新增 RocketMQ topic / consumer。
- 未新增 Outbox 事件。
- 未新增 DB polling worker。
- 未新增 Agent SSE 推送。
- 未修改 `tb_agent_task` / `tb_agent_step` 表结构。

## 后续最小切入点

1. 先把当前同步执行逻辑抽成可复用 executor，保持 Controller 不感知工具细节。
2. 再新增异步提交接口或参数，返回 taskId 后立即结束请求。
3. 最后接入 RocketMQ consumer，并用 task / step 查询接口完成前端轮询验证。

## Selector Metrics Debug 边界

T021 新增 selector shadow metrics 的内部只读 debug dump / reporter，但它不改变本文档的异步 Agent 设计边界：

- 不新增 HTTP API。
- 不新增 Actuator endpoint。
- 不接 Prometheus。
- 不落库。
- 不接 RocketMQ Agent。
- 不改变 `/api/ai/agent/run` 的同步执行语义。
- 不让 shadow decision 接管 primary routing。

当前选择内部 debug dump 的原因是：selector shadow metrics 包含 provider / decision 聚合等运行信息，在管理端鉴权、内网暴露范围和脱敏策略未设计前，不应直接对外开放。

T022 已完成观测入口设计决策文档，结论是：

- 短期继续使用本地 debug dump。
- 下一步优先做 Actuator endpoint 设计草案，但不直接实现。
- Prometheus 作为中期路线，只暴露数值指标和安全枚举 label。
- 管理端 API 暂缓，等待权限体系和审计策略更明确。

这些观测设计不改变 Agent 同步 / 异步执行路线，也不改变 production routing。
