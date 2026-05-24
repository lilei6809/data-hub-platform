---
id: "c1ff3582-7636-4216-8d87-bf4fbaba4ab1"
entity_type: "task"
entity_id: "6d4eccf8-2e77-4995-9615-dd5d2753201f"
title: "组装默认 Step Pipeline 并定义顺序执行语义 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-131"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:59.028726+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

把 7 个 Ensure 步骤按 PRD 顺序组装成默认 Pipeline，并定义其顺序执行 + fail-fast + 共享 Context 的执行语义。

## 实现要点

- 默认步骤顺序：

1. `EnsureOrganizationStep`
2. `EnsureOrganizationAttributesStep`
3. `EnsureAdminUserStep`
4. `EnsureOrganizationMembershipStep`
5. `EnsureTenantAdminRoleStep`
6. `MarkIamProvisionedStep`
7. `PublishTenantIamProvisionedEventStep`

- Pipeline 持有一个有序的 `TenantIamProvisioningStep` 列表，按顺序遍历执行，共享同一 `StepExecutionContext`。
- 步骤抛出异常 → 立即停止剩余步骤、原样抛出（不吞、不包装为成功）。状态推进与失败记录由 sibling 的 `TenantIamProvisioningService` 处理。
- 步骤列表通过构造注入或配置注入提供，方便后续在不修改 Pipeline 实现的情况下新增扩展 Step。

## 验收标准

- 默认 Pipeline 严格按 PRD 顺序包含 7 个步骤。
- 单一 `StepExecutionContext` 在步骤之间传递。
- 任一步骤异常导致 fail-fast。
- 步骤列表可扩展，无需修改 Pipeline 执行逻辑。
- Pipeline 不直接依赖外部基础设施。

## 技术约束

- 顺序执行；不引入并行或并发控制。
- 不在 Pipeline 内部捕获和转换异常（异常治理是 sibling 的 ProvisioningService 责任）。

## 范围边界

- **包含**：Pipeline 组件、默认 Step 顺序、Context 生命周期、fail-fast 语义、扩展点。
- **不包含**：状态机推进、失败记录、重试调度、并发控制、事件订阅入口、具体步骤实现。## Details

**Scope**: 默认 Pipeline 的步骤列表、顺序执行语义、StepExecutionContext 的生命周期、可扩展点设计

**Out of Scope**: TenantIamProvisioningService 的状态机推进与失败记录（sibling）、具体步骤实现、重试调度、并发控制、事件订阅入口

## Acceptance Criteria

- [ ] 存在一个可被 `TenantIamProvisioningService` 调用的 Pipeline 组件，其默认 Step 列表严格按 PRD 顺序包含七个 Ensure 步骤
- [ ] Pipeline 使用同一个 `StepExecutionContext` 依次调用所有步骤，使后续步骤能读取前序步骤写入的标识（organizationId、adminUserId）
- [ ] 任一步骤抛出异常时 Pipeline 立即停止执行后续步骤并将异常原样向上抛出，不被吞掉也不被包装成成功
- [ ] 步骤列表通过构造注入或配置提供，能在不修改 Pipeline 执行逻辑的前提下追加未来扩展 Step（如 IdP/MFA 步骤）
- [ ] Pipeline 组件不直接依赖 Keycloak SDK、数据库、Kafka，只依赖 Step 接口与 Context

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

