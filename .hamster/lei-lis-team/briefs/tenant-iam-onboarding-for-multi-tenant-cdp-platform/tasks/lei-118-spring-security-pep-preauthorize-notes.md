---
id: "8dec87f0-3e9b-425c-9c7b-1be745db8beb"
entity_type: "task"
entity_id: "73cc8c1b-a8d7-4abb-803f-611c14f517b5"
title: "提供 Spring Security PEP 集成与 @PreAuthorize 角色预筛模式 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-118"
parent_task_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:52.96997+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

提供 Thin Client SDK 的 Spring Security 集成层：AuthorizationEnforcer PEP 辅助类、Spring Boot AutoConfiguration 与 @PreAuthorize 使用规范。

## Implementation Approach

1. 创建 AuthorizationEnforcer：接收资源对象与 Permission，自动从 SecurityContext / TenantContextHolder 抽取 Subject，构造 EvaluationContext，调用 AuthorizationClient。
2. ALLOW 返回，DENY 抛出 Spring 的 AccessDeniedException（携带 decisionId 与 denyReason）。
3. AuthorizationUnavailableException 不被包裹，直接透传给上层。
4. 编写 AuthorizationClientAutoConfiguration：在 spring.factories / AutoConfiguration imports 中注册，按条件装配 HttpAuthorizationClient + CircuitBreaker + AuthorizationEnforcer，并暴露 ConfigurationProperties。
5. 提供 @PreAuthorize 示例：`@PreAuthorize("hasAnyAuthority('DATA_ENGINEER','TENANT_ADMIN')")` 仅基于平台角色做粗筛，并在文档中强调禁止在 @PreAuthorize 中调用 Repository。
6. 编写 MockMvc 集成测试：粗筛通过 → 加载资源 → AuthorizationEnforcer.check → ALLOW/DENY 两条路径，以及 PDP 不可用透传 503。

## Acceptance Criteria

- AuthorizationEnforcer 提供 check API，DENY 时抛 AccessDeniedException
- AutoConfiguration 自动装配 SDK 组件
- Subject 自动从 TenantAwareAuthentication 抽取
- 提供 @PreAuthorize 使用示例与说明
- 503 透传给上层
- MockMvc 集成测试覆盖完整 PEP 调用链

## Technical Constraints

- 不重复实现 TenantContextFilter / TenantAwareAuthentication（依赖 sibling 343cab7a）
- AutoConfiguration 必须可被业务服务通过单一 starter 依赖启用
- DENY 异常携带 decisionId + denyReason 以便上层日志## Details

**Scope**: AuthorizationEnforcer PEP 辅助类、Spring Boot AutoConfiguration、@PreAuthorize 使用模式与示例、从 TenantAwareAuthentication 抽取 Subject 构造 EvaluationContext、DENY 映射为 AccessDeniedException

**Out of Scope**: TenantContextFilter、TenantContextHolder、TenantAwareAuthentication 本身的实现（由 343cab7a 提供）、Envoy 配置、业务服务内部的资源归属校验代码、服务端 PDP 实现

## Acceptance Criteria

- [ ] 提供 AuthorizationEnforcer.check(Resource, Permission) 一行调用，ALLOW 返回成功，DENY 抛 AccessDeniedException
- [ ] Spring Boot AutoConfiguration 可自动装配 AuthorizationClient 与 AuthorizationEnforcer，可通过配置关闭
- [ ] Subject 字段（userId、tenantId、platformRoles）从 TenantAwareAuthentication / TenantContextHolder 自动提取
- [ ] 提供 @PreAuthorize 角色预筛使用示例与文档，明确说明不允许在控制器层触发数据库 IO
- [ ] AuthorizationUnavailableException 透传给上层，不被转换为 AccessDeniedException
- [ ] 提供使用 Spring MockMvc 的集成测试展示完整 PEP 调用路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

