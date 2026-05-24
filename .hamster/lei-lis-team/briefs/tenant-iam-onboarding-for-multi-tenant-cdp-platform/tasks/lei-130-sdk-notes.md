---
id: "e403da65-6118-4f91-81da-71c6ebe0918c"
entity_type: "task"
entity_id: "dccd8aee-8694-45dd-97ca-b54b0c542987"
title: "编写 SDK 端到端验证测试场景 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-130"
parent_task_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:57.72891+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

建立 Thin Client SDK 端到端验证测试集，覆盖 ALLOW、DENY、Fail-Closed (503/超时)、熔断器 OPEN 与完整 PEP 调用链。

## Implementation Approach

1. 使用 MockWebServer / WireMock 作为 PDP 替身，覆盖以下场景：

- 200 + ALLOW 决策
- 200 + DENY 决策（每种 denyReason 至少一次）
- 503 错误
- 读取超时
- 连接失败

1. 构造 sample controller + service 模块，演示：

- `@PreAuthorize("hasAuthority('DATA_ENGINEER')")` 角色预筛
- Repository 加载资源
- `authorizationEnforcer.check(resource, Permission.of("datasource:read"))`

1. 编写 ControllerAdvice 示例，把 AccessDeniedException → 403，AuthorizationUnavailableException → 503。
2. 使用 Spring MockMvc 编写集成测试覆盖所有场景。
3. 测试熔断打开后多次调用仍 503，半开后恢复正常 200。

## Acceptance Criteria

- ALLOW 场景业务返回 200
- DENY 场景业务返回 403 且 denyReason/decisionId 可见
- 503/超时业务返回 503（Fail-Closed）
- 熔断 OPEN 期间持续 503
- Sample controller 展示推荐 PEP 用法
- ControllerAdvice 把 SDK 异常映射为正确 HTTP 状态码

## Technical Constraints

- 测试不依赖真实 PDP 服务
- 不测试 Tenant Context 传播（由 sibling 任务覆盖）
- ControllerAdvice 仅作为示例，不强制业务服务采用## Details

**Scope**: ALLOW/DENY/503/超时/熔断 OPEN 场景的 SDK 集成测试、示例业务 controller + ControllerAdvice 映射 503 的示例与测试、PDP 替身（MockWebServer/WireMock）配置

**Out of Scope**: 真实 PDP 服务端实现（sibling a83402a7）、Tenant Context 传播测试（sibling 343cab7a）、单元测试（已在前续子任务中覆盖）、性能/负载测试

## Acceptance Criteria

- [ ] 集成测试覆盖 ALLOW 决策下业务响应 200 的路径
- [ ] 集成测试覆盖 DENY 决策下响应 403，并验证 denyReason 与 decisionId 在响应/日志中可见
- [ ] 集成测试覆盖 PDP 503 与超时场景业务响应 503，证明 Fail-Closed 生效
- [ ] 集成测试验证熔断器在达到阈值后 OPEN，后续调用仍返回 503 不放行
- [ ] 提供一个 sample controller + service，展示 @PreAuthorize 角色预筛与 AuthorizationEnforcer.check 组合的推荐用法
- [ ] 提供 ControllerAdvice 示例将 AuthorizationUnavailableException 映射为 HTTP 503

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 5 |

