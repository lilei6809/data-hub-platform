---
id: "0979baa7-dd69-4656-bdfd-2ba58bd2b2a3"
entity_type: "task"
entity_id: "9deb6d38-3556-4d76-b121-d31279b5d5f7"
title: "EventPublisher 的 Kafka 适配器发布 IAM 成功/失败事件 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-145"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:24.100292+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 `EventPublisher` Port 的 Kafka 适配器，将 IAM 成功与失败事件可靠发布到 Kafka 主题。

## Implementation Approach

1. 在基础设施层（`infrastructure/kafka/outbound`）创建 `KafkaEventPublisher`，实现兄弟任务定义的 `EventPublisher` 接口。
2. 定义出站事件 schema（与领域事件字段对齐）：

- `TenantIamProvisionedEvent`：`tenantId`、`organizationId`、`adminUserId`、`correlationId`
- `TenantIamProvisioningFailedEvent`：`tenantId`、`failureCode`、`retryable`、`correlationId`

1. 配置两个主题，按事件类型路由。
2. 使用 `tenantId` 作为分区键。
3. 将 `correlationId` 同时写入 Kafka Header 和消息体。
4. 通过 Spring 配置在 MVP（内存实现）与生产（Kafka 实现）之间切换。

## Acceptance Criteria

- KafkaEventPublisher 实现 EventPublisher 接口
- 成功/失败事件路由到不同主题
- tenantId 作为分区键保证顺序
- correlationId 透传到 Kafka Header
- 内存与 Kafka 实现可通过配置切换

## Technical Constraints

- 领域层不可感知 Kafka 类型，仅依赖 EventPublisher 接口
- 不与本地数据库事务绑定（避免分布式事务，依赖兄弟任务的状态机重试语义）
- 序列化失败必须显式抛错以触发应用层错误处理

## Code Patterns to Follow

- Ports and Adapters：KafkaEventPublisher 是出站适配器
- 与兄弟任务 "Tenant IAM 事件边界契约" 定义的事件 schema 保持一致## Details

**Scope**: EventPublisher Port 的 Kafka 适配器实现，发布 TenantIamProvisionedEvent 与 TenantIamProvisioningFailedEvent 到 Kafka

**Out of Scope**: EventPublisher 接口与领域事件类定义（兄弟任务）；Consumer 实现（独立子任务）；Outbox Pattern（属于 Authorization BC 任务）；应用服务中事件发布的触发点（兄弟任务）

## Acceptance Criteria

- [ ] KafkaEventPublisher 实现兄弟任务定义的 EventPublisher 接口
- [ ] TenantIamProvisionedEvent 被发布到成功事件主题，TenantIamProvisioningFailedEvent 被发布到失败事件主题
- [ ] tenantId 用作分区键，保证同一租户事件顺序
- [ ] correlationId 随事件载荷或 Kafka Header 一起传递
- [ ] 应用核心可通过配置在内存 EventPublisher 与 Kafka EventPublisher 之间切换，不修改调用方

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

