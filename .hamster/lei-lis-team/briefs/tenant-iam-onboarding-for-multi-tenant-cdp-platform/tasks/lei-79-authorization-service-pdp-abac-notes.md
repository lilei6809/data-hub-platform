---
id: "ddcbe2b8-42f8-465e-b28b-a77c3b18944b"
entity_type: "task"
entity_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
title: "Authorization Service PDP 契约与聚合可承载 ABAC 决策 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-79"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:55:00.687136+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 平台开发者可以通过 authorization-service 集中执行 ABAC 决策。

Authorization BC 是 Core Domain。把 Role、RoleAssignment、Policy 聚合统一收敛到单一 authorization-service，并通过 `POST /api/v1/authorization/evaluate` 提供 PDP 接口、通过 Outbox 可靠发布事件，避免业务服务复制策略评估逻辑。

## Experience

平台开发者得到三个聚合（`Role`、`RoleAssignment`、`Policy`）+ `PolicyRule` + `AuthorizationDecision` 模型，以及 HTTP 端点 `POST /api/v1/authorization/evaluate`。`ALLOW` 与 `DENY` 都返回 `200`，`503` 表示 PDP 不可用以驱动调用方 Fail-Closed。所有决策与绑定变更通过 Outbox 与本地事务一起落库，再异步发布。

## Interaction

1. PEP 提交完整 `EvaluationContext`（subject / resource / action / environment）。
2. `Policy.evaluate()` 集中执行 ABAC 评估。
3. 决策返回 `AuthorizationDecision`，DENY 时带枚举 `denyReason`。
4. Schema-per-Tenant 访问通过 `SET LOCAL search_path` 在显式事务内完成。
5. 决策与聚合变更事件通过 Outbox 与本地事务一致地发布。## Details

**User Capability**: 平台开发者可以把所有资源级授权决策集中交给 authorization-service：通过 `POST /api/v1/authorization/evaluate` 提交完整的 `EvaluationContext`，获得 `AuthorizationDecision`（`ALLOW` / `DENY` + 枚举 `denyReason` + `decisionId`）；同时 Role / RoleAssignment / Policy 聚合的变更通过 Outbox 可靠对外发布。

**Business Value**: Authorization BC 是 Core Domain。集中式 PDP 避免业务服务复制策略评估逻辑，统一收敛 Role / RoleAssignment / Policy，并通过 Outbox 保证决策与角色绑定变更的事件可靠传播。

**Functional Requirements**:
- 定义聚合（领域层）：
  - `Role(roleId, tenantId, roleName, description)`
  - `RoleAssignment(assignmentId, tenantId, subjectId, roleId, assignedAt, expiresAt, status)`
  - `Policy(policyId, tenantId, status, rules: List<PolicyRule>)`，约束"每个租户任意时刻只有一个生效 Policy"。
  - `PolicyRule(ruleId, permission, conditions, effect)`
- 枚举：`AssignmentStatus { ACTIVE, EXPIRED, REVOKED }`、`Decision { ALLOW, DENY }`、`DenyReason { NO_MATCHING_POLICY_RULE, SUBJECT_NOT_IN_TENANT, RESOURCE_NOT_IN_TENANT, POLICY_EVALUATION_ERROR }`。
- 实现 `Policy.evaluate(EvaluationContext)` 集中策略评估逻辑。
- 暴露 HTTP API：
  - `POST /api/v1/authorization/evaluate`
  - 请求体：`EvaluationContext { subject{userId, tenantId, platformRoles, customRoles}, resource{type, id, attributes}, action{permission}, environment }`
  - 响应：`AuthorizationDecision { decision, appliedPolicyRuleId, denyReason, decisionId, decidedBy }`
  - HTTP 语义：`200` for ALLOW/DENY、`400` for 非法请求（如 permission 非 `resource:action`）、`401` 服务间凭证缺失、`503` 内部错误或依赖不可用。
- 实现 Outbox Pattern：授权决策日志、角色绑定变更、策略变更事件必须与本地状态变更在同一个 PostgreSQL 事务中写入 outbox，再由发布器异步发送到事件总线。
- Schema-per-Tenant 数据库访问：必须在显式事务内使用 `SET LOCAL search_path = ...`，禁用 session-level `SET search_path`。
- 缓存（可选，但需在设计中体现）：RoleAssignment 热路径允许引入 Caffeine L1 + Redis L2 缓存；缓存失效必须与角色绑定变更事件对齐。
- Domain 层不感知 HTTP / JWT / Keycloak / 数据库 schema 切换 / Kafka 细节。

**Data Model & Structure**:
- 聚合、值对象、枚举如上。
- Outbox 表 schema：事件类型、tenantId、payload、status、createdAt。

**Technical Approach**:
- 单一 authorization-service 服务边界（不拆 Role / Assignment / Policy 微服务）。
- 显式 PEP / PDP 分离：PEP 在业务服务（由 Thin Client SDK 调用），PDP 在 authorization-service。
- 服务间身份鉴权（MVP 可用 Port + 测试替身，后续 mTLS 或等价机制）。

**Scope - INCLUDED**:
- 聚合、值对象、枚举定义。
- `POST /api/v1/authorization/evaluate` 实现与 HTTP 语义。
- Outbox Pattern 实现与与本地事务的耦合。
- Schema-per-Tenant 访问约束。
- 单元/集成测试：ALLOW、DENY（各类 denyReason）、跨租户拒绝、503 路径。

**Scope - EXCLUDED**:
- Thin Client SDK（独立任务）。
- Tenant Context Filter（独立任务）。
- OPA / Rego 引擎引入（明确排除）。
- 把 Role / Assignment / Policy 拆为独立服务（明确排除）。

**Success Criteria**:
- 跨租户访问被 `RESOURCE_NOT_IN_TENANT` / `SUBJECT_NOT_IN_TENANT` 正确拒绝。
- `NO_MATCHING_POLICY_RULE` 与 `POLICY_EVALUATION_ERROR` 在对应路径下被正确返回。
- 内部错误返回 `503`，符合 Fail-Closed 语义。
- Outbox 事件在本地事务回滚时不会被发布。
- DENY 是正常业务结果，不写为错误。

**Constraints & Considerations**:
- DENY 日志只记录 `decisionId`、枚举 `denyReason`，不写敏感资源属性全文。
- 自定义角色不进入 JWT，由 PDP 查询 RoleAssignment 解析。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | 可信 Tenant Context 传播链可在 Spring Boot 服务中安全使用 |

