# Tenant Management Service

Source of truth for tenant metadata in the Control Plane.

## Responsibilities

- create tenant records
- expose tenant details and lightweight tenant context
- update tenant lifecycle status
- publish tenant lifecycle events through a service-local publisher abstraction

## API

- `POST /api/v1/tenants`
- `GET /api/v1/tenants/{tenantId}`
- `PATCH /api/v1/tenants/{tenantId}/status`
- `GET /api/v1/tenants/{tenantId}/context`

## Notes

- persistence uses `Spring Data JDBC`
- database migrations use `Flyway`
- tenant metadata is stored in the service database, not in per-tenant schemas
- downstream event publishing is defined behind an interface and currently uses a no-op implementation
