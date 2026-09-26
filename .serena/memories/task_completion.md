# Task completion checklist

1. **Scoped tests during work** (per change type, see AGENTS.md table):
   - domain change → `cd backend && mvn test -pl mmx-domain -Dtest=<Class>`
   - application change → `mvn test -pl mmx-application -Dtest=<Class>`
   - REST contract change → `mvn test -pl mmx-adapter-in-rest -Dtest=<ControllerTest>` + the one matching `mmx-bootstrap` integration class
   - persistence change → `mvn test -pl mmx-adapter-out-persistence -am -Dtest='<Jpa...Test>'`
2. **Final backend verification**: single full-reactor run `cd backend && mvn test` (all categories). Do NOT run the full reactor mid-change.
3. **Frontend** (if touched): `cd frontend && npm test` (Vitest) and `npm run typecheck`. Cypress e2e only on demand with the app running.
4. **SDD parity**: update matching `openspec/specs/*/spec.md` + `contracts/<feature>/openapi.yaml` + `api-v1.md` for material changes; run `openspec validate`.
5. **Frontend contract changes**: `npm run verify:contracts` (regenerates types + typecheck) — committed code must compile against regenerated types.
6. If ArchUnit-relevant (new dependency direction, new adapter) → run `DomainArchitectureTest` + `HexagonalArchitectureTest`.
