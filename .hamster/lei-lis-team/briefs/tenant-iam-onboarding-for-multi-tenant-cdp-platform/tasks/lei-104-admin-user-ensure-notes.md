---
id: "cce652e9-a3c7-41ad-a057-6bf6187cd1b8"
entity_type: "task"
entity_id: "ad33f7fb-cb9b-4d9a-9e97-6a12cb3a2185"
title: "实现 Admin User 的 ensure 与临时凭证策略 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-104"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:12.136975+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

在真实 Keycloak Admin API 上实现 `ensureUser(email, temporaryCredentialPolicy)` 的幂等创建、复用与临时凭证写入。

## Implementation Approach

1. 实现 Port 的 `ensureUser(email, temporaryCredentialPolicy)`：先按 email exact match 查询 Users。
2. 命中则返回已有 `userId`；未命中调用 Users API 创建（enabled=true，emailVerified 视策略而定）。
3. 创建捕获 `409 Conflict`：重新按 email 查询并复用。
4. 创建成功后按策略写入凭证：临时密码 + `temporary=true` 标志；或仅置 `RequiredAction = UPDATE_PASSWORD`。
5. 全链路屏蔽临时密码：不进日志、不进异常消息、不进 metric label。

## Acceptance Criteria

- 首次创建用户并返回稳定 `userId`
- 重复调用复用已有用户
- 409 回退查询复用
- 临时凭证按策略写入且不泄漏
- 查询使用 email exact match

## Technical Constraints

- 不得使用本地事务包裹此远程调用
- 临时密码生成与传入由调用方（或后续 SecretStore 任务）决定，本任务只忠实写入
- 不在此阶段同步 user attributes 或 verified 状态

## Code Patterns to Follow

- `ensure` 语义
- 409 回退查询

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: 真实 Adapter 中 User 的 email 查询、创建、临时凭证写入与 409 复用

**Out of Scope**: Port 接口定义与 Fake Adapter（sibling 9b66bd20）；Membership 与 Role 分配（其他子任务）；SecretStore 中凭证生成策略源头（sibling 84d4e472）；邮件发送集成

## Acceptance Criteria

- [ ] 首次按 email 调用 `ensureUser` 在 Keycloak 中创建用户并返回稳定 `userId`
- [ ] 重复调用同一 email 复用已有用户，不创建重复，不抛 409
- [ ] 创建返回 409 Conflict 时适配器重查 email 并复用已有用户
- [ ] 临时凭证（临时密码或强制重置标志）按 `TemporaryCredentialPolicy` 写入，密码本身不出现在任何日志、异常信息或 metric 中
- [ ] User 查询使用 `email` exact match，不依赖不稳定的模糊查询

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
| skillReferences | Hamster Blueprint |

