# Plan — Term rate settings (005)

## Stack

- **Contracts**: `specs/005-term-rate-settings/contracts/openapi.yaml` + `api-v1.md`
- **Backend**: hexagonal modules (`mmx-domain`, `mmx-application`, `mmx-adapter-out-persistence`, `mmx-adapter-in-rest`, `mmx-bootstrap`)
- **Frontend**: Angular settings hub — Term rates tab at `/settings/term-rates`

## Implementation order

1. OpenAPI + codegen (`005`)
2. Domain: `TermRate`, `Tenor.fromCode`, `TermRateIngestPolicy`
3. Application: CSV parser, sample generator, upload/query services
4. Flyway `term_rate` + JPA repository
5. REST: multipart upload, list, days, sample CSV
6. Frontend: Term rates screen + settings sub-nav

## Commands

```bash
cd backend && mvn test
cd frontend && npm run test
```

## References

- OpenSpec change: `openspec/changes/term-rate-daily-upload/`
- Depends on: `specs/004-institution-settings/`, `specs/003-managed-currency-settings/`
