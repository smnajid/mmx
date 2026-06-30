## MODIFIED Requirements

### Requirement: Inbound callback is unauthenticated for the POC

The accounted callback endpoint SHALL accept requests without authentication for the duration of this POC. The OpenAPI specification SHALL declare this explicitly via `security: []` on the operation, overriding any global security scheme. Production deployment SHALL add at minimum a shared secret or network-level allow-list (out of scope for this change).

#### Scenario: Unauthenticated request is processed

- **WHEN** the accounted callback is invoked without any authentication header
- **THEN** the system processes the request normally and applies the rules above (200 / 404 / 409)

#### Scenario: Trader-facing endpoints remain authenticated

- **WHEN** a request to a Trader-facing endpoint (e.g. `/api/v1/orders/term/executed`) arrives without `X-User-Id`
- **THEN** the system rejects it per the existing MMXUser authentication rules — the unauthenticated callback path does not weaken Trader endpoints
