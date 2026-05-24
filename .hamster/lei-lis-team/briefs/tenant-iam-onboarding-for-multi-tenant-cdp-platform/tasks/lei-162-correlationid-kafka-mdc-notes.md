---
id: "d7ad8373-1dd3-45ff-90e9-21161f86c21b"
entity_type: "task"
entity_id: "061dc56b-a3fe-4ba0-8e33-5ed25ff5661d"
title: "CorrelationId 在 Kafka 消费/发布与日志 MDC 中端到端贯穿 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-162"
parent_task_id: "3f23a260-b3b2-4b13-bba0-38e8f6f01035"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:28.154391+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 `correlationId` 在事件驱动 onboarding 链路上的端到端贯穿（Consumer → MDC → 应用服务 → Publisher → Kafka Header → 日志）。

## Implementation Approach

1. 在 Kafka Consumer 入口处：

- 提取入站事件的 `correlationId`（缺失则生成 UUID 并记录"auto-generated"标记）
- 调用 `MDC.put("correlationId", ...)` 与 `MDC.put("tenantId", ...)`
- 在 `finally` 中调用 `MDC.clear()` 防止线程池泄漏

1. 在 Kafka Publisher 出口处：

- 把 `correlationId` 写入 Kafka `ProducerRecord` Header（key: `X-Correlation-Id`）
- 同时保留在事件 payload 中（双通道保证）

1. 配置 logback/log4j2 pattern 输出 `%X{correlationId}` 和 `%X{tenantId}`。
2. 编写测试验证 MDC 在异常路径、提交 offset 路径、正常路径都被清理。

## Acceptance Criteria

- Consumer 注入与清理 MDC
- 出站事件 correlationId 与入站一致
- Kafka Header 携带 correlationId
- 日志格式包含 correlationId 与 tenantId
- 缺失 correlationId 时自动生成不阻塞处理

## Technical Constraints

- MDC 必须在 finally 中清理，避免线程池场景下串租户
- 不要把 correlationId 当作主键参与去重决策（去重由独立子任务负责）
- 不在领域层使用 SLF4J MDC API（仅在适配器层使用）

## Code Patterns to Follow

- 与可观察性要求一致：日志格式按 `tenantId` 和 `correlationId` 可排查
- MDC 清理模式参考兄弟任务 "可信 Tenant Context 传播链" 的 finally 清理约束## Details

**Scope**: Kafka 事件驱动路径上 correlationId 的提取、MDC 注入与清理、出站 Kafka Header 传递、日志格式配置

**Out of Scope**: Kafka Consumer/Publisher 适配器本身的实现（独立子任务）；HTTP 同步路径的 correlationId；TenantContextHolder/TenantAwareExecutor（兄弟任务 可信 Tenant Context 传播链）；去重策略（独立子任务）

## Acceptance Criteria

- [ ] Consumer 处理事件时 correlationId 和 tenantId 被写入 SLF4J MDC，处理结束后 finally 块清理
- [ ] 应用服务发布的出站事件携带与入站事件相同的 correlationId
- [ ] Kafka Publisher 在出站消息的 Kafka Header 中写入 correlationId
- [ ] 日志输出包含 correlationId 和 tenantId 字段，便于按唯一请求排查
- [ ] 入站事件缺失 correlationId 时会生成新值并记录标记，不会导致消息处理失败

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

