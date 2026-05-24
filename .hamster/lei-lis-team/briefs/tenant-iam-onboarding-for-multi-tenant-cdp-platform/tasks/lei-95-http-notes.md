---
id: "47d53774-fed4-436b-8dbf-7d7eafa8f737"
entity_type: "task"
entity_id: "fbe358a0-67fd-4778-93eb-7b3667bc6f16"
title: "实现 HTTP 传输层并配置连接/读取/总调用超时 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-95"
parent_task_id: "c6b147e7-18ad-41f5-878e-43f611b1cf31"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:38.834714+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 Thin Client SDK 的 HTTP 传输适配，调用 authorization-service 的 evaluate 端点，并显式配置超时与状态码映射。

## Implementation Approach

1. 实现 AuthorizationClient 的 HTTP 适配类（HttpAuthorizationClient），使用 Spring Boot 内建 HTTP 客户端发起 POST 请求。
2. 通过 ConfigurationProperties 暴露 `authorization.client.endpoint`、`connect-timeout`、`read-timeout`、`call-timeout` 等属性，并设置安全默认值。
3. 实现请求体序列化与响应体反序列化（200 → AuthorizationDecision）。
4. 实现 HTTP 状态码映射：

- 200 → AuthorizationDecision
- 400 → InvalidEvaluationRequestException
- 401 → AuthorizationClientAuthenticationException
- 503 / IO 异常 / 超时 → AuthorizationUnavailableException

1. 编写基于 MockWebServer 的集成测试覆盖每种状态码与超时。

## Acceptance Criteria

- HTTP 实现能成功调用 evaluate 并返回决策
- 三类超时均可配置且具备默认值
- 200 ALLOW/DENY 都走成功路径
- 400/401/503/超时被映射为对应领域异常
- 集成测试覆盖所有映射场景

## Technical Constraints

- 不在 HTTP 层做重试，重试由熔断/Fail-Closed 子任务决定（MVP 不重试）
- HTTP 客户端必须可注入，便于测试替身
- 序列化字段命名必须与 PDP 服务端一致## Details

**Scope**: HTTP 客户端适配器实现、JSON 序列化、超时配置（连接/读取/调用）、HTTP 状态码到领域异常的映射、客户端配置属性（endpoint、timeout）

**Out of Scope**: 熔断器与 Fail-Closed 策略（下一个子任务）、Spring Security 集成、客户端间身份凭证（mTLS 等）的具体实现、authorization-service 服务端实现

## Acceptance Criteria

- [ ] AuthorizationClient 的 HTTP 实现能调用 POST /api/v1/authorization/evaluate 并返回 AuthorizationDecision
- [ ] 连接超时、读取超时和单次调用总超时可通过配置属性调整，均有安全默认值
- [ ] 200 响应被反序列化为 AuthorizationDecision，ALLOW 与 DENY 都走正常返回路径
- [ ] 400 映射为客户端请求错误异常，401 映射为服务间认证异常，503/超时/网络错误映射为 AuthorizationUnavailableException
- [ ] 集成测试（使用 MockWebServer 或等价手段）覆盖 200/400/401/503 与超时场景

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 5 |

