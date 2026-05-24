---
id: "5921a476-c362-4134-a082-67edb687a5fd"
entity_type: "task"
entity_id: "ba63b0d8-42cf-4713-9ec6-a02a0dbe4013"
title: "实现真实 Keycloak User 操作（按 email ensure + 临时凭证策略） - Notes"
status: "todo"
priority: "high"
display_id: "LEI-157"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:16.151727+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现真实 Keycloak Adapter 的 `ensureUser` 能力，按 email 幂等地确保租户初始管理员用户存在，并按策略写入临时凭证。

## 实施步骤

1. 在真实 adapter 中实现 `ensureUser(email, temporaryCredentialPolicy)`。
2. 优先按 email 在 realm `cdp` 中查询用户；命中则直接返回 userId。
3. 未命中时创建用户，根据 `temporaryCredentialPolicy` 设置临时密码及 `requiredActions`（如首次登录改密）。
4. 创建期间遇 409 Conflict 则按 email 退回查询并复用。
5. 严格审计日志：临时密码、credential value 永不进入 logger、异常 message、metrics 标签。
6. 编写单元测试覆盖：首次创建、已存在复用、409 退回查询、敏感字段日志安全。

## 验收标准

- 首次按 policy 创建带临时凭证的用户
- 已存在时复用 userId，不重置密码
- 409 时回退查询
- 测试断言敏感字段未出现在日志中
- 返回 userId 与 Keycloak `sub` 一致

## 技术约束

- ensure 语义
- 临时密码与凭证绝不落日志
- email 是用户幂等查找键

## 代码模式

- ensure 模式：先 lookup 再 create，create 期间冲突时再 lookup## Details

**Scope**: ensureUser 方法实现：email 查询、创建、临时凭证设置、409 退回查询

**Out of Scope**: Organization 、Membership、Role 操作；SecretStore 实现；密码重置邮件发送

**Constraints**: ensure 语义，重复调用不创建重复用户, 临时密码、secret 不得进入任何日志、异常信息或 metrics, email 作为幂等查找键

## Acceptance Criteria

- [ ] 实现 `ensureUser(email, temporaryCredentialPolicy)`：user 不存在时创建并按 policy 设置临时密码与 `requiredActions`（如首次登录改密）
- [ ] user 已存在（按 email 查询）时返回现有 userId，不重复创建也不重置现有密码
- [ ] 创建遇 409 Conflict 时能按 email 退回查询并复用 userId，不向上抛冲突
- [ ] 单元测试验证临时密码和凭证不出现在任何 logger 调用参数中
- [ ] 返回的 userId 与 Keycloak `sub` 一致，为后续 Membership / Role 子任务提供稳定句柄

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |

