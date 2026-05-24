---
id: "0109ab61-400c-4226-bdd0-0af87b057f93"
entity_type: "task"
entity_id: "aaab41fe-c579-4a49-9a6e-4d2bd90e05bd"
title: "暴露 PDP HTTP 契约 POST /api/v1/authorization/evaluate - Notes"
status: "todo"
priority: "high"
display_id: "LEI-110"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:20.064852+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 authorization-service 对外 PDP HTTP 评估接口，严格遵守 PRD 的请求/响应 schema 与 HTTP 语义。

## Implementation Approach

1. 定义 REST 控制器 `AuthorizationEvaluationController`，路由 `POST /api/v1/authorization/evaluate`。
2. 定义请求 DTO `EvaluateRequest`（subject/resource/action/environment）与响应 DTO `EvaluateResponse`（decision/decisionId/appliedPolicyRuleId/denyReason/decidedBy），用 Bean Validation 注解强校验 permission 格式与必填字段。
3. 在应用服务 `EvaluateAuthorizationUseCase` 中：根据 tenantId 加载 Policy 聚合 → 转换 DTO 为 `EvaluationContext` → 调用 `Policy.evaluate(context)` → 将结果映射回响应 DTO。
4. 全局异常处理：

- 校验失败 → 400（不含堆栈）。
- 凭证缺失/无效 → 401。
- 仓储/依赖异常或未捕获异常 → 503。

1. 编写契约层集成测试覆盖 200(ALLOW/DENY)、400、401、503 路径。

## Acceptance Criteria

- 接口 schema 与 HTTP 状态码完全符合 PRD
- DENY 与 ALLOW 都返回 200
- 400/401/503 语义正确，503 让 PEP 可 Fail-Closed
- 控制器不含决策逻辑
- 集成测试覆盖五种路径

## Technical Constraints

- 仅暴露评估接口，不引入 Role/Policy 管理 API（不在本任务范围）
- 不实现服务间 mTLS 细节，凭证校验通过现有安全过滤器或占位拦截器表达即可## Details

**Scope**: HTTP 路由、请求与响应 DTO、与领域 EvaluationContext / AuthorizationDecision 的转换、请求体校验与 400/401/503 错误映射、应用服务编排（装载 Policy 调用 evaluate）、契约层集成测试

**Out of Scope**: Policy.evaluate 决策逻辑（独立子任务）、仓储与持久化实现、Outbox 事件发布、Thin Client SDK 实现（兄弟任务 c6b147e7）、Tenant Context Filter（兄弟任务 343cab7a）、服务间 mTLS 实现细节

## Acceptance Criteria

- [ ] `POST /api/v1/authorization/evaluate` 端点可接受符合 PRD 的 EvaluationContext JSON，返回 AuthorizationDecision JSON（decision、decisionId、appliedPolicyRuleId、denyReason、decidedBy）
- [ ] ALLOW 与 DENY 结果都以 HTTP 200 返回，响应体中 denyReason 仅在 DENY 结果中出现
- [ ] 请求体校验失败（如 permission 不匹配 `resource:action`、必填字段缺失）返回 400，错误体不泄露内部堅栈
- [ ] 服务间身份凭证缺失或无效时返回 401
- [ ] 依赖组件（仓储）抛出异常或应用层发生未预期异常时返回 503，供调用方 Fail-Closed
- [ ] Controller/Handler 不包含任何决策判断逻辑，仅调用应用服务 + Policy.evaluate
- [ ] 契约层集成测试（如 MockMvc 或等价）覆盖 200(ALLOW)、200(DENY)、400、401、503 五种路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

