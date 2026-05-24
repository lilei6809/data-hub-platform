---
id: "9bec8fe2-86f2-4478-8baf-1fc010a8708e"
entity_type: "task"
entity_id: "1e26ecbe-e719-4454-b4c2-1db84719b722"
title: "定义 KeycloakAdminPort 意图型接口与领域错误类型 - Notes"
status: "todo"
priority: "medium"
display_id: "LEI-85"
parent_task_id: "9b66bd20-cdbf-415d-98d9-cdd9e7abced2"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:57:01.522112+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 概要

在应用核心定义 `KeycloakAdminPort` 意图型接口与 Port 层领域错误类型，建立 Step Pipeline 与所有 Keycloak Adapter 的共同契约。

## 实施方式

1. 在应用核心模块创建 `KeycloakAdminPort` 接口
2. 定义五个 ensure 方法签名（ensureOrganization / ensureUser / ensureOrganizationMembership / ensureRealmRole / ensureUserRealmRole），参数与返回类型只使用领域值对象
3. 定义 Port 层异常类型（NotFound / Conflict / TransientFailure / Unauthorized），由后续 Adapter 统一映射
4. 编写方法 JavaDoc，明确每个方法的 'ensure' 幂等语义
5. 通过模块结构或构建配置确保应用核心不依赖 Keycloak SDK

## 验收标准

- KeycloakAdminPort 接口包含五个 MVP 意图方法，签名不依赖 Keycloak SDK 类型
- 每个方法文档显式说明 ensure 语义与冲突处理约定
- Port 层独立异常类型覆盖 NotFound / Conflict / Transient / Unauthorized
- 接口模块不依赖 keycloak-admin-client
- JavaDoc 中说明未来扩展方法的命名约定但 MVP 不实现

## 技术约束

- 应用核心模块禁止依赖 Keycloak SDK
- 错误类型必须为领域语义而非 HTTP 状态码或 SDK 异常
- 方法返回稳定 ID，避免泄漏 SDK Representation## Details

**Scope**: KeycloakAdminPort 接口定义、Port 层领域错误/异常类型定义、方法 JavaDoc/语义文档

**Out of Scope**: 任何 Adapter 实现（Fake 或真实）、Step 实现、TenantIamProvisioningService、领域模型（如 TenantIamDesiredState 由兄弟任务 1 定义）、SecretStorePort（兄弟任务 8）、EventPublisher（兄弟任务 5）

**Implementation**: 1. 在应用核心模块创建 `KeycloakAdminPort` 接口文件。2. 定义五个 ensure 方法的签名，参数使用领域值对象（TenantId、Email、RoleName 等），返回稳定标识。3. 在同一模块下定义 Port 层异常基类与若干子类型（NotFound/Conflict/Transient/Unauthorized）。4. 编写接口与异常类型的语义文档，强调 ensure 幂等约定。5. 在模块构建脚本中验证应用核心模块不依赖 Keycloak SDK。

**Constraints**: 接口必须位于应用核心模块，禁止依赖 keycloak-admin-client 或任何 Keycloak 具体类型, 错误类型必须是领域级别的语义分类，不能直接透传 HTTP 状态码或 SDK 异常, 方法签名必须支持后续真实 Adapter 在不修改接口的前提下进行扩展（例如返回稳定 ID 而不是 SDK Representation 对象）

## Acceptance Criteria

- [ ] KeycloakAdminPort 接口包含 ensureOrganization、ensureUser、ensureOrganizationMembership、ensureRealmRole、ensureUserRealmRole 五个意图型方法，每个方法签名仅依赖领域类型或值对象，不引入任何 Keycloak SDK 类型
- [ ] 每个 ensure 方法的方法签名与文档明确说明 'ensure' 语义：目标不存在则创建、已存在则复用、返回稳定的标识（例如 organizationId、userId、roleId）
- [ ] Port 层定义独立的领域错误/异常类型（不是 Keycloak SDK 异常），覆盖至少 'NotFound'、'Conflict'、'TransientFailure'、'Unauthorized' 等基本类别，供 Fake 与真实 Adapter 统一映射
- [ ] 接口位于应用核心模块，不依赖 keycloak-admin-client 或其他外部库；模块依赖方向通过模块结构或构建脚本明确强制
- [ ] 接口文档（JavaDoc/Kotlin Doc）中显式说明 MVP 不实现的扩展方法命名约定（ensureIdentityProvider、ensureProtocolMapper、ensureClientAudience、ensureMfaPolicy），但接口本体不声明这些方法以避免空实现

## Context

| Field | Value |
|-------|-------|
| category | design |
| complexity | 4 |

