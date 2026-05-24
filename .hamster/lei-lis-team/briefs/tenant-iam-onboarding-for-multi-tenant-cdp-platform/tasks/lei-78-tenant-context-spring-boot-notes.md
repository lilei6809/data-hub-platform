---
id: "13f2b44a-e78f-4829-947d-0754e569031a"
entity_type: "task"
entity_id: "343cab7a-8d53-4549-8dc5-4f4f15361496"
title: "可信 Tenant Context 传播链可在 Spring Boot 服务中安全使用 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-78"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:54:31.534908+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 业务服务开发者可以在 Spring Boot 应用中安全使用 Tenant Context。

把"Envoy 注入 Header → 应用内可信上下文"这条信任传播链做成基础设施。业务代码不解析 JWT、不信任客户端直传 Header，租户隔离在框架层强制落地。

## Experience

业务服务开发者得到 `TenantContextFilter`、`TenantAwareAuthentication`、`TenantContextHolder` 和 `TenantAwareExecutor`。任何 controller / service / 异步任务都能稳定拿到 `tenantId`、`userId` 与平台角色；客户端伪造的身份 Header 会被显式忽略；异步任务也能正确继承上下文。

## Interaction

1. 请求到达，`TenantContextFilter` 读取 Envoy 注入的可信 Header。
2. 构建 `TenantAwareAuthentication` 写入 SecurityContext 与 ContextHolder。
3. 业务代码通过 `TenantContextHolder.get()` 获取上下文。
4. 异步任务通过 `TenantAwareExecutor` 自动捕获并恢复上下文。
5. 请求结束 `finally` 清理 ThreadLocal，避免串租户。## Details

**User Capability**: 业务服务开发者可以在任意 controller / service / 异步任务中通过 `TenantContextHolder` 与 `TenantAwareAuthentication` 获取 `tenantId`、`userId` 与平台角色，且不需要自己解析 JWT；客户端伪造身份 Header 不会被信任。

**Business Value**: 多租户隔离的"信任边界"必须在应用框架层落地：JWT 在 Envoy 验证、Header 由 Envoy 注入、应用只消费可信 Header。把 Tenant Context 做成统一基础设施，避免每个业务服务自行解析、易出 IDOR 漏洞。

**Functional Requirements**:
- 实现 `TenantContextFilter`（Servlet Filter / WebFilter）：
  - 读取 `X-Tenant-ID`、`X-User-ID`、`X-Platform-Roles`（逗号分隔）。
  - 显式忽略并拒绝任何客户端直传的相关 Header（即使存在也不允许覆盖 Envoy 注入的值；进入应用前应被 Envoy 剥除，应用层做防御性校验）。
  - 校验三个 Header 同时存在且非空；缺失视为未认证请求。
  - 构建 `TenantAwareAuthentication` 写入 `SecurityContextHolder`。
  - 同时写入 `TenantContextHolder`（基于 `ThreadLocal`），必须在 `finally` 中清理。
- 实现 `TenantAwareAuthentication`：承载 `tenantId`、`userId` 与平台角色。
- 实现 `TenantContextHolder`：提供 `get/set/clear` 接口，禁止泄漏。
- 实现 `TenantAwareExecutor`：包装 `ExecutorService`，在任务提交时捕获当前 `TenantContextHolder` 与 `SecurityContextHolder`，在任务执行时恢复并在 `finally` 中清理；支持虚拟线程场景。
- 提供示例 controller / 集成测试，验证：
  - 缺失 Header → 401 / 403（按团队约定，明确选一）。
  - 客户端伪造 Header → 不被信任。
  - 异步任务通过 `TenantAwareExecutor` 正确传播上下文。
  - Filter 抛异常路径下 ThreadLocal 仍被清理。

**Data Model & Structure**:
- `TenantAwareAuthentication` 实现 Spring `Authentication`。
- `TenantContext` 值对象：`tenantId`、`userId`、`platformRoles`。

**Technical Approach**:
- 基于 Spring Security 过滤器链与 `SecurityContextHolderStrategy`。
- 虚拟线程：使用 `ScopedValue` 或显式捕获/恢复策略。
- 严格遵守"应用不解析 JWT"约束。

**Scope - INCLUDED**:
- Filter、Authentication、ContextHolder、Executor。
- 单元/集成测试覆盖正常、缺失、伪造、异步、异常清理路径。

**Scope - EXCLUDED**:
- Envoy Gateway 自身配置改造（PRD 明确排除）。
- ABAC 决策（"Authorization Service PDP" 任务）。
- Thin Client SDK（独立任务）。

**Success Criteria**:
- 没有任何业务代码出现 JWT 解析。
- 客户端直传 `X-Tenant-ID` 无法影响应用内 `TenantContextHolder`。
- 异步任务在 `TenantAwareExecutor` 下能稳定获取上下文，任务结束后上下文被清理。
- 通过 `finally` 清理后，连续复用线程不会出现租户上下文泄漏。

**Constraints & Considerations**:
- 严禁在日志中打印完整角色列表或 Header 原文；只记录 `tenantId`、`userId`、`platformRoles` 计数。
- Filter 必须放在 Spring Security 过滤器链早期，确保后续授权拦截可见上下文。

## Context

| Field | Value |
|-------|-------|
| dependencyRationale | Tenant IAM Provisioning Service 与本地状态机可端到端编排 onboarding |

