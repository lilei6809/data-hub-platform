---
id: "8905582e-0692-4fbe-b1b3-c7bbec1681da"
entity_type: "task"
entity_id: "d8fbcd6a-48d2-43b2-9944-9a2e3d77044e"
title: "将 Keycloak 异常映射为领域错误 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-125"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:22.301571+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在真实 Keycloak Adapter 内部建立统一的异常 → 领域错误映射层，区分可重试与不可重试，并彻底封装 Keycloak SDK 异常。

## Implementation Approach

1. 定义/复用领域错误类别：`RetryableInfrastructureError`、`PermanentInfrastructureError`、`DomainValidationError`、`ResourceMissingError`（按 Port sibling 已定义的类型为准，缺失则在 Adapter 内补充必需的）。
2. 实现集中的异常映射方法：输入 Keycloak SDK 异常或 HTTP 响应，输出领域错误。
3. 处理矩阵：

- 401/403 → 不可重试凭证/权限错误
- 400 + 校验失败 → 不可重试领域校验错误
- 404 → 查询路径返回 Optional.empty；补丁路径转可重试 ResourceMissing
- 409 → 由 ensure 操作内部回退查询消化，原则上不进入映射层
- 429、5xx、SocketTimeout、ConnectException → 可重试瞬时错误

1. 错误消息脱敏：删除 Authorization、password、secret 字段。
2. 在每个 ensure 操作的 catch 边界统一调用该映射层。

## Acceptance Criteria

- 集中映射组件存在并被全部 ensure 操作复用
- HTTP 状态码 → 领域错误的映射符合需求矩阵
- 错误上下文包含 tenantId/operation，不含敏感字段
- Keycloak SDK 异常不泄漏到 Port 之外

## Technical Constraints

- 映射逻辑必须无副作用、线程安全
- 不得在映射层吞错（必须把分类信息向上传递）

## Code Patterns to Follow

- Ports and Adapters：Adapter 边界负责异常翻译
- Fail-fast on permanent errors, retry on transient errors

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: Keycloak 异常 → 领域错误的集中映射层；可重试/不可重试分类；异常上下文脱敏

**Out of Scope**: Step Pipeline 的重试策略与状态机跳转（sibling 4585bfc9、bba824b0）；各 ensure 操作内部的 409 复用逻辑（前面子任务）；领域错误类型的定义（如已存在于 Port 定义 sibling，本任务只使用；若未定义则在本任务内补充必要的适配器专用错误类型）

## Acceptance Criteria

- [ ] 存在一个集中的异常转换组件，被所有 ensure 操作复用
- [ ] 401/403 被映射为不可重试的凭证/权限错误；400 类校验错误映射为不可重试的领域校验错误
- [ ] 5xx 、连接超时、读写超时被映射为可重试的瞬时基础设施错误
- [ ] 404 在查询路径被表达为“not found”返回语义，不被折叠为异常；在补丁路径被映射为资源被外部删除的可重试错误
- [ ] 领域错误携带 tenantId、operation、Keycloak 错误码/简要描述，但不携带 secret、access token、临时密码
- [ ] Keycloak SDK 的原生异常类型不会被 Port 外部看到（静态检查或架构测试收敛）

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
| skillReferences | Hamster Blueprint |

