/**
 * Domain events raised by IAM provisioning aggregates.
 */
package io.datahub.platform.iamprovisioning.domain.service;

/**
 * 当前阶段 domain/service 可能暂时没用，可以空着，甚至先不放东西。
 *
 *   DDD 里的 domain service 不是“Service 后缀都放这里”，它只放一种东西：属于领域规则，但又不自然属于某个聚合/实体/值对象的方法。
 *
 *   在你当前 IAM Provisioning 里，大部分逻辑其实不该放 domain service：
 *
 *   TenantIamProvisioningService
 *   放 application/service。它是用例编排：加载状态、保存 checkpoint、调用 Step、处理失败、重试。
 *
 *   EnsureOrganizationStep / EnsureAdminUserStep
 *   放 application/pipeline/step。它们是 provisioning 流程步骤，调用 Port，不是纯领域规则。
 *
 *   KeycloakAdminPort
 *   放 application/port/out。它是外部系统边界。
 *
 *   TenantIamProvisioningState.markInProgress() / markCompleted() / markAwaitRetry()
 *   放 domain model。因为这是聚合自己的状态机规则。
 *
 *   AdminUser / TemporaryCredentialPolicy / TenantIamDesiredState
 *   放 domain model/valueobject。因为它们表达领域事实。
 *
 *   什么时候 domain/service 才有用？
 *
 *   比如以后你有这种规则：
 *
 *   public class TenantIamDesiredStateFactory {
 *       public TenantIamDesiredState deriveFrom(TenantInfrastructureProvisionedEvent event) {
 *           ...
 *       }
 *   }
 *
 *   如果它只做领域输入到领域模型的推导，不依赖 Kafka、不依赖 Spring、不依赖 Repository，可以考虑放 domain service。
 *
 *   或者：
 *
 *   public class ProvisioningFailureClassifier {
 *       public FailureDecision classify(IamProvisioningException exception, TenantIamProvisioningState state) {
 *           ...
 *       }
 *   }
 *
 *   如果失败分类规则变复杂，并且是领域概念，而不是应用层技术处理，也可以考虑 domain service。
 *
 *   但现在不建议为了“填满目录”放东西进去。当前更清晰的边界是：
 *
 *   domain/
 *     model/              聚合、实体
 *     valueobject/        值对象
 *     exception/          领域规则异常
 *     service/            先空着，等真正有跨聚合领域规则再用
 *
 *   application/
 *     service/            TenantIamProvisioningService
 *     pipeline/           Step 接口、StepExecutionContext
 *     pipeline/step/      EnsureXxxStep
 *     port/in/            ProvisionTenantIamUseCase
 *     port/out/           Repository / KeycloakAdminPort / EventPublisher
 *
 *   一句话：domain service 不是必需层。没有合适的领域规则，就不要用。
 */