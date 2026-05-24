---
id: "72eeabbd-0e4d-4de9-9fb9-7e22ace210bd"
entity_type: "task"
entity_id: "20d14149-4b55-4c8a-87b3-9fbddbf87287"
title: "配置 Keycloak Admin Client 与连接管理 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-89"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:09.555529+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为真实 Keycloak Admin API Adapter 建立 Admin Client 装配、Service Account 认证、Token 缓存、HTTP 超时与连接池等连接基础设施。

## Implementation Approach

1. 引入 Keycloak Admin Client 依赖（`keycloak-admin-client` 或同等 HTTP 客户端封装）。
2. 通过 Spring 配置类装配 `KeycloakAdminClient`：从配置读取 base URL、realm、service account client id；从 `SecretStorePort` 拉取 client secret。
3. 实现 Service Account token 管理：按 `expires_in` 缓存 access token，过期前主动刷新；token 拉取失败抛出基础设施异常。
4. 配置 HTTP 层连接超时、读取超时、连接池最大连接数与 keep-alive。
5. 在日志拦截器层屏蔽 Authorization Header、secret、临时密码等敏感字段。
6. 提供可选的启动期健康检查（如 ping realm endpoint）。

## Acceptance Criteria

- Keycloak Admin Client 通过配置与 SecretStorePort 完成装配
- Service Account token 在内存中按 `expires_in` 缓存并主动刷新；拉取失败抛出可识别异常
- HTTP 调用配置超时与连接池
- 日志中不出现 secret、access token、临时密码
- 启动期可执行健康检查并清晰报错

## Technical Constraints

- 凭证只能来自 `SecretStorePort`，禁止硬编码或直接读取环境变量
- Admin Client 必须线程安全，可被多个并发 Step 执行复用
- 不允许把 Keycloak Admin Client 类型泄漏到 Port 接口或领域层

## Code Patterns to Follow

- Ports and Adapters：本组件属于 infrastructure 层，仅服务于真实 Keycloak Adapter
- 配置与凭证解耦：配置走 Spring config，凭证走 SecretStorePort

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: Keycloak Admin Client 的装配、认证、Token 管理、HTTP 超时与连接池基础配置，以及与 SecretStorePort 的对接

**Out of Scope**: SecretStorePort 接口与 Vault 实现（sibling 84d4e472 owns）；Port 接口定义与 Fake Adapter（sibling 9b66bd20 owns）；Kafka 与事件发布（sibling 3f23a260 owns）；任何 Organization/User/Role 业务操作（由后续子任务实现）

## Acceptance Criteria

- [ ] Keycloak Admin Client 通过配置（base URL、realm、service account client id）和 SecretStorePort 提供的 client secret 完成装配
- [ ] Service Account token 在内存中按 expires_in 缓存并在过期前刷新；token 拉取失败抛出可识别的基础设施异常
- [ ] HTTP 调用层配置连接超时、读取超时、连接池上限，超时异常可被后续异常映射子任务捕获
- [ ] 日志输出不包含 client secret、access token、临时密码等敏感字段
- [ ] 该组件可在 Spring Boot 启动期完成健康检查（可选 ping Keycloak），失败时启动日志清晰可定位

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |
| skillReferences | Hamster Blueprint |

