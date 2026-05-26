---
id: "a4f5bd42-ee6b-40d9-adad-7b90010ccd93"
entity_type: "task"
entity_id: "330e85c2-f6ff-484b-ab0e-d747b9c4ab2a"
title: "实现 Step Pipeline 顺序组合与执行器 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-170"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:13.44996+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 最新任务调整

本任务暂不按原描述实现独立 `TenantIamProvisioningPipeline` 类。

原因：当前 provisioning 流程不只是顺序执行 steps，还必须在每个关键步骤之间更新并持久化 `TenantIamProvisioningState`。如果 Pipeline 只做：

```java
for (TenantIamProvisioningStep step : steps) {
    context = step.ensure(desired, context);
}
```

它会成为薄封装，并把“step 执行顺序”和“本地状态 checkpoint”拆开，反而增加理解成本。

调整后的任务语义：

- 有序 step 编排先由 `TenantIamProvisioningService` 完成。
- `LEI-155` 中实现 step 顺序、context 传递、异常中断、checkpoint 持久化。
- 若后续 Application Service 变重，再抽取 `TenantIamProvisioningPipelineExecutor`，但该 Executor 必须同时承载 checkpoint、失败处理和持久化策略，而不是只做顺序循环。

## 摘要

提供把 Step 顺序组合并执行的 Pipeline 类，承载 MVP 的 7-Step 默认装配。

## 实现思路

1. 定义 `TenantIamProvisioningPipeline`，构造函数接收有序 `List<TenantIamProvisioningStep>`。
2. 提供 `execute(TenantIamDesiredState desired, StepExecutionContext initialContext)` 方法：

- 按顺序遍历 Step，使用上一步返回的 ExecutionContext 作为下一步输入。
- 任一 Step 抛出异常 → 立即终止并向上传播。
- 全部完成 → 返回最终 ExecutionContext。

1. 提供工厂方法（或显式 Builder）装配默认 7-Step 顺序，便于测试与未来扩展。
2. 编写单元测试使用 Mock Step 验证顺序、ExecutionContext 传递、异常终止。

## 验收标准

- 7-Step 默认装配顺序正确。
- ExecutionContext 在 Step 之间正确传递。
- 异常立即终止且不吞错。
- 单元测试覆盖以上行为。

## 技术约束

- Pipeline 不捕获异常、不维护状态、不感知事件发布。
- 不引入重试、调度、并行等机制（MVP 范围）。

## 范围边界

- **不**包含 TenantIamProvisioningService 的状态机、错误处理、失败事件发布（兄弟任务）。
- **不**实现具体 Step（其他子任务）或 Port/Adapter（兄弟任务）。## Details

**Scope**: Pipeline 类与默认 7-Step 装配，以及针对顺序执行与异常传播的单元测试

**Out of Scope**: TenantIamProvisioningService 状态机与错误处理（兄弟任务）、具体 Step 实现、KeycloakAdminPort / Fake Adapter、重试调度 / 后台任务

## Acceptance Criteria

- [ ] Pipeline 按计划顺序执行第一版 7 个 Step，上一步返回的 ExecutionContext 被作为下一步输入
- [ ] 任一 Step 抛出领域异常时 Pipeline 立即终止并向上传播异常，不执行后续 Step
- [ ] Pipeline 以明确装配方式暴露默认 Step 顺序，便于测试中替换或裁剪
- [ ] 使用 Mock Step 编写单元测试验证：顺序调用、ExecutionContext 传递、异常提前终止

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
