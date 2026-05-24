---
id: "8965baf0-9b33-43c2-b8d6-c3b3b96605e3"
entity_type: "task"
entity_id: "c5ac7ac5-0ef1-4b83-973f-ed705f3cb571"
title: "实现 In-Memory EventPublisher Adapter 以支持 MVP 验证 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-165"
parent_task_id: "3fed74d7-7fa9-40f7-9015-70ea177ecf46"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:48.398722+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 `EventPublisher` 的内存 Adapter，按顺序记录已发布事件并提供查询/重置/失败模拟能力，支撑 MVP 阶段对事件边界的验证。

## 实现思路

1. 在测试支持包（或 application/adapter 包下标注为 in-memory）创建 `InMemoryEventPublisher`，实现 `EventPublisher` 接口。
2. 内部使用线程安全集合（如 `CopyOnWriteArrayList` 或 `Collections.synchronizedList`）按 publish 顺序保存事件。
3. 暴露查询 API：`publishedEvents()`、`eventsOfType(Class<T>)`、`eventsForTenant(TenantId)`，便于测试断言。
4. 提供 `reset()` / `clear()` 以在测试之间隔离状态。
5. 提供 `failNextPublishWith(EventPublishException)` 或 `setFailureMode(...)`，使 Provisioning Service / Step 兄弟任务可以验证发布失败路径。
6. 编写单元测试覆盖：基本发布与顺序、按类型查询、按 tenantId 查询、reset、失败注入。
7. 在类 KDoc 中明确：仅用于 MVP 与测试，不可用于生产；Phase 3 由 Kafka 适配器替换。

## 验收标准

- 实现接口并线程安全记录事件
- 支持按类型/租户/全部查询
- 支持 reset 与失败注入
- 单元测试覆盖以上行为

## 技术约束

- 仅依赖 EventPublisher 接口与领域事件类型
- 不引入 Kafka、Spring 容器依赖
- 必须线程安全

## 范围说明

- 包含：InMemoryEventPublisher 实现及其单元测试
- 不包含：在 Provisioning Service / Step 中调用 publisher 的编排逻辑（属于 Provisioning Service 与 Step Pipeline 兄弟任务）、Kafka 适配器、Outbox## Details

**Scope**: InMemoryEventPublisher 实现、按顺序记录事件、线程安全、可查询接口、可重置、可模拟失败、自身单元测试

**Out of Scope**: Kafka 适配器（Phase 3 兄弟任务）、Outbox 适配器（Authorization BC 兄弟任务）、事件类型定义、Provisioning Service 编排与 Step 发布调用（兄弟任务）

## Acceptance Criteria

- [ ] InMemoryEventPublisher 实现 EventPublisher 接口，按 publish 顺序保存所有事件
- [ ] 提供查询 API，可取出全部事件、按类型过滤、根据 tenantId 过滤
- [ ] 提供 reset/clear 机制以支持测试间隔离
- [ ] 提供可配置失败模式（下一次 publish 抛 EventPublishException）以支持验证调用方对发布失败的处理
- [ ] 实现为线程安全（使用同步列表或并发集合）
- [ ] 自身单元测试覆盖发布、查询、重置、失败模拟四种行为

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

