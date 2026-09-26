# pm-order-creation-widget Specification

## ADDED Requirements

### Requirement: Playground submits orderReady payloads to mmx intake

The widget playground SHALL act as a development PM host: for each logged `orderReady` event, the user SHALL be able to submit the payload to mmx intake via `POST /api/v1/orders` using the playground's configured API base URL (same base as the embedded widget). The playground SHALL map `OrderCreationPayload` to `ReceiveOrderRequest` per the canonical intake contract (`operation` → `orderOperation`, include `institutionCode`, omit display-only `counterparty`). The playground SHALL generate an `externalOrderReference` at submit time. The widget library itself SHALL NOT perform submission.

#### Scenario: User sends a completed order to intake

- **WHEN** the widget emits `orderReady` in the playground and the user clicks Send to mmx
- **THEN** the playground POSTs a `ReceiveOrderRequest` to `{apiBaseUrl}/api/v1/orders` (or `/api/v1/orders` via dev proxy when api base is empty) and displays the returned `orderId` and HTTP status on success

#### Scenario: Submit uses institutionCode from the payload

- **WHEN** the user sends an `orderReady` payload that includes `institutionCode: 'BNKCO'`
- **THEN** the POST body includes `institutionCode: 'BNKCO'` and does not include `counterparty`

#### Scenario: Submit failure is surfaced

- **WHEN** intake returns `400 Bad Request` (e.g. validation error)
- **THEN** the playground displays the error message in the event log and allows the user to retry Send with a newly generated `externalOrderReference`

#### Scenario: Successful submit disables duplicate send

- **WHEN** intake returns `201 Created` for a playground submit
- **THEN** that event entry shows success with `orderId` and does not offer another Send for the same event
