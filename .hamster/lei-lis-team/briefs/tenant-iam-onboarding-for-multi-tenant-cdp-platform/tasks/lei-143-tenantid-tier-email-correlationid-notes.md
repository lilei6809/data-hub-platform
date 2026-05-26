---
id: "dfb7362a-4e8e-45a5-87f4-2843d3c4e31f"
entity_type: "task"
entity_id: "97613845-dfbc-47ff-91b9-890ff8c7976f"
title: "建立基础标识与原语值对象（TenantId / Tier / Email / CorrelationId）及领域校验异常 - Notes"
status: "done"
priority: "medium"
display_id: "LEI-143"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:11.4769+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 IAL 领域模型的基础原语值对象 TenantId / Tier / Email / CorrelationId 与统一领域校验异常。

## Implementation Approach

1. 在 IAL 领域模块下定义值对象包，逐个实现四个值对象（推荐 Java record 或不可变 final class）。
2. 定义统一领域校验异常类型，承载 typeName 与原因，用作所有值对象构造期失败的语义出口。
3. 在工厂/构造器中执行校验：TenantId 非空且 slug 格式约束；Tier 非空白且最大长度；Email 含 @ 与长度限制；CorrelationId 非空白。
4. Email 提供 masked() 安全表示并让 toString 调用 masked()，避免日志泄漏。
5. CorrelationId 提供 of(String) 与 newCorrelationId()（基于 UUID.randomUUID()）。
6. 编写单元测试覆盖合法构造、非法输入、equals/hashCode、Email 遮蔽与 CorrelationId 工厂。

## Acceptance Criteria

- TenantId、Tier、Email、CorrelationId 均为不可变类型并实现 equals/hashCode
- TenantId 校验非空、非空白与最大长度；Tier 接受非空字符串并预留未来受限集合扩展
- Email 构造期做基本格式校验并提供 masked() 表示，toString 不泄漏原始邮箱
- CorrelationId 提供 of(String) 与 newCorrelationId() 两类工厂
- 定义统一领域校验异常，所有值对象非法输入抛出该异常并携带 typeName
- 单元测试覆盖合法构造、非法输入、equals/hashCode 与日志安全表示
- 不引入 Spring/Port/Repository/Jackson/Kafka 等基础设施依赖

## Technical Constraints

- 不允许将敏感字段（邮箱原文、密码、secret）写入 toString
- 不允许依赖任何基础设施层类型
- 校验异常必须为领域层定义，不能复用 Spring 或 Jakarta 验证异常

## Code Patterns to Follow

- 值对象构造期完成所有不变量校验
- 不可变 + equals/hashCode + 安全 toString
- 工厂方法 of(...) 表达校验意图## Details

**Scope**: TenantId、Tier、Email、CorrelationId 四个原语值对象，统一领域校验异常类型，及其单元测试

**Out of Scope**: AdminUser/TemporaryCredentialPolicy（独立子任务）、IdentityMode/RealmStrategy 枚举（独立子任务）、TenantIamDesiredState 聚合（独立子任务）、TenantIamProvisioningState（独立子任务）、SubjectId/RoleName/Permission 等授权 BC 值对象（属 Authorization 父任务）

**Implementation**: 1. 在 IAL 领域模块下创建值对象包，逐个实现 TenantId / Tier / Email / CorrelationId（推荐使用 Java record 或 final class）。
2. 定义领域校验异常（如 DomainValidationException），包含 typeName 与 message 字段，用于聚合所有值对象构造失败。
3. 在每个值对象的工厂/构造器中执行校验，非法输入抛出该异常。
4. 为 Email 提供 masked() 方法（如 j***@example.com），并让 toString 调用 masked()。
5. 为 CorrelationId 提供 of(String) 校验工厂与 newCorrelationId() 生成工厂。
6. 编写 JUnit 单元测试覆盖合法构造、非法输入、equals/hashCode、Email 遮蔽行为与 CorrelationId 工厂行为。

## Acceptance Criteria

- [ ] TenantId、Tier、Email、CorrelationId 均为不可变类型并实现 equals/hashCode
- [ ] TenantId 校验非空、非空白与最大长度；Tier 接受非空字符串并预留未来受限集合扩展
- [ ] Email 构造期做基本格式校验（含 @、长度限制）并提供 masked() 安全字符串表示，toString 不泄漏原始邮箱
- [ ] CorrelationId 提供 of(String) 与 newCorrelationId()（基于 UUID.randomUUID()）两类工厂
- [ ] 定义统一的领域校验异常类型，所有值对象非法输入抛出该异常并携带 typeName/原因
- [ ] 单元测试覆盖每个值对象的合法构造、非法输入、equals/hashCode 与日志安全表示
- [ ] 不引入对 Spring、Port、Repository、Jackson、Kafka 等基础设施的依赖

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |
