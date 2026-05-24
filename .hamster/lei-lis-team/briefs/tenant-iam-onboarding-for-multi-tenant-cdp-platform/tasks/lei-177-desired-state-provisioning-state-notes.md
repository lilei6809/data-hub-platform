---
id: "0c685185-41a6-475b-b769-22c195eefc0d"
entity_type: "task"
entity_id: "0ef937c0-3066-4ef9-8799-6ea32411f839"
title: "为 Desired State 与 Provisioning State 领域模型补充不变量与扩展性单元测试 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-177"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:19:18.996567+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为已落地的 Desired State 与 Provisioning State 领域类型补充跨类型不变量与扩展性单元测试，作为后续阶段安全网。

## Implementation Approach

1. 锁定 ofMinimalInput 默认值（LOCAL_ONLY、SHARED_REALM、空扩展列表）。
2. 验证向扩展列表追加占位元素后聚合仍合法构造。
3. 验证 TenantIamProvisioningState 初始化后 tenantId / correlationId 不被状态转移改写。
4. 长重试序列下 retryCount 单调累加，lastAttemptAt 单调不减。
5. toString 在所有分支不出现原始 email 与 secret 样本。
6. 测试仅依赖 JUnit 与领域类型。

## Acceptance Criteria

- ofMinimalInput 默认值测试锁定
- 扩展占位列表追加测试通过
- tenantId/correlationId 不可改写测试通过
- retryCount 与 lastAttemptAt 单调性测试通过
- toString 不泄漏敏感字段测试通过
- 不依赖 Spring / Mockito / Testcontainers

## Technical Constraints

- 仅领域模块测试源集，不引入基础设施
- 不替代后续 Step Pipeline / Service 的集成测试
- 不修改已实现的领域类型，仅补充测试## Details

**Scope**: 领域模型跨类型不变量测试、最小输入默认值锁定测试、扩展字段安全增加测试、状态机与聚合 correlationId/tenantId 一致性测试

**Out of Scope**: Step Pipeline / Provisioning Service 测试（兄弟任务）、Keycloak Fake Adapter 集成测试（兄弟任务）、事件发布测试（兄弟任务）、持久化 / Repository 集成测试

**Implementation**: 1. 编写测试套件锁定 ofMinimalInput 默认行为：identityMode==LOCAL_ONLY、realmStrategy==SHARED_REALM、identityProviders 与 policies 为空集合。
2. 编写测试验证向 identityProviders / policies 列表添加扩展占位元素后，聚合仍能合法构造且第一版主流程语义未变（仅通过构造与字段读取断言，不调用 Step）。
3. 编写测试验证 TenantIamProvisioningState.initialize(tenantId, correlationId) 与 TenantIamDesiredState 共享 tenantId 时的字段约束（如 tenantId 不可改写）。
4. 编写测试验证状态机在长重试序列下 retryCount 单调累加且 lastAttemptAt 单调不减。
5. 编写测试断言聚合与状态对象 toString 在任何分支都不出现原始 email 或失败 messages 中嵌入的 secret 占位（通过样本数据反向断言）。
6. 测试位于领域模块测试源集，仅使用 JUnit 与领域类型，不依赖 Spring / Mockito 基础设施扩展。

## Acceptance Criteria

- [ ] 测试锁定 ofMinimalInput 默认值：LOCAL_ONLY、SHARED_REALM、空 identityProviders、空 policies
- [ ] 测试证明向扩展列表添加占位元素后聚合仍可合法构造且不影响其他字段
- [ ] 测试验证 TenantIamProvisioningState 初始化后 tenantId 与 correlationId 不可被状态转移改写
- [ ] 长重试序列下 retryCount 单调累加且 lastAttemptAt 单调不减
- [ ] 所有 toString 路径测试断言不输出原始 email 与任何凭证样本字段
- [ ] 测试仅使用 JUnit 与领域类型，不依赖 Spring / Mockito / Testcontainers

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

