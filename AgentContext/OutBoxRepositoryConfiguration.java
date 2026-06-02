package io.datahub.platform.iamprovisioning.config.database;

import io.datahub.platform.iamprovisioning.infrastructure.persistence.InMemoryOutboxRepository;
import io.datahub.platform.iamprovisioning.infrastructure.persistence.PostgreOutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OutBoxRepositoryConfiguration {

    private final JdbcTemplate jdbcTemplate;

    public OutBoxRepositoryConfiguration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Bean
    @ConditionalOnProperty(
            name = "cdp.postgre.enabled",
            havingValue = "false"
    )
    public InMemoryOutboxRepository inMemoryOutboxRepository() {
        return  new InMemoryOutboxRepository();
    }


    @Bean
    @ConditionalOnProperty(
            name = "cdp.postgre.enabled",
            havingValue = "true"
    )
    public PostgreOutboxRepository postgreOutboxRepository() {
        return new PostgreOutboxRepository(jdbcTemplate);
    }
}
