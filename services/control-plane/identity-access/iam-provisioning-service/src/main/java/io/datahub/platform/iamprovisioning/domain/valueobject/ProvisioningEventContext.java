package io.datahub.platform.iamprovisioning.domain.valueobject;

public record ProvisioningEventContext(
        Tier tier,
        Email adminEmail,
        CorrelationId correlationId
) {



    public static ProvisioningEventContext of(Tier tier, Email adminEmail, CorrelationId correlationId) {
        return new ProvisioningEventContext(tier, adminEmail, correlationId);
    }
}
