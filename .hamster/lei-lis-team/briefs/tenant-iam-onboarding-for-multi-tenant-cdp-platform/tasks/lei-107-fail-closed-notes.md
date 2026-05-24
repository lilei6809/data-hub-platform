---
id: "3c54ca03-27dd-4d03-a848-a372238293da"
entity_type: "task"
entity_id: "41f2283c-bd69-4ebb-8c36-ca95b696e86f"
title: "实现熔断器与 Fail-Closed 默认行为 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-107"
parent_task_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:16.210974+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在 HTTP 客户端之上引入熔断器，并实现 Fail-Closed 默认行为：PDP 不可用时统一抛出 AuthorizationUnavailableException，绝不默认 ALLOW。

## Implementation Approach

1. 选择并引入熔断库（Resilience4j 推荐），用 CircuitBreaker 包裹 HTTP 调用。
2. 配置熔断属性：failureRateThreshold、minimumNumberOfCalls、slidingWindowSize、waitDurationInOpenState、permittedCallsInHalfOpenState；默认值偏保守。
3. 把以下情况都视为熔断的"失败"：AuthorizationUnavailableException（503/超时/网络错误）；不把 InvalidEvaluationRequestException、AuthenticationException 计入失败。
4. OPEN 状态下的快速失败也以 AuthorizationUnavailableException 暴露。
5. 定义并导出 AuthorizationUnavailableException（runtime exception），文档明确"业务层应映射为 HTTP 503"。
6. 编写单元测试覆盖：连续失败触发 OPEN、OPEN 期间快速失败、半开恢复、客户端错误（400）不计入熔断。

## Acceptance Criteria

- 熔断器正确包裹 evaluate 调用
- 不可用场景统一抛 AuthorizationUnavailableException
- 默认 Fail-Closed，不会降级到 ALLOW
- 熔断参数可配置
- 测试覆盖 OPEN/HALF_OPEN/CLOSED 状态切换

## Technical Constraints

- 不引入重试（MVP 范围之外，且重试可能放大 PDP 故障）
- 熔断状态变更应可被指标系统观察（暴露指标接口/事件，不强制接入特定监控）
- 必须保证 Spring 应用启动后熔断配置生效## Details

**Scope**: 熔断器包裹、熔断配置属性、AuthorizationUnavailableException 定义与语义、Fail-Closed 默认行为、熔断状态指标暴露点（接口层面）

**Out of Scope**: HTTP 请求实现本身、Spring Security 集成、业务服务中的 ControllerAdvice 实现、重试策略（MVP 不引入重试）、缓存（由服务端 Caffeine/Redis 方案负责，不在 SDK 范围）

## Acceptance Criteria

- [ ] AuthorizationClient 的调用被熔断器包裹，达到阈值后进入 OPEN 状态并快速失败
- [ ] 熔断快速失败与传输层失败都被统一映射为 AuthorizationUnavailableException
- [ ] 默认配置为 Fail-Closed：任何不可用场景下 SDK 不会返回 ALLOW
- [ ] 熔断阈值（失败比例、最小调用数、滑动窗口、half-open 试探）可通过配置调整
- [ ] 测试覆盖：底层连续 503 后熔断打开、OPEN 状态快速失败、half-open 恢复
- [ ] 提供 AuthorizationUnavailableException 类型，供业务服务映射为 HTTP 503

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

