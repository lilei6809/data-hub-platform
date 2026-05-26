---
id: "501f3676-1012-4337-b2d3-4829570c350e"
entity_type: "task"
entity_id: "85706d16-17c9-4a51-9962-fd101ae48f9a"
title: "实现 EnsureAdminUserStep 与 EnsureOrganizationMembershipStep - Notes"
status: "todo"
priority: "high"
display_id: "LEI-106"
parent_task_id: "4585bfc9-9d0e-45c2-b9c3-fc71e314bff8"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:16.025109+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 Step/Adapter 边界调整

User 与 Membership 的幂等细节由 `KeycloakAdminPort` Adapter 实现，不由 Step 实现：

- `EnsureAdminUserStep` 调用 `ensureUser(email, temporaryCredentialPolicy)`，不处理 email lookup、create 409 或密码重置细节。
- `EnsureOrganizationMembershipStep` 调用 `ensureOrganizationMembership(organizationId, userId)`，不直接检查 Keycloak membership。
- 已有 User、已有 Membership、创建时 409、关系已存在 no-op 都属于 Adapter 的 ensure 契约。
- Step 只负责 Context 读取/写入、敏感信息不入日志、端口异常翻译。

## 摘要

实现 Pipeline 中负责初始租户管理员的两个 Ensure 步骤：保证管理员 User 在 Keycloak 中存在，并属于上一步骤创建/复用的 Organization。

## 实现要点

- `EnsureAdminUserStep`
- 调用 `KeycloakAdminPort.ensureUser(email, temporaryCredentialPolicy)`。
- 写入 `adminUserId` 到 `StepExecutionContext`。
- 409 fallback 与同 email User 查询由 Adapter 在 `ensureUser(...)` 内完成。
- `EnsureOrganizationMembershipStep`
- 从 Context 读取 `organizationId` 与 `adminUserId`。
- 调用 `KeycloakAdminPort.ensureOrganizationMembership(organizationId, userId)`。
- 关系已存在视为成功。
- 任何包含临时密码或凭证策略细节的字段都不得进入日志、异常消息或步骤返回值。

## 验收标准

- 首次执行创建 User 并加入 Organization。
- 重复执行不创建重复 User、不重复添加 Membership。
- 409 不泄漏到 Step；Adapter 应按 Port 契约消解。
- 临时密码与凭证策略不会泄漏到日志。

## 技术约束

- 仅依赖 sibling 定义的 `KeycloakAdminPort` 与 `TenantIamDesiredState`/`AdminUser`。
- 不引入 Keycloak SDK、HTTP 客户端、SecretStore 实现。

## 范围边界

- **包含**：两个 Step 的实现及敏感信息保护逻辑。
- **不包含**：Port 接口、Adapter、角色相关步骤、Pipeline 组装、SecretStore 接入、状态机/事件发布步骤。## Details

**Scope**: EnsureAdminUserStep 与 EnsureOrganizationMembershipStep 的实现，以及临时密码不入日志的保护逻辑

**Out of Scope**: KeycloakAdminPort 接口定义、Fake/真实 Adapter、角色划拨步骤、状态机或事件步骤、Pipeline 组装、Secret 存储集成

## Acceptance Criteria

- [ ] `EnsureAdminUserStep` 能根据 Desired State 的 `adminEmail` 创建或复用 Keycloak User，并把 `adminUserId` 写入 StepExecutionContext
- [ ] `EnsureOrganizationMembershipStep` 能从 Context 读取 `organizationId` 与 `adminUserId`，调用 Port 保证归属关系存在；关系已存在时视为成功
- [ ] 重复执行这两个步骤不会创建重复 User、也不会重复添加 Membership
- [ ] Keycloak 返回 409 Conflict 时由 Adapter 查询现有 User 或验证现有 Membership 并继续；Step 不包含 409 分支
- [ ] 临时密码、凭证、secret 不会出现在步骤输出、异常信息或日志中

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |
| skillReferences | Hamster Blueprint |
