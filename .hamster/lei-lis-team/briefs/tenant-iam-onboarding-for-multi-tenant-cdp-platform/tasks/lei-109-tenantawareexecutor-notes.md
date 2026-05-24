---
id: "a4e29da6-5776-4427-a48f-0fb06b60c6d1"
entity_type: "task"
entity_id: "f84fa9e0-2dc9-40f2-a170-20b446113cdd"
title: "实现 TenantAwareExecutor 在异步路径恢复租户与安全上下文 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-109"
parent_task_id: "343cab7a-8d53-4549-8dc5-4f4f15361496"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:18.461143+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

提供 TenantAwareExecutor 装饰器，在异步与线程池场景下安全捕获与恢复 TenantContext 和 SecurityContext，并强制清理避免串租户。

## 实施步骤

1. 实现 `TenantAwareExecutor implements Executor`（以及一个 `TenantAwareExecutorService` 变体），构造时包装 delegate Executor。
2. 在 `execute(Runnable)` 内：捕获 `TenantContextHolder.get()` 与 `SecurityContextHolder.getContext()` 快照（缺失 TenantContext 抛 MissingTenantContextException）。
3. 提交一个包装 Runnable，在执行线程中：set 上下文 → try { run } finally { clear 两类 Holder }。
4. 实现 `TenantAwareCallable<T>` 同样逻辑。
5. 提供 `AsyncConfig implements AsyncConfigurer`，用 TenantAwareExecutor 装饰底层 ThreadPoolTaskExecutor。
6. 编写并发测试：两个租户上下文交替提交 100+ 任务到同一线程池，断言无串扰且最后两类 Holder 为空。

## 验收标准

- 任意 Executor 可被包装并在执行线程恢复双重上下文
- finally 清理保证线程池复用不串租户
- @Async 默认走 TenantAwareExecutor
- 并发测试覆盖多租户混合提交
- 文档说明禁用 InheritableThreadLocal 的原因

## 技术约束

- finally 必清理两类 Holder
- 禁止依赖 InheritableThreadLocal 模式
- 兼容 Platform 与 Virtual Threads

## 范围

- 包含：Executor/Runnable/Callable 装饰器、AsyncConfigurer 接入、并发测试
- 不包含：Reactor Context 传播、Kafka Consumer 上下文构造、远程调用 Header 透传## Details

**Scope**: TenantAwareExecutor / TenantAwareRunnable / TenantAwareCallable 装饰器、AsyncConfigurer 接入、上下文快照与清理、并发测试

**Out of Scope**: Reactive Context（Mono/Flux）传播、Kafka 消费者上下文传播、距离调用的 Header 透传

**Constraints**: 必须在执行线程中 finally 清理 TenantContextHolder 与 SecurityContextHolder, 使用 SecurityContextHolder 的 MODE_INHERITABLETHREADLOCAL 是禁止选项（易在线程池下泄露），应显式快照传递, 需兼容 Java Platform Threads 与 Virtual Threads

## Acceptance Criteria

- [ ] 提供 TenantAwareExecutor 包装任意 Executor，提交 Runnable/Callable 时捕获调用线程的 TenantContext 和 SecurityContext 并在执行线程恢复
- [ ] 任务执行完成后（含异常路径）执行线程的 TenantContextHolder 与 SecurityContextHolder 被清理，下一个调度任务不会看到上一租户上下文
- [ ] 提供默认 AsyncConfigurer，使 Spring @Async 默认使用 TenantAwareExecutor
- [ ] 并发测试（至少 2 个租户 x N 个任务）验证执行线程池复用时不会串租户，且在提交时缺失 TenantContext 会抛出 MissingTenantContextException
- [ ] 文档说明禁用 SecurityContextHolder MODE_INHERITABLETHREADLOCAL 的原因及推荐使用 TenantAwareExecutor 的开发指引

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

