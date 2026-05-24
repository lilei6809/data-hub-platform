---
id: "582ecad6-63a9-4cac-b00c-3edd3343c350"
entity_type: "task"
entity_id: "e7cf7519-f044-45ba-b50f-d0dcff93a065"
title: "实现 EnsureOrganizationStep 与 EnsureOrganizationAttributesStep - Notes"
status: "todo"
priority: "high"
display_id: "LEI-141"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:01.322897+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 Step Pipeline 中关于 Organization 身份事实的两个幂等步骤：Organization 本身的 ensure 与 Organization attributes 的 ensure。

## 实现思路

1. 实现 `EnsureOrganizationStep`：

- 通过 KeycloakAdminPort 查询/创建 Organization。
- 不存在则创建；已存在则复用；Port 抛 409 时按 tenantId 查询后继续。
- 将 organizationId 写入 ExecutionContext 并返回。

1. 实现 `EnsureOrganizationAttributesStep`：

- 读取 ExecutionContext 中的 organizationId。
- 读取当前 Organization 属性；与 Desired State 中的 `tenantId`、`tier` 比对。
- 缺失 → 写入；不一致 → 校正；一致 → 通过。

1. 编写 Step 级单元测试，使用 Mock/Stub 的 KeycloakAdminPort 覆盖幂等场景。

## 验收标准

- Organization 首次创建后 organizationId 被写入 ExecutionContext。
- 重复执行不会创建重复 Organization；409 Conflict 被识别为已存在。
- Attributes 缺失、不一致、一致三种场景均处理正确。
- 单元测试覆盖以上场景。

## 技术约束

- Step 内部不出现 Keycloak SDK 类型，仅依赖 KeycloakAdminPort 的意图型方法。
- 属性校正策略：以 Desired State 为权威。
- 失败通过领域异常表达。

## 范围边界

- **不**实现 KeycloakAdminPort 接口或 Fake Adapter（兄弟任务）。
- **不**实现其他 Step、Pipeline 组合或 Service 编排。## Details

**Scope**: EnsureOrganizationStep 与 EnsureOrganizationAttributesStep 的实现及单元测试

**Out of Scope**: KeycloakAdminPort 接口定义与 Fake Adapter（兄弟任务）、其他 Step、Pipeline 组合机制、Provisioning Service 编排

## Acceptance Criteria

- [ ] `EnsureOrganizationStep` 在 Organization 不存在时创建并将 organizationId 写入 ExecutionContext
- [ ] `EnsureOrganizationStep` 在 Organization 已存在或 Port 抛出 409 Conflict 时查询并复用，不创建重复对象
- [ ] `EnsureOrganizationAttributesStep` 在属性缺失时写入，属性不一致时按 Desired State 校正，属性一致时直接通过
- [ ] 两个 Step 均不直接依赖 Keycloak SDK，仅通过 KeycloakAdminPort 抽象访问外部系统
- [ ] 针对每个 Step 编写单元测试覆盖：首次创建、已存在复用、409 冲突恢复、属性校正

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

