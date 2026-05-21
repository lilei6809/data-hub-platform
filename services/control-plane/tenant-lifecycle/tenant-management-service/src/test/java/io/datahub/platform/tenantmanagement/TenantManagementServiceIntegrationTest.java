package io.datahub.platform.tenantmanagement;

import io.datahub.platform.tenantmanagement.infrastructure.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TenantManagementServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist tenant when create tenant endpoint is called")
    void shouldPersistTenantWhenCreateTenantEndpointIsCalled() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "Acme",
                                  "tier": "GROWTH",
                                  "region": "ap-southeast-1",
                                  "planConfig": "{\\"max_datasources\\":5}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("http://localhost/api/v1/tenants/.+")))
                .andExpect(jsonPath("$.tenantId").isNotEmpty())
                .andExpect(jsonPath("$.tenantName").value("Acme"))
                .andExpect(jsonPath("$.status").value("PROVISIONING"));

        assertThat(tenantRepository.findAll())
                .hasSize(1)
                .first()
                .extracting("tenantName", "region", "planConfig")
                .containsExactly("Acme", "ap-southeast-1", "{\"max_datasources\":5}");
    }
}
