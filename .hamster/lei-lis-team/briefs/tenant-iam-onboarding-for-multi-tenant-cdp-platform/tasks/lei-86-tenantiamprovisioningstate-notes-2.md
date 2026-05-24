---
id: "848633e9-a564-4bad-9266-0b8281126994"
entity_type: "task"
entity_id: "920fbd7f-a972-454b-97cc-146c317c16fc"
title: "建模 TenantIamProvisioningState 聚合与状态机转换规则 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-86"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:01.723993+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

建立本地 IAM provisioning 状态机的领域聚合 `TenantIamProvisioningState`，承载状态字段、`TenantIamStatus` 枚举与合法转换规则。

## 实现思路

1. 在 IAL 领域层新增 `TenantIamProvisioningState` 聚合，字段对齐 PRD：tenantId、iamStatus、lastAttemptAt、provisionedAt、failureCode、failureMessage、retryCount、workflowCorrelationId。
2. 定义 `TenantIamStatus` 枚举：PENDING_IAM、IAM_PROVISIONING、IAM_PROVISIONED、IAM_FAILED。
3. 在聚合内集中状态机规则：

- PENDING_IAM → IAM_PROVISIONING（markAttemptStarted，更新 lastAttemptAt）
- IAM_PROVISIONING → IAM_PROVISIONED（markProvisioned，写 provisionedAt，清理 failureCode/failureMessage）
- IAM_PROVISIONING → IAM_FAILED（markFailed，写 failureCode/failureMessage，retryCount++）
- IAM_FAILED → IAM_PROVISIONING（重试入口）

1. 非法转换抛出 `IllegalIamStateTransition` 领域异常。
2. 使用值对象包装 TenantId、CorrelationId，保持领域纯净。
3. 编写单元测试覆盖每条合法转换与代表性非法转换。

## 验收标准

- 聚合字段与 PRD 一致并用值对象包装关键标识
- 状态枚举包含四个标准值
- 所有合法转换都有对应行为方法并维护辅助字段
- 非法转换抛出领域异常
- 单元测试覆盖合法与非法路径
- 领域层不依赖持久化或 Keycloak SDK

## 技术约束

- 领域层不感知 Spring、JPA、Kafka 等基础设施
- 状态字段只能通过行为方法修改，不暴露公共 setter
- 转换规则集中在聚合内，避免泄漏到应用服务

## 相关技能

- Hamster Blueprint## Details

**Scope**: TenantIamProvisioningState 聚合、TenantIamStatus 枚举、状态转换行为方法与规则、对应单元测试

**Out of Scope**: 持久化接口与实现（由 Repository 子任务负责）、应用服务编排（由 Service 子任务负责）、事件发布（由兄弟父任务的事件契约负责）、Desired State 模型（由兄弟父任务负责）

## Acceptance Criteria

- [ ] TenantIamProvisioningState 聚合包含 PRD 列出的所有字段并使用值对象包装 TenantId、CorrelationId
- [ ] TenantIamStatus 枚举包含 PENDING_IAM、IAM_PROVISIONING、IAM_PROVISIONED、IAM_FAILED 四个值
- [ ] 聚合暴露 markAttemptStarted、markProvisioned、markFailed 行为方法，自动维护 lastAttemptAt、provisionedAt、failureCode、failureMessage、retryCount
- [ ] 非法状态转换（例如 IAM_PROVISIONED -> PENDING_IAM）抛出领域异常并附带当前状态和目标状态
- [ ] 单元测试覆盖所有合法转换路径与至少 2 条非法转换的拒绝路径
- [ ] 聚合不依赖任何持久化框架、Spring 注解或 Keycloak SDK

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

