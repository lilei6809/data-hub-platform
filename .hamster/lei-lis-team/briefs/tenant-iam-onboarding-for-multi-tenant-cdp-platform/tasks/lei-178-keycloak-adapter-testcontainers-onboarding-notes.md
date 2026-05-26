---
id: "bdeddbeb-0493-4831-99b4-aac3fdddae17"
entity_type: "task"
entity_id: "043b9bfd-3df0-401c-954b-53abc55d85ad"
title: "真实 Keycloak Adapter 集成测试（Testcontainers 验证生产 onboarding 幂等） - Notes"
status: "todo"
priority: "high"
display_id: "LEI-178"
parent_task_id: "082a15b8-2525-470b-a3b4-acc6d8b0be3b"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:19:25.730411+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 2026-05-26 集成测试边界调整

真实 Keycloak 集成测试应验证 Adapter 是否履行 `KeycloakAdminPort.ensureXxx` 契约，而不是验证 Step 是否处理 Keycloak 细节。

重点验收：

- 重复调用 Port 方法不产生重复 Organization/User/Membership/RoleAssignment。
- 创建冲突、已存在对象、已存在关系在 Adapter 内消解。
- Step/Application Service 不感知 Keycloak 409、HTTP status 或 SDK exception。
- `TENANT_ADMIN` 作为平台预置 Realm Role；缺失时应表现为非重试配置错误，而不是 tenant onboarding 自动创建。

## 摘要

使用 Testcontainers 启动真实 Keycloak 验证真实 Adapter 在生产语义下的幂等、属性正确性与异常映射，作为 Phase 2 验收证据。

## 实施步骤

1. 引入 Testcontainers Keycloak 模块，选用与生产一致的镜像版本并启用 Organizations 特性。
2. 通过 realm export JSON 或启动脚本初始化 shared realm `cdp`，准备 admin service account 凭证。
3. 编写集成测试用例：

- 首次 onboarding：依次调用 `ensureOrganization` → `ensureUser` → `ensureOrganizationMembership` → `ensureRealmRole("TENANT_ADMIN")` → `ensureUserRealmRole`，并独立查询 Keycloak 验证结果。
- 重复执行：同一 desired state 再跑一次，断言对象数量不变、id 一致。
- 中途失败恢复：预先创建 Organization，再触发完整 ensure 流程，断言剩余步骤完成。
- 属性校验：Organization 的 `tenant_id` 与 `tier` 写入正确，User 可按 email 查询并属于 Organization 且拥有 `TENANT_ADMIN`。
- 异常映射端到端：停掉 Keycloak 容器或指向不可达端口，断言抛出 `KeycloakTransientException` 且 retryable=true。

1. 将该测试套件归入 `integration-test` 或专用 Gradle/Maven profile，避免拖慢默认构建。

## 验收标准

- Testcontainers Keycloak 启动并配置 realm `cdp`
- 首次 onboarding 验证五类对象创建
- 重复执行不增加对象
- 中途失败恢复路径验证通过
- 属性、membership、role 三项断言通过
- Transient 异常映射端到端验证通过

## 技术约束

- 镜像版本与生产对齐
- 集成测试 profile 隔离
- 不依赖外部服务

## 代码模式

- ensure 模式幂等性以独立查询作为最终断言依据## Details

**Scope**: 使用 Testcontainers 启动真实 Keycloak，验证真实 Adapter 在幂等、状态、属性、异常映射上的生产可用性

**Out of Scope**: Step Pipeline、Provisioning Service、状态机、事件发布的单元测试（在兄弟任务）；Kafka 集成测试；Authorization Service 测试

**Constraints**: Testcontainers Keycloak 镜像版本必须与生产主版本一致并启用 Organizations 特性, 集成测试与单元测试 profile 分离，避免在默认构建中启动 container 拖慢 CI, 测试必须反复可运行，不依赖外部服务

## Acceptance Criteria

- [ ] 集成测试启动 Testcontainers Keycloak 实例并创建 shared realm `cdp`（可以通过 realm export/import 或初始化脚本）
- [ ] 验证首次完整 onboarding：调用真实 adapter 的各 ensure 方法后，通过独立查询确认 Organization、User、Membership、RealmRole、UserRealmRole 都存在，且 Organization 属性含正确的 `tenant_id` 与 `tier`
- [ ] 验证第二次重复调用同一 desired state 不产生重复对象：查询 Organization/User/Role Assignment 数量未增加
- [ ] 验证中途失败后恢复：手动创建 Organization 后调用完整 ensure 流程，能复用已有 Organization 并完成其余步骤
- [ ] 验证 admin 用户可按 email 查出、属于目标 Organization 且拥有 `TENANT_ADMIN` realm role
- [ ] 验证异常映射：停掉 Keycloak 容器（或使用错误端口）后调用 adapter 得到 `KeycloakTransientException` 且 retryable=true

## Context

| Field | Value |
|-------|-------|
| category | testing |
| complexity | 7 |
