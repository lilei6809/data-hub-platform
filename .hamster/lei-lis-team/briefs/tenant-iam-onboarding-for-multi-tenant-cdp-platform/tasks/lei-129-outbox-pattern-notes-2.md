---
id: "bbcf64cf-4973-498a-814a-5ff585304048"
entity_type: "task"
entity_id: "e6537965-d5b9-42fe-a247-955110244715"
title: "通过 Outbox Pattern 可靠发布授权领域事件 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-129"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:43.063573+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为 Authorization BC 实现 Outbox Pattern，使 RoleAssignment 与 Policy 变更事件与本地状态变更在同一 PostgreSQL 事务内可靠落盘并异步发布。

## Implementation Approach

1. 设计 `authorization_outbox` 表结构与迁移脚本：`eventId (PK)`、`aggregateType`、`aggregateId`、`tenantId`、`eventType`、`payload (jsonb)`、`occurredAt`、`status (PENDING/PUBLISHED/FAILED)`、`retryCount`、`lastError`。
2. 在领域层定义事件 `RoleAssignmentCreatedEvent`、`RoleAssignmentRevokedEvent`、`PolicyActivatedEvent`；聚合在状态变更时生成事件并通过 `DomainEvents` 收集。
3. 应用服务在事务边界内：保存聚合 → 收集领域事件 → 序列化写入 outbox 表 → 提交事务。利用上一子任务的 schema-per-tenant 事务模板。
4. 实现 `OutboxPublisher` 后台组件（@Scheduled 或独立线程）：批量取 PENDING → 调用 `EventPublisher` 抽象 → 成功标记 PUBLISHED；失败递增 retryCount 并记录 lastError。
5. 提供 `EventPublisher` 抽象的内存/日志实现以支持本地与测试运行。
6. 编写集成测试覆盖事务原子性、回滚行为、publisher 状态转换、失败重试。

## Acceptance Criteria

- outbox 表与三类领域事件落地
- 聚合保存与 outbox 写入在同一事务
- 事务回滚时 outbox 也回滚
- publisher 可靠转移 PENDING → PUBLISHED 并在失败时重试
- 不耦合 Kafka 具体实现
- 日志不打印敏感 payload

## Technical Constraints

- 不在 MVP 接入真实 Kafka，仅通过 EventPublisher 抽象保留边界
- outbox 写入必须使用与聚合保存相同的连接/事务
- 至少一次投递语义，消费者侧需具备幂等能力（消费者不在本任务范围）## Details

**Scope**: outbox 表结构、领域事件 (RoleAssignmentCreated/Revoked、PolicyActivated) 定义、与聚合保存同事务写入 outbox、独立 publisher 调度、发布状态机与重试、对接已存在的 EventPublisher 抽象

**Out of Scope**: Kafka 真实 Adapter 接入（后续阶段）、Thin Client SDK 与 PEP、Tenant Context 传播、事件消费者端逻辑、跨服务 schema 注册中心、决策事件 (AuthorizationDecision) 广播（PRD MVP 未明确要求）

## Acceptance Criteria

- [ ] 存在 `authorization_outbox` 表（字段至少包含 eventId、aggregateType、aggregateId、tenantId、eventType、payload、occurredAt、status、retryCount）与对应迁移脚本
- [ ] 定义 `RoleAssignmentCreatedEvent`、`RoleAssignmentRevokedEvent`、`PolicyActivatedEvent` 三个领域事件，携带 tenantId、聚合 ID 与必要业务字段
- [ ] 保存聚合时，对应领域事件在同一 PostgreSQL 事务中写入 outbox；事务回滚时 outbox 记录也不存在（集成测试证明）
- [ ] 独立 publisher（定时任务或后台线程）轮询 status=PENDING 记录，调用 EventPublisher 抽象发布后标记 PUBLISHED，失败时增加 retryCount 并保持 PENDING
- [ ] EventPublisher 抽象仅为接口，提供内存/日志实现以供测试；不耦合 Kafka 具体实现
- [ ] 集成测试覆盖：(a) 成功保存同时写入 outbox；(b) 事务回滚时不产生 outbox 记录；(c) publisher 将 PENDING 变为 PUBLISHED；(d) 发布异常时 retryCount 递增
- [ ] 日志中打印 tenantId 与 eventId，不打印敏感 payload 字段

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 7 |

