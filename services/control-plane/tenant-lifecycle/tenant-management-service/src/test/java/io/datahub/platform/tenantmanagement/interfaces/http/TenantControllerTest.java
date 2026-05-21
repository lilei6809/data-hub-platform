package io.datahub.platform.tenantmanagement.interfaces.http;

import io.datahub.platform.tenantmanagement.application.CreateTenantCommand;
import io.datahub.platform.tenantmanagement.application.TenantApplicationService;
import io.datahub.platform.tenantmanagement.domain.Tenant;
import io.datahub.platform.tenantmanagement.domain.TenantStatus;
import io.datahub.platform.tenantmanagement.domain.TenantTier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import(GlobalExceptionHandler.class)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantApplicationService tenantApplicationService;

    @Test
    void createTenantReturnsCreatedResponse() throws Exception {
        Tenant tenant = sampleTenant();
        when(tenantApplicationService.createTenant(ArgumentMatchers.any(CreateTenantCommand.class))).thenReturn(tenant);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName": "Acme",
                                  "tier": "GROWTH",
                                  "region": "ap-southeast-1",
                                  "planConfig": "{\\"max_datasources\\":5}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/tenants/" + tenant.tenantId()))
                .andExpect(jsonPath("$.tenantId").value(tenant.tenantId().toString()))
                .andExpect(jsonPath("$.status").value("PROVISIONING"));
    }

    @Test
    void getTenantReturnsTenantPayload() throws Exception {
        Tenant tenant = sampleTenant();
        when(tenantApplicationService.getTenant(tenant.tenantId())).thenReturn(tenant);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}", tenant.tenantId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantName").value("Acme"))
                .andExpect(jsonPath("$.tier").value("GROWTH"));
    }

    private Tenant sampleTenant() {
        return new Tenant(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Acme",
                TenantTier.GROWTH,
                TenantStatus.PROVISIONING,
                "ap-southeast-1",
                "{\"max_datasources\":5}",
                Instant.parse("2026-05-16T00:00:00Z"),
                null
        );
    }
}
