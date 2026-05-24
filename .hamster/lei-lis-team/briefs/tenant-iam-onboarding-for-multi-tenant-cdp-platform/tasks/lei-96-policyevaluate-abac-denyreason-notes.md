---
id: "68846fb6-e88a-43ab-9e59-a072749b4968"
entity_type: "task"
entity_id: "c8ffac3e-efc9-423a-bdff-851ffa904d5f"
title: "实现 Policy.evaluate() ABAC 决策逻辑与 DenyReason 映射 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-96"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:40.118069+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在领域层实现 Policy 聚合的 ABAC 评估算法，集中产生 AuthorizationDecision，并完整覆盖 PRD 的 DenyReason 枚举。

## Implementation Approach

1. 在 domain 层定义 `EvaluationContext` 及其子结构（Subject、Resource、Action、Environment），全部为不可变值对象。
2. 在 `Policy` 聚合实现 `evaluate(EvaluationContext): AuthorizationDecision` 方法：

- Step 1：校验 subject.tenantId == context.tenantId，否则 DENY/SUBJECT_NOT_IN_TENANT。
- Step 2：校验 resource.tenantId == context.tenantId，否则 DENY/RESOURCE_NOT_IN_TENANT。
- Step 3：遍历 PolicyRule，匹配 permission 与 conditions；首条匹配且 effect=ALLOW 返回 ALLOW，effect=DENY 返回 DENY。
- Step 4：无匹配 → DENY/NO_MATCHING_POLICY_RULE。
- Step 5：包裹 try/catch，意外异常 → DENY/POLICY_EVALUATION_ERROR 并记录内部错误。

1. 构造 `AuthorizationDecision`：生成 decisionId（UUID）、appliedPolicyRuleId、decidedBy（如 `authorization-service@v1`）。
2. 编写单元测试矩阵覆盖每个分支。

## Acceptance Criteria

- 评估算法覆盖全部四种 DenyReason 与 ALLOW 路径
- DENY 不抛异常，POLICY_EVALUATION_ERROR 由内部捕获产生
- 决策携带 decisionId / decidedBy / appliedPolicyRuleId
- 单元测试覆盖所有分支与多规则冲突

## Technical Constraints

- 评估逻辑只能存在于 authorization-service 领域层（PDP 单一权威）
- 不引入 OPA/Rego；conditions 通过简单结构化匹配实现
- 评估方法对调用方为纯函数风格，不写入持久化或事件## Details

**Scope**: EvaluationContext 领域型定义、Policy.evaluate() 决策算法、租户归属校验、PolicyRule 匹配与条件评估、AuthorizationDecision 构造、DenyReason 枚举映射、决策逻辑单元测试

**Out of Scope**: HTTP 接口与序列化（独立子任务）、持久化与仓储（独立子任务）、Outbox 事件发布、缓存、Thin Client SDK（兄弟任务）、复杂策略引擎（如 OPA/Rego）

## Acceptance Criteria

- [ ] 在 domain 层存在 `EvaluationContext`，包含 subject(userId、tenantId、platformRoles、customRoles)、resource(type、id、attributes、tenantId)、action(permission)、environment 预留字段
- [ ] `Policy.evaluate(context)` 返回一个 `AuthorizationDecision`，ALLOW 与 DENY 均为正常返回值而不抛出异常
- [ ] subject.tenantId 不等于请求 tenantId 时，返回 DENY 且 denyReason = SUBJECT_NOT_IN_TENANT
- [ ] resource.tenantId 不等于请求 tenantId 时，返回 DENY 且 denyReason = RESOURCE_NOT_IN_TENANT
- [ ] 无任何 PolicyRule 匹配请求的 permission 与条件时，返回 DENY 且 denyReason = NO_MATCHING_POLICY_RULE
- [ ] 规则评估过程中发生未预期异常时，被捕获并转换为 DENY 且 denyReason = POLICY_EVALUATION_ERROR，不向上报错
- [ ] ALLOW 决策携带 appliedPolicyRuleId，所有决策携带唯一 decisionId 与 decidedBy
- [ ] 单元测试覆盖上述每一种决策路径，包含 ALLOW、四种 DENY 原因以及多规则冲突场景

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 7 |

