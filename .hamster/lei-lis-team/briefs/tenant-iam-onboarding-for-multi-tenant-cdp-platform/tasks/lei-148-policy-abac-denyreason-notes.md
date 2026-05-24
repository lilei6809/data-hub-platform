---
id: "df0e9ae1-8aa7-4471-99ad-e40c4d35ef95"
entity_type: "task"
entity_id: "70d9d5cc-d970-41d9-a391-e0c7c0a98ed3"
title: "实现 Policy ABAC 评估算法与 DenyReason 决策语义 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-148"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:38.149808+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 Authorization BC 的 ABAC 评估核心：`Policy.evaluate(EvaluationContext)` 与配套的 EvaluationContext 模型、DenyReason 映射、跨租户资源归属校验。

## 实现方式

1. 在 domain 层定义 `EvaluationContext`、`SubjectContext`、`ResourceContext`、`ActionContext`、`EnvironmentContext` 值对象，字段对齐 PRD API。
2. 在 `Policy` 聚合上实现 `evaluate(EvaluationContext): AuthorizationDecision`：

- 第一步：subject.tenantId 与 resource.tenantId、policy.tenantId 三者比对，缺失/不一致返回 SUBJECT_NOT_IN_TENANT 或 RESOURCE_NOT_IN_TENANT。
- 第二步：遍历 PolicyRule，按 permission 匹配，再评估 conditions。
- 第三步：第一条匹配且 effect=ALLOW 的 rule 产出 ALLOW；若仅 DENY rule 匹配或无匹配，产出 DENY+NO_MATCHING_POLICY_RULE。
- 异常路径：Condition 评估抛错统一 catch，返回 DENY+POLICY_EVALUATION_ERROR。

1. 定义最小可用的 Condition 评估器（例如基于属性等值 / in 集合），抽象为 `ConditionEvaluator` 接口以便未来扩展，但 MVP 只交付 In-Process 默认实现。
2. AuthorizationDecision.decisionId 由领域服务生成 UUID。
3. 编写完整的 ALLOW、四种 DenyReason、空 Policy、多 Rule 优先级等单元测试。

## 验收标准

- EvaluationContext 字段与 PRD 一致
- Policy.evaluate 输出符合 AuthorizationDecision 契约
- 跨租户校验显式发生并映射 DenyReason
- 异常路径 Fail-Closed 到 POLICY_EVALUATION_ERROR
- 单元测试覆盖全部分支

## 技术约束

- 评估逻辑零基础设施依赖
- DENY 通过返回值表达，禁止抛异常
- 异常路径必须 Fail-Closed
- decisionId 全局唯一

## 范围

**包含**：EvaluationContext 模型、Policy.evaluate 算法、ConditionEvaluator 默认实现、单元测试。

**不包含**：REST 接口（独立子任务）、Repository 加载 Policy/RoleAssignment（独立子任务）、Outbox 事件（独立子任务）、L1/L2 缓存（独立子任务）、Thin Client SDK（兄弟任务）、JWT 解析（兄弟任务）。## Details

**Scope**: EvaluationContext 领域类型、Policy.evaluate 算法、PolicyRule 匹配与 condition 评估、DenyReason 映射、资源跳租户校验、AuthorizationDecision 生成、评估逻辑单元测试。

**Out of Scope**: REST 接口、Repository 加载 Policy/RoleAssignment、Outbox 事件、缓存策略、Thin Client SDK、JWT 解析。

**Constraints**: 评估逻辑不涉及 HTTP、JPA、Kafka、JWT, DENY 是正常返回值，不以异常表达拒绝, Condition 评估失败或遇到未知错误时必须返回 DENY+POLICY_EVALUATION_ERROR，不能 Fail-Open, decisionId 必须为 UUID 或全局唯一标识符以供后续审计

## Acceptance Criteria

- [ ] EvaluationContext 领域类型定义完整：subject(userId/tenantId/platformRoles/customRoles)、resource(type/id/attributes)、action(permission)、environment，与 PRD API 字段一致
- [ ] Policy.evaluate(context) 返回 AuthorizationDecision，包含 decisionId、decision、appliedPolicyRuleId(ALLOW 时)、denyReason(DENY 时)、decidedBy
- [ ] subject.tenantId != resource.tenantId 时返回 DENY + SUBJECT_NOT_IN_TENANT 或 RESOURCE_NOT_IN_TENANT，该分支有专门测试覆盖
- [ ] 所有 PolicyRule 都不匹配当前 permission/conditions 时返回 DENY + NO_MATCHING_POLICY_RULE
- [ ] Condition 评估中抛出异常或遇到未能解析的表达式时返回 DENY + POLICY_EVALUATION_ERROR，不能出现 Fail-Open
- [ ] 单元测试覆盖：ALLOW、4 种 DenyReason 分枝、多条 PolicyRule 优先级、空 Policy 场景

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 7 |

