---
id: "0174d460-43ec-4d78-8114-479e9c94d751"
entity_type: "task"
entity_id: "49b341d3-412c-4188-abd3-6de7426f05f2"
title: "编写 Kafka 端到端事件驱动 onboarding 集成测试 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-123"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:12.035131+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

编写 Kafka 端到端集成测试，证明事件驱动 onboarding 链路在成功、失败、重复事件三种关键场景下行为正确。

## 实现方式

1. 用 Embedded Kafka 或 Testcontainers 搭建测试 Kafka 环境。
2. 配置应用 Profile 启用 Kafka EventPublisher 与 Kafka Consumer，搭配 Fake Keycloak Adapter 与内存状态存储。
3. 编写三类测试用例：

- 成功路径：投递 `TenantInfrastructureProvisionedEvent` → 断言收到 `TenantIamProvisionedEvent`，且 correlationId 一致；本地状态为 `IAM_PROVISIONED`。
- 失败路径：让 Fake Keycloak 在某一步抛错 → 断言收到 `TenantIamProvisioningFailedEvent`，包含 failureCode 与 retryable 字段。
- 重复事件路径：投递同一事件两次 → 断言下游只收到一次 `TenantIamProvisionedEvent`，且状态/计数未被污染。

1. 通过断言出站事件主题、key、payload 与 header（含 correlationId）验证契约。

## 验收标准

- 三类核心场景均有端到端测试覆盖
- 测试不依赖真实 Keycloak、真实数据库或外部 IdP
- 测试可在 CI 中重复运行

## 范围

### 包含

- Kafka 端到端集成测试

### 不包含

- Step 单元测试与状态机单元测试（兄弟任务）
- 真实 Keycloak Adapter 测试（兄弟任务）## Details

**Scope**: Kafka 边界的端到端集成测试，覆盖成功、失败、重复事件三种场景。

**Out of Scope**: Step 幂等单元测试、状态机单元测试、真实 Keycloak Adapter 集成测试（均由其他兄弟任务负责）。

**Constraints**: 使用 Embedded Kafka 或 Testcontainers，不依赖外部环境, 测试须与 Fake Keycloak Adapter 与内存状态存储集成，保证可重复运行, 测试需断言出站事件的主题、key、payload 与 correlationId

## Acceptance Criteria

- [ ] 集成测试：向输入主题发送 `TenantInfrastructureProvisionedEvent` 后，能在输出主题观察到 `TenantIamProvisionedEvent`，且 correlationId 一致
- [ ] 集成测试：模拟中途失败时能观察到 `TenantIamProvisioningFailedEvent` 被发布，且携带 failureCode 与 retryable 语义
- [ ] 集成测试：同一 `TenantInfrastructureProvisionedEvent` 被重复投递后不会产生多个 `TenantIamProvisionedEvent`
- [ ] 测试使用 Embedded Kafka / Testcontainers，可在 CI 中重复运行
- [ ] 测试不依赖真实 Keycloak、真实数据库或外部 IdP

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 5 |

