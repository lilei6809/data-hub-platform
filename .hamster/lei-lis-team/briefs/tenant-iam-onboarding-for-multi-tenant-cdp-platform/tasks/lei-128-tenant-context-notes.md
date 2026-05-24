---
id: "0f54371e-caeb-4c16-b098-b5f1a984f36d"
entity_type: "task"
entity_id: "582115d8-3092-4420-8013-2057390c6051"
title: "端到端验证 Tenant Context 传播链的安全与隔离行为 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-128"
parent_task_id: "343cab7a-8d53-4549-8dc5-4f4f15361496"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:39.490894+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

通过端到端集成测试与开发者指引固化 Tenant Context 传播链的安全与隔离语义，覆盖同步、异步、伪造 Header 与上下文清理场景。

## 实施步骤

1. 搭建 `@SpringBootTest` + MockMvc 测试切片，装配 TenantContextFilter、SecurityFilterChain、TenantAwareExecutor 与一个测试用 Controller。
2. 同步场景：发起合法 Header 请求，Controller 内部调用 `TenantContextHolder.require()` 与 `SecurityContextHolder.getContext().getAuthentication()`，断言 tenantId/userId/角色一致；并验证 @PreAuthorize 行为。
3. 异步场景：Controller 触发 @Async 方法，把执行线程的 TenantContext/SecurityContext 写入 Future 后断言。
4. 安全场景：构造同时含合法 Envoy Header 与额外客户端伪造 Header（模拟 Envoy 已剥除时只保留合法值）的两类请求，验证上下文严格基于注入 Header；全部缺失返回 401。
5. 隔离场景：依次以两个租户身份请求，断言两次请求结束后 Holder 与 MDC 均为空。
6. 编写 `docs/tenant-context.md`（或 README 章节）阐述使用约定。

## 验收标准

- 同步、异步、安全、隔离四类集成测试全部通过
- @PreAuthorize 基于平台角色生效
- 开发者指引覆盖三条核心约定
- 测试不依赖真实 Envoy/Keycloak

## 技术约束

- 使用 Spring Boot 测试切片
- 异步路径走 TenantAwareExecutor
- 不引入新业务逻辑

## 范围

- 包含：端到端集成测试套件、开发者使用指引文档
- 不包含：新功能实现、Thin Client SDK 调用、PDP 集成、Envoy 配置测试## Details

**Scope**: 端到端场景测试（同步、异步、伪造 Header、上下文清理）、开发者使用指引文档

**Out of Scope**: 新处理逻辑、Thin Client SDK 集成、authorization-service 调用、Envoy 层配置验证

**Constraints**: 使用 @SpringBootTest + MockMvc 或 WebMvcTest 验证 Filter 装配, 异步场景使用 ThreadPoolTaskExecutor + TenantAwareExecutor, 测试不依赖真实 Envoy/Keycloak

## Acceptance Criteria

- [ ] 集成测试覆盖同步路径：Controller 能通过 TenantContextHolder.require() 获得正确上下文，且 @PreAuthorize 能根据平台角色允许/拒绝
- [ ] 集成测试覆盖异步路径：@Async 方法在执行线程中能读到与调用方一致的 TenantContext 与 SecurityContext
- [ ] 安全场景测试：客户端同时传入伪造 X-Tenant-ID 与合法 Envoy Header 的场景下（模拟 Envoy 已剥除，仅合法 Header 达达下游），上下文与合法 Header 一致；Header 全部缺失场景返回 401
- [ ] 上下文隔离测试：连续两个不同租户请求后 TenantContextHolder 与 SecurityContextHolder 为空，线程池复用场景下 MDC 也被清理
- [ ] 提供一份简短开发者指引（README 或 docs），说明：如何读取当前租户、为什么不能信任客户端 Header、异步任务必须走 TenantAwareExecutor

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 4 |

