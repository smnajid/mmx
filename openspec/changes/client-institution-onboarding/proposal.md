# Proposal: Client institution onboarding, counterparty accounts, and institution export

## Why

TradingClients hold institutions inconsistently today. A same-Organisation client can onboard thin proxies from its grants. A cross-Organisation client stores no institutions at all: it reads the hub's institutions live and orders directly on hub-native codes. The Deposits back office books each LegalEntity's contracts against that LegalEntity's own **counterparty accounts** per institution (BNP's accounts at LOC differ from BNP's accounts at PAR), and MMX neither holds nor sends them. A cross-Organisation ClientRepresentative also has no say over which granted institutions the client trades with. We are fixing this now, while the system is pre-production, so thin proxies can be converted without legacy-data constraints.

The domain language is settled in `CONTEXT.md`: **Granted institution**, **Institution onboarding**, **Onboarded institution**, **Institution offboarding**, **Closed to new business**, **Counterparty account**, **Institution export**, and **Counterparty account snapshot**. ADR 0008 records that a TradingClient owns its onboarded institutions and counterparty accounts, partially superseding ADR 0007.

## What Changes

- **Granted vs onboarded (client).** A delegated grant only makes a hub institution a *granted institution* for the client. The ClientRepresentative lists granted institutions and **onboards** the ones the client will trade, once per institution. Onboarding creates a client-owned **onboarded institution**, permanently linked to the hub institution. Its display name is the derived, non-editable `"{hub name} via {hub LegalEntityCode}"`. Only onboarded institutions are usable at client intake and in order-creation options.
- **BREAKING (cross-org, spec-level):** a remote (cross-Organisation) TradingClient **stores its onboarded institutions locally**. This replaces "the client stores zero institutions". Grants, currencies, and rates stay live reads from the hub.
- **Thin proxy retired.** Existing same-Organisation proxies are converted in place into onboarded institutions, with empty counterparty accounts. The term "proxy" leaves the specs and the UI.
- **Client enablement.** Per `(onboarded institution, currency)`, the ClientRepresentative switches granted tenors and notice periods on or off. It is opt-in (empty at onboarding), bounded by the grant, and stored client-side (the hub never sees it). New business uses the **effective enablement**, *grant ∩ client enablement*: a grant reduction caps it without erasing it, and a grant expansion never switches anything on. Switching a tenor off makes that scope closed to new business. This closes a gap: today the client screen only displays the grant.
- **Counterparty accounts.** Each `(LegalEntity, Institution)` holds exactly one **Term counterparty account** and one **OnCall counterparty account** (no currency). This applies to hub-native and client-onboarded institutions alike. The Trader maintains them on a TradingHub and the ClientRepresentative on a TradingClient. An order is refused at intake (and, for a routed order, as a routing failure at the hub) when either LegalEntity's institution lacks the counterparty account for the order's OrderType. This applies to all four operations.
- **Closed to new business.** Institution offboarding (client), grant revocation or reduction, and hub-institution deactivation all refuse **Subscription** and **Increase**, while **Decrease** and **Redemption** against existing contracts stay accepted, routed, and booked. **BREAKING (behaviour):** today a revoked grant rejects every routed order, Redemption included, both at client intake and at the hub's leg-A accept. Offboarding is reversible: re-onboarding reopens the same record.
- **Institution export (new async contract).** MMX publishes each LegalEntity's institution state, including counterparty accounts and the closed-to-new-business flag, to that LegalEntity's own back-office instance through the transactional outbox. It is published on onboarding and re-onboarding, on account changes, on offboarding, and on hub deactivation or reactivation.
- **Counterparty account snapshot on execution.** `OrderExecutedV1` gains the counterparty account for the order's OrderType as it stood at execution. For a routed trade, the client's account is snapshotted at routing, carried on Leg A (remote) or copied in-process (local), stored read-only on the hub-side order, and returned in the routing-context block as `clientCounterpartyAccount`. These are backward-compatible optional additions to `OrderExecutedV1`.
- **Seed and test data.** Dev seeds, the cross-org stack, and test fixtures gain counterparty accounts and onboarded client institutions. No legacy proxy data is preserved beyond the in-place conversion.

## Capabilities

### New Capabilities

- `institution-counterparty-accounts`: Term and OnCall counterparty accounts per `(LegalEntity, Institution)`, covering who maintains them, the rule that client enablement of an OrderType requires its account, and the rule that an order is refused when its OrderType's account is missing.

### Modified Capabilities

- `institution-onboarding`: the client list shows granted and onboarded institutions; onboarding and offboarding are ClientRepresentative actions, and re-onboarding is allowed; hub deactivation now means closed to new business; the proxy wording is removed.
- `delegated-institution-grants`: a grant makes an institution *granted*, not usable; a client-controlled **client enablement** subset of the grant is added; "thin-proxy institution" is replaced by "onboarded institution"; revocation and deactivation now mean closed to new business rather than a total block; the directory is keyed by the onboarded institution.
- `order-institution-constraints`: client intake requires an onboarded institution; Subscription and Increase are refused on a closed-to-new-business institution, while Decrease and Redemption are still accepted; the lifecycle match uses the onboarded institution code.
- `order-routing`: a remote client stores its onboarded institutions (grants and rates are still read live); the hub's leg-A grant check lets Decrease and Redemption through on a revoked grant; the routing field mapping and routing-context block carry the client counterparty account snapshot.
- `back-office-outbound-messaging`: `OrderExecutedV1` counterparty account snapshot fields; a new outbox-backed **institution export** event per LegalEntity.
- `async-schema-registry-governance`: the institution export schema gets a single canonical file, is registered in the Schema Registry, and is validated in tests like `OrderExecutedV1`.
- `pm-order-creation-options`: client counterparties come from onboarded institutions; institutions closed to new business are excluded from Subscription options.

## Impact

- **Stack and boundaries:** Spring Boot 4 hexagonal backend (domain → application → adapters → bootstrap, enforced by ArchUnit) and the Angular 21 SPA. Business rules go in `mmx-domain` / `mmx-application`; controllers stay thin.
- **Contracts (contract-first, same delivery):**
  - `contracts/004-institution-settings/openapi.yaml` + `api-v1.md`: granted list, onboard/offboard, counterparty accounts.
  - `contracts/006-delegated-institution-grants/`: wording and semantics.
  - `contracts/002-trader-orders-views/schemas/OrderExecutedV1.json` + AsyncAPI mirror: snapshot fields.
  - A new institution export JSON schema and AsyncAPI channel.
  - The cross-org routing request contract (Leg A): client counterparty account.
  - Frontend types are regenerated with `npm run generate:api`.
- **Persistence:** new Flyway migration(s) for:
  - client enablement per `(institution, currency)`;
  - counterparty accounts and the closed-to-new-business state on institutions;
  - converting proxies into onboarded institutions;
  - the client counterparty account snapshot on hub-side orders;
  - an outbox event type for the institution export.
- **Cross-org:** the CGEG client deployment writes institutions locally instead of reading them through `RemoteInstitutionRepository`. The hub's `AcceptRoutedHubOrderService` grant check becomes operation-aware.
- **Back office (external):** Deposits must consume the new institution export and the new optional `OrderExecutedV1` fields. There is no MMX dependency on Deposits reading MMX.
- **TDD:** all behavioural work follows strict red-green-refactor per `AGENTS.md`, with no waiver.
- **Docs:** `CONTEXT.md` and ADR 0008 are already updated. `docs/agents/codebase-map.md` and the Serena memories must be updated where modules or flows move.
