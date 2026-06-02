package io.datahub.platform.iamprovisioning.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datahub.platform.iamprovisioning.domain.model.TenantIamDesiredState;
import io.datahub.platform.iamprovisioning.domain.valueobject.AdminUser;
import io.datahub.platform.iamprovisioning.domain.valueobject.CorrelationId;
import io.datahub.platform.iamprovisioning.domain.valueobject.Email;
import io.datahub.platform.iamprovisioning.domain.valueobject.IdentityMode;
import io.datahub.platform.iamprovisioning.domain.valueobject.IdentityProviderConfig;
import io.datahub.platform.iamprovisioning.domain.valueobject.PolicyConfig;
import io.datahub.platform.iamprovisioning.domain.valueobject.RealmStrategy;
import io.datahub.platform.iamprovisioning.domain.valueobject.RequiredLoginAction;
import io.datahub.platform.iamprovisioning.domain.valueobject.TemporaryCredentialPolicy;
import io.datahub.platform.iamprovisioning.domain.valueobject.TemporaryCredentialType;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantId;
import io.datahub.platform.iamprovisioning.domain.valueobject.TenantName;
import io.datahub.platform.iamprovisioning.domain.valueobject.Tier;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshotRow;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.model.TenantIamProvisioningInputSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TenantIamProvisioningInputSnapshotDomainMapper {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public TenantIamProvisioningInputSnapshotDomainMapper() {
        this(new ObjectMapper());
    }

    public TenantIamProvisioningInputSnapshotDomainMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public TenantIamProvisioningInputSnapshot toDomain(TenantIamProvisioningInputSnapshotRow row) {
        Objects.requireNonNull(row, "row must not be null");
        ensureSupportedSchemaVersion(row.schemaVersion());

        DesiredStatePayloadV1 payload = readPayload(row.desiredState());

        return new TenantIamProvisioningInputSnapshot(
                TenantId.of(row.tenantId()),
                CorrelationId.of(row.correlationId()),
                row.schemaVersion(),
                toDesiredState(payload)
        );
    }

    public TenantIamProvisioningInputSnapshotRow toRow(TenantIamProvisioningInputSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        ensureSupportedSchemaVersion(snapshot.schemaVersion());

        return new TenantIamProvisioningInputSnapshotRow(
                snapshot.tenantId().value(),
                snapshot.correlationId().value(),
                snapshot.schemaVersion(),
                writePayload(toPayload(snapshot.desiredState()))
        );
    }

    private TenantIamDesiredState toDesiredState(DesiredStatePayloadV1 payload) {
        return new TenantIamDesiredState(
                TenantId.of(payload.tenantId()),
                TenantName.of(payload.tenantName()),
                Tier.of(payload.tier()),
                AdminUser.of(
                        Email.of(payload.adminUser().email()),
                        new TemporaryCredentialPolicy(
                                TemporaryCredentialType.valueOf(payload.adminUser().temporaryCredentialPolicy().credentialType()),
                                toRequiredActions(payload.adminUser().temporaryCredentialPolicy().requiredActions())
                        )
                ),
                IdentityMode.valueOf(payload.identityMode()),
                RealmStrategy.valueOf(payload.realmStrategy()),
                payload.identityProviders() == null ? List.of() : payload.identityProviders(),
                payload.policies() == null ? List.of() : payload.policies()
        );
    }

    private DesiredStatePayloadV1 toPayload(TenantIamDesiredState desiredState) {
        Objects.requireNonNull(desiredState, "desiredState must not be null");

        return new DesiredStatePayloadV1(
                desiredState.tenantId().value(),
                desiredState.tenantName().tenantName(),
                desiredState.tier().value(),
                new AdminUserPayloadV1(
                        desiredState.adminUser().email().value(),
                        new TemporaryCredentialPolicyPayloadV1(
                                desiredState.adminUser().temporaryCredentialPolicy().credentialType().name(),
                                desiredState.adminUser().temporaryCredentialPolicy().requiredActions().stream()
                                        .map(Enum::name)
                                        .collect(Collectors.toUnmodifiableSet())
                        )
                ),
                desiredState.identityMode().name(),
                desiredState.realmStrategy().name(),
                desiredState.identityProviders(),
                desiredState.policies()
        );
    }

    private DesiredStatePayloadV1 readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, DesiredStatePayloadV1.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize TenantIamDesiredState snapshot payload", e);
        }
    }

    private String writePayload(DesiredStatePayloadV1 payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize TenantIamDesiredState snapshot payload", e);
        }
    }

    private Set<RequiredLoginAction> toRequiredActions(Set<String> actions) {
        if (actions == null) {
            return Set.of();
        }

        return actions.stream()
                .map(RequiredLoginAction::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void ensureSupportedSchemaVersion(int schemaVersion) {
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported TenantIamDesiredState snapshot schema version: " + schemaVersion);
        }
    }

    record DesiredStatePayloadV1(
            String tenantId,
            String tenantName,
            String tier,
            AdminUserPayloadV1 adminUser,
            String identityMode,
            String realmStrategy,
            List<IdentityProviderConfig> identityProviders,
            List<PolicyConfig> policies
    ) {
    }

    record AdminUserPayloadV1(
            String email,
            TemporaryCredentialPolicyPayloadV1 temporaryCredentialPolicy
    ) {
    }

    record TemporaryCredentialPolicyPayloadV1(
            String credentialType,
            Set<String> requiredActions
    ) {
    }
}
