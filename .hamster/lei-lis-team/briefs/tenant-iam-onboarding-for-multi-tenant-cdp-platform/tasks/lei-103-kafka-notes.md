---
id: "0f1e9635-623c-4262-992d-038f9d76c2db"
entity_type: "task"
entity_id: "07096cfa-4daa-4b67-8a8a-5aeee81678bc"
title: "建立 Kafka 重复事件去重与幂等消费策略 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-103"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:05.351881+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

为 Kafka 消费链路建立显式的重复事件识别与处理策略，保证 at-least-once 投递下的端到端幂等性。

## 实现方式

1. 在 Kafka Consumer Adapter 内部增加重复事件识别逻辑，优先依据 `tenantId + correlationId`。
2. 调用 `TenantIamProvisioningService` 前后，与本地 `TenantIamProvisioningState` 配合判断：

- 若状态已是 `IAM_PROVISIONED`，跳过实际编排，仅记录"已处理"日志，必要时不重复发布成功事件。
- 若状态是 `IAM_PROVISIONING`，按已有重试/并发策略处理，不增加无效 retryCount。

1. 明确"重复事件不会重复发布 `TenantIamProvisionedEvent`"的契约。
2. 编写测试覆盖：同一事件二次到达、事件在不同 partition/rebalance 后重投、与失败重试事件并存。

## 验收标准

- 重复事件被显式识别
- 重复事件不重复发布下游事件
- 重复事件不污染失败计数与失败原因
- 拥有覆盖重复消费场景的测试

## 范围

### 包含

- Kafka 边界的重复事件识别与处理策略

### 不包含

- Step 内部 ensure 幂等（兄弟任务）
- 本地状态机定义（兄弟任务）## Details

**Scope**: Kafka Consumer 边界的重复事件识别与处理策略，以及重复发布的防护。

**Out of Scope**: Step Pipeline 内部的 ensure 幂等逻辑（兄弟任务）与本地状态机本身（兄弟任务）。

**Constraints**: 去重判断不能依赖 Kafka offset，必须基于业务识别（tenantId + correlationId）, 重复事件不会造成 retryCount 虚高或失败记录重复覆写, 重复事件后不应重复发布 `TenantIamProvisionedEvent`，需明确记录决策依据

## Acceptance Criteria

- [ ] Consumer 能识别重复的 `TenantInfrastructureProvisionedEvent`（基于 tenantId + correlationId 或等价机制）
- [ ] 重复事件不会导致 `IAM_PROVISIONED` 事件被重复发布给下游
- [ ] 重复事件不会错误地增加 retryCount 或覆写 failureCode/failureMessage
- [ ] 提供测试覆盖：同一事件多次进入 Consumer 后状态与外部可观察行为满足幂等要求
- [ ] 去重决策点与原因（如状态已是 IAM_PROVISIONED）能通过日志被观察

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

