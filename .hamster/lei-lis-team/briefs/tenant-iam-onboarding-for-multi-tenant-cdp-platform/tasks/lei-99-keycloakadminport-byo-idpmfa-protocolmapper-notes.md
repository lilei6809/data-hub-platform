---
id: "b60b6106-c00d-4f6e-b473-54d895917cb8"
entity_type: "task"
entity_id: "07da4f7b-7528-4e1e-b8f1-bcacc0e0c822"
title: "扩展 KeycloakAdminPort 承接 BYO IdP、MFA 与 ProtocolMapper 方法签名 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-99"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:44.587998+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在基础 KeycloakAdminPort 上追加 BYO IdP、MFA、ProtocolMapper、ClientAudience 四类企业扩展方法签名，并在 Fake Adapter 中提供可断言的实现以支撑扩展 Step 的测试。

## Implementation Approach

1. 在 KeycloakAdminPort 接口追加方法：

- `ensureIdentityProvider(tenantId, IdpConfig)`
- `ensureProtocolMapper(clientId, ProtocolMapperConfig)`
- `ensureClientAudience(clientId, ClientAudienceConfig)`
- `ensureMfaPolicy(tenantId, MfaPolicyConfig)`

1. 定义参数 DTO：`IdpConfig`（alias、providerId、displayName、config map）、`ProtocolMapperConfig`、`ClientAudienceConfig`、`MfaPolicyConfig`（factor、enforcement level）。
2. 扩展 Fake/In-Memory KeycloakAdminAdapter：

- 内部使用 Map 存储已配置 IdP/Mapper/Audience/MfaPolicy。
- 实现 ensure 幂等：存在则复用，属性不一致则覆盖。
- 暴露查询方法供测试断言。

1. 编写单元测试验证幂等行为、参数 DTO 序列化、Fake 断言能力。

## Acceptance Criteria

- KeycloakAdminPort 新增四个企业扩展方法签名
- 参数为不依赖 Keycloak SDK 的领域 DTO
- Fake Adapter 提供可断言的内存实现
- 重复调用 ensure 不产生重复对象
- 单元测试覆盖首次创建、重复调用、属性校正三种场景

## Technical Constraints

- 不得引入 Keycloak SDK 依赖到 Port 或 DTO 层
- 所有 ensure 方法必须返回稳定的标识符（例如 IdP alias、MapperId）供后续步骤引用
- 方法签名必须与现有 ensureOrganization/ensureUser 风格保持一致

## Code Patterns to Follow

- 与现有 ensureOrganization/ensureUser 的命名、返回类型、异常风格保持一致
- 意图型方法而非 CRUD

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: KeycloakAdminPort 上四个企业扩展方法的接口签名、参数 DTO、Fake Adapter 的可断言实现以及幂等语义的单元测试。

**Out of Scope**: 基础 ensureOrganization/ensureUser/ensureRealmRole 等方法（属于兄弟任务）、真实 Keycloak Admin API 调用（属于 Phase 2 兄弟任务）、Step 本身的编排逻辑。

## Acceptance Criteria

- [ ] KeycloakAdminPort 新增四个方法签名：ensureIdentityProvider、ensureProtocolMapper、ensureClientAudience、ensureMfaPolicy
- [ ] 所有新增方法的参数为领域 DTO（IdpConfig、ProtocolMapperConfig、ClientAudienceConfig、MfaPolicyConfig），不依赖 Keycloak SDK 类型
- [ ] Fake Adapter 提供可断言的内存实现，能够记录调用供测试验证
- [ ] 重复调用同一 ensure 方法 Fake 不会产生重复对象，验证幂等语义
- [ ] 单元测试覆盖：首次调用创建、重复调用复用、属性不一致时 Fake 表现为校正

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |
| skillReferences | Hamster Blueprint |

