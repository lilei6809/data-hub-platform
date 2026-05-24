---
id: "8caf945f-a642-48d3-bbe5-2c7b3fee1f0e"
entity_type: "task"
entity_id: "0627db3d-6f46-44f4-adf9-873de3a7066f"
title: "扩展 KeycloakAdminPort 以承载 BYO IdP、Protocol Mapper 与 MFA 意图方法 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-152"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:52.734378+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

在 KeycloakAdminPort 上扩展 BYO IdP、Protocol Mapper、Client Audience 与 MFA 的意图型方法，并在 Fake Adapter 中提供受控占位，让 Step 扩展点有稳定的 Port 边界可调用，而无需在 MVP 中接入真实实现。

## 实现要点

1. 在 KeycloakAdminPort 上新增四个方法签名：

- `ensureIdentityProvider(TenantId, IdentityProviderConfig)`
- `ensureProtocolMapper(ClientId, ProtocolMapperConfig)`
- `ensureClientAudience(ClientId, ClientAudience)`
- `ensureMfaPolicy(TenantId, MfaPolicy)`

1. 定义对应的不可变值对象（IdentityProviderConfig、ProtocolMapperConfig、ClientAudience、MfaPolicy），字段最小化，仅承载 brief 中明确提及的语义（如 IdP alias、协议类型、audience 字符串、MFA 启用开关与因子集合）。
2. 在 Fake Adapter 中实现上述方法为受控占位：抛 `UnsupportedInMvpException`，包含明确说明，避免被无意中调用。
3. 接口级单元测试验证占位行为与值对象不可变性。

## 验收标准

- 四个新方法签名加入 KeycloakAdminPort
- 值对象定义于领域层，不依赖 Keycloak SDK 类型
- Fake Adapter 提供受控占位实现
- 现有 MVP 测试不受影响
- 单元测试覆盖新方法占位行为

## 技术约束

- 接口和值对象保持基础设施中立
- 遵守 ensure 幂等命名约定
- 占位实现必须易于识别，避免误用

## 范围说明

- **包含**：接口扩展、值对象、Fake Adapter 占位、接口测试
- **不包含**：真实 Keycloak Admin API 上的 IdP/Mapper/MFA 实现；Step 实现本身；SecretStorePort 与 Adapter 的具体接线## Details

**Scope**: KeycloakAdminPort 接口新增四个意图型方法；IdentityProviderConfig、ProtocolMapperConfig、ClientAudience、MfaPolicy 值对象；Fake Adapter 中的幂等占位实现；接口级单元测试。

**Out of Scope**: 真实 Keycloak Admin API 上的 IdP/Mapper/MFA 实现（属于后续 Phase 4）；ConfigureIdentityProviderStep 与 MfaPolicyStep 本身（独立子任务）；SecretStorePort 集成（其他子任务）。

**Constraints**: 不得在接口中泄露 Keycloak SDK 类型（如 RealmRepresentation、IdentityProviderRepresentation）, 方法需严格遵守 ensure 幂等语义，不使用 create/update 名称, Fake Adapter 占位实现需明确可识别（例如拋 UnsupportedInMvpException），避免被误用作真实实现, 新增方法不得碎片化 Port 职责，须保持与已有 ensureXxx 方法一致的意图型风格

## Acceptance Criteria

- [ ] KeycloakAdminPort 新增 ensureIdentityProvider、ensureProtocolMapper、ensureClientAudience、ensureMfaPolicy 四个方法签名，参数为领域型值对象
- [ ] IdentityProviderConfig、ProtocolMapperConfig、ClientAudience、MfaPolicy 作为不可变值对象定义于领域层，不引用任何 Keycloak SDK 类型
- [ ] Fake Adapter 中提供明确的占位实现（拋 UnsupportedInMvpException 或类似），使调用者能立即发现误用
- [ ] 现有 LOCAL_ONLY/SHARED_REALM/空 policies 场景下的所有已有测试仍然通过（不调用新方法）
- [ ] 接口级单元测试覆盖新方法的占位拋错行为与参数不可变性

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

