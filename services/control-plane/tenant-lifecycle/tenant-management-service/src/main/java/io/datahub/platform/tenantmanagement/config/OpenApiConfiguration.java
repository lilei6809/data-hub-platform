package io.datahub.platform.tenantmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI tenantManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Tenant Management Service API")
                .version("v1")
                .description("Control Plane source of truth for tenant metadata"));
    }
}
