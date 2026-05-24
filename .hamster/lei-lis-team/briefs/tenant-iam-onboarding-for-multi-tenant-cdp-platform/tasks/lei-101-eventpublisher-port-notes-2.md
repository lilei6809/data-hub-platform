---
id: "f04e8814-4a02-4c0a-b365-a3d999ada6b3"
entity_type: "task"
entity_id: "748a7835-7cc3-4ceb-9219-a53a7f10e9cc"
title: "定义 EventPublisher Port 抽象 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-101"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:49.32284+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

定义 `EventPublisher` Port 抽象，作为发布 Tenant IAM 领域事件的中立边界，屏蔽内存、Kafka、Outbox 等具体实现差异。

## Implementation Approach

1. 在领域或应用 Port 包内创建 `EventPublisher` 接口。
2. （可选）引入 `DomainEvent` 标记接口，让上一子任务定义的三个事件类型实现该接口，使 Publisher 接受统一类型。
3. 定义 `publish(DomainEvent event)` 方法签名，语义为同步返回，失败抛出领域异常。
4. 创建 `EventPublishException` 领域异常类型，包装底层失败原因。
5. 编写接口契约文档（Javadoc），明确语义边界。

## Acceptance Criteria

- `EventPublisher` 接口存在于领域/Port 层，无基础设施依赖
- 可接受三个事件类型（通过泛型或 DomainEvent 标记接口）
- 发布失败由明确的领域异常表达
- Javadoc 文档完整说明语义
- 接口词汇保持中立，无 Kafka/topic/partition 术语

## Technical Constraints

- 不得引用 spring-kafka、kafka-clients 或任何具体 broker SDK
- 接口必须可被同步与异步实现共同满足

## Code Patterns to Follow

- Ports and Adapters：与兄弟任务的 `KeycloakAdminPort`、`TenantIamStateRepository` 风格保持一致

## Scope Boundaries

**In scope**：接口定义、领域异常、文档。

**Out of scope**：In-memory 实现、Kafka 实现、Outbox 实现、事件类型本身。## Details

**Scope**: EventPublisher 接口定义、可选的 DomainEvent 标记接口、发布失败的领域异常类型。

**Out of Scope**: 内存适配器实现（下一个子任务）、Kafka 适配器（Phase 3 兄弟任务）、事件类型本身的定义（上一个子任务）、Outbox Pattern 实现（Authorization BC 兄弟任务）。

## Acceptance Criteria

- [ ] `EventPublisher` 接口存在于领域或应用 Port 包内，不依赖任何基础设施库
- [ ] 接口可接受上一子任务定义的三个事件类型（可通过泛型、重载或 DomainEvent 标记接口实现）
- [ ] 发布失败的语义透过明确的领域异常类型表达（例如 `EventPublishException`），不是裸 RuntimeException
- [ ] 接口 Javadoc/文档明确说明语义：同步返回、失败抛异常、不承诺交付顺序以外的附加保证
- [ ] 接口中不出现 Kafka、topic、partition、serializer 等基础设施词汇

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

