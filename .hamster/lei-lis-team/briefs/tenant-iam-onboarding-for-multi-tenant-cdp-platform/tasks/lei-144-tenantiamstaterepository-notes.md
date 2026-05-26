---
id: "d82ebb03-0ec8-418f-a9c8-1d67d51d36bb"
entity_type: "task"
entity_id: "9a8b50af-f643-4474-a3b6-9ab20287e9d5"
title: "定义 TenantIamStateRepository 端口与内存实现 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-144"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:18.786316+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 最新任务调整

`LEI-144` 是当前 Application Service 前置任务，优先级高于重复任务 `LEI-94`。

本任务应作为 `TenantIamProvisioningService` 的本地状态持久化边界来实现，第一版只需要 Port + In-Memory Adapter：

- Port 放在 application core 的 outbound port 包中，不依赖 Spring Data、JDBC、JPA 或 PostgreSQL。
- In-Memory 实现仅用于 MVP、本地验证和后续 Application Service 验收，不代表生产持久化方案。
- `findOrInitialize(tenantId, correlationId)` 应该创建 `TenantIamProvisioningState.init(...)` 的初始状态，后续调用返回同一 tenant 的已有状态。
- 不在本任务中处理事务、Schema-per-Tenant、Outbox 或真实数据库映射。

## 概述

定义 `TenantIamStateRepository` 端口与内存实现，为状态机提供持久化边界。

## 实施方式

1. 在应用核心声明 `TenantIamStateRepository` 接口：`Optional<TenantIamProvisioningState> findByTenantId(TenantId)`、`void save(TenantIamProvisioningState)`、`TenantIamProvisioningState findOrInitialize(TenantId, CorrelationId)`。
2. 提供 `InMemoryTenantIamStateRepository` 实现，使用 `ConcurrentHashMap<TenantId, TenantIamProvisioningState>` 存储，保证并发安全。
3. `findOrInitialize` 在缺失时构造 `PENDING_IAM` 初始状态并保存。
4. 编写单元测试验证：空查询、初始化、保存覆盖、并发更新不丢失。

## 验收标准

- 接口与内存实现满足上述方法签名。
- 内存实现线程安全。
- 单元测试覆盖初始化、覆盖、并发场景。

## 技术约束

- 接口属于应用核心，不能引入 Spring Data、JPA、PostgreSQL 概念。
- 内存实现仅用于 MVP；命名上明确为 In-Memory，避免被误用于生产。

## 范围边界

- ✅ 端口接口、内存实现、相关单元测试。
- ❌ 真实数据库实现、事务、Schema 切换、Outbox。## Details

**Scope**: TenantIamStateRepository 接口定义、内存实现、针对内存实现的并发/幂等性单元测试。

**Out of Scope**: JPA/PostgreSQL 实现、Schema-per-Tenant、Outbox、事务管理、Service 编排、实体本身的定义（独立子任务）。

## Acceptance Criteria

- [ ] `TenantIamStateRepository` 接口提供 findByTenantId(tenantId)、save(state)、findOrInitialize(tenantId, correlationId) 三个方法
- [ ] findOrInitialize 在首次调用时创建状态为 PENDING_IAM 的记录，后续调用返回已有记录
- [ ] 内存实现在多线程并发 save 时不丢失更新（使用 ConcurrentHashMap 或等价机制）
- [ ] 重复 save 同一 tenantId 会覆盖之前的状态记录，体现幂等语义
- [ ] 单元测试覆盖：首次查询返回空、findOrInitialize 初始化、save+findByTenantId 循环、并发 save 不丢失
- [ ] 接口定义不依赖任何 Spring/JPA 符号，仅依赖领域类型

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
