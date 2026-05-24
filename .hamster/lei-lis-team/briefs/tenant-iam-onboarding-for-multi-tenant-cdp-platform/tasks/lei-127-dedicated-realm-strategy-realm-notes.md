---
id: "aef247cf-298a-4792-9886-3d4ecd718408"
entity_type: "task"
entity_id: "333f26f4-77ce-4e0b-87aa-bff8d7945b7e"
title: "为 Dedicated Realm Strategy 设计 Realm 解析扩展点 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-127"
parent_task_id: "84d4e472-cfee-4df6-b6b2-9addc7603ebf"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:59:26.410858+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

为未来 Dedicated Realm-per-Tenant 引入 RealmResolver 扩展点，消除 Port 与 Steps 中对 realm 名称的硬编码。

## Implementation Approach

1. 定义 `RealmName` 值对象（不可变字符串，构造期校验非空和命名规则）。
2. 定义 `RealmResolver` 接口：

- `RealmName resolveRealm(TenantIamDesiredState desiredState)`

1. 实现 `SharedRealmResolver`：

- `SHARED_REALM` → 返回 `RealmName("cdp")`
- `DEDICATED_REALM` → 抛出 `UnsupportedRealmStrategyException`（明确尚未实现）

1. 重构 KeycloakAdminPort Fake/接口签名（如需）与所有 ensure Steps，使其在需要 realm 上下文时通过依赖注入的 RealmResolver 解析，而不是引用 `"cdp"` 字符串常量。
2. 编写单元测试覆盖两种 realmStrategy 分支、命名校验、与现有幂等测试的回归。
3. 在代码或注释中标注 DedicatedRealmResolver 是后续阶段的扩展点。

## Acceptance Criteria

- RealmResolver 接口与 SharedRealmResolver 实现到位
- DEDICATED_REALM 明确抛出 UnsupportedRealmStrategyException
- 现有代码移除 realm 名称硬编码
- 现有幂等测试在重构后继续通过
- 新增单元测试覆盖两种策略分支

## Technical Constraints

- 不引入 DedicatedRealmResolver 的真实实现
- RealmName 校验必须拒绝 Keycloak 不允许的字符
- UnsupportedRealmStrategyException 是领域错误而非系统异常

## Code Patterns to Follow

- Resolver 模式与现有 Ports 一致通过依赖注入
- 明确的 Unsupported 异常优于沉默降级

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: RealmResolver 接口、SharedRealmResolver 默认实现、现有 Port 与 Steps 中 realm 引用的重构以使用 Resolver、单元测试。

**Out of Scope**: DedicatedRealmResolver 的真实创建逻辑、Dedicated Realm 下 Keycloak 资源拷贝与迁移、Realm 生命周期管理、Realm 删除与环境隔离。

## Acceptance Criteria

- [ ] RealmResolver 接口定义 resolveRealm(TenantIamDesiredState) 返回 RealmName 值对象
- [ ] SharedRealmResolver 在 realmStrategy=SHARED_REALM 时始终返回 cdp
- [ ] SharedRealmResolver 在 realmStrategy=DEDICATED_REALM 时报明确的 UnsupportedRealmStrategyException，而不是静默降级
- [ ] 现有 KeycloakAdminPort 调用与 ensure Steps 中对 realm 名称的引用均通过 RealmResolver 获取，不再出现硕编码常量
- [ ] 单元测试覆盖：SHARED_REALM 返回 cdp、DEDICATED_REALM 报错、重构后现有幂等测试仍然通过

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 4 |
| skillReferences | Hamster Blueprint |

