---
id: "cb1d7002-5b6c-4460-84d9-e9c8f7233f5e"
entity_type: "task"
entity_id: "1d598927-27bd-4384-ab4a-837a1f31cf36"
title: "实现 ConfigureIdentityProviderStep 扩展点以承接 BYO IdP 场景 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-164"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:38.750274+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

为 IdentityMode = BROKERED_IDP 场景提供 ConfigureIdentityProviderStep 扩展点；在 LOCAL_ONLY 默认场景下保持为可观察的 no-op，并将 IdP 凭证统一通过 SecretStorePort 解析。

## 实现要点

1. 实现 `ConfigureIdentityProviderStep`，按现有 Step 接口风格定义。
2. 在 Step Pipeline 注册位置：Organization/OrganizationAttributes Step 之后，TenantAdminRole Step 之前。
3. 入口分支：

- `identityMode = LOCAL_ONLY` 或 `identityProviders` 为空 → 直接返回成功（no-op）。
- `identityMode = BROKERED_IDP` 且非空 → 遍历每个 `IdentityProviderConfig`，通过 SecretStorePort 解析 secret，然后调用 `KeycloakAdminPort.ensureIdentityProvider`。

1. 严格脱敏：日志只输出 IdP alias 与 protocol，不输出 secret。
2. 单元测试覆盖三条路径：LOCAL_ONLY no-op、BROKERED_IDP 成功调用、Fake Adapter 占位抛错的错误处理。

## 验收标准

- Step 在 Pipeline 中位置正确
- LOCAL_ONLY 默认场景下不触达 KeycloakAdminPort.ensureIdentityProvider
- BROKERED_IDP 场景按顺序调用，secret 来自 SecretStorePort
- 重复执行依赖 ensure 语义保持幂等
- 单元测试三条路径全覆盖，日志脱敏验证通过

## 技术约束

- IdP secret 不得出现在 desired state、日志或异常消息中
- Step 必须遵守现有 ensure 幂等语义
- LOCAL_ONLY 路径必须为可观察的 no-op，不能误报失败

## 范围说明

- **包含**：Step 实现、Pipeline 注册、IdentityMode 分支、SecretStorePort 集成、单元测试
- **不包含**：真实 Keycloak Admin Adapter 中 ensureIdentityProvider 的实际 Keycloak API 调用；KeycloakAdminPort 或 SecretStorePort 自身的定义（其他子任务）## Details

**Scope**: ConfigureIdentityProviderStep 实现、Pipeline 中的位置注册、IdentityMode 分支、与 SecretStorePort 的集成、针对 LOCAL_ONLY 与 BROKERED_IDP 两条路径的幂等测试。

**Out of Scope**: ensureIdentityProvider 在真实 Keycloak Admin Adapter 的落地；SecretStorePort 或 KeycloakAdminPort 扩展方法本身的定义（其他子任务）；IdP 导出的动态重载与管理 UI。

**Constraints**: Step 必须继承与其他 ensureXxx Step 一致的接口与幂等语义, client secret 不得出现在 TenantIamDesiredState、日志或异常消息中，仅通过 SecretStorePort 解析, LOCAL_ONLY 路径下必须为可观察的 no-op，不得报错，不得写入本地状态除 step 进度以外的字段, 步骤在 Pipeline 中的位置需位于 EnsureOrganization、EnsureOrganizationAttributes 之后，使 IdP 可以附属到已存在的 Organization 上

## Acceptance Criteria

- [ ] ConfigureIdentityProviderStep 被注册进 Step Pipeline，位置位于 Organization 相关 Step 之后、Role 相关 Step 之前
- [ ] identityMode = LOCAL_ONLY 或 identityProviders 为空时，step 返回成功且不调用 KeycloakAdminPort.ensureIdentityProvider
- [ ] identityMode = BROKERED_IDP 且 identityProviders 非空时，step 遵序高效调用 ensureIdentityProvider，且通过 SecretStorePort 解析 client secret
- [ ] 重复执行同一 desired state 不会让 step 报错或重复创建 IdP（依赖底层 ensure 幂等语义）
- [ ] 单元测试覆盖三条路径：LOCAL_ONLY no-op、BROKERED_IDP 成功调用、Fake Adapter 拋 UnsupportedInMvpException 时的错误映射
- [ ] 测试明确验证日志输出不包含 client secret 明文

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

