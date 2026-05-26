---
id: "d4f4a504-dbf8-4cdf-9c34-6afdc5cb4786"
entity_type: "task"
entity_id: "24b45668-5441-423d-8477-e2c74aa7d837"
title: "定义 TenantIamProvisioningStep 接口与 StepExecutionContext - Notes"
status: "todo"
priority: "high"
display_id: "LEI-88"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:08.2408+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 Step 契约调整

`TenantIamProvisioningStep` 的接口注释不应再要求“Keycloak 409 由步骤自身查询后继续”。

新的接口契约是：

- Step 调用 `KeycloakAdminPort.ensureXxx(...)`，依赖 Port/Adapter 提供幂等保证。
- Step 不处理 Keycloak 409、HTTP status、SDK exception。
- Step 可以处理两类事情：Context 前置值缺失，以及 Port 抛出的端口级异常到 `IamProvisioningException` 的翻译。
- `StepExecutionContext` 只保存跨步骤需要的业务标识，例如 `OrganizationId`、`UserId`，不保存 Keycloak 原生 representation。

## 摘要

定义 Step Pipeline 的基础契约：`TenantIamProvisioningStep` 接口与 `StepExecutionContext`，作为后续所有 Ensure 步骤和 Pipeline 组装的共同基石。

## 实现要点

- 定义 `TenantIamProvisioningStep` 接口，约定一个 `execute(StepExecutionContext)` 方法。
- 定义 `StepExecutionContext`：承载只读输入（`TenantIamDesiredState`、`CorrelationId`），以及可被前序步骤填充、后续步骤读取的中间产物（`organizationId`、`adminUserId`、`realmRoleName` 等 Keycloak 标识符）。
- 在接口注释中明确 ensure 语义由 Port/Adapter 提供：不存在则创建、已存在则复用、关系已存在视为成功、属性不一致按规则校正、Keycloak 409 由 Adapter 查询后继续。
- 接口与 Context 不引用 Keycloak SDK / HTTP / DB / Kafka 类型，所有外部交互依赖 sibling 任务定义的 Port。

## 验收标准

- `TenantIamProvisioningStep` 接口落地并文档化 ensure 语义。
- `StepExecutionContext` 同时支持只读 desired state 与累积中间产物。
- 接口/Context 不泄漏任何外部基础设施类型。
- 有示例或文档展示步骤如何使用 Context。

## 技术约束

- 必须放在领域/应用层，独立于具体适配器。
- Context 的中间产物结构需要可扩展，但第一版仅包含 Pipeline 已知需要的字段，不预先设计未使用字段。

## 范围边界

- **包含**：Step 接口、Context 数据结构、ensure 语义文档化。
- **不包含**：具体 Ensure 步骤实现、Pipeline 执行器、状态机推进、事件发布、Port 定义。## Details

**Scope**: TenantIamProvisioningStep 接口、StepExecutionContext 结构，以及 ensure 语义在接口契约层的文档化

**Out of Scope**: 任何具体 Ensure 步骤实现、Pipeline 执行器、KeycloakAdminPort 定义、状态机推进、事件发布

## Acceptance Criteria

- [ ] `TenantIamProvisioningStep` 接口定义 execute 方法，接收 `StepExecutionContext`，并在合约层面要求实现采用 ensure 语义而非 create 语义
- [ ] `StepExecutionContext` 包含只读的 `TenantIamDesiredState` 与 `CorrelationId`，并提供可由前序步骤写入、后续步骤读取的中间产物槽位（如 organizationId、adminUserId）
- [ ] 接口与 Context 不引用 Keycloak SDK、HTTP、数据库或 Kafka 类型，所有外部交互通过 sibling 任务定义的 Port 抽象表达
- [ ] 接口的 Javadoc/注释明确说明：目标对象不存在则创建、已存在则复用、关系已存在视为成功、属性不一致按规则校正、Keycloak 409 由 Adapter 在 Port 契约内消解，Step 不自行处理
- [ ] 存在最少一个编译级或文档级示例说明步骤如何读取 desired state 并写入中间产物

## Context

| Field | Value |
|-------|-------|
| category | design |
| complexity | 3 |
| skillReferences | Hamster Blueprint |
