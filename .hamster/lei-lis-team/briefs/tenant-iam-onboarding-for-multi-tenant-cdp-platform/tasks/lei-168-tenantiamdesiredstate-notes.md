---
id: "2c4ef71f-95ac-41f1-9133-379d5961d6ea"
entity_type: "task"
entity_id: "b4e55420-f875-4541-9f54-6d0ce56bc1e7"
title: "实现 TenantIamDesiredState 聚合与最小输入工厂 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-168"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:02.633601+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 Tenant IAM Onboarding 核心聚合 TenantIamDesiredState 与最小输入工厂，作为 Step Pipeline 编排的稳定输入契约。

## Implementation Approach

1. 定义 TenantIamDesiredState（record 或 final class），字段含 tenantId、tier、adminUser、identityMode、realmStrategy、identityProviders、policies。
2. 主构造器完成不变量校验与 List.copyOf 防御性拷贝。
3. 提供 ofMinimalInput(TenantId, Tier, Email) 静态工厂，使用 LOCAL_ONLY、SHARED_REALM 与空列表为默认。
4. 访问器返回不可变视图；toString 不暴露敏感字段。
5. 严禁引入基础设施依赖。
6. 编写单元测试覆盖最小输入、完整构造、必填校验、默认值、防御性拷贝与 equals/hashCode。

## Acceptance Criteria

- 聚合不可变，包含 7 个字段
- ofMinimalInput 工厂使用安全默认值
- 必填字段构造期校验非空，违反抛出领域校验异常
- identityProviders 与 policies 通过 List.copyOf 不可变
- toString 不输出原始邮箱与凭证
- 不引入基础设施依赖
- 单元测试覆盖核心行为

## Technical Constraints

- 必须严格不可变（含集合字段）
- 不允许在聚合内执行 IO / 调用 Port / 记录日志
- 主流程仅处理 LOCAL_ONLY + SHARED_REALM，扩展字段仅作为占位## Details

**Scope**: TenantIamDesiredState 聚合本身、完整构造函数、最小输入工厂方法、不变量校验、不可变集合处理

**Out of Scope**: TenantId/Tier/Email/AdminUser/IdentityMode/RealmStrategy/IdentityProviderConfig/PolicyConfig 本身的定义（独立子任务）、Step Pipeline 与 Provisioning Service 编排（兄弟任务）、事件契约（兄弟任务）、本地状态记录与状态机（同父任务下另一个子任务）

**Implementation**: 1. 定义 TenantIamDesiredState（推荐 Java record 或 final class），字段：tenantId、tier、adminUser、identityMode、realmStrategy、identityProviders、policies。
2. 在主构造器中完成全部不变量校验：必填字段非空、列表非空（可空集合代替 null）、并通过 List.copyOf 保证防御性拷贝与不可变。
3. 暴露 ofMinimalInput(TenantId tenantId, Tier tier, Email adminEmail) 静态工厂：构造默认 AdminUser、设置 IdentityMode=LOCAL_ONLY、RealmStrategy=SHARED_REALM、空 identityProviders 与 policies。
4. 提供受控访问器返回不可变集合视图。
5. toString 不输出原始 email / 凭证字段（依赖 AdminUser 的安全 toString）。
6. 不引入 Spring、Port、Repository、Jackson、Kafka 依赖。
7. 编写单元测试覆盖：最小输入工厂构造、完整构造、必填字段缺失抛异常、扩展字段默认值、列表防御性拷贝、equals/hashCode。

## Acceptance Criteria

- [ ] TenantIamDesiredState 为不可变聚合，包含 tenantId、tier、adminUser、identityMode、realmStrategy、identityProviders、policies 七个字段
- [ ] ofMinimalInput(tenantId, tier, adminEmail) 工厂使用 LOCAL_ONLY、SHARED_REALM 与空扩展列表为默认值
- [ ] 所有必填字段构造期校验非空，违反抛出领域校验异常
- [ ] identityProviders 与 policies 通过防御性拷贝（如 List.copyOf）保证不可变
- [ ] toString 不输出原始邮箱与凭证字段
- [ ] 不依赖 Spring、Keycloak SDK、Port、Repository、Jackson、Kafka
- [ ] 单元测试覆盖最小输入工厂、完整构造、必填校验、默认值、防御性拷贝与 equals/hashCode

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

