---
id: "7fb7e61a-ed12-4956-a05e-21242010d0c4"
entity_type: "task"
entity_id: "b6a4bab3-3103-4f1c-8733-d0f7acfb506d"
title: "建立 Authorization BC 聚合与值对象领域模型 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-140"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:57.612743+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 Authorization BC 的聚合（Role、RoleAssignment、Policy、PolicyRule）、值对象与枚举，作为 ABAC 决策与 Outbox 事件的领域基础。

## 实现方式

1. 在 authorization-service 的 domain 模块下建立 `authorization` 包，分离 `model`、`vo`、`enums` 子包。
2. 实现值对象：`TenantId`、`SubjectId`、`RoleId`、`RoleName`、`Permission`、`PolicyId`、`AssignmentId`、`DecisionId`，统一在构造时做格式校验并保持 final/immutable。
3. 实现聚合根：

- `Role`：roleId、tenantId、realmRoleName、description，工厂方法保证 tenantId+realmRoleName 唯一语义。
- `RoleAssignment`：assignmentId、tenantId、subjectId、roleId、assignedAt、expiresAt、status，提供 `expire()`、`revoke()`、`isActiveAt(Instant)` 行为。
- `Policy`：policyId、tenantId、status、rules(List<PolicyRule>)，提供 `activate()`、`supersedeBy(Policy)`，保证同租户同时只有一个 ACTIVE。
- `PolicyRule`：ruleId、permission、conditions、effect。

1. 实现结果对象 `AuthorizationDecision`：decisionId、decision、denyReason、appliedPolicyRuleId、decidedBy（不可变），并提供 `allow(...)`、`deny(DenyReason, ...)` 静态工厂。
2. 定义枚举：`AssignmentStatus`、`Decision`、`DenyReason`。
3. 编写聚合不变量与值对象校验的单元测试。

## 验收标准

- Role、RoleAssignment、Policy、PolicyRule、AuthorizationDecision 在领域层以聚合或值对象形式存在，字段与 PRD Data Models 完全一致
- TenantId、SubjectId、RoleName、Permission 作为值对象封装格式校验
- AssignmentStatus、Decision、DenyReason 枚举完整定义
- Policy 同租户同时刻仅一个 ACTIVE 的不变量被强制
- RoleAssignment 通过聚合行为切换 status
- 领域类型零基础设施依赖

## 技术约束

- Domain 层禁止引用 HTTP、JWT、JPA、Kafka、Keycloak SDK 类型
- 值对象与聚合标识符必须 immutable
- DenyReason 必须使用枚举，禁止自由文本
- Permission 格式严格遵守 `resource:action`

## 范围

**包含**：Authorization BC 的聚合、值对象、枚举、聚合不变量与单元测试。

**不包含**：Policy 评估算法实现（独立子任务）、Repository/Port 接口（独立子任务）、REST 评估 API（独立子任务）、Outbox 事件发布（独立子任务）、Thin Client SDK（由兄弟任务承担）、TenantContext 传播（由兄弟任务承担）。## Details

**Scope**: Authorization BC 的 Role/RoleAssignment/Policy/PolicyRule/AuthorizationDecision 聚合与值对象、相关枚举、聚合不变量、聚合行为方法及其单元测试。

**Out of Scope**: Policy 评估算法、Repository Port、REST 评估 API、Outbox 发布、Thin Client SDK、TenantContext 传播。

**Constraints**: Domain 层不引入 HTTP/JWT/JPA/Kafka/Keycloak SDK 依赖, 值对象与聚合标识符必须 immutable, DenyReason、Decision、AssignmentStatus 必须为枚举, Permission 必须满足 resource:action 格式校验

## Acceptance Criteria

- [ ] Role、RoleAssignment、Policy、PolicyRule、AuthorizationDecision 在领域层以聚合或值对象形式存在，字段与 PRD Data Models 完全一致
- [ ] TenantId、SubjectId、RoleName、Permission 作为值对象封装格式校验：RoleName 仅允许大写字母与下划线、Permission 必须符合 resource:action 格式，违规构造抛出领域异常
- [ ] AssignmentStatus(ACTIVE/EXPIRED/REVOKED)、Decision(ALLOW/DENY)、DenyReason(NO_MATCHING_POLICY_RULE/SUBJECT_NOT_IN_TENANT/RESOURCE_NOT_IN_TENANT/POLICY_EVALUATION_ERROR) 枚举完整定义
- [ ] Policy 聚合不变量：同一 tenantId 同一时刻只有一个 status=ACTIVE 的 Policy，违反时通过领域校验拒绝
- [ ] RoleAssignment 在 expiresAt 到期或被 revoke 时通过聚合行为切换 status，不允许外部直接 set 字段
- [ ] 所有领域类型不引用 HTTP、JWT、JPA 注解、Kafka 或 Keycloak SDK 类型，单元测试可在纯 JVM 下运行

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

