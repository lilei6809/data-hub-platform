---
id: "b14fea85-dbbb-4840-8ac9-f4f28d5f1fa8"
entity_type: "task"
entity_id: "31100ba2-1f6e-4cb1-9c86-eb586039ee64"
title: "定义 EventPublisher Port 抽象隔离事件基础设施 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-158"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:16.816057+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

定义 `EventPublisher` Port 抽象，作为 IAM 领域事件发布的稳定边界，隔离 Kafka 等基础设施细节。

## 实现思路

1. 在应用层（或领域层 outbound port 包）创建 `EventPublisher` 接口。
2. 方法签名以意图为中心：`publish(TenantIamProvisionedEvent)` 与 `publish(TenantIamProvisioningFailedEvent)`；或定义一个 sealed `IamDomainEvent` 类型作为参数。
3. 明确失败语义：发布失败时抛出 `EventPublishException`（领域化异常），调用方决定如何处理（重试 / 标记状态）。
4. 在接口 KDoc 中明确：不保证与本地数据库事务的原子性；远程发布与本地状态写入是两次独立动作（与 brief Risk 3 一致，未来引入 Outbox 时再变更）。
5. 不在接口上暴露 topic、headers、序列化、partition key 等基础设施概念。

## 验收标准

- 接口签名以领域事件为参数，无基础设施类型泄漏
- 失败语义清晰（异常或 Result 二选一并文档化）
- KDoc 描述事务/原子性边界与未来 Outbox 演进点
- 接口可被注入到 Provisioning Service 与 Step 中

## 技术约束

- 不引入 Kafka、Spring Kafka、Jackson 依赖
- 接口必须可被简单测试替身实现

## 范围说明

- 包含：Port 接口定义、失败语义、KDoc
- 不包含：任何具体实现、Provisioning Service 编排、Step 实现## Details

**Scope**: EventPublisher Port 接口定义、入参语义、异常/失败语义、接口级 KDoc

**Out of Scope**: 内存适配器实现（独立子任务）、Kafka 适配器（Phase 3 兄弟任务）、Outbox 实现（Authorization BC 兄弟任务）、事件类型定义本身（前两个子任务）、Provisioning Service 中调用该 Port 的编排逻辑（Provisioning Service 兄弟任务）

## Acceptance Criteria

- [ ] EventPublisher 接口提供 publish(TenantIamProvisionedEvent) 与 publish(TenantIamProvisioningFailedEvent)（或统一的 publish(DomainEvent) 重载）
- [ ] 接口位于领域/应用层，不包含任何 Kafka、topic、partition、序列化或事务参数
- [ ] 接口 KDoc 明确说明：调用者不应在本地数据库事务内依赖发布原子性；失败语义明确（抛出领域化异常或返回结果）
- [ ] 接口可被 Provisioning Service 与 Step 实现依赖注入，且不引入循环依赖

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

