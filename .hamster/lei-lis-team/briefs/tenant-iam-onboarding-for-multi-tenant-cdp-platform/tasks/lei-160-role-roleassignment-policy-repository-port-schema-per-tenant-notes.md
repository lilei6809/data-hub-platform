---
id: "25d49155-f17c-4a4a-8e1a-daf159280cb5"
entity_type: "task"
entity_id: "e939f274-4a96-44a2-a30c-4dc0664baf4a"
title: "定义 Role/RoleAssignment/Policy Repository Port 与 Schema-per-Tenant 边界 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-160"
parent_task_id: "a83402a7-20f6-49dd-9cc5-344f4ed31ef5"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:20.992319+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 Authorization BC 的 Role/RoleAssignment/Policy Repository Port、Schema-per-Tenant 访问规范，并提供内存测试替身。

## 实现方式

1. 在 application/domain 层创建 `RoleRepository`、`RoleAssignmentRepository`、`PolicyRepository` 接口，使用聚合根与值对象作为参数与返回值。
2. 关键方法：

- `RoleRepository`：`findById(RoleId)`、`findByTenantAndName(TenantId, RoleName)`、`save(Role)`。
- `RoleAssignmentRepository`：`findActiveBySubject(TenantId, SubjectId)`、`findById(AssignmentId)`、`save(RoleAssignment)`。
- `PolicyRepository`：`findActivePolicy(TenantId)`、`save(Policy)`。

1. 在每个接口的 Javadoc 中写明 Schema-per-Tenant 约束：实现方必须在显式事务内执行 `SET LOCAL search_path = tenant_<id>`，禁止 session-level `SET search_path`，并禁止跨 tenant 查询。
2. 实现 `InMemoryRoleRepository`、`InMemoryRoleAssignmentRepository`、`InMemoryPolicyRepository`，按 `(tenantId -> Map<Id, Aggregate>)` 隔离存储。
3. 编写测试：跨租户隔离、Active Policy 唯一性、Assignment 状态过滤。

## 验收标准

- 三个 Repository Port 定义齐全且使用领域类型
- TenantId 显式参与查询签名，避免跨租户读取
- 文档化 SET LOCAL search_path 约束
- 提供内存实现并通过隔离测试

## 技术约束

- 接口零基础设施依赖
- 不暴露 JPA Entity 或 SQL 类型
- 文档化 schema-per-tenant 规范

## 范围

**包含**：Repository Port 定义、内存测试替身、访问约束文档。

**不包含**：真实 JPA/JDBC 适配器、缓存层（Caffeine/Redis）、Outbox 实现、REST 接口、Policy 评估算法。## Details

**Scope**: RoleRepository、RoleAssignmentRepository、PolicyRepository 接口定义；Schema-per-Tenant 访问约束文档化；内存/Fake 实现作为测试替身。

**Out of Scope**: 真实 JPA/JDBC 适配器、数据库 schema DDL、Caffeine/Redis 缓存、Outbox 实现、REST 接口、Policy 评估算法。

**Constraints**: Repository 接口使用领域类型，不暴露 JPA Entity 或 ResultSet, 所有查询必须以 TenantId 为必填参数或从事务上下文隐式传递，防止跳租户读取, 领域代码中文档明确写明 Schema-per-Tenant 使用 SET LOCAL search_path 的约定，以供后续 JPA 适配器遵循, 内存实现需按 tenantId 隔离存储，以验证抽象不泄露跳租户读取可能

## Acceptance Criteria

- [ ] RoleRepository 提供 findById/findByTenantAndName/save 等最小必要方法，均以领域类型为入/出参
- [ ] RoleAssignmentRepository 提供 findActiveBySubject(TenantId, SubjectId)、findByAssignmentId、save，返回的 Assignment 只含当前 tenant 数据
- [ ] PolicyRepository 提供 findActivePolicy(TenantId)、save(Policy)，保证同租户同时只能查到一个 ACTIVE Policy
- [ ] 访问约束文档（Javadoc 或 module README）明确说明：Schema-per-Tenant 下所有查询必须在显式事务内 SET LOCAL search_path，禁用 session-level SET
- [ ] 提供内存 InMemoryRoleRepository、InMemoryRoleAssignmentRepository、InMemoryPolicyRepository，可供单元与集成测试使用
- [ ] 测试验证：从 tenant A 写入的 Role/Assignment/Policy 不会被 tenant B 的查询返回

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

