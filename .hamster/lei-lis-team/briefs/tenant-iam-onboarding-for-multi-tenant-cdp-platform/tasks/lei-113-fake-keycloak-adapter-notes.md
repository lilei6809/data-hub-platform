---
id: "e383ae76-17a6-40c1-b262-d9e1e1747bd8"
entity_type: "task"
entity_id: "e1d21fcd-2985-4357-89de-1a5c109836c6"
title: "为 Fake Keycloak Adapter 编写幂等性与冲突处理契约测试 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-113"
parent_task_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:36.909297+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Execution Note

Deferred until the Step Pipeline and Application Service call shape are defined. Contract tests should verify the adapter against stable step-driven port semantics, including idempotency, conflict fallback, and controlled failure paths.

## 2026-05-26 Adapter Contract 调整

Fake Adapter 的目标不是模拟 Step 内部逻辑，而是验证 `KeycloakAdminPort.ensureXxx` 契约：

- 重复调用同一个 `ensureXxx` 返回稳定结果，不创建重复对象。
- 创建时模拟 409 后，Adapter 必须 fallback 到 lookup 并返回已有对象，不把 409 暴露给 Step/Application Service。
- 属性校正、关系已存在 no-op、角色绑定已存在 no-op 都在 Adapter 契约测试中覆盖。
- 故障注入用于验证 Adapter 抛出端口级异常及 retryable 标记，不用于驱动 Application Service 流程设计。

## 概要

为 Fake Keycloak Adapter 编写针对 `KeycloakAdminPort` 的契约测试，验证 ensure 幂等语义与 Port 层错误映射，同时为后续真实 Adapter 复用奠定契约规范。

## 实施方式

1. 创建 KeycloakAdminPortContractTest 抽象基类，所有断言只针对 Port 接口
2. 在 FakeKeycloakAdminAdapterTest 提供 Fake 实例运行契约用例
3. 为每个 ensure 方法编写"首次创建 / 重复幂等 / 乱序幂等"用例
4. 测试 ensureOrganization attribute 一致性校正规则
5. 利用 FaultInjector 模拟 409 Conflict 与 Transient 异常并断言 Port 层异常类型
6. 保证契约套件可被真实 Keycloak Adapter 直接复用

## 验收标准

- 每个 ensure 方法覆盖三类幂等用例
- attribute 校正规则有测试覆盖
- 模拟 409 Conflict 时 Adapter 行为符合 Port 契约
- Transient 异常断言为 Port 层异常类型，不是 SDK 异常
- 契约测试基类可被后续真实 Adapter 直接复用

## 技术约束

- 只针对 KeycloakAdminPort 接口编写，不耦合 Fake 内部实现
- 不启动 Spring 上下文，保持快速单元测试
- 断言只通过 Port 返回值或 Port 定义的只读查询 API## Details

**Scope**: FakeKeycloakAdminAdapter 的单元/集成测试，验证每个 ensure 方法的幂等语义、冲突处理、Port 错误映射；KeycloakAdminPort 契约测试套件骨架

**Out of Scope**: Step Pipeline 的幂等测试（兄弟任务 3）、端到端 onboarding 流程测试（兄弟任务 4）、真实 Keycloak Adapter 测试（兄弟任务 6）、事件发布测试（兄弟任务 5）

**Implementation**: 1. 在测试模块创建 KeycloakAdminPortContractTest 抽象基类，所有用例基于 KeycloakAdminPort 接口（不耦合 Fake 实现细节）。2. 在 FakeKeycloakAdminAdapterTest 中提供 Fake 实例并执行契约用例。3. 为每个 ensure 方法编写：首次调用创建对象、第二次调用返回相同 ID 不重复创建、并发或交叉顺序下幂等。4. 验证 ensureOrganization 在 attributes 不一致时按规则校正。5. 使用故障注入模拟 Conflict 与 Transient 异常，断言抛出的是 Port 层定义的异常类型而非 SDK 异常。6. 确保测试套件结构允许真实 Adapter 后续直接复用。

**Constraints**: 测试必须仅针对 KeycloakAdminPort 接口编写，不耦合 Fake 内部数据结构, 不引入 Spring Boot 上下文启动，保持为纯单元测试以保证执行速度, 断言必须通过 Port 返回值或 Port 定义的只读查询 API，避免反射访问 Fake 内部存储

## Acceptance Criteria

- [ ] 每个 MVP ensure 方法都有至少 'first call creates'、'second call returns same id'、'idempotent under reordering' 三类用例且全部通过
- [ ] ensureOrganization 的 attribute 一致性规则被测试覆盖：同一 tenantId 不同 attribute 输入时结果符合约定的校正行为
- [ ] 利用故障注入模拟 Keycloak 409 Conflict 场景：Adapter 能按 Port 契约 fallback 到查询已有对象并返回稳定 ID，而不向上抛出异常
- [ ] Transient 异常测试验证所抛出的异常为 Port 层定义的领域错误类型，不是 Keycloak SDK 异常或原始 HTTP 异常
- [ ] 契约测试抽象基类或共享用例模块可被后续真实 Keycloak Adapter 复用，只需提供不同的 Port 实例

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 4 |
