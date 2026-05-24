---
id: "2416e3a3-961a-436f-857e-42d3d1c2dfac"
entity_type: "task"
entity_id: "46e92c12-7a5d-4e8f-985c-d9b8bed408c8"
title: "实现 Fake/In-Memory Keycloak Adapter 承载幂等语义 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-147"
parent_task_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:35.006787+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 `KeycloakAdminPort` 的 Fake/In-Memory Adapter，使核心 onboarding 流程在无真实 Keycloak 时可运行并落实幂等语义。

## Implementation Approach

1. 创建 `FakeKeycloakAdminAdapter` 类，实现 `KeycloakAdminPort` 接口。
2. 设计内存存储：

- `Map<TenantId, Organization>` — by tenantId 去重
- `Map<Email, User>` — by email 去重
- `Map<OrganizationId, Set<UserId>>` — membership
- `Set<RoleName>` — realm roles
- `Map<UserId, Set<RoleName>>` — user role assignments
- 使用线程安全集合或显式锁，避免并发测试时数据竞争

1. 对每个 `ensure*` 方法实现"先查后建、关系已存在视为成功、属性不一致按规则校正"的语义。
2. 实现冲突自动消解：模拟内部生成 409 时，自动 fallback 到查询已有对象并返回其 ID。
3. 提供测试钩子方法（不属于 Port 接口）：

- `preload(...)` — 预植入对象以模拟"中途失败后已存在 Organization"场景
- `failNextCallTo(methodName, exceptionType)` — 让下一次调用抛出指定端口级异常
- `snapshot()` — 返回当前内存状态的只读视图供断言使用

1. 提供 Spring Boot 配置类或工厂方法，允许在 test/dev profile 下作为 `KeycloakAdminPort` 的默认实现注入（不强制使用 Spring，保持纯 Java 可构造）。

## Acceptance Criteria

- FakeKeycloakAdminAdapter 完整实现 Port 接口
- 所有 ensure* 方法对重复调用幂等，不产生重复对象或重复关系
- 提供测试钩子：preload、failNextCallTo、snapshot
- 内存存储线程安全
- 日志中不出现 secret、临时密码、凭证

## Technical Constraints

- 不依赖任何 Keycloak SDK 或 HTTP 库
- 不引入真实持久化（无数据库、无文件）
- 必须可在纯 JUnit 上下文中直接 `new` 出来使用

## Code Patterns to Follow

- ensure 语义：先查后建，已存在则复用
- 端口异常映射：Adapter 内部捕获/模拟底层错误，向调用方抛出 Port 抽象异常
- Test Double 风格：Fake（带行为）而非 Mock## Details

**Scope**: FakeKeycloakAdminAdapter 类、内存存储结构（Organization、User、Membership、Role、User-Role）、每个 ensure* 方法的幂等实现、模拟冲突与外部失败的测试钩子、查询已存在对象的辅助方法

**Out of Scope**: KeycloakAdminPort 接口本身的定义（上一个子任务）、真实 Keycloak Admin API Adapter（属于 sibling 6）、Step Pipeline、Provisioning Service、本地状态机、SecretStore、事件发布

## Acceptance Criteria

- [ ] FakeKeycloakAdminAdapter 实现 KeycloakAdminPort 接口的全部第一版方法
- [ ] Adapter 使用线程安全的内存结构存储 Organization、User、Membership、Realm Role、User-Role Assignment
- [ ] ensureOrganization 重复调用返回同一 OrganizationId；同 tenantId 不会出现第二个 Organization、且属性不一致时被校正
- [ ] ensureUser 同一 email 重复调用返回同一 UserId，不产生重复用户
- [ ] ensureOrganizationMembership、ensureRealmRole、ensureUserRealmRole 重复调用不报错且不重复创建关系
- [ ] 提供测试钩子：可预植入已存在的 Organization/User/Role，可配置某个方法报冲突或外部不可用以验证失败恢复路径
- [ ] Adapter 不打印 secret、临时密码、凭证到日志

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

