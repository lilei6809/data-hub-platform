---
id: "b5f6baee-8caf-438f-8863-e4cd4d03f3a7"
entity_type: "task"
entity_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
title: "Authorization Thin Client SDK 可让业务服务安全 Fail-Closed 调用 PDP - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-80"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:55:29.030298+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 业务服务开发者可以通过 Thin Client SDK 安全调用授权 PDP。

把 PEP 调用语义封装成统一 SDK：超时、熔断、Fail-Closed 一次写对，业务服务只关心"取资源 → 校验归属 → 调 evaluate"，避免每个服务自行实现并出现一致性偏差。

## Experience

业务服务开发者得到一个 `AuthorizationThinClient`。调用 `evaluate(EvaluationContext)` 拿到 `AuthorizationDecision`；PDP 不可用时 SDK 抛出 `AuthorizationUnavailableException`，框架统一映射为 HTTP `503`。配套示例展示标准 PEP 用法：`@PreAuthorize` 平台角色预筛 → 加载资源 → 校验 `resource.tenantId == currentTenantId` → 调用 PDP。

## Interaction

1. Controller 进入业务方法，先做 `@PreAuthorize` 平台角色预筛。
2. 加载资源，断言 `resource.tenantId == currentTenantId`。
3. 调用 `AuthorizationThinClient.evaluate(...)`。
4. ALLOW → 继续；DENY → 返回 403；不可用 → 抛出受控异常并被映射为 `503`。
5. 整个调用携带 `correlationId` 与 `tenantId`。## Details

**User Capability**: 业务服务开发者可以通过 `AuthorizationThinClient` 在 application service 中以 PEP 角色调用 authorization-service：传入 `EvaluationContext`，得到 `AuthorizationDecision`；当 authorization-service 不可用时，SDK 默认 Fail-Closed，业务服务统一对外返回 `503` 而不是放行。

**Business Value**: 把 PEP 调用语义、超时、熔断、Fail-Closed 行为封装在统一 SDK，避免每个业务服务自行实现并出现一致性偏差或 Fail-Open 漏洞。

**Functional Requirements**:
- 提供 `AuthorizationThinClient` SDK：
  - 方法：`evaluate(EvaluationContext) -> AuthorizationDecision`
  - 内置超时、重试（仅对幂等读，且严格上限）、熔断。
  - PDP 返回 `200 ALLOW/DENY` → 透传决策。
  - PDP 返回 `400` → 抛出客户端使用错误（业务 bug，需立即暴露）。
  - PDP 返回 `401` → 抛出服务间认证失败错误。
  - PDP 返回 `503` 或连接失败/超时/熔断打开 → Fail-Closed：抛出 `AuthorizationUnavailableException`，业务层捕获后必须向客户端返回 `503`。
- 提供 Spring 集成：
  - 在标准异常处理器中把 `AuthorizationUnavailableException` 映射为 HTTP `503`。
  - 在 controller / service 中提供示例 PEP 用法：先 `@PreAuthorize` 做平台角色预筛（不做数据库 IO），再加载资源、校验 `resource.tenantId == currentTenantId`，最后调用 `evaluate`。
- 严禁默认 Fail-Open；任何"降级放行"配置必须显式开启并伴随告警钩子（MVP 可不提供降级开关）。
- 必须传播 `correlationId` 与 `tenantId` 到 PDP 调用 Header。

**Data Model & Structure**:
- 复用 PDP 任务的 `EvaluationContext` 与 `AuthorizationDecision`。
- `AuthorizationUnavailableException` 受控异常。

**Technical Approach**:
- HTTP 客户端使用 Spring `RestClient` / `WebClient`，配合 Resilience4j（或等价）实现超时与熔断。
- 服务间身份：MVP 可使用 Port + 测试替身，预留 mTLS 接入点。

**Scope - INCLUDED**:
- Thin Client SDK 实现与配置。
- Spring 异常处理器集成。
- 标准 PEP 用法示例与文档。
- 单元/集成测试：ALLOW 透传、各类 DENY 透传、`400` / `401` / `503` 与超时/熔断的 Fail-Closed 行为。

**Scope - EXCLUDED**:
- authorization-service PDP 本身（"Authorization Service PDP"任务）。
- Tenant Context 传播链（"Tenant Context"任务）。
- 下游服务的具体 ABAC 业务策略（属于各下游服务实现）。

**Success Criteria**:
- 在 PDP 不可用场景下，业务服务返回 `503` 而不是放行。
- DENY 被视为正常业务结果，不被记录为系统错误。
- 调用始终携带 `correlationId` 与 `tenantId`。
- 熔断在持续故障下打开，并在 PDP 恢复后自动半开/关闭。

**Constraints & Considerations**:
- 严禁默认 Fail-Open。
- 严禁 SDK 内部缓存 DENY/ALLOW 决策（决策必须由 PDP 实时给出，缓存策略由 PDP 侧定义）。
- 日志只记录 `decisionId`、`tenantId`、`correlationId`，不记录资源属性全文。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Authorization Service PDP 契约与聚合可承载 ABAC 决策, 可信 Tenant Context 传播链可在 Spring Boot 服务中安全使用 |

