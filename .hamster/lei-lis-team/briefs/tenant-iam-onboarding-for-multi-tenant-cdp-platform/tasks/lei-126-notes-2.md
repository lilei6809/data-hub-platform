---
id: "374cd6b0-6ddb-49dd-b831-d304627e8d11"
entity_type: "task"
entity_id: "171f5973-69da-4ff2-b87a-9986294b6b26"
title: "实现授权决策可观察性与安全日志规范 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-126"
parent_task_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:25.463052+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为 Thin Client SDK 实现授权决策的结构化日志与最小指标输出，符合 PRD 的可观察性与日志安全要求。

## Implementation Approach

1. 在 AuthorizationClient/Enforcer 内部引入装饰器，记录每次 evaluate 调用的开始/结束时间。
2. 输出结构化日志字段：tenantId、subjectId、permission、resourceType、resourceId、decision、decisionId、denyReason、latencyMs、circuitState。
3. 显式禁止把 Resource.attributes 打入日志；只允许 resourceType + resourceId。
4. AuthorizationUnavailableException → WARN/ERROR 级别，附带 circuitState 与 cause。
5. 通过 MDC 把 correlationId（若存在）注入日志上下文。
6. 通过条件装配引入可选 Micrometer MeterRegistry：authorization.evaluate.count{decision}、authorization.evaluate.latency、authorization.circuit.state。
7. 单元测试验证敏感字段不被输出、AuthorizationUnavailableException 走告警级别。

## Acceptance Criteria

- 每次调用输出结构化决策日志，字段齐全
- 资源属性全文不进入日志
- Fail-Closed 走告警级别
- 日志携带 correlationId
- 提供 Micrometer 计数与计时指标
- 单元测试验证敏感字段过滤

## Technical Constraints

- 日志库使用 SLF4J；不绑定具体后端
- Micrometer 装配必须为可选，避免 SDK 强依赖
- decisionId 是必须字段；如服务端未返回则记录占位符并 WARN## Details

**Scope**: SDK 内部決策结果结构化日志、Fail-Closed 告警级别日志、敏感字段过滤、简单指标接口（调用计数、延迟、熔断状态）

**Out of Scope**: 指标后端接入（Prometheus/Grafana）、告警规则、审计事件存储（审计 BC 负责）、服务端决策日志（由 a83402a7 处理）

## Acceptance Criteria

- [ ] 每次 evaluate 调用输出一条结构化日志，包含 tenantId、subjectId、permission、resourceType、resourceId、decision、decisionId、denyReason、latencyMs
- [ ] Resource.attributes 全文不被输出到日志
- [ ] AuthorizationUnavailableException 以 WARN/ERROR 级别记录，与 ALLOW/DENY 日志可区分
- [ ] 日志携带 correlationId（如果上下文中可取）
- [ ] 提供简单计数/计时接口（Micrometer MeterRegistry 可选装配）记录決策计数与延迟
- [ ] 单元测试验证敏感字段过滤与 Fail-Closed 日志级别

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

