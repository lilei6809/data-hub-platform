---
id: "e44a284e-3df1-4e8b-9578-475af63a1485"
entity_type: "task"
entity_id: "0e38966f-11e5-450a-9eec-992eef9051a6"
title: "扩展 TenantIamDesiredState 模型承载 BYO IdP、MFA 与 Dedicated Realm 配置 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-108"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:17.569023+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

扩展 TenantIamDesiredState 领域模型，启用并完善 identityMode、realmStrategy、identityProviders、policies 四个预留字段，使其能够承载 BYO IdP、MFA 与 Dedicated Realm 场景。

## Implementation Approach

1. 启用枚举：

- `IdentityMode { LOCAL_ONLY, BROKERED_IDP }`，默认 LOCAL_ONLY。
- `RealmStrategy { SHARED_REALM, DEDICATED_REALM }`，默认 SHARED_REALM。

1. 定义 `IdentityProviderConfig` 值对象：alias、providerType（OIDC/SAML）、displayName、configMap、secretRef（指向 SecretStore）。
2. 定义 `TenantPolicySet` 值对象，第一版包含 `MfaPolicy`：factor（TOTP/WEBAUTHN）、enforcementLevel（REQUIRED/OPTIONAL）、appliedRoles。
3. 在 DesiredState 构造函数加入跨字段校验：

- identityMode=BROKERED_IDP 时 identityProviders 不能为空。
- realmStrategy=DEDICATED_REALM 仅在符合 tier 条件下允许。

1. secretRef 字段类型为不可读明文的引用，由 SecretStorePort 解析。
2. 编写单元测试覆盖默认值、非法组合、向后兼容。

## Acceptance Criteria

- IdentityMode 与 RealmStrategy 枚举完整且有默认值
- IdentityProviderConfig 与 MfaPolicy 值对象完整
- 构造期校验拒绝非法组合
- secretRef 不存明文
- 单元测试覆盖默认值、非法组合与兼容性

## Technical Constraints

- 不能修改 DesiredState 既有基础字段的语义
- 所有新值对象不可变
- secretRef 只能通过 SecretStorePort 解析为 SecretValue

## Code Patterns to Follow

- 与 DesiredState 已有值对象保持构造期校验风格一致
- 枚举默认值显式声明在工厂方法中

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: TenantIamDesiredState 中 identityMode/realmStrategy/identityProviders/policies 四个扩展字段的值对象、枚举、默认值、不变式与构造期校验。

**Out of Scope**: DesiredState 基础字段 tenantId/tier/adminEmail（属于兄弟任务 8c75479f）、Step Pipeline 编排逻辑、Keycloak Port 方法实现。

## Acceptance Criteria

- [ ] IdentityMode 枚举包含 LOCAL_ONLY 与 BROKERED_IDP，默认 LOCAL_ONLY
- [ ] RealmStrategy 枚举包含 SHARED_REALM 与 DEDICATED_REALM，默认 SHARED_REALM
- [ ] IdentityProviderConfig 值对象包含 alias、providerType、displayName、configMap、secretRef 字段，且 secretRef 不存明文 secret
- [ ] MfaPolicy 值对象包含 factor、enforcementLevel、适用角色范围
- [ ] 构造期校验：identityMode=BROKERED_IDP 但 identityProviders 为空时报领域错误，realmStrategy=DEDICATED_REALM 仅允许在对应 tier 下使用
- [ ] 单元测试覆盖默认值、非法组合进行拒绝、与现有 DesiredState 基础字段的兼容

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

