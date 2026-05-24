---
id: "f6e717a8-58b9-4656-a162-fc4d7b82f423"
entity_type: "task"
entity_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
title: "Kafka 事件驱动 Onboarding 集成可承接租户生命周期事件 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-76"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:53:43.04404+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 后续系统集成方可以通过 Kafka 触发 IAM onboarding 并消费 IAM 事件。

把 MVP 的事件边界接入真实 Kafka，让租户生命周期跨上下文协作在生产中运行：基础设施完成 → 触发 IAM → IAM 成功/失败 → 下游编排继续。

## Experience

下游服务通过订阅 `TenantIamProvisionedEvent` / `TenantIamProvisioningFailedEvent` 决定下一步动作，IAL 通过订阅 `TenantInfrastructureProvisionedEvent` 自动触发 onboarding。`correlationId` 在所有事件、状态记录和日志中端到端贯穿，重复投递不会破坏状态。

## Interaction

1. Kafka 消费者接收 `TenantInfrastructureProvisionedEvent`。
2. 映射为 `TenantIamDesiredState` 并调用 Provisioning Service。
3. 状态推进完成后再 ack 事件，保证 at-least-once。
4. Kafka 生产者发布成功/失败事件，下游消费继续编排。## Details

**User Capability**: 后续系统集成方（Connector Registry、DataSource、Connection Governance 等）可以通过 Kafka 发送 `TenantInfrastructureProvisionedEvent` 触发 IAM onboarding，并通过订阅 `TenantIamProvisionedEvent` / `TenantIamProvisioningFailedEvent` 决定下一步动作。

**Business Value**: 把 MVP 中通过 runner / 测试触发的事件边界落地到真实消息基础设施，让租户生命周期跨上下文协作可在生产中运行。

**Functional Requirements**:
- Kafka 消费者：订阅 `TenantInfrastructureProvisionedEvent` 主题，反序列化后映射为 `TenantIamDesiredState`，调用 `TenantIamProvisioningService`。
- Kafka 生产者：发布 `TenantIamProvisionedEvent` 与 `TenantIamProvisioningFailedEvent`。
- 重复事件处理：通过 `tenantId + correlationId` 或事件 ID 实现 at-least-once 的幂等消费（结合状态机与 Step Pipeline 的幂等性）。
- `correlationId` 在消费/发布/日志/状态记录中端到端贯穿。
- 消费失败重试与死信处理策略；不允许 Fail-Open（即 IAM 状态错乱或丢失事件）。
- Publisher 必须为后续 Outbox Pattern 留出接入点（本任务可使用 transactional outbox 或者明确标注后续替换路径）。

**Data Model & Structure**:
- 复用"事件边界契约"任务中的事件 schema。
- 引入序列化格式（如 JSON Schema 或 Avro）的初版定义。

**Technical Approach**:
- 使用 Spring Kafka 或等价客户端。
- 消费者侧：手动 ack，先持久化状态再 ack，避免事件丢失。
- 生产者侧：MVP 可先直接发送；如条件允许引入 Outbox。

**Scope - INCLUDED**:
- Kafka 消费者与生产者实现。
- 事件序列化/反序列化层。
- 重复事件处理策略与 correlationId 贯穿。
- 集成测试（Testcontainers Kafka）。

**Scope - EXCLUDED**:
- 事件 schema 本身（"事件边界契约"任务负责）。
- 真实 Keycloak Adapter（"真实 Keycloak Adapter"任务负责）。
- Authorization BC 的 Outbox 事件（"Authorization Service PDP"任务负责）。

**Success Criteria**:
- 在 Testcontainers Kafka 上端到端走通 onboarding。
- 重复投递同一事件不会创建重复对象，也不会破坏状态机。
- 日志可以按 `tenantId` 与 `correlationId` 串起整条链路。

**Constraints & Considerations**:
- 严禁把 secret / 临时密码 / IdP secret 放入事件 payload 或日志。
- 消费者必须在状态推进前不 ack 事件，保证 at-least-once 语义。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant IAM 事件边界契约可表达上下游事件驱动协作, Tenant IAM Provisioning Service 与本地状态机可端到端编排 onboarding |

