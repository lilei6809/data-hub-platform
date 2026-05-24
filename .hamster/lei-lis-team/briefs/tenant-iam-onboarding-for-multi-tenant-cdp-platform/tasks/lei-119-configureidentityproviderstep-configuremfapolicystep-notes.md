---
id: "28f4b7ec-dacd-4f21-9c88-1521ec995e3a"
entity_type: "task"
entity_id: "aa43b3a5-c021-4674-9888-081066a2e1be"
title: "实现 ConfigureIdentityProviderStep 与 ConfigureMfaPolicyStep 扩展步骤 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-119"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:54.417587+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 ConfigureIdentityProviderStep 与 ConfigureMfaPolicyStep 两个可选的幂等扩展步骤，承接 BYO IdP 与 MFA 配置。

## Implementation Approach

1. **ConfigureIdentityProviderStep**：

- 检查 `desiredState.identityMode == BROKERED_IDP` 且 `identityProviders` 非空，否则 skip 并返回成功。
- 遍历 identityProviders，对每个 IdpConfig：
- 通过 SecretStorePort 按 secretRef 解析 secret，注入 IdpConfig 的 sensitive config。
- 调用 `keycloakAdminPort.ensureIdentityProvider(tenantId, idpConfig)`。
- 记录已配置 alias 至执行上下文供后续步骤引用。

1. **ConfigureMfaPolicyStep**：

- 检查 `desiredState.policies.mfaPolicy` 是否存在，否则 skip。
- 调用 `keycloakAdminPort.ensureMfaPolicy(tenantId, mfaPolicyConfig)`。

1. 复用兄弟任务定义的 Step 接口、ExecutionContext、错误映射约定。
2. 日志输出仅记录 IdP alias、MFA factor，绝不打印 secret 明文或临时密码。
3. 编写单元测试，使用 Fake KeycloakAdminAdapter 与 InMemorySecretStoreAdapter 验证：

- 首次执行创建 IdP/MFA。
- 重复执行幂等。
- secret 解析失败映射为领域错误。
- 未配置时 skip。

## Acceptance Criteria

- 未配置时 skip
- 已配置时调用扩展 Port 方法且幂等
- secret 通过 SecretStorePort 解析且不外泄
- 单元测试覆盖 5 个场景
- 与兄弟任务 Step 接口契约一致

## Technical Constraints

- 不修改基础 Step Pipeline 顺序，由 ProvisioningService 在后续阶段决定接入位置
- secret 解析失败必须映射为可分类的领域错误（例如 SecretResolutionFailed）
- Step 单次执行不引入本地数据库事务

## Code Patterns to Follow

- 与基础 ensure Steps 共享相同的 Step 接口、ExecutionContext 与失败语义
- secret 通过 expose() 显式调用，并在使用后避免持有

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: ConfigureIdentityProviderStep 与 ConfigureMfaPolicyStep 两个扩展步骤的幂等实现、skip-when-not-configured 逻辑、与 SecretStorePort 集成、单元测试。

**Out of Scope**: 基础 Step Pipeline（兄弟 4585bfc9）、真实 Keycloak Adapter 实现（兄弟 082a15b8）、Dedicated Realm Strategy 选择逻辑、TenantIamProvisioningService 状态机修改。

## Acceptance Criteria

- [ ] ConfigureIdentityProviderStep 在 DesiredState.identityProviders 为空时跳过执行并返回成功
- [ ] ConfigureIdentityProviderStep 对 identityProviders 列表逐个调用 KeycloakAdminPort.ensureIdentityProvider，重复执行不产生重复对象
- [ ] ConfigureMfaPolicyStep 在 policies.mfaPolicy 不存在时跳过，存在时调用 KeycloakAdminPort.ensureMfaPolicy 并幂等
- [ ] 两个 Step 通过 SecretStorePort 解析 secretRef，不在领域对象或日志中外露明文 secret
- [ ] 单元测试覆盖：首次执行、重复执行、部分失败后重试、未配置时跳过、secret 解析失败时领域错误映射
- [ ] Step 实现与兄弟任务中的 Step 接口、ExecutionContext、错误映射约定保持一致

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
| skillReferences | Hamster Blueprint |

