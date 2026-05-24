---
id: "362df698-36c5-4032-a252-90e6bea848b2"
entity_type: "task"
entity_id: "90a37075-321f-4c22-a8c8-9d3b7d383d51"
title: "引入 RealmStrategy 解析扩展点以承接 Dedicated Realm 未来场景 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-174"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:19:00.092225+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概述

引入 RealmStrategyResolver 抽象，将 Realm 选择从各 ensureXxx Step 内部抽离到 Pipeline 入口，为未来 Dedicated Realm 留下稳定扩展边界；MVP 中只实现 SHARED_REALM，DEDICATED_REALM 显式拒绝。

## 实现要点

1. 定义 `RealmStrategyResolver` 接口：输入 `TenantIamDesiredState`，输出包含 `realmName` 与 `strategy` 的解析上下文对象。
2. 实现 `SharedRealmResolver`：返回可配置的 Realm name，默认 `cdp`。
3. 在 Step Pipeline 入口处调用 Resolver，并把结果存入执行上下文，供下游 Step 读取。
4. DEDICATED_REALM 路径：Resolver 抛出明确领域错误（例如 `UnsupportedRealmStrategyException`），由 `TenantIamProvisioningService` 捕获并写入 `IAM_FAILED` + `failureCode = UNSUPPORTED_REALM_STRATEGY`。
5. 单元测试三条路径：SHARED_REALM 默认名、SHARED_REALM 自定义名、DEDICATED_REALM 拒绝。

## 验收标准

- 接口与默认实现到位
- Step 执行上下文承载 resolved Realm name
- DEDICATED_REALM 不会被静默当作 SHARED_REALM
- DEDICATED_REALM 失败状态可清晰记录
- 单元测试三条路径覆盖

## 技术约束

- DEDICATED_REALM 不得被默认处理
- Resolver 在 Pipeline 入口执行一次
- Realm name 可配置，默认 `cdp`

## 范围说明

- **包含**：RealmStrategyResolver 接口、SharedRealmResolver、Pipeline 入口接线、DEDICATED_REALM 拒绝、单元测试
- **不包含**：真实 Dedicated Realm 创建实现；跨 Realm 数据隔离机制；ensureXxx Step 内部其他逻辑修改## Details

**Scope**: RealmStrategyResolver 接口、SharedRealmResolver 实现、Step 执行上下文暂存 resolved Realm name 的结构、DEDICATED_REALM 明确拒绝、单元测试。

**Out of Scope**: 真实 Dedicated Realm 创建与生命周期管理；Realm 跨机架底层数据隔离调整；现有 ensureXxx Step 内部逻辑的任何重写（只调整 Realm name 来源）。

**Constraints**: DEDICATED_REALM 不得被默默当作 SHARED_REALM 处理, SharedRealmResolver 返回的 Realm name 需可配置但默认为 brief 指定的 `cdp`, RealmStrategyResolver 必须位于 Pipeline 执行入口附近，避免每个 Step 重复解析, 拒绝 DEDICATED_REALM 时必须返回明确领域错误，使上层能记录 IAM_FAILED 状态与原因

## Acceptance Criteria

- [ ] RealmStrategyResolver 接口定义于应用层，输入 desired state，输出包含 Realm name 与策略身份的上下文对象
- [ ] SharedRealmResolver 默认返回可配置的 Realm name（默认 `cdp`）
- [ ] Step 执行上下文暂存 resolved Realm name，后续 Step 不需重复解析 realmStrategy
- [ ] desired state 中 realmStrategy = DEDICATED_REALM 时，Resolver 拒绝并报出明确领域错误；Service 能将状态记录为 IAM_FAILED 与可识别的 failureCode
- [ ] 单元测试覆盖：SHARED_REALM 返回 `cdp`、SHARED_REALM 使用自定义名、DEDICATED_REALM 被拒绝

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |

