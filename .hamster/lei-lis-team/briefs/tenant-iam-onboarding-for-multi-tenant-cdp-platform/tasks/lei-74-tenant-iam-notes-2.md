---
id: "0ae9715a-55b9-418a-ba6e-5327a5e7fd5f"
entity_type: "task"
entity_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
title: "Tenant IAM 事件边界契约可表达上下游事件驱动协作 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-74"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:52:50.335975+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台开发者可以在领域层表达 IAM provisioning 的事件契约。

IAL 不是孤立功能。即使 MVP 还没接入 Kafka，输入事件、输出事件、correlationId 贯穿这些事都必须先在领域层定型，让后续 Phase 3 接入 Kafka 时不会动主流程。

## Experience

平台开发者得到三个事件类型（`TenantInfrastructureProvisionedEvent`、`TenantIamProvisionedEvent`、`TenantIamProvisioningFailedEvent`）、一个 `EventPublisher` Port、配套的 In-Memory 实现，以及把输入事件映射为 `TenantIamDesiredState` 的 mapper。一个本地 runner / 集成测试可以演示完整"事件进 → 事件出"闭环。

## Interaction

1. 上游 `TenantInfrastructureProvisionedEvent` 进入系统。
2. Mapper 把事件翻译为 `TenantIamDesiredState`。
3. Provisioning Service 执行 Pipeline 并更新状态。
4. 成功发布 `TenantIamProvisionedEvent`，失败发布 `TenantIamProvisioningFailedEvent`，`correlationId` 端到端贯穿。## Details

**User Capability**: 平台开发者可以在应用核心中以事件方式表达 Tenant IAM onboarding 的输入与输出：消费 `TenantInfrastructureProvisionedEvent` 触发 onboarding，成功后发布 `TenantIamProvisionedEvent`，失败后发布 `TenantIamProvisioningFailedEvent`，并能在没有真实 Kafka 的情况下通过内存 publisher 验证。

**Business Value**: IAL 与 Tenant Management、Connector Registry、DataSource 等上下文通过事件解耦。即便 MVP 不接入真实 Kafka，也必须把事件 schema、消费入口与发布入口建模清楚，避免后续重构主流程。

**Functional Requirements**:
- 定义事件 schema（领域层不可变记录）：
  - `TenantInfrastructureProvisionedEvent { tenantId, tier, adminEmail, correlationId }`
  - `TenantIamProvisionedEvent { tenantId, organizationId, adminUserId, correlationId }`
  - `TenantIamProvisioningFailedEvent { tenantId, failureCode, retryable, correlationId }`
- 提供 `EventPublisher` Port，MVP 提供 In-Memory / no-op 实现。
- 提供事件→DesiredState 的映射器：把 `TenantInfrastructureProvisionedEvent` 映射为 `TenantIamDesiredState`（适用 LOCAL_ONLY + SHARED_REALM 默认值）。
- 提供本地 runner 或测试入口，演示"事件进 → Service 执行 → 事件出"的最小闭环。
- `correlationId` 必须在事件、状态记录、Step 执行上下文与日志中端到端贯穿。

**Data Model & Structure**:
- 事件 record / 值对象定义。
- `EventPublisher` 接口与 In-Memory 实现（按主题分桶保存已发布事件，便于测试断言）。

**Technical Approach**:
- 事件 schema 位于领域/应用层，无 Kafka 依赖。
- Publisher 抽象设计要兼容未来 Outbox + Kafka 模式（即同步调用语义即可，真实可靠性由后续基础设施提供）。
- 失败事件的 `retryable` 由 Service 根据 `failureCode` 推断。

**User Workflows**:
- 集成测试：构造 `TenantInfrastructureProvisionedEvent` → mapper → Service → Pipeline → In-Memory Publisher 中出现 `TenantIamProvisionedEvent`。
- 失败路径：Service 抛出/捕获异常 → 发布 `TenantIamProvisioningFailedEvent`。

**Scope - INCLUDED**:
- 事件 schema、Publisher 抽象与 In-Memory 实现。
- 事件↔DesiredState 映射器。
- 端到端最小闭环演示（runner 或测试）。

**Scope - EXCLUDED**:
- 真实 Kafka 消费/发布、Outbox 机制（Phase 3 任务）。
- Service 内部状态机本身（"Provisioning Service"任务）。
- Step Pipeline（依赖任务）。

**Success Criteria**:
- 触发 `TenantInfrastructureProvisionedEvent` 后，In-Memory Publisher 中可观察到对应的成功或失败事件。
- 事件 schema 与 PRD 字段一致，`correlationId` 端到端贯穿。

**Constraints & Considerations**:
- 不要把 Keycloak 内部 ID、secret、临时密码等敏感信息塞进事件 payload。
- 事件命名与字段必须显式版本可控，方便未来 Schema Registry 演进。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant IAM Provisioning Service 与本地状态机可端到端编排 onboarding |

