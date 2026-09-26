# Handoff — client-institution-onboarding (session 1, 2026-09-26)

Progress: groups 1–3 done; group 4 tasks 4.1–4.4 done (27/81). Next: task **4.5**.

## State
- Reactor compiles (`mvn install -DskipTests`). Green: all `fast|architecture` tests in every module,
  persistence integration (64), bootstrap `integration` (52). **Not run:** `e2e`, frontend tests.
- Tip: `export MAVEN_OPTS="-Xmx1g"` — an uncapped run was OOM-killed (exit 137).

## Temporary scaffolding to replace (search `TODO(client-institution-onboarding`)
- `InstitutionSettingsController`: `listGrantedInstitutions`, `updateCounterpartyAccounts`,
  `updateClientEnablement` throw `UnsupportedOperationException` (tasks 7.1/7.2).
- `InstitutionSettingsModuleConfiguration`: `InstitutionExportOutbox` is a **no-op** lambda (task 6.2 →
  real `InstitutionExportOutboxAdapter`); `HubInstitutionCatalog` is in-process over `InstitutionRepository`
  (task 8.1 → remote on CGEG, client `InstitutionRepository` local JPA per D7).
- Order paths still use the **old** exists+active institution check (inlined in `IntakeService`,
  `ExecuteOrderService`); `RoutedOrderIntake`/`RemoteRoutedOrderIntake`/`AcceptRoutedHubOrderService` are
  unchanged apart from the proxy→`Institution` rename. Group 5 drives `NewBusinessPolicy`,
  `CounterpartyAccountPolicy`, `OrderAgainstInstitutionPolicy.validateExecute(code, opt, op, type)` in red-first.
- `RoutedHubOrderDraft` has a convenience ctor without `clientCounterpartyAccount` (production callers
  must pass it in group 5); `MoneyMarketOrder.execute(...)` has an overload taking `counterpartyAccount`.

## Decisions taken (review / confirm)
- `ThinProxyInstitution` + `ProxyInstitutionRepository` removed; `InstitutionRepository` gained
  `findOnboardedByLegalEntityCode` / `findOnboarded(owner, HubInstitutionLink)` (default impls; JPA overrides).
- `OnboardInstitutionUseCase.Result(institution, created)` replaces `OnboardedInstitution` (201 vs 200).
- `InstitutionListView.Proxies` renamed `Onboarded`.
- Deactivate/activate are now role-aware in `ManageInstitutionSettingsService` (ClientRepresentative =
  offboard/re-onboard; Trader on a client institution → 403; other owner → 404); the controller's
  `ReferenceDataMutationGuard.ensureTrader` was removed for these two endpoints.
- V27: `institution_export_outbox.payload` is `TEXT` (existing outbox precedent), not `JSONB` as in design D4;
  onboarded uniqueness uses a plain unique index (NULLs distinct = same effect as the partial index, runs on H2).
  A copy of V27 lives in `mmx-adapter-out-persistence/src/test/resources/db/migration/`.
- `ClientEnablement.replaceWith` validates only newly added values against the grant (an "enabled, not
  granted" value may be kept).
- New 004 error codes: `INSTITUTION_ALREADY_ONBOARDED`, `COUNTERPARTY_ACCOUNT_IN_USE`, `FORBIDDEN` —
  `GlobalExceptionHandler` mapping still to do (group 7).
- Shared application test fakes: `mmx-application/src/test/java/com/mmx/order/application/support/`.
