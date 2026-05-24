---
id: "6a7efd4d-cffb-4720-87aa-54194af8008b"
entity_type: "task"
entity_id: "a252b447-43b4-4217-abd8-b6329f49af27"
title: "实现 RoleAssignmentRepository 与 PolicyRepository 持久化边界 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-120"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:56.362578+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为 Role / RoleAssignment / Policy 聚合提供以聚合为单位的持久化边界，并以 Schema-per-Tenant 的安全方式实现 PostgreSQL 适配器。

## Implementation Approach

1. 在 domain 层定义仓储接口：

- `RoleRepository`：按 ID/按 tenantId 列表查询、保存。
- `RoleAssignmentRepository`：按 ID、按 (tenantId, subjectId) 查找活跃绑定，保存与更新状态。
- `PolicyRepository`：按 tenantId 查找唯一生效 Policy，保存（支持版本/状态切换）。

1. 在 infrastructure 层实现 JDBC/JPA 适配器，所有 public 方法在事务模板中先执行 `SET LOCAL search_path = :tenantSchema, public`，再执行 SQL。
2. 提供事务边界辅助（如 `TenantSchemaTransactionTemplate`），统一约束 `SET LOCAL` 用法。
3. 编写 Testcontainers 集成测试：创建两个租户 schema，验证写入互不可见、`findActiveByTenant`、`findActiveBySubject` 返回符合预期。

## Acceptance Criteria

- 仓储接口领域纯净
- 持久化只通过 `SET LOCAL search_path` 切换 schema
- 关键查询语义正确（唯一生效 Policy、活跃 RoleAssignment）
- 事务原子保存为 Outbox 共事务写入打基础
- Testcontainers 集成测试验证多租户隔离

## Technical Constraints

- 禁止 session-level `SET search_path`
- 仓储不返回 ORM/JPA Entity 给领域层
- 不实现缓存层## Details

**Scope**: Role / RoleAssignment / Policy 仓储接口定义、PostgreSQL 适配器实现、`SET LOCAL search_path` 事务内切换、基本查询 (按 tenantId 取生效 Policy、按 tenantId+subjectId 取活跃 RoleAssignment、按 ID 读取与保存)、仓储集成测试

**Out of Scope**: Outbox 发布机制（独立子任务）、Caffeine/Redis 缓存、PDP HTTP 接口、Policy.evaluate 逻辑、跨租户 schema 创建/迁移脚本、Thin Client SDK

## Acceptance Criteria

- [ ] 领域层定义 `RoleRepository`、`RoleAssignmentRepository`、`PolicyRepository` 接口，只暴露聚合类型与值对象，不泄露 JPA/JDBC 类型
- [ ] Infrastructure 层提供基于 PostgreSQL 的仓储实现，所有查询/写入均发生在显式事务中，事务开始时执行 `SET LOCAL search_path = <tenant-schema>, public`，禁用 session-level `SET search_path`
- [ ] `PolicyRepository.findActiveByTenant(tenantId)` 返回该租户唯一生效 Policy，不存在时返回空
- [ ] `RoleAssignmentRepository.findActiveBySubject(tenantId, subjectId)` 仅返回 status=ACTIVE 且未过期的绑定
- [ ] 保存 RoleAssignment / Policy 时使用事务保证原子性，为后续 Outbox 同事务写入预留入口
- [ ] 针对仓储提供集成测试（Testcontainers 或等价），验证多租户 schema 隔离与基本 CRUD 路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

