---
id: "d1850faa-55cd-49d0-a8d8-adf71af197e7"
entity_type: "task"
entity_id: "9e705258-115a-4196-ad07-3839e9416e78"
title: "建模 Authorization BC 核心聚合 Role / RoleAssignment / Policy - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-84"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:01.330839+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在 authorization-service 领域层建立 Role / RoleAssignment / Policy 聚合及配套值对象、枚举，作为 PDP 评估与 Outbox 事件的领域基线。

## Implementation Approach

1. 在 authorization-service 的 domain 模块下新建 `role`、`roleassignment`、`policy` 三个聚合包，每个聚合包含聚合根、相关实体与领域服务接口（如需）。
2. 实现值对象 `TenantId`、`SubjectId`、`RoleName`、`Permission`，在构造函数中强制校验格式（`RoleName` 大写下划线、`Permission` 匹配 `^[a-z]+:[a-z]+$`）。
3. 定义枚举 `AssignmentStatus`、`Decision`、`DenyReason`，并在聚合中引用。
4. 在 `Policy` 聚合中显式建模租户级唯一生效 Policy 的状态机（如 DRAFT / ACTIVE / SUPERSEDED），通过工厂方法或聚合方法保证不变量。
5. 定义 `PolicyRule` 实体（ruleId、permission、conditions、effect），暂以结构化数据承载 conditions，不引入策略引擎。
6. 定义 `AuthorizationDecision` 值对象（decisionId、decision、denyReason、appliedPolicyRuleId、decidedBy）。
7. 为值对象、枚举与聚合不变量编写单元测试。

## Acceptance Criteria

- Role、RoleAssignment、Policy 三个聚合根存在且领域纯净
- 值对象自校验生效，非法格式构造抛出领域异常
- 枚举与 PRD 定义一致
- Policy 唯一生效不变量通过状态/工厂方法表达
- 单元测试覆盖合法与非法构造

## Technical Constraints

- Domain 层不得依赖 Spring、JPA、HTTP、Kafka、Keycloak
- 聚合边界遵守 PRD：三者收敛在单一服务，不拆分微服务
- 一个租户任意时刻仅一个生效 Policy## Details

**Scope**: Authorization BC 内 Role / RoleAssignment / Policy 聚合、PolicyRule 实体、AuthorizationDecision 值对象、相关值对象与枚举的定义及其不变量单测

**Out of Scope**: ABAC 评估逻辑（见独立子任务）、HTTP 评估接口、仓储与持久化实现、Outbox 事件发布、Thin Client SDK（兄弟任务 c6b147e7）、Tenant Context 传播（兄弟任务 343cab7a）

## Acceptance Criteria

- [ ] Role、RoleAssignment、Policy 三个聚合根存在于 authorization-service 领域层，且每个聚合根明确暴露不变量与业务方法，不依赖 Spring、JPA、HTTP 或 Keycloak 类型
- [ ] Role 包含 roleId、tenantId、roleName、description；RoleAssignment 包含 assignmentId、tenantId、subjectId、roleId、assignedAt、expiresAt、status；Policy 包含 policyId、tenantId、status、rules 与 PolicyRule(ruleId、permission、conditions、effect)
- [ ] 值对象 TenantId、SubjectId、RoleName、Permission 实现自校验：RoleName 仅允许大写字母与下划线，Permission 必须匹配 `resource:action` 格式，非法值在构造时抛出领域异常
- [ ] 枚举 AssignmentStatus(ACTIVE/EXPIRED/REVOKED)、Decision(ALLOW/DENY)、DenyReason(NO_MATCHING_POLICY_RULE/SUBJECT_NOT_IN_TENANT/RESOURCE_NOT_IN_TENANT/POLICY_EVALUATION_ERROR) 定义完成并被聚合引用
- [ ] Policy 聚合显式建模 `一个租户任意时刻只有一个生效 Policy` 的不变量（如通过 status 字段和工厂/状态迁移方法保证）
- [ ] 针对值对象与聚合不变量的单元测试覆盖合法与非法构造场景

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

