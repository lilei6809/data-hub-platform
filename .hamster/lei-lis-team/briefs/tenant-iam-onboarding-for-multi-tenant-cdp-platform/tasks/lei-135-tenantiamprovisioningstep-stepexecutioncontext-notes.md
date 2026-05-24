---
id: "326533a4-f50d-4eea-9d83-77851abad6b7"
entity_type: "task"
entity_id: "34b66095-3526-46b4-94f9-429be6d32b48"
title: "定义 TenantIamProvisioningStep 接口与 StepExecutionContext - Notes"
status: "todo"
priority: "high"
display_id: "LEI-135"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:46.205629+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 `TenantIamProvisioningStep` 接口与 `StepExecutionContext`，作为 Step Pipeline 中所有 ensure 步骤的统一协议。

## 实现思路

1. 定义 `TenantIamProvisioningStep` 接口：`StepExecutionContext ensure(TenantIamDesiredState desired, StepExecutionContext context)`。
2. 定义 `StepExecutionContext` 不可变值对象，承载步骤之间传递的身份事实：`organizationId`、`adminUserId` 等，使用 Optional 表达尚未填充的字段。
3. 提供 `withOrganizationId`、`withAdminUserId` 等派生方法返回新的 ExecutionContext 实例。
4. 允许 Step 实现通过领域异常表达失败；接口本身不感知具体 Adapter 类型。
5. 编写契约级单元测试验证 ExecutionContext 不可变性与字段累积行为。

## 验收标准

- `TenantIamProvisioningStep` 接口暴露单一 ensure 方法，签名为 `(TenantIamDesiredState, StepExecutionContext) -> StepExecutionContext`。
- `StepExecutionContext` 以不可变值对象方式承载已 ensure 的身份事实，提供 with 风格派生方法。
- Step 接口不依赖任何 Keycloak SDK、HTTP 或框架类型。
- 单元测试覆盖空上下文、累积身份事实、上下文不可变性。

## 技术约束

- Step 协议必须保持框架无关，便于在 MVP 与未来事件驱动调度中复用。
- ExecutionContext 不得使用 ThreadLocal 或可变全局状态。
- 接口本身不强制 Spring 注解。

## 范围边界

- 仅交付 Step 接口与 ExecutionContext，**不**实现具体 Step。
- **不**定义 KeycloakAdminPort 方法、Service 编排或 Desired State 模型（由兄弟任务负责）。## Details

**Scope**: Step 接口定义、StepExecutionContext 值对象、Step 协议的契约级单元测试

**Out of Scope**: 具体 Step 实现（在其他子任务）、KeycloakAdminPort 方法（兄弟任务）、TenantIamProvisioningService 编排（兄弟任务）、Desired State 定义（兄弟任务）

## Acceptance Criteria

- [ ] `TenantIamProvisioningStep` 接口暴露单一 ensure 方法，输入为 TenantIamDesiredState 与 StepExecutionContext，返回更新后的 StepExecutionContext
- [ ] `StepExecutionContext` 以不可变值对象方式承载已 ensure 的身份事实（至少包含 organizationId、adminUserId 字段，使用 Optional 表达尚未填充），并提供 with 风格的派生方法
- [ ] Step 接口允许通过领域异常表达失败，不依赖任何 Keycloak SDK 类型、HTTP 类型或框架注解
- [ ] 针对接口与 ExecutionContext 的契约编写单元测试，覆盖空上下文、累积身份事实、上下文不可变性

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

