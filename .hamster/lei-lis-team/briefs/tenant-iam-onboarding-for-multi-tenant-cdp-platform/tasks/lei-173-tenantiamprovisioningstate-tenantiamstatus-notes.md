---
id: "518881e9-8b89-461b-9106-2a7571616ce4"
entity_type: "task"
entity_id: "7ed24ddf-e6a4-4f83-a0a1-adf8978ecbac"
title: "建模 TenantIamProvisioningState 与 TenantIamStatus 状态机 - Notes"
status: "done"
priority: "high"
display_id: "LEI-173"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:42.588198+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现描述租户 IAM provisioning 进度的领域状态对象 TenantIamProvisioningState 与显式状态机枚举 TenantIamStatus。

## Implementation Approach

1. 定义 TenantIamStatus 枚举（PENDING_IAM / IAM_PROVISIONING / IAM_PROVISIONED / IAM_FAILED）。
2. 定义 FailureCode 枚举或受限值对象，避免自由文本失败原因。
3. 定义 TenantIamProvisioningState 含 8 个字段；提供 initialize(tenantId, correlationId) 工厂。
4. 实现状态转移方法 markProvisioning / markProvisioned / markFailed，校验前置状态、更新时间戳与 retryCount。
5. 非法转移抛出领域异常；toString 不泄漏 secret。
6. 单元测试覆盖初始状态、合法转移序列、非法转移、retryCount 累加与时间戳更新。

## Acceptance Criteria

- TenantIamStatus 枚举包含 4 个状态
- TenantIamProvisioningState 包含 8 个字段
- initialize 返回 PENDING_IAM 初始状态
- 状态转移方法校验前置状态，违反抛领域异常
- markFailed 累加 retryCount；markProvisioned 设置 provisionedAt 并清失败字段
- FailureCode 为枚举/受限值对象
- 不引入基础设施依赖
- 单元测试覆盖全部转移与不变量

## Technical Constraints

- 远程 Keycloak 调用不参与本地事务；状态机必须能独立表达进度
- 不允许使用布尔标志替代状态
- 失败原因必须枚举化，不允许自由文本作为唯一信号## Details

**Scope**: TenantIamProvisioningState 领域对象、TenantIamStatus 枚举、状态转移方法（markProvisioning / markProvisioned / markFailed）与不变量、单元测试

**Out of Scope**: TenantIamStateRepository 接口与内存实现（属 Step Pipeline / Provisioning Service 兄弟任务）、真实数据库 schema 与 PostgreSQL 集成（后续阶段）、Step Pipeline 编排与失败重试逻辑、事件契约

**Implementation**: 1. 定义 TenantIamStatus 枚举：PENDING_IAM、IAM_PROVISIONING、IAM_PROVISIONED、IAM_FAILED。
2. 定义 FailureCode 枚举或值对象（最小集合，例如 KEYCLOAK_UNAVAILABLE、UNKNOWN_ERROR），用于失败语义而非自由文本。
3. 定义 TenantIamProvisioningState（推荐 final class，包含可控变更方法）：字段 tenantId、iamStatus、lastAttemptAt、provisionedAt、failureCode、failureMessage、retryCount、workflowCorrelationId。
4. 提供静态工厂 initialize(tenantId, correlationId) 产生 PENDING_IAM 初始状态。
5. 实现状态转移方法：markProvisioning(now)、markProvisioned(now)、markFailed(now, failureCode, failureMessage)；每个方法校验合法的前置状态并更新 retryCount / 时间戳字段。
6. 任何非法状态转移抛出领域异常。
7. failureMessage toString 不泄漏 secret（保持简短领域语义）。
8. 编写单元测试覆盖：初始状态、合法转移序列（PENDING → PROVISIONING → PROVISIONED / FAILED → PROVISIONING 重试）、非法转移、retryCount 累加、provisionedAt 仅在 PROVISIONED 设置。

## Acceptance Criteria

- [ ] TenantIamStatus 枚举包含 PENDING_IAM、IAM_PROVISIONING、IAM_PROVISIONED、IAM_FAILED 四个状态
- [ ] TenantIamProvisioningState 包含 tenantId、iamStatus、lastAttemptAt、provisionedAt、failureCode、failureMessage、retryCount、workflowCorrelationId 八个字段
- [ ] 提供 initialize(tenantId, correlationId) 工厂返回 PENDING_IAM 初始状态，retryCount=0，未设置 provisionedAt
- [ ] markProvisioning / markProvisioned / markFailed 方法只允许合法前置状态下调用，违反抛出领域异常
- [ ] markFailed 递增 retryCount 与更新 lastAttemptAt；markProvisioned 设置 provisionedAt 且 failureCode/Message 被清除
- [ ] FailureCode 为枚举或受限值对象，不使用自由文本
- [ ] 状态对象不引入 Spring、JPA、Repository、Keycloak SDK、Kafka 依赖
- [ ] 单元测试覆盖初始状态、合法转移路径、非法转移、重试计数与时间戳更新

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |
