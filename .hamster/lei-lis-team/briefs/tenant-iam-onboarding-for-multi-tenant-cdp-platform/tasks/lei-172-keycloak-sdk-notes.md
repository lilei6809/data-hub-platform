---
id: "1b3d1d39-08b9-4f5a-bcf6-9862a9e35687"
entity_type: "task"
entity_id: "bcad0cba-9df8-4c26-8337-47caff79d5ec"
title: "Keycloak 异常映射层：将 SDK 异常转译为领域错误 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-172"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:40.64046+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

为真实 Keycloak Adapter 建立统一异常映射层，将 SDK 异常转译为带 retryable 语义的领域错误，供上层 Step Pipeline 做重试决策。

## 实施步骤

1. 定义领域异常类层次：`KeycloakAdapterException` 基类，含 `failureCode`、`retryable` 字段；子类 `KeycloakResourceNotFoundException`、`KeycloakClientException`、`KeycloakAuthenticationException`、`KeycloakTransientException`。
2. 实现映射函数，按 HTTP 状态码与异常类型分类：

- 401/403 → Authentication（不可重试）
- 404 → ResourceNotFound
- 其它 4xx（不含 409，409 在 ensure 内部处理）→ Client（不可重试）
- 5xx、SocketTimeoutException、ConnectException、IOException → Transient（可重试）

1. 选择拦截机制（AOP advice / 装饰器 / 显式 helper），让所有真实 adapter 方法都经过映射通道。
2. 严格控制异常 message：仅记录通用错误描述、HTTP 状态、Keycloak 对象类型与脱敏后的关键标识；不写入临时密码、secret、用户全字段。
3. 编写单元测试覆盖映射矩阵与日志安全断言。

## 验收标准

- 领域异常层次与 retryable 语义到位
- 映射函数覆盖 4xx/5xx/网络异常
- 所有真实 adapter 调用走统一映射通道
- 单元测试覆盖映射矩阵与敏感字段断言

## 技术约束

- 保留原始异常 cause
- 异常 message 不含密码、secret 或敏感属性
- retryable 标志驱动上层重试

## 代码模式

- ensure 步骤模式中 409 由方法内部处理，不进入映射层## Details

**Scope**: 领域异常类定义、SDK 异常到领域异常的映射规则与统一拦截机制

**Out of Scope**: Step Pipeline 重试调度（在 Step Pipeline 兄弟任务）；Provisioning Service 状态机错误记录（在 Provisioning Service 兄弟任务）；Event 发布 failure 事件

**Constraints**: 异常消息不得包含临时密码、secret、敏感用户属性, 保留原始异常作为 cause 以便排查, retryable 语义必须清晰，Transient 可重试，Client/Authentication 不可重试

## Acceptance Criteria

- [ ] 定义领域异常层次：`KeycloakAdapterException` 基类与子类 `KeycloakResourceNotFoundException`、`KeycloakClientException`（不可重试）、`KeycloakAuthenticationException`（不可重试）、`KeycloakTransientException`（可重试），每类包含 retryable 标志与 failureCode
- [ ] 提供统一映射函数，覆盖：4xx（除 409/404）→Client；401/403→Authentication；404→ResourceNotFound；5xx 与 SocketTimeout/ConnectTimeout/IOException→Transient
- [ ] 所有真实 adapter 方法的外部调用都走该映射通道（可通过 AOP、装饰器、或统一 helper 实现），不出现裸抠的 Keycloak SDK 异常向上报
- [ ] 单元测试覆盖各种 HTTP 状态与网络异常的映射路径，包含验证原始异常被保留作为 cause
- [ ] 测试断言映射后异常 message 中不出现临时密码与 secret

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

