package io.datahub.platform.tenantmanagement.application;

import java.util.UUID;

public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant not found: " + tenantId);
    }
}
