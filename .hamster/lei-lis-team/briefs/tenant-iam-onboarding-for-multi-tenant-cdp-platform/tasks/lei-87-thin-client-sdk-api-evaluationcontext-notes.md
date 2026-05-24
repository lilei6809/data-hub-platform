---
id: "9f2f230a-feff-40b8-9e50-eed3e5b3e08c"
entity_type: "task"
entity_id: "dd1c5cfd-fc05-4283-b9e5-1f2b743e64ec"
title: "定义 Thin Client SDK 公共 API 与 EvaluationContext 请求/响应模型 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-87"
parent_task_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:04.988144+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为业务服务定义 Authorization Thin Client SDK 的公共调用接口（AuthorizationClient.evaluate）以及 EvaluationContext / AuthorizationDecision 数据模型。

## Implementation Approach

1. 定义 AuthorizationClient 接口，方法签名为 `AuthorizationDecision evaluate(EvaluationContext ctx)`。
2. 建模 EvaluationContext：Subject(userId, tenantId, platformRoles, customRoles)、Resource(type, id, attributes)、Action(permission)、Environment（预留 Map 结构）。
3. 建模 AuthorizationDecision：decision、appliedPolicyRuleId、denyReason、decisionId、decidedBy。
4. 定义枚举 Decision（ALLOW/DENY）与 DenyReason（NO_MATCHING_POLICY_RULE、SUBJECT_NOT_IN_TENANT、RESOURCE_NOT_IN_TENANT、POLICY_EVALUATION_ERROR）。
5. 在 Permission 值对象构造时校验 `resource:action` 格式，非法时抛出 IllegalArgumentException。
6. 提供 EvaluationContext.Builder，让业务服务从已加载资源对象构造请求。
7. 编写模型层单元测试：序列化字段命名、枚举映射、Permission 格式校验。

## Acceptance Criteria

- AuthorizationClient 接口公开 evaluate(EvaluationContext) 方法
- EvaluationContext 与 PRD 契约字段一致
- AuthorizationDecision 与 PRD 契约字段一致
- Permission 在构造时强制 `resource:action` 校验
- 提供 Builder 帮助构造 EvaluationContext
- 单元测试覆盖序列化、枚举与校验

## Technical Constraints

- 模型必须与 sibling task（Authorization Service PDP 契约）的服务端模型字段名一一对应
- 客户端模型不得依赖 Spring、HTTP 库或 Keycloak SDK
- 枚举值必须与 PRD 中 DenyReason 列表完全一致，不允许扩展自由文本

## Code Patterns to Follow

- 使用值对象 + Builder 表达请求结构
- 客户端模型放在 SDK 模块内，与服务端模型物理隔离## Details

**Scope**: SDK 公共接口 AuthorizationClient、EvaluationContext 与 AuthorizationDecision 数据模型、相关枚举（Decision、DenyReason）、Permission 校验、模型层单元测试

**Out of Scope**: HTTP 客户端实现、超时与熔断、Spring Security 集成、Tenant Context 读取（由 343cab7a 处理）、authorization-service 服务端契约与聚合（由 a83402a7 处理）

## Acceptance Criteria

- [ ] AuthorizationClient 接口公开 evaluate(EvaluationContext) 方法，返回 AuthorizationDecision
- [ ] EvaluationContext 包含 Subject、Resource、Action、Environment 四个部分，且字段命名与 PRD 契约一致
- [ ] AuthorizationDecision 包含 decision、appliedPolicyRuleId、denyReason、decisionId、decidedBy 字段，并使用枚举表达 Decision 与 DenyReason
- [ ] Permission 字段在构造时校验 resource:action 格式，非法值在调用前抛出客户端校验异常
- [ ] 提供一个 Builder/帮助方法可从已加载的资源对象构造 EvaluationContext
- [ ] 单元测试覆盖模型序列化、枚举映射与 Permission 格式校验

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

