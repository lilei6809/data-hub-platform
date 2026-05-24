---
id: "043cf04b-3e25-437f-84d9-1e95031431c1"
entity_type: "task"
entity_id: "6b0523ba-6976-4a4b-a784-5ff6775f56d0"
title: "建模 TenantIamProvisioningState 实体与状态机枚举 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-136"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:48.58951+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

在领域层建立 `TenantIamProvisioningState` 实体与 `TenantIamStatus` 枚举，作为租户 IAM onboarding 本地状态机的领域核心。

## 实施方式

1. 定义 `TenantIamStatus` 枚举：PENDING_IAM、IAM_PROVISIONING、IAM_PROVISIONED、IAM_FAILED。
2. 定义 `TenantIamProvisioningState` 实体，字段包括 tenantId、iamStatus、lastAttemptAt、provisionedAt、failureCode、failureMessage、retryCount、workflowCorrelationId。
3. 暴露状态流转方法：`markProvisioning(correlationId)`、`markProvisioned(at)`、`markFailed(code, message, at)`、`incrementRetry()`。
4. 非法状态流转（如 IAM_PROVISIONED → PENDING_IAM）抛出领域异常，集中在实体内校验。
5. 编写单元测试覆盖每条合法流转、非法流转、失败字段写入、重试计数自增。

## 验收标准

- 枚举与字段与 PRD 完全一致。
- 状态机仅允许 PRD 定义的流转。
- 失败与成功字段语义符合 PRD 第 4 节。
- 领域层无任何基础设施依赖。

## 技术约束

- 必须保持领域纯净：不依赖 Spring、JPA、Keycloak、Kafka。
- 使用值对象封装 TenantId 与 CorrelationId。

## 范围边界

- ✅ 实体、枚举、状态流转方法、单元测试。
- ❌ 持久化、Service 编排、Step、Port、事件。## Details

**Scope**: TenantIamProvisioningState 实体类、TenantIamStatus 枚举、合法状态流转方法、非法流转的领域异常以及其单元测试。

**Out of Scope**: Repository 实现、ProvisioningService 编排逻辑、Step Pipeline、Keycloak Port、Desired State 模型（兄弟任务负责）、事件发布。

## Acceptance Criteria

- [ ] `TenantIamStatus` 枚举包含 PENDING_IAM、IAM_PROVISIONING、IAM_PROVISIONED、IAM_FAILED 四个值且无其他状态
- [ ] `TenantIamProvisioningState` 实体包含 tenantId、iamStatus、lastAttemptAt、provisionedAt、failureCode、failureMessage、retryCount、workflowCorrelationId 全部字段
- [ ] 实体暴露 markProvisioning、markProvisioned、markFailed、incrementRetry 等状态流转方法，非法流转（如从 IAM_PROVISIONED 回退到 PENDING_IAM）会抛出领域异常
- [ ] markFailed 时同时记录 failureCode、failureMessage 并更新 lastAttemptAt，但不清空已有 provisionedAt
- [ ] markProvisioned 会清空 failureCode、failureMessage 并写入 provisionedAt
- [ ] 领域层不引用任何 Spring、JPA、Keycloak SDK 或 Kafka 类型

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

