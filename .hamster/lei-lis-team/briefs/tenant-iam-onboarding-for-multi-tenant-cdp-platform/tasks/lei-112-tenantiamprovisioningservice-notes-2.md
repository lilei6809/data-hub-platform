---
id: "bc616007-024c-4e40-9305-25376167da49"
entity_type: "task"
entity_id: "2b2b236f-21bc-43a0-8b0b-2de960ff0f4c"
title: "实现 TenantIamProvisioningService 端到端编排与失败记录 - Notes"
status: "todo"
priority: "high"
display_id: "LEI-112"
parent_task_id: "bba824b0-b333-4d0c-9e34-db881377477a"
brief_id: "092e4d88-b9b5-4137-b557-de7ec1048ff4"
updated_at: "2026-05-23T05:58:22.472727+00:00"
synced_at: "2026-05-23T06:24:29Z"
---

## 摘要

实现 `TenantIamProvisioningService` 应用服务，端到端编排 onboarding：状态加载与初始化、状态机转换、Step Pipeline 调度、失败捕获与记录、retry 入口语义。

## 实现思路

1. 在 IAL 应用层创建 `TenantIamProvisioningService`，构造时注入：

- `TenantIamStateRepository`
- `List<TenantIamProvisioningStep>`（接口由兄弟父任务提供）

1. 暴露公开方法 `provisionTenantIam(TenantIamDesiredState desiredState, CorrelationId correlationId)`，内部流程：
2. 通过 `findByTenantId` 加载现有 state；若不存在则创建 PENDING_IAM 初始状态。
3. 若 state 已是 IAM_PROVISIONED，直接返回幂等成功结果，不再执行 pipeline。
4. 调用 `markAttemptStarted` 转入 IAM_PROVISIONING，写 correlationId/lastAttemptAt 后 save。
5. 顺序执行所有 Step；每个 Step 包裹 try/catch。
6. 全部成功后调用 `markProvisioned` 并 save。
7. 任何 Step 抛出领域错误时调用 `markFailed(failureCode, failureMessage)` 并 save，向上层抛出包装异常或返回失败结果。
8. 在每一次状态转换、Step 调用前后输出结构化日志：`tenantId`、`correlationId`、当前状态、步骤名。
9. 严格避免在日志中输出 Desired State 中的敏感字段（如 admin 临时凭证策略相关 secret）。
10. 编写单元测试，使用 Step 与 Repository 的测试替身覆盖：

- 首次 onboarding 成功路径
- 中途 Step 失败 → 状态 IAM_FAILED + retryCount=1
- 重试入口：state=IAM_FAILED 时再次调用 → Step 被重新执行
- 已完成路径：state=IAM_PROVISIONED → 直接返回，Step 未被调用

## 验收标准

- 单一公开入口同时覆盖首次执行与重试语义
- 状态转换全部通过 State 聚合行为方法，不直接修改字段
- 失败被捕获并写入 failureCode/failureMessage/retryCount
- 日志结构化包含 tenantId + correlationId
- 单元测试覆盖成功、失败、重试、幂等完成四条路径
- 日志不泄漏敏感字段

## 技术约束

- 远程 Step 调用不被包在本地事务中（PRD Risk 3）
- 服务必须保持纯应用层语义，不直接依赖 Keycloak SDK、Kafka、Spring Web
- correlationId 必须贯穿状态写入与日志

## 相关技能

- Hamster Blueprint## Details

**Scope**: TenantIamProvisioningService 类、provisionTenantIam 公开入口、状态加载/初始化/转换逻辑、Step Pipeline 调用与错误捕获、失败记录、correlationId/tenantId 结构化日志、重试入口语义

**Out of Scope**: Step 具体实现与接口定义（兄弟父任务）、KeycloakAdminPort 实现（兄弟父任务）、事件发布契约（兄弟父任务）、TenantContextFilter（兄弟父任务）、SecretStorePort（兄弟父任务）、Kafka 消费入口（兄弟父任务）

## Acceptance Criteria

- [ ] TenantIamProvisioningService 暴露单一公开入口 provisionTenantIam(TenantIamDesiredState, CorrelationId)
- [ ] 入口在本地状态不存在时初始化为 PENDING_IAM 后转入 IAM_PROVISIONING
- [ ] 入口在本地状态为 IAM_FAILED 时从 Desired State 重新执行 Step Pipeline，并复用同一 tenantId 的 state 记录
- [ ] 入口在本地状态已为 IAM_PROVISIONED 时返回幂等成功，不重复调用 Step Pipeline
- [ ] 任一 Step 拒出领域错误时服务捕获并调用 markFailed，写入 failureCode、failureMessage 并自增 retryCount
- [ ] 所有状态转换与 Step 调用都经由 TenantIamStateRepository 持久化并在调用前后记录结构化日志（含 tenantId、correlationId、当前状态、步骤名）
- [ ] 服务单元测试使用 Step 接口的测试替身验证成功、失败、重试、已完成四种路径
- [ ] 日志不输出 secret、临时密码或 Desired State 中的敏感凭证字段

## Context

| Field | Value |
|-------|-------|
| category | development |
| complexity | 6 |
| skillReferences | Hamster Blueprint |

