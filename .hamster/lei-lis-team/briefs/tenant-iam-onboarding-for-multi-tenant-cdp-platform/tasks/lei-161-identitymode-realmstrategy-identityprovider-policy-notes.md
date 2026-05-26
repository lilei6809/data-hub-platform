---
id: "12feb930-6a9c-45c3-b1f8-8173ee0ac958"
entity_type: "task"
entity_id: "a06934d0-f220-433f-b32c-5b5a6830042f"
title: "定义 IdentityMode / RealmStrategy 枚举与 IdentityProvider / Policy 扩展占位类型 - Notes"
status: "done"
priority: "medium"
display_id: "LEI-161"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:26.50522+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

定义 IdentityMode / RealmStrategy 枚举与 IdentityProvider / Policy 配置占位类型，为 Desired State 未来扩展预留稳定结构。

## Implementation Approach

1. 定义 IdentityMode（LOCAL_ONLY、BROKERED_IDP）与 RealmStrategy（SHARED_REALM、DEDICATED_REALM）枚举。
2. 定义 IdentityProviderConfig 与 PolicyConfig 占位类型（sealed interface 或最小 record），仅保留可识别字段。
3. Javadoc 标明第一版仅作为扩展占位，主流程默认处理 LOCAL_ONLY + SHARED_REALM + 空列表。
4. 单元测试覆盖枚举常量、占位类型构造与不变量。

## Acceptance Criteria

- IdentityMode 与 RealmStrategy 枚举包含未来扩展值
- IdentityProviderConfig 与 PolicyConfig 为最小占位类型
- Javadoc 明确"第一版仅作扩展占位"
- 不引入 IdP SDK、策略引擎或 Spring 依赖
- 单元测试覆盖枚举与占位类型

## Technical Constraints

- 不引入真实 IdP / MFA 策略字段
- 占位类型不能要求第一版主流程处理它们
- 仅作为后续 Step / Port 方法扩展的挂载点## Details

**Scope**: IdentityMode、RealmStrategy 枚举；IdentityProviderConfig 与 PolicyConfig 最小占位类型；默认值与单元测试

**Out of Scope**: TenantIamDesiredState 聚合本身（独立子任务）、Configure IdentityProvider Step 与 MFA Step 实现（兄弟任务 SecretStore 与企业 IAM 扩展）、Dedicated Realm 真实实现（兄弟任务）、真实策略引擎 / OPA 集成

**Implementation**: 1. 定义 IdentityMode 枚举，至少包含 LOCAL_ONLY 与 BROKERED_IDP；BROKERED_IDP 当前不被主流程使用但必须存在。
2. 定义 RealmStrategy 枚举，至少包含 SHARED_REALM 与 DEDICATED_REALM。
3. 定义 IdentityProviderConfig 占位类型（推荐 sealed interface 或抽象 record），第一版只保留可识别的最小字段（如 providerAlias），用于未来扩展。
4. 定义 PolicyConfig 占位类型（同上），表达未来 MFA、密码、登录策略；第一版字段最小。
5. 在 Javadoc 中明确"第一版仅作为扩展占位，主流程仅处理 LOCAL_ONLY + SHARED_REALM + 空列表"。
6. 单元测试覆盖枚举常量存在性、占位类型构造与 equals/hashCode。

## Acceptance Criteria

- [ ] IdentityMode 枚举包含 LOCAL_ONLY 与 BROKERED_IDP；RealmStrategy 包含 SHARED_REALM 与 DEDICATED_REALM
- [ ] IdentityProviderConfig 与 PolicyConfig 为最小占位类型，不引入真实 IdP/策略引擎字段
- [ ] Javadoc 明确标注“第一版仅作为扩展占位”与主流程默认行为
- [ ] 枚举与占位类型均不依赖 Spring、Keycloak SDK、OPA 或其他基础设施
- [ ] 单元测试验证枚举常量、占位类型构造与不变量

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |
