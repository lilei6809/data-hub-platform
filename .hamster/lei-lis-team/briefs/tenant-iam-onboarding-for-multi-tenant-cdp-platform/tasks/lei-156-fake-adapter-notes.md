---
id: "4ed64027-b474-44f8-8fe0-74ffb5c0c462"
entity_type: "task"
entity_id: "76a659f8-532c-4a20-a4d4-a16daaec229a"
title: "编写 Fake Adapter 幂等语义单元测试套件 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-156"
parent_task_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:17:13.342396+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为 Fake/In-Memory Keycloak Adapter 编写覆盖幂等语义的单元测试套件。

## Implementation Approach

1. 为每个 `ensure*` 方法编写一组聚焦测试：

- **ensureOrganization**：首次创建 / 重复调用相同 ID / 同 tenantId 不重复 / attributes 校正
- **ensureUser**：首次创建 / 同 email 复用 / 不产生重复用户
- **ensureOrganizationMembership**：首次加入 / 重复加入幂等 / 同一 user 不重复出现
- **ensureRealmRole**：首次创建 / 重复创建幂等
- **ensureUserRealmRole**：首次分配 / 重复分配幂等

1. 编写"中途失败后恢复"专项测试：

- 通过 `preload(...)` 模拟"Organization 已经存在但用户分配步骤之前失败过"
- 再次调用各 `ensure*` 方法应当复用已有对象并补齐缺失关系

1. 编写"外部失败后重试"测试：使用 `failNextCallTo` 钩子让第一次调用抛端口级异常，确认第二次调用成功。
2. 使用 `snapshot()` 在每个测试结尾断言内存状态（对象计数、关系集合），避免依赖内部字段。
3. 测试使用 JUnit 5 + AssertJ 风格的清晰断言；命名采用 `should_xxx_when_xxx` 模式。

## Acceptance Criteria

- 所有 5 个 ensure* 方法均覆盖首次/重复/边界条件
- 包含中途失败后恢复测试用例
- 包含 failNextCallTo 钩子驱动的重试测试
- 所有测试纯内存运行，无外部依赖
- 测试套件作为后续真实 Keycloak Adapter 的契约参考可被复用或借鉴

## Technical Constraints

- 仅使用 JUnit 5 + AssertJ（或项目已有等价测试栈），不引入新测试框架
- 测试不得访问真实 Keycloak、数据库、网络
- 单个测试用例运行时间应在毫秒级

## Code Patterns to Follow

- Given-When-Then 结构
- Fake 而非 Mock：使用 Adapter 自身钩子驱动状态，不使用 Mockito 模拟 Port 内部
- 每个测试只断言一个具体行为，便于失败时快速定位## Details

**Scope**: 针对 FakeKeycloakAdminAdapter 的单元测试：每个 ensure* 方法的首次创建、重复调用、冲突恢复、属性校正、模拟外部失败

**Out of Scope**: 端到端 onboarding 测试（属于 Provisioning Service 任务）、Step Pipeline 幂等测试（属于 sibling 3）、状态机测试（属于 sibling 4）、事件发布测试（属于 sibling 5）、真实 Keycloak 异常映射测试（属于 sibling 6）

## Acceptance Criteria

- [ ] 测试覆盖 ensureOrganization 首次创建、重复调用返回同一 ID、同 tenantId 不产生重复 Organization、属性不一致被校正四个场景
- [ ] 测试覆盖 ensureUser 同 email 重复调用返回同一 UserId，不产生重复用户
- [ ] 测试覆盖 ensureOrganizationMembership 重复调用不报错且关系唯一
- [ ] 测试覆盖 ensureRealmRole 与 ensureUserRealmRole 重复调用幂等
- [ ] 测试覆盖中途失败恢复场景：预植入已存在的 Organization，再次调用 ensureOrganization 返回原有 ID；同理验证 User、Membership、Role Assignment
- [ ] 测试覆盖 failNextCallTo 钩子：让某个 ensure* 抛出端口级异常，随后重试能成功完成
- [ ] 所有测试可独立运行，不依赖真实 Keycloak、数据库、Kafka 或网络

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 4 |

