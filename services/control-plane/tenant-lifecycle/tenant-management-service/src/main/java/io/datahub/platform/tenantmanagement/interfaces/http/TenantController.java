package io.datahub.platform.tenantmanagement.interfaces.http;

import io.datahub.platform.tenantmanagement.application.CreateTenantCommand;
import io.datahub.platform.tenantmanagement.application.TenantApplicationService;
import io.datahub.platform.tenantmanagement.application.UpdateTenantStatusCommand;
import io.datahub.platform.tenantmanagement.domain.Tenant;
import io.datahub.platform.tenantmanagement.domain.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantApplicationService tenantApplicationService;

    public TenantController(TenantApplicationService tenantApplicationService) {
        this.tenantApplicationService = tenantApplicationService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantApplicationService.createTenant(new CreateTenantCommand(
                request.tenantName(),
                request.tier(),
                request.region(),
                request.planConfig(),
                request.contractEndAt()
        ));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{tenantId}")
                .buildAndExpand(tenant.tenantId())
                .toUri();

        return ResponseEntity.created(location).body(TenantResponse.from(tenant));
    }

    @GetMapping("/{tenantId}")
    public TenantResponse getTenant(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenantApplicationService.getTenant(tenantId));
    }

    @PatchMapping("/{tenantId}/status")
    public TenantResponse updateTenantStatus(@PathVariable UUID tenantId,
                                             @Valid @RequestBody UpdateTenantStatusRequest request) {
        return TenantResponse.from(tenantApplicationService.updateTenantStatus(
                new UpdateTenantStatusCommand(tenantId, request.status())
        ));
    }

    @GetMapping("/{tenantId}/context")
    public TenantContextResponse getTenantContext(@PathVariable UUID tenantId) {
        TenantContext context = tenantApplicationService.getTenantContext(tenantId);
        return new TenantContextResponse(
                context.tenantId(),
                context.tier(),
                context.status(),
                context.region(),
                context.planConfig()
        );
    }
}
