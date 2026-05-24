---
id: "7e8b0a14-95f1-449e-b27d-1847c0fe08d1"
entity_type: "task"
entity_id: "ec798f91-7471-4c74-b768-c6c588b534c6"
title: "配置 Keycloak Admin Client 与连接基础设施 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-139"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:15:55.180453+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

为真实 Keycloak Admin API Adapter 引入 SDK 依赖、配置属性和客户端 Bean 装配，作为后续所有真实操作子任务的统一入口。

## 实施步骤

1. 在 identity-access 服务模块引入 Keycloak Admin Client SDK 依赖（版本与目标 Keycloak Server 一致，需支持 Organizations API）。
2. 定义 `KeycloakAdminProperties` 配置类，绑定 server URL、realm（默认 `cdp`）、admin client id、连接与读取超时。
3. 定义 `KeycloakAdminClientConfiguration`，装配 `Keycloak` Bean；凭证通过注入的 `SecretStorePort` 抽象获取。
4. 以条件装配（如 `@ConditionalOnProperty`）确保真实 adapter Bean 只在显式开启时加载，避免破坏 Fake Adapter profile。
5. 提供 `application-keycloak-real.yml` 范例配置，说明 server URL、realm 与超时占位值。

## 验收标准

- 引入 Keycloak Admin Client SDK 并兼容 Organizations API
- 配置属性类绑定 serverUrl、realm、adminClientId、超时
- 提供可注入的 Keycloak Admin Client Bean
- 装配代码无 `@Transactional`，保持远程调用与本地事务隔离
- 提供条件装配 + profile 范例配置

## 技术约束

- 远程调用必须与本地 PostgreSQL 事务完全分离
- SDK 版本必须支持 Organizations API
- 凭证只能通过 `SecretStorePort` 间接获取，不得硬编码或写入日志

## 代码模式

- Ports and Adapters：真实 Keycloak Adapter 位于基础设施层，向应用层暴露 `KeycloakAdminPort`## Details

**Scope**: Keycloak Admin Client SDK 依赖、配置属性、Bean 装配、条件装配与 profile 配置

**Out of Scope**: 具体 ensureOrganization/User/Role/Membership 操作的实现；SecretStore 真实 Vault 接入（由 SecretStore 任务处理）；Fake Adapter（已存在）

**Constraints**: 远程 Keycloak 调用与本地事务严格分离, SDK 版本兼容 Organizations API, 凭证仅通过 SecretStorePort 获取

## Acceptance Criteria

- [ ] 引入 Keycloak Admin Client SDK 依赖并锁定版本，与目标 Keycloak Server 版本兼容（支持 Organizations API）
- [ ] 提供可绑定的配置属性类，包含 serverUrl、realm（默认 `cdp`）、adminClientId、连接/读取超时
- [ ] 提供 Spring Configuration Bean 装配一个可注入的 Keycloak Admin Client 实例，凭证通过 `SecretStorePort` 抽象获取
- [ ] Keycloak Admin Client 装配代码不出现任何 `@Transactional` 注解，明确隔离本地事务
- [ ] 提供本地配置 profile（如 `application-keycloak-real.yml`）作为真实 adapter 启用范例，并通过条件装配避免影响 Fake Adapter profile

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

