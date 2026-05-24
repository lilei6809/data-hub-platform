---
id: "b8883a9f-4ab6-4940-b00a-07e44676be75"
entity_type: "task"
entity_id: "6aa56d02-2b89-44aa-a2fe-4d9d7bda4078"
title: "交付 authorization-service `/evaluate` REST 契约与 HTTP 语义 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-169"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:09.096924+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 authorization-service 的 PDP 入口 `POST /api/v1/authorization/evaluate`，定义请求/响应契约并落地 200/400/401/503 HTTP 语义。

## 实现方式

1. 在 web 层创建 `AuthorizationEvaluationController`，路径 `POST /api/v1/authorization/evaluate`。
2. 定义 DTO：

- `EvaluationContextRequest`：subject(userId, tenantId, platformRoles, customRoles)、resource(type, id, attributes)、action(permission)、environment。
- `AuthorizationDecisionResponse`：decision、appliedPolicyRuleId、denyReason、decisionId、decidedBy。

1. 请求校验：必填字段校验；`permission` 通过正则校验 `^[a-z0-9_-]+:[a-z0-9_-]+$`；非法时返回 400。
2. 控制器流程：DTO → Domain EvaluationContext → `PolicyRepository.findActivePolicy(tenantId)` →

- 无 ACTIVE Policy：返回 DENY + NO_MATCHING_POLICY_RULE（200）。
- 有 Policy：调用 `policy.evaluate(ctx)`，返回结果。

1. HTTP 语义映射：

- ALLOW/DENY → 200。
- 请求体非法 → 400（统一 `@ControllerAdvice`）。
- 服务间凭证无效 → 401（占位 SecurityFilter，可以基于配置开关）。
- Repository/未预期异常 → 503，由 `@ControllerAdvice` 兜底，确保调用方 Fail-Closed。

1. 响应中的 denyReason 仅允许枚举字符串；不得序列化 resource.attributes 原文进入响应或错误体。
2. 编写 Spring MVC 集成测试覆盖所有状态码与 4 种 DenyReason。

## 验收标准

- 请求/响应契约与 PRD 完全一致
- ALLOW/DENY 均为 200，400/401/503 按规则触发
- Controller 仅做映射，评估委托给 Policy.evaluate
- 集成测试覆盖全部状态码与 DenyReason
- 接口文档记录语义

## 技术约束

- DENY 不得返回非 200
- 503 必须用于让调用方 Fail-Closed
- denyReason 仅枚举
- 错误响应中不含敏感属性原文

## 范围

**包含**：Controller、DTO、校验、HTTP 语义、ControllerAdvice、集成测试、接口文档。

**不包含**：Policy 评估算法（独立子任务）、Repository 实现（独立子任务）、Thin Client SDK 客户端（兄弟任务）、mTLS / 服务身份机制实施、Outbox 发布（独立子任务）、TenantContextFilter（兄弟任务）。## Details

**Scope**: POST /api/v1/authorization/evaluate Controller、请求/响应 DTO、DTO↔Domain 映射、请求校验、HTTP 状态码语义、控制器集成测试。

**Out of Scope**: Policy.evaluate 算法（已独立子任务）、Repository 实现、Thin Client SDK 客户端（兄弟任务）、mTLS/服务身份机制、Outbox 发布、TenantContextFilter。

**Constraints**: ALLOW 与 DENY 都返回 200，不能用 4xx 表达 DENY, Permission 不符合 resource:action 格式时返回 400 而非 200 DENY, PDP 内部异常或依赖不可用必须返回 503，以使调用方 Fail-Closed, denyReason 仅可为枚举字符串，response 中不出现任何敏感资源属性原文

## Acceptance Criteria

- [ ] POST /api/v1/authorization/evaluate 接受包含 subject(userId/tenantId/platformRoles/customRoles)、resource(type/id/attributes)、action(permission)、environment 的请求体，响应体包含 decision、appliedPolicyRuleId、denyReason、decisionId、decidedBy
- [ ] ALLOW 和 DENY 都返回 HTTP 200；permission 不符合 resource:action 格式等请求体非法场景返回 400
- [ ] 服务间身份凭证缺失/无效返回 401；PDP 内部异常或依赖不可用（例如 Repository 抛错）返回 503
- [ ] Controller 层只负责 DTO↔Domain 映射与 HTTP 语义，评估逻辑委托给 Policy.evaluate
- [ ] 集成测试覆盖 ALLOW、四种 DenyReason、400、401、3 个 HTTP 5xx 等价映射为 503 的路径
- [ ] OpenAPI/接口文档（代码内注解或生成文档）记录 4 个状态码的语义与 Fail-Closed 要求

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

