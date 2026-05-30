# Plan — Institution settings (004)

## Stack

- **Contracts**: `specs/004-institution-settings/contracts/openapi.yaml` + `api-v1.md`
- **Execute delta**: `specs/002-trader-orders-views/contracts/openapi.yaml` (`institutionCode` on execute request)
- **Backend**: hexagonal modules (`mmx-domain`, `mmx-application`, `mmx-adapter-out-persistence`, `mmx-adapter-in-rest`, `mmx-bootstrap`)
- **Frontend**: Angular settings hub under `/settings/**`; institution feature + execute autocomplete

## Implementation order

1. Flyway `institution` + optional `institution_code` on orders
2. Domain: `Institution`, `InstitutionCodeAcronym`, `OrderAgainstInstitutionPolicy`
3. Application: `InstitutionRepository`, `ManageInstitutionSettingsService`, extend `ExecuteOrderService`
4. REST: codegen from 004 OpenAPI; `InstitutionSettingsController`; 002 execute mapper delta
5. Frontend: settings shell (Currencies | Institutions); institution CRUD UI; execute picker

## Commands

```bash
cd backend && mvn test
cd frontend && npm run test
```

## References

- OpenSpec change: `openspec/changes/institution-onboarding-rates/`
- Currency settings pattern: `specs/003-managed-currency-settings/`
