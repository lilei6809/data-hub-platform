---
id: "3052d612-916d-4076-bc3d-0a71d3132878"
entity_type: "task"
entity_id: "bba824b0-b333-4d0c-9e34-db881377477a"
title: "Tenant IAM Provisioning Service 与本地状态机可端到端编排 onboarding - Notes"
status: "todo"
priority: "high"
display_id: "LEI-73"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:52:25.914601+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 最新任务调整

`LEI-73` 继续作为 Tenant IAM onboarding 的 Application Service 主线任务。当前执行顺序调整为：

1. 先完成 `LEI-144`：定义 `TenantIamStateRepository` Port 与 In-Memory 实现。
2. 再完成 `LEI-155`：实现 `TenantIamProvisioningService`，由 Application Service 直接编排有序 steps，并在每个关键 checkpoint 后持久化本地状态。
3. 暂不单独实现只负责 `for` 循环的薄 `TenantIamProvisioningPipeline`。`LEI-170` 的执行语义并入 Application Service。
4. 暂不实现 `MarkIamProvisionedStep`。本地终态推进由 Application Service 调用 `TenantIamProvisioningState.markCompleted()` 完成。
5. `EventPublisher` 与事件发布后置，不阻塞当前 Application Service 闭环。

设计理由：当前核心风险不是 step 顺序本身，而是远程 Keycloak ensure 操作与本地状态 checkpoint 的边界。Application Service 必须拥有状态加载、状态转移、每步持久化、失败分类、重试状态记录这些职责；单独的薄 Pipeline 类会把流程控制和状态持久化拆散。

## 平台开发者可以通过单一应用服务驱动 onboarding 的状态流转与重试。

`TenantIamProvisioningService` 是 MVP 的端到端入口。它把"标记本地状态 → 执行幂等 Pipeline → 记录成功/失败 → 累计重试次数"串成一个显式状态机，保证远程 Keycloak 操作不会与本地事务纠缠。

## Experience

平台开发者得到一个应用服务和配套的 `TenantIamStateRepository`（含 In-Memory 实现），可以用一个 `TenantIamDesiredState + correlationId` 触发完整 onboarding。状态机覆盖 `PENDING_IAM → IAM_PROVISIONING → IAM_PROVISIONED / IAM_FAILED`，失败时记录原因、阶段和重试次数，重试只需再次调用同一方法。

## Interaction

1. 服务把状态置为 `IAM_PROVISIONING` 并提交本地事务。
2. 在事务外执行 Step Pipeline。
3. Pipeline 成功 → 状态推进到 `IAM_PROVISIONED` 并写 `provisionedAt`。
4. Pipeline 失败 → 状态回到 `IAM_FAILED`，写入 failureCode / failureMessage / retryCount。
5. 重试调用从 `IAM_FAILED` 重新进入流程，由 ensure 步骤保证幂等。## Details

**User Capability**: 平台开发者可以调用 `TenantIamProvisioningService.provisionTenantIam(desiredState, correlationId)`，由服务负责标记本地状态、执行 Step Pipeline、记录失败原因与重试次数，并最终把状态推进到 `IAM_PROVISIONED` 或 `IAM_FAILED`。

**Business Value**: 远程 Keycloak 操作不参与本地事务，必须通过显式状态机表达"处理中 / 成功 / 失败 / 可重试"等真实进度。该服务是 MVP 的端到端入口，也是后续事件消费与重试调度的挂载点。

**Functional Requirements**:
- 提供应用服务 `TenantIamProvisioningService`，方法签名：
  - `provisionTenantIam(TenantIamDesiredState desiredState, CorrelationId correlationId)`
- 状态流转：
  - 入口将状态从 `PENDING_IAM` / `IAM_FAILED` 推进至 `IAM_PROVISIONING`（本地事务）。
  - 调用 Step Pipeline，期间不持有本地事务。
  - Pipeline 成功 → 本地事务将状态推进至 `IAM_PROVISIONED`，写入 `provisionedAt`。
  - Pipeline 失败 → 本地事务把状态写回 `IAM_FAILED`，记录 `failureCode`、`failureMessage`、`lastAttemptAt`、`retryCount += 1`。
- 提供 `TenantIamStateRepository` 抽象与第一版 In-Memory 实现。
- 持久化字段必须覆盖：`tenantId`、`iamStatus`、`lastAttemptAt`、`provisionedAt`、`failureCode`、`failureMessage`、`retryCount`、`workflowCorrelationId`。
- 同一 `tenantId` 不允许两个 `IAM_PROVISIONING` 并发（通过状态机或乐观锁约束）。
- 失败信息不得写入 secret / 临时密码 / IdP secret。

**Data Model & Structure**:
- 枚举：`TenantIamStatus { PENDING_IAM, IAM_PROVISIONING, IAM_PROVISIONED, IAM_FAILED }`。
- `TenantIamProvisioningState` 聚合（字段同上）。
- `FailureCode` 枚举（最小集合：`KEYCLOAK_UNAVAILABLE`、`STEP_FAILED`、`UNEXPECTED_ERROR`）。

**Technical Approach**:
- 应用服务位于 application 层，依赖 Port 与 Step Pipeline 抽象。
- 状态推进与 Step 执行解耦，避免把远程调用放进本地事务。
- Repository 第一版用线程安全的内存实现；为后续 JDBC/JPA 适配预留接口稳定性。

**User Workflows**:
- 首次 onboarding：Service 创建/读取状态记录 → 设置 `IAM_PROVISIONING` → 执行 Pipeline → 成功后 `IAM_PROVISIONED`。
- 重试：Service 从 `IAM_FAILED` 重新进入 `IAM_PROVISIONING` → 重新执行 Pipeline → 由 ensure 语义保证幂等。
- 重复请求：状态已是 `IAM_PROVISIONED` 时 Service 可选择短路返回成功（不重新执行 Pipeline）或重新 reconcile，需有明确策略。

**Scope - INCLUDED**:
- `TenantIamProvisioningService` 实现。
- `TenantIamStateRepository` 抽象与 In-Memory 实现。
- 状态机推进逻辑、失败记录、retryCount 管理。
- 单元测试：首次成功、重复执行、中途失败后重试、状态字段断言。

**Scope - EXCLUDED**:
- Step 实现本身（"Idempotent Step Pipeline"任务）。
- Keycloak Port/Adapter（"Keycloak Admin Port"任务）。
- Kafka 消费与发布（Phase 3 任务）。
- 真实数据库 schema 与持久化适配（Phase 2/后续任务）。
- 事件 schema 定义（"事件边界契约"任务）。

**Success Criteria**:
- MVP 验收场景全部通过：首次创建、重复执行无重复对象、中途失败后重试可完成、状态字段正确流转。
- 在 Fake Keycloak 上执行端到端 onboarding 用例可重复运行，状态机最终为 `IAM_PROVISIONED`。
- 日志中始终包含 `tenantId` 与 `correlationId`，不出现 secret。

**Constraints & Considerations**:
- 远程调用严禁包裹在本地事务中。
- 服务层不得依赖 Keycloak SDK 或 Kafka 客户端。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | 幂等 Provisioning Step Pipeline 可 reconcile 租户身份事实 |
