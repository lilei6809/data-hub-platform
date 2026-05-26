---
id: "5fca7d07-9b0c-4574-b563-2489accdfb8f"
entity_type: "task"
entity_id: "a703c9e7-45d5-4ae3-b087-ff5d63002ba3"
title: "实现 MarkIamProvisionedStep 与 PublishTenantIamProvisionedEventStep 终态步骤 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-124"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:19.852855+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 最新任务调整

本任务暂缓，不按原描述实现 `MarkIamProvisionedStep`。

调整原因：

- `MarkIamProvisionedStep` 修改的是本地 provisioning 状态，不是外部 Keycloak desired fact。
- `TenantIamProvisioningStep` 当前语义是 ensure 外部 IAM 事实；把本地状态推进也做成 step 会混淆职责。
- 本地完成态应由 `TenantIamProvisioningService` 在所有 Keycloak ensure step 成功后调用 `TenantIamProvisioningState.markCompleted(now)`，并通过 `TenantIamStateRepository.save(state)` 持久化。

事件发布也后置处理：

- `PublishTenantIamProvisionedEventStep` 暂不进入当前 MVP step pipeline。
- 后续完成 `EventPublisher` Port 与事件模型后，再由 Application Service 或独立事件发布组件处理成功事件。

## 摘要

实现 Pipeline 末尾的两个终态步骤：把本地状态推进到 `IAM_PROVISIONED`、发布 `TenantIamProvisionedEvent`。

## 实现要点

- `MarkIamProvisionedStep`
- 通过 `TenantIamStateRepository`（由 sibling 任务定义）将状态置为 `IAM_PROVISIONED`、写入 `provisionedAt`。
- 已是 `IAM_PROVISIONED` 时视为成功，no-op，不抛错。
- `PublishTenantIamProvisionedEventStep`
- 从 Context 拼装 `TenantIamProvisionedEvent`（`tenantId`、`organizationId`、`adminUserId`、`correlationId`）。
- 通过 `EventPublisher`（由 sibling 任务定义）发布。
- 调用语义需按 ensure 思想保持安全可重试。
- 两个步骤均不与 Keycloak 调用放入同一本地事务（保持远程副作用与本地事务分离的约束）。

## 验收标准

- 终态步骤把状态写到 `IAM_PROVISIONED` 并发布事件。
- 重复执行不会重复推进状态或导致失败。
- 步骤仅依赖 sibling 任务的 Repository / EventPublisher 抽象。
- 本地状态写入与 Keycloak 调用之间不存在事务包裹。

## 技术约束

- 不直接依赖数据库实现、JPA、Kafka 客户端。
- `IAM_FAILED` 路径、`retryCount`、`failureCode` 的维护由 sibling 的 ProvisioningService/状态机负责，不在本步骤中处理。

## 范围边界

- **包含**：两个 Step 的实现及其幂等调用语义。
- **不包含**：Repository / EventPublisher 接口定义与实现、Service 层状态机推进、失败路径的状态记录、Kafka 集成。## Details

**Scope**: MarkIamProvisionedStep 与 PublishTenantIamProvisionedEventStep 两个 Step 的实现以及它们的幂等调用语义

**Out of Scope**: TenantIamStateRepository 与 EventPublisher 接口定义、其内存实现、TenantIamProvisioningService 状态机主流程、Kafka 集成、失败路径上的 IAM_FAILED 记录（都由 sibling 任务负责）

## Acceptance Criteria

- [ ] `MarkIamProvisionedStep` 调用 `TenantIamStateRepository` 将状态推进到 `IAM_PROVISIONED` 并记录 `provisionedAt`；状态已是 `IAM_PROVISIONED` 时不会重复写入或报错
- [ ] `PublishTenantIamProvisionedEventStep` 使用 Context 中的 `tenantId`、`organizationId`、`adminUserId`、`correlationId` 构造 `TenantIamProvisionedEvent` 并通过 EventPublisher 发布
- [ ] 两个步骤重复执行保持安全：Repository 写入幂等、事件发布不因重复调用报错
- [ ] Repository 写入与 Keycloak 远程调用不被包裹在同一个本地事务中（即两个步骤互不入同一事务边界）
- [ ] 步骤实现不引入具体存储、Kafka 或其他基础设施类型，仅依赖 sibling 任务定义的抽象

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |
