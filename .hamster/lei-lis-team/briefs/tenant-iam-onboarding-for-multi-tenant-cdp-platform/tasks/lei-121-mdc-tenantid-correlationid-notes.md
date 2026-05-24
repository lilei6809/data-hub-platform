---
id: "c2047f40-435d-474c-a48c-5b70c63ea600"
entity_type: "task"
entity_id: "7b0a23e4-7aed-402e-8da0-7ce2bad1173b"
title: "集成 MDC 日志注入 tenantId 与 correlationId - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-121"
parent_task_id: "343cab7a-8d53-4549-8dc5-4f4f15361496"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:56.747955+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

将 tenantId 与 correlationId 注入 SLF4J MDC，使所有业务日志可按租户与请求链追踪，并保证同步/异步路径都正确管理 MDC 生命周期。

## 实施步骤

1. 定义 `LoggingContextKeys` 常量类（tenantId、correlationId）。
2. 在 TenantContextFilter 成功构造上下文后写入 MDC；correlationId 优先取 `X-Correlation-ID`，缺失时使用 `UUID.randomUUID()` 并写入响应头。
3. 在 Filter 的 finally 块中清理 MDC 键（避免误调 MDC.clear 影响外部框架键）。
4. 扩展 TenantAwareExecutor：捕获 `MDC.getCopyOfContextMap()` 快照，执行线程中 `MDC.setContextMap`，finally 中 `MDC.clear()`（任务线程级安全清理）。
5. 编写测试使用 logback ListAppender 验证日志事件 MDC 内容。

## 验收标准

- Filter 写入并清理 MDC 中的 tenantId 与 correlationId
- correlationId 缺失时生成 UUID 并回写响应头
- TenantAwareExecutor 正确传递与清理 MDC 快照
- 集成测试断言业务日志含 tenantId/correlationId
- MDC 键名常量化

## 技术约束

- 不在日志中输出 secret、临时密码或 JWT 原文
- MDC 写入/清理与 TenantContextHolder 生命周期严格对齐

## 范围

- 包含：MDC 键定义、Filter MDC 注入与清理、Executor MDC 快照、日志测试
- 不包含：logback 配置文件改动、审计日志、集中日志接入## Details

**Scope**: MDC 键名约定、Filter 中的 MDC 写入/清理、TenantAwareExecutor 的 MDC 快照传递、correlationId 生成策略

**Out of Scope**: 日志格式模板配置（logback.xml）上线变更、集中日志平台接入、审计日志 BC

**Constraints**: MDC 键名使用常量类集中管理（如 tenantId、correlationId）, 禁止在日志中输出 secret、临时密码或 JWT 原文, MDC 必须与 TenantContextHolder 同起同落，请求/任务结束 finally 中清理

## Acceptance Criteria

- [ ] TenantContextFilter 在成功构造上下文后写入 MDC：tenantId 来自 X-Tenant-ID，correlationId 优先取 X-Correlation-ID 否则生成 UUID，并在响应头回写 X-Correlation-ID
- [ ] 请求结束（含异常）后 MDC 中 tenantId 与 correlationId 被移除，连续两个不同租户请求不会在日志中出现上一请求的键值
- [ ] TenantAwareExecutor 提交任务时捕获 MDC 快照（MDC.getCopyOfContextMap），在执行线程设置后于 finally 调用 MDC.clear
- [ ] 提供集成测试（可使用 ListAppender 或类似机制）验证业务层记录的日志事件携带正确的 tenantId 与 correlationId
- [ ] MDC 键名与默认值有常量类统一定义，避免在多处硬编码

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |

