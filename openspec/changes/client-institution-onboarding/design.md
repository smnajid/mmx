# Design: Client institution onboarding, counterparty accounts, and institution export

## Context

See `proposal.md` for motivation, and the delta specs for requirements. Current state that shapes the approach:

- **One aggregate, two shapes.** Hub-native institutions and same-org client proxies both live in the `institution` table (V11 + V20). A proxy is a row with non-null `hub_legal_entity_code` / `hub_institution_code`, modelled in the domain as `ThinProxyInstitution`. `institution_code` is the global PK. There is no per-LegalEntity active flag beyond row ownership.
- **Remote client stores nothing.** The CGEG deployment (`reference-data-remote: true`) registers `@Primary` read-only remote repositories for institutions and grants (`CrossOrgRoutingModuleConfiguration`). It orders directly on hub-native codes (`RemoteRoutedOrderIntake`).
- **Grant checks ignore the operation.** They happen in two places: `RoutedOrderIntake.resolveGrant` (local routing) and `AcceptRoutedHubOrderService.accept` (remote leg A). Neither looks at `OrderOperation`.
- **Existing outboxes.** `back_office_outbox` is order-keyed (unique `order_id` FK, one topic). The on-call precedent uses a separate table, port, and relay, with the event type carried in the payload.
- **No client-side enablement today.** Client intake validates straight against the grant's enabled set, and the client detail screen shows the grant read-only. This change adds a client-owned enablement layer (D9).

## Goals / Non-Goals

**Goals:**
- Make one `Institution` aggregate carry ownership, an optional hub link, counterparty accounts, and open/closed state, for hubs and clients in both deployment shapes.
- Put a single operation-aware rule, "adds exposure vs reduces exposure", in the domain and reuse it at every validation point.
- Make the institution export durable (outbox) and ordered per institution (a `version` field).

**Non-Goals:**
- Propagating a hub display-name change to already-onboarded client institutions. The derived name is fixed at onboarding.
- Changing the Deposits back office. It must adopt the new event and fields on its side.
- The global-account keying drift (`.scratch/.../09-unify-global-account-keying.md`).

## Decisions

### D1. One `Institution` aggregate; `ThinProxyInstitution` is folded in

`mmx-domain` `Institution` gains the following fields:

- `owningLegalEntityCode`;
- `Optional<HubInstitutionLink>`, holding `(hubLegalEntityCode, hubInstitutionCode)`;
- `CounterpartyAccounts`, holding `term` and `onCall` (both optional, value objects validated non-blank and at most 34 characters, the IBAN upper bound);
- `active`;
- `version`.

`closedToNewBusiness()` is derived as `!active`. For a hub institution, `active = false` means deactivated. For a client institution it means offboarded. The existing column keeps its meaning, and "closed to new business" becomes the domain name for it.

`ThinProxyInstitution` and its test are deleted, and the display-name derivation moves to a factory, `Institution.onboardFromGrant(...)`. Behaviour methods `offboard()`, `reopen()`, and `changeAccounts(...)` each return whether the exported state changed. That return value drives the "unchanged records nothing" rule.

*Alternative considered:* a separate `OnboardedInstitution` aggregate and table. Rejected because routing, rates lookup, lifecycle matching, and execute already treat both shapes as one `institution_code` space. Splitting them would double every lookup.

### D2. `NewBusinessPolicy` and `CounterpartyAccountPolicy` in the domain

Both policies live in `mmx-domain/.../policy/`:

- **`NewBusinessPolicy`** covers `addsExposure(OrderOperation)` (true for SUBSCRIPTION and INCREASE) and `requireOpenForNewBusiness(institution, operation)`.
- **`CounterpartyAccountPolicy`** covers `requireAccountFor(institution, OrderType)`, which returns the account used as the snapshot.

`OrderAgainstInstitutionPolicy.validateExecute` delegates to both.

They are called from:

