/**
 * Domain events raised by IAM provisioning aggregates.
 */
package io.datahub.platform.iamprovisioning.application.port;

/**
 * 那 port 除了考虑 in, out, 也要考虑是南北流量还是东西流量吧
 *
 * ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
 *
 * • 对，要考虑。但我建议把它理解成两个正交维度：
 *
 *   维度 1：Hexagonal 方向
 *   - in  port：谁来驱动我
 *   - out port：我需要调用谁
 *
 *   维度 2：流量性质
 *   - north-south：外部用户 / 网关 / API / 外部系统进出服务
 *   - east-west：服务内部之间 / 服务到服务 / 服务到基础设施组件
 *
 *   不要把它们混成一个目录维度，否则包会变复杂。
 *
 *   对 iam-provisioning-service 来说：
 *
 *   application/port/in
 *
 *   表示“外部驱动这个服务的用例入口”。
 *
 *   里面可以有：
 *
 *   ProvisionTenantIamUseCase
 *
 *   它可能被不同 inbound adapter 调用：
 *
 *   interfaces/http/              north-south：HTTP API 调用
 *   interfaces/messaging/         east-west：Kafka 事件触发
 *
 *   也就是说，同一个 in port 可以被南北流量 adapter 或东西流量 adapter 调用。
 *
 *   例如：
 *
 *   HTTP Controller
 *     -> ProvisionTenantIamUseCase
 *
 *   Kafka Consumer
 *     -> ProvisionTenantIamUseCase
 *
 *   所以不建议搞成：
 *
 *   application/port/in/northsouth
 *   application/port/in/eastwest
 *
 *   这个分类会让 application core 知道部署/通信拓扑，反而污染核心。
 *
 *   out port 也是一样：
 *
 *   application/port/out/keycloak/KeycloakAdminPort
 *   application/port/out/TenantIamStateRepository
 *   application/port/out/EventPublisher
 *
 *   它们按“业务依赖能力”分类，而不是按南北/东西分类。
 *
 *   对应 adapter 再体现流量性质：
 *
 *   infrastructure/keycloak/      east-west：调用 Keycloak
 *   infrastructure/persistence/   local infra：访问 DB
 *   infrastructure/messaging/     east-west：发布 Kafka 事件
 *   interfaces/http/              north-south：HTTP 入站
 *   interfaces/messaging/         east-west：Kafka 入站
 *
 *   所以推荐规则是：
 *
 *   application/port/in|out       按驱动方向 + 业务能力命名
 *   interfaces/infrastructure     按技术适配器 + 流量性质落地
 *
 *   放到你这个服务里，比较稳的结构是：
 *
 *   application/
 *     port/
 *       in/
 *         ProvisionTenantIamUseCase.java
 *       out/
 *         TenantIamStateRepository.java
 *         EventPublisher.java
 *         keycloak/
 *           KeycloakAdminPort.java
 *     service/
 *       TenantIamProvisioningService.java
 *
 *   interfaces/
 *     http/
 *       TenantIamProvisioningController.java       # north-south inbound
 *     messaging/
 *       TenantInfrastructureProvisionedConsumer.java # east-west inbound
 *
 *   infrastructure/
 *     keycloak/
 *       KeycloakAdminAdapter.java                  # east-west outbound
 *     messaging/
 *       KafkaEventPublisher.java                   # east-west outbound
 *     persistence/
 *       JdbcTenantIamStateRepository.java          # persistence outbound
 *
 *   一句话：port 先按 in/out 和业务能力建模；南北/东西流量主要体现在 adapter 层，而不是 application port 包结构里。
 */