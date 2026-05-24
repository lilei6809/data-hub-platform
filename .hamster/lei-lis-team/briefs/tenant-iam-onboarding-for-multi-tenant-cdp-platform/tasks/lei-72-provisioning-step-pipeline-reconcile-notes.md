---
id: "cb314a4b-2645-4448-8bdc-7292198a7135"
entity_type: "task"
entity_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
title: "幂等 Provisioning Step Pipeline 可 reconcile 租户身份事实 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-72"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:51:59.293539+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台开发者可以把 onboarding 表达为一组可重试的 ensure 步骤。

幂等 Step Pipeline 是 Tenant IAM Onboarding 的执行核心。把 reconcile 拆成"对象/属性/关系/角色/状态/事件"等独立步骤，每个都用 ensure 语义实现，让重复事件、部分失败和重试都能安全收敛。

## Experience

平台开发者得到一个 `TenantIamProvisioningStep` 抽象，以及第一版 7 个步骤：Ensure Organization、Ensure Organization Attributes、Ensure Admin User、Ensure Organization Membership、Ensure Tenant Admin Role、Mark Iam Provisioned、Publish Tenant Iam Provisioned Event。Pipeline 编排器按顺序执行步骤，任何一步失败都可以原地重试，已完成的步骤通过 ensure 走 REUSED 分支。

## Interaction

1. Service 把 Desired State 注入 Pipeline。
2. Pipeline 顺序执行步骤，单步失败即停止并保留可恢复性。
3. 重试时所有 ensure 步骤检查已有对象/关系并复用。
4. 全部成功后状态机推进到 `IAM_PROVISIONED` 并发布事件。## Details

**User Capability**: 平台开发者可以把 `TenantIamDesiredState` 输入到一条由独立 Step 组成的 Pipeline 中，逐步把 Keycloak 与本地状态向 Desired State reconcile，重复执行、部分失败重试都安全。

**Business Value**: 用幂等步骤替代一次性创建脚本，让租户 onboarding 在网络抖动、Keycloak 短暂故障、重复事件投递等真实场景下仍能收敛到正确状态，避免脏数据与冲突失败。

**Functional Requirements**:
- 定义 `TenantIamProvisioningStep` 接口与 `StepExecutionContext`（携带 `tenantId`、`correlationId`、Port 引用、Desired State 等）。
- 实现第一版步骤（顺序由 Pipeline 编排）：
  1. `EnsureOrganizationStep`
  2. `EnsureOrganizationAttributesStep`（写入并校正 `tenant_id` 与 `tier`）
  3. `EnsureAdminUserStep`
  4. `EnsureOrganizationMembershipStep`
  5. `EnsureTenantAdminRoleStep`（确保 `TENANT_ADMIN` Realm Role 存在并分配给 admin user）
  6. `MarkIamProvisionedStep`（驱动本地状态机进入 `IAM_PROVISIONED`）
  7. `PublishTenantIamProvisionedEventStep`（通过 EventPublisher 发布事件）
- 每个 Step 的 ensure 语义：
  - 目标对象/关系不存在 → 创建。
  - 目标对象/关系已存在 → 复用。
  - Keycloak 返回 409 → 查询已有对象后继续。
  - 属性不一致 → 按 Desired State 校正。
- Pipeline 编排器：
  - 顺序执行 Step。
  - 单个 Step 失败时停止后续步骤、记录失败原因、保留可恢复性。
  - 支持重新执行整条 Pipeline，由 ensure 语义保证幂等。

**Data Model & Structure**:
- `StepExecutionContext`：`tenantId`、`correlationId`、`desiredState`、Port 句柄、可写的 step result 集合。
- `StepResult { stepName, outcome (CREATED | REUSED | RECONCILED | SKIPPED), durationMs }`。

**Technical Approach**:
- 每个 Step 是无状态可注入组件，通过构造函数注入 Port。
- Pipeline 顺序由配置/工厂决定，不写死在 Service。
- 与 `TenantIamProvisioningService` 解耦：Pipeline 不负责状态机持久化，只负责执行步骤并报告结果。

**User Workflows**:
- 正常路径：所有 Step 依次成功 → 状态机推进 → 事件发布。
- 重复执行：所有 ensure Step 走 REUSED 分支，不创建重复对象。
- 中途失败：失败 Step 抛出领域错误 → Pipeline 停止 → 重试时已完成步骤走 REUSED。

**Scope - INCLUDED**:
- Step 接口、第一版步骤实现、Pipeline 编排器。
- 单元测试覆盖每个 Step 的 ensure 语义（创建 / 复用 / 409 回退 / 属性校正）。
- 集成测试覆盖整条 Pipeline 的首次执行、重复执行、中途失败后重试。

**Scope - EXCLUDED**:
- Keycloak Port 与 Fake Adapter 实现（依赖任务负责）。
- `TenantIamProvisioningService` 的状态机持久化与外层错误处理（"Provisioning Service 与状态机"任务负责）。
- 事件的真实 Kafka 发布（Phase 3 任务）；本任务通过 `EventPublisher` 接口完成调用即可。

**Success Criteria**:
- 同一个 Desired State 连续执行 N 次，Fake Keycloak 状态等价于执行一次。
- 中途失败后重试可以完成剩余步骤，无重复对象。
- Organization 包含正确的 `tenant_id` 和 `tier` 属性。
- Admin user 拥有 `TENANT_ADMIN` 角色并属于目标 Organization。

**Constraints & Considerations**:
- Step 不允许直接依赖具体 Keycloak SDK 或 Kafka 客户端。
- 不允许把 secret / 临时密码 / 角色细节明文打印到日志，仅记录 `tenantId`、`correlationId`、`stepName`、`outcome`。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant IAM Desired State 领域模型可表达租户身份事实, Keycloak Admin Port 与 Fake Adapter 可隔离外部身份系统 |

