package io.datahub.platform.iamprovisioning.interfaces.messaging.dto;

// 纯数据容器，Jackson 可以无需任何配置直接反序列化
// 字段命名与上游 BC 发布的 JSON schema 对齐
public record TenantInfrastructureProvisionedEventDto(
        String tenantId,
        String tenantName,
        String tier,
        String email,
        String correlationId,
        String occurredAt   // ISO-8601 格式字符串
) {
}
