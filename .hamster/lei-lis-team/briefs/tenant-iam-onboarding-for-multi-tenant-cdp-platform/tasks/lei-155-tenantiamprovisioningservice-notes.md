---
id: "692156c7-2b07-4fc6-b7e6-e8a5f7b14610"
entity_type: "task"
entity_id: "f08d851e-7646-463f-bf6e-9553913d89de"
title: "实现 TenantIamProvisioningService 端到端编排与状态流转 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-155"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:05.661504+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 最新任务调整

`LEI-155` 是当前下一步核心实现任务，优先级高于重复任务 `LEI-112`。

当前设计以 Application Service 直接编排有序 `TenantIamProvisioningStep`，不再依赖一个只负责顺序循环的薄 `TenantIamProvisioningPipeline`。服务职责调整为：

- 通过 `TenantIamStateRepository.findOrInitialize(...)` 加载或初始化本地状态。
- 执行前调用 `state.markInProgress(now)` 并保存。
- 顺序执行 steps；每个关键 step 成功后调用对应的 `state.markXxx(...)` 并保存 checkpoint。
- 所有 Keycloak ensure step 成功后调用 `state.markCompleted(now)` 并保存。
- 捕获 `IamProvisioningException` 后，根据 `retryable` 决定调用 `markAwaitRetry(...)` 或 `markFailed(...)`，不能所有失败都直接进入终态 `FAILED`。
- Application Service 不直接依赖 Keycloak SDK、Kafka 或具体 Adapter。

当前 checkpoint 映射建议：

- `EnsureOrganizationStep` 成功后：`markOrganizationCreated(now)`
- `EnsureAdminUserStep` 成功后：`markAdminUserCreated(now)`
- `EnsureTenantAdminRoleStep` 与 `EnsureOrganizationMembershipStep` 都成功后：`markDefaultRolesAssigned(now)`

`MarkIamProvisionedStep` 不作为 step 实现；本地完成态由 Application Service 调用领域状态机方法推进。

## 概述

实现 `TenantIamProvisioningService`，作为驱动租户 IAM onboarding 的单一应用入口，负责状态机推进、失败记录与重试入口。

## 实施方式

1. 依赖注入：`TenantIamStateRepository`、有序 `List<TenantIamProvisioningStep>`（由兄弟任务提供接口）。
2. `provisionTenantIam(TenantIamDesiredState desiredState, CorrelationId correlationId)`：

- 通过 `repository.findOrInitialize(tenantId, correlationId)` 取得状态。
- 若状态已是 `IAM_PROVISIONED`，仍允许执行（依赖 Step 幂等）以验证收敛，但不强制；调用 `markProvisioning(correlationId)` 并 `save`。
- 依次执行每个 Step；任一 Step 抛出领域异常时，捕获并 `markFailed(code, message, now)`、`incrementRetry()`、`save`，然后向上重新抛出（或转换为应用层失败结果）。
- 全部成功后 `markProvisioned(now)` 并 `save`。

1. 使用 MDC 或显式日志参数，将 `tenantId`、`correlationId` 注入每条日志；统一禁止打印凭证、临时密码等敏感字段。
2. 不开启本地事务包裹 Step 执行：Step 内部远程调用与本地状态写入分离。
3. 单元测试：使用 In-Memory Repository + 测试替身 Step 验证：

- 首次执行：PENDING_IAM → IAM_PROVISIONING → IAM_PROVISIONED。
- 第二次执行：直接基于已存状态推进且无异常。
- 某个 Step 抛异常：状态变为 IAM_FAILED 且 retryCount=1，failureCode/message 写入。
- 失败后重试：状态从 IAM_FAILED → IAM_PROVISIONING → IAM_PROVISIONED，retryCount=2。

## 验收标准

- 单一入口方法存在且签名符合 PRD。
- 状态机流转覆盖成功、失败、重试三条路径。
- 日志强制包含 tenantId 与 correlationId 且不泄漏敏感数据。
- 不在本地事务内包裹 Step 执行。

## 技术约束

- 不直接依赖 Keycloak SDK 或 Step 的具体实现。
- 通过端口与接口与外部协作。
- 失败必须以领域异常或失败码方式记录，不允许吞没异常。

## 范围边界

- ✅ 服务实现、状态流转编排、失败记录、重试入口、单元测试。
- ❌ Step 接口与具体 Step 实现、KeycloakAdminPort、Desired State 模型、事件发布、真实 Keycloak/Kafka。## Details

**Scope**: TenantIamProvisioningService 类、provisionTenantIam 入口方法、加载/初始化状态、调用 Step Pipeline、根据成败/失败推进状态机、记录 retryCount和 lastAttemptAt、失败原因映射、correlationId 贯穿日志。

**Out of Scope**: Step 接口与具体 Step 实现（兄弟任务）、KeycloakAdminPort 与 Fake Adapter（兄弟）、Desired State 领域模型（兄弟）、EventPublisher 与事件发布（兄弟）、状态实体/Repository 本身的定义（同父下独立子任务）、真实 Keycloak Adapter、Kafka、Tenant Context。

## Acceptance Criteria

- [ ] `TenantIamProvisioningService.provisionTenantIam(desiredState, correlationId)` 作为唯一应用用例入口存在
- [ ] 服务启动时调用 repository.findOrInitialize 加载或初始化状态，并在执行 Step 前将状态推进为 IAM_PROVISIONING 并持久化
- [ ] 所有 Step 成功执行后，状态推进为 IAM_PROVISIONED 并记录 provisionedAt
- [ ] 任何 Step 抛出领域异常时，状态推进为 IAM_FAILED，并写入 failureCode、failureMessage、lastAttemptAt，retryCount 递增
- [ ] 重复调用 provisionTenantIam 对同一 tenantId（无论上次是成功还是失败）均不会报错，且能从当前状态继续推进（依赖 Step 幂等性）
- [ ] 日志中每条消息都包含 tenantId 与 correlationId，且不输出临时密码、secret 或 IdP 凭证
- [ ] 不使用本地事务包裹 Step 执行；状态写入发生在 Step 调用前后、独立于远程调用

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
