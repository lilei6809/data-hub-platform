---
id: "df465930-e63f-4d7b-a0b4-0dfa90f7df32"
entity_type: "task"
entity_id: "02ab5dcf-3f05-4f2a-890b-432c17d83037"
title: "实现 EnforceMfaPolicyStep 扩展点以承接租户 MFA 策略 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-171"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:19.130775+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

为租户 MFA 策略提供 EnforceMfaPolicyStep 扩展点；在 MVP 默认（policies 为空）场景下保持为可观察 no-op，未来 MFA 落地不需重写 Pipeline。

## 实现要点

1. 实现 `EnforceMfaPolicyStep`，遵循现有 Step 接口风格。
2. 从 desired state 的 `policies` 集合中筛选 MFA 类型条目，按领域规则映射为单一 `MfaPolicy` 值对象。
3. 空集合或无 MFA policy 时返回成功（no-op）。
4. 有 MFA policy 时调用 `KeycloakAdminPort.ensureMfaPolicy(tenantId, mfaPolicy)`。
5. 在 Step Pipeline 中注册到 EnsureTenantAdminRoleStep 之后、PublishTenantIamProvisionedEventStep 之前。
6. 单元测试覆盖空 policies、含 MFA、Fake Adapter 占位抛错三条路径。

## 验收标准

- Step 在 Pipeline 中位置正确
- 空 policies 不触达 ensureMfaPolicy
- 含 MFA policy 时按映射规则调用一次
- 多 MFA 相关条目合并为单一 policy
- 单元测试三条路径覆盖

## 技术约束

- 遵守 ensure 幂等语义
- 只处理 MFA 类型策略，不接管密码/登录策略
- 空 policies 必须为 no-op

## 范围说明

- **包含**：Step 实现、Pipeline 注册、policies → MfaPolicy 映射、单元测试
- **不包含**：真实 Keycloak Authentication Flow 配置；密码策略 / 登录策略落地；KeycloakAdminPort.ensureMfaPolicy 自身定义## Details

**Scope**: EnforceMfaPolicyStep 实现、Pipeline 中的注册、从 policies 集合识别 MfaPolicy 的转换逻辑、三条路径的单元测试（空 policies、含 MFA policy、不含 MFA policy）。

**Out of Scope**: ensureMfaPolicy 在真实 Keycloak Admin Adapter 中的落地；KeycloakAdminPort 扩展方法本身的定义；MFA 活体验收场景与用户端体验。

**Constraints**: Step 遵守 ensure 幂等语义, policies 为空或不含 MFA 类型时必须为可观察 no-op, Step 不得接管其他类型的 policy（如密码策略、登录策略），这些在 brief 中被列为未来扩展但不在本任务范围, Pipeline 注册位置需在 EnsureTenantAdminRoleStep 之后、PublishTenantIamProvisionedEventStep 之前

## Acceptance Criteria

- [ ] EnforceMfaPolicyStep 被注册进 Step Pipeline，位置正确（TenantAdminRole 之后、PublishEvent 之前）
- [ ] policies 为空时 step 返回成功且不调用 KeycloakAdminPort.ensureMfaPolicy
- [ ] policies 含一个 MFA policy 时，step 映射为 MfaPolicy 值对象并调用 ensureMfaPolicy 一次
- [ ] policies 含多个 MFA 相关项时能按领域规则合并为单一 MfaPolicy，避免重复调用
- [ ] 单元测试覆盖：空 policies、含 MFA policy 成功路径、Fake Adapter 拋 UnsupportedInMvpException 的错误映射路径

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

