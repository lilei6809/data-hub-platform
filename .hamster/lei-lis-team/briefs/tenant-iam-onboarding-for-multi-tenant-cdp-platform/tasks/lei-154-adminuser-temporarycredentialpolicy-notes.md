---
id: "98b953a9-dde2-43e9-89de-153081f5043b"
entity_type: "task"
entity_id: "67bb53d8-4266-4b8b-bdda-d2d55e06b4af"
title: "建模 AdminUser 与 TemporaryCredentialPolicy 值对象 - Notes"
status: "done"
priority: "medium"
display_id: "LEI-154"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:16:57.197967+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## Summary

实现 AdminUser 与 TemporaryCredentialPolicy 两个值对象，作为 Desired State 中初始租户管理员的领域表达。

## Implementation Approach

1. 定义 TemporaryCredentialPolicy（最小策略字段，预留未来扩展结构）。
2. 定义 AdminUser 组合 Email 与 TemporaryCredentialPolicy，提供 of(Email) 工厂使用默认策略。
3. 构造期复用 Email 校验，并对策略字段做合理性校验。
4. toString 仅暴露遮蔽 email，不输出敏感字段。
5. 编写单元测试覆盖合法构造、非法 email、默认策略、equals/hashCode 与 toString 安全。

## Acceptance Criteria

- AdminUser 为不可变值对象，由 Email 与 TemporaryCredentialPolicy 组成
- TemporaryCredentialPolicy 提供默认实例并表达"临时、需改密"等最小语义
- AdminUser 提供工厂方法以 Email 创建带默认凭证策略的实例
- AdminUser.toString 仅输出遮蔽 email，不包含密码或 secret
- 单元测试覆盖合法构造、非法 email、默认策略、equals/hashCode 与日志安全
- 不引入 Keycloak SDK、Spring、Port 或持久化依赖

## Technical Constraints

- AdminUser 必须复用 Email 值对象，不允许直接持有 String email
- 不允许在领域层生成真实临时密码（仅描述策略）
- toString 严禁输出原始 email 或任何凭证## Details

**Scope**: AdminUser 与 TemporaryCredentialPolicy 两个值对象、其构造不变量、日志安全 toString、单元测试

**Out of Scope**: TenantIamDesiredState 聚合本身（独立子任务）、Keycloak Admin Port 及 Fake Adapter（兄弟任务）、EnsureAdminUserStep 实现（兄弟任务）、SecretStore/Vault 集成（兄弟任务）、真实密码生成与发送

**Implementation**: 1. 定义 TemporaryCredentialPolicy 值对象，至少包含"是否要求下次登录强制改密"等最小策略字段；预留未来扩展（密码长度、有效期等）的结构而不实现具体策略集合。
2. 定义 AdminUser 值对象，组合 Email 与 TemporaryCredentialPolicy；提供工厂方法表达"以 email 创建带默认临时凭证策略的管理员"。
3. 在构造期校验 Email 非空（复用 Email 值对象）并校验策略字段合理性。
4. toString 仅暴露遮蔽 email，不输出密码或凭证相关字段。
5. 编写单元测试覆盖合法构造、Email 校验失败、策略默认值、equals/hashCode 与 toString 安全性。

## Acceptance Criteria

- [ ] AdminUser 为不可变值对象，由 Email 与 TemporaryCredentialPolicy 组成
- [ ] TemporaryCredentialPolicy 提供默认实例并表达初始凭证为临时、下次登录需改密等最小语义
- [ ] AdminUser 提供工厂方法接受 Email 并默认使用默认凭证策略
- [ ] AdminUser.toString 仅输出遮蔽 email，不包含密码、secret 或原始邮箱
- [ ] 单元测试覆盖合法构造、非法 email、默认策略、equals/hashCode 与日志安全表示
- [ ] 不引入 Keycloak SDK、Spring、Port 或持久化依赖

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |
