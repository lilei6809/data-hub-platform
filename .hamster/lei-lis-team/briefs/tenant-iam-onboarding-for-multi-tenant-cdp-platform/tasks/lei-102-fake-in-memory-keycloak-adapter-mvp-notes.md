---
id: "819e0f30-20ea-479d-b885-110368545402"
entity_type: "task"
entity_id: "7e7c0b57-88e2-4b62-a423-77ee29061795"
title: "实现 Fake/In-Memory Keycloak Adapter 承载 MVP 流程 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-102"
parent_task_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:50.775737+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概要

实现 `KeycloakAdminPort` 的 Fake/In-Memory Adapter，使 MVP onboarding 流程可以在没有真实 Keycloak 的情况下运行并验证幂等语义。

## 实施方式

1. 创建 FakeKeycloakAdminAdapter 实现 KeycloakAdminPort 全部 MVP 方法
2. 使用内存 Map 存储 Organizations / Users / Memberships / Roles / RoleAssignments，以稳定领域键去重
3. 每个 ensure 方法按稳定键查询：命中返回已有 ID，未命中创建并返回新 ID
4. ensureOrganization 处理 attribute 不一致时的校正规则
5. 提供 FaultInjector 钩子：按方法名 + 调用序号注入 Transient/Conflict 异常
6. 暴露只读查询 API 供测试断言（findOrganizationByTenantId、findUserByEmail 等）

## 验收标准

- 所有 MVP ensure 方法实现 ensure 语义，重复调用幂等且返回相同 ID
- ensureOrganization 写入并维护 tenant_id 与 tier attributes
- Membership / Role Assignment 已存在时不报错也不重复
- 故障注入机制可在指定调用次序抛出 Port 层异常
- Adapter 可作为 KeycloakAdminPort 的可注入实现供测试使用
- 提供测试用只读查询 API

## 技术约束

- 仅用于测试与本地开发，无持久化与并发优化要求
- ensure 去重必须依赖领域稳定键，不依赖随机生成的 UUID
- 故障注入必须可确定性复现## Details

**Scope**: FakeKeycloakAdminAdapter 实现、内存数据结构（Organization、User、Membership、Role、RoleAssignment）、ensure 语义实现、故障注入钩子

**Out of Scope**: 真实 Keycloak Admin API Adapter（兄弟任务 6）、Step Pipeline 实现（兄弟任务 3）、状态机与 Provisioning Service（兄弟任务 4）、事件发布（兄弟任务 5）、SecretStorePort（兄弟任务 8）

**Implementation**: 1. 创建 FakeKeycloakAdminAdapter 类实现 KeycloakAdminPort。2. 使用 Map 等内存结构存储 organizations/users/memberships/roles/roleAssignments，键选择稳定字段（tenantId、email、roleName）。3. 每个 ensure 方法先按稳定键查询：命中返回已有 ID，未命中生成新 ID 并存储。4. ensureOrganization 在 attributes 不一致时按规则校正并记录。5. 提供 FaultInjector 配置（按方法名/调用序号触发异常或返回 Conflict）。6. 暴露只读查询方法供测试断言（findOrganizationByTenantId、findUserByEmail 等），但只用于测试代码。

**Constraints**: Fake 仅用于测试与本地开发，不引入持久化依赖, ensure 方法必须以领域稳定键查询，不允许依赖随机 UUID 作为唯一去重依据, 故障注入钩子仅接受确定性规则（调用序号、方法名），以保证测试可重复

## Acceptance Criteria

- [ ] FakeKeycloakAdminAdapter 实现 KeycloakAdminPort 的所有 MVP 方法，重复调用同一 ensure 方法不产生重复对象且返回相同稳定 ID
- [ ] ensureOrganization 能正确存储 tenant_id 与 tier attributes；后续调用发现已存在时按 attribute 一致性规则复用或校正
- [ ] ensureOrganizationMembership 与 ensureUserRealmRole 在关系已存在时返回成功，不抛出错误也不创建重复关系
- [ ] Adapter 提供可配置的故障注入机制，可以在指定方法的指定调用次序抛出 Port 层定义的 Transient/Conflict 异常，以支持失败恢复测试
- [ ] Adapter 作为应用核心的适配实现可被外部模块（如测试或 In-Memory Runner）依赖注入到 Step Pipeline 与 Provisioning Service 中
- [ ] 提供仅供测试使用的只读查询 API（例如按 tenantId 查 Organization、按 email 查 User），以支持验证场景中的断言

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

