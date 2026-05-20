# back-office-outbound-messaging (delta)

## ADDED Requirements

### Requirement: AsyncAPI schema is enforced by Schema Registry compatibility checks

The canonical `OrderExecutedV1` schema registered in Redpanda Schema Registry SHALL act as the registry-level compatibility gate for future changes to the outbound messaging contract. Any proposed change to `OrderExecutedV1.json` that would break `BACKWARD` compatibility SHALL be rejected by the registry before it can be deployed. This extends the existing contract-first rule from test-time-only validation to infrastructure-level enforcement.

#### Scenario: BACKWARD-compatible schema evolution is accepted

- **WHEN** a new optional field is added to `OrderExecutedV1.json` and re-registered
- **THEN** the Schema Registry accepts the new version under subject `mmx.order.executed-value`

#### Scenario: BACKWARD-incompatible change is rejected

- **WHEN** a required field is removed from `OrderExecutedV1.json` and registration is attempted
- **THEN** the Schema Registry rejects the registration with a compatibility error before the file can be deployed
