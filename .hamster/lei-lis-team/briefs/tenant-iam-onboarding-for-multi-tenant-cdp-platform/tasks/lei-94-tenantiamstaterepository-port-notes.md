---
id: "52b7e372-a7e3-4e1a-822c-f5a833e64db6"
entity_type: "task"
entity_id: "2c8ae65c-5831-420b-86e8-ec075c94b8a7"
title: "定义 TenantIamStateRepository Port 并提供内存实现 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-94"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:35.173511+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 `TenantIamStateRepository` Port 并提供 MVP 阶段的线程安全内存实现，让应用服务与状态持久化解耦。

## 实现思路

1. 在应用/领域核心层定义 `TenantIamStateRepository` 接口，至少包含：

- `Optional<TenantIamProvisioningState> findByTenantId(TenantId tenantId)`
- `void save(TenantIamProvisioningState state)`

1. 在基础设施层提供 `InMemoryTenantIamStateRepository`，使用 `ConcurrentHashMap<TenantId, TenantIamProvisioningState>` 存储。
2. 保存时整体替换聚合快照，避免暴露内部可变状态。
3. 编写单元测试覆盖空查询、保存后读取、覆盖更新、并发读写。
4. 接口与实现都不引入 JPA、Spring Data、数据库连接等基础设施依赖，保持 MVP 简洁。

## 验收标准

- 接口位于核心层且无持久化技术依赖
- 内存实现使用并发安全数据结构
- 支持空查询、保存、覆盖更新语义
- 单元测试覆盖核心读写与并发场景
- 可直接装配进本地运行与测试上下文

## 技术约束

- Port 接口必须可被真实 PostgreSQL 适配器替换（后续阶段）
- 不引入 Spring Data JPA 或 Hibernate
- 不在本子任务实现 Schema-per-Tenant 事务语义

## 相关技能

- Hamster Blueprint## Details

**Scope**: TenantIamStateRepository 接口定义、线程安全的内存实现、对应单元测试

**Out of Scope**: 状态转换规则（State 聚合子任务）、应用服务编排（Service 子任务）、真实 PostgreSQL/JPA 适配器（超出 MVP）、Schema-per-Tenant 事务处理

## Acceptance Criteria

- [ ] TenantIamStateRepository 接口位于应用/领域核心层，不依赖任何持久化技术
- [ ] 接口提供按 tenantId 加载状态与保存状态的方法，加载不存在时返回 Optional.empty 或等价语义
- [ ] 内存实现使用 ConcurrentHashMap 或等价结构保证并发安全
- [ ] 单元测试覆盖：首次查询为空、保存后可读、重复保存同一 tenantId 会覆盖、并发读写不抛异常
- [ ] 内存实现可被测试与本地运行环境直接装配

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

