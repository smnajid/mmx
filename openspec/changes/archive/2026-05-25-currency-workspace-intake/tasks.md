## 1. Spec and contract

- [x] 1.1 Update `specs/003-managed-currency-settings/contracts/openapi.yaml` (minItems 0, workspace rule in descriptions)
- [x] 1.2 Update `specs/003-managed-currency-settings/contracts/api-v1.md`

## 2. Domain and application

- [x] 2.1 Relax `ManagedCurrency` validation (allow empty tenor or notice set; require at least one workspace)
- [x] 2.2 Add `ManagedCurrencyTest` for Term-only, OnCall-only, both-empty reject
- [x] 2.3 Update `ManageCurrencySettingsServiceTest` (Term-only update OK; both-empty reject)
- [x] 2.4 Add `OrderAgainstCurrencyPolicyTest` for Term-only / OnCall-only intake

## 3. REST adapter

- [x] 3.1 `CurrencySettingsRestMapper`: PATCH `[]` clears workspace; onboard maps empty arrays
- [x] 3.2 Update `CurrencySettingsControllerTest`

## 4. Frontend

- [x] 4.1 Edit component: workspace toggle guards and save validation
- [x] 4.2 Vitest: Term-only save / toggle behaviour
- [x] 4.3 List rules summary labels Term vs OnCall (optional clarity)

## 5. Verification

- [x] 5.1 Backend tests green (`ManageCurrencySettingsServiceTest`, policy, controller)
- [x] 5.2 `npm run test` in `frontend/` green
