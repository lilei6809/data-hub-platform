---
id: "519a4590-a188-4a7e-ad1f-f9db7ff6e5a0"
entity_type: "task"
entity_id: "f0867a90-5e2d-4ea0-9152-5dbe03c15a85"
title: "定义 TenantId 与 AdminUser 值对象及校验规则 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-91"
parent_task_id: "8c75479f-d104-488f-8e31-fc15c1e6ab96"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T06:18:35.464131+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

交付 Tenant IAM Desired State 模型中的基础值对象：`TenantId`、`AdminUser`、`TemporaryCredentialPolicy`。

## 实施方式

1. 定义 `TenantId` 值对象，构造时校验 slug 格式（如 `^[a-z0-9][a-z0-9-]*[a-z0-9]$`），失败抛出领域异常。
2. 定义 `TemporaryCredentialPolicy` 值对象，首版至少表达「生成临时密码 + 强制首次登录修改」，结构上预留未来扩展（如有效期、复杂度）。
3. 定义 `AdminUser` 值对象，至少包含 `email`（基础格式校验）与 `temporaryCredentialPolicy`。
4. 三个值对象均实现不可变性、基于字段的 equals/hashCode 与可读 toString（不打印敏感字段）。
5. 编写单元测试覆盖合法/非法构造、相等性、不可变性。

## 验收标准

- `TenantId` 构造校验 slug 格式，非法输入抛出领域异常
- `AdminUser` 在构造时校验 email 基础格式
- `TemporaryCredentialPolicy` 表达「临时密码 + 强制首登修改」并预留扩展位
- 三者均为不可变值对象，equals/hashCode 基于字段
- 单元测试覆盖合法路径、非法路径与相等性

## 技术约束

- 领域层零外部依赖（不引入 Keycloak SDK、Spring、JPA 等）
- toString 不打印敏感字段（如临时密码内容）

## Relevant Skills

- Hamster Blueprint## Details

**Scope**: TenantId 值对象、AdminUser 值对象、TemporaryCredentialPolicy 值对象及其构造校验、相等性与单元测试。

**Out of Scope**: TenantIamDesiredState 聚合本身、IdentityMode/RealmStrategy 枚举、TenantIamProvisioningState（属于状态机 sibling 任务）、Keycloak Port、Step Pipeline、事件契约。

**Constraints**: 领域层不依赖任何基础设施框架（Keycloak SDK、Spring、JPA、Kafka）, 值对象必须不可变, toString / 日志输出不得泄漏临时凭证或敏感字段

## Acceptance Criteria

- [ ] `TenantId` 是不可变值对象，构造时校验 slug 格式（小写字母、数字、短横线），非法输入抛出领域异常
- [ ] `AdminUser` 是不可变值对象，至少包含 `email` 与 `temporaryCredentialPolicy` 字段，email 在构造时做基础格式校验
- [ ] `TemporaryCredentialPolicy` 以值对象形式表达，首版至少支持「临时密码 + 强制首次登录修改」语义，并预留未来扩展
- [ ] 两个值对象都实现基于字段的 equals/hashCode，确保领域比较稳定
- [ ] 提供单元测试覆盖：合法构造、非法 slug、非法 email、相等性、不可变性

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 3 |
| skillReferences | Hamster Blueprint |

