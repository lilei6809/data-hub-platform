---
id: "bb73b7d3-bdb9-45da-9a0f-8d1b7191d453"
entity_type: "task"
entity_id: "be1b961e-e50c-4877-9355-0ff5dffab557"
title: "在 Kafka 事件链路贯通 correlationId 与可观察性 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-114"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:38.395542+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

让 `correlationId` 在 Kafka 入站 → provisioning service → Kafka 出站全链路贯通，并保证日志可观察、敏感字段不外泄。

## 实现方式

1. 从入站 `TenantInfrastructureProvisionedEvent` 的 payload 字段或 Kafka header 中提取 correlationId，缺失时按约定策略生成或拒绝。
2. 在 Consumer 入口将 `tenantId`、`correlationId` 写入 MDC，并在 finally 中清理。
3. 调用 `TenantIamProvisioningService.provisionTenantIam(desiredState, correlationId)` 时传递同一个 correlationId。
4. 在 Kafka EventPublisher Adapter 中将 correlationId 写入出站事件 payload 与 Kafka header（如 `x-correlation-id`）。
5. 审查日志输出，确保失败信息不会包含 secret、临时密码或外部 IdP 凭证。

## 验收标准

- correlationId 从入站事件贯通到出站事件
- Consumer 与 Producer 日志包含 tenantId 与 correlationId
- 日志中不出现敏感凭证
- MDC 在请求处理结束时被清理

## 范围

### 包含

- Kafka 事件链路上的 correlationId 与 MDC 日志贯通

### 不包含

- 引入新的分布式追踪基础设施
- TenantContextFilter 与 Spring Security 上下文传播（兄弟任务）## Details

**Scope**: Kafka 入站/出站事件上的 correlationId 提取与传递、MDC 日志上下文、敏感字段过滤。

**Out of Scope**: 新增距离追踪系统（如 OpenTelemetry）；Tenant Context Filter 与 Spring Security 上下文传播由 "可信 Tenant Context 传播链" 兄弟任务负责。

**Constraints**: correlationId 必须从入站贯穿到出站事件，不能在中间步骤丢失, 日志中不允许出现 Keycloak service account secret、初始密码或 IdP secret, MDC 上下文必须在 finally 中清理，避免线程池重用造成床染

## Acceptance Criteria

- [ ] Consumer 能从入站事件 payload 或 Kafka header 中提取 correlationId 并写入 MDC
- [ ] `TenantIamProvisioningService.provisionTenantIam(...)` 调用携带与入站一致的 correlationId
- [ ] 发布的 `TenantIamProvisionedEvent` 与 `TenantIamProvisioningFailedEvent` 在 payload 与 Kafka header 中携带同一 correlationId
- [ ] Consumer 与 Producer 路径的日志同时包含 `tenantId` 和 `correlationId`
- [ ] 失败场景下日志不包含 secret、临时密码或 IdP 凭证
- [ ] MDC 上下文在消息处理结束后被清理，有测试或机制证明

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

