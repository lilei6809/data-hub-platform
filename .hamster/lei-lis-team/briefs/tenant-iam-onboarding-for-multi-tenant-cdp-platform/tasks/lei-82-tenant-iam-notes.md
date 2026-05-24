---
id: "ecb9ef7a-fa8e-406b-8929-e72d82d42499"
entity_type: "task"
entity_id: "b3e8a1cb-5102-4cb3-beea-8c4340abb611"
title: "定义 Tenant IAM 输入/输出领域事件契约 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-82"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:56:59.822842+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在领域层定义 Tenant IAM Onboarding 的三个事件契约（输入、成功输出、失败输出），作为上下游事件驱动协作的稳定边界。

## Implementation Approach

1. 创建领域事件包，集中存放 IAM 边界事件类型。
2. 定义 `TenantInfrastructureProvisionedEvent`，字段：`tenantId`、`tier`、`adminEmail`、`correlationId`、`occurredAt`。
3. 定义 `TenantIamProvisionedEvent`，字段：`tenantId`、`organizationId`、`adminUserId`、`correlationId`、`occurredAt`。
4. 定义 `TenantIamProvisioningFailedEvent`，字段：`tenantId`、`failureCode`、`retryable`、`correlationId`、`occurredAt`。
5. 所有事件实现为不可变值对象，构造期校验必填字段。
6. 复用兄弟任务中已经定义的 `TenantId`、`Tier`、`CorrelationId` 等值对象类型，不重复定义。
7. 编写单元测试覆盖构造、校验、等价语义。

## Acceptance Criteria

- 三个事件类型作为领域层值对象存在，字段与 PRD 完全一致
- 事件对象不可变，必填字段在构造时做非空校验
- 事件类型不引用任何基础设施 SDK
- 事件字段不包含任何敏感凭证
- 单元测试覆盖合法构造、缺字段失败、equals/hashCode 稳定性

## Technical Constraints

- 事件类型必须位于领域层（不在 application 或 infrastructure 包内）
- 事件不可携带敏感信息（密码、secret、token）
- 事件字段类型必须使用领域值对象，而不是裸 String

## Code Patterns to Follow

- 不可变值对象 + 构造期不变量校验
- 与 Desired State 领域模型同样的命名风格（兄弟任务 `Tenant IAM Desired State 领域模型`）

## Scope Boundaries

**In scope**：事件类型定义、字段校验、单元测试。

**Out of scope**：EventPublisher 接口（下一个子任务）、Kafka 序列化（Phase 3 兄弟任务）、PublishTenantIamProvisionedEventStep（Step Pipeline 兄弟任务）。## Details

**Scope**: 在领域层定义 TenantInfrastructureProvisionedEvent、TenantIamProvisionedEvent、TenantIamProvisioningFailedEvent 三个不可变事件值对象，包含字段校验和单元测试。

**Out of Scope**: EventPublisher 接口设计、内存实现、Kafka 序列化或绑定、PublishTenantIamProvisionedEventStep（由 Step Pipeline 兄弟任务负责）、状态机映射（由 Provisioning Service 兄弟任务负责）。

## Acceptance Criteria

- [ ] 三个事件类型 `TenantInfrastructureProvisionedEvent`、`TenantIamProvisionedEvent`、`TenantIamProvisioningFailedEvent` 作为领域层值对象存在，字段与 PRD 完全一致
- [ ] 事件对象不可变，对必填字段（`tenantId`、`correlationId` 等）在构造时做非空校验，缺失字段抛出领域异常
- [ ] 事件类型不引用 Kafka、Spring、序列化框架或任何基础设施 SDK，仅依赖标准库与领域内类型（如 `TenantId`、`CorrelationId`）
- [ ] 事件字段不包含临时密码、Keycloak admin secret 或 IdP secret 等敏感凭证
- [ ] 单元测试覆盖：合法构造成功；缺失必填字段抛出领域异常；事件值对象的 equals/hashCode 基于全部字段稳定

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

