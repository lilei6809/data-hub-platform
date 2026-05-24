---
id: "c5582c7a-743b-4ba9-b245-a8b3b4cf6ad1"
entity_type: "task"
entity_id: "76f55251-a3fd-4478-bdb8-9714667d9f47"
title: "实现 In-Memory EventPublisher 适配器 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-111"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:20.329618+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 `EventPublisher` 的内存适配器 `InMemoryEventPublisher`，为 MVP 提供事件发布能力，并支持测试观察与失败模拟。

## Implementation Approach

1. 在 infrastructure 或 testing support 模块创建 `InMemoryEventPublisher`，实现 `EventPublisher` 接口。
2. 内部维护线程安全的 `List<DomainEvent>` 记录发布顺序。
3. 实现 `publish(event)`：先检查失败模拟开关，开关激活则抛 `EventPublishException`；否则将事件加入列表。
4. 暴露测试访问方法：`publishedEvents()`、`publishedEventsOfType(Class)`、`clear()`、`failNextPublish()` / `failPublishesCount(int)`。
5. 编写单元测试验证：顺序保留、按类型过滤、线程安全、失败开关行为、清空行为。

## Acceptance Criteria

- 适配器实现 EventPublisher 接口
- 提供按类型查询与清空的测试辅助方法
- 提供可配置的失败模拟开关
- 线程安全
- 单元测试覆盖成功、失败、并发与清空场景

## Technical Constraints

- 不得在 publish 路径做任何 I/O 或序列化
- 不得引入消息中间件依赖
- 使用线程安全集合或显式同步

## Code Patterns to Follow

- 与兄弟任务 Fake Keycloak Adapter 在测试可观察性上的风格保持一致

## Scope Boundaries

**In scope**：In-memory 适配器、测试辅助、失败模拟、自身单元测试。

**Out of scope**：Kafka 适配器、Provisioning Service 集成测试、PublishEventStep 实现。## Details

**Scope**: InMemoryEventPublisher 适配器实现、测试访问辅助方法、模拟失败开关、实现层单元测试。

**Out of Scope**: Kafka 适配器（Phase 3 兄弟任务）、PublishTenantIamProvisionedEventStep 实现（Step Pipeline 兄弟任务）、Provisioning Service 状态机与事件发布的集成序列（Service 兄弟任务）、事件类型与 Port 接口本身。

## Acceptance Criteria

- [ ] `InMemoryEventPublisher` 实现 `EventPublisher` 接口，发布的事件按顺序保留于内部集合
- [ ] 提供测试访问方法：读取全部事件、按类型过滤、清空
- [ ] 提供模拟失败能力：可配置下一次或指定次数的 publish 抛出 `EventPublishException`
- [ ] 线程安全：多线程并发发布不丢事件、不抛 ConcurrentModificationException
- [ ] 单元测试覆盖：成功发布可读回；失败开关生效后抛出领域异常；clear() 后集合为空

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

