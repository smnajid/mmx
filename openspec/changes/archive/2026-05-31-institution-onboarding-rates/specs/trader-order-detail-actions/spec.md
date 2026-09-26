# trader-order-detail-actions Specification (delta)

## ADDED Requirements

### Requirement: Execute action uses institution picker policy

The order details feature SHALL treat **institution selection** as part of the execute flow for assignees. Action policy SHALL continue to allow **execute** only for the assigned trader on ASSIGNED orders; the execute form SHALL additionally require a catalog **institution** selection per `order-institution-constraints` (autocomplete over active institutions, submit `institutionCode`).

#### Scenario: Assignee sees institution execute input

- **WHEN** the loaded order status is ASSIGNED, `assignedTraderId` equals the current trader, and institutions exist in the catalog
- **THEN** the policy allows execute and the UI presents an institution picker (not free-text counterparty)

#### Scenario: Non-assignee does not execute

- **WHEN** the loaded order status is ASSIGNED and `assignedTraderId` differs from the current trader
- **THEN** the policy does not offer execute (unchanged from existing assignee rules)

#### Scenario: Execute hidden when catalog empty

- **WHEN** the loaded order status is ASSIGNED, the trader is the assignee, and the institution catalog is empty
- **THEN** execute is not offered as a submittable action (or is disabled with guidance to Settings)

---

### Requirement: Execute form labels use order vocabulary

The execute control group SHALL label the institution field using **counterparty** or equivalent order-context wording (e.g. “Counterparty”) while binding to catalog **`institutionCode`** under the hood, per ubiquitous language (Institution in Settings, Counterparty on orders).

#### Scenario: Label reflects counterparty on order screen

- **WHEN** the assignee opens the execute section on order details
- **THEN** the institution picker is labelled for traders as counterparty (or “Counterparty (institution)”) and not “Institution code”
