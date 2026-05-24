---
id: "b08fecbf-c839-482e-9608-13d59810a048"
entity_type: "task"
entity_id: "2d58d1df-62c2-4059-bd83-39f85243ce83"
title: "定义 IdentityMode、RealmStrategy 枚举与扩展字段占位类型 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-92"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:31.945413+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 `IdentityMode`、`RealmStrategy` 两个领域枚举，以及 `IdentityProviderConfig`、`TenantPolicyConfig` 两个扩展字段占位类型。

## 实施方式

1. 定义 `IdentityMode { LOCAL_ONLY, BROKERED_IDP }`，暴露默认值常量。
2. 定义 `RealmStrategy { SHARED_REALM, DEDICATED_REALM }`，暴露默认值常量。
3. 定义 `IdentityProviderConfig` 占位（接口或最小骨架），文档说明 MVP 不实现具体 IdP。
4. 定义 `TenantPolicyConfig` 占位（接口或最小骨架），文档说明 MVP 不实现 MFA/密码策略。
5. 编写最小单元测试验证默认常量、枚举值数量。

## 验收标准

- `IdentityMode` 含两常量并暴露 `LOCAL_ONLY` 默认值
- `RealmStrategy` 含两常量并暴露 `SHARED_REALM` 默认值
- `IdentityProviderConfig`、`TenantPolicyConfig` 作为扩展占位存在并有文档
- 单元测试验证常量与默认值

## 技术约束

- 占位类型不得引入 Keycloak SDK 或具体 IdP 协议依赖
- 命名必须与 PRD 中字段名严格一致

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: IdentityMode 枚举、RealmStrategy 枚举、IdentityProviderConfig 与 TenantPolicyConfig 扩展占位类型及其默认值约定与单元测试。

**Out of Scope**: TenantIamDesiredState 聚合组合（在单独子任务）、TenantIamStatus 状态枚举（属于状态机 sibling 任务）、Keycloak Port、Step Pipeline、真实 IdP/Policy 实现（属于 SecretStore 与企业扩展 sibling 任务）。

**Constraints**: 枚举命名严格遵循 PRD 中拼写（LOCAL_ONLY、BROKERED_IDP、SHARED_REALM、DEDICATED_REALM）, 占位类型零基础设施依赖，仅领域语义

## Acceptance Criteria

- [ ] `IdentityMode` 枚举包含 `LOCAL_ONLY`、`BROKERED_IDP` 两个常量，并暴露默认值常量 `LOCAL_ONLY`
- [ ] `RealmStrategy` 枚举包含 `SHARED_REALM`、`DEDICATED_REALM` 两个常量，并暴露默认值常量 `SHARED_REALM`
- [ ] 提供 `IdentityProviderConfig` 占位类型（接口或最小骨架值对象），文档注释清楚说明 MVP 不实现具体 IdP
- [ ] 提供 `TenantPolicyConfig` 占位类型（接口或最小骨架值对象），文档注释清楚说明 MVP 不实现 MFA/密码策略等具体策略
- [ ] 枚举与占位类型均带 KDoc/JavaDoc 说明扩展意图，并附带最小单元测试验证常量与默认值

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 2 |
| skillReferences | Hamster Blueprint |

