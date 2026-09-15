# Suggested commands (Darwin/macOS)

Darwin zsh; standard GNU-ish unix tools behave as expected — no special-casing needed. Maven reactor root is `backend/` (always `cd backend` first for mvn). npm commands run from `frontend/`.

## Backend (scoped loops — prefer over full reactor)
```bash
cd backend && mvn test -Dgroups=fast                        # unit loop, no containers, all modules
cd backend && mvn test -Dgroups='fast|integration'          # compose
cd backend && mvn test -Dgroups=integration                 # PostgreSQL Testcontainers
cd backend && mvn test -Dgroups=e2e                         # Kafka / full workflow (on demand)
cd backend && mvn test -pl mmx-domain -Dtest=<Class>        # single domain class
cd backend && mvn test -pl mmx-application -Dtest=<Class>   # single application class
cd backend && mvn test -pl mmx-adapter-in-rest -Dtest=<ControllerTest> -DskipOpenApiGenerate=true  # fast REST unit iteration
cd backend && mvn test -pl mmx-adapter-out-persistence -am -Dtest='<Jpa...Test>'                   # persistence integration
cd backend && mvn -q -pl mmx-adapter-in-rest -am compile -DskipTests   # compile + OpenAPI codegen
cd backend && mvn test -pl mmx-domain -Dtest=DomainArchitectureTest    # ArchUnit domain rules
cd backend && mvn test -pl mmx-bootstrap -am -Dtest=HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Frontend
```bash
cd frontend && npm test            # Vitest unit (pretest regenerates API types)
cd frontend && npm run typecheck   # tsc --noEmit
cd frontend && npm run generate:api          # regenerate OpenAPI TS types
cd frontend && npm run verify:contracts      # generate:api + typecheck
cd frontend && npm run e2e         # Cypress — app must be running (`npm start`)
```

## Specs
```bash
openspec validate && openspec status --json
```
