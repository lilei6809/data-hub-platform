---
id: "0b82a8af-a2bf-4df4-9821-34524348b2d5"
entity_type: "task"
entity_id: "7460277b-7dfa-4e35-8624-97973466cc42"
title: "定义 SecretStorePort 抽象与内存实现承载凭证读取 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-90"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:10.735565+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

定义 SecretStorePort 抽象与内存 Fake 实现，承载 Keycloak Admin 凭证与未来 BYO IdP secrets 的读取边界。

## Implementation Approach

1. 在领域/应用核心定义 `SecretStorePort` 接口，提供意图型方法：

- `getKeycloakAdminCredentials(): KeycloakAdminCredentials`
- `getTenantSecret(tenantId, secretName): SecretValue`

1. 引入 `SecretValue` 值对象，重写 `toString()` 与序列化为 `***masked***`，避免日志泄漏。
2. 实现 `InMemorySecretStoreAdapter`，通过预设的 Map 提供测试场景下的 secret。
3. 重构现有 KeycloakAdminPort/Fake Adapter 的凭证获取路径，使其通过 SecretStorePort 注入而非直接读取环境变量。
4. 编写单元测试：

- 读取存在的 secret 成功返回。
- 读取不存在的 secret 返回明确的 `SecretNotFoundException`（领域错误）。
- 日志输出中不包含 secret 明文。

## Acceptance Criteria

- SecretStorePort 接口定义至少包含获取 Keycloak Admin 凭证与按 tenantId + secretName 获取租户级 secret 的意图型方法
- 提供 InMemorySecretStoreAdapter Fake 实现
- Secret 值对象在日志输出中被遮蔽
- KeycloakAdminPort 与未来扩展 Step 通过依赖注入获取 SecretStorePort
- 单元测试覆盖 secret 不存在场景

## Technical Constraints

- 不允许在 Port 接口或值对象中暴露明文 secret 的 getter（必须通过显式 `expose()` 调用获取）
- Fake 实现必须支持按 tenantId 隔离 secret 命名空间
- 接口签名必须兼容未来 Vault Adapter（异步/同步语义统一）

## Code Patterns to Follow

- Ports and Adapters 结构（与 KeycloakAdminPort、TenantIamStateRepository、EventPublisher 保持一致）
- 意图型方法命名而不是 CRUD 方法

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: SecretStorePort 接口、SecretValue 值对象、InMemorySecretStoreAdapter Fake 实现，以及凭证遮蔽日志保护。

**Out of Scope**: 真实 Vault/HashiCorp/AWS Secrets Manager 接入、密钥轮转策略、BYO IdP 配置模型本身、ConfigureIdentityProviderStep 的实现逻辑。

## Acceptance Criteria

- [ ] SecretStorePort 接口定义至少包含获取 Keycloak Admin 凭证与按 tenantId + secretName 获取租户级 secret 的意图型方法
- [ ] 提供 InMemorySecretStoreAdapter Fake 实现，可在测试和本地运行模式下加载预设 secret
- [ ] Secret 值对象在 toString/日志输出中被遮蔽（masked），单元测试验证日志中不会泄漏明文
- [ ] Port 与 Fake 实现位于应用核心之外，KeycloakAdminPort 与未来扩展 Step 通过依赖注入获取 SecretStorePort
- [ ] 单元测试覆盖：读取不存在的 secret 返回明确的领域错误而不是 null

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

