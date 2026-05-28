package io.datahub.platform.iamprovisioning.interfaces.http;

/**
 * HTTP 请求体：手动触发 Tenant IAM Provisioning 所需的业务输入。
 *
 * <p>字段与 {@code TenantInfrastructureProvisionedEvent} 对齐：
 * tenantId 已在路径变量中，其余三个字段由请求体提供。</p>
 *
 * @param tenantName  租户显示名称，用于创建 Keycloak Organization
 * @param tier        租户等级（如 BASIC、PREMIUM），决定 IAM 配置策略
 * @param adminEmail  初始管理员邮箱
 */
public record ManualProvisionIamRequest(
        String tenantName,
        String tier,
        String adminEmail
) {}