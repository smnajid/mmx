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
