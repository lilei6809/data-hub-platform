---
id: "17595454-0ca2-4184-acd6-4849e87e08d0"
entity_type: "task"
entity_id: "8d4e1122-0bf6-4db6-baca-1dcf4e17043e"
title: "通过 Outbox Pattern 可靠发布授权领域事件 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-175"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:19:03.196905+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

为 Authorization BC 引入 Outbox Pattern，把 RoleAssignment、Policy 等聚合状态变更与领域事件在同一 PostgreSQL 事务内写入，再由 OutboxPublisher 异步交给 EventPublisher 边界发布。

## 实现方式

1. 定义 outbox 表 schema 与 Flyway/Liquibase 迁移：`eventId(PK)`、`aggregateType`、`aggregateId`、`tenantId`、`eventType`、`payload(JSONB)`、`occurredAt`、`status`、`attemptCount`、`lastError`。
2. 定义领域事件类型：`RoleAssignmentGranted`、`RoleAssignmentRevoked`、`PolicyActivated`、`PolicySuperseded`（在本任务范围内的状态变更）。事件字段仅含 ID、tenantId、occurredAt、必要枚举状态。
3. 定义 `OutboxRepository`（append、findPending、markPublished、markFailed）与 `OutboxPublisher`（搬运 PENDING → EventPublisher）。
4. 在应用服务 `RoleAssignmentService`、`PolicyService` 内：聚合状态变更与 `outboxRepository.append(event)` 在同一 `@Transactional` 中执行；事务回滚则 outbox 一并回滚。
5. 提供 `InMemoryOutboxRepository` 与默认 In-Memory `EventPublisher` 适配，使 outbox 流转可在无 Kafka 的环境验证。
6. 编写测试：同事务写入、事务回滚 outbox 也回滚、Publisher 失败保留 PENDING/FAILED 并可重试、相同 eventId 幂等。

## 验收标准

- outbox schema 与迁移到位
- 聚合状态变更与 outbox 同事务
- OutboxPublisher 正确流转 PENDING→PUBLISHED 与失败重试
- 事件契约定义齐全且不含敏感数据
- 内存实现可独立验证
- 测试覆盖关键路径

## 技术约束

- outbox 写入与聚合变更同事务
- 发布成功后才标记 PUBLISHED
- 事件 payload 不含敏感资源原文
- 遵守 Schema-per-Tenant 访问规范

## 范围

**包含**：outbox 表与迁移、Repository/Publisher 接口、内存实现、事件契约、同事务写入、测试。

**不包含**：真实 Kafka Producer/Consumer（Phase 3 / 兄弟任务）、跨服务 Schema Registry、RoleAssignment 缓存失效链路、Thin Client SDK（兄弟任务）、Tenant IAM Onboarding 事件（其他兄弟任务）。## Details

**Scope**: Outbox 表结构与迁移、OutboxRepository、OutboxPublisher 接口与默认实现、与聚合状态变更同事务写入、授权 BC 事件契约定义、单元与集成测试。

**Out of Scope**: 真实 Kafka Producer/Consumer、跨服务事件 schema registry、RoleAssignment 缓存失效逻辑、Thin Client SDK、Tenant IAM Onboarding 事件（其他兄弟任务）。

**Constraints**: outbox 写入必须与聚合状态变更处于同一个 PostgreSQL 事件, Publisher 不能丢失事件：发布成功后才能标记 status=PUBLISHED，发布失败可重试, 事件 payload 不包含敏感资源属性原文，仅使用 ID、tenantId、枚举型状态, 设计上预留跨 tenant outbox 查询能力，但在 Schema-per-Tenant 下遵守访问约束

## Acceptance Criteria

- [ ] outbox 表 schema 定义包含 eventId、aggregateType、aggregateId、tenantId、eventType、payload(JSON)、occurredAt、status(PENDING/PUBLISHED/FAILED)、attemptCount，并提供迁移脚本
- [ ] RoleAssignment 创建/撤销、Policy 激活/被取代时，应用服务在同一个事务内写入聚合与对应 outbox 记录；事务回滚时 outbox 也回滚（集成测试验证）
- [ ] OutboxPublisher 从 outbox 拉取 PENDING 事件，委托给 EventPublisher 调用；成功后更新 status=PUBLISHED，失败记录 attemptCount 并保留 PENDING/FAILED 状态以供重试
- [ ] 事件契约定义（RoleAssignmentGranted、RoleAssignmentRevoked、PolicyActivated、PolicySuperseded）作为领域事件类型存在，只邹收同名双包含必要标识符与 tenantId，不含敏感资源原文
- [ ] 提供默认 EventPublisher 适配（内存/no-op）与 InMemoryOutboxRepository，以便在没有 Kafka 环境下验证 outbox 流转
- [ ] 测试覆盖：同事务提交、同事务回滚、发布重试、重复发布幂等性（同一 eventId 不重复打包）

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 7 |

