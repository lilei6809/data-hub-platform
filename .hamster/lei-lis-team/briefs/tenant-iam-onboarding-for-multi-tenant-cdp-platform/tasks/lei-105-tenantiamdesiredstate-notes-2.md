---
id: "99b2ede0-3f84-4f91-b119-192c9f457d61"
entity_type: "task"
entity_id: "fc2487f9-2438-4a70-8fb5-e41e8070aebe"
title: "组合 TenantIamDesiredState 聚合与工厂方法 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-105"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:12.403754+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

把基础值对象、枚举和扩展占位组合为 `TenantIamDesiredState` 聚合，并提供从 MVP 最小输入构造的工厂方法。

## 实施方式

1. 定义 `Tier`（枚举或值对象，与 PRD tier 概念一致），用于聚合中的 `tier` 字段。
2. 定义 `TenantIamDesiredState` 聚合，字段严格对齐 PRD Data Models：tenantId、tier、adminUser、identityMode、realmStrategy、identityProviders、policies。
3. 在构造点校验必填字段非空，并对集合字段做不可变包装。
4. 提供工厂方法 `fromMinimalRequest(tenantId, tier, adminEmail)`，将 MVP 最小输入构造为聚合，扩展字段使用默认值。
5. 编写聚合层单元测试：默认值、必填校验、不可变性、工厂方法路径。

## 验收标准

- 聚合字段完整对齐 PRD
- 工厂方法可从最小输入构造完整聚合并填充默认值
- 聚合不可变，集合字段对外为不可修改视图
- 必填字段缺失抛出领域异常
- 单元测试覆盖默认值、必填校验、不可变性、工厂路径

## 技术约束

- 领域层不引入 Spring/JPA/Jackson/Keycloak SDK
- 字段命名严格对齐 PRD Data Models

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: TenantIamDesiredState 聚合定义、Tier 表达、默认值与不变量、从 MVP 最小输入构造聚合的工厂方法、聚合层单元测试。

**Out of Scope**: TenantIamProvisioningState/TenantIamStatus（sibling）、KeycloakAdminPort（sibling）、Step Pipeline（sibling）、TenantIamProvisioningService（sibling）、事件契约（sibling）、持久化映射。

**Constraints**: 领域层纯洁：不引入 Spring、JPA、Jackson 注解或 Keycloak SDK, 聚合不可变；identityProviders 与 policies 集合必须对外返回不可修改视图, 字段命名必须严格与 PRD Data Models 中的 TenantIamDesiredState 一致

## Acceptance Criteria

- [ ] `TenantIamDesiredState` 聚合包含 PRD 指定的全部字段：tenantId、tier、adminUser、identityMode、realmStrategy、identityProviders、policies
- [ ] 提供工厂方法从 `(tenantId, tier, adminEmail)` 最小输入构造 Desired State，扩展字段使用默认值（identityMode=LOCAL_ONLY、realmStrategy=SHARED_REALM、identityProviders 与 policies 为空）
- [ ] 聚合不可变，identityProviders、policies 对外返回不可修改集合
- [ ] 构造时校验必填字段非空（tenantId、tier、adminUser 不允许 null），违反抛出领域异常
- [ ] 提供单元测试覆盖：默认值填充、必填校验、不可变性、工厂方法路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

