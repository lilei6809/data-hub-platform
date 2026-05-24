---
id: "1b86fdb5-85cc-4f35-9f2a-4042fae05fcc"
entity_type: "task"
entity_id: "53f74f12-1692-480a-93b0-344db36aad3c"
title: "实现 EventPublisher 的 Kafka Adapter 发布 IAM 成功与失败事件 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-93"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:32.143897+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 `EventPublisher` 抽象的 Kafka Adapter，发布 `TenantIamProvisionedEvent` 与 `TenantIamProvisioningFailedEvent`。

## 实现方式

1. 在 infrastructure 层新增 Kafka Producer 配置（bootstrap、acks=all、retries、idempotence）。
2. 实现 `EventPublisher` 接口的 Kafka 版本，负责把领域事件序列化并发布到对应主题。
3. 提供主题命名约定与 key 选取（如以 `tenantId` 作为 key，保证同租户事件顺序）。
4. 通过 Spring/依赖注入按 Profile 或配置切换内存实现与 Kafka 实现，应用核心保持只依赖 `EventPublisher`。
5. 编写集成测试（如 Embedded Kafka 或 Testcontainers）验证事件 payload、主题和 key 满足契约。

## 验收标准

- Kafka 实现可发布成功与失败事件
- 应用核心不依赖 Kafka 类型
- Producer 配置满足可靠投递语义
- 事件 payload 不包含敏感信息
- 拥有集成测试覆盖

## 范围

### 包含

- EventPublisher 的 Kafka 实现、序列化与 Producer 配置

### 不包含

- 事件契约与 EventPublisher 接口本身（兄弟任务）
- 内存或 no-op EventPublisher 实现（兄弟任务）
- Provisioning 流程与状态机（兄弟任务）## Details

**Scope**: EventPublisher 的 Kafka 实现，发布 `TenantIamProvisionedEvent` 与 `TenantIamProvisioningFailedEvent`，包含序列化与 Producer 配置。

**Out of Scope**: EventPublisher 接口定义、内存实现、事件 schema 本身均由兄弟任务 "Tenant IAM 事件边界契约" 负责；状态机与失败记录由兄弟任务负责。

**Constraints**: 应用核心只能依赖 EventPublisher 接口，不能出现 Kafka 类型, Producer 配置需使用 acks=all 与适当重试策略以避免静默丢事件, 不允许在事件 payload 中输出 secret、临时密码或敏感凭证

## Acceptance Criteria

- [ ] 提供 `EventPublisher` 的 Kafka 实现，能发布 `TenantIamProvisionedEvent` 与 `TenantIamProvisioningFailedEvent`
- [ ] 事件发布使用明确的主题与序列化格式，且与 Consumer 契约一致
- [ ] Producer 配置设置为 `acks=all`，启用合理的重试与超时参数
- [ ] 应用核心不出现任何 Kafka 类型引用，Adapter 可被内存实现替换
- [ ] 提供集成测试验证事件可被 Embedded Kafka 或 Mock Producer 接收到且 payload 符合领域契约

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