- `IntakeService` (hub native);
- `RoutedOrderIntake` (local routing: client institution and hub institution);
- `RemoteRoutedOrderIntake` (client side, before leg A);
- `AcceptRoutedHubOrderService` (hub side);
- `ExecuteOrderService`.

*Why a domain policy:* five call sites across two use-case families. A single policy keeps the Q11c rule ("one rule for offboarding, revocation, deactivation") from drifting.

### D3. Operation-aware grant directory

`DelegatedGrantDirectory.resolveTenor/resolveNotice` gain an `OrderOperation` parameter. For reducing operations they return a permitted result without consulting the grant. `AcceptRoutedHubOrderService` skips the grant lookup and the closed-to-new-business check for reducing operations, but still enforces membership, institution existence, and the hub's counterparty account. The remote-client directory implementation (live read of `/cross-org/reference/grants`) gets the same parameter.

### D4. Persistence: V27 (forward-only; pre-production)

`V27__institution_counterparty_accounts_and_export.sql` does the following:

- `institution`: add `term_counterparty_account VARCHAR(34) NULL`, `oncall_counterparty_account VARCHAR(34) NULL`, and `version BIGINT NOT NULL DEFAULT 0`. Add a partial unique index on `(legal_entity_code, hub_legal_entity_code, hub_institution_code) WHERE hub_institution_code IS NOT NULL`, so there is at most one onboarded record per hub institution per client. Drop `fk_institution_hub_institution` and `fk_institution_hub_entity` (both from V20). In a remote client deployment neither the hub's institution row nor the hub's LegalEntity exists locally, and ADR 0007 forbids storing the foreign LegalEntity. The hub link becomes plain values, validated at onboarding against the granted list.
- `money_market_order`: add `counterparty_account VARCHAR(34) NULL` (the snapshot stamped at execute) and `client_counterparty_account VARCHAR(34) NULL` (hub-side routed orders, stamped at routing).
- New `client_institution_enablement` table: `legal_entity_code`, `institution_code` (FK to the client's own institution row), `currency`, and one boolean per tenor/notice (`tenor_1w`…`tenor_1y`, `notice_24h`, `notice_48h`), mirroring the grant table. Its PK is `(institution_code, currency)`. Absence of a row means nothing is enabled. Converted proxies start with no rows (opt-in).
- New `institution_export_outbox` table with `id`, `legal_entity_code`, `institution_code`, `version`, `payload JSONB`, `status`, `publish_attempts`, `created_at`, and `last_attempt_at`. Add a unique constraint on `(legal_entity_code, institution_code, version)`.

The in-place proxy conversion needs no data migration: the rows, codes, and hub links already exist, and accounts start null.

*Why store snapshots on the order* rather than only in the outbox payload: the hub-side `client_counterparty_account` must survive from routing to execution, so it has to be an order column. Adding `counterparty_account` next to it gives an auditable, symmetric record.

### D5. Institution export: separate outbox, port, and relay (on-call precedent)

The pieces are:

- the application port `InstitutionExportOutbox.schedule(Institution, ChangeReason)`;
- the adapter `InstitutionExportOutboxAdapter` and mapper `InstitutionUpdatedV1PayloadMapper` in `mmx-adapter-out-messaging`;
- `InstitutionExportRelay`/`Worker`, which publish to `${mmx.institution.kafka.topic-prefix:mmx.institution}.{legalEntityCode}` with key `institutionCode`;
- the relay toggle `mmx.institution.outbox.relay-enabled`.

Services call the port in the same transaction as the institution save. `mmx-bootstrap` wraps the use cases in transactions, as it does today.

*Alternative considered:* generalising `back_office_outbox`. Rejected because of its unique `order_id` FK and single topic. Changing it would touch the proven order-handoff path for no gain.

*Why a topic per LegalEntity:* it enforces delivery "only to the owning LegalEntity's back office" at the broker, with no consumer-side filter, and it mirrors `mmx.routed-order-outcome.{orgCode}`. Schema governance uses a single subject, `mmx.institution-value`, registered by `scripts/register-schemas.sh`. MMX serialises JSON itself; the registry is a compatibility gate, not a serializer dependency, so per-topic subjects are not needed.

### D6. Contracts (contract-first order)

- **`contracts/004-institution-settings/openapi.yaml` + `api-v1.md`:**
  - `InstitutionResponse` gains `termCounterpartyAccount`, `onCallCounterpartyAccount`, `closedToNewBusiness`, and `hubLegalEntityCode`.
  - `OnboardInstitutionRequest` gains optional accounts. Its `hubInstitutionCode` branch returns 200 when it reopens an offboarded record and 201 when it creates one.
  - New `GET /api/v1/settings/institutions/granted` (`listGrantedInstitutions`).
  - New `PUT /api/v1/settings/institutions/{institutionCode}/counterparty-accounts` (`updateCounterpartyAccounts`), a full replacement where null clears.
  - New `PUT /api/v1/settings/institutions/{institutionCode}/enablement/{currency}` (`updateClientEnablement`); `InstitutionResponse.enablements[]` (D9).
  - For a ClientRepresentative, the existing `POST /{code}/deactivate` is **offboard** and `POST /{code}/activate` is **re-onboard** (grant required). This is documented in `api-v1.md` rather than added as new endpoints, which keeps the surface minimal.
- **`contracts/004-institution-settings/asyncapi.yaml` + `asyncapi-v1.md` + `schemas/InstitutionUpdatedV1.json`**, all new.
- **`contracts/002-trader-orders-views/schemas/OrderExecutedV1.json` + `asyncapi-v1.md`:** optional `institutionCode`, `counterpartyAccount`, and `clientCounterpartyAccount`.
- **`contracts/007-cross-org-routing/openapi.yaml` + `api-v1.md`:** `AcceptRoutedOrderRequest.clientCounterpartyAccount` is **required**. This is safe pre-production because the two deployments ship together. `/cross-org/reference/institutions` stays, as the source of hub display names for the granted list.
- **`contracts/006-delegated-institution-grants/api-v1.md`:** document the prose semantics (closed to new business; no proxy).

Codegen then regenerates the backend interfaces in `mmx-adapter-in-rest` and the frontend types (`npm run generate:api`).

### D7. Remote client wiring

In `CrossOrgRoutingModuleConfiguration`, the client deployment's `InstitutionRepository` becomes the local JPA one: `RemoteInstitutionRepository` is no longer `@Primary`. The hub-native catalog read moves behind a new read-only out-port, `HubInstitutionCatalog`. It is remote-backed on CGEG and in-process on the same-org deployment. `ListGrantedInstitutionsService` uses it to join grants with hub display names.

Grants and rates keep their remote adapters. Order-creation counterparty support for a remote client becomes the intersection of three sets: local open onboarded institutions with the account, live grants, and live rates keyed by the linked hub code.

### D8. Frontend

The following change under `frontend/src/app/features/institution-settings/`:

- **List:** a single "Institutions" heading, open/closed badge, accounts columns.
- **Onboard:** a client picks from `listGrantedInstitutions` and can enter optional accounts; a Trader keeps entering the name.
- **Detail:** an accounts form, plus offboard/re-onboard for a ClientRepresentative; the read-only grant panel becomes per-currency client-enablement toggles bounded by the grant, with "enabled, not granted" flagged.

`institution-settings-api.service.ts` gains the two new calls. Role gating stays on `TraderContextService`. Vitest specs sit next to each component.

### D9. Client enablement: stored, capped at read time

`mmx-domain` gains `ClientEnablement`, a value per `(institution, currency)` with a tenor set and a notice set, plus `EffectiveEnablement.of(grant, clientEnablement)`, which returns the intersection. The intersection is computed on every read and never stored.

A reduced grant therefore caps the effective set without touching the client's rows, and a restored grant brings the earlier choice back with no client action. Enabling is validated against the **current** grant and the counterparty-account gate (`CounterpartyAccountPolicy`). Clearing an account is refused while any tenor of that OrderType is enabled, in any currency.

The following are touched:

- a new `ManageClientEnablementUseCase` (application layer);
- `PUT /api/v1/settings/institutions/{institutionCode}/enablement/{currency}` (`updateClientEnablement`, full replacement of that currency's tenor/notice sets);
- `InstitutionResponse` gains `enablements[]`, one entry per granted currency with `grantedTenors`, `grantedNoticePeriods`, `enabledTenors`, and `enabledNoticePeriods`. A tenor in the enabled set but not the granted set is the UI's "enabled, not granted".

Client intake (local `RoutedOrderIntake`, remote `RemoteRoutedOrderIntake`) and the order-creation counterparty support check effective enablement for Subscription/Increase. The hub's `AcceptRoutedHubOrderService` is unchanged, because it validates the grant only.

*Alternative considered:* pruning client enablement when a grant shrinks. Rejected (Q20): a temporary hub reduction would silently wipe the client's configuration.

*Alternative considered:* storing enablement at the hub, next to the grant. Rejected because it is a client-owned fact (ADR 0008), and a remote client must not write to the hub's reference data.

### D10. Seed and test data

- **`RestTestInstitutionBootstrap`:** seeded institutions get both accounts.
- **SQL-seeding integration tests** (`CrossOrgClientDeploymentIntegrationTest`, `OrderRoutingIntakeIntegrationTest`, `RoutedExecutionHandoffKafkaIntegrationTest`, `OrderRestApiIntegrationTest`, `OrderCreationOptionsRestApiIntegrationTest`, `TermRateRestApiIntegrationTest`, `TransactionalOrderLifecycleAtomicityIntegrationTest`): insert accounts, and replace proxy wording with onboarded institutions.
- **`scripts/cross-org-smoke.sh`:** set LOC accounts, onboard at CGEG with accounts before intake, and use the CGEG onboarded code.
- **`scripts/seed-demo-orders.sh`:** set accounts.

## Risks / Trade-offs

- **[Risk] New-business check order.** Reducing operations bypass grant checks, so a Redemption could be routed on an institution the client never legitimately held. → Mitigation: the existing lifecycle rule requires an executed source Subscription on the same institution code, and that check runs first.
- **[Risk] Required leg-A field.** A remote client deployment on an older build would get 400 from a hub on this build. → Mitigation: pre-production, and both stacks deploy together (`mmx-cross-org-start.sh`). Note this in `api-v1.md`.
- **[Risk] Relay polling races Flyway.** A new relay polling in client-role Spring tests could race Flyway clean+migrate (known gotcha). → Mitigation: `mmx.institution.outbox.relay-enabled=false` in those tests, as done for the other relays.
- **[Trade-off] Stale hub names.** The derived client display name is frozen at onboarding, so a hub rename is not reflected. This is accepted (non-goal).
- **[Trade-off] `active` doubles as closed-to-new-business.** One column, two role-specific meanings. The API exposes both `active` (legacy) and `closedToNewBusiness` (domain name) until `active` can be retired.

## Migration Plan

1. Contracts first, then codegen.
2. V27 migration (additive; existing proxy rows become onboarded institutions untouched).
3. Deploy hub and client together. Accounts start empty, so orders are refused until accounts are set. Seeds and smoke scripts set them.

Rollback: pre-production. Revert the release and re-run `flyway clean migrate` on dev stacks. There is no production data to preserve.

## Open Questions

- Whether Deposits wants `hubInstitutionCode` on `OrderExecutedV1`'s routing context, as well as on the export, to map "BNP via LOC" contracts. It is additive and optional, so it can follow later without changing this design.
