---
id: "a99aa7dd-0080-42ae-86d3-b3c6c507839e"
entity_type: "task"
entity_id: "2f73c5b4-7935-47e2-9381-6eb21af2e682"
title: "建立 TenantAwareAuthentication 与 TenantContextHolder 基础模型 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-83"
parent_task_id: "343cab7a-8d53-4549-8dc5-4f4f15361496"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:00.818594+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 Tenant Context 的核心承载类型 TenantAwareAuthentication 与 TenantContextHolder，作为后续 Filter、Executor 与 PEP 调用的统一上下文 API。

## 实施步骤

1. 定义 `TenantContext` 不可变值对象，字段包括 `tenantId`、`userId`、`platformRoles`（不可变 Set）。构造时校验 tenantId/userId 非空。
2. 实现 `TenantAwareAuthentication`：实现 Spring Security `Authentication`，将平台角色映射为 `SimpleGrantedAuthority("ROLE_" + role)`；`isAuthenticated()` 默认 true 并禁止反向设置；`getPrincipal()` 返回 userId，`getDetails()` 返回 TenantContext。
3. 实现 `TenantContextHolder`：基于 `ThreadLocal<TenantContext>`，提供 `set/get/clear/require` 静态方法；`require()` 在缺失时抛出 `MissingTenantContextException`。
4. 编写单元测试覆盖构造校验、authority 映射、跨线程隔离、clear 行为。

## 验收标准

- TenantAwareAuthentication 实现 Authentication 接口并正确映射平台角色为 GrantedAuthority
- TenantContextHolder 提供 set/get/clear/require 并强制清理语义
- TenantContext 字段不可变且构造校验非空
- 单元测试覆盖跨线程隔离与 clear 行为
- 不引用 HttpServletRequest / Filter 类型

## 技术约束

- 基于 Spring Security Core，不依赖 Servlet API
- ThreadLocal 必须配合显式 clear 使用，避免泄漏
- 平台角色仅承载 JWT `realm_access.roles`，不包含租户自定义角色

## 范围

- 包含：TenantContext 值对象、TenantAwareAuthentication、TenantContextHolder 的实现与单元测试
- 不包含：Header 解析与 Filter 实现（由后续子任务负责）、异步 Executor 的传播逻辑、MDC 日志集成## Details

**Scope**: TenantContext 值对象、TenantAwareAuthentication、TenantContextHolder 三个核心类型的实现及对应单元测试

**Out of Scope**: HTTP Header 解析、Filter 注册、异步 Executor、MDC 日志注入；这些由其他子任务负责

**Constraints**: 仅依赖 Spring Security Core，不依赖 Servlet/WebFlux, ThreadLocal 必须配合显式 clear 使用, 平台角色仅承载 JWT realm_access.roles，不包含租户自定义角色

## Acceptance Criteria

- [ ] TenantAwareAuthentication 实现 Spring Security Authentication 接口，承载 tenantId、userId 和平台角色集合（来自 X-Platform-Roles），并将平台角色映射为 GrantedAuthority（如 ROLE_TENANT_ADMIN）
- [ ] TenantContextHolder 暴露 set(context)/get()/clear() API，底层基于 ThreadLocal；提供 require() 方法在缺失时抛出明确异常，避免静默返回 null 导致跨租户污染
- [ ] 提供 TenantContext 值对象（tenantId、userId、platformRoles），所有字段不可变；构造时校验 tenantId/userId 非空，平台角色集合不可变
- [ ] 单元测试覆盖：构造校验、authority 映射、ThreadLocal 在不同线程间隔离、clear 后 get 返回空
- [ ] TenantAwareAuthentication 与 TenantContextHolder 不引用 HttpServletRequest 或 Filter 类型，仅依赖 Spring Security Core

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

