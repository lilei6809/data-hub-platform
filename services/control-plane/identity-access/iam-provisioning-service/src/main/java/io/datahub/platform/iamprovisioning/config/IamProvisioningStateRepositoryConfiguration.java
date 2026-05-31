package io.datahub.platform.iamprovisioning.config;

import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryTenantIamProvisioningStateRepository;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.PostgreTenantIamProvisioningRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class IamProvisioningStateRepositoryConfiguration {

    private final JdbcTemplate jdbcTemplate;

    public IamProvisioningStateRepositoryConfiguration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    @ConditionalOnProperty(name = "cdp.postgre.enabled",
    havingValue = "true")
    public PostgreTenantIamProvisioningRepository postgreTenantIamProvisioningRepository() {
        return new PostgreTenantIamProvisioningRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "cdp.postgre.enabled",
            havingValue = "false")
    public InMemoryTenantIamProvisioningStateRepository inMemoryTenantIamProvisioningStateRepository() {
        return new InMemoryTenantIamProvisioningStateRepository();
    }
}
