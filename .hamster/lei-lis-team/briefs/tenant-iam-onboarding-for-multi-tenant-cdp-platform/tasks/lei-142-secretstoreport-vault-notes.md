---
id: "cc082023-2531-4454-a0ea-62b9f5d60a00"
entity_type: "task"
entity_id: "c981c932-bf87-45a2-9761-8adaacfa6297"
title: "定义 SecretStorePort 抽象与内存实现以承接未来 Vault 接入 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-142"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:07.935608+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

定义 SecretStorePort 抽象与内存实现，作为 Keycloak 凭证与未来外部 IdP secret 的统一访问边界，让未来接入 Vault 时不需改动领域代码。

## 实现要点

1. 在领域/应用包中定义 `SecretStorePort` 接口，方法以意图命名（例如 `getSecret(SecretKey key)`），返回封装的 `Secret` 值对象。
2. 定义 `Secret` 值对象：内部持有明文字符串，但 `toString()`、日志序列化、异常消息均输出 `***REDACTED***`。
3. 定义 `SecretKey` 值对象，使用类型化结构（例如 `SecretKey.keycloakAdmin()`、`SecretKey.idpClientSecret(tenantId, idpAlias)`），避免散落的字符串拼接。
4. 提供 `InMemorySecretStore` 实现，支持在测试与本地启动时注册 secret，并按 key 查询。
5. 未知 key 抛出明确的领域错误 `SecretNotFoundException`。

## 验收标准

- SecretStorePort 接口意图型方法清晰，无 Vault/AWS 等具体实现痕迹
- Secret 值对象对所有序列化路径脱敏
- InMemorySecretStore 支持注册和查询，并由单元测试覆盖
- 真实 Adapter 未来引入不需要修改 Port 与上层调用方
- 单元测试覆盖：注册-读取、未知 key 报错、日志序列化脱敏

## 技术约束

- 严格遵守 brief："不允许把 Keycloak service account secret、临时密码或 IdP secret 打印到日志"
- 不在本子任务实现真实 Vault Adapter
- 不接入 Spring `@Value` 直接注入凭证；所有敏感凭证只通过 SecretStorePort 读取

## 范围说明

- **包含**：SecretStorePort 接口、Secret/SecretKey 值对象、InMemorySecretStore、单元测试
- **不包含**：真实 Vault/AWS Secrets Manager Adapter（Phase 4 后续）、Keycloak Admin Adapter 内部如何使用 SecretStorePort（由真实 Adapter 任务负责消费此接口）## Details

**Scope**: SecretStorePort 接口、Secret/SecretKey 值对象、脱敏序列化、InMemorySecretStore 实现、单元测试。

**Out of Scope**: 真实 Vault/AWS Secrets Manager Adapter；Keycloak Admin Adapter 内部消费 SecretStorePort 的具体改造（由真实 Keycloak Adapter 任务承担）；Spring Configuration 与外部启动绑定。

**Constraints**: 凭证不得通过日志或异常消息泄漏明文, Port 接口必须保持基础设施中立，不引入 Vault/AWS SDK 依赖, InMemorySecretStore 仅供本地与测试使用，不得在生产路径中被默认装配

## Acceptance Criteria

- [ ] SecretStorePort 接口在领域/应用层定义，暴露按逻辑名称（如 keycloak-admin-credentials、idp-secret:{tenantId}:{idpAlias}）获取 Secret 的意图型方法
- [ ] Secret 值对象封装敏感字符串，toString、equals、hashCode 与日志序列化均不会暴露明文
- [ ] 提供 InMemorySecretStore 实现，可在测试与本地启动场景下注册和读取凭证
- [ ] 真实/未来 Vault Adapter 的引入不需要改动 SecretStorePort 接口、TenantIamProvisioningService 与 Step Pipeline
- [ ] 对未知 secret key 的查询返回明确的领域错误（如 SecretNotFoundException），不抛出底层基础设施异常
- [ ] 单元测试覆盖：注册-读取、未知 key 报错、日志序列化不泄漏明文

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

