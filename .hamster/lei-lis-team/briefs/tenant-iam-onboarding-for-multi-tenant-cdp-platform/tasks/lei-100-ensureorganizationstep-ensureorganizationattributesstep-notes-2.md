---
id: "40dd2b06-76f8-4ce9-91fc-9bd736da3b6f"
entity_type: "task"
entity_id: "faa3155e-e5de-4d16-ba72-972b8e33c05b"
title: "实现 EnsureOrganizationStep 与 EnsureOrganizationAttributesStep - Notes"
status: "todo"
priority: "high"
display_id: "LEI-100"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:45.692406+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 Pipeline 中两个负责 Organization 的 Ensure 步骤，保证 Shared Realm `cdp` 中存在与 `tenantId` 对应的 Organization 并携带正确的 `tenant_id` 与 `tier` 属性。

## 实现要点

- `EnsureOrganizationStep`
- 通过 `KeycloakAdminPort.ensureOrganization(tenantId, attributes)` 创建或复用 Organization。
- 在 `StepExecutionContext` 写入 Keycloak 返回的 Organization 标识，供后续步骤使用。
- 收到 Keycloak 409 时查询已有 Organization 并继续。
- `EnsureOrganizationAttributesStep`
- 校对当前 Organization 属性与 Desired State 的 `tenant_id`、`tier`。
- 不一致时按 ensure 语义校正（写入正确值或调用 Port 提供的更新方法）。
- 一致时跳过实际写操作，结果视为成功。
- 两个步骤共用同一 Context 槽位，禁止重复调用 Port 造成多次远程副作用。

## 验收标准

- 首次执行创建 Organization 并设置正确属性。
- 重复执行复用 Organization、不重复写属性。
- Keycloak 409 不导致失败。
- 步骤实现只依赖 `KeycloakAdminPort`，不引用 SDK 类型。

## 技术约束

- 属于应用/领域层，禁止直接依赖 Keycloak SDK 或 HTTP 客户端。
- 仅消费 sibling 任务定义的 `KeycloakAdminPort` 与 `TenantIamDesiredState`，不重新定义。

## 范围边界

- **包含**：两个 Step 的实现与单元级方法逻辑。
- **不包含**：Port 接口定义、Fake/真实 Adapter、Pipeline 组装、其他 Ensure 步骤、状态机推进、事件发布、跨步骤的端到端测试。## Details

**Scope**: EnsureOrganizationStep 与 EnsureOrganizationAttributesStep 两个 Step 的实现，使用已存在的 KeycloakAdminPort 接口

**Out of Scope**: KeycloakAdminPort 接口本身的定义、Fake Adapter 实现、真实 Keycloak Adapter、后续 User/Membership/Role 步骤、Pipeline 组装、状态机推进

## Acceptance Criteria

- [ ] `EnsureOrganizationStep` 使用 `KeycloakAdminPort.ensureOrganization(tenantId, attributes)`；Organization 不存在时创建，存在时复用，并把得到的 Organization 标识写入 StepExecutionContext
- [ ] `EnsureOrganizationAttributesStep` 能检测 Organization 当前属性与 Desired State 的 `tenant_id`、`tier` 不一致的情况，并按资源 ensure 语义进行校正，调用后 Organization 属性与 Desired State 一致
- [ ] Keycloak 返回 409 Conflict 时两个步骤都能通过查询已有 Organization 继续，而不报错退出
- [ ] 重复执行这两个步骤不会创建重复 Organization，也不会重复写入相同的属性导致状态漂移
- [ ] 步骤实现不引入 Keycloak SDK 类型，仅依赖 KeycloakAdminPort 抽象

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |
| skillReferences | Hamster Blueprint |

