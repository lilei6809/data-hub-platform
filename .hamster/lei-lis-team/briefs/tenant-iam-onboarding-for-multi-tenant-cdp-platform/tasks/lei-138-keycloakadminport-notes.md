---
id: "172d4340-8355-431e-8d50-d3cf96254a91"
entity_type: "task"
entity_id: "0730d918-196a-444a-8c2e-38e92b99e591"
title: "定义 KeycloakAdminPort 意图型接口与端口契约 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-138"
parent_task_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:54.046344+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 Port 契约调整

`KeycloakAdminPort` 的 `ensureXxx` 方法现在被定义为应用层幂等契约，而不是 Keycloak SDK 的薄包装。

契约要求：

- 调用方只看到“目标 IAM fact 已经被确保存在/收敛”的结果。
- 对象不存在、对象已存在、属性不一致、关系已存在、创建时 409 Conflict 等情况，都应由 Adapter 在 `ensureXxx` 内部消解。
- Step 和 Application Service 不处理 Keycloak 409，不按 HTTP status 分支，也不引用 Keycloak SDK 异常。
- Adapter 只能向上抛端口级异常，例如认证失败、外部不可用、配置错误、资源缺失等，并标记是否可重试。

这个调整覆盖下文中关于“冲突由步骤处理”的旧描述。未来验收 `KeycloakAdminPort` 时，重点验收 Port 方法是否足够表达业务意图，以及 Adapter 是否完整履行幂等契约。

## Summary

定义 `KeycloakAdminPort` 意图型接口和端口契约，作为应用核心与 Keycloak 之间的稳定边界。

## Implementation Approach

1. 在应用/领域核心层创建 `KeycloakAdminPort` 接口，不引入任何 Keycloak SDK 依赖。
2. 定义 5 个第一版意图型方法：

- `ensureOrganization(tenantId, attributes) -> OrganizationId`
- `ensureUser(email, temporaryCredentialPolicy) -> UserId`
- `ensureOrganizationMembership(organizationId, userId)`
- `ensureRealmRole(realmRoleName)`
- `ensureUserRealmRole(userId, realmRoleName)`

1. 为入参定义稳定的端口级值对象（如 `OrganizationAttributes`、`TemporaryCredentialPolicy`），返回端口级 ID 类型（`OrganizationId`、`UserId`）。
2. 定义端口级异常族（如 `KeycloakAdminPortException` 及子类型），表达"外部不可用"、"非法输入"等抽象语义；不暴露 HTTP 状态码或 Keycloak 原生异常。
3. 在接口或同包内以注释/占位接口的方式标注未来扩展方法：`ensureIdentityProvider`、`ensureProtocolMapper`、`ensureClientAudience`、`ensureMfaPolicy`。
4. 为每个方法编写 Javadoc，明确 `ensure` 幂等语义：存在则复用、不存在则创建、冲突自动消解、属性按规则校正。

## Acceptance Criteria

- KeycloakAdminPort 接口定义在领域/应用核心层，不依赖 Keycloak SDK 或 HTTP 客户端
- 接口包含第一版 5 个 `ensure*` 方法及对应签名
- 返回类型为稳定的端口级 ID/结果对象，不泄漏 Keycloak 原生类型
- 定义端口级异常类型表达冲突、不可用等语义
- 为未来 4 个扩展方法预留占位或注释
- 每个方法 Javadoc 明确 ensure 幂等语义

## Technical Constraints

- 接口必须可被 JVM/Spring Boot 项目使用，但不依赖任何具体框架（无 `@Component`、无 Spring 注解）
- 不引入 `org.keycloak.*` 依赖
- 入参/出参值对象不可变（推荐 Java `record` 或等价不可变结构）

## Code Patterns to Follow

- 意图型 Port 命名：`ensure*` 而非 `create*`
- Hexagonal / Ports and Adapters 结构，Port 位于应用核心
- 端口暴露抽象异常，由 Adapter 负责映射底层异常## Details

**Scope**: KeycloakAdminPort 接口、方法签名、入参/返回类型对象（如 OrganizationAttributes、TemporaryCredentialPolicy、OrganizationId、UserId）、端口级异常类型、扩展点占位注释

**Out of Scope**: 任何 Adapter 实现（Fake 或真实 Keycloak）、Step Pipeline、Provisioning Service、TenantIamDesiredState 领域模型（属于 sibling 1）、SecretStorePort（属于 sibling 8）、EventPublisher（属于 sibling 5）

## Acceptance Criteria

- [ ] KeycloakAdminPort 接口定义在领域/应用核心层，不依赖任何 Keycloak SDK、HTTP 客户端或具体实现类型
- [ ] 接口包含第一版 5 个方法：ensureOrganization(tenantId, attributes)、ensureUser(email, temporaryCredentialPolicy)、ensureOrganizationMembership(organizationId, userId)、ensureRealmRole(realmRoleName)、ensureUserRealmRole(userId, realmRoleName)
- [ ] 每个方法返回稳定的端口级标识或结果对象（如 OrganizationId、UserId），不暴露 Keycloak 原生 representation 类型
- [ ] 定义端口级异常或错误类型，表达冲突、未找到、外部不可用等语义，调用方无需感知 HTTP 状态码
- [ ] 为未来扩展方法（ensureIdentityProvider、ensureProtocolMapper、ensureClientAudience、ensureMfaPolicy）以代码注释或单独占位接口形式预留扩展点，但不实现
- [ ] 方法 Javadoc 明确说明 ensure 语义：对象不存在则创建、已存在则复用、属性不一致按规则校正、不抛出冲突异常

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
