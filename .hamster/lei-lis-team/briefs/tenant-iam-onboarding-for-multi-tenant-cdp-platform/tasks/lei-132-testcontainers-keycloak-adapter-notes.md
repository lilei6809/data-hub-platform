---
id: "e6807eb2-f7c6-4c27-bdfc-9ac77f94b62c"
entity_type: "task"
entity_id: "270da484-b7da-44d4-ae70-74304a35840d"
title: "基于 Testcontainers 验证真实 Keycloak Adapter 幂等语义 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-132"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:00:02.165218+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

通过 Testcontainers 启动真实 Keycloak，对真实 Adapter 的 ensure 操作与异常映射执行集成测试。

## Implementation Approach

1. 配置 Testcontainers Keycloak 模块（或自定义 GenericContainer + import realm JSON），预配置 realm `cdp` 与 service account client。
2. 在测试 fixture 中按子任务 1 的方式装配 Admin Client（注入测试容器 base URL + 测试 secret）。
3. 编写 ensure 操作幂等测试：每个 ensure 方法运行 "首次 / 重复 / 重复 N 次" 用例，断言 Keycloak 内对象数量与属性符合预期。
4. 编写并发/409 复用测试：并行触发同一 ensure，断言最终对象唯一。
5. 编写中途失败重试测试：先成功创建前几步对象，再从头重跑剩余 ensure，断言流程能完成。
6. 编写异常映射测试：注入错误 secret 触发 401；查询不存在资源触发 404；停止容器或指向不可达端口触发超时；断言映射后的领域错误分类正确。
7. 断言日志不含敏感信息（可使用日志捕获 + 关键字检查）。

## Acceptance Criteria

- Testcontainers Keycloak 在 CI 中可启动并预配置 realm
- 覆盖 5 个 ensure 操作的首次/重复/重试场景
- 验证 Organization 属性、Admin 查询、Membership、TENANT_ADMIN 角色
- 401/404/超时映射符合预期
- 日志脱敏

## Technical Constraints

- 测试不得依赖外部 Keycloak 实例
- 不引入 Step Pipeline 或 Provisioning Service 依赖
- 单测套件运行时间需控制在合理范围（容器复用）

## Code Patterns to Follow

- Testcontainers + JUnit 5 lifecycle
- Adapter 层集成测试隔离于上层编排

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: 针对真实 Keycloak Adapter 的集成测试：Testcontainers 启动 Keycloak、realm 预配、各 ensure 操作的幂等与异常映射验证

**Out of Scope**: Step Pipeline 的端到端集成测试（sibling 4585bfc9、bba824b0）；Fake Adapter 的单元测试（sibling 9b66bd20）；Kafka 事件的集成测试（sibling 3f23a260）；Authorization 与 Tenant Context 相关测试

## Acceptance Criteria

- [ ] 集成测试使用 Testcontainers 启动真实 Keycloak 实例，预配 Shared Realm `cdp` 与 service account client
- [ ] 包含首次创建、重复 ensure、中途失败后重试三类场景，覆盖 Organization、User、Membership、Realm Role、用户角色绑定五个 ensure 操作
- [ ] 验证 Organization 携带正确的 `tenant_id` 和 `tier` 属性；初始管理员可通过 email 查询到；属于目标 Organization；拥有 `TENANT_ADMIN` 角色
- [ ] 验证异常映射：模拟 401（错误 secret）、404（查询不存在）、超时（不可达端点）能得到期望的领域错误分类
- [ ] 测试可在 CI 中独立运行，不依赖外部环境；日志不出现临时密码、client secret、access token

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 7 |
| skillReferences | Hamster Blueprint |

