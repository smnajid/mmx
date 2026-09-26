# Handoff — client-institution-onboarding (session 2, 2026-09-26)

Progress: groups 1–8 done (67/81). Next: group **9** (scripts and dev stack), then 10 (frontend), 11 (docs,
Serena memories), 12 (final verification).

## State
- Full `cd backend && mvn test` is green (all modules, including bootstrap `integration` and `e2e`
  Testcontainers suites). `openspec validate client-institution-onboarding --strict` passes.
- **Not run:** frontend tests / `npm run typecheck` (group 10 regenerates and uses the new types).
- Tip: `export MAVEN_OPTS="-Xmx1g"` — an uncapped run was OOM-killed (exit 137).

## Decisions taken in session 2 (review / confirm)
- `DelegatedGrantDirectory` keeps the operation rule in default methods (`resolveTenor/resolveNotice(…, op)`);
  adapters implement only `lookupTenor/lookupNotice`. New `GrantResolution.NOT_REQUIRED` + `isPermitted()`.
- Remote client (`RemoteRoutedOrderIntake`) checks only client-owned facts (onboarded, open, account, client
  enablement); the hub's leg-A grant check completes the effective enablement. No live grant read at intake.
- Order-creation options check the linked hub institution's account/open state only when that institution is
  stored locally (same-Organisation); a remote client cannot see hub accounts — the hub enforces at leg-A.
- `RemoteInstitutionRepository` became `RemoteHubInstitutionCatalog`; the client's `InstitutionRepository` is
  local JPA in every deployment (D7).
- Settings use cases are wrapped by `Transactional*UseCase` beans in `mmx-bootstrap` (the export outbox
  adapter is `Propagation.MANDATORY`).
- Single-institution REST responses carry `enablements[]` for onboarded institutions; the list does not.
- **Bug fixed (surfaced by 8.5):** `MoneyMarketOrder.propagateExecutionFromHub` stamped the hub-native
  institution code on the client-side execution, so a routed client Decrease/Redemption could not match its
  source Subscription. The client order now keeps its own onboarded institution code.
- `RoutedClientFixture` (bootstrap test support) converges the shared rest-test database to the routed baseline.

## Independent review (session 2) — applied
- **Leg-A accept hardened:** only the hub's own native institutions are accepted; a Decrease/Redemption
  needs the grant to exist in some state (revoked is fine, never-granted is rejected). Spec `order-routing`
  updated.
- **Remote lifecycle rule:** `IntakeService` applies the source-contract institution match before leg A on the
  remote path too (the routed path must not bypass it).
- **Scoped read:** `GET /settings/institutions/{code}` returns only institutions owned by the active scope
  (`ManageInstitutionSettingsUseCase.getInScope`), else 404; documented in the 004 contract.
- **Hub intake ownership:** a TradingHub order must reference that hub's own native institution.
- **One rule (D2):** `OrderAgainstInstitutionPolicy.refusal(...)` is the closed-to-new-business + account
  rule used by routed intake (both sides), leg-A accept and the options support; the options support uses
  `EffectiveEnablement` (D9). Design text updated.
- **Parity:** options carve-out for a hub institution not stored locally (spec + D7); enablement replacement
  wording ("newly switched on" must be granted) in the 004 contract and the grants spec; `enablements[]`
  documented as single-institution responses only; `clientCounterpartyAccount` must be non-blank (007
  contract pattern → 400).
- Remaining "proxy" identifiers renamed.

## Known, deferred (non-blocking)
- Two concurrent exported-state changes on one institution collide on the outbox `(le, code, version)` key:
  one rolls back correctly but surfaces as 500 (unmapped `DataIntegrityViolationException`); map to 409.
- Clearing an account and enabling a tenor of that OrderType can race (separate read-check-write); the intake
  account check is the backstop.
- Onboarding / re-onboarding without a grant returns 400 with the grants error schema, not the 004 one.
- Options decide "remote hub" by absence of the local hub row rather than `HubLocalityResolver`, and do not use
  the remote catalog's `active` flag.
- `npm run verify:contracts` does not generate the widget types it typechecks (`generate:api:widget` first).
