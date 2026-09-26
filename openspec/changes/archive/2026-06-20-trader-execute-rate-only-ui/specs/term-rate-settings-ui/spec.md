# term-rate-settings-ui Specification (delta)

## MODIFIED Requirements

### Requirement: Term rates UI does not surface rates on order screens

Term rate **management** UI (upload, review tree, day browser) MUST NOT appear on order detail screens. The trader execute form MAY display a **single proposed indicative rate** for the PM-chosen intake institution (fetched via order-creation counterparties APIs) — this is a execution aid, not a rate browse surface.

#### Scenario: Order detail has no term rate management UI

- **WHEN** the trader opens order details from a desk queue
- **THEN** Term rate upload/review controls are not shown on that screen

#### Scenario: Execute form may show proposed rate for intake institution

- **WHEN** the assignee opens the execute form on a Term order with intake `institutionCode` BNKCO
- **THEN** the form may pre-fill and label a proposed rate sourced from order-creation counterparties for BNKCO only (not a multi-institution rate table)
