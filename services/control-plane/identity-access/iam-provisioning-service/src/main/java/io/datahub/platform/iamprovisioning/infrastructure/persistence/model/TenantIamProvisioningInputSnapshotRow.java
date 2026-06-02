package io.datahub.platform.iamprovisioning.infrastructure.persistence.model;

public record TenantIamProvisioningInputSnapshotRow(
        String tenantId,
        String correlationId,
        int schemaVersion,
        String desiredState
) {
}
