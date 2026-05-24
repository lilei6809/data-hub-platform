---
id: "ce415ac7-58e1-49ba-b38f-75fb8109f3ad"
entity_type: "task"
entity_id: "0987ae93-a673-4b94-8d0f-0cd13cc41fb5"
title: "实现 TenantContextFilter 消费 Envoy 注入 Header 并构建 SecurityContext - Notes"
status: "todo"
priority: "high"
display_id: "LEI-97"
parent_task_id: "343cab7a-8d53-4549-8dc5-4f4f15361496"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:40.282148+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 TenantContextFilter，将 Envoy 注入的可信 Header 翻译为 SecurityContext 与 TenantContextHolder，并在请求结束时强制清理。

## 实施步骤

1. 实现 `TenantContextFilter extends OncePerRequestFilter`：在 `doFilterInternal` 中读取 `X-Tenant-ID`、`X-User-ID`、`X-Platform-Roles`。
2. 校验 tenantId/userId 非空；缺失返回 401 并写入精简错误响应；platformRoles 格式非法返回 400。
3. 构造 `TenantContext` 与 `TenantAwareAuthentication`，写入 `SecurityContextHolder` 与 `TenantContextHolder`。
4. 在 `try { chain.doFilter(...) } finally { clear }` 模式下保证上下文必清理。
5. 通过 `SecurityFilterChain` Bean 将 Filter 注册到 `UsernamePasswordAuthenticationFilter` 之前。
6. 文档化 Header 名称常量（建议放入 TenantContextHeaders 类）以便后续测试与异步传播复用。

## 验收标准

- Filter 解析三类 Header 并正确构造 TenantAwareAuthentication
- 缺失/非法 Header 时拦截请求（401/400），不进入业务
- 请求结束后两类 Holder 均被清理，连续请求不串租户
- @PreAuthorize 角色检查基于 X-Platform-Roles 正常工作
- MockMvc 测试覆盖各类正反场景

## 技术约束

- 仅信任 Envoy 注入 Header，不读取 Authorization
- 必须在 finally 中清理 ThreadLocal
- 注册位置位于 UsernamePasswordAuthenticationFilter 之前
- Header 名称可配置

## 范围

- 包含：Filter 实现、Security 配置注册、Header 解析与校验、清理逻辑、MockMvc 测试
- 不包含：JWT 验签、异步 Executor、MDC 日志、ABAC 调用## Details

**Scope**: TenantContextFilter 实现、Spring Security 配置注册、Header 解析与校验、请求结束后上下文清理

**Out of Scope**: JWT 验签（Envoy 负责）、异步传播、MDC 日志注入、ABAC 调用、Reactive WebFlux 栈

**Constraints**: 仅信任指定名称的 Envoy 注入 Header，不读取 Authorization/JWT, 必须在 finally 中 clear TenantContextHolder 和 SecurityContextHolder, Filter 注册在 Spring Security 过滤器链中，位于 UsernamePasswordAuthenticationFilter 之前, Header 名称可配置（默认匹配 PRD）以便本地开发覆盖

## Acceptance Criteria

- [ ] Filter 读取 X-Tenant-ID、X-User-ID、X-Platform-Roles（逗号分隔）并构造 TenantAwareAuthentication，同时写入 SecurityContextHolder 和 TenantContextHolder
- [ ] 当 X-Tenant-ID 或 X-User-ID 缺失/为空时，返回 401 且不调用后续 Filter；当 X-Platform-Roles 格式非法时返回 400
- [ ] 请求结束后（无论成功或异常）TenantContextHolder 与 SecurityContextHolder 被清理，可通过连续两个请求验证不会串租户
- [ ] Filter 在 Spring Security 配置中被注册在 UsernamePasswordAuthenticationFilter 之前，使 @PreAuthorize("hasRole('TENANT_ADMIN')") 能基于平台角色正常生效
- [ ] MockMvc 集成测试覆盖：合法 Header、缺失 Header、重复 Header、多角色逗号分隔、请求后上下文被清理

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

